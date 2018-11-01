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
import org.junit.Before;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.logger.Logger;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.dbfile.FileOperator;
import org.rapla.storage.dbfile.tests.FileOperatorTest.MyFileIO;
import org.rapla.test.util.RaplaTestCase;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;

@Ignore
@RunWith(JUnit4.class)
public class FileOperatorDiffTest
{
    CachableStorageOperator operator;
    private MyFileIO fileIO;
    private String resolvedPath;


    public boolean differ(byte[] bytes, String file2) throws IOException
    {
        BufferedReader in1 = null;
        BufferedReader in2 = null;
        boolean bDiffer = false;
        try
        {
            in1 = new BufferedReader(new StringReader(new String(bytes, "utf-8")));
            in2 = new BufferedReader(new FileReader(file2));
            int line = 0;
            while (true)
            {
                String b1 = in1.readLine();
                String b2 = in2.readLine();
                if (b1 == null || b2 == null)
                {
                    if (b1 != b2)
                    {
                        System.out.println("Different sizes");
                        bDiffer = true;
                    }
                    break;
                }
                line++;
                if (!b1.equals(b2))
                {
                    System.out.println("Different contents in line " + line);
                    System.out.println("File1: '" + b1 + "'");
                    System.out.println("File2: '" + b2 + "'");
                    bDiffer = true;
                    break;
                }
            }
            return bDiffer;
        }
        finally
        {
            if (in1 != null)
                in1.close();
            if (in2 != null)
                in2.close();
        }
    }

    public static Test suite()
    {
        return new TestSuite(FileOperatorDiffTest.class);
    }

    @Before
    public void setUp() throws Exception
    {
        Logger logger = RaplaTestCase.initLoger();
        String file = "/testdefault.xml";
        resolvedPath = RaplaTestCase.getTestDataFile(file);
        fileIO = new MyFileIO(resolvedPath, logger);
        RaplaFacade facade = RaplaTestCase.createFacadeWithFile(logger, resolvedPath, fileIO);
        operator = (CachableStorageOperator) facade.getOperator();
    }

    @org.junit.Test
    public void testSave() throws RaplaException, IOException
    {
        String testFile = new File(resolvedPath).getCanonicalFile().toURI().toURL().getFile();
        Assert.assertTrue(differ(fileIO.data, testFile) == false);
        operator.connect();
        ((FileOperator) operator).saveData();
        Assert.assertTrue("stored version differs from orginal " + testFile, differ(fileIO.data, testFile) == false);
    }

}
