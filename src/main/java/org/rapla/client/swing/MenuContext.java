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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;

import org.rapla.client.PopupContext;

public class MenuContext
{
    Collection<?> selectedObjects = Collections.EMPTY_LIST;
    Object focused;
    private final PopupContext popupContext;

    private Date selectedDate;
    
    public MenuContext( Object focusedObject) {
        this(  focusedObject, null );
    }

    public MenuContext(  Object focusedObject, PopupContext popupContext) {this.focused = focusedObject;
        this.popupContext = popupContext;
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
}









