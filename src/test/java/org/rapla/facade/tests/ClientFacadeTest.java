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
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.components.i18n.internal.DefaultBundleManager;
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
import org.rapla.entities.configuration.internal.CalendarModelConfigurationImpl;
import org.rapla.entities.configuration.internal.RaplaMapImpl;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.ClassificationFilterRule;
import org.rapla.entities.dynamictype.ConstraintIds;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.dynamictype.internal.ClassificationFilterImpl;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.Conflict;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.internal.CalendarModelImpl;
import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;
import org.rapla.framework.internal.RaplaLocaleImpl;
import org.rapla.plugin.weekview.WeekviewPlugin;
import org.rapla.scheduler.Promise;
import org.rapla.server.PromiseSynchroniser;
import org.rapla.test.util.RaplaTestCase;

@RunWith(JUnit4.class)
public class ClientFacadeTest  {
    ClientFacade clientFacade;
    RaplaFacade facade;
    Locale locale;

    @Before
    public void setUp() throws Exception {
        clientFacade = RaplaTestCase.createSimpleSimpsonsWithHomer();
        facade = clientFacade.getRaplaFacade();
        locale = Locale.getDefault();


    }


    @After
    public void tearDown() throws Exception {
        clientFacade.logout();
    }

    @Test
    public void testBelongsTo() throws Exception
    {
        Promise<Collection<Reservation>> all = facade.getReservationsForAllocatable(null, null, null, null);
        facade.removeObjects( PromiseSynchroniser.waitForWithRaplaException(all, 10000).toArray(Reservation.RESERVATION_ARRAY) );
        Reservation orig =  facade.newReservation();
        final DynamicType resourceType = facade.getDynamicType("room");
        final Allocatable parentResource = facade.getAllocatables(resourceType.newClassificationFilter().toArray())[0];
        final User user = clientFacade.getUser();
        final DynamicType resourceBelongsTo = facade.newDynamicType(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE);
        resourceBelongsTo.setKey("roompart");
        resourceBelongsTo.getName().setName("en","Roompart");
        final Attribute attribute = facade.newAttribute(AttributeType.ALLOCATABLE);
        attribute.setKey("belongsTo");
        attribute.setConstraint(ConstraintIds.KEY_BELONGS_TO, "true");
        attribute.setConstraint(ConstraintIds.KEY_DYNAMIC_TYPE, resourceType);
        resourceBelongsTo.addAttribute( attribute );
        facade.store( resourceBelongsTo);
        final Allocatable childResource;
        {
            final Classification classification = resourceBelongsTo.newClassification();
            classification.setValue("belongsTo", parentResource);
            classification.setValue("name","erwin_left");
            final Allocatable resourceChild = facade.newAllocatable(classification, user);
            facade.store( resourceChild);
            childResource = facade.getPersistant( resourceChild);
        }
        final Allocatable childChildResource;
        {
            final Classification classification = resourceBelongsTo.newClassification();
            classification.setValue("belongsTo", childResource);
            classification.setValue("name","erwin_left_left");
            final Allocatable resourceChild = facade.newAllocatable(classification, user);
            facade.store( resourceChild);
            childChildResource = facade.getPersistant( resourceChild);
        }

        orig.getClassification().setValue("name", "new");
        Date start = DateTools.toDateTime(new Date(), new Date(DateTools.toTime(10, 0, 0)));
        Date end = DateTools.toDateTime( start,new Date(DateTools.toTime(  12,0,0)));
        orig.addAppointment( facade.newAppointment( start, end));

        orig.addAllocatable(parentResource);
        facade.store(orig);
        // simply clone the event
        Reservation newEvent;
        {
            newEvent = facade.clone(orig, user);
            Appointment firstAppointment = newEvent.getAppointments()[0];
            final Collection<Allocatable> allocatables = Arrays.asList(newEvent.getAllocatables());
            final Promise<Map<Allocatable, Collection<Appointment>>> allocatableBindings = facade
                    .getAllocatableBindings(allocatables, Arrays.asList(newEvent.getAppointments()));
            final Map<Allocatable, Collection<Appointment>> allocatableCollectionMap = PromiseSynchroniser.waitForWithRaplaException(allocatableBindings, 10000);
            for (Allocatable allocatable : allocatables)
            {
                final Collection<Appointment> allocatedAppointments = new HashSet(allocatableCollectionMap.get(allocatable));
                Assert.assertTrue(allocatedAppointments.contains(firstAppointment));
                Assert.assertEquals(1, allocatedAppointments.size());
            }
            facade.store(newEvent);
        }
        // clone the event and add a second appointment one day later
        {
            newEvent = facade.clone(newEvent, user);
            Appointment firstAppointment = newEvent.getAppointments()[0];
            final Appointment newAppointment = facade.newAppointment(DateTools.addDay(start), DateTools.addDay(end));
            newEvent.addAppointment(newAppointment);
            final Collection<Allocatable> allocatables = Arrays.asList(newEvent.getAllocatables());
            {
                final Promise<Map<Allocatable, Collection<Appointment>>> allocatableBindings = facade
                        .getAllocatableBindings(allocatables, Arrays.asList(newEvent.getAppointments()));
                final Map<Allocatable, Collection<Appointment>> allocatableCollectionMap = PromiseSynchroniser.waitForWithRaplaException(allocatableBindings, 10000);
                for (Allocatable allocatable : allocatables)
                {
                    final Collection<Appointment> allocatedAppointments = new HashSet(allocatableCollectionMap.get(allocatable));
                    Assert.assertTrue(allocatedAppointments.contains(firstAppointment));
                    Assert.assertEquals(1, allocatedAppointments.size());
                }
            }
            firstAppointment.move( newAppointment.getEnd());
            {
                final Promise<Map<Allocatable, Collection<Appointment>>> allocatableBindings = facade
                        .getAllocatableBindings(allocatables, Arrays.asList(newEvent.getAppointments()));
                final Map<Allocatable, Collection<Appointment>> allocatableCollectionMap = PromiseSynchroniser.waitForWithRaplaException(allocatableBindings, 10000);

                for (Allocatable allocatable : allocatables)
                {
                    final Collection<Appointment> allocatedAppointments = new HashSet(allocatableCollectionMap.get(allocatable));
                    Assert.assertEquals(0, allocatedAppointments.size());
                }
            }
        }
        // now test if the child resource also returns bindings
        {
            newEvent = facade.clone(orig, user);
            newEvent.removeAllocatable( parentResource);
            newEvent.addAllocatable(childResource);
            Appointment firstAppointment = newEvent.getAppointments()[0];
            final Collection<Allocatable> allocatables = Arrays.asList(newEvent.getAllocatables());
            final Promise<Map<Allocatable, Collection<Appointment>>> allocatableBindings = facade
                    .getAllocatableBindings(allocatables, Arrays.asList(newEvent.getAppointments()));
            final Map<Allocatable, Collection<Appointment>> allocatableCollectionMap = PromiseSynchroniser.waitForWithRaplaException(allocatableBindings, 10000);
            for (Allocatable allocatable : allocatables)
            {
                final Collection<Appointment> allocatedAppointments = new HashSet(allocatableCollectionMap.get(allocatable));
                Assert.assertTrue(allocatedAppointments.contains(firstAppointment));
                Assert.assertEquals(1, allocatedAppointments.size());
            }
        }
        // now test transitive
        {
            newEvent = facade.clone(orig, user);
            newEvent.removeAllocatable( parentResource);
            newEvent.addAllocatable(childChildResource);
            Appointment firstAppointment = newEvent.getAppointments()[0];
            final Collection<Allocatable> allocatables = Arrays.asList(newEvent.getAllocatables());
            final Promise<Map<Allocatable, Collection<Appointment>>> allocatableBindings = facade
                    .getAllocatableBindings(allocatables, Arrays.asList(newEvent.getAppointments()));
            final Map<Allocatable, Collection<Appointment>> allocatableCollectionMap = PromiseSynchroniser.waitForWithRaplaException(allocatableBindings, 10000);
            for (Allocatable allocatable : allocatables)
            {
                final Collection<Appointment> allocatedAppointments = new HashSet(allocatableCollectionMap.get(allocatable));
                Assert.assertTrue(allocatedAppointments.contains(firstAppointment));
                Assert.assertEquals(1, allocatedAppointments.size());
            }
            // and store the event
            facade.store(newEvent);
        }
        {// now check from parent
            final Appointment firstAppointment = orig.getAppointments()[0];
            final Collection<Allocatable> allocatables = Arrays.asList(orig.getAllocatables());
            final Promise<Map<Allocatable, Collection<Appointment>>> allocatableBindings = facade
                    .getAllocatableBindings(allocatables, Arrays.asList(firstAppointment));
            final Map<Allocatable, Collection<Appointment>> allocatableCollectionMap = PromiseSynchroniser.waitForWithRaplaException(allocatableBindings, 10000);
            for (Allocatable allocatable : allocatables)
            {
                final Collection<Appointment> allocatedAppointments = new HashSet(allocatableCollectionMap.get(allocatable));
                Assert.assertTrue(allocatedAppointments.contains(firstAppointment));
                Assert.assertEquals(1, allocatedAppointments.size());
            }
        }
        {// check that your own booking is shown
            // first remove the new stored event
            facade.remove(newEvent);
            // now add the childChildResource to parent and store it
            final Reservation edit = facade.edit(orig);
            edit.addAllocatable(childChildResource);
            facade.store(edit);
            final Appointment firstAppointment = orig.getAppointments()[0];
            final Collection<Allocatable> allocatables = Arrays.asList(orig.getAllocatables());
            final Promise<Map<Allocatable, Collection<Appointment>>> allocatableBindings = facade
                    .getAllocatableBindings(allocatables, Arrays.asList(firstAppointment));
            final Map<Allocatable, Collection<Appointment>> allocatableCollectionMap = PromiseSynchroniser.waitForWithRaplaException(allocatableBindings, 10000);
            for (Allocatable allocatable : allocatables)
            {
                final Collection<Appointment> allocatedAppointments = new HashSet(allocatableCollectionMap.get(allocatable));
                Assert.assertTrue(allocatedAppointments.contains(firstAppointment));
                Assert.assertEquals(1, allocatedAppointments.size());
            }
        }
    }
    
