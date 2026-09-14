package org.rapla.plugin.tableview;

/**
 * Logical wire-format type of a table cell value. The descriptor lets
 * the client (Angular) decide how to render and sort each column
 * consistently — date columns get date-pickers, numeric columns get
 * right-alignment, etc. — without server-side pre-formatting.
 *
 * <p>The wire payload always carries scalar cell values
 * ({@code String} / {@code Integer} / {@code Long} / {@code Double} /
 * ISO-8601 date string / {@code Boolean}); the {@code TableCellType}
 * tells the client which scalar to expect.
 *
 * <p>This is the value-oriented counterpart to the Swing-flavoured
 * {@link TableColumnType} (which carries roughly the same information
 * for the Swing JTable column-class lookup). Both can coexist; PRD 030
 * uses {@code TableCellType} for the engine and the wire.
 */
public enum TableCellType
{
    STRING,
    INTEGER,
    LONG,
    DOUBLE,
    DATE,
    BOOLEAN
}
