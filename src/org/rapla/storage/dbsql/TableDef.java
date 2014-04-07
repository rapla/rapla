package org.rapla.storage.dbsql;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class TableDef
{

	public TableDef(String table) 
	{
		this.tablename = table;
	}
	Map<String,ColumnDef> columns = new LinkedHashMap<String,ColumnDef>();
	String tablename;
	public void addColumn(ColumnDef column)
	{
		columns.put( column.getName(), column);
	}
	
	public TableDef(String tablename, Collection<ColumnDef> columns) {
		this.tablename = tablename;
		for ( ColumnDef def: columns){
			addColumn( def);
		}
	}

	
	public ColumnDef getColumn(String name)
	{
		ColumnDef columnDef = columns.get( name.toUpperCase(Locale.ENGLISH));
		return columnDef;
	}
	
	public String toString() {
		return "TableDef [columns=" + columns + ", tablename=" + tablename	+ "]";
	}

	public void removeColumn(String oldColumnName) 
	{
		columns.remove( oldColumnName);
	}
	
}