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

package org.rapla.server;
import org.rapla.entities.Category;
import org.rapla.entities.User;
import org.rapla.framework.RaplaException;
import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;

@ExtensionPoint(id="org.rapla.Authentication",context = InjectionContext.server)
public interface AuthenticationStore {
    /** returns, if the user can be authenticated. */
    boolean authenticate(String username, String password) throws RaplaException;
    /** Initializes a user entity with the values provided by the authentication store.
     * @return <code>true</code> if the new user-object attributes (such as email, name, or groups) differ from the values stored before the method was executed, <code>false</code> otherwise. */
    boolean initUser( User user, String username, String password, Category groupRootCategory) throws RaplaException;
}




