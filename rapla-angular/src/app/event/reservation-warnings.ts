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
    allocatable: { name: string | null } | null;
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
  allocatableName: string;
  otherEventName: string | null;
  when: string;
}[] {
  return warnings.flatMap((w) =>
    (w.conflicts ?? []).map((c) => ({
      allocatableName: c.allocatable?.name ?? 'Ressource',
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

/** German texts mirroring the `RaplaResources_de` keys the Swing dialog renders. */
const TEXTS: Record<WarningCode, (args: string[]) => string> = {
  NO_RESERVATION_NAME: () => 'Die Veranstaltung hat keinen Namen.',
  DUPLICATED_APPOINTMENTS: (a) => `Zwei Termine sind identisch${a[0] ? ` (${a[0]})` : ''}.`,
  NO_ALLOCATABLES_SELECTED: () => 'Sie haben keine Ressourcen/Personen ausgewählt.',
  NOT_IN_CALENDAR: (a) =>
    `${arg(a, 0, 'Die Veranstaltung')} erscheint nicht in der aktuellen Ansicht.`,
  CONFLICT: () => 'Die Veranstaltung erzeugt Konflikte.',
  REQUEST_PENDING: (a) => `${arg(a, 0, 'Eine Ressource')} ist nur auf Anfrage buchbar (Antrag).`,
  HOLIDAY_ON_APPOINTMENT: (a) => `Ein Termin liegt auf einem Feiertag${a[0] ? ` (${a[0]})` : ''}.`,
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
