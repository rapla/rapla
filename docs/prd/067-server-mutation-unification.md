# PRD 067 — Facade split: sync explicit-user core + Swing client facade

**Status:** draft — opened 2026-06-10; design discussion in progress (decisions
D1–D3 below locked, OQs open)

## Goal

**Measurable end state: zero `RaplaFacade` calls in server code** (rapla-server,
rapla-app, plugin `*/server/*`, dhbwrapla server tiers), enforced by a
**module wall**: `RaplaFacade` + `FacadeImpl` + `ClientFacadeImpl` move from
rapla-core to rapla-client (D9). Verified 2026-06-10: rapla-server's pom has
**no** rapla-client dependency (AGENTS.md's PRD-005-D3 "server depends on
rapla-client" note is stale — `RaplaBuilder` lives in rapla-core), so the move
makes server-side facade usage a compile error. Until the move lands, the
transitional guards are bean-removal from `ServerCoreConfig` + an arch-test
forbidding the import in server packages. The facade completes §4's rule and
becomes the Swing client API only. Server code uses `StorageOperator` (queries,
edit, persistence + the new lifecycle methods, D8) + GraphQL
(business-operation API, HTTP or in-process).

**D9 precondition — 17 rapla-core files reference `RaplaFacade`** (outside the
facade impl itself), three buckets:
1. *Move with the facade* (client code parked in core):
   `org.rapla.client.internal.{HTMLInfo, ReservationInfoUI, AppointmentInfoUI,
   ClassificationInfoUI}`, `facade.client.ClientFacade`.
2. *Migrate off the facade onto operator* (server still needs them in core):
   `RaplaBuilder`, `HTMLRaplaBuilder`, `TableConfig`,
   `DefaultRaplaTableColumn`, `ExchangeConnectorConfig`,
   `EventTimeCalculatorFactory`, `AppointmentNoteFunctions`,
   `TimeslotProvider`, `PeriodModel`, `RaplaComponent`.
3. ~~*Audit — suspicious references*~~ **CLOSED (2026-06-10):** both were
   javadoc-only — `ReservationImpl`'s stale `@see` removed same day;
   `SyncStorageOperator`'s prose mentions get reworded during migration.

D9 final calls (2026-06-10, "fastest" criterion):
- **Keep the `org.rapla.facade` package name** — classes move to rapla-client
  module without repackaging (zero import churn; split package is fine on
  classpath/fat-JAR — revisit only if rapla ever adopts JPMS).
- **Bucket 2 migrates incrementally** inside the phase-3 changes that touch
  each class's callers anyway — no dedicated sweep. The migrations are
  pebbles, not icebergs: `RaplaBuilder` — the biggest — uses the facade for
  exactly `getPermissionController()` + two `getPreferences` tooltip flags,
  all operator pass-throughs (constructor swap + 3 call edits).

Test note: `FacadeTestSupport` (rapla-server + dhbwrapla tests) constructs
`FacadeImpl` — keep via **test-scoped** rapla-client dependency or migrate the
test base to operator; the production wall is unaffected.

Mechanically, split `RaplaFacade` into two layers so that entity
creation/mutation logic exists exactly once and the acting user is always
explicit:

1. **Sync core** (rapla-core) — synchronous, **stateless, no `workingUserId`
   field**. Every read/write that depends on a user takes `User` explicitly.
   Carries the canonical entity-lifecycle logic: `setNew` (timestamps, id
   allocation, resolver wiring, owner, null-owner guard), `newReservation`
   (`canCreate` check + `copyPermissions`), `cloneReservation` (appointment
   re-id, restriction re-map, `lastChanged` reset), edit-clone, `UpdateEvent`
   assembly, sync store. Consumers: GraphQL mutation controllers, REST/import
   controllers, dhbw sync jobs, and the client facade below.
2. **Swing client facade** (client tier) — owns the working-user session state
   (today `FacadeImpl.workingUserId`, set only by `ClientFacadeImpl`
   login/logout) and the async Promise wrappers (`newReservationAsync`,
   `editAsync`, `cloneAsync`, `dispatch`, …). Each wrapper resolves the working
   user and delegates to the sync core. Zero duplicated logic.

After the split, server modules **cannot name** the working-user convenience
methods — the Dualis bug class becomes a compile error, not a runtime 500.

## Decisions locked (2026-06-10 discussion)

- **D1 — hard split, not a parallel service.** ~~Working-user methods leave
  `RaplaFacade`; sync part stays in core, client part moves to a Swing client
  facade.~~ **SUPERSEDED by D4+D8+D9** (spotted 2026-06-10, late): once the
  *entire* facade moves to rapla-client (D9) and the server uses
  operator+lifecycle only (D4/D8), no interface surgery on `RaplaFacade` is
  needed — the working-user conveniences are harmless in a client-only type,
  and `workingUserId`/`templateId` become *legitimate* fields (a client-session
  object holding client-session state; the original sin was sharing that object
  with the server, not the fields). Consequence: **the ~40 Swing call sites
  don't change at all** — `RaplaFacade`'s interface survives intact as the
  Swing API; only `FacadeImpl`'s internals change (delegate to
  `operator.getLifecycle(workingUser, templateId)`). What survives of D1's
  substance: one implementation of the logic (drift fixed by deletion), and the
  facade as pure delegation.
