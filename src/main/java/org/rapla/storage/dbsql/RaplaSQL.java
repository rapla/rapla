/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org .       |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, of which license fullfill the Open Source    |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/
package org.rapla.storage.dbsql;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.rapla.components.util.Assert;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.xml.RaplaNonValidatedInput;
import org.rapla.entities.Annotatable;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.Timestamp;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.internal.PreferencesImpl;
import org.rapla.entities.configuration.internal.RaplaMapImpl;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.EntityPermissionContainer;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.Permission.AccessLevel;
import org.rapla.entities.domain.PermissionContainer;
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.RepeatingType;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.internal.AllocatableImpl;
import org.rapla.entities.domain.internal.AppointmentImpl;
import org.rapla.entities.domain.internal.PermissionImpl;
import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.internal.AttributeImpl;
import org.rapla.entities.dynamictype.internal.ClassificationImpl;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.entities.internal.CategoryImpl;
import org.rapla.entities.internal.ModifiableTimestamp;
import org.rapla.entities.internal.UserImpl;
import org.rapla.facade.Conflict;
import org.rapla.facade.internal.ConflictImpl;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.rapla.jsonrpc.common.internal.JSONParserWrapper;
import org.rapla.storage.PreferencePatch;
import org.rapla.storage.impl.server.EntityHistory;
import org.rapla.storage.impl.server.EntityHistory.HistoryEntry;
import org.rapla.storage.server.ImportExportEntity;
import org.rapla.storage.server.ImportExportEntityImpl;
import org.rapla.storage.xml.CategoryReader;
import org.rapla.storage.xml.DynamicTypeReader;
import org.rapla.storage.xml.PreferenceReader;
import org.rapla.storage.xml.PreferenceWriter;
import org.rapla.storage.xml.RaplaXMLContext;
import org.rapla.storage.xml.RaplaXMLReader;
import org.rapla.storage.xml.RaplaXMLWriter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

class RaplaSQL {
    private final List<RaplaTypeStorage> stores = new ArrayList<RaplaTypeStorage>();
    private final Logger logger;
    private final HistoryStorage history;
    RaplaXMLContext context;
    PreferenceStorage preferencesStorage;
    LockStorage lockStorage;
    
    RaplaSQL( RaplaXMLContext context) throws RaplaException{
    	this.context = context;
        logger =  context.lookup( Logger.class);
        lockStorage = new LockStorage(logger);
        // The order is important. e.g. appointments can only be loaded if the reservation they are refering to are already loaded.
	    stores.add(new CategoryStorage( context));
	    stores.add(new UserStorage( context));
	    stores.add(new DynamicTypeStorage( context));
	    stores.add(new AllocatableStorage( context));
	    preferencesStorage = new PreferenceStorage( context);
        stores.add(preferencesStorage);
	    ReservationStorage reservationStorage = new ReservationStorage( context);
		stores.add(reservationStorage);
	    AppointmentStorage appointmentStorage = new AppointmentStorage( context);
		stores.add(appointmentStorage);
		stores.add(new ConflictStorage( context));
		//stores.add(new DeleteStorage( context));
        history = new HistoryStorage( context, false);
        stores.add(history);
        stores.add(new HistoryStorage( context, true));
		// now set delegate because reservation storage should also use appointment storage
		reservationStorage.setAppointmentStorage( appointmentStorage);
	}
    
    private List<Storage<?>> getStoresWithChildren()
    {
    	List<Storage<?>> storages = new ArrayList<Storage<?>>();
    	for ( RaplaTypeStorage store:stores)
    	{
    		storages.add( store);
    		@SuppressWarnings("unchecked")
			Collection<Storage<?>> subStores = store.getSubStores();
			storages.addAll(subStores);
    	}
		return storages;
	}

    protected Logger getLogger() {
    	return logger;
    }

/***************************************************
 *   Create everything                             *
 ***************************************************/
    synchronized public void createAll(Connection con)
        throws SQLException,RaplaException
    {
        Date connectionTimestamp = getDatabaseTimestamp(con);
        lockStorage.setConnection(con, connectionTimestamp);
		for (RaplaTypeStorage storage: stores) {
			storage.setConnection(con, connectionTimestamp);
			try
			{
				storage.insertAll();
			}
			finally
			{
				storage.removeConnection();
			}
		}
    }


    synchronized public void removeAll(Connection con)
    throws SQLException
    {
        Date connectionTimestamp = getDatabaseTimestamp(con);
		for (RaplaTypeStorage storage: stores) {
            storage.setConnection(con, connectionTimestamp);
            try
            {
            	storage.deleteAll();
            }
            finally
            {
            	storage.removeConnection();
            }
        }
    }

    synchronized public void loadAll(Connection con) throws SQLException,RaplaException {
        Date connectionTimestamp = getDatabaseTimestamp(con);
		for (Storage storage:stores)
		{
			storage.setConnection(con, connectionTimestamp);
			try
			{
				storage.loadAll();
			}
			finally
			{
				storage.removeConnection();
			}
		}
    }

    @SuppressWarnings("unchecked")
	synchronized public void remove(Connection con, Entity entity, Date connectionTimestamp) throws SQLException,RaplaException {
    	if ( Attribute.TYPE ==  entity.getRaplaType() )
			return;
    	boolean couldDelete = false;
		for (RaplaTypeStorage storage:stores) {
		    if (storage.canDelete(entity)) {
		    	storage.setConnection(con, connectionTimestamp);
		    	try
		    	{
			    	List<Entity>list = new ArrayList<Entity>();
			    	list.add( entity);
	                storage.deleteEntities(list);
	                couldDelete = true;
		    	}
		    	finally
		    	{
		    		storage.removeConnection();
		    	}
		    }
		}
		if(!couldDelete)
		{
		    throw new RaplaException("No Storage-Sublass matches this object: " + entity.getClass());
		}
    }

    @SuppressWarnings("unchecked")
	synchronized public void store(Connection con, Collection<Entity> entities, Date connectionTimestamp) throws SQLException,RaplaException {

        Map<Storage,List<Entity>> store = new LinkedHashMap<Storage, List<Entity>>();
        for ( Entity entity:entities)
        {
            if ( Attribute.TYPE ==  entity.getRaplaType() )
                continue;
            boolean found = false;
            for ( RaplaTypeStorage storage: stores)
            {
                if (storage.canStore(entity)) {
                    List<Entity>list = store.get( storage);
                    if ( list == null)
                    {
                        list = new ArrayList<Entity>();
                        store.put( storage, list);
                    }
                    list.add( entity);
                    found = true;
                }
            }
            if (!found)
            {
                throw new RaplaException("No Storage-Sublass matches this object: " + entity.getClass());
            }   
        }
        for ( Storage storage: store.keySet())
        {
            storage.setConnection(con,connectionTimestamp);
            try
            {
            	List<Entity>list = store.get( storage);
            	storage.save(list);
            }
            finally
            {
            	storage.removeConnection();
            }
        }
    }

	public void createOrUpdateIfNecessary(Connection con, Map<String, TableDef> schema) throws SQLException, RaplaException {

        // We dont't need a timestamp for createOrUpdate
        Date connectionTimestamp = null;
		   // Upgrade db if necessary
        final List<TableStorage> storesWithChildren = getTableStorages();
        for (TableStorage storage: storesWithChildren)
		{
    		storage.setConnection(con, connectionTimestamp);
    		try
    		{
    			storage.createOrUpdateIfNecessary( schema);
    		}
    		finally
    		{
    			storage.removeConnection();
    		}
		}
	}

    private List<TableStorage> getTableStorages()
    {
        final List<TableStorage> storesWithChildren = new ArrayList<>();
        storesWithChildren.addAll(getStoresWithChildren());
        storesWithChildren.add( lockStorage);
        return storesWithChildren;
    }

    public void storePatches(Connection connection, List<PreferencePatch> preferencePatches, Date connectionTimestamp) throws SQLException, RaplaException {
        PreferenceStorage storage = preferencesStorage;
        storage.setConnection( connection, connectionTimestamp);
        try
        {
            preferencesStorage.storePatches( preferencePatches);
        }
        finally
        {
            storage.removeConnection();
        }
    }

    public EntityHistory update(Connection c, Date lastUpdated,  Date connectionTimestamp) throws SQLException
    {
        history.setConnection( c, connectionTimestamp);
        try
        {
            history.update(lastUpdated);
        }
        finally
        {
            history.removeConnection();
        }
        EntityHistory entityHistory = history.history;
        return entityHistory;
    }

    public void cleanupOldLocks(Connection c) throws SQLException
    {
        try
        {
            lockStorage.setConnection(c, null);
            lockStorage.cleanupOldLocks();
        }
        finally
        {
            lockStorage.setConnection(null, null);
        }
    }
    
    public Date getLastUpdated(Connection c) throws SQLException
    {
        try
        {
            lockStorage.setConnection(c, null);
            return lockStorage.readLockTimestamp();
        }
        finally
        {
            lockStorage.setConnection(null, null);
        }
    }

    synchronized public Date getDatabaseTimestamp(Connection con) throws SQLException
    {
        try
        {
            lockStorage.setConnection(con, null);
            return lockStorage.getDatabaseTimestamp();
        }
        finally
        {
            lockStorage.setConnection(null, null);
        }
    }

    public void getLocks(Connection connection, Collection<String> ids) throws SQLException
    {
        try
        {
            lockStorage.setConnection(connection, null);
            lockStorage.getLocks(ids);
        }
        finally
        {
            lockStorage.setConnection(null, null);
        }
    }
    
    public Date getGlobalLock(Connection connection) throws SQLException
    {
        try
        {
            lockStorage.setConnection(connection, null);
            return lockStorage.getGlobalLock();
        }
        finally
        {
            lockStorage.setConnection(null, null);
        }
    }

    public void removeLocks(Connection connection, Collection<String> ids) throws SQLException
    {
        try
        {
            lockStorage.setConnection(connection, null);
            lockStorage.removeLocks(ids);
        }
        finally
        {
            lockStorage.setConnection(null, null);
        }
    }

    public void removeGlobalLock(Connection connection) throws SQLException
    {
        try
        {
            lockStorage.setConnection(connection, null);
            lockStorage.removeLocks(Collections.singleton(LockStorage.GLOBAL_LOCK));
        }
        finally 
        {
            lockStorage.setConnection(null, null);
        }
    }
}

