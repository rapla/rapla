/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
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
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.rapla.components.util.ParseDateException;
import org.rapla.components.util.SerializableDateTimeFormat;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.internal.PreferencesImpl;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.internal.AllocatableImpl;
import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.entities.internal.CategoryImpl;
import org.rapla.entities.internal.UserImpl;
import org.rapla.entities.storage.EntityReferencer;
import org.rapla.facade.Conflict;
import org.rapla.facade.internal.ConflictImpl;

public class UpdateEvent
{
	transient Map listMap;// = new HashMap<Class, List<Entity>>(); 
	List<CategoryImpl> categories;
	List<DynamicTypeImpl> types;
	List<UserImpl> users;
	List<PreferencePatch> preferencesPatches;

	List<PreferencesImpl> preferences;
	List<AllocatableImpl> resources;
	List<ReservationImpl> reservations;
	List<ConflictImpl> conflicts;
	
	private Set<String> removeSet;
	private Set<String> storeSet;

    private String userId;
    
    private boolean needResourcesRefresh = false;

	private TimeInterval invalidateInterval;
    private String lastValidated;
	private int timezoneOffset;

	public UpdateEvent() {
    }

 	public void setUserId( String userId) {
        this.userId = userId;
    }
    public String getUserId() {
        return userId;
    }
    
    private void addRemove(String id) {
        if ( removeSet == null)
        {
            removeSet = new LinkedHashSet<String>();
        }
        removeSet.add( id);
//        if ( entity instanceof ConflictImpl)
//        {
//            removeConflicts.add( (ConflictImpl) entity);
//        }
    //    add( entity);
    }
    
//    public Collection<ConflictImpl> getRemoveConflicts() {
//        if ( removeConflicts == null)
//        {
//            return Collections.emptySet();
//        }
//        return removeConflicts;
//    }

	private void addStore(Entity entity) {
	    if ( storeSet == null)
	    {
	        storeSet = new LinkedHashSet<String>();
	    }
        storeSet.add( entity.getId());
        add( entity);
    }
	
	@SuppressWarnings({ "unchecked" })
	private Map<Class, List<Entity>> getListMap() {
		if ( listMap == null)
		{
			listMap = new HashMap<Class,Collection<Entity>>();
            put(Reservation.class, reservations);
			put( Allocatable.class,resources);
            put( Preferences.class,preferences);
			put(Category.class, categories);
			put(User.class, users);
			put(DynamicType.class, types);
			put(Conflict.class, conflicts);
		}
		return listMap;
	}

	@SuppressWarnings("unchecked")
    private <T extends Entity> void put(Class<T> class1, List<? extends T> list) {
        if ( list != null)
        {
            listMap.put( class1, list );
        }
        
    }

    @SuppressWarnings({ "unchecked" })
    private void add(Entity entity) {
		Class<? extends RaplaType> class1 = entity.getRaplaType().getTypeClass();
    	List list = getListMap().get( class1);
    	if ( list == null)
    	{
            if ( class1.equals(Reservation.class))
            {
                reservations = new ArrayList<ReservationImpl>();
                list = reservations;
            }
            else if ( class1.equals(Allocatable.class))
            {
                resources = new ArrayList<AllocatableImpl>();
                list = resources;
            }
            else if ( class1.equals(Preferences.class))
            {
    	        preferences = new ArrayList<PreferencesImpl>();
    	        list = preferences;
            }
            else if ( class1.equals(Category.class))
            {
                categories = new ArrayList<CategoryImpl>();
                list = categories;
            }
            else if ( class1.equals(User.class))
            {
                users = new ArrayList<UserImpl>();
                list = users;
            }
    	    else if ( class1.equals(DynamicType.class))
    	    {
                types = new ArrayList<DynamicTypeImpl>();
                list = types;
    	    }
            else if ( class1.equals(Conflict.class))
            {
                conflicts = new ArrayList<ConflictImpl>();
                list = conflicts;
            } 
            else
            {
                throw new IllegalArgumentException(entity.getRaplaType() + " can't be stored ");
            }
            listMap.put( class1, list);
    	}
    	
    	list.add( entity );
	}
    
