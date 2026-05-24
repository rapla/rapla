package org.rapla.storage.impl.server;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.SortedSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks in the behaviours that LocalAbstractCachableOperator relies on from the
 * change-feed structure that previously was Apache commons-collections4
 * DualTreeBidiMap.
 */
class IndexedSortedMapTest
{
    /** Test value that mirrors DeleteUpdateEntry's compareTo ordering: (timestamp, id). */
    private static final class Entry implements Comparable<Entry>
    {
        final String id;
        LocalDateTime timestamp;

        Entry(String id, LocalDateTime timestamp)
        {
            this.id = id;
            this.timestamp = timestamp;
        }

        @Override
        public int compareTo(Entry o)
        {
            if (o == this) return 0;
            int c = this.timestamp.compareTo(o.timestamp);
            return c != 0 ? c : this.id.compareTo(o.id);
        }

        // equals/hashCode by id only — same inconsistent-with-compareTo pattern as DeleteUpdateEntry
        @Override
        public boolean equals(Object o) { return o instanceof Entry && id.equals(((Entry) o).id); }
        @Override
        public int hashCode() { return id.hashCode(); }
        @Override
        public String toString() { return id + "@" + timestamp; }
    }

    private static Entry e(String id, int minuteOfHour)
    {
        return new Entry(id, LocalDateTime.of(2026, 1, 1, 12, minuteOfHour));
    }

    @Test
    void putThenGet()
    {
        IndexedSortedMap<String, Entry> m = new IndexedSortedMap<>(Comparator.naturalOrder());
        Entry a = e("a", 1);
        m.put(a.id, a);
        assertSame(a, m.get("a"));
        assertNull(m.get("missing"));
    }

    @Test
    void puttingSameKeyReplacesValueInSortedView()
    {
        // The critical invariant: only one entry per id in the sorted view, even after
        // the same id is put twice with different timestamps. Catches the case where
        // a naive TreeSet would hold both copies.
        IndexedSortedMap<String, Entry> m = new IndexedSortedMap<>(Comparator.naturalOrder());
        Entry first = e("a", 1);
        Entry second = e("a", 5);
        m.put(first.id, first);
        m.put(second.id, second);
        SortedSet<Entry> all = m.tailSetByValue(e("zzz", 0)); // fromElement before everything
        List<Entry> entries = new ArrayList<>(all);
        assertEquals(1, entries.size(), "exactly one entry per id, regardless of put-count");
        assertSame(second, entries.get(0));
    }

    @Test
    void mutateThenPutPattern()
    {
        // The pattern LocalAbstractCachableOperator.addToDeleteUpdate uses:
        // remove(id) -> mutate timestamp -> put(id, entry). Must end up with a single
        // entry at the new position.
        IndexedSortedMap<String, Entry> m = new IndexedSortedMap<>(Comparator.naturalOrder());
        Entry a = e("a", 1);
        m.put(a.id, a);
        Entry removed = m.remove("a");
        assertSame(a, removed);
        a.timestamp = LocalDateTime.of(2026, 1, 1, 12, 9);
        m.put(a.id, a);
        SortedSet<Entry> tail = m.tailSetByValue(e("", 0));
        assertEquals(1, tail.size());
        assertSame(a, tail.first());
    }

    @Test
    void tailSetByValueReturnsEntriesAtOrAfterFromElement()
    {
        IndexedSortedMap<String, Entry> m = new IndexedSortedMap<>(Comparator.naturalOrder());
        Entry a = e("a", 1);
        Entry b = e("b", 3);
        Entry c = e("c", 7);
        m.put(a.id, a);
        m.put(b.id, b);
        m.put(c.id, c);
        // fromElement at minute 3 with empty id — anything with timestamp >= minute 3 should appear
        Entry from = new Entry("", LocalDateTime.of(2026, 1, 1, 12, 3));
        List<Entry> tail = new ArrayList<>(m.tailSetByValue(from));
        assertEquals(2, tail.size());
        assertSame(b, tail.get(0));
        assertSame(c, tail.get(1));
    }

    @Test
    void equalTimestampsBothSurviveInTailSet()
    {
        // The reason DeleteUpdateEntry.compareTo tie-breaks on id: without it,
        // TreeSet would collapse same-timestamp entries to one. This test
        // proves the tie-break still works with the supplied Comparator.
        IndexedSortedMap<String, Entry> m = new IndexedSortedMap<>(Comparator.naturalOrder());
        LocalDateTime sameTime = LocalDateTime.of(2026, 1, 1, 12, 4);
        Entry a = new Entry("a", sameTime);
        Entry b = new Entry("b", sameTime);
        m.put(a.id, a);
        m.put(b.id, b);
        Entry from = new Entry("", sameTime);
        List<Entry> tail = new ArrayList<>(m.tailSetByValue(from));
        assertEquals(2, tail.size(), "both same-timestamp entries must be retained");
        assertTrue(tail.contains(a));
        assertTrue(tail.contains(b));
    }

    @Test
    void removeReturnsPrevValueAndClearsSortedView()
    {
        // addToDeleteUpdate (line 1551) actually uses the return value of remove
        // to log a warning if it was unexpectedly null.
        IndexedSortedMap<String, Entry> m = new IndexedSortedMap<>(Comparator.naturalOrder());
        Entry a = e("a", 1);
        m.put(a.id, a);
        assertSame(a, m.remove("a"));
        assertNull(m.get("a"));
        assertTrue(m.tailSetByValue(e("", 0)).isEmpty());
        assertNull(m.remove("a"), "second remove returns null");
    }
}
