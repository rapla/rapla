# PRD 077 — Calendar model & saved views over GraphQL

**Status:** draft (design — deferred until the SPA table views ship: server contract in PRD 074,
Angular renderer in [PRD 078](078-spa-graphql-view-renderer.md)). Carved out of the PRD 074
discussion (2026-06-20) so the "quick breakthrough" (tables in the SPA) isn't blocked by the
harder persistence/switching design. 077 reuses 078's transport + renderer + selection.

## Goal

Replace the legacy **`CalendarModel` / saved calendars** with a GraphQL-native model: how the SPA
**persists view state**, how a user's saved calendars map onto **stored GraphQL views + inputs**,
how **view-switching/conversion** works, and how **week/month calendar render-modes** are fed from
GraphQL. PRD 074 owns the *table* render layer + the `@view`/function-field/sort/pagination
mechanics; **this PRD owns persistence, switching, conversion, and the graphical calendar views.**

## Legacy ground truth (verified)

- Saved calendars live in **user Preferences**: `CONFIG_ENTRY` (`org.rapla.DefaultSelection`) = the
  one default calendar; **`EXPORT_ENTRY`** (`org.rapla.plugin.autoexport`) = a
  `RaplaMap<CalendarModelConfiguration>` of **named** saved calendars.
- Each `CalendarModelConfiguration` bundles: **selection** (allocatable ids), **filter**
  (`ClassificationFilter[]`), **dates** (`startDate`/`endDate`/`selectedDate`), **view**
  (`selectedView` — the render-mode toggle), **title**, **optionMap** (plugin flags).
- `CalendarModelImpl.selectedView` is the **render-mode toggle over stable inputs** — the legacy
  "switch table↔week↔month keeping inputs" charm. It also stores **both** the table range
  (`start/end`) **and** the week anchor (`selectedDate`) so the switch-back is lossless.

## Decisions carried in from the PRD 074 discussion (2026-06-20)

- **SPA views are independent** (PRD 074 Decision 6) — no uniform input contract, no `timeContext`
  reconciliation machinery, no enforced cross-view switching. Each view = its `@view` query + its
  own inputs + render-mode.
- **The switch charm is *emergent*, not enforced:** the event-family views (`events`/`appointments`/
  `appointments_per_day`/`week`/`month`) all root at reservations/appointmentBlocks → all take the
  **same `ReservationFilter`** → switching among them is **free** (same input, swap render-mode),
  client-side. Non-event views (allocatable lists → `AllocatableFilter`; conflicts → conflict-scope)
  have their own inputs.
- **The only intra-family difference is the *date* sub-input:** table = `from/to` **range**;
  week/month = **anchor** + fixed interval. Resolved by: **persist one date** (the view's render-mode),
  and keep **per-render-mode date in client-session state** for lossless round-trips (the legacy
  "co-store both" becomes ephemeral client state, not persisted).
- **Three storage levels:** (1) **view definition** (shared/admin — the `@view` query, PRD 074
  storage); (2) **SavedView instance** (per-user — `{ viewRef, inputs, title?, options }`); (3)
  **runtime** (client-ephemeral GraphQL variables).
- **Two orthogonal axes:** **domain** (root field + its schema filter type — Reservation/Allocatable/
  Conflict; *no* formal domain enum, it dissolves into "which root field") × **render-mode**
  (table/per-day/week/month — domain-agnostic, needs the fields it renders; week/month need
  time-bearing data → not valid for allocatables).
- **Selection-source ≠ display-domain:** the conflict view = event data (the affected resources'
  appointments) with a **conflict-sourced selection** — modelled as the view's query navigating
  (conflicts → resources → appointments) + a conflict-scope input, not a special domain.

## Scope (this PRD)

- **SavedView persistence model.** Reuse the existing Preferences/`RaplaMap` infra (gets
  `DynamicTypeDependant`/revalidate/ownership-via-prefs for free): a `RaplaMap<SavedView>` (named) +
  a default, mirroring `EXPORT_ENTRY`/`CONFIG_ENTRY`. Open: **`viewRef` (reference shared view) vs
  self-contained**; **inputs form** (GraphQL-variable-values JSON vs structured like the legacy
  config); **ownership/visibility** (private user-prefs vs shared/published).
- **Date model.** Per-view date input; **fixed vs dynamic** (absolute `{from,to}`/`{date}` vs a
  relative spec `THIS_WEEK`/`CURRENT_MONTH`, resolved on load). Lossless switch = client-session.
- **View switching / conversion.** Free within the event family (same input). Cross-input switch =
  **best-effort conversion à la dynamic-type `commitChange`** ("convert what fits, drop the rest" —
  e.g. table-range→week-anchor; conflict-selection dropped in a plain table). Open: client-side vs
  small server helper; how aggressive the defaulting is. May ship as a pure **client-side
  convenience** (PRD 074 Decision 6 leaves the door open).
- **Week/month render-modes.** Feed the calendar grid from GraphQL (appointmentBlocks with
  start/end + display + resources + **colors**). `extensions.view` becomes **render-mode-aware**
  (grid-hints instead of columns). **§12 RenderedBlock rule:** a block whose colors mix a
  reservation + an allocatable is only emitted if the user can read **both** (else drop the block).
  → **Month slice ships via [PRD 095](095-month-grid-render-mode.md)** (2026-07-07): NO
  grid-hints schema — the grid is a pure client renderer over the flat rows; the only server
  addition is a §12-gated `AppointmentBlock.color` field. Deviation from the sketch above:
  unreadable color contributors **null the color, keep the block** (095 D3) instead of
  dropping the block.
- **Migration** of existing `CalendarModelConfiguration` saved calendars → (shared view +
  SavedView instance).

## Out of scope

The PRD 074 **table** render layer (`@view`, function fields, sort, pagination, `extensions.view`
for tables) — that ships first. The function/composition engine (PRD 073).

## Open questions

1. `viewRef` reference vs self-contained SavedView.
2. Inputs as GraphQL-variable-values JSON vs structured record.
3. Ownership/visibility (private vs shared/published saved views).
4. Conversion: client-side only vs server helper; defaulting aggressiveness.
5. Week/month render-meta shape in `extensions.view` (grid-hints schema).
   *Resolved 2026-07-07 (PRD 095 D2):* **none needed** — a §12-gated
   `AppointmentBlock.color` field sufficed; `extensions.view` stays column-shaped
   and the month grid renders client-side from the flat rows.
6. Migration path for existing CalendarModels.
