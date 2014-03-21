/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Christopher Kohlhaas                                  |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org .       |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, of which license fullfill the Open Source     |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/
package org.rapla.entities.domain.internal;
/** The default Implementation of the <code>Reservation</code>
 *  @see ModificationEvent
 *  @see org.rapla.facade.ClientFacade
 */
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.rapla.components.util.Assert;
import org.rapla.components.util.iterator.IteratorChain;
import org.rapla.entities.Entity;
import org.rapla.entities.IllegalAnnotationException;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.dynamictype.internal.ClassificationImpl;
import org.rapla.entities.internal.ModifiableTimestamp;
import org.rapla.entities.storage.CannotExistWithoutTypeException;
import org.rapla.entities.storage.DynamicTypeDependant;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.ParentEntity;
import org.rapla.entities.storage.UnresolvableReferenceExcpetion;
import org.rapla.entities.storage.internal.SimpleEntity;


public class ReservationImpl extends SimpleEntity implements Reservation, ModifiableTimestamp, DynamicTypeDependant, ParentEntity
{
    private ClassificationImpl classification;
    private List<AppointmentImpl> appointments = new ArrayList<AppointmentImpl>(1);
    private Map<String,List<String>> restrictions;
    private Map<String,String> annotations;
    private Date lastChanged;
    private Date createDate;
    
    transient HashMap<String,AppointmentImpl> appointmentIndex;
    public static final String PERMISSION_READ = "permission_read";
	public static final String PERMISSION_MODIFY = "permission_modify";
        
    ReservationImpl() {
        this (null, null);
    }

    public ReservationImpl( Date createDate, Date lastChanged ) {
        this.createDate = createDate;
        if (createDate == null)
            this.createDate = new Date();
        this.lastChanged = lastChanged;
        if (lastChanged == null)
            this.lastChanged = this.createDate;
    }

    public void setResolver( EntityResolver resolver)  {
        super.setResolver( resolver);
        for (AppointmentImpl child:appointments)
        {
        	child.setParent( this);
        }
        if ( classification != null)
        {
        	classification.setResolver( resolver);
        }
    }
    
    public void addEntity(Entity entity) {
        checkWritable();
    	AppointmentImpl app = (AppointmentImpl) entity;
    	app.setParent(this);
		appointments.add(  app);
        appointmentIndex = null;
    }


    public void setReadOnly() {
        super.setReadOnly(  );
        classification.setReadOnly( );
    }

    final public RaplaType<Reservation> getRaplaType() {return TYPE;}

    // Implementation of interface classifiable
    public Classification getClassification() { return classification; }
    public void setClassification(Classification classification) {
        checkWritable();
        this.classification = (ClassificationImpl) classification;
    }

    public String getName(Locale locale) {
        Classification c = getClassification();
        if (c == null)
            return "";
        return c.getName(locale);
    }

    public Date getLastChangeTime() {
        return lastChanged;
    }

    public Date getCreateTime() {
        return createDate;
    }

    public void setLastChanged(Date date) {
        lastChanged = date;
    }

    public Appointment[] getAppointments()   {
        return appointments.toArray(Appointment.EMPTY_ARRAY);
    }
    
    @SuppressWarnings("unchecked")
	public Collection<AppointmentImpl> getSubEntities()
    {
		return appointments;
    }
    
    @Override
    public Iterable<String> getReferencedIds() {
        return new IteratorChain<String>
            (
             super.getReferencedIds()
             ,classification.getReferencedIds()
            )
            ;
    }
    
    public void removeAllSubentities() {
    	appointments.clear();
    }

    public void addAppointment(Appointment appointment) {
        if (appointment.getReservation() != null
            && !this.isIdentical(appointment.getReservation()))
            throw new IllegalStateException("Appointment '" + appointment
                                            + "' belongs to another reservation :"
                                            + appointment.getReservation());
        this.addEntity(appointment);

    }

