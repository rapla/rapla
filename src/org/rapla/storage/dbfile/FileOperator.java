/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Christopher Kohlhaas                                  |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org .       |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/
package org.rapla.storage.dbfile;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.concurrent.locks.Lock;

import org.rapla.ConnectInfo;
import org.rapla.components.util.IOUtil;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.framework.Configuration;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.RaplaException;
import org.rapla.framework.StartupEnvironment;
import org.rapla.framework.internal.ContextTools;
import org.rapla.framework.logger.Logger;
import org.rapla.storage.LocalCache;
import org.rapla.storage.UpdateEvent;
import org.rapla.storage.UpdateResult;
import org.rapla.storage.impl.AbstractCachableOperator;
import org.rapla.storage.impl.EntityStore;
import org.rapla.storage.impl.server.LocalAbstractCachableOperator;
import org.rapla.storage.xml.IOContext;
import org.rapla.storage.xml.RaplaMainReader;
import org.rapla.storage.xml.RaplaMainWriter;

/** Use this Operator to keep the data stored in an XML-File.
 * <p>Sample configuration:
 <pre>
 &lt;file-storage id="file">
 &lt;file>data.xml&lt;/file>
 &lt;encoding>utf-8&lt;/encoding>
 &lt;validate>no&lt;/validate>
 &lt;/facade>
 </pre>
 * <ul>
 *   <li>The file entry contains the path of the data file.
 *   If the path is not an absolute path it will be resolved
 *   relative to the location of the configuration file
 *   </li>
 *   <li>The encoding entry specifies the encoding of the xml-file.
 *   Currently only UTF-8 is tested.
 *   </li>
 *   <li>The validate entry specifies if the xml-file should be checked
 *    against a schema-file that is located under org/rapla/storage/xml/rapla.rng
 *    (Default is no)
 *   </li>
 * </ul>
 * </p>
 * <p>Note: The xmloperator doesn't check passwords.</p>

 @see AbstractCachableOperator
 @see org.rapla.storage.StorageOperator
 */
final public class FileOperator extends LocalAbstractCachableOperator
{
 	private File storageFile;
    private URL loadingURL;

    private final String encoding;
    protected boolean isConnected = false;
    final boolean includeIds ;
    /** Warning use this only for development purpose. Versions are temporary and will be lost during import/export */  
    private final boolean includeVersions;
    private final boolean validate;

    public FileOperator( RaplaContext context,Logger logger, Configuration config ) throws RaplaException
    {
        super( context, logger );
        StartupEnvironment env =  context.lookup( StartupEnvironment.class );

        URL contextRootURL = env.getContextRootURL();

        String datasourceName = config.getChild("datasource").getValue(null);
        if ( datasourceName != null)
        {
        	String filePath;
	        try {
	        	 filePath = ContextTools.resolveContext(datasourceName, context );
	        } catch (RaplaContextException ex) {
	        	filePath = "${context-root}/data.xml";
	        	String message = "JNDI config raplafile is not found using '" + filePath + "' :"+ ex.getMessage() ;
	        	getLogger().warn(message);
	        }
	        String 	resolvedPath = ContextTools.resolveContext(filePath, context);
	        try
	        {
				 storageFile = new File( resolvedPath);
				 loadingURL = storageFile.getCanonicalFile().toURI().toURL();
	        } catch (Exception e) {
	        	throw new RaplaException("Error parsing file '" + resolvedPath + "' " + e.getMessage());
	        }
        }
        else
        {
	        String fileName = config.getChild( "file" ).getValue( "data.xml" );
	        try
	        {
	            File file = new File( fileName );
	            if ( file.isAbsolute() )
	            {
	                storageFile = file;
	                loadingURL = storageFile.getCanonicalFile().toURI().toURL();
	            }
	            else
	            {
	                int startupEnv = env.getStartupMode();
	                if ( startupEnv == StartupEnvironment.WEBSTART || startupEnv == StartupEnvironment.APPLET )
	                {
	                    loadingURL = new URL( contextRootURL, fileName );
	                }
	                else
	                {
	                    File contextRootFile = IOUtil.getFileFrom( contextRootURL );
	                    storageFile = new File( contextRootFile, fileName );
	                    loadingURL = storageFile.getCanonicalFile().toURI().toURL();
	                }
	            }
	            getLogger().info("Data:" + loadingURL);
	        }
	        catch ( MalformedURLException ex )
	        {
	            throw new RaplaException( fileName + " is not an valid path " );
	        }
	        catch ( IOException ex )
	        {
	            throw new RaplaException( "Can't read " + storageFile + " " + ex.getMessage() );
	        }
        }
        encoding = config.getChild( "encoding" ).getValue( "utf-8" );
        validate = config.getChild( "validate" ).getValueAsBoolean( false );
        includeIds = config.getChild( "includeIds" ).getValueAsBoolean( false );
        // Warning use this only for development purpose. Versions are temporary and will be lost during import/export  
        includeVersions = config.getChild( "includeVersions" ).getValueAsBoolean( false );
    } 

