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

import org.rapla.components.util.TimeInterval;
import org.rapla.entities.storage.RefEntity;

public class UpdateEvent implements java.io.Serializable,Cloneable
{
    private static final long serialVersionUID = 1L;
    
    private LinkedHashMap<Object,RefEntity<?>> removeSet = new LinkedHashMap<Object,RefEntity<?>>();
    private LinkedHashMap<Object,RefEntity<?>> storeSet = new LinkedHashMap<Object,RefEntity<?>>();
    private LinkedHashMap<Object,RefEntity<?>> referenceSet = new LinkedHashMap<Object,RefEntity<?>>();
    
    private String userId;
    private long repositoryVersion;
    
    private boolean needResourcesRefresh = false;

	

	private TimeInterval invalidateInterval;
    
    public UpdateEvent() {
    }
    
    public void setUserId( String userId) {
        this.userId = userId;
    }
    public String getUserId() {
        return userId;
    }
    
    private void addRemove(RefEntity<?> entity) {
        removeSet.put( entity.getId(),entity);
    }
    
    private void addStore(RefEntity<?> entity) {
        storeSet.put( entity.getId(), entity);
    }

    public Collection<RefEntity<?>> getRemoveObjects() {
        return removeSet.values();
    }

    public Collection<RefEntity<?>> getStoreObjects() {
        return storeSet.values();
    }
    
    public void putReference(RefEntity<?> entity) {
		referenceSet.put(entity.getId(), entity);
	}

    public Collection<RefEntity<?>> getReferenceObjects() {
        return referenceSet.values();
    }
    /** use this method if you want to avoid adding the same Entity twice.*/
    public void putStore(RefEntity<?> entity) {
       
        if (storeSet.get(entity.getId()) == null)
            addStore(entity);
    }

    /** use this method if you want to avoid adding the same Entity twice.*/
    public void putRemove(RefEntity<?> entity) {
        if (removeSet.get(entity.getId()) == null)
            addRemove(entity);
    }

    /** find an entity in the update-event that matches the passed original. Returns null
     * if no such entity is found. */
    public RefEntity<?> findEntity(RefEntity<?> original) {
        RefEntity<?> entity =  storeSet.get( original.getId());
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
        clone.removeSet = (LinkedHashMap<Object,RefEntity<?>>) removeSet.clone();
        clone.storeSet = (LinkedHashMap<Object,RefEntity<?>>) storeSet.clone();
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

	public Collection<RefEntity<?>> getAllObjects() {
		HashSet<RefEntity<?>> objects = new HashSet<RefEntity<?>>();
		objects.addAll( storeSet.values());
		objects.addAll( removeSet.values());
		objects.addAll( referenceSet.values());
		return objects;
	}

	
    
}
