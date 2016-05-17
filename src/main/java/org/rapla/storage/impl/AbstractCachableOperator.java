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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.rapla.RaplaResources;
import org.rapla.components.util.Assert;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.Named;
import org.rapla.entities.Timestamp;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.internal.PreferencesImpl;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.permission.PermissionExtension;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.entities.extensionpoints.FunctionFactory;
import org.rapla.entities.internal.ModifiableTimestamp;
import org.rapla.entities.storage.EntityReferencer;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.RefEntity;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.entities.storage.internal.SimpleEntity;
import org.rapla.facade.Conflict;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.ResolvedPromise;
import org.rapla.storage.LocalCache;
import org.rapla.storage.PermissionController;
import org.rapla.storage.PreferencePatch;
import org.rapla.storage.StorageOperator;
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

public abstract class AbstractCachableOperator implements StorageOperator
{

    final protected RaplaLocale raplaLocale;

    final protected LocalCache cache;
    final protected RaplaResources i18n;
    final protected Logger logger;
    final protected ReadWriteLock lock = new ReentrantReadWriteLock();
    final protected Map<String, FunctionFactory> functionFactoryMap;
    private volatile Date lastRefreshed;
    final protected PermissionController permissionController;

    public AbstractCachableOperator(Logger logger, RaplaResources i18n, RaplaLocale raplaLocale, Map<String, FunctionFactory> functionFactoryMap,
            Set<PermissionExtension> permissionExtensions)
    {
        this.logger = logger;
        this.raplaLocale = raplaLocale;
        this.i18n = i18n;
        this.functionFactoryMap = functionFactoryMap;
        //		raplaLocale = context.lookupDeprecated(RaplaLocale.class);
        //		i18n = context.lookupDeprecated(RaplaComponent.RAPLA_RESOURCES);

        Assert.notNull(raplaLocale.getLocale());
        this.permissionController = new PermissionController(permissionExtensions, this);
        cache = new LocalCache(permissionController);
    }

    public PermissionController getPermissionController()
    {
        return permissionController;
    }

    public Logger getLogger()
    {
        return logger;
    }

    public Date getLastRefreshed()
    {
        return lastRefreshed;
    }

    protected void setLastRefreshed(Date lastRefreshed)
    {
        this.lastRefreshed = lastRefreshed;
    }

    // Implementation of StorageOperator
    public <T extends Entity> T editObject(T o, User user) throws RaplaException
    {
        Set<Entity> singleton = Collections.singleton((Entity) o);
        Collection<Entity> list = editObjects(singleton, user);
        Entity first = list.iterator().next();
        @SuppressWarnings("unchecked") T casted = (T) first;
        return casted;
    }

    public Collection<Entity> editObjects(Collection<Entity> list, User user) throws RaplaException
    {
        Collection<Entity> toEdit = new LinkedHashSet<Entity>();
        Map<Entity, Entity> persistantMap = getPersistant(list);
        // read unlock
        for (Entity o : list)
        {
            Entity persistant = persistantMap.get(o);
            Entity refEntity = editObject(o, persistant, user);
            toEdit.add(refEntity);
        }
        return toEdit;
    }

