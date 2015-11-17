package org.rapla.plugin.compactweekview;

import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.framework.TypedComponentRole;

/**
 * Created by Christopher on 22.09.2015.
 */
public interface CompactWeekviewPlugin
{
    String COMPACT_WEEK_VIEW = "week_compact";
    public static final TypedComponentRole<RaplaConfiguration> CONFIG = new TypedComponentRole<RaplaConfiguration>(COMPACT_WEEK_VIEW + ".config");
}
