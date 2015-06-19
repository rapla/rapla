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
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.rapla.components.util.Assert;
import org.rapla.components.util.TimeInterval;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.Named;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.internal.PreferencesImpl;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.internal.CategoryImpl;
import org.rapla.entities.internal.ModifiableTimestamp;
import org.rapla.entities.storage.EntityReferencer;
import org.rapla.entities.storage.EntityReferencer.ReferenceInfo;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.RefEntity;
import org.rapla.entities.storage.internal.SimpleEntity;
import org.rapla.facade.RaplaComponent;
import org.rapla.facade.internal.ConflictImpl;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.logger.Logger;
import org.rapla.storage.LocalCache;
import org.rapla.storage.PreferencePatch;
import org.rapla.storage.StorageOperator;
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

public abstract class AbstractCachableOperator implements StorageOperator {

	protected RaplaLocale raplaLocale;
	
	final List<StorageUpdateListener> storageUpdateListeners = new Vector<StorageUpdateListener>();
	protected LocalCache cache;
	protected I18nBundle i18n;
	protected RaplaContext context;
	Logger logger;
	protected ReadWriteLock lock = new ReentrantReadWriteLock();
	
	public AbstractCachableOperator(RaplaContext context, Logger logger) throws RaplaException {
		this.logger = logger;
		this.context = context;
		raplaLocale = context.lookup(RaplaLocale.class);
		i18n = context.lookup(RaplaComponent.RAPLA_RESOURCES);

		Assert.notNull(raplaLocale.getLocale());
		cache = new LocalCache();
	}

	public Logger getLogger() {
		return logger;
	}

	public User connect() throws RaplaException {
		return connect(null);
	}

	// Implementation of StorageOperator
	public <T extends Entity> T editObject(T o, User user)throws RaplaException {
		Set<Entity>singleton = Collections.singleton( (Entity)o);
		Collection<Entity> list = editObjects( singleton, user);
		Entity first = list.iterator().next();
		@SuppressWarnings("unchecked")
		T casted = (T) first;
		return casted;
	}

	public Collection<Entity> editObjects(Collection<Entity>list, User user)throws RaplaException {
		Collection<Entity> toEdit = new LinkedHashSet<Entity>();
		Map<Entity,Entity> persistantMap = getPersistant(list);
	    // read unlock 
		for (Entity o:list) 
	    {
			Entity persistant =   persistantMap.get(o);
			Entity refEntity = editObject(o, persistant, user);
    		toEdit.add( refEntity);
	    }
	    return toEdit;
	}

	protected Entity editObject(Entity newObj, Entity persistant, User user) {
		final SimpleEntity clone;
		if ( persistant != null)
		{
			clone = (SimpleEntity) persistant.clone();
		}
		else
		{
			clone = (SimpleEntity) newObj.clone();
		}
		SimpleEntity refEntity = clone;
		if (refEntity instanceof ModifiableTimestamp) {
		    ((ModifiableTimestamp) refEntity).setLastChangedBy(user);
		}
		return (Entity) refEntity;
	}

	public void storeAndRemove(final Collection<Entity> storeObjects,	final Collection<Entity>removeObjects, final User user) throws RaplaException {
		checkConnected();

		UpdateEvent evt = new UpdateEvent();
		if (user != null) {
			evt.setUserId(user.getId());
		}
		for (Entity obj : storeObjects) {
		    if ( obj instanceof Preferences)
		    {
		        PreferencePatch patch = ((PreferencesImpl)obj).getPatch();
                evt.putPatch( patch);
		    }
		    else
		    {
		        evt.putStore(obj);
		    }
		}

		for (Entity entity : removeObjects) {
			RaplaType type = entity.getRaplaType();
			if (Appointment.TYPE ==type || Category.TYPE == type || Attribute.TYPE ==  type) {
				String name = getName( entity);
				throw new RaplaException(getI18n().format("error.remove_object",name));
			} 
			evt.putRemove(entity);
		}
		dispatch(evt);
	}
	
	/**
	 * implement this method to implement the persistent mechanism. By default it
	 * calls <li>check()</li> <li>update()</li> <li>fireStorageUpdate()</li> <li>
	 * fireTriggerEvents()</li> You should not call dispatch directly from the
	 * client. Use storeObjects and removeObjects instead.
	 */
	public abstract void dispatch(UpdateEvent evt) throws RaplaException;

	public Collection<User> getUsers()	throws RaplaException {
		checkConnected();
		Lock readLock = readLock();
		try
		{
			Collection<User> collection = cache.getUsers();
			// We return a clone to avoid synchronization Problems
			return new LinkedHashSet<User>(collection);
		}
		finally
		{
			unlock(readLock);
		}
	}
	
