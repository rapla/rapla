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

import java.util.Locale;

import org.rapla.MockMailer;
import org.rapla.ServletTestBase;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.logger.Logger;
import org.rapla.plugin.mail.server.MailToUserImpl;
import org.rapla.server.internal.ServerServiceImpl;

/** listens for allocation changes */
public class MailPluginTest extends ServletTestBase {
    ServerServiceImpl raplaServer;

    ClientFacade facade1;
    Locale locale;


    public MailPluginTest(String name) {
        super(name);
    }

    protected void setUp() throws Exception {
        super.setUp();
       
       // start the server
        //ServerServiceContainer container = getContainer().lookupDeprecated(ServerServiceContainer.class, getStorageName());
        raplaServer = this.getContainer();
        // start the client service
        facade1 =  null;
        facade1.login("homer","duffs".toCharArray());
        locale = Locale.getDefault();
    }
    
    protected String getStorageName() {
        return "storage-file";
    }
    
    protected void tearDown() throws Exception {
        facade1.logout();
        super.tearDown();
    }
    
    public void test() throws Exception 
    {
        MockMailer mailMock = (MockMailer) null;
        final ClientFacade facade = null;
        Logger logger = null;

        MailToUserImpl mail = new MailToUserImpl(mailMock, facade, logger);
        mail.sendMail( "homer","Subject", "MyBody");

        Thread.sleep( 1000);

        assertNotNull( mailMock.getMailBody() );
   
    }
    
 
}

