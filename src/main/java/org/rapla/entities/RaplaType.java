/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
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

import org.rapla.entities.configuration.CalendarModelConfiguration;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.entities.configuration.RaplaMap;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Period;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.storage.ImportExportEntity;
import org.rapla.facade.Conflict;
import org.rapla.framework.RaplaException;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Enumeration Pattern for all Rapla objects. You should not instanciate Objects of this type,
 * there is only one instance of RaplaType for each class of objects. You can get it via
 * the object interface. E.g. Reservation.TYPE or Allocatable.TYPE
 */
public class RaplaType<T>  {



    private Class<T> type;
    private String localname;
    private static final  Map<Class<? extends RaplaObject>,RaplaType> registeredTypes;
    private static final  Map<String,RaplaType> registeredTypeNames;

    static
    {
        registeredTypes = new HashMap<>();
        registeredTypeNames = new HashMap<>();
        new RaplaType<>(Reservation.class, "reservation");
        new RaplaType<>(Appointment.class, "appointment");
        new RaplaType<>(Conflict.class, "conflict");
        new RaplaType<>(Category.class, "category");
        new RaplaType<>(User.class, "user");
        new RaplaType<>(CalendarModelConfiguration.class, "calendar");
        new RaplaType<>(Preferences.class, "preferences");
        new RaplaType<>(RaplaConfiguration.class, "config");
        new RaplaType<>(RaplaMap.class, "map");
        new RaplaType<>(Allocatable.class, "resource");
        new RaplaType<>(Period.class, "period");
        new RaplaType<>(Attribute.class, "attribute");
        new RaplaType<>(DynamicType.class, "dynamictype");
        new RaplaType<>(ImportExportEntity.class, "importexport");
    }


	private RaplaType(Class<T> clazz, String localname) {
//        @SuppressWarnings("unchecked")
//		Class<? extends RaplaObject> clazz2 = clazz;
		this.type = clazz;
        this.localname = localname;
        @SuppressWarnings("unchecked")
		Class<? extends RaplaObject> casted = (Class<? extends RaplaObject>) type;
		registeredTypes.put( casted, this);
        registeredTypeNames.put( localname, this);
    }
	
    static public Class<? extends Entity> find( String typeName) throws RaplaException
    {
    	RaplaType raplaType = registeredTypeNames.get( typeName);
    	if (raplaType != null)
    	{
    		return raplaType.type;
    	}
    	throw new RaplaException("Cant find Raplatype for name" + typeName);
    }

    static public <T extends RaplaObject> String getLocalName(RaplaObject object)
    {
        return getLocalName(object.getTypeClass());
    }


    static public <T extends RaplaObject> String getLocalName(Class<T> clazz)
    {
        RaplaType<T> result = get( clazz);
        return result.localname;
    }

    static private <T extends RaplaObject> RaplaType<T> get(Class<T> clazz)
    {
    	@SuppressWarnings("unchecked")
		RaplaType<T> result = registeredTypes.get( clazz);
		return result;
    }

    public String toString() {
        return type.getName();
    }

    public int hashCode() {
        return type.hashCode();
    }

    @SuppressWarnings("unchecked")
	public static  <T extends RaplaObject> Set<T> retainObjects(Collection<? extends RaplaObject> set,Collection<T> col) {
	    HashSet<RaplaObject> tempSet = new HashSet<>(set.size());
	    tempSet.addAll(set);
	    tempSet.retainAll(col);
	    if (tempSet.size() >0)
	    {
	    	HashSet<T> result = new HashSet<>();
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

