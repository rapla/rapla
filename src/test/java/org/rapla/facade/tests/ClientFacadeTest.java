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
package org.rapla.facade.tests;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.rapla.RaplaTestCase;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.SerializableDateTimeFormat;
import org.rapla.entities.DependencyException;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.ReadOnlyException;
import org.rapla.entities.User;
import org.rapla.entities.configuration.CalendarModelConfiguration;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaMap;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.Conflict;
import org.rapla.facade.ModificationModule;
import org.rapla.facade.QueryModule;
import org.rapla.facade.UserModule;
import org.rapla.facade.internal.CalendarModelImpl;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.TypedComponentRole;
import org.rapla.plugin.weekview.WeekViewFactory;

public class ClientFacadeTest extends RaplaTestCase {
    ClientFacade facade;
    Locale locale;

    public ClientFacadeTest(String name) {
        super(name);
    }

    public static Test suite() {
        return new TestSuite(ClientFacadeTest.class);
    }

    protected void setUp() throws Exception {
        super.setUp();
        facade = getContext().lookup(ClientFacade.class );
        facade.login("homer","duffs".toCharArray());
        locale = Locale.getDefault();
    }

    protected void tearDown() throws Exception {
        facade.logout();
        super.tearDown();
    }

    private Reservation findReservation(QueryModule queryMod,String name) throws RaplaException {
        Reservation[] reservations = queryMod.getReservationsForAllocatable(null,null,null,null);
        for (int i=0;i<reservations.length;i++) {
            if (reservations[i].getName(locale).equals(name))
                return reservations[i];
        }
        return null;
    }

    public void testConflicts() throws Exception {
        Conflict[] conflicts= facade.getConflicts( );
        Reservation[] all = facade.getReservationsForAllocatable(null, null, null, null);
        facade.removeObjects( all );
        Reservation orig =  facade.newReservation();
        orig.getClassification().setValue("name","new");
        Date start = DateTools.fillDate( new Date());
        Date end = getRaplaLocale().toDate( start, getRaplaLocale().toTime(  12,0,0));
        orig.addAppointment( facade.newAppointment( start, end));
        
        orig.addAllocatable( facade.getAllocatables()[0]);
       
        Reservation clone = facade.clone( orig );
        facade.store( orig );
        facade.store( clone );

        Conflict[] conflictsAfter = facade.getConflicts( );
        assertEquals( 1, conflictsAfter.length - conflicts.length );
        HashSet<Conflict> set = new HashSet<Conflict>( Arrays.asList( conflictsAfter ));

        assertTrue ( set.containsAll( new HashSet<Conflict>( Arrays.asList( conflicts ))));
        
        
    }

    // Make some Changes to the Reservation in another client
    private void changeInSecondFacade(String name) throws Exception {
        ClientFacade facade2 =  raplaContainer.lookup(ClientFacade.class ,"local-facade2");
        facade2.login("homer","duffs".toCharArray());
        UserModule userMod2 =  facade2;
        QueryModule queryMod2 =  facade2;
        ModificationModule modificationMod2 =  facade2;
        boolean bLogin = userMod2.login("homer","duffs".toCharArray());
        assertTrue(bLogin);
        Reservation reservation = findReservation(queryMod2,name);
        Reservation mutableReseravation = modificationMod2.edit(reservation);
        Appointment appointment =  mutableReseravation.getAppointments()[0];

        RaplaLocale loc = getRaplaLocale();
        Calendar cal = loc.createCalendar();
        cal.set(Calendar.DAY_OF_WEEK,Calendar.MONDAY);
        Date startTime = loc.toTime( 17,0,0);
		Date startTime1 = loc.toDate(cal.getTime(), startTime);
        Date endTime = loc.toTime( 19,0,0);
		Date endTime1 = loc.toDate(cal.getTime(), endTime);
        appointment.move(startTime1,endTime1);

        modificationMod2.store( mutableReseravation );
        //userMod2.logout();
    }

