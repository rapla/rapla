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
package org.rapla.gui.toolkit;

import java.awt.Point;
import java.util.EventObject;

public class PopupEvent extends EventObject {
    private static final long serialVersionUID = 1L;
    
    Point m_point;
    Object m_selectedObject;

    public PopupEvent(Object source, Object selectedObject, Point p) {
        super(source);
        m_selectedObject = selectedObject;
        m_point = p;
    }
    public Object getSelectedObject() {
        return m_selectedObject;
    }

    public Point getPoint() {
        return m_point;
    }
}
