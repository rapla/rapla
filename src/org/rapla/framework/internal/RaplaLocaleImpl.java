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

import org.rapla.components.util.DateTools;
import org.rapla.components.xmlbundle.LocaleSelector;
import org.rapla.components.xmlbundle.impl.LocaleSelectorImpl;
import org.rapla.framework.Configuration;
import org.rapla.framework.logger.Logger;

public class RaplaLocaleImpl extends AbstractRaplaLocale  {
	
	TimeZone zone;
    TimeZone systemTimezone;
    TimeZone importExportTimeZone;

	LocaleSelector localeSelector = new LocaleSelectorImpl();

    String[] availableLanguages;

    String COUNTRY = "country";
    String LANGUAGES = "languages";
    String LANGUAGE = "language";
    String CHARSET = "charset";
    String charsetForHtml;

	public RaplaLocaleImpl(Configuration config,Logger logger )  
    {
		String selectedCountry = config.getChild( COUNTRY).getValue(Locale.getDefault().getCountry() );
        Configuration languageConfig = config.getChild( LANGUAGES );
        Configuration[] languages = languageConfig.getChildren( LANGUAGE );
        charsetForHtml = config.getChild(CHARSET).getValue("iso-8859-15");
        availableLanguages = new String[languages.length];
        for ( int i=0;i<languages.length;i++ ) {
            availableLanguages[i] = languages[i].getValue("en");
        }
        String selectedLanguage = languageConfig.getAttribute( "default", Locale.getDefault().getLanguage() );
        if (selectedLanguage.trim().length() == 0)
            selectedLanguage = Locale.getDefault().getLanguage();
        localeSelector.setLocale( new Locale(selectedLanguage, selectedCountry) );
        zone = DateTools.getTimeZone();
        systemTimezone = TimeZone.getDefault();
        importExportTimeZone = systemTimezone;
        logger.info("Configured Locale= " + getLocaleSelector().getLocale().toString());
    }

    public TimeZone getSystemTimeZone() {
		return systemTimezone;
	}

	public LocaleSelector getLocaleSelector() {
        return localeSelector;
    }
	
	public Date fromUTCTimestamp(Date date)
	{
		Date raplaTime = toRaplaTime( importExportTimeZone,date );
		return raplaTime;
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
    
    @Deprecated
    public String formatTimestamp( Date date, TimeZone timezone ) 
    {
    	Locale locale = getLocale();
        StringBuffer buf = new StringBuffer();
		{
    		DateFormat format = DateFormat.getDateInstance( DateFormat.SHORT, locale );
    		format.setTimeZone( timezone );
    		String formatDate= format.format( date );
			buf.append( formatDate);
        }
        buf.append(" ");
        {
    		DateFormat format = DateFormat.getTimeInstance( DateFormat.SHORT, locale );
    		format.setTimeZone( timezone );
    		String formatTime = format.format( date );
			buf.append( formatTime);
        }
        return buf.toString();
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
     * @see org.rapla.common.IRaplaLocale#getMonth(java.util.Date)
     */
    public String getMonth( Date date ) {
    	TimeZone timeZone = getTimeZone();
        Locale locale = getLocale();
		SimpleDateFormat format = new SimpleDateFormat( "MMMMM", locale );
		format.setTimeZone( timeZone );
	    return format.format( date );
    }



    /* (non-Javadoc)
     * @see org.rapla.common.IRaplaLocale#getTimeZone()
     */
    public TimeZone getTimeZone() {
        return zone;
    }
    
    public TimeZone getImportExportTimeZone() {
		return importExportTimeZone;
	}

	public void setImportExportTimeZone(TimeZone importExportTimeZone) {
		this.importExportTimeZone = importExportTimeZone;
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

	


}