    public void testClone() throws Exception {
        ClassificationFilter filter = facade.getDynamicType("event").newClassificationFilter();
        filter.addEqualsRule("name","power planting");
        Reservation orig = facade.getReservationsForAllocatable(null, null, null, new ClassificationFilter[] { filter})[0];
        Reservation clone = facade.clone( orig );
        Appointment a = clone.getAppointments()[0];
        Date newStart = new SerializableDateTimeFormat().parseDateTime("2005-10-10","10:20:00");
        Date newEnd = new SerializableDateTimeFormat().parseDateTime("2005-10-12", null);
        a.move( newStart );
        a.getRepeating().setEnd( newEnd );
        facade.store( clone );
        Reservation[] allPowerPlantings = facade.getReservationsForAllocatable(null,  null, null, new ClassificationFilter[] { filter});
        assertEquals( 2, allPowerPlantings.length);
        Reservation[] onlyClones = facade.getReservationsForAllocatable(null,  newStart, null, new ClassificationFilter[] { filter});
        assertEquals( 1, onlyClones.length);
    }

    public void testRefresh() throws Exception {
        changeInSecondFacade("bowling");
        facade.refresh();
        Reservation resAfter = findReservation(facade,"bowling");
        Appointment appointment = resAfter.getAppointments()[0];
        Calendar cal = Calendar.getInstance(DateTools.getTimeZone());
        cal.setTime(appointment.getStart());
        assertEquals(17, cal.get(Calendar.HOUR_OF_DAY) );
        assertEquals(Calendar.MONDAY, cal.get(Calendar.DAY_OF_WEEK) );
        cal.setTime(appointment.getEnd());
        assertEquals(19, cal.get(Calendar.HOUR_OF_DAY));
        assertEquals( Calendar.MONDAY, cal.get(Calendar.DAY_OF_WEEK));
    }

    public void testExampleEdit() throws Exception {

    	String allocatableId;
    	String eventId;
    	{
    		Allocatable nonPersistantAllocatable = getFacade().newResource();
    		nonPersistantAllocatable.getClassification().setValue("name", "Bla");
    		 
    		Reservation nonPeristantEvent = getFacade().newReservation();
    		nonPeristantEvent.getClassification().setValue("name","dummy-event");
    		assertEquals( "event", nonPeristantEvent.getClassification().getType().getKey());
    		nonPeristantEvent.addAllocatable( nonPersistantAllocatable );
    		nonPeristantEvent.addAppointment( getFacade().newAppointment( new Date(), new Date()));
    		getFacade().storeObjects( new Entity[] { nonPersistantAllocatable, nonPeristantEvent} );
    		allocatableId = nonPersistantAllocatable.getId();
    		eventId = nonPeristantEvent.getId();
    	}
    	Allocatable allocatable = facade.edit(facade.getOperator().resolve(allocatableId, Allocatable.class) );
    	
        // Store the allocatable it a second time to test if it is still modifiable after storing
        allocatable.getClassification().setValue("name", "Blubs");
        getFacade().store( allocatable );

        // query the allocatable from the store
        ClassificationFilter filter = getFacade().getDynamicType("room").newClassificationFilter();
        filter.addEqualsRule("name","Blubs");
        Allocatable persistantAllocatable = getFacade().getAllocatables( new ClassificationFilter[] { filter} )[0];

        // query the event from the store
        ClassificationFilter eventFilter = getFacade().getDynamicType("event").newClassificationFilter();
        eventFilter.addEqualsRule("name","dummy-event");
        Reservation persistantEvent = getFacade().getReservationsForAllocatable( null, null, null,new ClassificationFilter[] { eventFilter} )[0];
        // Another way to get the persistant event would have been
        //Reservation persistantEvent = getFacade().getPersistant( nonPeristantEvent );

        // test if the ids of editable Versions are equal to the persistant ones
        assertEquals( persistantAllocatable, allocatable);
        Reservation event = facade.getOperator().resolve( eventId, Reservation.class);
		assertEquals( persistantEvent, event);
        assertEquals( persistantEvent.getAllocatables()[0], event.getAllocatables()[0]);

//        // Check if the modifiable/original versions are different to the persistant versions
//        assertTrue( persistantAllocatable !=  allocatable );
//        assertTrue( persistantEvent !=  event );
//        assertTrue( persistantEvent.getAllocatables()[0] != event.getAllocatables()[0]);

        // Test the read only constraints
        try {
            persistantAllocatable.getClassification().setValue("name","asdflkj");
            fail("ReadOnlyException should have been thrown");
        } catch (ReadOnlyException ex) {
        }

        try {
            persistantEvent.getClassification().setValue("name","dummy-event");
            fail("ReadOnlyException should have been thrown");
        } catch (ReadOnlyException ex) {
        }

        try {
            persistantEvent.removeAllocatable( allocatable);
            fail("ReadOnlyException should have been thrown");
        } catch (ReadOnlyException ex) {
        }

        // now we get a second edit copy of the event
        Reservation nonPersistantEventVersion2 =  getFacade().edit( persistantEvent);
        assertTrue( nonPersistantEventVersion2 !=  event );

        // Both allocatables are persitant, so they have the same reference
        assertTrue( persistantEvent.getAllocatables()[0] == nonPersistantEventVersion2.getAllocatables()[0]);
    }

