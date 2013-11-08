/*--------------------------------------------------------------------------*
 | Copyright (C) 2013 Christopher Kohlhaas                                  |
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
package org.rapla.facade.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;

import org.rapla.ConnectInfo;
import org.rapla.components.util.Command;
import org.rapla.components.util.CommandScheduler;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.TimeInterval;
import org.rapla.components.util.undo.CommandHistory;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.MultiLanguageName;
import org.rapla.entities.Ownable;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaMap;
import org.rapla.entities.configuration.internal.RaplaMapImpl;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Period;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.RepeatingType;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.Template;
import org.rapla.entities.domain.internal.AllocatableImpl;
import org.rapla.entities.domain.internal.AppointmentImpl;
import org.rapla.entities.domain.internal.PeriodImpl;
import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.dynamictype.internal.AttributeImpl;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.entities.internal.CategoryImpl;
import org.rapla.entities.internal.ModifiableTimestamp;
import org.rapla.entities.internal.UserImpl;
import org.rapla.entities.storage.Mementable;
import org.rapla.entities.storage.RefEntity;
import org.rapla.facade.AllocationChangeEvent;
import org.rapla.facade.AllocationChangeListener;
import org.rapla.facade.CalendarOptions;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.Conflict;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.ModificationListener;
import org.rapla.facade.PeriodModel;
import org.rapla.facade.RaplaComponent;
import org.rapla.facade.UpdateErrorListener;
import org.rapla.framework.Configuration;
import org.rapla.framework.Container;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.internal.ContextTools;
import org.rapla.framework.logger.Logger;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.StorageOperator;
import org.rapla.storage.StorageUpdateListener;
import org.rapla.storage.UpdateResult;

/**
 * This is the default implementation of the necessary Client-Facade to the
 * DB-Subsystem.
 * <p>
 * Sample configuration 1:
 * 
 * <pre>
 *    &lt;facade id="facade">
 *       &lt;store>file&lt;/store>
 *    &lt;/facade>
 * </pre>
 * 
 * </p>
 * <p>
 * The store entry contains the id of a storage-component. Storage-Components
 * are all components that implement the {@link StorageOperator} interface.
 * </p>
 */

public class FacadeImpl implements ClientFacade,StorageUpdateListener {
	protected CommandScheduler notifyQueue;
	private User workingUser = null;
	private StorageOperator operator;
	private Vector<ModificationListener> modificatonListenerList = new Vector<ModificationListener>();
	private Vector<AllocationChangeListener> allocationListenerList = new Vector<AllocationChangeListener>();
	private Vector<UpdateErrorListener> errorListenerList = new Vector<UpdateErrorListener>();
	private I18nBundle i18n;
	private PeriodModelImpl periodModel;
//	private ConflictFinder conflictFinder;
	private Vector<ModificationListener> directListenerList = new Vector<ModificationListener>();
	public CommandHistory commandHistory = new CommandHistory();

	Locale locale;
	RaplaContext context;

	Logger logger;
	
	String templateName;
	
	public FacadeImpl(RaplaContext context, Configuration config, Logger logger) throws RaplaException {
		this( context, getOperator(context, config, logger), logger);
	}

	private static StorageOperator getOperator(RaplaContext context, Configuration config, Logger logger)
			throws RaplaContextException {
		String configEntry = config.getChild("store").getValue("*");
		String storeSelector = ContextTools.resolveContext(configEntry, context ).toString();
		logger.info("Using rapladatasource " +storeSelector);
		try {
			StorageOperator operator =  context.lookup(Container.class).lookup(StorageOperator.class, storeSelector);
			return operator;
		} 
		catch (RaplaContextException ex) {
			throw new RaplaContextException("Store "
					+ storeSelector 
					+ " is not found (or could not be initialized)", ex);
		}
	}
	
	public FacadeImpl(RaplaContext context, StorageOperator operator, Logger logger) throws RaplaException {
		this.operator = operator;
		this.logger = logger;
	    i18n = context.lookup(RaplaComponent.RAPLA_RESOURCES);
		locale = context.lookup(RaplaLocale.class).getLocale();
		this.context = context;
	
		operator.addStorageUpdateListener(this);
		notifyQueue = context.lookup( CommandScheduler.class );
				//org.rapla.components.util.CommandQueue.createCommandQueue();
	}
	
	public Logger getLogger() 
	{
        return logger;
    }
	
	public StorageOperator getOperator() {
		return operator;
	}
	
	// Implementation of StorageUpdateListener.
	/**
	 * This method is called by the storage-operator, when stored objects have
	 * changed.
	 * 
	 * <strong>Caution:</strong> You must not lock the storage operator during
	 * processing of this call, because it could have been locked by the store
	 * method, causing deadlocks
	 */
	public void objectsUpdated(UpdateResult evt) {
		if (getLogger().isDebugEnabled())
			getLogger().debug("Objects updated");

    	cacheValidString = null;
		cachedReservations = null;
    	if (workingUser != null)
		{
			if ( evt.isModified( User.TYPE))
			{
				try
				{
					Iterator<User> it = operator.getObjects(User.class).iterator();
					User newUser = null;
					while (it.hasNext()) {
						User user = it.next();
						if (user.equals(workingUser)) {
						    newUser = user;
						    break;
						}
					}
					if ( newUser == null)
					{
						EntityNotFoundException ex = new EntityNotFoundException("User (" + workingUser + ") not found. Maybe it was removed.");
						fireUpdateError(ex);
						return;
					}
					else
					{
						workingUser = newUser;
					}
				}
				catch (RaplaException ex)
				{
					fireUpdateError(ex);
					return;
				}
			}
		}
				
		fireUpdateEvent(evt);
	}

	public void updateError(RaplaException ex) {
		getLogger().error(ex.getMessage(), ex);
		fireUpdateError(ex);
	}

	public void storageDisconnected(String message) {
		fireStorageDisconnected(message);
	}

	/******************************
	 * Update-module *
	 ******************************/
	public boolean isClientForServer() {
		return operator.supportsActiveMonitoring();
	}

