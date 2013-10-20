/*--------------------------------------------------------------------------*
  | Copyright (C) 2006 Christopher Kohlhaas                                  |
  |                                                                          |
  | This program is free software; you can redistribute it and/or modify     |
  | it under the terms of the GNU General Public License as published by the |
  | Free Software Foundation. A copy of the license has been included with   |
  | these distribution in the COPYING file, if not go to www.fsf.org .       |
  |                                                                          |
  | As a special exception, you are granted the permissions to link this     |
  | program with every library, which license fulfills the Open Source       |
  | Definition as published by the Open Source Initiative (OSI).             |
  *--------------------------------------------------------------------------*/

package org.rapla.storage;
import java.util.HashMap;
import java.util.Map;

import org.rapla.entities.RaplaType;
import org.rapla.entities.storage.RefEntity;
import org.rapla.entities.storage.internal.SimpleIdentifier;
import org.rapla.framework.RaplaException;

/** Maintains the highest ids for every RaplaType in the LocalCache.*/
public class IdTable {
    Map<RaplaType,SimpleIdentifier> idTable = new HashMap<RaplaType,SimpleIdentifier>();
    LocalCache cache;
    /** increment and return the highest id for the selected RaplaType */
    public Comparable createId(RaplaType raplaType) throws RaplaException {
        SimpleIdentifier oldId =  idTable.get(raplaType);
        if ( oldId == null) {
            oldId = calc(cache, raplaType);
            idTable.put(raplaType, oldId);
        }
        if (oldId == null)
            throw new RaplaException("Error in Program: RaplaType '" + raplaType +
                                     "' not found in idtable. Have you called recalc?");
        SimpleIdentifier newId = new SimpleIdentifier(raplaType,oldId.getKey() + 1);
        idTable.put(raplaType,newId);
        return newId;
    }

    /** Finds the highest id in an entity-collection */
    protected SimpleIdentifier calc(LocalCache cache,RaplaType raplaType) {
        int max = 0;
        for ( RefEntity<?> it: cache.getCollection( raplaType))
        {
            SimpleIdentifier id =(SimpleIdentifier) it.getId();
            if (id != null && id.getKey() > max)
                max = id.getKey();
        }
        return new SimpleIdentifier(raplaType,max);
    }
    
    public void setCache(LocalCache cache) {
        this.cache = cache;
        idTable.clear();
    }
}
