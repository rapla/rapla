# PRD 120 — Resource hierarchy for UIs: `Resource.parents` / `children` and a store-time cycle check over both kinds

**Status:** in progress — 2026-10-03: Phases 1+2 done (uncommitted, rapla-impl); the Phase 3 `idInMatchesHierarchy`
bullet is with rapla-impl, the rest of Phase 3 plus Phases 4–5 with rapla-impl2. D7 (no "alle wählen" on resource
nodes) and D8 (nest under categorization groups) added 2026-10-03. Drafted 2026-09-16 (D1–D6); the spike
`Resource.belongsTo` / `packageIds` (12fb1bfb4) is still live until Phase 3 removes it.
**Related:** [PRD 119](119-spa-one-search-resource-picker.md) (picker tree; this PRD delivers its deferred S2
"belongsTo nesting" in a different shape), [PRD 082](082-storage-memory-model.md) (`DependencyIndex` — built, not
wired), [PRD 116](done/116-graphql-allocatable-to-resource-rename.md) (`Resource` wire names),
[PRD 081](081-graphql-omnibox-multisearch.md) (SPA never interprets deployment attributes)

## Abstract

On dhbw-test a building cannot be expanded in the SPA resource picker: the PRD 119 tree only groups by
`categorization`, while real deployments model hierarchy with the attribute constraints `belongsTo` (room → building)
and `package` (course group → courses). This PRD adds two fixed GraphQL fields, `Resource.parents` and
`Resource.children`, read from the dependency graph the server already keeps for conflicts. The SPA builds the tree
from them. It also replaces the store-time cycle check, which today checks each kind separately and lets mixed cycles
through. End state: a building expands to its rooms and a course group to its courses on dhbw-test, and no store can
make a resource its own transitive parent.

## Implementation

**Verified current state (2026-09-16):**
- **Constraints (input).** `AttributeImpl`: `belongsTo=true` forces single value, `package=true` forces multi value;
  both only on resource-reference attributes. `DynamicTypeImpl` validation allows at most one `belongsTo` and one
  `package` attribute per type. Swing type editor: "Multiselect: yes / no / belongsTo / package" (`AttributeDefaultConstraints`); GraphQL
  `Multiplicity { SINGLE, LIST, BELONGS_TO, PACKAGE }`.
- **Live graph.** `LocalCache.graph` (rapla-core) — `updateDependencies` on every allocatable put/remove stores each
  edge in both directions: `BelongsTo` / `BelongsToTarget`, `Packages` / `PackagesTarget`. Read transitively by
  `getDependentRef` / `getDependent` (`fillDependent`: visited set + depth > 20 throws) for conflicts
  (`LocalAbstractCachableOperator.intervalCandidatesMs`) and calendar expansion (`CalendarModelImpl`). Every pod and
  the Swing client (`RemoteOperator`) keep it current. **No direct-neighbour read exists** — `GraphNode` is
  package-private.
- **`DependencyIndex`** (rapla-server readmodel) duplicates the graph and is not wired (PRD 082). Not touched.
- **Store-time cycle check.** `LocalAbstractCachableOperator.checkConsitency` → `checkBelongsTo` / `checkPackages`:
  per kind, depth > 20 → `error.belongsToCycle` / `error.packageCycle`, self-reference → `…CantReferToSelf`. A mixed
  cycle (A belongsTo B, B belongsTo C, A packages C — C is A's child and ancestor) is accepted. (A belongsTo B plus
  B packages A is the same edge twice, not a cycle — D3.)
- **Other walks.** `StructuralTypeFetchers.idInMatchesHierarchy` walks belongsTo upward with a counter (≤ 21 steps,
  own `belongsToParent` helper). Swing `TreeFactoryImpl` (`addBelongsToNodes`, `fillPackages`) recurses without a
  guard but never mixes the kinds.
- **Deployment data.** One production deployment uses belongsTo on five room/course-style types (OQ2), and package on a course-group type; the concrete keys are in the private dhbwrapla docs (`docs/prd/rapla-120-hierarchy-data.md`).
- **Demo Hochschule** has no hierarchy — only `categorization` on `room.building`, `course.program`,
  `equipment.storageLocation` (seed v4, `1bee9639f`); unaffected.

