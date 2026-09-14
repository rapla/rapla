package org.rapla.entities.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.rapla.entities.User;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.test.util.FacadeTestSupport;

/**
 * PRD 074 Baustein 9 — regression: {@code AppointmentBlock.name} is block/appointment-aware and
 * honours the per-appointment note override (appointmentnote plugin), so two blocks of the SAME
 * reservation can render different names, and a noted block's name diverges from the bare
 * reservation name.
 *
 * <p>Verified live against dhbw data (reservation "Feedback Studiengangsleitung": only the noted
 * occurrence rendered "… &lt;Mit Klausureinsicht&gt;"); this locks the behaviour with a synthetic,
 * §17-clean fixture (dummy name + note) so a refactor can't silently reopen it.
 */
class AppointmentBlockNoteNameTest extends FacadeTestSupport
{
    private static final Locale LOCALE = Locale.ENGLISH;

    @Test
    void blockNameAppliesPerAppointmentNoteOverride() throws Exception
    {
        User admin = adminUser();

        // 1. Give the event type a note-aware nameformat: "{name} <note>" (note via the
        //    appointmentnote plugin function, evaluated per block/appointment).
        DynamicType[] eventTypes =
                facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION);
        assertTrue(eventTypes.length > 0, "fixture must have a reservation type");
        DynamicType editableType = facade.edit(eventTypes[0]);
        editableType.setAnnotation(DynamicTypeAnnotations.KEY_NAME_FORMAT,
                "{name} {format(\"<%s>\",appointment:note())}");
        facade.store(editableType);

        DynamicType eventType = facade.getDynamicType(editableType.getKey());
        assertNotNull(eventType.getAttribute("name"), "event type needs a 'name' attribute for this test");

        // 2. One reservation, two single appointments; note only on the second.
        Classification c = eventType.newClassification();
        c.setValue("name", "Lecture");
        org.rapla.entities.domain.Reservation r = facade.newReservation(c, admin);
        Appointment plain = facade.newAppointmentWithUser(
                LocalDateTime.parse("2030-04-03T16:15"), LocalDateTime.parse("2030-04-03T17:45"), admin);
        Appointment noted = facade.newAppointmentWithUser(
                LocalDateTime.parse("2030-06-03T13:30"), LocalDateTime.parse("2030-06-03T15:00"), admin);
        r.addAppointment(plain);
        r.addAppointment(noted);
        // appointment-note override on the second occurrence only (reservation annotation).
        r.setAnnotation("appointment_note_" + noted.getId(), "Klausureinsicht");
        facade.store(r);

        // 3. Block-aware names per appointment (r's classification points at the updated type).
        String plainBlockName = blockName(r, "2030-04-03");
        String notedBlockName = blockName(r, "2030-06-03");
        String reservationName = r.getName(LOCALE);

        assertEquals("Lecture", reservationName.trim(),
                "reservation-level name has no per-appointment note");
        assertFalse(plainBlockName.contains("<"),
                () -> "block without a note must not carry a <…> suffix; got '" + plainBlockName + "'");
        assertTrue(notedBlockName.contains("<Klausureinsicht>"),
                () -> "noted block name must carry the note override; got '" + notedBlockName + "'");
        assertFalse(notedBlockName.equals(plainBlockName),
                "two blocks of the same reservation diverge by the per-appointment note");
        assertFalse(notedBlockName.trim().equals(reservationName.trim()),
                "noted block name diverges from the reservation name (Baustein 9)");
    }

    /** Name of the single block of {@code stored} whose start falls on {@code isoDate} (yyyy-MM-dd). */
    private String blockName(org.rapla.entities.domain.Reservation stored, String isoDate)
    {
        LocalDateTime from = LocalDateTime.parse(isoDate + "T00:00");
        LocalDateTime to = from.plusDays(1);
        List<AppointmentBlock> blocks = new ArrayList<>();
        for (Appointment a : stored.getAppointments())
        {
            a.createBlocks(from, to, blocks);
        }
        assertEquals(1, blocks.size(), () -> "exactly one block expected on " + isoDate);
        return NameFormatUtil.getName(blocks.get(0), LOCALE);
    }

    private User adminUser() throws Exception
    {
        for (User u : facade.getUsers())
        {
            if (u.isAdmin()) return u;
        }
        throw new IllegalStateException("no admin user in fixture");
    }
}
