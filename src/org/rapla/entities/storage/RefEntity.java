/*--------------------------------------------------------------------------*
 | Copyright (C) 2013 Christopher Kohlhaas                                     |
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

/**
The id is the unique key to distinct the entity from all others.
It is needed to safely update the entities and their associations (or aggregations)
with other entities.<br>
<b>Note:</b> Use this interface only in the
storage-backend.
*/
public interface RefEntity extends EntityReferencer
{
    void setId(String id);
    String getId();
    void setReadOnly();
    
}







