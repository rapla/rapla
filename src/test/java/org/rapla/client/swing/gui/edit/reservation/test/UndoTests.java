package org.rapla.client.swing.gui.edit.reservation.test;

import org.junit.Assert;
import org.rapla.client.PopupContext;
import org.rapla.client.ReservationController;
import org.rapla.client.swing.gui.tests.GUITestCase;
import org.rapla.client.swing.toolkit.DialogUI;
import org.rapla.client.swing.toolkit.RaplaButton;
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

import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import java.awt.Window;
import java.util.Date;
import java.util.concurrent.Semaphore;

public class UndoTests extends GUITestCase {

//	ReservationEdit reservationEdit;
	
	protected void setUp() throws Exception {
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
		final ClientFacade facade = getFacade();
		final ReservationController control = getService(ReservationController.class);

        //Creating Event
		final Reservation event = createEvent(facade.getRaplaFacade().newResource(), facade.getRaplaFacade().newReservation());
        final Appointment changedAppointment = changeTime( true);
		int buttonNr = 1;
		executeControlAndPressButton(new Runnable() {
			
			public void run() {
				try {
					Appointment appOrig = event.getAppointments()[0];
					AppointmentBlock appointmentBlock = new AppointmentBlock( appOrig);
					Date newStart = changedAppointment.getStart();
					Date newEnd = changedAppointment.getEnd();
					PopupContext popupContext = createPopupContext();
                    control.resizeAppointment(appointmentBlock, newStart, newEnd, popupContext, false);
				} catch (RaplaException e) {
					Assert.fail(e.getMessage());
				}
			}
		}, buttonNr);
       
        //need to use the last event, because if you change only one appointment within the repeating
        //it will create a new appointment and add it to the end of the array
        //Then comparing the starttimes of the nonPersistantEvent-Appointment and the PersistantEvent-Appointment
		{
			Reservation persistant = facade.getRaplaFacade().getPersistant( event );
			Appointment appOrig = event.getAppointments()[0];
			Appointment appCpy = persistant.getAppointments()[0];
			Assert.assertFalse(appOrig.matches(appCpy));
		}
		{
			facade.getCommandHistory().undo();
			Reservation persistant = facade.getRaplaFacade().getPersistant( event );
			Appointment appOrig = event.getAppointments()[0];
			Appointment appCpy = persistant.getAppointments()[0];
			Assert.assertTrue(appOrig.matches(appCpy));
		}
		{
			facade.getCommandHistory().redo();
			Reservation persistant = facade.getRaplaFacade().getPersistant( event );
			Appointment appOrig = event.getAppointments()[0];
			Appointment appCpy = persistant.getAppointments()[0];
			Assert.assertFalse(appOrig.matches(appCpy));
		}
    }
	
	
	/**
	 * Testing the resize function (change duration of an event/reservation). 
	 * You still have to decide whether you want to change only one appointment or the whole reservation
	 * @throws Exception
	 */
	
	public void testResizeUndo() throws Exception{
    	final ClientFacade facade = getFacade();
		final ReservationController control = getService(ReservationController.class);

		final Reservation event = createEvent(facade.getRaplaFacade().newResource(), facade.getRaplaFacade().newReservation());
        final Appointment changedAppointment = changeTime( false);
        //control.resizeAppointment(persistantEvent.getAppointments()[0], persistantEvent.getAppointments()[0].getStart(), changedAppointment.getStart(), changedAppointment.getEnd(), null, null, false);
        
		int buttonNr = 0;
		executeControlAndPressButton(new Runnable() {
			
			public void run() {
				try {
					AppointmentBlock appointmentBlock = new AppointmentBlock( event.getAppointments()[0]);
					Date newStart = changedAppointment.getStart();
					Date newEnd = changedAppointment.getEnd();
					PopupContext popupContext= createPopupContext();
                    control.resizeAppointment(appointmentBlock, newStart, newEnd, popupContext, false);
				} catch (RaplaException e) {
					Assert.fail(e.getMessage());
				}
			}
		}, buttonNr);
        
        //need to use the last event, because if you change only one appointment within the repeating
        //it will create a new appointment and add it to the end of the array.
        //Then comparing the starttimes of the nonPersistantEvent-Appointment and the PersistantEvent-Appointment
		{
			Reservation persistant = facade.getRaplaFacade().getPersistant( event );
			Appointment appOrig = event.getAppointments()[0];
			Appointment appCpy = persistant.getAppointments()[0];
			Assert.assertFalse(appOrig.matches(appCpy));
		}
		{
			facade.getCommandHistory().undo();
			Reservation persistant = facade.getRaplaFacade().getPersistant( event );
			Appointment appOrig = event.getAppointments()[0];
			Appointment appCpy = persistant.getAppointments()[0];
			Assert.assertTrue(appOrig.matches(appCpy));
		}
		{
			facade.getCommandHistory().redo();
			Reservation persistant = facade.getRaplaFacade().getPersistant( event );
			Appointment appOrig = event.getAppointments()[0];
			Appointment appCpy = persistant.getAppointments()[0];
			Assert.assertFalse(appOrig.matches(appCpy));
		}

    }
	
	
	/**
	 * Testing the delete function. 
	 * You still have to decide whether you want to delete only one appointment or the whole reservation
	 * @throws Exception
	 */
	
