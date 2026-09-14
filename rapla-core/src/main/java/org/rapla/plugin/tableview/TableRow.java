package org.rapla.plugin.tableview;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One wire-shipped row in a {@link TablePage}.
 *
 * @param id    stable id of the underlying entity (used for cursor pagination
 *              and for client-side "click row → open entity" flows)
 * @param cells column-id → scalar value. {@code null} values are kept in the
 *              map so the client can distinguish "value absent for this row"
 *              from "column missing entirely". The map is unmodifiable.
 */
public record TableRow(String id, Map<String, Object> cells)
{
    public TableRow
    {
        if (id == null) throw new IllegalArgumentException("id must not be null");
        if (cells == null) cells = Map.of();
        else cells = Collections.unmodifiableMap(new LinkedHashMap<>(cells));
    }
}
