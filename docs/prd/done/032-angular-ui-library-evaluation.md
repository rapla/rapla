# 032 — Angular calendar view + UI component library

**Status:** done — both decisions locked (Material 2026-05-12; calendar 2026-07-07: **own implementation, no external calendar library** — see §Calendar view decision). The Phase-1 bake-off is cancelled (no library to bake off); calendar implementation ships via PRD 095 (month) and PRD 077 (week/resources).

## Goal

Pick the UI stack for the Angular frontend from PRD 026.

**Component library: Angular Material (decided 2026-05-12).** Rationale below in §Component library decision. This PRD no longer evaluates component libraries — only the calendar.

**Calendar view library: none — own implementation (decided 2026-07-07).** The original bake-off premise ("view-only: no drag-edit, no resource axis") was superseded: multi-resource week + in-grid drag-edit ARE on the roadmap (PRD 077/095), which collapses the finalist field — see §Calendar view decision.

Constraint: **OSS license compatible with Rapla (AGPL / Apache 2.0)**. Commercial libs (Syncfusion, Bryntum, Mobiscroll, Kendo, DevExtreme, AG Grid Enterprise, FullCalendar Premium, Schedule-X Premium) are disqualified up front.

The calendar lib doesn't have to be Angular-native — a framework-agnostic JS / web-component that integrates cleanly into Angular is fine. The component library does need first-class Angular bindings.

## Why

**Calendar view, not scheduler.** Rapla's Swing client has a full scheduler (multi-resource timeline, drag-edit, conflict overlays). The Angular v1 deliberately starts smaller: render events on week + month grids, click → open editor. Keeps the bake-off cheap; avoids picking on requirements the SPA doesn't have yet. A future PRD can introduce a resource-axis view if needed.

**Editor is handmade, not vendor.** The reservation editor carries ~15 years of rapla-specific behaviour: `AppointmentController` rules, recurrence + exception dates, permission gating per field, `EventCheck` pre-save validators, classification-aware fields. None maps to a vendor form-builder. Swing knowledge transfers to the editor's *behaviour*, not to a calendar component's *rendering*.

**Component library is therefore for editor primitives** — text fields, date pickers, dialogs, the table listing reservations — plus option panels migrated in PRD 026.

PRD 026 Phase 0 proved the toolchain (Angular 21 + typed REST client + same-origin hosting) on a plain-HTML reservation table. Phase 1 needs real calendar rendering + proper form controls; this PRD chooses both.

## Scope

In scope:

- Evaluate OSS week-grid + month-grid calendar view libraries.
- Run one short bake-off, score, decide.
- Wire the calendar pick + Angular Material into `rapla-angular/`: replace the Phase 0 plain-HTML list with `mat-table` + open a stub dialog (`MatDialog`) on row click.

Out of scope:

