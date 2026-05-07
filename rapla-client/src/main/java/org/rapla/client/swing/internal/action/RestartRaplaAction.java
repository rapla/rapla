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
import org.rapla.client.UserClientService;
import org.rapla.client.swing.RaplaAction;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;


public class RestartRaplaAction extends RaplaAction{
    private final UserClientService clientService;

    public RestartRaplaAction(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, UserClientService clientService)
    {
        super(facade, i18n, raplaLocale, logger);
        this.clientService = clientService;
        boolean logoutAvailable = clientService.isLogoutAvailable();
        String string = getString("restart_client");
        if (logoutAvailable)
        {
        	string = getString("logout") +" / " + string;
        }
        putValue(NAME,string);
        setIcon(i18n.getIcon("icon.restart"));
    }

    public void actionPerformed() {
        clientService.restart();
    }


}
