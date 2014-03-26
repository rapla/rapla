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
package org.rapla.plugin.jndi.internal;

import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;

/** RemoteStub */
public class RaplaJNDITestOnServer extends RaplaComponent implements JNDITest
{
	JNDITest testService;
    public RaplaJNDITestOnServer( RaplaContext context, JNDITest testService ) 
    {
        super( context );
        this.testService = testService;
    }

    public void test(String config,String username,String password ) throws RaplaException
    {
        testService.test(config, username, password);
    }

}