- **Resource-axis / timeline scheduling view.** Not in SPA v1; separate PRD if needed.
- **Drag-create / drag-resize / drag-move inside the calendar.** Click → opens handmade editor.
- **The reservation editor itself.** Lives in PRD 026 Phases 2–5.
- Charts, rich text editors, file upload, drag-drop tree.
- Theming / branding (default theme is fine for v1).
- i18n wiring (verify the API exists; don't connect it).
- Replacing the Phase 0 reservation list with a calendar view — calendar lands in PRD 026 Phase 4.

## Calendar view requirements

Derived from `docs/architecture/reservation-edit.md` and `conflicts-and-events.md`, restricted to view-only.

**Must-have:**

1. **Week view** (7-day grid, time axis) and **month view** (date cells with event chips).
2. **OSS license** compatible with AGPL / Apache 2.0. MIT, Apache 2.0, BSD, LGPL all fine. GPL v2/v3 only if rapla accepts dropping Apache 2.0 dual-licence — recommendation: rule GPL out.
3. **Custom block content** — render title + owner + classification colour (equivalent of Swing's `RaplaBuilder.colorize`). Render hook returning full HTML required.
4. **Event-click handler** — fires with clicked event's data; handmade editor opens from this.
5. **View switcher + navigation** (prev / next / today, week ⇄ month).
6. **Locale** (German dominant) and **first-day-of-week** configurable.
7. **Compatible with Angular 21** — native Angular package or framework-agnostic library wrapping cleanly inside an Angular component.
8. **Active maintenance** — release within last 12 months.

**Nice-to-have:**

9. Day view as third option.
10. Keyboard navigation + a11y annotations.
11. Bundle ≤ 100 KB compressed (view-only is much lighter than full scheduler).
12. Time-range navigation hook (pre-fetching events on visible window — pairs with `/edit/expand-blocks` in PRD 026 §B4).

**Explicitly NOT required:**

- Multi-resource axis / timeline / `rowHeaderColumns`.
- Drag-resize, drag-create, drag-move.
- Conflict overlay during drag.
- Virtualised resource scrolling.

## Calendar view candidates (OSS, screened)

With drag-edit and resource view out of scope, several previously-screened libraries come back.

| Library | Licence | Week+Month | Custom block render | Bundle | Active | Verdict |
|---|---|---|---|---:|---|---|
| **EventCalendar (vkurko/calendar)** | MIT | ✅ TimeGrid + DayGrid | ✅ `eventContent` → `{html}` | ~35 KB br | v5.7.0 Apr 2026 | **finalist** |
| **FullCalendar core** | MIT | ✅ (only Resource views are paid — irrelevant here) | ✅ `eventContent` slot | ~90 KB min | active | **finalist** — biggest ecosystem |
| **Schedule-X core** | MIT | ✅ (premium features irrelevant) | ✅ event content slot | moderate | active | **finalist** — most modern API |
| angular-calendar (mattlewis92) | MIT | ✅ exactly month/week/day, nothing else | ✅ template projection | small | active (Angular 20.2+) | viable fallback |
| TOAST UI Calendar | MIT | ✅ | ✅ | n/a | maintenance unclear | fallback only |
| DayPilot Lite | Apache 2.0 | ✅ | ✅ | ~60 KB min | active | viable; no clear advantage |
| DHTMLX Scheduler Standard | **GPL v2** | ✅ | ✅ | ~120 KB min | active | **out on license** |
| FullCalendar Premium / Schedule-X Premium / etc. | commercial | — | — | — | — | **out (commercial)** |

**Top 3 to bake off:** EventCalendar, FullCalendar core, Schedule-X core.

## Calendar view decision (2026-07-07): own implementation, references not runtime deps

**Requirement change that forced the re-screen:** the SPA roadmap needs **multi-resource
week views + in-grid drag-edit** (PRD 077; month grid first via PRD 095) — both were on
this PRD's original "explicitly NOT required" list. Multi-resource views are exactly what
the commercial vendors paywall: FullCalendar core (MIT) has no resource axis (Premium,
proprietary — visible source is NOT copyable), Schedule-X resource scheduler is paid,
angular-calendar/DayPilot Lite have no real multi-resource story. The only MIT option
with resource views + drag-edit is **EventCalendar (vkurko/calendar)** — which is written
in **Svelte + untyped JS**. Consuming it compiled is build-chain-neutral, but forking it
(the insurance against its bus-factor-1 maintenance) would mean a second frontend build
chain in a foreign dialect — **ruled out** (constraint: no separate build chain).

**Decision: build the calendar surfaces ourselves, in the existing Angular build chain,
from rapla's own battle-tested logic, using EventCalendar's source only as a read-only
reference.** The reference hierarchy:

1. **Primary — rapla's three in-house implementations:**
   - Layout math + block model: `rapla-core/components/calendarview/` (`Builder`,
     `BestFitStrategy`, `AbstractGroupStrategy`, `WeekdayMapper`/`MonthMapper`) +
     `RaplaBuilder`/`BlockColors` (`plugin/abstractcalendar`, `plugin/calendarview`).
   - Live server-rendered HTML calendar: `rapla-server/plugin/{weekview,monthview,
     compactweekview,dayresource,timeslot}/server/` — incl. `HTMLDayResourcePage`
     (resources-as-columns = the multi-resource grid pattern), in production at
     `/rapla/calendar`.
   - Interaction semantics: `rapla-client/components/calendarview/swing/`
     `DraggingHandler` (250 LOC) + `SelectionHandler` (296 LOC) — drag-move/resize/create
     rules, slot hit-testing, permission gating. Ported as a pure-TS state machine
     (tier-5 testable); Swing mouse mechanics replaced by browser pointer events.
2. **Secondary — EventCalendar source (github.com/vkurko/calendar), read-only sibling
   clone, never in this repo:** modern-browser pointer patterns (`interaction` package:
   touch, scroll-while-drag, ghost rendering) and grid DOM/CSS shape (`day-grid`,
   `time-grid`, `resource-time-grid` packages).

**Attribution rule (MIT → Apache-2.0/GPL-3.0 dual is compatible, one-way):** every file
containing code copied or *closely translated* from EventCalendar (translation = derivative
work; patterns/ideas are free) carries a header —
`Portions derived from EventCalendar (https://github.com/vkurko/calendar), Copyright (c)
Vladimir Kurko, MIT License — see LICENSE_MIT_EVENTCALENDAR.` — and the full MIT text
lands once as `LICENSE_MIT_EVENTCALENDAR` next to `LICENSE_APACHE2`/`LICENSE_GPL3`
(created with the first actual copy, not before). GPL-only sources (DHTMLX) remain
un-copyable into the Apache side; FullCalendar Premium is proprietary — not copyable at all.

**Rejected alternatives:** (a) consume EventCalendar as pinned npm dep — fastest to a
working grid, but bus-factor-1 with no fork insurance and a public-hooks ceiling;
(b) fork EventCalendar — Svelte build chain + untyped-JS maintenance, ruled out;
(c) resurrect-or-port question for the *layout engine* (server-side `CalendarLayoutEngine`,
deleted 2026-05-27 `f4e9c048`, recoverable from git — vs a TS port of `BestFitStrategy`)
stays OPEN, owned by the week-grid work — the month grid (PRD 095) needs no overlap layout.

## Component library decision

**Picked: Angular Material (MIT)** — decided 2026-05-12, no bake-off run.

Reasoning:

- **A11y first-class.** Every component ships with correct ARIA roles, keyboard interaction, focus management. Rapla has public-sector deployments where WCAG 2.2 AA matters; Material reduces remediation work for audits that PrimeNG / Taiga / NG-ZORRO would require by hand.
- **Maintained by Angular team.** Tracks Angular 21 cadence directly; no risk of falling behind on major bumps.
- **Curated component set.** ~35 components is smaller than PrimeNG's 80+ but covers every rapla need (forms, dialog, table, snackbar, date/time picker, tabs, tree). Smaller surface is a feature — less bundle bloat, less drift risk.
- **CDK Table is a toolkit, not a finished grid.** Main weakness vs PrimeNG's DataTable. Acceptable for view-list rendering (sort + paginate + filter are documented patterns); revisit only if a future admin list needs advanced grid features.
- **Theming via CSS variables** plays cleanly with whatever calendar lib wins Phase 1.

Considered and not picked: **PrimeNG** (broader, faster data-grid wins, but weaker a11y defaults and larger surface to police); **Taiga UI / NG-ZORRO / Clarity** (viable but smaller ecosystems; nothing outweighs Material's a11y + Angular-team-maintained advantage).

What rapla uses from Material:

| Need | Material component |
|---|---|
| Reservation list | `mat-table` + `MatSort` + `MatPaginator` |
| Editor dialog | `MatDialog` |
| Form fields | `mat-form-field` + `matInput` / `MatDatepicker` / `MatSelect` / `MatAutocomplete` / `MatCheckbox` / `MatRadio` / `MatSlideToggle` |
| Toasts | `MatSnackBar` |
| DynamicType tree | `mat-tree` |
| Tabs (option panels) | `mat-tab-group` |
| Expansion panels | `mat-expansion-panel` |

## Mixing libraries — what's supported

Supported shape is **one calendar lib + Angular Material**. They cover different surface area: calendar is a single widget inside one route, Material wraps everything else. CSS-variable themes align with a few overrides; only real seams are z-index (calendar popovers vs `MatDialog` overlay) and date-picker style drift (calendar header nav vs `MatDatepicker` in the editor).

**No second general component library.** Adding PrimeNG / Taiga / NG-ZORRO alongside Material is an anti-pattern — two button styles, two dialog stacks fighting for focus and z-index, two `ControlValueAccessor` adapters, two date pickers, two icon sets, roughly doubled bundle weight. If a missing Material component bites us, prefer building on `@angular/cdk` primitives over adding a second library.

## Plan

### Phase 1 — calendar view bake-off (target: 1.5 days)

Build the same slice three times under `rapla-angular/src/app/bakeoff/` (gitignored, deleted at end):

> "Render 50 mock events on a week grid + month grid. Click event → `console.log` the event id. Custom block renders title + owner + a coloured left-border based on a mock `classification` field. View switcher (week ⇄ month). Locale `de`, first day of week Monday."

Build in **EventCalendar**, **FullCalendar core**, and **Schedule-X core**.

Score each on:

- Time-to-first-render with real Angular 21 + signals.
- API ergonomics (declarative inputs vs imperative `new Calendar(el, opts)`).
- Custom-block render fidelity (HTML escape hatch, performance with 50 blocks).
- Theming primitives (CSS vars vs inline-style vs opinionated CSS bundle) — must coexist with Material tokens.
- Locale + first-day-of-week wiring.
- Bundle impact (`ng build --configuration=production`, compare `main.*.js` gzipped delta on top of Material baseline).
- Angular integration depth (typed inputs, signal compat, SSR not relevant).
- Docs + examples coverage of the rapla-shaped use case.

Write the scorecard at the bottom of this PRD. Decide.

### Phase 2 — wire-up (target: 0.5 day)

- `npm install @angular/material @angular/cdk` and the calendar pick.
- Replace Phase 0 plain-HTML list with `mat-table` + `MatSort` + `MatPaginator` (reading from `GET /storage/resources` + appointment query, unchanged).
- Wire `MatDialog`; row-click opens stub editor dialog (single read-only `mat-form-field` with title, no save).
- Add `MatSnackBar` for save/error toasts (used later by PRD 026 phases 2+).
- Confirm `ng build --configuration=production` and same-origin load through `http://localhost:8051/rapla/app/` still work.
- Delete `rapla-angular/src/app/bakeoff/`.
- Update PRD 026 Phase 1 plan to reference Material + the picked calendar lib by name.

No calendar code lands in this PRD — calendar wired in PRD 026 Phase 4 using the library chosen here.

## Tests

- One tier-3 MockMvc test in rapla-app confirming `GET /app/index.html` still returns 200 after the bundle grows — guards static-resource path against silent break under larger SPA payload.
- Two `vitest` tests on the wire-up:
  - Mounts `mat-table` with fixed dataset, asserts rendered row count and `MatSort` behaviour.
  - Mounts a `MatDialog` host, fires open + ESC, asserts close.
- No tests for bake-off code (the `bakeoff/` directory is deleted before merge).

Smoke test by `mvn -pl rapla-app -am spring-boot:run -Dspring-boot.run.fork=false` + `cd rapla-angular && ng build --watch` per PRD 026 §Dev-mode wiring, load `http://localhost:8051/rapla/app/`, log in, verify list + stub dialog round-trip works.

## Open questions

1. **Bundle budget for production `main.*.js`.** Phase 0 prototype is ~250 KB gzipped; Material + calendar lib pushes it to 500 KB – 1 MB. Soft proposal: **1 MB gzipped** including generated typed client. Confirm before Phase 1 starts.
2. **Date-picker coexistence.** `MatDatepicker` is the editor's date input; the calendar lib has its own navigation header. Accept two visual styles, or override the calendar's header to use `MatDatepicker`? Decide during Phase 1.
3. **Calendar lib's CSS bundle vs tree-shaking.** Some candidates ship a single CSS file; others provide per-view imports. Note any awkward all-or-nothing imports in the scorecard.
4. **Material theme choice.** Material 3 ships prebuilt themes (`azure-blue`, `cyan-orange`, …) and a custom-theme generator. Default to a prebuilt for v1; pick the colour scheme during Phase 2.

## Decisions

| Decision | Pick | Date | Rationale |
|---|---|---|---|
| Component library | **Angular Material** | 2026-05-12 | A11y first-class, Angular-team-maintained, curated set covers all rapla needs |
| Calendar view library | **none — own implementation** | 2026-07-07 | Multi-resource + drag-edit requirement collapsed the MIT field to EventCalendar (Svelte); no-fork/no-second-build-chain constraint + rich in-house logic (Swing/HTML/builder) → build ourselves, EventCalendar source as MIT-attributed reference. §Calendar view decision |

## References

- **Angular UI libraries** —
  [Material or PrimeNG? (Syncfusion, Apr 2026)](https://www.syncfusion.com/blogs/post/angular-material-vs-primeng),
  [Choosing an Angular Library in 2026 (Syncfusion)](https://www.syncfusion.com/blogs/post/angular-component-libraries-in-2026),
  [16 Options Compared (Colorlib, 2026)](https://colorlib.com/wp/angular-components/),
  [Angular Material Alternatives (Infragistics, 2026)](https://www.infragistics.com/blogs/angular-material-alternatives).
- **Calendar components** —
  [EventCalendar (vkurko/calendar, MIT)](https://github.com/vkurko/calendar),
  [FullCalendar Angular docs (MIT core, paid resource view)](https://fullcalendar.io/docs/angular),
  [Schedule-X (MIT core)](https://github.com/schedule-x/schedule-x),
  [Schedule-X Angular wrapper](https://github.com/schedule-x/angular),
  [angular-calendar (mattlewis92, MIT)](https://github.com/mattlewis92/angular-calendar),
  [DayPilot Lite (Apache 2.0)](https://javascript.daypilot.org/open-source/),
  [TOAST UI Calendar (MIT)](https://github.com/nhn/tui.calendar).
- **Rapla architecture** —
  `docs/architecture/reservation-edit.md`,
  `docs/architecture/conflicts-and-events.md`,
  `docs/architecture/permissions.md`,
  and PRD 026.
