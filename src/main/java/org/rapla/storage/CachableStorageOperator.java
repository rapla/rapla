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

import java.util.Collection;
import java.util.Date;
import java.util.TimeZone;

import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.storage.EntityReferencer.ReferenceInfo;
import org.rapla.facade.Conflict;
import org.rapla.framework.RaplaException;

public interface CachableStorageOperator extends StorageOperator {
	
	void runWithReadLock(CachableStorageOperatorCommand cmd) throws RaplaException;
    void dispatch(UpdateEvent evt) throws RaplaException;
    String authenticate(String username,String password) throws RaplaException;
    void saveData(LocalCache cache, String version) throws RaplaException;
    
    Collection<Entity> getVisibleEntities(final User user) throws RaplaException;
    Collection<Entity> getUpdatedEntities(final User user,Date timestamp) throws RaplaException;
    Collection<ReferenceInfo> getDeletedEntities(final User user, final Date timestamp) throws RaplaException;

    TimeZone getTimeZone();
    //DynamicType getUnresolvedAllocatableType(); 
    //DynamicType getAnonymousReservationType();
    void fillConflictDisableInformation(User user, Conflict conflict);
	
}
















