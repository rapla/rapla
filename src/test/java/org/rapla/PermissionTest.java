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

import org.rapla.components.util.DateTools;
import org.rapla.entities.Category;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.facade.ClientFacade;
import org.rapla.server.ServerServiceContainer;
import org.rapla.storage.RaplaSecurityException;


public class PermissionTest extends ServletTestBase {
    
    ClientFacade adminFacade;
    ClientFacade testFacade;
    Locale locale;

    public PermissionTest(String name) {
        super(name);
    }

    protected void setUp() throws Exception {
        super.setUp();
        // start the server
        getContainer().lookup(ServerServiceContainer.class, "storage-file");
        // start the client service
        adminFacade =  getContainer().lookup(ClientFacade.class , "remote-facade");
        adminFacade.login("homer","duffs".toCharArray());
        locale = Locale.getDefault();
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
            	assertNotNull( testGroup2);
            }
            User user = adminFacade.newUser();
            user.setUsername("test");
            user.addGroup( testGroup );
            adminFacade.store( user );
            adminFacade.changePassword( user, new char[]{}, new char[] {});
        }
        catch (Exception ex) {
            adminFacade.logout();
            super.tearDown();
            throw ex;
        }
        // Wait for update;
        testFacade = getContainer().lookup(ClientFacade.class , "remote-facade-2");
        boolean canLogin = testFacade.login("test","".toCharArray());
        assertTrue( "Can't login", canLogin );
    }

    protected void tearDown() throws Exception {
        adminFacade.logout();
        testFacade.logout();
        super.tearDown();
    }

    public void testReadPermissions() throws Exception {
        // first create a new resource and set the permissions

        Allocatable allocatable = adminFacade.newResource();
        allocatable.getClassification().setValue("name","test-allocatable");
        //remove default permission.
        allocatable.removePermission( allocatable.getPermissionList().iterator().next() );
        Permission permission = allocatable.newPermission();
        Category testGroup = adminFacade.getUserGroupsCategory().getCategory("test-group");
        assertNotNull( testGroup);
        permission.setGroup ( testGroup );
        permission.setAccessLevel( Permission.READ );
        allocatable.addPermission( permission );
        adminFacade.store( allocatable );
        // Wait for update
        testFacade.refresh();
        // test the permissions in the second facade.
        clientReadPermissions();
    }
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
            fail("RaplaSecurityException expected!");
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
            fail("RaplaSecurityException expected!");
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
        User user = testFacade.getUser();
        Allocatable a = getTestResource();
        assertNotNull( a );
        assertTrue( a.canRead( user ) );
        assertTrue( !a.canModify( user ) );
        assertTrue( !a.canCreateConflicts( user ) );
        assertTrue( !a.canAllocate( user, null, null, testFacade.today()));
    }

    private void clientAllocatePermissions() throws Exception {
        Allocatable allocatable = getTestResource();
        User user = testFacade.getUser();
        assertNotNull( allocatable );
        assertTrue( allocatable.canRead( user ) );
        Date start1 = DateTools.addDay(testFacade.today());
        Date end1 = new Date(start1.getTime() + DateTools.MILLISECONDS_PER_HOUR * 2);
        Date start2 = new Date(start1.getTime() + DateTools.MILLISECONDS_PER_HOUR * 1);
        Date end2 = new Date(start1.getTime() + DateTools.MILLISECONDS_PER_HOUR * 3);
        assertTrue( allocatable.canAllocate( user, null, null, testFacade.today() ) );

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
			fail("RaplaSecurityException expected! Conflicts should be created");
		} catch (RaplaSecurityException ex) {
		//	System.err.println ( ex.getMessage());
		}


    }

}