**Interim spike to replace (uncommitted, 2026-09-16):** `ResourceTreeRules.belongsTo` / `packageIds` + tier-2 tests,
`StructuralTypeFetchers.ALLOCATABLE_BELONGS_TO` / `ALLOCATABLE_PACKAGE_IDS` + `readableResource`, schema fields
`belongsTo: ID` / `packageIds: [ID!]!`, extended `ResourceTreeFieldsGraphQLTest`. Phase 2 removes the fields and the
rules methods; the `readableResource` predicate and the test seed (DozGruppe → Burns, Room A66.1 → Room A66) are kept.

## Goal

- `{ resources { id parents { id } children { id } } }` as admin on dhbw-test: a room lists its building in `parents`,
  a building lists its rooms in `children`, a course group lists its courses in `children`.
- Tier-3 leak test: a non-admin never receives the id of an unreadable parent or child.
- SPA picker on the test deployment: building chip → a building expands to its rooms (→ sub-rooms); course-group chip → a
  group expands to its courses. The same holds in the "Alle" / "Favoriten" / "Zuletzt" chips (D9).
- Tier-2: storing a mixed belongsTo/package cycle is rejected; room belongsTo building **and** building packages room
  stores.

## Scope

### In scope
- Direct-neighbour read on `LocalCache.graph`, exposed on the operator.
- GraphQL `Resource.parents` / `Resource.children` (§12 filtered).
- Store-time cycle check over both kinds with normalised direction.
- SPA picker tree nesting by `parents`.

### Out of scope
- Renaming the constraints or `Multiplicity` values (D1).
- More than one belongsTo / package attribute per type (D5).
- Checking data loaded at startup or imported without a store event (D6).
- Wiring or deleting `DependencyIndex`.

## Plan

### Phase 1 — Direct neighbours on the existing graph
*Done 2026-10-03 (uncommitted): `ResourceNeighboursTest`.*
- [x] `LocalCache.getParentRefs(ref)` = connections `BelongsTo` ∪ `PackagesTarget`; `getChildRefs(ref)` =
      `BelongsToTarget` ∪ `Packages`. O(degree); unknown node → empty.
- [x] `StorageOperator.getParents(Allocatable)` / `getChildren(Allocatable)` in `AbstractCachableOperator`, resolved via
      `tryResolve`, unresolved ids dropped (same idiom as `getDependent`).
- [x] Tier-2 test: Room A66.1 ↔ Room A66 (belongsTo, `resource1.a1`), DozGruppe ↔ Burns Monty (package,
      `resource2.a1`), neighbours follow an edited value.

### Phase 2 — Store-time cycle check over both kinds
*Done 2026-10-03 (uncommitted): `checkResourceCycles`, `ResourceCycleCheckTest` (9). Load-time cycle check (`removeInconsistentEntities`) dropped with the old per-kind checks (D6); `error.packageCycle` now unused, key kept.*
- [x] Replace `checkBelongsTo` / `checkPackages` with one check: for **every** resource in the event, walk **up** over
      normalised parents (D3) with a visited set; reaching the start → reject.
- [x] Overlay: event objects contribute their **new** belongsTo / package values; cache edges whose source is in the
      event are ignored (stale); everything else from Phase 1's neighbour read.
- [x] Messages: self-reference keeps `…CantReferToSelf`; a cycle uses `error.belongsToCycle` (no new i18n key). Depth
      limit 20 as cycle detector goes (kept as a separate rule, D10).
- [x] Tier-2 tests: see § Tests.
- [x] (done uncommitted, rapla-impl: `MAX_HIERARCHY_LEVELS`, counted in edges — 10 edges = 11 resources; OQ7) Depth limit kept (D10): a store is rejected when the longest chain through a stored resource (ancestors +
      descendants, both kinds normalised) exceeds 10 levels; message as before (`error.belongsToCycle`). Tier-2: a
      10-level chain stores, extending it to 11 from either end is rejected.
