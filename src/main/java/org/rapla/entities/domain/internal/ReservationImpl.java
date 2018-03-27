/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
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
 *  @see org.rapla.facade.RaplaFacade
 */

import org.rapla.RaplaResources;
import org.rapla.components.util.Assert;
import org.rapla.components.util.iterator.IterableChain;
import org.rapla.components.util.iterator.NestedIterable;
import org.rapla.entities.Entity;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.AppointmentStartComparator;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.RaplaObjectAnnotations;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.dynamictype.internal.ClassificationImpl;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.entities.dynamictype.internal.EvalContext;
import org.rapla.entities.dynamictype.internal.ParsedText;
import org.rapla.entities.internal.ModifiableTimestamp;
import org.rapla.entities.storage.CannotExistWithoutTypeException;
import org.rapla.entities.storage.DynamicTypeDependant;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.ParentEntity;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.entities.storage.UnresolvableReferenceExcpetion;
import org.rapla.entities.storage.internal.SimpleEntity;
import org.rapla.framework.RaplaException;

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
import java.util.stream.Stream;

public final class ReservationImpl extends SimpleEntity implements Reservation, ModifiableTimestamp, DynamicTypeDependant, ParentEntity
{
    private ClassificationImpl classification;
    private List<AppointmentImpl> appointments = new ArrayList<>(1);
    private List<PermissionImpl> permissions = new ArrayList<>(1);
    private Map<String,List<String>> restrictions;
    private Map<String,String> annotations;
    private Date lastChanged;
    private Date createDate;
    
    transient HashMap<String,AppointmentImpl> appointmentIndex;
        
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

    public static void checkReservation(RaplaResources i18n,Reservation reservation, EntityResolver resolver) throws RaplaException
    {
        Locale locale = i18n.getLocale();
        String name = reservation.getName(locale);
        if (reservation.getAppointments().length == 0)
        {
            throw new RaplaException(i18n.getString("error.no_appointment") + " " + name + " [" + reservation.getId() + "]");
        }
        final String templateId = reservation.getAnnotation(RaplaObjectAnnotations.KEY_TEMPLATE);
        Allocatable template;
        if ( templateId != null)
        {
            template = resolver.tryResolve( templateId, Allocatable.class );
        }
        else
        {
            template = null;
        }
        if (reservation.getAllocatables().length == 0 && template == null)
        {
            throw new RaplaException(i18n.getString("warning.no_allocatables_selected") + " " + name + " [" + reservation.getId() + "]");
        }
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
        for (PermissionImpl p:permissions)
        {
             p.setResolver( resolver);
        }
    }
    
    public Collection<Appointment> getSortedAppointments()
    {
        List<Appointment> sortedAppointments = new ArrayList<>(appointments);
        Collections.sort(sortedAppointments, new AppointmentStartComparator() );
        return sortedAppointments;
    }
    
