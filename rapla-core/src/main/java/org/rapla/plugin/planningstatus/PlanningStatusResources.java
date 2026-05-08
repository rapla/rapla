package org.rapla.plugin.planningstatus;

import org.jetbrains.annotations.PropertyKey;
import org.rapla.components.i18n.AbstractBundle;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.i18n.I18nBundle;
import org.rapla.plugin.eventtimecalculator.EventTimeCalculatorPlugin;

import org.springframework.beans.factory.annotation.Autowired;
import jakarta.inject.Singleton;
import java.util.Locale;

@Singleton
 public class PlanningStatusResources extends AbstractBundle
{
    public static final String BUNDLENAME = PlanningStatusPlugin.PLUGIN_ID + ".PlanningStatusResources";

    @Autowired public PlanningStatusResources(BundleManager loader)
    {
        super(BUNDLENAME, loader);
    }

    public String getString(@PropertyKey(resourceBundle = BUNDLENAME) String key)
    {
        return super.getString(key);
    }

    public String getString(@PropertyKey(resourceBundle = BUNDLENAME) String key, Locale locale)
    {
        return super.getString(key, locale);
    }

    public String format(@PropertyKey(resourceBundle = BUNDLENAME) String key, Object... obj)
    {
        return super.format(key, obj);
    }
    
    public String formatTime(String timeFormat, Object... obj)
    {
        return replaceArgs(timeFormat, obj);
    }

}
