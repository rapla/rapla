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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.Timestamp;
import org.rapla.entities.User;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.internal.ModifiableTimestamp;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.storage.LocalCache;
import org.rapla.storage.impl.EntityStore;
import org.rapla.storage.impl.server.EntityHistory;
import org.rapla.storage.xml.RaplaXMLContext;

abstract class EntityStorage<T extends Entity<T>> extends AbstractTableStorage implements Storage<T> {
	//String searchForIdSql;

    protected LocalCache cache;
    protected EntityStore entityStore;
    private RaplaLocale raplaLocale;
    
    protected final EntityHistory history;
    protected Collection<SubStorage<T>> subStores = new ArrayList<SubStorage<T>>();
	private int lastParameterIndex;
    RaplaXMLContext context;

    protected EntityStorage( RaplaXMLContext context, String table,String[] entries) throws RaplaException {
        this(context,table,entries, containsLastChangedColumn(entries));
    }
    private static boolean containsLastChangedColumn(String[] entries)
    {
        for (String columnDef : entries)
        {
            if(columnDef.startsWith("LAST_CHANGED"))
            {
                return true;
            }
        }
        return false;
    }
    protected EntityStorage( RaplaXMLContext context, String table,String[] entries, boolean checkLastChanged) throws RaplaException {
		super(table,context.lookup(Logger.class), entries, checkLastChanged);
        this.context = context;
        if ( context.has( EntityStore.class))
        {
            this.entityStore =  context.lookup( EntityStore.class);
        }
        if ( context.has( LocalCache.class))
        {
            this.cache = context.lookup( LocalCache.class);
        }
        if(context.has(EntityHistory.class))
        {
            this.history = context.lookup(EntityHistory.class);
        }
        else
        {
            this.history = null;
        }
        this.raplaLocale = context.lookup( RaplaLocale.class);
		lastParameterIndex = entries.length;


    }

	public void setForeignId(String foreignId)
	{
		selectUpdateSql = selectUpdateSql.replace("where "+idName,"where " + foreignId);
		deleteSql = deleteSql.replace("where "+ idName, "where " + foreignId);
	}

	public void updateWithForeignId( String foreignId) throws SQLException, RaplaException
	{
		try (final PreparedStatement stmt = con.prepareStatement(selectUpdateSql))
		{
			stmt.setString(1, foreignId);
			final ResultSet result = stmt.executeQuery();
			if (result == null)
			{
				return;
			}
			while (result.next())
			{
				load(result);
				String subId =result.getString(idName);
				updateSubstores( subId);
			}
		}
	}
    /*
    public void update(String id) throws SQLException
    {// default implementation is to ask the sub stores to update

    }*/

	protected void setId(PreparedStatement stmt, int column, Entity<?> entity) throws SQLException {
	    setId( stmt, column, entity != null ? entity.getReference() : null);
	}

	protected void setId(PreparedStatement stmt, int column, ReferenceInfo id) throws SQLException {
        if ( id != null) {
            stmt.setString( column, id.getId() );
        } else {
            stmt.setObject(column, null, Types.VARCHAR);
        }
    }

	protected <T extends Entity> ReferenceInfo<T> readId(ResultSet rset, int column, Class<T> class1) throws SQLException, RaplaException {
		return readId(rset, column, class1, false);
	}

	protected <T extends Entity> ReferenceInfo readId(ResultSet rset, int column, @SuppressWarnings("unused") Class<T> class1, boolean nullAllowed) throws SQLException, RaplaException {
		String id = rset.getString( column );
		if ( rset.wasNull() || id == null )
		{
			if ( nullAllowed )
			{
				return null;
			}
			throw new RaplaException("Id can't be null for " + getTableName());
		}
		return new ReferenceInfo(id, class1);
	}

    protected <S extends Entity> S resolveFromId(ResultSet rset, int column, Class<S> class1) throws SQLException
    {
		String id = rset.getString( column );
        if  (rset.wasNull() || id == null)
        {
            return null;
        }
        try {
            S resolved = entityStore.resolve(id, class1);
			return resolved;
        }
        catch ( EntityNotFoundException ex)
        {
			getLogger().warn("Could not find "  + class1.getName() +"  with id "+ id + " in the " + getTableName() + " table. Ignoring." );
            return null;
        }
    }

	protected void updateSubstores(String foreignId) throws SQLException, RaplaException
	{
		for (SubStorage<T> subStorage:getSubStores())
		{
			subStorage.updateWithForeignId(foreignId);
		}
	}

	protected void addSubStorage(SubStorage<T> subStore) {
    	subStores.add(subStore);
    }

    public Collection<SubStorage<T>> getSubStores() {
		return subStores;
	}


    public void setConnection(Connection con, Date connectionTimestamp) throws SQLException {
		super.setConnection(con,connectionTimestamp);
		for (TableStorage subStore: subStores) {
		    subStore.setConnection(con, connectionTimestamp);
		}

    }

    public Locale getLocale() {
    	return raplaLocale.getLocale();
    }

	public boolean has(String id)
    {
        try ( PreparedStatement stmt = con.prepareStatement(containsSql))
        {

            stmt.setString(1, id);
            stmt.execute();
            final ResultSet resultSet = stmt.getResultSet();
            final boolean has = resultSet != null && resultSet.next() && resultSet.getInt(1) == 1;
            return has;
        }
        catch(Exception e)
        {

        }
        return false;
    }

