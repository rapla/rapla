# PRD 035: GraphQL foundations — external integration API substrate

**Status:** done — architectural foundations landed; active work split into PRDs [056](../056-graphql-events-write-api.md)/[059](059-graphql-typed-where-predicates.md)/[060](../060-graphql-mcp-foundations.md)/[061](../061-graphql-dt-mutations-v2.md) (2026-05-29)
**Date:** 2026-05-13 (original); archived 2026-05-29

This PRD captures the locked architectural foundations of rapla's GraphQL
external integration API. The umbrella draft originally covered the MCP
server, write mutations, search, compute operations, and a long-tail of
follow-on work in a single file. As pieces firmed up and shipped, the
umbrella was split into focused PRDs (see spin-out map). What remains
here is the substrate every active PRD depends on.

## Spin-out map

| Former section | New home |
|---|---|
| §5d typed-where predicates on `allocatables(filter:)` | [PRD 059](059-graphql-typed-where-predicates.md) |
| §6 bulk mutations (typed per-entity) | [PRD 056](../056-graphql-events-write-api.md) (supersedes) |
| §8 new query roots, §9 search root, Compute ops (`findFreeSlots` / `checkConflicts` / `whoIsFree`), MCP transport shape | [PRD 060](../060-graphql-mcp-foundations.md) |
| §7 type change reshape (non-persistent reshape query) | deferred — no active PRD |
| OQs 15–20 (Conflict symmetry, §12 on conflict hits, window semantics, default window, max-range cap, viewport-centered) | [PRD 060](../060-graphql-mcp-foundations.md) |
| Plan Phases 3–7 (write side, MCP, scoped API keys, docs, showcase recording) | PRDs [056](../056-graphql-events-write-api.md)/[060](../060-graphql-mcp-foundations.md)/[043](../043-api-keys-jwt-pat.md) |

## Scope-change context

Originally (2026-05-13) the PRD covered only an MCP server exposing six
fixed `@McpTool` scheduling primitives. A 2026-05-15 design review
broadened it: the deliverable became a **shared task-level service
layer** with **two transports** — an external **GraphQL** API and the
**MCP** server — over a single permission boundary. MCP becomes a thin
hybrid on top of the GraphQL surface rather than a hand-written set of
RPC tools.

The 2026-05-24 refinement then folded the SPA into v1 (the SPA migrates
from the thick `/api/storage/*` client to a thin GraphQL client),
promoted group output interfaces to v1, and revised plugin-contributed
types: plugins get their own per-plugin APIs rather than being stitched
into the core schema. The decisions captured below are the substrate
PRDs [055](../055-graphql-events-read-api.md)/[056](../056-graphql-events-write-api.md)/[059](059-graphql-typed-where-predicates.md)/[060](../060-graphql-mcp-foundations.md)/[061](../061-graphql-dt-mutations-v2.md) build on.

## Goal

Give rapla a deliberate, stable, permission-safe **external integration
surface** — distinct from the internal `/api/storage/*` client plumbing
([PRD 009](../009-server-bulk-storage-rest-api.md)) and the SPA-internal `client` API group (PRD 031). Two
transports, one substrate:

1. **GraphQL external API** — for scripts, custom plugins, custom
   table/report tooling, the Angular SPA, and deployment-coupled
   third-party integrators.
2. **MCP server** — for AI assistants embedded in chat tools. A thin
   hybrid over the GraphQL surface (covered by [PRD 060](../060-graphql-mcp-foundations.md)).

Both sit on **one shared task-level service layer**
(`ExternalSchedulingService` or similar) and enforce the AGENTS.md §12
permission-leak invariant at **one** output boundary — the GraphQL field
resolvers. Neither transport reimplements scheduling logic or the leak
filter.

## Why now

1. **MCP adoption** — 97 M monthly SDK downloads as of March 2026;
   every major AI vendor supports it; Q2 2026 OAuth 2.1 + PKCE which
   rapla already has ([PRD 029](../029-swing-oauth-login.md)).
2. **Spring AI lands the MCP layer for Java** — `@McpTool` + Spring DI
   make wiring concise.
3. **rapla's value is what an LLM agent wants to call.** "Find a free
   90-minute slot next Tuesday for these three people" is one prompt,
   today a five-step REST sequence.
4. **No deliberate external API today.** `/api/storage/*` is internal
   bulk plumbing; `/api/resources` + `/api/events` ([PRD 009](../009-server-bulk-storage-rest-api.md)) are
   in-progress raw-entity CRUD never designed as a stable third-party
   contract. MCP needs a real substrate; building it once for both
   transports avoids divergence.
5. **MCP read surface needs a schema.** A generated GraphQL schema *is*
   that machine-readable description of the deployment's DynamicTypes
   — so the schema work is on the critical path for MCP anyway.

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

- **Shared service layer.** Task-level operations plus the GraphQL field
  resolvers. Both transports call it; neither bypasses it.
- **GraphQL is the external transport.** Decision record below.
- **MCP is a hybrid over GraphQL** (see [PRD 060](../060-graphql-mcp-foundations.md)).
- **One §12 boundary.** The permission-leak filter lives in the
  GraphQL field resolvers — one filter point per type, covering every
  query shape and both transports. Stronger than per-endpoint leak tests.

## SPA-on-GraphQL refinement (2026-05-24)

The 2026-05-24 session tightened v1 around three principles:

1. **Bounded contexts.** The core GraphQL surface covers the
   scheduling domain only. Preferences, plugin features, calendar feed
   export, and modification history have their own API surfaces.
2. **Server-side rendering for views, descriptor-on-edit for forms.**
   The SPA never walks raw classifications on view paths; descriptors
   are fetched only on edit-form open. No long-lived client-side
   typeRegistry, no Zod runtime validation.
3. **Server is authoritative on validation.** Widget pre-validation is
   UX convenience; every save runs L1 (GraphQL schema) + L2 (rapla
   semantic) against the merged full object under [PRD 040](../040-dispatch-validate-before-lock.md)'s lock.

### 1. Three callers, three mutation input shapes

| Caller | Read path | Write path |
|---|---|---|
| Angular SPA | `renderedBlocks` for views; `reservation(id) + type(key)` for edits | Generic `updateReservation(patch: ReservationPatch!)` — accepts type change in `classification.typeKey` (admin-gated) |
| Codegen integrator | Typed `course`/`lecture`/... roots + group-interface roots | Typed `updateCourse(patch: CoursePatch!)` etc. — reject type change with `TYPE_MISMATCH` |
| MCP agent | `graphql_query` over the same surface | Curated `book(...)` (cautious) + the generic mutations |

### 2. View paths vs edit paths

| Path | Query shape | Descriptor needed? |
|---|---|---|
| **View** (calendar grid, table, dashboard) | `renderedBlocks(window, filter, viewType)` — server-rendered display blocks | No |
| **Edit-open** | `reservation(id) + type(key)` in one query — atomic descriptor + entity per edit (~1 KB overhead) | Yes |
| **Admin / introspection** | `types` standalone | Yes (full set) |

`renderedBlocks` is a GraphQL field wrapping
[PRD 030](../030-server-side-view-rendering.md)'s existing
`CalendarLayoutEngine` + `CalendarViewController` substrate (already
shipped Phases 1–6). [PRD 030](../030-server-side-view-rendering.md)'s REST endpoints stay for direct REST
callers; CSV/iCal export stays REST (own API surface).