    public int indexOf(Appointment app)
    {
        int indexOf = appointments.indexOf( app);
        return indexOf;
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

    public Class<Reservation> getTypeClass()
    {
        return Reservation.class;
    }

    // Implementation of interface classifiable
    public Classification getClassification() { return classification; }
    public void setClassification(Classification classification) {
        checkWritable();
        this.classification = (ClassificationImpl) classification;
    }
    
    public void addPermission(Permission permission) {
        checkWritable();
        permissions.add((PermissionImpl)permission);
    }

    public boolean removePermission(Permission permission) {
        checkWritable();
        return permissions.remove(permission);
    }

    public Permission newPermission() {
        PermissionImpl permissionImpl = new PermissionImpl();
        if ( resolver != null)
        {
            permissionImpl.setResolver( resolver);
        }
        return permissionImpl;
    }
    
    public Collection<Permission> getPermissionList()
    {
        Collection uncasted = permissions;
        @SuppressWarnings("unchecked")
        Collection<Permission> casted = uncasted;
        return casted;
    }

    public String getName(Locale locale) {
        Classification c = getClassification();
        if (c == null)
            return "";
        return format( locale, DynamicTypeAnnotations.KEY_NAME_FORMAT);
    }
    
    public String format(Locale locale, String annotationName)
    {
        return formatAppointment( locale, annotationName, (Appointment)null);
    }

    public String formatAppointment(Locale locale, String annotationName, Appointment appointment)
    {
        DynamicTypeImpl type = (DynamicTypeImpl)getClassification().getType();
        ParsedText parsedAnnotation = type.getParsedAnnotation( annotationName );
        if (parsedAnnotation == null)
        {
            return "";
        }
        EvalContext evalContext = appointment != null ? createEvalContext(locale, annotationName, appointment ) : createEvalContext(locale, annotationName, this);
        String nameString = parsedAnnotation.formatName(evalContext).trim();
        return nameString;
    }

    public String formatAppointmentBlock(Locale locale, String annotationName, AppointmentBlock block)
    {
        DynamicTypeImpl type = (DynamicTypeImpl)getClassification().getType();
        ParsedText parsedAnnotation = type.getParsedAnnotation( annotationName );
        if (parsedAnnotation == null)
        {
            return "";
        }
        EvalContext evalContext = block != null ? createEvalContext(locale,  annotationName, block ) : createEvalContext(locale,  annotationName, this);
        String nameString = parsedAnnotation.formatName(evalContext).trim();
        return nameString;
    }
    
    private EvalContext createEvalContext(Locale locale, String annotationName, Object object)
    {
        DynamicTypeImpl type = (DynamicTypeImpl)getClassification().getType();
        return type.createEvalContext(locale, annotationName, object);
    }
    
    public Date getLastChanged() {
        return lastChanged;
    }
    
    public Date getCreateDate() {
        return createDate;
    }

    public void setLastChanged(Date date) {
    	checkWritable();
    	lastChanged = date;
    }
    
    @Override public void setCreateDate(Date date)
    {
        checkWritable();
        this.createDate = date;
    }


    public Appointment[] getAppointments()   {
        return appointments.toArray(Appointment.EMPTY_ARRAY);
    }
    
    public Collection<AppointmentImpl> getAppointmentList()
    {
    	return appointments;
    }

    @Override
    public Stream<Appointment> getAppointmentStream() {
        return (Stream)appointments.stream();
    }

    @SuppressWarnings("unchecked")
	public Collection<AppointmentImpl> getSubEntities()
    {
		return appointments;
    }
    
    @Override
    public Iterable<ReferenceInfo> getReferenceInfo() {
        return new IterableChain<>
                (
                        super.getReferenceInfo()
                        , classification.getReferenceInfo()
                        , new NestedIterable<ReferenceInfo, PermissionImpl>(permissions) {
                    public Iterable<ReferenceInfo> getNestedIterable(PermissionImpl obj) {
                        return obj.getReferenceInfo();
                    }
                }
                );
    }

    
    @Override
    protected Class<? extends Entity> getInfoClass(String key) {
        Class<? extends Entity> result = super.getInfoClass(key);
        if ( result == null)
        {
            if ( key.equals("resources"))
            {
                return Allocatable.class;
            }
        }
        return result;
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
        appointments.remove( appointment );
        // Remove allocatable if its restricted to the appointment
        String appointmentId = appointment.getId();
        // we clone the list so we can safely remove a refererence it while traversing
		Collection<String> ids = new ArrayList<>(getIds("resources"));
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
                    list = new ArrayList<>();
                list.add(key);
            }
        }
        if (list == null)
            return;
        
        
        for (String key: list) 
        {
            ArrayList<String> newApps = new ArrayList<>();
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
        if ( hasAllocated( allocatable))
        {
            return;
        }
        addAllocatablePrivate(allocatable);
    }

