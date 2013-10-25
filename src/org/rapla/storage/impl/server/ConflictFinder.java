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
package org.rapla.storage.impl.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.rapla.components.util.DateTools;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.AppointmentBlockEndComparator;
import org.rapla.entities.domain.AppointmentBlockStartComparator;
import org.rapla.entities.domain.AppointmentStartComparator;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.internal.AppointmentImpl;
import org.rapla.facade.Conflict;
import org.rapla.facade.internal.ConflictImpl;
import org.rapla.storage.UpdateResult;

public class ConflictFinder {
	AllocationMap  appointmentMap;
    Map<Allocatable,Set<Conflict>> conflictMap;

    public ConflictFinder( AllocationMap  appointmentMap, Date today)  {
    	this.appointmentMap = appointmentMap;
    	conflictMap = new HashMap<Allocatable, Set<Conflict>>();
        for (Allocatable allocatable:appointmentMap.getAllocatables())
		{
			updateConflicts(allocatable, null, today);
		}
	}
	
	private SortedSet<Appointment> getAndCreateListId(Map<Allocatable,SortedSet<Appointment>> appointmentMap,Allocatable alloc) {
		SortedSet<Appointment> set = appointmentMap.get( alloc);
		if ( set == null)
		{
			set = new TreeSet<Appointment>();
			appointmentMap.put(alloc, set);
		}
		return set;
	}
       

    public static class AllocationChange
    {
    	public SortedSet<Appointment> toChange =  new TreeSet<Appointment>(new AppointmentStartComparator());
    	public SortedSet<Appointment> toRemove=  new TreeSet<Appointment>(new AppointmentStartComparator());
    	
    	public String toString()
    	{
    		return "toChange="+toChange.toString() + ";toRemove=" + toRemove.toString();
    	}
    }
	
    private void updateConflicts(Allocatable allocatable,AllocationChange change, Date today) 
    {
		SortedSet<Appointment> allAppointments = appointmentMap.getAppointments(allocatable);
		SortedSet<Appointment> changedAppointments;
    	SortedSet<Appointment> removedAppointments;
    	if ( change == null)
		{
			changedAppointments = allAppointments;
	    	removedAppointments = new TreeSet<Appointment>();

		}
		else
		{
			changedAppointments = change.toChange;
	    	removedAppointments = change.toRemove;
		}
		
		Set<Conflict> conflictList = conflictMap.get(allocatable);
		if (!conflictMap.containsKey(allocatable))
		{
			conflictList = new LinkedHashSet<Conflict>();
			conflictMap.put( allocatable, conflictList);
		}
		removeConflicts(conflictList, removedAppointments);
		removeConflicts(conflictList, changedAppointments);
		
		{
			SortedSet<AppointmentBlock> allAppointmentBlocks = createBlocks(allAppointments, new AppointmentBlockEndComparator());
			SortedSet<AppointmentBlock> appointmentBlocks = createBlocks(changedAppointments, new AppointmentBlockStartComparator());
            
			
			// Check the conflicts for each time block
			for (AppointmentBlock appBlock:appointmentBlocks)
			{
				final Appointment appointment1 = appBlock.getAppointment();
				
                long start = appBlock.getStart();
				/*
				 * Shrink the set of all time blocks down to those with a start date which is
				 * later than or equal to the start date of the block
				 */
				AppointmentBlock compareBlock = new AppointmentBlock(start, start, appointment1,false);
				final SortedSet<AppointmentBlock> tailSet = allAppointmentBlocks.tailSet(compareBlock);
            	
				// Check all time blocks which start after or at the same time as the block which is being checked
				for (AppointmentBlock appBlock2:tailSet)
				{
					// If the start date of the compared block is after the end date of the block, quit the loop
					if (appBlock2.getStart() > appBlock.getEnd())
					{
						break;
					}
					// Check if the corresponding appointments of both blocks overlap each other
                    final Appointment appointment2 = appBlock2.getAppointment();
                    if (!appointment2.equals( appointment1) && appointment2.overlaps(appointment1))
					{
						// Add appointments to conflict list
                    	ConflictImpl.addConflicts(conflictList, appointment1, appointment2,  allocatable, today);
					}
				}
			}
		}
		
    }

