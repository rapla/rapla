# GraphQL API — generic query catalog

PRD 035 exposes a GraphQL endpoint at `POST /api/graphql` covering
allocatables (resources + persons), classifications, dynamic types, and
reservations (PRD 055/066). Live UIs: GraphiQL at `/graphiql/`,
Scalar at `/scalar/`, schema-as-data via introspection.

---

## TODO — Angular SPA admin convention helper (PRD 035 §5b follow-up)

> **Big TODO, not yet implemented.** Land alongside the schema-editor
> work or as a separate small PRD.

**Problem.** Rapla keys go into the GraphQL schema verbatim — no
PascalCase, no SCREAMING_SNAKE, no automatic transformation
(PRD 058 + PRD 035 §5b revision 2026-05-28). That's the right
boundary: PRD 058 owns syntax (the GraphQL identifier regex),
admins own convention (case style). But it means an admin who keys a
DynamicType `room` and category leaves `seminar_raum` / `hoersaal`
gets a schema like:

```graphqls
type roomClassification implements Classification { ... }
enum raumtyp { seminar_raum hoersaal }
```

That's **spec-valid GraphQL** — graphql-java loads it, queries work,
introspection returns it as-is. But it deviates from convention
(PascalCase type names, SCREAMING_SNAKE enum values), and some
codegen / lint tooling will warn or auto-rename downstream.

**What we should NOT do.** Imposing the convention server-side (e.g.
having the SDL generator PascalCase type names or uppercase enum
values) re-introduces the same class of bug PRD 058 just removed:
silent transformations destroy information. Concrete burn 2026-05-28
on dhbw: `DIN_5_2_3_11` / `DIN_5_2_31_1` / `DIN_52_3_11` all collapsed
to `DIN52311`; SDL generator silently dropped 2 of every 3 leaves
with a WARN. Verbatim emission was the fix.

**What we should do.** The Angular schema-editor screens should
**auto-suggest** the GraphQL convention at key-creation time:

- DynamicType key field — show a soft hint "Convention: PascalCase
  (e.g. `Room` instead of `room`)" and offer a one-click
  "Apply convention" button that rewrites the input
- Category leaf key field (when the parent root is VALUE_LIST) —
  same hint pattern with SCREAMING_SNAKE_CASE suggestion
  (e.g. `HOERSAAL` instead of `hoersaal`)
- Attribute key — camelCase (matches GraphQL field convention)
- Never enforce; never silently rewrite on save. The admin's
  explicit choice always wins. The hint is education, not policy.

This lives in the SPA's schema-editor forms (PRD 057
`createDynamicType` / `updateDynamicType` mutation consumers when
those ship). Server-side, the existing PRD 058 spec check is the only
gate — same as today.

**Why this is fine to defer.** Existing dhbw deploys already have keys
in mixed conventions; the verbatim emission preserves whatever the
admin chose. Schema convention deviation is cosmetic and tool-handled
(graphql-codegen normalizes for target-language types). No urgency
from the server side. The hint UX lands when the schema editor lands.

---

This doc is a deployment-agnostic tour. **Deployment-specific examples**
(real type keys like `Raum`/`Gebaeude`/`Lehrveranstaltung`, role
walkthroughs against actual users, capacity / building / equipment
queries) live alongside that deployment — see for example
`dhbwrapla/docs/graphql.md`.

For the schema design, the `Classification` interface split (§540 lock-in),
and the SPA-on-interface contract see
[PRD 035 (done) — GraphQL foundations](prd/done/035-graphql-foundations.md).

## Testing the queries in this doc

Every fenced ```graphql block in this file and in
`~/git/dhbwrapla/docs/graphql.md` is executable against a running rapla
server. The schema-as-data nature of the API plus rapla's PRD 058
verbatim-key emission means doc drift is real — queries go stale
silently when admins rename a DynamicType / attribute / category root,
or refactor the schema.

The companion script `docs/test-graphql-doc.sh` runs every executable
block in a graphql.md file against a live server and reports per-block
OK/ERR with the GraphQL error messages. ```graphqls fenced blocks
(schema definitions like `input X { … }`, `enum Y { … }`,
`type Z { … }`) are intentionally skipped — they're SDL documentation,
not queries.

```bash
RAPLA_USER=your.user@example.org RAPLA_PASS=secret \
  ./docs/test-graphql-doc.sh                                # tests this generic doc

RAPLA_USER=... RAPLA_PASS=... \
  ./docs/test-graphql-doc.sh ~/git/dhbwrapla/docs/graphql.md  # tests the dhbw deployment-specific tour
```

Defaults to the local dev server on `:8051`; override with `RAPLA_URL`.
`QUIET=1` suppresses the per-OK output, keeping only failures. Exit
code 0 = all blocks OK, 1 = at least one block errored, 2 = auth or
network failure.

Run after editing either doc; run before merging if you've touched
schema-shaping code (`ClassificationSdlGenerator`, migration paths,
controller resolvers).

## Auth

GraphQL is a regular `/api/*` endpoint behind Spring Security. Get a JWT
via the password grant (rapla-client public OAuth client):

```bash
TOK=$(curl -s -X POST http://localhost:8051/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=rapla-client&grant_type=password" \
  -d "username=YOUR_USER" \
  -d "password=YOUR_PASS" \
  -d "scope=read+write" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")
```

Then attach to every request:

```bash
gq() {
  curl -s -X POST http://localhost:8051/api/graphql \
    -H "Authorization: Bearer $TOK" \
    -H "Content-Type: application/json" \
    -d "$1" | python3 -m json.tool
}
```

GraphiQL / Scalar / Swagger UI all read the SPA's `localStorage.access_token`
— log in via the SPA at `/app/login` first; the explorer panels pick the
token up automatically.

## Schema topology

```
Query
├── me, users(filter:), user(username:)            # user surface
├── periods                                        # rapla period model
├── category(path:), categories(rootKey:)          # category tree
├── types, type(key:)                              # DynamicType descriptors
└── allocatables(filter:), allocatable(id:)        # resources + persons

Classification (interface, base — shape: typeKey, type)
├── AllocatableClassification  (narrows resources + persons)
│   ├── <typeKey>Classification  ← one per resource/person DynamicType, generated
└── ReservationClassification  (narrows reservations)
    └── <typeKey>Classification  ← one per reservation DynamicType, generated

# Rapla-internal scaffolding types (annotation classification-type=rapla
# — period / template / defaultUser / anonymousEvent) are filtered out
# of every GraphQL surface: not in `types`, not in `type(key:)`, not in
# `allocatables*`, no generated Rapla*Classification. They remain
# accessible via the dedicated query roots (`periods`, …) and via the
# operator for the subsystems that need them.
```

**β refactor 2026-05-28** removed the `attributes: [AttributeValue!]!`
field from the `Classification` interface AND the `attributes:
[AttributeDescriptor!]!` field from `DynamicType`. Per-attribute structure
lives on the generated `<typeKey>Classification` types — read via
introspection + SDL custom directives (`@displayName`,
`@expectedType`, `@rootCategory`, `@multiplicity`, `@required`).

**SPA pattern:** introspect the schema to learn the deployment's
attribute layout, then construct typed-narrow queries dynamically:
`... on roomClassification { name seats Gebaeude { displayName } }`.
**Codegen consumer pattern:** same typed fragments, but as static
documents — regenerate types when admin edits a DynamicType (rare,
explicit, deploy-coupled).

---

## Categories, enums, and groups — design model

rapla's category tree serves three distinct purposes that look the same
in storage but should look different at the API boundary. The GraphQL
schema makes this split explicit (PRD 035 §5a-c).

### Three category modes

