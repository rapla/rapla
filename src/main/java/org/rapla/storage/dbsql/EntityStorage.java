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

import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.rapla.components.util.DateTools;
import org.rapla.components.util.xml.RaplaNonValidatedInput;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.RaplaType;
import org.rapla.entities.Timestamp;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.TypedComponentRole;
import org.rapla.framework.logger.Logger;
import org.rapla.server.internal.TimeZoneConverterImpl;
import org.rapla.storage.LocalCache;
import org.rapla.storage.impl.EntityStore;
import org.rapla.storage.xml.PreferenceReader;
import org.rapla.storage.xml.PreferenceWriter;
import org.rapla.storage.xml.RaplaXMLContext;
import org.rapla.storage.xml.RaplaXMLContextException;
import org.rapla.storage.xml.RaplaXMLReader;
import org.rapla.storage.xml.RaplaXMLWriter;

abstract class EntityStorage<T extends Entity<T>> implements Storage<T> {
    protected String insertSql;
    protected String deleteSql;
    protected String selectSql;
    protected String deleteAllSql;
    private String containsSql;
    protected String loadAllUpdatesSql;
	protected String selectUpdateSql;
    //String searchForIdSql;

    RaplaXMLContext context;
    protected LocalCache cache;
    protected EntityStore entityStore;
    private RaplaLocale raplaLocale;

    protected Collection<SubStorage<T>> subStores = new ArrayList<SubStorage<T>>();
    protected Connection con;
    private int lastParameterIndex; /** first paramter is 1 */
    protected final String tableName;
    protected final boolean hasLastChangedTimestamp;

    protected Logger logger;
    private String dbProductName = "";
    protected Map<String,ColumnDef> columns = new LinkedHashMap<String,ColumnDef>();

    private Calendar datetimeCal;
	protected String idName;

	protected EntityStorage( RaplaXMLContext context, String table,String[] entries) throws RaplaException {
        this.context = context;
        if ( context.has( EntityStore.class))
        {
            this.entityStore =  context.lookup( EntityStore.class);
        }
        if ( context.has( LocalCache.class))
        {
            this.cache = context.lookup( LocalCache.class);
        }
        this.raplaLocale = context.lookup( RaplaLocale.class);
        datetimeCal =Calendar.getInstance( getSystemTimeZone());
        logger = context.lookup( Logger.class);
        lastParameterIndex = entries.length;
        tableName = table;
        boolean hasLastChangedColumn = false;
        for ( String unparsedEntry: entries)
        {
        	ColumnDef col = new ColumnDef(unparsedEntry);
        	if (col.getName().equals("LAST_CHANGED"))
        	{
        	    hasLastChangedColumn = true;
        	}
        	columns.put( col.getName(), col);
        }
        this.hasLastChangedTimestamp = hasLastChangedColumn;
    	createSQL(columns.values());
        if (getLogger().isDebugEnabled()) {
            getLogger().debug(insertSql);
            getLogger().debug(deleteSql);
            getLogger().debug(selectSql);
            getLogger().debug(deleteAllSql);
            getLogger().debug(containsSql);
            getLogger().debug(loadAllUpdatesSql);
        }
    }

	public void setForeignId(String foreignId)
	{
		selectUpdateSql = selectUpdateSql.replace("where "+idName,"where " + foreignId);
		deleteSql = deleteSql.replace("where "+ idName, "where " + foreignId);
	}

	public void updateWithForeignId( String foreignId) throws SQLException
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

	protected Date getDate( ResultSet rset,int column) throws SQLException
	{

		java.sql.Timestamp timestamp = rset.getTimestamp( column, datetimeCal);
		if (rset.wasNull() || timestamp == null)
		{
			return null;
		}
		long time = timestamp.getTime();
		TimeZone systemTimeZone = getSystemTimeZone();
		long offset = TimeZoneConverterImpl.getOffset( DateTools.getTimeZone(), systemTimeZone, time);
		Date returned = new Date(time + offset);
		return returned;
	}

	// Always use gmt for storing timestamps
	protected Date getTimestampOrNow(ResultSet rset, int column) throws SQLException {
	    Date currentTimestamp = getCurrentTimestamp();
	    java.sql.Timestamp timestamp = rset.getTimestamp( column, datetimeCal);
        if (rset.wasNull() || timestamp == null)
        {
            return currentTimestamp;
        }
        Date date = new Date( timestamp.getTime());
        if ( date != null)
		{
		    if ( date.after( currentTimestamp))
		    {
		        getLogger().error("Timestamp in table " + tableName + " in the future. Ignoring.");
		    }
		    else
		    {
		        return date;
		    }
		}
        return currentTimestamp;
	}

