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
7. **Week grid → standard Google-Calendar behaviour** — *Resolved 2026-07-14.* Full SPA render
   model in [§ SPA week grid render model (OQ7 resolved 2026-07-14)](#spa-week-grid-render-model-oq7-resolved-2026-07-14).
   Autofit is replaced by a **full-day fixed raster scrolled to worktime** — Swing's own behaviour
   (`SwingWeekView.scrollToStart()`), not a Google divergence — on a **one-grid, three-sticky-region**
   skeleton (day-header / hour-gutter / corner, settling "header inside or outside the scroll?" as
   *inside, as sticky regions of one grid*), with **worktime shading** fed by a new `CalendarOptions`
   GraphQL query, and an **all-day/multi-day banner band**:
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
   - Rest of the Google bundle — current-time indicator, scroll-to-worktime on open, fixed hour
     raster replacing autofit — all resolved in the section.

## SPA week grid render model (OQ7 resolved 2026-07-14)

The SPA week grid's rendering half of OQ7, locked in the 2026-07-14 design session. It **consumes
the [PRD 097 § Phase 5](097-event-html-templates-mustache.md#phase-5--2d-time-grid-rendering-the-layout-engine-half)
server geometry** (`segments`, `banner`, `bandBars: bars(scope: BANNER)`, `wholeDay`) instead of
computing layout client-side — so the *same* event never renders differently in `/app` vs a
document, and `week-lanes.ts` (then `month-chunks.ts`) retire. This section owns the half 097 does
**not**: the on-screen render/scroll model, worktime, the banner *interaction*, and print. (The
multi-day classification is server-side and settled — Rule B; there is no client-side Rule A/B
staging.)

### Vertical model — full-day fixed raster + scroll-to-worktime (Swing parity)

Replaces **autofit** (`week-grid-autofit` — the whole day squeezed into the viewport). The axis is
a **fixed** hour raster (`rowSize × rowsPerHour`); the body renders the **full day 00:00–24:00**
and the scroller is positioned to **worktime-start** on open — a direct port of
`SwingWeekView.scrollToStart()` (`y = rowScale.getStartWorktimePixel()`), plus a live per-minute
**now-line** (the red line + `nowTop()` exist today, static). Rejected alternatives: *Model 1
clamped range* (axis = worktime ∪ data-extent — jitters as data loads / pages change) and *Model 3
hybrid collapse* (collapsible empty-night strips — most code). Full-24 wins on **zero layout
jitter** and is what Swing / Google / Outlook all do.

### Worktime shading + `CalendarOptions` over GraphQL (new server slice)

Hours inside `[worktimeStart, worktimeEnd]` render normal; outside greyed
(`LargeDaySlot.NON_WORKTIME_BACKGROUND` parity), overnight-aware via `WorktimeRange.isOvernight`.
This needs calendar options the SPA **cannot see today** — a new read query exposing per-user
(→ system fallback) `worktimeStartMinutes`, `worktimeEndMinutes`, `rowsPerHour` (rapla default
**4**; the SPA currently hardcodes a local 2), `firstDayOfWeek`, `excludedDays`, `worktimeOvernight`.
Read-only for v1 — the SPA calendar-options **editor** is a separate future item (the
`WorktimeRange` javadoc already anticipates "the Angular calendar-options form"). The grid's
`rowsPerHour` `<select>` becomes a **per-session override** of the configured default (ephemeral
runtime state). `firstDayOfWeek` + `excludedDays` are honoured (the grid hardcodes Monday via
`mondayOf` today; excluded days drop their column, Swing-style).

### Layout skeleton — one grid, three sticky regions

Today's **two** grids (`.hdr` + `.body`, column-aligned by duplicated `minmax(px,fr)`) unify into
**one** CSS grid with `position: sticky` on the day-header row (`top:0`), the hour-gutter column
(`left:0`), and the corner cell — the CSS equivalent of Swing's `JScrollPane`
column-header / row-header / corner. This settles OQ7's "header inside or outside the scroll?"
question: **inside, as sticky regions of one grid** — columns can't drift (single grid) and
horizontal scroll stays synced for free. This unification is the **long pole** of the feature and a
prerequisite for the sticky banner band.

### Banner band — interaction (classification + geometry are server-side, Rule B)

Classification (`banner`) and geometry (`bandBars`, `segments`) come from the server; the SPA
renders the band as sticky chrome between the day header and the time grid, and owns the
**interaction**, resolved so a date-granular band never edits a time it can't express:

- **Move** a bar → whole-day steps (changes dates, preserves time-of-day; reuses the grid's
  `moveBlock` with `minuteDelta: 0`).
- **Resize** a bar → whole-day `+24h` steps — a **new verb shape** `{endDayDelta}`, distinct from
  the grid's minute-of-day `resizeBlock`. Both map to the PRD 101 `moveAppointment` / resize verbs.
- **Minute/time edits** happen in the **grid**; the multi-day case opens the **editor**
  (double-click). This is interaction-complete **and** eliminates mid-gesture band↔grid migration
  (day-steps can't cross the "covers a full day" threshold; shrinking below it only happens in the
  editor, where a full re-layout is expected).

### Band overflow (#3) + print (#4)

- **#3 — band too tall on screen.** Cap at ~3 rows; overflow into a per-day **"+N more"** chip that
  expands the band (Google model). Expanding shrinks the sticky chrome → **re-run
  scroll-to-worktime** so working hours aren't pushed out of view. `expanded` is ephemeral; the cap
  is **screen-only**. A resource scheduler makes this real (many multi-day bars).
- **#4 — stacked print can't span.** Banner-class blocks render per target, all from the server
  fields:

  | Target | Banner render | Cap |
  |---|---|---|
  | Screen | spanning bars in the sticky band (`bandBars`) | capped (#3), expandable |
  | Print — grid | same band, days side by side | **uncapped** (paper has no "+N more") |
  | Print — stacked | per-day **strip** of that day's `segments` + ‹ › markers | n/a |

  Grid vs stacked print is unchanged (`printMode()` — side-by-side while every lane keeps
  ≥ `PRINT_MIN_LANE_PX`, else stack days). **Print clamps the hour range** to `worktime ∪
  data-extent` (not the full-24 screen raster) so stacked days stay compact and empty nights aren't
  printed; print continues to pin `--wg-hpx: 48px`. The shared week axis keeps full horizontal
  time-comparison in grid print; stacked print keeps the identical per-day scale but loses the
  horizontal sweep (an existing property of stacked mode, not new).

### Robustness — the SPA must render when the server hasn't set everything

The SPA **prefers server data, never hard-depends on it.** Three "not set" cases, each with a
fallback so the grid always renders:

1. **`CalendarOptions` absent/partial** (old server, or unconfigured deployment/user) → client
   defaults: worktime 8–18, `rowsPerHour` 4, Monday first, no excluded days. The options query is
   an enhancement, not a gate.
2. **Geometry fields absent** (`segments` / `banner` / `bandBars` — a server without
   [PRD 097 § Phase 5](097-event-html-templates-mustache.md#phase-5--2d-time-grid-rendering-the-layout-engine-half),
   *or* a custom `@view` that doesn't select them; 097 makes them lazy-on-selection) → compute
   client-side via the existing `week-lanes.ts` (`layoutWeek` for segments + Rule B for `banner` +
   bars stacking for `bandBars`).
3. **Mixed** → per-field: use the server value when present, else compute that one field.

**Committing to Rule B is what makes this safe:** it is a single fixed semantics (no per-view rule
argument — 097), so the client fallback (`wholeDay || fullDayCovered`) reproduces the server's
`banner` classification **identically**. A configurable rule would let `/app` diverge from a
document; one rule cannot.

**Consequence for `week-lanes.ts`:** it is **demoted to the fallback tier, not deleted.** Phase C
adds a "prefer-server-geometry" consumption path; full retirement is a **later** step gated on the
server *always* providing geometry *and* every view selecting it (never guaranteed for arbitrary
custom views). The earlier "retiring `week-lanes.ts`" framing (and 097's "consolidation option")
is the aspiration, not a Phase-C deliverable.

### Plan (phased)

- **Phase A — scrollable grid core.** `CalendarOptions` GraphQL query (+ client defaults per the
  robustness note) + client threading; one-grid three-sticky-region skeleton; full-day fixed raster
  + scroll-to-worktime; worktime shading; live now-line; print range-clamp. *(No banners.)*
- **Phase B — week composition options.** Honour `firstDayOfWeek` + `excludedDays`.
- **Phase C — banner band, prefer server geometry.** Consume server `segments` / `banner` /
  `bandBars` (Rule B) when present, else client fallback (`week-lanes.ts`); screen spanning bars;
  #3 overflow cap + reclamp; #4 print routing; banner day-granular move/resize verbs. Lands **with**
  PRD 097 Phase 5 so `/app` and documents share one geometry implementation for the builtin view.
