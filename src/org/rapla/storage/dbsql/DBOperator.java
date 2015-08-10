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
package org.rapla.storage.dbsql;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Driver;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.locks.Lock;

import javax.sql.DataSource;

import org.rapla.ConnectInfo;
import org.rapla.components.util.xml.RaplaNonValidatedInput;
import org.rapla.entities.Entity;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.storage.RefEntity;
import org.rapla.facade.Conflict;
import org.rapla.framework.Configuration;
import org.rapla.framework.ConfigurationException;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.RaplaDefaultContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.internal.ContextTools;
import org.rapla.framework.logger.Logger;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.CachableStorageOperatorCommand;
import org.rapla.storage.IdCreator;
import org.rapla.storage.ImportExportManager;
import org.rapla.storage.LocalCache;
import org.rapla.storage.PreferencePatch;
import org.rapla.storage.UpdateEvent;
import org.rapla.storage.UpdateResult;
import org.rapla.storage.impl.EntityStore;
import org.rapla.storage.impl.server.LocalAbstractCachableOperator;
import org.rapla.storage.xml.IOContext;
import org.rapla.storage.xml.RaplaMainWriter;

/** This Operator is used to store the data in a SQL-DBMS.*/
public class DBOperator extends LocalAbstractCachableOperator
{
    private String driverClassname;
    protected String datasourceName;
    protected String user;
    protected String password;
    protected String dbURL;
    protected Driver dbDriver;
    protected boolean isConnected;
    Properties dbProperties = new Properties();
    boolean bSupportsTransactions = false;
    boolean hsqldb = false;

    private String backupEncoding;
    private String backupFileName;
    
    Object lookup;
    String connectionName;
	
    public DBOperator(RaplaContext context, Logger logger,Configuration config) throws RaplaException {
        super( context, logger);

        String backupFile = config.getChild("backup").getValue("");
        if (backupFile != null)
        	backupFileName = ContextTools.resolveContext( backupFile, context);
       
        backupEncoding = config.getChild( "encoding" ).getValue( "utf-8" );
 
        datasourceName = config.getChild("datasource").getValue(null);
        // dont use datasource (we have to configure a driver )
        if ( datasourceName == null)
        {
	        try 
	        {
	            driverClassname = config.getChild("driver").getValue();
	            dbURL = ContextTools.resolveContext( config.getChild("url").getValue(), context);
                getLogger().info("Data:" + dbURL);
	        } 
	        catch (ConfigurationException e) 
	        {
	            throw new RaplaException( e );
	        }
	        dbProperties.setProperty("user", config.getChild("user").getValue("") );
	        dbProperties.setProperty("password", config.getChild("password").getValue("") );
	        hsqldb = config.getChild("hsqldb-shutdown").getValueAsBoolean( false );
	        try 
	        {
	            dbDriver = (Driver) getClass().getClassLoader().loadClass(driverClassname).newInstance();
	        } 
	        catch (ClassNotFoundException e) 
	        {
	            throw new RaplaException("DB-Driver not found: " + driverClassname +
	                                          "\nCheck classpath!");
	        } 
	        catch (Exception e) 
	        {
	            throw new RaplaException("Could not instantiate DB-Driver: " + driverClassname, e);
	        }
        }
        else
        {
	        try {
	        	lookup  = ContextTools.resolveContextObject(datasourceName, context );
	        } catch (RaplaContextException ex) {
	        	throw new RaplaDBException("Datasource " + datasourceName + " not found"); 
	        }
        }
        
    }

    public boolean supportsActiveMonitoring() {
        return false;
    }

    public String getConnectionName()
    {
    	if ( connectionName != null)
    	{
    		return connectionName;
    	}
    	if ( datasourceName != null)
    	{
    		return datasourceName;
    	}
    	return dbURL;
    }

    public Connection createConnection() throws RaplaException {
        boolean withTransactionSupport = true;
        return createConnection(withTransactionSupport);
    }
    
