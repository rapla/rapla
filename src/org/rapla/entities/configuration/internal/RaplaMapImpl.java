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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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

public class RaplaMapImpl<T> implements RaplaMap<T>, Serializable,EntityReferencer, DynamicTypeDependant {
   private static final long serialVersionUID = 1;
   //this map stores all objects in the map 
   private Map<String,List<String>> links = new LinkedHashMap<String,List<String>>();
   private Map<String,String> constants;
   private Map<String,RaplaConfiguration> configurations;
   private Map<String,RaplaMapImpl> maps;
   private Map<String,CalendarModelConfigurationImpl> calendars;

   private transient Map<String,T> map;
   
   // this map only stores the references
   private ReferenceHandler referenceHandler;
  
   // this map only stores the child objects (not the references)
   
   public RaplaMapImpl() {
   }

   public RaplaMapImpl( Collection<T> list) {
       this( makeMap(list) );
   }

   private  static <T> Map<String,T> makeMap(Collection<T> list) {
       Map<String,T> map = new TreeMap<String,T>();
       int key = 0;
       for ( Iterator<T> it = list.iterator();it.hasNext();) {
           map.put( new String( String.valueOf(key++)), it.next());
       }
       return map;
   }

   public RaplaMapImpl( Map<String,T> map) {
       for ( Iterator<String> it = this.map.keySet().iterator();it.hasNext();) {
           String key = it.next();
           T o = this.map.get(key );
          putPrivate(key, o);
       }
       	referenceHandler = new ReferenceHandler(links);
   }

   
   /** This method is only used in storage operations, please dont use it from outside*/
   public void putPrivate(String key, T value)
   {
	   if ( ! (value instanceof RaplaObject ) && !(value instanceof String) )
       {   
       }
       if ( value instanceof Entity) {
    	   if ( links == null)
    	   {
    		   links = new LinkedHashMap<>();
    	   }
    	   links.put(key, Collections.singletonList( ((Entity) value).getId()));
       }
       else if ( value instanceof RaplaConfiguration) {
    	   configurations.put( key, (RaplaConfiguration) value);
       }
       else if ( value instanceof RaplaMap) {
    	   maps.put( key, (RaplaMapImpl) value);
       }
       else if ( value instanceof CalendarModelConfiguration) {
    	   calendars.put( key, (CalendarModelConfigurationImpl) value);
       }
       else if ( value instanceof String) {
    	   constants.put( key , (String) value);
       } else {
    	   throw new IllegalArgumentException("Map type not supported only entities, maps, configuration  or Strings are allowed.");
       }
   }

   public Iterable<String> getReferencedIds() {
	   NestedIterator<String,EntityReferencer> refIt = new NestedIterator<String,EntityReferencer>( getEntityReferencers()) {
           public Iterable<String> getNestedIterator(EntityReferencer obj) {
               return obj.getReferencedIds();
           }
       };
       return new IteratorChain<String>( refIt, getReferenceHandler().getReferencedIds());
   }

   private Iterable<EntityReferencer> getEntityReferencers() {
       return new FilterIterator<EntityReferencer>( map.values()) {
           protected boolean isInIterator(Object obj) {
               return obj instanceof EntityReferencer;
           }
       };
   }


   public boolean isRefering(String object) {
       if ( getReferenceHandler().isRefering( object )) {
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
       referenceHandler.setResolver( resolver );
       this.map =  fillMap(resolver);
   }

   private Map<String, T> fillMap(EntityResolver resolver)  {
	   Map<String,T> map = new LinkedHashMap<String,T>();
	   fillMap(maps);
	   fillMap(configurations);
	   fillMap(constants);
	   fillMap(calendars);
       return map;
   }

   private void fillMap(Map<String, ?> map) {
	   if ( map == null)
	   {
		   return;
	   }
	   this.map.putAll( (Map<? extends String, ? extends T>) map);
   }

public ReferenceHandler getReferenceHandler() {
       return referenceHandler;
   }

    public RaplaType getRaplaType() {
        return TYPE;
    }

    public boolean needsChange(DynamicType type) {
        for (Iterator<T> it = map.values().iterator();it.hasNext();) {
            Object obj = it.next();
            if ( obj instanceof DynamicTypeDependant) {
                if (((DynamicTypeDependant) obj).needsChange( type ))
                    return true;
            }
        }
        return false;
    }

    public void commitChange(DynamicType type) {
        for (Iterator<T> it = map.values().iterator();it.hasNext();) {
            Object obj = it.next();
            if ( obj instanceof DynamicTypeDependant) {
                ((DynamicTypeDependant) obj).commitChange( type );
            }
        }
    }

    public void commitRemove(DynamicType type) throws CannotExistWithoutTypeException {
        for (Iterator<T> it = map.values().iterator();it.hasNext();) {
            Object obj = it.next();
            if ( obj instanceof DynamicTypeDependant) {
                ((DynamicTypeDependant) obj).commitRemove( type );
            }
        }
    }
    /**
     * @see java.util.Map#size()
     */
    public int size() {
        return map.size();
    }

    /**
     * @see java.util.Map#isEmpty()
     */
    public boolean isEmpty() {
        return map.isEmpty();
    }

    /**
     * @see java.util.Map#containsKey(java.lang.Object)
     */
    public boolean containsKey(Object key) {
        return map.containsKey( key);
    }

    /**
     * @see java.util.Map#containsValue(java.lang.Object)
     */
    public boolean containsValue(Object key) {
        return map.containsValue( key);
    }

    /**
     * @see java.util.Map#get(java.lang.Object)
     */
    public T get(Object key) {
        return map.get(key);
    }

    /**
     * @see java.util.Map#put(java.lang.Object, java.lang.Object)
     */
    public T put(String key, T value) {
        throw createReadOnlyException();
    }

    /**
     * @see java.util.Map#remove(java.lang.Object)
     */
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
     * @see java.util.Map#clear()
     */
    public void clear() {
        throw createReadOnlyException();
    }

    ReadOnlyException createReadOnlyException() {
        return new ReadOnlyException("RaplaMap is readonly you must create a new Object");
    }
    /**
     * @see java.util.Map#keySet()
     */
    public Set<String> keySet() {
        return map.keySet();
    }

    /**
     * @see java.util.Map#values()
     */
    public Collection<T> values() {
    	if ( links == null)
    	{
    		return map.values();
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
    
    transient Set<Map.Entry<String, T>> linkEntries; 
    /**
     * @see java.util.Map#entrySet()
     */
    public Set<Map.Entry<String, T>> entrySet() {
    	if ( links != null)
    	{
    		if ( linkEntries != null)
    		{
    			linkEntries = new HashSet<Map.Entry<String, T>>();
    			for (Map.Entry<String,List<String>> entry:links.entrySet())
    			{
    				String key = entry.getKey();
    				List<String> list = entry.getValue();
    				String id = (list== null || list.size() == 0) ? null: list.get( 0);
					linkEntries.add(new Entry( key, id));
    			}
    		}
    		return linkEntries;
    	}
    	else
    	{
    		return map.entrySet();
    	}
    }

    
    /** Clones the entity and all subentities*/
    public RaplaMap<T> deepClone()
    {
    	RaplaMapImpl<T> clone = new RaplaMapImpl<T>(this);
    	clone.setResolver( getReferenceHandler().getResolver());
    	return clone;
    }

    /** Clones the entity while preserving the references to the subentities*/
    public RaplaMap<T> clone()
    {
    	return deepClone();    	
    }

	




}
