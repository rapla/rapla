package org.rapla.plugin.resourcerequest;

import org.jetbrains.annotations.PropertyKey;
import org.rapla.components.i18n.AbstractBundle;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.i18n.I18nBundle;
import org.rapla.inject.Extension;
import org.rapla.plugin.notification.NotificationPlugin;

import javax.inject.Inject;

@Extension(provides = I18nBundle.class, id = NotificationPlugin.PLUGIN_ID)
public class ResourceRequestResources extends AbstractBundle
{
    private static final String BUNDLENAME = ResourceRequestPlugin.PLUGIN_ID + ".ResourceRequestResources";


    @Inject
    public ResourceRequestResources(BundleManager localeLoader)
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
