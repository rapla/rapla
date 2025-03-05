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
package org.rapla.facade.internal;

import org.rapla.entities.Entity;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.RequestStatus;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.internal.AllocatableImpl;
import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.entities.storage.internal.ReferenceHandler;
import org.rapla.facade.AllocationChangeEvent;
import org.rapla.logger.Logger;
import org.rapla.storage.StorageOperator;
import org.rapla.storage.UpdateResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Converts updateResults into AllocationChangeEvents */
public class AllocationChangeFinder 
{
    ArrayList<AllocationChangeEvent> changeList = new ArrayList<>();
    Logger logger;
    private final EntityResolver resolver;

    private AllocationChangeFinder(Logger logger, UpdateResult updateResult, User user, EntityResolver resolver) {
        this.logger = logger;
        this.resolver = resolver;
        if ( updateResult == null)
            return;
        for (UpdateResult.Add addOp: updateResult.getOperations( UpdateResult.Add.class )) {
            final ReferenceInfo reference = addOp.getReference();
            final Entity lastKnown = updateResult.getLastKnown(reference);
            added(  lastKnown, user );
        }
        for (UpdateResult.Remove removeOp: updateResult.getOperations( UpdateResult.Remove.class )) {
            final ReferenceInfo reference = removeOp.getReference();
            final Entity removedEvent = updateResult.getLastEntryBeforeUpdate(reference);
            removed( reference, removedEvent, user );
        }
        for (UpdateResult.Change changeOp :updateResult.getOperations( UpdateResult.Change.class )) {
            final ReferenceInfo reference = changeOp.getReference();
            Entity old =  updateResult.getLastEntryBeforeUpdate(reference);
            Entity newObj = updateResult.getLastKnown(reference);
            changed(old , newObj, user );
        }
    }
    
    public Logger getLogger() 
    {
        return logger;
    }

    static public List<AllocationChangeEvent> getTriggerEvents(UpdateResult result, User user, Logger logger, EntityResolver resolver) {
    	AllocationChangeFinder finder = new AllocationChangeFinder(logger, result, user, resolver);
    	return finder.changeList; 
    }

    private void added(RaplaObject entity,  User user) {
        if ( entity.getTypeClass() == Reservation.class ) {
            Reservation newRes = (Reservation) entity;
            addAppointmentAdd(
                              user
                              ,newRes
                              ,newRes
                              ,Arrays.asList(newRes.getAllocatables())
                              ,Arrays.asList(newRes.getAppointments())
                              );
        }
    }

    private void removed(ReferenceInfo reference, Entity removedEntity, User user) {
        if ( reference.getType() == Reservation.class ) {
            if (getLogger().isDebugEnabled())
                getLogger().debug("Reservation removed: " + removedEntity.getId());
            Reservation oldRes = (Reservation) removedEntity;
            final Appointment[] appointments = oldRes.getAppointments();
            final List<Allocatable> allocatables = getAllocatablesUsingIds(oldRes);
//            for (Allocatable allocatable:allocatables)
//            {
//                for (Appointment appointment:appointments)
//                {
//                    if (!oldRes.hasAllocated(allocatable,appointment))
//                        continue;
//
//                    changeList.add(new AllocationChangeEvent(AllocationChangeEvent.REMOVE,user, newRes,allocatable,appointment));
//                }
//            }
            addAppointmentRemove(
                                 user
                                 ,oldRes
                                 ,oldRes
                                 ,allocatables
                                 ,Arrays.asList(appointments)
                                );
        }
    }
    
