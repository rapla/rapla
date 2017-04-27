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
import org.rapla.client.swing.images.RaplaImages;
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
import org.rapla.storage.PermissionController;

import javax.inject.Inject;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

@Extension(provides = EventCheck.class,id="conflictperiodcheck")
public class ConflictPeriodReservationCheck extends RaplaGUIComponent implements EventCheck
{

    private final PermissionController permissionController;
    private final TreeFactory treeFactory;
    private final RaplaImages raplaImages;
    private final DialogUiFactoryInterface dialogUiFactory;
    @Inject
    public ConflictPeriodReservationCheck(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, TreeFactory treeFactory, RaplaImages raplaImages, DialogUiFactoryInterface dialogUiFactory) {
        super(facade, i18n, raplaLocale, logger);
        this.permissionController = facade.getRaplaFacade().getPermissionController();
        this.treeFactory = treeFactory;
        this.raplaImages = raplaImages;
        this.dialogUiFactory = dialogUiFactory;
    }

    public Promise<Boolean> check(Collection<Reservation> reservations, PopupContext sourceComponent) {

        final PeriodModel periodModel = getPeriodModel();
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

                    boolean excludeExceptions = false;
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
            JComponent content = getConflictPanel(periodConflicts, atomicBoolean);
            DialogInterface dialog = dialogUiFactory.create(sourceComponent, true, content, new String[] { getString("continue"), getString("back") });
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
                    for (Map.Entry<Appointment,Set<Period>> entry:periodConflicts.entrySet())
                    {
                        final Appointment appointment = entry.getKey();
                        final Set<Period> periods = entry.getValue();
                        final Repeating repeating = appointment.getRepeating();
                        for ( Period period:periods)
                        {
                            repeating.addExceptions( period.getInterval());
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



    private JComponent getConflictPanel(Map<Appointment, Set<Period>> conflicts, AtomicBoolean atomicBoolean) throws RaplaException {
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
        for (Category category:timetables.getCategoryList())
        {
            final Locale locale = getRaplaLocale().getLocale();
            DefaultMutableTreeNode catNode = new DefaultMutableTreeNode(category.getName(locale));
            root.add(catNode);
            for ( Period p:allPeriods)
            {
                final Set<Category> categories = p.getCategories();
                if (categories.contains( category))
                {
                    DefaultMutableTreeNode pNode = new DefaultMutableTreeNode(p.getName());
                    catNode.add( pNode);
                }
            }
        }
		TreeModel treeModel =  new DefaultTreeModel(root);
    	RaplaTree treeSelection = new RaplaTree();
    	JTree tree = treeSelection.getTree();
    	tree.setRootVisible(false);
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
        panel.add(BorderLayout.SOUTH, ausnahmenCheck);
    	return panel;
    }

}



