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
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.rapla.components.util.DateTools;
import org.rapla.entities.Entity;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.internal.AllocatableImpl;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.internal.SimpleEntity;
import org.rapla.facade.Conflict;
import org.rapla.facade.RaplaComponent;

/**
 * A conflict is the allocation of the same resource at the same time by different
 * reservations. There's one conflict for each resource and each overlapping of
 * two allocations. So if there are 3 reservations that allocate the same 2 resources
 * on 2 days of the week, then we got ( 3 * 2 ) *  2 * 2 = 24 conflicts. Thats
 * 3 reservations, each conflicting with two other 2 reservations on 2 days with 2 resources.
 *
 * @author Christopher Kohlhaas
 */

public class ConflictImpl extends SimpleEntity implements Conflict
{
	Date startDate;
	String reservation1Name;
	String reservation2Name;
	ConflictImpl() {
	}
	    
	public ConflictImpl(
                    Allocatable allocatable,
                    Appointment app1,
                    Appointment app2,
                    Date today
                    )
    {
		putEntity("allocatable", allocatable);
		startDate = getStartDate_(today, app1, app2);
		putEntity("appointment1", app1);
		putEntity("appointment2", app2);
		Reservation reservation1 = app1.getReservation();
		Reservation reservation2 = app2.getReservation();
		putEntity("reservation1", reservation1);
		putEntity("reservation2", reservation2);
		putEntity("owner1", reservation1.getOwner());
		putEntity("owner2", reservation2.getOwner());
		this.reservation1Name = reservation1.getName(Locale.getDefault());
		this.reservation2Name = reservation2.getName(Locale.getDefault());
		setResolver( ((AllocatableImpl)allocatable).getResolver());
		setId( createId());
    }
	
	public String getReservation1Name() {
		return reservation1Name;
	}
	
	public String getReservation2Name() {
		return reservation2Name;
	}
	
	public Date getStartDate()
	{
		return startDate;
	}
	
	@Override
	public Iterable<String> getReferencedIds() {
		return Collections.emptyList();
	}
   
	private Date getStartDate_(Date today,Appointment app1, Appointment app2) {
		Date fromDate = today;
		Date start1 = app1.getStart();
		if ( start1.before( fromDate))
		{	
			fromDate = start1; 
		}
		Date start2 = app2.getStart();
		if ( start2.before( fromDate))
		{
			fromDate = start2; 
		}
		Date toDate = DateTools.addDays( today, 365 * 10);
		Date date = getFirstConflictDate(fromDate, toDate, app1, app2);
		return date;
	}

    public String createId()
    {
    	StringBuilder buf = new StringBuilder();
    	buf.append(getId("allocatable"));
    	buf.append(";");
     	buf.append(getId("appointment1"));
     	buf.append(";");
     	buf.append(getId("appointment2"));
     	return buf.toString();
    }

  
    
//	public ConflictImpl(String id) throws RaplaException {
//		String[] split = id.split(";");
//		ReferenceHandler referenceHandler = getReferenceHandler();
//		referenceHandler.putId("allocatable", LocalCache.getId(Allocatable.TYPE,split[0]));
//		referenceHandler.putId("appointment1", LocalCache.getId(Appointment.TYPE,split[1]));
//		referenceHandler.putId("appointment2", LocalCache.getId(Appointment.TYPE,split[2]));
//		setId( id);
//	}
	
//	public static boolean isConflictId(String id) {
//		if ( id == null)
//		{
//			return false;
//		}
//		String[] split = id.split(";");
//		if ( split.length != 3)
//		{
//			return false;
//		}
//		try {
//			LocalCache.getId(Allocatable.TYPE,split[0]);
//			LocalCache.getId(Appointment.TYPE,split[1]);
//			LocalCache.getId(Appointment.TYPE,split[2]);
//		} catch (RaplaException e) {
//			return false;
//		}
//		return true;
//	}

	/** @return the first Reservation, that is involed in the conflict.*/
//    public Reservation getReservation1() 
//    { 
//    	Appointment appointment1 = getAppointment1();
//		if ( appointment1 == null)
//		{
//			throw new IllegalStateException("Appointment 1 is null resolve not called");
//		}
//    	return appointment1.getReservation(); 
//    }
	
    /** The appointment of the first reservation, that causes the conflict. */
    public String getAppointment1() 
    { 
    	return getId("appointment1");
    }
    
    public String getReservation1() 
    { 
    	return getId("reservation1");
    }
    
    public String getReservation2() 
    { 
    	return getId("reservation2");
    }


    /** @return the allocatable, allocated for the same time by two different reservations. */
    public Allocatable getAllocatable() 
    { 
    	return (Allocatable)getEntity("allocatable"); 
    }
//    /** @return the second Reservation, that is involed in the conflict.*/
//    public Reservation getReservation2() 
//    { 
//    	Appointment appointment2 = getAppointment2();
//    	if ( appointment2 == null)
//		{
//			throw new IllegalStateException("Appointment 2 is null resolve not called");
//		}
//		return appointment2.getReservation(); 
//    }
//    /** @return The User, who created the second Reservation.*/
//    public User getUser2() 
//    { 
//    	return getReservation2().getOwner(); 
//    }
    
