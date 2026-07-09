package org.rapla.storage.dbsql;

import org.rapla.components.util.DateTools;
import org.rapla.components.util.IOUtil;
import org.rapla.framework.RaplaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.rapla.framework.internal.TimeZoneConverterImpl;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.time.LocalDateTime;
public class AbstractTableStorage implements TableStorage
{
	private static final Logger LOGGER = LoggerFactory.getLogger(AbstractTableStorage.class);
	/** first paramter is 1 */
    protected final String tableName;
	protected final boolean checkLastChanged;
	protected Connection con;
	private String dbProductName = "";
	final private Map<String,ColumnDef> columns = new LinkedHashMap<>();
	protected String insertSql;
	protected String deleteSql;
	protected String deleteSqlWithoutCheck;
	protected String selectSql;
	protected String deleteAllSql;
	protected String containsSql;
	protected String selectUpdateSql;
	protected String idName;
	final private Calendar datetimeCal;
	private LocalDateTime connectionTimestamp;


	public AbstractTableStorage(String table, String[] entries,boolean checkLastChanged)
	{
		tableName = table;
		for ( String unparsedEntry: entries)
		{
			ColumnDef col = new ColumnDef(unparsedEntry);
			columns.put( col.getName(), col);
		}

		this.checkLastChanged = checkLastChanged;//
		datetimeCal =Calendar.getInstance( getSystemTimeZone());
		createSQL(columns.values());
		LOGGER.debug(insertSql);
		LOGGER.debug(deleteSql);
		LOGGER.debug(deleteSqlWithoutCheck);
		LOGGER.debug(selectSql);
		LOGGER.debug(deleteAllSql);
		LOGGER.debug(containsSql);
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

	/** Concrete-class slf4j logger — used by subclasses for per-class log routing
	 *  after the PRD 053 Logger-DI removal. */
	protected Logger getLogger()
	{
		return LoggerFactory.getLogger(getClass());
	}

	protected ColumnDef getColumn(String name)
    {
    	return columns.get( name);
    }

	public void removeConnection()
	{
		con = null;
	}

	public LocalDateTime getConnectionTimestamp()
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
		boolean result = dbProductName.contains("mysql") || dbProductName.contains("mariadb");
		return result;
	}

	protected boolean isSQLServer() {
        boolean result = dbProductName.toLowerCase().contains("microsoft");
        return result;
    }

	protected boolean isHsqldb() {
		boolean result = dbProductName.contains("hsql");
		return result;
	}

	protected boolean isPostgres() {
		boolean result = dbProductName.contains("postgres");
		return result;
	}

