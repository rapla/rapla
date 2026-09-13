package org.rapla.plugin.exchangeconnector.server;

import microsoft.exchange.webservices.data.core.enumeration.property.Sensitivity;
import microsoft.exchange.webservices.data.core.exception.http.HttpErrorException;
import org.junit.jupiter.api.Test;
import org.rapla.entities.User;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.plugin.exchangeconnector.server.exchange.AppointmentSynchronizer;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PRD 114 Phase 1 — the four production hotfix hunks of 2026-09-09, ported to spring-boot. */
class SynchronisationManagerHotfixTest {

    // hunk 1: only an account-level failure aborts the user's remaining tasks
    @Test
    void only401AbortsRemainingTasksOfTheUser() {
        assertTrue(SynchronisationManager.abortsRemainingTasksForUser(new HttpErrorException("unauthorized", 401)));
        assertFalse(SynchronisationManager.abortsRemainingTasksForUser(new HttpErrorException("forbidden", 403)));
        assertFalse(SynchronisationManager.abortsRemainingTasksForUser(new RuntimeException("Access is denied. Check credentials and try again.")));
        assertFalse(SynchronisationManager.abortsRemainingTasksForUser(new SocketTimeoutException("Read timed out")));
        assertFalse(SynchronisationManager.abortsRemainingTasksForUser(new IOException("Keine Verbindung")));
    }

    // hunk 2: paging advances by a whole page, not by one item
    @Test
    void pagingAdvancesByPageSize() {
        assertEquals(100, AppointmentSynchronizer.nextPageOffset(0, 100, 100));
        assertEquals(200, AppointmentSynchronizer.nextPageOffset(100, 100, 100));
        assertEquals(-1, AppointmentSynchronizer.nextPageOffset(200, 100, 37));
        assertEquals(-1, AppointmentSynchronizer.nextPageOffset(0, 100, 0));
    }

    // hunk 4: the sweep only enqueues tasks for the mailbox it just diffed
    @Test
    void sweepKeepsOnlyTasksOfTheDiffedResource() {
        SynchronizationTask own = task("a1", "r-own");
        SynchronizationTask coParticipant = task("a1", "r-other");
        Collection<SynchronizationTask> filtered = SynchronisationManager.tasksForResource(Arrays.asList(own, coParticipant), "r-own");
        assertEquals(List.of(own), filtered.stream().collect(Collectors.toList()));
    }

    // hunk 7 + 10: items the mailbox owner created (Outlook copy keeps the rapla marker) or marked private are not rapla's —
    // never update/delete them, never count them; items rapla created stay rapla's after owner edits/moves
    @Test
    void onlyNormalItemsCreatedBySyncAccountAreRaplasOwn() {
        assertTrue(AppointmentSynchronizer.ownedBySyncAccount("RaplaTermin", Sensitivity.Normal, "raplatermin@site-a.example.org"));
        assertTrue(AppointmentSynchronizer.ownedBySyncAccount("rapla-termin", Sensitivity.Normal, "rapla-termin@site-b.example.org"));
        assertTrue(AppointmentSynchronizer.ownedBySyncAccount("Raplatermin", null, "raplatermin@site-c.example.org"));   // edited/moved by owner: still rapla's
        assertTrue(AppointmentSynchronizer.ownedBySyncAccount("RaplaTermin VS", Sensitivity.Normal, "RaplaTermin.VS@example.org"));       // display name with site suffix
        assertTrue(AppointmentSynchronizer.ownedBySyncAccount("Rapla Termin HN", Sensitivity.Normal, "raplatermin.hn@site-d.example.org"));
        assertTrue(AppointmentSynchronizer.ownedBySyncAccount("raplatermin", Sensitivity.Normal, "raplatermin.hn@site-d.example.org"));  // short display name, suffixed login
        assertTrue(AppointmentSynchronizer.ownedBySyncAccount(null, null, "raplatermin@site-c.example.org"));            // legacy item without MAPI names
        assertFalse(AppointmentSynchronizer.ownedBySyncAccount("Burns, Prof. Dr. Monty", Sensitivity.Normal, "raplatermin@site-a.example.org"));  // copied by owner
        assertFalse(AppointmentSynchronizer.ownedBySyncAccount("Raplatermin", Sensitivity.Private, "raplatermin@site-c.example.org"));   // marked private by owner: Exchange refuses delegate writes
    }

    // hunk 11: an owner's private/copied item counts as up to date only if start, end and subject match what rapla would write
    @Test
    void foreignItemIsUpToDateOnlyWhenStartEndSubjectMatch() {
        java.util.Date s = new java.util.Date(1_000_000L), e = new java.util.Date(2_000_000L);
        assertTrue(AppointmentSynchronizer.sameStartEndSubject(s, e, "Vorlesung", new java.util.Date(1_000_000L), new java.util.Date(2_000_000L), "Vorlesung"));
        assertFalse(AppointmentSynchronizer.sameStartEndSubject(s, e, "Vorlesung", new java.util.Date(1_000_001L), e, "Vorlesung"));
        assertFalse(AppointmentSynchronizer.sameStartEndSubject(s, e, "Vorlesung", s, e, "Vorlesung (verschoben)"));
        assertFalse(AppointmentSynchronizer.sameStartEndSubject(null, e, "x", null, e, "x"));
    }

