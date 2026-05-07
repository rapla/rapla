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
package org.rapla.entities.storage;
import org.rapla.entities.dynamictype.DynamicType;

/** DynamicTypeDependent needs to be implemented by all classes that would be affected by a change to a dynamic type.
 * E.g. If you remove or modify an attribute of a dynamic resource type. All resources of this types must take certain actions.*/
public interface DynamicTypeDependant  {
    /** returns true if the object needs to be changed with new dynamic type change and false if no modification of the object is requiered. 
     * Example: If you remove an attribute from a resource type, and one resource of the resourcetype doesnt use this attribute this resource doesnt need modifaction, so it can return false
     * @param type The new dynamic type
     * */
    boolean needsChange(DynamicType type);
    /** process the change  in the object
    *Example: If you remove an attribute from a resource type, you should remove the corresponding attriabute value in all resources of the resourcetype
    * @param type The new dynamic type*/
    void commitChange(DynamicType type);
    /** throws a CannotExistWithoutTypeException when type cannot be removed*/
    void commitRemove(DynamicType type) throws CannotExistWithoutTypeException;
}







