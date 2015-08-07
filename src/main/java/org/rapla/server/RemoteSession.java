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

import org.rapla.entities.User;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.logger.Logger;

/** An interface to access the SessionInformation. An implementation of
 * RemoteSession gets passed to the creation RaplaRemoteService.*/

public interface RemoteSession 
{
	boolean isAuthentified();
	User getUser() throws RaplaContextException;
	Logger getLogger();
	//String getAccessToken();
}
