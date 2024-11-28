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
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.RaplaResources;
import org.rapla.components.i18n.internal.AbstractBundleManager;
import org.rapla.components.i18n.server.ServerBundleManager;
import org.rapla.components.util.DateTools;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.domain.*;
import org.rapla.entities.domain.internal.AllocatableImpl;
import org.rapla.entities.domain.permission.PermissionExtension;
import org.rapla.entities.domain.permission.impl.RaplaDefaultPermissionImpl;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.dynamictype.internal.AttributeImpl;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.entities.dynamictype.internal.StandardFunctions;
import org.rapla.entities.extensionpoints.FunctionFactory;
import org.rapla.entities.storage.ExternalSyncEntity;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.facade.internal.CalendarModelImpl;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.internal.DefaultScheduler;
import org.rapla.framework.internal.RaplaLocaleImpl;
import org.rapla.logger.Logger;
import org.rapla.logger.RaplaBootstrapLogger;
import org.rapla.server.PromiseWait;
import org.rapla.server.internal.PromiseWaitImpl;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.CachableStorageOperatorCommand;
import org.rapla.storage.LocalCache;
import org.rapla.storage.PermissionController;
import org.rapla.storage.dbfile.FileOperator;
import org.rapla.test.util.DefaultPermissionControllerSupport;
import org.rapla.test.util.RaplaTestCase;

import java.util.*;

@RunWith(JUnit4.class)
public class LocalCacheTest  {
    Locale locale;


    @Before
    public void setUp() throws Exception {
        locale = Locale.getDefault();
    }

    public DynamicTypeImpl createDynamicType() throws Exception {
        AttributeImpl attribute = new AttributeImpl(AttributeType.STRING);
        attribute.setKey("name");
        attribute.setId(getId(Attribute.class,1));
        DynamicTypeImpl dynamicType = new DynamicTypeImpl();
        dynamicType.setKey("defaultResource");
        dynamicType.setId(getId(DynamicType.class,1));
        dynamicType.addAttribute(attribute);
        dynamicType.setAnnotation(DynamicTypeAnnotations.KEY_NAME_FORMAT,"{name}");
        return dynamicType;
    }


    public AllocatableImpl createResource(LocalCache cache,int intId,DynamicType type,String name) {
        Date today = new Date();
        AllocatableImpl resource = new AllocatableImpl(today, today);
        resource.setId(getId(Allocatable.class,intId));
        resource.setResolver( cache);
        Classification classification = type.newClassification();
        classification.setValue("name",name);
        resource.setClassification(classification);
        return resource;
    }

    private String getId(Class<? extends Entity> type, int intId) {
        return type.toString() + "_" + intId;
    }

    @Test
    public void testAllocatable() throws Exception {
        String resolvedPath = "";
        Logger logger = RaplaBootstrapLogger.createRaplaLogger();
        AbstractBundleManager bundleManager = new ServerBundleManager();
        RaplaResources i18n = new RaplaResources(bundleManager);

        final DefaultScheduler scheduler = new DefaultScheduler(logger);
        RaplaLocale raplaLocale = new RaplaLocaleImpl(bundleManager);

        Map<String, FunctionFactory> functionFactoryMap = new HashMap<String, FunctionFactory>();
        StandardFunctions functions = new StandardFunctions(raplaLocale);
        functionFactoryMap.put(StandardFunctions.NAMESPACE, functions);

        RaplaDefaultPermissionImpl defaultPermission = new RaplaDefaultPermissionImpl();
        Set<PermissionExtension> permissionExtensions = new LinkedHashSet<>();
        permissionExtensions.add(defaultPermission);
        PromiseWait promiseWait = new PromiseWaitImpl(logger);
        FileOperator operator = new FileOperator(logger, promiseWait,i18n, raplaLocale, scheduler, functionFactoryMap, resolvedPath,
                permissionExtensions);
        final PermissionController controller = DefaultPermissionControllerSupport.getController(operator);
        LocalCache cache = new LocalCache(controller);

        DynamicTypeImpl type = createDynamicType();
        type.setResolver( cache);
        type.setOperator(operator);
        type.setReadOnly(  );
        cache.put( type );
        AllocatableImpl resource1 = createResource(cache,1,type,"Adrian");
        cache.put(resource1);
        AllocatableImpl resource2 = createResource(cache,2,type,"Beta");
        cache.put(resource2);
        AllocatableImpl resource3 = createResource(cache,3,type,"Ceta");
        cache.put(resource3);

        resource1.getClassification().setValue("name","Zeta");
        cache.put(resource1);
        Allocatable[] resources = cache.getAllocatables().toArray(Allocatable.ALLOCATABLE_ARRAY);
        Assert.assertEquals(3, resources.length);
        String name = resources[1].getName(locale);
        Assert.assertEquals("Beta", name);
    }

