package org.rapla.plugin.tableview;

import org.rapla.framework.RaplaException;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.List;

/**
 * Server-side CSV export of the table surface (PRD 030 Phase 5).
 *
 * <p>Wire-shape mirrors {@link TableViewService#reservations}: the same
 * (from, to, columns, sort) inputs produce the same rows. The difference
 * is the response — CSV body rather than {@link TablePage} JSON.
 *
 * <p>Locale + columns are explicit request parameters; the server has no
 * ambient defaults to drift on (see PRD 030 Risk #4). Same inputs → same
 * bytes out.
 *
 * <p>Pagination is intentionally NOT exposed here: an export is a
 * "give me everything in one file" operation. Server enforces the same
 * safety cap as {@code /table/*} for the no-{@code pageSize} branch.
 *
 * <p><b>Returns synchronous types</b> — see {@link TableViewService}
 * Promise caveat.
 */
@HttpExchange("/api/export")
public interface ExportService
{
    /**
     * @param tableName  {@code "events"} or {@code "appointments"}
     * @param fromIso    inclusive start date in ISO format ({@code yyyy-MM-dd})
     * @param toIso      exclusive end date in ISO format
     * @param columnIds  ordered list of column ids to include in the export
     * @param sortSpecs  ordered sort directives, each {@code "<columnId>:<asc|desc>"}
     * @return raw CSV body — RFC 4180 framing, CRLF line endings, header row
     *         from {@link TableColumnDescriptor#label()}
     */
    @GetExchange(value = "/csv", accept = "text/csv")
    String csv(@RequestParam("tableName") String tableName,
               @RequestParam("from") String fromIso,
               @RequestParam("to") String toIso,
               @RequestParam(value = "columns", required = false) List<String> columnIds,
               @RequestParam(value = "sort", required = false) List<String> sortSpecs)
            throws RaplaException;
}
