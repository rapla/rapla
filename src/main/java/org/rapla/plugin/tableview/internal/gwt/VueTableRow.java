package org.rapla.plugin.tableview.internal.gwt;

import jsinterop.annotations.JsType;

@JsType
public interface VueTableRow {
    Object getId(); // RaplaObject?
    Object[] getData();
}
