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
package org.rapla.storage.xml.tests;

import org.rapla.RaplaTestCase;

public class ConverterTest extends RaplaTestCase {
    public ConverterTest(String name) {
        super(name);
    }

    protected void setUp() throws Exception  {
        // we do it in convert
    }


    public void testConvert() throws Exception {
        super.setUp( "org/rapla/storage/xml/tests/version-0.5.xml" );
        assertEquals(2,getFacade().getAllocatables().length);
    }

}





