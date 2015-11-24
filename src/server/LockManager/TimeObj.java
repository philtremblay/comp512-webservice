package server.LockManager;

import java.io.Serializable;
import java.util.Date;

public class TimeObj extends XObj implements Serializable
{
    private Date date = new Date();
    
    // The data members inherited are
    // XObj:: private int xid;
    
    TimeObj()
    {
        super();
    }
    
    TimeObj(int xid)
    {
        super(xid);
    }
    
    public long getTime() {
        return date.getTime();
    }
}
