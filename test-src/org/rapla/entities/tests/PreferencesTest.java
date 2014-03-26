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

import junit.framework.Test;
import junit.framework.TestSuite;

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

public class PreferencesTest extends RaplaTestCase {
    CategoryImpl areas;
    ModificationModule modificationMod;
    QueryModule queryMod;
    UpdateModule updateMod;

    public PreferencesTest(String name) {
        super(name);
    }

    public static Test suite() {
        return new TestSuite(PreferencesTest.class);
    }

    protected void setUp() throws Exception {
        super.setUp();
        ClientFacade facade = getFacade();
        queryMod = facade;
        modificationMod = facade;
        updateMod = facade;
    }

    public void testLoad() throws Exception {
        Preferences preferences = queryMod.getPreferences();
        TypedComponentRole<RaplaConfiguration> SESSION_TEST = new TypedComponentRole<RaplaConfiguration>("org.rapla.SessionTest");
        Configuration config = preferences.getEntry(SESSION_TEST);
        assertEquals("testvalue",config.getAttribute("test"));
    }

    public void testStore() throws Exception {
        Preferences preferences = queryMod.getPreferences();
        Preferences clone  = modificationMod.edit(preferences);
        //Allocatable allocatable = queryMod.getAllocatables()[0];
        //Configuration config = queryMod.createReference((RaplaType)allocatable);
        //clone.putEntry("org.rapla.gui.weekview", config);
        modificationMod.store(clone);
        //assertEquals(allocatable, queryMod.resolve(config));
        updateMod.refresh();
    }

}





