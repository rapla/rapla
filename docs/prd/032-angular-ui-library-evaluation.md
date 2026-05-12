# 032 — Angular calendar view + UI component library

**Status:** draft (2026-05-12)

## Goal

Pick the UI stack for the Angular frontend from PRD 026.

**Component library: Angular Material (decided 2026-05-12).**
Rationale below in §Component library decision. This PRD no
longer evaluates component libraries — only the calendar.

**Calendar view library: TBD by Phase 1 bake-off.** Renders
week and month views of appointments. View-only: a click on an
event opens the handmade editor. No in-grid drag-edit, no
resource axis.

Constraint: **OSS license compatible with Rapla
(AGPL / Apache 2.0)**. Commercial libs (Syncfusion, Bryntum,
Mobiscroll, Kendo, DevExtreme, AG Grid Enterprise,
FullCalendar Premium, Schedule-X Premium) are disqualified up
front and not evaluated below.

The calendar lib doesn't have to be Angular-native — a
framework-agnostic JS / web-component that integrates cleanly
into Angular is fine. The component library does need
first-class Angular bindings.

## Why

**Calendar view, not scheduler.** Rapla's Swing client has a
full scheduler (multi-resource timeline, drag-edit, conflict
overlays). The Angular v1 deliberately starts smaller: render
events on a week grid + month grid, click → open editor. This
keeps the bake-off cheap and avoids picking on requirements
the SPA doesn't have yet. A future PRD can introduce a
resource-axis view if needed.

**Editor is handmade, not vendor.** The reservation editor
carries ~15 years of accumulated rapla-specific behaviour:
`AppointmentController` rules, recurrence + exception dates,
permission gating per field, `EventCheck` pre-save validators,
classification-aware fields. None of that maps to a vendor
form-builder. The Swing knowledge transfers to the editor's
*behaviour*, not to a calendar component's *rendering*.

**The component library is therefore for editor primitives**
— text fields, date pickers, dialogs, the table that lists
reservations — plus the option panels migrated in PRD 026.

PRD 026 Phase 0 proved the toolchain (Angular 21 + typed REST
client + same-origin hosting) on a plain-HTML reservation
table. Phase 1 needs real calendar rendering and proper form
controls; this PRD chooses both.

## Scope

In scope:

- Evaluate OSS week-grid + month-grid calendar view libraries.
- Run one short bake-off, score, decide.
- Wire the calendar pick + Angular Material into `rapla-angular/`:
  replace the Phase 0 plain-HTML list with `mat-table` + open a
  stub dialog (`MatDialog`) on row click.

Out of scope:

- **Resource-axis / timeline scheduling view.** Not in SPA v1.
  Revisit in a separate PRD if a customer needs it.
- **Drag-create / drag-resize / drag-move inside the
  calendar.** Click → opens the handmade editor instead.
- **The reservation editor itself.** Lives in PRD 026 Phases
  2–5, built on top of whatever component library wins here.
