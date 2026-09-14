package org.rapla.entities.dynamictype.internal;

import org.junit.jupiter.api.Test;
import org.rapla.entities.extensionpoints.Function;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 unit tests for the generic expression-language functions
 * {@code now()} and {@code isBefore(a, b)} (overdue-style view computations,
 * e.g. {@code isBefore(end(p), now())}).
 */
class StandardFunctionsNowIsBeforeTest
{
    private static Function constant(Object value)
    {
        return new Function("org.rapla", "const", List.of())
        {
            @Override public Object eval(EvalContext context)
            {
                return value;
            }
        };
    }

    @Test
    void nowReturnsCurrentServerTime() throws Exception
    {
        LocalDateTime before = LocalDateTime.now().minusMinutes(1);
        Object result = new StandardFunctions.NowFunction(List.of()).eval(null);
        LocalDateTime after = LocalDateTime.now().plusMinutes(1);
        assertTrue(result instanceof LocalDateTime);
        LocalDateTime now = (LocalDateTime) result;
        assertTrue(now.isAfter(before) && now.isBefore(after));
    }

    @Test
    void isBeforeComparesDateTimes() throws Exception
    {
        LocalDateTime earlier = LocalDateTime.of(2026, 7, 1, 8, 0);
        LocalDateTime later = LocalDateTime.of(2026, 7, 8, 12, 0);
        assertEquals(Boolean.TRUE,
                new StandardFunctions.IsBeforeFunction(List.of(constant(earlier), constant(later))).eval(null));
        assertEquals(Boolean.FALSE,
                new StandardFunctions.IsBeforeFunction(List.of(constant(later), constant(earlier))).eval(null));
        assertEquals(Boolean.FALSE,
                new StandardFunctions.IsBeforeFunction(List.of(constant(later), constant(later))).eval(null));
    }

    @Test
    void isBeforeIsNullSafeAndTypeSafe() throws Exception
    {
        LocalDateTime date = LocalDateTime.of(2026, 7, 1, 8, 0);
        assertNull(new StandardFunctions.IsBeforeFunction(List.of(constant(null), constant(date))).eval(null));
        assertNull(new StandardFunctions.IsBeforeFunction(List.of(constant(date), constant("2026-07-08"))).eval(null));
    }

    @Test
    void isBeforeComparesStrings() throws Exception
    {
        assertEquals(Boolean.TRUE,
                new StandardFunctions.IsBeforeFunction(List.of(constant("a"), constant("b"))).eval(null));
        assertFalse((Boolean) new StandardFunctions.IsBeforeFunction(List.of(constant("b"), constant("a"))).eval(null));
    }
}
