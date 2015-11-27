package org.rapla.plugin.exchangeconnector.server;

import static org.rapla.entities.configuration.CalendarModelConfiguration.EXPORT_ENTRY;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.inject.Inject;

import org.rapla.RaplaResources;
import org.rapla.components.util.Command;
import org.rapla.components.util.CommandScheduler;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.TimeInterval;
import org.rapla.components.xmlbundle.CompoundI18n;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.configuration.CalendarModelConfiguration;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.permission.PermissionController;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.ModificationListener;
import org.rapla.facade.RaplaComponent;
import org.rapla.facade.internal.CalendarModelImpl;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorConfig;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorPlugin;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorResources;
import org.rapla.plugin.exchangeconnector.SyncError;
import org.rapla.plugin.exchangeconnector.SynchronizationStatus;
import org.rapla.plugin.exchangeconnector.SynchronizeResult;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorConfig.ConfigReader;
import org.rapla.plugin.exchangeconnector.server.SynchronizationTask.SyncStatus;
import org.rapla.plugin.exchangeconnector.server.exchange.AppointmentSynchronizer;
import org.rapla.plugin.exchangeconnector.server.exchange.EWSConnector;
import org.rapla.server.RaplaKeyStorage;
import org.rapla.server.RaplaKeyStorage.LoginInfo;
import org.rapla.server.TimeZoneConverter;
import org.rapla.storage.UpdateOperation;
import org.rapla.storage.UpdateResult;

import microsoft.exchange.webservices.data.core.exception.http.HttpErrorException;

public class SynchronisationManager implements ModificationListener {
    private static final long SCHEDULE_PERIOD = DateTools.MILLISECONDS_PER_HOUR * 2;
    // existing tasks in memory
	private final ExchangeAppointmentStorage appointmentStorage;
	private final Map<String,List<CalendarModelImpl>> calendarModels = new HashMap<String,List<CalendarModelImpl>>();
	private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final AppointmentFormater appointmentFormater;
    private final ClientFacade facade;
    private final I18nBundle i18n;
    private final Logger logger;
    private final RaplaKeyStorage keyStorage;

    private final TimeZoneConverter converter;
    private final String exchangeUrl;
    private final String exchangeTimezoneId;
    private final String exchangeAppointmentCategory;
    
    private final int syncPeriodPast;
    private final PermissionController permissionController;
    
    @Inject
	public SynchronisationManager(ClientFacade facade,RaplaResources i18nRapla, ExchangeConnectorResources i18nExchange, Logger logger, TimeZoneConverter converter, AppointmentFormater appointmentFormater, RaplaKeyStorage keyStorage, ExchangeAppointmentStorage appointmentStorage, CommandScheduler scheduler, PermissionController permissionController, ConfigReader config) throws RaplaException {
		super();
		this.converter = converter;
		this.logger = logger;
		this.facade = facade;
        this.permissionController = permissionController;
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
        facade.addModificationListener(this);
        for ( User user : facade.getUsers())
        {
        	updateCalendarMap( user );
        }
        long delay =0;
		
		scheduler.schedule( new RetryCommand(), delay, SCHEDULE_PERIOD);
        //final Timer scheduledDownloadTimer = new Timer("ScheduledDownloadThread",true);
        //scheduledDownloadTimer.schedule(new ScheduledDownloadHandler(context, clientFacade, getLogger()), 30000, ExchangeConnectorPlugin.PULL_FREQUENCY*1000);
	}
	
	class RetryCommand implements Command
	{
	    boolean firstExecution = true;
	    
		public void execute()  {
		    try
		    {
    			Collection<SynchronizationTask> allTasks = appointmentStorage.getAllTasks();
    			Collection<SynchronizationTask> includedTasks = new ArrayList<SynchronizationTask>();
    			final Date now = new Date();
    			for ( SynchronizationTask task:allTasks)
    			{
    			    final int retries = task.getRetries();
    			    if ( retries > 5 && !firstExecution)
    			    {
    			        final Date lastRetry = task.getLastRetry();
    			        if ( lastRetry != null )
    			        {
    			            // skip a schedule Period for the time of retries
    			            if (lastRetry.getTime() > now.getTime() - (retries-5) * SCHEDULE_PERIOD)
    			            {
    			                continue;
    			            }
    			        }
    			    }
    			    includedTasks.add( task );
    			}
    			firstExecution = false;
    			SynchronisationManager.this.execute(includedTasks);
		    }
		    catch (Exception ex)
		    {
		        logger.error( ex.getMessage(), ex);
		    }
		}
	}
	public synchronized void dataChanged(ModificationEvent evt) throws RaplaException {
		synchronize((UpdateResult) evt);
	}
	