	public void refresh() throws RaplaException {
		if (operator.supportsActiveMonitoring()) {
			operator.refresh();
		}
	}
	

	void setName(MultiLanguageName name, String to)
	{
		String currentLang = i18n.getLang();
		name.setName("en", to);
		try
		{
			// try to find a translation in the current locale
			String translation = i18n.getString( to);
			name.setName(currentLang, translation);
		}
		catch (Exception ex)
		{
			// go on, if non is found
		}
	}
	
	public void addModificationListener(ModificationListener listener) {
		modificatonListenerList.add(listener);
	}

	public void addDirectModificationListener(ModificationListener listener) 
	{
		directListenerList.add(listener);
	}
	
	public void removeModificationListener(ModificationListener listener) {
		directListenerList.remove(listener);
		modificatonListenerList.remove(listener);
	}

	private Collection<ModificationListener> getModificationListeners() {
		if (modificatonListenerList.size() == 0)
		{
			return Collections.emptyList();
		}
		synchronized (this) {
			Collection<ModificationListener> list = new ArrayList<ModificationListener>(3);
			if (periodModel != null) {
				list.add(periodModel);
			}
			Iterator<ModificationListener> it = modificatonListenerList.iterator();
			while (it.hasNext()) {
				ModificationListener listener =  it.next();
				list.add(listener);
			}
			return list;
		}
	}

	public void addAllocationChangedListener(AllocationChangeListener listener) {
		if ( operator.supportsActiveMonitoring())
		{
			throw new IllegalStateException("You can't add an allocation listener to a client facade because reservation objects are not updated");
		}
		allocationListenerList.add(listener);
	}

	public void removeAllocationChangedListener(AllocationChangeListener listener) {
		allocationListenerList.remove(listener);
	}

	private Collection<AllocationChangeListener> getAllocationChangeListeners() {
		if (allocationListenerList.size() == 0)
		{
			return Collections.emptyList();
		}
		synchronized (this) {
			Collection<AllocationChangeListener> list = new ArrayList<AllocationChangeListener>( 3);
			Iterator<AllocationChangeListener> it = allocationListenerList.iterator();
			while (it.hasNext()) {
				AllocationChangeListener listener = it.next();
				list.add(listener);
			}
			return list;
		}
	}
	
	public AllocationChangeEvent[] createAllocationChangeEvents(UpdateResult evt) {
		Logger logger = getLogger().getChildLogger("trigger.allocation");
		List<AllocationChangeEvent> triggerEvents = AllocationChangeFinder.getTriggerEvents(evt, logger);
		return triggerEvents.toArray( new AllocationChangeEvent[0]);
	}

	public void addUpdateErrorListener(UpdateErrorListener listener) {
		errorListenerList.add(listener);
	}

	public void removeUpdateErrorListener(UpdateErrorListener listener) {
		errorListenerList.remove(listener);
	}

	public UpdateErrorListener[] getUpdateErrorListeners() {
		return errorListenerList.toArray(new UpdateErrorListener[] {});
	}

	protected void fireUpdateError(RaplaException ex) {
		UpdateErrorListener[] listeners = getUpdateErrorListeners();
		for (int i = 0; i < listeners.length; i++) {
			listeners[i].updateError(ex);
		}
	}

	protected void fireStorageDisconnected(String message) {
		UpdateErrorListener[] listeners = getUpdateErrorListeners();
		for (int i = 0; i < listeners.length; i++) {
			listeners[i].disconnected(message);
		}
	}

	final class UpdateCommandAllocation implements Runnable, Command {
		Collection<AllocationChangeListener> listenerList;
		AllocationChangeEvent[] allocationChangeEvents;

		public UpdateCommandAllocation(Collection<AllocationChangeListener> allocationChangeListeners, UpdateResult evt) {
			this.listenerList= new ArrayList<AllocationChangeListener>(allocationChangeListeners);
			if ( allocationChangeListeners.size() > 0)
			{
				allocationChangeEvents = createAllocationChangeEvents(evt);
			}
		}

		public void execute() {
			run();
		}

		public void run() {
			for (AllocationChangeListener listener: listenerList) 
			{
				try {
					if (isAborting())
						return;
					if (getLogger().isDebugEnabled())
						getLogger().debug("Notifying " + listener);
					if (allocationChangeEvents.length > 0) {
						listener.changed(allocationChangeEvents);
					}
				} catch (Exception ex) {
					getLogger().error("update-exception", ex);
				}
			}
		}
	}
	
	final class UpdateCommandModification implements Runnable, Command {
		Collection<ModificationListener> listenerList;
		ModificationEvent modificationEvent;

		public UpdateCommandModification(Collection<ModificationListener> modificationListeners, UpdateResult evt) {
			this.listenerList = new ArrayList<ModificationListener>(modificationListeners);
			this.modificationEvent = evt;
		}

		public void execute() {
			run();
		}

		public void run() {
			for (ModificationListener listener: listenerList) {
				
				try {
					if (isAborting())
						return;
					if (getLogger().isDebugEnabled())
						getLogger().debug("Notifying " + listener);
					listener.dataChanged(modificationEvent);
				} catch (Exception ex) {
					getLogger().error("update-exception", ex);
				}
			}
		}

	}	/**
	 * fires update event asynchronous.
	 */
	protected void fireUpdateEvent(UpdateResult evt) {
		if (periodModel != null) {
			try {
				periodModel.update();
			} catch (RaplaException e) {
				getLogger().error("Can't update Period Model", e);
			}
		}
		{
			Collection<ModificationListener> modificationListeners = directListenerList;
			if (modificationListeners.size() > 0 ) {
				new UpdateCommandModification(modificationListeners,evt).execute(); 
			}
		}
		{
			Collection<ModificationListener> modificationListeners = getModificationListeners();
			if (modificationListeners.size() > 0 ) {
				notifyQueue.schedule(new UpdateCommandModification(modificationListeners, evt),0);
			}
			Collection<AllocationChangeListener> allocationChangeListeners = getAllocationChangeListeners();
			if (allocationChangeListeners.size() > 0) {
				notifyQueue.schedule(new UpdateCommandAllocation(allocationChangeListeners, evt),0);
			}
		}
	}

