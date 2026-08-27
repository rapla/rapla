package org.rapla.client.edit.check;

import java.util.List;
import java.util.Objects;

/**
 * Pure-Java warning produced by a pre-save reservation check. Each
 * warning carries a stable {@link Code} (for view-side dispatch — Swing
 * dialog message, Angular toast, REST response) plus optional argument
 * strings to interpolate into the i18n template.
 * <p>
 * Carved out of {@code rapla-client/.../internal/check/} so Angular can
 * call the same decision functions and render its own dialog. The Swing
 * tier consumes these records and pushes the rendered strings into the
 * existing {@code CheckView} dialog.
 *
 * <p>i18n contract: each {@link Code} corresponds to one bundle key
 * (listed in the enum). The {@link #args} are positional format args
 * for {@code MessageFormat} / {@code i18n.format(key, args...)}.
 */
public record ReservationWarning(Code code, List<String> args)
{
    public ReservationWarning
    {
        Objects.requireNonNull(code);
        args = args == null ? List.of() : List.copyOf(args);
    }

    public static ReservationWarning of(Code code, String... args)
    {
        return new ReservationWarning(code, args == null ? List.of() : List.of(args));
    }

    /**
     * PRD 105 D7 — how a client must treat the warning. Carried by the CODE so both tiers agree:
     * a warning that stops the save in one client and shrugs in the other would make the same
     * event creatable or not depending on which UI the user happens to hold.
     */
    public enum Severity
    {
        /** The save cannot proceed (Swing: {@code EventCheck} returns false). */
        BLOCKING,
        /** The user may save anyway (Swing: continue/cancel dialog). */
        CONFIRMABLE
    }

    /** Stable codes — the view layer dispatches on these. */
    public enum Code
    {
        /** Reservation has no name and is not a template. i18n: {@code error.no_reservation_name}. */
        NO_RESERVATION_NAME(Severity.BLOCKING),
        /** Two appointments in the same reservation are identical. i18n: {@code warning.duplicated_appointments}, arg0 = short summary. */
        DUPLICATED_APPOINTMENTS(Severity.CONFIRMABLE),
        /** Reservation has zero allocatables (and is not a template). i18n: {@code warning.no_allocatables_selected}. */
        NO_ALLOCATABLES_SELECTED(Severity.CONFIRMABLE),
        /** Reservation will not appear in the currently visible calendar. i18n: {@code warning.not_in_calendar}, arg0 = reservation name. */
        NOT_IN_CALENDAR(Severity.CONFIRMABLE),
        /** Reservation creates conflicts the user can't ignore. i18n: {@code warning.conflict}. */
        CONFLICT(Severity.CONFIRMABLE),
        /** At least one allocatable is in REQUEST mode. i18n: {@code warning.request_pending}. */
        REQUEST_PENDING(Severity.CONFIRMABLE),
        /** Appointment falls on a configured holiday. i18n: {@code warning.holiday}, arg0 = appointment summary. */
        HOLIDAY_ON_APPOINTMENT(Severity.CONFIRMABLE);

        private final Severity severity;

        Code(Severity severity)
        {
            this.severity = severity;
        }

        /** PRD 105 D7 — read off Swing's four {@code EventCheck} impls; see {@code ReservationWarningSeverityTest}. */
        public Severity severity()
        {
            return severity;
        }
    }
}
