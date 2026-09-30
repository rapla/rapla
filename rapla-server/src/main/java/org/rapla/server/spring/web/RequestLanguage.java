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
import org.rapla.storage.StorageOperator;
import org.springframework.stereotype.Component;

/**
 * PRD 124 OQ2 — the display language of a browser request: the login-page choice
 * ({@value #COOKIE} cookie, shipped languages only), else the user's language preference,
 * else the server language. Shared by /api/locale and /api/auth/me.
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
        return raplaLocale.getLocale();
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
