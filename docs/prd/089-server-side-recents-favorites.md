# PRD 089 — Server-side recents & favorites (+ search-rank boost)

**Status:** in progress — 2026-06-27 (Phases 1, 3, 4 landed; Phase 2 deferred by design)
**Related:** PRD 081 (omnibox multisearch — the rank-boost target), PRD 078 (SPA GraphQL view renderer — the consumer), PRD 074/077 (saved views — same Preferences persistence), PRD 067 (server mutation unification / EntityLifecycle — the delete seam), PRD 049 (interface-`@HttpExchange` routing), PRD 072 (cookie auth, `/api/auth/me`, per-user prefs precedent)

## Abstract

The SPA keeps **recents** and **favorites** in browser `localStorage`
(`rapla.resourceSelection.recents` / `.favorites`). That has two defects: the
keys are **global per browser**, so different rapla accounts on the same browser
see each other's lists; and the data is **trapped on one device**. Move recents
and favorites to **per-user server storage** (rapla Preferences, the same place
the refresh token lives), so they follow the user across devices, stay isolated
per account, are **auto-scrubbed when a referenced resource/user is deleted**,
and can **boost search ranking** (a caller's favorites/recents float to the top
of their omnibox buckets). The remaining client-only view state (scope chips,
render mode, date window, last view) stays in `localStorage` but is **namespaced
per user** to close the same cross-account bleed cheaply.

## Goal

Measurable end state:
- `GET /api/recents` and `GET /api/favorites` return the caller's lists; two
  different accounts on one browser get **different** lists (tier-3 MockMvc).
- Selecting a search hit (explicit `POST /api/recents`) adds a recent; the
  search query itself performs **no** write (§16 — tier-3 asserts a `search`
  GraphQL call leaves prefs untouched).
- After an `Allocatable`/`User` is deleted, it never appears in any user's
  recents/favorites reads — for free, via live-resolve-skip-missing (D3), the
  same pattern rapla already uses for calendar-model `selected` (tier-2 facade
  test: delete, then re-read, entry gone). A stored sweep is optional hygiene.
- In `SearchGraphQLController`, a matching hit that is one of the caller's
  favorites (then recents) sorts **above** an equally/less-relevant non-favorite
  in the same kind bucket (tier-1/2 over the ranking comparator).
- No `localStorage` key is shared across users: recents/favorites are gone from
  `localStorage`; scope/renderMode/window/lastView keys are suffixed `::u=<id>`.

## Implementation

### Storage model (server)
Store both lists as **JSON strings** in the user's `Preferences`, mirroring
`RefreshSessionService` (`rapla-server/.../RefreshSessionService.java`, JSON
under a `TypedComponentRole<String>`):

```java
static final TypedComponentRole<String> RECENTS   = new TypedComponentRole<>("org.rapla.user.recents");
static final TypedComponentRole<String> FAVORITES = new TypedComponentRole<>("org.rapla.user.favorites");
```

