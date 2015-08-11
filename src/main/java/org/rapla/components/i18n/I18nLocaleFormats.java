package org.rapla.components.i18n;

public interface I18nLocaleFormats
{
    
    String getFormatDateShort();

    String getFormatDateLong( );

    String[] getWeekdays( );

    String[] getMonths( );

    boolean isAmPmFormat();
    
    String getAmPmFormat();

    String getFormatTime();

    String getFormatMonthYear();

    String getFormatHour();

}
