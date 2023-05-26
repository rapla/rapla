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
package org.rapla.entities.storage;

import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.storage.internal.SimpleEntity;

/** resolves the id to a proper reference to the object.
    @see org.rapla.entities.storage.internal.ReferenceHandler
*/

public interface EntityResolver
{
    // Internal Types
    // Internal Types
    String UNRESOLVED_RESOURCE_TYPE = "rapla:unresolvedResource";
    String ANONYMOUSEVENT_TYPE = "rapla:anonymousEvent";
    String DEFAULT_USER_TYPE = "rapla:defaultUser";
    String PERIOD_TYPE = "rapla:period";
    String RAPLA_TEMPLATE = "rapla:template";

    static boolean isInternalType(DynamicType type) {
        return type.getKey().startsWith("rapla:");
    }

    /** same as resolve but returns null when an entity is not found instead of throwing an {@link EntityNotFoundException} */
    <T extends Entity> T tryResolve(String id, Class<T> entityClass);

    default <T extends Entity> T tryResolve(ReferenceInfo<T> referenceInfo) {
        final Class<T> type = (Class<T>) referenceInfo.getType();
        return tryResolve(referenceInfo.getId(), type);
    }
    
    /** now the type safe version */
    default <T extends Entity> T resolve(String id, Class<T> entityClass) throws EntityNotFoundException {
        T entity = tryResolve(id, entityClass);
        SimpleEntity.checkResolveResult(id, entityClass, entity);
        return entity;

    }

    default <T extends Entity> T resolve(ReferenceInfo<T> referenceInfo) throws EntityNotFoundException {
        final Class<T> type = (Class<T>) referenceInfo.getType();
        return resolve(referenceInfo.getId(), type);
    }
    
    DynamicType getDynamicType(String key);

    //FunctionFactory getFunctionFactory(String functionName);

    //PermissionController getPermissionController();
}




