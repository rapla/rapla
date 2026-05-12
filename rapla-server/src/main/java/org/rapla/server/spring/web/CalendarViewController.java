package org.rapla.server.spring.web;

import jakarta.servlet.http.HttpServletRequest;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.CalendarOptions;
import org.rapla.facade.RaplaComponent;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaLocale;
import org.rapla.storage.PermissionController;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.calendarview.BlockDecorator;
import org.rapla.plugin.calendarview.CalendarLayoutEngine;
import org.rapla.plugin.calendarview.CalendarPage;
import org.rapla.plugin.calendarview.CalendarViewService;
import org.rapla.plugin.calendarview.GroupBy;
import org.rapla.plugin.calendarview.LayoutStrategyId;
import org.rapla.plugin.calendarview.RaplaBlockDecorator;
import org.rapla.scheduler.Promise;
import org.rapla.server.RemoteSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * REST surface for the server-side calendar layout (PRD 024 Phase 3).
 * <p>
 * Thin glue: parse query params → query reservations via {@link RaplaFacade} →
 * call {@link CalendarLayoutEngine} → return {@link CalendarPage}.
 * Permission filter is the facade's existing per-user read scope —
 * {@code getReservations(user, ...)} only returns reservations the user
 * can read.
 */
@RestController
@ConditionalOnBean(RemoteSession.class)
@RequestMapping(value = "/calendar", produces = "application/json")
public class CalendarViewController implements CalendarViewService
{
    private final RemoteSession session;
    private final HttpServletRequest request;
    private final RaplaFacade facade;
    private final RaplaLocale raplaLocale;

    public CalendarViewController(RemoteSession session,
                                  HttpServletRequest request,
                                  RaplaFacade facade,
                                  RaplaLocale raplaLocale)
    {
        this.session = session;
        this.request = request;
        this.facade = facade;
        this.raplaLocale = raplaLocale;
    }

    @Override
    @GetMapping("/view")
    public CalendarPage view(@RequestParam("from") String fromIso,
                             @RequestParam("to") String toIso,
                             @RequestParam("strategy") LayoutStrategyId strategy,
                             @RequestParam("groupBy") GroupBy groupBy,
                             @RequestParam(value = "allocatables", required = false) List<String> allocatableIds)
            throws RaplaException
    {
        User user = session.checkAndGetUser(request);
        LocalDate from = LocalDate.parse(fromIso);
        LocalDate to   = LocalDate.parse(toIso);

        // facade.getReservations(user, …) already permission-filters per-user:
        // the user only sees reservations they can read.
        Collection<Reservation> reservations = waitFor(
                facade.getReservations(user, from.atStartOfDay(), to.atStartOfDay(), null));

        List<Allocatable> resourceFilter = resolveResourceFilter(allocatableIds, user);

        // Resolve the user's coloring preferences for this request. Both
        // pipelines (Swing's RaplaBlock and this server-side decorator)
        // call the same RaplaBuilder.getColorForClassifiable + BlockColors.resolve
        // helpers, so the colours emitted here match what Swing renders for
        // the same user + data.
        CalendarOptions options = RaplaComponent.getCalendarOptions(user, facade);
        BlockDecorator decorator = new RaplaBlockDecorator(
                options.isEventColoring(),
                options.isResourceColoring());

        return CalendarLayoutEngine.layout(from, to, strategy, groupBy, reservations, resourceFilter,
                raplaLocale.getLocale(), decorator);
    }

    /**
     * Resolve the requested allocatable ids AND drop any the user is not
     * permitted to read. This is the load-bearing permission gate — without
     * it, a user could probe arbitrary ids by passing them in the query
     * string and infer their existence from whether they appear as columns
     * in the response. We rebuild the column set only from ids the user
     * actually sees; an unknown or unreadable id is silently dropped.
     * <p>
     * See AGENTS.md §12 — never leak server-side info to a client beyond
     * what the user already sees in their permitted scope.
     */
    private List<Allocatable> resolveResourceFilter(List<String> ids, User user) throws RaplaException
    {
        if (ids == null || ids.isEmpty()) return null;
        PermissionController permissionController = facade.getPermissionController();
        List<Allocatable> out = new ArrayList<>(ids.size());
        for (String id : ids)
        {
            Allocatable a = facade.tryResolve(new ReferenceInfo<>(id, Allocatable.class));
            if (a == null) continue;
            if (!permissionController.canRead(a, user)) continue;
            out.add(a);
        }
        return out;
    }

    private static <T> T waitFor(Promise<T> promise) throws RaplaException
    {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> err = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        promise.thenAccept(value -> { result.set(value); done.countDown(); })
                .exceptionally(throwable -> { err.set(throwable); done.countDown(); });
        try
        {
            if (!done.await(30, TimeUnit.SECONDS))
            {
                throw new RaplaException("calendar query timed out");
            }
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new RaplaException("calendar query interrupted", e);
        }
        if (err.get() != null)
        {
            Throwable t = err.get();
            if (t instanceof RaplaException re) throw re;
            throw new RaplaException(t.getMessage(), t);
        }
        return result.get();
    }
}
