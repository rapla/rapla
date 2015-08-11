package org.rapla.components.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class I18nLocaleFormatsImpl implements I18nLocaleFormats
{
    private boolean isAmPm;
    private String amPm;
    private String formatDateShort;
    private String formatDateLong;
    private String[] weekdays;
    private String[] months;
    private String formatHour;
    private String formatMonthYear;
    private String formatTime;

    public I18nLocaleFormatsImpl()
    {
    }

    public static I18nLocaleFormats read(String localeId)
    {
        final InputStream stream = I18nLocaleFormatsImpl.class.getResourceAsStream(localeId + ".properties");
        final I18nLocaleFormatsImpl result = new I18nLocaleFormatsImpl();
        final Properties properties = new Properties();
        try
        {
            properties.load(stream);
            result.amPm = properties.getProperty("amPm");
            result.isAmPm = Boolean.parseBoolean(properties.getProperty("isAmPm"));
            result.formatDateShort = properties.getProperty("formatDateShort");
            result.formatDateLong = properties.getProperty("formatDateLong");
            result.formatHour = properties.getProperty("formatHour");
            result.formatMonthYear = properties.getProperty("formatMonthYear");
            result.formatTime = properties.getProperty("formatTime");
            result.weekdays = parseArray(properties.getProperty("weekdays"));
            result.months = parseArray(properties.getProperty("months"));
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
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

    @Override
    public String getFormatDateShort()
    {
        return formatDateShort;
    }

    @Override
    public String getFormatDateLong()
    {
        return formatDateLong;
    }

    @Override
    public String[] getWeekdays()
    {
        return weekdays;
    }

    @Override
    public String[] getMonths()
    {
        return months;
    }

    @Override
    public boolean isAmPmFormat()
    {
        return isAmPm;
    }

    @Override
    public String getAmPmFormat()
    {
        return amPm;
    }

    @Override
    public String getFormatTime()
    {
        return formatTime;
    }

    @Override
    public String getFormatMonthYear()
    {
        return formatMonthYear;
    }

    @Override
    public String getFormatHour()
    {
        return formatHour;
    }

}
