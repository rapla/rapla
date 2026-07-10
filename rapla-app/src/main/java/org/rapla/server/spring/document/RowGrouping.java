package org.rapla.server.spring.document;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PRD 097 Phase 3 / OQ3 — turn the flat GraphQL result rows into nested sections so a logic-less
 * template can paint them ({@code {{#groups}}<h2>{{label}}</h2>{{#rows}}…{{/rows}}{{/groups}}}).
 *
 * <p>No new GraphQL field: the view already declares the grouping column via
 * {@code @column(group: true)}, and {@code ViewMetaInstrumentation} already emits
 * {@code extensions.view.groupBy} (the column's alias) and {@code groupFormat}. This is the
 * server-side twin of the SPA's {@code groupByColumn} + {@code formatGroupLabel}; the two must
 * agree or a server-rendered document drifts from the same view shown in the SPA.
 */
final class RowGrouping
{
    /** One section: rows sharing one value of the grouping column. */
    record Group(String key, String label, List<Map<String, Object>> rows) { }

    private static final String EMPTY_LABEL = "—";
    private static final Pattern DATE_PREFIX = Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})");

    private static final String[] WEEKDAY_SHORT = { "Mo", "Di", "Mi", "Do", "Fr", "Sa", "So" };
    private static final String[] WEEKDAY_FULL =
            { "Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag" };
    private static final String[] MONTH_SHORT =
            { "Jan", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez" };
    private static final String[] MONTH_FULL = { "Januar", "Februar", "März", "April", "Mai", "Juni",
            "Juli", "August", "September", "Oktober", "November", "Dezember" };

    private RowGrouping() {}

    /**
     * Bucket rows by the string value of {@code alias}, preserving first-seen group order and
     * input order within each group. Missing/empty values collect into a trailing group.
     * {@code format} may be null — then the label is the raw value.
     */
    static List<Group> groupByColumn(List<Map<String, Object>> rows, String alias, String format)
    {
        Map<String, List<Map<String, Object>>> buckets = new LinkedHashMap<>();
        for (Map<String, Object> row : rows)
        {
            Object raw = row.get(alias);
            String key = raw == null || raw.toString().isEmpty() ? "" : raw.toString();
            buckets.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }
        List<Group> result = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : buckets.entrySet())
        {
            if (entry.getKey().isEmpty()) continue;   // the empty bucket goes last
            result.add(new Group(entry.getKey(), label(entry.getKey(), format), entry.getValue()));
        }
        List<Map<String, Object>> empty = buckets.get("");
        if (empty != null) result.add(new Group("", EMPTY_LABEL, empty));
        return result;
    }

    private static String label(String key, String format)
    {
        return format == null || format.isBlank() ? key : formatGroupLabel(key, format);
    }

    /**
     * Interpret the server's opaque {@code groupFormat} token (e.g. {@code "EE dd.MM"}).
     * Only the date part is read, so the server's default zone can never shift a row into the
     * previous day. A value that is not a {@code YYYY-MM-DD} date passes through verbatim —
     * non-date group columns are already display-ready.
     */
    static String formatGroupLabel(String value, String pattern)
    {
        LocalDate date = parseDate(value);
        if (date == null) return value;

        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < pattern.length())
        {
            char c = pattern.charAt(i);
            if (c == 'E' || c == 'd' || c == 'M' || c == 'y')
            {
                int j = i;
                while (j < pattern.length() && pattern.charAt(j) == c) j++;
                out.append(token(c, j - i, date));
                i = j;
            }
            else
            {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static String token(char letter, int length, LocalDate d)
    {
        int dayOfWeek = d.getDayOfWeek().getValue();   // 1 = Monday … 7 = Sunday
        return switch (letter)
        {
            case 'E' -> length >= 4 ? WEEKDAY_FULL[dayOfWeek - 1] : WEEKDAY_SHORT[dayOfWeek - 1];
            case 'd' -> length >= 2 ? pad2(d.getDayOfMonth()) : String.valueOf(d.getDayOfMonth());
            case 'M' -> length >= 4 ? MONTH_FULL[d.getMonthValue() - 1]
                    : length == 3 ? MONTH_SHORT[d.getMonthValue() - 1]
                    : length == 2 ? pad2(d.getMonthValue()) : String.valueOf(d.getMonthValue());
            case 'y' -> length == 2 ? pad2(d.getYear() % 100) : String.valueOf(d.getYear());
            default -> String.valueOf(letter).repeat(length);
        };
    }

    private static String pad2(int n)
    {
        return n < 10 ? "0" + n : String.valueOf(n);
    }

    private static LocalDate parseDate(String value)
    {
        if (value == null) return null;
        Matcher m = DATE_PREFIX.matcher(value);
        if (!m.find()) return null;
        try
        {
            return LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
        }
        catch (DateTimeException | NumberFormatException e)
        {
            return null;   // e.g. 2026-02-31 — not a date, so not formatted
        }
    }
}
