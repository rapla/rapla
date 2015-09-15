package org.rapla.components.i18n.server;

import org.rapla.RaplaResources;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.i18n.I18nLocaleFormats;
import org.rapla.components.i18n.server.locales.I18nLocaleLoadUtil;
import org.rapla.components.util.DateTools;
import org.rapla.components.xmlbundle.LocaleChangeEvent;
import org.rapla.components.xmlbundle.LocaleChangeListener;
import org.rapla.components.xmlbundle.impl.ResourceBundleLoader;
import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import javax.inject.Inject;
import java.text.MessageFormat;
import java.util.*;

@DefaultImplementation(of=BundleManager.class,context = { InjectionContext.server, InjectionContext.swing})
public class ServerBundleManager implements BundleManager {
    private I18nLocaleFormats formats;
    private LinkedHashMap<String,ResourceBundle> packMap = new LinkedHashMap<String,ResourceBundle>();
    private Set<String> availableLanguages = new LinkedHashSet<String>();
    Locale locale;
    Vector<LocaleChangeListener> localeChangeListeners = new Vector<LocaleChangeListener>();



    @Inject
    public ServerBundleManager() throws RaplaException
    {
        String selectedCountry = Locale.getDefault().getCountry() ;
        String selectedLanguage = Locale.getDefault().getLanguage();
        locale = new Locale(selectedLanguage, selectedCountry);
        //locale = getDefaultLocale();
        //localeSelector.setLocale( locale );
        this.formats = I18nLocaleLoadUtil.read(locale);
        availableLanguages = loadAvailableLanguages();
    }

    public static Set<String> loadAvailableLanguages() {
        Set<String> availableLanguages = new LinkedHashSet<String>();
        Locale[] availableLocales = Locale.getAvailableLocales();
        final String prefix = "/org/rapla/RaplaResources";
        final String suffix = ".properties";
        for (Locale aLocale: availableLocales){
            String localeString = aLocale.toString();
            final String rb;
            if(localeString.equalsIgnoreCase("en"))
            {
                rb = prefix+suffix;
            }
            else
            {
                rb= prefix + "_" + localeString +suffix;
            }
            if(RaplaResources.class.getResource(rb) != null){
                availableLanguages.add(aLocale.getLanguage());
            }
        }
        return availableLanguages;
    }

    public Set<String> getAvailableLanguages() {
        return availableLanguages;
    }

    public Collection<String> getKeys(String packageId)
    {
        ResourceBundle pack = loadLocale(packageId, Locale.ENGLISH);
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



    protected Locale getDefaultLocale()
    {
        Locale aDefault = Locale.getDefault();
        return aDefault;
    }

    public void addLocaleChangeListener(LocaleChangeListener listener) {
        localeChangeListeners.add(listener);
    }
    public void removeLocaleChangeListener(LocaleChangeListener listener) {
        localeChangeListeners.remove(listener);
    }

    public void setLocale(Locale locale) {
        this.locale = locale;
        fireLocaleChanged();
    }

    public Locale getLocale() {
        return this.locale;
    }

    public LocaleChangeListener[] getLocaleChangeListeners() {
        return localeChangeListeners.toArray(new LocaleChangeListener[]{});
    }

    public void setLanguage(String language) {
        Locale locale = DateTools.changeLang(language, this.locale);
        setLocale(locale);
    }

    public void setCountry(String country) {
        Locale locale = DateTools.changeCountry(country, this.locale);
        setLocale(locale);
    }

    public String getLanguage() {
        return DateTools.getLang( this.locale);
    }

    protected void fireLocaleChanged() {
        if (localeChangeListeners.size() == 0)
            return;
        LocaleChangeListener[] listeners = getLocaleChangeListeners();
        LocaleChangeEvent evt = new LocaleChangeEvent(this,getLocale());
        for (int i=0;i<listeners.length;i++)
            listeners[i].localeChanged(evt);
    }

    /** This Listeners is for the bundles only, it will ensure the bundles are always
     notified first.
     */
    void addLocaleChangeListenerFirst(LocaleChangeListener listener) {
        localeChangeListeners.add(0,listener);
    }


}
