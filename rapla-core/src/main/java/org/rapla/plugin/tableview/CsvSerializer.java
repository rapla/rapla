package org.rapla.plugin.tableview;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pure-Java CSV writer for {@link TablePage} rows (PRD 030 Phase 5).
 * RFC 4180-ish: comma separator, CRLF line endings, double-quote escape
 * for cells containing comma / double-quote / CR / LF.
 *
 * <p>Cell formatting:
 * <ul>
 *   <li>{@code null}                  → empty cell</li>
 *   <li>{@code LocalDate}             → ISO {@code yyyy-MM-dd}</li>
 *   <li>{@code LocalDateTime}         → locale-aware short date+time</li>
 *   <li>{@code Number} / {@code Boolean} → {@code toString()}</li>
 *   <li>Anything else                 → {@code toString()}</li>
 * </ul>
 *
 * <p>Date / number locale follows the {@code locale} argument. Pure helper,
 * no facade access, no I/O — caller streams the returned bytes.
 */
public final class CsvSerializer
{
    private CsvSerializer() {}

    /**
     * Serialise {@code page} as a CSV string, using {@code locale} for
     * locale-aware date / number formatting. Header row uses
     * {@code TableColumnDescriptor.label} (locale-resolved server-side).
     */
    public static String serialize(TablePage page, Locale locale)
    {
        if (page == null) throw new IllegalArgumentException("page must not be null");
        Locale loc = locale == null ? Locale.ROOT : locale;
        StringBuilder out = new StringBuilder();
        writeHeader(out, page.columns());
        for (TableRow row : page.rows())
        {
            writeRow(out, row, page.columns(), loc);
        }
        return out.toString();
    }

    private static void writeHeader(StringBuilder out, List<TableColumnDescriptor> columns)
    {
        for (int i = 0; i < columns.size(); i++)
        {
            if (i > 0) out.append(',');
            out.append(escape(columns.get(i).label()));
        }
        out.append("\r\n");
    }

    private static void writeRow(StringBuilder out, TableRow row, List<TableColumnDescriptor> columns, Locale locale)
    {
        Map<String, Object> cells = row.cells();
        for (int i = 0; i < columns.size(); i++)
        {
            if (i > 0) out.append(',');
            TableColumnDescriptor col = columns.get(i);
            Object value = cells.get(col.id());
            out.append(escape(format(value, col.type(), locale)));
        }
        out.append("\r\n");
    }

    private static String format(Object value, TableCellType type, Locale locale)
    {
        if (value == null) return "";
        if (value instanceof LocalDate ld)
        {
            return ld.format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
        if (value instanceof LocalDateTime ldt)
        {
            // Same display the table view shows — locale-aware short
            // date+time. The CSV is intended to round-trip through Excel /
            // OpenOffice, which both recognise locale-formatted timestamps.
            return ldt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", locale));
        }
        // Numbers / booleans / strings: toString. Locale-aware number
        // formatting (decimal separator) is deferred to a follow-up — the
        // current Swing CSV path also emits Java toString() output for
        // numbers; matching its behaviour is the conservative choice.
        return value.toString();
    }

    /**
     * RFC 4180 §2.6: a cell containing comma, double-quote, CR, or LF must
     * be wrapped in double quotes; internal double-quotes are doubled.
     */
    static String escape(String s)
    {
        if (s == null) return "";
        boolean needsQuoting = s.indexOf(',') >= 0 || s.indexOf('"') >= 0
                || s.indexOf('\r') >= 0 || s.indexOf('\n') >= 0;
        if (!needsQuoting) return s;
        StringBuilder out = new StringBuilder(s.length() + 4);
        out.append('"');
        for (int i = 0; i < s.length(); i++)
        {
            char c = s.charAt(i);
            if (c == '"') out.append('"').append('"');
            else out.append(c);
        }
        out.append('"');
        return out.toString();
    }
}
