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

import org.eclipse.jetty.server.Server;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.entities.Category;
import org.rapla.entities.User;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.logger.Logger;
import org.rapla.test.util.RaplaTestCase;

import javax.inject.Provider;
import java.util.Locale;

@RunWith(JUnit4.class)
public class UserTest  {
    
    ClientFacade adminFacade;
    ClientFacade testFacade;
    Locale locale;
    Server server;

    @Before
    public void setUp() throws Exception {
        int port = 8052;
        final Logger raplaLogger = RaplaTestCase.initLoger();
        server = RaplaTestCase.createServerContext(raplaLogger, "testdefault.xml", port).getServer();
        // start the client service
        final Provider<ClientFacade> facadeWithRemote = RaplaTestCase.createFacadeWithRemote(raplaLogger, port);
        adminFacade = facadeWithRemote.get();
        adminFacade.login("homer","duffs".toCharArray());
        locale = Locale.getDefault();

        try
        {
            final RaplaFacade raplaFacade = adminFacade.getRaplaFacade();
            Category groups = raplaFacade.edit( raplaFacade.getUserGroupsCategory() );
            Category testGroup = raplaFacade.newCategory();
            testGroup.setKey("test-group");
            groups.addCategory( testGroup );
            raplaFacade.store( groups );
        } catch (RaplaException ex) {
            adminFacade.logout();
            throw ex;
            
        }
        testFacade = facadeWithRemote.get();
        boolean canLogin = testFacade.login("homer","duffs".toCharArray());
        Assert.assertTrue("Can't login", canLogin);
    }

    @After
    public void tearDown() throws Exception {
        adminFacade.logout();
        testFacade.logout();
        server.stop();
    }

    @Test
    public void testCreateAndRemoveUser() throws Exception {
        User user = adminFacade.getRaplaFacade().newUser();
        user.setUsername("test");
        user.setName("Test User");
        adminFacade.getRaplaFacade().store( user );
        testFacade.getRaplaFacade().refresh();
        final RaplaFacade raplaFacade = testFacade.getRaplaFacade();
        User newUser = raplaFacade.getUser("test");
        raplaFacade.remove( newUser );
        // first create a new resource and set the permissions
    }


}





