# PRD 021 — Client-side resource stubs for large deployments

**Status:** wont-fix — superseded by [PRD 026](../026-angular-frontend.md) (Angular frontend). 2026-05-11.
**Author:** Christopher Kohlhaas
**Created:** 2026-05-10
**Related:** PRD 002 (multi-tenancy lazy init), [PRD 009](../009-server-bulk-storage-rest-api.md) (server bulk storage REST), [PRD 012](../012-dhbwrapla-client-migration.md) (DHBW client migration), [PRD 026](../026-angular-frontend.md) (Angular frontend)

## Why wont-fix

The stub design would require a multi-phase carve-out of the Swing client's
`LocalCache`/`RemoteOperator` paths plus a new server endpoint surface, plus a
permission-flip detection mechanism, plus a per-plugin compat audit. The
Angular frontend ([PRD 026](../026-angular-frontend.md)) replaces the whole Swing tier — including the
client-side cache that motivates this PRD — so the investment would be paid
off only on the deprecated client.

**The one quick win extracted from this PRD** — a name-search field next to
the filter button in `AllocatableSelection` and the reservation filter — has
been moved to [PRD 023](../023-presenter-view-extraction.md) (presenter / view carve-out), since 023 already
touches both components. The search field is implementable today against the
existing full-resource client cache; no stub/server changes required.

Everything below is preserved for reference only.

## Goal

Reduce the client-side memory and login-time cost of loading the resource
(`Allocatable`) set. DHBW admins see ~30 000 resources; today every one is
serialized over the wire and held in `LocalCache` as a fully-resolved
`AllocatableImpl` (classification map, permissions list, annotations map,
timestamps) even though the resource tree and most picker UIs read only a
handful of fields.

Introduce a **stub** representation. The client keeps stubs for the full
set; full `AllocatableImpl` is loaded on demand (open detail, edit, tooltip,
conflict drill-down). Filtering moves to the server.

## Non-goals

- Changing the entity model on the server. Server still holds full
  `AllocatableImpl` and computes everything from it.

## Where stubs live

**Stubs are a wire format + client-side cache concept only.**

- **Server-side:** no stub type in the in-memory model. The server's
  `LocalCache` holds full `AllocatableImpl` objects, exactly as today.
  `PermissionController`, `RaplaFacade`, conflict detection, history
  evaluation, REST controllers — all operate on full entities. None
  of them sees an `AllocatableStub` instance.
- **Wire boundary:** when serving the stub / tree / treeDelta /
  byIds endpoints (and when packing poll responses), the REST layer
  **projects** the full entity into a stub DTO record at serialization
  time. The DTO is a thin shape: id, name (locale-resolved), type id,
  effective-permission-for-this-user (computed via the existing
  `PermissionController`), lastChanged, holdsAppointments. No round-
  trip to look up later — a single projection at write-out.
- **Client-side:** stub objects exist in `LocalCache` alongside full
  ones (see Open Q §2 for class shape), implement the `Allocatable`
  interface so reservation lookups stay typed, and throw on deep-
  method access.

This keeps the server simple. The "AllocatableStub" class only exists
in `rapla-core` for wire (de)serialization plus on the client; no
server-side service ever references it. It also keeps server-side
testing straightforward — `FacadeTestSupport` and friends keep using
full entities; only the wire-layer tests need to assert stub-shaped
JSON.
- Reservations / appointments / users / dynamic types — out of scope, this
  PRD is resource-only. (Users may be a follow-up if numbers warrant.)
- Offline mode / sync queues — the stub model assumes server reachable.
- Removing `LocalCache` entirely. Stubs live in `LocalCache` alongside the
  full objects that get pulled on demand.

## Sizing by deployment — when stub mode is worth it

The complexity cost of stub mode is fixed: a second cache shape, a
call-site audit, plugin migration, permission-flip detection, two
configuration regimes to maintain. The benefit scales with resource
count.

Rough numbers, with ~200–250 bytes per stub on the wire and ~3 KB per
full `AllocatableImpl`:

