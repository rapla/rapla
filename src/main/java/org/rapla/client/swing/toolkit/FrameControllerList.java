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

import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Stack;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.rapla.components.util.Assert;
import org.rapla.components.util.Tools;
import org.rapla.logger.Logger;

/**All rapla-windows are registered on the FrameControllerList.
   The FrameControllerList is responsible for positioning the windows
   and closing all open windows on exit.
*/
@Singleton
final public class FrameControllerList {
    private Stack<FrameController> openFrameController = new Stack<FrameController>();
    private Window mainWindow = null;
    Point center;
    Logger logger = null;
    ArrayList<FrameControllerListener> listenerList = new ArrayList<FrameControllerListener>();

    @Inject
    public FrameControllerList(Logger logger) 
    {
        this.logger = logger;
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        center = new Point(screenSize.width / 2
                               ,screenSize.height / 2);
    }

    protected Logger getLogger() {
        return logger;
    }

    /** the center will be used by the
        <code>centerWindow()</code> function. */
    public void setCenter(Container window) {
        center.x = window.getLocationOnScreen().x +  window.getSize().width/2;
        center.y = window.getLocationOnScreen().y +  window.getSize().height/2;
    }

    /** the center will be used by the
        <code>centerWindow(Window)</code> function.
        @see #centerWindow(Window)
    */
    public void setCenter(Point center) {
        this.center = center;
    }

    /** the main-window will be used by the
        <code>placeRelativeToMain(Window)</code> function.
        @see #placeRelativeToMain(Window)
    */
    public void setMainWindow(Window window) {
        this.mainWindow = window;
    }

    public Window getMainWindow() {
        return mainWindow;
    }

    /** places the window relative to the main-window if set.
        Otherwise the the <code>centerWindow(Window)</code> method is called.
        @param newWindow the window to place
     */
    public void placeRelativeToMain(Window newWindow) {
        if (getLogger() != null && getLogger().isDebugEnabled() && mainWindow != null)
            getLogger().debug("placeRelativeToMainWindow(" + Tools.left(mainWindow.toString(),60) + ")");
        if (mainWindow ==null)
            centerWindow(newWindow);
        else
            placeRelativeToWindow(newWindow,mainWindow);
    }

    /** adds a window to the FrameControllerList */
    synchronized public void add(FrameController c) {
        Assert.notNull(c);
        Assert.isTrue(!openFrameController.contains(c),"Duplicated Entries are not allowed");
        openFrameController.add(c);
    }

    /** removes a window from the FrameControllerList */
    public void remove(FrameController c) {
        openFrameController.remove(c);
        String s = c.toString();
        if (getLogger() != null && getLogger().isDebugEnabled())
            getLogger().debug("Frame closed " + Tools.left(s,60) + "...");
        fireFrameClosed(c);
        if (openFrameController.size() == 0)
            fireListEmpty();
    }

    /** closes all windows registered on the FrameControllerList */
    public void closeAll() {
        while (!openFrameController.empty()) {
            FrameController c = openFrameController.peek();
            int size = openFrameController.size();
            c.close();
            if ( size <= openFrameController.size())
                getLogger().error("removeFrameController() not called in close() in " + c);
        }
    }
    
	public void setCursor(Cursor cursor) {
		FrameController[] anArray = openFrameController.toArray( new FrameController[] {});
		for ( FrameController frame:anArray)
		{
			frame.setCursor(cursor);
		}
		
	}


    public void addFrameControllerListener(FrameControllerListener listener) {
        listenerList.add(listener);
    }

    public void removeFrameControllerListener(FrameControllerListener listener) {
        listenerList.remove(listener);
    }
    public FrameControllerListener[] getFrameControllerListeners() {
        synchronized(listenerList) {
            return listenerList.toArray(new FrameControllerListener[]{});
        }
    }

    protected void fireFrameClosed(FrameController controller) {
        if (listenerList.size() == 0)
            return;
        FrameControllerListener[] listeners = getFrameControllerListeners();
        for (int i = 0;i<listeners.length;i++) {
            listeners[i].frameClosed(controller);
        }
    }

    protected void fireListEmpty() {
        if (listenerList.size() == 0)
            return;
        FrameControllerListener[] listeners = getFrameControllerListeners();
        for (int i = 0;i<listeners.length;i++) {
            listeners[i].listEmpty();
        }
    }


    /** centers the window around the specified center */
    public void centerWindow(Window window) {
        Dimension preferredSize = window.getSize();
        int x = center.x - (preferredSize.width / 2);
        int y = center.y - (preferredSize.height / 2);
        fitIntoScreen(x,y,window);
    }

    /** centers the window around the specified center */
    static public void centerWindowOnScreen(Window window) {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        Dimension preferredSize = window.getSize();
        int x = screenSize.width/2 - (preferredSize.width / 2);
        int y = screenSize.height/2 - (preferredSize.height / 2);
        fitIntoScreen(x,y,window);
    }

    /** Tries to place the window, that it fits into the screen. */
    static public void fitIntoScreen(int x, int y, Component window) {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        Dimension windowSize = window.getSize();
        if (x + windowSize.width > screenSize.width)
            x =  screenSize.width - windowSize.width;

        if (y + windowSize.height > screenSize.height)
            y =  screenSize.height - windowSize.height;

        if (x<0) x = 0;
        if (y<0) y = 0;
        window.setLocation(x,y);
    }

    /** places the window relative to the owner-window.
        The newWindow will be placed in the middle of the owner-window.
        @param newWindow the window to place
        @param owner the window to place into
     */
    public static void placeRelativeToWindow(Window newWindow,Window owner) {
        placeRelativeToComponent(newWindow,owner,null);
    }

    public static void placeRelativeToComponent(Window newWindow,Component component,Point point) {
        if (component == null)
            return;
        Dimension dlgSize = newWindow.getSize();
        Dimension parentSize = component.getSize();
        Point loc = component.getLocationOnScreen();

        if (point != null) {
            int x = loc.x + point.x - (dlgSize.width) / 2;
            int y = loc.y + point.y - ((dlgSize.height) * 2) / 3;
            //System.out.println (loc + ",  " + point + " x: " + x + " y: " + y);
            fitIntoScreen(x,y,newWindow);
        } else {
            int x = (parentSize.width - dlgSize.width) / 2 + loc.x;
            int y = loc.y + 10;
            fitIntoScreen(x,y,newWindow);
        }
    }


}














