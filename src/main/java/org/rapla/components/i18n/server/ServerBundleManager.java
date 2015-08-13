package org.rapla.components.i18n.server;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import javax.inject.Inject;

import org.rapla.components.i18n.BundleManager;
import org.rapla.components.i18n.I18nLocaleFormats;
import org.rapla.components.i18n.server.locales.I18nLocaleLoadUtil;
import org.rapla.components.xmlbundle.LocaleSelector;
import org.rapla.components.xmlbundle.impl.ResourceBundleLoader;
import org.rapla.framework.RaplaException;

public class ServerBundleManager implements BundleManager {
    private final LocaleSelector localeSelector;
    private I18nLocaleFormats formats;
    private LinkedHashMap<String,ResourceBundle> packMap = new LinkedHashMap<String,ResourceBundle>();

    @Inject
    public ServerBundleManager(LocaleSelector localeSelector) throws RaplaException
    {
        this.localeSelector = localeSelector;
        String selectedCountry = Locale.getDefault().getCountry() ;
        String selectedLanguage = Locale.getDefault().getLanguage();
        final Locale locale = new Locale(selectedLanguage, selectedCountry);
        localeSelector.setLocale( locale );
        this.formats = I18nLocaleLoadUtil.read(locale);
    }

    public Collection<String> getKeys(String packageId)
    {
        ResourceBundle pack = loadLocale(createKey(packageId, Locale.ENGLISH), Locale.ENGLISH);
        final ArrayList<String> keys = Collections.list(pack.getKeys());
        return keys;
    }

    private final String createKey(String packageId, Locale locale){
        return packageId+"_"+locale.toString();
    }

    @Override
    public I18nLocaleFormats getFormats() {
        return formats;
    }

    @Override
    public Locale getLocale() {
        return localeSelector.getLocale();
    }

    @Override
    public String format(String string, Object[] obj) {
        final MessageFormat messageFormat = new MessageFormat(string);
        final String format = messageFormat.format(obj);
        return format;
    }

    @Override
    public String getString(String packageId,String key, Locale locale) {
        ResourceBundle pack = loadLocale(packageId, locale);
        return pack.getString(key);
    }

    @Override
    public String getString(String packageId, String key) {
        Locale locale = getLocale();
        return getString(packageId,key, locale);
    }

    protected ResourceBundle loadLocale(String packageId,Locale locale)  throws MissingResourceException {
        final String packKey = createKey(packageId, locale);
        ResourceBundle pack = packMap.get(packKey);
        if (pack != null)
        {
            return pack;
        }
        synchronized ( packMap )
        {
            // again, now with synchronization
            pack = packMap.get(packKey);
            if (pack != null)
            {
                return pack;
            }
            pack = ResourceBundleLoader.loadResourceBundle(packageId, locale);
            packMap.put( packKey, pack);
            return pack;
        }
    }

}
