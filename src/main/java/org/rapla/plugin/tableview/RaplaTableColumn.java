package org.rapla.plugin.tableview;

import jsinterop.annotations.JsType;

@JsType
public interface RaplaTableColumn<T, C> {
  
  String getColumnName();
  
  Object getValue(T object);
  
  void init(C column);
  
  Class<?> getColumnClass();
  
  TableColumnType getType();
  
  String getHtmlValue(T object);
  
}