	public Collection<DynamicType> getDynamicTypes() throws RaplaException {
		checkConnected();
		Lock readLock = readLock();
		try
		{
			Collection<DynamicType> collection =  cache.getDynamicTypes();
			// We return a clone to avoid synchronization Problems
			return new ArrayList<DynamicType>( collection);
		}
		finally
		{
			unlock(readLock);
		}
	}

	public Collection<Allocatable> getAllocatables(ClassificationFilter[] filters) throws RaplaException
	{
		Collection<Allocatable> allocatables = new LinkedHashSet<Allocatable>();
		checkConnected();
		Lock readLock = readLock();
		try
		{
			Collection<Allocatable> collection = cache.getAllocatables();
			// We return a clone to avoid synchronization Problems
			allocatables.addAll(collection);
		}
		finally
		{
			unlock(readLock);
		}
		removeFilteredClassifications(allocatables, filters);
		return allocatables;
	}

	protected void removeFilteredClassifications(	Collection<? extends Classifiable> list, ClassificationFilter[] filters) {
		if (filters == null)
		{
			// remove internal types if not specified in filters to remain backwards compatibility 
			Iterator<? extends Classifiable> it = list.iterator();
			while (it.hasNext()) {
				Classifiable classifiable = it.next();
				if ( Classifiable.ClassifiableUtil.isInternalType(classifiable) )
				{
					it.remove();
				}
			}
			return;
		}

		Iterator<? extends Classifiable> it = list.iterator();
		while (it.hasNext()) {
			Classifiable classifiable = it.next();
			if (!ClassificationFilter.Util.matches(filters, classifiable))
			{
				it.remove();
			}
		}
	}
	
	public User getUser(final String username) throws RaplaException {
		checkConnected();
		Lock readLock = readLock();
		try
		{
			return cache.getUser(username);
		}
		finally
		{
			unlock(readLock);
		}
	}

	protected Map<String,PreferencesImpl> emptyPreferencesProxy = new HashMap<String, PreferencesImpl>();

	public Preferences getPreferences(final User user, boolean createIfNotNull) throws RaplaException {
		checkConnected();
		// Test if user is already stored
		if (user != null) {
			resolve(user.getId(), User.class);
		}
		String userId = user != null ? user.getId() : null;
        String preferenceId = PreferencesImpl.getPreferenceIdFromUser(userId);
		PreferencesImpl pref = (PreferencesImpl) cache.tryResolve( preferenceId, Preferences.class);
		if (pref == null && createIfNotNull )
		{
	        synchronized ( emptyPreferencesProxy) { 
    			PreferencesImpl preferencesImpl = emptyPreferencesProxy.get( preferenceId);
    			if ( preferencesImpl != null)
    			{
    				return preferencesImpl;
    			}
	        }
		}

		if (pref == null && createIfNotNull) {
		    synchronized ( emptyPreferencesProxy) {
    			PreferencesImpl newPref = newPreferences(userId);
    			newPref.setReadOnly(  );
    			pref = newPref;
    			emptyPreferencesProxy.put(preferenceId , pref);
		    }
		}
		return pref;
	}

