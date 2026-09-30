package org.rapla.server.spring.web;

import jakarta.servlet.http.HttpServletRequest;
import org.rapla.components.i18n.I18nLocaleFormats;
import org.rapla.components.i18n.LocalePackage;
import org.rapla.components.i18n.server.ServerBundleManager;
import org.rapla.components.util.LocaleTools;
import org.rapla.entities.User;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.server.RemoteSession;
import org.rapla.server.internal.ResourceBundleList;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.RemoteLocaleService;
import org.rapla.storage.StorageOperator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestController
@ConditionalOnBean(RemoteSession.class)
public class RemoteLocaleController implements RemoteLocaleService
{
    private final ServerBundleManager bundleManager;
    private final RaplaLocale raplaLocale;
    private final RemoteSession session;
    private final StorageOperator operator;
    private final ResourceBundleList resourceBundleList;
    private final HttpServletRequest request;
    private final RequestLanguage requestLanguage;

    public RemoteLocaleController(ServerBundleManager bundleManager,
                                  RaplaLocale raplaLocale,
                                  RemoteSession session,
                                  StorageOperator operator,
                                  ResourceBundleList resourceBundleList,
                                  HttpServletRequest request,
                                  RequestLanguage requestLanguage)
    {
        this.bundleManager = bundleManager;
        this.raplaLocale = raplaLocale;
        this.session = session;
        this.operator = operator;
        this.resourceBundleList = resourceBundleList;
        this.request = request;
        this.requestLanguage = requestLanguage;
    }

    @Override
    public LocalePackage locale(String id, String localeString)
    {
        try
        {
            if (localeString == null)
            {
                // PRD 050 Phase 7a: single resolveJwtOrThrow per request.
                User validUser = null;
                try
                {
                    validUser = session.checkAndGetUser(request);
                }
                catch (RaplaSecurityException unauthenticated)
                {
                    // Anonymous request — no user preference.
                }
                localeString = requestLanguage.resolve(request, validUser).toString();
            }
            Locale locale = LocaleTools.getLocale(localeString);
            final I18nLocaleFormats formats = bundleManager.getFormats(locale);
            Map<String, Map<String, String>> bundles = resourceBundleList.getBundles(locale);
            String language = locale.getLanguage();
            String country = locale.getCountry();
            Set<String> availableLanguages = bundleManager.getAvailableLanguages();
            return new LocalePackage(formats, language, country, bundles, availableLanguages);
        }
        catch (RaplaException ex)
        {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public Map<String, Set<String>> countries(Set<String> languages)
    {
        return bundleManager.getCountriesForLanguage(languages);
    }
}
