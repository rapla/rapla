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

import javax.swing.SwingUtilities;

import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.RaplaAction;
import org.rapla.storage.dbrm.RestartServer;


public class RestartServerAction extends RaplaAction {
    /**
     * @param sm
     * @throws RaplaException
     */
    public RestartServerAction(RaplaContext sm) throws RaplaException {
        super(sm);
        putValue(NAME,getString("restart_server"));
        putValue(SMALL_ICON,getIcon("icon.restart"));
    }
    
    public void actionPerformed(ActionEvent arg0) {
        SwingUtilities.invokeLater( new Runnable() {
            public void run() {
                try {
                	RestartServer service = getService( RestartServer.class);
					service.restartServer();
                } catch (RaplaException ex) {
                    getLogger().error("Error restarting ", ex);
                }
            }
        });
    }


}
