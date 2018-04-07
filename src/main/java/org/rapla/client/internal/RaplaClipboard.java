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
package org.rapla.client.internal;

import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentStartComparator;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.ModificationListener;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.logger.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

@Singleton
public class RaplaClipboard implements ModificationListener
{
    private Appointment appointment;
    private Collection<Reservation> reservations = Collections.emptyList();

	private Allocatable[] restrictedAllocatables;
	private Collection<Allocatable> contextAllocatables = Collections.emptyList();
	public enum CopyType
	{
	    CUT_BLOCK,
	    CUT_RESERVATION,
	    COPY_RESERVATION,
	    COPY_BLOCK
	}

	CopyType copyType;
	
	@Inject
    public RaplaClipboard( ClientFacade facade, Logger logger )
    {
        facade.addModificationListener( this );
    }

    public void dataChanged( ModificationEvent evt ) throws RaplaException
    {
        if ( appointment == null )
            return;
        if ( evt.isRemoved(  appointment) || (appointment.getReservation() != null && evt.isRemoved( appointment.getReservation())))
        {
            clearAppointment();
        }
    }

	private void clearAppointment()
    {
        this.appointment = null;
        this.copyType = CopyType.COPY_BLOCK;
        this.restrictedAllocatables = null;
        this.reservations = Collections.emptyList();
        this.contextAllocatables = Collections.emptyList();
    }

    public void setAppointment( Appointment appointment,  Reservation destReservation, CopyType copyType, Allocatable[] restrictedAllocatables,Collection<Allocatable> contextAllocatables )
    {
    	this.appointment = appointment;
    	this.copyType = copyType;
        this.reservations = Collections.singleton(destReservation);
        this.restrictedAllocatables = restrictedAllocatables;
        this.contextAllocatables = contextAllocatables;
    }
    
    public void setReservation(Collection<Reservation> copyReservation, Collection<Allocatable> contextAllocatables)
    {
    	ArrayList<Appointment> appointmentList = new ArrayList<>();
    	for (Reservation r:copyReservation)
    	{
    		appointmentList.addAll( Arrays.asList( r.getAppointments()));
    	}
    	Collections.sort( appointmentList, new AppointmentStartComparator());
    	appointment = appointmentList.get(0);
    	copyType = CopyType.COPY_RESERVATION;
    	restrictedAllocatables = Allocatable.ALLOCATABLE_ARRAY;
    	reservations = copyReservation;
    	this.contextAllocatables = contextAllocatables;
    }
    
    public boolean isWholeReservation() 
    {
  		return copyType == CopyType.COPY_RESERVATION  || copyType == CopyType.CUT_RESERVATION;
  	}
    
    public boolean isPasteExistingPossible()
    {
        return copyType == CopyType.COPY_BLOCK || copyType ==CopyType.CUT_BLOCK;
    }
    
    public Appointment getAppointment()
    {
        return appointment;
    }
    
    
    public Allocatable[] getRestrictedAllocatables()
    {
        return restrictedAllocatables;
    }

	public Reservation getReservation() 
	{	
		if ( reservations == null || reservations.size() == 0)
		{
			return null;
		}
		return reservations.iterator().next();
	}
	
	public Collection<Reservation> getReservations() 
	{
		return reservations;
	}
	

    public Collection<Allocatable> getContextAllocatables() {
		return contextAllocatables;
	}

	public void setContextAllocatables(Collection<Allocatable> contextAllocatables) {
		this.contextAllocatables = contextAllocatables;
	}
	
	/** by default does nothing. Can be overriden with sytem specific implementation */
	public void copyToSystemClipboard(String content)
	{
	    
	}


}

/*
 class AllocationData implements Transferable {
 public static final DataFlavor allocationFlavor = new DataFlavor(java.util.Map.class, "Rapla Allocation");
 private static DataFlavor[] flavors = new DataFlavor[] {allocationFlavor};

 Map data;

 AllocationData(Map data) {
 this.data = data;
 }

 public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
 if (isDataFlavorSupported(flavor))
 return data;
 else
 throw new UnsupportedFlavorException(flavor);
 }

 public DataFlavor[] getTransferDataFlavors() {
 return flavors;
 }

 public boolean isDataFlavorSupported(DataFlavor flavor) {
 return flavor.equals(allocationFlavor);
 }

 }*/

