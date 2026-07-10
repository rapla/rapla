# PRD 100 — SPA block-renderer unification (month/week grids, Swing parity)

**Status:** in progress — 2026-07-08. Phases 1+2 SHIPPED (shared `block-style.ts`,
black chip text browser-verified on the light-color cases, Swing lane pipeline in
`week-lanes.ts` with the full tier-5 suite — 34 tests). D6 added: chip double-click
opens the editor (Swing/table parity). Phases 3+4 open.
**Related:** [PRD 077](077-calendar-model-graphql.md) (calendar model — owns week/month render modes; the week grid
shipped as a prototype under this PRD's scope), [PRD 095](095-month-grid-render-mode.md) (month grid — its OQ2 chip
contrast is resolved here as D1), [PRD 032](done/032-angular-ui-library-evaluation.md) (own-implementation calendar decision,
EventCalendar MIT reference rule), [PRD 094](094-spa-main-view-actions-and-popups.md) (context actions on blocks/selections —
explicitly out of scope here)

## Abstract

The SPA now has two block-based calendar renderers — `MonthGridComponent` ([PRD 095](095-month-grid-render-mode.md))
and the week time-grid prototype (`WeekGridComponent`, 2026-07-08) — each carrying
its own copy of chip styling, color handling, and time/name composition, and the week
grid diverges from the Swing week view in documented ways. This PRD (a) extracts the
shared block-rendering logic into one module — the Swing analog is
`SwingRaplaBlock`/`RaplaBuilder`/`BlockColors`, shared across all Swing/HTML calendar
views — and (b) closes the Swing-parity gaps found in the 2026-07-08 source
comparison, locking which divergences are deliberate. End state: one
`block-style.ts` consumed by every block renderer, black-text chips everywhere, the
week grid's lane model matching Swing's fixed-slots/compact semantics, and a tier-5
suite over all extracted pure logic.

## Ground truth — Swing vs. SPA comparison (2026-07-08, verified against source)

> The full rule set extracted from the Swing/HTML renderers — including the HTML
> export pages, which share the exact same rapla-core strategy machinery — lives in
> [`docs/architecture/calendar-rendering.md`](../architecture/calendar-rendering.md).
> The table below is the diff view driving this PRD's phases.

