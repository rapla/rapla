package org.rapla.plugin.tableview;

import org.rapla.entities.User;

public interface RaplaTableColumn<T, C> {

	String getColumnName();

	Object getValue(T object);

	void init(C column);

	Class<?> getColumnClass();

	String getHtmlValue(T object);

}