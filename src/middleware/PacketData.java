package middleware;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Stack;
import java.util.Vector;

/**
 * Created by marcyang on 2015-11-23.
 */
public class PacketData implements Serializable{

    //this class compresses the customer manager and the transaction manager

    TimeToLive[] m_ttl = null;

    RMHashtable mm_itemHT;
    Hashtable<Integer, Vector> m_activeRM;
    Hashtable<Integer, Stack> m_cmdList;



    public PacketData(RMHashtable custdata, TimeToLive[] ttl) {
        this.mm_itemHT = custdata;

        this.m_ttl = ttl;
    }


    public PacketData(RMHashtable mm_itemHT, Hashtable<Integer, Vector> m_activeRM, Hashtable<Integer, Stack> m_cmdList) {
        this.mm_itemHT = mm_itemHT;
        this.m_activeRM = m_activeRM;
        this.m_cmdList = m_cmdList;
    }
}