Each entry persists the **minimum**: `{ id, kind, ts }` (`kind` ∈
`resource|user`). Presentation (`label`, `typeKey`, `color`) is **resolved live
from the entity at read time**, not stored — so labels never go stale and a
deleted/now-invisible entity simply drops out of the read (defense-in-depth, see
D3). Recents are an ordered list capped at 20 (newest first, re-touch promotes
without duplicating — same semantics as today's `pushRecent`). Favorites are an
ordered set, no cap.

Read/write goes through the facade edit/store pattern already used by
`SettingsController.setMe` (`facade.edit(facade.getPreferences(user))` →
`putEntry` → `facade.store`). A small `UserListsService` (server) owns the
JSON (de)serialization, the cap/promote logic, and **§12-filtering on read**.

### REST surface (PRD 049 interface-implements)
New `@HttpExchange("/api/recents")` + `@HttpExchange("/api/favorites")`
interfaces in `rapla-core/.../rest/`, implemented by a controller in
`rapla-server/.../web/` next to `SettingsController`. Reads are side-effect-free
`GET`; writes are explicit `POST`/`DELETE` (§16):

| Verb + path | Effect |
|---|---|
| `GET /api/recents` | caller's recents (live-resolved, §12-filtered) |
| `POST /api/recents` `{id,kind}` | add/promote, cap 20 |
| `DELETE /api/recents` | clear (the "× leeren" button) |
| `GET /api/favorites` | caller's favorites (live-resolved, §12-filtered) |
| `POST /api/favorites` `{id,kind}` | pin |
| `DELETE /api/favorites/{id}` | unpin (toggle off) |

Return DTO mirrors today's `ResourceItem` (`id,label,color,kind,typeKey`) so the
SPA store changes only its *source*, not its shape.

### Deletion handling — reuse the existing lenient-resolve pattern
rapla already has an "autoremove" for deleted entities in settings, and it is
**two** distinct mechanisms (verified 2026-06-27, `LocalAbstractCachableOperator`):

- **Type/User deletion → stored auto-patch.** `addChangedDynamicTypeDependant`
  (3330) / `addRemovedUserDependant` (3378) walk every referencing entity from
  `getReferencingEntities` (3556) — which **includes every user's + system
  `Preferences`** (3565‑3579) — and `commitRemove(...)` them. Keyed on
  DynamicType + User removal.
- **Allocatable deletion → lenient-resolve-on-read (no stored sweep).** No
  `Allocatable` branch exists in the remove dispatch (3196‑3257). A calendar
  model's selected allocatables are real references, so a plain delete is
  *blocked* by `checkNoDependencies` (4124) unless the `forceRessourceDelete`
  flag is set; on a force-delete the dangling id stays in `selected` but
  `CalendarModelConfigurationImpl.getSelected()` (136‑159) does
  `resolver.tryResolve(id)` and **skips nulls** — the deleted resource just
  vanishes from the calendar on read.

Recents/favorites follow the **second** pattern (D3): persist ids, live-resolve
+ §12-filter on read, skip anything that no longer resolves or isn't visible.
That gives the user-requested "auto-delete on resource deletion" **for free**,
with zero delete-path code — exactly how rapla already treats deleted
allocatables in calendar selections. An explicit stored sweep is therefore
**optional storage hygiene** (Phase 2, deferred), not a correctness requirement.

### Search-rank boost
In `SearchGraphQLController`, load the caller's favorite-id and recent-id sets
once per `search(...)` call (cheap — `caller` is already resolved), then make
the per-bucket sort favorites-first, recents-next, then existing
`rank → id`. Bump `score()` accordingly so the wire order matches. This is an
**in-bucket re-rank of matches only** (no new always-on bucket — D4).

### Client (SPA)
- New `RecentsFavoritesService` (Angular) calling the REST endpoints; loads on
  identity, mutates via `POST`/`DELETE` with optimistic in-memory signals.
- `resource-selection-store.ts` drops its `localStorage` `load`/`save` for
  recents+favorites and delegates to the service; `omnibox.rememberResource`
  → `POST /api/recents`; `toggleFavorite`/`clearRecents` → the endpoints.
- Remaining keys (`rapla.scope`, `rapla.renderMode`, `rapla.window`,
  `rapla.lastView`) get a per-user suffix `::u=<userId>` via a small
  `ScopedStorage` helper that reloads the signals when identity changes.

## Scope

### In scope
- Server per-user recents+favorites storage, REST read/write, §12-filtered reads.
- Auto-cleanup of recents/favorites on `Allocatable`/`User` delete.
- Favorites/recents boost in `SearchGraphQLController` ranking.
- SPA migration of recents+favorites off `localStorage` onto the service.
- Per-user `localStorage` namespacing for scope/renderMode/window/lastView.

### Out of scope
- Multi-tab / multi-user-in-different-tabs (impossible by the cookie-per-origin
  auth model — confirmed; use separate Chrome profiles). No tab isolation work.
- Migrating existing `localStorage` recents/favorites into the server (one-time
  loss on cutover is acceptable; could add a best-effort import later).
- Reverse index for O(1) cleanup (see OQ2) — start with the O(users) sweep.
- Sharing favorites between users / team favorites.

## Plan

### Phase 1 — Server storage + REST ✅ landed 2026-06-27
- [x] `UserListsService` (JSON in prefs, cap/promote, §12 read filter, D6 compaction).
- [x] `RecentsService`/`FavoritesService` `@HttpExchange` interfaces +
      `RecentsController`/`FavoritesController`; DTOs `UserListItem` / `UserListEntryRequest`.
- [x] tier-3 MockMvc `UserListsControllerIntegrationTest` (9 cases incl. §12 leak).

### Phase 2 — (optional) stored cleanup sweep — DEFERRED
Not needed for correctness: D3's live-resolve-skip-missing already makes deleted
entries vanish from reads (the existing allocatable pattern). Implement only if
unbounded prefs growth becomes a measured problem.
- [ ] If pursued: scrub hook on the `Allocatable`/`User` delete path; tier-2
      facade test (delete → entry gone from stored JSON, not just from the read).

### Phase 3 — Search-rank boost ✅ landed 2026-06-27
- [x] `SearchGraphQLController` reads caller's favorite/recent id sets (id-only,
      §16-pure) from `UserListsService`; in-bucket re-rank in pure helper
      `SearchRankBoost` (FAVORITE +2.0 / RECENT +1.0 score offset over the (0,1] base).
