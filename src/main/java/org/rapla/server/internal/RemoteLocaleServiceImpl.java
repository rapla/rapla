package org.rapla.server.internal;

import org.rapla.components.i18n.AbstractBundle;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.i18n.I18nLocaleFormats;
import org.rapla.components.i18n.LocalePackage;
import org.rapla.components.i18n.server.ServerBundleManager;
import org.rapla.components.util.LocaleTools;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.logger.Logger;
import org.rapla.gwtjsonrpc.common.FutureResult;
import org.rapla.gwtjsonrpc.common.ResultImpl;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.server.RemoteSession;
import org.rapla.storage.RemoteLocaleService;
import org.rapla.storage.StorageOperator;

import javax.inject.Inject;
import java.util.*;

@DefaultImplementation(of = RemoteLocaleService.class, context = InjectionContext.server)
public class RemoteLocaleServiceImpl implements RemoteLocaleService
{
    private final ServerBundleManager bundleManager;
    private final RaplaLocale raplaLocale;
    private final RemoteSession session;
    private final Logger logger;
    private final StorageOperator operator;
    ResourceBundleList resourceBundleList;
    @Inject
    public RemoteLocaleServiceImpl(BundleManager bundleManager, RaplaLocale raplaLocale, RemoteSession session, Logger logger, StorageOperator operator,ResourceBundleList resourceBundleList)
    {
        this.resourceBundleList = resourceBundleList;
        // bad
        this.bundleManager = (ServerBundleManager) bundleManager;
        this.raplaLocale = raplaLocale;
        this.operator = operator;
        this.session = session;
        this.logger = logger;
    }

    private Map<String, Map<String, String>> getBundles(Locale locale)
    {
        Map<String, Map<String, String>> bundles = new LinkedHashMap<String, Map<String, String>>();
        for (String packageId : resourceBundleList.getBundleIds())
        {
            final Collection<String> keys = bundleManager.getKeys(packageId);
            final LinkedHashMap<String, String> raplaResourceIdMap = new LinkedHashMap<String, String>();
            bundles.put(packageId, raplaResourceIdMap);
            for (String key : keys)
            {
                raplaResourceIdMap.put(key, bundleManager.getString(packageId, key, locale));
            }
        }
        return bundles;
    }


    @Override public FutureResult<LocalePackage> locale(String id, String localeString)
    {
        try
        {
            if (localeString == null)
            {
                final User validUser = session.getUser();
                if (validUser != null)
                {
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
            Map<String, Map<String, String>> bundles = getBundles(locale);
            String language = locale.getLanguage();
            String country = locale.getCountry();
            Set<String> availableLanguages = bundleManager.getAvailableLanguages();
            final LocalePackage localePackage = new LocalePackage(formats, language, country, bundles, availableLanguages);
            return new ResultImpl<LocalePackage>(localePackage);
        }
        catch (Exception e1)
        {
            logger.error("No locales found", e1);
            return new ResultImpl<LocalePackage>(e1);
        }
    }

    @Override public FutureResult<Map<String, Set<String>>> countries(Set<String> languages)
    {
        Map<String, Set<String>> result = bundleManager.getCountriesForLanguage(languages);
        return new ResultImpl<Map<String, Set<String>>>(result);
    }
}
