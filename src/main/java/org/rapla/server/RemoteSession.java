/*--------------------------------------------------------------------------*
 | Copyright (C) 2006  Christopher Kohlhaas                               |
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
package org.rapla.server;

import javax.servlet.http.HttpServletRequest;

import org.rapla.entities.User;
import org.rapla.logger.Logger;
import org.rapla.storage.RaplaSecurityException;

/** An interface to access the SessionInformation. An implementation of
 * RemoteSession gets passed to the creation RaplaRemoteService.*/

public interface RemoteSession
{

    Logger getLogger();

    User getUser(HttpServletRequest request) throws RaplaSecurityException;

    boolean isAuthentified(HttpServletRequest request);

    void logout();
}
