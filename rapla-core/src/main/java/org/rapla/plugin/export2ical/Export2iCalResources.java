package org.rapla.plugin.export2ical;

import org.jetbrains.annotations.PropertyKey;
import org.rapla.components.i18n.AbstractBundle;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.i18n.I18nBundle;

import org.springframework.beans.factory.annotation.Autowired;
import jakarta.inject.Singleton;

 
@Singleton
public class Export2iCalResources extends AbstractBundle
{
    public static final String BUNDLENAME = Export2iCalPlugin.PLUGIN_ID + ".Export2iCalResources";

    @Autowired public Export2iCalResources(BundleManager loader)
    {
        super(BUNDLENAME, loader);
    }

    public String getString(@PropertyKey(resourceBundle = BUNDLENAME) String key)
    {
        return super.getString(key);
    }

    public String format(@PropertyKey(resourceBundle = BUNDLENAME) String key, Object... obj)
    {
        return super.format(key, obj);
    }

}