    public void removeAppointment(Appointment appointment)   {
        checkWritable();
        if ( findAppointment( appointment ) == null)
            return;
        appointments.remove( appointment );
        // Remove allocatable if its restricted to the appointment
        String appointmentId = appointment.getId();
        // we clone the list so we can safely remove a refererence it while traversing
		Collection<String> ids = new ArrayList<String>(getIds("resources"));
        for (String allocatableId:ids) {
        	List<String> restriction = getRestrictionPrivate(allocatableId);
			if (restriction.size() == 1 && restriction.get(0).equals(appointmentId)) {
				removeId(allocatableId);
			}
        }
        clearRestrictions(appointment);
        if (this.equals(appointment.getReservation()))
            ((AppointmentImpl) appointment).setParent(null);
        appointmentIndex = null;
    }

    private void clearRestrictions(Appointment appointment) {
        if (restrictions == null)
            return;
        ArrayList<String> list = null;
        for (String key:restrictions.keySet()) {
            List<String> appointments = restrictions.get(key);
            if ( appointments.size()>0)
            {
                if (list == null)
                    list = new ArrayList<String>();
                list.add(key);
            }
        }
        if (list == null)
            return;
        
        
        for (String key: list) 
        {
            ArrayList<String> newApps = new ArrayList<String>();
            for ( String appId:restrictions.get(key)) {
                if ( !appId.equals( appointment.getId() ) ) {
                    newApps.add( appId );
               }
            }
            setRestrictionForId( key, newApps);
        }
    }

    public void addAllocatable(Allocatable allocatable) {
        checkWritable();
        addAllocatablePrivate(allocatable.getId());
    }

	private void addAllocatablePrivate(String allocatableId) {
		if (isRefering(allocatableId))
            return;
		synchronized (this) {
			addId("resources",allocatableId);
		}
	}

    public void removeAllocatable(Allocatable allocatable)   {
        checkWritable();
        removeId(allocatable.getId());
    }

    public Allocatable[] getAllocatables()  {
        Collection<Allocatable> allocatables = getAllocatables(null);
		return 	allocatables.toArray(new Allocatable[allocatables.size()]);
    }

    public Allocatable[] getResources() {
        Collection<Allocatable> allocatables = getAllocatables(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE);
		return 	allocatables.toArray(new Allocatable[allocatables.size()]);
    }

    public Allocatable[] getPersons() {
        Collection<Allocatable> allocatables = getAllocatables(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON);
		return 	allocatables.toArray(new Allocatable[allocatables.size()]);
    }
    
    

    public Collection<Allocatable> getAllocatables(String annotationType) {
        Collection<Allocatable> allocatableList = new ArrayList<Allocatable>();
   		Collection<Entity> list = getList("resources");
		for (Entity o: list)
    	{
			Allocatable alloc = (Allocatable) o;
			if ( annotationType != null)
			{
				boolean person = alloc.isPerson();
				if (annotationType.equals(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON) ) 
				{
					if (!person)
					{
						continue;
					}
				}
				else
				{
					if (person)
					{
						continue;
					}
					
				}
			
			} else {
			}
			allocatableList.add(alloc);
    	}
		return allocatableList;
    }

    public boolean hasAllocated(Allocatable allocatable) {
        return isRefering(allocatable.getId());
    }

    public boolean hasAllocated(Allocatable allocatable,Appointment appointment) {
        if (!hasAllocated(allocatable))
            return false;
        if  (restrictions == null)
        {
        	return true;
        }
        List<String> r = this.restrictions.get(allocatable.getId());
        String appointmentId = appointment.getId();
		if ( r == null || r.size() == 0 || r.contains( appointmentId))
		{
			return true;
        }
        return false;
    }

    public void setRestriction(Allocatable allocatable,Appointment[] appointments) {
    	checkWritable();
    	List<String> appointmentIds = new ArrayList<String>();
    	for ( Appointment app:appointments)
    	{
    		appointmentIds.add( app.getId());
    	}
    	setRestrictionPrivate(allocatable, appointmentIds);
    }
    
