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
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.rapla.components.util.DateTools;
import org.rapla.components.util.Tools;
import org.rapla.entities.Category;
import org.rapla.entities.DependencyException;
import org.rapla.entities.Entity;
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
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.internal.AppointmentImpl;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.entities.storage.CannotExistWithoutTypeException;
import org.rapla.entities.storage.DynamicTypeDependant;
import org.rapla.entities.storage.RefEntity;
import org.rapla.entities.storage.internal.SimpleEntity;
import org.rapla.entities.storage.internal.SimpleIdentifier;
import org.rapla.facade.AllocationChangeEvent;
import org.rapla.facade.AllocationChangeEvent.Type;
import org.rapla.facade.Conflict;
import org.rapla.facade.RaplaComponent;
import org.rapla.facade.internal.AllocationChangeFinder;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.rapla.storage.IdTable;
import org.rapla.storage.LocalCache;
import org.rapla.storage.RaplaNewVersionException;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.ReferenceNotFoundException;
import org.rapla.storage.UpdateEvent;
import org.rapla.storage.UpdateResult;
import org.rapla.storage.impl.AbstractCachableOperator;
import org.rapla.storage.impl.server.ConflictFinder.AllocationChange;


public abstract class LocalAbstractCachableOperator extends AbstractCachableOperator implements AllocationMap {
	protected IdTable idTable;
	protected String encryption = "sha-1";
	private ConflictFinder conflictFinder;
	
	public LocalAbstractCachableOperator(RaplaContext context, Logger logger) throws RaplaException {
		super( context, logger);
	}

