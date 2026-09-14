package org.rapla.plugin.externaleventimport;

import java.util.Collection;
import java.util.Map;

import org.rapla.framework.RaplaException;

/**
 * What a deployment must know about its staged events that can be answered from the rapla store
 * alone — no external system involved.
 *
 * <p>Split off {@link ExternalEventSnapshotProvider} on purpose: reading the source needs the
 * external system (for dhbw a campusnet connection), while attribution, binding and scope do
 * not. A node that only serves the worklist or the create path therefore wires this interface
 * and needs no connection to the source at all. Bundling both was what coupled the web tier to
 * the sync tier.
 */
public interface ExternalEventAttribution
{
    /** External-system id the staged rows are stored under, e.g. {@code "DUALIS"}. */
    String systemId();

    /**
     * Maps items to the allocatables that carry their booking rights. Batch, because a full
     * snapshot can be tens of thousands of items.
     *
     * @return {@code sourceItemId} → allocatable ids; a missing or empty entry means "cannot be
     *         attributed". Callers re-check each id against the caller's rights and take group
     *         names from the resolved allocatable — never from this mapping — so an id the
     *         caller may not see leaks neither name nor existence.
     */
    Map<String, Collection<String>> resolveGroups(Collection<ImportItem> items) throws RaplaException;

    /**
     * The external id under which an imported item is stamped on its reservation, e.g.
     * {@code "Lehrveranstaltung:<dualisId>"}. Binding is derived from this rather than stored,
     * so a staged row can never drift away from the stamp on the reservation.
     */
    String externalIdOf(ImportItem item) throws RaplaException;

    /**
     * Optional retention scope, e.g. the semester an item belongs to. Rows whose scope has left
     * the snapshot entirely are deleted rather than marked vanished — a source that drops whole
     * scopes on rollover is history, not cancellation.
     *
     * @return {@code null} (the default) to disable the rule.
     */
    default String scopeKeyOf(ImportItem item)
    {
        return null;
    }
}
