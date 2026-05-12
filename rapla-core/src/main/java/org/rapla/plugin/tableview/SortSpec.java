package org.rapla.plugin.tableview;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Multi-column sort specification for {@link TableViewEngine}. The
 * {@code fields} list defines the primary → secondary → tertiary
 * ordering. Stable sort, so ties fall through to the input order.
 *
 * <p>Unknown column ids (not present in the engine's input columns)
 * are silently dropped — see Risk-3 logic in PRD 030.
 */
public record SortSpec(List<SortField> fields)
{
    public SortSpec
    {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }

    /** No sort — engine preserves input order. */
    public static final SortSpec NONE = new SortSpec(List.of());

    /** Single-column shortcut. */
    public static SortSpec of(String columnId, Direction direction)
    {
        return new SortSpec(List.of(new SortField(columnId, direction)));
    }

    public enum Direction { ASC, DESC }

    public record SortField(String columnId, Direction direction)
    {
        public SortField
        {
            if (columnId == null) throw new IllegalArgumentException("columnId must not be null");
            if (direction == null) throw new IllegalArgumentException("direction must not be null");
        }
    }
}
