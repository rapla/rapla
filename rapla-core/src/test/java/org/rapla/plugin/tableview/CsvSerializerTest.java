package org.rapla.plugin.tableview;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 contract pin for {@link CsvSerializer} (PRD 030 Phase 5).
 */
class CsvSerializerTest
{
    private static TableColumnDescriptor col(String id, String label, TableCellType type)
    {
        return new TableColumnDescriptor(id, label, type);
    }

    private static TableRow row(String id, Object... pairs)
    {
        Map<String, Object> cells = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2)
        {
            cells.put((String) pairs[i], pairs[i + 1]);
        }
        return new TableRow(id, cells);
    }

    @Test
    void emptyPageProducesHeaderOnly()
    {
        TablePage page = new TablePage(
                List.of(col("name", "Name", TableCellType.STRING)),
                List.of(), 0, null, false);
        String csv = CsvSerializer.serialize(page, Locale.ROOT);
        assertEquals("Name\r\n", csv);
    }

    @Test
    void singleRowAllScalarsRendered()
    {
        TablePage page = new TablePage(
                List.of(
                        col("name", "Name",  TableCellType.STRING),
                        col("age",  "Age",   TableCellType.INTEGER)),
                List.of(row("r1", "name", "Alice", "age", 30)),
                1, null, false);
        String csv = CsvSerializer.serialize(page, Locale.ROOT);
        assertEquals("Name,Age\r\nAlice,30\r\n", csv);
    }

    @Test
    void nullCellValueIsEmptyField()
    {
        TablePage page = new TablePage(
                List.of(
                        col("name", "Name", TableCellType.STRING),
                        col("age",  "Age",  TableCellType.INTEGER)),
                List.of(row("r1", "name", "Alice", "age", null)),
                1, null, false);
        String csv = CsvSerializer.serialize(page, Locale.ROOT);
        assertEquals("Name,Age\r\nAlice,\r\n", csv);
    }

    @Test
    void localDateFormattedAsIso()
    {
        TablePage page = new TablePage(
                List.of(col("d", "Date", TableCellType.DATE)),
                List.of(row("r1", "d", LocalDate.of(2026, 6, 15))),
                1, null, false);
        String csv = CsvSerializer.serialize(page, Locale.ROOT);
        assertEquals("Date\r\n2026-06-15\r\n", csv);
    }

    @Test
    void localDateTimeFormattedAsLocaleAware()
    {
        TablePage page = new TablePage(
                List.of(col("d", "When", TableCellType.DATE)),
                List.of(row("r1", "d", LocalDateTime.of(2026, 6, 15, 13, 45))),
                1, null, false);
        String csv = CsvSerializer.serialize(page, Locale.ROOT);
        assertEquals("When\r\n2026-06-15 13:45\r\n", csv);
    }

    @Test
    void cellWithCommaIsQuoted()
    {
        TablePage page = new TablePage(
                List.of(col("name", "Name", TableCellType.STRING)),
                List.of(row("r1", "name", "Smith, John")),
                1, null, false);
        String csv = CsvSerializer.serialize(page, Locale.ROOT);
        assertEquals("Name\r\n\"Smith, John\"\r\n", csv);
    }

    @Test
    void cellWithDoubleQuoteIsQuotedAndDoubled()
    {
        TablePage page = new TablePage(
                List.of(col("name", "Name", TableCellType.STRING)),
                List.of(row("r1", "name", "She said \"hi\"")),
                1, null, false);
        String csv = CsvSerializer.serialize(page, Locale.ROOT);
        assertEquals("Name\r\n\"She said \"\"hi\"\"\"\r\n", csv);
    }

    @Test
    void cellWithNewlineIsQuoted()
    {
        TablePage page = new TablePage(
                List.of(col("note", "Note", TableCellType.STRING)),
                List.of(row("r1", "note", "line1\nline2")),
                1, null, false);
        String csv = CsvSerializer.serialize(page, Locale.ROOT);
        assertEquals("Note\r\n\"line1\nline2\"\r\n", csv);
    }

    @Test
    void headerLabelWithCommaIsQuoted()
    {
        TablePage page = new TablePage(
                List.of(col("name", "Name, full", TableCellType.STRING)),
                List.of(), 0, null, false);
        String csv = CsvSerializer.serialize(page, Locale.ROOT);
        assertEquals("\"Name, full\"\r\n", csv);
    }

    @Test
    void multipleRowsAndColumnsInOrder()
    {
        TablePage page = new TablePage(
                List.of(
                        col("name", "Name", TableCellType.STRING),
                        col("age",  "Age",  TableCellType.INTEGER)),
                List.of(
                        row("r1", "name", "Alice",   "age", 25),
                        row("r2", "name", "Bob",     "age", 30),
                        row("r3", "name", "Charlie", "age", 35)),
                3, null, false);
        String csv = CsvSerializer.serialize(page, Locale.ROOT);
        assertEquals(
                "Name,Age\r\n" +
                "Alice,25\r\n" +
                "Bob,30\r\n" +
                "Charlie,35\r\n",
                csv);
    }

    @Test
    void escapeHelperDirectChecks()
    {
        assertEquals("plain", CsvSerializer.escape("plain"));
        assertEquals("\"has,comma\"", CsvSerializer.escape("has,comma"));
        assertEquals("\"has\"\"quote\"", CsvSerializer.escape("has\"quote"));
        assertEquals("\"has\nnewline\"", CsvSerializer.escape("has\nnewline"));
        assertEquals("\"has\rcarriage\"", CsvSerializer.escape("has\rcarriage"));
        assertEquals("", CsvSerializer.escape(null));
        assertEquals("", CsvSerializer.escape(""));
    }

    @Test
    void cellOrderFollowsColumnOrderNotMapInsertion()
    {
        // Row's cell map has "age" before "name" — output must still follow
        // column declaration order ("name" first).
        Map<String, Object> cells = new LinkedHashMap<>();
        cells.put("age", 42);
        cells.put("name", "Eve");
        TableRow row = new TableRow("r1", cells);
        TablePage page = new TablePage(
                List.of(
                        col("name", "Name", TableCellType.STRING),
                        col("age",  "Age",  TableCellType.INTEGER)),
                List.of(row), 1, null, false);
        String csv = CsvSerializer.serialize(page, Locale.ROOT);
        assertEquals("Name,Age\r\nEve,42\r\n", csv);
    }

    /**
     * PRD 097 — the document CSV export brings its own rows (a GraphQL view result, already
     * stringified by the render pipeline), but must not bring its own escaping: same RFC-4180
     * quoting and CRLF endings as the table export, one implementation.
     */
    @Test
    void plainRowsShareTheEscapingOfTheTableExport()
    {
        String csv = CsvSerializer.serialize(
                List.of("Wert", "Name"),
                List.of(List.of("a@x", "Simpson, Homer"), List.of("a@x", "\"Monty\"")));

        assertEquals("Wert,Name\r\na@x,\"Simpson, Homer\"\r\na@x,\"\"\"Monty\"\"\"\r\n", csv);
    }

    @Test
    void plainRowsShorterThanTheHeaderPadWithEmptyCells()
    {
        assertEquals("A,B\r\nx,\r\n", CsvSerializer.serialize(List.of("A", "B"), List.of(List.of("x"))));
    }
}
