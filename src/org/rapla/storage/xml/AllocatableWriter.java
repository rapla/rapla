/*--------------------------------------------------------------------------*
  | Copyright (C) 2006 Christopher Kohlhaas                                  |
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

import java.io.IOException;

import org.rapla.entities.Category;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.internal.CategoryImpl;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;


public class AllocatableWriter extends ClassifiableWriter {
    public AllocatableWriter(RaplaContext sm) throws RaplaException {
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
        printVersion( allocatable);
        printOwner(allocatable);
        printTimestamp(allocatable );
        closeTag();
        printAnnotations( allocatable);
        printClassification(allocatable.getClassification());

        Permission[] permissions = allocatable.getPermissions();
        for ( int i = 0; i < permissions.length; i++ ){
            printPermission(permissions[i]);
        }
        closeElement(tagName);
    }
    
    public void writeObject(RaplaObject object) throws IOException, RaplaException {
        printAllocatable( (Allocatable) object);
    }


    protected void printPermission(Permission p) throws IOException,RaplaException {
        openTag("rapla:permission");
        if ( p.getUser() != null ) {
            att("user", getId( p.getUser() ));
        } else if ( p.getGroup() != null ) {
            if ( isIdOnly() ) {
                att( "groupidref", getId( p.getGroup() ) );
            } else {
                att( "group", getGroupPath( p.getGroup() ) );
            }
        }
        if ( p.getMinAdvance() != null ) {
            att ( "min-advance", p.getMinAdvance().toString() );
        }
        if ( p.getMaxAdvance() != null ) {
            att ( "max-advance", p.getMaxAdvance().toString() );
        }
        if ( p.getStart() != null ) {
            att ( "start-date", dateTimeFormat.formatDate(  p.getStart() ) );
        }
        if ( p.getEnd() != null ) {
            att ( "end-date", dateTimeFormat.formatDate(  p.getEnd() ) );
        }
        if ( p.getAccessLevel() != Permission.ALLOCATE_CONFLICTS ) {
            att("access", Permission.ACCESS_LEVEL_NAMEMAP.get( p.getAccessLevel() ) );
        }
        closeElementTag();
    }

    private String getGroupPath( Category category) throws EntityNotFoundException {
        Category rootCategory = getSuperCategory().getCategory(Permission.GROUP_CATEGORY_KEY);
        return ((CategoryImpl) rootCategory ).getPathForCategory(category);
    }




}



