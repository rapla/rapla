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

import io.reactivex.functions.Consumer;
import org.rapla.RaplaResources;
import org.rapla.components.util.DateTools;
import org.rapla.entities.*;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaMap;
import org.rapla.entities.configuration.internal.RaplaMapImpl;
import org.rapla.entities.domain.*;
import org.rapla.entities.domain.internal.AllocatableImpl;
import org.rapla.entities.domain.internal.AppointmentImpl;
import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.dynamictype.internal.AttributeImpl;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.entities.internal.CategoryImpl;
import org.rapla.entities.internal.ModifiableTimestamp;
import org.rapla.entities.internal.UserImpl;
import org.rapla.entities.storage.ParentEntity;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.entities.storage.internal.SimpleEntity;
import org.rapla.facade.*;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.ResolvedPromise;
import org.rapla.storage.PermissionController;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.StorageOperator;

import javax.inject.Inject;
import javax.inject.Singleton;
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

/**
 * This is the default implementation of the necessary JavaClient-Facade to the
 * DB-Subsystem.
 * <p>
 * The store entry contains the id of a storage-component. Storage-Components
 * are all components that implement the {@link StorageOperator} interface.
 * </p>
 */
@Singleton
@DefaultImplementation(of = RaplaFacade.class, context = InjectionContext.all)
public class FacadeImpl implements RaplaFacade {
	private StorageOperator operator;
	private RaplaResources i18n;

	Locale locale;

	Logger logger;

	String templateId;
	protected CommandScheduler notifyQueue;

	String workingUserId;


	@Inject
	public FacadeImpl(RaplaResources i18n, CommandScheduler notifyQueue, Logger logger) {
	    
		this.logger = logger;
		this.i18n = i18n;
		this.notifyQueue = notifyQueue;
		locale = i18n.getLocale();
	}

	public CommandScheduler getScheduler()
	{
		return notifyQueue;
	}

	public void setWorkingUserId(String workingUserId) {
		this.workingUserId = workingUserId;
	}

	public String getWorkingUserId() {
		return workingUserId;
	}

	public RaplaResources getI18n()
	{
		return i18n;
	}

	public boolean canAllocate(CalendarModel model,User user)
	{
		//Date start, Date end,
		Collection<Allocatable> allocatables = model.getMarkedAllocatables();
		boolean canAllocate = true;

		Date start = RaplaComponent.getStartDate(model, this,user);
		Date end = RaplaComponent.calcEndDate(model, start);
		for (Allocatable allo : allocatables)
		{
			if (!getPermissionController().canAllocate(start, end, allo, user))
			{
				canAllocate = false;
			}
		}
		return canAllocate;
	}

	public PermissionController getPermissionController()
	{
		if ( operator== null)
		{
//		    throw new RaplaException("Dependency Cycle detected. Please use provider for operator ");
			throw new RaplaInitializationException("Dependency Cycle detected. Please use provider for operator ");
		}
		return operator.getPermissionController() ;
	}

    public Logger getLogger()
	{
        return logger;
    }
	
	public StorageOperator getOperator() {
		return operator;
	}

	public void setOperator(StorageOperator operator) {
		this.operator = operator;
	}

	/******************************
	 * Update-module *
	 ******************************/
	public void refresh() throws RaplaException {
		if (operator.supportsActiveMonitoring())
		{
			operator.refresh();
		}
	}

