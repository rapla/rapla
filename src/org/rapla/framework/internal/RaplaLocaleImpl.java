/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/
package org.rapla.framework.internal;
import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import javax.inject.Inject;

import org.rapla.components.util.DateTools;
import org.rapla.components.xmlbundle.LocaleSelector;
import org.rapla.components.xmlbundle.impl.LocaleSelectorImpl;
import org.rapla.framework.Configuration;
import org.rapla.framework.DefaultConfiguration;

public class RaplaLocaleImpl extends AbstractRaplaLocale  {
	
	TimeZone zone;
	LocaleSelector localeSelector = new LocaleSelectorImpl();

    String[] availableLanguages;

    String COUNTRY = "country";
    String LANGUAGES = "languages";
    String LANGUAGE = "language";
    String CHARSET = "charset";
    String charsetForHtml;
    private TimeZone importExportTimeZone;

    @Inject
    public RaplaLocaleImpl( )
    {
        this(new DefaultConfiguration());
    }
    
	public RaplaLocaleImpl(Configuration config )  
    {
		String selectedCountry = config.getChild( COUNTRY).getValue(Locale.getDefault().getCountry() );
        Configuration languageConfig = config.getChild( LANGUAGES );
        charsetForHtml = config.getChild(CHARSET).getValue("iso-8859-15");
        Configuration[] languages = languageConfig.getChildren( LANGUAGE );
        if ( languages.length == 0)
        {
            availableLanguages = new String[]{
                    "de",
                    "en",
                    "fr",
                    "es",
                    "zh",
                    "cs",
                    "nl",
                    "pl",
                    "pt"
            };
        }
        else
        {
            availableLanguages = new String[languages.length];
            for ( int i=0;i<languages.length;i++ ) {
                availableLanguages[i] = languages[i].getValue("en");
            }
        }
        String selectedLanguage = languageConfig.getAttribute( "default", Locale.getDefault().getLanguage() );
        if (selectedLanguage.trim().length() == 0)
            selectedLanguage = Locale.getDefault().getLanguage();
        localeSelector.setLocale( new Locale(selectedLanguage, selectedCountry) );
        zone = DateTools.getTimeZone();
        importExportTimeZone = TimeZone.getDefault();
    }


	public LocaleSelector getLocaleSelector() {
        return localeSelector;
    }
	
	public Date fromUTCTimestamp(Date date)
	{
        long time = date.getTime();
        long offset =  importExportTimeZone.getOffset(time);
        long raplaTime = time + offset;
		return new Date(raplaTime);
	}
	
	public void setImportExportTimeZone(TimeZone importExportTimeZone) {
        this.importExportTimeZone = importExportTimeZone;
    }

    /* (non-Javadoc)
     * @see org.rapla.common.IRaplaLocale#getAvailableLanguages()
     */
    public String[] getAvailableLanguages() {
        return availableLanguages;
    }

    /* (non-Javadoc)
     * @see org.rapla.common.IRaplaLocale#createCalendar()
     */
    public Calendar createCalendar() {
        return Calendar.getInstance( zone, getLocale() );
    }


    /* (non-Javadoc)
     * @see org.rapla.common.IRaplaLocale#formatTime(java.util.Date)
     */
    public String formatTime( Date date ) {
        Locale locale = getLocale();
        TimeZone timezone = getTimeZone();
		DateFormat format = DateFormat.getTimeInstance( DateFormat.SHORT, locale );
		format.setTimeZone( timezone );
		String formatTime = format.format( date );
		return formatTime;
    }

    /* (non-Javadoc)
     * @see org.rapla.common.IRaplaLocale#formatNumber(long)
     */
    public String formatNumber( long number ) {
        Locale locale = getLocale();
		return NumberFormat.getInstance( locale).format(number );
    }

	/* (non-Javadoc)
     * @see org.rapla.common.IRaplaLocale#formatDateShort(java.util.Date)
     */
    public String formatDateShort( Date date ) {
    	Locale locale = getLocale();
    	TimeZone timezone = zone;
		StringBuffer buf = new StringBuffer();
	    FieldPosition fieldPosition = new FieldPosition( DateFormat.YEAR_FIELD );
		DateFormat format = DateFormat.getDateInstance( DateFormat.SHORT, locale );
		format.setTimeZone( timezone );
	    buf = format.format(date,
	    					buf,
	                        fieldPosition
	                        );
	    if ( fieldPosition.getEndIndex()<buf.length() ) {
	    	buf.delete( fieldPosition.getBeginIndex(), fieldPosition.getEndIndex()+1 );
	    } else if ( (fieldPosition.getBeginIndex()>=0) ) {
	    	buf.delete( fieldPosition.getBeginIndex(), fieldPosition.getEndIndex() );
	    }
	    String result = buf.toString();
	    return result;
    }

 
    /* (non-Javadoc)
     * @see org.rapla.common.IRaplaLocale#formatDate(java.util.Date)
     */
    public String formatDate( Date date ) {
    	TimeZone timezone = zone;
        Locale locale = getLocale();
    	DateFormat format = DateFormat.getDateInstance( DateFormat.SHORT, locale );
		format.setTimeZone( timezone );
	    return format.format( date );
    }

    /* (non-Javadoc)
     * @see org.rapla.common.IRaplaLocale#formatDateLong(java.util.Date)
     */
    public String formatDateLong( Date date ) {
    	TimeZone timezone = zone;
        Locale locale = getLocale();
    	DateFormat format = DateFormat.getDateInstance( DateFormat.MEDIUM, locale );
		format.setTimeZone( timezone );
	    String dateFormat = format.format( date);
		return dateFormat + " (" + getWeekday(date) + ")";
    }
    