	/******************************
	 * Query-module *
	 ******************************/
	private Collection<Allocatable> getVisibleAllocatables(	ClassificationFilter[] filters) throws RaplaException {
		Collection<Allocatable> allocatables = new ArrayList<Allocatable>();
		Collection<Allocatable> objects = operator.getObjects(Allocatable.class);
		allocatables.addAll(objects);

		Iterator<Allocatable> it = allocatables.iterator();
		while (it.hasNext()) {
			Allocatable allocatable = it.next();
			if (workingUser == null || workingUser.isAdmin())
				continue;
			if (!allocatable.canRead(workingUser))
				it.remove();
		}

		removeFilteredClassifications(allocatables, filters);
		return allocatables;
	}

	int queryCounter = 0;
	private Collection<Reservation> getVisibleReservations(User user, Allocatable[] allocatables, Date start, Date end, ClassificationFilter[] reservationFilters)
			throws RaplaException {
		if ( templateName != null)
		{
			Collection<Reservation> reservations = getTemplateReservations( templateName);
			return reservations;
		}
		Collection<Reservation> reservations = new ArrayList<Reservation>();
		List<Allocatable> allocList;
		if (allocatables != null)
		{
			if ( allocatables.length == 0 )
			{
				return Collections.emptyList();
			}
			allocList = Arrays.asList( allocatables);
		}
		else
		{
			allocList = Collections.emptyList();
		}
		reservations.addAll(operator.getReservations(user,allocList, start, end));
		removeFilteredClassifications(reservations, reservationFilters);
		Iterator<Reservation> it =reservations.iterator();
		while (it.hasNext()) {
			Reservation reservation = it.next();
			String reservationTemplate = reservation.getAnnotation( Reservation.TEMPLATE);
			if ( reservationTemplate != null )
			{
				it.remove();
			}
		}

		// Category can_see = getUserGroupsCategory().getCategory(
		// Permission.GROUP_CAN_READ_EVENTS_FROM_OTHERS);
		if (getLogger().isDebugEnabled())
		{
			getLogger().debug((++queryCounter)+". Query reservation called user=" + user + " start=" +start  + " end=" + end);
		}
		return reservations;
	}

