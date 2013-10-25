package org.rapla.storage.dbrm;

public interface StatusUpdater {
    enum Status
    {
    	READY,
    	BUSY
    }
    void setStatus( Status status);
}
