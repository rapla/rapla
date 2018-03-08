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
package org.rapla.client.swing.gui.tests;

import org.rapla.client.PopupContext;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.toolkit.ErrorDialog;
import org.rapla.client.swing.toolkit.FrameController;
import org.rapla.client.swing.toolkit.FrameControllerList;
import org.rapla.client.swing.toolkit.FrameControllerListener;
import org.rapla.client.swing.toolkit.RaplaFrame;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;

import javax.swing.JComponent;
import java.awt.BorderLayout;
import java.util.concurrent.Semaphore;

public abstract class GUITestCase  {

    Logger logger;
    ClientFacade facade;
    RaplaLocale raplaLocale;

    protected <T> T getService(Class<T> role) throws RaplaException {
           return null;
    }

    public Logger getLogger()
    {
        return logger;
    }

    public ClientFacade getFacade()
    {
        return facade;
    }

    public RaplaLocale getRaplaLocale()
    {
        return raplaLocale;
    }

    protected PopupContext createPopupContext()
    {
        return new SwingPopupContext( null, null);
    }


    public void interactiveTest(String methodName) {
        try {
            ErrorDialog.THROW_ERROR_DIALOG_EXCEPTION = false;
            try {
                this.getClass().getMethod(methodName, new Class[] {}).invoke(this);
                waitUntilLastFrameClosed( getService(FrameControllerList.class) );
                System.exit(0);
            } catch (Exception ex) {
                logger.error(ex.getMessage(), ex);
                System.exit(1);
            } finally {
                //tearDown();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }

    /** Waits until the last window is closed.
        Every window should register and unregister on the FrameControllerList.
        @see org.rapla.client.swing.toolkit.RaplaFrame
     */
    public static void waitUntilLastFrameClosed(FrameControllerList list) throws InterruptedException {
        MyFrameControllerListener listener = new MyFrameControllerListener();
        list.addFrameControllerListener(listener);
        listener.waitUntilClosed();
    }

    static class MyFrameControllerListener implements FrameControllerListener {
        Semaphore mutex;

        MyFrameControllerListener() {
            mutex = new Semaphore(1);
        }
        public void waitUntilClosed() throws InterruptedException {
            mutex.acquire();
            // we wait for the mutex to be released
            mutex.acquire();
            mutex.release();
        }

        public void frameClosed(FrameController frame) {
        }
        public void listEmpty() {
            mutex.release();
        }
    }

    /** createInfoDialog a frame of size x,y and place the panel inside the Frame.
        Use this method for testing new GUI-Components.
     */
    public void testComponent(JComponent component,int x,int y) throws Exception{
        RaplaFrame frame = new RaplaFrame();
        frame.getContentPane().setLayout(new BorderLayout());
        frame.getContentPane().add(component, BorderLayout.CENTER);
        frame.setSize(x,y);
        frame.setVisible(true);
    }



}





