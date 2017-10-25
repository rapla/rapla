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

import org.junit.Assert;
import org.junit.Test;
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
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.test.util.RaplaTestCase;

import java.util.Date;

public abstract class AbstractOperatorTest  {

	abstract protected RaplaFacade getFacade();
	protected CachableStorageOperator getOperator()
	{
		RaplaFacade facade = getFacade();
		return (CachableStorageOperator)facade.getOperator();
	}

	@Test
    public void testReservationStore() throws RaplaException {
		RaplaFacade facade = getFacade();
		final User user = facade.getUsers()[0];
        // abspeichern
        {
	        Reservation r = facade.newReservation(facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION)[0].newClassification(), user);
	        r.getClassification().setValue("name","myTest");
	        Appointment app = facade.newAppointmentWithUser( new Date(), new Date(), user);
	        Appointment app2 = facade.newAppointmentWithUser( new Date(), new Date(), user);
	        Allocatable resource = facade.newAllocatable(facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE)[0].newClassification(), user);
	        r.addAppointment( app);
	        r.addAppointment( app2);
	        r.addAllocatable(resource );
	        r.setRestriction( resource, new Appointment[] {app});
	        app.setRepeatingEnabled( true );
	        app.getRepeating().setType(Repeating.DAILY);
	        app.getRepeating().setNumber( 10);
	        app.getRepeating().addException( new Date());
	        facade.storeAndRemove(new Entity[] { r, resource }, Entity.ENTITY_ARRAY, user);
	    }
		CachableStorageOperator operator = getOperator();
        operator.disconnect();
        operator.connect();
        // einlesen
        {
	        String defaultReservation = "event";
	        ClassificationFilter filter = facade.getDynamicType( defaultReservation ).newClassificationFilter();
	        filter.addRule("name",new Object[][] { {"contains","myTest"}});
	        Reservation reservation = RaplaTestCase
                    .waitForWithRaplaException(facade.getReservationsForAllocatable( null, null, null, new ClassificationFilter[] {filter} ), 10000).iterator().next();
	        Appointment[] apps = reservation.getAppointments();
	        Allocatable resource = reservation.getAllocatables()[0];
	        Assert.assertEquals(2, apps.length);
			Assert.assertEquals(1, reservation.getAppointmentsFor(resource).length);
	        Appointment app = reservation.getAppointmentsFor( resource )[0];
			Assert.assertEquals(1, app.getRepeating().getExceptions().length);
			Assert.assertEquals(Repeating.DAILY, app.getRepeating().getType());
			Assert.assertEquals(10, app.getRepeating().getNumber());
        }
    }

	@Test
    public void testUserStore() throws RaplaException {
		RaplaFacade facade = getFacade();
        {
            User u = facade.newUser();
            u.setUsername("kohlhaas");
	        u.setAdmin( false);
	        u.addGroup( facade.getUserGroupsCategory().getCategory("my-group"));
	        facade.store( u );
        }
		CachableStorageOperator operator = getOperator();
        operator.disconnect();
        operator.connect();
        {
            User u = facade.getUser("kohlhaas");
            Category[] groups = u.getGroupList().toArray( new Category [] {});
			Assert.assertEquals(groups.length, 4);
			Assert.assertEquals(facade.getUserGroupsCategory().getCategory("my-group"), groups[3]);
			Assert.assertFalse(u.isAdmin());
        }
    }

	@Test
    public void testCategoryAnnotation() throws RaplaException {
		RaplaFacade facade = getFacade();
    	String sampleDoc = "This is the category for user-groups";
    	String sampleAnnotationValue = "documentation";
        {
        	Category userGroups = facade.edit( facade.getUserGroupsCategory());
        	userGroups.setAnnotation( sampleAnnotationValue, sampleDoc );
        	facade.store( userGroups );
        }
		CachableStorageOperator operator = getOperator();
        operator.disconnect();
        operator.connect();
        {
        	Category userGroups = facade.getUserGroupsCategory();
			Assert.assertEquals(sampleDoc, userGroups.getAnnotation(sampleAnnotationValue));
        }
    }

	@Test
    public void testAttributeStore() throws RaplaException {
		RaplaFacade facade = getFacade();
		final User user = facade.getUser("homer");
		CachableStorageOperator operator = getOperator();
        // abspeichern
        {
	        DynamicType type = facade.edit( facade.getDynamicType("event"));

	        Attribute att = facade.newAttribute( AttributeType.STRING );
	        att.setKey("test-att");
	        type.addAttribute( att );

	        Reservation r = facade.newReservation(facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION)[0].newClassification(), user);
	        try {
	        	r.setClassification( type.newClassification() );
				Assert.fail("Should have thrown an IllegalStateException");
	        } catch (IllegalStateException ex) {
	        }

	        facade.storeAndRemove(new Entity[]{type}, Entity.ENTITY_ARRAY, user);

	        r.setClassification( facade.getPersistant(type).newClassification() );

	        r.getClassification().setValue("name","myTest");
	        r.getClassification().setValue("test-att","test-att-value");
	        Appointment app = facade.newAppointmentWithUser( new Date(), new Date(), user);
	        Appointment app2 = facade.newAppointmentWithUser( new Date(), new Date(), user);
	        Allocatable resource = facade.newAllocatable(facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE)[0].newClassification(), user);
	        r.addAppointment( app);
	        r.addAppointment( app2);
	        r.addAllocatable(resource );
	        r.setRestriction( resource, new Appointment[] {app});
	        app.setRepeatingEnabled( true );
	        app.getRepeating().setType(Repeating.DAILY);
	        app.getRepeating().setNumber( 10);
	        app.getRepeating().addException( new Date());
	        facade.storeAndRemove(new Entity[] { r, resource }, Entity.ENTITY_ARRAY, user);
        	operator.disconnect();
	    }
        // einlesen
        {
	        operator.connect();
	        String defaultReservation = "event";
	        ClassificationFilter filter = facade.getDynamicType( defaultReservation ).newClassificationFilter();
	        filter.addRule("name",new Object[][] { {"contains","myTest"}});
	        Reservation reservation = RaplaTestCase
                    .waitForWithRaplaException(facade.getReservationsForAllocatable( null, null, null, new ClassificationFilter[] {filter} ), 10000).iterator().next();
	        Appointment[] apps = reservation.getAppointments();
	        Allocatable resource = reservation.getAllocatables()[0];
			Assert.assertEquals("test-att-value", reservation.getClassification().getValue("test-att"));
			Assert.assertEquals(2, apps.length);
			Assert.assertEquals(1, reservation.getAppointmentsFor(resource).length);
	        Appointment app = reservation.getAppointmentsFor( resource )[0];
			Assert.assertEquals(1, app.getRepeating().getExceptions().length);
			Assert.assertEquals(Repeating.DAILY, app.getRepeating().getType());
			Assert.assertEquals(10, app.getRepeating().getNumber());
        }
    }
}





