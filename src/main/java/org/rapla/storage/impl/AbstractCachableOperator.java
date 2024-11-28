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

import org.jetbrains.annotations.NotNull;
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
import org.rapla.entities.domain.*;
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
import org.rapla.entities.storage.internal.ReferenceHandler;
import org.rapla.entities.storage.internal.SimpleEntity;
import org.rapla.facade.Conflict;
import org.rapla.facade.PeriodModel;
import org.rapla.facade.RaplaComponent;
import org.rapla.facade.internal.ConflictImpl;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
    final protected Map<String, FunctionFactory> functionFactoryMap;
    private volatile Date lastRefreshed;
    final protected PermissionController permissionController;
    private PeriodModelImpl periodModel;

    private PeriodModelImpl periodModelHoliday;

    protected RaplaLock lockManager;
    ThreadLocal<Map<String,Object>> threadContextMap = new ThreadLocal<>();

    public AbstractCachableOperator(Logger logger, RaplaResources i18n, RaplaLocale raplaLocale, Map<String, FunctionFactory> functionFactoryMap,
            Set<PermissionExtension> permissionExtensions, RaplaLock lockManager)
    {
        this.logger = logger;
        this.lockManager = lockManager;
        this.raplaLocale = raplaLocale;
        this.i18n = i18n;
        this.functionFactoryMap = functionFactoryMap;
        //		raplaLocale = context.lookupDeprecated(RaplaLocale.class);
        //		i18n = context.lookupDeprecated(RaplaComponent.RAPLA_RESOURCES);

        Assert.notNull(raplaLocale.getLocale());
        this.permissionController = new PermissionController(permissionExtensions, this);
        cache = new LocalCache(permissionController);
    }

    @Override
    public Map<String, Object> getThreadContextMap() {
        Map<String, Object> map = threadContextMap.get();
        if ( map == null) {
            map = new LinkedHashMap<>();
            threadContextMap.set( map );
        }
        return map;
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
        Set<Entity> singleton = Collections.singleton(o);
        Map<Entity,Entity> list = editObjects(singleton, user);
        Entity first = list.values().iterator().next();
        @SuppressWarnings("unchecked") T casted = (T) first;
        return casted;
    }

    public Map<Entity,Entity> editObjects(Collection<Entity> list, User user) throws RaplaException
    {
        Map<ReferenceInfo<Entity>, Entity> idMap = createReferenceInfoMap(list);
        Map<ReferenceInfo<Entity>, Entity> resolvedList = getFromId(idMap.keySet(), false);
        return editObjects(list, user, resolvedList);
    }

    @Override
    public Promise<Map<Entity,Entity>> editObjectsAsync(Collection<Entity> objList, User user, boolean checkLastChanged) {

        Map<ReferenceInfo<Entity>, Entity> idMap = createReferenceInfoMap(objList);
        return getFromIdAsync(idMap.keySet(), false).thenApply(
                map->
                {
                    if ( checkLastChanged)
                    {
                        checkLastChanged(objList, user, map);
                    }
                    return editObjects(objList, user,map);}
        );
    }


    /** checks if the user that  is the user that last changed the entites
     *
     * @throws RaplaException if the logged in user is not the lastChanged user of any entities. If isNew is false then an exception is also thrown, when an entity is not found in persistent storage
     */
     protected void checkLastChanged(Collection<Entity> objList, User user, Map<ReferenceInfo<Entity>, Entity> map) throws RaplaException {
        for ( Entity entity:objList)
        {
            if ( entity instanceof ModifiableTimestamp)
            {
                Entity persistent = map.get( entity.getReference());
                if ( persistent != null)
                {
                    ReferenceInfo<User> lastChangedBy = ((ModifiableTimestamp) persistent).getLastChangedBy();
                    if (lastChangedBy != null && !user.getReference().equals(lastChangedBy))
                    {
                        final Locale locale = i18n.getLocale();
                        String name = entity instanceof Named ? ((Named) entity).getName( locale) : entity.toString();
                        throw new RaplaException(i18n.format("error.new_version", name));
                    }
                }
            }
        }
    }

    @NotNull
    protected Map<Entity, Entity> editObjects(Collection<Entity> list, User user, Map<ReferenceInfo<Entity>, Entity> resolvedList) {
        Map<Entity,Entity> toEdit = new LinkedHashMap<>();
        for (Entity o : list)
        {
            Entity persistent = resolvedList.get(o.getReference());
            Entity refEntity = editObject(o, persistent, user);
            Entity key = persistent != null ? persistent : o;
            toEdit.put(key,refEntity);
        }
        return toEdit;
    }

    protected <T extends Entity> T editObject(T newObj, T persistent, User user)
    {
        final SimpleEntity clone;
        if (persistent != null)
        {
            clone = (SimpleEntity) persistent.clone();
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
        return (T) refEntity;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public <T extends Entity,S extends Entity> void storeAndRemove(final Collection<T> storeObjects,
                                                                   final Collection<ReferenceInfo<S>> removeObjects, final User user,  boolean forceRessourceDelete) throws RaplaException
    {
        checkConnected();
        UpdateEvent evt = createUpdateEvent(storeObjects, removeObjects, user);
        evt.setForceAllocatableDeletesIgnoreDependencies( forceRessourceDelete );
        dispatch(evt);
    }

    @NotNull
    protected <T extends Entity, S extends Entity> UpdateEvent createUpdateEvent(Collection<T> storeObjects, Collection<ReferenceInfo<S>> removeObjects, User user) throws RaplaException {
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
                evt.addStore(obj);
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
        return evt;
    }



    public Promise<Collection<Conflict>> getConflicts(Reservation reservation)
    {
        Date today = today();
        if (RaplaComponent.isTemplate(reservation))
        {
            return new ResolvedPromise<>(Collections.emptyList());
        }
        final Collection<Allocatable> allocatables = Arrays.asList(reservation.getAllocatables());
        final Collection<Appointment> appointments = Arrays.asList(reservation.getAppointments());
        final Collection<Reservation> ignoreList = Collections.singleton(reservation);
        final Promise<Map<ReferenceInfo<Allocatable>, Map<Appointment, Collection<Appointment>>>> allAllocatableBindingsPromise = getAllAllocatableBindings(allocatables,
                appointments, ignoreList);
        final Promise<Collection<Conflict>> promise = allAllocatableBindingsPromise.thenApply((map) ->
        {
            ArrayList<Conflict> conflictList = new ArrayList<>();
            for (Map.Entry<ReferenceInfo<Allocatable>, Map<Appointment, Collection<Appointment>>> entry : map.entrySet())
            {
                ReferenceInfo<Allocatable> allocatableRef = entry.getKey();
                Allocatable allocatable = tryResolve(allocatableRef);
                if ( allocatable == null) {
                    continue;
                }
                String annotation = allocatable.getAnnotation(ResourceAnnotations.KEY_CONFLICT_CREATION);
                boolean holdBackConflicts = annotation != null && annotation.equals(ResourceAnnotations.VALUE_CONFLICT_CREATION_IGNORE);
                if (holdBackConflicts)
                {
                    continue;
                }
                Map<Appointment, Collection<Appointment>> appointmentMap = entry.getValue();
                for (Map.Entry<Appointment, Collection<Appointment>> appointmentEntry : appointmentMap.entrySet())
                {
                    Appointment appointment = appointmentEntry.getKey();
                    if (reservation.hasAllocatedOn(allocatable, appointment))
                    {
                        Collection<Appointment> conflictionAppointments = appointmentEntry.getValue();
                        if (conflictionAppointments != null)
                        {
                            for (Appointment conflictingAppointment : conflictionAppointments)
                            {

                                Appointment appointment1 = appointment;
                                Appointment appointment2 = conflictingAppointment;
                                ConflictImpl.checkAndAddConflicts(conflictList, allocatable, appointment1, appointment2, today);
                            }
                        }
                    }
                }
            }
            return conflictList;
        });
        return promise;
    }

    public Collection<Allocatable> getDependent(Collection<Allocatable> allocatables)
    {
        final Set<ReferenceInfo<Allocatable>> dependentIds = cache.getDependent(allocatables);
        final Set<Allocatable> result = new LinkedHashSet<>();
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
        Collection<User> collection = cache.getUsers();
        // We return a clone to avoid synchronization Problems
        return new LinkedHashSet<>(collection);
    }

    public Collection<DynamicType> getDynamicTypes() throws RaplaException
    {
        checkLoaded();
        Collection<DynamicType> collection = cache.getDynamicTypes();
        // We return a clone to avoid synchronization Problems
        return new LinkedHashSet<>(collection);
    }


    @Override
    public Promise<AppointmentMapping> queryAppointments(User user, Collection<Allocatable> allocatables, Collection<User> owners, Date start, Date end,
                                                         ClassificationFilter[] reservationFilters, String templateId)
    {
        Collection<Allocatable> allocList;

        {
            if (allocatables != null)
            {
                if (allocatables.isEmpty() && templateId == null && (owners == null || owners.isEmpty()))
                {
                    return new ResolvedPromise<>(new AppointmentMapping());
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
            Promise<AppointmentMapping> query = queryAppointments(callUser, allocList, owners,start, end, reservationFilters, annotationQuery, false);
            return query;
        }
    }


    @Override
    public PeriodModel getPeriodModelFor(String key) throws RaplaException {
        if (periodModel == null || periodModelHoliday == null) {
            DynamicType type = getDynamicType(StorageOperator.PERIOD_TYPE);
            ClassificationFilter[] filters = type.newClassificationFilter().toArray();
            Collection<Allocatable> allPeriods = getAllocatables( filters);
            periodModel = new PeriodModelImpl(this, Category.CATEGORY_ARRAY, allPeriods);
            final Category periodsCategory = PeriodModel.getPeriodsCategory(getSuperCategory());
            Category[] periodsCategoryCategories = periodsCategory != null ? periodsCategory.getCategories() :Category.CATEGORY_ARRAY;
            periodModelHoliday = new PeriodModelImpl(this, periodsCategoryCategories, allPeriods);
        }
        if ( key == null)
        {
            return periodModel;
        }
        else
        {
            return periodModelHoliday;
        }
    }


    public Collection<Allocatable> getAllocatables(ClassificationFilter[] filters) throws RaplaException
    {
        return getAllocatables(filters, -1);
    }

    protected Collection<Allocatable> getAllocatables(ClassificationFilter[] filters, int maxPerType) throws RaplaException
    {
        checkLoaded();
        Collection<Allocatable> allocatables = new HashSet<>(cache.getAllocatables());
        Map<DynamicType,Integer> typeCount = new LinkedHashMap<>();

            Iterator<? extends Classifiable> it = allocatables.iterator();
            while (it.hasNext())
            {
                Classifiable classifiable = it.next();
                // remove internal types if not specified in filters to remain backwards compatibility
                if (filters != null &&!ClassificationFilter.Util.matches(filters, classifiable) || (filters == null && Classifiable.ClassifiableUtil.isInternalType(classifiable) ))
                {
                    if ( maxPerType > 0)
                    {
                        DynamicType type = classifiable.getClassification().getType();
                        Integer integer = typeCount.get(type);
                        if (integer == null)
                        {
                            integer = 1;
                        }
                        else
                        {
                            integer += 1;
                        }
                        if (integer > maxPerType)
                        {
                            it.remove();
                            continue;
                        }
                        typeCount.put(type, integer);
                    }

                    it.remove();
                }
            }
        return allocatables;
    }

    public User getUser(final String username) throws RaplaException
    {
        checkLoaded();
        return cache.getUser(username);
    }

    final protected Map<ReferenceInfo<Preferences>, PreferencesImpl> emptyPreferencesProxy = new HashMap<>();

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

    protected <T extends Entity> Map<ReferenceInfo<T>, T> getFromId(Collection<ReferenceInfo<T>> idSet, boolean throwEntityNotFound)
            throws RaplaException
    {
        checkLoaded();
        Map<ReferenceInfo<T>, T> result = new HashMap<>();
        for (ReferenceInfo<T> id : idSet)
        {
            T persistent = (throwEntityNotFound ? cache.resolve(id) : cache.tryResolve(id));
            if (persistent != null)
            {
                result.put(id, persistent);
            }
        }
        return result;
    }

    public RaplaLock.WriteLock writeLockIfLoaded(String name) throws RaplaException
    {
        final RaplaLock.WriteLock lock = lockManager.writeLock(getClass(),name,60);
        try
        {
            checkLoaded();
        }
        catch (Throwable ex)
        {
            lockManager.unlock(lock);
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

    @Override public Map<Entity, Entity> getPersistent(Collection<? extends Entity> list) throws RaplaException
    {
        Map<ReferenceInfo<Entity>, Entity> idMap = createReferenceInfoMap(list);
        Map<ReferenceInfo<Entity>, Entity> resolvedList = getFromId(idMap.keySet(), false);
        Map<Entity, Entity> result = new LinkedHashMap<>();
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

    @NotNull
    protected Map<ReferenceInfo<Entity>, Entity> createReferenceInfoMap(Collection<? extends Entity> list) {
        Map<ReferenceInfo<Entity>, Entity> idMap = new LinkedHashMap<>();
        for (Entity key : list)
        {
            ReferenceInfo id = key.getReference();
            idMap.put(id, key);
        }
        return idMap;
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
            final String prefix;
            if ( class1 != null) {
                // Resolve might not work because its not in cache yet
                if (ReferenceHandler.tryResolveMissingAllocatable( resolver, id, class1) != null) {
                    return;
                }
                prefix = class1.getName();
            } else {
                prefix = "unkown type";
            }
            if ( isEntityNotFoundWarningFor( reference)) {
                throw new EntityNotFoundException(prefix + " with id " + id + " not found for " + obj);
            }
        }
    }

    /** override if you want to ignore certain reference eg for users on client*/
    protected boolean isEntityNotFoundWarningFor(ReferenceInfo reference) {
        return true;
    }

    protected String getName(Object object)
    {
        if (object == null) {
            return null;
        }
        if (object instanceof Named) {
            return (((Named) object).getName(i18n.getLocale()));
        }
        return object.toString();
    }

    protected String getString(String key)
    {
        return getI18n().getString(key);
    }

    public DynamicType getDynamicType(String key)
    {
        if ( cache != null ) {
            return cache.getDynamicType( key );
        } else if (!isLoaded()) {
            return null;
        } else {
            throw new IllegalStateException("Cache not initialized");
        }
    }

    @Override public <T extends Entity> T tryResolve(String id, Class<T> entityClass)
    {
        return tryResolve(cache, id, entityClass);
    }

    protected <T extends Entity> T resolve(EntityResolver resolver, String id, Class<T> entityClass) throws EntityNotFoundException
    {
        T entity = tryResolve(resolver, id, entityClass);
        SimpleEntity.checkResolveResult(id, entityClass, entity);
        return entity;
    }


    protected <T extends Entity> T tryResolve(EntityResolver resolver, String id, Class<T> entityClass)
    {
        Assert.notNull(id);
        T entity = resolver.tryResolve(id, entityClass);
        return entity;
//        if (entityClass != null && isAllocatableClass(entityClass))
//        {
//            AllocatableImpl unresolved = new AllocatableImpl(null, null);
//            unresolved.setId(id);
//            DynamicType dynamicType = resolver.getDynamicType(UNRESOLVED_RESOURCE_TYPE);
//            if (dynamicType == null)
//            {
//                //throw new IllegalStateException("Unresolved resource type not found");
//                return null;
//            }
//            getLogger().warn("ResourceReference with ID " + id + " obsolete ");
//            Classification newClassification = dynamicType.newClassification();
//            unresolved.setClassification(newClassification);
//            @SuppressWarnings("unchecked") T casted = (T) unresolved;
//            return casted;
//        }
    }


    final protected UpdateResult update(Date since, Date until, Collection<Entity> storeObjects1, Collection<PreferencePatch> preferencePatches,
            Collection<ReferenceInfo> removedIds) throws RaplaException
    {
        HashMap<ReferenceInfo, Entity> oldEntities = new HashMap<>();
        // First make a copyReservations of the old entities
        Collection<Entity> storeObjects = new LinkedHashSet<>(storeObjects1);
        for (Entity entity : storeObjects)
        {
            Entity persistentEntity = findPersistent(entity);
            if (persistentEntity == null)
            {
                continue;
            }

            if (getLogger().isDebugEnabled())
            {
                getLogger().debug("Storing old: " + entity);
            }

            if (persistentEntity instanceof Appointment)// || ((persistantEntity instanceof Category) && storeObjects.contains( ((Category) persistantEntity).getParent())))
            {
                throw new RaplaException(persistentEntity.getTypeClass() + " can only be stored via parent entity ");
                // we ingore subentities, because these are added as bellow via addSubentites. The originals will be contain false parent references (to the new parents) when copyReservations is called
            }
            else
            {
                Entity oldEntity = persistentEntity;
                oldEntities.put(persistentEntity.getReference(), oldEntity);
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
        List<Entity> updatedEntities = new ArrayList<>();
        // Then update the new entities
        for (Entity entity : storeObjects)
        {
            Entity persistent = findPersistent(entity);
            // do nothing, because the persitantVersion is always read only
            if (persistent == entity)
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
            addToCache(entity);
            updatedEntities.add(entity);
        }
        Collection<ReferenceInfo> toRemove = new HashSet<>();
        for (ReferenceInfo id : removedIds)
        {
            Entity persistantVersion = cache.tryResolve(id);
            if (persistantVersion != null)
            {
                cache.remove(persistantVersion);
                oldEntities.put(id, persistantVersion);
                toRemove.add(id);
            }
            else if (id.getType() == Conflict.class || id.getType() == Reservation.class)
            {
                toRemove.add(id);
            }
        }
        setResolver(updatedEntities);
        updatePeriods(updatedEntities, toRemove);
        final UpdateResult updateResult = createUpdateResult(oldEntities, updatedEntities, toRemove, since, until);
        setLastRefreshed(until);
        return updateResult;
    }

    protected void addToCache(Entity entity) {
        cache.put(entity);
    }

    private void updatePeriods(Collection<Entity> updatedEntities,Collection<ReferenceInfo> toRemove) {
        if (periodModel == null || periodModelHoliday == null)
        {
            return;
        }

        try {
            DynamicType type = getDynamicType(StorageOperator.PERIOD_TYPE);
            ClassificationFilter[] filters = type.newClassificationFilter().toArray();
            Collection<Allocatable> allPeriods = getAllocatables( filters);

            if (periodModel != null ) {
                periodModel.update(allPeriods, updatedEntities, toRemove);
            }
            if (periodModelHoliday != null ) {
                periodModelHoliday.update(allPeriods, updatedEntities, toRemove);

            }
        } catch (RaplaException e) {
            getLogger().error("Can't update Period Model", e);
        }

    }

    protected Entity findPersistent(Entity entity)
    {
        @SuppressWarnings("unchecked") Class<? extends Entity> typeClass = entity.getTypeClass();
        Entity persistentEntity = cache.tryResolve(entity.getId(), typeClass);
        return persistentEntity;
    }

    protected UpdateResult createUpdateResult(Map<ReferenceInfo, Entity> oldEntities, Collection<Entity> updatedEntities, Collection<ReferenceInfo> toRemove,
            Date since, Date until) throws EntityNotFoundException
    {
        //		User user = null;
        //		if (userId != null) {
        //			user = resolve(cache,userId, User.class);
        //		}

        Map<ReferenceInfo, Entity> updatedEntityMap = new LinkedHashMap<>();
        for (Entity toUpdate : updatedEntities)
        {
            updatedEntityMap.put(toUpdate.getReference(), toUpdate);
        }
        UpdateResult result = new UpdateResult(since, until, oldEntities, updatedEntityMap);

        for (Entity toUpdate : updatedEntities)
        {
            Entity newEntity = toUpdate;
            boolean createdAfterSince = isCreatedAfterSince(since, newEntity);
            if (createdAfterSince)
            {
                result.addOperation(new UpdateResult.Add(newEntity.getReference()));
            }
            else
            {
                result.addOperation(new UpdateResult.Change(newEntity.getReference()));
            }
        }

        for (ReferenceInfo entity : toRemove)
        {
            result.addOperation(new UpdateResult.Remove(entity));
        }

        return result;
    }

    protected boolean isCreatedAfterSince(Date since, Entity newEntity)
    {
        if (since == null)
        {
            return true;
        }
        if (newEntity instanceof Timestamp)
        {
            Date createTime = ((Timestamp) newEntity).getCreateDate();
            if (createTime != null)
            {
                return createTime.after(since);
                 //logger.info(" create time " + createTime + " since " + since + " afterS " + createdAfterSince);
            }
            else
            {
                getLogger().warn("No create date set for entity " + newEntity.getReference());
                return false;
            }
        }
        else
        {
            getLogger().warn("entity  " + newEntity.getReference() + " does not implement timestamp");
            return false;
        }
    }


    @Override public FunctionFactory getFunctionFactory(String functionName)
    {
        final FunctionFactory functionFactory = functionFactoryMap.get(functionName);
        return functionFactory;
    }

}
