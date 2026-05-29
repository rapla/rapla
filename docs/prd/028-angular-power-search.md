# PRD 028 — Angular power search (single-calendar shell)

**Status:** in-progress — GraphQL substrate Phase 1 SHIPPED 2026-05-29 (see §"GraphQL substrate augmentations"). SPA-side consumption (Apollo client, codegen, calendar shell, tier composition, hasConflicts badge UX) NOT STARTED — it's a multi-day delivery slot, schedule when ready.
**Author:** Christopher Kohlhaas (with AI assistance)
**Created:** 2026-05-11

## Goal

Define a **single-calendar shell** for the Angular frontend
(PRD 026) with a persistent **power-search box** above one main
calendar. The search box is the primary way to find allocatables,
reservations, and conflicts. Results are ranked by **how relevant
they are to what's currently on screen** — selected resources and
the visible time window — so finding something doesn't require
leaving the calendar.

This PRD is about the **search UX, ranking model, and interaction
verbs**. Wire format and backend endpoints land in a follow-up
server PRD once the UX is locked.

## Scope

In scope:

- Single-calendar shell layout: search-bar at top, calendar below.
- Power-search box: ranked result list across three entity types
  (allocatable, reservation, conflict).
- Ranking model (priority tiers — see below).
- Result-row verbs: select / unselect an allocatable, navigate to
  a reservation's first occurrence, "switch selection" affordance.
- Recency boost: recently searched / visited entities float up.
- Behaviour for reservations that **don't** touch any currently-selected
  allocatable (the "orphan" case).

Out of scope:

- The reservation-edit flow itself (PRD 026 Phase 2+).
- Admin views, plugin UIs, user / category administration.
- The top-level cross-domain `Query.search(text:, scope:)` root + MCP
  search tool — those stay in
  [PRD 060 — Discovery, Compute, MCP Transport](060-graphql-mcp-foundations.md)
  because they serve a different consumer (AI agents).
