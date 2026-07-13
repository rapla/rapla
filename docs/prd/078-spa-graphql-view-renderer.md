# PRD 078 — SPA GraphQL view renderer

**Status:** in progress — 2026-06-21; updated 2026-06-22. The Angular consumer of [PRD 074](074-graphql-declarative-views.md)'s
server-side view contract. Carved out of [PRD 074](074-graphql-declarative-views.md) so the
server view model (schema, `@view`, `extensions.view` emission, §12) and the SPA rendering
layer (transport, generic renderer, type-driven variable binding) evolve as separate concerns. The
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
| **GraphQL transport** (`graphql.service.ts`), **generic table renderer** from `extensions.view`, **type-driven variable binding** (`buildVariablesByType`) + **`view.window` date-nav seeding**, component registry, sort/pagination UX, `monaco-graphql` authoring editor | **078** (this PRD — SPA) |
| SavedView persistence, CalendarModel replacement, week/month render-modes, view-switching | **077** |
| Global unified/power-search across views | **077 / 060** |
| Omnibox multisearch (typed, ranked, §12-scoped search resolver) | **081** (server) — the omnibox's `SearchService.search` seam |
| Reservation **editing** forms (mutations) | **026 / 075** |

078 is **downstream of 074's `extensions.view` contract** and an **upstream dependency of
077** (the calendar render-modes reuse the same transport + renderer + selection).

## Current state (2026-07-13)

- **Server data layer — done** (074): `Query.appointmentBlocks(filter)`,
  `AppointmentBlock.{reservation, allocatables(filter:), duration, times, compute(expr:)}`,
  `name(variant: DISPLAY|EXPORT|PLANNING)` (`displayName` `@deprecated`), power search
  (`searchText`/`matchKind`).
- **Server render-meta — done** (074): `ViewMetaInstrumentation` emits `extensions.view`
  (`key`/`title`/`columns`/`groupBy`/`groupFormat`/`renderModes`/`variables`/`window`/`page`);
  the stored-view transport (`StoredViewInterceptor`) and the **server-resolved `view.window`**
  are live.
