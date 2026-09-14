package org.rapla.plugin.dayresource;

import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.framework.TypedComponentRole;

/**
 * Created by Christopher on 22.09.2015.
 */
public interface DayResourcePlugin
{
    String DAY_RESOURCE_VIEW = "day_resource";
    TypedComponentRole<RaplaConfiguration> CONFIG = new TypedComponentRole<>(DAY_RESOURCE_VIEW + ".config");
}
