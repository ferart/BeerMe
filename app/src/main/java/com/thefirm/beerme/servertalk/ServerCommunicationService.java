package com.thefirm.beerme.servertalk;


import android.util.Log;

import com.squareup.okhttp.Callback;
import com.squareup.okhttp.FormEncodingBuilder;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import com.thefirm.beerme.BeerModel.BeerPojo;
import com.thefirm.beerme.BeerModel.QueueManager;

import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by root on 9/15/16.
 */
public class ServerCommunicationService {

    private ScheduledExecutorService scheduledExecutorService;
    private final long mInitialDelay=2;
    private final long mDelayGaps=5;
    OkHttpClient client;
    private Request request;
    private Future<?> future;
    private boolean schedulerRunningFlag;
    private static final String TOKEN="fSEApjLbFAWLf3QZQLdSTb7O";
    public static final MediaType JSON  = MediaType.parse("application/json; charset=utf-8");
    SimpleCallback simpleCallback;


    private static ServerCommunicationService serverCommunicationService;
    public static ServerCommunicationService getInstance(){
        if (serverCommunicationService==null){
            return new ServerCommunicationService();
        }
        return serverCommunicationService;
    }

    public ServerCommunicationService() {
        client = new OkHttpClient();
        scheduledExecutorService= Executors.newScheduledThreadPool(1);

    }

    private final class ServerCommTask implements Runnable{

        @Override
        public void run() {
            RequestBody body = new FormEncodingBuilder()
                    .add("token", TOKEN)
                    .add("action","fetch")
                    .build();

            request = new Request.Builder()
                    .url("http://thefirm.x10host.com/beerme_retrieve_order.php")
                    .post(body)
                    .build();
            // Get a handler that can be used to post to the main thread
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Request request, IOException e) {
                    if (request!=null){

                    }
                }

                @Override
                public void onResponse(Response response) throws IOException {
                    if (response.isSuccessful()) {
                        // Read data on the worker thread

                        try {
                            final String responseData = response.body().string();
                            JSONObject json = new JSONObject(responseData);
                            if(json.getString("status").equals("success")){
                                long timestamp= Long.parseLong(json.getString("timestamp"));
                                String location= json.getString("location");
                                String beerType=json.getString("beer");
                                String user= json.getString("user");
                                int orderId=Integer.parseInt(json.getString("order_id"));
                                BeerPojo beerPojo= new BeerPojo(timestamp,location,beerType,user,orderId);;
                                if (orderId==-1){
                                    simpleCallback.error();
                                }else {
                                    if (!QueueManager.commandExistInQueue(orderId)) {
                                        QueueManager.beerCommandsQueue.add(beerPojo);
                                    }
                                    if (!QueueManager.commandIsSent(orderId)) {
                                        simpleCallback.success(beerPojo);
                                    }
                                }

                            }

                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }

                }

            });

        }
    }

    public boolean ackSent(final int orderId){
        RequestBody body = new FormEncodingBuilder()
                .add("token", TOKEN)
                .add("action","complete")
                .add("order_id",String.valueOf(orderId))
                .build();

        Request request = new Request.Builder()
                .url("http://thefirm.x10host.com/beerme_retrieve_order.php")
                .post(body)
                .build();


        // Get a handler that can be used to post to the main thread
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                if (request!=null){

                }
            }

            @Override
            public void onResponse(Response response) throws IOException {
                if (response.isSuccessful()) {
                    // Read data on the worker thread

                    try {
                        final String responseData = response.body().string();
                        JSONObject json = new JSONObject(responseData);
                        if(json.getString("status").equals("success")) {
                            Log.v("firm", "Info has been sent to the server");
                            if (json.getString("status").equals("success")) {
                                QueueManager.setCommandSent(orderId);
                            }

                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

            }

        });


        return false;
    }

    public ScheduledExecutorService getScheduledExecutorService() {
        return scheduledExecutorService;
    }

    public void startScheduler(){
        try {
            if (mDelayGaps>0) {
                future=scheduledExecutorService.scheduleWithFixedDelay(new ServerCommTask(), mInitialDelay, mDelayGaps, TimeUnit.SECONDS);
                schedulerRunningFlag =true;
            }
        }catch (RejectedExecutionException e){
            schedulerRunningFlag =false;
            if (scheduledExecutorService.isTerminated()){
                scheduledExecutorService= Executors.newScheduledThreadPool(1);
                startScheduler();
            }
        }


    }

    public void stoScheduler(){
        if (future!=null && schedulerRunningFlag){
            future.cancel(true);
            schedulerRunningFlag =false;
        }

    }

    public void shutdownSchedulerService(){
        scheduledExecutorService.shutdownNow();
        schedulerRunningFlag =false;
    }

    public SimpleCallback getSimpleCallback() {
        return simpleCallback;
    }

    public void setSimpleCallback(SimpleCallback simpleCallback) {
        this.simpleCallback = simpleCallback;
    }
}
