package org.rapla.test.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 unit coverage of the {@link RecordingView} reflective proxy
 * pattern. Uses a small synthetic interface so the test doesn't pull in
 * a real Rapla view.
 */
class RecordingViewTest
{
    interface SampleView
    {
        void show(String name);
        void hide();
        String prompt(String question);
        boolean isVisible();
        int rowCount();
    }

    @Test
    void recordsEveryCallInOrder()
    {
        RecordingView<SampleView> view = RecordingView.of(SampleView.class);
        SampleView v = view.proxy();
        v.show("hello");
        v.hide();
        v.show("again");
        List<RecordingView.Call> calls = view.calls();
        assertEquals(3, calls.size());
        assertEquals("show", calls.get(0).method());
        assertEquals("hello", calls.get(0).arg(0));
        assertEquals("hide", calls.get(1).method());
        assertEquals("show", calls.get(2).method());
        assertEquals("again", calls.get(2).arg(0));
    }

    @Test
    void assertCalledPassesWhenCalled()
    {
        RecordingView<SampleView> view = RecordingView.of(SampleView.class);
        view.proxy().show("x");
        view.assertCalled("show");
    }

    @Test
    void assertCalledFailsWhenNotCalled()
    {
        RecordingView<SampleView> view = RecordingView.of(SampleView.class);
        assertThrows(AssertionError.class, () -> view.assertCalled("show"));
    }

    @Test
    void assertNeverCalledPassesWhenAbsent()
    {
        RecordingView<SampleView> view = RecordingView.of(SampleView.class);
        view.assertNeverCalled("show");
    }

    @Test
    void assertNeverCalledFailsWhenPresent()
    {
        RecordingView<SampleView> view = RecordingView.of(SampleView.class);
        view.proxy().show("x");
        assertThrows(AssertionError.class, () -> view.assertNeverCalled("show"));
    }

    @Test
    void lastCallReturnsMostRecent()
    {
        RecordingView<SampleView> view = RecordingView.of(SampleView.class);
        view.proxy().show("first");
        view.proxy().show("second");
        assertEquals("second", view.lastCall("show").arg(0));
    }

    @Test
    void callCountMatchesInvocations()
    {
        RecordingView<SampleView> view = RecordingView.of(SampleView.class);
        view.proxy().show("a");
        view.proxy().show("b");
        view.proxy().show("c");
        assertEquals(3, view.callCount("show"));
        assertEquals(0, view.callCount("hide"));
    }

    @Test
    void clearCallsResetsRecording()
    {
        RecordingView<SampleView> view = RecordingView.of(SampleView.class);
        view.proxy().show("a");
        view.clearCalls();
        assertTrue(view.calls().isEmpty());
    }

    @Test
    void defaultReturnIsNullForObjectAndZeroForPrimitive()
    {
        RecordingView<SampleView> view = RecordingView.of(SampleView.class);
        assertNull(view.proxy().prompt("?"));
        assertEquals(0, view.proxy().rowCount());
        // boolean default is false (autoboxed)
        assertEquals(false, view.proxy().isVisible());
    }

    @Test
    void stubReturnsCannedValue()
    {
        RecordingView<SampleView> view = RecordingView.of(SampleView.class)
                .stub("prompt", "yes")
                .stub("rowCount", 42);
        assertEquals("yes", view.proxy().prompt("any"));
        assertEquals(42, view.proxy().rowCount());
        // Still records the calls even when stubbed.
        assertEquals(2, view.calls().size());
    }

    @Test
    void rejectsConcreteClass()
    {
        assertThrows(IllegalArgumentException.class,
                () -> RecordingView.of(String.class));
    }

    @Test
    void equalsAndHashCodeAreInstanceIdentity()
    {
        RecordingView<SampleView> a = RecordingView.of(SampleView.class);
        RecordingView<SampleView> b = RecordingView.of(SampleView.class);
        assertSame(a.proxy(), a.proxy());
        // The two proxies are distinct instances.
        assertTrue(a.proxy() != b.proxy());
        // Object-method calls aren't recorded:
        a.proxy().toString();
        a.proxy().hashCode();
        assertTrue(a.calls().isEmpty(),
                "Object methods should not be recorded");
    }
}
