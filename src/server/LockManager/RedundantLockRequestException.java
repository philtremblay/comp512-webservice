package server.LockManager;

/*
	The transaction requested a lock that it already had.
*/

import java.io.Serializable;

public class RedundantLockRequestException extends Exception implements Serializable
{
	protected int xid = 0;
	
	public RedundantLockRequestException (int xid, String msg)
	{
		super(msg);
		this.xid = xid;
	}
	
	public int getXId() {
		return this.xid;
	}
}
