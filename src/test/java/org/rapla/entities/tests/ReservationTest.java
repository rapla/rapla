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
package org.rapla.entities.tests;
import java.util.Calendar;
import java.util.Date;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.rapla.RaplaTestCase;
import org.rapla.components.util.DateTools;
import org.rapla.entities.Entity;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.ModificationModule;
import org.rapla.facade.QueryModule;
import org.rapla.framework.RaplaException;

public class ReservationTest extends RaplaTestCase {
    Reservation reserv1;
    Reservation reserv2;
    Allocatable allocatable1;
    Allocatable allocatable2;
    Calendar cal;

    ModificationModule modificationMod;
    QueryModule queryMod;

    public ReservationTest(String name) {
        super(name);
    }

    public static Test suite() {
        return new TestSuite(ReservationTest.class);
    }

    public void setUp() throws Exception {
        super.setUp();

        ClientFacade facade = getFacade();
        queryMod = facade;
        modificationMod = facade;

        cal = Calendar.getInstance(DateTools.getTimeZone());


        reserv1 = modificationMod.newReservation();
        reserv1.getClassification().setValue("name","Test Reservation 1");

        reserv2 = modificationMod.newReservation();
        reserv2.getClassification().setValue("name","Test Reservation 2");

        allocatable1 = modificationMod.newResource();
        allocatable1.getClassification().setValue("name","Test Resource 1");

        allocatable2 = modificationMod.newResource();
        allocatable2.getClassification().setValue("name","Test Resource 2");

        cal.set(Calendar.DAY_OF_WEEK,Calendar.TUESDAY);
        cal.set(Calendar.HOUR_OF_DAY,13);
        cal.set(Calendar.MINUTE,0);
        Date startDate = cal.getTime();
        cal.set(Calendar.HOUR_OF_DAY,16);
        Date endDate = cal.getTime();
        Appointment appointment = modificationMod.newAppointment(startDate, endDate);
        reserv1.addAppointment(appointment);
        reserv1.addAllocatable(allocatable1);
    }

    public void testHasAllocated() {
        assertTrue(reserv1.hasAllocated(allocatable1));
        assertTrue( ! reserv1.hasAllocated(allocatable2));
    }

    public void testEqual() {
        assertTrue( ! reserv1.equals (reserv2));
        assertTrue(reserv1.equals (reserv1));
    }

    public void testEdit() throws RaplaException {
        // store the reservation to create the id's
        modificationMod.storeObjects(new Entity[] {allocatable1,allocatable2, reserv1});
        String eventId;
        {
	        Reservation persistantReservation = modificationMod.getPersistant( reserv1);
	        eventId = persistantReservation.getId();
	        @SuppressWarnings("unused")
			Appointment oldAppointment= persistantReservation.getAppointments()[0];
	
	        // Clone the reservation
	        Reservation clone =  modificationMod.edit(persistantReservation);
	        assertTrue(persistantReservation.equals(clone));
	        assertTrue(clone.hasAllocated(allocatable1));
	
	        // Modify the cloned appointment
	        Appointment clonedAppointment= clone.getAppointments()[0];
	        cal = Calendar.getInstance(DateTools.getTimeZone());
	        cal.setTime(clonedAppointment.getStart());
	        cal.set(Calendar.HOUR_OF_DAY,12);
	        clonedAppointment.move(cal.getTime());
	
	        // Add a new appointment
	        cal.setTime(clonedAppointment.getStart());
	        cal.set(Calendar.DAY_OF_WEEK,Calendar.MONDAY);
	        cal.set(Calendar.HOUR_OF_DAY,15);
	        Date startDate2 = cal.getTime();
	        cal.set(Calendar.HOUR_OF_DAY,17);
	        Date endDate2 = cal.getTime();
	        Appointment newAppointment = modificationMod.newAppointment(startDate2, endDate2);
	        clone.addAppointment(newAppointment);
	
	        // store clone
	        modificationMod.storeObjects(new Entity[] {clone});
	    }
        Reservation persistantReservation = getFacade().getOperator().resolve(eventId, Reservation.class);
		assertTrue(persistantReservation.hasAllocated(allocatable1));
		// Check if oldAppointment has been modified
		Appointment[] appointments = persistantReservation.getAppointments();
        cal.setTime(appointments[0].getStart());
        assertTrue(cal.get(Calendar.DAY_OF_WEEK) == Calendar.TUESDAY);
        assertTrue(cal.get(Calendar.HOUR_OF_DAY) == 12);

        // Check if newAppointment has been added
        assertTrue(appointments.length == 2);
        cal.setTime(appointments[1].getEnd());
        assertEquals(17,cal.get(Calendar.HOUR_OF_DAY));
        assertEquals(Calendar.MONDAY,cal.get(Calendar.DAY_OF_WEEK));
        cal.setTime(appointments[1].getStart());
        assertEquals(15,cal.get(Calendar.HOUR_OF_DAY));
        assertEquals(Calendar.MONDAY,cal.get(Calendar.DAY_OF_WEEK));
    }

}





