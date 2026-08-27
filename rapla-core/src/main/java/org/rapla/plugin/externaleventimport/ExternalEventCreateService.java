package org.rapla.plugin.externaleventimport;

import java.time.LocalDateTime;
import java.util.List;

import org.rapla.entities.User;
import org.rapla.framework.RaplaException;

/**
 * Server-internal SPI for the v3 "Sync" create path (rapla PRD 104): turn staged external
 * events into STORED reservations in one transaction — deployment mapping (source row →
 * classification + allocations), per-type template copy, external-id stamp. The deployment
 * that provides the {@link ExternalEventSnapshotProvider} provides this too; without an
 * implementation the generic GraphQL mutation reports the feature as absent.
 *
 * <p>Placement semantics are the deployment's concern (rapla PRD 104: "parked" API,
 * concretized deployment-side — v1: every created reservation starts at {@code defaultStart}
 * with a default duration; the user places it via the normal calendar gestures).
 */
public interface ExternalEventCreateService
{
    /**
     * @param caller            authorization: booking right on each item's group allocatable;
     *                          unknown ids and forbidden ids fail identically
     * @param sourceItemIds     staged items to create
     * @param lectureTemplateId template (allocatable id) for non-exam items, null = deployment default
     * @param examTemplateId    template for exam items, null = deployment default
     * @param defaultStart      where unplaced items materialize (v1 placement)
     * @return the created reservation ids, in input order
     */
    List<String> createFromStagedItems(User caller, List<String> sourceItemIds, String lectureTemplateId,
            String examTemplateId, LocalDateTime defaultStart) throws RaplaException;

    /**
     * Binds a staged item to an EXISTING reservation (rapla PRD 104 "verknüpfen statt neu"):
     * the deployment applies its full external-id stamp (classification id attribute,
     * annotation, sync bookkeeping) so the item counts as LINKED from then on. No attribute
     * sync in v1 — only the binding.
     *
     * @return false when the reservation cannot carry the deployment's stamp (e.g. its type
     *         has no external-id attribute) — indistinguishable from the caller-side §12
     *         denials by design; true when the stamp was stored
     */
    boolean bindStagedItem(User caller, ImportItem stagedItem, String reservationId) throws RaplaException;
}
