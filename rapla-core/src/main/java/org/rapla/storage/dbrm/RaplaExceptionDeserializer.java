package org.rapla.storage.dbrm;

import org.rapla.entities.DependencyException;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaSynchronizationException;
import org.rapla.rest.SerializableExceptionInformation;
import org.rapla.rest.client.ExceptionDeserializer;
import org.rapla.storage.RaplaInvalidTokenException;
import org.rapla.storage.RaplaNewVersionException;
import org.rapla.storage.RaplaSecurityException;

public class RaplaExceptionDeserializer implements ExceptionDeserializer {
	public RaplaException deserializeException(SerializableExceptionInformation exeInfo, int statusCode)
    {
	    final String message = exeInfo.getMessage();
	    final String classname = exeInfo.getExceptionClass();
    	String error = "";
    	if ( message != null)
    	{
    		error+=message;
    	}
	    if ( classname != null)
	    {
            if ( classname.equals( WrongRaplaVersionException.class.getName()))
            {
                return new WrongRaplaVersionException( message);
            }
            else if ( classname.equals(RaplaNewVersionException.class.getName()))
            {
                return new RaplaNewVersionException( message);
            }
            else if ( classname.equals(RaplaInvalidTokenException.class.getName()))
            {
                return new RaplaInvalidTokenException( message);
            }
            else if ( classname.equals( RaplaSecurityException.class.getName()))
            {
                return new RaplaSecurityException( message);
            }
            else if ( classname.equals( RaplaSynchronizationException.class.getName()))
            {
                return new RaplaSynchronizationException( message);
            }
            else if ( classname.equals( EntityNotFoundException.class.getName()))
            {
//                    if ( param != null)
//                    {
//                            String id = (String)convertFromString( String.class, param);
//                            return new EntityNotFoundException( message, id);
//                    }
                return new EntityNotFoundException( message);
            }
            else if ( classname.equals( DependencyException.class.getName()))
            {
                return new DependencyException( message);
            }
            else
            {
                    error = classname + " " + error;
            }
	    }
	    return new RaplaException( error);
    }
}
