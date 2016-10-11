package com.thefirm.beerme.BeerModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by root on 9/15/16.
 */
public class QueueManager {
    public static List<BeerPojo> beerCommandsQueue=new ArrayList<BeerPojo>();


    public static boolean commandExistInQueue(int orderID){
        for (BeerPojo beerCommand:beerCommandsQueue) {
            if (beerCommand.getOrderId()==orderID){
                return true;
            }
        }
        return false;
    }
    public static boolean commandIsSent(int orderID){
        for (BeerPojo beerCommand:beerCommandsQueue) {
            if (beerCommand.getOrderId()==orderID){
                if (beerCommand.isSentCommand())
                    return true;
            }
        }
        return false;
    }

    public static void setCommandSent(int orderID){
        for (int i=0;i<beerCommandsQueue.size();i++) {
            if (beerCommandsQueue.get(i).getOrderId() == orderID) {
                beerCommandsQueue.get(i).setSentCommand(true);
            }
        }
    }
}
