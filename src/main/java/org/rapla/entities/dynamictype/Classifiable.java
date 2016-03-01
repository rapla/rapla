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
package org.rapla.entities.dynamictype;

import org.rapla.entities.Named;

/** This Interfaces is implemented by all Rapla-Objects that can
 *  have classification information: Reservation, Resource, Person.
 *  @see Classification
 */
public interface Classifiable extends Named
{
    Classification getClassification();
    void setClassification(Classification classification);
    
    Classifiable[] CLASSIFIABLE_ARRAY = new Classifiable[0];
    
    class ClassifiableUtil {
        public static boolean isInternalType(Classifiable classifiable) {
            boolean isRaplaType =false;
            Classification classification = classifiable.getClassification();
            if ( classification != null )
            {
                String classificationType = classification.getType().getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
                if ( classificationType != null && classificationType.equals( DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RAPLATYPE))
                {
                    isRaplaType = true;
                }
            }
            return isRaplaType;
        }
    }
    
}







