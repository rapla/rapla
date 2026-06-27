# PRD 081 — GraphQL omnibox multisearch

**Status:** Phase 1 LANDED — 2026-06-21 (RESOURCE + EVENT). The server-side GraphQL contract for the SPA omnibox's
**typed, ranked, §12-scoped search across entity kinds** (resources, events, occurrences,
groups). Carved out so the server search resolver and the SPA omnibox evolve in parallel
against a fixed seam. Downstream consumer + the stable seam: [PRD 078](078-spa-graphql-view-renderer.md)
(SPA). Related: [PRD 028](028-angular-power-search.md) (resource `searchText` — already shipped),
[PRD 077](077-calendar-model-graphql.md) (groups = `ClassificationFilter[]`, saved views),
[PRD 074](074-graphql-declarative-views.md) (views the omnibox navigates to).

## Goal

One GraphQL surface the omnibox calls to turn a free-text term into **typed result rows**
the SPA renders with per-kind actions. The omnibox is the SPA's single entry point: it
**finds** resources/events/occurrences/groups; the ResourceSelection list **steps**; the
chip rail **filters** the rendered view. This PRD owns only the **find** half — the
server search resolver.

The SPA seam already exists and must NOT change shape when the server lands:

```ts
// rapla-angular/src/app/search/search.service.ts
search(term: string): Observable<SearchResultGroup[]>
```
```ts
type SearchResultKind = 'resource' | 'event' | 'occurrence' | 'group' | 'savedView';
interface SearchResult  { id: string; kind: SearchResultKind; label: string;
                          sublabel?: string; color?: string; count?: number; }
interface SearchResultGroup { kind: SearchResultKind; heading: string; results: SearchResult[]; }
```

**Actions are a CLIENT concern** — the SPA derives the action buttons (Belegung / + / ↵ /
✏️ / in Liste laden) from `kind` by convention. The server returns **kind + data**, never
an action list. This keeps the server contract lean and the action taxonomy in one place
(the omnibox).

## Current state (2026-06-21)

- **Resource search SHIPS:** `Query.allocatables(filter: { searchText, matchKind, limit })`
  (PRD 028) — §12-scoped, returns `id / name / isPerson / classification.typeKey`. The SPA's
  `SearchService` **already fans out to this today** (resources only): it maps each hit to a
  `resource` result with `sublabel = typeKey`. Verified live against the dhbw store.
- **Everything else is missing:** no event/occurrence search in the omnibox, no group results,
  no single ranked surface. That is this PRD.

## What multisearch needs — federated vs unified

Two ways to satisfy the seam. **Recommendation: unified resolver.**

| | Federated fan-out (SPA-side) | **Unified resolver (this PRD)** |
|---|---|---|
| Server work | none | new `search(...)` resolver |
| Round-trips | N parallel queries, SPA merges | 1 |
| §12 boundary | N boundaries (each root re-checks) | 1 audited boundary |
| Cross-type ranking | impossible (no shared score) | server ranks all kinds together |
| Group results | impossible (no root) | first-class |

Federated works for resources-only **today** (already wired) and is the fallback if the
unified resolver slips — but it cannot do groups or cross-type ranking, so it is a bridge,
not the target.

## Recommended schema

```graphql
extend type Query {
  "Omnibox multisearch. Ranked, §12-scoped, capped per kind."
  search(query: String!, kinds: [SearchKind!], limit: Int = 20): SearchResults!
}

enum SearchKind { RESOURCE EVENT OCCURRENCE GROUP USER }   # SAVED_VIEW later (PRD 077)

type SearchResults { groups: [SearchGroup!]! }        # already kind-bucketed + ordered

type SearchGroup {
  kind: SearchKind!
  heading: String!                                    # "Ressourcen", "Veranstaltungen", …
  hits: [SearchHit!]!
}

interface SearchHit {
  id: ID!
  label: String!                                      # primary display text
  sublabel: String                                    # type/context line
  score: Float!                                       # relevance, desc
}

type ResourceHit implements SearchHit {               # → filter-replace / filter-add
  id: ID!  label: String!  sublabel: String  score: Float!
  allocatable: Allocatable!                            # carries the id the chip filters by
}

type EventHit implements SearchHit {                  # → navigate(first occ) / filter-add / edit
  id: ID!  label: String!  sublabel: String  score: Float!
  reservation: Reservation!
  firstOccurrenceStart: LocalDateTime                 # for navigate
}

type OccurrenceHit implements SearchHit {             # → navigate / edit
  id: ID!  label: String!  sublabel: String  score: Float!
  start: LocalDateTime!
  reservation: Reservation!
}

type GroupHit implements SearchHit {                  # → load-group (materialize members)
  id: ID!  label: String!  sublabel: String  score: Float!
  count: Int!                                         # number of §12-READABLE members
  memberFilter: AllocatableFilter!                    # SPA passes this to allocatables(filter:)
}

type UserHit implements SearchHit {                  # → filter-add (a USER scope chip)
  id: ID!  label: String!  sublabel: String  score: Float!
  user: User!                                         # carries the id the chip scopes by (ownerEq)
}
```