    public Connection createConnection(boolean withTransactionSupport) throws RaplaException {
        return createConnection(withTransactionSupport, 0);
    }
    private Connection createConnection(final boolean withTransactionSupport, final int count) throws RaplaException {
    	Connection connection = null;
        try {
        	 //datasource lookup 
        	Object source = lookup;
//        	if ( lookup instanceof String)
//        	{
//        		InitialContext ctx = new InitialContext();
//        		source  = ctx.lookup("java:comp/env/"+ lookup);
//        	}
//        	else
//        	{
//        		source = lookup;
//        	}
        	
        	if ( source != null)
        	{
        		ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        		try
        		{
        			try
        			{
        				Thread.currentThread().setContextClassLoader(source.getClass().getClassLoader());
        			}
        			catch (Exception ex)
        			{
        			}
	    			try
	        		{
	        			DataSource ds = (DataSource) source;
	        			connection = ds.getConnection();	 
	        		}
	        		catch (ClassCastException ex)
	        		{
	        			String text = "Datasource object " + source.getClass() + " does not implement a datasource interface.";
	        			getLogger().error( text);
						throw new RaplaDBException(text);
	        		}
        		}
        		finally
        		{
        			try
        			{
        				Thread.currentThread().setContextClassLoader(contextClassLoader);
        			}
        			catch (Exception ex)
        			{
        			}
        		}
        	 }
        	 // or driver initialization
        	 else
        	 {
        		 connection = dbDriver.connect(dbURL, dbProperties);
        		 connectionName = dbURL;
                 if (connection == null)
                 {
                     throw new RaplaDBException("No driver found for: " + dbURL + "\nCheck url!");
                 }
        	 }
             if ( withTransactionSupport)
             {
                 bSupportsTransactions = connection.getMetaData().supportsTransactions();
                 if (bSupportsTransactions)
                 {
                     connection.setAutoCommit( false );
                 }
                 else
                 {
                     getLogger().warn("No Transaction support");
                 }
             }
             else
             {
                 connection.setAutoCommit( true );
             }
             //connection.createStatement().execute( "ALTER TABLE RESOURCE RENAME TO RAPLA_RESOURCE");
// 		     connection.commit();
             return connection;
        } catch (Throwable ex) {
        	 if ( connection != null)
        	 {
        		 close(connection);
        	 }
        	 if ( ex instanceof SQLException && count <2)
        	 {
        	     getLogger().warn("Getting error " + ex.getMessage() + ". Retrying.");
        	     return createConnection(withTransactionSupport, count+1);
        	 }
             if ( ex instanceof RaplaDBException)
             {
                 throw (RaplaDBException) ex;
             }
             throw new RaplaDBException("DB-Connection aborted",ex);
        }
    }

    synchronized public User connect(ConnectInfo connectInfo) throws RaplaException {
        if (isConnected())
        {
            return null;
        }
    	getLogger().debug("Connecting: " + getConnectionName());
        loadData();
        initIndizes();
        isConnected = true;
    	getLogger().debug("Connected");
    	return null;
    }

    public boolean isConnected() {
        return isConnected;
    }
    
    final public void refresh() throws RaplaException {
        getLogger().warn("Incremental refreshs are not supported");
    }

    synchronized public void disconnect() throws RaplaException 
    {
    	if (!isConnected())
    		return;
        backupData();
    	getLogger().info("Disconnecting: " + getConnectionName());

        cache.clearAll();
        //idTable.setCache( cache );

        // HSQLDB Special
        if ( hsqldb ) 
        {
            String sql  ="SHUTDOWN COMPACT";
            try 
            {
                Connection connection = createConnection();
                Statement statement = connection.createStatement();
                statement.execute(sql);
                statement.close();
            } 
            catch (SQLException ex) 
            {
                 throw new RaplaException( ex);
            }
        }
        isConnected = false;
        fireStorageDisconnected("");
    	getLogger().info("Disconnected");
    }

    public final void loadData() throws RaplaException {
    	Connection c = null;
    	Lock writeLock = writeLock();
        try {
        	c = createConnection();
        	connectionName = c.getMetaData().getURL();
			getLogger().info("Using datasource " + c.getMetaData().getDatabaseProductName() +": " +  connectionName);
    		if (upgradeDatabase(c))
    		{
    		    close( c);
    		    c = null;
    		    c = createConnection();
    		}
			cache.clearAll();
  	        addInternalTypes(cache);
  	        loadData( c, cache );
  	        
  	        if ( getLogger().isDebugEnabled())
  	        	getLogger().debug("Entities contextualized");
	
  	        if ( getLogger().isDebugEnabled())
  	        	getLogger().debug("All ConfigurationReferences resolved");
        } 
        catch (RaplaException ex) 
        {
            throw ex;
        } 
        catch (Exception ex) 
        {
            throw new RaplaException( ex);
        } 
        finally 
        {
        	unlock(writeLock);
        	close ( c );
        	c = null;
        }
    }

