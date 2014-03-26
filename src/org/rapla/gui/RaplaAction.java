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
package org.rapla.gui;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.swing.Action;

import org.rapla.framework.RaplaContext;

public abstract class RaplaAction extends RaplaGUIComponent implements Action {
    private Map<String,Object> values = new HashMap<String,Object>();
    private ArrayList<PropertyChangeListener> listenerList = new ArrayList<PropertyChangeListener>();

    public RaplaAction(RaplaContext sm) {
        super( sm );
        setEnabled(true);
    }

    public Object getValue(String key) {
        return values.get(key);
    }
    public void putValue(String key,Object value) {
        Object oldValue = getValue(key);
        values.put(key,value);
        firePropertyChange(key,oldValue,value);
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        listenerList.add(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        listenerList.remove(listener);
    }

    protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
        if (listenerList.size() == 0)
            return;
        if (oldValue == newValue)
            return;

        //        if (oldValue != null && newValue != null && oldValue.equals(newValue))
        //return;

        PropertyChangeEvent evt = new PropertyChangeEvent(this,propertyName,oldValue,newValue);
        PropertyChangeListener[] listeners = getPropertyChangeListeners();
        for (int i = 0;i<listeners.length; i++) {
            listeners[i].propertyChange(evt);
        }
    }

    public PropertyChangeListener[] getPropertyChangeListeners() {
        return listenerList.toArray(new PropertyChangeListener[]{});
    }

    public void setEnabled(boolean enabled) {
        putValue("enabled", new Boolean(enabled));
    }

    public boolean isEnabled() {
        Boolean enabled = (Boolean)getValue("enabled");
        return (enabled != null && enabled.booleanValue());
    }

}