| Resources | Bulk wire (gzip) | Bulk heap | Stub wire (gzip) | Stub heap | Net |
|---|---:|---:|---:|---:|---|
| 500 | ~30 KB | ~1.5 MB | ~5 KB | ~150 KB | Bulk is fine; stub adds complexity for no user-visible win. |
| 1 500 | ~90 KB | ~5 MB | ~15 KB | ~500 KB | Bulk still fine. Stub savings invisible to user; complexity cost real. |
| 5 000 | ~300 KB | ~15 MB | ~50 KB | ~1.5 MB | Bulk login starts to feel slow on cold network; stub mode begins to pay off. |
| 15 000 | ~1 MB | ~50 MB | ~150 KB | ~5 MB | Bulk login adds seconds; heap pressure on low-end clients. Stub clearly worthwhile. |
| 30 000+ | ~2 MB | ~100 MB | ~300 KB | ~10 MB | Bulk model is the bottleneck. Stub mode is the only way to keep the client responsive. |

**At 1 500 resources, stub mode is not worth the risk.** Concretely:

- The 90 KB gzipped bulk load + 5 MB heap is unobservable on any
  modern client. Login is already fast.
- The complexity cost (audit, plugin migration, permission-flip
  detection, dual-mode cache invariants) is the same regardless of
  resource count.
- Every risk we ranked still applies — silent permission-flip
  leakage, plugin breakage, call-site audit completeness — and the
  user-visible reward is zero.
- The threshold flag exists precisely to keep deployments at this
  size on the bulk path. Default-on bulk mode below ~5 000
  resources; opt-in stub mode above.

**Sweet spot:** deployments at 10 000+ readable resources per admin.
DHBW at ~30 000 is the primary motivation; mid-size deployments
between 5 000 and 10 000 are the call-judgment range — measurable
benefit but not yet bottlenecked.

This is what the threshold flag controls: not a magic switch, but the
deployment-by-deployment decision of "is the bulk model causing
visible pain here?" If the answer is no, stay on bulk and inherit
none of this PRD's risks.

## Why now

Survey numbers (see PRD body for file references):
- `AllocatableImpl` ≈ 310 LOC of state per instance. Empirically each
  carries on the order of 1–4 KB once classification + permissions are
  materialized. At 30 k that's tens of MB of heap *and* the same volume on
  the wire on every login.
- The login wire call (`RemoteStorage.getResources()`) is a single
  `UpdateEvent` blob; first paint waits for the whole thing.
- UI rendering today reads `getName()`, `isPerson()`, dynamic type (for
  tree grouping), and `permissionController.canRead/canModify(user)` —
  nothing else, for the vast majority of rows.

## Stub field set (design point — needs review)

The minimum the UI demonstrably needs:

