package org.rapla.plugin.exchangeconnector.server;

import static org.rapla.entities.configuration.CalendarModelConfiguration.EXPORT_ENTRY;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.rapla.RaplaResources;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.TimeInterval;
import org.rapla.components.xmlbundle.CompoundI18n;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.User;
import org.rapla.entities.configuration.CalendarModelConfiguration;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.TypedComponentRole;
import org.rapla.logger.Logger;
import org.rapla.inject.Extension;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorConfig;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorConfig.ConfigReader;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorPlugin;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorResources;
import org.rapla.plugin.exchangeconnector.SyncError;
import org.rapla.plugin.exchangeconnector.SynchronizationStatus;
import org.rapla.plugin.exchangeconnector.SynchronizeResult;
import org.rapla.plugin.exchangeconnector.extensionpoints.ExchangeConfigExtensionPoint;
import org.rapla.plugin.exchangeconnector.server.SynchronizationTask.SyncStatus;
import org.rapla.plugin.exchangeconnector.server.exchange.AppointmentSynchronizer;
import org.rapla.plugin.exchangeconnector.server.exchange.EWSConnector;
import org.rapla.plugin.mail.server.MailToUserImpl;
import org.rapla.scheduler.Command;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.server.RaplaKeyStorage;
import org.rapla.server.RaplaKeyStorage.LoginInfo;
import org.rapla.server.TimeZoneConverter;
import org.rapla.server.extensionpoints.ServerExtension;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.UpdateOperation;
import org.rapla.storage.UpdateResult;

import microsoft.exchange.webservices.data.core.exception.http.HttpErrorException;

@Extension(id = ExchangeConnectorPlugin.PLUGIN_ID, provides = ServerExtension.class)
@Singleton
public class SynchronisationManager implements ServerExtension
{
    private static final long SCHEDULE_PERIOD = DateTools.MILLISECONDS_PER_HOUR * 2;
    private static final long VALID_LOCK_DURATION = DateTools.MILLISECONDS_PER_MINUTE * 10;
    private static final String EXCHANGE_LOCK_ID = "EXCHANGE";
    private static final TypedComponentRole<Boolean> RETRY_USER = new TypedComponentRole<Boolean>("org.rapla.plugin.exchangconnector.retryUser");
    private static final TypedComponentRole<Boolean> RESYNC_USER = new TypedComponentRole<Boolean>("org.rapla.plugin.exchangconnector.resyncUser");
    private static final TypedComponentRole<Boolean> PASSWORD_MAIL_USER = new TypedComponentRole<Boolean>("org.rapla.plugin.exchangconnector.passwordMailSent");
    // existing tasks in memory
    private final ExchangeAppointmentStorage appointmentStorage;
    private final AppointmentFormater appointmentFormater;
    private final RaplaFacade facade;
    private final I18nBundle i18n;
    private final Logger logger;
    private final RaplaKeyStorage keyStorage;
    private final CachableStorageOperator operator;
    private final TimeZoneConverter converter;
    private final String exchangeUrl;
    private final String exchangeTimezoneId;
    private final String exchangeAppointmentCategory;

    private final int syncPeriodPast;
    CommandScheduler scheduler;
    private final Set<ExchangeConfigExtensionPoint> configExtensions;
    private final MailToUserImpl mailToUserInterface;

