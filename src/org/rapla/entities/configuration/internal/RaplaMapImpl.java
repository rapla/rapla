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
import java.util.Collection;
import java.util.Collections;
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

public class RaplaMapImpl implements Serializable,EntityReferencer, DynamicTypeDependant, RaplaObject {
   //this map stores all objects in the map 
   protected Map<String,List<String>> links = new LinkedHashMap<String,List<String>>();
   private Map<String,String> constants;
   private Map<String,RaplaConfiguration> configurations;
   private Map<String,RaplaMapImpl> maps;
   private Map<String,CalendarModelConfigurationImpl> calendars;

   protected transient Map<String,Object> map;
   
   // this map only stores the references
   transient private ReferenceHandler referenceHandler;
  
   // this map only stores the child objects (not the references)
   
   public RaplaMapImpl() {
      	referenceHandler = new ReferenceHandler(links);
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
       for ( Iterator<String> it = this.map.keySet().iterator();it.hasNext();) {
           String key = it.next();
           Object o = this.map.get(key );
          putPrivate(key, o);
       }
       	referenceHandler = new ReferenceHandler(links);
   }

   
   /** This method is only used in storage operations, please dont use it from outside*/
   public void putPrivate(String key, Object value)
   {
	   if ( value == null)
	   {
		   if ( links != null)
		   {
			   links.remove(key);
		   }
		   if ( maps != null)
		   {
			   maps.remove( key);
		   }
		   if ( map != null)
		   {
			   map.remove( key);
		   }
		   return;
	   }
	   if ( ! (value instanceof RaplaObject ) && !(value instanceof String) )
       {   
       }
       if ( value instanceof Entity) {
    	   if ( links == null)
    	   {
    		   links = new LinkedHashMap<>();
    	   }
    	   links.put(key, Collections.singletonList( ((Entity) value).getId()));
    	   return;
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
       if ( map == null)
       {
    	   map = new LinkedHashMap<String,Object>();
       }
       map.put(key, value);
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

   private Map<String, Object> fillMap(EntityResolver resolver)  {
	   Map<String,Object> map = new LinkedHashMap<String,Object>();
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
	   this.map.putAll(  map);
   }

   public ReferenceHandler getReferenceHandler() {
       return referenceHandler;
   }

   
   public Object get(Object key) {
       return  map.get(key);
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
    * @see java.util.Map#keySet()
    */
   public Set<String> keySet() {
       return map.keySet();
   }

    public boolean needsChange(DynamicType type) {
        for (Iterator it = map.values().iterator();it.hasNext();) {
            Object obj = it.next();
            if ( obj instanceof DynamicTypeDependant) {
                if (((DynamicTypeDependant) obj).needsChange( type ))
                    return true;
            }
        }
        return false;
    }

    public void commitChange(DynamicType type) {
        for (Object obj:map.values()) {
            if ( obj instanceof DynamicTypeDependant) {
                ((DynamicTypeDependant) obj).commitChange( type );
            }
        }
    }

    public void commitRemove(DynamicType type) throws CannotExistWithoutTypeException {
    	for (Object obj:map.values()) {
    		if ( obj instanceof DynamicTypeDependant) {
                ((DynamicTypeDependant) obj).commitRemove( type );
            }
        }
    }
        /** Clones the entity and all subentities*/
    public RaplaMapImpl deepClone()
    {
    	RaplaMapImpl clone = new RaplaMapImpl(map);
    	clone.setResolver( getReferenceHandler().getResolver());
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





}