- [x] (done uncommitted, rapla-impl: `logResourceCyclesOnLoad`, `ResourceCycleCheckTest` 16/16) Load time (OQ5): an ERROR log per pre-existing cycle with its resource ids, read from the attribute values; no
      removal, loading continues. Tier-2 test: a cycle injected without a store event is logged and stays loaded.
      Plus: storing a cycle member with the edge removed (cycle broken) stores; any other edit of a cycle member is
      rejected with `error.belongsToCycle` until the cycle is broken (overlay reads the new values).

### Phase 3 — GraphQL fields
*Code done 2026-10-03 (uncommitted, rapla-impl2 + rapla-impl for idIn); default lanes green. Open: 8051 restart, then arm `parents { id }` in the SPA.*
- [x] `schema.graphqls` `type Resource`: `parents: [Resource!]!`, `children: [Resource!]!` with descriptions.
- [x] `StructuralTypeFetchers`: two fetchers over the Phase 1 operator methods, filtered by `readableResource` (admin or
      `canRead`).
- [x] Remove the spike fields `belongsTo` / `packageIds`, their fetchers and `ResourceTreeRules.belongsTo` / `packageIds`
      with their tests.
- [x] (rapla-impl, user go 2026-10-03; done uncommitted: `StorageOperator.isSelfOrAncestorIn`, `AppointmentResourcesIdInHierarchyGraphQLTest`; §12 residual case accepted, OQ4) `idInMatchesHierarchy` walks up over `getParentRefs` (both kinds, several parents) with a visited set instead of
      the belongsTo-only `belongsToParent` chain with its counter; a course matches its group's id like a room its
      building's (same expansion as `getDependentRef`). A pre-existing cycle (D6) must not loop. Tier-3: course
      matched via group id; cyclic data terminates; an `idIn` id the caller cannot read matches nothing (§12).
- [x] Tier-3 `ResourceTreeFieldsGraphQLTest`: admin sees both directions; monty (Room A66 and Burns unreadable) gets `[]`
      and the raw `{ resources { id parents { id } children { id } } }` body contains neither id; mutation check (drop the
      filter → red).
- [x] Announce the schema change; server restart on 8051 before the SPA query uses the fields (AGENTS.md §7a).

### Phase 4 — SPA picker tree
*Code done 2026-10-03 (uncommitted, rapla-impl2); Vitest green (resource-tree 19/19, resource-picker 8/8, 96/96 in the run), `tsc --noEmit` ok. `parents { id }` is out of the live query until Phase 3 is in the schema and 8051 restarted (AGENTS §7a); `parentIds` maps to [] meanwhile. Group "alle wählen" (`membersOf`) does not descend into resource nodes (D7); in a search, hits under a parent kept only as path still count.*
- [x] `resource-selection-store.ts`: lean list query adds `parents { id }`; `ResourceItem.parentIds`.
- [x] `resource-tree.ts` `buildTree`: a resource node's children = resources of the whole lean list (any type) whose
      `parentIds` contain it, recursively, with a path guard; a node with several parents appears under each; resources
      keep their place under their own type (Swing parity); nesting also applies under categorization group nodes (D8).
- [x] `visibleRows` / `pathKeysTo` handle expandable resource nodes; `resource/resource-picker.component.ts` shows the ▸/▾ toggle
      on resource rows with children (reuse `toggleGroup`), label click still steps; no count / "alle wählen" on
      resource nodes (D7).
- [x] Tier-5 `resource-tree.spec.ts`, tier-6 `resource-picker.component.spec.ts` (§ Tests).
- [x] (done uncommitted, rapla-impl2: `childIndex` / `resourceNodes`; auto-open of the path to a selected child only in the type tree — OQ6) Flat chips too (user, 2026-10-03: "im alle/recent/fav modus nicht"): in "Alle" / "Favoriten" / "Zuletzt"
      (`treeMode()` false → flat `paged()` rows) a resource row with children gets the same toggle and expands to its
      children from the whole lean list (`store.resources()`), recursively with the path guard; paging (D12) still
      counts top-level rows only; search hits stay expandable (D9).