	private void removeFilteredClassifications(	Collection<? extends Classifiable> list, ClassificationFilter[] filters) {
		if (filters == null)
		{
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

	public Allocatable[] getAllocatables() throws RaplaException {
		return getAllocatables(null);
	}

	public Allocatable[] getAllocatables(ClassificationFilter[] filters) throws RaplaException {
		return getVisibleAllocatables(filters).toArray(	Allocatable.ALLOCATABLE_ARRAY);
	}

	public boolean canExchangeAllocatables(Reservation reservation) {
		try {
			Allocatable[] all = getAllocatables(null);
			User user = getUser();
			for (int i = 0; i < all.length; i++) {
				if (all[i].canModify(user)) {
					return true;
				}
			}
		} catch (RaplaException ex) {
		}
		return false;
	}

	public Preferences getPreferences() throws RaplaException {
		return getPreferences(getUser());
	}

	public Preferences getPreferences(User user) throws RaplaException {
		return operator.getPreferences(user);
	}

	public Category getSuperCategory() {
	    return  operator.getSuperCategory();
	}

	public Category getUserGroupsCategory() throws RaplaException {
		Category userGroups = getSuperCategory().getCategory(
				Permission.GROUP_CATEGORY_KEY);
		if (userGroups == null) {
			throw new RaplaException("No category '"+ Permission.GROUP_CATEGORY_KEY + "' available");
		}
		return userGroups;
	}
	
	public Collection<String> getTemplateNames() throws RaplaException
	{
		Map<String, Template> templateMap = operator.getTemplateMap();
		if ( templateMap != null)
		{
			return templateMap.keySet();
		}
		else
		{
			return Collections.emptyList();
		}
	}
	
	public Collection<Reservation> getTemplateReservations(String name) throws RaplaException
	{
		Map<String, Template> templateMap = operator.getTemplateMap();
		if (templateMap != null)
		{
			Template template = templateMap.get( name);
			return template.getReservations();
		}
		return Collections.emptyList();
	}
	
	public Reservation[] getReservations(User user, Date start, Date end,ClassificationFilter[] filters) throws RaplaException {
		return getVisibleReservations(user, null,start, end, filters).toArray(Reservation.RESERVATION_ARRAY);
	}
	
	private String cacheValidString;
	private Reservation[] cachedReservations;
	public Reservation[] getReservations(Allocatable[] allocatables,Date start, Date end) throws RaplaException {
		String cacheKey = createCacheKey( allocatables, start, end);
    	if ( cacheValidString != null && cacheValidString.equals( cacheKey) && cachedReservations != null)
    	{
    		return cachedReservations;
    	}
    	Reservation[] reservationsForAllocatable = getReservationsForAllocatable(allocatables, start, end, null);
    	cachedReservations = reservationsForAllocatable;
    	cacheValidString = cacheKey;
		return reservationsForAllocatable;
	}
	
	private String createCacheKey(Allocatable[] allocatables, Date start,
			Date end) {
		StringBuilder buf = new StringBuilder();
		if ( allocatables != null)
		{
			for ( Allocatable alloc:allocatables)
			{
				buf.append(((RefEntity<?>)alloc).getId());
				buf.append(";");
			}
		}
		else
		{
			buf.append("all_reservations;");
		}
		if ( start != null)
		{
			buf.append(start.getTime() + ";");
		}
		if ( end != null)
		{
			buf.append(end.getTime() + ";");
		}
		return buf.toString();
	}


	public Reservation[] getReservationsForAllocatable(Allocatable[] allocatables, Date start, Date end,ClassificationFilter[] reservationFilters) throws RaplaException {
		//System.gc();
		Collection<Reservation> reservations = getVisibleReservations(null,	allocatables,start, end, reservationFilters);
		return reservations.toArray(Reservation.RESERVATION_ARRAY);
	}

	public Period[] getPeriods() throws RaplaException {
		Period[] result = operator.getObjects(Period.class).toArray(Period.PERIOD_ARRAY);
		return result;
	}

	public PeriodModel getPeriodModel() throws RaplaException {
		if (periodModel == null) {
			periodModel = new PeriodModelImpl(this);
		}
		return periodModel;
	}

	public DynamicType[] getDynamicTypes(String classificationType)
			throws RaplaException {
		if (classificationType == null) {
			return operator.getObjects(DynamicType.class).toArray(DynamicType.DYNAMICTYPE_ARRAY);
		}
		ArrayList<DynamicType> result = new ArrayList<DynamicType>();
		Collection<DynamicType> collection = operator.getObjects(DynamicType.class);
		for (DynamicType type: collection) {
			if (classificationType.equals(type.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE))) {
				result.add(type);
			}
		}
		return result.toArray(DynamicType.DYNAMICTYPE_ARRAY);
	}

	public DynamicType getDynamicType(String elementKey) throws RaplaException {
		Collection<DynamicType> collection = operator.getObjects(DynamicType.class);
		for (Iterator<DynamicType> it = collection.iterator(); it.hasNext();) {
			DynamicType type = it.next();
			if (type.getElementKey().equals(elementKey))
				return type;
		}
		throw new EntityNotFoundException("No dynamictype with elementKey "
				+ elementKey);
	}

	public User[] getUsers() throws RaplaException {
		Set<User> users = new TreeSet<User>();
		users.addAll(operator.getObjects(User.class));
		User[] result = users.toArray(User.USER_ARRAY);
		return result;
	}

	public User getUser(String username) throws RaplaException {
		User user = operator.getUser(username);
		if (user == null)
			throw new EntityNotFoundException("No User with username " + username);
		return user;
	}

	  public Conflict[] getConflicts(Reservation reservation) throws RaplaException {
	    	Date today = operator.today();
	    	if ( RaplaComponent.isTemplate( reservation))
	    	{
	    		return Conflict.CONFLICT_ARRAY;
	    	}
	    	Collection<Allocatable> allocatables = Arrays.asList(reservation.getAllocatables());
            Collection<Appointment> appointments = Arrays.asList(reservation.getAppointments());
            Collection<Reservation> ignoreList = Collections.singleton( reservation );
            Map<Allocatable, Map<Appointment, Collection<Appointment>>> allocatableBindings = operator.getAllAllocatableBindings( allocatables, appointments, ignoreList);
            ArrayList<Conflict> conflictList = new ArrayList<Conflict>();
            for ( Map.Entry<Allocatable, Map<Appointment, Collection<Appointment>>> entry: allocatableBindings.entrySet() )
			{
				Allocatable allocatable= entry.getKey();
				Map<Appointment, Collection<Appointment>> appointmentMap = entry.getValue();
				for (Map.Entry<Appointment, Collection<Appointment>> appointmentEntry: appointmentMap.entrySet())
				{
					Appointment appointment = appointmentEntry.getKey();
					if ( reservation.hasAllocated( allocatable, appointment))
					{
						Collection<Appointment> conflictionAppointments = appointmentEntry.getValue();
						if ( conflictionAppointments != null)
						{
							for ( Appointment conflictingAppointment: conflictionAppointments)
							{
								
								Appointment appointment1 = appointment;
								Appointment appointment2 = conflictingAppointment;
								if (ConflictImpl.isConflict(appointment1, appointment2, today))
		                		{
		                			ConflictImpl.addConflicts(conflictList, allocatable,appointment1, appointment2);
		                		}
							}
						}
					}
				}
			}
            return conflictList.toArray(Conflict.CONFLICT_ARRAY);

	  }

	public Conflict[] getConflicts() throws RaplaException {
		final User user;
		if ( workingUser != null && !workingUser.isAdmin())
		{
			user = workingUser;
		}
		else
		{
			user = null;
		}
		Collection<Conflict> conflicts = operator.getConflicts(  user);
		if (getLogger().isDebugEnabled())
		{
			getLogger().debug("getConflits called. Returned " + conflicts.size() + " conflicts.");
		}
	
		return conflicts.toArray(new Conflict[] {});
	}
	
	public static boolean hasPermissionToAllocate( User user, Appointment appointment,Allocatable allocatable, Reservation original, Date today) {
        if ( user.isAdmin()) {
            return true;
        }
        Date start = appointment.getStart();
        Date end = appointment.getMaxEnd();
        Permission[] permissions = allocatable.getPermissions();
        for ( int i = 0; i < permissions.length; i++) 
        {
            Permission p = permissions[i];
            int accessLevel = p.getAccessLevel();
            if ( (!p.affectsUser( user )) ||  accessLevel< Permission.READ) {
                continue;
            }
            
            if ( accessLevel ==  Permission.ADMIN)
            {
                // user has the right to allocate
                return true;
            }
           
            if ( accessLevel >= Permission.ALLOCATE && p.covers( start, end, today ) ) 
            {
                return true;
            }
            if ( original == null )
            {
                continue;
            }

            // We must check if the changes of the existing appointment
            // are in a permisable timeframe (That should be allowed)

            // 1. check if appointment is old,
            // 2. check if allocatable was already assigned to the appointment
            Appointment originalAppointment = original.findAppointment( appointment );
            if ( originalAppointment == null || !original.hasAllocated( allocatable, originalAppointment))
            {
                continue;
            }

            // 3. check if the appointment has changed during
            // that time
            if ( appointment.matches( originalAppointment ) ) 
            {
                return true;
            }
            if ( accessLevel >= Permission.ALLOCATE )
            {
            	Date maxTime = DateTools.max(appointment.getMaxEnd(), originalAppointment.getMaxEnd());
                if (maxTime == null)
                {
                    maxTime = DateTools.addYears( today, 4);
                }
    
                Date minChange = appointment.getFirstDifference( originalAppointment, maxTime );
                Date maxChange = appointment.getLastDifference( originalAppointment, maxTime );
                //System.out.println ( "minChange: " + minChange + ", maxChange: " + maxChange );
    
                if ( p.covers( minChange, maxChange, today ) ) {
                    return true;
                }
            }
        }
        return false;
    }

	public boolean canEditTemplats(User user) {
		return hasGroupRights(user, Permission.GROUP_CAN_EDIT_TEMPLATES);
	}
	
	public boolean canReadReservationsFromOthers(User user) {
		return hasGroupRights(user, Permission.GROUP_CAN_READ_EVENTS_FROM_OTHERS);
	}

	protected boolean hasGroupRights(User user, String groupKey) {
		if (user == null) {
			return workingUser == null || workingUser.isAdmin();
		}
		if (user.isAdmin()) {
			return true;
		}
		try {
			Category group = getUserGroupsCategory().getCategory(	groupKey);
			if ( group == null)
			{
				return true;
			}
			return user.belongsTo(group);
		} catch (Exception ex) {
			getLogger().error("Can't get permissions!", ex);
		}
		return false;
	}
	
	public boolean canCreateReservations(User user) {
		return hasGroupRights(user, Permission.GROUP_CAN_CREATE_EVENTS);
	}

	@Deprecated
	public Allocatable[] getAllocatableBindings(Appointment forAppointment)	throws RaplaException {
		List<Allocatable> allocatableList = Arrays.asList(getAllocatables());
		List<Allocatable> result = new ArrayList<Allocatable>();
		
		Map<Allocatable, Collection<Appointment>> bindings = getAllocatableBindings( allocatableList, Collections.singletonList(forAppointment));
		for (Map.Entry<Allocatable, Collection<Appointment>> entry: bindings.entrySet())
		{
			Collection<Appointment> appointments = entry.getValue();
			if ( appointments.contains( forAppointment))
			{
				Allocatable alloc = entry.getKey();
				result.add( alloc);
			}
		}
		return result.toArray(Allocatable.ALLOCATABLE_ARRAY);

	}
	
	public Map<Allocatable,Collection<Appointment>> getAllocatableBindings(Collection<Allocatable> allocatables, Collection<Appointment> appointments) throws RaplaException {
		Collection<Reservation> ignoreList = new HashSet<Reservation>();
		if ( appointments != null)
		{
			for (Appointment app: appointments)
			{
				Reservation r = app.getReservation();
				if ( r != null)
				{
					ignoreList.add( r );
				}
			}
		}
		return operator.getFirstAllocatableBindings(allocatables, appointments, ignoreList);
	}

	public Date getNextAllocatableDate(Collection<Allocatable> allocatables,	Appointment appointment, CalendarOptions options) throws RaplaException {
		int worktimeStartMinutes = options.getWorktimeStartMinutes();
		int worktimeEndMinutes = options.getWorktimeEndMinutes();
		Integer[] excludeDays = options.getExcludeDays().toArray( new Integer[] {});
		int rowsPerHour = options.getRowsPerHour();
		Reservation reservation = appointment.getReservation();
		Collection<Reservation> ignoreList;
		if (reservation != null) 
		{
			ignoreList = Collections.singleton( reservation);
		} 
		else
		{
			ignoreList = Collections.emptyList();
		}
		return operator.getNextAllocatableDate(allocatables, appointment,ignoreList, worktimeStartMinutes, worktimeEndMinutes, excludeDays, rowsPerHour);
	}
	
	/******************************
	 * Login - Module *
	 ******************************/
	public User getUser() throws RaplaException {
		if (this.workingUser == null) {
			throw new RaplaException("no user loged in");
		}
		return this.workingUser;
	}

   public boolean login(String username, char[] password)
            throws RaplaException {
       return login( new ConnectInfo(username, password));
   }
   
   public boolean login(ConnectInfo connectInfo)
			throws RaplaException {
		try {
			if (!operator.isConnected()) {
				operator.connect( connectInfo);
			}
		} catch (RaplaSecurityException ex) {
			return false;
		} finally {
			// Clear password
//				for (int i = 0; i < password.length; i++)
//					password[i] = 0;
		}
		String username = connectInfo.getUsername();
		if  ( connectInfo.getConnectAs() != null)
		{
		    username = connectInfo.getConnectAs();
		}
		User user = operator.getUser(username);
		if (user != null) {
			this.workingUser = user;
			getLogger().info("Login " + user.getUsername());
			return true;
		} else {
		    return false;
		}
	}

	public boolean canChangePassword() {
		try {
			return operator.canChangePassword();
        } catch (RaplaException e) {
            return false;
        }
	}

	public boolean isSessionActive() {
		return (this.workingUser != null);
	}

	private boolean aborting;
	public void logout() throws RaplaException {
		
		if (this.workingUser == null )
			return;
		getLogger().info("Logout " + workingUser.getUsername());
		aborting = true;
			
		try
		{
			// now we can add it again
			this.workingUser = null;
			// we need to remove the storage update listener, because the disconnect
			// would trigger a restart otherwise
			operator.removeStorageUpdateListener(this);
			operator.disconnect();
			operator.addStorageUpdateListener(this);
		} 
		finally
		{
			aborting = false;
		}
	}
	
	private boolean isAborting() {
		return aborting || !operator.isConnected();
	}

	@SuppressWarnings("unchecked")
	public void changePassword(User user, char[] oldPassword, char[] newPassword)
			throws RaplaException {
		operator.changePassword((RefEntity<User>) user, oldPassword, newPassword);
	}

	/******************************
	 * Modification-module *
	 ******************************/
	public String getTemplateName() 
	{
		if ( templateName != null)
		{
			return templateName;
		}
		return null;
	}

	public void setTemplate(Template template)
	{
		setTemplateName( template != null ? template.getName() : null);
	}

	public void setTemplateName(String templateName) 
	{
		this.templateName = templateName;
		cachedReservations = null;
		cacheValidString = null;
		UpdateResult updateResult = new UpdateResult( workingUser);
		updateResult.setInvalidateInterval( new TimeInterval(null, null));
		fireUpdateEvent( updateResult);
	}

	public <T> RaplaMap<T> newRaplaMap(Map<String, T> map) {
		return new RaplaMapImpl<T>(map);
	}

	public <T> RaplaMap<T> newRaplaMap(Collection<T> col) {
		return new RaplaMapImpl<T>(col);
	}

	public Appointment newAppointment(Date startDate, Date endDate) throws RaplaException {
		User user = getUser();
		return newAppointment(startDate, endDate, user);
	}
	
	public Reservation newReservation() throws RaplaException 
    {
        Classification classification = getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION)[0].newClassification();
		return newReservation( classification );
    }
	