    private List<Allocatable> getAllocatablesUsingIds(Reservation reservation)
    {
        final ArrayList<Allocatable> result = new ArrayList<>();
        ReservationImpl resImpl = ((ReservationImpl)reservation);
        final Appointment[] appointments = resImpl.getAppointments();
        Collection<ReferenceInfo<Allocatable>> allocatableIds = new HashSet<>();
        for (Appointment appointment : appointments)
        {
            final Collection<ReferenceInfo<Allocatable>> allocatableIdsFor = resImpl.getAllocatableIdsFor(appointment);
            allocatableIds.addAll(allocatableIdsFor);
        }
        for (ReferenceInfo<Allocatable> allocatableId : allocatableIds)
        {
            Allocatable alloc = resolver.tryResolve(allocatableId);
            if (alloc == null)
            {
                alloc = ReferenceHandler.tryResolveMissingAllocatable( resolver, allocatableId.getId(), Allocatable.class );
            }
            result.add(alloc);
        }
        return result;
    }

    private void changed(Entity oldEntity,Entity newEntity, User user) {
        Class<? extends Entity> raplaType = newEntity.getTypeClass();
        if (oldEntity == null) {
            getLogger().error(" change event triggered but old entity = null for " + newEntity);
            return;
        }
        if (raplaType ==  Reservation.class ) {
            if (getLogger().isDebugEnabled())
                getLogger().debug("Reservation changed: " + oldEntity);
            Reservation oldRes = (Reservation) oldEntity;
            Reservation newRes = (Reservation) newEntity;

            List<Allocatable> alloc1 = getAllocatablesUsingIds(oldRes);
            List<Allocatable> alloc2 = Arrays.asList(newRes.getAllocatables());

            List<Appointment> app1 = Arrays.asList(oldRes.getAppointments());
            List<Appointment> app2 = Arrays.asList(newRes.getAppointments());

            ArrayList<Allocatable> removeList = new ArrayList<>(alloc1);
            removeList.removeAll(alloc2);
            // add removed allocations to the change list
            addAppointmentRemove(user, newRes, oldRes, removeList, app1);

            ArrayList<Allocatable> addList = new ArrayList<>(alloc2);
            addList.removeAll(alloc1);
            // add new allocations to the change list
            addAppointmentAdd(user, newRes,oldRes, addList, app2);

            ArrayList<Allocatable> changeList = new ArrayList<>(alloc2);
            changeList.retainAll(alloc1);
            addAllocationDiff(user, changeList,oldRes,newRes);
        }
    }

    /*
    private void printList(List list) {
        Iterator it = list.iterator();
        while (it.hasNext()) {
            System.out.println(it.next());
        }
    }
    */

