package org.rapla.server.spring.web;

import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.rapla.server.internal.ResourceBundleList;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * PRD 124 Phase 1: the SPA-only texts ship as the SpaResources bundle, so /api/locale
 * (ResourceBundleList) delivers them next to the Swing bundles, per language.
 */
@Tag("e2e")
@SpringBootTest(classes = RaplaSpringBootApplication.class)
class SpaResourcesBundleTest extends IsolatedDefaultDatasetTest
{
    @Autowired
    ResourceBundleList bundles;

    @Test
    void spaBundleIsDeliveredPerLanguage()
    {
        Map<String, String> de = bundles.getBundles(Locale.GERMAN).get("org.rapla.SpaResources");
        Map<String, String> en = bundles.getBundles(Locale.ENGLISH).get("org.rapla.SpaResources");
        assertNotNull(de, "SpaResources must be a registered I18nBundle");
        assertEquals("Neu laden", de.get("reload"));
        assertEquals("Reload", en.get("reload"));
    }
}
