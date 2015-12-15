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

import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.RaplaResources;
import org.rapla.components.i18n.internal.DefaultBundleManager;
import org.rapla.components.util.CommandScheduler;
import org.rapla.entities.domain.permission.DefaultPermissionControllerSupport;
import org.rapla.entities.domain.permission.PermissionController;
import org.rapla.entities.domain.permission.impl.RaplaDefaultPermissionImpl;
import org.rapla.entities.dynamictype.internal.StandardFunctions;
import org.rapla.entities.extensionpoints.FunctionFactory;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.internal.FacadeImpl;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.internal.DefaultScheduler;
import org.rapla.framework.internal.RaplaLocaleImpl;
import org.rapla.framework.logger.Logger;
import org.rapla.framework.logger.RaplaBootstrapLogger;
import org.rapla.storage.dbfile.FileOperator;
import org.rapla.storage.tests.AbstractOperatorTest;
import org.rapla.test.util.RaplaTestCase;
import org.xml.sax.InputSource;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;

@RunWith(JUnit4.class)
public class FileOperatorTest extends AbstractOperatorTest {

    ClientFacade facade;
    Logger logger;

    @Before
    public void setUp() throws IOException
    {
        logger = RaplaTestCase.initLoger();
        String file = "testdefault.xml";
        String resolvedPath = RaplaTestCase.getTestDataFile(file);
        facade = RaplaTestCase.createFacadeWithFile(logger, resolvedPath, new MyFileIO(resolvedPath,logger));
    }

    static class MyFileIO implements FileOperator.FileIO
    {
        byte[] data;
        Logger logger;

        MyFileIO(final String resolvedPath,final Logger logger) throws IOException
        {
            this.logger = logger;
            Path path = Paths.get(resolvedPath);
            try(AsynchronousFileChannel fileChannel = AsynchronousFileChannel.open(path, StandardOpenOption.READ))
            {

                ByteBuffer buffer = ByteBuffer.allocate(1024*1024);
                long position = 0;

                Future<Integer> operation = fileChannel.read(buffer, position);

                while (!operation.isDone())
                    ;

                buffer.flip();
                data = new byte[buffer.limit()];
                buffer.get(data);
            }
            String stringData = new String( data);
            logger.debug("Reading data " + stringData);
        }

        @Override public InputSource getInputSource(URI storageURL) throws IOException
        {
            ByteArrayInputStream in = new ByteArrayInputStream(data);
            return new InputSource(new InputStreamReader(in, "utf-8"));
        }

        @Override public void write(FileOperator.RaplaWriter writer, URI storageURL) throws IOException
        {
            ByteArrayOutputStream outBytes =new ByteArrayOutputStream();
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(outBytes,"utf-8"));
            writer.write(out);
            out.close();
            data = outBytes.toByteArray();
            String stringData = new String( data);
            logger.debug("Writing data " + stringData);
        }
    }

    @Override protected ClientFacade getFacade()
    {
        return facade;
    }
}