    private PreferencesImpl newPreferences(final String userId) throws EntityNotFoundException {
        Date now = getCurrentTimestamp();
        String id = PreferencesImpl.getPreferenceIdFromUser(userId);
        PreferencesImpl newPref = new PreferencesImpl(now,now);
        newPref.setResolver( this);
        if ( userId != null)
        {
            User user = resolve( userId, User.class);
            newPref.setOwner(user);
        }
        newPref.setId( id );
        return newPref;
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

	public synchronized StorageUpdateListener[] getStorageUpdateListeners() {
		return storageUpdateListeners.toArray(new StorageUpdateListener[] {});
	}

	protected Lock writeLock() throws RaplaException {
		return RaplaComponent.lock( lock.writeLock(), 60);
	}

	protected Lock readLock() throws RaplaException {
		return RaplaComponent.lock( lock.readLock(), 20);
	}
	
	protected void unlock(Lock lock) {
		RaplaComponent.unlock( lock );
	}
	
	protected I18nBundle getI18n() {
		return i18n;
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

	
	// End of StorageOperator interface
	protected void checkConnected() throws RaplaException {
		if (!isConnected())
		{
			throw new RaplaException(getI18n().format("error.connection_closed", ""));
		}
	}
	
	@Override
	public Map<String,Entity> getFromId(Collection<String> idSet, boolean throwEntityNotFound)	throws RaplaException {
    	Lock readLock = readLock();
		try
		{
			Map<String, Entity> result= new LinkedHashMap<String,Entity>();
			for ( String id:idSet)
			{
				Entity persistant = (throwEntityNotFound ? cache.resolve(id) : cache.tryResolve(id));
		    	if ( persistant != null)
		    	{
		    		result.put( id,persistant);
		    	}
			}
			return result;
		}
		finally
		{
			unlock(readLock);
		}
	}
	
	@Override
	public Map<Entity,Entity> getPersistant(Collection<? extends Entity> list) throws RaplaException 
	{
		Map<String,Entity> idMap = new LinkedHashMap<String,Entity>();
        for ( Entity key: list)
    	{
     		String id =  key.getId().toString();
     		idMap.put( id, key);
    	}
		Map<Entity,Entity> result = new LinkedHashMap<Entity,Entity>();
		Set<String> keySet = idMap.keySet();
		Map<String,Entity> resolvedList = getFromId( keySet, false);
    	for (Entity entity:resolvedList.values())
    	{
    		String id = entity.getId().toString();
			Entity key = idMap.get( id);
			if ( key != null )
			{
				result.put( key, entity);
			}
    	}
    	return result;
	}
	
	/**
	 * @throws RaplaException  
	 */
	protected void setResolver(Collection<? extends Entity> entities) throws RaplaException {
		for (Entity entity: entities) {
		    if (entity instanceof EntityReferencer)
            {
		        ((EntityReferencer)entity).setResolver(this);
            }
		}
		// It is important to do the read only later because some resolve might involve write to referenced objects
		for (Entity entity: entities) {
		    if (entity instanceof RefEntity)
            {
		        ((RefEntity)entity).setReadOnly();
            }
		}
	}

	protected void testResolve(Collection<? extends Entity> entities) throws EntityNotFoundException {
		EntityStore store = new EntityStore( this, getSuperCategory());
		store.addAll( entities);
		for (Entity entity: entities) {
		    if (entity instanceof EntityReferencer)
		    {
		        ((EntityReferencer)entity).setResolver(store);
		    }
		}
		for (Entity entity: entities) {
		    if (entity instanceof EntityReferencer)
            {
		        testResolve(store, (EntityReferencer)entity);
            }
		}
	}

    protected void testResolve(EntityResolver resolver, EntityReferencer referencer) throws EntityNotFoundException {
        Iterable<ReferenceInfo> referencedIds =referencer.getReferenceInfo();
        for ( ReferenceInfo id:referencedIds)
        {
        	testResolve(resolver, referencer, id);
        }
    }

	private void testResolve(EntityResolver resolver, EntityReferencer obj, ReferenceInfo reference) throws EntityNotFoundException {
        Class<? extends Entity> class1 = reference.getType();
        String id = reference.getId();
        if (tryResolve(resolver,id, class1) == null)
        {
            String prefix = (class1!= null) ? class1.getName() : " unkown type";
            throw new EntityNotFoundException(prefix + " with id " + id + " not found for " + obj);
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
	
	public DynamicType getDynamicType(String key) {
		Lock readLock = null;
		try {
			readLock = readLock();
		} catch (RaplaException e) {
			// this is not so dangerous 
			getLogger().warn("Returning type " + key + " without read lock ");
		}
		try
		{
			return cache.getDynamicType( key);
		}
		finally
		{
			unlock(readLock);
		}
	}
	
	@Override
	public Entity tryResolve(String id) 
	{
	    return tryResolve( id, null);
	}
	
	@Override
    public Entity resolve(String id) throws EntityNotFoundException 
    {
        return resolve( id, null);
    }
	
	@Override
	public <T extends Entity> T tryResolve(String id,Class<T> entityClass) {
		Lock readLock = null;
		try {
			readLock = readLock();
		} catch (RaplaException e) {
			getLogger().warn("Returning object for id  " + id + " without read lock ");
		}
		try
		{
			return tryResolve(cache,id, entityClass);
		}
		finally
		{
			unlock(readLock);
		}
	}
	
	@Override
	public <T extends Entity> T resolve(String id,Class<T> entityClass) throws EntityNotFoundException {
		Lock readLock;
		try {
			readLock = readLock();
		} catch (RaplaException e) {
			throw new EntityNotFoundException( e.getMessage() + " " +e.getCause());
		}
		try
		{
			return resolve(cache,id, entityClass);
		}
		finally
		{
			unlock(readLock);
		}
	}
	
	protected <T extends Entity> T resolve(EntityResolver resolver,String id,Class<T> entityClass) throws EntityNotFoundException {
	    T entity = tryResolve(resolver,id, entityClass);
	    SimpleEntity.checkResolveResult(id, entityClass, entity);
		return entity;
	}

    protected <T extends Entity> T tryResolve(EntityResolver resolver,String id,Class<T> entityClass)  {
        return resolver.tryResolve(id, entityClass);
    }

	/** Writes the UpdateEvent in the cache */
	protected UpdateResult update(final UpdateEvent evt) throws RaplaException {
		HashMap<Entity,Entity> oldEntities = new HashMap<Entity,Entity>();
		// First make a copy of the old entities
		Collection<Entity>storeObjects = new LinkedHashSet<Entity>(evt.getStoreObjects());
		for (Entity entity : storeObjects) 
		{
			Entity persistantEntity = findPersistant(entity);
			if ( persistantEntity == null)
			{
				continue;
			}

			if (getLogger().isDebugEnabled())
			{
				getLogger().debug("Storing old: " + entity);
			}

			if ( persistantEntity instanceof Appointment  || ((persistantEntity instanceof Category) && storeObjects.contains( ((Category) persistantEntity).getParent())))
			{
				throw new RaplaException( persistantEntity.getRaplaType() + " can only be stored via parent entity ");
				// we ingore subentities, because these are added as bellow via addSubentites. The originals will be contain false parent references (to the new parents) when copy is called
			}
			else
			{
				Entity oldEntity = persistantEntity;
				oldEntities.put(persistantEntity, oldEntity);
			}

		}
		Collection<PreferencePatch> preferencePatches = evt.getPreferencePatches();
		for ( PreferencePatch patch:preferencePatches)
		{
		    String userId = patch.getUserId();
            PreferencesImpl oldEntity = cache.getPreferencesForUserId( userId);
            PreferencesImpl clone;
            if ( oldEntity == null)
            {
                clone = newPreferences( userId);
            }
            else
            {
                clone = oldEntity.clone();
            }
            clone.applyPatch( patch);
            oldEntities.put(clone, oldEntity);
            storeObjects.add( clone);

		}
		List<Entity>updatedEntities = new ArrayList<Entity>();
		// Then update the new entities
		for (Entity entity : storeObjects) {
			Entity persistant = findPersistant(entity);
			// do nothing, because the persitantVersion is always read only
			if (persistant == entity) {
				continue;
			}
			if ( entity instanceof Category)
			{
				Category category = (Category)entity;
				CategoryImpl parent = (CategoryImpl)category.getParent();
				if ( parent != null)
				{
					parent.replace( category);
				}
			}
			cache.put(entity);
			updatedEntities.add(entity);	
		}
		Collection<String> removedIds = evt.getRemoveIds();
		Collection<Entity> toRemove = new HashSet<Entity>();
		for (String id: removedIds) {
            Entity persistantVersion = cache.tryResolve(id );
			if (persistantVersion != null) {
			    cache.remove(persistantVersion);
			    ((RefEntity)persistantVersion).setReadOnly();
			    toRemove.add( persistantVersion);
			}
			else if ( ConflictImpl.isConflictId( id ))
			{
			    Date today = getCurrentTimestamp();
			    ConflictImpl conflict = new ConflictImpl( id, today);
			    toRemove.add( conflict );
			}
		}
//		Collection<ConflictImpl> removedConflicts = evt.getRemoveConflicts();
//		for (Conflict conflict: removedConflicts)
//		{
//	           toRemove.add( conflict );
//		}
		setResolver(updatedEntities);
		TimeInterval invalidateInterval = evt.getInvalidateInterval();
		String userId = evt.getUserId();
		return createUpdateResult(oldEntities, updatedEntities, toRemove, invalidateInterval, userId);
	}

    protected Entity findPersistant(Entity entity) {
        @SuppressWarnings("unchecked")
        Class<? extends Entity> typeClass = entity.getRaplaType().getTypeClass();
        Entity persistantEntity = cache.tryResolve(entity.getId(), typeClass);
        return persistantEntity;
    }

	protected UpdateResult createUpdateResult(
			Map<Entity,Entity> oldEntities,
			Collection<Entity>updatedEntities,
			Collection<Entity>toRemove, 
			TimeInterval invalidateInterval,
			String userId) 
					throws EntityNotFoundException {
		User user = null;
		if (userId != null) {
			user = resolve(cache,userId, User.class);
		}

		UpdateResult result = new UpdateResult(user);
		if ( invalidateInterval != null)
		{
			result.setInvalidateInterval( invalidateInterval);
		}
		for (Entity toUpdate:updatedEntities) 
		{
			Entity newEntity = toUpdate;
			Entity oldEntity = oldEntities.get(toUpdate);
			if (oldEntity != null) {
				result.addOperation(new UpdateResult.Change( newEntity, oldEntity));
			} else {
				result.addOperation(new UpdateResult.Add( newEntity));
			}
		}
		
		for (Entity entity:toRemove)
		{
			result.addOperation(new UpdateResult.Remove(entity));
		}

		return result;
	}
}
