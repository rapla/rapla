package org.rapla.components.i18n;

import java.util.Collection;
import java.util.Locale;

public interface BundleManager {
    Locale getLocale();

    String getString(String packageId, String key, Locale locale);

    String getString(String packageId,String key);

    I18nIcon getIcon(String packageId,String key);

    Collection<String> getKeys(String packageId);

    I18nLocaleFormats getFormats();

    Collection<String> getAvailableLanguages();

    void setLanguage(String language);


}
