package org.rapla.plugin.compactweekview;

import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.framework.TypedComponentRole;

public interface CompactWeekviewPlugin
{
    String COMPACT_WEEK_VIEW = "week_compact";
    TypedComponentRole<RaplaConfiguration> CONFIG = new TypedComponentRole<RaplaConfiguration>(COMPACT_WEEK_VIEW + ".config");
}
