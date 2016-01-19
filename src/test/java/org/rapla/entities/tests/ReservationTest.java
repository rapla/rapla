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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.components.util.DateTools;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.test.util.RaplaTestCase;

import java.util.Calendar;
import java.util.Date;


@RunWith(JUnit4.class)
public class ReservationTest {
    Reservation reserv1;
    Reservation reserv2;
    Allocatable allocatable1;
    Allocatable allocatable2;
    Calendar cal;

    RaplaFacade facade;

    @Before
    public void setUp() throws Exception
    {
        ClientFacade clientFacade = RaplaTestCase.createSimpleSimpsonsWithHomer();
        facade = clientFacade.getRaplaFacade();

        cal = Calendar.getInstance(DateTools.getTimeZone());

        User user =clientFacade.getUser();
        reserv1 = facade.newReservation();
        reserv1.getClassification().setValue("name","Test Reservation 1");

        reserv2 = facade.newReservation();
        reserv2.getClassification().setValue("name","Test Reservation 2");

        allocatable1 = facade.newResource();
        allocatable1.getClassification().setValue("name", "Test Resource 1");

        allocatable2 = facade.newResource();
        allocatable2.getClassification().setValue("name", "Test Resource 2");

        cal.set(Calendar.DAY_OF_WEEK,Calendar.TUESDAY);
        cal.set(Calendar.HOUR_OF_DAY,13);
        cal.set(Calendar.MINUTE,0);
        Date startDate = cal.getTime();
        cal.set(Calendar.HOUR_OF_DAY,16);
        Date endDate = cal.getTime();
        Appointment appointment = facade.newAppointment(startDate, endDate);
        reserv1.addAppointment(appointment);
        reserv1.addAllocatable(allocatable1);
    }


    @Test
    public void testHasAllocated() {
        Assert.assertTrue(reserv1.hasAllocated(allocatable1));
        Assert.assertTrue(!reserv1.hasAllocated(allocatable2));
    }

    @Test
    public void testEqual() {
        Assert.assertTrue(!reserv1.equals(reserv2));
        Assert.assertTrue(reserv1.equals(reserv1));
    }

    @Test
    public void testEdit() throws RaplaException {
        // store the reservation to create the id's
        facade.storeObjects(new Entity[] { allocatable1, allocatable2, reserv1 });
        String eventId;
        {
	        Reservation persistantReservation = facade.getPersistant( reserv1);
	        eventId = persistantReservation.getId();
	        @SuppressWarnings("unused")
			Appointment oldAppointment= persistantReservation.getAppointments()[0];
	
	        // Clone the reservation
	        Reservation clone =  facade.edit(persistantReservation);
            Assert.assertTrue(persistantReservation.equals(clone));
            Assert.assertTrue(clone.hasAllocated(allocatable1));
	
	        // Modify the cloned appointment
	        Appointment clonedAppointment= clone.getAppointments()[0];
	        cal = Calendar.getInstance(DateTools.getTimeZone());
	        cal.setTime(clonedAppointment.getStart());
	        cal.set(Calendar.HOUR_OF_DAY, 12);
	        clonedAppointment.move(cal.getTime());
	
	        // Add a new appointment
	        cal.setTime(clonedAppointment.getStart());
	        cal.set(Calendar.DAY_OF_WEEK,Calendar.MONDAY);
	        cal.set(Calendar.HOUR_OF_DAY,15);
	        Date startDate2 = cal.getTime();
	        cal.set(Calendar.HOUR_OF_DAY,17);
	        Date endDate2 = cal.getTime();
	        Appointment newAppointment = facade.newAppointment(startDate2, endDate2);
	        clone.addAppointment(newAppointment);
	
	        // store clone
	        facade.storeObjects(new Entity[] { clone });
	    }
        Reservation persistantReservation = facade.getOperator().resolve(eventId, Reservation.class);
        Assert.assertTrue(persistantReservation.hasAllocated(allocatable1));
		// Check if oldAppointment has been modified
		Appointment[] appointments = persistantReservation.getAppointments();
        cal.setTime(appointments[0].getStart());
        Assert.assertTrue(cal.get(Calendar.DAY_OF_WEEK) == Calendar.TUESDAY);
        Assert.assertTrue(cal.get(Calendar.HOUR_OF_DAY) == 12);

        // Check if newAppointment has been added
        Assert.assertTrue(appointments.length == 2);
        cal.setTime(appointments[1].getEnd());
        Assert.assertEquals(17, cal.get(Calendar.HOUR_OF_DAY));
        Assert.assertEquals(Calendar.MONDAY, cal.get(Calendar.DAY_OF_WEEK));
        cal.setTime(appointments[1].getStart());
        Assert.assertEquals(15, cal.get(Calendar.HOUR_OF_DAY));
        Assert.assertEquals(Calendar.MONDAY, cal.get(Calendar.DAY_OF_WEEK));
    }

}





