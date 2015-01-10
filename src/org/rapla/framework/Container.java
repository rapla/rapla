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
package org.rapla.framework;

import java.util.Collection;

public interface Container extends Disposable
{
     RaplaContext getContext();
     /** lookup an named component from the raplaserver.xconf 
      * @deprecated*/
     <T> T lookup(Class<T> componentRole, String hint) throws RaplaContextException;

     <T,I extends T> void addContainerProvidedComponent(Class<T> roleInterface,Class<I> implementingClass);
     <T,I extends T> void addContainerProvidedComponent(Class<T> roleInterface,Class<I> implementingClass, Configuration config);
     <T,I extends T> void addContainerProvidedComponent(TypedComponentRole<T> roleInterface, Class<I> implementingClass);
     <T,I extends T> void addContainerProvidedComponent(TypedComponentRole<T> roleInterface, Class<I> implementingClass, Configuration config);
  
     /** lookup all services for this role*/
     <T> Collection<T> lookupServicesFor(TypedComponentRole<T> extensionPoint) throws RaplaContextException;
     /** lookup all services for this role*/
     <T> Collection<T> lookupServicesFor(Class<T> role) throws RaplaContextException;    
     
 }