    public void testLogin() throws Exception {
        ClientFacade facade2 = raplaContainer.lookup(ClientFacade.class , "local-facade2");
        assertEquals(false, facade2.login("non_existant_user","".toCharArray()));
        assertEquals(false, facade2.login("non_existant_user","fake".toCharArray()));
        assertTrue(facade2.login("homer","duffs".toCharArray()));
        assertEquals("homer",facade2.getUser().getUsername());
        facade.logout();
    }

    public void testSavePreferences() throws Exception {
        ClientFacade facade2 = raplaContainer.lookup(ClientFacade.class , "local-facade2");
        assertTrue(facade2.login("monty","burns".toCharArray()));
        Preferences prefs = facade.edit( facade.getPreferences() );
        facade2.store( prefs );
        facade2.logout();
    }

    public void testNewUser() throws RaplaException {
        User newUser = facade.newUser();
        newUser.setUsername("newUser");
        try {
            facade.getPreferences(newUser);
            fail( "getPreferences should throw an Exception for non existant user");
        } catch (EntityNotFoundException ex ){
        }
        facade.store( newUser );
        Preferences prefs =  facade.edit( facade.getPreferences(newUser) );
        facade.store( prefs );
    }

    public void testPreferenceDependencies() throws RaplaException {
        Allocatable allocatable = facade.newResource();
        facade.store( allocatable);

        CalendarSelectionModel calendar = facade.newCalendarModel(facade.getUser() );
        calendar.setSelectedObjects( Collections.singleton( allocatable));
        calendar.setViewId( WeekViewFactory.WEEK_VIEW);
        CalendarModelConfiguration config = ((CalendarModelImpl)calendar).createConfiguration();

        RaplaMap<CalendarModelConfiguration> calendarList = facade.newRaplaMap( Collections.singleton( config ));

        Preferences preferences = facade.getPreferences();
        Preferences editPref  =  facade.edit( preferences );
        TypedComponentRole<RaplaMap<CalendarModelConfiguration>> TEST_ENTRY  = new TypedComponentRole<RaplaMap<CalendarModelConfiguration>>("TEST");
        editPref.putEntry(TEST_ENTRY, calendarList );
        facade.store( editPref );
        try {
            facade.remove( allocatable );
            fail("DependencyException should have thrown");
        } catch (DependencyException ex) {
        }

        calendarList = facade.newRaplaMap( new ArrayList<CalendarModelConfiguration> ());
        editPref  = facade.edit( preferences );
        editPref.putEntry( TEST_ENTRY, calendarList );
        facade.store( editPref );

        facade.remove( allocatable );
    }

    public void testResourcesNotEmpty() throws RaplaException {
        Allocatable[] resources = facade.getAllocatables(null);
        assertTrue(resources.length > 0);
    }

    void printConflicts(Conflict[] c) {
        System.out.println(c.length + " Conflicts:");
        for (int i=0;i<c.length;i++) {
            printConflict(c[i]);
        }
    }
    void printConflict(Conflict c) {
        System.out.println("Conflict: " + c.getAppointment1()
                           + " with " + c.getAppointment2());
        System.out.println("          " + c.getAllocatable().getName(locale)) ;
        System.out.println("          " + c.getAppointment1() + " overlapps " + c.getAppointment2());
    }



}





