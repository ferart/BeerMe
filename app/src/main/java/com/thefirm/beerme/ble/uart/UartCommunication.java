package com.thefirm.beerme.ble.uart;

import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;


import com.thefirm.beerme.ble.ble.BleManager;
import com.thefirm.beerme.ble.mqtt.MqttManager;
import com.thefirm.beerme.ble.mqtt.MqttSettings;

import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.nio.charset.Charset;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;

public class UartCommunication extends UartInterface implements MqttManager.MqttManagerListener {
    // Log
    private final static String TAG = UartCommunication.class.getSimpleName();

    // Constants
    private static final int kNumPublishFeeds = 2;
    public static final int kPublishFeed_RX = 0;
    public static final int kPublishFeed_TX = 1;
    // Configuration
    private final static boolean kUseColorsForData = true;
    public final static int kDefaultMaxPacketsToPaintAsText = 500;

    // Activity request codes (used for onActivityResult)
    private static final int kActivityRequestCode_ConnectedSettingsActivity = 0;
    private static final int kActivityRequestCode_MqttSettingsActivity = 1;

    // Constants
    private final static String kPreferences = "UartActivity_prefs";
    private final static String kPreferences_eol = "eol";
    private final static String kPreferences_echo = "echo";
    private final static String kPreferences_asciiMode = "ascii";
    private final static String kPreferences_timestampDisplayMode = "timestampdisplaymode";

    // Colors
    private int mTxColor;
    private int mRxColor;
    private int mInfoColor = Color.parseColor("#F21625");



    // UI TextBuffer (refreshing the text buffer is managed with a timer because a lot of changes an arrive really fast and could stall the main thread)
    private Handler mUIRefreshTimerHandler = new Handler();
    private Runnable mUIRefreshTimerRunnable = new Runnable() {
        @Override
        public void run() {
            if (isUITimerRunning) {
                // Log.d(TAG, "updateDataUI");
                mUIRefreshTimerHandler.postDelayed(this, 200);
            }
        }
    };
    private boolean isUITimerRunning = false;

    // Data
    private boolean mShowDataInHexFormat;
    private boolean mIsTimestampDisplayMode;
    private boolean mIsEchoEnabled;
    private boolean mIsEolEnabled;

    private volatile SpannableStringBuilder mTextSpanBuffer;
    private volatile ArrayList<UartDataChunk> mDataBuffer= new ArrayList<>();;
    private volatile int mSentBytes;
    private volatile int mReceivedBytes;

    private DataFragment mRetainedDataFragment;

    private MqttManager mMqttManager;

    private int maxPacketsToPaintAsText;

    private Context context;


    public  UartCommunication(Context context){

        this.context=context;
        mBleManager = BleManager.getInstance(context);
        restoreRetainedDataFragment();

        // Get default theme colors
        TypedValue typedValue = new TypedValue();



        // Read shared preferences
        maxPacketsToPaintAsText = kDefaultMaxPacketsToPaintAsText;
        //Log.d(TAG, "maxPacketsToPaintAsText: "+maxPacketsToPaintAsText);

        // Read local preferences
        SharedPreferences preferences = context.getSharedPreferences(kPreferences, context.MODE_PRIVATE);
        mShowDataInHexFormat = !preferences.getBoolean(kPreferences_asciiMode, true);
        final boolean isTimestampDisplayMode = preferences.getBoolean(kPreferences_timestampDisplayMode, false);
        mIsEchoEnabled = preferences.getBoolean(kPreferences_echo, true);
        mIsEolEnabled = preferences.getBoolean(kPreferences_eol, true);


        // Continue
        onServicesDiscovered();

        // Mqtt init
        mMqttManager = MqttManager.getInstance(context);
        if (MqttSettings.getInstance(context).isConnected()) {
            mMqttManager.connectFromSavedSettings(context);
        }

        resumeUartComm();
    }


    public void resumeUartComm() {

        // Setup listeners
        mBleManager.setBleListener(this);

        mMqttManager.setListener(this);
        updateMqttStatus();

        isUITimerRunning = true;
        mUIRefreshTimerHandler.postDelayed(mUIRefreshTimerRunnable, 0);

    }


