package org.rapla.plugin.tableview;

public interface RaplaTableColumn<T, C> {

	String getColumnName();

	Object getValue(T object);

	void init(C column);

	Class<?> getColumnClass();

	String getHtmlValue(T object);

}