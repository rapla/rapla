package org.rapla.components.i18n.server.locales;

import java.util.Locale;
import java.util.ResourceBundle;

import org.rapla.components.i18n.I18nLocaleFormats;
import org.rapla.components.xmlbundle.impl.ResourceBundleLoader;
import org.rapla.framework.RaplaException;

public class I18nLocaleLoadUtil
{

    public static I18nLocaleFormats read(Locale localeId) 
    {
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
        final I18nLocaleFormats result = new I18nLocaleFormats(isAmPm, amPm, formatDateShort, formatDateLong, weekdays, months, formatHour, formatMonthYear, formatTime);
        return result;
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