- **D2 — reads are included, as a rename of an implicit contract.** Only 4
  facade reads consult the working user (`getAllocatablesWithFilter`/
  `getVisibleAllocatables`, `getDynamicTypes(String)`, `getResourceRequests`,
  `canAdminResourceRequests`). Verified: the **only** caller of
  `setWorkingUserId` in the codebase is `ClientFacadeImpl` (login/logout);
  the server (`ServerCoreConfig:338`) never sets it. So on the server the
  working user is always null and all 12 server call sites (9 dhbw job/export
  sites, `Export2iCalConverter`, `ClassificationFilterUtil`,
  `DualisEventsLoaderImpl`) rely on the null → **unfiltered** branch. Migration
  is therefore zero-behavior-change: they map onto a *named* system read
  (`getAllocatablesAsSystem(filters)` — OQ3 resolved, see below), and explicit-user
  filtered variants (`getAllocatables(filters, user)`) exist for callers that
  want per-user visibility. The static helper
  `FacadeImpl.getDynamicTypes(operator, type, user)` already exists — promote
  it. Client facade keeps the no-arg conveniences delegating with the working
  user. Value: turns "filtered on client / everything on server, decided by a
  null field" into a type-level contract.
- **D3 — write primitive: `UpdateEvent` + dispatch.** `UpdateEvent` is already
  the common currency of the two real consumers: Swing's dominant write is
  `facade.dispatch(storeList, removeList)` (×13 → `operator.storeAndRemoveAsync`)
  and GraphQL calls `operator.dispatch(UpdateEvent)` directly; only REST/import
  uses bare `storeAndRemove(…, user)`. The sync core assembles the `UpdateEvent`
  (userId stamping, change records) and exposes a synchronous `store(...)`;
  the client keeps its async `dispatch` façade over the same assembly. Sync vs
  async is a wrapper concern, never duplicated logic (server uses `*Sync`,
  client keeps Promise — see memory note `no-promise-latch-on-server`).

## End state (discussion continuation, 2026-06-10 evening)

**D4 — no facade on the server at all; GraphQL is the API (PRD 035/060).**
Server-side call inventory (rapla-server + rapla-app + dhbwrapla main):

- **Async facade mutations on the server: 0** (the Dualis call fixed today was
  the last). Only 4 `waitFor` sites unwrap Promise-only *reads*
  (`getAllocatableBindings`, 2× `RaplaPruefungen` reservation queries, dhbw
  `PromiseWait`) — those reads need sync variants.
- **Sync facade mutations to migrate: 87** in ~30 files — `edit` ×40 + `store`
  ×24 (dominantly Preferences edit→store pairs in admin panels /
  `RaplaKeyStorage` / `RefreshSessionService` / `SynchronisationManager` + dhbw
  sync), `storeAndRemove`/`storeObjects`/`remove*` ×14, creation ×9.
  Layering oddity to unwind: `RaplaSQL.java:931` calls `facade.store(`.
- **Queries: 138 direct-operator vs 197 via facade**, but per tier: rapla-app
  (GraphQL) 79 operator / 5 facade — already operator-pure; rapla-server 49/124
  and dhbwrapla 10/54 — facade-heavy legacy.
