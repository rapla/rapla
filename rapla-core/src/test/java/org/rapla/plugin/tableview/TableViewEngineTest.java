package org.rapla.plugin.tableview;

import org.junit.jupiter.api.Test;
import org.rapla.plugin.tableview.TableCellType;
import org.rapla.plugin.tableview.TableViewEngine;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 contract pins for {@link TableViewEngine} (PRD 030 Phase 1).
 *
 * <p>The engine is entity-agnostic — it operates on any {@code Collection<T>}
 * given a column descriptor + {@link CellExtractor} per column + a way to
 * extract a stable row id. Tests here use a tiny {@code Person} record so
 * the engine's behaviour is pinned without depending on facade entities;
 * the contract surface (sort, paging, projection, totalCount, incomplete
 * flag) is fully exercised.
 */
class TableViewEngineTest
{
    private record Person(String id, String name, Integer age, LocalDate birthday) {}

    private static final CellExtractor<Person> NAME     = p -> p.name();
    private static final CellExtractor<Person> AGE      = p -> p.age();
    private static final CellExtractor<Person> BIRTHDAY = p -> p.birthday();

    private static EngineColumn<Person> col(String id, String label, TableCellType type, CellExtractor<Person> ex)
    {
        return new EngineColumn<>(new TableColumnDescriptor(id, label, type), ex);
    }

    private static final List<EngineColumn<Person>> COLUMNS = List.of(
            col("name",     "Name",     TableCellType.STRING,  NAME),
            col("age",      "Age",      TableCellType.INTEGER, AGE),
            col("birthday", "Birthday", TableCellType.DATE,    BIRTHDAY));

    private static List<Person> samplePeople()
    {
        return List.of(
                new Person("p1", "Charlie", 30, LocalDate.of(1995, 1, 1)),
                new Person("p2", "Alice",   25, LocalDate.of(2000, 6, 15)),
                new Person("p3", "Bob",     30, LocalDate.of(1995, 5, 20)),
                new Person("p4", "Diana",   28, LocalDate.of(1997, 9, 10)),
                new Person("p5", "Eve",     22, LocalDate.of(2003, 3, 3)));
    }

    // ---------- empty / single / preservation ----------

    @Test
    void emptyRowsYieldEmptyPage()
    {
        TablePage page = TableViewEngine.project(List.of(), COLUMNS, SortSpec.NONE, PageSpec.ALL, Person::id);
        assertEquals(0, page.rows().size());
        assertEquals(0, page.totalCount());
        assertNull(page.nextCursor());
        assertFalse(page.incomplete());
        assertEquals(3, page.columns().size());
        assertEquals("name", page.columns().get(0).id());
    }

    @Test
    void singleRowProjectsAllColumns()
    {
        List<Person> people = List.of(new Person("p1", "Alice", 25, LocalDate.of(2000, 6, 15)));
        TablePage page = TableViewEngine.project(people, COLUMNS, SortSpec.NONE, PageSpec.ALL, Person::id);
        assertEquals(1, page.rows().size());
        assertEquals(1, page.totalCount());
        TableRow row = page.rows().get(0);
        assertEquals("p1", row.id());
        assertEquals("Alice", row.cells().get("name"));
        assertEquals(25, row.cells().get("age"));
        assertEquals(LocalDate.of(2000, 6, 15), row.cells().get("birthday"));
    }

    @Test
    void noSortPreservesInputOrder()
    {
        TablePage page = TableViewEngine.project(samplePeople(), COLUMNS, SortSpec.NONE, PageSpec.ALL, Person::id);
        assertEquals(List.of("p1", "p2", "p3", "p4", "p5"),
                page.rows().stream().map(TableRow::id).toList());
    }

    // ---------- sort ----------

    @Test
    void sortByStringAsc()
    {
        SortSpec sort = SortSpec.of("name", SortSpec.Direction.ASC);
        TablePage page = TableViewEngine.project(samplePeople(), COLUMNS, sort, PageSpec.ALL, Person::id);
        assertEquals(List.of("p2", "p3", "p1", "p4", "p5"),
                page.rows().stream().map(TableRow::id).toList(),
                "Alphabetical: Alice, Bob, Charlie, Diana, Eve");
    }

    @Test
    void sortByStringDesc()
    {
        SortSpec sort = SortSpec.of("name", SortSpec.Direction.DESC);
        TablePage page = TableViewEngine.project(samplePeople(), COLUMNS, sort, PageSpec.ALL, Person::id);
        assertEquals(List.of("p5", "p4", "p1", "p3", "p2"),
                page.rows().stream().map(TableRow::id).toList());
    }

