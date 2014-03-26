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
package org.rapla.plugin.mail.internal;

import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.mail.MailToUserInterface;

/** RemoteStub */
public class RaplaMailToUserOnServer extends RaplaComponent implements MailToUserInterface
{
	MailToUserInterface service;
    public RaplaMailToUserOnServer( RaplaContext context, MailToUserInterface service ) 
    {
        super( context );
        this.service = service;
    }

    public void sendMail( String username, String subject, String mailBody ) throws RaplaException
    {
        service.sendMail(username, subject, mailBody);
    }
    
   

}

