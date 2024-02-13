package org.rapla.plugin.exchangeconnector.server;

import  io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Action;
import microsoft.exchange.webservices.data.core.exception.http.HttpErrorException;
import microsoft.exchange.webservices.data.core.service.folder.CalendarFolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rapla.RaplaResources;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.TimeInterval;
import org.rapla.components.i18n.CompoundI18n;
import org.rapla.components.i18n.I18nBundle;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.User;
import org.rapla.entities.configuration.CalendarModelConfiguration;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaMap;
import org.rapla.entities.domain.*;
import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.entities.dynamictype.*;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.TypedComponentRole;
import org.rapla.inject.Extension;
import org.rapla.logger.Logger;
import org.rapla.plugin.exchangeconnector.*;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorConfig.ConfigReader;
import org.rapla.plugin.exchangeconnector.extensionpoints.ExchangeConfigExtensionPoint;
import org.rapla.plugin.exchangeconnector.server.SynchronizationTask.SyncStatus;
import org.rapla.plugin.exchangeconnector.server.exchange.AppointmentSynchronizer;
import org.rapla.plugin.exchangeconnector.server.exchange.EWSConnector;
import org.rapla.plugin.mail.server.MailToUserImpl;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.sync.SynchronizedCompletablePromise;
import org.rapla.server.RaplaKeyStorage;
import org.rapla.server.RaplaKeyStorage.LoginInfo;
import org.rapla.server.TimeZoneConverter;
import org.rapla.server.extensionpoints.ServerExtension;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.UpdateOperation;
import org.rapla.storage.UpdateResult;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.rapla.entities.configuration.CalendarModelConfiguration.EXPORT_ENTRY;

@Extension(id = ExchangeConnectorPlugin.PLUGIN_ID, provides = ServerExtension.class)
@Singleton
public class SynchronisationManager implements ServerExtension
{
    private static final long SCHEDULE_PERIOD = DateTools.MILLISECONDS_PER_MINUTE / 10;

    private static final long SCHEDULE_PERIOD_REFRESH_MAILBOXES = DateTools.MILLISECONDS_PER_MINUTE * 20;

    private static final long VALID_LOCK_DURATION = DateTools.MILLISECONDS_PER_MINUTE /30;
    private static final String EXCHANGE_LOCK_ID = "EXCHANGE";
    private static final TypedComponentRole<Boolean> REFRESH_MAILBOXES = new TypedComponentRole<>("org.rapla.plugin.exchangconnector.refreshMailboxes");
    private static final TypedComponentRole<String> RESYNC_USER = new TypedComponentRole<>("org.rapla.plugin.exchangconnector.resyncUser");
    private static final TypedComponentRole<Boolean> PASSWORD_MAIL_USER = new TypedComponentRole<>("org.rapla.plugin.exchangconnector.passwordMailSent");
    // existing tasks in memory
    private final ExchangeAppointmentStorage appointmentStorage;
    private final AppointmentFormater appointmentFormater;
    private final RaplaFacade facade;
    private final I18nBundle i18n;
    private final Logger logger;
    private final RaplaKeyStorage keyStorage;
    private final CachableStorageOperator cachableStorageOperator ;

    private final TimeZoneConverter converter;
    private final String exchangeUrl;
    private final String exchangeTimezoneId;
    private final String exchangeAppointmentCategory;

    private final int syncPeriodPast;
    CommandScheduler scheduler;
    private final Set<ExchangeConfigExtensionPoint> configExtensions;
    private final MailToUserImpl mailToUserInterface;
    Disposable schedule;
    Disposable scheduleMailboxes;
    boolean enabled;
    ShowExchangeForUser showExchangeForUser;

    Map<ReferenceInfo<User>, EWSConnector.UserConnect> connectMap = new ConcurrentHashMap<>();
    Map<ReferenceInfo<Allocatable>, SynchronizationBox> synchronizationBoxMap = new ConcurrentHashMap<>();
    @Inject
    public SynchronisationManager(RaplaFacade facade, RaplaResources i18nRapla, ExchangeConnectorResources i18nExchange, Logger logger,
                                  TimeZoneConverter converter, AppointmentFormater appointmentFormater, RaplaKeyStorage keyStorage, ExchangeAppointmentStorage appointmentStorage,
                                  CommandScheduler scheduler, ConfigReader config, Set<ExchangeConfigExtensionPoint> configExtensions, MailToUserImpl mailToUserInterface, ShowExchangeForUser showExchangeForUser) throws
            RaplaInitializationException
    {
        super();
        this.scheduler = scheduler;
        this.converter = converter;
        this.logger = logger;
        this.facade = facade;
        this.configExtensions = configExtensions;
        this.mailToUserInterface = mailToUserInterface;
        this.cachableStorageOperator = (CachableStorageOperator) facade.getOperator();
        this.i18n = new CompoundI18n(i18nRapla, i18nExchange);
        this.appointmentFormater = appointmentFormater;
        this.keyStorage = keyStorage;
        //		final RaplaConfiguration config = facade.getSystemPreferences().getEntry(ExchangeConnectorConfig.EXCHANGESERVER_CONFIG, new ExchangeConn());
        //RaplaConfiguration systemConfig = facade.getSystemPreferences().getEntry( ExchangeConnectorConfig.EXCHANGESERVER_CONFIG, new RaplaConfiguration());
        exchangeUrl = config.getExchangeServerURL();
        exchangeTimezoneId = config.getExchangeTimezone();
        exchangeAppointmentCategory = config.getAppointmentCategory();
        syncPeriodPast = config.getSyncPeriodPast();
        enabled = config.isEnabled();

        this.appointmentStorage = appointmentStorage;
        this.showExchangeForUser = showExchangeForUser;

        //final Timer scheduledDownloadTimer = new Timer("ScheduledDownloadThread",true);
        //scheduledDownloadTimer.schedule(new ScheduledDownloadHandler(context, clientFacade, getLogger()), 30000, ExchangeConnectorPlugin.PULL_FREQUENCY*1000);
    }