    @SuppressWarnings("deprecation")
    private boolean upgradeDatabase(Connection c) throws SQLException, RaplaException, RaplaContextException {
        Map<String, TableDef> schema = loadDBSchema(c);
        TableDef dynamicTypeDef = schema.get("DYNAMIC_TYPE"); 
        boolean empty = false;
        int oldIdColumnCount = 0;
        int unpatchedTables = 0;
        
        if ( dynamicTypeDef != null)
        {
            PreparedStatement prepareStatement = null;
            ResultSet set = null;
            try
            {
                prepareStatement = c.prepareStatement("select * from DYNAMIC_TYPE");
                set = prepareStatement.executeQuery();
                empty = !set.next();
            }
            finally
            {
                if ( set != null)
                {
                    set.close();
                }
                if ( prepareStatement != null)
                {
                    prepareStatement.close();
                }
            }
            
            {
                org.rapla.storage.dbsql.pre18.RaplaPre18SQL raplaSQLOutput =  new org.rapla.storage.dbsql.pre18.RaplaPre18SQL(createOutputContext(cache));
                Map<String,String> idColumnMap = raplaSQLOutput.getIdColumns();
                oldIdColumnCount = idColumnMap.size();
                for ( Map.Entry<String, String> entry:idColumnMap.entrySet())
                {
                    String table = entry.getKey();
                    String idColumnName = entry.getValue();
                    TableDef tableDef = schema.get(table);
                    if ( tableDef != null)
                    {
                        ColumnDef idColumn = tableDef.getColumn(idColumnName);
                        if ( idColumn == null)
                        {
                            throw new RaplaException("Id column not found");
                        }
                        if ( idColumn.isIntType())
                        {
                            unpatchedTables++;
        //                        else if ( type.toLowerCase().contains("varchar"))
        //                        {
        //                            patchedTables++;
        //                        }
                        }
                    }
                }
            }
        } 
        else
        {
            empty = true;
        }
        if ( !empty && (unpatchedTables == oldIdColumnCount && unpatchedTables > 0))
        {
            getLogger().warn("Old database schema detected. Initializing conversion!");
            org.rapla.storage.dbsql.pre18.RaplaPre18SQL raplaSQLOutput =  new org.rapla.storage.dbsql.pre18.RaplaPre18SQL(createOutputContext(cache));
            raplaSQLOutput.createOrUpdateIfNecessary( c, schema);

            CachableStorageOperator sourceOperator = context.lookup(ImportExportManager.class).getSource();
            if ( sourceOperator == this)
            {
                throw new RaplaException("Can't export old db data, because no data export is set.");
            }
            LocalCache cache =new LocalCache();
            cache.clearAll();
            addInternalTypes(cache);
            loadOldData(c, cache );
            
            getLogger().info("Old database loaded in memory. Now exporting to xml: " + sourceOperator);
            sourceOperator.saveData(cache, "1.1");
            getLogger().info("XML export done.");
            
            //close( c);
        }
        
        
        if ( empty  || unpatchedTables > 0  ) 
        {
            CachableStorageOperator sourceOperator = context.lookup(ImportExportManager.class).getSource();
            if ( sourceOperator == this)
            {
                throw new RaplaException("Can't import, because db is configured as source.");
            }
            if ( unpatchedTables > 0)
            {
                getLogger().info("Reading data from xml.");
            }
            else
            {
                getLogger().warn("Empty database. Importing data from " + sourceOperator);
            }
            sourceOperator.connect();
            if ( unpatchedTables > 0)
            {
                org.rapla.storage.dbsql.pre18.RaplaPre18SQL raplaSQLOutput =  new org.rapla.storage.dbsql.pre18.RaplaPre18SQL(createOutputContext(cache));
                getLogger().warn("Dropping database tables and reimport from " + sourceOperator);
                raplaSQLOutput.dropAll(c);
                // we need to load the new schema after dropping
                schema = loadDBSchema(c);
            }
            {
                RaplaSQL raplaSQLOutput =  new RaplaSQL(createOutputContext(cache));
                raplaSQLOutput.createOrUpdateIfNecessary( c, schema);
            }
            close( c);
            c = null;
            c = createConnection();
            final Connection conn = c;
            sourceOperator.runWithReadLock( new CachableStorageOperatorCommand() {
                
                @Override
                public void execute(LocalCache cache) throws RaplaException {
                    try
                    {
                        saveData(conn, cache);
                    }
                    catch (SQLException ex)
                    {
                        throw new RaplaException(ex.getMessage(),ex);
                    } 
                }
            });
            return true;
        }
        else
        {
            RaplaSQL raplaSQLOutput =  new RaplaSQL(createOutputContext(cache));
            raplaSQLOutput.createOrUpdateIfNecessary( c, schema);
        }
        return false;
    }
    
