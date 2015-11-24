package server;

import server.LockManager.LockManager;

import java.io.Serializable;


/**
 * Created by marcyang on 2015-11-23.
 */
public class DataPacket implements Serializable {


    RMHashtable mm_itemHT = null;
    LockManager m_lock = null;

    public DataPacket(RMHashtable m_itemHT, LockManager lockServer) {

        this.mm_itemHT = m_itemHT;
        this.m_lock = lockServer;
    }

}
