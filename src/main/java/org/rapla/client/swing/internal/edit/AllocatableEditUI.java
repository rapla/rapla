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
import java.awt.Dimension;
import java.util.HashSet;
import java.util.Set;

import javax.inject.Inject;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.rapla.RaplaResources;
import org.rapla.client.swing.EditComponent;
import org.rapla.client.swing.internal.edit.fields.BooleanField;
import org.rapla.client.swing.internal.edit.fields.BooleanField.BooleanFieldFactory;
import org.rapla.client.swing.internal.edit.fields.ClassificationField;
import org.rapla.client.swing.internal.edit.fields.ClassificationField.ClassificationFieldFactory;
import org.rapla.client.swing.internal.edit.fields.PermissionListField;
import org.rapla.client.swing.internal.edit.fields.PermissionListField.PermissionListFieldFactory;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.ResourceAnnotations;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.inject.Extension;
import org.rapla.storage.PermissionController;

/****************************************************************
 * This is the controller-class for the Resource-Edit-Panel     *
 ****************************************************************/

@Extension(provides = EditComponent.class, id="org.rapla.entities.domain.Allocatable")
public class AllocatableEditUI  extends AbstractEditUI<Allocatable>  {
    ClassificationField<Allocatable> classificationField;
    PermissionListField permissionListField;
    BooleanField holdBackConflictsField;
    final JComponent holdBackConflictPanel;
    private final PermissionController permissionController;

    @SuppressWarnings("unchecked")
    @Inject
    public AllocatableEditUI(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, ClassificationFieldFactory classificationFieldFactory, PermissionListFieldFactory permissionListFieldFactory, BooleanFieldFactory booleanFieldFactory) throws RaplaInitializationException {
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
        
        this.permissionListField.setPermissionLevels( Permission.DENIED,  Permission.READ_NO_ALLOCATION, Permission.READ, Permission.ALLOCATE, Permission.ALLOCATE_CONFLICTS, Permission.EDIT, Permission.ADMIN);
        final JComponent permissionPanel = permissionListField.getComponent();
        editPanel.setLayout( new BorderLayout());
        editPanel.add( classificationField.getComponent(), BorderLayout.CENTER);
        holdBackConflictsField = booleanFieldFactory.create(getString("holdbackconflicts"));
        holdBackConflictPanel = new JPanel();
        holdBackConflictPanel.setLayout( new BorderLayout());
        holdBackConflictPanel.add(new JLabel(holdBackConflictsField.getFieldName() + ": "), BorderLayout.WEST);
        holdBackConflictPanel.add(holdBackConflictsField.getComponent(), BorderLayout.CENTER);

        classificationField.addChangeListener(new ChangeListener()
        {
            
            @Override
            public void stateChanged(ChangeEvent e)
            {
                final boolean mainTabSelected = classificationField.isMainTabSelected();
                permissionPanel.setVisible( !mainTabSelected);
                if ( !mainTabSelected && !editPanel.isAncestorOf( permissionPanel) )
                {
                    editPanel.remove( holdBackConflictPanel);
                    editPanel.add( permissionPanel, BorderLayout.SOUTH);
                    editPanel.repaint();
                }
                
                if (  mainTabSelected && ( !editPanel.isAncestorOf( holdBackConflictPanel)) )
                {
                    editPanel.remove( permissionPanel );
                    editPanel.add( holdBackConflictPanel, BorderLayout.SOUTH);
                    editPanel.repaint();
                }
                AllocatableEditUI.this.stateChanged(e);
            }
        });
        editPanel.setPreferredSize( new Dimension(800,600));
    }

    public void mapToObjects() throws RaplaException {
        classificationField.mapTo( objectList);
        permissionListField.mapTo( objectList);
        if ( getName(objectList).length() == 0)
            throw new RaplaException(getString("error.no_name"));
        for ( Allocatable alloc:objectList)
        {
            if ( !isInternalType( alloc))
            {
                Boolean value = holdBackConflictsField.getValue();
                alloc.setAnnotation(ResourceAnnotations.KEY_CONFLICT_CREATION, (value != null && value) ? ResourceAnnotations.VALUE_CONFLICT_CREATION_IGNORE : null);
            }
        }
    }

    private boolean isInternalType(Allocatable alloc) {
        DynamicType type = alloc.getClassification().getType();
        String annotation = type.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
        return annotation != null && annotation.equals( DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RAPLATYPE);
    }

    protected void mapFromObjects() throws RaplaException {
        classificationField.mapFrom( objectList);
        permissionListField.mapFrom( objectList);
        Set<Boolean> values = new HashSet<Boolean>();
        boolean canAdmin = true;
        boolean allPermissions = true;
        boolean internal = false;
        final User user = getUser();

        for ( Allocatable alloc:objectList)
        {
            if ( isInternalType( alloc))
            {
                internal = true;
            }
            if ((( DynamicTypeImpl)alloc.getClassification().getType()).isInternal())
            {
                allPermissions = false;
            }
            String annotation = alloc.getAnnotation( ResourceAnnotations.KEY_CONFLICT_CREATION);
			boolean holdBackConflicts = annotation != null && annotation.equals( ResourceAnnotations.VALUE_CONFLICT_CREATION_IGNORE);
			values.add(holdBackConflicts);
            if ( !permissionController.canAdmin( alloc, user))
			{
			    canAdmin = false;
			}
        }
        if ( canAdmin == false)
        {
            permissionListField.getComponent().setVisible( false );
        }
        if ( allPermissions)
        {
            permissionListField.setPermissionLevels( Permission.DENIED,  Permission.READ_NO_ALLOCATION, Permission.READ, Permission.ALLOCATE, Permission.ALLOCATE_CONFLICTS, Permission.EDIT, Permission.ADMIN);
        }
        else
        {
            permissionListField.setPermissionLevels( Permission.DENIED,  Permission.READ );
        }
        if ( internal)
        {
            holdBackConflictPanel.setVisible( false );
            classificationField.setTypeChooserVisible( false );
        }
        else
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
            holdBackConflictPanel.setVisible( true);
            classificationField.setTypeChooserVisible( true);
        }

    }



}