    protected Entity editObject(Entity newObj, Entity persistant, User user)
    {
        final SimpleEntity clone;
        if (persistant != null)
        {
            clone = (SimpleEntity) persistant.clone();
        }
        else
        {
            clone = (SimpleEntity) newObj.clone();
        }
        SimpleEntity refEntity = clone;
        if (refEntity instanceof ModifiableTimestamp && user != null)
        {
            refEntity.setLastChangedBy(user);
        }
        return (Entity) refEntity;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public <T extends Entity,S extends Entity> void storeAndRemove(final Collection<T> storeObjects,
            final Collection<ReferenceInfo<S>> removeObjects, final User user) throws RaplaException
    {
        checkConnected();

        UpdateEvent evt = new UpdateEvent();
        if (user != null)
        {
            evt.setUserId(user.getId());
        }
        for (Entity obj : storeObjects)
        {
            if (obj instanceof Preferences)
            {
                PreferencePatch patch = ((PreferencesImpl) obj).getPatch();
                evt.putPatch(patch);
            }
            else
            {
                evt.putStore(obj);
            }
        }
        for (ReferenceInfo<?> entity : removeObjects)
        {
            Class<? extends Entity> type = entity.getType();
            if (Appointment.class == type || Attribute.class == type)
            {
                String name = getName(entity);
                throw new RaplaException(getI18n().format("error.remove_object", name));
            }
            evt.putRemoveId(entity);
        }
        dispatch(evt);
    }

    public Collection<Allocatable> getDependent(Collection<Allocatable> allocatables)
    {
        final Set<ReferenceInfo<Allocatable>> dependentIds = cache.getDependent(allocatables);
        final Set<Allocatable> result = new LinkedHashSet<Allocatable>();
        for (ReferenceInfo<Allocatable> dependentId : dependentIds)
        {
            Allocatable allocatable = tryResolve(dependentId);
            if (allocatable != null)
            {
                result.add(allocatable);
            }
        }
        return result;
    }

    /**
     * implement this method to implement the persistent mechanism. By default it
     * calls <li>check()</li> <li>update()</li> <li>fireStorageUpdate()</li> <li>
     * fireTriggerEvents()</li> You should not call dispatch directly from the
     * client. Use storeObjects and removeObjects instead.
     */
    public abstract void dispatch(UpdateEvent evt) throws RaplaException;

    public Collection<User> getUsers() throws RaplaException
    {
        checkLoaded();
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

    public Collection<DynamicType> getDynamicTypes() throws RaplaException
    {
        checkLoaded();
        Lock readLock = readLock();
        try
        {
            Collection<DynamicType> collection = cache.getDynamicTypes();
            // We return a clone to avoid synchronization Problems
            return new ArrayList<DynamicType>(collection);
        }
        finally
        {
            unlock(readLock);
        }
    }

    public Promise<Map<Allocatable, Collection<Appointment>>> queryAppointments(User user, Collection<Allocatable> allocatables, Date start, Date end,
            ClassificationFilter[] reservationFilters, String templateId)
    {
        Collection<Allocatable> allocList;

        {
            if (allocatables != null)
            {
                if (allocatables.size() == 0 && templateId == null)
                {
                    return new ResolvedPromise<>(Collections.emptyMap());
                }
                allocList = allocatables;
            }
            else
            {
                allocList = Collections.emptyList();
            }
        }

        if (templateId != null)
        {
            final Allocatable template = tryResolve(templateId, Allocatable.class);
            if (template == null)
            {
                return new ResolvedPromise<>(new RaplaException("Can't load template with id " + templateId));
            }
            else
            {
                allocList = new ArrayList<>(allocList);
                allocList.add(template);
            }
        }
        {
            final Map<String, String> annotationQuery = null;
            final User callUser = templateId != null ? null : user;
            Promise<Map<Allocatable, Collection<Appointment>>> query = queryAppointments(callUser, allocList, start, end, reservationFilters, annotationQuery);
            return query;
        }
    }

    public Collection<Allocatable> getAllocatables(ClassificationFilter[] filters) throws RaplaException
    {
        checkLoaded();
        Collection<Allocatable> allocatables = new LinkedHashSet<Allocatable>();
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
        if (filters == null)
        {
            // remove internal types if not specified in filters to remain backwards compatibility
            Iterator<? extends Classifiable> it = allocatables.iterator();
            while (it.hasNext())
            {
                Classifiable classifiable = it.next();
                if (Classifiable.ClassifiableUtil.isInternalType(classifiable))
                {
                    it.remove();
                }
            }
        }
        else
        {

            Iterator<? extends Classifiable> it = allocatables.iterator();
            while (it.hasNext())
            {
                Classifiable classifiable = it.next();
                if (!ClassificationFilter.Util.matches(filters, classifiable))
                {
                    it.remove();
                }
            }
        }
        return allocatables;
    }

    protected boolean isInFilter(Classifiable classifiable, ClassificationFilter[] filters)
    {
        if (filters == null)
        {
            return (!Classifiable.ClassifiableUtil.isInternalType(classifiable));
        }
        else
        {
            return ClassificationFilter.Util.matches(filters, classifiable);
        }
    }

    public User getUser(final String username) throws RaplaException
    {
        checkLoaded();
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

    protected Map<ReferenceInfo<Preferences>, PreferencesImpl> emptyPreferencesProxy = new HashMap<ReferenceInfo<Preferences>, PreferencesImpl>();

    public Preferences getPreferences(final User user, boolean createIfNotNull) throws RaplaException
    {
        checkLoaded();
        // Test if user is already stored
        if (user != null)
        {
            resolve(user.getId(), User.class);
        }
        String userId = user != null ? user.getId() : null;
        ReferenceInfo<Preferences> preferenceId = PreferencesImpl.getPreferenceIdFromUser(userId);
        PreferencesImpl pref = (PreferencesImpl) cache.tryResolve(preferenceId);
        if (pref == null && createIfNotNull)
        {
            synchronized (emptyPreferencesProxy)
            {
                PreferencesImpl preferencesImpl = emptyPreferencesProxy.get(preferenceId);
                if (preferencesImpl != null)
                {
                    return preferencesImpl;
                }
            }
        }

        if (pref == null && createIfNotNull)
        {
            synchronized (emptyPreferencesProxy)
            {
                PreferencesImpl newPref = newPreferences(userId);
                newPref.setReadOnly();
                pref = newPref;
                emptyPreferencesProxy.put(preferenceId, pref);
            }
        }
        return pref;
    }

    private PreferencesImpl newPreferences(final String userId) throws EntityNotFoundException
    {
        Date now = getCurrentTimestamp();
        ReferenceInfo<Preferences> id = PreferencesImpl.getPreferenceIdFromUser(userId);
        PreferencesImpl newPref = new PreferencesImpl(now, now);
        newPref.setResolver(this);
        if (userId != null)
        {
            User user = resolve(userId, User.class);
            newPref.setOwner(user);
        }
        newPref.setId(id.getId());
        return newPref;
    }

    public Category getSuperCategory()
    {
        return cache.getSuperCategory();
    }

    protected Lock writeLock() throws RaplaException
    {
        final Lock lock = RaplaComponent.lock(this.lock.writeLock(), 60);
        try
        {
            checkLoaded();
        }
        catch (Throwable ex)
        {
            unlock(lock);
            if (ex instanceof RaplaException)
            {
                throw ex;
            }
            else
            {
                throw new RaplaException(ex);
            }
        }
        return lock;
    }

    protected Lock readLock() throws RaplaException
    {
        final Lock lock = RaplaComponent.lock(this.lock.readLock(), 20);
        /*
        try
		{
			checkLoaded();
		}
		catch (Throwable ex)
		{
			unlock( lock);
			if ( ex instanceof  RaplaException)
			{
				throw ex;
			}
			else
			{
				throw new RaplaException( ex);
			}
		}*/
        return lock;
    }

    protected void unlock(Lock lock)
    {
        RaplaComponent.unlock(lock);
    }

    protected RaplaResources getI18n()
    {
        return i18n;
    }

    // End of StorageOperator interface
    protected void checkConnected() throws RaplaException
    {
        if (!isConnected())
        {
            throw new RaplaException(getI18n().format("error.connection_closed", ""));
        }
    }

    protected void checkLoaded() throws RaplaException
    {
        if (!isLoaded())
        {
            throw new RaplaException(getI18n().format("error.connection_closed", ""));
        }
    }

    public abstract boolean isLoaded();

    @Override public <T extends Entity> Map<ReferenceInfo<T>, T> getFromId(Collection<ReferenceInfo<T>> idSet, boolean throwEntityNotFound)
            throws RaplaException
    {
        checkLoaded();
        Lock readLock = readLock();
        try
        {
            Map<ReferenceInfo<T>, T> result = new LinkedHashMap();
            for (ReferenceInfo<T> id : idSet)
            {
                T persistant = (throwEntityNotFound ? cache.resolve(id) : cache.tryResolve(id));
                if (persistant != null)
                {
                    result.put(id, persistant);
                }
            }
            return result;
        }
        finally
        {
            unlock(readLock);
        }
    }

    @Override public Map<Entity, Entity> getPersistant(Collection<? extends Entity> list) throws RaplaException
    {
        Map<ReferenceInfo<Entity>, Entity> idMap = new LinkedHashMap<ReferenceInfo<Entity>, Entity>();
        for (Entity key : list)
        {
            ReferenceInfo id = key.getReference();
            idMap.put(id, key);
        }
        Map<Entity, Entity> result = new LinkedHashMap<Entity, Entity>();
        Set<ReferenceInfo<Entity>> keySet = idMap.keySet();
        Map<ReferenceInfo<Entity>, Entity> resolvedList = getFromId(keySet, false);
        for (Entity entity : resolvedList.values())
        {
            ReferenceInfo reference = entity.getReference();
            Entity key = idMap.get(reference);
            if (key != null)
            {
                result.put(key, entity);
            }
        }
        return result;
    }

    /**
     * @throws RaplaException
     */
    protected void setResolver(Collection<? extends Entity> entities) throws RaplaException
    {
        for (Entity entity : entities)
        {
            if (entity instanceof DynamicType)
            {
                ((DynamicTypeImpl) entity).setOperator(this);
            }
            if (entity instanceof EntityReferencer)
            {
                ((EntityReferencer) entity).setResolver(this);
            }
        }
        // It is important to do the read only later because some resolve might involve write to referenced objects
        for (Entity entity : entities)
        {
            if (entity instanceof RefEntity)
            {
                ((RefEntity) entity).setReadOnly();
            }
        }
    }

    protected void testResolve(Collection<? extends Entity> entities) throws EntityNotFoundException
    {
        EntityStore store = new EntityStore(this);
        store.addAll(entities);
        for (Entity entity : entities)
        {
            if (entity instanceof EntityReferencer)
            {
                ((EntityReferencer) entity).setResolver(store);
            }
            if (entity instanceof DynamicType)
            {
                ((DynamicTypeImpl) entity).setOperator(this);
            }
        }
        for (Entity entity : entities)
        {

            if (entity instanceof EntityReferencer)
            {
                testResolve(store, (EntityReferencer) entity);
            }

        }
    }

    protected void testResolve(EntityResolver resolver, EntityReferencer referencer) throws EntityNotFoundException
    {
        Iterable<ReferenceInfo> referencedIds = referencer.getReferenceInfo();
        for (ReferenceInfo id : referencedIds)
        {
            testResolve(resolver, referencer, id);
        }
    }

    protected void testResolve(EntityResolver resolver, EntityReferencer obj, ReferenceInfo reference) throws EntityNotFoundException
    {
        Class<? extends Entity> class1 = reference.getType();
        String id = reference.getId();
        if (tryResolve(resolver, id, class1) == null)
        {
            String prefix = (class1 != null) ? class1.getName() : " unkown type";
            throw new EntityNotFoundException(prefix + " with id " + id + " not found for " + obj);
        }
    }

    protected String getName(Object object)
    {
        if (object == null)
            return null;
        if (object instanceof Named)
            return (((Named) object).getName(i18n.getLocale()));
        return object.toString();
    }

    protected String getString(String key)
    {
        return getI18n().getString(key);
    }

    public DynamicType getDynamicType(String key)
    {
        if (!isLoaded())
        {
            return null;
        }
        Lock readLock = null;
        try
        {
            readLock = readLock();
        }
        catch (RaplaException e)
        {
            // this is not so dangerous
            getLogger().warn("Returning type " + key + " without read lock ");
        }
        try
        {
            return cache.getDynamicType(key);
        }
        finally
        {
            unlock(readLock);
        }
    }

    @Override public <T extends Entity> T tryResolve(ReferenceInfo<T> referenceInfo)
    {
        final Class<T> type = (Class<T>) referenceInfo.getType();
        return tryResolve(referenceInfo.getId(), type);
    }

    @Override public <T extends Entity> T resolve(ReferenceInfo<T> referenceInfo) throws EntityNotFoundException
    {
        final Class<T> type = (Class<T>) referenceInfo.getType();
        return resolve(referenceInfo.getId(), type);
    }

    @Override public <T extends Entity> T tryResolve(String id, Class<T> entityClass)
    {
        Lock readLock = null;
        try
        {
            readLock = readLock();
        }
        catch (RaplaException e)
        {
            getLogger().warn("Returning object for id  " + id + " without read lock ");
        }
        try
        {
            return tryResolve(cache, id, entityClass);
        }
        finally
        {
            unlock(readLock);
        }
    }

    @Override public <T extends Entity> T resolve(String id, Class<T> entityClass) throws EntityNotFoundException
    {
        Lock readLock;
        try
        {
            readLock = readLock();
        }
        catch (RaplaException e)
        {
            throw new EntityNotFoundException(e.getMessage() + " " + e.getCause());
        }
        try
        {
            return resolve(cache, id, entityClass);
        }
        finally
        {
            unlock(readLock);
        }
    }

    protected <T extends Entity> T resolve(EntityResolver resolver, String id, Class<T> entityClass) throws EntityNotFoundException
    {
        T entity = tryResolve(resolver, id, entityClass);
        SimpleEntity.checkResolveResult(id, entityClass, entity);
        return entity;
    }

    protected <T extends Entity> T tryResolve(EntityResolver resolver, String id, Class<T> entityClass)
    {
        return resolver.tryResolve(id, entityClass);
    }

    final protected UpdateResult update(Date since, Date until, Collection<Entity> storeObjects1, Collection<PreferencePatch> preferencePatches,
            Collection<ReferenceInfo> removedIds) throws RaplaException
    {
        HashMap<ReferenceInfo, Entity> oldEntities = new HashMap<ReferenceInfo, Entity>();
        // First make a copy of the old entities
        Collection<Entity> storeObjects = new LinkedHashSet<Entity>(storeObjects1);
        for (Entity entity : storeObjects)
        {
            Entity persistantEntity = findPersistant(entity);
            if (persistantEntity == null)
            {
                continue;
            }

            if (getLogger().isDebugEnabled())
            {
                getLogger().debug("Storing old: " + entity);
            }

            if (persistantEntity instanceof Appointment)// || ((persistantEntity instanceof Category) && storeObjects.contains( ((Category) persistantEntity).getParent())))
            {
                throw new RaplaException(persistantEntity.getTypeClass() + " can only be stored via parent entity ");
                // we ingore subentities, because these are added as bellow via addSubentites. The originals will be contain false parent references (to the new parents) when copy is called
            }
            else
            {
                Entity oldEntity = persistantEntity;
                oldEntities.put(persistantEntity.getReference(), oldEntity);
            }

        }
        for (PreferencePatch patch : preferencePatches)
        {
            String userId = patch.getUserId();
            PreferencesImpl oldEntity = cache.getPreferencesForUserId(userId);
            PreferencesImpl clone;
            if (oldEntity == null)
            {
                clone = newPreferences(userId);
            }
            else
            {
                clone = oldEntity.clone();
            }
            clone.applyPatch(patch);
            oldEntities.put(clone.getReference(), oldEntity);
            storeObjects.add(clone);

        }
        List<Entity> updatedEntities = new ArrayList<Entity>();
        // Then update the new entities
        for (Entity entity : storeObjects)
        {
            Entity persistant = findPersistant(entity);
            // do nothing, because the persitantVersion is always read only
            if (persistant == entity)
            {
                continue;
            }
            //if ( entity instanceof Category)
            //{
            //	Category category = (Category)entity;
            //	CategoryImpl parent = (CategoryImpl)category.getParent();
            //	if ( parent != null)
            //	{
            //		parent.replace( category);
            //	}
            //}

            cache.put(entity);
            updatedEntities.add(entity);
        }
        Collection<ReferenceInfo> toRemove = new HashSet<ReferenceInfo>();
        for (ReferenceInfo id : removedIds)
        {
            Entity persistantVersion = cache.tryResolve(id);
            if (persistantVersion != null)
            {
                cache.remove(persistantVersion);
                oldEntities.put(id, persistantVersion);
                toRemove.add(id);
            }
            else if (id.getType() == Conflict.class)
            {
                toRemove.add(id);
            }
        }
        setResolver(updatedEntities);
        final UpdateResult updateResult = createUpdateResult(oldEntities, updatedEntities, toRemove, since, until);
        setLastRefreshed(until);
        return updateResult;
    }

    protected Entity findPersistant(Entity entity)
    {
        @SuppressWarnings("unchecked") Class<? extends Entity> typeClass = entity.getTypeClass();
        Entity persistantEntity = cache.tryResolve(entity.getId(), typeClass);
        return persistantEntity;
    }

    protected UpdateResult createUpdateResult(Map<ReferenceInfo, Entity> oldEntities, Collection<Entity> updatedEntities, Collection<ReferenceInfo> toRemove,
            Date since, Date until) throws EntityNotFoundException
    {
        //		User user = null;
        //		if (userId != null) {
        //			user = resolve(cache,userId, User.class);
        //		}

        Map<ReferenceInfo, Entity> updatedEntityMap = new LinkedHashMap<ReferenceInfo, Entity>();
        for (Entity toUpdate : updatedEntities)
        {
            updatedEntityMap.put(toUpdate.getReference(), toUpdate);
        }
        UpdateResult result = new UpdateResult(since, until, oldEntities, updatedEntityMap);

        for (Entity toUpdate : updatedEntities)
        {
            Entity newEntity = toUpdate;
            boolean createdBeforeSince = isCreatedBeforeSince(since, newEntity);
            if (createdBeforeSince)
            {
                result.addOperation(new UpdateResult.Change(newEntity.getReference()));
            }
            else
            {

                result.addOperation(new UpdateResult.Add(newEntity.getReference()));
            }
        }

        for (ReferenceInfo entity : toRemove)
        {
            result.addOperation(new UpdateResult.Remove(entity));
        }

        return result;
    }

    protected boolean isCreatedBeforeSince(Date since, Entity newEntity)
    {
        boolean createdBeforeSince;
        if (since == null)
        {
            return false;
        }
        if (newEntity instanceof Timestamp)
        {
            Date createTime = ((Timestamp) newEntity).getCreateDate();
            if (createTime != null)
            {
                createdBeforeSince = createTime.before(since);
            }
            else
            {
                getLogger().warn("No create date set for entity " + newEntity.getReference());
                createdBeforeSince = true;
            }
        }
        else
        {
            getLogger().warn("entity  " + newEntity.getReference() + " does not implement timestamp");
            createdBeforeSince = true;
        }
        return createdBeforeSince;
    }

    @Override public FunctionFactory getFunctionFactory(String functionName)
    {
        final FunctionFactory functionFactory = functionFactoryMap.get(functionName);
        return functionFactory;
    }

}
