# Calendar rendering — block building, lanes, scale, selection

Rules extracted from the legacy calendar renderers (Swing client + server HTML
export pages), verified against source 2026-07-08. These are the ground truth for
the SPA's block-based render modes (month/week grids and future day/program) —
PRD 100 tracks the SPA-side unification; PRD 095 shipped the month slice, the week
grid prototype landed 2026-07-08 (PRD 077).

**The core design principle worth copying:** all block/lane/color logic lives ONCE
in `rapla-core` (`org.rapla.components.calendarview.*`,
`org.rapla.plugin.abstractcalendar.*`) and is consumed unchanged by BOTH the Swing
client views and the server-side HTML export pages (`HTMLWeekViewPage`,
`HTMLCompactWeekViewPage`, `HTMLMonthViewPage`, …). Swing and the exported HTML
calendars render identically because they share the machinery. The SPA equivalent:
shared pure-TS modules (`week-lanes.ts`, the PRD 100 `block-style.ts`) consumed by
every grid component, with the server keeping color/§12 authority.

## 1. Block building & coloring

- **Builder:** `RaplaBuilder` (rapla-core, `plugin/abstractcalendar/`) expands
  appointments into `RaplaBlock`s for a date window and carries the build context
  (selected allocatables, permissions, colors).
- **Color resolution order** (`RaplaBuilder.getColorForClassifiable` +
  `BlockColors.resolve`, rapla-core `plugin/calendarview/`): the reservation's own
  color annotation wins; otherwise the first color-bearing allocatable's color.
  The SPA gets the *effective* color as the §12-gated `AppointmentBlock.color`
  GraphQL field (PRD 095 D2/D3 — unreadable contributor ⇒ color nulls, block
  stays); clients never re-derive colors.
- **Text on blocks is ALWAYS black**: `SwingRaplaBlock.FOREGROUND_COLOR =
  Color.black` (`SwingRaplaBlock.java:111`). There is no luminance-based flip —
  deployments choose block colors that read with black text, and every renderer
  (Swing, HTML export, SPA) must agree. PRD 100 D1; resolves PRD 095 OQ2.
- Special block states tint the *background*, not the text: exceptions, conflicts,
  not-visible/anonymous blocks, request-state blocks (alpha-adjusted colors in
  `SwingRaplaBlock`).

## 2. Lane (slot) model — how overlapping blocks get columns

One day column contains N **slots** (lanes). Assignment is a strategy pipeline in
`AbstractGroupStrategy.getSortedSlots` (rapla-core `components/calendarview/`):

```
group(blocks)                  # strategy-specific initial grouping
→ resolveConflicts(groups)     # if enabled: colliding blocks within a group are
                               #   pushed into a NEW slot inserted right after it
→ mergeSlots(groups)           # UNLESS fixedSlots: greedy merge of slots whose
                               #   blocks don't collide (fewer lanes, "compact")
→ sortSlotsBySize (optional)
```

- **Collision test** (`isCollision`): interval overlap with a **5-minute minimum
  block length** — zero-length blocks still claim lane space.
- **`GroupAllocatablesStrategy`** (the week-view default): initial grouping is
  **one group per SELECTED allocatable** — the grouping key of a block is its
  first *selected* allocatable (`RaplaBuilder.BlockContext.getGroupAllocatable`,
  `RaplaBuilder.java:813`); only when the block matches no selected allocatable
  does it fall back to its own first allocatable (**resources before persons**,
  `RaplaBlockContext` ctor comment "Prefer resources when grouping"); blocks
  with none land in a shared no-allocatable group appended LAST. Groups are
  ordered by locale-collated allocatable name (`NamedComparator`). ⇒ Lanes are
  stable per selected resource: room A is the same lane on every day of the week.