class LockStorage extends AbstractTableStorage
{
    static final String GLOBAL_LOCK = "GLOBAL_LOCK";
    private final String countLocksSql = "SELECT COUNT(LOCKID) FROM WRITE_LOCK WHERE LOCKID <> '" + GLOBAL_LOCK + "'";
    private final String cleanupSql = "delete from WRITE_LOCK WHERE (LAST_CHANGED < ? AND LOCKID <> '" + GLOBAL_LOCK + "') OR (LAST_CHANGED < ? AND LOCKID = '"+GLOBAL_LOCK+"')";
    private String readTimestampInclusiveLockedSql;
    private String requestTimestampSql;
    public LockStorage(Logger logger)
    {
        super("WRITE_LOCK", logger, new String[] {"LOCKID VARCHAR(255) NOT NULL PRIMARY KEY","LAST_CHANGED TIMESTAMP"});
        insertSql = "insert into WRITE_LOCK (LOCKID, LAST_CHANGED) values (?, CURRENT_TIMESTAMP)";
        deleteSql = "delete from WRITE_LOCK WHERE LOCKID = ?";
        selectSql += " WHERE LOCKID = ?";
    }
    
    @Override
    public void setConnection(Connection con, Date connectionTimestamp) throws SQLException
    {
        super.setConnection(con, connectionTimestamp);
        if ( isHsqldb())
        {
            requestTimestampSql = "VALUES(CURRENT_TIMESTAMP)";
        }
        else
        {
            requestTimestampSql = "SELECT CURRENT_TIMESTAMP";
        }
        readTimestampInclusiveLockedSql = "SELECT LAST_CHANGED FROM WRITE_LOCK UNION "+ requestTimestampSql + " ORDER BY LAST_CHANGED ASC LIMIT 1";
    }

