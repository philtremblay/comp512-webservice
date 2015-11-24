package middleware;

import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.ReceiverAdapter;
import org.jgroups.View;

import java.io.*;
import java.util.BitSet;
import java.util.Hashtable;
import java.util.Stack;
import java.util.Vector;

/**
 * Created by marcyang on 2015-11-23.
 */
public class MidBroadcast extends ReceiverAdapter implements Runnable{



    JChannel channel;
    private String configFile = null;
    private File history = new File("MiddleHist.ser");
    public BitSet bit = new BitSet(2);


    private PacketData dataPackage = null;


    private RMHashtable mm_itemHT = null;
    private Hashtable<Integer, Vector> m_activeRM =null;
    private Hashtable<Integer, Stack> m_cmdList = null;

    //private TimeToLive[] m_ttl = null;

    public MidBroadcast(String xmlfile, RMHashtable m_itemHT, Hashtable<Integer, Vector> activeTxnRM, Hashtable<Integer, Stack> txnCmdList) {
        System.setProperty("java.net.preferIPv4Stack" , "true");
        this.configFile = xmlfile;

        this.mm_itemHT = m_itemHT;
        this.m_activeRM = activeTxnRM;
        this.m_cmdList = txnCmdList;

        this.dataPackage = new PacketData(mm_itemHT, m_activeRM, m_cmdList);


    }

/*
    private void startTimer() {

        int i = 0;
        while (i < m_ttl.length) {

            if (m_ttl[i] != null) {
                Thread t = new Thread(m_ttl[i]);
                t.start();
            }
            i++;
        }


    }
*/


    public void receive(Message msg) {
        System.out.println("RECEIVER SIDE!!!!");

        FileInputStream fileIn = null;
        PacketData e1 = null;
        try {
            fileIn = new FileInputStream((File) msg.getObject());
            ObjectInputStream in = new ObjectInputStream(fileIn);
            e1 = (PacketData) in.readObject();
            in.close();
            fileIn.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        
        synchronized (mm_itemHT) {
            mm_itemHT.putAll(e1.mm_itemHT);
        }
        synchronized (m_activeRM) {
            m_activeRM.putAll(e1.m_activeRM);
        }
        synchronized (m_cmdList) {
            m_cmdList.putAll(e1.m_cmdList);
        }

        System.out.println("\n\n\n\n Received!!!"+ m_activeRM.size() + "\n\n\n\n");

    }


    public void viewAccepted(View new_view) {
        System.out.println("** view: " + new_view);
        if (channel.getView().getMembers().get(0).toString().equals(channel.getName().toString())) {
            System.out.println("** coordinator = this channel: turn on the timer ");
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
                    out.writeObject(dataPackage);
                    System.out.println("SENDING THE PACKET");
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
        String serverConfig = workingDir+"/conf/middleudp.xml";
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
