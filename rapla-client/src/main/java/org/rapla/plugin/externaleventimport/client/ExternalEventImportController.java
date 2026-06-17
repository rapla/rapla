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
import org.rapla.entities.dynamictype.internal.ClassificationImpl;
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
import org.rapla.plugin.externaleventimport.ImportItem;
import org.rapla.plugin.externaleventimport.SyncClassificationRequest;
import org.rapla.plugin.externaleventimport.SyncClassificationResult;
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

    public void syncReservation(ReservationEdit<?> reservationEdit, PopupContext popupContext)
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
            dialog.busy(popupContext);
            scheduler.supply(() -> service.loadEvents(criteria)).thenAccept(result -> {
                dialog.idle(popupContext);
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
                        ImportItem selected = findSingleSyncableItem(result, sourceItemIds);
                        if (selected == null)
                        {
                            dialogUiFactory.showWarning("Sync requires exactly one not-yet-imported item.", popupContext);
                            return;
                        }
                        SyncClassificationRequest req = new SyncClassificationRequest();
                        req.setSelectedItem(selected);
                        req.setClassification((ClassificationImpl) reservationEdit.getReservation().getClassification());
                        scheduler.supply(() -> service.syncClassification(req))
                                .thenAccept(syncResult -> applySyncResult(reservationEdit, syncResult, popupContext))
                                .exceptionally(ex -> { dialogUiFactory.showException(ex, popupContext); });
                    }

                    @Override
                    public boolean isValidSelection(List<String> sourceItemIds)
                    {
                        return findSingleSyncableItem(result, sourceItemIds) != null;
                    }
                };
                DialogInterface di = dialog.createImportDialog(popupContext, selectionModel, metadata, result, callback, true);
                di.start(false);
            }).exceptionally(ex -> {
                dialog.idle(popupContext);
                dialogUiFactory.showException(ex, popupContext);
            });
        }).exceptionally(ex -> { dialogUiFactory.showException(ex, popupContext); });
    }

    /** Sync accepts exactly one row, and only one that isn't already imported — sync *binds* an
     *  unbound reservation to a source event; refreshing imported ones is not supported. */
    static ImportItem findSingleSyncableItem(ExternalEventImportResult result, List<String> sourceItemIds)
    {
        if (sourceItemIds == null || sourceItemIds.size() != 1) return null;
        for (ImportItem item : result.getItems())
        {
            if (sourceItemIds.get(0).equals(item.getSourceItemId()))
            {
                return item.isImported() ? null : item;
            }
        }
        return null;
    }

    /** Sync applies the server-merged classification ONLY — as an undoable command on the
     *  editor's history, same entry as a type change via the dropdown. The returned
     *  allocatableIds stay on the wire for future UIs but are deliberately not applied here
     *  (PRD 068: allocations are a create-flow concern; the planner curates them manually). */
    private void applySyncResult(ReservationEdit<?> reservationEdit, SyncClassificationResult syncResult, PopupContext popupContext)
    {
        try
        {
            syncResult.getClassification().setResolver(raplaFacade.getOperator());
            reservationEdit.changeClassificationUndoable(syncResult.getClassification())
                    .exceptionally(ex -> { dialogUiFactory.showException(ex, popupContext); });
        }
        catch (Exception e)
        {
            dialogUiFactory.showException(e, popupContext);
        }
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
        dialog.busy(popupContext);
        scheduler.supply(() -> service.loadEvents(criteria)).thenAccept(result -> {
            dialog.idle(popupContext);
            if (result.getItems().isEmpty())
            {
                String msg = uiOverride(metadata, "no.events.found", "No events found for the selected items.");
                dialogUiFactory.showWarning(msg, popupContext);
                return;
            }
            ExternalEventImportSubmitCallback callback = createCallback(popupContext, picked, template, result);
            DialogInterface di = dialog.createImportDialog(popupContext, selectionModel, metadata, result, callback, false);
            di.start(false);
        }).exceptionally(ex -> {
            dialog.idle(popupContext);
            dialogUiFactory.showException(ex, popupContext);
        });
    }

    private ExternalEventImportSubmitCallback createCallback(PopupContext popupContext, Collection<Allocatable> picked,
            Allocatable template, ExternalEventImportResult result)
    {
        return new ExternalEventImportSubmitCallback()
        {
            @Override
            public void submit(List<String> sourceItemIds)
            {
                // relay the selected rows (with their sourceData) back to the server, which maps + builds
                List<org.rapla.plugin.externaleventimport.ImportItem> selectedItems = new ArrayList<>();
                for (org.rapla.plugin.externaleventimport.ImportItem item : result.getItems())
                {
                    if (sourceItemIds.contains(item.getSourceItemId())) selectedItems.add(item);
                }
                CreateReservationsRequest req = new CreateReservationsRequest();
                req.setSelectedItems(selectedItems);
                req.setSourceItemIds(sourceItemIds);
                if (template != null) req.setTemplateAllocatableId(template.getId());
                req.setAdditionalAllocatableIds(otherAllocatableIds(picked));
                fillCalendarInterval(req);
                scheduler.supply(() -> service.createReservations(req))
                        .thenAccept(reservations -> openEditor(reservations, popupContext))
                        .exceptionally(ex -> { dialogUiFactory.showException(ex, popupContext); });
            }

            @Override
            public boolean isValidSelection(List<String> sourceItemIds)
            {
                return !sourceItemIds.isEmpty();
            }
        };
    }

    /** The server returns un-persisted reservations; the REST deserialization (unlike the storage query
     *  path) doesn't resolve them, so wire them to the client operator before the editor accepts them. */
    private void openEditor(List<? extends Reservation> reservations, PopupContext popupContext)
    {
        if (reservations == null || reservations.isEmpty()) return;
        try
        {
            org.rapla.storage.StorageOperator operator = raplaFacade.getOperator();
            for (Reservation r : reservations)
            {
                if (r instanceof org.rapla.entities.storage.EntityReferencer)
                {
                    ((org.rapla.entities.storage.EntityReferencer) r).setResolver(operator);
                }
            }
            editController.edit(reservations, popupContext);
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
