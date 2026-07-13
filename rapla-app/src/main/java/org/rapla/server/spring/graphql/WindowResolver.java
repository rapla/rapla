package org.rapla.server.spring.graphql;

import graphql.language.Argument;
import graphql.language.Directive;
import graphql.language.EnumValue;
import graphql.language.IntValue;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.OperationDefinition;
import graphql.language.Value;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * PRD 074 §"Window and inputs directives" — server-side date-window resolution. Evaluates a
 * view's {@code @window(from:{anchor,offset,unit}, to:{...})} directive (or the render-mode
 * default when absent) at request time, so the window is never stale and the SPA no longer
 * resolves anchors client-side. The resolved window fills {@code filter.from/to}
 * ({@link ViewVariables}) and is emitted as {@code extensions.view.window}
 * ({@link ViewMetaInstrumentation}).
 */
public final class WindowResolver
{
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /** A resolved zoneless window, both ends at 00:00:00 (from inclusive, to exclusive). */
    public record Window(String from, String to) {}

    private WindowResolver() {}

    /** Resolve one anchor+offset spec at {@code today} — twin of the old SPA {@code resolveAnchorOffset}. */
    public static String resolve(ViewAnchor anchor, int offset, ViewDateUnit unit, LocalDate today)
    {
        LocalDate base = switch (anchor)
        {
            case TODAY -> today;
            case WEEK_START -> today.with(DayOfWeek.MONDAY);
            case MONTH_START -> today.withDayOfMonth(1);
        };
        LocalDate shifted = switch (unit)
        {
            case DAYS -> base.plusDays(offset);
            case WEEKS -> base.plusWeeks(offset);
            case MONTHS -> base.plusMonths(offset);
        };
        return shifted.atStartOfDay().format(FMT);
    }

    /** The {@code @window} directive off the operation, resolved — or null when not declared. */
    public static Window fromOperation(OperationDefinition op, LocalDate today)
    {
        if (op == null) return null;
        Directive window = op.getDirectives().stream()
                .filter(d -> "window".equals(d.getName())).findFirst().orElse(null);
        if (window == null) return null;
        String from = resolveArg(window, "from", today);
        String to = resolveArg(window, "to", today);
        if (from == null || to == null) return null;
        return new Window(from, to);
    }

    /**
     * The render-mode default when no {@code @window} is declared: {@code week} → current ISO
     * week, {@code month} → current month, else (table/grouped/…) → TODAY−7 … +7.
     */
    public static Window defaultWindow(List<String> renderModes, LocalDate today)
    {
        String first = renderModes.isEmpty() ? "" : renderModes.get(0);
        return switch (first)
        {
            case "week" -> new Window(
                    resolve(ViewAnchor.WEEK_START, 0, ViewDateUnit.DAYS, today),
                    resolve(ViewAnchor.WEEK_START, 7, ViewDateUnit.DAYS, today));
            case "month" -> new Window(
                    resolve(ViewAnchor.MONTH_START, 0, ViewDateUnit.DAYS, today),
                    resolve(ViewAnchor.MONTH_START, 1, ViewDateUnit.MONTHS, today));
            default -> new Window(
                    resolve(ViewAnchor.TODAY, -7, ViewDateUnit.DAYS, today),
                    resolve(ViewAnchor.TODAY, 7, ViewDateUnit.DAYS, today));
        };
    }

    /** Resolve one {@code from:}/{@code to:} object-literal arg — {@code {anchor, offset, unit}}. */
    private static String resolveArg(Directive window, String argName, LocalDate today)
    {
        Argument arg = window.getArguments().stream()
                .filter(a -> argName.equals(a.getName())).findFirst().orElse(null);
        if (arg == null || !(arg.getValue() instanceof ObjectValue spec)) return null;
        ViewAnchor anchor = null;
        int offset = 0;
        ViewDateUnit unit = ViewDateUnit.DAYS;
        for (ObjectField f : spec.getObjectFields())
        {
            Value<?> v = f.getValue();
            switch (f.getName())
            {
                case "anchor" -> { if (v instanceof EnumValue ev) anchor = parse(ViewAnchor.class, ev.getName()); }
                case "offset" -> { if (v instanceof IntValue iv) offset = iv.getValue().intValue(); }
                case "unit" -> { if (v instanceof EnumValue ev) unit = parse(ViewDateUnit.class, ev.getName()); }
            }
        }
        if (anchor == null || unit == null) return null;
        return resolve(anchor, offset, unit, today);
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String name)
    {
        try { return Enum.valueOf(type, name); }
        catch (IllegalArgumentException e) { return null; }
    }
}