    public void removeLocks(Collection<String> ids) throws RaplaException
    {
        if (ids == null || ids.isEmpty())
        {
            return;
        }
        try(final PreparedStatement stmt = con.prepareStatement(deleteSql))
        {
            for(String id : ids)
            {
                stmt.setString(1, id);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
        catch(Exception e)
        {
            throw new RaplaException("Could not free locks");
        }
    }
    
    void cleanupOldLocks() throws RaplaException
    {
        final Date now = getDatabaseTimestamp();
        try(final PreparedStatement deleteStmt = con.prepareStatement(cleanupSql))
        {
            // max 60 sec wait
            deleteStmt.setTimestamp(1, new java.sql.Timestamp(now.getTime() - 60000l));
            // max 5 min wait
            deleteStmt.setTimestamp(2, new java.sql.Timestamp(now.getTime() - 300000l));
            deleteStmt.addBatch();
            final int[] executeBatch = deleteStmt.executeBatch();
            logger.debug("deleted logs: "+Arrays.toString(executeBatch));
        }
        catch(Exception e)
        {
            throw new RaplaException("could not delete old locks: " + e.getMessage(), e);
        }
    }

    Date getDatabaseTimestamp()
    {
        final Date now;
        {
            try(final PreparedStatement stmt = con.prepareStatement(requestTimestampSql))
            {
                final ResultSet result = stmt.executeQuery();
                result.next();
                now = result.getTimestamp(1);
            }
            catch(Exception e)
            {
                throw new RaplaException("Could not get current Timestamp from DB");
            }
        }
        return now;
    }
    
    private void checkGlobalLockThrowException() throws RaplaException
    {
        try(final PreparedStatement stmt = con.prepareStatement(selectSql))
        {
            stmt.setString(1, GLOBAL_LOCK);
            final ResultSet result = stmt.executeQuery();
            if(result.next())
            {
                throw new RaplaException("Global lock set");
            }
        }
        catch(SQLException e)
        {
            throw new RaplaException("Global lock set", e);
        }
    }

    public void getLocks(Collection<String> ids) throws RaplaException
    {
        if (ids == null || ids.isEmpty())
        {
            return;
        }
        checkGlobalLockThrowException();
        try(final PreparedStatement stmt = con.prepareStatement(insertSql))
        {
            for (String id : ids)
            {
                stmt.setString(1, id);
                stmt.addBatch();
            }
            stmt.executeBatch();
            if(con.getMetaData().supportsTransactions())
            {
                con.commit();
            }
        }
        catch(Exception e)
        {
            throw new RaplaException("Could not get Locks", e);
        }
        try
        {
            checkGlobalLockThrowException();
        }
        catch(RaplaException e)
        {
            removeLocks(ids);
            try
            {
                if(con.getMetaData().supportsTransactions())
                {
                    con.commit();
                }
            }
            catch(SQLException re)
            {
                logger.error("Could not commit remove of locks (" + ids + ") caused by: " + re.getMessage(), re);
            }
            throw e;
        }
    }

    public Date getGlobalLock() throws RaplaException
    {
        final Date lastLocked = readLockTimestamp();
        final Date lockTimestamp = requestLock(GLOBAL_LOCK, lastLocked);
        return lockTimestamp;
    }

    private Date requestLock(String lockId, Date lastLocked)
    {
        try(final PreparedStatement stmt = con.prepareStatement(insertSql))
        {
            stmt.setString(1, lockId);
            stmt.executeUpdate();
            final Date newLockTimestamp = readLockTimestamp();
            if(!newLockTimestamp.after(lastLocked))
            {
                Thread.sleep(1);
                // remove it so we can request a new
                removeLocks(Collections.singleton(GLOBAL_LOCK));
                return requestLock(lockId, lastLocked);
            }
            else
            {
                if(con.getMetaData().supportsTransactions())
                {// Commit so others see the global lock and do not get locks by their own
                    con.commit();
                }
                final long startWaitingTime = System.currentTimeMillis();
                try(PreparedStatement cstmt = con.prepareStatement(countLocksSql))
                {
                    final ResultSet result = cstmt.executeQuery();
                    result.next();
                    while(result.getInt(1) > 0)
                    {
                        // wait for max 30 seconds
                        final long actualTime = System.currentTimeMillis();
                        if((actualTime - startWaitingTime) > 30000l)
                        {
                            removeLocks(Collections.singleton(GLOBAL_LOCK));
                            if(con.getMetaData().supportsTransactions())
                            {// Commit so others do not see the global lock any more
                                con.commit();
                            }
                            throw new RaplaException("Global lock timed out");
                        }
                        // wait
                        Thread.sleep(100);
                    }
                }
                return newLockTimestamp;
            }
        }
        catch(Exception e)
        {
            throw new RaplaException("Error receiving lock for " + lockId);
        }
    }

    Date readLockTimestamp() throws RaplaException
    {
        try(PreparedStatement stmt = con.prepareStatement(readTimestampInclusiveLockedSql))
        {
            final ResultSet result = stmt.executeQuery();
            while(result.next())
            {
                Date now = result.getTimestamp(1);
                return now;
            }
        }
        catch(Exception e)
        {
        }
        throw new RaplaException("Could not read Timestamp from DB");
    }
}
abstract class RaplaTypeStorage<T extends Entity<T>> extends EntityStorage<T> {
	RaplaType raplaType;

	RaplaTypeStorage( RaplaXMLContext context, RaplaType raplaType, String tableName, String[] entries) throws RaplaException {
		super( context,tableName, entries );
		this.raplaType = raplaType;

	}
    boolean canStore(Entity entity) {
    	return entity.getRaplaType() == raplaType;
    }

    boolean canDelete(Entity entity) {
        return canStore(entity);
    }
    
    abstract void insertAll() throws SQLException,RaplaException;

    protected String getXML(RaplaXMLWriter writer,RaplaObject raplaObject) throws RaplaException {
		StringWriter stringWriter = new StringWriter();
	    BufferedWriter bufferedWriter = new BufferedWriter(stringWriter);
	    writer.setWriter(bufferedWriter);
	    writer.setSQL(true);
	    try {
	        writer.writeObject(raplaObject);
	        bufferedWriter.flush();
	    } catch (IOException ex) {
	        throw new RaplaException( ex);
	    }
	    return stringWriter.getBuffer().toString();
	}

    protected void processXML(RaplaXMLReader raplaXMLReader, String xml) throws RaplaException {
	    if ( xml== null ||  xml.trim().length() <= 10) {
	        throw new RaplaException("Can't load empty xml"  );
	    }
	    String xmlWithNamespaces = RaplaXMLReader.wrapRaplaDataTag(xml);
        RaplaNonValidatedInput parser = context.lookup(RaplaNonValidatedInput.class);
		parser.read(xmlWithNamespaces, raplaXMLReader, logger);
	    //return raplaXMLReader;
	}

//    public void update( Date lastUpdated, UpdateResult updateResult) throws SQLException
//    {
//        if (!hasLastChangedTimestamp)
//        {
//            return;
//        }
//        PreparedStatement stmt = null;
//        try
//        {
//            stmt = con.prepareStatement(loadAllUpdatesSql);
//            setTimestamp(stmt, 1, lastUpdated);
//            stmt.execute();
//            final ResultSet resultSet = stmt.getResultSet();
//            int count =0;
//            if (resultSet == null)
//            {
//                return;
//            }
//            while(resultSet.next())
//            {
//                count ++;
//                final String id = resultSet.getString(1);
//                if(id == null)
//                {
//                    continue;
//                }
//
//                // deletion of entities is handled in DeleteStorage
//                final Entity<?> oldEntity = entityStore.tryResolve(id);
//                if(oldEntity != null)
//                {
//                    // TODO think about do not load if the lastChanged timestamp has not changed
////                    int lastChangedColumn = resultSet.;
////                    getTimestamp(resultSet, lastChangedColumn);
//                }
//                load(resultSet);
//                updateSubstores(id);
//                final Entity<?> newEntity = entityStore.tryResolve(id);
//                if(oldEntity == null)
//                {// we have a new entity
//                    updateResult.addOperation(new UpdateResult.Add(newEntity.getId(), newEntity.getRaplaType()));
//                }
//                else
//                {// or a update
//                    final Date lastChangedOld = ((Timestamp)oldEntity).getLastChanged();
//                    final Date lastChangedNew = ((Timestamp)newEntity).getLastChanged();
//                    if(lastChangedOld.before(lastChangedNew))
//                    {
//                        updateResult.addOperation(new UpdateResult.Change(newEntity.getId(), newEntity.getRaplaType()));
//                    }
//                }
//            }
//            getLogger().debug("Updated " + count);
//        }
//        finally
//        {
//            if (stmt != null)
//            {
//                stmt.close();
//            }
//        }
//    }


    //    protected StringBuilder createQueryString(final Collection<String> ids, final String startQueryString)
//    {
//        final StringBuilder sb = new StringBuilder(startQueryString);
//        boolean first = true;
//        for (String localId : ids)
//        {
//            if(first)
//            {
//                first = false;
//            }
//            else
//            {
//                sb.append(",");
//            }
//            sb.append(localId);
//        }
//        sb.append(")");
//        return sb;
//    }
    protected Collection<Category> getTransitiveCategories(Category cat) {
        Set<Category> allChilds = new LinkedHashSet<Category>();
        allChilds.add( cat);
        for ( Category child: cat.getCategories())
        {
            allChilds.addAll(getTransitiveCategories(child));
        }
        return allChilds;
    }

}

class CategoryStorage extends RaplaTypeStorage<Category> {
	Map<Category,Integer> orderMap =  new HashMap<Category,Integer>();
    Map<Category,String> categoriesWithoutParent = new TreeMap<Category,String>(new Comparator<Category>()
        {
            public int compare( Category o1, Category o2 )
            {
                if ( o1.equals( o2))
                {
                    return 0;
                }
                int ordering1 = ( orderMap.get( o1 )).intValue();
                int ordering2 = (orderMap.get( o2 )).intValue();
                if ( ordering1 < ordering2)
                {
                    return -1;
                }
                if ( ordering1 > ordering2)
                {
                    return 1;
                }
                if (o1.hashCode() > o2.hashCode())
                {
                    return -1;
                }
                else
                {
                    return 1;
                }
            }
        }
    );



    public CategoryStorage(RaplaXMLContext context) throws RaplaException {
    	super(context,Category.TYPE, "CATEGORY",new String[] {"ID VARCHAR(255) NOT NULL PRIMARY KEY","PARENT_ID VARCHAR(255) KEY","CATEGORY_KEY VARCHAR(255) NOT NULL","DEFINITION TEXT NOT NULL","PARENT_ORDER INTEGER", "LAST_CHANGED TIMESTAMP KEY"});
    }

    @Override
    public void createOrUpdateIfNecessary(Map<String, TableDef> schema) throws SQLException, RaplaException
    {
        super.createOrUpdateIfNecessary( schema);
        checkAndAdd(schema, "LAST_CHANGED");
        checkAndDrop(schema, "DELETED");
    }

    @Override
    public void deleteEntities(Iterable<Category> entities) throws SQLException,RaplaException {
        Set<Category> transitiveCategories = new HashSet<Category>();
    	for ( Category cat:entities)
    	{
    		transitiveCategories.addAll(getTransitiveCategories(cat));
    	}
        super.deleteEntities( transitiveCategories );
    }
    
    @Override
    public void insert(Iterable<Category> entities) throws SQLException, RaplaException {
        Set<Category> transitiveCategories = new LinkedHashSet<Category>();
        for ( Category cat: entities)
        {
            transitiveCategories.addAll(getTransitiveCategories(cat));
        }
        super.insert(transitiveCategories);
    }
    
    // get
    private Collection<String> getTransitiveIds(String parentId) throws SQLException, RaplaException {
		Set<String> childIds = new HashSet<String>();
		String sql = "SELECT ID FROM CATEGORY WHERE PARENT_ID=?";
		PreparedStatement stmt = null;
        ResultSet rset = null;
        try {
            stmt = con.prepareStatement(sql);
            setString(stmt,1, parentId);
            rset = stmt.executeQuery();
            while (rset.next ()) {
            	String id = readId(rset, 1, Category.class);
            	childIds.add( id);
            }
        } finally {
            if (rset != null)
                rset.close();
            if (stmt!=null)
                stmt.close();
        }
        Set<String> result = new HashSet<String>();
        for (String childId : childIds)
        {
        	result.addAll( getTransitiveIds(childId));
        }
        result.add( parentId);
		return result;
    }

    @Override
	protected int write(PreparedStatement stmt,Category category) throws SQLException, RaplaException {
    	Category root = getSuperCategory();
        if ( category.equals( root ))
            return 0;
        setId( stmt,1, category);
		setId( stmt,2, category.getParent());
        int order = getOrder( category);
        RaplaXMLWriter categoryWriter = context.lookup( PreferenceWriter.WRITERMAP ).get(Category.TYPE);
        String xml = getXML( categoryWriter,category );
        setString(stmt, 3, category.getKey());
        setText(stmt, 4, xml);
        setInt(stmt, 5, order);
        setTimestamp(stmt, 6, category.getLastChanged());
		stmt.addBatch();
		return 1;
    }

    private int getOrder( Category category )
    {
        Category parent = category.getParent();
        if ( parent == null)
        {
            return 0;
        }
        Category[] childs = parent.getCategories();
        for ( int i=0;i<childs.length;i++)
        {
            if ( childs[i].equals( category))
            {
                return i;
            }
        }
        getLogger().error("Category not found in parent");
        return 0;
    }

    @Override
    protected void load(ResultSet rset) throws SQLException, RaplaException {
    	String id = readId(rset, 1, Category.class);
    	String parentId = readId(rset, 2, Category.class, true);

        String xml = getText( rset, 4 );
    	Integer order = getInt(rset, 5 );
        CategoryImpl category;
    	if ( xml != null && xml.length() > 10 )
    	{
            CategoryReader categoryReader = (CategoryReader )context.lookup(PreferenceReader.READERMAP).get(Category.TYPE);
            categoryReader.setReadOnlyThisCategory( true);
            processXML( categoryReader, xml );
    	    category = categoryReader.getCurrentCategory();
            //cache.remove( category );
    	}
    	else
    	{
			getLogger().warn("Category has empty xml field. Ignoring.");
			return;
    	}
        final Date lastChanged = getTimestampOrNow(rset, 6);
        category.setLastChanged( lastChanged);
		category.setId( id);
        put( category );

        orderMap.put( category, order);
        // parentId can also be null
        categoriesWithoutParent.put( category, parentId);
    }

    @Override
    public void loadAll() throws RaplaException, SQLException {
    	categoriesWithoutParent.clear();
    	super.loadAll();
    	// then we rebuild the hierarchy
    	Iterator<Map.Entry<Category,String>> it = categoriesWithoutParent.entrySet().iterator();
    	while (it.hasNext()) {
    		Map.Entry<Category,String> entry = it.next();
    		String parentId = entry.getValue();
    		Category category =  entry.getKey();
    		Category parent;
            Assert.notNull( category );
    		if ( parentId != null) {
    		    parent = entityStore.resolve( parentId ,Category.class);
            } else {
    		    parent = getSuperCategory();
            }
            Assert.notNull( parent );
            parent.addCategory( category );
    	}
    }

	@Override
	void insertAll() throws SQLException, RaplaException {
		CategoryImpl superCategory = cache.getSuperCategory();
		Set<Category> childs = new HashSet<Category>();
		addChildren(childs, superCategory);
		insert( childs);
	}
	
	private void addChildren(Collection<Category> list, Category category) {
		for (Category child:category.getCategories())
		{
			list.add( child );
			addChildren(list, child);
		}
	}
}

class AllocatableStorage extends RaplaTypeStorage<Allocatable>  {
    Map<String,Classification> classificationMap = new HashMap<String,Classification>();
    Map<String,Allocatable> allocatableMap = new HashMap<String,Allocatable>();
    AttributeValueStorage<Allocatable> resourceAttributeStorage;
    PermissionStorage<Allocatable> permissionStorage;

    public AllocatableStorage(RaplaXMLContext context ) throws RaplaException {
        super(context,Allocatable.TYPE,"RAPLA_RESOURCE",new String [] {"ID VARCHAR(255) NOT NULL PRIMARY KEY","TYPE_KEY VARCHAR(255) NOT NULL","OWNER_ID VARCHAR(255)","CREATION_TIME TIMESTAMP","LAST_CHANGED TIMESTAMP KEY","LAST_CHANGED_BY VARCHAR(255) DEFAULT NULL"});  
        resourceAttributeStorage = new AttributeValueStorage<Allocatable>(context,"RESOURCE_ATTRIBUTE_VALUE", "RESOURCE_ID",classificationMap, allocatableMap);
        permissionStorage = new PermissionStorage<Allocatable>( context, "RESOURCE",allocatableMap);
        addSubStorage(resourceAttributeStorage);
        addSubStorage(permissionStorage);
    }

    @Override
    public void createOrUpdateIfNecessary(Map<String, TableDef> schema) throws SQLException, RaplaException
    {
        super.createOrUpdateIfNecessary(schema);
        checkAndDrop(schema, "DELETED");
    }
    
	@Override
	void insertAll() throws SQLException, RaplaException {
		insert( cache.getAllocatables());
	}

    @Override
	protected int write(PreparedStatement stmt,Allocatable entity) throws SQLException,RaplaException {
	  	AllocatableImpl allocatable = (AllocatableImpl) entity;
	  	String typeKey = allocatable.getClassification().getType().getKey();
		setId(stmt, 1, entity);
	  	setString(stmt,2, typeKey );
		org.rapla.entities.Timestamp timestamp = allocatable;
		setId(stmt,3, allocatable.getOwner() );
        setTimestamp(stmt, 4,timestamp.getCreateTime() );
		setTimestamp(stmt, 5,timestamp.getLastChanged() );
		setId( stmt,6,timestamp.getLastChangedBy() );
		stmt.addBatch();
      	return 1;
    }
    @Override
    protected void load(ResultSet rset) throws SQLException, RaplaException {
        String id= readId(rset,1, Allocatable.class);
    	String typeKey = getString(rset,2 , null);
		final Date createDate = getTimestampOrNow( rset, 4);
		final Date lastChanged = getTimestampOrNow( rset, 5);
     	
    	AllocatableImpl allocatable = new AllocatableImpl(createDate, lastChanged);
    	allocatable.setLastChangedBy( resolveFromId(rset, 6, User.class) );
    	allocatable.setId( id);
    	allocatable.setResolver( entityStore);
    	DynamicType type = null;
    	if ( typeKey != null)
    	{
    		type = getDynamicType(typeKey );
    	}
    	if ( type == null)
        {
            getLogger().error("Allocatable with id " + id + " has an unknown type " + typeKey + ". Try ignoring it");
            return;
        }
		{
		    final User user = resolveFromId(rset, 3, User.class);
		    allocatable.setOwner( user );
		}
    	Classification classification = ((DynamicTypeImpl)type).newClassificationWithoutCheck(false);
    	allocatable.setClassification( classification );
    	classificationMap.put( id, classification );
    	allocatableMap.put( id, allocatable);
    	put( allocatable );
    }
    
    @Override
	public void loadAll() throws RaplaException, SQLException {
    	classificationMap.clear();
    	super.loadAll();
    }
}

class ReservationStorage extends RaplaTypeStorage<Reservation> {
    Map<String,Classification> classificationMap = new HashMap<String,Classification>();
    Map<String,Reservation> reservationMap = new HashMap<String,Reservation>();
    AttributeValueStorage<Reservation> attributeValueStorage;
    // appointmentstorage is not a sub store but a delegate
	AppointmentStorage appointmentStorage;
	PermissionStorage<Reservation> permissionStorage;

    public ReservationStorage(RaplaXMLContext context) throws RaplaException {
        super(context,Reservation.TYPE, "EVENT",new String [] {"ID VARCHAR(255) NOT NULL PRIMARY KEY","TYPE_KEY VARCHAR(255) NOT NULL","OWNER_ID VARCHAR(255) NOT NULL","CREATION_TIME TIMESTAMP","LAST_CHANGED TIMESTAMP KEY","LAST_CHANGED_BY VARCHAR(255) DEFAULT NULL"});
        attributeValueStorage = new AttributeValueStorage<Reservation>(context,"EVENT_ATTRIBUTE_VALUE","EVENT_ID", classificationMap, reservationMap);
        addSubStorage(attributeValueStorage);
        permissionStorage = new PermissionStorage<Reservation>( context,"EVENT", reservationMap);
        addSubStorage(permissionStorage);
    }

    @Override
    public void createOrUpdateIfNecessary(Map<String, TableDef> schema) throws SQLException, RaplaException
    {
        super.createOrUpdateIfNecessary(schema);
        checkAndDrop(schema, "DELETED");
    }
    
    public void setAppointmentStorage(AppointmentStorage appointmentStorage)
    {
    	this.appointmentStorage = appointmentStorage;
    }
    
   	@Override
	void insertAll() throws SQLException, RaplaException {
		insert( cache.getReservations());
	}

	@Override
	public void save(Iterable<Reservation> entities) throws RaplaException,	SQLException {
		super.save(entities);
		Collection<Appointment> appointments = new ArrayList<Appointment>();
		for (Reservation r: entities)
		{
			appointments.addAll( Arrays.asList(r.getAppointments()));
		}
		appointmentStorage.insert( appointments );
	}
	
	@Override
	public void setConnection(Connection con, Date connectionTimestamp) throws SQLException {
		super.setConnection(con, connectionTimestamp);
		appointmentStorage.setConnection(con, connectionTimestamp);
	}

    @Override
    protected int write(PreparedStatement stmt,Reservation event) throws SQLException,RaplaException {
      	String typeKey = event.getClassification().getType().getKey();
      	setId(stmt,1, event );
      	setString(stmt,2, typeKey );
    	setId(stmt,3, event.getOwner() );
    	org.rapla.entities.Timestamp timestamp = event;
        Date createTime = timestamp.getCreateTime();
        setTimestamp( stmt,4,createTime);
        setTimestamp( stmt,5,timestamp.getLastChanged());
        setId(stmt, 6, timestamp.getLastChangedBy());
        stmt.addBatch();
        return 1;
    }

    @Override
    protected void updateSubstores(String foreignId) throws SQLException
    {
        super.updateSubstores(foreignId);
        appointmentStorage.updateWithForeignId(foreignId);
    }

    @Override
	protected void load(ResultSet rset) throws SQLException, RaplaException {
    	final Date createDate = getTimestampOrNow(rset,4);
        final Date lastChanged = getTimestampOrNow(rset, 5);
        ReservationImpl event = new ReservationImpl(createDate, lastChanged);
    	String id = readId(rset,1,Reservation.class);
		event.setId( id);
        event.setResolver(entityStore);
        String typeKey = getString(rset, 2, null);
        DynamicType type = null;
        if (typeKey != null)
        {
            type = getDynamicType(typeKey);
        }
        if (type == null)
        {
            getLogger().error("Reservation with id " + id + " has an unknown type " + typeKey + ". Try ignoring it");
            return;
        }
        {
            User user = resolveFromId(rset, 3, User.class);
            if (user == null)
            {
                return;
            }
            event.setOwner(user);
        }
        {
            User user = resolveFromId(rset, 6, User.class);
            event.setLastChangedBy( user );
        }

        Classification classification = ((DynamicTypeImpl)type).newClassificationWithoutCheck(false);
        event.setClassification( classification );
        classificationMap.put( id, classification );
        reservationMap.put(id, event);
        put(event);
    }

    @Override
    public void loadAll() throws RaplaException, SQLException {
    	classificationMap.clear();
    	super.loadAll();
    }

    @Override protected void deleteFromSubStores(Set<String> ids) throws SQLException
    {
        super.deleteFromSubStores(ids);
        appointmentStorage.deleteAppointments(ids);
    }

}

class AttributeValueStorage<T extends Entity<T>> extends EntityStorage<T> implements  SubStorage<T> {
    Map<String,Classification> classificationMap;
    Map<String,? extends Annotatable> annotableMap;
    final String foreignKeyName;
    // TODO Write conversion script to update all old entries to new entries
    public final static String OLD_ANNOTATION_PREFIX = "annotation:";
  	public final static String ANNOTATION_PREFIX = "rapla:";

    public AttributeValueStorage(RaplaXMLContext context,String tablename, String foreignKeyName, Map<String,Classification> classificationMap, Map<String, ? extends Annotatable> annotableMap) throws RaplaException {
    	super(context, tablename, new String[]{foreignKeyName + " VARCHAR(255) NOT NULL KEY","ATTRIBUTE_KEY VARCHAR(255)","ATTRIBUTE_VALUE VARCHAR(20000)"});
        this.foreignKeyName = foreignKeyName;
        this.classificationMap = classificationMap;
        this.annotableMap = annotableMap;
    }
    
    @Override
	protected int write(PreparedStatement stmt,T classifiable) throws EntityNotFoundException, SQLException {
        Classification classification =  ((Classifiable)classifiable).getClassification();
        Attribute[] attributes = classification.getAttributes();
        int count =0;
        for (int i=0;i<attributes.length;i++) {
            Attribute attribute = attributes[i];
            Collection<Object> values = classification.getValues( attribute );
            for (Object value: values)
            {
            	String valueAsString;
            	if ( value instanceof Category || value instanceof Allocatable)
            	{
                    Entity casted = (Entity) value;
                    valueAsString = casted.getId();
            	}
            	else
            	{
            		valueAsString = AttributeImpl.attributeValueToString( attribute, value, true);
            	}
		        setId(stmt,1, classifiable);
		        setString(stmt,2, attribute.getKey());
		        setString(stmt,3, valueAsString);
		        stmt.addBatch();
	            count++;
            }
        }
    	Annotatable annotatable = (Annotatable)classifiable;
    	for ( String key: annotatable.getAnnotationKeys())
    	{
    		String valueAsString = annotatable.getAnnotation( key);
    		setId(stmt,1, classifiable);
	        setString(stmt,2, ANNOTATION_PREFIX + key);
	     	setString(stmt,3, valueAsString);
	     	stmt.addBatch();
            count++;
    	}
        return count;
    }

    @Override
    protected void load(ResultSet rset) throws SQLException, RaplaException {
        Class<? extends Entity> idClass = foreignKeyName.indexOf("RESOURCE")>=0 ? Allocatable.class : Reservation.class;
		String classifiableId = readId(rset, 1, idClass);
        String attributekey = rset.getString( 2 );
        boolean annotationPrefix = attributekey.startsWith(ANNOTATION_PREFIX);
		boolean oldAnnotationPrefix = attributekey.startsWith(OLD_ANNOTATION_PREFIX);
		if ( annotationPrefix || oldAnnotationPrefix)
        {
        	String annotationKey = attributekey.substring( annotationPrefix ? ANNOTATION_PREFIX.length() : OLD_ANNOTATION_PREFIX.length());
        	Annotatable annotatable = annotableMap.get(classifiableId);
        	if (annotatable != null)
        	{
	        	String valueAsString = rset.getString( 3);
	    	    if ( rset.wasNull() || valueAsString == null)
	    	    {
	    	    	annotatable.setAnnotation(annotationKey, null);
	    	    }
	    	    else
	    	    {
	    	    	annotatable.setAnnotation(annotationKey, valueAsString);
	    	    }
        	}
        	else
        	{
                getLogger().warn("No resource or reservation found for the id " + classifiableId  + " ignoring.");
        	}
        }
        else
        {
            ClassificationImpl classification = (ClassificationImpl) classificationMap.get(classifiableId);
            if ( classification == null) {
                getLogger().warn("No resource or reservation found for the id " + classifiableId  + " ignoring.");
                return;
            }
	    	Attribute attribute = classification.getType().getAttribute( attributekey );
	    	if ( attribute == null) {
	    		getLogger().error("DynamicType '" +classification.getType() +"' doesnt have an attribute with the key " + attributekey + " Current allocatable/reservation Id " + classifiableId + ". Ignoring attribute.");
	    		return;
	    	}
	    	String valueAsString = rset.getString( 3);
    	    if ( valueAsString != null )
            {
    	        try
    	        {
    	            Object value = AttributeImpl.parseAttributeValue(attribute, valueAsString);
    	            if ( value != null)
    	            {
    	                classification.addValue( attribute, value);
    	            }
    	        }
    	        catch(RaplaException e)
    	        {
                    getLogger().error("DynamicType '" +classification.getType() +"' doesnt have a valid attribute with the key " + attributekey + " Current allocatable/reservation Id " + classifiableId + ". Ignoring attribute.");
    	        }
            }
        }
    }
}

 class PermissionStorage<T extends EntityPermissionContainer<T>> extends EntityStorage<T>  implements  SubStorage<T> {
    Map<String,T> referenceMap;
    public PermissionStorage(RaplaXMLContext context,String type,Map<String,T> idMap) throws RaplaException {
        super(context,type+"_PERMISSION",new String[] {type + "_ID VARCHAR(255) NOT NULL KEY","USER_ID VARCHAR(255)","GROUP_ID VARCHAR(255)","ACCESS_LEVEL INTEGER NOT NULL","MIN_ADVANCE INTEGER","MAX_ADVANCE INTEGER","START_DATE DATETIME","END_DATE DATETIME"});
        this.referenceMap = idMap;
    }

    protected int write(PreparedStatement stmt, EntityPermissionContainer container) throws SQLException, RaplaException {
        int count = 0;
        Iterable<Permission> permissionList = container.getPermissionList();
        for (Permission s:permissionList) {
			setId(stmt,1,container);
			setId(stmt,2,s.getUser());
			setId(stmt,3,s.getGroup());
			@SuppressWarnings("deprecation")
            int numericLevel = s.getAccessLevel().getNumericLevel();
            setInt(stmt,4, numericLevel);
			setInt( stmt,5, s.getMinAdvance());
			setInt(stmt, 6, s.getMaxAdvance());
			setDate(stmt, 7, s.getStart());
			setDate(stmt, 8, s.getEnd());
			stmt.addBatch();
			count ++;
        }
        return count;
    }

    protected void load(ResultSet rset) throws SQLException, RaplaException {
        Class<? extends Entity> clazz = null;
        String referenceIdInt = readId(rset, 1, clazz);
        PermissionContainer allocatable = referenceMap.get(referenceIdInt);
        if ( allocatable == null)
        {
        	getLogger().warn("Could not find resource object with id "+ referenceIdInt + " for permission. Maybe the resource was deleted from the database.");
        	return;
        }
        PermissionImpl permission = new PermissionImpl();
        permission.setUser( resolveFromId(rset, 2, User.class));
        permission.setGroup( resolveFromId(rset, 3, Category.class));
        Integer accessLevel = getInt( rset, 4);
        if  ( accessLevel !=null)
        {
	        AccessLevel enumLevel = AccessLevel.find(accessLevel);
	        permission.setAccessLevel( enumLevel );
        }
        permission.setMinAdvance( getInt(rset,5));
        permission.setMaxAdvance( getInt(rset,6));
        permission.setStart(getDate(rset, 7));
        permission.setEnd(getDate(rset, 8));
        // We need to add the permission at the end to ensure its unique. Permissions are stored in a set and duplicates are removed during the add method 
        allocatable.addPermission( permission );
    }

}

// TODO is it possible to add this as substorage
class AppointmentStorage extends RaplaTypeStorage<Appointment> {
    AppointmentExceptionStorage appointmentExceptionStorage;
    AllocationStorage allocationStorage;
    private String foreignId;

    public AppointmentStorage(RaplaXMLContext context) throws RaplaException
    {
        super(context, Appointment.TYPE, "APPOINTMENT",
                new String[] { "ID VARCHAR(255) NOT NULL PRIMARY KEY", "EVENT_ID VARCHAR(255) NOT NULL KEY", "APPOINTMENT_START DATETIME NOT NULL",
                        "APPOINTMENT_END DATETIME NOT NULL", "REPETITION_TYPE VARCHAR(255)", "REPETITION_NUMBER INTEGER", "REPETITION_END DATETIME",
                        "REPETITION_INTERVAL INTEGER" });
        setForeignId("EVENT_ID");
        appointmentExceptionStorage = new AppointmentExceptionStorage(context);
        allocationStorage = new AllocationStorage(context);
        addSubStorage(appointmentExceptionStorage);
        addSubStorage(allocationStorage);
    }

    void deleteAppointments(Collection<String> reservationIds)
            throws SQLException, RaplaException {
        deleteIds(reservationIds);
//        Set<String> ids = new HashSet<String>();
//        String sql = "SELECT ID FROM APPOINTMENT WHERE EVENT_ID=?";
//        for (String eventId:reservationIds)
//        {
//            PreparedStatement stmt = null;
//            ResultSet rset = null;
//            try {
//                stmt = con.prepareStatement(sql);
//                setString(stmt,1,  eventId);
//                rset = stmt.executeQuery();
//                while (rset.next ()) {
//                    String appointmentId = readId(rset, 1, Appointment.class);
//                    ids.add( appointmentId);
//                }
//            } finally {
//                if (rset != null)
//                    rset.close();
//                if (stmt!=null)
//                    stmt.close();
//            }
//        }
//        // and delete them
//        deleteIds(ids);
    }

    @Override
	void insertAll() throws SQLException, RaplaException {
		Collection<Reservation> reservations = cache.getReservations();
		Collection<Appointment> appointments = new LinkedHashSet<Appointment>();
		for (Reservation r: reservations)
		{
			appointments.addAll( Arrays.asList(r.getAppointments()));
		}
		insert( appointments);
	}

    @Override
    protected int write(PreparedStatement stmt,Appointment appointment) throws SQLException,RaplaException {
      	setId( stmt, 1, appointment);
      	setId( stmt, 2, appointment.getReservation());
      	setDate(stmt, 3, appointment.getStart());
      	setDate(stmt, 4, appointment.getEnd());
      	Repeating repeating = appointment.getRepeating();
      	if ( repeating == null) {
      		setString( stmt,5, null);
			setInt( stmt,6, null);
			setDate( stmt,7, null);
			setInt( stmt,8, null);
      	} else {
      		setString( stmt,5, repeating.getType().toString());
      	    int number = repeating.getNumber();
      	    setInt(stmt, 6, number >= 0 ? number : null);
      	    setDate(stmt, 7, repeating.getEnd());
      	    setInt(stmt,8, repeating.getInterval());
      	}
      	stmt.addBatch();
      	return 1;
    }

    @Override
	protected void load(ResultSet rset) throws SQLException, RaplaException {
        String id = readId(rset, 1, Appointment.class);
        Reservation reservation = resolveFromId(rset, 2, Reservation.class);
        if ( reservation == null)
        {
        	return;
        }
        Date start = getDate(rset,3);
        Date end = getDate(rset,4);
        boolean wholeDayAppointment = start.getTime() == DateTools.cutDate( start.getTime()) && end.getTime() == DateTools.cutDate( end.getTime());
    	AppointmentImpl appointment = new AppointmentImpl(start, end);
    	appointment.setId(id);
    	appointment.setWholeDays(wholeDayAppointment);
    	reservation.addAppointment(appointment);
    	String repeatingType = getString( rset,5, null);
    	if ( repeatingType != null ) {
    	    appointment.setRepeatingEnabled( true );
    	    Repeating repeating = appointment.getRepeating();
    	    repeating.setType( RepeatingType.findForString( repeatingType ) );
    	    Date repeatingEnd = getDate(rset, 7);
	        if ( repeatingEnd != null ) {
	            repeating.setEnd( repeatingEnd);
	        } else {
	        	Integer number  = getInt( rset, 6);
	        	if ( number != null) {
	        		repeating.setNumber( number);
	        	} else {
	                repeating.setEnd( null );
	        	}
	        }

	        Integer interval = getInt( rset,8);
    	    if ( interval != null)
    	        repeating.setInterval( interval);
    	}
        put(appointment);
    }


}


class AllocationStorage extends EntityStorage<Appointment> implements SubStorage<Appointment> {

    public AllocationStorage(RaplaXMLContext context) throws RaplaException
    {
        super(context, "ALLOCATION", new String[] { "APPOINTMENT_ID VARCHAR(255) NOT NULL KEY", "RESOURCE_ID VARCHAR(255) NOT NULL", "PARENT_ORDER INTEGER" });
    }
    
    @Override
    protected int write(PreparedStatement stmt, Appointment appointment) throws SQLException, RaplaException {
        Reservation event = appointment.getReservation();
        int count = 0;
        for (Allocatable allocatable: event.getAllocatablesFor(appointment)) {
    		setId(stmt,1, appointment);
    		setId(stmt,2, allocatable);
            stmt.setObject(3, null);
    		stmt.addBatch();
    		count++;
        }
        return count;
    }

    @Override
    protected void load(ResultSet rset) throws SQLException, RaplaException {
    	Appointment appointment =resolveFromId(rset, 1, Appointment.class);
    	if ( appointment == null)
    	{
    		return;
    	}
        ReservationImpl event = (ReservationImpl) appointment.getReservation();
        Allocatable allocatable = resolveFromId(rset, 2, Allocatable.class);
        if ( allocatable == null)
        {
        	return;
        }
        if ( !event.hasAllocated( allocatable ) ) {
            event.addAllocatable( allocatable );
        }
        Appointment[] appointments = event.getRestriction( allocatable );
        Appointment[] newAppointments = new Appointment[ appointments.length+ 1];
        System.arraycopy(appointments,0, newAppointments, 0, appointments.length );
        newAppointments[ appointments.length] = appointment;
        if (event.getAppointmentList().size() > newAppointments.length ) {
            event.setRestriction( allocatable, newAppointments );
        } else {
            event.setRestriction( allocatable, new Appointment[] {} );
        }
    }

 }

class AppointmentExceptionStorage extends EntityStorage<Appointment> implements SubStorage<Appointment> {
    public AppointmentExceptionStorage(RaplaXMLContext context) throws RaplaException {
        super(context,"APPOINTMENT_EXCEPTION",new String [] {"APPOINTMENT_ID VARCHAR(255) NOT NULL KEY","EXCEPTION_DATE DATETIME NOT NULL"});
    }

    @Override
    protected int write(PreparedStatement stmt, Appointment entity) throws SQLException, RaplaException {
        Repeating repeating = entity.getRepeating();
        int count = 0;
        if ( repeating == null) {
            return count;
        }
	    for (Date exception: repeating.getExceptions()) {
	        setId( stmt, 1, entity );
	        setDate(stmt, 2, exception);
	        stmt.addBatch();
	        count++;
        }
        return count;
	}

    @Override
    protected void load(ResultSet rset) throws SQLException, RaplaException {
        Appointment appointment = resolveFromId( rset, 1, Appointment.class);
        if ( appointment == null)
        {
        	return;
        }
        Repeating repeating = appointment.getRepeating();
        if ( repeating != null) {
            Date date = getDate(rset,2 );
            repeating.addException(date);
        }
    }

}

class DynamicTypeStorage extends RaplaTypeStorage<DynamicType> {

    public DynamicTypeStorage(RaplaXMLContext context) throws RaplaException {
        super(context, DynamicType.TYPE,"DYNAMIC_TYPE", new String [] {"ID VARCHAR(255) NOT NULL PRIMARY KEY","TYPE_KEY VARCHAR(255) NOT NULL","DEFINITION TEXT NOT NULL","LAST_CHANGED TIMESTAMP KEY", "DELETED TIMESTAMP KEY"});//, "CREATION_TIME TIMESTAMP","LAST_CHANGED TIMESTAMP","LAST_CHANGED_BY INTEGER DEFAULT NULL"});

    }

    @Override
    public void createOrUpdateIfNecessary(Map<String, TableDef> schema) throws SQLException, RaplaException
    {
        super.createOrUpdateIfNecessary( schema);
        checkAndAdd(schema, "LAST_CHANGED");
        checkAndAdd(schema, "DELETED");
    }

    @Override
	protected int write(PreparedStatement stmt, DynamicType type) throws SQLException, RaplaException {
		if (((DynamicTypeImpl) type).isInternal())
		{
			return 0;
		}
        setId(stmt,1,type);
        setString(stmt,2, type.getKey());
        RaplaXMLWriter typeWriter = context.lookup( PreferenceWriter.WRITERMAP ).get(DynamicType.TYPE);
        setText(stmt,3,  getXML( typeWriter,type) );
        setDate(stmt, 4,type.getLastChanged() );
        setTimestamp(stmt, 5, null);
//    	setDate(stmt, 5,timestamp.getLastChanged() );
//    	setId( stmt,6,timestamp.getLastChangedBy() );
        stmt.addBatch();
        return 1;
    }
	
	@Override
	void insertAll() throws SQLException, RaplaException {
		Collection<DynamicType> dynamicTypes = new ArrayList<DynamicType>(cache.getDynamicTypes());
		Iterator<DynamicType> it = dynamicTypes.iterator();
		while ( it.hasNext())
		{
		    if (((DynamicTypeImpl)it.next()).isInternal())
		    {
		        it.remove();
		    }
		}
        insert( dynamicTypes);
	}

	protected void load(ResultSet rset) throws SQLException,RaplaException {
    	@SuppressWarnings("unused")
        String id = readId(rset, 1, DynamicType.class);
	    String xml = getText(rset,3);
        DynamicTypeReader typeReader = (DynamicTypeReader)context.lookup(PreferenceReader.READERMAP).get(DynamicType.TYPE);
        processXML(typeReader, xml);
//    	final Date createDate = getDate( rset, 4);
    	final Date lastChanged = getTimestampOrNow(rset, 4);
        final Entity entity = typeReader.getStore().resolve(id);
        ((ModifiableTimestamp) entity).setLastChanged(lastChanged);
//    	AllocatableImpl allocatable = new AllocatableImpl(createDate, lastChanged);
//    	allocatable.setLastChangedBy( resolveFromId(rset, 6, User.class) );
    	
  //  	DynamicType type = reader.getStore().resolve(id, DynamicType.class);
//    	idMap.put( id, type);
	}

}


class PreferenceStorage extends RaplaTypeStorage<Preferences> 
{
    public PreferenceStorage(RaplaXMLContext context) throws RaplaException {
        super(context,Preferences.TYPE,"PREFERENCE",
	    new String [] {"USER_ID VARCHAR(255) KEY","ROLE VARCHAR(255) NOT NULL","STRING_VALUE VARCHAR(10000)","XML_VALUE TEXT","LAST_CHANGED TIMESTAMP KEY"});
    }

    @Override
    public void createOrUpdateIfNecessary(Map<String, TableDef> schema) throws SQLException, RaplaException
    {
        super.createOrUpdateIfNecessary( schema);
        checkAndAdd(schema, "LAST_CHANGED");
        checkAndDrop(schema, "DELETED");
    }
    
	public void storePatches(List<PreferencePatch> preferencePatches) throws RaplaException, SQLException 
	{
	    for ( PreferencePatch patch:preferencePatches)
	    {
	        String userId = patch.getUserId();
            PreparedStatement stmt = null;
	        try {
	            
	            final String deleteSqlWithRole;
                int count = 0;
	            if ( userId != null)
	            {
	                deleteSqlWithRole = deleteSql + " and role=?";
	                stmt = con.prepareStatement(deleteSqlWithRole);
	                for ( String role: patch.getRemovedEntries())
	                {
	                    setId(stmt, 1, userId);
                        setTimestamp(stmt, 2, patch.getLastChanged());
                        setString(stmt, 3, role);
                        stmt.addBatch();
	                    count++;
	                }
	                for ( String role: patch.keySet())
	                {
	                    setId(stmt, 1, userId);
                        setTimestamp(stmt, 2, patch.getLastChanged());
                        setString(stmt, 3, role);
                        stmt.addBatch();
	                    count++;
	                }
	            }
	            else
	            {
	                deleteSqlWithRole = "delete from " + getTableName() + " where user_id IS null and LAST_CHANGED = ? and role=?";
                    stmt = con.prepareStatement(deleteSqlWithRole);
                    for ( String role: patch.getRemovedEntries())
                    {
                        setTimestamp( stmt,1, patch.getLastChanged());
                        setString(stmt,2,role);
                        stmt.addBatch();
                        count++;
                    }
                    for ( String role: patch.keySet())
                    {
                        setTimestamp( stmt,1, patch.getLastChanged());
                        setString(stmt,2,role);
                        stmt.addBatch();
                        count++;
                    }
	            }
	            
	            if ( count > 0)
	            {
	                stmt.executeBatch();
	            } 
	        } finally {
	            if (stmt!=null)
	                stmt.close();
	        }
	    }
	    
	    PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement(insertSql);
            int count = 0;
            Date lastChanged = getConnectionTimestamp();
            for ( PreferencePatch patch:preferencePatches)
            {
                String userId = patch.getUserId();
                patch.setLastChanged(lastChanged );
                for ( String role:patch.keySet())
                {
                    Object entry = patch.get( role);
                    insertEntry(stmt, userId, role, entry,lastChanged);
                    count++;
                }
            }

            if ( count > 0)
            {
                stmt.executeBatch();
            } 
        } catch (SQLException ex) {
            throw ex;
        } finally {
            if (stmt!=null)
                stmt.close();
        }
    }

    @Override
	void insertAll() throws SQLException, RaplaException {
		List<Preferences> preferences = new ArrayList<Preferences>();
		{
			PreferencesImpl systemPrefs = cache.getPreferencesForUserId(null);
			if ( systemPrefs != null)
			{
				preferences.add( systemPrefs);
			}
		}
		Collection<User> users = cache.getUsers();
		for ( User user:users)
		{
			String userId = user.getId();
			PreferencesImpl userPrefs = cache.getPreferencesForUserId(userId);
			if ( userPrefs != null)
			{
				preferences.add( userPrefs);
			}
		}
		insert(preferences);
	}

    @Override
    protected int write(PreparedStatement stmt, Preferences entity) throws SQLException, RaplaException {
        PreferencesImpl preferences = (PreferencesImpl) entity;
        User user = preferences.getOwner();
        String userId = user != null ? user.getId():null; 
        int count = 0;
        for (String role:preferences.getPreferenceEntries()) {
            Object entry = preferences.getEntry(role);
            final Date lastChanged = getConnectionTimestamp();
            insertEntry(stmt, userId, role, entry,lastChanged);
            count++;
        }
       
        return count;
    }

    private void insertEntry(PreparedStatement stmt, String userId, String role, Object entry, Date lastChanged) throws SQLException, RaplaException {
        setString( stmt, 1, userId);
        setString(stmt, 2, role);
        String xml;
        String entryString;
        if ( entry instanceof String) {
            entryString = (String) entry;
        	xml = null;
        } else {
        	//System.out.println("Role " + role + " CHILDREN " + conf.getChildren().length);
            entryString = null;
            final RaplaObject raplaObject = (RaplaObject) entry;
            final RaplaType raplaType = raplaObject.getRaplaType();
            final RaplaXMLWriter raplaXMLWriter = context.lookup( PreferenceWriter.WRITERMAP ).get(raplaType);
            xml = getXML(raplaXMLWriter,raplaObject);
        }
        setString(stmt, 3, entryString);
        setText(stmt, 4, xml);
        setTimestamp( stmt,5, lastChanged);

        stmt.addBatch();
    }

    @Override
    protected void load(ResultSet rset) throws SQLException, RaplaException {
    	//findPreferences
    	//check if value set
    	//  yes read value
    	//  no read xml

        String userId = readId(rset, 1,User.class, true);
        User owner;
        if  ( userId == null || userId.equals(Preferences.SYSTEM_PREFERENCES_ID) )
        {
        	owner = null;
        }
        else
        {
        	User user = entityStore.tryResolve( userId,User.class);
        	if ( user != null)
        	{
        		owner = user;
        	}
        	else
        	{
        		getLogger().warn("User with id  " + userId + " not found ingnoring preference entry.");
        		return;
        	}
        }
   
        String configRole = getString( rset, 2, null);
        String preferenceId = PreferencesImpl.getPreferenceIdFromUser(userId);
        if ( configRole == null)
        {
        	getLogger().warn("Configuration role for " + preferenceId + " is null. Ignoring preference entry.");
        	return;
        }
        String value = getString( rset,3, null);
//        if (PreferencesImpl.isServerEntry(configRole))
//        {
//        	entityStore.putServerPreferences(owner,configRole, value);
//        	return;
//        }
        
        PreferencesImpl preferences = preferenceId != null ? (PreferencesImpl) entityStore.tryResolve( preferenceId, Preferences.class ): null;
        if ( preferences == null) 
        {
            Date now = getConnectionTimestamp();
            preferences = new PreferencesImpl(now, now);
            preferences.setId(preferenceId);
            preferences.setOwner(owner);
            put( preferences );
        }
      
        if ( value!= null) {
            preferences.putEntryPrivate(configRole, value);
        } else {
        	String xml = getText(rset, 4);
	        if ( xml != null && xml.length() > 0)
	        {
                PreferenceReader preferenceReader = (PreferenceReader )context.lookup(PreferenceReader.READERMAP).get(Preferences.TYPE);
                processXML( preferenceReader, xml );
		        RaplaObject type = preferenceReader.getChildType();
		        preferences.putEntryPrivate(configRole, type);
	        }
        }
    }

    @Override
    public void deleteEntities( Iterable<Preferences> entities) throws SQLException {
        PreparedStatement stmt = null;
        boolean deleteNullUserPreference = false;
        try {
            stmt = con.prepareStatement(deleteSql);
            boolean empty = true;
            for ( Preferences preferences: entities)
            {
                User user = preferences.getOwner();
                if ( user == null) {
                	deleteNullUserPreference = true;
                }
                empty = false;
                setId(stmt, 1, user);
                final Date lastChanged = preferences.getLastChanged();
                setTimestamp( stmt, 2, lastChanged);
                stmt.addBatch();
            }
            if ( !empty)
            {
                stmt.executeBatch();
            } 
        } finally {
            if (stmt!=null)
                stmt.close();
        }
        if ( deleteNullUserPreference )
        {
            PreparedStatement deleteNullStmt = con.prepareStatement("DELETE FROM " + getTableName() + " WHERE USER_ID IS NULL OR USER_ID=0");
            deleteNullStmt.execute();
        }
    }
 }

class UserStorage extends RaplaTypeStorage<User> {
    UserGroupStorage groupStorage;
    
