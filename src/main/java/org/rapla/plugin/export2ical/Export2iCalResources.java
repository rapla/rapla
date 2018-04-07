package org.rapla.plugin.export2ical;

import org.jetbrains.annotations.PropertyKey;
import org.rapla.components.i18n.AbstractBundle;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.i18n.I18nBundle;
import org.rapla.inject.Extension;

import javax.inject.Inject;
import javax.inject.Singleton;

@Extension(provides = I18nBundle.class, id = Export2iCalPlugin.PLUGIN_ID) 
@Singleton
public class Export2iCalResources extends AbstractBundle
{
    public static final String BUNDLENAME = Export2iCalPlugin.PLUGIN_ID + ".Export2iCalResources";

    @Inject public Export2iCalResources(BundleManager loader)
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