    protected Date getTimestamp(ResultSet rset, int column) throws SQLException {
        Date currentTimestamp = getCurrentTimestamp();
        java.sql.Timestamp timestamp = rset.getTimestamp( column, datetimeCal);
        if (rset.wasNull() || timestamp == null)
        {
            return null;
        }
        Date date = new Date( timestamp.getTime());
        if ( date != null)
        {
            if ( date.after( currentTimestamp))
            {
                getLogger().error("Timestamp in table " + tableName + " in the future. Something went wrong");
            }
            else
            {
                return date;
            }
        }
        return null;
    }

	public Date getCurrentTimestamp() {
		long time = System.currentTimeMillis();
		return new Date( time);
	}

	public String getIdColumn() {
        for (Map.Entry<String, ColumnDef> entry:columns.entrySet())
        {
            String column = entry.getKey();
            ColumnDef def = entry.getValue();
            if ( def.isPrimary())
            {
                return column;
            }
        }
        return null;
    }
    public String getTableName() {
        return tableName;
    }

	protected TimeZone getSystemTimeZone() {
		return TimeZone.getDefault();
	}

	protected void setDate(PreparedStatement stmt,int column, Date time) throws SQLException {
    	if ( time != null)
        {
    		TimeZone systemTimeZone = getSystemTimeZone();
    		// same as TimeZoneConverterImpl.fromRaplaTime
    		long offset = TimeZoneConverterImpl.getOffset( DateTools.getTimeZone(), systemTimeZone, time.getTime());
            long timeInMillis = time.getTime() - offset;
			stmt.setTimestamp( column, new java.sql.Timestamp( timeInMillis), datetimeCal);
        }
        else
        {
            stmt.setObject(column, null, Types.TIMESTAMP);
        }
	}

	protected void setTimestamp(PreparedStatement stmt,int column, Date time) throws SQLException {
    	if ( time != null)
        {
    		//TimeZone systemTimeZone = getSystemTimeZone();
    		// same as TimeZoneConverterImpl.fromRaplaTime
    		//long offset = TimeZoneConverterImpl.getOffset( DateTools.getTimeZone(), systemTimeZone, time.getTime());
    		long offset = 0;
            long timeInMillis = time.getTime() - offset;
			final java.sql.Timestamp x = new java.sql.Timestamp(timeInMillis);
			stmt.setTimestamp( column, x, datetimeCal);
        }
        else
        {
            stmt.setObject(column, null, Types.TIMESTAMP);
        }
	}

	protected void setId(PreparedStatement stmt, int column, Entity<?> entity) throws SQLException {
	    setId( stmt, column, entity != null ? entity.getId() : null);
	}

	protected void setId(PreparedStatement stmt, int column, String id) throws SQLException {
        if ( id != null) {
            stmt.setString( column, id );
        } else {
            stmt.setObject(column, null, Types.VARCHAR);
        }
    }

	protected String readId(ResultSet rset, int column, Class<? extends Entity> class1) throws SQLException, RaplaException {
		return readId(rset, column, class1, false);
	}