    @Test
    void sortByDate()
    {
        SortSpec sort = SortSpec.of("birthday", SortSpec.Direction.ASC);
        TablePage page = TableViewEngine.project(samplePeople(), COLUMNS, sort, PageSpec.ALL, Person::id);
        assertEquals(List.of("p1", "p3", "p4", "p2", "p5"),
                page.rows().stream().map(TableRow::id).toList(),
                "Birthday ascending: 1995-01 (Charlie), 1995-05 (Bob), 1997 (Diana), 2000 (Alice), 2003 (Eve)");
    }

    @Test
    void multiColumnSortPrimaryThenSecondary()
    {
        // age ASC, then name ASC — Bob and Charlie are both 30
        SortSpec sort = new SortSpec(List.of(
                new SortSpec.SortField("age",  SortSpec.Direction.ASC),
                new SortSpec.SortField("name", SortSpec.Direction.ASC)));
        TablePage page = TableViewEngine.project(samplePeople(), COLUMNS, sort, PageSpec.ALL, Person::id);
        assertEquals(List.of("p5", "p2", "p4", "p3", "p1"),
                page.rows().stream().map(TableRow::id).toList(),
                "Eve 22, Alice 25, Diana 28, then age=30 broken by name: Bob, Charlie");
    }

    @Test
    void stringSortIsCaseInsensitive()
    {
        List<Person> mixed = List.of(
                new Person("p1", "alice", 1, null),
                new Person("p2", "Bob",   2, null),
                new Person("p3", "ALICE", 3, null));
        SortSpec sort = SortSpec.of("name", SortSpec.Direction.ASC);
        TablePage page = TableViewEngine.project(mixed, COLUMNS, sort, PageSpec.ALL, Person::id);
        // Both "alice" and "ALICE" come before "Bob"; their internal order
        // depends on the sort being stable.
        List<String> ids = page.rows().stream().map(TableRow::id).toList();
        assertEquals("p2", ids.get(2), "Bob is last");
        assertTrue(ids.subList(0, 2).contains("p1"));
        assertTrue(ids.subList(0, 2).contains("p3"));
    }

    @Test
    void unknownSortColumnIsIgnored()
    {
        SortSpec sort = SortSpec.of("doesnotexist", SortSpec.Direction.ASC);
        TablePage page = TableViewEngine.project(samplePeople(), COLUMNS, sort, PageSpec.ALL, Person::id);
        // Falls back to input order
        assertEquals(List.of("p1", "p2", "p3", "p4", "p5"),
                page.rows().stream().map(TableRow::id).toList());
    }

    // ---------- pagination ----------

    @Test
    void noPageSizeReturnsAllRows()
    {
        TablePage page = TableViewEngine.project(samplePeople(), COLUMNS, SortSpec.NONE, PageSpec.ALL, Person::id);
        assertEquals(5, page.rows().size());
        assertEquals(5, page.totalCount());
        assertNull(page.nextCursor());
        assertFalse(page.incomplete());
    }

    @Test
    void firstPageReturnsCursorWhenMoreRowsRemain()
    {
        PageSpec p = PageSpec.firstPage(2);
        TablePage page = TableViewEngine.project(samplePeople(), COLUMNS, SortSpec.NONE, p, Person::id);
        assertEquals(2, page.rows().size());
        assertEquals(5, page.totalCount(), "totalCount is the full-result size, not the page size");
        assertNotNull(page.nextCursor(), "cursor must be present when more rows exist");
        assertFalse(page.incomplete());
    }

    @Test
    void cursorAdvancesToNextPage()
    {
        PageSpec p1 = PageSpec.firstPage(2);
        TablePage firstPage = TableViewEngine.project(samplePeople(), COLUMNS, SortSpec.NONE, p1, Person::id);
        assertEquals(List.of("p1", "p2"), firstPage.rows().stream().map(TableRow::id).toList());

        PageSpec p2 = PageSpec.nextPage(2, firstPage.nextCursor());
        TablePage secondPage = TableViewEngine.project(samplePeople(), COLUMNS, SortSpec.NONE, p2, Person::id);
        assertEquals(List.of("p3", "p4"), secondPage.rows().stream().map(TableRow::id).toList());
        assertNotNull(secondPage.nextCursor());

        PageSpec p3 = PageSpec.nextPage(2, secondPage.nextCursor());
        TablePage thirdPage = TableViewEngine.project(samplePeople(), COLUMNS, SortSpec.NONE, p3, Person::id);
        assertEquals(List.of("p5"), thirdPage.rows().stream().map(TableRow::id).toList());
        assertNull(thirdPage.nextCursor(), "last page emits no cursor");
    }

    @Test
    void totalCountReflectsFullResultRegardlessOfPageSize()
    {
        TablePage page = TableViewEngine.project(samplePeople(), COLUMNS, SortSpec.NONE, PageSpec.firstPage(1), Person::id);
        assertEquals(5, page.totalCount());
    }

