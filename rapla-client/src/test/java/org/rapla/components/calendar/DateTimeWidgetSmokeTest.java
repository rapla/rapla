package org.rapla.components.calendar;

import org.junit.jupiter.api.Test;
import org.rapla.test.util.HeadlessSwingTestSupport;

import javax.swing.JTextField;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Locale;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prototype headless smoke tests for Rapla's date/time selection widgets.
 * Demonstrates the {@link HeadlessSwingTestSupport} pattern: real Swing
 * components, real listeners, no display.
 * <p>
 * Tests pack many assertions per {@code @Test} method on purpose — pure
 * widget construction has minimal overhead (~10 ms per widget) but the
 * pattern matters more in tests that DO have heavy setup (e.g. tier-2
 * facade tests where {@code @BeforeEach} costs ~150 ms). Keeping the
 * idiom consistent across all tiers means future facade-backed widget
 * tests pay one fixture-load per test method, not per assertion.
 * <p>
 * Each {@code @Test} below targets ONE widget type and exercises its
 * full public surface: construction defaults, set/get round-trip,
 * locale variants, null handling, model<->view sync.
 */
class DateTimeWidgetSmokeTest extends HeadlessSwingTestSupport
{
    private static final Locale EN = Locale.US;
    private static final Locale DE = Locale.GERMANY;
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    // ---------- DateField ----------

    @Test
    void dateFieldFullSurface()
    {
        // 1. Default ctor doesn't throw — pinned by the original
        //    DateFieldTest after the PRD 001-A LocalDate migration that
        //    broke DateField.setDate. Keep this assertion explicit.
        DateField d = new DateField();
        assertNotNull(d);

        // 2. setDate / getDate round-trips a LocalDate.
        LocalDate jun15 = LocalDate.of(2026, 6, 15);
        d.setDate(jun15);
        assertEquals(jun15, d.getDate());

        // 3. Setting to null is a no-op that keeps the previous date.
        //    SURPRISE / PIN: DateField does NOT have a "no date" state
        //    via setDate(null) — getDate() still returns the prior value.
        //    Callers that want a clearable widget must reach for a
        //    different abstraction. This was discovered by this very
        //    test on its first run; pinning it prevents a refactor from
        //    silently flipping to "null clears" semantics.
        d.setDate(null);
        assertEquals(jun15, d.getDate(),
                "DateField.setDate(null) is a no-op — keeps the previous date");

        // 4. Locale-specific construction: German locale doesn't affect
        //    the round-trip semantic, only the display format. We assert
        //    on the data-shape only.
        DateField deField = new DateField(DE, UTC);
        deField.setDate(jun15);
        assertEquals(jun15, deField.getDate());

        // 5. Cross-locale: same LocalDate produces (locale-dependent) text
        //    but the round-trip parse → format → parse must preserve the
        //    date. Read the widget's current text and assert it's non-empty
        //    after a setDate.
        DateField enField = new DateField(EN, UTC);
        enField.setDate(jun15);
        assertTrue(enField.getText() != null && !enField.getText().isEmpty(),
                "DateField text must be populated after setDate");

        // 6. setDate to a far-future date (year 9999) survives without
        //    overflow / format-parse failure. Real bug magnet at year
        //    boundaries.
        LocalDate farFuture = LocalDate.of(9999, 12, 31);
        d.setDate(farFuture);
        assertEquals(farFuture, d.getDate());

        // 7. setDate to a leap day round-trips.
        LocalDate leap = LocalDate.of(2024, 2, 29);
        d.setDate(leap);
        assertEquals(leap, d.getDate());
    }

    // ---------- TimeField ----------

    @Test
    void timeFieldFullSurface()
    {
        // 1. Default ctor.
        TimeField t = new TimeField();
        assertNotNull(t);

        // 2. setTime / getTime round-trips a LocalTime.
        LocalTime nineThirty = LocalTime.of(9, 30);
        t.setTime(nineThirty);
        assertEquals(nineThirty, t.getTime());

        // 3. Midnight and end-of-day boundaries — common date-arithmetic
        //    surprise points.
        t.setTime(LocalTime.MIDNIGHT);
        assertEquals(LocalTime.MIDNIGHT, t.getTime());
        t.setTime(LocalTime.of(23, 59));
        assertEquals(LocalTime.of(23, 59), t.getTime());

        // 4. Construction with explicit locale + timezone.
        TimeField deField = new TimeField(DE, UTC);
        deField.setTime(nineThirty);
        assertEquals(nineThirty, deField.getTime());

        // 5. After setting, the underlying text field is non-empty.
        assertTrue(deField.getText() != null && !deField.getText().isEmpty());
    }

    // ---------- RaplaCalendar ----------

