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
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import org.rapla.components.util.DateTools;
import org.rapla.entities.Entity;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.ResourceAnnotations;
import org.rapla.entities.domain.internal.AppointmentImpl;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.facade.Conflict;
import org.rapla.facade.RaplaComponent;
import org.rapla.facade.internal.ConflictImpl;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.rapla.storage.LocalCache;
import org.rapla.storage.UpdateResult;
import org.rapla.storage.UpdateResult.Change;

class ConflictFinder {
	AllocationMap  allocationMap;
    Map<Allocatable,Set<Conflict>> conflictMap;
    Logger logger;
    EntityResolver resolver;
    LocalCache cache;
    public ConflictFinder( AllocationMap  allocationMap, Date today, Logger logger, EntityResolver resolver, LocalCache cache)  {
    	this.logger = logger;
    	this.cache = cache;
    	this.allocationMap = allocationMap;
    	conflictMap = new HashMap<Allocatable, Set<Conflict>>();
    	long startTime = System.currentTimeMillis();
    	int conflictSize = 0;
        for (Allocatable allocatable:allocationMap.getAllocatables())
		{
        	Set<Conflict> newConflicts = calculateConflicts(allocatable, today);
        	conflictMap.put( allocatable, newConflicts);
        	conflictSize+= newConflicts.size();
		}
        logger.info("Conflict initialization found " + conflictSize + " conflicts and took " + (System.currentTimeMillis()- startTime) + "ms. " ); 
        this.resolver = resolver;
	}
    
    public Conflict findConflict(String id,Date date)
    {
        ConflictImpl conflict;
        try {
            conflict = new ConflictImpl(id, date);
        } catch (RaplaException e) {
            logger.error(e.getMessage(), e);
            return null;
        }
        conflict.setResolver( resolver);
        Allocatable allocatable = conflict.getAllocatable();
        Set<Conflict> set = conflictMap.get( allocatable);
        if ( set == null)
        {
            return null;
        }
        else
        {
            for ( Conflict c:set)
            {
                if ( c.getId().equals( id))
                {
                    return c;
                }
            }
        }
        return null;
    }
    
    public void setConflictEnabledState( String conflictId,Date date, boolean appointment1Enabled,boolean appointment2Enabled)
    {
        ConflictImpl conflict = (ConflictImpl)findConflict(conflictId, date);
        conflict.setAppointment1Enabled( appointment1Enabled);
        conflict.setAppointment2Enabled( appointment2Enabled);
    }
    
