package org.rapla.storage.impl.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rapla.entities.User;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.internal.AppointmentImpl;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.framework.RaplaException;
import org.rapla.test.util.FacadeTestSupport;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-2 pin for PRD 056 §9 {@code checkIdIntegrity} check #2 — an appointment
 * id that already lives in a <em>different</em> reservation must be rejected at
 * the operator dispatch choke point. Two appointments sharing a
 * {@code ReferenceInfo} corrupt the conflict engine, the appointment/block
 * index and restrictions, and violate the composite-sub-entity invariant.
 *
 * <p>Drives the guard through {@code facade.store(...)} (the same dispatch path
 * every write takes) so the check can't be bypassed by GraphQL, Swing, or a
 * plugin import.
 */
class AppointmentIdIntegrityTest extends FacadeTestSupport
{
    private User actingUser;
    private DynamicType eventType;

    @BeforeEach
    void resolveFixture() throws Exception
    {
        for (User u : facade.getUsers())
        {
            if (u.isAdmin()) { actingUser = u; break; }
        }
        assertNotNull(actingUser, "fixture must include an admin");

        DynamicType[] reservationTypes =
                facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);
        assertTrue(reservationTypes.length > 0, "fixture must include a reservation type");
        eventType = reservationTypes[0];
    }

    private Reservation makeReservation(String name, LocalDateTime start, LocalDateTime end) throws Exception
    {
        Classification c = eventType.newClassification();
        if (c.getType().getAttribute("name") != null) c.setValue("name", name);
        Reservation r = facade.newReservation(c, actingUser);
        Appointment a = facade.newAppointmentWithUser(start, end, actingUser);
        r.addAppointment(a);
        return r;
    }

    @Test
    void appointmentIdLivingInAnotherReservationIsRejected() throws Exception
    {
        Reservation a = makeReservation("ID-INTEGRITY-A",
                LocalDateTime.parse("2030-06-10T09:00"),
                LocalDateTime.parse("2030-06-10T11:00"));
        facade.store(a);
        String stolenAppointmentId = a.getAppointments()[0].getId();

        Reservation b = makeReservation("ID-INTEGRITY-B",
                LocalDateTime.parse("2030-06-11T09:00"),
                LocalDateTime.parse("2030-06-11T11:00"));
        ((AppointmentImpl) b.getAppointments()[0]).setId(stolenAppointmentId);

        assertThrows(RaplaException.class, () -> facade.store(b),
                "storing a reservation whose appointment id already lives in another reservation must fail");
    }

    @Test
    void reStoringOwnReservationKeepsItsAppointmentIds() throws Exception
    {
        Reservation a = makeReservation("ID-INTEGRITY-SELF",
                LocalDateTime.parse("2030-07-10T09:00"),
                LocalDateTime.parse("2030-07-10T11:00"));
        facade.store(a);

        // Re-store the same reservation (an update): the appointment id resolves
        // to the *same* reservation, so the integrity guard must NOT fire.
        Reservation editable = facade.edit(a);
        editable.getClassification().setValue("name", "ID-INTEGRITY-SELF-renamed");
        facade.store(editable);
    }

    // === PRD 056 §9 — id syntax validation for NEW entities ===
    // Rule: ASCII alphanumerics + hyphen, alphanumeric start, length 8-64
    // (Tools.isValidEntityId). Only enforced for entities not yet persistent —
    // legacy store ids (e.g. period_1 with underscore) stay valid on update.

    @Test
    void newReservationWithTooShortIdIsRejected() throws Exception
    {
        Reservation r = makeReservation("ID-SYNTAX-SHORT",
                LocalDateTime.parse("2030-08-10T09:00"),
                LocalDateTime.parse("2030-08-10T11:00"));
        ((org.rapla.entities.storage.internal.SimpleEntity) r).setId("abc1234"); // 7 chars
        assertThrows(RaplaException.class, () -> facade.store(r),
                "new reservation with a 7-char id must fail syntax validation");
    }

    @Test
    void newReservationWithIllegalCharacterIdIsRejected() throws Exception
    {
        Reservation r = makeReservation("ID-SYNTAX-CHARSET",
                LocalDateTime.parse("2030-08-11T09:00"),
                LocalDateTime.parse("2030-08-11T11:00"));
        ((org.rapla.entities.storage.internal.SimpleEntity) r).setId("e47ac10b;58cc-4372");
        assertThrows(RaplaException.class, () -> facade.store(r),
                "new reservation with ';' in the id must fail syntax validation");
    }

    @Test
    void newAppointmentWithInvalidIdIsRejected() throws Exception
    {
        Reservation r = makeReservation("ID-SYNTAX-APP",
                LocalDateTime.parse("2030-08-12T09:00"),
                LocalDateTime.parse("2030-08-12T11:00"));
        ((AppointmentImpl) r.getAppointments()[0]).setId("bad id with spaces");
        assertThrows(RaplaException.class, () -> facade.store(r),
                "new appointment with whitespace in the id must fail syntax validation");
    }

    @Test
    void newAllocatableWithInvalidIdIsRejected() throws Exception
    {
        org.rapla.entities.domain.Allocatable alloc =
                facade.newAllocatable(facade.getDynamicTypes(
                        org.rapla.entities.dynamictype.DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE)[0]
                        .newClassification(), actingUser);
        ((org.rapla.entities.storage.internal.SimpleEntity) alloc).setId("x".repeat(65)); // too long
        assertThrows(RaplaException.class, () -> facade.store(alloc),
                "new allocatable with a 65-char id must fail syntax validation");
    }

    @Test
    void newReservationWithValidClientUuidPasses() throws Exception
    {
        Reservation r = makeReservation("ID-SYNTAX-OK",
                LocalDateTime.parse("2030-08-13T09:00"),
                LocalDateTime.parse("2030-08-13T11:00"));
        ((org.rapla.entities.storage.internal.SimpleEntity) r).setId(
                "e0000000-1111-4222-8333-444455556666");
        facade.store(r);
    }
}
