package org.rapla.gui.edit.reservation.test;

import java.awt.Window;
import java.util.Date;
import java.util.concurrent.Semaphore;

import javax.swing.JDialog;
import javax.swing.SwingUtilities;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.rapla.client.ClientService;
import org.rapla.components.util.DateTools;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.RepeatingType;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.gui.ReservationController;
import org.rapla.gui.tests.GUITestCase;
import org.rapla.gui.toolkit.DialogUI;
import org.rapla.gui.toolkit.RaplaButton;

public class UndoTests extends GUITestCase {

//	ReservationEdit reservationEdit;
	
	public UndoTests(String name) {
		super(name);
	}

	public static Test suite() {
		return new TestSuite(UndoTests.class);
	}

	protected void setUp() throws Exception {
		super.setUp();
//		reservationEdit = (ReservationEditImpl)control.getEditWindows()[0];
//		reservationEdit.allocatableEdit
//		selection = new AllocatableSelection(getContext(),true,null);
	}

	private void executeControlAndPressButton(final Runnable runnable,final  int buttonNr) throws Exception
	{
		final Semaphore mutex = new Semaphore(1);
		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				
				try {
					runnable.run();
				} finally {
					mutex.release();
				}
			}
		});
		// We block a mutex to wait for the move thread to finish
		mutex.acquire();
		SwingUtilities.invokeAndWait(new Runnable() {

			public void run() {
				for (Window window : JDialog.getWindows()) {
					if (window instanceof DialogUI) {
						RaplaButton button = ((DialogUI) window).getButton(buttonNr);
						button.doClick();
					}
				}
			}
		});
		// now wait until move thread is finished
		mutex.acquire();
		mutex.release();
	}
	
	
	/**
	 * Testing the resize function (not changing duration)
	 * You still have to decide whether you want to change only one appointment or the whole reservation
	 * @throws Exception
	 */
	
	//Erstellt von Jens Fritz
	public void testMoveUndo() throws Exception{ 
		final ClientService clientService = getClientService();
    	final ClientFacade facade = clientService.getFacade();
		final ReservationController control = getService(ReservationController.class);

        //Creating Event
		final Reservation event = createEvent(facade.newResource(), facade.newReservation());
        final Appointment changedAppointment = changeTime( true);
		int buttonNr = 1;
		executeControlAndPressButton(new Runnable() {
			
			public void run() {
				try {
					Appointment appOrig = event.getAppointments()[0];
					AppointmentBlock appointmentBlock = new AppointmentBlock( appOrig);
					Date newStart = changedAppointment.getStart();
					Date newEnd = changedAppointment.getEnd();
					control.resizeAppointment(appointmentBlock, newStart, newEnd, null, null, false);
				} catch (RaplaException e) {
					fail(e.getMessage());
				}
			}
		}, buttonNr);
       
        //need to use the last event, because if you change only one appointment within the repeating
        //it will create a new appointment and add it to the end of the array
        //Then comparing the starttimes of the nonPersistantEvent-Appointment and the PersistantEvent-Appointment
		{
			Reservation persistant = facade.getPersistant( event );
			Appointment appOrig = event.getAppointments()[0];
			Appointment appCpy = persistant.getAppointments()[0];
			assertFalse(appOrig.matches(appCpy));
		}
		{
			facade.getCommandHistory().undo();
			Reservation persistant = facade.getPersistant( event );
			Appointment appOrig = event.getAppointments()[0];
			Appointment appCpy = persistant.getAppointments()[0];
			assertTrue(appOrig.matches(appCpy));
		}
		{
			facade.getCommandHistory().redo();
			Reservation persistant = facade.getPersistant( event );
			Appointment appOrig = event.getAppointments()[0];
			Appointment appCpy = persistant.getAppointments()[0];
			assertFalse(appOrig.matches(appCpy));
		}
    }
	
	
	/**
	 * Testing the resize function (change duration of an event/reservation). 
	 * You still have to decide whether you want to change only one appointment or the whole reservation
	 * @throws Exception
	 */
	
	public void testResizeUndo() throws Exception{
		final ClientService clientService = getClientService();
    	final ClientFacade facade = clientService.getFacade();
		final ReservationController control = getService(ReservationController.class);

		final Reservation event = createEvent(facade.newResource(), facade.newReservation());
        final Appointment changedAppointment = changeTime( false);
        //control.resizeAppointment(persistantEvent.getAppointments()[0], persistantEvent.getAppointments()[0].getStart(), changedAppointment.getStart(), changedAppointment.getEnd(), null, null, false);
        
		int buttonNr = 0;
		executeControlAndPressButton(new Runnable() {
			
			public void run() {
				try {
					AppointmentBlock appointmentBlock = new AppointmentBlock( event.getAppointments()[0]);
					Date newStart = changedAppointment.getStart();
					Date newEnd = changedAppointment.getEnd();
					control.resizeAppointment(appointmentBlock, newStart, newEnd, null, null, false);
				} catch (RaplaException e) {
					fail(e.getMessage());
				}
			}
		}, buttonNr);
        
        //need to use the last event, because if you change only one appointment within the repeating
        //it will create a new appointment and add it to the end of the array.
        //Then comparing the starttimes of the nonPersistantEvent-Appointment and the PersistantEvent-Appointment
		{
			Reservation persistant = facade.getPersistant( event );
			Appointment appOrig = event.getAppointments()[0];
			Appointment appCpy = persistant.getAppointments()[0];
			assertFalse(appOrig.matches(appCpy));
		}
		{
			facade.getCommandHistory().undo();
			Reservation persistant = facade.getPersistant( event );
			Appointment appOrig = event.getAppointments()[0];
			Appointment appCpy = persistant.getAppointments()[0];
			assertTrue(appOrig.matches(appCpy));
		}
		{
			facade.getCommandHistory().redo();
			Reservation persistant = facade.getPersistant( event );
			Appointment appOrig = event.getAppointments()[0];
			Appointment appCpy = persistant.getAppointments()[0];
			assertFalse(appOrig.matches(appCpy));
		}

    }
	
	
	/**
	 * Testing the delete function. 
	 * You still have to decide whether you want to delete only one appointment or the whole reservation
	 * @throws Exception
	 */
	
	//Erstellt von Jens Fritz
	public void testDeleteUndo() throws Exception{
		final ClientService clientService = getClientService();
    	final ClientFacade facade = clientService.getFacade();
		final ReservationController control = getService(ReservationController.class);

		Allocatable nonPersistantAllocatable = facade.newResource();
        Reservation nonPersistantEvent = facade.newReservation();
        
        //Creating Event
        createEvent(nonPersistantAllocatable, nonPersistantEvent);
        final Reservation persistantEvent = facade.getPersistant( nonPersistantEvent );
    	int buttonNr = 1;
		executeControlAndPressButton(new Runnable() {
			
			public void run() {
				try {
					AppointmentBlock appointmentBlock = new AppointmentBlock( persistantEvent.getAppointments()[0]);
					control.deleteAppointment(appointmentBlock, null, null);
				} catch (RaplaException e) {
					fail(e.getMessage());
				}
			}
		}, buttonNr);
        Reservation exist=null;
        try {
        	//if you deleted the whole event, it will throw an exception at this point
        	exist = facade.getPersistant(nonPersistantEvent);
        	
        	//checks if an appointment was deleted (not used if exception is thrown)
        	assertNotNull(exist.getAppointments()[0].getRepeating().getExceptions());
		} catch (EntityNotFoundException e) {
			facade.getCommandHistory().undo();
		}
        try {
        	exist = facade.getPersistant(nonPersistantEvent);
        	assertTrue(exist !=null);
		} catch (EntityNotFoundException e) {
			e.printStackTrace();
		}
        facade.getCommandHistory().redo();
        try {
        	exist = facade.getPersistant(nonPersistantEvent);
        	assertNotNull(exist.getAppointments()[0].getRepeating().getExceptions());
		} catch (Exception e) {
		}
        	
        	
	}
	
	
	/**
	 * Creating an event which repeats daily throughout 3 days 
	 * @param nonPersistantAllocatable
	 * @param nonPersistantEvent
	 * @throws RaplaException
	 * @throws Exception
	 */
	private Reservation createEvent(Allocatable nonPersistantAllocatable,
			Reservation nonPersistantEvent) throws RaplaException, Exception {
		nonPersistantAllocatable.getClassification().setValue("name", "Bla");
		nonPersistantEvent.getClassification().setValue("name","dummy-event");
        assertEquals( "event", nonPersistantEvent.getClassification().getType().getKey());
        nonPersistantEvent.addAllocatable( nonPersistantAllocatable );
        Appointment appointment = getFacade().newAppointment( new Date(), new Date());
        appointment.setRepeatingEnabled( true);
        appointment.getRepeating().setType(RepeatingType.DAILY);
        appointment.getRepeating().setNumber( 3 );
		nonPersistantEvent.addAppointment( appointment);
//        getFacade().newAppointment(new Date(), new Date(),RepeatingType.findForString("weekly"), 5);
        getFacade().storeObjects( new Entity[] { nonPersistantAllocatable, nonPersistantEvent} );
        return nonPersistantEvent;
	}

	private Appointment changeTime(boolean keepTime) throws RaplaException, Exception {
		Date newStart = new Date();
		Date newEnd = new Date();
		if(!keepTime){
			newStart = getRaplaLocale().toTime(13, 0, 0);
			newEnd  = getRaplaLocale().toTime(13, 0, 0);
		}else{
			newStart = new Date(newStart.getTime() + DateTools.MILLISECONDS_PER_HOUR * 2);
			newEnd = new Date(newEnd.getTime() + DateTools.MILLISECONDS_PER_HOUR * 2);
		}
		Appointment retAppointment = getFacade().newAppointment(newStart, newEnd);
		return retAppointment;
	}
}
