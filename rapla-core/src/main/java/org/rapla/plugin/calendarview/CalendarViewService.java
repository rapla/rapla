package org.rapla.plugin.calendarview;

import org.rapla.framework.RaplaException;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.List;

/**
 * Server-side calendar layout endpoint (PRD 024 Phase 3).
 * <p>
 * The server queries reservations for {@code [from, to]}, runs the
 * requested {@link LayoutStrategyId} headlessly via the existing
 * {@code RaplaBuilder} + strategy pipeline in rapla-core, and returns
 * pre-positioned tiles as {@link CalendarPage}. This avoids re-implementing
 * Rapla's specific overlap / slot-packing semantics in TypeScript for the
 * future Angular client; both Swing (in-process) and Angular (over REST)
 * consume the same layout.
 * <p>
 * <b>Returns synchronous types</b> — same caveat as
 * {@code RemoteLocaleService}: Spring's HttpServiceProxyFactory has no
 * built-in Promise adapter. Async callers should wrap in
 * {@code commandScheduler.supply(...)}.
 */
@HttpExchange("/calendar")
public interface CalendarViewService
{
    /**
     * @param fromIso              inclusive start date in ISO format ({@code yyyy-MM-dd})
     * @param toIso                exclusive end date in ISO format
     * @param strategy             which layout strategy to run server-side
     * @param groupBy              column semantics ({@code DAY} vs {@code RESOURCE})
     * @param allocatableIds       optional filter — only blocks for these allocatables (reference ids).
     *                             {@code null} or empty means "all visible to user".
     */
    @GetExchange("/view")
    CalendarPage view(@RequestParam("from") String fromIso,
                      @RequestParam("to") String toIso,
                      @RequestParam("strategy") LayoutStrategyId strategy,
                      @RequestParam("groupBy") GroupBy groupBy,
                      @RequestParam(value = "allocatables", required = false) List<String> allocatableIds)
            throws RaplaException;
}
