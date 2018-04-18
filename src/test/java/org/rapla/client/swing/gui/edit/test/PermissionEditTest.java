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

package org.rapla.client.swing.gui.edit.test;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.RaplaResources;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.dialog.swing.DialogUI.DialogUiFactory;
import org.rapla.client.internal.TreeItemFactory;
import org.rapla.client.swing.SwingSchedulerImpl;
import org.rapla.client.TreeFactory;
import org.rapla.client.swing.gui.tests.GUITestCase;
import org.rapla.client.swing.internal.RaplaDateRenderer;
import org.rapla.client.swing.internal.edit.RaplaListEdit.RaplaListEditFactory;
import org.rapla.client.swing.internal.edit.fields.DateField.DateFieldFactory;
import org.rapla.client.swing.internal.edit.fields.LongField.LongFieldFactory;
import org.rapla.client.swing.internal.edit.fields.PermissionField.PermissionFieldFactory;
import org.rapla.client.swing.internal.edit.fields.PermissionListField;
import org.rapla.client.internal.TreeFactoryImpl;
import org.rapla.client.swing.internal.view.TreeItemFactorySwing;
import org.rapla.components.calendar.DateRenderer;
import org.rapla.components.i18n.internal.AbstractBundleManager;
import org.rapla.components.i18n.server.ServerBundleManager;
import org.rapla.components.iolayer.DefaultIO;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Permission;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.internal.RaplaLocaleImpl;
import org.rapla.logger.Logger;
import org.rapla.scheduler.CommandScheduler;

import java.util.Collections;

@RunWith(JUnit4.class)
@Ignore
public final class PermissionEditTest extends GUITestCase
{
    @Test
    public void testMain() throws Exception {
        final Logger logger = getLogger();
        final AbstractBundleManager bundleManager = new ServerBundleManager();
        RaplaResources i18n = new RaplaResources(bundleManager);
        RaplaLocale raplaLocale = new RaplaLocaleImpl(bundleManager);
        IOInterface ioInterface = new DefaultIO(logger);
        ClientFacade facade = getFacade();
        CommandScheduler scheduler = new SwingSchedulerImpl(logger);
        DialogUiFactoryInterface dialogUiFactory = new DialogUiFactory(i18n,  scheduler,bundleManager,  logger );
        TreeItemFactory treeItemFactory = new TreeItemFactorySwing(i18n);
        TreeFactory treeFactory = new TreeFactoryImpl(getFacade(), i18n, getRaplaLocale(), getLogger(), treeItemFactory);
        DateRenderer dateRenderer = new RaplaDateRenderer(getFacade().getRaplaFacade(),  getRaplaLocale());
        RaplaListEditFactory raplaListEditFactory = new RaplaListEditFactory( i18n);
        DateFieldFactory dateFieldFactory = new DateFieldFactory(getFacade(), i18n, getRaplaLocale(), getLogger(), dateRenderer, ioInterface);
        LongFieldFactory longFieldFactory = new LongFieldFactory(facade, i18n, raplaLocale, logger, ioInterface);
        PermissionFieldFactory permissionFieldFactory = new PermissionFieldFactory(getFacade(), i18n, getRaplaLocale(), getLogger(), treeFactory,  dialogUiFactory, dateFieldFactory, longFieldFactory);
        PermissionListField editor = new PermissionListField(getFacade(), i18n, getRaplaLocale(), getLogger(),"permissions", raplaListEditFactory, permissionFieldFactory);
        Allocatable a = facade.getRaplaFacade().getAllocatablesWithFilter(null)[0];
        Allocatable r = facade.getRaplaFacade().edit( a );
        Permission p1 = r.newPermission();
        p1.setUser(facade.getRaplaFacade().getUsers()[0]);
        r.addPermission(p1);
        Permission p2 = r.newPermission();
        p2.setUser( facade.getRaplaFacade().getUsers()[1]);
        r.addPermission(p2);
        Permission p3 = r.newPermission();
        r.addPermission(p3);
        editor.mapFrom(Collections.singletonList(r));
        testComponent(editor.getComponent(),700,300);
        getLogger().info("Permission edit started");
    }


    public static void main(String[] args) {
        new PermissionEditTest().interactiveTest("testMain");
    }
}

