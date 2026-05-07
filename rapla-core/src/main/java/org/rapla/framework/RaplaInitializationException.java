package org.rapla.framework;

public class RaplaInitializationException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    public RaplaInitializationException(String message)
    {
        super(message);
    }
    
    public RaplaInitializationException(String message, Throwable cause)
    {
        super(message, cause);
    }

    public RaplaInitializationException(Throwable cause)
    {
        super(cause.getMessage(), cause);
    }

}
