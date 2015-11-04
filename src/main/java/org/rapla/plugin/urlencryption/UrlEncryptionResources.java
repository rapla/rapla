package org.rapla.plugin.urlencryption;

import javax.inject.Inject;

import org.jetbrains.annotations.PropertyKey;
import org.rapla.components.i18n.AbstractBundle;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.inject.Extension;

@Extension(provides = I18nBundle.class, id = UrlEncryptionPlugin.PLUGIN_ID)
public class UrlEncryptionResources extends AbstractBundle
{
        private static final String BUNDLENAME = UrlEncryptionPlugin.PLUGIN_ID +  ".UrlEncryptionResources";
        @Inject
        public UrlEncryptionResources(BundleManager loader)
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
