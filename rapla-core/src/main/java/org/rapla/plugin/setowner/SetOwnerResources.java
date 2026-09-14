package org.rapla.plugin.setowner;

import org.jetbrains.annotations.PropertyKey;
import org.rapla.components.i18n.AbstractBundle;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.i18n.I18nBundle;

import org.springframework.beans.factory.annotation.Autowired;


public class SetOwnerResources extends AbstractBundle
{
    public static final String ID = "org.rapla.plugin.setowner";
    public static final String BUNDLENAME = ID + ".SetOwnerResources";

    @Autowired
    public SetOwnerResources(BundleManager bundleManager)
    {
        super(BUNDLENAME, bundleManager);
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
