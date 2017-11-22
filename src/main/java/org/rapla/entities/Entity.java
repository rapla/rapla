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

import jsinterop.annotations.JsType;
import org.rapla.entities.storage.ReferenceInfo;

@JsType
public interface Entity<T extends Entity> extends RaplaObject<T> {
    /** returns true, if the passed object is an instance of Entity
     * and has the same id as the object. If both Entities have
     * no ids, the == operator will be applied.
     */
	
	String getId();
	
    boolean isIdentical(Entity id2);

    boolean isReadOnly();

    ReferenceInfo<T> getReference();
    Entity<?>[] ENTITY_ARRAY = new Entity[0];
}    







