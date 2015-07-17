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

package org.rapla.gui.tests;
import java.util.Date;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.rapla.facade.CalendarSelectionModel;
import org.rapla.gui.internal.CalendarEditor;


public final class CalendarEditorTest extends GUITestCase
{
    public CalendarEditorTest(String name) {
        super(name);
    }

    public static Test suite() {
        return new TestSuite(CalendarEditorTest.class);
    }

    public void testShow() throws Exception {
        CalendarSelectionModel settings = getFacade().newCalendarModel(getFacade().getUser() );
        settings.setSelectedDate(new Date());
        CalendarEditor editor = new CalendarEditor(getClientService().getContext(),settings);
        testComponent(editor.getComponent(),1024,600);
        editor.start();
        //editor.listUI.treeSelection.getTree().setSelectionRow(1);
    }

/* #TODO uncomment me and make me test again
 * 
    public void testCreateException() throws Exception {
        CalendarModel settings = new CalendarModelImpl( getClientService().getContext() );
        settings.setSelectionType( Reservation.TYPE );
        settings.setSelectedDate(new Date());
        CalendarEditor editor = new CalendarEditor(getClientService().getContext(),settings);
        testComponent(editor.getComponent(),1024,600);
        editor.start();
        editor.getComponent().revalidate();
        editor.selection.treeSelection.getTree().setSelectionRow(1);
        Reservation r = (Reservation) editor.selection.treeSelection.getSelectedElement();
        //editor.getComponent().revalidate();

        //editor.calendarContainer.dateChooser.goNext();
        Date date1 = editor.getCalendarView().getStartDate();
        Reservation editableRes = (Reservation) getFacade().edit( r );
        Appointment app1 = editableRes.getAppointments()[0];
        Appointment app2= editableRes.getAppointments()[1];
        app1.getRepeating().addException( DateTools.addDays( app1.getStart(), 7 ));
        editableRes.removeAppointment( app2 );
        getFacade().store( editableRes);
        //We need to wait until the notifaction of the change
        Thread.sleep(500);
        assertEquals( date1, editor.getCalendarView().getStartDate());
        getLogger().info("WeekViewEditor started");
    }
 */

    public static void main(String[] args) {
        new CalendarEditorTest(CalendarEditorTest.class.getName()
                               ).interactiveTest("testShow");
    }
}

