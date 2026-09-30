import { t } from '../i18n/i18n.service';

/**
 * PRD 105 — the pre-save findings, as the SPA consumes them.
 *
 * The RULES live on the server (one implementation for Swing and the SPA); this file only turns a
 * code into text and decides what a code does to the save button. The severity is NOT re-decided
 * here — it arrives with the warning, so the same event is creatable, or not, in both clients (D7).
 */

export type WarningCode =
  | 'NO_RESERVATION_NAME'
  | 'DUPLICATED_APPOINTMENTS'
  | 'NO_ALLOCATABLES_SELECTED'
  | 'NOT_IN_CALENDAR'
  | 'CONFLICT'
  | 'REQUEST_PENDING'
  | 'HOLIDAY_ON_APPOINTMENT';

export interface ReservationWarning {
  code: WarningCode;
  args: string[];
  severity: 'BLOCKING' | 'CONFIRMABLE';
  /** PRD 105 — evidence carried by the finding itself; populated for CONFLICT, empty otherwise. */
  conflicts?: {
    resource: { name: string | null } | null;
    reservation2: { name: string | null } | null;
    startDate: string;
  }[];
}

/**
 * Flatten the evidence of all CONFLICT findings for the dialog. It rides WITH the warning so every
 * write path shows the same detail — the first attempt fetched it separately in the sheet path only,
 * and a drag therefore showed a bare "erzeugt Konflikte".
 */
export function conflictDetails(warnings: ReservationWarning[]): {
  resourceName: string;
  otherEventName: string | null;
  when: string;
}[] {
  return warnings.flatMap((w) =>
    (w.conflicts ?? []).map((c) => ({
      resourceName: c.resource?.name ?? t('resource'),
      otherEventName: c.reservation2?.name ?? null,
      when: c.startDate,
    })),
  );
}

/** An argument the server left empty is a missing argument — `??` alone does not catch `""`. */
function arg(args: string[], index: number, fallback: string): string {
  const value = args[index];
  return value === undefined || value === '' ? fallback : value;
}

/** Texts mirroring the `RaplaResources` texts the Swing dialog renders. */
const TEXTS: Record<WarningCode, (args: string[]) => string> = {
  NO_RESERVATION_NAME: () => t('event_warning_no_name'),
  DUPLICATED_APPOINTMENTS: (a) =>
    a[0] ? t('event_warning_duplicated_detail', a[0]) : t('event_warning_duplicated'),
  NO_ALLOCATABLES_SELECTED: () => t('event_warning_no_allocatables'),
  NOT_IN_CALENDAR: (a) =>
    t('event_warning_not_in_calendar', arg(a, 0, t('event_warning_fallback_event'))),
  CONFLICT: () => t('event_warning_conflict'),
  REQUEST_PENDING: (a) =>
    t('event_warning_request_pending', arg(a, 0, t('event_warning_fallback_resource'))),
  HOLIDAY_ON_APPOINTMENT: (a) =>
    a[0] ? t('event_warning_holiday_detail', a[0]) : t('event_warning_holiday'),
};

export function warningText(warning: ReservationWarning): string {
  const render = TEXTS[warning.code];
  return render ? render(warning.args ?? []) : warning.code;
}

/** True when at least one finding forbids saving — the user cannot confirm their way past it. */
export function isBlocked(warnings: ReservationWarning[]): boolean {
  return warnings.some((w) => w.severity === 'BLOCKING');
}

/** What the save flow does next: save silently, ask, or refuse. */
export function saveGate(warnings: ReservationWarning[]): 'save' | 'confirm' | 'blocked' {
  if (warnings.length === 0) return 'save';
  return isBlocked(warnings) ? 'blocked' : 'confirm';
}
