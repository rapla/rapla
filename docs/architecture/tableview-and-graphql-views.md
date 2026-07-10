# TableView config → GraphQL views — capability benchmark

**Status:** reference. **Purpose:** the legacy Swing/HTML **TableView** configs become
legacy with the Angular SPA. This doc captures the *existing* table views (ground truth
from the dhbw deployment + the rapla code) as a **capability benchmark** for the new
GraphQL-native view system ([PRD 074](../prd/074-graphql-declarative-views.md)): _can the new GraphQL views reproduce exactly what
the existing tables do?_ It exists so future discussions don't re-derive the table config
or argue about whether GraphQL can match it.

Design of the replacement: [PRD 074 — Declarative GraphQL View Definitions](../prd/074-graphql-declarative-views.md).
Function/column-annotation reference: [PRD 073](../prd/073-graphql-function-equivalents.md).

> Dummy data only (AGENTS.md §17). Real lecturer/room names from live probes are
> replaced with `Prof. X`, generic course codes, etc.

## 1. How the legacy TableView config works

Source: `org.rapla.plugin.tableview.internal.TableConfig` (rapla-core). Configured in the
Swing client under **Ressourcen bearbeiten → plugins → Tableview Plugin**.

> **Legacy = a combination of BOTH stores (corrected):** the legacy TableView config is
> *both* (a) per-type **`tablecolumn_*` annotations** on the reservation type (the column
> compositions, e.g. `tablecolumn_name = {p->name(p)}`) **and** (b) the
> **`org.rapla.plugin.tableview.config`** preference (the views + which columns each shows
> + ordering). Both are present in both datasets (wochenplan: 121 annotations + 1 config;
> dhbw: 50 annotations + config). The new GraphQL views replace the **combination**
> (greenfield — no migration).

`tableview.config` (the preference half) stores:

- `org.rapla.plugin.tableview.config` — a `RaplaConfiguration` holding **column
  definitions** (`<column>`: `key`, `defaultValue`, `type`, localized `name`) **and**
  per-**view** `ViewDefinition`s (each: a `contentDefinition` + an ordered column list).
- `org.rapla.plugin.tableview.<view>.sortingstring` — per-view **sort** spec only
  (e.g. `0-;` = sort by column 0 descending). **Not** column membership.

A **column** is a named rapla **Function composition** (`defaultValue`, a `ParsedText`
expression) evaluated per row. A **view** has a `contentDefinition` (the row expansion)
and an ordered subset of the defined columns.

### Standard column definitions (rapla defaults, `TableConfig.java`)

| key | `defaultValue` (col annotation) | type |
|---|---|---|
| `name` | `{p->name(p)}` | string |
| `start` | `{p->start(p)}` | datetime |
| `end` | `{p->end(p)}` | datetime |
| `lastchanged` | `{p->lastchanged(p)}` | datetime |
| `resources` | `{p->filter(resources(p),r->not(isPerson(r)))}` | string |
| `persons` | `{p->filter(resources(p),r->isPerson(r))}` | string |
| `times` | `{p->times(p)}` | string |
| `org.rapla.eventtimecalculator:duration` | `{p->org.rapla.eventtimecalculator:duration(p)}` | string |

Deployments may add **custom columns** (renamed `customColumn_n` with their own
`defaultValue`) — see the dhbw `appointments` view below.

### Default views (`TableConfig.java`)

| view | `contentDefinition` (rows) | default columns |
|---|---|---|
| `events` | `{p->events(p)}` — reservations | name, start, lastchanged |
| `appointments` | `{p->appointmentBlocks(p)}` — appointment blocks | name, start, end, resources, persons |
| `appointments_per_day` | `{p->appointmentBlocks(p)}` — blocks, grouped per day | times, name, resources, persons |

## 2. The dhbw **configured** `appointments` (Termine) view — ground truth

Verified from the deployment's Tableview-Plugin dialog (the column-ordering for *Termine*):

**Columns (in order):** `Name`, `Beginn`, `Ende`, `Kurs`, `Person`, `Raum`, `Dauer`

