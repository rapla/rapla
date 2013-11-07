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
package org.rapla.facade;

import java.util.Collection;
import java.util.Set;

import org.rapla.components.util.TimeInterval;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;

/** Encapsulate the changes that are made in the backend-store.*/
public interface ModificationEvent
{
    /** returns if the objects has changed.*/
    boolean hasChanged(RaplaObject object);

    /** returns if the objects was removed.*/
    boolean isRemoved(RaplaObject object);
    /** returns if the objects has changed or was removed.*/
    boolean isModified(RaplaObject object);
    
    /** returns if any object of the specified type has changed or was removed.*/
    boolean isModified(RaplaType raplaType);

    /** returns the modified objects from a given set.
     * @deprecated use the retainObjects instead in combination with getChanged*/
    <T extends RaplaObject> Set<T> getRemoved(Collection<T> col);

    /** returns the modified objects from a given set.
     * @deprecated use the retainObjects instead in combination with getChanged*/
    <T extends RaplaObject> Set<T> getChanged(Collection<T> col);
    
    /** returns all removed objects .*/
    Set<RaplaObject> getRemoved();

    /** returns all changed object .*/
    Set<RaplaObject> getChanged();

	Set<RaplaObject> getAddObjects();
    
    TimeInterval getInvalidateInterval();

	boolean isModified();

	

}




