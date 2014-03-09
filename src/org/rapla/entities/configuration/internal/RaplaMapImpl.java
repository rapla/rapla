/*--------------------------------------------------------------------------*
| C
o//pyright (C) 2006 Christopher Kohlhaas                                  |
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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.rapla.components.util.iterator.FilterIterator;
import org.rapla.components.util.iterator.IteratorChain;
import org.rapla.components.util.iterator.NestedIterator;
import org.rapla.entities.Entity;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.ReadOnlyException;
import org.rapla.entities.configuration.CalendarModelConfiguration;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.entities.configuration.RaplaMap;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.storage.CannotExistWithoutTypeException;
import org.rapla.entities.storage.DynamicTypeDependant;
import org.rapla.entities.storage.EntityReferencer;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.internal.ReferenceHandler;

public class RaplaMapImpl implements Serializable,EntityReferencer, DynamicTypeDependant, RaplaObject, RaplaMap {
   //this map stores all objects in the map 
   private Map<String,String> constants;
   private Map<String,RaplaConfiguration> configurations;
   private Map<String,RaplaMapImpl> maps;
   private Map<String,CalendarModelConfigurationImpl> calendars;
   protected ReferenceHandler links;

   protected transient Map<String,Object> map;
   transient EntityResolver resolver;
   
   // this map only stores the references
  
   // this map only stores the child objects (not the references)
   
   public RaplaMapImpl() {
   }
   

   public RaplaMapImpl( Collection list) {
       this( makeMap(list) );
   }

   public RaplaType getRaplaType() {
       return RaplaMap.TYPE;
   }
  
   

   private  static <T> Map<String,T> makeMap(Collection<T> list) {
       Map<String,T> map = new TreeMap<String,T>();
       int key = 0;
       for ( Iterator<T> it = list.iterator();it.hasNext();) {
           map.put( new String( String.valueOf(key++)), it.next());
       }
       return map;
   }

   public RaplaMapImpl( Map<String,?> map) {
       for ( Iterator<String> it = map.keySet().iterator();it.hasNext();) {
           String key = it.next();
           Object o = map.get(key );
           putPrivate(key, o);
       }
   }

   
   /** This method is only used in storage operations, please dont use it from outside*/
   public void putPrivate(String key, Object value)
   {
	   cachedEntries = null;
	   if ( value == null)
	   {
		   if (links != null)
		   {
			   links.removeWithKey(key);
		   }
		   if ( maps != null)
		   {
			   maps.remove( key);
		   }
		   if ( map != null)
		   {
			   map.remove( key);
		   }
		   if ( configurations != null)
		   {
			   configurations.remove(key);
		   }
		   if ( maps != null)
		   {
			   maps.remove(key);
		   }
		   if ( calendars != null)
		   {
			   calendars.remove(key);
		   }
		   if ( constants != null)
		   {
			   constants.remove(key);
		   }
		   return;
	   }
	   if ( ! (value instanceof RaplaObject ) && !(value instanceof String) )
       {   
       }
       getMap().put(key, value);
       if ( value instanceof Entity) 
       {
    	   String id = ((Entity) value).getId();
    	   putIdPrivate(key, id);
    	   return;
       }
       else if ( value instanceof RaplaConfiguration) {
    	   if ( configurations == null)
    	   {
    		   configurations = new LinkedHashMap<String,RaplaConfiguration>();
    	   }
    	   configurations.put( key, (RaplaConfiguration) value);
       }
       else if ( value instanceof RaplaMap) {
    	   if ( maps == null)
    	   {
    		   maps = new LinkedHashMap<String,RaplaMapImpl>();
    	   }
    	   maps.put( key, (RaplaMapImpl) value);
       }
       else if ( value instanceof CalendarModelConfiguration) {
    	   if ( calendars == null)
    	   {
    		   calendars = new LinkedHashMap<String,CalendarModelConfigurationImpl>();
    	   }
    	   calendars.put( key, (CalendarModelConfigurationImpl) value);
       }
       else if ( value instanceof String) {
    	   if ( constants == null)
    	   {
    		   constants = new LinkedHashMap<String,String>();
    	   }
    	   constants.put( key , (String) value);
       } else {
    	   throw new IllegalArgumentException("Map type not supported only entities, maps, configuration  or Strings are allowed.");
       }
   }
 
   private Map<String, Object> getMap() {
	   if ( map == null)
	   {
		   map = new LinkedHashMap<String,Object>();
		   fillMap(maps);
		   fillMap(configurations);
		   fillMap(constants);
		   fillMap(calendars);
	   }
	   return map;
   }	


   private void fillMap(Map<String, ?> map) {
	   if ( map == null)
	   {
		   return;
	   }
	   this.map.putAll(  map);
   }
   public void putIdPrivate(String key, String id) {
	if ( links == null)
	   {
		   links = new ReferenceHandler();
		   if ( resolver != null)
		   {
			   links.setResolver( resolver);
		   }
			   
	   }
	   links.putId( key,id);
}

   public Iterable<String> getReferencedIds() {
	   NestedIterator<String,EntityReferencer> refIt = new NestedIterator<String,EntityReferencer>( getEntityReferencers()) {
           public Iterable<String> getNestedIterator(EntityReferencer obj) {
               return obj.getReferencedIds();
           }
       };
       if ( links == null)
       {
    	   return refIt;
       }
       return new IteratorChain<String>( refIt, links.getReferencedIds());
   }

   private Iterable<EntityReferencer> getEntityReferencers() {
       return new FilterIterator<EntityReferencer>( getMap().values()) {
           protected boolean isInIterator(Object obj) {
               return obj instanceof EntityReferencer;
           }
       };
   }


   public boolean isRefering(String object) {
       if ( links != null && links.isRefering( object )) {
           return true;
       }
       for (EntityReferencer ref:getEntityReferencers()) {
           if (ref.isRefering( object)) {
               return true;
           }
       }
       return false;
   }
   /*
   public Iterator getReferences() {
       return getReferenceHandler().getReferences();
   }

   public boolean isRefering(Entity entity) {
       return getReferenceHandler().isRefering( entity);
   }*/

   public void setResolver( EntityResolver resolver) {
	   this.resolver = resolver;
	   if ( links != null)
	   {
		   links.setResolver( resolver );
	   }
	   setResolver( calendars);
	   setResolver( maps );
   }


   
   private void setResolver(Map<String,? extends EntityReferencer> map) {
	   if ( map == null)
	   {
		   return;
	   }
	   for (EntityReferencer ref:map.values())
	   {
		   ref.setResolver( resolver);
	   }
   }