    public Appointment[] getRestriction(Allocatable allocatable) {
        List<String> restrictionPrivate = getRestrictionPrivate(allocatable.getId());
		Appointment[] list = new Appointment[restrictionPrivate.size()];
		int i=0;
		updateIndex();
        for (String id:restrictionPrivate)
        {
        	list[i++] = appointmentIndex.get( id );
        }
		return list;
    }

	private void updateIndex() {
		if (appointmentIndex == null)
		{
			appointmentIndex = new HashMap<String,AppointmentImpl>();
			for (AppointmentImpl app: appointments)
			{
				appointmentIndex.put( app.getId(), app);
			}
		}
	}


	protected void setRestrictionPrivate(Allocatable allocatable,List<String> appointmentIds) {
		String id = allocatable.getId();
        if ( !hasAllocated( allocatable))
        {
            addAllocatablePrivate( allocatable.getId());
        }
        Assert.notNull(id,"Allocatable object has no ID");
        setRestrictionForId(id,appointmentIds);
	}

    
    public void setRestriction(Appointment appointment, Allocatable[] restrictedAllocatables) {
    	for ( Allocatable alloc: restrictedAllocatables)
        {
            List<String> restrictions = new ArrayList<String>();
            String allocatableId = alloc.getId();
			if ( !hasAllocated( alloc))
            {
                addAllocatablePrivate( allocatableId);
            }
            else
            {
                restrictions.addAll(getRestrictionPrivate(allocatableId) );    
            }
            String appointmentId = appointment.getId();
			if ( !restrictions.contains(appointmentId))
            {
            	restrictions.add( appointmentId);
            }
            String id = allocatableId;
            setRestrictionForId( id, restrictions);
        }
    }

    public void setRestrictionForId(String id,List<String> appointmentIds) {
        if (restrictions == null)
        {
            restrictions = new HashMap<String,List<String>>(1);
        }
        if (appointmentIds == null || appointmentIds.size() == 0)
        {
            restrictions.remove(id);
        }
        else
        {
            restrictions.put(id, appointmentIds);
        }
    }
    
    public void addRestrictionForId(String id,String appointmentId) {
        if (restrictions == null)
            restrictions = new HashMap<String,List<String>>(1);
        List<String> appointments = restrictions.get( id );
        if ( appointments == null)
        {
        	appointments = new ArrayList<String>();
        	restrictions.put(id , appointments);
        }
        appointments.add(appointmentId);
    }


	public List<String> getRestrictionPrivate(String allocatableId) {
        Assert.notNull(allocatableId,"Allocatable object has no ID");
        if (restrictions != null) {
            List<String> restriction =  restrictions.get(allocatableId);
            if (restriction != null)
            {
                return restriction;
            }
        }
        return Collections.emptyList();
	}

    public Appointment[] getAppointmentsFor(Allocatable allocatable) {
        Appointment[] restrictedAppointments = getRestriction( allocatable);
        if ( restrictedAppointments.length == 0)
            return getAppointments();
        else
            return restrictedAppointments;
    }

    public Allocatable[] getRestrictedAllocatables(Appointment appointment) {
        HashSet<Allocatable> set = new HashSet<Allocatable>();
        for (String allocatableId: getIds("resources")) {
            for (String restriction:getRestrictionPrivate( allocatableId ))
            {
            	if ( restriction.equals( appointment.getId() ) ) {
            		Allocatable alloc = (Allocatable) getResolver().tryResolve( allocatableId);
            		if ( alloc == null)
            		{
            			throw new UnresolvableReferenceExcpetion( allocatableId, toString());
            		}
            		set.add( alloc );
                }
            }
        }
        return set.toArray( Allocatable.ALLOCATABLE_ARRAY);
    }

