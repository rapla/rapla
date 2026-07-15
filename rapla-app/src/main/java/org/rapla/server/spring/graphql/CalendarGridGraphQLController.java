package org.rapla.server.spring.graphql;

import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.rapla.plugin.timeslot.TimeslotProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

/**
 * PRD 097 Phase 5 — the 2D-grid GraphQL surface: {@code strips(filter:)} and the
 * {@code AppointmentBlock} geometry/classification fields ({@code segments}, {@code bars(scope:)},
 * {@code banner}, {@code wholeDay}, {@code timeslot}). All geometry is pure
 * {@link CalendarGridLayout} math over the query's OWN §12-gated result — no entity plumbing, no
 * layout engine (the template does the pixels via number substitution + CSS grid).
 *
 * <p>{@code lane}/{@code row} are LIST-SCOPED: {@code appointmentBlocks} stashes its returned page
 * (+ its filter window) into the {@link GraphQLContext}; each primitive is computed lazily on
 * first selection and memoized for the request. Blocks outside that flat list (e.g. nested
 * {@code Appointment.blocks}) answer empty geometry.
 */
@Controller
public class CalendarGridGraphQLController
{
    /** The flat block page of THIS request ({@code List<AppointmentBlockDto>}), stashed by the resolver. */
    static final String GRID_PAGE_CTX_KEY = "rapla.grid.page";
    /** The page's filter ({@code ReservationFilter}) — the geometry's window + day set. */
    static final String GRID_FILTER_CTX_KEY = "rapla.grid.filter";

    private static final String SEGMENTS_CTX_KEY = "rapla.grid.segments";
    private static final String BARS_ALL_CTX_KEY = "rapla.grid.bars.all";
    private static final String BARS_BANNER_CTX_KEY = "rapla.grid.bars.banner";

    private final ObjectProvider<TimeslotProvider> timeslotProvider;

    public CalendarGridGraphQLController(ObjectProvider<TimeslotProvider> timeslotProvider)
    {
        this.timeslotProvider = timeslotProvider;
    }

    // ============================================================ query root

    @QueryMapping
    public List<CalendarGridLayout.Strip> strips(@Argument("filter") Map<String, Object> filterMap,
            DataFetchingEnvironment env)
    {
        var rc = RequestContextInstrumentation.from(env.getGraphQlContext());
        UnauthenticatedException.require(rc.caller());
        return CalendarGridLayout.strips(
                (LocalDateTime) filterMap.get("from"),
                (LocalDateTime) filterMap.get("to"),
                parseWeekdays(filterMap.get("weekdays")));
    }

    // ============================================================ per-block fields

    @SchemaMapping(typeName = "AppointmentBlock")
    public boolean wholeDay(ReservationGraphQLController.AppointmentBlockDto dto)
    {
        return dto.appointment() != null && dto.appointment().isWholeDaysSet();
    }

    @SchemaMapping(typeName = "AppointmentBlock")
    public boolean banner(ReservationGraphQLController.AppointmentBlockDto dto)
    {
        return CalendarGridLayout.banner(dto.start(), dto.end(), wholeDay(dto));
    }

    @SchemaMapping(typeName = "AppointmentBlock")
    public String timeslot(ReservationGraphQLController.AppointmentBlockDto dto)
    {
        TimeslotProvider provider = timeslotProvider.getIfAvailable();
        if (provider == null) return null;
        return CalendarGridLayout.timeslot(dto.start(), provider.getTimeslots());
    }

    @SchemaMapping(typeName = "AppointmentBlock")
    public List<CalendarGridLayout.Segment> segments(
            ReservationGraphQLController.AppointmentBlockDto dto, DataFetchingEnvironment env)
    {
        Map<ReservationGraphQLController.AppointmentBlockDto, List<CalendarGridLayout.Segment>> byBlock =
                memoized(env.getGraphQlContext(), SEGMENTS_CTX_KEY,
                        (page, strips) -> CalendarGridLayout.segments(spansOf(page), strips));
        if (byBlock == null) return List.of();
        List<CalendarGridLayout.Segment> segments = byBlock.get(dto);
        return segments == null ? List.of() : segments;
    }

    @SchemaMapping(typeName = "AppointmentBlock")
    public List<CalendarGridLayout.Bar> bars(
            ReservationGraphQLController.AppointmentBlockDto dto,
            @Argument("scope") String scope, DataFetchingEnvironment env)
    {
        boolean bannerOnly = "BANNER".equals(scope);
        Map<ReservationGraphQLController.AppointmentBlockDto, List<CalendarGridLayout.Bar>> byBlock =
                memoized(env.getGraphQlContext(), bannerOnly ? BARS_BANNER_CTX_KEY : BARS_ALL_CTX_KEY,
                        (page, strips) -> CalendarGridLayout.bars(spansOf(page), strips, bannerOnly));
        if (byBlock == null) return List.of();
        List<CalendarGridLayout.Bar> bars = byBlock.get(dto);
        return bars == null ? List.of() : bars;
    }

    // ============================================================ lazy list-scoped layout

    private interface LayoutFn<T>
    {
        List<List<T>> compute(List<ReservationGraphQLController.AppointmentBlockDto> page,
                List<CalendarGridLayout.Strip> strips);
    }

    /**
     * The per-request layout of one primitive: computed from the stashed page on first access,
     * keyed back to the dto instances by IDENTITY (the page list holds the same instances GraphQL
     * hands to the field resolvers). Null when no flat block page ran in this request.
     */
    private <T> Map<ReservationGraphQLController.AppointmentBlockDto, List<T>> memoized(
            GraphQLContext context, String key, LayoutFn<T> fn)
    {
        Map<ReservationGraphQLController.AppointmentBlockDto, List<T>> cached = context.get(key);
        if (cached != null) return cached;
        List<ReservationGraphQLController.AppointmentBlockDto> page = context.get(GRID_PAGE_CTX_KEY);
        ReservationGraphQLController.ReservationFilter filter = context.get(GRID_FILTER_CTX_KEY);
        if (page == null || filter == null) return null;
        List<CalendarGridLayout.Strip> strips = CalendarGridLayout.strips(
                filter.from(), filter.to(), parseWeekdays(filter.weekdays()));
        List<List<T>> perBlock = fn.compute(page, strips);
        Map<ReservationGraphQLController.AppointmentBlockDto, List<T>> byBlock = new IdentityHashMap<>();
        for (int i = 0; i < page.size(); i++)
        {
            byBlock.put(page.get(i), perBlock.get(i));
        }
        context.put(key, byBlock);
        return byBlock;
    }

    private List<CalendarGridLayout.BlockSpan> spansOf(
            List<ReservationGraphQLController.AppointmentBlockDto> page)
    {
        List<CalendarGridLayout.BlockSpan> spans = new ArrayList<>(page.size());
        for (ReservationGraphQLController.AppointmentBlockDto dto : page)
        {
            spans.add(new CalendarGridLayout.BlockSpan(dto.start(), dto.end(), banner(dto)));
        }
        return spans;
    }

    /** {@code Weekday} enum names (raw argument map) → {@link DayOfWeek} set; null/empty → null (all days). */
    static Set<DayOfWeek> parseWeekdays(Object raw)
    {
        if (!(raw instanceof List<?> list) || list.isEmpty()) return null;
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (Object entry : list)
        {
            if (entry instanceof DayOfWeek day) days.add(day);
            else if (entry != null) days.add(DayOfWeek.valueOf(entry.toString()));
        }
        return days.isEmpty() ? null : days;
    }
}
