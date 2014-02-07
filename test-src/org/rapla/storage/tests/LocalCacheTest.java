/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Christopher Kohlhaas                                  |
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
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.rapla.RaplaTestCase;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Period;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.internal.AllocatableImpl;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.dynamictype.internal.AttributeImpl;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.CachableStorageOperatorCommand;
import org.rapla.storage.LocalCache;

public class LocalCacheTest extends RaplaTestCase {
    Locale locale;

    public LocalCacheTest(String name) {
        super(name);
    }

    public static Test suite() {
        return new TestSuite(LocalCacheTest.class);
    }

    protected void setUp() throws Exception {
        super.setUp();
    }

    public DynamicTypeImpl createDynamicType() throws Exception {
        AttributeImpl attribute = new AttributeImpl(AttributeType.STRING);
        attribute.setKey("name");
        attribute.setId(Attribute.TYPE.getId(1));
        DynamicTypeImpl dynamicType = new DynamicTypeImpl();
        dynamicType.setElementKey("defaultResource");
        dynamicType.setId(DynamicType.TYPE.getId(1));
        dynamicType.addAttribute(attribute);
        dynamicType.setAnnotation(DynamicTypeAnnotations.KEY_NAME_FORMAT,"{name}");
        return dynamicType;
    }


    public AllocatableImpl createResource(int id,DynamicType type,String name) {
        Date today = new Date();
        AllocatableImpl resource = new AllocatableImpl(today, today);
        resource.setId(Allocatable.TYPE.getId(id));
        Classification classification = type.newClassification();
        classification.setValue("name",name);
        resource.setClassification(classification);
        return resource;
    }

    public void testAllocatable() throws Exception {
        LocalCache cache = new LocalCache();

        DynamicTypeImpl type = createDynamicType();
        type.setReadOnly( true );
        cache.put( type );
        AllocatableImpl resource1 = createResource(1,type,"Adrian");
        cache.put(resource1);
        AllocatableImpl resource2 = createResource(2,type,"Beta");
        cache.put(resource2);
        AllocatableImpl resource3 = createResource(3,type,"Ceta");
        cache.put(resource3);

        resource1.getClassification().setValue("name","Zeta");
        cache.put(resource1);
        Allocatable[] resources = cache.getCollection( Allocatable.TYPE).toArray(Allocatable.ALLOCATABLE_ARRAY);
        assertEquals(3, resources.length);
        assertTrue(resources[1].getName(locale).equals("Beta"));
    }

    public void test2() throws Exception {
        final CachableStorageOperator storage = raplaContainer.lookup(CachableStorageOperator.class , "raplafile");
        storage.connect();
        storage.runWithReadLock(new CachableStorageOperatorCommand() {
			
			@Override
			public void execute(LocalCache cache) throws RaplaException {
				{
		            Iterator<?> it = cache.getCollection(Period.TYPE).iterator();
		            Period period = (Period) it.next();
		            Collection<Reservation> reservations = storage.getReservations(null,null,period.getStart(),period.getEnd());
		            assertEquals(0,reservations.size());
		            period = (Period) it.next();
		            reservations = storage.getReservations(null,null,period.getStart(),period.getEnd());
		            assertEquals(2, reservations.size());
		            User user = cache.getUser("homer");
		            reservations = storage.getReservations(user,null,null,null);
		            assertEquals(3, reservations.size());
		            reservations = storage.getReservations(user,null,period.getStart(),period.getEnd());
		            assertEquals(2, reservations.size());
		        }
		        {
		            Iterator<Allocatable> it = cache.getCollection(Allocatable.class).iterator();
		            assertEquals("erwin",it.next().getName(locale));
		            assertEquals("Room A66",it.next().getName(locale));
		        }		
			}
		});
       
    }
    
    public void testConflicts() throws Exception {
        ClientFacade facade = getFacade();
        Reservation reservation = facade.newReservation();
        //start is 13/4  original end = 28/4
        Date startDate = getRaplaLocale().toRaplaDate(2013, 4, 13);
        Date endDate = getRaplaLocale().toRaplaDate(2013, 4, 28);
        Appointment appointment = facade.newAppointment(startDate, endDate);
        reservation.addAppointment(appointment);
        reservation.getClassification().setValue("name", "test");
        facade.store( reservation);
        
        Reservation modifiableReservation = facade.edit(reservation);

        
        Date splitTime = getRaplaLocale().toRaplaDate(2013, 4, 20);
        Appointment modifiableAppointment = modifiableReservation.findAppointment( appointment);
       // left part
        //leftpart.move(13/4, 20/4)
        modifiableAppointment.move(appointment.getStart(), splitTime);

        facade.store( modifiableReservation);
      
        CachableStorageOperator storage =  raplaContainer.lookup(CachableStorageOperator.class, "raplafile");
        User user = null;
		Collection<Allocatable> allocatables = null;
		Collection<Reservation> reservations = storage.getReservations(user, allocatables, startDate, endDate);
        assertEquals( 1, reservations.size());
    }
}





