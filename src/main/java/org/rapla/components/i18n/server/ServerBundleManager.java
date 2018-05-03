package org.rapla.components.i18n.server;

import org.rapla.RaplaResources;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.i18n.I18nIconURL;
import org.rapla.components.i18n.internal.AbstractBundleManager;
import org.rapla.components.i18n.I18nIcon;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@DefaultImplementation(of=BundleManager.class,context = { InjectionContext.server})
@Singleton
public class ServerBundleManager extends AbstractBundleManager {
    final Map<String, Set<String>> countriesForLanguage;
    @Inject
    public ServerBundleManager()
    {
        this.countriesForLanguage = loadAvailableCountries();
    }
    @Override
    public I18nIcon getIcon(String packageId, String key) {
        String location = getString(packageId,key);
        return new I18nIconURL(key,location);
    }

    public Map<String, Set<String>> getCountriesForLanguage(Set<String> languages)
    {
        final LinkedHashMap<String, Set<String>> result = new LinkedHashMap<>();
        if (languages != null)
        {
            for (String language : languages)
            {
                final Set<String> countries = countriesForLanguage.get(language);
                if (countries != null)
                {
                    result.put(language, countries);
                }
            }
        }
        return result;
    }

    private Map<String, Set<String>> loadAvailableCountries()
    {
        LinkedHashMap<String, Set<String>> countriesForLanguage= new LinkedHashMap<>();
        {
            final Set<String> availableLanguages = getAvailableLanguages();
            for (String language : availableLanguages)
            {
                final LinkedHashSet<String> countries = new LinkedHashSet<>();
                countries.add(language.toUpperCase());
                final String[] isoCountries = Locale.getISOCountries();
                for (String country : isoCountries)
                {
                    final String propertiesFileName = "/org/rapla/components/i18n/internal/locales/format_"+language+"_"+country+".properties";
                    final URL resource = RaplaResources.class.getResource(propertiesFileName);
                    if(resource != null)
                    {
                        countries.add(country.toUpperCase());
                    }
                }
                countriesForLanguage.put(language, Collections.unmodifiableSet(countries));
            }
        }
        return Collections.unmodifiableMap(countriesForLanguage);
    }


}
