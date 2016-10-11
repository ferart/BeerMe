package com.thefirm.beerme.BeerModel;

/**
 * Created by root on 9/15/16.
 */
public class BeerPojo {
    private long timestamp;
    private boolean sentCommand;
    private String location;
    private String beerType;
    private String user;
    private int orderId;

    public BeerPojo(long timestamp, String location, String beerType, String user, int orderId) {
        this.timestamp = timestamp;
        this.location = location;
        this.beerType = beerType;
        this.user = user;
        this.orderId = orderId;
        sentCommand=false;
    }




    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isSentCommand() {
        return sentCommand;
    }

    public void setSentCommand(boolean sentCommand) {
        this.sentCommand = sentCommand;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getBeerType() {
        return beerType;
    }

    public void setBeerType(String beerType) {
        this.beerType = beerType;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public int getOrderId() {
        return orderId;
    }

    public void setOrderId(int orderId) {
        this.orderId = orderId;
    }
}
