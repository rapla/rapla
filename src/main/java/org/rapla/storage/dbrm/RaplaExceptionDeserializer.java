package org.rapla.storage.dbrm;

import org.rapla.entities.DependencyException;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaSynchronizationException;
import org.rapla.storage.RaplaNewVersionException;
import org.rapla.storage.RaplaSecurityException;

import java.util.List;

public class RaplaExceptionDeserializer {
	public RaplaException deserializeException(String classname, String message, List<String> params) 
    {
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
            else if ( classname.equals( RaplaSecurityException.class.getName()))
            {
                return new RaplaSecurityException( message);
            }
            else if ( classname.equals( RaplaSynchronizationException.class.getName()))
            {
                return new RaplaSynchronizationException( message);
            }
            else if ( classname.equals( RaplaConnectException.class.getName()))
            {
                return new RaplaConnectException( message);
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
                if ( params != null)
                {
                	return new DependencyException( message,params);
                }
                //Collection<String> depList = Collections.emptyList();
                return new DependencyException( message, new String[] {});
            }
            else
            {
                    error = classname + " " + error;
            }
	    }
	    return new RaplaException( error);
    }
}
