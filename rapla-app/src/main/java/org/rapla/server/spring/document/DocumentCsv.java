package org.rapla.server.spring.document;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.rapla.plugin.tableview.CsvSerializer;

/**
 * PRD 097 — project a rendered document's model to CSV.
 *
 * <p>The Mustache template is free-form HTML, so a spreadsheet export cannot be derived from it.
 * It is projected from the same source the template renders: the view's own {@code @column}
 * metadata (header, order, hidden) over the same row list — including the grouping/HAVING the page
 * applies, so the CSV of a duplicate report carries exactly the rows the page shows.
 *
 * <p>Escaping is not reimplemented here — {@link CsvSerializer} owns it for every rapla CSV.
 */
final class DocumentCsv
{
    private DocumentCsv() {}

    /**
     * {@code viewMeta} is {@code extensions.view} of the executed view; {@code model} is the render
     * model (GraphQL data plus, when the view groups, the {@code groups} sections). A view without
     * column metadata yields an empty body — guessing columns from the data would export whatever
     * the query happened to select, in map order.
     */
    @SuppressWarnings("unchecked")
    static String toCsv(Map<String, Object> viewMeta, Map<String, Object> model)
    {
        List<Map<String, Object>> columns = viewMeta == null ? null
                : (List<Map<String, Object>>) viewMeta.get("columns");
        if (columns == null || columns.isEmpty()) return "";

        List<String> headers = new ArrayList<>();
        List<Map<String, Object>> exported = new ArrayList<>();
        for (Map<String, Object> column : columns)
        {
            if (Boolean.TRUE.equals(column.get("hidden"))) continue;
            Object alias = column.get("alias");
            if (alias == null) continue;
            exported.add(column);
            Object header = column.get("header");
            headers.add(header == null ? alias.toString() : header.toString());
        }
        if (exported.isEmpty()) return "";

        List<List<String>> rows = new ArrayList<>();
        for (Map<String, Object> row : exportedRows(model))
        {
            List<String> cells = new ArrayList<>(exported.size());
            for (Map<String, Object> column : exported)
            {
                cells.add(cell(row.get(column.get("alias").toString()), column.get("flatten")));
            }
            rows.add(cells);
        }
        return CsvSerializer.serialize(headers, rows);
    }

    /** The grouped rows when the view groups (that is what the page shows), else the root row list. */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> exportedRows(Map<String, Object> model)
    {
        if (model.get(DocumentRenderService.GROUPS_KEY) instanceof List<?> groups)
        {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Object group : groups)
            {
                if (group instanceof RowGrouping.Group g) rows.addAll(g.rows());
            }
            return rows;
        }
        List<Map<String, Object>> rows = DocumentRenderService.rootRowList(model);
        return rows == null ? List.of() : rows;
    }

    /**
     * Scalars stringify. A nested object is a cell only where the view said so with
     * {@code @flatten} — the leaf name, or {@code true} for a single-field selection. Anything else
     * nested has no single-cell reading and stays empty.
     */
    private static String cell(Object value, Object flatten)
    {
        if (value instanceof Map<?, ?> nested && flatten != null)
        {
            Object leaf = flatten instanceof String name ? nested.get(name)
                    : nested.size() == 1 ? nested.values().iterator().next() : null;
            return cell(leaf, null);
        }
        if (value == null || value instanceof Map || value instanceof Collection) return "";
        return value.toString();
    }
}
