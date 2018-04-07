package org.rapla.plugin.eventtimecalculator;

import org.jetbrains.annotations.PropertyKey;
import org.rapla.components.i18n.AbstractBundle;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.i18n.I18nBundle;
import org.rapla.inject.Extension;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Locale;

@Singleton
@Extension(provides = I18nBundle.class, id = EventTimeCalculatorResources.BUNDLENAME) public class EventTimeCalculatorResources extends AbstractBundle
{
    public static final String BUNDLENAME = EventTimeCalculatorPlugin.PLUGIN_ID + ".EventTimeCalculatorResources";

    @Inject public EventTimeCalculatorResources(BundleManager loader)
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
