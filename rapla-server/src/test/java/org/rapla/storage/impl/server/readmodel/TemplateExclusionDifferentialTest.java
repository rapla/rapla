package org.rapla.storage.impl.server.readmodel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rapla.entities.Annotatable;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.RaplaObjectAnnotations;
import org.rapla.entities.domain.Reservation;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Reproduces (and guards) the window-first TEMPLATE leak found on the real store (event eedc7134, which
 * carries a {@code KEY_TEMPLATE} annotation): the legacy {@code queryAppointmentsSync} excludes template
 * reservations (its {@code isTemplate != isResourceTemplate} post-loop), but the global index is
 * binding-complete and held the template's appointments, so {@code reservationsInWindowGlobal} leaked the
 * template reservation into normal window-first results. After the fix both paths exclude it.
 */
class TemplateExclusionDifferentialTest extends DifferentialReadSupport
{
    private static final LocalDateTime BASE_START = LocalDateTime.parse("2025-04-02T14:00:00");
    private static final LocalDateTime BASE_END   = LocalDateTime.parse("2025-04-02T17:45:00");

    @BeforeEach
    void setUp() throws Exception { initDifferential(); }

    private LocalDateTime occFrom(int n) { return BASE_START.plusWeeks(n).toLocalDate().atStartOfDay(); }
    private LocalDateTime occTo(int n)   { return occFrom(n).plusWeeks(1); }

    @Test
    void templateReservation_excludedFromWindowFirstAndPerAlloc() throws Exception
    {
        List<Allocatable> allocs = someAllocatables(3);

        // A normal weekly reservation on the 3 allocatables (must appear).
        store("normal-weekly", allocs, weekly(appt(BASE_START, BASE_END), 9));

        // A TEMPLATE reservation: identical shape (weekly, same allocatables/window) but marked with the
        // KEY_TEMPLATE annotation — like the real eedc7134. It must NOT appear in normal window reads.
        Reservation tmpl = store("template-weekly", allocs, weekly(appt(BASE_START, BASE_END), 9));
        Reservation editable = edit(tmpl);
        ((Annotatable) editable).setAnnotation(RaplaObjectAnnotations.KEY_TEMPLATE, "rtemplate-test-0000");
        storeEdit(editable);

        // window-first global must equal the legacy unscoped read (both: normal present, template absent).
        assertGlobalConsistent("template excluded [base week]", occFrom(0), occTo(0));
        assertGlobalConsistent("template excluded [occurrence wk5]", occFrom(5), occTo(5));
        assertGlobalConsistent("template excluded [all-time]", null, null);
        // per-allocatable scoped path already excludes templates via the post-loop — assert it stays so.
        assertScopedConsistent("template scoped [wk5]", occFrom(5), occTo(5), allocs);
    }
}
