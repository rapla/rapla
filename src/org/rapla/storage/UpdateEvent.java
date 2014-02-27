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

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.rapla.components.util.TimeInterval;
import org.rapla.entities.Entity;
import org.rapla.entities.configuration.internal.PreferencesImpl;
import org.rapla.entities.domain.internal.AllocatableImpl;
import org.rapla.entities.domain.internal.PeriodImpl;
import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.entities.internal.CategoryImpl;
import org.rapla.entities.internal.UserImpl;

public class UpdateEvent implements java.io.Serializable,Cloneable
{
	List<PreferencesImpl> preferences;
	List<AllocatableImpl> allocatable;
	List<CategoryImpl> categories;
	List<UserImpl> users;
	List<DynamicTypeImpl> types;
	List<ReservationImpl> reservations;
	List<PeriodImpl> periods;
	
	private static final long serialVersionUID = 1L;
    //List<User> changedUser;
    transient private Map<String,Entity> removeSet = new LinkedHashMap<String,Entity>();
    transient private Map<String,Entity> storeSet = new LinkedHashMap<String,Entity>();
    transient private Map<String,Entity> referenceSet = new LinkedHashMap<String,Entity>();
//    RaplaMapImpl<RaplaObject> objMap = new RaplaMapImpl<>();
    private String userId;
    private long repositoryVersion;
    
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


    public void setUserId( String userId) {
        this.userId = userId;
    }
    public String getUserId() {
        return userId;
    }
    
    private void addRemove(Entity entity) {
        removeSet.put( entity.getId(),(Entity)entity);
    }
    
    private void addStore(Entity entity) {
        storeSet.put( entity.getId(), (Entity)entity);
    }

    public Collection<Entity>getRemoveObjects() {
        return removeSet.values();
    }

    public Collection<Entity>getStoreObjects() {
        return storeSet.values();
    }
    
    public void putReference(Entity entity) {
		referenceSet.put(entity.getId(), entity);
	}

    public Collection<Entity>getReferenceObjects() {
        return referenceSet.values();
    }
    /** use this method if you want to avoid adding the same Entity twice.*/
    public void putStore(Entity entity) {
       
        if (storeSet.get(entity.getId()) == null)
            addStore(entity);
    }

    /** use this method if you want to avoid adding the same Entity twice.*/
    public void putRemove(Entity entity) {
        if (removeSet.get(entity.getId()) == null)
            addRemove(entity);
    }

    /** find an entity in the update-event that matches the passed original. Returns null
     * if no such entity is found. */
    public Entity findEntity(Entity original) {
        Entity entity =  storeSet.get( original.getId());
        if ( entity != null)
            return entity;
        entity =  removeSet.get( original.getId());
        if ( entity != null)
            return entity;
        return null;
    }

    @SuppressWarnings("unchecked")
	public UpdateEvent clone() {
        UpdateEvent clone = new UpdateEvent( );
        clone.repositoryVersion = repositoryVersion;
        clone.invalidateInterval = invalidateInterval;
        clone.needResourcesRefresh = needResourcesRefresh;
        clone.userId = userId;
        clone.removeSet = (Map<String,Entity>) ((LinkedHashMap<String,Entity>) removeSet).clone();
        clone.storeSet = (Map<String,Entity>) ((LinkedHashMap<String,Entity>) storeSet).clone();
        return clone;
    }

    public long getRepositoryVersion()
    {
        return repositoryVersion;
    }

    public void setRepositoryVersion( long repositoryVersion )
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
		objects.addAll( storeSet.values());
		objects.addAll( removeSet.values());
		objects.addAll( referenceSet.values());
		return objects;
	}
	
    
}
