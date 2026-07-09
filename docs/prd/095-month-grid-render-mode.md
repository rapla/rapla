# PRD 095 — Month-grid render mode (SPA) + §12-gated block color

**Status:** in progress — 2026-07-07. Phases 1+2 SHIPPED (color field + leak test,
builtin `renderModes: [table, month]`, month-chunks module, `MonthGridComponent`
spanning bars, control-strip month nav, browser-verified end-to-end). Phase 3
drag-CREATE shipped (day-range selection → event sheet prefilled via
`rangeScopedDraft`); drag-MOVE still open. OQ1 resolved: **spanning bars**
(EventCalendar/Google model — one chunk per week row, prev-chain stacking +
long-chunk push-down; `month-chunks.ts`, MIT-attributed, see
`LICENSE_MIT_EVENTCALENDAR`). Per-day chip mode NOT built (bars only).
**Related:** PRD 077 (calendar-model GraphQL — owns week/month render-modes on paper;
this PRD ships the month slice and closes its OQ5), PRD 078 (SPA view renderer —
`ViewHostComponent` this grid mounts into), PRD 074 (`@view` / `extensions.view`
contract — unchanged by this PRD), PRD 094 (main-view actions & popups — click
actions on grid chips land there; this PRD only navigates to the event sheet)

## Abstract

Give the SPA a real **month calendar grid** (6×7, Monday-first, day cells with event
chips) as a render mode of the existing builtin **Termine** view (`rapla_appointments`)
— no new view, no new render-meta schema. The only server addition is a **§12-gated
`color: String` field on `AppointmentBlock`** so chips can tint by the effective
reservation/resource color; the grid itself is a pure client renderer over the same
flat block rows the table already consumes.

## Implementation

Design outcome of the 2026-07-07 dialog (session context; decisions below):

**References (PRD 032 §Calendar view decision, 2026-07-07):** own implementation, no
calendar library. Primary reference = rapla's in-house logic (Swing
`DraggingHandler`/`SelectionHandler` for interaction semantics, `HTMLMonthViewPage`
for month-grid structure, `BlockColors` for colors); secondary = EventCalendar source
(MIT-attributed per the 032 rule) for browser pointer patterns and grid CSS.

