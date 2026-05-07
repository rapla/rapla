package org.rapla.plugin.tableview;


public interface RaplaTableColumn<T> {
  
  String getColumnName();
  
  Object getValue(T object, String contextAnnotationName);
  
  Class<?> getColumnClass();
  
  TableColumnType getType();
  
  String getHtmlValue(T object);


}