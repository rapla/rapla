package org.rapla.plugin.externaleventimport.client;

import org.rapla.client.EditController;
import org.rapla.client.PopupContext;
import org.rapla.client.ReservationEdit;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.client.ClientFacade;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.externaleventimport.CreateReservationsRequest;
import org.rapla.plugin.externaleventimport.ExternalEventImportMetadata;
import org.rapla.plugin.externaleventimport.ExternalEventImportPlugin;
import org.rapla.plugin.externaleventimport.ExternalEventImportResult;
import org.rapla.plugin.externaleventimport.ExternalEventImportService;
import org.rapla.plugin.externaleventimport.ImportCriteria;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.scheduler.Promise;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Wizard controller. All deployment-specific knowledge (which "course" type to query,
 * how to map result rows into Reservations, etc.) lives server-side in the
 * {@link ExternalEventImportService} impl and its returned
 * {@link ExternalEventImportMetadata}. The controller's job is just to orchestrate:
 * fetch metadata, pick allocatables, fetch results, render dialog, submit selections.
 */
@Service
public class ExternalEventImportController
{
    private final ClientFacade clientFacade;
    private final RaplaFacade raplaFacade;
    private final CalendarSelectionModel selectionModel;
    private final ExternalEventImportDialog dialog;
    private final DialogUiFactoryInterface dialogUiFactory;
    private final ExternalEventImportService service;
    private final EditController editController;
    private final CommandScheduler scheduler;

    private volatile ExternalEventImportMetadata cachedMetadata;

    @Autowired
    public ExternalEventImportController(ClientFacade clientFacade, CalendarSelectionModel selectionModel, ExternalEventImportDialog dialog,
            DialogUiFactoryInterface dialogUiFactory, ExternalEventImportService service, EditController editController, CommandScheduler scheduler)
    {
        this.clientFacade = clientFacade;
        this.raplaFacade = clientFacade.getRaplaFacade();
        this.selectionModel = selectionModel;
        this.dialog = dialog;
        this.dialogUiFactory = dialogUiFactory;
        this.service = service;
        this.editController = editController;
        this.scheduler = scheduler;
    }

    public Promise<ExternalEventImportMetadata> getMetadata()
    {
        if (cachedMetadata != null) return scheduler.supply(() -> cachedMetadata);
        return scheduler.supply(() -> {
            ExternalEventImportMetadata m = service.getMetadata();
            cachedMetadata = m;
            return m;
        });
    }

    public void importEvents(PopupContext popupContext, Allocatable template)
    {
        getMetadata().thenAccept(metadata -> {
            try
            {
                Collection<Allocatable> available = getSelectableAllocatables(metadata);
                Collection<Allocatable> preselected = getPreselectedAllocatables(available);
                if (!preselected.isEmpty() || metadata.getSelectableAllocatableTypeKey() == null
                        || metadata.getSelectableAllocatableTypeKey().isEmpty())
                {
                    loadAndShow(popupContext, metadata, preselected, template);
                }
                else
                {
                    dialog.showAllocatableSelection(metadata, available, preselected)
                            .thenAccept(picked -> loadAndShow(popupContext, metadata, picked, template))
                            .exceptionally(ex -> { dialogUiFactory.showException(ex, popupContext); });
                }
            }
            catch (RaplaException e)
            {
                dialogUiFactory.showException(e, popupContext);
            }
        }).exceptionally(ex -> { dialogUiFactory.showException(ex, popupContext); });
    }