### Phase 5 — Docs and rollout
- [x] PRD 119: S2 / D2 point to this PRD (file is rapla-concept's working copy — coordinate first).
- [x] `docs/graphql.md`: parents / children vs. constraints; `docs/architecture/`: the invariant (D3) and the graph.
- [ ] Signed build + deploy to dhbw-test; check the Goal probes.

## Tests

- **Tier 2 — neighbours:** Phase 1 cases.
- **Tier 2 — cycle check** (`FacadeTestSupport`):
  - mixed cycle in one store → `RaplaException`;
  - mixed cycle closed by the second of two stores → rejected;
  - A belongsTo B **and** A packages B → rejected;
  - room belongsTo building **and** building packages room → stores (same edge twice);
  - course in two course groups (diamond) → stores;
  - pure belongsTo cycle, pure package cycle, self-reference in either kind → rejected;
  - fixture hierarchies still store.
- **Tier 3 — leak:** `ResourceTreeFieldsGraphQLTest` as in Phase 3.
- **Tier 5:** building ▸ room ▸ sub-room; course under two groups; a cyclic input does not loop.
- **Tier 6:** under a type chip a building row has a toggle; expanding shows its rooms.
- Commands: `mvn -pl rapla-server -am test -Dtest=…`, `mvn -pl rapla-app -am test -Dtest=ResourceTreeFieldsGraphQLTest`,
  `npx vitest run` on the two specs.

## Open Questions

- **OQ1** — Does the SPA list need `children` too, or only `parents`? *Resolution (2026-10-03):* only `parents { id }`;
  children are derived in the browser (`childIndex`, d38a499fa).
- **OQ2** — Two course-style types of a production deployment were thought to carry `belongsTo=true` on a *category*
  attribute. *Resolution (2026-10-03, checked in the production export):* not a category — the attribute is a resource
  reference (`rapla:allocatable`, no type constraint) to a resource type of its own, so it is a valid belongsTo and the
  courses nest under it; no data error.
- **OQ3** — Log a warning when a pre-existing cycle is met at load time? *Resolution:* superseded by OQ5 (ERROR).
- **OQ4** — `idIn` matches via ancestors without a read check, so an unreadable id that hits reveals it is an
  ancestor of a readable resource (pre-existing, unchanged by the fix). (a) filter `idIn` to readable ids first, or
  (b) stop the walk at unreadable ancestors. *Resolution (user, 2026-10-03):* neither — normally a user may read the parent
  resource, so the residual case is accepted; no change.
- **OQ5** — The old load path deleted resources failing the per-kind check (`removeInconsistentEntities` →
  `DBOperator` `removeObjects`; also chains deeper than 20). Phase 2 drops this (D6). Log a warning instead (cf. OQ3)?
  The removal deleted whole resources (`RaplaSQL.remove` → DELETE), not just the links. Rapla 2 on a shared DB keeps
  this load path, but parallel operation is a two-week exception for one customer (user, 2026-10-03); Rapla 2 is not
  changed. *Resolution (user, 2026-10-03):* no delete, no warning — Rapla 3 logs an **ERROR** at load naming the
  ids of the resources on each cycle; loading continues. The detection reads the belongsTo / package values (not the
  graph, which cannot show a 2-cycle). An existing chain deeper than 10 (D10) is logged as ERROR too.
- **OQ6** — In the flat chips, should the path to a selected child open automatically (as `pathKeysTo` does in the
  type tree, PRD 123 D8)? *Resolution (user, 2026-10-03):* no — in "Alle" the child is its own row anyway.
- **OQ7** — D10 "10 levels": edges (11 resources in a chain, as implemented) or resources (10, constant 9)?
  *Resolution:* pending user.
- **Graph limit (finding 2026-10-03):** `GraphNode.connections` holds one type per neighbour pair, so a loaded 2-cycle
  A↔B (both belongsTo) appears in one direction only; walks terminate but cannot see it. Store-time detection is
  unaffected (overlay); D6 tests use 3-node cycles.

## Decisions locked

**D1 — Constraints stay input; UIs read fixed graph fields (user, 2026-09-16).** `belongsTo` and `package` remain
attribute options with deployment-specific attribute names; GraphQL `Multiplicity` and storage keys unchanged.
Graph-building clients use `Resource.parents` / `Resource.children` only and never see which attribute or kind
produced an edge. Rejected: renaming the constraints to parent/children in editor and enum (migration, Rapla 2 data
compatibility, no visible gain); per-kind fields `belongsTo` / `packageIds` (the spike — leaks storage direction into
clients).

**D2 — Read the graph the server already keeps.** `parents` / `children` come from `LocalCache.graph`, the structure
conflicts and calendar expansion use, via a new direct-neighbour read. Tree and conflict logic cannot diverge. Rejected:
scanning all resources for reverse edges (O(n²) on DHBW), a new index, wiring `DependencyIndex`.

**D3 — Invariant: no resource is ever its own transitive parent or child (user, 2026-09-16).** Both statements are one
(ancestor of itself ⇔ descendant of itself). Direction is normalised before any walk: a belongsTo value is a
**parent** of the holder; a package value is a **child** of the holder. Consequence: room belongsTo building plus
building packages room is the same edge twice, not a cycle.

**D4 — Store-time check walks up from every stored resource.** Every new edge is incident to a stored resource, so any
new cycle passes through it; following a directed cycle in the parent direction returns to the start, so a cycle
"created below" (new package value) is caught as long as the event's new values are read (overlay). Cost = the
ancestors of the stored resource, not the whole graph; the visited set makes diamonds free. Rejected: separate
per-kind checks (today's gap), a depth limit as cycle detector (rejects deep chains, misses nothing more).

**D5 — At most one belongsTo and one package attribute per type stays (user, 2026-09-16).** The existing validation is
kept; the graph reads that one attribute.

**D6 — Legacy data: no code (user, 2026-09-16).** Data loaded at startup or imported without a store event is not
checked. The visited set is the safety net: a pre-existing cycle elsewhere neither loops the walk nor blocks storing an
unrelated resource.

**D7 — A resource node stands for its subtree; no "alle wählen" on it (user, 2026-10-03).** Selecting a building or
course group already selects everything below it: Swing expands the selection with `getDependent` (down over
`Packages` / `BelongsToTarget`, `CalendarModelImpl.getAllAllocatables`), and the server's appointment lookup does the
same (`LocalAbstractCachableOperator.getAppointments` → `cache.getDependentRef`). Resource nodes therefore get the toggle and are
selectable themselves, without count or "alle wählen"; type and category group nodes keep theirs (PRD 119 D2,
PRD 123 D8). Rejected: "alle wählen" per resource node (one chip per room, same calendar result).

**D8 — Nest under categorization groups too (user, 2026-10-03).** Swing nests only resources outside a categorization
group (`TreeFactoryImpl`: `fillPackages` / `addBelongsToNodes` only on uncategorized nodes). Neither commit
(`e39c6705c`, `f5399eb7c`, rapla2, 2016) gives a reason; the likely cause is the 1:1 `objectToNamedNode` map, while a
categorized resource has one node per value. The restriction prevents neither duplicates (Swing lists a part under
its type and its parent anyway) nor cycles (both Swing recursions have no guard). The SPA nests everywhere; Phase 4's
path guard handles cycles. Rejected: copying the restriction (a building with a category value could not be expanded).

**D9 — Nesting in every chip, children from the whole list (user, 2026-10-03).** The hierarchy is not a type-chip
feature: in "Alle", "Favoriten" and "Zuletzt" a resource row with children expands as under a type chip (D7 rules,
path guard). Children come from the whole lean list, not from the chip's list — a favourite building shows all its
rooms, matching what selecting it brings into the calendar (D7). Paging (PRD 119 D12) counts top-level rows only.
Rejected: tree only under type chips (first cut — the hierarchy disappeared in the default chip); children filtered to
the chip (favourites would show a partial building).

**D10 — Chains are limited to 10 levels (user, 2026-10-03: "mehr als 10 — da gibt es keinen Use Case").** Phase 2 dropped the depth limit together with the old
cycle check, but `LocalCache.fillDependent` (calendar expansion, `getDependentRef`) still throws beyond depth 20, so a
deeper chain would break appointment queries for its top resource. The limit returns as its own store-time rule next
to the cycle check (D4 rejected it only as a cycle detector), at 10 — below `fillDependent`'s 20; an existing chain
of 11–20 stays loaded and queryable, is logged as ERROR at load (OQ5), and its resources can only be stored once the
chain is shortened. Rejected: allowing deep chains and dropping the limit in `fillDependent` (no real need, larger change).