    @Inject
    public SynchronisationManager(RaplaFacade facade, RaplaResources i18nRapla, ExchangeConnectorResources i18nExchange, Logger logger,
            TimeZoneConverter converter, AppointmentFormater appointmentFormater, RaplaKeyStorage keyStorage, ExchangeAppointmentStorage appointmentStorage,
            CommandScheduler scheduler, ConfigReader config, Set<ExchangeConfigExtensionPoint> configExtensions, MailToUserImpl mailToUserInterface) throws
            RaplaInitializationException
    {
        super();
        this.scheduler = scheduler;
        this.converter = converter;
        this.logger = logger;
        this.facade = facade;
        this.configExtensions = configExtensions;
        this.mailToUserInterface = mailToUserInterface;
        this.operator = (CachableStorageOperator) facade.getOperator();
        this.i18n = new CompoundI18n(i18nRapla, i18nExchange);
        this.appointmentFormater = appointmentFormater;
        this.keyStorage = keyStorage;
        //		final RaplaConfiguration config = facade.getSystemPreferences().getEntry(ExchangeConnectorConfig.EXCHANGESERVER_CONFIG, new ExchangeConn());
        //RaplaConfiguration systemConfig = facade.getSystemPreferences().getEntry( ExchangeConnectorConfig.EXCHANGESERVER_CONFIG, new RaplaConfiguration());
        exchangeUrl = config.getExchangeServerURL();
        exchangeTimezoneId = config.getExchangeTimezone();
        exchangeAppointmentCategory = config.getAppointmentCategory();
        syncPeriodPast = config.getSyncPeriodPast();

        this.appointmentStorage = appointmentStorage;

        //final Timer scheduledDownloadTimer = new Timer("ScheduledDownloadThread",true);
        //scheduledDownloadTimer.schedule(new ScheduledDownloadHandler(context, clientFacade, getLogger()), 30000, ExchangeConnectorPlugin.PULL_FREQUENCY*1000);
    }

    @Override
    public void start()
    {
        long delay = 0;
        logger.info("Scheduling Exchange synchronization tasks");
        scheduler.schedule(new RetryCommand(), delay, SCHEDULE_PERIOD);

        scheduler.schedule(() ->
            {
                final CachableStorageOperator cachableStorageOperator = (CachableStorageOperator) SynchronisationManager.this.facade.getOperator();
                Date lastUpdated = null;
                Date updatedUntil = null;
                try
                {
                    lastUpdated = cachableStorageOperator.requestLock(EXCHANGE_LOCK_ID, VALID_LOCK_DURATION);
                    final UpdateResult updateResult = cachableStorageOperator.getUpdateResult(lastUpdated);
                    synchronize(updateResult);
                    // set it as last, so update must have been successful
                    updatedUntil = updateResult.getUntil();
                }
                catch (Throwable t)
                {
                    SynchronisationManager.this.logger.debug("Error updating exchange queue");
                }
                finally
                {
                    if (lastUpdated != null)
                    {
                        cachableStorageOperator.releaseLock(EXCHANGE_LOCK_ID, updatedUntil);
                    }
                }
        }, delay, 20000);
    }

    class RetryCommand implements Command
    {
        boolean firstExecution = true;

        public void execute()
        {
            try
            {
                operator.requestLock(EXCHANGE_LOCK_ID, VALID_LOCK_DURATION);
                appointmentStorage.refresh();
                Collection<SynchronizationTask> allTasks = appointmentStorage.getAllTasks();
                Collection<SynchronizationTask> includedTasks = new ArrayList<SynchronizationTask>();
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
                SynchronisationManager.this.execute(includedTasks);
                operator.releaseLock(EXCHANGE_LOCK_ID, null);
            }
            catch (Exception ex)
            {
                logger.warn("Could not synchronize with exchange: " + ex.getMessage());
                if (logger.isDebugEnabled())
                {
                    final StringWriter sw = new StringWriter();
                    ex.printStackTrace(new PrintWriter(sw));
                    logger.debug(sw.toString());
                }
            }
        }
    }

