# PRD 078 — SPA GraphQL view renderer

**Status:** in progress — 2026-06-21; updated 2026-06-22. The Angular consumer of [PRD 074](074-graphql-declarative-views.md)'s
server-side view contract. Carved out of [PRD 074](074-graphql-declarative-views.md) so the
server view model (schema, `@view`, `extensions.view` emission, §12) and the SPA rendering
layer (transport, generic renderer, control inference) evolve as separate concerns. The
generic `ViewHostComponent` (one component renders every server-declared view by name),
type-driven variable binding, and the **scope gate** (§"Scope") are built; see that section
for the selection model and what remains (`user`/`group` chips, own-user pin).

## Goal

Render [PRD 074](074-graphql-declarative-views.md)'s table views in the Angular SPA (`rapla-angular/`, served at `/app/`):
a **generic renderer** that turns an `appointmentBlocks`/`reservations` GraphQL response
+ its `extensions.view` render-meta into a table — **without per-view client code**. First
target: the dhbw **`appointments`** table (Name, Beginn, Ende, Kurs, Person, Raum, Dauer)
over recurrence blocks.

## Boundary — who owns what

| Concern | PRD |
|---|---|
| `@view` directive, composition-field generator, **`extensions.view` emission**, §12 execution, save-time validation, sort/pagination semantics | **074** (server) |
| **GraphQL transport** (`graphql.service.ts`), **generic table renderer** from `extensions.view`, **control inference/rendering** from `extensions.view.inputs`, component registry, sort/pagination UX, `monaco-graphql` authoring editor | **078** (this PRD — SPA) |
| SavedView persistence, CalendarModel replacement, week/month render-modes, view-switching | **077** |
| Global unified/power-search across views | **077 / 060** |
| Omnibox multisearch (typed, ranked, §12-scoped search resolver) | **081** (server) — the omnibox's `SearchService.search` seam |
| Reservation **editing** forms (mutations) | **026 / 075** |

078 is **downstream of 074's `extensions.view` contract** and an **upstream dependency of
077** (the calendar render-modes reuse the same transport + renderer + selection).

## Current state (2026-06-21)

- **Server data layer — done** (074, committed): `Query.appointmentBlocks(filter)`,
  `AppointmentBlock.{reservation, allocatables(filter:), duration, times, compute(expr:)}`,
  `name(variant: DISPLAY|EXPORT|PLANNING)` (`displayName` `@deprecated`), power search
  (`searchText`/`matchKind`). The **`appointments` query runs end-to-end on the wire** —
  verifiable in GraphiQL at `/graphiql`.
- **Server render-meta — in flight** (074): `@view` directive + `extensions.view` emission
  (columns/title/sort/inputs) not yet present.
- **SPA — greenfield.** No GraphQL transport, no renderer. `reservations.component` still
  uses the legacy `/api/table/*` REST ([PRD 030](030-server-side-view-rendering.md), deprecated). This PRD is all of it.

## Architecture — the connection is cheap (cookie auth)

The whole transport is a plain `HttpClient.post` — **no Apollo, no token wiring, no CORS**:

- **Endpoint:** `POST /api/graphql` (`spring.graphql.http.path`), body `{ query, variables }`.
- **Auth — free.** The SPA holds **no token** ([PRD 072](072-server-side-login-dialog.md) Phase 4): the JWT lives in an
  HttpOnly `access_token` cookie the browser auto-sends same-origin. GraphQL calls carry it
  with **no `Authorization` header**, and the existing `auth.interceptor` already handles
  **401 → `/api/auth/refresh` → replay** for any `/api/*` request.
- **Dev proxy — already configured.** `proxy.conf.js` proxies `/api` (and `/graphiql`) to
  `:8051` with `cookieDomainRewrite`, so GraphQL works in the `ng serve` split out of the box.

```ts
// graphql.service.ts — the entire new transport
interface GqlResponse<T> {
  data: T;
  errors?: { message: string; extensions?: { code?: string } }[];
  extensions?: { view?: ViewMeta };          // ← PRD 074's render-meta
}
query<T>(document: string, variables: Record<string, unknown>) {
  return this.http.post<GqlResponse<T>>('/api/graphql', { query: document, variables });
}
```

`ViewMeta` is **hand-typed** (it is runtime `extensions`, not in the GraphQL schema, so no
codegen reaches it): `{ key, title, columns: {alias, header, type, sort?, join?, hidden?,
group?}[], inputs: {name, control, default?}[], page?: {...} }`.

## Inputs = query variables, controls inferred ([PRD 074](074-graphql-declarative-views.md) §"Inputs")

