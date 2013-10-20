/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Gereon Fassbender, Christopher Kohlhaas               |
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
package org.rapla.entities.storage.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.rapla.components.util.Assert;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.storage.EntityReferencer;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.RefEntity;

/** The ReferenceHandler takes care of serializing and deserializing references to Entity objects.
<p>
    The references will be serialized to the ids of the corresponding entity. Deserialization of
    the ids takes place in the contextualize method. You need to provide an EntityResolver on the Context.
</p>
<p>
The ReferenceHandler support both named and unnamed References. Use the latter one, if you don't need to refer to the particular reference by name and if you want to keep the order of the references.

<pre>

// put a named reference
referenceHandler.put("owner",user);

// put unnamed reference
Iterator it = resources.iterator();
while (it.hasNext())
   referenceHandler.add(it.next());

// returns
User referencedUser = referenceHandler.get("owner");

// returns both the owner and the resources
Itertor references = referenceHandler.getReferences();
</pre>

</p>
    @see EntityResolver
 */
public class ReferenceHandler implements EntityReferencer, java.io.Serializable, Cloneable{
    // Don't forget to increase the serialVersionUID when you change the fields
    private static final long serialVersionUID = 1;
    
    private HashMap<String,ReferenceEntry[]> map;
    private ArrayList<ReferenceEntry> list;
    private transient boolean contextualizeCalled;

    // added for performance reasons
    private transient boolean referencesUpToDate;
    private transient List<RefEntity<?>> referenceList;

    /**
     * @see org.rapla.entities.storage.EntityReferencer#resolveEntities(org.rapla.entities.storage.EntityResolver)
     */
    public void resolveEntities(EntityResolver resolver) throws EntityNotFoundException {
        try {
            if (map != null) {
                for (ReferenceEntry[] entries:map.values()) {
                    for ( ReferenceEntry entry: entries)
                    {
                    	resolve(resolver, entry);
                    }
                }
            }
            if (list != null) {
            	for (ReferenceEntry entry:list) 
            	{
                    resolve(resolver, entry);
                }
            }
        } catch (EntityNotFoundException ex) {
            clearReferences();
            throw ex;
        }
        contextualizeCalled = true;
        referencesUpToDate = false;
    }

	private void resolve(EntityResolver resolver, ReferenceEntry entry)
			throws EntityNotFoundException {
		try
		{
			entry.reference = resolver.resolve(entry.id);
		}
		catch (EntityNotFoundException ex)
		{
			// only throw exception if reference is not alread resolved
			if ( entry.reference == null)
			{
				throw ex;
			}
		}
	}
    
    public List<Comparable> getReferencedIds()
    {
    	ArrayList<Comparable> result = new ArrayList<Comparable>();
    	if (map != null) {
            for (ReferenceEntry[] entries:map.values()) {
                for ( ReferenceEntry entry: entries)
                {
                	if ( entry.id instanceof Comparable)
                	{
                		result.add((Comparable) entry.id);
                	}
                }
            }
        }
        if (list != null) {
        	for (ReferenceEntry entry:this.list) 
        	{
        		if ( entry.id instanceof Comparable)
            	{
            		result.add((Comparable) entry.id);
            	}
            }
        }
        return result;
    }
    
    /** Use this method if you want to implement deserialization of the object manualy.
     * You have to add the reference-ids to other entities immediatly after the constructor.
     * @throws IllegalStateException if contextualize has been called before.
     */
    public void addId(Object id) {
        if (contextualizeCalled)
            throw new IllegalStateException("Contextualize has been called before.");
        synchronized (this) 
        {
            if (list == null)
                list = new ArrayList<ReferenceEntry>(3);
            Assert.notNull(id);

            ReferenceEntry entry = new ReferenceEntry();
            entry.id = id ;
            if ( list.contains( entry))
            {
                return;
            }
            list.add(entry);		
		}
    
    }

    /** Use this method if you want to implement deserialization of the object manualy.
     * You have to add the reference-ids to other entities immediatly after the constructor.
     * @throws IllegalStateException if contextualize has been called before.
     */
    public void putId(String key,Object id) {
    	putIds( key, new Object[] {id});
    }
    
    public void putIds(String key,Object[] ids) {
    	synchronized (this) 
        {
	    	if (contextualizeCalled)
	            throw new IllegalStateException("Contextualize has been called before.");
	        if (map == null)
	            map = new HashMap<String,ReferenceEntry[]>(5);
	
	        if (ids == null || ids.length == 0) {
	            map.remove(key);
	            return;
	        }
	
	        ReferenceEntry[] entries = new ReferenceEntry[ids.length];
	        for (int i=0;i<ids.length;i++)
	        {
	        	ReferenceEntry entry = new ReferenceEntry();
	        	entry.id = ids[i];
	        	entries[i] = entry;
	        }
	        map.put(key, entries);
        }
    }
    