    @Override
    public void start()
    {
        if ( !enabled ) {
            return;
        }
        logger.info("Scheduling Exchange synchronization tasks");
        final Action synchronizeMailboxesAction = () ->
        {
            logger.info("Synchronizing mailboxes");
            Collection<User> users = cachableStorageOperator.getUsers();
            Map<String, Allocatable> allocatablesPerMailbox = getAllocatableForMailbox();
            for (User user:users) {
                try {
                    refreshMailbox(user, allocatablesPerMailbox, false);
                } catch ( Throwable e) {
                    logger.error("Aborting refresh");
                }
            }
        };
        scheduleMailboxes = scheduler.schedule(synchronizeMailboxesAction, 0, SCHEDULE_PERIOD_REFRESH_MAILBOXES);
        final Action synchronizeAction = () ->
        {
            Date lastUpdated = null;
            Date updatedUntil = null;
            try {
                lastUpdated = cachableStorageOperator.requestLock(EXCHANGE_LOCK_ID, VALID_LOCK_DURATION);
            } catch (Throwable t) {
                SynchronisationManager.this.logger.error("Can't get exchange lock. Another Process maybe blocking. Waiting until its released again ");
            }
            if (lastUpdated == null) {
                return;
            }
            try {
                final UpdateResult updateResult = cachableStorageOperator.getUpdateResult(lastUpdated);
                synchronize(updateResult);
                // set it as last, so update must have been successful
                updatedUntil = updateResult.getUntil();
            } catch (Throwable t) {
                SynchronisationManager.this.logger.error("Error updating exchange queue", t);
            } finally {
                cachableStorageOperator.releaseLock(EXCHANGE_LOCK_ID, updatedUntil);
            }
        };
        schedule = scheduler.schedule(synchronizeAction, 0, SCHEDULE_PERIOD);
    }

    @Override
    public void stop() {
        if ( scheduleMailboxes != null) {
            scheduleMailboxes.dispose();
        }
        if ( schedule != null) {
            schedule.dispose();
        }
    }

    boolean firstExecution = true;

    public Collection<String> refreshMailboxes(User user) throws RaplaException
    {

        LoginInfo secrets = keyStorage.getSecrets(user, ExchangeConnectorServerPlugin.EXCHANGE_USER_STORAGE);
        if ( secrets == null)
        {
            throw new RaplaException("User " + user.getUsername() + " not connected to exchange");
        }
        try
        {
            String exchangeUrl = extractExchangeUrl(user);
            String exchangeUsername = secrets.login;
            String exchangePassword = secrets.secret;
            final EWSConnector connector = new EWSConnector(exchangeUrl, exchangeUsername, exchangePassword, logger, user.getEmail());
            connector.test();
            final Preferences userPreferences = facade.edit(facade.getPreferences(user));
            userPreferences.putEntry(REFRESH_MAILBOXES, true);
            facade.store(userPreferences);
            List<String> result = loadMailboxes(connector);
            return result;
        }
        catch (Exception e)
        {
            throw new RaplaException("Kann die Verbindung zu Exchange nicht herstellen: " + e.getMessage());
        }

    }

    public Collection<String> changeUser(String exchangeUsername, String exchangePassword, User user) throws RaplaException
    {
        try
        {
            String exchangeUrl = extractExchangeUrl(user);
            final EWSConnector connector = new EWSConnector(exchangeUrl, exchangeUsername, exchangePassword, logger, user.getEmail());
            connector.test();
            logger.debug("Invoked change connection for user " + user.getUsername());
            keyStorage.storeLoginInfo( user, ExchangeConnectorServerPlugin.EXCHANGE_USER_STORAGE, exchangeUsername, exchangePassword);
            logger.info("New exchangename stored for " + user.getUsername());
            final Preferences userPreferences = facade.edit(facade.getPreferences(user));
            userPreferences.putEntry(REFRESH_MAILBOXES, true);
            facade.store(userPreferences);
            return loadMailboxes(connector);
        }
        catch (Exception e)
        {
            throw new RaplaException("Kann die Verbindung zu Exchange nicht herstellen: " + e.getMessage());
        }
    }

    @NotNull
    private static List<String> loadMailboxes(EWSConnector connector) throws Exception {
        EWSConnector.UserConnect userConnect = connector.loadMailboxes();
        List<String> result = new ArrayList<>(userConnect.getSharedMailboxes().keySet());
        result.addAll( userConnect.getErrorMessages());
        Collections.sort( result );
        return result;
    }


    public SynchronizationStatus getSynchronizationStatus(User user) throws RaplaException
    {
        SynchronizationStatus result = new SynchronizationStatus();
        LoginInfo secrets = keyStorage.getSecrets(user, ExchangeConnectorServerPlugin.EXCHANGE_USER_STORAGE);
        boolean connected = secrets != null;
        result.enabled = connected;
        result.username = secrets != null ? secrets.login : "";
        result.syncInterval = getSyncRange();
        return result;
    }

    public void synchronizeUser(User user, String mailbox) throws RaplaException
    {
        final Preferences userPreferences = facade.edit(facade.getPreferences(user));
        userPreferences.putEntry(RESYNC_USER, mailbox);
        facade.store(userPreferences);
    }

    private Collection<SynchronizationTask> updateTasksSetDelete(ReferenceInfo<Appointment> appointmentId) throws RaplaException
    {
        Collection<SynchronizationTask> result = new HashSet<>();
        Collection<SynchronizationTask> taskList = appointmentStorage.getTasks(appointmentId);
        for (SynchronizationTask task : taskList)
        {
            task.setStatus(SyncStatus.toDelete);
            result.add(task);
        }
        return result;
    }

