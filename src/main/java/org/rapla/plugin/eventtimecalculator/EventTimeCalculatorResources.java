package org.rapla.plugin.eventtimecalculator;

import java.util.Locale;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.jetbrains.annotations.PropertyKey;
import org.rapla.components.i18n.AbstractBundle;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.inject.Extension;

@Singleton
@Extension(provides = I18nBundle.class, id = EventTimeCalculatorPlugin.PLUGIN_ID) public class EventTimeCalculatorResources extends AbstractBundle
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
        return bundleManager.format(timeFormat, obj);
    }

}