- Charts, rich text editors, file upload, drag-drop tree.
- Theming / branding (default theme is fine for v1).
- i18n wiring (verify the API exists; don't connect it).
- Replacing the Phase 0 reservation list with a calendar view —
  the calendar view lands in PRD 026 Phase 4 referencing the
  pick made here, not in this PRD.

## Calendar view requirements

Derived from `docs/architecture/reservation-edit.md` and
`conflicts-and-events.md`, restricted to the view-only slice.

**Must-have:**

1. **Week view** (7-day grid, time axis) and **month view**
   (date cells with event chips).
2. **OSS license** compatible with AGPL / Apache 2.0. MIT,
   Apache 2.0, BSD, LGPL all fine. GPL v2/v3 only if rapla
   accepts dropping the Apache 2.0 side of its dual licence —
   recommendation is to rule GPL out.
3. **Custom block content** — appointment blocks render title
   + owner + classification colour (the equivalent of Swing's
   `RaplaBuilder.colorize`). A render hook returning full HTML
   is required.
4. **Event-click handler** — fires with the clicked event's
   data; the handmade editor opens from this.
5. **View switcher + navigation** (prev / next / today, week
   ⇄ month).
6. **Locale** (German is the dominant locale) and
   **first-day-of-week** configurable.
7. **Compatible with Angular 21** — either a native Angular
   package or a framework-agnostic library that wraps cleanly
   inside an Angular component.
8. **Active maintenance** — release within the last 12 months.

**Nice-to-have:**

9. Day view as a third option.
10. Keyboard navigation + a11y annotations on the grid.
11. Bundle ≤ 100 KB compressed (view-only is much lighter than
    a full scheduler).
12. Time-range navigation hook (for pre-fetching events on the
    visible window — pairs with `/edit/expand-blocks` in
    PRD 026 §B4).

**Explicitly NOT required (out of scope):**

- Multi-resource axis / timeline / `rowHeaderColumns`.
- Drag-resize, drag-create, drag-move.
- Conflict overlay during drag.
- Virtualised resource scrolling.

## Calendar view candidates (OSS, screened)

With drag-edit and resource view out of scope, several
libraries previously screened out come back into play.

| Library | Licence | Week+Month | Custom block render | Bundle | Active | Verdict |
|---|---|---|---|---:|---|---|
| **EventCalendar (vkurko/calendar)** | MIT | ✅ TimeGrid + DayGrid | ✅ `eventContent` → `{html}` | ~35 KB br | v5.7.0 Apr 2026 | **finalist** |
| **FullCalendar core** | MIT | ✅ (only Resource views are paid — irrelevant here) | ✅ `eventContent` slot | ~90 KB min | active | **finalist** — biggest ecosystem |
| **Schedule-X core** | MIT | ✅ (premium features irrelevant) | ✅ event content slot | moderate | active | **finalist** — most modern API |
| angular-calendar (mattlewis92) | MIT | ✅ exactly month/week/day, nothing else | ✅ template projection | small | active (Angular 20.2+) | viable fallback |
| TOAST UI Calendar | MIT | ✅ | ✅ | n/a | maintenance unclear | fallback only |
| DayPilot Lite | Apache 2.0 | ✅ | ✅ | ~60 KB min | active | viable; no clear advantage under view-only scope |
| DHTMLX Scheduler Standard | **GPL v2** | ✅ | ✅ | ~120 KB min | active | **out on license** — forfeits rapla's Apache 2.0 dual-licence side |
| FullCalendar Premium / Schedule-X Premium / etc. | commercial | — | — | — | — | **out (commercial)** |

**Top 3 to bake off:** EventCalendar, FullCalendar core,
Schedule-X core.

## Component library decision

**Picked: Angular Material (MIT)** — decided 2026-05-12, no
bake-off run.

Reasoning:

- **A11y is first-class.** Every component ships with correct
  ARIA roles, keyboard interaction patterns, and focus
  management. Rapla has public-sector deployments where WCAG
  2.2 AA matters; Material reduces remediation work for
  audits that PrimeNG / Taiga / NG-ZORRO would require us to
  do by hand.
- **Maintained by the Angular team.** Tracks Angular 21
  release cadence directly; no risk of falling behind on a
  major Angular bump.
- **Curated component set.** ~35 components is smaller than
  PrimeNG's 80+ but covers every rapla need (forms, dialog,
  table, snackbar, date/time picker, tabs, tree). The smaller
  surface area is a feature — less bundle bloat, less drift
  risk.
- **CDK Table is a toolkit, not a finished grid.** This is
  Material's main weakness vs. PrimeNG's DataTable. Acceptable
  trade-off for view-list rendering (sort + paginate + filter
  are documented patterns); revisit only if a future admin
  list needs advanced data-grid features that justify a paid
  add-on or a switch.
- **Theming via CSS variables** plays cleanly with whatever
  calendar lib wins Phase 1.

Considered and not picked:

- **PrimeNG** — broader, faster initial data-grid wins, but
  weaker a11y defaults and the larger surface area would have
  to be policed.
- **Taiga UI / NG-ZORRO / Clarity** — viable but smaller
  ecosystems; nothing they offer outweighs Material's a11y +
  Angular-team-maintained advantage.

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

The supported shape is **one calendar lib + Angular Material**.
They cover different surface area: the calendar is a single
widget inside one route, Material wraps everything else.
CSS-variable themes can be aligned with a few overrides; the
only real seams are z-index (calendar popovers vs.
`MatDialog` overlay) and date-picker style drift (calendar
header nav vs. `MatDatepicker` in the editor).

**No second general component library.** Adding PrimeNG /
Taiga / NG-ZORRO alongside Material is an anti-pattern — two
button styles, two dialog stacks fighting for focus and
z-index, two `ControlValueAccessor` adapters for the same
widget, two date pickers, two icon sets, roughly doubled
common-bundle weight. If a missing Material component bites
us later, prefer building it on `@angular/cdk` primitives over
adding a second library.

