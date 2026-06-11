package org.rapla.plugin.externaleventimport;

import org.rapla.entities.domain.Reservation;
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
     *         opens them in the editor, and the user saves (which persists them).
     */
    @PostExchange("/createReservations")
    List<Reservation> createReservations(@RequestBody CreateReservationsRequest request) throws RaplaException;
}
