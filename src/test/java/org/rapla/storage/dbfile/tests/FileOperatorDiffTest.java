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
import org.junit.Assert;
import org.rapla.framework.RaplaException;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.dbfile.FileOperator;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class FileOperatorDiffTest  {
    CachableStorageOperator operator;


    public boolean differ(String file1, String file2) throws IOException {
        BufferedReader in1 = null;
        BufferedReader in2 = null;
        boolean bDiffer = false;
        try {
            in1 = new BufferedReader(new FileReader(file1));
            in2 = new BufferedReader(new FileReader(file2));
            int line=0;
            while (true) {
                String b1 = in1.readLine();
                String b2 = in2.readLine();
                if ( b1 == null || b2 == null)
                {
                    if (b1 != b2)
                    {
                        System.out.println("Different sizes");
                        bDiffer = true;
                    }   
                    break;
                }
                line ++;
                if (!b1.equals(b2)) {
                    System.out.println("Different contents in line " + line );
                    System.out.println("File1: '" +b1 + "'");
                    System.out.println("File2: '" +b2 + "'");
                    bDiffer = true;
                    break;
                }
            }
            return bDiffer;
        }
        finally {
            if (in1 != null)
              in1.close();
            if (in2 != null)
              in2.close();
        }
    }

    public static Test suite() {
        return new TestSuite(FileOperatorDiffTest.class);
    }

    public void setUp() throws Exception {
        operator = null;// FIXME raplaContainer.lookupDeprecated(CachableStorageOperator.class, "raplafile");
    }

    public void testSave() throws RaplaException,IOException  {
        String testFile = "test-src/testdefault.xml";
        // FIXME real foleder
        String TEST_FOLDER_NAME = "";
        Assert.assertTrue(differ(TEST_FOLDER_NAME + "/test.xml", testFile) == false);
        operator.connect();
        ((FileOperator)operator).saveData();
        Assert.assertTrue("stored version differs from orginal " + testFile, differ(TEST_FOLDER_NAME + "/test.xml", testFile) == false);
    }

}





