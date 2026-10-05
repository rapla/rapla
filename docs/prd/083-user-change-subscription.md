# PRD 083 — permission-scoped read index + user change-subscription

**Status:** in progress — Part A first cut built (per-user cached readable set, `PermissionIndex`, live in every GraphQL request); `access_grant` inverted index not built; Part B (`changesSince`) not built; concept decided 2026-10-05 (B-D1–B-D4), open SQ1/SQ3/SQ4/SQ5. State checked 2026-10-05. (Absorbed [PRD 082](082-storage-memory-model.md) Workstream B — the permission read index — 2026-06-24.)
**Related:** [PRD 082](082-storage-memory-model.md) (storage memory model — foundation: H2 read-model + put/remove projection seam this index sits on), [PRD 086](086-appointment-block-index.md) (appointment index — dual-API sibling), [PRD 087](087-classification-type-indices.md) (classification indices — GraphQL-only sibling), [PRD 035](done/035-graphql-foundations.md) (GraphQL foundations), [PRD 026](026-angular-frontend.md) (Angular SPA), [`docs/authentication.md`](../authentication.md) (stateless JWT), AGENTS.md §12 (data-leak prevention)

**This PRD both *owns* and *consumes* the permission index.** **Part A** builds the `access_grant`
read index + caller-context cache (answers "what can this user read/allocate, fast"). **Part B** is
the `changesSince` user change-subscription that consumes it per-update (answers "what changed that
is relevant to this user, so the UI can refresh"). Same index, two evaluation modes.

**GraphQL-only (like [PRD 087](087-classification-type-indices.md), unlike [PRD 086](086-appointment-block-index.md)).** The permission-filter pain is GraphQL-specific
(`canRead × all` in the controller loop, *per query*); the old RemoteStorage `getResources()` filters
once per session-refresh and is left as-is. The index accelerates the GraphQL controller's `canRead`
loop and the SPA subscription — it does **not** touch the old Swing protocol (whose §12 filtering is
production-load-bearing). A later, lower-priority, higher-risk follow-up could accelerate the old path
too, but only if the amortized session-refresh measurably hurts (today it does not).

---

# Part A — the permission read index (`access_grant`)

## State (2026-10-05) — first cut built, not the inverted index

- `rapla-server/.../storage/impl/server/readmodel/PermissionIndex.java` computes, per user, the readable allocatable ids (and the information-only set) once by delegating to `PermissionController.canRead`, and caches them keyed by user id; §12 equivalence holds by construction.
- Wired in `LocalAbstractCachableOperator` (`permissionIndex()`, `readableAllocatableIds`, `informationOnlyAllocatableIds`), flip-gated by `isReadModelAuthoritative()`; `RequestContextInstrumentation` puts the caller's readable set into every GraphQL request context, `StructuralTypeFetchers` reads it.
- Invalidation in `updateReadModel`, the seam for local writes and other pods' changes (`refresh` → `updateIndizes` → `updateReadModel`): user added/changed/removed → that user's entry; category, allocatable or dynamic type added/changed/removed → all entries; reservation changes → none. Built lazily per user on first request (`computeIfAbsent` over all allocatables); `clear()`/`remove()` wait for a running build and drop its result (ConcurrentHashMap bin lock). Active when `rapla.readmodel.authoritative` (default true). Source: rapla-permission, 2026-10-05.
- Not built: the inverted `access_grant` structure (avoids the once-per-user full scan), world-readable / READ_TYPE union (PRD 087), owner-based reservation scoping. AQ1–AQ5 below still apply to that structure.
- [PRD 129](129-cached-permission-groups.md) (cached permission groups, uncommitted) is the caller-context cache this Part sketches: the index build reads groups from `UserGroupsCache`, cleared at the same seam before the index (build per location admin 300–680 ms → 40–63 ms). That answers **AQ5** in favour of the per-user cache (not principals in the JWT).

**Problem.** At large instances a user may read only *part* of the store, and `canRead` checks are a
read-path bottleneck — the measured untyped `allocatables({})` = 155 ms is dominated by
`canRead × ~50,000`. §12 forbids skipping the check. Need: the readable set **without iterating all
entities**.

**Permission model (`PermissionController`):** `canRead` walks the entity's **permission list**; each
permission grants a **user OR a group (Category)** an **AccessLevel** (READ / READ_TYPE / EDIT /
ADMIN), optionally time-bounded. Groups resolve through the category hierarchy; plus type-level read
and an admin short-circuit.

## The inverted index — carries the access *level*, not just "read"

Permission is **not** binary visible/invisible — it has **levels** (READ / ALLOCATE / ADMIN). A user
may *see* a resource but no longer *allocate* it. So the index stores the **access level per grant**.
Instead of "per entity, check if the user may read it" → "for the user's principals, look up the
entities directly":

```sql
CREATE TABLE access_grant (
  entity_id     VARCHAR,
  principal_key VARCHAR,    -- user-id OR group-id, EXACTLY as granted (un-expanded)
  access_level  VARCHAR,    -- READ / ALLOCATE / ADMIN (+ READ_TYPE via type-bucket)
  valid_from    TIMESTAMP, valid_to TIMESTAMP   -- time-bounded permissions
);
CREATE INDEX ix_grant_principal ON access_grant(principal_key);
```

Capability sets by level, one indexed lookup each (cost O(grants for those principals), not O(all)):

```sql
-- readable: access_level >= 'READ'; allocatable: access_level >= 'ALLOCATE'
SELECT DISTINCT entity_id FROM access_grant
WHERE principal_key IN (:callerPrincipals) AND access_level >= :threshold
  AND (valid_from IS NULL OR valid_from <= :now) AND (valid_to IS NULL OR valid_to >= :now);
```

Full readable set = `access_grant` (≥READ) ∪ type-bucket(READ_TYPE types, [PRD 087](087-classification-type-indices.md)) ∪ world-readable;
admin → short-circuit. The per-resource **effective level** is returned with the resource so the UI
renders capabilities (read-only vs bookable) — this is what Part B's re-query relies on.

## Expand the hierarchy on the CALLER side, not the entity side

- **Entity index stores DIRECT grants only** (un-expanded principal keys + level).
- **Caller principal set** = `{user-id}` ∪ `{direct groups}` ∪ `{all ancestor groups}` ∪ `{WORLD}`;
  the `IN (...)` resolves the hierarchy.

Why: a group-membership/hierarchy change then touches only the **caller set** (cheap recompute), not
thousands of entity rows — avoids mass-invalidation. (10,000 allocatables grant READ to "Faculty";
one user joins a Faculty subgroup → recompute that *one* caller's principal set, not 10,000 rows.) An
entity's permission edit still touches only that entity's grant rows, at the put/remove seam
(drift-safe, idempotent, rebuildable — [PRD 082](082-storage-memory-model.md) foundation).

## Worked example — "sees it but cannot book it"

```
entity   | principal | level
Room-101 | Faculty   | READ
Room-101 | CS-Staff  | ALLOCATE
Room-202 | WORLD     | READ
Lab-A    | CS-Staff  | ADMIN
```

Caller **bob** ∈ `CS-Dept` (under `Faculty`), **not** in `CS-Staff` → principal set `{bob, CS-Dept,
Faculty, WORLD}`. Readable (≥READ): Room-101 via `Faculty:READ`, Room-202 via `WORLD:READ`; Lab-A ✗.
Allocatable (≥ALLOCATE): none (`Faculty:READ` < ALLOCATE; `CS-Staff:ALLOCATE` not in bob's set). →
bob **sees** Room-101/202 but books **neither** — the `access_level` column distinguishes it on the
same index, just a different threshold. Maintenance on a permission edit: `DELETE FROM access_grant
WHERE entity_id=? ` + reinsert the few rows (idempotent); the old-vs-new grant diff *is* Part B's
update-relevance signal.

## Caller-context cache (stateless — keyed by user-id, not session)

rapla is stateless (JWT per request). Cache the caller's expanded principal set **per user-id**, per
pod, lazy on first request, reused across that user's requests. Eviction LRU + TTL (no session-end
signal). Invalidation at the seam: user-membership change → drop that user's entry; hierarchy change
→ flush (rare). Pure performance: cache-miss → re-derive from the `category → ancestors` closure;
drop-and-rebuildable. (Alternative: bake the principal set into the JWT — no server cache, but stale
≤ token TTL.)

## Correctness bar (security-critical)

The index must be **provably equivalent to `PermissionController`** — a divergence is a **leak**.
Differential-test over many users × entities: index-derived capability sets == `canRead`/`canAllocate`
sets; existing §12 MockMvc leak tests stay green. The flattening must reproduce level ordering
(READ < ALLOCATE < ADMIN), READ_TYPE, time windows, hierarchy direction, and `canReadOnlyInformation`.
**Narrow, never gate** ([PRD 082](082-storage-memory-model.md) D5): on any doubt fall back to the real `PermissionController`.

### Part A open questions

- **AQ1** — Exact semantics to flatten: level ordering, READ_TYPE precedence, `canReadOnlyInformation`,
  time-bounded grants on the fast path vs fallback.
- **AQ2** — World-readable representation: a WORLD principal row vs a "no-restriction" flag.
- **AQ3** — Reservations vs allocatables: reuse `access_grant`, or a separate owner-based index for the
  reservation `canModify` path.
- **AQ4** — Closure home: app-side cached `category → ancestors` map vs an in-engine closure table.
- **AQ5** — Caller principals in the JWT (no cache, ≤TTL stale) vs the per-user cache. *Resolution (2026-10-05):* per-user cache — [PRD 129](129-cached-permission-groups.md) `UserGroupsCache`.

---

# Part B — user change-subscription (consumes Part A)

**State (2026-10-05):** not started — no `changesSince` in schema, server or SPA. The SPA has no refresh path for changes by others at all (no polling, no visibility hook); concept round with the user pending.

**Consumes Part A.** 083 Part A answers "what can this user read/allocate, fast"; Part B answers "what
changed that is relevant to this user, so the UI can refresh" — the same permission answers (since 2026-10-05: `PermissionController` + the PRD 129 group cache directly on the small delta, B-D1 — not the `access_grant` index) +
caller-context cache, evaluated per-update instead of per-query.

## Problem

The SPA / Swing client must refresh its view when the store changes — but at large instances re-fetching everything on every change is wasteful, and filtering changes by "relevant to me" the naive way is the same `canRead × all` bottleneck [PRD 082](082-storage-memory-model.md) attacks. The UI needs a **cheap signal: "for the following resources something changed that matters to you"**, then it decides whether its current view is affected and **re-queries** (using the already-fast scoped query) — rather than the server pushing full filtered payloads.

## What already exists (build on it, don't reinvent)

rapla's cross-pod / client refresh is **timestamp-watermarked**, not versioned:
- `UpdateResult(since, until, oldEntities, updatedEntities)` — `LocalDateTime` bounds, and **carries old AND new** state per changed entity.
- `RemoteStorage.refresh(lastValidated)` / `refreshAllEvents(lastValidated)` — the client passes its last-synced timestamp, gets the delta `UpdateEvent`.
- `RemoteOperator.lastSyncedTimeLocal` holds the client watermark.

So the **watermark-poll mechanism is already there**. PRD 083 adds, on top: (a) **per-user permission-scoped relevance filtering** of that delta, and (b) the **lightweight resource-keyed hint** shape the UI consumes.

### What `EntityHistory` provides (verified 2026-06-24)

- **`Map<entityId, List<HistoryEntry>>`** — a *time-ordered list* per entity (not just the latest). Each `HistoryEntry` = `timestamp` (millis) + **full JSON snapshot of the entity** + **`isDelete`** flag + `ref` (id + type).
- **Supported types:** Allocatable, DynamicType, Reservation, User, Category, Conflict, Preferences — crucially **User and Category are in the history**, so membership/hierarchy changes are first-class delta entries.
- `get(id, since)` reconstructs an entity's state at a timestamp; `deleteUpdateSet` (timestamp-sorted `IndexedSortedMap`) serves "changed since `T`" efficiently.

Three simplifications this buys 083, plus one constraint:

1. **Level-changes are directly computable** — because the delta carries **old + new** per entity, comparing old-vs-new grants on a changed allocatable yields "READ stays, ALLOCATE dropped" with no separate tracking.
2. **Deletes are explicit** (`isDelete`) → clean "drop id", no inference.
3. **Membership/hierarchy changes are covered** — a changed `User`/`Category` is a delta entry; old-vs-new diff gives the caller's changed groups → affected resources via `access_grant`.
4. **Constraint — history is pruned** (`removeUnneeded` / `getHistoryValidStart`). If the client's `since` predates the retained window, **no incremental delta is possible → full resync**. The contract must signal this (`resync: true` when `since < historyValidStart`).

## The contract

```graphql
changesSince(since: Timestamp) → {
  until: Timestamp                        # new watermark the client stores for next call
  resync: Boolean                         # true when `since` predates historyValidStart → client must full-reload
  resourcesChanged:         [resourceId]      # ANY change relevant to me on this resource
  eventsChangedOnResources: [resourceId]      # new/changed events on resources I can read
  eventsChangedForOwners:   [userId]          # owners of new/changed events I can read (user chips, ownerIn — PRD 123 D10)
}
```

When `resync` is true the id-lists are empty and the client discards its state and re-fetches its full scope (the history no longer reaches back to `since`).

- **`resourcesChanged` is comprehensive** — fires on *any* change to a resource that matters to the caller, **not** binary appeared/disappeared:
  - **content** change (rename / attribute edit),
  - **effective access-level** change — including **"still readable but no longer ALLOCATE"** (the case bare visibility deltas cannot express),
  - **visibility** change (gained/lost READ) — falls out as a special case.
- **`eventsChangedOnResources`** — reservation (event) changes mapped to their allocatable-ids, intersected with the caller's readable resources.
- **No `appeared`/`disappeared`** id-deltas: a resource leaving scope is simply *absent* from the client's re-query; a resource entering scope appears in it. The client reconciles by re-querying.

### The client re-queries — and gets effective rights back

On a hint, the client re-runs its normal **scoped block/catalog query** (fast via [PRD 082](082-storage-memory-model.md)'s indices) for the affected resources / its current window. The re-query returns each resource **with the caller's effective access level** (`canRead` / `canAllocate`), so the UI renders capabilities correctly:

| Change | Re-query result | UI effect |
|---|---|---|
| content edited | new content | update display |
| lost ALLOCATE, keeps READ | resource returns, `canAllocate=false` | **booking disabled, still shown** |
| lost READ | resource absent from scope | client drops it |
| gained READ | resource present in scope | client adds it |

The server never decides the client's view — it only names affected resource-ids; the client knows its window/view and decides relevance.

## Mechanism — read-time filtering over the existing delta

The delta is **window-bounded** (changes since the watermark), so it is small; relevance-filtering it is cheap. Per caller, since `T`:

1. **Event changes** — changed reservations in `[T, now]` → their allocatable-ids → ∩ caller's readable resources (`canRead` via `PermissionController` + PRD 129 group cache, B-D1) → `eventsChangedOnResources`.
2. **Resource changes** — changed allocatables in `[T, now]` whose **content or the caller's effective level** changed → `resourcesChanged`. The level change is detectable because the delta carries old and new state of the allocatable: compare the caller's level on both (`PermissionController`, B-D1).
3. ~~Caller's own membership / hierarchy change~~ — replaced by B-D1: answered with `resync: true`.

All three are **id-set operations over [PRD 082](082-storage-memory-model.md)'s indices + the caller-context cache** — no entity hydration, no content serialization in the feed. The actual data flows only on the client's subsequent re-query, and only if it deems the change relevant.

**Read-time filtering, not write-time fan-out:** do *not* maintain per-user "inboxes" written on every change — that is write-amplification / mass-invalidation. The delta is small enough to filter at read time against the cached caller set.

## Transport: poll baseline + optional push

- **Baseline = watermark poll.** The client calls `changesSince(since)` periodically; idempotent and retry-safe (re-sending the same timestamp just re-returns the delta; the client applies idempotently — re-query is naturally idempotent). This is the correctness baseline and needs no durable infrastructure.
- **Timestamp caveat:** use an **inclusive** lower bound + idempotent client apply so changes sharing a timestamp aren't missed (at-least-once, occasional re-delivery is harmless). Same posture rapla's existing `refresh(lastValidated)` takes.
- **Push (optional latency upgrade):** a server→client signal ("you have changes, pull") via SSE/WebSocket, routed using the `access_grant` index as the **pub/sub routing table** — only notify sessions that may read the affected entity. This is **distinct from the pod→pod poll** ([PRD 082](082-storage-memory-model.md) deprioritizes that — cross-pod correctness rides on the store lock). Server→client push is a UX latency feature; the permission index is its enabler. Still poll-backstopped (push isn't durable).

## §12 (data-leak prevention) — the notification is a read boundary

- Hints name only resources the caller **can read** (`resourcesChanged`/`eventsChangedOnResources` filtered to ≥READ). Never signal a non-readable entity — a notification would leak its existence.
- A resource the caller **lost READ** on: send its id in `resourcesChanged` (so the client drops it) — id only, **never content**. The client re-query confirms it is gone from scope.
- Provably-equivalent-to-`PermissionController` bar (inherited from [PRD 082](082-storage-memory-model.md)): the relevance predicate is the same flattened index; differential-tested; existing §12 leak tests stay green.

## Plan

- **Phase 1** — SPA side (B-D4) + `changesSince(since)` GraphQL query over the existing `UpdateResult` delta, with the read-time relevance filter (event-changes + resource-changes arms) using `PermissionController` + the PRD 129 group cache (B-D1). Poll-only.
- **Phase 2** — ~~membership/hierarchy-change arm~~ replaced by B-D1: any change of the caller's groups or of the category hierarchy → `resync: true`.
- **Phase 3** — optional server→client push (SSE/WebSocket) routed via the permission index; poll-backstopped.

## Tests

- Tier-3: `changesSince` returns only readable resource-ids; a non-readable changed entity is absent (byte-identical to the "no such change" case — §12). A level-flip (lose ALLOCATE, keep READ) appears in `resourcesChanged`; the re-query returns `canAllocate=false`.
- Differential: relevance set == `PermissionController` evaluation over the delta, across many users.
- Idempotency: re-sending the same `since` timestamp yields a delta the client applies without divergence.

## Part B decisions (2026-10-05)

- **B-D1 — Group or hierarchy change → `resync` (user, 2026-10-05).** A change of the caller's user entity (groups) or of any category since `since` answers `resync: true`; the SPA reloads its scope. Rare, and it avoids needing the inverted `access_grant` index for Part B: the delta is small, so the relevance filter runs `PermissionController` directly on it, with the [PRD 129](129-cached-permission-groups.md) group cache. SQ2 likewise: a changed dynamic type or the caller's preferences → `resync`.
- **B-D2 — `eventsChangedForOwners` (user, 2026-10-05).** User chips scope by owner (`ownerIn`, PRD 123 D10); an owner's event on resources the view does not show would otherwise go unnoticed. Owner ids of new/changed/deleted events the caller can read; §12 as for the other arms.
- **B-D3 — Wire names follow PRD 116:** `resourceId`, not `allocatableId`.
- **B-D4 — SPA consumer (to be built, part of Phase 1).** One poller calls `changesSince`; on a hint it reloads what is affected: the lean resource list (`resourcesChanged`; then `FilterStore.reconcile` runs), the current view when a hint hits its scope (resource chips, `ownerIn`), and the PRD 128 conflict/request counts when the Prüfen context is loaded (any event hint). `resync` → reload everything in scope. Also on tab `visibilitychange` → visible.

## Open Questions

- **SQ1** — Granularity: is resource-keyed (`allocatableId`) enough, or do some views need event-id-level hints (a single event changed without a resource-level signal)? The resource re-query covers it but may over-fetch.
- **SQ2** — *(resolved by B-D1: `resync`)* Non-resource entities the UI shows (dynamic types, categories, the user's preferences): do they need their own change arms, or is a coarse "metadata changed → resync" sufficient?
- **SQ3** — Push timing (Phase 3): is server→client push justified by measured UX need, or is the watermark poll (tunable interval) sufficient? (Mirrors [PRD 082](082-storage-memory-model.md)'s "poll → push" deprioritization for the cross-pod path.)
- **SQ4** — Swing client: does it consume the same `changesSince` contract, or keep its existing `refresh(lastValidated)` full-delta path? (The SPA is the primary driver.)
- **SQ5** — Poll interval for the SPA: like Swing (~10 s), or slower (e.g. 60 s) plus an immediate poll when the tab becomes visible again? *Resolution:* pending (user, 2026-10-05).

