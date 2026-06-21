import { ViewMeta } from '../graphql/graphql.service';

/**
 * The "Wochenansicht" view definition (PRD 074 declarative @view query). One
 * row per recurrence block in the window; columns Von / Bis / Zeit / Titel /
 * Personen / Nicht-Personen. The per-column {@code allocatables(filter:)} splits
 * are INLINE annotation filters; variables are {@code $filter} (the date window
 * + optional {@code allocatableMatching} selection), {@code $sort}, {@code $offset}.
 *
 * The non-person split uses the GENERIC {@code isPersonEq: false} — no
 * deployment-specific type key — so it works on any rapla store. Hidden fields
 * ({@code durationMinutes}, {@code isException}, {@code reservation}) are fetched
 * for later weekday-grouping / edit-gating, not rendered as columns.
 *
 * NOTE (verified against the live server 2026-06-21): per PRD 074, EVERY selected
 * top-level field becomes a column unless it carries {@code @hidden} ITSELF —
 * {@code @hidden} on a nested sub-field does NOT hide the parent. So {@code
 * reservation} must be {@code reservation @hidden { … }}; without it the renderer
 * shows a junk "reservation" column. The sub-fields {@code id}/{@code canModify}
 * are never top-level columns, so they need no directive.
 */
export const WEEK_VIEW_QUERY = `
query Wochenansicht(
  $filter: ReservationFilter!,
  $sort:   [BlockSort!] = [{ field: START, dir: ASC }],
  $offset: Int = 0
) @view(title: "Wochenansicht") {
  appointmentBlocks(filter: $filter, sort: $sort, offset: $offset) {
    start  @column(header: "Von",   order: 1)
    end    @column(header: "Bis",   order: 2)
    times  @column(header: "Zeit",  order: 3)
    name   @column(header: "Titel", order: 4)

    durationMinutes @hidden
    isException     @hidden
    reservation @hidden { id  canModify }

    personen: allocatables(filter: { isPersonEq: true })
      @join(separator: ", ") @column(header: "Personen", order: 5) {
      id  name  isLocation
    }
    nichtPersonen: allocatables(filter: { isPersonEq: false })
      @join(separator: ", ") @column(header: "Nicht-Personen", order: 6) {
      id  name  isLocation
    }
  }
}`.trim();

/**
 * Transitional render-meta — used ONLY until the server emits
 * {@code extensions.view} for this query. The component prefers
 * {@code response.extensions.view} and falls back to this; column order /
 * headers / list-join mirror what the {@code @view} directive resolves.
 */
export const WEEK_VIEW_FALLBACK: ViewMeta = {
  key: 'Wochenansicht',
  title: 'Wochenansicht',
  columns: [
    { alias: 'start', header: 'Von', type: 'LocalDateTime', order: 1 },
    { alias: 'end', header: 'Bis', type: 'LocalDateTime', order: 2 },
    { alias: 'times', header: 'Zeit', type: 'String', order: 3 },
    { alias: 'name', header: 'Titel', type: 'String', order: 4 },
    { alias: 'personen', header: 'Personen', type: 'Allocatable', order: 5, join: ', ' },
    { alias: 'nichtPersonen', header: 'Nicht-Personen', type: 'Allocatable', order: 6, join: ', ' },
  ],
};

/**
 * The Monday-to-next-Monday window containing {@code now} (7 days, upper bound
 * exclusive — matching the example variables 2026-06-15 → 2026-06-22). Emits
 * zoneless {@code LocalDateTime} strings as {@code ReservationFilter!} requires.
 * Computed in UTC for determinism (same approach as {@code defaultWindow}).
 */
export function weekWindow(now: Date = new Date()): { from: string; to: string } {
  const day = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()));
  const mondayOffset = (day.getUTCDay() + 6) % 7; // Mon=0 … Sun=6
  const monday = new Date(day);
  monday.setUTCDate(day.getUTCDate() - mondayOffset);
  const nextMonday = new Date(monday);
  nextMonday.setUTCDate(monday.getUTCDate() + 7);
  const fmt = (d: Date) => `${d.toISOString().slice(0, 10)}T00:00:00`;
  return { from: fmt(monday), to: fmt(nextMonday) };
}
