/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Christopher Kohlhaas                                  |
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
import org.rapla.framework.Configuration;
import org.rapla.framework.ConfigurationException;
import org.rapla.framework.Container;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.ImportExportManager;
import org.rapla.storage.LocalCache;
/**  Imports the content of on store into another.
 Export does an import with source and destination exchanged.
<p>Configuration:</p>
<pre>
  <importexport id="importexport" activation="request">
    <source>raplafile</source>
    <dest>rapladb</dest>
  </importexport>
</pre>
*/
public class ImportExportManagerImpl implements ImportExportManager {

    Container container;
    String sourceString;
    String destString;
    Logger logger;
    
    public ImportExportManagerImpl(RaplaContext context,Configuration configuration) throws RaplaException
    {
        this.logger =  context.lookup( Logger.class);
        this.container = context.lookup( Container.class);
        try {
            sourceString = configuration.getChild("source").getValue();
            destString = configuration.getChild("dest").getValue();
        } catch (ConfigurationException e) {
            throw new RaplaException( e);
        }
    }
    
    protected Logger getLogger() {
        return logger.getChildLogger("importexport");
    }

    /* Import the source into dest.   */
    public void doImport() throws RaplaException {
        getLogger().info("Import from " + sourceString + " into " + destString);
        doConvert(getSource(),getDestination());
        getLogger().info("Import completed");
    }

    /* Export the dest into source.   */
    public void doExport() throws RaplaException {
        getLogger().info("Export from " +  destString + " into " + sourceString);
        doConvert(getDestination(),getSource());
        getLogger().info("Export completed");
    }

    private void doConvert(CachableStorageOperator cachableStorageOperator1,CachableStorageOperator cachableStorageOperator2) throws RaplaException {
    	LocalCache cache = cachableStorageOperator1.getCache();
    	cachableStorageOperator2.saveData(cache);
    }

	@Override
	public CachableStorageOperator getSource() throws RaplaException 
	{
		CachableStorageOperator lookup = container.lookup(CachableStorageOperator.class, sourceString);
		return lookup;
	}

	@Override
	public CachableStorageOperator getDestination() throws RaplaException 
	{
		CachableStorageOperator lookup = container.lookup(CachableStorageOperator.class, destString);
		return lookup;
	}
}