A view's inputs **are** its GraphQL variables (`$filter`, `$sort`). The SPA renders one
**control per input**, by convention (name + type): `from`+`to` → date-range; a search
string → combobox; an id-list → resource picker. The inference rule is 074's; **078 renders
the controls.** Two transport shapes:

1. **SPA holds the query** → it reads the variable list from the query text itself.
2. **Server holds the query** (`executeView(name)`) → the SPA can't see the text, so the
   server must report the input-meta in **`extensions.view.inputs`** (parallel to `columns`).

## First slice — `appointments` table, **no power search**

```graphql
query appointments($filter: ReservationFilter!) {
  appointmentBlocks(filter: $filter) {
    name:   reservation { displayName }
    start  end
    kurs:   allocatables(filter:{ typeKeyIn:["Kurs","Teilkurs","Kursgruppe"] }) { displayName }
    person: allocatables(filter:{ isPersonEq:true }) { displayName }
    raum:   allocatables(filter:{ typeKeyIn:["Raum","Teilraum","virtuellerRaum"] }) { displayName }
    duration
  }
}
```

- **Server side: 100% ready** — all six fields resolve today.
- **Selection for the first cut = the date window only** (`$filter.from/to`). The table shows
  every caller-visible block in the window (§12 carries it). **No search box, no resource
  picker, no tree** — deferred.
- **SPA work:** `graphql.service.ts`; a generic table component (`mat-table`/`cdk-table`)
  driven by `extensions.view.columns` (list cells joined by `, `, datetime format); a
  date-range control bound to `$filter.from/to`; swap it into the reservations route.

### Why power search is *not* in the first slice (the conflict)

"Power search as both event-filter **and** resource-select" wants to be a **global,
cross-view selector** — which collides with [PRD 074](074-graphql-declarative-views.md) **Locked Decision #6** (views are
independent; each declares **its own** inputs; the uniform shared-input / selection-transfer
machinery was deliberately dropped). It also can't be expressed today: the `reservations`
resolver **ANDs** `searchText` with `allocatableIdsIn`/`allocatableMatching` (intersection),
not the OR a unified relevance search needs, and the unified `Query.search` ([PRD 060](060-graphql-mcp-foundations.md)) isn't
built. So:

- **Event-name filter** (`ReservationFilter.searchText`, ✅ honored) — a *per-view* input;
  can be added to a view later as one `inputs` row (table-name search box).
- **Resource selection via search** (`allocatables(filter:{searchText})` → picks →
  `allocatableIdsIn`, ✅ honored) — a *per-view* input (search-driven picker, the lightweight
  alternative to a tree).
- **Global unified power-search across views** → **PRD [077](077-calendar-model-graphql.md) / [060](060-graphql-mcp-foundations.md)**, not here.
- **Omnibox multisearch (the GraphQL search resolver behind `SearchService`)** → **[PRD 081](081-graphql-omnibox-multisearch.md)**.

## Scope — a view only queries within a selection (performance)

**Added 2026-06-22.** A rendered view fires **no** `executeView` query until a **scope** is
set. Scope = at least one *scoping* chip in the filter rail; a chip's kind is `resource`
(allocatable), `group`, or `user` — an `event` chip is a navigation target, not a scope. With
no scope the view shows a hint ("Wähle eine Ressource, Gruppe oder Person als Scope") and
issues **zero** GraphQL view requests (`listViews` for the nav still runs — it is cheap).

**Why.** An unscoped view query is a full-window firehose: the `reservations` resolver caps
at 500 rows but still scans the whole `from`/`to` window across *all* resources (a [PRD 035](done/035-graphql-foundations.md) hot
path). Requiring a scope makes every view query bounded by construction. "Remove all scope
first" = the default state is empty; scope exists **only** as explicit chips, never implicitly
from the date window.

**Scope → variables** (type-driven, [PRD 074](074-graphql-declarative-views.md) §"Inputs"/variables; `variable-binder.ts` fills
each declared variable by its GraphQL type, not its name):

| Chip kind | Binds into |
|---|---|
| `resource` | `ReservationFilter.allocatableMatching.idIn` (+ a second `AllocatableFilter.idIn` for aggregation/pivot views) |
| `user` | `ReservationFilter.ownerEq` — the user's own events |
| `group` | `ReservationFilter.accessibleByGroup` ([PRD 069](069-graphql-resource-access-read-api.md), admin-scoped) |

**Own user pinned (quick "my events").** The logged-in user is permanently pinned at the
top-left of the resource selection — one click away from a `user`-scope chip (`ownerEq:<me>`),
so a planner sees all their own events immediately. Requires the user **id** in the SPA
identity: extend `GET /api/auth/me` (`IdentityResponse`) with `id` (it carries
username/name/roles today, no id).

