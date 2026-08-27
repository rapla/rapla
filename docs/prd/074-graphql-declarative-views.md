# PRD 074 — Declarative GraphQL View Definitions

**Status:** draft — 2026-06-20. **Preferred design: GraphQL-native views with NO
expression engine** (server-evaluated composition fields + GraphQL selection +
presentation directives — see §"Preferred design"). **No expression engine:** complex
compositions are evaluated **server-side by rapla's own `ParsedText` engine**; CEL / a
client transform pipeline were evaluated and **dropped** (no added capability over
rapla's engine). Data-layer feeder: [PRD 073](073-graphql-function-equivalents.md).

## Goal

A **saved view** lets an admin define a data view by storing a **GraphQL document**
(inputs + data selection) plus thin **presentation metadata** (column order/header where
convention isn't enough) — with **no client redeploy and no server-side column config**.
The Angular SPA is a generic renderer over the GraphQL response; server-rendered export
(CSV/HTML/iCal) renders the *same* data. Replaces TableView's per-deployment column
config (`/table/*`, [PRD 030](030-server-side-view-rendering.md) — deprecated, frozen, **no migration**; the new path is
greenfield).

## Architecture at a glance

GraphQL is the spine. Read tables need **no expression engine**: composition columns are
**server-evaluated GraphQL fields** (rapla's existing `ParsedText` engine), selection is
GraphQL, shaping is convention + a few presentation directives. Each surface uses the
best tool for *its* output:

| Surface | Renders | Engine | Status |
|---|---|---|---|
| **Read tables** (SPA + CSV/HTML/iCal) | server-eval fields + presentation | **none** — rapla `ParsedText`, server-side; `cdk-table` + `ngComponentOutlet` registry | this PRD |
| **Charts** | client only | **Vega-Lite** (interpreter mode, CSP-safe) | companion |
| **Edit forms** | client only | **ngx-formly** → GraphQL mutations | companion / future [PRD 075](075-expression-language-standardization.md) |

**Adaptive Cards is dropped** (cross-host portability isn't needed for an Angular-only
SPA — ngx-formly replaces it for forms; server-side `displayName`/composition fields +
flatten parent-projection replace its templating role on the read side). **CEL and a
client transform pipeline were evaluated and dropped** — composition is evaluated
server-side by rapla's own engine, so no dual-runtime / client engine is needed (see
§"If a view ever needs more").

## Locked decisions

1. **Separation of concerns** — GraphQL owns **prediction (filter) + navigation (which
   data)**; presentation owns **column layout / formatting / grouping**. Composition
   columns (incl. nameformats) are evaluated **server-side via rapla's `ParsedText`
   engine** and returned as plain GraphQL fields (`displayName`/`times`/`duration`).
2. **Maximize GraphQL.** Filtering/partitioning/computed scalars belong in the query —
   e.g. the room/course/lecturer split is **aliased, filtered sub-selections**:
   ```graphql
   resources: allocatables(filter:{ isPersonEq:false }) { displayName }
   persons:   allocatables(filter:{ isPersonEq:true })  { displayName }
   ```
   (Needs data-layer additions — see Dependencies.)
3. **No expression engine; no dual-runtime.** Compositions evaluate **server-side only**,
   reusing rapla's engine — no client expression runtime, no TS↔Java parity surface.
   Server-rendered export reuses the same server-side evaluation.
4. **Directives are optional overrides.** Field order = column order, alias = column key
   → localized header, type → formatting, list → join (`, ` default). Explicit directives
   override *on demand* only: `@column(header:/order:)`, `@join(separator:)`, `@flatten`,
   `@group`/`@hidden`. CEL is **not** used — see §"If a view ever needs more".
5. **Views are stored as persisted-query *text*** (Preferences/`RaplaMap`, like
   `tableview.config`; defaults shipped as code constants) — **no `idref` binding, no invented
   format** (GraphQL is name-based by spec; durable rationale extracted to
   [ADR 0005](../decisions/0005-graphql-keys-are-api-identity.md)). Key currency is handled by **revalidate-and-mark**:
   a type/attribute/category save triggers the existing `HotSwappableGraphQlSource.rebuild()`,
   after which **all** stored views are re-validated against the new schema and marked
   `valid`/`invalidReason`; invalid views keep their text, refuse execution, and the **admin
   fixes** them (no auto-migrate, no silent prune). `saveView` validates the GraphQL document
   **and** each `compute(...)` EL before persisting (parity with `setAnnotation`). See
   §"Storage & lifecycle — persisted-query text + revalidate-on-change".
6. **SPA views are independent — no `CalendarModel` re-implementation** (decided 2026-06-20). Each
   view = its `@view` query + **its own** inputs + render-mode; a saved view = `{ view, inputs }`,
   standalone. We deliberately **give up the legacy uniform "switch view, keep inputs"** (the
   in-place table↔week↔month flip over one shared input set) — a genuinely *charming* rapla feature
   — as a **simplification trade**. Consequences: **no** uniform per-domain input contract, **no**
   `timeContext` cross-render-mode reconciliation (fixed/dynamic width-preservation/snapping), **no**
   domain-switching/selection-transfer machinery. Each view declares only the inputs it needs
   (table → `from/to`+filter; week → anchor+filter; allocatable list → type-filter, no date;
   conflict → conflict-sourced selection); fixed/dynamic date stays **per view**. The flip-switch
   **may return later as a pure client-side convenience** over views that happen to share inputs —
   not as server input-contract/`timeContext` machinery. This **supersedes** the explored (U)
   uniform-input-contract direction (never recorded — it was the legacy generality we chose not to
   inherit).
7. **Pre-made views are immutable code constants; the admin catalog is fork-only.** Rapla ships
   three canonical view definitions (`Termine_events`, `Termine_appointments`, `Termine_perDay`) as
   query-text constants in code. They are always available, never invalidated by model changes
   (kept current with the schema — a breaking schema change is a developer/test responsibility),
   and cannot be overwritten or deleted via the admin API. An admin forks a pre-made view by
   loading its query text and saving it under a new name; the fork becomes a custom view subject
   to the normal lifecycle (editable, deletable, revalidate-on-change). There is no "reset to
   default" — the original is always reachable under its canonical name regardless of Preferences
   state.
8. **GraphiQL is the view authoring editor.** Rapla already ships `/api/graphiql` with schema
   introspection, variable autocompletion, and full cookie-based auth (HttpOnly `access_token`
   cookie + XSRF double-submit + 401→refresh→replay — [PRD 072](done/072-server-side-login-dialog.md) Phase 3/4). The admin authors
   views directly in GraphiQL (load query text, edit, validate, save under a name). No separate
   view-editor is built. GraphiQL gains two toolbar actions — **Load view** (populate editor
   from a stored view) and **Save view** (call `saveView` with the current query text + a name)
   — both fire through the existing fetcher and inherit auth for free. The inline schema feedback
   is the authoring affordance; the `invalidReason` from `listViews` is the fix signal.

## Inputs = query variables (controls inferred by convention)

The client maps each variable to a control by **name + type**; pageability adds
navigation. No directive for the common cases:

| Variable(s) | Inferred control |
|---|---|
| `from` + `to` (date/time) | date-range picker |
| `reservationTypes: [String!]` | reservation-type checkboxes (Lehrveranstaltung / Prüfung / …) |
| `allocatableIds: [ID!]` | resource-tree picker (rooms / courses / persons) |
| `name` / search string | search combobox |
| query is pageable (offset/cursor) | next / prev — *future* |

Controls are state over variables: the control mutates a variable, the query re-runs.
Non-conventional bindings use a `@control` client directive (override only).

**Filters live in variables, not the view definition** — and there are **two filter
sources with two homes:**
- **Annotation filters stay *inline* in the query** — they *are* the view definition. The
  `Kurs` column's `allocatables(filter:{ typeIn:[Kurs,Teilkurs,Kursgruppe] })`,
  the `Raum` column's room-type filter, etc. define *which allocatables each column shows*;
  they come from the column annotation and **never** become variables. This is exactly the
  shipped **`Appointment.allocatables(filter: AppointmentAllocatableFilter)`** field
  ([PRD 073](073-graphql-function-equivalents.md) Phase 0, 2026-06-19) — `typeKeyIn`/`isPersonEq` were its v1 scalars (renamed `typeIn` + per-kind enums, [PRD 059](done/059-graphql-typed-where-predicates.md) Phase 7 / ADR 0005).
- **CalendarModel filters become *variables*** — all user state, carried in the root's
  `filter: $filter` (`ReservationFilter`, SDL below):
  - **reservation type** (checkbox) → `typeIn`; per-type **classification rules**
    (*neue Regel für*) → the generated `where<TypeKey>` predicates (AND/OR/NOT + attribute
    comparisons, [PRD 059](done/059-graphql-typed-where-predicates.md)) — richer than a flat type list
  - **resource-tree selection** → `allocatableMatching` ([PRD 066](066-graphql-reservation-allocatable-matching.md)) or `allocatableIdsIn`
  - **date range** (from/to control) → `from` / `to`

```graphql
query Termine($filter: ReservationFilter!) {
  appointmentBlocks(filter: $filter) {    # $filter ← CalendarModel, e.g.
        # { from, to, typeIn:[Lehrveranstaltung],
        #   whereLehrveranstaltung:{ AND:[{campus:{eq:"KA"}},{year:{eq:2024}}] },   # neue Regel für
        #   allocatableMatching:{ typeIn:[Raum,Teilraum], idIn:["room-1"] } }  # resource tree
    name: reservation { displayName }
    kurs: allocatables(filter:{ typeIn:[Kurs,Teilkurs,Kursgruppe] }) { displayName }  # annotation filter — INLINE
    # … start, end, person, raum, duration … (see Worked queries) …
  }
}
```
(`from`/`to` live **inside** `ReservationFilter`, so they're part of `$filter` — no
separate args.)
`reservations(filter:)` / `appointmentBlocks(filter:)` — type, `where` rules **and**
`allocatableMatching` — is **all from the model** → `$filter`. The per-column
`allocatables(filter:)` is **from the annotation** → inline.

The variable's type is the **existing** schema input `ReservationFilter!`:
```graphql
input ReservationFilter {
  from: LocalDateTime!   to: LocalDateTime!        # mandatory window (the date-range control)
  typeIn: [ReservationTypeKey!]                    # reservation type(s) (generated per-kind enum; per-type rules below carry the type)
  whereLehrveranstaltung: LehrveranstaltungWhere   # ← runtime-generated per type (PRD 059): the *neue Regel für* rules (AND/OR/NOT + attribute predicates)
  allocatableMatching: AllocatableFilter           # PRD 066 — the resource-tree selection (typeIn + per-type where + idIn) in one shot
  allocatableIdsIn: [ID!]                          #   …or explicit ids
  searchText: String   matchKind: MatchKind        # PRD 028 power search
  accessibleByUsername: String   accessibleByGroup: [String!]   accessLevel: AccessLevel   # PRD 069 admin-scoped
  ownerEq: ID   nameContains: String   limit: Int
}
```
So the **CalendarModel** maps straight onto this input — the generated `where<TypeKey>`
predicates carry the *neue Regel für* classification rules (far richer than a flat type
list), and `allocatableMatching` carries the resource-tree selection.

## Data = GraphQL (prediction + navigation only)

The query decides **what data is in the result**: prediction (`where` [PRD 059](done/059-graphql-typed-where-predicates.md) +
`searchText`/`matchKind` [PRD 028](028-angular-power-search.md) + access selectors [PRD 069](069-graphql-resource-access-read-api.md)), navigation
(selection), and **server-computed scalars** the client can't derive
(`displayName`, `durationMinutes`, typed attributes — the [PRD 073](073-graphql-function-equivalents.md) gaps). It selects
raw nested data; it does **not** shape a table.

**Trust precondition for "maximize GraphQL".** A view may push a filter into the
query and omit it from the transform layer **only if every advertised predicate of
that input actually runs in the resolver.** A filter input that advertises more
than it honours (the silent `advertised ≠ honored` contract lie — see the
nested-`allocatables` defect) would make the view drop a filter that never fires.
Hence views depend on the schema-hygiene guideline in
[PRD 073 § Schema-design guideline](073-graphql-function-equivalents.md): focused
filter inputs, shared matcher logic, one test per filter field.

## Preferred design — GraphQL-native views (no expression engine)

This is the **preferred, default design.** Validated against the real dhbw tables
(below), a view decomposes into **three CLOSED, engine-free building blocks** — no
expression engine, no new library, no TS↔Java parity problem. The conditional/string
logic the real views use lives in **nameformats that rapla already evaluates
server-side**, and `filter(...)` compositions are **selection** → GraphQL — so nothing
needs a client engine. (There is **no** expression engine; should a view ever need a
complex composition, it is another rapla Function composition evaluated **server-side** —
see §"If a view ever needs more". CEL was evaluated and dropped.)

The three building blocks:

- **Selection + filter → GraphQL** — `where` ([PRD 059](done/059-graphql-typed-where-predicates.md)), the nested `allocatables`
  filter (shipped 2026-06-19), access selectors ([PRD 069](069-graphql-resource-access-read-api.md)). In the dhbw model
  **rooms, courses and lecturers are referenced *allocatables*, not classification
  attributes**: `Raum`/`Teilraum`/`virtuellerRaum`, `Kurs`/`Teilkurs`/`Kursgruppe`,
  `Person` — split per column by `typeIn`. Rows are **AppointmentBlocks**
  (recurrence expansion) = the flatten unit.
- **Composition cells → server-evaluated fields.** Each column's `defaultValue`
  composition (and the type nameformats) is evaluated **server-side via rapla's existing
  `ParsedText` engine** and exposed as a plain GraphQL field — `displayName` is just the
  field for the `name`/nameformat composition; `times`/`duration` likewise. No client
  engine, no expression string on the wire; the response stays plain typed data.
- **Shaping → presentation, implicit by default.** GraphQL field order = column order,
  alias = column key → localized header, field type → formatting, list → join. Explicit
  directives (`@flatten`/`@column(order:)`/`@groupBy`/`@when`) appear **only** where the
  structure forces it — cross-level flattening that reorders, or the per-day row
  grouping (see the three worked tables).

Aggregation (count/sum/groupBy *collapse*) is **not used by any of the three standard
tables** — they flatten/split/group rows but never collapse them. A GraphQL
aggregate-field convention is a *possible future* capability, off the critical path.

Directive kinds: a small **fixed/closed structural** set (`@view`/`@column`/`@flatten`/
`@join`/`@when`/`@groupBy`) — all **optional overrides** over the conventions above, plus
the server-evaluated composition fields generated from the col annotations (same
generation pattern as `ClassificationSdlGenerator`).

### Authoring — the admin writes the GraphQL view directly

**The admin authors the GraphQL query directly** (via a query builder / editor) — there is
**no intermediate spec language** and no spec→query compilation. The view *is* the GraphQL
query plus a few presentation directives; the worked queries below are exactly what is
authored and stored (no hidden YAML layer).

This is **why** the queries are deliberately kept **flat, block-rooted and
directive-light** — so they stay **readable and directly editable** by an admin. The
simplicity *is* the authoring affordance: there's no need for an abstraction layer because
the GraphQL itself is the editable artefact (incl. inline filters and `compute(...)` cells,
see worked example 4).

### Declaration format & the two paths (decided 2026-06-20)

A view **is a named GraphQL operation + a `@view` directive** carrying the envelope:

```graphql
query appointments @view(title: "Termine", variant: DISPLAY) {   # operation name = view key
  appointmentBlocks(filter: $filter) {                           # root field = legacy contentDefinition
    name:  reservation { displayName }
    start  @sort(ASC, priority: 1)                               # per-field sort (canonical, multi-key via priority)
    raum:  allocatables(filter:{ typeIn:[Raum,Teilraum] }) { displayName } @join(separator: ", ")
    day:   start @bucket(DAY) @group @hidden
  }
}
```

| Envelope part | where | notes |
|---|---|---|
| **key** | operation name | identifies the stored view |
| **content-root** | the root field | one row = one root object |
| **columns + order** | the (flat) selection | field order = column order |
| **title** | `@view(title:)` — **literal *or* composition** | composition runs server-side at the VIEW_TITLE level (`allocatables`/`timeIntervall`/`selectedDate`); `ParsedText` passes literals through |
| **sort** | per-field **`@sort(ASC\|DESC, priority:n)`** (canonical); `@view(sort:)` optional shorthand | legacy `sortingstring` maps to per-field |
| **variant default** | `@view(variant:)`; per-field override via `name(variant:)` | both — view-wide default + per-column override |
| **window seed** | `@view(fromAnchor:, fromOffset:, toAnchor:, toOffset:, unit:)` | default date range for a `$filter: ReservationFilter!` view; anchors `TODAY\|WEEK_START\|MONTH_START`, offsets in `unit` `DAYS\|WEEKS\|MONTHS`; omitted ⇒ `TODAY −7…+7`. Plus `rowLabel`/`groupLabel`/`renderModes`. |

**Two paths, two needs:**

1. **Consumer / render (the default)** — the client sends only **view name + input variables**
   (`executeView(name, variables)`); the server holds the stored query and executes it with the
   variables + execution context. The client **never holds the query text**. Benefits: small
   payload, and — load-bearing — **only admin-vetted stored views run** (trusted/persisted
   documents; no arbitrary-query surface for rendering — a §12 win). The export/in-process path
   (§"Server-side rendering") is the **same** call: reference by name + variables + context.
2. **Author / editor** — needs the full `@view` GraphQL text (the query builder) + `saveView`
   validation. The only place the text is handled.

**Response = `data` + `extensions.view`.** Because the generic SPA renderer works from name+input
(it doesn't know the query), the server returns the resolved render metadata in `extensions.view`
— **shipped on every view query, which is acceptable**:

```json
{ "data": { "appointmentBlocks": [ … ] },
  "extensions": { "view": {
    "key": "appointments",
    "title": "Termine",                                  // resolved (TITLE-composition)
    "columns": [
      { "alias": "name",  "header": "Name",   "type": "String" },
      { "alias": "start", "header": "Beginn", "type": "DateTime", "sort": "ASC" },
      { "alias": "raum",  "header": "Raum",   "type": "[String]", "join": ", " },
      { "alias": "day",   "hidden": true, "group": "DAY" } ] } } }
```

→ Name + input in; data + render-meta out. (Operation directives aren't returned in standard
GraphQL responses, so the *resolved* envelope rides `extensions.view` — not `data` — keeping
`data` clean.)

### Two layers, orthogonal axes — `data` vs `extensions.view` (decided 2026-06-20)

The API is **public GraphQL**, and we layer **SPA-view extensions** on top. The split:

- **Public contract → `data`/schema.** Entities, fields, filters, and all **field compositions**
  (`name(variant:)`, `compute`, `duration`, `times`, `note`) resolve **into `data`** — they are real
  schema fields, usable by any client.
- **Client-specific render chrome → `extensions.view`.** The resolved **title** (a VIEW_TITLE
  composition), **column** render-hints, and **page** state ride `extensions.view` — *exactly* what
  GraphQL `extensions` is for (non-contract, client metadata; public clients ignore it). My earlier
  "public API ⇒ never extensions" was too absolute: contract data → `data`; client render-meta →
  `extensions` is the textbook split.

**`extensions.view` ⟺ the `@view` directive.** It is emitted **iff** the executed query carries
`@view`; field directives (`@column`/`@hidden`/`@join`/`@group`) **feed** it but don't trigger it
alone. No `@view` → plain `data`, no overhead.

**Two orthogonal axes** (don't conflate):
1. **Transport** — raw query text vs **name+input** (server holds a persisted/trusted document).
   About payload/trust/storage; independent of render-meta.
2. **Render-meta** — `@view` present or not. Independent of transport.

So `extensions.view` depends only on `@view` (whoever holds the query), not on the transport. In
practice it pairs with the stored-view path (the consumer runs an admin view it didn't author, so it
*needs* the meta), but a raw client may add `@view` too.

### Sort & pagination — inputs, server-applied (decided 2026-06-20)

**Sort is an *input*, not a declaration.** Legacy stored `sortingstring` in the (legacy) CalendarModel
`optionMap` — i.e. it is user/calendar state, parallel to the filter. So it maps to a **`$sort`
variable** (`[{field, dir}]`, list order = multi-key priority), **server-applied** and **deterministic**
(stable `id` tiebreaker — required for offset pagination). **No config default exists in rapla** (the
`TableConfig` fallback is commented out). Three default sources, layered:

1. **Attribute `sorting` annotation** (`ascending`/`descending`, `KEY_SORTING`) → the **type-level
   default order of allocatables**, applied **server-side today** via `SortedClassifiableComparator`
   (`CalendarModelImpl`/`RaplaBuilder`/HTML export/tree). Views over allocatables must respect it.
2. **Admin view-seed** — the admin may seed an initial `$sort` for the rows (e.g. `start`; per-day =
   `day` then `time`).
3. **User override** — header click sets `$sort`, re-query.

**Collation: locale-aware Collator** (rapla already uses `NamedComparator`'s `Collator` for entity
sorting) — consistent + correct for German `ä/ö/ü`; a deliberate improvement over the legacy table's
ASCII `String.CASE_INSENSITIVE_ORDER`.

**Pagination — offset/limit, flat data, page-state in `extensions.view.page`:**
- Offset/limit via the filter input; **root stays a flat list** (no Relay `nodes`/connection wrapper —
  that would change the public shape and break the flat-row model). Cursor/Relay **deferred** (only if
  strict concurrency stability is ever needed).
- `extensions.view.page = { hasNext, offset, limit, total? }`. **`hasNext` cheap** (`limit+1` probe);
  **`total` opt-in** (expensive). One mechanism serves **next/prev *and* infinite-scroll** (client UX
  choice). **Export passes no limit** → full set (same view, different input).
- **Grouping × pagination:** row-pagination; a group may **split across a page** (presentational);
  server-side group-pagination deferred.
- **Invariants:** a server **result-cap** (DoS, configurable default) even without a client limit;
  the deterministic sort tiebreaker (above).
- Offset-drift under concurrent mutation is acceptable for date-windowed views (cursor only if strict).

### Worked queries — the three real dhbw tables

The three real views are `org.rapla.plugin.tableview.{events, appointments,
appointments_per_day}`. Their column sets + the GraphQL reproduction are the **capability
benchmark** in [docs/architecture/tableview-and-graphql-views.md](../architecture/tableview-and-graphql-views.md)
(ground truth, not invented — the legacy configs become legacy with the SPA; the doc
proves GraphQL reproduces them). Summary:

| View | `contentDefinition` (rows) | columns (in order) |
|---|---|---|
| `events` | `{p->events(p)}` — reservations | `Name`, `Beginn`, `zuletzt geändert` *(rapla default)* |
| `appointments` (**dhbw-configured**) | `{p->appointmentBlocks(p)}` — blocks | `Name`, `Beginn`, `Ende`, `Kurs`, `Person`, `Raum`, `Dauer` |
| `appointments_per_day` | `{p->appointmentBlocks(p)}` — blocks, grouped per day | `Zeiten`, `Name`, `Ressourcen`, `Personen` *(rapla default)* |

The `appointments` row is the dhbw deployment's **configured** Termine view (verified
from its Tableview-Plugin dialog): the generic `resources` column is split into **`Kurs`**
and **`Raum`** (non-person allocatables by type), **`Person`** is the person allocatables,
and **`Dauer`** (eventtimecalculator duration, values like `"2 UE 0 Min"`) is added —
appointments only. Each column maps to a GraphQL construct: selection →
`allocatables(filter:{ typeIn / isPersonEq })`; projection/derivation → a
**server-evaluated field** (rapla's `ParsedText`; `displayName` = the `name` composition,
`Dauer` = the duration composition). This per-type column split is exactly the
nested-`allocatables` filter use case.

**Each view roots its query at the level its `contentDefinition` names** — `reservations`
for `events`, **`appointmentBlocks`** for `appointments` / `appointments_per_day`. Rooted
correctly, **one row = one root object**, so the query is **flat**: field order = column
order, alias = column key → localized header, type drives formatting, lists join by
convention. **No `@flatten` / `@column(order:)` needed** — those were only an artifact of
rooting block-row tables at `reservations`. The only structural directive left is
`@groupBy` (per-day sectioning). Filter homes (above) still apply: the **model** filter is
the root's `$filter` variable; per-column **annotation** `allocatables(filter:)` stays
inline.

**Table 1 — `events` · root `reservations` · columns Name, Beginn, zuletzt geändert · flat → 0 directives:**
```graphql
query Termine_events($filter: ReservationFilter!) {
  reservations(filter: $filter) {      # rows = reservations ({p->events(p)}); $filter ← CalendarModel
    name:  displayName                  # {p->name(p)}
    start: firstDate                    # {p->start(p)}        (reservation's first date)
    lastchanged: lastModifiedAt         # {p->lastchanged(p)}
  }
}
```
Three reservation-level fields → flat: order implicit, header from alias, datetime by
convention. **No `end`, no resources/persons** — exactly the real config.

*Output* (dummy data, §17) → *GUI* (one row per reservation):
```json
{ "data": { "reservations": [
  { "name": "Programmieren II", "start": "2026-06-22T10:00:00", "lastchanged": "2026-06-10T14:22:00Z" } ] } }
```
| Name | Beginn | Geändert |
|---|---|---|
| Programmieren II | 22.06.2026 10:00 | 10.06.2026 14:22 |

**Table 2 — `appointments` (dhbw Termine) · root `appointmentBlocks` · headers Name, Beginn, Ende, Kurs, Person, Raum, Dauer · flat → 0 reorder directives:**
```graphql
query Termine_appointments($filter: ReservationFilter!) {
  appointmentBlocks(filter: $filter) {   # rows = blocks ({p->appointmentBlocks(p)}); $filter ← CalendarModel (incl. from/to)
    name:   reservation { displayName }                                             # Name    {p->name(p)}
    start                                                                           # Beginn  {p->start(p)}
    end                                                                             # Ende    {p->end(p)}
    kurs:   allocatables(filter:{ typeIn:[Kurs,Teilkurs,Kursgruppe] })     { displayName }   # Kurs   (annotation → inline)
    person: allocatables(filter:{ isPersonEq:true })                                { displayName }   # Person
    raum:   allocatables(filter:{ typeIn:[Raum,Teilraum,virtuellerRaum] }) { displayName }   # Raum
    duration                                                                        # Dauer   {p->…:duration(p)}
  }
}
```
Rooted at the **blocks** (matching `{p->appointmentBlocks(p)}`), one row = one block →
**flat**: field order = column order, so **no `@flatten`, no `@column(order:)`**. Headers
`Name/Beginn/Ende/Kurs/Person/Raum/Dauer` verified from the deployment screenshot (optional
`@column(header:)` overrides the localized label). `Kurs`/`Person`/`Raum` are **lists**
(`allocatables(filter:)` → `[Allocatable!]!`): the cell **joins** the `displayName`s with
`, ` **by convention — no directive**; an explicit **`@join(separator: "; ")`** on a list
field overrides the separator *on demand*. The legacy generic `resources` column is
**split by allocatable type** to get separate `Kurs` + `Raum`. (The archived snapshot has
only the generic `resources`/`persons` `defaultValue`s; the live deployment splits them.)

*Output* (one block per row; `Kurs`/`Person`/`Raum` are lists) → *GUI* (list cells joined):
```json
{ "data": { "appointmentBlocks": [
  { "name": { "displayName": "Programmieren II" }, "start": "2026-06-15T08:00:00", "end": "2026-06-15T09:30:00",
    "kurs":   [ { "displayName": "FN-TEK23" }, { "displayName": "FN-TEN23" } ],
    "person": [ { "displayName": "Prof. X" }, { "displayName": "Dr. A" } ],
    "raum":   [ { "displayName": "H004 Seminarraum" }, { "displayName": "H005 Seminarraum" } ],
    "duration": "2 UE 0 Min" } ] } }
```
| Name | Beginn | Ende | Kurs | Person | Raum | Dauer |
|---|---|---|---|---|---|---|
| Programmieren II | 15.06.2026 08:00 | 15.06.2026 09:30 | FN-TEK23, FN-TEN23 | Prof. X, Dr. A | H004 Seminarraum, H005 Seminarraum | 2 UE 0 Min |

**Table 3 — `appointments_per_day` · root `appointmentBlocks` · flat, each row a block + a hidden `day` group/sort column:**
Same flat block rows as `appointments`, plus a **`day` column derived from `start`** used
to **sort + group** the rows but **not displayed**. Shown columns: Zeiten, Name,
Ressourcen, Personen.
```graphql
query Termine_perDay($filter: ReservationFilter!) {
  appointmentBlocks(filter: $filter) {
    day: start @bucket(DAY) @group @hidden     # DATE bucket of start (type Date, e.g. 2026-06-22) — group + sort key, NOT displayed
    times                                       # Zeiten      {p->times(p)}
    name:      reservation { displayName }      # Name        {p->name(p)}
    resources: allocatables(filter:{ isPersonEq:false }) { displayName }   # Ressourcen
    persons:   allocatables(filter:{ isPersonEq:true })  { displayName }   # Personen
  }
}
```
**Flat** — each row is one block with all its columns. `day` is the **Date** bucket of
`start` (a `Date` like `2026-06-22`, **not** the datetime) — a **hidden grouping/sort
column**: it orders the rows and clusters them by day but is never shown. No aggregation
(no count/sum/collapse), no `@flatten`, no `@column(order:)`.

*Output* (flat block rows + hidden `day`) → *GUI* (rows sorted+grouped by `day`; `day` not shown):
```json
{ "data": { "appointmentBlocks": [
  { "day": "2026-06-22", "times": "10:00–11:30", "name": { "displayName": "Programmieren II" },
    "resources": [ { "displayName": "A474 Hörsaal" } ], "persons": [ { "displayName": "Prof. X" } ] },
  { "day": "2026-06-22", "times": "14:00–15:30", "name": { "displayName": "Datenbanken" },
    "resources": [ { "displayName": "B12" } ], "persons": [ { "displayName": "Dr. A" } ] },
  { "day": "2026-06-23", "times": "09:00–10:30", "name": { "displayName": "Software Engineering" },
    "resources": [ { "displayName": "A474 Hörsaal" } ], "persons": [ { "displayName": "Prof. X" } ] } ] } }
```
| *(Tag)* | Zeiten | Name | Ressourcen | Personen |
|---|---|---|---|---|
| **▸ 22.06.2026** | 10:00–11:30 | Programmieren II | A474 Hörsaal | Prof. X |
|  | 14:00–15:30 | Datenbanken | B12 | Dr. A |
| **▸ 23.06.2026** | 09:00–10:30 | Software Engineering | A474 Hörsaal | Prof. X |

**None of the three real tables aggregate.** `@groupBy` is presentation sectioning,
distinct from aggregation (count/sum/collapse). A GraphQL aggregate-field convention
remains a *possible future* capability for explicit aggregate views, **not** used by the
standard dhbw tables.

**The rule, on the real tables:**

| View | query root | columns | directives needed |
|---|---|---|---|
| `events` | `reservations` | Name, Beginn, zuletzt geändert | **none** (flat) |
| `appointments` (dhbw) | `appointmentBlocks` | Name, Beginn, Ende, Kurs, Person, Raum, Dauer | **none** (flat) |
| `appointments_per_day` | `appointmentBlocks` | Zeiten, Name, Ressourcen, Personen (+ hidden `day`) | hidden `day` column: **`@group(by:DAY)` + `@hidden`** |

So: **root each view at the level its `contentDefinition` names, and every table is
flat** — field order = column order, no `@flatten`, no `@column(order:)`. The *only*
structural directive across all three is `@groupBy` for the per-day sectioning. (Rooting
block-row tables at `reservations` was what previously forced `@flatten`/`@column(order:)`
— an artifact, now gone.)

### Worked example 4 — `Seminarplanung` (wochenplan / yoga domain) · the deliberate op-set case

The three dhbw tables above need **no** op-set. This fourth example — from the *other*
real dataset (`wochenplan.xml`, a yoga/seminar-planning deployment) — shows where the
op-set **is** used: a **view-level computed column** and a **type-level marker-derived
field**. Compositions are the real wochenplan ones; dummy persons (§17).

```graphql
query Seminarplanung($filter: ReservationFilter!) {
  appointmentBlocks(filter: $filter) {
    seminar:    reservation { displayName }                # type-level naming: {name}: "{title}" - {hinweis}
    zeitspanne: compute("concat(substring(times,0,5),'--',substring(times,8,13))")   # VIEW-LEVEL op-set column
    leitung:    allocatables(filter:{ isPersonEq:true }) { planningName }             # type-level DERIVED field (marker chain)
  }
}
```
- **`leitung.planningName`** — the **meaningful** op-set case: a **type-level derived
  field** with a **marker chain** (`im_haus → " # "`, `dispo-modus="anfrage" → "*"`,
  `="selbstaendig" → "**"`, `ausdrucksstarke_yl → "+"`). Real conditional value selection
  (`if`/`equals`/`key` + boolean fields) — status symbols by data; reusable wherever the
  planning name is shown. **This is where the op-set earns its keep.**
- **`zeitspanne`** — the **view-level** one-off column. The *real* `customColumn_1`
  (`concat(substring(times,0,5),"--",substring(times,8,13))`) pulls the start/end out of
  the `times` string and rejoins them **on one line** — most likely to **avoid the line
  breaks** the raw `times` field introduces (ParsedText turns `\n` into real newlines),
  which would otherwise leave **blank lines** in the cell. So it's a real (if fragile,
  fixed-position) workaround that **tames a source field's formatting** — not pure
  cosmetics. The cleaner fix is single-line `start`/`end` fields; the example shows admins
  use the op-set to repair upstream formatting quirks.
- `seminar` = the reservation's display name (type-level field-list/op-set naming).

*Output* → *GUI*:
```json
{ "data": { "appointmentBlocks": [
  { "seminar": { "displayName": "Hatha Basics: \"Grundlagen\" - Wochenende" },
    "zeitspanne": "10:00--11:30",
    "leitung": [ { "planningName": "Ananda B. # *" } ] } ] } }
```
| Seminar | Zeitspanne | Leitung |
|---|---|---|
| Hatha Basics: "Grundlagen" - Wochenende | 10:00--11:30 | Ananda B. # * |

This closes the arc: **tables 1–3 (dhbw) = no op-set; table 4 (yoga) = the deliberate
op-set** in its two homes — **view-level** (`zeitspanne`, one-off) and **type-level
derived field** (`planningName`, reusable). The op-set language + catalog: [PRD 073 §
Composition op-set](073-graphql-function-equivalents.md).

### Validated against the real dhbw tables (data.xml, 2026-06-20)

The three real table views — `org.rapla.plugin.tableview.{events, appointments,
appointments_per_day}` — use eight standard rapla columns (column `defaultValue`
expressions — stored across the two complementary legacy stores: per-type `tablecolumn_*`
annotations + the `tableview.config` preference). The **real** col annotations and their mapping:

| rapla column | col annotation (`defaultValue`) | GraphQL-native mapping | engine? |
|---|---|---|---|
| `name` | `{p->name(p)}` | `displayName` — event-type **nameformat already evaluated server-side** | **no** |
| `start` / `end` | `{p->start(p)}` / `{p->end(p)}` | `start` / `end` + `@format(DATETIME)` | no |
| `times` | `{p->times(p)}` | server-evaluated `times` field (rapla engine) | no |
| `persons` | `{p->filter(resources(p),r->isPerson(r))}` | `allocatables(filter:{ isPersonEq:true })` (join by convention) | no |
| `resources` | `{p->filter(resources(p),r->not(isPerson(r)))}` | `allocatables(filter:{ isPersonEq:false })` (join by convention) | no |
| `duration` | `{p->org.rapla.eventtimecalculator:duration(p)}` | server-evaluated `duration` field; raw `durationMinutes` ([PRD 073](073-graphql-function-equivalents.md) Ph2) for aggregation | no |
| `lastchanged` | `{p->lastchanged(p)}` | `lastModifiedAt` + `@format(DATETIME)` | no |

**Decisive finding — no client engine is needed even for the *real* compositions.**
The event-type nameformats are not trivial — they carry conditionals, predicates,
printf and lambda-filters:
```
Lehrveranstaltung name:  {if(not(status),"*","")} {Name} {Beschreibung} {format("<%s>",appointment:note())}
Pruefung export:         … {filter(event:allocatables, r->or(equals(key(type(r)),"Kurs"),
                                                             equals(key(type(r)),"Teilkurs"),
                                                             equals(key(type(r)),"Kursgruppe")))}
Raum name:               {if(or(equals(substring(Gebaeude,0,3),"MOS"),equals(substring(Gebaeude,0,2),"KA")),
                              concat(Raumnummer," ",Raumname), concat(SekundaereRaumnummer," ",Raumname))}
```
But these `if`/`or`/`equals`/`substring`/`concat`/`format`/`note` expressions live in
**nameformats, which rapla already evaluates server-side** into `displayName` — the
client gets a finished string, so the conditional/string logic **never runs
client-side**. The `filter(...)` expressions are **selection** → GraphQL
`allocatables(filter:{ isPersonEq } / { typeIn:[…] })`. What remains for the table
layer is closed formatting (`@format`/`@join`/`@times`). **The expressive real
compositions are either (a) server-pre-computed nameformats → `displayName`, or (b)
selection → GraphQL filters — neither needs an engine.** This validates the no-engine
verdict on real data, not invented examples.

**`appointments_per_day` is grouping, not aggregation:** the same flat block table as
`appointments` (columns `Zeiten`, `Name`, `Ressourcen`, `Personen`) plus a **hidden `day`
column** (`day: start @bucket(DAY) @group @hidden` — a **Date**, e.g. `2026-06-22`) that sorts + groups the rows. No counting,
no summing, no row collapse, no split — pure presentation. **Not** an aggregate field.
None of the three standard tables aggregate.

### Why this is attractive
- **No expression engine anywhere** → no CEL lib, no JS port, no TS↔Java parity
  corpus, no `eval`/XSS-via-expression surface. Directly serves the security-first stance.
- **Result on the wire stays valid GraphQL** (data response unchanged; directives inert
  server-side; aggregate is typed data).
- **Reuses the nested-`allocatables` filter** for its intended purpose (split
  rooms/courses/lecturers per column in one query).
- **Admins keep authoring compositions in rapla syntax**; generation emits the closed directives.

### Feasibility + open verdict
- **Confirmed:** `ParsedText` already compiles an expression to a **walkable `Function`
  tree** (name/namespace/args; `getRepresentation` proves recursive traversal) — so a
  composition can be compiled to a bounded op-tree. Spike
  `rapla-core/.../viewspec/CompositionDirectiveSpikeTest` proves a small **closed** op
  registry evaluates the two real compositions and **rejects unknown functions** (the
  no-eval property), and generates the closed directive from each.
- **No aggregation needed** by the three standard tables (they flatten/split/group,
  never collapse) — so even the aggregate-field convention is off the critical path.
- **Verdict:** the three real views need **no** free cross-field arithmetic/predicates
  beyond the closed blocks → this GraphQL-native path is the **preferred default**. Any
  future complex composition is evaluated **server-side by rapla's own engine** (§"If a
  view ever needs more") — no CEL, no client engine.

### If a view ever needs more — rapla's own engine, server-side (no CEL)

There is **no CEL**, and no new expression engine. Should a future view need a free
cross-field expression the three closed blocks can't express, the answer is **another
rapla Function composition, evaluated server-side via the existing `ParsedText`
engine** — the *same* mechanism the preferred design already uses for `displayName` /
`times` / `duration`. rapla's engine is itself a bounded, non-`eval`, non-Turing
composition language (`if`/`concat`/`substring`/`filter`/lambda); evaluating it
server-side covers any complex composition without a client runtime.

CEL (and a Vega/AC-style client transform pipeline) was evaluated and **dropped**: its
only unique value was client-side / dual-runtime evaluation, which the server-side model
makes moot — it would add a dependency, a new language, a TS↔Java parity corpus, and a
client expression runtime for **zero** added capability over rapla's own engine. The
only thing rapla's server-side engine doesn't give is *client-side* free evaluation
(interactivity over already-loaded data without a roundtrip) — not a current
requirement (charts use Vega-Lite's own client transform); revisit only if a concrete
need appears.

**The composition language (op-set) + its use-case catalog live in
[PRD 073 § Composition op-set](073-graphql-function-equivalents.md).** Real **view-level**
computed-column example (verified in `wochenplan.xml`):
```
customColumn = concat(substring(times, 0, 5), "--", substring(times, 8, 13))
```
— a one-off "time range" column reformatting the `times` string; this is the view-level
op-set case (vs type-level **derived fields** like `displayName` / `Raum.effectiveRoomNumber`,
which are reusable and stay on the type). Placement rule: **reusable → type-level derived
field; one-off → view-level column.**

**Governance + composition fields (converged 2026-06-20 — see [PRD 073](073-graphql-function-equivalents.md) § Composition
fields):** rapla Functions are **kept** as the (bounded, server-side) composition engine;
no new engine. The gap GraphQL closes is the **composition-field bridge** (admin rapla
composition → server-evaluated GraphQL field):
- **view-level computed columns** — written **directly in the admin's GraphQL query** as a
  `compute(...)`-style field over the selected raw fields (see worked example 4:
  `zeitspanne: compute("concat(substring(times,…))")`) → **admin**, no schema change
  (server-eval over the query result). *Admins compose freely here, in the GraphQL itself.*
- **type-level display fields** `displayName`/`exportName`/`planningName`/`exportDescription`
  → generated from the four `nameformat*` annotations (**admin** edits the value); the
  variants **fall back to `displayName`** when unset (all four always resolve).
- **new reusable type-level Classification fields** → **plugin** extension point (schema
  stability, not security).

**Storage (corrected):** the legacy TableView config is **always a combination of two
complementary stores** — per-type **`tablecolumn_*` annotations** (the column
*compositions*) **+** the **`tableview.config`** preference (the *view configuration* —
which views, which columns each shows, ordering/sorting). Both are present in both datasets
(verified). They complement, not replace, each other; both predate the GraphQL views, which
replace the combination (greenfield — no forced migration; legacy Swing/export keep the
per-type nameformats).

## Storage & lifecycle — persisted-query text + revalidate-on-change

A saved view **is** its GraphQL query (+ a few presentation directives). The query embeds
deployment keys in three positions — field selection `{ Raumnummer }` (attribute key),
`typeIn:[Raum]` literal (type key), inline fragment `... on RaumClassification` (type
key) — while structural names (`appointmentBlocks`, `reservation`, `allocatables`, `filter`,
`displayName`) and admin aliases (`kurs:`, `name:`) are schema-stable / admin-chosen.

### Storage = persisted-query text (the standard; no invented format)

The **standard** way to persist a GraphQL operation is its **text** — a *persisted query*
(graphql-java round-trips it with `Parser.parse` ↔ `AstPrinter.printAst`). **GraphQL has no
stable-field-id binding** — fields and types are referenced **by name**, by spec. So there is
**no standard "bound" form to store, and we do not invent one** (an earlier `idref`-AST draft
was exactly such an invention — dropped).

Views are stored as **query text in a Preferences/`RaplaMap` entry**, the same home
`tableview.config` uses today (which is likewise plain strings). The **default views**
(`events` / `appointments` / `appointments_per_day`) ship as **query-text constants in code**
(as `TableConfig` ships default columns today) and are seeded into the store on first use —
admin-editable from then on.

### Rename / delete of a type / category → revalidate-and-mark all views

GraphQL being name-based, a rename or delete **breaks the stored text** (the old name no
longer resolves). We do **not** auto-migrate or auto-bind. Instead we use rapla's **existing**
schema-rebuild hook to re-check every view **eagerly, in the same save**:

1. A type / attribute / category edit is saved → `UpdateEvent` → **`HotSwappableGraphQlSource.rebuild()`**
   regenerates the schema (the per-DynamicType Classification types; SDL-hash skip on no-op).
   *This hook already exists* ([PRD 035](done/035-graphql-foundations.md) §5b: "admin add/remove/rename of children triggers
   schema rebuild").
2. **Immediately after a successful rebuild** (schema must be current first), iterate **all
   stored views** and validate each against the **new** `GraphQLSchema` with graphql-java's
   `Validator` — the same check `saveView` runs, re-run. Renamed/deleted field/type/category →
   that view becomes **invalid**.
3. Persist a per-view **mark** (`valid` + `invalidReason` / error list) on each view.

This is a **write path** (the type save), so writing the marks back is legitimate (§16) — and
exactly parallel to `addChangedDynamicTypeDependant`, which already pulls dependent entities
(reservations/allocatables) into the same `UpdateEvent`. Placement is **rapla-app** (where the
`GraphQLSchema` lives — *not* rapla-core `commitChange`, which has no schema), in the same
listener that calls `rebuild()`. Cost is negligible (a handful of views, cheap document
validation, hash-skipped when the schema didn't change).

### Admin fixes invalid views (visible, not magic)

An invalid view **keeps its text**; execution is **refused with the error list**; the admin UI
lists it as **"needs update"**. The admin adjusts the query. Nothing is auto-pruned,
auto-emptied, or guessed — which matches rapla's existing string-config behaviour
(`tableview.config` doesn't auto-migrate either) and is **GraphQL-conformant** (name-based).
The deliberate trade: a rename does **not** self-heal — but a human edit to the dependent
views is usually wanted at rename time anyway, and the breakage is **surfaced immediately**
(eager mark at save), not discovered later at run time.

### Why not auto-migrate (the road not taken)

rapla's own `ParsedText` shows why binding wouldn't even cover the cases: it self-heals **only
own-type bare-name renames** (`keyChanged` re-emit by id), **silently empties on delete**
(`getRepresentation`→`""`), and **never migrates cross-type references** (the `attribute("k")`
quoted-string escape is eval-time, name-based). `DynamicTypeImpl` isn't even a
`DynamicTypeDependant`, so the `commitChange`/`commitRemove` walk never touches annotations.
A view is **cross-type by nature** (block → reservation → allocatables of several types), so
any binding scheme would have to out-do `ParsedText` *and* invent a non-standard stored
format. Revalidate-and-mark gets correctness with **zero new format and zero new mechanism** —
just the existing `rebuild()` hook plus the existing `Validator`.

> **Embedded `compute(...)` cells** are revalidated the same way: their `ParsedText` is parsed
> + type-checked against the catalog at `saveView`, and a deleted attribute inside a `compute`
> makes the whole view invalid at the next rebuild (flagged for the admin) rather than silently
> emptying that one cell — closing the annotation failure mode at the view layer.

### Validation at save (two layers, parity with `setAnnotation`)

`saveView` validates **before persisting** — so no invalid query/EL ever reaches eval-time
(today's `DynamicTypeImpl.setAnnotation` already validates the composition at save; the view
path must reach the same bar):

1. **GraphQL document vs schema** — graphql-java `Validator`: fields exist, types match,
   fragments valid, arguments well-typed. Catches a selection on a deleted/renamed field.
2. **Each `compute(...)` EL** — `ParsedText.init` (brackets, parens, function exists,
   attribute exists, arity via `assertArgs`) **+** the return-/arg-type check against the
   curated function catalog (see [PRD 073](073-graphql-function-equivalents.md) — the one new type layer).

The validation walks the query against the schema's own type structure (block → reservation →
allocatable types), so cross-type selections are checked in their own scope for free — no
separate per-selection binding context is needed (the schema already carries it). Each
`compute(...)` EL parses against the type its column is rooted on.

## View catalog & admin authoring

### Pre-made views (BUILTIN)

Rapla ships three canonical views as query-text constants in code — the same pattern as
`TableConfig` default columns today. They are available on every deployment regardless of
Preferences state. Source tag `BUILTIN` distinguishes them from admin-saved (`CUSTOM`) views.

| Name | Root | Columns | Notes |
|---|---|---|---|
| `Termine_events` | `reservations` | Name, Beginn, zuletzt geändert | rapla default |
| `Termine_appointments` | `appointmentBlocks` | Name, Beginn, Ende, Kurs, Person, Raum, Dauer | dhbw-configured default |
| `Termine_perDay` | `appointmentBlocks` | Zeiten, Name, Ressourcen, Personen (+ hidden `day`) | rapla default |

The query texts for these views are the **worked queries in §"Worked queries"** — they are the
canonical form, not an approximation. A deployment-specific view (e.g. a dhbw-specific Termine
split) is created by an admin forking `Termine_appointments` and saving it under a custom name.

### Admin authoring — GraphiQL + save/load (locked decision #8)

The shipped `/api/graphiql` (schema-aware, variable autocompletion) is the authoring
surface. No separate view-editor is built. The admin workflow:

1. Open GraphiQL → **Load view** (dropdown over `listViews` result, populates editor).
2. Edit the query (schema feedback inline — unknown fields, type mismatches highlighted).
3. **Save view** (name prompt → calls `saveView`; two-layer validation runs server-side; error
   displayed inline on failure).

GraphiQL gains two thin toolbar extensions — a **Load** selector and a **Save** button backed
by the mutations below. Both are optional progressive enhancements over the base GraphiQL build
(the mutations are usable via any GraphQL client even without the toolbar).

### Admin save/delete/list API (contract — persistence mechanics in [PRD 077](077-calendar-model-graphql.md))

```graphql
type Mutation {
  saveView(name: String!, query: String!,
           public: Boolean = false, groups: [String!] = []): SaveViewResult!
  deleteView(name: String!): Boolean!   # CUSTOM only; BUILTIN → SaveViewResult error
}

type Query {
  listViews:               [ViewMeta!]!
  getViewQuery(name: String!): String   # null if not found
}

type SaveViewResult {
  ok:            Boolean!
  invalidReason: [String!]   # non-empty on validation failure OR on BUILTIN-name collision
}

type ViewMeta {
  name:          String!
  title:         String       # resolved from @view(title:), null if not parseable
  source:        ViewSource!  # BUILTIN | CUSTOM
  valid:         Boolean!
  invalidReason: [String!]
  public:        Boolean!     # true → all authenticated users see this view
  groups:        [String!]!   # group keys whose members see this view
}

enum ViewSource { BUILTIN CUSTOM }
```

**Name collision rules (locked 2026-06-21):**
- `saveView` with a **BUILTIN name** → rejected; `ok: false`, `invalidReason: ["Cannot overwrite
  a built-in view — fork it under a new name"]`. Disabling individual BUILTINs is a future
  capability (Phase N), not in Phase 1.
- `saveView` with an **existing CUSTOM name** → **silent overwrite** (the API always overwrites).
  The GraphiQL "Save" toolbar action (re-saving the currently loaded view) calls this path
  directly. The "Save as…" toolbar action checks `listViews` first and shows a confirmation
  dialog if the name is taken — the warning lives in the UI, not the API.

**View not found (locked 2026-06-21):** executing a named operation whose view doesn't exist
returns HTTP 200 with `errors: [{ message: "View 'X' not found", extensions: { code:
"VIEW_NOT_FOUND" } }]` — GraphQL convention (never HTTP 404 on `POST /api/graphql`).

**Visibility (locked 2026-06-21):** each CUSTOM view carries `public` + `groups`.
- `public: true` → every authenticated user sees it in `listViews` and can execute it.
- `groups: ["dhbw-ka"]` → only members of listed groups (plus admins) see and execute it.
- Both false/empty → admin-only (visible only to `isAdmin` users).
- `listViews` returns only views the calling user is entitled to see; admins see all.

Access control: only `isAdmin` users may call `saveView`/`deleteView` (Phase 1). Group-admin
scope (a group-admin may manage views visible to their group) is Phase 4 — same authoring
scope entry in §Plan.

### Invalid view UX (locked 2026-06-21)

- **SPA** — invalid views appear in `/app/views` marked as broken (e.g. `⚠ BrokenView`).
  Navigating to `/app/views/BrokenView` shows the `invalidReason` list instead of the table,
  with an "Edit in GraphiQL" link for admins. Non-admin users see the broken state but have no
  fix action.
- **GraphiQL toolbar** — the **Load view** dropdown includes broken views with the same `⚠`
  marker. Loading one populates the editor with the stored query text and displays
  `invalidReason` inline — the admin edits and re-saves. This is the fix surface.

### Model-change lifecycle — addendum to §"Storage & lifecycle"

The revalidate-and-mark pass (§"Rename / delete of a type / category") applies to **CUSTOM**
views only. BUILTIN views are exempt — they are maintained alongside the schema in code and
never written to Preferences.

Fork flow (the canonical path for a deployment to customise a built-in view):
1. Admin calls `getViewQuery("Termine_appointments")` → receives the canonical query text.
2. Edits in GraphiQL (type-specific filters, additional columns, locale headers).
3. Calls `saveView("MeineTermine", editedQuery)` → stored as CUSTOM, subject to
   revalidate-on-change from here on.

## View loading — execution transport (locked 2026-06-21)

### Named-operation transport (trusted document)

A stored view is executed by sending its name as `operationName` with **no `query` field** —
the standard persisted / trusted-document pattern:

```
POST /api/graphql
{ "operationName": "Termine_appointments",
  "variables": { "filter": { "from": "2026-06-23T00:00:00", "to": "2026-06-29T23:59:59" } } }
```

The server intercepts requests where `operationName` is set and `query` is absent, looks up
the view (BUILTIN catalog first, then CUSTOM Preferences store), and executes the stored query
text. The client **never holds the query text** (locked decision path 1). When both
`operationName` and `query` are present it is a regular client-supplied operation (GraphiQL
authoring) — no conflict.

**Lookup order and error (locked 2026-06-21):** BUILTIN catalog checked first, then CUSTOM
store. CUSTOM views cannot shadow a BUILTIN name (`saveView` rejects it). If the name is not
found in either: HTTP 200 + `errors: [{ message: "View 'X' not found", extensions: { code:
"VIEW_NOT_FOUND" } }]` — GraphQL convention, never HTTP 404.

### Server-side variable defaults

> **Superseded 2026-07-11** by [§ Window and inputs directives](#window-and-inputs-directives-decided-2026-07-12). The Monday-week merge below is replaced by the render-mode-intrinsic default plus a server-resolved `extensions.view.window`. Kept for history.

`ReservationFilter.from/to` are `LocalDateTime!` (required). GraphQL validates variables
before any resolver runs so the server cannot silently fill a missing required field on the
normal `/api/graphql` path — but the named-operation path intercepts before validation. The
server merges defaults into the variable map for **known optional-in-practice fields** before
building `ExecutionInput`:

| Missing variable | Server default |
|---|---|
| `filter.from` | start of current ISO week (`LocalDate.now().with(DayOfWeek.MONDAY).atStartOfDay()`) |
| `filter.to` | end of same week (Monday + 7 days) |

Only the above two are merged; all other absent variables surface as normal GraphQL validation
errors. This resolves [PRD 078](078-spa-graphql-view-renderer.md)'s open question (server-merge, option 1).

### `extensions.view.inputs` — input metadata for the SPA

> **Superseded 2026-07-11** by [§ Window and inputs directives](#window-and-inputs-directives-decided-2026-07-12). `inputs` is removed as a wire concept and the client-side anchor resolver (`resolveAnchorOffset`/`resolveWindowFromInputs`) is deleted: the window is server-resolved into `extensions.view.window`, and explicit inputs are declared with `@param`. Kept for history.

Because the SPA never sees the query text, the server reports the view's input-variable
metadata in `extensions.view.inputs` (parallel to `columns`). Shape added to the v1 contract:

```jsonc
"extensions": {
  "view": {
    "inputs": [
      { "name": "filter.from", "control": "DATE_RANGE_START",
        "default": { "anchor": "TODAY", "offset": -14, "unit": "DAYS" } },
      { "name": "filter.to",   "control": "DATE_RANGE_END",
        "default": { "anchor": "TODAY", "offset": 28,  "unit": "DAYS" } }
    ]
  }
}
```

**Default spec — anchor + offset (locked 2026-06-21):** date defaults are never absolute
dates (would go stale). Instead a structured `{ anchor, offsetDays }` that the SPA resolves
client-side at render time:

| Anchor | Resolves to |
|---|---|
| `TODAY` | today at 00:00 |
| `WEEK_START` | Monday of current week at 00:00 |
| `MONTH_START` | first day of current month at 00:00 |

`offset` is a signed integer (negative = past, positive = future, 0 = anchor itself);
`unit` is `DAYS` | `WEEKS` | `MONTHS` — **Phase 1 implements `DAYS` only**; `WEEKS` and
`MONTHS` are reserved for later. The SPA handles one general rule — no closed sentinel list,
no SPA redeploy needed when a view wants a different window.

Examples:
- Current week: `{ anchor: WEEK_START, offset: 0, unit: DAYS }` / `{ anchor: WEEK_START, offset: 6, unit: DAYS }`
- 2 weeks back, 4 forward: `{ anchor: TODAY, offset: -14, unit: DAYS }` / `{ anchor: TODAY, offset: 28, unit: DAYS }`
- This month: `{ anchor: MONTH_START, offset: 0, unit: DAYS }` / `{ anchor: MONTH_START, offset: 30, unit: DAYS }`

Static defaults (sort, limit) are concrete values, not anchor specs.

### Window and inputs directives (decided 2026-07-12)

Supersedes the 2026-06-21 `inputs`/client-anchor design in the two subsections above. Two
observations fixed the shape: the window is the **only** input needing a computed (non-stale,
per-request) default, and every other input is just `name` + `into` (+ `required`). So there
are **two directives, one per kind** — the datetime baggage lives only on `@window`.

```graphql
directive @window(
  into: String         # the filter variable; optional — defaults to the sole ReservationFilter var
  from: WindowAnchor   # { anchor, offset, unit }
  to:   WindowAnchor
) repeatable on QUERY

directive @param(
  name: String!        # public input name — URL key (+ future SPA control id)
  into: String!        # private dotted variable path it fills, e.g. "filter.allocatableIdsIn"
  required: Boolean = false
) repeatable on QUERY

input WindowAnchor {
  anchor: ViewAnchor   # TODAY | WEEK_START | MONTH_START      (existing enum)
  offset: Int          # signed; 0 = the anchor itself
  unit:   ViewDateUnit # DAYS | WEEKS | MONTHS  (DAYS in Phase 1; others reserved)
}
```

**Derived, not declared.** A param's coercion type is **derived from `into`** by walking the
schema from the operation's variable type — `into: "filter.allocatableIdsIn"` ⇒ `[ID!]`; no
restated `type`. Static defaults (string/int/enum) come from the **GraphQL variable's own
default** (`query Suche($q: String = "Seminar", …)`), never a directive.

**The window (`@window`).** The clean replacement for the deleted
`@view(fromAnchor/fromOffset/…)` args: a structured anchor triple (not five flat args),
explicit `into` (not the `isReservationFilter` sniff in `ViewMetaInstrumentation.inputsFrom`),
URL-overridable, server-resolved. `from`/`to` are the URL keys; `offset` is a free signed
integer, so any span — `to: { WEEK_START, 14, DAYS }` is a two-week default, `{ TODAY, 3, DAYS }`
is three days.

- **Neither directive** → the render-mode default window (`table` → `TODAY−7 … +7`, `week` →
  current ISO week, `month` → current month), resolved server-side.
- **`@window` present** → its anchors seed the window; a template `?from=…&to=…` overrides.

**The value inputs (`@param`).** Scope (`room` ← `filter.allocatableIdsIn`), single id
(`eventId`, `required: true`), owner, search (`q` ← `filter.searchText`). Two consumers:

- **SPA (078)** fills each variable from ambient shell state (window / resource-selection /
  owner) **by type** (`buildVariablesByType`, unchanged). `@param` is the explicit form, used
  only to open the URL surface (and, once controls exist, to disambiguate). The SPA never
  enforces `required` — it constructs its own variables.
- **Document validator (097)** coerces each URL value, maps public `name` → private `into`,
  **rejects any query-param not in the `@param` allowlist** (reject-undeclared, document path
  only). *Revised 2026-08-11 ([PRD 097 D8](097-event-html-templates-mustache.md#decisions-locked)):*
  `required` checks the **effective value after the variable merge** (URL, document pin, or —
  preview only — a default derived from the view's example `defaultVariables`); still unfilled →
  a hint page naming the param, not a masked 404. View `defaultVariables` themselves are
  authoring example data and are never merged at runtime (neither here nor on the SPA transport).

**Transport.** The server resolves the window at request time and emits
`extensions.view.window { from, to }`. The SPA seeds its date-nav from `view.window` and no
longer resolves anchors client-side — `resolveAnchorOffset` / `resolveWindowFromInputs` and the
`view-inputs.ts` date logic are deleted. `inputs` is removed as a wire concept.

**Worked — six on Übersicht** (body `reservations(filter: $filter) { name appointments { start end } resources { name } }`):

```graphql
# 1 baseline — mode-default window, SPA type-fills, no URL inputs
query Uebersicht($filter: ReservationFilter!) @view(title: "Übersicht") { …body… }
# 2 room scope
… @param(name: "room", into: "filter.allocatableIdsIn")
# 3 overridable two-week window — one directive
… @window(from: { anchor: WEEK_START, offset: 0, unit: DAYS }, to: { anchor: WEEK_START, offset: 14, unit: DAYS })
# 4 required single id (Leihschein)
query Leihschein($eventId: ID!) @view(title: "Leihschein")
  @param(name: "eventId", into: "eventId", required: true)
{ reservation(id: $eventId) { …fields… } }
# 5 two scopes (second not SPA-fillable until controls land; both URL-addressable)
… @param(name: "rooms", into: "rooms")  @param(name: "equipment", into: "equipment")
# 6 search
… @param(name: "q", into: "filter.searchText")
```

**Deferred — declared as a known shape, not built in v1:**

- **SPA input controls** (`ParamControl`: `RESOURCE_PICKER` / `TEXT` / `DATE_RANGE` / `SELECT`).
  The only audience is exotic multi-input SPA views, and 078's controls are deferred; until then
  a scalar/id with no shell source is simply not SPA-fillable (still works via URL on templates).
  Add when 078 builds controls.
- **Computed identity default** (`CURRENT_USER` for a "my bookings" template's `ownerEq`) — a
  non-breaking future addition; no such template exists yet.
- **Free-span grids.** `@window` sets the *filter* window to any span, and **tables/lists honor
  it today** (they list whatever rows return). **Grid geometry stays mode-fixed for v1** — the
  week grid draws 7 columns (`dayCount=7`) and steps 7 days, the month grid draws its month,
  regardless of a non-standard span (`view-host.component.ts:130`,
  `view-control-strip.component.ts:311`). "Window-follows-geometry" (a week grid drawing
  `ceil(span/7)` rows, step = span) is a later PRD 077/095 render enhancement, not this contract.

**Status — implemented 2026-07-12** (both phases, test-first):

- *Window*: `WindowResolver` (anchor eval + mode default, 15-test tier-1 parity suite),
  `@window`/`WindowAnchor`/`@param` in the schema, `@view` anchor args deleted,
  `ViewVariables` Monday hardcode replaced, `extensions.view.window` emitted,
  SPA seeds from it, `view-inputs.ts` + client anchor resolver deleted.
- *Gate*: `ViewParamDirectives` (parse `@param`/`@window` off the view) +
  `DocumentRenderService.gateParams` — undeclared URL key → 400, public `name` →
  private `into` translation, `from`/`to` accepted iff `@window`, missing `required`
  → the same 404 (§12). List values via repeated keys (`?resource=a&resource=b`),
  never comma-split (`RequestVariables` scar). Tier-3 suite: `DocumentParamGateTest`.
- *Save-time validation* (2026-07-13): `ViewParamDirectives.validate` runs after the standard
  GraphQL validator on every `saveView` — `into` must resolve variable→input-object path in the
  schema, public names unique and plain (no dots), no `from`/`to` shadowing when `@window` is
  declared, `@window` target must carry `from`/`to`. Tier-3: `ViewParamSaveValidationTest`.
- *`into` authoring affordance — **landed 2026-07-13**, in `/graphiql` itself.* Decision #8 above
  stands: no separate view-editor (and [PRD 078](078-spa-graphql-view-renderer.md) Phase 4, which
  proposed one, is **dropped**). The problem: `into` is a **String** argument, so GraphiQL's
  schema-driven validation and completion are structurally blind to it — a bogus path is a
  perfectly valid String, and a typo only surfaced as a failed save. Shipped:
  - `validateView(query)` — dry-runs the exact `saveView` validation, stores nothing, and returns
    each issue **with line/column** (`Directive.getSourceLocation()`; without a position nothing
    can be marked). Errors name the alternatives (`… has no field 'allocatableIdsInX' — available:
    allocatableIdsIn, ownerEq, …`).
  - `intoPaths(query)` — the completion source, enumerated by the **same walk** the validator uses
    (`ViewParamDirectives.resolveIntoPath`), so completion can never suggest something `saveView`
    would reject.
  - `MonacoBridge` in `static/graphiql/index.html` — a headless component using the **public
    `useMonaco()` hook** from `@graphiql/react` to capture GraphiQL's OWN Monaco. (The hook yields
    a store slice `{ actions, monaco, monacoGraphQL }`; the namespace is `.monaco`.) That gives
    real `setModelMarkers` (red line on Validate) and `registerCompletionItemProvider` (Ctrl+Space
    inside `@param(into: "…")`) on the live `graphql` model.

  **Routes that do NOT work — do not retry:** (a) externalising `monaco-editor` via the importmap
  — esm.sh rewrites each package's internal relative imports to its own canonical URLs, so
  `editor.api` reached through the importmap is a *different module instance* than GraphiQL's; the
  json contribution registers `languages.json` on a copy GraphiQL never reads and its variables
  editor dies on `jsonDefaults`; (b) a Monaco global — GraphiQL exposes none; (c) the plugin API —
  side-panels only, no editor providers. Self-hosting GraphiQL (~2.5 MB ≈ 5% of the server
  artifact, plus a JS bundler in this Java repo) was considered and **rejected** — and is
  unnecessary given `useMonaco()`.
- *Still open*: projecting `@param` into `extensions.view` for future SPA controls (deferred
  with `ParamControl`).

### SPA routing — `/app/views/:viewName`

Each view is addressable by name — the route is `/app/views/:viewName`. SPA routing details
(URL param strategy, back/forward navigation, control-state sync) are a client concern →
**[PRD 078](078-spa-graphql-view-renderer.md)**. The server contract here is: `operationName` in the POST body identifies the
view; server defaults fill missing variables. The URL is never parsed server-side.

**SPA transport — two methods, one service ([PRD 078](078-spa-graphql-view-renderer.md)):**

```ts
// Consumer path — named operation; client never holds query text
executeView<T>(viewName: string, variables: Record<string, unknown>): Observable<GqlResponse<T>>
// Authoring path — full query text (GraphiQL toolbar, `saveView` validation preview)
query<T>(document: string, variables: Record<string, unknown>): Observable<GqlResponse<T>>
```

Both go to `POST /api/graphql`; the consumer path sends `{ operationName, variables }` (no
`query`); the authoring path sends `{ query, variables }` as before.

## XSS / injection hardening (load-bearing)

The saved view is **admin-authored, shared, transferred to every client**, and all
entity data is untrusted. **XSS is conditional on execution** — a string is inert
until parsed as HTML or run as code. The job is to guarantee non-execution:

- **Markup execution → output escaping everywhere.** SPA: Angular text
  interpolation (auto-escaped); **never** `[innerHTML]`/`bypassSecurityTrustHtml`;
  component registry is a safe allowlist (no raw-HTML; `link` = `<a>` text +
  validated `https` href). Server HTML: auto-escaping engine, entity-encode every
  cell. CSV: OWASP formula-injection guard (prefix `= + - @`, tab, CR; quote).
- **Code execution → none by construction.** No `eval`/`new Function`; the
  expression language is total + side-effect-free (Vega deny-list at parse time);
  CSP keeps `script-src` without `unsafe-eval`.
- **Plugin ops are the only Turing-complete surface, and developer-only.** Authors
  reference ops by name; ops are compiled in-reactor code. **Never** register an op
  that itself evaluates author strings (the Vega `scale`-CVE lesson); reserve the
  `org.rapla` namespace so a plugin can't shadow core ops.
- **Validate on save + load**: schema-validate GraphQL + transform; enforce AST
  depth/node caps at parse time; reject unknown ops/functions.
- **Permission boundary stays in GraphQL** — the transform runs on already
  `canRead`-filtered data and cannot widen scope (AGENTS.md §12).

### Prior art — why a bounded language, not editable JS

The view is **admin-authored at runtime** — the same risk class as BI / low-code /
dashboard configuration, **not** the GraphQL `@auth`-directive pattern (those
resolvers are *developer*-written, compiled, reviewed — not runtime-editable, so
they're not this risk). Runtime-authored logic splits into two camps, and the
historical record is one-sided:

| Approach | Real-world examples | Outcome |
|---|---|---|
| Runtime-editable **general** scripting | Elasticsearch Groovy scripts; MongoDB `$where`/`mapReduce` JS; Retool `{{ JS }}` transformers; AG-Grid `valueGetter` expression strings (`new Function()`) | RCE CVE / stored-XSS / perpetual sandbox-hardening |
| Runtime-editable **bounded** expression language | Elasticsearch **Painless**; Kubernetes / Envoy **CEL**; spreadsheet formulas | no `eval`, not Turing-complete, no RCE path |

- **Elasticsearch Groovy → Painless is the textbook precedent.** `CVE-2015-1427`:
  user-supplied Groovy in queries bypassed the sandbox via Java reflection → shell
  execution as the ES process. Elastic's fix was **not** a better sandbox but a new
  **bounded language (Painless)** — general scripting was removed. **rapla starts at
  that endpoint:** the only expression language admins touch is rapla's own bounded
  composition DSL, evaluated server-side — no editable JS, no `eval`, ever.
- **Retool shows sandboxing alone doesn't close XSS.** Admin `{{ JS }}` transformers
  run in a sandboxed iframe, yet a transformer that builds an HTML string from
  user-supplied data and renders it is internal stored-XSS to the next operator —
  on a SOC-2/HIPAA-mature platform. The residual risk is the *admin-authored JS
  itself*.
- **AG-Grid `valueGetter` expression strings compile via `new Function()`** — which
  is exactly why AG-Grid is rejected for the render layer (cdk-table instead).

So for admin-authored views there are only two honest options: a bounded language (the
safe column) or sandbox-plus-perpetual-hardening (Retool, which still ships XSS). rapla
is the former by construction — admins author **closed compositions** (rapla DSL,
server-evaluated) + **closed presentation directives**; no author string is ever run as
code or rendered as HTML. (Even CEL would have been unnecessary — rapla's own engine is
already the bounded-language answer.)

## Server-side rendering — in-process GraphQL execution + request context (load-bearing)

Once server-side exports (calendar / table / CSV / iCal) are **generated from GraphQL views**
(the PRD [035](done/035-graphql-foundations.md)/074 direction "exports go through GraphQL too"), the export endpoints must execute
the stored view query **in-process via `GraphQlSource`** — **not** by proxying the public
`POST /api/graphql`. This is load-bearing because the set of server-rendered exports includes the
**unencrypted public exports**, where person-name privacy depends on a server-set context flag the
client must never control.

### Two parameter kinds — kept strictly separate

| Kind | Examples | Source | Client may influence? |
|---|---|---|---|
| **Query variables** | `$filter`, `$from`, `$to` (which calendar, date range) | URL / request | ✅ yes — it's the *selection* |
| **Execution context** | `internal_request`, `user`, `locale` | proxy path / auth | ❌ **no — server-set only** |

```java
// export endpoint (proxy already chose the public vs *_internal URL)
threadContextMap.put("internal_request", pathIsInternal);          // CONTEXT — server-set
ExecutionInput in = ExecutionInput.newExecutionInput()
    .query(storedView.queryText())
    .variables(Map.of("filter", calendarModelFilter, "from", from, "to", to))   // VARIABLES — selection
    .graphQLContext(Map.of("user", user, "locale", locale))                     // CONTEXT — never a variable
    .build();
render(graphQlSource.graphQl().executeAsync(in).join().getData());  // → HTML / CSV / iCal
```

The composition `DataFetcher` reads `internal_request` (& siblings) from `getThreadContextMap()`
into `EvalContext.environment` (the **environment bridge**, [PRD 073](073-graphql-function-equivalents.md) §"Server mechanics") — so
`exportName`'s `env("internal_request")` renders person names on/off. **One** stored view query
serves both internal and public export; the difference is **only** the server-set context.

### §12 guard — the context flag is never a query variable

- `internal_request` is set by the endpoint from the **proxy-chosen URL path** (intranet →
  `*_internal` → `true`; public unencrypted → `false`), **never** from a GraphQL variable or any
  client-supplied input. The public `POST /api/graphql` is a *different* path and is always the
  authenticated/internal channel (§12 `canRead` filters per user there).
- For the **unauthenticated** public export there is **no identity**, so `canRead` can't filter
  per-user — the **server-set `internal_request` is the sole name-privacy gate**. Therefore the
  `*_internal` URLs **must be reachable only via the intranet proxy**, never directly from the
  public internet (deployment invariant the code assumes via `path.startsWith("internal")`).
- A GraphQL-rendered public export that failed to set `internal_request=false` would leak exactly
  the names the legacy HTML/iCal controllers suppress today — so the in-process execution path must
  mirror the controllers' `threadContextMap` handling (`CalendarPageController` /
  `Export2iCalController`).

## Companion use cases (client-only; share the GraphQL spine)

- **Charts → Vega-Lite.** If a view wants a chart, render it client-side with
  Vega-Lite in **interpreter mode** (`vega-interpreter`, AST-walk, no `unsafe-eval`).
  The **same bounded transform produces the rows**; a table renders them as a grid,
  a chart feeds them into an *encoding-only* Vega-Lite spec (one transform, two
  renderers). No JVM needed (charts are client-only). Server-rendered charts (HTML/
  PDF export) would need a Node sidecar — out of scope unless required.
- **Edit forms → Adaptive Cards → mutations.** AC's actual strength (inputs +
  `Action.Submit`) fits the *write* side: an admin-defined form binds existing data
  (GraphQL query + AC templating, client-side) and submits to a **GraphQL mutation**
  (PRDs [056](056-graphql-events-write-api.md)/[057](done/057-graphql-dt-mutations-v1.md)/[061](061-graphql-dt-mutations-v2.md)/[063](063-graphql-allocatables-write-api.md)). No JVM needed (forms are client-rendered; the server only
  runs the mutation, which enforces `canModify`/`canAdmin` per §12/§16). Candidate
  **[PRD 075](075-expression-language-standardization.md)**. Complex types (repeating appointments, allocatable refs) may exceed a
  flat form.

## Dependencies

- **Data-layer gaps — verified against live `rapla-test.dhbw.de` (2026-06-19).** To
  push filtering/computed-values into GraphQL: `Appointment.allocatables` takes **no
  arguments** (so aliased filtered sub-selections aren't expressible); **no
  `durationMinutes`**; **no `typeGroup`** / declared type-groups ([PRD 065](065-graphql-declared-type-groups.md)). Present:
  `AllocatableFilter` (`typeIn`/`isPersonEq` + per-type `where*`,
  [PRD 059](done/059-graphql-typed-where-predicates.md)) on `Query.allocatables` only. Closing these (PRD [073](073-graphql-function-equivalents.md) + [065](065-graphql-declared-type-groups.md)) keeps the
  transform thin.
- **Pagination + prev/next + server-side `aggregate`** are future. Client-side
  aggregation covers non-paginated admin tables; once paginated, full-set totals
  move to a server `aggregate` field (Hasura-style).

## Scope

> **Quick-breakthrough scope (2026-06-20): the SPA *table* views first.** This PRD ships the
> **table** render layer — `@view`, the generated function fields (`name(variant:)`, `compute`,
> `duration`, …), sort, pagination, `extensions.view` for tables, save-time validation, §12. The
> **persistence model** (SavedView / CalendarModel replacement), **view-switching/conversion**, and
> the **week/month calendar render-modes** are **carved out to
> [PRD 077 — Calendar model & saved views over GraphQL](077-calendar-model-graphql.md)** so the
> table win isn't blocked. For the first cut, standard table views may ship as **code-shipped
> defaults** (full saved-view authoring/persistence comes with [PRD 077](077-calendar-model-graphql.md)).

**In (server only):** the GraphQL-native read-**table** view model — the generator that
compiles each col annotation to a server-evaluated composition field (reusing rapla's
`ParsedText`) or a GraphQL filter; GraphQL selection/filter; presentation directives
(`@column`/`@flatten`/`@groupBy`) as optional overrides; **the `@view` directive + the
`extensions.view` render-meta the SPA consumes** (incl. the `inputs` block the SPA infers
controls from); per-user §12 execution; save-time validation; XSS hardening; sort +
pagination (decided). **074 owns the server contract; the Angular consumer is [PRD 078](078-spa-graphql-view-renderer.md).**

**Out:** any client expression engine / CEL / dual-runtime parity (evaluated and dropped
— compositions run server-side); the rapla DSL / Swing-HTML TableView (deprecated, not
migrated); GraphQL mutations / edit forms (companion); charts beyond the client-only
Vega-Lite note; **the SPA render/control layer — GraphQL transport, generic `cdk-table`
renderer, control rendering/inference, component registry, `monaco-graphql` authoring
→ [PRD 078 — SPA GraphQL view renderer](078-spa-graphql-view-renderer.md)**; **SavedView
persistence, CalendarModel replacement, view-switching/conversion, week/month calendar
render-modes → [PRD 077](077-calendar-model-graphql.md)**; **the global unified/power-search across views → PRD [077](077-calendar-model-graphql.md) / [060](060-graphql-mcp-foundations.md)**
(it is a shared cross-view selector and conflicts with Locked Decision #6's per-view inputs).

## Plan — phased

> **Implementation status (2026-06-21) — the block data layer is built + green** (tier-3 tests,
> `ReservationGraphQLControllerTest`, 36 passing; each "Baustein" reviewed by an adversarial
> multi-agent workflow). Done:
> - **Baustein 1** — `Query.appointmentBlocks(filter: ReservationFilter!): [AppointmentBlock!]!`:
>   block-rooted flat read, reuses `reservations()` (§12 + window + limit), **bounded top-N heap**
>   (the limit-earliest blocks, O(limit) memory — not naive expand-all-then-truncate, which a
>   review caught would also break the sort).
> - **Baustein 2** — `Reservation.displayName` (nameformat composition, server-resolved) +
>   `AppointmentBlock.reservation` (block → reservation navigation; DTO carries it).
> - **Baustein 3** — `AppointmentBlock.allocatables(filter: AppointmentAllocatableFilter)`,
>   reusing the shared §12-gated + per-appointment-restriction resolver from `Appointment.allocatables`.
> - **Baustein 4** — `AppointmentBlock.duration` + `.times`, server-evaluated via the **rapla
>   function bridge** (`evalBlockFunction`: factory-by-namespace → `createFunction` with an identity
>   arg → `EvalContext` over the real block → `toString`). The bridge is the runtime half of [PRD 073](073-graphql-function-equivalents.md).
> - **Baustein 5** — `AppointmentBlock.compute(expr:)`, inline composition reusing the table-column
>   machinery (`ParsedText` over the block's `DynamicType` parse context). Max 2000 chars; invalid → null.
> - **Baustein 6** — `Reservation.name(variant: NameVariant = DISPLAY)` (model A; `displayName`
>   `@deprecated`). `Allocatable.name(variant:)` + deprecated `displayName` (**Baustein 7**).
> - **Baustein 8** — render-meta layer: `@view`/`@column`/`@hidden`/`@join` directives +
>   `ViewMetaInstrumentation` emitting `extensions.view = {key,title,columns}` (no `@view` → no
>   extensions). Each column carries a schema-derived **`type`** hint and the descriptors are
>   **sorted by `@column(order:)`** — the GUI renders left-to-right without re-deriving anything.

#### `extensions.view` contract v1 (GUI consumer — [PRD 078](078-spa-graphql-view-renderer.md))

**Directives are CLIENT/render-only (locked 2026-06-21).** `@column`/`@hidden`/`@join`/`@flatten`
are pure presentation hints in `extensions.view.columns`; they never touch `data`. Server-side
**evaluation** is NOT done via directives — it lives in **query args** (`filter`/`sort`/`offset` on
the flat table) and in the **separate typed field `appointmentBlockStats`** (aggregation + grouping,
[PRD 079](079-graphql-grouped-aggregates.md)). The earlier `@aggregate`/`@group` directives + `extensions.view.totals`/`.groups` were
**removed**: aggregates belong in typed `data` ("like compute"), not an untyped side-channel.
Rationale (incl. the "footer in one pass" trade-off we accepted): [PRD 079](079-graphql-grouped-aggregates.md).

The render directives shape `extensions.view` only and never alter `data`. A query without `@view`
returns no `extensions.view` (zero overhead). `extensions.view.page` (pagination meta of the flat
table) is the one non-column entry the server still emits.

| Directive | On | Args | Effect on the column descriptor |
|---|---|---|---|
| `@view` | QUERY (operation) | `title: String` | enables emission; `key` = operation name, `title` = arg |
| `@column` | FIELD | `header: String`, `order: Int` | `header` (default = alias), sort position |
| `@hidden` | FIELD | — | `hidden: true` (present in data, not a visible column — e.g. group/sort keys) |
| `@join` | FIELD | `separator: String` | `join: "<sep>"` — renderer joins a list column's leaf values |

Emitted shape:

```jsonc
"extensions": {
  "view": {
    "key": "Termine",            // operation name
    "title": "Termine KW",       // @view(title:)
    "columns": [                 // sorted by @column(order:), then declaration order
      { "alias": "head",    "header": "Veranstaltung", "type": "Reservation" },
      { "alias": "start",   "header": "start",         "type": "LocalDateTime" },
      { "alias": "day",     "header": "day",           "type": "LocalDateTime", "hidden": true },
      { "alias": "persons", "header": "persons",       "type": "Allocatable",   "join": "; " }
    ]
  }
}
```

- `alias` = the field's response key (read `data` by this) — the GraphQL alias, else the field name.
- `type` = the **unwrapped** GraphQL type name (list/non-null stripped): scalars (`String`,
  `Int`, `Float`, `Boolean`, `LocalDateTime`, `Date`, `ID`) → leaf cell + alignment/format;
  object names (`Reservation`, `Allocatable`) → nested value the renderer projects/joins.
- Field directives must precede the sub-selection: `head: reservation @column(header:"…") { name }`.

Example query the SPA can ship as-is:

```graphql
query Termine @view(title: "Termine KW") {
  appointmentBlocks(filter: { from: "2006-01-01T00:00:00", to: "2006-12-31T00:00:00" }) {
    name        @column(header: "Veranstaltung", order: 0)
    start       @column(header: "Beginn",        order: 1)
    duration    @column(header: "Dauer",         order: 2)
    persons:   allocatables(filter: { isPersonEq: true }) @join(separator: "; ") @column(header: "Dozent", order: 3) { name }
    resources: allocatables(filter: { typeIn:[room] }) @join(separator: ", ") @column(header: "Raum",  order: 4) { name }
  }
}
```
> - **Baustein 9** — `AppointmentBlock.name(variant:)` (FLAT, **block-aware** via
>   `reservation.formatAppointmentBlock` — NOT the reservation name; honors appointment-note overrides)
>   + `Appointment.name(variant:)` (appointment-aware via `formatAppointment`). Mirrors the three
>   `NameFormatUtil` levels (block / appointment / classifiable).
>
> **Test debt (Baustein 9) — RESOLVED 2026-06-21.** The divergence regression now exists:
> `AppointmentBlockNoteNameTest` (tier-2, `rapla-server`, no GraphQL schema) gives the event type a
> note-aware nameformat (`{name} {format("<%s>",appointment:note())}`), creates one reservation with
> two appointments — note only on the second — and asserts the noted block's name carries
> `<Klausureinsicht>` while the plain block and the reservation name do not (`block.name !=
> reservation.name`). Backed by a live dhbw probe (reservation "Feedback Studiengangsleitung": only the
> noted occurrence rendered the suffix). `FacadeTestSupport` now also wires the appointmentnote
> FunctionFactory so `appointment:note()` resolves in tier-2.
>
> - **Baustein 10** — **sort** (`appointmentBlocks(sort: [BlockSort!])` — `BlockSortField`
>   START/END/NAME × `SortDir` ASC/DESC, locale Collator for NAME, stable reservation-id tiebreaker)
>   + **pagination** (`offset: Int`; bounded heap keeps `offset+limit+1` so `hasMore` needs no full
>   count). With `@view`, `extensions.view.page = { offset, limit, returned, hasMore }` (resolver →
>   GraphQLContext → `ViewMetaInstrumentation`). GUI-ready parameterized query (variables
>   `$filter`/`$sort`/`$offset`) lives in the contract block above.
>
> - **Baustein 11** — `@flatten(field:)` directive: meta-only hint adding `flatten: "<leaf>"` to
>   the column descriptor (explicit arg, or auto-detected single sub-field) so the GUI projects a
>   nested object/list column to a flat value. Data stays nested.
> - **Baustein 12** — numeric per-block field `AppointmentBlock.durationMinutes` (wall-clock end−start),
>   the aggregatable basis for analytics.
> - **Aggregation/grouping → moved to [PRD 079](079-graphql-grouped-aggregates.md) (Shape A).** An interim `@aggregate`/`@group` directive
>   pass (totals/groups in `extensions.view`) was built and then **reverted**: aggregates belong in
>   typed `data`, delivered by the dedicated `appointmentBlockStats` field (global total = no-groupBy).
>   074's directives are now render-only.
>
> **A + V2 + Stufe b (2026-06-21) — built + green** (53 GraphQL tests; legacy nameformat/tableview
> EL tests still green — 6 + 26):
> - **A — filter unification.** The 3 nested allocatable spots (`Appointment.allocatables`,
>   `AppointmentBlock.allocatables`, `appointmentBlockStats.groupBy.allocatables`) now use the **full
>   `AllocatableFilter`** (was the lean `AppointmentAllocatableFilter`, **removed**). `filterAllocatables`
>   reuses the SAME helpers as `Query.allocatables` — `matchesMap` (scalar) + `WhereEvaluator` (`where<TypeKey>`)
>   + `idIn` + `AccessTargetFilter` (`accessibleBy*`/`accessLevel`, e.g. "resources of this event I may edit")
>   + `limit`. **Option 2** (apply everything; no ignored fields). §12: `canRead` runs FIRST → narrowing
>   can't leak. The lean type was only a guardrail against silent no-ops; once every field is honored it's
>   unneeded. → enables "Raumauslastung Standort Mosbach" server-side via `whereRaum.Gebaeude`.
> - **V2 — one rapla-expression surface.** Subject **`item`**; **bare body** auto-wraps as `{item -> …}`
>   in `computeBlockExpr`; **0-arg default** on unary subject functions (`start`/`times`/`end` extended,
>   additive; `name`/`duration`/`resources` already supported it) → `times()`; arrow **`->` and `=>`**
>   both accepted (ParsedText, `=>` is the externally-documented form). Explicit/n-param lambdas use the
>   braced form `{(a,b) -> …}`. Legacy `{p->fn(p)}` unchanged. Applies to `compute`, group-`expr`, metric-`expr`.
> - **Stufe b — expr metrics.** `BlockAggregate` gains `expr` (numeric; `field` now optional). `metricValue`
>   evaluates the expr (`computeBlockExpr`) and coerces the result to a double (canonical `.`); non-numeric/
>   formatted results are skipped → feeds the existing reduction. Constant/numeric exprs work now.
> - **a — reference by name.** `AllocatableWhere` gains `nameContains` → filter a reference by the
>   referenced entity's display name in ONE query (`whereRaum: { Gebaeude: { nameContains: "MOS" } }`).
> - **b — typed reference recursion (PRD [059](done/059-graphql-typed-where-predicates.md)/[065](065-graphql-declared-type-groups.md)).** Reference attributes with a `KEY_DYNAMIC_TYPE`
>   constraint now generate a `<RefType>RefWhere` ( `eq/ne/in/isNull/nameContains` + `where: <RefType>Where` )
>   and the field targets it. `WhereEvaluator` resolves the referenced allocatable, **§12-`canRead`-gates it**
>   (caller/pc threaded through evaluate→…→matchAllocatable; hidden ref ⇒ row dropped, no attribute leak),
>   then recurses into its typed `where` (depth-capped). → filter rooms by the building's OWN typed
>   attributes, e.g. `whereRaum: { Gebaeude: { Standort: { eq: "Mosbach" } } }`. **Schema cost bounded**:
>   one small `<T>RefWhere` per referenced allocatable type. **Test caveat:** the unit fixture
>   (`testdefault.xml`) has no allocatable-reference attribute → b is inert there (no regression; 53 green),
>   so the recursion is **verified live** against dhbw (`Raum.Gebaeude`) after a server restart — owed: a
>   fixture with a reference attribute for a tier-2/3 b regression + §12-leak test.
>
> **Remaining (server):** the `ComputeFunctions` SDL catalog ([PRD 073](073-graphql-function-equivalents.md) descriptor-SPI). **Deferred:**
> **Stufe c** — in-expression arithmetic (`add/sub/mul/div`), the EL number-model ([PRD 073](073-graphql-function-equivalents.md)), which then
> serves all expr surfaces; persistence / SavedView / switching / week-month → [PRD 077](077-calendar-model-graphql.md); the Angular
> table renderer → [PRD 078](078-spa-graphql-view-renderer.md).

1. **Phase 1 — Generator + render-meta.** Compile col annotations → server-evaluated
   composition fields (reuse `ParsedText`) + GraphQL filters; the `@view` directive +
   `extensions.view` emission for `events` + `appointments`. §12 via existing resolvers.
   (The Angular `cdk-table` renderer that *consumes* `extensions.view` is **[PRD 078](078-spa-graphql-view-renderer.md)**.)
2. **Phase 2 — Grouping + server export.** The `appointmentBlocks(filter:)` query root
   (flat block rows); the hidden `day` group/sort column (`@group(by: DAY) @hidden`) for
   `appointments_per_day`; CSV/HTML/iCal export reuse the same **server-side** evaluation.
   (`@flatten`/`@column(order:)` are *not* needed once each view roots at the right level.)
3. **Phase 3 — Authoring + polish.** Override directives (`@column`/`@when`) — server side.
   (The component registry + `monaco-graphql` authoring editor are SPA → **[PRD 078](078-spa-graphql-view-renderer.md)**.)
4. **Phase 4 — Authoring scope + shared views** (global vs group-admin; personal vs
   shared).
5. **Future — pagination/prev-next; optional aggregate-field convention; companion
   charts/forms PRDs.**

## Tests

- **Tier 1 — composition-field correctness** — each generated server-eval field
  returns the same value as the rapla Function it compiled from (`name`/`times`/
  `duration`/`persons`/`resources` over a fixture). No dual-runtime corpus needed —
  one server-side evaluator.
- **Tier 3 (MockMvc) §12 leak test** — two users run the same saved view; each sees
  only their readable rows (byte-identical to visible-only subset); the view can't
  widen scope.
- **Renderer (tier 5/6)** — convention rendering, grouping, the multi-level case → **[PRD 078](078-spa-graphql-view-renderer.md)**
  (Angular consumer tests; 074 stops at the `extensions.view` contract).
- **Server/client equivalence** — SPA render and CSV/HTML/iCal export over the *same*
  GraphQL response produce the same rows (the SPA half lives in **[PRD 078](078-spa-graphql-view-renderer.md)**).
- **Save-time validation** — invalid GraphQL / unknown directive / over-deep query rejected.

## Open questions

1. **Parser strategy** — hand-written recursive-descent pair vs ABNF code-gen (APG).
   Tiny grammar; decide before writing either.
2. **Number + collation model** — decimal/rounding for `sum`/`mean`; sort order
   (byte vs locale; German `ä/ö/ü`). Highest drift risk; written decision required.
3. **Flatten parent-projection ergonomics** — explicit `project:{}` (leak-safe) vs
   auto-carry parent scalars under `$parent.`. Lean explicit.
4. **`window` op** — defer to v2 (the two dhbw tables don't need it).
5. **Plugin-op parity SPI** — per-op shared descriptor (name/namespace/arity/return)
   + conformance gate so the Java and TS ops can't diverge or go missing.
6. **`specVersion` + migration** — stored shared specs need a version + compat policy
   from day one.
7. **Server consumers** — confirm every server-rendered consumer (CSV, HTML, iCal)
   so parity covers the paths actually evaluated on the JVM.
8. **Live-preview tooling** — v1 (validator + SPA preview) vs v2.
9. **Generation mechanism spec** — how each col annotation compiles to its GraphQL
   construct (selection → `allocatables(filter:)`; projection/derivation →
   server-evaluated field), and how `@flatten` / `@groupBy(field:)` / `@column(order:)`
   are defined as schema + render constructs. (The old A-vs-A′ engine question is moot —
   the preferred design has no client engine; complex compositions evaluate server-side
   via rapla's own engine, CEL dropped.)