	protected boolean isH2() {
		boolean result = dbProductName.contains("h2");
		return result;
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
    	List<String> createSQL = new ArrayList<>();
    	StringBuffer buf = new StringBuffer();
    	String table = tableName;
		// HSQLDB defaults CREATE TABLE to a MEMORY table, whose rows are all
		// serialised as SQL INSERTs in the .script file and re-parsed into RAM
		// on every boot — startup then scales with total row count. CACHED
		// tables keep rows in the binary .data file and load lazily. CACHED is
		// HSQLDB-specific syntax, so only emit it for that backend.
		String tableKeyword = isHsqldb() ? "CREATE CACHED TABLE " : "CREATE TABLE ";
		buf.append(tableKeyword + table + " (");
		List<String> keyCreates = new ArrayList<>();
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
        LOGGER.info("Creating table {}", tablename);
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
            LOGGER.warn("Patching Database for table {} adding column {}", tableName, name);
            {
            	String sql = "ALTER TABLE " + tableName + " ADD COLUMN ";
                sql += getColumnCreateStatemet( col, true, true);
    			try (Statement stmt = con.createStatement())
    			{
    				stmt.execute( sql);
    			}
            	con.commit();
            }
			if ( col.isKey() && !col.isPrimary())
			{
				String sql = createKeySQL(tableName, name);
	            LOGGER.info("Adding index for {}", name);

    			try (Statement stmt = con.createStatement())
    			{
    				stmt.execute( sql);
    			}
	            con.commit();
			}
        }
	}

	// Always use gmt for storing timestamps
	protected LocalDateTime getTimestampOrNow(ResultSet rset, int column) throws SQLException {
	    LocalDateTime currentTimestamp = getConnectionTimestamp();
	    java.sql.Timestamp timestamp = rset.getTimestamp( column, datetimeCal);
        if (rset.wasNull() || timestamp == null)
        {
            return currentTimestamp;
        }
        LocalDateTime date = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(timestamp.getTime()), java.time.ZoneOffset.UTC);
		if ( date.isAfter( currentTimestamp))
		{
			LOGGER.error("Timestamp in table {} in the future. {} > {} Ignoring.", getTableName(), date, currentTimestamp);
		}
		else
		{
			return date;
		}
	    return currentTimestamp;
	}

	protected LocalDateTime getTimestamp(ResultSet rset, int column, boolean checkCurrent) throws SQLException {
        LocalDateTime currentTimestamp = getConnectionTimestamp();
        java.sql.Timestamp timestamp = rset.getTimestamp( column, datetimeCal);
        if (rset.wasNull() || timestamp == null)
        {
            return null;
        }
        LocalDateTime date = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(timestamp.getTime()), java.time.ZoneOffset.UTC);
		if ( date.isAfter( currentTimestamp) && checkCurrent)
		{
			LOGGER.error("Timestamp in table {} in the future. Something went wrong", getTableName());
			return null;
		}
		else
		{
			return date;
		}
    }

	protected void setDate(PreparedStatement stmt,int column, LocalDateTime time) throws SQLException {
    	if ( time != null)
        {
    		TimeZone systemTimeZone = getSystemTimeZone();
    		// same as TimeZoneConverterImpl.fromRaplaTime
    		long offset = TimeZoneConverterImpl.getOffset( IOUtil.getTimeZone(), systemTimeZone, DateTools.toMilli(time));
            long timeInMillis = DateTools.toMilli(time) - offset;
			stmt.setTimestamp( column, new java.sql.Timestamp( timeInMillis), datetimeCal);
        }
        else
        {
            stmt.setObject(column, null, Types.TIMESTAMP);
        }
	}

	protected void setTimestamp(PreparedStatement stmt,int column, LocalDateTime time) throws SQLException {
    	if ( time != null)
        {
    		//TimeZone systemTimeZone = getSystemTimeZone();
    		// same as TimeZoneConverterImpl.fromRaplaTime
    		//long offset = TimeZoneConverterImpl.getOffset( DateTools.getTimeZone(), systemTimeZone, time.getTime());
    		long offset = 0;
            long timeInMillis = DateTools.toMilli(time) - offset;
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

	public void setConnection(Connection con, LocalDateTime connectionTimestamp) throws SQLException
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
		deleteSqlWithoutCheck = "delete from " + table + " where " + idName + "= ?";
		selectUpdateSql = "SELECT " + getEntryList(entries) + " from " + tableName + " where " + idName + " = ?";
		String valueString = " (" + getEntryList(entries) + ")";
		insertSql = "insert into " + table + valueString + " values (" + getMarkerList(entries.size()) + ")";
		deleteAllSql = "delete from " + table;
		//searchForIdSql = "select id from " + table + " where id = ?";
	}

	protected String getEntryList(Collection<ColumnDef> entries) {
        StringBuilder buf = new StringBuilder();
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
        StringBuilder buf = new StringBuilder();
        for (int i=0;i<length; i++) {
            buf.append('?');
            if (i < length - 1)
            {
                buf.append(',');
            }
        }
        return buf.toString();
    }

	protected LocalDateTime getDate( ResultSet rset,int column) throws SQLException
	{

		java.sql.Timestamp timestamp = rset.getTimestamp( column, datetimeCal);
		if (rset.wasNull() || timestamp == null)
		{
			return null;
		}
		long time = timestamp.getTime();
		TimeZone systemTimeZone = getSystemTimeZone();
		long offset = TimeZoneConverterImpl.getOffset(IOUtil.getTimeZone(), systemTimeZone, time);
		LocalDateTime returned = DateTools.toLocalDateTime(time + offset);
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
            stmt.setInt( column, number );
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
        if (rset.wasNull())
        {
            return null;
        }
        return value;
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
        if (  isHsqldb() || isH2() )
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
        LOGGER.info("Dropping table {}", getTableName());
        String sql = "DROP TABLE " + getTableName();
        try (Statement stmt = con.createStatement())
        {
            stmt.execute( sql);
        }
        con.commit();
    }

}
