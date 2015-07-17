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

import java.util.EventListener;


/** After a store all registered ChangeListeners get notified by calling
 *  the trigger method. A list with all changes is passed.
 *  At the moment only AllocationChangeEvents are triggered.
 *  By this you can get notified, when any Reservation changes.
 *  The difference between the UpdateEvent and a ChangeEvent is,
 *  that the UpdateEvent contains the new Versions of all updated enties,
 *  while a ChangeEvent contains Information about a single change.
 *  That change can be calculated as with the AllocationChangeEvent, which
 *  represents a single allocation change for one allocatable object
 * ,including information about the old allocation and the new one.
 * @see AllocationChangeEvent
 */
public interface AllocationChangeListener extends EventListener
{
    void changed(AllocationChangeEvent[] changeEvents);
}
