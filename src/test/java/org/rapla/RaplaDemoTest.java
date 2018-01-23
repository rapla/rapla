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

import org.junit.Assert;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.entities.domain.Allocatable;
import org.rapla.facade.client.ClientFacade;
import org.rapla.test.util.RaplaTestCase;

@RunWith(JUnit4.class)
public class RaplaDemoTest {

    @org.junit.Test
    public void testAccess() throws Exception {
        ClientFacade facade = RaplaTestCase.createSimpleSimpsonsWithHomer();
        Allocatable[] resources = facade.getRaplaFacade().getAllocatables();
        Assert.assertTrue(resources.length > 0);
    }

}





