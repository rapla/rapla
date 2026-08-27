# PRD 073 — Rapla Server Functions ↔ GraphQL Equivalence Map

**Status:** in-progress — Phase 0 (filterable nested `Appointment.allocatables`) done 2026-06-19; **descriptor SPI + `computeFunctions` catalog done 2026-06-21** (see Implementation status); remaining field-phases are design.

## Implementation status (2026-06-21)

- ✅ **Descriptor SPI (option a).** `FunctionFactory.getDescriptors()` (default empty) +
  `FunctionDescriptor { name, namespace, minArgs, maxArgs, returnType, sourceLevel, doc }` in
  `org.rapla.entities.extensionpoints` (rapla-core). Declared in rapla terms, **GraphQL-agnostic**.
- ✅ **All three factories declare descriptors** — `StandardFunctions` (31 core fns), `DurationFunctions`
  (`duration`/`durationCompare`), `AppointmentNoteFunctions` (`note`). Arities/return/sourceLevel
  verified against the real `assertArgs`/`eval` bodies.
- ✅ **`Query.computeFunctions: [ComputeFunction!]!`** (`ComputeFunctionsController`) aggregates every
  registered `FunctionFactory`'s descriptors (core + active plugins), dedupes by `namespace:name`,
  sorts. SDL type `ComputeFunction`. Tier-3 test asserts core fns + metadata (`concat` variadic,
  `attribute` CLASSIFIABLE/2-arg, `start` EVENT). **62 GraphQL tests green.**
- ✅ **`isLocation` / `isPerson`** surfaced as `Allocatable` fields ([PRD 080](done/080-typed-entity-stats.md) work) — closes row #22's
  field gap (filter `isLocationEq` still open; `isPersonEq` already shipped).
- ✅ **Descriptor-driven generated fields (first cut).** `FunctionFieldGenerator` derives eligible
  fields from the same descriptors and emits them BOTH as `extend type … { }` SDL (appended in
  `HotSwappableGraphQlSource`) AND as EL-backed DataFetchers (`GeneratedClassificationWiring`), so
  schema + wiring can't drift. Scope: EVENT-source, String-return, name-not-already-present, onto
  `AppointmentBlock` → today yields **`AppointmentBlock.note`** (from the appointmentnote plugin
  descriptor; `times`/`duration` already exist statically). The fetcher evaluates the
  (namespace-qualified) function via `StructuralTypeFetchers.computeEntityExpr` against the block;
  §12 rides on the already-gated row set + the EL's `canReadInformation`. Test:
  `appointmentBlocks { note }` resolves (blank when no note). **63 GraphQL tests green.**
  Widening = extend `TARGET_TYPES` / `EXISTING_FIELDS` / `returnTypeToGraphql`.
- ✅ **Int return mapping.** `returnTypeToGraphql` now maps `Int`→`Int`; the fetcher takes the RAW
  eval result (`computeEntityExprObject`) and coerces via `FunctionFieldGenerator.coerceInt`
  (Number or numeric String → Integer, blank/non-numeric → null) — avoids locale-formatting a
  number through `formatName`. Yields **`AppointmentBlock.number: Int`** (core block sequence #,
  1-based). Test asserts it's a real `Integer` ≥ 1. **64 GraphQL tests green.**
- ✅ **Boolean / DateTime / Date scalar mapping.** `returnTypeToGraphql`: `Boolean`→`Boolean`,
  `DateTime`→`LocalDateTime` (wall-time scalar, matching start/end), `Date`→`Date` (extended scalar).
  `coerceScalar` turns the RAW eval result into the Java type each scalar expects (LocalDateTime
  pass-through; Date ← `LocalDateTime.toLocalDate()`; Boolean/Int from value-or-string) — no
  formatName stringify, so the scalar's `serialize` never sees a wrong type. New generated fields:
  **`AppointmentBlock.date: Date`** + **`AppointmentBlock.lastchanged: LocalDateTime`** (both
  block-aware). Test asserts the serialized shapes (yyyy-MM-dd / ISO). **65 GraphQL tests green.**
  (No EVENT Boolean function today → Boolean mapping is future-proofing.)
- ✅ **Four descriptor-generated fields live on AppointmentBlock:** `note: String`, `number: Int`,
  `date: Date`, `lastchanged: LocalDateTime` — all from one pipeline, scalar-typed.
- ✅ **Target type = Appointment added.** `TARGET_TYPES = [AppointmentBlock, Appointment]`. Generates
  `note`/`date`/`lastchanged` (+ `times`/`duration`) on `Appointment`; `number` is `BLOCK_ONLY`
  (undefined without a concrete occurrence) so it's excluded from non-block targets — test asserts
  `Appointment.number` does NOT exist. **66 GraphQL tests green.**
- **Target-type rationale (decided 2026-06-21):** sensible targets are the **EVENT** GraphQL types
  `AppointmentBlock` (primary — the flat table/week row) and `Appointment` (secondary). NOT
  `Reservation` (most EVENT fns are appointment/block-scoped → ambiguous on a multi-appointment
  reservation, which already has `firstDate`/`lastDate`/`lastModifiedAt`); NOT CLASSIFIABLE/ALLOCATABLE
  (projections already exist as generated attribute + structural fields, incl. `isPerson`/`isLocation`);
  NOT ANY/VIEW_TITLE (argument-based, no subject-only field semantics).
- ⏳ **Not built:** object/list returns (`[Allocatable]`, `DynamicType` — need GraphQL object types
  + §12 + TypeResolver, overlaps existing typed fields); the `saveView` type-check that consumes the
  catalog (**likely owned by another session**).

## Goal

Rapla has a small server-side **expression language** — the
`org.rapla.entities.extensionpoints.Function` tree — used inside DynamicType
nameformats, classification filters, and annotations (e.g. a room's
`nameformat` `{if(Gebaeude startsWith "MOS", Raumnummer+" "+Raumname, …)}`).
The GraphQL API ([PRD 035](done/035-graphql-foundations.md) family) is the modern read/query surface. This PRD
answers: **for each rapla Function, what can a GraphQL caller do today, and
where are the gaps?** It is the reference for deciding which Function semantics
deserve a first-class GraphQL construct (computed field, filter operator) versus
staying client-side or server-template-only.

## Background — what "Functions" are