    public UserStorage(RaplaXMLContext context) throws RaplaException {
        super( context,User.TYPE, "RAPLA_USER",
	    new String [] {"ID VARCHAR(255) NOT NULL PRIMARY KEY","USERNAME VARCHAR(255) NOT NULL","PASSWORD VARCHAR(255)","NAME VARCHAR(255) NOT NULL","EMAIL VARCHAR(255) NOT NULL","ISADMIN INTEGER NOT NULL", "CREATION_TIME TIMESTAMP", "LAST_CHANGED TIMESTAMP KEY"});
        groupStorage = new UserGroupStorage( context );
        addSubStorage(groupStorage);
    }
    
    @Override
    public void createOrUpdateIfNecessary(Map<String, TableDef> schema) throws SQLException, RaplaException
    {
        super.createOrUpdateIfNecessary(schema);
        checkAndDrop(schema, "DELETED");
    }

    @Override
	void insertAll() throws SQLException, RaplaException {
		insert(cache.getUsers());
	}

    @Override
    protected int write(PreparedStatement stmt,User user) throws SQLException, RaplaException {
    	setId(stmt, 1, user);
    	setString(stmt,2,user.getUsername());
    	String password = cache.getPassword(user.getId());
    	setString(stmt,3,password);
    	//setId(stmt,4,user.getPerson());
    	setString(stmt,4,user.getName());
    	setString(stmt,5,user.getEmail());
    	stmt.setInt(6,user.isAdmin()?1:0);
        setTimestamp(stmt, 7, user.getCreateTime() );
   		setTimestamp(stmt, 8, user.getLastChanged() );
   		stmt.addBatch();
   		return 1;
    }
    
