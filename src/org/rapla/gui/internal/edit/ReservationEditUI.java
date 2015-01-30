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

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import javax.swing.JComponent;
import javax.swing.event.ChangeListener;

import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.Reservation;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.EditField;
import org.rapla.gui.internal.edit.fields.ClassificationField;
import org.rapla.gui.internal.edit.fields.EditFieldLayout;
import org.rapla.gui.internal.edit.fields.EditFieldWithLayout;
import org.rapla.gui.internal.edit.fields.PermissionListField;
import org.rapla.gui.internal.edit.reservation.AllocatableSelection;
/****************************************************************
 * This is the controller-class for the Resource-Edit-Panel     *
 ****************************************************************/
class ReservationEditUI  extends AbstractEditUI<Reservation>  {
    ClassificationField<Reservation> classificationField;
    PermissionListField permissionField;
    AllocatableSelection allocatableSelection;
    
    public ReservationEditUI(RaplaContext context) throws RaplaException {
        super(context);
        ArrayList<EditField> fields = new ArrayList<EditField>();
        classificationField = new ClassificationField<Reservation>(context);
        fields.add( classificationField);
        allocatableSelection = new AllocatableSelection( context )
        {
            public boolean isRestrictionVisible() {return false;}
        };
        fields.add( new AllocatableField() );
        permissionField = new PermissionListField(context,getString("permissions"));
        fields.add( permissionField );
        allocatableSelection.getComponent().setPreferredSize( new Dimension(600, 200));
        permissionField.setPermissionLevels(Permission.DENIED, Permission.READ,Permission.EDIT, Permission.ADMIN);
        permissionField.setDefaultAccessLevel( Permission.READ );
        setFields(fields);
    }
    
    class AllocatableField implements EditField,EditFieldWithLayout
    {

        @Override
        public JComponent getComponent() {
            return allocatableSelection.getComponent();
        }

        @Override
        public String getFieldName() {
            return "";
        }

        @Override
        public void addChangeListener(ChangeListener listener) {
            
        }

        @Override
        public void removeChangeListener(ChangeListener listener) {
        }

        EditFieldLayout layout = new EditFieldLayout();
        {
            layout.setBlock(true);
            layout.setVariableSized(false);
        }
        
        @Override
        public EditFieldLayout getLayout() {
            return layout;
        }
        
    }

    public void mapToObjects() throws RaplaException {
        classificationField.mapTo( objectList);
        permissionField.mapTo( objectList);
        if ( getName(objectList).length() == 0)
            throw new RaplaException(getString("error.no_name"));

    }

    protected void mapFromObjects() throws RaplaException {
        classificationField.mapFrom( objectList);
        permissionField.mapFrom( objectList);
        boolean canAdmin = true;
        for ( Reservation event:objectList)
        {
            if ( !canAdmin( event))
            {
                canAdmin = false;
            }
        }
        if ( canAdmin == false)
        {
            permissionField.getComponent().setVisible( false );
        }
        Collection<Reservation> originalReservation = Collections.emptyList();
        allocatableSelection.setReservation( objectList, originalReservation);
    }


}
