package org.rapla.plugin.externaleventimport.server;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.storage.ExternalSyncEntity;
import org.rapla.entities.storage.ImportExportDirections;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.externaleventimport.ExternalEventAttribution;
import org.rapla.plugin.externaleventimport.ImportItem;
import org.rapla.rest.JsonParserWrapper;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.PermissionController;

/**
 * Builds the Abgleich worklist over the staging store.
 *
 * <p>§12 at the output boundary: the scope is BOOKING RIGHTS on the group allocatable, which is
 * stricter than the read floor. An item is only ever surfaced under a group the caller may
 * book; an item whose groups are all unattributable, unreadable or unbookable is dropped whole.
 * "This group does not exist" and "you may not book this group" therefore produce identical
 * responses, and no name, id or count of a hidden group leaks. Group names come from the
 * resolved allocatable, never from the provider's mapping.
 *
 * <p>Always scoped to the groups the caller asks for — there is no "everything I may book" mode
 * (decided 2026-08-11). Work happens per Kurs, so a query returns dozens of items, not tens of
 * thousands, and neither paging nor a cache is needed.
 *
 * <p>Needs only {@link ExternalEventAttribution}, never the snapshot provider: a node that
 * serves the worklist requires no connection to the source system.
 *
 * <p>Group resolution runs per query rather than being stored on the row, so a renamed or newly
 * created group takes effect immediately instead of drifting until the next reconciliation.
 */
public class ExternalEventWorklistAssembler
{
    private final JsonParserWrapper.JsonParser json = JsonParserWrapper.defaultJson().get();
    private final CachableStorageOperator operator;
    private final ExternalEventAttribution provider;

    public ExternalEventWorklistAssembler(CachableStorageOperator operator, ExternalEventAttribution provider)
    {
        this.operator = operator;
        this.provider = provider;
    }

    public WorklistView assemble(User caller, PermissionController permissionController,
            Collection<String> allocatableIds, String scopeKey) throws RaplaException
    {
        if (caller == null || permissionController == null || allocatableIds == null || allocatableIds.isEmpty())
        {
            return WorklistView.empty();
        }
        final Set<String> requested = new java.util.LinkedHashSet<>(allocatableIds);

        final List<ImportItem> items = new ArrayList<>();
        final Map<String, StagedRowData> rowsBySourceId = new LinkedHashMap<>();
        StagingMeta meta = null;

        for (ExternalSyncEntity row : operator
                .getImportExportEntities(provider.systemId(), ImportExportDirections.IMPORT).values())
        {
            if (ExternalEventStagingConstants.CONTEXT_EVENT_STAGING_META.equals(row.getContext()))
            {
                meta = parse(row, StagingMeta.class, StagingMeta::new);
                continue;
            }
            if (!ExternalEventStagingConstants.CONTEXT_EVENT_STAGING.equals(row.getContext()))
            {
                continue;
            }
            final StagedRowData data = StagedRowData.of(json, row);
            final ImportItem item = data.getSource();
            if (item == null || item.getSourceItemId() == null)
            {
                continue;
            }
            if (scopeKey != null && !scopeKey.equals(data.getScopeKey()))
            {
                continue;
            }
            if (StagedBinding.of(operator, provider, item).state() == StagedBinding.State.UNDETERMINED)
            {
                // Not listed: the create path cannot offer what it cannot duplicate-check, so
                // showing it here would promise work that silently does nothing.
                continue;
            }
            items.add(item);
            rowsBySourceId.put(item.getSourceItemId(), data);
        }

        final Map<String, Collection<String>> groupIds = items.isEmpty() ? Map.of() : provider.resolveGroups(items);
        final Map<String, Allocatable> bookable = new LinkedHashMap<>();
        final LocalDate today = operator.today();
        final Map<String, List<WorklistItem>> byGroup = new LinkedHashMap<>();

        for (ImportItem item : items)
        {
            final Collection<String> candidates = groupIds.get(item.getSourceItemId());
            if (candidates == null)
            {
                continue;
            }
            final StagedRowData data = rowsBySourceId.get(item.getSourceItemId());
            WorklistItem worklistItem = null;
            for (String allocatableId : candidates)
            {
                if (!requested.contains(allocatableId))
                {
                    continue;
                }
                final Allocatable group = bookable.computeIfAbsent(allocatableId,
                        id -> resolveBookable(id, caller, permissionController, today));
                if (group == null)
                {
                    continue;
                }
                if (worklistItem == null)
                {
                    worklistItem = toWorklistItem(item, data, caller, permissionController);
                }
                byGroup.computeIfAbsent(allocatableId, id -> new ArrayList<>()).add(worklistItem);
            }
        }

        final List<WorklistGroup> groups = new ArrayList<>(byGroup.size());
        WorklistCounts total = WorklistCounts.zero();
        for (Map.Entry<String, List<WorklistItem>> entry : byGroup.entrySet())
        {
            final List<WorklistItem> groupItems = entry.getValue();
            final WorklistCounts counts = WorklistCounts.of(groupItems);
            final Allocatable group = bookable.get(entry.getKey());
            groups.add(new WorklistGroup(entry.getKey(), group.getName(null), groupItems, counts));
            total = total.plus(counts);
        }
        groups.sort(Comparator.comparing(WorklistGroup::name, Comparator.nullsLast(Comparator.naturalOrder())));

        final Long lastRefresh = meta != null && meta.getLastFinished() > 0 ? meta.getLastFinished() : null;
        return new WorklistView(groups, total, lastRefresh, meta != null ? meta.getLastError() : null);
    }