    private Map<String, TableDef> loadDBSchema(Connection c)
			throws SQLException {
		Map<String,TableDef> tableMap = new LinkedHashMap<String,TableDef>();
		List<String> catalogList = new ArrayList<String>();
		DatabaseMetaData metaData = c.getMetaData();
		{
			ResultSet set = metaData.getCatalogs();
			try
			{
				while (set.next())
				{
					String name = set.getString("TABLE_CAT");
					catalogList.add( name);
				}
			}
			finally
			{
				set.close();
			}
		}
		List<String> schemaList = new ArrayList<String>();
		{
			ResultSet set = metaData.getSchemas();
			try
			{
				while (set.next())
				{
					String name = set.getString("TABLE_SCHEM");
					String cat = set.getString("TABLE_CATALOG");
					schemaList.add( name);
					if ( cat != null)
					{
						catalogList.add( name);
					}
				}
			}
			finally
			{
				set.close();
			}
		}
		
		if ( catalogList.isEmpty())
		{
			catalogList.add( null);
		}
		Map<String,Set<String>> tables = new LinkedHashMap<String, Set<String>>();
		for ( String cat: catalogList)
		{
			LinkedHashSet<String> tableSet = new LinkedHashSet<String>();
			String[] types = new String[] {"TABLE"};
			tables.put( cat, tableSet);
			{
				ResultSet set = metaData.getTables(cat, null, null, types);
				try
				{
					while (set.next())
					{
						String name = set.getString("TABLE_NAME");
						tableSet.add( name);
					}
				}
				finally
				{
					set.close();
				}
			}
		}
		for ( String cat: catalogList)
		{
			Set<String> tableNameSet = tables.get( cat);
			for ( String tableName: tableNameSet )
			{
				ResultSet set = metaData.getColumns(null, null,tableName, null);
				try
				{
					while (set.next())
					{
						String table = set.getString("TABLE_NAME").toUpperCase(Locale.ENGLISH);
						TableDef tableDef = tableMap.get( table);
						if ( tableDef == null )
						{
							tableDef = new TableDef(table);
							tableMap.put( table,tableDef );
						}
						ColumnDef columnDef = new ColumnDef( set);
						tableDef.addColumn( columnDef);
					}
				}
				finally
				{
					set.close();
				}
			}
		}
		return tableMap;
	}
    
    public void dispatch(UpdateEvent evt) throws RaplaException {
    	UpdateResult result;
    	Lock writeLock = writeLock();
    	try
        {
    	    preprocessEventStorage(evt);
    	    Collection<Entity> storeObjects = evt.getStoreObjects();
            List<PreferencePatch> preferencePatches = evt.getPreferencePatches();
            Collection<String> removeObjects = evt.getRemoveIds();
	        dbStore(storeObjects, preferencePatches, removeObjects);
	        result = super.update(evt);
        } 
    	finally
        {
    		unlock( writeLock );
        }
        fireStorageUpdated(result);
    }

