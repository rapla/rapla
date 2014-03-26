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
package org.rapla.entities.tests;
import java.util.Locale;

import org.rapla.ServletTestBase;
import org.rapla.entities.Category;
import org.rapla.entities.User;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.server.ServerServiceContainer;


public class UserTest extends ServletTestBase {
    
    ClientFacade adminFacade;
    ClientFacade testFacade;
    Locale locale;

    public UserTest(String name) {
        super(name);
    }

    protected void setUp() throws Exception {
        super.setUp();
        // start the server
        getContainer().lookup(ServerServiceContainer.class, "storage-file");
        // start the client service
        adminFacade = getContainer().lookup(ClientFacade.class , "remote-facade");
        adminFacade.login("homer","duffs".toCharArray());
        locale = Locale.getDefault();

        try
        {
            Category groups = adminFacade.edit( adminFacade.getUserGroupsCategory() );
            Category testGroup = adminFacade.newCategory();
            testGroup.setKey("test-group");
            groups.addCategory( testGroup );
            adminFacade.store( groups );
        } catch (RaplaException ex) {
            adminFacade.logout();
            super.tearDown();
            throw ex;
            
        }
        testFacade = getContainer().lookup(ClientFacade.class , "remote-facade-2");
        boolean canLogin = testFacade.login("homer","duffs".toCharArray());
        assertTrue( "Can't login", canLogin );
    }

    protected void tearDown() throws Exception {
        adminFacade.logout();
        testFacade.logout();
        super.tearDown();
    }

    public void testCreateAndRemoveUser() throws Exception {
        User user = adminFacade.newUser();
        user.setUsername("test");
        user.setName("Test User");
        adminFacade.store( user );
        testFacade.refresh();
        User newUser = testFacade.getUser("test");
        testFacade.remove( newUser );
        // first create a new resource and set the permissions
    }


}





