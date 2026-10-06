package org.rapla.server.spring.web;

import java.util.Locale;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.util.LocaleTools;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.server.internal.ServerLocaleResolver;
import org.rapla.storage.StorageOperator;
import org.springframework.stereotype.Component;

/**
 * PRD 124 OQ2 — the display language of a browser request: the login-page choice
 * ({@value #COOKIE} cookie, shipped languages only), else the user's language preference,
 * else the configured server language, else the browser's Accept-Language (shipped languages only), else the JVM/bundle default. Shared by /api/locale and /api/auth/me.
 */
@Component
public class RequestLanguage
{
    public static final String COOKIE = "raplaLocale";

    private final BundleManager bundleManager;
    private final StorageOperator operator;
    private final RaplaLocale raplaLocale;

    public RequestLanguage(BundleManager bundleManager, StorageOperator operator, RaplaLocale raplaLocale)
    {
        this.bundleManager = bundleManager;
        this.operator = operator;
        this.raplaLocale = raplaLocale;
    }

    public Locale resolve(HttpServletRequest request, User user) throws RaplaException
    {
        String chosen = cookie(request);
        if (chosen != null && bundleManager.getAvailableLanguages().contains(chosen))
        {
            return LocaleTools.getLocale(chosen);
        }
        if (user != null)
        {
            Preferences preferences = operator.getPreferences(user, true);
            String entry = preferences.getEntryAsString(RaplaLocale.LANGUAGE_ENTRY, null);
            if (entry != null)
            {
                return new Locale(entry);
            }
        }
        Locale configured = ServerLocaleResolver.configured(operator);
        if (configured != null)
        {
            return configured;
        }
        String browser = browserLanguage(request);
        if (browser != null)
        {
            return LocaleTools.getLocale(browser);
        }
        return ServerLocaleResolver.resolve(operator, raplaLocale);
    }

    /** The first Accept-Language entry (q order) whose language the shipped bundles cover; null if none. */
    public String browserLanguage(HttpServletRequest request)
    {
        if (request.getHeader("Accept-Language") == null)
        {
            return null;    // without the header the container reports its own default locale
        }
        java.util.Enumeration<Locale> locales = request.getLocales();
        while (locales != null && locales.hasMoreElements())
        {
            String lang = locales.nextElement().getLanguage();
            if (bundleManager.getAvailableLanguages().contains(lang))
            {
                return lang;
            }
        }
        return null;
    }

    /** {@link #resolve} for the servlet request bound to the current thread; null when there is none. */
    public Locale resolveCurrent(User user) throws RaplaException
    {
        return org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()
                instanceof org.springframework.web.context.request.ServletRequestAttributes attrs
                ? resolve(attrs.getRequest(), user) : null;
    }

    private static String cookie(HttpServletRequest request)
    {
        Cookie[] cookies = request.getCookies();
        if (cookies != null)
        {
            for (Cookie c : cookies)
            {
                if (COOKIE.equals(c.getName()))
                {
                    return c.getValue();
                }
            }
        }
        return null;
    }
}
