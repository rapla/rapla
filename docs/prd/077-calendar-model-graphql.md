# PRD 077 — Calendar model & saved views over GraphQL

**Status:** draft (design — deferred until the SPA table views ship: server contract in [PRD 074](074-graphql-declarative-views.md),
Angular renderer in [PRD 078](078-spa-graphql-view-renderer.md)). Carved out of the [PRD 074](074-graphql-declarative-views.md)
discussion (2026-06-20) so the "quick breakthrough" (tables in the SPA) isn't blocked by the
harder persistence/switching design. 077 reuses 078's transport + renderer + selection.

## Goal

Replace the legacy **`CalendarModel` / saved calendars** with a GraphQL-native model: how the SPA
**persists view state**, how a user's saved calendars map onto **stored GraphQL views + inputs**,
how **view-switching/conversion** works, and how **week/month calendar render-modes** are fed from
GraphQL. [PRD 074](074-graphql-declarative-views.md) owns the *table* render layer + the `@view`/function-field/sort/pagination
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

## Decisions carried in from the [PRD 074](074-graphql-declarative-views.md) discussion (2026-06-20)

- **SPA views are independent** ([PRD 074](074-graphql-declarative-views.md) Decision 6) — no uniform input contract, no `timeContext`
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
- **Three storage levels:** (1) **view definition** (shared/admin — the `@view` query, [PRD 074](074-graphql-declarative-views.md)
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
  convenience** ([PRD 074](074-graphql-declarative-views.md) Decision 6 leaves the door open).
- **Week/month render-modes.** Feed the calendar grid from GraphQL (appointmentBlocks with
  start/end + display + resources + **colors**). `extensions.view` becomes **render-mode-aware**
  (grid-hints instead of columns). **§12 RenderedBlock rule:** a block whose colors mix a
  reservation + an allocatable is only emitted if the user can read **both** (else drop the block).
  → **Month slice ships via [PRD 095](095-month-grid-render-mode.md)** (2026-07-07): NO
  grid-hints schema — the grid is a pure client renderer over the flat rows; the only server
  addition is a §12-gated `AppointmentBlock.color` field. Deviation from the sketch above:
  unreadable color contributors **null the color, keep the block** (095 D3) instead of
  dropping the block.
  → **Week slice prototyped 2026-07-08** (same no-grid-hints pattern): `WeekGridComponent`
  + `week-lanes.ts` (dynamic lanes), FLOW cross-day drag-create (Swing `SelectionHandler`
  port), rows-per-hour raster, builtin `rapla_appointments` `renderModes: [table, week,
  month]`. **Mode shuffle:** the grouped day-list moved `week` → `day`; `week` is now the
  time grid (stored custom views declaring `week` for the grouped list need `day`).
  Swing-parity hardening (lane model, zoom, worktime, shared block styling) is
  [PRD 100](100-spa-block-renderer-unification.md).
  → **Print support landed 2026-07-09**: browser print shows ONLY the main pane (shell
  chrome hidden via `@media print` in `app.css`; Material drawer un-caged; a print-only
  `view-title` heading carries "Termine (68 Termine)", the on-screen count moved to the
  control strip as right-aligned `ViewStateStore.resultInfo`). The week/day grid picks its
  print layout via `printMode()` in `week-lanes.ts`: **grid** (lanes drop the 80px
  floor, squeeze as `fr` onto the page) vs **stacked days** (one full-width day per
  block with print-only day label + hour axis) when lanes would fall below `PRINT_MIN_LANE_PX` (56px,
  ≈3+ lanes/day avg on a 7-day week). Horizontal overflow is unpaginatable in CSS —
  stacking converts it to vertical flow. The week/day view ALWAYS prints landscape
  (`@page A4 landscape` injected while mounted — 2026-07-09 decision: grid needs the
  width, stacked days get wider lanes and a typical 8–18h day still fits one landscape
  page; table/month keep the user's free orientation choice). Tests: `week-print.spec.ts` (boundary math),
  `week-grid-print.spec.ts` (class/`@page` wiring), `view-host-result-info.spec.ts`.
- **Migration** of existing `CalendarModelConfiguration` saved calendars → (shared view +
  SavedView instance).

## Out of scope

The [PRD 074](074-graphql-declarative-views.md) **table** render layer (`@view`, function fields, sort, pagination, `extensions.view`
for tables) — that ships first. The function/composition engine ([PRD 073](073-graphql-function-equivalents.md)).

## Open questions

1. `viewRef` reference vs self-contained SavedView.
2. Inputs as GraphQL-variable-values JSON vs structured record.
3. Ownership/visibility (private vs shared/published saved views).
4. Conversion: client-side only vs server helper; defaulting aggressiveness.
5. Week/month render-meta shape in `extensions.view` (grid-hints schema).
   *Resolved 2026-07-07 ([PRD 095](095-month-grid-render-mode.md) D2):* **none needed** — a §12-gated
   `AppointmentBlock.color` field sufficed; `extensions.view` stays column-shaped
   and the month grid renders client-side from the flat rows.
6. Migration path for existing CalendarModels.
7. **Week grid → standard Google-Calendar behaviour** (noted 2026-07-14; own session — it has its
   own challenges). Today the SPA week grid autofits the whole day into the viewport
   (`week-grid-autofit`) and splits multi-day blocks into per-day segments with continuation
   markers (`week-lanes.ts`). The Google-standard model differs:
   - **Scrollable time grid, fixed chrome** — the day/date header (and an all-day band) stay
     pinned while the hour grid scrolls to a sensible default (e.g. 07:00), instead of squeezing
     00–24 into the viewport. The named challenge: **is the header inside or outside the scroll
     container?** Outside = sticky header + a separate scroll area, but then column widths must
     be kept in sync across two grids (the current template deliberately uses ONE grid because
     the header text's min-content would otherwise drift the columns apart —
     `week-grid.component.ts`); inside (`position: sticky` on row 1) keeps one grid but
     constrains chrome/print handling (`@media print` currently relies on the autofit layout).
   - **All-day / multi-day banner band** in the header — **decided 2026-07-14: Rule B is the
     model** (`banner` ⇔ `wholeDay` OR a full calendar day lies inside `[start, end)`; NOT a
     duration threshold — Mon 16:00→Tue 16:00 is 24h without a covered day → grid; night shifts
     keep the midnight-split). ⚠️ Coupled to
     [PRD 097 § Phase 5](097-event-html-templates-mustache.md#phase-5--2d-time-grid-rendering-the-layout-engine-half),
     which now ships the fields (`banner`, `wholeDay`, `bars(scope: BANNER)` for band-only
     stacking, banner blocks emit no `segments`): this item is a **committed companion, not an
     option** — the SPA week grid must adopt the band in the same arc so the same event never
     renders differently in `/app` vs a document; ideally the SPA consumes the same fields,
     beginning the `week-lanes.ts` retirement.
   - Also in the Google bundle, to be scoped then: current-time indicator, scroll-to-now on open,
     fixed hour raster (scrolling replaces autofit).
