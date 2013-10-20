/*--------------------------------------------------------------------------*
 | Copyright (C) 2006  Christopher Kohlhaas                                 |
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

package org.rapla.gui.edit.test;
import java.util.Collections;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.rapla.client.ClientService;
import org.rapla.gui.internal.edit.CategoryEditUI;
import org.rapla.gui.tests.GUITestCase;

public final class CategoryEditTest extends GUITestCase
{
    public CategoryEditTest(String name) {
        super(name);
    }

    public static Test suite() {
        return new TestSuite(CategoryEditTest.class);
    }


    public void testMain() throws Exception {
        ClientService clientService = getClientService();
        CategoryEditUI editor = new CategoryEditUI( clientService.getContext(), false);
        editor.setObjects( Collections.singletonList(clientService.getFacade().getSuperCategory().getCategories()[0] ));
        testComponent(editor.getComponent(),600,500);
        getLogger().info("Category edit started");
    }


    public static void main(String[] args) {
        new CategoryEditTest(CategoryEditTest.class.getName()
                               ).interactiveTest("testMain");
    }
}

