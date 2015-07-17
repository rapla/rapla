package org.rapla.storage.dbsql;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

public class ColumnDef
{
	String name;

	boolean key;
	boolean primary;
	boolean notNull;
	String type;
	String defaultValue;
	
	public ColumnDef(String unparsedEntry) {
		// replace all double "  " with single " "
		while (true)
		{
			String replaceAll = unparsedEntry.replaceAll("  ", " ").trim();
			if ( replaceAll.equals( unparsedEntry))
			{
				break;
			}
			unparsedEntry = replaceAll;
		}
		String[] split = unparsedEntry.split(" ");
		name = split[0];
		type = split[1];
		for ( int i=2;i< split.length;i++)
		{
			String s = split[i];
			if ( s.equalsIgnoreCase("KEY"))
			{
				key = true;
			}
			if ( s.equalsIgnoreCase("PRIMARY"))
			{
				primary = true;
			}
			if ( s.equalsIgnoreCase("NOT") && i<split.length -1 && split[i+1].equals("NULL"))
			{
				notNull = true;
			}
			if ( s.equalsIgnoreCase("DEFAULT") && i<split.length -1)
			{
				defaultValue = split[i+1];
			}
		}
	}

	public ColumnDef(ResultSet set) throws SQLException {
		name = set.getString("COLUMN_NAME").toUpperCase(Locale.ENGLISH); 
		type = set.getString("TYPE_NAME").toUpperCase(Locale.ENGLISH);
		int nullableInt = set.getInt( "NULLABLE");
		notNull = nullableInt <=0;
		int charLength = set.getInt("CHAR_OCTET_LENGTH");
		if ( type.equals("VARCHAR") && charLength>0)
		{
			type +="("+charLength+")";
		}
		
		/*
		int nullbable = set.
		COLUMN_NAME String => column name 
				DATA_TYPE int => SQL type from java.sql.Types 
				TYPE_NAME String => Data source dependent type name, for a UDT the type name is fully qualified 
				COLUMN_SIZE int => column size. 
				BUFFER_LENGTH is not used. 
				DECIMAL_DIGITS int => the number of fractional digits. Null is returned for data types where DECIMAL_DIGITS is not applicable. 
				NUM_PREC_RADIX int => Radix (typically either 10 or 2) 
				NULLABLE int => is NULL allowed.
				*/ 
	}

	public String getName() {
		return name;
	}
	
	public boolean isKey() {
		return key;
	}

	public boolean isPrimary() {
		return primary;
	}

	public boolean isNotNull() {
		return notNull;
	}

	public String getType() {
		return type;
	}
	
	public String getDefaultValue() {
		return defaultValue;
	}

	@Override
	public String toString() {
		return "Column [name=" + name + ", key=" + key + ", primary="
				+ primary + ", notNull=" + notNull + ", type=" + type
				+ ", defaultValue=" + defaultValue + "]";
	}

    public boolean isIntType() 
    {
        if ( type == null)
        {
            return false;
        }
        String lowerCase = type.toLowerCase();
        return  (lowerCase.contains("int"));
    }

	
}