**Finding users** — typing a name in the omnibox to add a `user` scope chip — is the **find**
half, owned by **[PRD 081](081-graphql-omnibox-multisearch.md)** (add a `USER` search kind / `UserHit`).

**Status (2026-06-22):** scope gate + hint + `resource` binding **built** (`ViewHostComponent`
`hasScope`, regression-tested). Remaining: `user`/`group` chip kinds, the own-user pin, and the
`id` on `/api/auth/me`.

## Execution transport & routing (locked 2026-06-21 — see [PRD 074](074-graphql-declarative-views.md) §"View loading")

**Open question resolved: server-merge (option 1).** Full design in [PRD 074](074-graphql-declarative-views.md)
§"View loading — execution transport". SPA summary:

- **Consumer path** — `POST /api/graphql` with `{ operationName: viewName, variables }` (no
  `query`). Server looks up stored view, merges `from`/`to` defaults if absent, executes.
  Client never holds query text.
- **Authoring path** — `POST /api/graphql` with `{ query: document, variables }` (unchanged).

```ts
// graphql.service.ts — two methods, one HttpClient
executeView<T>(viewName: string, variables: Record<string, unknown>): Observable<GqlResponse<T>>
query<T>(document: string,      variables: Record<string, unknown>): Observable<GqlResponse<T>>
```

**Routing:** `/app/views` (lazy `listViews` → view list) and `/app/views/:viewName` (execute
+ render). Date controls (`from`/`to`) are synced to URL query params so browser back/forward
moves through date windows (`pushState` on each navigation). Complex filters (resource tree,
`searchText`, `where` predicates) stay in component state — lost on reload, which is
acceptable. On first visit (no URL params) the SPA uses `default` sentinels from
`extensions.view.inputs` to compute the initial window client-side (no extra round-trip).

## Plan — phased

1. **Phase 1 — transport + render the `appointments` table.** `graphql.service.ts`; generic
   `cdk-table` renderer from `extensions.view.columns` (order/header/join/format); date-range
   control → `$filter.from/to`; route swap. Depends on 074's `extensions.view` landing.
2. **Phase 2 — control inference + per-view inputs.** Render controls from
   `extensions.view.inputs`; the resource-search picker (`allocatables(filter:{searchText})`
   → `allocatableIdsIn`) and the per-view name-search box; the date-window pre-fill decision.
3. **Phase 3 — grouping + component registry.** **Client-side** day/weekday sectioning —
   the server has NO grouping directive (`@group`/`@aggregate` were removed from 074 on
   2026-06-21 → [PRD 079](079-graphql-grouped-aggregates.md); render directives are `@column`/`@hidden`/`@join`/`@flatten` only).
   Day-grouping is a pure SPA renderer concern: `groupByWeekday()` over the flat `start`
   column (`graphql/weekday-grouping.ts`), buckets ordered Montag→Sonntag. `ngComponentOutlet`
   cell-component registry (safe allowlist, no raw HTML); sort-on-header-click → `$sort`;
   pagination UX (next/prev / infinite scroll).
4. **Phase 4 — authoring.** `monaco-graphql` query editor + live SPA preview over the
   validator (server save-time validation is 074).

## Tests (AGENTS.md §10 pyramid)

- **Tier 5 (Vitest, no TestBed)** — `graphql.service` request/response + error unwrap;
  `extensions.view` → column-descriptor mapping; control inference from `inputs`; list-join
  + datetime formatting.
- **Tier 6 (TestBed)** — the table component renders columns in `extensions.view` order,
  joins list cells, hides `hidden` columns, renders day sections via client-side
  `groupByWeekday` (no `@group` directive — see Phase 3).
- **Tier 7 (Playwright, sparing)** — one end-to-end: log in → open the `appointments` view →
  rows render for the default window. Critical-path only.
- **XSS (§"XSS hardening" in 074)** — never `[innerHTML]`/`bypassSecurityTrustHtml`; cells
  are Angular text interpolation (auto-escaped); the component registry is a safe allowlist.

## Dependencies

- **074** — the `@view` directive + `extensions.view` emission (columns/title/sort/inputs/
  page) must land before Phase 1 can render generically. Until then, a throwaway hardcoded
  column list can prove the transport, but is not the deliverable.
- **072** — cookie auth + the refresh interceptor (already shipped; the transport relies on it).
- **angular-frontend skill / [PRD 026](026-angular-frontend.md)** — the SPA build/test/codegen conventions this renderer
  follows.
