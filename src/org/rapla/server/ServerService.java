/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 ?, Christopher Kohlhaas                               |
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

import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaContext;

/** Encapsulates a StorageOperator. This service is responsible for
<ul>
  <li>synchronizing update and remove request from clients and passing
  them to the storage-operator</li>

  <li>authentification of the clients</li>

  <li>notifying subscribed clients when the stored-data has
  changed</li>
</ul>
*/
public interface ServerService {
    ClientFacade getFacade();
    RaplaContext getContext();
}
