package org.rapla.plugin.urlencryption;

import org.jetbrains.annotations.PropertyKey;
import org.rapla.components.i18n.AbstractBundle;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.i18n.I18nBundle;

import org.springframework.beans.factory.annotation.Autowired;


public class UrlEncryptionResources extends AbstractBundle
{
        private static final String BUNDLENAME = UrlEncryptionPlugin.PLUGIN_ID +  ".UrlEncryptionResources";
        @Autowired
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
