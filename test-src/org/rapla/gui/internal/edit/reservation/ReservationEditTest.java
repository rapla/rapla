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

package org.rapla.gui.internal.edit.reservation;
import junit.framework.Test;
import junit.framework.TestSuite;

import org.rapla.client.ClientService;
import org.rapla.entities.domain.Reservation;
import org.rapla.gui.ReservationController;
import org.rapla.gui.ReservationEdit;
import org.rapla.gui.tests.GUITestCase;

public final class ReservationEditTest extends GUITestCase{
	
	ClientService clientService;
	Reservation[] reservations;
	ReservationController c;
	ReservationEdit window;
	ReservationEditImpl internalWindow;
	
    public ReservationEditTest(String name) {
        super(name);
    }

    public static Test suite() {
        return new TestSuite(ReservationEditTest.class);
    }
    
    public void setUp() throws Exception{
    	super.setUp();
        clientService = getClientService();
        reservations = clientService.getFacade().getReservationsForAllocatable(null,null,null,null);
        c = clientService.getContext().lookup(ReservationController.class);
        window = c.edit(reservations[0]);
        internalWindow = (ReservationEditImpl) window;
    }

    public void testAppointmentEdit() throws Exception {
        AppointmentListEdit appointmentEdit = internalWindow.appointmentEdit;
		// Deletes the second appointment
        int listSize = appointmentEdit.getListEdit().getList().getModel().getSize();
        // Wait for the swing thread to paint otherwise we get a small paint exception in the console window due to concurrency issues
        int paintDelay = 10;
		Thread.sleep( paintDelay);
        appointmentEdit.getListEdit().select(1);
        Thread.sleep( paintDelay);
        appointmentEdit.getListEdit().removeButton.doClick();
        Thread.sleep( paintDelay);
        // Check if its removed frmom the list
        int listSizeAfter = appointmentEdit.getListEdit().getList().getModel().getSize();
        assertEquals( listSize-1, listSizeAfter);
        internalWindow.commandHistory.undo();
        Thread.sleep( paintDelay);
        listSizeAfter = appointmentEdit.getListEdit().getList().getModel().getSize();
        assertEquals(listSize, listSizeAfter);
        internalWindow.commandHistory.redo();
        Thread.sleep( paintDelay);
        listSizeAfter = appointmentEdit.getListEdit().getList().getModel().getSize();
        assertEquals(listSize-1, listSizeAfter);
        appointmentEdit.getListEdit().createNewButton.doClick();
        Thread.sleep( paintDelay);
        appointmentEdit.getListEdit().createNewButton.doClick();
        Thread.sleep( paintDelay);
        listSizeAfter = appointmentEdit.getListEdit().getList().getModel().getSize();
        assertEquals(listSize+1, listSizeAfter);
        appointmentEdit.getListEdit().select(1);
        Thread.sleep( paintDelay);
        appointmentEdit.getListEdit().removeButton.doClick();
        Thread.sleep( paintDelay);
        appointmentEdit.getListEdit().select(1);
        Thread.sleep( paintDelay);
        appointmentEdit.getListEdit().removeButton.doClick();
        Thread.sleep( paintDelay);
        appointmentEdit.getListEdit().createNewButton.doClick();
        Thread.sleep( paintDelay);
        listSizeAfter = appointmentEdit.getListEdit().getList().getModel().getSize();
        assertEquals(listSize, listSizeAfter);
        internalWindow.commandHistory.undo();
        Thread.sleep( paintDelay);
        internalWindow.commandHistory.undo();
        Thread.sleep( paintDelay);
        listSizeAfter = appointmentEdit.getListEdit().getList().getModel().getSize();
        assertEquals(listSize, listSizeAfter);
    }
    
    public void testAllocatable() throws Exception{
        AllocatableSelection allocatableEdit = internalWindow.allocatableEdit;
        int firstState = getAllocatableSize(allocatableEdit);
        
        // deleting allocatables 
        allocatableEdit.selectedTable.selectAll();
        allocatableEdit.btnRemove.doClick();
        int secondState = getAllocatableSize(allocatableEdit);
        assertFalse(firstState == secondState);
        internalWindow.commandHistory.undo();
        int thirdState = getAllocatableSize(allocatableEdit);
        assertTrue(firstState == thirdState);
        
        //adding all allocatables
        allocatableEdit.completeTable.selectAll();
        allocatableEdit.btnAdd.doClick();
        int fourthState = getAllocatableSize(allocatableEdit);
        assertFalse (firstState == fourthState);
        internalWindow.commandHistory.undo();
        int fifthState = getAllocatableSize(allocatableEdit);
        assertTrue (firstState == fifthState);
        internalWindow.commandHistory.redo();
        int sixthState = getAllocatableSize(allocatableEdit);
        assertTrue (fourthState == sixthState);
    }
    
    public void testRepeatingEdit() throws Exception{
        AppointmentListEdit appointmentEdit = internalWindow.appointmentEdit;
    	//ReservationInfoEdit repeatingAndAttributeEdit = internalWindow.reservationInfo;
    	appointmentEdit.getListEdit().select(0);
    	String firstSelected = getSelectedRadioButton(appointmentEdit.getAppointmentController());
    	appointmentEdit.getAppointmentController().yearlyRepeating.doClick();
    	String secondSelected = getSelectedRadioButton(appointmentEdit.getAppointmentController());
    	assertFalse(firstSelected.equals(secondSelected));
    	internalWindow.commandHistory.undo();
    	assertEquals(firstSelected, getSelectedRadioButton(appointmentEdit.getAppointmentController()));
    	internalWindow.commandHistory.redo();
    	assertEquals("yearly", getSelectedRadioButton(appointmentEdit.getAppointmentController()));
    	appointmentEdit.getAppointmentController().noRepeating.doClick();
    	appointmentEdit.getAppointmentController().monthlyRepeating.doClick();
    	appointmentEdit.getAppointmentController().dailyRepeating.doClick();
    	internalWindow.commandHistory.undo();
    	assertEquals("monthly", getSelectedRadioButton(appointmentEdit.getAppointmentController()));
    	appointmentEdit.getAppointmentController().yearlyRepeating.doClick();
    	appointmentEdit.getAppointmentController().weeklyRepeating.doClick();
    	appointmentEdit.getAppointmentController().noRepeating.doClick();
    	internalWindow.commandHistory.undo();
    	internalWindow.commandHistory.undo();
    	internalWindow.commandHistory.redo();
    	assertEquals("weekly", getSelectedRadioButton(appointmentEdit.getAppointmentController()));

    	
    }

	private int getAllocatableSize(AllocatableSelection allocatableEdit) {
	    int count = 0;
	    for (Reservation r:allocatableEdit.mutableReservations)
        {
	        count += r.getAllocatables().length;
        }
	    return count;
	}
    
    public String getSelectedRadioButton(AppointmentController editor){
    	if(editor.dailyRepeating.isSelected())
    		return "daily";
    	if(editor.weeklyRepeating.isSelected())
    		return "weekly";
    	if(editor.monthlyRepeating.isSelected())
    		return "monthly";
    	if(editor.yearlyRepeating.isSelected())
    		return "yearly";
    	if(editor.noRepeating.isSelected())
    		return "single";
		return "false";
    }
    
    public static void main(String[] args) {
        new ReservationEditTest(ReservationEditTest.class.getName()
                               ).interactiveTest("testMain");
    }
}