	public Reservation newReservation(Classification classification) throws RaplaException 
    {
		User user = getUser();
        return newReservation( classification,user );
    }
	
	public Reservation newReservation(Classification classification,User user) throws RaplaException 
    {
        if (!canCreateReservations( user))
        {
            throw new RaplaException("User not allowed to create events");
        }
    	Date now = operator.getCurrentTimestamp();
        ReservationImpl reservation = new ReservationImpl(now ,now );
        if ( templateName != null )
        {
        	reservation.setAnnotation(Reservation.TEMPLATE, templateName);
        }
        reservation.setClassification(classification);
        setNew(reservation, user);
        return reservation;
    }

	public Allocatable newAllocatable( Classification classification) throws RaplaException 
	{
		return newAllocatable(classification, getUser());
	}
	
	public Allocatable newAllocatable( Classification classification, User user) throws RaplaException {
        Date now = operator.getCurrentTimestamp();
        AllocatableImpl allocatable = new AllocatableImpl(now, now);
        allocatable.addPermission(allocatable.newPermission());
        
        if (!user.isAdmin()) {
            Permission permission = allocatable.newPermission();
            permission.setUser(user);
            permission.setAccessLevel(Permission.ADMIN);
            allocatable.addPermission(permission);
        }
        allocatable.setClassification(classification);
        setNew(allocatable, user);
        return allocatable;
    }

