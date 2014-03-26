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
package org.rapla.gui.tests;

import java.awt.BorderLayout;
import java.util.concurrent.Semaphore;

import javax.swing.JComponent;

import org.rapla.RaplaTestCase;
import org.rapla.framework.RaplaException;
import org.rapla.gui.toolkit.ErrorDialog;
import org.rapla.gui.toolkit.FrameController;
import org.rapla.gui.toolkit.FrameControllerList;
import org.rapla.gui.toolkit.FrameControllerListener;
import org.rapla.gui.toolkit.RaplaFrame;

public abstract class GUITestCase extends RaplaTestCase {
    
    public GUITestCase(String name)
    {
        super(name);
    }
    
    protected <T> T getService(Class<T> role) throws RaplaException {
           return getClientService().getContext().lookup( role);
       }

    public void interactiveTest(String methodName) {
        try {
            setUp();
            ErrorDialog.THROW_ERROR_DIALOG_EXCEPTION = false;
            try {
                this.getClass().getMethod(methodName, new Class[] {}).invoke(this,new Object[] {});
                waitUntilLastFrameClosed( getClientService().getContext().lookup(FrameControllerList.class) );
                System.exit(0);
            } catch (Exception ex) {
                getLogger().error(ex.getMessage(), ex);
                System.exit(1);
            } finally {
                tearDown();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }

    /** Waits until the last window is closed.
        Every window should register and unregister on the FrameControllerList.
        @see org.rapla.gui.toolkit.RaplaFrame
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

    /** create a frame of size x,y and place the panel inside the Frame.
        Use this method for testing new GUI-Components.
     */
    public void testComponent(JComponent component,int x,int y) throws Exception{
        RaplaFrame frame = new RaplaFrame(getClientService().getContext());
        frame.getContentPane().setLayout(new BorderLayout());
        frame.getContentPane().add(component, BorderLayout.CENTER);
        frame.setSize(x,y);
        frame.setVisible(true);
    }



}