    public Allocatable[] getAllocatablesFor(Appointment appointment) {
        HashSet<Allocatable> set = new HashSet<Allocatable>();
        Collection<String> list = getIds("resources");
        String id = appointment.getId();
        for (String allocatableId:list) {
        	boolean found = false;
            List<String> restriction = getRestrictionPrivate( allocatableId );
            if ( restriction.size() == 0)
            {
            	found = true;
            }
            else
            {
                for (String rest:restriction) {
    				if ( rest.equals( id ) ) {
    					found = true;
                    }
                }
            }
            if (found )
            {
            	Allocatable alloc = (Allocatable) getResolver().tryResolve( allocatableId);
            	if ( alloc == null)
        		{
        			throw new UnresolvableReferenceExcpetion( allocatableId, toString());
        		}
            	set.add( alloc);
            }
        }
        return  set.toArray( Allocatable.ALLOCATABLE_ARRAY);
    }

    public Appointment findAppointment(Appointment copy) {
		updateIndex();
        String id = copy.getId();
		return (Appointment) appointmentIndex.get( id);
    }


    public boolean needsChange(DynamicType type) {
        return classification.needsChange( type );
    }

    public void commitChange(DynamicType type) {
        classification.commitChange( type );
    }

    public ReservationImpl clone() {
        ReservationImpl clone = new ReservationImpl();
        super.deepClone(clone);
        // First we must invalidate the arrays.
        clone.classification = (ClassificationImpl) classification.clone();
        for (String resourceId:getIds("resources"))
        {
        	if ( restrictions != null)
        	{
        		if ( clone.restrictions == null)
        		{
        			clone.restrictions = new LinkedHashMap<String,List<String>>();
        		}
        		List<String> list = restrictions.get( resourceId);
	        	if ( list != null)
	        	{
	        		clone.restrictions.put( resourceId, new ArrayList<String>(list));
	        	}
        	}
        }
        clone.createDate = createDate;
        clone.lastChanged = lastChanged;
        @SuppressWarnings("unchecked")
        Map<String,String> annotationClone = (Map<String, String>) (annotations != null ?  ((HashMap<String,String>)(annotations)).clone() : null);
		clone.annotations = annotationClone;
        return clone;
    }

    public void commitRemove(DynamicType type) throws CannotExistWithoutTypeException 
    {
        classification.commitRemove(type);
    }

	public Date getFirstDate() 
	{
        Appointment[] apps = getAppointments();
        Date minimumDate = null;
        for (int i=0;i< apps.length;i++) {
            Appointment app = apps[i];
            if ( minimumDate == null || app.getStart().before( minimumDate)) {
                minimumDate = app.getStart();
            }
        }
        return minimumDate;
	}
	
	public Date getMaxEnd() 
	{
        Appointment[] apps = getAppointments();
        Date maximumDate = getFirstDate();
        for (int i=0;i< apps.length;i++) {
			if ( maximumDate == null)
			{
				break;
			}
            Appointment app = apps[i];
            Date maxEnd = app.getMaxEnd();
			if ( maxEnd == null || maxEnd.after( maximumDate)) {
            	maximumDate = maxEnd;
            }
        }
        return maximumDate;
	}
	
    public String getAnnotation(String key) {
    	if ( annotations == null)
    	{
    		return null;
    	}
        return annotations.get(key);
    }

    public String getAnnotation(String key, String defaultValue) {
        String annotation = getAnnotation( key );
        return annotation != null ? annotation : defaultValue;
    }

    public void setAnnotation(String key,String annotation) throws IllegalAnnotationException {
        checkWritable();
        if ( annotations == null)
        {
        	annotations = new LinkedHashMap<String, String>(1);
        }
        if (annotation == null) {
            annotations.remove(key);
            return;
        }
        annotations.put(key,annotation);
    }

    public String[] getAnnotationKeys() {
    	if ( annotations == null)
    	{
    		return RaplaObject.EMPTY_STRING_ARRAY;
    	}
        return annotations.keySet().toArray(RaplaObject.EMPTY_STRING_ARRAY);
    }



}








