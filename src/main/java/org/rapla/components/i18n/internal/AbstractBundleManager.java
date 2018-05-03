package org.rapla.components.i18n.internal;

import org.rapla.RaplaResources;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.i18n.I18nLocaleFormats;
import org.rapla.components.util.DateTools;
import org.rapla.components.i18n.LocaleChangeEvent;
import org.rapla.components.i18n.LocaleChangeListener;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.Vector;


public abstract class AbstractBundleManager implements BundleManager {
    private I18nLocaleFormats formats;
    private LinkedHashMap<String,ResourceBundle> packMap = new LinkedHashMap<>();
    private final Set<String> availableLanguages;
    Locale locale;
    Vector<LocaleChangeListener> localeChangeListeners = new Vector<>();

    public AbstractBundleManager()
    {
        String selectedCountry = Locale.getDefault().getCountry() ;
        String selectedLanguage = Locale.getDefault().getLanguage();
        locale = new Locale(selectedLanguage, selectedCountry);
        //locale = getDefaultLocale();
        //localeSelector.setLocale( locale );
        this.formats = getFormats(locale);
        this.availableLanguages = loadAvailableLanguages();
    }


    private Set<String> loadAvailableLanguages() {
        Set<String> availableLanguages = new LinkedHashSet<>();
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
        return Collections.unmodifiableSet(availableLanguages);
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
    public String getString(String packageId,String key, Locale locale) {
        ResourceBundle pack = loadLocale(packageId, locale);
        return pack.getString(key);
    }

    @Override
    public String getString(String packageId, String key) {
        Locale locale = getLocale();
        return getString(packageId, key, locale);
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
        if(this.locale.equals(locale))
        {
            return;
        }
        this.locale = locale;
        this.formats = getFormats(locale);
        fireLocaleChanged();
    }

    public Locale getLocale() {
        return this.locale;
    }

    public LocaleChangeListener[] getLocaleChangeListeners() {
        return localeChangeListeners.toArray(new LocaleChangeListener[]{});
    }

    public void setLanguage(String language) {
        Locale locale = new Locale(language, this.locale.getCountry());
        setLocale(locale);
    }

    public void setCountry(String country) {
        Locale locale = new Locale(this.locale.getLanguage(), country);
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

    private  final Map<Locale, I18nLocaleFormats> cache = new HashMap<>();

    public  I18nLocaleFormats getFormats(Locale localeId)
    {
        I18nLocaleFormats formats = cache.get(localeId);
        if (formats != null)
            return formats;
        synchronized (cache)
        {
            formats = cache.get(localeId);
            if (formats != null)
                return formats;
            final String className = AbstractBundleManager.class.getPackage().getName() + ".locales.format";
            final ResourceBundle bundle = ResourceBundleLoader.loadResourceBundle(className, localeId);
            String[] amPm = parseArray(bundle.getString("amPm"));
            boolean isAmPm = Boolean.parseBoolean(bundle.getString("isAmPm"));
            String formatDateShort = bundle.getString("formatDateShort");
            String formatDateLong = bundle.getString("formatDateLong");
            String formatHour = bundle.getString("formatHour");
            String formatMonthYear = bundle.getString("formatMonthYear");
            String formatTime = bundle.getString("formatTime");
            String[] weekdays = parseArray(bundle.getString("weekdays"));
            String[] months = parseArray(bundle.getString("months"));
            String[] shortWeekdays = parseArray(bundle.getString("shortWeekdays"));
            String[] shortMonths = parseArray(bundle.getString("shortMonths"));
            formats = new I18nLocaleFormats(isAmPm, amPm, formatDateShort, formatDateLong, weekdays, shortWeekdays, months, shortMonths, formatHour, formatMonthYear, formatTime);
            cache.put(localeId, formats);
            return formats;
        }
    }

    private static String[] parseArray(String property)
    {
        if (property == null)
            return null;
        final String[] result = property.substring(1, property.length() - 1).split(",");
        for (int i = 0; i < result.length; i++)
        {
            result[i] = result[i].trim();
        }
        return result;
    }
}
