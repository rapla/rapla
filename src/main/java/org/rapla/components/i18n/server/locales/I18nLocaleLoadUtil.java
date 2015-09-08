package org.rapla.components.i18n.server.locales;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import org.rapla.components.i18n.I18nLocaleFormats;
import org.rapla.components.xmlbundle.impl.ResourceBundleLoader;

public class I18nLocaleLoadUtil
{

    private static final Map<Locale, I18nLocaleFormats> cache = new HashMap<Locale, I18nLocaleFormats>();

    public static I18nLocaleFormats read(Locale localeId)
    {
        I18nLocaleFormats formats = cache.get(localeId);
        if (formats != null)
            return formats;
        synchronized (cache)
        {
            formats = cache.get(localeId);
            if (formats != null)
                return formats;
            final String className = I18nLocaleLoadUtil.class.getPackage().getName() + ".format";
            final ResourceBundle bundle = ResourceBundleLoader.loadResourceBundle(className, localeId);
            String amPm = bundle.getString("amPm");
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
