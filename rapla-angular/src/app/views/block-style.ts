/**
 * Shared block/chip styling logic for the block-based calendar renderers
 * (month grid, week grid, future day/program) — PRD 100 D2. The Swing analog is
 * {@code SwingRaplaBlock}/{@code RaplaBuilder}/{@code BlockColors}: block look
 * and color logic live ONCE and every view renders the same block. See
 * docs/architecture/calendar-rendering.md §1.
 */

import type { FilterEntry } from '../state/filter-store';

type Row = Record<string, unknown>;

/**
 * Chip text is ALWAYS black — Swing parity ({@code SwingRaplaBlock.FOREGROUND_COLOR
 * = Color.black}, PRD 100 D1, resolves PRD 095 OQ2). No luminance-based flip:
 * deployments choose block colors that read with black text, and the same event
 * must render identically in Swing, exported HTML, and the SPA.
 */
export const CHIP_TEXT_COLOR = '#000';

/** The §12-gated effective block color from the wire; null → neutral chip. */
export function chipColor(row: Row): string | null {
  const c = row['color'];
  return typeof c === 'string' && c ? c : null;
}

/** Server-formatted time range (`times`), falling back to the start's HH:mm. */
export function chipTime(row: Row): string {
  const t = row['times'];
  if (typeof t === 'string' && t) return t;
  return String(row['start'] ?? '').slice(11, 16);
}

export function chipName(row: Row): string {
  return String(row['name'] ?? '');
}

/** PRD 128 Phase 1b — a block of a reservation the caller cannot read: explicit `reservation: null`, no content. */
export function isAnonymousRow(row: Row): boolean {
  return row['reservation'] === null;
}

/**
 * Drag-move gate (PRD 095 D6, client UX only — the server re-checks in
 * `moveReservations`): the mutation shifts ALL appointments of the reservation,
 * so only a `canModify`, single-appointment, non-repeating block may be dragged.
 * Fail-closed: custom views that don't select the hidden facts get no drag
 * affordance. Swing analog: `RaplaBlock.isMovable()`.
 */
export function isMovableRow(row: Row): boolean {
  const reservation = row['reservation'] as Record<string, unknown> | null | undefined;
  const appointment = row['appointment'] as Record<string, unknown> | null | undefined;
  return (
    reservation?.['canModify'] === true &&
    reservation?.['appointmentCount'] === 1 &&
    appointment != null &&
    'repeating' in appointment &&
    appointment['repeating'] === null
  );
}

/**
 * Wider drag/resize gate (PRD 101 Phase 5): a `canModify` block with a
 * resolvable appointment id that is NOT an already-skipped exception occurrence
 * (Swing parity — exception blocks aren't draggable). Repeating and
 * multi-appointment blocks ARE draggable here; the drop resolves EVENT/SERIE/
 * SINGLE server-side via a scope dialog (see move-scope.ts). Fail-closed: custom
 * views that don't select the hidden `reservation`/`appointment` facts get no
 * affordance. Swing analog: `RaplaBlock.isMovable()` / `isEditable()`.
 */
export function isDraggableRow(row: Row): boolean {
  const reservation = row['reservation'] as Record<string, unknown> | null | undefined;
  const appointment = row['appointment'] as Record<string, unknown> | null | undefined;
  return (
    reservation?.['canModify'] === true &&
    appointment != null &&
    typeof appointment['id'] === 'string' &&
    row['isException'] !== true
  );
}

/**
 * Base chip look shared by all block renderers. Positioning (absolute, top/left/
 * width/height) stays with each grid — the *look* is view-independent, the
 * *layout* is not.
 */
export const CHIP_BASE_CSS = `
  .chip {
    box-sizing: border-box;
    border-radius: 4px;
    font-size: 0.72rem;
    color: ${CHIP_TEXT_COLOR};
    cursor: pointer;
    user-select: none;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .chip.neutral {
    background: #e4e6ee;
  }
  .chip .t {
    opacity: 0.7;
    font-variant-numeric: tabular-nums;
  }
`;

function appointmentIdOf(row: Row): string | null {
  const appointment = row['appointment'] as Row | null | undefined;
  if (typeof appointment?.['id'] === 'string') return appointment['id'];
  return typeof row['appointmentId'] === 'string' ? row['appointmentId'] : null;
}

function reservationIdOf(row: Row): string | null {
  const reservation = row['reservation'] as Row | null | undefined;
  if (typeof reservation?.['id'] === 'string') return reservation['id'];
  return typeof row['reservationId'] === 'string' ? row['reservationId'] : null;
}

/**
 * PRD 128 D1 — with a conflict or request chip set, every block outside the focus is drawn pale. A conflict keeps the
 * blocks of either side that overlap a block of the other side (Swing `RaplaBuilder` `overlapsBlock` over
 * `ConflictImpl.getMap`); an unreadable side arrives as an anonymous block with its flat ids (D6). A request keeps the
 * blocks of its reservation (the chip id `REQUEST;<resource>;<reservation>`). No focus chip → nothing pale.
 */
export function paleRows(rows: readonly Row[], chips: readonly FilterEntry[]): ReadonlySet<Row> {
  const focusChips = chips.filter((c) => c.kind === 'conflict' || c.kind === 'request');
  if (focusChips.length === 0) return new Set();
  const requested = new Set(
    focusChips.filter((c) => c.kind === 'request').map((c) => c.id.split(';')[2]),
  );
  const partners = new Map<string, Set<string>>();
  const link = (a: string, b: string) => partners.set(a, (partners.get(a) ?? new Set()).add(b));
  for (const c of focusChips) {
    if (c.kind !== 'conflict') continue;
    const [, , a, b] = c.id.split(';');
    link(a, b);
    link(b, a);
  }
  const byAppointment = new Map<string, Row[]>();
  for (const r of rows) {
    const id = appointmentIdOf(r);
    if (id) byAppointment.set(id, [...(byAppointment.get(id) ?? []), r]);
  }
  const overlaps = (r: Row, o: Row) =>
    String(r['start']) < String(o['end']) && String(o['start']) < String(r['end']);
  const inFocus = (r: Row) => {
    const reservationId = reservationIdOf(r);
    if (reservationId && requested.has(reservationId)) return true;
    const id = appointmentIdOf(r);
    return [...(partners.get(id ?? '') ?? [])].some((p) =>
      (byAppointment.get(p) ?? []).some((o) => overlaps(r, o)),
    );
  };
  return new Set(rows.filter((r) => !inFocus(r)));
}