The deployment **split** the generic `resources` column into **`Kurs`** and **`Raum`**
(non-person allocatables by classification type), kept **`Person`** (person allocatables),
and **added `Dauer`** (the eventtimecalculator duration, values like `2 UE 0 Min`). Rows
are appointment blocks. This is the concrete example of per-type allocatable columns.

(`events` / `appointments_per_day` are configured the same way per deployment; only the
`appointments` ordering was captured here. `appointments_per_day` is the same flat block
table plus a **hidden `day` column** that sorts + groups the rows — a presentation
grouping, **not** an aggregation: no row is collapsed, nothing is counted or summed.)

## 3. GraphQL reproduction (the benchmark)

Each legacy column maps to a GraphQL construct ([PRD 074](../prd/074-graphql-declarative-views.md)): **selection** (rooms / courses /
persons are referenced *allocatables*, split by `typeKeyIn` / `isPersonEq`) →
`allocatables(filter:)`; **projection / derivation** (name, start, end, times, duration,
lastchanged) → a **server-evaluated field** that reuses rapla's existing `ParsedText`
engine (so `displayName` = the `name` composition, `Dauer` = the duration composition).
No client-side expression engine.

### `appointments` (dhbw Termine) — GraphQL query

Rooted at the **blocks** (`{p->appointmentBlocks(p)}`) → one row = one block → **flat**
(field order = column order, no `@flatten`/`@column(order:)`). The **model** filter is the
`$filter` variable; the per-column `allocatables(filter:)` is the **annotation** filter,
inline (the shipped `Appointment.allocatables(filter: AppointmentAllocatableFilter)`):

```graphql
query Termine_appointments($filter: ReservationFilter!) {
  appointmentBlocks(filter: $filter) {                                              # rows = blocks; $filter ← CalendarModel (incl. from/to)
    name:   reservation { displayName }                                             # Name    {p->name(p)}
    start                                                                           # Beginn  {p->start(p)}
    end                                                                             # Ende    {p->end(p)}
    kurs:   allocatables(filter:{ typeKeyIn:["Kurs","Teilkurs","Kursgruppe"] })     { displayName }   # Kurs (annotation → inline)
    person: allocatables(filter:{ isPersonEq:true })                                { displayName }   # Person
    raum:   allocatables(filter:{ typeKeyIn:["Raum","Teilraum","virtuellerRaum"] }) { displayName }   # Raum
    duration                                                                        # Dauer   {p->…:duration(p)}
  }
}
```

Renders (`Kurs`/`Person`/`Raum` are **lists** — one appointment commonly has several,
joined per cell):

| Name | Beginn | Ende | Kurs | Person | Raum | Dauer |
|---|---|---|---|---|---|---|
| Programmieren II | 15.06.2026 08:00 | 15.06.2026 09:30 | FN-TEK23, FN-TEN23 | Prof. X, Dr. A | H004 Seminarraum, H005 Seminarraum | 2 UE 0 Min |

> **Snapshot caveat.** The column **headers** `Name/Beginn/Ende/Kurs/Person/Raum/Dauer`
> are verified from the live deployment screenshot. The archived `data.xml` / HSQLDB
> snapshot only contains the generic `resources` (all non-persons) + `persons` column
> `defaultValue`s — the live config splits `resources` into the separate `Kurs` and
> `Raum` columns by allocatable type (the exact legacy split expression isn't in the
> snapshot). The GraphQL `typeKeyIn` split above is the mapping any two separate
> Kurs/Raum columns require.

### Why almost no directives are needed

Each view **roots its query at the level its `contentDefinition` names** — `reservations`
for `events`, `appointmentBlocks` for `appointments` / `appointments_per_day`. Rooted
correctly, one row = one root object, so the query is **flat**: field order = column
order, alias = column key → localized header, type drives formatting, lists join by
convention. So **events and appointments need no directives at all**. The *only*
structural directive is the `appointments_per_day` **hidden `day` column** (`@group(by:
DAY) @hidden`) that sorts + groups the rows. (Rooting block-row tables at `reservations`
was what previously forced `@flatten`/`@column(order:)` — an artifact, now gone.)

