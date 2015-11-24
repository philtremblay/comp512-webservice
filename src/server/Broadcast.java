package server;

import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.ReceiverAdapter;
import org.jgroups.View;
import org.jgroups.util.Util;
import java.io.*;
import java.util.*;

import server.LockManager.LockManager;
import server.RMHashtable;


/**
 * Created by marcyang on 2015-11-22.
 */
public class Broadcast extends ReceiverAdapter implements Runnable {


    JChannel channel;
    private DataPacket tempPacket = null;
    private LockManager tempLock;
    private RMHashtable tempTable;

    public BitSet bit = new BitSet(2);
    String configFile = null;
    File history = new File("hist.ser");

    public Broadcast(String xmlfile, LockManager lockServer, RMHashtable mItemHT) {
        System.setProperty("java.net.preferIPv4Stack" , "true");

        tempTable = mItemHT;
        tempLock = lockServer;
        this.configFile = xmlfile;

        this.tempPacket = new DataPacket(tempTable, tempLock);
    }

    public void receive(Message msg) {
        System.out.println("RECEIVER SIDE!!!!");

        FileInputStream fileIn = null;
        DataPacket e1 = null;
        try {
            fileIn = new FileInputStream((File) msg.getObject());
            ObjectInputStream in = new ObjectInputStream(fileIn);
            e1 = (DataPacket) in.readObject();
            in.close();
            fileIn.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        synchronized (tempTable) {
            tempTable.putAll(e1.mm_itemHT);
        }

        synchronized (tempLock) {
            tempLock = e1.m_lock;
        }

        //System.out.println("\n\n\n\n\n" + tempTable.size() + " \n\n\n");

    }


    public void viewAccepted(View new_view) {
        System.out.println("** view: " + new_view);
    }


    public void getState(OutputStream output) throws Exception {
        //System.out.println("\n\n\nSENDING THE STATE!!!!\n\n\n");

        synchronized(tempPacket) {
            FileOutputStream fileOut = new FileOutputStream(history);
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            out.writeObject(tempPacket);
            Util.objectToStream(history, out);
        }
    }

    public void setState(InputStream input) {
        DataPacket list = null;
        try {
            input = new FileInputStream(history);
            ObjectInputStream d = new ObjectInputStream(input);
            list = (DataPacket) d.readObject();
        } catch (Exception e) {
            e.printStackTrace();
        }
        synchronized (tempTable) {
            tempTable.clear();
            tempTable.putAll(list.mm_itemHT);
        }
        synchronized (tempLock) {
            tempLock = list.m_lock;
        }
    }


    private void multicast() {

        BufferedInputStream bis = null;
        while(true) {
            try {
                if(bit.get(0) && !bit.get(1)) {

                    File file = new File ("h.ser");

                    FileOutputStream fileOut = new FileOutputStream(file);
                    ObjectOutputStream out = new ObjectOutputStream(fileOut);
                    out.writeObject(tempPacket);

                    System.out.println("SENDING THE MESSAGE!!!!!!");

                    Message msg = new Message(null, null, file);
                    channel.send(msg);

                    //fileOut.close();
                    //out.close();
                    //turn off after sending the message
                    bit.flip(0);
                }
                else if (bit.get(1)) {
                    break;
                }
            }
            catch(Exception e) {

            }
        }
    }


    @Override
    public void run() {
        String workingDir = System.getProperty("user.dir");
        String serverConfig = workingDir+"/conf/" + configFile;
        try {
            channel = new JChannel(serverConfig);
            channel.setReceiver(this);
            channel.connect("Flight-Cluster");
            channel.getState(null, 10000);
            multicast();
            channel.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }



}
