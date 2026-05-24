package org.rapla.components.util;

import java.util.HashMap;

/**
 * Bidirectional hash map: O(1) lookup by key AND by value. Replaces the
 * single Apache commons-collections4 {@code DualHashBidiMap} use site
 * (see {@code LocalAbstractCachableOperator}). Not thread-safe — existing
 * call sites either populate once before publication or hold an external
 * lock.
 */
public final class TwoWayMap<K, V>
{
    private final HashMap<K, V> forward = new HashMap<>();
    private final HashMap<V, K> inverse = new HashMap<>();

    public V get(K key)
    {
        return forward.get(key);
    }

    public K getKey(V value)
    {
        return inverse.get(value);
    }

    public void put(K key, V value)
    {
        V oldValue = forward.remove(key);
        if (oldValue != null)
        {
            inverse.remove(oldValue);
        }
        K oldKey = inverse.remove(value);
        if (oldKey != null)
        {
            forward.remove(oldKey);
        }
        forward.put(key, value);
        inverse.put(value, key);
    }

    public void remove(K key)
    {
        V value = forward.remove(key);
        if (value != null)
        {
            inverse.remove(value);
        }
    }
}
