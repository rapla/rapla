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
package org.rapla.client;

import java.util.Map;

import org.rapla.entities.User;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;
import org.rapla.gui.toolkit.RaplaFrame;
import org.rapla.gui.toolkit.RaplaWidget;

/** This service starts and manages the rapla-gui-client.
 */
public interface ClientService
{
    public static TypedComponentRole<Map<Object,Object>> SESSION_MAP = new TypedComponentRole<Map<Object,Object>>("org.rapla.SessionMap");
    public static TypedComponentRole<RaplaFrame> MAIN_COMPONENT = new TypedComponentRole<RaplaFrame>("org.rapla.MainComponent");
    public static TypedComponentRole<RaplaWidget> WELCOME_FIELD = new TypedComponentRole<RaplaWidget>("org.rapla.gui.WelcomeField");
    
    
    void addRaplaClientListener(RaplaClientListener listener);
    void removeRaplaClientListener(RaplaClientListener listener);

    ClientFacade getFacade() throws RaplaContextException;
    /** setup a component with the services logger,context and servicemanager */
    boolean isRunning();
    
    /** the admin can switch to another user!
     * @throws RaplaContextException 
     * @throws RaplaException */
    void switchTo(User user) throws  RaplaException;
    /** returns true if the admin has switched to anoter user!*/
    boolean canSwitchBack();

    /** restarts the complete Client and displays a new login*/
    void restart();
	/** returns true if an logout option is available. This is true when the user used an login dialog.*/
    boolean isLogoutAvailable();
    
    RaplaContext getContext();
  
    void logout();
}
