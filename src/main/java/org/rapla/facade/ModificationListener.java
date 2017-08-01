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

import java.util.EventListener;

/** Classes implementing this interface will be notified when changes to
 *  reservations or resources occurred. The listener can be registered by calling
 *  <code>addModificationListener</code> of the <code>UpdateModule</code> <br>
 *  Don't forget to remove the listener by calling <code>removeModificationLister</code>
 *  when no longer needed.
 *  @author Christopher Kohlhaas
 *  @see ModificationEvent
 */

public interface ModificationListener extends EventListener {
    /** this notifies all listeners that data in the rapla-backend has changed.
     *  The {@link ModificationEvent} describes these changes.
     */
    void dataChanged(ModificationEvent evt) throws RaplaException;

}





