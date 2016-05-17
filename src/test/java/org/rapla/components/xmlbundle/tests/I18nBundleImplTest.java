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
package org.rapla.components.xmlbundle.tests;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.components.i18n.internal.DefaultBundleManager;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.components.xmlbundle.impl.I18nBundleImpl;
import org.rapla.logger.ConsoleLogger;
import org.rapla.logger.Logger;

import java.util.Locale;


@RunWith(JUnit4.class)
public class I18nBundleImplTest extends AbstractI18nTest {
    I18nBundleImpl i18n;
    DefaultBundleManager localeSelector;
    Logger logger = new ConsoleLogger(ConsoleLogger.LEVEL_WARN);

    @Before
    public void setUp() throws Exception {
        String config = "org.rapla.RaplaResources";
        localeSelector = new DefaultBundleManager();
        i18n = new I18nBundleImpl(new ConsoleLogger(), config, localeSelector);
    }

    public I18nBundle getI18n() {
        return i18n;
    }


    @Test
    public void testLocaleChanged() {
        localeSelector.setLocale(new Locale("de","DE"));
        Assert.assertEquals(getI18n().getString("cancel"), "Abbrechen");
        localeSelector.setLocale(new Locale("en","DE"));
        Assert.assertEquals(getI18n().getString("cancel"), "Cancel");
    }

    @Test
    public void testInvalidConfig() throws Exception {
        try {
            String config = "invalid";
            i18n = new I18nBundleImpl(new ConsoleLogger(), config, new DefaultBundleManager());
        } catch (Exception ex) {
        }
    }
}





