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

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.rapla.components.util.Assert;
import org.rapla.components.util.iterator.FilterIterator;
import org.rapla.components.util.iterator.IteratorChain;
import org.rapla.components.util.iterator.NestedIterator;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.ReadOnlyException;
import org.rapla.entities.configuration.RaplaMap;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.storage.CannotExistWithoutTypeException;
import org.rapla.entities.storage.DynamicTypeDependant;
import org.rapla.entities.storage.EntityReferencer;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.Mementable;
import org.rapla.entities.storage.RefEntity;
import org.rapla.entities.storage.internal.ReferenceHandler;

public class RaplaMapImpl<T> implements RaplaMap<T>, Serializable,EntityReferencer, DynamicTypeDependant,Mementable<RaplaMap<T>> {
   private static final long serialVersionUID = 1;

   //this map stores all objects in the map 
   private Map<String,T> map;
   
   // this map only stores the references
   private ReferenceHandler referenceHandler = new ReferenceHandler();
  
   // this map only stores the child objects (not the references)
   private Map<String,T> childMap = new HashMap<String,T>();
   
   public RaplaMapImpl() {
       this.map = new TreeMap<String,T>();
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
       this.map = Collections.unmodifiableMap(map);
       for ( Iterator<String> it = this.map.keySet().iterator();it.hasNext();) {
           String key = it.next();
           T o = this.map.get(key );
           if ( ! (o instanceof RaplaObject ) && !(o instanceof String) )
           {   
        	   throw new IllegalArgumentException("Only map entries of type RaplaObject or String are allowed.");
           }
           if ( o instanceof RefEntity) {
               getReferenceHandler().put( key, (RefEntity<?>)o);
           } else  {
               childMap.put( key, o );
           }
       }
   }

   
   /** This method is only used in storage operations, please dont use it from outside*/
   public void putPrivate(String key, T value)
   {
       childMap.put( key, value);
   }

   
   public Iterator<RefEntity<?>> getReferences() {
       Iterator<RefEntity<?>> refIt = new NestedIterator<RefEntity<?>>( getEntityReferencers()) {
           public Iterator<RefEntity<?>> getNestedIterator(Object obj) {
               return ((EntityReferencer)obj).getReferences();
           }
       };
       return new IteratorChain<RefEntity<?>>( refIt, getReferenceHandler().getReferences());
   }

   private Iterator<EntityReferencer> getEntityReferencers() {
       return new FilterIterator<EntityReferencer>( map.values().iterator()) {
           protected boolean isInIterator(Object obj) {
               return obj instanceof EntityReferencer;
           }
       };
   }


   public boolean isRefering(RefEntity<?> object) {
       if ( getReferenceHandler().isRefering( object )) {
           return true;
       }
       for (Iterator<EntityReferencer> it = getEntityReferencers();it.hasNext();) {
           if (it.next().isRefering( object)) {
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

   public void resolveEntities( EntityResolver resolver) throws EntityNotFoundException {
       referenceHandler.resolveEntities( resolver );
       this.map =  fillMap(resolver);
   }

   private Map<String, T> fillMap(EntityResolver resolver) throws EntityNotFoundException {
	   Map<String,T> map = new HashMap<String,T>();
       for ( String key:childMap.keySet()) 
       {
           T entity =  childMap.get(key) ;
           if (resolver != null && entity instanceof EntityReferencer) {
               ((EntityReferencer) entity).resolveEntities( resolver);
           }
           map.put( key, entity);
       }
       for ( String key: getReferenceHandler().getReferenceKeys()) {
           @SuppressWarnings("unchecked")
           T entity = (T) getReferenceHandler().get(key) ;
           Assert.notNull( entity );
           map.put( key, entity);
       }
       Map<String, T> unmodifiableMap = Collections.unmodifiableMap( map );
       return unmodifiableMap;
   }

   public ReferenceHandler getReferenceHandler() {
       return referenceHandler;
   }

    public RaplaType getRaplaType() {
        return TYPE;
    }

    public boolean needsChange(DynamicType type) {
        for (Iterator<T> it = childMap.values().iterator();it.hasNext();) {
            Object obj = it.next();
            if ( obj instanceof DynamicTypeDependant) {
                if (((DynamicTypeDependant) obj).needsChange( type ))
                    return true;
            }
        }
        return false;
    }

    public void commitChange(DynamicType type) {
        for (Iterator<T> it = childMap.values().iterator();it.hasNext();) {
            Object obj = it.next();
            if ( obj instanceof DynamicTypeDependant) {
                ((DynamicTypeDependant) obj).commitChange( type );
            }
        }
    }

    public void commitRemove(DynamicType type) throws CannotExistWithoutTypeException {
        for (Iterator<T> it = childMap.values().iterator();it.hasNext();) {
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
        return map.values();
    }

    /**
     * @see java.util.Map#entrySet()
     */
    public Set<Map.Entry<String, T>> entrySet() {
        return map.entrySet();
    }

    @SuppressWarnings("unchecked")
	static private <T> void copy(RaplaMapImpl<T> source,RaplaMapImpl<T> dest) {
    	dest.referenceHandler = (ReferenceHandler)source.referenceHandler.clone();
    	dest.childMap = new HashMap<String,T>();
    	for (Map.Entry<String, T> entry:source.childMap.entrySet())
    	{
    		String key = entry.getKey();
    		T value = entry.getValue();
    		T copy;
    		if ( value instanceof Mementable)
    		{
    			copy = ((Mementable<? extends T>)value).deepClone();
    		}
    		else
    		{
    			copy = value;
    		}
    		dest.childMap.put( key, copy);
    	}
    	try {
			dest.map = dest.fillMap(null);
		} catch (EntityNotFoundException e) {
			// Do nothing as we don't resolve entities
		}
    }
	
    
        /** Sets the attributes of the object implementing this interface 
     * to the attributes stored in the passed objects.
     */
    public void copy( RaplaMap<T> obj )
    {
    	copy( (RaplaMapImpl<T>)obj, this);
    	    	
    }
    
    /** Clones the entity and all subentities*/
    public RaplaMap<T> deepClone()
    {
    	RaplaMapImpl<T> clone = new RaplaMapImpl<T>();
    	copy( this,clone);
    	return clone;
    }

    /** Clones the entity while preserving the references to the subentities*/
    public RaplaMap<T> clone()
    {
    	return deepClone();    	
    }

	




}
