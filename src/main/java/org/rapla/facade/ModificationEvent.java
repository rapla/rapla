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

import org.rapla.components.util.TimeInterval;
import org.rapla.entities.Entity;
import org.rapla.entities.storage.ReferenceInfo;

import java.util.Set;

/** Encapsulate the changes that are made in the backend-store.*/
public interface ModificationEvent
{
    /** returns if the objects has changed.*/
    boolean hasChanged(Entity object);

    /** returns if the objects was removed.*/
    boolean isRemoved(Entity object);
    /** returns if the objects has changed or was removed.*/
    boolean isModified(Entity object);
    
    /** returns if any object of the specified type has changed or was removed.*/
    boolean isModified(Class<? extends Entity> raplaType);
    
    Set<ReferenceInfo> getRemovedReferences();

    /** returns all changed object .*/
    Set<Entity> getChanged();

	Set<Entity> getAddObjects();
    
    TimeInterval getInvalidateInterval();

	boolean isModified();

    /** @Deprecated not supported in 2.1*/
    @Deprecated
    boolean isSwitchTemplateMode();

}