- (Server-side search augmentations for the SPA's tier model — the
  per-type / per-group `searchText` + `matchKind` args — moved INTO
  scope per the 2026-05-29 consolidation. See §"GraphQL substrate
  augmentations" below.)
- Conflict detail UI; this PRD only ranks conflicts as a result
  type, clicking one defers to a future conflict-detail view.

## Ranking model

The result list is partitioned into **priority tiers**. Within each
tier, results are sorted by string-match quality (prefix > substring >
fuzzy) and then by recency. Tiers are visually separated (subheading
or thin divider); search-as-you-type re-ranks on every keystroke.

### Allocatables (resources)

| Tier | Definition | Visual |
|---|---|---|
| **A1 — Selected** | Currently in the calendar's selection (drawn as a row / colour band) | Highlighted; checkbox shown checked |
| **A2 — Recent** | In the user's "recently visited / searched" list, not currently selected | Mid-emphasis; checkbox unchecked |
| **A3 — Other** | Match the query, neither selected nor recent | Low-emphasis; checkbox unchecked |

### Reservations (events)

| Tier | Definition | Visual |
|---|---|---|
| **E1 — Visible** | At least one appointment block falls inside the **currently visible time window** AND touches at least one **currently selected allocatable** | Highlighted; "jump to block" is the default action |
| **E2 — Selected resource, off-screen** | Touches a selected allocatable but **no block is inside the visible window** | Mid-emphasis; default action is "navigate to first start" |
| **E3 — Recent** | Recently visited reservation, not in E1/E2 | Mid-emphasis; default action "navigate to first start" |
| **E4 — Orphan** | Matches the query but touches **none** of the selected allocatables | Low-emphasis with a badge ("not in current selection") — see *orphan handling* below |

### Conflicts

Conflicts are pairs of reservations. Rank as the *worst* of the two
component reservations (i.e. if one side is E4, the conflict row is
E4). Conflict rows always carry the conflict badge so users can
distinguish them from plain reservation matches.

### Recency boost

A bounded ring of "recently visited" IDs (allocatable, reservation)
per user, kept in browser storage (e.g. `localStorage`, ~50 entries
each, FIFO eviction). Updated on:

- explicit selection of an allocatable from the search,
- opening a reservation (edit, detail, or "jump to") from the search,
- direct click on a reservation block in the calendar.

Recency only **lifts a result into tier A2 / E3**; it does not
override the higher tiers. (A selected resource is always A1, even
if it was selected ten seconds ago.)

## Interaction verbs

### On an allocatable result row

- **Primary**: checkbox toggle (select / unselect). Re-renders the
  calendar inline; search results stay open and re-rank
  (the selection delta moves rows between A1 and A2/A3).
- **Switch selection** (modifier-click, e.g. shift-click, or a row
  side-button): replace the current selection with **just this
  one** allocatable. Avoids the "uncheck-everything-then-check"
  shuffle. Probably the highest-frequency power-user verb.
- **Open detail** (secondary): full allocatable view (out of scope
  for v1 — link to future PRD).

### On a reservation result row

- **E1 (visible)**: default click scrolls the calendar to the
  block and flashes it. The reservation is **already on screen** —
  this is a "show me where" verb.
- **E2 / E3 (off-screen, but touches a selected resource)**:
  default click moves the calendar viewport to the **first
  appointment's start date** and selects that occurrence. Selection
  is preserved.
- **E4 (orphan)**: see below.
- **Open edit** (secondary, modifier-click or row side-button):
  open the reservation-edit dialog (PRD 026 Phase 5).

### On a conflict result row

- Default click: same as the underlying reservation, but the calendar
  draws the conflict's overlap interval emphasized.
- Secondary: open conflict-detail (deferred PRD).

## Orphan reservations (E4)

A reservation that matches the query but doesn't touch **any**
currently selected allocatable poses a UX problem: jumping to it
would either (a) show an empty calendar at that date (because
nothing the reservation uses is selected), or (b) silently expand
the selection behind the user's back.

Three candidate behaviours; **pick one before Phase 1**:

1. **Auto-add to selection on click.** Clicking E4 adds the
   reservation's primary allocatable to the calendar selection,
   then jumps. Cheap; mutates user state.
2. **"Add & jump" / "Just jump" split-button.** Two affordances
   on the E4 row — explicit. Costs a click on every orphan.
3. **Preview overlay.** Click opens a transient pop-out showing
   *just* this reservation's block + its allocatables, without
   modifying the calendar selection. Closing it returns to the
   selection state from before. Best UX but largest implementation.

Recommendation pending user input — annotate decision here once
made.

## GraphQL substrate augmentations (added 2026-05-29)

The SPA's tier model composes 3-5 aliased queries per keystroke (A1
selected + A2 recent + A3 deployment-wide; E1-E4 the same shape for
reservations). For that pattern to work without N round-trips per
keystroke, the existing query roots need `searchText` + `matchKind`
args and a few companion fields. None of this needs a new top-level
query root — existing roots grow new args additively.

**Phased so Phase 1 ships without any of the harder typed-schema work.**

### Phase 1 — name-only search (NO group / classification dependencies) — SHIPPED 2026-05-29

Goal: ship the working power-search GraphQL surface against the
structural `name` field today. No PRD 065 (declared groups), no
per-attribute typed match, no introspection of generated
`<TypeKey>Classification`s. Just the rapla-resolved name string that
already drives the existing `nameContains` filter on
`AllocatableFilter` and `ReservationFilter`.

**What shipped 2026-05-29 (GraphQL substrate only — SPA consumption still pending):**
- `enum MatchKind { PREFIX SUBSTRING FUZZY }` in `schema.graphqls`
- `searchText: String` + `matchKind: MatchKind = SUBSTRING` on
  `AllocatableFilter` and `ReservationFilter`
- `Reservation.hasConflicts: Boolean!` field
- `SearchMatcher.java` — case-insensitive PREFIX / SUBSTRING / FUZZY
  (Levenshtein ≤ 1 with length+/-1 windows) + `rank()` for server-side
  ordering (kind weight × 10000 + match position)