    private Collection<SynchronizationTask> updateOrCreateTasks(Appointment appointment) throws RaplaException
    {
        Collection<SynchronizationTask> result = new HashSet<>();
        if (isInSyncInterval(appointment))
        {
            Collection<SynchronizationTask> taskList = appointmentStorage.getTasks(appointment.getReference());
            result.addAll(taskList);
            Map<String, SynchronizationBox> boxMap = appointment.getReservation().getAllocatablesFor(appointment).map(Entity::getReference).map(synchronizationBoxMap::get).filter(Objects::nonNull).collect(Collectors.toMap(SynchronizationBox::getMailboxName, Function.identity()));

            //Collection<ReferenceInfo<User>> matchingUserIds = cachableStorageOperator.findUsersThatExport(appointment);
            // delete all appointments that are no longer covered
            for (SynchronizationTask task : taskList)
            {
                String userId = task.getUserId();
                String mailboxName = task.getMailboxName();
                if (userId != null && !boxMap.containsKey(mailboxName) && task.getStatus() != SyncStatus.deleted)
                {
                    task.setStatus(SyncStatus.toDelete);
                }
            }
            for (Map.Entry<String,SynchronizationBox> entry:boxMap.entrySet()){
                String mailboxName = entry.getKey();
                SynchronizationBox box = entry.getValue();
                SynchronizationTask task = appointmentStorage.getTask(appointment, mailboxName);
                if (task == null)
                {

                    task = new SynchronizationTask(box,appointment.getReference());
                    result.add(task);
                }
                task.setStatus(SyncStatus.toUpdate);

            }
        }
        return result;
    }

    static class SynchronizationBox {


        public ReferenceInfo<User> getUserId() {
            return userId;
        }

        public void setUserId(ReferenceInfo<User> userId) {
            this.userId = userId;
        }

        public ReferenceInfo<Allocatable> getResourceId() {
            return resourceId;
        }

        public void setResourceId(ReferenceInfo<Allocatable> resourceId) {
            this.resourceId = resourceId;
        }

        public CalendarFolder getCalendarFolder() {
            return calendarFolder;
        }

        public void setCalendarFolder(CalendarFolder calendarFolder) {
            this.calendarFolder = calendarFolder;
        }

        public String getExchangeUserId() {
            return exchangeUserId;
        }

        public void setExchangeUserId(String exchangeUserId) {
            this.exchangeUserId = exchangeUserId;
        }

        public String getMailboxName() {
            return mailboxName;
        }

        public EWSConnector.UserConnect getUserConnect() {
            return userConnect;
        }
        ReferenceInfo<User> userId;
        ReferenceInfo<Allocatable> resourceId;
        CalendarFolder calendarFolder;

        String mailboxName;

        String exchangeUserId;

        EWSConnector.UserConnect userConnect;

        @Override
        public String toString() {
            return "{" +
                    "mailboxName=" + mailboxName  +
                    ", resourceId=" + resourceId.getId() +
                    ", exchangeUserId=" + exchangeUserId  +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SynchronizationBox that = (SynchronizationBox) o;
            return Objects.equals(userId, that.userId) && Objects.equals(resourceId, that.resourceId) && Objects.equals(mailboxName, that.mailboxName) && Objects.equals(exchangeUserId, that.exchangeUserId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, resourceId, mailboxName, exchangeUserId, userConnect);
        }
    }

    String getMailbox(Allocatable allocatable) {
        Classification c = allocatable.getClassification();
        String mailbox = getAttribute(c, "exchangeMailbox");
        if ( mailbox != null ){
            return mailbox;
        }
        Attribute[] attributes = c.getAttributes();
        for ( Attribute attribute: attributes) {
            String annotation = attribute.getAnnotation(AttributeAnnotations.KEY_EMAIL);
            if (Boolean.TRUE.toString().equalsIgnoreCase(annotation)) {
                Object valueForAttribute = c.getValueForAttribute(attribute);
                if ( valueForAttribute instanceof  String) {
                    mailbox = (String) valueForAttribute;
                }
            }
        }
        if ( mailbox == null ) {
            mailbox = getAttribute(c, "email");
        }
        if ( mailbox == null) {
            return  null;
        }
        return mailbox.toLowerCase();
    }


    static class UserAndMailbox {
        String mailbox;
        ReferenceInfo<User> userRef;

        public UserAndMailbox( ReferenceInfo<User> userRef, String mailbox) {
            this.mailbox = mailbox;
            this.userRef = userRef;
        }

        public String getMailbox() {
            return mailbox;
        }

        public ReferenceInfo<User> getUserRef() {
            return userRef;
        }
    }