    @Override
    protected void load(ResultSet rset) throws SQLException, RaplaException {
        String userId = readId(rset,1, User.class );
        String username = getString(rset,2, null);
        if ( username == null)
        {
        	getLogger().warn("Username is null for " + userId + " Ignoring user.");
        }
        String password = getString(rset,3, null);
        //String personId = readId(rset,4, Allocatable.class, true);
        String name = getString(rset,4,"");
        String email = getString(rset,5,"");
        boolean isAdmin = rset.getInt(6) == 1;
        Date createDate = getTimestampOrNow( rset, 7);
		Date lastChanged = getTimestampOrNow( rset, 8);
     	
        UserImpl user = new UserImpl(createDate, lastChanged);
//        if ( personId != null)
//        {
//            user.putId("person", personId);
//        }
        user.setId( userId );
        user.setUsername( username );
        user.setName( name );
        user.setEmail( email );
        user.setAdmin( isAdmin );
        if ( password != null) {
            putPassword(userId,password);
        }
        put(user);
   }

}

class ConflictStorage extends RaplaTypeStorage<Conflict> {
    
    public ConflictStorage(RaplaXMLContext context) throws RaplaException {
        super(context, Conflict.TYPE, "RAPLA_CONFLICT",
                new String[] { "RESOURCE_ID VARCHAR(255) NOT NULL", "APPOINTMENT1 VARCHAR(255) NOT NULL", "APPOINTMENT2 VARCHAR(255) NOT NULL",
                        "APP1ENABLED INTEGER NOT NULL", "APP2ENABLED INTEGER NOT NULL", "LAST_CHANGED TIMESTAMP KEY" });
        this.deleteSql = "delete from " + getTableName() + " where RESOURCE_ID=? and APPOINTMENT1=? and APPOINTMENT2=? and LAST_CHANGED=?";
    }
    
