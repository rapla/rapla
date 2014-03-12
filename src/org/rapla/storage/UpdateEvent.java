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
package org.rapla.storage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.rapla.components.util.TimeInterval;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.internal.PreferencesImpl;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Period;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.internal.AllocatableImpl;
import org.rapla.entities.domain.internal.PeriodImpl;
import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.entities.internal.CategoryImpl;
import org.rapla.entities.internal.UserImpl;
import org.rapla.facade.Conflict;
import org.rapla.facade.internal.ConflictImpl;

public class UpdateEvent implements java.io.Serializable,Cloneable
{
	transient Map listMap;// = new HashMap<Class, List<Entity>>(); 
	//transient Map<Class,List> lists = new LinkedHashMap<Class,List>();
	List<PreferencesImpl> preferences = createList(Preferences.class);
	List<AllocatableImpl> allocatable = createList(Allocatable.class);
	List<CategoryImpl> categories = createList(Category.class);
	List<UserImpl> users = createList(User.class);
	List<DynamicTypeImpl> types = createList(DynamicType.class);
	List<ReservationImpl> reservations =  createList(Reservation.class);
	List<PeriodImpl> periods =  createList(Period.class);
	List<ConflictImpl> conflicts =  createList(Conflict.class);
	
	private Set<String> removeSet = new LinkedHashSet<String>();
	private Set<String> storeSet = new LinkedHashSet<String>();
	private static final long serialVersionUID = 1L;
    //List<User> changedUser;
    //transient private Map<String,Entity> removeSet = new LinkedHashMap<String,Entity>();
    //transient private Map<String,Entity> storeSet = new LinkedHashMap<String,Entity>();
   // transient private Map<String,Entity> referenceSet = new LinkedHashMap<String,Entity>();
//    RaplaMapImpl<RaplaObject> objMap = new RaplaMapImpl<>();
    private String userId;
    private int repositoryVersion;
    
    private boolean needResourcesRefresh = false;

	private TimeInterval invalidateInterval;
    
    public UpdateEvent() {
    }


//	@Override
//	public void resolveEntities(EntityResolver resolver) throws EntityNotFoundException {
//		objMap.resolveEntities(resolver);
//	}
//
//	@Override
//	public Iterable<Entity>getReferences() {
//		return objMap.getReferences();
//	}
//
//	@Override
//	public boolean isRefering(Entity object) {
//		return objMap.isRefering(object);
//	}


    private  <T> List<T> createList(Class<? super T> clazz) {
		ArrayList<T> list = new ArrayList<T>();
		//lists.put(clazz, list);
		//listMap.put( clazz, (List<Entity>) list);
		return list;
	}


	public void setUserId( String userId) {
        this.userId = userId;
    }
    public String getUserId() {
        return userId;
    }
    
    private void addRemove(Entity entity) {
        removeSet.add( entity.getId());
        add( entity);
    }

	private void addStore(Entity entity) {
        storeSet.add( entity.getId());
        add( entity);
    }
	
	@SuppressWarnings("unchecked")
	public Map<Class, Collection<Entity>> getListMap() {
		if ( listMap == null)
		{
			listMap = new HashMap<Class,Collection<Entity>>();
			listMap.put( Preferences.class,preferences);
			listMap.put( Allocatable.class,allocatable);
			listMap.put(Category.class, categories);
			listMap.put(User.class, users);
			listMap.put(DynamicType.class, types);
			listMap.put(Reservation.class, reservations);
			listMap.put(Period.class, periods);
			listMap.put(Conflict.class, conflicts);
		}
		return listMap;
	}

    private void add(Entity entity) {
    	@SuppressWarnings("unchecked")
		Class<? extends RaplaType> class1 = entity.getRaplaType().getTypeClass();
    	Collection<Entity> list = getListMap().get( class1);
    	if ( list == null)
    	{
    		//listMap.put( class1, list);
    		throw new IllegalArgumentException(entity.getRaplaType() + " can't be stored ");
    	}
    	list.add( entity);
	}

