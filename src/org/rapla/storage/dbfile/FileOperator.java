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
import java.util.Date;

import org.rapla.ConnectInfo;
import org.rapla.components.util.IOUtil;
import org.rapla.entities.MultiLanguageName;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.internal.AllocatableImpl;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.dynamictype.internal.AttributeImpl;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.entities.internal.CategoryImpl;
import org.rapla.entities.internal.UserImpl;
import org.rapla.entities.storage.RefEntity;
import org.rapla.framework.Configuration;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.RaplaException;
import org.rapla.framework.StartupEnvironment;
import org.rapla.framework.internal.ContextTools;
import org.rapla.framework.logger.Logger;
import org.rapla.storage.IOContext;
import org.rapla.storage.LocalCache;
import org.rapla.storage.UpdateEvent;
import org.rapla.storage.UpdateResult;
import org.rapla.storage.impl.AbstractCachableOperator;
import org.rapla.storage.impl.EntityStore;
import org.rapla.storage.impl.server.LocalAbstractCachableOperator;
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
            if ( getLogger().isDebugEnabled() )
                getLogger().debug( "Reading data from file:" + loadingURL );

            RaplaInput xmlAdapter = new RaplaInput( getLogger().getChildLogger( "reading" ) );

            EntityStore entityStore = new EntityStore( null, cache.getSuperCategory() );
            RaplaContext inputContext = new IOContext().createInputContext( context, entityStore, idTable );
            RaplaMainReader contentHandler = new RaplaMainReader( inputContext );
            xmlAdapter.read( loadingURL, contentHandler, validate );
            resolveEntities( entityStore.getList(), entityStore );
            cache.putAll( entityStore.getList() );
            cache.getSuperCategory().setReadOnly(true);
            for (User user:cache.getCollection(User.class))
            {
                Object id = ((RefEntity<?>)user).getId();
                String password = entityStore.getPassword( id );
                //System.out.println("Storing password in cache" + password);
                cache.putPassword( id, password );
            }
            // contextualize all Entities
            if ( getLogger().isDebugEnabled() )
                getLogger().debug( "Entities contextualized" );

            // save the converted file;
            if ( xmlAdapter.wasConverted() )
            {
                getLogger().info( "Storing the converted file" );
                saveData(cache);
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

        
    synchronized public void dispatch( UpdateEvent evt ) throws RaplaException
    {
    	lock.writeLock().lock();
        try
        {
	    	evt = createClosure( evt );
	        check( evt );
	        // call of update must be first to update the cache.
	        // then saveData() saves all the data in the cache
	        UpdateResult result = update( evt);
	        saveData(cache);
	        fireStorageUpdated( result );
        }
        finally
        {
        	lock.writeLock().unlock();
        }
    }

    synchronized final public void saveData(LocalCache cache) throws RaplaException
    {
        try
        {
            if ( storageFile == null )
            {
                return;
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            writeData( buffer,cache );
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

    private void writeData( OutputStream out, LocalCache cache ) throws IOException, RaplaException
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

    
    protected void createDefaultSystem(LocalCache cache) throws RaplaException
	{
    	EntityStore list = new EntityStore( null, cache.getSuperCategory() );
        
    	DynamicTypeImpl resourceType = newDynamicType(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE,"resource");
		setName(resourceType.getName(), "resource");
		add(list, resourceType);
		
		DynamicTypeImpl personType = newDynamicType(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON,"person");
		setName(personType.getName(), "person");
		add(list, personType);
		
		DynamicTypeImpl eventType = newDynamicType(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION, "event");
		setName(eventType.getName(), "event");
		add(list, eventType);
		
		String[] userGroups = new String[] {Permission.GROUP_REGISTERER_KEY, Permission.GROUP_MODIFY_PREFERENCES_KEY,Permission.GROUP_CAN_READ_EVENTS_FROM_OTHERS, Permission.GROUP_CAN_CREATE_EVENTS, Permission.GROUP_CAN_EDIT_TEMPLATES};
		CategoryImpl groupsCategory = new CategoryImpl();
		groupsCategory.setKey("user-groups");
		setName( groupsCategory.getName(), groupsCategory.getKey());
		setNew( groupsCategory);
		list.put( groupsCategory);
		for ( String catName: userGroups)
		{
			CategoryImpl group = new CategoryImpl();
			group.setKey( catName);
			setNew(group);
			setName( group.getName(), group.getKey());
			groupsCategory.addCategory( group);
			list.put( group);
		}
		cache.getSuperCategory().addCategory( groupsCategory);
		UserImpl admin = new UserImpl();
		admin.setUsername("admin");
		admin.setAdmin( true);
		setNew(admin);
		list.put( admin);
	
	    resolveEntities( list.getList(), list );
	    cache.putAll( list.getList() );
	    
    	UserImpl user = cache.getUser("admin");
    	String password ="";
		cache.putPassword( user.getId(), password );
		cache.getSuperCategory().setReadOnly(true);
	
        Date now = getCurrentTimestamp();
		AllocatableImpl allocatable = new AllocatableImpl(now, now);
	    allocatable.addPermission(allocatable.newPermission());
        Classification classification = cache.getDynamicType("resource").newClassification();
        allocatable.setClassification(classification);
        setNew(allocatable);
        classification.setValue("name", getString("test_resource"));
        allocatable.setOwner( user);
        cache.put( allocatable);
	}
    
    private void add(EntityStore list, DynamicTypeImpl type) {
    	list.put( type);
    	for (Attribute att:type.getAttributes())
    	{
    		list.put((RefEntity<?>) att);
    	}
	}

	private Attribute createStringAttribute(String key, String name) throws RaplaException {
		Attribute attribute = newAttribute(AttributeType.STRING);
		attribute.setKey(key);
		setName(attribute.getName(), name);
		return attribute;
	}

	private DynamicTypeImpl newDynamicType(String classificationType, String key) throws RaplaException {
		DynamicTypeImpl dynamicType = new DynamicTypeImpl();
		dynamicType.setAnnotation("classification-type", classificationType);
		dynamicType.setElementKey(key);
		setNew(dynamicType);
		if (classificationType.equals(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE)) {
			dynamicType.addAttribute(createStringAttribute("name", "name"));
			dynamicType.setAnnotation(DynamicTypeAnnotations.KEY_NAME_FORMAT,"{name}");
			dynamicType.setAnnotation(DynamicTypeAnnotations.KEY_COLORS,"automatic");
		} else if (classificationType.equals(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION)) {
			dynamicType.addAttribute(createStringAttribute("name","eventname"));
			dynamicType.setAnnotation(DynamicTypeAnnotations.KEY_NAME_FORMAT,"{name}");
			dynamicType.setAnnotation(DynamicTypeAnnotations.KEY_COLORS, null);
		} else if (classificationType.equals(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON)) {
			dynamicType.addAttribute(createStringAttribute("surname", "surname"));
			dynamicType.addAttribute(createStringAttribute("firstname", "firstname"));
			dynamicType.addAttribute(createStringAttribute("email", "email"));
			dynamicType.setAnnotation(DynamicTypeAnnotations.KEY_NAME_FORMAT, "{surname} {firstname}");
			dynamicType.setAnnotation(DynamicTypeAnnotations.KEY_COLORS, null);
		}
		return dynamicType;
	}

	private Attribute newAttribute(AttributeType attributeType)	throws RaplaException {
		AttributeImpl attribute = new AttributeImpl(attributeType);
		setNew(attribute);
		return attribute;
	}
	
	private <T extends RefEntity<?>> void setNew(T entity)
			throws RaplaException {

		RaplaType raplaType = entity.getRaplaType();
		entity.setId(createIdentifier(raplaType,1)[0]);
		entity.setVersion(0);
	}
	
	
	void setName(MultiLanguageName name, String to)
	{
		String currentLang = i18n.getLang();
		name.setName("en", to);
		try
		{
			String translation = i18n.getString( to);
			name.setName(currentLang, translation);
		}
		catch (Exception ex)
		{
			
		}
	}
}