    public void onPause() {

        //Log.d(TAG, "remove ui timer");
        isUITimerRunning = false;
        mUIRefreshTimerHandler.removeCallbacksAndMessages(mUIRefreshTimerRunnable);

        // Save preferences
        SharedPreferences preferences = context.getSharedPreferences(kPreferences, context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(kPreferences_echo, mIsEchoEnabled);
        editor.putBoolean(kPreferences_eol, mIsEolEnabled);
        editor.putBoolean(kPreferences_asciiMode, !mShowDataInHexFormat);
        editor.putBoolean(kPreferences_timestampDisplayMode, mIsTimestampDisplayMode);

        editor.apply();
    }


    public void onDestroy() {
        // Disconnect mqtt
        if (mMqttManager != null) {
            mMqttManager.disconnect();
        }

        // Retain data
        saveRetainedDataFragment();
    }



    public void sendDataToDevice(String data, SendDataCompletionHandler sendDataCompletionHandler) {
        uartSendData(data, false, sendDataCompletionHandler);
    }

    private void uartSendData(String data, boolean wasReceivedFromMqtt,SendDataCompletionHandler sendDataCompletionHandler) {
        // MQTT publish to TX
        MqttSettings settings = MqttSettings.getInstance(context);
        if (!wasReceivedFromMqtt) {
            if (settings.isPublishEnabled()) {
                String topic = settings.getPublishTopic(kPublishFeed_TX);
                final int qos = settings.getPublishQos(kPublishFeed_TX);
                mMqttManager.publish(topic, data, qos);
            }
        }

        // Add eol
        if (mIsEolEnabled) {
            // Add newline character if checked
            data += "\n";
        }

        // Send to uart
        if (!wasReceivedFromMqtt || settings.getSubscribeBehaviour() == MqttSettings.kSubscribeBehaviour_Transmit) {
            sendData(data,sendDataCompletionHandler);
            mSentBytes += data.length();
        }

        // Add to current buffer
        UartDataChunk dataChunk = new UartDataChunk(System.currentTimeMillis(), UartDataChunk.TRANSFERMODE_TX, data);
        mDataBuffer.add(dataChunk);

        final String formattedData = mShowDataInHexFormat ? asciiToHex(data) : data;


    }





    /*
    public void onClickFormatAscii(View view) {
        mShowDataInHexFormat = false;
        recreateDataView();
    }

    public void onClickFormatHex(View view) {
        mShowDataInHexFormat = true;
        recreateDataView();
    }

    public void onClickDisplayFormatText(View view) {
        setDisplayFormatToTimestamp(false);
        recreateDataView();
    }

    public void onClickDisplayFormatTimestamp(View view) {
        setDisplayFormatToTimestamp(true);
        recreateDataView();
    }
    */





    private int mMqttMenuItemAnimationFrame = 0;






    // endregion

    // region BleManagerListener
    /*
    @Override
    public void onConnected() {

    }

    @Override
    public void onConnecting() {

    }
*/
    @Override
    public void onDisconnected() {
        super.onDisconnected();
        Log.d(TAG, "Disconnected. Back to previous activity");

    }

    @Override
    public void onServicesDiscovered() {
        super.onServicesDiscovered();
        enableRxNotifications();
    }

    @Override
    public synchronized void onDataAvailable(BluetoothGattCharacteristic characteristic) {
        super.onDataAvailable(characteristic);
        // UART RX
        if (characteristic.getService().getUuid().toString().equalsIgnoreCase(UUID_SERVICE)) {
            if (characteristic.getUuid().toString().equalsIgnoreCase(UUID_RX)) {
                final byte[] bytes = characteristic.getValue();
                final String data = new String(bytes, Charset.forName("UTF-8"));

                mReceivedBytes += bytes.length;

                final UartDataChunk dataChunk = new UartDataChunk(System.currentTimeMillis(), UartDataChunk.TRANSFERMODE_RX, data);
                mDataBuffer.add(dataChunk);

                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        if (mIsTimestampDisplayMode) {
                            final String currentDateTimeString = DateFormat.getTimeInstance().format(new Date(dataChunk.getTimestamp()));
                            final String formattedData = mShowDataInHexFormat ? asciiToHex(data) : convertLineSeparators(data);

                        }

                    }
                });

                // MQTT publish to RX
                MqttSettings settings = MqttSettings.getInstance(context);
                if (settings.isPublishEnabled()) {
                    String topic = settings.getPublishTopic(kPublishFeed_RX);
                    final int qos = settings.getPublishQos(kPublishFeed_RX);
                    mMqttManager.publish(topic, data, qos);
                }
            }
        }
    }

    private String convertLineSeparators(String text) {
        String formattedText = text.replaceAll("(\\r\\n|\\r)", "\n");
        return formattedText;
    }
/*
    @Override
    public void onDataAvailable(BluetoothGattDescriptor descriptor) {

    }

    @Override
    public void onReadRemoteRssi(int rssi) {

    }
    */
    // endregion

    /*
    private void addTextToSpanBuffer(SpannableStringBuilder spanBuffer, String text, int color) {

        if (kUseColorsForData) {
            final int from = spanBuffer.length();
            spanBuffer.append(text);
            spanBuffer.setSpan(new ForegroundColorSpan(color), from, from + text.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            spanBuffer.append(text);
        }
    }
*/



    private int mDataBufferLastSize = 0;
