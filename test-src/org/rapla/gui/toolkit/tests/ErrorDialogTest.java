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
package org.rapla.gui.toolkit.tests;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.rapla.gui.tests.GUITestCase;
import org.rapla.gui.toolkit.ErrorDialog;

public class ErrorDialogTest extends GUITestCase {

    public ErrorDialogTest(String name) {
        super(name);
    }

    public static Test suite() {
        return new TestSuite(ErrorDialogTest.class);
    }

    public void testError() throws Exception {
        ErrorDialog.THROW_ERROR_DIALOG_EXCEPTION = false;
        ErrorDialog dialog = new ErrorDialog(getContext());
        dialog.show("This is a very long sample error-text for our error-message-displaying-test"
                        + " it should be wrapped so that the whole text is diplayed.");
    }

    public static void main(String[] args) {
        new ErrorDialogTest("ErrorDialogTest").interactiveTest("testError");
    }


}










