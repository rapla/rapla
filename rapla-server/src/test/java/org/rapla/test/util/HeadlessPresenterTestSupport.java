package org.rapla.test.util;

import org.junit.jupiter.api.BeforeEach;

/**
 * Tier-2 base for presenter / model tests that need a real
 * {@link org.rapla.facade.RaplaFacade} (via {@link FacadeTestSupport})
 * plus a deterministic clock and a slot for recording views.
 * <p>
 * Companion to PRD 023 (presenter extraction) + PRD 025 (this harness).
 * <pre>{@code
 *   class ReservationEditPresenterTest extends HeadlessPresenterTestSupport {
 *       RecordingView<ReservationView> view;
 *       ReservationEditPresenter presenter;
 *
 *       @BeforeEach
 *       void wirePresenter() {
 *           view = RecordingView.of(ReservationView.class);
 *           presenter = new ReservationEditPresenter(facade, view.proxy(), clock);
 *       }
 *
 *       @Test
 *       void editCallsShow() throws Exception {
 *           Reservation r = facade.newReservation(...);
 *           presenter.edit(r, true);
 *           view.assertCalled("show");
 *       }
 *   }
 * }</pre>
 *
 * <p>Inheritance: extends {@link FacadeTestSupport} so the {@code facade}
 * field is populated against {@code testdefault.xml} per-test. JUnit 5
 * lifecycle annotations chain correctly via inheritance.
 */
public abstract class HeadlessPresenterTestSupport extends FacadeTestSupport
{
    /** Deterministic clock — pin {@code today()} before exercising date logic. */
    protected MutableClock clock;

    @BeforeEach
    void setUpHarness()
    {
        clock = new MutableClock();
    }
}
