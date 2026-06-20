# PRD 073 — Rapla Server Functions ↔ GraphQL Equivalence Map

**Status:** draft — analysis only, 2026-06-19. No code yet; this PRD catalogues the
rapla expression-language **Functions** and their GraphQL-API equivalents, then
proposes which gaps are worth closing.

## Goal

Rapla has a small server-side **expression language** — the
`org.rapla.entities.extensionpoints.Function` tree — used inside DynamicType
nameformats, classification filters, and annotations (e.g. a room's
`nameformat` `{if(Gebaeude startsWith "MOS", Raumnummer+" "+Raumname, …)}`).
The GraphQL API (PRD 035 family) is the modern read/query surface. This PRD
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
     (PRD 059, done) + structural filters.
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
GraphQL mutations (write API — PRDs 056/057/061/063); the Swing/iCal nameformat
engine itself (unchanged — Functions remain the templating language there).

## Equivalence table

Status legend: **✅ full** · **◐ partial** · **❌ gap** · **N/A** (role doesn't
belong on a read/query API).

| # | Function (id) | Role | GraphQL equivalent | Status |
|---|---|---|---|---|
| 1 | `and` / `or` / `not` | filter | `<TypeKey>Where.AND:[…] / OR:[…] / NOT:{…}` (PRD 059, depth-cap 10, `WhereEvaluator`) | ✅ |
| 2 | `equals` | filter | `*Where.eq` / `.ne` on every typed predicate (`StringWhere`, `IntWhere`, `BooleanWhere`, `LocalDateTimeWhere`, `CategoryWhere`, `AllocatableWhere`) | ✅ |
| 3 | `stringComparator` | sort | no comparator; closest is `searchText` + `matchKind` ranking (PRD 028) | ◐ |
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
| 16 | `events(obj)` | navigation | `Query.reservations(filter:{ allocatableIdsIn / allocatableMatching })` (PRD 066) | ✅ |
| 17 | `resources(obj)` | navigation | `Appointment.allocatables: [Allocatable!]!`, `Reservation.allocations` | ✅ |
| 18 | `filter(list,pred)` | collection | `allocatables(filter:{ where<TypeKey> })` (PRD 059); reservations via `ReservationFilter` | ◐ |
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
- **The `/table/*` REST API (PRD 030)** — `TableViewEngine` renders each column
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
5. **Cross-check** against PRD 028 (power search) and PRD 069 (resource-access) so
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
   (PRD 062); (c) decide global-admin vs. group-admin authoring scope
   (`canAdminUsers`). This makes the client-side-formatting path (group C / Phase
   6 option 1) the default and leaves the server-side eval field unneeded for the
   SPA. → **Now [PRD 074 — Declarative GraphQL View Definitions](074-graphql-declarative-views.md)** (broadened: query document = whole view; variables→controls, selection→output, client directives for the rest; SPA-only).
