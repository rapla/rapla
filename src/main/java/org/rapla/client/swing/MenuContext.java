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

import org.rapla.client.PopupContext;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaDefaultContext;

public class MenuContext extends RaplaDefaultContext
{
    Collection<?> selectedObjects = Collections.EMPTY_LIST;
    Object focused;
    private final PopupContext popupContext;
    
    public MenuContext(RaplaContext parentContext, Object focusedObject) {
        this( parentContext, focusedObject, null );
    }

    public MenuContext(RaplaContext parentContext,  Object focusedObject, PopupContext popupContext) {
        super( parentContext);
        this.focused = focusedObject;
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

}









