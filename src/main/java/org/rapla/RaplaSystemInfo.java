package org.rapla;

import org.jetbrains.annotations.PropertyKey;
import org.rapla.components.i18n.AbstractBundle;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.i18n.I18nBundle;
import org.rapla.inject.Extension;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Locale;

@Extension(provides = I18nBundle.class, id = RaplaSystemInfo.BUNDLENAME)
@Singleton
public class RaplaSystemInfo extends AbstractBundle
{
    public static final String BUNDLENAME = "org.rapla.RaplaSystemInfo";
    @Inject
    public RaplaSystemInfo(BundleManager bundleManager)
    {
        super(BUNDLENAME, bundleManager);
    }
    @Override
    public String getString(@PropertyKey(resourceBundle = BUNDLENAME) String key)
    {
        return super.getString(key);
    }

    @Override
    public String getString(@PropertyKey(resourceBundle = BUNDLENAME) String key,Locale locale)
    {
        return super.getString(key, locale);
    }

    @Override
    public String format(@PropertyKey(resourceBundle = BUNDLENAME) String key, Object... obj)
    {
        return super.format(key, obj);
    }

    // custom format for info text
    public String infoText(String javaversion) {
        String signed = "x";
        return format("info.text",signed,javaversion );
    }
}
