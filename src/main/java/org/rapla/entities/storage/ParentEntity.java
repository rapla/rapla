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

import java.util.Collection;

import org.rapla.entities.Entity;

public interface ParentEntity {

    /**
       returns all entities that are aggregated under the entity.
       This information is usefull to transparently store the
       subentities along with their parent.
       * The difference between subEntities and other references is,
     * that the subEntities are aggregated instead of associated. That
     * means SubEntities should be
     * <li>stored, when the parent is stored</li>
     * <li>deleted, when the parent is deleted or when they are
     * removed from the parent</li>
     */
    <T extends Entity> Collection<T> getSubEntities();

    void addEntity(Entity entity);
    
    Entity findEntity(Entity copy);
    
}







