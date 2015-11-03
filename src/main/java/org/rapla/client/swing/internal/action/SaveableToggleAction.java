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
package org.rapla.client.swing.internal.action;
import org.rapla.entities.configuration.Preferences;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.TypedComponentRole;
import org.rapla.client.swing.RaplaAction;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.toolkit.DialogUI.DialogUiFactory;

public class SaveableToggleAction extends RaplaAction {
	
   TypedComponentRole<Boolean> configEntry;
   String name;
    private final DialogUiFactory dialogUiFactory;
   
   public SaveableToggleAction(RaplaContext context,String name,TypedComponentRole<Boolean> configEntry, DialogUiFactory dialogUiFactory)  
   {
        super( context );
        this.name = name;
        this.dialogUiFactory = dialogUiFactory;
        putValue( NAME, getString( name));
        this.configEntry = configEntry;
        //putValue(SMALL_ICON,getIcon("icon.unchecked"));
    }
   
    public String getName()
    {
        return name;
    }
   
   public void actionPerformed() {
 	   	if ( isModifyPreferencesAllowed())
 	   	{
 	   	    try {
 	                Preferences prefs = this.newEditablePreferences();
 	               boolean newSelected = !prefs.getEntryAsBoolean(configEntry, false);
 	                prefs.putEntry( configEntry, newSelected);
 	                getModification().store( prefs);
 	            } catch (Exception ex) {
 	                showException(  ex, new SwingPopupContext(null, null), dialogUiFactory );
 	                return;
 	            }
 	   }
 	}

    public TypedComponentRole<Boolean> getConfigEntry()
    {
        return configEntry;
    }

}
