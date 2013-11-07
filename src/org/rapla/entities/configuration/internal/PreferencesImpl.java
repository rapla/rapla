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
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.storage.CannotExistWithoutTypeException;
import org.rapla.entities.storage.DynamicTypeDependant;
import org.rapla.entities.storage.EntityReferencer;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.Mementable;
import org.rapla.entities.storage.RefEntity;
import org.rapla.entities.storage.internal.SimpleEntity;
import org.rapla.framework.TypedComponentRole;

public class PreferencesImpl extends SimpleEntity<Preferences>
    implements
        Preferences
        , DynamicTypeDependant
{
    HashMap<String,Object> map = new HashMap<String,Object>();
    
    final public RaplaType<Preferences> getRaplaType() {return TYPE;}
    
    public void putEntry(String role,RaplaObject entry) {
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
    
    public void resolveEntities( EntityResolver resolver) throws EntityNotFoundException {
        super.resolveEntities( resolver);
        for (Object obj:getEntityReferencers())
        {
            ((EntityReferencer) obj).resolveEntities( resolver);
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

    public Iterable<RefEntity<?>> getReferences() {
        return new NestedIterator<RefEntity<?>>( getEntityReferencers() ) {
            public Iterable<RefEntity<?>> getNestedIterator(Object obj) {
                return ((EntityReferencer) obj).getReferences();
            }
        };
    }
    
    public boolean isRefering(RefEntity<?> object) {
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
    
    static private void copy(PreferencesImpl source,PreferencesImpl dest) {
        HashMap<String,Object> map = new HashMap<String,Object>();
        for (Iterator<String> it = source.map.keySet().iterator();it.hasNext();)
        {
            String role =  it.next();
            Object entry = source.map.get( role );
            Object clone;
            if (entry instanceof Mementable )
            {
            	clone = ((Mementable<?>) entry).deepClone();
            }
            else 
            {
            	clone = entry;
            }
            map.put( role , clone);
        }
        dest.map = map;
    }

    @SuppressWarnings("unchecked")
	public void copy(Preferences obj) {
    	synchronized ( this) {
            super.copy((SimpleEntity<Preferences>) obj);
            copy((PreferencesImpl) obj,this);
		}
    }

    public Preferences deepClone() {
        PreferencesImpl clone = new PreferencesImpl();
        super.deepClone(clone);
        copy(this,clone);
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
		putEntry( role.getId(), entry);
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