    private void synchronize(UpdateResult evt) throws Exception
    {
        int size1 =  evt.getAddedAndChangedIds().size() + evt.getRemovedIds().size();
        if ( size1 >0) {
            SynchronisationManager.this.logger.info("Update triggered. Looking for " + size1 + " changes since " + evt.getSince() );
        } else if (firstExecution){
            SynchronisationManager.this.logger.debug("empty update since " + evt.getSince());
        } else {
            return;
        }

        // get all exchange users

        List<Preferences> preferencesToStore = new ArrayList<>();
        Collection<UserAndMailbox> resynchronizeUsers = new ArrayList<>();
        //lock
        for (UpdateOperation operation : evt.getOperations())
        {
            final Class<? extends Entity> raplaType = operation.getType();

            // the exported calendars could have changed
            if (raplaType == Preferences.class)
            {
                final Preferences preferences;
                UpdateOperation<Preferences> op = operation;
                if (operation instanceof UpdateResult.Add)
                {
                    preferences = evt.getLastKnown(op.getReference());
                }
                else if (operation instanceof UpdateResult.Change)
                {
                    preferences = evt.getLastKnown(op.getReference());
                    boolean savePreferences = false;
                    ReferenceInfo<User> userRef = preferences.getOwnerRef();
                    String mailbox = preferences.getEntryAsString(RESYNC_USER, null);
                    boolean resyncMailboxSet= (mailbox != null && !mailbox.equalsIgnoreCase("false") && ! mailbox.equalsIgnoreCase("true"));
                    if (preferences.getEntryAsBoolean(REFRESH_MAILBOXES, false) )
                    {
                        final User resolvedUser = facade.tryResolve(userRef);
                        logger.info("refresh mailbox for user  " + resolvedUser);
                        if(resolvedUser != null)
                        {
                            refreshMailbox(resolvedUser, getAllocatableForMailbox(), true);
                        }
                        savePreferences = true;
                    }
                    // used to be a boolean before
                    if ( resyncMailboxSet )
                    {
                        final User user = facade.tryResolve(userRef);
                        if(user != null)
                        {
                            final LoginInfo secrets = keyStorage.getSecrets(user, ExchangeConnectorServerPlugin.EXCHANGE_USER_STORAGE);
                            if (secrets != null)
                            {
                                logger.info("resync user  " + user + " mailbox " + mailbox);
                                resynchronizeUsers.add(new UserAndMailbox(userRef,mailbox));
                            }
                            else
                            {
                                logger.warn("Keine Benutzerkonto mit Exchange verknuepft. " + user.getUsername());
                            }
                        }
                        savePreferences = true;
                    }
                    if (savePreferences)
                    {
                        final Preferences resolve = facade.resolve(preferences.getReference());
                        final User resolvedUser = facade.tryResolve(userRef);
                        final Preferences editPreferences = facade.edit(resolve);
                        editPreferences.putEntry(RESYNC_USER, "false");
                        editPreferences.putEntry(REFRESH_MAILBOXES, false);
                        preferencesToStore.add(editPreferences);
                        logger.info("Proccessing exchange sync/refresh request for " + resolvedUser);
                    }
                }

            }
            else if (raplaType == User.class)
            {
                ReferenceInfo<User> userId = operation.getReference();
                if (operation instanceof UpdateResult.Remove)
                {
                    appointmentStorage.removeTasksForUser(userId, null);
                }
            }
            else if (raplaType == Allocatable.class)
            {
                if (operation instanceof UpdateResult.Remove)
                {
                    UpdateOperation<Allocatable> op = operation;
                    ReferenceInfo<Allocatable> reference = op.getReference();
                    SynchronizationBox synchronizationBox = synchronizationBoxMap.get(reference);
                    if (synchronizationBox != null) {
                        //allocatablesRemoved
                        // find task that export the resource and update them
                    }
                }
            }
        }
        appointmentStorage.refresh();
        Set<SynchronizationTask> allTasks = appointmentStorage.getAllTasks();
        Set<SynchronizationTask> includedTasks = new HashSet<>();
        final Date now = new Date();
        for (SynchronizationTask task : allTasks)
        {
            final int retries = task.getRetries();
            if (retries > 5 && !firstExecution)
            {
                final Date lastRetry = task.getLastRetry();
                if (lastRetry != null)
                {
                    // skip a schedule Period for the time of retries
                    if (lastRetry.getTime() > now.getTime() - (retries - 5) * SCHEDULE_PERIOD)
                    {
                        continue;
                    }
                }
            }
            includedTasks.add(task);
        }
        firstExecution = false;

        Collection<SynchronizationTask> tasks = includedTasks;
        for (UserAndMailbox userAndMailbox:resynchronizeUsers) {

            Collection<SynchronizationTask> synchronizationTasks = updateTasksForMailbox(userAndMailbox);
            // we have to replace the old tasks with the same id
            tasks.removeAll( synchronizationTasks );
            tasks.addAll(synchronizationTasks);
        }

        for (UpdateOperation operation : evt.getOperations())
        {
            final Class<? extends Entity> raplaType = operation.getType();
            if (raplaType == Reservation.class)
            {
                UpdateOperation<Reservation> op = operation;
                if (operation instanceof UpdateResult.Remove)
                {
                    Reservation current = evt.getLastEntryBeforeUpdate(op.getReference());
                    Reservation oldReservation = current;
                    for (Appointment app : oldReservation.getAppointments())
                    {
                        Collection<SynchronizationTask> result = updateTasksSetDelete(app.getReference());
                        tasks.removeAll(result);
                        tasks.addAll(result);
                    }
                    logger.info("Removing  " + oldReservation);
                }
                else if (operation instanceof UpdateResult.Add)
                {
                    Reservation newReservation = evt.getLastKnown(op.getReference());
                    for (Appointment app : newReservation.getAppointments())
                    {
                        Collection<SynchronizationTask> result = updateOrCreateTasks(app);
                        tasks.removeAll(result);
                        tasks.addAll(result);
                    }
                    logger.debug("Adding  " + newReservation);
                }
                else //if ( operation instanceof UpdateResult.Change)
                {
                    Reservation oldReservation = evt.getLastEntryBeforeUpdate(op.getReference());
                    Reservation newReservation = evt.getLastKnown(op.getReference());
                    logger.debug("changing  " + newReservation);
                    if (oldReservation == null ){
                        logger.warn("No old reservation found for  " + newReservation);
                        continue;
                    }
                    Map<String, Appointment> oldAppointments = Appointment.AppointmentUtil.idMap(oldReservation.getAppointments());
                    Map<String, Appointment> newAppointments = Appointment.AppointmentUtil.idMap(newReservation.getAppointments());
                    for (Appointment oldApp : oldAppointments.values())
                    {
                        if (newAppointments.containsKey(oldApp.getId()))
                        {
                            continue;
                        }
                        // remove all appointments that are no longer used
                        Collection<SynchronizationTask> result = updateTasksSetDelete(oldApp.getReference());
                        tasks.removeAll(result);
                        tasks.addAll(result);
                    }
                    for (Appointment newApp : newAppointments.values())
                    {
                        // TODO check if appointment change is really necessary. should be done by a diff in AppointmentSynchronizer instead of here.
                        // reservation info could also be changed, so that all appointments should be updated
                        String appId = newApp.getId();
                        Appointment oldApp = oldAppointments.get( appId );
                        if ( oldApp != null)
                        {
                            if ( oldApp.matches( newApp))
                            {
                                Set<String> oldAllocatables = ((ReservationImpl)oldReservation).getAllocatableIdsFor(oldApp.getReference()).collect(Collectors.toSet());
                                Set<String> newAllocatables = ((ReservationImpl)newReservation).getAllocatableIdsFor( newApp.getReference()).collect(Collectors.toSet());
                                if (oldAllocatables.equals( newAllocatables))
                                {
                                    Classification classification1 = oldReservation.getClassification();
                                    Classification classification2 = newReservation.getClassification();
                                    if ( classification1 != null && classification2 != null && classification1.equals(classification2)) {
                                        continue;
                                    }
                                }
                            }
                        }
                        Collection<SynchronizationTask> result = updateOrCreateTasks(newApp);
                        tasks.removeAll(result);
                        tasks.addAll(result);
                    }
                }
            }

        }
        final int size = tasks.size();
        if (size > 0)
        {
            Collection<SynchronizationTask> toRemove = Collections.emptyList();
            appointmentStorage.storeAndRemove(tasks, toRemove);
            final SynchronizeResult execute = execute(tasks );
            if (execute.changed>0 || execute.open > 0 || execute.removed> 0 || execute.errorMessages.size() > 0)
            {
                logger.info("synchronisaction result " + execute);
            }
            if(!resynchronizeUsers.isEmpty())
            {
                for (UserAndMailbox userAndMailbox : resynchronizeUsers)
                {
                    ReferenceInfo<User> userRef = userAndMailbox.getUserRef();
                    final Collection<SynchronizationTask> userTasks = appointmentStorage.getTasksForUser(userRef, userAndMailbox.getMailbox());
                    User user = facade.tryResolve( userRef);
                    if ( user == null) {
                        continue;
                    }
                    final StringBuilder sb = new StringBuilder();
                    for (SynchronizationTask synchronizationTask : userTasks)
                    {
                        final String lastError = synchronizationTask.getLastError();
                        if(lastError != null)
                        {
                            if(sb.length() == 0)
                            {
                                sb.append("Errors occured:\n");
                            }
                            sb.append(lastError);
                            sb.append("\n");
                        }
                    }
                    if(sb.length() == 0)
                    {
                        sb.append("No errors occured, all sychronized");
                    }
                    try
                    {
                        mailToUserInterface.sendMail(user.getUsername(), "Rapla Exchange synchronization", sb.toString());
                    }
                    catch(Throwable em)
                    {
                        logger.error(
                                "Error sending mail to user " + user.getUsername() + " [" + sb + "] for synchronizsation result: " + em.getMessage());
                    }
                }
            }
            
        }
        if (!preferencesToStore.isEmpty())
        {
            facade.storeObjects(preferencesToStore.toArray(new Entity[preferencesToStore.size()]));
            logger.info("Synchronizing new preferences <done>.");
        }
    }