- `ReservationGraphQLController` + `ClassificationGraphQLController`
  apply the new args in `matches()` and post-filter sort by
  `SearchMatcher.rank` → id tiebreak when `searchText` is set
- `Reservation.hasConflicts` wired as a `LightDataFetcher` singleton
  in `StructuralTypeFetchers` using
  `SyncStorageOperator.getConflictsSync(r)` — §12-gated (caller must
  read both sides + allocatable for a conflict to count)
- Sync-refactor cleanup: 3 server-side resolver sites that wrapped
  sync work in CountDownLatch around `Promise` switched to
  `SyncStorageOperator`'s synchronous variants (`getConflictsSync`,
  `queryAppointmentsSync`); ~90 LOC of helpers deleted
- 8 new tier-3 tests; 126 GraphQL tests total green

What ships (the unchanged design — what the resolvers expose):

1. **`searchText: String` + `matchKind: MatchKind` args on existing
   roots:**
   - `Query.allocatables(filter:, searchText:, matchKind:)` —
     match against `Allocatable.getName(locale)` (= `displayName`).
     Same value `AllocatableFilter.nameContains` matches today.
   - `Query.reservations(filter:, searchText:, matchKind:)` —
     match against `Reservation.getName(locale)` (rapla-resolved per
     the `nameformat` annotation; same value the existing
     `ReservationFilter.nameContains` queries).

   When both `searchText` and `nameContains` are set the server ANDs
   them (`nameContains` stays for legacy callers; new SPA flows pass
   `searchText` exclusively).

2. **`MatchKind` enum:**
   ```graphql
   enum MatchKind {
     PREFIX     # "Sm" matches "Smith" but not "Cosmo"
     SUBSTRING  # "mit" matches "Smith" (default — current `nameContains` behavior)
     FUZZY      # Levenshtein-1 or similar; "Smyth" matches "Smith"
   }
   ```
   Tier-to-kind mapping: A1/E1 → PREFIX, A2/E2 → SUBSTRING, A3/E3 → FUZZY.

3. **Server-side row ranking** within each `searchText`-bearing call:
   1. Match strength (PREFIX > SUBSTRING > FUZZY)
   2. Match position (earlier in field = higher rank)
   3. Stable tie-break by id

   Tier composition stays client-side; per-call internal order is
   server-authoritative.

4. **`Reservation.hasConflicts: Boolean!`** — picks up the PRD 064
   deferred field. Power search needs the badge; the calendar view
   may later too. Implementation: `LightDataFetcher` reading the
   per-query `RequestContextInstrumentation` cache + per-row
   `operator.getConflicts(r)` filtered by §12 (caller must read both
   sides + allocatable, else "no visible conflicts").

5. **`Allocatable.displayName`** — already exists; no change.

6. **Tier composition example (Phase 1):**
   ```graphql
   query SearchPhase1($text: String!, $from: LocalDateTime!, $to: LocalDateTime!) {
     # E1-E3 — reservations
     e1: reservations(
       filter: { from: $from, to: $to, allocatableIdsIn: [...] },
       searchText: $text, matchKind: PREFIX
     ) { id firstDate lastDate hasConflicts }

     e2: reservations(
       filter: { from: $from, to: $to, ownerEq: "..." },
       searchText: $text, matchKind: SUBSTRING, limit: 20
     ) { id firstDate lastDate hasConflicts }

     e3: reservations(
       filter: { from: $from, to: $to },
       searchText: $text, matchKind: FUZZY, limit: 50
     ) { id hasConflicts }

     # A1-A3 — allocatables
     a1: allocatables(filter: { ... }, searchText: $text, matchKind: PREFIX) { id displayName }
     a2: allocatables(filter: { ... }, searchText: $text, matchKind: SUBSTRING) { id displayName }
     a3: allocatables(filter: { ... }, searchText: $text, matchKind: FUZZY) { id displayName }
   }
   ```

   One round-trip per keystroke, six aliased queries, all §12-filtered,
   all ranked.

