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
package org.rapla.plugin.notification.client.swing;

import java.util.Collection;
import java.util.Locale;

import javax.inject.Inject;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.rapla.RaplaResources;
import org.rapla.client.extensionpoints.UserOptionPanel;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.internal.TreeAllocatableSelection;
import org.rapla.components.layout.TableLayout;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaMap;
import org.rapla.entities.domain.Allocatable;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.inject.Extension;
import org.rapla.plugin.notification.NotificationPlugin;
import org.rapla.plugin.notification.NotificationResources;

@Extension(provides = UserOptionPanel.class, id= NotificationPlugin.PLUGIN_ID)
public class NotificationOption extends RaplaGUIComponent implements UserOptionPanel {
    JPanel content= new JPanel();
    JCheckBox notifyIfOwnerCheckBox;
    TreeAllocatableSelection selection;
    Preferences preferences;
    NotificationResources notificationI18n;

    @Inject
    public NotificationOption(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, NotificationResources notificationI18n, TreeAllocatableSelection selection) {
        super(facade, i18n, raplaLocale, logger);
        this.notificationI18n = notificationI18n;
        this.selection = selection;
        selection.setAddDialogTitle(notificationI18n.getString("subscribe_notification"));
        double[][] sizes = new double[][] {
                {5,TableLayout.FILL,5}
                ,{TableLayout.PREFERRED,5,TableLayout.PREFERRED,5,TableLayout.FILL}
            };
        TableLayout tableLayout = new TableLayout(sizes);
        content.setLayout(tableLayout);
        content.add(new JLabel(getAsHTML(notificationI18n.getString("notification.option.description"))), "1,0");
      
        notifyIfOwnerCheckBox = new JCheckBox();
        content.add(notifyIfOwnerCheckBox, "1,2");
        
        notifyIfOwnerCheckBox.setText(getAsHTML(notificationI18n.getString("notify_if_owner")));
       
        content.add(selection.getComponent(), "1,4");
    }
    
    @Override
    public boolean isEnabled()
    {
        return true;// || preferences.getEntryAsBoolean( NotificationPlugin.NOTIFY_IF_OWNER_CONFIG, false);
    }

    /** calls "&lt;html&gt;" + getI18n().getString(key) + "&lt;/html&gt;"*/
    final public String getAsHTML(String string) {
        return "<html>" + string + "</html>";
    }


    public JComponent getComponent() {
        return content;
    }
    public String getName(Locale locale) {
        return notificationI18n.getString("notification_options");
    }

    public void show() throws RaplaException {
        boolean notify = preferences.getEntryAsBoolean( NotificationPlugin.NOTIFY_IF_OWNER_CONFIG, false);
        notifyIfOwnerCheckBox.setEnabled( false );
        notifyIfOwnerCheckBox.setSelected(notify);
        notifyIfOwnerCheckBox.setEnabled( true );
        RaplaMap<Allocatable> raplaEntityList = preferences.getEntry( NotificationPlugin.ALLOCATIONLISTENERS_CONFIG );
        if ( raplaEntityList != null ){
        	Collection<Allocatable> values = raplaEntityList.values();
            selection.setAllocatables(values);
        } 
    }


    public void setPreferences(Preferences preferences) {
        this.preferences = preferences;
    }

    public void commit() {
    	preferences.putEntry( NotificationPlugin.NOTIFY_IF_OWNER_CONFIG,  notifyIfOwnerCheckBox.isSelected());
        Collection<Allocatable> allocatables = selection.getAllocatables();
		preferences.putEntry( NotificationPlugin.ALLOCATIONLISTENERS_CONFIG ,getFacade().newRaplaMap( allocatables  ));
    }


    
}
