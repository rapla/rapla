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
package org.rapla.storage.tests;
import java.util.Date;

import org.rapla.RaplaTestCase;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.storage.CachableStorageOperator;

public abstract class AbstractOperatorTest extends RaplaTestCase {

    protected CachableStorageOperator operator;
    protected ClientFacade facade;
    public AbstractOperatorTest(String name) {
        super(name);
    }

    protected void setUp() throws Exception {
        super.setUp();
        operator = raplaContainer.lookup(CachableStorageOperator.class , getStorageName());
        facade = raplaContainer.lookup(ClientFacade.class, getFacadeName());
    }

    abstract protected String getStorageName();

    abstract protected String getFacadeName();

    public void testReservationStore() throws RaplaException {
        // abspeichern
        facade.login("homer", "duffs".toCharArray() );
        {
	        Reservation r = facade.newReservation();
	        r.getClassification().setValue("name","test");
	        Appointment app = facade.newAppointment( new Date(), new Date());
	        Appointment app2 = facade.newAppointment( new Date(), new Date());
	        Allocatable resource = facade.newResource();
	        r.addAppointment( app);
	        r.addAppointment( app2);
	        r.addAllocatable(resource );
	        r.setRestriction( resource, new Appointment[] {app});
	        app.setRepeatingEnabled( true );
	        app.getRepeating().setType(Repeating.DAILY);
	        app.getRepeating().setNumber( 10);
	        app.getRepeating().addException( new Date());
	        facade.storeObjects( new Entity[] { r, resource });
	    }
        operator.disconnect();
        operator.connect();
        facade.login("homer", "duffs".toCharArray() );
        // einlesen
        {
	        String defaultReservation = "event";
	        ClassificationFilter filter = facade.getDynamicType( defaultReservation ).newClassificationFilter();
	        filter.addRule("name",new Object[][] { {"contains","test"}});
	        Reservation reservation = facade.getReservationsForAllocatable( null, null, null, new ClassificationFilter[] {filter} )[0];
	        Appointment[] apps = reservation.getAppointments();
	        Allocatable resource = reservation.getAllocatables()[0];
	        assertEquals( 2, apps.length);
	        assertEquals( 1, reservation.getAppointmentsFor( resource ).length);
	        Appointment app = reservation.getAppointmentsFor( resource )[0];
	        assertEquals( 1, app.getRepeating().getExceptions().length);
	        assertEquals( Repeating.DAILY, app.getRepeating().getType());
	        assertEquals( 10, app.getRepeating().getNumber());
        }
    }

    public void testUserStore() throws RaplaException {
        facade.login("homer", "duffs".toCharArray() );
        {
            User u = facade.newUser();
            u.setUsername("kohlhaas");
	        u.setAdmin( false);
	        u.addGroup( facade.getUserGroupsCategory().getCategory("my-group"));
	        facade.store( u );
        }
        operator.disconnect();
        operator.connect();
        facade.login("homer", "duffs".toCharArray() );
        {
            User u = facade.getUser("kohlhaas");
            Category[] groups = u.getGroupList().toArray( new Category [] {});
            assertEquals( groups.length, 4 );
            assertEquals( facade.getUserGroupsCategory().getCategory("my-group"), groups[3]);
            assertFalse( u.isAdmin() );
        }
    }

    public void testCategoryAnnotation() throws RaplaException {
    	String sampleDoc = "This is the category for user-groups";
    	String sampleAnnotationValue = "documentation";
    	facade.login("homer", "duffs".toCharArray() );
        {
        	Category userGroups = facade.edit( facade.getUserGroupsCategory());
        	userGroups.setAnnotation( sampleAnnotationValue, sampleDoc );
        	facade.store( userGroups );
        }
        operator.disconnect();
        operator.connect();
        facade.login("homer", "duffs".toCharArray() );
        {
        	Category userGroups = facade.getUserGroupsCategory();
        	assertEquals( sampleDoc, userGroups.getAnnotation( sampleAnnotationValue ));
        }
    }

    public void testAttributeStore() throws RaplaException {
        facade.login("homer", "duffs".toCharArray() );
        // abspeichern
        {
	        DynamicType type = facade.edit( facade.getDynamicType("event"));

	        Attribute att = facade.newAttribute( AttributeType.STRING );
	        att.setKey("test-att");
	        type.addAttribute( att );

	        Reservation r = facade.newReservation();
	        try {
	        	r.setClassification( type.newClassification() );
	        	fail("Should have thrown an IllegalStateException");
	        } catch (IllegalStateException ex) {
	        }

	        facade.store( type );

	        r.setClassification( facade.getPersistant(type).newClassification() );

	        r.getClassification().setValue("name","test");
	        r.getClassification().setValue("test-att","test-att-value");
	        Appointment app = facade.newAppointment( new Date(), new Date());
	        Appointment app2 = facade.newAppointment( new Date(), new Date());
	        Allocatable resource = facade.newResource();
	        r.addAppointment( app);
	        r.addAppointment( app2);
	        r.addAllocatable(resource );
	        r.setRestriction( resource, new Appointment[] {app});
	        app.setRepeatingEnabled( true );
	        app.getRepeating().setType(Repeating.DAILY);
	        app.getRepeating().setNumber( 10);
	        app.getRepeating().addException( new Date());
	        facade.storeObjects( new Entity[] { r, resource });
        	operator.disconnect();
	    }
        // einlesen
        {
	        operator.connect();
	        facade.login("homer", "duffs".toCharArray() );
	        String defaultReservation = "event";
	        ClassificationFilter filter = facade.getDynamicType( defaultReservation ).newClassificationFilter();
	        filter.addRule("name",new Object[][] { {"contains","test"}});
	        Reservation reservation = facade.getReservationsForAllocatable( null, null, null, new ClassificationFilter[] {filter} )[0];
	        Appointment[] apps = reservation.getAppointments();
	        Allocatable resource = reservation.getAllocatables()[0];
	        assertEquals( "test-att-value", reservation.getClassification().getValue("test-att"));
	        assertEquals( 2, apps.length);
	        assertEquals( 1, reservation.getAppointmentsFor( resource ).length);
	        Appointment app = reservation.getAppointmentsFor( resource )[0];
	        assertEquals( 1, app.getRepeating().getExceptions().length);
	        assertEquals( Repeating.DAILY, app.getRepeating().getType());
	        assertEquals( 10, app.getRepeating().getNumber());
        }
    }
}