    @NotNull
    private Map<String, Allocatable> getAllocatableForMailbox() throws RaplaException {
        Collection<Allocatable> allocatables = cachableStorageOperator.getAllocatables(null);
        Map<String,Allocatable> allocatablesPerMailbox = new ConcurrentHashMap<>();
        for ( Allocatable allocatable : allocatables) {
            String mailbox = getMailbox(allocatable);
            if ( mailbox != null) {
                allocatablesPerMailbox.put( mailbox, allocatable);
            }
        }
        return allocatablesPerMailbox;
    }

    synchronized private void refreshMailbox(User user, Map<String, Allocatable> allocatablesPerMailbox, boolean forceReplace) throws RaplaException {
        if (!showExchangeForUser.isExchangeEnabledFor(user)) {
            return;
        }

        final LoginInfo secrets = keyStorage.getSecrets(user, ExchangeConnectorServerPlugin.EXCHANGE_USER_STORAGE);
        if ( secrets == null) {
            logger.debug("No exchange secrets found for user " + user.getUsername() + ". Ignoring updates");
            return;
        }
        final String username = secrets.login;
        final String password = secrets.secret;

        final String exchangeUrl = extractExchangeUrl(user);
        EWSConnector.UserConnect userConnect;
        try {
            final Logger ewsLogger = logger.getChildLogger("webservice");
            String mailboxAddress = user.getEmail();
            EWSConnector ewsConnector = new EWSConnector(exchangeUrl, username,password , ewsLogger, mailboxAddress);
            ewsConnector.test();
            userConnect = ewsConnector.loadMailboxes();
            //userConnect = new UserConnect(ewsConnector, sharedMailboxes, username);
        } catch (Exception ex) {
            logger.error("Internal error while fetching mailboxes for  " +username + ". Ignoring task. " + ex.getMessage());
            return;
        }
        if (userConnect != null ){
            connectMap.put( user.getReference(), userConnect);
            Map<String, CalendarFolder> sharedMailboxes = userConnect.getSharedMailboxes();
            for (Map.Entry<String,CalendarFolder> entry :sharedMailboxes.entrySet()) {
                String mailbox = entry.getKey();
                CalendarFolder folder = entry.getValue();
                Allocatable allocatable = allocatablesPerMailbox.get(mailbox);
                if ( allocatable != null) {
                    ReferenceInfo<Allocatable> reference = allocatable.getReference();
                    SynchronizationBox existingBox = synchronizationBoxMap.get(reference);
                    SynchronizationBox newBox = new SynchronizationBox();
                    newBox.exchangeUserId = userConnect.getUsername();
                    newBox.calendarFolder = folder;
                    newBox.resourceId = reference;
                    newBox.userConnect = userConnect;
                    newBox.mailboxName = mailbox;
                    newBox.userId = user.getReference();
                    if (existingBox != null && !forceReplace )
                    {
                        if ( !existingBox.equals( newBox)) {
                            logger.info("Replacing mailbox for  " + allocatable.getName(null)  + " with " + newBox);
                            synchronizationBoxMap.put( reference, newBox);
                        }
                    } else {
                        synchronizationBoxMap.put( reference, newBox);
                        logger.info("Found mailbox " + newBox);
                    }
                } else {
                    // todo remove all rapla appointments
                }
            }
            Set<ReferenceInfo<Allocatable>> mailboxesToRemove = synchronizationBoxMap.values().stream().filter(box -> box.getUserId().equals(user.getReference())).filter(box -> !sharedMailboxes.keySet().contains(box.mailboxName)).map(SynchronizationBox::getResourceId).collect(Collectors.toSet());
            for ( ReferenceInfo<Allocatable> allocatableReferenceInfo: mailboxesToRemove) {
                SynchronizationBox remove = synchronizationBoxMap.remove(allocatableReferenceInfo);
                if ( remove != null) {
                    logger.info("Removing mailbox " + remove);
                }
            }
        } else {
            connectMap.remove( user.getReference());
        }

    }

