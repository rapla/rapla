# PRD 060 — GraphQL Discovery, Compute Operations, and MCP Transport

**Status:** draft — extracted from PRD 035 on 2026-05-29; design locked, MCP transport gated on Spring AI starter SB4 alignment

**Date:** 2026-05-29

**Parent:** PRD 035 (foundations) — [done/035-graphql-foundations.md](done/035-graphql-foundations.md). This PRD picks up the MCP-side work and the cross-type discovery / compute primitives that didn't ship as part of PRD 035 Phase 1-2.

**Siblings:**
- [PRD 055 — Events Read API](055-graphql-events-read-api.md) — single-type reservation reads
- [PRD 056 — Events Write API](056-graphql-events-write-api.md) — curated `book` mutation tool wraps PRD 056's `createReservation`
- [PRD 028 — Angular Power Search](028-angular-power-search.md) — substrate for the search root in §"Search & discovery"
- [PRD 043 — API Keys (JWT/PAT)](043-api-keys-jwt-pat.md) — the scoped-key mechanism the MCP transport sits on

## Goal

The original PRD 035 goal was an MCP server exposing rapla's scheduling
primitives to AI assistants. The 2026-05-15 design review broadened that to
a shared GraphQL + MCP substrate; PRD 035 Phase 1-2 shipped the structural
schema, classification generation, and the read surface for single-type
queries (PRD 055). This PRD lands the remaining surface needed before the
MCP transport itself is worth wiring: cross-type discovery (search), the
three compute primitives (`findFreeSlots` / `checkConflicts` / `whoIsFree`),
the new query roots that close the SPA's scheduling-domain needs, and the
MCP transport itself.

The 2026-05-24 SPA-on-GraphQL pivot doesn't change the MCP transport design
— it just deprioritized MCP relative to SPA needs. The MCP transport is a
thin layer over the same surface the SPA already consumes, so landing the
SPA surface first is the right ordering.

## Scope

**In:**
- §"New query roots" — `categories` / `users` / `periods` / `conflicts` / `templates` / `serverTime`
- §"Search & discovery" — top-level `search` root + per-type `searchText` arg
- §"Compute operations" — `findFreeSlots`, `checkConflicts`, `whoIsFree`
- §"MCP transport" — `graphql_query` + `graphql_schema` + curated mutation tools (`book` etc.)

**Out:**
- Subscriptions / streaming (Phase 2 of PRD 035)
- Multi-pool `findFreeSlots` / embedded `poolFilter` (v1 single explicit-id `poolIds`)
- `whoIsFree` by category (v1 takes explicit `subjectIds`)
- M365 Copilot deployment — deferred to PRD 036
- A GraphQL mutation passthrough for MCP (per-op safety markers lost)

## New query roots — completing the SPA's scheduling-domain needs

Each maps 1-1 to an existing `RaplaFacade` operation, was implicit in
PRD 035, now made explicit:

```graphql
type Query {
  category(id: ID, path: String): Category
  categories(rootId: ID, depth: Int = -1, filter: CategoryFilter): [Category!]!

  users(filter: UserFilter, first: Int, after: String): UserConnection!
  user(id: ID, username: String): User

  periods: [Period!]!
  period(id: ID!): Period

  conflicts(filter: ConflictFilter, first: Int, after: String): ConflictConnection!

  templates(first: Int, after: String): ReservationConnection!     # reservations where isTemplate=true

  serverTime: DateTime!
}

type Category { id: ID!  key: String!  name: String!  path: String!  parent: Category  children: [Category!]!  depth: Int! }
type Period   { id: ID!  name: String!  start: DateTime!  end: DateTime! }
# Reservation gains: isTemplate: Boolean!
```

All §12-filtered; all paginated where lists can grow.

## Search & discovery — top-level global search

