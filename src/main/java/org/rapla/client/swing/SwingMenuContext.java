/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas, Bettina Lademann                |
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
package org.rapla.client.swing;

import org.rapla.client.MenuContext;
import org.rapla.client.PopupContext;

import javax.swing.JComponent;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;

public class SwingMenuContext implements MenuContext
{
    Collection<?> selectedObjects = Collections.EMPTY_LIST;
    Object focused;
    private final PopupContext popupContext;

    private Date selectedDate;
    private JComponent component;
    private Point point;
    
    public SwingMenuContext( Object focusedObject) {
        this(  focusedObject, null, null, null );
    }

    public SwingMenuContext(  Object focusedObject, PopupContext popupContext, JComponent component, Point point) {this.focused = focusedObject;
        this.popupContext = popupContext;
        this.component = component;
        this.point = point;
    }

    public void setSelectedObjects(Collection<?> selectedObjects) {
        this.selectedObjects= new ArrayList<Object>(selectedObjects);
    }

    public Collection<?> getSelectedObjects() {
        return selectedObjects;
    }

    public PopupContext getPopupContext()
    {
        return popupContext;
    }

    public Object getFocusedObject() {
        return  focused;
    }

    public void setSelectedDate(Date selectedDate)
    {
        this.selectedDate = selectedDate;
    }

    public Date getSelectedDate()
    {
        return selectedDate;
    }
    
    public Point getPoint()
    {
        return point;
    }

    public JComponent getComponent()
    {
        return component;
    }
}









