package org.rapla.plugin.externaleventimport.client;

import org.jetbrains.annotations.PropertyKey;
import org.rapla.components.i18n.AbstractBundle;
import org.rapla.components.i18n.BundleManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Generic UI strings for the external-event-import wizard. Carries no
 * domain-specific terminology: nouns for "what is being imported" come from
 * the server's {@code ExternalEventImportMetadata} response at runtime, and
 * fill the {@code {0}}/{@code {1}} placeholders in the templates here.
 */
@Component
public class ExternalEventImportResources extends AbstractBundle
{
    public static final String BUNDLENAME = "org.rapla.plugin.externaleventimport.client.ExternalEventImportResources";

    @Autowired
    public ExternalEventImportResources(BundleManager bundleManager)
    {
        super(BUNDLENAME, bundleManager);
    }

    @Override
    public String getString(@PropertyKey(resourceBundle = BUNDLENAME) String key)
    {
        return super.getString(key);
    }

    @Override
    public String getString(@PropertyKey(resourceBundle = BUNDLENAME) String key, Locale locale)
    {
        return super.getString(key, locale);
    }

    @Override
    public String format(@PropertyKey(resourceBundle = BUNDLENAME) String key, Object... obj)
    {
        return super.format(key, obj);
    }
}
