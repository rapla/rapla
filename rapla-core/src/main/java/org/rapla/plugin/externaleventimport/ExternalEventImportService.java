package org.rapla.plugin.externaleventimport;

import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.framework.RaplaException;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

/**
 * Generic external-event-import contract. Any deployment that integrates with an
 * external event source (Dualis, SAP HR, etc.) provides one impl; the rapla wizard
 * is fully driven by the metadata returned from {@link #getMetadata()}.
 */
@HttpExchange("/api/externaleventimport")
public interface ExternalEventImportService
{
    @GetExchange("/metadata")
    ExternalEventImportMetadata getMetadata() throws RaplaException;

    @PostExchange("/loadEvents")
    ExternalEventImportResult loadEvents(@RequestBody ImportCriteria criteria) throws RaplaException;

    /**
     * Builds (but does not store) {@code Reservation} objects for the rows the user selected
     * from a previous {@code loadEvents} result. The request carries the selected
     * {@link ImportItem}s (with their {@code sourceData}) + the chosen template + the calendar
     * interval; all deployment-specific mapping (source row → classification + allocations,
     * template copy) happens here on the server, so the client carries no domain logic.
     *
     * @return the un-persisted reservations; the client resolves them against its operator,
     *         opens them in the editor, and the user saves (which persists them). The wire type
     *         is the concrete {@link ReservationImpl} (the {@code UpdateEvent} convention) so
     *         Jackson deserializes without an abstract-type mapping and springdoc emits a real
     *         OpenAPI model.
     */
    @PostExchange("/createReservations")
    List<ReservationImpl> createReservations(@RequestBody CreateReservationsRequest request) throws RaplaException;

    /**
     * Sync flow (PRD 068): merges the data of exactly one not-yet-imported source row into the
     * classification of a reservation already open in the editor. The server seeds from the
     * shipped classification ({@code newClassificationFrom}), applies the source values, and
     * resolves the row's referenced allocatables to rapla ids — it stores nothing and reads no
     * persisted reservation state; saving stays with the editor.
     */
    @PostExchange("/syncClassification")
    SyncClassificationResult syncClassification(@RequestBody SyncClassificationRequest request) throws RaplaException;

    /**
     * The {@code ExternalSyncEntity.externalSystem} id this deployment's import writes
     * (e.g. "DUALIS") — lets generic server code tell THIS source's bindings apart from
     * other {@code externalid}-annotation writers (iCal import stamps UIDs but creates
     * no sync entities). Not a wire endpoint. Null (the default) disables the
     * distinction → {@code externalEventLinkedReservationIds} returns empty.
     */
    default String getExternalSystemId()
    {
        return null;
    }
}