| Mode | API surface | When schema rebuilds |
|---|---|---|
| **Value-list** (flat picklist — e.g. an attribute's allowed values) | Generated enum, one per root | On category leaf add/remove/rename in that root |
| **Hierarchical organization tree** (e.g. department/location hierarchies) | Generic `Category` with `kind: ORGANIZATION` | Never — runtime tree-walk, schema-stable |
| **Permission groups** (the `user-groups` subtree internally) | Separate `type Group` | Internal-only changes don't affect the schema |

Determination is per category root, deployment-agnostic:

1. **rapla-core rule** — the `user-groups` subtree is filtered out of every
   Category resolver and surfaces only via `type Group`. Categories under
   the super-category that match rapla-internal names are SYSTEM-kind
   (hidden).
2. **Admin annotation** — `category-kind: value-list` / `organization` on
   the category root wins if present.
3. **Heuristic** — root with depth = 1 (no grandchildren) → `VALUE_LIST`;
   depth ≥ 2 → `ORGANIZATION`. Stable structure-based check; no
   deployment-specific root names baked into rapla code.

The deployment data (which roots are which) is purely the admin's call;
the mechanism is the same at every install.

### `Category` and `CategoryKind`

For ORGANIZATION-kind and SYSTEM-kind categories (plus generic admin/MCP
exploration of any category including the trees backing VALUE_LIST
roots), the generic Category type:

```graphqls
enum CategoryKind {
  VALUE_LIST     # flat picklist — also surfaces as a generated enum (see below)
  ORGANIZATION   # hierarchical tree — render as drill-down picker
  SYSTEM         # rapla-internal (super-category etc.) — not normally exposed
}

type Category {
  id:       ID!
  key:      String!
  name:     String!       # locale-resolved
  path:     String!       # slash-separated keys from root
  kind:     CategoryKind!
  parent:   Category
  children: [Category!]!
}
```

The `kind` field lets a generic consumer (SPA renderer, MCP tool, admin
explorer) render any Category appropriately without prior deployment
knowledge — flat dropdown vs tree breadcrumb. Discovery in GraphiQL is
one query:

```graphql
{ categories { name kind } }
```

### Generated enums per VALUE_LIST root

For every root with `kind: VALUE_LIST`, the schema also generates a
GraphQL enum whose values mirror the root's children. Classification
typed fields targeting those roots use the enum directly:

```graphqls
enum Raumart {
  """Büroräume allgemein"""
  Bueroraeume
  """Hörsaal"""
  Hoersaal
  """Labor"""
  Labor
  """Prüfungsraum"""
  Pruefungsraum
}

type roomClassification implements Classification & AllocatableClassification {
  typeKey: String!
  type: DynamicType!
  Raumart:          Raumart                 @displayName(value: "Raumart")    @rootCategory(path: "Raumtypen")
  AusstattungListe: [Ausstattung!]          @displayName(value: "Ausstattung") @rootCategory(path: "Ausstattungen") @multiplicity(value: LIST)
  SyncStatus:       SyncStatus              @displayName(value: "Sync-Status")
  Gebaeude:         Allocatable             @displayName(value: "Gebäude")     @expectedType(key: "building")
}
```

Sanitization rule for enum value names (mandatory because GraphQL
enums must match `[_A-Za-z][_0-9A-Za-z]*`):

- Umlauts ASCII-folded: `ä` → `ae`, `ö` → `oe`, `ü` → `ue`, `ß` → `ss`
- Spaces / dashes / dots / colons → `_`, then collapse repeats
- Non-identifier characters dropped or replaced
- Result must start with a letter or underscore
- Locale-resolved name carried via the enum value's `description`

**Identity stability across admin edits:** category rename → schema
rebuild emits a new enum value → consumers regenerate (codegen) or
rediscover (introspection). Same model as DynamicType attribute key
renames (which we already accept). Category renames are admin-rare;
the rebuild churn is acceptable. Admin-add is safe (existing queries
unaffected, new value visible after rebuild). Admin-delete invalidates
in-flight queries referencing the removed value during the ~10 s
hot-swap window.

**Why enums for VALUE_LIST roots specifically:**

- AI / GraphiQL discoverability — `__type(name: "Raumart")` introspection
  returns the full value space; autocomplete shows values inline when
  typing predicates. No separate descriptor query for value discovery.
- Type-safe filtering — `{ Raumart: { eq: Bueroraeume } }` is validated
  at parse time; UUID-typed predicates aren't.
- Smaller wire payload — enum value names vs full Category objects
- Codegen consumers get typed enum types in TypeScript / Java.

**What stays as `Category` (not enum):**

- ORGANIZATION-kind roots — schema rebuild on every leaf change would
  be too churn-prone; hierarchical walks need the parent/children API.
- Cross-cutting `category(path:)` / `categories(rootKey:)` query roots
  return `Category` for any root including VALUE_LIST (admin tooling
  and debugging benefit from the generic shape).
- Heuristic-flip protection: admin annotation
  `category-kind: value-list` locks the kind so accidental depth growth
  doesn't break consumers (grandchildren on a value-list root simply
  aren't enum values).

### Permission groups — separate `type Group`

Internally rapla stores permission groups under the `user-groups`
category subtree. **The GraphQL API does NOT expose this as a Category.**
A first-class `type Group` covers all group reads. This keeps the
Category contract focused and gives the API a clean affordance for
permission-group operations:

```graphqls
type Group {
  id:   ID!
  key:  String!
  name: String!
  # Hierarchical extensions deferred to a separate PRD —
  # `parent: Group` and `children: [Group!]!` may or may not appear.
}

type User {
  ...
  groups: [Group!]!
}

type Query {
  groups: [Group!]!
  group(id: ID!): Group
}
```

`categories(rootKey: "user-groups")` returns null / empty — the subtree
is not addressable through the Category surface. Admin tooling that
needs the underlying tree must use the Group queries.

The Group type may grow hierarchical fields (`parent` / `children`) in
a future PRD if real consumer needs surface. Initial shipping is flat.

---

## Core query catalog

Substitute `"<TypeKey>"` with an actual key from `{ types { key } }`,
and `<typeKey>Classification` with the corresponding PascalCased
generated type name (e.g. key `course-group` → type `CourseGroupClassification`).

### 1. who am I

```graphql
{ me { username name isAdmin authSource } }
```

### 2. list DynamicTypes (the deployment's schema-as-data)

```graphql
{ types { key name classificationType } }
```

Returns only user-visible classifications — internal rapla scaffolding
(`rapla:*`) is filtered server-side.

### 3. one DynamicType — basic metadata

```graphql
{ type(key: "<TypeKey>") { key name classificationType } }
```

**β refactor 2026-05-28:** `DynamicType.attributes` was removed.
Per-attribute metadata lives on the generated `<typeKey>Classification`
type — discover it via introspection (query 4 below).

### 4. allocatables — schema-as-data via introspection

The right SPA shape after the β refactor: discover the deployment's
attribute layout by introspecting the generated classification types,
then construct typed-narrow queries dynamically.

Introspection — what attribute fields exist on a given classification:

```graphql
{
  __type(name: "<typeKey>Classification") {
    name
    interfaces { name }
    fields {
      name
      type { name kind ofType { name kind ofType { name kind } } }
    }
  }
}
```

The field `name` is the attribute key. The field `type` (unwrapping
through `ofType` for `[X!]` / `!` wrappers) tells you the value type:

- `name: "String" | "Int" | "Boolean"` etc. — scalar attribute
- `name: "<EnumName>"`, `kind: "ENUM"` — VALUE_LIST CATEGORY attribute
- `name: "Category"` — ORGANIZATION CATEGORY attribute
- `name: "Allocatable"` — ALLOCATABLE attribute (server validates the
  expected DynamicType per the `@expectedType` directive)

SDL directives carry the bits introspection alone doesn't expose:

| Directive | When emitted | Read |
|---|---|---|
| `@displayName(value: "...")` | Always (locale-resolved at SDL-gen time) | Human-readable form label |
| `@required` | Attribute is `!isOptional()` | Save-time required, read-time still nullable (legacy data may carry null) |
| `@multiplicity(value: BELONGS_TO \| PACKAGE)` | ALLOCATABLE only, non-default multiplicity | Widget hint (vs plain LIST/SINGLE which is implied by the field type wrapper) |
| `@expectedType(key: "...")` | ALLOCATABLE attrs with a DynamicType constraint | Filter the allocatable picker |
| `@rootCategory(path: "key/path")` | CATEGORY attrs with an admin-set root | Allowed root for the category picker |

Once the SPA has the descriptor info, the read query targets the
specific classification's typed fields directly:

```graphql
{
  allocatables(filter: { typeKeyEq: "<typeKey>" }) {
    id
    displayName
    classification {
      ... on <typeKey>Classification {
        # The SPA injects field selections per the descriptor it just
        # learned. Apollo/urql `gql(string)` accepts dynamically-constructed
        # query documents; codegen consumers use static fragments.
      }
    }
  }
}
```

### 5. allocatables — typed narrowing (codegen consumer path)

DO NOT do this from the SPA — type names are deployment-coupled. Codegen
consumers with a fixed deployment use this for compile-time field access:

```graphql
{
  allocatables(filter: { typeKeyEq: "<TypeKey>" }) {
    displayName
    classification {
      ... on <typeKey>Classification {
        # Replace with actual attribute keys from query 3.
        someStringAttribute
        someIntAttribute
      }
    }
  }
}
```

### 6. filters

```graphql
# only one type
{ allocatables(filter: { typeKeyEq: "<TypeKey>" }) { displayName } }

# only persons
{ allocatables(filter: { isPersonEq: true }) { displayName } }

# substring name search (case-insensitive)
{ allocatables(filter: { nameContains: "foo" }) { displayName } }

# combined AND
{ allocatables(filter: { typeKeyEq: "<TypeKey>", nameContains: "foo" }) { displayName } }
```

### 7. follow a reference attribute

When an attribute is typed `Allocatable` (per the `@expectedType(key:)`
directive on the field), chain through to the referenced entity's typed
classification:

```graphql
{
  allocatables(filter: { typeKeyEq: "<TypeKey>" }) {
    displayName
    classification {
      ... on <typeKey>Classification {
        someStringAttribute
        someReferenceAttribute {     # this is an Allocatable
          displayName
          classification {
            ... on <OtherKey>Classification {
              someStringAttributeOnTarget
            }
          }
        }
      }
    }
  }
}
```

§12 is enforced at every step — an unreadable target yields a null
reference, never a partial object.

### 8. multi-query batch

One round-trip for a workbench:

```graphql
query Workbench {
  me { username isAdmin }
  types: types { key }
  things: allocatables(filter: { typeKeyEq: "<TypeKey>" }) {
    id displayName
  }
  serverNow: serverTime
}
```

### 9. introspection — see the interface split

```graphql
# only resources + persons
{ __type(name: "AllocatableClassification") { possibleTypes { name } } }

# only reservations
{ __type(name: "ReservationClassification") { possibleTypes { name } } }

# detailed shape of one generated type
{
  __type(name: "<typeKey>Classification") {
    interfaces { name }
    fields { name type { name kind ofType { name } } }
  }
}
```

### 10. interface mismatch (validation error, by design)

A resource-typed `Allocatable.classification` rejects an event-only
narrowing at validation time — SPA mistakes never produce silent nulls:

```graphql
{
  allocatables(filter: { typeKeyEq: "<ResourceKey>" }) {
    classification { ... on <ReservationKey>Classification { typeId } }
  }
}
```

Yields:
```
Fragment cannot be spread here as objects of type 'AllocatableClassification'
can never be of type '<ReservationKey>Classification'
```

### 11. reservations + the calendar query (PRD 055 + PRD 066)

`reservations(filter:)` requires a mandatory time window and supports
three orthogonal ways to select which allocatables drive the result —
mirroring the calendar UI's tree-selection model (type checkboxes,
per-type filter rules, explicit ticks). The combined input shape is:

```graphqls
input ReservationFilter {
  from:                LocalDateTime!     # inclusive
  to:                  LocalDateTime!     # exclusive
  typeKeyEq:           String             # narrow to one reservation DT
  ownerEq:             ID                 # who created it
  allocatableIdsIn:    [ID!]              # PRD 055 — uses ANY of these ids
  allocatableMatching: AllocatableFilter  # PRD 066 — uses ANY allocatable matching this filter
  nameContains:        String
  limit:               Int                # default 500, hard cap 5000
}
```

`allocatableMatching` reuses the full `AllocatableFilter` shape — the
same `typeKeyIn` + per-type `whereXxx` (PRD 059) + `idIn` the calendar
sidebar produces. Semantic: result is the union of the type-bucket
predicate set and `idIn`; per-type filter rules apply only to the
type-bucket; `idIn` is additive and ignores filter rules.

```graphql
# Calendar query — "show me events in this building over the semester"
{
  reservations(filter: {
    from: "2026-04-01T00:00:00"
    to:   "2026-09-30T00:00:00"
    allocatableMatching: {
      typeKeyEq: "<ResourceKey>"
      where<ResourceKey>: { <RefAttribute>: { eq: "<reference-id>" } }
    }
  }) {
    firstDate
    classification {
      typeKey
      ... on <ReservationKey>Classification { displayField1 displayField2 }
    }
  }
}
```

```graphql
# Mixed tree-selection — type bucket + explicit picks
{
  reservations(filter: {
    from: "..."
    to:   "..."
    allocatableMatching: {
      typeKeyIn: ["<TypeA>", "<TypeB>"]      # type checkboxes
      where<TypeA>: { ... }                  # per-type filter rule
      idIn: ["<id-1>", "<id-2>"]             # additive ticks (any types)
    }
  }) { firstDate classification { typeKey } }
}
```

§12 invariants on `reservations(filter:)`:

- Anonymous caller → `[]` unconditionally
- Each reservation passes `pc.canRead(r, caller)` post-loop
- Each allocatable in `idIn` / `allocatableMatching` passes `pc.canRead(a, caller)` at resolution time — unreadable ids drop silently (existence not leaked)
- Both `idIn` and `allocatableIdsIn` are subject to §12; explicit picks do NOT bypass `canRead`

Window cap is configurable via Spring Boot property
`rapla.graphql.max-query-window-days` (default null = no cap). Result
size caps at 5000 entries (default 500). Per-deployment example
queries with real dataset numbers live in
[`dhbwrapla/docs/graphql.md`](../../dhbwrapla/docs/graphql.md)
§"Reservation queries".

#### Nested `Appointment.allocatables(filter:)` — PRD 073

Each appointment exposes its pre-resolved allocatable list. An optional
`filter` argument narrows it using `AppointmentAllocatableFilter` — a
strict subset of `AllocatableFilter` containing only the v1 scalar
predicates (`typeKeyEq`, `typeKeyIn`, `isPersonEq`, `nameContains`,
`searchText`, `matchKind`, `ownerEq`). Fields like `idIn`, `limit`,
`accessibleBy*`, and generated `where<TypeKey>` blocks are intentionally
absent — passing them is a GraphQL validation error, not a silent no-op.

The canRead gate runs **before** the filter, so a hidden allocatable can
never leak even when it would match.

```graphql
# Split rooms and lecturers per appointment in one query
{
  reservations(filter: { from: "...", to: "..." }) {
    appointments {
      start end
      rooms:     allocatables(filter: { isPersonEq: false }) { displayName }
      lecturers: allocatables(filter: { isPersonEq: true  }) { displayName }
    }
  }
}
```

```graphql
# Narrow to a specific resource type key in the nested list
{
  reservations(filter: { from: "...", to: "..." }) {
    appointments {
      start end
      allocatables(filter: { typeKeyIn: ["<TypeA>", "<TypeB>"] }) {
        displayName
        classification { typeKey }
      }
    }
  }
}
```

`AppointmentAllocatableFilter` does **not** support `idIn` / `limit` /
`accessibleBy*` / `where<TypeKey>` — use `Query.allocatables(filter:)` for
those. The nested filter is for structural column splitting (rooms vs.
persons vs. a named type), not for cross-appointment id selection.

