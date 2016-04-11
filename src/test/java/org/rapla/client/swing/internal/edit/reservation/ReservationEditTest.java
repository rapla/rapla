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

package org.rapla.client.swing.internal.edit.reservation;

import java.util.Collection;

import org.junit.Assert;
import org.rapla.client.ReservationController;
import org.rapla.client.ReservationEdit;
import org.rapla.client.swing.gui.tests.GUITestCase;
import org.rapla.entities.domain.Reservation;
import org.rapla.server.PromiseSynchroniser;

public final class ReservationEditTest extends GUITestCase{


	Collection<Reservation> reservations;
	ReservationController c;
	ReservationEdit window;
	ReservationEditImpl internalWindow;
	
    public void setUp() throws Exception{
        reservations = PromiseSynchroniser.waitForWithRaplaException(getFacade().getRaplaFacade().getReservationsForAllocatable(null,null,null,null), 10000);
        c = null;//clientService.getContext().lookup(ReservationController.class);
        // FIXME
//        window = c.edit(reservations.iterator().next());
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
        Assert.assertEquals(listSize - 1, listSizeAfter);
        internalWindow.commandHistory.undo();
        Thread.sleep( paintDelay);
        listSizeAfter = appointmentEdit.getListEdit().getList().getModel().getSize();
        Assert.assertEquals(listSize, listSizeAfter);
        internalWindow.commandHistory.redo();
        Thread.sleep( paintDelay);
        listSizeAfter = appointmentEdit.getListEdit().getList().getModel().getSize();
        Assert.assertEquals(listSize - 1, listSizeAfter);
        appointmentEdit.getListEdit().createNewButton.doClick();
        Thread.sleep( paintDelay);
        appointmentEdit.getListEdit().createNewButton.doClick();
        Thread.sleep( paintDelay);
        listSizeAfter = appointmentEdit.getListEdit().getList().getModel().getSize();
        Assert.assertEquals(listSize + 1, listSizeAfter);
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
        Assert.assertEquals(listSize, listSizeAfter);
        internalWindow.commandHistory.undo();
        Thread.sleep( paintDelay);
        internalWindow.commandHistory.undo();
        Thread.sleep( paintDelay);
        listSizeAfter = appointmentEdit.getListEdit().getList().getModel().getSize();
        Assert.assertEquals(listSize, listSizeAfter);
    }
    
    public void testAllocatable() throws Exception{
        AllocatableSelection allocatableEdit = internalWindow.allocatableEdit;
        int firstState = getAllocatableSize(allocatableEdit);
        
        // deleting allocatables 
        allocatableEdit.selectedTable.selectAll();
        allocatableEdit.btnRemove.doClick();
        int secondState = getAllocatableSize(allocatableEdit);
        Assert.assertFalse(firstState == secondState);
        internalWindow.commandHistory.undo();
        int thirdState = getAllocatableSize(allocatableEdit);
        Assert.assertTrue(firstState == thirdState);
        
        //adding all allocatables
        allocatableEdit.completeTable.selectAll();
        allocatableEdit.btnAdd.doClick();
        int fourthState = getAllocatableSize(allocatableEdit);
        Assert.assertFalse(firstState == fourthState);
        internalWindow.commandHistory.undo();
        int fifthState = getAllocatableSize(allocatableEdit);
        Assert.assertTrue(firstState == fifthState);
        internalWindow.commandHistory.redo();
        int sixthState = getAllocatableSize(allocatableEdit);
        Assert.assertTrue(fourthState == sixthState);
    }
    
    public void testRepeatingEdit() throws Exception{
        AppointmentListEdit appointmentEdit = internalWindow.appointmentEdit;
    	//ReservationInfoEdit repeatingAndAttributeEdit = internalWindow.reservationInfo;
    	appointmentEdit.getListEdit().select(0);
    	String firstSelected = getSelectedRadioButton(appointmentEdit.getAppointmentController());
    	appointmentEdit.getAppointmentController().yearlyRepeating.doClick();
    	String secondSelected = getSelectedRadioButton(appointmentEdit.getAppointmentController());
        Assert.assertFalse(firstSelected.equals(secondSelected));
    	internalWindow.commandHistory.undo();
        Assert.assertEquals(firstSelected, getSelectedRadioButton(appointmentEdit.getAppointmentController()));
    	internalWindow.commandHistory.redo();
        Assert.assertEquals("yearly", getSelectedRadioButton(appointmentEdit.getAppointmentController()));
    	appointmentEdit.getAppointmentController().noRepeating.doClick();
    	appointmentEdit.getAppointmentController().monthlyRepeating.doClick();
    	appointmentEdit.getAppointmentController().dailyRepeating.doClick();
    	internalWindow.commandHistory.undo();
        Assert.assertEquals("monthly", getSelectedRadioButton(appointmentEdit.getAppointmentController()));
    	appointmentEdit.getAppointmentController().yearlyRepeating.doClick();
    	appointmentEdit.getAppointmentController().weeklyRepeating.doClick();
    	appointmentEdit.getAppointmentController().noRepeating.doClick();
    	internalWindow.commandHistory.undo();
    	internalWindow.commandHistory.undo();
    	internalWindow.commandHistory.redo();
    	Assert.assertEquals("weekly", getSelectedRadioButton(appointmentEdit.getAppointmentController()));

    	
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
        new ReservationEditTest().interactiveTest("testMain");
    }
}

