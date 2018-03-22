package org.rapla.plugin.exchangeconnector;

import org.jetbrains.annotations.PropertyKey;
import org.rapla.components.i18n.AbstractBundle;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.i18n.I18nBundle;
import org.rapla.inject.Extension;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Locale;

@Extension(provides = I18nBundle.class, id = ExchangeConnectorResources.BUNDLENAME)
@Singleton
public class ExchangeConnectorResources extends AbstractBundle
{
    public static final String BUNDLENAME = "org.rapla.plugin.exchangeconnector.ExchangeConnectorResources";

    @Inject
    public ExchangeConnectorResources(BundleManager bundleManager)
    {
        super(BUNDLENAME, bundleManager);
    }

    public String getString(@PropertyKey(resourceBundle = BUNDLENAME) String key)
    {
        return super.getString(key);
    }

    public String getString(@PropertyKey(resourceBundle = BUNDLENAME) String key, Locale locale)
    {
        return super.getString(key, locale);
    }

    public String format(@PropertyKey(resourceBundle = BUNDLENAME) String key, Object... obj)
    {
        return super.format(key, obj);
    }

}
