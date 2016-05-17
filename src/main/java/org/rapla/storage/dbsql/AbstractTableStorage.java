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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.rapla.components.util.DateTools;
import org.rapla.framework.RaplaException;
import org.rapla.logger.Logger;
import org.rapla.server.internal.TimeZoneConverterImpl;

public class AbstractTableStorage implements TableStorage
{
	/** first paramter is 1 */
    protected final String tableName;
	protected final boolean checkLastChanged;
	protected Connection con;
	protected Logger logger;
	private String dbProductName = "";
	private Map<String,ColumnDef> columns = new LinkedHashMap<String,ColumnDef>();
	protected String insertSql;
	protected String deleteSql;
	protected String selectSql;
	protected String deleteAllSql;
	protected String containsSql;
	protected String selectUpdateSql;
	protected String idName;
	private Calendar datetimeCal;
	private Date connectionTimestamp;


	public AbstractTableStorage(String table, Logger logger, String[] entries,boolean checkLastChanged)
	{
		tableName = table;
		this.logger = logger;
		for ( String unparsedEntry: entries)
		{
			ColumnDef col = new ColumnDef(unparsedEntry);
			columns.put( col.getName(), col);
		}

		this.checkLastChanged = checkLastChanged;//
		datetimeCal =Calendar.getInstance( getSystemTimeZone());
		createSQL(columns.values());
		if (getLogger().isDebugEnabled()) {
			getLogger().debug(insertSql);
			getLogger().debug(deleteSql);
			getLogger().debug(selectSql);
			getLogger().debug(deleteAllSql);
			getLogger().debug(containsSql);
		}
	}

	public static void executeBatchedStatement(Connection con,String sql) throws SQLException {
        try (Statement stmt= con.createStatement()){
		    StringTokenizer tokenizer = new StringTokenizer(sql,";");
		    while (tokenizer.hasMoreTokens())
		        stmt.executeUpdate(tokenizer.nextToken());
        }
    }

	@Override public void deleteAll() throws SQLException
	{
		executeBatchedStatement(con, deleteAllSql);
	}

	protected Logger getLogger() {
        return logger;
    }

	protected ColumnDef getColumn(String name)
    {
    	return columns.get( name);
    }

	public void removeConnection()
	{
		con = null;
	}

	public Date getConnectionTimestamp()
	{
		return connectionTimestamp;
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
			String newColumnName) throws SQLException
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

	//CREATE INDEX KEY_ALLOCATION_APPOINTMENT ON ALLOCATION(APPOINTMENT_ID);
	protected String createKeySQL(String table, String colName) {
		return "create index KEY_"+ table + "_" + colName + " on " + table + "(" + colName +")";
	}

	public void createOrUpdateIfNecessary( Map<String,TableDef> schema) throws SQLException, RaplaException
    {
    	String tablename = tableName;
		final List<String> createSQL1 = getCreateSQL();
		if (schema.get (tablename) != null )
    	{
    		return;
    	}
        getLogger().info("Creating table " + tablename);
		for (String createSQL : createSQL1)
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

	// Always use gmt for storing timestamps
	protected Date getTimestampOrNow(ResultSet rset, int column) throws SQLException {
	    Date currentTimestamp = getConnectionTimestamp();
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
		        getLogger().error("Timestamp in table " + getTableName() + " in the future. Ignoring.");
		    }
		    else
		    {
		        return date;
		    }
		}
        return currentTimestamp;
	}

	protected Date getTimestamp(ResultSet rset, int column) throws SQLException {
        Date currentTimestamp = getConnectionTimestamp();
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
                getLogger().error("Timestamp in table " + getTableName() + " in the future. Something went wrong");
            }
            else
            {
                return date;
            }
        }
        return null;
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

	public void setConnection(Connection con, Date connectionTimestamp) throws SQLException
	{
		this.connectionTimestamp = connectionTimestamp;
		this.con = con;
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

	protected void createSQL(Collection<ColumnDef> entries) {

        idName = entries.iterator().next().getName();
        String table = tableName;
		selectSql = "select " + getEntryList(entries) + " from " + table ;
        containsSql = "select count(" + idName + ") from " + table + " where " + idName + "= ?";
        deleteSql = "delete from " + table + " where " + idName + "= ?" + (checkLastChanged ? " AND LAST_CHANGED = ?" : "");
		selectUpdateSql = "SELECT " + getEntryList(entries) + " from " + tableName + " where " + idName + " = ?";
		String valueString = " (" + getEntryList(entries) + ")";
		insertSql = "insert into " + table + valueString + " values (" + getMarkerList(entries.size()) + ")";
		deleteAllSql = "delete from " + table;
		//searchForIdSql = "select id from " + table + " where id = ?";
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

	protected Date getDate( ResultSet rset,int column) throws SQLException
	{

		java.sql.Timestamp timestamp = rset.getTimestamp( column, datetimeCal);
		if (rset.wasNull() || timestamp == null)
		{
			return null;
		}
		long time = timestamp.getTime();
		TimeZone systemTimeZone = getSystemTimeZone();
		long offset = TimeZoneConverterImpl.getOffset(DateTools.getTimeZone(), systemTimeZone, time);
		Date returned = new Date(time + offset);
		return returned;
	}

	public String getTableName() {
        return tableName;
    }

	protected TimeZone getSystemTimeZone() {
		return TimeZone.getDefault();
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

	public void dropTable() throws SQLException
    {
        getLogger().info("Dropping table " + getTableName());
        String sql = "DROP TABLE " + getTableName();
        try (Statement stmt = con.createStatement())
        {
            stmt.execute( sql);
        }
        con.commit();
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
}
