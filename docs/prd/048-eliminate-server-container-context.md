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

A mutable plain-Java bag from pre-Spring rapla's hand-rolled DI container, carrying server bootstrap config. `LegacyServerBridgeConfig` was named for a bridge bean that PRD 001 found non-viable and deleted; the class kept the name but now only produces `raplaLogger()` and `serverContainerContext()`.

### Facet inventory

| Facet | State | Replacement |
|---|---|---|
| `Map<String,DataSource>` | real content; built by `LegacyServerBridgeConfig` (PRD 045 Phase 3) | proper `@Bean DataSource`(s) |
| `shutdownService` | dead — default throws `"Restart not implemented"` | real `ReloadService` |
| `shutdownCommand` | dead — never set | dropped |
| `mailSession` (`Object`) | dead — `mailSessionProvider` returns null | `@Bean Supplier<Object>` → null |
| `services`, `patchScript` | already in `RaplaServerProperties` | consumers read it directly |

### Restart-from-client is dead

Chain: Swing menu → `RestartServerAction` → `RemoteOperator.restartServer` → `RemoteStorageController` → `shutdownService.shutdown(true)` — but the default `ShutdownService` throws. No class `implements ShutdownService`. Non-functional since the Spring Boot migration.

### What "restart" should do (decided 2026-05-18)

Logical **reload**, not JVM restart: reload data, clear/rebuild caches, re-arm schedulers. Maps to operator `disconnect()` + `connect()`: `loadData()` calls `cache.clearAll()` + reloads + `scheduleConnectedTasks` re-arms. `FacadeImpl` holds no own cache (delegates to operator), so clearing `LocalCache` is the whole job.

### Cross-repo exposure — dhbwrapla

`ServerContainerContext` is `public` and dhbwrapla (`spring-boot`) depends on it in 3 files — coordinated two-repo change:

| dhbwrapla file | Uses |
|---|---|
| `DualisViewLoader` | `scc.getDbDatasource("jdbc/dualisdb")` |
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

