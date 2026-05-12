package org.rapla.plugin.tableview.internal;

import org.rapla.plugin.tableview.RaplaTableColumn;
import org.rapla.plugin.tableview.TableColumnType;
import org.rapla.plugin.tableview.TableRow;

/**
 * {@link RaplaTableColumn} flavour that reads cell values from a
 * {@link TableRow}'s cells map (keyed by column id), delegating column
 * metadata (label, type, class) to a wrapped reference column.
 *
 * <p>Used by the Swing reservation-table view (PRD 030 Phase 7) to render
 * server-projected rows from {@code /table/reservations} without
 * re-projecting from entity references.
 */
public class TableRowColumn implements RaplaTableColumn<TableRow>
{
    private final RaplaTableColumn<?> metadata;

    public TableRowColumn(RaplaTableColumn<?> metadata)
    {
        this.metadata = metadata;
    }

    @Override public String getKey() { return metadata.getKey(); }

    @Override public String getColumnName() { return metadata.getColumnName(); }

    @Override public Class<?> getColumnClass() { return metadata.getColumnClass(); }

    @Override public TableColumnType getType() { return metadata.getType(); }

    @Override public Object getValue(TableRow row, String contextAnnotationName)
    {
        return row != null ? row.cells().get(metadata.getKey()) : null;
    }

    @Override public String getHtmlValue(TableRow row)
    {
        Object value = getValue(row, null);
        return value == null ? "" : String.valueOf(value);
    }
}