## Plan

### Phase 1 — calendar view bake-off (target: 1.5 days)

Build the same slice three times under
`rapla-angular/src/app/bakeoff/` (gitignored, deleted at end):

> "Render 50 mock events on a week grid + month grid. Click
> event → `console.log` the event id. Custom block renders
> title + owner + a coloured left-border based on a mock
> `classification` field. View switcher (week ⇄ month).
> Locale `de`, first day of week Monday."

Build it in **EventCalendar**, **FullCalendar core**, and
**Schedule-X core**.

Score each on:

- Time-to-first-render with real Angular 21 + signals.
- API ergonomics (declarative inputs vs. imperative
  `new Calendar(el, opts)`).
- Custom-block render fidelity (HTML escape hatch, performance
  with 50 rendered blocks).
- Theming primitives (CSS vars vs. inline-style vs. opinionated
  CSS bundle) — must coexist with Angular Material's tokens.
- Locale + first-day-of-week wiring.
- Bundle impact (`ng build --configuration=production`, compare
  `main.*.js` gzipped delta on top of the Material baseline).
- Angular integration depth (typed inputs, signal compat, SSR
  not relevant).
- Docs + examples coverage of the rapla-shaped use case.

Write the scorecard at the bottom of this PRD. Decide.

### Phase 2 — wire-up (target: 0.5 day)

- `npm install @angular/material @angular/cdk` and the calendar
  pick.
- Replace the Phase 0 plain-HTML reservation list with
  `mat-table` + `MatSort` + `MatPaginator` (reading from
  `GET /storage/resources` + appointment query, unchanged).
- Wire `MatDialog`; row-click opens a stub editor dialog (a
  single read-only `mat-form-field` with title, no save). Full
  editor lives in PRD 026.
- Add `MatSnackBar` for save / error toasts (used later by
  PRD 026 phases 2+).
- Confirm `ng build --configuration=production` and a
  same-origin load through `http://localhost:8051/rapla/app/`
  still work.
- Delete `rapla-angular/src/app/bakeoff/`.
- Update PRD 026 Phase 1 plan to reference Material + the
  picked calendar lib by name.

No calendar code lands in this PRD — the calendar view is
wired in PRD 026 Phase 4, using the library chosen here.

## Tests

- One tier-3 MockMvc test in rapla-app confirming
  `GET /app/index.html` still returns 200 after the bundle
  grows — guards the static-resource path against silent break
  under a larger SPA payload.
- Two `vitest` tests on the wire-up:
  - Mounts `mat-table` with a fixed dataset, asserts rendered
    row count and `MatSort` behaviour.
  - Mounts a `MatDialog` host, fires open + ESC, asserts close.
- No tests for bake-off code (the `bakeoff/` directory is
  deleted before merge).

Smoke test by `mvn -pl rapla-app -am spring-boot:run -Dspring-boot.run.fork=false`
+ `cd rapla-angular && ng build --watch` per PRD 026
§Dev-mode wiring, then load `http://localhost:8051/rapla/app/`,
log in, verify the list + stub dialog round-trip works.

## Open questions

1. **Bundle budget for production `main.*.js`.** Phase 0
   prototype is ~250 KB gzipped; Material + calendar lib
   pushes it to 500 KB – 1 MB. Soft proposal: **1 MB gzipped**
   including the generated typed client. Confirm before
   Phase 1 starts.
2. **Date-picker coexistence.** `MatDatepicker` is the editor's
   date input; the calendar lib has its own navigation header.
   Accept two visual styles, or override the calendar's header
   to use `MatDatepicker`? Decide during Phase 1.
3. **Calendar lib's CSS bundle vs. tree-shaking.** Some
   candidates ship a single CSS file ("import once, get
   everything"); others provide per-view CSS imports. Note
   any awkward all-or-nothing imports in the bake-off scorecard.
4. **Material theme choice.** Material 3 ships with several
   prebuilt themes (`azure-blue`, `cyan-orange`, …) and a
   custom-theme generator. Default to a prebuilt for v1;
   pick the colour scheme during Phase 2 wire-up. Branding
   work is out of scope.

## Decisions

| Decision | Pick | Date | Rationale |
|---|---|---|---|
| Component library | **Angular Material** | 2026-05-12 | A11y first-class, Angular-team-maintained, curated component set covers all rapla needs; see §Component library decision |
| Calendar view library | _TBD_ | — | Filled by Phase 1 scorecard |

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
