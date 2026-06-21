# PRD 079 — GraphQL grouped aggregates (utilization analytics)

**Status:** draft (design). Carved out of the PRD 074 table-view work (2026-06-21) when global
totals (`extensions.view.totals`) landed and the next ask was *grouped* analytics — "how many
hours per week is a room utilized over a year". 074 owns the flat table + per-row fields + global
totals; **this PRD owns group-by + bucketed aggregation.**

## Motivating use cases (from the user, 2026-06-21)

1. **Global total** — total duration of all queried blocks. ✅ *already shipped* in PRD 074 as
   `extensions.view.totals { count, wallClockMinutes, wallClockHours, unitMinutes, unit }` over the
   full matched set (O(1) memory in the block loop). This PRD does **not** re-do that.
2. **Grouped/bucketed** — *hours per week per room over a year*: `group by (ISO-week, room)` →
   sum a duration metric per bucket. Not expressible today (the block read is flat; `compute` is
   per-row; totals are global). **This is the PRD.**

## What's already in place (reuse, don't rebuild)

- **Metrics both exist and are cheap.** Wall-clock = `Duration.between(start,end)`; teaching-unit
  (UE) = `EventTimeModel.calcDuration(block)` (break-adjusted, eventtimecalculator plugin). Both
  are summed incrementally in the existing `appointmentBlocks` loop for the global totals — the
  same accumulation generalises to per-bucket accumulation.
- **§12 is handled upstream.** Buckets are built from the already-`canRead`-gated `visible`
  reservation set, so an aggregate can never count a block the caller can't see. The leak rule
  still applies to **bucket keys**: a `roomId`/`roomName` key must be a readable allocatable
  (drop the bucket otherwise — never emit an id/name the caller couldn't get via the table).
- **Block → allocatables** resolver (`appointmentBlockAllocatables`, §12 + per-appointment
  restriction) gives the room/person dimension per block.

## Proposed surface (open — two candidate shapes)

### Shape A — dedicated stats query (lean, OLAP-style)

```graphql
appointmentBlockStats(
  filter: ReservationFilter!,
  groupBy: [BlockGroupKey!]!,          # e.g. [ISO_WEEK, ALLOCATABLE]
  allocatableScope: AppointmentAllocatableFilter,   # which dimension allocatable (e.g. rooms)
  metric: BlockMetric = WALL_CLOCK     # WALL_CLOCK | UNIT | COUNT
): [BlockStatBucket!]!

type BlockStatBucket {
  keys:    [BucketKey!]!   # one per groupBy dim, in order
  count:   Int!
  minutes: Int!
  hours:   Float!
  unit:    String          # formatted UE total when metric/plugin applies
}
type BucketKey { dim: BlockGroupKey!, value: String!, label: String }  # value=isoWeek/allocatableId
enum BlockGroupKey { DAY  ISO_WEEK  MONTH  YEAR  ALLOCATABLE  RESERVATION_TYPE }
enum BlockMetric   { COUNT  WALL_CLOCK  UNIT }
```

- One row per non-empty bucket; server-side accumulation, bounded by *bucket count* (not block
  count). A year × 1 room × ISO-week = 52 buckets — tiny.
- `ALLOCATABLE` grouping needs a scope (which allocatables form the dimension) — reuse
  `AppointmentAllocatableFilter` (`typeKeyIn:["room"]`).

### Shape B — render-meta `@group` directive on the existing table (consistent with PRD 074)

```graphql
query Auslastung @view(title:"Raum-Auslastung") {
  appointmentBlocks(filter: {...}) {
    week:  start @group(by: ISO_WEEK) @column(header:"KW")
    room:  allocatables(filter:{typeKeyIn:["room"]}) @group @column(header:"Raum") { name }
    hours: duration @aggregate(fn: SUM_WALLCLOCK) @column(header:"Stunden")
  }
}
```

- Emits buckets in `extensions.view.groups` (or rows collapsed server-side). Keeps one query
  surface (the table *is* the analytic), aligns with the `@view`/`@column` model.
- Heavier to implement (directive-driven grouping + aggregation pass over the result).

**Lean recommendation:** Shape A for the analytic use case (clean, testable, OLAP-shaped), keep
Shape B as a future ergonomic layer if the SPA wants "table that collapses into a summary".

## Open questions

1. **A vs B** (dedicated query vs `@group`/`@aggregate` directives). Lean A first.
2. **Time bucketing** — ISO week (ISO-8601, Mon-start) vs locale week; blocks crossing a
   week/day boundary (split across buckets vs assign-to-start). Highest-drift decision.
3. **Metric default + plurality** — one metric per call vs always emit count+wallclock+unit.
4. **Allocatable dimension scope** — required `allocatableScope`, or derive from groupBy?
5. **§12 bucket-key suppression** — drop unreadable-allocatable buckets vs merge into "other".
6. **Empty buckets** — omit (sparse) vs zero-fill a calendar range (dense, for charts).
7. **Cost guard** — max buckets / max blocks scanned; `log()`-style truncation signal.

## Out of scope

Global totals (done, PRD 074). Charts/visualisation (SPA, future). Persistence of saved analytic
views (PRD 077). The function-catalog/descriptor-SPI (PRD 073).

## Tests (when implemented)

- Tier 2/3 — known fixture: N blocks in 2 weeks × 2 rooms → exactly the expected buckets + sums.
- §12 — non-admin caller: buckets never include an unreadable room's id/name; sums exclude its blocks.
- Bucketing edge — a block spanning a week boundary lands per the decided rule.
- Metric parity — `WALL_CLOCK` bucket sums equal the sum of per-block `Duration.between`.
