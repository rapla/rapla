package org.rapla.server.internal;

import org.rapla.components.i18n.BundleManager;
import org.rapla.components.i18n.I18nLocaleFormats;
import org.rapla.components.i18n.LocalePackage;
import org.rapla.components.i18n.internal.AbstractBundleManager;
import org.rapla.components.i18n.server.ServerBundleManager;
import org.rapla.components.util.LocaleTools;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.server.RemoteSession;
import org.rapla.storage.RemoteLocaleService;
import org.rapla.storage.StorageOperator;

import org.springframework.beans.factory.annotation.Autowired;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Context;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class RemoteLocaleServiceImpl implements RemoteLocaleService
{
    @Autowired
    ServerBundleManager bundleManager;
    @Autowired
    RaplaLocale raplaLocale;
    @Autowired
    RemoteSession session;
    @Autowired
    Logger logger;
    @Autowired
    StorageOperator operator;
    @Autowired
    ResourceBundleList resourceBundleList;
    private final HttpServletRequest request;

    @Autowired
    public RemoteLocaleServiceImpl(@Context HttpServletRequest request)
    {
        this.request = request;
    }

    @Override
    public LocalePackage locale(String id, String localeString)
    {
        try { return localeSync(id, localeString); }
        catch (RaplaException ex) { throw new RuntimeException(ex); }
    }

    /** Sync sibling for server-internal callers (REST controllers etc.) — same work as
     *  {@link #locale}, raw return + checked exception. */
    public LocalePackage localeSync(String id, String localeString) throws RaplaException
    {
        if (localeString == null)
        {
            if (session.isAuthentified(request))
            {
                final User validUser = session.checkAndGetUser(request);
                final Preferences preferences = operator.getPreferences(validUser, true);
                final String entry = preferences.getEntryAsString(RaplaLocale.LANGUAGE_ENTRY, null);
                if (entry != null)
                {
                    localeString = new Locale(entry).toString();
                }
            }
            if (localeString == null)
            {
                localeString = raplaLocale.getLocale().toString();
            }
        }
        Locale locale = LocaleTools.getLocale(localeString);
        final I18nLocaleFormats formats = bundleManager.getFormats(locale);
        Map<String, Map<String, String>> bundles = resourceBundleList.getBundles(locale);
        String language = locale.getLanguage();
        String country = locale.getCountry();
        Set<String> availableLanguages = bundleManager.getAvailableLanguages();
        return new LocalePackage(formats, language, country, bundles, availableLanguages);
    }

    @Override
    public Map<String, Set<String>> countries(Set<String> languages)
    {
        return countriesSync(languages);
    }

    public Map<String, Set<String>> countriesSync(Set<String> languages)
    {
        return bundleManager.getCountriesForLanguage(languages);
    }
}