    private Allocatable resolveBookable(String allocatableId, User caller, PermissionController permissionController,
            LocalDate today)
    {
        final Allocatable allocatable;
        try
        {
            allocatable = operator.tryResolve(new ReferenceInfo<>(allocatableId, Allocatable.class));
        }
        catch (RuntimeException ex)
        {
            return null;
        }
        if (allocatable == null || !permissionController.canAllocate(allocatable, caller, today))
        {
            return null;
        }
        return allocatable;
    }

    private WorklistItem toWorklistItem(ImportItem item, StagedRowData data, User caller,
            PermissionController permissionController) throws RaplaException
    {
        final StagedBinding.Binding bound = StagedBinding.of(operator, provider, item);
        final StagedBinding.State binding = bound.state();
        final StagedEventState state;
        if (data.getIgnoredSince() != null)
        {
            state = StagedEventState.IGNORED;
        }
        else if (binding != StagedBinding.State.BOUND)
        {
            state = StagedEventState.OPEN;
        }
        else if (data.getChangedSince() != null)
        {
            state = StagedEventState.CHANGED;
        }
        else
        {
            state = StagedEventState.LINKED;
        }
        final List<WorklistColumn> columns = new ArrayList<>();
        for (Map.Entry<String, Object> column : item.getColumns().entrySet())
        {
            columns.add(new WorklistColumn(column.getKey(),
                    column.getValue() != null ? column.getValue().toString() : null));
        }
        return new WorklistItem(item.getSourceItemId(), data.getScopeKey(), state, columns, data.getChangedSince(),
                data.getIgnoredSince(),
                readableReservationId(bound, caller, permissionController));
    }

    /** §12: the bound reservation's id only when the caller may read it — otherwise null, the
     *  same answer an unbound item gives. */
    private String readableReservationId(StagedBinding.Binding bound, User caller,
            PermissionController permissionController)
    {
        if (bound.state() != StagedBinding.State.BOUND || bound.reference() == null)
        {
            return null;
        }
        final org.rapla.entities.Entity entity = operator.tryResolve(bound.reference());
        if (!(entity instanceof org.rapla.entities.domain.Reservation reservation))
        {
            return null;
        }
        return permissionController.canRead(reservation, caller) ? reservation.getId() : null;
    }

    private <T> T parse(ExternalSyncEntity row, Class<T> type, java.util.function.Supplier<T> fallback)
    {
        final String data = row.getData();
        if (data == null || data.isEmpty())
        {
            return fallback.get();
        }
        return json.fromJson(data, type);
    }
}
