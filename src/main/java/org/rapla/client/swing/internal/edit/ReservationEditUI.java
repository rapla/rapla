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

import org.rapla.RaplaResources;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.dialog.InfoFactory;
import org.rapla.client.menu.MenuFactory;
import org.rapla.client.swing.EditComponent;
import org.rapla.client.swing.EditField;
import org.rapla.client.TreeFactory;
import org.rapla.client.swing.internal.FilterEditButton.FilterEditButtonFactory;
import org.rapla.client.swing.internal.MultiCalendarPresenter;
import org.rapla.client.swing.internal.edit.fields.BooleanField.BooleanFieldFactory;
import org.rapla.client.swing.internal.edit.fields.ClassificationField;
import org.rapla.client.swing.internal.edit.fields.ClassificationField.ClassificationFieldFactory;
import org.rapla.client.swing.internal.edit.fields.DateField.DateFieldFactory;
import org.rapla.client.swing.internal.edit.fields.EditFieldLayout;
import org.rapla.client.swing.internal.edit.fields.EditFieldWithLayout;
import org.rapla.client.swing.internal.edit.fields.PermissionListField;
import org.rapla.client.swing.internal.edit.fields.PermissionListField.PermissionListFieldFactory;
import org.rapla.client.swing.internal.edit.reservation.AllocatableSelection;
import org.rapla.components.util.undo.CommandHistory;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.Extension;
import org.rapla.logger.Logger;
import org.rapla.storage.PermissionController;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.swing.JComponent;
import javax.swing.event.ChangeListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.Collection;
import java.util.Collections;

/****************************************************************
 * This is the controller-class for the Resource-Edit-Panel     *
 ****************************************************************/
@Extension(provides = EditComponent.class, id="org.rapla.entities.domain.Reservation")
public class ReservationEditUI  extends AbstractEditUI<Reservation>  {
    ClassificationField<Reservation> classificationField;
    PermissionListField permissionListField;
    AllocatableSelection allocatableSelection;
    private final PermissionController permissionController;

    @Inject
    public ReservationEditUI(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger,  ClassificationFieldFactory classificationFieldFactory, PermissionListFieldFactory permissionListFieldFactory,
             AllocatableSelection.AllocatableSelectionFactory allocatableSelectionFactory) throws RaplaInitializationException
    {
        super(facade, i18n, raplaLocale, logger);
        this.permissionController = facade.getRaplaFacade().getPermissionController();
        classificationField = classificationFieldFactory.create();
        try
        {
            this.permissionListField = permissionListFieldFactory.create(getString("permissions"));
        }
        catch (RaplaException e1)
        {
            throw new RaplaInitializationException(e1);
        } 

        allocatableSelection = allocatableSelectionFactory.create(false, new CommandHistory(), false);
        final JComponent holdBackConflictPanel = allocatableSelection.getComponent();
        holdBackConflictPanel.setPreferredSize( new Dimension(600, 200));
        this.permissionListField.setPermissionLevels(Permission.DENIED, Permission.READ,Permission.EDIT, Permission.ADMIN);
        this.permissionListField.setDefaultAccessLevel( Permission.READ );

        final JComponent permissionPanel = permissionListField.getComponent();
        editPanel.setLayout( new BorderLayout());
        editPanel.add( classificationField.getComponent(), BorderLayout.CENTER);
        editPanel.add( holdBackConflictPanel, BorderLayout.SOUTH);
        classificationField.addChangeListener(e -> {
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
        permissionListField.mapTo( objectList);
        if ( getName(objectList).length() == 0)
            throw new RaplaException(getString("error.no_name"));

    }

    protected void mapFromObjects() throws RaplaException {
        classificationField.mapFrom( objectList);
        permissionListField.mapFrom( objectList);
        boolean canAdmin = true;
        final RaplaFacade raplaFacade = getFacade();
        for ( Reservation event:objectList)
        {
            if ( !permissionController.canAdmin( event, getUser()))
            {
                canAdmin = false;
            }
        }
        if ( !canAdmin )
        {
            permissionListField.getComponent().setVisible( false );
        }
        Collection<Reservation> originalReservation = Collections.emptyList();
        allocatableSelection.setReservation( objectList, originalReservation);
    }


}