	private Classification newClassification(String classificationType)
			throws RaplaException {
		DynamicType[] dynamicTypes = getDynamicTypes(classificationType);
        DynamicType dynamicType = dynamicTypes[0];
        Classification classification = dynamicType.newClassification();
		return classification;
	}

    public Appointment newAppointment(Date startDate, Date endDate, User user) throws RaplaException {
        AppointmentImpl appointment = new AppointmentImpl(startDate, endDate);
        setNew(appointment, user);
        return appointment;
    }

	public Appointment newAppointment(Date startDate, Date endDate,	RepeatingType repeatingType, int repeatingDuration)	throws RaplaException {
		AppointmentImpl appointment = new AppointmentImpl(startDate, endDate, repeatingType, repeatingDuration);
		User user = getUser();
		setNew(appointment, user);
		return appointment;
	}

	public Allocatable newResource() throws RaplaException {
		User user = getUser();
		Classification classification = newClassification(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE);
		return newAllocatable(classification, user);
	}

	public Allocatable newPerson() throws RaplaException {
		User user = getUser();
		Classification classification = newClassification(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON);
		return newAllocatable(classification, user);
	}

	public Period newPeriod() throws RaplaException {
		PeriodImpl period = new PeriodImpl();
		Date today = today();
		period.setStart(DateTools.cutDate(today));
		period.setEnd(DateTools.fillDate(today));
		setNew(period);
		return period;
	}

	public Date today() {
		return operator.today();
	}

	public Category newCategory() throws RaplaException {
		CategoryImpl category = new CategoryImpl();
		setNew(category);
		return category;
	}

	private Attribute createStringAttribute(String key, String name) throws RaplaException {
		Attribute attribute = newAttribute(AttributeType.STRING);
		attribute.setKey(key);
		setName(attribute.getName(), name);
		return attribute;
	}

	public DynamicType newDynamicType(String classificationType) throws RaplaException {
		DynamicTypeImpl dynamicType = new DynamicTypeImpl();
		dynamicType.setAnnotation("classification-type", classificationType);
		dynamicType.setElementKey(createDynamicTypeKey(classificationType));
		setNew(dynamicType);
		if (classificationType.equals(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE)) {
			dynamicType.addAttribute(createStringAttribute("name", "name"));
			dynamicType.setAnnotation(DynamicTypeAnnotations.KEY_NAME_FORMAT,"{name}");
			dynamicType.setAnnotation(DynamicTypeAnnotations.KEY_COLORS,"automatic");
		} else if (classificationType.equals(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION)) {
			dynamicType.addAttribute(createStringAttribute("name","eventname"));
			dynamicType.setAnnotation(DynamicTypeAnnotations.KEY_NAME_FORMAT,"{name}");
			dynamicType.setAnnotation(DynamicTypeAnnotations.KEY_COLORS, null);
		} else if (classificationType.equals(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON)) {
			dynamicType.addAttribute(createStringAttribute("surname", "surname"));
			dynamicType.addAttribute(createStringAttribute("firstname", "firstname"));
			dynamicType.addAttribute(createStringAttribute("email", "email"));
			dynamicType.setAnnotation(DynamicTypeAnnotations.KEY_NAME_FORMAT, "{surname} {firstname}");
			dynamicType.setAnnotation(DynamicTypeAnnotations.KEY_COLORS, null);
		}
		return dynamicType;
	}

	public Attribute newAttribute(AttributeType attributeType)	throws RaplaException {
		AttributeImpl attribute = new AttributeImpl(attributeType);
		setNew(attribute);
		return attribute;
	}

