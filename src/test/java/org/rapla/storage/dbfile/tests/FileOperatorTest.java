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
import java.util.Collection;
import java.util.concurrent.Future;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.entities.storage.ImportExportDirections;
import org.rapla.entities.storage.ImportExportEntity;
import org.rapla.entities.storage.internal.ImportExportEntityImpl;
import org.rapla.facade.RaplaFacade;
import org.rapla.logger.Logger;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.dbfile.FileOperator;
import org.rapla.storage.tests.AbstractOperatorTest;
import org.rapla.test.util.RaplaTestCase;
import org.xml.sax.InputSource;

@RunWith(JUnit4.class)
public class FileOperatorTest extends AbstractOperatorTest {

    RaplaFacade facade;
    Logger logger;

    @Before
    public void setUp() throws Exception
    {
        logger = RaplaTestCase.initLoger();
        String file = "testdefault.xml";
        String resolvedPath = RaplaTestCase.getTestDataFile(file);
        facade = RaplaTestCase.createFacadeWithFile(logger, resolvedPath, new MyFileIO(resolvedPath,logger));
    }

    public static class MyFileIO implements FileOperator.FileIO
    {
        byte[] data;
        Logger logger;

        public MyFileIO(final String resolvedPath,final Logger logger) throws IOException
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

    @Override protected RaplaFacade getFacade()
    {
        return facade;
    }
    
    @Test
    public void testImportExportEntity() throws Exception
    {
        final ImportExportEntityImpl importExportEntity = new ImportExportEntityImpl();
        final String context = "context";
        final int direction = ImportExportDirections.EXPORT;
        final String externalSystem = "ExtSys";
        final String data = "data";
        final String id = "testid";
        final String raplaId = "raplaId";
        importExportEntity.setContext(context);
        importExportEntity.setData(data);
        importExportEntity.setDirection(direction);
        importExportEntity.setExternalSystem(externalSystem);
        importExportEntity.setId(id);
        importExportEntity.setRaplaId(raplaId);
        facade.store(importExportEntity);
        final CachableStorageOperator operator = getOperator();
        {
            final Collection<ImportExportEntity> importExportEntities = operator.getImportExportEntities("ExtSys", ImportExportDirections.EXPORT);
            Assert.assertEquals(1, importExportEntities.size());
            final ImportExportEntity next = importExportEntities.iterator().next();
            Assert.assertEquals(data, next.getData());
            Assert.assertEquals(context, next.getContext());
            Assert.assertEquals(direction, next.getDirection());
            Assert.assertEquals(externalSystem, next.getExternalSystem());
            Assert.assertEquals(id, next.getId());
            Assert.assertEquals(raplaId, next.getRaplaId());
        }
        operator.disconnect();
        operator.connect();
        {
            final Collection<ImportExportEntity> importExportEntities = operator.getImportExportEntities("ExtSys", ImportExportDirections.EXPORT);
            Assert.assertEquals(1, importExportEntities.size());
            final ImportExportEntity next = importExportEntities.iterator().next();
            Assert.assertEquals(data, next.getData());
            Assert.assertEquals(context, next.getContext());
            Assert.assertEquals(direction, next.getDirection());
            Assert.assertEquals(externalSystem, next.getExternalSystem());
            Assert.assertEquals(id, next.getId());
            Assert.assertEquals(raplaId, next.getRaplaId());
        }
        facade.remove(importExportEntity);
        {
            final Collection<ImportExportEntity> importExportEntities = operator.getImportExportEntities("ExtSys", ImportExportDirections.EXPORT);
            Assert.assertEquals(0, importExportEntities.size());
        }
        operator.disconnect();
        operator.connect();
        facade.remove(importExportEntity);
        {
            final Collection<ImportExportEntity> importExportEntities = operator.getImportExportEntities("ExtSys", ImportExportDirections.EXPORT);
            Assert.assertEquals(0, importExportEntities.size());
        }
    }
}





