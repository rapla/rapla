package org.rapla.plugin.externaleventimport;

import org.rapla.framework.RaplaException;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * Generic external-event-import contract. Any deployment that integrates with an
 * external event source (Dualis, SAP HR, etc.) provides one impl; the rapla wizard
 * is fully driven by the metadata returned from {@link #getMetadata()}.
 *
 * <p>{@link #uploadCsv(MultipartFile)} is optional — it must be implemented if
 * {@code metadata.supportsCsvImport == true}, otherwise the impl may throw
 * {@link UnsupportedOperationException}.
 */
@HttpExchange("/externaleventimport")
public interface ExternalEventImportService
{
    @GetExchange("/metadata")
    ExternalEventImportMetadata getMetadata() throws RaplaException;

    @PostExchange("/loadEvents")
    ExternalEventImportResult loadEvents(@RequestBody ImportCriteria criteria) throws RaplaException;

    @PostExchange("/uploadCsv")
    ExternalEventImportResult uploadCsv(@RequestPart("file") MultipartFile file) throws RaplaException;

    /**
     * Creates (or updates, in sync mode) {@code Reservation} entities from the IDs of
     * source items the user picked from a previous {@code loadEvents} / {@code uploadCsv}
     * result. All deployment-specific mapping (Dualis row → Reservation classification +
     * appointment, etc.) happens here on the server, so the client wizard carries no
     * domain logic.
     *
     * @return list of created/updated Reservation IDs the client can open an editor on
     */
    @PostExchange("/createReservations")
    java.util.List<String> createReservations(@RequestBody CreateReservationsRequest request) throws RaplaException;
}