- What the facade *adds* per category on the server: queries — nothing
  (pass-throughs; working-user filter dead, always null); `edit` — nothing
  (`editList` = cast + `operator.editObjects(list, workingUser)` + cast);
  store — almost nothing (transient-category handling to verify); **creation/
  clone — the only real value** (`setNew`, `canCreate`, `copyPermissions`,
  `cloneReservation` re-id rules), which the operator lacks and GraphQL
  re-implemented with drift.

Therefore the sync core shrinks from "facade minus working user" to ~200–300
lines of entity-lifecycle logic — and **D8 (2026-06-10, late): it's reached
through the operator as a context-bound, immutable object**. `EntityLifecycle`
is a plain cohesive class in rapla-core, **constructed with its context**:
`operator.getLifecycle(user)` / `operator.getLifecycle(user, templateId)` —
factory methods on `StorageOperator`, implemented in `AbstractCachableOperator`
(rapla-core, 980 lines today, the shared base of BOTH
`LocalAbstractCachableOperator` server-side AND `RemoteOperator` client-side —
one implementation, two tiers, for free). Mirrors the
`operator.getPermissionController()` idiom. Call shape:
`operator.getLifecycle(caller).newReservation(cls)` — no user/template
threading through signatures. Properties:

- **"always have a user" enforced at construction** — `getLifecycle(null)`
  throws; a lifecycle without identity is unconstructible;
- **immutable per-operation instance** (operator ref + `User` + templateOrNull)
  — the legitimate version of what `FacadeImpl` got wrong: same ergonomics as
  its `workingUserId`/`templateId` fields, but no shared mutable state, no
  cross-thread leak, nothing to reset. Created per operation, never cached;
- **reads stay on the operator** — the lifecycle is the write/creation context,
  not a query surface (D2 unaffected); the client facade constructs it per call
  from its session state, the only place mutable session state survives.

Rationale for operator placement:

- every dependency is `this`: `createIdentifier`, `getCurrentTimestamp`,
  `getPermissionController`, and the resolver (`setNew` does
  `entity.setResolver(operator)`);
- `RemoteOperator` already implements **sync** `createIdentifier` (blocking
  remote call) → resolves OQ4 (no async id seam needed; client facade wrappers
  run off-EDT as today);
- no new bean → resolves OQ6 (GraphQL controllers, plugins, facade already hold
  the operator);
- precedent: `LocalAbstractCachableOperator` already hand-creates template
  `AllocatableImpl`s (lines ~870, ~3848) — fold into the new methods later;
- naming question dies (no separate component to name).

Boundary sentence becomes: **the operator is the entity API — persistence and
lifecycle; GraphQL is the remote API; the facade is the Swing convenience
layer.** Architecture:

```
GraphQL mutations/queries            ← THE API (SPA, MCP, integrations)
    ↓
StorageOperator (persistence + entity lifecycle)
    ↓
storage
```

`RaplaFacade` survives only client-side (Swing) — and since rapla-core is
shared, the Swing facade delegates to the *same* `EntityLifecycle` running
client-side; both permanent write paths (Swing → `RemoteOperator` →
`/api/storage/dispatch`; SPA/MCP → GraphQL → `operator.dispatch`) converge on
the same `UpdateEvent` primitive and the same creation semantics. This resolves
OQ2: the facade keeps its name and becomes honestly the client API; no server
rename debate.

**D5 — GraphQL is usable by plugins without HTTP.** `HotSwappableGraphQlSource`
implements `GraphQlSource`; Spring Boot auto-wires `ExecutionGraphQlService`
over it — in-process execution with the same schema/data fetchers/permission
gates. Caller identity is thread-local (`requireCaller()` reads
`SecurityContextHolder`), so in-process callers establish identity by
populating the security context (a deliberate seam for system/import users —
§12 review required). Tiering rule:

| Tier | Who | Granularity |
|---|---|---|
| GraphQL (HTTP or in-process `ExecutionGraphQlService`) | SPA, MCP, integrations, plugins acting on behalf of a user | business operations |
| `EntityLifecycle` + `StorageOperator` | sync jobs, bulk reconcile, framework internals, the GraphQL layer itself | entity/infrastructure |
| Facade | Swing client only | client session |

Bulk sync (e.g. Dualis reconcile over thousands of allocatables) stays on
operator + lifecycle: GraphQL's map inputs lose type safety, results are
projections not live entities, and per-field fetch overhead is material
(see PRD 035 perf hotspots).

