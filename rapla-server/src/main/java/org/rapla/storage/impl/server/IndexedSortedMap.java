package org.rapla.storage.impl.server;

import java.util.Comparator;
import java.util.HashMap;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Map keyed by K with values that are also indexed in a sorted view by an
 * external Comparator. Supports replace-by-key + range-scan-by-value, the two
 * primitives the change-feed in {@link LocalAbstractCachableOperator} needs.
 * Replaces the single use of Apache commons-collections4 DualTreeBidiMap.
 *
 * <p>The supplied Comparator must be a total order — equal-only-on-identical
 * entries — otherwise the TreeSet collapses values it considers equal and
 * silently loses data. DeleteUpdateEntry.compareTo provides this via
 * (timestamp, id) tie-break.
 *
 * <p>Not thread-safe — call sites synchronize externally on the map instance.
 */
final class IndexedSortedMap<K, V>
{
    private final HashMap<K, V> byKey = new HashMap<>();
    private final TreeSet<V> sortedByValue;

    IndexedSortedMap(Comparator<? super V> valueComparator)
    {
        this.sortedByValue = new TreeSet<>(valueComparator);
    }

    V get(K key)
    {
        return byKey.get(key);
    }

    /** Inserts or replaces the entry for {@code key}; old value (if any) is removed from the sorted view. */
    void put(K key, V value)
    {
        V prev = byKey.put(key, value);
        if (prev != null && prev != value)
        {
            sortedByValue.remove(prev);
        }
        sortedByValue.add(value);
    }

    /** Removes the entry for {@code key} and returns the previous value, or null if absent. */
    V remove(K key)
    {
        V prev = byKey.remove(key);
        if (prev != null)
        {
            sortedByValue.remove(prev);
        }
        return prev;
    }

    /** Live view of all values >= fromElement, in Comparator order. */
    SortedSet<V> tailSetByValue(V fromElement)
    {
        return sortedByValue.tailSet(fromElement);
    }
}