---

## Hot-swap probe

When an admin saves a DynamicType change (e.g. adds an attribute):

1. `GraphQlSchemaRebuilder` polls `operator.getDynamicTypes()` every 10 s.
2. SHA-256 of the generated SDL is compared against the last run —
   identical SDL short-circuits the rebuild.
3. Otherwise the schema is rebuilt and atomically swapped; in-flight
   queries finish on their captured `GraphQL` instance.

Verify:

```graphql
# Before admin save:
{ __type(name: "<typeKey>Classification") { fields { name } } }
# Admin adds an attribute "NewFlag" on <TypeKey>.
# Wait ~10s, then re-query:
{ __type(name: "<typeKey>Classification") { fields { name } } }
# 'NewFlag' should now appear in the fields list.
```

If introspection doesn't update but `type(key:)` does, the rebuilder is
the issue, not the operator — check server logs for
`GraphQL schema rebuilt — N DynamicType classification(s) regenerated`.

---

## §12 expectations (permission boundary)

Every read goes through `PermissionController.canRead` at the output
boundary — see AGENTS.md §12 and PRD 035 line 491-500. The contract:

- **Anonymous callers** see `me: null`, empty `users`, empty `allocatables`.
  Trivial probes (`hello`, `serverTime`, `version`) and the schema /
  introspection remain open (admin/debug aid).
- **Self-visibility**: every authenticated user always sees themselves in
  `users` (and via `user(username: <self>)`), regardless of admin status.
- **Other users**: only those the caller `canAdminUser` are visible.
- **Allocatables / attribute references**: filtered by `canRead`;
  unreadable entries are silently dropped. Existence not leaked.
- **Mixed-id requests**: a query like `allocatable(id: X)` returns the
  same `null` for "doesn't exist" and "exists but you can't see it".

---

## Performance patterns

The Cut C work hit a 15 s ceiling on a 42 k × 11-typed-field admin query, then dropped to 6.35 s (58 % faster) by moving the hot-path resolvers off `@SchemaMapping` onto `LightDataFetcher` singletons and caching request-scoped state. Every pattern below is documented here so the next batch of resolvers (reservations, conflicts, search) can follow the same playbook.

**Apply these rules to any per-row resolver.** Top-level `@QueryMapping` (runs once per request) is fine on the annotation path; only the per-row stuff matters.

### Why `@SchemaMapping` is slow per-row

Profiling at 42k rows × 11 fields = 462k dispatches surfaced two structural costs:

| Layer | What happens per dispatch | Why it adds up |
|---|---|---|
| Spring `SchemaMappingDataFetcher.get(env)` | Allocates a new `DataFetcherHandlerMethod`, walks the arg-resolver chain, reflects via `Method.toGenericString` | ~5–8 µs/call × 462 k = ~3 s |
| Spring `ContextDataFetcherDecorator.get(env)` | Calls `DefaultContextSnapshotFactory.captureFromContext` (Micrometer thread-local capture) before delegating | ~3–5 µs/call × 462 k = ~2 s |
| graphql-java `ExecutionStrategy.invokeDataFetcher` | Constructs the full `DataFetchingEnvironment` even if your fetcher only needs `env.getSource()` | ~3–5 µs/call |

Total: **~8 s of pure framework overhead** for resolvers that just need `source` and return a getter result. The fix is to bypass both layers for hot-path fields by registering `LightDataFetcher` singletons via `RuntimeWiringConfigurer`.

### What `LightDataFetcher` does

