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
package org.rapla.storage;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.rapla.components.util.Assert;
import org.rapla.components.util.iterator.FilterIterator;
import org.rapla.components.util.iterator.NestedIterator;
import org.rapla.entities.Category;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.IllegalAnnotationException;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.internal.PreferencesImpl;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentStartComparator;
import org.rapla.entities.domain.Period;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.internal.AllocatableImpl;
import org.rapla.entities.domain.internal.AppointmentImpl;
import org.rapla.entities.domain.internal.PeriodImpl;
import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.dynamictype.internal.AttributeImpl;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.entities.internal.CategoryImpl;
import org.rapla.entities.internal.UserImpl;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.RefEntity;
import org.rapla.entities.storage.internal.SimpleIdentifier;
import org.rapla.facade.Conflict;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaException;

public class LocalCache implements EntityResolver
{
    Map<Object,String> passwords = new HashMap<Object,String>();

    Map<Object,RefEntity<?>> entities;
    Set<DynamicTypeImpl> dynamicTypes;
    Set<UserImpl> users;
    Set<AllocatableImpl> resources;
    Set<ReservationImpl> reservations;
    Set<PeriodImpl> periods;

    Set<CategoryImpl> categories;
    Set<AppointmentImpl> appointments;
    Set<AttributeImpl> attributes;
    Set<PreferencesImpl> preferences;
    
    Map<RaplaType,Set<? extends RefEntity<?>>> entityMap;

