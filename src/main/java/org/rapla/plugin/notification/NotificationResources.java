package org.rapla.plugin.notification;

import javax.inject.Inject;

import org.jetbrains.annotations.PropertyKey;
import org.rapla.components.i18n.AbstractBundle;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.inject.Extension;

@Extension(provides = I18nBundle.class, id = NotificationPlugin.PLUGIN_ID)
public class NotificationResources extends AbstractBundle
{
    private static final String BUNDLENAME = NotificationPlugin.PLUGIN_ID + ".NotificationResources";


    @Inject
    public NotificationResources(BundleManager localeLoader)
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
