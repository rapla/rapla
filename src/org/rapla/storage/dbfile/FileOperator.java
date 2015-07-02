/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
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
import org.rapla.components.util.xml.RaplaContentHandler;
import org.rapla.components.util.xml.RaplaErrorHandler;
import org.rapla.components.util.xml.RaplaSAXHandler;
import org.rapla.components.util.xml.XMLReaderAdapter;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.PermissionContainer;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.storage.RefEntity;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.Configuration;
import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.RaplaDefaultContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.StartupEnvironment;
import org.rapla.framework.TypedComponentRole;
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
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;

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
        boolean validate = config.getChild( "validate" ).getValueAsBoolean( false );
        if ( validate )
        {
            getLogger().error("Validation currently not supported");
        }
        includeIds = config.getChild( "includeIds" ).getValueAsBoolean( false );
        
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
    final public User connect(ConnectInfo connectInfo) throws RaplaException
    {
        if ( isConnected )
            return null;
    	getLogger().info("Connecting: " + getURL()); 
        loadData();
        initIndizes();
        isConnected = true;
    	getLogger().debug("Connected");
    	return null;
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
            addInternalTypes(cache);
            if ( getLogger().isDebugEnabled() )
                getLogger().debug( "Reading data from file:" + loadingURL );

            EntityStore entityStore = new EntityStore( cache, cache.getSuperCategory() );
            RaplaDefaultContext inputContext = new IOContext().createInputContext( context, entityStore, this );
            RaplaMainReader contentHandler = new RaplaMainReader( inputContext );
            parseData(  contentHandler );
            Collection<Entity> list = entityStore.getList();
			cache.putAll( list );
			Preferences preferences = cache.getPreferencesForUserId( null);
			if ( preferences != null)
			{
			    TypedComponentRole<RaplaConfiguration> oldEntry = new TypedComponentRole<RaplaConfiguration>("org.rapla.plugin.export2ical");
                if (preferences.getEntry(oldEntry, null) != null)
                {
                    preferences.putEntry( oldEntry, null);
                }
                RaplaConfiguration entry = preferences.getEntry(RaplaComponent.PLUGIN_CONFIG, null);
                if ( entry != null)
                {
                    DefaultConfiguration pluginConfig = (DefaultConfiguration)entry.find("class", "org.rapla.export2ical.Export2iCalPlugin");
                    entry.removeChild( pluginConfig);
                }
			}

	        resolveInitial( list, this);
	        // It is important to do the read only later because some resolve might involve write to referenced objects
	        if ( inputContext.lookup(RaplaMainReader.VERSION)< 1.2)
	        {
	            migrateSpecialAttributes( list);
	        }
	        Collection<Entity> migratedTemplates = migrateTemplates();
	        cache.putAll( migratedTemplates);
	        removeInconsistentEntities(cache, list);
	        for (Entity entity: migratedTemplates) {
	            ((RefEntity)entity).setReadOnly();
	        }
	        for (Entity entity: list) 
	        {
	            ((RefEntity)entity).setReadOnly();
            }
	        cache.getSuperCategory().setReadOnly();
            for (User user:cache.getUsers())
            {
                String id = user.getId();
                String password = entityStore.getPassword( id );
                //System.out.println("Storing password in cache" + password);
                cache.putPassword( id, password );
            }
            // contextualize all Entities
            if ( getLogger().isDebugEnabled() )
                getLogger().debug( "Entities contextualized" );
            processPermissionGroups();
        }
        catch ( FileNotFoundException ex )
        {
            getLogger().warn( "Data file not found " + loadingURL + " creating default system.");
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
    
    private void migrateSpecialAttributes(Collection<Entity> list) {
        for ( Entity entity:list)
        {
            if ( entity instanceof Classifiable && entity instanceof PermissionContainer)
            {
                Classification c = ((Classifiable)entity).getClassification();
                PermissionContainer permCont = (PermissionContainer) entity;
                if ( c == null)
                {
                    continue;
                }
                Attribute attribute = c.getAttribute("permission_modify");
                if ( attribute == null)
                {
                    continue;
                }
                Collection<Object> values = c.getValues( attribute);
                if ( values == null || values.size() == 0)
                {
                    continue; 
                }
                if ( attribute.getType() == AttributeType.BOOLEAN)
                {
                    if (values.iterator().next().equals( Boolean.TRUE))
                    {
                        Permission permission = permCont.newPermission();
                        permission.setAccessLevel(Permission.ADMIN);
                        permCont.addPermission(permission);
                    }
                }
                else if ( attribute.getType() == AttributeType.CATEGORY)
                {
                    for (Object value: values)
                    {
                        Permission permission = permCont.newPermission();
                        permission.setAccessLevel(Permission.ADMIN);
                        permission.setGroup( (Category) value);
                        permCont.addPermission(permission);
                    }
                }
            }
        }
    }

    private void parseData( RaplaSAXHandler reader)  throws RaplaException,IOException {
            ContentHandler contentHandler = new RaplaContentHandler( reader);
            try {
                InputSource source = new InputSource( loadingURL.toString() );
                XMLReader parser = XMLReaderAdapter.createXMLReader(false);
                RaplaErrorHandler errorHandler = new RaplaErrorHandler(getLogger().getChildLogger( "reading" ));
                parser.setContentHandler(contentHandler);
                parser.setErrorHandler(errorHandler);
                parser.parse(source);
            } catch (SAXException ex) {
                Throwable cause = ex.getCause();
                while (cause != null && cause.getCause() != null) {
                    cause = cause.getCause();
                }
                if (ex instanceof SAXParseException) {
                    throw new RaplaException("Line: " + ((SAXParseException)ex).getLineNumber()
                                             + " Column: "+ ((SAXParseException)ex).getColumnNumber() + " "
                                             +  ((cause != null) ? cause.getMessage() : ex.getMessage())
                                             ,(cause != null) ? cause : ex );
                }
                if (cause == null) {
                    throw new RaplaException( ex);
                }
                if (cause instanceof RaplaException)
                    throw (RaplaException) cause;
                else
                    throw new RaplaException( cause);
            }
            /*  End of Exception Handling */
        }
        
        
    public void dispatch( final UpdateEvent evt ) throws RaplaException
    {
    	final UpdateResult result;
    	final Lock writeLock = writeLock();
    	try
        {
    	 	preprocessEventStorage(evt);
	        // call of update must be first to update the cache.
	        // then saveData() saves all the data in the cache
    	 	result = update( evt);
    	 	saveData(cache, null, includeIds);
        }
        finally
        {
        	unlock( writeLock );
        }
        fireStorageUpdated( result );
    }

    synchronized final public void saveData() throws RaplaException
    {
        final Lock writeLock = writeLock();
        try
        {
            saveData(cache, null, includeIds);
        }
        finally
        {
            unlock( writeLock );
        }
    }
    synchronized final public void saveData(LocalCache cache, String version) throws RaplaException
    {
    	saveData( cache,version, true);
    }

    synchronized final private void saveData(LocalCache cache, String version, boolean includeIds) throws RaplaException
    {
        try
        {
            if ( storageFile == null )
            {
                return;
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            writeData( buffer,cache, version, includeIds );
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

    private void writeData( OutputStream out, LocalCache cache,String version, boolean includeIds ) throws IOException, RaplaException
    {
        RaplaContext outputContext = new IOContext().createOutputContext( context, cache.getSuperCategoryProvider(), includeIds );
        RaplaMainWriter writer = new RaplaMainWriter( outputContext, cache );
        writer.setEncoding( encoding );
        if ( version != null)
        {
            writer.setVersion( version );
        }
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

    public String toString()
    {
        return "FileOpertator for " + getURL();
    }
    
}