    private Set<Conflict> calculateConflicts(Allocatable allocatable,Date today ) 
    {
        if ( isConflictIgnored(allocatable))
        {
            return Collections.emptySet();
        }
        Set<Appointment> allAppointments = allocationMap.getAppointments(allocatable);
//        Set<Appointment> changedAppointments;
//        Set<Appointment> removedAppointments;
//        if ( change == null)
//        {
//            changedAppointments = allAppointments;
//            removedAppointments = new TreeSet<Appointment>();
//        }
//        else
//        {
//            changedAppointments = change.toChange;
//            removedAppointments = change.toRemove;
//        }
        if (allAppointments.isEmpty() /*|| changedAppointments.isEmpty()*/)
        {
            return Collections.emptySet();
        }
      //  Set<Conflict> conflictList =  new HashSet<Conflict>( );//conflictMap.get(allocatable);
//        {
//            Set<String> idList1 = getIds( removedAppointments);
//            Set<String> idList2 = getIds( changedAppointments);
//            for ( Conflict conflict:oldList)
//            {
//                if (endsBefore(conflict, today) || contains(conflict, idList1) || contains(conflict, idList2))
//                {
//                    continue;
//                }
//                conflictList.add( conflict );
//            }
//        }
        Set<Conflict> conflictList =   updateConflicts(allocatable, today, allAppointments);
        //updateConflictsOld(allocatable, today, allAppointments, changedAppointments, conflictList);
        if ( conflictList.isEmpty())
        {
            return Collections.emptySet();
        }
        return conflictList;
    }


//    private void updateConflictsOld(Allocatable allocatable, Date today, Set<Appointment> allAppointments, Set<Appointment> changedAppointments, Set<Conflict> conflictList) {
//        Set<String> foundConflictIds = new HashSet<String>();
//        //SortedSet<AppointmentBlock> allAppointmentBlocksSortedByStartDescending = null;//new TreeSet<AppointmentBlock>(new InverseComparator<AppointmentBlock>(new AppointmentBlockStartComparator())); 
//        SortedSet<AppointmentBlock> allAppointmentBlocks =new TreeSet<AppointmentBlock>( new AppointmentBlockEndComparator()); 
//        createBlocks(today,allAppointments,allAppointmentBlocks, null);
//        SortedSet<AppointmentBlock> appointmentBlocks =  new TreeSet<AppointmentBlock>(  new AppointmentBlockStartComparator());
//        createBlocks(today,changedAppointments,appointmentBlocks, null);
//        long startTime = 0;
//        if  ( appointmentBlocks.size() > 7000)
//        {
//            startTime = System.nanoTime();
//        }
//        // Check the conflicts for each time block
//        for (AppointmentBlock appBlock:appointmentBlocks)
//        {
//            final Appointment appointment1 = appBlock.getAppointment();
//            
//            long start = appBlock.getStart();
//            long end = appBlock.getEnd();
//            /*
//             * Shrink the set of all time blocks down to those with a start date which is
//             * later than or equal to the start date of the block
//             */
//            AppointmentBlock compareBlock = new AppointmentBlock(start, start, appointment1,false);
//            final SortedSet<AppointmentBlock> tailSet = allAppointmentBlocks.tailSet(compareBlock);
//            
//            // Check all time blocks which start after or at the same time as the block which is being checked
//            for (AppointmentBlock appBlock2:tailSet)
//            {
//                // If the start date of the compared block is after the end date of the block, skip the appointment
//                if (appBlock2.getStart() > end)
//                {
//                    break;
//                }
//                final Appointment appointment2 = appBlock2.getAppointment();
//                // we test that in the next step
//                if ( appBlock == appBlock2 || appBlock2.includes( appBlock) || appointment1.equals( appointment2) )
//                {
//                    continue;
//                }
//                // Check if the corresponding appointments of both blocks overlap each other
//                
//                if (!appointment2.equals( appointment1) && appointment2.overlaps(appointment1))
//                {
//                    String id = ConflictImpl.createId(allocatable.getId(), appointment1.getId(), appointment2.getId());
//                    if ( foundConflictIds.contains(id ))
//                    {
//                        continue;
//                    }
//                    // Add appointments to conflict list
//                    if (ConflictImpl.isConflictWithoutCheck(appointment1, appointment2, today))
//                    {
//                        final ConflictImpl conflict = new ConflictImpl(allocatable,appointment1, appointment2, today, id);
//                        conflictList.add(conflict);
//                        foundConflictIds.add( id);
//                    }                       
//                }
//            }
//
//            // we now need to check overlaps with appointments that start before and end after the appointment
//            //AppointmentBlock compareBlock2 = new AppointmentBlock(end, end, appointment1,false);
//            //SortedSet<AppointmentBlock> descending = allAppointmentBlocksSortedByStartDescending.tailSet(compareBlock);
//            for (AppointmentBlock appBlock2:tailSet)
//            {
//                final Appointment appointment2 = appBlock2.getAppointment();
//                if ( appBlock == appBlock2 || !appBlock2.includes( appBlock) || appointment2.equals( appointment1) )
//                {
//                    continue;
//                }
//                String id = ConflictImpl.createId(allocatable.getId(), appointment1.getId(), appointment2.getId());
//                if ( foundConflictIds.contains(id ))
//                {
//                    continue;
//                }
//                if (ConflictImpl.isConflictWithoutCheck(appointment1, appointment2, today))
//                {
//                    final ConflictImpl conflict = new ConflictImpl(allocatable,appointment1, appointment2, today, id);
//                    conflictList.add(conflict);
//                    foundConflictIds.add( id);
//                }                       
//            }
//        }
//        if ( startTime > 0 )
//        {
//            long time = System.nanoTime() - startTime;
//            System.out.println(  " Passed time for " + appointmentBlocks.size() + " blocks " + (time / 1000000.0) + "  ms ");
//        }
//    }
    
