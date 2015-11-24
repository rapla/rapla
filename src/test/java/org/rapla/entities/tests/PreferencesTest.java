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
package org.rapla.entities.tests;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.RaplaTestCase;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.entities.internal.CategoryImpl;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.ModificationModule;
import org.rapla.facade.QueryModule;
import org.rapla.facade.UpdateModule;
import org.rapla.framework.Configuration;
import org.rapla.framework.TypedComponentRole;
import org.rapla.framework.logger.RaplaBootstrapLogger;

@RunWith(JUnit4.class)
public class PreferencesTest  {
    CategoryImpl areas;
    ModificationModule modificationMod;
    QueryModule queryMod;
    UpdateModule updateMod;

    @Before
    public void setUp() throws Exception {
        ClientFacade facade = RaplaTestCase.createSimpleSimpsonsWithHomer();
        queryMod = facade;
        modificationMod = facade;
        updateMod = facade;
    }

    @Test
    public void testLoad() throws Exception {
        Preferences preferences = queryMod.getPreferences();
        TypedComponentRole<RaplaConfiguration> SESSION_TEST = new TypedComponentRole<RaplaConfiguration>("org.rapla.SessionTest");
        Configuration config = preferences.getEntry(SESSION_TEST);
        Assert.assertEquals("testvalue", config.getAttribute("test"));
    }

    @Test
    public void testStore() throws Exception {
        Preferences preferences = queryMod.getPreferences();
        Preferences clone  = modificationMod.edit(preferences);
        //Allocatable allocatable = queryMod.getAllocatables()[0];
        //Configuration config = queryMod.createReference((RaplaType)allocatable);
        //clone.putEntry("org.rapla.client.swing.gui.weekview", config);
        modificationMod.store(clone);
        //assertEquals(allocatable, queryMod.resolve(config));
        updateMod.refresh();
    }

}





