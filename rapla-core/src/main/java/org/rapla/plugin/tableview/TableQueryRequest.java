package org.rapla.plugin.tableview;

import org.rapla.entities.RaplaObject;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.ClassificationFilterRule;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.facade.CalendarModel;
import org.rapla.framework.RaplaException;

import java.util.ArrayList;
import java.util.List;

/**
 * Wire-format request body for {@link TableViewService#reservations} and
 * {@link TableViewService#appointments}. POST body rather than GET query
 * params because the {@link ReservationFilter} shape mirrors rapla's
 * in-process {@code ClassificationFilter[]} — too nested for query strings.
 *
 * <p>Selection semantics (all three lists empty → "all readable allocatables"):
 * <ul>
 *   <li>{@code allocatables} — specific allocatable ids selected in the tree</li>
 *   <li>{@code types}        — resource {@code DynamicType} ids whose allocatables to include</li>
 *   <li>{@code owners}       — user ids whose reservations to anchor on</li>
 * </ul>
 *
 * <p>Reservation filter:
 * <ul>
 *   <li>{@code reservationFilter} empty/null — "all reservation types"
 *       (matches the calendar model's default).</li>
 *   <li>Non-empty — narrows to reservations of those types matching the
 *       per-rule conditions. Mirrors what Swing's
 *       {@code model.getReservationFilter()} already produces; the SPA
 *       sends the same shape when its filter UI lands.</li>
 * </ul>
 *
 * <p>All fields are nullable; {@code from} and {@code to} are required.
 */
public record TableQueryRequest(
        String from,
        String to,
        List<String> allocatables,
        List<String> types,
        List<String> owners,
        List<ReservationFilter> reservationFilter,
        List<String> columns,
        List<String> sort,
        String tableName)
{
    /**
     * Backwards-compat constructor without {@code tableName}. Server then
     * uses the endpoint's default view config (events for /reservations,
     * appointments for /appointments). Callers that need a different
     * variant (e.g. {@code "appointments_per_day"}) use the full
     * constructor.
     */
    public TableQueryRequest(String from, String to,
                             List<String> allocatables, List<String> types,
                             List<String> owners,
                             List<ReservationFilter> reservationFilter,
                             List<String> columns, List<String> sort)
    {
        this(from, to, allocatables, types, owners, reservationFilter,
                columns, sort, null);
    }

    /**
     * One classification-filter entry, mirroring
     * {@code org.rapla.entities.dynamictype.ClassificationFilter}: a
     * reservation type plus a list of attribute rules. All rules are
     * AND-ed; within a rule, the conditions are OR-ed (same semantic
     * as the in-process filter).
     */
    public record ReservationFilter(
            String typeId,
            List<Rule> rules)
    {}

    /** One per-attribute rule. {@code conditions} OR-ed together. */
    public record Rule(
            String attributeKey,
            List<Condition> conditions)
    {}

    /**
     * One {@code (operator, value)} condition. Values are deserialized as
     * {@code Object} — String / Integer / Boolean / etc. depending on the
     * JSON node type. The in-process matcher resolves their semantic
     * meaning against the attribute's type.
     */
    public record Condition(
            String operator,
            Object value)
    {}

    /**
     * Build a wire-format request from a Swing {@link CalendarModel}'s
     * current selection + reservation filter. Partitions
     * {@link CalendarModel#getSelectedObjects()} into the three id lists
     * and converts the in-process {@link ClassificationFilter}[] into
     * nested DTOs. Empty lists become {@code null} so the wire payload
     * stays minimal.
     *
     * <p>Skipped on purpose: {@code Conflict}, {@code Reservation}, and
     * the {@code ALLOCATABLES_ROOT} marker (server's "no selection"
     * default already covers the all-readable case).
     */
    public static TableQueryRequest fromCalendarModel(CalendarModel model,
                                                       String fromIso, String toIso,
                                                       List<String> columnIds,
                                                       List<String> sortSpecs) throws RaplaException
    {
        return fromCalendarModel(model, fromIso, toIso, columnIds, sortSpecs, null);
    }

    /** Variant that pins the server-side view config (e.g.
     *  {@code "appointments_per_day"}). Needed when the view's column set
     *  differs from the endpoint's default — without it the server
     *  resolves columns against {@code appointments} and silently drops
     *  the per-day-only ids ({@code times}, {@code appointment_per_date_date}).
     */
    public static TableQueryRequest fromCalendarModel(CalendarModel model,
                                                       String fromIso, String toIso,
                                                       List<String> columnIds,
                                                       List<String> sortSpecs,
                                                       String tableName) throws RaplaException
    {
        List<String> allocs = new ArrayList<>();
        List<String> types  = new ArrayList<>();
        List<String> owners = new ArrayList<>();
        for (RaplaObject obj : model.getSelectedObjects())
        {
            if      (obj instanceof Allocatable a) allocs.add(a.getId());
            else if (obj instanceof DynamicType t) types.add(t.getId());
            else if (obj instanceof User u)        owners.add(u.getId());
        }

        List<ReservationFilter> resFilter = null;
        if (!model.isDefaultEventTypes())
        {
            ClassificationFilter[] rf = model.getReservationFilter();
            if (rf != null && rf.length > 0)
            {
                resFilter = new ArrayList<>(rf.length);
                for (ClassificationFilter f : rf)
                {
                    resFilter.add(toReservationFilterDto(f));
                }
            }
        }

        return new TableQueryRequest(
                fromIso, toIso,
                allocs.isEmpty() ? null : allocs,
                types.isEmpty()  ? null : types,
                owners.isEmpty() ? null : owners,
                resFilter,
                columnIds,
                sortSpecs,
                tableName);
    }

    private static ReservationFilter toReservationFilterDto(ClassificationFilter f)
    {
        List<Rule> rules = new ArrayList<>();
        // Each filter exposes its rules via ruleIterator (one rule per attribute
        // it constrains; conditions inside a rule are OR-ed).
        java.util.Iterator<? extends ClassificationFilterRule> it = f.ruleIterator();
        while (it.hasNext())
        {
            ClassificationFilterRule r = it.next();
            List<Condition> conds = new ArrayList<>();
            String[] ops = r.getOperators();
            Object[] vals = r.getValues();
            int n = ops == null ? 0 : ops.length;
            for (int i = 0; i < n; i++)
            {
                conds.add(new Condition(ops[i], vals[i]));
            }
            rules.add(new Rule(r.getAttribute().getKey(), conds));
        }
        return new ReservationFilter(f.getType().getId(), rules);
    }
}
