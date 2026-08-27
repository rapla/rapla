package org.rapla.server.spring.document;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * PRD 097 — the CSV twin of the rendered page: same view, same columns, same rows, no template.
 * The template is free-form HTML, so the CSV cannot be derived from it; it is projected from the
 * view's own {@code @column} metadata, which is what makes the two views of the data agree.
 */
class DocumentCsvTest
{
    private static Map<String, Object> col(String alias, String header, Object... extra)
    {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("alias", alias);
        c.put("header", header);
        for (int i = 0; i < extra.length; i += 2) c.put((String) extra[i], extra[i + 1]);
        return c;
    }

    private static Map<String, Object> meta(Map<String, Object>... columns)
    {
        return Map.of("columns", List.of(columns));
    }

    @Test
    void columnsBecomeTheHeaderAndTheRowsFollowTheirOrder()
    {
        Map<String, Object> model = Map.of("allocatables", List.of(
                Map.of("name", "Homer", "mail", "h@x"),
                Map.of("name", "Monty", "mail", "m@x")));

        assertEquals("Wert,Name\r\nh@x,Homer\r\nm@x,Monty\r\n",
                DocumentCsv.toCsv(meta(col("mail", "Wert"), col("name", "Name")), model));
    }

    @Test
    void aHiddenColumnIsNotExported()
    {
        Map<String, Object> model = Map.of("rows", List.of(Map.of("name", "Homer", "secret", "s")));

        assertEquals("Name\r\nHomer\r\n",
                DocumentCsv.toCsv(meta(col("name", "Name"), col("secret", "Geheim", "hidden", true)), model));
    }

    /**
     * When the view groups ({@code @column(group: true)}), the page shows the surviving groups —
     * so the CSV must export exactly those rows. Exporting the raw result instead would hand a
     * duplicate report every singleton it deliberately dropped.
     */
    @Test
    void groupedViewsExportTheGroupedRowsNotTheRawResult()
    {
        List<Map<String, Object>> rows = List.of(
                Map.of("mail", "a@x", "name", "A1"),
                Map.of("mail", "a@x", "name", "A2"),
                Map.of("mail", "b@x", "name", "B1"));
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("allocatables", rows);
        model.put("groups", RowGrouping.groupByColumn(rows, "mail", null, null, 2));

        assertEquals("Wert,Name\r\na@x,A1\r\na@x,A2\r\n",
                DocumentCsv.toCsv(meta(col("mail", "Wert"), col("name", "Name")), model));
    }

    @Test
    void missingAndNonScalarCellsAreEmptyNotToStringNoise()
    {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", "Homer");
        row.put("nested", Map.of("typeKey", "Person"));
        Map<String, Object> model = Map.of("rows", List.of(row));

        assertEquals("Name,Nested,Fehlt\r\nHomer,,\r\n",
                DocumentCsv.toCsv(meta(col("name", "Name"), col("nested", "Nested"), col("gone", "Fehlt")), model));
    }

    /**
     * {@code @flatten(field:)} declares which leaf of a nested selection IS the column — the render
     * hint the SPA table uses. Data stays nested (field directives never alter {@code data}), so the
     * CSV has to read the hint; otherwise a declared column silently exports an empty cell.
     */
    @Test
    void aFlattenedColumnExportsItsDeclaredLeaf()
    {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("typ", Map.of("typeKey", "Person"));
        row.put("einzel", Map.of("nurEins", "x"));
        Map<String, Object> model = Map.of("rows", List.of(row));

        assertEquals("Typ,Einzel\r\nPerson,x\r\n", DocumentCsv.toCsv(
                meta(col("typ", "Typ", "flatten", "typeKey"), col("einzel", "Einzel", "flatten", true)), model));
    }

    @Test
    void aViewWithoutColumnMetadataExportsNothingRatherThanGuessing()
    {
        assertEquals("", DocumentCsv.toCsv(Map.of(), Map.of("rows", List.of(Map.of("name", "Homer")))));
    }
}
