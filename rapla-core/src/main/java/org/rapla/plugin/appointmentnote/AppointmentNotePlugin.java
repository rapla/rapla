package org.rapla.plugin.appointmentnote;

import org.rapla.framework.TypedComponentRole;

public interface AppointmentNotePlugin {
    String PLUGIN_ID = "org.rapla.plugin.appointmentnote";
    TypedComponentRole<Boolean> ENABLED = new TypedComponentRole<>(PLUGIN_ID + "." + "enabled");
}