    // is called when the calendarModel is changed (e.g. store of preferences), not when the reservation changes
    private Collection<SynchronizationTask> updateTasksForMailbox(UserAndMailbox userAndMailbox) throws Exception
    {
        final ReferenceInfo<User> userRef = userAndMailbox.getUserRef();
        String mailbox = userAndMailbox.getMailbox();

        Map<ReferenceInfo<Allocatable>, SynchronizationBox> boxMapForUser = synchronizationBoxMap.entrySet().stream().filter(entry -> userRef.equals(entry.getValue().getUserId()) && mailbox.equals( entry.getValue().getMailboxName())).collect(Collectors.toMap(entry -> entry.getKey(), entry -> entry.getValue()));
        Set<Allocatable> allocatables = boxMapForUser.keySet().stream().map(cachableStorageOperator::tryResolve).filter(Objects::nonNull).collect(Collectors.toSet());
        Optional<SynchronizationBox> first = boxMapForUser.values().stream().findFirst();
        if ( !first.isPresent() ) {
            return  Collections.emptyList();
        }
        SynchronizationBox firstBox = first.get();
        String mailboxName = firstBox.getMailboxName();
        EWSConnector.UserConnect userConnect = firstBox.getUserConnect();
        removeAllAppointmentsFromExchangeAndAppointmentStore(userRef,userConnect, mailboxName);
        Promise<AppointmentMapping> appointmentMappingPromise = cachableStorageOperator.queryAppointments(null, allocatables, Collections.emptyList(), null, null, null, Collections.emptyMap());

        AppointmentMapping appointmentMapping = SynchronizedCompletablePromise.waitFor(appointmentMappingPromise, 5000, logger);
        Set<Appointment> appointments = appointmentMapping.getAllAppointments();
        final Collection<SynchronizationTask> result = new HashSet<>();
        Set<String> appointmentsFound = new HashSet<>();
        Collection<SynchronizationTask> newTasksFromCalendar = new HashSet<>();
        for (Appointment app : appointments)
        {
            Reservation reservation = app.getReservation();
            if ( reservation == null) {
                continue;
            }
            Stream<Allocatable> allocatablesFor = reservation.getAllocatablesFor(app);
            allocatablesFor.filter( alloc-> boxMapForUser.containsKey(alloc.getReference())).forEach(alloc->
            {
                SynchronizationBox synchronizationBox = boxMapForUser.get(alloc.getReference());
                SynchronizationTask task = appointmentStorage.getTask(app, synchronizationBox.getMailboxName());
                if (task == null)
                {
                    task = new SynchronizationTask(synchronizationBox, app.getReference());
                } else {
                    task.setStatus(SyncStatus.toUpdate);
                }
                newTasksFromCalendar.add(task);
                appointmentsFound.add(app.getId());

            });
            // add new appointments to the appointment store, we don't need to check for updates here, as this will be triggered by a reservation change
            //			else if ( addUpdated) // can be removed if we remove all rapla appointments via find
            //			{
            //				task.setStatus( SyncStatus.toReplace);
            //				result.add(  task );
            //			}

        }
        result.addAll(newTasksFromCalendar);

        // iterate over all existing tasks of the userAndMailbox
        Collection<SynchronizationTask> userTasks = appointmentStorage.getTasksForUser(userRef, mailbox);
        //TimeInterval syncRange = getSyncRange();
        // if a calendar changes delete all the appointments that are now longer covered by the calendars
        for (SynchronizationTask task : userTasks)
        {
            String appointmentId = task.getAppointmentId();
            SyncStatus status = task.getStatus();

            // existing appointmentId in tasks not found in current calendar export
            // only add delete tasks once for each appointment
            if ((status != SynchronizationTask.SyncStatus.deleted && status != SynchronizationTask.SyncStatus.toDelete)
                    && !appointmentsFound.contains(appointmentId))
            {
                task.setStatus(SyncStatus.deleted);
                result.add(task);
            }
        }

        return result;
    }

    private SynchronizeResult execute(Collection<SynchronizationTask> tasks) throws RaplaException
    {
        SynchronizeResult result = processTasks(tasks,false);
        return result;
    }


    private String getAppointmentMessage(SynchronizationTask task)
    {
        boolean isDelete = task.status == SyncStatus.toDelete;
        String appointmentId = task.getAppointmentId();
        return getAppointmentMessage(appointmentId, isDelete);
    }

    private String getAppointmentMessage(String appointmentId, boolean isDeleteTask)
    {
        StringBuilder appointmentMessage = new StringBuilder();
        if (isDeleteTask)
        {
            appointmentMessage.append(i18n.getString("delete"));
            appointmentMessage.append(" ");
        }
        // we don't resolve the appointment if we delete
        Appointment appointment = isDeleteTask ? null : facade.tryResolve(new ReferenceInfo<Appointment>(appointmentId, Appointment.class));
        if (appointment != null)
        {
            Reservation reservation = appointment.getReservation();
            if (reservation != null)
            {
                Locale locale = i18n.getLocale();
                appointmentMessage.append(reservation.getName(locale));
            }
            appointmentMessage.append(" ");
            String shortSummary = appointmentFormater.getShortSummary(appointment);
            appointmentMessage.append(shortSummary);
        }
        else
        {
            appointmentMessage.append(i18n.getString("appointment"));
            appointmentMessage.append(" ");
            appointmentMessage.append(appointmentId);
        }
        return appointmentMessage.toString();
    }

    private Collection<SyncError> removeAllAppointmentsFromExchangeAndAppointmentStore(ReferenceInfo<User> userRef,EWSConnector.UserConnect userConnect, String mailbox) throws RaplaException
    {
        Collection<SyncError> result = new LinkedHashSet<>();
        try
        {
            CalendarFolder calendarFolder = userConnect.getSharedMailboxes().get(mailbox);
            Collection<String> appointments = AppointmentSynchronizer.remove(logger, userConnect.getEwsConnector(), calendarFolder);
            for (String errorMessage : appointments)
            {
                // appointment remove failed
                if (errorMessage != null && !errorMessage.isEmpty())
                {
                    SyncError error = new SyncError(errorMessage, errorMessage);
                    result.add(error);
                }
            }
        }
        catch (Exception ex)
        {
            logger.error(ex.getMessage(),ex);
        }
        appointmentStorage.removeTasksForUser(userRef, mailbox);
        return result;
    }

