package middleware;

import java.io.Serializable;
import java.util.*;

import static java.lang.Math.max;

/**
 * Created by Phil Tremblay on 2015-11-06.
 */
public class TxnManager implements Serializable {



    //DataStructure containing active transactions, list of update commands by a transaction and the list of RM's used by this transaction
    protected Hashtable <Integer,Vector> activeTxnRM = new Hashtable<>();
    protected Hashtable <Integer,Stack> txnCmdList = new Hashtable<>();

    //methods for txnCmdList
    private void addUpdateCmdToStack(){

    }
    //creating unique trxnId
    protected Integer currentTxnId;

    private synchronized void incrTxnId(){

        //increment transaction ID based on the maximum of the existing ID
        /*
        synchronized (this.activeTxnRM) {
            Integer temp = 0;
            if (this.activeTxnRM.size() > 0) {
                for (Integer id : this.activeTxnRM.keySet()) {
                    if (temp < id) {
                        temp = id;
                    }
                }
            }
            this.currentTxnId = temp + 1;
        }
        */

        this.currentTxnId++;

    }
    private synchronized Integer getTxnIdVal(){
        return this.currentTxnId;
    }


    //initiate the global transactionId counter
    public TxnManager() {
        this.currentTxnId = 0;
    }

    public Integer newTxn(){
        //increment txn counter
        this.incrTxnId();

        //assign this counter to new txn
        Integer txnId = this.getTxnIdVal();

        //add this transaction to the TM datastructures and create
        Stack cmdStack = new Stack();
        Vector activeRMList = new Vector();

        synchronized (this.txnCmdList) {
            this.txnCmdList.put(txnId, cmdStack);
        }
        synchronized (this.activeTxnRM) {
            this.activeTxnRM.put(txnId,activeRMList);
        }
        return txnId;
    }

    public boolean setNewUpdateItem(int txnId, Vector cmd){
        //check if it's a valid txnId
        if (this.txnCmdList.containsKey(txnId)){
            appendStack(txnId,cmd);
            return true;
        }
        else{
            System.out.println("TRANSACTION HAS NO VALID ENTRY IN TRANSACTION COMMAND LIST HASH");
            return false;
        }
    }
    public boolean enlist(int txnId, int RMType){
        if (this.activeTxnRM.containsKey(txnId)){
            //check for duplicates
            if (!this.activeTxnRM.contains(RMType)) {
                appendActive(txnId,RMType);
            }
            return true;
        }
        else{
            System.out.println("TRANSACTION HAS NO VALID ENTRY IN TRANSACTION COMMAND LIST HASH");
            return false;
        }

    }


    private void appendActive(int txnId,int RMType){
        synchronized (this.activeTxnRM){
            Vector list = this.activeTxnRM.get(txnId);
            list.add(RMType);
            this.activeTxnRM.put(txnId,list);
        }

    }
    //append to cmdStack
    private void appendStack(int txnId, Vector command){
        synchronized (this.txnCmdList){
            Stack cmdList = this.txnCmdList.get(txnId);
            if (!cmdList.contains(command)) {
                cmdList.push(command);
                this.txnCmdList.put(txnId, cmdList);
            }
        }
    }
}
