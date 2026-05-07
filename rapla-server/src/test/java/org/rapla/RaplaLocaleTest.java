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

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.components.i18n.internal.AbstractBundleManager;
import org.rapla.components.i18n.server.ServerBundleManager;
import org.rapla.components.util.ParseDateException;
import org.rapla.components.util.SerializableDateTimeFormat;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.internal.RaplaLocaleImpl;

import java.util.Date;

@RunWith(JUnit4.class)
public class RaplaLocaleTest 
{
    @Test
    public void testDateFormatDe() throws Exception
    {
        final AbstractBundleManager bundleManager = new ServerBundleManager();
        bundleManager.setCountry("de");
        bundleManager.setLanguage("DE");
        RaplaLocale raplaLocale = new RaplaLocaleImpl(bundleManager);
        final Date date = new SerializableDateTimeFormat().parseDate("2001-01-12", false);
        {
            String s = raplaLocale.formatDate(date);
            Assert.assertEquals("12.01.01", s);
        }
        {
            String s = raplaLocale.formatDateLong(date);
            Assert.assertEquals("12.01.2001", s);
        }
    }

    @Test
    public void testDateFormatUs() throws Exception
    {
        final AbstractBundleManager bundleManager = new ServerBundleManager();
        bundleManager.setCountry("US");
        bundleManager.setLanguage("en");
        RaplaLocale raplaLocale = new RaplaLocaleImpl(bundleManager);
        final Date date = new SerializableDateTimeFormat().parseDate("2001-01-12", false);
        {
            String s = raplaLocale.formatDate(date);
            Assert.assertEquals("1/12/01", s);
        }
        {
            String s = raplaLocale.formatDateLong(date);
            Assert.assertEquals("Jan 12, 2001", s);
        }
    }

    @Test
    public void testTimeFormatUs() throws ParseDateException
    {
        final AbstractBundleManager bundleManager = new ServerBundleManager();
        bundleManager.setCountry("us");
        bundleManager.setLanguage("en");
        RaplaLocale raplaLocale = new RaplaLocaleImpl(bundleManager);
        final Date time = new SerializableDateTimeFormat().parseTime("21:00:00");
        String s = raplaLocale.formatTime(time);
        Assert.assertEquals("9:00 PM", s);
    }

    @Test
    public void testTimeFormatDe() throws ParseDateException
    {
        final AbstractBundleManager bundleManager = new ServerBundleManager();
        bundleManager.setCountry("DE");
        bundleManager.setLanguage("de");
        RaplaLocale raplaLocale = new RaplaLocaleImpl(bundleManager);
        final Date time = new SerializableDateTimeFormat().parseTime("21:00:00");
        String s = raplaLocale.formatTime(time);
        Assert.assertEquals("21:00", s);
    }

}