    private SynchronizeResult processTasks(Collection<SynchronizationTask> tasks, boolean skipNotification) throws RaplaException
    {
        Map<String, List<SynchronizationTask>> groups = tasks.stream().collect(Collectors.groupingBy(SynchronizationTask::getUserId));
        final Collection<SynchronizationTask> toStore = new HashSet<>();
        final Collection<SynchronizationTask> toRemove = new HashSet<>();
        final SynchronizeResult result = new SynchronizeResult();
        for ( Map.Entry<String,List<SynchronizationTask>> entry:groups.entrySet()) {
            final ReferenceInfo<User> userId = new ReferenceInfo<>(entry.getKey(), User.class);
            List<SynchronizationTask> tasksForUser = entry.getValue();
            if ( tasksForUser.isEmpty()) {
                continue;
            }
            final User user;
            try {
                // we don't resolve the appointment if we delete
                user = facade.resolve(userId);
            } catch (EntityNotFoundException e) {
                logger.info("Removing synchronize tasks for user with id  " + userId + " due to " + e.getMessage());
                toRemove.addAll(tasksForUser);
                continue;
            }
            if (!showExchangeForUser.isExchangeEnabledFor(user)) {
                logger.info("Removing synchronize task for  user " + user.getUsername() + ". He does not belong to group " + ExchangeConnectorPlugin.EXCHANGE_SYNCHRONIZATION_GROUP);
                toRemove.addAll(tasksForUser);
                continue;
            }

            final LoginInfo secrets = keyStorage.getSecrets(user, ExchangeConnectorServerPlugin.EXCHANGE_USER_STORAGE);
            if ( secrets == null) {
                logger.info("No exchange secrets found for user " + user.getUsername() + ". Ignoring updates");
                toRemove.addAll(tasksForUser);
                continue;
            }
            EWSConnector.UserConnect userConnect = connectMap.get(userId);
            if ( userConnect == null ){
                continue;
            }
            try {
                userConnect.getEwsConnector().test();
            } catch (Exception ex) {
                String message = "Internal error while processing SynchronizationTask for " +user.getUsername() + ". Ignoring task. ";
                tasksForUser.stream().forEach(t->t.increaseRetries( message));
                logger.error(message, ex);
                continue;
            }
            final boolean notificationMail;
            if (skipNotification) {
                notificationMail = false;
            } else {
                Preferences preferences = facade.getPreferences(user);
                notificationMail = preferences.getEntryAsBoolean(ExchangeConnectorConfig.EXCHANGE_SEND_INVITATION_AND_CANCELATION,
                        ExchangeConnectorConfig.DEFAULT_EXCHANGE_SEND_INVITATION_AND_CANCELATION);
            }
            for (SynchronizationTask task : tasksForUser) {
                final SyncStatus beforeStatus = task.getStatus();
                final ReferenceInfo<Appointment> appointmentId = new ReferenceInfo<>(task.getAppointmentId(), Appointment.class);
                final Appointment appointment = beforeStatus != SyncStatus.toDelete ? facade.tryResolve(appointmentId) : null;

                if ((beforeStatus == SyncStatus.deleted) || (appointment != null && !isInSyncInterval(appointment))) {
                    toRemove.add(task);
                    continue;
                }
                if (beforeStatus == SyncStatus.synched) {
                    continue;
                }
                Map<ReferenceInfo<Allocatable>,String> mailboxForResources = new HashMap<>();
                if ( appointment == null) {
                    toRemove.add(task);
                    if ( beforeStatus != SyncStatus.toDelete) {
                        continue;
                    }
                    mailboxForResources.put(new ReferenceInfo<Allocatable>(task.getResourceId(), Allocatable.class), task.getMailboxName());
                } else {
                    ((ReservationImpl) appointment.getReservation()).getAllocatablesReferences(appointment.getReference()).forEach(
                            allocRef ->
                            {
                                Allocatable allocatable = cachableStorageOperator.tryResolve(allocRef);
                                if (allocatable == null) {
                                    return;
                                }
                                String mailbox = getMailbox(allocatable);
                                if (mailbox == null) {
                                    return;
                                }
                                mailboxForResources.put(allocRef, mailbox);
                            }
                    );
                }
                if ( mailboxForResources.isEmpty()) {
                    toRemove.add(task);
                    continue;
                }
                Map<ReferenceInfo<Allocatable>, CalendarFolder> usedSharedMailboxes = new HashMap<>();
                Map<String, CalendarFolder> sharedMailboxes = userConnect.getSharedMailboxes();
                mailboxForResources.entrySet().stream()
                        .filter(x -> x.getValue() != null && sharedMailboxes.containsKey(x.getValue())).forEach(x ->
                        {
                            ReferenceInfo<Allocatable> key = x.getKey();
                            SynchronizationBox synchronizationBox = synchronizationBoxMap.get(key);
                            if (synchronizationBox == null) {
                                return;
                            }
                            CalendarFolder calendarFolder = synchronizationBox.getCalendarFolder();
                            usedSharedMailboxes.put( key, calendarFolder);
                });


                if (usedSharedMailboxes.isEmpty()) {
                    continue;
                }
                final Logger logger = this.logger.getChildLogger("exchange");
                final AppointmentSynchronizer worker = new AppointmentSynchronizer(logger, converter, exchangeTimezoneId, exchangeAppointmentCategory, user, userConnect.getEwsConnector(),
                        notificationMail, task, appointment, i18n.getLocale(), usedSharedMailboxes);

                try {
                    try {
                        worker.execute();
                    } catch (RaplaException ex) {
                        String message = "Internal error while processing SynchronizationTask " + task + ". Ignoring task. ";
                        task.increaseRetries(message);
                        if ( message.contains("Read timed out ")) {
                            logger.warn(message + ex.getMessage());
                        } else {
                            logger.error(message, ex);
                        }
                    }
                    final Preferences userPreferences = facade.getPreferences(user);
                    if (userPreferences.getEntryAsBoolean(PASSWORD_MAIL_USER, false)) {
                        final Preferences userPreferencesEdit = facade.edit(userPreferences);
                        userPreferencesEdit.putEntry(PASSWORD_MAIL_USER, false);
                        facade.store(userPreferencesEdit);
                    }
                } catch (Exception e) {
                    String message = e.getMessage();
                    Throwable cause = e.getCause();
                    if (cause != null && cause.getCause() != null) {
                        cause = cause.getCause();
                    }
                    if (cause instanceof HttpErrorException) {
                        int httpErrorCode = ((HttpErrorException) cause).getHttpErrorCode();
                        if (httpErrorCode == 401) {
                            message = "Exchangezugriff verweigert. Ist das eingetragenen Exchange Passwort noch aktuell fuer den user '" + user.getUsername() + "' ?";
                            final Preferences preferences = facade.getPreferences(user);
                            final Boolean mailSent = preferences.getEntryAsBoolean(PASSWORD_MAIL_USER, false);
                            if (!mailSent) {
                                final Preferences editPreferences = facade.edit(preferences);
                                editPreferences.putEntry(PASSWORD_MAIL_USER, true);
                                facade.store(editPreferences);
                                try {
                                    mailToUserInterface.sendMail(user.getUsername(), "Rapla Exchangezugriff", message);
                                } catch (Throwable me) {
                                    logger.error("Error sending password mail to user " + user.getUsername() + ": " + me.getMessage(), me);
                                }
                            }
                        }
                    }
                    if (cause instanceof IOException) {
                        message = "Keine Verbindung zum Exchange " + cause.getMessage();
                    }
                    String toString = getAppointmentMessage(task);
                    if (message != null) {
                        message = message.replaceAll("The request failed. ", "");
                        message = message.replaceAll("The request failed.", "");
                    } else {
                        message = "Synchronisierungsfehler mit exchange " + e;
                    }
                    task.increaseRetries(message);
                    result.errorMessages.add(new SyncError(toString, message));
                    logger.warn("Can't synchronize " + task + " " + toString + " " + message);
                    result.open++;
                    toStore.add(task);
                }
                SyncStatus after = task.getStatus();
                if (after == SyncStatus.deleted && beforeStatus != SyncStatus.deleted) {
                    toRemove.add(task);
                    result.removed++;
                }
                if (after == SyncStatus.synched && beforeStatus != SyncStatus.synched) {
                    toStore.add(task);
                    result.changed++;
                }
            }
        }
        if (!toStore.isEmpty() || !toRemove.isEmpty())
        {
            appointmentStorage.storeAndRemove(toStore, toRemove);
        }
        return result;
    }



