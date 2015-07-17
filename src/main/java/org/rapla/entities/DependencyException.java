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
package org.rapla.entities;
import java.util.Arrays;
import java.util.Collection;

import org.rapla.framework.RaplaException;

public class DependencyException extends RaplaException {
    private static final long serialVersionUID = 1L;
    
    Collection<String> dependentObjects;
    public DependencyException(String message,String[] dependentObjectsNames)  {
        this(message, Arrays.asList(dependentObjectsNames));
    }
    
    public DependencyException(String message,Collection<String> dependentObjectsNames)  {
        super(message);
        this.dependentObjects = dependentObjectsNames ;
    }


    public Collection<String> getDependencies() {
        return dependentObjects;
    }
}







