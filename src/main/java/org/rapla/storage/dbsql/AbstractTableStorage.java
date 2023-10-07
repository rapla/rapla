package org.rapla.storage.dbsql;

import org.rapla.components.util.IOUtil;
import org.rapla.framework.RaplaException;
import org.rapla.logger.Logger;
import org.rapla.server.internal.TimeZoneConverterImpl;

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

public class AbstractTableStorage implements TableStorage
{
	/** first paramter is 1 */
    protected final String tableName;
	protected final boolean checkLastChanged;
	protected Connection con;
	protected Logger logger;
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
			getLogger().debug(deleteSqlWithoutCheck);
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
		buf.append("CREATE TABLE " + table + " (");
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
    			try (Statement stmt = con.createStatement())
    			{
    				stmt.execute( sql);
    			}
            	con.commit();
            }
			if ( col.isKey() && !col.isPrimary())
			{
				String sql = createKeySQL(tableName, name);
	            getLogger().info("Adding index for " + name);

    			try (Statement stmt = con.createStatement())
    			{
    				stmt.execute( sql);
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
		if ( date.after( currentTimestamp))
		{
			getLogger().error("Timestamp in table " + getTableName() + " in the future. " + date+ " > "+ currentTimestamp +" Ignoring.");
		}
		else
		{
			return date;
		}
	    return currentTimestamp;
	}

	protected Date getTimestamp(ResultSet rset, int column, boolean checkCurrent) throws SQLException {
        Date currentTimestamp = getConnectionTimestamp();
        java.sql.Timestamp timestamp = rset.getTimestamp( column, datetimeCal);
        if (rset.wasNull() || timestamp == null)
        {
            return null;
        }
        Date date = new Date( timestamp.getTime());
		if ( date.after( currentTimestamp) && checkCurrent)
		{
			getLogger().error("Timestamp in table " + getTableName() + " in the future. Something went wrong");
			return null;
		}
		else
		{
			return date;
		}
    }

	protected void setDate(PreparedStatement stmt,int column, Date time) throws SQLException {
    	if ( time != null)
        {
    		TimeZone systemTimeZone = getSystemTimeZone();
    		// same as TimeZoneConverterImpl.fromRaplaTime
    		long offset = TimeZoneConverterImpl.getOffset( IOUtil.getTimeZone(), systemTimeZone, time.getTime());
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

	protected Date getDate( ResultSet rset,int column) throws SQLException
	{

		java.sql.Timestamp timestamp = rset.getTimestamp( column, datetimeCal);
		if (rset.wasNull() || timestamp == null)
		{
			return null;
		}
		long time = timestamp.getTime();
		TimeZone systemTimeZone = getSystemTimeZone();
		long offset = TimeZoneConverterImpl.getOffset(IOUtil.getTimeZone(), systemTimeZone, time);
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
        getLogger().info("Dropping table " + getTableName());
        String sql = "DROP TABLE " + getTableName();
        try (Statement stmt = con.createStatement())
        {
            stmt.execute( sql);
        }
        con.commit();
    }

}
