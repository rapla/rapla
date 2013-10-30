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

package org.rapla.storage.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.rapla.components.util.DateTools;
import org.rapla.components.util.TimeInterval;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.entities.Category;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.Named;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.internal.PreferencesImpl;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.Template;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.internal.ModifiableTimestamp;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.Mementable;
import org.rapla.entities.storage.RefEntity;
import org.rapla.facade.Conflict;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.logger.Logger;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.LocalCache;
import org.rapla.storage.StorageUpdateListener;
import org.rapla.storage.UpdateEvent;
import org.rapla.storage.UpdateResult;

/**
 * An abstract implementation of the StorageOperator-Interface. It operates on a
 * LocalCache-Object and doesn't implement connect, isConnected, refresh,
 * createIdentifier and disconnect. <b>!!WARNING!!</b> This operator is not
 * thread-safe. {@link org.rapla.server.internal.ServerServiceImpl} for an
 * example of an thread-safe wrapper around this storageoperator.
 * 
 * @see LocalCache
 */

public abstract class AbstractCachableOperator implements CachableStorageOperator {
	protected RaplaLocale raplaLocale;
	
	final List<StorageUpdateListener> storageUpdateListeners = new Vector<StorageUpdateListener>();
	protected LocalCache cache;
	/**
	 * set encryption if you want to enable password encryption. Possible values
	 * are "sha" or "md5".
	 */
	protected I18nBundle i18n;
	protected RaplaContext context;
	Logger logger;
	protected ReadWriteLock lock = new ReentrantReadWriteLock();
	
	//protected IdTable idTable = new IdTable();
	
	public AbstractCachableOperator(RaplaContext context, Logger logger) throws RaplaException {
		this.logger = logger;
		this.context = context;
		raplaLocale = context.lookup(RaplaLocale.class);
		i18n = context.lookup(RaplaComponent.RAPLA_RESOURCES);

		LocalCache newCache = new LocalCache();
		setCache(newCache);
	}

	public Logger getLogger() {
		return logger;
	}

	public void connect() throws RaplaException {
		connect(null);
	}

	public abstract Date getCurrentTimestamp();
	
	protected I18nBundle getI18n() {
		return i18n;
	}

	// Implementation of StorageOperator
	public <T> RefEntity<T> editObject(RefEntity<T> o, User user)throws RaplaException {
		Collection<RefEntity<T>> list = editObjects( Collections.singleton( o), user);
		return list.iterator().next();
	}

	@SuppressWarnings("unchecked")
	public <T> Collection<RefEntity<T>> editObjects(Collection<RefEntity<T>> list, User user)throws RaplaException {
		checkConnected();
		Collection<RefEntity<T>> toEdit = new LinkedHashSet<RefEntity<T>>();
		Map<RefEntity<T>, T> persistantMap = getPersistant(list);
	    for (RefEntity<T> o:list) 
	    {
			RefEntity<T> persistant = (RefEntity<T>) persistantMap.get(o);
			final T clone;
			if ( persistant != null)
			{
    			clone = persistant.deepClone();
    		}
			else
			{
    			clone = o.deepClone();
    		}
    		RefEntity<T> refEntity = (RefEntity<T>) clone;
			if (refEntity instanceof ModifiableTimestamp) {
    			((ModifiableTimestamp) refEntity).setLastChanged(getCurrentTimestamp());
    			if (user != null) {
    				((ModifiableTimestamp) refEntity).setLastChangedBy(user);
    			}
    		}
    		refEntity.setReadOnly(false);
    		toEdit.add( refEntity);
	    }
	    return toEdit;
	}

	synchronized public void storeAndRemove(final Collection<RefEntity<?>> storeObjects,	final Collection<RefEntity<?>> removeObjects, final RefEntity<User> user) throws RaplaException {
		checkConnected();

		UpdateEvent evt = new UpdateEvent();
		if (user != null) {
			evt.setUserId(user.getId());
		}
		for (RefEntity<?> obj : storeObjects) {
			evt.putStore(obj);
		}

		for (RefEntity<?> entity : removeObjects) {
			RaplaType type = entity.getRaplaType();
			if (Appointment.TYPE ==type || Category.TYPE == type || Attribute.TYPE ==  type) {
				String name = getName( entity);
				throw new RaplaException(getI18n().format("error.remove_object",name));
			} 
			evt.putRemove(entity);
		}
		dispatch(evt);
	}

	public <T extends RaplaObject> Collection<T> getObjects(Class<T> typeClass)	throws RaplaException {
		checkConnected();
		try
		{
			lock.readLock().lock();
			Collection<T> collection = cache.getCollection(typeClass);
			return collection;
		}
		finally
		{
			lock.readLock().unlock();
		}
	}

