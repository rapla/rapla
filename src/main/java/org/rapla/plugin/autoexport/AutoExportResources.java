package org.rapla.plugin.autoexport;

import org.jetbrains.annotations.PropertyKey;
import org.rapla.components.i18n.AbstractBundle;
import org.rapla.components.i18n.BundleManager;

import javax.inject.Inject;

public class AutoExportResources extends AbstractBundle
{
        public static final String ID = "org.rapla.plugin.autoexport.AutoExportResources";
        @Inject
        public AutoExportResources(BundleManager loader)
        {
            super(ID, loader);
        }
        public String getString(@PropertyKey(resourceBundle = ID) String key)
        {
            return super.getString(key);
        }

        public String format(@PropertyKey(resourceBundle = ID) String key, Object... obj)
        {
            return super.format(key, obj);
        }

}
