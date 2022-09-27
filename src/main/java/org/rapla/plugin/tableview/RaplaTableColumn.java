package org.rapla.plugin.tableview;


public interface RaplaTableColumn<T> {
  
  String getColumnName();
  
  Object getValue(T object);
  
  Class<?> getColumnClass();
  
  TableColumnType getType();
  
  String getHtmlValue(T object);
  
}