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
package org.rapla.gui.internal.edit.reservation;

import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.JComponent;
import javax.swing.JTree;
import javax.swing.tree.TreeModel;

import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.Conflict;
import org.rapla.facade.internal.CalendarOptionsImpl;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.EventCheck;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.gui.TreeFactory;
import org.rapla.gui.internal.view.TreeFactoryImpl;
import org.rapla.gui.toolkit.DialogUI;
import org.rapla.gui.toolkit.RaplaTree;

public class ConflictReservationCheck extends RaplaGUIComponent implements EventCheck
{
    public ConflictReservationCheck(RaplaContext context) {
        super(context);
    }

    public boolean check(Collection<Reservation> reservations, Component sourceComponent) throws RaplaException {
        List<Conflict> conflictList = new ArrayList<Conflict>();
        for (Reservation reservation:reservations)
        {
            Conflict[] conflicts =  getQuery().getConflicts(reservation);
            for ( Conflict conflict: conflicts)
            {
                if ( conflict.checkEnabled())
                {
                    conflictList.add( conflict);
                }
            }
        }
        if (conflictList.size() == 0) {
            return true;
        }
        boolean showWarning = getQuery().getPreferences().getEntryAsBoolean(CalendarOptionsImpl.SHOW_CONFLICT_WARNING, true);
        User user = getUser();
        if ( !showWarning && canCreateConflicts( conflictList, user))
        {
            return true;
        }
        JComponent content = getConflictPanel(conflictList);
        DialogUI dialog = DialogUI.create(
                getContext()
                ,sourceComponent
                    ,true
                    ,content
                    ,new String[] {
                            getString("continue")
                            ,getString("back")
                    }
        );
        dialog.setDefault(1);
        dialog.setIcon(getIcon("icon.big_folder_conflicts"));
        dialog.getButton(0).setIcon(getIcon("icon.save"));
        dialog.getButton(1).setIcon(getIcon("icon.cancel"));
        dialog.setTitle(getString("warning.conflict"));
        dialog.start();
        if (dialog.getSelectedIndex()  == 0) {
            try {
                return true;
            } catch (Exception ex) {
                showException(ex,sourceComponent);
                return false;
            }
        }
        return false;
    }

    private boolean canCreateConflicts(Collection<Conflict> conflicts, User user) 
    {
        Set<Allocatable> allocatables = new HashSet<Allocatable>();
        for (Conflict conflict:conflicts)
        {
            allocatables.add(conflict.getAllocatable());
        }
        for ( Allocatable allocatable:allocatables)
        {
            if ( !allocatable.canCreateConflicts( user))
            {
                return false;
            }
        }
        return true;
    }

    private JComponent getConflictPanel(Collection<Conflict> conflicts) throws RaplaException {
    	TreeFactory treeFactory = getService(TreeFactory.class);
		TreeModel treeModel = treeFactory.createConflictModel( conflicts);
    	RaplaTree treeSelection = new RaplaTree();
    	JTree tree = treeSelection.getTree();
    	tree.setRootVisible(false);
    	tree.setShowsRootHandles(true);
    	tree.setCellRenderer(((TreeFactoryImpl) treeFactory).createConflictRenderer());
    	treeSelection.exchangeTreeModel(treeModel);
		treeSelection.expandAll();
		treeSelection.setPreferredSize( new Dimension(400,200));
    	return treeSelection;
    }

}



