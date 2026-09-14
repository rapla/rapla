package org.rapla.plugin.tableview;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Pure-Java table-projection engine (PRD 030 Phase 1). Given a row collection,
 * a column set, a sort spec and a pagination spec, returns a {@link TablePage}
 * with projected, sorted, paginated wire records.
 *
 * <p>The engine is <b>entity-agnostic</b> — it operates on any
 * {@code Collection<T>} given a {@link CellExtractor} per column and a
 * way to extract a stable row id. This is the load-bearing property:
 * tier-1 tests can pin every contract surface (sort, paging, projection,
 * total count, incomplete flag) without facade entities; production
 * callers wire in {@code Reservation} / {@code AppointmentBlock} extractors
 * via the plugin SPI.
 *
 * <p>The wire output is JSON-friendly: column descriptors carry their
 * {@link TableCellType}; rows carry scalar values keyed by column id.
 * Angular's client uses the type descriptor to pick a renderer.
 *
 * <p>See {@code TableViewEngineTest} for the contract surface.
 */
public final class TableViewEngine
{
    private TableViewEngine() {}

    /**
     * Project {@code rows} through {@code columns} with the given
     * {@code sort} + {@code page} spec. Returns the wire-shipped page.
     *
     * @param rows         input data; never {@code null}
     * @param columns      column set with descriptors + extractors; never {@code null}
     * @param sort         multi-column sort spec; {@code null} means {@link SortSpec#NONE}
     * @param page         pagination spec; {@code null} means {@link PageSpec#ALL}
     * @param idExtractor  stable id extractor for cursor pagination; never {@code null}
     */
    public static <T> TablePage project(Collection<T> rows,
                                        List<EngineColumn<T>> columns,
                                        SortSpec sort,
                                        PageSpec page,
                                        Function<T, String> idExtractor)
    {
        if (rows == null) throw new IllegalArgumentException("rows must not be null");
        if (columns == null) throw new IllegalArgumentException("columns must not be null");
        if (idExtractor == null) throw new IllegalArgumentException("idExtractor must not be null");
        if (sort == null) sort = SortSpec.NONE;
        if (page == null) page = PageSpec.ALL;

        // 1. Index columns by id for extractor lookup during projection.
        Map<String, EngineColumn<T>> columnsById = new LinkedHashMap<>();
        List<TableColumnDescriptor> descriptors = new ArrayList<>(columns.size());
        for (EngineColumn<T> col : columns)
        {
            columnsById.put(col.descriptor().id(), col);
            descriptors.add(col.descriptor());
        }

        // 2. Materialise + (stable-)sort.
        List<T> sortedRows = new ArrayList<>(rows);
        applySort(sortedRows, sort, columnsById);

        int totalCount = sortedRows.size();

        // 3. Apply cursor (skip rows up to and including the cursor id).
        List<T> afterCursor = applyCursor(sortedRows, page.cursor(), idExtractor);

        // 4. Apply pageSize / cap.
        int limit = -1;       // -1 means "no limit"
        boolean enforceCap = false;
        if (page.pageSize() != null)
        {
            limit = page.pageSize();
        }
        else if (page.cap() > 0)
        {
            limit = page.cap();
            enforceCap = true;
        }

        List<T> pageRows;
        boolean incomplete = false;
        String nextCursor = null;
        if (limit < 0 || afterCursor.size() <= limit)
        {
            pageRows = afterCursor;
        }
        else
        {
            pageRows = afterCursor.subList(0, limit);
            // The cursor is the id of the LAST row in this page — next request
            // starts at the next row after it.
            nextCursor = idExtractor.apply(pageRows.get(pageRows.size() - 1));
            incomplete = enforceCap;
        }

        // 5. Project each row through the column extractors.
        List<TableRow> projectedRows = new ArrayList<>(pageRows.size());
        for (T raw : pageRows)
        {
            String id = idExtractor.apply(raw);
            Map<String, Object> cells = new LinkedHashMap<>(columns.size());
            for (EngineColumn<T> col : columns)
            {
                cells.put(col.descriptor().id(), col.extractor().extract(raw));
            }
            projectedRows.add(new TableRow(id, cells));
        }

        return new TablePage(descriptors, projectedRows, totalCount, nextCursor, incomplete);
    }

    // ---------- sort ----------

    private static <T> void applySort(List<T> rows,
                                      SortSpec sort,
                                      Map<String, EngineColumn<T>> columnsById)
    {
        if (sort.fields().isEmpty()) return;
        // Build the multi-column comparator. Unknown column ids are dropped.
        Comparator<T> comparator = null;
        for (SortSpec.SortField f : sort.fields())
        {
            EngineColumn<T> col = columnsById.get(f.columnId());
            if (col == null) continue;
            CellExtractor<T> ex = col.extractor();
            TableCellType type = col.descriptor().type();
            Comparator<T> step = (a, b) -> compareValues(ex.extract(a), ex.extract(b), type);
            if (f.direction() == SortSpec.Direction.DESC) step = step.reversed();
            comparator = (comparator == null) ? step : comparator.thenComparing(step);
        }
        if (comparator != null)
        {
            // Collections.sort is stable — ties preserve input order.
            rows.sort(comparator);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static int compareValues(Object a, Object b, TableCellType type)
    {
        if (a == null && b == null) return 0;
        if (a == null) return -1;     // nulls first
        if (b == null) return 1;
        if (type == TableCellType.STRING)
        {
            return String.CASE_INSENSITIVE_ORDER.compare(a.toString(), b.toString());
        }
        if (a instanceof Comparable ac && b.getClass() == a.getClass())
        {
            return ac.compareTo(b);
        }
        // Fallback: case-insensitive toString compare. Unknown / mixed types
        // shouldn't reach here; this keeps sort defined rather than throwing.
        return a.toString().toLowerCase(Locale.ROOT).compareTo(b.toString().toLowerCase(Locale.ROOT));
    }

    // ---------- cursor ----------

    private static <T> List<T> applyCursor(List<T> rows, String cursor, Function<T, String> idExtractor)
    {
        if (cursor == null) return rows;
        int idx = -1;
        for (int i = 0; i < rows.size(); i++)
        {
            if (cursor.equals(idExtractor.apply(rows.get(i))))
            {
                idx = i;
                break;
            }
        }
        if (idx < 0)
        {
            // Cursor refers to a row no longer in the result — return empty.
            // Treating this as "scrolled past the end" is the conventional choice;
            // surfaces eventually-consistent removals cleanly to the client.
            return List.of();
        }
        return new ArrayList<>(rows.subList(idx + 1, rows.size()));
    }
}
