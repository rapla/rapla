package org.rapla.framework;


public class RaplaContextException
    extends RaplaException

{
    private static final long serialVersionUID = 1L;
    
    public RaplaContextException( final String key ) {
        super( "Unable to provide implementation for "  + key + " " );
    }
   
    public RaplaContextException( final Class<?> clazz, final String message ) {
        super("Unable to provide implementation for " + clazz.getName() + " " + message);
    }
    
    public RaplaContextException(  final String message, final Throwable throwable )
    {
        super( message, throwable );
    }



}
