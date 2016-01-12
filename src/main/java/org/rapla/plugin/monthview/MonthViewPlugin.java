package org.rapla.plugin.monthview;

import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.framework.TypedComponentRole;

/**
 * Created by Christopher on 22.09.2015.
 */
public interface MonthViewPlugin
{
    String MONTH_VIEW = "month";
    TypedComponentRole<RaplaConfiguration> CONFIG = new TypedComponentRole<RaplaConfiguration>(MONTH_VIEW + ".config");
}
