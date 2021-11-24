package org.rapla.plugin.tableview;

import jsinterop.annotations.JsType;

@JsType
public enum TableColumnType {
    STRING, INTEGER, DATE;
    public String getTypeName() {
        return name();
    }
}
