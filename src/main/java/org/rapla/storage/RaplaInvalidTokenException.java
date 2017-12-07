package org.rapla.storage;

/** Is thrown when authentication token is no longer valid */
public class RaplaInvalidTokenException extends RaplaSecurityException
{
    public RaplaInvalidTokenException(String text)
    {
        super(text);
    }
}
