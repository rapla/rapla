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
package org.rapla.client.swing.internal.edit.reservation;

import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.extensionpoints.EventCheck;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.TreeFactory;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.toolkit.RaplaTree;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.Category;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Period;
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.PeriodModel;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.Extension;
import org.rapla.logger.Logger;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.ResolvedPromise;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Extension(provides = EventCheck.class,id="conflictperiodcheck")
@Singleton
public class ConflictPeriodReservationCheck extends RaplaGUIComponent implements EventCheck
{
    private final DialogUiFactoryInterface dialogUiFactory;
    private final TreeFactory treeFactory;
    @Inject
    public ConflictPeriodReservationCheck(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger,
            DialogUiFactoryInterface dialogUiFactory, TreeFactory treeFactory) {
        super(facade, i18n, raplaLocale, logger);
        this.dialogUiFactory = dialogUiFactory;
        this.treeFactory = treeFactory;
    }

    public Promise<Boolean> check(Collection<Reservation> reservations, PopupContext sourceComponent) {

        final PeriodModel periodModel;
        try
        {
            periodModel = getQuery().getPeriodModel("feiertag");
        }
        catch (RaplaException e)
        {
            return new ResolvedPromise<Boolean>(e);
        }
        final Map<Appointment,Set<Period>> periodConflicts = new LinkedHashMap<>();
        for (Reservation reservation : reservations)
        {
            for (Appointment app : reservation.getAppointments())
            {
                final Repeating repeating = app.getRepeating();
                if (repeating == null)
                {
                    continue;
                }
                final TimeInterval interval = new TimeInterval(app.getStart(), app.getMaxEnd());
                final List<Period> periodsFor = periodModel.getPeriodsFor(interval);
                for (Period period : periodsFor)
                {

                    boolean excludeExceptions = true;
                    final boolean overlaps = app.overlaps(period.getStart(), period.getEnd(), excludeExceptions);
                    if (overlaps)
                    {
                        Set<Period> periods = periodConflicts.get(app);
                        if (periods == null)
                        {
                            periods = new LinkedHashSet<>();
                            periodConflicts.put(app, periods);
                        }
                        periods.add(period);
                    }
                }
            }
        }
        if (periodConflicts.isEmpty())
        {
            return new ResolvedPromise<Boolean>(true);
        }
        try
        {
            AtomicBoolean atomicBoolean = new AtomicBoolean(false);
            AtomicReference<Set> selectedSetStorage = new AtomicReference<>();
            selectedSetStorage.set(Collections.emptySet());
            JComponent content = getConflictPanel(periodConflicts, atomicBoolean, selectedSetStorage);
            DialogInterface dialog = dialogUiFactory.create(sourceComponent, true, content, new String[] { getString("continue"), getString("cancel") });
            dialog.setDefault(1);
            dialog.setIcon("icon.big_folder_conflicts");
            dialog.getAction(0).setIcon("icon.save");
            dialog.getAction(1).setIcon("icon.cancel");
            dialog.setTitle("Wiederholungstermine überschneiden sich mit");
            dialog.start(true);
            if (dialog.getSelectedIndex() == 0)
            {
                if ( atomicBoolean.get())
                {
                    final Set selecteSet = selectedSetStorage.get();
                    for (Map.Entry<Appointment,Set<Period>> entry:periodConflicts.entrySet())
                    {
                        final Appointment appointment = entry.getKey();
                        final Set<Period> periods = entry.getValue();
                        final Repeating repeating = appointment.getRepeating();
                        for ( Period period:periods)
                        {

                            if (selecteSet.contains( period) || !Collections.disjoint(period.getCategories(), selecteSet))
                            {
                                repeating.addExceptions( period.getInterval());
                            }
                        }
                    }
                }
                return new ResolvedPromise<Boolean>( true);
            }
        }
        catch (RaplaException ex)
        {
            dialogUiFactory.showException(ex, new SwingPopupContext((Component) sourceComponent, null));
        }
        return new ResolvedPromise<Boolean>( false);
    }



    private JComponent getConflictPanel(Map<Appointment, Set<Period>> conflicts, AtomicBoolean atomicBoolean, AtomicReference<Set> selectedItems) throws RaplaException {
        JPanel panel = new JPanel();
        BorderLayout layout = new BorderLayout();
        panel.setLayout( layout);

        Set<Period> allPeriods = new TreeSet<Period>();
        for ( Set<Period> periods: conflicts.values())
        {
            allPeriods.addAll( periods);
        }
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("");
        final Category timetables = getQuery().getSuperCategory().getCategory("timetables");
        Map<Category,List<Period>> list = new LinkedHashMap<>();
        for ( Category category:timetables.getCategoryList())
        {
            list.put( category, new ArrayList<>());
        }
        for ( Period p:allPeriods)
        {
            for (Category category:p.getCategories())
            {
                final List<Period> periods = list.get(category);
                if ( periods != null)
                {
                    periods.add(p);
                }
            }
        }

        for ( Map.Entry<Category,List<Period>> entry: list.entrySet())
        {
            Category category = entry.getKey();
            final List<Period> value = entry.getValue();
            if ( value.size() == 0)
            {
                continue;
            }
            DefaultMutableTreeNode catNode = treeFactory.newNamedNode(category);
            root.add(catNode);
            for ( Period p: value)
            {
                final Set<Category> categories = p.getCategories();
                if (categories.contains( category))
                {
                    DefaultMutableTreeNode pNode = treeFactory.newNamedNode( p);
                    catNode.add( pNode);
                }
            }
        }
        TreeModel treeModel =  new DefaultTreeModel(root);
    	RaplaTree treeSelection = new RaplaTree();
    	JTree tree = treeSelection.getTree();
    	//tree.setCellRenderer( treeF);
    	tree.setRootVisible(false);
    	treeSelection.setMultiSelect( true);
    	tree.setShowsRootHandles(true);
    	//tree.setCellRenderer(treeFactory.createConflictRenderer());
    	treeSelection.exchangeTreeModel(treeModel);
		treeSelection.expandAll();
		treeSelection.setPreferredSize( new Dimension(400,200));
		panel.add(BorderLayout.CENTER, treeSelection);
        final JCheckBox ausnahmenCheck = new JCheckBox("Als Ausnahmen hinzufügen");
        ausnahmenCheck.addChangeListener((e)->{
         atomicBoolean.set( ausnahmenCheck.isSelected());
        });
        tree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener()
        {
            @Override
            public void valueChanged(TreeSelectionEvent e)
            {
                final List<Object> selectedElements = treeSelection.getSelectedElements();
                selectedItems.set( new HashSet(selectedElements));
            }
        });
        panel.add(BorderLayout.SOUTH, ausnahmenCheck);
    	return panel;
    }

}



