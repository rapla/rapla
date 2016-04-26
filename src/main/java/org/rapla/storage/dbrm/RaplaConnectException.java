package org.rapla.storage.dbrm;

import org.rapla.framework.RaplaException;

public class RaplaConnectException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RaplaConnectException()
    {
        super("Connect Exception");
    }

    public RaplaConnectException(String text) {
        super(text, null);
    }
    
    public RaplaConnectException( Throwable cause) {
        super(cause.getMessage(), cause);
    }

}
