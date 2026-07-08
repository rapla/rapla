package org.rapla.server.internal;

import java.util.Locale;

import org.rapla.components.util.LocaleTools;
import org.rapla.entities.configuration.Preferences;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.storage.StorageOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves the server-configured display language — the admin "Server Sprache"
 * setting, stored as the SYSTEM preference {@link RaplaLocale#LANGUAGE_ENTRY}
 * (owner {@code null}). Everything that renders a localized name for ALL clients
 * off a shared artifact (the generated GraphQL SDL {@code @displayName}s, the
 * {@code DynamicType.name} / {@code Allocatable.displayName} / {@code Category.name}
 * fetchers) must resolve names against this, NOT {@link Locale#getDefault()}
 * (the JVM default, which is deployment-environment noise).
 */
public final class ServerLocaleResolver
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerLocaleResolver.class);

    private ServerLocaleResolver() {}

    /**
     * @return the {@link RaplaLocale#LANGUAGE_ENTRY} system-preference language
     *         if set; otherwise {@code raplaLocale.getLocale()}, finally the JVM
     *         default. Never {@code null}.
     */
    public static Locale resolve(StorageOperator operator, RaplaLocale raplaLocale)
    {
        try
        {
            Preferences sys = operator == null ? null : operator.getPreferences(null, false);
            if (sys != null)
            {
                String lang = sys.getEntryAsString(RaplaLocale.LANGUAGE_ENTRY, null);
                if (lang != null && !lang.isBlank())
                {
                    return LocaleTools.getLocale(lang);
                }
            }
        }
        catch (RaplaException ex)
        {
            LOGGER.warn("Could not read the system-preference language; falling back to the configured default locale", ex);
        }
        if (raplaLocale != null && raplaLocale.getLocale() != null)
        {
            return raplaLocale.getLocale();
        }
        return Locale.getDefault();
    }
}