    public void syncReservation(ReservationEdit reservationEdit, PopupContext popupContext)
    {
        getMetadata().thenAccept(metadata -> {
            Reservation reservation = reservationEdit.getReservation();
            Collection<Allocatable> matched = getAllocatablesOfType(reservation.getAllocatables(), metadata.getSelectableAllocatableTypeKey());
            if (matched.isEmpty())
            {
                dialogUiFactory.showWarning("Cannot sync: reservation has no allocatables of the selectable type.", popupContext);
                return;
            }
            ImportCriteria criteria = new ImportCriteria(null, idsOf(matched));
            dialog.busy();
            scheduler.supply(() -> service.loadEvents(criteria)).thenAccept(result -> {
                dialog.idle();
                if (result.getItems().isEmpty())
                {
                    String msg = uiOverride(metadata, "no.events.found", "No events found for the selected items.");
                    dialogUiFactory.showWarning(msg, popupContext);
                    return;
                }
                ExternalEventImportSubmitCallback callback = new ExternalEventImportSubmitCallback()
                {
                    @Override
                    public void submit(List<String> sourceItemIds)
                    {
                        if (sourceItemIds.size() != 1)
                        {
                            dialogUiFactory.showWarning("Sync requires exactly one selected item.", popupContext);
                            return;
                        }
                        CreateReservationsRequest req = new CreateReservationsRequest();
                        req.setSourceItemIds(sourceItemIds);
                        req.setUpdateExistingReservation(true);
                        req.setExistingReservationId(reservation.getId());
                        scheduler.supply(() -> service.createReservations(req)).thenAccept(ids -> {
                            reservationEdit.setHasChanged(true);
                        }).exceptionally(ex -> { dialogUiFactory.showException(ex, popupContext); });
                    }

                    @Override
                    public boolean isValidSelection(List<String> sourceItemIds)
                    {
                        return sourceItemIds.size() == 1;
                    }
                };
                DialogInterface di = dialog.createImportDialog(popupContext, selectionModel, metadata, result, callback, true);
                di.start(false);
            }).exceptionally(ex -> {
                dialog.idle();
                dialogUiFactory.showException(ex, popupContext);
            });
        }).exceptionally(ex -> { dialogUiFactory.showException(ex, popupContext); });
    }

    private void loadAndShow(PopupContext popupContext, ExternalEventImportMetadata metadata, Collection<Allocatable> picked, Allocatable template)
    {
        if (metadata.getSelectableAllocatableTypeKey() != null && !metadata.getSelectableAllocatableTypeKey().isEmpty()
                && picked.isEmpty())
        {
            String leafLabel = leafLevelLabel(metadata, "item");
            String key = "no_item_selected";
            String msg = uiOverride(metadata, key, "No " + leafLabel + " selected");
            dialogUiFactory.showWarning(msg, popupContext);
            return;
        }
        int max = metadata.getMaxSelectableItems();
        if (max > 0 && picked.size() > max)
        {
            String leafLabel = leafLevelLabel(metadata, "item");
            String msg = uiOverride(metadata, "too_many_items_selected",
                    "Too many " + leafLabel + " selected. Maximum is " + max + ".");
            dialogUiFactory.showWarning(msg, popupContext);
            return;
        }
        ImportCriteria criteria = new ImportCriteria(null, idsOf(picked));
        dialog.busy();
        scheduler.supply(() -> service.loadEvents(criteria)).thenAccept(result -> {
            dialog.idle();
            if (result.getItems().isEmpty())
            {
                String msg = uiOverride(metadata, "no.events.found", "No events found for the selected items.");
                dialogUiFactory.showWarning(msg, popupContext);
                return;
            }
            ExternalEventImportSubmitCallback callback = createCallback(popupContext, picked, template);
            DialogInterface di = dialog.createImportDialog(popupContext, selectionModel, metadata, result, callback, false);
            di.start(false);
        }).exceptionally(ex -> {
            dialog.idle();
            dialogUiFactory.showException(ex, popupContext);
        });
    }

    private ExternalEventImportSubmitCallback createCallback(PopupContext popupContext, Collection<Allocatable> picked, Allocatable template)
    {
        return new ExternalEventImportSubmitCallback()
        {
            @Override
            public void submit(List<String> sourceItemIds)
            {
                CreateReservationsRequest req = new CreateReservationsRequest();
                req.setSourceItemIds(sourceItemIds);
                if (template != null) req.setTemplateAllocatableId(template.getId());
                req.setAdditionalAllocatableIds(otherAllocatableIds(picked));
                fillCalendarInterval(req);
                scheduler.supply(() -> service.createReservations(req))
                        .thenAccept(reservationIds -> openEditor(reservationIds, popupContext))
                        .exceptionally(ex -> { dialogUiFactory.showException(ex, popupContext); });
            }

            @Override
            public boolean isValidSelection(List<String> sourceItemIds)
            {
                return !sourceItemIds.isEmpty();
            }
        };
    }

