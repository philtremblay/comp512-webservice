package client;


import java.net.MalformedURLException;
import java.util.*;

/**
 * Created by marcyang on 2015-11-08.
 */
public class TimeToLive implements Runnable {


    protected Hashtable<Integer, Stack<Integer>> activeTransaction = new Hashtable<>();
    int txnid;
    protected Timer timer;
    RemindTask rt;
    client.ResourceManager proxy;


    public TimeToLive(int txnId, client.ResourceManager proxy) {

        this.txnid = txnId;
        this.proxy = proxy;
        init(txnId);
        timer = new Timer();
        rt = new RemindTask(activeTransaction.get(txnId));

    }

    private void init(int txnId) {

        Stack s = new Stack<>();
        s.push(new Integer(txnId));

        synchronized (this.activeTransaction) {
            activeTransaction.put(txnId, s);
        }

    }

    public boolean pushItem(int txnid) {
        boolean pushed = false;
        synchronized (this.activeTransaction) {
            if (this.activeTransaction.containsKey(txnid)) {
                this.activeTransaction.get(txnid).push(new Integer(txnid));
                pushed = true;
            }
        }
        return pushed;
    }

    public boolean pushCommit(int txnid) {
        boolean pushed = false;
        synchronized (this.activeTransaction) {
            if (this.activeTransaction.containsKey(txnid)) {
                this.activeTransaction.get(txnid).push(new Integer(0));
                pushed = true;
            }
        }

        return pushed;
    }

    public boolean empty(int txnid) {
        synchronized (this.activeTransaction) {
            return this.activeTransaction.get(txnid).empty();
        }
    }


    @Override
    public void run() {

        timer.scheduleAtFixedRate(rt, new Date(), 60000);

    }

    class RemindTask extends TimerTask{


        private Stack<Integer> sTran;

        public RemindTask(Stack<Integer> s) {
            this.sTran = s;


        }

        @Override
        public void run() {

            synchronized (this.sTran) {

                if (!this.sTran.empty()) {
                    //pop everything


                    int object = sTran.pop();
                    this.sTran.clear();

                        //System.out.println("POP object "+ object + ", wait for 30 seconds");
                    if (object == 0) {
                        System.out.println("TIME OUT --> ready to commit TRANSACTION ID " + txnid);
                        timer.cancel();
                        Thread.currentThread().interrupt();
                    }

                }
                else{
                    System.out.println("TIME OUT");

                    System.out.println("TRYING TO ABORT TRANSACTION" + txnid);

                    proxy.abort(txnid);
                    System.out.println("TRANSACTION " + txnid + " ABORTED ");

                    timer.cancel();
                    Thread.currentThread().interrupt();

                }
            }
        }
    }



}


