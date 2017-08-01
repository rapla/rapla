/*--------------------------------------------------------------------------*
  | Copyright (C) 2014 Christopher Kohlhaas                                  |
  |                                                                          |
  | This program is free software; you can redistribute it and/or modify     |
  | it under the terms of the GNU General Public License as published by the |
  | Free Software Foundation. A copy of the license has been included with   |
  | these distribution in the COPYING file, if not go to www.fsf.org .       |
  |                                                                          |
  | As a special exception, you are granted the permissions to link this     |
  | program with every library, which license fulfills the Open Source       |
  | Definition as published by the Open Source Initiative (OSI).             |
  *--------------------------------------------------------------------------*/

package org.rapla.storage.xml;

import org.rapla.entities.RaplaObject;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.framework.RaplaException;

import java.io.IOException;


public class AllocatableWriter extends ClassifiableWriter {
    public AllocatableWriter(RaplaXMLContext sm) throws RaplaException {
        super(sm);
    }

    public void printAllocatable(Allocatable allocatable) throws IOException,RaplaException {
        String tagName;
        DynamicType type = allocatable.getClassification().getType();
        String annotation = type.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
        if ( DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON.equals(annotation))
        {
        	tagName = "rapla:person";
        }
        else if( DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE.equals(annotation))
        {
        	tagName = "rapla:resource";
        }
        else if( DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RAPLATYPE.equals(annotation))
        {
        	tagName = "rapla:extension";
        }
        else
        {
        	throw new RaplaException("No or unknown classification type '" + annotation + "'  set for " + allocatable.toString()  + " ignoring ");
        }
        openTag(tagName);
        printId(allocatable);
        printOwner(allocatable);
        printTimestamp(allocatable );
        closeTag();
        printAnnotations( allocatable);
        printClassification(allocatable.getClassification());

        printPermissions(allocatable);
        closeElement(tagName);
    }

    
    public void writeObject(RaplaObject object) throws IOException, RaplaException {
        printAllocatable( (Allocatable) object);
    }

  



}