	synchronized public Collection<RefEntity<?>> getVisibleEntities(final User user)throws RaplaException {
		checkConnected();
		try
		{
			lock.readLock().lock();
			Collection<RefEntity<?>> list = cache.getVisibleEntities(user);
			return list;
		}
		finally
		{
			lock.readLock().unlock();
		}
	}
	
	public Map<String, Template> getTemplateMap() {
        Map<String,Template> templateMap =new LinkedHashMap<String, Template>();
        Collection<Reservation> reservations;
		try
		{
			lock.readLock().lock();
			reservations = cache.getCollection(Reservation.class);
		}
		finally
		{
			lock.readLock().unlock();
		}
    	//Reservation[] reservations = cache.getReservations(user, start, end, filters.toArray(ClassificationFilter.CLASSIFICATIONFILTER_ARRAY));
        
        for ( Reservation r:reservations)
        {
        	String templateName = r.getAnnotation(Reservation.TEMPLATE);
        	if ( templateName != null)
        	{
        		Template template = templateMap.get( templateName);
        		if ( template == null)
                {
                    template = new Template( templateName);
                    templateMap.put( templateName, template);
                }
        		template.add( r);
        	}
        }
        return templateMap;
	}
	
	public User getUser(final String username) throws RaplaException {
		checkConnected();
		lock.readLock().lock();
		try
		{
			return cache.getUser(username);
		}
		finally
		{
			lock.readLock().unlock();
		}
	}

	@SuppressWarnings("unchecked")
	public Preferences getPreferences(final User user) throws RaplaException {
		checkConnected();
		// Test if user is already stored
		if (user != null) {
			resolveId(((RefEntity<User>) user).getId());
		}
		Preferences pref;
		lock.readLock().lock();
		try
		{
			pref = cache.getPreferences(user);
		}
		finally
		{
			lock.readLock().unlock();
		}
		if (pref == null) {
			lock.writeLock().lock();
			try
			{
				PreferencesImpl newPref = new PreferencesImpl();
				newPref.setOwner(user);
				Comparable createIdentifier = createIdentifier(Preferences.TYPE,1)[0];
				newPref.setId(createIdentifier);
				pref = newPref;
				cache.put(newPref);
			}
			finally
			{
				lock.writeLock().unlock();
			}
		}

		return pref;
	}

	public Category getSuperCategory() {
		return cache.getSuperCategory();
	}

	public synchronized void addStorageUpdateListener(StorageUpdateListener listener) {
		storageUpdateListeners.add(listener);
	}

	public synchronized void removeStorageUpdateListener(StorageUpdateListener listener) {
		storageUpdateListeners.remove(listener);
	}

	public StorageUpdateListener[] getStorageUpdateListeners() {
		return storageUpdateListeners.toArray(new StorageUpdateListener[] {});
	}

	protected void fireStorageUpdated(final UpdateResult evt) {
		StorageUpdateListener[] listeners = getStorageUpdateListeners();
		for (int i = 0; i < listeners.length; i++) {
			listeners[i].objectsUpdated(evt);
		}
	}

	protected void fireUpdateError(final RaplaException ex) {
		if (storageUpdateListeners.size() == 0)
			return;
		StorageUpdateListener[] listeners = getStorageUpdateListeners();
		for (int i = 0; i < listeners.length; i++) {
			listeners[i].updateError(ex);
		}
	}

	protected void fireStorageDisconnected(String message) {
		if (storageUpdateListeners.size() == 0)
			return;
		StorageUpdateListener[] listeners = getStorageUpdateListeners();
		for (int i = 0; i < listeners.length; i++) {
			listeners[i].storageDisconnected(message);
		}
	}

	public Date today() {
		return DateTools.cutDate( getCurrentTimestamp());
	}

	// End of StorageOperator interface
	protected void checkConnected() throws RaplaException {
		if (!isConnected())
		{
			throw new RaplaException(getI18n().format("error.connection_closed", ""));
		}
	}

	protected void setCache(final LocalCache cache) {
		this.cache = cache;
	}

	public LocalCache getCache() {
		return cache;
	}

	public <T> Map<RefEntity<T>, T> getPersistant(Collection<RefEntity<T>> list) throws RaplaException 
	{
		lock.readLock().lock();
		try
		{
			Map<RefEntity<T>,T> result = new LinkedHashMap<RefEntity<T>,T>();
		    for (RefEntity<T> o:list) 
		    {
		    	@SuppressWarnings("unchecked")
				RefEntity<T> persistant = (RefEntity<T>) cache.get(o.getId());
		    	if ( persistant != null)
		    	{
		    		result.put( o,persistant.cast());
		    	}
		    }
			return result;
		}
		finally
		{
			lock.readLock().unlock();
		}
	}
	