	ConcurrentHashMap<String, Object> synchronizer = new ConcurrentHashMap<String, Object>();
	public SynchronizeResult retry(User user) throws RaplaException  
	{
	    final Object sync = synchronizer.putIfAbsent(user.getId(), new Object());
	    synchronized (sync)
        {
	        final String userId = user.getId();
            Collection<SynchronizationTask> existingTasks = appointmentStorage.getTasksForUser(userId);
	        for (SynchronizationTask task:existingTasks)
	        {
	            task.resetRetries();
	        }
	        return execute( existingTasks);
        }
	}
	
	public SynchronizationStatus getSynchronizationStatus(User user) throws RaplaException 
	{
	    SynchronizationStatus result = new SynchronizationStatus();
        LoginInfo secrets = keyStorage.getSecrets(user, ExchangeConnectorServerPlugin.EXCHANGE_USER_STORAGE);
        boolean connected = secrets != null;
        result.enabled = connected;
        result.username = secrets != null ? secrets.login :"";
        if ( secrets != null)
        {
            final String userId = user.getId();
            Collection<SynchronizationTask> existingTasks = appointmentStorage.getTasksForUser(userId);
            for ( SynchronizationTask task:existingTasks)
            {
                SyncStatus status = task.getStatus();
                if (status.isUnsynchronized( ))
                {
                    String lastError = task.getLastError();
                    if ( lastError != null)
                    {
                        String appointmentDetail = getAppointmentMessage(task);
                        result.synchronizationErrors.add( new SyncError(appointmentDetail, lastError) );
                    }
                    result.unsynchronizedEvents++;
                }
                if (status == SyncStatus.synched)
                {
                    result.synchronizedEvents++;
                }
            }
        }
        result.syncInterval = getSyncRange();
        return result;
	}
	
	public synchronized SynchronizeResult synchronizeUser(User user) throws RaplaException  {
	    // remove old appointments
	    Collection<SyncError> removingErrors = removeAllAppointmentsFromExchangeAndAppointmentStore(user);
	    // then insert and update the new tasks
        Collection<SynchronizationTask> updateTasks = updateCalendarMap(user);
		// we skip notification on a resync
        SynchronizeResult result = execute( updateTasks, true);
        result.errorMessages.addAll( 0, removingErrors);
		return result;
    }
	
    private Collection<SynchronizationTask> updateTasksSetDelete(Appointment appointment) throws RaplaException  {
        Collection<SynchronizationTask> result = new HashSet<SynchronizationTask>();
        Collection<SynchronizationTask> taskList = appointmentStorage.getTasks( appointment);
        for (SynchronizationTask task:taskList)
        {
            task.setStatus( SyncStatus.toDelete);
            result.add( task );
        }
        return result;
    }

    private Collection<SynchronizationTask> updateOrCreateTasks(Appointment appointment) throws RaplaException  {
	    Collection<SynchronizationTask> result = new HashSet<SynchronizationTask>();
		 if ( isInSyncInterval( appointment))
         {
		     Collection<SynchronizationTask> taskList = appointmentStorage.getTasks( appointment);
             result.addAll(taskList);
		     Collection<String> matchingUserIds = findMatchingUser( appointment);
		     // delete all appointments that are no longer covered  
		     for (SynchronizationTask task:taskList)
		     {
		         String userId = task.getUserId();
		         if ( userId != null && !matchingUserIds.contains( userId) && task.getStatus() != SyncStatus.deleted)
		         {
		             task.setStatus( SyncStatus.toDelete);
		         }
		     }
			 for( String userId:matchingUserIds)
			 {
			     SynchronizationTask task = appointmentStorage.getTask( appointment,userId);
			     if ( task == null)
			     {
			         task = appointmentStorage.createTask(appointment, userId);
	                 result.add( task );
			     }
			     task.setStatus( SyncStatus.toUpdate);
			 }
         }
		return result;
	}
		
