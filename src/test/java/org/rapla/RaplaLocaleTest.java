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
package org.rapla;

import java.util.Calendar;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.components.i18n.internal.DefaultBundleManager;
import org.rapla.components.util.SerializableDateTimeFormat;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.internal.RaplaLocaleImpl;

@RunWith(JUnit4.class)
public class RaplaLocaleTest 
{
    @Test
    public void testDateFormat3() throws Exception
    {
        RaplaLocale raplaLocale = new RaplaLocaleImpl(new DefaultBundleManager());
        String s = raplaLocale.formatDate(new SerializableDateTimeFormat().parseDate("2001-01-12", false));
        Assert.assertEquals("12.01.01", s);
    }

    @Test
    public void testTimeFormat4()
    {
        final DefaultBundleManager bundleManager = new DefaultBundleManager();
        bundleManager.setCountry("us");
        bundleManager.setLanguage("en");
        RaplaLocale raplaLocale = new RaplaLocaleImpl(bundleManager);
        Calendar cal = Calendar.getInstance(raplaLocale.getTimeZone(), raplaLocale.getLocale());
        cal.set(Calendar.HOUR_OF_DAY, 21);
        cal.set(Calendar.MINUTE, 0);
        String s = raplaLocale.formatTime(cal.getTime());
        Assert.assertEquals("9:00 PM", s);
    }

}