    @Test
    void pageSizeLargerThanResultReturnsAll()
    {
        TablePage page = TableViewEngine.project(samplePeople(), COLUMNS, SortSpec.NONE, PageSpec.firstPage(100), Person::id);
        assertEquals(5, page.rows().size());
        assertNull(page.nextCursor());
    }

    @Test
    void unknownCursorYieldsEmptyPage()
    {
        PageSpec p = PageSpec.nextPage(2, "unknown-id");
        TablePage page = TableViewEngine.project(samplePeople(), COLUMNS, SortSpec.NONE, p, Person::id);
        // The cursor refers to a row that isn't in the result — we've
        // "scrolled past the end". Empty page, total still reflects the source.
        assertEquals(0, page.rows().size());
        assertEquals(5, page.totalCount());
        assertNull(page.nextCursor());
    }

    // ---------- cap / incomplete ----------

    @Test
    void noPageSizeWithinCapReturnsAll()
    {
        TablePage page = TableViewEngine.project(samplePeople(), COLUMNS,
                SortSpec.NONE, PageSpec.allWithCap(10), Person::id);
        assertEquals(5, page.rows().size());
        assertFalse(page.incomplete());
        assertNull(page.nextCursor());
    }

    @Test
    void noPageSizeOverCapTruncatesAndFlagsIncomplete()
    {
        TablePage page = TableViewEngine.project(samplePeople(), COLUMNS,
                SortSpec.NONE, PageSpec.allWithCap(3), Person::id);
        assertEquals(3, page.rows().size(), "truncated to cap");
        assertEquals(5, page.totalCount(), "totalCount reflects what would have been returned");
        assertTrue(page.incomplete(), "incomplete flag set when cap was hit");
        assertNotNull(page.nextCursor(), "cursor lets the client keep loading");
    }

    @Test
    void noPageSizeExactlyAtCapIsNotIncomplete()
    {
        TablePage page = TableViewEngine.project(samplePeople(), COLUMNS,
                SortSpec.NONE, PageSpec.allWithCap(5), Person::id);
        assertEquals(5, page.rows().size());
        assertFalse(page.incomplete(), "exactly at cap is not 'over'");
        assertNull(page.nextCursor());
    }

    // ---------- defensive ----------

    @Test
    void rejectsNullRows()
    {
        assertThrows(IllegalArgumentException.class, () ->
                TableViewEngine.project(null, COLUMNS, SortSpec.NONE, PageSpec.ALL, Person::id));
    }

    @Test
    void rejectsNullColumns()
    {
        assertThrows(IllegalArgumentException.class, () ->
                TableViewEngine.project(samplePeople(), null, SortSpec.NONE, PageSpec.ALL, Person::id));
    }

    @Test
    void rejectsNullIdExtractor()
    {
        assertThrows(IllegalArgumentException.class, () ->
                TableViewEngine.project(samplePeople(), COLUMNS, SortSpec.NONE, PageSpec.ALL, null));
    }

    @Test
    void nullSortFallsBackToNone()
    {
        TablePage page = TableViewEngine.project(samplePeople(), COLUMNS, null, PageSpec.ALL, Person::id);
        assertEquals(5, page.rows().size());
    }

    @Test
    void nullPageFallsBackToAll()
    {
        TablePage page = TableViewEngine.project(samplePeople(), COLUMNS, SortSpec.NONE, null, Person::id);
        assertEquals(5, page.rows().size());
        assertNull(page.nextCursor());
    }

    @Test
    void cellsReturnedAreImmutable()
    {
        TablePage page = TableViewEngine.project(samplePeople(), COLUMNS, SortSpec.NONE, PageSpec.ALL, Person::id);
        TableRow row = page.rows().get(0);
        assertThrows(UnsupportedOperationException.class, () -> row.cells().put("name", "Modified"));
    }

    @Test
    void columnDescriptorsAreImmutableInResponse()
    {
        TablePage page = TableViewEngine.project(samplePeople(), COLUMNS, SortSpec.NONE, PageSpec.ALL, Person::id);
        assertThrows(UnsupportedOperationException.class, () ->
                page.columns().add(new TableColumnDescriptor("hack", "Hack", TableCellType.STRING)));
    }

    @Test
    void nullCellValuesAreCarriedThrough()
    {
        List<Person> people = List.of(new Person("p1", "Alice", null, null));
        TablePage page = TableViewEngine.project(people, COLUMNS, SortSpec.NONE, PageSpec.ALL, Person::id);
        TableRow row = page.rows().get(0);
        // The cell map contains the null entries so Angular can distinguish
        // "value absent" from "column missing".
        assertTrue(row.cells().containsKey("age"));
        assertNull(row.cells().get("age"));
        assertTrue(row.cells().containsKey("birthday"));
        assertNull(row.cells().get("birthday"));
    }
}