| Aspect | Swing | SPA today | Verdict |
|---|---|---|---|
| Chip text color | **Always black** (`SwingRaplaBlock.FOREGROUND_COLOR = Color.black`); users pick block colors that work with black text | White text on colored chips, dark on neutral — unreadable on light colors (yellow/lime, see 2026-07-08 screenshot) | **Unify → D1** |
| Block color resolution | `RaplaBuilder.getColorForClassifiable` + `BlockColors.resolve`, shared by ALL views | Server `AppointmentBlock.color` ([PRD 095](095-month-grid-render-mode.md), delegates to the same helpers, §12-gated) — correct; but client-side chip styling duplicated per grid | **Extract → D2** |
| Lane assignment (week) | `GroupAllocatablesStrategy` (+`AbstractGroupStrategy`): group blocks by their first **selected** allocatable (locale-sorted; selection = the calendar's chosen resources); `resolveConflicts` spawns extra slots on collision; **fixed-slots** mode (default when not compact) keeps each selected resource's lane stable across the week; `mergeSlots` (compact) greedily collapses non-colliding lanes | Pure overlap greedy (interval partitioning) ≈ Swing's *compact* mode only; lanes reshuffle day-by-day, no resource identity | **Unify → D3** |
| Collision floor | 5-minute minimum block length in `isCollision` (zero-length blocks claim lane space) | `endMin <= startMin` clamped to +30 min | **Unify → D3** |
| Rows-per-hour | `CalendarOptions.getRowsPerHour()` — per-user persisted; **also a zoom**: hour height = `rowSize × rowsPerHour` (`LinearRowScale`) | Component-local signal; raster affects gridlines + selection snap only, hour height fixed 48 px | **Unify → D4** |
| Worktime / excluded days | `CalendarOptions` worktime start/end minutes + `excludeDays` (hide weekends) | Auto-fit axis (default 8–18, expands to data); always 7 days | **Partial-unify → D4** (auto-fit stays as fallback) |
| Cross-day selection | `SelectionHandler` FLOW: anchor-swap, intermediate days fully selected, one continuous interval | Ported 1:1 (2026-07-08, browser-verified) | ✅ done |
| Selection → creation | Selection persists after mouse-up; creation via context menu (`fireSelectionPopup`) | Release opens the prefilled event sheet immediately ([PRD 095](095-month-grid-render-mode.md) 3a decision) | **Deliberate divergence → D5** |
| Auto-scroll during drag | `m_wv.scrollTo` follows the pointer | missing | Phase 4 |
| Selection raster clamp | row clamped to `rowsPerDay-1` | clamped to axis bounds | ✅ done |

## Implementation

**Shared module** `rapla-angular/src/app/views/block-style.ts` (Phase 1): pure
functions/constants consumed by `MonthGridComponent`, `WeekGridComponent`, and any
future block renderer (day/program):
- `chipColor(row): string | null` — reads the §12-gated `color` field, null → neutral.
- `CHIP_TEXT_COLOR` — black (D1); one constant, no per-chip computation.
- `chipTime(row)`, `chipName(row)` — `times`-field-with-`start`-fallback + name.
- Chip base CSS (border-radius, padding, ellipsis) as a shared style constant or a
  `.chip` mixin both components import — the *look* of a block is view-independent,
  exactly like `SwingRaplaBlock` is view-independent in Swing.

**Week lane parity** (Phase 2) in `week-lanes.ts` (stays pure, no DOM):
- Port `GroupAllocatablesStrategy` semantics — grouping is **over the SELECTED
  resources only** (`RaplaBuilder.getGroupAllocatable`, `RaplaBuilder.java:813`):
  a block's grouping key is its first allocatable *that is in the calendar's
  selection* (SPA: the scope chips); only a block matching no selected resource
  falls back to its own first allocatable; blocks with none go to a shared
  no-allocatable group appended last. Groups locale-sorted; `resolveConflicts`
  within groups; `mergeSlots` for compact mode.
- Fixed-slots vs compact as a mode flag; Swing chooses via
  `CalendarOptions.isCompactColumns()` — SPA default: compact (current behaviour),
  fixed-slots when >1 resource is scoped (OQ2).
- 5-minute collision floor (`isCollision` parity).

**Setting plumbing** (Phase 3): rows-per-hour becomes zoom (hour height =
`rowSize × rowsPerHour`) and persists; worktime + excludeDays as options with
auto-fit-to-data as the no-config fallback. Persistence home is OQ1 ([PRD 077](077-calendar-model-graphql.md)'s
SavedView options vs user preferences).

## Goal

- Zero chip-styling duplication: both grids import `block-style.ts`; a grep for
  hex text-colors inside the grid components finds nothing.
- The yellow/lime chips from the 2026-07-08 screenshot render with black text.
- Week grid with 3 scoped rooms shows each room in a stable lane across all days
  (fixed-slots mode), matching the Swing week view over the same data.
- `week-lanes.spec.ts` + `block-style.spec.ts` tier-5 suites green.

## Scope

### In scope
- `block-style.ts` extraction + black-text rule in both grids.
- Week lane model parity (grouping, fixed/compact, collision floor).
- Rows-per-hour as zoom + persistence; worktime/excludeDays options.
- Tier-5 tests for all extracted pure logic (also covering `week-lanes.ts`'s
  existing window math + FLOW selection helpers, which shipped untested).
- Control-strip week label (`KW n · DD.–DD.MM.`) replacing the raw window range.
- Doc: "render mode → field contract" table in
  `docs/architecture/tableview-and-graphql-views.md` (which fields each render mode
  consumes, fail-closed degradation when a custom view omits one).

### Out of scope
- Drag-move of existing blocks ([PRD 095](095-month-grid-render-mode.md) Phase 3b for month; week follows after).
- Context menus / actions on blocks and selections — [PRD 094](094-spa-main-view-actions-and-popups.md). But the *shape* is
  locked here: context menus are ONE shared concept across table and block
  renderers (Swing: `SelectionMenuContext` + `ObjectMenuFactory` serve table rows
  AND calendar blocks alike). Block chips must produce the same `RowContext` as
  their table row and feed the existing shared row-menu path ([PRD 094](094-spa-main-view-actions-and-popups.md) D4/099) —
  no renderer-private menu implementations.
  *Shipped 2026-07-08 (single-row slice):* chip right-click → `openMenu {row,x,y}`
  → view-host `onChipMenu` → the shared `ctx-anchor` menu via `rowItems`
  (Bearbeiten/Anzeigen/Löschen incl. delete-scope); chip double-click →
  `onRowDblClick` (D6). Still [PRD 094](094-spa-main-view-actions-and-popups.md): multi-block selection + selection-context
  menus + menu on a standing time selection.
- Day/program render modes themselves (only: the shared module must not assume 7
  columns).
- Server changes — the wire contract (color, appointment, appointmentCount) is
  complete for this PRD.
- Migration of stored custom views declaring `renderModes: [week]` that intended the
  grouped day-list (now `day` after the 2026-07-08 mode shuffle) — dhbwrapla sweep,
  coordinated separately.

## Plan

### Phase 1 — shared block-style module + black text (DONE 2026-07-08)
- [x] Tier-5 `block-style.spec.ts`: color extraction (string/null/non-string),
      time fallback chain, name coercion, CHIP_TEXT_COLOR = black.
- [x] `block-style.ts` (chipColor/chipTime/chipName + `CHIP_BASE_CSS` shared into
      both grids' styles arrays); chip text always black (D1).
- [x] Browser-verified on the dhbw light-color cases (lime/crimson chips readable).

### Phase 2 — week lane Swing parity (DONE 2026-07-08)
- [x] Tier-5 `week-lanes.spec.ts` (17 tests): `mondayOf`/window math incl. year
      wrap, day clipping + continuation flags, packing shapes, 5-min collision
      floor, group-by-SELECTED-resource fixed lanes (incl. empty-lane reservation
      + stable lanes across days + conflict-lane insertion), compact merge,
      fallback + no-allocatable trailing group.
- [x] Faithful port of `resolveConflicts`/`canMerge`/`mergeSlots`/
      `getGroupAllocatable` into `week-lanes.ts`; `LaneOptions {selected, mode,
      allocsOf}`. View-host passes locale-sorted scope resources; mode = fixed
      when >1 resource scoped (OQ2 default), else compact.
- [x] D6: chip DOUBLE-click opens the editor in both grids (single click reserved
      for selection/[PRD 094](094-spa-main-view-actions-and-popups.md)); month-grid spec pins the contract.

### Phase 3 — options: zoom + worktime
- [ ] Rows-per-hour drives hour height (zoom) and persists (OQ1).
- [ ] Worktime + excludeDays options; auto-fit stays as fallback.

### Phase 5 — server-computed lane matching (OQ4 resolution; SYNC-BY-SEAM)
The algorithm splits at the Swing seam: **matching = Java** (single source of
truth, reusing the binding/belongsTo logic the CalendarModel uses), **grouping
pipeline = the faithful TS port** (already line-for-line + spec-pinned).

Conceptual frame: the field is **match PROVENANCE** — the scope filter decides
THAT a block is in the result; `matchedBy` records WHY, i.e. which SCOPED
allocatable admitted it (belongsTo-resolved: a lecture in a room is admitted by
the selected BUILDING, so `matchedBy` = `[building]`, not the room the block
allocates). **Name: `matchedBy`** (provenance vocabulary; `boundTo` was Swing's
internal binding term). Empty `matchedBy` ⇒ the query was unscoped OR the block
was admitted by a non-resource criterion (owner/user chip) ⇒ compact fallback.
Reuse beyond lanes: "why is this shown" debugging, chip-tinted highlighting.

**Landed 2026-07-09 (server side).** The "same code" is literal: the binding test
was lifted into `AppointmentMapping.getMatchingAllocatables(appointment,
candidates)` (rapla-core, `org.rapla.entities.domain`) — the SINGLE primitive both
Swing's `RaplaBuilder.RaplaBlockContext.addAllocatables` and the GraphQL resolver
call. Sync-by-seam is a compile-time fact, not a discipline.

**No argument — the candidate pool is the query's OWN resolved scope** (decision
2026-07-09, D7 below): `matchedBy` takes no `ids`. `reservations()` already
resolves the filter's `allocatableIdsIn`/`allocatableMatching` to a §12-gated
`AppointmentMapping` (its per-allocatable appointment sets ARE the belongsTo
bindings); it stashes that mapping in the request context ONLY when the query is
explicitly allocatable-scoped, and the resolver reads it — no second query, no
firehose when unscoped. This structurally guarantees the **filter-consistency
invariant** (`matchedBy` can't diverge from the filter that selected the block,
because it IS that filter's resolved scope) and §12 (the pool = the query's
canRead-gated allocatables, so only readable, already-scoped resources surface).

- [x] Schema: `AppointmentBlock.matchedBy: [Allocatable!]!` — no arg; navigable
      Allocatables (the builtin view selects only `{ id }` — lanes have no
      visible label, so no name). Reused the `AppointmentMapping` the query builds.
- [x] Resolver: `APPOINTMENT_BLOCK_MATCHED_BY` reads the context-stashed scoped
      mapping (`MATCHED_BY_SCOPE_KEY`) and returns
      `mapping.getMatchingAllocatables(appointment, null)`; no mapping ⇒ `[]`.
      `reservations()` stashes the mapping iff `hasIdsIn || hasMatching`.
- [x] Builtin view: `matchedBy @hidden { id }` (no `$scopeIds` variable —
      the pool comes from the same `$filter` that scopes the query).
- [x] Client (2026-07-09): grouping key = `matchedBy[0] ?? location-fallback`;
      compact iff no block has a non-empty `matchedBy` (exactly Swing's
      `builder.getAllocatables().isEmpty()` switch) — the earlier heuristics
      became the no-server-data fallback for custom views. `week-lanes.ts`
      `groupBySelected`/`layoutWeek` take a `matchedByOf`; `week-grid.component`
      reads `row.matchedBy`. No variable-binder change (no arg to bind).
      Tier-5 specs in `week-lanes.spec.ts`.
- [ ] **Cross-language contract fixtures**: one checked-in JSON set
      (blocks + selection → expected lane assignment) consumed by BOTH a Java
      test over `AbstractGroupStrategy` and the Vitest suite over
      `week-lanes.ts` — either codebase drifting goes red on the other.

### Phase 4 — polish
- [x] Minimum lane width + horizontal scroll (2026-07-09, Swing
      `SwingWeekView.updateSize`/`minBlockWidth` parity): lanes floor at 80 px
      (`MIN_LANE_PX`), busy weeks overflow the container and scroll horizontally
      instead of squeezing chips into slivers.
- [ ] Rich chip text (Swing parity): multi-line block content — time range,
      title, person names (italic), resources — when the lane is wide/tall
      enough; the persons/resources cells are already in the builtin selection.
- [ ] Auto-scroll during drag (Swing `scrollTo` parity).
- [ ] Control-strip week label.
- [ ] Field-contract table in `docs/architecture/tableview-and-graphql-views.md`.

## Tests

Tier 5: `block-style.spec.ts`, `week-lanes.spec.ts` (window, clipping, packing,
grouping, collision floor). Tier 6: month + week grid render smoke with shared
styling (black text asserted on a colored chip). Browser: yellow-chip readability
probe on the dhbw dataset.

## Open Questions

- **OQ4** — container scope chips (building/category): the server expands them
  for the QUERY, but no block carries the chip id, so client-side lane grouping
  can't match them. Interim heuristics shipped 2026-07-09: fallback lane key
  prefers the block's **location** ref; compact when NO block matches any chip
  id (proxy for Swing's empty-allocatables switch; empty week keeps fixed).
  *Resolution:* 2026-07-09 — **server-computed matching** (design locked, see
  Phase 5). Ground truth found in `RaplaBuilder.RaplaBlockContext.addAllocatables`
  (`RaplaBuilder.java:763`): Swing/HTML matching is NOT id intersection — a
  block matches a selected allocatable iff the **query-layer bindings**
  (`bindings.getAppointments(alloc)`) contain its appointment, which resolves
  belongsTo hierarchies. A building selection therefore groups ALL its blocks
  under the building itself (ONE group → resolveConflicts → the dense greedy
  columns of the Swing/HTML screenshots). Unreplicable client-side by design —
  the matching fact must come from the server.

- **OQ1** — persistence home for rows-per-hour/worktime/excludeDays: [PRD 077](077-calendar-model-graphql.md)
  SavedView options (per saved calendar, Swing-`CalendarModelConfiguration`-like) vs
  user preferences (per user, Swing-`CalendarOptions`-like). Swing splits them:
  rowsPerHour/worktime are CalendarOptions (user-level). *Resolution:* pending —
  decide with [PRD 077](077-calendar-model-graphql.md)'s SavedView design.
- **OQ2** — SPA default for fixed-slots vs compact. *Resolution:* 2026-07-09 —
  **fixed whenever ANY resource is scoped; compact only with no scope** (Swing
  effective behavior). Source finding: `isCompactColumns` is DEAD config —
  `CalendarOptionsImpl` hardcodes `compactColumns = false`, the `COMPACT_COLUMNS`
  key is never parsed, and the Swing options panel has no toggle; the week view's
  only compact path is the empty-selection fallback
  (`CalendarWeekViewPresenter:188`). The "Week/Resource" *compact week* view
  (one row per resource, `compactweekview` plugin, hardcoded strategy) is a
  separate not-yet-ported render mode, not this switch.
- **OQ3** — lane grouping key when the scope mixes persons and resources.
  *Resolution:* 2026-07-08 — follow Swing: the key is the block's first **selected**
  allocatable regardless of person/resource kind, groups ordered by locale-collated
  name (`NamedComparator`); no-match blocks fall back to their own first
  allocatable, none → shared trailing group (`RaplaBuilder.getGroupAllocatable`).

## Decisions locked

**D1 — chip text is ALWAYS black; no luminance-based white text.** Swing parity:
`SwingRaplaBlock.FOREGROUND_COLOR = Color.black` — deployments choose event/resource
colors that work with black text, and the same colors must read identically in Swing
and SPA. The SPA's white-on-color default produced unreadable chips on light colors
(yellow `Studienko…`, lime `Info der Leitung` — 2026-07-08 screenshot). Resolves PRD
095 OQ2 *against* its "client luminance check" lean: a luminance flip would make the
SAME event render white in one view and black in another and silently diverge from
Swing. Rejected: server-emitted text color (more wire surface for a constant).

**D2 — one shared block-style module, mirroring Swing's shared-block design.** In
Swing, block look and color logic live once (`SwingRaplaBlock`, `RaplaBuilder`,
`BlockColors`) and every view renders the same block. The SPA's per-grid copies have
already diverged once (text color); extraction is the fix, not discipline. Server
stays the single source of the *effective color* ([PRD 095](095-month-grid-render-mode.md) D2/D3 — §12 gating stays
server-side).

**D3 — week lanes get Swing's model: group by SELECTED resource + fixed/compact
modes + 5-min collision floor.** The stable-lane-per-selected-resource behaviour is
the genuinely rapla-ish part of the Swing week view (multi-resource planning was
the reason [PRD 032](done/032-angular-ui-library-evaluation.md) rejected off-the-shelf calendar libs) — and the grouping key is
the calendar's *selection* (SPA: scope chips), not any allocatable of the block
(`RaplaBuilder.getGroupAllocatable`). Pure-TS port into `week-lanes.ts`, Swing
`AbstractGroupStrategy`/`GroupAllocatablesStrategy` as the reference — in-house
first per [PRD 032](done/032-angular-ui-library-evaluation.md); EventCalendar stays reference-only for pointer/CSS patterns.
The identical strategy stack drives the exported HTML calendars
(`HTMLWeekViewPage:85`), so parity here keeps SPA ↔ HTML-export ↔ Swing agreeing
on the same data (see `docs/architecture/calendar-rendering.md` §2).

**D4 — rows-per-hour is a zoom AND a raster, persisted.** Swing parity
(`LinearRowScale`): more rows per hour = taller hours + finer snap. The prototype's
raster-only select was a stopgap. Worktime/excludeDays follow the same options path;
the prototype's auto-fit axis stays as the unconfigured fallback (strictly better
than Swing's blank evening rows when data falls outside worktime).

**D6 — chip double-click opens the editor; single click is reserved for
selection.** Swing and the SPA tables both edit on double-click (`onRowDblClick`);
a one-click editor on block chips was inconsistent and steals the click needed for
the shared selection/context-menu concept (PRD [094](094-spa-main-view-actions-and-popups.md)/099) that blocks will join.
Keyboard: Enter on a focused chip still activates (a11y parity with tables).

**D5 — selection→creation stays immediate (divergence from Swing, deliberate).**
Swing keeps the selection standing and creates via context menu; the SPA opens the
prefilled event sheet on release ([PRD 095](095-month-grid-render-mode.md) 3a "editor öffnet vorbefüllt", reaffirmed
for the week grid 2026-07-08). Context actions on a standing selection are [PRD 094](094-spa-main-view-actions-and-popups.md)
territory and can layer on later without changing the default.

**D7 — `matchedBy` takes NO argument; its candidate pool is the query's own
resolved allocatable scope.** (2026-07-09.) An earlier draft had
`matchedBy(ids: [ID!]!)` with the client re-sending its scope chips via a
`$scopeIds` variable. Rejected in favour of no argument, deriving the pool from the
same `$filter` (`allocatableIdsIn`/`allocatableMatching`) that already scoped the
query. Rationale:
- **Can't diverge.** `AppointmentBlock.allocatables(filter:)` takes an *independent*
  filter (the builtin view uses `isPersonEq` lane filters) — reusing it, or a
  client-sent `$scopeIds`, could attribute against a *different* set than the one
  that selected the block. Deriving from the query filter makes divergence
  impossible.
- **belongsTo is a query-scope concept, not a block-resource one.** `matchedBy`
  must return the *scoped* allocatable (the building) that admitted the block via
  belongsTo — the block only *owns* the room, so no per-block field
  (`allocatables(filter:)`) can produce it. It lives on the query's
  `AppointmentMapping`, which the resolver reuses (no second query).
- **No client plumbing, structural §12.** The pool is the query's canRead-gated
  scope set; unscoped query ⇒ no mapping ⇒ empty ⇒ compact (Swing's
  empty-selection fallback). The variable-binder needs no `[ID!]` filler.
Alternative kept in mind: an *optional* `ids` arg for callers wanting provenance
against an arbitrary set (highlighting, counts) — deferred (YAGNI) until a consumer
needs it.
