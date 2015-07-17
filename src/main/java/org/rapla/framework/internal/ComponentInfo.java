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
package org.rapla.framework.internal;

public class ComponentInfo {
    String className;
    String[] roles;
    public ComponentInfo(String className) {
        this( className, className );
    }
    
    public ComponentInfo(String className, String roleName) {
        this( className, new String[] {roleName} );
    }
    public ComponentInfo(String className, String[] roleNames) {
        this.className = className;
        this.roles = roleNames;
    }
    public String[] getRoles() {
        return roles;
    }
    
    public String getClassname() {
        return className;
    }
}
