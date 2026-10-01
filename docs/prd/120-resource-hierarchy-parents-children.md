# PRD 120 — Resource hierarchy for UIs: `Resource.parents` / `children` and a store-time cycle check over both kinds

**Status:** draft — 2026-09-16; interim spike landed 2026-10-01 (`Resource.belongsTo` / `packageIds` with read-scoped ids, `ResourceTreeRules`, commit 12fb1bfb4). Direction decided by the user in the same session (rulings D1–D6). The full plan (parents/children, cycle guard) is not implemented
yet; an interim spike (`Resource.belongsTo: ID`, `Resource.packageIds: [ID!]!`, uncommitted) is replaced by this PRD
(§ Implementation).
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
  cycle (A belongsTo B, B packages A) is accepted.
- **Other walks.** `StructuralTypeFetchers.idInMatchesHierarchy` walks belongsTo upward with a counter (≤ 21 steps,
  own `belongsToParent` helper). Swing `TreeFactoryImpl` (`addBelongsToNodes`, `fillPackages`) recurses without a
  guard but never mixes the kinds.
- **Deployment data.** One production deployment uses belongsTo on three room/course-style types plus on a category attribute (see OQ2), and package on a course-group type; the concrete keys are in the private dhbwrapla docs (`docs/prd/rapla-120-hierarchy-data.md`).
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
  group expands to its courses.
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
- belongsTo on a category attribute in one deployment (OQ2).

## Plan

### Phase 1 — Direct neighbours on the existing graph
- [ ] `LocalCache.getParentRefs(ref)` = connections `BelongsTo` ∪ `PackagesTarget`; `getChildRefs(ref)` =
      `BelongsToTarget` ∪ `Packages`. O(degree); unknown node → empty.
- [ ] `StorageOperator.getParents(Allocatable)` / `getChildren(Allocatable)` in `AbstractCachableOperator`, resolved via
      `tryResolve`, unresolved ids dropped (same idiom as `getDependent`).
- [ ] Tier-2 test: Room A66.1 ↔ Room A66 (belongsTo, `resource1.a1`), DozGruppe ↔ Burns Monty (package,
      `resource2.a1`), neighbours follow an edited value.

### Phase 2 — Store-time cycle check over both kinds
- [ ] Replace `checkBelongsTo` / `checkPackages` with one check: for **every** resource in the event, walk **up** over
      normalised parents (D3) with a visited set; reaching the start → reject.
- [ ] Overlay: event objects contribute their **new** belongsTo / package values; cache edges whose source is in the
      event are ignored (stale); everything else from Phase 1's neighbour read.
- [ ] Messages: self-reference keeps `…CantReferToSelf`; a cycle uses `error.belongsToCycle` (no new i18n key). Depth
      limit 20 goes.
- [ ] Tier-2 tests: see § Tests.

### Phase 3 — GraphQL fields
- [ ] `schema.graphqls` `type Resource`: `parents: [Resource!]!`, `children: [Resource!]!` with descriptions.
- [ ] `StructuralTypeFetchers`: two fetchers over the Phase 1 operator methods, filtered by `readableResource` (admin or
      `canRead`).
- [ ] Remove the spike fields `belongsTo` / `packageIds`, their fetchers and `ResourceTreeRules.belongsTo` / `packageIds`
      with their tests.
- [ ] Tier-3 `ResourceTreeFieldsGraphQLTest`: admin sees both directions; monty (Room A66 and Burns unreadable) gets `[]`
      and the raw `{ resources { id parents { id } children { id } } }` body contains neither id; mutation check (drop the
      filter → red).
- [ ] Announce the schema change; server restart on 8051 before the SPA query uses the fields (AGENTS.md §7a).

### Phase 4 — SPA picker tree
- [ ] `resource-selection-store.ts`: lean list query adds `parents { id }`; `ResourceItem.parentIds`.
- [ ] `resource-tree.ts` `buildTree`: a resource node's children = resources of the whole lean list (any type) whose
      `parentIds` contain it, recursively, with a path guard; a node with several parents appears under each; resources
      keep their place under their own type (Swing parity).
- [ ] `visibleRows` / `pathKeysTo` handle expandable resource nodes; `resource-selection.component.ts` shows the ▸/▾ toggle
      on resource rows with children (reuse `toggleGroup`), label click still steps; no count / "alle wählen" on
      resource nodes.
- [ ] Tier-5 `resource-tree.spec.ts`, tier-6 `resource-selection.component.spec.ts` (§ Tests).

### Phase 5 — Docs and rollout
- [ ] PRD 119: S2 / D2 point to this PRD (file is rapla-concept's working copy — coordinate first).
- [ ] `docs/graphql.md`: parents / children vs. constraints; `docs/architecture/`: the invariant (D3) and the graph.
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

- **OQ1** — Does the SPA list need `children` too, or only `parents`? *Resolution:* pending; plan loads only
  `parents { id }` and derives children in the browser.
- **OQ2** — Two course-style types of a production deployment carry `belongsTo=true` on a *category* attribute, which `AttributeImpl` should refuse (keys: dhbwrapla docs). Data error, or should the programme become a grouping via `categorization`? *Resolution:* pending; the graph ignores non-resource values either way.
- **OQ3** — Log a warning when a pre-existing cycle is met at load time? *Resolution:* pending; not required (D6).

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
