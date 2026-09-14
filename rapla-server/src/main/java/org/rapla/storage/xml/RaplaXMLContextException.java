package org.rapla.storage.xml;

import org.rapla.framework.RaplaException;

public class RaplaXMLContextException
    extends RaplaException

{
    private static final long serialVersionUID = 1L;
    
    public RaplaXMLContextException(final String key) {
        super( "Unable to provide implementation for "  + key + " " );
    }
   
    public RaplaXMLContextException(final Class<?> clazz, final String message) {
        super("Unable to provide implementation for " + clazz.getName() + " " + message);
    }
    
    public RaplaXMLContextException(final String message, final Throwable throwable)
    {
        super( message, throwable );
    }



}
