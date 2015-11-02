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
package org.rapla.client.swing.internal.edit;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import javax.inject.Inject;
import javax.swing.JComponent;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.rapla.client.swing.extensionpoints.SwingViewFactory;
import org.rapla.components.util.undo.CommandHistory;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.permission.PermissionController;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.client.swing.EditComponent;
import org.rapla.client.swing.EditField;
import org.rapla.client.swing.InfoFactory;
import org.rapla.client.swing.MenuFactory;
import org.rapla.client.swing.TreeFactory;
import org.rapla.client.swing.internal.edit.fields.ClassificationField;
import org.rapla.client.swing.internal.edit.fields.EditFieldLayout;
import org.rapla.client.swing.internal.edit.fields.EditFieldWithLayout;
import org.rapla.client.swing.internal.edit.fields.PermissionListField;
import org.rapla.client.swing.internal.edit.reservation.AllocatableSelection;
import org.rapla.client.swing.toolkit.DialogUI;
import org.rapla.inject.Extension;

/****************************************************************
 * This is the controller-class for the Resource-Edit-Panel     *
 ****************************************************************/
@Extension(provides = EditComponent.class, id="org.rapla.entities.domain.Reservation")
public class ReservationEditUI  extends AbstractEditUI<Reservation>  {
    ClassificationField<Reservation> classificationField;
    PermissionListField permissionField;
    AllocatableSelection allocatableSelection;

    @Inject
    public ReservationEditUI(RaplaContext context, Set<SwingViewFactory>swingViewFactories, TreeFactory treeFactory, CalendarSelectionModel originalModel, AppointmentFormater appointmentFormater, PermissionController permissionController, MenuFactory menuFactory, InfoFactory<Component, DialogUI> infoFactory) throws RaplaException {
        super(context);
        classificationField = new ClassificationField<Reservation>(context, treeFactory);
        permissionField = new PermissionListField(context,treeFactory,getString("permissions"));

        allocatableSelection = new AllocatableSelection( context, false, new CommandHistory(), swingViewFactories, treeFactory, originalModel, appointmentFormater, permissionController, menuFactory, infoFactory)
        {
            public boolean isRestrictionVisible() {return false;}
        };
        final JComponent holdBackConflictPanel = allocatableSelection.getComponent();
        holdBackConflictPanel.setPreferredSize( new Dimension(600, 200));
        permissionField.setPermissionLevels(Permission.DENIED, Permission.READ,Permission.EDIT, Permission.ADMIN);
        permissionField.setDefaultAccessLevel( Permission.READ );

        final JComponent permissionPanel = permissionField.getComponent();
        editPanel.setLayout( new BorderLayout());
        editPanel.add( classificationField.getComponent(), BorderLayout.CENTER);
        editPanel.add( holdBackConflictPanel, BorderLayout.SOUTH);
        classificationField.addChangeListener(new ChangeListener()
        {
            
            @Override
            public void stateChanged(ChangeEvent e)
            {
                final boolean mainTabSelected = classificationField.isMainTabSelected();
                permissionPanel.setVisible( !mainTabSelected);
                if ( !editPanel.isAncestorOf( permissionPanel) && !mainTabSelected)
                {
                    editPanel.remove( holdBackConflictPanel);
                    editPanel.add( permissionPanel, BorderLayout.SOUTH);
                    editPanel.repaint();
                }
                
                if ( !editPanel.isAncestorOf( holdBackConflictPanel) && mainTabSelected)
                {
                    editPanel.remove( permissionPanel );
                    editPanel.add( holdBackConflictPanel, BorderLayout.SOUTH);
                    editPanel.repaint();
                }
            }
        });
        editPanel.setPreferredSize( new Dimension(800,600));
        
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
