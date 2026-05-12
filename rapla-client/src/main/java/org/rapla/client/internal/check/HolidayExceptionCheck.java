package org.rapla.client.internal.check;

import org.jetbrains.annotations.NotNull;
import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.RaplaTreeNode;
import org.rapla.client.TreeFactory;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.extensionpoints.EventCheck;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.Category;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Period;
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.PeriodModel;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.facade.internal.CalendarOptionsImpl;
import org.rapla.framework.RaplaException;
import org.rapla.scheduler.Promise;
import org.springframework.stereotype.Service;
import org.rapla.scheduler.ResolvedPromise;

import org.springframework.beans.factory.annotation.Autowired;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service

public class HolidayExceptionCheck implements EventCheck
{
    final ClientFacade clientFacade;
    final RaplaFacade raplaFacade;
    final RaplaResources i18n;
    private final TreeFactory treeFactory;
    final private HolidayCheckDialogView checkDialogView;
    final private DialogUiFactoryInterface dialogUiFactory;

    @Autowired
    public HolidayExceptionCheck(ClientFacade clientFacade, RaplaFacade raplaFacade, RaplaResources i18n, TreeFactory treeFactory,
            HolidayCheckDialogView checkDialogView, DialogUiFactoryInterface dialogUiFactory)
    {
        this.clientFacade = clientFacade;
        this.raplaFacade = raplaFacade;
        this.i18n = i18n;
        this.treeFactory = treeFactory;
        this.checkDialogView = checkDialogView;
        this.dialogUiFactory = dialogUiFactory;
    }

    /**
     * Thin wrapper over the pure-Java
     * {@link org.rapla.client.edit.check.HolidayWarningModel#findHolidayConflicts}.
     * Kept here as a {@code public static} method for callers that have
     * a {@link RaplaFacade} but not a {@link PeriodModel} directly.
     */
    @NotNull
    static public Map<Appointment, Set<Period>> getPeriodConflicts(RaplaFacade raplaFacade, Collection<Reservation> reservations) throws RaplaException
    {
        return org.rapla.client.edit.check.HolidayWarningModel.findHolidayConflicts(
                PeriodModel.getHoliday(raplaFacade), reservations);
    }

    @Override
    public Promise<Boolean> check(Collection<Reservation> reservations, PopupContext sourceComponent)
    {
        boolean showWarning;
        boolean showWarningSingleAppointments;
        try
        {
            final User user = clientFacade.getUser();
            Preferences preferences = raplaFacade.getPreferences(user);
            showWarning = preferences.getEntryAsBoolean(CalendarOptionsImpl.SHOW_HOLIDAY_WARNING, true);
            showWarningSingleAppointments = preferences.getEntryAsBoolean(CalendarOptionsImpl.SHOW_HOLIDAY_WARNING_SINGLE_APPOINTMENT, true);
        }
        catch (RaplaException e)
        {
            dialogUiFactory.showException( e, sourceComponent);
            return new ResolvedPromise( false);
        }

        final Map<Appointment, Set<Period>> periodConflicts;
        try
        {
            periodConflicts = getPeriodConflicts(raplaFacade, reservations);
        }
        catch (RaplaException e)
        {
            return new ResolvedPromise<>(e);
        }
        if (periodConflicts.isEmpty())
        {
            return new ResolvedPromise<>(true);
        }
        Map<Appointment, Set<Period>> filteredConflicts = org.rapla.client.edit.check.HolidayWarningModel
                .filterByPreference(periodConflicts, showWarning, showWarningSingleAppointments);
        if ( filteredConflicts.isEmpty()) {
            return new ResolvedPromise<>(true);
        }
        return showPeriodConflicts(sourceComponent, filteredConflicts, false).thenApply((list) -> list != null);
    }

    @NotNull
    public Promise<List<TimeInterval>> showPeriodConflicts(PopupContext sourceComponent, Map<Appointment, Set<Period>> periodConflicts, boolean showCheckbox)
    {
        Map<Category, List<Period>> list = mapToPeriodConflicts(periodConflicts);
        RaplaTreeNode root = createPeriodTree(list);
        final HolidayCheckDialogView.HolidayCheckPanel conflictPanel = checkDialogView.getConflictPanel(root, showCheckbox);
        DialogInterface dialog = dialogUiFactory
                .createContentDialog(sourceComponent, conflictPanel.component, new String[] {
                        i18n.getString("continue"), i18n.getString("cancel") });
        dialog.setDefault(1);
        dialog.setIcon(i18n.getIcon("icon.big_folder_conflicts"));
        dialog.getAction(0).setIcon(i18n.getIcon("icon.save"));
        dialog.getAction(1).setIcon(i18n.getIcon("icon.cancel"));
        dialog.setTitle(i18n.getString("appointment_collision_title"));
        return dialog.start(true).thenApply(index -> {
            if (index == 0)
            {
                List<TimeInterval> result = new ArrayList<>();
                 if (conflictPanel.checked)
                {
                    final Set selectedSet = conflictPanel.selectedItems;
                    for (Map.Entry<Appointment, Set<Period>> entry : periodConflicts.entrySet())
                    {
                        final Appointment appointment = entry.getKey();
                        final Set<Period> periods = entry.getValue();
                        final Repeating repeating = appointment.getRepeating();
                        for (Period period : periods)
                        {
                            if (selectedSet.contains(period) || !Collections.disjoint(period.getCategories(), selectedSet))
                            {
                                result.add(period.getInterval());
                            }
                        }

                    }
                }
                return result;
            }
            else
            {
                return null;
            }
        });
    }

    private RaplaTreeNode createPeriodTree(Map<Category, List<Period>> list)
    {
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
        return root;
    }

    @NotNull
    private Map<Category, List<Period>> mapToPeriodConflicts(Map<Appointment, Set<Period>> periodConflicts)
    {
        Set<Period> allPeriods = new TreeSet<>();
        for (Set<Period> periods : periodConflicts.values())
        {
            allPeriods.addAll(periods);
        }
        final Category periodsCategory = getPeriodsCategory();
        Map<Category, List<Period>> list = new LinkedHashMap<>();
        if (periodsCategory != null)
        {
            for (Category category : periodsCategory.getCategoryList())
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
        return list;
    }

    public Category getPeriodsCategory()
    {
        Category superCategory = raplaFacade.getSuperCategory();
        return PeriodModel.getPeriodsCategory( superCategory );
    }

}