### Two filter sources, two homes

- **Annotation filters → inline (view definition).** The per-column allocatable filter
  (`Kurs` = `typeKeyIn:["Kurs","Teilkurs","Kursgruppe"]`, `Raum` = room types, `Person` =
  `isPersonEq:true`) comes from the column annotation and defines *which allocatables the
  column shows*. It stays **inline** and is exactly the shipped
  **`Appointment.allocatables(filter: AppointmentAllocatableFilter)`** field ([PRD 073](../prd/073-graphql-function-equivalents.md)
  Phase 0, 2026-06-19).
- **`CalendarModel` filters → query variable** (`$filter: ReservationFilter!`). The saved
  GUI filter — reservation type (`typeKeyEq`), *neue Regel für* classification rules (the
  generated `where<TypeKey>` predicates, AND/OR/NOT, [PRD 059](../prd/done/059-graphql-typed-where-predicates.md) — richer than a flat type
  list), the resource-tree selection (`allocatableMatching`, [PRD 066](../prd/066-graphql-reservation-allocatable-matching.md)), and the date range
  (`from`/`to`) — is **user state**, carried in the root's `filter: $filter`.

```graphql
query Termine($filter: ReservationFilter!) {
  appointmentBlocks(filter: $filter) {    # $filter ← CalendarModel, e.g.
        # { from, to, typeKeyEq:"Lehrveranstaltung",
        #   whereLehrveranstaltung:{ AND:[{campus:{eq:"KA"}},{year:{eq:2024}}] },   # neue Regel für
        #   allocatableMatching:{ typeKeyIn:["Raum","Teilraum"], idIn:["room-1"] } }  # resource tree
    name: reservation { displayName }
    kurs: allocatables(filter:{ typeKeyIn:["Kurs","Teilkurs","Kursgruppe"] }) { displayName }   # annotation filter — INLINE
    # … start, end, person, raum, duration …
  }
}
```
`ReservationFilter` is the existing schema input (from/to mandatory, `typeKeyEq`,
`allocatableMatching`/`allocatableIdsIn`, generated `whereXxx`, `searchText`/`matchKind`,
`accessibleBy*`). The same saved view runs against any selection; toggling a checkbox/tree
node re-binds `$filter` and re-runs — the view definition (incl. its annotation filters)
is untouched.

### Benchmark verdict

| Legacy table | Reproducible in GraphQL views? | How |
|---|---|---|
| `events` (name, start, lastchanged) | ✅ flat, 0 directives | `reservations(filter:$filter)` + plain field selection |
| `appointments` (Name, Beginn, Ende, Kurs, Person, Raum, Dauer) | ✅ flat, 0 directives | `appointmentBlocks(filter:$filter)` + type-split annotation `allocatables(filter:)` + server-eval `start`/`end`/`duration` |
| `appointments_per_day` (Zeiten, Name, Ressourcen, Personen) | ✅ flat | same `appointmentBlocks` + hidden `day` column (`@group(by:DAY) @hidden`) sorts/groups |

**Open data-layer dependencies** the benchmark relies on ([PRD 073](../prd/073-graphql-function-equivalents.md)): nested
`Appointment.allocatables(filter:)` (shipped 2026-06-19); a server-evaluated `duration`
field; `times` field; the `typeKeyIn` room/course groups (or [PRD 065](../prd/065-graphql-declared-type-groups.md) `typeGroup` instead
of the hard-coded `["Raum","Teilraum",…]` lists).

## 4. What this means

Everything the legacy TableView does is reproducible as a **GraphQL-native view** with
**no expression engine** (compositions evaluate server-side via rapla's own engine) and
mostly **no directives** (convention-driven rendering; directives only for cross-level
ordering/grouping). The legacy config is the acceptance criterion; this doc is the record
so the equivalence isn't re-litigated.
