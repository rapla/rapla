package org.rapla;

import org.jetbrains.annotations.PropertyKey;
import org.rapla.components.i18n.AbstractBundle;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.i18n.I18nBundle;

import org.springframework.beans.factory.annotation.Autowired;
import java.util.Locale;


public class RaplaSystemInfo extends AbstractBundle
{
    public static final String BUNDLENAME = "org.rapla.RaplaSystemInfo";
    private String version;
    @Autowired
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

    public void setVersion(String version)
    {
        this.version = version;
    }

    // custom format for info text
    public String infoText(String javaversion) {
        String signed = "";
        String shown = version == null || version.isBlank() ? getString("rapla.version") : version;
        return format("info.text",signed,javaversion, shown);
    }
}
