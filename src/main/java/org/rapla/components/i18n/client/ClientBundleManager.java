package org.rapla.components.i18n.client;

import java.util.Collection;
import java.util.Locale;

import org.rapla.components.i18n.BundleManager;
import org.rapla.components.i18n.I18nLocaleFormats;
import org.rapla.components.i18n.LocalePackage;
import org.rapla.components.xmlbundle.LocaleSelector;
import org.rapla.storage.dbrm.RemoteServer;

import com.google.inject.Inject;

public class ClientBundleManager implements BundleManager {
    private final RemoteServer remoteServer;
    private final LocaleSelector selector;
    LocalePackage localePackage;

    @Inject
    protected ClientBundleManager(LocaleSelector selector, RemoteServer remoteServer) {
        this.remoteServer = remoteServer;
        this.selector=selector;
    }


    @Override
    public String format(String string, Object[] obj) {
        // TODO
//        final MessageFormat messageFormat = new MessageFormat(string);
//        final String format = messageFormat.format(obj);
//        return format;
        return string;
    }

    @Override
    public String getString(String packageId,String key, Locale locale) {
        return getString( packageId, key);
    }

    @Override
    public String getString(String packageId, String key) {
        String result = localePackage.getBundles().get( packageId).get(key);
        return result;
    }

    @Override
    public Locale getLocale()
    {
        return selector.getLocale();
    }

    @Override
    public Collection<String> getKeys(String packageId) {
        return localePackage.getBundles().get( packageId).keySet();
    }


    @Override
    public I18nLocaleFormats getFormats()
    {
        return null;
    }
}
