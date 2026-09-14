package org.rapla.server.internal;

import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.rapla.components.i18n.server.ServerBundleManager;
import org.rapla.entities.configuration.Preferences;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.internal.RaplaLocaleImpl;
import org.rapla.test.util.FacadeTestSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The server-configured "Server Sprache" is a SYSTEM preference
 * ({@link RaplaLocale#LANGUAGE_ENTRY}); the GraphQL schema/name resolution must
 * honor it rather than the JVM default {@link Locale#getDefault()}.
 */
class ServerLocaleResolverTest extends FacadeTestSupport
{
    private RaplaLocale newRaplaLocale()
    {
        return new RaplaLocaleImpl(new ServerBundleManager());
    }

    @Test
    void usesSystemPreferenceLanguageOverJvmDefault() throws Exception
    {
        Preferences edit = facade.edit(facade.getSystemPreferences());
        edit.putEntry(RaplaLocale.LANGUAGE_ENTRY, "de");
        facade.store(edit);

        Locale resolved = ServerLocaleResolver.resolve(operator, newRaplaLocale());
        assertEquals("de", resolved.getLanguage());
    }

    @Test
    void fallsBackToConfiguredLocaleWhenSystemPreferenceUnset() throws Exception
    {
        RaplaLocale fallback = newRaplaLocale();
        Locale resolved = ServerLocaleResolver.resolve(operator, fallback);
        assertEquals(fallback.getLocale(), resolved);
    }
}
