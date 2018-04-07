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
import org.rapla.client.dialog.swing.DialogUI.DialogUiFactory;
import org.rapla.client.swing.internal.edit.fields.BooleanField.BooleanFieldFactory;
import org.rapla.client.swing.internal.edit.fields.ClassificationField.ClassificationFieldFactory;
import org.rapla.client.swing.internal.edit.fields.ListField;
import org.rapla.client.swing.internal.edit.fields.PermissionListField.PermissionListFieldFactory;
import org.rapla.entities.domain.Allocatable;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;

import javax.inject.Inject;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/****************************************************************
 * This is the controller-class for the Resource-Edit-Panel     *
 ****************************************************************/
public class AllocatableMergeEditUI extends AllocatableEditUI
{
    List<Allocatable> allAllocatables;
    final ListField<Allocatable> allocatableSelectField;
    @SuppressWarnings("unchecked")
    @Inject
    public AllocatableMergeEditUI(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger,
            ClassificationFieldFactory classificationFieldFactory, PermissionListFieldFactory permissionListFieldFactory,
            BooleanFieldFactory booleanFieldFactory, final DialogUiFactory dialogUiFactory) throws RaplaInitializationException
    {
        super(facade, i18n, raplaLocale, logger, classificationFieldFactory, permissionListFieldFactory, booleanFieldFactory);
        
        allocatableSelectField = new ListField<>(facade, i18n, raplaLocale, logger, false);
        final JLabel label = new JLabel(i18n.getString("selection"));
        final JComponent component = allocatableSelectField.getComponent();
        final JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
        header.add(label);
        header.add(Box.createHorizontalStrut(20));
        header.add(component);
        editPanel.add(header, BorderLayout.NORTH);
        allocatableSelectField.addChangeListener(e -> {
            try
            {
                final Allocatable value = allocatableSelectField.getValue();
                AllocatableMergeEditUI.super.setObjects(Collections.singletonList(value));
                classificationField.getComponent().revalidate();
                permissionListField.getComponent().revalidate();
                holdBackConflictsField.getComponent().revalidate();
            }
            catch (RaplaException e1)
            {
                dialogUiFactory.showException(e1, null);
            }
        });
        editPanel.setPreferredSize(new Dimension(800, 600));
    }

    @Override
    public void setObjects(List<Allocatable> o) throws RaplaException
    {
        allAllocatables = o;
        allocatableSelectField.setVector(o);
        super.setObjects(Collections.singletonList(o.get(0)));
    }

    @Override
    public List<Allocatable> getObjects()
    {
        List<Allocatable> result  = new ArrayList<>(super.getObjects());
        for ( Allocatable alloc:allAllocatables)
        {
            if ( !result.contains( alloc))
            {
                result.add( alloc);
            }
        }
        return result;
    }



}
