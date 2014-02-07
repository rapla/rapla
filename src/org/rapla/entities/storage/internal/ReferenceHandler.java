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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
public class ReferenceHandler implements EntityReferencer {
    
    private HashMap<String,List> map;
    //private ArrayList<ReferenceEntry> list;
    private transient boolean contextualizeCalled;

    // added for performance reasons
    private transient Set<RefEntity<?>> referenceList;

    /**
     * @see org.rapla.entities.storage.EntityReferencer#resolveEntities(org.rapla.entities.storage.EntityResolver)
     */
    public void resolveEntities(EntityResolver resolver) throws EntityNotFoundException {
// This is dangerous as resolveEntities could be called many times to link to a more recent clone
//        if ( contextualizeCalled )
//        {
//        	return;
//        }
		try {
		    if (map != null) {
		        for (String key :map.keySet()) {
		        	List newEntries = new ArrayList<>();
		        	List entries = map.get( key); 
		        	for (Object entry : entries)
		            {
		            	String id = getId(entry);
		            	Object entity = resolver.resolve(id);
						newEntries.add( entity);
		            }
		        	map.put( key, newEntries);
		        }
		    }
//		    if (list != null) {
//		    	for (ReferenceEntry entry:list) 
//		    	{
//		            entry.reference = resolver.resolve(entry.id);
//		        }
//		    }
		} catch (EntityNotFoundException ex) {
		    clearReferences();
		    throw ex;
		}
        contextualizeCalled = true;
        referenceList = null;
    }

	public String getId(Object entry) {
		String id;
		if ( entry instanceof RefEntity)
		{
			id = ((RefEntity)entry).getId();
		}
		else
		{
			id = (String) entry;
		}
		return id;
	}
    
    public List<Comparable> getReferencedIds()
    {
    	ArrayList<Comparable> result = new ArrayList<Comparable>();
    	if (map != null) {
            for (List entries:map.values()) {
                for ( Object entry: entries)
                {
                	Comparable id = getId( entry );
					result.add(id);
                }
            }
        }
//        if (list != null) {
//        	for (ReferenceEntry entry:this.list) 
//        	{
//        		result.add(entry.id);
//            }
//        }
        return result;
    }
    
    public boolean isContextualizeCalled() {
		return contextualizeCalled;
	}

    /** Use this method if you want to implement deserialization of the object manualy.
     * You have to add the reference-ids to other entities immediatly after the constructor.
     * @throws IllegalStateException if contextualize has been called before.
     */
//    public void addId(Comparable id) {
//        if (contextualizeCalled)
//            throw new IllegalStateException("Contextualize has been called before.");
//        synchronized (this) 
//        {
//	        if (list == null)
//	            list = new ArrayList<ReferenceEntry>(1);
//	        Assert.notNull(id);
//	
//	        ReferenceEntry entry = new ReferenceEntry();
//	        entry.id = id ;
//	        if ( list.contains( entry))
//	        {
//	            return;
//	        }
//	        list.add(entry);
//        }
//    }

    /** Use this method if you want to implement deserialization of the object manualy.
     * You have to add the reference-ids to other entities immediatly after the constructor.
     * @throws IllegalStateException if contextualize has been called before.
     */
    public void putId(String key,String id) {
    	putIds( key, Collections.singleton(id));
    }
    
    public void addId(String key,String id) {
        if (contextualizeCalled)
            throw new IllegalStateException("Contextualize has been called before.");
        addObject(key, id);
    }

    public void add(String key, RefEntity<?> entity) {
        addObject(key, entity);
	}

	private void addObject(String key, Object obj) {
		synchronized (this) 
        {
	        if (map == null)
	        {
	            map = new HashMap<String,List>(1);
	        }
	        List referenceEntries = map.get( key );
	        if ( referenceEntries == null )
	        {
	        	referenceEntries = new ArrayList<>();
	        	map.put(key, referenceEntries);
	        }
			referenceEntries.add(obj);
			referenceList = null;
        }
	}    