	public Promise<Void> refreshAsync()  {
		try {
			if (operator.supportsActiveMonitoring()) {
				operator.refresh();
			}
			return ResolvedPromise.VOID_PROMISE;
		} catch (RaplaException ex)
		{
			return new ResolvedPromise<>(ex);
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

	/** unlike getUserFromRequest this can be null if working user not set*/
	private User getWorkingUser() throws EntityNotFoundException {
		if ( workingUserId == null)
		{
			return null;
		}
		return operator.resolve( workingUserId, User.class);
	}

	/******************************
	 * Query-module *
	 ******************************/
	private Collection<Allocatable> getVisibleAllocatables(	ClassificationFilter[] filters) throws RaplaException {
        User workingUser = getWorkingUser();
		Collection<Allocatable> objects = operator.getAllocatables(filters);
		if (workingUser != null && !workingUser.isAdmin()) {
			PermissionController permissionController = getPermissionController();
			Iterator<Allocatable> it = objects.iterator();
			while (it.hasNext()) {
				Allocatable allocatable = it.next();
				if (!permissionController.canRead(allocatable, workingUser))
					it.remove();
			}
		}
		return objects;
	}

	public Promise<Collection<Reservation>> getReservationsAsync(User user, Allocatable[] allocatables, Date start, Date end, ClassificationFilter[] reservationFilters) {
        final CommandScheduler scheduler = getScheduler();
        final Promise<Collection<Allocatable>> allocatablesPromise = scheduler.supply(() ->
        {
            if (templateId != null)
            {
                final Allocatable template = getTemplate();
                if (template == null)
                {
                    throw new RaplaException("Template for id " + templateId + " not found!");
                }
            }
            final Collection<Allocatable> allocatablesCollection = allocatables != null ? Arrays.asList(allocatables) : null;
            return allocatablesCollection;
        });
        final Promise<Map<Allocatable, Collection<Appointment>>> appointmentMapPromise = allocatablesPromise.thenCompose((allocatablesCollection) ->
        {
            final Promise<Map<Allocatable, Collection<Appointment>>> appointmentsAsync = operator.queryAppointments(user, allocatablesCollection,
                    start, end, reservationFilters, templateId);
            return appointmentsAsync;
        });
        final Promise<Collection<Reservation>> promise = appointmentMapPromise.thenApply((appointments) ->
        {
            final Collection<Reservation> allReservations = CalendarModelImpl.getAllReservations(appointments);
            return allReservations;
        });
        return promise;
	}

	public Allocatable[] getAllocatables() throws RaplaException {
		return getAllocatablesWithFilter(null);
	}

	public Allocatable[] getAllocatablesWithFilter(ClassificationFilter[] filters) throws RaplaException {
		return getVisibleAllocatables(filters).toArray(	Allocatable.ALLOCATABLE_ARRAY);
	}

	public boolean canExchangeAllocatables(User user,Reservation reservation) {
		if ( user == null)
		{
			return true;
		}
		try {
			Allocatable[] all = getAllocatablesWithFilter(null);
			PermissionController permissionController = getPermissionController();
			for (int i = 0; i < all.length; i++) {
				if (permissionController.canModify(all[i], user)) {
					return true;
				}
			}
		} catch (RaplaException ex) {
		}
		return false;
	}

	public Preferences getSystemPreferences() throws RaplaException
	{
        return operator.getPreferences(null, true);
	}

	public Preferences getAdminPreferences() throws RaplaException
	{
		return operator.getPreferences(null, true);
	}

	public Preferences getPreferences(User user) throws RaplaException {
		return operator.getPreferences(user, true);
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
	
	public Collection<Allocatable> getTemplates() throws RaplaException
	{
	    DynamicType dynamicType = getDynamicType(StorageOperator.RAPLA_TEMPLATE);
	    ClassificationFilter[] array = dynamicType.newClassificationFilter().toArray();
	    Collection<Allocatable> allocatables = operator.getAllocatables( array);
		return allocatables;
	}
	
	public Promise<Collection<Reservation>> getTemplateReservations(Allocatable template)
	{
		User user = null;
		Collection<Allocatable> allocList = new ArrayList<Allocatable>();
		allocList.add(template);
		Date start = null;
		Date end = null;
		Map<String,String> annotationQuery = null;
		//Map<String,String> annotationQuery = new LinkedHashMap<String,String>();
		//annotationQuery.put(RaplaObjectAnnotations.KEY_TEMPLATE, template.getId());
        final Promise<Map<Allocatable, Collection<Appointment>>> promiseMap = operator.queryAppointments(user, allocList, start, end, null, annotationQuery);
        final Promise<Collection<Reservation>> reservationPromise = promiseMap.thenApply((allocatable) ->
        {
            final Collection<Reservation> result = CalendarModelImpl.getAllReservations(allocatable);
            return result;
        });
        return reservationPromise;
	}
	
	public Promise<Collection<Reservation>> getReservations(User user, Date start, Date end,ClassificationFilter[] reservationFilters) {
        Promise<Collection<Reservation>>collection = getReservationsAsync(user, null,start, end, reservationFilters);
        return collection;
	}
	
	public Promise<Collection<Reservation>> getReservationsForAllocatable(Allocatable[] allocatables, Date start, Date end,ClassificationFilter[] reservationFilters) {
        Promise<Collection<Reservation>> collection = getReservationsAsync(null, allocatables,start, end, reservationFilters);
        return collection;
    }



	public DynamicType[] getDynamicTypes(String classificationType)
			throws RaplaException {
		return getDynamicTypes(operator, classificationType, getWorkingUser());
	}

	static public DynamicType[] getDynamicTypes(StorageOperator operator,String classificationType, User user)
			throws RaplaException {
		ArrayList<DynamicType> result = new ArrayList<DynamicType>();
		Collection<DynamicType> collection = operator.getDynamicTypes();
		PermissionController permissionController = operator.getPermissionController();
		for (DynamicType type: collection) {
			String classificationTypeAnno = type.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
			// ignore internal types for backward compatibility
			if ((( DynamicTypeImpl)type).isInternal())
			{
				continue;
			}
			if ( user != null && !permissionController.canRead(type, user))
			{
				continue;
			}
			if ( classificationType == null || classificationType.equals(classificationTypeAnno)) {
				result.add(type);
			}
		}
		return result.toArray(DynamicType.DYNAMICTYPE_ARRAY);
	}

	public DynamicType getDynamicType(String elementKey) throws RaplaException {
		DynamicType dynamicType = operator.getDynamicType(elementKey);
		if ( dynamicType == null)
		{
			throw new EntityNotFoundException("No dynamictype with elementKey "
				+ elementKey);
		}
		return dynamicType;
		
	}

	public User[] getUsers() throws RaplaException {
		User[] result = operator.getUsers().toArray(User.USER_ARRAY);
		return result;
	}

	public User getUser(String username) throws RaplaException {
		User user = operator.getUser(username);
		if (user == null)
			throw new EntityNotFoundException("No User with username " + username);
		return user;
	}

    public Promise<Collection<Conflict>> getConflictsForReservation(Reservation reservation)
    {
        return operator.getConflicts(reservation);
    }

	public Collection<Conflict> getConflicts() throws RaplaException {
	    
		final User user;
        User workingUser = getWorkingUser();
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
	
		return conflicts;
	}
	
//	public boolean canReadReservationsFromOthers(User user) {
//		return hasGroupRights(user, Permission.GROUP_CAN_READ_EVENTS_FROM_OTHERS);
//	}

//	protected boolean hasGroupRights(User user, String groupKey) {
//		if (user == null) {
//		    User workingUser;
//            try {
//                workingUser = getWorkingUser();
//            } catch (EntityNotFoundException e) {
//                return false;
//            }
//			return workingUser == null || workingUser.isAdmin();
//		}
//		if (user.isAdmin()) {
//			return true;
//		}
//		try {
//			Category group = getUserGroupsCategory().getCategory(	groupKey);
//			if ( group == null)
//			{
//				return true;
//			}
//			return user.belongsTo(group);
//		} catch (Exception ex) {
//			getLogger().error("Can't get permissions!", ex);
//		}
//		return false;
//	}

//	@Deprecated
//	public Allocatable[] getAllocatableBindings(Appointment forAppointment)	throws RaplaException {
//		List<Allocatable> allocatableList = Arrays.asList(getAllocatables());
//		List<Allocatable> result = new ArrayList<Allocatable>();
//
//		FutureResult<Map<Allocatable, Collection<Appointment>>> allocatableBindings = getAllocatableBindings( allocatableList, Collections.singletonList(forAppointment));
//        Map<Allocatable, Collection<Appointment>> bindings;
//        try {
//            bindings = allocatableBindings.get();
//        } catch (RaplaException e) {
//            throw e;
//        } catch (Exception e) {
//            throw new RaplaException(e.getMessage());
//        }
//		for (Map.Entry<Allocatable, Collection<Appointment>> entry: bindings.entrySet())
//		{
//			Collection<Appointment> appointments = entry.getValue();
//			if ( appointments.contains( forAppointment))
//			{
//				Allocatable alloc = entry.getKey();
//				result.add( alloc);
//			}
//		}
//		return result.toArray(Allocatable.ALLOCATABLE_ARRAY);
//
//	}
	
	public Promise<Map<Allocatable,Collection<Appointment>>> getAllocatableBindings(Collection<Allocatable> allocatables, Collection<Appointment> appointments)  {
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
	
	
	public Promise<Date> getNextAllocatableDate(Collection<Allocatable> allocatables,	Appointment appointment, CalendarOptions options)  {
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
	 * Modification-module *
	 ******************************/

	@SuppressWarnings("unchecked")
	public <T> RaplaMap<T> newRaplaMapForMap(Map<String, T> map) {
		RaplaMapImpl impl = new RaplaMapImpl(map);
		impl.setResolver( operator );
        RaplaMap<T> raplaMap = (RaplaMap<T>) impl;
        return raplaMap;
	}

	@SuppressWarnings("unchecked")
	public <T> RaplaMap<T> newRaplaMap(Collection<T> col) {
		RaplaMapImpl impl = new RaplaMapImpl(col);
        impl.setResolver( operator );
		RaplaMap<T> raplaMap = (RaplaMap<T>) impl;
		return raplaMap;
	}

	public Appointment newAppointment(Date startDate, Date endDate) throws RaplaException {
		User user = getUser();
		return newAppointmentWithUser(startDate, endDate, user);
	}
	
	public Reservation newReservationDeprecated() throws RaplaException
    {
        Classification classification = getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION)[0].newClassification();
		User user = getUser();
		return newReservation( classification,user );
    }

	public Reservation newReservation(Classification classification,User user) throws RaplaException 
    {
        if (!getPermissionController().canCreate(classification.getType(), user))
        {
            throw new RaplaException("User not allowed to create events");
        }
    	Date now = operator.getCurrentTimestamp();
        ReservationImpl reservation = new ReservationImpl(now ,now );
        
        reservation.setClassification(classification);
        PermissionContainer.Util.copyPermissions(classification.getType(), reservation);
        setNew(reservation, user);
        if ( templateId != null )
        {
            setTemplateParams(reservation );
        }
        return reservation;
    }

	public Allocatable getTemplate()
	{
		if ( templateId != null)
		{
			Allocatable template = operator.tryResolve( templateId, Allocatable.class);
			return template;
		}
		return null;
	}

    private void setTemplateParams(Reservation reservation) throws RaplaException {
        Allocatable template = getTemplate();
        if ( template == null)
        {
            throw new RaplaException("Template not found " + templateId);
        }
        reservation.setAnnotation(RaplaObjectAnnotations.KEY_TEMPLATE, template.getId());
    }

    public Allocatable newAllocatable( Classification classification) throws RaplaException 
	{
		return newAllocatable(classification, getUser());
	}
	
	public Allocatable newAllocatable( Classification classification, User user) throws RaplaException {
        Date now = operator.getCurrentTimestamp();
        AllocatableImpl allocatable = new AllocatableImpl(now, now);
        allocatable.setClassification(classification);
        PermissionContainer.Util.copyPermissions(classification.getType(), allocatable);
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

    public Appointment newAppointmentWithUser(Date startDate, Date endDate, User user) throws RaplaException {
        AppointmentImpl appointment = new AppointmentImpl(startDate, endDate);
        setNew(appointment, user);
        return appointment;
    }

	public User getUser() throws RaplaException {
		User user = getWorkingUser();
		if (user == null) {
			throw new RaplaException("no user loged in");
		}
		return user;
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

	public Allocatable newPeriod(User user) throws RaplaException {
		DynamicType periodType = getDynamicType(StorageOperator.PERIOD_TYPE);
		Classification classification = periodType.newClassification();
		classification.setValue("name", "");
		Date today = today();
		classification.setValue("start", DateTools.cutDate(today));
		classification.setValue("end", DateTools.addDays(DateTools.fillDate(today),7));
		Allocatable period = newAllocatable(classification, user);
		setNew(period, user);
		return period;
	}


	public Date today() {
		return operator.today();
	}

	public Category newCategory() throws RaplaException {
		Date now = operator.getCurrentTimestamp();
        CategoryImpl category = new CategoryImpl(now, now);
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
		Date now = operator.getCurrentTimestamp();
		DynamicTypeImpl dynamicType = new DynamicTypeImpl(now,now);
		dynamicType.setAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE, classificationType);
		dynamicType.setKey(createDynamicTypeKey(classificationType));
		setNew(dynamicType);
		if (classificationType.equals(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE)) {
			dynamicType.addAttribute(createStringAttribute("name", "name"));
			dynamicType.setAnnotation(DynamicTypeAnnotations.KEY_NAME_FORMAT,"{name}");
			dynamicType.setAnnotation(DynamicTypeAnnotations.KEY_COLORS,"automatic");
			addDefaultResourcePermissions(dynamicType);
		} else if (classificationType.equals(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION)) {
			dynamicType.addAttribute(createStringAttribute("name","eventname"));
			dynamicType.setAnnotation(DynamicTypeAnnotations.KEY_NAME_FORMAT,"{name}");
			dynamicType.setAnnotation(DynamicTypeAnnotations.KEY_COLORS, null);
			addDefaultEventPermissions(dynamicType);
		} else if (classificationType.equals(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON)) {
			dynamicType.addAttribute(createStringAttribute("surname", "surname"));
			dynamicType.addAttribute(createStringAttribute("firstname", "firstname"));
			dynamicType.addAttribute(createStringAttribute("email", "email"));
			dynamicType.setAnnotation(DynamicTypeAnnotations.KEY_NAME_FORMAT, "{surname} {firstname}");
			dynamicType.setAnnotation(DynamicTypeAnnotations.KEY_COLORS, null);
            addDefaultResourcePermissions(dynamicType);
		}
		return dynamicType;
	}

    @SuppressWarnings("deprecation")
    private void addDefaultEventPermissions(DynamicTypeImpl dynamicType) throws RaplaException {
        {
            Permission permission = dynamicType.newPermission();
            permission.setAccessLevel( Permission.READ_TYPE);
            dynamicType.addPermission( permission);
        }
        Category canReadEventsFromOthers = getUserGroupsCategory().getCategory(Permission.GROUP_CAN_READ_EVENTS_FROM_OTHERS);
        if ( canReadEventsFromOthers != null)
        {
            Permission permission = dynamicType.newPermission();
            permission.setAccessLevel( Permission.READ);
            permission.setGroup( canReadEventsFromOthers);
            dynamicType.addPermission( permission);
        }
        Category canCreate = getUserGroupsCategory().getCategory(Permission.GROUP_CAN_CREATE_EVENTS);
        if ( canCreate != null)
        {
            Permission permission = dynamicType.newPermission();
            permission.setAccessLevel( Permission.CREATE);
            permission.setGroup( canCreate);
            dynamicType.addPermission( permission);
        }
    }

    private void addDefaultResourcePermissions(DynamicTypeImpl dynamicType) throws RaplaException {
        {
            Permission permission = dynamicType.newPermission();
            permission.setAccessLevel( Permission.READ_TYPE);
            dynamicType.addPermission( permission);
        }
        {
            Permission permission = dynamicType.newPermission();
            permission.setAccessLevel( Permission.ALLOCATE_CONFLICTS);
            dynamicType.addPermission( permission);
        }
        @SuppressWarnings("deprecation")
        Category registerer = getUserGroupsCategory().getCategory(Permission.GROUP_REGISTERER_KEY);
        if ( registerer != null)
        {
            Permission permission = dynamicType.newPermission();
            permission.setAccessLevel( Permission.CREATE);
            permission.setGroup( registerer);
            dynamicType.addPermission( permission);
        }
    }

	public Attribute newAttribute(AttributeType attributeType)	throws RaplaException {
		AttributeImpl attribute = new AttributeImpl(attributeType);
		setNew(attribute);
		return attribute;
	}

	public User newUser() throws RaplaException {
		Date now = operator.getCurrentTimestamp();
		UserImpl user = new UserImpl( now, now);
		setNew(user);
		@SuppressWarnings("deprecation")
        String[] defaultGroups = new String[] {Permission.GROUP_CAN_READ_EVENTS_FROM_OTHERS,Permission.GROUP_CAN_CREATE_EVENTS, Permission.GROUP_MODIFY_PREFERENCES_KEY, Permission.GROUP_MODIFY_PREFERENCES_KEY};
		for ( String groupKey: defaultGroups)
		{
			Category group = getUserGroupsCategory().getCategory( groupKey);
			if (group != null) 
			{
				user.addGroup(group);
			}
		}
		final User workingUser = getWorkingUser();
		if ( workingUser != null)
		{
			if ( !workingUser.isAdmin())
			{
				final Collection<Category> adminGroups = PermissionController.getGroupsToAdmin(workingUser);
				if ( adminGroups.size() == 0)
				{
					throw new RaplaSecurityException("User " + workingUser +" can't create a new User " );
				}
				else {
					// add first admin group to user
					final Category firstAdminGroup = adminGroups.iterator().next();
					user.addGroup( firstAdminGroup);
				}
			}
		}
		return user;
	}

	public CalendarSelectionModel newCalendarModel(User user) throws RaplaException{
	    User workingUser = getWorkingUser();
        if ( workingUser != null && !workingUser.isAdmin() && !user.equals(workingUser))
	    {
	        throw new RaplaException("Can't create a calendar model for a different user.");
	    }
	    return new CalendarModelImpl( locale, user, operator, logger);
    }

	private String createDynamicTypeKey(String classificationType)
			throws RaplaException {
		DynamicType[] dts = getDynamicTypes(classificationType);
		int max = 1;
		for (int i = 0; i < dts.length; i++) {
			String key = dts[i].getKey();
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

	private void setNew(Entity entity) throws RaplaException {
		setNew(entity, null);
	}
	
	private void setNew(Entity entity,User user) throws RaplaException {
	    ArrayList<Entity> arrayList = new ArrayList<Entity>();
	    arrayList.add( entity );
	    setNew(arrayList, entity.getTypeClass(), user);
	}


	private <T extends Entity> void setNew(Collection<T> entities, Class<T> raplaType,User user)
			throws RaplaException {

		for ( T entity: entities)
		{
			if ((entity instanceof ParentEntity) && (((ParentEntity)entity).getSubEntities().iterator().hasNext()) && ! (entity instanceof Reservation) ) {
				throw new RaplaException("The current Rapla Version doesnt support cloning entities with sub-entities. (Except reservations)");
			}
		}
		ReferenceInfo<T>[] ids = operator.createIdentifier(raplaType, entities.size());
		int i = 0;
		for ( T uncasted: entities)
		{
			ReferenceInfo id = ids[i++];
			SimpleEntity entity = (SimpleEntity) uncasted;
			entity.setId(id.getId());
			entity.setResolver(operator);
			if (getLogger() != null && getLogger().isDebugEnabled()) {
				getLogger().debug("new " + entity.getId());
			}
	
			if (entity instanceof Reservation) {
				if (user == null)
					throw new RaplaException("The reservation " + entity + " needs an owner but user specified is null ");
			}
			if ( entity instanceof Reservation || entity instanceof Allocatable)
			{
	             entity.setOwner(user);
			}
		}
	}

	public String getUsername(ReferenceInfo<User> userId) throws RaplaException
	{
		return operator.getUsername(userId);
	}

	@Override
	public <T extends Entity> T edit(T obj) throws RaplaException {
		if (obj == null)
			throw new NullPointerException("Can't edit null objects");
		Set<T> singleton = Collections.singleton( obj);
		Collection<T> edit = editList(singleton);
		T result = edit.iterator().next();
		return result;
	}

	@Override
	public <T extends Entity> Promise<T> editAsync(T obj) {
		if (obj == null)
			throw new NullPointerException("Can't edit null objects");
		return getScheduler().supply(()-> edit( obj));
	}

	@Override
	public <T extends Entity> Promise<Collection<T>> editAsyncList(Collection<T> list) {
		if (list == null)
			throw new NullPointerException("Can't edit null objects");
		return getScheduler().supply(()-> editList( list));
	}


	public <T extends Entity> Collection<T> editList(Collection<T> list) throws RaplaException
	{
		List<Entity> castedList = new ArrayList<Entity>();
		for ( Entity entity:list)
		{
			castedList.add( entity);
		}
		User workingUser = getWorkingUser();
		Collection<Entity> result = operator.editObjects(castedList,	workingUser);
		List<T> castedResult = new ArrayList<T>();
		for ( Entity entity:result)
		{
			@SuppressWarnings("unchecked")
			T casted = (T) entity;
			castedResult.add( casted);
		}
		return castedResult;
	}

	@Override
	public <T extends Entity> Promise<Void> update(T obj, Consumer<T> updateFunction)
	{
		Promise<Void> updatePromise = editAsync( obj).thenApply((editableObject)-> {updateFunction.accept(editableObject);return editableObject;}
		).thenAccept((editableObject)->dispatch(Collections.singleton( editableObject), Collections.emptyList()));
		return updatePromise;
	}

	@Override
	public <T extends Entity> Promise<Void> updateList(Collection<T> list, Consumer<Collection<T>> updateFunction)
	{
		Promise<Void> updatePromise = getScheduler().supply(()-> editList( list)).thenApply((editableObject)->{updateFunction.accept(editableObject); return editableObject;}
		).thenAccept((editableObject)->dispatch(editableObject, Collections.emptyList()));
		return updatePromise;
	}



	   public Collection<Reservation> copy(Collection<Reservation> toCopy, Date beginn, boolean keepTime, User user) throws RaplaException
	    {
	        List<Reservation> sortedReservations = new ArrayList<Reservation>(  toCopy);
	        Collections.sort( sortedReservations, new ReservationStartComparator(i18n.getLocale()));
	        List<Reservation> copies = new ArrayList<Reservation>();
	        Date firstStart = null;
	        for (Reservation reservation: sortedReservations) {
	            if ( firstStart == null )
	            {
	                firstStart = ReservationStartComparator.getStart( reservation);
	            }
	            Reservation copy = copy(reservation, beginn, firstStart, keepTime, user);
	            copies.add( copy);
	        }
	        return copies;
	    }
	    
	    private  Reservation copy(Reservation reservation, Date destStart,Date firstStart, boolean keepTime, User user) throws RaplaException {
	        Reservation r =  clone( reservation, user);
	        Appointment[] appointments = r.getAppointments();
	    
	        for ( Appointment app :appointments) {
	            Repeating repeating = app.getRepeating();
	            
	            Date oldStart = app.getStart();
	            Date newStart ;
	            // we need to calculate an offset so that the reservations will place themself relativ to the first reservation in the list
	            if ( keepTime)
	            {
	                long offset = DateTools.countDays( firstStart, oldStart) * DateTools.MILLISECONDS_PER_DAY;
	                Date destWithOffset = new Date(destStart.getTime() + offset );
	                newStart = DateTools.toDateTime(  destWithOffset  , oldStart );
	            }
	            else
	            {
	                long offset = destStart.getTime() - firstStart.getTime();
	                newStart = new Date(oldStart.getTime() + offset );
	            }
	            app.moveTo( newStart) ;
	            if (repeating != null)
	            {
	                Date[] exceptions = repeating.getExceptions();
	                repeating.clearExceptions();
	                for (Date exc: exceptions)
	                {
	                    long days = DateTools.countDays(oldStart, exc);
	                    Date newDate = DateTools.addDays(newStart, days);
	                    repeating.addException( newDate);
	                }
	                
	                if ( !repeating.isFixedNumber())
	                {
	                    Date oldEnd = repeating.getEnd();
	                    if ( oldEnd != null)
	                    {
	                        // If we don't have and ending destination, just make the repeating to the original length
	                        long days = DateTools.countDays(oldStart, oldEnd);
	                        Date end = DateTools.addDays(newStart, days);
	                        repeating.setEnd( end);
	                    }
	                }       
	            }
	        }
	        return r;
	    }

	@SuppressWarnings("unchecked")
	private <T extends Entity> T _clone(T obj) throws RaplaException {
		T deepClone =  (T) obj.clone();
		T clone = deepClone;

		Class<? extends Entity> raplaType = clone.getTypeClass();
		if (raplaType == Appointment.class) {
			// Hack for 1.6 compiler compatibility
			Object temp = clone;
			((AppointmentImpl) temp).removeParent();
		}
		if (raplaType == Category.class) {
			// Hack for 1.6 compiler compatibility
			Object temp = clone;
			((CategoryImpl) temp).removeParent();
		}
		User workingUser = getWorkingUser();
        setNew(clone, workingUser);
		return clone;
	}


	@SuppressWarnings("unchecked")
	public <T extends Entity> T clone(T obj, User user) throws RaplaException {
		if (obj == null)
			throw new NullPointerException("Can't clone null objects");

		T result;
		Class<T> raplaType = obj.getTypeClass();
		if (raplaType == Appointment.class ){
			T _clone = _clone(obj);
			((AppointmentImpl) _clone).setParent(null);
			result = _clone;
		} else if (raplaType == Reservation.class) {
			Reservation clonedReservation = cloneReservation((Reservation) obj);
			Reservation r = clonedReservation;
			if ( user != null)
			{
				r.setOwner( user );
				((ReservationImpl)r).setLastChangedBy( user);
				((ReservationImpl)r).setLastChanged( null);
			}
			if ( templateId != null )
			{
				setTemplateParams(r );
			}
			else
			{
				String originalTemplate = r.getAnnotation( RaplaObjectAnnotations.KEY_TEMPLATE);
				if (originalTemplate != null)
				{
					r.setAnnotation(RaplaObjectAnnotations.KEY_TEMPLATE_COPYOF, originalTemplate);
				}
				r.setAnnotation(RaplaObjectAnnotations.KEY_TEMPLATE, null);
			}
			result = (T)r;
			
		}
		else
		{
			try {
				T _clone = _clone(obj);
				result = _clone;
			} catch (ClassCastException ex) {
				throw new RaplaException("This entity can't be cloned ", ex);
			} finally {
			}
		}
		return result;

	}

	/** Clones a reservation and sets new ids for all appointments and the reservation itsel
	 */
	private Reservation cloneReservation(Reservation obj) throws RaplaException {
	    User workingUser = getWorkingUser();
		// first we do a reservation deep clone
		Reservation clone =  obj.clone();
		HashMap<Allocatable, Appointment[]> restrictions = new HashMap<Allocatable, Appointment[]>();
		Allocatable[] allocatables = clone.getAllocatables();

		for (Allocatable allocatable:allocatables) {
			restrictions.put(allocatable, clone.getRestriction(allocatable));
		}

		// then we set new ids for all appointments
		Appointment[] clonedAppointments = clone.getAppointments();
		setNew(Arrays.asList(clonedAppointments),Appointment.class, workingUser);
		
		for (Appointment clonedAppointment:clonedAppointments) {
			clone.removeAppointment(clonedAppointment);
		}

		// and now a new id for the reservation
		setNew( clone, workingUser);
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

	public <T extends Entity> T getPersistant(T entity) throws RaplaException {
		Set<T> persistantList = Collections.singleton( entity);
		Map<T,T> map = getPersistantForList( persistantList);
		T result = map.get( entity);
		if ( result == null)
		{
			throw new EntityNotFoundException(	"There is no persistant version of " + entity);
		}
		return result;
	}
	
	public <T extends Entity> Map<T,T> getPersistantForList(Collection<T> list) throws RaplaException {
		Map<Entity,Entity> result = operator.getPersistant(list);
		LinkedHashMap<T, T> castedResult = new LinkedHashMap<T, T>();
		for ( Map.Entry<Entity,Entity> entry: result.entrySet())
		{
			@SuppressWarnings("unchecked")
			T key = (T) entry.getKey();
			@SuppressWarnings("unchecked")
			T value = (T) entry.getValue();
			castedResult.put( key, value);
		}
		return castedResult;
	}

	@Override
	public <T extends Entity> void store(T obj) throws RaplaException {
		if (obj == null)
			throw new NullPointerException("Can't store null objects");
		storeObjects(new Entity[] { obj });
	}

	@Override
	public <T extends Entity> void remove(T obj) throws RaplaException {
		if (obj == null)
			throw new NullPointerException("Can't remove null objects");
		removeObjects(new Entity[] { obj });
	}

	@Override
	public <T extends Entity> void storeObjects(T[] obj) throws RaplaException {
		User user = getWorkingUser();
		storeAndRemove(obj, Entity.ENTITY_ARRAY, user);
	}

	@Override
	public <T extends Entity> void removeObjects(T[] obj) throws RaplaException {
		User user = getWorkingUser();
		storeAndRemove(Entity.ENTITY_ARRAY, obj, user);
	}

	@Override
	public <T extends Entity,S extends Entity> Promise<Void> dispatch( Collection<T> storeList, Collection<ReferenceInfo<S>> removeList)
	{
		long time = System.currentTimeMillis();
		try
		{
			User user = getWorkingUser();
			dispatchSynchronized(storeList, removeList, user);
			if (getLogger().isDebugEnabled())
				getLogger().debug("Storing took " + (System.currentTimeMillis() - time) + " ms.");
		}
		catch ( Exception ex)
		{
			return new ResolvedPromise<Void>(ex);
		}
		return ResolvedPromise.VOID_PROMISE;
	}

	private <T extends Entity, S extends Entity> void dispatchSynchronized(Collection<T> storeList, Collection<ReferenceInfo<S>> removeList, User user)
			throws RaplaException
	{
		if (storeList.size() == 0 && removeList.size() == 0)
		{
			return;
		}

		for (ReferenceInfo<?> removedObject:removeList) {
            if (removedObject == null) {
                throw new RaplaException("Removed Objects cant be null");
            }
        }
		for (T toStore : storeList) {
            if (toStore == null) {
                throw new RaplaException("Stored Objects cant be null");
            }
            final Class typeClass = toStore.getTypeClass();
            if (typeClass == Reservation.class) {
                ReservationImpl.checkReservation(i18n,(Reservation) toStore,operator);
            }
        }
		List<T> transientCategories = new ArrayList<>();
		for (T toStore : storeList) {
            final Class typeClass = toStore.getTypeClass();
            if ( typeClass == Category.class)
            {
                // add non resolvable categories
                addTransientCategories(transientCategories, (CategoryImpl) toStore,0);
            }
        }
		if ( transientCategories.size() > 0)
        {
            storeList = new ArrayList<>(storeList);
            storeList.addAll( transientCategories);
        }

		operator.storeAndRemove(storeList, removeList, user);
	}

	@Override
	public <T extends Entity,S extends Entity> void storeAndRemove(T[] storedObjects, S[] removedObjects, User user) throws RaplaException
	{
		ArrayList<T>storeList = new ArrayList<>();
		ArrayList<ReferenceInfo<S>>removeList = new ArrayList<>();
		for (S toRemove : removedObjects) {
			removeList.add( toRemove.getReference());
		}
		for (T toStore : storedObjects) {
			storeList.add( toStore);
		}
		dispatchSynchronized(storeList, removeList, user);
	}

	private <T extends  Entity> void addTransientCategories(List<T> storeList, CategoryImpl toStore, int depth)
	{
		if ( depth >20)
		{
			throw new IllegalStateException("Category cycle detected " + toStore);
		}
		for (Category cat: toStore.getTransientCategoryList())
        {
            if (tryResolve(cat.getReference())== null)
            {
                storeList.add( (T)cat );
				addTransientCategories(storeList, (CategoryImpl) cat, depth +1);
            }
        }
	}

	public <T extends Entity> T tryResolve( ReferenceInfo<T> info)
	{
		return operator.tryResolve( info);
	}

    public  <T extends Entity> T resolve( ReferenceInfo<T> info) throws EntityNotFoundException
	{
		return operator.resolve(info);
	}
	
	@Override
	public Promise<Allocatable> doMerge(Allocatable selectedObject, Set<ReferenceInfo<Allocatable>> allocatableIds, User user)
	{
		return operator.doMerge(selectedObject, allocatableIds, user);
	}


	public void setTemplateId(String templateId) {
		this.templateId = templateId;
	}

	@Override
	public <T extends Entity> Map<T,T> checklastChanged(Collection<T> entities, User user,boolean isNew) throws RaplaException
	{
		Map<T,T> persistantVersions= getPersistantForList(entities);
		checklastChanged(entities, persistantVersions,user,isNew);
		return persistantVersions;
	}

	private <T extends Entity> void checklastChanged(Collection<T> entities, Map<T,T> persistantVersions, User user,boolean isNew) throws RaplaException
	{
		//refresh();
		for ( T entity:entities)
		{
			if ( entity instanceof ModifiableTimestamp)
			{
				T persistant = persistantVersions.get( entity);
				if ( persistant != null)
				{
					ReferenceInfo<User> lastChangedBy = ((ModifiableTimestamp) persistant).getLastChangedBy();
					if (lastChangedBy != null && !user.getReference().equals(lastChangedBy))
					{
						final Locale locale = i18n.getLocale();
						String name = entity instanceof Named ? ((Named) entity).getName( locale) : entity.toString();
						throw new RaplaException(i18n.format("error.new_version", name));
					}
				}
				else
				{
					// if there exists an older version
					if ( !isNew )
					{
						final Locale locale = i18n.getLocale();
						String name = entity instanceof Named ? ((Named) entity).getName( locale) : entity.toString();
						throw new RaplaException(i18n.format("error.new_version", name));
					}
					// otherwise we ignore it
				}

			}
		}
	}

	@Override
	public Period[] getPeriods() throws RaplaException {
		Period[] result = getPeriodModel().getAllPeriods();
		return result;
	}

	@Override
	public PeriodModel getPeriodModel() throws RaplaException {
		return  getPeriodModelFor( null);
	}

	@Override
	public PeriodModel getPeriodModelFor(String key) throws RaplaException {
		return getOperator().getPeriodModelFor( key);
	}


}