The cross-domain `search` root is the MCP-agent-friendly primitive
("find anything matching X"). For the SPA's per-type / per-group tier
model — `searchText` + `matchKind` args on the existing query roots —
ownership moved to
[PRD 028 §"GraphQL substrate augmentations"](028-angular-power-search.md#graphql-substrate-augmentations-added-2026-05-29)
on 2026-05-29 (closer to the consumer that drives the requirements).
The `MatchKind` enum is defined once and shared by both surfaces.

```graphql
type Query {
  search(text: String!, scope: [SearchScope!],
         from: DateTime, to: DateTime,             # reservation/conflict window — bounded by default
         first: Int = 20, after: String): SearchConnection!
}

enum SearchScope { RESERVATIONS  ALLOCATABLES  USERS  CATEGORIES  CONFLICTS }

type SearchConnection {
  edges:       [SearchEdge!]!
  pageInfo:    PageInfo!
  totalCounts: SearchCounts!
}
type SearchEdge {
  node:         SearchHit!
  matchedField: String                          # for client highlighting
  matchKind:    MatchKind!                      # shared enum (also used by PRD 028 per-type args)
  cursor:       String!
}
type SearchCounts { reservations: Int!  allocatables: Int!  users: Int!  categories: Int!  conflicts: Int! }
union SearchHit = Reservation | Allocatable | User | Category | Conflict
enum MatchKind  { PREFIX  SUBSTRING  FUZZY }
```

- Substring scan over `LocalCache` for v1 (same algorithm rapla uses
  today, just exposed via GraphQL). Full-text indexing is follow-on.
- §12: drop hits where the matched field is unreadable; entity-level §12
  also applies (a hit's entity must be readable).
- The PRD 028 tier model (A1/A2/A3 for allocatables, E1–E4 for
  reservations) is **client-side** — depends on calendar selection +
  viewport + recency, all client state. PRD 028 composes tiers from
  multiple aliased calls to the per-type `searchText` roots (now
  documented in PRD 028 itself).

PRD 028 OQ#3 (bounded window) and OQ#10 (§12) are resolved by the
cross-domain root above + the per-type augmentations specified in PRD
028.

## Compute operations

The three task-level computes — side-effect-free **Query** fields, reachable
via the MCP `graphql_query` tool. Shared input:
`input TimeWindow { from: DateTime!  to: DateTime! }`.

### `findFreeSlots` — find a slot, and a room

```graphql
findFreeSlots(
  window: TimeWindow!
  durationMinutes: Int!
  requiredIds: [ID!]          # all must be free for the whole slot
  poolIds: [ID!]              # any ONE must be free; the slot reports which
  stepMinutes: Int = 15       # slot-start alignment
  limit: Int = 20
): [Slot!]!

type Slot { start: DateTime!  end: DateTime!  picked: Allocatable }
```

Two requirement kinds, because a real booking mixes them:

- **`requiredIds`** — specific resources/people (the lecturer, a named
  projector) — *all* free for the whole slot. (A person is an allocatable, so
  there is no separate `attendeeIds` — people and rooms are both ids here.)
- **`poolIds`** — a candidate pool ("any room that fits 10") — *any one*
  free; `Slot.picked` names the chosen member. `picked` may differ across
  slots (14:00 → room A, 15:30 → room B if A is then busy). Tie-break: first
  free in the given order — the caller passes the pool ordered by preference.

The resolver requires at least one of `requiredIds` / `poolIds`. The pool is
an **explicit id list** — the agent composes `rooms(filter: …) →
findFreeSlots(poolIds: …)`. *Deferred (not v1):* multiple independent pools
(a requirement-list form) and a one-call embedded `poolFilter`.

### `checkConflicts` — dry-run conflict check

```graphql
checkConflicts(reservationId: ID, proposed: ProposedReservationInput): ConflictReport!

input ProposedReservationInput { appointments: [AppointmentInput!]!  allocatableIds: [ID!]! }
type ConflictReport { hasConflicts: Boolean!  conflicts: [Conflict!]! }
type Conflict {
  allocatable:      Allocatable!         # the double-booked resource (stub)
  myAppointment:    Appointment!         # the appointment in the input
  otherAppointment: Appointment          # the appointment we clash with — §12: null if unreadable
  withReservation:  Reservation          # parent of otherAppointment — §12: null if unreadable
  dates:            [LocalDate!]!        # days where both appointments fire
}
```

Checks an existing reservation *or* a proposed (unsaved) shape. Classification
is irrelevant to conflicts — the input is appointments × allocatables only.

**Aggregation:** one `Conflict` per `(allocatable, myAppointment,
otherAppointment)` triple, with all clash dates collected into
`dates[]`. Two weekly recurring lectures sharing Room A produce
**one** Conflict spanning N dates, not N Conflicts. Matches rapla's
internal `ConflictFinder` shape and how the Swing client displays
conflicts. A block-pair shape would force every consumer to
re-aggregate (see [domain-model.md §Conflict](../architecture/domain-model.md#conflict-facade-level-computed)).

### `whoIsFree` — availability picture

```graphql
whoIsFree(subjectIds: [ID!]!, window: TimeWindow!): AvailabilityMatrix!

type AvailabilityMatrix { window: TimeRange!  entries: [AvailabilityEntry!]! }
type AvailabilityEntry { subject: Allocatable!  busy: [BusyInterval!]! }
type BusyInterval { start: DateTime!  end: DateTime! }
```

Returns per-subject **busy intervals** — not a bucketed grid; the client
buckets for display. `findFreeSlots` *computes the answer*, `whoIsFree`
*shows the picture* — kept separate (more discoverable for an LLM agent).

### §12 — applies to all three

- An unreadable **or** nonexistent input id → the *same* error
  (`UNKNOWN_RESOURCE`) — indistinguishable, so existence cannot be probed;
  never silently dropped (that would yield a silently-wrong answer).
- `checkConflicts.withReservation` → null if the caller cannot read the
  conflicting reservation; the conflict (allocatable + window) still surfaces.
- `whoIsFree.busy` is **time-only** — no reservation identity (privacy;
  matches PRD 039 `BusyOnlyProjection`).

### Execution

No new core algorithms — these compose existing facade operations + interval
math: `whoIsFree` / `findFreeSlots` from `queryReservations(allocatables,
window)` + `getNextAllocatableDate`; `checkConflicts` from `ConflictFinder`.
Once PRD 039 lands, external-calendar `ExternalAppointment` busy times feed
into all three.

## Operation surface — Query / Mutation roots and MCP mapping

The shared service-layer contract — the GraphQL root operations both
transports sit on.

### Query roots

| Operation | Purpose |
|---|---|
| `<reservationType>(filter, from, to, owner, allocatableIds, first, after)` — e.g. `courses` | list reservations of a type; typed filter + structural args; cursor-paginated |
| `<group>(filter, …)` — e.g. `courseEvents` | list across a declared type group; typed group filter |
| `<resourceType>(filter, first, after)` — e.g. `rooms` | list resources of a type |
| `reservations` / `resources` (`typeIn`, structural args, `classificationFilter`) | cross-type listing — agnostic or ad-hoc cross-type |
| `node(id): Node` | global opaque-id refetch |
| `types: [DynamicType!]` | runtime schema descriptor for data-driven clients |
| `me: User` | the authenticated principal |
| `findFreeSlots(window, durationMinutes, resourceIds, attendeeIds): [Slot!]` | candidate booking windows — see Compute operations |
| `checkConflicts(reservationId \| proposed): ConflictReport` | dry-run conflict check — see Compute operations |
| `whoIsFree(subjectIds, window): AvailabilityMatrix` | per-subject availability — see Compute operations |

The three computes are **Query** fields — side-effect-free, even
`checkConflicts` (takes a proposed reservation, writes nothing).

### Mutation roots

| Operation | Purpose |
|---|---|
| `create<ReservationType>(input)` — e.g. `createCourse` | create a reservation (typed classification) |
| `update<ReservationType>(id, patch)` — e.g. `updateCourse` | patch a reservation |
| `deleteReservation(id)` | delete a reservation (dependency-checked) |

### Out of scope for v1

Resource (allocatable) writes — admin-managed via Swing/SPA + PRD 009's
`/api/resources`; the external API is booking-focused. Subscriptions /
streaming (Phase 2). Calendar-sync operations (PRDs 038/039).

### MCP mapping

`graphql_query` → every Query root (reads + computes). `graphql_schema` →
`types` + introspection. Curated mutation tools (`book` = wraps
`create<Type>`) → the Mutation roots.

## MCP transport shape — hybrid, not six fixed tools

MCP is a **thin hybrid over the GraphQL surface**. The distinction that drives
the shape is **read/traverse vs. compute/act**, not REST vs. GraphQL:

| MCP surface | Shape | Why |
|---|---|---|
| Read / traverse | **One `graphql_query` tool** (read-only) | Frontier model authors GraphQL well; on-demand field selection keeps the agent's context lean; §12 enforced once in field resolvers covers every query shape |
| Schema discovery | **`graphql_schema` tool** | Returns the (scoped) introspection result so the agent knows the deployment's types/attributes |
| Compute | GraphQL **fields with arguments** (`freeSlots(window,duration,resources)`, `conflicts(...)`) reachable through `graphql_query` | Free/busy is an algorithm; GraphQL fields wrap arbitrary resolvers, so it still fits the one read tool |
| Write / act | **Curated, individually named mutation tools** (`book`, …) | MCP confirmation UX is *per-tool*. PRD 035 Phase 3 requires `book` marked `cautious` so the host prompts. A generic `graphql_mutation` tool collapses every mutation to one risk class — unacceptable. Named tools stay confirm-gated, logged, auditable. |

So: **not** six fixed RPC tools (the original draft), and **not** a full
GraphQL passthrough. A `graphql_query` read tool + `graphql_schema` +
curated, safety-marked mutation tools.

**Caveat (recorded):** a `graphql_query` passthrough is a *broad* capability,
less legible to the human approving the MCP server than a curated tool list.
Mitigation: server-side query logging (PRD 035 already logs `book`) + the
resolver-level §12 filter bounds "any query" to the caller's read scope.

**Design note — `graphql_schema` payload shape.** (Downgraded from PRD 035
OQ#13.) The schema is small (~10–15 core types + one per DynamicType,
~25–40 total). Not a context problem. The `graphql_schema` tool returns
**SDL** (~200–300 lines), not raw introspection JSON (the verbose form).

## Plan

### Phase 1 — Discovery + compute primitives

Land the new query roots (§"New query roots"), the `search` root + per-type
`searchText` args (§"Search & discovery"), and the three compute operations
(`findFreeSlots`, `checkConflicts`, `whoIsFree`). Tier-3 §12 leak tests per
query — see Tests section. Implementation note: resolvers compose existing
`RaplaFacade` operations; no new core algorithms (per the "Execution" note
in §"Compute operations").

### Phase 2 — MCP transport spike

Verify the Spring AI MCP starter's Spring Boot 4 / Jackson 3 alignment
(residual OQ#2 from PRD 035 — the **implementation-gating item** from the
2026-05-16 design handoff: "the Spring AI MCP starter's Spring Boot 4 /
Jackson 3 alignment is a Phase-1 spike — verify before committing to the
MCP transport").

If clean: implement `graphql_query` (read-only) + `graphql_schema` tools,
and the curated `book` mutation tool marked `cautious` and logged. Smoke-test
from a Claude Code session.

If blocked: spike a thinner direct MCP integration (raw JSON-RPC over the
Spring MVC stack, no starter), or defer the MCP transport to a later
Spring AI release while keeping the GraphQL surface available for any MCP
host that can hand-roll a `graphql_query` tool over HTTP.

### Phase 3 — Showcase tracks

The three Phase 0 showcase tracks from PRD 035 still apply, all sharing the
same Phase 1+2 surface:

| # | Track | Stack |
|---|---|---|
| 1 | **OpenClaw + Ollama + multi-channel + voice** (lead) | OpenClaw daemon → Ollama → rapla MCP (stdio); WhatsApp + voice + Slack |
| 2 | **OpenCode + Ollama (terminal)** | `opencode` CLI → Ollama → rapla MCP |
| 3 | **Claude Desktop + Anthropic API** | Claude Desktop → rapla MCP |

Record each (~60–90 s, shared script). Pin under `docs/showcases/`.

## Tests

- **Tier 3** — `GraphQlLeakTest` extension for the batched paths added
  here (the `search` root fans out across types in one query; `conflicts`
  + the `checkConflicts` compute touch reservations the caller may not be
  permitted to see; `categories` may include subtrees with mixed
  visibility). Non-admin user, mixed visible/hidden/non-existent ids,
  response byte-identical to the visible-only subset; error messages name
  no unreadable entity.
- **Tier 4** — one `@SpringBootTest(webEnvironment=RANDOM_PORT)`
  `@Tag("e2e")` per MCP transport: MCP `graphql_query` + `book` over the
  wire, JSON-RPC response shape, schema introspection returns SDL.
- **Compute operations** — tier-2 `ExternalSchedulingServiceTest` (against
  a real `RaplaFacade` via `FacadeTestSupport`) for the algorithmic shape
  of `findFreeSlots` / `checkConflicts` / `whoIsFree`, plus tier-3
  `GraphQlLeakTest` for the §12 properties listed in §"Compute operations
  → §12".

## Open questions

### OQ-A — `Conflict` GraphQL type: symmetry + N-way + compute "self" perspective

*Opened 2026-05-24 during the §"2026-05-24 design refinement" §9 search work
(PRD 035 OQ#15).* Three coupled sub-questions:

(a) **Symmetric** `Conflict { reservation1, reservation2 }` — one type
    shared across `search`, `checkConflicts`, `conflicts(...)` — vs.
    **keep asymmetric** `Conflict { withReservation }` (per the original
    §"Compute operations" shape) plus a separate symmetric `ConflictPair`
    for search results. The original is asymmetric because `checkConflicts`
    has a natural "self" perspective; symmetric is the right fit for
    search (no caller-side "self"). One type is cleaner; two types preserve
    each use case's natural shape.

(b) **If symmetric:** how does `checkConflicts` carry the "self"
    perspective — by convention (`self = reservation1` in compute results)
    or via a wrapper (`SelfPerspectiveConflict { conflict: Conflict,
    selfIndex: Int }`)?

(c) **Are rapla conflicts always pairwise**, or is N-way possible (three
    reservations all booking the same room at the same time)? Check
    `ConflictFinder`. If N-way is possible, the type carries `reservations:
    [Reservation!]!` not a `reservation1`/`reservation2` pair, and
    `matchedField` becomes `"reservations[i].<field>"`.

Affects: `Conflict` GraphQL type definition, `matchedField` path syntax
for search hits, `checkConflicts` return shape.

### OQ-B — §12 on Conflict search hits where one side is unreadable

*Opened 2026-05-24 (PRD 035 OQ#16).* When a conflict surfaces in search
and one of the paired reservations is §12-unreadable (private to a group
the caller doesn't belong to), two options:

- **Privacy-first drop** — exclude the conflict entirely. Consistent with
  PRD 035's §12 doctrine ("behave as if response were a CSV emailed to the
  user"). Default lean.
- **Utility-first null-render** — return the conflict with the unreadable
  reservation as `null` ("Room 101 14:00–15:30 conflicts with: <Public
  Event>, <unavailable>"). The caller learns "something's in the way at
  this time" — but that's existence-of-private-data leakage.

UX cost of drop: a caller can't see a conflict warning at a time where one
party is private even if the OTHER party is fully public. Tradeoff is
real; needs explicit decision rather than implicit default.

### OQ-C — Window-match semantic for `search` / `reservations(from, to)`

*Opened 2026-05-24 (PRD 035 OQ#17).* Settle: (a) half-open `[from, to)`
boundaries with intersection rule (`appointment.end > from AND
appointment.start < to`); (b) "a reservation matches if ANY of its
appointments intersects the window" — matches rapla's existing
`getReservations(allocatables, from, to)` rule; (c) `DateTime` is
wall-time / `LocalDateTime` per PRD 014, in the deployment timezone.
Mostly documentation, but worth landing so the SPA doesn't use `<=` on
the boundary. Implementation note: resolver should pre-compute
per-reservation `[firstStart, lastEnd]` bounds to cheaply reject
non-overlapping reservations before walking individual appointments
(avoids 500k-intersection cost on 10k reservations × 50 appointments × 5y
windows).

### OQ-D — Default window — value + partial-input handling

*Opened 2026-05-24 (PRD 035 OQ#18).* When the client doesn't supply
`from`/`to`:

(a) **Value**: `search.defaultWindowDays` deployment config; fallback 730
    (±1 year), symmetric around `serverTime`.
(b) **Partial inputs**: strict (both supplied together or both absent;
    partial = `INVALID_ARGUMENT`) vs permissive (server fills the missing
    endpoint from the default window). Lean strict — clearer semantics;
    partial-input is ambiguous ("from this date, default forward" vs
    "from this date, default reach").
(c) Server default centered on `serverTime`, NOT on a notional viewport
    — the server doesn't know the client's viewport. SPA passes its own
    viewport-derived `from`/`to` (see OQ-F).

### OQ-E — Max-range cap — value + admin override + cap target

*Opened 2026-05-24 (PRD 035 OQ#19).* To prevent DoS via unbounded searches:

(a) **Value**: `search.maxWindowDays` deployment config; fallback 1825
    (~5 years).
(b) **Error**: top-level GraphQL `WINDOW_TOO_LARGE { maxDays, requestedDays }`
    — query rejected, not partial result.
(c) **Admin override**: none v1 (admins paginate windows for archive
    searches). Future: opt-in admin-only unbounded search backed by a real
    index (follow-on, not v1).
(d) **Cap target**: days-based (simple, predictable contract) rather than
    cost-based (match count, time budget). Days is a proxy for cost — bad
    on very-large or very-small deployments — but the API contract is
    clearer this way. Implementations may also have a defensive
    time/count budget on top.

### OQ-F — PRD 028 viewport-centered default — cross-PRD

*Opened 2026-05-24 (PRD 035 OQ#20).* The SPA's calendar viewport is client
state; the server default centers on `serverTime`. A user viewing the March
2025 calendar in June 2026 expects "search Algorithms" to find the
Algorithms course in March 2025 — needs the SPA to pass viewport-derived
`from`/`to` rather than rely on the server default. Decision is PRD 028
territory (not PRD 060): does the SPA always pass viewport, never pass, or
conditionally? Flag for whenever PRD 028 resumes. PRD 060's contract is
unchanged either way — server default is `serverTime`-centered for
headless callers.

### OQ-G — `whoIsFree` by category

*From PRD 035 §"Compute operations → Open questions" #2.* v1 takes
explicit `subjectIds`; expanding a category (e.g. a department) to subject
ids is the agent's job for v1. Whether to grow a category-expanding
convenience (e.g. `whoIsFree(categoryId: ID, window: TimeWindow!)`) is
follow-on; for v1 the agent composes
`categories(rootId: ...) { children { id } } → whoIsFree(subjectIds: ...)`.

## Risks

| Risk | Mitigation |
|---|---|
| Spring AI MCP starter not yet aligned to Spring Boot 4 / Jackson 3 | Verify Phase 2 (the spike). Spring for GraphQL itself is clean (PRD 035 OQ#2). Worst case the starter drags Jackson 2 — survivable, isolate it like `SwaggerJacksonConfig` does for springdoc. If the starter remains blocked, fall back to a thinner direct integration or defer the MCP transport. |
| `book` invoked autonomously without confirmation | Curated tool marked `cautious`; host prompts; all `book` calls logged; `created_via=mcp` flag on the reservation for fast revert. |

## Cross-references

- [PRD 035 (done) — Foundations](done/035-graphql-foundations.md)
- [PRD 028 — Angular Power Search](028-angular-power-search.md) — `search` root resolves PRD 028 OQ#3 (bounded window) and OQ#10 (§12)
- [PRD 036 — External IdP OAuth Login](036-external-idp-oauth-login.md) — M365 Copilot showcase track
- [PRD 040 — dispatch validate before lock](040-dispatch-validate-before-lock.md) — multi-pod lock semantics
- [PRD 043 — API Keys JWT/PAT](043-api-keys-jwt-pat.md) — scoped key mechanism
- [PRD 055 — Events Read API](055-graphql-events-read-api.md)
- [PRD 056 — Events Write API](056-graphql-events-write-api.md)
- AGENTS.md §12 permission-leak invariant
