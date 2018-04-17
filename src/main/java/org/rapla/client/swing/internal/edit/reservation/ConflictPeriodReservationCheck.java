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

import org.jetbrains.annotations.NotNull;
import org.rapla.RaplaResources;
import org.rapla.client.AppointmentListener;
import org.rapla.client.PopupContext;
import org.rapla.client.RaplaWidget;
import org.rapla.client.ReservationEdit;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.ReservationToolbarExtension;
import org.rapla.client.TreeFactory;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.internal.view.RaplaSwingTreeModel;
import org.rapla.client.RaplaTreeNode;
import org.rapla.client.swing.toolkit.RaplaButton;
import org.rapla.client.swing.toolkit.RaplaTree;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.Category;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Period;
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.PeriodModel;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.Extension;
import org.rapla.logger.Logger;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.ResolvedPromise;

import javax.inject.Inject;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.tree.TreeModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Extension(provides = ReservationToolbarExtension.class, id = "holidayexception")
public class ConflictPeriodReservationCheck extends RaplaGUIComponent implements ReservationToolbarExtension
{
    private final DialogUiFactoryInterface dialogUiFactory;
    private final TreeFactory treeFactory;
    private Reservation reservation;
    private RaplaButton button;

    @Inject
    public ConflictPeriodReservationCheck(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger,
            DialogUiFactoryInterface dialogUiFactory, TreeFactory treeFactory)
    {
        super(facade, i18n, raplaLocale, logger);
        this.dialogUiFactory = dialogUiFactory;
        this.treeFactory = treeFactory;
    }
    enum DialogResult
    {
        CANCEL,
        OK,
        OK_MODIFIED
    }

    public Promise<Boolean> check(Collection<Reservation> reservations, PopupContext sourceComponent)
    {
        final Map<Appointment, Set<Period>> periodConflicts;
        try
        {
            periodConflicts = getPeriodConflicts(reservations);
        }
        catch (RaplaException e)
        {
            return new ResolvedPromise<>(e);
        }
        if (periodConflicts.isEmpty())
        {
            return new ResolvedPromise<>(true);
        }
        return showPeriodConflicts(sourceComponent, periodConflicts).thenApply((result)->result != DialogResult.CANCEL);
    }

    @NotNull
    private Promise<DialogResult> showPeriodConflicts(PopupContext sourceComponent, Map<Appointment, Set<Period>> periodConflicts)
    {
        AtomicBoolean atomicBoolean = new AtomicBoolean(false);
        AtomicReference<Set> selectedSetStorage = new AtomicReference<>();
        selectedSetStorage.set(Collections.emptySet());
        JComponent content = getConflictPanel(periodConflicts, atomicBoolean, selectedSetStorage);
        DialogInterface dialog = dialogUiFactory.createContentDialog(sourceComponent, content, new String[] { getString("continue"), getString("cancel") });
        dialog.setDefault(1);
        dialog.setIcon(i18n.getIcon("icon.big_folder_conflicts"));
        dialog.getAction(0).setIcon(i18n.getIcon("icon.save"));
        dialog.getAction(1).setIcon(i18n.getIcon("icon.cancel"));
        dialog.setTitle("Wiederholungstermine überschneiden sich mit");
        return dialog.start(true).thenApply(index->
        {
            if (index == 0) {
                boolean modified = false;
                if (atomicBoolean.get()) {
                    final Set selecteSet = selectedSetStorage.get();
                    for (Map.Entry<Appointment, Set<Period>> entry : periodConflicts.entrySet()) {
                        final Appointment appointment = entry.getKey();
                        final Set<Period> periods = entry.getValue();
                        final Repeating repeating = appointment.getRepeating();
                        for (Period period : periods) {

                            if (selecteSet.contains(period) || !Collections.disjoint(period.getCategories(), selecteSet)) {
                                final Date[] exceptionsBefore = repeating.getExceptions();
                                repeating.addExceptions(period.getInterval());
                                final Date[] exceptionsAfter = repeating.getExceptions();
                                if (!Arrays.equals(exceptionsAfter, exceptionsBefore)) {
                                    modified = true;
                                }
                            }
                        }
                    }
                }
                return modified ? DialogResult.OK_MODIFIED : DialogResult.OK;
            } else {
                return DialogResult.CANCEL;
            }
        });
    }

    @NotNull
    private Map<Appointment, Set<Period>> getPeriodConflicts(Collection<Reservation> reservations) throws RaplaException
    {
        Map<Appointment, Set<Period>>periodConflicts = new LinkedHashMap<>();
        final PeriodModel periodModel;
        periodModel = getFacade().getPeriodModelFor("feiertag");
        if ( periodModel == null)
        {
            return periodConflicts;
        }
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
                    final boolean overlaps = app.overlaps(period.getStart(), period.getEnd());
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
        return periodConflicts;
    }

