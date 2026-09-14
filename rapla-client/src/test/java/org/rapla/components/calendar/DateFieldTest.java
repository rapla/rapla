package org.rapla.components.calendar;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Reproduces the bug introduced by PRD 001-A's Date→LocalDateTime migration:
 * {@link DateField#setDate(java.time.LocalDate)} was migrated to take {@code LocalDate},
 * but the implementation still calls {@code java.text.DateFormat.format(value)} which
 * only accepts {@code java.util.Date}. Constructing a {@code DateField} therefore
 * throws {@code IllegalArgumentException: Cannot format given Object as a Date},
 * which cascades up the Spring bean graph through {@code RaplaCalendar} →
 * {@code IntervalChooserPanel} → ... → {@code applicationViewSwing} and prevents
 * the Swing client from booting.
 *
 * <p>This test is headless-safe: {@code java.awt.headless} stays at whatever the
 * surefire JVM has, since DateField extends JTextField — Swing components can be
 * constructed in headless mode (only display would fail).
 */
@RunWith(JUnit4.class)
public class DateFieldTest
{
    @Test
    public void noArgConstructorDoesNotThrow()
    {
        // Reproduces the production failure: `new DateField()` calls setDate(LocalDate.now())
        // which tries to format a LocalDate via java.text.DateFormat → IllegalArgumentException.
        new DateField();
    }
}
