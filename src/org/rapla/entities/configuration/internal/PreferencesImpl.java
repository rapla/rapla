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
package org.rapla.entities.configuration.internal;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;

import org.rapla.components.util.iterator.FilterIterator;
import org.rapla.components.util.iterator.NestedIterator;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.configuration.CalendarModelConfiguration;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.entities.configuration.RaplaMap;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.storage.CannotExistWithoutTypeException;
import org.rapla.entities.storage.DynamicTypeDependant;
import org.rapla.entities.storage.EntityReferencer;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.internal.ReferenceHandler;
import org.rapla.entities.storage.internal.SimpleEntity;
import org.rapla.framework.TypedComponentRole;

public class PreferencesImpl extends SimpleEntity
    implements
        Preferences
        , DynamicTypeDependant
{
    HashMap<String,Object> map = new HashMap<String,Object>();
    
    final public RaplaType<Preferences> getRaplaType() {return TYPE;}
    
    public PreferencesImpl() {
    	super();
    }
    
    @Override
    public void putEntry(TypedComponentRole<CalendarModelConfiguration> role, CalendarModelConfiguration entry) {
    	putEntryPrivate(role.getId(), entry);
    }
 
    @Override
    public void putEntry(TypedComponentRole<RaplaConfiguration> role, RaplaConfiguration entry) {
    	putEntryPrivate(role.getId(), entry);
    }
    
    @Override
    public <T> void putEntry(TypedComponentRole<RaplaMap<T>> role, RaplaMap<T> entry) {
    	putEntryPrivate(role.getId(), entry);
    }
    
    public void putEntryPrivate(String role,RaplaObject entry) {
        checkWritable();
        if ( entry == null)
        {
            map.remove( role);
        }
        else
        {
        	if (entry instanceof EntityReferencer)
        	{
        		setResolver(((ReferenceHandler)getEntityReferencers()).getResolver());
        	}
        	map.put( role ,entry);
        }
    }
    
    public void putEntry(String role,String entry) {
        checkWritable();
        if ( entry == null)
        {
            map.remove( role);
        }
        else
        {
            map.put( role ,entry);
        }
    }
    
    public void setResolver( EntityResolver resolver)  {
        super.setResolver( resolver);
        for (Object obj:getEntityReferencers())
        {
            ((EntityReferencer) obj).setResolver( resolver);
        }
    }
        
    public <T> T getEntry(String role) {
        return getEntry(role, null);
    }
    
    public <T> T getEntry(String role, T defaultValue) {
        try
        {
            @SuppressWarnings("unchecked")
            T result = (T) map.get( role );
            if ( result == null)
            {
            	return defaultValue;
            }
            return  result;
        }
        catch ( ClassCastException ex)
        {
            throw new ClassCastException( "Stored entry is not of requested Type: "  + ex.getMessage());
        }
    }


    public String getEntryAsString(String role) {
        return (String) map.get( role );
    }

    public String getEntryAsString(String role, String defaultValue) {
        String value = getEntryAsString( role);
        if ( value != null)
            return value;
        return defaultValue;
    }

    public Iterator<String> getPreferenceEntries() {
        return map.keySet().iterator();
    }

    private Iterable<EntityReferencer> getEntityReferencers() {
        return new FilterIterator<EntityReferencer>( map.values()) {
            protected boolean isInIterator(Object obj) {
                return obj instanceof EntityReferencer;
            }
        };
    }

    public Iterable<String> getReferencedIds() {
        return new NestedIterator<String,EntityReferencer>( getEntityReferencers() ) {
            public Iterable<String> getNestedIterator(EntityReferencer obj) {
                return  obj.getReferencedIds();
            }
        };
    }
    
    public boolean isRefering(String object) {
        for (EntityReferencer entityReferencer:getEntityReferencers()) {
            if (entityReferencer.isRefering( object)) {
                return true;
            }
        }
        return false;
    }

    public boolean isEmpty() {
        return map.keySet().isEmpty();
    }
    

    public PreferencesImpl clone() {
        PreferencesImpl clone = new PreferencesImpl();
        super.deepClone(clone);
        HashMap<String,Object> map = new HashMap<String,Object>();
        for (Iterator<String> it = map.keySet().iterator();it.hasNext();)
        {
            String role =  it.next();
            Object entry = map.get( role );
            Object clone1;
            // fixme need to clone not 
            if (entry instanceof RaplaObject )
            {
            	clone1 = ((RaplaObject) entry).clone();
            }
            else 
            {
            	clone1 = entry;
            }
            map.put( role , clone1);
        }
        clone.map = map;
        return clone;
    }

    /**
     * @see org.rapla.entities.Named#getName(java.util.Locale)
     */
    public String getName(Locale locale) {
        StringBuffer buf = new StringBuffer();
        if ( getOwner() != null) {
            buf.append( "Preferences of ");
            buf.append( getOwner().getName( locale));
        } else {
            buf.append( "Rapla Preferences!");
        }
        return buf.toString();
    }
	/* (non-Javadoc)
	 * @see org.rapla.entities.configuration.Preferences#getEntryAsBoolean(java.lang.String, boolean)
	 */
	public boolean getEntryAsBoolean(String role, boolean defaultValue) {
		String entry = getEntryAsString( role);
		if ( entry == null)
			return defaultValue;
		return Boolean.valueOf(entry).booleanValue();
	}
    
	/* (non-Javadoc)
	 * @see org.rapla.entities.configuration.Preferences#getEntryAsInteger(java.lang.String, int)
	 */
	public int getEntryAsInteger(String role, int defaultValue) {
		String entry = getEntryAsString( role);
		if ( entry == null)
			return defaultValue;
		return Integer.parseInt(entry);
	}
    
    public boolean needsChange(DynamicType type) {
        for (Iterator<Object> it = map.values().iterator();it.hasNext();) {
            Object obj = it.next();
            if ( obj instanceof DynamicTypeDependant) {
                if (((DynamicTypeDependant) obj).needsChange( type ))
                    return true;
            }
        }
        return false;
    }
    
    public void commitChange(DynamicType type) {
        for (Iterator<Object> it = map.values().iterator();it.hasNext();) {
            Object obj = it.next();
            if ( obj instanceof DynamicTypeDependant) {
                ((DynamicTypeDependant) obj).commitChange( type );
            }
        }
    }


    public void commitRemove(DynamicType type) throws CannotExistWithoutTypeException 
    {
        for (Iterator<Object> it = map.values().iterator();it.hasNext();) {
            Object obj = it.next();
            if ( obj instanceof DynamicTypeDependant) {
                ((DynamicTypeDependant) obj).commitRemove( type );
            }
        } 
    }

    
    public String toString()
    {
        return map.toString();
    }

	public <T extends RaplaObject> void putEntry(TypedComponentRole<T> role,
			T entry) {
		putEntryPrivate( role.getId(), entry);
	}

	public <T extends RaplaObject> T getEntry(TypedComponentRole<T> role) {
		return getEntry( role, null);
	}
	
	public <T extends RaplaObject> T getEntry(TypedComponentRole<T> role, T defaultValue) {
		return getEntry( role.getId(), defaultValue);
	}

	public boolean hasEntry(TypedComponentRole<?> role) {
		return map.get( role.getId() ) != null;
	}

	public void putEntry(TypedComponentRole<Boolean> role, Boolean entry) {
		putEntry_(role, entry);
	}
	
	public void putEntry(TypedComponentRole<Integer> role, Integer entry) 
	{
		putEntry_(role, entry);
	}

	public void putEntry(TypedComponentRole<String> role, String entry) {
		putEntry_(role, entry);
	}
	
	protected void putEntry_(TypedComponentRole<?> role, Object entry) {
		checkWritable();
		String id = role.getId();
        if ( entry == null)
        {
            map.remove( id);
        }
        else
        {
            map.put( id ,entry.toString());
        }
	}

	public String getEntryAsString(TypedComponentRole<String> role,
			String defaultValue) {
		return getEntryAsString(role.getId(), defaultValue);
	}

	public Boolean getEntryAsBoolean(TypedComponentRole<Boolean> role,
			boolean defaultValue) {
		return getEntryAsBoolean(role.getId(), defaultValue);
	}

	public Integer getEntryAsInteger(TypedComponentRole<Integer> role,
			int defaultValue) {
		return getEntryAsInteger(role.getId(), defaultValue);
	}
}