    public Collection<Entity> getRemoveObjects()
    {
		HashSet<Entity> objects = new HashSet<Entity>();
		for ( Collection<Entity> list:getListMap().values())
        {
        	for ( Entity entity:list)
        	{
        		if ( removeSet.contains( entity.getId()))
        		{
        			objects.add(entity);
        		}
        	}
        }
		return objects;

    }

    public Collection<Entity> getStoreObjects() 
    {
		HashSet<Entity> objects = new HashSet<Entity>();
		for ( Collection<Entity> list:getListMap().values())
        {
        	for ( Entity entity:list)
        	{
        		if ( storeSet.contains( entity.getId()))
        		{
        			objects.add(entity);
        		}
        	}
        }
		return objects;

    }
    
//    public void putReference(Entity entity) {
//		referenceSet.put(entity.getId(), entity);
//	}
//
//    public Collection<Entity>getReferenceObjects() {
//        return referenceSet.values();
//    }
    /** use this method if you want to avoid adding the same Entity twice.*/
    public void putStore(Entity entity) {
       
        if (!storeSet.contains(entity.getId()))
            addStore(entity);
    }

    /** use this method if you want to avoid adding the same Entity twice.*/
    public void putRemove(Entity entity) {
        if (!removeSet.contains(entity.getId()))
            addRemove(entity);
    }

    /** find an entity in the update-event that matches the passed original. Returns null
     * if no such entity is found. */
    public Entity findEntity(Entity original) {
        String originalId = original.getId();
		if (!storeSet.contains( originalId))
        {
			if (!removeSet.contains( originalId))
	        {
	        	return null;
	        }
        }
		
        for ( Collection<Entity> list:getListMap().values())
        {
        	for ( Entity entity:list)
        	{
        		if ( entity.getId().equals( originalId))
        		{
        			return entity;
        		}
        	}
        }
      	throw new IllegalStateException("Entity in store/remove set but not found in list");
    }

    public UpdateEvent clone() {
        UpdateEvent clone = new UpdateEvent( );
        clone.repositoryVersion = repositoryVersion;
        clone.invalidateInterval = invalidateInterval;
        clone.needResourcesRefresh = needResourcesRefresh;
        clone.userId = userId;
        clone.removeSet = new LinkedHashSet<String>( removeSet);
        clone.storeSet = new LinkedHashSet<String>( storeSet);
        
        for ( Collection<Entity> list:getListMap().values())
        {
        	for ( Entity entity:list)
        	{
        		String id = entity.getId();
				if ( storeSet.contains( id))
        		{
        			clone.addStore( entity);
        		}
        		if ( removeSet.contains( id))
        		{
        			clone.addRemove( entity);
        		}
        	}
        }
        return clone;
    }

    public int getRepositoryVersion()
    {
        return repositoryVersion;
    }

    public void setRepositoryVersion( int repositoryVersion )
    {
        this.repositoryVersion = repositoryVersion;
    }

	public void setInvalidateInterval(TimeInterval invalidateInterval) 
	{
		this.invalidateInterval = invalidateInterval;
	}
	
	public TimeInterval getInvalidateInterval() 
	{
		return invalidateInterval;
	}
	
	public boolean isNeedResourcesRefresh() {
		return needResourcesRefresh;
	}

	public void setNeedResourcesRefresh(boolean needResourcesRefresh) {
		this.needResourcesRefresh = needResourcesRefresh;
	}

	public Collection<Entity> getAllObjects() {
		HashSet<Entity> objects = new HashSet<Entity>();
		for ( Collection<Entity> list:getListMap().values())
        {
        	for ( Entity entity:list)
        	{
        		objects.add(entity);
        	}
        }
		return objects;
	}


	public boolean isEmpty() {
        boolean isEmpty = removeSet.isEmpty() && storeSet.isEmpty() && invalidateInterval == null;
        return isEmpty;
	}
	
    
}
