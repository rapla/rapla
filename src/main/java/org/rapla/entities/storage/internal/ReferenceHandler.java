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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.rapla.entities.Entity;
import org.rapla.entities.storage.EntityReferencer;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.entities.storage.UnresolvableReferenceExcpetion;

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
abstract public class ReferenceHandler /*extends HashMap<String,List<String>>*/ implements EntityReferencer {
	protected Map<String,List<String>> links = new LinkedHashMap<String,List<String>>();
    protected transient EntityResolver resolver;
	
    public EntityResolver getResolver()
    {
    	return resolver;
    }
    
    public ReferenceHandler()
    {
    }
    
    public Map<String,?> getLinkMap()
    {
    	return links;
    }
    /**
     * @see org.rapla.entities.storage.EntityReferencer#setResolver(org.rapla.entities.storage.EntityResolver)
     */
    public void setResolver(EntityResolver resolver)  {
    	if (resolver == null){
    		throw new IllegalArgumentException("Null not allowed");
    	}
		this.resolver = resolver;
			
//    	try {
//	        for (String key :idmap.keySet()) {
//	        	List<String> ids = idmap.get( key); 
//	        	for (String id: ids)
//	            {
//	            	Entity entity = resolver.resolve(id);
//	            }
//	        }
//		} catch (EntityNotFoundException ex) {
//		    clearReferences();
//		    throw ex;
//		}
    }
    
    @Override
    public Iterable<ReferenceInfo> getReferenceInfo()
    {
        Set<ReferenceInfo> result = new HashSet<ReferenceInfo>();
        if (links != null) {
            for (String key:links.keySet()) {
                List<String> entries = links.get( key);
                for ( String id: entries)
                {
                    ReferenceInfo referenceInfo = new ReferenceInfo(id, getInfoClass( key));
                    result.add( referenceInfo);
                }
            }
        }
        return result;
    }

    protected <T extends Entity> ReferenceInfo<T> getReferenceFor(String key, Class<T> typeClass)
    {
        String id = getId(key);
        if ( id == null)
        {
            return null;
        }
        return getReferenceForId(id, typeClass);
    }

    protected <T extends Entity> ReferenceInfo<T> getReferenceForId(String id, Class<T> typeClass)
    {
        return new ReferenceInfo(id, typeClass);
    }
    
    abstract protected Class<? extends Entity> getInfoClass(String key);

    /** Use this method if you want to implement deserialization of the object manualy.
     * You have to add the reference-ids to other entities immediatly after the constructor.
     * @throws IllegalStateException if contextualize has been called before.
     */
    public void putId(String key,String id) {
    	putIds( key, Collections.singleton(id));
    }

    public void putId(String key,ReferenceInfo id) {
        if (id == null)
        {
            removeWithKey( key);
        }
        else
        {
            putIds( key, Collections.singleton(id.getId()));
        }
    }
    
    public void addId(String key,String id) {
    	synchronized (this) 
        {
	        List<String> idEntries = links.get( key );
	        if ( idEntries == null )
	        {
	        	idEntries = new ArrayList<String>();
	        	links.put(key, idEntries);
	        }
	        if ( idEntries.contains( id))
	        {
	            return;
	        }
			idEntries.add(id);
        }
    }

    public void add(String key, Entity entity) {
		synchronized (this) 
        {
			addId( key, entity.getId());
        }
	}
    
    public void putEntity(String key,Entity entity) {
        synchronized (this)
        {
            if (entity == null) {
                links.remove(key);
                return;
            }
            links.put(key, Collections.singletonList(entity.getId()) );
        }
    }

    public void putIds(String key,Collection<String> ids) {
        synchronized (this) 
        {
	        if (ids == null || ids.size() == 0) {
	            links.remove(key);
	            return;
	        }
	
	        List<String> entries = new ArrayList<String>();
			entries.addAll( ids);
	        links.put(key, entries);
        }
    }

    public void putReferences(String key,Collection<? extends ReferenceInfo> refs) {
        List<String> ids = new ArrayList<String>();
        for (ReferenceInfo ref:refs)
        {
            ids.add( ref.getId());
        }
        putIds(key, ids);
    }
    
