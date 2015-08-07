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
import java.util.Locale;

import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.components.xmlbundle.LocaleSelector;
import org.rapla.components.xmlbundle.impl.I18nBundleImpl;
import org.rapla.components.xmlbundle.impl.LocaleSelectorImpl;
import org.rapla.framework.logger.ConsoleLogger;
import org.rapla.framework.logger.Logger;

import junit.framework.Test;
import junit.framework.TestSuite;


public class I18nBundleImplTest extends AbstractI18nTest {
    I18nBundleImpl i18n;
    LocaleSelector localeSelector;
    Logger logger = new ConsoleLogger(ConsoleLogger.LEVEL_WARN);

    public I18nBundleImplTest(String name) {
        super(name);
    }

    protected void setUp() throws Exception {
        String config = "org.rapla.RaplaResources";
        
        i18n = create(config);
    }

    private I18nBundleImpl create(String config) throws Exception {
        I18nBundleImpl i18n;
        localeSelector = new LocaleSelectorImpl();
        i18n = new I18nBundleImpl(localeSelector,new ConsoleLogger(), config);
        return i18n;
    }

    protected void tearDown() {
        i18n.dispose();
    }
    public I18nBundle getI18n() {
        return i18n;
    }

    public static Test suite() {
        TestSuite suite = new TestSuite();
        // The first four test only succeed if the Resource Bundles are build.
        suite.addTest(new I18nBundleImplTest("testLocaleChanged"));
        suite.addTest(new I18nBundleImplTest("testGetIcon"));
        suite.addTest(new I18nBundleImplTest("testGetString"));
        suite.addTest(new I18nBundleImplTest("testLocale"));
        return suite;
    }

    public void testLocaleChanged() {
        localeSelector.setLocale(new Locale("de","DE"));
        assertEquals(getI18n().getString("cancel"),"Abbrechen");
        localeSelector.setLocale(new Locale("en","DE"));
        assertEquals(getI18n().getString("cancel"),"Cancel");
    }

    public void testInvalidConfig() throws Exception {
        try {
            create("i18n");
        } catch (Exception ex) {
        }
    }
}