- Interface: `rapla-core/.../entities/extensionpoints/Function.java`; factory
  `FunctionFactory.createFunction(name, args)`; evaluation via
  `ParsedText` / `EvalContext` against a context object (an Allocatable,
  Reservation, Appointment, AppointmentBlock, Category, CalendarModel, …).
- Three providers (all registered server-side via the `FunctionFactory` map):
  | Provider | Namespace | Functions |
  |---|---|---|
  | `StandardFunctions` (core) | `org.rapla` | 31 (see table) |
  | `AppointmentNoteFunctions` (plugin `appointmentnote`) | — | `note` |
  | `DurationFunctions` (plugin `eventtimecalculator`) | — | `duration`, `durationCompare` |
- **Three distinct roles** the Functions play — the GraphQL equivalent differs
  by role:
  1. **Filter / predicate** (`equals`, `and`, `or`, `not`, `isPerson`,
     `isLocation`, `attribute`+compare) → GraphQL **where-predicate** language
     ([PRD 059](done/059-graphql-typed-where-predicates.md), done) + structural filters.
  2. **Projection / read access** (`attribute`, `type`, `key`, `parent`,
     `name`, `start`, `end`, `appointments`, `appointmentBlocks`, `events`,
     `resources`, `lastchanged`) → GraphQL **fields** on the generated
     `<TypeKey>Classification` types + `Reservation`/`Appointment` types.
  3. **String / presentation derivation** (`concat`, `format`, `substring`,
     `reverse`, `times`, `duration`, `name(…,lang)`) → mostly **client-side
     projection**; a GraphQL "gap" only if we want server-computed fields.

## Scope

**In scope:** a verified equivalence table for all 34 Functions; a grouped gap
analysis; a phased proposal for the gaps worth closing on the GraphQL read API.

**Out of scope:** implementing the closures (each lands as its own PRD/phase);
GraphQL mutations (write API — PRDs [056](056-graphql-events-write-api.md)/[057](done/057-graphql-dt-mutations-v1.md)/[061](061-graphql-dt-mutations-v2.md)/[063](063-graphql-allocatables-write-api.md)); the Swing/iCal nameformat
engine itself (unchanged — Functions remain the templating language there).

## Equivalence table

Status legend: **✅ full** · **◐ partial** · **❌ gap** · **N/A** (role doesn't
belong on a read/query API).

