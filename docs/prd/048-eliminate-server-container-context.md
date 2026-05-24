# PRD 048: Eliminate `ServerContainerContext` + implement reload-on-restart

**Status:** in-progress — Phases 1 + 2 done & verified (rapla repo, 2026-05-18);
Phase 3 source migration done in dhbwrapla, compile/test of dhbwrapla pending
publication of the updated rapla artifacts to `~/.m2`
**Date:** 2026-05-18

## Goal

Remove `ServerContainerContext` and its `LegacyServerBridgeConfig` — a
pre-Spring container-config object and the dead remnant of a bridge that was
abandoned during the Spring Boot migration (PRD 001). Replace its facets with
proper Spring beans and direct `RaplaServerProperties` reads.

As part of the same change, replace the **dead restart stub** with a working
`ReloadService`: a client-triggered "restart" should reload all data, clear
the caches, and re-arm the operator's schedulers — **without restarting the
JVM / Spring context**.

## Background

### `ServerContainerContext` — what it is

A mutable plain-Java bag from pre-Spring rapla. Before the Spring Boot
migration rapla had its own hand-rolled DI container; `ServerContainerContext`
carried server bootstrap config into it. `LegacyServerBridgeConfig` was named
for a *bridge bean* (`ServerServiceContainer` via `ServerCreator.create()`)
that PRD 001 found non-viable and deleted — the class kept the name but now
only produces `raplaLogger()` and `serverContainerContext()`.

### Facet inventory

| Facet | State today | Replacement |
|---|---|---|
| `Map<String,DataSource>` | the only real content; built by `LegacyServerBridgeConfig` (PRD 045 Phase 3) | proper `@Bean DataSource`(s) |
| `shutdownService` | **dead** — nothing calls `setShutdownService`; default throws `"Restart not implemented"` | real `ReloadService` (this PRD) |
| `shutdownCommand` | **dead** — never set | dropped |
| `mailSession` (`Object`) | **dead** — never set; `mailSessionProvider` returns null | `@Bean Supplier<Object>` returning null (or a real mail `Session` later) |
| `services` (`Map<String,Boolean>`) | plain config — **already** in `RaplaServerProperties` | consumers read `RaplaServerProperties` |
| `patchScript` | plain config — **already** in `RaplaServerProperties` | consumers read `RaplaServerProperties` |

### Restart-from-client is currently dead

The chain exists end-to-end — Swing admin menu → `RestartServerAction` →
`RemoteOperator.restartServer()` → `RemoteStorage.restartServer()` →
`RemoteStorageController` → `RemoteStorageImpl.restartServer()` (admin check) →
`shutdownService.shutdown(true)` — but the terminal call hits the default
`ShutdownService` whose `shutdown(true)` throws
`IllegalStateException("Restart not implemented")`. Nothing installs a real
implementation; no class `implements ShutdownService`. The feature has been
non-functional since the Spring Boot migration.

### What "restart" should do (decided 2026-05-18)

Not a JVM/Spring restart — a logical **reload** of the rapla server state:
reload all data from the store, clear + rebuild the caches, re-arm the
schedulers. This maps almost exactly onto an operator
`disconnect()` + `connect()` cycle: `CachableStorageOperator.connect()` →
`loadData()` does `cache.clearAll()`, reloads from the store, and
`scheduleConnectedTasks(...)` re-arms the operator's periodic tasks.
`FacadeImpl` holds no cache of its own (it delegates every query to the
operator), so clearing the operator's `LocalCache` is the whole job.

### Cross-repo exposure — dhbwrapla

`ServerContainerContext` is `public` and **dhbwrapla** (`~/git/dhbwrapla`,
branch `spring-boot`) depends on it in 3 files — so removal is a coordinated
two-repo change:

| dhbwrapla file | Uses |
|---|---|
| `DualisViewLoader` | `scc.getDbDatasource("jdbc/dualisdb")` — its Dualis secondary database |
| `DhbwNtlmAuthStore` | `serverContainerContext.isServiceEnabled(ID)` |
| `RaplaPruefungen` | `@Inject ServerContainerContext` field |

## Scope

In scope:
- rapla-server: delete `ServerContainerContext` + `LegacyServerBridgeConfig`;
  re-point every consumer.