- [x] tier-1 `SearchRankBoostTest` (favorite > recent > plain; tier dominates base).

### Phase 4 — SPA migration ✅ landed 2026-06-27
- [x] `RecentsFavoritesService` (identity-driven load, optimistic signals) + rewired
      `resource-selection-store` (recents/favorites off localStorage), omnibox.
- [x] `ScopedStorage` + `bindPerUser` (`persist.ts`) per-user namespacing for the
      4 remaining keys (`rapla.scope`, `renderMode`, `window`, `lastView`).
- [x] Vitest: `recents-favorites.service.spec`, `scoped-storage.spec`, migrated store/component specs.

## Tests

- tier-3 (`rapla-app`): `/api/recents` + `/api/favorites` per-user isolation and
  the §12 mixed-id leak test (mandatory per §12 for any id-list endpoint).
- tier-2 (`rapla-server`, `FacadeTestSupport`): delete-scrub; the JSON
  cap/promote logic.
- tier-1: the ranking comparator (favorite > recent > plain at equal rank).
- tier-5/6 (`rapla-angular`): service-backed store; `::u=` namespacing reload.
- Probe: `curl -b cookies GET /api/recents`; delete a resource via the admin
  path and re-`GET` to see it gone.

## Open Questions

- **OQ1 — recents write trigger.** Today a recent is added only when a search
  hit is *acted on* (filter-replace/add). Keep that, or also add when a resource
  is opened/viewed in a view? *Resolution:* pending — start with act-on-hit
  (parity with today), revisit once view-open events exist.
- **OQ2 — do we ever need the stored sweep at all?** Resolved by D6: write-time
  compaction prunes dangling ids on every `POST`/`DELETE`, and the 20-recents cap
  bounds the rest. The O(users) delete-path sweep (Phase 2) stays deferred unless
  a favorites-only-never-touched edge case is ever measured to grow. *Resolution:*
  resolved — D6; Phase 2 deferred.
- **OQ3 — boost vs. dedicated bucket.** D4 picks an in-bucket re-rank. If users
  want a persistent "Zuletzt/Favoriten" row even without a matching term, add a
  bucket later. *Resolution:* pending — ship the re-rank, gather feedback.
- **OQ4 — favorites of deleted-then-recreated / cross-store ids.** `kind=user`
  entries on user delete are covered; confirm group ids (Phase 2 of PRD 081) when
  GROUP search lands. *Resolution:* pending — out of scope until GROUP exists.

