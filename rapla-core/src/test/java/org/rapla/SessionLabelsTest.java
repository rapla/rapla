package org.rapla;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.junit.jupiter.api.Test;

/** PRD 131 R3, D6, OQ3: the Swing session labels (user menu, File menu, session-ended dialog). */
class SessionLabelsTest
{
    private static Properties load(String file) throws IOException
    {
        Properties p = new Properties();
        try (InputStream in = SessionLabelsTest.class.getResourceAsStream("/org/rapla/" + file))
        {
            assertNotNull(in, file);
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return p;
    }

    @Test
    void germanLabels() throws IOException
    {
        Properties de = load("RaplaResources_de.properties");
        assertEquals("Neu anmelden", de.getProperty("session.relogin"));
        assertEquals("Beenden (ohne Abmelden)", de.getProperty("session.quit_stay"));
        assertEquals("Beenden", de.getProperty("session.quit"));
        assertEquals("Abmelden (Logout)", de.getProperty("session.logout"));
        assertEquals("Sie wurden abgemeldet.", de.getProperty("session.ended"));
        assertEquals("Zurückwechseln", de.getProperty("session.switch_back"));
        assertEquals("Zu Benutzer wechseln…", de.getProperty("session.switch_to"));
        assertEquals("Ihre Login-Daten konnten auf diesem Rechner nicht gespeichert werden ({0}). Beim nächsten Start ist eine neue Anmeldung nötig.",
                de.getProperty("session.not_stored"));
    }

    @Test
    void englishLabels() throws IOException
    {
        Properties en = load("RaplaResources.properties");
        assertEquals("Log in again", en.getProperty("session.relogin"));
        assertEquals("Quit (stay logged in)", en.getProperty("session.quit_stay"));
        assertEquals("Quit", en.getProperty("session.quit"));
        assertEquals("Logout", en.getProperty("session.logout"));
    }

    @Test
    void everyLanguageHasEveryKeyOrFallsBackToEnglish() throws IOException
    {
        Properties en = load("RaplaResources.properties");
        for (String key : en.stringPropertyNames())
        {
            if (!key.startsWith("session.")) continue;
            for (String lang : new String[] { "de", "cs", "es", "fi", "fr", "nl", "pl", "pt" })
            {
                assertNotNull(load("RaplaResources_" + lang + ".properties").getProperty(key), key + " missing in " + lang);
            }
        }
    }
}
