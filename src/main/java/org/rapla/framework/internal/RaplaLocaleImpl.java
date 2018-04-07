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

import org.rapla.components.i18n.BundleManager;
import org.rapla.components.util.IOUtil;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.text.Collator;
import java.text.NumberFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

@DefaultImplementation(of= RaplaLocale.class, context = {InjectionContext.swing,InjectionContext.server} )
@Singleton
public class RaplaLocaleImpl extends AbstractRaplaLocale  {

    String charsetForHtml;
    private TimeZone importExportTimeZone;
    @Inject
    public RaplaLocaleImpl(BundleManager bundleManager)
    {
        super(bundleManager);
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

    public TimeZone getTimeZone() {
        return IOUtil.getTimeZone();
    }


    public String formatNumber( Long number ) {
        Locale locale = getLocale();
		return NumberFormat.getInstance( locale).format(number );
    }

    public String getCharsetNonUtf()
    {
        return charsetForHtml;
    }

    @Override
    public Locale newLocale(String language, String country)
    {
        return new Locale(language, country != null ? country : "");
    }

    @Override
    public Comparator<String> getCollator()
    {
        return (Comparator<String>) (Comparator) Collator.getInstance(getLocale());
    }
}


