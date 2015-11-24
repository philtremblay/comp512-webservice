package middleware;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Stack;
import java.util.Vector;

/**
 * Created by marcyang on 2015-11-23.
 */
public class MiddleDatabase implements Serializable{

    //this class compresses the customer manager and the transaction manager
    RMHashtable customerData = null;
    TxnManager transactionData = null;
    TimeToLive[] m_ttl = null;

    public MiddleDatabase(RMHashtable custdata, TxnManager transManager, TimeToLive[] ttl) {
        this.customerData = custdata;
        this.transactionData = transManager;
        this.m_ttl = ttl;
    }














}
