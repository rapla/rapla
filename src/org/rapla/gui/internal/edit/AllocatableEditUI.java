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
package org.rapla.gui.internal.edit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.ResourceAnnotations;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
/****************************************************************
 * This is the controller-class for the Resource-Edit-Panel     *
 ****************************************************************/
class AllocatableEditUI  extends AbstractEditUI<Allocatable>  {
    ClassificationField<Allocatable> classificationField;
    PermissionListField permissionField;
    BooleanField holdBackConflictsField;
    boolean internal =false;
    
    public AllocatableEditUI(RaplaContext contest, boolean internal) throws RaplaException {
        super(contest);
        this.internal = internal;
        ArrayList<EditField> fields = new ArrayList<EditField>();
        classificationField = new ClassificationField<Allocatable>(contest);
        fields.add(classificationField );
        permissionField = new PermissionListField(contest,"permissions");
        fields.add( permissionField );
        if ( !internal)
        {
            holdBackConflictsField = new BooleanField(contest,"holdBackConflicts");
            fields.add(holdBackConflictsField );
        }
        setFields(fields);
    }

    public void mapToObjects() throws RaplaException {
        classificationField.mapTo( objectList);
        permissionField.mapTo( objectList);
        if ( getName(objectList).length() == 0)
            throw new RaplaException(getString("error.no_name"));
        if ( !internal)
        {
            Boolean value = holdBackConflictsField.getValue();
            if ( value != null)
            {
                for ( Allocatable alloc:objectList)
                {
                	alloc.setAnnotation(ResourceAnnotations.KEY_CONFLICT_CREATION, value  ? ResourceAnnotations.VALUE_CONFLICT_CREATION_IGNORE  : null); 
                }
            }
        }
    }

    protected void mapFromObjects() throws RaplaException {
        classificationField.mapFrom( objectList);
        permissionField.mapFrom( objectList);
        Set<Boolean> values = new HashSet<Boolean>();
        for ( Allocatable alloc:objectList)
        {
            String annotation = alloc.getAnnotation( ResourceAnnotations.KEY_CONFLICT_CREATION);
			boolean holdBackConflicts = annotation != null && annotation.equals( ResourceAnnotations.VALUE_CONFLICT_CREATION_IGNORE);
			values.add(holdBackConflicts);
        }
        if ( !internal)
        {   
            if ( values.size() ==  1)
            {
                Boolean singleValue = values.iterator().next();
                holdBackConflictsField.setValue( singleValue);
            }
            if ( values.size() > 1)
            {
                holdBackConflictsField.setFieldForMultipleValues();
            }
        }
        classificationField.setTypeChooserVisible( !internal);
    }



}
