package org.rapla.components.i18n;

public class I18nLocaleFormats
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

    public I18nLocaleFormats()
    {
    }

    public I18nLocaleFormats(boolean isAmPm, String amPm, String formatDateShort, String formatDateLong, String[] weekdays, String[] months, String formatHour,
            String formatMonthYear, String formatTime)
    {
        super();
        this.isAmPm = isAmPm;
        this.amPm = amPm;
        this.formatDateShort = formatDateShort;
        this.formatDateLong = formatDateLong;
        this.weekdays = weekdays;
        this.months = months;
        this.formatHour = formatHour;
        this.formatMonthYear = formatMonthYear;
        this.formatTime = formatTime;
    }

    public String getFormatDateShort()
    {
        return formatDateShort;
    }

    public String getFormatDateLong()
    {
        return formatDateLong;
    }

    public String[] getWeekdays()
    {
        return weekdays;
    }

    public String[] getMonths()
    {
        return months;
    }

    public boolean isAmPmFormat()
    {
        return isAmPm;
    }

    public String getAmPmFormat()
    {
        return amPm;
    }

    public String getFormatTime()
    {
        return formatTime;
    }

    public String getFormatMonthYear()
    {
        return formatMonthYear;
    }

    public String getFormatHour()
    {
        return formatHour;
    }

}