	//Erstellt von Jens Fritz
	public void testDeleteUndo() throws Exception{
    	final ClientFacade facade = getFacade();
		final ReservationController control = getService(ReservationController.class);

		Allocatable nonPersistantAllocatable = facade.getRaplaFacade().newResource();
        Reservation nonPersistantEvent = facade.getRaplaFacade().newReservation();
        
        //Creating Event
        createEvent(nonPersistantAllocatable, nonPersistantEvent);
        final Reservation persistantEvent = facade.getRaplaFacade().getPersistant( nonPersistantEvent );
    	int buttonNr = 1;
		executeControlAndPressButton(new Runnable() {
			
			public void run() {
				try {
					AppointmentBlock appointmentBlock = new AppointmentBlock( persistantEvent.getAppointments()[0]);
					PopupContext popupContext = createPopupContext();
                    control.deleteAppointment(appointmentBlock, popupContext);
				} catch (RaplaException e) {
					Assert.fail(e.getMessage());
				}
			}
		}, buttonNr);
        Reservation exist=null;
        try {
        	//if you deleted the whole event, it will throw an exception at this point
        	exist = facade.getRaplaFacade().getPersistant(nonPersistantEvent);
        	
        	//checks if an appointment was deleted (not used if exception is thrown)
			Assert.assertNotNull(exist.getAppointments()[0].getRepeating().getExceptions());
		} catch (EntityNotFoundException e) {
			facade.getCommandHistory().undo();
		}
        try {
        	exist = facade.getRaplaFacade().getPersistant(nonPersistantEvent);
			Assert.assertTrue(exist != null);
		} catch (EntityNotFoundException e) {
			e.printStackTrace();
		}
        facade.getCommandHistory().redo();
        try {
        	exist = facade.getRaplaFacade().getPersistant(nonPersistantEvent);
			Assert.assertNotNull(exist.getAppointments()[0].getRepeating().getExceptions());
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
			Reservation nonPersistantEvent) throws Exception {
		nonPersistantAllocatable.getClassification().setValue("name", "Bla");
		nonPersistantEvent.getClassification().setValue("name","dummy-event");
		Assert.assertEquals( "event", nonPersistantEvent.getClassification().getType().getKey());
        nonPersistantEvent.addAllocatable( nonPersistantAllocatable );
        Appointment appointment = getFacade().getRaplaFacade().newAppointment( new Date(), new Date());
        appointment.setRepeatingEnabled( true);
        appointment.getRepeating().setType(RepeatingType.DAILY);
        appointment.getRepeating().setNumber( 3 );
		nonPersistantEvent.addAppointment( appointment);
//        getFacade().newAppointment(new Date(), new Date(),RepeatingType.findForString("weekly"), 5);
        getFacade().getRaplaFacade().storeObjects( new Entity[] { nonPersistantAllocatable, nonPersistantEvent} );
        return nonPersistantEvent;
	}

	private Appointment changeTime(boolean keepTime) throws Exception {
		Date newStart = new Date();
		Date newEnd = new Date();
		if(!keepTime){
			newStart = getRaplaLocale().toTime(13, 0, 0);
			newEnd  = getRaplaLocale().toTime(13, 0, 0);
		}else{
			newStart = new Date(newStart.getTime() + DateTools.MILLISECONDS_PER_HOUR * 2);
			newEnd = new Date(newEnd.getTime() + DateTools.MILLISECONDS_PER_HOUR * 2);
		}
		Appointment retAppointment = getFacade().getRaplaFacade().newAppointment(newStart, newEnd);
		return retAppointment;
	}
}
