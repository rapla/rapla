package org.rapla.plugin.tableview.internal.gwt;


import jsinterop.annotations.JsType;

@JsType
public interface VueTableColumn {
    String getName();
    TableColumnType getType();
    boolean isVisibleOnMobile();
}