| Field | Source | Why |
|---|---|---|
| `id` | `ReferenceInfo<Allocatable>` | identity, lookup |
| `name` | computed server-side from name attribute(s) | tree / picker rows |
| `dynamicTypeId` | `Classification.getType().getId()` | tree grouping, type filtering, icon (incl. `isPerson` — derived from the type's classification annotation; dynamic-type set already lives client-side, so no extra stub field needed) |
| `effectivePermission` | server-side `PermissionController` per current user | enum: `READ` / `EDIT` / `ADMIN` — replaces client-side permission walk |
| `lastChanged` | from full entity | invalidation / update events |

Open question: do we also need …

- a **sort key** (today `AllocatableImpl.compareTo` uses name + id — fine,
  no extra field)?
- a **conflict-creation flag** (`holdsAppointments` resource annotation)?
  Used by conflict UI to skip non-bookable resources. **Probably yes.**
- a small set of **commonly-displayed attributes** (e.g. `email` for
  person-type, building/room for room-type) so tooltips don't have to
  trigger a full fetch? Risk: this re-opens the "stub knows too much"
  question and re-introduces classification cost. **Leaning no** — tooltip
  is the trigger for the full-load.

Note that **grouping attributes (category references etc.) do not need to
live on the stub** — see the tree-building section below: the server
builds the tree and emits group-header nodes for category groupings, so
the stub stays grouping-agnostic.

Concretely two shapes to choose between (see Open Questions §1).

## Tree building moves server-side

Today `TreeFactory.createClassifiableModel()` walks the full
`Allocatable` set on the client and groups by dynamic type (always) and
sometimes by a category-typed classification attribute. That walk needs
the full classification of every resource — exactly the data we're
trying to avoid shipping.

Move tree construction to the server:

- New endpoint `GET /storage/resources/tree` (or a `tree=true` mode on
  the stubs endpoint) takes the same filter parameters as the stubs
  endpoint plus a **grouping spec**: an ordered list of grouping keys.
  Each key is either `byType` (default) or
  `byAttribute(typeId, attrKey)` — naming the classification attribute
  whose value (category reference, string, choice) becomes the group.
- Response is a tree: nodes are either **group headers**
  (`{kind: "group", label, key, children}`) or **resource stubs**
  (`{kind: "stub", id, name, dynamicTypeId, effectivePermission, ...}`).
  Group labels are computed server-side (category name resolution,
  i18n) and shipped pre-rendered.
- The server already has every category, every dynamic type, and every
  resource — grouping there is one query, not 30 k client-side lookups.
- Filter + grouping happen in the same request: server filters the
  resource set, then groups it, then streams the tree. The client
  renders top-down without ever materializing a flat list.
- Category set itself stays client-side resident (it's small and used
  for editor UIs); only the grouping/aggregation moves.

Phase 4 (UI) consumes this tree directly instead of building one from
stubs. `TreeFactory` becomes a thin adapter that turns the server tree
into the Swing tree model.

## Reservation → resource references

Reservations carry a list of allocations; each allocation is a
`ReferenceInfo<Allocatable>` (an id). Today dereferencing is free because
the LocalCache already holds every readable allocatable. In stub mode
that guarantee disappears — a reservation may arrive referencing a
resource the client has never heard of.

**Decision: keep the references as bare ids on the reservation entity,
and make the stub implement `Allocatable`.**

We do not denormalize allocation rows into the reservation, and we do
not change the wire shape of `Reservation` / `Appointment`. What changes
is how the client *resolves* an id when it needs to render or edit.

### The stub IS an `Allocatable`

Today `Reservation.getAllocatables()` returns `Allocatable[]`. The
`EntityResolver` walks the stored `ReferenceInfo<Allocatable>` ids and
hands back whatever the cache has for each id — currently always a full
`AllocatableImpl`. If the stub were a separate type that didn't
implement `Allocatable`, every call site that today does
`reservation.getAllocatables()[i].getName(loc)` would break.

So the stub is a second implementation of the `Allocatable` interface
(working name: `AllocatableStub`, or alternatively an
`AllocatableImpl` constructed in a `partial=true` mode — see Open Q
§2 for the class-shape decision; this section is about the interface
contract). It returns real values for:
`getId()`, `getName(locale)`, `getType()` (resolved through the
dynamic-type set already in cache), `isPerson()` (derived from
the type), `getEffectivePermissionForCurrentUser()`, and a new
`isStub()` query method (always `true` on a stub, always `false` on a
full).

It does **not** return real values for `getClassification()`,
`getPermissionList()`, `getAnnotation(key)`, `getLastChanged()`. Those
either throw a clear `StubNotResolvedException` or, where the API
contract forbids exceptions, return a sentinel ("not loaded") that
callers can check. We pick exceptions for first-class fields so
accidental deep access is loud, not silently empty.

### `isStub()` — when to use it

`isStub()` is for **callers who know mixed-mode exists and want to
batch-prefetch or branch on it**. Two intended usages:

1. **Plugin bridge.** A plugin that hasn't been audited can be wrapped
   with one defensive check at the entry point:
   ```java
   if (alloc.isStub()) {
       alloc = facade.resolveFull(alloc);
   }
   // pre-existing plugin code continues unchanged
   ```
   Migrates plugins one at a time without rewriting every internal
   access. Replaces the `instanceof AllocatableImpl` downcast pattern
   with a typed query.

2. **Batch prefetch.** UI code iterating a collection can partition
   stubs vs. fulls, batch-resolve the stubs via `byIds`, then iterate.
   Cheaper than N sequential resolves.

**Anti-pattern (do not use):** `String x = alloc.isStub() ? "" :
alloc.getClassification().getValue("x");`. Silent-empty fallback for a
stub is exactly the failure mode we throw to prevent. If you can't
batch-prefetch, throw and fix the call site to resolve first.

### Why we still throw on deep methods despite `isStub()`

Two options for deep-method behavior on a stub:

- **Throw `StubNotResolvedException`** (chosen). Loud failure;
  unaware callers see a stack trace in dev/test; bug surfaces fast.
- **Lazy auto-resolve** (deferred). Deep method blocks on a `byIds`
  round-trip the first time it's called. Unaware callers stay correct
  but slower; a tight loop over 1000 allocatables becomes 1000
  sequential network hops. Easier to miss; harder to diagnose in
  production.

We ship throwing. If the audit work proves too painful or production
shows the throw fires from paths we missed, the escape valve is lazy
auto-resolve gated by a flag, with a dev-mode assertion that throws
instead of resolving (so developers still catch the issue locally).

### Call-site audit

A class is either **stub-safe** (calls only `getId`/`getName`/`getType`/
`isPerson`/`getEffectivePermission`) or **needs-full** (touches
classification/permissions/annotations). Phase 4's first sub-task is
walking every site that calls `reservation.getAllocatables()` and
labelling it:

- Calendar block rendering, conflict list, tree row: stub-safe.
- Reservation editor, allocation detail dialog, iCal/Exchange export,
  permission-revaluation paths: needs-full → batch-resolve via
  `POST /storage/resources/byIds` before the call.
- Plugin code (tableview, mergeallocatables, eventtimecalendar): per-
  plugin audit, fallback to full-load if a plugin can't be ported in
  time (matches Open Q §5).

The audit is mechanical but the unit of correctness — getting it
wrong means a stub-mode user clicks something and sees an exception or
an empty field. Tier-2 tests should construct stubs explicitly and
exercise each stub-safe site against them.

### What the server ships alongside a reservation

When the server returns reservations (calendar query, single fetch,
update event), it also returns a **stub sidecar**: a flat map of
`{id → AllocatableStub}` containing every allocatable referenced by
those reservations, modulo what the request body says the client
already has cached. The client drops the sidecar into `LocalCache`
before processing the reservations, so every allocation reference
resolves locally. This is one extra map field on the existing
reservation response — no new round-trip.

Reservation update events use the same pattern: if a delta introduces
a new allocation id, the event carries the corresponding stub.

### Resources the user can't read

A reservation visible to the user may reference an allocatable the user
has **no read permission** on (shared rooms / private resources). The
server **does not ship** anything for those — no stub, no name, no
type. The reservation arrives with id references that have no
corresponding sidecar entry.

Resolution is client-side: `RemoteOperator` (and the facade accessor
that wraps id → Allocatable lookups) detects the dangling id and
synthesizes a **non-visible allocatable** — a singleton/sentinel object
carrying just the id, a localized placeholder name ("Private resource"
or similar), and an `effectivePermission: NONE`. All action paths
(tooltip, edit, drill-down) short-circuit on the permission flag.

This is the same shape as today's "deleted/unknown reference" handling
— the missing-id case already exists; we're widening it to also cover
the permission-blocked case, with the same UI affordance. Net effect:
the conflict UI shows "this slot is taken by something you can't see"
without the server having to leak even the type or name.

### Edit / detail / save paths

Opening a reservation for **edit** still requires the full Allocatable
for each allocation (capacity, classification attributes used in the
edit form, etc.). The editor explicitly batch-resolves via
`POST /storage/resources/byIds` for the reservation's allocation set
before opening the dialog. Sync, blocking, with a spinner — same UX as
"loading reservation" today.

Save submits the same reservation shape it does today (id references
only); the server's permission check on save reads the full
allocatables it already has.

### Why not denormalize allocation rows

We considered embedding stub fields directly into each allocation row
on the reservation (name + permission inline). It would shrink the
sidecar to zero and remove one indirection client-side. We rejected
it because:

- The wire shape of `Reservation` / `Appointment` is consumed by
  several external integrations (Exchange connector, iCal export,
  third-party REST clients). Changing it cascades.
- Stub fields would go stale across update events — a resource rename
  would have to walk every reservation that references it. The
  sidecar approach lets the rename event ship one stub update.
- The sidecar de-duplicates: 100 reservations all using the same
  room ship one stub, not 100 inline copies.

## Name search field

Once filtering is server-side, a plain "search by name" field at the
top of the resource view becomes cheap to implement and useful even on
small installations.

- Single-line text field above the resource tree in
  `ResourceSelectionViewSwing` (and the picker dialogs that reuse it).
- On change, debounce (~250 ms) and call
  `facade.queryResourceStubs(filter.withNameSubstring(text), …)`. The
  filter is just an additional parameter on the existing stubs/tree
  endpoint — server does a case-insensitive substring (or prefix) match
  on `name`, returns matching stubs.
- While a search is active, the server returns a **flat list** (no
  grouping) — group headers carrying a single hit each is noise. Empty
  search reverts to the configured grouping.
- Minimum query length (e.g. 2 chars) to avoid scanning 30 k rows for a
  single keystroke. Tunable server-side.
- Result count is capped (e.g. 500) with a "refine your search" hint
  beyond — same cap applies in stub mode regardless of source.
- Tier-3 test covers the wire contract (substring match, case
  insensitivity, cap behaviour, min-length rejection).
- This composes with the existing classification filter: the user can
  set a type/attribute filter *and* type a name; server applies both.

## Plan

### Phase 1 — server: stub DTO + endpoint
1. Define `AllocatableStub` DTO in `rapla-core` (wire-side; not an entity —
   doesn't go into `LocalCache.resources` keyed map alongside full
   allocatables until Phase 3).
2. Add `RemoteStorage.getResourceStubs()` (and matching REST endpoint
   `GET /storage/resources/stubs`). Computes effective permission
   server-side per row using existing `PermissionController`. Streams
   compactly — one row per resource, no nested classification, no
   permission list.
3. Add `RemoteStorage.getResourceTree()` (REST `GET /storage/resources/tree`)
   — accepts the same filter parameters plus a grouping spec, returns a
   tree of group-headers + stubs as described in "Tree building moves
   server-side". Reuses the stub serializer for leaf nodes.
4. Add server-side filtering parameters to both endpoints: dynamic
   type id(s), classification attribute filters (the `ClassificationFilter`
   rules currently applied client-side), text/name search. Reuse the
   existing `ClassificationFilterImpl` rule shape — just evaluate it
   server-side instead of client-side.
5. Tier-2 test: `FacadeTestSupportTest`-style assertions that the stub
   list for a known fixture user matches the filtered full list; tree
   endpoint produces the same node set the client-side `TreeFactory`
   would have built from the full resource set under the same
   grouping spec.
6. Tier-3 test: MockMvc against both endpoints — auth gate,
   permission filtering, type filter, name search, grouping spec.

### Phase 2 — server: lazy resource resolve endpoint
1. `GET /storage/resources/{id}` already exists
   (`RaplaResourcesController`) — confirm it returns the full
   `AllocatableImpl` with classification + permissions.
2. Add `POST /storage/resources/byIds` (body: list of ids) for batch
   resolution of N visible rows when a detail/tooltip is opened on a
   filtered list. Cheaper than N round trips when the UI prefetches.
3. Tests at tier 3.

### Phase 3 — client: filter-driven cache (no bulk preload)
The client does **not** preload the resource set on login. Each view
that needs resources (tree, picker, conflict panel, calendar) issues a
filter+grouping request and receives only the stubs that match. Login
ships dynamic types, categories, current user, preferences — but no
resources.

1. `LocalCache` learns to hold either a `Stub` or a full `AllocatableImpl`
   per id. Type-safe accessors:
   - `getAllocatableStub(id)` returns a stub if any is cached, else null;
   - `getAllocatable(id)` returns the full object **or** triggers a
     fetch (blocking or async, depending on call site) if only a stub
     or nothing is present.
2. `RemoteOperator.loadData()` skips the bulk `getResources()` call in
   stub mode and just primes the lightweight prerequisites
   (types/categories/user/prefs). Below a configurable threshold
   (deployment opt-in) the existing bulk path remains for
   backwards-compatibility — small installations don't pay the
   round-trip-per-view cost.
3. New facade-level API: `facade.queryResourceStubs(filter, groupingSpec)`
   → returns the server-built tree of stubs for a view, **also** seeding
   `LocalCache` with the returned stubs so cross-view id lookups hit
   warm cache. Views are encouraged to call this exactly once per
   filter change, not per render.
4. Reservation/appointment rendering needs the allocations' resources
   by id. When a reservation surfaces in the calendar and an allocated
   id isn't cached, batch-resolve via `POST /storage/resources/byIds`
   (Phase 2). The calendar prefetches all ids in the visible date range
   in one round-trip, not N.
5. LRU eviction applies to both stubs and full entities — stubs from a
   closed picker view can be evicted unless still referenced by a
   pinned view (the resource tree, the currently displayed reservation
   set). Pinning is an explicit hint, not a guess.
6. Tests at tier 2 against a stub-mode `FacadeTestSupport` variant.

### Phase 4 — client: rendering + on-demand resolve
1. `TreeFactory.createClassifiableModel()` and friends consume stubs.
   `SimpleTreeCellRenderer` already only reads `getName()` + `isPerson()`;
   `isPerson()` is implemented on the stub by looking up its
   `dynamicTypeId` in the cached dynamic-type set (the type set is small
   and stays fully resident in `LocalCache`).
2. `TreeAllocatableSelection`, `ResourceSelectionViewSwing`,
   `ResourceRequestSelectionViewSwing`, `ConflictSelectionViewSwing` —
   audit each for which `Allocatable` methods they call. Anything beyond
   the stub field set triggers a full load (sync for a single row click,
   batch via `byIds` for multi-row operations).
3. Tooltip / detail / edit actions explicitly call
   `facade.resolveFull(allocatable)` before opening the popup or editor.
4. `FacadeImpl.getAllocatablesWithFilter()` switches to server-side
   filter call when in stub mode. The client never walks the full
   `Allocatable` list to filter.
5. Permission checks (`permissionController.canRead/canModify(user)`)
   short-circuit on the stub's precomputed `effectivePermission` when
   only a stub is present.

### Phase 5 — invalidation via id-level update events

Update propagation today is **pull-based**: every client polls
`RemoteStorage.refresh(lastSyncedSeq)` on a timer
(`RaplaClientServiceImpl.initRefresh`, default
`ClientFacade.REFRESH_INTERVAL_DEFAULT`); the server returns an
`UpdateEvent` carrying the full changed entities (`UpdateEvent.java:54`)
and a deletion id set. The client merges with `cache.put(entity)` per
entity (`AbstractCachableOperator.update`) and fires
`StorageUpdateListener.objectsUpdated`.

Stub mode keeps the poll cadence and the listener mechanism unchanged.
The poll response payload shrinks — but we ship the stub body inline,
not just the id, to avoid an extra round-trip on the common case:

```
UpdateEvent {
  resourcesChanged: [{ id, stub }, ...]    // create / update / perm flip
  resourcesRemoved: [id, id, ...]          // delete / user lost read
}
```

### Why inline the stub instead of just shipping the id

Today an `UpdateEvent` carries the full `AllocatableImpl` body
(~1–4 KB / change). Two stub-mode designs were considered:

1. **Id-only deltas + `treeDelta` round-trip per changed id.** Smaller
   wire (~50 bytes/id) but adds one round-trip whenever an active
   view needs to know where the changed row goes.
2. **Inline-stub deltas (chosen).** Stub body inline (~200 bytes /
   change). No extra round-trip when the change is in-group (rename,
   permission flip). `treeDelta` only fires when the path may have
   moved or the id is new to the view.

The chosen shape is ~20× smaller on the wire than today *and* keeps
"one update, one render, no extra calls" for the most common case
(an existing visible resource is renamed or has its permission
flipped). It costs ~4× the wire of pure-id deltas, which we accept
in exchange for cutting the network hop.

### Client decision tree on each changed entry

1. **Id is in the active tree, groupable fields unchanged**
   (`dynamicTypeId` same, grouping-attribute values same): in-place
   update the row from the inline stub. Re-sort within group locally
   if `name` changed. **No round-trip.** This is the common-path win
   over the id-only design.
2. **Id is in the active tree, groupable fields changed** (type
   changed, or a grouping-attribute value changed): the path may
   move. Fall back to `treeDelta(filter, grouping, [id])` to learn
   the new path; splice (remove from old, insert at new).
3. **Id is not in the active tree.** The client cannot evaluate the
   filter against the stub locally (stubs don't carry classification
   attributes). Fall back to `treeDelta` for the id; if the server
   says it matches under the active filter, splice in; otherwise just
   cache the stub for future use.
4. **Id is in a flat-list picker view** (no grouping): in-place update
   if cached; the picker can re-sort locally. For new ids,
   `treeDelta` (with no grouping spec, returns flat upserts).
5. **`resourcesRemoved`:** drop from cache; drop from any active view
   that has it. No round-trip. Reservations referencing the id fall
   back to the synthesized non-visible placeholder (an admin truly
   deleted it, or this user lost read permission — the client doesn't
   need to distinguish).

### Tree delta on update

A naive re-fetch of the whole tree on every `resourcesChanged` event
costs too much (30 k rows over the wire) and discards the user's
expand/collapse + scroll state. We splice instead.

**New endpoint:** `POST /storage/resources/treeDelta`.

Request body:
```
{ filter, groupingSpec, ids: [id1, id2, ...] }
```

The same filter + grouping the client used to fetch the tree
originally, plus the id list from the update event.

Server, for each id, evaluates:

- **Does the user still have read on this id?** No → mark `removed`.
- **Does the stub match the filter?** No → mark `removed` (id was
  formerly in the tree, no longer matches).
- **Yes to both:** compute the group-path under the active grouping
  spec and the insertion position (alphabetical within the group, or
  whatever sort the server uses). Return `{id, stub, path, position}`.

Response:
```
{
  upserts: [{ id, stub, path: ["group:type=Room"], position: 42 }, ...],
  removes: [id, id, ...]
}
```

The client applies the delta to its existing tree model:

- **Upsert into a new path:** create the group-header node if absent
  (using the path tokens that name it), insert the stub at `position`.
  If the id was already in the tree at a different path (e.g. its
  type changed → moves between groups), remove the old node first.
- **Upsert into an existing path:** if the stub is already in the
  tree, in-place update the row (name/permission flip); else insert.
- **Remove:** drop the node; if the parent group is now empty, drop
  it too.
- Empty group-header nodes that aren't pinned by anything else get
  collapsed/removed; non-empty groups keep their user expand state.

Cost is bounded by `ids.length`, not by tree size. Expand/collapse
state and scroll position survive because we're splicing into the
existing model.

The same endpoint serves the "user changed filter" case too — that's
just a full re-fetch (no `ids` parameter, returns the full tree),
which is what the tree endpoint already does. `treeDelta` with ids is
the incremental variant.

Removal events (`resourcesRemoved`) don't need a round-trip: the id
either is or isn't in the local tree model. If present, drop it;
clean up empty parents. No server call.

Edge cases:

- **Splice above visible viewport** moves visible content down. Swing
  trees handle this gracefully; if it proves jumpy under heavy bursts,
  consider deferring inserts above the viewport until next user
  interaction.
- **Burst of `resourcesChanged` ids** (e.g. admin bulk-imports 500
  resources) — debounce the update event, send one `treeDelta` call
  with all 500 ids. Server batches the per-id evaluation in one DB
  pass. Above some threshold (say 1 000 ids), the server can choose
  to respond `{ fullRefetchRequired: true }` and the client just
  re-fetches the tree — same cost as opening the view fresh.
- **The user has scrolled into a lazy-loaded subtree** (if we add
  lazy children later — currently the tree response is full): the
  same splice logic applies, but inserts into a not-yet-loaded
  subtree are no-ops; they'll arrive when the subtree loads.

### How this resolves the catches

- **Permission change (catch 1):** today `getUpdateResult` walks the
  *history table* — what changed. Permission changes on a category /
  type / resource only enter the history for the entity that was
  edited, not for the resources whose user-visibility flipped. Stub
  mode needs a second pass in `getUpdateResult`: when a permission-
  relevant entity has changed in `[lastSynced, now]`, recompute the
  user's readable-resource set and diff it against
  `[lastSynced]`'s set. Newly-readable ids → `resourcesChanged`;
  newly-unreadable → `resourcesRemoved`. Per-user, computed at poll
  time, no broadcast.
- **Live updates to active views (catch 2):** on each poll tick the
  client gets the id list and (for the tree view) issues a
  `treeDelta` call with those ids. Splice into the model. Polling
  cadence already coalesces bursts; no extra debounce needed beyond
  what the poll interval already provides.
- **Reconnect (catch 6):** identical to today — the client passes its
  last-seen sequence number on first poll after reconnect; server
  returns the id deltas since. If the gap exceeds a configurable
  threshold the server can respond with a "do a clean reload" flag
  and the client drops the entire resource cache + re-issues active
  view queries from scratch.

### Why id-only and not stub-bodied deltas

We considered shipping the new stub body inline on `resourcesChanged`
to save a round-trip. Rejected because:

- The client may not actually need the stub. An id that changed but
  isn't in any active view should just be invalidated, not refetched.
- The server doesn't know which clients have which stubs cached.
  Shipping every stub to every connected user on every change wastes
  bandwidth.
- Active views re-query against the filter anyway — the stub for the
  changed id arrives in that response, deduplicated with other view
  results.

The trade-off is one extra round-trip when an actively-visible row
changes. Acceptable; the alternative is per-user fan-out of full stub
payloads on every write.

## Open questions

1. **Stub field set** — minimum (id/name/type/permission) vs.
   minimum+holdsAppointments+commonlyDisplayedAttr. Lean minimum+holdsAppt.
   Decide before Phase 1 ships.
2. **Stub vs. partial-entity** — represent the stub as a separate DTO
   class, or as an `AllocatableImpl` with sentinel-empty
   classification/permissions and a `partial=true` flag? Separate DTO
   is cleaner but doubles the code path everywhere; partial entity is
   smaller-surface but bug-prone (a method that reaches into classification
   silently returns empty). **Leaning separate DTO** with explicit accessor
   contracts.
3. **Threshold for stub mode** — fixed (e.g. server-side cutoff at
   "user readable allocatables > 5 000") or always-stub? Always-stub is
   simpler but regresses tiny deployments that today rely on the full
   cache for offline-ish behavior. **Leaning fixed threshold,
   server-configured.** In stub mode the client never bulk-loads; each
   view issues its own filtered query.
4. **Filter wire format** — reuse current `ClassificationFilter`
   serialization or define a smaller server-side filter DTO? Reusing
   keeps client unchanged; defining a smaller DTO is cleaner. **Leaning
   reuse** to keep client diff small.
5. **Plugin compatibility** — `tableview`, `eventtimecalendar`,
   `mergeallocatables`, and admin panels iterate allocatables in ways
   the survey didn't enumerate. Each needs an audit before Phase 4
   ships, with a fallback to full-load on stub-mode iteration if the
   plugin can't be ported in time.
6. **Permission precomputation cost** — for 30 k resources × M users,
   precomputing `effectivePermission` per request is fine
   (`PermissionController` already does it lazily). Caching the result
   server-side is a follow-up if measurements justify.

## Tests

- **Tier 1** (rapla-core): `AllocatableStub` (de)serialization,
  effective-permission enum derivation.
- **Tier 2** (rapla-server, `FacadeTestSupport`): server-side filter
  evaluation matches client-side filter on the same fixture; stubs vs.
  full match on id/name/type/permission per user.
- **Tier 3** (rapla-app, MockMvc): stub endpoint auth gate, filter
  parameters, byIds endpoint.
- **Tier 4** (`@e2e`): one end-to-end "login → tree opens → click row →
  full resource loads → edit → save → tree updates" against a
  large-fixture deployment. Tag with `@Tag("e2e")`.
- Memory regression test (manual or scripted) on a 30 k-resource
  fixture: heap delta after login, wire-payload size.

## Risk / rollout

- Keep both paths (full bulk load + stub mode) behind a server-side
  config so a rollback is a single flag.
- Ship Phase 1+2 (server endpoints) before Phase 3+4 (client
  consumers) so the new endpoints can be exercised independently.
- DHBW deployment is the primary target; canary there before
  flipping default for all installs.
