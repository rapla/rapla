package org.rapla.plugin.externaleventimport.client;

import org.junit.jupiter.api.Test;
import org.rapla.plugin.externaleventimport.ExternalEventImportResult;
import org.rapla.plugin.externaleventimport.ImportItem;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * PRD 068 sync flow, client side: the sync dialog only accepts exactly one not-yet-imported
 * row — sync binds an unbound reservation to a source event; already-imported rows are not
 * syncable. (The apply itself is classification-only and delegates to the editor's existing
 * {@code UndoReservationTypeChange} command — no plugin-side apply logic to test.)
 */
class ExternalEventImportSyncApplyTest
{
    @Test
    void syncSelectionAcceptsExactlyOneNotYetImportedRow()
    {
        ImportItem fresh = new ImportItem();
        fresh.setSourceItemId("v:1");
        ImportItem imported = new ImportItem();
        imported.setSourceItemId("v:2");
        imported.setImported(true);
        ExternalEventImportResult result = new ExternalEventImportResult(List.of(fresh, imported));

        assertSame(fresh, ExternalEventImportController.findSingleSyncableItem(result, List.of("v:1")));
        assertNull(ExternalEventImportController.findSingleSyncableItem(result, List.of("v:2")),
                "already-imported rows are not syncable");
        assertNull(ExternalEventImportController.findSingleSyncableItem(result, List.of("v:1", "v:2")),
                "sync requires exactly one row");
        assertNull(ExternalEventImportController.findSingleSyncableItem(result, List.of()));
        assertNull(ExternalEventImportController.findSingleSyncableItem(result, List.of("v:99")),
                "unknown id selects nothing");
    }
}
