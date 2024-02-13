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

package org.rapla.storage.impl.server;

import io.reactivex.rxjava3.functions.Action;
import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.SortedBidiMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.apache.commons.collections4.bidimap.DualTreeBidiMap;
import org.jetbrains.annotations.NotNull;
import org.rapla.RaplaResources;
import org.rapla.components.util.Assert;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.IOUtil;
import org.rapla.components.util.SerializableDateTimeFormat;
import org.rapla.components.util.Tools;
import org.rapla.components.util.iterator.IterableChain;
import org.rapla.entities.Annotatable;
import org.rapla.entities.Category;
import org.rapla.entities.DependencyException;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.LastChangedTimestamp;
import org.rapla.entities.MultiLanguageName;
import org.rapla.entities.Named;
import org.rapla.entities.Ownable;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.Timestamp;
import org.rapla.entities.UniqueKeyException;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.internal.PreferencesImpl;
import org.rapla.entities.domain.*;
import org.rapla.entities.domain.Permission.AccessLevel;
import org.rapla.entities.domain.PermissionContainer.Util;
import org.rapla.entities.domain.internal.AllocatableImpl;
import org.rapla.entities.domain.internal.AppointmentImpl;
import org.rapla.entities.domain.internal.PermissionImpl;
import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.entities.domain.permission.PermissionExtension;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.ConstraintIds;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.dynamictype.internal.AttributeImpl;
import org.rapla.entities.dynamictype.internal.ClassificationImpl;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.entities.extensionpoints.FunctionFactory;
import org.rapla.entities.internal.CategoryImpl;
import org.rapla.entities.internal.UserImpl;
import org.rapla.entities.storage.CannotExistWithoutTypeException;
import org.rapla.entities.storage.DynamicTypeDependant;
import org.rapla.entities.storage.EntityReferencer;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.ExternalSyncEntity;
import org.rapla.entities.storage.RefEntity;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.entities.storage.internal.ExternalSyncEntityImpl;
import org.rapla.entities.storage.internal.ReferenceHandler;
import org.rapla.entities.storage.internal.SimpleEntity;
import org.rapla.facade.Conflict;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.Disposable;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorPlugin;
import org.rapla.rest.JsonParserWrapper;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.ResolvedPromise;
import org.rapla.server.PromiseWait;
import org.rapla.server.internal.TimeZoneConverterImpl;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.CachableStorageOperatorCommand;
import org.rapla.storage.IdCreator;
import org.rapla.storage.LocalCache;
import org.rapla.storage.PreferencePatch;
import org.rapla.storage.RaplaNewVersionException;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.StorageOperator;
import org.rapla.storage.UpdateEvent;
import org.rapla.storage.UpdateOperation;
import org.rapla.storage.UpdateResult;
import org.rapla.storage.UpdateResult.Add;
import org.rapla.storage.UpdateResult.Change;
import org.rapla.storage.UpdateResult.Remove;
import org.rapla.storage.impl.AbstractCachableOperator;
import org.rapla.storage.impl.DefaultRaplaLock;
import org.rapla.storage.impl.EntityStore;
import org.rapla.storage.impl.RaplaLock;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class LocalAbstractCachableOperator extends AbstractCachableOperator implements Disposable, CachableStorageOperator, IdCreator
{

    InitStatus connectStatus = InitStatus.Disconnected;
    // some indexMaps
    AppointmentMapClass appointmentBindings;
    private BidiMap<String, ReferenceInfo> externalIds;

    protected enum InitStatus
    {
        Disconnected,
        Loading,
        Loaded,
        Connected
    }

    /**
     * The duration which the history must support, only one older Entry than the specified time are needed.
     */
    public static final long HISTORY_DURATION = DateTools.MILLISECONDS_PER_WEEK;

    /**
     * set encryption if you want to enable password encryption. Possible values
     * are "sha" or "md5".
     */
    private final String encryption = "sha-1";
    private ConflictFinder conflictFinder;
    //private SortedSet<LastChangedTimestamp> timestampSet;
    // we need a bidi to sort the values instead of the keys
    protected final EntityHistory history;
    private SortedBidiMap<String, DeleteUpdateEntry> deleteUpdateSet;

    private TimeZone systemTimeZone = TimeZone.getDefault();
    private final CommandScheduler scheduler;
    private final List< io.reactivex.rxjava3.disposables.Disposable> scheduledTasks = new ArrayList<>();
    private Date connectStart;
    private final DefaultRaplaLock disconnectLock;
    private final PromiseWait promiseWait;

    public LocalAbstractCachableOperator(Logger logger, PromiseWait promiseWait, RaplaResources i18n, RaplaLocale raplaLocale, CommandScheduler scheduler,
            Map<String, FunctionFactory> functionFactoryMap, Set<PermissionExtension> permissionExtensions)
    {
        super(logger, i18n, raplaLocale, functionFactoryMap, permissionExtensions, new DefaultRaplaLock(logger));
        this.scheduler = scheduler;
        this.promiseWait = promiseWait;
        disconnectLock = new DefaultRaplaLock(logger);
        //context.lookupDeprecated( CommandScheduler.class);
        this.history = new EntityHistory();
        appointmentBindings = new AppointmentMapClass(logger);
    }

    @Override
    final public boolean isConnected()
    {
        return connectStatus == InitStatus.Connected;
    }

    @Override
    public boolean isLoaded()
    {
        return connectStatus.ordinal() >= InitStatus.Loaded.ordinal();
    }

    protected void changeStatus(InitStatus status)
    {
        connectStatus = status;
        getLogger().debug("Initstatus " + status);
    }

    @Override
    public <T extends Entity> List<ReferenceInfo<T>> createIdentifier(Class<T> raplaType, int count) throws RaplaException
    {
        List<ReferenceInfo<T>> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
        {
            final String id = createId(raplaType);
            ids.add(new ReferenceInfo<>(id, raplaType));
        }
        return ids;
    }


    @Override
    public <T extends Entity> Promise<List<ReferenceInfo<T>>> createIdentifierAsync(Class<T> raplaType, int count)
    {
        try
        {
            return new ResolvedPromise<>(createIdentifier(raplaType, count));
        }
        catch (RaplaException e)
        {
            return new ResolvedPromise<>(e);
        }
    }


    @Override
    public <T> T waitForWithRaplaException(Promise<T> promise, int timeoutInMillis) throws RaplaException
    {
        return promiseWait.waitForWithRaplaException(promise, timeoutInMillis);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public <T extends Entity,S extends Entity> Promise<Void> storeAndRemoveAsync(final Collection<T> storeObjects,
                                                                                 final Collection<ReferenceInfo<S>> removeObjects, final User user, boolean forceRessourceDelete)
    {
        return scheduler.run(()
                ->
        {

        });
    }

    @Override
    public <T extends Entity> Promise<Map<ReferenceInfo<T>,T>> getFromIdAsync(Collection<ReferenceInfo<T>> idSet, boolean throwEntityNotFound)
    {
        return scheduler.supply(()->getFromId( idSet, throwEntityNotFound));
    }

    public CommandScheduler getScheduler()
    {
        return scheduler;
    }

    protected void addInternalTypes(LocalCache cache) throws RaplaException
    {
        {
            DynamicTypeImpl type = new DynamicTypeImpl();
            String key = UNRESOLVED_RESOURCE_TYPE;
            type.setKey(key);
            type.setId(key);
            type.setAnnotation(DynamicTypeAnnotations.KEY_NAME_FORMAT, "{p->type(p)}");
            final MultiLanguageName name = type.getName();
            type.setAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE, DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE);
            type.setResolver(this);
            for (String lang : raplaLocale.getAvailableLanguages())
            {
                Locale locale = new Locale(lang);
                name.setName(lang, i18n.getString("not_visible_or_deleted", locale));
            }
            {
                Permission newPermission = type.newPermission();
                newPermission.setAccessLevel(Permission.READ_TYPE);
                type.addPermission(newPermission);
            }
            type.setReadOnly();
            cache.put(type);
        }
        {
            DynamicTypeImpl type = new DynamicTypeImpl();
            String key = ANONYMOUSEVENT_TYPE;
            type.setKey(key);
            type.setId(key);
            type.setAnnotation(DynamicTypeAnnotations.KEY_NAME_FORMAT, "{p->type(p)}");
            type.setAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE, DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);
            final MultiLanguageName name = type.getName();
            type.setResolver(this);
            for (String lang : raplaLocale.getAvailableLanguages())
            {
                Locale locale = new Locale(lang);
                name.setName(lang, i18n.getString("not_visible", locale));
            }
            {
                Permission newPermission = type.newPermission();
                newPermission.setAccessLevel(Permission.READ_TYPE);
                type.addPermission(newPermission);
            }
            type.setReadOnly();
            cache.put(type);
        }

        {
            DynamicTypeImpl type = new DynamicTypeImpl();
            String key = DEFAULT_USER_TYPE;
            type.getName().setName("en","user");
            type.setKey(key);
            type.setId(key);
            type.setAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE, DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RAPLATYPE);
            //type.setAnnotation(DynamicTypeAnnotations.KEY_TRANSFERED_TO_CLIENT, DynamicTypeAnnotations.VALUE_TRANSFERED_TO_CLIENT_NEVER);
            addAttributeWithInternalId(type, "surname", AttributeType.STRING);
            addAttributeWithInternalId(type, "firstname", AttributeType.STRING);
            addAttributeWithInternalId(type, "email", AttributeType.STRING);
            {
                Permission newPermission = type.newPermission();
                newPermission.setAccessLevel(Permission.READ_TYPE);
                type.addPermission(newPermission);
            }
            type.setResolver(this);
            type.setReadOnly();
            cache.put(type);
        }
        {
            DynamicTypeImpl type = new DynamicTypeImpl();
            String key = PERIOD_TYPE;
            type.setKey(key);
            type.getName().setName("en","period");
            type.setId(key);
            type.setAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE, DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RAPLATYPE);
            addAttributeWithInternalId(type, "name", AttributeType.STRING);
            addAttributeWithInternalId(type, "start", AttributeType.DATE);
            addAttributeWithInternalId(type, "end", AttributeType.DATE);
            final Attribute categoryAtt = addAttributeWithInternalId(type, "category", AttributeType.CATEGORY);
            categoryAtt.setConstraint(ConstraintIds.KEY_MULTI_SELECT, Boolean.TRUE);
            //categoryAtt.setConstraint(ConstraintIds.KEY_ROOT_CATEGORY, cache.getSuperCategory());
            type.setAnnotation(DynamicTypeAnnotations.KEY_NAME_FORMAT, "{name}");
            type.setResolver(this);
            {
                Permission newPermission = type.newPermission();
                newPermission.setAccessLevel(Permission.READ);
                type.addPermission(newPermission);
            }
            {
                Permission newPermission = type.newPermission();
                newPermission.setAccessLevel(Permission.READ_TYPE);
                type.addPermission(newPermission);
            }
            type.setReadOnly();
            cache.put(type);
        }
        {
            DynamicTypeImpl type = new DynamicTypeImpl();
            String key = RAPLA_TEMPLATE;
            type.setKey(key);
            type.setId(key);
            type.getName().setName("en","template");
            type.setAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE, DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RAPLATYPE);
            addAttributeWithInternalId(type, "name", AttributeType.STRING);
            {
                Attribute att = addAttributeWithInternalId(type, ResourceAnnotations.FIXEDTIMEANDDURATION, AttributeType.BOOLEAN);
                att.setDefaultValue(Boolean.FALSE);
            }
            type.setAnnotation(DynamicTypeAnnotations.KEY_NAME_FORMAT, "{name}");
            type.setResolver(this);
            {
                Permission newPermission = type.newPermission();
                newPermission.setAccessLevel(Permission.CREATE);
                type.addPermission(newPermission);
            }
            {
                Permission newPermission = type.newPermission();
                newPermission.setAccessLevel(Permission.READ_TYPE);
                type.addPermission(newPermission);
            }
            type.setReadOnly();
            cache.put(type);
        }
    }

    @Override
    public String getUsername(ReferenceInfo<User> userId)
    {
        User user = tryResolve(userId);
        if (user == null)
        {
            return "unknown";
        }
        Locale locale = raplaLocale.getLocale();
        String name = user.getName(locale);
        return name;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Date getConnectStart()
    {
        return connectStart;
    }

    protected void setConnectStart(Date connectStart)
    {
        this.connectStart = connectStart;
    }

    /*
    public User connect() throws RaplaException
    {
        try {
            User user = connectAsync().get();
            return user;
        } catch (RaplaException e) {
            throw e;
        } catch (Exception e) {
            throw new RaplaException(e.getMessage(),e);
        }
    }

    @Override public FutureResult<User> connectAsync()
    {

        return new FutureResult<User>()
        {

            @Override public User get() throws Exception
            {
                return connect(null);
            }

            @Override public void get(final AsyncCallback<User> callback)
            {
                scheduler.schedule(new Command()
                {

                    @Override public void execute()
                    {
                        User connect;
                        try
                        {
                            connect = connect(null);
                        }
                        catch (RaplaException e)
                        {
                            callback.onFailure(e);
                            return;
                        }
                        callback.onSuccess(connect);
                    }

                }, 0);
            }
        };
    }*/

    public void runWithReadLock(CachableStorageOperatorCommand cmd) throws RaplaException
    {
        RaplaLock.ReadLock readLock = lockManager.readLock(getClass(),"runWithReadLock " + cmd.getClass());
        try
        {
            Collection<ExternalSyncEntity> externalSyncEntityList = getAllExternalSyncEntities();
            cmd.execute(cache, externalSyncEntityList);
        }
        finally
        {
            lockManager.unlock(readLock);
        }
    }

    protected abstract Collection<ExternalSyncEntity> getAllExternalSyncEntities() throws RaplaException;

    /**
     * @param user the owner of the reservation or null for reservations from all users
     */
    @Override
    public Promise<AppointmentMapping> queryAppointments(final User user, final Collection<Allocatable> allocatables, final Collection<User> owners, final Date start,
                                                         final Date end, final ClassificationFilter[] filters, final Map<String, String> annotationQuery)
    {

        final Promise<AppointmentMapping> promise = scheduler.supply(() ->
        {
            boolean excludeExceptions = false;
            boolean isResourceTemplate = containsResourceTemplate(allocatables);
            final Collection<Entity> entities;
            final Set<Allocatable> nonTemplates;

            if ( isResourceTemplate)
            {
                entities = allocatables.stream().filter(this::isTemplate).collect(Collectors.toList());
                nonTemplates = allocatables.stream().filter( (alloc)->!isTemplate(alloc)).collect(Collectors.toSet());
            }
            else
            {
                entities = (allocatables != null)  ? new LinkedHashSet<>(allocatables  ) : new LinkedHashSet<>();
                if ( owners != null && owners.size() > 0) {
                    entities.addAll(owners);
                }
                nonTemplates = Collections.emptySet();
            }
            Map<Entity, Collection<Appointment>> allocatableMap = new LinkedHashMap<>();
            for (Entity entity: entities)
            {
                SortedSet<Appointment> appointmentSet;
                final SortedSet<Appointment> appointments;
                if (entity.getTypeClass()==User.class) {
                    ReferenceInfo<User> reference = ((User) entity).getReference();
                    appointments = getAppointmentsForUser(reference);
                } else {
                    appointments = getAppointments((Allocatable) entity);
                }
                appointmentSet = AppointmentImpl.getAppointments(appointments, user, start, end, excludeExceptions);
                for (Appointment appointment : appointmentSet)
                {
                    Reservation reservation = appointment.getReservation();
                    if (!match(reservation, annotationQuery))
                    {
                        continue;
                    }
                    final Stream<Allocatable> allocatablesFor = reservation.getAllocatablesFor(appointment);
                    if ( !nonTemplates.isEmpty())
                    {
                        if (!allocatablesFor.anyMatch(nonTemplates::contains))
                        {
                            continue;
                        }
                    }
                    // Ignore Templates if not explicitly requested

                    final boolean isTemplate = RaplaComponent.isTemplate(reservation);
                    if ((isTemplate != isResourceTemplate) )
                    {
                        // FIXME this special case should be refactored, so one can get all reservations in one method
                        continue;
                    }
                    if (filters != null && !ClassificationFilter.Util.matches(filters, reservation))
                    {
                        continue;
                    }
                    Collection<Appointment> appointmentCollection = allocatableMap.get(entity);
                    if (appointmentCollection == null)
                    {
                        appointmentCollection = new LinkedHashSet<>();
                        allocatableMap.put(entity, appointmentCollection);
                    }
                    appointmentCollection.add(appointment);
                }
            }

            AppointmentMapping result = new AppointmentMapping(allocatableMap);
            return result;
        });
        return promise;
    }


    private boolean containsResourceTemplate(Collection<Allocatable> allocs) {
        if ( allocs == null)
        {
            return false;
        }
        for ( Allocatable alloc:allocs) {
            if (isTemplate(alloc)) {
                return true;
            }
        }
        return false;
    }

    private boolean isTemplate(Allocatable allocatable)
    {
        Classification classification = allocatable.getClassification();
        return classification.getType().getKey().equals(RAPLA_TEMPLATE);
    }

    public boolean match(Reservation reservation, Map<String, String> annotationQuery)
    {
        if (annotationQuery != null)
        {
            for (String key : annotationQuery.keySet())
            {
                String annotationParam = annotationQuery.get(key);
                String annotation = reservation.getAnnotation(key);
                if (annotation == null || annotationParam == null)
                {
                    if (annotationParam != null)
                    {
                        return false;
                    }
                }
                else
                {
                    if (!annotation.equals(annotationParam))
                    {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    @Override
    public String createId(Class<? extends Entity> raplaType) throws RaplaException
    {
        String string = UUID.randomUUID().toString();
        String result = replaceFirst(raplaType, string);
        return result;
    }

    private String replaceFirst(Class<? extends Entity> raplaType, String string)
    {
        final String localName = RaplaType.getLocalName(raplaType);
        Character firstLetter = raplaType == Reservation.class ? 'e' : localName.charAt(0);
        String result = firstLetter + string.substring(1);
        return result;
    }

    @Override
    public String createId(Class<? extends Entity> raplaType, String seed) throws RaplaException
    {

        byte[] data = new byte[16];
        MessageDigest md;
        try
        {
            md = MessageDigest.getInstance("MD5");
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new RaplaException(e.getMessage(), e);
        }
        data = md.digest(seed.getBytes());
        if (data.length != 16)
        {
            throw new RaplaException("Wrong algorithm");
        }
        data[6] &= 0x0f;  /* clear version        */
        data[6] |= 0x40;  /* set to version 4     */
        data[8] &= 0x3f;  /* clear variant        */
        data[8] |= 0x80;  /* set to IETF variant  */

        long msb = 0;
        long lsb = 0;
        for (int i = 0; i < 8; i++)
            msb = (msb << 8) | (data[i] & 0xff);
        for (int i = 8; i < 16; i++)
            lsb = (lsb << 8) | (data[i] & 0xff);
        long mostSigBits = msb;
        long leastSigBits = lsb;

        UUID uuid = new UUID(mostSigBits, leastSigBits);
        String result = replaceFirst(raplaType, uuid.toString());
        return result;
    }


    public Date today()
    {
        long time = getCurrentTimestamp().getTime();
        long offset = TimeZoneConverterImpl.getOffset(IOUtil.getTimeZone(), systemTimeZone, time);
        Date raplaTime = new Date(time + offset);
        return DateTools.cutDate(raplaTime);
    }

    public Date getCurrentTimestamp()
    {
        long time = System.currentTimeMillis();
        return new Date(time);
    }

    public void setTimeZone(TimeZone timeZone)
    {
        systemTimeZone = timeZone;
    }

    public TimeZone getTimeZone()
    {
        return systemTimeZone;
    }

    public String authenticate(String username, String password) throws RaplaException
    {
        checkConnected();
        getLogger().debug("Check password for User " + username);
        User user = cache.getUser(username);
        if (user != null)
        {
            String userId = user.getId();
            if (checkPassword(user.getReference(), password))
            {
                return userId;
            }
        }
        getLogger().warn("Login failed for " + username);
        throw new RaplaSecurityException(i18n.getString("error.login"));
    }

    public boolean canChangePassword() throws RaplaException
    {
        return true;
    }

    public void changePassword(User user, char[] oldPassword, char[] newPassword) throws RaplaException
    {
        getLogger().info("Change password for User " + user.getUsername());
        String password = new String(newPassword);
        if (encryption != null)
            password = encrypt(encryption, password);
        User editObject = editObject(user, null);

        changePassword( editObject, password);
    }

    abstract protected void changePassword(User user, String password) throws RaplaException;

    public void changeName(User user, String title, String firstname, String surname) throws RaplaException
    {
        User editableUser = editObject(user, user);
        Allocatable personReference = editableUser.getPerson();
        if (personReference == null)
        {
            editableUser.setName(surname);
            storeUser(editableUser);
        }
        else
        {
            Allocatable editablePerson = editObject(personReference, null);
            Classification classification = editablePerson.getClassification();
            {
                Attribute attribute = classification.getAttribute("title");
                if (attribute != null)
                {
                    classification.setValueForAttribute(attribute, title);
                }
            }
            {
                Attribute attribute = classification.getAttribute("firstname");
                if (attribute != null)
                {
                    classification.setValueForAttribute(attribute, firstname);
                }
            }
            {
                Attribute attribute = classification.getAttribute("surname");
                if (attribute != null)
                {
                    classification.setValueForAttribute(attribute, surname);
                }
            }
            ArrayList<Entity> arrayList = new ArrayList<>();
            arrayList.add(editableUser);
            arrayList.add(editablePerson);
            Collection<ReferenceInfo<Entity>> removeObjects = Collections.emptySet();
            // synchronization will be done in the dispatch method
            storeAndRemove(arrayList, removeObjects, null);
        }
    }

    public void changeEmail(User user, String newEmail) throws RaplaException
    {
        User editableUser = user.isReadOnly() ? editObject(user, user) : user;
        Allocatable personReference = editableUser.getPerson();
        ArrayList<Entity> storeObjects = new ArrayList<>();
        Collection<ReferenceInfo<Entity>> removeObjects = Collections.emptySet();
        storeObjects.add(editableUser);
        if (personReference == null)
        {
            editableUser.setEmail(newEmail);
        }
        else
        {
            Allocatable editablePerson = editObject(personReference, null);
            Classification classification = editablePerson.getClassification();
            classification.setValue("email", newEmail);
            storeObjects.add(editablePerson);
        }
        storeAndRemove(storeObjects, removeObjects, null);
    }

    protected void resolveInitial(Collection<? extends Entity> entities, EntityResolver resolver) throws RaplaException
    {
        testResolve(entities);

        for (Entity entity : entities)
        {
            if (entity instanceof EntityReferencer)
            {
                ((EntityReferencer) entity).setResolver(resolver);
            }
            if (entity instanceof DynamicType)
            {
                ((DynamicTypeImpl) entity).setOperator(this);
            }
        }
        processUserPersonLink(entities);
    }

    protected Collection<Entity> migrateTemplates() throws RaplaException
    {
        Collection<Allocatable> allocatables = cache.getAllocatables();
        for (Allocatable r : allocatables)
        {
            String key = r.getClassification().getType().getKey();
            if (key.equals(StorageOperator.RAPLA_TEMPLATE))
            {
                return Collections.emptyList();
            }
        }
        Map<String, Collection<Reservation>> templateMap = new HashMap<>();
        for (Reservation r : cache.getReservations())
        {
            String annotation = r.getAnnotation(RaplaObjectAnnotations.KEY_TEMPLATE, null);
            if (annotation == null)
            {
                continue;
            }

            Collection<Reservation> collection = templateMap.get(annotation);
            if (collection == null)
            {
                collection = new ArrayList<>();
                templateMap.put(annotation, collection);
            }
            collection.add(r);
        }
        if (templateMap.size() == 0)
        {
            return Collections.emptyList();
        }
        getLogger().warn("Found old templates. Migrating.");

        Collection<Entity> toStore = new HashSet<>();
        for (String templateKey : templateMap.keySet())
        {
            Collection<Reservation> templateEvents = templateMap.get(templateKey);
            Date date = getCurrentTimestamp();
            AllocatableImpl template = new AllocatableImpl(date, date);
            template.setResolver(this);
            String templateId = createId(Allocatable.class);
            Classification newClassification = getDynamicType(RAPLA_TEMPLATE).newClassification();
            newClassification.setValue("name", templateKey);
            template.setClassification(newClassification);
            template.setId(templateId);
            Permission permission = template.newPermission();
            permission.setAccessLevel(AccessLevel.READ);
            template.addPermission(permission);
            User owner = null;
            for (Reservation r : templateEvents)
            {
                r.setAnnotation(RaplaObjectAnnotations.KEY_TEMPLATE, templateId);
                toStore.add(r);
                final ReferenceInfo<User> ownerId = r.getOwnerRef();
                if (owner == null && ownerId != null)
                {
                    owner = tryResolve(ownerId);
                }
            }
            getLogger().info("Migrating " + templateKey);
            template.setOwner(owner);
            toStore.add(template);
        }
        return toStore;
    }

    protected void processUserPersonLink(Collection<? extends Entity> entities) throws RaplaException
    {
        // resolve emails
        Map<String, Allocatable> resolvingMap = new HashMap<>();
        for (Entity entity : entities)
        {
            if (entity instanceof Allocatable)
            {
                Allocatable allocatable = (Allocatable) entity;
                final Classification classification = allocatable.getClassification();
                final Attribute attribute = classification.getAttribute("email");
                if (attribute != null)
                {
                    final String email = (String) classification.getValueForAttribute(attribute);
                    if (email != null)
                    {
                        resolvingMap.put(email, allocatable);
                    }
                }
            }
        }
        for (Entity entity : entities)
        {
            if (entity.getTypeClass() == User.class)
            {
                User user = (User) entity;
                String email = user.getEmail();
                if (email != null && email.trim().length() > 0)
                {
                    Allocatable person = resolvingMap.get(email);
                    if (person != null)
                    {
                        user.setPerson(person);
                    }
                }
            }
        }
    }

    public void confirmEmail(User user, String newEmail) throws RaplaException
    {
        throw new RaplaException("Email confirmation must be done in the remotestorage class");
    }

    /**
     * Determines all conflicts the user can modify. if no user is passed all conflicts are returned
     */
    public Promise<Collection<Conflict>> getConflicts(User user)
    {
        return scheduler.supply(()-> {
            checkConnected();
            Collection<Conflict> conflictList = new HashSet<>();
            final Collection<Conflict> conflicts = conflictFinder.getConflicts(user);
            for (Conflict conflict : conflicts) {
                // conflict is filled with disable/enable status from cache
                Conflict conflictClone = cache.fillConflictDisableInformation(user, conflict);
                conflictList.add(conflictClone);
            }
            return conflictList;
        });
    }

    boolean disposing;

    public void dispose()
    {
        // prevent reentrance in dispose
        synchronized (this)
        {
            if (disposing)
            {
                getLogger().warn("Disposing is called twice", new RaplaException(""));
                return;
            }
            disposing = true;
        }
        try
        {
            forceDisconnect();
        }
        finally
        {
            disposing = false;
        }
    }

    final protected void scheduleConnectedTasks(final Action command, long delay, long period)
    {
        //        if (true)
        //            return;
        Action task = ()->
        {
            final RaplaLock.ReadLock lock = disconnectLock.readLock(getClass(), "scheduleConnectedTasks",3);
            try
            {
                if (isConnected())
                {
                    command.run();
                }
            }
            finally
            {
                disconnectLock.unlock(lock);
            }
        };
        io.reactivex.rxjava3.disposables.Disposable schedule = scheduler.schedule(task, delay,period);
        scheduledTasks.add(schedule);
    }

    protected void forceDisconnect()
    {
        try
        {
            disconnect();
        }
        catch (Exception ex)
        {
            getLogger().error("Error during disconnect ", ex);
        }
    }

    /** performs Integrity constraints check */
    protected void check(final UpdateEvent evt, final EntityStore store) throws RaplaException
    {
        Set<Entity> storeObjects = new HashSet<>(evt.getStoreObjects());
        //Set<Entity> removeObjects = new HashSet<Entity>(evt.getRemoveObjects());
        setResolverAndCheckReferences(evt, store);
        checkConsistency(evt, store);
        checkUnique(evt, store);
        checkNoDependencies(evt, store);
        checkVersions(storeObjects);
    }

    protected void initIndizes() throws RaplaException
    {
        deleteUpdateSet = new DualTreeBidiMap<>();
        externalIds = new DualHashBidiMap<>();
        // The appointment map

        final Collection<Allocatable> alloctables = cache.getAllocatables();
        for (Allocatable alloc : alloctables)
        {
            final String externalId = alloc.getAnnotation(RaplaObjectAnnotations.KEY_EXTERNALID);

            if (externalId != null)
            {
                externalIds.put(externalId, alloc.getReference());
            }
            else
            {
                final Classification classification = alloc.getClassification();
                Attribute idAtt = classification.getAttribute("DualisId");
                if (idAtt != null)
                {
                    final Object value = classification.getValueForAttribute(idAtt);
                    if (value != null)
                    {
                        externalIds.put(value.toString(), alloc.getReference());
                    }
                }
            }
        }

        final Collection<Reservation> events = cache.getReservations();
        for (Reservation event : events)
        {
            final String externalId = event.getAnnotation(RaplaObjectAnnotations.KEY_EXTERNALID);
            if (externalId != null)
            {
                externalIds.put(externalId, event.getReference());
            }
        }
        appointmentBindings.initAppointmentBindings(events);
        Date today2 = today();
        AllocationMap allocationMap = new AllocationMap()
        {
            public SortedSet<Appointment> getAppointments(Allocatable allocatable)
            {
                return LocalAbstractCachableOperator.this.getAppointments(allocatable);
            }

            @SuppressWarnings("unchecked")
            public Collection<Allocatable> getAllocatables()
            {
                return cache.getAllocatables();
            }
        };
        // The conflict map
        Logger logger = getLogger();
        conflictFinder = new ConflictFinder(allocationMap, today2, logger, this, permissionController);

        // if a client request changes before the start date return refresh conflict flag
        Action cleanUpConflicts = ()->
            {
                removeOldConflicts();
                removeOldHistory();
            };
        removeOldConflicts();
        //removeOldHistory();
        for (ReferenceInfo id : history.getAllIds())
        {
            EntityHistory.HistoryEntry entry = history.getLatest(id);
            addToDeleteUpdate(entry);
        }
        final Collection<Conflict> conflicts = conflictFinder.getConflicts(null);
        for (Conflict conflict : conflicts)
        {
            ReferenceInfo referenceInfo = conflict.getReference();
            boolean isDelete = false;
            Date timestamp = conflict.getLastChanged();
            addToDeleteUpdate(referenceInfo, timestamp, isDelete, conflict);
        }

        final Collection<User> users = cache.getUsers();
        final Set<User> systemUser = Collections.singleton(null);
        for (User user : new IterableChain<>(users, systemUser))
        {
            String userId = user != null ? user.getId() : null;
            Preferences preference = cache.getPreferencesForUserId(userId);
            if (preference == null)
            {
                continue;
            }
            ReferenceInfo referenceInfo = preference.getReference();
            boolean isDelete = false;
            Date timestamp = preference.getLastChanged();
            addToDeleteUpdate(referenceInfo, timestamp, isDelete, preference);
        }
        final long delayCleanup = DateTools.MILLISECONDS_PER_HOUR;
        scheduleConnectedTasks(cleanUpConflicts, delayCleanup, DateTools.MILLISECONDS_PER_HOUR);
        final int refreshPeriod = 1000 * 60;
        final long delayRefresh = 1000;
        scheduleConnectedTasks(()->
            {
                try
                {
                    final RaplaLock.ReadLock readLock = lockManager.readLock(getClass(),"schedule readData");
                    Object refreshData;
                    try
                    {
                        refreshData = getRefreshData();
                    }
                    finally
                    {
                         lockManager.unlock( readLock);
                    }
                    if (refreshData == null)
                    {
                        return;
                    }
                    final RaplaLock.WriteLock writeLock = lockManager.writeLockIfAvaliable(getClass(),"schedule Refresh");
                    if (writeLock != null)
                    {
                        try
                        {
                            refreshWithoutLock(refreshData);
                        }
                        finally
                        {
                            lockManager.unlock(writeLock);
                        }
                    }
                }
                catch (Throwable t)
                {
                    getLogger().info("Could not refresh data");
                }
        }, delayRefresh, refreshPeriod);

    }

    @Override
    public void refresh() throws RaplaException
    {
        Object refreshData = getRefreshData();
        final RaplaLock.WriteLock lock = writeLockIfLoaded("refreshing");
        try
        {
            refreshWithoutLock(refreshData);
        }
        finally
        {
            lockManager.unlock(lock);
        }
    }

    @Override
    public Promise<Void> refreshAsync() {
        return scheduler.supply(()->{refresh(); return null;});
    }

    abstract protected Object getRefreshData();
    abstract protected void refreshWithoutLock(Object refreshData) throws RaplaException;

    @Override
    synchronized public void disconnect() throws RaplaException
    {
        if (!isConnected())
            return;
        RaplaLock.WriteLock writeLock = null;
        try
        {
            writeLock = writeLockIfLoaded("disconnect");
        }
        catch (Exception ex)
        {
            getLogger().error("Could not get writeLock. Scheduled task is probably running > 10sec. Forcing disconnect." + ex.getMessage(), ex);
        }
        RaplaLock.WriteLock disconnectWrite;
        try
        {
            disconnectWrite = this.disconnectLock.writeLock(getClass(),"disconnect");
        }
        catch (Exception ex)
        {
            getLogger().error("Could not get disconnectWirte. Scheduled task is probably running > 10sec. Forcing disconnect." + ex.getMessage(), ex);
            disconnectWrite = null;
        }
        try
        {
            changeStatus(LocalAbstractCachableOperator.InitStatus.Disconnected);
            cache.clearAll();
            history.clear();
        }
        finally
        {
            lockManager.unlock(writeLock);
        }

        try
        {
            for ( io.reactivex.rxjava3.disposables.Disposable task : scheduledTasks)
            {
                task.dispose();
            }
        }
        finally
        {
            this.disconnectLock.unlock(disconnectWrite);
        }
    }

    /*
    public void setConflictEnabledState( String conflictId,Date date, boolean appointment1Enabled,boolean appointment2Enabled, Date lastChanged)
    {
        ConflictImpl conflict = (ConflictImpl)conflictFinder.findConflict(conflictId, date);
        conflict.setAppointment1Enabled( appointment1Enabled);
        conflict.setAppointment2Enabled( appointment2Enabled);
        conflict.setLastChanged( lastChanged);
    }


    private void checkAndAddConflict(Entity entity)
    {
        if (!(entity instanceof Conflict))
        {
            return;
        }
        Conflict conflict = (Conflict) entity;
        String conflictId = conflict.getId();
        Date date = getCurrentTimestamp();
        boolean appointment1Enabled = conflict.isAppointment1Enabled();
        boolean appointment2Enabled = conflict.isAppointment2Enabled();
        setConflictEnabledState(conflictId, date, appointment1Enabled, appointment2Enabled,conflict.getLastChanged());
    }
    */

    static public class UpdateBindingsResult
    {
        Set<ReferenceInfo<Allocatable>> toUpdate = new HashSet<>();
        List<ReferenceInfo<Allocatable>> removedAllocatables = new ArrayList<>();
        boolean isEmpty()
        {
            return toUpdate.isEmpty() && removedAllocatables.isEmpty();
        }
    }

    /** updates the bindings of the resources and returns a map with all processed allocation changes*/
    private Collection<ConflictFinder.ConflictChangeOperation> updateIndizes(UpdateResult result) throws RaplaException
    {
        final Collection<UpdateOperation> conflictChanges = new ArrayList<>();
        for (UpdateOperation op : result.getOperations())
        {
            ReferenceInfo id = op.getReference();
            final Class<? extends Entity> raplaType = op.getType();
            if (raplaType == Conflict.class || raplaType == Allocatable.class || raplaType == Reservation.class || raplaType == DynamicType.class
                    || raplaType == User.class || raplaType == Category.class)
            {
                //history.getBefore(id, now);
                //Date timestamp = ((LastChangedTimestamp) newEntity).getLastChanged();
                //boolean isDelete = false;
                //final EntityHistory.HistoryEntry historyEntry = history.addHistoryEntry(newEntity, timestamp, isDelete);
                final EntityHistory.HistoryEntry historyEntry = history.getLatest(id);
                addToDeleteUpdate(historyEntry);
                updateExternalId(op, id);
            }
            else if (raplaType == Preferences.class)
            {
                ReferenceInfo referenceInfo = op.getReference();
                boolean isDelete = op instanceof Remove;
                Entity current = tryResolve(referenceInfo.getId(), Preferences.class);
                if (current != null)
                {
                    Date timestamp = ((Preferences) current).getLastChanged();
                    addToDeleteUpdate(referenceInfo, timestamp, isDelete, current);
                }
            }
            if (raplaType == Conflict.class)
            {
                conflictChanges.add(op);
            }
        }

        // 1. update appoimtment binding map
        UpdateBindingsResult bindingResult = updateAppointmentBindings(result);

        // check if conflict is disabled or enabled
        // add, change and remove are only for conflict disabling/enabling
        // conflicts are enabled by default.
        // a conflict can be disabled for the two participating appointments
        // if a conflict is not disabled then it is not stored in the database
        // you get an database add if you disable an enabled conflic
        // you get an database change if you change disabled flags without enabling the conflict again (e.g. add a second disable)
        // you get an database remove if conflict is enabled for both appointments
        /*
        for (Add add : result.getOperations(Add.class))
        {
            String id = add.getCurrentId();
            final Entity lastKnown = result.getLastKnown(id);//.getUnresolvedEntity();
            checkAndAddConflict(lastKnown);
        }
        for (Change changes : result.getOperations(Change.class))
        {
            String id = changes.getCurrentId();
            final Entity lastKnown = result.getLastKnown(id);//.getUnresolvedEntity();
            checkAndAddConflict(lastKnown);
        }

        for (Remove removed : result.getOperations(UpdateResult.Remove.class))
        {
            String id = removed.getCurrentId();
            final Entity lastKnown = result.getLastEntryBeforeUpdate(id);
            checkAndAddConflict(lastKnown);
        }
        */
        Date today = today();
        // processes the conflicts and adds the changes to the result
        final Collection<ConflictFinder.ConflictChangeOperation> calculatedConflictChanges = conflictFinder.updateConflicts(bindingResult, result, today);
        for (ConflictFinder.ConflictChangeOperation updateOperation : calculatedConflictChanges)
        {
            final UpdateOperation operation = updateOperation.getOperation();
            ReferenceInfo referenceInfo = operation.getReference();
            final Conflict conflict;
            boolean isDelete;
            Date timestamp;
            if (operation instanceof Remove)
            {
                conflict = null;
                isDelete = true;
                timestamp = getCurrentTimestamp();
            }
            else
            {
                conflict = conflictFinder.findConflict(referenceInfo);
                timestamp = conflict != null ? conflict.getLastChanged() : getCurrentTimestamp();
                isDelete = conflict == null;
            }
            addToDeleteUpdate(referenceInfo, timestamp, isDelete, conflict);
            conflictChanges.add(operation);
        }

        // Order is important. Can't remove from database if removed from cache first
        final Set<ReferenceInfo<Conflict>> conflictsToDelete = getConflictsToDelete(conflictChanges);
        removeConflictsFromDatabase(conflictsToDelete);
        removeConflictsFromCache(conflictsToDelete);
        return calculatedConflictChanges;

        //      Collection<Change> changes = result.getOperations( UpdateResult.Change.class);
        //      for ( Change change:changes)
        //      {
        //          Entity old = change.getOld();
        //          if (!( old instanceof Conflict))
        //          {
        //              continue;
        //          }
        //          Conflict conflict = (Conflict)change.getNew();
        //          if (conflict.isEnabledAppointment1() && conflict.isEnabledAppointment2())
        //          {
        //              conflicts.add( conflict);
        //          }
        //      }

    }

    private void updateExternalId(UpdateOperation op, ReferenceInfo id)
    {
        final BidiMap<ReferenceInfo, String> referenceInfoStringBidiMap = externalIds.inverseBidiMap();
        final String oldExternalId = referenceInfoStringBidiMap.get(id);
        if (op instanceof Remove)
        {
            externalIds.remove(oldExternalId);
        }
        else
        {
            final Entity entity = tryResolve(id);
            if (entity instanceof Annotatable)
            {
                final String newExternalId = ((Annotatable) entity).getAnnotation(RaplaObjectAnnotations.KEY_EXTERNALID);
                if (oldExternalId != null && (newExternalId == null || !newExternalId.equals(oldExternalId)))
                {
                    externalIds.remove(oldExternalId);
                }
                if (newExternalId != null && (oldExternalId == null || !oldExternalId.equals(newExternalId)))
                {
                    externalIds.put(newExternalId, id);
                }
            }
        }
    }

    // updates appointmentBinding
    private UpdateBindingsResult updateAppointmentBindings(UpdateResult result)
    {
        UpdateBindingsResult bindingResult = new UpdateBindingsResult();
        Set<ReferenceInfo<Allocatable>> toUpdate = bindingResult.toUpdate;
        List<ReferenceInfo<Allocatable>> removedAllocatables = bindingResult.removedAllocatables;
        for (Add add : result.getOperations(Add.class))
        {
            ReferenceInfo id = add.getReference();
            if (id.getType() == Reservation.class)
            {
                Reservation newReservation = result.getLastKnown((ReferenceInfo<Reservation>) id);//.getUnresolvedEntity();
                appointmentBindings.updateReservation( newReservation, toUpdate, false);
            }
        }
        for (Change changes : result.getOperations(Change.class))
        {

            ReferenceInfo id = changes.getReference();
            final Entity lastKnown = result.getLastKnown(id);//.getUnresolvedEntity();
            if (lastKnown instanceof Reservation)
            {
                Reservation newReservation = (Reservation) lastKnown;
                appointmentBindings.updateReservation( newReservation, toUpdate, false);
            }
            if (lastKnown instanceof DynamicType)
            {
                DynamicType dynamicType = (DynamicType) lastKnown;
                DynamicType old = (DynamicType) result.getLastEntryBeforeUpdate(id);
                String conflictsNew = dynamicType.getAnnotation(DynamicTypeAnnotations.KEY_CONFLICTS);
                String conflictsOld = old != null ? old.getAnnotation(DynamicTypeAnnotations.KEY_CONFLICTS) : null;
                if (conflictsNew != conflictsOld)
                {
                    if (conflictsNew == null || !conflictsNew.equals(conflictsOld))
                    {
                        Collection<Reservation> reservations = cache.getReservations();
                        for (Reservation reservation : reservations)
                        {
                            if (dynamicType.equals(reservation.getClassification().getType()))
                            {
                                for (Allocatable alloc:reservation.getAllocatables()) {
                                    toUpdate.add(alloc.getReference());
                                }
                            }
                        }
                    }
                }
            }
        }
        {
            for (Remove removed : result.getOperations(Remove.class))
            {
                final ReferenceInfo reference = removed.getReference();
                final Class<? extends Entity> type = reference.getType();
                if (type == Reservation.class)
                {
                    final Entity lastKnown = result.getLastEntryBeforeUpdate(reference);
                    appointmentBindings.updateReservation( (Reservation) lastKnown, toUpdate, true);
                }
                else if (type == Allocatable.class)
                {
                    removedAllocatables.add(reference);
                }
            }
        }
        if ( !bindingResult.isEmpty())
        {
            if (!appointmentBindings.checkAbandonedAppointments(cache)) {
                final Collection<Reservation> events = cache.getReservations();
                appointmentBindings.initAppointmentBindings(events);
            }
        }
        return bindingResult;
    }

    protected void addToDeleteUpdate(EntityHistory.HistoryEntry historyEntry)
    {
        Entity current = history.getEntity(historyEntry);
        final boolean isDelete = historyEntry.isDelete();
        final Date timestamp = new Date(historyEntry.getTimestamp());
        ReferenceInfo ref = historyEntry.getId();
        addToDeleteUpdate(ref, timestamp, isDelete, current);
    }

    private void addToDeleteUpdate(ReferenceInfo referenceInfo, Date timestamp, boolean isDelete, Entity current)
    {
        synchronized ( deleteUpdateSet ) {
            final Class<? extends Entity> type = referenceInfo.getType();
            String id = referenceInfo.getId();

            DeleteUpdateEntry entry = deleteUpdateSet.get(id);
            if (entry == null) {
                entry = new DeleteUpdateEntry(referenceInfo, timestamp, isDelete);
            } else {
                DeleteUpdateEntry remove = deleteUpdateSet.remove(id);
                entry.isDelete = isDelete;
                entry.timestamp = timestamp;
                if (isDelete && remove == null) {
                    getLogger().warn("Can't remove entry for id " + id);
                }
            }
            if (type == User.class && current != null) {
                final Collection<String> groupIdList = ((UserImpl) current).getGroupIdList();
                entry.addGroupIds(groupIdList);
            } else if (current instanceof EntityPermissionContainer) {
                entry.addPermissions((EntityPermissionContainer) current, Permission.READ_NO_ALLOCATION);
            } else if (type == Category.class) {
                entry.affectAll = true;
            } else if (type == Conflict.class) {
                if (entry.isDelete) {
                    entry.affectAll = true;
                } else {
                    Conflict conflict = (Conflict) current;
                    addPermissions(deleteUpdateSet, entry, conflict.getReservation1());
                    addPermissions(deleteUpdateSet, entry, conflict.getReservation2());
                }
            } else if (current instanceof Preferences) {
                ReferenceInfo<User> owner = ((PreferencesImpl) current).getOwnerRef();
                if (owner != null) {
                    entry.addUserIds(Collections.singletonList(owner.getId()));
                }
            }
            deleteUpdateSet.put(entry.getId(), entry);
        }
    }

    private void addPermissions(SortedBidiMap<String, DeleteUpdateEntry> deleteUpdateSet, DeleteUpdateEntry entry, ReferenceInfo<Reservation> reservation)
    {
        Reservation event = tryResolve(reservation);
        if (event != null)
        {
            entry.addPermissions(event, Permission.EDIT);
        }
        else if (reservation != null)
        {
            final String id = reservation.getId();
            DeleteUpdateEntry deleteUpdateEntry = deleteUpdateSet.get(id);
            if (deleteUpdateEntry != null)
            {
                entry.addPermssions(deleteUpdateEntry);
            }
        }
    }

    /*
    @Override public Collection<ReferenceInfo> getDeletedEntities(User user, final Date timestamp) throws RaplaException
    {
        boolean isDelete = true;
        Collection<ReferenceInfo> result = getEntities(user, timestamp, isDelete);
        return result;
    }
    */

    private boolean isAffected(DeleteUpdateEntry entry, String userId, final Collection<String> groupsIncludingParents)
    {
        if (entry.affectAll)
        {
            return true;
        }
        else
        {
            if (entry.affectedGroupIds != null)
            {
                if (!Collections.disjoint(entry.affectedGroupIds, groupsIncludingParents))
                {
                    return true;
                }
            }
            if (entry.affectedUserIds != null)
            {
                return entry.affectedUserIds.contains(userId);
            }
        }
        return false;
    }

    class DeleteUpdateEntry implements Comparable<DeleteUpdateEntry>
    {
        public boolean affectAll;
        Date timestamp;
        final ReferenceInfo reference;
        Set<String> affectedGroupIds;
        Set<String> affectedUserIds;
        boolean isDelete;

        DeleteUpdateEntry(ReferenceInfo reference, Date timestamp, boolean isDelete)
        {
            this.isDelete = isDelete;
            this.timestamp = timestamp;
            this.reference = reference;
        }

        @Override
        public int compareTo(DeleteUpdateEntry o)
        {
            if (o == this)
            {
                return 0;
            }
            Date time1 = this.timestamp;
            Date time2 = o.timestamp;
            int result = time1.compareTo(time2);
            if (result != 0)
            {
                return result;
            }
            String deleteId = getId();
            result = deleteId.compareTo(o.getId());
            return result;
        }

        public void addPermssions(DeleteUpdateEntry deleteUpdateEntry)
        {
            if (deleteUpdateEntry.affectAll)
            {
                affectAll = true;
            }
            Set<String> groupIds = deleteUpdateEntry.affectedGroupIds;
            if (groupIds != null)
            {
                addGroupIds(groupIds);
            }
            Set<String> userIds = deleteUpdateEntry.affectedUserIds;
            if (userIds != null)
            {
                addUserIds(userIds);
            }
        }

        public void addUserIds(Collection<String> userIds)
        {
            if (affectedUserIds == null)
            {
                affectedUserIds = new HashSet<>(1);
            }
            affectedUserIds.addAll(userIds);
        }

        private void addGroupIds(Collection<String> groupIds)
        {
            if (affectedGroupIds == null)
            {
                affectedGroupIds = new HashSet<>(1);
            }
            affectedGroupIds.addAll(groupIds);
        }

        String getId()
        {
            return reference.getId();
        }

        @Override
        public boolean equals(Object o)
        {
            boolean equals = getId().equals(((DeleteUpdateEntry) o).getId());
            return equals;
        }

        @Override
        public int hashCode()
        {
            return getId().hashCode();
        }

        @Override
        public String toString()
        {
            return reference + (isDelete ? " removed on " : "changed on ") + timestamp;
        }

        private void addPermissions(EntityPermissionContainer current, Permission.AccessLevel minimumLevel)
        {
            DeleteUpdateEntry entry = this;
            if (current instanceof Ownable)
            {
                ReferenceInfo<User> ownerId = current.getOwnerRef();
                if (ownerId != null)
                {
                    if (entry.affectedUserIds == null)
                    {
                        entry.affectedUserIds = new HashSet<>(1);
                    }
                    entry.affectedUserIds.add(ownerId.getId());
                }
            }
            Collection<Permission> permissions = current.getPermissionList();
            for (Permission p : permissions)
            {
                String groupId = ((PermissionImpl) p).getGroupId();
                String userId = p.getUserId();
                Permission.AccessLevel accessLevel = p.getAccessLevel();
                if (minimumLevel.includes(accessLevel))
                {
                    continue;
                }
                if (groupId != null)
                {
                    addGroupIds(entry, groupId);
                }
                else if (userId != null)
                {
                    if (entry.affectedUserIds == null)
                    {
                        entry.affectedUserIds = new HashSet<>(1);
                    }
                    entry.affectedUserIds.add(userId);
                }
                else
                {
                    // we have an all usere group here
                    entry.affectAll = true;
                    break;
                }
            }
        }

        private void addGroupIds(DeleteUpdateEntry entry, String groupId)
        {
            final Entity entity = tryResolve(groupId, Category.class);
            if (entity == null)
            {
                return;
            }
            if (entry.affectedGroupIds == null)
            {
                entry.affectedGroupIds = new HashSet<>(1);
            }
            entry.affectedGroupIds.add(groupId);
            final Category parent = ((Category) entity).getParent();
            if (parent != null && !parent.equals(getSuperCategory()))
            {
                addGroupIds(entry, parent.getId());
            }
        }

    }

    /**
     * returns all entities with a timestamp > the passed timestamp
     */
    private Collection<ReferenceInfo> getEntities(User user, final Date timestamp, boolean isDelete)
    {
        Assert.notNull(timestamp);
        // we use an empty id here because the implmentation of the DeleteUpdateEntry compare compares idStrings if timestamps are equal
        // so tailMap returns all entities with a timestamp >= timestamp
        final String dummyId = "";
        // we need to add +1 so that we dont get entities with the passed (guaranteed timestamp)
        DeleteUpdateEntry fromElement = new DeleteUpdateEntry(new ReferenceInfo(dummyId, Allocatable.class), new Date(timestamp.getTime() + 1), isDelete);
        LinkedList<ReferenceInfo> result = new LinkedList<>();

        final Collection<String> groupsIncludingParents = user != null ? UserImpl.getGroupsIncludingParents(user) : null;
        String userId = user != null ? user.getId() : null;
        synchronized ( deleteUpdateSet )
        {
            SortedMap<DeleteUpdateEntry, String> tailMap = deleteUpdateSet.inverseBidiMap().tailMap(fromElement);
            Set<DeleteUpdateEntry> tailSet = tailMap.keySet();
            for (DeleteUpdateEntry entry : tailSet)
            {
                if (entry.isDelete != isDelete)
                {
                    continue;
                }
                if (user == null || user.isAdmin() || isAffected(entry, userId, groupsIncludingParents))
                {
                    ReferenceInfo deletedReference = entry.reference;
                    result.add(deletedReference);
                }
            }
        }

        return result;
    }

    static final SortedSet<Appointment> EMPTY_SORTED_SET = Collections.unmodifiableSortedSet(new TreeSet<Appointment>());

    /** returs all appointments for the allocatable and all groupMembers and belongsTo*/
    protected SortedSet<Appointment> getAppointments(Allocatable allocatable)
    {
        final ReferenceInfo<Allocatable> reference = allocatable != null ? allocatable.getReference() : null;
        Set<ReferenceInfo<Allocatable>> allocatableIds = cache.getDependentRef(reference);
        if (allocatableIds.size() == 0)
        {
            return EMPTY_SORTED_SET;
        }
        else
        {
            SortedSet<Appointment> transitive = new TreeSet<>(new AppointmentStartComparator());
            for (ReferenceInfo<Allocatable> allocatableId : allocatableIds)
            {
                SortedSet<Appointment> s = appointmentBindings.getAppointments(allocatableId);
                for (Appointment appointment : s)
                {
                    transitive.add(appointment);
                }
            }
            return transitive;
        }
    }

    protected SortedSet<Appointment> getAppointmentsForUser(ReferenceInfo<User> user) {
        SortedSet<Appointment> s = appointmentBindings.getAppointmentsForUser(user);
        if (s != null) {
            return s;
        }
        return EMPTY_SORTED_SET;
    }

    static final class AppointmentMapClass
    {
        final private Logger logger;
        private Map<ReferenceInfo<User>, SortedSet<Appointment>> appointmentUserMap;
        private Map<ReferenceInfo<Allocatable>, SortedSet<Appointment>> appointmentMap;

        private Map<ReferenceInfo<Reservation>, Set<ReferenceInfo<Allocatable>>> reservationAllocatableMap;

        private Map<ReferenceInfo<Reservation>, ReferenceInfo<User>> reservationUserMap;

        Set<String> problematicIdSet = Collections.synchronizedSet(new HashSet<>());


        private AppointmentMapClass(Logger newLogger)
        {
            logger = newLogger;
        }

        private void initAppointmentBindings(Collection<Reservation> reservations)
        {
            appointmentMap = new ConcurrentHashMap<>();
            appointmentUserMap = new ConcurrentHashMap<>();
            reservationAllocatableMap = new ConcurrentHashMap<>();
            reservationUserMap = new ConcurrentHashMap<>();
            for (Reservation r : reservations)
            {
                updateReservation(r, new HashSet<>(), false);
            }

        }

        private void updateReservation(Reservation r, Set<ReferenceInfo<Allocatable>> toUpdate, boolean remove) {
            ReferenceInfo<Reservation> reference = r.getReference();
            ReferenceInfo<User> oldUser = reservationUserMap.get(reference);
            Set<ReferenceInfo<Allocatable>> oldResources = reservationAllocatableMap.get(reference);
            ReferenceInfo<User> newUser = r.getOwnerRef();
            ReservationImpl event = (ReservationImpl) r;
            final String annotation = event.getAnnotation(RaplaObjectAnnotations.KEY_TEMPLATE);
            ReferenceInfo<Allocatable> templateAlloc = (annotation != null) ? new ReferenceInfo(annotation, Allocatable.class) : null;

            if ( oldResources != null) {
                for (ReferenceInfo<Allocatable> alloc: oldResources) {
                    SortedSet<Appointment> appointments = appointmentMap.get(alloc);
                    if ( appointments != null) {
                        Iterator<Appointment> it = appointments.iterator();
                        while (it.hasNext()) {
                            Appointment app = it.next();
                            Reservation parent = app.getReservation();
                            if ( parent == null) {
                                toUpdate.add( alloc );
                                it.remove();
                                break;
                            }
                            ReferenceInfo<Reservation> referenceParent = parent.getReference();
                            if (referenceParent.equals( reference)) {
                                // check if appointment still allocates reservation or is the template
                                if (remove || !(event.hasAllocatedOnRef(alloc, app) && !alloc.equals( templateAlloc))) {
                                    toUpdate.add( alloc );
                                    it.remove();
                                    break;
                                } else {
                                    // check if appointment has changed; if so remove it and we add it later
                                    Appointment newAppointment = event.findAppointment( app );
                                    if ( newAppointment == null || !newAppointment.matches( app) ) {
                                        toUpdate.add( alloc );
                                        it.remove();
                                        break;
                                    }
                                }
                            }
                        }
                        if ( appointments.isEmpty()) {
                            appointmentMap.remove( alloc );
                        }
                    }
                }
            }
            if ( oldUser != null) {
                SortedSet<Appointment> appointments = appointmentUserMap.get(oldUser);
                if ( appointments != null) {
                    Iterator<Appointment> it = appointments.iterator();
                    while (it.hasNext()) {
                        Appointment app = it.next();
                        Reservation parent = app.getReservation();
                        if ( parent == null) {
                            it.remove();
                            break;
                        }
                        if (parent.getReference().equals( reference)) {
                            if ( !parent.getOwnerRef().equals(newUser) ) {
                                it.remove();
                                break;
                            }
                            // check if appointment still allocates reservation
                            Appointment newAppointment = event.findAppointment( app );

                            if (remove || newAppointment == null) {
                                it.remove();
                                break;
                            } else {
                                // check if appointment has changed; if so remove it and we add it later
                                if ( !newAppointment.matches( app) ) {
                                    it.remove();
                                    break;
                                }
                            }
                        }
                    }
                    if ( appointments.isEmpty()) {
                        appointmentUserMap.remove( oldUser );
                    }
                }
            }
            if ( remove ) {
                reservationUserMap.remove( reference );
                reservationAllocatableMap.remove( reference);
                return;
            }
            reservationUserMap.put( reference, newUser );
            Set<ReferenceInfo<Allocatable>> newResources = new HashSet<>(event.getIds("resources").stream().map(id->new ReferenceInfo<Allocatable>(id,Allocatable.class)).collect(Collectors.toSet()));
            if (templateAlloc != null) {
                newResources.add( templateAlloc);
            }
            Appointment[] allAppointments = event.getAppointments();
            for (ReferenceInfo<Allocatable> alloc: newResources) {
                toUpdate.add( alloc );
                SortedSet<Appointment> appointments;
                synchronized (alloc.getId().intern()) {
                    appointments = appointmentMap.get(alloc);
                    if (appointments == null) {
                        appointments = new ConcurrentSkipListSet<>(new AppointmentStartComparator());
                        appointmentMap.put(alloc, appointments);
                    }
                }
                Appointment[] restrictionForAllocatableRef = event.getRestrictionForAllocatableRef(alloc.getId());
                Appointment[] newAppointments = (restrictionForAllocatableRef.length == 0) ? allAppointments : restrictionForAllocatableRef;
                for (Appointment app : newAppointments) {
                    appointments.remove( app );
                    appointments.add(app);
                }
            }
            reservationAllocatableMap.put( reference, new HashSet<>(newResources));
            {
                SortedSet<Appointment> appointments;
                synchronized (newUser.getId().intern()) {
                    appointments = appointmentUserMap.get(newUser);
                    if (appointments == null) {
                        appointments = new ConcurrentSkipListSet<>(new AppointmentStartComparator());
                        appointmentUserMap.put(newUser, appointments);
                    }
                }
                for (Appointment app : allAppointments) {
                    appointments.remove( app );
                    appointments.add(app);
                }
            }
            reservationUserMap.put( reference, newUser );
        }

        // this check is only there to detect rapla bugs in the conflict api and can be removed if it causes performance issues
        public boolean checkAbandonedAppointments(LocalCache cache)
        {
            Collection<? extends Allocatable> allocatables = cache.getAllocatables();
            Logger logger = this.logger.getChildLogger("appointmentcheck");
            try
            {
                for (Allocatable allocatable : allocatables)
                {
                    SortedSet<Appointment> appointmentSet = this.appointmentMap.get(allocatable.getReference());
                    if (appointmentSet == null)
                    {
                        continue;
                    }
                    Iterator<Appointment> it = appointmentSet.iterator();
                    while (it.hasNext())
                    {
                        Appointment app = it.next();
                        Reservation reservation = app.getReservation();
                        if (reservation == null)
                        {
                            logger.error("Appointment without a reservation stored in cache " + app);
                            return false;
                        }
                        final String annotation = reservation.getAnnotation(RaplaObjectAnnotations.KEY_TEMPLATE);
                        Allocatable template = annotation != null ? cache.tryResolve(annotation, Allocatable.class) : null;
                        if (!reservation.hasAllocatedOn(allocatable, app) && (template == null || !template.equals(allocatable)))
                        {
                            logger.error(
                                    "Allocation is not stored correctly for " + reservation + " " + app + " " + allocatable + " removing binding for " + app);
                            return false;
                        }
                        else
                        {
                            {
                                Reservation original = reservation;
                                ReferenceInfo<Reservation> id = original.getReference();
                                if (id == null)
                                {
                                    logger.error("Empty id  for " + original);
                                    continue;
                                }
                                Reservation persistent = cache.tryResolve(id);
                                if (persistent != null)
                                {
                                    Date lastChanged = original.getLastChanged();
                                    Date persistantLastChanged = persistent.getLastChanged();
                                    if (persistantLastChanged != null && !persistantLastChanged.equals(lastChanged))
                                    {
                                        if ( !problematicIdSet.contains( persistent.getId()))
                                        {
                                            problematicIdSet.add( persistent.getId());
                                            logger.error("Reservation " + persistent.getId() + " stored in LocalCache " + persistantLastChanged + ": " + persistent.getSortedAppointments() + " is not the same as in appointment store " + lastChanged + ":" + original.getSortedAppointments());
                                            //updateReservation( persistent, new HashSet<>(), false);
                                            return false;
                                        }
                                        continue;
                                    }
                                }
                                else
                                {
                                    logger.error("Reservation not stored in cache " + original + " removing binding for " + app);
                                    //updateReservation( original, new HashSet<>(), true);
                                    return false;
                                }
                            }

                        }
                    }
                }
            }
            catch (Exception ex)
            {
                logger.error(ex.getMessage(), ex);
            }
            return true;
        }

        final SortedSet<Appointment> EMPTY_SORTED_REF_SET = Collections.unmodifiableSortedSet(new TreeSet<Appointment>());

        public SortedSet<Appointment> getAppointments(ReferenceInfo<Allocatable> allocatableId)
        {
            final SortedSet<Appointment> referenceInfos = appointmentMap.get(allocatableId);
            if (referenceInfos != null)
            {
                return referenceInfos;
            }
            return EMPTY_SORTED_REF_SET;
        }

        public SortedSet<Appointment> getAppointmentsForUser(ReferenceInfo<User> userId)
        {
            final SortedSet<Appointment> referenceInfos = appointmentUserMap.get(userId);
            if (referenceInfos != null)
            {
                return referenceInfos;
            }
            return EMPTY_SORTED_REF_SET;
        }

    }

    protected UpdateResult refresh(Date since, Date until, Collection<Entity> storeObjects, Collection<PreferencePatch> preferencePatches,
            Collection<ReferenceInfo> removedIds) throws RaplaException
    {

        UpdateResult update = super.update(since, until, storeObjects, preferencePatches, removedIds);
        final Collection<ConflictFinder.ConflictChangeOperation> updateOperations = updateIndizes(update);
        for (ConflictFinder.ConflictChangeOperation op : updateOperations)
        {
            // conflicts
            update.addOperation(op.getNewConflict(), op.getOldConflict(), op.getOperation());
        }
        return update;
    }

    private void removeOldHistory()
    {
        Date lastUpdated = getLastRefreshed();
        Date date = new Date(lastUpdated.getTime() - HISTORY_DURATION);
        history.removeUnneeded(date);
    }

    private void removeOldConflicts()
    {
        Date today = today();
        Set<ReferenceInfo<Conflict>> conflictsToDelete;
        {
            conflictsToDelete = new HashSet<>(conflictFinder.removeOldConflicts(today));
            conflictsToDelete.retainAll(cache.getDisabledConflictIds());
        }

        Collection<Conflict> conflicts = cache.getDisabledConflicts();
        for (Conflict conflict : conflicts)
        {
            if (!conflictFinder.isActiveConflict(conflict, today))
            {
                conflictsToDelete.add(conflict.getReference());
            }
        }

        if (!conflictsToDelete.isEmpty())
        {
            final String message = "Removing old conflicts " + conflictsToDelete.size();
            getLogger().info(message);
            removeConflictsFromDatabase(conflictsToDelete);
            //Order is important they can't be removed from database if they are not in cache
            removeConflictsFromCache(conflictsToDelete);
        }
    }

    protected void removeConflictsFromCache(Collection<ReferenceInfo<Conflict>> disabledConflicts)
    {
        for (ReferenceInfo<Conflict> conflictId : disabledConflicts)
        {
            cache.removeWithId(conflictId);
        }
    }

    protected void removeConflictsFromDatabase(@SuppressWarnings("unused") Collection<ReferenceInfo<Conflict>> disabledConflicts)
    {
    }

    private Set<ReferenceInfo<Conflict>> getConflictsToDelete(Collection<UpdateOperation> operations)
    {
        Set<ReferenceInfo<Conflict>> conflicts = new HashSet<>();
        for (UpdateOperation op : operations)
        {
            final ReferenceInfo ref = op.getReference();
            if (op instanceof Remove)
            {
                if (cache.getDisabledConflictIds().contains(ref.getId()))
                {
                    conflicts.add(ref);
                }
            }
            if (op instanceof Change)
            {
                Conflict conflict = (Conflict) tryResolve(ref);
                if (conflict != null && conflict.isAppointment1Enabled() && conflict.isAppointment2Enabled())
                {
                    conflicts.add(conflict.getReference());
                }
            }
        }
        return conflicts;
    }

    protected void preprocessEventStorage(final UpdateEvent evt) throws RaplaException
    {
        EntityStore store = new EntityStore(this);
        Collection<Entity> storeObjects = evt.getStoreObjects();
        for (Entity entity : storeObjects)
        {
            store.put(entity);
        }
        //    Map<String,Category> categoriesToStore = new LinkedHashMap<String,Category>();
        //    Collections.sort(categoriesToStore, new Comparator<Category>()
        //        {
        //            @Override
        //            public int compare(Category o1, Category o2)
        //            {
        //                return o1.compareTo(o2);
        //            }
        //        });
        //        for (Category category : categoriesToStore.values())
        //        {
        //            evt.putStore(category);
        //        }

        for (Entity entity : storeObjects)
        {
            if (getLogger().isDebugEnabled())
                getLogger().debug("Contextualizing " + entity);
            ((EntityReferencer) entity).setResolver(store);
            if (entity instanceof DynamicType)
            {
                ((DynamicTypeImpl) entity).setOperator(this);
            }
            // add all child categories to store
        }
        //        Collection<Entity>removeObjects = evt.getRemoveIds();
        //        store.addAll( removeObjects );
        //
        //        for ( Entity entity:removeObjects)
        //        {
        //            ((EntityReferencer)entity).setResolver( store);
        //        }
        // add transitve changes to event
        addClosure(evt, store);
        // check event for inconsistencies
        check(evt, store);
    }

    /**
     * Create a closure for all objects that should be updated. The closure
     * contains all objects that are sub-entities of the entities and all
     * objects and all other objects that are affected by the update: e.g.
     * Classifiables when the DynamicType changes. The method will recursivly
     * proceed with all discovered objects.
     */
    protected void addClosure(final UpdateEvent evt, EntityStore store) throws RaplaException
    {
        User user = null;
        if (evt.getUserId() != null)
        {
            user = resolve(cache, evt.getUserId(), User.class);
        }
        Collection<Entity> storeObjects = new ArrayList<>(evt.getStoreObjects());
        Collection<ReferenceInfo> removeIds = new ArrayList<>(evt.getRemoveIds());
        for (Entity entity : storeObjects)
        {
            Class<? extends Entity> raplaType = entity.getTypeClass();
            if (raplaType == DynamicType.class)
            {
                DynamicTypeImpl dynamicType = (DynamicTypeImpl) entity;
                addChangedDynamicTypeDependant(evt, user, store, dynamicType, false);
            }
            if (entity instanceof Classifiable)
            {
                processOldPermssionModify(store, entity);
            }
        }
        Set<String> categoriesToRemove = new HashSet<>();
        Set<String> categoriesToStore = new HashSet<>();
        Collection<Entity> dynamicTypesToStore = new HashSet<>();
        for (Entity entity : storeObjects)
        {
            // update old classifiables, that may not been update before via a change event
            // that could be the case if an old reservation is restored via undo but the dynamic type changed in between.
            // The undo cache does not notice the change in type
            if (entity instanceof Classifiable && entity instanceof Timestamp)
            {
                Date lastChanged = ((LastChangedTimestamp) entity).getLastChanged();
                ClassificationImpl classification = (ClassificationImpl) ((Classifiable) entity).getClassification();
                DynamicTypeImpl dynamicType = classification.getType();
                Date typeLastChanged = dynamicType.getLastChanged();
                if (typeLastChanged != null && lastChanged != null && typeLastChanged.after(lastChanged))
                {
                    if (classification.needsChange(dynamicType))
                    {
                        addChangedDependencies(evt, user, store, dynamicType, entity, false);
                    }
                }
            }
            if (entity instanceof Category)
            {
                final CategoryImpl category = (CategoryImpl) entity;
                addReferers(cache.getDynamicTypes(), category, dynamicTypesToStore);
                final ReferenceInfo<Category> reference = entity.getReference();
                ReferenceInfo<Category> parentReference = category.getParentRef();
                categoriesToStore.add(reference.getId());
                // remove childs categories from cache if stored version does not contain the childs anymore
                {
                    // support a move from one parent to the next. We dont add the category to remove if its found as child in on of the stored categories
                    final Collection<String> storedChildIds = category.getChildIds();
                    categoriesToStore.addAll(storedChildIds);
                    // test if category already exists
                    final CategoryImpl exisiting = (CategoryImpl) cache.tryResolve(reference);
                    if (exisiting != null)
                    {
                        final Collection<String> exisitingChildIds = exisiting.getChildIds();
                        final Collection<String> toRemove = new HashSet<>(exisitingChildIds);
                        toRemove.removeAll(storedChildIds);
                        categoriesToRemove.addAll(toRemove);
                    }
                }
                if (!reference.equals(Category.SUPER_CATEGORY_REF))
                {
                    // add to supercategory if no parent is specfied
                    if (parentReference == null)
                    {
                        parentReference = Category.SUPER_CATEGORY_REF;
                    }
                    // if parent of category is not submitted then try to find parent and edit parent as well
                    final CategoryImpl exisitingParent = (CategoryImpl) cache.tryResolve(parentReference);
                    if (exisitingParent != null && !exisitingParent.hasCategory(category))
                    {
                        Category editableParent = (Category) evt.findEntity(exisitingParent);
                        if (editableParent == null)
                        {
                            editableParent = editObject(null, exisitingParent, user);
                            evt.addToStoreIfNotExisitant(editableParent);
                            categoriesToStore.add(exisitingParent.getReference().getId());
                        }
                        editableParent.addCategory(category);
                    }
                }
            }

            // TODO add conversion of classification filters or other dynamictypedependent that are stored in preferences
            //			for (PreferencePatch patch:evt.getPreferencePatches())
            //			{
            //			    for (String key: patch.keySet())
            //			    {
            //			        Object object = patch.get( key);
            //			        if ( object instanceof DynamicTypeDependant)
            //			        {
            //
            //			        }
            //			    }
            //			}
        }
        for (Entity dynamicType : dynamicTypesToStore)
        {
            final Entity clone = editObject(dynamicType, null, user);
            evt.addToStoreIfNotExisitant(clone);
        }
        categoriesToRemove.removeAll(categoriesToStore);
        for (ReferenceInfo removeId : removeIds)
        {
            final Class<? extends Entity> raplaType = removeId.getType();
            Entity entity = store.tryResolve(removeId);
            if (entity == null)
            {
                continue;
            }
            if (DynamicType.class == raplaType)
            {
                DynamicTypeImpl dynamicType = (DynamicTypeImpl) entity;
                addChangedDynamicTypeDependant(evt, user, store, dynamicType, true);
            }
            // If entity is a user, remove the preference object
            if (User.class == raplaType)
            {
                addRemovedUserDependant(evt, store, (User) entity);
            }
            if (entity instanceof Annotatable)
            {
                final String externalID = ((Annotatable) entity).getAnnotation(RaplaObjectAnnotations.KEY_EXTERNALID);
                // also remove import export entities
                if (externalID != null)
                {
                    final ReferenceInfo<ExternalSyncEntity> ref = new ReferenceInfo(externalID, ExternalSyncEntity.class);
                    if ( isDeleteExportEntity( entity)) {
                        evt.putRemoveId(ref);
                    }  else {
                        ExternalSyncEntityImpl syncEntity = new ExternalSyncEntityImpl();
                        // if we only set the id but no system then we just replace the data entry of an exisiting entry
                        syncEntity.setId( ref );
                        syncEntity.setData("");
                        evt.addStore(syncEntity);
                    }
                }
            }
            if (Category.class == raplaType)
            {
                CategoryImpl category = (CategoryImpl) tryResolve(removeId);
                if (category != null)
                {
                    ReferenceInfo<Category> parentReference = category.getParentRef();
                    final CategoryImpl exisitingParent = (CategoryImpl) cache.tryResolve(parentReference);
                    if (exisitingParent != null && exisitingParent.hasCategory(category))
                    {
                        if (!isInDeleted(exisitingParent, categoriesToRemove, 0))
                        {
                            Category editableParent = (Category) evt.findEntity(exisitingParent);
                            if (editableParent == null)
                            {
                                editableParent = exisitingParent.clone();
                                evt.addToStoreIfNotExisitant(editableParent);
                                categoriesToStore.add(exisitingParent.getReference().getId());
                            }
                            Category removableCategory = category.clone();
                            editableParent.removeCategory(removableCategory);
                        }
                    }
                    categoriesToRemove.add(removeId.getId());
                }
            }
        }
        for (String categoryId : categoriesToRemove)
        {
            addCategoryToRemove(evt, categoriesToStore, categoryId, 0);
        }
    }

    private boolean isDeleteExportEntity(Entity entity) {
        if ( ! ( entity instanceof Classifiable )){
            return  true;
        }
        Classification classification = ((Classifiable) entity).getClassification();
        if (classification == null) {
            return true;
        }
        String annotation = classification.getType().getAnnotation(DynamicTypeAnnotations.KEY_DELETE_EXTERNALS_ON_RESOURCE_DELETE);
        return !"false".equalsIgnoreCase(annotation);
    }

    private boolean isInDeleted(Category exisitingParent, Set<String> categoriesToRemove, int depth)
    {
        if (depth > 20)
        {
            throw new IllegalStateException("Category Cycle detected in " + exisitingParent);
        }
        final ReferenceInfo<Category> ref = exisitingParent.getReference();
        if (categoriesToRemove.contains(ref.getId()))
        {
            return true;
        }
        final ReferenceInfo<Category> parentRef = ((CategoryImpl) exisitingParent).getParentRef();
        if (parentRef == null)
        {
            return false;
        }
        final Category parent = cache.tryResolve(parentRef);
        if (parent == null)
        {
            return false;
        }
        return isInDeleted(parent, categoriesToRemove, depth + 1);
    }

    private void addCategoryToRemove(UpdateEvent evt, Set<String> categoriesToStore, String categoryId, int depth)
    {
        if (depth > 20)
        {
            throw new IllegalStateException("Category Cycle detected while removing");
        }
        final ReferenceInfo<Category> ref = new ReferenceInfo<>(categoryId, Category.class);
        if (!categoriesToStore.contains(ref.getId()))
        {
            evt.putRemoveId(ref);
            Category existing = cache.tryResolve(ref);
            if (existing != null)
            {
                final Collection<String> childIds = ((CategoryImpl) existing).getChildIds();
                for (String childId : childIds)
                {
                    addCategoryToRemove(evt, categoriesToStore, childId, depth + 1);
                }
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void processOldPermssionModify(@SuppressWarnings("unused") EntityStore store, Entity entity)
    {
        Class<? extends Entity> clazz = (entity instanceof Reservation) ? Reservation.class : Allocatable.class;
        Classifiable persistant = (Classifiable) tryResolve(entity.getId(), clazz);
        Util.processOldPermissionModify((Classifiable) entity, persistant);
    }

    protected void addChangedDynamicTypeDependant(UpdateEvent evt, User user, EntityStore store, DynamicTypeImpl type, boolean toRemove) throws RaplaException
    {
        Set<Entity> referencingEntities = getReferencingEntities(type, store);
        for (Entity entity : referencingEntities) {
            if (!(entity instanceof DynamicTypeDependant)) {
                continue;
            }
            DynamicTypeDependant dependant = (DynamicTypeDependant) entity;
            // Classifiables need update?
            if (!dependant.needsChange(type) && !toRemove)
                continue;
            if (getLogger().isDebugEnabled())
                getLogger().debug("Classifiable " + entity + " needs change!");
            // Classifiables are allready on the store list
            addChangedDependencies(evt, user, store, type, entity, toRemove);
        }
    }

    private void addChangedDependencies(UpdateEvent evt, User user, EntityStore store, DynamicTypeImpl type, Entity entity, boolean toRemove)
    {
        DynamicTypeDependant dependant = (DynamicTypeDependant) evt.findEntity(entity);
        if (dependant == null)
        {
            // no, then create a clone of the classfiable object and add to list

            Class<Entity> entityType = entity.getTypeClass();
            Entity persistant = store.tryResolve(entity.getId(), entityType);
            dependant = (DynamicTypeDependant) editObject(entity, persistant, user);
            // replace or add the modified entity
            evt.addToStoreIfNotExisitant((Entity) dependant);
        }
        if (toRemove)
        {
            try
            {
                dependant.commitRemove(type);
            }
            catch (CannotExistWithoutTypeException ex)
            {
                // getLogger().warn(ex.getMessage(),ex);
            }
        }
        else
        {
            dependant.commitChange(type);
        }
    }

    // add all objects that are dependet on a user and can be safely removed and are added to the remove list
    private void addRemovedUserDependant(UpdateEvent updateEvt, EntityStore store, User user)
    {
        PreferencesImpl preferences = cache.getPreferencesForUserId(user.getId());
        // remove preferences of user
        if (preferences != null)
        {
            updateEvt.putRemove(preferences);
        }
        Set<Entity> referencingEntities = getReferencingEntities(user, store);
        Iterator<Entity> it = referencingEntities.iterator();
        List<Allocatable> templates = new ArrayList<>();
        while (it.hasNext())
        {
            Entity entity = it.next();
            // Remove internal resources automatically if the owner is deleted
            if (entity instanceof Classifiable && entity instanceof Ownable)
            {
                Classification classification = ((Classifiable) entity).getClassification();
                DynamicType type = classification.getType();
                // remove all internal resources (e.g. templates) that have the user as owner
                if (((DynamicTypeImpl) type).isInternal())
                {
                    ReferenceInfo<User> ownerId = ((Ownable) entity).getOwnerRef();
                    if (ownerId != null && ownerId.isSame(user.getReference()))
                    {
                        updateEvt.putRemove(entity);
                        if (type.getKey().equals(StorageOperator.RAPLA_TEMPLATE))
                        {
                            templates.add((Allocatable) entity);
                        }
                        continue;
                    }
                }
            }
            // change the lastchangedby for all objects that are last edited by the user. Change last changed to null
            if (entity instanceof Timestamp)
            {
                Timestamp timestamp = (Timestamp) entity;
                ReferenceInfo<User> lastChangedBy = timestamp.getLastChangedBy();
                if (lastChangedBy == null || !lastChangedBy.equals(user.getReference()))
                {
                    continue;
                }
                if (entity instanceof Ownable)
                {
                    ReferenceInfo<User> ownerId = ((Ownable) entity).getOwnerRef();
                    // we do nothing if the user is also owner,  that dependencies need to be resolved manually
                    if (ownerId != null && ownerId.equals(user.getReference()))
                    {
                        continue;
                    }
                }
                // check if entity is already in updateEvent (e.g. is modified)
                final Entity updateEntity = updateEvt.findEntity(entity);
                if (updateEntity != null)
                {
                    ((SimpleEntity) updateEntity).setLastChangedBy(null);
                }
                else
                {
                    @SuppressWarnings("unchecked")
                    Class<? extends Entity> typeClass = entity.getTypeClass();
                    Entity persistant = cache.tryResolve(entity.getId(), typeClass);
                    Entity dependant = editObject(entity, persistant, user);
                    ((SimpleEntity) dependant).setLastChangedBy(null);
                    updateEvt.addToStoreIfNotExisitant(entity);
                }
            }
        }
        // now delete template events for the removed templates
        for (Allocatable template : templates)
        {
            String templateId = template.getId();
            Collection<Reservation> reservations = cache.getReservations();
            for (Reservation reservation : reservations)
            {
                if (reservation.getAnnotation(RaplaObjectAnnotations.KEY_TEMPLATE, "").equals(templateId))
                {
                    updateEvt.putRemove(reservation);
                }
            }
        }
    }

    /**
     * returns all entities that depend one the passed entities. In most cases
     * one object depends on an other object if it has a reference to it.
     */
    final protected Map<ReferenceInfo,Set<Entity>> getDependencies(Collection<Entity> entityList, EntityStore store)
    {
        Set<ReferenceInfo> set = entityList.stream().filter(x -> {
            Class<? extends Entity> type = x.getTypeClass();
            return (Category.class == type || DynamicType.class == type || Allocatable.class == type || User.class == type);
        }).map(Entity::getReference).collect(Collectors.toSet());
        Map<ReferenceInfo, Set<Entity>> referencingEntities = getReferencingEntities(set, store);
        return referencingEntities;
    }

    private void removeLastChangedReference(Collection<Entity> entityList, Map<ReferenceInfo, Set<Entity>> referencingEntities) {
        for (Entity entity:entityList) {
            Class<? extends Entity> type = entity.getTypeClass();
            if ( type != User.class) {
                continue;
            }
            ReferenceInfo<User> reference = entity.getReference();
            Set<Entity> entities = referencingEntities.get(reference);
            if ( entities == null) {
                continue;
            }
            for (Iterator<Entity> it= entities.iterator();it.hasNext();){
                Entity referencing = it.next();
                if ( entityList.contains( referencing)) {
                    it.remove();
                    continue;
                }
                if (isOnlyLastChangedReference(reference, referencing)) {
                    it.remove();
                }
            }
        }
    }

    private static boolean isOnlyLastChangedReference(ReferenceInfo<User> reference, Entity referencing) {
        SimpleEntity simpleEntity = (SimpleEntity) referencing;
        ReferenceInfo<User> lastChangedBy = simpleEntity.getLastChangedBy();
        int count = 0;
        if ( reference.equals( lastChangedBy) && !reference.equals( simpleEntity.getOwnerRef())) {
            Class<? extends Entity> typeOfReferencing = referencing.getTypeClass();
            if (typeOfReferencing == Allocatable.class || typeOfReferencing == Reservation.class || typeOfReferencing == Category.class){
                for (ReferenceInfo referenceInfo : simpleEntity.getReferenceInfo()) {
                    if ( referenceInfo.equals(reference)) {
                        count++;
                    }
                }
            }
        }
        boolean isOnlyLastChanged = count == 1;
        return isOnlyLastChanged;
    }


    private Set<Entity> getReferencingEntities(Entity entity, EntityStore store)
    {
        Map<ReferenceInfo, Set<Entity>> referencingEntities = getReferencingEntities(Collections.singleton(entity.getReference()), store);
        Set<Entity> entities = referencingEntities.get(entity.getReference());
        if ( entities == null) {
            return Collections.emptySet();
        }
        return entities;
    }

    public Set<ReferenceInfo<Allocatable>> filterAllocatablesWithNonTemplateReservations(Set<ReferenceInfo<Allocatable>> allocatables) {
        Iterable<Reservation> refererList = cache.getReservations();
        Set<ReferenceInfo<Allocatable>> entityReferences = allocatables;
        Set<ReferenceInfo<Allocatable>> result = new HashSet<>();
        for (Reservation referer : refererList)
        {
            // we ingnore Templates
            if (RaplaComponent.isTemplate( referer ))
            {
                continue;
            }
            if (referer != null && !entityReferences.contains(referer.getReference()))
            {
                Iterable<ReferenceInfo> referenceInfo = ((EntityReferencer) referer).getReferenceInfo();
                for (ReferenceInfo info : referenceInfo)
                {
                    if (entityReferences.contains( info ))
                    {
                        result.add( info);
                    }
                }
            }
        }
        return result;
    }

    @NotNull
    private Map<ReferenceInfo, Set<Entity>> getReferencingEntities(Set<ReferenceInfo> entityReferences, EntityStore store) {
        Map<ReferenceInfo,Set<Entity>> result = new LinkedHashMap<>();
        addReferers(cache.getReservations(), entityReferences, result);
        addReferers(cache.getAllocatables(), entityReferences, result);
        Collection<User> users = cache.getUsers();
        addReferers(users, entityReferences, result);
        addReferers(cache.getDynamicTypes(), entityReferences, result);
        addReferers(CategoryImpl.getRecursive(cache.getSuperCategory()), entityReferences, result);

        List<Preferences> preferenceList = new ArrayList<>();
        for (User user : users)
        {
            PreferencesImpl preferences = cache.getPreferencesForUserId(user.getId());
            if (preferences != null)
            {
                preferenceList.add(preferences);
            }
        }
        PreferencesImpl systemPreferences = cache.getPreferencesForUserId(null);
        if (systemPreferences != null)
        {
            preferenceList.add(systemPreferences);
        }
        addReferers(preferenceList, entityReferences, result);
        return result;
    }

    private void addReferers(Iterable<? extends Entity> refererList, Set<ReferenceInfo> entityReferences, Map<ReferenceInfo,Set<Entity>> result)
    {
        for (Entity referer : refererList)
        {
            if (referer != null && !entityReferences.contains(referer.getReference()))
            {
                Iterable<ReferenceInfo> referenceInfo = ((EntityReferencer) referer).getReferenceInfo();
                for (ReferenceInfo info : referenceInfo)
                {
                    if (entityReferences.contains( info ))
                    {
                        Set<Entity> entities = result.computeIfAbsent(info, (k) -> new HashSet<>());
                        entities.add( referer);
                    }
                }
            }
        }
    }

    private void addReferers(Iterable<? extends Entity> refererList, Entity object, Collection<Entity> result)
    {
        for (Entity referer : refererList)
        {
            if (referer != null && !referer.isIdentical(object))
            {
                for (ReferenceInfo info : ((EntityReferencer) referer).getReferenceInfo())
                {
                    if (info.isReferenceOf(object))
                    {
                        result.add(referer);
                    }
                }
            }
        }
    }

    private int countDynamicTypes(Collection<? extends RaplaObject> entities, Set<String> classificationTypes) throws RaplaException
    {
        Iterator<? extends RaplaObject> it = entities.iterator();
        int count = 0;
        while (it.hasNext())
        {
            RaplaObject entity = it.next();
            if (DynamicType.class != entity.getTypeClass())
                continue;
            DynamicType type = (DynamicType) entity;
            String annotation = type.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
            if (annotation == null)
            {
                throw new RaplaException(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE + " not set for " + type);
            }
            if (classificationTypes.contains(annotation))
            {
                count++;
            }
        }
        return count;
    }

    // Count dynamic-types to ensure that there is least one dynamic type left
    private void checkDynamicType(Collection<Entity> entities, Set<String> classificationTypes) throws RaplaException
    {
        int count = countDynamicTypes(entities, classificationTypes);
        Collection<? extends DynamicType> allTypes = cache.getDynamicTypes();
        int countAll = countDynamicTypes(allTypes, classificationTypes);
        if (count >= 0 && count >= countAll)
        {
            throw new RaplaException(i18n.getString("error.one_type_requiered"));
        }
    }

    /**
     * Check if the references of each entity refers to an object in cache or in
     * the passed collection.
     */
    final protected void setResolverAndCheckReferences(UpdateEvent evt, EntityStore store) throws RaplaException
    {

        for (EntityReferencer entity : evt.getEntityReferences())
        {
            entity.setResolver(store);
            for (ReferenceInfo info : entity.getReferenceInfo())
            {
                String id = info.getId();
                // Reference in cache or store?
                Class class1 = info.getType();
                Entity resolved = store.tryResolve(id, class1);
                if (resolved != null)
                    continue;
                resolved = ReferenceHandler.tryResolveMissingAllocatable(store, id, class1);
                if (resolved != null)
                    continue;
                throw new EntityNotFoundException(i18n.format("error.reference_not_stored", class1 + ":" + id));
            }
        }
    }

    /**
     * check if we find an object with the same name. If a different object
     * (different id) with the same unique attributes is found a
     * UniqueKeyException will be thrown.
     */
    final protected void checkUnique(final UpdateEvent evt, final EntityStore store) throws RaplaException
    {
        for (Entity entity : evt.getStoreObjects())
        {
            final Class<? extends Entity> typeClass = entity.getTypeClass();
            if (DynamicType.class == typeClass)
            {
                DynamicType type = (DynamicType) entity;
                String name = type.getKey();
                Entity entity2 = store.getDynamicType(name);
                if (entity2 != null && !entity2.equals(entity))
                    throwNotUnique(name);
            }

            if (Category.class == typeClass)
            {
                Category category = (Category) entity;
                Category[] categories = category.getCategories();
                for (int i = 0; i < categories.length; i++)
                {
                    final Category category1 = categories[i];
                    String key = category1.getKey();
                    for (int j = i + 1; j < categories.length; j++)
                    {
                        final Category category2 = categories[j];
                        String key2 = category2.getKey();
                        if (Objects.equals(key, key2))
                        {
                            throwNotUnique(key);
                        }
                    }
                }
            }

            if (User.class == entity.getTypeClass())
            {
                String name = ((User) entity).getUsername();
                if (name == null || name.trim().isEmpty())
                {
                    String message = i18n.format("error.no_entry_for", getString("username"));
                    throw new RaplaException(message);
                }
                // FIXME Replace with store.getUserFromRequest for the rare case that two users with the same username are stored in one operation
                Entity entity2 = cache.getUser(name);
                if (entity2 != null && !entity2.equals(entity))
                    throwNotUnique(name);
            }
        }
    }

    private void throwNotUnique(String name) throws UniqueKeyException
    {
        throw new UniqueKeyException(i18n.format("error.not_unique", name));
    }

    /**
     * compares the version of the cached entities with the versions of the new
     * entities. Throws an Exception if the newVersion != cachedVersion
     */
    protected void checkVersions(Collection<Entity> entities) throws RaplaException
    {
        for (Entity entity : entities)
        {
            // Check Versions
            Entity persistantVersion = findPersistent(entity);
            // If the entities are newer, everything is o.k.
            if (persistantVersion != null && persistantVersion != entity)
            {
                if ((persistantVersion instanceof Timestamp))
                {
                    Date lastChangeTimePersistant = ((LastChangedTimestamp) persistantVersion).getLastChanged();
                    Date lastChangeTime = ((LastChangedTimestamp) entity).getLastChanged();
                    if (lastChangeTimePersistant != null && lastChangeTime != null && lastChangeTimePersistant.after(lastChangeTime))
                    {
                        getLogger().warn("There is a newer  version for: " + entity.getId() + " stored version :" + SerializableDateTimeFormat.INSTANCE
                                .formatTimestamp(lastChangeTimePersistant) + " version to store :" + SerializableDateTimeFormat.INSTANCE
                                .formatTimestamp(lastChangeTime));
                        throw new RaplaNewVersionException(getI18n().format("error.new_version", entity.toString()));
                    }
                }

            }
        }
    }

    protected Collection<ReferenceInfo> removeInconsistentEntities(LocalCache cache, Collection<Entity> list)
    {
        List<ReferenceInfo> toRemove = new ArrayList<>();
        for (Iterator iterator = list.iterator(); iterator.hasNext(); )
        {
            Entity entity = (Entity) iterator.next();
            final Class<? extends Entity> typeClass = entity.getTypeClass();
            // Don't check types and categories for inconsistencies
            if (typeClass == DynamicType.class || typeClass == Category.class )
            {
                continue;
            }
            try
            {
                checkConsitency(entity, cache);
            }
            catch (RaplaException | IllegalStateException e)
            {
                if (entity instanceof Conflict && e instanceof EntityNotFoundException)
                {
                    getLogger().info("Not loading disabled conflict with id: " + entity.getId() + " appointment not found, so conflict is probably removed.");
                }
                else
                {
                    getLogger().error("Not loading entity with id: " + entity.getId(), e);
                }
                toRemove.add( entity.getReference() );
                cache.remove(entity);
                iterator.remove();
            }
        }
        return toRemove;
    }

    /** Check if the objects are consistent, so that they can be safely stored. */
    /**
     * @param evt the update event to check
     * @param store the store to lookup the references
     * @throws RaplaException if an inconsistence is found
     */
    protected void checkConsistency(UpdateEvent evt, EntityStore store) throws RaplaException
    {
        Collection<EntityReferencer> entityReferences = evt.getEntityReferences();
        for (EntityReferencer referencer : entityReferences)
        {
            for (ReferenceInfo referenceInfo : referencer.getReferenceInfo())
            {
                Class type = referenceInfo.getType();
                if (type == null || ExternalSyncEntity.class.isAssignableFrom( type ) || Preferences.class.isAssignableFrom( type ) || Conflict.class.isAssignableFrom( type) || Reservation.class.isAssignableFrom(type) || Appointment.class.isAssignableFrom(type))
                {
                    throw new RaplaException("The current version of Rapla doesn't allow references to objects of type " + type + " id " + referenceInfo.getId());
                }
            }
        }

        for (Entity entity : evt.getStoreObjects())
        {
            checkConsitency(entity, store);
        }

    }

    protected void checkConsitency(Entity entity, EntityResolver store) throws RaplaException
    {
        Class<? extends Entity> raplaType = entity.getTypeClass();
        final RaplaResources i18n = getI18n();
        if (Category.class == raplaType)
        {
            Category category = (Category) entity;
            DynamicTypeImpl.checkKey(i18n, category.getKey());
            if (entity.getReference().equals(Category.SUPER_CATEGORY_REF))
            {
                // Check if the user group is missing
                Category userGroups = category.getCategory(Permission.GROUP_CATEGORY_KEY);
                if (userGroups == null)
                {
                    throw new RaplaException("The category with the key '" + Permission.GROUP_CATEGORY_KEY + "' is missing.");
                }
            }
            else
            {
                // check if the category to be stored has a parent
                Category parent = category.getParent();
                if (parent == null)
                {
                    throw new RaplaException("The category " + category + " needs a parent.");
                }
                else
                {
                    int i = 0;
                    while (true)
                    {
                        if (parent == null)
                        {
                            throw new RaplaException("Category needs to be a child of super category.");
                        }
                        else if (parent.getReference().equals(Category.SUPER_CATEGORY_REF))
                        {
                            break;
                        }
                        parent = parent.getParent();
                        i++;
                        if (i > 80)
                        {
                            throw new RaplaException("infinite recursion detection for category " + category);
                        }
                    }
                }
            }
        }
        else if (Reservation.class == raplaType)
        {
            Reservation reservation = (Reservation) entity;
            ReservationImpl.checkReservation(i18n, reservation, store);
        }
        else if (Conflict.class == raplaType)
        {
            Conflict conflict = (Conflict) entity;
            final ReferenceInfo<Appointment> appointment1 = conflict.getAppointment1();
            store.resolve(appointment1);
            final ReferenceInfo<Appointment> appointment2 = conflict.getAppointment2();
            store.resolve(appointment2);
        }
        else if (DynamicType.class == raplaType)
        {
            DynamicType type = (DynamicType) entity;
            DynamicTypeImpl.validate(type, this.i18n);
        }
        else if (Allocatable.class == raplaType)
        {
            final Classification classification = ((Allocatable) entity).getClassification();
            if (classification.getType().getKey().equals(PERIOD_TYPE))
            {
                String keyName = null;
                if (classification.getValue("start") == null)
                {
                    keyName = getString("start_date");
                }
                else if (classification.getValue("end") == null)
                {
                    keyName = getString("end_date");
                }
                else if (classification.getValue("name") == null || classification.getValue("name").toString().trim().isEmpty())
                {
                    keyName = getString("name");
                }
                if (keyName != null)
                {
                    throw new RaplaException(i18n.format("error.no_entry_for", keyName));
                }
            }
        }
        checkBelongsTo(entity, entity,0);
        checkPackages(entity, entity,0);
    }

    private void checkPackages(final Object originalEntity,final Object currentEntity, final int depth) throws RaplaException
    {
        if (depth > 20)
        {
            final String name = getName(currentEntity);
            final String format = i18n.format("error.packageCycle", name);
            throw new RaplaException(format);
        }
        if (currentEntity instanceof Classifiable)
        {
            final Classifiable classifiable = (Classifiable) currentEntity;
            final Classification classification = classifiable.getClassification();
            if (classification != null)
            {
                final Attribute[] attributes = classification.getAttributes();
                for (Attribute att : attributes)
                {
                    final Boolean packages = (Boolean) att.getConstraint(ConstraintIds.KEY_PACKAGE);
                    if (packages != null && packages)
                    {
                        final Collection<Object> targets = classification.getValues(att);
                        if (targets != null)
                        {
                            for (Object target : targets)
                            {
                                if (target.equals(originalEntity))
                                {
                                    final String name = getName(originalEntity);
                                    final String format = getI18n().format("error.packageCantReferToSelf", name);
                                    throw new RaplaException(format);
                                }
                                else
                                {
                                    checkPackages(originalEntity,target, depth + 1);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void checkBelongsTo(final Object originalEntity,final Object currentEntity, final int depth) throws RaplaException
    {
        if (depth > 20)
        {
            final String name = getName(originalEntity);
            final String format = i18n.format("error.belongsToCycle", name);
            throw new RaplaException(format);
        }

        if (currentEntity instanceof Classifiable)
        {
            final Classifiable classifiable = (Classifiable) currentEntity;
            final Classification classification = classifiable.getClassification();
            if (classification != null)
            {
                final Attribute[] attributes = classification.getAttributes();
                for (Attribute att : attributes)
                {
                    final Boolean belongsTo = (Boolean) att.getConstraint(ConstraintIds.KEY_BELONGS_TO);
                    if (belongsTo != null && belongsTo)
                    {
                        final Object target = classification.getValueForAttribute(att);
                        if (target != null)
                        {
                            if (target.equals(originalEntity))
                            {
                                final String name = getName(originalEntity);
                                final String format = getI18n().format("error.belongsToCantReferToSelf", name);
                                throw new RaplaException(format);
                            }
                            else
                            {
                                checkBelongsTo(originalEntity,target, depth + 1);
                            }
                        }
                    }
                }
            }
        }
    }

    protected Collection<ReferenceInfo> removeInconsistentReservations(EntityStore store)
    {
        List<Reservation> reservations = new ArrayList<>();
        List<ReferenceInfo> reservationRefs = new ArrayList<>();
        Collection<Entity> list = store.getList();
        for (Entity entity : list)
        {
            if (!(entity instanceof Reservation))
            {
                continue;
            }
            Reservation reservation = (Reservation) entity;
            if (reservation.getSortedAppointments().isEmpty())
            {
                reservations.add(reservation);
                for (Appointment app : reservation.getAppointments())
                {
                    reservationRefs.add(app.getReference());
                }
                reservationRefs.add(reservation.getReference());
            }
        }
        for (ReferenceInfo<Reservation> ref : reservationRefs)
        {
            store.remove(ref);
        }

        if (!reservations.isEmpty())
        {
            JsonParserWrapper.JsonParser gson = JsonParserWrapper.defaultJson().get();
            getLogger().error("The following events will be removed because they have no appointments: \n" + gson.toJson(reservations));
        }
        return reservationRefs;
    }

    protected void checkNoDependencies(final UpdateEvent evt, final EntityStore store) throws RaplaException
    {
        Collection<ReferenceInfo> removedIds = evt.getRemoveIds();
        Collection<Entity> storeObjects = new HashSet<>(evt.getStoreObjects());
        HashSet<Entity> dep = new HashSet<>();
        Collection<Entity> removeEntities = new ArrayList<>();
        for (ReferenceInfo id : removedIds)
        {
            Entity persistent = store.tryResolve(id);
            if (persistent != null)
            {
                removeEntities.add(persistent);
            }
        }
        //IterableChain<Entity> iteratorChain = new IterableChain<Entity>(deletedCategories, removeEntities);
        for (Entity entity : removeEntities) {
            // First we add the dependencies from the stored object list
            for (Entity obj : storeObjects) {
                if (obj instanceof EntityReferencer) {
                    EntityReferencer referencer = (EntityReferencer) obj;
                    if (isRefering(referencer, entity)) {
                        if (!isOnlyLastChangedReference(entity.getReference(),(Entity)referencer)) {
                            dep.add(obj);
                        }
                    }
                }
            }
            // we check if the user deletes himself
            if (entity instanceof User) {
                String eventUserId = evt.getUserId();
                if (eventUserId != null && eventUserId.equals(entity.getId())) {
                    List<String> emptyList = Collections.emptyList();
                    throw new DependencyException(i18n.getString("error.deletehimself"), emptyList);
                }
            }
        }
        // Than we add the dependencies from the cache. It is important that
        // we don't add the dependencies from the stored object list here,
        // because a dependency could be removed in a stored object
        Map<ReferenceInfo, Set<Entity>> dependencyMap = getDependencies(removeEntities, store);
        removeLastChangedReference(removeEntities, dependencyMap);
        for (Entity entity : removeEntities) {
            Set<Entity> dependencies = dependencyMap.get(entity.getReference());
            if ( dependencies == null) {
                continue;
            }
            for (Entity dependency : dependencies) {
                if (!storeObjects.contains(dependency) && !removeEntities.contains(dependency)) {
                    // only add the first 21 dependencies;
                    if (dep.size() > MAX_DEPENDENCY) {
                        break;
                    }
                    dep.add(dependency);
                }
            }
        }

        // CKO We skip this check as the admin should have the possibility to deny a user read to allocatables objects even if he has reserved it prior
        //		for (Entity entity : storeObjects) {
        //			if ( entity.getRaplaType() == Allocatable.TYPE)
        //			{
        //				Allocatable alloc = (Allocatable) entity;
        //				for (Entity reference:getDependencies(entity))
        //				{
        //					if ( reference instanceof Ownable)
        //					{
        //						User user = ((Ownable) reference).getOwner();
        //						if (user != null && !alloc.canReadOnlyInformation(user))
        //						{
        //							throw new DependencyException( "User " + user.getUsername() + " refers to " + getNamespace(alloc) + ". Read permission is required.", Collections.singleton( getDependentName(reference)));
        //						}
        //					}
        //				}
        //			}
        //		}

        if (!dep.isEmpty())
        {
            if (!Allocatable.isAllocatablesOnly( removeEntities) || !evt.isForceAllocatableDeletesIgnoreDependencies()) {
                Collection<String> names = new ArrayList<>();
                for (Entity obj : dep)
                {
                    String string = getDependentName(obj);
                    names.add(string);
                }
                if (!names.isEmpty()) {
                    throw new DependencyException(getString("error.dependencies"), names.toArray(new String[]{}));
                }
            }
        }
        // Count dynamic-types to ensure that there is least one dynamic type
        // for reservations and one for resources or persons
        checkDynamicType(removeEntities, Collections.singleton(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION));
        checkDynamicType(removeEntities, new HashSet<>(Arrays.asList(
                DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE, DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON)));
    }

    private boolean isRefering(EntityReferencer referencer, Entity entity)
    {
        for (ReferenceInfo info : referencer.getReferenceInfo())
        {
            if (info.isReferenceOf(entity))
            {
                return true;
            }
        }
        return false;
    }

    protected String getDependentName(Entity obj)
    {
        Locale locale = raplaLocale.getLocale();
        StringBuilder buf = new StringBuilder();
        if (obj instanceof Reservation)
        {
            buf.append(getString("reservation"));
        }
        else if (obj instanceof Preferences)
        {
            buf.append(getString("preferences"));
        }
        else if (obj instanceof Category)
        {
            buf.append(getString("category"));
        }
        else if (obj instanceof Allocatable)
        {
            buf.append(getString("resources_persons"));
        }
        else if (obj instanceof User)
        {
            buf.append(getString("user"));
        }
        else if (obj instanceof DynamicType)
        {
            buf.append(getString("dynamictype"));
        }
        if (obj instanceof Named)
        {
            final String string = ((Named) obj).getName(locale);
            buf.append(": " + string);
        }
        else
        {
            buf.append(obj.toString());
        }
        if (obj instanceof Reservation)
        {
            Reservation reservation = (Reservation) obj;

            Appointment[] appointments = reservation.getAppointments();
            if (appointments.length > 0)
            {
                buf.append(" ");
                Date start = appointments[0].getStart();
                buf.append(raplaLocale.formatDate(start));
            }

            String template = reservation.getAnnotation(RaplaObjectAnnotations.KEY_TEMPLATE);
            if (template != null)
            {
                Allocatable templateObj = tryResolve(template, Allocatable.class);
                if (templateObj != null)
                {
                    String name = templateObj.getName(locale);
                    buf.append(" in template " + name);
                    ReferenceInfo<User> ownerId = templateObj.getOwnerRef();
                    if (ownerId != null)
                    {
                        User user = tryResolve(ownerId);
                        if (user != null)
                        {
                            buf.append(" of user " + user.getUsername());
                        }
                        else
                        {
                            buf.append(" with userId " + ownerId);
                        }
                    }
                }
                else
                {
                    buf.append(" in template " + template);
                }
            }
        }
        final Object idFull = obj.getId();
        if (idFull != null)
        {
            String idShort = idFull.toString();
            int dot = idShort.lastIndexOf('.');
            buf.append(" (" + idShort.substring(dot + 1) + ")");
        }
        return buf.toString();
    }

    private void storeUser(User refUser) throws RaplaException
    {
        ArrayList<Entity> storeObjects = new ArrayList<>();
        storeObjects.add(refUser);
        Collection<ReferenceInfo<Entity>> removeObjects = Collections.emptySet();
        storeAndRemove(storeObjects, removeObjects, null);
    }

    public static String encrypt(String encryption, String password) throws RaplaException
    {
        MessageDigest md;
        try
        {
            md = MessageDigest.getInstance(encryption);
        }
        catch (NoSuchAlgorithmException ex)
        {
            throw new RaplaException(ex);
        }
        synchronized (md)
        {
            md.reset();
            md.update(password.getBytes());
            return encryption + ":" + Tools.convert(md.digest());
        }
    }

    private boolean checkPassword(ReferenceInfo<User> userId, String password) throws RaplaException
    {
        if (userId == null)
            return false;

        String correct_pw = cache.getPassword(userId);
        if (correct_pw == null)
        {
            return false;
        }

        if (correct_pw.equals(password))
        {
            return true;
        }

        int columIndex = correct_pw.indexOf(":");
        if (columIndex > 0 && correct_pw.length() > 20)
        {
            String encryptionGuess = correct_pw.substring(0, columIndex);
            if (encryptionGuess.contains("sha") || encryptionGuess.contains("md5"))
            {
                password = encrypt(encryptionGuess, password);
                return correct_pw.equals(password);
            }
        }
        return false;
    }

    @Override
    public Promise<Map<ReferenceInfo<Allocatable>, Collection<Appointment>>> getFirstAllocatableBindings(Collection<Allocatable> allocatables,
            Collection<Appointment> appointments, Collection<Reservation> ignoreList)
    {
        final Promise<Map<ReferenceInfo<Allocatable>, Collection<Appointment>>> prom = scheduler
                .supply(() -> getFirstAllocatableBindingsMap(allocatables, appointments, ignoreList));
        return prom;
    }

    private Map<ReferenceInfo<Allocatable>, Collection<Appointment>> getFirstAllocatableBindingsMap(Collection<Allocatable> allocatables, Collection<Appointment> appointments,
            Collection<Reservation> ignoreList)
    {
        Map<ReferenceInfo<Allocatable>, Map<Appointment, Collection<Appointment>>> allocatableBindings = getAllocatableBindings(allocatables, appointments, ignoreList, true);
        Map<ReferenceInfo<Allocatable>, Collection<Appointment>> map = new HashMap<>();
        for (Map.Entry<ReferenceInfo<Allocatable>, Map<Appointment, Collection<Appointment>>> entry : allocatableBindings.entrySet())
        {
            ReferenceInfo<Allocatable> alloc = entry.getKey();
            Collection<Appointment> list = entry.getValue().keySet();
            map.put(alloc, list);
        }
        return map;
    }

    @Override
    public Promise<Map<ReferenceInfo<Allocatable>, Map<Appointment, Collection<Appointment>>>> getAllAllocatableBindings(Collection<Allocatable> allocatables,
            Collection<Appointment> appointments, Collection<Reservation> ignoreList)
    {
        return scheduler.supply(() ->
        {
            Map<ReferenceInfo<Allocatable>, Map<Appointment, Collection<Appointment>>> allocatableBindings = getAllocatableBindings(allocatables, appointments, ignoreList,
                    false);
            return allocatableBindings;
        });
    }

    public Map<ReferenceInfo<Allocatable>, Map<Appointment, Collection<Appointment>>> getAllocatableBindings(Collection<Allocatable> allocatables,
            Collection<Appointment> appointments, Collection<Reservation> ignoreList, boolean onlyFirstConflictingAppointment)
    {
        Map<ReferenceInfo<Allocatable>, Map<Appointment, Collection<Appointment>>> map = new HashMap<>();
        for (Allocatable allocatable : allocatables)
        {
            {
                String annotation = allocatable.getAnnotation(ResourceAnnotations.KEY_CONFLICT_CREATION);
                boolean holdBackConflicts = annotation != null && annotation.equals(ResourceAnnotations.VALUE_CONFLICT_CREATION_IGNORE);
                if (holdBackConflicts)
                {
                    continue;
                }
                // TODO check also parents and children from allocatables
                SortedSet<Appointment> appointmentSet = getAppointments(allocatable);
                if (appointmentSet == null)
                {
                    continue;
                }
                map.put(allocatable.getReference(), new HashMap<>());
                for (Appointment appointment : appointments)
                {
                    Set<Appointment> conflictingAppointments = AppointmentImpl
                            .getConflictingAppointments(appointmentSet, appointment, ignoreList, onlyFirstConflictingAppointment);
                    if (conflictingAppointments.size() > 0)
                    {
                        Map<Appointment, Collection<Appointment>> appMap = map.get(allocatable);
                        if (appMap == null)
                        {
                            appMap = new HashMap<>();
                            map.put(allocatable.getReference(), appMap);
                        }
                        appMap.put(appointment, conflictingAppointments);
                    }
                }
            }
        }
        return map;
    }

    @Override
    public Promise<Date> getNextAllocatableDate(final Collection<Allocatable> allocatables, final Appointment appointment,
            final Collection<Reservation> ignoreList, final Integer worktimeStartMinutes, final Integer worktimeEndMinutes, final Integer[] excludedDays,
            final Integer rowsPerHour)
    {
        Promise<Date> promise = scheduler.supply(() ->
        {
            Appointment newState = appointment;
            Date firstStart = appointment.getStart();
            boolean startDateExcluded = isExcluded(excludedDays, firstStart);
            boolean wholeDay = appointment.isWholeDaysSet();
            boolean inWorktime = inWorktime(appointment, worktimeStartMinutes, worktimeEndMinutes);
            final int rowsPerHourInt = (rowsPerHour == null || rowsPerHour <= 1) ? 1 : rowsPerHour;
            for (int i = 0; i < 366 * 24 * rowsPerHourInt; i++)
            {
                newState = ((AppointmentImpl) newState).clone();
                Date start = newState.getStart();
                long millisToAdd = wholeDay ? DateTools.MILLISECONDS_PER_DAY : (DateTools.MILLISECONDS_PER_HOUR / rowsPerHourInt);
                Date newStart = new Date(start.getTime() + millisToAdd);
                if (!startDateExcluded && isExcluded(excludedDays, newStart))
                {
                    continue;
                }
                newState.moveTo(newStart);
                if (!wholeDay && inWorktime && !inWorktime(newState, worktimeStartMinutes, worktimeEndMinutes))
                {
                    continue;
                }
                if (!isAllocated(allocatables, newState, ignoreList))
                {
                    return newStart;
                }
            }
            return null;
        });
        return promise;
    }

    private boolean inWorktime(Appointment appointment, Integer worktimeStartMinutes, Integer worktimeEndMinutes)
    {
        long start = appointment.getStart().getTime();
        int minuteOfDayStart = DateTools.getMinuteOfDay(start);
        long end = appointment.getEnd().getTime();
        int minuteOfDayEnd = DateTools.getMinuteOfDay(end) + (int) DateTools.countDays(start, end) * 24 * 60;
        boolean inWorktime = (worktimeStartMinutes == null || worktimeStartMinutes <= minuteOfDayStart) && (worktimeEndMinutes == null
                || worktimeEndMinutes >= minuteOfDayEnd);
        return inWorktime;
    }

    private boolean isExcluded(Integer[] excludedDays, Date date)
    {
        Integer weekday = DateTools.getWeekday(date);
        if (excludedDays != null)
        {
            for (Integer day : excludedDays)
            {
                if (day.equals(weekday))
                {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isAllocated(Collection<Allocatable> allocatables, Appointment appointment, Collection<Reservation> ignoreList) throws Exception
    {
        Map<ReferenceInfo<Allocatable>, Collection<Appointment>> firstAllocatableBindings = getFirstAllocatableBindingsMap(allocatables, Collections.singleton(appointment),
                ignoreList);
        for (Map.Entry<ReferenceInfo<Allocatable>, Collection<Appointment>> entry : firstAllocatableBindings.entrySet())
        {
            if (entry.getValue().size() > 0)
            {
                return true;
            }
        }
        return false;
    }

    public Collection<Entity> getVisibleEntities(final User user) throws RaplaException
    {
        checkLoaded();
        return cache.getVisibleEntities(user);
    }

    @SuppressWarnings("deprecation")
    private void addDefaultEventPermissions(DynamicTypeImpl dynamicType, Category userGroups)
    {
        {
            Permission permission = dynamicType.newPermission();
            permission.setAccessLevel(Permission.READ_TYPE);
            dynamicType.addPermission(permission);
        }
        Category canReadEventsFromOthers = userGroups.getCategory(Permission.GROUP_CAN_READ_EVENTS_FROM_OTHERS);
        if (canReadEventsFromOthers != null)
        {
            Permission permission = dynamicType.newPermission();
            permission.setAccessLevel(Permission.READ);
            permission.setGroup(canReadEventsFromOthers);
            dynamicType.addPermission(permission);
        }
        Category canCreate = userGroups.getCategory(Permission.GROUP_CAN_CREATE_EVENTS);
        if (canCreate != null)
        {
            Permission permission = dynamicType.newPermission();
            permission.setAccessLevel(Permission.CREATE);
            permission.setGroup(canCreate);
            dynamicType.addPermission(permission);
        }
    }

    protected void createDefaultSystem(EntityStore store) throws RaplaException
    {
        Date now = getCurrentTimestamp();

        PreferencesImpl newPref = new PreferencesImpl(now, now);
        newPref.setId(PreferencesImpl.getPreferenceIdFromUser(null).getId());
        newPref.setResolver(store);
        newPref.setReadOnly();
        store.put(newPref);

        @SuppressWarnings("deprecation")
        String[] userGroups = new String[] { Permission.GROUP_CAN_READ_EVENTS_FROM_OTHERS, Permission.GROUP_CAN_CREATE_EVENTS, ExchangeConnectorPlugin.EXCHANGE_SYNCHRONIZATION_GROUP};

        CategoryImpl groupsCategory = new CategoryImpl(now, now);
        groupsCategory.setKey("user-groups");
        groupsCategory.setResolver(store);
        setName(groupsCategory.getName(), groupsCategory.getKey());
        setNew(groupsCategory);
        store.put(groupsCategory);

        CategoryImpl periodsCategory = new CategoryImpl(now, now);
        periodsCategory.setKey("periods");
        periodsCategory.setResolver(store);
        setName(periodsCategory.getName(), periodsCategory.getKey());
        setNew(periodsCategory);
        store.put(periodsCategory);

        CategoryImpl holidaysCategory = new CategoryImpl(now, now);

        holidaysCategory.setKey("holiday");
        holidaysCategory.setResolver(store);
        setName(holidaysCategory.getName(), holidaysCategory.getKey());
        setNew(holidaysCategory);
        store.put(holidaysCategory);
        periodsCategory.addCategory(holidaysCategory);

        for (String catName : userGroups)
        {
            CategoryImpl group = new CategoryImpl(now, now);
            group.setKey(catName);
            setNew(group);
            setName(group.getName(), group.getKey());
            groupsCategory.addCategory(group);
            group.setResolver(store);
            store.put(group);
        }
        final Category superCategory = store.resolve(Category.SUPER_CATEGORY_REF);
        superCategory.addCategory(groupsCategory);
        superCategory.addCategory(periodsCategory);

        DynamicTypeImpl resourceType = newDynamicType(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE, "resource", groupsCategory, store);
        setName(resourceType.getName(), "resource");
        addDependencies(store, resourceType);
        Assert.isTrue(
                DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE.equals(resourceType.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE)));

        DynamicTypeImpl personType = newDynamicType(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON, "person", groupsCategory, store);
        setName(personType.getName(), "person");
        addDependencies(store, personType);

        DynamicTypeImpl eventType = newDynamicType(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION, "event", groupsCategory, store);
        setName(eventType.getName(), "event");
        addDependencies(store, eventType);

        UserImpl admin = new UserImpl(now, now);
        admin.setUsername("admin");
        admin.setAdmin(true);
        setNew(admin);
        store.put(admin);

        Collection<Entity> list = store.getList();
        for (Entity entity : list)
        {
            if (entity instanceof EntityReferencer)
            {
                ((EntityReferencer) entity).setResolver(store);
            }
        }

        String password = "";
        store.putPassword(admin.getReference(), password);
        ((CategoryImpl) superCategory).setReadOnly();

        AllocatableImpl allocatable = new AllocatableImpl(now, now);
        allocatable.setResolver(store);
        Classification classification = resourceType.newClassificationWithoutCheck(true);

        allocatable.setClassification(classification);
        PermissionContainer.Util.copyPermissions(resourceType, allocatable);
        setNew(allocatable);
        classification.setValue("name", getString("test_resource"));
        allocatable.setOwner(admin);

        store.put(allocatable);

    }

    private void addDependencies(EntityStore list, DynamicTypeImpl type)
    {
        list.put(type);
        for (Attribute att : type.getAttributes())
        {
            list.put(att);
        }
    }

    private Attribute createStringAttribute(String key, String name) throws RaplaException
    {
        Attribute attribute = newAttribute(AttributeType.STRING, null);
        attribute.setKey(key);
        setName(attribute.getName(), name);
        return attribute;
    }

    private Attribute addAttributeWithInternalId(DynamicType dynamicType, String key, AttributeType type) throws RaplaException
    {
        String id = "rapla_" + dynamicType.getKey() + "_" + key;
        Attribute attribute = newAttribute(type, id);
        attribute.setKey(key);
        setName(attribute.getName(), key);
        dynamicType.addAttribute(attribute);
        return attribute;
    }

    private DynamicTypeImpl newDynamicType(String classificationType, String key, Category userGroups, EntityResolver resolver) throws RaplaException
    {
        DynamicTypeImpl dynamicType = new DynamicTypeImpl();
        dynamicType.setResolver(resolver);
        dynamicType.setAnnotation("classification-type", classificationType);
        dynamicType.setKey(key);
        setNew(dynamicType);
        if (classificationType.equals(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE))
        {
            dynamicType.addAttribute(createStringAttribute("name", "name"));
            dynamicType.setAnnotation(DynamicTypeAnnotations.KEY_NAME_FORMAT, "{name}");
            dynamicType.setAnnotation(DynamicTypeAnnotations.KEY_COLORS, "automatic");
            addDefaultResourcePermissions(dynamicType);
        }
        else if (classificationType.equals(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION))
        {
            dynamicType.addAttribute(createStringAttribute("name", "eventname"));
            dynamicType.setAnnotation(DynamicTypeAnnotations.KEY_NAME_FORMAT, "{name}");
            dynamicType.setAnnotation(DynamicTypeAnnotations.KEY_COLORS, null);
            addDefaultEventPermissions(dynamicType, userGroups);
        }
        else if (classificationType.equals(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON))
        {
            dynamicType.addAttribute(createStringAttribute("surname", "surname"));
            dynamicType.addAttribute(createStringAttribute("firstname", "firstname"));
            dynamicType.addAttribute(createStringAttribute("email", "email"));
            dynamicType.setAnnotation(DynamicTypeAnnotations.KEY_NAME_FORMAT, "{surname} {firstname}");
            dynamicType.setAnnotation(DynamicTypeAnnotations.KEY_COLORS, null);
            addDefaultResourcePermissions(dynamicType);
        }
        return dynamicType;
    }

    private void addDefaultResourcePermissions(DynamicTypeImpl dynamicType)
    {
        {
            Permission permission = dynamicType.newPermission();
            permission.setAccessLevel(Permission.READ_TYPE);
            dynamicType.addPermission(permission);
        }
        {
            Permission permission = dynamicType.newPermission();
            permission.setAccessLevel(Permission.ALLOCATE_CONFLICTS);
            dynamicType.addPermission(permission);
        }
    }

    private Attribute newAttribute(AttributeType attributeType, String id) throws RaplaException
    {
        AttributeImpl attribute = new AttributeImpl(attributeType);
        if (id == null)
        {
            setNew(attribute);
        }
        else
        {
            attribute.setId(id);
        }
        attribute.setResolver(this);
        return attribute;
    }

    private <T extends Entity> void setNew(T entity) throws RaplaException
    {
        Class<T> raplaType = entity.getTypeClass();
        ReferenceInfo<T> id = createIdentifier(raplaType, 1).get(0);
        ((RefEntity) entity).setId(id.getId());
    }

    private void setName(MultiLanguageName name, String to)
    {
        String currentLang = i18n.getLang();
        name.setName("en", to);
        try
        {
            String translation = i18n.getString(to);
            name.setName(currentLang, translation);
        }
        catch (Exception ex)
        {

        }
    }

    public ReferenceInfo tryResolveExternalId(String externalId)
    {
        final ReferenceInfo referenceInfo = externalIds.get(externalId);
        return referenceInfo;
    }

    public UpdateResult getUpdateResult(Date since, User user) throws RaplaException
    {
        checkConnected();
        // date when current history begins. history entries before that date can be deleted, so they should be ingnored here
        final Date historyValidStart = getHistoryValidStart();
        // check if we can use the history to return a result
        if (since == null || since.before(historyValidStart))
        {
            since = historyValidStart;
            // if not we tell the server what would be a valid history
            // 10 minutes
            final Date until = new Date(historyValidStart.getTime() + DateTools.MILLISECONDS_PER_MINUTE * 10);
            return new UpdateResult(since, until, Collections.emptyMap(), Collections.emptyMap());
        }
        Date until = getLastRefreshed();
        final Collection<ReferenceInfo> toUpdate = getEntities(user, since, false);
        Map<ReferenceInfo, Entity> oldEntities = new LinkedHashMap<>();
        Collection<Entity> updatedEntities = new ArrayList<>();
        for (ReferenceInfo update : toUpdate)
        {
            Entity oldEntity;
            Entity newEntity;
            final Class<? extends Entity> type = update.getType();
            if (type == Conflict.class)
            {
                final Conflict conflict = conflictFinder.findConflict((ReferenceInfo<Conflict>) update);
                if (conflict != null)
                {
                    newEntity = cache.fillConflictDisableInformation(user, conflict);
                    // can be null if no conflict disable information is stored
                    if (history.hasHistory(update))
                    {
                        oldEntity = history.get(update, since);
                    }
                    else
                    {
                        oldEntity = null;
                    }
                } else {
                    // conflict may be deleted
                    newEntity = null;
                    oldEntity = null;
                }

            }
            else if (type == Preferences.class)
            {
                newEntity = tryResolve(update);
                oldEntity = newEntity;
            }
            else
            {
                oldEntity = history.get(update, since);
                newEntity = tryResolve(update);
            }
            // if newEntity is null, then it must be deleted and within the to removed entities
            if (newEntity != null)
            {
                updatedEntities.add(newEntity);
                if (oldEntity != null)
                {
                    oldEntities.put(update, oldEntity);
                }
            }
        }
        Collection<ReferenceInfo> toRemove = getEntities(user, since, true);
        for (ReferenceInfo update : toRemove) {
            Entity entity;
            if (update.getType() == Conflict.class) {
                entity = null;
            } else {
                entity = history.get(update, since);
            }
            Entity oldEntity = null;
            if (entity != null) {
                oldEntity = entity;
            } else if (update.getType() != Conflict.class) {
                final EntityHistory.HistoryEntry latest = history.getLatest(update);
                if (latest != null) {
                    oldEntity = history.getEntity(latest);
                } else {
                    getLogger().warn("the entity " + update + " was deleted but not found in the history.");
                }
            }
            if (oldEntity != null) {
                oldEntities.put(update, oldEntity);
            }
        }
        UpdateResult updateResult = createUpdateResult(oldEntities, updatedEntities, toRemove, since, until);
        return updateResult;
    }

    @Override
    public UpdateResult getUpdateResult(Date since) throws RaplaException
    {
        return getUpdateResult(since, null);
    }


    /*
     * Dependencies for belongsTo and package
     */
    protected void merge(Allocatable selectedObject, Set<ReferenceInfo<Allocatable>> allocatableIds, User user) throws RaplaException
    {
        final RaplaLock.WriteLock writeLock = writeLockIfLoaded("merging " + allocatableIds.size() + " allocatables into " + selectedObject.getId()  );
        try
        {
            final ReferenceInfo<Allocatable> newRef = selectedObject.getReference();
            selectedObject.getReference();
            // FIXME check write permissions
            Set<Allocatable> allocatables = new LinkedHashSet<>();
            for (ReferenceInfo<Allocatable> allocatableId : allocatableIds)
            {
                // Ignore the allocatable we want to merge into
                if ( newRef.equals( allocatableId))
                {
                    continue;
                }
                final Allocatable resolve = resolve(allocatableId);
                allocatables.add(resolve);
            }
            final Collection<Entity> storeObjects = new LinkedHashSet<>();
            if (!selectedObject.isReadOnly())
            {
                storeObjects.add(selectedObject);
            }
            {// now change the references
                Set<ReferenceInfo>  allocatableReferences = allocatables.stream().map(Allocatable::getReference).collect(Collectors.toSet());
                Map<ReferenceInfo, Set<Entity>> referencingEntityMap = getReferencingEntities(allocatableReferences, new EntityStore(this));
                for (Allocatable allocatable : allocatables)
                {
                    final Set<Entity> referencingEntities = referencingEntityMap.get( allocatable.getReference());
                    if ( referencingEntities == null) {
                        continue;
                    }
                    for (Entity entity : referencingEntities)
                    {
                        final Entity editObject = editObject(entity, user);
                        final ReferenceInfo<Allocatable> oldRef = allocatable.getReference();

                        ((EntityReferencer) editObject).replace(oldRef, newRef);
                        storeObjects.add(editObject);
                    }
                }
            }
            storeAndRemove(storeObjects, allocatableIds, user);
        }
        catch (RaplaException ra)
        {
            getLogger().error("Error doing a merge for " + selectedObject + " and allocatables " + allocatableIds + ": " + ra.getLocalizedMessage());
            throw ra;
        }
        catch (Exception e)
        {
            getLogger().error("Error doing a merge for " + selectedObject + " and allocatables " + allocatableIds + ": " + e.getMessage());
            throw new RaplaException(e);
        }
        finally
        {
            lockManager.unlock(writeLock);
        }
    }
    @Override
    public Promise<Allocatable> doMerge(Allocatable selectedObject, Set<ReferenceInfo<Allocatable>> allocatableIds, User user) {
        try {
            merge(selectedObject,allocatableIds,user);
            return new ResolvedPromise<>(resolve( selectedObject.getReference()));
        } catch (RaplaException e) {
            return new ResolvedPromise<>(e);
        }
    }
}