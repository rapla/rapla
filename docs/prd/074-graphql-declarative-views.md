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
config (`/table/*`, PRD 030 — deprecated, frozen, **no migration**; the new path is
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
| **Edit forms** | client only | **ngx-formly** → GraphQL mutations | companion / future PRD 075 |

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
   → localized header, type → formatting, list → join; explicit
   `@column(order:)`/`@flatten`/`@groupBy` only where nesting flattens *and* reorders
   (the three worked tables). CEL is **not** used — see §"If a view ever needs more".

## Inputs = query variables (controls inferred by convention)

The client maps each variable to a control by **name + type**; pageability adds
navigation. No directive for the common cases:

| Variable(s) | Inferred control |
|---|---|
| `from` + `to` (date/time) | date-range picker |
| `name` / search string | search combobox |
| query is pageable (offset/cursor) | next / prev — *future* |

Controls are state over variables: the control mutates a variable, the query
re-runs. Non-conventional bindings use a `@control` client directive (override
only).

## Data = GraphQL (prediction + navigation only)

The query decides **what data is in the result**: prediction (`where` PRD 059 +
`searchText`/`matchKind` PRD 028 + access selectors PRD 069), navigation
(selection), and **server-computed scalars** the client can't derive
(`displayName`, `durationMinutes`, typed attributes — the PRD 073 gaps). It selects
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

- **Selection + filter → GraphQL** — `where` (PRD 059), the nested `allocatables`
  filter (shipped 2026-06-19), access selectors (PRD 069). In the dhbw model
  **rooms, courses and lecturers are referenced *allocatables*, not classification
  attributes**: `Raum`/`Teilraum`/`virtuellerRaum`, `Kurs`/`Teilkurs`/`Kursgruppe`,
  `Person` — split per column by `typeKeyIn`. Rows are **AppointmentBlocks**
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

### Authoring layer (readability)

The stacked-directive query below is **machine-generated, never hand-written**. The
admin authors a compact spec (layer ①); the system compiles it to the generated query
+ per-composition directives (layer ②); GraphQL returns §12-filtered data and the SPA
renders the table (layer ③). This closes the readability gap **without** giving up the
closed-directive wire form — the dense form is a machine artefact, not an authoring
format. The raw composition (`concat(...)`) lives only in ①, compiled once to the
`@timeRange` directive.

> **Open / unsettled (2026-06-20):** whether a *separate* authored spec is even
> needed (the directive query may be authored directly via a form/builder instead),
> and **exactly how ① maps to ②** — the spec→directive/query compilation is
> unspecified. The ① YAML below is an **illustrative sketch, not a committed format**.

### Worked queries — the three real dhbw tables

The three real views are `org.rapla.plugin.tableview.{events, appointments,
appointments_per_day}`. Their **exact column sets** are the default `ViewDefinition`s in
`TableConfig.java` (and persist in the dhbw `data.xml`) — documented here verbatim as an
**existing table config** (ground truth, not invented):

| View | `contentDefinition` (rows) | columns (in order) |
|---|---|---|
| `events` | `{p->events(p)}` — reservations | `name`, `start`, `lastchanged` |
| `appointments` | `{p->appointmentBlocks(p)}` — blocks | `name`, `start`, `end`, `resources`, `persons` |
| `appointments_per_day` | `{p->appointmentBlocks(p)}` — blocks, grouped per day | `times`, `name`, `resources`, `persons` |

(`duration` is a *defined* column in `tableview.config` but is in **none** of the three
default views.) Each column's `defaultValue` maps to a GraphQL construct: selection →
`allocatables(filter:)`; projection/derivation → a **server-evaluated field** (rapla's
`ParsedText`; `displayName` is just the `name` composition).

**Directives are implicit by default, explicit only where the structure forces it.**
GraphQL preserves field order (= column order); alias = column key → localized header;
type drives formatting; a list is joined by convention. `@column(order:)`/`@flatten` are
needed **only** when columns come from different nesting levels and the order interleaves
them.

**Table 1 — `events` · rows = reservations · columns `name`, `start`, `lastchanged` · flat → 0 directives:**
```graphql
query Termine_events {
  reservations(filter: { typeKeyIn: ["Lehrveranstaltung"] }) {
    name: displayName             # {p->name(p)}
    start: firstDate              # {p->start(p)}        (reservation's first date)
    lastchanged: lastModifiedAt   # {p->lastchanged(p)}
  }
}
```
Three reservation-level fields → flat: order implicit, header from alias, datetime
formatted by convention. **No `end`, no resources/persons** — exactly the real config.

**Table 2 — `appointments` · rows = blocks · columns `name`, `start`, `end`, `resources`, `persons` · multi-level → `@flatten` + `@column(order:)`:**
```graphql
query Termine_appointments($from: DateTime!, $to: DateTime!) {
  reservations(filter: { typeKeyIn: ["Lehrveranstaltung"] }) {
    name: displayName @column(order: 1)                       # res level   {p->name(p)}
    appointments {
      resources: allocatables(filter: { isPersonEq: false }) @column(order: 4) { displayName }
      persons:   allocatables(filter: { isPersonEq: true })  @column(order: 5) { displayName }
      blocks(from: $from, to: $to) @flatten(project: ["name","resources","persons"]) {
        start @column(order: 2)                               # block date+time   {p->start(p)}
        end   @column(order: 3)                               # block date+time   {p->end(p)}
      }
    }
  }
}
```
`@column(order:)` because the nesting order (name, resources, persons, start, end) ≠ the
column order (name, **start**, **end**, resources, persons) — `start`/`end` are block-deep
but want columns 2–3, and the res > appt > block tree can't be reordered.
`@flatten(project:)` pulls the res/appt fields onto each block row. `start`/`end` carry
the **full date+time** (this is the table with the date per row). **No `times`, no
`duration`** — exactly the real config.

**Table 3 — `appointments_per_day` · rows = blocks, grouped per day · columns `times`, `name`, `resources`, `persons`:**
Rows are appointment blocks (same `{p->appointmentBlocks(p)}` content as `appointments`);
the **server page groups them per day** (`AppointmentPerDayViewPage`) and the row shows
`times` (time-of-day) — there is **no** `start`/`end` date column (the date is the day
grouping). `times` is column 1.
```graphql
query Termine_perDay($from: DateTime!, $to: DateTime!) {
  reservations(filter: { typeKeyIn: ["Lehrveranstaltung"] }) {
    name: displayName @column(order: 2)                       # {p->name(p)}  (column 2 — times is 1)
    appointments {
      resources: allocatables(filter: { isPersonEq: false }) @column(order: 3) { displayName }
      persons:   allocatables(filter: { isPersonEq: true })  @column(order: 4) { displayName }
      blocks(from: $from, to: $to)
            @flatten(project: ["name","resources","persons"])
            @groupBy(field: "start", by: DAY)     # group block rows by the DAY of start (section header)
      {
        start                                      # selected only to derive the day (not a column)
        times @column(order: 1)                    # {p->times(p)}  — time-of-day, column 1
      }
    }
  }
}
```
`start` is selected **only** so the SPA can derive the day for the section header — it is
not a displayed column. Rows are per **block** (not per day); `@groupBy` clusters them by
the day of `start`. There is **no split** (a block is one occurrence) and **no
count/sum/collapse**. `times` is column 1, `name` column 2 → `@column(order:)` needed
(`times` is block-deep but wants column 1). **No `duration`** — exactly the real config.

**None of the three real tables aggregate.** `@groupBy` is presentation sectioning,
distinct from aggregation (count/sum/collapse). A GraphQL aggregate-field convention
remains a *possible future* capability for explicit aggregate views, **not** used by the
standard dhbw tables.

**The rule, on the real tables:**

| View | rows | columns | directives needed |
|---|---|---|---|
| `events` | reservations | name, start, lastchanged | **none** (flat, all implicit) |
| `appointments` | blocks | name, start, end, resources, persons | `@flatten(project:)`, `@column(order:)` |
| `appointments_per_day` | blocks (grouped per day) | times, name, resources, persons | `@flatten(project:)`, `@groupBy(field: start, DAY)`, `@column(order:)` |

So: **implicit by default; explicit only where nesting flattens *and* reorders** — both
`appointments` and `appointments_per_day` pull columns across res/appt/block levels (so
both need `@flatten` + `@column(order:)`); `events` is flat and needs nothing.

### Example outputs + GUI rendering (all three)

Dummy data (no real persons, AGENTS.md §17). Each shows the **GraphQL response** (plain
typed data, server already evaluated the composition fields + §12-filtered) and **what
the SPA renders**.

**Table 1 — `events` · columns name, start, lastchanged · GraphQL output:**
```json
{ "data": { "reservations": [
  { "name": "Programmieren II",
    "start": "2026-06-22T10:00:00",
    "lastchanged": "2026-06-10T14:22:00Z" }
] } }
```
**GUI** — one row per reservation; `start` = first date, `lastchanged` type-formatted:

| Name | Beginn | Geändert |
|---|---|---|
| Programmieren II | 22.06.2026 10:00 | 10.06.2026 14:22 |

**Table 2 — `appointments` · columns name, start, end, resources, persons · GraphQL output**
(nested; `start`/`end` are datetimes):
```json
{ "data": { "reservations": [
  { "name": "Programmieren II",
    "appointments": [
      { "resources": [ { "displayName": "A474 Hörsaal" } ],
        "persons":   [ { "displayName": "Prof. X" } ],
        "blocks": [
          { "start": "2026-06-22T10:00:00", "end": "2026-06-22T11:30:00" },
          { "start": "2026-06-29T10:00:00", "end": "2026-06-29T11:30:00" } ] } ] }
] } }
```
**GUI** — `@flatten(project:)` makes one row per block with `name`/`resources`/`persons`
projected down; `@column(order:)` puts `Beginn`/`Ende` (block-level) into columns 2–3:

| Name | Beginn | Ende | Ressourcen | Personen |
|---|---|---|---|---|
| Programmieren II | 22.06.2026 10:00 | 22.06.2026 11:30 | A474 Hörsaal | Prof. X |
| Programmieren II | 29.06.2026 10:00 | 29.06.2026 11:30 | A474 Hörsaal | Prof. X |

**Table 3 — `appointments_per_day` · columns times, name, resources, persons · GraphQL output**
(`start` is selected only to derive the day; the SPA groups rows under day headers):
```json
{ "data": { "reservations": [
  { "name": "Programmieren II",
    "appointments": [ { "resources": [ { "displayName": "A474 Hörsaal" } ],
                        "persons": [ { "displayName": "Prof. X" } ],
                        "blocks": [ { "start": "2026-06-22T10:00:00", "times": "10:00–11:30" } ] } ] },
  { "name": "Datenbanken",
    "appointments": [ { "resources": [ { "displayName": "B12" } ],
                        "persons": [ { "displayName": "Dr. A" } ],
                        "blocks": [ { "start": "2026-06-22T14:00:00", "times": "14:00–15:30" } ] } ] }
] } }
```
**GUI** — rows grouped under day section-headers (from `start`); columns `times`, `name`,
`resources`, `persons` (times first); `start` itself is not shown:

```
▼ Mo 22.06.2026
    10:00–11:30   Programmieren II       A474 Hörsaal   Prof. X
    14:00–15:30   Datenbanken            B12            Dr. A
▼ Di 23.06.2026
    09:00–10:30   Software Engineering   A474 Hörsaal   Prof. X
```
No row is collapsed — the day is purely a section header.

### Validated against the real dhbw tables (data.xml, 2026-06-20)

The three real table views — `org.rapla.plugin.tableview.{events, appointments,
appointments_per_day}` — use eight standard rapla columns (stored as `tableview.config`
column `defaultValue` expressions). The **real** col annotations and their mapping:

| rapla column | col annotation (`defaultValue`) | GraphQL-native mapping | engine? |
|---|---|---|---|
| `name` | `{p->name(p)}` | `displayName` — event-type **nameformat already evaluated server-side** | **no** |
| `start` / `end` | `{p->start(p)}` / `{p->end(p)}` | `start` / `end` + `@format(DATETIME)` | no |
| `times` | `{p->times(p)}` | server-evaluated `times` field (rapla engine) | no |
| `persons` | `{p->filter(resources(p),r->isPerson(r))}` | `allocatables(filter:{ isPersonEq:true })` (join by convention) | no |
| `resources` | `{p->filter(resources(p),r->not(isPerson(r)))}` | `allocatables(filter:{ isPersonEq:false })` (join by convention) | no |
| `duration` | `{p->org.rapla.eventtimecalculator:duration(p)}` | server-evaluated `duration` field; raw `durationMinutes` (PRD 073 Ph2) for aggregation | no |
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
`allocatables(filter:{ isPersonEq } / { typeKeyIn:[…] })`. What remains for the table
layer is closed formatting (`@format`/`@join`/`@times`). **The expressive real
compositions are either (a) server-pre-computed nameformats → `displayName`, or (b)
selection → GraphQL filters — neither needs an engine.** This validates the no-engine
verdict on real data, not invented examples.

**`appointments_per_day` is grouping, not aggregation:** rows are appointment blocks
(columns `times`, `name`, `resources`, `persons`); the SPA **groups the rows under day
section-headers** derived from each block's `start`. No counting, no summing, no row
collapse, no split — just `@groupBy(field: "start", by: DAY)` (presentation). **Not** an
aggregate field. None of the three standard tables aggregate.

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
  (PRDs 056/057/061/063). No JVM needed (forms are client-rendered; the server only
  runs the mutation, which enforces `canModify`/`canAdmin` per §12/§16). Candidate
  **PRD 075**. Complex types (repeating appointments, allocatable refs) may exceed a
  flat form.

## Dependencies

- **Data-layer gaps — verified against live `rapla-test.dhbw.de` (2026-06-19).** To
  push filtering/computed-values into GraphQL: `Appointment.allocatables` takes **no
  arguments** (so aliased filtered sub-selections aren't expressible); **no
  `durationMinutes`**; **no `typeGroup`** / declared type-groups (PRD 065). Present:
  `AllocatableFilter` (`typeKeyEq`/`typeKeyIn`/`isPersonEq` + per-type `where*`,
  PRD 059) on `Query.allocatables` only. Closing these (PRD 073 + 065) keeps the
  transform thin.
- **Pagination + prev/next + server-side `aggregate`** are future. Client-side
  aggregation covers non-paginated admin tables; once paginated, full-set totals
  move to a server `aggregate` field (Hasura-style).

## Scope

**In:** the GraphQL-native read-table view model — the generator that compiles each col
annotation to a server-evaluated composition field (reusing rapla's `ParsedText`) or a
GraphQL filter; GraphQL selection/filter; presentation directives
(`@column`/`@flatten`/`@groupBy`) as optional overrides; variable→control inference;
per-user §12 execution; save-time validation; XSS hardening.

**Out:** any client expression engine / CEL / dual-runtime parity (evaluated and dropped
— compositions run server-side); the rapla DSL / Swing-HTML TableView (deprecated, not
migrated); GraphQL mutations / edit forms (companion / PRD 075); charts beyond the
client-only Vega-Lite note; pagination + aggregate-field convention (future).

## Plan — phased

1. **Phase 1 — Generator + renderer.** Compile col annotations → server-evaluated
   composition fields (reuse `ParsedText`) + GraphQL filters; convention-driven
   `cdk-table` renderer (field order = columns, alias → header, join, format); render
   `events` + `appointments`. Saved-view config entity (CRUD, admin-scoped). §12 via
   existing resolvers.
2. **Phase 2 — Multi-level shaping + server export.** `@flatten`/`@column(order:)` for
   the cross-level `appointments` order; `@groupBy(field: "start", by: DAY)` for
   `appointments_per_day`; CSV/HTML/iCal export reuse the same **server-side** evaluation.
3. **Phase 3 — Authoring + polish.** Override directives (`@column`/`@when`), component
   registry, `monaco-graphql` autocomplete over the query (no transform-spec editor).
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
- **Renderer (tier 5/6)** — convention rendering (field order = columns, alias →
  header, list join, type format) and the multi-level case (flatten + `@column(order:)`
  → correct column order; `@groupBy(day)` → day section headers, no collapse).
- **Server/client equivalence** — SPA render and CSV/HTML/iCal export over the *same*
  GraphQL response produce the same rows.
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
