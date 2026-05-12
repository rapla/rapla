package org.rapla.rest.dto;

/**
 * Per-user settings DTO. Mirrors {@code SettingsController.UserSettings}.
 *
 * <p>Used by the Swing client's {@code WarningsOption}, {@code UserOption}
 * etc. when reading fresh values via {@code GET /settings/me} (instead of
 * the bulk-bootstrap preference cache), and by the Angular SPA for the
 * user-prefs panel.
 */
public record UserSettings(
        String language,
        boolean showConflictWarning,
        boolean showNotInCalendarWarning,
        boolean showAbortEditWarning,
        boolean showHolidayWarning,
        boolean showHolidayWarningSingleAppointment
)
{
}
