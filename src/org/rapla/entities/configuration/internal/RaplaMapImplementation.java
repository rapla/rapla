/*--------------------------------------------------------------------------*
| Co//pyright (C) 2006 Christopher Kohlhaas                                  |
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.rapla.entities.Entity;
import org.rapla.entities.configuration.RaplaMap;

public class RaplaMapImplementation<T> extends RaplaMapImpl implements RaplaMap<T> 
{
	public RaplaMapImplementation() {
		super();
	}
	
	public RaplaMapImplementation( Map<String,T> map) {
	   super(map);
   }

   
    public RaplaMapImplementation(Collection<T> col) {
    	super(col);
    }

	
    /**
     * @see java.util.Map#get(java.lang.Object)
     */
    public T get(Object key) {
        return (T) super.get(key);
    }

    /**
     * @see java.util.Map#put(java.lang.Object, java.lang.Object)
     */
    public T put(String key, T value) {
    	throw createReadOnlyException();
    }


    public T remove(Object arg0) {
        throw createReadOnlyException();
    }

    /**
     * @see java.util.Map#putAll(java.util.Map)
     */
	public void putAll(Map<? extends String, ? extends T> m) {
        throw createReadOnlyException();
    }

	

    /**
     * @see java.util.Map#values()
     */
    public Collection<T> values() {
    	if ( links == null)
    	{
    		return (Collection<T>) map.values();
    	}
    	else
    	{
    		List<T> result = new ArrayList();
    		Collection<List<String>> values = links.values();
    		for (List<String> list: values)
    		{
    			if ( list!= null && list.size() > 0 )
    			{
    				String id = list.get(0);
    				Entity resolved = getReferenceHandler().getResolver().tryResolve( id);
    				result.add((T) resolved);
    			}
    		}
    		return result;
    	}
    }

    @Override
    public void putPrivate(String key, Object value) {
    	super.putPrivate(key, value);
    	cachedEntries = null;
    }
    
    class Entry implements Map.Entry<String, T>
    {
    	String key;
    	String id;
    	
    	Entry(String key,String id)
    	{
    		this.key = key;
    		this.id = id;
    	}
    	public String getKey() {
			return key;
		}
    	
		public T getValue() {
			if ( id == null)
			{
				return null;
			}
			Entity resolve = getReferenceHandler().getResolver().tryResolve( id );
			return (T) resolve;
		}

		public T setValue(T value) {
			throw new UnsupportedOperationException();
		}
		
		public int hashCode() {
			return key.hashCode();
		}
		
		@Override
		public boolean equals(Object obj) {
			return key.equals( ((Entry)  obj).key);
		}
    }
    
    transient Set<Map.Entry<String, T>> cachedEntries; 
    public Set<Map.Entry<String, T>> entrySet() {
		if ( links != null)
    	{
    		if ( cachedEntries != null)
    		{
    			cachedEntries = new HashSet<Map.Entry<String, T>>();
    			for (Map.Entry<String,List<String>> entry:links.entrySet())
    			{
    				String key = entry.getKey();
    				List<String> list = entry.getValue();
    				String id = (list== null || list.size() == 0) ? null: list.get( 0);
					cachedEntries.add(new Entry( key, id));
    			}
    		}
    		return cachedEntries;
    	}
    	else
    	{
    		if ( cachedEntries != null)
    		{
    			cachedEntries = new HashSet<Map.Entry<String, T>>();
    			for (Map.Entry<String,Object> entry:map.entrySet())
    			{
					cachedEntries.add((Map.Entry<String, T>) entry);
    			}
    		}
    		return cachedEntries;
    	}
	}

    @Override
    public RaplaMapImpl deepClone()
    {
    	RaplaMapImplementation clone = new RaplaMapImplementation(map);
    	clone.setResolver( getReferenceHandler().getResolver());
    	return clone;
    }

}
