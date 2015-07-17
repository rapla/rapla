package org.rapla.plugin.tableview;

import javax.swing.table.TableColumn;

public interface RaplaTableColumn<T> {

	public abstract String getColumnName();

	public abstract Object getValue(T object);

	public abstract void init(TableColumn column);

	public abstract Class<?> getColumnClass();

	public abstract String getHtmlValue(T object);

}