    public void putPatch(PreferencePatch patch) 
    {
        if ( preferencesPatches == null)
        {
            preferencesPatches = new ArrayList<PreferencePatch>();
        }
        preferencesPatches.add( patch);
    }


    public Collection<String> getRemoveIds()
    {
        if ( removeSet == null)
        {
            return Collections.emptyList();
        }
        return removeSet;
//		HashSet<Entity> objects = new LinkedHashSet<Entity>();
//		for ( Collection<Entity> list:getListMap().values())
//        {
//        	for ( Entity entity:list)
//        	{
//        		if (  removeSet.contains( entity.getId()))
//        		{
//        			objects.add(entity);
//        		}
//        	}
//        }
//		return objects;

    }

    public Collection<Entity> getStoreObjects() 
    {
        if ( storeSet == null)
        {
            return Collections.emptyList();
        }
        // Needs to be a linked hashset to keep the order of the entities
		HashSet<Entity> objects = new LinkedHashSet<Entity>();
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
    
    public Collection<EntityReferencer> getEntityReferences() 
    {
        HashSet<EntityReferencer> objects = new HashSet<EntityReferencer>();
        for ( Collection<Entity> list:getListMap().values())
        {
            for ( Entity entity:list)
            {
                String id = entity.getId();
                boolean contains = (storeSet != null && storeSet.contains( id)) ;
                if ( contains && entity instanceof EntityReferencer)
                {
                    EntityReferencer references = (EntityReferencer)entity;
                    objects.add(references);
                }
            }
        }
        
        for ( PreferencePatch patch:getPreferencePatches())
        {
            objects.add(patch);
        }
        return objects;

    }

    
    public List<PreferencePatch> getPreferencePatches() 
    {
        if ( preferencesPatches == null)
        {
            return Collections.emptyList();
        }
        return preferencesPatches;
    }
    
    /** use this method if you want to avoid adding the same Entity twice.*/
    public void putStore(Entity entity) {
        if (storeSet == null || !storeSet.contains(entity.getId()))
            addStore(entity);
    }

    /** use this method if you want to avoid adding the same Entity twice.*/
    public void putRemove(Entity entity) {
        String id = entity.getId();
        putRemoveId(id);
    }

    public void putRemoveId(String id) {
        if (removeSet == null || !removeSet.contains(id))
            addRemove(id);
    }

    /** find an entity in the update-event that matches the passed original. Returns null
     * if no such entity is found. */
    public Entity findEntity(Entity original) {
        String originalId = original.getId();
		if (storeSet == null || !storeSet.contains( originalId))
        {
			if (removeSet == null || !removeSet.contains( originalId))
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

    public void setLastValidated( Date serverTime )
    {
    	if ( serverTime == null)
    	{
    		this.lastValidated = null;
    	}
        this.lastValidated = SerializableDateTimeFormat.INSTANCE.formatTimestamp(serverTime);
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

//	public Collection<Entity> getAllObjects() {
//		HashSet<Entity> objects = new HashSet<Entity>();
//		for ( Collection<Entity> list:getListMap().values())
//        {
//        	for ( Entity entity:list)
//        	{
//        		objects.add(entity);
//        	}
//        }
//		return objects;
//	}

	public boolean isEmpty() {
        boolean isEmpty = removeSet == null && storeSet == null && invalidateInterval == null;
        return isEmpty;
	}

	public Date getLastValidated() 
	{
		if ( lastValidated == null)
		{
			return null;
		}
		try {
			return SerializableDateTimeFormat.INSTANCE.parseTimestamp(lastValidated);
		} catch (ParseDateException e) {
			throw new IllegalStateException(e.getMessage());
		}
	}
	
	public int getTimezoneOffset() 
	{
		return timezoneOffset;
	}

	public void setTimezoneOffset(int timezoneOffset) 
	{
		this.timezoneOffset = timezoneOffset;
	}



}
