package org.rapla.storage.impl.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TwoWayMapTest
{
    @Test
    void putThenGetAndGetKey()
    {
        TwoWayMap<String, Integer> m = new TwoWayMap<>();
        m.put("a", 1);
        m.put("b", 2);
        assertEquals(Integer.valueOf(1), m.get("a"));
        assertEquals(Integer.valueOf(2), m.get("b"));
        assertEquals("a", m.getKey(1));
        assertEquals("b", m.getKey(2));
    }

    @Test
    void removeDropsBothDirections()
    {
        TwoWayMap<String, Integer> m = new TwoWayMap<>();
        m.put("a", 1);
        m.remove("a");
        assertNull(m.get("a"));
        assertNull(m.getKey(1));
    }

    @Test
    void puttingSameKeyWithNewValueEvictsOldInverse()
    {
        TwoWayMap<String, Integer> m = new TwoWayMap<>();
        m.put("a", 1);
        m.put("a", 2);
        assertEquals(Integer.valueOf(2), m.get("a"));
        assertNull(m.getKey(1), "old value 1 must no longer map back to any key");
        assertEquals("a", m.getKey(2));
    }

    @Test
    void puttingSameValueWithNewKeyEvictsOldForward()
    {
        TwoWayMap<String, Integer> m = new TwoWayMap<>();
        m.put("a", 1);
        m.put("b", 1);
        assertNull(m.get("a"), "old key a must no longer have a value");
        assertEquals(Integer.valueOf(1), m.get("b"));
        assertEquals("b", m.getKey(1));
    }

    @Test
    void removeUnknownKeyIsNoop()
    {
        TwoWayMap<String, Integer> m = new TwoWayMap<>();
        m.put("a", 1);
        m.remove("zzz");
        assertEquals(Integer.valueOf(1), m.get("a"));
        assertEquals("a", m.getKey(1));
    }

    @Test
    void getUnknownReturnsNull()
    {
        TwoWayMap<String, Integer> m = new TwoWayMap<>();
        assertNull(m.get("a"));
        assertNull(m.getKey(1));
    }
}
