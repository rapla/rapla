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

import javax.swing.SwingUtilities;

import org.rapla.RaplaResources;
import org.rapla.client.swing.RaplaAction;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.storage.dbrm.RestartServer;


public class RestartServerAction extends RaplaAction {
    private final RestartServer service;

    public RestartServerAction(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, final RestartServer service, RaplaImages raplaImages) {
        super(facade, i18n, raplaLocale, logger);
        this.service = service;
        putValue(NAME,getString("restart_server"));
        putValue(SMALL_ICON, raplaImages.getIconFromKey("icon.restart"));
    }
    
    public void actionPerformed() {
        SwingUtilities.invokeLater( new Runnable() {
            public void run() {
                try {
					service.restartServer();
                } catch (RaplaException ex) {
                    getLogger().error("Error restarting ", ex);
                }
            }
        });
    }


}