    Locale locale;
    DynamicTypeImpl unresolvedAllocatableType = new DynamicTypeImpl();
    DynamicTypeImpl anonymousReservationType = new DynamicTypeImpl();
    {
    	try
    	{
	    	{
				DynamicTypeImpl unresolved = unresolvedAllocatableType;
				String key = "unresolved_resource";
				unresolved.setElementKey(key);
				unresolved.setId(new SimpleIdentifier(DynamicType.TYPE, -1));
				unresolved.setAnnotation(DynamicTypeAnnotations.KEY_NAME_FORMAT,"{"+key + "}");
				unresolved.getName().setName("en", "anonymous");
				unresolved.setAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE, DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON);
				unresolved.setReadOnly( true);
			}
			{
				DynamicTypeImpl unresolved = anonymousReservationType;
				String key = "anonymous";
				unresolved.setElementKey(key);
				unresolved.setId(new SimpleIdentifier(DynamicType.TYPE, 0));
				unresolved.setAnnotation(DynamicTypeAnnotations.KEY_NAME_FORMAT,"{"+key + "}");
				unresolved.setAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE, DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);
				unresolved.getName().setName("en", "anonymous");
				unresolved.setReadOnly( true);
			}
    	}
    	catch ( IllegalAnnotationException ex)
    	{
    		throw new IllegalStateException( ex.getMessage());
    	}
    }

    // Index for start and end dates
    TreeSet<Appointment> appointmentsStart;
    
    public SortedSet<Appointment> getAppointmentsSortedByStart() {
		return appointmentsStart;
	}
    
    class IdComparator implements Comparator<RefEntity<?>> {
        public int compare(RefEntity<?> o1,RefEntity<?> o2) {
            SimpleIdentifier id1 = (SimpleIdentifier)o1.getId();
            SimpleIdentifier id2 = (SimpleIdentifier)o2.getId();
            if ( id1.getKey() == id2.getKey())
                return 0;
            return (id1.getKey() < id2.getKey()) ? -1 : 1;
        }
    }

    private CategoryImpl superCategory = new CategoryImpl();

    public LocalCache(Locale locale) {
        this.locale = locale;
        superCategory.setId(LocalCache.SUPER_CATEGORY_ID);
        superCategory.setKey("supercategory");
        superCategory.getName().setName("en", "Root");
        
        Comparator<RefEntity<?>> comp = new IdComparator();
        
        entityMap = new HashMap<RaplaType, Set<? extends RefEntity<?>>>();
        entities = new HashMap<Object, RefEntity<?>>();
        // top-level-entities
        reservations = new TreeSet<ReservationImpl>(comp);
        periods = new TreeSet<PeriodImpl>(comp);
        users = new TreeSet<UserImpl>(comp);
        resources = new TreeSet<AllocatableImpl>(comp);
        dynamicTypes = new TreeSet<DynamicTypeImpl>(comp);

        // non-top-level-entities with exception of one super-category
        categories = new HashSet<CategoryImpl>();
        appointments = new HashSet<AppointmentImpl>();
        preferences = new HashSet<PreferencesImpl>();
        attributes = new HashSet<AttributeImpl>();
        
        entityMap.put(DynamicType.TYPE,dynamicTypes);
        entityMap.put(Attribute.TYPE, attributes);
        entityMap.put(Category.TYPE, categories);
        entityMap.put(Allocatable.TYPE,resources);
        entityMap.put(User.TYPE,users);
        entityMap.put(Period.TYPE,periods);
        entityMap.put(Reservation.TYPE,reservations);
        entityMap.put(Appointment.TYPE,appointments);
        entityMap.put(Preferences.TYPE, preferences);


        appointmentsStart = new TreeSet<Appointment>(new AppointmentStartComparator());
        initSuperCategory();
    }
    
    /** @return true if the entity has been removed and false if the entity was not found*/
    synchronized public boolean remove(RefEntity<?> entity) {
        RaplaType raplaType = entity.getRaplaType();
        Set<? extends RefEntity<?>> entitySet = entityMap.get(raplaType);
        boolean bResult = true;
        if (entitySet != null) {
            if (entities.get(entity.getId()) != null)
                bResult = false;
            if (entity.getId() == null)
                return false;


            if ( Appointment.TYPE.equals( raplaType )) {
                removeAppointment(entity);
            }

            entities.remove(entity.getId());
            entitySet.remove( entity );
        } else {
            throw new RuntimeException("UNKNOWN TYPE. Can't remove object:" + entity.getRaplaType());
        }
        return bResult;
    }

    synchronized public void put(RefEntity<?> entity) {
        Assert.notNull(entity);
        RaplaType raplaType = entity.getRaplaType();
        Object id = entity.getId();
        if (id == null)
            throw new IllegalStateException("ID can't be null");

        @SuppressWarnings("unchecked")
		Set<RefEntity<?>> entitySet =  (Set<RefEntity<?>>) entityMap.get(raplaType);
        if (entitySet != null) {

            if (Appointment.TYPE.equals( raplaType )) {
                removeAppointment(entity);
                Appointment appointment = (Appointment)entity;
				appointmentsStart.add(appointment);
	        }
            entities.put(id,entity);
            entitySet.remove( entity );
			entitySet.add( entity );
        } 
        else 
        {
            throw new RuntimeException("UNKNOWN TYPE. Can't store object in cache: " + entity.getRaplaType());
        }
    }


    public RefEntity<?> get(Object id) {
        if (id == null)
            throw new RuntimeException("id is null");
        return entities.get(id);
    }

    synchronized private void removeAppointment(RefEntity<?> entity) {
        if (appointments.remove(entity)) {
            // start date could have been changed, so we probably won't find it with a binary search
            if (!appointmentsStart.remove(entity)) {
                Iterator<Appointment> it = appointmentsStart.iterator();
                while (it.hasNext())
                    if (entity.equals(it.next())) {
                        it.remove();
                        break;
                    }
            }
        }
    }

    @SuppressWarnings("unchecked")
	synchronized public <T extends RefEntity<?>> Collection<T> getCollection(RaplaType type) {
        Set<? extends RefEntity<?>> entities =  entityMap.get(type);
        if ( Period.TYPE.equals( type)) {
            entities = new TreeSet<RefEntity<?>>( entities);
        }

        if (entities != null) {
            return (Collection<T>) entities;
        } else {
            throw new RuntimeException("UNKNOWN TYPE. Can't get collection: "
                                       +  type);
        }
    }

    @SuppressWarnings("unchecked")
	public <T extends RaplaObject> Collection<T> getCollection(Class<T> clazz)  {
    	RaplaType type = RaplaType.get(clazz);
		return (Collection<T>) getCollection(type);
    }

    /*
    @SuppressWarnings("unchecked")
	public Iterator<RefEntity<?>> getIterator(RaplaType type) throws RaplaException {
    	Set<? extends RefEntity<?>> entities =  entityMap.get(type);
        if (entities != null) {
            return (Iterator<RefEntity<?>>) entities.iterator();
        }
        throw new RaplaException("Can't get iterator for  "  + type);
    }
    
    @SuppressWarnings("unchecked")
	public <T extends RaplaObject> Iterator<T> getIterator(Class<T> clazz) throws RaplaException {
    	RaplaType type = RaplaType.get(clazz);
		return (Iterator<T>) getIterator(type);
    }
	*/

    synchronized public void clearAll() {
        passwords.clear();
        Iterator<Set<? extends RefEntity<?>>> it = entityMap.values().iterator();
        while (it.hasNext()) {
            it.next().clear();
        }
        appointmentsStart.clear();
        entities.clear();
        initSuperCategory();
    }

    private void initSuperCategory() {
        entities.put (LocalCache.SUPER_CATEGORY_ID, superCategory);
        superCategory.setReadOnly( false );
        categories.add( superCategory );
        Category[] childs = superCategory.getCategories();
        for (int i=0;i<childs.length;i++) {
            superCategory.removeCategory( childs[i] );
        }
    }

    public static Comparable SUPER_CATEGORY_ID = new SimpleIdentifier(Category.TYPE,0);
    public CategoryImpl getSuperCategory() {
        return (CategoryImpl) get(SUPER_CATEGORY_ID);
    }

    synchronized public Collection<Attribute> getAttributeList(RefEntity<?> ref)    {
        HashSet<Attribute> result = new HashSet<Attribute>();
        Iterator<AttributeImpl> it = attributes.iterator();
        while (it.hasNext()) {
            AttributeImpl attribute =  it.next();
            if (attribute.isRefering(ref))
                result.add(attribute);
        }
        return result;
    }

    synchronized public Collection<DynamicType> getDynamicTypeList(RefEntity<?> ref)    {
        HashSet<DynamicType> result = new HashSet<DynamicType>();
        Iterator<Attribute> it = getAttributeList(ref).iterator();
        while (it.hasNext()) {
            result.add(it.next().getDynamicType());
        }
        return result;
    }

    synchronized public Collection<RefEntity<?>> getReferers(RaplaType raplaType,RefEntity<?> object) {
        ArrayList<RefEntity<?>> result = new ArrayList<RefEntity<?>>();
        Iterator<RefEntity<?>> it = getCollection(raplaType).iterator();
        while (it.hasNext())
        {
        	RefEntity<?> referer =  it.next();
            if (referer != null && !referer.isIdentical(object) && referer.isRefering(object)) {
                result.add(referer);
            }
        }
        return result;
    }

    synchronized public UserImpl getUser(String username) {
        for (UserImpl user: users)
        {
            if (user.getUsername().equals(username))
                return user;
        }
        for (UserImpl user: users)
        {
            if (user.getUsername().equalsIgnoreCase(username))
                return user;
        }
        return null;
    }

    synchronized public PreferencesImpl getPreferences(User user) {
        Iterator<PreferencesImpl> it = preferences.iterator();
        while (it.hasNext()) {
            PreferencesImpl pref =  it.next();
            User owner = pref.getOwner();
            if ( user == null && owner == null ) {
                return pref;
            }
            if (user!= null && pref.getOwner() != null && user.equals(pref.getOwner())) {
                return pref;
            }

        }
        return null;
    }

    static public Comparable getId(RaplaType type,String str) throws ParseException {
    	if (str == null)
    		throw new ParseException("Id string for " + type + " can't be null", 0);
    	int index = str.lastIndexOf("_") + 1;
        if (index>str.length())
            throw new ParseException("invalid rapla-id '" + str + "'", index);
        try {
        	if ( type == null )
        	{
        		if ( index <= 0)
        		{
		            throw new ParseException("invalid rapla-id '" + str + "' Type is missing and not passed as argument.", index);
        		}
        		String typeName = str.substring(0, index -1);
        		try {
					type = RaplaType.find(typeName);
				} catch (RaplaException e) {
		            throw new ParseException("invalid rapla-id '" + str + "'", index);
				}
        	}
        	return new SimpleIdentifier(type,Integer.parseInt(str.substring(index)));
        } catch (NumberFormatException ex) {
            throw new ParseException("invalid rapla-id '" + str + "'", index);
        }
    }

    synchronized public DynamicTypeImpl getDynamicType(String elementKey) {
        Iterator<DynamicTypeImpl> it = dynamicTypes.iterator();
        while (it.hasNext()) {
            DynamicTypeImpl dt =  it.next();
            if (dt.getElementKey().equals(elementKey))
                return dt;
        }
        return null;
    }

    synchronized public Iterator<RefEntity<?>> getVisibleEntities(final User user) {
        return new NestedIterator<RefEntity<?>>(entityMap.keySet().iterator()) {
                @SuppressWarnings("unchecked")
				public Iterator<RefEntity<?>> getNestedIterator(Object key) {
                    RaplaType raplaType = (RaplaType)key;
                    if (   Conflict.TYPE.equals( raplaType ))
                    {
                    	return null;
                    }
                    Set<RefEntity<?>> set =  (Set<RefEntity<?>>) entityMap.get( key);
                    if (   Appointment.TYPE.equals( raplaType )  || Reservation.TYPE.equals( raplaType))
                    {
                    	return new FilterIterator<RefEntity<?>>(set.iterator()) {

							protected boolean isInIterator(Object obj) {
								boolean canRead = RaplaComponent.isTemplate((RaplaObject<?>)obj);
								return canRead;
							}
						};
                    }
                    
                    if (user != null && !user.isAdmin())
                    {
	                    if (   Preferences.TYPE.equals( raplaType )  )
	                    {
	                        List<RefEntity<?>> list = new ArrayList<RefEntity<?>>();
	                        {
	                            PreferencesImpl preferences = getPreferences( null );
	                            if ( preferences != null)
	                            {
	                                list.add( preferences);
	                            }
	                        }
	                        {
	                            PreferencesImpl preferences = getPreferences( user );
	                            if ( preferences != null)
	                            {
	                                list.add( preferences);
	                            }
	                        }
	                        return list.iterator();
	                    }
	                    if (   Allocatable.TYPE.equals( raplaType )  )
	                    {
	                    	return new FilterIterator<RefEntity<?>>(set.iterator()) {

								protected boolean isInIterator(Object obj) {
									Allocatable alloc = (Allocatable) obj;
									boolean canRead = alloc.canReadOnlyInformation( user);
									return canRead;
								}
							};
	                    }
                    }
                    return set.iterator();
                }
            };
    }

    synchronized public  Iterator<RefEntity<?>> getAllEntities() {
        return new NestedIterator<RefEntity<?>>(entityMap.keySet().iterator()) {
                @SuppressWarnings("unchecked")
				public Iterator<RefEntity<?>> getNestedIterator(Object raplaType) {
                    Set<RefEntity<?>> set = (Set<RefEntity<?>>) entityMap.get(raplaType);
                    return set.iterator();
                }
                /*
                public Object next() {
                    Object obj = super.next();
                    System.out.println(obj);
                    return obj;
                }
                */
            };
    }

    // Implementation of EntityResolver
    public RefEntity<?> resolve(Object id) throws EntityNotFoundException {
        if (!(id instanceof SimpleIdentifier))
            throw new EntityNotFoundException("Unknown identifier class: " + id.getClass()
                                        + ". Only the SimpleIdentier class is supported.");
        RefEntity<?> entity =  get(id);

        if (entity == null)
            throw new EntityNotFoundException("Object for id [" + id.toString() + "] not found",(Comparable)id);
        return entity;
    }

    public String getPassword(Object userId) {
        return  passwords.get(userId);
    }

    public void putPassword(Object userId, String password) {
        passwords.put(userId,password);
    }

    synchronized public void putAll( Collection<? extends RefEntity<?>> list )
    {
    	for (RefEntity<?> obj: list )
    	{
            put(obj);
        }
    }
    

	synchronized public RefEntity<?> resolveEmail(final String emailArg) throws EntityNotFoundException
    {
		Set<? extends RefEntity<?>> entities = entityMap.get(Allocatable.TYPE);
    	for (RefEntity<?> entity: entities)
    	{
    		final Classification classification = ((Allocatable) entity).getClassification();
    		final Attribute attribute = classification.getAttribute("email");
    		if ( attribute != null)
    		{
    			final String email = (String)classification.getValue(attribute);
    			if ( email != null && email.equals( emailArg))
    			{
    				return entity;
    			}
    		}
        }
    	throw new EntityNotFoundException("Object for email " + emailArg + " not found");
    }

	public DynamicTypeImpl getUnresolvedAllocatableType() 
	{
		return unresolvedAllocatableType;
	}



	public DynamicTypeImpl getAnonymousReservationType() 
	{
		return anonymousReservationType;
	}

}