### 3. Group output interfaces — v1

Output `<Group>` interfaces (typed shared attributes across
DynamicTypes) are v1 alongside the `<Group>Filter` input groups. SPA
queries `courseEvents { status semester { path } lecturer }` and gets
typed cross-DynamicType shared-attribute access without per-deployment
rebuild. Codegen integrators get typed `updateCourseEvent(patch:
CourseEventPatch!)`. The `groups` annotation becomes the SPA's
schema-stability boundary: changing a group definition is an SDK
contract change; adding a member DynamicType is not.

### 4. Descriptor transfer mechanism — descriptor-on-edit (chosen)

**Descriptor query, lazy.** `type(key: String!): DynamicType` fetched
alongside the entity in the same edit-open query (atomic per-edit);
`types: [DynamicType!]!` standalone for admin UIs / MCP
`graphql_schema`. No long-lived client-side `typeRegistry`; no Zod
validation. Descriptor drives widget configuration on form open only.

Rejected alternatives:

- **A — `AttributeValue` value-wrapping union.** Per-value type tagging
  is redundant once descriptors are present; constraints/annotations
  live on `AttributeDescriptor`.
- **C — per-response `typesUsed` / `typeVersions`.** Type changes are
  rare; stale descriptors surface as L2 rejection on save.

### 5. Widget-driven form input + server-authoritative save validation

Form widgets are configured from `AttributeDescriptor`:

| Descriptor signal | Widget |
|---|---|
| `valueType: STRING` + annotation `email`/`url`/`phone` | typed `<input type="...">` |
| `valueType: STRING` + `constraints.maxLength`/`pattern` | text input with HTML5 constraints |
| `valueType: INT` + `constraints.min`/`max` | `<input type="number" min max>` |
| `valueType: CATEGORY` + `constraints.rootCategory` | category picker rooted at that node |
| `valueType: ALLOCATABLE` + `constraints.expectedType` | allocatable picker filtered |
| `required: true` | required marker + submit block |
| `multiplicity: LIST` | wrap widget in add/remove list |

Widgets catch syntactic errors at input time. Server runs L1 (schema) +
L2 (semantic — permissions, conflicts, required satisfaction, reference
integrity, business rules) against the merged full object on every save.
`ValidationError { path, code, message }` maps back to form fields. No
client-side revalidation layer.

**Follow-up (2026-07-08) — name locale = system preference, not JVM default.**
The `@displayName` directives emitted here (and the VALUE_LIST enum names in
§5b, plus the structural `DynamicType.name` / `Allocatable.displayName` /
`Category.name` fetchers) are locale-resolved at SDL-build time. They
originally resolved against `Locale.getDefault()` / `raplaLocale.getLocale()`
(the JVM/bundle default), which leaked English names on a German deployment.
Corrected to read the admin **"Server Sprache"** system preference
(`RaplaLocale.LANGUAGE_ENTRY`) via `ServerLocaleResolver.resolve(...)`, threaded
into `ClassificationSdlGenerator.generate(types, Locale)` and
`StructuralTypeFetchers.wire()`. Full write-up + caveats: [PRD 096](../096-spa-classification-editor.md) bugfix
ride-along (2026-07-08).

### 5a. Category kind discriminator + concrete descriptor schema

Categories bifurcate into value-list (flat picklist) vs organization-tree
(hierarchical). The distinction is **explicit and generic**, not
inferred per-deployment. Permission groups are a separate API surface
(see §5c); the Category API exposes only true value-list and
organization-tree categories.

```graphql
enum CategoryKind { VALUE_LIST  ORGANIZATION  SYSTEM }

type Category {
  id: ID!  key: String!  name: String!  path: String!
  kind: CategoryKind!
  parent: Category  children: [Category!]!
}
```

**Determination of `kind`** (deployment-agnostic, layered):