- **SPA — live, and well past this PRD's original scope.** `graphql.service.ts` (transport) plus
  the generic `ViewHostComponent` render every stored view by name at `/app/views/:viewName`.
  Beyond Phases 1–3 below, the renderer has since absorbed the render-modes and interaction work
  owned by other PRDs: week/day time grid ([PRD 077](077-calendar-model-graphql.md)), month grid
  ([PRD 095](095-month-grid-render-mode.md)), stats projection
  ([PRD 079](079-graphql-grouped-aggregates.md)/[PRD 080](080-typed-entity-stats.md)),
  table selection ([PRD 099](099-spa-table-selection.md)), event sheet + edit
  ([PRD 091](091-spa-reservation-edit-and-availability.md)), row actions + undo
  ([PRD 094](094-spa-main-view-actions-and-popups.md)), print support. Those are tracked in their
  own PRDs; this one owns the transport, the generic renderer, and the binding contract.

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
group?}[], window?: {from, to}, page?: {...} }` (2026-07-12: `inputs` removed — see §Inputs).

## Inputs — type-driven binding + `view.window` ([PRD 074](074-graphql-declarative-views.md) §"Window and inputs directives")

> **Revised 2026-07-12.** The "controls inferred from `extensions.view.inputs`" model below is
> superseded by 074's two-directive decision (`@window` + `@param`). `inputs` is removed as a
> wire concept; the SPA fills variables **by type** and seeds the window from a **server-resolved**
> `view.window`.

The SPA does **not** render per-view input controls in v1. It fills a view's GraphQL variables
from ambient shell state, **by type** (`variable-binder.ts` `buildVariablesByType`):
`ReservationFilter` ← window + resource-selection + owner; `AllocatableFilter` ← selection. The
date window is **not** computed client-side — the server resolves it (from a view's `@window`
directive or the render-mode default) and emits `extensions.view.window { from, to }`, which the
SPA seeds into its date-nav.

`@window`/`@param` are consumed **server-side** (coercion, public→private mapping, the
document/URL reject-undeclared gate — 097). The SPA reads only `view.window` + the variable
type signature. **SPA input controls (`ParamControl`: resource-picker, search box, …) are
deferred** — added when a view genuinely needs an input with no shell source (074 §"Deferred").

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
acceptable. On first visit (no URL params) the SPA seeds the initial window from the
server-resolved `extensions.view.window` (2026-07-12 — resolved server-side from `@window`/the
mode default; no client-side anchor computation).

## Plan — phased

- [x] **Phase 1 — transport + generic table.** `graphql.service.ts` (plain `HttpClient.post`, no
      Apollo); generic Material-table renderer driven by `extensions.view.columns`
      (order/header/join/format/type/hidden); the stored-view consumer path
      (`executeView(name)` + `storedView` extension flag); route `/app/views/:viewName`
      (`ViewHostComponent`). No code-shipped fallback `ViewMeta` — the server is the single
      source of truth for what a view looks like.
- [x] **Phase 2 — server-resolved window + type-driven binding.** *(2026-07-12)*
  - [x] The date-nav seeds from `extensions.view.window`; the client-side anchor resolver
        (`view-inputs.ts` — `resolveAnchorOffset`/`resolveWindowFromInputs`) is **deleted**, and
        `ViewMeta.inputs` is replaced by `ViewMeta.window`.
  - [x] Variable binding is **type-driven** (`variable-binder.ts` `buildVariablesByType`):
        `ReservationFilter` ← window + resource-selection + owner; `AllocatableFilter` ← selection.
  - [x] `@window`/`@param` are consumed **server-side only** — the SPA reads neither. **`@param`
        input controls (`ParamControl`: resource-picker, search box, …) are deferred**
        ([074 § Window and inputs directives](074-graphql-declarative-views.md#window-and-inputs-directives-decided-2026-07-12)).
- [ ] **Phase 3 — grouping + component registry + pagination.** *(partially landed)*
  - [x] **Client-side** day/weekday sectioning (`graphql/weekday-grouping.ts` `groupByWeekday`,
        buckets Montag→Sonntag). The server has **no** grouping directive (`@group`/`@aggregate`
        were removed from 074 on 2026-06-21 → [PRD 079](079-graphql-grouped-aggregates.md); the
        render directives are `@column`/`@hidden`/`@join`/`@flatten` only), so day-grouping is a
        pure SPA renderer concern.
  - [x] Sort-on-header-click (Material `matSort`; disabled in grouped mode).
  - [ ] `ngComponentOutlet` cell-component registry (safe allowlist, no raw HTML) — **not built**.
  - [ ] Pagination UX (next/prev / infinite scroll) over the server's `offset` / `view.page`
        meta — **not built**.
- ~~**Phase 4 — authoring.** `monaco-graphql` query editor + live SPA preview.~~ **DROPPED
      2026-07-13.** It contradicted two locked decisions — [PRD 074](074-graphql-declarative-views.md)
      § "Admin authoring — GraphiQL + save/load" (*the shipped GraphiQL is the authoring surface;
      no separate view-editor is built*) and [PRD 097](097-event-html-templates-mustache.md)
      Phase 4's delivery shape (*authoring tools are static CDN pages, explicitly **not** part of
      the Angular SPA, to avoid the Monaco-in-Angular embedding cost*). It was written 2026-06-21,
      before either landed, and was never reconciled. Both halves of what it wanted already exist:
  - **Schema-aware editing + validation** → `/graphiql`, which *is* the purpose-built GraphQL
    editor. The `@param`/`@window` affordances (`into` completion, red markers on a bad path) land
    **there** — feasible because GraphiQL 5.2.1 is itself Monaco-based. See
    [074 § Window and inputs directives](074-graphql-declarative-views.md#window-and-inputs-directives-decided-2026-07-12)
    → the `into` authoring affordance.
  - **"Live SPA preview"** → already free: the SPA renders **any** stored view by name at
    `/app/views/:viewName`. Save in GraphiQL, open the URL. Nothing to build.

  (The *template*-authoring UI is a different thing and is done — 097 Phase 4,
  `static/template-editor/`, the presentation layer's own static page.)

## Tests (AGENTS.md §10 pyramid)

- **Tier 5 (Vitest, no TestBed)** — `graphql.service` request/response + error unwrap;
  `extensions.view` → column-descriptor mapping; type-driven variable binding
  (`buildVariablesByType`); list-join + datetime formatting.
- **Tier 6 (TestBed)** — the table component renders columns in `extensions.view` order,
  joins list cells, hides `hidden` columns, renders day sections via client-side
  `groupByWeekday` (no `@group` directive — see Phase 3).
- **Tier 7 (Playwright, sparing)** — one end-to-end: log in → open the `appointments` view →
  rows render for the default window. Critical-path only.
- **XSS (§"XSS hardening" in 074)** — never `[innerHTML]`/`bypassSecurityTrustHtml`; cells
  are Angular text interpolation (auto-escaped); the component registry is a safe allowlist.

## Dependencies

- **074** — the `@view` directive + `extensions.view` emission (columns/title/sort/window/
  page) must land before Phase 1 can render generically. Until then, a throwaway hardcoded
  column list can prove the transport, but is not the deliverable.
- **072** — cookie auth + the refresh interceptor (already shipped; the transport relies on it).
- **angular-frontend skill / [PRD 026](026-angular-frontend.md)** — the SPA build/test/codegen conventions this renderer
  follows.
