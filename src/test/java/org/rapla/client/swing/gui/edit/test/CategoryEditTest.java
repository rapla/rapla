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
import java.awt.Component;
import java.util.Collections;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.rapla.AppointmentFormaterImpl;
import org.rapla.RaplaResources;
import org.rapla.client.ClientService;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.internal.RaplaLocaleImpl;
import org.rapla.framework.logger.Logger;
import org.rapla.client.swing.internal.edit.CategoryEditUI;
import org.rapla.client.swing.internal.view.InfoFactoryImpl;
import org.rapla.client.swing.internal.view.TreeFactoryImpl;
import org.rapla.client.swing.toolkit.DialogUI;
import org.rapla.client.swing.toolkit.DialogUI.DialogUiFactory;
import org.rapla.client.swing.toolkit.FrameControllerList;
import org.rapla.components.i18n.server.ServerBundleManager;
import org.rapla.components.iolayer.DefaultIO;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.entities.domain.permission.DefaultPermissionControllerSupport;
import org.rapla.entities.domain.permission.PermissionController;
import org.rapla.facade.ClientFacade;
import org.rapla.client.swing.InfoFactory;
import org.rapla.client.swing.TreeFactory;
import org.rapla.client.swing.gui.tests.GUITestCase;
import org.rapla.client.swing.images.RaplaImages;

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
        RaplaContext context = null;
        final Logger logger = getLogger();
        final ServerBundleManager bundleManager = new ServerBundleManager();
        RaplaResources i18n = new RaplaResources(bundleManager);
        RaplaLocale raplaLocale = new RaplaLocaleImpl(bundleManager);
        AppointmentFormater appointmentFormater = new AppointmentFormaterImpl(i18n, raplaLocale);
        IOInterface ioInterface = new DefaultIO(logger);
        PermissionController permissionController = DefaultPermissionControllerSupport.getController();
        ClientFacade facade = getFacade();
        RaplaImages raplaImages = new RaplaImages(logger);
        FrameControllerList frameList = new FrameControllerList(logger);
        DialogUiFactory dialogUiFactory = new DialogUiFactory(i18n, raplaImages, bundleManager, frameList );
        InfoFactory<Component, DialogUI> infoFactory = new InfoFactoryImpl(context, appointmentFormater, ioInterface, permissionController, i18n, raplaLocale, facade, logger, raplaImages, dialogUiFactory);
        TreeFactory treeFactory = new TreeFactoryImpl(context, permissionController, infoFactory, raplaImages);
        CategoryEditUI editor = new CategoryEditUI( context, treeFactory, raplaImages, dialogUiFactory);
        editor.setObjects( Collections.singletonList(clientService.getFacade().getSuperCategory().getCategories()[0] ));
        testComponent(editor.getComponent(),600,500);
        getLogger().info("Category edit started");
    }


    public static void main(String[] args) {
        new CategoryEditTest(CategoryEditTest.class.getName()
                               ).interactiveTest("testMain");
    }
}

