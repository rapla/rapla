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
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import javax.inject.Inject;

import org.rapla.components.i18n.BundleManager;
import org.rapla.components.util.DateTools;
import org.rapla.components.xmlbundle.LocaleSelector;
import org.rapla.components.xmlbundle.impl.LocaleSelectorImpl;
import org.rapla.framework.Configuration;
import org.rapla.framework.DefaultConfiguration;

public class RaplaLocaleImpl extends AbstractRaplaLocale  {
	

    String[] availableLanguages;

    String COUNTRY = "country";
    String LANGUAGES = "languages";
    String LANGUAGE = "language";
    String CHARSET = "charset";
    String charsetForHtml;
    private TimeZone importExportTimeZone;
    @Inject
    public RaplaLocaleImpl(BundleManager bundleManager)
    {
         super(bundleManager);
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
        importExportTimeZone = TimeZone.getDefault();
        charsetForHtml = "ISO-8859-1";
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
        return Calendar.getInstance(getTimeZone(), getLocale());
    }


    /* (non-Javadoc)
     * @see org.rapla.common.IRaplaLocale#formatNumber(long)
     */
    public String formatNumber( long number ) {
        Locale locale = getLocale();
		return NumberFormat.getInstance( locale).format(number );
    }

    protected String _format(Date date, final String pattern)
    {
        final SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
        simpleDateFormat.setTimeZone( DateTools.getTimeZone());
        final String format = simpleDateFormat.format(date);
        return format;
    }

    public String getCharsetNonUtf()
    {
        return charsetForHtml;
    }


}


