/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Christopher Kohlhaas                                  |
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
package org.rapla.entities.storage;

import org.rapla.entities.EntityNotFoundException;


/** resolves the id to a proper reference to the object.
    @see org.rapla.entities.storage.internal.ReferenceHandler
*/

public interface EntityResolver
{
    public RefEntity<?> resolve(Object id) throws EntityNotFoundException;
    public RefEntity<?> resolveEmail(final String emailArg) throws EntityNotFoundException;
    
}




