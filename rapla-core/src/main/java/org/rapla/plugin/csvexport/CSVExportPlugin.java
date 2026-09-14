package org.rapla.plugin.csvexport;

import org.rapla.framework.TypedComponentRole;

public class CSVExportPlugin {
    public static final String PLUGIN_ID = "org.rapla.plugin.cssexport";
    public static final TypedComponentRole<Boolean> ENABLED = new TypedComponentRole<>(PLUGIN_ID + "." + "enabled");

}
