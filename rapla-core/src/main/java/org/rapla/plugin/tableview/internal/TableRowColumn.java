package org.rapla.plugin.tableview.internal;

import org.rapla.components.util.ParseDateException;
import org.rapla.components.util.SerializableDateTimeFormat;
import org.rapla.plugin.tableview.RaplaTableColumn;
import org.rapla.plugin.tableview.TableColumnType;
import org.rapla.plugin.tableview.TableRow;

import java.time.LocalDateTime;

/**
 * {@link RaplaTableColumn} flavour that reads cell values from a
 * {@link TableRow}'s cells map (keyed by column id), delegating column
 * metadata (label, type, class) to a wrapped reference column.
 *
 * <p>Used by the Swing reservation-table view to render server-projected
 * rows from {@code /api/table/*} without re-projecting from entity
 * references.
 *
 * <p>DATE-typed cells come over the wire as ISO strings (Jackson's default
 * serialization of {@link LocalDateTime}) — but the JTable cell renderer
 * picks based on {@link #getColumnClass()}, which is delegated to the
 * wrapped metadata and reports {@code LocalDateTime.class}. To bridge,
 * {@link #getValue} parses DATE-typed strings back to {@code LocalDateTime}
 * so the renderer sees what it expects (locale-formatted, not raw ISO).
 */
public class TableRowColumn implements RaplaTableColumn<TableRow>
{
    private final RaplaTableColumn<?> metadata;

    public TableRowColumn(RaplaTableColumn<?> metadata)
    {
        this.metadata = metadata;
    }

    /** The wrapped reference column — exposed so a Swing-side caller can
     *  apply per-column setup (cell renderer, preferred width) defined on
     *  the metadata column itself. The wrapper alone can't carry that
     *  because the relevant classes (e.g. {@code DateCellRenderer}) live
     *  in {@code rapla-client}. */
    public RaplaTableColumn<?> getMetadata() { return metadata; }

    @Override public String getKey() { return metadata.getKey(); }

    @Override public String getColumnName() { return metadata.getColumnName(); }

    @Override public Class<?> getColumnClass() { return metadata.getColumnClass(); }

    @Override public TableColumnType getType() { return metadata.getType(); }

    @Override public Object getValue(TableRow row, String contextAnnotationName)
    {
        if (row == null) return null;
        Object raw = row.cells().get(metadata.getKey());
        if (raw == null) return null;
        if (metadata.getType() == TableColumnType.DATE && raw instanceof String s)
        {
            try { return SerializableDateTimeFormat.INSTANCE.parseTimestamp(s); }
            catch (ParseDateException ignore) { /* try date-only below */ }
            try { return SerializableDateTimeFormat.INSTANCE.parseDate(s, false); }
            catch (ParseDateException ignore) { return raw; }
        }
        return raw;
    }

    @Override public String getHtmlValue(TableRow row)
    {
        Object value = getValue(row, null);
        return value == null ? "" : String.valueOf(value);
    }
}
