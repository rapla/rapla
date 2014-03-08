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
import org.rapla.components.util.DateTools;
import org.rapla.components.util.TimeInterval;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.IllegalAnnotationException;
import org.rapla.entities.Named;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.internal.PreferencesImpl;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.Template;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.entities.internal.ModifiableTimestamp;
import org.rapla.entities.storage.EntityReferencer;
import org.rapla.entities.storage.ParentEntity;
import org.rapla.entities.storage.RefEntity;
import org.rapla.entities.storage.internal.SimpleEntity;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.logger.Logger;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.CachableStorageOperatorCommand;
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
	
	DynamicTypeImpl unresolvedAllocatableType = new DynamicTypeImpl();
    DynamicTypeImpl anonymousReservationType = new DynamicTypeImpl();
    {
    	try
    	{
	    	{
				DynamicTypeImpl unresolved = unresolvedAllocatableType;
				String key = "unresolved_resource";
				unresolved.setElementKey(key);
				unresolved.setId(DynamicType.TYPE.getId(-1));
				unresolved.setAnnotation(DynamicTypeAnnotations.KEY_NAME_FORMAT,"{"+key + "}");
				unresolved.getName().setName("en", "anonymous");
				unresolved.setAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE, DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON);
				unresolved.setReadOnly( true);
			}
			{
				DynamicTypeImpl unresolved = anonymousReservationType;
				String key = "anonymous";
				unresolved.setElementKey(key);
				unresolved.setId(DynamicType.TYPE.getId( 0));
				unresolved.setAnnotation(DynamicTypeAnnotations.KEY_NAME_FORMAT,"{"+key + "}");
				unresolved.setAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE, DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);
				unresolved.getName().setName("en", "anonymous");
				unresolved.setReadOnly( true);
			}
    	}
    	catch ( IllegalAnnotationException ex)
    	{
    		throw new IllegalStateException( ex.getMessage());
    	}
    }
    	

	public AbstractCachableOperator(RaplaContext context, Logger logger) throws RaplaException {
		this.logger = logger;
		this.context = context;
		raplaLocale = context.lookup(RaplaLocale.class);
		i18n = context.lookup(RaplaComponent.RAPLA_RESOURCES);

		Assert.notNull(raplaLocale.getLocale());
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

	// Implementation of StorageOperator
	public <T extends Entity> T editObject(T o, User user)throws RaplaException {
		Set<Entity>singleton = Collections.singleton( (Entity)o);
		Collection<Entity> list = editObjects( singleton, user);
		return (T) list.iterator().next();
	}

	public Collection<Entity> editObjects(Collection<Entity>list, User user)throws RaplaException {
		checkConnected();
		Collection<Entity> toEdit = new LinkedHashSet<Entity>();
		// read lock
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

	protected Entity editObject(Entity obj, Entity persistant, User user) {
		final SimpleEntity clone;
		if ( persistant != null)
		{
			clone = (SimpleEntity) persistant.clone();
		}
		else
		{
			clone = (SimpleEntity) obj.clone();
		}
		SimpleEntity refEntity = clone;
		if (refEntity instanceof ModifiableTimestamp) {
			((ModifiableTimestamp) refEntity).setLastChanged(getCurrentTimestamp());
			if (user != null) {
				((ModifiableTimestamp) refEntity).setLastChangedBy(user);
			}
		}
		refEntity.setReadOnly(false);
		return (Entity) refEntity;
	}

	public void storeAndRemove(final Collection<Entity> storeObjects,	final Collection<Entity>removeObjects, final User user) throws RaplaException {
		checkConnected();

		UpdateEvent evt = new UpdateEvent();
		if (user != null) {
			evt.setUserId(user.getId());
		}
		for (Entity obj : storeObjects) {
			evt.putStore(obj);
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

	public <T extends RaplaObject> Collection<T> getObjects(Class<T> typeClass)	throws RaplaException {
		checkConnected();
		Lock readLock = readLock();
		try
		{
			RaplaType type = RaplaType.get(typeClass);
			@SuppressWarnings("unchecked")
			Collection<T> collection = (Collection<T>) cache.getCollection(type);
			// We return a clone to avoid synchronization Problems
			return new LinkedHashSet<T>(collection);
		}
		finally
		{
			unlock(readLock);
		}
	}

	public List<Entity> getVisibleEntities(final User user)throws RaplaException {
		checkConnected();
		Lock readLock = readLock();
		try
		{
			ArrayList<Entity>list = new ArrayList<Entity>();
			Collection<Entity>it = cache.getVisibleEntities(user);
			list.addAll( it );
			return list;
		}
		finally
		{
			unlock(readLock);
		}
	}
	
	public Map<String, Template> getTemplateMap() {
        Map<String,Template> templateMap =new LinkedHashMap<String, Template>();
        Collection<Reservation> reservations;
    	Lock readLock;
		try {
			readLock = readLock();
		} catch (RaplaException e) {
			getLogger().error(e.getMessage(), e);
			return templateMap;
		}
		reservations = cache.getCollection(Reservation.class);
		unlock(readLock);

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

	@SuppressWarnings("unchecked")
	public Preferences getPreferences(final User user, boolean createIfNotNull) throws RaplaException {
		checkConnected();
		// Test if user is already stored
		if (user != null) {
			resolve(user.getId());
		}
		Preferences pref;
		Lock readLock = readLock();
		try
		{
			pref = cache.getPreferences(user);
		}
		finally
		{
			unlock(readLock);
		}
		if (pref == null && createIfNotNull) {
			Lock writeLock = writeLock();
			try
			{
				PreferencesImpl newPref = new PreferencesImpl();
				newPref.setResolver( this);
				newPref.setOwner(user);
				String createIdentifier = createIdentifier(Preferences.TYPE,1)[0];
				newPref.setId(createIdentifier);
				pref = newPref;
				cache.put(newPref);
			}
			finally
			{
				unlock(writeLock);
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

	public synchronized StorageUpdateListener[] getStorageUpdateListeners() {
		return storageUpdateListeners.toArray(new StorageUpdateListener[] {});
	}

	protected Lock writeLock() throws RaplaException {
		return RaplaComponent.lock( lock.writeLock(), 60);
	}

	protected Lock readLock() throws RaplaException {
		return RaplaComponent.lock( lock.readLock(), 10);
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

	public void runWithReadLock(CachableStorageOperatorCommand cmd) throws RaplaException
	{
		Lock readLock = readLock();
		try
		{
			cmd.execute( cache );
		}
		finally
		{
			unlock( readLock);
		}
	}

	@Override
	public Map<String,Entity> getFromId(Collection<String> idSet, boolean throwEntityNotFound)	throws RaplaException {
		Map<String, Entity> result= new LinkedHashMap<String,Entity>();
		for ( String id:idSet)
		{
			Entity persistant = (Entity) (throwEntityNotFound ? cache.resolve(id) : cache.tryResolve(id));
	    	if ( persistant != null)
	    	{
	    		result.put( id,persistant);
	    	}
		}
		return result;
	}
	
	@Override
	  public Map<Entity,Entity> getPersistant(Collection<? extends Entity> list) throws RaplaException 
		{
	    	Lock readLock = readLock();
			try
			{
				Map<String,Entity> idMap = new LinkedHashMap<String,Entity>();
		        for ( Entity key: list)
		    	{
		     		String id =  key.getId().toString();
		     		idMap.put( id, key);
		    	}
		    
				
				Map<Entity,Entity> result = new LinkedHashMap<Entity,Entity>();
	    		Map<String,Entity> resolvedList = getFromId( idMap.keySet(), false);
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
			finally
			{
				unlock(readLock);
			}
		}
	
	
	

	protected void resolveEntities(Collection<? extends Entity> entities) throws RaplaException {
		List<Entity>readOnlyList = new ArrayList<Entity>();
		for (Entity obj: entities) {
			((RefEntity)obj).setResolver(this);
//			}
//			catch ( EntityNotFoundException ex)
//			{
//				logEntityNotFound( obj, ex);
//				throw ex;
//			}
			readOnlyList.add(obj);
		}
		// It is important to do the read only later because some resolve might involve write to referenced objects
		for (Entity entity: entities) {
			 ((RefEntity)entity).setReadOnly(true);
		}
	}
	
	/** override for special log handling
	 */
	@SuppressWarnings({ "unused", "unused" })
	protected void logEntityNotFound(Entity obj,  EntityNotFoundException ex) {
	}

	/** Check if the objects are consistent, so that they can be safely stored. */
	protected void checkConsistency(Collection<Entity>entities) throws RaplaException {
		for (Entity entity : entities) {
			for (String referencedIds:((RefEntity)entity).getReferencedIds())
			{
				//FIXME add check later
				//				if (reference instanceof Preferences
//						|| reference instanceof Conflict
//						|| (reference instanceof Reservation && !( entity instanceof Appointment)) 
//						|| (reference instanceof Appointment && !( entity instanceof Reservation)) 
//						)
//				{
//					throw new RaplaException("The current version of Rapla doesn't allow references to objects of type "	+ reference.getRaplaType());
//				}
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

		
	@Override
	public Entity resolveEmail(String emailArg)
			throws EntityNotFoundException {
		Lock readLock;
		try {
			readLock = readLock();
		} catch (RaplaException e) {
			throw new EntityNotFoundException( e.getMessage() + " " +e.getCause());
		}
		try
		{
			Collection<Allocatable> allocatables = cache.getCollection( Allocatable.class);
			for (Allocatable entity: allocatables)
	    	{
	    		final Classification classification = entity.getClassification();
	    		final Attribute attribute = classification.getAttribute("email");
	    		if ( attribute != null)
	    		{
	    			final String email = (String)classification.getValue(attribute);
	    			if ( email != null && email.equals( emailArg))
	    			{
	    				return (Entity)entity;
	    			}
	    		}
	        }
	    	throw new EntityNotFoundException("Object for email " + emailArg + " not found");
		}
		finally
		{
			unlock(readLock);
		}
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
	public Entity tryResolve(String id) {
		Lock readLock = null;
		try {
			readLock = readLock();
		} catch (RaplaException e) {
			getLogger().warn("Returning object for id  " + id + " without read lock ");
		}
		try
		{
			return cache.tryResolve( id);
		}
		finally
		{
			unlock(readLock);
		}
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public Entity resolve(String id) throws EntityNotFoundException {
		Lock readLock;
		try {
			readLock = readLock();
		} catch (RaplaException e) {
			throw new EntityNotFoundException( e.getMessage() + " " +e.getCause());
		}
		try
		{
			return resolveIdWithoutSync(id);
		}
		finally
		{
			unlock(readLock);
		}
	}
	
	protected Entity resolveIdWithoutSync(String id)
			throws EntityNotFoundException {
		return cache.resolve(id);
	}

	/** Writes the UpdateEvent in the cache */
	@SuppressWarnings("unchecked")
	protected UpdateResult update(final UpdateEvent evt) throws RaplaException {
		HashMap<Entity,Entity> oldEntities = new HashMap<Entity,Entity>();
		// First make a copy of the old entities
		Collection<Entity>storeObjects = evt.getStoreObjects();
		for (Entity entity : storeObjects) 
		{
			if (!isStorableInCache( entity))
			{
				continue;
			}
				
			Entity persistantEntity = findInLocalCache(entity);
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

			if ( persistantEntity instanceof Appointment  || ((persistantEntity instanceof Category) && storeObjects.contains( ((Category) persistantEntity).getParent())))
			{
				throw new RaplaException( persistantEntity.getRaplaType() + " can only be stored via parent entity ");
				// we ingore subentities, because these are added as bellow via addSubentites. The originals will be contain false parent references (to the new parents) when copy is called
			}
			else
			{
				Entity oldEntity = persistantEntity;
				oldEntities.put(persistantEntity, oldEntity);
				//addSubentities( oldEntities, oldEntity);
			}

		}
		List<Entity>updatedEntities = new ArrayList<Entity>();
		// Then update the new entities
		for (Entity entity : storeObjects) {
			Entity toUpdate = null;
			boolean storableInCache = isStorableInCache( entity);
			if ( storableInCache)
			{
				increaseVersion(entity);
				toUpdate = findInLocalCache(entity);
				// do nothing, because the persitantVersion is always read only
				if (toUpdate == entity) {
					continue;
				}
				if (toUpdate != null) {
//					if (getLogger().isDebugEnabled())
//					{
//						getLogger().debug("Changing: " + entity);
//					}
					// FIXME ths line is dangerous because it requires synchronisation in the entities e.g. processing the copy method could cause a temporary inconsistant state 
					//((Mementable<Entity>) toUpdate).copy(entity);
				} else {
//					if (getLogger().isDebugEnabled())
//					{
//						getLogger().debug("Adding entity: " + entity);
//					}
					// we clone the entity because it could be modified after calling dispatch
				}
				toUpdate = entity;
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
		Collection<Entity> removeObjects = evt.getRemoveObjects();
		Collection<Entity> toRemove = new HashSet<Entity>();
		for (Entity entity : removeObjects) {
			Entity persistantVersion = null;
			if ( isStorableInCache(entity))
			{
				increaseVersion(entity);
				persistantVersion = findInLocalCache(entity);
				if (persistantVersion != null) {
					cache.remove(persistantVersion);
					((RefEntity)persistantVersion).setReadOnly(true);
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
		for (Entity toUpdate:updatedEntities) 
		{
			if  ( !toUpdate.isPersistant())
			{
				((EntityReferencer)toUpdate).setResolver(this);
			}
		}
		// it is important to set readonly only after a complete resolval of all entities, because it operates recursive and could set an unresolved subentity to read only which is forbidden
		for (Entity toUpdate:updatedEntities) 
		{
			if  ( !toUpdate.isPersistant())
			{
				((RefEntity)toUpdate).setReadOnly( true);
			}
		}

		TimeInterval invalidateInterval = evt.getInvalidateInterval();
		String userId = evt.getUserId();
		return createUpdateResult(oldEntities, updatedEntities, toRemove, invalidateInterval, userId);
	}

	private void addSubentities(HashMap<Entity,Entity> oldEntities,	Entity oldEntity) {
		if (!( oldEntity instanceof ParentEntity))
		{
			return;
		}
		Iterable<Entity>subEntities = ((ParentEntity)oldEntity).getSubEntities();
		for (Entity entity: subEntities)
		{
			Entity persistantEntity = findInLocalCache(entity);
			if ( persistantEntity == null)
			{
				continue;
			}
			oldEntities.put( persistantEntity,entity);
			addSubentities(oldEntities, entity);
		}

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
			user = (User) resolveIdWithoutSync(userId);
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

	/** returns null if no persistant version found */
	protected Entity findInLocalCache(Entity entity)
	{
		return cache.tryResolve(entity.getId());
	}
	
	abstract protected boolean isAddedToUpdateResult(Entity entity);

	abstract protected boolean isStorableInCache(Entity entity);

	protected void increaseVersion(Entity entity) {
		SimpleEntity e = (SimpleEntity) entity;
		e.setVersion(e.getVersion() + 1);
		if (getLogger().isDebugEnabled()) {
			getLogger().debug("Increasing Version for " + e + " to " + e.getVersion());
		}
	}
	
	public DynamicType getUnresolvedAllocatableType() 
	{
		return unresolvedAllocatableType;
	}

	public DynamicType getAnonymousReservationType() 
	{
		return anonymousReservationType;
	}

}
