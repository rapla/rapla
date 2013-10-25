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
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.rapla.components.util.DateTools;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.storage.RefEntity;
import org.rapla.entities.storage.internal.ReferenceHandler;
import org.rapla.entities.storage.internal.SimpleEntity;
import org.rapla.facade.Conflict;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaException;
import org.rapla.storage.LocalCache;

/**
 * A conflict is the allocation of the same resource at the same time by different
 * reservations. There's one conflict for each resource and each overlapping of
 * two allocations. So if there are 3 reservations that allocate the same 2 resources
 * on 2 days of the week, then we got ( 3 * 2 ) *  2 * 2 = 24 conflicts. Thats
 * 3 reservations, each conflicting with two other 2 reservations on 2 days with 2 resources.
 *
 * @author Christopher Kohlhaas
 */

public class ConflictImpl extends SimpleEntity<Conflict> implements Conflict
{
    public ConflictImpl(
                    Allocatable allocatable,
                    Appointment app1,
                    Appointment app2)
    {
    	RefEntity<?> allocEntity = (RefEntity<?>) allocatable;
    	RefEntity<?> app1Entity = (RefEntity<?>) app1;
    	RefEntity<?> app2entity = (RefEntity<?>) app2;
		ReferenceHandler referenceHandler = getReferenceHandler();
		referenceHandler.put("allocatable", allocEntity);
		referenceHandler.put("appointment1", app1Entity);
		referenceHandler.put("appointment2", app2entity);
		setId( createId());
    }

    public Comparable createId()
    {
    	ReferenceHandler referenceHandler = getReferenceHandler();
    	StringBuilder buf = new StringBuilder();
    	buf.append(referenceHandler.getId("allocatable"));
    	buf.append(";");
     	buf.append(referenceHandler.getId("appointment1"));
     	buf.append(";");
     	buf.append(referenceHandler.getId("appointment2"));
     	return buf.toString();
    }
    public ConflictImpl() {
		// TODO Auto-generated constructor stub
	}
    
	public ConflictImpl(String id) throws RaplaException {
		String[] split = id.split(";");
		ReferenceHandler referenceHandler = getReferenceHandler();
		referenceHandler.putId("allocatable", LocalCache.getId(Allocatable.TYPE,split[0]));
		referenceHandler.putId("appointment1", LocalCache.getId(Appointment.TYPE,split[1]));
		referenceHandler.putId("appointment2", LocalCache.getId(Appointment.TYPE,split[2]));
		setId( id);
	}

	/** @return the first Reservation, that is involed in the conflict.*/
    public Reservation getReservation1() 
    { 
    	Appointment appointment1 = getAppointment1();
		if ( appointment1 == null)
		{
			throw new IllegalStateException("Appointment 1 is null resolve not called");
		}
    	return appointment1.getReservation(); 
    }
    /** The appointment of the first reservation, that causes the conflict. */
    public Appointment getAppointment1() 
    { 
    	return (Appointment)getReferenceHandler().get("appointment1");
    }
    /** @return the allocatable, allocated for the same time by two different reservations. */
    public Allocatable getAllocatable() 
    { 
    	return (Allocatable)getReferenceHandler().get("allocatable"); 
    }
    /** @return the second Reservation, that is involed in the conflict.*/
    public Reservation getReservation2() 
    { 
    	Appointment appointment2 = getAppointment2();
    	if ( appointment2 == null)
		{
			throw new IllegalStateException("Appointment 2 is null resolve not called");
		}
		return appointment2.getReservation(); 
    }
    /** @return The User, who created the second Reservation.*/
    public User getUser2() 
    { 
    	return getReservation2().getOwner(); 
    }
    /** The appointment of the second reservation, that causes the conflict. */
    public Appointment getAppointment2() 
    { 
    	return (Appointment)getReferenceHandler().get("appointment2");
    }

    public static final ConflictImpl[] CONFLICT_ARRAY= new ConflictImpl[] {};

    /**
     * @see org.rapla.entities.Named#getName(java.util.Locale)
     */
    public String getName(Locale locale) {
        return getReservation1().getName( locale );
    }

    public boolean canModify(User user) {
		Reservation reservation = getReservation1();
		Reservation overlappingReservation = getReservation2();
		Allocatable allocatable = getAllocatable();
		boolean canModifiy = user == null || user.isAdmin() || allocatable.canRead( user ) &&(RaplaComponent.canModify(reservation, user) || RaplaComponent.canModify(overlappingReservation, user));
		return canModifiy;
	}
    private boolean contains(Appointment appointment) {
        if ( appointment == null)
            return false;
        
        Appointment app1 = getAppointment1();
        Appointment app2 = getAppointment2();
		if  ( app1 != null && app1.equals( appointment))
            return true;
        if  ( app2 != null && app2.equals( appointment))
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
        buf.append( "'" );
        buf.append( conflict.getReservation1() );
        buf.append( "'" );
        buf.append( "with");
        buf.append( " '" );
        buf.append( conflict.getReservation2() );
        buf.append( "' " );
        buf.append( " owner ");
        User user = conflict.getUser2();
		if ( user != null)
		{
			buf.append( user.getUsername());
		}
        return buf.toString();
    }
    
    public Date getFirstConflictDate(final Date  fromDate, Date toDate) {
        Appointment a1  =getAppointment1();
        Appointment a2  =getAppointment2();
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
	public static void addConflicts(Collection<Conflict> conflictList, Appointment appointment1, Appointment appointment2,   Allocatable allocatable,Date today)
	{
		// Don't add conflicts, when in the past
		Date maxEnd1 = appointment1.getMaxEnd();
		Date maxEnd2 = appointment2.getMaxEnd();
		if (maxEnd1 != null && maxEnd1.before( today))
		{
			return;
		}
		if (maxEnd2 != null && maxEnd2.before( today))
		{
			return;
		}
		if (appointment1.equals(appointment2))
			return;
		if (RaplaComponent.isTemplate( appointment1))
		{
			return;
		}
		if (RaplaComponent.isTemplate( appointment2))
		{
			return;
		}
	    {
	        final ConflictImpl conflict = new ConflictImpl(allocatable,appointment1, appointment2 );
	        
	        // Rapla 1.4: Don't add conflicts twice
	        if (!contains(conflict, conflictList) )
	        {
	            conflictList.add(conflict);
	        }
	    }
	}

	public boolean hasAppointment(Appointment appointment) 
	{
		boolean result = getAppointment1().equals( appointment) || getAppointment2().equals( appointment);
		return result;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void copy(Conflict obj) {
		super.copy((SimpleEntity<Conflict>) obj);
	}
	
	@Override
	public Conflict deepClone() 
	{
		ConflictImpl clone = new ConflictImpl();
		super.deepClone( clone);
		return clone;
	}
	
}










