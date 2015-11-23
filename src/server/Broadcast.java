package server;

import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.ReceiverAdapter;
import org.jgroups.View;
import org.jgroups.util.Util;
import java.io.*;
import java.util.*;
import server.RMHashtable;


/**
 * Created by marcyang on 2015-11-22.
 */
public class Broadcast extends ReceiverAdapter implements Runnable {




    JChannel channel;


    private RMHashtable tempTable;

    public BitSet bit = new BitSet(2);
    ResourceManagerImpl m_resourceManager;
    File history = new File("hist.ser");

    public Broadcast(ResourceManagerImpl resourceManager, RMHashtable m_itemHT) {
        System.setProperty("java.net.preferIPv4Stack" , "true");
        m_resourceManager = resourceManager;
        tempTable = m_itemHT;
    }






    public void receive(Message msg) {
        System.out.println("RECEIVER SIDE!!!!");

        FileInputStream fileIn = null;
        RMHashtable e1 = null;
        try {
            fileIn = new FileInputStream((File) msg.getObject());
            ObjectInputStream in = new ObjectInputStream(fileIn);
            e1 = (RMHashtable) in.readObject();
            in.close();
            fileIn.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }



        //System.out.println("\n\n\n"+msg.getSrc() + "\n\n\n");
        tempTable.putAll(e1);

        System.out.println(e1.size());

    }


    public void viewAccepted(View new_view) {
        System.out.println("** view: " + new_view);
    }


    public void getState(OutputStream output) throws Exception {
        System.out.println("\n\n\nSENDING THE STATE!!!!\n\n\n");

        synchronized(tempTable) {
            FileOutputStream fileOut = new FileOutputStream(history);
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            out.writeObject(tempTable);
            Util.objectToStream(history, out);
        }
    }

    public void setState(InputStream input) {
        RMHashtable list = null;
        try {
            input = new FileInputStream(history);
            ObjectInputStream d = new ObjectInputStream(input);
            list = (RMHashtable) d.readObject();
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        synchronized(tempTable) {
            tempTable.clear();
            tempTable.putAll(list);
        }
        System.out.println("\n\n\n\nreceived state (messages in chat history):\n\n\n\n");

    }


    private void multicast() {

        BufferedInputStream bis = null;
        while(true) {
            try {
                if(bit.get(0) && !bit.get(1)) {
                    //byte[] mybytearray = new byte[1024];
                    File file = new File ("h.ser");
                    FileOutputStream fileOut = new FileOutputStream(file);
                    ObjectOutputStream out = new ObjectOutputStream(fileOut);
                    out.writeObject(tempTable);
                    System.out.println("SENDING THE MESSAGE!!!!!!");

                    Message msg = new Message(null, null, file);
                    channel.send(msg);
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
        String serverConfig = workingDir+"/conf/"+"flightudp.xml";
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