| # | Function (id) | Role | GraphQL equivalent | Status |
|---|---|---|---|---|
| 1 | `and` / `or` / `not` | filter | `<TypeKey>Where.AND:[…] / OR:[…] / NOT:{…}` ([PRD 059](done/059-graphql-typed-where-predicates.md), depth-cap 10, `WhereEvaluator`) | ✅ |
| 2 | `equals` | filter | `*Where.eq` / `.ne` on every typed predicate (`StringWhere`, `IntWhere`, `BooleanWhere`, `LocalDateTimeWhere`, `CategoryWhere`, `AllocatableWhere`) | ✅ |
| 3 | `stringComparator` | sort | no comparator; closest is `searchText` + `matchKind` ranking ([PRD 028](028-angular-power-search.md)) | ◐ |
| 4 | `attribute(obj,"key")` | projection | generated `<TypeKey>Classification` typed per-attribute fields (`ClassificationSdlGenerator`); §12-gated for reference attrs | ✅ |
| 5 | `type(obj)` | projection | `Classification.type: DynamicType!` + `Classification.typeKey: String!` | ✅ |
| 6 | `key(category\|type)` | projection | `Category.key: String!`, `DynamicType.key: String!` | ✅ |
| 7 | `parent(category)` | projection | `Category.parent: Category` (single hop); filter `CategoryWhere.descendantOf: ID` for subtree match | ◐ |
| 8 | `name(obj[,lang])` | projection | `Allocatable.displayName: String!`, structural `.name`; **default-locale only** — no per-language selector | ◐ |
| 9 | `start` / `end` | projection | `Appointment.start/end`, `Reservation.firstDate/lastDate`, `AppointmentBlock.start/end` | ✅ |
| 10 | `date` (cut to day) | projection | none; truncate `start`/`end` client-side | ◐ |
| 11 | `intervall` / `times` | projection | `start`+`end` pair only; no aggregated `TimeInterval` / formatted `"10:00 - 11:30"` field | ◐ |
| 12 | `appointments` | navigation | `Reservation.appointments: [Appointment!]!` | ✅ |
| 13 | `appointmentBlocks` | navigation | `Appointment.blocks(from:,to:): [AppointmentBlock!]!` (same recurrence expansion) | ✅ |
| 14 | `number` (block seq #) | projection | none on `AppointmentBlock` | ❌ |
| 15 | `lastchanged` | projection | `Reservation.lastModifiedAt`, `Allocatable.lastModifiedAt` (`DateTime`/offset) | ✅ |
| 16 | `events(obj)` | navigation | `Query.reservations(filter:{ allocatableIdsIn / allocatableMatching })` ([PRD 066](066-graphql-reservation-allocatable-matching.md)) | ✅ |
| 17 | `resources(obj)` | navigation | `Appointment.allocatables: [Allocatable!]!`, `Reservation.allocations` | ✅ |
| 18 | `filter(list,pred)` | collection | `allocatables(filter:{ where<TypeKey> })` ([PRD 059](done/059-graphql-typed-where-predicates.md)); reservations via `ReservationFilter` | ◐ |
| 19 | `sort(list,cmp)` | collection | no server sort (results unordered); client sorts | ❌ |
| 20 | `index(list,n)` | collection | only `limit` truncation; no positional index | ◐ |
| 21 | `isPerson` | filter | `Allocatable.type: AllocatableType` (PERSON/RESOURCE) + `AllocatableFilter.isPersonEq: Boolean` | ✅ |
| 22 | `isLocation` | filter | none — the DT `location` annotation is not surfaced or filterable | ❌ |
| 23 | `env(key)` | context | only `Query.me: User` (caller identity); arbitrary env vars not queryable | ◐ |
| 24 | `concat` | derivation | reachable server-side via the expression engine (`displayName`/nameformat, `/table/*` columns); **no general GraphQL expression/column field** | ❌ |
| 25 | `format` (printf) | derivation | same — expression-engine operator; no GraphQL surface | ❌ |
| 26 | `substring` | derivation | extract: same gap. (filter-only `StringWhere.contains/startsWith/endsWith` exists separately) | ❌ |
| 27 | `reverse` | derivation | same — expression-engine operator; no GraphQL surface | ❌ |
| 28 | `note` (plugin) | projection | none — appointment notes (reservation annotation `appointment_note_<id>`) not exposed | ❌ |
| 29 | `duration` (plugin) | projection | none — server computes locale-specific duration; not a field | ❌ |
| 30 | `durationCompare` (plugin) | filter/sort | none | ❌ |

(IDs 1–30 fold the 34 named functions: `and`/`or`/`not` share a row, the four
appointment/reservation date accessors map across rows 9–13.)

## Gap analysis — grouped

**A. Genuine read-API gaps worth considering (server-computed):**
- `note` (#28) — appointment notes are real stored data a caller may legitimately
  want; exposing them is a read-API completeness gap, not a derivation. **Highest
  value.** (Mind §12: notes can carry content the user mustn't see — gate by
  reservation read permission.)
- `duration` / `durationCompare` (#29/#30) — a computed `Appointment.durationMinutes`
  (raw, locale-independent) would let clients avoid re-implementing the
  event-time-calculator's break/rounding rules. The *formatted* duration stays
  client-side. Filtering/sorting by duration (`durationCompare`) is a follow-on.
- `isLocation` (#22) — surface the DT location flag as `Allocatable`/DynamicType
  metadata + an `AllocatableFilter.isLocationEq: Boolean`, mirroring `isPersonEq`.
- `number` (#14) — `AppointmentBlock.sequenceNumber` (1-based index within the
  reservation's sorted blocks) is cheap and occasionally needed for export labels.

**B. Partial — close only if a concrete consumer needs it:**
- Per-language `name(obj, lang)` (#8) — add a `displayName(locale: String)`
  argument / `names: [LocalizedName!]` field. Real for multi-locale deployments.
- **Export nameformat (`nameformat_export`)** — the type's richer export-only
  composition (e.g. the `Kurs`/`Teilkurs`/`Kursgruppe` filter + `{link}`). **Decision:
  two plain fields `displayName` + `displayNameExport`** (not a `format:` argument) —
  the format set is a fixed two, so two server-computed scalar fields are simpler than
  an enum arg / query variable, stay introspectable, and are trivially selectable in
  the column/directive model. (A `format: DISPLAY|EXPORT` variable would only pay off
  if the format set grew.)
- `intervall`/`times`/`date` (#10/#11) — presentation helpers; keep client-side
  unless a non-JS consumer (CSV/MCP) needs them server-rendered.
- `index` (#20), server `sort` (#19), `stringComparator` (#3) — pagination &
  ordering are a broader design (cursor pagination + `orderBy`); track separately,
  don't bolt on per-function.

**C. Field-transformation / displayable-table gap (#24–#27, and the broader
"query → ready-to-display table" use case).** The string functions `concat`,
`format`, `substring`, `reverse` are **not** client-only — they are operators of
the rapla **expression engine** (`ParsedText` / `EvalContext`), which rapla
already evaluates server-side in two places:
- **`Allocatable.displayName`** — the DynamicType's `nameformat` expression
  (with `if`/`concat`/`substring`/`startsWith`) evaluated via `getName(locale)`.
  This is the existing precedent that GraphQL *can* return a server-evaluated
  Function expression — it's just hard-wired to one annotation.
- **The `/table/*` REST API ([PRD 030](030-server-side-view-rendering.md))** — `TableViewEngine` renders each column
  from a stored `ParsedText` expression per row (e.g. `{appointmentBlocks()}`,
  `{p->filter(resources(p), r->isPerson(r))}`), producing finished display
  strings + `/export/csv`. **This is the legacy Swing + HTML view solution** — it
  proves the expression engine renders displayable tables server-side, but it is
  *not* the API a GraphQL/Angular-SPA client should consume (it's coupled to the
  Swing/HTML view model, column config, and CSV export). Treat it as the
  reference implementation of the engine, not a route to reuse.

So the gap is **not** "add a `concat` operator" — it is that GraphQL has **no
general expression/column field**. Closing it once unlocks all four string
functions (plus `if`, `format`, etc.) because they are the expression language.
**A flexible GraphQL query can replace TableView for the SPA.** TableView bundles
two jobs; GraphQL replaces them separately:
- **Selection (which columns/rows)** — GraphQL's core strength, and *more*
  flexible than TableView's fixed server-side column config: the SPA selects
  exactly the typed fields it wants, traverses references, filters, narrows by
  type — in one query. This replaces a TableView column set outright.
- **String derivation (render each cell)** — the part GraphQL won't do inline
  (`concat`, `substring`, `format`, conditional display). Two options:
  1. **Client-side formatting (recommended default).** The SPA gets typed fields
     and formats strings in TypeScript. `displayName` already covers the
     "standard name of the type" case server-side. Maximally flexible,
     GraphQL-idiomatic, no template engine in the query path.
  2. **Optional server-side eval field (escape hatch).** `format(expr: "…")` /
     `render(annotation: "nameformat" | "<custom>")` on
     `Allocatable`/`Reservation`, reusing the same `ParsedText`/`EvalContext`
     engine TableView uses. Worth it ONLY to (i) reuse existing per-deployment
     nameformat/column definitions without re-implementing them in TS, or
     (ii) feed non-JS consumers (CSV export, MCP, server-rendered HTML) that need
     finished strings. **Recommendation: defer until such a consumer is concrete;
     ship selection-via-GraphQL + client-side formatting first.**
**Security:** an evaluated-expression field can reach any attribute/reference the
expression names, so it MUST run under the same §12 read-scope gate as the typed
classification fields (drop, don't render, values the caller can't read) — and a
custom `expr:` argument needs a parse/complexity cap (the nameformat parser, not
arbitrary code).

**D. Already fully covered — no action:** rows 1, 2, 4, 5, 6, 9, 12, 13, 15, 16,
17, 21 (the filter + projection + navigation core).

## Composition fields — keep rapla Functions, bridge them to GraphQL (2026-06-20, converged; discussion open)

The display derivations (`nameformat` & co.) and computed table columns are rapla
**Function compositions**. **Decision: keep rapla Functions as the composition engine** —
they are already bounded (non-Turing, no `eval`), server-side, and evaluate the *existing*
stored compositions in place. Since everything is server-side (no client engine, no
TS↔Java parity), a *new* op-set buys little; rapla's own engine **is** the bounded
composition language. (The op-set catalogued below is "something similar" — kept only as an
*optional later* cleanup, **not** a build requirement; CEL / transform pipeline dropped,
[PRD 074](074-graphql-declarative-views.md).)

**The one real GraphQL gap = the composition-field bridge.** GraphQL can't do composition
natively; the bridge takes an (admin-configured) rapla-Function composition, **evaluates it
server-side, and exposes it as a GraphQL field.** Two scopes + a governance split:

| Scope | what | who defines | schema change? |
|---|---|---|---|
| **type-level — standard variants** | `displayName`/`exportName`/`planningName`/`exportDescription` from the four `nameformat*` annotations | **admin** (edits the annotation) | no (fields are standard) |
| **type-level — custom composite field** | a new reusable Classification field (`Raum.effectiveRoomNumber`, `roomCode`) | **plugin** (extension point — fits `FunctionFactory`/`TableColumnDefinitionExtension`) | yes → controlled |
| **view-level** | a one-off computed column in a view (`concat(substring(times,…))`) | **admin** (in the view spec, `value:`) | no (view-local, server-eval) |

**Admins compose freely** — at view-level and by editing the standard variant annotations.
Only **adding new reusable type-level schema fields** is plugin-gated (schema *stability*,
not security — rapla Functions are bounded + server-side + output-escaped + §12-gated).

**How `displayName` (and the variants) are defined — STILL UNDER DISCUSSION (2026-06-20).**
Leading candidate: keep the existing `nameformat*` annotations (admin-editable, rapla
syntax — **no migration**); the schema generator maps the four standard keys to four
standard fields and generates a **server-evaluated** field each (≈ how `displayName`
already works), with `@derivedFrom` introspection for explicitness:
```graphql
displayName: String @derivedFrom(fields: ["Name", "Beschreibung", "status", "note"])
```
Mapping: `nameformat → displayName` · `nameformat_export → exportName` ·
`nameformat_planning → planningName` · `descriptionformat_export → exportDescription`.
**Fallback chain:** a missing *variant* annotation **falls back to `displayName`**
(`exportName`/`planningName`/`exportDescription` default to `displayName`); `displayName`
itself falls back to `getName()` when there's no `nameformat`. So **all four always
resolve** — variants only *override* `displayName` where set. *(This candidate is not
locked — the storage/definition form is the open discussion.)*

The op-set / catalog below is the **repertoire the bridge evaluates** (and the optional
future clean re-implementation), derived from the *real* dhbw + wochenplan compositions.

### Server mechanics — how the bridge evaluates (thin DataFetcher over `ParsedText`)

Every GraphQL field has a **DataFetcher**; the composition resolvers just funnel the rapla
composition through rapla's **existing `ParsedText` / `EvalContext`** engine — no new
evaluator, no migration.

- **Type-level field** (`displayName`/`exportName`/…): the DataFetcher evaluates the type's
  `nameformat*` annotation against the **entity** — exactly what `classification.getName(locale)`
  (internally `ParsedText.formatName(EvalContext)`) does today. ~already wired.
- **View-level `compute(expr:)`**: the DataFetcher parses the `expr` argument and evaluates
  it against the source entity:
  ```java
  Object entity = env.getSource();                  // current block / reservation / allocatable
  ParsedText p  = ParsedText.parse(env.getArgument("expr"));
  EvalContext c = new EvalContext(locale, annotationName, permissionController,
                                  environment, user, List.of(entity), 0);
  return ParsedText.evalToString(p.eval(c), c);
  ```
  rapla functions resolve `Gebaeude`/`Raumnummer`/`start`/… **themselves from the entity**
  (via the classification) — so the query need **not** also select those raw fields.

**The `EvalContext` is built by the GraphQL layer per evaluation.** Almost everything is
already in the server context; **one** piece is genuinely new wiring:

| `EvalContext` arg | source in the resolver |
|---|---|
| `locale` | request (`Accept-Language`) / user pref / variable |
| `user` | auth context (JWT → rapla `User`) — already present (§12) |
| `permissionController` | server bean |
| `contextObjects` | `env.getSource()` (the entity) |
| `annotationName`, `callStackDepth` | the field / `0` |
| **`environment`** | **NEW bridge** — resolves `env(...)` from `operator.getThreadContextMap()` (the *same* map `CalendarPageController`/`Export2iCalController` populate today). **Server-set only**, never a client variable — see below. |

- **`internal_request` is server-set, never a client argument (§12, load-bearing).** The flag is
  put into `getThreadContextMap()` by the **request channel**: proxy-chosen `*_internal` URL
  (intranet) or auth → `true`; unencrypted public export → `false`. The composition
  `env("internal_request")` then renders person names on/off. A client must **never** be able to
  set it (a GraphQL *variable* or field arg) — otherwise any caller flips it and exfiltrates names.
  For unauthenticated public exports it is the **sole** name-privacy gate (no identity → no
  `canRead`). The in-process export-execution path + the proxy-only-reachability invariant live in
  **[PRD 074 §"Server-side rendering"](074-graphql-declarative-views.md)**; this bridge just *reads*
  the map.
- **§12 comes free:** `EvalContext` carries the `user` + `PermissionController`, and
  `ParsedText.evalToString` already does the `canReadInformation` check (returns `"???"` for
  unreadable) — same §12 path as Swing.
- **Bounded + safe:** rapla functions are non-`eval`/non-Turing → an arbitrary `expr` can't
  execute code, only the known functions over the entity; output is a String (GUI escapes
  it). **Cap** parse depth/complexity (DoS) and **cache** the parsed `ParsedText` per `expr`.

So "computes on the server" = **a thin GraphQL DataFetcher over rapla's existing `ParsedText`
engine**; the only real integration is mapping the **request context → `EvalContext.environment`**
(for `env(...)`).

### Ground truth — the dhbw display derivations (four variants per type)

| Type | variant | composition |
|---|---|---|
| Lehrveranstaltung | `nameformat` | `{if(not(status),"*","")} {Name} {Beschreibung} {format("<%s>",appointment:note())}` |
| | `nameformat_export` / `descriptionformat_export` | …+ `{filter(event:allocatables, r->or(equals(key(type(r)),"Kurs"),"Teilkurs","Kursgruppe"))}` |
| Pruefung | `nameformat` | `{if(not(status),"*","")} {Pruefungsart} {Name} {Beschreibung} {format("<%s>",note())}` |
| Person | `nameformat` / `_planning` | `{surname}, {firstname}` · `{surname}, {firstname} - {campusnetId}: {hinweis_dualis}` |
| | `nameformat_export` | `{if(env("internal_request"), concat(firstname," ",surname), concat())}` |
| Raum | `nameformat` | `{if(or(equals(substring(Gebaeude,0,3),"MOS"),equals(substring(Gebaeude,0,2),"KA")), concat(Raumnummer," ",Raumname), concat(SekundaereRaumnummer," ",Raumname))}` |
| Kurs / Gebaeude | `nameformat` | `{Kursname}` / `{Gebaeudename}` |

### The name field — one parameterized field, variant resolved by context (decided 2026-06-20)

The four composition annotations are **not four GraphQL fields**. Verified against the real
callers (`NameFormatUtil` + the render/export sites), the variant is **chosen by whoever runs
the query**, and the three name variants form a **fallback chain rooted at display** — they are
not peers:

| variant | annotation | who selects it (real callers) | fallback |
|---|---|---|---|
| **DISPLAY** | `KEY_NAME_FORMAT` | UI calendar block (`RaplaBlock`), info — the **root/default** | `getName()` |
| **EXPORT** | `KEY_NAME_FORMAT_EXPORT` | all export services (Exchange subject, iCal summary, `HTMLRaplaBlock`) | → DISPLAY |
| **PLANNING** | `KEY_NAME_FORMAT_PLANNING` | the resource **tree** only (`TreeItemFactorySwing`, `AllocatableSelection`) — Swing-only today | → DISPLAY |

→ **One parameterized field**, variant = the `EvalContext.annotationName`, resolved:

```
name(variant: NameVariant = DISPLAY)        # NameVariant { DISPLAY EXPORT PLANNING }
  1. explicit arg              (always wins)
  2. else execution context    (export service sets EXPORT; SPA tree-view sets PLANNING) — graphQLContext
  3. else DISPLAY              (the static default; visible in SDL)
```

- This is why **the same stored query yields DISPLAY in the UI and EXPORT when the export
  service runs it in-process** (the service sets the context; see [PRD 074](074-graphql-declarative-views.md) §"Server-side
  rendering") — and the tree can force `name(variant: PLANNING)`.
- The `NameVariant` enum **self-documents the three variants in the served SDL** — the
  schema-file-only AI/author sees them natively.
- The existing **`displayName`** field stays as `@deprecated(reason: "use name(variant: DISPLAY)")`
  → resolves to `name(DISPLAY)`; no SPA break, migration is introspectable. (Unlike the *function*
  catalog, which omits legacy names — GraphQL fields carry native `@deprecated`.)
- **`description`** is a **separate** field (export **body**: `KEY_DESCRIPTION_FORMAT_EXPORT` —
  Exchange body / iCal description, *not* the subject). Single-variant today; its fallback is the
  export service's own (Exchange attendee-list / iCal `null`), **not** the name chain — so it is
  not symmetric to `name`.
- **`internal_request` is NOT this arg** — it is the server-only security gate (names appear at
  all), carried in `environment`, never client-settable. The `variant` arg is presentational and
  may be client/renderer-chosen.

### Decomposition — most of it is already GraphQL; the residual is tiny

| Part of a derivation | maps to |
|---|---|
| fields (`Name`, `Beschreibung`, `status`, `surname`, `Raumnummer`, …) | **raw typed GraphQL fields** (generated, already present) |
| `appointment:note()` | **GraphQL field** `note` (Phase 1) |
| `filter(allocatables, r->key(type(r))∈{Kurs,…})` | **GraphQL** `allocatables(filter:{ typeKeyIn:[…] })` (selection) |
| `env("internal_request")` | **server-set execution context** (`environment`, never a client arg — §12) |
| **`if` · `not` · `or` · `equals` · `concat` · `substring` · `format`** | **the op-set** (string/logic) |

### Function inventory + the `FunctionDescriptor` — levels via arg-types, B-hard (decided 2026-06-20)

Verified against the real `eval` bodies (`StandardFunctions` + plugins). **"Level" is not a
separate tag — it is the function's *source-arg type*.** Validation (`saveView`) is a **type-check
over the parsed tree**, context-aware through lambda bindings: `filter(resources(p), r->isPerson(r))`
is valid because `resources()→[Allocatable]`, `filter` binds `r:Allocatable`, `isPerson` wants an
Allocatable; `start()` on an Allocatable-rooted column is a **type error**. Navigation is
**one-way: event → resources, never the inverse** (an Allocatable has no back-link to its events),
so EVENT functions are invalid on Allocatable-rooted columns — exactly what B-hard rejects.

**A (name field), B (levels), and I (return types) collapse into ONE descriptor per function:**
```
FunctionDescriptor { name, namespace, argTypes[] (+ arity), returnType, doc }
```
from which we generate (a) the served-SDL catalog, (b) return-type inference (`@derivedFrom` +
typed field), (c) the `saveView` tree type-check (subsumes "levels"), **and (d) generated GraphQL
fields for the scalar/derived accessors** (`AppointmentBlock.duration`, `…start`, `…times`,
`Allocatable.isPerson`, …). The catalog/fields are generated from the live `FunctionFactory`
registry at schema build (all plugins present).

### How a (plugin) function becomes a GraphQL field — the descriptor SPI (decided 2026-06-20: option a)

Today the `FunctionFactory` interface has **only `createFunction(name, args)`** — no enumeration, no
signatures. So a plugin function like **`org.rapla.eventtimecalculator:duration` is reachable *only*
via the EL** (`compute("…:duration(p)")`); it is **not** a GraphQL field, and the GraphQL generator
has no knowledge of functions. To make scalar/derived accessors first-class **generated fields**, two
mechanisms were weighed:

- **(a) Descriptor SPI — chosen.** Extend `FunctionFactory` (or a companion SPI) so every factory —
  core *and* plugin — **declares its functions as `FunctionDescriptor`s** in *rapla terms* (name,
  source-arg level, return type, `fieldable`, doc), **knowing nothing about GraphQL**. One central
  generator (`ClassificationSdlGenerator`) reads **all** descriptors and uniformly emits the fields
  on their source types + wires the DataFetcher (→ `ParsedText(fn).eval(EvalContext)`). Fits rapla's
  existing declarative plugin model (plugins already contribute `FunctionFactory` via `@Extension`;
  this just adds metadata). Constrained to function-as-field.
- **(b) Plugin wires GraphQL itself** (RuntimeWiring / SDL type-extension + DataFetcher) — rejected as
  the default: each plugin must know GraphQL; distributed plumbing. Kept only as an **escalation** for
  a plugin that needs arbitrary GraphQL beyond function-as-field.

So **`duration` becomes `AppointmentBlock.duration: String`** because the eventtimecalculator plugin
declares a descriptor (source EVENT → AppointmentBlock/Appointment, return String); the central
generator emits the field. **Plugin-namespacing:** the GraphQL field uses the **local name**
(`duration`); the namespace lives in the descriptor; a **collision rule** is needed if two plugins
expose the same local name on the same source type. **Field-allowedness is then pure schema** — a
consumer/AI sees `AppointmentBlock.duration` (and *not* `Allocatable.duration`) by introspection;
GraphQL validates it. The "level" is only the generator's *placement rule* + the compute-EL check —
it never surfaces to the consumer.

**EVENT** — source resolves to `Block | Appointment | Reservation | CalendarModel`:

| fn | signature |
|---|---|
| `start` `end` `lastchanged` | (event) → DateTime |
| `date` (event) → Date · `intervall` (event) → TimeInterval | |
| `times` `number` | (event) → String |
| `appointments`(event?) → [Appointment] · `appointmentBlocks`(event?,from?,to?) → [AppointmentBlock] | |
| `resources`(event?) → [Allocatable] · `events`(event?) → [Reservation] | |
| `note` *(ns `appointment`)* (appt?) → String | |
| `duration` *(ns `org.rapla.eventtimecalculator`)* (event?) → String · `durationCompare`(event?,Int) → Int | |

**CLASSIFIABLE** — source `Allocatable | Reservation` (has a Classification):
`attribute`(classifiable,key) → attr-value · `type`(classifiable) → DynamicType · `key`(category|attrVar) → String · `name`(named?) → String · `parent`(raplaObj) → Entity

**ALLOCATABLE-only** — tests `KEY_CLASSIFICATION_TYPE` (only exists on allocatable types):
`isPerson`(alloc?) → Boolean · `isLocation`(alloc?) → Boolean

**ANY** — argument-based, source-agnostic:
`not` `and` `or`(Bool…) → Bool · `if`(Bool,T,T) → T · `equals`(a,b) → Bool ·
`concat`(String… **0..∞**) → String · `substring`(String,Int,Int) → String · `format`(String,…) → String ·
`filter`([T], T→Bool) → [T] · `sort`([T],cmp) → [T] · `index`([T],Int) → T · `reverse` · `stringComparator` ·
`env`(String) → String/Bool *(reads server-set `environment`; see §"Server mechanics")*

**VIEW_TITLE-only** — source is the CalendarModel title context (`CalendarModelParseContext`, today `@Deprecated`):
`allocatables` → [Allocatable] · `timeIntervall` → TimeInterval · `selectedDate` → Date

**Flags — all resolved (2026-06-20):**
- **`number`** (ID=`number`, `AppointmentBlockFunction`): the **block's sequence number within its
  reservation** ("which appointment in the series" — 1-based). Expands all the reservation's
  appointments to blocks from `getFirstDate()`, counts `headSet(block).size()+1`. Source =
  AppointmentBlock (EVENT), returns String (semantically Int). Name/class consistent (class works
  over the block; `number` = the block's number).
- **Raum** `nameformat` `or(MOS,KA)` in the ground-truth table **is correct** (the wochenplan
  dataset is older).
- **`nameformat_planing`** (one-`n`) in wochenplan `personen` is **dead data** — the code constant
  is `nameformat_planning` (two-`n`); the typo'd annotation is never read (harmless orphan).

### The op-set (≈7 ops — the whole string/logic residual)

> **Function-name standardization is investigated separately in
> [PRD 075](075-expression-language-standardization.md)** — whether these op names align to a
> standard vocabulary (common-intersection / CEL / minimal aliases), and whether one
> expression vocabulary should be shared across views, filters, exports, validation, import
> mapping, etc. The engine stays rapla's own (decided here); 075 only concerns naming + scope.

```
if(cond, then, else)   not(x)   or(a,b)   equals(a,b)
concat(a, b, …)        substring(s, start, len)   format(pattern, x)
```

Everything else is GraphQL or a variable. This is "something similar to the rapla
Functions" (per the maintainer), reimplemented cleanly, **bounded, non-`eval`,
server-side** — no CEL, no client runtime, no TS↔Java parity.

### Storage decision (corrected 2026-06-20)

- **`nameformat` & co. STAY at the type** (annotation storage is fine — the canonical
  display of a type, reused everywhere). They are evaluated **server-side via the op-set**
  and exposed as **`displayName(variant: DISPLAY|EXPORT|PLANNING)`** (or named fields). The
  "out of annotation" goal was **only for the table-column definitions**, not nameformat.
- **Table-column definitions MOVE** from the legacy config into the **GraphQL view
  definition** (view-level, explicit — [PRD 074](074-graphql-declarative-views.md)). The op-set serves both: nameformat at the
  type, and any computed view column.
- **No migration; legacy keeps its evaluation.** The `nameformat` annotations stay on the
  types and serve **both** paths over the **same stored rapla syntax**:
  - **Legacy Swing + old exports (iCal/CSV/TableView)** keep evaluating them via rapla's
    existing `ParsedText` engine — **unchanged**.
  - The **new GraphQL/SPA path is additive** — it reads the *same* nameformat annotations
    to produce `displayName` (server-side); initially it can reuse `ParsedText`, with the
    bounded op-set as the clean re-implementation over time. **No conversion of stored
    strings, no disruption of legacy.**
  Legacy column defs stay legacy; new GraphQL views are **greenfield** ([PRD 074](074-graphql-declarative-views.md) — no
  TableView migration). The op-set is mainly the bounded composition language for **new
  view-level computed columns**; for nameformat the existing evaluation is reused
  server-side (a clean op-set swap is optional, later).
  - *Storage (corrected):* the legacy TableView config is **always a combination of two
    complementary stores** — the per-type **`tablecolumn_*` annotations** (the column
    *compositions*, `defaultValue` per column, incl. type-specific custom columns) **+** the
    **`tableview.config`** preference (the *view configuration* — which views, which columns
    each shows, ordering, sorting). Both are present in both datasets (wochenplan: 121
    annotations + config; dhbw: 50 + config). They complement, not replace, each other.
    Both predate the GraphQL views; the new views replace the **combination** — no migration.

### Use-case catalog — display derivations (dhbw + wochenplan, verified 2026-06-20)

Across both datasets the derivations fall into **five complexity tiers** — the first two
are simple naming, the rest need the op-set:

| Tier | Example | mechanism |
|---|---|---|
| 1 **single field** | `{name}` · `{title}` · `{Kursname}` | field |
| 2 **field-list** | `{name} {title}` · `{title} {thema}` · `{name}: "{title}" - {hinweis}` | interpolation (simple naming) |
| 3 **optional suffix** | `{name}{if(equals("",substring(ebene,0,1)),"",concat(":",ebene))}` | `if`+`substring`+`concat` (skip-if-empty) |
| 4 **format + plugin** | `{zusatz} {title} {format("<%s>",appointment:note())}` | `format` + `note` |
| 5 **marker chains** | `{angezeigter_name} {surname} {if(im_haus," # ","")}{if(equals(key(dispo-modus),"anfrage"),"*","")}…{if(ausdrucksstarke_yl,"+","")}` | `if`+`equals`+`key`+`<bool field>` |
| (Raum) **conditional value** | `{if(or(equals(substring(Gebaeude,0,3),"MOS"),…"KA"), concat(Raumnummer," ",Raumname), concat(SekundaereRaumnummer," ",Raumname))}` | `if`+`or`+`equals`+`substring`+`concat` |

**Consolidated op-set** (everything tiers 3–5 + Raum need):
```
if · not · or · equals          concat · substring · format
key(enum) · type                <bool field> as condition
plugin: note    context: env
```
Two **convenience helpers** for the recurring patterns (keep the op-set readable):
```
marker(cond, symbol)        # = if(cond, symbol, "")                              — tier 5
optional(field, separator)  # = if(isEmpty(field), "", concat(separator, field))  — tier 3
```

**Placement** (per the reusability rule): tiers 1–2 → simple field-list naming
(type-level); tiers 3–5 + the Raum conditional → op-set in a **named derived field**
(type-level, reusable — e.g. `Raum.effectiveRoomNumber`, `Person.planningName`) or
view-level for a one-off.

### Worked example — the hardest case (`Raum`), factored

The **real** `Raum` nameformat (today's `displayName`) packs everything into one rapla
composition:
```
{if(or(equals(substring(Gebaeude,0,3),"MOS"),equals(substring(Gebaeude,0,2),"KA")),
    concat(Raumnummer," ",Raumname),
    concat(SekundaereRaumnummer," ",Raumname))}
```
The pattern — **factor the complex value selection into a named derived field, keep the
display trivial** — stays in **rapla syntax** (no new format, no migration):
```
# derived field (reusable) — picks the right room number:
effectiveRoomNumber = {if(or(equals(substring(Gebaeude,0,3),"MOS"),equals(substring(Gebaeude,0,2),"KA")),Raumnummer,SekundaereRaumnummer)}
# displayName then just concatenates:
displayName         = {concat(effectiveRoomNumber," ",Raumname)}
```
The logic lives in one named, bounded, reusable field; `displayName` stays trivial.
(*How `displayName` is stored/defined is still under discussion — see below.*)
(Data note: `substring(Gebaeude,…)` really derives the *campus* — missing structured data;
a real `Campus` field shrinks it to
`{if(or(equals(Campus,"KA"),equals(Campus,"MOS")),Raumnummer,SekundaereRaumnummer)}`.)

## Plan — phased (each phase is independently shippable)

0. **Phase 0 — Filterable nested `Appointment.allocatables` (DONE, 2026-06-19).**
   Optional `filter` argument on the nested field so a view can split rooms/persons
   per column in one query. v1 scalars only (`typeKeyIn`, `isPersonEq`, …), gated
   **after** `pc.canRead` (§12). Shipped with a dedicated `AppointmentAllocatableFilter`
   input (per the Schema-design guideline above) — `idIn`/`limit`/`accessibleByUsername`
   are rejected with validation errors, not silently dropped. Regression + leak tests
   in `ReservationGraphQLControllerTest`.

1. **Phase 1 — Appointment note read field.** `Appointment.note: String` (and/or
   `AppointmentBlock.note`), resolved from the `appointment_note_<id>` reservation
   annotation, gated by reservation read permission (§12). Tier-1 wire test +
   tier-3 leak test (non-owner must not see notes on reservations they can't read).
2. **Phase 2 — Raw duration field.** `Appointment.durationMinutes: Int` computed
   via the existing `EventTimeModel` (reuse `DurationFunctions.calcDuration`), null
   when the plugin/config is absent. Tier-1 against a fixture with a break rule.
3. **Phase 3 — `isLocation` metadata + filter.** Surface the DT location flag;
   add `AllocatableFilter.isLocationEq: Boolean`, mirroring `isPersonEq`’s
   resolver + leak posture. Tier-3 filter test.
4. **Phase 4 — `AppointmentBlock.sequenceNumber`.** 1-based index within the
   reservation's sorted blocks (reuse `AppointmentBlockFunction` logic). Tier-1.
5. **Phase 5 (optional, gated on a consumer) — locale-aware names + duration
   filter/sort.** `displayName(locale:)`; `durationCompare` → a `where` numeric
   predicate on `durationMinutes`. Defer until a concrete CSV/MCP/i18n consumer
   asks.

Phases 1–4 are small and additive (new nullable fields / one filter flag); none
changes existing query results. Out-of-scope string-derivation functions (group C)
get **no** GraphQL surface by decision.

## Tests

- **Tier 1 (wire):** for each new field — note, durationMinutes, sequenceNumber —
  a `rapla-core`/`rapla-app` JSON-shape test over a hand-built fixture, asserting
  the value matches the equivalent Function's output for the same entity.
- **Tier 3 (MockMvc leak):** note + any permission-sensitive field gets the §12
  leak test — non-owner/limited user, mixed visible/hidden reservations, response
  byte-identical to the visible-only subset.
- **Equivalence guard:** a `rapla-core` test that, for a sample DynamicType,
  asserts the GraphQL `typeKey`/`key`/`parent`/`displayName` resolvers return the
  same values the corresponding Functions (`type`,`key`,`parent`,`name`) produce —
  locking the projection equivalences in row D against drift.

## Schema-design guideline — focused filter inputs over one reused filter

Motivated by the nested-`allocatables` filter (the `Appointment.allocatables(filter:)`
data-layer addition, 2026-06-19): reusing the broad `AllocatableFilter` on a field
whose resolver honours only a subset of its predicates produced a silent
**contract lie**. The general rule, to apply on every new filter field:

> **A GraphQL input type *is* the contract of what the resolver behind it honours.
> A field must only advertise predicates it actually evaluates.** GraphQL raises no
> error for a set-but-ignored input field — `set-but-ignored` is syntactically
> valid — so an over-broad input breaks its promise *invisibly*.

**The defect, by example.** `Appointment.allocatables(filter: AllocatableFilter)`
reused the whole query-root filter; the nested resolver reads only `typeKeyIn` +
`isPersonEq`. Three concrete lies followed:

| Query | Client expectation | Actual behaviour |
|---|---|---|
| `filter:{ idIn:["raum-A1"] }` | only `raum-A1` | **all** allocatables (idIn dropped) |
| `filter:{ whereRaum:{ Kapazitaet:{ gt:50 } } }` | rooms > 50 seats | **all** rooms (typed where never run) |
| `filter:{ accessibleByUsername:"user-X" }` | user-X's access scope | ignored — PRD-069 admin-scope check skipped |

The `whereRaum` lie is the nastiest because it is *automatic*:
`ClassificationSdlGenerator` injects every `where<TypeKey>` block via
`extend input AllocatableFilter` **globally**, so any field reusing that input
inherits the typed-where surface whether or not its resolver wires the
`WhereEvaluator`. Reuse is therefore **all-or-nothing** — you cannot opt a field
out of the generated predicates.

**The rule, operationalised:**
1. **Focused input per use-site.** When a field honours a subset, give it a
   dedicated input containing *only* the honoured predicates (e.g.
   `AppointmentAllocatableFilter` with the v1 scalars). Setting a non-existent
   field then yields a clean validation error instead of a silent drop —
   the schema *cannot* lie because the lie won't parse.
2. **Reuse the broad input only where the resolver honours all of it** —
   typically the query roots (`Query.allocatables`, `Query.reservations`).
3. **Share the *logic*, not the *type surface*.** DRY belongs in the matcher
   (`ClassificationGraphQLController.matchesMap` funnels every site through the
   same `fromMap`→`matches` path); it must **not** drive input-type reuse.
4. **`extend input` targets are a design lever.** Point the generator's
   `extend input` at the input that actually evaluates `where<TypeKey>`; a focused
   field that should not carry typed-where simply isn't a target, so no phantom
   predicates appear.
5. **Test-per-field invariant.** Every filter field gets a test that a set
   predicate either *takes effect* or is *rejected* — never silently ignored.
   This locks the contract generally, not just at the one field that exposed it.

Longer term, if a filter input keeps accreting concerns (selection + text-search +
id-union + access-scope + pagination), prefer **nested predicate groups**
(`filter:{ type:{…}, text:{…}, access:{…} }`) over a flat monolith: GraphQL has no
input inheritance, so nesting is the idiomatic way to compose cohesive, honest
filters — each site includes only the groups it honours. (Hasura/Prisma compose
`AND`/`OR`/`NOT` the same way.)

This guideline governs the declarative-view work too: **a view's transform layer
may safely omit a filter it pushed into GraphQL *only if* every advertised
predicate truly runs** — see [PRD 074](074-graphql-declarative-views.md).

## Open Questions

1. **Notes & §12** — are appointment notes ever more sensitive than the reservation
   itself (e.g. internal-only)? If so, Phase 1 needs a finer gate than
   "reservation readable". Confirm with the data model before shipping.
2. **Duration unit** — `durationMinutes: Int` vs a richer `Duration` scalar? Int is
   simplest and matches the calculator's resolution; confirm no sub-minute need.
3. **`env`/`me` scope** — is `Query.me` sufficient, or do callers need other
   request-context values (deployment id, locale) the nameformat `env()` exposes?
4. **Pagination/sort (rows 3/19/20)** — split into a dedicated cursor-pagination +
   `orderBy` PRD rather than per-function patches? (Recommended.)
5. **Cross-check** against [PRD 028](028-angular-power-search.md) (power search) and [PRD 069](069-graphql-resource-access-read-api.md) (resource-access) so
   new filters compose with `searchText`/`matchKind` and the access-by-target
   selectors rather than duplicating them.
6. **Admin-defined saved GraphQL table views (follow-on PRD).** The strategic
   replacement for TableView's per-deployment column config: an admin saves a
   *GraphQL query + client-side column spec* (header / result-path / optional
   client formatter), stored as a `TableConfig`-analog config entity; the SPA runs
   it and renders the grid. Key invariants for that PRD: (a) the saved query
   executes **under each running user's permissions** — §12 is enforced by the
   resolvers, so authoring a query grants no data access; (b) validate the query
   against the live schema at save time + keep GraphQL complexity/depth caps
   ([PRD 062](062-graphql-api-robustness.md)); (c) decide global-admin vs. group-admin authoring scope
   (`canAdminUsers`). This makes the client-side-formatting path (group C / Phase
   6 option 1) the default and leaves the server-side eval field unneeded for the
   SPA. → **Now [PRD 074 — Declarative GraphQL View Definitions](074-graphql-declarative-views.md)** (broadened: query document = whole view; variables→controls, selection→output, client directives for the rest; SPA-only).
