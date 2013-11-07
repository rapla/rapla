/*--------------------------------------------------------------------------*
 | Copyright (C) 2013 Christopher Kohlhaas                                  |
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
/** A StorageOperator that operates on a LocalCache-Object.
 */
package org.rapla.storage;

import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.entities.storage.RefEntity;
import org.rapla.framework.RaplaException;

public interface CachableStorageOperator extends StorageOperator, EntityResolver {
	public static final int MAX_DEPENDENCY = 20;
    /**@deprecated use getEntityResolver to access the cache. Accessing the cache directly is dangerous because it is not thread safe.
     * Instead use runWithReadLock and {@link CachableStorageOperatorCommand} to access the cache in a thread safe encapsulation*/
	LocalCache getCache();
	void runWithReadLock(CachableStorageOperatorCommand cmd) throws RaplaException;
	void dispatch(UpdateEvent evt) throws RaplaException;
    void authenticate(String username,String password) throws RaplaException;
    void saveData(LocalCache cache) throws RaplaException;
    /**@deprecated use resolve instead */
    RefEntity<?> resolveId(Object id) throws EntityNotFoundException;
    
    DynamicType getUnresolvedAllocatableType(); 
	
	DynamicType getAnonymousReservationType();
}












