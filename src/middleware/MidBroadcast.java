package middleware;

import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.ReceiverAdapter;
import org.jgroups.View;
import org.jgroups.util.Util;
import server.*;

import java.io.*;
import java.util.BitSet;

/**
 * Created by marcyang on 2015-11-23.
 */
public class MidBroadcast extends ReceiverAdapter implements Runnable{



    JChannel channel;
    private MiddleDatabase tempData;

    public BitSet bit = new BitSet(2);
    private String configFile = null;
    private File history = new File("MiddleHist.ser");
    private BitSet m_timerBit = null;

    public MidBroadcast(String xmlfile, MiddleDatabase middata, BitSet timerBit) {
        System.setProperty("java.net.preferIPv4Stack" , "true");
        this.configFile = xmlfile;

        this.tempData = middata;
        this.m_timerBit = timerBit;
    }


    public void receive(Message msg) {
        System.out.println("RECEIVER SIDE!!!!");

        FileInputStream fileIn = null;
        server.RMHashtable e1 = null;
        try {
            fileIn = new FileInputStream((File) msg.getObject());
            ObjectInputStream in = new ObjectInputStream(fileIn);
            e1 = (server.RMHashtable) in.readObject();
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
        //tempTable.putAll(e1);

        System.out.println(e1.size());

    }


    public void viewAccepted(View new_view) {
        System.out.println("** view: " + new_view);

        if (channel.getView().getMembers().get(0).toString().equals(channel.getName().toString())) {
            System.out.println("** coordinator = this channel: turn on the timer ");
            m_timerBit.set(0);
        }
    }

/*
    public void getState(OutputStream output) throws Exception {

        synchronized(tempTable) {
            FileOutputStream fileOut = new FileOutputStream(history);
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            out.writeObject(tempTable);
            Util.objectToStream(history, out);
        }
    }

    public void setState(InputStream input) {
        server.RMHashtable list = null;
        try {
            input = new FileInputStream(history);
            ObjectInputStream d = new ObjectInputStream(input);
            list = (server.RMHashtable) d.readObject();
        } catch (Exception e) {
            e.printStackTrace();
        }

        synchronized(tempTable) {
            tempTable.clear();
            tempTable.putAll(list);
        }
        //System.out.println("\n\n\n\nreceived state (messages in chat history):\n\n\n\n");

    }
*/

    private void multicast() {

        BufferedInputStream bis = null;
        while(true) {
            try {
                if(bit.get(0) && !bit.get(1)) {

                    File file = new File ("Middle.ser");
                    FileOutputStream fileOut = new FileOutputStream(file);
                    ObjectOutputStream out = new ObjectOutputStream(fileOut);
                    out.writeObject(tempData);

                    Message msg = new Message(null, null, file);
                    channel.send(msg);
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
            channel.connect("Middleware-Cluster");
            channel.getState(null, 10000);
            multicast();
            channel.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

}
