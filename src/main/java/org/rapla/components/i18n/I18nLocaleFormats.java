package org.rapla.components.i18n;

public class I18nLocaleFormats
{
    private boolean isAmPm;
    private String[] amPm;
    private String formatDateShort;
    private String formatDateLong;
    private String[] weekdays;
    private String[] shortWeekdays;
    private String[] months;
    private String[] shortMonths;
    private String formatHour;
    private String formatMonthYear;
    private String formatTime;

    public I18nLocaleFormats()
    {
        isAmPm = false;
        amPm = new String[] {};
        weekdays = new String[] {"M","T","W","T","F","S","S"};
        shortWeekdays = weekdays;
        months = new String[] {"J","F","M","A","M","J","J", "A","S","O","N","D"};
        shortMonths = months;
        formatDateLong="M/d/yy";
        formatDateShort = formatDateLong;
        formatHour="h:mm a";
        formatTime="h:mm:ss a";
    }

    public I18nLocaleFormats(boolean isAmPm, String[] amPm, String formatDateShort, String formatDateLong, String[] weekdays, String[] shortWeekdays, String[] months, String[] shortMonths, String formatHour,
            String formatMonthYear, String formatTime)
    {
        super();
        this.isAmPm = isAmPm;
        this.amPm = amPm;
        this.formatDateShort = formatDateShort;
        this.formatDateLong = formatDateLong;
        this.weekdays = weekdays;
        this.shortWeekdays = shortWeekdays;
        this.months = months;
        this.shortMonths = shortMonths;
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
    
    public String[] getShortMonths()
    {
        return shortMonths;
    }
    
    public String[] getShortWeekdays()
    {
        return shortWeekdays;
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

    public String getAmFormat()
    {
        return amPm[0];
    }

    public String getPmFormat()
    {
        return amPm[1];
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