    public Object getId(String key)
    {
    	ReferenceEntry entry = _get(key);
    	if ( entry == null)
    	{
    		return null;
    	}
    	return entry.id;
    }

    public boolean removeId(String key) {
    	synchronized (this) 
        {
	    	if (map == null)
	            return false;
	        if  ( map.remove(key) != null ) {
	            referencesUpToDate = false;
	            return true;
	        } else {
	            return false;
	        }
        }
    }

    
//    private Object getId(String key) {
//        if (map == null)
//            throw new IllegalStateException("Map is empty.");
//        ReferenceEntry entry = (ReferenceEntry)map.get(key);
//        if (entry != null)
//            return entry.id;
//        throw new IllegalStateException("Key not found." + key);
//    }

    public void put(String key,RefEntity<?> entity) {
	   synchronized (this) 
       {
	    	if (map == null)
	        {
	        	if ( entity == null)
	        	{
	        		return;
	        	}
	        	map = new HashMap<String,ReferenceEntry[]>(5);
	        }
	        if (entity == null) {
	            map.remove(key);
	            return;
	        }
	        
	        ReferenceEntry entry = new ReferenceEntry();
	        entry.id = entity.getId() ;
	        entry.reference = entity;
	        map.put(key,new ReferenceEntry[] {entry} );
	        referencesUpToDate = false;
       }
    }
    
    public void putList(String key, Collection<RefEntity<?>> entities) {
    	synchronized ( this) 
    	{
			if (map == null)
	            map = new HashMap<String,ReferenceEntry[]>(5);
	        if (entities == null || entities.size() == 0) 
	        {
	            map.remove(key);
	            return;
	        }
	
	        ReferenceEntry[] entries = new ReferenceEntry[entities.size()];
	        int i=0;
	        for ( RefEntity<?> entity: entities)
	        {
		        ReferenceEntry entry = new ReferenceEntry();
		        entry.id = entity.getId() ;
		        entry.reference = entity;
		        entries[i++] = entry;
	        }
	        map.put(key,entries );
	        referencesUpToDate = false;
    	}
    	 
    }
    

	public Collection<RefEntity<?>> getList(String key) 
	{
		if (map == null)
			return null;
		ReferenceEntry[] entries  = map.get(key);
		if ( entries == null )
		{
			return null;
		}
		if ( entries.length == 0)
		{
			return Collections.emptyList();
		}
		if ( entries.length == 1)
		{
			RefEntity<?> first = entries[0].reference;
			List<?> singletonList = Collections.singletonList( first );
			Collection<RefEntity<?>> singleton = (Collection<RefEntity<?>>) singletonList;
			return singleton;
		}
		ArrayList<RefEntity<?>> entities = new ArrayList<RefEntity<?>>();
		for ( ReferenceEntry entry: entries)
		{
			entities.add(entry.reference);
		}
		return entities;
		
	}	

    public RefEntity<?> get(String key) {
        ReferenceEntry entry = _get(key);
        if ( entry == null)
        {
        	return null;
        }
        return entry.reference;
    }

	protected ReferenceEntry _get(String key) {
		if (map == null)
            return null;
        ReferenceEntry[] entries  = map.get(key);
        if ( entries == null || entries.length == 0)
        {
        	return null;
        }
        ReferenceEntry entry  = entries[0];
        if (entry == null)
            return null;
		return entry;
	}


	synchronized public void add(RefEntity<?> entity) {
        if (isRefering(entity))
            return;
        synchronized ( this ) 
        {
	        if (list == null)
	            list = new ArrayList<ReferenceEntry>(3);
	        ReferenceEntry entry = new ReferenceEntry();
	        entry.id = entity.getId() ;
	        entry.reference = entity;
	        list.add(entry);
	        referencesUpToDate = false;
        }
    }