    public void putIds(String key,Collection<String> ids) {
        if (contextualizeCalled)
            throw new IllegalStateException("Contextualize has been called before.");
        synchronized (this) 
        {
	        if (map == null)
	            map = new HashMap<String,List>(1);
	
	        if (ids == null || ids.size() == 0) {
	            map.remove(key);
	            return;
	        }
	
	        List entries = new ArrayList();
	        for (Comparable id:ids)
	        {
	        	entries.add( id);
	        }
	        map.put(key, entries);
        }
        
    }
    
    public Object getId(String key)
    {
    	Object entry = _get(key);
    	if ( entry == null)
    	{
    		return null;
    	}
    	Object id = getId( entry);
		return id;
    }
    
	public Collection<String> getIds(String key) 
	{
		if (map == null)
			return Collections.emptyList();
		List entries  = map.get(key);
		if ( entries == null )
		{
			return Collections.emptyList();
		}
		ArrayList<String> entities = new ArrayList<String>(1);
		for ( Object entry: entries)
		{
			String id = getId( entry);
			entities.add(id);
		}
		return entities;
		
	}	

    public boolean removeWithKey(String key) {
    	synchronized (this) 
        {
        	if (map == null)
        		return false;
	        if  ( map.remove(key) != null ) {
	            referenceList = null;
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
	        	map = new HashMap<String,List>(1);
	        }
	        if (entity == null) {
	            map.remove(key);
	            return;
	        }
	        
	        map.put(key, Collections.singletonList(entity) );
	        referenceList = null;
        }
    }
    
    public void putList(String key, Collection<RefEntity<?>> entities) {
        synchronized (this) 
        {
	    	if (map == null)
	            map = new HashMap<String,List>(1);
     
            if (entities == null || entities.size() == 0) 
	        {
	            map.remove(key);
	            return;
	        }
	
	        List entries = new ArrayList(entities);
	        map.put(key,entries );
	        referenceList = null;
        }
    }
    

	public Collection<RefEntity<?>> getList(String key) 
	{
		if (map == null)
			return Collections.emptyList();

		List entries  = map.get(key);
		if ( entries == null )
		{
			return Collections.emptyList();
		}
		ArrayList<RefEntity<?>> entities = new ArrayList<RefEntity<?>>(1);
		for ( Object entry: entries)
		{
			entities.add((RefEntity<?>) entry);
		}
		return entities;
		
	}	

    public RefEntity<?> get(String key) {
        Object entry = _get(key);
        if ( entry == null)
        {
        	return null;
        }
        if ( !(entry instanceof RefEntity))
        {
        	return null;
        }
        return (RefEntity<?>) entry;
    }

	protected Object _get(String key) {
		if (map == null)
            return null;
        List entries  = map.get(key);
        if ( entries == null || entries.size() == 0)
        {
        	return null;
        }
        Object entry  = entries.get(0);
        if (entry == null)
            return null;
		return entry;
	}


//    public void add(RefEntity<?> entity) {
//        if (isRefering(entity))
//            return;
//        synchronized (this) 
//        {
//	        if (list == null)
//	            list = new ArrayList<ReferenceEntry>(1);
//	        ReferenceEntry entry = new ReferenceEntry();
//	        entry.id = entity.getId() ;
//	        entry.reference = entity;
//	        list.add(entry);
//	        referenceList = null;
//        }
//    }

    public boolean remove(RefEntity<?> entity) {
        if (!isRefering(entity)) {
            return false;
        }
        synchronized (this) 
        {
//	        if (list != null) {
//	            Iterator<ReferenceEntry> it = list.iterator();
//	            while (it.hasNext()) {
//	                ReferenceEntry entry =  it.next();
//	                if (entry.reference.equals(entity))
//	                    it.remove();
//	            }
//	        }
	        if (map != null) {
	            Iterator<String> it = map.keySet().iterator();
	            Map<String,List<Object>> toChange = new HashMap<String, List<Object>>();
	            while (it.hasNext()) {
	                String key = it.next();
					List entries = map.get(key);
	                boolean remove = false;
	                for (Object entry: entries)
	                {
	                	if (entry.equals(entity))
	                	{
	                		remove = true;
	                	}
	                }
	                if ( remove)
	                {
	                	List<Object> newEntries = new ArrayList<>();
	                	for (Object entry: entries)
	                	{
	                		if (!entry.equals(entity))
	                		{
	                			newEntries.add(entry);
	                        }
	                	}
	                	toChange.put(key, newEntries);
	                }
	            }
	            for ( Map.Entry<String, List<Object>> entry: toChange.entrySet())
	            {
	            	List<Object> values = entry.getValue();
	            	String key = entry.getKey();
	            	if ( values.size() == 0)
	            	{
	            		map.remove( key);
	            	}
	            	else
	            	{
	            		List array = values;
						map.put( key, array);
	            	}
	            }
	        }
	        referenceList = null;
	        return true;
        }
    }

