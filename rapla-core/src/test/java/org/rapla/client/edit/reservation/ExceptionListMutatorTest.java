package org.rapla.client.edit.reservation;

import org.junit.jupiter.api.Test;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.RepeatingType;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 contract pin for {@link ExceptionListMutator} (PRD 023 carve-out).
 *
 * <p>The mutator is a thin static helper around the {@link Repeating} entity's
 * exception-list mutation API. Tests use a recording {@link Repeating} stub
 * (via {@link Proxy}) so the helper's behaviour is pinned without depending
 * on the full entity graph.
 */
class ExceptionListMutatorTest
{
    /** Recording stub for {@link Repeating} — captures only the calls the
     *  mutator makes, in order. Everything else throws so a test failure
     *  surfaces an unexpected method invocation. */
    private static final class RecordingRepeating
    {
        final List<String> calls = new ArrayList<>();

        Repeating asInterface()
        {
            return (Repeating) Proxy.newProxyInstance(
                    Repeating.class.getClassLoader(),
                    new Class[] { Repeating.class },
                    (proxy, method, args) -> {
                        switch (method.getName())
                        {
                            case "addException":
                                calls.add("addException(" + args[0] + ")");
                                return null;
                            case "removeException":
                                calls.add("removeException(" + args[0] + ")");
                                return null;
                            case "addExceptions":
                                TimeInterval ti = (TimeInterval) args[0];
                                calls.add("addExceptions(" + ti.getStart() + "->" + ti.getEnd() + ")");
                                return null;
                            default:
                                throw new UnsupportedOperationException(
                                        "stub does not implement " + method.getName());
                        }
                    });
        }
    }

    private static TimeInterval interval(LocalDateTime start, LocalDateTime end)
    {
        return new TimeInterval(start, end);
    }

    private static final LocalDateTime D1 = LocalDateTime.of(2026, 6, 1, 0, 0);
    private static final LocalDateTime D2 = LocalDateTime.of(2026, 6, 2, 0, 0);
    private static final LocalDateTime D3 = LocalDateTime.of(2026, 6, 3, 0, 0);

    // ---------- applyAdditions ----------

    @Test
    void applyAdditionsCallsAddExceptionsForEachInterval()
    {
        RecordingRepeating r = new RecordingRepeating();
        List<TimeInterval> intervals = List.of(interval(D1, D2), interval(D3, D3.plusDays(1)));
        ExceptionListMutator.applyAdditions(r.asInterface(), intervals);
        assertEquals(List.of(
                "addExceptions(" + D1 + "->" + D2 + ")",
                "addExceptions(" + D3 + "->" + D3.plusDays(1) + ")"),
                r.calls);
    }

    @Test
    void applyAdditionsEmptyListNoCalls()
    {
        RecordingRepeating r = new RecordingRepeating();
        ExceptionListMutator.applyAdditions(r.asInterface(), List.of());
        assertTrue(r.calls.isEmpty());
    }

    @Test
    void applyAdditionsNullListTreatedAsEmpty()
    {
        RecordingRepeating r = new RecordingRepeating();
        ExceptionListMutator.applyAdditions(r.asInterface(), null);
        assertTrue(r.calls.isEmpty());
    }

    // ---------- revertAdditions ----------

    @Test
    void revertAdditionsRemovesIntervalStartForEach()
    {
        RecordingRepeating r = new RecordingRepeating();
        List<TimeInterval> intervals = List.of(interval(D1, D2), interval(D3, D3.plusDays(1)));
        ExceptionListMutator.revertAdditions(r.asInterface(), intervals);
        assertEquals(List.of(
                "removeException(" + D1 + ")",
                "removeException(" + D3 + ")"),
                r.calls);
    }

    @Test
    void revertAdditionsEmptyListNoCalls()
    {
        RecordingRepeating r = new RecordingRepeating();
        ExceptionListMutator.revertAdditions(r.asInterface(), List.of());
        assertTrue(r.calls.isEmpty());
    }

    // ---------- applyRemovals ----------

    @Test
    void applyRemovalsCallsRemoveExceptionForEachDate()
    {
        RecordingRepeating r = new RecordingRepeating();
        ExceptionListMutator.applyRemovals(r.asInterface(), List.of(D1, D2, D3));
        assertEquals(List.of(
                "removeException(" + D1 + ")",
                "removeException(" + D2 + ")",
                "removeException(" + D3 + ")"),
                r.calls);
    }

    @Test
    void applyRemovalsAcceptsArrayInputForLegacyCallers()
    {
        RecordingRepeating r = new RecordingRepeating();
        ExceptionListMutator.applyRemovals(r.asInterface(), new Object[] { D1, D2 });
        assertEquals(List.of(
                "removeException(" + D1 + ")",
                "removeException(" + D2 + ")"),
                r.calls);
    }

    @Test
    void applyRemovalsArrayWithNullEntryIgnored()
    {
        RecordingRepeating r = new RecordingRepeating();
        ExceptionListMutator.applyRemovals(r.asInterface(), new Object[] { D1, null, D2 });
        assertEquals(List.of(
                "removeException(" + D1 + ")",
                "removeException(" + D2 + ")"),
                r.calls);
    }

    @Test
    void applyRemovalsEmptyArrayNoCalls()
    {
        RecordingRepeating r = new RecordingRepeating();
        ExceptionListMutator.applyRemovals(r.asInterface(), new Object[0]);
        assertTrue(r.calls.isEmpty());
    }

    // ---------- revertRemovals ----------

    @Test
    void revertRemovalsCallsAddExceptionForEachDate()
    {
        RecordingRepeating r = new RecordingRepeating();
        ExceptionListMutator.revertRemovals(r.asInterface(), List.of(D1, D2));
        assertEquals(List.of(
                "addException(" + D1 + ")",
                "addException(" + D2 + ")"),
                r.calls);
    }

    @Test
    void revertRemovalsAcceptsArrayInputForLegacyCallers()
    {
        RecordingRepeating r = new RecordingRepeating();
        ExceptionListMutator.revertRemovals(r.asInterface(), new Object[] { D1, D2 });
        assertEquals(List.of(
                "addException(" + D1 + ")",
                "addException(" + D2 + ")"),
                r.calls);
    }

    @Test
    void revertRemovalsArrayWithNullEntryIgnored()
    {
        RecordingRepeating r = new RecordingRepeating();
        ExceptionListMutator.revertRemovals(r.asInterface(), new Object[] { D1, null });
        assertEquals(List.of("addException(" + D1 + ")"), r.calls);
    }

    // ---------- defensive ----------

    @Test
    void nullRepeatingIsNoOp()
    {
        // All four operations must accept a null Repeating without throwing
        // — Swing flow may invoke these when the underlying appointment has
        // been reset between user click and event dispatch.
        ExceptionListMutator.applyAdditions(null, List.of(interval(D1, D2)));
        ExceptionListMutator.revertAdditions(null, List.of(interval(D1, D2)));
        ExceptionListMutator.applyRemovals(null, List.of(D1));
        ExceptionListMutator.revertRemovals(null, List.of(D1));
        ExceptionListMutator.applyRemovals(null, new Object[] { D1 });
        ExceptionListMutator.revertRemovals(null, new Object[] { D1 });
    }
}
