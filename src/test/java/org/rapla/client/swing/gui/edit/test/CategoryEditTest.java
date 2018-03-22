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

import org.rapla.RaplaResources;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.dialog.swing.DialogUI.DialogUiFactory;
import org.rapla.client.swing.SwingSchedulerImpl;
import org.rapla.client.swing.gui.tests.GUITestCase;
import org.rapla.client.swing.internal.edit.CategoryEditUI;
import org.rapla.client.swing.internal.edit.fields.MultiLanguageField.MultiLanguageFieldFactory;
import org.rapla.client.swing.internal.edit.fields.TextField.TextFieldFactory;
import org.rapla.components.i18n.client.swing.SwingBundleManager;
import org.rapla.components.i18n.internal.AbstractBundleManager;
import org.rapla.components.iolayer.DefaultIO;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.internal.RaplaLocaleImpl;
import org.rapla.logger.Logger;
import org.rapla.scheduler.CommandScheduler;

import java.util.Collections;

public final class CategoryEditTest extends GUITestCase
{

    public void testMain() throws Exception {
        final Logger logger = getLogger();
        final AbstractBundleManager bundleManager = new SwingBundleManager(logger);
        RaplaResources i18n = new RaplaResources(bundleManager);
        RaplaLocale raplaLocale = new RaplaLocaleImpl(bundleManager);
        IOInterface ioInterface = new DefaultIO(logger);
        ClientFacade facade = getFacade();
        CommandScheduler scheduler = new SwingSchedulerImpl(logger);
        DialogUiFactoryInterface dialogUiFactory = new DialogUiFactory(i18n,  scheduler,bundleManager, logger );
        TextFieldFactory textField = new TextFieldFactory(facade, i18n, raplaLocale, logger, ioInterface);
        MultiLanguageFieldFactory multiLAnguageFieldFactoy = new MultiLanguageFieldFactory(facade, i18n, raplaLocale, logger,  dialogUiFactory, textField, ioInterface);
        TextFieldFactory longFieldFactory = new TextFieldFactory(facade, i18n, raplaLocale, logger, ioInterface);
        CategoryEditUI editor = new CategoryEditUI( getFacade(), i18n, getRaplaLocale(), getLogger(),  dialogUiFactory, multiLAnguageFieldFactoy, longFieldFactory);
        editor.setObjects( Collections.singletonList(getFacade().getRaplaFacade().getSuperCategory().getCategories()[0] ));
        testComponent(editor.getComponent(),600,500);
        getLogger().info("Category edit started");
    }


    public static void main(String[] args) {
        new CategoryEditTest().interactiveTest("testMain");
    }
}

