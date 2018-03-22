/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas, Bettina Lademann                |
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
package org.rapla.components.calendarview;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.i18n.internal.AbstractBundleManager;
import org.rapla.components.i18n.server.ServerBundleManager;
import org.rapla.components.util.DateTools;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.internal.RaplaLocaleImpl;

import java.util.Calendar;

@RunWith(JUnit4.class)
public class WeekdayMapperTest   {
    String[] weekdayNames;
    int[] weekday2index;
    int[] index2weekday;



    @Before
    public  void setup()
    {



    }

    @Test
    public void testLocaleGermany() {
        BundleManager bundleManager = new ServerBundleManager();
        RaplaLocale raplaLocale =new RaplaLocaleImpl(bundleManager);
        WeekdayMapper mapper = new WeekdayMapper(raplaLocale, DateTools.MONDAY);
        Assert.assertEquals(6, mapper.indexForDay(Calendar.SUNDAY));
        Assert.assertEquals(0, mapper.indexForDay(Calendar.MONDAY));
        Assert.assertEquals(Calendar.SUNDAY, mapper.dayForIndex(6));
        Assert.assertEquals(Calendar.MONDAY, mapper.dayForIndex(0));
        Assert.assertEquals("Montag", mapper.getName(Calendar.MONDAY));
    }

    @Test
    public void testLocaleUS() {
        AbstractBundleManager bundleManager = new ServerBundleManager();
        bundleManager.setLanguage("en");
        bundleManager.setCountry("US");
        RaplaLocale raplaLocale =new RaplaLocaleImpl(bundleManager);
        WeekdayMapper mapper = new WeekdayMapper(raplaLocale, DateTools.SUNDAY);

        Assert.assertEquals(0, mapper.indexForDay(Calendar.SUNDAY));
        Assert.assertEquals(1, mapper.indexForDay(Calendar.MONDAY));
        Assert.assertEquals(Calendar.MONDAY, mapper.dayForIndex(1));
        Assert.assertEquals("Monday", mapper.getName(Calendar.MONDAY));
    }
    
}

