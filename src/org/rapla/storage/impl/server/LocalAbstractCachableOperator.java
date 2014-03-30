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
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.concurrent.locks.Lock;

import org.rapla.components.util.Assert;
import org.rapla.components.util.Cancelable;
import org.rapla.components.util.Command;
import org.rapla.components.util.CommandScheduler;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.SerializableDateTimeFormat;
import org.rapla.components.util.TimeInterval;
import org.rapla.components.util.Tools;
import org.rapla.components.util.iterator.IteratorChain;
import org.rapla.entities.Category;
import org.rapla.entities.DependencyException;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
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
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.ReservationAnnotations;
import org.rapla.entities.domain.ResourceAnnotations;
import org.rapla.entities.domain.internal.AllocatableImpl;
import org.rapla.entities.domain.internal.AppointmentImpl;
import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.dynamictype.internal.AttributeImpl;
import org.rapla.entities.dynamictype.internal.ClassificationImpl;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.entities.internal.CategoryImpl;
import org.rapla.entities.internal.UserImpl;
import org.rapla.entities.storage.CannotExistWithoutTypeException;
import org.rapla.entities.storage.DynamicTypeDependant;
import org.rapla.entities.storage.EntityReferencer;
import org.rapla.entities.storage.RefEntity;
import org.rapla.entities.storage.internal.SimpleEntity;
import org.rapla.facade.Conflict;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.Disposable;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.rapla.server.internal.TimeZoneConverterImpl;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.CachableStorageOperatorCommand;
import org.rapla.storage.IdTable;
import org.rapla.storage.LocalCache;
import org.rapla.storage.RaplaNewVersionException;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.UpdateEvent;
import org.rapla.storage.UpdateOperation;
import org.rapla.storage.UpdateResult;
import org.rapla.storage.impl.AbstractCachableOperator;
import org.rapla.storage.impl.EntityStore;


public abstract class LocalAbstractCachableOperator extends AbstractCachableOperator implements Disposable, CachableStorageOperator {
	protected IdTable idTable;
	/**
	 * set encryption if you want to enable password encryption. Possible values
	 * are "sha" or "md5".
	 */
	private  String encryption = "sha-1";
	private ConflictFinder conflictFinder;
	private Map<String,SortedSet<Appointment>> appointmentMap;
	private SortedSet<Timestamp> timestampSet;
	private TimeZone systemTimeZone = TimeZone.getDefault();
	private CommandScheduler scheduler;
	private Cancelable cleanConflictsTask;
	
