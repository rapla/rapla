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
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Permission;
import org.rapla.gui.internal.edit.fields.PermissionListField;
import org.rapla.gui.tests.GUITestCase;

public final class PermissionEditTest extends GUITestCase
{
    public PermissionEditTest(String name) {
        super(name);
    }

    public static Test suite() {
        return new TestSuite(PermissionEditTest.class);
    }

    public void testMain() throws Exception {
        ClientService clientService = getClientService();
        PermissionListField editor = new PermissionListField(clientService.getContext(),"permissions");
        Allocatable a = clientService.getFacade().getAllocatables(null)[0];
        Allocatable r = clientService.getFacade().edit( a );
        Permission p1 = r.newPermission();
        p1.setUser(clientService.getFacade().getUsers()[0]);
        r.addPermission(p1);
        Permission p2 = r.newPermission();
        p2.setUser( clientService.getFacade().getUsers()[1]);
        r.addPermission(p2);
        Permission p3 = r.newPermission();
        r.addPermission(p3);
        editor.mapFrom(Collections.singletonList(r));
        testComponent(editor.getComponent(),700,300);
        getLogger().info("Permission edit started");
    }


    public static void main(String[] args) {
        new PermissionEditTest(PermissionEditTest.class.getName()
                               ).interactiveTest("testMain");
    }
}

