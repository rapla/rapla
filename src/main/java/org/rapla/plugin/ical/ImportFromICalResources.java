package org.rapla.plugin.ical;

import org.jetbrains.annotations.PropertyKey;
import org.rapla.components.i18n.AbstractBundle;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.inject.Extension;

import javax.inject.Inject;

@Extension(provides = I18nBundle.class, id = ImportFromICalResources.PLUGIN_ID)
public class ImportFromICalResources extends AbstractBundle
{
    public static final String PLUGIN_ID ="org.rapla.plugin.ical";
    private static final String BUNDLENAME = PLUGIN_ID + ".ImportFromICalResources";

    @Inject
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
