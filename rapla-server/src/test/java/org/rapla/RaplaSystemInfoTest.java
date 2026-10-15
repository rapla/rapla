package org.rapla;

import org.junit.jupiter.api.Test;
import org.rapla.components.i18n.server.ServerBundleManager;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RaplaSystemInfoTest
{
    @Test
    void infoTextShowsTheConfiguredVersionAndLinksTheHomepageInEveryLanguage()
    {
        ServerBundleManager bundleManager = new ServerBundleManager();
        RaplaSystemInfo info = new RaplaSystemInfo(bundleManager);
        String builtIn = info.getString("rapla.version");
        for (String language : new String[] { "en", "cs", "de", "es", "fi", "fr", "nl", "pl", "pt" })
        {
            bundleManager.setLocale(Locale.forLanguageTag(language));
            info.setVersion(null);
            String text = info.infoText("21");
            assertTrue(text.contains(builtIn) && !text.contains("{2}"), language + ": " + text);
            assertTrue(text.contains("<a href=\"http://rapla.org\">http://rapla.org</a>"), language + ": " + text);
            info.setVersion("3.0 DHBW");
            text = info.infoText("21");
            assertTrue(text.contains("3.0 DHBW") && !text.contains(builtIn), language + ": " + text);
        }
    }
}
