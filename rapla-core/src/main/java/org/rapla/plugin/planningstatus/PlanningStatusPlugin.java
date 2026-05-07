package org.rapla.plugin.planningstatus;

import org.rapla.framework.TypedComponentRole;

public class PlanningStatusPlugin {
    public static final String PLUGIN_ID = "org.rapla.plugin.planningstatus";
    public static final boolean ENABLE_BY_DEFAULT = false;
    public static final String PLANNINGSTATUS_CONDITION_ANNOTATION_NAME ="eventstatus_condition";
    public static final TypedComponentRole<Boolean> ENABLED = new TypedComponentRole<>(PLUGIN_ID + "." + "enabled");

    public static final String PUBLISH_NON_PLANNED = PLUGIN_ID + "." + "publishNonPlanned";
}
