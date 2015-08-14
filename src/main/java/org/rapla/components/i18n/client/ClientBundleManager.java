package org.rapla.components.i18n.client;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;

import org.rapla.components.i18n.BundleManager;
import org.rapla.components.i18n.I18nLocaleFormats;
import org.rapla.components.i18n.LocalePackage;
import org.rapla.components.xmlbundle.LocaleSelector;
import org.rapla.storage.dbrm.RemoteServer;

import com.google.inject.Inject;

public class ClientBundleManager implements BundleManager
{
    private final LocaleSelector selector;
    LocalePackage localePackage;

    @Inject
    protected ClientBundleManager(LocaleSelector selector, RemoteServer remoteServer)
    {
        this.selector = selector;
        try
        {
            localePackage = remoteServer.locale("123", getLocale().toString()).get();
        }
        catch (Exception e)
        {
            throw new IllegalStateException("Could not load language pack");
        }
    }

    @Override
    public String format(String string, Object[] obj)
    {
        // TODO
        //        final MessageFormat messageFormat = new MessageFormat(string);
        //        final String format = messageFormat.format(obj);
        //        return format;
        return string;
    }

    @Override
    public String getString(String packageId, String key, Locale locale)
    {
        return getString(packageId, key);
    }

    @Override
    public String getString(String packageId, String key)
    {
        final Map<String, Map<String, String>> bundles = localePackage.getBundles();
        final Map<String, String> language = bundles.get(packageId);
        String result = language.get(key);
        return result;
    }

    @Override
    public Locale getLocale()
    {
        return selector.getLocale();
    }

    @Override
    public Collection<String> getKeys(String packageId)
    {
        return localePackage.getBundles().get(packageId).keySet();
    }

    @Override
    public I18nLocaleFormats getFormats()
    {
        return localePackage.getFormats();
    }
}