- **Phase 1 done.** `ServerContainerContext` + `LegacyServerBridgeConfig` deleted. `ServerCoreConfig` owns `raplaLogger()`, a `@Primary @ConditionalOnProperty raplaDataSource` `@Bean` (from `rapla.db-datasources.rapladb`), null `mailSessionProvider`. `ServerStorageSelector` takes nullable `DataSource` + `RaplaServerProperties`. `RaplaServerProperties` gained `isServiceEnabled()` / `getMainFilesource()` / constants. Re-pointed: `RaplaStatusPageGenerator`, `RaplaIndexPageGenerator`, `JavascriptPatcher`, `ServerServiceImpl`.
- **Phase 2 done.** `CachableStorageOperator.reload()` added — `synchronized disconnect() + connect()` in `LocalAbstractCachableOperator` (no external lock — caveat below; `LockOrderingAuditTest` green). `disconnect()` clears `scheduledTasks` after cancelling. `ShutdownService` → concrete `ReloadService`; `RemoteStorageImpl.restartServer()` calls `reloadService.reload()`.
- **Phase 3 — source migration done in dhbwrapla.** `DualisViewLoader` takes `@Qualifier("dualisDataSource") DataSource`. `DhbwNtlmAuthStore` migrated to constructor-injected `RaplaServerProperties`. `RaplaPruefungen` unused field dropped. **Not yet compile/test-verified** — dhbwrapla resolves rapla from `~/.m2`; awaiting coordinated build per AGENTS.md.
- **Qualifier hardening.** `serverStorageSelector` injects `raplaDataSource` via `@Qualifier("raplaDataSource")`-narrowed `ObjectProvider` so deployment-private secondaries (dhbwrapla's `dualisDataSource`) can't be mistaken for the rapla store.

**Phase 1 — rapla-server: replace facets, delete the type.**
- Expose `DataSource`(s) as `@Bean`s (move PRD 045 Phase 3 logic from `LegacyServerBridgeConfig`).
- `ServerStorageSelector` ctor takes nullable primary `DataSource` + file path + `RaplaServerProperties`.
- `mailSessionProvider` → `() -> null` (no behaviour change).
- `RaplaStatusPageGenerator`, `RaplaIndexPageGenerator`, `JavascriptPatcher` → inject `RaplaServerProperties`; `isServiceEnabled(key)` looks up `getServices()` (absent ⇒ true).
- `ServerServiceImpl` → drop `ServerContainerContext` parameter (only use was already commented out).
- Move `raplaLogger()` to `ServerCoreConfig`.
- Delete both types.

**Phase 2 — `ReloadService`.**
- Add `CachableStorageOperator.reload()` — disconnect + reconnect (OQ1). **Lock-correctness caveat:** `disconnect()` takes `lockManager.write` then `disconnectLock.write` (inverse of scheduled tasks; safe only via timeouts — `LockOrderingAuditTest` is the contract). Both `disconnect()` and `connect()` are `synchronized` and acquire+release `lockManager.write` internally. So `reload()` must **not** hold `lockManager.write` across both calls (re-entrant deadlock risk). Recommended: `synchronized reload()` calling `disconnect()` then `connect()` in sequence — serialises against other mutations; the brief `Disconnected` window fails concurrent reads cleanly. Run `LockOrderingAuditTest` after.
- New `ReloadService` `@Bean` injected with `CachableStorageOperator`.
- Rename `ShutdownService` → `ReloadService`, `shutdown(boolean)` → `reload()`. `RemoteStorageImpl` autowires it.

**Phase 3 — dhbwrapla (lockstep with Phase 1).**
- `DualisViewLoader` — qualified `@Bean` from `DhbwProperties` (OQ4).
- `DhbwNtlmAuthStore` — `RaplaServerProperties.getServices()`.
- `RaplaPruefungen` — drop unused field.

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

All resolved 2026-05-18:

1. **Atomic `reload()` placement** — on `CachableStorageOperator` interface, impl in `LocalAbstractCachableOperator`. `ReloadService` just calls `operator.reload()`.
2. **Scheduler inventory** — investigated: operator-owned tasks (conflict/lock/history cleanup) re-arm via `scheduleConnectedTasks` in `connect()`. Spring `@Scheduled` tasks (`ArchiverServiceTask`, `NotificationService`, `SynchronisationManager`) are operator-independent and keep ticking — must NOT restart. `operator.reload()` re-arms exactly the right set.
3. **Multi-pod** — `reload()` reloads only the serving pod; other pods re-sync via update history.
4. **dhbwrapla Dualis datasource** — keep dhbw-private `@Bean DataSource` with `@Qualifier`, not in vanilla rapla's `rapla.db-datasources` map.
5. **`raplaLogger()` home** — `ServerCoreConfig`.
6. **Sequencing vs PRD 003** — Phases 1+3 land together (delete breaks dhbwrapla compile immediately). Phase 2 with or just after.

## Adjacent cleanup — commons-collections4 → in-tree helpers (2026-05-24)

Folded in because it touches `LocalAbstractCachableOperator` and removes pre-Spring scaffolding in the same spirit. `commons-collections4` had two consumers, both private fields:

- `DualHashBidiMap<String, ReferenceInfo> externalIds` — external-id ↔ entity (Dualis, KEY_EXTERNALID, EWS UIDs).
- `DualTreeBidiMap<String, DeleteUpdateEntry> deleteUpdateSet` — change-feed powering `getEntities(user, since=T)` and ~10s update-history polling for multi-pod cache invalidation. Needs replace-by-key **and** range-scan-by-value (plain `TreeMap` insufficient).

Both replaced with `rapla-server` package-private helpers:

| Helper | Backing | Methods | Replaces |
|---|---|---|---|
| `TwoWayMap<K,V>` | two `HashMap`s | `put`, `get`, `getKey`, `remove` | `DualHashBidiMap` |
| `IndexedSortedMap<K,V>` | `HashMap` + `TreeSet` with external `Comparator` | `put` (replaces+evicts), `get`, `remove` (returns prev), `tailSetByValue` | `DualTreeBidiMap` |

12 tier-1 tests cover load-bearing invariants: equal-timestamp tie-break preserves entries (`DeleteUpdateEntry.compareTo` ties on id); `put(k, new)` evicts old from sorted view (else dup change-feed entries); `remove(k)` returns prev (used by `addToDeleteUpdate:1551` warning log); `TwoWayMap.put` evicts both directions on collision.

Dropped from poms: `commons-collections4` dep + dependencyManagement entry + version property; dead `guava.version` + `requestfactory.version` properties (residue from pre-Spring-Boot era).

Net: 4 lines removed, ~115 LOC in-tree (helpers + tests), one transitive library out. Verified by full reactor `mvn clean compile` + `mvn test` (core/client/server green; rapla-app reds are unrelated parallel auth/SPA WIP).