    /* (non-Javadoc)
     * @see org.rapla.common.IRaplaLocale#getWeekday(java.util.Date)
     */
    public String getWeekday( Date date ) {
    	TimeZone timeZone = getTimeZone();
        Locale locale = getLocale();
		SimpleDateFormat format = new SimpleDateFormat( "EE", locale );
		format.setTimeZone( timeZone );
	    return format.format( date );
    }




    /* (non-Javadoc)
     * @see org.rapla.common.IRaplaLocale#getTimeZone()
     */
    public TimeZone getTimeZone() {
        return zone;
    }
        
    /* (non-Javadoc)
     * @see org.rapla.common.IRaplaLocale#getLocale()
     */
    public Locale getLocale() {
        return localeSelector.getLocale();
    }
    
    public String getCharsetNonUtf()
    {
        return charsetForHtml;
    }

    
    /** formats the date and month in the selected locale and timeZone*/
    public String formatDateMonth(Date date ) {
//        DateWithoutTimezone date2 = DateTools.toDate( date.getTime());
//        return date2.month + "/" + date2.day;
        Locale locale = getLocale();
        FieldPosition fieldPosition = new FieldPosition( DateFormat.YEAR_FIELD );
        StringBuffer buf = new StringBuffer();
        DateFormat format = DateFormat.getDateInstance( DateFormat.SHORT, locale);
        buf = format.format(date,
                buf,
                fieldPosition
                );
        if ( fieldPosition.getEndIndex()<buf.length() ) {
            buf.delete( fieldPosition.getBeginIndex(), fieldPosition.getEndIndex()+1 );
        } else if ( (fieldPosition.getBeginIndex()>=0) ) {
            buf.delete( fieldPosition.getBeginIndex(), fieldPosition.getEndIndex() );
        }
        char lastChar = buf.charAt(buf.length()-1);
        if (lastChar == '/' || lastChar == '-' ) {
            String result = buf.substring(0,buf.length()-1);
            return result;
        } else {
            String result = buf.toString();
            return result;
        }
    }

    /** formats the day of week, date and month in the selected locale and timeZone*/
    public String formatDayOfWeekDateMonth(Date date) {
        //SimpleDateFormat format =  new SimpleDateFormat("EEE", locale);
        String datePart = getWeekday(date);
        String dateOfMonthPart = formatDateMonth( date  );
        return datePart + " " + dateOfMonthPart ;
    }


    public boolean isAmPmFormat() {
        Locale locale = getLocale();
        DateFormat format= DateFormat.getTimeInstance(DateFormat.SHORT, locale);
        FieldPosition amPmPos = new FieldPosition(DateFormat.AM_PM_FIELD);
        format.format(new Date(), new StringBuffer(),amPmPos);
        return (amPmPos.getEndIndex()>0);
    }

    public String formatMonthYear(Date date)
    {
        int year = DateTools.toDate( date.getTime()).year;
        String result = formatMonth( date ) + " " + year;
        return result;

    }
    
    public String getWeekdayName(int weekday)
    {
        TimeZone timeZone = getTimeZone();
        Locale locale = getLocale();
        SimpleDateFormat format = new SimpleDateFormat( "EEEEEE", locale );
        format.setTimeZone(timeZone);
        Calendar instance = Calendar.getInstance( timeZone);
        instance.set(Calendar.DAY_OF_WEEK, weekday);
        String result = format.format( instance.getTime());
//        String result;
//        switch (weekday)
//        {
//            case 1: result= "sunday";break;
//            case 2: result= "monday";break;
//            case 3: result= "tuesday";break;
//            case 4: result= "wednesday";break;
//            case 5: result= "thursday";break;
//            case 6: result= "friday";break;
//            case 7: result= "saturday";break;
//            default: throw new IllegalArgumentException("Weekday " + weekday + " not supported.");
//        }
        return result;
    }

    
    public String formatMonth( Date date ) {
        TimeZone timeZone = getTimeZone();
        Locale locale = getLocale();
        SimpleDateFormat format = new SimpleDateFormat( "MMMMM", locale );
        format.setTimeZone( timeZone );
        return format.format( date );
    }


    
//    public String formatMonth(Date date)
//    {    
//        int month = DateTools.toDate( date.getTime()).month;
//        String result;
//        switch (month)
//        {
//            case 0: result= "january";break;
//            case 1: result= "february";break;
//            case 2: result= "march";break;
//            case 3: result= "april";break;
//            case 4: result= "may";break;
//            case 5: result= "june";break;
//            case 6: result= "july";break;
//            case 7: result= "august";break;
//            case 8: result= "september";break;
//            case 9: result= "october";break;
//            case 10: result= "november";break;
//            case 11: result= "december";break;
//            default: throw new IllegalArgumentException("Month " + month + " not supported.");
//        }
//        return result;
//    }

    
    public String formatTime(int minuteOfDay) {
        boolean useAM_PM = isAmPmFormat();
        int minute = minuteOfDay%60;
        int hour = minuteOfDay/60;
        String displayedHour = "" + (useAM_PM ? hour %12 : hour);
        String displayedMinute = minute > 9 ? ""+ minute : "0"+minute ;
        String string = displayedHour + ":" + displayedMinute;
        if (useAM_PM ) {
            if ( hour >= 12)
            {
                string += " PM";
            }
            else
            {
                string += " AM";
            }
        }
        return string;
    }

    public String formatHour(int hour) {
        boolean useAM_PM = isAmPmFormat();
        String displayedHour = "" + (useAM_PM ? hour %12 : hour);
        String string = displayedHour;
        if (useAM_PM ) {
            if ( hour >= 12)
            {
                string += " PM";
            }
            else
            {
                string += " AM";
            }
        }
        return string;
    }



}


