package server.LockManager;

/*
    The transaction is deadlocked.  Somebody should abort it.
*/

import java.io.Serializable;

public class DeadlockException extends Exception implements Serializable
{
    private int xid = 0;
    
    public DeadlockException (int xid, String msg)
    {
        super("The transaction " + xid + " is deadlocked:" + msg);
        this.xid = xid;
    }
    
    int GetXId()
    {
        return xid;
    }
}
