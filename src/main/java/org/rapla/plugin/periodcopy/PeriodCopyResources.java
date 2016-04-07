package org.rapla.plugin.periodcopy;

import javax.inject.Inject;

import org.jetbrains.annotations.PropertyKey;
import org.rapla.components.i18n.AbstractBundle;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.inject.Extension;

@Extension(provides = I18nBundle.class, id = PeriodCopyResources.PLUGIN_ID)
public class PeriodCopyResources extends AbstractBundle
{
    public static final String PLUGIN_ID ="org.rapla.plugin.periodcopy";
    private static final String BUNDLENAME = PLUGIN_ID + ".PeriodCopyResources";

    @Inject
    public PeriodCopyResources( BundleManager bundleManager)
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
