package org.rapla.plugin.tableview;

/**
 * Wire-shipped description of a table column: stable id, display label,
 * and value type. Carried by {@link TablePage#columns()}.
 *
 * @param id      stable identifier (plugin column key) — referenced by
 *                {@link TableRow#cells()} entries and by {@link SortSpec}
 * @param label   locale-resolved display label for the column header
 * @param type    cell-value type for Angular rendering / sorting
 */
public record TableColumnDescriptor(String id, String label, TableCellType type)
{
    public TableColumnDescriptor
    {
        if (id == null) throw new IllegalArgumentException("id must not be null");
        if (label == null) throw new IllegalArgumentException("label must not be null");
        if (type == null) throw new IllegalArgumentException("type must not be null");
    }
}
