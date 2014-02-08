/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Christopher Kohlhaas                                  |
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.concurrent.locks.Lock;

import org.rapla.components.util.Cancelable;
import org.rapla.components.util.Command;
import org.rapla.components.util.CommandScheduler;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.TimeInterval;
import org.rapla.components.util.Tools;
import org.rapla.entities.Category;
import org.rapla.entities.DependencyException;
import org.rapla.entities.Entity;
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
import org.rapla.entities.domain.internal.AllocatableImpl;
import org.rapla.entities.domain.internal.AppointmentImpl;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.dynamictype.internal.AttributeImpl;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.entities.internal.CategoryImpl;
import org.rapla.entities.internal.UserImpl;
import org.rapla.entities.storage.CannotExistWithoutTypeException;
import org.rapla.entities.storage.DynamicTypeDependant;
import org.rapla.entities.storage.RefEntity;
import org.rapla.entities.storage.internal.SimpleEntity;
import org.rapla.entities.storage.internal.SimpleIdentifier;
import org.rapla.facade.Conflict;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.Disposable;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.rapla.server.internal.TimeZoneConverterImpl;
import org.rapla.storage.IdTable;
import org.rapla.storage.LocalCache;
import org.rapla.storage.RaplaNewVersionException;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.ReferenceNotFoundException;
import org.rapla.storage.UpdateEvent;
import org.rapla.storage.UpdateOperation;
import org.rapla.storage.UpdateResult;
import org.rapla.storage.impl.AbstractCachableOperator;
import org.rapla.storage.impl.EntityStore;


public abstract class LocalAbstractCachableOperator extends AbstractCachableOperator implements Disposable {
	protected IdTable idTable;
	protected String encryption = "sha-1";
	private ConflictFinder conflictFinder;
	Map<Allocatable,SortedSet<Appointment>> appointmentMap;
	TimeZone configuredTimeZone = TimeZone.getDefault();
	CommandScheduler scheduler;
	Cancelable cleanConflictsTask;
	
	/**
	 * implement this method to implement the persistent mechanism. By default it
	 * calls <li>check()</li> <li>update()</li> <li>fireStorageUpdate()</li> <li>
	 * fireTriggerEvents()</li> You should not call dispatch directly from the
	 * client. Use storeObjects and removeObjects instead.
	 */
	abstract public void dispatch(final UpdateEvent evt) throws RaplaException;
	public LocalAbstractCachableOperator(RaplaContext context, Logger logger) throws RaplaException {
		super( context, logger);
		scheduler = context.lookup( CommandScheduler.class);
	}

	public String getEncryption() {
		return encryption;
	}

