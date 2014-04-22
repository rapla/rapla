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
package org.rapla.gui.internal.edit.fields;

import java.util.ArrayList;

import javax.swing.JComponent;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.rapla.framework.RaplaContext;
import org.rapla.gui.EditField;
import org.rapla.gui.RaplaGUIComponent;

/** Base class for most rapla edit fields. Provides some mapping
    functionality such as reflection invocation of getters/setters.
    A fieldName "username" will result in a getUsername() and setUsername()
    method.
*/
public abstract class AbstractEditField extends RaplaGUIComponent
    implements EditField

{
    String fieldName;
    ArrayList<ChangeListener> listenerList = new ArrayList<ChangeListener>();

    public AbstractEditField(RaplaContext context) {
        super(context);
    }
    
    public void addChangeListener(ChangeListener listener) {
        listenerList.add(listener);
    }

    public void removeChangeListener(ChangeListener listener) {
        listenerList.remove(listener);
    }

    public ChangeListener[] getChangeListeners() {
        return listenerList.toArray(new ChangeListener[]{});
    }

    protected void fireContentChanged() {
        if (listenerList.size() == 0)
            return;
        ChangeEvent evt = new ChangeEvent(this);
        ChangeListener[] listeners = getChangeListeners();
        for (int i = 0;i<listeners.length; i++) {
            listeners[i].stateChanged(evt);
        }
    }

    abstract public JComponent getComponent();

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getFieldName() {
        return this.fieldName;
    }
}