    private void dbStore(Collection<Entity> storeObjects, List<PreferencePatch> preferencePatches, Collection<String> removeObjects) throws RaplaException, RaplaDBException {
        Connection connection = createConnection();
        try {
            RaplaSQL raplaSQLOutput =  new RaplaSQL(createOutputContext(cache));
            raplaSQLOutput.store( connection, storeObjects);
            raplaSQLOutput.storePatches( connection, preferencePatches);
            for (String id: removeObjects) {
                 Entity entity = cache.get(id);
                 if (entity != null)
                     raplaSQLOutput.remove( connection, entity);
            }
            if (bSupportsTransactions) {
                getLogger().debug("Commiting");
                connection.commit();
            }
         } catch (Exception ex) {
             try {
                 if (bSupportsTransactions) {
                     connection.rollback();
                     getLogger().error("Doing rollback for: " + ex.getMessage()); 
                     throw new RaplaDBException(getI18n().getString("error.rollback"),ex);
                 } else {
                     String message = getI18n().getString("error.no_rollback");
                     getLogger().error(message);
                     forceDisconnect();
                     throw new RaplaDBException(message,ex);
                 }
             } catch (SQLException sqlEx) {
                 String message = "Unrecoverable error while storing";
                 getLogger().error(message, sqlEx);
                 forceDisconnect();
                 throw new RaplaDBException(message,sqlEx);
             }
        } finally {
            close( connection );
        }
    }
    
    @Override
    protected void removeConflictsFromDatabase(Collection<Conflict> disabledConflicts) 
    {
        super.removeConflictsFromDatabase(disabledConflicts);
        if ( disabledConflicts.size() <1)
        {
            return;
        }
        Collection<Entity> storeObjects = Collections.emptyList();
        List<PreferencePatch> preferencePatches = Collections.emptyList();
        Collection<String> removeObjects = new ArrayList<String>();
        for ( Conflict conflict:disabledConflicts)
        {
            removeObjects.add( conflict.getId());
        }
        try
        {
            dbStore(storeObjects, preferencePatches, removeObjects);
        }
        catch (RaplaException ex)
        {
            getLogger().warn("disabled conflicts could not be removed from database due to ", ex);
        }
       
    }

	public void removeAll() throws RaplaException {
        Connection connection = createConnection();
        try {
             RaplaSQL raplaSQLOutput =  new RaplaSQL(createOutputContext(cache));
             raplaSQLOutput.removeAll( connection );
             connection.commit();
             // do something here
             getLogger().info("DB cleared");
        } 
        catch (SQLException ex) 
        {
            throw new RaplaException(ex);
        }
        finally
        {
            close( connection );
        }
    }
    
    public synchronized void saveData(LocalCache cache, String version) throws RaplaException {
    	Connection connection = createConnection();
    	try {
    		Map<String, TableDef> schema = loadDBSchema(connection);
    		RaplaSQL raplaSQLOutput =  new RaplaSQL(createOutputContext(cache));
    		raplaSQLOutput.createOrUpdateIfNecessary( connection, schema);
        	saveData(connection, cache);
        } 
        catch (SQLException ex) 
        {
            throw new RaplaException(ex);
        }
        finally
        {
        	close( connection );
        }
    }

	protected void saveData(Connection connection, LocalCache cache) throws RaplaException, SQLException {
		String connectionName = getConnectionName();
		getLogger().info("Importing Data into " + connectionName);
		RaplaSQL raplaSQLOutput =  new RaplaSQL(createOutputContext(cache));
		
//		if (dropOldTables)
//		{
//		    getLogger().info("Droping all data from " + connectionName);
//            raplaSQLOutput.dropAndRecreate( connection );
//		}
//		else
		{
	        getLogger().info("Deleting all old Data from " + connectionName);
		    raplaSQLOutput.removeAll( connection );
		}
		getLogger().info("Inserting new Data into " + connectionName);
		raplaSQLOutput.createAll( connection );
		if ( !connection.getAutoCommit())
		{
		    connection.commit();
		}
		// do something here
		getLogger().info("Import complete for " + connectionName);
	}

	private void close(Connection connection)
    {
		
    	if ( connection == null)
    	{
    		return;
    	}
        try 
        {
            if (!connection.isClosed())
        	{
                getLogger().debug("Closing "  + connection);
        	    connection.close();
        	}
        } 
        catch (SQLException e) 
        {
            getLogger().error( "Can't close connection to database ", e);
        }
    }

