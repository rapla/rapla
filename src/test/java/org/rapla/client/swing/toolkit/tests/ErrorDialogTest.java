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
package org.rapla.client.swing.toolkit.tests;

import org.rapla.RaplaResources;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.dialog.swing.DialogUI.DialogUiFactory;
import org.rapla.client.swing.gui.tests.GUITestCase;
import org.rapla.client.swing.toolkit.ErrorDialog;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.i18n.server.ServerBundleManager;
import org.rapla.framework.internal.DefaultScheduler;
import org.rapla.logger.Logger;
import org.rapla.scheduler.CommandScheduler;

public class ErrorDialogTest extends GUITestCase {

    public void testError() throws Exception {
        ErrorDialog.THROW_ERROR_DIALOG_EXCEPTION = false;
        final Logger logger = getLogger();
        BundleManager bundleManager = new ServerBundleManager();
        RaplaResources i18n = new RaplaResources(bundleManager);
        RaplaResources raplaResources = new RaplaResources(bundleManager);
        CommandScheduler scheduler = new DefaultScheduler(logger);
        DialogUiFactoryInterface dialogUiFactory = new DialogUiFactory(i18n, scheduler, bundleManager, logger );
        ErrorDialog dialog = new ErrorDialog(logger, raplaResources,  dialogUiFactory);
        dialog.show("This is a very long sample error-text for our error-message-displaying-test"
                        + " it should be wrapped so that the whole text is diplayed.");
    }

    public static void main(String[] args) {
        new ErrorDialogTest().interactiveTest("testError");
    }


}