    private  Set<Conflict>  updateConflicts(Allocatable allocatable, Date today, Set<Appointment> allAppointments) {
        Collection<AppointmentBlock> allAppointmentBlocks =new LinkedList<AppointmentBlock>(); 
        createBlocks(today,allAppointments,allAppointmentBlocks, null);
//        Collection<AppointmentBlock> appointmentBlocks =  new LinkedList<AppointmentBlock>();
//        createBlocks(today,changedAppointments,appointmentBlocks, null);
//        long startTime = 0;
//        if  ( appointmentBlocks.size() > 7000)
//        {
//            startTime = System.nanoTime();
//        }
        return sweepLine(allocatable,today, allAppointmentBlocks);
//        if ( startTime > 0 )
//        {
//            long time = System.nanoTime() - startTime;
//            System.out.println(  " Passed time for " + appointmentBlocks.size() + " blocks " + (time / 1000000.0) + "  ms ");
//        }
    }


 // helper class for events in sweep line algorithm
    public static class Event implements Comparable<Event> {
        long time;
        AppointmentBlock interval;

        public Event(long time, AppointmentBlock interval) {
            this.time     = time;
            this.interval = interval;
        }

        public int compareTo(Event b) {
            Event a = this;
            if      (a.time < b.time) return -1;
            else if (a.time > b.time) return +1;
            else                      return  0; 
        }
    }


    // the sweep-line algorithm
    public static Set<Conflict> sweepLine(Allocatable allocatable, Date today, Collection<AppointmentBlock> intervals) {
        Set<Conflict> conflictList =  new HashSet<Conflict>( );//conflictMap.get(allocatable);
        Set<String> foundConflictIds = new HashSet<String>();
        // generate N random intervals

        // create events
        MinPQ<Event> pq = new MinPQ<Event>();
        for (AppointmentBlock block:intervals) {
            Event e1 = new Event(block.getStart(),  block);
            Event e2 = new Event(block.getEnd(), block);
            pq.insert(e1);
            pq.insert(e2);
        }

        // run sweep-line algorithm
        HashSet<AppointmentBlock> st = new HashSet<AppointmentBlock>();
        while (!pq.isEmpty()) {
            Event e = pq.delMin();
            long time = e.time;
            AppointmentBlock appBlock = e.interval;
            Appointment appointment1 = appBlock.getAppointment();

            // next event is the right endpoint of interval i
            if (time == appBlock.getEnd())
                st.remove(appBlock);

            // next event is the left endpoint of interval i
            else {
                for (AppointmentBlock appBlock2 : st) {
                    final Appointment appointment2 = appBlock2.getAppointment();
                    if ( appBlock == appBlock2 || appointment1.equals( appointment2) )
                    {
                        continue;
                    }
                    if ( appointment2.overlaps(appointment1))
                    {
                        String id = ConflictImpl.createId(allocatable.getId(), appointment1.getId(), appointment2.getId());
                        if ( foundConflictIds.contains(id ))
                        {
                            continue;
                        }
                        // Add appointments to conflict list
                        if (ConflictImpl.isConflictWithoutCheck(appointment1, appointment2, today))
                        {
                            final ConflictImpl conflict = new ConflictImpl(allocatable,appointment1, appointment2, today, id);
                            conflictList.add(conflict);
                            foundConflictIds.add( id);
//                            System.out.println("Conflict " + appointment1 + " and " + appointment2);
                        }                       
                    }

                }
                st.add(appBlock);
            }
        }
        return conflictList;

    }

    
//    private Map<AppointmentBlock,Integer> updateConflictsRandomTree(Allocatable allocatable, Date today, Set<Conflict> conflictList, Collection<AppointmentBlock> allAppointmentBlocks,
//            Collection<AppointmentBlock> appointmentBlocks) {
//        Map<AppointmentBlock,Integer> intersections = new LinkedHashMap();
//        Set<String> foundConflictIds = new HashSet<String>();
//
//        IntervalST intervalST = new IntervalST();
//        for ( AppointmentBlock block:allAppointmentBlocks)
//        {
//            intervalST.put( block);
//        }
//         
//        // Check the conflicts for each time block
//        for (AppointmentBlock appBlock:appointmentBlocks)
//        {
//            Appointment appointment1 = appBlock.getAppointment();
//            Collection<AppointmentBlock> intersecting = intervalST.searchAll(appBlock);
//            int count = 0;
//            for ( AppointmentBlock appBlock2:intersecting)
//            {
//                final Appointment appointment2 = appBlock2.getAppointment();
//                // we test that in the next step
//                if ( appBlock == appBlock2 || appointment1.equals( appointment2) )
//                {
//                    continue;
//                }
//                if ( appointment2.overlaps(appointment1))
//                {
//                    String id = ConflictImpl.createId(allocatable.getId(), appointment1.getId(), appointment2.getId());
//                    if ( foundConflictIds.contains(id ))
//                    {
//                        continue;
//                    }
//                    // Add appointments to conflict list
//                    if (ConflictImpl.isConflictWithoutCheck(appointment1, appointment2, today))
//                    {
//                        count++;
//                        final ConflictImpl conflict = new ConflictImpl(allocatable,appointment1, appointment2, today, id);
//                        conflictList.add(conflict);
//                        foundConflictIds.add( id);
//                    }                       
//                }
//            }
//            if ( count > 0)
//            {
//                intersections.put( appBlock, count);
//            }
//            
//        }
//        return intersections;
//       // System.out.println("Conflicts for Vicara " + foundConflictIds.size());
//
//    }

    
    public Set<String> getIds(Collection<? extends Entity> list) {
        if ( list.isEmpty())
        {
            return Collections.emptySet();
        }
        Set<String> idList = new HashSet<String>();
        for ( Entity entity:list)
        {
            idList.add( entity.getId());
        }
        return idList;
    }


