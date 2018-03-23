package org.rapla.plugin.tableview.internal.gwt;

import jsinterop.annotations.JsType;

@JsType
public interface VueTable {
    VueTableColumn[] getColumns();
    VueTableRow[] getRows();
}