**Priority flip:** since GraphQL is the API, the two mutation-controller drifts
are defects in the canonical write path, not low-urgency curiosities — fix them
first (by migrating GraphQL onto `EntityLifecycle`) before SPA writes / MCP
arrive.

**D7 — GraphQL mutation internals are unshipped → rewrite freely; risk lives in
the facade extraction.** The mutation controllers have no consumer (no SPA
writes, no MCP yet); only their own tests exercise them. Consequences: (a) no
red-test-first ritual for the two drifts — correct behavior (permission parity,
appointment re-id on copy) is written as ordinary spec tests of
`EntityLifecycle`, not bug-regression locks; (b) controller internals can be
replaced wholesale — what must survive is the **API contract** pinned by the
existing `*MutationControllerTest`s (verb names, validation, error codes/paths
from PRDs 056/063), which stay green through the swap; (c) the only delicate
step in the whole plan is **phase 1** — `FacadeImpl` is live under the Swing
client, so the extraction must be a behavior-identical delegation refactor with
golden-master tests, done as the smallest possible diff. Everything after
phase 1 is low-risk call-site work.

**D6 — Dualis import wizard is legacy Swing; successor is Angular + GraphQL.**
The Swing import wizard (and its `/api/externaleventimport/*` REST contract)
lives only as long as the Swing client needs it. Its internals migrate to
`EntityLifecycle` (cheap, shared), but **no investment in the REST endpoint
shape** — the replacement is an Angular wizard driving GraphQL mutations
(future PRD; the external-event-import metadata-driven contract from PRD 003/012
can inform the GraphQL schema). Same reasoning applies to other Swing-serving
import REST endpoints (iCal import) as their SPA successors arrive.

**D10 — owner is always the acting user at creation; ownership changes only via
the explicit changeOwner mutation.** The lifecycle binds the acting user
(permission checks, `lastChangedBy`, `UpdateEvent.userId`) and `setNew` sets
owner = actor unconditionally — **no owner parameters anywhere in
`EntityLifecycle`**. Rationale: owner-at-create is audit erasure — an entity
owned by X "from birth" carries no record that ownership was assigned rather
than earned by creating; create-then-changeOwner leaves two explicit records
and notifies the new owner (nothing appears silently in someone's name, with
their request-approval rights attached). Every legitimate flow
(responsible-person setup, bulk migration, admin UI) composes from create +
changeOwner with a better audit trail. Consequences: the `ownerId` input on
GraphQL `createAllocatable` is **removed** (unshipped, D7 — amend PRD 063); the
ownership-privilege gate lives in exactly one place, `changeOwner`
(`lifecycle.changeOwner(entity, newOwner)` — currently `isAdmin`; widen to
admin-or-group-admin per the `canAdminUsers` convention only when a real
group-delegation case materializes). Phase-2 verify: confirm owner-change is
also enforced at operator dispatch (server-side), not only in the GraphQL verb
and Swing setowner menu visibility.

## D11 — `EntityLifecycle` API (locked 2026-06-10, late)

Actor-bound, immutable, created per operation via `operator.getLifecycle(user)`
/ `operator.getLifecycle(user, templateId)`; `getLifecycle(null)` throws.
Inject the operator, never the lifecycle; create it where the user is resolved
(JWT / session / job config). Never a field, never a bean.

```java
// creation — owner = actor, always (D10); canCreate gate; copyPermissions;
// KEY_TEMPLATE stamp from binding
Reservation  newReservation(Classification cls);
Allocatable  newAllocatable(Classification cls);
Appointment  newAppointment(LocalDateTime start, LocalDateTime end);
Category     newCategory();
RaplaMap<T>  newRaplaMap(...);          // the newRaplaMapForMap family

// clone / copy — copy's owner = actor; appointment re-id + restriction re-map
// + lastChanged reset; KEY_TEMPLATE vs KEY_TEMPLATE_COPYOF branch from binding
<T extends Entity> T clone(T entity);
Collection<Reservation> copyReservations(Collection<Reservation> src,
                                         LocalDateTime begin, boolean keepTime);

// edit / store — attributed writes: lastChangedBy = actor,
// UpdateEvent.userId = actor (server audit improves: today's 64 server
// edit/store calls run unattributed with null)
<T extends Entity> T edit(T entity);
<T extends Entity> Collection<T> editList(Collection<T> entities);
void store(Entity... entities);
void storeAndRemove(Entity[] store, ReferenceInfo<?>[] remove);

// ownership — the ONE gated ownership mutation (D10); atomic: gate → edit →
// setOwner → store immediately, one change record. Gate: isAdmin (widen to
// admin-or-group-admin per canAdminUsers convention only on real demand)
void changeOwner(Ownable entity, User newOwner);
```

