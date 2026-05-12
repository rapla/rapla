package org.rapla.plugin.tableview;


public interface RaplaTableColumn<T> {

  /**
   * Stable identifier for this column — distinct from the locale-resolved
   * display name. Used by the REST surface (PRD 030 Phase 2) to match
   * request column ids back to columns server-side. Default implementation
   * falls back to the column name for backwards compatibility with
   * implementors that don't carry a separate key; the standard
   * {@code DefaultRaplaTableColumn} overrides this to return the
   * {@code TableColumnConfig} key.
   */
  default String getKey() { return getColumnName(); }

  String getColumnName();

  Object getValue(T object, String contextAnnotationName);

  Class<?> getColumnClass();

  TableColumnType getType();

  String getHtmlValue(T object);


}