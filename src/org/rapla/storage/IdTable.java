/*--------------------------------------------------------------------------*
  | Copyright (C) 2014 Christopher Kohlhaas                                  |
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

import org.rapla.entities.Entity;
import org.rapla.entities.RaplaType;
import org.rapla.framework.RaplaException;

/** Maintains the highest ids for every RaplaType in the LocalCache.*/
public class IdTable {
    Map<RaplaType,String> idTable = new HashMap<RaplaType,String>();
    LocalCache cache;
    /** increment and return the highest id for the selected RaplaType */
    public String createId(RaplaType raplaType) throws RaplaException {
        String oldId =  idTable.get(raplaType);
        if ( oldId == null) {
            oldId = calc(cache, raplaType);
            idTable.put(raplaType, oldId);
        }
        if (oldId == null)
            throw new RaplaException("Error in Program: RaplaType '" + raplaType +
                                     "' not found in idtable. Have you called recalc?");
        String newId = raplaType.getId(RaplaType.parseId(oldId) + 1);
        idTable.put(raplaType,newId);
        return newId;
    }

    /** Finds the highest id in an entity-collection */
    protected String calc(LocalCache cache,RaplaType raplaType) {
        int max = 0;
        for (Entity ref: cache.getCollection( raplaType )) {
            String id = ref.getId();
            int key = RaplaType.parseId(id);
			if (id != null && key > max)
                max = key;
        }
        return raplaType.getId(max);
    }
    
    public void setCache(LocalCache cache) {
        this.cache = cache;
        idTable.clear();
    }
}
