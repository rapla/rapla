/*--------------------------------------------------------------------------*
 | Copyright (C) 2006  Christopher Kohlhaas                                 |
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

package org.rapla.gui.edit.reservation.test;
import java.awt.Point;
import java.awt.Window;
import java.util.Date;
import java.util.concurrent.Semaphore;

import javax.swing.JDialog;
import javax.swing.SwingUtilities;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.rapla.client.ClientService;
import org.rapla.components.util.DateTools;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.gui.ReservationController;
import org.rapla.gui.ReservationEdit;
import org.rapla.gui.tests.GUITestCase;
import org.rapla.gui.toolkit.DialogUI;
import org.rapla.gui.toolkit.RaplaButton;
import org.rapla.storage.StorageOperator;

public final class ReservationControllerTest extends GUITestCase {
	public ReservationControllerTest(String name) {
		super(name);
	}

	public static Test suite() {
		return new TestSuite(ReservationControllerTest.class);
	}

	public void testMain() throws Exception {
		ClientService clientService = getClientService();
		Reservation[] reservations = clientService.getFacade()
				.getReservationsForAllocatable(null, null, null, null);
		final ReservationController c = clientService.getContext().lookup(ReservationController.class);
		final Reservation reservation = reservations[0];
		c.edit(reservation);
		getLogger().info("ReservationController started");
	}

	public void testMove() throws Exception {
		final ClientService clientService = getClientService();
		Reservation[] reservations = clientService.getFacade().getReservationsForAllocatable(null, null, null, null);
		final ReservationController c =  clientService.getContext().lookup(ReservationController.class);
		final Reservation reservation = reservations[0];
		Appointment[] appointments = reservation.getAppointments();
		final Appointment appointment = appointments[0];
		final Date from = appointment.getStart();
		final Semaphore mutex = new Semaphore(1);
		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				boolean keepTime = true;
				Point p = null;
				AppointmentBlock appointmentBlock = new AppointmentBlock( appointment);
				Date newStart = DateTools.addDay(appointment.getStart());
				try {
					c.moveAppointment(appointmentBlock, newStart, null, p,	keepTime);
					Appointment app = clientService.getFacade().getPersistant(reservation).getAppointments()[0];
					assertEquals(DateTools.addDay(from), app.getStart());
					// Now the test can end
					mutex.release();
				} catch (RaplaException e) {
					e.printStackTrace();
				}
			}
		});
		// We block a mutex to wait for the move thread to finish
		mutex.acquire();
		SwingUtilities.invokeAndWait(new Runnable() {

			@Override
			public void run() {
				for (Window window : JDialog.getWindows()) {
					if (window instanceof DialogUI) {
						RaplaButton button = ((DialogUI) window).getButton(1);
						button.doClick();
					}
				}
			}
		});
		// now wait until move thread is finished
		mutex.acquire();
		mutex.release();
		
		//Testing undo & redo function
		clientService.getFacade().getCommandHistory().undo();
		assertEquals(from, clientService.getFacade().getPersistant(reservation).getAppointments()[0].getStart());
		clientService.getFacade().getCommandHistory().redo();
		assertEquals(DateTools.addDay(from), clientService.getFacade().getPersistant(reservation).getAppointments()[0].getStart());
	}
	
	
	public void testPeriodChange() throws Exception {
		ClientFacade facade = getFacade();
		ClassificationFilter[] filters = facade.getDynamicType(StorageOperator.PERIOD_TYPE).newClassificationFilter().toArray();
		Allocatable[] periods = facade.getAllocatables(filters);
		facade.removeObjects(periods);
		Thread.sleep(500);
		ClientService clientService = getClientService();
		Reservation[] reservations = clientService.getFacade()
				.getReservationsForAllocatable(null, null, null, null);
		ReservationController c = clientService.getContext().lookup(ReservationController.class);
		c.edit(reservations[0]);
		getLogger().info("ReservationController started");
		ReservationEdit editor = c.getEditWindows()[0];
		Date startDate = new Date();
		editor.addAppointment(startDate, new Date(startDate.getTime() + DateTools.MILLISECONDS_PER_DAY));
		editor.save();
		Allocatable period = facade.newPeriod();
		Classification classification = period.getClassification();
		classification.setValue("start",startDate);
		classification.setValue("start",new Date(startDate.getTime() + 3
				* DateTools.MILLISECONDS_PER_DAY));
		facade.store(period);
		Thread.sleep(500);
	}

	public static void main(String[] args) {
		new ReservationControllerTest(ReservationControllerTest.class.getName())
				.interactiveTest("testMain");
	}
}