- **The selected-match is a QUERY-LAYER BINDING, not an id intersection**
  (`RaplaBlockContext.addAllocatables`, `RaplaBuilder.java:763`): a block matches
  a selected allocatable iff `bindings.getAppointments(alloc)` contains its
  appointment — the storage query resolves **belongsTo hierarchies**, so a
  lecture in a room matches its selected BUILDING. A building selection thus
  groups ALL its blocks into ONE group → `resolveConflicts` → dense greedy
  columns (this is what the Swing + exported-HTML screenshots show; the
  selection was building + person). Client renderers cannot derive this from
  row cells — the SPA gets it as a server-computed **`matchedBy`** field (NO
  argument) returning the matched SELECTED allocatables (match provenance, PRD 100
  Phase 5, shipped 2026-07-09). Its candidate pool is the QUERY'S OWN resolved
  allocatable scope (`allocatableIdsIn`/`allocatableMatching`), so it can't diverge
  from the filter that selected the block; the client groups lanes by
  `matchedBy[0]`, empty ⇒ compact. The binding test itself is the ONE shared
  primitive `AppointmentMapping.getMatchingAllocatables(appointment, candidates)`
  (rapla-core): both this loop (`RaplaBlockContext.addAllocatables`) and the
  `matchedBy` resolver call it, over the SAME `AppointmentMapping` the query builds
  via `queryAppointmentsSync` — Swing and server can't drift. Note: distinct from
  `AppointmentBlock.allocatables(filter:)`, which filters the block's OWN reserved
  resources by an independent predicate (no belongsTo, not query-scoped).
- **Fixed vs compact** (`CalendarOptions.isCompactColumns()`):
  - *fixed slots* (`setFixedSlotsEnabled(true)`, the default when not compact):
    the per-selected-resource groups are NOT merged — each selected resource keeps
    its lane even on conflict-free days.
  - *compact*: `mergeSlots` collapses non-colliding lanes greedily — minimal lane
    count, lanes lose resource identity.
  - **`isCompactColumns` is dead config in practice** (verified 2026-07-09):
    `CalendarOptionsImpl` hardcodes it `false`, the `COMPACT_COLUMNS` key is never
    parsed from config, and the Swing options panel offers no toggle. Effective
    Swing week-view behavior: **fixed whenever any allocatable is selected;
    compact only as the empty-selection fallback**
    (`compactColumns = isCompactColumns() || allocatables.isEmpty()`). Don't
    confuse this with the separate "Week/Resource" *compact week* view
    (`compactweekview` plugin — one ROW per resource, own hardcoded strategy).
- **Per-view strategy configuration** (all verified in source):

| View | Strategy | fixedSlots | resolveConflicts | Source |
|---|---|---|---|---|
| Swing week | `GroupAllocatablesStrategy` | `!compactColumns` | true | `CalendarWeekViewPresenter:187` |
| HTML export week | `GroupAllocatablesStrategy` | `!compactColumns` | true | `HTMLWeekViewPage:85` |
| HTML compact week (resource rows) | `GroupAllocatablesStrategy` + `setAllocatables(selected)`; single-group subclass when nothing selected | true | false | `HTMLCompactWeekViewPage:81` |
| Timeslot compact | (group strategy) | true | false | `HTMLCompactViewPage:89` |
| HTML month | `GroupStartTimesStrategy` (slots by start time within the day cell) | — | — | `HTMLMonthViewPage:52` |
| generic best-fit | `BestFitStrategy` (single group ⇒ pure compact packing) | — | — | `BestFitStrategy` |

- **SPA mapping (2026-07-08):** `week-lanes.ts` implements pure overlap greedy ≈
  compact mode without resource grouping. PRD 100 Phase 2 ports the
  selected-resource grouping + fixed/compact modes + 5-min floor. The SPA month
  grid deliberately does NOT use per-day slots — it renders EventCalendar-style
  spanning bars (PRD 095, `month-chunks.ts`), a locked divergence from
  `HTMLMonthViewPage`.