	protected void resolveEntities(Collection<RefEntity<?>> entities,	EntityResolver resolver) throws RaplaException {
		List<RefEntity<?>> readOnlyList = new ArrayList<RefEntity<?>>();
		for (RefEntity<?> obj:entities) {
			try
			{
				obj.resolveEntities(resolver);
			}
			catch ( EntityNotFoundException ex)
			{
				logEntityNotFound( obj, ex);
				throw ex;
			}
			readOnlyList.add(obj);
		}
		// It is important to do the read only later because some resolve might involve write to referenced objects
		for (Iterator<RefEntity<?>> it = readOnlyList.iterator(); it.hasNext();) {
			 it.next().setReadOnly(true);
		}
	}
	
	/** override for special log handling
	 */
	@SuppressWarnings({ "unused", "unused" })
	protected void logEntityNotFound(RefEntity<?> obj,  EntityNotFoundException ex) {
		getLogger().warn(ex.getMessage());
	}
	
	/** Check if the objects are consistent, so that they can be safely stored. */
	protected void checkConsistency(Collection<RefEntity<?>> entities) throws RaplaException {
		for (RefEntity<?> entity : entities) {
			for (RefEntity<?> reference:entity.getReferences())
		    {
				if (reference instanceof Preferences
						|| reference instanceof Conflict
						|| (reference instanceof Reservation && !( entity instanceof Appointment)) 
						|| (reference instanceof Appointment && !( entity instanceof Reservation)) 
						)
				{
					throw new RaplaException("The current version of Rapla doesn't allow references to objects of type "	+ reference.getRaplaType());
				}
			}
			// Check if the user group is missing
			if (Category.TYPE == entity.getRaplaType()) {
				if (entity.equals(cache.getSuperCategory())) {
					Category userGroups = ((Category) entity).getCategory(Permission.GROUP_CATEGORY_KEY);
					if (userGroups == null) {
						throw new RaplaException("The category with the key '"
								+ Permission.GROUP_CATEGORY_KEY
								+ "' is missing.");
					}
				} else {
					Category category = (Category) entity;
					if (category.getParent() == null) {
						throw new RaplaException("The category " + category
								+ " needs a parent.");
					}
				}
			}
		}
	}

	protected String getName(Object object) {
		if (object == null)
			return null;
		if (object instanceof Named)
			return (((Named) object).getName(i18n.getLocale()));
		return object.toString();
	}

	protected String getString(String key) {
		return getI18n().getString(key);
	}

	public RefEntity<?> resolveId(Comparable id) throws EntityNotFoundException {
		lock.readLock().lock();
		try
		{
			return cache.resolve(id);
		}
		finally
		{
			lock.readLock().unlock();
		}
	}