	private void removeConflicts(Set<Conflict> conflictList,
			Set<Appointment> list) {
		for ( Iterator<Conflict> it = conflictList.iterator();it.hasNext();)
		{
			Conflict conflict = it.next();
			Appointment appointment1 = conflict.getAppointment1();
			Appointment appointment2 = conflict.getAppointment2();
			if ( list.contains( appointment1) || list.contains( appointment2))
			{
				it.remove();
			}
		}
	}

	private SortedSet<AppointmentBlock> createBlocks(
			SortedSet<Appointment> appointmentSet,final Comparator<AppointmentBlock> comparator) {
		  // overlaps will be checked two  260 weeks (5 years) from now on
        long maxCheck = System.currentTimeMillis() + DateTools.MILLISECONDS_PER_WEEK * 260;
      
		
		// Create a new set of time blocks, ordered by their start dates
		SortedSet<AppointmentBlock> allAppointmentBlocks = new TreeSet<AppointmentBlock>(comparator);

		if ( appointmentSet.isEmpty())
		{
			return allAppointmentBlocks;
		}
		//Appointment last = appointmentSet.last();
		
		// Get all time blocks of all appointments
		for (Appointment appointment:appointmentSet)
		{
			// Get the end date of the appointment (if repeating, end date of last occurence)
			Date maxEnd = appointment.getMaxEnd();
			
			// Check if the appointment is repeating forever
			 if ( maxEnd == null || maxEnd.getTime() > maxCheck)
			 {
				 // If the repeating has no end, set the end to the start of the last appointment in the set + 100 weeks (~2 years)
				 maxEnd = new Date(maxCheck); 
			 }
			/*
			 * If the appointment has a repeating, get all single time blocks of it. If it is no
			 * repeating, this will just create one block, which is equal to the appointment
			 * itself.
			 */
			appointment.createBlocks(appointment.getStart(), DateTools.fillDate(maxEnd), allAppointmentBlocks);
		}
		return allAppointmentBlocks;
	}


	/**
	 * Determines all conflicts which occur after a given start date.
	 * if user is passed then only returns conflicts the user can modify
	 * 
	 * @param allocatables 
	 */
	public Collection<Conflict> getConflicts( User user) 
	{
		Collection<Conflict> conflictList = new HashSet<Conflict>();
		for ( Allocatable allocatable: conflictMap.keySet())
		{
			Set<Conflict> set = conflictMap.get( allocatable);
			if ( allocatable.isHoldBackConflicts())
			{
				continue;
			}
			if ( set != null)
			{
				for ( Conflict conflict: set)
				{
					if (conflict.canModify(user))
					{
						conflictList.add(conflict);
					}
				}
			}
		}
		return conflictList;
	}

