package org.rapla.storage.dbrm;


public class RaplaRestartingException extends RaplaConnectException {

    private static final long serialVersionUID = 1L;

    public RaplaRestartingException() {
        super("Connection to server aborted. Restarting client.");
    }

}