Phase 1 requirements:
| Augmentation | Requires |
|---|---|
| `searchText` + `matchKind` on `allocatables` / `reservations` | None (additive on PRDs 055 + 059) |
| `MatchKind` enum | None |
| Server-side row ranking | None (resolver-internal) |
| `Reservation.hasConflicts` | PRD 064 v1's `operator.getConflicts(r)` (shipped) |

Phase 1 ships everything power search needs **for the dominant case**
("find me the room/person/booking named ..."). It deliberately doesn't
yet handle "search across non-name attributes" (e.g. find a lecture by
course-number, find a person by email) — that's Phase 2.

### Phase 2 — classification-attribute search

Goal: extend `searchText` to also match the SAME-VALUE-TYPE STRING
attribute fields on the target type's typed classification (course
number, email, room number, etc.).

Two ways to expose this:

- **Server-side fanout (preferred):** `searchText` continues to be a
  single arg; the resolver, on Phase 2 deployment, also matches against
  all STRING-valued attribute fields the type has — discovered at
  schema-build time from the DynamicType definition, no SPA changes.
  Wire shape unchanged from Phase 1; the field set just widens.
- **Client-driven via PRD 059 typed where:** the SPA composes
  `whereRoom: { Raumnummer: { contains: $text } }` for the per-type
  tier — no new substrate, but the SPA must know per-DT attribute
  names. Lean: server-side fanout — keeps the SPA polymorphic.

Phase 2 requirements:
| Augmentation | Requires |
|---|---|
| Extended `searchText` field set discovery | None — derives from already-generated `<TypeKey>Classification` SDL |
| Per-attribute match weighting | None (resolver-internal) |

### Phase 3 — declared-group search

Goal: typed cross-type results via the declared-group surface from
PRD 065. The SPA's E1/E2 tier model can return `[CourseEvent!]!`
(typed-on-shared-attributes) instead of `[Reservation!]!` (structural
only), avoiding per-type inline fragments and giving codegen consumers
typed shared fields.

Phase 3 requirements:
| Augmentation | Requires |
|---|---|
| `searchText` + `matchKind` on per-group roots | [PRD 065 — Declared Type Groups](065-graphql-declared-type-groups.md) |

Phase 3 is purely additive — Phase 1 (name) and Phase 2
(classification-attribute) keep working against the structural roots.
Groups expose a new typed cross-type surface for consumers that want
the typed shared fields.

### Hot-swap awareness

The Phase 2 searchable field set comes from the generated
`<TypeKey>Classification` SDL — admin adds a new STRING attribute,
schema rebuild picks it up within ~10s, search picks it up on the
next SPA query. Phase 1 (name-only) is hot-swap-trivial (already on
`getName`).

## Open questions

1. **Tier visualization.** Section headings vs. inline dividers vs.
   no separators (just sort order). Headings communicate the model
   but eat vertical space; if the result list is short, they read
   as noise. Probably headings appear only when ≥ 2 tiers are
   non-empty.
2. **Tier limits.** Cap each tier at N results (say, 5) with a
   "show all" expander, or let the longest tier dominate? Power
   users with thousands of allocatables will hit this; needs a
   bounded query shape on the backend either way.
3. **Asymmetric scope per tier.** ~~Should E1/E2 query the full
   reservation history (years back) or only a bounded window
   (say, ±1 year around the viewport)?~~
   **Resolved 2026-05-24 via PRD 035 §"2026-05-24 design refinement"
   §9** — bounded window via `from`/`to` args on the GraphQL `search`
   root, server default ±1 year around `serverTime`, client-overridable.
   "Search older →" expander widens the window on demand by re-issuing
   the query with broader `from`/`to`.
4. **Conflict tier interaction.** When the user clicks a conflict
   row that maps to two off-screen reservations, jump to which?
   Earliest start? Or open a transient "conflict pair" preview?
5. **Search-as-you-type debounce.** Local-only filtering is free,
   but reservations need a server call (the SPA doesn't hold the
   full reservation graph). 200 ms debounce + cancel-prior is the
   conventional answer; confirm.