    // hunk 12: last writer wins — an own item the owner edited after rapla's last change is left alone (unless the task is a forced resync)
    @Test
    void ownerEditAfterRaplaChangeWinsUnlessForced() {
        java.util.Date raplaChanged = new java.util.Date(1_000_000L);
        assertTrue(AppointmentSynchronizer.ownerEditedAfterRapla("Burns, Prof. Dr. Monty", new java.util.Date(2_000_000L), raplaChanged, "raplatermin@site-a.example.org"));
        assertFalse(AppointmentSynchronizer.ownerEditedAfterRapla("Burns, Prof. Dr. Monty", new java.util.Date(500_000L), raplaChanged, "raplatermin@site-a.example.org"));   // rapla changed later: rapla wins
        assertFalse(AppointmentSynchronizer.ownerEditedAfterRapla("RaplaTermin VS", new java.util.Date(2_000_000L), raplaChanged, "RaplaTermin.VS@example.org"));                // site account itself
        assertFalse(AppointmentSynchronizer.ownerEditedAfterRapla("RaplaTermin", new java.util.Date(2_000_000L), raplaChanged, "raplatermin@site-a.example.org"));            // last modifier is rapla itself
        assertFalse(AppointmentSynchronizer.ownerEditedAfterRapla(null, null, raplaChanged, "raplatermin@site-a.example.org"));
    }

    // hunk 13: an Exchange item that ends before the sweep window is invisible to the sweep — never compared, never deleted
    @Test
    void sweepWindowNeverProducesDeletesForOldItems() {
        java.util.Date windowStart = new java.util.Date(10_000L);
        assertTrue(SynchronisationManager.outsideSweepWindow(false, new java.util.Date(9_999L), windowStart));
        assertFalse(SynchronisationManager.outsideSweepWindow(false, new java.util.Date(10_000L), windowStart));
        assertFalse(SynchronisationManager.outsideSweepWindow(false, null, windowStart));           // unknown end: treat as in window
        assertFalse(SynchronisationManager.outsideSweepWindow(false, new java.util.Date(1L), null)); // no window configured
        assertFalse(SynchronisationManager.outsideSweepWindow(true, new java.util.Date(1L), windowStart)); // recurring master: getEnd() is the FIRST occurrence, series may still run
        // delete loop passes recurring=false on purpose: a master whose first occurrence ended before the window is never deleted by the sweep
        assertTrue(SynchronisationManager.outsideSweepWindow(false, new java.util.Date(1L), windowStart));
    }

    // hunk 9: a mailbox whose task failed n times is skipped for the next n sweeps (capped), a success resets it
    @Test
    void failingMailboxIsSkippedForAsManySweepsAsItFailed() {
        SynchronisationManager.FailureBackoff b = new SynchronisationManager.FailureBackoff();
        assertFalse(b.skipThisSweep("r1"));
        b.failed("r1");                                   // 1st failure → skip 1 sweep
        assertTrue(b.skipThisSweep("r1"));
        assertFalse(b.skipThisSweep("r1"));               // retried in the sweep after
        b.failed("r1");                                   // 2nd failure → skip 2 sweeps
        assertTrue(b.skipThisSweep("r1"));
        assertTrue(b.skipThisSweep("r1"));
        assertFalse(b.skipThisSweep("r1"));
        b.succeeded("r1");                                // success resets
        b.failed("r1");
        assertTrue(b.skipThisSweep("r1"));
        assertFalse(b.skipThisSweep("r1"));
        for (int i = 0; i < 100; i++) b.failed("r2");     // capped at one day of hourly sweeps
        int skipped = 0; while (b.skipThisSweep("r2")) skipped++;
        assertEquals(24, skipped);
        assertFalse(b.skipThisSweep("other"));
    }

    // hunk 14: a weekly repeating with several weekdays is written as an Exchange weekly pattern with all of them, not only the start's day
    @Test
    void weeklyRepeatingExportsEveryWeekday() {
        assertEquals(List.of(microsoft.exchange.webservices.data.core.enumeration.property.time.DayOfTheWeek.Wednesday,
                        microsoft.exchange.webservices.data.core.enumeration.property.time.DayOfTheWeek.Thursday,
                        microsoft.exchange.webservices.data.core.enumeration.property.time.DayOfTheWeek.Friday),
                Arrays.asList(AppointmentSynchronizer.weeklyDays(new java.util.TreeSet<>(Arrays.asList(6, 4, 5)))));
        assertEquals(List.of(microsoft.exchange.webservices.data.core.enumeration.property.time.DayOfTheWeek.Sunday,
                        microsoft.exchange.webservices.data.core.enumeration.property.time.DayOfTheWeek.Saturday),
                Arrays.asList(AppointmentSynchronizer.weeklyDays(new java.util.TreeSet<>(Arrays.asList(1, 7)))));
    }

    private static SynchronizationTask task(String appointmentId, String resourceId) {
        return new SynchronizationTask("mb@example.org", new ReferenceInfo<>(appointmentId, Appointment.class), new ReferenceInfo<>("u1", User.class),
                new ReferenceInfo<>(resourceId, Allocatable.class), 0, null, null);
    }
}
