# PRD 035: rapla external integration API + MCP server

**Status:** draft — **design complete 2026-05-16; implementation not started** (2026-05-13; scope broadened 2026-05-15 after design review)
**Date:** 2026-05-13

> **2026-05-15 scope change.** This PRD originally covered only an MCP server
> exposing six fixed `@McpTool` scheduling primitives. A design review
> (recorded in "GraphQL transport — decision record" below) broadened it: the
> deliverable is now a **shared task-level service layer** with **two
> transports** — an external **GraphQL** API and the **MCP** server — over a
> single permission boundary. MCP becomes a thin hybrid on top of the GraphQL
> surface rather than a hand-written set of RPC tools. The showcase tracks
> (Phase 0) are unchanged.

## Design status & handoff (2026-05-16)

**The design is complete.** Every section below is settled — data model,
schema design + implementation, the GraphQL transport decision, the filter &
query language, write mutations, write-side validation, the compute
operations, the operation surface, the MCP hybrid transport, auth — and all
14 Open Questions are resolved. The next step is **implementation**, per the
Plan (Phase 1 onward); no further design work is required to start.

**Deferred — not v1, by deliberate decision** (so a later reader does not read
these as gaps):

- Group **output** interfaces — input groups (typed cross-type *filtering*)
  are v1; output interfaces (typed shared *output* fields) are deferred.
- `findFreeSlots` multiple independent pools + one-call embedded `poolFilter`
  — v1 has a single explicit-id `poolIds`.
- Resource (allocatable) **writes** — v1 is booking-focused (read resources,
  write reservations).
- Subscriptions / streaming — Phase 2.
- Rate limiting / per-key quotas — add with a real consumer.
- Plugin-contributed schema types — OQ#8, Phase-N.
- SPA → GraphQL migration — a separate track gated on PRDs 023/030; v1 ships
  GraphQL for external + MCP without it.