	private boolean isConflictIgnored(Allocatable allocatable) 
	{
		String annotation = allocatable.getAnnotation(ResourceAnnotations.KEY_CONFLICT_CREATION);
		if ( annotation != null && annotation.equals(ResourceAnnotations.VALUE_CONFLICT_CREATION_IGNORE))
		{
			return true;
		}
		return false;
	}

//	private boolean contains(Conflict conflict, Set<String> idList)
//	{
//	    String appointment1 = conflict.getAppointment1();
//        String appointment2 = conflict.getAppointment2();
//        return( idList.contains( appointment1) || idList.contains( appointment2));
//	}
	
    private void createBlocks(Date today, Collection<Appointment> appointmentSet,  Collection<AppointmentBlock> allAppointmentBlocks, Set<AppointmentBlock> additionalSet) {
        // overlaps will be checked  260 weeks (5 years) from now on
		long maxCheck = System.currentTimeMillis() + DateTools.MILLISECONDS_PER_WEEK * 260;
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
            if ( maxEnd.before( today))
            {
                continue;
            }
   		
			if ( RaplaComponent.isTemplate(appointment.getReservation()))
			{
			    continue;
			}
			
			Reservation r1 = appointment.getReservation();
	        DynamicType type1 = r1 != null ? r1.getClassification().getType() : null;
	        String annotation1 = ConflictImpl.getConflictAnnotation( type1);
	        if ( ConflictImpl.isNoConflicts( annotation1 ) )
	        {
	            continue;
	        }
			/*
			 * If the appointment has a repeating, get all single time blocks of it. If it is no
			 * repeating, this will just create one block, which is equal to the appointment
			 * itself.
			 */
			Date start = appointment.getStart();
			if ( start.before( today))
			{
			    start = today;
			}
            ((AppointmentImpl)appointment).createBlocks(start, DateTools.fillDate(maxEnd), allAppointmentBlocks,additionalSet);
		}
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
			if ( set != null)
			{
				for ( Conflict conflict: set)
				{
					if (RaplaComponent.canModify(conflict,user,resolver))
					{
					    cache.fillConflictDisableInformation(user, conflict);
                        conflictList.add(conflict);
					}
				}
			}
		}
		return conflictList;
	}

    
	private boolean endsBefore(Conflict conflict,Date date )
	{
		Appointment appointment1 = getAppointment( conflict.getAppointment1());
		Appointment appointment2 = getAppointment( conflict.getAppointment2());
		if ( appointment1 == null || appointment2 == null)
		{
			return false;
		}
		boolean result = ConflictImpl.endsBefore( appointment1, appointment2, date);
		return result;
	}
	
	public boolean isActiveConflict(Conflict conflict,Date today)
	{
	    Appointment appointment1 = getAppointment( conflict.getAppointment1());
        Appointment appointment2 = getAppointment( conflict.getAppointment2());
        if ( appointment1 == null || appointment2 == null)
        {
            return false;
        }
        if (ConflictImpl.endsBefore( appointment1, appointment2, today))
        {
            return false;
        }
        if (!ConflictImpl.isConflict(appointment1, appointment2, today))
        {
            return false;
        }
        Reservation res1 = appointment1.getReservation();
        Reservation res2 = appointment2.getReservation();
        Allocatable alloc = resolver.tryResolve(conflict.getAllocatableId(), Allocatable.class);
        if ( res1 == null || res2 == null || alloc == null)
        {
            return false;
        }
        String annotation = alloc.getAnnotation( ResourceAnnotations.KEY_CONFLICT_CREATION);
        boolean holdBackConflicts = annotation != null && annotation.equals( ResourceAnnotations.VALUE_CONFLICT_CREATION_IGNORE);
        if ( holdBackConflicts)
        {
            return false;
        }
        if (res1.hasAllocated(alloc, appointment1) && res2.hasAllocated(alloc,appointment2))
        {
            return true;
        }
        return false;
	}
	
	private Appointment getAppointment(String id) 
	{
		return resolver.tryResolve(id, Appointment.class);
	}

	

	public void updateConflicts(Map<Allocatable, AllocationChange> toUpdate,UpdateResult evt, Date today,Collection<Allocatable> removedAllocatables)
	{
		Iterable<Change> it = evt.getOperations(UpdateResult.Change.class);
		for (Change next:it)
		{
			RaplaObject current = next.getNew();
			if ( current.getRaplaType() == Allocatable.TYPE)
			{
				Allocatable old = (Allocatable) next.getOld();
				Allocatable newAlloc = (Allocatable) next.getNew();
				if ( old != null && newAlloc != null )
				{
					if (isConflictIgnored(old) != isConflictIgnored(newAlloc))
					{
						// add and recalculate all if holdbackconflicts changed
						toUpdate.put( newAlloc, null);
					}
				}
			}
		}
		
		for ( Allocatable alloc: removedAllocatables)
		{
		    Set<Conflict> sortedSet = conflictMap.get( alloc);
		    if ( sortedSet != null && !sortedSet.isEmpty())
		    {
		        logger.error("Removing non empty conflict map for resource " +  alloc + " Appointments:" + sortedSet);
		    }
		    conflictMap.remove( alloc);
		}
		
    	Set<Conflict> added = new HashSet<Conflict>();
    	// this will recalculate the conflicts for that resource and the changed appointments
    	for ( Map.Entry<Allocatable, AllocationChange> entry:toUpdate.entrySet())
    	{
    		Allocatable allocatable = entry.getKey();
    		
    		AllocationChange changedAppointments = entry.getValue();
    		if ( changedAppointments == null)
			{
				conflictMap.remove( allocatable);
			}
			
    		Set<Conflict> conflictListBefore =  conflictMap.get(allocatable);
    		if ( conflictListBefore == null)
    		{
    			conflictListBefore = new LinkedHashSet<Conflict>();
    		}
			Set<Conflict> conflictListAfter = calculateConflicts( allocatable , today);
			conflictMap.put( allocatable, conflictListAfter);
			//User user = evt.getUser();
		
			for ( Conflict conflict: conflictListBefore)
			{
				boolean isRemoved = !conflictListAfter.contains(conflict);
				if  ( isRemoved )
				{
				    cache.fillConflictDisableInformation( evt.getUser(), conflict);
					evt.addOperation( new UpdateResult.Remove(conflict));
				}
			}
			for ( Conflict conflict: conflictListAfter)
			{
				boolean isNew = !conflictListBefore.contains(conflict);
				if  ( isNew )
				{
				    cache.fillConflictDisableInformation( evt.getUser(), conflict);
					evt.addOperation( new UpdateResult.Add(conflict));
					added.add( conflict);
				}
			}
    	}
    	
    	// so now we have the new conflicts, but what if a reservation or appointment changed without affecting the allocation but still 
    	// the conflict is still the same but the name could change, so we must somehow indicate the clients displaying that conflict, that they need to refresh the name,
    	// because the involving reservations are not automatically pushed to the client
    	
    	// first we create a list with all changed appointments. Notice if a reservation is changed all the appointments will change to
    	Map<Allocatable, Set<String>> appointmentUpdateMap = new LinkedHashMap<Allocatable, Set<String>>();
    	for (@SuppressWarnings("rawtypes") RaplaObject obj:evt.getChanged())
    	{
    		if ( obj.getRaplaType().equals( Reservation.TYPE))
    		{
    			Reservation reservation = (Reservation) obj;
    			for (Appointment app: reservation.getAppointments())
    			{
	    			for ( Allocatable alloc:reservation.getAllocatablesFor( app))
	    			{
	    				Set<String> set = appointmentUpdateMap.get( alloc);
	    				if ( set == null)
	    				{
	    					set = new HashSet<String>();
	    					appointmentUpdateMap.put(alloc, set);
	    				}
	    				set.add( app.getId());
	    			}
    			}
    		}
    	}
    	// then we create a map and look for any conflict that has changed appointment. This could still contain old appointment references  
    	Map<Conflict,Conflict> toUpdateConflicts = new LinkedHashMap<Conflict, Conflict>();
    	for ( Allocatable alloc: appointmentUpdateMap.keySet())
    	{
    		Set<String> changedAppointments = appointmentUpdateMap.get( alloc);
    		Set<Conflict> conflicts = conflictMap.get( alloc);
    		if ( conflicts != null)
    		{
	    		for ( Conflict conflict:conflicts)
	    		{
	    			String appointment1Id = conflict.getAppointment1();
	    			String appointment2Id = conflict.getAppointment2();
	    			boolean contains1 = changedAppointments.contains( appointment1Id);
					boolean contains2 = changedAppointments.contains( appointment2Id);
					if ( contains1 || contains2)
	    			{
	    				Conflict oldConflict = conflict;
						Appointment appointment1 = getAppointment( appointment1Id);
						Appointment appointment2 = getAppointment( appointment2Id);
						Conflict newConflict = new ConflictImpl( alloc, appointment1, appointment2, today);
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
    		    cache.fillConflictDisableInformation( evt.getUser(), newConflict);
    			evt.addOperation( new UpdateResult.Change( newConflict, oldConflict));
    		}
    	}

	}

//	private SortedSet<Appointment> getAndCreateListId(Map<Allocatable,SortedSet<Appointment>> appointmentMap,Allocatable alloc) {
//		SortedSet<Appointment> set = appointmentMap.get( alloc);
//		if ( set == null)
//		{
//			set = new TreeSet<Appointment>();
//			appointmentMap.put(alloc, set);
//		}
//		return set;
//	}
       
//	private Appointment getAppointment(
//			SortedSet<Appointment> changedAppointments, Appointment appointment) {
//		Appointment foundAppointment = changedAppointments.tailSet( appointment).iterator().next();
//		return foundAppointment;
//	}

	public int removeOldConflicts(UpdateResult result, Date today) 
	{
	    int count = 0;
		for (Set<Conflict> sortedSet: conflictMap.values())
		{
			Iterator<Conflict> it = sortedSet.iterator();
			while (it.hasNext())
			{
				Conflict conflict = it.next();
				if ( endsBefore( conflict,today))
				{
					it.remove();
					count++;
					result.addOperation( new UpdateResult.Remove(conflict));
				}
			}
		}
		return count;
		
	}

}