System tier (actor-less, named — the D2 "declared, not accidental" move):
- `operator.storeAndRemoveAsSystem(store, remove)` — today's null-user store
  semantics, declared. For archiver / notification / exchange-sync jobs. The
  SYSTEM-user question dissolves: no synthetic user entity; change records
  carry null userId as today. Arch-test bans it in `*/web/*` request code.
- `operator.editObjects(list, null)` stays as the low-level edit (edit is
  audit-only; store is the security-relevant op worth naming).

## Background — the drift this fixes

Server entity creation currently lives in three places that have already diverged:

| Path | Create | Store | User source |
|---|---|---|---|
| `FacadeImpl.newReservation/newAllocatable/newAppointmentWithUser` + `setNew` | full (canCreate, timestamps, id, resolver, owner, copyPermissions) | `facade.storeAndRemove(…, user)` → `operator.storeAndRemove` | explicit `user` (client variants default to `getWorkingUser()`) |
| GraphQL `ReservationMutationController` / `AllocatableMutationController` | hand-rolled vs operator | `operator.dispatch(UpdateEvent)` + re-resolve | JWT caller (`requireCaller`) |
| REST/import (`ICalImportController`, Dualis `DualisEventsLoaderImpl`, dhbw `semesterplan/ImportController`, `AbstractRaplaMapping`) | `facade.newReservation/newAppointmentWithUser` | `facade.storeAndRemove(…, user)` | explicit session/import user |

The GraphQL controllers don't just mimic *create* — they hand-roll **edit
(clone-before-mutate), copy/clone, move, and changeOwner** too. Two confirmed
drifts:

1. **`createReservation` omits `copyPermissions`** —
   `PermissionContainer.Util.copyPermissions(type, reservation)` (FacadeImpl:702)
   is not called, so GraphQL-created reservations don't inherit type-default
   permissions, while facade/Swing/import-created ones do.
   `AllocatableMutationController` *does* copy (FacadeImpl:734). §12-adjacent.
2. **`copyReservations` omits new-appointment-id allocation** —
   `facade.cloneReservation` removes the cloned appointments, allocates **fresh
   appointment IDs** via `setNew(...)`, re-adds them, re-maps allocatable
   restrictions, and resets `lastChangedBy`/`lastChanged`. The GraphQL copy only
   re-IDs the reservation + `setOwner` + `a.move(shift)`, leaving the cloned
   appointments carrying the **source** reservation's appointment IDs — an
   id-collision/aliasing hazard on dispatch. (Needs a confirming test; flagged
   as a likely bug.)

Map of what GraphQL re-implements vs the facade:

| GraphQL op | Facade equivalent | Drift |
|---|---|---|
| `createReservation` / `createAllocatable` | `newReservation`/`newAllocatable` + `setNew` | reservation create missing `copyPermissions` |
| `copyReservations` | `clone`/`cloneReservation`/`copyReservations` | missing appointment re-id + `lastChanged` reset |
| `updateReservation` | `edit` (clone-before-mutate) | manual `.clone()` instead of shared path |
| `moveReservations` | entity `Appointment.move()` | entity-level; lower risk |
| `changeReservationOwner` | edit + `setOwner` | manual |

**Bug already fixed that motivated this (2026-06-10):** `DualisEventsLoaderImpl`
used `facade.newAppointmentAsync(interval)` (a client method that resolves
`getUser()`) → `"no user loged in"` 500 on the server thread. Replaced with
`facade.newAppointmentWithUser(start, end, user)`. The split makes that class of
bug unrepresentable on the server.

## Consumer map (writes)

