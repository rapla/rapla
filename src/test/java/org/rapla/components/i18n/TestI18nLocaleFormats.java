package org.rapla.components.i18n;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.rapla.components.i18n.internal.DefaultBundleManager;

import java.util.Locale;

public class TestI18nLocaleFormats
{
    DefaultBundleManager bundleManager;
    @Before
    public void setUp        (){
        this.bundleManager = bundleManager;
    }

    @Test
    public void testEncoding() throws Exception
    {
        final I18nLocaleFormats formats = bundleManager.getFormats(Locale.GERMANY);
        final String month = formats.getMonths()[2];
        Assert.assertEquals("M�rz", month);
    }



    //    @Test
    public void testJreLocales()
    {
        final Locale[] availableLocales = Locale.getAvailableLocales();
        for (Locale locale : availableLocales)
        {
            final String localeString = locale.toString();
            if (!localeString.trim().isEmpty())
            {
                try
                {
                    final I18nLocaleFormats format = bundleManager.getFormats(locale);
                    Assert.assertNotNull(format.getFormatDateLong());
                    Assert.assertNotNull(format.isAmPmFormat());
                    Assert.assertNotNull(format.getAmPmFormat());
                    Assert.assertNotNull(format.getFormatDateShort());
                    Assert.assertNotNull(format.getFormatHour());
                    Assert.assertNotNull(format.getFormatMonthYear());
                    Assert.assertNotNull(format.getFormatTime());
                    Assert.assertNotNull(format.getMonths());
                    Assert.assertNotNull(format.getWeekdays());
                }
                catch (Exception e)
                {
                    System.out.println("Missing properties for " + localeString);
                }
            }
        }

    }
}