    /**
     * Calculates the allocations that have changed
     */
    private void addAllocationDiff(User user,List<Allocatable> allocatableList,Reservation oldRes,Reservation newRes) {
        List<Appointment> app1 = Arrays.asList(oldRes.getAppointments());
        List<Appointment> app2 = Arrays.asList(newRes.getAppointments());
        ArrayList<Appointment> removeList = new ArrayList<>(app1);
        removeList.removeAll(app2);
        addAppointmentRemove(user, newRes, oldRes, allocatableList,removeList);
        ArrayList<Appointment> addList = new ArrayList<>(app2);
        addList.removeAll(app1);
        addAppointmentAdd(user, newRes,oldRes,allocatableList,addList);
        /*
        System.out.println("OLD appointments");
        printList(app1);
        System.out.println("NEW appointments");
        printList(app2);
        */
        Set<Appointment> newList = new HashSet<>(app2);
        newList.retainAll(app1);

        ArrayList<Appointment> oldList = new ArrayList<>(app1);
        oldList.retainAll(app2);
        sort(oldList);
        

        for (int i=0;i<oldList.size();i++) {
            Appointment oldApp =  oldList.get(i);
            Appointment newApp = null;
            for ( Appointment app:newList)
            {
            	if ( app.equals( oldApp))
            	{
            		newApp = app;
            	}
            }
            if ( newApp == null)
            {
            	// This should never happen as we call retainAll before
            	getLogger().error("Not found matching pair for " + oldApp);
            	continue;
            }
            for (Allocatable allocatable: allocatableList )
            {
                boolean oldAllocated = oldRes.hasAllocatedOn(allocatable, oldApp);
                boolean newAllocated = newRes.hasAllocatedOn(allocatable, newApp);
                final RequestStatus requestStatus = newRes.getRequestStatus(allocatable);
                final RequestStatus oldRequestStatus = oldRes.getRequestStatus(allocatable);
                boolean denied = false;
                if (!oldAllocated && !newAllocated) {
                    continue;
                }
                else if (!oldAllocated && newAllocated)
                {
                    changeList.add(new AllocationChangeEvent(AllocationChangeEvent.ADD,user,newRes,oldRes, allocatable,newApp));
                }
                else if (oldAllocated && !newAllocated)
                {
                    changeList.add(new AllocationChangeEvent(AllocationChangeEvent.REMOVE,user,newRes, oldRes,allocatable,newApp));
                    if ( oldRes.getRequestStatus( allocatable) == RequestStatus.REQUESTED ) {
                        changeList.add(new AllocationChangeEvent(AllocationChangeEvent.DENIED,user, newRes,oldRes,allocatable,newApp));
                        denied = true;
                    }
                }
                else if (!newApp.matches(oldApp))
                {
                    getLogger().debug("\n" + newApp + " doesn't match \n" + oldApp);
                    changeList.add(new AllocationChangeEvent(user,newRes,oldRes,allocatable,newApp,oldApp));
                }
                if (requestStatus != oldRequestStatus ){
                    if (  requestStatus == RequestStatus.REQUESTED) {
                        changeList.add(new AllocationChangeEvent(AllocationChangeEvent.REQUESTED,user, newRes,oldRes,allocatable,newApp));
                    }
                    if (  requestStatus == null) {
                        if ( newRes != null && newRes.hasAllocated( allocatable)) {
                            changeList.add(new AllocationChangeEvent(AllocationChangeEvent.CONFIRMED, user, newRes, oldRes,allocatable, newApp));
                         } else {
                            changeList.add(new AllocationChangeEvent(AllocationChangeEvent.DENIED, user, newRes, oldRes,allocatable, newApp));
                        }
                    }
                }
            }
        }
    }

	@SuppressWarnings("unchecked")
	public void sort(ArrayList<Appointment> oldList) {
		Collections.sort(oldList);
	}

    private void addAppointmentAdd(User user,Reservation newRes,Reservation oldRes,List<Allocatable> allocatables,List<Appointment> appointments) {
        for (Allocatable allocatable:allocatables)
        {
            for (Appointment appointment:appointments)
            {
                final RequestStatus requestStatus = newRes.getRequestStatus(allocatable);
                if (  requestStatus == RequestStatus.REQUESTED ) {
                    changeList.add(new AllocationChangeEvent(AllocationChangeEvent.REQUESTED,user, newRes,oldRes,allocatable,appointment));
                }
                if (!newRes.hasAllocatedOn(allocatable,appointment))
                    continue;

                changeList.add(new AllocationChangeEvent(AllocationChangeEvent.ADD,user, newRes,oldRes,allocatable,appointment));
            }
        }
    }

    private void addAppointmentRemove(User user,Reservation newRes,Reservation oldRes,List<Allocatable> allocatables,List<Appointment> appointments) {
    	 for (Allocatable allocatable:allocatables)
         {
         	for (Appointment appointment:appointments)
             {
                if (!oldRes.hasAllocatedOn(allocatable,appointment))
                    continue;
                 changeList.add(new AllocationChangeEvent(AllocationChangeEvent.REMOVE, user, newRes, oldRes, allocatable, appointment));
                 if ( oldRes.getRequestStatus( allocatable) == RequestStatus.REQUESTED ) {
                     changeList.add(new AllocationChangeEvent(AllocationChangeEvent.DENIED, user, newRes, oldRes, allocatable, appointment));
                 }
            }
        }
    }
}

