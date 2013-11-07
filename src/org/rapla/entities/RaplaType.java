/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Christopher Kohlhaas                                  |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/
package org.rapla.entities;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.rapla.framework.RaplaException;

/**
 * Enumeration Pattern for all Rapla objects. You should not instanciate Objects of this type,
 * there is only one instance of RaplaType for each class of objects. You can get it via
 * the object interface. E.g. Reservation.TYPE or Allocatable.TYPE
 */
public class RaplaType<T>  {

    private Class<? extends RaplaObject> type;
    private String localname;
    private static Map<Class<? extends RaplaObject>,RaplaType> registeredTypes = new HashMap<Class<? extends RaplaObject>,RaplaType>();
    private static Map<String,RaplaType> registeredTypeNames = new HashMap<String,RaplaType>();

	public RaplaType(Class<T> clazz, String localname) {
        @SuppressWarnings("unchecked")
		Class<? extends RaplaObject> clazz2 = (Class<? extends RaplaObject>) clazz;
		this.type = clazz2;
        this.localname = localname;
        if ( registeredTypes == null)
        {
            registeredTypes = new HashMap<Class<? extends RaplaObject>,RaplaType>();
            registeredTypeNames = new HashMap<String,RaplaType>();
        }
        if ( registeredTypes.get( clazz ) != null) {
            throw new IllegalStateException( "Type already registered");
        }
        registeredTypes.put( type, this);
        registeredTypeNames.put( clazz.getName(), this);
    }

    static public RaplaType find( String typeName) throws RaplaException 
    {
    	if ( typeName.contains("."))
    	{
        	RaplaType raplaType = registeredTypeNames.get( typeName);
        	if (raplaType != null)
        	{
        		return raplaType;
        	}
    	}
    	throw new RaplaException("Cant find Raplatype for name" + typeName);
    }
    
    static public <T extends RaplaObject> RaplaType<T> get(Class<T> clazz)
    {
    	@SuppressWarnings("unchecked")
		RaplaType<T> result = registeredTypes.get( clazz);
		return result;
    }
    
    public boolean is(RaplaType other) {
        if ( other == null)
            return false;
        return type.equals( other.type);
    }

    public String getLocalName() {
        return localname;
    }

    public String toString() {
        return type.getName();
    }

    public boolean equals( Object other) {
        if ( !(other instanceof RaplaType))
            return false;
        return is( (RaplaType)other);
    }

    public int hashCode() {
        return type.hashCode();
    }
    
    @SuppressWarnings("unchecked")
	public static  <T extends RaplaObject> Set<T> retainObjects(Collection<RaplaObject> set,Collection<T> col) {
	    HashSet<RaplaObject> tempSet = new HashSet<RaplaObject>(set.size());
	    tempSet.addAll(set);
	    tempSet.retainAll(col);
	    if (tempSet.size() >0)
	    {
	    	HashSet<T> result = new HashSet<T>();
	    	for ( RaplaObject t : tempSet)
	    	{
	    		result.add( (T)t);
	    	}
	    	return result;
	    }
	    else
	    {
	        return Collections.emptySet();
	    }
	}

}

