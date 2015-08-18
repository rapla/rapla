package org.rapla.components.i18n;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class LocalePackage
{

    private I18nLocaleFormats formats;
    private Map<String, Map<String, String>> bundles;
    private Set<String> availableLanguages;

    public LocalePackage()
    {
    }

    public LocalePackage(I18nLocaleFormats formats, Map<String, Map<String, String>> bundles, Set<String> availableLanguages)
    {
        this.formats = formats;
        this.bundles = bundles;
        this.availableLanguages = availableLanguages;
    }

    public I18nLocaleFormats getFormats()
    {
        return formats;
    }

    public Map<String, Map<String, String>> getBundles()
    {
        return bundles;
    }

    public Collection<String> getAvailableLanguages() {
        return availableLanguages;
    }
}