1. **rapla-core hardcoded**: super-category root → `SYSTEM`;
   `user-groups` subtree filtered out of every Category resolver
   (surfaces only as §5c's `type Group`).
2. **Admin annotation** — `category-kind` on root: `value-list` /
   `organization` wins if present.
3. **Heuristic fallback** — depth = 1 → `VALUE_LIST`; depth ≥ 2 →
   `ORGANIZATION`.

Zero deployment-specific names in code.

**`AttributeDescriptor` — concrete validation surface** carries `key`,
`name`, `valueType`, `multiplicity`, `required`, plus typed CATEGORY
constraints (`rootCategoryPath: String` + `rootCategory: Category`),
ALLOCATABLE constraints (`expectedTypeKey: String` + `expectedType:
DynamicType`), STRING/INT constraints (`maxLength`, `minLength`,
`pattern`, `minIntValue`, `maxIntValue`), and a `hint: String`
(annotation-driven — `"email"`/`"url"`/`"phone"`).

All driven from existing `Attribute.getConstraint(ConstraintIds.KEY_*)`
calls — same data the Swing client uses for its form widgets. One
query carries everything a form needs: widget config, current values,
value spaces.

`ValidationError.code` taxonomy (extensible): `REQUIRED`, `OUT_OF_ROOT`,
`WRONG_ALLOCATABLE_TYPE`, `MAX_LENGTH_EXCEEDED`, `MIN_LENGTH_NOT_REACHED`,
`PATTERN_MISMATCH`, `OUT_OF_RANGE`, `TYPE_MISMATCH`, `PERMISSION_DENIED`,
`CONFLICT`.

### 5b. Enum generation for VALUE_LIST category roots

For category roots whose `kind == VALUE_LIST`, the generator emits a
GraphQL enum whose values mirror the root's children. The typed
classification field uses the enum directly instead of `Category`:

```graphql
enum Raumart { Bueroraeume Hoersaal Labor Pruefungsraum }

type RaumClassification implements Classification & AllocatableClassification {
  type: DynamicType!  typeKey: String!  attributes: [AttributeValue!]!
  Raumart:          Raumart           # enum value, not Category
  AusstattungListe: [Ausstattung!]
  Gebaeude:         Allocatable       # ORGANIZATION roots still return Category
}
```

**AI / GraphiQL discoverability:** `__type(name: "Raumart")`
introspection returns the full value space; GraphiQL autocompletes; no
separate descriptor query needed for VALUE_LIST attributes.

**Naming — verbatim.** Emit the admin-authored rapla key VERBATIM (no
PascalCase, no case folding). Type names join nested paths with `_`
(e.g. `Veranstaltungsattribute_Veranstaltungskategorien`). [PRD 058](../058-graphql-key-spec-migration.md)
guarantees the key is GraphQL-spec-compliant; the generator verifies
via `ClassificationSdlGenerator.checkGraphQlCompliantName`. The
original PascalCase convention collapsed dhbw's DIN room references
(`DIN_5_2_3_11`, `DIN_5_2_31_1`, `DIN_52_3_11`) to one identifier and
silently dropped 2 of every 3 leaves — **[PRD 058](../058-graphql-key-spec-migration.md) owns syntax; admin
owns convention**. The SPA may auto-suggest GraphQL convention at
key-creation time but never rewrites silently.

**Identity stability:** category rename → schema rebuild emits new enum
value name → consumers regenerate or rediscover; bounded by the
existing rebuilder cadence (~10 s). Same model as attribute key
renames.

**What stays as `Category`:** ORGANIZATION-kind roots; heuristic-flip
protection (admin annotation locks kind); cross-cutting
`category(path:)` / `categories(rootKey:)` query roots.

**Resolver adapter:** `Classification.getValue` still returns a
`Category` instance. When the schema declares an enum, the data fetcher
coerces Category → enum value name (via the child key). Save side
reverses.

### 5c. Permission Group — separate API type

Internally rapla stores user permission groups under the `user-groups`
category subtree. **The GraphQL API does NOT expose this as a
Category.** A first-class `type Group { id, key, name }` covers all
group reads; `User.groups: [Group!]!`; `Query.groups` /
`Query.group(id)`.

**Why a separate type:** semantic clarity (categories are
classification values, groups are permission grants); future
extensibility (Groups may grow `membersCount` / `permissions` /
hierarchy); internal-storage independence (if rapla ever moves
permission groups out of the category tree, the API contract doesn't
change); filtering hygiene (admin/MCP `categories(rootKey:)` won't
accidentally surface security-sensitive group data).

**Resolver:** `groups` reads the `user-groups` sub-categories from
`operator.getSuperCategory()`. Initial shipping is flat. Tree support
deferred to a separate PRD.

## §10. Time-field shape — single `LocalDateTime` scalar

Every time-bearing field in the schema is a single custom `LocalDateTime`
scalar (ISO-8601 wall-time, no offset; format `2026-05-25T14:30:00`).
Covers `Appointment.start/end`, `Reservation.firstDate/lastDate`,
`Period.start/end`, `RepeatingRule.end` (via the related `Date` scalar
for the date-only part), every `LocalDateTimeWhere` predicate, every
`ReservationFilter.from/to`, all `createdAt`/`lastModifiedAt` audit
timestamps.

Implementation wired in `GraphQlScalarConfig.LOCAL_DATE_TIME`. Distinct
from `DateTime` (`OffsetDateTime`, used for the few true instant-in-time
fields like `Query.serverTime`) and from `Date` (`LocalDate`).

**Alternatives considered and rejected:**

| Shape | Why rejected |
|---|---|
| Two fields (`date: Date! + time: LocalTime!`) | Doubles every time-bearing field and every filter input; range queries become 3–4 conjunctions; reachable invalid states; splits a single concept across the schema. |
| Plain `String` | Type system says nothing — introspection can't distinguish a time string from a name string; MCP/AI consumers can't reason; validation pushed to every resolver. |
| `DateTime` (offset) | **Wrong domain semantics.** A "10:00 lecture in Berlin" stored as offset shifts an hour at DST transitions. Conflicts with iCal floating-time. [PRD 014](014-appointment-long-to-java-time.md) wall-time invariant is non-negotiable. |

**Rationale:** domain fit (`java.time.LocalDateTime` 1:1, wall-time per
[PRD 014](014-appointment-long-to-java-time.md)); one field per moment; trivial wire format; lexicographically
total-orderable; typed semantics in introspection for MCP/AI.

**SPA guidance:** treat `LocalDateTime` values as opaque strings or map
to `Temporal.PlainDateTime` once it ships. Avoid `new Date(...)` — it
parses as local time in the browser, but the `Date` carries an implicit
zone and `.toISOString()` reshapes through UTC. Opaque-string is safer.

**Tradeoffs accepted:** day-equality queries take a window
(`gte 2026-05-25T00:00:00, lt 2026-05-26T00:00:00`); "all-day vs timed"
requires a sibling `allDay: Boolean` flag on `Appointment` (matches
iCal's `VALUE=DATE` / `VALUE=DATE-TIME` discriminator).

## §11. `typeKey` vs `typeId` — `typeKey` only

Surfaced by the [PRD 056](../056-graphql-events-write-api.md) happy-path test: `Classification.typeId`
returned the DynamicType UUID, but the input `CreateReservationInput.typeId`
was the discriminator that had to match the `@oneOf` variant name (the
verbatim DynamicType key). Same field name, two semantics.

**Decision:** `typeId` dropped entirely from Classification + generated
typed impls. Final surface:

| Field | What it returns | When to use |
|---|---|---|
| `typeKey: String!` | Human-readable key (`"event"`, `"room"`). Unique across deployment. | Discriminator for `@oneOf` variant. Codegen typed-narrow fragment selector. SPA display. `@expectedType(key:)` directive target. |
| `type: DynamicType!` | Full DynamicType ref. | When you need other fields on the type (name, classificationType, `id` for the rare UUID-needing consumer). |

Reasoning:

- Rapla enforces DynamicType key uniqueness across the deployment, so
  `typeKey` alone unambiguously identifies the type.
- Nothing user-facing uses the UUID. `@oneOf` variants, typed-narrow
  fragments (`... on eventClassification`), generated enum names, MCP
  tool descriptions, SPA codegen all use the key.
- Verbatim key emission means a key rename is already a breaking
  change for the GraphQL schema, so caching the UUID doesn't save you.
- Carrying both forced every consumer to decide "which one do I use?".
- The deref escape hatch `type { id }` is still available.

**Inputs.** `CreateReservationInput.typeKey` +
`UpdateReservationInput.typeKey`. `AttributeInput.expectedTypeKey`
([PRD 057](057-graphql-dt-mutations-v1.md)) — target DynamicType key for ALLOCATABLE attributes,
matching the `@expectedType(key:)` directive on the read side.

**Mechanical implementation:** `Classification`,
`AllocatableClassification`, `ReservationClassification` interfaces
declare `typeKey: String!` + `type: DynamicType!` only;
`ClassificationSdlGenerator` emits both on every generated typed impl;
`StructuralTypeFetchers.CLASSIFICATION_TYPE_KEY` returns `dt.getKey()`;
`GeneratedClassificationWiring` re-registers on every generated
implementation; `HotSwappableGraphQlSource.validateInterfaceCoverage`
covers the three interfaces.

**Lesson.** Whenever a field name appears on both input and output,
check that each direction has the same semantic — or use different
names.

## Out of core GraphQL — own API surfaces

Bounded contexts: the core GraphQL surface is scheduling-domain only.
These each have their own API surface:

| Subsystem | Surface | Why |
|---|---|---|
| Preferences + preference-derived features | Separate preferences subsystem (PRD TBD) | Hidden behind purpose-built typed endpoints; not a generic key/value bag |
| Saved calendar configurations | Preferences subsystem | Stored in preferences today |
| Plugin features | Per-plugin API (REST today; GraphQL per-plugin if a plugin wants it) | Plugins are independent contexts; not stitched into the core schema |
| Calendar feed export (`/rapla/calendar.csv`, `/rapla/ical`) | Existing literal URLs (AGENTS.md §15 allow-list) | External subscribers depend on URLs; stays REST |
| CSV export (`/export/csv`) | [PRD 030](../030-server-side-view-rendering.md)'s existing REST endpoint | Bytes-streamed download; GraphQL fits poorly |
| Modification history / audit log | Separate PRD (TBD) | Distinct concern |
| Resource utilization aggregates | Follow-on | Not blocked; compute operations later |

## External API data model

The external DTOs are **hand-built, versioned, a tree not a graph, with
zero back-references**. They are *not* the internal entity wire format.

### Consumer-driven read surfaces (locked 2026-05-27)

Read consumers fall into four shapes; the schema is designed so each
shape gets the natural primitive for its job.

| Consumer | Primitive | Why |
|---|---|---|
| **Editor open / save** | `Reservation` (deep, with `allocations[]` restriction-aware) | Round-trip must preserve restriction structure — `Reservation.allocations` is the lossless source of truth |
| **Listviews + per-appointment allocation visibility** | `Reservation { appointments { allocatables[] } }` — restrictions pre-resolved per appointment | No client-side join; the workhorse query for SPA list/table views |
| **iCal / CalDAV export** | `Appointment` with `repeating[]` rule intact, no expansion | iCal carries RRULE / EXDATE natively; expanding to blocks throws away the structure |
| **Calendar grid rendering** | `Appointment.blocks(from:, to:)` sub-resolver — materialized expansion | Calendar grid indexes by time; server-side expansion via `Appointment.createBlocks` avoids reimplementing rapla's recurrence semantics in JS |
| **Scheduling pre-flight** | `checkConflicts(...)` returning `Conflict` aggregated at appointment-pair + dates | A weekly clash between two recurring lectures is **one** conflict spanning N dates, not N — see [domain-model.md §Conflict](../../architecture/domain-model.md#conflict-facade-level-computed) |

**Block is a sub-resolution of Appointment, not a peer query root.**
No top-level `appointmentBlocks(filter:)` query. Calendar UI selects
`appointments(filter:) { blocks(from:, to:) { ... } }` and flatMaps
client-side. Block has no `allocatables` field of its own — they come
via `block.appointment.allocatables`.

**Two-shape allocation exposure rationale.** `Reservation.allocations`
(restriction-aware structure) and `Appointment.allocatables`
(pre-resolved list) serve different consumers and live at different
abstraction levels. Editor uses the restriction structure; every other
consumer uses the resolved view.

### Out of the External API data model — what stays REST

The literal-URL feed exports under
[AGENTS.md §15 allow-list](../../../AGENTS.md) — `/rapla/ical`,
`/rapla/calendar.csv` — stay on REST regardless. External calendar
subscribers depend on the URLs.

> **Why not reuse the internal format.** [PRD 009](../009-server-bulk-storage-rest-api.md) Risk 1 is the canary:
> the internal Jackson format serializes raw `EntityImpl` graphs whose
> back-refs must be patched `@JsonIgnore` getter by getter. That format
> is tied to `LocalCache` and is a moving target. A tree-shaped
> external DTO set sidesteps the entire class of round-trip bugs by
> construction.

### Composition vs association — the one mechanical rule

| Relationship | Treatment | Examples |
|---|---|---|
| **Component** (owned, no independent lifecycle) | Always embedded in full | `appointments[]`, `classification` |
| **Association** (points at an independently-managed entity) | **Stub** `{ id, type, displayName }`; expandable | allocatable refs, category refs, owner/user refs |

In GraphQL terms a component is a nested object type reached with no
resolver round-trip; an association is an object-typed field whose
resolver does the id-lookup + §12 filter. A "stub" is the association's
object type with only `id`/`type`/`displayName` selected.

### Per-type shape

- **Event (`Reservation`)** — top-level booking:
  `{ id, classification, appointments:[...], allocations:[...], ownerId,
  lastChanged, canModify }`. Appointments array *owns* its children; an
  Appointment carries **no** `eventId` back-pointer.
- **Appointment** — `{ id, start, end, allDay, repeating:{ type,
  interval, end|count, exceptions[] }, allocatables:[<stub>] }`.
  start/end are `LocalDateTime`. Recurrence two ways: raw `repeating`
  rule, or materialized via `blocks(from:, to:)`. **`allocatables` is
  per-appointment pre-resolved view** — restrictions already applied.
- **Allocation** — `{ allocatable: <stub>, appointmentIds: [...] | null }`.
  Restriction structure exposed: `appointmentIds` is **intra-document
  id list** (the appointments are components of the same event). `null`
  = bound to every appointment. The editor's lossless source-of-truth
  — the only place where restriction structure is visible on the wire.
- **Allocatable (resource/person)** — `{ id, type:"resource"|"person",
  displayName, classification }`. Person allocatables may link to a
  `User` — expose `displayName` only, never the account/login.
- **Classification** — a component, embedded full:
  `{ typeKey, type, attributes:{...} }`. Reference-valued attributes
  *inside* it are stubs.

### Stub fields

`{ id, type, displayName }`. `displayName` is **locale-resolved** (one
string, not a map; locale from `Accept-Language` or a locale bound to
the API key) and for allocatables/events it is the
**classification-derived** display name.

§12: a stub only ever appears when the caller can read the referenced
entity (unreadable reference is *dropped*, never emitted hollow). The
name on the stub is therefore §12-safe by construction.

### Write side

Mutations accept the same shapes but with associations as **bare ids**
the caller supplies. An unknown id and an unreadable id fail
**identically** (§12 existence rule) — the external caller cannot probe
existence.

## Schema design — structural-static + classification-generated

| Part of schema | Shape | Lifecycle |
|---|---|---|
| **Structural types** — `Reservation`, `Appointment`, `Allocatable`, allocation, references, query/mutation roots, scalars | Static SDL, shipped with the build | Never changes per deployment → codegen once, type-safe forever |
| **Classification types** — one GraphQL type per `DynamicType` (`CourseClassification { lecturer: String, roomCapacity: Int }`) | Generated from the deployment's DynamicTypes at startup | Rebuilt on admin change |

**Rebuild on admin change.** Hook the `DynamicType` save path (rapla's
`UpdateEvent` storage stream). On change, regenerate the classification
SDL, rebuild the `GraphQLSchema`, hot-swap the immutable `GraphQL`
instance. In-flight queries finish on the old instance; no restart.

### One schema, two consumption modes

| Mode | Consumers | Property |
|---|---|---|
| **Runtime / introspection** (schema-as-data) | SPA classification rendering, dynamic selector / column-picker UIs, MCP `graphql_schema` tool | Data-driven rendering → admin DynamicType change picked up by browser reload, no client rebuild |
| **Build-time codegen** (schema-as-source) | Custom plugin authors, custom table *components*, deployment-coupled external integrators | Typed `CourseClassification.lecturer`, typed filter inputs, compiler-checked |

These are the *same* schema. The duality is exactly what GraphQL
introspection gives for free.

**Consequence for the SPA:** classification rendering must be
**data-driven** (iterate `attributes` from a runtime schema descriptor)
— TypeScript types are erased at build, so typed classification access
would force a frontend rebuild+redeploy on every DynamicType change.
The SPA codegens only the *structural* types (stable) and renders
classifications generically.

**The `Classification` interface (decision 2026-05-15 — "Approach A").**
The classification output carries an interface
`Classification { typeKey: String!  type: DynamicType! }`, and each
generated `CourseClassification implements Classification` adds the
typed per-attribute fields. SPA queries the interface-level generic
attributes (via introspection) and renders via one descriptor-driven
component; deployment-coupled codegen consumers use the typed fields.

### Schema implementation — type resolution & data fetching

rapla has **no Java class per DynamicType** — a `lecture` and a `test`
are both `ReservationImpl`; the type is *data*. So the generated
GraphQL types are **virtual** — no backing class — and the usual
GraphQL mechanisms do not apply: no POJO field fetchers, no
`instanceof` type resolution.

The layer is **schema-first with programmatic wiring**: the
DynamicType→SDL generator produces the schema; a wiring step attaches
fetchers and resolvers programmatically (Spring for GraphQL
`RuntimeWiringConfigurer`), rebuilt with the schema on DynamicType
change. Four pieces:

1. **`TypeResolver`** — one per interface. Dispatches on **data, not
   Java type**: `entity → getClassification().getType().getKey() →
   generated type name`.
2. **Generic attribute `DataFetcher`** — every generated classification
   field is served by one key-parameterized fetcher
   (`source.getClassification().getValue(key)`). Where the §12 filter is
   applied (no DataLoader shipped — see OQ#7).
3. **Structural field fetchers** — `id`, `appointments`, `allocations`,
   `owner` on the `Reservation` / `Allocatable` interfaces — written
   once, shared by all concrete types.
4. **Roots** — fixed roots may be annotation-driven `@QueryMapping`;
   the *generated* per-type / group roots are wired programmatically.

Annotation-driven `@SchemaMapping` is used only for the fixed
structural parts. This is the standard pattern for dynamic GraphQL
schemas (Hasura et al. have no class-per-table either).

## Filter & query language

Queries filter along **three independent axes**:

| Axis | Expressed as | Covers |
|---|---|---|
| **Structural** | query arguments | `from`/`to` (appointment time), `owner`, `allocatableIds`, `typeIn` |
| **Classification** | `filter` / `classificationFilter` argument | dynamic attributes — `status`, `capacity`, `lecturer` |
| **Traversal** | connection fields | `allocatable(id) { reservations(…) }` — "what is booked on this resource" |

`allocatableIds` is first-class: "reservations for an allocatable in a
time range" is rapla's *primary* facade query.

### Structural axis

Top-level arguments on every reservation/resource root —
`reservations(typeIn:, from:, to:, owner:, allocatableIds:, first:,
after:)`. They map straight to facade-query parameters; never touch
`ClassificationFilter`.

### Classification axis — typed per-type filters

**Fixed comparison inputs** — hand-written, stable, one per scalar
kind: `IntFilter` (eq/ne/gt/gte/lt/lte/in), `StringFilter`
(eq/ne/in/contains/startsWith), `DateTimeFilter`, `BooleanFilter`,
`CategoryFilter` (eq/in/descendantOf), `AllocatableFilter` (eq/in). All
fields optional; multiple fields in one comparison = AND.

**Generated per-DynamicType filter inputs** — the generator emits one
filter input per type:

```graphql
input CourseFilter {
  _and: [CourseFilter!]   _or: [CourseFilter!]   _not: CourseFilter
  lecturer:   StringFilter
  capacity:   IntFilter
  department: CategoryFilter
}
```

GraphQL rejects at validate-time: unknown attribute, wrong value type,
wrong operator — client codegen turns each into a build error.

### Classification axis — cross-type queries

A query spanning types (`typeIn: ["lecture","test"]`) cannot use a
per-type filter — the attribute schemas differ. Two routes:

- attributes shared by a **declared group** → typed group filter;
- otherwise → a **runtime-validated** `classificationFilter`:
  ```graphql
  reservations(typeIn: ["lecture","test"], classificationFilter: {
    rules: [{ attribute: "status", conditions: [{ operator: EQ, value: "planned" }] }]
  })
  ```
  Resolver validates each named attribute exists on **every** `typeIn`
  type — else `INVALID_ATTRIBUTE` error.

### Traversal axis

`Allocatable.reservations(typeIn:, from:, to:, filter:)` — the events
allocating that allocatable.

### Query modes

| Mode | Root | Classification filter |
|---|---|---|
| Per-type | `courses(filter: CourseFilter)` | typed, compile-time-checked |
| Declared group | `courseEvents(filter: CourseEventFilter)` | typed over group's shared attributes — a primary cross-type surface |
| Ad-hoc cross-type | `reservations(typeIn:, classificationFilter:)` | runtime-validated — ad-hoc escape hatch |
| Fully agnostic | `reservations(from:, to:)` | structural only |

### Shared attributes — declared groups

Cross-type *typed* filtering needs attributes several types share with
the **same key and same value-type**. Three solutions considered:

1. **Auto-intersection onto interface** — *rejected*: fragile, adding
   one divergent type silently removes a field (breaking change).
2. **Declared type groups (chosen)** — DynamicType annotation declares
   membership (`group: "CourseEvent"`); generator emits a `CourseEvent`
   interface + `CourseEventFilter`. Typed and stable.
3. **Runtime-validated `classificationFilter`** — always available, not
   compile-time typed; demoted to ad-hoc escape hatch.

Cross-type queries are pervasive in rapla, so groups are v1. They form
an interface hierarchy:

```
Reservation (interface)        structural only — all reservation types
  └ CourseEvent (interface)    declared group — + shared typed attributes
      └ Lecture, Test (types)  concrete — + type-specific attributes
```

The `groups` annotation is a **list** — a type may belong to several
(`Lecture implements Reservation & CourseEvent & GradedActivity`). The
top interfaces stay structural only. No field collision is possible —
every interface field for a concrete type derives from that type's
single attribute of that key, and the group-intersection rule
guarantees agreement.

**Semantic caveat:** "same key + same value-type" is a structural
match, not semantic. `lecture.status` and `test.status` may both be
`string` yet have different value domains. The runtime `types`
descriptor carries each type's real per-attribute constraints.

### Input vs output groups — both v1

The 2026-05-24 refinement promoted output group interfaces to v1
alongside input group filters. **Input group** (`CourseEventFilter`)
is the typed `filter:` argument for cross-type filtering. **Output
group** (`CourseEvent` interface) gives direct typed selection of
shared attributes:

```graphql
courseEvents(filter: { status: { eq: "planned" } }) {
  edges { node { status  semester { path } } }
}
```

### Execution — no engine rewrite

The resolver normalizes a typed filter tree to disjunctive normal form
(OR of ANDs) and maps it onto rapla's existing `ClassificationFilter[]`:
each AND-clause → one `ClassificationFilter`, OR across clauses → the
array, `_not` pushed to the leaf (negated operator). For cross-type the
resolver builds one `ClassificationFilter` per `typeIn` type. The
existing `matches` engine executes all of it unchanged.

### Scope boundary

`ClassificationFilter` is the *historic execution engine*, reused
as-is. The generated typed inputs are the *new API surface* — the two
are deliberately decoupled. Modernizing the internal/stored filter
model and the Swing filter UI is separable, larger, and out of scope.

### §12

Filter results pass the per-entity permission filter; a predicate must
never leak existence — a filter matching only hidden entities returns
identically to one matching nothing. Per-controller leak tests (e.g.
`ResourceAccessQueryGraphQLTest`, `UsersControllerLeakTest`,
`MutationExistenceLeakTest`) cover filtered paths — there is no single
`GraphQlLeakTest` class.

## GraphQL transport — decision record

The external HTTP transport is **GraphQL**, not REST. Each objection
was raised and resolved — re-litigating it later wastes a session:

| Objection | Resolution |
|---|---|
| GraphQL's static SDL can't express rapla's admin-configurable DynamicTypes | Schema-from-config is a proven pattern (Hasura, PostGraphile). rapla generates classification types from DynamicTypes. |
| A generated schema goes stale when an admin edits a type | Rebuild + hot-swap on the `DynamicType` save path. Server-side correctness is instant. |
| Client codegen goes stale → forces a rebuild | Only if a client codegens the *dynamic* part. Structural types are static (codegen once); classification is consumed data-driven via introspection. |
| MCP agents shouldn't author query strings | Frontier models compose GraphQL reliably; on-demand field selection is a *context-budget* win. MCP uses a `graphql_query` tool ([PRD 060](../060-graphql-mcp-foundations.md)). |
| Operational surface (DoS via deep/expensive queries) | Per-request wall-clock deadline (`GraphQlExecutionDeadlineInstrumentation`, `rapla.graphql.execution-budget-millis`, default 30s). Depth/complexity limiting was never implemented — it wouldn't catch shallow-but-wide queries anyway. (Persisted-query allowlisting deliberately not adopted — OQ#5.) |

What tipped it: the **multi-consumer unification**. One schema serves
the SPA, custom plugins, custom tables, external REST consumers *and*
the MCP read surface.

**Not chosen / out of scope:** GraphQL passthrough for MCP *mutations*
(loses per-operation safety markers); REST with sparse-fieldsets as
primary transport (Spring for GraphQL is Jackson-3-clean on Spring
Boot 4 — OQ#2 — so REST fallback no longer needed).

## Write mutations — design substrate

> **[PRD 056](../056-graphql-events-write-api.md) supersedes the bulk-mutations design (§6 of the original
> umbrella).** The patch-principle and validation substrate described
> below remain the foundation [PRD 056](../056-graphql-events-write-api.md) builds on.

All write mutations funnel through rapla's existing
`UpdateEvent`/dispatch path; the whole mutation maps to **exactly one
`UpdateEvent`**, dispatched once (OQ#14) — no partial writes.

### Patch principle — clobber-proof by construction

`update*` mutations are **patches**, not full-entity replacements. The
resolver: load the current entity (fresh, under the dispatch lock — see
[PRD 040](../040-dispatch-validate-before-lock.md)) → apply the delta →
dispatch the merged full entity. The client sends only what it changes;
every omitted field is filled from current server state, so a client
that does not know about field X structurally cannot erase X. This is
server-side merge, done in the resolver — no legacy `dbStore` change.

### Concurrency

`expectedVersion` (or `expectedLastChanged`) optional. Provided and
stale → `CONFLICT` error, patch not applied. Omitted → apply the delta
onto current — already safer than full-entity last-write-wins, since a
patch touches only named fields and therefore *merges*.

## Write-side validation

A generated schema validates write payloads in **two layers** and
returns **structured, detailed errors** — not a 500 with a stack trace.

**Layer 1 — GraphQL schema validation (before execution).** Because
classification input types are generated from the DynamicTypes, GraphQL
itself rejects, at validate-time:

- an attribute key that doesn't exist on the type,
- a wrong scalar,
- a missing required (non-null) attribute.

These never reach a resolver. The error names the exact field path.

**Layer 2 — rapla semantic validation (in the mutation resolver).**
GraphQL's type system has no constraints (no min/max, no regex, no
category-membership, no conflict detection). These run in the resolver
and are returned as **errors-as-data** in the mutation payload — *not*
as GraphQL top-level errors:

```graphql
type ReservationMutationResult {
  reservation: Reservation        # null on failure
  errors: [ValidationError!]!
}
type ValidationError {
  path: String!     # e.g. "classification.attributes.roomCapacity" or "allocations[0]"
  code: String!     # CONSTRAINT_VIOLATION | CONFLICT | NOT_FOUND | PERMISSION
  message: String!  # human-readable, §12-safe
}
```

§12 applies to error messages too: a conflict message must say *"Room A
is allocated 14:00–15:30"* and never name the conflicting reservation if
the caller cannot read it. A `NOT_FOUND` for an unreadable id is
indistinguishable from a genuinely missing id.

All write mutations funnel through the **same operator/dispatch path**
as [PRD 009](../009-server-bulk-storage-rest-api.md)'s `dispatch(UpdateEvent)` — conflict detection and
persistence are not reimplemented. The GraphQL mutation is a thin
adapter onto it.

The full code taxonomy, bulk semantics, atomic-vs-partial mode,
`applyChanges` escape hatch, and the typed-per-DynamicType input
generation are elaborated in **[PRD 056](../056-graphql-events-write-api.md)**.

## Worked scenario — query, expand, save, validation

A concrete end-to-end walk-through (spine of the Phase-3 tests).

**1. Query** — find a lecturer's courses (associations as stubs):

```graphql
courses(filter: { lecturer: { eq: "Dr. Schmidt" } }, first: 20) {
  edges { node {
    id
    classification { typeKey attributes }     # component — embedded
    appointments { id start end }             # component — embedded
    allocations {
      allocatable { id type displayName }     # association — STUB
      appointmentIds                          # intra-doc id list
    }
  } }
}
```

Allocatables the caller cannot read are dropped from `allocations[]`
entirely (§12) — not returned hollow.

**2. Expand on demand** — client selects deeper under the association
(`allocations { allocatable { ... classification { typeKey attributes } } }`).
No new endpoint, no flag — "expand" is field selection depth. The
resolver re-runs the §12 filter on the expanded entity.

**3. Save** — create a booking via `createReservation(input: {
classification: { typeKey: "course", attributes: {...} },
appointments: [...], allocations: [...] }) { reservation { id }  errors
{ path code message } }`.

**4. Validation failure** — three layers, one shape:

| Mistake | Caught by | Result |
|---|---|---|
| `roomCapacity: "eighteen"` | Layer 1 — GraphQL schema | Query rejected before execution |
| `roomCapacity: 500` but Room A holds 20 | Layer 2 — resolver constraint | `errors:[{ path:"classification.attributes.roomCapacity", code:"CONSTRAINT_VIOLATION", message:"Room A capacity is 20" }]` |
| Room A already booked 14:00–15:30 | Layer 2 — conflict detection | `errors:[{ path:"allocations[0]", code:"CONFLICT", message:"Room A is allocated 14:00–15:30" }]` — no reservation id if caller can't read it (§12) |

`reservation` is `null` whenever `errors` is non-empty.

## Auth — scoped API keys

External callers are non-interactive → **long-lived API keys**, extended
with **simple scopes** (`read`, `write`, `book`). Scopes are
OAuth-compatible. **Two layers, both enforced:** the scope gates the
*transport surface*; rapla's `PermissionController` gates the *data*
(AGENTS.md §12).

Full design and implementation details:
[PRD 043 — API keys (JWT PAT)](../043-api-keys-jwt-pat.md). This PRD's
auth needs are covered there.

## Scope

**In scope (foundation, this archive):**

- New module `rapla-mcp` (or `rapla-integration`) in the reactor,
  depending on `rapla-server` + `rapla-core` +
  `spring-boot-starter-graphql` + `spring-ai-starter-mcp-server`.
- `ExternalSchedulingService` shared service layer + GraphQL field
  resolvers.
- Static structural SDL + `DynamicType → SDL` classification generator
  + rebuild-on-change hook.
- GraphQL HTTP endpoint (`/api/graphql`), query depth/complexity
  limits.
- Two-layer write validation + `ValidationError` payload shape.

**Out of scope:**

- Replacing `/api/storage/*` (internal client plumbing stays).
- GraphQL *subscription* (streaming) surface — Phase-2 material.
- Multi-tenant isolation (PRD 002).
- External-IdP integration — inherits from [PRD 036](../036-external-idp-oauth-login.md).
- Per-tool rate limiting beyond depth/complexity limits.
- M365 Copilot deployment — deferred to [PRD 036](../036-external-idp-oauth-login.md).

## Plan — phases that landed

### Phase 0 — Showcase scope (drives priority)

Three showcase tracks ranked by strategic value; each dictates which
surface lands first.

| # | Track | Stack | Required surface |
|---|---|---|---|
| 1 | **OpenClaw + Ollama + multi-channel + voice** (lead) | OpenClaw daemon → Ollama → rapla MCP (stdio); WhatsApp + voice + Slack | `graphql_query` (free-slot + reservation fields), `book` |
| 2 | **OpenCode + Ollama (terminal)** | `opencode` CLI → Ollama → rapla MCP | same |
| 3 | **Claude Desktop + Anthropic API** | Claude Desktop → rapla MCP | same |

All three share the same surface.

### Phase 1 — Shared service layer + GraphQL skeleton

1. Add the module to the reactor.
2. `ExternalSchedulingService` + the static **structural** SDL only (no
   classification generation yet).
3. One read query end-to-end (`reservations`), §12 filter in the
   resolver.
4. `/api/graphql` is `permitAll` by design — unauthenticated requests
   return HTTP 200 with an `UNAUTHENTICATED` error; access is gated
   per-field by §12, not by an endpoint-level 401.
5. Verify `spring-boot-starter-graphql` is Jackson-3 / Spring Boot 4
   clean.

### Phase 2 — Classification schema generation

1. `DynamicType → SDL` generator (incl. name-mangling — OQ#1).
2. Rebuild + hot-swap on the `DynamicType` save path.
3. Introspection tested as both data (runtime) and codegen source.
4. Cursor pagination on list fields; custom date/time scalars.
5. **β read simplification (locked 2026-05-28, [PRD 055](../055-graphql-events-read-api.md) decision log):**
   drop `attributes: [AttributeValue!]!` from the `Classification`
   interface and `AttributeDescriptor` from `DynamicType.attributes`.
   Schema becomes the **one source of truth** for both values and
   descriptor metadata — values via the typed `<TypeKey>Classification`
   fields; descriptor metadata via custom directives on those generated
   fields.
6. Custom directives: `@expectedType(name: String!)`,
   `@multiplicity(min: Int!, max: Int)`, `@required`,
   `@enumDomain(values: [String!]!)`. Emitted by the SDL generator on
   each `<TypeKey>Classification` field. SPA + codegen consumers read
   them via introspection.
7. SPA dynamic query construction via `__type(name: ...)` introspection
   at app start — discover `<TypeKey>Classification` fields + their
   directives, build editor/listview queries on the fly.
8. Symmetric β² (write side, [PRD 056](../056-graphql-events-write-api.md) dependency): typed
   per-DynamicType input types (`Create<TypeKey>ClassificationInput`)
   mirror the read types — same hot-swap, same introspection-driven SPA
   construction.

### Phases 3–7 — see spin-out PRDs

- Phase 3 (write side + validation): [PRD 056](../056-graphql-events-write-api.md).
- Phase 4 (MCP transport): [PRD 060](../060-graphql-mcp-foundations.md).
- Phase 5 (scoped API keys): [PRD 043](../043-api-keys-jwt-pat.md).
- Phase 6 (docs + skill): rolled into PRDs [056](../056-graphql-events-write-api.md)/[060](../060-graphql-mcp-foundations.md).
- Phase 7 (showcase recording): follows [PRD 060](../060-graphql-mcp-foundations.md).

## Tests

- **Tier 1** — `DynamicTypeToSdlTest` (name-mangling, scalars,
  reference attributes, category trees); `ExternalDtoMappingTest`
  (composition/association rule, stub shape, allocation
  `appointmentIds`).
- **Tier 2** — `ExternalSchedulingServiceTest` against a real
  `RaplaFacade` (`FacadeTestSupport`): query, expand, two-layer write
  validation, conflict errors.
- **Tier 3** — the §12 leak-test convention (AGENTS.md §12: non-admin
  user, mixed visible/hidden/non-existent ids, response byte-identical
  to the visible-only subset; error messages name no unreadable
  entity); `SchemaRebuildTest` (edit a DynamicType → schema reflects
  it, in-flight queries unaffected).
- **Tier 4** — one `@SpringBootTest(webEnvironment=RANDOM_PORT)`
  `@Tag("e2e")`: MCP `graphql_query` + `book` over the wire, JSON-RPC
  response shape.
- The **worked scenario** is the Phase-3 integration test verbatim.

## Open Questions — resolved decision log

1. **DynamicType attribute keys → GraphQL field names.** GraphQL field
   names = attribute keys **verbatim** — no mangling layer. Keys kept
   GraphQL-valid by enforcement: creation-boundary validator rejects
   umlauts/hyphens for new keys; migration tool rewrites existing
   invalid keys + every reference (`ParsedText` name templates,
   `ClassificationFilterRule`s) in one atomic pass; schema build
   fails loud if any invalid key remains.
2. **Jackson 3 + Spring for GraphQL.** No issue. rapla on Jackson 3;
   Spring for GraphQL 2.x ships with Spring Boot 4. graphql-java is
   JSON-library-agnostic. Residual risk: Spring AI MCP starter's SB4
   alignment (verified in Phase 1).
3. **GraphQL API versioning.** No endpoint versioning. Single evolving
   schema at `/api/graphql`. Structural types follow `@deprecated` +
   sunset cadence; classification types are per-deployment, not a
   versioned contract.
4. **Does GraphQL replace [PRD 009](../009-server-bulk-storage-rest-api.md)'s CRUD?** GraphQL is *the* external
   surface. `/api/resources` + `/api/events` stay internal/transitional.
   `/api/storage/*` stays for the Swing client unconditionally. SPA
   migration is v1 (per the 2026-05-24 refinement).
5. **Persisted queries vs schema rebuild.** Not adopting persisted
   queries. rapla's GraphQL endpoint is authenticated-only,
   scope-gated, §12-filtered, logged. DoS defense is depth + complexity
   limiting alone. Parse+validate cost recovered via graphql-java's
   `PreparsedDocumentProvider`; flush on schema rebuild.
6. **MCP auth: API key vs MCP's OAuth.** Both, no new code. OAuth JWT
   and rapla API key both arrive as `Authorization: Bearer X`; PRD 031
   filter dispatches them. v1 default — scoped API key; OAuth Auth Code
   + PKCE for interactive multi-user MCP hosts.
7. **§12 leak protection.** As shipped there is **no DataLoader** and
   **no `GraphQlLeakTest`**. Leak protection is per-controller inline
   `canRead` in each field resolver: the resolver reads the
   authenticated user from the per-request `GraphQLContext` and drops
   entities the user can't read, so unreadable *and* non-existent ids
   both resolve to `null` (existence never leaks). The batched-loader
   design and its five locked rules described in earlier drafts were
   never implemented.
8. **Plugin-contributed types.** Revised 2026-05-24: plugins get their
   own per-plugin API surfaces — **not** stitched into the core schema.
   No schema-stitching / federation machinery needed.
9. **PRD [038](../038-graph-calendar-sync.md)/[039](../039-external-ical-subscription-per-resource.md) entities in the schema.** [PRD 038](../038-graph-calendar-sync.md) adds no schema-visible
   entities. [PRD 039](../039-external-ical-subscription-per-resource.md) adds `ExternalCalendarSubscription`,
   `ExternalAppointment`, `AvailabilityWindow` as structural types when
   it lands (035 ships without them). [PRD 039](../039-external-ical-subscription-per-resource.md)'s `BusyOnlyProjection` is
   implemented as **per-field resolvers** — `summary`/`description`/
   `location`/`url` resolve to `null` for non-owner/non-admin viewers
   on `BUSY_ONLY` subscriptions; time fields always resolve. Schema
   generator keeps structural-type set open.
10. **Idempotency for `book`.** Not a rapla defect. Entity ids
    allocated *before* store (`createIdentifier`), so a transport retry
    resends same id; `dbStore` version-checks or overwrites identically.
    The id *is* the transport-level idempotency key. Agent re-invocation
    covered by MCP-layer `cautious` prompt + tool-description hint. No
    client `idempotencyKey` parameter.
11. **Introspection as recon surface.** Expose schema whole,
    introspection on — required for data-driven SPA. Exposure is
    *schema*-existence, not *data*-existence: every data field still
    passes per-field §12 filter. Per-user schema scoping rejected.
12. **GraphQL returns HTTP 200 even on errors.** Correct per GraphQL
    spec. Serve `application/graphql-response+json`. Transport failures
    keep proper codes (auth → 401, malformed → 400). Monitoring alerts
    on the `errors` array, not HTTP status.
13. **MCP `graphql_schema` payload.** Downgraded — schema is small
    (~25–40 types). Returns **SDL** (~200–300 lines), not raw
    introspection JSON.
14. **Mutation atomicity.** A `createReservation` with N appointments +
    M allocations maps to exactly **one** `UpdateEvent` ([PRD 009](../009-server-bulk-storage-rest-api.md)
    dispatch is atomic per batch).

OQs 15–20 (Conflict symmetry, §12 on conflict hits, window semantics,
default window, max-range cap, viewport-centered) are owned by [PRD 060](../060-graphql-mcp-foundations.md).

## Risks

| Risk | Mitigation |
|---|---|
| Spring AI MCP starter SB4/Jackson 3 alignment | Verify Phase 1; worst case isolate Jackson 2 like `SwaggerJacksonConfig` |
| DoS via deep/expensive queries | Per-request wall-clock deadline (`GraphQlExecutionDeadlineInstrumentation`, `rapla.graphql.execution-budget-millis`, default 30s); query logging (no depth/complexity limiting — shallow-but-wide queries slip it; no persisted-query allowlist — OQ#5) |
| Permission-leak via clever traversal | §12 `canRead` in *every* field resolver; per-controller leak tests (no single `GraphQlLeakTest` class shipped) |
| `book` invoked autonomously without confirmation | Curated tool marked `cautious`; host prompts; all `book` calls logged; `created_via=mcp` flag for fast revert |
| Name-mangling collisions | Deterministic scheme + collision detection at generation time → fail rebuild loudly |
| MCP spec churn through 2026 | Spring AI tracks the spec; exposed surface is simple |

## Open investigations

- **Nextcloud Assistant integration** (potential Track 5; not
  committed). Source review of `nextcloud/context_agent` shows the
  outbound MCP loader uses a single admin-tenant credential — no
  per-user identity propagation. Draft upstream issue at
  [`docs/upstream/nextcloud-context-agent-per-user-mcp-auth.md`](../../upstream/nextcloud-context-agent-per-user-mcp-auth.md).
  Empirical verification against a running Nextcloud + Context Agent
  gates filing the issue or adding a Nextcloud showcase track.

## Deferred — not v1, by deliberate decision

- `findFreeSlots` multiple independent pools + one-call embedded
  `poolFilter` — v1 has single explicit-id `poolIds` (owned by [PRD 060](../060-graphql-mcp-foundations.md)).
- Resource (allocatable) **writes** — v1 is booking-focused.
- Subscriptions / streaming — Phase 2.
- Rate limiting / per-key quotas — add with a real consumer.
- **Type change reshape** (§7 of the original umbrella —
  non-persistent reshape query): no active PRD; revisit when a
  consumer needs cross-type editing UX.

## Cross-references

- [PRD 009 — Server bulk-storage REST API](../009-server-bulk-storage-rest-api.md) — the dispatch path write mutations funnel through.
- [PRD 014 — appointment long → java.time](../done/014-appointment-long-to-java-time.md) — `LocalDateTime` scalar rationale.
- [PRD 028 — Angular power search](../028-angular-power-search.md) — substrate provided by [PRD 060](../060-graphql-mcp-foundations.md).
- [PRD 029 — Swing OAuth login](../029-swing-oauth-login.md) — the OAuth surface.
- [PRD 030 — Server-side view rendering](../030-server-side-view-rendering.md) — `renderedBlocks` wraps its substrate.
- [PRD 031 — Token refresh & API keys](../031-token-refresh-and-api-keys.md) — the API-keys half (now [PRD 043](../043-api-keys-jwt-pat.md)).
- [PRD 040 — Dispatch validate before lock](../040-dispatch-validate-before-lock.md) — coupled dependency for bulk; lock-set computation includes allocatables across the batch.
- [PRD 043 — API keys (JWT PAT)](../043-api-keys-jwt-pat.md) — scoped API key mechanism.
- [PRD 055 — GraphQL Events Read API](../055-graphql-events-read-api.md) — Reservation/Appointment/Allocation read surface, β read simplification (reopened 2026-05-29 for Tier-1 perf migration).
- [PRD 056 — GraphQL Events Write API](../056-graphql-events-write-api.md) — supersedes the former §6 bulk-mutations design; ATOMIC-only v1, named verbs + `applyChanges` escape hatch.
- [PRD 057 (done) — DT mutations v1](057-graphql-dt-mutations-v1.md) — `@expectedType` directive consumers; v2 follow-ups in [PRD 061](../061-graphql-dt-mutations-v2.md).
- [PRD 058 — GraphQL key spec migration](../058-graphql-key-spec-migration.md) — guarantees verbatim emission is safe.
- [PRD 059 — Typed `<TypeKey>Where` predicates](../059-graphql-typed-where-predicates.md) — per-attribute filtering on `allocatables(filter:)`.
- [PRD 060 — GraphQL MCP foundations](../060-graphql-mcp-foundations.md) — new query roots, search, `findFreeSlots`/`checkConflicts`/`whoIsFree`, MCP transport shape.
- [PRD 061 — GraphQL DT mutations v2](../061-graphql-dt-mutations-v2.md) — see this PRD's tree for active mutation work.
- [domain-model.md §Conflict](../../architecture/domain-model.md#conflict-facade-level-computed) — conflict aggregation.
- [domain-model.md §Reservation](../../architecture/domain-model.md#reservation) — restriction structure.
- [AGENTS.md §12 — permission-leak invariant](../../../AGENTS.md); §13 — mock policy; §15 — `/api/` prefix.

## Sources

- [Build an MCP server — Model Context Protocol](https://modelcontextprotocol.io/docs/develop/build-server)
- [Building an MCP Server with Spring AI](https://senoritadeveloper.medium.com/building-an-mcp-server-with-spring-ai-and-testing-with-claude-desktop-e815b5bbd908)
- [Spring for GraphQL reference](https://docs.spring.io/spring-graphql/reference/)
- [GraphQL schema-from-database — Hasura / PostGraphile](https://hasura.io/)
- [The Complete Guide to MCP in 2026](https://www.essamamdani.com/blog/complete-guide-model-context-protocol-mcp-2026)