## 3. Time scale — rows per hour, worktime, excluded days

`LinearRowScale` (rapla-client `components/calendarview/swing/scaling/`):

- **`rowsPerHour`** is BOTH the selection raster AND the vertical zoom: pixel
  height of an hour = `rowSize × rowsPerHour`. More rows per hour ⇒ finer snap
  AND taller hours. Defaults: Swing scale 4, HTML week presenter 2.
- Persisted per user in **`CalendarOptions`** (preferences), alongside
  **worktime** (`getWorktimeStartMinutes`/`getWorktimeEndMinutes` — the rendered
  axis) and **`excludeDays`** (hide e.g. weekends from the week grid).
- `offsetMinutes` shifts the day boundary (blocks before the offset count to the
  previous day — `AbstractGroupStrategy.getBlockMap`).
- `VariableRowScale` exists for non-linear axes (per-period rows); the SPA doesn't
  need it yet.
- **SPA mapping:** week grid has a raster select (snap + gridlines only, no zoom,
  not persisted) and auto-fits the axis to data (default 8–18, expands). PRD 100
  D4/Phase 3 brings zoom + persistence; auto-fit stays as the no-config fallback.

## 4. Selection — creating from the grid

`SelectionHandler` (rapla-client `components/calendarview/swing/`):

- **FLOW strategy** (week/day views): a drag selects ONE continuous datetime
  interval. The anchor cell is fixed; dragging to a later slot/row makes the
  anchor the start, to an earlier one makes the pointer the start — **the anchor
  cell always stays inside the selection**. Rendering: first day from anchor to
  day end, intermediate days fully, last day from day start to the pointer
  (`setSelectionFlow`).
- **BLOCK strategy**: rectangular day×row selection (compact views).
- Selection row indices are clamped to `rowsPerDay - 1`; the view auto-scrolls to
  follow the pointer (`m_wv.scrollTo`).
- In Swing the selection PERSISTS after mouse-up; creation happens via the
  context menu on the selection (`fireSelectionPopup`). **The SPA deliberately
  diverges** (PRD 095 3a, PRD 100 D5): releasing the drag opens the event sheet
  immediately, prefilled with the interval; nothing persists until Speichern.
- **Editing opens on DOUBLE-click, everywhere.** Swing blocks and the SPA table
  rows (`onRowDblClick`) edit on double-click; single click selects. Block chips
  follow the same rule (PRD 100 D6) — a one-click editor would steal the click
  the selection/context-menu concept needs.
- **Context menus are ONE shared concept across table AND calendar views.** In
  Swing every view's popup — table rows, calendar blocks, empty slot selections —
  funnels through the same `SelectionMenuContext` + `ObjectMenuFactory` extension
  chain; a block popup and a table-row popup on the same reservation offer the
  same actions. SPA equivalent: the `RowContext` subject extraction + shared row
  menu (PRD 094 D4, PRD 099) that the table views already use — block chips and
  grid selections must feed THAT system (chip → same `RowContext` as its table
  row; standing selection → a time-scoped creation context), never a
  renderer-private menu.
- **SPA mapping:** FLOW is ported 1:1 in `WeekGridComponent` (cross-day verified
  2026-07-08); the month grid's day-range drag-create is the day-granular
  analogue. Auto-scroll is pending (PRD 100 Phase 4).

## 5. Dragging existing blocks

`DraggingHandler` (rapla-client, same package) moves/resizes existing blocks;
`RaplaBlock.isMovable()` gates it (`canModify` + not an exception occurrence).
The SPA plan (PRD 095 Phase 3b, D6): drag gate = `canModify && appointmentCount
=== 1 && appointment.repeating == null` read from the builtin view's hidden
fields — fail-closed when a custom view omits them; the server re-checks in
`moveReservations`. Repeating/multi-appointment blocks are not draggable until
the occurrence-vs-series dialog exists (PRD 094/091).