`LightDataFetcher` extends `TrivialDataFetcher` (graphql-java's marker interface) and adds a fast `get(fieldDef, source, envSupplier)` signature. Two distinct fast paths fire:

1. **Spring's `ContextDataFetcherDecorator$ContextTypeVisitor`** does `instanceof TrivialDataFetcher` and SKIPS wrapping the fetcher in the Micrometer-context-capturing decorator. Verified by bytecode: `ifeq` branch returns false → no wrap.

2. **graphql-java's `ExecutionStrategy.invokeDataFetcher`** does `instanceof LightDataFetcher` and calls `lightDataFetcher.get(fieldDef, source, envSupplier)` directly. The `envSupplier` is lazy (an `IntraThreadMemoizedSupplier`) — `env.getSource()`, args, context aren't materialized unless the body calls `envSupplier.get()`.

**Net: per-field cost drops from ~25-30 µs to ~5-10 µs.**

### Pattern 1 — base class for source-typed fetchers

`StructuralTypeFetchers.LightSourceFetcher<S, T>` is the boilerplate trimmer. New fetchers extend it:

```java
private abstract static class LightSourceFetcher<S, T> implements LightDataFetcher<T> {
    private final Class<S> sourceType;

    protected LightSourceFetcher(Class<S> sourceType) { this.sourceType = sourceType; }

    @Override
    public final T get(GraphQLFieldDefinition fieldDef, Object source,
            Supplier<DataFetchingEnvironment> envSupplier) throws Exception {
        return sourceType.isInstance(source)
                ? read(sourceType.cast(source), envSupplier)
                : null;
    }

    @Override
    public final T get(DataFetchingEnvironment env) throws Exception {
        // Cold fallback for callers not on the Light fast path.
        return get(env.getFieldDefinition(), env.getSource(), () -> env);
    }

    protected abstract T read(S source, Supplier<DataFetchingEnvironment> envSupplier) throws Exception;
}
```

Per-field fetchers become tight singletons (one allocation at schema build, reused for every row):

```java
static final LightDataFetcher<String> ALLOCATABLE_DISPLAY_NAME =
        new LightSourceFetcher<Allocatable, String>(Allocatable.class)
        {
            @Override protected String read(Allocatable a, Supplier<DataFetchingEnvironment> env)
            {
                return a.getName(localeFrom(env));
            }
        };
```

When the fetcher needs an operator-injected dependency, return a factory instead of a static field:

```java
static LightDataFetcher<User> allocatableOwner(StorageOperator operator)
{
    return new LightSourceFetcher<Allocatable, User>(Allocatable.class) {
        @Override protected User read(Allocatable a, Supplier<DataFetchingEnvironment> env)
                throws RaplaException {
            ReferenceInfo<User> ref = a.getOwnerRef();
            return ref == null ? null : operator.tryResolve(ref);
        }
    };
}
```

### Pattern 2 — per-attribute LightDataFetcher class for generated types

When fetchers are parameterized (e.g. one per `(DynamicType, Attribute)` for typed classification fields), use a concrete class with `final` fields captured at construction. **Avoid lambdas** — they allocate per-call closure state and don't always get JIT-promoted out of allocation hot paths.

`AttributeDataFetcher` is the template:

```java
final class AttributeDataFetcher implements LightDataFetcher<Object> {
    private final Attribute     attribute;
    private final String        key;
    private final AttributeType type;
    private final boolean       multi;

    AttributeDataFetcher(Attribute attribute) {
        this.attribute = attribute;
        this.key       = attribute.getKey();
        this.type      = attribute.getType();
        this.multi     = isMultiSelect(attribute);
    }

    @Override
    public Object get(GraphQLFieldDefinition fieldDef, Object source,
                      Supplier<DataFetchingEnvironment> envSupplier) {
        if (!(source instanceof Classification c)) return null;
        return multi ? readMulti(c, envSupplier) : readSingle(c, envSupplier);
    }

    @Override
    public Object get(DataFetchingEnvironment env) throws Exception {
        return get(env.getFieldDefinition(), env.getSource(), () -> env);
    }
    // ...readSingle, readMulti...
}
```

One instance per (DynamicType, Attribute). 22 dhbw types × ~15 attrs avg = ~330 instances total, allocated once at schema build.

### Pattern 3 — `RequestContextInstrumentation` for query-scoped state

DataFetchers must NOT re-resolve `SecurityContextHolder` / `operator.getUser` / `LocaleContextHolder` per dispatch. Resolve once at `beginExecution`, store in `GraphQLContext`:

```java
@Component
public class RequestContextInstrumentation extends SimplePerformantInstrumentation
{
    private final StorageOperator operator;

    @Override
    public InstrumentationContext<ExecutionResult> beginExecution(
            InstrumentationExecutionParameters parameters,
            InstrumentationState state)
    {
        GraphQLContext ctx = parameters.getGraphQLContext();
        ctx.put(RequestCtx.KEY, new RequestCtx(
                resolveCallerFromSecurityContext(),
                operator.getPermissionController(),
                LocaleContextHolder.getLocale()));
        return SimpleInstrumentationContext.noOp();
    }

    public record RequestCtx(User caller, PermissionController permissionController, Locale locale) {
        static final String KEY = "rapla.requestCtx";
    }
}
```

Fetchers read it cheaply:

```java
private static boolean canReadAllocatable(Allocatable a, Supplier<DataFetchingEnvironment> envSupplier)
{
    DataFetchingEnvironment env = envSupplier.get();
    RequestCtx rc = RequestContextInstrumentation.from(env.getGraphQlContext());
    if (rc.caller() == null || rc.permissionController() == null) return false;
    return rc.permissionController().canRead(a, rc.caller());
}
```

**Caveat — env materialization cost:** calling `envSupplier.get()` materializes the full `DataFetchingEnvironment`. For fields that always need it (ALLOCATABLE attrs needing §12), unavoidable. For fields that don't (locale-aware names), cache the value at wire time instead.

### Pattern 4 — cache `RaplaLocale` at wire time, not per request

For locale-aware field reads (`Allocatable.displayName`, `Category.name`, `DynamicType.name`), reading from `RequestCtx` requires `envSupplier.get()` — ~25 µs of env materialization that dominates if you have many locale-aware fields per row. Instead snapshot the configured locale at schema build:

```java
public final class StructuralTypeFetchers
{
    private static volatile Locale serverLocale = Locale.getDefault();

    public static void wire(RuntimeWiring.Builder b, StorageOperator operator, RaplaLocale raplaLocale)
    {
        if (raplaLocale != null) {
            Locale l = raplaLocale.getLocale();
            if (l != null) serverLocale = l;
        }
        b.type("Allocatable", t -> t.dataFetcher("displayName", ALLOCATABLE_DISPLAY_NAME));
        // ...
    }

    private static Locale localeFrom(Supplier<DataFetchingEnvironment> envSupplier) {
        return serverLocale;  // single memory load; no env materialization
    }
}
```

Per-request `Accept-Language` is sacrificed for ~25 µs/call × N per row × M rows. Single-locale deployments lose nothing; multi-locale deployments need a real design (ThreadLocal mirror, or a per-request invalidation path).

### Pattern 5 — graphql-java doesn't propagate interface fetchers — re-register on every implementation

Spring's `@SchemaMapping(typeName = "Classification", field = "X")` walked the schema and registered against every implementation. `RuntimeWiringConfigurer.type("Classification").dataFetcher("X", ...)` does NOT — registering on the interface alone doesn't propagate.

**Every generated concrete type must explicitly register every inherited interface field's fetcher.** In `GeneratedClassificationWiring.configure`:

```java
wiringBuilder.type(typeName, builder -> {
    // Re-register inherited Classification interface fields on every concrete type
    builder.dataFetcher("typeKey", StructuralTypeFetchers.CLASSIFICATION_TYPE_KEY);
    builder.dataFetcher("type",    StructuralTypeFetchers.CLASSIFICATION_TYPE);
    // Plus per-attribute generated fields
    for (Attribute attr : dt.getAttributes()) { ... }
    return builder;
});
```

Forgetting this lets graphql-java fall back to `PropertyDataFetcher` (the JavaBean getter walker), which **returns null silently** for our entity-backed types. That's a production bug with no log scream.

### Pattern 6 — startup validation catches forgotten interface re-registration

The "silent null on forgotten interface re-registration" bug deserves a boot-time check. In `HotSwappableGraphQlSource.buildSource()` after the source is built:

```java
private static void validateInterfaceCoverage(GraphQLSchema schema)
{
    for (String interfaceName : new String[] {
            "Classification", "AllocatableClassification", "ReservationClassification" })
    {
        GraphQLType t = schema.getType(interfaceName);
        if (!(t instanceof GraphQLInterfaceType iface)) continue;
        List<GraphQLFieldDefinition> ifaceFields = iface.getFields();
        GraphQLCodeRegistry codeRegistry = schema.getCodeRegistry();
        for (GraphQLObjectType impl : schema.getImplementations(iface))
        {
            for (GraphQLFieldDefinition field : ifaceFields)
            {
                DataFetcher<?> fetcher = codeRegistry.getDataFetcher(impl, field);
                if (isPropertyFallback(fetcher))
                {
                    throw new IllegalStateException(
                            "GraphQL wiring incomplete: '" + interfaceName + "." + field.getName()
                            + "' has no explicit DataFetcher on '" + impl.getName()
                            + "' — falling back to PropertyDataFetcher returns null silently.");
                }
            }
        }
    }
}

private static boolean isPropertyFallback(DataFetcher<?> fetcher)
{
    return fetcher == null
            || fetcher instanceof PropertyDataFetcher<?>
            || fetcher instanceof SingletonPropertyDataFetcher<?>;
}
```

Boot fails loudly with the exact missing field. Add new interfaces (e.g. group interfaces for reservation types) to the loop when they land.

### Pattern 7 — JVM tuning: `optimizedLaunch=false` for `spring-boot:run`

Spring Boot's `spring-boot-maven-plugin` adds `-XX:TieredStopAtLevel=1` to JVM args by default — limits JIT compilation to tier 1 (interpreter + C1) for faster startup. For perf-testing or steady-state work, override:

```bash
mvn ... spring-boot:run \
    -Dspring-boot.run.optimizedLaunch=false \
    ...
```

That single flag saved ~1 s on the 42k Person query in measurement (~14 % wall-clock). For production deploys (`java -jar`, not `spring-boot:run`), the flag doesn't apply — full JIT is the default.

### What was tried and rejected

**`ClassificationImpl.getType()` caching in rapla-core.** Tempting (saves ~25-70 ms on the slow query by avoiding `resolver.tryResolve` per `getValue` call), but the operator's update path does NOT invalidate cached `DynamicTypeImpl` references on referencing Classifications. A cached pointer goes stale after an admin DynamicType edit, producing wrong attribute reads until the Classification is reloaded. Not safe without an operator-side invalidation walker (rapla-core change requiring careful verification). Skipped.

**Schema-level skip-null on Jackson side.** GraphQL spec mandates `null` for nullable fields that resolve to null. Adding `@JsonInclude(NON_NULL)` on the DTOs doesn't help because graphql-java's response is a `Map<String, Object>` (not the records); Jackson serializes the Map. Schema-skip-nulls would violate the spec.

**`-Xms2g -Xmx2g -Xtune:throughput` OpenJ9 tuning.** Caused OOM during boot — rapla's data cache + connection pool needs more headroom. Default heap (~4 GB) works; OpenJ9 perf tuning is a config knob, not a code change.

### Final perf numbers (post-Tier-1, dhbw admin × 42 250 Persons × 11 typed fields)

| Stage | Time | Notes |
|---|---:|---|
| Pre-perf-work baseline | 15.0 s | initial Cut C |
| + Tier 1 (Patterns 1–6 above) | 7.0 s | code changes only |
| + Pattern 7 (`optimizedLaunch=false`) | **6.35 s** | JVM flag only |
| Realistic Tier 1 floor with all known tweaks | ~5.5-5.7 s | adds rapla-core change + JVM heap tuning (deferred) |
| Tier 2 (`Classification.values: JSON` or `allocatablesPage`) | <1 s | schema change, deferred until real consumer |

5 measured runs at 6.35 s mean had stddev 0.05 s — predictable.

---

## Limitations

- **No mutations yet.** PRD 035 §6 bulk-mutation design is locked but not implemented. Reads only.
- **No subscriptions.** Polling only (10 s) for schema changes; queries themselves are request/response.
- **Rapla-internal types fully hidden.** Templates, periods, default-user, and anonymous-event don't appear in any GraphQL surface. Use the dedicated query roots (`periods`, …) for those.

## Wochenansicht — Grundlage für Wochenabfragen (`appointmentBlocks` + `@view`)

Kanonische Vorlage für eine Wochen-/Tabellenansicht: `appointmentBlocks` liefert die flachen Blöcke,
`@view`/`@column`/`@hidden`/`@join` erzeugen die Render-Metadaten (`extensions.view`), die gesamte
Eingabe steckt im `$filter`-Objekt (`ReservationFilter`). **Instanz-neutral** — keine
deployment-spezifischen Typ-Keys im Query-Text; die kommen nur als Variablen-Daten rein.

```graphql
query Wochenansicht(
  $filter: ReservationFilter!,                     # Event-Fenster (+ optional allocatableMatching)
  $sort:   [BlockSort!] = [{ field: START, dir: ASC }],
  $offset: Int = 0
) @view(title: "Wochenansicht") {
  appointmentBlocks(filter: $filter, sort: $sort, offset: $offset) {
    date   @column(header: "Datum", order: 1, group: true)  # Date (yyyy-MM-dd), Gruppen-Achse (PRD 073/074)
    times  @column(header: "Zeit",  order: 2)      # "10:00 - 11:30" (serverformatiert)
    name   @column(header: "Titel", order: 3)      # block-aware (ehrt appointment-note overrides)

    start @hidden  end @hidden                      # LocalDateTime! → exakte Grid-Positionierung/-Höhe
    durationMinutes @hidden                        # Int Wall-Clock-Minuten (≠ UE-Dauer)
    isException     @hidden                         # Boolean! → Ausnahme-Styling
    reservation { id @hidden  canModify @hidden }   # stabiler Editier-Handle (Block hat KEINE eigene id)

    # Generische Ressourcen-Lanes — Trennung rein über isPersonEq, kein typeKey im Query:
    personen: allocatables(filter: { isPersonEq: true })
      @join(separator: ", ") @column(header: "Personen", order: 5) {
      id  name  isLocation
    }
    nichtPersonen: allocatables(filter: { isPersonEq: false })
      @join(separator: ", ") @column(header: "Nicht-Personen", order: 6) {
      id  name  isLocation
    }
  }
}
```

Variablen (mit `AllocatableFilter` in Aktion — schränkt die Termine auf passende Ressourcen ein):

```json
{
  "filter": {
    "from": "2026-06-15T00:00:00",
    "to":   "2026-06-22T00:00:00",
    "allocatableMatching": {
      "typeKeyIn": ["Raum"],
      "whereRaum": { "Gebaeude": { "where": { "Gebaeudename": { "startsWith": "MOS" } } } }
    },
    "limit": 2000
  },
  "offset": 0
}
```

Erläuterung:

- **Identität:** `AppointmentBlock` hat **keine eigene `id`** (synthetisch pro Expansion). Stabiler
  Render-/Edit-Key = `reservation.id` + `start`; `canModify` gated den Edit-Button ohne 2. Request.
- **Ressourcen-Lanes:** rein über `isPersonEq: true|false` getrennt (server-seitig, kein deployment-Key).
  Innerhalb „Nicht-Personen" weiter über `isLocation` lanen (Raum/Ort vs. Sonstiges). `isPerson`/
  `isLocation` sind universelle Felder (PRD 080): `isPerson` == `type: PERSON`, `isLocation` == die
  DynamicType-Annotation `location=true` (derselbe Marker wie der iCal-Export).
- **`@join`** macht aus der Ressourcen-Liste eine Zelle (`", "`-getrennt); die Daten bleiben verschachtelt.
- **AllocatableFilter an drei Stellen, alle derselbe Typ:** `$filter.allocatableMatching` (welche
  *Termine* erscheinen) und die zwei Lane-Filter (welche *Ressourcen pro Row*). Die GUI kann auf die
  Lane-Filter `where<Type>`, `accessLevel: EDIT`, `idIn` … draufpacken, ohne den Query-Text zu ändern.
- **Duration:** der UE-String (`duration`, eventtimecalculator) ist bewusst draußen. `durationMinutes`
  (`@hidden`) ist reine Wall-Clock-Differenz für die Block-Höhe — weglassbar, da aus `start`/`end`
  ableitbar. Wochentag pro Row: `tag: compute(expr: "format(\"%tA\", date(item))")`.
- **Output:** `extensions.view.columns` enthält die nicht-`@hidden`-Felder (mit `header`, `order`, `type`,
  `join`), `extensions.view.page` `{ offset, limit, returned, hasMore }`. `@hidden`-Felder liegen in
  `data`, nicht in `columns`.

### Gruppieren nach Tag (`@column(group: true, format: …)` → `view.groupBy` / `view.groupFormat`)

Gruppieren ist **Render-Sache** (flache Zeilen + Hint); es gibt **kein** `@group`-Direktiv. Stattdessen
markierst du **eine Spalte** als Gruppen-Achse mit `@column(group: true)` und gibst optional ein
**Format-Token** für den Gruppen-Header mit (`format:`). Der Server emittiert:

```jsonc
"extensions": { "view": {
  "groupBy":     "date",          // alias der group-Spalte (Top-Level-Hint)
  "groupFormat": "EE dd.MM",      // optionales Format-Token der group-Spalte (Header)
  "columns": [
    { "alias": "date", "header": "Datum", "type": "Date", "order": 1, "group": true, "format": "EE dd.MM" },
    { "alias": "times", … }, { "alias": "name", … }, …
  ]
}}
```

Query: `date @column(header: "Datum", order: 1, group: true, format: "EE dd.MM")`.

Der Renderer gruppiert die (nach `START` sortierten) Flat-Rows nach `row[view.groupBy]` und formatiert
den Header mit `view.groupFormat`:

```ts
const groupField = view.groupBy ?? view.columns.find(c => c.type === 'Date')?.alias;  // "date"; Fallback: Date-Spalte
const d = new Date(sec.key);                                   // sec.key = "2026-06-15"
const header = formatDate(d, view.groupFormat ?? 'EEEE, dd.MM.yyyy');   // "EE dd.MM" → "Mo 15.06"
// Schnitt-bei-Wechsel über die sortierte Liste → eine Sektion pro Tag
```

- **Nach echtem Datum gruppieren, nicht nach Wochentag-Name** — sonst landen Montag 15.06. und Montag
  22.06. in *einer* Sektion. Gruppen-Achse ist `date` (das echte Datum), nicht ein `format("%tA",…)`-Feld.
- **`format` ist ein opakes Render-Token** (z. B. date-fns/ICU `EE dd.MM` → „Mo 15.06", `EEEE, dd.MM.yyyy`
  → „Montag, 15.06.2026"). Der Server reicht es **unverändert** durch — er interpretiert/validiert es
  nicht; der Client kennt seine Locale + Formatter-Lib.
- **Drei Wege, den Tag/Label zu erzeugen** (von deklarativ → konkret): (C) `format` im View-Meta
  (Renderer formatiert den Key — empfohlen), (B) Renderer leitet selbst aus dem `date`-Key ab,
  (A) `tag: compute(expr:"concat(format(\"%tA\",date(item)),\" \",substring(date(item),0,10))") @hidden`
  liefert den fertigen String in `data` (für Nicht-JS-Konsumenten wie CSV/Export). Die View-Meta selbst
  **rechnet keine Werte** — sie trägt nur Struktur + das Format-Token.
- **Die `date`-Spalte darf zusätzlich `@hidden`** sein (`group:true` + `format:` + `@hidden` zusammen ok):
  sie steht dann nur im Header statt redundant in jeder Zeile; `view.groupBy`/`groupFormat` bleiben gesetzt.

## The rapla expression (`expr`) — one language, several slots (PRD 074 V2)

`expr` is the bounded rapla expression language (the `ParsedText` / nameformat engine), exposed in
GraphQL wherever a **derived value** is produced — **one language, learned once**:

| Slot | Shape | Produces |
|---|---|---|
| Column projection | `compute(expr: "…")` on `AppointmentBlock` | a per-row string cell |
| Group key | `groupBy: [{ key, expr: "…" }]` (PRD 079) | a bucket key |
| Metric value (Stufe b) | `aggregate: [{ key, expr: "…", fn }]` | a numeric value (coerced) |

**Syntax (externally documented form):**
- **Bare body** — no wrapper needed; the server wraps it as `{item -> … }`. `compute(expr: "concat(name(), times())")`.
- **Subject `item`** — the current object (an `AppointmentBlock` in these slots). Mostly **implicit**
  via 0-arg subject functions: `name()`, `times()`, `start()`, `end()`, `duration()`. Write `item`
  explicitly only when you must pass the subject: `fn(item)`.
- **Arrow** — `=>` (documented) or `->` (also accepted). The bare single-subject form needs no arrow;
  the explicit/n-parameter lambda uses the braced form `{(a, b) => fn(a, b)}`.
- **Functions** — `name`, `times`, `start`, `end`, `duration`, `concat`, `substring`, `if`, `equals`,
  `attribute`, `key`, `type`, `resources`, … (the bridged rapla function set).

Examples:
```graphql
compute(expr: "concat(substring(times(),0,5), \"–\", substring(times(),8,13))")   # "08:00–11:15"
compute(expr: "if(equals(key(type()), \"Pruefung\"), \"📝\", \"Lehre\")")
groupBy:   [{ key: "initial", expr: "substring(name(),0,1)" }]
aggregate: [{ key: "sum",     expr: "attribute(item, \"<numericAttr>\")", fn: SUM }]
```

**Welche Funktionen gibt es? → `computeFunctions` (PRD 073).** Der Katalog der verfügbaren
expr-Funktionen ist abfragbar — für Editor-Autocomplete und View-Validierung:

```graphql
query { computeFunctions { name namespace minArgs maxArgs returnType sourceLevel doc } }
```

Aggregiert aus allen registrierten `FunctionFactory`s (Core `org.rapla` + aktive Plugins, z. B.
`duration` aus eventtimecalculator, `note` aus appointmentnote). `sourceLevel` =
`EVENT | CLASSIFIABLE | ALLOCATABLE | ANY | VIEW_TITLE` (auf welchem Subjekt die Funktion sinnvoll
ist), `maxArgs: -1` = variadisch (`concat`). Der Katalog wird aus der Descriptor-SPI generiert
(`FunctionFactory.getDescriptors()`), nicht aus geparstem Quellcode.

**Not yet (PRD 073 number-model / Stufe c):** in-expression arithmetic (`add/sub/mul/div`). Single
numeric values work (Stufe b); composing numbers inside the expr needs a numeric type in the EL,
which would then serve every `expr` slot.

## Raumauslastung — kanonische Query (`appointmentBlockStats`, PRD 079/080)

Auslastung pro Raum: **Gebäude-Scope in ZWEI Variablen** — `$filter` (effiziente Suche, lädt nur
betroffene Reservierungen) **und** `$allocatableFilter` (Raumauswahl: welcher Raum eine Zeile wird),
**beide mit demselben Scope gefüllt**. Plus **Raumgröße + Gebäudename über die typisierte Entität**
(kein Client-Join), als `@view` (stats-bewusste Spalten). Live verifiziert gegen dhbw.

> **Beide Variablen MÜSSEN gesetzt sein — sie machen Unterschiedliches:**
> - **`$filter` (`ReservationFilter`) = effiziente Suche.** `allocatableMatching` ist ein
>   *Reservierungs*-Prädikat: eine Reservierung kommt rein, sobald sie **≥1** passenden Raum belegt —
>   **mitsamt allen ihren übrigen Räumen** (auch fremder Gebäude). Senkt nur die geladene Datenmenge.
> - **`$allocatableFilter` (`AllocatableFilter`) = Raumauswahl im `groupBy`.** Entscheidet pro
>   Termin-Block, **welcher Raum zur Zeile wird**. Ohne Gebäude-Scope hier würden die Fremdgebäude-Räume
>   derselben Reservierung über den **groupBy-Fan-out** zu Falschzeilen (live gesehen: „Schloss 11a",
>   „Johann-Hammer-Straße"). **Das ist der korrektheitsentscheidende Filter.**
>
> Die **Dopplung** (gleicher Scope in beiden) ist gewollt und ok — die GUI füllt beide aus einer Auswahl.
>
> **`where<Type>` impliziert den Typ-Gate (Option B′):** `whereRaum` gatet automatisch auf Raum-
> Allocatables — `typeKeyIn:["Raum"]` ist nicht mehr nötig (bleibt optional als Storage-Vorfilter; mehrere
> `where<…>` ⇒ Union ihrer Typen). Ausnahme: explizites `typeKeyIn`/`typeKeyEq` ist autoritativ.

```graphql
query Raumauslastung($filter: ReservationFilter!, $allocatableFilter: AllocatableFilter!) @view(title: "Raumauslastung") {
  appointmentBlockStats(
    filter:    $filter,                                              # effiziente Suche (Reservierungen)
    groupBy:   [ { key: "raum", allocatables: $allocatableFilter } ], # Raumauswahl (welche Zeile)
    aggregate: [ { key: "minuten", field: DURATION_MINUTES, fn: SUM },     # Stunden = number/60
                 { key: "termine", field: DURATION_MINUTES, fn: COUNT } ],
    limit: 1000
  ) {
    keys {
      value                                          # → Spalte "raum" (Raumname)
      entity {
        ... on Allocatable {
          id
          classification {
            ... on RaumClassification {
              AnzahlPlaetzeInsgesamt                 # → Spalte "AnzahlPlaetzeInsgesamt" (Raumgröße)
              Gebaeude { classification { ... on GebaeudeClassification { Gebaeudename } } }  # → "Gebaeudename"
            }
          }
        }
      }
    }
    values { key number }                            # → Spalten "minuten" / "termine"
  }
}
```

**Variablen — Gebäude nach Name** (derselbe Gebäude-Scope in beiden; `whereRaum` gatet selbst auf Raum):
```json
{
  "filter": {
    "from": "2024-10-01T00:00:00",
    "to":   "2025-09-30T00:00:00",
    "allocatableMatching": {
      "whereRaum": { "Gebaeude": { "where": { "Gebaeudename": { "contains": "Schloss 2" } } } }
    }
  },
  "allocatableFilter": {
    "whereRaum": { "Gebaeude": { "where": { "Gebaeudename": { "contains": "Schloss 2" } } } }
  }
}
```
Schichtung von `whereRaum`: `RaumWhere` → `Gebaeude` (= `GebaeudeRefWhere`: `eq`/`ne`/`in`/`isNull`/`nameContains` **+** `where`) → `where` (= `GebaeudeWhere`, eigene Attribute) → `Gebaeudename` (= `StringWhere`: `eq`/`in`/`contains`/`startsWith`/`endsWith`/`isNull`). `where:`-Wrapper nur für **Attribute** des Gebäudes; eine konkrete Gebäude-Id direkt per `Gebaeude: { eq: "<id>" }`.

**Variante — bekanntes Gebäude per Id** (beide Variablen mit **identischer** `AllocatableFilter`-Shape `{idIn:[gebäudeId]}`):
```json
{
  "filter": { "from": "2024-10-01T00:00:00", "to": "2025-09-30T00:00:00", "allocatableMatching": { "idIn": ["r7ed4347-9058-45a6-b402-6e3fb64c031f"] } },
  "allocatableFilter": { "idIn": ["r7ed4347-9058-45a6-b402-6e3fb64c031f"] }
}
```
> **`idIn` mit einer Gebäude-Id wirkt belongsTo-bewusst — aber NUR im Stats-/Fan-out-Pfad.**
> - **`$filter.allocatableMatching.idIn` / `$allocatableFilter.idIn`** matchen einen Raum auch über
>   seine **belongsTo-Vorfahren** → die Gebäude-Id wählt die Räume des Gebäudes (Filter-Pfad via
>   `getDependentRef` nach unten, Fan-out-Pfad via belongsTo-Up-Walk in `filterAllocatables`).
> - **`Query.allocatables(filter:{idIn:[…]})`** (globale Katalog-Abfrage) bleibt **exakte Id** — gibt
>   das Gebäude selbst zurück, NICHT seine Räume. Eine normale Allocatable-Abfrage tauscht nie ein
>   Gebäude gegen seine Räume.
>
> Mehrere Gebäude-Ids: `idIn:["id1","id2"]` (in beiden), oder per Name `whereRaum.Gebaeude.where.Gebaeudename.contains`.

- **`$filter` = effiziente Suche, `$allocatableFilter` = Raumauswahl.** Beide mit demselben Scope; die
  Dopplung ist gewollt — die GUI füllt aus *einer* Gebäude-Auswahl **beide** Variablen identisch.
- **Ohne `$allocatableFilter`-Scope** (nur `typeKeyIn:["Raum"]` o.ä.) ⇒ Fremdgebäude-Räume über
  groupBy-Fan-out → Falschzeilen. **Beide setzen.**
- **Gelöschtes Gebäude:** ein Raum, dessen `Gebaeude`-Referenz auf eine **gelöschte** Ressource zeigt,
  matcht `whereRaum.Gebaeude…` **nicht** (kein Fail-open auf den Platzhalter) und liefert
  `entity.Gebaeude: null` — statt die Query zu killen.
- **Raumgröße + Gebäudename ohne Join** über `keys.entity` (typisierte Gruppen-Entität, PRD 080);
  unauflösbare Referenz → Feld `null` (TypeResolver/Fetcher-Guard).
- **`@view` über Stats** ⇒ flache `extensions.view.columns` aus `groupBy`/`aggregate` (`raum` +
  Entity-Felder + `minuten`/`termine`), **nicht** die generischen `keys/values/count`. `count` nur,
  wenn selektiert (redundant mit `termine`).

## Beispiel: Raumauslastung nach Standort (typisierte Referenz-Filter, PRD 074 b)

`appointmentBlockStats` + ein **typisierter Filter über eine Referenz**: Räume werden über das
**eigene Attribut ihres Gebäudes** eingeschränkt (`Raum.Gebaeude` → `Gebaeude.Gebaeudename`). Der
Referenz-Filter `<RefType>RefWhere` trägt id/name-Prädikate **und** ein verschachteltes
`where: <RefType>Where`; der `WhereEvaluator` löst die Referenz §12-`canRead`-gegated auf und wertet
das typisierte where rekursiv aus (tiefen-gedeckelt). Live verifiziert gegen dhbw (DHBW Mosbach).

```graphql
query RaumauslastungMosbach {
  appointmentBlockStats(
    filter: { from: "2026-03-21T00:00:00", to: "2026-06-21T00:00:00" },
    groupBy:   [ { key: "raum", allocatables: {
                   typeKeyIn: ["Raum"],
                   whereRaum: { Gebaeude: { where: { Gebaeudename: { startsWith: "MOS" } } } }
                 } } ],
    aggregate: [ { key: "stunden", field: DURATION_MINUTES, fn: SUM },
                 { key: "termine", field: DURATION_MINUTES, fn: COUNT } ]
  ) {
    keys   { value }      # Raumname
    values { key number } # stunden = number/60, termine
    count
  }
}
```

Schichtung des Filters:

```
whereRaum:          RaumWhere          # Attribute des Raums
  Gebaeude:         GebaeudeRefWhere   # Referenz: eq/ne/in/isNull/nameContains + where
    where:          GebaeudeWhere      # EIGENE Attribute des Gebäudes
      Gebaeudename: StringWhere        # eq/ne/in/contains/startsWith/endsWith/isNull
```

- **§12:** ein nicht-lesbares Gebäude ⇒ der Raum (bzw. seine Blöcke) fällt heraus — kein Attribut-Leak.
- **Standort-Feld:** `Gebaeudename` (alternativ `Kuerzel`/`Adresse`) — generierte `GebaeudeWhere`-Felder.
- **Raumgröße ohne Join:** `keys.entity` trägt das **echte, typisierte Gruppen-Objekt** — siehe nächster
  Abschnitt; `AnzahlPlaetzeInsgesamt` ist direkt im Bucket selektierbar, **kein** zweiter Request nötig.

## Typisierte Gruppen-Entität im Stats-Bucket (`StatKey.entity`, PRD 080)

Ein Stats-Bucket bleibt generisch (`keys` + `values` + `count`), **aber** jeder Gruppenschlüssel trägt
zusätzlich die **echte, typisierte Entität**, nach der gruppiert wurde — als Union `StatEntity`:

```graphql
union StatEntity = Allocatable | Reservation | Category

type StatKey {
  key:    String!     # der groupBy-"key"-Name
  value:  String!     # Anzeigestring (immer gesetzt)
  entity: StatEntity  # die typisierte Gruppen-Entität — null bei Zeit-/Skalar-Dimensionen
}
```

Damit ist **jedes Feld der Gruppen-Entität im selben Request selektierbar** (kein Client-Join). Die
`allocatables`-Dimension liefert ein `Allocatable`, die `reservation: true`-Dimension eine
`Reservation`; Zeit-Buckets (`date`/`by`) und reine `expr`-Strings haben `entity: null`.

Mosbach-Auslastung **mit Raumgröße, eine Query**:

```graphql
query RaumauslastungMitGroesse {
  appointmentBlockStats(
    filter: { from: "2026-03-21T00:00:00", to: "2026-06-21T00:00:00" },
    groupBy:   [ { key: "raum", allocatables: {
                   typeKeyIn: ["Raum"],
                   whereRaum: { Gebaeude: { where: { Gebaeudename: { startsWith: "MOS" } } } }
                 } } ],
    aggregate: [ { key: "stunden", field: DURATION_MINUTES, fn: SUM } ]
  ) {
    keys {
      value                       # Raumname (Anzeigestring)
      entity {
        __typename
        ... on Allocatable {
          classification { ... on RaumClassification { AnzahlPlaetzeInsgesamt } }
        }
      }
    }
    values { key number }
    count
  }
}
```

Gruppieren nach Veranstaltung (`Reservation`-Entität):

```graphql
appointmentBlockStats(
  groupBy:   [ { key: "kurs", reservation: true } ],
  aggregate: [ { key: "stunden", field: DURATION_MINUTES, fn: SUM } ]
) {
  keys   { value entity { __typename ... on Reservation { id } } }
  values { key number }
  count
}
```

- **§12:** `entity` kommt aus demselben `canRead`-gegateten Resolver-Pfad wie alle anderen Entitäts-
  Felder (`filterAllocatables` / rekursive Referenzauflösung) — eine nicht-lesbare Entität wird gar
  nicht erst zum Gruppenschlüssel.
- **Generik bleibt:** ad-hoc `groupBy`/`aggregate` und der eine geteilte Bucket-Typ über alle Familien
  bleiben; nur der Schlüssel ist jetzt zusätzlich typisiert navigierbar.
- **Status:** `appointmentBlockStats` (Allocatable- + Reservation-Dimension) ist umgesetzt; eigene
  `allocatableStats`/`reservationStats`-Felder, die Category-Dimension und `expr → Entity` sind in
  [PRD 080](prd/080-typed-entity-stats.md) als ⏳ offen geführt.
