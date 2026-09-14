/**
 * Shared block/chip styling logic for the block-based calendar renderers
 * (month grid, week grid, future day/program) — PRD 100 D2. The Swing analog is
 * {@code SwingRaplaBlock}/{@code RaplaBuilder}/{@code BlockColors}: block look
 * and color logic live ONCE and every view renders the same block. See
 * docs/architecture/calendar-rendering.md §1.
 */

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