- rapla: implement `ReloadService` + an atomic operator `reload()`; rename the
  `ShutdownService` misnomer.
- dhbwrapla: migrate the 3 dependent files in lockstep.

Out of scope:
- The restart UI (`RestartServerAction`, the admin menu item, the REST
  endpoint) — kept; it just finally works.
- Multi-pod restart semantics beyond documenting "reloads the serving pod".
- A real mail `Session` bean — mail session is dead today; this PRD keeps it
  null, no regression.

## Plan

### Implementation status (2026-05-18)

- **Phase 1 — done.** `ServerContainerContext` + `LegacyServerBridgeConfig`
  deleted. `ServerCoreConfig` now owns `raplaLogger()`, a `@Primary`
  `@ConditionalOnProperty` `raplaDataSource` `@Bean` (built from
  `rapla.db-datasources.rapladb`), and a null-returning `mailSessionProvider`.
  `ServerStorageSelector` takes a nullable `DataSource` + `RaplaServerProperties`
  (`getMainFilesource()`). `RaplaServerProperties` gained `isServiceEnabled()` /
  `getMainFilesource()` / the `MAIN_DB_DATASOURCE` + `MAIN_FILE_DATASOURCE`
  constants. `RaplaStatusPageGenerator`, `RaplaIndexPageGenerator`,
  `JavascriptPatcher`, `ServerServiceImpl` re-pointed.
- **Phase 2 — done.** `CachableStorageOperator.reload()` added, implemented in
  `LocalAbstractCachableOperator` as a `synchronized` `disconnect()` +
  `connect()` (no external lock — see the caveat below; `LockOrderingAuditTest`
  still green). `disconnect()` now clears `scheduledTasks` after cancelling.
  `ShutdownService` renamed/replaced by the concrete `ReloadService`;
  `RemoteStorageImpl.restartServer()` calls `reloadService.reload()`.
- **Phase 3 — source migration done (dhbwrapla, branch `spring-boot`).**
  `DualisViewLoader` now takes `@Qualifier("dualisDataSource") DataSource`
  (the existing `DhbwDatasourceConfig` bean) instead of `scc.getDbDatasource`.
  `DhbwNtlmAuthStore` migrated to constructor injection of
  `RaplaServerProperties` (`isServiceEnabled`). `RaplaPruefungen`'s unused
  `ServerContainerContext` field dropped. **Not yet compile/test-verified** —
  dhbwrapla resolves `rapla-core`/`rapla-server`/`rapla-app` from `~/.m2`, so
  it can only build once the updated rapla artifacts are published there
  (`mvn install` of rapla, or a CI publish). That step is left to the
  coordinated build per AGENTS.md build discipline.