    /** The appointment of the second reservation, that causes the conflict. */
    public String getAppointment2() 
    { 
    	return getId("appointment2");
    }

    public static final ConflictImpl[] CONFLICT_ARRAY= new ConflictImpl[] {};

    /**
     * @see org.rapla.entities.Named#getName(java.util.Locale)
     */
    public String getName(Locale locale) {
        return getAllocatable().getName( locale );
    }

   public boolean isOwner( User user)
	{
	    User owner1 = getOwner1();
		User owner2 = getOwner2();
		if (user != null && !user.equals(owner1) && !user.equals(owner2)) {
	        return false;
	    }
		return true;
	}
   
   
   
// public Date getFirstConflictDate(final Date  fromDate, Date toDate) {
// Appointment a1  =getAppointment1();
// Appointment a2  =getAppointment2();
// return getFirstConflictDate(fromDate, toDate, a1, a2);
//}
    
    public User getOwner1() {
    	return (User) getEntity("owner1");
    }

    public User getOwner2() {
    	return (User) getEntity("owner2");
    }

	private boolean contains(String appointmentId) {
        if ( appointmentId == null)
            return false;
        
        String app1 = getAppointment1();
        String app2 = getAppointment2();
		if  ( app1 != null && app1.equals( appointmentId))
            return true;
        if  ( app2 != null && app2.equals( appointmentId))
            return true;
        return false;
    }

    static public boolean equals( ConflictImpl firstConflict,Conflict secondConflict) {
        if  (secondConflict == null  )
            return false;

        if (!firstConflict.contains( secondConflict.getAppointment1()))
            return false;
        if (!firstConflict.contains( secondConflict.getAppointment2()))
            return false;
        Allocatable allocatable = firstConflict.getAllocatable();
		if ( allocatable != null && !allocatable.equals( secondConflict.getAllocatable())) {
            return false;
        }
        return true;
    }
    
	private static boolean contains(ConflictImpl conflict,
			Collection<Conflict> conflictList) {
		for ( Conflict conf:conflictList)
		{
			if ( equals(conflict,conf))
			{
				return true;
			}
		}
		return false;
	}
    
    
    public RaplaType<Conflict> getRaplaType()
    {
        return Conflict.TYPE;
    }


    public String toString()
    {
        Conflict conflict =  this;
        StringBuffer buf = new StringBuffer();
        buf.append( "Conflict for");
        buf.append( conflict.getAllocatable());
        buf.append( " " );
        buf.append( conflict.getAppointment1());
        buf.append( " " );
        buf.append( "with");
        buf.append( " " );
        buf.append( conflict.getAppointment2());
        buf.append( " " );
        return buf.toString();
    }
    
	static public Date getFirstConflictDate(final Date fromDate, Date toDate,
			Appointment a1, Appointment a2) {
		Date minEnd =  a1.getMaxEnd();
        if ( a1.getMaxEnd() != null && a2.getMaxEnd() != null && a2.getMaxEnd().before( a1.getMaxEnd())) {
            minEnd = a2.getMaxEnd();
        }
        Date maxStart = a1.getStart();
        if ( a2.getStart().after( a1.getStart())) {
            maxStart = a2.getStart();
        }
        if ( fromDate != null && maxStart.before( fromDate))
        {
            maxStart = fromDate;
        }
        // look for  10 years in the future (520 weeks)
        if ( minEnd == null)
            minEnd = new Date(maxStart.getTime() + DateTools.MILLISECONDS_PER_DAY * 365 * 10 );
       
        if ( toDate != null && minEnd.after( toDate))
        {
            minEnd = toDate;
        }
        
        List<AppointmentBlock> listA = new ArrayList<AppointmentBlock>();
        a1.createBlocks(maxStart, minEnd, listA );
        List<AppointmentBlock> listB = new ArrayList<AppointmentBlock>();
        a2.createBlocks( maxStart, minEnd, listB );
        for ( int i=0, j=0;i<listA.size() && j<listB.size();) {
            long s1 = listA.get( i).getStart();
            long s2 = listB.get( j).getStart();
            long e1 = listA.get( i).getEnd();
            long e2 = listB.get( j).getEnd();
            if ( s1< e2 && s2 < e1) {
                return new Date( Math.max( s1, s2));
            }
            if ( s1> s2)
               j++;
            else
               i++;
        }
        return null;
    }
	public static void addConflicts(Collection<Conflict> conflictList, Allocatable allocatable,Appointment appointment1, Appointment appointment2,Date today)
	{
        final ConflictImpl conflict = new ConflictImpl(allocatable,appointment1, appointment2, today);
        // Rapla 1.4: Don't add conflicts twice
        if (!contains(conflict, conflictList) )
        {
            conflictList.add(conflict);
        }
	}
	