    @Override
    public void createOrUpdateIfNecessary(Map<String, TableDef> schema) throws SQLException, RaplaException
    {
        super.createOrUpdateIfNecessary(schema);
        checkAndDrop(schema, "DELETED");
    }

    @Override
    void insertAll() throws SQLException, RaplaException {
        insert(cache.getDisabledConflicts());
    }

    public void deleteEntities(Iterable<Conflict> entities) throws SQLException, RaplaException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement(deleteSql);
            boolean execute = false;
            for (Conflict conflict : entities)
            {
                Date lastChanged = conflict.getLastChanged();
                String allocatableId = conflict.getAllocatableId();
                String appointment1Id = conflict.getAppointment1();
                String appointment2Id = conflict.getAppointment2();
                stmt.setString(1,allocatableId);
                stmt.setString(2,appointment1Id);
                stmt.setString(3,appointment2Id);
                setTimestamp(stmt, 4, lastChanged);
                stmt.addBatch();
                execute = true;
            }
            if ( execute)
            {
                stmt.executeBatch();
            }
        } finally {
            if (stmt!=null)
                stmt.close();
        }
    }

    
    @Override
    protected int write(PreparedStatement stmt,Conflict conflict) throws SQLException, RaplaException {
        String allocatableId = conflict.getAllocatableId();
        setId(stmt, 1, allocatableId);
        setId(stmt, 2, conflict.getAppointment1());
        setId(stmt, 3, conflict.getAppointment2());
        boolean appointment1Enabled = conflict.isAppointment1Enabled();
        setInt(stmt, 4, appointment1Enabled ? 1 : 0);
        boolean appointment2Enabled = conflict.isAppointment2Enabled();
        setInt(stmt, 5, appointment2Enabled ? 1 : 0);
        setTimestamp(stmt, 6, conflict.getLastChanged() );
        stmt.addBatch();
        return 1;
    }
    
    
    @Override
    protected void load(ResultSet rset) throws SQLException, RaplaException {
        String allocatableId = readId(rset,1, Allocatable.class );
        String appointment1Id = readId(rset,2, Appointment.class );
        String appointment2Id = readId(rset,3, Appointment.class );

        boolean appointment1Enabled = rset.getInt(4) == 1;
        boolean appointment2Enabled = rset.getInt(5) == 1;
        Date timestamp = getTimestamp(rset, 6);
        Date today = getConnectionTimestamp();
        String id = ConflictImpl.createId(allocatableId, appointment1Id, appointment2Id); 
        ConflictImpl conflict = new ConflictImpl(id, today, timestamp);
        conflict.setAppointment1Enabled(appointment1Enabled );
        conflict.setAppointment2Enabled(appointment2Enabled );
        put(conflict);
   }

}