- **rapla-server qualifier hardening.** The `raplaDataSource` bean is named
  `raplaDataSource` and `serverStorageSelector` injects it via a
  `@Qualifier("raplaDataSource")`-narrowed `ObjectProvider` — so a
  deployment-private secondary `DataSource` (dhbwrapla's `dualisDataSource`)
  is never mistaken for the rapla store in file-backed deployments.

**Phase 1 — rapla-server: replace the facets, delete the type.**
- Expose the database `DataSource`(s) as proper `@Bean`s (built from
  `RaplaServerProperties.getDbDatasources()` as PRD 045 Phase 3 already does in
  `LegacyServerBridgeConfig` — move that logic into a `@Bean` factory).
- `ServerStorageSelector` constructor takes the primary `DataSource` (nullable)
  + the file-datasource path + `RaplaServerProperties`, instead of
  `ServerContainerContext`.
- `mailSessionProvider` (`ServerCoreConfig`) → returns `() -> null` (mail
  session is dead today; no behaviour change).
- `RaplaStatusPageGenerator`, `RaplaIndexPageGenerator`, `JavascriptPatcher` →
  inject `RaplaServerProperties`; `isServiceEnabled(key)` becomes a lookup in
  `getServices()` (absent ⇒ `true`, matching the old default), `getPatchScript()`
  reads the property.
- `ServerServiceImpl` → drop the `ServerContainerContext` constructor parameter
  (its only use — the mail session — is already commented out).
- Move `raplaLogger()` to `ServerCoreConfig` (or a small dedicated `@Bean`).
- Delete `ServerContainerContext` and `LegacyServerBridgeConfig`.

**Phase 2 — `ReloadService` (the real restart).**
- Add `CachableStorageOperator.reload()` — disconnect + reconnect (OQ1).
  **Lock-correctness caveat — read before implementing:**
  `LocalAbstractCachableOperator.disconnect()` carries an *audited* lock-ordering
  contract — it takes `lockManager.write` **then** `disconnectLock.write`, the
  inverse of what scheduled tasks do, and the inversion is only deadlock-safe
  because of timeouts (`LockOrderingAuditTest` is the contract). `disconnect()`
  and `connect()` are each `synchronized` and each acquire **and release**
  `lockManager.write` internally. So `reload()` must **not** hold
  `lockManager.write` across both calls (re-entrant deadlock unless
  `DefaultRaplaLock`'s write lock is verified reentrant). Recommended shape:
  a `synchronized reload()` that calls `disconnect()` then `connect()` in
  sequence — `synchronized` serialises it against other operator mutations; the
  brief `Disconnected` window is acceptable (a concurrent read during it fails
  cleanly, same as any disconnect). Run `LockOrderingAuditTest` after.
- New `ReloadService` `@Bean` injected with `CachableStorageOperator`;
  `reload()` calls `operator.reload()`.
- Rename `ShutdownService` → `ReloadService`, `shutdown(boolean restart)` →
  `reload()`. `RemoteStorageImpl` autowires `ReloadService` and
  `restartServer()` calls `reloadService.reload()` (admin check unchanged).

**Phase 3 — dhbwrapla migration (separate repo, lockstep with Phase 1).**
- `DualisViewLoader` — obtain the Dualis `DataSource` from a qualified `@Bean`
  / `DhbwProperties` instead of `scc.getDbDatasource("jdbc/dualisdb")` (OQ4).
- `DhbwNtlmAuthStore` — `isServiceEnabled` → `RaplaServerProperties.getServices()`.
- `RaplaPruefungen` — drop the injected `ServerContainerContext` field (verify
  it is unused first).
- Compile + test dhbwrapla against the rapla-server change.

## Tests

- Phase 1: existing `DbDatasourceBootIntegrationTest` (PRD 045) must still pass
  — DB datasource still wired, `ServerStorageSelector` still picks `DBOperator`.
  Tier-2/3 coverage that file-mode and db-mode both still boot.
- Phase 2: a test that `ReloadService.reload()` reloads data and clears the
  cache — mutate the backing store out-of-band, `reload()`, assert the facade
  sees the change; assert the operator stays connected throughout.
- Phase 3: dhbwrapla full `mvn test` green against the updated rapla-server.
- Per AGENTS.md §10 nevers: constructor changes to `ServerStorageSelector` /
  `ServerServiceImpl` are not `FacadeImpl`/`FileOperator`, but `FacadeTestSupport`
  must still compile; DB-touching tests stay `@Tag("db")`.

## Open Questions

All resolved 2026-05-18 — design complete, ready to implement.

1. ~~Atomic `reload()` placement.~~ **Resolved.** Add `reload()` to the
   `CachableStorageOperator` interface, implemented in
   `LocalAbstractCachableOperator` — disconnect + reconnect under the operator's
   existing `disconnectLock` write lock, so the disconnect/connect gap is never
   visible to concurrent requests. `ReloadService` just calls
   `operator.reload()`; no external lock.
2. ~~"All the schedulers" inventory.~~ **Resolved — investigated.** Every
   `CommandScheduler` periodic task is operator-owned, scheduled via
   `LocalAbstractCachableOperator.scheduleConnectedTasks(...)` (conflict
   cleanup, lock cleanup, history cleanup) and re-armed by `connect()`. The
   Spring `@Scheduled` server tasks (`ArchiverServiceTask`, `NotificationService`,
   `SynchronisationManager`, …) are tied to Spring's `TaskScheduler`, operator-
   independent, and keep ticking — they must **not** be restarted. So
   `operator.reload()` re-arms exactly the right set; `ReloadService` needs no
   separate scheduler logic.
3. ~~Multi-pod restart semantics.~~ **Resolved.** `reload()` reloads only the
   pod that served the request; document that. No fan-out — other pods re-sync
   via the update history regardless.
4. ~~dhbwrapla Dualis datasource.~~ **Resolved (recommendation — overridable).**
   Keep the Dualis DB a dhbwrapla-private `@Bean DataSource` with a
   `@Qualifier`, *not* in vanilla rapla's `rapla.db-datasources` map — dualis is
   dhbw-specific and shouldn't surface in vanilla rapla's config namespace.
   `DhbwProperties` already owns dhbw config; `DualisViewLoader` takes the
   qualified bean.
5. ~~`raplaLogger()` new home.~~ **Resolved.** Move to `ServerCoreConfig`.
6. ~~Sequencing vs PRD 003.~~ **Resolved.** Phases 1 + 3 must land **together**
   — deleting `ServerContainerContext` breaks dhbwrapla's compile immediately,
   so the dhbwrapla migration is part of the same coordinated change. Phase 2
   (`ReloadService`) lands with or just after Phase 1. Cross-reference from
   PRD 003 D2, but this PRD does not block on PRD 003's other work.

## Adjacent cleanup — commons-collections4 → in-tree helpers (2026-05-24)

Not in this PRD's main goal; folded in because it touches the same file
(`LocalAbstractCachableOperator`) and removes pre-Spring scaffolding in the
same spirit. Apache `commons-collections4` had exactly two consumers in the
whole reactor, both private fields of `LocalAbstractCachableOperator`:

- `DualHashBidiMap<String, ReferenceInfo> externalIds` — bidirectional
  external-id ↔ entity-reference map (Dualis import, KEY_EXTERNALID
  annotation, ExchangeWebServices UIDs).
- `DualTreeBidiMap<String, DeleteUpdateEntry> deleteUpdateSet` — the
  change-feed structure that powers `getEntities(user, since=T)` and the
  ~10 s update-history polling that drives multi-pod cache invalidation
  (`docs/architecture/locking.md`). Needs replace-by-key **and**
  range-scan-by-value, which is why a plain `TreeMap` couldn't do it.

Both replaced with package-private helpers in `rapla-server`:

| Helper | Backing | Methods | Replaces |
|---|---|---|---|
| `TwoWayMap<K,V>` | two `HashMap`s kept in sync | `put`, `get`, `getKey`, `remove` | `DualHashBidiMap` |
| `IndexedSortedMap<K,V>` | `HashMap<K,V>` + `TreeSet<V>` with external `Comparator` | `put` (replaces & evicts old from sorted view), `get`, `remove` (returns prev), `tailSetByValue` | `DualTreeBidiMap` |

12 tier-1 unit tests cover the load-bearing invariants:

- Equal-timestamp tie-break in the `Comparator` does not collapse same-timestamp
  entries in the `TreeSet` (`DeleteUpdateEntry.compareTo` tie-breaks on id —
  removing the tie-break would silently lose change-feed entries).
- `put(k, newValue)` removes the old value from the sorted view (otherwise a
  second update to the same id would leave two entries in the change feed).
- `remove(k)` returns the previous value (`addToDeleteUpdate` at
  `LocalAbstractCachableOperator.java:1551` uses the return for a warning log).
- `TwoWayMap.put` evicts both directions when the new key or value collides.

Dropped from poms:

- `commons-collections4` dependency in `rapla-server/pom.xml`.
- `commons-collections4` dependencyManagement entry + `commons-collections.version`
  property in `rapla-bom/pom.xml`.
- Dead `guava.version` and `requestfactory.version` properties in
  `rapla-bom/pom.xml` — neither artifact appears in any `<dependency>` block
  nor in `mvn dependency:tree` (residue from the pre-Spring-Boot era).

Net delta: 4 dep/property lines removed, ~115 LOC of focused in-tree code
added (helpers + tests), one transitive third-party library out of the
reactor. Verified by full reactor `mvn clean compile` + `mvn test`
(rapla-core, rapla-client, rapla-server all green; rapla-app reds are
unrelated parallel auth/SPA WIP — see memory
`project_spring_boot_auth_wip_failures`).