	public User newUser() throws RaplaException {
		UserImpl user = new UserImpl();
		setNew(user);
		String[] defaultGroups = new String[] {Permission.GROUP_MODIFY_PREFERENCES_KEY,Permission.GROUP_CAN_READ_EVENTS_FROM_OTHERS,Permission.GROUP_CAN_CREATE_EVENTS};
		for ( String groupKey: defaultGroups)
		{
			Category group = getUserGroupsCategory().getCategory( groupKey);
			if (group != null) 
			{
				user.addGroup(group);
			}
		}
		return user;
	}
	
	public CalendarSelectionModel newCalendarModel(User user) throws RaplaException{
	    if ( workingUser != null && !workingUser.isAdmin() && !user.equals(workingUser))
	    {
	        throw new RaplaException("Can't create a calendar model for a different user.");
	    }
	    return new CalendarModelImpl( context, user, this);
    }

	private String createDynamicTypeKey(String classificationType)
			throws RaplaException {
		DynamicType[] dts = getDynamicTypes(classificationType);
		int max = 1;
		for (int i = 0; i < dts.length; i++) {
			String key = dts[i].getElementKey();
			int len = classificationType.length();
			if (key.indexOf(classificationType) >= 0 && key.length() > len && Character.isDigit(key.charAt(len))) {
				try {
					int value = Integer.valueOf(key.substring(len)).intValue();
					if (value >= max)
						max = value + 1;
				} catch (NumberFormatException ex) {
				}
			}
		}
		return classificationType + (max);
	}

	private void setNew(RefEntity<?> entity) throws RaplaException {
		setNew(entity, null);
	}
	
	private void setNew(RefEntity<?> entity,User user) throws RaplaException {
		setNew(Collections.singleton(entity), entity.getRaplaType(), user);
	}


	private <T extends Entity> void setNew(Collection<T> entities, RaplaType raplaType,User user)
			throws RaplaException {

		for ( T entity: entities)
		{
			if (((RefEntity<?>)entity).getSubEntities().iterator().hasNext()) {
				throw new RaplaException("The current Rapla Version doesnt support cloning entities with sub-entities. (Except reservations)");
			}
		}
		Comparable[] ids = operator.createIdentifier(raplaType, entities.size());
		int i = 0;
		for ( T uncasted: entities)
		{
			Comparable id = ids[i++];
			RefEntity<?> entity = (RefEntity<?>) uncasted;
			entity.setId(id);
			entity.setVersion(0);
			if (getLogger() != null && getLogger().isDebugEnabled()) {
				getLogger().debug("new " + entity.getId());
			}
	
			if (entity instanceof Ownable) {
				if (user == null)
					throw new RaplaException("The object " + entity + " needs an owner but user specified is null ");
				((Ownable) entity).setOwner(user);
			}
		}
	}

	public void checkReservation(Reservation reservation) throws RaplaException {
		if (reservation.getAppointments().length == 0) {
			throw new RaplaException(i18n.getString("error.no_appointment"));
		}

		if (reservation.getName(i18n.getLocale()).trim().length() == 0) {
			throw new RaplaException(i18n.getString("error.no_reservation_name"));
		}
	}

	public <T extends Entity<T>> T edit(Entity<T> obj) throws RaplaException {
		if (obj == null)
			throw new NullPointerException("Can't edit null objects");
		Collection<Entity<T>> edit = edit(Collections.singleton( obj));
		T result = edit.iterator().next().cast();
		return result;
	}
	
	public <T extends Entity<T>> Collection<Entity<T>> edit(Collection<Entity<T>> list) throws RaplaException
	{
		List<RefEntity<T>> castedList = new ArrayList<RefEntity<T>>();
		for ( Entity<T> entity:list)
		{
			castedList.add( (RefEntity<T>) entity);
		}
		Collection<RefEntity<T>> result = operator.editObjects(castedList,	workingUser);
		List<Entity<T>> castedResult = new ArrayList<Entity<T>>();
		for ( Entity<T> entity:result)
		{
			castedResult.add(  entity);
		}
		return castedResult;
	}


	@SuppressWarnings("unchecked")
	private <T extends Entity<T>> T _clone(Entity<T> obj) throws RaplaException {
		// We assume that all type preconditions are met, e.g. obj implements
		// Refentity and Memementable
		Entity<T> deepClone = ((Mementable<T>) obj).deepClone();
		T clone = deepClone.cast();

		RaplaType raplaType = clone.getRaplaType();
		if (raplaType == Appointment.TYPE) {
			// Hack for 1.6 compiler compatibility
			Object temp = clone;
			((AppointmentImpl) temp).removeParent();
		}
		if (raplaType == Category.TYPE) {
			// Hack for 1.6 compiler compatibility
			Object temp = clone;
			((CategoryImpl) temp).removeParent();
		}

		setNew((RefEntity<T>) clone, this.workingUser);
		return clone;
	}

	@SuppressWarnings("unchecked")
	public <T extends Entity<T>> T clone(Entity<T> obj) throws RaplaException {
		if (obj == null)
			throw new NullPointerException("Can't clone null objects");

		T result;
		RaplaType<T> raplaType = obj.getRaplaType();
		if (raplaType ==  Appointment.TYPE ){
			T _clone = _clone(obj);
			// Hack for 1.6 compiler compatibility
			Object temp = _clone;
			((AppointmentImpl) temp).setParent(null);
			result = _clone;
		} else if (raplaType == Reservation.TYPE) {
			// Hack for 1.6 compiler compatibility
			Object temp = obj;
			Entity<Reservation> clonedReservation = cloneReservation((Entity<Reservation>) temp);
			// Hack for 1.6 compiler compatibility
			Reservation r = clonedReservation.cast();
			if ( workingUser != null)
			{
				r.setOwner( workingUser );
			}
			if ( templateName != null )
			{
				r.setAnnotation(Reservation.TEMPLATE, templateName);
			}
			else
			{
				String originalTemplate = r.getAnnotation( Reservation.TEMPLATE);
				if (originalTemplate != null)
				{
					r.setAnnotation(Reservation.TEMPLATE_COPYOF, originalTemplate);
				}
				r.setAnnotation(Reservation.TEMPLATE, null);
			}
			// Hack for 1.6 compiler compatibility
			Object r2 =  r;
			result = (T)r2;
			
		}
		else
		{
			try {
				T _clone = _clone(obj);
				result = _clone.cast();
			} catch (ClassCastException ex) {
				throw new RaplaException("This entity can't be cloned ", ex);
			} finally {
			}
		}
		if (result instanceof ModifiableTimestamp) {
			((ModifiableTimestamp) result).setLastChanged(operator.getCurrentTimestamp());
			if (workingUser != null) {
				((ModifiableTimestamp) result).setLastChangedBy(workingUser);
			}
		}
		return result;

	}

