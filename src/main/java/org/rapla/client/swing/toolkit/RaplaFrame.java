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
package org.rapla.client.swing.toolkit;

import org.rapla.framework.RaplaException;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JFrame;
import javax.swing.JRootPane;
import java.awt.AWTEvent;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyVetoException;
import java.beans.VetoableChangeListener;
import java.util.ArrayList;

@Singleton
public class RaplaFrame extends JFrame
{
    private static final long serialVersionUID = 1L;
    
    ArrayList<VetoableChangeListener> listenerList = new ArrayList<>();
    /**
       This frame registers itself on the FrameControllerList on <code>contextualzize</code>
       and unregisters upon <code>dispose()</code>.
       Use addVetoableChangeListener() to get notified on a window-close event (and throw
       a veto if necessary.
     * @throws RaplaException
    */
    @Inject
    public RaplaFrame() {
        enableEvents(AWTEvent.WINDOW_EVENT_MASK);
        /*
        AWTAdapterFactory fact =
            AWTAdapterFactory.getFactory();
        if (fact != null) {
            fact.createFocusAdapter( this ).ignoreFocusComponents(new FocusTester() {
                    public boolean accept(ServerComponent component) {
                        return !(component instanceof HTMLView) ;
                    }
                });
        }*/
        final JRootPane rootPane2 = getRootPane();
        rootPane2.setGlassPane(new DisabledGlassPane());
    }

    public void busy(String text)
    {
        final DisabledGlassPane glassPane = (DisabledGlassPane) getRootPane().getGlassPane();
        glassPane.activate(text);
    }
    public void idle()
    {
        final DisabledGlassPane glassPane = (DisabledGlassPane) getRootPane().getGlassPane();
        glassPane.deactivate();
    }

    @Override
    protected void processWindowEvent(WindowEvent e) {
        final int id = e.getID();
        if (id == WindowEvent.WINDOW_CLOSING) {
            try {
                fireFrameClosing();
                close();
            } catch (PropertyVetoException ex) {
                return;
            }
        }
        super.processWindowEvent(e);
    }

    public void addVetoableChangeListener(VetoableChangeListener listener) {
        listenerList.add(listener);
    }

    public void removeVetoableChangeListener(VetoableChangeListener listener) {
        listenerList.remove(listener);
    }

    public VetoableChangeListener[] getVetoableChangeListeners() {
        return listenerList.toArray(new VetoableChangeListener[]{});
    }



    protected void fireFrameClosing() throws PropertyVetoException {
        if (listenerList.size() == 0)
            return;
        // The propterychange event indicates that the window
        // is closing.
        PropertyChangeEvent evt = new PropertyChangeEvent(
                                                          this
                                                          ,"visible"
                                                          ,new Boolean(true)
                                                          ,new Boolean(false)
                                                          )
            ;
        VetoableChangeListener[] listeners = getVetoableChangeListeners();
        for (int i = 0;i<listeners.length; i++) {
            listeners[i].vetoableChange(evt);
        }
    }

    public void dispose() {
        super.dispose();
    }

    public void close() {

        dispose();
    }

}
