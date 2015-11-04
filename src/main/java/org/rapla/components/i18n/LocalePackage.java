package org.rapla.components.i18n;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class LocalePackage
{

    private I18nLocaleFormats formats;
    private Map<String, Map<String, String>> bundles;
    private Set<String> availableLanguages;
    private String language;
    private String country;

    public LocalePackage()
    {
    }

    public LocalePackage(I18nLocaleFormats formats,String language, String country,Map<String, Map<String, String>> bundles, Set<String> availableLanguages)
    {
        this.language = language;
        this.country = country;
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

    public String getLanguage()
    {
        return language;
    }

    public String getCountry()
    {
        return country;
    }
}