### `USER` kind — users as scope chips (added 2026-06-22)

A `UserHit` lets the planner type a person's name in the omnibox and add a **`user` scope
chip** to the rail. The chip carries the user **id**; the SPA binds it into
`ReservationFilter.ownerEq` (the user's own events) — see [PRD 078](078-spa-graphql-view-renderer.md)
§"Scope". §12: search must only surface users the caller may see (mirror the `users(filter:)`
visibility — `canAdminUser` / self), and the per-kind count must not leak hidden users. The
own logged-in user does **not** need search — it is pinned in the SPA selection (PRD 078); this
kind covers finding *other* users (admin/planner scoping to someone else's events, which on the
read path is `ownerEq` for owned events, or PRD 069 `accessibleByUsername` for access-scoped).

### Why `GroupHit.memberFilter` (the hard part)

Groups are the reason multisearch needs the server. A group is `ClassificationFilter[]`
(PRD 077) — three flavours: **type-groups** ("alle Räume"), **hierarchical**
(Gebäude → seine Räume, Studiengang → seine Kurse), **self-defined** filters. Expanding a
group to its members for "in Liste laden" needs **deployment-specific relation knowledge**
(`Raum.Gebaeude`, `Kurs.Studiengang`) the SPA must not hardcode.

Solution: `GroupHit` carries an opaque `memberFilter: AllocatableFilter` (the same input
`allocatables(filter:)` already takes — `typeKeyIn` + per-type `whereXxx` + `idIn`). The SPA
does NOT interpret it; on "in Liste laden" it calls `allocatables(filter: hit.memberFilter)`
to materialize the §12-readable members into the ResourceSelection list. Relation knowledge
stays server-side; the SPA stays generic. `count` is the readable-member count (single source
of truth for "Räume C-Bau (12)").

### Windowless event/occurrence search

`reservations`/`appointmentBlocks` require a mandatory `from`/`to`. Omnibox known-item search
("find the event by name, regardless of date") has no window. **Decided (2026-06-21, OQ1): (b)
a true windowless name index.** EVENT search matches reservation names regardless of date — a
known-item lookup must find an event whatever its date. This is a new code path outside the
windowed `reservations` evaluator (the resolver matches over all §12-readable reservations by
name, not via a from/to query).

## §12 — non-negotiable (load the `data-leak-prevention` skill)

The omnibox surfaces **existence**; it is a textbook leak surface. Every rule from AGENTS.md
§12 fires:

- Every hit permission-filtered at the resolver boundary — do not trust the search index.
  **RESOURCE** gates on `PermissionController.canRead`; **EVENT** gates on `canModify` (editable-only,
  see Plan Phase 1) — a stricter gate, since event rows are for editing.
- **No existence leak:** a term that matches only hidden entities returns the SAME empty result
  as a term that matches nothing. No counts, no headings betraying hidden matches.
- `GroupHit.count` counts **only readable members**; `memberFilter` re-evaluates under the
  caller's scope (a group of 30 rooms shows "12" to a user who sees 12).
- An `OccurrenceHit`/`EventHit` is dropped **wholesale** unless every entity it names
  (reservation + referenced allocatables in its label/sublabel) is readable — never strip fields.
- **Mandatory tier-3 MockMvc leak test before merge:** non-admin caller, term matching a mix of
  visible + hidden entities, assert the response is byte-identical to the visible-only subset
  (and to the all-hidden / no-match case). Status, body, latency bucket.

## Ranking

Per-kind ordering by `score` desc: `PREFIX` > `SUBSTRING` > `FUZZY` match; tie-break by
type priority (configurable: rooms before persons before courses, deployment default) then
name. `limit` caps **per kind** (default 20); the resolver must `log` when it truncates so the
SPA can show "… N weitere". **Decided (OQ2): kind buckets, not cross-kind flat ranking** — the
seam stays `SearchResults { groups: [SearchGroup] }`, each bucket ranked internally. Cross-kind
ranking is a later refinement. **Decided (OQ3): fuzzy on by default for RESOURCE** in the omnibox
— broader recall; FUZZY hits rank below PREFIX/SUBSTRING so the leak surface stays reasoned via
score order. **EVENT matching is PREFIX/SUBSTRING only, never FUZZY** — the windowless reservation
scan is over a potentially huge candidate set, and per-candidate Levenshtein would be O(n·m); fuzzy
recall is not worth the cost on that path.

### Performance — naive scan first, indices second

Phase 1 deliberately does the **naive full scan** (RESOURCE reuses the PRD 028 evaluator; EVENT
scans all §12-readable reservations by name). **We measure performance after the implementation
lands** against the dhbw store, then decide on a name index (a second step / follow-up) for
effective matching if the scan is too slow. Do not pre-optimize with an index in Phase 1.

## Plan — phased (server)

1. **Phase 1 — unify resources + events + users. ✅ DONE 2026-06-21 (RESOURCE+EVENT), USER added
   2026-06-24.** `search(query, kinds, limit)` over RESOURCE (reuses the PRD 028 `allocatables`
   evaluator, FUZZY) + EVENT (windowless name scan over `CachableStorageOperator.getReservations()`,
   SUBSTRING-only, edit-gated) + USER (name/username FUZZY scan, §12 = self + `canAdminUser`).
   Ranking + per-kind cap + truncation log. RESOURCE/EVENT/USER are all in the **default** kind set.
   The SPA flipped `SearchService` from the resources-only fan-out to `search(...)` — seam unchanged.
   Implementation:
   - `SearchGraphQLController` (resolver + `SearchResults`/`SearchGroup`/`SearchHit`/`ResourceHit`/
     `EventHit`/`UserHit` output types); `SearchHit` TypeResolver in `GeneratedClassificationWiring`.
   - `CachableStorageOperator.getReservations()` (new windowless accessor; impl in
     `LocalAbstractCachableOperator` → `cache.getReservations()`).
   - Schema: `search` query + `SearchKind` enum (RESOURCE/EVENT/USER/…) + the result/hit types.
   - SPA: `search.service.ts` calls `search(query, limit)`, maps `groups` → `SearchResultGroup[]`,
     derives actions per kind (`user` → `filter-add` scope chip). `'user'` added to
     `SearchResultKind`; omnibox `run()` routes `user`/`event` hits to the matching `FilterStore`
     chip kind (no longer coerces non-event → resource). `FilterStore` already had the `user` kind.
   - **USER kind (2026-06-24):** lets the planner type a person's name/login and add a `user` scope
     chip (the SPA binds `UserHit.id` into `ReservationFilter.ownerEq`). §12 mirrors `users(filter:)`
     visibility — self + `canAdminUser`; verified red-on-leak (a non-admin must not see a user she
     can't admin). Label = display name (person name, else username), sublabel = username.
   - **GUI: a found user behaves like a resource (2026-06-24).** User rows carry the same actions as
     resources (`filter-replace` "Belegung" + `filter-add` "+"), and acting on one **pulls it into
     the ResourceSelection "Zuletzt" list** so it can be re-selected. To keep the scope correct,
     `ResourceItem` gained an optional `kind` (`resource`|`user`, default `resource`) and
     `ResourceSelection.step()` builds the `FilterStore` entry with that kind — so stepping a user
     row creates a `user` (ownerEq) chip, not a resource filter. (The logged-in user stays pinned at
     the top via `scopeToMe`; this covers finding *other* users.)
   - **EVENT gate = `canModify`, not `canRead` (2026-06-21 decision):** the omnibox surfaces only
     events the caller can **edit**. Event rows carry edit/navigate actions, so a non-editable
     event is noise *and* a wider leak surface; restricting to editable events is both better UX
     and a stricter §12 posture. RESOURCE stays read-gated (resource rows only filter the view).
   - **§12 leak tests are genuine red-on-leak guards.** RESOURCE: monty's search bucket must equal
     her §12-readable allocatables (Room A66 is hideable). EVENT: monty CAN read homer's events but
     CANNOT edit them, so the edit-gated EVENT bucket is empty — verified red when the gate is
     weakened to `canRead` (homer's events leak through). The earlier "fixture can't hide an event"
     limitation is resolved by gating on edit instead of read.
2. **Phase 2 — groups.** `GroupHit` + `memberFilter` + readable `count`: type-groups first,
   then hierarchical (Gebäude/Studiengang) via the existing `whereXxx` evaluators.
3. **Phase 3 — occurrences + SAVED_VIEW.** OCCURRENCE kind (block name match); SAVED_VIEW once
   PRD 077 persists views.

## Open questions

- ~~**OQ1** — Default window for EVENT/OCCURRENCE: server default vs windowless index?~~
  **RESOLVED 2026-06-21 → windowless name index** (see "Windowless event/occurrence search").
- ~~**OQ2** — Re-rank across kinds into ONE list, or keep kind buckets?~~ **RESOLVED → kind
  buckets** (current seam preserved; see Ranking).
- ~~**OQ3** — Fuzzy on by default, or opt-in?~~ **RESOLVED → fuzzy on by default** (FUZZY ranks
  below PREFIX/SUBSTRING; see Ranking).
- **OQ4** — Group identity: what is `GroupHit.id` (stable key for a hierarchical group)? Needed
  if groups become favouritable / saveable (PRD 077). *Deferred to Phase 2 (groups).*

## Tests

- **Tier 1/2 (server)** — ranking order; per-kind cap + truncation log; `memberFilter`
  round-trips through `allocatables` to the right members; default-window application.
- **Tier 3 (MockMvc) — the leak test is mandatory** (see §12 above); plus the kind-bucket shape
  + `count`-equals-readable-members.
- **SPA (already green)** — `SearchService` maps the response to `SearchResultGroup[]`; the
  omnibox derives actions per kind. When Phase 1 lands, only `SearchService`'s query text +
  mapping change; the omnibox component and its tests do not.