	/** Writes the UpdateEvent in the cache */
	@SuppressWarnings("unchecked")
	synchronized protected UpdateResult update(final UpdateEvent evt) throws RaplaException 
	{
		lock.writeLock().lock();
		try
		{
			HashMap<RefEntity<?>, RefEntity<?>> oldEntities = new HashMap<RefEntity<?>, RefEntity<?>>();
			// First make a copy of the old entities
			Collection<RefEntity<?>> storeObjects = evt.getStoreObjects();
			for (RefEntity<?> entity : storeObjects) 
			{
				if (!isStorableInCache( entity))
				{
					continue;
				}
					
				RefEntity<?> persistantEntity = findInLocalCache(entity);
				if ( persistantEntity == null)
				{
					continue; 
				}
				
				// do nothing, because the persitantVersion is the same as the
				// stored
				if (persistantEntity == entity ) 
				{
					continue;
				}
	
				if (getLogger().isDebugEnabled())
				{
					getLogger().debug("Storing old: " + entity);
				}
	
				// we need to clone the persistent entity here to keep the old entries because its content will be replaced by the copy mechanism below
				RefEntity<?> oldEntity = deepClone(persistantEntity);
				oldEntities.put(persistantEntity, oldEntity);
			}
			Collection<RefEntity<?>> updatedEntities = new ArrayList<RefEntity<?>>();
			// Then update the new entities
			for (RefEntity<?> entity : storeObjects) {
				RefEntity<?> toUpdate = null;
				boolean storableInCache = isStorableInCache( entity);
				if ( storableInCache)
				{
					increaseVersion(entity);
					toUpdate = findInLocalCache(entity);
					// do nothing, because the persitantVersion is always ReadOnly
					if (toUpdate == entity) {
						continue;
					}
					if (toUpdate != null) {
						if (getLogger().isDebugEnabled())
						{
							getLogger().debug("Changing: " + entity);
						}
						((Mementable<RefEntity<?>>) toUpdate).copy(entity);
					} else {
						if (getLogger().isDebugEnabled())
						{
							getLogger().debug("Adding entity: " + entity);
						}
						// we clone the entity because it could be modified after calling dispatch
						// Note: All entities referencing to the entity (e.g. the parent entity) still refer to the orginal .
						// To update to the new objects resolve Entities is called below
						toUpdate = deepClone(entity);
					}
					cache.put(toUpdate);
				}
				else
				{
					toUpdate = entity;
				}
				if ( isAddedToUpdateResult ( entity))
				{
					updatedEntities.add(toUpdate);	
				}
			}
			Collection<RefEntity<?>> removeObjects = evt.getRemoveObjects();
			Collection<RefEntity<?>> toRemove = new HashSet<RefEntity<?>>();
			for (RefEntity<?> entity : removeObjects) {
				RefEntity<?> persistantVersion = null;
				if ( isStorableInCache(entity))
				{
					increaseVersion(entity);
					persistantVersion = findInLocalCache(entity);
					if (persistantVersion != null) {
						cache.remove(persistantVersion);
						persistantVersion.setReadOnly(true);
					}
				}
				if  ( persistantVersion == null)
				{
					persistantVersion = entity;
				}
				if  ( isAddedToUpdateResult(entity))
				{
					toRemove.add( entity);
				}
			}
	
	
			/**
			 * we need to update every reference in the stored entity. So that the
			 * references in the persistant entities always point to persistant
			 * entities and never to local working copies. This is done by a call to resolveEntities
			 */
			for (RefEntity<?> toUpdate:updatedEntities) 
			{
				if  ( !toUpdate.isPersistant())
				{
					toUpdate.resolveEntities(cache);
				}
			}
			// it is important to set readonly only after a complete resolval of all entities, because it operates recursiv and could set an unresolved subentity to read only which is forbidden
			for (RefEntity<?> toUpdate:updatedEntities) 
			{
				if  ( !toUpdate.isPersistant())
				{
					toUpdate.setReadOnly( true);
				}
			}
	
			TimeInterval invalidateInterval = evt.getInvalidateInterval();
			Comparable userId = evt.getUserId();
			return createUpdateResult(oldEntities, updatedEntities, toRemove, invalidateInterval, userId);
		}
		finally
		{
			lock.writeLock().unlock();
		}
	}

	protected UpdateResult createUpdateResult(
			Map<RefEntity<?>, RefEntity<?>> oldEntities,
			Collection<RefEntity<?>> updatedEntities,
			Collection<RefEntity<?>> toRemove, TimeInterval invalidateInterval,
			Comparable userId) throws EntityNotFoundException {
		User user = null;
		if (userId != null) {
			user = (User) resolveId(userId);
		}

		UpdateResult result = new UpdateResult(user);
		if ( invalidateInterval != null)
		{
			result.setInvalidateInterval( invalidateInterval);
		}
		for (RefEntity<?> toUpdate:updatedEntities) 
		{
			RefEntity<?> newEntity = toUpdate;
			RefEntity<?> oldEntity = oldEntities.get(toUpdate);
			if (oldEntity != null) {
				result.addOperation(new UpdateResult.Change( newEntity, oldEntity));
			} else {
				result.addOperation(new UpdateResult.Add( newEntity));
			}
		}
		
		for (RefEntity<?> entity:toRemove)
		{
			result.addOperation(new UpdateResult.Remove(entity));
		}

		return result;
	}

	/** returns null if no persistant version found */
	protected RefEntity<?> findInLocalCache(RefEntity<?> entity)
	{
		return cache.get(entity.getId());
	}
	
	abstract protected boolean isAddedToUpdateResult(RefEntity<?> entity);

	abstract protected boolean isStorableInCache(RefEntity<?> entity);

	protected void increaseVersion(RefEntity<?> e) {
		e.setVersion(e.getVersion() + 1);
		if (getLogger().isDebugEnabled()) {
			getLogger().debug("Increasing Version for " + e + " to " + e.getVersion());
		}
	}
	protected RefEntity<?> deepClone(RefEntity<?> entity) 
	{
		return (RefEntity<?>) entity.deepClone();
	}
	

}
