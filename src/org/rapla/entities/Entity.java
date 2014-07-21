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


public interface Entity<T> extends RaplaObject<T> {
    /** returns true, if the passed object is an instance of Entity
     * and has the same id as the object. If both Entities have
     * no ids, the == operator will be applied.
     */
	
	String getId();
	
    boolean isIdentical(Entity id2);

    /** @deprecated as of rapla 1.8 there can be multiple persistant versions of an object. You can still use isReadOnly to test if the object is editable 
     * returns if the instance of the entity is persisant and the cache or just a local copy.
     * Persistant objects are usably not editable and are updated in a multiuser system.
     * Persistant instances with the same id should therefore have the same content and
     * <code>persistant1.isIdentical(persistant2)</code> implies <code>persistant1 == persistant2</code>.
     * A non persistant instance has never the same reference as the persistant entity with the same id.
     * <code>persistant1.isIdentical(nonPersitant1)</code> implies <code>persistant1 != nonPersistant2</code>.
     * As
     */
    @Deprecated
    boolean isPersistant();

    boolean isReadOnly();
    
    public static Entity<?>[] ENTITY_ARRAY = new Entity[0];
}    