    @Test
    public void test2() throws Exception {

        final ClientFacade clientFacade = RaplaTestCase.createSimpleSimpsonsWithHomer();
        RaplaFacade facade = clientFacade.getRaplaFacade();
        Collection<User> users = Arrays.asList(facade.getUsers());
        final CachableStorageOperator storage = (CachableStorageOperator) facade.getOperator();
        final Period[] periods = facade.getPeriods();
        storage.runWithReadLock(new CachableStorageOperatorCommand() {
			
			@Override
			public void execute(LocalCache cache, Collection<ExternalSyncEntity> syncEntityList) throws RaplaException {
			    try
				{
		            ClassificationFilter[] filters = null;
                    List<Allocatable> list = Arrays.asList(facade.getAllocatables());

                    Map<String, String> annotationQuery = null;
		            {
		                final Period period = periods[2];
                        AppointmentMapping reservations = storage.waitForWithRaplaException(storage.queryAppointments(null, list, users,period.getStart(), period.getEnd(), filters,
                                annotationQuery, false), 10000);
                        Assert.assertEquals(0, reservations.getAllAppointments().size());
		            }
		            {
		                final Period period = periods[1];
                        AppointmentMapping reservations = storage.waitForWithRaplaException(storage.queryAppointments(null, list, users, period.getStart(), period.getEnd(), filters,
                                annotationQuery, false), 10000);
                        Set<Appointment> allAppointments = reservations.getAllAppointments();
                        Assert.assertEquals(3, allAppointments.size());
		            }
		            {
    		            User user = cache.getUser("homer");
                        AppointmentMapping reservations = storage.waitForWithRaplaException(storage.queryAppointments(user, list, Collections.singletonList(user),null, null, filters, annotationQuery, false), 10000);
                        Set<Appointment> allAppointments = reservations.getAllAppointments();
                        Assert.assertEquals(9, allAppointments.size());
		            }
		            {
		                User user = cache.getUser("homer");
		                final Period period = periods[1];
                        AppointmentMapping reservations = storage.waitForWithRaplaException(storage.queryAppointments(user, list, users,  period.getStart(), period.getEnd(), filters,
                                annotationQuery, false), 10000);
                        Set<Appointment> allAppointments = reservations.getAllAppointments();
                        Assert.assertEquals(3, allAppointments.size());
		            }
		        }
			    catch (Exception ex)
			    {
			        throw new RaplaException(ex.getMessage(),ex);
			    }
		        {
		            for (Allocatable next:cache.getAllocatables())
		            {
		                if ( ((DynamicTypeImpl)next.getClassification().getType()).isInternal())
		                {
		                    continue;
		                }
                        Assert.assertEquals("Room A66", next.getName(locale));
		                break;
		            }
		        }		
			}
		});
       
    }

    @Test
    public void testConflicts() throws Exception {
        final ClientFacade clientFacade = RaplaTestCase.createSimpleSimpsonsWithHomer();
        RaplaFacade facade = clientFacade.getRaplaFacade();
        CachableStorageOperator storage = (CachableStorageOperator) facade.getOperator();
        Reservation reservation = facade.newReservationDeprecated();
        //start is 13/4  original end = 28/4
        Date startDate = new Date(DateTools.toDate(2013, 4, 13));
        Date endDate = new Date(DateTools.toDate(2013, 4, 28));
        Appointment appointment = facade.newAppointmentDeprecated(startDate, endDate);
        reservation.addAppointment(appointment);
        reservation.getClassification().setValue("name", "test");
        facade.store( reservation);
        
        Reservation modifiableReservation = facade.edit(reservation);

        
        Date splitTime = new Date(DateTools.toDate(2013, 4, 20));
        Appointment modifiableAppointment = modifiableReservation.findAppointment( appointment);
       // left part
        //leftpart.move(13/4, 20/4)
        modifiableAppointment.move(appointment.getStart(), splitTime);

        facade.store( modifiableReservation);
      
        User user = null;
		Collection<Allocatable> allocatables = null;
		Map<String, String> annotationQuery = null;
		ClassificationFilter[] filters = null;
        Collection<User> owners = Arrays.asList(facade.getUsers());
        AppointmentMapping reservations = storage.waitForWithRaplaException(storage.queryAppointments(user, allocatables, owners, startDate, endDate, filters, annotationQuery, false), 10000);
        Assert.assertEquals(1, reservations.getAllAppointments().size());
    }
}