	public Map<Allocatable, Map<Appointment,Collection<Appointment>>> getAllocatableBindings(Collection<Allocatable> allocatables,Collection<Appointment> appointments, Collection<Reservation> ignoreList, boolean onlyFirstConflictingAppointment) {
		Map<Allocatable, Map<Appointment,Collection<Appointment>>> map = new HashMap<Allocatable, Map<Appointment,Collection<Appointment>>>();
        for ( Allocatable allocatable:allocatables)
        {
			if ( allocatable.isHoldBackConflicts())
			{
				continue;
			}
			SortedSet<Appointment> appointmentSet = appointmentMap.getAppointments( allocatable);
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

	public void updateConflicts(Map<Allocatable, AllocationChange> toUpdate,UpdateResult evt, Date today)
	{
    	Set<Conflict> added = new HashSet<Conflict>();
    	// this will recalculate the conflicts for that resource and the changed appointments
    	for ( Map.Entry<Allocatable, AllocationChange> entry:toUpdate.entrySet())
    	{
    		Allocatable allocatable = entry.getKey();
    		AllocationChange changedAppointments = entry.getValue();
    		if (!conflictMap.containsKey(allocatable))
    		{
    			LinkedHashSet<Conflict> conflictList = new LinkedHashSet<Conflict>();
    			conflictMap.put( allocatable, conflictList);
    		}
    		Set<Conflict> conflictListBefore = new HashSet<Conflict>( conflictMap.get(allocatable));
			updateConflicts( allocatable, changedAppointments, today);
			Set<Conflict> conflictListAfter = new HashSet<Conflict>( conflictMap.get(allocatable));
			User user = evt.getUser();
		
			for ( Conflict conflict: conflictListBefore)
			{
				boolean isResolved = !conflictListAfter.contains(conflict);
				if  ( isResolved && conflict.canModify(user))
				{
					evt.addOperation( new UpdateResult.Remove(conflict));
				}
			}
			for ( Conflict conflict: conflictListAfter)
			{
				boolean isNew = !conflictListBefore.contains(conflict);
				if  ( isNew && conflict.canModify(user))
				{
					evt.addOperation( new UpdateResult.Add(conflict));
					added.add( conflict);
				}
			}
    	}
    	
    	// so now we have the new conflicts, but what if a reservation or appointment changed without affecting the allocation but still 
    	// the conflict is still the same but the name could change, so we must somehow indicate the clients displaying that conflict, that they need to refresh the name,
    	// because the involving reservations are not automatically pushed to the client
    	
    	// first we create a list with all changed appointments. Notice if a reservation is changed all the appointments will change to
    	Map<Allocatable, SortedSet<Appointment>> appointmentUpdateMap = new LinkedHashMap<Allocatable, SortedSet<Appointment>>();
    	for (@SuppressWarnings("rawtypes") RaplaObject obj:evt.getChanged())
    	{
    		if ( obj.getRaplaType() ==  Appointment.TYPE)
    		{
    			Appointment app = ((Appointment) obj);
    			for ( Allocatable alloc:app.getReservation().getAllocatablesFor( app))
    			{
    				Collection<Appointment> list = getAndCreateListId(appointmentUpdateMap,alloc);
    				list.add( app);
    			}
    		}
    	}
    	// then we create a map and look for any conflict that has changed appointment. This could still contain old appointment references  
    	Map<Conflict,Conflict> toUpdateConflicts = new LinkedHashMap<Conflict, Conflict>();
    	for ( Allocatable alloc: appointmentUpdateMap.keySet())
    	{
    		SortedSet<Appointment> changedAppointments = appointmentUpdateMap.get( alloc);
    		Set<Conflict> conflicts = conflictMap.get( alloc);
    		if ( conflicts != null)
    		{
	    		for ( Conflict conflict:conflicts)
	    		{
	    			Appointment appointment1 = conflict.getAppointment1();
	    			Appointment appointment2 = conflict.getAppointment2();
	    			boolean contains1 = changedAppointments.contains( appointment1);
					boolean contains2 = changedAppointments.contains( appointment2);
					if ( contains1 || contains2)
	    			{
	    				Conflict oldConflict = conflict;
	    				Appointment newAppointment1 = contains1 ? getAppointment(changedAppointments, appointment1) : appointment1;
						Appointment newAppointment2 = contains2 ? getAppointment(changedAppointments, appointment2): appointment2;
						Conflict newConflict = new ConflictImpl( alloc, newAppointment1, newAppointment2);
	    				toUpdateConflicts.put( oldConflict, newConflict);
	    			}
	    		}
    		}
    	}
    	
    	// we update the conflict with the new appointment references
    	ArrayList<Conflict> updateList = new ArrayList<Conflict>( toUpdateConflicts.keySet());
    	for ( Conflict oldConflict:updateList)
    	{
    		Conflict newConflict = toUpdateConflicts.get( oldConflict);
    		Set<Conflict> conflicts = conflictMap.get( oldConflict.getAllocatable());
    		conflicts.remove( oldConflict);
    		conflicts.add( newConflict);
    		// we add a change operation 
    		// TODO Note that this list also contains the NEW conflicts, but the UpdateResult.NEW could still contain the old conflicts
    		//if ( added.contains( oldConflict))
    		{
    			evt.addOperation( new UpdateResult.Change( newConflict, oldConflict));
    		}
    	}

	}

	private Appointment getAppointment(
			SortedSet<Appointment> changedAppointments, Appointment appointment) {
		Appointment foundAppointment = changedAppointments.tailSet( appointment).iterator().next();
		return foundAppointment;
	}

}