## Decisions locked

**D1 — Preferences JSON, not entity-reference RaplaMap.** Store opaque
`{id,kind,ts}` JSON (à la `RefreshSessionService`), not `RaplaMap<Allocatable>`
entity references. Rationale: preserves order + timestamps + cross-kind (user)
entries, and keeps the lists **invisible to the dependency/reference graph**
(`PreferencesImpl.getReferenceInfo` never surfaces them) so deleting a resource
that sits in N users' recents proceeds with **no `DependencyException` and no
force flag**. A recent/favorite is a **view-only soft bookmark, not a structural
dependency** — it must stay out of `checkNoDependencies`, the export graph, and
the conflict finder, exactly the opposite of a calendar model's `selected` list
(which *is* a real reference and *does* block delete, needing `forceRessourceDelete`).
**Do not** later "promote" these to entity references — that would reintroduce
the block. Rejected for the same reason: entity-ref RaplaMap + adding them to the
"soft reference" set (like `removeLastChangedReference`) — more integrated but
blocks-or-cascades through the checker and loses ordering/timestamps/user support.

**D2 — Server storage for recents/favorites; localStorage only for view state.**
Recents/favorites are inherently per-user and worth carrying across devices →
server. Scope chips, render mode, date window, last view are ephemeral
per-browser UI state → stay in `localStorage`, just namespaced `::u=<userId>`.
Security on the client is explicitly **not** a goal (same physical person,
different rapla accounts), so namespacing — not server storage — suffices there.

**D3 — Live-resolve + §12-filter on read.** Persist minimal ids; resolve
`label`/`color`/`typeKey` from the live entity at read time and drop any entry
the caller can't `canRead` (resource) / `canAdminUser` (user). This keeps labels
fresh, makes deleted/hidden entries vanish from reads independent of the cleanup
sweep, and satisfies §12 (never surface an entity the user couldn't see in the
Swing client). This is the **same mechanism rapla already uses** for deleted
allocatables in calendar-model `selected` lists
(`CalendarModelConfigurationImpl.getSelected` skips `tryResolve` nulls), so it is
the proven idiom, not a new invention. The Phase-2 delete sweep is therefore
**optional storage hygiene**, not a correctness dependency.

**D4 — In-bucket re-rank, no always-on bucket.** Favorites/recents boost the
order of **matching** hits within their existing kind bucket (favorite > recent
> plain), rather than introducing a new top bucket that shows them without a
search term. Matches the literal request ("recent und favourites zuerst im
ranking") and avoids changing the omnibox's empty-term contract.

**D5 — Two separate Preferences entries, not one combined blob.** Store
`org.rapla.user.recents` and `org.rapla.user.favorites` as two distinct
`TypedComponentRole<String>` JSON lists. Rationale: rapla's `PreferencePatch`
tracks changes per role key, so a favorite toggle patches only `favorites` and a
recent push patches only `recents` — smaller patches, less write contention, less
to replicate across pods than one `{recents,favorites}` object that rewrites both
on every change. Also one key ↔ one endpoint (`/api/recents`, `/api/favorites`),
mirroring `SettingsController`'s field-per-role mapping. Element shape: recents
`{id,kind,ts}` (ts drives promote/order, cap 20), favorites `{id,kind}`
(insertion order, uncapped).

**D6 — Opportunistic write-time compaction; reads stay §16-pure.** Dangling ids
(resource removed, since there is *no* propagation of a resource removal into
prefs — only non-existing references, verified 2026-06-27) are pruned on the
**write** path: whenever a `POST`/`DELETE` re-serializes a list, drop every
element whose id no longer resolves, in the same patch. Recents self-heal on
every search-selection write; favorites compact on any toggle. The **read** path
never writes (§16) — it only live-resolves + skips (D3). This bounds prefs growth
with zero delete-path code and makes the Phase-2 sweep unnecessary in practice.
```
