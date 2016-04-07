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

import java.util.Collection;
import java.util.Comparator;

import org.rapla.entities.domain.Allocatable;
import org.rapla.framework.RaplaException;


/**
The User-Class is mainly for authentication-purpose
*/
public interface User extends Entity<User>, Named, Comparable, Timestamp
{
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

    @Deprecated
    Category[] getGroups();

    Collection<Category> getGroupList();
    
    boolean belongsTo( Category group );
    

    User[] USER_ARRAY = new User[0];
    
    Comparator<User> USER_COMPARATOR= new Comparator<User>()
        {
            @SuppressWarnings("unchecked")
            @Override
            public int compare(User u1, User u2) {
                if ( u2 == null)
                {
                    return 1;
                }
                if ( u1==u2 || u1.equals(u2)) return 0;
                int result = String.CASE_INSENSITIVE_ORDER.compare(
                                              u1.getUsername()
                                              ,u2.getUsername()
                                              );
                if ( result !=0 )
                    return result;
                
                result = u1.compareTo( u2 );
                return result;
            }
        };
    
}





