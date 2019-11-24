package org.rapla.plugin.tableview;

import jsinterop.annotations.JsType;

@JsType
public interface RaplaTableColumn<T> {
  
  String getColumnName();
  
  Object getValue(T object);
  
  Class<?> getColumnClass();
  
  TableColumnType getType();
  
  String getHtmlValue(T object);
  
}