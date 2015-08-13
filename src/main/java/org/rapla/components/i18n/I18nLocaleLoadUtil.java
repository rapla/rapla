package org.rapla.components.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Properties;

import org.rapla.framework.RaplaException;

public class I18nLocaleLoadUtil
{

    public static I18nLocaleFormats read(String localeId) throws RaplaException
    {
        final String localeFile = localeId + ".properties";
        final InputStream stream = I18nLocaleFormats.class.getResourceAsStream(localeFile);
        final Properties properties = new Properties();
        try
        {
            Reader reader = new InputStreamReader(stream, "UTF-8");
            properties.load(reader);
            String amPm = properties.getProperty("amPm");
            boolean isAmPm = Boolean.parseBoolean(properties.getProperty("isAmPm"));
            String formatDateShort = properties.getProperty("formatDateShort");
            String formatDateLong = properties.getProperty("formatDateLong");
            String formatHour = properties.getProperty("formatHour");
            String formatMonthYear = properties.getProperty("formatMonthYear");
            String formatTime = properties.getProperty("formatTime");
            String[] weekdays = parseArray(properties.getProperty("weekdays"));
            String[] months = parseArray(properties.getProperty("months"));
            final I18nLocaleFormats result = new I18nLocaleFormats(isAmPm, amPm, formatDateShort, formatDateLong, weekdays, months, formatHour, formatMonthYear, formatTime);
            return result;
        }
        catch (IOException e)
        {
            throw new RaplaException("File not found: "+localeFile, e);
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