| Consumer | Create/edit/clone | Write primitive | User source | Sync? |
|---|---|---|---|---|
| Swing client | full facade API (`newReservation`, `editAsync` ×7, `cloneAsync` ×8, `copyReservations` ×3, `editListAsync(ForUndo)`, …) | `facade.dispatch(UpdateEvent)` ×13 | working user (login session) | async (Promise) |
| GraphQL | hand-rolled (drifted) | `operator.dispatch(UpdateEvent)` | JWT caller | sync |
| REST/import (iCal, Dualis, dhbw sync) | `facade.newAppointmentWithUser` factory | `storeAndRemove(…, user)` | session/import user | sync |

~40 client write call sites total (mechanical migration); 12 server
working-user-read sites (see D2).

## Timing — no live GraphQL client consumer yet

As of 2026-06-10 the GraphQL mutations have **no client caller**: the Angular SPA
is effectively read-only (single `POST /api/table/reservations`, which is a
query; no `/graphql`, no `gql`/Apollo). The Swing client writes via
`RemoteOperator` → `/api/storage/dispatch` (operator path), not GraphQL. The
mutation controllers' only callers are their `*MutationControllerTest`s
(+ planned MCP, PRD 060). So the drifts are **not user-reachable today** — which
makes this the right moment to unify, *before* the SPA wires up writes and bakes
in divergent behavior. Lower urgency, higher leverage.

## Scope

**In:**
- `EntityLifecycle` class in rapla-core, reached via `operator.getLifecycle()`
  (D8): explicit-user creation (`newReservation(type, user, templateOrNull)`,
  `newAllocatable(type, user)`, `newAppointment(start, end)`), edit-clone,
  `clone(obj, user, templateOrNull)` (= `cloneReservation` with appointment
  re-id + restriction re-map + `lastChanged` reset for reservations; `_clone`
  with `removeParent` for Appointment/Category), and
  `copyReservations(toCopy, begin, keepTime, user)` — keeping the date-shift
  copy here makes Swing period-copy and the GraphQL `copyReservations` verb one
  implementation. Explicit-user + named-unfiltered reads (D2), `UpdateEvent`
  assembly + sync `store` (D3). Stateless; tier-2 testable, no Spring.
- **Template context as explicit parameter** (resolves OQ5 for clone/copy):
  `FacadeImpl.templateId` has 4 consumers — `newReservation` stamping
  `KEY_TEMPLATE` (:704), reservation-query scoping (:242), the clone/copy
  branch (:1270 — stamps `KEY_TEMPLATE` in template mode, else strips it and
  stamps `KEY_TEMPLATE_COPYOF` from the source), and template getters. The
  lifecycle takes `templateOrNull`; the client facade keeps `templateId` as
  session state and passes it; server/GraphQL pass null. **Drift #3 found
  here:** GraphQL `copyReservations` skips the `KEY_TEMPLATE`/`COPYOF` handling
  entirely — copying a template reservation via GraphQL keeps `KEY_TEMPLATE`
  (the copy lands inside the template). Fixed by the shared implementation.
  Promise/working-user wrappers (`cloneAsync`, `cloneList`, `copyAppointment`,
  `editListAsyncForUndo` + undo machinery, template-scoped queries) stay
  client-facade-side.
- `RaplaFacade`/`FacadeImpl`/`ClientFacadeImpl` move to rapla-client **with
  interface intact** (D1-superseded/D9): working-user + template state stays as
  legitimate client-session fields; `FacadeImpl` internals delegate to
  `operator.getLifecycle(workingUser, templateId)`. **Zero Swing call-site
  changes.**
- Migrate: GraphQL mutation controllers, `ICalImportController`, Dualis import,
  dhbw sync/export paths (server tiers only).

**Out (for now):**
- The XML/SQL low-level `new …Impl` constructors in storage readers
  (`ReservationReader`, `RaplaSQL`) — deserialization, not creation.
- Client-only facade surface beyond mutation/reads: listeners
  (`addModificationListener`), `PeriodModel`, `CalendarModel` wiring — they move
  with the client facade but are not redesigned here.

## Plan (GraphQL first — it's the API; Swing last — most regression-sensitive,
98% Windows Swing user base)

1. **Add the lifecycle methods to the operator** (D8): declare on
   `StorageOperator`, implement in `AbstractCachableOperator`, porting `setNew`
   + `newReservation`/`newAllocatable`/`newAppointmentWithUser` +
   `cloneReservation` verbatim (incl. `copyPermissions`, null-owner guard).
   `FacadeImpl` methods become one-line delegations in the same change.
   Golden-master tier-2 tests: `facade.newX` vs `operator.newX` produce
   identical entities. *(The one risky step — FacadeImpl is live under Swing;
   smallest possible diff, behavior-identical by construction.)*
