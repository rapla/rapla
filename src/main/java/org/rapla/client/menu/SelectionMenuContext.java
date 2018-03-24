package org.rapla.client.menu;

import jsinterop.annotations.JsType;
import org.rapla.client.PopupContext;
import org.rapla.client.menu.MenuContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;

@JsType
public class SelectionMenuContext implements MenuContext {
    Collection<?> selectedObjects = Collections.EMPTY_LIST;
    Object focused;
    private final PopupContext popupContext;
    private Date selectedDate;

    public SelectionMenuContext(Object focusedObject, PopupContext popupContext) {this.focused = focusedObject;
        this.popupContext = popupContext;
        this.focused = focusedObject;
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
