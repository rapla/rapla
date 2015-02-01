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
package org.rapla.plugin.notification;

import java.util.Collection;
import java.util.Locale;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.rapla.components.layout.TableLayout;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaMap;
import org.rapla.entities.domain.Allocatable;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.OptionPanel;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.gui.internal.TreeAllocatableSelection;

public class NotificationOption extends RaplaGUIComponent implements OptionPanel {
    JPanel content= new JPanel();
    JCheckBox notifyIfOwnerCheckBox;
    TreeAllocatableSelection selection;
    Preferences preferences;
    
    public NotificationOption(RaplaContext sm) {
        super( sm);
        setChildBundleName( NotificationPlugin.RESOURCE_FILE);
        selection = new TreeAllocatableSelection(sm);
        selection.setAddDialogTitle(getString("subscribe_notification"));
        double[][] sizes = new double[][] {
                {5,TableLayout.FILL,5}
                ,{TableLayout.PREFERRED,5,TableLayout.PREFERRED,5,TableLayout.FILL}
            };
        TableLayout tableLayout = new TableLayout(sizes);
        content.setLayout(tableLayout);
        content.add(new JLabel(getStringAsHTML("notification.option.description")), "1,0");
      
        notifyIfOwnerCheckBox = new JCheckBox();
        content.add(notifyIfOwnerCheckBox, "1,2");
        
        notifyIfOwnerCheckBox.setText(getStringAsHTML("notify_if_owner"));
       
        content.add(selection.getComponent(), "1,4");
    }
    
    public JComponent getComponent() {
        return content;
    }
    public String getName(Locale locale) {
        return getString("notification_options");
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
		preferences.putEntry( NotificationPlugin.ALLOCATIONLISTENERS_CONFIG ,getModification().newRaplaMap( allocatables  ));
    }


    
}
