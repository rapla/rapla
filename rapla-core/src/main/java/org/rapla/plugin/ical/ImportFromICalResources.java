package org.rapla.plugin.ical;

import org.jetbrains.annotations.PropertyKey;
import org.rapla.components.i18n.AbstractBundle;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.i18n.I18nBundle;

import org.springframework.beans.factory.annotation.Autowired;


public class ImportFromICalResources extends AbstractBundle
{
    private static final String BUNDLENAME = ImportFromICalPlugin.PLUGIN_ID + ".ImportFromICalResources";

    @Autowired
    public ImportFromICalResources(BundleManager localeLoader)
    {
        super(BUNDLENAME, localeLoader);
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
