package org.rapla.test.util;

import org.junit.jupiter.api.BeforeAll;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.InvocationTargetException;

/**
 * Base class for **headless Swing component tests** — tests that
 * construct real Swing widgets (JPanel / JTextField / JComboBox / etc.)
 * without an actual display, exercise them via public methods + the
 * existing widget API (focus listeners, action listeners), and assert
 * on the resulting state.
 *
 * <p>The trick is Java's {@code java.awt.headless} flag: when set
 * before any AWT class loads, {@code GraphicsEnvironment.isHeadless()}
 * is {@code true}, the default {@code Toolkit} is a headless one, and
 * calls that <em>require</em> a display ({@code Window.setVisible(true)},
 * {@code Robot}, mouse-position queries) throw {@code HeadlessException}.
 * Calls that merely construct or mutate components work fine — that's
 * 80% of what we want to test.
 *
 * <h2>What works</h2>
 * <ul>
 *   <li>Instantiating any {@code JComponent}: panels, fields, buttons, tables, trees.</li>
 *   <li>Calling {@code setText} / {@code setDate} / {@code setSelected} / etc. on widgets.</li>
 *   <li>Reading back the resulting model state via getters.</li>
 *   <li>Firing {@code ActionEvent} / {@code DocumentEvent} programmatically.</li>
 *   <li>Walking a JComponent tree with the helpers below.</li>
 * </ul>
 *
 * <h2>What does NOT work</h2>
 * <ul>
 *   <li>{@code JFrame.setVisible(true)} / {@code JDialog.setVisible(true)} — throws.
 *       <b>Solution:</b> stub the {@code DialogUiFactoryInterface} bean so
 *       {@code start(...)} never calls {@code setVisible}.</li>
 *   <li>Focus traversal — needs the native event queue.</li>
 *   <li>{@code Robot} mouse events — needs a display.</li>
 *   <li>{@code Component.paint()} geometry — layout managers don't fire
 *       without a display, so {@code getPreferredSize()} is reliable but
 *       {@code getBounds()} after invisible-layout is not.</li>
 * </ul>
 *
 * <h2>How to use</h2>
 * <pre>{@code
 *   class MyWidgetSmokeTest extends HeadlessSwingTestSupport {
 *       @Test
 *       void widgetRoundTripsCleanState() {
 *           MyWidget w = new MyWidget();
 *           w.setValue("foo");
 *           assertEquals("foo", w.getValue());
 *           // Pack many assertions into one @Test — saves the per-test
 *           // overhead in tests that DO have heavy setup (facade tests).
 *       }
 *   }
 * }</pre>
 *
 * <h2>For tests that also need a facade</h2>
 *
 * Combine with {@code FacadeTestSupport} via composition rather than
 * inheritance — JUnit 5 doesn't allow two base classes. Either use a
 * JUnit extension, or copy the {@code @BeforeEach} setup of
 * {@code FacadeTestSupport} into a separate base that also extends this.
 */
public abstract class HeadlessSwingTestSupport
{
    /**
     * Set the headless flag if not already locked in. NOT enforced —
     * rapla-bom intentionally pre-sets {@code java.awt.headless=false} at
     * the surefire JVM level (see rapla-bom/pom.xml) so legacy loaders'
     * {@code setProperty("java.awt.headless", "true")} become no-ops. With
     * forkCount=0 we share one Maven JVM across all rapla-client tests, so
     * by the time we get here, GraphicsEnvironment is already initialised
     * in either mode — we can't flip it.
     * <p>
     * That's fine for widget-construction smoke tests: components build and
     * round-trip state in both modes. The headless flag only matters if a
     * test calls {@code setVisible(true)} — which these tests don't.
     */
    @BeforeAll
    static void enableHeadless()
    {
        if (!GraphicsEnvironment.isHeadless())
        {
            // Best effort; if AWT has already initialised this is a no-op.
            System.setProperty("java.awt.headless", "true");
        }
    }

    /**
     * Run the runnable on the Swing EDT and wait for completion.
     * Many Swing operations are EDT-only by contract; the static
     * {@code SwingUtilities.invokeAndWait(...)} works in headless mode
     * because the EDT thread is still alive (it just doesn't pump
     * native events). Test bodies that touch listener-fired state are
     * safer when wrapped.
     * <p>Skipped (runs on the current thread) when already on the EDT.
     */
    protected static void onEdt(Runnable r) throws InterruptedException, InvocationTargetException
    {
        if (SwingUtilities.isEventDispatchThread())
        {
            r.run();
            return;
        }
        SwingUtilities.invokeAndWait(r);
    }
}