    private void openEditor(List<String> reservationIds, PopupContext popupContext)
    {
        if (reservationIds == null || reservationIds.isEmpty()) return;
        try
        {
            List<Reservation> resolved = new ArrayList<>();
            for (String id : reservationIds)
            {
                ReferenceInfo<Reservation> ref = new ReferenceInfo<>(id, Reservation.class);
                Reservation r = raplaFacade.tryResolve(ref);
                if (r != null) resolved.add(r);
            }
            if (!resolved.isEmpty()) editController.edit(resolved, popupContext);
        }
        catch (Exception e)
        {
            dialogUiFactory.showException(e, popupContext);
        }
    }

    private List<String> otherAllocatableIds(Collection<Allocatable> picked)
    {
        try
        {
            Collection<Allocatable> selected = selectionModel.getSelectedAllocatablesAsList();
            return selected.stream().filter(a -> !picked.contains(a)).map(Allocatable::getId).collect(Collectors.toList());
        }
        catch (RaplaException e)
        {
            return new ArrayList<>();
        }
    }

    private void fillCalendarInterval(CreateReservationsRequest req)
    {
        Collection<TimeInterval> markedIntervals = selectionModel.getMarkedIntervals();
        LocalDateTime start = selectionModel.getStartDate();
        LocalDateTime end = selectionModel.getEndDate();
        if (!markedIntervals.isEmpty())
        {
            TimeInterval iv = markedIntervals.iterator().next();
            start = iv.getStart();
            end = iv.getEnd();
        }
        if (start != null) req.setCalendarStart(start);
        if (end != null) req.setCalendarEnd(end);
    }

    private Collection<Allocatable> getSelectableAllocatables(ExternalEventImportMetadata metadata) throws RaplaException
    {
        String typeKey = metadata.getSelectableAllocatableTypeKey();
        if (typeKey == null || typeKey.isEmpty()) return List.of();
        DynamicType type = raplaFacade.getDynamicType(typeKey);
        if (type == null) return List.of();
        ClassificationFilter[] filters = type.newClassificationFilter().toArray();
        return new LinkedHashSet<>(Arrays.asList(raplaFacade.getAllocatablesWithFilter(filters)));
    }

    private Collection<Allocatable> getPreselectedAllocatables(Collection<Allocatable> available) throws RaplaException
    {
        Collection<Allocatable> selected = selectionModel.getSelectedAllocatablesAsList();
        Collection<Allocatable> preselected = new ArrayList<>();
        for (Allocatable a : selected)
        {
            if (available.contains(a)) preselected.add(a);
        }
        return preselected;
    }

    private static Collection<Allocatable> getAllocatablesOfType(Allocatable[] all, String typeKey)
    {
        Collection<Allocatable> result = new ArrayList<>();
        if (typeKey == null || typeKey.isEmpty()) return result;
        for (Allocatable a : all)
        {
            if (typeKey.equals(a.getClassification().getType().getKey())) result.add(a);
        }
        return result;
    }

    private static List<String> idsOf(Collection<Allocatable> allocatables)
    {
        List<String> ids = new ArrayList<>(allocatables.size());
        for (Allocatable a : allocatables) ids.add(a.getId());
        return ids;
    }

    private static String uiOverride(ExternalEventImportMetadata metadata, String key, String fallback)
    {
        if (metadata.getUiMessageOverrides() != null)
        {
            String v = metadata.getUiMessageOverrides().get(key);
            if (v != null) return v;
        }
        return fallback;
    }

    public static String leafLevelLabel(ExternalEventImportMetadata metadata, String fallback)
    {
        if (metadata.getHierarchyLevels() == null || metadata.getHierarchyLevels().isEmpty()) return fallback;
        return metadata.getHierarchyLevels().get(metadata.getHierarchyLevels().size() - 1).label();
    }
}