    private Collection<RefEntity<?>> getReferenceList() {
        if (referenceList != null)
            return referenceList;
        synchronized (this) 
        {
	        Set<RefEntity<?>> referenceList = new LinkedHashSet<RefEntity<?>>(1);
//	        if (list != null) {
//	            Iterator<ReferenceEntry> it = list.iterator();
//	            while (it.hasNext()) {
//	                ReferenceEntry entry =  it.next();
//	                if (entry.reference == null)
//	                    throw new IllegalStateException("Contextualize was not called. References need to be resolved in context.");
//	                referenceList.add(entry.reference);
//	            }
//	        }
	        if (map != null) {
	            Iterator<String> it = map.keySet().iterator();
	            while (it.hasNext()) {
	                List entries =  map.get(it.next());
	                for (Object entry:entries)
	                {
		                if (!(entry  instanceof RefEntity))
		                    throw new IllegalStateException("Contextualize was not called. References need to be resolved in context.");
		                referenceList.add((RefEntity<?>)entry);
	                }
	            }
	        }
	        this.referenceList= referenceList;
        }
        return referenceList;
    }

    public boolean isRefering(RefEntity<?> obj) {
        if (/*list == null && */map == null)
            return false;
        Collection<RefEntity<?>> referenceList = getReferenceList();
        return referenceList.contains(obj);
    }

    @SuppressWarnings("unchecked")
	public Iterable<RefEntity<?>> getReferences() {
        if (/*list == null &&*/ map == null)
            return Collections.EMPTY_LIST;
        return getReferenceList();
    }
    
    public Iterable<String> getReferenceKeys() {
        if (map == null)
            return Collections.emptySet();
        return map.keySet();
    }
    
    public void clearReferences() {
        if (map != null)
            map.clear();
//        if (list != null)
//            list.clear();
        referenceList = null;
    }

    @SuppressWarnings("unchecked")
	public Object clone() {
        ReferenceHandler clone;
		clone = new ReferenceHandler();
		clone.contextualizeCalled = this.contextualizeCalled;
		clone.referenceList = this.referenceList;
		clone.map = this.map;
		if (map != null)
            clone.map = (HashMap<String,List>) map.clone();
//        clone.list = this.list;
//        if (list != null)
//            clone.list = (ArrayList<ReferenceEntry>) list.clone();
        
        return clone;
    }

//    class ReferenceEntry  {
//    	RefEntity<?> reference;
//        Comparable id;
//        public int hashCode() {
//            final int prime = 31;
//            int result = 1;
//            result = prime * result + ((id == null) ? 0 : id.hashCode());
//            return result;
//        }
//        
//        public boolean equals(Object obj)
//        {
//            if ( !(obj instanceof ReferenceEntry))
//            {
//                return false;
//            }
//            Object id2 = ((ReferenceEntry)obj).id;
//            if ( id2 == null)
//            {
//                return false;
//            }
//            return id2.equals( id);
//        }
//        
//        public String toString()
//        {
//        	if ( reference != null)
//        	{
//        		return reference.toString();
//        	}
//        	else if ( id != null)
//        	{
//        		return id.toString();
//        	}
//        	else
//        	{
//        		return "";
//        	}
//        }
//        
//    }

	public String toString()
	{
		StringBuilder builder = new StringBuilder();
		for (RefEntity<?> ref: getReferences())
		{
			builder.append(ref);
		}
	    return builder.toString();
	    
	}

    


}
