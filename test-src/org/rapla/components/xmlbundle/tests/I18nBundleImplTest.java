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

import junit.framework.Test;
import junit.framework.TestSuite;

import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.components.xmlbundle.LocaleSelector;
import org.rapla.components.xmlbundle.impl.I18nBundleImpl;
import org.rapla.components.xmlbundle.impl.LocaleSelectorImpl;
import org.rapla.framework.Configuration;
import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaDefaultContext;
import org.rapla.framework.logger.ConsoleLogger;
import org.rapla.framework.logger.Logger;


public class I18nBundleImplTest extends AbstractI18nTest {
    I18nBundleImpl i18n;
    boolean useFile;
    LocaleSelector localeSelector;
    Logger logger = new ConsoleLogger(ConsoleLogger.LEVEL_WARN);

    public I18nBundleImplTest(String name) {
        this(name, true);
    }

    public I18nBundleImplTest(String name,boolean useFile) {
        super(name);
        this.useFile = useFile;
    }

    protected void setUp() throws Exception {
        DefaultConfiguration config = new DefaultConfiguration("i18n");
        if (this.useFile) {
            DefaultConfiguration child = new DefaultConfiguration("file");
            child.setValue("src/org/rapla/RaplaResources.xml");
            config.addChild(child);
        } else {
            config.setAttribute("id","org.rapla.RaplaResources");
        }
        i18n = create(config);
    }

    private I18nBundleImpl create(Configuration config) throws Exception {
        I18nBundleImpl i18n;
        RaplaDefaultContext context = new RaplaDefaultContext();
        localeSelector = new LocaleSelectorImpl();
        context.put(LocaleSelector.class,localeSelector);
        i18n = new I18nBundleImpl(context,config,new ConsoleLogger());
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
        suite.addTest(new I18nBundleImplTest("testLocaleChanged",false));
        suite.addTest(new I18nBundleImplTest("testGetIcon",false));
        suite.addTest(new I18nBundleImplTest("testGetString",false));
        suite.addTest(new I18nBundleImplTest("testLocale",false));
        /*
       */
        suite.addTest(new I18nBundleImplTest("testInvalidConfig",true));
        suite.addTest(new I18nBundleImplTest("testLocaleChanged",true));
        suite.addTest(new I18nBundleImplTest("testGetIcon",true));
        suite.addTest(new I18nBundleImplTest("testGetString",true));
        suite.addTest(new I18nBundleImplTest("testLocale",true));

        return suite;
    }

    public void testLocaleChanged() {
        localeSelector.setLocale(new Locale("de","DE"));
        assertEquals(getI18n().getString("cancel"),"Abbrechen");
        localeSelector.setLocale(new Locale("en","DE"));
        assertEquals(getI18n().getString("cancel"),"Cancel");
    }

    public void testInvalidConfig() throws Exception {
        if (!this.useFile)
            return;
        DefaultConfiguration config = new DefaultConfiguration("i18n");
        try {
            create(config);
            assertTrue("id is missing should be reported", true);
        } catch (Exception ex) {
        }
        config.setAttribute("id","org.rapla.RaplaResources");
        DefaultConfiguration child = new DefaultConfiguration("file");
        child.setValue("./src/org/rapla/RaplaResou");
        config.addChild( child );
        try {
            create(config);
            assertTrue("file ./src/org/rapla/RaplaResou should fail", true);
        } catch (Exception ex) {
        }
    }
}





