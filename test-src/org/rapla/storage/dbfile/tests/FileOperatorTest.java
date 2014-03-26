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
package org.rapla.storage.dbfile.tests;
import junit.framework.Test;
import junit.framework.TestSuite;

import org.rapla.storage.tests.AbstractOperatorTest;

public class FileOperatorTest extends AbstractOperatorTest {

    public FileOperatorTest(String name) {
        super(name);
    }

    public static Test suite() {
        return new TestSuite(FileOperatorTest.class);
    }


    protected String getStorageName() {
        return "raplafile";
    }
    
    protected String getFacadeName() {
        return "local-facade";
    }
    
 
}