	protected void addInternalTypes(LocalCache cache) throws RaplaException
    {
		{
    		DynamicTypeImpl type = new DynamicTypeImpl();
			String key = UNRESOLVED_RESOURCE_TYPE;
			type.setElementKey(key);
			type.setId(DynamicType.TYPE.getId(-1));
			type.setAnnotation(DynamicTypeAnnotations.KEY_NAME_FORMAT,"{"+key + "}");
			type.getName().setName("en", "anonymous");
			type.setAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE, DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON);
			type.setResolver( this);
			type.setReadOnly( );
			cache.put( type);
		}
		{
			DynamicTypeImpl type = new DynamicTypeImpl();
			String key = ANONYMOUSEVENT_TYPE;
			type.setElementKey(key);
			type.setId(DynamicType.TYPE.getId( 0));
			type.setAnnotation(DynamicTypeAnnotations.KEY_NAME_FORMAT,"{"+key + "}");
			type.setAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE, DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);
			type.getName().setName("en", "anonymous");
			type.setResolver( this);
			cache.put( type);
		}
		
		{
			DynamicTypeImpl type = new DynamicTypeImpl();
			type.setElementKey(CRYPTO_TYPE);
			type.setAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE, DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RAPLATYPE);
			type.setAnnotation(DynamicTypeAnnotations.KEY_TRANSFERED_TO_CLIENT, DynamicTypeAnnotations.VALUE_TRANSFERED_TO_CLIENT_NEVER);
			type.setId(DynamicType.TYPE.getId( -4));
			type.setResolver( this);
			type.setReadOnly( );
			cache.put( type);
		}
		{
			DynamicTypeImpl type = new DynamicTypeImpl();
			type.setElementKey(SYNCHRONIZATIONTASK_TYPE);
			type.setId(DynamicType.TYPE.getId( -3));
			type.setAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE, DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RAPLATYPE);
			type.setAnnotation(DynamicTypeAnnotations.KEY_TRANSFERED_TO_CLIENT, DynamicTypeAnnotations.VALUE_TRANSFERED_TO_CLIENT_NEVER);
			type.addAttribute(createAttributeWithId("objectId", AttributeType.STRING,-7));
			type.addAttribute(createAttributeWithId("externalObjectId",AttributeType.STRING, -8));
			type.addAttribute(createAttributeWithId("status",AttributeType.STRING, -9));
			type.addAttribute(createAttributeWithId("retries", AttributeType.STRING,-10));
			type.setResolver( this);
			type.setReadOnly();
			cache.put( type);
		}
		{
			DynamicTypeImpl type = new DynamicTypeImpl();
			type.setElementKey(PERIOD_TYPE);
			type.setId(DynamicType.TYPE.getId( -2));
			type.setAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE, DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RAPLATYPE);
			type.setAnnotation(DynamicTypeAnnotations.KEY_TRANSFERED_TO_CLIENT, null);
			type.addAttribute(createAttributeWithId("name", AttributeType.STRING, -11));
			type.addAttribute(createAttributeWithId("start", AttributeType.DATE, -12));
			type.addAttribute(createAttributeWithId("end", AttributeType.DATE, -13));
			type.setAnnotation(DynamicTypeAnnotations.KEY_NAME_FORMAT,"{name}");
			type.setResolver( this);
			type.setReadOnly();
			cache.put( type);
		}
    }
	
	public LocalAbstractCachableOperator(RaplaContext context, Logger logger) throws RaplaException {
		super( context, logger);
		scheduler = context.lookup( CommandScheduler.class);
	}

	public void runWithReadLock(CachableStorageOperatorCommand cmd) throws RaplaException
	{
		Lock readLock = readLock();
		try
		{
			cmd.execute( cache );
		}
		finally
		{
			unlock( readLock);
		}
	}

	public List<Reservation> getReservations(User user, Collection<Allocatable> allocatables, Date start, Date end, ClassificationFilter[] filters,Map<String,String> annotationQuery) throws RaplaException {
		boolean excludeExceptions = false;
		HashSet<Reservation> reservationSet = new HashSet<Reservation>();
		if (allocatables == null || allocatables.size() ==0) 
		{
			allocatables = Collections.singleton( null);
		}
		
        for ( Allocatable allocatable: allocatables)
        {
        	Lock readLock = readLock();
			SortedSet<Appointment> appointments;
			try
			{
				appointments = getAppointments( allocatable);
			}
			finally
			{
				unlock( readLock);
			}
			SortedSet<Appointment> appointmentSet = AppointmentImpl.getAppointments(appointments,user,start,end, excludeExceptions);
			for (Appointment appointment:appointmentSet)
			{
	            Reservation reservation = appointment.getReservation();
                if ( !match(reservation, annotationQuery) )
                {
                    continue;
                } // Ignore Templates if not explicitly requested
                // FIXME this special case should be refactored, so one can get all reservations in one method
                else if ( RaplaComponent.isTemplate( reservation) &&  (annotationQuery == null || !annotationQuery.containsKey(ReservationAnnotations.KEY_TEMPLATE) ))
                {
                    continue;
                }
	            if ( !reservationSet.contains( reservation))
	            {
	            	reservationSet.add( reservation );
	            }
			}
        }
        ArrayList<Reservation> result = new ArrayList<Reservation>(reservationSet);
        removeFilteredClassifications(result, filters);
		return result;
	}

    public boolean match(Reservation reservation, Map<String, String> annotationQuery) {
        if ( annotationQuery != null)
        {
        	for (String key : annotationQuery.keySet())
        	{
        		String annotationParam = annotationQuery.get( key);
        		String annotation = reservation.getAnnotation( key);
        		if ( annotation == null || annotationParam == null)
        		{
        			if (annotationParam!= null)
        			{
        			    return false;
        			}
        		}
        		else
        		{
        			if ( !annotation.equals(annotationParam))
        			{
                        return false;
        			}
        		}
        	}
        }
        return true;
    }

	public Collection<String> getTemplateNames() throws RaplaException {
    	Lock readLock = readLock();
    	Collection<Reservation> reservations;
    	try
    	{
    		reservations = cache.getReservations();
    	}
    	finally 
    	{
    		unlock(readLock);
    	}    		
		//Reservation[] reservations = cache.getReservations(user, start, end, filters.toArray(ClassificationFilter.CLASSIFICATIONFILTER_ARRAY));
        
    	Set<String> templates = new LinkedHashSet<String>();
        for ( Reservation r:reservations)
        {
        	String templateName = r.getAnnotation(ReservationAnnotations.KEY_TEMPLATE);
        	if ( templateName != null)
        	{
				templates.add( templateName);
        	}
        }
        return templates;
	}
	
	public String[] createIdentifier(RaplaType raplaType, int count) throws RaplaException {
        String[] ids = new String[ count];
        synchronized ( idTable) {
        	for ( int i=0;i<count;i++)
            {
            	ids[i] = idTable.createId(raplaType);
            }
		}
        return ids;
    }
	
	public Date today() {
		long time = getCurrentTimestamp().getTime();
		long offset = TimeZoneConverterImpl.getOffset( DateTools.getTimeZone(), systemTimeZone, time);
		Date raplaTime = new Date(time + offset);
		return DateTools.cutDate( raplaTime);
	}
	
	public Date getCurrentTimestamp() {
		long time = System.currentTimeMillis();
		return new Date( time); 
	}

	public void setTimeZone( TimeZone timeZone)
	{
		systemTimeZone = timeZone;
	}
	
	public TimeZone getTimeZone()
	{
		return systemTimeZone;
	}
	
	public String authenticate(String username, String password)
			throws RaplaException {
		Lock readLock = readLock();
		try {
			getLogger().info("Check password for User " + username);
			User user = cache.getUser(username);
			if (user != null)
			{
				String userId = user.getId();
				if (checkPassword(userId, password)) 
				{
					return userId;
				}
			}
			getLogger().warn("Login failed for " + username);
			throw new RaplaSecurityException(i18n.getString("error.login"));
		}
		finally
		{
			unlock( readLock );
		}
	}

	public boolean canChangePassword() throws RaplaException {
		return true;
	}

	public void changePassword(User user, char[] oldPassword,char[] newPassword) throws RaplaException {
		getLogger().info("Change password for User " + user.getUsername());
		Object userId = (user).getId();
		String password = new String(newPassword);
		if (encryption != null)
			password = encrypt(encryption, password);
		Lock writeLock = writeLock(  );
		try
		{
			cache.putPassword(userId, password);
		}
		finally
		{
			unlock( writeLock );
		}
		User editObject = editObject(user, null);
		List<Entity> editList = new ArrayList<Entity>(1);
		editList.add(editObject);
		Collection<Entity>removeList = Collections.emptyList();
		// synchronization will be done in the dispatch method
		storeAndRemove(editList, removeList, user);
	}

	public void changeName(User user, String title,String firstname, String surname) throws RaplaException {
		User editableUser = editObject(user,  user);
		Allocatable personReference =  editableUser.getPerson();
		if (personReference == null) {
			editableUser.setName(surname);
			storeUser(editableUser);
		} else {
			Allocatable editablePerson = editObject(personReference,	null);
			Classification classification = editablePerson.getClassification();
			{
				Attribute attribute = classification.getAttribute("title");
				if (attribute != null) {
					classification.setValue(attribute, title);
				}
			}
			{
				Attribute attribute = classification.getAttribute("firstname");
				if (attribute != null) {
					classification.setValue(attribute, firstname);
				}
			}
			{
				Attribute attribute = classification.getAttribute("surname");
				if (attribute != null) {
					classification.setValue(attribute, surname);
				}
			}
			ArrayList<Entity> arrayList = new ArrayList<Entity>();
			arrayList.add(editableUser);
			arrayList.add(editablePerson);
			Collection<Entity> storeObjects = arrayList;
			Collection<Entity> removeObjects = Collections.emptySet();
			// synchronization will be done in the dispatch method
			storeAndRemove(storeObjects, removeObjects, null);
		}
	}

	public void changeEmail(User user, String newEmail)	throws RaplaException {
		User editableUser = user.isReadOnly() ? editObject(user, (User) user) : user;
		Allocatable personReference = editableUser.getPerson();
		ArrayList<Entity>arrayList = new ArrayList<Entity>();
		Collection<Entity>storeObjects = arrayList;
		Collection<Entity>removeObjects = Collections.emptySet();
		storeObjects.add(editableUser);
		if (personReference == null) {
			editableUser.setEmail(newEmail);
		} else {
			Allocatable editablePerson = editObject(personReference,	null);
			Classification classification = editablePerson.getClassification();
			classification.setValue("email", newEmail);
			storeObjects.add(editablePerson);
		}
		storeAndRemove(storeObjects, removeObjects, null);
	}
	
	protected void resolveInitial(Collection<? extends Entity> entities) throws RaplaException {
		testResolve(entities);
		
		for (Entity obj: entities) {
			((RefEntity)obj).setResolver(this);
		}
		
		// resolve emails
		Map<String,Allocatable> resolvingMap = new HashMap<String,Allocatable>();
		for (Entity entity: entities)
    	{
			if ( entity instanceof Allocatable)
			{
				Allocatable allocatable = (Allocatable) entity;
	    		final Classification classification = allocatable.getClassification();
	    		final Attribute attribute = classification.getAttribute("email");
	    		if ( attribute != null)
	    		{
	    			final String email = (String)classification.getValue(attribute);
	    			if ( email != null )
	    			{
	    				resolvingMap.put( email, allocatable);
	    			}
	    		}
			}
        }	
		for ( Entity entity: entities)
		{
			if ( entity.getRaplaType().getTypeClass() == User.class)
			{
				User user = (User)entity;
				String email = user.getEmail();
				if ( email != null && email.trim().length() > 0)
				{
					Allocatable person = resolvingMap.get(email);
					if ( person != null)
					{
						user.setPerson(person);
					}
				}
			}
		}

		// It is important to do the read only later because some resolve might involve write to referenced objects
		for (Entity entity: entities) {
			 ((RefEntity)entity).setReadOnly();
		}
	}
	
	public void confirmEmail(User user, String newEmail)	throws RaplaException {
		throw new RaplaException("Email confirmation must be done in the remotestorage class");
	}
	
    public Collection<Conflict> getConflicts(User user) throws RaplaException
    {
    	Lock readLock = readLock();
    	try
		{
			return conflictFinder.getConflicts( user);
		}
		finally
		{
			unlock( readLock );
		}			
    }
        
    boolean disposing;
    public void dispose() {
    	// prevent reentrance in dispose
    	synchronized ( this)
    	{
	    	if ( disposing)
	    	{
	    		getLogger().warn("Disposing is called twice",new RaplaException(""));
	    		return;
	    	}
	    	disposing = true;
    	}
    	try
    	{
    		if ( cleanConflictsTask != null)
    		{
    			cleanConflictsTask.cancel();
    		}
    		forceDisconnect();
    	}
    	finally
    	{
    		disposing = false;
    	}
    }

    protected void forceDisconnect() {
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
	protected void check(final UpdateEvent evt, final EntityStore store) throws RaplaException {
		Set<Entity> storeObjects = new HashSet<Entity>(evt.getStoreObjects());
		//Set<Entity> removeObjects = new HashSet<Entity>(evt.getRemoveObjects());
		checkConsistency(evt, store);
		checkUnique(evt,store);
		checkReferences(evt, store);
		checkNoDependencies(evt, store);
		checkVersions(storeObjects);
	}
	
	class TimestampComparator implements Comparator<Timestamp>
	{
		public int compare(Timestamp o1, Timestamp o2) {
			if ( o1 == o2)
			{
				return 0;
			}
			Date d1 = o1.getLastChanged();
			Date d2 = o2.getLastChanged();
			// if d1 is null and d2 is not then d1 is before d2
			if ( d1 == null && d2 != null  )
			{
				return -1;
			}
			// if d2 is null and d1 is not then d2 is before d1
			if ( d1 != null  && d2 == null)
			{
				return 1;
			}
			if ( d1 != null && d2 != null)
			{
				int result =  d1.compareTo( d2);
				if ( result != 0)
				{
					return result;
				}
			}
			String id1 = o1.getId();
			String id2 = o2.getId();
		     if ( id1 == null)
		     {
		       	 if ( id2 == null)
		       	 {
		       		throw new IllegalStateException("Can't compare two entities without ids");
		       	 }
		       	 else
		       	 {
		       		return -1; 
		       	 }
		     }
		     else if ( id2 == null)
		     {
		    	 return 1;
		     }
		     return id1.compareTo( id2 );
		}
		
	}
	
	protected void initIndizes() {
		timestampSet = new TreeSet<Timestamp>(new TimestampComparator());
		timestampSet.addAll( cache.getDynamicTypes());
		timestampSet.addAll( cache.getReservations());
		timestampSet.addAll( cache.getAllocatables());
		timestampSet.addAll( cache.getUsers());
		// The appointment map
		appointmentMap = new HashMap<String, SortedSet<Appointment>>();
		Collection<Reservation> reservations = cache.getReservations();
    	for ( Reservation r: reservations)
    	{
			for ( Appointment app:((ReservationImpl)r).getAppointmentList())
			{
				Reservation reservation = app.getReservation();
				Allocatable[] allocatables = reservation.getAllocatablesFor(app);
				{
					Collection<Appointment> list = getAndCreateList(appointmentMap,null);
					list.add( app);
				}
				for ( Allocatable alloc:allocatables)
				{
					Collection<Appointment> list = getAndCreateList(appointmentMap,alloc);
					list.add( app);
				}
			}
    	}
		Date today2 = today();
		AllocationMap allocationMap = new AllocationMap() {
			    public SortedSet<Appointment> getAppointments(Allocatable allocatable)
			    {
			    	return LocalAbstractCachableOperator.this.getAppointments(allocatable);
			    }
			    public Collection<Allocatable> getAllocatables()
			    {
			    	return cache.getAllocatables();
			    }
		};
		// The conflict map
		conflictFinder = new ConflictFinder(allocationMap, today2, getLogger(), this);
		long delay = DateTools.MILLISECONDS_PER_HOUR;
		long period = DateTools.MILLISECONDS_PER_HOUR;
		Command cleanUpConflicts = new Command() {
			
			@Override
			public void execute() throws Exception {
				removeOldConflicts();
			}
		};
		cleanConflictsTask = scheduler.schedule( cleanUpConflicts, delay, period);
	}
	
	/** updates the bindings of the resources and returns a map with all processed allocation changes*/
	private void updateIndizes(UpdateResult result) {
		Map<Allocatable,AllocationChange> toUpdate = new HashMap<Allocatable,AllocationChange>();
		List<Allocatable> removedAllocatables = new ArrayList<Allocatable>();
		for (UpdateOperation operation: result.getOperations())
		{
			Entity current = operation.getCurrent();
			RaplaType raplaType = current.getRaplaType();
			if ( raplaType ==  Reservation.TYPE )
			{
				if ( operation instanceof UpdateResult.Remove)
				{
					Reservation old = (Reservation) current;
					for ( Appointment app: old.getAppointments() )
					{
						updateBindings( toUpdate, app, true);
					}
				}
				if ( operation instanceof UpdateResult.Add)
				{
					Reservation newReservation = (Reservation) ((UpdateResult.Add) operation).getNew();
					for ( Appointment app: newReservation.getAppointments() )
					{
						updateBindings( toUpdate, app, false);
					}
				}
				if ( operation instanceof UpdateResult.Change)
				{
					Reservation oldReservation = (Reservation) ((UpdateResult.Change) operation).getOld();
					Reservation newReservation =(Reservation) ((UpdateResult.Change) operation).getNew();
					Appointment[] oldAppointments =  oldReservation.getAppointments();
					for ( Appointment oldApp: oldAppointments)
					{
						updateBindings( toUpdate, oldApp, true);
					}
					Appointment[] newAppointments =  newReservation.getAppointments();
					for ( Appointment newApp: newAppointments)
					{
						updateBindings( toUpdate, newApp, false);
					}
				}
			}
			if ( raplaType ==  Allocatable.TYPE )
			{
				if ( operation instanceof UpdateResult.Remove)
				{
					Allocatable old = (Allocatable) current;
					removedAllocatables.add( old);
				}
			}
			if (raplaType == Allocatable.TYPE || raplaType == Reservation.TYPE || raplaType == DynamicType.TYPE || raplaType == User.TYPE || raplaType == Preferences.TYPE )
			{
				if ( operation instanceof UpdateResult.Remove)
				{
					Timestamp old = (Timestamp) current;
					timestampSet.remove( old);
				}
				if ( operation instanceof UpdateResult.Add)
				{
					Timestamp newEntity = (Timestamp) ((UpdateResult.Add) operation).getNew();
					timestampSet.add( newEntity);
				}
				if ( operation instanceof UpdateResult.Change)
				{
					Timestamp newEntity = (Timestamp) ((UpdateResult.Change) operation).getNew();
					Timestamp oldEntity = (Timestamp) ((UpdateResult.Change) operation).getOld();
					timestampSet.remove( oldEntity);
					timestampSet.add( newEntity);
				}
			}
		}

		for ( Allocatable alloc: removedAllocatables)
		{
			SortedSet<Appointment> sortedSet = appointmentMap.get( alloc);
			if ( sortedSet != null && !sortedSet.isEmpty())
			{
				getLogger().error("Removing non empty appointment map for resource " +  alloc + " Appointments:" + sortedSet);
			}
			appointmentMap.remove( alloc);
		}
	   	Date today = today();
	   	// processes the conflicts and adds the changes to the result
		conflictFinder.updateConflicts(toUpdate,result, today, removedAllocatables);
		checkAbandonedAppointments();
	}
	
	@Override
	public Collection<Entity> getUpdatedEntities(final Date timestamp) throws RaplaException {

		Timestamp fromElement = new Timestamp() {
			
			@Override
			public User getLastChangedBy() {
				return null;
			}
			
			@Override
			public Date getLastChanged() {
				return timestamp;
			}
			
			@Override
			public Date getLastChangeTime() {
				return getLastChanged();
			}
			
			@Override
			public String getId() {
				return "";
			}
			
			@Override
			public Date getCreateTime() 
			{
				return timestamp;
			}
		};
		Lock lock = readLock();
		Collection<Entity> result = new ArrayList<Entity>();
		try
		{
			SortedSet<Timestamp> tailSet = timestampSet.tailSet(fromElement);
			for ( Timestamp entry : tailSet)
			{
				result.add( (Entity) entry );
			}
		}
		finally
		{
			unlock(lock);
		}
		return result;
	}

	protected void updateBindings(Map<Allocatable, AllocationChange> toUpdate,Appointment app, boolean remove)  {
		
		Set<Allocatable> allocatablesToProcess = new HashSet<Allocatable>();
		allocatablesToProcess.add( null);
		Reservation reservation = app.getReservation();
		if ( reservation != null)
		{
			Allocatable[] allocatablesFor = reservation.getAllocatablesFor( app);
			allocatablesToProcess.addAll( Arrays.asList(allocatablesFor));
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
		
		for ( Allocatable allocatable: allocatablesToProcess)
		{
			AllocationChange updateSet;
			if ( allocatable != null)
			{
				updateSet = toUpdate.get( allocatable);
				if ( updateSet == null)
				{
					updateSet = new AllocationChange();
					toUpdate.put(allocatable, updateSet);
				}
			}
			else
			{
				updateSet = null;
			}
			if ( remove)
			{
				Collection<Appointment> appointmentSet = getAndCreateList(appointmentMap,allocatable);
				// binary search could fail if the appointment has changed since the last add, which should not 
				// happen as we only put and search immutable objects in the map. But the method is left here as a failsafe 
				// with a log messaget
				if (!appointmentSet.remove( app)) 
				{
					getLogger().error("Appointent has changed, so its not found in indexed binding map. Removing via full search");
					// so we need to traverse all appointment
					Iterator<Appointment> it = appointmentSet.iterator();
					while (it.hasNext())
					{
						if (app.equals(it.next())) {
							it.remove();
							break;
						}
					}
				}
				if ( updateSet != null)
				{
					updateSet.toRemove.add( app);
				}
			}
			else
			{
				SortedSet<Appointment> appointmentSet = getAndCreateList(appointmentMap, allocatable);
				appointmentSet.add(app);
				if ( updateSet != null)
				{
					updateSet.toChange.add( app);
				}
			}
		}
	}

	static final SortedSet<Appointment> EMPTY_SORTED_SET = Collections.unmodifiableSortedSet( new TreeSet<Appointment>());
	protected SortedSet<Appointment> getAppointments(Allocatable allocatable)
    {
		String allocatableId = allocatable != null ? allocatable.getId() : null;
		SortedSet<Appointment> s = appointmentMap.get( allocatableId);
    	if ( s == null)
    	{
    		return EMPTY_SORTED_SET; 
    	}
		return Collections.unmodifiableSortedSet(s);
    }

	private SortedSet<Appointment> getAndCreateList(Map<String,SortedSet<Appointment>> appointmentMap,Allocatable alloc) {
		String allocationId = alloc != null ? alloc.getId() : null;
		SortedSet<Appointment> set = appointmentMap.get( allocationId);
		if ( set == null)
		{
			set = new TreeSet<Appointment>(new AppointmentStartComparator());
			appointmentMap.put(allocationId, set);
		}
		return set;
	}
	
    @Override
	protected UpdateResult update(UpdateEvent evt)
			throws RaplaException {
		UpdateResult update = super.update(evt);
	   	updateIndizes(update);
		return update;
	}
    
    public void removeOldConflicts() throws RaplaException
    {
    	Map<Entity,Entity> oldEntities = new LinkedHashMap<Entity,Entity>();
		Collection<Entity>updatedEntities = new LinkedHashSet<Entity>();
		Collection<Entity>toRemove  = new LinkedHashSet<Entity>();
		TimeInterval invalidateInterval = null;
		String userId = null;
		UpdateResult result = createUpdateResult(oldEntities, updatedEntities, toRemove, invalidateInterval, userId);
		//Date today = getCurrentTimestamp();
		Date today = today();
		Lock readLock = readLock();
		try
		{
			conflictFinder.removeOldConflicts(result, today);
    	}
    	finally
    	{
    		unlock( readLock);
    	}
		fireStorageUpdated( result );
    }
	
	protected UpdateEvent checkAndCreateClosure(final UpdateEvent evt) throws RaplaException {
		EntityStore store = new EntityStore(this, this.getSuperCategory());
    	Collection<Entity>storeObjects = evt.getStoreObjects();
		store.addAll(storeObjects);
        for (Entity entity:storeObjects) {
            if (getLogger().isDebugEnabled())
                getLogger().debug("Contextualizing " + entity);
            ((EntityReferencer)entity).setResolver( store);
            if ( entity instanceof Category)
            {
            	Set<Category> children = getAllCategories( (Category)entity);
            	store.addAll(children);
            }
        }
        Collection<Entity>removeObjects = evt.getRemoveObjects();
        store.addAll( removeObjects );

		// add all child categories to store
	
        for ( Entity entity:removeObjects)
        {
            ((EntityReferencer)entity).setResolver( store);
        }
		final UpdateEvent closure = createClosure( evt, store );
		check( closure, store);
		return closure;
	}

	/**
	 * Create a closure for all objects that should be updated. The closure
	 * contains all objects that are sub-entities of the entities and all
	 * objects and all other objects that are affected by the update: e.g.
	 * Classifiables when the DynamicType changes. The method will recursivly
	 * proceed with all discovered objects.
	 */
	protected UpdateEvent createClosure(final UpdateEvent evt,EntityStore store) throws RaplaException {
		UpdateEvent closure = evt.clone();
		for (Entity entity: evt.getStoreObjects()) 
		{
			addStoreOperationsToClosure(closure, store,entity);
		}
		for (Entity entity: evt.getStoreObjects()) {
			// update old classifiables, that may not been update before via a change event
			// that could be the case if an old reservation is restored via undo but the dynamic type changed in between. 
			// The undo cache does not notice the change in type   
			if ( entity instanceof Classifiable && entity instanceof Timestamp)
			{
				Date lastChanged = ((Timestamp) entity).getLastChanged();
				ClassificationImpl classification = (ClassificationImpl) ((Classifiable) entity).getClassification();
				DynamicTypeImpl dynamicType = classification.getType();
				Date typeLastChanged = dynamicType.getLastChanged();
				if ( typeLastChanged != null  && lastChanged != null  && typeLastChanged.after( lastChanged))
				{
					if (classification.needsChange(dynamicType))
					{
						addChangedDependencies(evt, store, dynamicType, entity, false);
					}
				}
			}
		}

		for (Entity object: evt.getRemoveObjects()) 
		{
			addRemoveOperationsToClosure(closure, store, object);
		}
		Set<Entity> deletedCategories = getDeletedCategories(evt.getStoreObjects());
		for (Entity entity: deletedCategories)
		{
			closure.putRemove(entity);
		}
		return closure;
	}

	protected void addStoreOperationsToClosure(UpdateEvent evt, EntityStore store,Entity entity) throws RaplaException {
		if (getLogger().isDebugEnabled() && !evt.getStoreObjects().contains(entity)) {
			getLogger().debug("Adding " + entity + " to store closure");
		}
		evt.putStore(entity);
		if (DynamicType.TYPE == entity.getRaplaType()) {
			DynamicTypeImpl dynamicType = (DynamicTypeImpl) entity;
			addChangedDynamicTypeDependant(evt,store, dynamicType, false);
		}
	}

	private void addRemoveOperationsToClosure(UpdateEvent evt,EntityStore store,Entity entity) throws RaplaException {
		if (getLogger().isDebugEnabled() && !evt.getRemoveObjects().contains(entity)) {
			getLogger().debug("Adding " + entity + " to remove closure");
		}
		evt.putRemove(entity);

		if (DynamicType.TYPE == entity.getRaplaType()) {
			DynamicTypeImpl dynamicType = (DynamicTypeImpl) entity;
			addChangedDynamicTypeDependant(evt, store,dynamicType, true);
		}
		
		// If entity is a user, remove the preference object
		if (User.TYPE == entity.getRaplaType()) {
			addRemovedUserDependant(evt, store,(User) entity);
		}
		
	}

	protected void setCache(final LocalCache cache) {
		super.setCache( cache);
		if ( idTable == null)
		{
			idTable = new IdTable();
		}
		idTable.setCache(cache);
	}
	

	protected void addChangedDynamicTypeDependant(UpdateEvent evt, EntityStore store,DynamicTypeImpl type, boolean toRemove) throws RaplaException {
		List<Entity> referencingEntities = getReferencingEntities( type, store);
		Iterator<Entity>it = referencingEntities.iterator();
		while (it.hasNext()) {
			Entity entity = it.next();
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
			addChangedDependencies(evt, store, type,  entity, toRemove);
		}
	}

	private void addChangedDependencies(UpdateEvent evt,EntityStore store, DynamicTypeImpl type, Entity entity,boolean toRemove) throws EntityNotFoundException, RaplaException {
		DynamicTypeDependant dependant;
		if (evt.getStoreObjects().contains(entity)) {
			dependant = (DynamicTypeDependant) evt.findEntity(entity);
		} else {
			// no, then create a clone of the classfiable object and add to list
			User user = null;
			if (evt.getUserId() != null) {
				user = (User) resolveIdWithoutSync(evt.getUserId());
			}
			Entity persistant = store.tryResolve(entity.getId());
			dependant = (DynamicTypeDependant) editObject( entity, persistant, user);
			// replace or add the modified entity
			addStoreOperationsToClosure(evt, store,(Entity) dependant);
		} 
		if (toRemove) {
			try {
				dependant.commitRemove(type);
			} catch (CannotExistWithoutTypeException ex) {
				// getLogger().warn(ex.getMessage(),ex);
			}
		} else {
			dependant.commitChange(type);
		}
	}
	
	protected void addRemovedUserDependant(UpdateEvent evt, EntityStore store,User user) throws RaplaException {
		PreferencesImpl preferences = cache.getPreferencesForUserId(user.getId());
		if (preferences != null)
		{
			evt.putRemove(preferences);
		}
		List<Entity>referencingEntities = getReferencingEntities( user, store);
		Iterator<Entity>it = referencingEntities.iterator();
		while (it.hasNext()) {
			Entity entity = it.next();
			// Remove internal resources automatically if the owner is deleted
			if ( entity instanceof Classifiable  && entity instanceof Ownable)
			{
				DynamicType type = ((Classifiable) entity).getClassification().getType();
				if (((DynamicTypeImpl)type).isInternal())
				{
					User owner = ((Ownable)entity).getOwner();
					if ( owner != null && owner.equals( user))
					{
						evt.putRemove( entity);
						continue;
					}
				}
			}
			if (entity instanceof Timestamp) {
				Timestamp timestamp = (Timestamp) entity;
				User lastChangedBy = timestamp.getLastChangedBy();
				if ( lastChangedBy == null || !lastChangedBy.equals( user) )
				{
					continue;
				}
				if ( entity instanceof Ownable  )
				{
					 User owner = ((Ownable)entity).getOwner();
					 // we do nothing if the user is also owner,  that dependencies need to be resolved manually
					 if ( owner != null && owner.equals(user))
					 {
						 continue;
					 }
				}
				if (evt.getStoreObjects().contains(entity)) 
				{
					((SimpleEntity)evt.findEntity(entity)).setLastChangedBy(null);
				}
				else
				{
					Entity persistant= cache.tryResolve( entity.getId());
					Entity dependant = editObject( entity, persistant, user);
					((SimpleEntity)dependant).setLastChangedBy( null );
					addStoreOperationsToClosure(evt, store, dependant);
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
	final protected Set<Entity> getDependencies(Entity entity, EntityStore store)  {
		HashSet<Entity> dependencyList = new HashSet<Entity>();
		RaplaType type = entity.getRaplaType();
		final Collection<Entity>referencingEntities;
		if (Category.TYPE == type || DynamicType.TYPE == type || Allocatable.TYPE == type || User.TYPE == type) {
			referencingEntities = getReferencingEntities(entity, store);
		} else {
			referencingEntities = cache.getReferers(Preferences.class, entity);
		}
		if ( entity instanceof User)
		{
		}
		dependencyList.addAll(referencingEntities);
		return dependencyList;
	}

	protected List<Entity> getReferencingEntities(Entity entity, EntityStore store) {
		ArrayList<Entity> list = new ArrayList<Entity>();
		list.addAll(cache.getReferers(Reservation.class, entity));
		list.addAll(cache.getReferers(Allocatable.class, entity));
		list.addAll(cache.getReferers(Preferences.class, entity));
		list.addAll(cache.getReferers(User.class, entity));
		list.addAll(cache.getReferers(DynamicType.class, entity));
		return list;
	}

	private int countDynamicTypes(Collection<? extends RaplaObject> entities, String classificationType) throws RaplaException {
		Iterator<? extends RaplaObject> it = entities.iterator();
		int count = 0;
		while (it.hasNext()) {
			RaplaObject entity = it.next();
			if (DynamicType.TYPE != entity.getRaplaType())
				continue;
			DynamicType type = (DynamicType) entity;
			String annotation = type.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
			if ( annotation == null)
			{
				throw new RaplaException(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE + " not set for " + type);
			}
			if (annotation.equals(	classificationType)) {
				count++;
			}
		}
		return count;
	}

	// Count dynamic-types to ensure that there is least one dynamic type left
	private void checkDynamicType(Collection<Entity>entities, String[] classificationTypes) throws RaplaException {
		int count = 0;
		for ( String classificationType: classificationTypes)
		{
			count += countDynamicTypes(entities, classificationType);
		}
		Collection<DynamicType> allTypes = cache.getDynamicTypes();
		int countAll = 0;
		for ( String classificationType: classificationTypes)
		{
			countAll = countDynamicTypes(allTypes, classificationType);
		}
		if (count >= 0	&& count >= countAll) {
			throw new RaplaException(i18n.getString("error.one_type_requiered"));
		}
	}

	/**
	 * Check if the references of each entity refers to an object in cache or in
	 * the passed collection.
	 */
	final protected void checkReferences(UpdateEvent evt, EntityStore store)	throws RaplaException {
		
		for (Entity entity: evt.getStoreObjects()) {
			for (String id: ((RefEntity)entity).getReferencedIds())
			{				
				// Reference in cache or store?
				if (store.tryResolve(id) != null)
					continue;
			
				throw new EntityNotFoundException(i18n.format("error.reference_not_stored", id));
			}
		}
	}

	/**
	 * check if we find an object with the same name. If a different object
	 * (different id) with the same unique attributes is found a
	 * UniqueKeyException will be thrown.
	 */
	final protected void checkUnique(final UpdateEvent evt, final EntityStore store)	throws RaplaException {
		for (Entity entity : evt.getStoreObjects()) {
			String name = "";
			Entity entity2 = null;
			if (DynamicType.TYPE == entity.getRaplaType()) {
				DynamicType type = (DynamicType) entity;
				name = type.getElementKey();
				entity2 = (Entity) store.getDynamicType(name);
				if (entity2 != null && !entity2.equals(entity))
					throwNotUnique(name);
			}

			if (Category.TYPE == entity.getRaplaType()) {
				Category category = (Category) entity;
				Category[] categories = category.getCategories();
				for (int i = 0; i < categories.length; i++) {
					String key = categories[i].getKey();
					for (int j = i + 1; j < categories.length; j++) {
						String key2 = categories[j].getKey();
						if (key == key2 || (key != null && key.equals(key2))) {
							throwNotUnique(key);
						}
					}
				}
			}

			if (User.TYPE == entity.getRaplaType()) {
				name = ((User) entity).getUsername();
				if (name == null || name.trim().length() == 0) {
					String message = i18n.format("error.no_entry_for", getString("username"));
					throw new RaplaException(message);
				}
				// FIXME Replace with store.getUser for the rare case that two users with the same username are stored in one operation
				entity2 = cache.getUser(name);
				if (entity2 != null && !entity2.equals(entity))
					throwNotUnique(name);
			}
		}
	}

	private void throwNotUnique(String name) throws UniqueKeyException {
		throw new UniqueKeyException(i18n.format("error.not_unique", name));
	}

	/**
	 * compares the version of the cached entities with the versions of the new
	 * entities. Throws an Exception if the newVersion != cachedVersion
	 */
	protected void checkVersions(Collection<Entity>entities)	throws RaplaException {
		Iterator<Entity>it = entities.iterator();
		while (it.hasNext()) {
			// Check Versions
			SimpleEntity entity = (SimpleEntity) it.next();
			SimpleEntity persistantVersion = (SimpleEntity) findInLocalCache((Entity)entity);
			// If the entities are newer, everything is o.k.
			if (persistantVersion != null && persistantVersion != entity)
			{
				if (( persistantVersion instanceof Timestamp))
				{
					Date lastChangeTimePersistant = ((Timestamp)persistantVersion).getLastChanged();
					Date lastChangeTime = ((Timestamp)entity).getLastChanged();
					if ( lastChangeTimePersistant != null && lastChangeTime != null && lastChangeTimePersistant.after( lastChangeTime) )
					{
						getLogger().warn(
								"There is a newer  version for: " + entity.getId()
										+ " stored version :"
										+ SerializableDateTimeFormat.INSTANCE.formatTimestamp(lastChangeTimePersistant)
										+ " version to store :" + SerializableDateTimeFormat.INSTANCE.formatTimestamp(lastChangeTime));
						throw new RaplaNewVersionException(getI18n().format(
								"error.new_version", entity.toString()));						
					}
				}

			}
		}
	}
	
	/** Check if the objects are consistent, so that they can be safely stored. */
	protected void checkConsistency(UpdateEvent evt, EntityStore store) throws RaplaException {
		for (Entity entity : evt.getStoreObjects()) {
			for (String referencedIds:((RefEntity)entity).getReferencedIds())
			{
				Entity reference = store.resolve( referencedIds);
				if (reference instanceof Preferences
						|| reference instanceof Conflict
						|| reference instanceof Reservation
						|| reference instanceof Appointment
						)
				{
					throw new RaplaException("The current version of Rapla doesn't allow references to objects of type "	+ reference.getRaplaType());
				}
			}
			
			CategoryImpl superCategory = store.getSuperCategory();
			if (Category.TYPE == entity.getRaplaType()) {
				if (entity.equals(superCategory)) {
					// Check if the user group is missing
					Category userGroups = ((Category) entity).getCategory(Permission.GROUP_CATEGORY_KEY);
					if (userGroups == null) {
						throw new RaplaException("The category with the key '"
								+ Permission.GROUP_CATEGORY_KEY
								+ "' is missing.");
					}
				} else {
					// check if the category to be stored has a parent
					Category category = (Category) entity;
					Category parent = category.getParent();
					if (parent == null) {
						throw new RaplaException("The category " + category
								+ " needs a parent.");
					}
					else 
					{
						int i = 0;
						while ( true)
						{
							if ( parent == null)
							{
								throw new RaplaException("Category needs to be a child of super category.");
							} 
							else if ( parent.equals( superCategory))
							{
								break;
							}
							parent = parent.getParent();
							i++;
							if ( i>80)
							{
								throw new RaplaException("infinite recursion detection for category " + category);
							}
						}
					}
				}
			}
		}
	}

	protected void checkNoDependencies(final UpdateEvent evt, final EntityStore store) throws RaplaException {
		Collection<Entity> removeEntities = evt.getRemoveObjects();
		Collection<Entity> storeObjects = new HashSet<Entity>(evt.getStoreObjects());
		HashSet<Entity> dep = new HashSet<Entity>();
		Set<Entity> deletedCategories = getDeletedCategories(storeObjects);
		IteratorChain<Entity> iteratorChain = new IteratorChain<Entity>( deletedCategories,removeEntities);
		for (Entity entity : iteratorChain) {
			// Add dependencies for the entity

			// First we add the dependencies from the stored object list
			for (Entity obj : storeObjects) {
				if (((EntityReferencer)obj).isRefering(entity.getId())) {
					dep.add(obj);
				}
			}
			if ( entity instanceof User)
			{
			    String eventUserId = evt.getUserId();
                if (eventUserId != null && eventUserId.equals( entity.getId()))
                {
                    List<String> emptyList = Collections.emptyList();
                    throw new DependencyException("User can't delete himself", emptyList);
                }
			}

			// Than we add the dependencies from the cache. It is important that
			// we don't add the dependencies from the stored object list here,
			// because a dependency could be removed in a stored object
			Set<Entity> dependencies = getDependencies(entity, store);
			for (Entity dependency : dependencies) {
				if (!storeObjects.contains(dependency) && !removeEntities.contains( dependency)) {
					// only add the first 21 dependencies;
					if (dep.size() > MAX_DEPENDENCY )
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
//							throw new DependencyException( "User " + user.getUsername() + " refers to " + getName(alloc) + ". Read permission is required.", Collections.singleton( getDependentName(reference)));
//						}
//					}
//				}
//			}
//		}
		
		if (dep.size() > 0) {
			Collection<String> names = new ArrayList<String>();
			for (Entity obj: dep)
			{				
				String string = getDependentName(obj);
				names.add(string);
			}
			throw new DependencyException(getString("error.dependencies"),names.toArray( new String[]{}));
		}
		// Count dynamic-types to ensure that there is least one dynamic type
		// for reservations and one for resources or persons
		checkDynamicType(removeEntities, new String[] {DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION});
		checkDynamicType(removeEntities, new String[] {DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE,DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON});
	}

	private Set<Entity> getDeletedCategories(Iterable<Entity> storeObjects) {
		Set<Entity> deletedCategories = new HashSet<Entity>();
		for (Entity entity : storeObjects) {
			if ( entity.getRaplaType() == Category.TYPE)
			{
				Category newCat = (Category) entity;
				Category old = (Category) findInLocalCache(entity);
				if ( old != null)
				{
					Set<Category> oldSet = getAllCategories( old);
					Set<Category> newSet = getAllCategories( newCat);
					oldSet.removeAll( newSet);
					deletedCategories.addAll( oldSet );
				}
			}
		}
		return deletedCategories;
	}

	private Set<Category> getAllCategories(Category old) {
		HashSet<Category> result = new HashSet<Category>();
		result.add( old);
		for (Category child : old.getCategories())
		{
			result.addAll( getAllCategories(child));
		}
		return result;
	}

	protected String getDependentName(Entity obj) {
		StringBuffer buf = new StringBuffer();
		if (obj instanceof Reservation) {
			buf.append(getString("reservation"));
		} else if (obj instanceof Preferences) {
			buf.append(getString("preferences"));
		} else if (obj instanceof Category) {
			buf.append(getString("categorie"));
		} else if (obj instanceof Allocatable) {
			buf.append(getString("resources_persons"));
		} else if (obj instanceof User) {
			buf.append(getString("user"));
		} else if (obj instanceof DynamicType) {
			buf.append(getString("dynamictype"));
		}
		if (obj instanceof Named) {
			Locale locale = i18n.getLocale();
			final String string = ((Named) obj).getName(locale);
			buf.append(": " + string);
		} else {
			buf.append(obj.toString());
		}
		if (obj instanceof Reservation) {
			Reservation reservation = (Reservation)obj;
			
			Appointment[] appointments = reservation.getAppointments();
			if ( appointments.length > 0)
			{
				buf.append(" ");
				Date start = appointments[0].getStart();
				buf.append(raplaLocale.formatDate(start));
			}
			
			String template = reservation.getAnnotation(ReservationAnnotations.KEY_TEMPLATE);
			if ( template != null)
			{
				buf.append(" in template " + template);
			}
		}
		final Object idFull = obj.getId();
		if (idFull != null) {
			String idShort = idFull.toString();
			int dot = idShort.lastIndexOf('.');
			buf.append(" (" + idShort.substring(dot + 1) + ")");
		}
		String string = buf.toString();
		return string;
	}

	/**
	 * @param entity  
	 */
	protected boolean isAddedToUpdateResult(Entity entity) {
		return true;
	}

	/**
	 * @param entity  
	 */
	protected boolean isStorableInCache(Entity entity) {
		return true;
	}
	

	private void storeUser(User refUser) throws RaplaException {
		ArrayList<Entity> arrayList = new ArrayList<Entity>();
		arrayList.add(refUser);
		Collection<Entity> storeObjects = arrayList;
		Collection<Entity> removeObjects = Collections.emptySet();
		storeAndRemove(storeObjects, removeObjects, null);
	}
	
	protected String encrypt(String encryption, String password) throws RaplaException {
		MessageDigest md;
		try {
			md = MessageDigest.getInstance(encryption);
		} catch (NoSuchAlgorithmException ex) {
			throw new RaplaException(ex);
		}
		synchronized (md) 
		{
			md.reset();
			md.update(password.getBytes());
			return encryption + ":" + Tools.convert(md.digest());
		}
	}

	private boolean checkPassword(Object userId, String password) throws RaplaException {
		if (userId == null)
			return false;

		String correct_pw = cache.getPassword(userId);
		if (correct_pw == null) {
			return false;
		}

		if (correct_pw.equals(password)) {
			return true;
		}

		int columIndex = correct_pw.indexOf(":");
		if (columIndex > 0 && correct_pw.length() > 20) {
			String encryptionGuess = correct_pw.substring(0, columIndex);
			if (encryptionGuess.contains("sha")	|| encryptionGuess.contains("md5")) {
				password = encrypt(encryptionGuess, password);
				if (correct_pw.equals(password)) {
					return true;
				}
			}
		}
		return false;
	}


	@Override
	public Map<Allocatable,Collection<Appointment>> getFirstAllocatableBindings(Collection<Allocatable> allocatables, Collection<Appointment> appointments, Collection<Reservation> ignoreList) throws RaplaException {
		Lock readLock = readLock();
		Map<Allocatable, Map<Appointment, Collection<Appointment>>> allocatableBindings;
		try
		{
			allocatableBindings = getAllocatableBindings(allocatables,	appointments, ignoreList,true);
		}
		finally
		{
			unlock( readLock);
		}
		Map<Allocatable, Collection<Appointment>> map = new HashMap<Allocatable, Collection<Appointment>>();
		for ( Map.Entry<Allocatable, Map<Appointment, Collection<Appointment>>> entry: allocatableBindings.entrySet())
		{
			Allocatable alloc = entry.getKey();
			Collection<Appointment> list = entry.getValue().keySet();
			map.put( alloc, list);
		}
		return map;
	}
	
	@Override
    public Map<Allocatable, Map<Appointment,Collection<Appointment>>> getAllAllocatableBindings(Collection<Allocatable> allocatables, Collection<Appointment> appointments, Collection<Reservation> ignoreList) throws RaplaException
    {
		Lock readLock = readLock();
		try
		{
			return getAllocatableBindings( allocatables, appointments, ignoreList, false);
	   	}
		finally
		{
			unlock( readLock );
		}
    }
	
	public Map<Allocatable, Map<Appointment,Collection<Appointment>>> getAllocatableBindings(Collection<Allocatable> allocatables,Collection<Appointment> appointments, Collection<Reservation> ignoreList, boolean onlyFirstConflictingAppointment) {
		Map<Allocatable, Map<Appointment,Collection<Appointment>>> map = new HashMap<Allocatable, Map<Appointment,Collection<Appointment>>>();
        for ( Allocatable allocatable:allocatables)
        {
            String annotation = allocatable.getAnnotation( ResourceAnnotations.KEY_CONFLICT_CREATION);
			boolean holdBackConflicts = annotation != null && annotation.equals( ResourceAnnotations.VALUE_CONFLICT_CREATION_IGNORE);
			if ( holdBackConflicts)
			{
				continue;
			}
			SortedSet<Appointment> appointmentSet = getAppointments( allocatable);
			if ( appointmentSet == null)
    		{
				continue;
    		}
			map.put(allocatable,  new HashMap<Appointment,Collection<Appointment>>() );
        	for (Appointment appointment:appointments)
        	{
    			Set<Appointment> conflictingAppointments = AppointmentImpl.getConflictingAppointments(appointmentSet, appointment, ignoreList, onlyFirstConflictingAppointment);
        		if ( conflictingAppointments.size() > 0)
        		{
	        		Map<Appointment,Collection<Appointment>> appMap = map.get( allocatable);
	        		if ( appMap == null)
	        		{
	        			appMap = new HashMap<Appointment, Collection<Appointment>>();
	        			map.put( allocatable, appMap);
	        		}
	        		appMap.put( appointment,  conflictingAppointments);
        		}
        	}
        }
        return map;
    }
	
	@Override
    public Date getNextAllocatableDate(Collection<Allocatable> allocatables,Appointment appointment,Collection<Reservation> ignoreList,Integer worktimeStartMinutes,Integer worktimeEndMinutes, Integer[] excludedDays, Integer rowsPerHour) throws RaplaException {
    	Lock readLock = readLock();
		try
		{
			Appointment newState = appointment;
			Date firstStart = appointment.getStart();
			boolean startDateExcluded = isExcluded(excludedDays, firstStart);
			boolean wholeDay = appointment.isWholeDaysSet();
			boolean inWorktime = inWorktime(appointment, worktimeStartMinutes,worktimeEndMinutes);
			if ( rowsPerHour == null || rowsPerHour <=1)
			{
				rowsPerHour = 1;
			}
			for ( int i=0;i<366*24 *rowsPerHour ;i++)
			{
				newState = ((AppointmentImpl) newState).clone();
				Date start = newState.getStart();
				long millisToAdd = wholeDay ? DateTools.MILLISECONDS_PER_DAY : (DateTools.MILLISECONDS_PER_HOUR / rowsPerHour );
				Date newStart = new Date(start.getTime() + millisToAdd);
				if (!startDateExcluded &&  isExcluded(excludedDays, newStart))
				{
					continue;
				}
				newState.move( newStart );
				if ( !wholeDay && inWorktime && !inWorktime(newState, worktimeStartMinutes, worktimeEndMinutes))
				{
					continue;
				}
				if  (!isAllocated(allocatables, newState, ignoreList))
				{
					return newStart;
				}
			}
			return null;
		}
		finally
		{
			unlock( readLock );
		}
    }

	private boolean inWorktime(Appointment appointment,
			Integer worktimeStartMinutes, Integer worktimeEndMinutes) {
		long start = appointment.getStart().getTime();
		int minuteOfDayStart = DateTools.getMinuteOfDay( start );
		long end = appointment.getEnd().getTime();
		int minuteOfDayEnd = DateTools.getMinuteOfDay( end ) + (int) DateTools.countDays(start, end) * 24 * 60;
		boolean inWorktime =  (worktimeStartMinutes == null || worktimeStartMinutes<= minuteOfDayStart) && ( worktimeEndMinutes == null || worktimeEndMinutes >= minuteOfDayEnd);
		return inWorktime;
	}

	private boolean isExcluded(Integer[] excludedDays, Date date) {
		Integer weekday = DateTools.getWeekday( date);
		if (excludedDays != null)
		{
			for ( Integer day:excludedDays)
			{
				if ( day.equals( weekday))
				{
					return true;
				}
			}
		}
		return false;
	}

	private boolean isAllocated(Collection<Allocatable> allocatables,
			Appointment appointment, Collection<Reservation> ignoreList) throws RaplaException 
	{
		Map<Allocatable, Collection<Appointment>> firstAllocatableBindings = getFirstAllocatableBindings(allocatables, Collections.singleton( appointment) , ignoreList);
		for (Map.Entry<Allocatable, Collection<Appointment>> entry: firstAllocatableBindings.entrySet())
		{
			if (entry.getValue().size() > 0)
			{
				return true;
			}
		}
		return false;
	}

    public List<Entity> getVisibleEntities(final User user)throws RaplaException {
		checkConnected();
		Lock readLock = readLock();
		try
		{
			List<Entity> result = new ArrayList<Entity>();
			result.add( getSuperCategory());
			@SuppressWarnings("deprecation")
			Set<Entry<RaplaType, Set<? extends Entity>>> entrySet = cache.entrySet();
			for ( Map.Entry<RaplaType,Set<? extends Entity>> entry:entrySet )
			{
				RaplaType raplaType = entry.getKey();
				if (   Conflict.TYPE.equals( raplaType ))
				{
					continue;
				}
				@SuppressWarnings("unchecked")
				Set<Entity>set =  (Set<Entity>) entry.getValue();
				if ( Appointment.TYPE.equals( raplaType )  || Reservation.TYPE.equals( raplaType) || Attribute.TYPE.equals( raplaType) || Category.TYPE.equals( raplaType))
				{
					continue;
				}
				if (user == null )
				{
					result.addAll( set);
				}
				else
				{
					if (   Preferences.TYPE.equals( raplaType )  )
					{
						{
							PreferencesImpl preferences = cache.getPreferencesForUserId( null );
							if ( preferences != null)
							{
								result.add( preferences);
							}
						}
						{
							String userId = user.getId();
							Assert.notNull( userId);
							PreferencesImpl preferences = cache.getPreferencesForUserId( userId );
							if ( preferences != null)
							{
								result.add( preferences);
							}
						}
					}
					else if (   Allocatable.TYPE.equals( raplaType )  )
					{
						for ( Entity obj: set)
						{
							Allocatable alloc = (Allocatable) obj;
							if (user.isAdmin() || alloc.canReadOnlyInformation( user))
							{
								result.add( obj);
							}
						}
					}
					else
					{
						result.addAll( set);
					}
				}
			}
			return result;
		}
		finally
		{
			unlock(readLock);
		}
	}
    
    // this check is only there to detect rapla bugs in the conflict api and can be removed if it causes performance issues
    private void checkAbandonedAppointments() {
		Collection<Allocatable> allocatables = cache.getAllocatables();
		Logger logger = getLogger().getChildLogger("appointmentcheck");
		try
		{
			for ( Allocatable allocatable:allocatables)
			{
				SortedSet<Appointment> appointmentSet = this.appointmentMap.get( allocatable.getId());
				if ( appointmentSet == null)
				{
					continue;
				}
				for (Appointment app:appointmentSet)
				{
					{
						SimpleEntity original = (SimpleEntity)app;
						String id = original.getId();
						if ( id == null )
						{
							logger.error( "Empty id  for " + original);
							continue;
						}
						SimpleEntity persistant = (SimpleEntity) cache.tryResolve( id );
						if ( persistant == null )
						{
							logger.error( "appointment not stored in cache " + original );
							continue;
						}
					}
					Reservation reservation = app.getReservation();
					if (reservation == null)
					{
						logger.error("Appointment without a reservation stored in cache " + app );
						appointmentSet.remove( app);
						continue;
					}
					else if (!reservation.hasAllocated( allocatable, app))
					{
						logger.error("Allocation is not stored correctly for " + reservation + " " + app + " "  + allocatable + " removing binding for " + app);
						appointmentSet.remove( app);
						continue;
					}
					else
					{
						{
							Reservation original = reservation;
							String id = original.getId();
							if ( id == null )
							{
								logger.error( "Empty id  for " + original);
								continue;
							}
							Reservation persistant = (Reservation) cache.tryResolve( id );
							if ( persistant != null )
							{
								Date lastChanged = original.getLastChanged();
								Date persistantLastChanged = persistant.getLastChanged();
								if (persistantLastChanged != null &&  !persistantLastChanged.equals(lastChanged))
								{
									logger.error( "Reservation stored in cache is not the same as in allocation store " + original );
									continue;
								}
							}
							else
							{
								logger.error( "Reservation not stored in cache " + original + " removing binding for " + app);
								appointmentSet.remove( app);
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

    protected void createDefaultSystem(LocalCache cache) throws RaplaException
	{
    	EntityStore store = new EntityStore( null, cache.getSuperCategory() );
        
    	DynamicTypeImpl resourceType = newDynamicType(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE,"resource");
		setName(resourceType.getName(), "resource");
		add(store, resourceType);
		
		DynamicTypeImpl personType = newDynamicType(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON,"person");
		setName(personType.getName(), "person");
		add(store, personType);
		
		DynamicTypeImpl eventType = newDynamicType(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION, "event");
		setName(eventType.getName(), "event");
		add(store, eventType);
		
		String[] userGroups = new String[] {Permission.GROUP_REGISTERER_KEY, Permission.GROUP_MODIFY_PREFERENCES_KEY,Permission.GROUP_CAN_READ_EVENTS_FROM_OTHERS, Permission.GROUP_CAN_CREATE_EVENTS, Permission.GROUP_CAN_EDIT_TEMPLATES};
		Date now = getCurrentTimestamp();
		CategoryImpl groupsCategory = new CategoryImpl(now,now);
		groupsCategory.setKey("user-groups");
		setName( groupsCategory.getName(), groupsCategory.getKey());
		setNew( groupsCategory);
		store.put( groupsCategory);
		for ( String catName: userGroups)
		{
			CategoryImpl group = new CategoryImpl(now,now);
			group.setKey( catName);
			setNew(group);
			setName( group.getName(), group.getKey());
			groupsCategory.addCategory( group);
			store.put( group);
		}
		cache.getSuperCategory().addCategory( groupsCategory);
		UserImpl admin = new UserImpl(now,now);
		admin.setUsername("admin");
		admin.setAdmin( true);
		setNew(admin);
		store.put( admin);
	
		Collection<Entity> list = store.getList();
		cache.putAll( list );
		testResolve( list);
	    setResolver( list);
	    
    	UserImpl user = cache.getUser("admin");
    	String password ="";
		cache.putPassword( user.getId(), password );
		cache.getSuperCategory().setReadOnly();
	
		AllocatableImpl allocatable = new AllocatableImpl(now, now);
		allocatable.setResolver( this);
		allocatable.addPermission(allocatable.newPermission());
        Classification classification = cache.getDynamicType("resource").newClassification();
        allocatable.setClassification(classification);
        setNew(allocatable);
        classification.setValue("name", getString("test_resource"));
        allocatable.setOwner( user);
        
        cache.put( allocatable);
	}
    
    private void add(EntityStore list, DynamicTypeImpl type) {
    	list.put( type);
    	for (Attribute att:type.getAttributes())
    	{
    		list.put((Entity) att);
    	}
	}

	private Attribute createStringAttribute(String key, String name) throws RaplaException {
		Attribute attribute = newAttribute(AttributeType.STRING, null);
		attribute.setKey(key);
		setName(attribute.getName(), name);
		return attribute;
	}
	
	private Attribute createAttributeWithId(String key, AttributeType type,int id) throws RaplaException {
		Attribute attribute = newAttribute(type, Attribute.TYPE.getId(id));
		attribute.setKey(key);
		setName(attribute.getName(), key);
		return attribute;
	}
	
	private DynamicTypeImpl newDynamicType(String classificationType, String key) throws RaplaException {
		DynamicTypeImpl dynamicType = new DynamicTypeImpl();
		dynamicType.setAnnotation("classification-type", classificationType);
		dynamicType.setElementKey(key);
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
		dynamicType.setResolver( this);
		return dynamicType;
	}

	private Attribute newAttribute(AttributeType attributeType,String id)	throws RaplaException {
		AttributeImpl attribute = new AttributeImpl(attributeType);
		if ( id == null)
		{
			setNew(attribute);
		}
		else
		{
			((RefEntity)attribute).setId(id);
		}
		attribute.setResolver( this);
		return attribute;
	}
	
	private <T extends Entity> void setNew(T entity)
			throws RaplaException {

		RaplaType raplaType = entity.getRaplaType();
		String id = createIdentifier(raplaType,1)[0];
		((RefEntity)entity).setId(id);
	}
	
	
	void setName(MultiLanguageName name, String to)
	{
		String currentLang = i18n.getLang();
		name.setName("en", to);
		try
		{
			String translation = i18n.getString( to);
			name.setName(currentLang, translation);
		}
		catch (Exception ex)
		{
			
		}
	}
	
	
	
}