6. **Recency scope.** Per-user-per-device (browser storage) vs.
   server-persisted recency (`/preferences` round-trip). Device
   is cheaper and survives no backend round-trip; server-side gives
   portability across devices. Pick one.
7. **Orphan handling.** Decide between the three candidate
   behaviours above.
8. **"Switch selection" gesture.** Shift-click? Cmd/Ctrl-click?
   Or a dedicated icon-button on each row? Modifier-clicks are
   invisible; explicit icons crowd the row.
9. **Filter editor coexistence.** Does the classification-filter
   editor (PRD 023's `ClassificationFilterBuilder`) disappear
   entirely in the SPA, hide behind a "filter" affordance on the
   search box, or live as a separate panel? Search subsumes most
   filter use cases but not "show me all reservations of type X
   in this period" — that's still a filter.
10. **Permission leakage.** ~~§12 of AGENTS.md is the binding
    constraint — search must not echo back ids the user can't
    read. The `/storage/resources` payload is already filtered;
    confirm a future `/search` endpoint uses the same boundary.~~
    **Resolved 2026-05-24 via PRD 035 §"2026-05-24 design refinement"
    §9** — the GraphQL `search` resolver applies §12 at both layers:
    (a) drop hits whose entity is unreadable; (b) drop hits whose
    matched field is unreadable (existence-of-match is information,
    don't leak it). Mandatory `GraphQlLeakTest` coverage of search
    paths (PRD 035 §Tests).

## Plan

To be drafted once open questions are decided. Likely shape:

- **Phase 0** — pick orphan handling, switch-selection gesture,
  tier-limit policy, recency scope. Update this PRD with the
  decisions.
- **Phase 1** — search shell (input box, result list, tier
  rendering) wired to **client-side filtering** of the already-loaded
  allocatable cache. No server changes. Tier A1/A2/A3 work end-to-end;
  E-tiers stubbed as "search reservations →" CTA.
- **Phase 2** — add reservation search. Consumes
  [PRD 060 — Discovery, Compute, MCP Transport](060-graphql-mcp-foundations.md):
  GraphQL `reservations(allocatableIds, from, to, searchText)` for
  E1/E2 + `search(text, scope, from, to)` for E3/E4 + `conflicts`
  scope for conflict rows. Tier composition is client-side via
  multiple aliased queries in one GraphQL request (e.g. `inViewport:
  reservations(...)`, `selectedAll: reservations(...)`, `global:
  search(...)` — one round trip, server parallelizes).
- **Phase 3** — recency ring (read/write), tier ordering by
  recency.
- **Phase 4** — conflict result type; piggy-backs on
  `/storage/conflicts` + the search index.
- **Phase 5** — "switch selection" verb, orphan handling
  (per Phase 0 decision), filter-editor coexistence (per OQ 9).

## Tests

To be drafted per phase. Initial test charter:

- **Tier-1 (pure)**: ranking function — given (`query`, `selected
  set`, `viewport`, `recency ring`, candidate list), expected
  ordering. Pure Java if we share the ranker via PRD 024, pure
  TS otherwise. Multiple cases per tier boundary
  (just-in/just-out of viewport; resource just-deselected; etc.).
- **Tier-3 (MockMvc)**: the eventual `/search` endpoint —
  permission leak test per AGENTS §12; bounded-window honoured;
  empty-query empty-response.
- **E2E (Playwright or similar)**: type query → click result →
  calendar scrolls. One golden-path test per tier
  (A1 toggle, E1 jump, E2 navigate-to-first, E4 orphan path).
  Worth investing here because the tier model is what's hardest
  to keep coherent over future changes.

## See also

- [PRD 026 — Angular frontend (reservation editing)](026-angular-frontend.md)
- [PRD 023 — Presenter / Model carve-out](023-presenter-view-extraction.md)
- [PRD 024 — Server-side edit services](024-server-side-edit-services.md)
- [`architecture/rest-api.md`](../architecture/rest-api.md) — the
  endpoints this PRD assumes will be available.
- AGENTS.md §12 — permission-leak rule that constrains the
  eventual `/search` endpoint.
