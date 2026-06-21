import { ViewMeta } from '../graphql/graphql.service';

/**
 * PRD 078 — the dhbw {@code appointments} table view (worked query 2 in PRD 074):
 * one row per recurrence block, columns Name / Beginn / Ende / Kurs / Person /
 * Raum / Dauer. The per-column {@code allocatables(filter:)} splits are the
 * INLINE annotation filters (PRD 074 §"Inputs" — not variables); the only
 * variable is {@code $filter} (the CalendarModel selection, here just the date
 * window).
 *
 * The server data layer for every field below is shipped (PRD 074, 2026-06-21).
 */
export const APPOINTMENTS_QUERY = `
query Termine($filter: ReservationFilter!) @view(title: "Termine KW") {
  appointmentBlocks(filter: $filter) {
    name      @column(header: "Veranstaltung", order: 0)
    start     @column(header: "Beginn",        order: 1)
    duration  @column(header: "Dauer",         order: 2)
    persons:   allocatables(filter: { isPersonEq: true })    @join(separator: "; ") @column(header: "Dozent", order: 3) { name }
    resources: allocatables(filter: { typeKeyIn: ["room"] }) @join(separator: ", ") @column(header: "Raum",   order: 4) { name }
  }
}`.trim();

/**
 * GraphQL response rows are GENERIC — the renderer keys cells by the
 * {@code extensions.view} column aliases, not by a fixed shape. A typed
 * per-view interface would only re-encode the (deployment-specific) query, so
 * rows stay {@code Record<string, unknown>}.
 */
export interface AppointmentsData {
  appointmentBlocks: Record<string, unknown>[];
}
export type AppointmentBlockRow = Record<string, unknown>;

/**
 * Transitional render-meta. Used ONLY until the server emits
 * {@code extensions.view} (PRD 074's {@code @view} directive). The component
 * prefers {@code response.extensions.view} and falls back to this — so the
 * day the server ships the meta, the table switches to it with no code change.
 * Column order / headers / list-join mirror what the {@code @view} directive
 * will resolve from the query's field order + aliases.
 */
export const APPOINTMENTS_VIEW_FALLBACK: ViewMeta = {
  key: 'Termine',
  title: 'Termine KW',
  columns: [
    { alias: 'name', header: 'Veranstaltung', type: 'String', order: 0 },
    { alias: 'start', header: 'Beginn', type: 'LocalDateTime', order: 1 },
    { alias: 'duration', header: 'Dauer', type: 'String', order: 2 },
    { alias: 'persons', header: 'Dozent', type: 'Allocatable', order: 3, join: '; ' },
    { alias: 'resources', header: 'Raum', type: 'Allocatable', order: 4, join: ', ' },
  ],
};

/**
 * Default date window — SPA-computed for the first cut (today ±1 year, matching
 * the legacy table; wide enough to surface a teacher's semester). The
 * server-merge-vs-SPA pre-fill decision is PRD 078's open question; this is a
 * placeholder default, not the answer. Emits {@code LocalDateTime} strings
 * ({@code YYYY-MM-DDT00:00:00}, no zone) as the {@code ReservationFilter!}
 * schema requires.
 */
export function defaultWindow(now: Date = new Date()): { from: string; to: string } {
  const from = new Date(now);
  from.setFullYear(now.getFullYear() - 1);
  const to = new Date(now);
  to.setFullYear(now.getFullYear() + 1);
  const fmt = (d: Date) => `${d.toISOString().slice(0, 10)}T00:00:00`;
  return { from: fmt(from), to: fmt(to) };
}
