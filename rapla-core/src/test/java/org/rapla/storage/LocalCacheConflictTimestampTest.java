package org.rapla.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rapla.components.util.DateTools;
import org.rapla.facade.Conflict;
import org.rapla.facade.internal.ConflictImpl;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * WP25 — a disabled conflict loaded without lastChanged (the DB load nulls a future stamp) falls back to "now";
 * that "now" is stored again (ConflictStorage.insertAll) and sent to clients, so it must be the UTC wall-clock.
 */
class LocalCacheConflictTimestampTest
{
    private TimeZone originalZone;

    @BeforeEach
    void pinZone()
    {
        originalZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"));
    }

    @AfterEach
    void restoreZone()
    {
        TimeZone.setDefault(originalZone);
    }

    @Test
    void conflictWithoutLastChangedFallsBackToTheUtcNow() throws Exception
    {
        LocalCache cache = new LocalCache(new PermissionController(Collections.emptySet(), null));
        ConflictImpl conflict = new ConflictImpl("CONFLICT;alloc-1;app-1;app-2", DateTools.toLocalDateTime(System.currentTimeMillis()), null);
        conflict.setAppointment1Enabled(false);
        cache.put(conflict);

        Conflict disabled = cache.getDisabledConflicts().iterator().next();
        Conflict filled = cache.fillConflictDisableInformation(null, conflict);
        LocalDateTime utcNow = DateTools.toLocalDateTime(System.currentTimeMillis());
        assertFalse(disabled.getLastChanged().isAfter(utcNow), "getDisabledConflicts: " + disabled.getLastChanged() + " after UTC now " + utcNow);
        assertFalse(filled.getLastChanged().isAfter(utcNow), "fillConflictDisableInformation: " + filled.getLastChanged() + " after UTC now " + utcNow);
    }
}
