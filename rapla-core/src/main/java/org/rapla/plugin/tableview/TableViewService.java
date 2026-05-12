package org.rapla.plugin.tableview;

import org.rapla.framework.RaplaException;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.List;

/**
 * Server-side table projection endpoints (PRD 030 Phase 2).
 *
 * <p>The server queries reservations / appointment-blocks for {@code [from, to)},
 * projects them through the requested column set via
 * {@link TableViewEngine#project(java.util.Collection, java.util.List, SortSpec, PageSpec, java.util.function.Function)},
 * and returns a {@link TablePage} of scalar rows. Avoids re-implementing
 * the table column / sort / pagination logic in TypeScript for the
 * future Angular client.
 *
 * <p>Permission filter: server-authoritative via
 * {@code RaplaFacade.getReservations(user, ...)}. Unknown / unreadable
 * reservations are silently dropped (AGENTS.md §12).
 *
 * <p><b>Returns synchronous types</b> — same caveat as
 * {@code RemoteLocaleService} / {@code CalendarViewService}: Spring's
 * HttpServiceProxyFactory has no built-in Promise adapter. Async callers
 * should wrap in {@code commandScheduler.supply(...)}.
 */
@HttpExchange("/table")
public interface TableViewService
{
    /**
     * Reservation table — one row per reservation.
     *
     * @param fromIso     inclusive start date in ISO format ({@code yyyy-MM-dd})
     * @param toIso       exclusive end date in ISO format
     * @param columnIds   optional ordered list of column ids to project; when
     *                    omitted, the server uses the user's configured column
     *                    set from preferences
     * @param sortSpecs   optional list of sort directives, each of the form
     *                    {@code "<columnId>:<asc|desc>"}; applied in order
     * @param cursor      opaque cursor from a previous page's {@code nextCursor};
     *                    omit for the first page
     * @param pageSize    when present, return at most this many rows + a cursor;
     *                    when omitted, return all rows (subject to a server-side
     *                    safety cap, see {@link TablePage#incomplete()})
     */
    @GetExchange("/reservations")
    TablePage reservations(@RequestParam("from") String fromIso,
                           @RequestParam("to") String toIso,
                           @RequestParam(value = "columns", required = false) List<String> columnIds,
                           @RequestParam(value = "sort", required = false) List<String> sortSpecs,
                           @RequestParam(value = "cursor", required = false) String cursor,
                           @RequestParam(value = "pageSize", required = false) Integer pageSize)
            throws RaplaException;

    /**
     * Appointment-block table — one row per visible appointment block (each
     * occurrence of a repeating appointment within {@code [from, to)} is a
     * separate row). Same parameter contract as {@link #reservations(String, String, List, List, String, Integer)}.
     */
    @GetExchange("/appointments")
    TablePage appointments(@RequestParam("from") String fromIso,
                           @RequestParam("to") String toIso,
                           @RequestParam(value = "columns", required = false) List<String> columnIds,
                           @RequestParam(value = "sort", required = false) List<String> sortSpecs,
                           @RequestParam(value = "cursor", required = false) String cursor,
                           @RequestParam(value = "pageSize", required = false) Integer pageSize)
            throws RaplaException;
}