	public List<Reservation> getReservations(User user, Collection<Allocatable> allocatables, Date start, Date end) throws RaplaException {
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
			for (Appointment appointment:AppointmentImpl.getAppointments(appointments,user,start,end, excludeExceptions))
			{
	            Reservation reservation = appointment.getReservation();
	            if ( !reservationSet.contains( reservation))
	            {
	            	reservationSet.add( reservation );
	            }
			}
        }
        return new ArrayList<Reservation>(reservationSet);
	}

	
	public Comparable[] createIdentifier(RaplaType raplaType, int count) throws RaplaException {
        Comparable[] ids = new SimpleIdentifier[ count];
        synchronized ( idTable) {
        	for ( int i=0;i<count;i++)
            {
            	ids[i] = idTable.createId(raplaType);
            }
		}
        return ids;
    }
	
	public Date getCurrentTimestamp() {
		long time = System.currentTimeMillis();
		long offset = TimeZoneConverterImpl.getOffset( DateTools.getTimeZone(), configuredTimeZone, time);
		Date raplaTime = new Date(time + offset);
		return raplaTime; 
	}

	public void setTimeZone( TimeZone timeZone)
	{
		configuredTimeZone = timeZone;
	}
	
	public void authenticate(String username, String password)
			throws RaplaException {
		Lock readLock = readLock();
		try {
			getLogger().info("Check password for User " + username);
			RefEntity<User> user = cache.getUser(username);
			if (user != null && checkPassword(user.getId(), password)) {
				return;
	
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

	public void changePassword(RefEntity<User> user, char[] oldPassword,char[] newPassword) throws RaplaException {
		getLogger().info("Change password for User " + user.cast().getUsername());
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
		RefEntity<User> editObject = editObject(user, null);
		List<RefEntity<?>> editList = new ArrayList<RefEntity<?>>(1);
		editList.add(editObject);
		Collection<RefEntity<?>> removeList = Collections.emptyList();
		// synchronization will be done in the dispatch method
		storeAndRemove(editList, removeList, user);
	}

	public void changeName(RefEntity<User> user, String title,String firstname, String surname) throws RaplaException {
		RefEntity<User> editableUser = editObject(user, (User) user);
		@SuppressWarnings("unchecked")
		RefEntity<Allocatable> personReference = (RefEntity<Allocatable>) editableUser.cast().getPerson();
		if (personReference == null) {
			editableUser.cast().setName(surname);
			storeUser(editableUser);
		} else {
			RefEntity<Allocatable> editablePerson = editObject(personReference,	null);
			Classification classification = editablePerson.cast().getClassification();
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
			ArrayList<RefEntity<?>> arrayList = new ArrayList<RefEntity<?>>();
			arrayList.add(editableUser);
			arrayList.add(editablePerson);
			Collection<RefEntity<?>> storeObjects = arrayList;
			Collection<RefEntity<?>> removeObjects = Collections.emptySet();
			// synchronization will be done in the dispatch method
			storeAndRemove(storeObjects, removeObjects, null);
		}
	}

	public void changeEmail(RefEntity<User> user, String newEmail)
			throws RaplaException {
		RefEntity<User> editableUser = user.isPersistant() ? editObject(user, (User) user) : user;
		@SuppressWarnings("unchecked")
		RefEntity<Allocatable> personReference = (RefEntity<Allocatable>) editableUser.cast().getPerson();
		ArrayList<RefEntity<?>> arrayList = new ArrayList<RefEntity<?>>();
		Collection<RefEntity<?>> storeObjects = arrayList;
		Collection<RefEntity<?>> removeObjects = Collections.emptySet();
		storeObjects.add(editableUser);
		if (personReference == null) {
			editableUser.cast().setEmail(newEmail);
		} else {
			RefEntity<Allocatable> editablePerson = editObject(personReference,	null);
			Classification classification = editablePerson.cast().getClassification();
			classification.setValue("email", newEmail);
			storeObjects.add(editablePerson);
		}
		storeAndRemove(storeObjects, removeObjects, null);
	}

	public void confirmEmail(RefEntity<User> user, String newEmail)	throws RaplaException {
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
	protected void check(final UpdateEvent evt) throws RaplaException {
		Set<RefEntity<?>> storeObjects = new HashSet<RefEntity<?>>(evt.getStoreObjects());
		Set<RefEntity<?>> removeObjects = new HashSet<RefEntity<?>>(evt.getRemoveObjects());
		checkConsistency(storeObjects);
		checkUnique(storeObjects);
		checkReferences(storeObjects);
		checkNoDependencies(removeObjects, storeObjects);
		checkVersions(storeObjects);
	}
	
	protected void initAppointments() {
		appointmentMap = new HashMap<Allocatable, SortedSet<Appointment>>();
		Collection<Appointment> unsortedAppointments = cache.getCollection(Appointment.class);
    	for ( Appointment app:unsortedAppointments)
		{
			Reservation reservation = app.getReservation();
			if ( RaplaComponent.isTemplate( reservation))
			{
				continue;
			}
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
		Date today2 = today();
		AllocationMap allocationMap = new AllocationMap() {
			    public SortedSet<Appointment> getAppointments(Allocatable allocatable)
			    {
			    	return LocalAbstractCachableOperator.this.getAppointments(allocatable);
			    }
			    public Collection<Allocatable> getAllocatables()
			    {
			    	return cache.getCollection( Allocatable.class);
			    }
		};
		conflictFinder = new ConflictFinder(allocationMap, today2, getLogger());
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
	private void updateBindings(UpdateResult result, Logger logger) throws RaplaException {
		Map<Allocatable,AllocationChange> toUpdate = new HashMap<Allocatable,AllocationChange>();
		List<Allocatable> removedAllocatables = new ArrayList<Allocatable>();
		for (UpdateOperation operation: result.getOperations())
		{
			RaplaObject current = operation.getCurrent();
			if ( current.getRaplaType() ==  Appointment.TYPE )
			{
				Appointment oldApp = (Appointment) current;
				if ( operation instanceof UpdateResult.Add)
				{
					Appointment newApp =(Appointment) ((UpdateResult.Add) operation).getNew();
					updateBindings( toUpdate, newApp, false);
				}
				else if ( operation instanceof UpdateResult.Remove)
				{
					updateBindings( toUpdate, oldApp, true);	
				}
				else if ( operation instanceof UpdateResult.Change)
				{
					Appointment newApp =(Appointment) ((UpdateResult.Change) operation).getNew();
					oldApp =(Appointment) ((UpdateResult.Change) operation).getOld();
					// remove first
					updateBindings( toUpdate, oldApp, true);
					// then add again
					updateBindings( toUpdate, newApp, false);
				}
			}
			if ( current.getRaplaType() ==  Allocatable.TYPE )
			{
				if ( operation instanceof UpdateResult.Remove)
				{
					removedAllocatables.add( (Allocatable) current);
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
		checkAbandonedAppointments(cache.getCollection( Allocatable.class));
	}
	
	protected void updateBindings(Map<Allocatable, AllocationChange> toUpdate,Appointment app, boolean remove) throws RaplaException {
		
		Set<Allocatable> allocatablesToProcess = new HashSet<Allocatable>();
		allocatablesToProcess.add( null);
		Reservation reservation = app.getReservation();
		if ( reservation != null)
		{
			Allocatable[] allocatablesFor = reservation.getAllocatablesFor( app);
			allocatablesToProcess.addAll( Arrays.asList(allocatablesFor));
			// This double check is very imperformant and will be removed in the future, if it doesnt show in test runs
			if ( remove)
			{
				Collection<Allocatable> allocatables = cache.getCollection(Allocatable.class);
				for ( Allocatable allocatable:allocatables)
				{
					SortedSet<Appointment> appointmentSet = this.appointmentMap.get( allocatable);
					if ( appointmentSet == null)
					{
						continue;
					}
					for (Appointment app1:appointmentSet)
					{
						if ( app1.equals( app))
						{
							if ( !allocatablesToProcess.contains( allocatable))
							{
								getLogger().warn("Old reservation " + reservation.toString() + " has not the correct allocatable information. Using full search for appointment " + app + " and resource " + allocatable ) ;
								allocatablesToProcess.add(allocatable);
							}
						}
					}
				}
			}
		}
		else
		{
			getLogger().error("Appointment without reservation found " + app + " ignoring.");
		}
		
		for ( Allocatable alloc: allocatablesToProcess)
		{
			Allocatable allocatable; 
			AllocationChange updateSet;
			if ( alloc != null)
			{
				allocatable =  getPersistantForUpdate(alloc);
				updateSet = toUpdate.get( allocatable);
				if ( updateSet == null)
				{
					updateSet = new AllocationChange();
					toUpdate.put(allocatable, updateSet);
				}
			}
			else
			{
				allocatable = alloc;
				updateSet = null;
			}
			if ( remove)
			{
				Collection<Appointment> appointmentSet = getAndCreateList(appointmentMap,allocatable);
				// binary search could fail if the appointment has changed since the last add 
				if (!appointmentSet.remove( app)) 
				{
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
				Appointment persistant = getPersistantForUpdate(app);
				SortedSet<Appointment> appointmentSet = getAndCreateList(appointmentMap, allocatable);
				appointmentSet.add(persistant);
				if ( updateSet != null)
				{
					updateSet.toChange.add( persistant);
				}
			}
		}
	}

	static final SortedSet<Appointment> EMPTY_SORTED_SET = Collections.unmodifiableSortedSet( new TreeSet<Appointment>());
	protected SortedSet<Appointment> getAppointments(Allocatable allocatable)
    {
		SortedSet<Appointment> s = appointmentMap.get( allocatable);
    	if ( s == null)
    	{
    		return EMPTY_SORTED_SET; 
    	}
		return Collections.unmodifiableSortedSet(s);
    }

	private SortedSet<Appointment> getAndCreateList(Map<Allocatable,SortedSet<Appointment>> appointmentMap,Allocatable alloc) {
		SortedSet<Appointment> set = appointmentMap.get( alloc);
		if ( set == null)
		{
			set = new TreeSet<Appointment>(new AppointmentStartComparator());
			appointmentMap.put(alloc, set);
		}
		return set;
	}
	
	protected <T extends Entity<T>> T getPersistantForUpdate(
			T object) throws RaplaException {
		RefEntity<T> app = (RefEntity<T>)object;
		T appointment2 = (T) cache.tryResolve( app.getId() );
		//T appointment2 = getPersistant(Collections.singleton(app)).get( object);
		if ( appointment2 == null )
		{
			throw new RaplaException("Only persistant entities can be added to binding map " + appointment2 );
		}
		return appointment2;
	}
	
    @Override
	protected UpdateResult update(UpdateEvent evt)
			throws RaplaException {
		UpdateResult update = super.update(evt);
	   	Logger logger = getLogger();
	   	updateBindings(update, logger);
		return update;
	}
    
    public void removeOldConflicts() throws RaplaException
    {
    	Map<RefEntity<?>, RefEntity<?>> oldEntities = new LinkedHashMap<RefEntity<?>, RefEntity<?>>();
		Collection<RefEntity<?>> updatedEntities = new LinkedHashSet<RefEntity<?>>();
		Collection<RefEntity<?>> toRemove  = new LinkedHashSet<RefEntity<?>>();
		TimeInterval invalidateInterval = null;
		Object userId = null;
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
	
	/**
	 * Create a closure for all objects that should be updated. The closure
	 * contains all objects that are sub-entities of the entities and all
	 * objects and all other objects that are affected by the update: e.g.
	 * Classifiables when the DynamicType changes. The method will recursivly
	 * proceed with all discovered objects.
	 */
	protected UpdateEvent createClosure(final UpdateEvent evt)
			throws RaplaException {
		UpdateEvent closure = evt.clone();
		Iterator<RefEntity<?>> it = evt.getStoreObjects().iterator();
		while (it.hasNext()) {
			RefEntity<?> object = it.next();
			addStoreOperationsToClosure(closure, object);
		}

		it = evt.getRemoveObjects().iterator();
		while (it.hasNext()) {
			RefEntity<?> object = it.next();
			addRemoveOperationsToClosure(closure, object);
		}
		return closure;
	}

	
	protected void addStoreOperationsToClosure(UpdateEvent evt, RefEntity<?> entity) throws RaplaException {
		if (getLogger().isDebugEnabled() && !evt.getStoreObjects().contains(entity)) {
			getLogger().debug("Adding " + entity + " to store closure");
		}
		evt.putStore(entity);
		if (DynamicType.TYPE == entity.getRaplaType()) {
			DynamicTypeImpl dynamicType = (DynamicTypeImpl) entity;
			addChangedDynamicTypeDependant(evt, dynamicType, false);
		}
		
		Iterator<RefEntity<?>> it = entity.getSubEntities();
		while (it.hasNext()) {
			RefEntity<?> subEntity = it.next();
			addStoreOperationsToClosure(evt, subEntity);
		}

		for (RefEntity<?> ref:getRemovedEntities(entity))
		{
			addRemoveOperationsToClosure(evt, ref);
		}
	}

	private void addRemoveOperationsToClosure(UpdateEvent evt,
			RefEntity<?> entity) throws RaplaException {
		if (getLogger().isDebugEnabled() && !evt.getRemoveObjects().contains(entity)) {
			getLogger().debug("Adding " + entity + " to remove closure");
		}
		evt.putRemove(entity);

		if (DynamicType.TYPE == entity.getRaplaType()) {
			DynamicTypeImpl dynamicType = (DynamicTypeImpl) entity;
			addChangedDynamicTypeDependant(evt, dynamicType, true);
		}

		// add the subentities
		Iterator<RefEntity<?>> it = entity.getSubEntities();
		while (it.hasNext()) {
			addRemoveOperationsToClosure(evt, it.next());
		}

		// And also add the SubEntities that have been removed, before storing
		for ( RefEntity<?> ref: getRemovedEntities(entity)) {
			addRemoveOperationsToClosure(evt, ref);
		}

		// If entity is a user, remove the preference object
		if (User.TYPE == entity.getRaplaType()) {
			addRemovedUserDependant(evt, (User) entity);
		}

	}

	private Collection<RefEntity<?>> getRemovedEntities(RefEntity<?> entity) {
		RefEntity<?> original = findInLocalCache(entity);
		List<RefEntity<?>> result = null;
		if (original != null) {
			Iterator<RefEntity<?>> it = original.getSubEntities();
			while (it.hasNext()) {
				RefEntity<?> subEntity = it.next();
				if (!entity.isParentEntity(subEntity)) {
					// SubEntity not found in the new entity add it to remove
					// List
					if (result == null) {
						result = new ArrayList<RefEntity<?>>();
					}
					result.add(subEntity);
					// System.out.println( "Removed " + subEntity);
				}
			}
		}
		if (result != null) {
			return result;
		} else {
			return Collections.emptySet();
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
	

	protected void addChangedDynamicTypeDependant(UpdateEvent evt, DynamicTypeImpl type, boolean toRemove) throws RaplaException {
		List<RefEntity<?>> referencingEntities = getReferencingEntities( type);
		Iterator<RefEntity<?>> it = referencingEntities.iterator();
		while (it.hasNext()) {
			RefEntity<?> entity = it.next();
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
			if (evt.getStoreObjects().contains(entity)) {
				dependant = (DynamicTypeDependant) evt.findEntity(entity);
			} else {
				// no, then create a clone of the classfiable object and add to list
				User user = null;
				if (evt.getUserId() != null) {
					user = (User) resolveIdWithoutSync(evt.getUserId());
				}
				RefEntity<?> persistant =  cache.tryResolve(entity.getId());
				dependant = (DynamicTypeDependant) editObject(entity, persistant, user);
				addStoreOperationsToClosure(evt, ((RefEntity<?>) dependant));
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
	}
	
	protected void addRemovedUserDependant(UpdateEvent evt, User user) throws RaplaException {
		PreferencesImpl preferences = cache.getPreferences(user);
		if (preferences != null)
			addRemoveOperationsToClosure(evt, preferences);

		List<RefEntity<?>> referencingEntities = getReferencingEntities((RefEntity<?>) user);
		Iterator<RefEntity<?>> it = referencingEntities.iterator();
		while (it.hasNext()) {
			RefEntity<?> entity = it.next();
			if (!(entity instanceof Timestamp)) {
				continue;
			}
			Timestamp timestamp = (Timestamp) entity;
			User lastChangedBy = timestamp.getLastChangedBy();
			if ( lastChangedBy == null || !lastChangedBy.equals( user) )
			{
				continue;
			}
			if ( entity instanceof Ownable  )
			{
				 User owner = ((Ownable) entity).getOwner();
				 // we do nothing if the user is also owner,  that dependencies need to be resolved manually
				 if ( owner != null && owner.equals(user))
				 {
					 continue;
				 }
			}
			
			RefEntity<?> persistant= cache.tryResolve( entity.getId());
			RefEntity<?> dependant = editObject( entity, persistant, user);
			((SimpleEntity)dependant).setLastChangedBy( null );
			addStoreOperationsToClosure(evt,  dependant);
		
		}
		
	}

	/**
	 * returns all entities that depend one the passed entities. In most cases
	 * one object depends on an other object if it has a reference to it.
	 * 
	 * @param entity
	 */
	final protected Set<RefEntity<?>> getDependencies(RefEntity<?> entity)  {
		HashSet<RefEntity<?>> dependencyList = new HashSet<RefEntity<?>>();
		RaplaType type = entity.getRaplaType();
		final Collection<RefEntity<?>> referencingEntities;
		if (Category.TYPE == type || DynamicType.TYPE == type || Allocatable.TYPE == type || User.TYPE == type) {
			referencingEntities = getReferencingEntities(entity);
		} else {
			referencingEntities = cache.getReferers(Preferences.TYPE, entity);
		}
		dependencyList.addAll(referencingEntities);
		return dependencyList;
	}

	protected List<RefEntity<?>> getReferencingEntities(RefEntity<?> entity) {
		ArrayList<RefEntity<?>> list = new ArrayList<RefEntity<?>>();
		list.addAll(cache.getReferers(Reservation.TYPE, entity));
		list.addAll(cache.getReferers(Allocatable.TYPE, entity));
		list.addAll(cache.getReferers(Preferences.TYPE, entity));
		list.addAll(cache.getReferers(User.TYPE, entity));
		list.addAll(cache.getReferers(DynamicType.TYPE, entity));
		return list;
	}

	private int countDynamicTypes(Collection<? extends RaplaObject> entities, String classificationType) {
		Iterator<? extends RaplaObject> it = entities.iterator();
		int count = 0;
		while (it.hasNext()) {
			RaplaObject entity = it.next();
			if (DynamicType.TYPE != entity.getRaplaType())
				continue;
			DynamicType type = (DynamicType) entity;
			if (type.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE).equals(	classificationType)) {
				count++;
			}
		}
		return count;
	}

	// Count dynamic-types to ensure that there is least one dynamic type left
	private void checkDynamicType(Collection<RefEntity<?>> entities, String classificationType) throws RaplaException {
		Collection<RefEntity<?>> allTypes = cache.getCollection(DynamicType.TYPE);
		int count = countDynamicTypes(entities, classificationType);
		if (count >= 0	&& count >= countDynamicTypes(allTypes, classificationType)) {
			throw new RaplaException(i18n.getString("error.one_type_requiered"));
		}
	}

	/**
	 * Check if the references of each entity refers to an object in cache or in
	 * the passed collection.
	 */
	final protected void checkReferences(Collection<RefEntity<?>> entities)	throws RaplaException {
		Iterator<RefEntity<?>> it = entities.iterator();
		while (it.hasNext()) {
			RefEntity<?> entity = it.next();
			Iterator<RefEntity<?>> it2 = entity.getReferences();
			while (it2.hasNext()) {
				
				RefEntity<?> reference = it2.next();
				
				// Reference in cache ?
				if (findInLocalCache(reference) != null)
					continue;
				// References in collection.
				if (entities.contains(reference))
					continue;
				
			
				throw new ReferenceNotFoundException(i18n.format("error.reference_not_stored", getName(reference)));
			}
		}
	}

	/**
	 * check if we find an object with the same name. If a different object
	 * (different id) with the same unique attributes is found a
	 * UniqueKeyException will be thrown.
	 */
	final protected void checkUnique(Collection<RefEntity<?>> entities)	throws RaplaException {
		for (RefEntity<?> entity : entities) {
			String name = "";
			RefEntity<?> entity2 = null;
			if (DynamicType.TYPE == entity.getRaplaType()) {
				DynamicType type = (DynamicType) entity;
				name = type.getElementKey();

				entity2 = cache.getDynamicType(name);
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
	protected void checkVersions(Collection<RefEntity<?>> entities)	throws RaplaException {
		Iterator<RefEntity<?>> it = entities.iterator();
		while (it.hasNext()) {
			// Check Versions
			RefEntity<?> entity = it.next();
			RefEntity<?> persistantVersion = findInLocalCache(entity);
			// If the entities are newer, everything is o.k.
			if (persistantVersion != null && persistantVersion != entity
					&& entity.getVersion() < persistantVersion.getVersion()) {
				getLogger().warn(
						"There is a newer  version for: " + entity.getId()
								+ " stored version :"
								+ persistantVersion.getVersion()
								+ " version to store :" + entity.getVersion());
				throw new RaplaNewVersionException(getI18n().format(
						"error.new_version", entity.toString()));
			}
		}
	}

	
	protected void checkNoDependencies(Set<RefEntity<?>> removeEntities, Set<RefEntity<?>> storeObjects) throws RaplaException {
		HashSet<RefEntity<?>> dep = new HashSet<RefEntity<?>>();

		for (RefEntity<?> entity : removeEntities) {
			// Add dependencies for the entity

			// First we add the dependencies from the stored object list
			for (RefEntity<?> obj : storeObjects) {
				if (obj.isRefering(entity)) {
					dep.add(obj);
				}
			}

			// Than we add the dependencies from the cache. It is important that
			// we don't add the dependencies from the stored object list here,
			// because a dependency could be removed in a stored object
			Set<RefEntity<?>> dependencies = getDependencies(entity);
			for (RefEntity<?> dependency : dependencies) {
				if (!storeObjects.contains(dependency)) {
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
//		for (RefEntity<?> entity : storeObjects) {
//			if ( entity.getRaplaType() == Allocatable.TYPE)
//			{
//				Allocatable alloc = (Allocatable) entity;
//				for (RefEntity<?> reference:getDependencies(entity))
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
			for (RefEntity<?> obj: dep)
			{				
				String string = getDependentName(obj);
				names.add(string);
			}
			throw new DependencyException(getString("error.dependencies"),names.toArray( new String[]{}));
		}
		// Count dynamic-types to ensure that there is least one dynamic type
		// for resources, for persons and for reservations
		checkDynamicType(removeEntities, DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);
		checkDynamicType(removeEntities, DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE);
		checkDynamicType(removeEntities, DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON);
	}

	protected String getDependentName(RefEntity<?> obj) {
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
			
			String template = reservation.getAnnotation(Reservation.TEMPLATE);
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
	protected boolean isAddedToUpdateResult(RefEntity<?> entity) {
		return true;
	}

	/**
	 * @param entity  
	 */
	protected boolean isStorableInCache(RefEntity<?> entity) {
		return true;
	}
	

	private void storeUser(RefEntity<User> refUser) throws RaplaException {
		ArrayList<RefEntity<?>> arrayList = new ArrayList<RefEntity<?>>();
		arrayList.add(refUser);
		Collection<RefEntity<?>> storeObjects = arrayList;
		Collection<RefEntity<?>> removeObjects = Collections.emptySet();
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
			if ( allocatable.isHoldBackConflicts())
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
    
	private void checkAbandonedAppointments(Collection<Allocatable> allocatables) {
		
		Logger logger = getLogger().getChildLogger("appointmentcheck");
		try
		{
			for ( Allocatable allocatable:allocatables)
			{
				SortedSet<Appointment> appointmentSet = this.appointmentMap.get( allocatable);
				if ( appointmentSet == null)
				{
					continue;
				}
				for (Appointment app:appointmentSet)
				{
					Reservation reservation = app.getReservation();
					if (reservation == null)
					{
						logger.error("Appointment without a reservation stored in cache " + app );
						return;
					}
					else if (!reservation.hasAllocated( allocatable, app))
					{
						logger.error("Allocation is not stored correctly for " + reservation + " " + app + " "  + allocatable + " removing from cache!");
						return;
					}
					else
					{
						{
							RefEntity<?> original = (RefEntity<?>)reservation;
							Comparable id = original.getId();
							if ( id == null )
							{
								logger.error( "Empty id  for " + original);
								return;
							}
							RefEntity<?> persistant = cache.tryResolve( id );
							if ( persistant != null )
							{
								if (persistant.getVersion() != original.getVersion())
								{
									logger.error( "Reservation stored in cache is not the same as in allocation store " + original );
									return;
								}
							}
							else
							{
								logger.error( "Reservation not stored in cache " + original );
								return;
							}
						}
						{
							RefEntity<?> original = (RefEntity<?>)app;
							Comparable id = original.getId();
							if ( id == null )
							{
								logger.error( "Empty id  for " + original);
								return;
							}
							RefEntity<?> persistant = cache.tryResolve( id );
							if ( persistant != null )
							{
								if (persistant.getVersion() != original.getVersion())
								{
									logger.error( "appointment stored in cache is not the same as in allocation store " + original );
									return;
								}
							}
							else
							{
								logger.error( "appointment not stored in cache " + original );
								return;
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
    	EntityStore list = new EntityStore( null, cache.getSuperCategory() );
        
    	DynamicTypeImpl resourceType = newDynamicType(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE,"resource");
		setName(resourceType.getName(), "resource");
		add(list, resourceType);
		
		DynamicTypeImpl personType = newDynamicType(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON,"person");
		setName(personType.getName(), "person");
		add(list, personType);
		
		DynamicTypeImpl eventType = newDynamicType(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION, "event");
		setName(eventType.getName(), "event");
		add(list, eventType);
		
		String[] userGroups = new String[] {Permission.GROUP_REGISTERER_KEY, Permission.GROUP_MODIFY_PREFERENCES_KEY,Permission.GROUP_CAN_READ_EVENTS_FROM_OTHERS, Permission.GROUP_CAN_CREATE_EVENTS, Permission.GROUP_CAN_EDIT_TEMPLATES};
		CategoryImpl groupsCategory = new CategoryImpl();
		groupsCategory.setKey("user-groups");
		setName( groupsCategory.getName(), groupsCategory.getKey());
		setNew( groupsCategory);
		list.put( groupsCategory);
		for ( String catName: userGroups)
		{
			CategoryImpl group = new CategoryImpl();
			group.setKey( catName);
			setNew(group);
			setName( group.getName(), group.getKey());
			groupsCategory.addCategory( group);
			list.put( group);
		}
		cache.getSuperCategory().addCategory( groupsCategory);
		UserImpl admin = new UserImpl();
		admin.setUsername("admin");
		admin.setAdmin( true);
		setNew(admin);
		list.put( admin);
	
	    resolveEntities( list.getList().iterator(), list );
	    cache.putAll( list.getList() );
	    
    	UserImpl user = cache.getUser("admin");
    	String password ="";
		cache.putPassword( user.getId(), password );
		cache.getSuperCategory().setReadOnly(true);
	
        Date now = getCurrentTimestamp();
		AllocatableImpl allocatable = new AllocatableImpl(now, now);
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
    		list.put((RefEntity<?>) att);
    	}
	}

	private Attribute createStringAttribute(String key, String name) throws RaplaException {
		Attribute attribute = newAttribute(AttributeType.STRING);
		attribute.setKey(key);
		setName(attribute.getName(), name);
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
		return dynamicType;
	}

	private Attribute newAttribute(AttributeType attributeType)	throws RaplaException {
		AttributeImpl attribute = new AttributeImpl(attributeType);
		setNew(attribute);
		return attribute;
	}
	
	private <T extends RefEntity<?>> void setNew(T entity)
			throws RaplaException {

		RaplaType raplaType = entity.getRaplaType();
		entity.setId(createIdentifier(raplaType,1)[0]);
		entity.setVersion(0);
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