**Implementation-gating item:** the Spring AI MCP starter's Spring Boot 4 /
Jackson 3 alignment (OQ#2 residual) is a Phase-1 spike — verify before
committing to the MCP transport.

**Tier-C — settles during implementation, not design:** pagination cursor
encoding, the full error-`code` taxonomy.

**Related PRDs:** [PRD 040](040-dispatch-validate-before-lock.md) and
[PRD 041](041-openapi-runtime-removal.md) are independent spun-out cleanups;
[PRD 043](043-api-keys-jwt-pat.md) details the scoped-API-key mechanism this
PRD's Auth section sits on (supersedes PRD 031 §"API key surface").

## Goal

Give rapla a deliberate, stable, permission-safe **external integration
surface** — distinct from the internal `/api/storage/*` client plumbing
(PRD 009) and the SPA-internal `client` API group (PRD 031). Two transports,
one substrate:

1. **GraphQL external API** — for scripts, custom plugins, custom table/report
   tooling, the Angular SPA, and deployment-coupled third-party integrators.
2. **MCP server** — for AI assistants embedded in chat tools (Claude Desktop,
   Claude Code, OpenCode, OpenClaw). A thin hybrid over the GraphQL surface.

Both sit on **one shared task-level service layer** (`ExternalSchedulingService`
or similar) and enforce the AGENTS.md §12 permission-leak invariant at **one**
output boundary — the GraphQL field resolvers. Neither transport reimplements
scheduling logic or the leak filter.

## Why now

Three signals from the original MCP draft still hold:

1. **MCP adoption.** 97 M monthly SDK downloads as of March 2026; every major
   AI vendor supports it. Q2 2026 brings OAuth 2.1 + PKCE for browser-based MCP
   agents — which rapla already has (PRD 029).
2. **Spring AI lands the MCP layer for Java**; `@McpTool` + Spring DI make
   wiring concise.
3. **rapla's user-facing value is exactly what an LLM agent wants to call.**
   "Find a free 90-minute slot next Tuesday for these three people" is one
   prompt, today a five-step REST sequence.

Broadened-scope additions:

4. **There is no deliberate external API today.** `/api/storage/*` is internal
   bulk plumbing (raw `UpdateEvent`/`dispatch`); `/api/resources` + `/api/events`
   (PRD 009) are in-progress raw-entity CRUD, unversioned, never designed as a
   stable third-party contract. MCP needs a real substrate to sit on; building
   it once for both transports avoids divergence.
5. **The MCP read surface needs a schema.** The original `query_resources(type,
   attribute)` tool (OQ#3) is unimplementable without a machine-readable
   description of the deployment's DynamicTypes. A generated GraphQL schema
   *is* that description — so the schema work is on the critical path for MCP
   anyway.

## Architecture

```
            ┌──────────────┐     ┌──────────────┐
  scripts → │  GraphQL API │     │  MCP server  │ ← Claude Desktop / Code,
  SPA     → │  (HTTP)      │     │  (hybrid)    │   OpenCode, OpenClaw
  plugins → └──────┬───────┘     └──────┬───────┘
                   │                    │
                   └─────────┬──────────┘
                             ▼
                ┌────────────────────────────┐
                │  ExternalSchedulingService  │  task-level operations
                │  (shared service layer)     │  + GraphQL field resolvers
                │  — §12 permission filter —  │  ← single leak boundary
                └────────────┬───────────────┘
                             ▼
                   RaplaFacade / operators
```

- **Shared service layer.** Task-level operations (`findFreeSlots`,
  `createBooking`, `queryReservations`, `checkConflicts`, availability) plus
  the GraphQL field resolvers. Both transports call it; neither bypasses it.
- **GraphQL is the external transport.** Decision record below.
- **MCP is a hybrid over GraphQL** (see "MCP transport shape").
- **One §12 boundary.** The permission-leak filter (AGENTS.md §12) lives in the
  GraphQL field resolvers — one filter point per type, covering every query
  shape and both transports. This is *stronger* than per-endpoint or per-tool
  leak tests.

## External API data model

The external DTOs are **hand-built, versioned, a tree not a graph, with zero
back-references**. They are *not* the internal entity wire format.

> **Why not reuse the internal format.** PRD 009 Risk 1 is the canary: the
> internal Jackson format serializes raw `EntityImpl` graphs whose back-refs
> (`getResolver()` → scheduler → unserializable inner class) must be patched
> `@JsonIgnore` getter by getter. That format is tied to `LocalCache` and is a
> moving target. A tree-shaped external DTO set sidesteps the entire class of
> round-trip bugs by construction.

### Composition vs. association — the one mechanical rule

Every field is either an **owned component** or a **reference to an
independent entity**:

| Relationship | Treatment | Examples |
|---|---|---|
| **Component** (owned, no independent lifecycle) | Always embedded in full | `appointments[]`, `classification` |
| **Association** (points at an independently-managed entity) | **Stub** `{ id, type, displayName }`; expandable | allocatable refs, category refs, owner/user refs |

In GraphQL terms a component is a nested object type reached with no resolver
round-trip; an association is an object-typed field whose resolver does the
id-lookup + §12 filter. A "stub" is simply the association's object type with
only `id`/`type`/`displayName` selected — "expand on demand" is the client
selecting deeper fields.

### Per-type shape

- **Event (`Reservation`)** — the top-level booking:
  `{ id, classification, appointments:[...], allocations:[...], ownerId,
  lastChanged, canModify }`. The appointments array *owns* its children; an
  Appointment carries **no** `eventId` back-pointer.
- **Appointment** — `{ id, start, end, allDay, repeating:{ type, interval,
  end|count, exceptions[] } }`. start/end are `LocalDateTime` (PRD 014).
  Recurrence two ways: the raw `repeating` rule by default, or materialized
  instances for a window via an `occurrences(from:,to:)` field.
- **Allocation** — explicit, *not* the internal implicit-restriction model:
  `{ allocatable: <stub>, appointmentIds: [...] | null }`. `appointmentIds`
  is an **intra-document id list** (the appointments are components of the
  same event, already present) — not a stub, not expandable. `null` = bound
  to every appointment. On write the resolver translates back to
  `setRestriction(alloc, appointments[])`.
- **Allocatable (resource/person)** — `{ id, type:"resource"|"person",
  displayName, classification }`. Person allocatables may link to a `User` —
  expose `displayName` only, never the account/login.
- **Classification** — a component, embedded full: `{ typeId, attributes:{...} }`.
  But reference-valued attributes *inside* it (allocatable-valued,
  category-valued) are stubs. Category references resolve to `{ id, path }`
  (rapla categories are a tree).

### Stub fields

`{ id, type, displayName }`. `displayName` is **locale-resolved** (one string,
not a map; locale from `Accept-Language` or a locale bound to the API key) and
for allocatables/events it is the **classification-derived** display name —
so a client renders a label without expanding the classification.

§12: a stub only ever appears when the caller can read the referenced entity
(an unreadable reference is *dropped*, never emitted hollow). The name on the
stub is therefore §12-safe by construction.

### Write side

Mutations accept the same shapes but with associations as **bare ids** the
caller supplies. An unknown id and an unreadable id fail **identically** (§12
existence rule) — the external caller cannot probe existence.

## Schema design — structural-static + classification-generated

| Part of the schema | Shape | Lifecycle |
|---|---|---|
| **Structural types** — `Reservation`, `Appointment`, `Allocatable`, allocation, references, query/mutation roots, scalars | Static SDL, shipped with the build | Never changes per deployment → codegen once, type-safe forever |
| **Classification types** — one GraphQL type per `DynamicType` (`CourseClassification { lecturer: String, roomCapacity: Int, … }`) | Generated from the deployment's DynamicTypes at startup | Rebuilt on admin change |

**Rebuild on admin change.** Hook the `DynamicType` save path (rapla's
`UpdateEvent` storage stream). On change, regenerate the classification SDL,
rebuild the `GraphQLSchema`, hot-swap the immutable `GraphQL` instance.
In-flight queries finish on the old instance; no restart.

### One schema, two consumption modes

| Mode | Consumers | Property |
|---|---|---|
| **Runtime / introspection** (schema-as-data) | SPA classification rendering, dynamic selector / column-picker UIs, MCP `graphql_schema` tool | Data-driven rendering → an admin DynamicType change is picked up by a **browser reload, no client rebuild**. Filter UIs build themselves from attribute metadata. |
| **Build-time codegen** (schema-as-source) | Custom plugin authors, custom table *components*, deployment-coupled external integrators | Typed `CourseClassification.lecturer`, typed filter inputs, compiler-checked. These consumers are already in a build/deploy loop; rare DynamicType changes cost a regen. |

These are the *same* schema. The duality is exactly what GraphQL introspection
gives for free (introspection = schema-as-data; SDL = codegen source) — with
plain REST you would hand-build a `/types` endpoint *and* an OpenAPI doc
separately and they would drift.

**Consequence for the SPA:** classification rendering must be **data-driven**
(iterate `attributes` from a runtime schema descriptor) — TypeScript types are
erased at build and AOT templates are baked at build, so typed classification
access would force a frontend rebuild+redeploy on every DynamicType change.
The SPA codegens only the *structural* types (stable) and renders
classifications generically. This keeps the "admin edits a type → users just
reload" property.

**The `Classification` interface (decision 2026-05-15 — "Approach A").** The
classification output carries *both* forms: an interface
`Classification { typeId: ID!  attributes: [AttributeValue!]! }`, and each
generated `CourseClassification implements Classification` adds the typed
per-attribute fields. The SPA queries the interface-level generic `attributes`
and renders via one descriptor-driven component (display + edit form +
filter UI, all built from the `types` descriptor); deployment-coupled codegen
consumers use the typed fields. The concrete typed classification types are
explicitly **not** for the SPA — one SPA build serves deployments with
different classification schemas, so it cannot reference deployment-specific
attribute names regardless of how it is typed.

### Schema implementation — type resolution & data fetching

rapla has **no Java class per DynamicType** — a `lecture` and a `test` are
both `ReservationImpl`; the type is *data* (`getClassification().getType()`),
not a subclass. So the generated GraphQL types (`Lecture`, `CourseEvent`,
`CourseClassification`) are **virtual** — no backing class — and the usual
GraphQL mechanisms do not apply: no POJO field fetchers, no `instanceof` type
resolution, no compile-time `@SchemaMapping` controllers for them.

The layer is **schema-first with programmatic wiring**: the DynamicType→SDL
generator produces the schema; a wiring step attaches fetchers and resolvers
programmatically (Spring for GraphQL `RuntimeWiringConfigurer`), rebuilt with
the schema on DynamicType change. Four pieces:

1. **`TypeResolver`** — one per interface (`Reservation`, `Allocatable`, each
   group interface). Dispatches on **data, not Java type**:
   `entity → getClassification().getType().getKey() → generated type name`.
   The concrete type's declared `implements` chain handles the interface
   hierarchy.
2. **Generic attribute `DataFetcher`** — every generated classification field
   (`Lecture.lecturer`, `CourseEvent.status`, `CourseClassification.capacity`)
   is served by one key-parameterized fetcher
   (`source.getClassification().getValue(key)`), attached to each field at
   wiring time. This is also where the §12 filter + DataLoader batching live
   (OQ#7).
3. **Structural field fetchers** — `id`, `appointments`, `allocations`,
   `owner` on the `Reservation` / `Allocatable` interfaces — written once,
   shared by all concrete types.
4. **Roots** — fixed roots (`node`, `me`, the computes) may be
   annotation-driven `@QueryMapping`; the *generated* per-type / group roots
   (`courses`, `courseEvents`) are wired programmatically.

Annotation-driven `@SchemaMapping` is used only for the fixed structural
parts. This is the standard pattern for dynamic GraphQL schemas (Hasura et al.
have no class-per-table either).

## Filter & query language

Queries filter along **three independent axes** — they do not mix:

| Axis | Expressed as | Covers |
|---|---|---|
| **Structural** | query arguments | `from`/`to` (appointment time), `owner`, `allocatableIds` (allocation membership), `typeIn` |
| **Classification** | a `filter` / `classificationFilter` argument | dynamic attributes — `status`, `capacity`, `lecturer`, … |
| **Traversal** | connection fields | `allocatable(id) { reservations(…) }` — "what is booked on this resource" |

`allocatableIds` is first-class: "reservations for an allocatable in a time
range" is rapla's *primary* facade query. "Events for a course" means the
event *allocates* the course allocatable — a structural predicate, **not** a
classification attribute.

### Structural axis

Top-level arguments on every reservation/resource root —
`reservations(typeIn:, from:, to:, owner:, allocatableIds:, first:, after:)`.
They map straight to facade-query parameters; they never touch
`ClassificationFilter`.

### Classification axis — typed per-type filters

**Fixed comparison inputs** — hand-written, stable, one per scalar kind:

```graphql
input IntFilter      { eq: Int  ne: Int  gt: Int  gte: Int  lt: Int  lte: Int  in: [Int!] }
input StringFilter   { eq: String  ne: String  in: [String!]  contains: String  startsWith: String }
input DateTimeFilter { eq: DateTime  gt: DateTime  gte: DateTime  lt: DateTime  lte: DateTime }
input BooleanFilter  { eq: Boolean }
input CategoryFilter { eq: ID  in: [ID!]  descendantOf: ID }   # descendantOf → category subtree
input AllocatableFilter { eq: ID  in: [ID!] }
```

All fields optional; multiple fields in one comparison = AND
(`{gte: 10, lt: 100}` → 10 ≤ x < 100).

**Generated per-DynamicType filter inputs** — the generator that emits the
classification output types also emits one filter input per type, each
attribute typed by the comparison-input matching its value-type:

```graphql
input CourseFilter {
  _and: [CourseFilter!]   _or: [CourseFilter!]   _not: CourseFilter
  lecturer:   StringFilter
  capacity:   IntFilter
  department: CategoryFilter
}
```

Multiple fields = AND; `_and`/`_or`/`_not` nest. GraphQL rejects at
validate-time: unknown attribute (typo), wrong value type
(`capacity: {gte: "ten"}`), wrong operator (`capacity: {contains: …}`) —
client codegen turns each into a build error.

### Classification axis — cross-type queries

A query spanning types (`typeIn: ["lecture","test"]`) cannot use a per-type
filter — the attribute schemas differ. Two routes, by whether the attribute
is shared (see "Shared attributes"):

- attributes shared by a **declared group** → typed group filter;
- otherwise → a **runtime-validated** `classificationFilter`:
  ```graphql
  reservations(typeIn: ["lecture","test"], classificationFilter: {
    rules: [{ attribute: "status", conditions: [{ operator: EQ, value: "planned" }] }]
  })
  ```
  The resolver validates each named attribute exists, with the named
  operator, on **every** `typeIn` type — else an `INVALID_ATTRIBUTE` error.

### Traversal axis

`Allocatable.reservations(typeIn:, from:, to:, filter:)` — the events
allocating that allocatable. The natural form of "events for a course," since
the course is an allocatable the events allocate.

### Query modes

| Mode | Root | Classification filter |
|---|---|---|
| Per-type | `courses(filter: CourseFilter)` | typed, compile-time-checked |
| Declared group | `courseEvents(filter: CourseEventFilter)` | typed over the group's shared attributes — a primary cross-type surface |
| Ad-hoc cross-type | `reservations(typeIn:, classificationFilter:)` | runtime-validated — ad-hoc escape hatch |
| Fully agnostic | `reservations(from:, to:)` | structural only |

### Shared attributes

Cross-type *typed* filtering needs attributes several types share with the
**same key and same value-type**. Three solutions, trading typedness vs
stability vs config effort:

1. **Auto-intersection onto an interface.** The generator computes the
   attributes common to *all* reservation types and puts them, typed, on the
   `Reservation` interface. Zero config — but **fragile**: adding one
   divergent event type silently removes a field from the interface, a
   breaking schema change. Rejected as the default for that reason.
2. **Declared type groups (recommended).** A DynamicType annotation declares
   membership (`group: "CourseEvent"`). The generator emits a `CourseEvent`
   interface + `CourseEventFilter` over the group's shared attributes. Typed,
   and **stable** — membership is pinned by declaration, not derived, so an
   unrelated new type does not disturb it. Needs a small grouping concept in
   rapla (annotation — does not exist today).
3. **Runtime-validated `classificationFilter`.** Always available, zero
   generation, handles any ad-hoc `typeIn`. Not compile-time typed.

**Chosen design (v1) — declared groups as a primary schema layer.** Cross-type
queries are pervasive in rapla (lectures/tests, room/room-group, …), so groups
are v1, not a later add-on. They form an interface hierarchy:

```
Reservation (interface)        structural only — all reservation types
  └ CourseEvent (interface)    declared group — + shared typed attributes
      └ Lecture, Test (types)  concrete — + type-specific attributes
```

GraphQL interfaces implement interfaces, so `Lecture implements CourseEvent &
Reservation`. Query roots exist at every level — `courseEvents(filter:
CourseEventFilter)` (group, a *primary* surface — users query "events" /
"space" more than one narrow type), `lectures(filter: LectureFilter)`
(per-type), `reservations(…)` (structural agnostic). The `groups` annotation
on a DynamicType is a **list** — a type may belong to several
(`lecture` ∈ `CourseEvent` and `GradedActivity`). The `Reservation` /
`Allocatable` top interfaces stay **structural only** (id, appointments,
allocations, owner — rock-stable). A deployment that declares no groups still
gets fully-typed per-type roots + the runtime cross-type filter — groups are
purely additive; migration tooling may *suggest* groups (scan for type
clusters sharing attributes) but declaration stays explicit. The runtime
`classificationFilter` demotes to the **ad-hoc escape hatch** for `typeIn`
combinations no declared group covers.

**Group filter input vs. group output interface — independent (decision
2026-05-15).** A declared group always produces a typed **filter input**
(`CourseEventFilter`) — **v1**; this is what gives typed cross-type *filtering*
(a `status` predicate checked at GraphQL validate-time, not in the resolver).
A group **output interface** (`CourseEvent` carrying typed shared *output*
fields) is a *separate, optional* artifact — `courseEvents(filter:
CourseEventFilter)` can simply return `[Reservation]`, clients reading shared
attributes via the generic `attributes` or `... on Lecture`. The output
interface only adds typed shared-output-field access, which only
deployment-coupled codegen consumers benefit from (the SPA is generic; MCP
works off introspection). **v1: generate group filter inputs; defer group
output interfaces** until an integrator needs them.

**Semantic caveat:** "same key + same value-type" is a *structural* match,
not semantic. `lecture.status` and `test.status` may both be `string` yet
have different value domains (planned/done vs draft/graded), or both be
category-valued over different category trees. A shared typed `status` field
is structurally sound but the caller must know the domains differ; the
runtime `types` descriptor carries each type's real per-attribute
constraints.

### Input vs. output groups

A declared group yields two *independent* artifacts:

- an **input group** — the generated filter input (`CourseEventFilter`), used
  as the `filter:` argument; gives typed cross-type *filtering*. **v1.**
- an **output group** — the generated interface (`CourseEvent`), used as a
  *return type*; gives direct typed selection of shared attributes in the
  result. **Deferred** — `courseEvents` returns `[Reservation]` meanwhile, and
  shared attributes stay reachable via the generic `attributes` or
  `... on Lecture`.

```graphql
# input group — typed cross-type filtering (v1)
courseEvents(filter: { status: { eq: "planned" } }) { … }

# output group present → status directly selectable, typed, codegens
courseEvents(…) { edges { node { status  semester { path } } } }

# output group absent → status via generic attributes or an inline fragment
courseEvents(…) { edges { node { classification { attributes }
                                  ... on Lecture { recordingUrl } } } }
```

**Multiple group membership.** The `groups` annotation is a list, so a type
may belong to several groups — `Lecture implements Reservation & CourseEvent &
GradedActivity`. Not a conflict: a GraphQL object type implements *many*
interfaces, while an entity still resolves to exactly **one concrete type**.
The `TypeResolver` returns that one type name (`Lecture`); its declared
`implements` list (emitted transitively by the generator) makes it a valid
member of every group root. **No field collision is possible** — every
interface field for a concrete type derives from that type's single attribute
of that key, and the group-intersection rule guarantees agreement, so two
groups can never present the same key at different types on one concrete type.

### Execution — no engine rewrite

The resolver normalizes a typed filter tree to disjunctive normal form (OR of
ANDs) and maps it onto rapla's existing `ClassificationFilter[]`: each
AND-clause → one `ClassificationFilter` (a set of `ClassificationFilterRule`s),
the OR across clauses → the array, `_not` pushed to the leaf (negated
operator). For a cross-type query the resolver builds one `ClassificationFilter`
per `typeIn` type — the `ClassificationFilter[]` array is already an
OR-across-type-scoped-filters, so it expresses cross-type natively. The
existing `matches` engine executes all of it unchanged.

### Scope boundary

`ClassificationFilter` is the *historic execution engine*, reused as-is. The
generated typed inputs are the *new API surface* — the two are deliberately
decoupled. Modernizing the internal/stored filter model and the Swing filter
UI (saved calendar filters, storage format) is separable, larger, and out of
scope here. The migration-path principle (OQ#1) applies: design the API
surface for the future; the internal model can catch up independently.

### §12

Filter results pass the per-entity permission filter; a predicate must never
leak existence — a filter matching only hidden entities returns identically
to one matching nothing. `GraphQlLeakTest` covers a filtered path.

## GraphQL transport — decision record

The external HTTP transport is **GraphQL**, not REST. The path to this
decision (kept because each objection was raised and resolved — re-litigating
it later wastes a session):

| Objection | Resolution |
|---|---|
| GraphQL's static SDL can't express rapla's admin-configurable DynamicTypes | Schema-from-config is a proven pattern (Hasura, PostGraphile introspect a DB and generate a schema). rapla generates classification types from DynamicTypes. |
| A generated schema goes stale when an admin edits a type | Rebuild + hot-swap on the `DynamicType` save path (above). Server-side correctness is instant. |
| Client codegen goes stale → forces a rebuild | Only if a client codegens the *dynamic* part. Structural types are static (codegen once); classification is consumed data-driven via introspection. DynamicType changes then never invalidate client codegen. |
| MCP agents shouldn't author query strings | Frontier models compose GraphQL reliably; on-demand field selection is a *context-budget* win for an agent (response size = tokens consumed). MCP uses a `graphql_query` tool — see below. |
| Operational surface (DoS via deep/expensive queries) | Query depth + complexity limiting. (Persisted-query allowlisting deliberately *not* adopted — see OQ#5.) A modest one-time setup cost. |

What tipped it: the **multi-consumer unification**. One schema serves the SPA,
custom plugins, custom tables, external REST consumers *and* the MCP read
surface — and that unification is precisely what GraphQL's schema+introspection
duality delivers. Plain REST would need a separately-maintained `/types`
endpoint.

**Not chosen / out of scope:** a GraphQL passthrough for MCP *mutations* (loses
per-operation safety markers — see MCP section); REST with sparse-fieldsets as
the primary transport (Spring for GraphQL is Jackson-3-clean on Spring Boot 4 —
OQ#2 — so this is no longer a needed fallback).

## Write mutations — create, update (patch), delete

All write mutations funnel through rapla's existing `UpdateEvent`/dispatch
path; the whole mutation maps to **exactly one `UpdateEvent`**, dispatched
once (OQ#14) — no partial writes.

### Patch principle — clobber-proof by construction

`update*` mutations are **patches**, not full-entity replacements. The
resolver: load the current entity (fresh, under the dispatch lock — see
[PRD 040](040-dispatch-validate-before-lock.md)) → apply the delta → dispatch
the merged full entity. The client sends only what it changes; every omitted
field is filled from current server state, so a client that does not know
about field X structurally cannot erase X. This is server-side merge, done in
the resolver — no legacy `dbStore` change. (It also means a non-lockstep
client — stale JNLP, old custom-deployment build, third-party consumer — is
safe on the GraphQL API, where full-entity dispatch would let it clobber.)

### Create / update — per-type, typed

`create*` and `update*` are generated **per DynamicType** so the
classification payload is typed (`CourseClassificationPatch` — validate-time
attribute checking), consistent with the typed filter/output decision:

```graphql
mutation { updateCourse(id: ID!, patch: CoursePatch!): ReservationMutationResult }

input CoursePatch {
  classification:  CourseClassificationPatch
  appointments:    AppointmentCollectionPatch
  allocations:     AllocationCollectionPatch
  ownerId:         ID
  expectedVersion: Int          # optional optimistic-lock guard
}
```

- **Scalar / classification fields** — absent = unchanged, present-with-value
  = set, present-as-null = clear. graphql-java exposes the raw argument map,
  so absent-vs-explicit-null is distinguishable — do not rely on a nulled
  POJO.
- **Collections** (`appointments`, `allocations`) — **explicit per-element
  ops**, never full-replace, never diff-by-id (diff-by-id's "missing =
  delete" lets a partial view delete things):

  ```graphql
  input AppointmentCollectionPatch {
    add:    [AppointmentInput!]
    update: [AppointmentUpdate!]      # { id, ...patch }
    remove: [ID!]
  }
  ```

  A client touches only what it names; unmentioned appointments / allocations
  are untouched.

### Concurrency

`expectedVersion` optional. Provided and stale → `CONFLICT` error, patch not
applied. Omitted → apply the delta onto current — already far safer than
full-entity last-write-wins, since a patch touches only named fields and
therefore *merges* (a title change reapplied onto a newer version is fine
unless the title itself moved).

### Delete

```graphql
mutation { deleteReservation(id: ID!): DeleteResult }   # generic — needs only an id
```

- Runs rapla's dependency check (`getDependent`); blocked → `HAS_DEPENDENTS`
  error, **§12-filtered** — never name a dependent the caller cannot read.
- §12: deleting an entity the caller cannot see is indistinguishable from
  not-found.
- Appointments / allocations are **components** — removed via the `update*`
  patch (`appointments.remove`), not a separate mutation. Only independent
  entities (Reservation, Allocatable) get a `delete*` mutation.
- `delete*` stays **generic** — no classification payload, so nothing to
  generate per type.

Result shapes: `ReservationMutationResult { reservation, errors }`
(create / update — see below), `DeleteResult { deletedId, errors }`.

## Write-side validation

A generated schema validates write payloads in **two layers** and returns
**structured, detailed errors** — not a 500 with a stack trace.

**Layer 1 — GraphQL schema validation (before execution).** Because
classification input types are generated from the DynamicTypes, GraphQL itself
rejects, at validate-time:

- an attribute key that doesn't exist on the type (unknown field),
- a wrong scalar (`roomCapacity: "many"` where the schema says `Int`),
- a missing required (non-null) attribute.

These never reach a resolver. The error names the exact field path.

**Layer 2 — rapla semantic validation (in the mutation resolver).** GraphQL's
type system has no constraints (no min/max, no regex, no category-membership,
no conflict detection). These run in the resolver and are returned as
**errors-as-data** in the mutation payload — *not* as GraphQL top-level errors
(top-level errors are reserved for protocol/unexpected faults):

```graphql
type ReservationMutationResult {
  reservation: Reservation        # null on failure
  errors: [ValidationError!]!
}
type ValidationError {
  path: String!     # e.g. "classification.attributes.roomCapacity" or "allocations[0]"
  code: String!     # CONSTRAINT_VIOLATION | CONFLICT | NOT_FOUND | PERMISSION
  message: String!  # human-readable, §12-safe (see below)
}
```

§12 applies to error messages too: a conflict message must say *"Room A is
allocated 14:00–15:30"* and never name the conflicting reservation if the
caller cannot read it. A `NOT_FOUND` for an unreadable id is indistinguishable
from a genuinely missing id.

All write mutations funnel through the **same operator/dispatch path** as PRD
009's `dispatch(UpdateEvent)` — conflict detection and persistence are not
reimplemented. The GraphQL mutation is a thin adapter onto it.

## Worked scenario — query, expand, save, validation

A concrete end-to-end walk-through (also the spine of the Phase-3/4 tests).

### 1. Query — find a lecturer's courses (associations as stubs)

```graphql
query {
  courses(filter: { lecturer: { eq: "Dr. Schmidt" } }, first: 20) {
    edges { node {
      id
      classification { typeId attributes }      # component — embedded
      appointments { id start end }             # component — embedded
      allocations {
        allocatable { id type displayName }     # association — STUB
        appointmentIds                          # intra-doc id list
      }
    } }
    pageInfo { hasNextPage endCursor }
  }
}
```

Response: each `allocatable` is a `{id,type,displayName}` stub — enough to
render "Room A" without a second fetch. Allocatables the caller cannot read
are dropped from `allocations[]` entirely (§12) — not returned hollow.

### 2. Expand on demand — pull the room's classification

Same query, the client just selects deeper under the association:

```graphql
      allocations {
        allocatable {
          id type displayName
          classification { typeId attributes }   # EXPAND — resolver fetches it
        }
        appointmentIds
      }
```

No new endpoint, no flag — "expand" is field selection depth. The resolver
re-runs the §12 filter on the expanded entity.

### 3. Save — create a booking

```graphql
mutation {
  createReservation(input: {
    classification: { typeId: "course",
                      attributes: { name: "Algorithms II",
                                    lecturer: "Dr. Schmidt",
                                    roomCapacity: 18 } }
    appointments: [{ start: "2026-06-02T14:00", end: "2026-06-02T15:30" }]
    allocations: [{ allocatableId: "alloc_room_a", appointmentIds: null }]
  }) {
    reservation { id }
    errors { path code message }
  }
}
```

Funnels through the PRD 009 dispatch path; on success `reservation.id` is set,
`errors` is empty.

### 4. Validation failure — three layers, one shape

| Mistake | Caught by | Result |
|---|---|---|
| `roomCapacity: "eighteen"` | Layer 1 — GraphQL schema | Query rejected before execution; error: *field `roomCapacity` expects `Int`*. |
| `roomCapacity: 500` but Room A holds 20 | Layer 2 — resolver constraint | `errors:[{ path:"classification.attributes.roomCapacity", code:"CONSTRAINT_VIOLATION", message:"Room A capacity is 20" }]` |
| Room A already booked 14:00–15:30 | Layer 2 — conflict detection | `errors:[{ path:"allocations[0]", code:"CONFLICT", message:"Room A is allocated 14:00–15:30" }]` — no reservation id if the caller can't read it (§12) |

`reservation` is `null` whenever `errors` is non-empty; the agent or client
gets a precise, machine-readable, §12-safe diagnosis.

## Operation surface

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
  allocatable: Allocatable!          # the double-booked resource (stub)
  start: DateTime!  end: DateTime!   # the overlapping window
  withReservation: Reservation       # §12 — null if the caller cannot read it
}
```

Checks an existing reservation *or* a proposed (unsaved) shape. Classification
is irrelevant to conflicts — the input is appointments × allocatables only.

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

### Open questions

1. **`findFreeSlots` pool-picking.** *Resolved 2026-05-16 — pool-picking is
   v1.* `findFreeSlots` takes `requiredIds` (all free) + `poolIds` (any one
   free; `Slot.picked` names it; tie-break first-in-order). Deferred:
   multiple independent pools, and a one-call embedded `poolFilter`.
2. **`whoIsFree` by category.** v1 takes explicit `subjectIds`; expanding a
   category (e.g. a department) to subject ids is the agent's job for v1.

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

## Auth — scoped API keys, OAuth-compatible

External callers are non-interactive → **long-lived API keys** (PRD 031's
draft API-keys half), extended with **simple scopes**:

- `read` — `graphql_query` and all read fields.
- `write` — create/update mutations.
- `book` — the booking mutation specifically (so a key can be booking-only).

Scopes are OAuth-compatible (expressible as OAuth `scope` claims) so an
interactive MCP host using the Q2-2026 OAuth 2.1 + PKCE flow gets the same
gate. **Two layers, both enforced:** the scope gates the *transport surface*
(which tools/mutations are reachable); rapla's `PermissionController` gates the
*data* (AGENTS.md §12). This resolves PRD 031 (API-keys) OQ#5.

## Scope

**In scope:**

- New module `rapla-mcp` (or `rapla-integration`) in the reactor, depending on
  `rapla-server` + `rapla-core` + `spring-boot-starter-graphql` +
  `spring-ai-starter-mcp-server`.
- `ExternalSchedulingService` shared service layer + GraphQL field resolvers.
- Static structural SDL + the `DynamicType → SDL` classification generator +
  rebuild-on-change hook.
- GraphQL HTTP endpoint (`/api/graphql`), query depth/complexity limits.
- The two-layer write validation + `ValidationError` payload shape.
- MCP server: `graphql_query`, `graphql_schema`, curated mutation tools.
- Scoped API keys (coordinate with PRD 031 API-keys half).
- Three showcase recordings (Phase 0 below) + `docs/development.md` section +
  a `rapla-mcp` skill.

**Out of scope:**

- Replacing `/api/storage/*` (internal client plumbing stays).
- A GraphQL *subscription* (streaming) surface — Phase-2 material.
- Multi-tenant isolation (PRD 002) — single-tenant model, as REST.
- External-IdP integration — inherits from PRD 036.
- Per-tool rate limiting beyond depth/complexity limits — add with a real
  consumer.
- M365 Copilot deployment — deferred to **PRD 036**.
- A GraphQL mutation passthrough for MCP (per-op safety markers lost).

## Plan

### Phase 0 — Showcase scope (drives priority)

Three showcase tracks ranked by strategic value; each dictates which surface
lands first.

| # | Track | Stack | Required surface |
|---|---|---|---|
| 1 | **OpenClaw + Ollama + multi-channel + voice** (lead) | OpenClaw daemon → Ollama → rapla MCP (stdio); WhatsApp + voice + Slack | `graphql_query` (free-slot + reservation fields), `book` |
| 2 | **OpenCode + Ollama (terminal)** | `opencode` CLI → Ollama → rapla MCP | same |
| 3 | **Claude Desktop + Anthropic API** | Claude Desktop → rapla MCP | same |
| 4 | M365 Copilot in Outlook | deferred to PRD 036 | — |

All three share the same surface → Phases 1–4 deliver it.

### Phase 1 — Shared service layer + GraphQL skeleton

1. Add the module to the reactor.
2. `ExternalSchedulingService` + the static **structural** SDL only (no
   classification generation yet).
3. One read query end-to-end (`reservations`), §12 filter in the resolver.
4. OAuth / API-key gate: unauthenticated → 401.
5. Verify `spring-boot-starter-graphql` is Jackson-3 / Spring Boot 4 clean
   (Risk below) before going further.

### Phase 2 — Classification schema generation

1. `DynamicType → SDL` generator (incl. the name-mangling scheme — OQ#1).
2. Rebuild + hot-swap on the `DynamicType` save path.
3. Introspection tested as both data (runtime) and codegen source.
4. Cursor pagination on list fields; custom date/time scalars.

### Phase 3 — Write side + validation

1. `createReservation` / `updateReservation` mutations through the PRD 009
   dispatch path.
2. Two-layer validation + `ValidationError` payload.
3. The worked scenario above becomes the integration test.

### Phase 4 — MCP transport

1. `graphql_query` (read-only) + `graphql_schema` tools.
2. Curated `book` mutation tool, `cautious`-marked, logged.
3. Smoke-test from a Claude Code session.

### Phase 5 — Scoped API keys

Coordinate with PRD 031 (API-keys half): mint/list/revoke + `read`/`write`/
`book` scopes + bearer acceptance.

### Phase 6 — Docs + skill

`docs/development.md` MCP section; `docs/integration-api.md` for the GraphQL
surface; new `.agents/skills/rapla-mcp/SKILL.md`; `README.md` mention.

### Phase 7 — Showcase recording

Once Phases 1–4 land, record the three Phase-0 tracks (~60–90 s each, shared
script). Pin under `docs/showcases/`.

## Tests

- **Tier 1** — `DynamicTypeToSdlTest` (name-mangling, scalars, reference
  attributes, category trees); `ExternalDtoMappingTest` (composition/association
  rule, stub shape, allocation `appointmentIds`).
- **Tier 2** — `ExternalSchedulingServiceTest` against a real `RaplaFacade`
  (`FacadeTestSupport`): query, expand, the two-layer write validation, conflict
  errors.
- **Tier 3** — `GraphQlLeakTest` (mandatory AGENTS.md §12: non-admin user,
  mixed visible/hidden/non-existent ids, response byte-identical to the
  visible-only subset; error messages name no unreadable entity);
  `SchemaRebuildTest` (edit a DynamicType → schema reflects it, in-flight
  queries unaffected).
- **Tier 4** — one `@SpringBootTest(webEnvironment=RANDOM_PORT)` `@Tag("e2e")`:
  MCP `graphql_query` + `book` over the wire, JSON-RPC response shape.
- The **worked scenario** is the Phase-3 integration test verbatim.

## Open Questions — known obstacles

1. **DynamicType attribute keys → GraphQL field names.**
   **Resolved 2026-05-15 — enforce valid keys, no runtime mangling.**
   *Validator checked — `Tools.isKey()`,
   `rapla-core/.../components/util/Tools.java:43-69`:* a rapla key starts with
   `_` / `-` / any Unicode letter and continues with those plus digits. So
   **spaces are already forbidden**, but **hyphens and non-ASCII letters are
   allowed** — `room-capacity`, `Raumgröße` are valid stored keys, and umlauts
   are *likely* given the German-university audience. GraphQL's `Name` rule is
   ASCII-only, no hyphen.
   Decision: GraphQL field names = attribute keys **verbatim** — no mangling
   layer, no `@raplaKey` directive, no schema-gen collision detection. Keys
   are kept GraphQL-valid by enforcement instead:
   - **New keys** — the creation-boundary validator (interactive admin UI +
     bulk DynamicType import) rejects umlauts/hyphens. Nothing invalid enters
     going forward.
   - **Existing invalid keys** — a **migration tool/command** rewrites the key
     *and every reference* in one atomic pass. Keys are referenced *by string*
     in `ParsedText` name-annotation templates and `ClassificationFilterRule`s,
     so a rename is non-local — a hand-edit would silently break name
     templates.
   - On GraphQL-API enable / schema build, if any invalid key remains, **fail
     loud** with a message pointing to the tool. Fail-loud over silent
     auto-migrate: a key rename is visible and referenced, so the admin runs
     the migration deliberately and reviews it.
   The generator then uses keys verbatim — guaranteed valid.
2. **Jackson 3 + Spring for GraphQL.**
   **Resolved 2026-05-15 — no issue.** rapla is on Jackson 3
   (`tools.jackson:jackson-databind:3.1.2`, Spring Boot 4.0.6). Spring for
   GraphQL 2.x ships *with* Spring Boot 4 — same Jackson-3 generation.
   graphql-java is JSON-library-agnostic (own parser, `Map`/`List`
   structures); GraphQL field resolution uses reflection (`PropertyDataFetcher`),
   not Jackson; Jackson only (de)serializes the HTTP request/response envelope
   via Spring MVC's standard converter = Jackson 3. The *residual* risk is the
   **Spring AI MCP starter's** Spring Boot 4 alignment — verify in Phase 1.
   Worst case it drags Jackson 2, which is survivable, not a blocker: the
   current build already runs `com.fasterxml.jackson:jackson-databind:2.21.2`
   (pulled by springdoc-openapi 3.0.0) alongside `tools.jackson:3.1.2` with no
   conflict — `SwaggerJacksonConfig` is the working precedent for isolating a
   Jackson-2 library.
3. **GraphQL API versioning.**
   **Resolved 2026-05-15.** No endpoint versioning — no `/v2/graphql` (a
   versioned GraphQL endpoint is an anti-pattern; clients select fields, so
   additive change is non-breaking by construction). Single evolving schema at
   `/api/graphql`, two policies:
   - **Structural types** — an external contract. Additive evolution is free;
     breaking changes (remove/rename a field, tighten nullability, change a
     type) go through `@deprecated` + a documented sunset window, announced.
     Last-resort unavoidable break: a parallel field (`reservationV2`), never
     a new endpoint.
   - **Classification types** — generated per-deployment, **not a versioned
     contract**. They track the admin's DynamicTypes; an attribute removal is
     a breaking change for a client that selected it, but that is intrinsic to
     the dynamic model and outside the `@deprecated` cadence. Clients codegen
     classification per-deployment and accept this; the SPA sidesteps it by
     rendering classifications data-driven.
   Independent of the PRD 031 SpringDoc groups (those are OpenAPI/REST).
4. **Does GraphQL replace PRD 009's `/api/resources` + `/api/events` CRUD as
   the external surface?**
   **Resolved 2026-05-15.** GraphQL is *the* external surface.
   `/api/resources` + `/api/events` are not promoted to a second external
   contract — they stay internal/transitional (PRD 009) and get absorbed or
   deprecated as GraphQL covers their cases. `/api/storage/*` stays for the
   Swing client unconditionally (Swing is an irreducibly thick
   `RemoteStorage`/`LocalCache` client). The Angular SPA is *also* a thick
   client today (it calls `getResources()` for a full-tree sync), so moving
   the SPA onto GraphQL is a **thick→thin re-architecture**, not a transport
   swap — GraphQL gives a full-sync client nothing. That migration rides on
   the PRD 023/030 thin-client move, incrementally view-by-view, and is a
   **separate follow-on track — PRD 035 ships GraphQL for external + MCP
   without waiting for it.** Until then the thick SPA keeps using
   `/api/storage/*` alongside Swing.
5. **Persisted queries vs. schema rebuild.**
   **Resolved 2026-05-15 — not adopting persisted queries.** The allowlist
   flavor protects public/anonymous endpoints; rapla's GraphQL endpoint is
   authenticated-only, scope-gated, §12-filtered and logged. It's also
   unusable on the two transports that accept arbitrary input (MCP composes
   queries; external integrators author their own) and pointless on the SPA
   (first-party code). The APQ optimization flavor only saves request-payload
   bytes — negligible at rapla's QPS. DoS defense is **depth + complexity
   limiting alone**. With no persisted-query registry, the schema-rebuild
   tension disappears entirely. Revisit only if rapla ever exposes an
   unauthenticated GraphQL endpoint (not in scope).
   *No performance penalty:* the parse+validate cost is recovered via
   graphql-java's `PreparsedDocumentProvider` (server-side cache of
   parsed/validated documents keyed by query hash — same saving as persisted
   queries, no client registration). Flush that cache on schema rebuild. The
   only thing skipped — full query text vs. a hash in the request body — is a
   few KB of upload, negligible at rapla's QPS.
6. **MCP auth: API key vs MCP's OAuth.**
   **Resolved 2026-05-15 — both, no new code.** An OAuth JWT and a rapla API
   key both arrive as `Authorization: Bearer X`, and PRD 031's resource-server
   filter already dispatches them (JWT-decode, else API-key-by-hash). The MCP
   transport inherits that filter.
   - **v1 default — scoped API key.** Paste into the MCP host config
     (`claude mcp add --header`, `claude_desktop_config.json`); no per-user
     OAuth flow. Fits all three Phase 0 showcase tracks (self-hosted,
     single-user). The key's scope gates the MCP tool surface.
   - **OAuth Auth Code + PKCE** — for interactive *multi-user* MCP hosts where
     each end user authenticates as themselves (deployed server; PRD 036
     territory). rapla satisfies the MCP spec's OAuth requirement (it has an
     OAuth server) but also accepts the API-key bearer.
   - Caveat to document: a strictly spec-compliant MCP client may insist on
     OAuth discovery and refuse a static bearer; Claude Code/Desktop allow
     static headers, so the API-key path works there.
   Scope enforcement is identical either way (OAuth `scope` claims / API-key
   scopes → same gate). Phase 6 docs cover both paths.
7. **§12 + DataLoader batching.**
   **Resolved 2026-05-15.** Association resolvers batch via DataLoader to
   avoid N+1; the trap is hoisting the permission check out of the loop. Five
   rules:
   (a) the §12 filter (`PermissionController.canRead(entity, user)`) runs
   **per-entity inside the batch loader**, never per-batch — the batch
   optimizes the *fetch*, not the *filter*;
   (b) the batch loader reads the authenticated user from the per-request
   `GraphQLContext` (`BatchLoaderEnvironment`);
   (c) DataLoaders are **request-scoped** (Spring default) — never
   application-scoped, or a shared id-keyed cache serves one user's filtered
   entity to another (cross-user cache poisoning);
   (d) unreadable *and* non-existent ids both map to `null` in the batch
   result — drop, don't error, don't distinguish; a batch loader must not
   throw for one bad id;
   (e) the mandatory `GraphQlLeakTest` must exercise a **batched** path
   (a query fanning out to many associations, mixed visible/hidden/
   non-existent ids) so a per-batch-filter regression is caught.
8. **Plugin-contributed types.** rapla plugins (`custom/`) may add entities.
   Schema stitching / type extension by plugins — Phase-N, but flag now so the
   generator isn't built closed.
9. **PRD 038/039 entities in the schema.**
   **Resolved 2026-05-15.** PRD 038 (Graph calendar sync) adds no
   schema-visible entities — sync-backend plumbing over existing
   Reservation/Appointment/Allocatable. PRD 039 adds `ExternalCalendarSubscription`,
   `ExternalAppointment`, `AvailabilityWindow`:
   - they enter the GraphQL schema as **structural types when PRD 039 lands**
     (035 ships without them);
   - PRD 039's `BusyOnlyProjection` is implemented as **per-field resolvers** —
     `summary`/`description`/`location`/`url` on `ExternalAppointment` resolve
     to `null` for non-owner/non-admin viewers on `BUSY_ONLY` subscriptions;
     time fields always resolve. This is GraphQL-native field-level authz —
     cleaner than the REST DTO-strip. A non-readable resource drops the whole
     `externalAppointments` association (standard §12);
   - `GraphQlLeakTest` gains an external-appointment privacy case (the
     GraphQL-transport analogue of PRD 039's `ExternalCalendarLeakTest`).
   The schema generator must keep the **structural-type set open** so 039's
   types slot in without a rewrite — same requirement as OQ#8.
10. **Idempotency for `book`.**
    **Resolved 2026-05-15 — not a rapla defect.** Entity ids are allocated
    *before* store (`createIdentifier` → `RemoteStorage.createIdentifier`), so
    a **transport retry** resends the same `UpdateEvent` with the same id:
    `dbStore` sees the entity already exists (`tryResolve`), treats it as an
    update, and version-checks or overwrites identically — never a duplicate
    row. The id *is* the transport-level idempotency key. The only residue is
    **agent re-invocation** — the LLM issues a genuinely fresh `book` tool
    call → fresh id → a second valid reservation. That is not a rapla bug; it
    is a client booking twice (a human clicking "book" twice does the same).
    So OQ#10 collapses to an **MCP-layer guard rail**, not a server fix:
    the tool-description hint ("check for a similar event first") + the
    `cautious` confirmation prompt (Phase 3) cover it. A server-side content
    fingerprint (lock on the fingerprint string, scan recent history, dedup)
    is *optional polish* — implement only if the guard rail proves
    insufficient. No client `idempotencyKey` parameter. *(Distinct from the
    multi-pod stale-cache validation issue surfaced during this analysis —
    that is its own concern, [PRD 040](040-dispatch-validate-before-lock.md).)*
11. **Introspection is a recon surface rapla cannot disable.**
    **Resolved 2026-05-15 — expose the schema whole, introspection on.** It is
    required for the data-driven SPA, so disabling it is not an option.
    Exposure is *schema*-existence (a user learns `CourseClassification.lecturer`
    exists) — not *data*-existence: type/field names are not sensitive, and
    every actual data field still passes the per-field §12 filter. Per-user
    schema scoping rejected — per-user `GraphQL` instances for a marginal
    benefit. Revisit only if a customer objects.
12. **GraphQL returns HTTP 200 even on errors.**
    **Resolved 2026-05-15.** A 200 for an *executed* query carrying field/data
    errors is correct per the GraphQL spec — document it. Serve the
    `application/graphql-response+json` content type so tooling distinguishes.
    Transport-level failures keep proper codes (auth → 401, malformed request
    → 400); only executed operations with an `errors` array return 200.
    Monitoring alerts on the `errors` array, not HTTP status alone.
13. **MCP `graphql_schema` payload.** *Downgraded 2026-05-15* — the schema is
    small (~10–15 core types + one per DynamicType, ~25–40 total). Not a
    context problem. Design note, not an obstacle: the `graphql_schema` tool
    returns **SDL** (~200–300 lines), not raw introspection JSON (the verbose
    form).
14. **Mutation atomicity.** A `createReservation` with N appointments + M
    allocations must map to exactly **one** `UpdateEvent` (PRD 009 dispatch is
    atomic per batch) — never multiple, or a partial write is possible. The
    GraphQL mutation resolver builds one `UpdateEvent` and dispatches once.

## Risks

| Risk | Mitigation |
|---|---|
| Spring AI MCP starter not yet aligned to Spring Boot 4 / Jackson 3 | Verify Phase 1. Spring for GraphQL itself is clean (OQ#2). Worst case the starter drags Jackson 2 — survivable, isolate it like `SwaggerJacksonConfig` does for springdoc |
| Generated schema is a DoS vector (deep/expensive queries) | Depth + complexity limiting, query logging (no persisted-query allowlist — OQ#5) |
| Permission-leak via a clever query traversal | §12 filter in *every* field resolver; mandatory `GraphQlLeakTest`; behave as if the response were a CSV emailed to the user |
| `book` invoked autonomously without confirmation | Curated tool marked `cautious`; host prompts; all `book` calls logged; `created_via=mcp` flag on the reservation for fast revert |
| Name-mangling collisions across DynamicType attributes | Deterministic scheme + collision detection at generation time → fail the rebuild loudly, never silently alias |
| MCP spec churn through 2026 | Spring AI tracks the spec; the exposed surface is simple |

## Open investigations

- **Nextcloud Assistant integration** (potential Track 5; not committed).
  Source review of `nextcloud/context_agent` shows the outbound MCP loader uses
  a single admin-tenant credential — no per-user identity propagation. Draft
  upstream issue at
  [`docs/upstream/nextcloud-context-agent-per-user-mcp-auth.md`](../upstream/nextcloud-context-agent-per-user-mcp-auth.md).
  Empirical verification against a running Nextcloud + Context Agent gates
  filing the issue or adding a Nextcloud showcase track.

## Cross-references

- [PRD 009 — Server bulk-storage REST API](009-server-bulk-storage-rest-api.md) — the dispatch path write mutations funnel through; the `/api/resources` + `/api/events` CRUD whose external role is OQ#4.
- [PRD 029 — Swing OAuth login](029-swing-oauth-login.md) — the OAuth surface.
- [PRD 031 — Token refresh & API keys](031-token-refresh-and-api-keys.md) — the API-keys half; this PRD drives its scope (OQ#5 there).
- [PRD 031 — API namespace redesign](031-api-namespace-redesign.md) — `/api/` namespace; GraphQL at `/api/graphql`, MCP at `/mcp`.
- [PRD 017 — Test coverage strategy](017-test-coverage-strategy.md) — pyramid tiers.
- [PRD 023 — Presenter/view extraction](023-presenter-view-extraction.md) — `AllocationConflictModel` used by conflict detection.
- [PRD 026 — Angular frontend](026-angular-frontend.md) — the SPA consumes the structural schema (codegen) + classification schema (introspection).
- [PRD 034 — CI baseline workflow](034-ci-baseline-workflow.md) — tier-4 tests run in the e2e job.
- [PRD 038 / 039](038-graph-calendar-sync.md) — calendar-sync entities; their schema exposure is OQ#9.
- AGENTS.md §12 — permission-leak invariant; §13 — mock policy; §15 — `/api/` prefix.

## Sources

- [Build an MCP server — Model Context Protocol](https://modelcontextprotocol.io/docs/develop/build-server)
- [Building an MCP Server with Spring AI](https://senoritadeveloper.medium.com/building-an-mcp-server-with-spring-ai-and-testing-with-claude-desktop-e815b5bbd908)
- [Spring for GraphQL reference](https://docs.spring.io/spring-graphql/reference/)
- [GraphQL schema-from-database — Hasura / PostGraphile](https://hasura.io/)
- [The Complete Guide to MCP in 2026](https://www.essamamdani.com/blog/complete-guide-model-context-protocol-mcp-2026)