    private JComponent getConflictPanel(Map<Appointment, Set<Period>> conflicts, AtomicBoolean atomicBoolean, AtomicReference<Set> selectedItems)
    {
        JPanel panel = new JPanel();
        BorderLayout layout = new BorderLayout();
        panel.setLayout(layout);

        Set<Period> allPeriods = new TreeSet<>();
        for (Set<Period> periods : conflicts.values())
        {
            allPeriods.addAll(periods);
        }
        final Category timetables = getTimetablesCategory();
        Map<Category, List<Period>> list = new LinkedHashMap<>();
        if (timetables != null)
        {
            for (Category category : timetables.getCategoryList())
            {
                list.put(category, new ArrayList<>());
            }
        }
        for (Period p : allPeriods)
        {
            for (Category category : p.getCategories())
            {
                final List<Period> periods = list.get(category);
                if (periods != null)
                {
                    periods.add(p);
                }
            }
        }

        RaplaTreeNode root = treeFactory.newRootNode();
        for (Map.Entry<Category, List<Period>> entry : list.entrySet())
        {
            Category category = entry.getKey();
            final List<Period> value = entry.getValue();
            if (value.size() == 0)
            {
                continue;
            }
            RaplaTreeNode catNode = treeFactory.newNamedNode(category);
            root.add(catNode);
            for (Period p : value)
            {
                final Set<Category> categories = p.getCategories();
                if (categories.contains(category))
                {
                    RaplaTreeNode pNode = treeFactory.newNamedNode(p);
                    catNode.add(pNode);
                }
            }
        }
        TreeModel treeModel = new RaplaSwingTreeModel(root);
        RaplaTree treeSelection = new RaplaTree();
        JTree tree = treeSelection.getTree();
        //tree.setCellRenderer( treeF);
        tree.setRootVisible(false);
        treeSelection.setMultiSelect(true);
        tree.setShowsRootHandles(true);
        //tree.setCellRenderer(treeFactory.createConflictRenderer());
        treeSelection.exchangeTreeModel(treeModel);
        treeSelection.expandAll();
        treeSelection.setPreferredSize(new Dimension(400, 200));
        panel.add(BorderLayout.CENTER, treeSelection);
        final JCheckBox ausnahmenCheck = new JCheckBox("Als Ausnahmen hinzufügen");
        ausnahmenCheck.addChangeListener((e) ->
        {
            atomicBoolean.set(ausnahmenCheck.isSelected());
        });
        tree.getSelectionModel().addTreeSelectionListener(e -> {
            final List<Object> selectedElements = treeSelection.getSelectedElements();
            selectedItems.set(new HashSet(selectedElements));
        });
        panel.add(BorderLayout.SOUTH, ausnahmenCheck);
        return panel;
    }

    private Category getTimetablesCategory()
    {
        return getQuery().getSuperCategory().getCategory("timetables");
    }

    @Override
    public Collection<RaplaWidget> createExtensionButtons(ReservationEdit edit)
    {
        final Category timetablesCategory = getTimetablesCategory();
        if (timetablesCategory == null)
        {
            return Collections.emptyList();
        }
        if ( timetablesCategory.getCategories().length == 0)
        {
        	return Collections.emptyList();
        }
        final PopupContext popupContext = new SwingPopupContext((Component) edit.getComponent(), null);
        button = new RaplaButton();
        button.setText("Feiertage/Ferien");
        button.addActionListener((evt) ->
        {
            try
            {
                final Map<Appointment, Set<Period>> periodConflicts = getPeriodConflicts(Collections.singletonList(reservation));
                showPeriodConflicts( popupContext, periodConflicts).thenAccept((result) ->
                {
                    if ( result == DialogResult.OK_MODIFIED)
                    {
                        updateButton(reservation);
                        edit.fireChange();
                    }
                });
            } catch (RaplaException ex)
            {
                dialogUiFactory.showException( ex, popupContext);
            }
        });
        edit.addAppointmentListener(new AppointmentListener()
        {
            @Override
            public void appointmentAdded(Collection<Appointment> appointment)
            {
                updateButton( reservation);
            }

            @Override
            public void appointmentRemoved(Collection<Appointment> appointment)
            {
                updateButton( reservation);
            }

            @Override
            public void appointmentChanged(Collection<Appointment> appointment)
            {
                updateButton( reservation);
            }

            @Override
            public void appointmentSelected(Collection<Appointment> appointment)
            {

            }
        });
        return Collections.singletonList(() -> button);
    }

    @Override
    public void setReservation(Reservation newReservation, Appointment mutableAppointment) throws RaplaException
    {
        updateButton(newReservation);
        this.reservation = newReservation;
    }

    private void updateButton(Reservation newReservation)
    {
    	if ( button == null)
    	{
    		return;
    	}
    	final Map<Appointment, Set<Period>> periodConflicts;
        try
        {
            periodConflicts = getPeriodConflicts(Collections.singletonList(newReservation));
        }
        catch (RaplaException e)
        {
            getLogger().error( e.getMessage(),e);
            return;
        }
        int count = 0;
        for (Set<Period> periods : periodConflicts.values())
        {
            count += periods.size();
        }
        button.setText("Feiertage/Ferien (" + count + ")");
    }
}



