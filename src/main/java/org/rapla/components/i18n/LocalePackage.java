package org.rapla.components.i18n;

import java.util.Map;

public class LocalePackage
{

    private I18nLocaleFormats formats;
    private Map<String, Map<String, String>> bundles;

    public LocalePackage()
    {
    }

    public LocalePackage(I18nLocaleFormats formats, Map<String, Map<String, String>> bundles)
    {
        this.formats = formats;
        this.bundles = bundles;
    }

    public I18nLocaleFormats getFormats()
    {
        return formats;
    }

    public Map<String, Map<String, String>> getBundles()
    {
        return bundles;
    }

}
