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

import org.rapla.RaplaResources;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.swing.RaplaAction;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.TypedComponentRole;
import org.rapla.logger.Logger;

public class SaveableToggleAction extends RaplaAction
{

    TypedComponentRole<Boolean> configEntry;
    String name;
    private final DialogUiFactoryInterface dialogUiFactory;

    public SaveableToggleAction(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, String name,
            TypedComponentRole<Boolean> configEntry, DialogUiFactoryInterface dialogUiFactory)
    {
        super(facade, i18n, raplaLocale, logger);
        this.name = name;
        this.dialogUiFactory = dialogUiFactory;
        putValue(NAME, getString(name));
        this.configEntry = configEntry;
        //putValue(SMALL_ICON,getIcon("icon.unchecked"));
    }

    public String getName()
    {
        return name;
    }

    public void actionPerformed()
    {
        if (isModifyPreferencesAllowed())
        {
            try
            {
                final User user = getUser();
                final RaplaFacade facade = getFacade();
                Preferences prefs = facade.edit( facade.getPreferences( user));
                final boolean oldEntry = prefs.getEntryAsBoolean(configEntry, true);
                boolean newSelected = !oldEntry;
                prefs.putEntry(configEntry, newSelected);
                facade.store(prefs);
            }
            catch (Exception ex)
            {
                dialogUiFactory.showException(ex, new SwingPopupContext(null, null));
                return;
            }
        }
    }

    public TypedComponentRole<Boolean> getConfigEntry()
    {
        return configEntry;
    }

}
