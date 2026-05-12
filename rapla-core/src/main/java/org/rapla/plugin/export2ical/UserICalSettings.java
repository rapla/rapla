package org.rapla.plugin.export2ical;

/**
 * Per-user iCal export overrides exposed at {@code GET /ical/config/user}.
 *
 * <p>Nullable fields signal "no user override — fall back to the system
 * default from {@link ICalConfigService#getUserDefaultConfig()}". The Swing
 * panel uses these to decide whether the "use user-defined interval"
 * checkbox is on and which values to pre-fill.
 *
 * <p>Replaces the per-key preference reads
 * ({@code preferences.getEntryAsInteger(PREF_BEFORE_DAYS, ...)},
 *  {@code ...getEntryAsBoolean(EXPORT_ATTENDEES_PREFERENCE, ...)}, etc.)
 * in {@code Export2iCalUserOption}.
 */
public record UserICalSettings(
        Integer daysBefore,
        Integer daysAfter,
        Boolean exportAttendees,
        String participationStatus
) {}
