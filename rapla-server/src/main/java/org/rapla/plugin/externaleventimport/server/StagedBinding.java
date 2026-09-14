package org.rapla.plugin.externaleventimport.server;

import org.rapla.framework.RaplaException;
import org.rapla.plugin.externaleventimport.ExternalEventAttribution;
import org.rapla.plugin.externaleventimport.ImportItem;
import org.rapla.storage.CachableStorageOperator;

/**
 * The single answer to "is this staged item already in rapla?", shared by the worklist and the
 * create path.
 *
 * <p>They must never disagree: a reader that calls an item OPEN while the create path considers
 * it bound offers work that then silently does nothing — observed 2026-08-11, when a worklist
 * listed four exams as OPEN and the create path dropped them (stamp state was in flux from a
 * concurrent undo). Neither path may swallow an error into a state.
 */
final class StagedBinding
{
    enum State
    {
        /** No entity carries the item's external id — free to create. */
        OPEN,
        /** An entity carries it — creating again would duplicate. */
        BOUND,
        /** The provider cannot say (no external id). Not offered anywhere: an item we cannot
         *  check cannot be protected against duplication. */
        UNDETERMINED
    }

    /** @param reference the bound entity, non-null exactly when {@code state == BOUND} */
    record Binding(State state, org.rapla.entities.storage.ReferenceInfo reference)
    {
    }

    private StagedBinding()
    {
    }

    static Binding of(CachableStorageOperator operator, ExternalEventAttribution attribution, ImportItem item)
            throws RaplaException
    {
        final String externalId = attribution.externalIdOf(item);
        if (externalId == null)
        {
            return new Binding(State.UNDETERMINED, null);
        }
        final org.rapla.entities.storage.ReferenceInfo reference = operator.tryResolveExternalId(externalId);
        return reference != null ? new Binding(State.BOUND, reference) : new Binding(State.OPEN, null);
    }
}
