package org.rapla.components.i18n.client;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;

import com.google.gwt.core.client.GWT;
import org.gwtbootstrap3.client.ui.form.validator.MessageFormat;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.i18n.I18nLocaleFormats;
import org.rapla.components.i18n.LocalePackage;
import org.rapla.rest.gwtjsonrpc.common.FutureResult;
import org.rapla.storage.dbrm.RemoteServer;

import com.google.inject.Inject;

public class ClientBundleManager implements BundleManager
{
    LocalePackage localePackage;
    Locale locale;

    @Inject
    protected ClientBundleManager( RemoteServer remoteServer)
    {
        try
        {
            String id = "123";
            String localeParam = null;
            final FutureResult<LocalePackage> locale1 = remoteServer.locale(id, localeParam);
            localePackage = locale1.get();
            String language = localePackage.getLanguage();
            String country = localePackage.getCountry();

            locale = Locale.getDefault();
            StringBuilder sb = new StringBuilder(language);
            if(country != null && !country.isEmpty())
            {
                sb.append("_");
                sb.append(country);
            }
            String localeString = sb.toString();
            change(locale, localeString);
        }
        catch (Exception e)
        {
            throw new IllegalStateException("Could not load language pack",e);
        }
    }

    @Override
    public Collection<String> getAvailableLanguages() {
        return localePackage.getAvailableLanguages();
    }

    @Override
    public String format(String string, Object[] obj)
    {
        // TODO
        //        final MessageFormat messageFormat = new MessageFormat(string);
        //        final String format = messageFormat.format(obj);
        //        return format;
        return MessageFormat.format(string, obj);
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
        return locale;
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

    private native void change(final Locale locale,final String id)/*-{
        var res = [];
        for(var m in locale) {
            if(typeof locale[m] == "function") {
                res.push(m.toString());
                console.log("Func " + m);
            }
            else
            {
                console.log("Attr " + m);
            }
        }

        locale[res[1]]=function(){
            return id;
        };
    }-*/ ;

}