    public String getURL()
    {
    	return loadingURL.toExternalForm();
    }

    public boolean supportsActiveMonitoring()
    {
        return false;
    }

    /** Sets the isConnected-flag and calls loadData.*/
    final public void connect(ConnectInfo connectInfo) throws RaplaException
    {
        if ( isConnected )
            return;
    	getLogger().info("Connecting: " + getURL()); 
        loadData();
        initAppointments();
        isConnected = true;
    	getLogger().debug("Connected"); 	
    }

    final public boolean isConnected()
    {
        return isConnected;
    }

    final public void disconnect() throws RaplaException
    {
    	boolean wasConnected = isConnected();
    	if ( wasConnected)
    	{
	    	getLogger().info("Disconnecting: " + getURL());
	    	cache.clearAll();
	        idTable.setCache( cache );
	        isConnected = false;
	        fireStorageDisconnected("");
	    	getLogger().debug("Disconnected");
    	}
    }
    
    final public void refresh() throws RaplaException
    {
        getLogger().warn( "Incremental refreshs are not supported" );
    }

    final protected void loadData() throws RaplaException
    {
        try
        {
            cache.clearAll();
            idTable.setCache( cache );
            addInternalTypes(cache);
            if ( getLogger().isDebugEnabled() )
                getLogger().debug( "Reading data from file:" + loadingURL );

            RaplaInput xmlAdapter = new RaplaInput( getLogger().getChildLogger( "reading" ) );
            EntityStore entityStore = new EntityStore( cache, cache.getSuperCategory() );
            RaplaContext inputContext = new IOContext().createInputContext( context, entityStore, idTable );
            RaplaMainReader contentHandler = new RaplaMainReader( inputContext );
            xmlAdapter.read( loadingURL, contentHandler, validate );
            Collection<Entity> list = entityStore.getList();
			cache.putAll( list );
            resolveEntities( list, true);
            cache.getSuperCategory().setReadOnly(true);
            for (User user:cache.getCollection(User.class))
            {
                String id = user.getId();
                String password = entityStore.getPassword( id );
                //System.out.println("Storing password in cache" + password);
                cache.putPassword( id, password );
            }
            resolveEmails( list );
            // contextualize all Entities
            if ( getLogger().isDebugEnabled() )
                getLogger().debug( "Entities contextualized" );

            // save the converted file;
            if ( xmlAdapter.wasConverted() )
            {
                getLogger().info( "Storing the converted file" );
                saveData(cache, includeIds);
            }
            
        }
        catch ( FileNotFoundException ex )
        {
        	createDefaultSystem(cache);
        }
        catch ( IOException ex )
        {
        	getLogger().warn( "Loading error: " + loadingURL);
        	throw new RaplaException( "Can't load file at " + loadingURL + ": " + ex.getMessage() );
        }
        catch ( RaplaException ex )
        {
            throw ex;
        }
        catch ( Exception ex )
        {
            throw new RaplaException( ex );
        }
    }
        
    public void dispatch( final UpdateEvent evt ) throws RaplaException
    {
    	final UpdateResult result;
    	final Lock writeLock = writeLock();
    	try
        {
    	 	final UpdateEvent closure = checkAndCreateClosure(evt);
	        // call of update must be first to update the cache.
	        // then saveData() saves all the data in the cache
	        result = update( closure);
	        saveData(cache, includeIds);
        }
        finally
        {
        	unlock( writeLock );
        }
        fireStorageUpdated( result );
    }

    synchronized final public void saveData(LocalCache cache) throws RaplaException
    {
    	saveData( cache, true);
    }

    synchronized final private void saveData(LocalCache cache, boolean includeIds) throws RaplaException
    {
        try
        {
            if ( storageFile == null )
            {
                return;
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            writeData( buffer,cache, includeIds );
            byte[] data = buffer.toByteArray();
            buffer.close();
            File parentFile = storageFile.getParentFile();
            if (!parentFile.exists())
            {
            	getLogger().info("Creating directory " + parentFile.toString());
            	parentFile.mkdirs();
            }
            //String test = new String( data);
            moveFile( storageFile, storageFile.getPath() + ".bak" );
            OutputStream out = new FileOutputStream( storageFile );
            out.write( data );
            out.close();
        }
        catch ( IOException e )
        {
            throw new RaplaException( e.getMessage() );
        }
    }

    private void writeData( OutputStream out, LocalCache cache, boolean includeIds ) throws IOException, RaplaException
    {
        RaplaContext outputContext = new IOContext().createOutputContext( context, cache.getSuperCategoryProvider(), includeIds, includeVersions );
        RaplaMainWriter writer = new RaplaMainWriter( outputContext, cache );
        writer.setEncoding( encoding );
        BufferedWriter w = new BufferedWriter(new OutputStreamWriter(out,encoding));
        writer.setWriter(w);
        writer.printContent();
        w.flush();
    }

    private void moveFile( File file, String newPath ) 
    {
        File backupFile = new File( newPath );
        backupFile.delete();
        file.renameTo( backupFile );
    }

    
}