	private void addAllocatablePrivate(Allocatable allocatable) {
		synchronized (this) {
		    add("resources",allocatable);
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
        Collection<Allocatable> allocatableList = new ArrayList<>();
   		Collection<Allocatable> list = getList("resources", Allocatable.class);
		for (Allocatable alloc: list)
    	{
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
        return isRefering("resources",allocatable.getId());
    }

    public boolean hasAllocatedOn(Allocatable allocatable,Appointment appointment) {
        if (!hasAllocated(allocatable))
            return false;
        if  (restrictions == null)
        {
        	return true;
        }
        List<String> r = this.restrictions.get(allocatable.getId());
        String appointmentId = appointment.getId();
        return r == null || r.size() == 0 || r.contains(appointmentId);
    }

    public void setRestriction(Allocatable allocatable,Appointment[] appointments) {
    	checkWritable();
    	List<String> appointmentIds;
    	if ( appointments != null)
    	{
        	appointmentIds = new ArrayList<>();
        	for ( Appointment app:appointments)
        	{
        		appointmentIds.add( app.getId());
        	}
    	}
    	else
    	{
    	    appointmentIds = null;
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
			appointmentIndex = new HashMap<>();
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
            addAllocatable( allocatable);
        }
        Assert.notNull(id,"Allocatable object has no ID");
        setRestrictionForId(id,appointmentIds);
	}

    
    public void setRestrictionForAppointment(Appointment appointment, Allocatable[] restrictedAllocatables) {
    	for ( Allocatable alloc: restrictedAllocatables)
        {
            List<String> restrictions = new ArrayList<>();
            String allocatableId = alloc.getId();
			if ( !hasAllocated( alloc))
            {
                addAllocatable( alloc);
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
            restrictions = new HashMap<>(1);
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
            restrictions = new HashMap<>(1);
        List<String> appointments = restrictions.get( id );
        if ( appointments == null)
        {
        	appointments = new ArrayList<>();
        	restrictions.put(id , appointments);
        }
        appointments.add(appointmentId);
    }

	private List<String> getRestrictionPrivate(String allocatableId) {
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
        if ( !hasAllocated(allocatable))
        {
            return Appointment.EMPTY_ARRAY;
        }
        Appointment[] restrictedAppointments = getRestriction( allocatable);
        if ( restrictedAppointments.length == 0)
            return getAppointments();
        else
            return restrictedAppointments;
    }

    public Allocatable[] getRestrictedAllocatables(Appointment appointment) {
        HashSet<Allocatable> set = new HashSet<>();
        for (String allocatableId: getIds("resources")) {
            for (String restriction:getRestrictionPrivate( allocatableId ))
            {
            	if ( restriction.equals( appointment.getId() ) ) {
            		Allocatable alloc = getResolver().tryResolve( allocatableId, Allocatable.class);
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

    public Collection<ReferenceInfo<Allocatable>> getAllocatableIdsFor(Appointment appointment)
    {
        HashSet<ReferenceInfo<Allocatable>> set = new HashSet<>();
        Collection<String> list = getIds("resources");
        String id = appointment.getId();
        for (String allocatableId:list) {
            boolean found = false;
            List<String> restriction = getRestrictionPrivate( allocatableId );
            if ( restriction.isEmpty())
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
                set.add(new ReferenceInfo<>(allocatableId, Allocatable.class));
            }
        }
        return set;
    }

    public Stream<Allocatable> getAllocatablesFor(Appointment appointment) {
        Collection<String> list = getIds("resources");
        String appointmentId = appointment.getId();
        final Stream<Allocatable> allocatableStream = list.stream().filter((allocId) -> {
            List<String> restrictions = getRestrictionPrivate(allocId);
            return restrictions.isEmpty() || restrictions.contains(appointmentId);
        }).map(allocId -> resolve(allocId, Allocatable.class));
        return allocatableStream;
    }

    private <T extends Entity> T resolve( String id, Class<T> tClass)
    {
        final EntityResolver resolver = getResolver();
        final T alloc = resolver.tryResolve(id,tClass);
        if ( alloc == null)
        {
            throw new UnresolvableReferenceExcpetion( tClass.getName() + ":" + id, toString());
        }
        return alloc;
    }

    public Appointment findAppointment(Appointment copy) {
		updateIndex();
        String id = copy.getId();
		return appointmentIndex.get( id);
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
        clone.classification =  classification.clone();
        clone.permissions.clear();
        for (PermissionImpl perm:permissions) {
            PermissionImpl permClone = perm.clone();
            clone.permissions.add(permClone);
        }
        for (String resourceId:getIds("resources"))
        {
        	if ( restrictions != null)
        	{
        		if ( clone.restrictions == null)
        		{
        			clone.restrictions = new LinkedHashMap<>();
        		}
        		List<String> list = restrictions.get( resourceId);
	        	if ( list != null)
	        	{
	        		clone.restrictions.put( resourceId, new ArrayList<>(list));
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

    public void setAnnotation(String key,String annotation)  {
        checkWritable();
        if ( annotations == null)
        {
        	annotations = new LinkedHashMap<>(1);
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
    
    @Override
    public int compareTo(Object r2) {
        if ( ! (r2 instanceof Reservation))
        {
            return super.compareTo( r2);
        }
        int result = SimpleEntity.timestampCompare( this,(Reservation)r2);
        if ( result != 0)
        {
            return result;
        }
        else
        {
            return super.compareTo( r2);
        }
    }
    
    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append(getTypeClass());
        buf.append(" [");
        buf.append(super.toString());
        buf.append("] ");
        try
        {
            if ( getClassification() != null) {
                buf.append (getClassification().toString()) ;
            }
        }
        catch ( NullPointerException ex)
        {
        }
        return buf.toString();
    }
    
    @Override
    public void replace(ReferenceInfo origId, ReferenceInfo newId)
    {
        super.replace(origId, newId);
        if(restrictions != null && restrictions.containsKey(origId.getId()))
        {
            final List<String> restrictionsOfRemoved = restrictions.remove(origId.getId());
            final ArrayList<String> allRestrinctions = new ArrayList<>(restrictionsOfRemoved);
            if(restrictions.containsKey(newId.getId()))
            {
                final List<String> restrictionsOfNew = restrictions.get(newId.getId());
                allRestrinctions.addAll(restrictionsOfNew);
            }
            restrictions.put(newId.getId(), allRestrinctions);
        }
    }


}








