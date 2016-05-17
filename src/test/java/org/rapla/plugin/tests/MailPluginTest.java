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
package org.rapla.plugin.tests;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.MockMailer;
import org.rapla.facade.RaplaFacade;
import org.rapla.logger.Logger;
import org.rapla.plugin.mail.server.MailToUserImpl;
import org.rapla.server.internal.ServerServiceImpl;
import org.rapla.test.util.RaplaTestCase;

import java.util.Locale;

/** listens for allocation changes */
@RunWith(JUnit4.class)
public class MailPluginTest {
    ServerServiceImpl raplaServer;

    RaplaFacade facade1;
    Locale locale;

    @Before
    public void setUp() throws Exception {
        Logger logger = RaplaTestCase.initLoger();
        raplaServer = (ServerServiceImpl) RaplaTestCase.createServiceContainer(logger, "testdefault.xml");
        // start the server
        // start the client service
        facade1 =  raplaServer.getFacade();
        locale = Locale.getDefault();
    }
    

    @Test
    public void test() throws Exception 
    {
        MockMailer mailMock = new MockMailer();
        final RaplaFacade facade = null;
        Logger logger = null;

        MailToUserImpl mail = new MailToUserImpl(mailMock, facade, logger);
        mail.sendMail( "homer","Subject", "MyBody");

        Thread.sleep( 1000);

        Assert.assertNotNull(mailMock.getMailBody());
   
    }
    
 
}

