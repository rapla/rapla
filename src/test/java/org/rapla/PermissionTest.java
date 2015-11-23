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
package org.rapla;
import java.util.Date;
import java.util.Locale;

import org.eclipse.jetty.server.Server;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.components.util.DateTools;
import org.rapla.entities.Category;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.permission.DefaultPermissionControllerSupport;
import org.rapla.entities.domain.permission.PermissionController;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.logger.Logger;
import org.rapla.framework.logger.RaplaBootstrapLogger;
import org.rapla.storage.RaplaSecurityException;

import javax.inject.Provider;

@RunWith(JUnit4.class)
public class PermissionTest  {
    
    ClientFacade adminFacade;
    ClientFacade testFacade;
    Locale locale;
    private Server server;

    @Before
    public void setUp() throws Exception
    {
        locale = Locale.getDefault();
        Logger logger = RaplaBootstrapLogger.createRaplaLogger();
        int port = 8052;
        server = RaplaTestCase.createServer(port, logger, "testdefault.xml");
        Provider<ClientFacade> clientFacadeProvider = RaplaTestCase.createFacadeWithRemote(logger, port);
        adminFacade = clientFacadeProvider.get();
        testFacade = clientFacadeProvider.get();
        adminFacade.login("homer","duffs".toCharArray());
        try
        {
            Category userGroupsCategory = adminFacade.getUserGroupsCategory();
            Category groups =  adminFacade.edit( userGroupsCategory );
            Category testGroup = adminFacade.newCategory();
            testGroup.setKey("test-group");
            groups.addCategory( testGroup );
            adminFacade.store( groups );
            {
                Category testGroup2 = adminFacade.getUserGroupsCategory().getCategory("test-group");
                Assert.assertNotNull(testGroup2);
            }
            User user = adminFacade.newUser();
            user.setUsername("test");
            user.addGroup( testGroup );
            adminFacade.store( user );
            adminFacade.changePassword( user, new char[]{}, new char[] {});
            testFacade.login("test","".toCharArray());
        }
        catch (Exception ex) {
            throw ex;
        }
    }

    @After
    public void tearDown() throws  Exception
    {
        adminFacade.logout();
        testFacade.logout();
        server.stop();
    }


    @Test
    public void testReadPermissions() throws Exception {
        // first create a new resource and set the permissions

        Allocatable allocatable = adminFacade.newResource();
        allocatable.getClassification().setValue("name","test-allocatable");
        //remove default permission.
        allocatable.removePermission( allocatable.getPermissionList().iterator().next() );
        Permission permission = allocatable.newPermission();
        Category testGroup = adminFacade.getUserGroupsCategory().getCategory("test-group");
        Assert.assertNotNull(testGroup);
        permission.setGroup ( testGroup );
        permission.setAccessLevel( Permission.READ );
        allocatable.addPermission( permission );
        adminFacade.store( allocatable );
        // Wait for update
        testFacade.refresh();
        // test the permissions in the second facade.
        clientReadPermissions();
    }

    @Test
    public void testAllocatePermissions() throws Exception {
        // first create a new resource and set the permissions

        Allocatable allocatable = adminFacade.newResource();
        allocatable.getClassification().setValue("name","test-allocatable");
        //remove default permission.
        allocatable.removePermission( allocatable.getPermissionList().iterator().next() );
        Permission permission = allocatable.newPermission();
        Category testGroup = adminFacade.getUserGroupsCategory().getCategory("test-group");
        permission.setGroup ( testGroup );
        permission.setAccessLevel( Permission.ALLOCATE );
        allocatable.addPermission( permission );
        adminFacade.store( allocatable );
        // Wait for update
        testFacade.refresh();

        // test the permissions in the second facade.
        clientAllocatePermissions();

        // Uncovers bug 1237332,
        ClassificationFilter filter = testFacade.getDynamicType("event").newClassificationFilter();
        filter.addEqualsRule("name","R1");
        Reservation evt = testFacade.getReservationsForAllocatable( null, null, null, new ClassificationFilter[] {filter} )[0];
        evt =  testFacade.edit( evt );
        evt.removeAllocatable( allocatable );
        testFacade.store( evt );

        allocatable = adminFacade.edit( allocatable );
        allocatable.getPermissionList().iterator().next().setAccessLevel( Permission.READ);
        adminFacade.store( allocatable );

        testFacade.refresh();


        evt = testFacade.edit( evt );
        evt.addAllocatable( allocatable );
        try {
            testFacade.store( evt );
            Assert.fail("RaplaSecurityException expected!");
        } catch (RaplaSecurityException ex) {
        //  System.err.println ( ex.getMessage());
        }

        Allocatable allocatable2 = adminFacade.newResource();
        allocatable2.getClassification().setValue("name","test-allocatable2");
        permission = allocatable.newPermission();
        permission.setUser( testFacade.getUser());
        permission.setAccessLevel( Permission.ADMIN);
        allocatable2.addPermission( permission );
        adminFacade.store( allocatable2 );
        testFacade.refresh();
        evt.addAllocatable( allocatable2 );
        try {
            testFacade.store( evt );
            Assert.fail("RaplaSecurityException expected!");
        } catch (RaplaSecurityException ex) {
        }
        Thread.sleep( 100);

    }

    private Allocatable getTestResource() throws Exception {
        Allocatable[] all = testFacade.getAllocatables();
        for ( int i=0;i< all.length; i++ ){
            if ( all[i].getName( locale ).equals("test-allocatable") ) {
                return all [i];
            }
        }
        return null;
    }

    private void clientReadPermissions() throws Exception {
        final PermissionController permissionController = DefaultPermissionControllerSupport.getController();
        User user = testFacade.getUser();
        Allocatable a = getTestResource();
        Assert.assertNotNull(a);
        Assert.assertTrue(permissionController.canRead(a, user));
        Assert.assertTrue(!permissionController.canModify(a, user));
        Assert.assertTrue(!permissionController.canCreateConflicts(a, user));
        Assert.assertTrue(!permissionController.canAllocate(a, user, null, null, testFacade.today()));
    }

    private void clientAllocatePermissions() throws Exception {
        final PermissionController permissionController = DefaultPermissionControllerSupport.getController();
        Allocatable allocatable = getTestResource();
        User user = testFacade.getUser();
        Assert.assertNotNull(allocatable);
        Assert.assertTrue(permissionController.canRead(allocatable, user));
        Date start1 = DateTools.addDay(testFacade.today());
        Date end1 = new Date(start1.getTime() + DateTools.MILLISECONDS_PER_HOUR * 2);
        Date start2 = new Date(start1.getTime() + DateTools.MILLISECONDS_PER_HOUR * 1);
        Date end2 = new Date(start1.getTime() + DateTools.MILLISECONDS_PER_HOUR * 3);
        Assert.assertTrue(permissionController.canAllocate(allocatable, user, null, null, testFacade.today()));

        Reservation r1 = testFacade.newReservation();
        r1.getClassification().setValue("name","R1");
		Appointment a1 = testFacade.newAppointment( start1, end1 );
		r1.addAppointment( a1 );
		r1.addAllocatable( allocatable );

		testFacade.store( r1 );

		Reservation r2 = testFacade.newReservation();
		r2.getClassification().setValue("name","R2");
		Appointment a2 = testFacade.newAppointment( start2, end2 );
		r2.addAppointment( a2 );
		r2.addAllocatable( allocatable );
		try {
			testFacade.store( r2 );
            Assert.fail("RaplaSecurityException expected! Conflicts should be created");
		} catch (RaplaSecurityException ex) {
		//	System.err.println ( ex.getMessage());
		}


    }

}





