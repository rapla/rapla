# PRD 092 — Free-slot search (free time for a fixed resource set)

**Status:** draft — 2026-07-05
**Related:** PRD 091 (SPA reservation edit & resource availability — this PRD is the
complementary axis, split out of 091; shared schema vocabulary defined there),
PRD 060 (GraphQL MCP foundations — sketched `findFreeSlots`), PRD 086 (appointment
block index — the enumeration substrate), PRD 077 (calendar render mode — future
home of the availability strip), PRD 024 (server-side edit services)

## Abstract

Answer "when are room X and lecturer Y free?" properly: a GraphQL `freeSlots` query
that returns *ranked candidate times* for a fixed resource set — replacing the Swing
"free appointment >>" button, which returns only the first hit, hides its parameters
in global `CalendarOptions`, and brute-force-scans up to a year slot by slot. Serves
the SPA slot finder (use case UC-E6 in `docs/usecases/reservation-editing.md`) and
MCP clients (PRD 060).

## Current state

- Only primitive: `getNextAllocatableDate` — single appointment, first free start
  only, linear scan of up to `366 × 24 × rowsPerHour` candidate slots, each doing a
  full bindings computation (`LocalAbstractCachableOperator.getNextAllocatableDateSync`,
  ~:4451). Exposed only over the legacy Swing RPC `/api/storage/allocatable/date/next`.
- Search parameters (worktime start/end, excluded weekdays, slot granularity) come
  from global `CalendarOptions`, not from the caller.
- PRD 086's `IntervalIndex` (behind `rapla.readmodel.authoritative`) gives cheap
  per-allocatable busy-interval retrieval for a window — the substrate for a
  gap-based algorithm.

## Goal

A §12-scoped, side-effect-free GraphQL query: given resource ids, a search window,
a duration and optional constraints, return ranked candidate slots — including, for
recurring intent, weekly-pattern candidates ranked by conflict-free quota ("Tue
10–12: free in 14/15 weeks"). Measurable: ranked free slots for room+lecturer over
a semester weekday pattern in one query, sub-second on a production-shaped dataset.

## Design

### The two modes (OQ1 — the load-bearing fork)

1. **Concrete slots** — "next week, 90 min, room X + person Y" → list of concrete
   free start times. The `date/next` successor, N results instead of first hit.
2. **Pattern search** — "weekly, Tue–Thu mornings, over the term" → candidates are
   weekday×time pairs, ranked by how many weeks of the pattern are conflict-free.

The inputs overlap ~80% (resources, window, duration, worktime constraints); the
*result shapes* differ (concrete `start` vs. weekday+time with a quota). Options:
one query with optional `pattern` and a result type covering both (PRD 091's
original sketch — heterogeneous), a `@oneOf` result union, or two queries sharing
input types. **Undecided — see OQ1.**

### Sketch (input side, mode question left open)

```graphql
input FreeSlotInput {
  resourceIds: [ID!]!
  window: TimeWindow!            # shared vocabulary from PRD 091
  durationMinutes: Int!
  pattern: RecurrencePattern     # optional — see OQ1
  worktime: WorktimeInput        # start/end hour, excluded weekdays — explicit,
  limit: Int                     # NOT from global CalendarOptions
}
```

### Algorithm: gap enumeration, not grid scan

Fetch the busy intervals of all requested resources for the window **once** (block
index / `intervalConflictCandidates`), merge them, and read off the free gaps
≥ duration directly. No `rowsPerHour` raster parameter; optional snapping to
:00/:15/:30 boundaries is output cosmetics, not part of the search. This is the
performance fix over the legacy per-slot bindings recomputation. Pattern mode
groups gaps by weekday×time and counts free weeks.

Cost controls: window capped server-side (e.g. 1 year), `limit` on results,
resource count bounded.

### Ranking

- Concrete mode: chronological (earliest first) + `limit`. No attribute-fit
  ranking here — resource fit is the finder's job (PRD 091 C, OQ2 there).
- Pattern mode: quota first ("14/15"), then chronological.

### §12

Resource ids the caller cannot read are dropped silently (existence must not
leak — same-response rule for nonexistent vs. hidden ids). The response contains
only times and counts, never details of the blocking reservations.

## UI surfaces

### Slot finder (the primary; was proposal E in PRD 091)

In the event sheet's "when" section: pick window, duration, optional weekly
pattern; resource set defaults to the event's allocation → ranked list; picking a
slot sets the appointment/series and jumps to the PRD 091 matrix for residual
conflicts. All parameters in-dialog (fixes the CalendarOptions burial).

### Later increments

- **Availability strip:** while dragging an occurrence, Outlook-style mini
  free/busy lanes per allocated resource for that day — candidate for the PRD 077
  calendar render mode.
- **Weekday×hour heatmap** at term scale (When2Meet-style: cell shade = number of
  free weeks) — pure presentation over the pattern-mode result.

## Scope

### In scope
- `freeSlots` GraphQL query (both modes, pending OQ1), gap-enumeration
  implementation, §12 leak tests
- SPA slot finder in the event sheet

### Out of scope
- Resource-axis search and the assignment matrix (PRD 091)
- Availability strip + heatmap (later increments, listed above)
- Replacing/removing the legacy `date/next` RPC (Swing keeps it; separate cleanup)

## Plan

### Phase 1 — Query + algorithm
- [ ] Resolve OQ1 (mode shape), define result type(s)
- [ ] Gap enumeration over busy intervals (index fast path, legacy fallback)
- [ ] §12 leak tests (hidden resource ids; no blocking-event details)

### Phase 2 — SPA slot finder
- [ ] Finder UI in the when-axis; handoff to the PRD 091 matrix

### Phase 3 — Increments
- [ ] Heatmap (pattern mode), availability strip (with PRD 077)

## Tests

- Tier 1: gap-merge + pattern-quota functions as pure unit tests (fixture busy
  intervals; boundary cases: adjacent bookings, overnight gaps, excluded days,
  DST-irrelevant naive times)
- Tier 3: GraphQL leak tests (§12), window-cap enforcement
- Tier 5/6: finder component; tier 7 covered by PRD 091's Playwright path once
  integrated

## Open Questions

- **OQ1** — Mode shape: one query with optional `pattern` (heterogeneous result),
  `@oneOf` result union, or two queries (`freeSlots` / `freeSlotPattern`) sharing
  input types? *Resolution:* pending.
- **OQ2** — Snapping policy: raw gap starts vs. snapped to :00/:15/:30 (and who
  decides — server default, client parameter)? *Resolution:* pending.
- **OQ3** — Should pattern mode account for the deployment's `Timeslot` bands
  (archetype B, "Wochenprogramm") as candidate rows instead of free-form times?
  *Resolution:* pending.
- **OQ4** — Index dependency: require `rapla.readmodel.authoritative` or ship a
  legacy-scan fallback (mirror of PRD 091 OQ3)? *Resolution:* pending.

## Decisions locked

**D1 — GraphQL transport (2026-07-05).** Same rationale as PRD 091 D1 (SPA is
GraphQL-only, PRD 067 direction, MCP for free); inherits its rejected
alternatives.

**D2 — Gap enumeration replaces grid scan (2026-07-05).** Busy-interval merge +
free-gap readout instead of the legacy per-slot bindings recomputation; no
`rowsPerHour` in the API. The legacy `date/next` stays untouched for Swing.
