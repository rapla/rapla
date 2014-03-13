/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Gereon Fassbender                                     |
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
package org.rapla.entities;

import org.rapla.entities.domain.Allocatable;
import org.rapla.framework.RaplaException;


/**
The User-Class is mainly for authentication-purpose
*/
public interface User extends Entity<User>, Named, Comparable
{
    final RaplaType<User> TYPE = new RaplaType<User>(User.class,"user");

    /** returns the loginname  */
    String getUsername();
    /** returns the complete name of user */
    String getName();
    /** returns the email of the user */
    String getEmail();
    /** returns if the user has admin-privilige */
    boolean isAdmin();
    
    void setPerson(Allocatable person) throws RaplaException;
    Allocatable getPerson();

    void setUsername(String username);
    void setName(String name);
    void setEmail(String email);
    void setAdmin(boolean isAdmin);

    void addGroup(Category group);
    boolean removeGroup(Category group);

    Category[] getGroups();

    boolean belongsTo( Category group );

    public static User[] USER_ARRAY = new User[0];
}





