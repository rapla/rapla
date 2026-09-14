package org.rapla.plugin.tableview;

/**
 * Value-oriented column SPI for {@link TableViewEngine} (PRD 030 Phase 1).
 *
 * <p>Returns a scalar value ({@code String} / {@code Integer} / {@code Long} /
 * {@code Double} / {@code java.time.LocalDate} / {@code java.time.LocalDateTime} /
 * {@code Boolean}) — <b>no Swing types, no entity references, no HTML strings.</b>
 * The engine collects extractor outputs into {@link TableRow#cells()} and the
 * Angular client renders them according to the column's {@link TableCellType}.
 *
 * <p>This is the parallel SPI alongside the Swing-flavoured
 * {@link RaplaTableColumn} (which returns {@code Object} for the JTable
 * model and {@code String} for HTML). Plugin authors can register both;
 * a future cleanup may have {@code RaplaTableColumn} delegate to a
 * {@code CellExtractor} + a per-column HTML formatter to eliminate the
 * duplication entirely.
 *
 * @param <T> the row type (typically {@code Reservation} or {@code AppointmentBlock})
 */
@FunctionalInterface
public interface CellExtractor<T>
{
    /**
     * Extract the scalar cell value for {@code row}. May return {@code null}
     * to indicate "no value for this column on this row" — Angular renders
     * an empty cell.
     */
    Object extract(T row);
}
