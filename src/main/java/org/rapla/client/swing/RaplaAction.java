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

import org.rapla.RaplaResources;
import org.rapla.client.menu.IdentifiableMenuEntry;
import org.rapla.client.menu.MenuInterface;
import org.rapla.client.menu.MenuItemFactory;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.components.i18n.I18nIcon;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public abstract class RaplaAction extends RaplaGUIComponent implements Action {
    private Map<String,Object> values = new HashMap<>();
    private ArrayList<PropertyChangeListener> listenerList = new ArrayList<>();
    I18nIcon icon;

    public RaplaAction(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger)
    {
        super(facade, i18n, raplaLocale, logger);
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

    public void setIcon(I18nIcon icon) {
        this.icon = icon;
        putValue(SMALL_ICON , RaplaImages.getIcon( icon));
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

    public String getName()
    {
        return (String) getValue(NAME);
    }

    public I18nIcon getIcon()
    {
        return icon;
    }

    public RaplaAction addTo(MenuInterface menu, MenuItemFactory menuItemFactory) {
        final IdentifiableMenuEntry menuItem = menuItemFactory.createMenuItem(getName(), getIcon(), (context) -> actionPerformed());
        menu.addMenuItem(menuItem);
        return this;
    }
}
