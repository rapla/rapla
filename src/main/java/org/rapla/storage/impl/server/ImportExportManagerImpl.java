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
import org.rapla.logger.Logger;
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
    CachableStorageOperator source;
    CachableStorageOperator dest;
    Logger logger;


    public ImportExportManagerImpl(Logger logger,FileOperator source,DBOperator dest)
    {
        this.logger =  logger;
        this.source = source;
        this.dest = dest;
        
    }
    
    protected Logger getLogger() {
        return logger.getChildLogger("importexport");
    }

    /* Import the source into dest.   */
    public void doImport() throws RaplaException {
        Logger logger = getLogger();
		CachableStorageOperator source = getSource();
        CachableStorageOperator destination = getDestination();
        logger.info("Import from " + source.toString() + " into " + dest.toString());
        source.connect();
		doConvert(source,destination);
        logger.info("Import completed");
    }

    /* Export the dest into source.   */
    public void doExport() throws RaplaException {
        Logger logger = getLogger();
        CachableStorageOperator source = getSource();
        CachableStorageOperator destination = getDestination();
        logger.info("Export from " +  dest.toString() + " into " + source.toString());
        destination.connect();
		doConvert(destination,source);
        logger.info("Export completed");
    }

    private void doConvert(final CachableStorageOperator cachableStorageOperator1,final CachableStorageOperator cachableStorageOperator2) throws RaplaException {
    	cachableStorageOperator1.runWithReadLock(cache -> cachableStorageOperator2.saveData(cache, null));

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