    public String getId(String key)
    {
    	List<String> entries  = links.get(key);
    	if ( entries == null || entries.size() == 0)
    	{
    		return null;
    	}
    	String entry  = entries.get(0);
    	if (entry == null)
    		return null;
    	return entry;
    }

    public <T extends Entity> ReferenceInfo<T> getRef(String key,Class<T> clazz)
    {
        final String id = getId(key);
        if ( id == null)
        {
            return null;
        }
        return new ReferenceInfo<T>(id,clazz);
    }
    
	public Collection<String> getIds(String key) 
	{
		List<String> entries  = links.get(key);
		if ( entries == null )
		{
			return Collections.emptyList();
		}
		return entries;
	}
    
    public void putList(String key, Collection<Entity>entities) {
        synchronized (this) 
        {
            if (entities == null || entities.size() == 0) 
	        {
	            links.remove(key);
	            return;
	        }
	        List<String> idEntries = new ArrayList<String>();
	        for (Entity ent: entities)
	        {
	        	String id = ent.getId();
				idEntries.add( id);
	        }
	        links.put(key, idEntries);
        }
    }
    
    public <T extends Entity> Collection<T> getList(String key, Class<T> entityClass) 
	{
		List<String> ids  = links.get(key);
		if ( ids == null )
		{
			return Collections.emptyList();
		}
		List<T> entries = new ArrayList<T>(ids.size());
		for ( String id:ids)
		{
			T entity = tryResolve(id, entityClass);
			if ( entity != null)
			{
				entries.add( entity );
			}
			else
			{
				throw new UnresolvableReferenceExcpetion( entityClass.getName() + ":" + id, toString() );
			}
		}
		return Collections.unmodifiableCollection(entries);
	}

	protected <T extends Entity> T tryResolve(String id,Class<T> entityClass) 
	{
        return resolver.tryResolve( id , entityClass);
	}	

//    public Entity getEntity(String key) {
//    	
//    }
    
    public <T extends Entity> T getEntity(String key,Class<T> entityClass)
    {
        List<String>entries  = links.get(key);
        if ( entries == null || entries.size() == 0)
        {
           return null;
        }
        String id  = entries.get(0);
        if (id == null)
            return null;
        if ( resolver == null)
        {
            throw new IllegalStateException("Resolver not set");
        }
        T resolved = tryResolve(id, entityClass);
        if ( resolved == null)
        {
            throw new UnresolvableReferenceExcpetion(entityClass.getName() + ":" + id);
        }
        return resolved;
    }

	public boolean removeWithKey(String key) {
    	synchronized (this) 
        {
			return links.remove(key) != null;
	    }
    }

    public boolean removeId(String id) {
        boolean removed = false;
    	synchronized (this) 
        {
        	for (String key: links.keySet())
        	{
				List<String> entries = links.get(key);
				if ( entries.contains( id))
				{
					entries.remove( id);
					removed = true;
				}
        	}
        }
        return removed;
    }

    // TODO if list is performant for many reference, e.g. hasAllocated
    protected boolean isRefering(String key,String id) {
        List<String> ids  = links.get(key);
        if ( ids == null)
        {
            return false;
        }
        return ids.contains( id);
    }

    public Iterable<String> getReferenceKeys() {
        return links.keySet();
    }
    
    public void clearReferences() {
    	links.clear();
    }

//    @SuppressWarnings("unchecked")
//	public ReferenceHandler clone() {
//        ReferenceHandler clone;
//    }

    @Override
    public void replace(ReferenceInfo origId, ReferenceInfo newId)
    {
        final Collection<Entry<String, List<String>>> entries = links.entrySet();
        for (Entry<String, List<String>> entry : entries)
        {
            final List<String> list = entry.getValue();
            if(list.contains(origId.getId()))
            {
                final ArrayList<String> copy = new ArrayList<>(list);
                final int indexOf = copy.indexOf(origId.getId());
                copy.remove(origId.getId());
                if(!copy.contains(newId.getId()))
                {
                    copy.add(indexOf, newId.getId());
                }
                links.put(entry.getKey(), copy);
            }
        }
    }

	public String toString()
	{
		StringBuilder builder = new StringBuilder();
		for (ReferenceInfo ref: getReferenceInfo())
		{
			builder.append(ref);
			builder.append(",");
		}
	    return builder.toString();
	}

}
