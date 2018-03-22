package org.rapla.plugin.autoexport;

import org.jetbrains.annotations.PropertyKey;
import org.rapla.components.i18n.AbstractBundle;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.i18n.I18nBundle;
import org.rapla.inject.Extension;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@Extension(provides = I18nBundle.class, id = AutoExportPlugin.PLUGIN_ID)
public class AutoExportResources extends AbstractBundle
{
    private static final String BUNDLENAME = AutoExportPlugin.PLUGIN_ID +  ".AutoExportResources";
        @Inject
        public AutoExportResources(BundleManager loader)
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
