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

package org.rapla.entities.domain;

import org.rapla.entities.Entity;
import org.rapla.entities.User;


public interface EntityPermissionContainer<T> extends Entity<T>,PermissionContainer 
{
    // adds a permission. Permissions are stored in a hashset so the same permission can't be added twice
    void addPermission( Permission permission );
    boolean removePermission( Permission permission );
    
    /** returns if the user has the permission to modify the allocatable (and also its permission-table).*/
    boolean canModify( User user );
    
    /** returns if the user has the permission to read the information and the allocations of this resource.*/
    boolean canRead( User user );
    
    /** returns if the user has the permission to read only the information but not the allocations of this resource.*/
    boolean canReadOnlyInformation( User user );
    
    Permission[] getPermissions();
    Permission newPermission();
}
