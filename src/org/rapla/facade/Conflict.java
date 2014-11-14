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
package org.rapla.facade;

import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;

import org.rapla.entities.Entity;
import org.rapla.entities.Named;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;

/**
 * A conflict is the allocation of the same resource at the same time by different
 * reservations. There's one conflict for each resource and each overlapping of
 * two allocating appointments. 
 */

public interface Conflict extends Named, Entity<Conflict>
{
    static public final RaplaType<Conflict> TYPE = new RaplaType<Conflict>(Conflict.class,"conflict");
    /** @return the allocatable, allocated for the same time by two different reservations. */
    public Allocatable getAllocatable();
//    /** @return the first Reservation, that is involved in the conflict.*/
//    public Reservation getReservation1();
    /** The appointment of the first reservation, that causes the conflict. */
    public String getAppointment1();
//    /** @return the second Reservation, that is involved in the conflict.*/
//    public Reservation getReservation2();
//    /** @return The User, who created the second Reservation.*/
//    public User getUser2();
    /** The appointment of the second reservation, that causes the conflict. */
    public String getAppointment2();
    String getReservation1();
    String getReservation2();
    
    String getAllocatableId();
    
    String getReservation1Name();
	
    String getReservation2Name();

    
    ///** Find the first occurance of a conflict in the specified interval or null when not in intervall*/
    //public Date getFirstConflictDate(final Date  fromDate, final Date toDate);
    
    //public boolean canModify(User user);

    public boolean isOwner( User user);
    
    public static final Conflict[] CONFLICT_ARRAY= new Conflict[] {};
    
    public class Util
    {

		public static Collection<Allocatable> getAllocatables(
				Collection<Conflict> conflictsSelected) {
			LinkedHashSet<Allocatable> allocatables = new LinkedHashSet<Allocatable>();
		    for ( Conflict conflict: conflictsSelected)
		    {
		    	allocatables.add(conflict.getAllocatable());
		    }
		    return allocatables;
		}

//		static public List<Reservation> getReservations(Collection<Conflict> conflicts) {
//			Collection<Reservation> reservations = new LinkedHashSet<Reservation>();
//			for (Conflict conflict:conflicts)
//			{
//				reservations.add(conflict.getReservation1());
//				reservations.add(conflict.getReservation2());
//		
//			}
//			return new ArrayList<Reservation>( reservations);
//		}
//		
    	
    }
    
    boolean hasAppointment(Appointment appointment);
    
    //boolean endsBefore(Date date);
    
    Date getStartDate();
   
    boolean isAppointment1Enabled();
    
    boolean isAppointment2Enabled();
    
    boolean isAppointment1Editable();
    
    boolean isAppointment2Editable();
    
    boolean checkEnabled();
}