	public void loadAll() throws SQLException,RaplaException {

        try (Statement stmt = con.createStatement())
		{
			try (ResultSet rset = stmt.executeQuery(selectSql))
			{
				while (rset.next())
				{
					load(rset);
				}
			}
		}
        for (Storage storage: subStores) {
            storage.loadAll();
        }
    }

    public void insert(Iterable<T> entities) throws SQLException,RaplaException {
        for (Storage<T> storage: subStores)
        {
            storage.insert(entities);
        }
        try (PreparedStatement stmt = con.prepareStatement(insertSql)){
            int count = 0;
            for ( T entity: entities)
            {
                count+= write(stmt, entity);
            }
            if ( count > 0)
            {
                stmt.executeBatch();
            }
        } catch (SQLException ex) {
            throw ex;
        }
    }

//    public void update(Collection<Entity>> entities ) throws SQLException,RaplaException {
//        for (Storage<T> storage: subStores) {
//            storage.delete( entities );
//		    storage.insert( entities);
//		}
//        PreparedStatement stmt = null;
//        try {
//            stmt = con.prepareStatement( updateSql);
//            int count = 0;
//            for (Entity entity: entities)
//            {
//                int id = getId( entity );
//                stmt.setInt( lastParameterIndex + 1,id );
//                count+=write(stmt, entity);
//            }
//            if ( count > 0)
//            {
//                stmt.executeBatch();
//            }
//        } finally {
//            if (stmt!=null)
//                stmt.close();
//        }
//    }

//    public void save(Collection<Entity>> entities) throws SQLException,RaplaException {
//        Collection<Entity>>  toUpdate = new ArrayList<Entity>>();
//        Collection<Entity>>  toInsert = new ArrayList<Entity>>();
//        for (Entity entity:entities)
//        {
//
//            if (cache.tryResolve( entity.getId())!= null) {
//    		    toUpdate.add( entity );
//    		} else {
//    		    toInsert.add( entity );
//    		}
//        }
//        if  ( !toInsert.isEmpty())
//        {
//            insert( toInsert);
//        }
//        if  ( !toUpdate.isEmpty())
//        {
//            update( toUpdate);
//        }
//    }

    public void save( Iterable<T> entities ) throws RaplaException, SQLException{
        Collection<ReferenceInfo<T>> toDelete = new ArrayList<ReferenceInfo<T>>();
        for (Entity entity:entities)
        {
            toDelete.add( entity.getReference());
        }
        deleteEntities(  toDelete );
		for (Entity entity:entities)
		{
			if (entity instanceof ModifiableTimestamp)
			{
				final ModifiableTimestamp timestamp = (ModifiableTimestamp) entity;
				updateTimestamp( timestamp );
			}
		}
        insert( entities );
    }

	protected void updateTimestamp(ModifiableTimestamp timestamp)
	{
		final Date currentTimestamp = getConnectionTimestamp();
		timestamp.setLastChanged(currentTimestamp);
	}

    public void deleteEntities(Iterable<ReferenceInfo<T>> entities) throws SQLException, RaplaException {
        Set<String> ids = new HashSet<String>();
        for ( ReferenceInfo entity: entities)
        {
        	ids.add( entity.getId());
        }
        if(ids.isEmpty())
        {
            return;
        }
        deleteFromSubStores(ids);
        if(checkLastChanged)
        {
            PreparedStatement stmt = null;
            try
            {
                stmt = con.prepareStatement(deleteSql);
                boolean commitNeeded = false;
                for (ReferenceInfo referenceInfo : entities)
                {
                    final Timestamp castedEntity = (Timestamp)cache.get(referenceInfo.getId());
                    if(has(referenceInfo.getId()))
                    {
                        stmt.setString(1, referenceInfo.getId());
                        setTimestamp(stmt, 2, castedEntity.getLastChanged());
                        stmt.addBatch();
                        commitNeeded = true;
                    }
                }
                if(commitNeeded)
                {
                    final int[] executeBatch = stmt.executeBatch();
                    for (int i : executeBatch)
                    {
                        if (i != 1)
                        {
                            throw new RaplaException("Entry already deleted");
                        }
                    }
                }
            }
            finally
            {
                if (stmt != null)
                {
                    stmt.close();
                }
            }

        }
        else
        {
            deleteIds(ids);
        }
    }

    protected void deleteFromSubStores(Set<String> ids) throws SQLException, RaplaException
    {
        for (SubStorage<T> storage : subStores)
        {
            storage.deleteIds(ids);
        }
    }

	public void deleteIds(Collection<String> ids) throws SQLException, RaplaException {
    	PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement(deleteSql);
            for ( String id: ids)
            {
                stmt.setString(1,id);
                stmt.addBatch();
            }
            if ( ids.size() > 0)
            {
                stmt.executeBatch();
            }
        } finally {
            if (stmt!=null)
                stmt.close();
        }
	}

    public void deleteAll() throws SQLException {
		for (Storage<T> subStore: subStores)
		{
		    subStore.deleteAll();
		}
		executeBatchedStatement(con,deleteAllSql);
    }
    abstract protected int write(PreparedStatement stmt,T entity) throws SQLException,RaplaException;
    abstract protected void load(ResultSet rs) throws SQLException,RaplaException;

    protected void put( Entity entity )
    {
		entityStore.put(entity);
    }

    protected EntityResolver getResolver()
    {
        return entityStore;
    }

    protected void putPassword( ReferenceInfo<User> userId, String password )
    {
        entityStore.putPassword( userId, password);
    }

    protected DynamicType getDynamicType( String typeKey )
    {
        return entityStore.getDynamicType( typeKey);
    }

}