    @Test
    public void testGroup() throws Exception
    {
        // set up model
        final DynamicType newDynamicType = facade.newDynamicType("package");
        final Attribute nameAttribute = facade.newAttribute(AttributeType.STRING);
        nameAttribute.setKey("name");
        final Attribute packageAttribute = facade.newAttribute(AttributeType.ALLOCATABLE);
        packageAttribute.setKey("package");
        newDynamicType.getName().setName("en", "packageType");
        newDynamicType.addAttribute(nameAttribute);
        newDynamicType.addAttribute(packageAttribute);
        packageAttribute.setConstraint(ConstraintIds.KEY_PACKAGE, Boolean.TRUE);
        facade.store(newDynamicType);
        // create example package
        final Classification packageClassification = newDynamicType.newClassification();
        final User user = clientFacade.getUser();
        final Allocatable packageAllocatable = facade.newAllocatable(packageClassification, user);
        packageClassification.setValue(nameAttribute, "test package");
        final Allocatable[] allocatables = facade.getAllocatables();
        Collection<Allocatable> referencedAllocatables = new ArrayList<Allocatable>();
        final Allocatable alloc1 = allocatables[0];
        referencedAllocatables.add(alloc1);
        final Allocatable alloc2 = allocatables[1];
        referencedAllocatables.add(alloc2);
        packageClassification.setValues(packageAttribute, referencedAllocatables);
        facade.store(packageAllocatable);
        // check allocationBindings
        Classification classification = facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION)[0].newClassification();
        final Reservation reservation1 = facade.newReservation(classification, user);
        {
            reservation1.addAppointment(facade.newAppointment(DateTools.toDateTime(new Date(), new Date(DateTools.toTime(10, 00, 00))), DateTools.toDateTime(new Date(), new Date(DateTools.toTime(12, 00, 00)))));
            reservation1.addAllocatable(alloc1);
            {
                Collection<Allocatable> allocatablesFromAppointment = Arrays.asList(reservation1.getAllocatables()[0]);
                Collection<Appointment> forAppointment = Arrays.asList(reservation1.getAppointmentsFor(allocatablesFromAppointment.iterator().next()));
                final Promise<Map<Allocatable, Collection<Appointment>>> allocatableBindings = facade.getAllocatableBindings(allocatablesFromAppointment, forAppointment);
                final Map<Allocatable, Collection<Appointment>> result = PromiseSynchroniser.waitForWithRaplaException(allocatableBindings, 10000);
                for (Allocatable allocatable : allocatablesFromAppointment)
                {
                    final Collection<Appointment> collection = result.get(allocatable);
                    Assert.assertEquals(0, collection.size());
                }
            }
            facade.store(reservation1);
        }
        final Reservation clone ;
        {// clone second with group booked
            clone = facade.clone(reservation1, user);
            clone.removeAllocatable(alloc1);
            clone.addAllocatable(packageAllocatable);
            {
                Collection<Allocatable> allocatablesFromAppointment = Arrays.asList(clone.getAllocatables()[0]);
                Collection<Appointment> forAppointment = Arrays.asList(clone.getAppointmentsFor(allocatablesFromAppointment.iterator().next()));
                final Promise<Map<Allocatable, Collection<Appointment>>> allocatableBindings = facade.getAllocatableBindings(allocatablesFromAppointment, forAppointment);
                final Map<Allocatable, Collection<Appointment>> result = PromiseSynchroniser.waitForWithRaplaException(allocatableBindings, 10000);
                for (Allocatable allocatable : allocatablesFromAppointment)
                {
                    final Collection<Appointment> collection = result.get(allocatable);
                    Assert.assertEquals(1, collection.size());
                }
            }
            facade.store(clone);
        }
        {// check other direction
            Collection<Allocatable> allocatablesFromAppointment = Arrays.asList(reservation1.getAllocatables()[0]);
            Collection<Appointment> forAppointment = Arrays.asList(reservation1.getAppointmentsFor(allocatablesFromAppointment.iterator().next()));
            final Promise<Map<Allocatable, Collection<Appointment>>> allocatableBindings = facade.getAllocatableBindings(allocatablesFromAppointment, forAppointment);
            final Map<Allocatable, Collection<Appointment>> result = PromiseSynchroniser.waitForWithRaplaException(allocatableBindings, 10000);
            for (Allocatable allocatable : allocatablesFromAppointment)
            {
                final Collection<Appointment> collection = result.get(allocatable);
                Assert.assertEquals(1, collection.size());
            }
        }
        {// remove other so clone has no conflict any more
            facade.remove(reservation1);
            Collection<Allocatable> allocatablesFromAppointment = Arrays.asList(clone.getAllocatables()[0]);
            Collection<Appointment> forAppointment = Arrays.asList(clone.getAppointmentsFor(allocatablesFromAppointment.iterator().next()));
            final Promise<Map<Allocatable, Collection<Appointment>>> allocatableBindings = facade.getAllocatableBindings(allocatablesFromAppointment, forAppointment);
            final Map<Allocatable, Collection<Appointment>> result = PromiseSynchroniser.waitForWithRaplaException(allocatableBindings, 10000);
            for (Allocatable allocatable : allocatablesFromAppointment)
            {
                final Collection<Appointment> collection = result.get(allocatable);
                Assert.assertEquals(0, collection.size());
            }
        }
    }
    
    @Test
    public void testConflicts() throws Exception {
        Collection<Conflict> conflicts= facade.getConflicts( );
        Promise<Collection<Reservation>> all = facade.getReservationsForAllocatable(null, null, null, null);
        facade.removeObjects( PromiseSynchroniser.waitForWithRaplaException(all, 10000).toArray(Reservation.RESERVATION_ARRAY) );
        Reservation orig =  facade.newReservation();
        orig.getClassification().setValue("name","new");
        Date start = DateTools.toDateTime(new Date(), new Date(DateTools.toTime(10, 0, 0)));
        Date end = DateTools.toDateTime( start,new Date(DateTools.toTime(  12,0,0)));
        orig.addAppointment( facade.newAppointment( start, end));

        orig.addAllocatable( facade.getAllocatables()[0]);
        facade.store(orig);
        Reservation newEvent;
        {
            newEvent = facade.clone(orig, clientFacade.getUser());
            Appointment firstAppointment = newEvent.getAppointments()[0];
            final Collection<Allocatable> allocatables = Arrays.asList(newEvent.getAllocatables());
            final Promise<Map<Allocatable, Collection<Appointment>>> allocatableBindings = facade
                    .getAllocatableBindings(allocatables, Arrays.asList(newEvent.getAppointments()));
            final Map<Allocatable, Collection<Appointment>> allocatableCollectionMap = PromiseSynchroniser.waitForWithRaplaException(allocatableBindings, 10000);
            facade.store(newEvent);
        }
        Collection<Conflict> conflictsAfter = facade.getConflicts( );
        Assert.assertEquals(1, conflictsAfter.size() - conflicts.size());
        HashSet<Conflict> set = new HashSet<Conflict>( conflictsAfter );

        Assert.assertTrue(set.containsAll(new HashSet<Conflict>(conflicts)));
    }


    @Test
    public void testClone() throws Exception {
        ClassificationFilter filter = facade.getDynamicType("event").newClassificationFilter();
        filter.addEqualsRule("name","power planting");
        final Promise<Collection<Reservation>> reservationsForAllocatablePromise = facade.getReservationsForAllocatable(null, null, null, new ClassificationFilter[] { filter});
        Reservation orig = PromiseSynchroniser.waitForWithRaplaException(reservationsForAllocatablePromise, 10000).iterator().next();
        Reservation clone = facade.clone( orig, clientFacade.getUser() );
        Appointment a = clone.getAppointments()[0];
        Date newStart = new SerializableDateTimeFormat().parseDateTime("2005-10-10","10:20:00");
        Date newEnd = new SerializableDateTimeFormat().parseDateTime("2005-10-12", null);
        a.move( newStart );
        a.getRepeating().setEnd( newEnd );
        facade.store( clone );
        Collection<Reservation> allPowerPlantings = PromiseSynchroniser.waitForWithRaplaException(facade.getReservationsForAllocatable(null,  null, null, new ClassificationFilter[] { filter}), 10000);
        Assert.assertEquals(2, allPowerPlantings.size());
        Collection<Reservation> onlyClones = PromiseSynchroniser.waitForWithRaplaException(facade.getReservationsForAllocatable(null,  newStart, null, new ClassificationFilter[] { filter}), 10000);
        Assert.assertEquals(1, onlyClones.size());
    }



    @Test
    public void testExampleEdit() throws Exception {

    	String allocatableId;
    	String eventId;
    	{
    		Allocatable nonPersistantAllocatable = facade.newResource();
    		nonPersistantAllocatable.getClassification().setValue("name", "Bla");
    		 
    		Reservation nonPeristantEvent = facade.newReservation();
    		nonPeristantEvent.getClassification().setValue("name", "dummy-event");
    		Assert.assertEquals("event", nonPeristantEvent.getClassification().getType().getKey());
    		nonPeristantEvent.addAllocatable( nonPersistantAllocatable );
    		nonPeristantEvent.addAppointment( facade.newAppointment(new Date(), new Date()));
    		facade.storeObjects(new Entity[] { nonPersistantAllocatable, nonPeristantEvent });
    		allocatableId = nonPersistantAllocatable.getId();
    		eventId = nonPeristantEvent.getId();
    	}
    	Allocatable allocatable = facade.edit(facade.getOperator().resolve(allocatableId, Allocatable.class) );
    	
        // Store the allocatable it a second time to test if it is still modifiable after storing
        allocatable.getClassification().setValue("name", "Blubs");
        facade.store(allocatable);

        // query the allocatable from the store
        ClassificationFilter filter = facade.getDynamicType("room").newClassificationFilter();
        filter.addEqualsRule("name","Blubs");
        Allocatable persistantAllocatable = facade.getAllocatables(new ClassificationFilter[] { filter })[0];

        // query the event from the store
        ClassificationFilter eventFilter = facade.getDynamicType("event").newClassificationFilter();
        eventFilter.addEqualsRule("name","dummy-event");
        Reservation persistantEvent = PromiseSynchroniser.waitForWithRaplaException(facade.getReservationsForAllocatable(null, null, null, new ClassificationFilter[] { eventFilter }),10000).iterator().next();
        // Another way to get the persistant event would have been
        //Reservation persistantEvent = facade.getPersistant( nonPeristantEvent );

        // test if the ids of editable Versions are equal to the persistant ones
        Assert.assertEquals(persistantAllocatable, allocatable);
        Reservation event = facade.getOperator().resolve( eventId, Reservation.class);
		Assert.assertEquals(persistantEvent, event);
        Assert.assertEquals(persistantEvent.getAllocatables()[0], event.getAllocatables()[0]);

//        // Check if the modifiable/original versions are different to the persistant versions
//        Assert.assertTrue( persistantAllocatable !=  allocatable );
//        Assert.assertTrue( persistantEvent !=  event );
//        Assert.assertTrue( persistantEvent.getAllocatables()[0] != event.getAllocatables()[0]);

        // Test the read only constraints
        try {
            persistantAllocatable.getClassification().setValue("name","asdflkj");
            Assert.fail("ReadOnlyException should have been thrown");
        } catch (ReadOnlyException ex) {
        }

        try {
            persistantEvent.getClassification().setValue("name","dummy-event");
            Assert.fail("ReadOnlyException should have been thrown");
        } catch (ReadOnlyException ex) {
        }

        try {
            persistantEvent.removeAllocatable( allocatable);
            Assert.fail("ReadOnlyException should have been thrown");
        } catch (ReadOnlyException ex) {
        }

        // now we get a second edit copy of the event
        Reservation nonPersistantEventVersion2 =  facade.edit(persistantEvent);
        Assert.assertTrue(nonPersistantEventVersion2 != event);

        // Both allocatables are persitant, so they have the same reference
        Assert.assertTrue(persistantEvent.getAllocatables()[0] == nonPersistantEventVersion2.getAllocatables()[0]);
    }



    @Test
    public void testNewUser() throws RaplaException {
        User newUser = facade.newUser();
        newUser.setUsername("newUser");
        try {
            facade.getPreferences(newUser);
            Assert.fail("getPreferences should throw an Exception for non existant user");
        } catch (EntityNotFoundException ex ){
        }
        facade.store( newUser );
        Preferences prefs =  facade.edit( facade.getPreferences(newUser) );
        facade.store( prefs );
    }

    @Test
    public void testPreferenceDependencies() throws RaplaException {
        Allocatable allocatable = facade.newResource();
        facade.store( allocatable);

        CalendarSelectionModel calendar = facade.newCalendarModel(clientFacade.getUser() );
        calendar.setSelectedObjects( Collections.singleton( allocatable));
        calendar.setViewId( WeekviewPlugin.WEEK_VIEW);
        CalendarModelConfiguration config = ((CalendarModelImpl)calendar).createConfiguration();

        RaplaMap<CalendarModelConfiguration> calendarList = facade.newRaplaMap( Collections.singleton( config ));

        Preferences preferences = facade.getPreferences();
        Preferences editPref  =  facade.edit( preferences );
        TypedComponentRole<RaplaMap<CalendarModelConfiguration>> TEST_ENTRY  = new TypedComponentRole<RaplaMap<CalendarModelConfiguration>>("TEST");
        editPref.putEntry(TEST_ENTRY, calendarList );
        facade.store( editPref );
        try {
            facade.remove( allocatable );
            Assert.fail("DependencyException should have thrown");
        } catch (DependencyException ex) {
        }

        calendarList = facade.newRaplaMap( new ArrayList<CalendarModelConfiguration> ());
        editPref  = facade.edit( preferences );
        editPref.putEntry( TEST_ENTRY, calendarList );
        facade.store( editPref );

        facade.remove( allocatable );
    }

    @Test
    public void testResourcesNotEmpty() throws RaplaException {
        Allocatable[] resources = facade.getAllocatables(null);
        Assert.assertTrue(resources.length > 0);
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
    
    @Test
    public void testMerge() throws Exception
    {
        // set up model
        final User user = clientFacade.getUser();
        final Allocatable allocatableWinsMerge;
        final Allocatable allocatableWillBeMerge;
        final DynamicType dynamicType;
        {
            dynamicType = facade.newDynamicType("testDynamicTypeForMergeTest");
            final Attribute newAttribute = facade.newAttribute(AttributeType.STRING);
            newAttribute.setKey("name");
            dynamicType.addAttribute(newAttribute);
            dynamicType.getName().setName("en", "testDynamicTypeForMergeTest");
            facade.store(dynamicType);
        }
        {
            allocatableWinsMerge = facade.newAllocatable(dynamicType.newClassification(), user);
            allocatableWinsMerge.getClassification().setValue("name", "AllocatbaleWinsMerge");
            facade.store(allocatableWinsMerge);
        }
        {
            allocatableWillBeMerge = facade.newAllocatable(dynamicType.newClassification(), user);
            allocatableWillBeMerge.getClassification().setValue("name", "AllocatableWillBeMerge");
            facade.store(allocatableWillBeMerge);
        }
        final ReferenceInfo<Reservation> reservationReference;
        {// create reservation with two appointments holding each on of the allocatable, and one holding both
            final Reservation reservation = facade.newReservation(facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION)[0].newClassification(), user);
            reservationReference = reservation.getReference();
            reservation.addAllocatable(allocatableWillBeMerge);
            reservation.addAllocatable(allocatableWinsMerge);
            {// first appointment holding allocatableWinsMerge
                final Date today = DateTools.cutDate(new Date(System.currentTimeMillis()));
                final Date startAppointment = DateTools.toDateTime(today, new Date(DateTools.toTime(10, 00, 00)));
                final Date endAppointment = DateTools.toDateTime(today, new Date(DateTools.toTime(12, 00, 00)));
                final Appointment newAppointment = facade.newAppointment(startAppointment, endAppointment);
                reservation.addAppointment(newAppointment);
                reservation.setRestriction(newAppointment, new Allocatable[]{allocatableWinsMerge});
            }
            {// second appointment holding allocatableWillBeMerge
                final Date today = DateTools.cutDate(new Date(System.currentTimeMillis()));
                final Date startAppointment = DateTools.toDateTime(today, new Date(DateTools.toTime(13, 00, 00)));
                final Date endAppointment = DateTools.toDateTime(today, new Date(DateTools.toTime(14, 00, 00)));
                final Appointment newAppointment = facade.newAppointment(startAppointment, endAppointment);
                reservation.addAppointment(newAppointment);
                reservation.setRestriction(newAppointment, new Allocatable[]{allocatableWillBeMerge});
            }
            {// third appointment no restriction
                final Date today = DateTools.cutDate(new Date(System.currentTimeMillis()));
                final Date startAppointment = DateTools.toDateTime(today, new Date(DateTools.toTime(13, 00, 00)));
                final Date endAppointment = DateTools.toDateTime(today, new Date(DateTools.toTime(14, 00, 00)));
                final Appointment newAppointment = facade.newAppointment(startAppointment, endAppointment);
                reservation.addAppointment(newAppointment);
            }
            facade.store(reservation);
            {
                final Date today = DateTools.cutDate(new Date(System.currentTimeMillis()));
                Allocatable[] allocatablesToLookFor = new Allocatable[]{allocatableWinsMerge};
                final Date tomorrow = DateTools.addDay(today);
                final Collection<Reservation> reservations = PromiseSynchroniser.waitForWithRaplaException(facade.getReservationsForAllocatable(allocatablesToLookFor, today, tomorrow, null), 10000);
                Assert.assertEquals(1, reservations.size());
            }
        }
        final ReferenceInfo<Allocatable> groupAllocatableReference;
        final ReferenceInfo<Allocatable> groupAllocatableReferenceWithWinningAllocatable;
        {// test package referencing deleted allocatable
            final DynamicType mergeGroupDynamicType = facade.newDynamicType("mergeGroup");
            mergeGroupDynamicType.setKey("mergeGroup");
            mergeGroupDynamicType.getName().setName("en", "mergeGroup");
            final Attribute packageAttribute = facade.newAttribute(AttributeType.ALLOCATABLE);
            packageAttribute.setKey("packageAttribute");
            packageAttribute.setConstraint(ConstraintIds.KEY_PACKAGE, Boolean.TRUE.toString());
            mergeGroupDynamicType.addAttribute(packageAttribute);
            facade.store(mergeGroupDynamicType);
            final Allocatable groupAllocatable = facade.newAllocatable(mergeGroupDynamicType.newClassification(), user);
            groupAllocatableReference = groupAllocatable.getReference();
            groupAllocatable.getClassification().setValues(packageAttribute, Collections.singleton(allocatableWillBeMerge));
            facade.store(groupAllocatable);
            final Allocatable secondMergeGroup = facade.newAllocatable(mergeGroupDynamicType.newClassification(), user);
            final ArrayList<Allocatable> values = new ArrayList<Allocatable>();
            values.add(allocatableWinsMerge);
            values.add(allocatableWillBeMerge);
            secondMergeGroup.getClassification().setValues(packageAttribute, values);
            groupAllocatableReferenceWithWinningAllocatable = secondMergeGroup.getReference();
            facade.store(secondMergeGroup);
        }
        final ReferenceInfo<Allocatable> belongsToAllocatableReference;
        {// test belongsTo
            final DynamicType belongsToDynamicType = facade.newDynamicType("mergeBelongsTo");
            belongsToDynamicType.getName().setName("en", "mergeBelongsTo");
            final Attribute belongsToAttribute = facade.newAttribute(AttributeType.ALLOCATABLE);
            belongsToAttribute.setKey("belongsTo");
            belongsToAttribute.setConstraint(ConstraintIds.KEY_BELONGS_TO, Boolean.TRUE.toString());
            belongsToDynamicType.addAttribute(belongsToAttribute);
            facade.store(belongsToDynamicType);
            final Allocatable belongsToAllocatable = facade.newAllocatable(belongsToDynamicType.newClassification(), user);
            belongsToAllocatableReference = belongsToAllocatable.getReference();
            belongsToAllocatable.getClassification().setValue(belongsToAttribute, allocatableWillBeMerge);
            facade.store(belongsToAllocatable);
        }
        {// CalendarModelConfiguration
            final CalendarModelConfigurationImpl configurationWithBothAllocatables;
            {
                final CalendarModelImpl calendarModelImpl = new CalendarModelImpl(facade, clientFacade, new RaplaLocaleImpl(new DefaultBundleManager()));
                Collection<Allocatable> markedAllocatables = new ArrayList<>();
                markedAllocatables.add(allocatableWillBeMerge);
                markedAllocatables.add(allocatableWinsMerge);
                calendarModelImpl.setSelectedObjects(markedAllocatables);
                configurationWithBothAllocatables = calendarModelImpl.createConfiguration();
                Assert.assertEquals(2, configurationWithBothAllocatables.getSelected().size());
            }
            final CalendarModelConfigurationImpl configurationWithLoosesAllocatable;
            {
                final CalendarModelImpl calendarModelImpl = new CalendarModelImpl(facade, clientFacade, new RaplaLocaleImpl(new DefaultBundleManager()));
                Collection<Allocatable> markedAllocatables = new ArrayList<>();
                markedAllocatables.add(allocatableWillBeMerge);
                calendarModelImpl.setSelectedObjects(markedAllocatables);
                configurationWithLoosesAllocatable = calendarModelImpl.createConfiguration();
                Assert.assertEquals(1, configurationWithLoosesAllocatable.getSelected().size());
            }
            final CalendarModelConfigurationImpl configurationClassificationFilterWithLoosesAllocatable;
            {// ClassificationFilter
                final CalendarModelImpl calendarModelImpl = new CalendarModelImpl(facade, clientFacade, new RaplaLocaleImpl(new DefaultBundleManager()));
                configurationClassificationFilterWithLoosesAllocatable = calendarModelImpl.createConfiguration();
                List<ClassificationFilterImpl> classificationFilters = new ArrayList<>();
                final DynamicType dynamicTypeGroup = facade.getDynamicType("mergeGroup");
                final ClassificationFilter newClassificationFilter = dynamicTypeGroup.newClassificationFilter();
                newClassificationFilter.addEqualsRule("packageAttribute", allocatableWillBeMerge);
                classificationFilters.add((ClassificationFilterImpl) newClassificationFilter);
                configurationClassificationFilterWithLoosesAllocatable.setClassificationFilter(classificationFilters);
            }
            final Preferences preferences = facade.edit(facade.getPreferences(user, true));
            Map<String,CalendarModelConfiguration> exportMap= preferences.getEntry(CalendarModelConfiguration.EXPORT_ENTRY);
            Map<String,CalendarModelConfiguration> newMap;
            if ( exportMap == null)
                newMap = new TreeMap<String,CalendarModelConfiguration>();
            else
                newMap = new TreeMap<String,CalendarModelConfiguration>( exportMap);
            newMap.put("testForMerge", configurationWithBothAllocatables);
            newMap.put("testForMergeWithoutWins", configurationWithLoosesAllocatable);
            newMap.put("testForFilter", configurationClassificationFilterWithLoosesAllocatable);
            RaplaMapImpl map = new RaplaMapImpl(newMap);
            map.setResolver( facade.getOperator() );
            preferences.putEntry(CalendarModelConfiguration.EXPORT_ENTRY, map);
            facade.store(preferences);
        }
        final ReferenceInfo<Allocatable> allocatableReferencingNormally;
        {// Classification normally referencing 
            final DynamicType newDynamicType = facade.newDynamicType("dynamicTypeReferencingAllocatableLooses");
            newDynamicType.getName().setName("en", "dynamicTypeReferencingAllocatableLooses");
            final Attribute refAttribute = facade.newAttribute(AttributeType.ALLOCATABLE);
            refAttribute.setKey("refAllocatable");
            refAttribute.setConstraint(ConstraintIds.KEY_DYNAMIC_TYPE, allocatableWillBeMerge.getClassification().getType());
            refAttribute.setConstraint(ConstraintIds.KEY_MULTI_SELECT, Boolean.TRUE.toString());
            newDynamicType.addAttribute(refAttribute);
            facade.store(newDynamicType);
            final Allocatable newAllocatable = facade.newAllocatable(newDynamicType.newClassification(), user);
            allocatableReferencingNormally = newAllocatable.getReference();
            final Classification classification = newAllocatable.getClassification();
            final Attribute[] attributes = classification.getAttributes();
            Assert.assertEquals(1, attributes.length);
            classification.setValues(refAttribute, Collections.singleton(allocatableWillBeMerge));
            facade.store(newAllocatable);
        }
        // merge two allocatables
        {
            Set<ReferenceInfo<Allocatable>> allocatableIds = new LinkedHashSet<>();
            allocatableIds.add(allocatableWillBeMerge.getReference());
            facade.doMerge(allocatableWinsMerge, allocatableIds, user);
        }
        // Test result
        {
            final Date today = DateTools.cutDate(new Date(System.currentTimeMillis()));
            Reservation myTestReservation = null;
            Allocatable[] allocatablesToLookFor = new Allocatable[]{allocatableWinsMerge};
            final Collection<Reservation> reservations = PromiseSynchroniser.waitForWithRaplaException(facade.getReservationsForAllocatable(allocatablesToLookFor, today, DateTools.addDay(today), null), 10000);
            for (Reservation reservation : reservations)
            {
                if(reservation.getReference().equals(reservationReference))
                {
                    myTestReservation = reservation;
                    break;
                }
            }
            Assert.assertNotNull("looked for today reservations which should have included " + reservationReference, myTestReservation);
            final Allocatable[] allocatables = myTestReservation.getAllocatables();
            Assert.assertEquals(1, allocatables.length);
            Assert.assertEquals(allocatableWinsMerge, allocatables[0]);
            final Appointment[] appointments = myTestReservation.getAppointments();
            Assert.assertEquals(3, appointments.length);
            Assert.assertEquals(1, myTestReservation.getRestrictedAllocatables(appointments[0]).length);
            Assert.assertEquals(allocatableWinsMerge, myTestReservation.getRestrictedAllocatables(appointments[0])[0]);
            Assert.assertEquals(1, myTestReservation.getRestrictedAllocatables(appointments[1]).length);
            Assert.assertEquals(allocatableWinsMerge, myTestReservation.getRestrictedAllocatables(appointments[1])[0]);
            Assert.assertEquals(0, myTestReservation.getRestrictedAllocatables(appointments[2]).length);
            final Appointment[] restriction = myTestReservation.getRestriction(allocatableWinsMerge);
            Assert.assertEquals(2, restriction.length);
        }
        {// test package
            {
                final Allocatable alloc = facade.getOperator().resolve(groupAllocatableReference);
                final Classification classification = alloc.getClassification();
                final Attribute[] attributes = classification.getAttributes();
                Assert.assertEquals(1, attributes.length);
                final Collection<Object> values = classification.getValues(attributes[0]);
                Assert.assertEquals(1, values.size());
                Assert.assertEquals(allocatableWinsMerge, values.iterator().next());
            }
            {
                final Allocatable alloc = facade.getOperator().resolve(groupAllocatableReferenceWithWinningAllocatable);
                final Classification classification = alloc.getClassification();
                final Attribute[] attributes = classification.getAttributes();
                Assert.assertEquals(1, attributes.length);
                final Collection<Object> values = classification.getValues(attributes[0]);
                Assert.assertEquals(1, values.size());
                Assert.assertEquals(allocatableWinsMerge, values.iterator().next());
            }            
        }
        {// belongsTo
            final Allocatable belongsToAllocatable = facade.getOperator().resolve(belongsToAllocatableReference);
            final Classification classification = belongsToAllocatable.getClassification();
            final Attribute[] attributes = classification.getAttributes();
            Assert.assertEquals(1, attributes.length);
            final Object value = classification.getValue(attributes[0]);
            Assert.assertEquals(allocatableWinsMerge, value);
        }
        {// CalendarModelConfiguration
            final RaplaMap<CalendarModelConfiguration> entry = facade.getPreferences(user).getEntry(CalendarModelConfiguration.EXPORT_ENTRY);
            {
                final CalendarModelConfiguration calendarModelConfiguration = entry.get("testForMerge");
                final Collection<Entity> selected = calendarModelConfiguration.getSelected();
                Assert.assertEquals(1, selected.size());
                Assert.assertEquals(allocatableWinsMerge, selected.iterator().next());
            }
            {
                final CalendarModelConfiguration calendarModelConfiguration = entry.get("testForMergeWithoutWins");
                final Collection<Entity> selected = calendarModelConfiguration.getSelected();
                Assert.assertEquals(1, selected.size());
                Assert.assertEquals(allocatableWinsMerge, selected.iterator().next());
            }
            {//ClassificationFilter
                final CalendarModelConfiguration calendarModelConfiguration = entry.get("testForFilter");
                final ClassificationFilter[] filter = calendarModelConfiguration.getFilter();
                Assert.assertEquals(1, filter.length);
                final Iterator<? extends ClassificationFilterRule> ruleIterator = filter[0].ruleIterator();
                Assert.assertTrue(ruleIterator.hasNext());
                final ClassificationFilterRule next = ruleIterator.next();
                final Object[] values = next.getValues();
                Assert.assertEquals(1, values.length);
                Assert.assertEquals(allocatableWinsMerge, values[0]);
                Assert.assertFalse(ruleIterator.hasNext());
            }
        }
        {// Classification normally referenced
            final Allocatable resolve = facade.getOperator().resolve(allocatableReferencingNormally);
            final Classification classification = resolve.getClassification();
            final Attribute[] attributes = classification.getAttributes();
            Assert.assertEquals(1, attributes.length);
            final Collection<Object> values = classification.getValues(attributes[0]);
            Assert.assertEquals(1, values.size());
            Assert.assertEquals(allocatableWinsMerge, values.iterator().next());
        }
    }
    
    @Test
    public void testGroupConflicts() throws RaplaException
    {
        Classification classification = facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION)[0].newClassification();
        User user = clientFacade.getUser();
        Date startDate = DateTools.toDateTime(new Date(System.currentTimeMillis()), new Date(DateTools.toTime(10, 00, 00)));
        Date endDate = DateTools.toDateTime(new Date(System.currentTimeMillis()), new Date(DateTools.toTime(12, 00, 00)));
        {// Store new Reservation with resource
            final Reservation newReservation = facade.newReservation(classification, user);
            final Allocatable montyAllocatable = facade.getOperator().tryResolve("r9b69d90-46a0-41bb-94fa-82079b424c03", Allocatable.class);//facade.getOperator().tryResolve("f92e9a11-c342-4413-a924-81eee17ccf92", Allocatable.class);
            newReservation.addAllocatable(montyAllocatable);
            newReservation.addAppointment(facade.newAppointment(startDate, endDate, user));
            facade.store(newReservation);
        }
        // create reservation with group allocatable
        final Reservation newReservation = facade.newReservation(classification, user);
        final Allocatable dozGroupAllocatable = facade.getOperator().tryResolve("f92e9a11-c342-4413-a924-81eee17ccf92", Allocatable.class);//facade.getOperator().tryResolve("r9b69d90-46a0-41bb-94fa-82079b424c03", Allocatable.class);
        newReservation.addAllocatable(dozGroupAllocatable);
        newReservation.addAppointment(facade.newAppointment(startDate, endDate, user));
        final Collection<Conflict> conflicts = PromiseSynchroniser.waitForWithRaplaException(facade.getConflicts(newReservation), 10000);
        Assert.assertEquals(1, conflicts.size());
    }
    
    @Test
    public void testBelongsToConflicts() throws Exception
    {
        Classification classification = facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION)[0].newClassification();
        User user = clientFacade.getUser();
        Date startDate = DateTools.toDateTime(new Date(System.currentTimeMillis()), new Date(DateTools.toTime(10, 00, 00)));
        Date endDate = DateTools.toDateTime(new Date(System.currentTimeMillis()), new Date(DateTools.toTime(12, 00, 00)));
        {// Store new Reservation with resource
            final Reservation newReservation = facade.newReservation(classification, user);
            final Allocatable roomA66Allocatable = facade.getOperator().tryResolve("c24ce517-4697-4e52-9917-ec000c84563c", Allocatable.class);
            newReservation.addAllocatable(roomA66Allocatable);
            newReservation.addAppointment(facade.newAppointment(startDate, endDate, user));
            facade.store(newReservation);
        }
        // create reservation with group allocatable
        final Reservation newReservation = facade.newReservation(classification, user);
        final Allocatable partRoomAllocatable = facade.getOperator().tryResolve("rdd6b473-7c77-4344-a73d-1f27008341cb", Allocatable.class);
        newReservation.addAllocatable(partRoomAllocatable);
        newReservation.addAppointment(facade.newAppointment(startDate, endDate, user));
        final Collection<Conflict> conflicts = PromiseSynchroniser.waitForWithRaplaException(facade.getConflicts(newReservation), 10000);
        Assert.assertEquals(1, conflicts.size());
    }

}





