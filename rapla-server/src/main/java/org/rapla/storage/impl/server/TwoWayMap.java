package org.rapla.storage.impl.server;

import java.util.HashMap;

/**
 * Bidirectional hash map: O(1) lookup by key AND by value. Replaces the single
 * use of Apache commons-collections4 DualHashBidiMap. Not thread-safe — current
 * call sites in {@link LocalAbstractCachableOperator} don't synchronize this
 * structure either.
 */
final class TwoWayMap<K, V>
{
    private final HashMap<K, V> forward = new HashMap<>();
    private final HashMap<V, K> inverse = new HashMap<>();

    V get(K key)
    {
        return forward.get(key);
    }

    K getKey(V value)
    {
        return inverse.get(value);
    }

    void put(K key, V value)
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

    void remove(K key)
    {
        V value = forward.remove(key);
        if (value != null)
        {
            inverse.remove(value);
        }
    }
}
