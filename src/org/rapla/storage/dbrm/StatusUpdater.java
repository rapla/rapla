package org.rapla.storage.dbrm;

public interface StatusUpdater {
    public enum Status
    {
    	READY,
    	BUSY
    }
    void setStatus( Status status);
}