    // checks all exports if appointment is still in on of the exported calendars (check eslected resources)
    private Collection<String> findMatchingUser(Appointment appointment) throws RaplaException {
    	Set<String> result = new HashSet<String>();
		Lock lock = readLock();
		try	{
			for (String userId :calendarModels.keySet())
			{
				List<CalendarModelImpl> list = calendarModels.get(userId);
				for ( CalendarModelImpl conf:list)
				{
					if (conf.isMatchingSelectionAndFilter( appointment))
					{
						result.add( userId);
						break;
					}
				}
			}
			
		} finally {
			RaplaComponent.unlock( lock);
		}
		return result;
	}

    // checks all exports if appointment is still in on of the exported calendars (check eslected resources)
    private Collection<String> findMatchingUsers(Allocatable allocatable) throws RaplaException {
        Set<String> result = new HashSet<String>();
        Lock lock = readLock();
        try {
            for (String userId :calendarModels.keySet())
            {
                List<CalendarModelImpl> list = calendarModels.get(userId);
                for ( CalendarModelImpl conf:list)
                {
                    if (conf.getSelectedObjectsAndChildren().contains( allocatable))
                    {
                        result.add( userId);
                        break;
                    }
                }
            }
            
        } finally {
            RaplaComponent.unlock( lock);
        }
        return result;
    }