/*
    private void updateTextDataUI() {

        if (!mIsTimestampDisplayMode) {
            if (mDataBufferLastSize != mDataBuffer.size()) {

                final int bufferSize = mDataBuffer.size();
                if (bufferSize > maxPacketsToPaintAsText) {
                    mDataBufferLastSize = bufferSize - maxPacketsToPaintAsText;
                    mTextSpanBuffer.clear();
                    addTextToSpanBuffer(mTextSpanBuffer, getString(R.string.uart_text_dataomitted) + "\n", mInfoColor);
                }

                // Log.d(TAG, "update packets: "+(bufferSize-mDataBufferLastSize));
                for (int i = mDataBufferLastSize; i < bufferSize; i++) {
                    final UartDataChunk dataChunk = mDataBuffer.get(i);
                    final boolean isRX = dataChunk.getMode() == UartDataChunk.TRANSFERMODE_RX;
                    final String data = dataChunk.getData();
                    final String formattedData = mShowDataInHexFormat ? asciiToHex(data) : convertLineSeparators(data);
                    addTextToSpanBuffer(mTextSpanBuffer, formattedData, isRX ? mRxColor : mTxColor);
                }

                mDataBufferLastSize = mDataBuffer.size();
                mBufferTextView.setText(mTextSpanBuffer);
                mBufferTextView.setSelection(0, mTextSpanBuffer.length());        // to automatically scroll to the end
            }
        }
    }
    */

    private void recreateDataView() {
/*
        if (mIsTimestampDisplayMode) {
            mBufferListAdapter.clear();

            final int bufferSize = mDataBuffer.size();
            for (int i = 0; i < bufferSize; i++) {

                final UartDataChunk dataChunk = mDataBuffer.get(i);
                final boolean isRX = dataChunk.getMode() == UartDataChunk.TRANSFERMODE_RX;
                final String data = dataChunk.getData();
                final String formattedData = mShowDataInHexFormat ? asciiToHex(data) : data;

                final String currentDateTimeString = DateFormat.getTimeInstance().format(new Date(dataChunk.getTimestamp()));
                mBufferListAdapter.add(new TimestampData("[" + currentDateTimeString + "] " + (isRX ? "RX" : "TX") + ": " + formattedData, isRX ? mRxColor : mTxColor));
//                mBufferListAdapter.add("[" + currentDateTimeString + "] " + (isRX ? "RX" : "TX") + ": " + formattedData);
            }
            mBufferListView.setSelection(mBufferListAdapter.getCount());
        } else {
            mDataBufferLastSize = 0;
            mTextSpanBuffer.clear();
            mBufferTextView.setText("");
        }
        */
    }

    private String asciiToHex(String text) {
        StringBuffer stringBuffer = new StringBuffer();
        for (int i = 0; i < text.length(); i++) {
            String charString = String.format("0x%02X", (byte) text.charAt(i));

            stringBuffer.append(charString + " ");
        }
        return stringBuffer.toString();
    }

    // region DataFragment
    public static class DataFragment extends Fragment {
        private boolean mShowDataInHexFormat;
        private SpannableStringBuilder mTextSpanBuffer;
        private ArrayList<UartDataChunk> mDataBuffer;
        private int mSentBytes;
        private int mReceivedBytes;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setRetainInstance(true);
        }
    }

    private void restoreRetainedDataFragment() {
        /*
        // find the retained fragment
        FragmentManager fm = getFragmentManager();
        mRetainedDataFragment = (DataFragment) fm.findFragmentByTag(TAG);

        if (mRetainedDataFragment == null) {
            // Create
            mRetainedDataFragment = new DataFragment();
            fm.beginTransaction().add(mRetainedDataFragment, TAG).commit();

            mDataBuffer = new ArrayList<>();
            mTextSpanBuffer = new SpannableStringBuilder();
        } else {
            // Restore status
            mShowDataInHexFormat = mRetainedDataFragment.mShowDataInHexFormat;
            mTextSpanBuffer = mRetainedDataFragment.mTextSpanBuffer;
            mDataBuffer = mRetainedDataFragment.mDataBuffer;
            mSentBytes = mRetainedDataFragment.mSentBytes;
            mReceivedBytes = mRetainedDataFragment.mReceivedBytes;
        }
        */
    }

    private void saveRetainedDataFragment() {
        mRetainedDataFragment.mShowDataInHexFormat = mShowDataInHexFormat;
        mRetainedDataFragment.mTextSpanBuffer = mTextSpanBuffer;
        mRetainedDataFragment.mDataBuffer = mDataBuffer;
        mRetainedDataFragment.mSentBytes = mSentBytes;
        mRetainedDataFragment.mReceivedBytes = mReceivedBytes;
    }
    // endregion


    // region MqttManagerListener

    private void updateMqttStatus() {

        MqttManager mqttManager = MqttManager.getInstance(context);
        MqttManager.MqqtConnectionStatus status = mqttManager.getClientStatus();

    }

    @Override
    public void onMqttConnected() {
        updateMqttStatus();
    }

    @Override
    public void onMqttDisconnected() {
        updateMqttStatus();
    }

    @Override
    public void onMqttMessageArrived(String topic, MqttMessage mqttMessage) {
        final String message = new String(mqttMessage.getPayload());

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                uartSendData(message, true,null);       // Don't republish to mqtt something received from mqtt
            }
        });

    }

    // endregion




    // endregion
}