public Object get(Object key) {
       return  getMap().get(key);
   }

   
   /**
    * @see java.util.Map#clear()
    */
   public void clear() {
       throw createReadOnlyException();
   }

   protected ReadOnlyException createReadOnlyException() {
       return new ReadOnlyException("RaplaMap is readonly you must create a new Object");
   }
   
   /**
    * @see java.util.Map#size()
    */
   public int size() {
       return getMap().size();
   }

   /**
    * @see java.util.Map#isEmpty()
    */
   public boolean isEmpty() {
       return getMap().isEmpty();
   }

   /**
    * @see java.util.Map#containsKey(java.lang.Object)
    */
   public boolean containsKey(Object key) {
       return getMap().containsKey( key);
   }

   /**
    * @see java.util.Map#containsValue(java.lang.Object)
    */
   public boolean containsValue(Object key) {
       return getMap().containsValue( key);
   }

   /**
    * @see java.util.Map#keySet()
    */
   public Set<String> keySet() {
       return getMap().keySet();
   }

    public boolean needsChange(DynamicType type) {
        for (Iterator it = getMap().values().iterator();it.hasNext();) {
            Object obj = it.next();
            if ( obj instanceof DynamicTypeDependant) {
                if (((DynamicTypeDependant) obj).needsChange( type ))
                    return true;
            }
        }
        return false;
    }

    public void commitChange(DynamicType type) {
        for (Object obj:getMap().values()) {
            if ( obj instanceof DynamicTypeDependant) {
                ((DynamicTypeDependant) obj).commitChange( type );
            }
        }
    }

    public void commitRemove(DynamicType type) throws CannotExistWithoutTypeException {
    	for (Object obj:getMap().values()) {
    		if ( obj instanceof DynamicTypeDependant) {
                ((DynamicTypeDependant) obj).commitRemove( type );
            }
        }
    }
        /** Clones the entity and all subentities*/
    public RaplaMapImpl deepClone()
    {
    	RaplaMapImpl clone = new RaplaMapImpl(getMap());
    	clone.setResolver( resolver );
    	return clone;
    }

//	public Collection<RaplaObject> getLinkValues() 
//	{
//		ArrayList<RaplaObject> result = new ArrayList<RaplaObject>();
//		EntityResolver resolver = getReferenceHandler().getResolver();
//		for (String id: getReferencedIds())
//		{
//			result.add( resolver.tryResolve( id));
//		}
//		return result;
//
//	}

    
    /** Clones the entity while preserving the references to the subentities*/
    public Object clone()
    {
    	return deepClone();    	
    }


    /**
     * @see java.util.Map#put(java.lang.Object, java.lang.Object)
     */
    public Object put(Object key, Object value) {
    	throw createReadOnlyException();
    }


    public Object remove(Object arg0) {
        throw createReadOnlyException();
    }

    /**
     * @see java.util.Map#putAll(java.util.Map)
     */
	public void putAll(Map m) {
        throw createReadOnlyException();
    }
	

    /**
     * @see java.util.Map#values()
     */
    public Collection values() {
    	if ( links == null)
    	{
    		return  map.values();
    	}
    	else
    	{
    		List result = new ArrayList();
    		Iterable<String> values = links.getReferencedIds();
    		for (String id: values)
    		{
				Entity resolved = links.getResolver().tryResolve( id);
				result.add( resolved);
    		}
    		return result;
    	}
    }

    
    class Entry implements Map.Entry<String, Object>
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
    	
		public Object getValue() {
			if ( id == null)
			{
				return null;
			}
			Entity resolve = links.getResolver().tryResolve( id );
			return resolve;
		}

		public Object setValue(Object value) {
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
    
    transient Set<Map.Entry<String, Object>> cachedEntries; 
    public Set<Map.Entry<String, Object>> entrySet() {
		if ( links != null)
    	{
    		if ( cachedEntries == null)
    		{
    			cachedEntries = new HashSet<Map.Entry<String, Object>>();
    			for (String key:links.getReferenceKeys())
    			{
    				String id = links.getId( key);
					cachedEntries.add(new Entry( key, id));
    			}
    		}
    		return cachedEntries;
    	}
    	else
    	{
    		if ( cachedEntries == null)
    		{
    			cachedEntries = new HashSet<Map.Entry<String, Object>>();
    			for (Map.Entry<String,Object> entry:map.entrySet())
    			{
					cachedEntries.add((Map.Entry<String, Object>) entry);
    			}
    		}
    		return cachedEntries;
    	}
	}


}