class UserGroupStorage extends EntityStorage<User> implements SubStorage<User>{
    public UserGroupStorage(RaplaXMLContext context) throws RaplaException {
        super(context,"RAPLA_USER_GROUP", new String [] {"USER_ID VARCHAR(255) NOT NULL KEY","CATEGORY_ID VARCHAR(255) NOT NULL"});
    }

    @Override
    protected int write(PreparedStatement stmt, User entity) throws SQLException, RaplaException {
        setId(stmt, 1, entity);
        int count = 0;
        for (Category category:entity.getGroupList()) {
            setId(stmt, 2, category);
            stmt.addBatch();
            count++;
        }
        return count;
    }

    @Override
    protected void load(ResultSet rset) throws SQLException, RaplaException {
        User user = resolveFromId(rset, 1, User.class);
        if ( user == null)
        {
        	return;
        }
        Category category = resolveFromId(rset, 2,  Category.class);
        if ( category == null)
        {
        	return;
        }
        user.addGroup(category);
    }
}
/*
class DeleteStorage<T extends Entity<T>> extends RaplaTypeStorage<T>
{
    protected DeleteStorage(RaplaXMLContext context) throws RaplaException
    {
        super(context, null, "DELETED", new String[] { "ID VARCHAR(255) NOT NULL PRIMARY KEY", "LAST_CHANGED TIMESTAMP KEY" });
    }
    
    public void update(Date lastUpdated, UpdateResult updateResult) throws SQLException
    {
        try(final PreparedStatement stmt = con.prepareStatement(loadAllUpdatesSql))
        {
            setTimestamp(stmt, 1, lastUpdated);
            final ResultSet result = stmt.executeQuery();
            if(result == null)
            {
                return;
            }
            while(result.next())
            {
                final String id = result.getString(1);
                if(id != null)
                {
                    // FIXME 
//                    updateResult.addOperation(new UpdateResult.Remove(id));
                }
            }
        }
    }

    @Override boolean canDelete(Entity entity)
    {
        return true;
    }

    @Override boolean canStore(Entity entity)
    {
        return false;
    }

    @Override
    public void deleteEntities(Iterable<T> entities) throws SQLException, RaplaException
    {
        try (PreparedStatement stmt = con.prepareStatement(insertSql))
        {
            boolean execute = false;
            for (T entity : entities)
            {
                write(stmt, entity);
                execute = true;
            }
            if(execute )
            {
                stmt.executeBatch();
            }
        }
    }

    @Override
    protected int write(PreparedStatement stmt, T entity) throws SQLException, RaplaException
    {
        stmt.setString(1, entity.getId());
        setTimestamp(stmt, 2, getCurrentTimestamp());
        stmt.addBatch();
        return 1;
    }

    @Override
    protected void load(ResultSet rs) throws SQLException, RaplaException
    {
        // On startup no load is needed 
    }

    @Override
    void insertAll() throws SQLException, RaplaException
    {
        // no initialization needed
    }
}
*/


