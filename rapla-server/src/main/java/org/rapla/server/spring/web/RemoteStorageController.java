package org.rapla.server.spring.web;

import org.rapla.RaplaResources;
import org.rapla.components.i18n.I18nBundle;
import org.rapla.components.util.ParseDateException;
import org.rapla.components.util.SerializableDateTimeFormat;
import org.rapla.entities.DependencyException;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentMapping;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.internal.AllocatableImpl;
import org.rapla.entities.domain.internal.AppointmentImpl;
import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.entities.storage.EntityReferencer;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.internal.ConflictImpl;
import org.rapla.framework.RaplaException;
import org.rapla.framework.internal.AbstractRaplaLocale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.rapla.plugin.mail.MailPlugin;
import org.rapla.plugin.mail.server.MailInterface;
import org.rapla.server.PrePostDispatchProcessor;
import org.rapla.server.RemoteSession;
import org.rapla.server.internal.ReloadService;
import org.rapla.server.internal.SecurityManager;
import org.rapla.server.internal.UpdateDataManager;
import org.rapla.server.internal.UpdateDataManagerImpl;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.PermissionController;
import org.rapla.storage.PreferencePatch;
import org.rapla.storage.RaplaNewVersionException;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.StorageOperator;
import org.rapla.storage.SyncStorageOperator;
import org.rapla.storage.UpdateEvent;
import org.rapla.storage.dbrm.AppointmentMap;
import org.rapla.storage.dbrm.RemoteStorage;
import org.rapla.storage.impl.EntityStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@RestController
@ConditionalOnBean(RemoteSession.class)
public class RemoteStorageController implements RemoteStorage
{
    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteStorageController.class);
    private final RemoteSession session;
    private final CachableStorageOperator operator;
    private final SyncStorageOperator syncOperator;
    private final SecurityManager security;
    private final ReloadService reloadService;
    private final Set<PrePostDispatchProcessor> prePostDispatchProcessors;
    private final RaplaResources i18n;
    private final Supplier<MailInterface> mailInterface;
    private final UpdateDataManager updateDataManager;
    private final HttpServletRequest request;

    public RemoteStorageController(RemoteSession session,
                                   CachableStorageOperator operator,
                                   SyncStorageOperator syncOperator,
                                   SecurityManager security,
                                   ReloadService reloadService,
                                   Set<PrePostDispatchProcessor> prePostDispatchProcessors,
                                   RaplaResources i18n,
                                   Supplier<MailInterface> mailInterface,
                                   UpdateDataManager updateDataManager,
                                   HttpServletRequest request)
    {
        this.session = session;
        this.operator = operator;
        this.syncOperator = syncOperator;
        this.security = security;
        this.reloadService = reloadService;
        this.prePostDispatchProcessors = prePostDispatchProcessors;
        this.i18n = i18n;
        this.mailInterface = mailInterface;
        this.updateDataManager = updateDataManager;
        this.request = request;
    }

    @Override
    public UpdateEvent getResources() throws RaplaException
    {
        User user = checkSessionUser();
        LOGGER.debug("A RemoteAuthentificationService wants to get all resource-objects.");
        Collection<Entity> visibleEntities = operator.getVisibleEntities(user);
        UpdateEvent evt = new UpdateEvent();
        evt.setUserId(user.getId());
        for (Entity entity : visibleEntities)
        {
            if (UpdateDataManagerImpl.isTransferedToClient(entity))
            {
                if (entity instanceof Preferences)
                {
                    // Strip .server.* entries (RSA private key, LDAP / SMTP / Exchange credentials,
                    // and the per-user org.rapla.crypto.server.* refreshToken / api-key material)
                    // from EVERY preferences entity — system AND user-owned. The incremental path
                    // (UpdateDataManagerImpl.processClientReadable) already strips unconditionally;
                    // the bootstrap previously guarded on ownerId==null, leaking a user's own
                    // server-only entries (e.g. their refreshToken) on the initial getResources —
                    // a read-only api-key could escalate from it. Admin tools that need the
                    // credential values use the dedicated plugin-config endpoints instead.
                    entity = UpdateDataManagerImpl.removeServerOnlyPreferences((Preferences) entity);
                }
                evt.addStore(entity);
            }
        }
        evt.setLastValidated(operator.getLastRefreshed());
        return evt;
    }

    @Override
    public UpdateEvent getEntityRecursive(Boolean errorIfNotFound, UpdateEvent.SerializableReferenceInfo[] ids) throws RaplaException
    {
        User sessionUser = checkSessionUser();
        ArrayList<Entity> completeList = new ArrayList<>();
        for (UpdateEvent.SerializableReferenceInfo id : ids)
        {
            final ReferenceInfo reference = id.getReference();
            Entity entity;
            try
            {
                entity = operator.resolve(reference);
            }
            catch (EntityNotFoundException ex)
            {
                if (errorIfNotFound == null || errorIfNotFound)
                {
                    throw ex;
                }
                else
                {
                    continue;
                }
            }
            if (entity instanceof Classifiable)
            {
                if (!DynamicTypeImpl.isTransferedToClient((Classifiable) entity))
                {
                    throw new RaplaSecurityException("Entity for id " + id + " is not transferable to the client");
                }
            }
            if (entity instanceof DynamicType)
            {
                if (!DynamicTypeImpl.isTransferedToClient((DynamicType) entity))
                {
                    throw new RaplaSecurityException("Entity for id " + id + " is not transferable to the client");
                }
            }
            if (entity instanceof Reservation)
            {
                entity = checkAndMakeReservationsAnonymous(sessionUser, entity);
            }
            if (entity instanceof Preferences)
            {
                entity = UpdateDataManagerImpl.removeServerOnlyPreferences((Preferences) entity);
            }
            security.checkRead(sessionUser, entity);
            completeList.add(entity);
            LOGGER.debug("Get entity {}", entity);
        }
        UpdateEvent evt = new UpdateEvent();
        evt.setLastValidated(operator.getLastRefreshed());
        for (Entity entity : completeList)
        {
            evt.addStore(entity);
        }
        return evt;
    }

    @Override
    public UpdateEvent getEntityDependencies(Boolean errorIfNotFound, UpdateEvent.SerializableReferenceInfo[] infos) throws RaplaException
    {
        return getEntityRecursive(errorIfNotFound, infos);
    }

    @Override
    public AppointmentMap queryAppointments(QueryAppointments job) throws RaplaException
    {
        User sessionUser = checkSessionUser();
        String[] allocatableIds = job.getResources();
        String[] ownerIds = job.getOwnerIds();
        LocalDateTime start = job.getStart();
        LocalDateTime end = job.getEnd();
        Map<String, String> annotationQuery = job.getAnnotations();
        LOGGER.debug("A RemoteAuthentificationService wants to reservations from .{} to {}", start, end);
        User user = null;
        List<Allocatable> allocatables = new ArrayList<>();
        if (allocatableIds != null)
        {
            for (String id : allocatableIds)
            {
                Allocatable allocatable = operator.resolve(id, Allocatable.class);
                if (security.getPermissionController().canReadInformation(allocatable, sessionUser))
                {
                    allocatables.add(allocatable);
                }
            }
        }
        Collection<User> owners = new ArrayList<>();
        if (ownerIds != null)
        {
            for (String id : ownerIds)
            {
                User owner = operator.resolve(id, User.class);
                owners.add(owner);
            }
        }
        ClassificationFilter[] classificationFilters = null;
        boolean requestsOnly = job.isRequestsOnly();
        AppointmentMapping reservations = syncOperator.queryAppointmentsSync(user, allocatables, owners, start, end, classificationFilters, annotationQuery, requestsOnly);
        AppointmentMap list = new AppointmentMap(reservations);
        LOGGER.debug("Get reservations {} {}: ,{}", start, end, list);
        return list;
    }

    @Override
    public void restartServer() throws RaplaException
    {
        final User user = checkSessionUser();
        if (!user.isAdmin())
        {
            throw new RaplaSecurityException("Only admins can restart the server");
        }
        reloadService.reload();
    }

    @Override
    public UpdateEvent dispatch(UpdateEvent event) throws RaplaException
    {
        return store(event);
    }

    @Override
    public ProfileEditCapabilities getProfileEditCapabilities() throws RaplaException
    {
        User sessionUser = checkSessionUser();
        String source = sessionUser.getAuthenticationSource();
        if (source != null)
        {
            return new ProfileEditCapabilities(false, false, false, source);
        }
        return new ProfileEditCapabilities(operator.canChangePassword(), true, true, null);
    }

    @Override
    public void disconnectExternalAuth(String userId) throws RaplaException
    {
        // PRD 050: admin-only. Converts an external-auth user back to local-only
        // by clearing the source marker. Idempotent (no-op when already null).
        User admin = checkSessionUser();
        if (!admin.isAdmin())
        {
            throw new RaplaSecurityException("Only admins can disconnect users from external auth");
        }
        User target = operator.resolve(userId, User.class);
        if (target.getAuthenticationSource() == null)
        {
            LOGGER.info("disconnectExternalAuth: user '{}' is already local — no-op", target.getUsername());
            return;
        }
        String previousSource = target.getAuthenticationSource();
        User edit = (User) operator.editObjects(java.util.Collections.singleton((org.rapla.entities.Entity) target), admin).values().iterator().next();
        edit.setAuthenticationSource(null);
        operator.storeAndRemove(java.util.Collections.singletonList(edit),
                java.util.Collections.emptyList(), admin);
        LOGGER.info("disconnectExternalAuth: admin '{}' disconnected user '{}' from external auth (was: {})", admin.getUsername(), target.getUsername(), previousSource);
    }

    @Override
    public void changePassword(PasswordPost job) throws RaplaException
    {
        String username = job.getUsername();
        String oldPassword = job.getOldPassword();
        String newPassword = job.getNewPassword();
        User sessionUser = checkSessionUser();
        User user = operator.getUser(username);
        // PRD 050: external-auth users — block (no admin fallback either).
        // Admin must explicitly disconnect first via the disconnect endpoint.
        requireLocalIdentity(user, "change password");
        if (!PermissionController.canAdminUser(sessionUser, user))
        {
            operator.authenticate(username, oldPassword);
        }
        operator.changePassword(user, oldPassword.toCharArray(), newPassword.toCharArray());
    }

    @Override
    public void changeName(ChangeNamePost job) throws RaplaException
    {
        User changingUser = checkSessionUser();
        User user = operator.getUser(job.getUsername());
        if (changingUser.isAdmin() || user.equals(changingUser))
        {
            requireLocalIdentity(user, "change name");
            operator.changeName(user, job.getNewTitle(), job.getNewSurename(), job.getNewLastname());
        }
        else
        {
            throw new RaplaSecurityException("Not allowed to change email from other users");
        }
    }

    @Override
    public void changeEmail(ChangeEmailPost job) throws RaplaException
    {
        User changingUser = checkSessionUser();
        User user = operator.getUser(job.getUsername());
        if (changingUser.isAdmin() || user.equals(changingUser))
        {
            requireLocalIdentity(user, "change email");
            operator.changeEmail(user, job.getNewEmail());
        }
        else
        {
            throw new RaplaSecurityException("Not allowed to change email from other users");
        }
    }

    /**
     * PRD 050 guard. The {@code authenticationSource} marker drives every
     * gate: if non-null, the user's password / name / email live in the IdP
     * named by the marker, and rapla refuses to write a local copy that
     * would shadow it. Admins get the same rejection — fallback is the
     * dedicated disconnect endpoint, not silent overwrite.
     */
    private static void requireLocalIdentity(User target, String operation) throws RaplaSecurityException
    {
        String source = target.getAuthenticationSource();
        if (source != null)
        {
            throw new RaplaSecurityException(
                    "Cannot " + operation + " for user '" + target.getUsername()
                            + "' — identity managed by " + source
                            + ". Disconnect external auth first (admin only).");
        }
    }

    @Override
    public String getUsername(String userId) throws RaplaException
    {
        checkSessionUser();
        return operator.getUsername(new ReferenceInfo<>(userId, User.class));
    }

    @Override
    public void confirmEmail(ChangeEmailPost job) throws RaplaException
    {
        User changingUser = checkSessionUser();
        User user = operator.getUser(job.getUsername());
        if (changingUser.isAdmin() || user.equals(changingUser))
        {
            requireLocalIdentity(user, "confirm email");
            String subject = getString("security_code");
            Preferences prefs = operator.getPreferences(null, true);
            String mailbody = getString("send_code_mail_body_1") + user.getUsername() + ",\n\n" + getString("send_code_mail_body_2") + "\n\n" + getString(
                    "security_code") + Math.abs(user.getEmail().hashCode()) + "\n\n" + getString("send_code_mail_body_3") + "\n\n"
                    + "-----------------------------------------------------------------------------------" + "\n\n" + getString("send_code_mail_body_4")
                    + prefs.getEntryAsString(AbstractRaplaLocale.TITLE, getString("rapla.title")) + " " + getString("send_code_mail_body_5");

            final MailInterface mail = mailInterface.get();
            final String defaultSender = prefs.getEntryAsString(MailPlugin.DEFAULT_SENDER_ENTRY, "");

            mail.sendMail(defaultSender, job.getNewEmail(), subject, mailbody);
        }
        else
        {
            throw new RaplaSecurityException("Not allowed to change email from other users");
        }
    }

    @Override
    public List<String> createIdentifier(String type, int count) throws RaplaException
    {
        checkSessionUser();
        Class<? extends Entity> typeClass = RaplaType.find(type);
        checkSessionUser();
        return operator.createIdentifier(typeClass, count).stream().map(ReferenceInfo::getId).collect(Collectors.toList());
    }

    @Override
    public UpdateEvent refresh(String lastSyncedTime) throws RaplaException
    {
        final User user = checkSessionUser();
        try
        {
            LocalDateTime clientRepoVersion = lastSyncedTime != null ? SerializableDateTimeFormat.INSTANCE.parseTimestamp(lastSyncedTime) : null;
            return updateDataManager.createUpdateEvent(user, clientRepoVersion);
        }
        catch (ParseDateException e)
        {
            throw new RaplaException("Illegal last synced date " + lastSyncedTime + " caused " + e.getMessage(), e);
        }
    }

    @Override
    public UpdateEvent refreshAllEvents(String lastSyncedTime) throws RaplaException
    {
        final User user = checkSessionUser();
        try
        {
            LocalDateTime clientRepoVersion = lastSyncedTime != null ? SerializableDateTimeFormat.INSTANCE.parseTimestamp(lastSyncedTime) : null;
            return updateDataManager.createUpdateEventReservations(user, clientRepoVersion);
        }
        catch (ParseDateException e)
        {
            throw new RaplaException("Illegal last synced date " + lastSyncedTime + " caused " + e.getMessage(), e);
        }
    }

    @Override
    public List<ConflictImpl> getConflicts() throws RaplaException
    {
        User sessionUser = checkSessionUser();
        return syncOperator.getConflictsSync(sessionUser).stream().map(conflict -> (ConflictImpl) conflict).collect(Collectors.toList());
    }

    @Override
    public LocalDateTime getNextAllocatableDate(NextAllocatableDateRequest job) throws RaplaException
    {
        String[] allocatableIds = job.getAllocatableIds();
        AppointmentImpl appointment = job.getAppointment();
        String[] reservationIds = job.getReservationIds();
        Integer worktimestartMinutes = job.getWorktimeStartMinutes();
        Integer worktimeendMinutes = job.getWorktimeEndMinutes();
        Integer[] excludedDays = job.getExcludedDays();
        Integer rowsPerHour = job.getRowsPerHour();
        checkSessionUser();
        List<Allocatable> allocatables = resolveAllocatables(allocatableIds);
        Collection<Reservation> ignoreList = resolveReservations(reservationIds);
        return syncOperator.getNextAllocatableDateSync(allocatables, appointment, ignoreList, worktimestartMinutes, worktimeendMinutes, excludedDays, rowsPerHour);
    }

    @Override
    public BindingMap getFirstAllocatableBindings(AllocatableBindingsRequest job) throws RaplaException
    {
        String[] allocatableIds = job.getAllocatableIds();
        List<AppointmentImpl> appointments = job.getAppointments();
        String[] reservationIds = job.getReservationIds();
        checkSessionUser();
        List<Allocatable> allocatables = resolveAllocatables(allocatableIds);
        Collection<Reservation> ignoreList = resolveReservations(reservationIds);
        List<Appointment> asList = cast(appointments);
        Map<ReferenceInfo<Allocatable>, Collection<Appointment>> bindings = syncOperator.getFirstAllocatableBindingsSync(allocatables, asList, ignoreList);
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (ReferenceInfo<Allocatable> allocRef : bindings.keySet())
        {
            Collection<Appointment> apps = bindings.get(allocRef);
            if (apps == null)
            {
                apps = Collections.emptyList();
            }
            ArrayList<String> indexArray = new ArrayList<>(apps.size());
            for (Appointment app : apps)
            {
                for (Appointment app2 : appointments)
                {
                    if (app2.equals(app))
                    {
                        indexArray.add(app.getId());
                    }
                }
            }
            result.put(allocRef.getId(), indexArray);
        }
        return new BindingMap(result);
    }

    @Override
    public List<ReservationImpl> getAllAllocatableBindings(AllocatableBindingsRequest job) throws RaplaException
    {
        String[] allocatableIds = job.getAllocatableIds();
        List<AppointmentImpl> appointments = job.getAppointments();
        String[] reservationIds = job.getReservationIds();
        checkSessionUser();
        List<Allocatable> allocatables = resolveAllocatables(allocatableIds);
        Collection<Reservation> ignoreList = resolveReservations(reservationIds);
        List<Appointment> asList = cast(appointments);
        Map<ReferenceInfo<Allocatable>, Map<Appointment, Collection<Appointment>>> bindings = syncOperator.getAllAllocatableBindingsSync(allocatables, asList, ignoreList);
        Set<ReservationImpl> result = new HashSet<>();
        for (ReferenceInfo<Allocatable> allocRef : bindings.keySet())
        {
            Map<Appointment, Collection<Appointment>> appointmentBindings = bindings.get(allocRef);
            for (Appointment app : appointmentBindings.keySet())
            {
                Collection<Appointment> bound = appointmentBindings.get(app);
                if (bound != null)
                {
                    for (Appointment appointment : bound)
                    {
                        ReservationImpl reservation = (ReservationImpl) appointment.getReservation();
                        if (reservation != null)
                        {
                            result.add(reservation);
                        }
                    }
                }
            }
        }
        return new ArrayList<>(result);
    }

    @Override
    public UpdateEvent doMerge(MergeRequest job, String lastSyncedTime) throws RaplaException
    {
        AllocatableImpl allocatable = job.getAllocatable();
        String[] allocatableIds = job.getAllocatableIds();
        final Set<ReferenceInfo<Allocatable>> allocReferences = new LinkedHashSet<>();
        final User sessionUser = checkSessionUser();
        security.checkWritePermissions(sessionUser, allocatable);
        for (final String allocId : allocatableIds)
        {
            allocReferences.add(new ReferenceInfo<>(allocId, Allocatable.class));
        }
        syncOperator.doMergeSync(allocatable, allocReferences, sessionUser);
        return refresh(lastSyncedTime);
    }

    private UpdateEvent store(UpdateEvent event) throws RaplaException
    {
        User sessionUser = checkSessionUser();
        LocalDateTime lastRefreshed = operator.getLastRefreshed();
        LocalDateTime lastSynced = event.getLastValidated();
        if (lastSynced == null)
        {
            throw new RaplaException("client sync time is missing");
        }
        if (lastSynced.isAfter(lastRefreshed))
        {
            long diff = java.time.Duration.between(lastRefreshed, lastSynced).toMillis();
            LOGGER.warn("Timestamp of client {} ms  after server ", diff);
            lastSynced = lastRefreshed;
        }
        LOGGER.info("Dispatching change for user {}", sessionUser);
        if (sessionUser != null)
        {
            event.setUserId(sessionUser.getId());
        }
        dispatch_(event);
        LOGGER.info("Change for user {} dispatched.", sessionUser);
        UpdateEvent result = updateDataManager.createUpdateEvent(sessionUser, lastSynced);
        for (PrePostDispatchProcessor processor : prePostDispatchProcessors)
        {
            processor.postProcess(sessionUser, result);
        }
        return result;
    }

    private ReservationImpl checkAndMakeReservationsAnonymous(User sessionUser, Entity entity)
    {
        ReservationImpl reservation = (ReservationImpl) entity;
        PermissionController permissionController = operator.getPermissionController();
        boolean reservationVisible = permissionController.canRead(reservation, sessionUser);
        if (!reservationVisible)
        {
            ReservationImpl clone = reservation.clone();
            DynamicType anonymousReservationType = operator.getDynamicType(StorageOperator.ANONYMOUSEVENT_TYPE);
            clone.setClassification(anonymousReservationType.newClassification());
            clone.setReadOnly();
            return clone;
        }
        return reservation;
    }

    private String getString(String key)
    {
        return getI18n().getString(key);
    }

    private I18nBundle getI18n()
    {
        return i18n;
    }

    private User checkSessionUser() throws RaplaException
    {
        return session.checkAndGetUser(request);
    }

    private void dispatch_(UpdateEvent evt) throws RaplaException
    {
        try
        {
            User user;
            if (evt.getUserId() != null)
            {
                user = operator.resolve(evt.getUserId(), User.class);
            }
            else
            {
                user = checkSessionUser();
            }
            Collection<Entity> storeObjects = evt.getStoreObjects();
            EntityStore store = new EntityStore(operator);
            store.addAll(storeObjects);
            for (EntityReferencer references : evt.getEntityReferences())
            {
                references.setResolver(store);
            }
            for (Entity entity : storeObjects)
            {
                security.checkWritePermissions(user, entity);
            }
            List<PreferencePatch> preferencePatches = evt.getPreferencePatches();
            for (PreferencePatch patch : preferencePatches)
            {
                security.checkWritePermissions(user, patch);
            }
            Collection<ReferenceInfo> removeObjects = evt.getRemoveIds();
            for (ReferenceInfo id : removeObjects)
            {
                Entity entity = operator.tryResolve(id);
                if (entity != null)
                {
                    security.checkDeletePermissions(user, entity);
                }
            }

            LOGGER.debug("Processing plugin-update processors ");
            for (PrePostDispatchProcessor processor : prePostDispatchProcessors)
            {
                processor.preProcess(user, evt);
            }
            LOGGER.debug("Dispatching changes to {}", operator.getClass());

            operator.dispatch(evt);
            LOGGER.debug("Changes dispatched returning result.");
        }
        catch (DependencyException ex)
        {
            throw ex;
        }
        catch (RaplaNewVersionException ex)
        {
            throw ex;
        }
        catch (RaplaSecurityException ex)
        {
            LOGGER.warn(ex.getMessage());
            throw ex;
        }
        catch (RaplaException ex)
        {
            LOGGER.error(ex.getMessage(), ex);
            throw ex;
        }
        catch (Exception ex)
        {
            LOGGER.error(ex.getMessage(), ex);
            throw new RaplaException(ex);
        }
        catch (Error ex)
        {
            LOGGER.error(ex.getMessage(), ex);
            throw ex;
        }
    }

    private List<Appointment> cast(List<AppointmentImpl> appointments)
    {
        List<Appointment> result = new ArrayList<>(appointments.size());
        for (Appointment app : appointments)
        {
            result.add(app);
        }
        return result;
    }

    private List<Allocatable> resolveAllocatables(String[] allocatableIds) throws RaplaException
    {
        List<Allocatable> allocatables = new ArrayList<>();
        User sessionUser = checkSessionUser();
        for (String id : allocatableIds)
        {
            Allocatable entity = operator.tryResolve(id, Allocatable.class);
            if (entity != null)
            {
                allocatables.add(entity);
                security.checkRead(sessionUser, entity);
            }
        }
        return allocatables;
    }

    private Collection<Reservation> resolveReservations(String[] ignoreList)
    {
        Set<Reservation> ignoreConflictsWith = new HashSet<>();
        for (String reservationId : ignoreList)
        {
            try
            {
                Reservation entity = operator.resolve(reservationId, Reservation.class);
                ignoreConflictsWith.add(entity);
            }
            catch (EntityNotFoundException ex)
            {
                // Do nothing reservation not found and assumed new
            }
        }
        return ignoreConflictsWith;
    }
}
