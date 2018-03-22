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
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.TypedComponentRole;
import org.rapla.logger.Logger;

import java.util.Collections;

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
            final RaplaFacade facade = getFacade();
            facade.getScheduler().supply(
                    ()->facade.getPreferences(getUser())).thenAccept(
                            (preferences)->facade.editAsync(preferences).thenCompose(prefs-> {
                            final boolean oldEntry = prefs.getEntryAsBoolean(configEntry, true);
                            boolean newSelected = !oldEntry;
                            prefs.putEntry(configEntry, newSelected);
                            return facade.dispatch(Collections.singleton(prefs),Collections.emptySet());
                        })).exceptionally(ex->dialogUiFactory.showException(ex, new SwingPopupContext(null, null)));
        }
    }

    public TypedComponentRole<Boolean> getConfigEntry()
    {
        return configEntry;
    }

}
