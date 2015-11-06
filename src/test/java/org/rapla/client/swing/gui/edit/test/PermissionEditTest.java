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
import java.util.Collections;

import org.rapla.AppointmentFormaterImpl;
import org.rapla.RaplaResources;
import org.rapla.client.UserClientService;
import org.rapla.client.swing.InfoFactory;
import org.rapla.client.swing.TreeFactory;
import org.rapla.client.swing.gui.tests.GUITestCase;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.RaplaDateRenderer;
import org.rapla.client.swing.internal.edit.RaplaListEdit.RaplaListEditFactory;
import org.rapla.client.swing.internal.edit.fields.DateField.DateFieldFactory;
import org.rapla.client.swing.internal.edit.fields.LongField.LongFieldFactory;
import org.rapla.client.swing.internal.edit.fields.PermissionField.PermissionFieldFactory;
import org.rapla.client.swing.internal.edit.fields.PermissionListField;
import org.rapla.client.swing.internal.view.InfoFactoryImpl;
import org.rapla.client.swing.internal.view.TreeFactoryImpl;
import org.rapla.client.swing.toolkit.DialogUI.DialogUiFactory;
import org.rapla.client.swing.toolkit.FrameControllerList;
import org.rapla.components.calendar.DateRenderer;
import org.rapla.components.i18n.server.ServerBundleManager;
import org.rapla.components.iolayer.DefaultIO;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.permission.DefaultPermissionControllerSupport;
import org.rapla.entities.domain.permission.PermissionController;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.internal.RaplaLocaleImpl;
import org.rapla.framework.logger.Logger;

import junit.framework.Test;
import junit.framework.TestSuite;

public final class PermissionEditTest extends GUITestCase
{
    public PermissionEditTest(String name) {
        super(name);
    }

    public static Test suite() {
        return new TestSuite(PermissionEditTest.class);
    }

    public void testMain() throws Exception {
        final Logger logger = getLogger();
        final ServerBundleManager bundleManager = new ServerBundleManager();
        RaplaResources i18n = new RaplaResources(bundleManager);
        RaplaLocale raplaLocale = new RaplaLocaleImpl(bundleManager);
        AppointmentFormater appointmentFormater = new AppointmentFormaterImpl(i18n, raplaLocale);
        IOInterface ioInterface = new DefaultIO(logger);
        PermissionController permissionController = DefaultPermissionControllerSupport.getController();
        ClientFacade facade = getFacade();
        final RaplaImages raplaImages = new RaplaImages(logger);
        FrameControllerList frameList = new FrameControllerList(logger);
        DialogUiFactory dialogUiFactory = new DialogUiFactory(i18n, raplaImages, bundleManager, frameList );
        InfoFactory infoFactory = new InfoFactoryImpl(getFacade(), i18n, getRaplaLocale(), getLogger(), appointmentFormater, ioInterface, permissionController, raplaImages, dialogUiFactory);
        TreeFactory treeFactory = new TreeFactoryImpl(getFacade(), i18n, getRaplaLocale(), getLogger(), permissionController, infoFactory, raplaImages);
        DateRenderer dateRenderer = new RaplaDateRenderer(getFacade(), i18n, getRaplaLocale(), getLogger());
        RaplaListEditFactory raplaListEditFactory = new RaplaListEditFactory(raplaImages);
        DateFieldFactory dateFieldFactory = new DateFieldFactory(getFacade(), i18n, getRaplaLocale(), getLogger(), dateRenderer, ioInterface);
        LongFieldFactory longFieldFactory = new LongFieldFactory(facade, i18n, raplaLocale, logger, ioInterface);
        PermissionFieldFactory permissionFieldFactory = new PermissionFieldFactory(getFacade(), i18n, getRaplaLocale(), getLogger(), treeFactory, raplaImages, dateRenderer, dialogUiFactory, dateFieldFactory, longFieldFactory);
        PermissionListField editor = new PermissionListField(getFacade(), i18n, getRaplaLocale(), getLogger(),"permissions", raplaListEditFactory, permissionFieldFactory);
        Allocatable a = facade.getAllocatables(null)[0];
        Allocatable r = facade.edit( a );
        Permission p1 = r.newPermission();
        p1.setUser(facade.getUsers()[0]);
        r.addPermission(p1);
        Permission p2 = r.newPermission();
        p2.setUser( facade.getUsers()[1]);
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