    public void retry(User user) throws RaplaException
    {
        final Preferences userPreferences = facade.edit(facade.getPreferences(user));
        userPreferences.putEntry(RETRY_USER, true);
        facade.store(userPreferences);
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

    public void synchronizeUser(User user) throws RaplaException
    {
        final Preferences userPreferences = facade.edit(facade.getPreferences(user));
        userPreferences.putEntry(RESYNC_USER, true);
        facade.store(userPreferences);
    }

    private Collection<SynchronizationTask> updateTasksSetDelete(ReferenceInfo<Appointment> appointmentId) throws RaplaException
    {
        Collection<SynchronizationTask> result = new HashSet<SynchronizationTask>();
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
        Collection<SynchronizationTask> result = new HashSet<SynchronizationTask>();
        if (isInSyncInterval(appointment))
        {
            Collection<SynchronizationTask> taskList = appointmentStorage.getTasks(appointment.getReference());
            result.addAll(taskList);
            Collection<ReferenceInfo<User>> matchingUserIds = operator.findUsersThatExport(appointment);
            // delete all appointments that are no longer covered
            for (SynchronizationTask task : taskList)
            {
                String userId = task.getUserId();
                if (userId != null && !matchingUserIds.contains(userId) && task.getStatus() != SyncStatus.deleted)
                {
                    task.setStatus(SyncStatus.toDelete);
                }
            }
            for (ReferenceInfo<User> userId : matchingUserIds)
            {
                SynchronizationTask task = appointmentStorage.getTask(appointment, userId);
                if (task == null)
                {
                    task = appointmentStorage.createTask(appointment, userId);
                    result.add(task);
                }
                task.setStatus(SyncStatus.toUpdate);
            }
        }
        return result;
    }

    private void synchronize(UpdateResult evt) throws RaplaException
    {
        appointmentStorage.refresh();
        List<Preferences> preferencesToStore = new ArrayList<Preferences>();
        Collection<SynchronizationTask> tasks = new ArrayList<SynchronizationTask>();
        Collection<User> resynchronizeUsers = new ArrayList<>();
        //lock
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
                        tasks.addAll(result);
                    }
                }
                else if (operation instanceof UpdateResult.Add)
                {
                    Reservation newReservation = evt.getLastKnown(op.getReference());
                    for (Appointment app : newReservation.getAppointments())
                    {
                        Collection<SynchronizationTask> result = updateOrCreateTasks(app);
                        tasks.addAll(result);
                    }
                }
                else //if ( operation instanceof UpdateResult.Change)
                {
                    Reservation oldReservation = evt.getLastEntryBeforeUpdate(op.getReference());
                    Reservation newReservation = evt.getLastKnown(op.getReference());
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
                        tasks.addAll(result);
                    }
                    for (Appointment newApp : newAppointments.values())
                    {
                        // TODO check if appointment change is really necessary. should be done by a diff in AppointmentSynchronizer instead of here.
                        // reservation info could also be changed, so that all appointments should be updated
                        //                        String appId = newApp.getId();
                        //                        Appointment oldApp = oldAppointments.get( appId );
                        //                        if ( oldApp != null)
                        //                        {
                        //                            if ( oldApp.matches( newApp))
                        //                            {
                        //                                Allocatable[] oldAllocatables = oldReservation.getAllocatablesFor( oldApp);
                        //                                Allocatable[] newAllocatables = newReservation.getAllocatablesFor( newApp);
                        //                                if (Arrays.equals( oldAllocatables,  newAllocatables))
                        //                                {
                        //                                    continue;
                        //                                }
                        //                            }
                        //                        }
                        Collection<SynchronizationTask> result = updateOrCreateTasks(newApp);
                        tasks.addAll(result);
                    }
                }
            }
            // the exported calendars could have changed
            else if (raplaType == Preferences.class)
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
                    if (preferences.getEntryAsBoolean(RETRY_USER, false))
                    {
                        final ReferenceInfo<User> ownerId = preferences.getOwnerRef();
                        Collection<SynchronizationTask> existingTasks = appointmentStorage.getTasksForUser(ownerId);
                        for (SynchronizationTask task : existingTasks)
                        {
                            task.resetRetries();
                        }
                        tasks.addAll(existingTasks);
                        final User resolvedUser = operator.tryResolve(preferences.getOwnerRef());
                        if(resolvedUser != null)
                        {
                            resynchronizeUsers.add(resolvedUser);
                        }
                        savePreferences = true;
                    }
                    if (preferences.getEntryAsBoolean(RESYNC_USER, false))
                    {
                        final User user = operator.tryResolve(preferences.getOwnerRef());
                        if (user != null)
                        {
                            removeAllAppointmentsFromExchangeAndAppointmentStore(user);
                        }
                        final User resolvedUser = operator.tryResolve(preferences.getOwnerRef());
                        if(resolvedUser != null)
                        {
                            resynchronizeUsers.add(resolvedUser);
                        }
                        savePreferences = true;
                    }
                    if (savePreferences)
                    {
                        final Preferences resolve = operator.resolve(preferences.getReference());
                        final Preferences editPreferences = facade.edit(resolve);
                        editPreferences.putEntry(RESYNC_USER, false);
                        editPreferences.putEntry(RETRY_USER, false);
                        preferencesToStore.add(editPreferences);
                    }
                }
                else
                {
                    preferences = null;
                }
                if (preferences != null)
                {
                    ReferenceInfo<User> ownerId = preferences.getOwnerRef();
                    if (ownerId != null)
                    {
                        User owner = facade.getOperator().resolve(ownerId);
                        Collection<SynchronizationTask> result = updateTasksForUser(owner);
                        tasks.addAll(result);
                    }
                }
            }
            else if (raplaType == User.class)
            {
                ReferenceInfo<User> userId = operation.getReference();
                if (operation instanceof UpdateResult.Remove)
                {
                    appointmentStorage.removeTasksForUser(userId);
                }
                else if (operation instanceof UpdateResult.Change)
                {
                    User owner = facade.getOperator().tryResolve(userId);
                    if (owner != null)
                    {
                        Collection<SynchronizationTask> result = updateTasksForUser(owner);
                        tasks.addAll(result);
                    }
                }
            }
            else if (raplaType == Allocatable.class)
            {
                if (operation instanceof UpdateResult.Change)
                {
                    UpdateOperation<Allocatable> op = operation;
                    Allocatable allocatable = evt.getLastKnown(op.getReference());
                    final boolean isInternal = Classifiable.ClassifiableUtil.isInternalType(allocatable);
                    if (!isInternal)
                    {
                        final Collection<ReferenceInfo<User>> users = operator.findUsersThatExport(allocatable);
                        for (ReferenceInfo<User> userId : users)
                        {
                            User owner = facade.getOperator().tryResolve(userId);
                            if (owner != null)
                            {
                                Collection<SynchronizationTask> result = updateTasksForUser(owner);
                                tasks.addAll(result);
                            }
                        }
                    }
                }
            }
        }
        if (tasks.size() > 0)
        {
            Collection<SynchronizationTask> toRemove = Collections.emptyList();
            appointmentStorage.storeAndRemove(tasks, toRemove);
            execute(tasks);
            if(!resynchronizeUsers.isEmpty())
            {
                for (User user : resynchronizeUsers)
                {
                    final Collection<SynchronizationTask> userTasks = appointmentStorage.getTasksForUser(user.getReference());
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
                                "Error sending mail to user " + user.getUsername() + " [" + sb.toString() + "] for synchronizsation result: " + em.getMessage(),
                                em);
                    }
                }
            }
            
        }
        if (!preferencesToStore.isEmpty())
        {
            facade.storeObjects(preferencesToStore.toArray(new Entity[preferencesToStore.size()]));
        }
    }

    // is called when the calendarModel is changed (e.g. store of preferences), not when the reservation changes
    private Collection<SynchronizationTask> updateTasksForUser(User user) throws RaplaException
    {

        final ReferenceInfo<User> userId = user.getReference();
        TimeInterval syncRange = getSyncRange();

        Collection<Appointment> appointments = operator.getAppointmentsFromUserCalendarModels(userId, syncRange);
        final Collection<SynchronizationTask> result = new HashSet<SynchronizationTask>();
        Set<String> appointmentsFound = new HashSet<String>();
        Collection<SynchronizationTask> newTasksFromCalendar = new HashSet<SynchronizationTask>();
        for (Appointment app : appointments)
        {
            SynchronizationTask task = appointmentStorage.getTask(app, userId);
            appointmentsFound.add(app.getId());
            // add new appointments to the appointment store, we don't need to check for updates here, as this will be triggered by a reservation change
            if (task == null)
            {
                task = appointmentStorage.createTask(app, userId);
                newTasksFromCalendar.add(task);
            }
            //			else if ( addUpdated) // can be removed if we remove all rapla appointments via find
            //			{
            //				task.setStatus( SyncStatus.toReplace);
            //				result.add(  task );
            //			}

        }
        result.addAll(newTasksFromCalendar);

        // iterate over all existing tasks of the user
        Collection<SynchronizationTask> userTasks = appointmentStorage.getTasksForUser(userId);
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
                task.setStatus(SyncStatus.toDelete);
                result.add(task);
            }
        }

        return result;
    }

    private SynchronizeResult execute(Collection<SynchronizationTask> tasks) throws RaplaException
    {
        return execute(tasks, false);
    }

    private SynchronizeResult execute(Collection<SynchronizationTask> tasks, boolean skipNotification) throws RaplaException
    {
        SynchronizeResult result = processTasks(tasks, skipNotification);
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
        EntityResolver resolver = facade.getOperator();
        Appointment appointment = isDeleteTask ? null : resolver.tryResolve(appointmentId, Appointment.class);
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

    private Collection<SyncError> removeAllAppointmentsFromExchangeAndAppointmentStore(User user) throws RaplaException
    {
        final LoginInfo secrets = keyStorage.getSecrets(user, ExchangeConnectorServerPlugin.EXCHANGE_USER_STORAGE);
        if (secrets == null)
        {
            throw new RaplaException("Keine Benutzerkonto mit Exchange verknuepft.");
        }
        final String username = secrets.login;
        final String password = secrets.secret;
        final Collection<String> exchangeUrls = extractExchangeUrls(user);
        Collection<SyncError> result = new LinkedHashSet<SyncError>();
        for (String exchangeUrl : exchangeUrls)
        {
            Collection<String> appointments = AppointmentSynchronizer.remove(logger, exchangeUrl, username, password);
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
        ReferenceInfo<User> userId = user.getReference();
        appointmentStorage.removeTasksForUser(userId);
        return result;
    }

    private SynchronizeResult processTasks(Collection<SynchronizationTask> tasks, boolean skipNotification) throws RaplaException
    {
        final Collection<SynchronizationTask> toStore = new HashSet<SynchronizationTask>();
        final Collection<SynchronizationTask> toRemove = new HashSet<SynchronizationTask>();

        final SynchronizeResult result = new SynchronizeResult();
        final EntityResolver resolver = facade.getOperator();
        for (SynchronizationTask task : tasks)
        {
            final String userId = task.getUserId();
            final String appointmentId = task.getAppointmentId();
            final Appointment appointment;
            final User user;
            final SyncStatus beforeStatus = task.getStatus();
            try
            {
                // we don't resolve the appointment if we delete
                appointment = beforeStatus != SyncStatus.toDelete ? resolver.tryResolve(appointmentId, Appointment.class) : null;
                user = resolver.resolve(userId, User.class);
            }
            catch (EntityNotFoundException e)
            {
                logger.info("Removing synchronize " + task + " due to " + e.getMessage());
                toRemove.add(task);
                continue;
            }
            if ((beforeStatus == SyncStatus.deleted) || (appointment != null && !isInSyncInterval(appointment)))
            {
                toRemove.add(task);
                continue;
            }
            if (beforeStatus == SyncStatus.synched)
            {
                continue;
            }
            final Collection<AppointmentSynchronizer> workers;
            try
            {
                workers = createAppoinmentSynchronizer(skipNotification, task, appointment, user);
                if (workers == null)
                {
                    logger.info("User no longer connected to Exchange ");
                    toRemove.add(task);
                    continue;
                }
            }
            catch (RaplaException ex)
            {
                String message = "Internal error while processing SynchronizationTask " + task + ". Ignoring task. ";
                task.increaseRetries(message);
                logger.error(message, ex);
                continue;
            }
            try
            {
                for (AppointmentSynchronizer worker : workers)
                {
                    worker.execute();
                }
                final Preferences userPreferences = facade.getPreferences(user);
                if(userPreferences.getEntryAsBoolean(PASSWORD_MAIL_USER, false))
                {
                    final Preferences userPreferencesEdit = facade.edit(userPreferences);
                    userPreferencesEdit.putEntry(PASSWORD_MAIL_USER, false);
                    facade.store(userPreferencesEdit);
                }
            }
            catch (Exception e)
            {
                String message = e.getMessage();
                Throwable cause = e.getCause();
                if (cause != null && cause.getCause() != null)
                {
                    cause = cause.getCause();
                }
                if (cause instanceof HttpErrorException)
                {
                    int httpErrorCode = ((HttpErrorException) cause).getHttpErrorCode();
                    if (httpErrorCode == 401)
                    {
                        message = "Exchangezugriff verweigert. Ist das eingetragenen Exchange Passwort noch aktuell?";
                        final Preferences preferences = facade.getPreferences(user);
                        final Boolean mailSent = preferences.getEntryAsBoolean(PASSWORD_MAIL_USER, false);
                        if(!mailSent)
                        {
                            final Preferences editPreferences = facade.edit(preferences);
                            editPreferences.putEntry(PASSWORD_MAIL_USER, true);
                            facade.store(editPreferences);
                            try
                            {
                                mailToUserInterface.sendMail(user.getUsername(), "Rapla Exchangezugriff", message);
                            }
                            catch(Throwable me)
                            {
                                logger.error("Error sending password mail to user " + user.getUsername() + ": " + me.getMessage(), me);
                            }
                        }
                    }
                }
                if (cause instanceof IOException)
                {
                    message = "Keine Verbindung zum Exchange " + cause.getMessage();
                }

                //if ( message != null && message.indexOf("Connection not estab") >=0)

                String toString = getAppointmentMessage(task);

                if (message != null)
                {
                    message = message.replaceAll("The request failed. ", "");
                    message = message.replaceAll("The request failed.", "");
                }
                else
                {
                    message = "Synchronisierungsfehler mit exchange " + e.toString();
                }
                task.increaseRetries(message);
                result.errorMessages.add(new SyncError(toString, message));
                logger.warn("Can't synchronize " + task + " " + toString + " " + message);
                result.open++;
                toStore.add(task);

            }
            SyncStatus after = task.getStatus();
            if (after == SyncStatus.deleted && beforeStatus != SyncStatus.deleted)
            {
                toRemove.add(task);
                result.removed++;
            }
            if (after == SyncStatus.synched && beforeStatus != SyncStatus.synched)
            {
                toStore.add(task);
                result.changed++;
            }
        }
        if (!toStore.isEmpty() || !toRemove.isEmpty())
        {
            appointmentStorage.storeAndRemove(toStore, toRemove);
        }
        return result;
    }

    private Collection<AppointmentSynchronizer> createAppoinmentSynchronizer(boolean skipNotification, SynchronizationTask task, final Appointment appointment,
            final User user) throws RaplaException
    {
        final Collection<AppointmentSynchronizer> workers;
        final LoginInfo secrets = keyStorage.getSecrets(user, ExchangeConnectorServerPlugin.EXCHANGE_USER_STORAGE);
        if (secrets != null)
        {
            workers = new ArrayList<>();
            final String username = secrets.login;
            final String password = secrets.secret;
            final boolean notificationMail;
            if (skipNotification)
            {
                notificationMail = false;
            }
            else
            {
                Preferences preferences = facade.getPreferences(user);
                notificationMail = preferences.getEntryAsBoolean(ExchangeConnectorConfig.EXCHANGE_SEND_INVITATION_AND_CANCELATION,
                        ExchangeConnectorConfig.DEFAULT_EXCHANGE_SEND_INVITATION_AND_CANCELATION);
            }
            final Logger logger = this.logger.getChildLogger("exchange");
            final Locale locale = i18n.getLocale();
            final Collection<String> exchangeUrls = extractExchangeUrls(user);
            for (String exchangeUrl : exchangeUrls)
            {
                final AppointmentSynchronizer worker = new AppointmentSynchronizer(logger, converter, exchangeUrl, exchangeTimezoneId, exchangeAppointmentCategory, user, username, password,
                        notificationMail, task, appointment, locale);
                workers.add(worker);
                
            }
        }
        else
        {
            workers = null;
        }
        return workers;
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
        boolean createIfNotNull = false;
        Preferences preferences = facade.getPreferences(user, createIfNotNull);
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
                Map<String, String> newMap = new LinkedHashMap<String, String>(optionMap);
                newMap.remove(ExchangeConnectorPlugin.EXCHANGE_EXPORT);
                CalendarModelConfiguration newConfig = modelConfig.cloneWithNewOptions(newMap);
                preferences.putEntry(CalendarModelConfiguration.CONFIG_ENTRY, newConfig);
            }
        }
        Map<String, CalendarModelConfiguration> exportMap = preferences.getEntry(CalendarModelConfiguration.EXPORT_ENTRY);
        if (exportMap != null)
        {
            Map<String, CalendarModelConfiguration> newExportMap = new TreeMap<String, CalendarModelConfiguration>(exportMap);
            for (String key : exportMap.keySet())
            {
                CalendarModelConfiguration calendarModelConfiguration = exportMap.get(key);
                Map<String, String> optionMap = calendarModelConfiguration.getOptionMap();
                if (optionMap.containsKey(ExchangeConnectorPlugin.EXCHANGE_EXPORT))
                {
                    Map<String, String> newMap = new LinkedHashMap<String, String>(optionMap);
                    newMap.remove(ExchangeConnectorPlugin.EXCHANGE_EXPORT);
                    CalendarModelConfiguration newConfig = calendarModelConfiguration.cloneWithNewOptions(newMap);
                    newExportMap.put(key, newConfig);
                }
            }
            preferences.putEntry(EXPORT_ENTRY, facade.newRaplaMap(newExportMap));
        }
        facade.store(preferences);
    }

    public void testConnection(String exchangeUsername, String exchangePassword, User user) throws RaplaException
    {
        try
        {
            final Collection<String> exchangeUrls = extractExchangeUrls(user);
            for (String exchangeUrl : exchangeUrls)
            {
                final EWSConnector connector = new EWSConnector(exchangeUrl, exchangeUsername, exchangePassword, null);
                connector.test();
            }
        }
        catch (Exception e)
        {
            throw new RaplaException("Kann die Verbindung zu Exchange nicht herstellen: " + e.getMessage());
        }
    }

    private Collection<String> extractExchangeUrls(User user)
    {
        final Collection<String> exchangeConnectionUrls = new ArrayList<>();
        if (configExtensions != null)
        {
            for (ExchangeConfigExtensionPoint exchangeConfigExtension : configExtensions)
            {
                if (exchangeConfigExtension.isResponsibleFor(user))
                {
                    final String exchangeUrl = exchangeConfigExtension.getExchangeUrl(user);
                    if (exchangeUrl != null && !exchangeUrl.isEmpty())
                    {
                        exchangeConnectionUrls.add(exchangeUrl);
                    }
                }
            }
        }
        if(exchangeConnectionUrls.isEmpty())
        {
            exchangeConnectionUrls.add(exchangeUrl);
        }
        return exchangeConnectionUrls;
    }
}