	public static boolean endsBefore(Appointment appointment1,Appointment appointment2, Date date) {
		Date maxEnd1 = appointment1.getMaxEnd();
		Date maxEnd2 = appointment2.getMaxEnd();
		if (maxEnd1 != null && maxEnd1.before( date))
		{
			return true;
		}
		if (maxEnd2 != null && maxEnd2.before( date))
		{
			return true;
		}
		return false;
	}
	
	public static boolean isConflict(Appointment appointment1,Appointment appointment2, Date today) {
		// Don't add conflicts, when in the past
		if (endsBefore(appointment1, appointment2, today))
		{
			return false;
		}
		if (appointment1.equals(appointment2))
			return false;
		if (RaplaComponent.isTemplate( appointment1))
		{
			return false;
		}
		if (RaplaComponent.isTemplate( appointment2))
		{
			return false;
		}
		boolean conflictTypes = checkForConflictTypes( appointment1, appointment2);
		if ( !conflictTypes )
		{
			return false;
		}
		Date maxEnd1 = appointment1.getMaxEnd();
		Date maxEnd2 = appointment2.getMaxEnd();
		Date checkEnd = maxEnd1;
        if ( maxEnd2 != null && checkEnd !=null && maxEnd2.before( checkEnd) )
        {
        	checkEnd = maxEnd2;
        }
        if (checkEnd != null && ConflictImpl.getFirstConflictDate(today, checkEnd, appointment1, appointment2) == null)
        {
        	return false;
        }
        return true;
	}

	private static boolean checkForConflictTypes(Appointment a1,	Appointment a2) {
		String annotation1 = getConflictAnnotation(a1);
		String annotation2 = getConflictAnnotation(a2);
		if ( isNoConflicts( annotation1 ) || isNoConflicts ( annotation2))
		{
			return false;
		}
		if ( annotation1 != null )
		{
			if ( annotation1.equals(DynamicTypeAnnotations.VALUE_CONFLICTS_WITH_OTHER_TYPES))
			{
				if  (annotation2 != null)
				{
					DynamicType type2 = a2.getReservation().getClassification().getType();
					DynamicType type1 = a1.getReservation().getClassification().getType();
					if (type1.equals(type2))
					{
						return false;
					}
				}
			}
		}
		return true;
	}

	private static boolean isNoConflicts(String annotation) {
		if ( annotation != null && annotation.equals(DynamicTypeAnnotations.VALUE_CONFLICTS_NONE))
		{
			return true;
		}
		return false;
	}

	private static String getConflictAnnotation(Appointment a) {
		Reservation reservation = a.getReservation();
		if ( reservation == null)
		{
			return null;
		}
		return reservation.getClassification().getType().getAnnotation(DynamicTypeAnnotations.KEY_CONFLICTS);
	}

	public boolean hasAppointment(Appointment appointment) 
	{
		boolean result = getAppointment1().equals( appointment) || getAppointment2().equals( appointment);
		return result;
	}

	@Override
	public Conflict clone() 
	{
		ConflictImpl clone = new ConflictImpl();
		super.deepClone( clone);
		return clone;
	}

	static public boolean canModify(Conflict conflict,User user, EntityResolver resolver) {
		Allocatable allocatable = conflict.getAllocatable();
		if (user == null || user.isAdmin())
		{
			return true;
		}
		if (allocatable.canRead( user ))
		{
			Entity reservation = resolver.tryResolve(conflict.getReservation1());
			if ( reservation == null )
			{
				// reservation will be deleted, and conflict also so return 
				return false;
			}
			if (RaplaComponent.canModify(reservation, user) )
			{
				return true;
			}
			Entity overlappingReservation = resolver.tryResolve(conflict.getReservation2());
			if ( overlappingReservation == null )
			{
				return false;
			}
			if (RaplaComponent.canModify(overlappingReservation, user))
			{
				return true;
			}
		}
		return false;
	}

	public static Map<Appointment, Set<Appointment>> getMap(Collection<Conflict> selectedConflicts,List<Reservation> reservations) 
	{
		Map<Appointment, Set<Appointment>> result = new HashMap<Appointment,Set<Appointment>>();
		Map<String, Appointment> map = new HashMap<String,Appointment>();
		for ( Reservation reservation:reservations)
		{
			for (Appointment app:reservation.getAppointments())
			{
				map.put( app.getId(), app);
			}
		}
		for ( Conflict conflict:selectedConflicts)
		{
			Appointment app1 = map.get(conflict.getAppointment1());
			Appointment app2 = map.get(conflict.getAppointment2());
			add(result, app1, app2);
			add(result, app2, app1);
		}
		return result;
	}

	private static void add(Map<Appointment, Set<Appointment>> result,Appointment app1, Appointment app2) {
		Set<Appointment> set = result.get( app1);
		if ( set == null)
		{
			set = new HashSet<Appointment>();
			result.put(app1,set);
		}
		set.add( app2);
	}


	
}










