package org.rapla.plugin.externaleventimport;

import java.util.Collection;

import org.rapla.framework.RaplaException;

/**
 * Reads the external source. Wired ONLY on the node that runs the reconciliation job — it is
 * the single method that needs a connection to the source system, which is why everything a
 * reader needs lives on {@link ExternalEventAttribution} instead.
 *
 * <p>Deliberately NOT part of {@link ExternalEventImportService}: that is an
 * {@code @HttpExchange} contract for the wizard client, while the staging engine calls this
 * in-process.
 */
public interface ExternalEventSnapshotProvider extends ExternalEventAttribution
{
    /**
     * The COMPLETE current snapshot of the source. Must either return everything or throw — the
     * reconciliation infers "gone" from absence, so a partially read snapshot would mark the
     * missing rows as vanished or delete them.
     *
     * <p>Item field order must be stable across calls: the engine detects "unchanged" by
     * comparing the serialized item, and an unstable map order would rewrite every row on every
     * run.
     */
    Collection<ImportItem> readAll() throws RaplaException;

    /**
     * The current source rows of exactly these source item ids (PRD 004 OQ6 refresh). A missing
     * id means the source no longer has it. Must either return every requested row or throw.
     */
    default Collection<ImportItem> readByIds(Collection<String> sourceItemIds) throws RaplaException
    {
        final java.util.Set<String> ids = new java.util.HashSet<>(sourceItemIds);
        return readAll().stream().filter(item -> ids.contains(item.getSourceItemId())).toList();
    }
}