	private void synchronize(UpdateResult evt) throws RaplaException {
        Collection<SynchronizationTask> tasks = new ArrayList<SynchronizationTask>();
        
        for (UpdateOperation operation: evt.getOperations())
		{
			final RaplaType<?> raplaType = operation.getRaplaType();
            if ( raplaType ==  Reservation.TYPE )
			{
				if ( operation instanceof UpdateResult.Remove)
				{
					Entity<?> current = operation.getCurrent();
					Reservation oldReservation = (Reservation) current;
					for ( Appointment app: oldReservation.getAppointments() )
					{
						Collection<SynchronizationTask> result = updateTasksSetDelete(app);
						tasks.addAll(result);
					}
				}
				else if ( operation instanceof UpdateResult.Add)
				{
					Reservation newReservation = (Reservation) ((UpdateResult.Add) operation).getNew();
					for ( Appointment app: newReservation.getAppointments() )
					{
					    Collection<SynchronizationTask> result =  updateOrCreateTasks(app);
						tasks.addAll(result);
					}
				}
				else //if ( operation instanceof UpdateResult.Change)
				{
				    Reservation oldReservation = (Reservation) ((UpdateResult.Change) operation).getOld();
					Reservation newReservation =(Reservation) ((UpdateResult.Change) operation).getNew();
					Map<String,Appointment> oldAppointments =  Appointment.AppointmentUtil.idMap(oldReservation.getAppointments());
					Map<String,Appointment> newAppointments =  Appointment.AppointmentUtil.idMap(newReservation.getAppointments());
					for ( Appointment oldApp: oldAppointments.values())
					{
						if ( newAppointments.containsKey( oldApp.getId()))
						{
							continue;
						}
						// remove all appointments that are no longer used
						Collection<SynchronizationTask> result =  updateTasksSetDelete(oldApp);
						tasks.addAll(result);
					}
					for ( Appointment newApp: newAppointments.values())
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
						Collection<SynchronizationTask> result =  updateOrCreateTasks(newApp);
						tasks.addAll(result);
					}
				}
			}
			// the exported calendars could have changed
			else if ( raplaType ==  Preferences.TYPE )
			{
				final Preferences preferences;
				if ( operation instanceof UpdateResult.Add)
				{
					preferences = (Preferences)((UpdateResult.Add) operation).getCurrent();
				}
				else if ( operation instanceof UpdateResult.Add)
				{
					preferences = (Preferences)((UpdateResult.Change) operation).getCurrent();
				}
				else
				{
					preferences = null;
				}
				if ( preferences != null)
				{
					User owner = preferences.getOwner();
					if (owner != null)
					{
						Collection<SynchronizationTask> result = updateCalendarMap(owner);
						tasks.addAll(result);
					}
				}
			}
			else if ( raplaType ==  User.TYPE )
			{
				String userId = operation.getCurrentId();
                if (operation instanceof UpdateResult.Remove)
                {
                    Lock lock = writeLock();
                    try
                    {
                        calendarModels.remove(userId);
                    }
                    finally
                    {
                        RaplaComponent.unlock(lock);
                    }
                    appointmentStorage.removeTasksForUser(userId);
                }
                else if (operation instanceof UpdateResult.Change)
                {
                    User owner = facade.getOperator().tryResolve(userId, User.class);
                    if (owner != null)
                    {
                        Collection<SynchronizationTask> result = updateCalendarMap(owner);
                        tasks.addAll(result);
                    }
                }   
			}
			else if ( raplaType ==  Allocatable.TYPE )
			{
                if (operation instanceof UpdateResult.Change)
                {
                    Allocatable allocatable = (Allocatable) ((UpdateResult.Change)operation).getCurrent();
                    final boolean isInternal = Classifiable.ClassifiableUtil.isInternalType(allocatable);
                    if (!isInternal)
                    {
                        final Collection<String> users = findMatchingUsers(allocatable);
                        for (String userId : users)
                        {
                            User owner = facade.getOperator().tryResolve(userId, User.class);
                            if (owner != null)
                            {
                                Collection<SynchronizationTask> result = updateCalendarMap(owner);
                                tasks.addAll(result);
                            }
                        }
                    }
                }
			}
		}
        if ( tasks.size() > 0)
		{
            Collection<SynchronizationTask> toRemove = Collections.emptyList();
            appointmentStorage.storeAndRemove( tasks, toRemove);
			execute( tasks );
		}
    }

	protected Lock writeLock() throws RaplaException {
		return RaplaComponent.lock( lock.writeLock(), 60);
	}

	protected Lock readLock() throws RaplaException {
		return RaplaComponent.lock( lock.readLock(), 10);
	}

	/** this method does not update the appointments only create new or remove existing appointments.
    New exchange appointments are added if the rapla appointment is in an exported calendar view but not in exchange.  
    Exchange appointments are removed if the rapla appointment is not in an exported calendar view anymore.  
     */
    private Collection<SynchronizationTask> updateCalendarMap(User user) throws RaplaException 
    {
        final Collection<SynchronizationTask> result = new HashSet<SynchronizationTask>();
    	final boolean createIfNotNull = false;
    	final String userId = user.getId();
    	final Preferences preferences = facade.getPreferences(user, createIfNotNull);
		if ( preferences == null)
		{
		    final Lock lock = writeLock();
			try	{
				this.calendarModels.remove( userId);
			} finally {
			    RaplaComponent.unlock( lock);
			}
			return result;
		}
		final CalendarModelConfiguration modelConfig = preferences.getEntry(CalendarModelConfiguration.CONFIG_ENTRY);
		final Map<String,CalendarModelConfiguration> exportMap= preferences.getEntry(CalendarModelConfiguration.EXPORT_ENTRY);
		final List<CalendarModelConfiguration> configList = new ArrayList<CalendarModelConfiguration>();
        if ( modelConfig == null && exportMap == null)
        {
            final Lock lock = writeLock();
			try	{
				this.calendarModels.remove( userId);
			} finally {
			    RaplaComponent.unlock( lock);
			}
        	return result;
        }
        final List<CalendarModelImpl> calendarModelList = new ArrayList<CalendarModelImpl>();
        Set<String> appointmentsFound = new HashSet<String>();
        if ( modelConfig!= null)
        {
            configList.add( modelConfig);
        }
        if ( exportMap != null)
        {
            configList.addAll( exportMap.values());
        }
        // at this point configList contains all exported calendars for the user
        for ( CalendarModelConfiguration config:configList)
        {
            // is exchange export enabled in export config?
    		if ( hasExchangeExport( config))
        	{
    		    // calculate tasks depending on the current calendarModel and put all exported appointments into appointmentFound set
    		    Collection<SynchronizationTask> newTasksFromCalendar = createTasksFromCalendar(user, config, calendarModelList,appointmentsFound);
        		result.addAll(newTasksFromCalendar);
        	}
        }
        // iterate over all existing tasks of the user
        Collection<SynchronizationTask> userTasks = appointmentStorage.getTasksForUser(userId);
        //TimeInterval syncRange = getSyncRange();
        // if a calendar changes delete all the appointments that are now longer covered by the calendars
        for ( SynchronizationTask task: userTasks)
        {
            String appointmentId = task.getAppointmentId();
            SyncStatus status = task.getStatus();
            
            // existing appointmentId in tasks not found in current calendar export
            // only add delete tasks once for each appointment
            if ( (status != SynchronizationTask.SyncStatus.deleted && status != SynchronizationTask.SyncStatus.toDelete) && !appointmentsFound.contains( appointmentId) )
            {
                task.setStatus( SyncStatus.toDelete);
                result.add( task);
            }
        }
        final Lock lock = writeLock();
        try {
            if ( calendarModelList.size() > 0)
            {
                this.calendarModels.put( userId, calendarModelList);
            }
            else
            {
                this.calendarModels.remove( userId);
            }
        } finally {
            RaplaComponent.unlock( lock);
        }
		return result;
	}

    // check if filter or calendar selection changes so that we need to add or remove events from the exchange calendar
	private Collection<SynchronizationTask> createTasksFromCalendar(User user,CalendarModelConfiguration modelConfig,List<CalendarModelImpl> configList, Set<String> appointmentsFound) throws RaplaException {
		String userId = user.getId();
		
		Set<SynchronizationTask> result = new HashSet<SynchronizationTask>();
		final Locale locale = i18n.getLocale();
        CalendarModelImpl calendarModelImpl = new CalendarModelImpl(locale, user, facade, permissionController);
		Map<String, String> alternativOptions = null;
		calendarModelImpl.setConfiguration( modelConfig, alternativOptions);
		configList.add( calendarModelImpl);
		TimeInterval syncRange = getSyncRange();
		Collection<Appointment> appointments = calendarModelImpl.getAppointments(syncRange);
		for ( Appointment app:appointments)
		{
			SynchronizationTask task = appointmentStorage.getTask(app, userId);
			appointmentsFound.add( app.getId());
			// add new appointments to the appointment store, we don't need to check for updates here, as this will be triggered by a reservation change
			if ( task == null)
			{
				task = appointmentStorage.createTask(app, userId);
				result.add( task);
			} 
//			else if ( addUpdated) // can be removed if we remove all rapla appointments via find
//			{
//				task.setStatus( SyncStatus.toReplace);
//				result.add(  task );
//			}
			
		}
		return result;
	}

	private boolean hasExchangeExport(CalendarModelConfiguration modelConfig) {
		String option = modelConfig.getOptionMap().get(ExchangeConnectorPlugin.EXCHANGE_EXPORT);
		if ( option != null && option.equals("true"))
		{
			return true;
		}
		return false;
	}

	private SynchronizeResult execute(Collection<SynchronizationTask> tasks) throws RaplaException {
		return execute( tasks, false);
	}
	
	private SynchronizeResult execute(Collection<SynchronizationTask> tasks, boolean skipNotification) throws RaplaException {
		SynchronizeResult result = processTasks(tasks, skipNotification);
		return result;
	}
	
	public String getAppointmentMessage( SynchronizationTask task)
	{
        boolean isDelete = task.status == SyncStatus.toDelete;
        String appointmentId = task.getAppointmentId();
        return getAppointmentMessage(appointmentId, isDelete);
	}

    private String getAppointmentMessage(String appointmentId, boolean isDeleteTask)
    {
        StringBuilder appointmentMessage = new StringBuilder();
        if ( isDeleteTask)
        {
            appointmentMessage.append(i18n.getString("delete"));
            appointmentMessage.append(" ");
        }
        // we don't resolve the appointment if we delete
        EntityResolver resolver = facade.getOperator();
        Appointment appointment = isDeleteTask  ?  null : resolver.tryResolve( appointmentId, Appointment.class);
        if ( appointment != null)
        {
            Reservation reservation = appointment.getReservation();
            if ( reservation != null)
            {
                Locale locale = i18n.getLocale();
                appointmentMessage.append(reservation.getName( locale));
            }
            appointmentMessage.append(" ");
            String shortSummary = appointmentFormater.getShortSummary(appointment);
            appointmentMessage.append(shortSummary);
        } else {
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
        Collection<String> appointments = AppointmentSynchronizer.remove(logger, exchangeUrl, username, password);
        Collection<SyncError> result = new LinkedHashSet<SyncError>();
        for (String errorMessage: appointments)
        {
            // appointment remove failed
            if (errorMessage != null && !errorMessage.isEmpty())
            {
                SyncError error = new SyncError(errorMessage, errorMessage);
                result.add(error);
            }
        }
        String userId = user.getId();
        appointmentStorage.removeTasksForUser(userId);
        return result;
    }

	private SynchronizeResult processTasks(Collection<SynchronizationTask> tasks, boolean skipNotification) throws RaplaException {
	    final Collection<SynchronizationTask> toStore = new HashSet<SynchronizationTask>();
        final Collection<SynchronizationTask> toRemove = new HashSet<SynchronizationTask>();
        
	    final SynchronizeResult result = new SynchronizeResult();
		final EntityResolver resolver = facade.getOperator();
	    for ( SynchronizationTask task:tasks)
		{
			 final String userId = task.getUserId();
			 final String appointmentId = task.getAppointmentId();
			 final Appointment appointment;
			 final User user;
			 final SyncStatus beforeStatus = task.getStatus();
             try
			 {
			 	// we don't resolve the appointment if we delete 
				 appointment = beforeStatus != SyncStatus.toDelete  ? resolver.tryResolve( appointmentId, Appointment.class) : null;
				 user = resolver.resolve( userId, User.class);
			 } catch (EntityNotFoundException e) {
				 logger.info( "Removing synchronize " + task + " due to " + e.getMessage() );
				 toRemove.add( task);
				 continue;
			 }
			 if ( (beforeStatus == SyncStatus.deleted) || (appointment != null && !isInSyncInterval( appointment)) )
			 {
				 toRemove.add( task);
				 continue;
			 }
			 if ( beforeStatus == SyncStatus.synched)
			 {
				 continue;
			 }
			 final AppointmentSynchronizer worker; 
		     try
			 {
				 worker = createAppoinmentSynchronizer(skipNotification, task, appointment, user);
				 if ( worker == null)
				 {
				     logger.info( "User no longer connected to Exchange " );
					 toRemove.add( task);
					 continue;
				 }
			 } 
			 catch (RaplaException ex)
			 {
				 String message = "Internal error while processing SynchronizationTask " + task  +". Ignoring task. ";
				 task.increaseRetries(message);
				 logger.error( message, ex);
				 continue;
			 }
			 try
			 {
				 worker.execute();
			 } catch (Exception e) {
				 String message = e.getMessage();
				 Throwable cause = e.getCause();
				 if ( cause != null && cause.getCause() != null)
				 {
				     cause = cause.getCause();
				 }
				 if ( cause instanceof HttpErrorException)
				 {
				     int httpErrorCode = ((HttpErrorException)cause).getHttpErrorCode();
				     if ( httpErrorCode == 401)
				     {
				         message = "Exchangezugriff verweigert. Ist das eingetragenen Exchange Passwort noch aktuell?";
				     }
				 }
				 if ( cause instanceof IOException)
				 {
				     message = "Keine Verbindung zum Exchange " + cause.getMessage();
				 }
				     
				 //if ( message != null && message.indexOf("Connection not estab") >=0)
				
				 String toString = getAppointmentMessage(task);
                 
				 if ( message != null)
				 {
				     message = message.replaceAll("The request failed. ", "");
				     message = message.replaceAll("The request failed.", "");
				 }
				 else
				 {
				     message = "Synchronisierungsfehler mit exchange " + e.toString();
				 }
                 task.increaseRetries( message );
                 result.errorMessages.add(new SyncError(toString, message));
				 logger.warn( "Can't synchronize " + task + " "  + toString + " " + message);
				 result.open++;
				 toStore.add( task);

			 }
			 SyncStatus after = task.getStatus();
			 if ( after == SyncStatus.deleted && beforeStatus != SyncStatus.deleted)
			 {
		           toRemove.add( task);
                   result.removed ++;
			 }
             if ( after == SyncStatus.synched && beforeStatus != SyncStatus.synched)
             {
                 toStore.add( task);
                 result.changed ++;
             }
		}
	    appointmentStorage.storeAndRemove(toStore, toRemove);
	    return result;
	}

    private AppointmentSynchronizer createAppoinmentSynchronizer(boolean skipNotification, SynchronizationTask task, final Appointment appointment, final User user)
            throws RaplaException
    {
        final AppointmentSynchronizer worker;
        final LoginInfo secrets = keyStorage.getSecrets( user, ExchangeConnectorServerPlugin.EXCHANGE_USER_STORAGE);
        if ( secrets != null)
        {
        	 final String username = secrets.login;
        	 final String password = secrets.secret;
        	 final boolean notificationMail;
        	 if ( skipNotification)
        	 {
        		 notificationMail = false;
        	 }
        	 else
        	 {
        		 Preferences preferences = facade.getPreferences( user);
        		 notificationMail = preferences.getEntryAsBoolean( ExchangeConnectorConfig.EXCHANGE_SEND_INVITATION_AND_CANCELATION, ExchangeConnectorConfig.DEFAULT_EXCHANGE_SEND_INVITATION_AND_CANCELATION);
        	 }
        	 final Logger logger = this.logger.getChildLogger("exchange");
             final Locale locale = i18n.getLocale();
             worker = new AppointmentSynchronizer(logger, converter, exchangeUrl, exchangeTimezoneId, exchangeAppointmentCategory, user, username,password, notificationMail, task, appointment, locale);
         }
         else
         {
             worker = null;
         }
        return worker;
    }
        
    private TimeInterval getSyncRange()
    {
    	Date today = facade.today();
        Date start = DateTools.addDays(today, -syncPeriodPast
    	        );
    	Date end = null;// DateTools.addDays(today, config.get(ExchangeConnectorConfig.SYNCING_PERIOD_FUTURE).intValue());
    	return new TimeInterval(start, end);
    }

    private boolean isInSyncInterval( Appointment appointment)  {
    	final Date start = appointment.getStart();
    	final TimeInterval appointmentRange = new TimeInterval(start, appointment.getMaxEnd());
    	final TimeInterval syncRange = getSyncRange();
		if ( !syncRange.overlaps( appointmentRange))
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
		String userId = user.getId();
		appointmentStorage.removeTasksForUser( userId);
		Lock lock = writeLock();
		try	{
			this.calendarModels.remove( userId);
		} finally {
			RaplaComponent.unlock( lock);
		}
		boolean createIfNotNull = false;
		Preferences preferences = facade.getPreferences(user, createIfNotNull);
		if ( preferences == null)
		{
			return;
		}
		preferences = facade.edit( preferences);
		CalendarModelConfiguration modelConfig = preferences.getEntry(CalendarModelConfiguration.CONFIG_ENTRY);
        if ( modelConfig != null )
        {
        	Map<String, String> optionMap = modelConfig.getOptionMap();
        	if ( optionMap.containsKey(ExchangeConnectorPlugin.EXCHANGE_EXPORT))
        	{
        	    Map<String,String> newMap = new LinkedHashMap<String, String>( optionMap);
        	    newMap.remove( ExchangeConnectorPlugin.EXCHANGE_EXPORT);
        	    CalendarModelConfiguration newConfig = modelConfig.cloneWithNewOptions(newMap);
        	    preferences.putEntry( CalendarModelConfiguration.CONFIG_ENTRY, newConfig);
        	}
        }
        Map<String,CalendarModelConfiguration> exportMap= preferences.getEntry(CalendarModelConfiguration.EXPORT_ENTRY);
        if ( exportMap != null)
        {
            Map<String,CalendarModelConfiguration> newExportMap = new TreeMap<String,CalendarModelConfiguration>( exportMap);
            for ( String key:exportMap.keySet())
        	{
        		CalendarModelConfiguration calendarModelConfiguration = exportMap.get( key);
                Map<String, String> optionMap = calendarModelConfiguration.getOptionMap();
        		if ( optionMap.containsKey(ExchangeConnectorPlugin.EXCHANGE_EXPORT))
                {
                    Map<String,String> newMap = new LinkedHashMap<String, String>( optionMap);
                    newMap.remove( ExchangeConnectorPlugin.EXCHANGE_EXPORT);
                    CalendarModelConfiguration newConfig = calendarModelConfiguration.cloneWithNewOptions(newMap);
                    newExportMap.put( key, newConfig);
                }
        	}
            preferences.putEntry( EXPORT_ENTRY, facade.newRaplaMap( newExportMap ));
        }
        facade.store( preferences);
	}

	public void testConnection(String exchangeUsername, String exchangePassword) throws RaplaException {
		try {
		    final EWSConnector connector = new EWSConnector(exchangeUrl, exchangeUsername, exchangePassword, null);
			connector.test();
		} catch (Exception e) {
			throw new RaplaException("Kann die Verbindung zu Exchange nicht herstellen: " + e.getMessage());
		}
	}
}
