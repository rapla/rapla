package org.rapla.server.internal;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;

import org.rapla.components.i18n.BundleManager;
import org.rapla.components.i18n.I18nLocaleFormats;
import org.rapla.components.i18n.LocalePackage;
import org.rapla.components.i18n.internal.DefaultBundleManager;
import org.rapla.components.util.LocaleTools;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.logger.Logger;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.server.RemoteSession;
import org.rapla.storage.RemoteLocaleService;
import org.rapla.storage.StorageOperator;

@DefaultImplementation(context = InjectionContext.server, of = RemoteLocaleService.class)
public class RemoteLocaleServiceImpl implements RemoteLocaleService
{
    @Inject
    BundleManager bundleManager;
    @Inject
    RaplaLocale raplaLocale;
    @Inject
    RemoteSession session;
    @Inject
    Logger logger;
    @Inject
    StorageOperator operator;
    @Inject
    ResourceBundleList resourceBundleList;
    private final HttpServletRequest request;

    @Inject
    public RemoteLocaleServiceImpl(@Context HttpServletRequest request)
    {
        this.request = request;
    }

    @Override
    public FutureResult<LocalePackage> locale(String id, String localeString)
    {
        try
        {
            if (localeString == null)
            {
                if (session.isAuthentified(request))
                {
                    final User validUser = session.getUser(request);
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
            final DefaultBundleManager defBundleManager = (DefaultBundleManager) bundleManager;
            final I18nLocaleFormats formats = defBundleManager.getFormats(locale);
            Map<String, Map<String, String>> bundles = resourceBundleList.getBundles(locale);
            String language = locale.getLanguage();
            String country = locale.getCountry();
            Set<String> availableLanguages = defBundleManager.getAvailableLanguages();
            final LocalePackage localePackage = new LocalePackage(formats, language, country, bundles, availableLanguages);
            return new ResultImpl<LocalePackage>(localePackage);
        }
        catch (Exception e1)
        {
            logger.error("No locales found", e1);
            return new ResultImpl<LocalePackage>(e1);
        }
    }

    @Override
    public FutureResult<Map<String, Set<String>>> countries(Set<String> languages)
    {
        Map<String, Set<String>> result = ((DefaultBundleManager) bundleManager).getCountriesForLanguage(languages);
        return new ResultImpl<Map<String, Set<String>>>(result);
    }
}
