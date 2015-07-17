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
package org.rapla.entities;

/** This Exception is thrown when the application
    wants to write to a read-only entity. You should use
    the edit method to get a writable version of the entity.
 */
public class ReadOnlyException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    
    public ReadOnlyException(Object object) {
        super("Can't modify entity [" + object + "]. Use the edit-method to get a writable version!");
    }
}