	@SuppressWarnings({ "unchecked" })
	/** Clones a reservation and sets new ids for all appointments and the reservation itsel
	 */
	private Reservation cloneReservation(Entity<Reservation> obj)
			throws RaplaException {
		// first we do a reservation deep clone
		Reservation clone =  ((Mementable<Reservation>) obj).deepClone();
		HashMap<Allocatable, Appointment[]> restrictions = new HashMap<Allocatable, Appointment[]>();
		Allocatable[] allocatables = clone.getAllocatables();

		for (Allocatable allocatable:allocatables) {
			restrictions.put(allocatable, clone.getRestriction(allocatable));
		}

		// then we set new ids for all appointments
		Appointment[] clonedAppointments = clone.getAppointments();
		setNew(Arrays.asList(clonedAppointments),Appointment.TYPE, this.workingUser);
		
		for (Appointment clonedAppointment:clonedAppointments) {
			clone.removeAppointment(clonedAppointment);
		}

		// and now a new id for the reservation
		setNew((RefEntity<Reservation>) clone, this.workingUser);
		for (Appointment clonedAppointment:clonedAppointments) {
			clone.addAppointment(clonedAppointment);
		}

		for (Allocatable allocatable:allocatables) {
			clone.addAllocatable( allocatable);
			Appointment[] appointments = restrictions.get(allocatable);
			if (appointments != null) {
				clone.setRestriction(allocatable, appointments);
			}
		}

		return clone;
	}

	public <T> T getPersistant(Entity<T> entity) throws RaplaException {
		Set<Entity<T>> persistantList = Collections.singleton( entity);
		Map<Entity<T>,T> map = getPersistant( persistantList);
		T result = map.get( entity);
		if ( result == null)
		{
			throw new EntityNotFoundException(	"There is no persistant version of " + entity);
		}
		return result;
	}
	
	public <T> Map<Entity<T>,T> getPersistant(Collection<Entity<T>> list) throws RaplaException {
		List<RefEntity<T>> castedList = new ArrayList<RefEntity<T>>();
		for ( Entity<T> entity:list)
		{
			castedList.add( (RefEntity<T>) entity);
		}
		Map<RefEntity<T>, T> result = operator.getPersistant(castedList);
		LinkedHashMap<Entity<T>, T> castedResult = new LinkedHashMap<Entity<T>, T>();
		for ( Map.Entry<RefEntity<T>, T> entry: result.entrySet())
		{
			castedResult.put( entry.getKey(), entry.getValue());
		}
		return castedResult;
	}

	public void store(Entity<?> obj) throws RaplaException {
		if (obj == null)
			throw new NullPointerException("Can't store null objects");
		storeObjects(new Entity[] { obj });
	}

	public void remove(Entity<?> obj) throws RaplaException {
		if (obj == null)
			throw new NullPointerException("Can't remove null objects");
		removeObjects(new Entity[] { obj });
	}

	public void storeObjects(Entity<?>[] obj) throws RaplaException {
		storeAndRemove(obj, Entity.ENTITY_ARRAY);
	}

	public void removeObjects(Entity<?>[] obj) throws RaplaException {
		storeAndRemove(Entity.ENTITY_ARRAY, obj);
	}

	@SuppressWarnings("unchecked")
	public void storeAndRemove(Entity<?>[] storeObjects, Entity<?>[] removedObjects) throws RaplaException {
		if (storeObjects.length == 0 && removedObjects.length == 0)
			return;
		long time = System.currentTimeMillis();
		for (int i = 0; i < storeObjects.length; i++) {
			if (storeObjects[i] == null) {
				throw new RaplaException("Stored Objects cant be null");
			}
			if (storeObjects[i].getRaplaType() == Reservation.TYPE) {
				checkReservation((Reservation) storeObjects[i]);
			}
		}

		for (int i = 0; i < removedObjects.length; i++) {
			if (removedObjects[i] == null) {
				throw new RaplaException("Removed Objects cant be null");
			}
		}

		ArrayList<RefEntity<?>> storeList = new ArrayList<RefEntity<?>>();
		ArrayList<RefEntity<?>> removeList = new ArrayList<RefEntity<?>>();
		for (Entity<?> toStore : storeObjects) {
			storeList.add((RefEntity<?>) toStore);
		}
		for (Entity<?> toRemove : removedObjects) {
			removeList.add((RefEntity<?>) toRemove);
		}
		operator.storeAndRemove(storeList, removeList, (RefEntity<User>) workingUser);
	
		if (getLogger().isDebugEnabled())
			getLogger().debug("Storing took " + (System.currentTimeMillis() - time)	+ " ms.");
	}

	public CommandHistory getCommandHistory() 
	{
		return commandHistory;
	}

	public void changeName(String title, String firstname, String surname) throws RaplaException
	{
		@SuppressWarnings("unchecked")
		RefEntity<User> user = (RefEntity<User>)getUser();
		getOperator().changeName(user,title,firstname,surname);
	}

	public void changeEmail(String newEmail)  throws RaplaException
	{
		@SuppressWarnings("unchecked")
		RefEntity<User> user = (RefEntity<User>)getUser();
		getOperator().changeEmail(user, newEmail);
	}

	public void confirmEmail(String newEmail) throws RaplaException {
		@SuppressWarnings("unchecked")
		RefEntity<User> user = (RefEntity<User>)getUser();
		getOperator().confirmEmail(user, newEmail);
	}

	

}
