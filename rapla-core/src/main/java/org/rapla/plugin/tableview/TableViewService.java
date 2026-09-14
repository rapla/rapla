package org.rapla.plugin.tableview;

import org.rapla.framework.RaplaException;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * Server-side table projection endpoints.
 *
 * <p>The server resolves the caller's tree-selection from
 * {@link TableQueryRequest#allocatables} / {@link TableQueryRequest#types} /
 * {@link TableQueryRequest#owners}, applies
 * {@link TableQueryRequest#reservationFilter} as a classification filter,
 * queries via the same {@code CalendarModel.queryReservationsSync} path the
 * week view uses, then projects the result through the requested column set
 * via {@link TableViewEngine#project(java.util.Collection, java.util.List, SortSpec, PageSpec, java.util.function.Function)}.
 * The TypeScript / SPA client avoids re-implementing rapla's column / sort
 * logic; Swing's table view shares the same wire contract.
 *
 * <p>Permission filter is built into the {@code CalendarModel} path —
 * resources the caller can't read are silently dropped (AGENTS.md §12).
 *
 * <p>POST rather than GET because {@code reservationFilter} mirrors the
 * full in-process {@code ClassificationFilter[]} shape, nested deeper than
 * a query string handles cleanly. Same trade {@code /api/storage/queryAppointments}
 * makes. No server-side pagination: the row payload is N×(columnCount × scalar)
 * after projection. A {@code DEFAULT_CAP} safety limit prevents unbounded
 * responses; when exceeded the response carries {@code incomplete: true}.
 *
 * <p><b>Returns synchronous types</b> — same caveat as
 * {@code RemoteLocaleService}: Spring's HttpServiceProxyFactory has no
 * built-in Promise adapter. Async callers should wrap in
 * {@code commandScheduler.supply(...)}.
 */
@HttpExchange("/api/table")
public interface TableViewService
{
    /** Reservation table — one row per reservation. */
    @PostExchange("/reservations")
    TablePage reservations(@RequestBody TableQueryRequest body) throws RaplaException;

    /**
     * Appointment-block table — one row per visible appointment block (each
     * occurrence of a repeating appointment within {@code [from, to)} is a
     * separate row). Same request shape as {@link #reservations}.
     */
    @PostExchange("/appointments")
    TablePage appointments(@RequestBody TableQueryRequest body) throws RaplaException;

    /**
     * The user's visible column set for {@code tableName} ({@code "events"}
     * or {@code "appointments"}). Server reads from per-user preferences —
     * same store Swing's {@code TableviewOption} writes to, so settings
     * survive device switches and stay consistent between the Swing and
     * Angular clients. Ordered: the response's {@code columns} list defines
     * display order.
     */
    @GetExchange("/config")
    TableColumnsResponse config(@RequestParam("tableName") String tableName) throws RaplaException;

    /**
     * The universe of available columns for {@code tableName} — every column
     * the system knows about, including plugin contributions, regardless of
     * whether the current user has it in their visible set. Lets the Angular
     * UI offer "add this column" without re-fetching the universe per request.
     */
    @GetExchange("/columns/catalog")
    TableColumnsResponse columnsCatalog(@RequestParam("tableName") String tableName) throws RaplaException;
}