	protected String readId(ResultSet rset, int column, @SuppressWarnings("unused") Class<? extends Entity> class1, boolean nullAllowed) throws SQLException, RaplaException {
		String id = rset.getString( column );
		if ( rset.wasNull() || id == null )
		{
			if ( nullAllowed )
			{
				return null;
			}
			throw new RaplaException("Id can't be null for " + tableName );
		}
		return id;
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
			getLogger().warn("Could not find "  + class1.getName() +"  with id "+ id + " in the " + tableName + " table. Ignoring." );
            return null;
        }
    }

    protected void setInt(PreparedStatement stmt, int column, Integer number) throws SQLException {
    	if ( number != null) {
		    stmt.setInt( column, number.intValue() );
		} else {
			stmt.setObject(column, null, Types.INTEGER);
		}
	}

    protected String getString(ResultSet rset,int index, String defaultString) throws SQLException {
		String value = rset.getString(index);
		if (rset.wasNull() || value == null)
		{
			return defaultString;
		}
		return value;
    }
    protected Integer getInt( ResultSet rset,int column) throws SQLException
	{
		Integer value = rset.getInt( column);
		if (rset.wasNull() || value == null)
		{
			return null;
		}
		return value;
	}
    protected void setLong(PreparedStatement stmt, int column, Long number) throws SQLException {
    	if ( number != null) {
		    stmt.setLong( column, number.longValue() );
		} else {
			stmt.setObject(column, null, Types.BIGINT);
		}
	}



    protected void setString(PreparedStatement stmt, int column, String object) throws SQLException {
		if ( object == null)
		{
			stmt.setObject( column, null, Types.VARCHAR);
		}
		else
		{
			stmt.setString( column, object);
		}
	}

    protected Logger getLogger() {
        return logger;
    }

    public List<String> getCreateSQL()
    {
    	List<String> createSQL = new ArrayList<String>();
    	StringBuffer buf = new StringBuffer();
    	String table = tableName;
		buf.append("CREATE TABLE " + table + " (");
		List<String> keyCreates = new ArrayList<String>();
		boolean first= true;
		for (ColumnDef col: columns.values())
		{
			if (first)
			{
				first = false;
			}
			else
			{
				buf.append( ", ");
			}
			boolean includePrimaryKey = true;
			boolean includeDefaults = false;
			String colSql = getColumnCreateStatemet(col, includePrimaryKey, includeDefaults);
			buf.append(colSql);
			if (  col.isKey() && !col.isPrimary())
			{
				String colName = col.getName();
				String keyCreate = createKeySQL(table, colName);
				keyCreates.add(keyCreate);
			}
		}
		buf.append(")");
		String sql = buf.toString();
		createSQL.add( sql);
		createSQL.addAll( keyCreates);
		return createSQL;
	}

    protected void createSQL(Collection<ColumnDef> entries) {

        idName = entries.iterator().next().getName();
        String table = tableName;
		selectSql = "select " + getEntryList(entries) + " from " + table ;
        containsSql = "select count(" + idName + ") from " + table + " where " + idName + "= ?";
        deleteSql = "delete from " + table + " where " + idName + "= ?" + (hasLastChangedTimestamp ? " AND LAST_CHANGED = ?" : "");
		selectUpdateSql = "SELECT " + getEntryList(entries) + " from " + tableName + " where " + idName + " = ?";
		String valueString = " (" + getEntryList(entries) + ")";
		insertSql = "insert into " + table + valueString + " values (" + getMarkerList(entries.size()) + ")";
		deleteAllSql = "delete from " + table;
		loadAllUpdatesSql = hasLastChangedTimestamp ? selectSql + " where LAST_CHANGED > ? " : null;
		//searchForIdSql = "select id from " + table + " where id = ?";
	}

	//CREATE INDEX KEY_ALLOCATION_APPOINTMENT ON ALLOCATION(APPOINTMENT_ID);
	private String createKeySQL(String table, String colName) {
		return "create index KEY_"+ table + "_" + colName + " on " + table + "(" + colName +")";
	}

    public void createOrUpdateIfNecessary( Map<String,TableDef> schema) throws SQLException, RaplaException
    {
    	String tablename = tableName;
    	if (schema.get (tablename) != null )
    	{
    		return;
    	}
        getLogger().info("Creating table " + tablename);
        for (String createSQL : getCreateSQL())
		{
			Statement stmt = con.createStatement();
			try
			{
				stmt.execute(createSQL );
			}
			finally
			{
				stmt.close();
			}
			con.commit();
		}
        schema.put( tablename, new TableDef(tablename,columns.values()));
    }

	protected ColumnDef getColumn(String name)
    {
    	return columns.get( name);
    }

    protected void checkAndAdd(Map<String, TableDef> schema, String columnName) throws SQLException {
		ColumnDef col = getColumn(columnName);
		if ( col == null)
		{
			throw new IllegalArgumentException("Column " + columnName + " not found in table schema " + tableName);
		}
    	String name = col.getName();
		TableDef tableDef = schema.get(tableName);

		if (tableDef.getColumn(name) == null)
        {
            getLogger().warn("Patching Database for table " + tableName + " adding column "+ name);
            {
            	String sql = "ALTER TABLE " + tableName + " ADD COLUMN ";
                sql += getColumnCreateStatemet( col, true, true);
    			Statement stmt = con.createStatement();
    			try
    			{
    				stmt.execute( sql);
    			}
    			finally
    			{
    				stmt.close();
    			}

            	con.commit();
            }
			if ( col.isKey() && !col.isPrimary())
			{
				String sql = createKeySQL(tableName, name);
	            getLogger().info("Adding index for " + name);
	            Statement stmt = con.createStatement();
    			try
    			{
    				stmt.execute( sql);
    			}
    			finally
    			{
    				stmt.close();
    			}
	            con.commit();
			}
        }
	}

	protected void updateSubstores(String foreignId) throws SQLException
	{
		for (SubStorage<T> subStorage:getSubStores())
		{
			subStorage.updateWithForeignId(foreignId);
		}
	}

    protected String getDatabaseProductType(String type) {
        if ( type.equals("TEXT"))
        {
            if ( isHsqldb())
            {
		        return "VARCHAR(16777216)";
            }
            if ( isMysql())
            {
                return "LONGTEXT";
            }
            if ( isH2())
            {
                return "CLOB";
            }
        }
        if ( isSQLServer())
        {
            final Matcher matcher = Pattern.compile("VARCHAR\\((\\d+)\\)").matcher(type);
            if ( matcher.find())
            {
                final String group = matcher.group(1);
                if ( Integer.parseInt(group)> 8000)
                {
                    return "VARCHAR(8000)";
                }
            }
        }
        if ( type.equals("TIMESTAMP"))
        {
            if (isSQLServer())
            {
                return "DATETIME";
            }
        }

        if ( type.equals("DATETIME"))
        {
            if ( !isH2() && !isMysql() && !isSQLServer())
            {
                return "TIMESTAMP";
            }
        }
		return type;
	}


	protected void checkAndRename( Map<String, TableDef> schema, String oldColumnName,
			String newColumnName) throws  SQLException
    {
		String errorPrefix = "Can't rename " + oldColumnName + " " + newColumnName + " in table " + tableName;
		TableDef tableDef = schema.get(tableName);
		if (tableDef.getColumn( newColumnName) != null )
    	{
    		return;
    	}
		ColumnDef oldColumn = tableDef.getColumn( oldColumnName);
		if (oldColumn == null)
    	{
			throw new SQLException(errorPrefix + " old column " + oldColumnName + " not found.");
    	}
		ColumnDef newCol = getColumn(newColumnName);
		if ( newCol == null)
		{
			throw new IllegalArgumentException("Column " + newColumnName + " not found in table schema " + tableName);
		}
		getLogger().warn("Patching Database for table " + tableName + " renaming column "+ oldColumnName + " to " + newColumnName);
        String sql = "ALTER TABLE " + tableName + " RENAME COLUMN " + oldColumnName + " TO " + newColumnName;
		if ( isMysql())
        {
			sql =   "ALTER TABLE " + tableName + " CHANGE COLUMN " +oldColumnName + " ";
			String columnSql = getColumnCreateStatemet(newCol, false, true);
			sql+= columnSql;

        }
		Statement stmt = con.createStatement();
		try
		{
			stmt.execute( sql);
		}
		finally
		{
			stmt.close();
		}
		con.commit();
        tableDef.removeColumn( oldColumnName);
        tableDef.addColumn( newCol);
	}

	protected void checkAndRetype(Map<String, TableDef> schema, String columnName) throws RaplaException
	{
		TableDef tableDef = schema.get(tableName);
		ColumnDef oldDef = tableDef.getColumn( columnName);
		ColumnDef newDef = getColumn(columnName);
		if (oldDef == null  || newDef == null)
    	{
			throw new RaplaException("Can't retype column " + columnName + " it is not found");
    	}
		boolean includePrimaryKey = false;
		boolean includeDefaults = false;
		String stmt1 = getColumnCreateStatemet(oldDef, includePrimaryKey, includeDefaults);
		String stmt2 = getColumnCreateStatemet(newDef, includePrimaryKey, includeDefaults);
		if ( stmt1.equals( stmt2))
		{
			return;
		}

		String columnSql = getColumnCreateStatemet(newDef, false, true);
		getLogger().warn("Column "+ tableName + "."+ columnName + " change from '" + stmt1+  "' to new type '" + columnSql + "'");
		getLogger().warn("You should patch the database accordingly.");
// We do not autopatch colum types yet
//		String sql =   "ALTER TABLE " + tableName + " ALTER COLUMN " ;
	//	sql+= columnSql;
    //    con.createStatement().execute(sql);
	}

	protected String getColumnCreateStatemet(ColumnDef col, boolean includePrimaryKey, boolean includeDefaults) {
		StringBuffer buf = new StringBuffer();
		String colName = col.getName();
		buf.append(colName);
		final String origType = col.getType();
        String type = getDatabaseProductType(origType);
		buf.append(" " + type);
		if ( col.isNotNull())
		{
			buf.append(" NOT NULL");
		}
		else if ( !isSQLServer())
		{
			buf.append(" NULL");
		}
		if ( includeDefaults)
		{
			if ( type.equals("TIMESTAMP"))
			{
				if ( !isHsqldb() && !isH2())
				{
					buf.append( " DEFAULT " + "'2000-01-01 00:00:00'");
				}
			}
			else if ( col.getDefaultValue() != null)
			{
				buf.append( " DEFAULT " + col.getDefaultValue());
			}
		}
		if (includePrimaryKey &&  col.isPrimary())
		{
			buf.append(" PRIMARY KEY");
		}
		String columnSql =buf.toString();
		return columnSql;
	}

	protected boolean isMysql() {
		boolean result = dbProductName.indexOf("mysql") >=0;
		return result;
	}

	protected boolean isSQLServer() {
        boolean result = dbProductName.toLowerCase().indexOf("microsoft") >=0;
        return result;
    }

	protected boolean isHsqldb() {
		boolean result = dbProductName.indexOf("hsql") >=0;
		return result;
	}

	protected boolean isPostgres() {
		boolean result = dbProductName.indexOf("postgres") >=0;
		return result;
	}

	protected boolean isH2() {
		boolean result = dbProductName.indexOf("h2") >=0;
		return result;
	}


    protected void checkRenameTable( Map<String, TableDef> tableMap, String oldTableName) throws SQLException
    {
        boolean isOldTableName = false;
		if ( tableMap.get( oldTableName) != null)
        {
            isOldTableName = true;
        }

		if ( tableMap.get(tableName) != null)
        {
            isOldTableName = false;
        }

	    if ( isOldTableName)
	    {
	    	getLogger().warn("Table " + tableName + " not found. Patching Database : Renaming " + oldTableName + " to "+ tableName);
	    	String sql = "ALTER TABLE " + oldTableName + " RENAME TO " + tableName + "";

			try ( Statement stmt = con.createStatement())
			{
				stmt.execute( sql);
			}
			con.commit();
			tableMap.put( tableName, tableMap.get( oldTableName));
			tableMap.remove( oldTableName);
	    }
	}

    protected void checkAndDrop(Map<String, TableDef> schema, String columnName) throws SQLException {
    	TableDef tableDef = schema.get(tableName);
		if (tableDef.getColumn(columnName) != null)
        {
			String sql = "ALTER TABLE " + tableName + " DROP COLUMN " + columnName;
			try ( Statement stmt = con.createStatement())
			{
				stmt.execute( sql);
			}
		}
		con.commit();
	}

    @Override
    public void dropTable() throws SQLException
    {
        getLogger().info("Dropping table " + tableName);
        String sql = "DROP TABLE " + tableName ;
        try (Statement stmt = con.createStatement())
        {
            stmt.execute( sql);
        }
        con.commit();
    }


    protected void addSubStorage(SubStorage<T> subStore) {
    	subStores.add(subStore);
    }

    public Collection<SubStorage<T>> getSubStores() {
		return subStores;
	}

    public void setConnection(Connection con) throws SQLException {
		this.con= con;
		for (Storage<T> subStore: subStores) {
		    subStore.setConnection(con);
		}
		if ( con != null)
		{
			String databaseProductName = con.getMetaData().getDatabaseProductName();
			if ( databaseProductName != null)
			{
				Locale locale = Locale.ENGLISH;
				dbProductName = databaseProductName.toLowerCase(locale);
			}
		}
    }

    public Locale getLocale() {
    	return raplaLocale.getLocale();
    }

    protected String getEntryList(Collection<ColumnDef> entries) {
		StringBuffer buf = new StringBuffer();
		for (ColumnDef col: entries) {
		    if (buf.length() > 0 )
		    {
		    	buf.append(", ");
		    }
		    buf.append(col.getName());
		}
		return buf.toString();
    }

    protected String getMarkerList(int length) {
		StringBuffer buf = new StringBuffer();
		for (int i=0;i<length; i++) {
		    buf.append('?');
		    if (i < length - 1)
		    {
		    	buf.append(',');
		    }
		}
		return buf.toString();
    }
    protected String getUpdateList(Collection<ColumnDef> entries) {
		StringBuffer buf = new StringBuffer();
		for (ColumnDef col: entries) {
			if (buf.length() > 0 )
		    {
		    	buf.append(", ");
		    }
			buf.append(col.getName());
		    buf.append("=? ");
		}
		return buf.toString();
    }

    public static void executeBatchedStatement(Connection con,String sql) throws SQLException {
        try (Statement stmt= con.createStatement()){
		    StringTokenizer tokenizer = new StringTokenizer(sql,";");
		    while (tokenizer.hasMoreTokens())
		        stmt.executeUpdate(tokenizer.nextToken());
        }
    }

    public boolean has(String id)
    {
        try ( PreparedStatement stmt = con.prepareStatement(containsSql))
        {

            stmt.setString(1, id);
            stmt.execute();
            final ResultSet resultSet = stmt.getResultSet();
            return resultSet != null && resultSet.next() && resultSet.getInt(1) == 1;
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
        deleteEntities( entities );
        insert( entities );
    }

    public void deleteEntities(Iterable<T> entities) throws SQLException, RaplaException {
        Set<String> ids = new HashSet<String>();
        for ( T entity: entities)
        {
        	ids.add( entity.getId());
        }
        if(ids.isEmpty())
        {
            return;
        }
        deleteFromSubStores(ids);
        if(hasLastChangedTimestamp)
        {
            PreparedStatement stmt = null;
            try
            {
                stmt = con.prepareStatement(deleteSql);
                boolean commitNeeded = false;
                for (T entity : entities)
                {
                    final Timestamp castedEntity = (Timestamp)entity;
                    if(has(entity.getId()))
                    {
                        stmt.setString(1, entity.getId());
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

    protected void deleteFromSubStores(Set<String> ids) throws SQLException
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

    public RaplaNonValidatedInput getReader() throws RaplaException {
        return lookup( RaplaNonValidatedInput.class);

    }

    public RaplaXMLReader getReaderFor( RaplaType type) throws RaplaException {
		Map<RaplaType,RaplaXMLReader> readerMap = lookup( PreferenceReader.READERMAP);
        return readerMap.get( type);
    }

    public RaplaXMLWriter getWriterFor( RaplaType type) throws RaplaException {
		Map<RaplaType,RaplaXMLWriter> writerMap = lookup( PreferenceWriter.WRITERMAP );
        return writerMap.get( type);
    }

    protected <S> S lookup( TypedComponentRole<S> role) throws RaplaException {
        try {
            return context.lookup( role);
        } catch (RaplaXMLContextException e) {
            throw new RaplaException( e);
        }
    }

    protected <S> S lookup( Class<S> role) throws RaplaException {
        try {
            return context.lookup( role);
        } catch (RaplaXMLContextException e) {
            throw new RaplaException( e);
        }

    }

    protected void put( Entity entity )
    {
		entityStore.put(entity);
    }

    protected EntityResolver getResolver()
    {
        return entityStore;
    }

    protected void putPassword( String userId, String password )
    {
        entityStore.putPassword( userId, password);
    }

    protected DynamicType getDynamicType( String typeKey )
    {
        return entityStore.getDynamicType( typeKey);
    }

    protected Category getSuperCategory()
    {
        if ( cache != null)
        {
            return cache.getSuperCategory();
        }
        return entityStore.getSuperCategory();
    }

    protected void setText(PreparedStatement stmt, int columIndex, String xml)
			throws SQLException {
    	if (  isHsqldb() || isH2())
		{
		    if (xml != null)
		    {
		    	Clob clob = con.createClob();
		    	clob.setString(1, xml);
		    	stmt.setClob( columIndex, clob);
		    }
		    else
		    {
		    	stmt.setObject( columIndex,  null, Types.CLOB);
		    }
		}
		else
		{
			stmt.setString( columIndex, xml);
		}
	}

	protected String getText(ResultSet rset, int columnIndex)
			throws SQLException {
		String xml = null;
		if ( isMysql())
		{
			Clob clob = rset.getClob( columnIndex );
			if ( clob!= null)
			{
				int length = (int)clob.length();
				if ( length > 0)
				{
					xml = clob.getSubString(1, length);
			      //  String xml = rset.getString( 4);
				}
			}
		}
		else
		{
			xml = rset.getString(columnIndex);
		}
		return xml;
	}
}