    protected void loadData(Connection connection, LocalCache cache) throws RaplaException, SQLException {
        EntityStore entityStore = new EntityStore(cache, cache.getSuperCategory());
        RaplaSQL raplaSQLInput =  new RaplaSQL(createInputContext(entityStore, this));
        raplaSQLInput.loadAll( connection );
        Collection<Entity> list = entityStore.getList();
		cache.putAll( list);
		resolveInitial( list, this );
        removeInconsistentEntities(cache, list);
        Collection<Entity> migratedTemplates = migrateTemplates();
        cache.putAll( migratedTemplates);
        List<PreferencePatch> preferencePatches = Collections.emptyList();
        Collection<String> removeObjects = Collections.emptyList();
        dbStore(migratedTemplates, preferencePatches, removeObjects);
        // It is important to do the read only later because some resolve might involve write to referenced objects
        for (Entity entity: list) {
             ((RefEntity)entity).setReadOnly();
        }
        for (Entity entity: migratedTemplates) {
            ((RefEntity)entity).setReadOnly();
       }
        cache.getSuperCategory().setReadOnly();
        for (User user:cache.getUsers())
        {
            String id = user.getId();
			String password = entityStore.getPassword( id);
            cache.putPassword(id, password);
        }
        processPermissionGroups();
	}
    
    @SuppressWarnings("deprecation")
    protected void loadOldData(Connection connection, LocalCache cache) throws RaplaException, SQLException {
        EntityStore entityStore = new EntityStore(cache, cache.getSuperCategory());
        IdCreator idCreator = new IdCreator() {
            
            @Override
            public String createId(RaplaType type, String seed) throws RaplaException {
                String id = org.rapla.storage.OldIdMapping.getId(type, seed);
                return id;
            }
            
            @Override
            public String createId(RaplaType raplaType) throws RaplaException {
                throw new RaplaException("Can't create new ids in " + getClass().getName() + " this class is import only for old data ");
            }
        };
        RaplaDefaultContext inputContext = createInputContext(entityStore, idCreator);
        
        org.rapla.storage.dbsql.pre18.RaplaPre18SQL raplaSQLInput =  new org.rapla.storage.dbsql.pre18.RaplaPre18SQL(inputContext);
        raplaSQLInput.loadAll( connection );
        Collection<Entity> list = entityStore.getList();
        cache.putAll( list);
        resolveInitial( list, cache);
        // It is important to do the read only later because some resolve might involve write to referenced objects
        for (Entity entity: list) {
             ((RefEntity)entity).setReadOnly();
        }
        cache.getSuperCategory().setReadOnly();
        for (User user:cache.getUsers())
        {
            String id = user.getId();
            String password = entityStore.getPassword( id);
            cache.putPassword(id, password);
        }
    }

	private RaplaDefaultContext createInputContext(  EntityStore store, IdCreator idCreator) throws RaplaException {
        RaplaDefaultContext inputContext =  new IOContext().createInputContext(context, store, idCreator);
        RaplaNonValidatedInput xmlAdapter = context.lookup(RaplaNonValidatedInput.class);
        inputContext.put(RaplaNonValidatedInput.class,xmlAdapter);
        return inputContext;
    }
    
    private RaplaDefaultContext createOutputContext(LocalCache cache) throws RaplaException {
        RaplaDefaultContext outputContext =  new IOContext().createOutputContext(context, cache.getSuperCategoryProvider(),true);
        outputContext.put( LocalCache.class, cache);
        return outputContext;
        
    }

//implement backup at disconnect 
    final public void backupData() throws RaplaException { 
        try {

            if (backupFileName.length()==0)
            	return;

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            writeData(buffer);
            byte[] data = buffer.toByteArray();
            buffer.close();
            OutputStream out = new FileOutputStream(backupFileName);
            out.write(data);
            out.close();
            getLogger().info("Backup data to: " + backupFileName);
        } catch (IOException e) {
            getLogger().error("Backup error: " + e.getMessage());
            throw new RaplaException(e.getMessage());
        }
    }
   

    private void writeData( OutputStream out ) throws IOException, RaplaException
    {
    	RaplaContext outputContext = new IOContext().createOutputContext( context,cache.getSuperCategoryProvider(), true );
        RaplaMainWriter writer = new RaplaMainWriter( outputContext, cache );
        writer.setEncoding(backupEncoding);
        BufferedWriter w = new BufferedWriter(new OutputStreamWriter(out,backupEncoding));
        writer.setWriter(w);
        writer.printContent();
    }
    
}
