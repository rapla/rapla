package org.rapla.plugin.tableview;


public enum TableColumnType {
    STRING, INTEGER, DATE;
    public String getTypeName() {
        return name();
    }
}
