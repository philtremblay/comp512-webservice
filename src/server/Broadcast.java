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



    RMHashtable m_itemHT;
    JChannel channel;
    String user_name=System.getProperty("user.name", "n/a");


    Hashtable<String, RMItem> tempTable = new Hashtable<String, RMItem>();

    public BitSet bit = new BitSet(2);

    public Broadcast(RMHashtable m_itemHT) throws Exception {
        System.setProperty("java.net.preferIPv4Stack" , "true");
        this.m_itemHT = m_itemHT;

        for (Object key: m_itemHT.keySet()) {
            tempTable.put((String) key, readData((String) key));
        }

    }

    public void receive(Message msg) {
        Hashtable<String, RMItem> dataReceived = (Hashtable<String, RMItem>) msg.getObject();
        synchronized(tempTable) {
            tempTable.putAll(dataReceived);
            m_itemHT.putAll(tempTable);
        }


    }


    public void viewAccepted(View new_view) {
        System.out.println("** view: " + new_view);
    }
    /*
    public void getState(OutputStream output) throws Exception {
        synchronized(m_itemHT) {
            Util.objectToStream(m_itemHT, new DataOutputStream(output));
        }
    }

    public void setState(InputStream input) throws Exception {
        ObjectInputStream d = new ObjectInputStream(input);
        RMHashtable list= (RMHashtable) Util.objectFromStream(d);
        synchronized(m_itemHT) {
            m_itemHT.clear();
            m_itemHT.putAll(list);
        }
        System.out.println("received state (" + list.size() + " messages in chat history):");

        System.out.println(list.toString());
    }
    */

    private void multicast() {
        while(true) {
            try {

                if(bit.get(0) && !bit.get(1)) {
                    Message msg = new Message(null, null, tempTable);
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
    // Read a data item.
    private RMItem readData(String key) {
        synchronized(m_itemHT) {
            return (RMItem) m_itemHT.get(key);
        }
    }

    // Write a data item.
    private void writeData(String key, RMItem value) {
        synchronized(m_itemHT) {
            m_itemHT.put(key, value);
        }
    }

    // Remove the item out of storage.
    protected RMItem removeData(String key) {
        synchronized(m_itemHT) {
            return (RMItem) m_itemHT.remove(key);
        }
    }


}
