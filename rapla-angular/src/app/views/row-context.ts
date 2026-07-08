import type { ViewColumn } from '../graphql/graphql.service';

/**
 * PRD 094 D4 — typed row subject for generic view rows.
 *
 * View rows are `Record<string, unknown>` from arbitrary stored views: an
 * appointment block, a resource, a user, or a menu-less aggregate row. A view
 * that wants row actions selects a hidden subject field under a well-known
 * alias (`reservation @hidden { id canModify }`, `allocatable @hidden {...}`,
 * `user @hidden { id }`) — the Swing analog is `SelectionMenuContext` carrying
 * a typed RaplaObject. A row without a subject simply has no menu.
 */
export type EntityKind = 'reservation' | 'allocatable' | 'user';

export interface EntityRef {
  kind: EntityKind;
  id: string;
  canModify?: boolean;
}

export interface RowBlock {
  appointmentId: string | null;
  /** ISO LocalDateTime of the row's block occurrence (first date column). */
  start: string | null;
  isException: boolean;
}

export interface RowContext {
  /** THE subject of the row (what the row is about); null → no row menu.
   *  PRD 099 D3 (Swing `focused` rule): set only when EXACTLY ONE row is in
   *  the context — multi-select ⇒ null, providers dispatch on {@link subjects}. */
  primary: EntityRef | null;
  /** All refs the row(s) carry: primaries first, then secondary refs from
   *  Allocatable-typed cells (persons/resources already select `id`). */
  entities: EntityRef[];
  /** Per-row primary subjects in selection order (rows without a subject are
   *  skipped) — what multi-row actions iterate (PRD 099). Single row ⇒ [primary]. */
  subjects: EntityRef[];
  /** Block identity for block-rooted rows (`appointmentId @hidden` +
   *  `isException @hidden` in the view) — drives the delete-scope flow.
   *  Meaningful for single-row contexts only. */
  block: RowBlock;
  /** The selected rows (PRD 099); single-row contexts carry exactly one. */
  rows: Record<string, unknown>[];
  viewName: string;
}

/** Subject aliases in priority order — first match wins. */
const SUBJECT_ALIASES: readonly { alias: string; kind: EntityKind }[] = [
  { alias: 'reservation', kind: 'reservation' },
  { alias: 'allocatable', kind: 'allocatable' },
  { alias: 'user', kind: 'user' },
];

function asRef(value: unknown, kind: EntityKind): EntityRef | null {
  if (typeof value !== 'object' || value === null) return null;
  const obj = value as Record<string, unknown>;
  if (typeof obj['id'] !== 'string') return null;
  return {
    kind,
    id: obj['id'],
    canModify: typeof obj['canModify'] === 'boolean' ? obj['canModify'] : undefined,
  };
}

export function extractRowContext(
  row: Record<string, unknown>,
  viewName: string,
  columns: ViewColumn[] = [],
): RowContext {
  let primary: EntityRef | null = null;
  for (const { alias, kind } of SUBJECT_ALIASES) {
    primary = asRef(row[alias], kind);
    if (primary) break;
  }
  // Scalar fallback for views rooted directly at the entity (no self-reference
  // in GraphQL): `reservationId: id @hidden` + `canModify @hidden`.
  if (!primary && typeof row['reservationId'] === 'string') {
    primary = {
      kind: 'reservation',
      id: row['reservationId'],
      canModify: typeof row['canModify'] === 'boolean' ? row['canModify'] : undefined,
    };
  }

  const entities: EntityRef[] = primary ? [primary] : [];
  for (const col of columns) {
    if (col.type !== 'Allocatable') continue;
    const cell = row[col.alias];
    if (!Array.isArray(cell)) continue;
    for (const item of cell) {
      const ref = asRef(item, 'allocatable');
      if (ref) entities.push(ref);
    }
  }

  const dateAlias =
    columns.find((c) => c.type === 'Date' || c.type === 'LocalDateTime')?.alias ?? 'start';
  const startValue = row[dateAlias];
  // PRD 095 Phase 3b — the navigable `appointment @hidden { id … }` object is the
  // preferred block-identity source; the flat `appointmentId @hidden` scalar stays
  // supported for older stored views.
  const appointment = row['appointment'] as Record<string, unknown> | null | undefined;
  const appointmentId =
    typeof appointment?.['id'] === 'string'
      ? appointment['id']
      : typeof row['appointmentId'] === 'string'
        ? row['appointmentId']
        : null;
  const block: RowBlock = {
    appointmentId,
    start: typeof startValue === 'string' ? startValue : null,
    isException: row['isException'] === true,
  };
  return { primary, entities, subjects: primary ? [primary] : [], block, rows: [row], viewName };
}

/**
 * PRD 099 — context over a multi-row selection. One row delegates to
 * {@link extractRowContext}; N rows aggregate the per-row subjects and
 * secondary refs with `primary = null` (D3 — single-row items disappear,
 * multi-row items dispatch on {@link RowContext.subjects}).
 */
export function extractSelectionContext(
  rows: Record<string, unknown>[],
  viewName: string,
  columns: ViewColumn[] = [],
): RowContext {
  if (rows.length === 1) return extractRowContext(rows[0], viewName, columns);
  const contexts = rows.map((r) => extractRowContext(r, viewName, columns));
  return {
    primary: null,
    entities: contexts.flatMap((c) => c.entities),
    subjects: contexts.flatMap((c) => c.subjects),
    block: { appointmentId: null, start: null, isException: false },
    rows,
    viewName,
  };
}
