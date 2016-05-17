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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.SortedBidiMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.apache.commons.collections4.bidimap.DualTreeBidiMap;
import org.rapla.RaplaResources;
import org.rapla.components.util.Assert;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.SerializableDateTimeFormat;
import org.rapla.components.util.TimeInterval;
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
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentStartComparator;
import org.rapla.entities.domain.EntityPermissionContainer;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.Permission.AccessLevel;
import org.rapla.entities.domain.PermissionContainer;
import org.rapla.entities.domain.PermissionContainer.Util;
import org.rapla.entities.domain.RaplaObjectAnnotations;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.ResourceAnnotations;
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
import org.rapla.entities.storage.RefEntity;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.entities.storage.internal.SimpleEntity;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.Conflict;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.Disposable;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.rest.JsonParserWrapper;
import org.rapla.scheduler.Cancelable;
import org.rapla.scheduler.Command;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.scheduler.Promise;
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
import org.rapla.storage.impl.EntityStore;

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

    @Override final public boolean isConnected()
    {
        return connectStatus == InitStatus.Connected;
    }

    @Override public boolean isLoaded()
    {
        return connectStatus.ordinal() >= InitStatus.Loaded.ordinal();
    }

    protected void changeStatus(InitStatus status)
    {
        connectStatus = status;
        getLogger().debug("Initstatus " + status);
    }

    /**
     * The duration which the history must support, only one older Entry than the specified time are needed.
     */
    public static final long HISTORY_DURATION = DateTools.MILLISECONDS_PER_HOUR;

    /**
     * set encryption if you want to enable password encryption. Possible values
     * are "sha" or "md5".
     */
    private String encryption = "sha-1";
    private ConflictFinder conflictFinder;
    //private SortedSet<LastChangedTimestamp> timestampSet;
    // we need a bidi to sort the values instead of the keys
    protected final EntityHistory history;
    private SortedBidiMap<String, DeleteUpdateEntry> deleteUpdateSet;

    private TimeZone systemTimeZone = TimeZone.getDefault();
    private CommandScheduler scheduler;
    private List<Cancelable> scheduledTasks = new ArrayList<Cancelable>();
    private CalendarModelCache calendarModelCache;
    private Date connectStart;

    public LocalAbstractCachableOperator(Logger logger, RaplaResources i18n, RaplaLocale raplaLocale, CommandScheduler scheduler,
            Map<String, FunctionFactory> functionFactoryMap, Set<PermissionExtension> permissionExtensions)
    {
        super(logger, i18n, raplaLocale, functionFactoryMap, permissionExtensions);
        this.scheduler = scheduler;
        //context.lookupDeprecated( CommandScheduler.class);
        this.history = new EntityHistory();
        appointmentBindings = new AppointmentMapClass(logger);
        calendarModelCache = new CalendarModelCache(this, i18n, logger);
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
            type.setId(key);
            type.setAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE, DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RAPLATYPE);
            addAttributeWithInternalId(type, "name", AttributeType.STRING);
            addAttributeWithInternalId(type, "start", AttributeType.DATE);
            addAttributeWithInternalId(type, "end", AttributeType.DATE);
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
            type.setAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE, DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RAPLATYPE);
            addAttributeWithInternalId(type, "name", AttributeType.STRING);
            {
                Attribute att = addAttributeWithInternalId(type, "fixedtimeandduration", AttributeType.BOOLEAN);
                att.setDefaultValue(Boolean.TRUE);
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

    @Override public String getUsername(ReferenceInfo<User> userId)
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

    @Override public Date getConnectStart()
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
        Lock readLock = readLock();
        try
        {
            cmd.execute(cache);
        }
        finally
        {
            unlock(readLock);
        }
    }

    /**
     * @param user the owner of the reservation or null for reservations from all users
     */
    public Promise<Map<Allocatable, Collection<Appointment>>> queryAppointments(final User user, final Collection<Allocatable> allocatables,
            final Date start, final Date end, final ClassificationFilter[] filters, final Map<String, String> annotationQuery)
    {


        final Promise<Map<Allocatable, Collection<Appointment>>> promise = scheduler.supply(() -> {
            boolean excludeExceptions = false;
            final HashSet<Reservation> reservationSet = new HashSet<Reservation>();
            final Collection<Allocatable> allocs = (allocatables == null || allocatables.size() == 0) ? getAllocatables( null): allocatables;
            Map<Allocatable, Collection<Appointment>> result = new LinkedHashMap<Allocatable, Collection<Appointment>>();
            boolean isResourceTemplate = allocs.size() == 1 && (allocs.iterator().next().getClassification().getType().getKey().equals(RAPLA_TEMPLATE));
            for (Allocatable allocatable : allocs)
            {
                Lock readLock = readLock();
                SortedSet<Appointment> appointments;
                try
                {
                    appointments = getAppointments(allocatable);
                }
                finally
                {
                    unlock(readLock);
                }
                SortedSet<Appointment> appointmentSet = AppointmentImpl.getAppointments(appointments, user, start, end, excludeExceptions);
                for (Appointment appointment : appointmentSet)
                {
                    Reservation reservation = appointment.getReservation();
                    if (!match(reservation, annotationQuery))
                    {
                        continue;
                    }
                    // Ignore Templates if not explicitly requested
                    else if (RaplaComponent.isTemplate(reservation) && !isResourceTemplate)
                    {
                        // FIXME this special case should be refactored, so one can get all reservations in one method

                        continue;
                    }
                    if (filters != null && !ClassificationFilter.Util.matches(filters, reservation))
                    {
                        continue;
                    }
                    if (!reservationSet.contains(reservation))
                    {
                        reservationSet.add(reservation);
                    }
                    Collection<Appointment> appointmentCollection = result.get(allocatable);
                    if (appointmentCollection == null)
                    {
                        appointmentCollection = new LinkedHashSet<>();
                        result.put(allocatable, appointmentCollection);
                    }
                    appointmentCollection.add(appointment);
                }
            }
            return result;
        });
        return promise;
    }
    CompletionStage<Map<String,Integer>> filter(Map<String,Integer> map)
    {
        return null;
    }

    Void VOID = null;

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

    @Override public String createId(Class<? extends Entity> raplaType) throws RaplaException
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

    @Override public String createId(Class<? extends Entity> raplaType, String seed) throws RaplaException
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

    public <T extends Entity> ReferenceInfo<T>[] createIdentifier(Class<T> raplaType, int count) throws RaplaException
    {
        ReferenceInfo<T>[] ids = new ReferenceInfo[count];
        for (int i = 0; i < count; i++)
        {
            final String id = createId(raplaType);
            ids[i] = new ReferenceInfo<T>(id, raplaType);
        }
        return ids;
    }

    public Date today()
    {
        long time = getCurrentTimestamp().getTime();
        long offset = TimeZoneConverterImpl.getOffset(DateTools.getTimeZone(), systemTimeZone, time);
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
        Lock readLock = readLock();
        try
        {
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
        finally
        {
            unlock(readLock);
        }
    }

    public boolean canChangePassword() throws RaplaException
    {
        return true;
    }

    public void changePassword(User user, char[] oldPassword, char[] newPassword) throws RaplaException
    {
        getLogger().info("Change password for User " + user.getUsername());
        ReferenceInfo<User> userId = user.getReference();
        String password = new String(newPassword);
        if (encryption != null)
            password = encrypt(encryption, password);
        Lock writeLock = writeLock();
        try
        {
            cache.putPassword(userId, password);
        }
        finally
        {
            unlock(writeLock);
        }
        User editObject = editObject(user, null);
        List<Entity> editList = new ArrayList<>(1);
        editList.add(editObject);
        Collection<ReferenceInfo<Entity>> removeList = Collections.emptyList();
        // synchronization will be done in the dispatch method
        storeAndRemove(editList, removeList, user);
    }

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
                    classification.setValue(attribute, title);
                }
            }
            {
                Attribute attribute = classification.getAttribute("firstname");
                if (attribute != null)
                {
                    classification.setValue(attribute, firstname);
                }
            }
            {
                Attribute attribute = classification.getAttribute("surname");
                if (attribute != null)
                {
                    classification.setValue(attribute, surname);
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
        Map<String, Collection<Reservation>> templateMap = new HashMap<String, Collection<Reservation>>();
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
                collection = new ArrayList<Reservation>();
                templateMap.put(annotation, collection);
            }
            collection.add(r);
        }
        if (templateMap.size() == 0)
        {
            return Collections.emptyList();
        }
        getLogger().warn("Found old templates. Migrating.");

        Collection<Entity> toStore = new HashSet<Entity>();
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
        Map<String, Allocatable> resolvingMap = new HashMap<String, Allocatable>();
        for (Entity entity : entities)
        {
            if (entity instanceof Allocatable)
            {
                Allocatable allocatable = (Allocatable) entity;
                final Classification classification = allocatable.getClassification();
                final Attribute attribute = classification.getAttribute("email");
                if (attribute != null)
                {
                    final String email = (String) classification.getValue(attribute);
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
    public Collection<Conflict> getConflicts(User user) throws RaplaException
    {
        checkConnected();
        Lock readLock = readLock();
        try
        {
            Collection<Conflict> conflictList = new HashSet<Conflict>();
            final Collection<Conflict> conflicts = conflictFinder.getConflicts(user);
            for (Conflict conflict : conflicts)
            {
                // conflict is filled with disable/enable status from cache
                Conflict conflictClone = cache.fillConflictDisableInformation(user, conflict);
                conflictList.add(conflictClone);
            }
            return conflictList;
        }
        finally
        {
            unlock(readLock);
        }
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

    ReentrantReadWriteLock disconnectLock = new ReentrantReadWriteLock();

    final protected void scheduleConnectedTasks(final Command command, long delay, long period)
    {
        //        if (true)
        //            return;
        final Command wrapper = new Command()
        {
            @Override public void execute() throws Exception
            {
                final Lock lock = RaplaComponent.lock(disconnectLock.readLock(), 3);
                try
                {
                    if (isConnected())
                    {
                        command.execute();
                    }
                }
                finally
                {
                    RaplaComponent.unlock(lock);
                }
            }
        };
        final Cancelable schedule = scheduler.schedule(wrapper, delay, period);
        scheduledTasks.add( schedule);
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
        Set<Entity> storeObjects = new HashSet<Entity>(evt.getStoreObjects());
        //Set<Entity> removeObjects = new HashSet<Entity>(evt.getRemoveObjects());
        setResolverAndCheckReferences(evt, store);
        checkConsistency(evt, store);
        checkUnique(evt, store);
        checkNoDependencies(evt, store);
        checkVersions(storeObjects);
    }

    protected void initIndizes() throws RaplaException
    {
        deleteUpdateSet = new DualTreeBidiMap<String, DeleteUpdateEntry>();
        externalIds = new DualHashBidiMap<String, ReferenceInfo>();
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
                    final Object value = classification.getValue(idAtt);
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

            @SuppressWarnings("unchecked") public Collection<Allocatable> getAllocatables()
            {
                return (Collection) cache.getAllocatables();
            }
        };
        // The conflict map
        Logger logger = getLogger();
        conflictFinder = new ConflictFinder(allocationMap, today2, logger, this, permissionController);

        // if a client request changes before the start date return refresh conflict flag
        final long delay = 0;//DateTools.MILLISECONDS_PER_HOUR;
        Command cleanUpConflicts = new Command()
        {
            @Override public void execute() throws Exception
            {
                removeOldConflicts();
                removeOldHistory();
            }
        };

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
        calendarModelCache.initCalendarMap();
        scheduleConnectedTasks(cleanUpConflicts, delay, DateTools.MILLISECONDS_PER_HOUR);
        final int refreshPeriod = 1000 * 3;
        scheduleConnectedTasks(new Command()
        {
            @Override public void execute() throws Exception
            {
                try
                {
                    Lock writeLock = lock.writeLock();
                    // dispatch also does an refresh without lock so we get the new data each time a store is called
                    boolean tryLock = writeLock.tryLock();
                    // dispatch also does an refresh without lock so we get the new data each time a store is called
                    if (tryLock)
                    {
                        try
                        {
                            refreshWithoutLock();
                        }
                        finally
                        {
                            RaplaComponent.unlock(writeLock);
                        }
                    }
                }
                catch (Throwable t)
                {
                    getLogger().info("Could not refresh data");
                }
            }
        }, delay, refreshPeriod);

    }

    @Override public void refresh() throws RaplaException
    {
        final Lock lock = writeLock();
        try
        {
            refreshWithoutLock();
        }
        finally
        {
            RaplaComponent.unlock(lock);
        }
    }

    abstract protected void refreshWithoutLock();

    @Override synchronized public void disconnect() throws RaplaException
    {
        if (!isConnected())
            return;
        Lock writeLock = null;
        try
        {
            writeLock = writeLock();
        }
        catch (Exception ex)
        {
            getLogger().error("Could not get writeLock. Scheduled task is probably running > 10sec. Forcing disconnect." + ex.getMessage(), ex);
            writeLock = null;
        }
        Lock disconnectLock;
        try
        {
            disconnectLock = RaplaComponent.lock(this.disconnectLock.writeLock(), 60);
        }
        catch (Exception ex)
        {
            getLogger().error("Could not get disconnectLock. Scheduled task is probably running > 10sec. Forcing disconnect." + ex.getMessage(), ex);
            disconnectLock = null;
        }
        try
        {
            changeStatus(LocalAbstractCachableOperator.InitStatus.Disconnected);
            cache.clearAll();
            history.clear();
        }
        finally
        {
            RaplaComponent.unlock(writeLock);
        }

        try
        {
            for (Cancelable task : scheduledTasks)
            {
                task.cancel();
            }
        }
        finally
        {
            RaplaComponent.unlock(disconnectLock);
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
        Map<ReferenceInfo<Allocatable>, AllocationChange> toUpdate = new HashMap<ReferenceInfo<Allocatable>, AllocationChange>();
        List<ReferenceInfo<Allocatable>> removedAllocatables = new ArrayList<ReferenceInfo<Allocatable>>();
    }

    /** updates the bindings of the resources and returns a map with all processed allocation changes*/
    private Collection<ConflictFinder.ConflictChangeOperation> updateIndizes(UpdateResult result) throws RaplaException
    {
        calendarModelCache.synchronizeCalendars(result);
        final Collection<UpdateOperation> conflictChanges = new ArrayList<UpdateOperation>();
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
            externalIds.remove(id);
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
        Map<ReferenceInfo<Allocatable>, AllocationChange> toUpdate = bindingResult.toUpdate;
        List<ReferenceInfo<Allocatable>> removedAllocatables = bindingResult.removedAllocatables;
        boolean rebuildAllBindings = false;
        for (Add add : result.getOperations(Add.class))
        {
            ReferenceInfo id = add.getReference();
            if (id.getType() == Reservation.class)
            {
                Reservation newReservation = result.getLastKnown((ReferenceInfo<Reservation>) id);//.getUnresolvedEntity();
                for (Appointment app : newReservation.getAppointments())
                {
                    updateBindings(toUpdate, newReservation, app, false);
                }
            }
        }
        for (Change changes : result.getOperations(Change.class))
        {
            ReferenceInfo id = changes.getReference();
            final Entity lastKnown = result.getLastKnown(id);//.getUnresolvedEntity();
            if (lastKnown instanceof Reservation)
            {
                Reservation oldReservation = (Reservation) result.getLastEntryBeforeUpdate(id);
                Reservation newReservation = (Reservation) lastKnown;
                if (oldReservation == null)
                {
                    rebuildAllBindings = true;
                    getLogger().error("can't find last entry before update for " + id + " rebuilding appointmentbindings index");
                    break;
                }
                for (Appointment oldApp : oldReservation.getAppointments())
                {
                    updateBindings(toUpdate, oldReservation, oldApp, true);
                }
                Appointment[] newAppointments = newReservation.getAppointments();
                for (Appointment newApp : newAppointments)
                {
                    updateBindings(toUpdate, newReservation, newApp, false);
                }
            }
            if (lastKnown instanceof DynamicType)
            {
                DynamicType dynamicType = (DynamicType) lastKnown;
                DynamicType old = (DynamicType) result.getLastEntryBeforeUpdate(id);
                String conflictsNew = dynamicType.getAnnotation(DynamicTypeAnnotations.KEY_CONFLICTS);
                String conflictsOld = old != null ? old.getAnnotation(DynamicTypeAnnotations.KEY_CONFLICTS) : null;
                if (conflictsNew != conflictsOld)
                {
                    if (conflictsNew == null || conflictsOld == null || !conflictsNew.equals(conflictsOld))
                    {
                        Collection<Reservation> reservations = cache.getReservations();
                        for (Reservation reservation : reservations)
                        {
                            if (dynamicType.equals(reservation.getClassification().getType()))
                            {
                                Collection<AppointmentImpl> appointments = ((ReservationImpl) reservation).getAppointmentList();
                                for (Appointment app : appointments)
                                {
                                    updateBindings(toUpdate, reservation, app, true);
                                }
                                for (Appointment app : appointments)
                                {
                                    updateBindings(toUpdate, reservation, app, false);
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!rebuildAllBindings)
        {
            for (Remove removed : result.getOperations(Remove.class))
            {
                final ReferenceInfo reference = removed.getReference();
                final Class<? extends Entity> type = reference.getType();
                if (type == Reservation.class)
                {
                    final Entity lastKnown = result.getLastEntryBeforeUpdate(reference);
                    Reservation old = (Reservation) lastKnown;
                    if (old == null)
                    {
                        rebuildAllBindings = true;
                        getLogger().error("can't find last entry before update for " + reference + " rebuilding appointmentbindings index");
                        break;
                    }
                    for (Appointment app : old.getAppointments())
                    {
                        updateBindings(toUpdate, old, app, true);
                    }
                }
                else if (type == Allocatable.class)
                {
                    removedAllocatables.add(reference);
                }
            }
        }
        if (!rebuildAllBindings)
        {
            appointmentBindings.removeBindings(removedAllocatables);
        }
        if (rebuildAllBindings)
        {
            appointmentBindings.initAppointmentBindings(cache.getReservations());
        }
        appointmentBindings.checkAbandonedAppointments(cache);
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
        final Class<? extends Entity> type = referenceInfo.getType();
        String id = referenceInfo.getId();

        DeleteUpdateEntry entry = deleteUpdateSet.get(id);
        if (entry == null)
        {
            entry = new DeleteUpdateEntry(referenceInfo, timestamp, isDelete);
        }
        else
        {
            DeleteUpdateEntry remove = deleteUpdateSet.remove(id);
            entry.isDelete = isDelete;
            entry.timestamp = timestamp;
            if (isDelete && remove == null)
            {
                getLogger().warn("Can't remove entry for id " + id);
            }
        }
        if (type == User.class && current != null)
        {
            final Collection<String> groupIdList = ((UserImpl) current).getGroupIdList();
            entry.addGroupIds(groupIdList);
        }
        else if (current instanceof EntityPermissionContainer)
        {
            entry.addPermissions((EntityPermissionContainer) current, Permission.READ_NO_ALLOCATION);
        }
        else if (type == Category.class)
        {
            entry.affectAll = true;
        }
        else if (type == Conflict.class)
        {
            if (entry.isDelete)
            {
                entry.affectAll = true;
            }
            else
            {
                Conflict conflict = (Conflict) current;
                addPermissions(entry, conflict.getReservation1());
                addPermissions(entry, conflict.getReservation2());
            }
        }
        else if (current instanceof Preferences)
        {
            ReferenceInfo<User> owner = ((PreferencesImpl) current).getOwnerRef();
            if (owner != null)
            {
                entry.addUserIds(Collections.singletonList(owner.getId()));
            }
        }
        deleteUpdateSet.put(entry.getId(), entry);
    }

    private void addPermissions(DeleteUpdateEntry entry, ReferenceInfo<Reservation> reservation1)
    {
        Reservation event = tryResolve(reservation1);
        if (event != null)
        {
            entry.addPermissions(event, Permission.EDIT);
        }
        else
        {
            DeleteUpdateEntry deleteUpdateEntry = deleteUpdateSet.get(event.getId());
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
                if (entry.affectedUserIds.contains(userId))
                {
                    return true;
                }
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

        @Override public int compareTo(DeleteUpdateEntry o)
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
                affectedUserIds = new HashSet<String>(1);
            }
            affectedUserIds.addAll(userIds);
        }

        private void addGroupIds(Collection<String> groupIds)
        {
            if (affectedGroupIds == null)
            {
                affectedGroupIds = new HashSet<String>(1);
            }
            affectedGroupIds.addAll(groupIds);
        }

        String getId()
        {
            return reference.getId();
        }

        @Override public boolean equals(Object o)
        {
            boolean equals = getId().equals(((DeleteUpdateEntry) o).getId());
            return equals;
        }

        @Override public int hashCode()
        {
            return getId().hashCode();
        }

        @Override public String toString()
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
                        entry.affectedUserIds = new HashSet<String>(1);
                    }
                    entry.affectedUserIds.add(ownerId.getId());
                }
            }
            Collection<Permission> permissions = current.getPermissionList();
            for (Permission p : permissions)
            {
                String groupId = ((PermissionImpl) p).getGroupId();
                String userId = ((PermissionImpl) p).getUserId();
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
                        entry.affectedUserIds = new HashSet<String>(1);
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
                entry.affectedGroupIds = new HashSet<String>(1);
            }
            entry.affectedGroupIds.add(groupId);
            final Category parent = ((Category) entity).getParent();
            if (parent != null && !parent.equals(getSuperCategory()))
            {
                addGroupIds(entry, parent.getId());
            }
        }

    }

    /*
    @Override public Collection<Entity> getUpdatedEntities(final User user, final Date timestamp) throws RaplaException
    {
        boolean isDelete = false;
        Collection<ReferenceInfo> references = getEntities(user, timestamp, isDelete);
        ArrayList<Entity> result = new ArrayList<Entity>();
        for (ReferenceInfo info : references)
        {
            String id = info.getId();
            Class<? extends Entity> entityClass = info.getType();
            if (entityClass != null && entityClass == Conflict.class)
            {
                Conflict conflict = conflictFinder.findConflict(id, timestamp);
                if (conflict != null)
                {
                    result.add(conflict);
                }
            }
            else
            {
                Entity entity = tryResolve(id, entityClass);
                if (entity != null)
                {
                    result.add(entity);
                }
                else
                {
                    getLogger().warn("Can't find updated entity for id " + id);
                }
            }
        }

        return result;
    }
    */

    /**
     * returns all entities with a timestamp > the passed timestamp
     */
    private Collection<ReferenceInfo> getEntities(User user, final Date timestamp, boolean isDelete) throws RaplaException
    {
        Assert.notNull(timestamp);
        // we use an empty id here because the implmentation of the DeleteUpdateEntry compare compares idStrings if timestamps are equal
        // so tailMap returns all entities with a timestamp >= timestamp
        final String dummyId = "";
        // we need to add +1 so that we dont get entities with the passed (guaranteed timestamp)
        DeleteUpdateEntry fromElement = new DeleteUpdateEntry(new ReferenceInfo(dummyId, Allocatable.class), new Date(timestamp.getTime() + 1), isDelete);
        LinkedList<ReferenceInfo> result = new LinkedList<ReferenceInfo>();

        Lock lock = readLock();
        final Collection<String> groupsIncludingParents = user != null ? UserImpl.getGroupsIncludingParents(user) : null;
        String userId = user != null ? user.getId() : null;
        try
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
        finally
        {
            unlock(lock);
        }
        return result;
    }

    protected void updateBindings(Map<ReferenceInfo<Allocatable>, AllocationChange> toUpdate, Reservation reservation, Appointment app, boolean remove)
    {

        Set<ReferenceInfo<Allocatable>> allocatablesToProcess = new HashSet<ReferenceInfo<Allocatable>>();
        allocatablesToProcess.add(null);
        if (reservation != null)
        {
            final Collection<ReferenceInfo<Allocatable>> allocatableIdsFor = ((ReservationImpl) reservation).getAllocatableIdsFor(app);
            allocatablesToProcess.addAll(allocatableIdsFor);
            final String templateId = reservation.getAnnotation(RaplaObjectAnnotations.KEY_TEMPLATE);
            if (templateId != null)
            {
                allocatablesToProcess.add(new ReferenceInfo<Allocatable>(templateId, Allocatable.class));
            }
            // This double check is very imperformant and will be removed in the future, if it doesnt show in test runs
            //			if ( remove)
            //			{
            //				Collection<Allocatable> allocatables = cache.getCollection(Allocatable.class);
            //				for ( Allocatable allocatable:allocatables)
            //				{
            //					SortedSet<Appointment> appointmentSet = this.appointmentMap.get( allocatable.getId());
            //					if ( appointmentSet == null)
            //					{
            //						continue;
            //					}
            //					for (Appointment app1:appointmentSet)
            //					{
            //						if ( app1.equals( app))
            //						{
            //							if ( !allocatablesToProcess.contains( allocatable))
            //							{
            //								getLogger().error("Old reservation " + reservation.toString() + " has not the correct allocatable information. Using full search for appointment " + app + " and resource " + allocatable ) ;
            //								allocatablesToProcess.add(allocatable);
            //							}
            //						}
            //					}
            //				}
            //			}
        }
        else
        {
            getLogger().error("Appointment without reservation found " + app + " ignoring.");
        }

        for (ReferenceInfo<Allocatable> allocatableRef : allocatablesToProcess)
        {
            AllocationChange updateSet;
            if (allocatableRef != null)
            {
                updateSet = toUpdate.get(allocatableRef);
                if (updateSet == null)
                {
                    updateSet = new AllocationChange();
                    toUpdate.put(allocatableRef, updateSet);
                }
            }
            else
            {
                updateSet = null;
            }
            if (remove)
            {
                appointmentBindings.removeAppointmentBinding(app, allocatableRef);
                if (updateSet != null)
                {
                    updateSet.toRemove.add(app);
                }
            }
            else
            {
                appointmentBindings.addAppointmentBinding(app, allocatableRef);
                if (updateSet != null)
                {
                    updateSet.toChange.add(app);
                }
            }
        }
    }

    static final SortedSet<Appointment> EMPTY_SORTED_SET = Collections.unmodifiableSortedSet(new TreeSet<Appointment>());

    /** returs all appointments for the allocatable and all groupMembers and belongsTo*/
    protected SortedSet<Appointment> getAppointments(Allocatable allocatable)
    {
        final ReferenceInfo<Allocatable> reference = allocatable != null ? allocatable.getReference() : null;
        Set<ReferenceInfo<Allocatable>> allocatableIds = cache.getDependentRef(reference);
        if (allocatableIds.size() == 0)
        {
            SortedSet<Appointment> s = appointmentBindings.getAppointments(null);
            if (s != null)
            {
                return s;
            }
            return EMPTY_SORTED_SET;
        }
        else
        {
            SortedSet<Appointment> transitive = new TreeSet<Appointment>(new AppointmentStartComparator());
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

    static final class AppointmentMapClass
    {
        final private Logger logger;
        private Map<ReferenceInfo<Allocatable>, SortedSet<Appointment>> appointmentMap;

        private AppointmentMapClass(Logger newLogger)
        {
            logger = newLogger;
        }

        private void initAppointmentBindings(Collection<Reservation> reservations)
        {
            appointmentMap = new HashMap<ReferenceInfo<Allocatable>, SortedSet<Appointment>>();
            for (Reservation r : reservations)
            {
                for (Appointment app : ((ReservationImpl) r).getAppointmentList())
                {
                    ReservationImpl reservation = (ReservationImpl) app.getReservation();
                    Collection<ReferenceInfo<Allocatable>> allocatables = reservation.getAllocatableIdsFor(app);
                    {
                        final ReferenceInfo<Allocatable> alloc = null;
                        addAppointmentBinding(app, alloc);
                    }
                    for (ReferenceInfo<Allocatable> alloc : allocatables)
                    {
                        addAppointmentBinding(app, alloc);
                    }
                    final String annotation = reservation.getAnnotation(RaplaObjectAnnotations.KEY_TEMPLATE);
                    if (annotation != null)
                    {
                        ReferenceInfo<Allocatable> alloc = new ReferenceInfo(annotation, Allocatable.class);
                        addAppointmentBinding(app, alloc);
                    }
                }
            }
        }

        private void removeAppointmentBinding(Appointment app, ReferenceInfo<Allocatable> allocationId)
        {
            Collection<Appointment> appointmentSet = appointmentMap.get(allocationId);
            if (appointmentSet == null)
            {
                return;
            }

            // binary search could fail if the appointment has changed since the last add, which should not
            // happen as we only put and search immutable objects in the map. But the method is left here as a failsafe
            // with a log messaget
            if (!appointmentSet.remove(app))
            {
                logger.error("Appointent has changed, so its not found in indexed binding map. Removing via full search");
                // so we need to traverse all appointment
                Iterator<Appointment> it = appointmentSet.iterator();
                while (it.hasNext())
                {
                    if (app.equals(it.next()))
                    {
                        it.remove();
                        break;
                    }
                }
            }
        }

        private void removeBindings(List<ReferenceInfo<Allocatable>> removedAllocatables)
        {
            for (ReferenceInfo<Allocatable> alloc : removedAllocatables)
            {
                SortedSet<Appointment> sortedSet = appointmentMap.get(alloc);
                if (sortedSet != null && !sortedSet.isEmpty())
                {
                    logger.error("Removing non empty appointment map for resource " + alloc + " Appointments:" + sortedSet);
                }
                appointmentMap.remove(alloc);
            }
        }

        private void addAppointmentBinding(Appointment appRef, ReferenceInfo<Allocatable> allocationId)
        {
            SortedSet<Appointment> set = appointmentMap.get(allocationId);
            if (set == null)
            {
                set = new TreeSet<Appointment>(new AppointmentStartComparator());
                appointmentMap.put(allocationId, set);
            }
            set.add(appRef);
        }

        // this check is only there to detect rapla bugs in the conflict api and can be removed if it causes performance issues
        private void checkAbandonedAppointments(LocalCache cache)
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
                    for (Appointment app : appointmentSet)
                    {
                        Reservation reservation = app.getReservation();
                        final String annotation = reservation.getAnnotation(RaplaObjectAnnotations.KEY_TEMPLATE);
                        Allocatable template = annotation != null ? cache.tryResolve(annotation, Allocatable.class) : null;
                        if (reservation == null)
                        {
                            logger.error("Appointment without a reservation stored in cache " + app);
                            appointmentSet.remove(app);
                            continue;
                        }
                        else if (!reservation.hasAllocated(allocatable, app) && (template == null || !template.equals(allocatable)))
                        {
                            logger.error(
                                    "Allocation is not stored correctly for " + reservation + " " + app + " " + allocatable + " removing binding for " + app);
                            appointmentSet.remove(app);
                            continue;
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
                                Reservation persistant = cache.tryResolve(id);
                                if (persistant != null)
                                {
                                    Date lastChanged = original.getLastChanged();
                                    Date persistantLastChanged = persistant.getLastChanged();
                                    if (persistantLastChanged != null && !persistantLastChanged.equals(lastChanged))
                                    {
                                        logger.error("Reservation stored in cache is not the same as in allocation store " + original);
                                        continue;
                                    }
                                }
                                else
                                {
                                    logger.error("Reservation not stored in cache " + original + " removing binding for " + app);
                                    appointmentSet.remove(app);
                                    continue;
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

    private void removeOldHistory() throws RaplaException
    {
        final Lock writeLock = writeLock();
        try
        {
            Date lastUpdated = getLastRefreshed();
            Date date = new Date(lastUpdated.getTime() - HISTORY_DURATION);
            history.removeUnneeded(date);
        }
        finally
        {
            unlock(writeLock);
        }
    }

    private void removeOldConflicts() throws RaplaException
    {
        Date today = today();
        Set<ReferenceInfo<Conflict>> conflictsToDelete;
        {
            Lock readLock = readLock();
            try
            {
                conflictsToDelete = new HashSet<ReferenceInfo<Conflict>>();
                conflictsToDelete.addAll(conflictFinder.removeOldConflicts(today));
                conflictsToDelete.retainAll(cache.getConflictIds());
            }
            finally
            {
                unlock(readLock);
            }
        }

        Collection<Conflict> conflicts = cache.getDisabledConflicts();
        for (Conflict conflict : conflicts)
        {
            if (!conflictFinder.isActiveConflict(conflict, today))
            {
                conflictsToDelete.add(conflict.getReference());
            }
        }

        if (conflictsToDelete.size() > 0)
        {
            getLogger().info("Removing old conflicts " + conflictsToDelete.size());
            Lock writeLock = writeLock();
            try
            {
                //Order is important they can't be removed from database if they are not in cache
                removeConflictsFromDatabase(conflictsToDelete);
                removeConflictsFromCache(conflictsToDelete);
            }
            finally
            {
                unlock(writeLock);
            }
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
        Set<ReferenceInfo<Conflict>> conflicts = new HashSet<ReferenceInfo<Conflict>>();
        for (UpdateOperation op : operations)
        {
            final ReferenceInfo ref = op.getReference();
            if (op instanceof Remove)
            {
                if (cache.getConflictIds().contains(ref.getId()))
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
        Collection<Entity> storeObjects = new ArrayList<Entity>(evt.getStoreObjects());
        Collection<ReferenceInfo> removeIds = new ArrayList<ReferenceInfo>(evt.getRemoveIds());
        for (Entity entity : storeObjects)
        {
            //evt.putStore(entity);
            Class<? extends Entity> raplaType = entity.getTypeClass();
            if (raplaType == DynamicType.class)
            {
                DynamicTypeImpl dynamicType = (DynamicTypeImpl) entity;
                addChangedDynamicTypeDependant(evt, store, dynamicType, false);
            }
            if (entity instanceof Classifiable)
            {
                processOldPermssionModify(store, entity);
            }
        }
        Set<String> categoriesToRemove = new HashSet<String>();
        Set<String> categoriesToStore = new HashSet<String>();
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
                        addChangedDependencies(evt, store, dynamicType, entity, false);
                    }
                }
            }
            if (entity instanceof Category)
            {
                final CategoryImpl category = (CategoryImpl) entity;
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
                        final Collection<String> toRemove = new HashSet(exisitingChildIds);
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
                            editableParent = exisitingParent.clone();
                            evt.putStore(editableParent);
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
                addChangedDynamicTypeDependant(evt, store, dynamicType, true);
            }
            // If entity is a user, remove the preference object
            if (User.class == raplaType)
            {
                addRemovedUserDependant(evt, store, (User) entity);
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
                                evt.putStore(editableParent);
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
        final ReferenceInfo<Category> ref = new ReferenceInfo(categoryId, Category.class);
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

    @SuppressWarnings("deprecation") private void processOldPermssionModify(@SuppressWarnings("unused") EntityStore store, Entity entity)
    {
        Class<? extends Entity> clazz = (entity instanceof Reservation) ? Reservation.class : Allocatable.class;
        Classifiable persistant = (Classifiable) tryResolve(entity.getId(), clazz);
        Util.processOldPermissionModify((Classifiable) entity, persistant);
    }

    protected void addChangedDynamicTypeDependant(UpdateEvent evt, EntityStore store, DynamicTypeImpl type, boolean toRemove) throws RaplaException
    {
        List<Entity> referencingEntities = getReferencingEntities(type, store);
        Iterator<Entity> it = referencingEntities.iterator();
        while (it.hasNext())
        {
            Entity entity = it.next();
            if (!(entity instanceof DynamicTypeDependant))
            {
                continue;
            }
            DynamicTypeDependant dependant = (DynamicTypeDependant) entity;
            // Classifiables need update?
            if (!dependant.needsChange(type) && !toRemove)
                continue;
            if (getLogger().isDebugEnabled())
                getLogger().debug("Classifiable " + entity + " needs change!");
            // Classifiables are allready on the store list
            addChangedDependencies(evt, store, type, entity, toRemove);
        }
    }

    private void addChangedDependencies(UpdateEvent evt, EntityStore store, DynamicTypeImpl type, Entity entity, boolean toRemove) throws RaplaException
    {
        DynamicTypeDependant dependant = (DynamicTypeDependant) evt.findEntity(entity);
        if (dependant == null)
        {
            // no, then create a clone of the classfiable object and add to list
            User user = null;
            if (evt.getUserId() != null)
            {
                user = resolve(cache, evt.getUserId(), User.class);
            }
            Class<Entity> entityType = entity.getTypeClass();
            Entity persistant = store.tryResolve(entity.getId(), entityType);
            dependant = (DynamicTypeDependant) editObject(entity, persistant, user);
            // replace or add the modified entity
            evt.putStore((Entity) dependant);
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
        List<Entity> referencingEntities = getReferencingEntities(user, store);
        Iterator<Entity> it = referencingEntities.iterator();
        List<Allocatable> templates = new ArrayList<Allocatable>();
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
                    @SuppressWarnings("unchecked") Class<? extends Entity> typeClass = entity.getTypeClass();
                    Entity persistant = cache.tryResolve(entity.getId(), typeClass);
                    Entity dependant = editObject(entity, persistant, user);
                    ((SimpleEntity) dependant).setLastChangedBy(null);
                    updateEvt.putStore(entity);
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
     *
     * @param entity
     */
    final protected Set<Entity> getDependencies(Entity entity, EntityStore store)
    {
        Class<? extends Entity> type = entity.getTypeClass();
        final Collection<Entity> referencingEntities;
        if (Category.class == type || DynamicType.class == type || Allocatable.class == type || User.class == type)
        {
            HashSet<Entity> dependencyList = new HashSet<Entity>();
            referencingEntities = getReferencingEntities(entity, store);
            dependencyList.addAll(referencingEntities);
            return dependencyList;
        }
        return Collections.emptySet();
    }

    private List<Entity> getReferencingEntities(Entity entity, EntityStore store)
    {
        List<Entity> result = new ArrayList<Entity>();
        addReferers(cache.getReservations(), entity, result);
        addReferers(cache.getAllocatables(), entity, result);
        Collection<User> users = cache.getUsers();
        addReferers(users, entity, result);
        addReferers(cache.getDynamicTypes(), entity, result);
        addReferers(CategoryImpl.getRecursive(cache.getSuperCategory()), entity, result);

        List<Preferences> preferenceList = new ArrayList<Preferences>();
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
        addReferers(preferenceList, entity, result);
        return result;
    }

    private void addReferers(Iterable<? extends Entity> refererList, Entity object, List<Entity> result)
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
                if (store.tryResolve(id, info.getType()) != null)
                    continue;

                throw new EntityNotFoundException(i18n.format("error.reference_not_stored", info.getType() + ":" + id));
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
            String name = "";
            Entity entity2 = null;
            final Class<? extends Entity> typeClass = entity.getTypeClass();
            if (DynamicType.class == typeClass)
            {
                DynamicType type = (DynamicType) entity;
                name = type.getKey();
                entity2 = store.getDynamicType(name);
                if (entity2 != null && !entity2.equals(entity))
                    throwNotUnique(name);
            }

            if (Category.class == typeClass)
            {
                Category category = (Category) entity;
                Category[] categories = category.getCategories();
                for (int i = 0; i < categories.length; i++)
                {
                    String key = categories[i].getKey();
                    for (int j = i + 1; j < categories.length; j++)
                    {
                        String key2 = categories[j].getKey();
                        if (key == key2 || (key != null && key.equals(key2)))
                        {
                            throwNotUnique(key);
                        }
                    }
                }
            }

            if (User.class == entity.getTypeClass())
            {
                name = ((User) entity).getUsername();
                if (name == null || name.trim().length() == 0)
                {
                    String message = i18n.format("error.no_entry_for", getString("username"));
                    throw new RaplaException(message);
                }
                // FIXME Replace with store.getUserFromRequest for the rare case that two users with the same username are stored in one operation
                entity2 = cache.getUser(name);
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
            Entity persistantVersion = findPersistant(entity);
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

    protected void removeInconsistentEntities(LocalCache cache, Collection<Entity> list)
    {
        for (Iterator iterator = list.iterator(); iterator.hasNext(); )
        {
            Entity entity = (Entity) iterator.next();
            try
            {
                checkConsitency(entity, cache);
            }
            catch (RaplaException | IllegalStateException e)
            {
                if (entity instanceof Reservation)
                {
                    getLogger().error("Not loading entity with id: " + entity.getId(), e);
                    cache.remove(entity);
                    iterator.remove();
                }
            }
        }
    }

    /** Check if the objects are consistent, so that they can be safely stored. */
    /**
     * @param evt
     * @param store
     * @throws RaplaException
     */
    protected void checkConsistency(UpdateEvent evt, EntityStore store) throws RaplaException
    {
        Collection<EntityReferencer> entityReferences = evt.getEntityReferences();
        for (EntityReferencer referencer : entityReferences)
        {
            for (ReferenceInfo referenceInfo : referencer.getReferenceInfo())
            {
                Entity reference = store.resolve(referenceInfo.getId(), referenceInfo.getType());
                if (reference instanceof Preferences || reference instanceof Conflict || reference instanceof Reservation || reference instanceof Appointment)
                {
                    throw new RaplaException("The current version of Rapla doesn't allow references to objects of type " + reference.getTypeClass());
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
        checkBelongsTo(entity, 0);
        checkPackages(entity, 0);
    }

    private void checkPackages(final Object entity, final int depth) throws RaplaException
    {
        if (depth > 20)
        {
            final String name = getName(entity);
            final String format = i18n.format("error.packageCycle", name);
            throw new RaplaException(format);
        }
        if (entity instanceof Classifiable)
        {
            final Classifiable classifiable = (Classifiable) entity;
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
                                if (target.equals(entity))
                                {
                                    final String name = getName(entity);
                                    final String format = getI18n().format("error.packageCantReferToSelf", name);
                                    throw new RaplaException(format);
                                }
                                else
                                {
                                    checkPackages(target, depth + 1);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void checkBelongsTo(final Object entity, final int depth) throws  RaplaException
    {
        if (depth > 20)
        {
            final String name = getName(entity);
            final String format = i18n.format("error.belongsToCycle", name);
            throw new RaplaException(format);
        }

        if (entity instanceof Classifiable)
        {
            final Classifiable classifiable = (Classifiable) entity;
            final Classification classification = classifiable.getClassification();
            if (classification != null)
            {
                final Attribute[] attributes = classification.getAttributes();
                for (Attribute att : attributes)
                {
                    final Boolean belongsTo = (Boolean) att.getConstraint(ConstraintIds.KEY_BELONGS_TO);
                    if (belongsTo != null && belongsTo)
                    {
                        final Object target = classification.getValue(att);
                        if (target != null)
                        {
                            if (target.equals(entity))
                            {
                                final String name = getName(entity);
                                final String format = getI18n().format("error.belongsToCantReferToSelf", name);
                                throw new RaplaException(format);
                            }
                            else
                            {
                                checkBelongsTo(target, depth + 1);
                            }
                        }
                    }
                }
            }
        }
    }

    protected Collection<ReferenceInfo> removeInconsistentReservations(EntityStore store)
    {
        List<Reservation> reservations = new ArrayList<Reservation>();
        List<ReferenceInfo> reservationRefs = new ArrayList<ReferenceInfo>();
        Collection<Entity> list = store.getList();
        for (Entity entity : list)
        {
            if (!(entity instanceof Reservation))
            {
                continue;
            }
            Reservation reservation = (Reservation) entity;
            if (reservation.getSortedAppointments().size() == 0 || (reservation.getAllocatables().length == 0 && !RaplaComponent.isTemplate(reservation)))
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

        if (reservations.size() != 0)
        {
            JsonParserWrapper.JsonParser gson = JsonParserWrapper.defaultJson().get();
            getLogger().error("The following events will be removed because they have no resources or appointments: \n" + gson.toJson(reservations));
        }
        return reservationRefs;
    }

    protected void checkNoDependencies(final UpdateEvent evt, final EntityStore store) throws RaplaException
    {
        Collection<ReferenceInfo> removedIds = evt.getRemoveIds();
        Collection<Entity> storeObjects = new HashSet<Entity>(evt.getStoreObjects());
        HashSet<Entity> dep = new HashSet<Entity>();
        Collection<Entity> removeEntities = new ArrayList<Entity>();
        for (ReferenceInfo id : removedIds)
        {
            Entity persistant = store.tryResolve(id);
            if (persistant != null)
            {
                removeEntities.add(persistant);
            }
        }
        //IterableChain<Entity> iteratorChain = new IterableChain<Entity>(deletedCategories, removeEntities);
        for (Entity entity : removeEntities)
        {
            // First we add the dependencies from the stored object list
            for (Entity obj : storeObjects)
            {
                if (obj instanceof EntityReferencer)
                {
                    if (isRefering((EntityReferencer) obj, entity))
                    {
                        dep.add(obj);
                    }
                }
            }
            // we check if the user deletes himself
            if (entity instanceof User)
            {
                String eventUserId = evt.getUserId();
                if (eventUserId != null && eventUserId.equals(entity.getId()))
                {
                    List<String> emptyList = Collections.emptyList();
                    throw new DependencyException(i18n.getString("error.deletehimself"), emptyList);
                }
            }

            // Than we add the dependencies from the cache. It is important that
            // we don't add the dependencies from the stored object list here,
            // because a dependency could be removed in a stored object
            Set<Entity> dependencies = getDependencies(entity, store);
            for (Entity dependency : dependencies)
            {
                if (!storeObjects.contains(dependency) && !removeEntities.contains(dependency))
                {
                    // only add the first 21 dependencies;
                    if (dep.size() > MAX_DEPENDENCY)
                    {
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

        if (dep.size() > 0)
        {
            Collection<String> names = new ArrayList<String>();
            for (Entity obj : dep)
            {
                String string = getDependentName(obj);
                names.add(string);
            }
            throw new DependencyException(getString("error.dependencies"), names.toArray(new String[] {}));
        }
        // Count dynamic-types to ensure that there is least one dynamic type
        // for reservations and one for resources or persons
        checkDynamicType(removeEntities, Collections.singleton(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION));
        checkDynamicType(removeEntities, new HashSet<String>(Arrays.asList(
                new String[] { DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE, DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON })));
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
        StringBuffer buf = new StringBuffer();
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
            buf.append(getString("categorie"));
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
        String string = buf.toString();
        return string;
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
                if (correct_pw.equals(password))
                {
                    return true;
                }
            }
        }
        return false;
    }

    @Override public Promise<Map<Allocatable, Collection<Appointment>>> getFirstAllocatableBindings(Collection<Allocatable> allocatables,
            Collection<Appointment> appointments, Collection<Reservation> ignoreList)
    {
        final Promise<Map<Allocatable, Collection<Appointment>>> prom = scheduler.supply(() -> getFirstAllocatableBindingsMap(allocatables, appointments, ignoreList));
        return prom;
    }

    private Map<Allocatable, Collection<Appointment>> getFirstAllocatableBindingsMap(Collection<Allocatable> allocatables, Collection<Appointment> appointments,
            Collection<Reservation> ignoreList) throws RaplaException
    {
        final Lock readLock = readLock();
        Map<Allocatable, Map<Appointment, Collection<Appointment>>> allocatableBindings;
        try
        {
            allocatableBindings = getAllocatableBindings(allocatables, appointments, ignoreList, true);
        }
        finally
        {
            unlock(readLock);
        }
        Map<Allocatable, Collection<Appointment>> map = new HashMap<Allocatable, Collection<Appointment>>();
        for (Map.Entry<Allocatable, Map<Appointment, Collection<Appointment>>> entry : allocatableBindings.entrySet())
        {
            Allocatable alloc = entry.getKey();
            Collection<Appointment> list = entry.getValue().keySet();
            map.put(alloc, list);
        }
        return map;
    }

    @Override public Promise<Map<Allocatable, Map<Appointment, Collection<Appointment>>>> getAllAllocatableBindings(Collection<Allocatable> allocatables,
            Collection<Appointment> appointments, Collection<Reservation> ignoreList)
    {
        return scheduler.supply(() ->
        {
            Lock readLock = readLock();
            try
            {
                Map<Allocatable, Map<Appointment, Collection<Appointment>>> allocatableBindings = getAllocatableBindings(allocatables, appointments, ignoreList,
                        false);
                return allocatableBindings;
            }
            finally
            {
                unlock(readLock);
            }
        });
    }

    public Map<Allocatable, Map<Appointment, Collection<Appointment>>> getAllocatableBindings(Collection<Allocatable> allocatables,
            Collection<Appointment> appointments, Collection<Reservation> ignoreList, boolean onlyFirstConflictingAppointment)
    {
        Map<Allocatable, Map<Appointment, Collection<Appointment>>> map = new HashMap<Allocatable, Map<Appointment, Collection<Appointment>>>();
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
                map.put(allocatable, new HashMap<Appointment, Collection<Appointment>>());
                for (Appointment appointment : appointments)
                {
                    Set<Appointment> conflictingAppointments = AppointmentImpl
                            .getConflictingAppointments(appointmentSet, appointment, ignoreList, onlyFirstConflictingAppointment);
                    if (conflictingAppointments.size() > 0)
                    {
                        Map<Appointment, Collection<Appointment>> appMap = map.get(allocatable);
                        if (appMap == null)
                        {
                            appMap = new HashMap<Appointment, Collection<Appointment>>();
                            map.put(allocatable, appMap);
                        }
                        appMap.put(appointment, conflictingAppointments);
                    }
                }
            }
        }
        return map;
    }

    @Override public Promise<Date> getNextAllocatableDate(final Collection<Allocatable> allocatables, final Appointment appointment,
            final Collection<Reservation> ignoreList, final Integer worktimeStartMinutes, final Integer worktimeEndMinutes, final Integer[] excludedDays, final Integer rowsPerHour)
    {
        Promise<Date> promise = scheduler.supply( () -> {
            Lock readLock = readLock();
            try
            {
                Appointment newState = appointment;
                Date firstStart = appointment.getStart();
                boolean startDateExcluded = isExcluded(excludedDays, firstStart);
                boolean wholeDay = appointment.isWholeDaysSet();
                boolean inWorktime = inWorktime(appointment, worktimeStartMinutes, worktimeEndMinutes);
                final int rowsPerHourInt = (rowsPerHour == null || rowsPerHour <= 1) ? 1: rowsPerHour;
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
                    newState.move(newStart);
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
            }
            finally
            {
                unlock(readLock);
            }
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
        Map<Allocatable, Collection<Appointment>> firstAllocatableBindings = getFirstAllocatableBindingsMap(allocatables, Collections.singleton(appointment), ignoreList);
        for (Map.Entry<Allocatable, Collection<Appointment>> entry : firstAllocatableBindings.entrySet())
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
        Lock readLock = readLock();
        try
        {
            return cache.getVisibleEntities(user);
        }
        finally
        {
            unlock(readLock);
        }
    }

    @SuppressWarnings("deprecation") private void addDefaultEventPermissions(DynamicTypeImpl dynamicType, Category userGroups)
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
        newPref.putEntry(CalendarModel.ONLY_MY_EVENTS_DEFAULT, false);
        newPref.setReadOnly();
        store.put(newPref);

        @SuppressWarnings("deprecation") String[] userGroups = new String[] { Permission.GROUP_CAN_READ_EVENTS_FROM_OTHERS,
                Permission.GROUP_CAN_CREATE_EVENTS };

        CategoryImpl groupsCategory = new CategoryImpl(now, now);
        groupsCategory.setKey("user-groups");
        groupsCategory.setResolver(store);
        setName(groupsCategory.getName(), groupsCategory.getKey());
        setNew(groupsCategory);
        store.put(groupsCategory);
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
        ReferenceInfo<T> id = createIdentifier(raplaType, 1)[0];
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

    protected void convertTemplates()
    {
        //        for (Reservation reservations : cache.getReservations())
        //            ;

    }

    public ReferenceInfo tryResolveExternalId(String externalId)
    {
        final ReferenceInfo referenceInfo = externalIds.get(externalId);
        return referenceInfo;
    }

    public UpdateResult getUpdateResult(Date since, User user) throws RaplaException
    {
        checkConnected();
        final Date historyValidStart = getHistoryValidStart();
        if (since.before(historyValidStart))
        {
            return new UpdateResult(null, historyValidStart, null, null);
        }
        Date until = getLastRefreshed();
        final Collection<ReferenceInfo> toUpdate = getEntities(user, since, false);
        Map<ReferenceInfo, Entity> oldEntities = new LinkedHashMap<ReferenceInfo, Entity>();
        Collection<Entity> updatedEntities = new ArrayList<Entity>();
        for (ReferenceInfo update : toUpdate)
        {
            Entity oldEntity;
            Entity newEntity;
            final Class<? extends Entity> type = update.getType();
            if (type == Conflict.class)
            {
                final Conflict conflict = conflictFinder.findConflict((ReferenceInfo<Conflict>) update);
                newEntity = cache.fillConflictDisableInformation(user, conflict);
                // can be null if no conflict disalbe information is stored
                if (history.hasHistory(update))
                {
                    oldEntity = history.get(update, since);
                }
                else
                {
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
        for (Iterator<ReferenceInfo> it = toRemove.iterator(); it.hasNext(); )
        {
            ReferenceInfo update = it.next();
            Entity entity;
            if (update.getType() == Conflict.class)
            {
                entity = null;
            }
            else
            {
                entity = history.get(update, since);
            }
            Entity oldEntity = null;
            if (entity != null)
            {
                oldEntity = entity;
            }
            else if (update.getType() != Conflict.class)
            {
                final EntityHistory.HistoryEntry latest = history.getLatest(update);
                if (latest != null)
                {
                    oldEntity = history.getEntity(latest);
                }
                else
                {
                    getLogger().warn("the entity " + update + " was deleted but not found in the history.");
                }
            }
            if (oldEntity != null)
            {
                oldEntities.put(update, oldEntity);
            }
        }
        UpdateResult updateResult = createUpdateResult(oldEntities, updatedEntities, toRemove, since, until);
        return updateResult;
    }

    @Override public UpdateResult getUpdateResult(Date since) throws RaplaException
    {
        return getUpdateResult(since, null);
    }

    @Override public Collection<Appointment> getAppointmentsFromUserCalendarModels(ReferenceInfo<User> userId, TimeInterval syncRange) throws RaplaException
    {
        checkConnected();
        return calendarModelCache.getAppointments(userId, syncRange);
    }

    @Override public Collection<ReferenceInfo<User>> findUsersThatExport(Allocatable allocatable) throws RaplaException
    {
        checkConnected();
        return calendarModelCache.findMatchingUsers(allocatable);
    }

    @Override public Collection<ReferenceInfo<User>> findUsersThatExport(Appointment appointment) throws RaplaException
    {
        checkConnected();
        return calendarModelCache.findMatchingUser(appointment);
    }

    /*
     * Dependencies for belongsTo and package
     */

    @Override public void doMerge(Allocatable selectedObject, Set<ReferenceInfo<Allocatable>> allocatableIds, User user) throws RaplaException
    {
        final Lock writeLock = writeLock();
        try
        {
            // FIXME check write permissions
            Set<Allocatable> allocatables = new LinkedHashSet<>();
            for (ReferenceInfo<Allocatable> allocatableId : allocatableIds)
            {
                final Allocatable resolve = resolve(allocatableId);
                allocatables.add(resolve);
            }
            final Collection<Entity> storeObjects = new LinkedHashSet<>();
            if (!selectedObject.isReadOnly())
            {
                storeObjects.add(selectedObject);
            }
            {// now change the references
                for (Allocatable allocatable : allocatables)
                {
                    final List<Entity> referencingEntities = getReferencingEntities(allocatable, new EntityStore(this));
                    for (Entity entity : referencingEntities)
                    {
                        final Entity editObject = editObject(entity, user);
                        final ReferenceInfo<Allocatable> oldRef = allocatable.getReference();
                        final ReferenceInfo<Allocatable> newRef = selectedObject.getReference();
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
            unlock(writeLock);
        }
    }

}