    @Nullable
    private String getAttribute( Classification c, String attributeKey) {
        final Locale locale = i18n.getLocale();
        Attribute exchangeMailbox = c.getAttribute(attributeKey);
        if (exchangeMailbox == null)
            return null;
        String mailbox = c.getValueAsString(exchangeMailbox, locale);
        if (mailbox == null) {
            return null;
        }
        if (mailbox.isEmpty()) {
            return null;
        }
        return mailbox.toLowerCase();
    }

    private TimeInterval getSyncRange()
    {
        Date today = facade.today();
        Date start = DateTools.addDays(today, -syncPeriodPast);
        Date end = null;// DateTools.addDays(today, config.get(ExchangeConnectorConfig.SYNCING_PERIOD_FUTURE).intValue());
        return new TimeInterval(start, end);
    }

    private boolean isInSyncInterval(Appointment appointment)
    {
        final Date start = appointment.getStart();
        final TimeInterval appointmentRange = new TimeInterval(start, appointment.getMaxEnd());
        final TimeInterval syncRange = getSyncRange();
        if (!syncRange.overlaps(appointmentRange))
        {
            logger.debug("Skipping update of appointment " + appointment + " because is date of item is out of range");
            return false;
        }
        else
        {
            return true;
        }
    }

    public void removeTasksAndExports(User user) throws RaplaException
    {
        keyStorage.removeLoginInfo(user, ExchangeConnectorServerPlugin.EXCHANGE_USER_STORAGE);
        logger.info("Removed login info for " + user);
        Preferences preferences = cachableStorageOperator.getPreferences(user, false);
        if (preferences == null)
        {
            return;
        }
        preferences = facade.edit(preferences);
        CalendarModelConfiguration modelConfig = preferences.getEntry(CalendarModelConfiguration.CONFIG_ENTRY);
        if (modelConfig != null)
        {
            Map<String, String> optionMap = modelConfig.getOptionMap();
            if (optionMap.containsKey(ExchangeConnectorPlugin.EXCHANGE_EXPORT))
            {
                Map<String, String> newMap = new LinkedHashMap<>(optionMap);
                newMap.remove(ExchangeConnectorPlugin.EXCHANGE_EXPORT);
                CalendarModelConfiguration newConfig = modelConfig.cloneWithNewOptions(newMap);
                preferences.putEntry(CalendarModelConfiguration.CONFIG_ENTRY, newConfig);
            }
        }
        RaplaMap<CalendarModelConfiguration> exportMap = preferences.getEntry(CalendarModelConfiguration.EXPORT_ENTRY);
        if (exportMap != null)
        {
            boolean exchangeCalenderRemoved = false;
            Map<String, CalendarModelConfiguration> newExportMap = new TreeMap<>(exportMap.toMap());
            for (String key : exportMap.keySet())
            {
                CalendarModelConfiguration calendarModelConfiguration = exportMap.get(key);
                Map<String, String> optionMap = calendarModelConfiguration.getOptionMap();
                if (optionMap.containsKey(ExchangeConnectorPlugin.EXCHANGE_EXPORT))
                {
                    Map<String, String> newMap = new LinkedHashMap<>(optionMap);
                    newMap.remove(ExchangeConnectorPlugin.EXCHANGE_EXPORT);
                    CalendarModelConfiguration newConfig = calendarModelConfiguration.cloneWithNewOptions(newMap);
                    newExportMap.put(key, newConfig);
                    exchangeCalenderRemoved = true;
                }
            }
            if (exchangeCalenderRemoved)
            {
                preferences.putEntry(EXPORT_ENTRY, facade.newRaplaMapForMap(newExportMap));
            }
        }
        facade.store(preferences);
        logger.info("Removed exchange export infos for " + user);
    }

    private String extractExchangeUrl(User user)
    {
        if (configExtensions != null)
        {
            for (ExchangeConfigExtensionPoint exchangeConfigExtension : configExtensions)
            {
                if (exchangeConfigExtension.isResponsibleFor(user))
                {
                    final String exchangeUrl = exchangeConfigExtension.getExchangeUrl(user);
                    if (exchangeUrl != null && !exchangeUrl.isEmpty())
                    {
                        return exchangeUrl;
                    }
                }
            }
        }
        return exchangeUrl;
    }
}
