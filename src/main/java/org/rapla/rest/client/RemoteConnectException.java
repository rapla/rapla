package org.rapla.rest.client;

public class RemoteConnectException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RemoteConnectException()
    {
        super("Error to connect");
    }
    public RemoteConnectException(String text) {
        this(text, null);
    }
    
    public RemoteConnectException(Throwable cause) {
        this(cause.getMessage(), cause);
    }

    public RemoteConnectException(String message,Throwable cause) {
        super(message, cause);
    }


}
