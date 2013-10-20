/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Gereon Fassbender, Christopher Kohlhaas               |
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

/** <h3>Why use a Memento here ?</h3> 
 * <p>
 * <strong>Problem:</strong> Realization of an undo-feature when editing a object
 * The user edits only a clone of the original.
 * When the user aborts, the clone will be discarded. Upon committing the
 * changes, the original object should be set to the state of 
 * the clone and the clone should be discarded after that.
 * </p>
 * <p>
 * The <strong>Memento-Pattern</strong> is used to get a clone of the original.
 * The newly created clone gets the state of the original:
 <pre>
 clone = original.deepClone()
 </pre>
 * To apply the changes to the original call 
 <pre>
 original.copy(clone)
 </pre>
 * </p>
 */

public interface Mementable<T> extends Cloneable
{
    /** Sets the attributes of the object implementing this interface 
     * to the attributes stored in the passed objects.
     */
    public void copy( T obj );

    /** Clones the entity and all subentities*/
    public T deepClone();

}