**Server (rapla-app):**
- `AppointmentBlock.color: String` — the single *effective* CSS color of the block,
  **delegating to the existing `BlockColors` helper**
  (`rapla-core/plugin/calendarview/` — the PRD 030 Phase 4 survivor; do not
  reimplement color resolution).
  **§12 rule:** if any color-contributing entity is not readable by the caller, the
  field resolves to **null** (chip renders neutral) — the block itself already passed
  the §12 output filter, so we null the color rather than drop the block (deliberate
  deviation from PRD 077's sketch, recorded there).
- Builtin view text in `ViewCatalogService.BUILTIN_VIEWS`: `rapla_appointments` gets
  `@view(... renderModes: [table, month])` and a `color @hidden` selection. `@hidden`
  keeps it out of the table columns — "invisible info" carried for the grid only.

**SPA (rapla-angular):**
- `MonthGridComponent` (new, `src/app/views/`) — mounted by `ViewHostComponent` when
  `viewState.renderMode() === 'month'` (the table/grouped branch stays for
  `table`/`week`). Buckets `displayRows()` into day cells by the **date part** of
  `start`; renders week rows Mo–So; adjacent-month fill days greyed; per-cell overflow
  cap with "+N weitere"; chip = time + name, background from `color` (null → neutral).
  Chip click navigates via the (already selected, hidden) `reservation.id` to the
  event sheet — richer actions are PRD 094.
- **Month window derivation:** per PRD 077's date model, the store keeps ONE anchor
  (the existing `window`); per-render-mode windows are ephemeral client state. In
  month mode the query effect derives grid-Monday (on/before the 1st of the anchor's
  month) → grid-Sunday (on/after the last day) and queries that range — the persisted
  window/anchor is not rewritten by the derivation.
- `view-control-strip` month branch: `◀ Juni 2026 ▶` stepping whole months (alongside
  the week-nav and table date-picker branches); "Heute" jumps to the current month.

## Goal

- GraphiQL: `appointmentBlocks { color }` returns the Swing-equivalent block color for
  an admin, and null for a caller who cannot read the color-contributing resource.
- SPA: Termine view shows a "Monat" toggle; selecting it renders a 6×7 grid whose day
  cells contain colored chips; ◀/▶ steps months; clicking a chip opens the event sheet.
- Tier-3 leak test proves the color field leaks nothing past read scope.

## Scope

### In scope
- `AppointmentBlock.color` resolver + schema + §12 gating + leak test.
- `renderModes: [table, month]` + `color @hidden` on the builtin `rapla_appointments`.
- `MonthGridComponent` + mount in `ViewHostComponent` + control-strip month nav +
  month-window derivation.

### In scope (Phase 3 — proposed)
- Drag-move of a chip to another day cell for the safe subset (single-appointment,
  non-repeating, `canModify` reservations) via the existing `moveReservations`
  mutation + PRD 094-style undo toast.

### Out of scope
- A `week` grid or the grouped-day-list week mode for the builtin views (client
  grouping path exists; separate decision — see PRD 077).
- Server grid-hints / render-mode-aware `extensions.view` (rejected — D2).
- `ViewAnchor.MONTH_START` input defaults (no view needs a month default window yet;
  the month window derives client-side).
- Drag of repeating / multi-appointment blocks (needs the occurrence-vs-series
  dialog + exception semantics — PRD 094/091 territory).
- Drag-create and resize (no time axis in a month cell; week-grid work).
- Chip context actions, multi-select (PRD 094).
- SavedView persistence, view conversion, CalendarModel migration (PRD 077).

## Plan

### Phase 1 — server color field
- [ ] Failing tier-3 test: `appointmentBlocks { color }` returns expected color
      (fixture data), null for non-readable contributor (leak test, §12 recipe).
- [ ] `AppointmentBlock.color` resolver (RaplaBuilder-equivalent effective color).
- [ ] Builtin view text: `renderModes: [table, month]`, `color @hidden`.

### Phase 2 — SPA month grid
- [ ] Tier-5 tests: month-window derivation (grid Monday/Sunday incl. year wrap),
      day bucketing of blocks into cells.
- [ ] `MonthGridComponent` + mount branch in `ViewHostComponent` (tier-6 test:
      grid renders cells + chips from stubbed rows/meta).
- [ ] Control-strip month branch (◀ Monat Jahr ▶, Heute) + window derivation wiring.

### Phase 3a — drag-create (SHIPPED 2026-07-07)
- [x] `rangeScopedDraft(typeKey, chips, fromDay, toDay)` in `event-draft.ts` (tier-5):
      single day → 09:00–10:00 (newDraft convention), range → first 09:00 → last 17:00;
      scope resources pre-allocated via `scopeAllocations`.
- [x] Day-range selection state machine in `MonthGridComponent` (pointer capture +
      `elementsFromPoint` hit-testing, 4-px click/drag threshold, ESC cancel,
      `.selecting` highlight) → `createRange` output (tier-6, 4 tests).
- [x] `ViewHostComponent.openCreateRange`: resolves the first RESERVATION type (same
      query as the toolbar "Neu"), opens the event sheet `isNew` + prefilled draft —
      NOTHING persists until Speichern (the "editor öffnet vorbefüllt" decision;
      silent-create+undo was rejected: a real reservation needs type/name choices).
      Multi-type deployments get the first type — type is changeable in the sheet.

### Phase 3b — drag-move (DONE 2026-07-08, month + week grids)
Server: `appointment { id repeating { type } }` + `reservation { id canModify
appointmentCount }` in the builtin selection (OQ3). The SPA row context sources
block identity from the `appointment` object (legacy `appointmentId` scalar still
accepted). **Migration note (PRD 101, 2026-07-09):** the `dateShift: Duration`
verbs referenced below were replaced by the `reference`/`target` transpose family
(`moveReservations(ids, reference, target)`, `moveAppointment`, `splitOccurrence`;
`Duration` scalar deleted); month/week drag now dispatch through the shared
scope-aware `view-host.onMoveBlock` — see PRD 101 Phase 5 and
`docs/architecture/reservation-edit.md § "SPA move/resize — implemented"`.
Client (browser-verified end-to-end: drag → „…verschoben" toast → Rückgängig →
restored):
- [x] Drag state machine in both grids (idle → armed → dragging → drop/ESC, 4-px
      threshold, pointer capture, `elementsFromPoint` hit-testing). Month: whole-day
      shift + drop-target cell highlight; week: day+minute shift snapped to the
      rows-per-hour raster + dashed preview box.
- [x] Gate `isMovableRow` in the shared `block-style.ts` (PRD 100): `canModify` AND
      `appointmentCount === 1` AND `repeating === null` — STRICT null (a view that
      doesn't select `repeating` is not movable; fail-closed). Clipped multi-day
      week segments not draggable in v1.
- [x] Drop → `buildMoveCommand` → `moveReservations([id], PT<n>M)`; undo = the
      compensating negative shift (PRD 094 command shape); MutationBus refresh
      re-queries the window. Month-grid tier-6 tests pin drag/gate/ESC.
Also shipped with this phase (PRD 100/094 wiring): chip right-click opens the
SHARED row menu (Bearbeiten/Anzeigen/Löschen — same providers as table rows) and
chip double-click runs the shared edit path (`onRowDblClick`, D6).

## Tests

- Tier 3 (rapla-app MockMvc): color value + §12 leak test (visible vs hidden
  contributor byte-identical to visible-only).
- Tier 5: window math + bucketing (pure functions, no TestBed).
- Tier 6: `MonthGridComponent` renders 42 cells, chips in the right cells, overflow
  cap, grey fill-days.

## Open Questions

- **OQ1** — multi-day blocks: repeat the chip in every covered day cell vs a spanning
  bar across the week row. *Resolution:* pending (v1 leans repeat-per-day — trivial
  with the bucketing; spanning bars are layout work).
- **OQ2** — chip text contrast on dark colors (compute text color client-side from
  luminance vs server-emitted text color). *Resolution:* 2026-07-08 — **always black
  text, no luminance flip** (PRD 100 D1, Swing `SwingRaplaBlock.FOREGROUND_COLOR`
  parity); deployments pick colors that work with black. Implemented via the shared
  block-style module (PRD 100 Phase 1).
- **OQ3** — how the client knows a block is single-appointment + non-repeating for
  the drag gate. *Resolution:* 2026-07-08 — two raw facts on the wire (D2-consistent,
  no render-hint boolean): `AppointmentBlock.appointment: Appointment!` (the owning
  appointment, navigable — `repeating` read from there; `appointmentId` stays as
  PRD 094 scalar sugar) and `Reservation.appointmentCount: Int!` (list-free
  cardinality). Both in the builtin `rapla_appointments` hidden selection; tier-3
  `AppointmentBlockAppointmentGraphQLTest` green. Gate (client UX only, server
  re-checks in `moveReservations`): `canModify && appointmentCount === 1 &&
  appointment.repeating == null`, fail-closed when a custom view omits the fields.
  Rejected: `reservation.appointments` list on every row (payload × blocks), a
  server `movableWholeDay` boolean (render-hint, D2 violation), and a `compute()`
  EL expression (no `size()` function exists; untyped and fail-quiet).

## Decisions locked

**D1 — no new view; month is a render mode of `rapla_appointments`.** PRD 077's
"emergent switch charm": event-family views share `ReservationFilter`, so modes swap
freely on one view. A separate view would only be justified by a different root
field/filter, different column needs, or a different audience — none apply.

**D2 — no grid-hints schema; one §12-gated `color` field.** Closes PRD 077 OQ5. The
grid is a client renderer over the same flat rows; the only thing the client cannot
derive (or must not enforce) is the color and its permission gate. Rejected: the
render-mode-aware `extensions.view` rewrite sketched in 077 — much larger, and the
columns meta the table needs stays valid as-is.

**D3 — null the color, keep the block.** Deviation from 077's "drop the block whose
colors mix unreadable entities": the block already passed the §12 output filter on
its own data; only the color aggregates extra entities, so the color nulls and the
chip renders neutral. Dropping whole blocks would make the month view disagree with
the table view over identical data.

**D4 — per-render-mode window is ephemeral client state.** Reaffirms PRD 077's date
model: one persisted anchor, mode-specific windows derived at query time
(table = picker range; month = 42-day grid range).

**D5 — own implementation, in-house references first (PRD 032, 2026-07-07).** No
calendar library at runtime, no fork, no second build chain. Swing
`DraggingHandler`/`SelectionHandler` carry the interaction *semantics* (ported as a
pure-TS state machine); EventCalendar source is a read-only, MIT-attributed reference
for browser pointer *mechanics* and grid CSS. Full rationale + attribution rule:
PRD 032 §Calendar view decision.

**D6 — drag v1 reused `moveReservations`, gated to the safe subset — SUPERSEDED
by PRD 101 Phase 5 (2026-07-09).** v1: a month-cell drop is a whole-day shift and
`moveReservations([id], dateShift)` was exactly that for a single-appointment,
non-repeating reservation; repeating/multi blocks were not draggable (silently
picking EVENT vs occurrence-split would surprise users). **Now:** the drag gate is
widened (`block-style.isDraggableRow`) and a repeating/multi drop pops the
EVENT/SERIE/SINGLE scope dialog, dispatching `moveReservations` / `moveAppointment`
/ `splitOccurrence` server-side (PRD 101 D1). Month stays move-only (no resize —
Swing parity); the undo toast is still the compensating command (split is not
undoable in v1). See PRD 101 Phase 5.
