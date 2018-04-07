package org.rapla.components.i18n.client.gwt;

import org.rapla.components.i18n.BundleManager;
import org.rapla.components.i18n.I18nLocaleFormats;
import org.rapla.components.i18n.I18nIconURL;
import org.rapla.components.i18n.LocalePackage;
import org.rapla.components.i18n.I18nIcon;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;

@DefaultImplementation(of=BundleManager.class,context= InjectionContext.gwt)
@Singleton
public class GwtBundleManager implements BundleManager
{
    LocalePackage localePackage;
    Locale locale;

    @Inject
    public GwtBundleManager()
    {
        setLocalPackage(new LocalePackage());
    }

    public void setLocalPackage(LocalePackage localePackage)
    {
        this.localePackage = localePackage;
        String language = localePackage.getLanguage();
        String country = localePackage.getCountry();
        locale = newLocale(language, country);
    }

    @Override
    public Collection<String> getAvailableLanguages() {
        return localePackage.getAvailableLanguages();
    }

    @Override
    public String getString(String packageId, String key, Locale locale)
    {
        return getString(packageId, key);
    }

    @Override
    public String getString(String packageId, String key)
    {
        final Map<String, String> language = localePackage.getBundle(packageId);
        if ( language == null)
        {
            return key;
        }
        String result = language.get(key);
        return result;
    }

    public void setLanguage(String language)
    {
        // language change currently no supported in gwt
    }

    @Override
    public I18nIcon getIcon(String packageId, String key) {
        String location = getString(packageId, key);
        return new I18nIconURL( key, location);
    }

    @Override
    public Locale getLocale()
    {
        return locale;
    }

    @Override
    public Collection<String> getKeys(String packageId)
    {
        return localePackage.getBundle(packageId).keySet();
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
//                console.log("Func " + m);
//            }
//            else
//            {
//                console.log("Attr " + m);
            }
        }

        locale[res[1]]=function(){
            return id;
        };
    }-*/ ;

    public Locale newLocale(String language, String country)
    {
        Locale locale = Locale.getDefault();
        StringBuilder sb = new StringBuilder(language);
        if(country != null && !country.isEmpty())
        {
            sb.append("_");
            sb.append(country);
        }
        String localeString = sb.toString();
        change(locale, localeString);
        return locale;
    }

}