    synchronized public boolean remove(RefEntity<?> entity) {

    	if (!isRefering(entity)) {
            return false;
        }
        synchronized ( this ) 
        {
	    	if (list != null) {
	            Iterator<ReferenceEntry> it = list.iterator();
	            while (it.hasNext()) {
	                ReferenceEntry entry =  it.next();
	                if (entry.reference.equals(entity))
	                    it.remove();
	            }
	        }
	        if (map != null) {
	            Iterator<String> it = map.keySet().iterator();
	            Map<String,Collection<ReferenceEntry>> toChange = new HashMap<String, Collection<ReferenceEntry>>();
	            while (it.hasNext()) {
	                String key = it.next();
					ReferenceEntry[] entries = map.get(key);
	                boolean remove = false;
	                for (ReferenceEntry entry: entries)
	                {
	                	if (entry.reference.equals(entity))
	                	{
	                		remove = true;
	                	}
	                }
	                if ( remove)
	                {
	                	Collection<ReferenceEntry> newEntries = new ArrayList<ReferenceHandler.ReferenceEntry>();
	                	for (ReferenceEntry entry: entries)
	                	{
	                		if (!entry.reference.equals(entity))
	                		{
	                			newEntries.add(entry);
	                        }
	                	}
	                	toChange.put(key, newEntries);
	                }
	            }
	            for ( Map.Entry<String, Collection<ReferenceEntry>> entry: toChange.entrySet())
	            {
	            	Collection<ReferenceEntry> values = entry.getValue();
	            	String key = entry.getKey();
	            	if ( values.size() == 0)
	            	{
	            		map.remove( key);
	            	}
	            	else
	            	{
	            		ReferenceEntry[] array = values.toArray(new ReferenceEntry[] {});
						map.put( key, array);
	            	}
	            }
	        }
	        referencesUpToDate = false;
	        return true;
        }
    }

    private Collection<RefEntity<?>> getReferenceList() {
        if (referencesUpToDate && referenceList != null)
            return referenceList;
        synchronized (this) 
        {
	        List<RefEntity<?>> referenceList = new ArrayList<RefEntity<?>>(1);
	        if (list != null) {
	            Iterator<ReferenceEntry> it = list.iterator();
	            while (it.hasNext()) {
	                ReferenceEntry entry =  it.next();
	                if (entry.reference == null)
	                    throw new IllegalStateException("Contextualize was not called. References need to be resolved in context.");
	                referenceList.add(entry.reference);
	            }
	        }
	        if (map != null) {
	            Iterator<String> it = map.keySet().iterator();
	            while (it.hasNext()) {
	                ReferenceEntry[] entries =  map.get(it.next());
	                for (ReferenceEntry entry:entries)
	                {
		                if (entry.reference == null)
		                    throw new IllegalStateException("Contextualize was not called. References need to be resolved in context.");
		                referenceList.add(entry.reference);
	                }
	            }
	        }
	        this.referenceList = referenceList;
	        referencesUpToDate = true;
	        return referenceList;
        }
    }

    public boolean isRefering(RefEntity<?> obj) {
        if (list == null && map == null)
            return false;
        Collection<RefEntity<?>> referenceList = getReferenceList();
        return referenceList.contains(obj);
    }

    @SuppressWarnings("unchecked")
	public Iterator<RefEntity<?>> getReferences() {
        if (list == null && map == null)
            return Collections.EMPTY_LIST.iterator();
        return getReferenceList().iterator();
    }
    
    public Iterable<String> getReferenceKeys() {
        if (map == null)
            return Collections.emptySet();
        return map.keySet();
    }
    
    public void clearReferences() {
        if (map != null)
            map.clear();
        if (list != null)
            list.clear();
        referencesUpToDate = false;
    }

    @SuppressWarnings("unchecked")
	public Object clone() {
        ReferenceHandler clone;
		try {
			clone = ((ReferenceHandler)super.clone());
		} catch (CloneNotSupportedException e) {
			throw new NullPointerException("Clone not supperted");
		}
        if (map != null)
            clone.map = (HashMap<String,ReferenceEntry[]>) map.clone();
        if (list != null)
            clone.list = (ArrayList<ReferenceEntry>) list.clone();
        
        return clone;
    }

    class ReferenceEntry implements java.io.Serializable {
        private static final long serialVersionUID = 1;
        transient RefEntity<?> reference;
        Object id;
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((id == null) ? 0 : id.hashCode());
            return result;
        }
        
        @Override
        public boolean equals(Object obj)
        {
            if ( !(obj instanceof ReferenceEntry))
            {
                return false;
            }
            Object id2 = ((ReferenceEntry)obj).id;
            if ( id2 == null)
            {
                return false;
            }
            return id2.equals( id);
        }
        
        public String toString()
        {
        	if ( reference != null)
        	{
        		return reference.toString();
        	}
        	else if ( id != null)
        	{
        		return id.toString();
        	}
        	else
        	{
        		return "";
        	}
        }
        
    }


	public String toString()
	{
		StringBuilder builder = new StringBuilder();
		Iterator<RefEntity<?>> it = getReferences();
		while ( it.hasNext())
		{
			builder.append(it.next());
		}
	    return builder.toString();
	    
	}
    
    


}
