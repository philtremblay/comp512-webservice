package middleware;

import java.io.Serializable;
import java.util.*;

/**
 * Created by marcyang on 2015-11-08.
*/
public class TimeToLive implements Serializable, Runnable {


        protected Hashtable<Integer, Stack<Integer>> activeTransaction = new Hashtable<>();
        int txnid;
        protected Timer timer;
        RemindTask rt;
        ResourceManagerImpl proxy;


        public TimeToLive(int txnId, ResourceManagerImpl resourceManager) {

            this.txnid = txnId;
            this.proxy = resourceManager;
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
        public boolean pushAbort(int txnid) {
            boolean pushed = false;
            synchronized (this.activeTransaction) {
                if (this.activeTransaction.containsKey(txnid)) {
                    this.activeTransaction.get(txnid).push(new Integer(-1));
                    pushed = true;
                }
            }

            return pushed;
        }

        //public boolean





        @Override
        public void run() {

            timer.scheduleAtFixedRate(rt, new Date(), 60000);

        }

        class RemindTask extends TimerTask implements Serializable{


            private Stack<Integer> sTran;

            public RemindTask(Stack<Integer> s) {
                this.sTran = s;
            }

            @Override
            public void run() {

                synchronized (this.sTran) {

                    if (!this.sTran.empty()) {
                        //pop everything

                        while (!this.sTran.empty()) {

                            int object = sTran.pop();

                            //System.out.println("POP object "+ object + ", wait for 30 seconds");
                            if (object == 0) {
                                System.out.println("TIME OUT --> already committed TRANSACTION ID " + txnid);
                                timer.cancel();
                                Thread.currentThread().interrupt();
                                break;
                            } else if (object == -1) {
                                System.out.println("TIME OUT --> already aborted TRANSACTION ID " + txnid);
                                timer.cancel();
                                Thread.currentThread().interrupt();
                                break;
                            }
                            else {
                                continue;
                            }
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