	public String getEncryption() {
		return encryption;
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
	

	Map<Allocatable,SortedSet<Appointment>> appointmentMap;

	protected void initAppointments() {
		appointmentMap = new HashMap<Allocatable, SortedSet<Appointment>>();
    	SortedSet<Appointment> allAppointments = cache.getAppointmentsSortedByStart();
		for ( Appointment app:allAppointments)
		{
			Reservation reservation = app.getReservation();
			if ( RaplaComponent.isTemplate( reservation))
			{
				continue;
			}
			Allocatable[] allocatables = reservation.getAllocatablesFor(app);
			for ( Allocatable alloc:allocatables)
			{
				Collection<Appointment> list = getAndCreateList(appointmentMap,alloc);
				list.add( app);
			}
		}
		Date today2 = today();
		conflictFinder = new ConflictFinder(this, today2);
	}
	
	 public List<Reservation> getReservations(User user, Collection<Allocatable> allocatables, Date start, Date end) {
		lock.readLock().lock();
		try
		{
			boolean excludeExceptions = false;
			HashSet<Reservation> reservationSet = new HashSet<Reservation>();
			if (allocatables != null && allocatables.size() > 0) 
			{
		        for ( Allocatable allocatable: allocatables)
	            {
	            	SortedSet<Appointment> sortedSet = getAppointments( allocatable);
	    			for (Appointment appointment:AppointmentImpl.getAppointments(sortedSet,user,start,end, excludeExceptions))
	    			{
	    	            Reservation reservation = appointment.getReservation();
	    	            if ( !reservationSet.contains( reservation))
	    	            {
	    	            	reservationSet.add( reservation );
	    	            }
	    			}
	            }
	        }
			else
			{
				for (Appointment appointment:AppointmentImpl.getAppointments(cache.getAppointmentsSortedByStart(),user,start,end, excludeExceptions))
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
		finally
		{
			lock.readLock().unlock();
		}
	
    }
    static final SortedSet<Appointment> EMPTY_SORTED_SET = Collections.unmodifiableSortedSet( new TreeSet<Appointment>());
    public SortedSet<Appointment> getAppointments(Allocatable allocatable)
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
		T appointment2 = getPersistant(Collections.singleton(app)).get( object);
		if ( appointment2 == null )
		{
			throw new RaplaException("Only persistant entities can be added to binding map " + appointment2 );
		}
		return appointment2;
	}
	
	
	/** updates the bindings of the resources and returns a map with all processed allocation changes*/
	private Map<Allocatable, AllocationChange> updateBindings(UpdateResult result, Logger logger) throws RaplaException {
		
		Collection<AllocationChangeEvent> events = AllocationChangeFinder.getTriggerEvents(result, logger);

    	Map<Allocatable,AllocationChange> toUpdate = new HashMap<Allocatable,AllocationChange>();
    	for ( AllocationChangeEvent evt: events)
		{
			Allocatable allocatable = getPersistantForUpdate(evt.getAllocatable());
			AllocationChange updateSet = toUpdate.get( allocatable);
			if ( updateSet == null)
			{
				updateSet = new AllocationChange();
				toUpdate.put(allocatable, updateSet);
			}
			Collection<Appointment> appointmentSet = getAndCreateList(appointmentMap,allocatable);
			Type type = evt.getType();
			if (type == AllocationChangeEvent.REMOVE)
			{
				Appointment appointment = evt.getOldAppointment();
				appointmentSet.remove(appointment);
				updateSet.toRemove.add( appointment);
			}
			else if (type == AllocationChangeEvent.ADD)
			{
				Appointment appointment = evt.getNewAppointment();
				Appointment persistant = getPersistantForUpdate(appointment);
				appointmentSet.add(persistant);
				updateSet.toChange.add( persistant);
			}
			else if (type == AllocationChangeEvent.CHANGE)
			{
				Appointment old = evt.getOldAppointment();
				appointmentSet.remove(old);
				updateSet.toRemove.add( old);
				Appointment appointment = evt.getNewAppointment();
				Appointment persistant = getPersistantForUpdate(appointment);
				appointmentSet.add(persistant);
				updateSet.toChange.add( persistant);
			}
			else
			{
				throw new IllegalStateException("AllocationChangeEventType " + type + " not supported");
			}
		}
		return toUpdate;
	}

    
    public Collection<Allocatable> getAllocatables()
    {
    	return cache.getCollection( Allocatable.class);
    }
	
	@Override
	synchronized protected UpdateResult update(UpdateEvent evt)
			throws RaplaException {
		UpdateResult update = super.update(evt);
	   	Logger logger = getLogger();
	   	Map<Allocatable, AllocationChange> toUpdate = updateBindings(update, logger);
	   	conflictFinder.updateConflicts(toUpdate,update, today());
		return update;
	}
	
	synchronized public Comparable[] createIdentifier(RaplaType raplaType, int count) throws RaplaException {
        Comparable[] ids = new SimpleIdentifier[ count];
        for ( int i=0;i<count;i++)
        {
        	ids[i] = idTable.createId(raplaType);
        }
        return ids;
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

	public Date getCurrentTimestamp() {
		Date time = new Date(System.currentTimeMillis());
		return raplaLocale.toRaplaTime(raplaLocale.getImportExportTimeZone(), time);
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

		for (RefEntity<?> subEntity:entity.getSubEntities())
		{
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
		for ( RefEntity<?> ref: entity.getSubEntities()) {
			addRemoveOperationsToClosure(evt, ref);
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
			for (RefEntity<?> subEntity: original.getSubEntities())
			{
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

	/**
	 * implement this method to implement the persistent mechanism. By default it
	 * calls <li>check()</li> <li>update()</li> <li>fireStorageUpdate()</li> <li>
	 * fireTriggerEvents()</li> You should not call dispatch directly from the
	 * client. Use storeObjects and removeObjects instead.
	 */
	abstract public void dispatch(final UpdateEvent evt) throws RaplaException;

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
					user = (User) resolveId(evt.getUserId());
				}
				dependant = (DynamicTypeDependant) editObject(entity, user);
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
			
			RefEntity<?> dependant = editObject(entity, user);
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
	final protected Set<RefEntity<?>> getDependencies(RefEntity<?> entity) {
		HashSet<RefEntity<?>> dependencyList = new HashSet<RefEntity<?>>();
		RaplaType type = entity.getRaplaType();
		final Collection<RefEntity<?>> referencingEntities;
		if (Category.TYPE == type || DynamicType.TYPE == type || Allocatable.TYPE == type || User.TYPE == type) {
			referencingEntities = getReferencingEntities(entity);
		} else {
			referencingEntities = cache.getReferers(Preferences.class, entity);
		}
		dependencyList.addAll(referencingEntities);
		return dependencyList;
	}

	protected List<RefEntity<Reservation>> getReferencingReservations(RefEntity<?> entity)  {
		ArrayList<RefEntity<Reservation>> result = new ArrayList<RefEntity<Reservation>>();
		Iterator<Reservation> it = this.getReservations(null,null, null, null).iterator();
		while (it.hasNext()) {
			@SuppressWarnings("unchecked")
			RefEntity<Reservation> referer = (RefEntity<Reservation>) it.next();
			if (referer != null && referer.isRefering(entity)) {
				result.add(referer);
			}
		}
		return result;
	}

	protected List<RefEntity<?>> getReferencingEntities(RefEntity<?> entity) {
		ArrayList<RefEntity<?>> list = new ArrayList<RefEntity<?>>();
		// Important to use getReferncingReservations here, because the method
		// getReservations could be overidden in the subclass,
		// to avoid loading unneccessary Reservations in client/server mode.

		list.addAll(getReferencingReservations(entity));
		Collection<RefEntity<?>> referers = cache.getReferers(Allocatable.class, entity);
		list.addAll(referers);
		list.addAll(cache.getReferers(Preferences.class, entity));
		list.addAll(cache.getReferers(User.class, entity));
		list.addAll(cache.getReferers(DynamicType.class, entity));
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
		int count = countDynamicTypes(entities, classificationType);
		Collection<DynamicType> allTypes = cache.getCollection(DynamicType.class);
		if (count >= 0	&& count >= countDynamicTypes(allTypes, classificationType)) {
			throw new RaplaException(i18n.getString("error.one_type_requiered"));
		}
	}

	/**
	 * Check if the references of each entity refers to an object in cache or in
	 * the passed collection.
	 */
	final protected void checkReferences(Collection<RefEntity<?>> entities)	throws RaplaException {
		for (RefEntity<?> entity: entities)
		{
			for(RefEntity<?> reference:entity.getReferences())
			{
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

				entity2 = (RefEntity<?>) cache.getDynamicType(name);
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

	
	public void authenticate(String username, String password)
			throws RaplaException {
		lock.readLock().lock();
		try
		{
			getLogger().info("Check password for User " + username);
			RefEntity<User> user = cache.getUser(username);
			if (user != null && checkPassword(user.getId(), password)) {
				return;
	
			}
			getLogger().error("Login failed for " + username);
			throw new RaplaSecurityException(i18n.getString("error.login"));
		}
		finally
		{
			lock.readLock().unlock();
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
		try
		{
			lock.writeLock().lock();
			cache.putPassword(userId, password);
		}
		finally
		{
			lock.writeLock().unlock();
		}
		RefEntity<User> editObject = editObject(user, null);
		List<RefEntity<?>> editList = new ArrayList<RefEntity<?>>(1);
		editList.add(editObject);
		Collection<RefEntity<?>> removeList = Collections.emptyList();
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
		editableUser.cast().setEmail(newEmail);
		if (personReference != null) {
			RefEntity<Allocatable> editablePerson = editObject(personReference,	null);
			Classification classification = editablePerson.cast().getClassification();
			classification.setValue("email", newEmail);
			storeObjects.add(editablePerson);
		}
		storeAndRemove(storeObjects, removeObjects, null);
	}

	public void confirmEmail(RefEntity<User> user, String newEmail)	throws RaplaException {
		throw new RaplaException(
				"Email confirmation must be done in the remotestorage class");
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
		synchronized (md) {

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


	public Map<Allocatable,Collection<Appointment>> getFirstAllocatableBindings(Collection<Allocatable> allocatables, Collection<Appointment> appointments, Collection<Reservation> ignoreList) throws RaplaException {
		lock.readLock().lock();
		Map<Allocatable, Map<Appointment, Collection<Appointment>>> allocatableBindings;
		try
		{
			allocatableBindings = conflictFinder.getAllocatableBindings(allocatables,	appointments, ignoreList,true);
		}
		finally
		{
			lock.readLock().unlock();
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
		lock.readLock().lock();
		try
		{
	    	return conflictFinder.getAllocatableBindings( allocatables, appointments, ignoreList, false);
	   	}
		finally
		{
			lock.readLock().unlock();
		}
    }
    
    
    @Override
    public Date getNextAllocatableDate(Collection<Allocatable> allocatables,Appointment appointment,Collection<Reservation> ignoreList,Integer worktimeStartMinutes,Integer worktimeEndMinutes, Integer[] excludedDays, Integer rowsPerHour) throws RaplaException {
		lock.readLock().lock();
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
				newState = ((AppointmentImpl) newState).deepClone();
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
			lock.readLock().unlock();
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
			Appointment appointment, Collection<Reservation> ignoreList) throws RaplaException {
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
    
    
    
    public Collection<Conflict> getConflicts(User user) throws RaplaException
    {
		lock.readLock().lock();
		try
		{
			return conflictFinder.getConflicts( user);
		}
		finally
		{
			lock.readLock().unlock();
		}			
    }

}
