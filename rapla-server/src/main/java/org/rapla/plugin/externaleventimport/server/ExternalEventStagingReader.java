package org.rapla.plugin.externaleventimport.server;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
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
 * Read and authorization helpers over the staging store for callers that act on staged items
 * (the create path). Needs only {@link ExternalEventAttribution}, so it wires on a node without
 * any connection to the source system.
 */
public class ExternalEventStagingReader
{
    private final JsonParserWrapper.JsonParser json = JsonParserWrapper.defaultJson().get();
    private final CachableStorageOperator operator;
    private final ExternalEventAttribution attribution;

    public ExternalEventStagingReader(CachableStorageOperator operator, ExternalEventAttribution attribution)
    {
        this.operator = operator;
        this.attribution = attribution;
    }

    /** Staged OPEN items for the given ids. Rows that are missing, ignored or already
     *  bound (derived via the external-id stamp) are silently absent, so callers cannot create
     *  duplicates and cannot tell an unknown id from one they may not see. */
    public List<ImportItem> loadOpenItems(Collection<String> sourceItemIds) throws RaplaException
    {
        final Set<String> wanted = new LinkedHashSet<>(sourceItemIds);
        final List<ImportItem> out = new ArrayList<>();
        for (ExternalSyncEntity row : operator
                .getImportExportEntities(attribution.systemId(), ImportExportDirections.IMPORT).values())
        {
            if (!ExternalEventStagingConstants.CONTEXT_EVENT_STAGING.equals(row.getContext()))
            {
                continue;
            }
            final StagedRowData data = StagedRowData.of(json, row);
            final ImportItem item = data.getSource();
            if (item == null || !wanted.contains(item.getSourceItemId()) || data.getIgnoredSince() != null)
            {
                continue;
            }
            if (StagedBinding.of(operator, attribution, item).state() != StagedBinding.State.OPEN)
            {
                continue;
            }
            out.add(item);
        }
        return out;
    }

    /** One staged item with its "changed" marker — for the ⓘ details (WP23x C4). */
    public record StagedDetails(ImportItem item, String changedSince,
            java.util.List<org.rapla.plugin.externaleventimport.ExternalEventAttribution.DetailField> fields)
    {
    }

    /** The staged item for the details view, in any binding state; missing and ignored rows are
     *  silently absent (null). */
    public StagedDetails loadDetails(String sourceItemId) throws RaplaException
    {
        // by the row id, not by scanning: unknown and forbidden cost the same (review round 19)
        final ExternalSyncEntity row = operator.getImportExportEntity(attribution.systemId(),
                ImportExportDirections.IMPORT, ExternalEventStagingConstants.idOf(sourceItemId));
        if (row == null || !ExternalEventStagingConstants.CONTEXT_EVENT_STAGING.equals(row.getContext()))
        {
            return null;
        }
        final StagedRowData data = StagedRowData.of(json, row);
        final ImportItem item = data.getSource();
        return item == null || data.getIgnoredSince() != null ? null
                : new StagedDetails(item, data.getChangedSince(), attribution.detailFields(item));
    }

    /** A bound item whose source changed since it was bound, with the reservation it is bound to
     *  and the marker it was read with (compare-and-clear, review round 16). */
    public record ChangedBoundItem(ImportItem item, String reservationId, String changedSince)
    {
    }

    /** Staged items that are BOUND and carry the "changed in the source" marker. Missing, ignored,
     *  open and unchanged rows are silently absent (dhbwrapla PRD 004 § H3a). */
    public List<ChangedBoundItem> loadChangedBoundItems(Collection<String> sourceItemIds) throws RaplaException
    {
        final java.util.Map<String, ExternalSyncEntity> rows = operator.getImportExportEntities(attribution.systemId(),
                ImportExportDirections.IMPORT);
        final List<ChangedBoundItem> out = new ArrayList<>();
        for (String sourceItemId : new LinkedHashSet<>(sourceItemIds))
        {
            final ExternalSyncEntity row = rows.get(ExternalEventStagingConstants.idOf(sourceItemId));
            if (row == null || !ExternalEventStagingConstants.CONTEXT_EVENT_STAGING.equals(row.getContext()))
            {
                continue;
            }
            final StagedRowData data = StagedRowData.of(json, row);
            final ImportItem item = data.getSource();
            if (item == null || data.getIgnoredSince() != null || data.getChangedSince() == null)
            {
                continue;
            }
            final StagedBinding.Binding binding = StagedBinding.of(operator, attribution, item);
            if (binding.state() == StagedBinding.State.BOUND)
            {
                out.add(new ChangedBoundItem(item, binding.reference().getId(), data.getChangedSince()));
            }
        }
        return out;
    }

    /** §12 gate for the create path: the groups of each item the caller may actually book.
     *  Items without such a group are absent from the result — unknown, unattributable and
     *  forbidden are indistinguishable, and the caller reports "n requested, m created".
     *
     *  @return sourceItemId → bookable group allocatable ids */
    public Map<String, Collection<String>> bookableGroups(User caller, PermissionController permissionController,
            Collection<ImportItem> items) throws RaplaException
    {
        final Map<String, Collection<String>> groups = attribution.resolveGroups(items);
        final LocalDate today = operator.today();
        final Map<String, Collection<String>> out = new java.util.LinkedHashMap<>();
        for (ImportItem item : items)
        {
            final Collection<String> candidates = groups.get(item.getSourceItemId());
            if (candidates == null)
            {
                continue;
            }
            final Collection<String> bookable = new ArrayList<>();
            for (String allocatableId : candidates)
            {
                final Allocatable allocatable = operator
                        .tryResolve(new ReferenceInfo<>(allocatableId, Allocatable.class));
                if (allocatable != null && permissionController.canAllocate(allocatable, caller, today))
                {
                    bookable.add(allocatableId);
                }
            }
            if (!bookable.isEmpty())
            {
                out.put(item.getSourceItemId(), bookable);
            }
        }
        return out;
    }
}
