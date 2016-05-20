package org.rapla.components.i18n;

import java.util.Collection;
import java.util.Locale;

/**
 * Created by Christopher on 13.08.2015.
 */
public interface BundleManager {
    Locale getLocale();

    String format(String string, Object[] obj);

    String getString(String packageId, String key, Locale locale);

    String getString(String packageId,String key);

    Collection<String> getKeys(String packageId);

    I18nLocaleFormats getFormats();

    Collection<String> getAvailableLanguages();

    void setLanguage(String language);
}