2. **Migrate GraphQL** controllers to the operator lifecycle methods — the
   canonical API write path; closes both drift gaps by deletion; add §12
   permission-parity + appointment-re-id spec tests (D7: no red-first ritual —
   unshipped code).
3. **Migrate legacy server plugins/jobs**: 87 facade mutation calls → operator
   (`editObjects`, `storeAndRemove`, new lifecycle methods for the 9
   creations); ~178 facade query calls → operator (mechanical). Incl. the 12
   unfiltered-read sites → named system reads (D2) and the `RaplaSQL:931`
   inversion.
4. **REST import controllers** (iCal, Dualis): internals onto the operator
   lifecycle methods only — endpoints are Swing-lifetime shims, replaced by
   Angular wizards on GraphQL mutations (D6; separate PRD when the SPA wizard
   work starts).
5. **D9 module move**: migrate the 17 rapla-core facade references (move
   client-internal classes to rapla-client; switch shared core classes to
   operator; audit `ReservationImpl`/`SyncStorageOperator`), then `git mv`
   `RaplaFacade`/`FacadeImpl`/`ClientFacadeImpl` to rapla-client and remove the
   server `RaplaFacade` bean. Compile wall replaces the transitional arch-test.
   `workingUserId`/`templateId` state stays client-side by construction.
6. **dhbwrapla coordination**: its server tiers (sync jobs, exporters) migrate
   in step 3's sweep; `FacadeTestSupport` via test-scoped dep or operator
   rewrite.

## Tests

- Tier-2 golden master: sync-core create/clone output identical to current
  `FacadeImpl` (id wired, resolver set, owner set, type-default permissions,
  timestamps, appointment re-id on clone).
- Tier-2: store path persists with explicit user; no working-user dependence
  (generalizes the Dualis regression test).
- Tier-3 GraphQL: permission parity (§12) + copy-appointment-fresh-id.
- Arch-test (OQ3): forbid named-unfiltered reads in `*/web/*` controllers,
  mirroring `ApiPrefixArchitectureTest`.
- Existing GraphQL controller tests + full Swing-relevant suite green through
  each phase.

## Open Questions

1. **Sync core vs operator boundary (one-sentence rule).** Proposal: *operator =
   persistence; sync core = entity lifecycle* (construction semantics,
   permission copy, clone rules, UpdateEvent assembly). §4's "server depends on
   StorageOperator, not RaplaFacade" gets amended: entity creation/mutation goes
   through the sync core.
2. **Naming + interface evolution.** Does the sync core keep the name
   `RaplaFacade` (interface shrinks; least churn for server code already
   injecting it) with the client side becoming `SwingClientFacade` extending/
   wrapping it? Or new name (`RaplaEntityService`?) and `RaplaFacade` is
   deprecated? Affects every injection point.
3. **Unfiltered reads policy — RESOLVED (2026-06-10):** named `AsSystem`
   variants, symmetric across reads and writes: `getAllocatablesAsSystem(filters)`,
   `getDynamicTypesAsSystem(type)`, `storeAndRemoveAsSystem(store, remove)` —
   plus ONE arch-test banning all `*AsSystem` calls in `*/web/*` request-driven
   code. No synthetic system user.
4. **Async id plumbing.** Client-side, `createIdentifierAsync` fetches id ranges
   over REST inside the async create methods. If the sync core calls
   `createIdentifier` synchronously, the client wrapper blocks a worker thread
   (it already does for much of this via `waitFor`) — or the core needs an
   id-provider seam. Decide during phase 1.
5. **Hidden client state in `FacadeImpl`.** `templateId` (template editing mode)
   leaks into `newReservation` (`setTemplateParams`). The sync core must not
   carry it — template becomes an explicit parameter or stays client-facade-side.
   Audit `FacadeImpl` for further session state beyond `workingUserId` +
   `templateId` during phase 1.
6. **Wiring:** singleton `@Bean` in `ServerServiceConfig` (per §4) for the sync
   core on the server; plain construction in `SwingClientConfig` for the client
   facade. Confirm statelessness (no request scope).