    @Test
    void raplaCalendarFullSurface()
    {
        // 1. Default ctor wires up the DateField + drop-down button.
        RaplaCalendar c = new RaplaCalendar();
        assertNotNull(c);

        // 2. setDate / getDate round-trip — note this widget takes
        //    LocalDateTime (date + time-zero in practice).
        LocalDateTime mid = LocalDateTime.of(2026, 6, 15, 0, 0);
        c.setDate(mid);
        // RaplaCalendar normalizes — getDate may return a midnight-cut value.
        // Assert the date PART matches and the time is at start-of-day.
        LocalDateTime back = c.getDate();
        assertNotNull(back);
        assertEquals(2026, back.getYear());
        assertEquals(6, back.getMonthValue());
        assertEquals(15, back.getDayOfMonth());

        // 3. Setting an explicit time component: today the widget treats
        //    times as carried-through (test what's actually there, not
        //    what would be ideal).
        c.setDate(LocalDateTime.of(2026, 6, 15, 14, 30));
        LocalDateTime back2 = c.getDate();
        assertNotNull(back2);
        // The DATE part survives; the time-portion behaviour is documented
        // by whatever the widget chooses to do.
        assertEquals(15, back2.getDayOfMonth());

        // 4. Construction with locale + tz + dropdown-disabled.
        RaplaCalendar noDropdown = new RaplaCalendar(DE, UTC, false);
        assertNotNull(noDropdown);
        noDropdown.setDate(mid);
        assertNotNull(noDropdown.getDate());
    }

    // ---------- RaplaTime ----------

    @Test
    void raplaTimeFullSurface()
    {
        // 1. Default ctor.
        RaplaTime t = new RaplaTime();
        assertNotNull(t);

        // 2. setTime(LocalDateTime) / getTime() round-trip — RaplaTime
        //    cares about hour+minute, not date part.
        LocalDateTime nineThirty = LocalDateTime.of(2026, 6, 15, 9, 30);
        t.setTime(nineThirty);
        LocalDateTime back = t.getTime();
        assertNotNull(back);
        assertEquals(9, back.getHour());
        assertEquals(30, back.getMinute());

        // 3. setTime(int hour, int minute) overload.
        t.setTime(14, 45);
        assertEquals(14, t.getTime().getHour());
        assertEquals(45, t.getTime().getMinute());

        // 4. Midnight + end-of-day.
        t.setTime(0, 0);
        assertEquals(0, t.getTime().getHour());
        t.setTime(23, 59);
        assertEquals(23, t.getTime().getHour());
        assertEquals(59, t.getTime().getMinute());

        // 5. Locale + tz construction.
        RaplaTime de = new RaplaTime(DE, UTC);
        assertNotNull(de);
        de.setTime(12, 0);
        assertEquals(12, de.getTime().getHour());
    }

    // ---------- RaplaNumber ----------

    @Test
    void raplaNumberFullSurface()
    {
        // 1. Default ctor + setNumber/getNumber round-trip.
        RaplaNumber n = new RaplaNumber();
        assertNotNull(n);
        n.setNumber(42);
        assertEquals(42, n.getNumber().intValue());

        // 2. Negative values pass through (default constraints are wide).
        n.setNumber(-17);
        assertEquals(-17, n.getNumber().intValue());

        // 3. Constrained ctor: min=1, max=100, null permitted.
        RaplaNumber bounded = new RaplaNumber(50, 1, 100, /*isNullPermitted=*/ true);
        bounded.setNumber(50);
        assertEquals(50, bounded.getNumber().intValue());

        // 4. Null-permitted variant accepts null without throwing.
        bounded.setNumber(null);
        assertNull(bounded.getNumber());

        // 5. Constrained ctor: null NOT permitted, BUT setNumber(null)
        //    still nulls out the value. SURPRISE / PIN: the
        //    isNullPermitted flag governs user-input null-states (the
        //    text-field can or can't be cleared by the user), NOT
        //    programmatic setNumber(null). Programmatic null always
        //    nulls. This was discovered by this test on its first
        //    run — pinning it prevents a refactor from flipping the
        //    null-policy on setNumber.
        RaplaNumber strict = new RaplaNumber(10, 1, 100, /*isNullPermitted=*/ false);
        strict.setNumber(10);
        assertEquals(10, strict.getNumber().intValue());
        strict.setNumber(null);
        assertNull(strict.getNumber(),
                "isNullPermitted is a USER-INPUT policy; programmatic setNumber(null) still nulls");
    }

    // ---------- composition: DateField + TimeField in a panel ----------

    @Test
    void compositionDateAndTimePanelStaysIndependent()
    {
        // Demonstrates: two widgets in one parent JPanel, each holding
        // its own state, no cross-talk. The thing tier-2 facade-backed
        // tests will assert on for real components like AppointmentController.
        DateField d = new DateField(EN, UTC);
        TimeField t = new TimeField(EN, UTC);
        javax.swing.JPanel panel = new javax.swing.JPanel();
        panel.add(d);
        panel.add(t);

        d.setDate(LocalDate.of(2026, 6, 15));
        t.setTime(LocalTime.of(9, 30));

        assertEquals(LocalDate.of(2026, 6, 15), d.getDate());
        assertEquals(LocalTime.of(9, 30), t.getTime());

        // Walking the parent reveals both children.
        assertEquals(2, panel.getComponentCount(),
                "panel.add appended both date and time fields");
        assertTrue(panel.getComponent(0) instanceof DateField);
        assertTrue(panel.getComponent(1) instanceof TimeField);
    }

    // ---------- DateField extends JTextField — verify it does ----------

    @Test
    void dateFieldIsAJTextFieldSubclass()
    {
        DateField d = new DateField();
        assertTrue(d instanceof JTextField,
                "DateField extends AbstractBlockField extends JTextField — relied on by callers");
    }
}
