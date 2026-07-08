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
package org.rapla.storage.impl.server;
import org.rapla.framework.RaplaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.CachableStorageOperatorCommand;
import org.rapla.storage.ImportExportManager;
import org.rapla.storage.LocalCache;
import org.rapla.storage.dbfile.FileOperator;
import org.rapla.storage.dbsql.DBOperator;
/**  Imports the content of on store into another.
 Export does an import with source and destination exchanged.
*/
public class ImportExportManagerImpl implements ImportExportManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImportExportManagerImpl.class);
    CachableStorageOperator source;
    CachableStorageOperator dest;

    public ImportExportManagerImpl(CachableStorageOperator source, CachableStorageOperator dest)
    {
        this.source = source;
        this.dest = dest;
    }

    /* Import the source into dest.   */
    public void doImport() throws RaplaException {
        CachableStorageOperator source = getSource();
        CachableStorageOperator destination = getDestination();
        LOGGER.info("Import from {} into {}", source, destination);
        source.connect();
		doConvert(source,destination);
        LOGGER.info("Import completed");
    }

    /* Export the dest into source.   */
    public void doExport() throws RaplaException {
        CachableStorageOperator source = getSource();
        CachableStorageOperator destination = getDestination();
        LOGGER.info("Export from {} into {}", destination, source);
        destination.connect();
		doConvert(destination,source);
        LOGGER.info("Export completed");
    }

    private void doConvert(final CachableStorageOperator cachableStorageOperator1,final CachableStorageOperator cachableStorageOperator2) throws RaplaException {
    	cachableStorageOperator1.runWithReadLock((cache, syncEntityList, artifacts) -> cachableStorageOperator2.saveData(cache, syncEntityList, artifacts, null));
    }

	@Override
	public CachableStorageOperator getSource() throws RaplaException 
	{
		return source;
	}

	@Override
	public CachableStorageOperator getDestination() throws RaplaException 
	{
		return dest;
	}
}
