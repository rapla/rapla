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
package org.rapla.gui.internal.action;
import java.awt.event.ActionEvent;

import javax.swing.JMenuItem;

import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;
import org.rapla.gui.RaplaAction;
import org.rapla.gui.toolkit.RaplaMenuItem;

public class SaveableToggleAction extends RaplaAction {
	
   TypedComponentRole<Boolean> configEntry;
   String name;
   
   public SaveableToggleAction(RaplaContext context,String name,TypedComponentRole<Boolean> configEntry)  
   {
        super( context );
        this.name = name;
        putValue( NAME, getString( name));
        this.configEntry = configEntry;
        //putValue(SMALL_ICON,getIcon("icon.unchecked"));
    }
   
   public RaplaMenuItem createMenuItem() throws RaplaException
   {
	   RaplaMenuItem menu = new RaplaMenuItem(name);
       menu.setAction( this);
       final User user = getUser();
       final Preferences preferences = getQuery().getPreferences( user );
       boolean selected = preferences.getEntryAsBoolean( configEntry , true);
       if(selected) {
           menu.setSelected(true);
           menu.setIcon(getIcon("icon.checked"));
       }
       else {
           menu.setSelected(false);
           menu.setIcon(getIcon("icon.unchecked"));
       }
       return menu;
   }

   public void actionPerformed(ActionEvent evt) {
	   toggleCheckbox((JMenuItem)evt.getSource());
   }
   
   public void toggleCheckbox(JMenuItem toolTip) {
 	   boolean newSelected = !toolTip.isSelected();
 	   	if ( isModifyPreferencesAllowed())
 	   	{
 	   	    try {
 	                Preferences prefs = this.newEditablePreferences();
 	                prefs.putEntry( configEntry, newSelected);
 	                getModification().store( prefs);
 	            } catch (Exception ex) {
 	                showException(  ex, null );
 	                return;
 	            }
 	   }
 	   toolTip.setSelected(newSelected);
 	   javax.swing.ToolTipManager.sharedInstance().setEnabled(newSelected);
 	   toolTip.setIcon(newSelected ? getIcon("icon.checked"):getIcon("icon.unchecked"));
 	}

}
