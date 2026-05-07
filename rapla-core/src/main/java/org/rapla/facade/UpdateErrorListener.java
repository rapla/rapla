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
package org.rapla.facade;
import org.rapla.framework.RaplaException;

/** Classes implementing this interface will be notified when an update error
 *  occurred. The listener can be registered by calling
 *  <code>addUpdateErrorListener</code> of the <code>UpdateModule</code> <br>
 *  Don't forget to remove the listener by calling <code>removeUpdateErrorLister</code>
 *  when no longer need.
 */

public interface UpdateErrorListener {
    /** this notifies all listeners that the update of the data has
        caused an error. A normal source for UpdateErrors is a broken
        connection to the server.
     */
    void updateError(RaplaException ex);
    void disconnected(String message);
}