class HistoryStorage<T extends Entity<T>> extends RaplaTypeStorage<T>
{

    private Gson gson;
    private final Date supportTimestamp;
    private final boolean asDeletion;
    private final String loadAllUpdatesSql;

    HistoryStorage(RaplaXMLContext context, boolean asDeletion) throws RaplaException
    {
        super(context, null, "CHANGES", new String[]{"ID VARCHAR(255) KEY", "TYPE VARCHAR(50)", "ENTITY_CLASS VARCHAR(255)", "XML_VALUE TEXT NOT NULL", "CHANGED_AT TIMESTAMP KEY", "ISDELETE INTEGER NOT NULL" });
        this.asDeletion = asDeletion;
        Class[] additionalClasses = new Class[] { RaplaMapImpl.class };
        final GsonBuilder gsonBuilder = JSONParserWrapper.defaultGsonBuilder(additionalClasses);
        loadAllUpdatesSql = "SELECT ID, TYPE, ENTITY_CLASS, XML_VALUE, CHANGED_AT, ISDELETE FROM CHANGES WHERE CHANGED_AT > ? ORDER BY CHANGED_AT ASC";
        selectSql += " ORDER BY CHANGED_AT DESC";
        gson = gsonBuilder.create();
        if(context.has(Date.class))
        {
            supportTimestamp = context.lookup(Date.class);
        }
        else
        {
            supportTimestamp = null;
        }
    }
    
    @Override
    void insertAll() throws SQLException, RaplaException
    {
        if(asDeletion)
        {
            return;
        }
        final Collection<Entity> entites = new LinkedList<Entity>();
        entites.addAll(cache.getAllocatables());
        entites.addAll(cache.getDynamicTypes());
        entites.addAll(cache.getReservations());
        entites.addAll(cache.getUsers());
        entites.addAll(getTransitiveCategories(getSuperCategory()));
        if(entites.isEmpty())
        {
            return;
        }
        try(PreparedStatement stmt = con.prepareStatement(insertSql))
        {
            for (Entity entity : entites)
            {
                write(stmt, (T) entity);
                if(entity instanceof User)
                {
                    final String userId = entity.getId();
                    final PreferencesImpl preferencesForUserId = cache.getPreferencesForUserId(userId);
                    if(preferencesForUserId != null)
                    {
                        write(stmt, (T) preferencesForUserId);
                    }
                }
            }
            stmt.executeBatch();
        }
    }

    // Don't update timestamp in historystorage it is already updated  in the storage of the entity itself
    @Override protected void updateTimestamp(ModifiableTimestamp timestamp)
    {

    }

    @Override
    public void deleteEntities(Iterable<T> entities) throws SQLException, RaplaException
    {
        if(asDeletion)
        {
            Collection<T> entitiesWithTransitiveCategories = getEntitiesWithTransitiveCategories(entities);
            super.insert(entitiesWithTransitiveCategories);
        }
    }
    
    @Override
    public void insert(Iterable<T> entities) throws SQLException, RaplaException
    {
        if(!asDeletion)
        {
            Collection<T> entitiesWithTransitiveCategories = getEntitiesWithTransitiveCategories(entities);
            super.insert(entitiesWithTransitiveCategories);
        }
    }

    protected Collection<T> getEntitiesWithTransitiveCategories(Iterable<T> entities)
    {
        Collection<T> entitiesWithTransitiveCategories = new LinkedList<T>();
        for(T entity : entities)
        {
            if(entity instanceof Category)
            {
                final Collection<Category> transitiveCategories = getTransitiveCategories((Category) entity);
                for (Category category : transitiveCategories)
                {
                    entitiesWithTransitiveCategories.add((T) category);
                }
            }
            else
            {
                entitiesWithTransitiveCategories.add(entity);
            }
        }
        return entitiesWithTransitiveCategories;
    }
    
    @Override
    boolean canDelete(Entity entity)
    {
        return asDeletion && isSupportedEntity(entity);
    }
    
    @Override
    boolean canStore(@SuppressWarnings("rawtypes") Entity entity)
    {
        return !asDeletion && isSupportedEntity(entity);
    }

    private boolean isSupportedEntity(Entity entity)
    {
        return (entity instanceof Allocatable) || (entity instanceof DynamicType) || (entity instanceof Reservation) || (entity instanceof User)
                || (entity instanceof Category) || (entity instanceof Preferences);
    }
    
    @Override
    protected int write(PreparedStatement stmt, T entity) throws SQLException, RaplaException
    {
        stmt.setString(1, entity.getId());
        stmt.setString(2, entity.getRaplaType().getLocalName());
        stmt.setString(3, entity.getClass().getCanonicalName());
        setText(stmt, 4, gson.toJson(entity));
        Date lastChanged;
        if(entity instanceof Timestamp)
        {
            lastChanged = Timestamp.class.cast(entity).getLastChanged();
            if(lastChanged == null)
            {
                lastChanged = getConnectionTimestamp();
            }
        }
        else
        {
            lastChanged = getConnectionTimestamp();
        }
        setTimestamp(stmt, 5, lastChanged);
        setInt(stmt,6, asDeletion? 1:0);
        stmt.addBatch();
        return 1;
    }
    
    public void update(Date lastUpdated) throws SQLException
    {
        if(asDeletion)
        {
            return;
        }
        try(final PreparedStatement stmt = con.prepareStatement(loadAllUpdatesSql))
        {
            setTimestamp(stmt, 1, lastUpdated);
            final ResultSet result = stmt.executeQuery();
            if(result == null)
            {
                return;
            }
            while(result.next())
            {
                load(result);
            }
        }
    }

    @Override
    public void loadAll() throws SQLException, RaplaException
    {
        if(asDeletion)
        {
            return;
        }
        try (Statement stmt = con.createStatement(); ResultSet rset = stmt.executeQuery(selectSql))
        {
            final HashSet<String> finishedIdsToLoad = new HashSet<String>();
            while (rset.next())
            {
                if(finishedIdsToLoad.contains(rset.getString(1)))
                {
                    continue;
                }
                load(rset);
                // the select is ordered desc by last_changed, so if we get to early in time, we do not need to load it
                if(supportTimestamp != null && getTimestamp(rset, 5).getTime() < supportTimestamp.getTime())
                {
                    finishedIdsToLoad.add(rset.getString(1));
                }
            }
        }
        {
            final Collection<String> allIds = history.getAllIds();
            final Date connectionTimestamp = getConnectionTimestamp();
            for (String id : allIds)
            {
                final Entity<?> entity = entityStore.resolve(id);
                if(entity instanceof Timestamp)
                {
                    final Date lastChanged = ((Timestamp) entity).getLastChanged();
                    if(lastChanged != null)
                    {
                        if (!lastChanged.before(connectionTimestamp))
                        {// we need to restore from history
                            final HistoryEntry before = history.getBefore(id, connectionTimestamp);
                            if (before != null)
                            {
                                put(history.getEntity(before));
                            }
                        }
                    }
                    else
                    {
                        logger.debug("Ignoring entity without timestamp " + entity);
                    }
                }
            }
        }
    }
    
    @Override
    protected void load(ResultSet rs) throws SQLException, RaplaException
    {
        final String id = rs.getString(1);
        final String raplaTypeLocalName = rs.getString(2);
        final RaplaType raplaType = RaplaType.find(raplaTypeLocalName);
        final String className = getString(rs, 3, null);
        final String json = getText(rs, 4);
        final Date lastChanged = getTimestamp(rs, 5);
        final Integer isDelete = getInt( rs, 6);
        try
        {
            @SuppressWarnings({ "rawtypes", "unchecked" })
            final Class<? extends Entity> entityClass = (Class<? extends Entity>) Class.forName(className);
            history.addHistoryEntry(id, json, entityClass, lastChanged, isDelete != null && isDelete == 1);
        }
        catch (ClassNotFoundException e)
        {
            throw new RaplaException("found history entry (" + id + ") with classname " + className + " which is not defined in java");
        }
    }
    
}

class ImportExportStorage extends RaplaTypeStorage<ImportExportEntity>
{
    
    public ImportExportStorage(RaplaXMLContext context) throws RaplaException
    {
        super(context, ImportExportEntityImpl.raplaType, "IMPORT_EXPORT",
                new String[] { "FOREIGN_ID VARCHAR(255) KEY", "EXTERNAL_SYSTEM VARCHAR(255) KEY", "RAPLA_ID VARCHAR(255)", "DIRECTION INTEGER NOT NULL", "DATA TEXT NOT NULL",
                "CONTEXT TEXT NOT NULL", "CHANGED_AT TIMESTAMP KEY" });
    }

    @Override
    void insertAll() throws SQLException, RaplaException
    {
        // do nothing
    }

    @Override
    protected int write(PreparedStatement stmt, ImportExportEntity entity) throws SQLException, RaplaException
    {
        stmt.setString(1, entity.getId());
        stmt.setString(2, entity.getExternalSystem());
        stmt.setString(3, entity.getRaplaId());
        setInt(stmt, 4, entity.getDirection());
        setText(stmt, 5, entity.getData());
        setText(stmt, 6, entity.getContext());
        setTimestamp(stmt, 7, getConnectionTimestamp());
        return 0;
    }

    @Override
    protected void load(ResultSet rs) throws SQLException, RaplaException
    {
        final ImportExportEntityImpl importExportEntityImpl = new ImportExportEntityImpl();
        importExportEntityImpl.setId(rs.getString(1));
        importExportEntityImpl.setExternalSystem(rs.getString(2));
        importExportEntityImpl.setRaplaId(rs.getString(3));
        importExportEntityImpl.setDirection(rs.getInt(4));
        importExportEntityImpl.setData(getText(rs, 5));
        importExportEntityImpl.setContext(getText(rs, 6));
        put(importExportEntityImpl);
    }
    
}
