package org.rapla.storage.xml;

import org.rapla.framework.RaplaContextException;
import org.rapla.framework.TypedComponentRole;

public interface RaplaXMLContext
{
	/** Returns a reference to the requested object (e.g. a component instance).
	 *  throws a RaplaContextException if the object can't be returned. This could have
	 *  different reasons: For example it is not found in the context, or there has been 
	 *  a problem during the component creation.   
	 */    
    <T> T lookup(Class<T> componentRole) throws RaplaContextException;
    boolean has(Class<?> clazz);
    <T> T lookup(TypedComponentRole<T> componentRole) throws RaplaContextException;
    //<T> T lookupDeprecated(TypedComponentRole<T> componentRole, String hint) throws RaplaContextException;
    boolean has(TypedComponentRole<?> componentRole);
}
