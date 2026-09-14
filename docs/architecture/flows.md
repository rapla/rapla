# Cross-cutting flows

The other architecture pages cover **what** the pieces are. This one
covers **how they move data**:

1. Login and bootstrap
2. Read query (calendar opening, resource list)
3. Store (mutate one or more entities)
4. Refresh (the polling cycle that keeps clients in sync)
5. Conflict reindex on store

For the per-feature flow of editing a reservation see
[reservation-edit.md](reservation-edit.md). For the conflict overlap
algorithm see [../conflict-detection.md](../conflict-detection.md).

---

## The main moving parts

```
                 ┌─────────────────────────────┐
client process   │  RaplaFacade   (rapla-core) │     "do this" — the public API
                 └─────────────┬───────────────┘
                               │
                 ┌─────────────▼───────────────┐
                 │  ClientFacadeImpl           │     listener registry, undo
                 └─────────────┬───────────────┘
                               │
                 ┌─────────────▼───────────────┐
                 │  RemoteOperator             │     REST proxy, polling,
                 │   (rapla-core, dbrm)        │     LocalCache (subset)
                 └─────────────┬───────────────┘
                               │ HTTP / JSON / JWT
─────────────────────────────────────────────────────────────────────
                               │
                 ┌─────────────▼───────────────┐
server process   │  RemoteStorageImpl          │     REST handler
                 │   (rapla-server)            │
                 └─────────────┬───────────────┘
                               │
                 ┌─────────────▼───────────────┐
                 │  SecurityManager            │     permission gates
                 │   (rapla-server)            │
                 └─────────────┬───────────────┘
                               │
                 ┌─────────────▼───────────────┐
                 │  LocalAbstractCachable      │
                 │  Operator                   │     dispatch / cache / locks
                 │   (rapla-server)            │
                 └────┬────────────────────┬───┘
                      │                    │
            ┌─────────▼──────┐    ┌────────▼─────────────┐
            │ FileOperator   │    │ DbsqlOperator        │
            │ (XML on disk)  │    │ (RaplaSQL → JDBC)    │
            └────────────────┘    └──────────────────────┘
```

Same `LocalCache` interface lives in both processes; the client's is a
subset (no other users' reservations) maintained from server pushes.

## 1. Login and bootstrap

```
client                                                 server
─────────────────────────────────────────────────────────────
1. POST /oauth2/token
     grant_type=password&username=X&password=Y
     &client_id=rapla-client&scope=openid+profile
                                                       Spring Security / PasswordGrantProvider
                                                       AuthenticationStore.authenticate
                                                          (file / DB / LDAP via jndi)
                                                       RefreshSessionService issues access + refresh
2. ← {access_token, refresh_token, expires_in, …}

3. RemoteOperator.connect()
   GET /api/storage/resources    Authorization: Bearer …
                                                       SpringSecurityRemoteSession resolves
                                                          JWT.sub → User
                                                       RemoteStorageImpl.getResources(user)
                                                          LocalCache.getVisibleEntities(user)
                                                          → categories, users (filtered),
                                                            allocatables (filtered),
                                                            dynamic types,
                                                            preferences (own + system),
                                                            periods, conflicts (filtered)
4. ← UpdateEvent (initial snapshot)

5. RemoteOperator.refresh(snapshot)
   ├── populate client-side LocalCache
   ├── set lastRefreshed
   └── fire StorageUpdateListener

6. ClientFacadeImpl notifies subscribers
   → CalendarPresenter sees data ready
   → opens main window
```

The initial snapshot is **everything the user can read except other
users' reservations**. Reservations are loaded lazily when a calendar
view asks for a date range (see #2). This keeps memory bounded for
large deployments.

For deployments with very large allocatable sets, PRD 021
("Client-side resource stubs") covers the shrink to id-only stubs.
At the time of writing, that work is in draft.

## 2. Read query — opening a calendar week

The calendar view is the primary data consumer. Opening a week:

```
SwingWeekView is shown for [Mon, Sun]
   └── calendarModel.queryBlocks(interval)
         └── facade.queryAppointments(user, allocatables, interval, filters)
               └── RemoteOperator.queryAppointmentsAsync(...)
                     │  Promise<Map<Allocatable, Collection<Appointment>>>
                     └── HTTP POST /api/storage/queryAppointments
                                  {start, end, resourceIds, ownerIds}

server side:
   RemoteStorageImpl.queryAppointments
      → SecurityManager checks per-allocatable READ
      → LocalAbstractCachableOperator.queryAppointments
         → LocalCache.AllocationMap lookup per allocatable
         → for each appointment: createBlocks(window) [server-side]
         → permission filter per reservation (canRead)
         → ClassificationFilter rules
      → returns Map<allocatableRef, list-of-appointments>

client side:
   RemoteOperator deserialises, attaches resolver, returns Promise
   CalendarModelImpl expands appointments → AppointmentBlocks
   SwingWeekView renders blocks at their pixel positions
```

The block expansion happens client-side because the server returns
appointments (compact: one repeat rule + exceptions per appointment),
and the client expands locally. This minimises wire bytes for
weekly-recurring events.

## 3. Store — mutating entities

There are two top-level entry points:

- `facade.store(entity)` / `facade.storeObjects(entities)` — synchronous
  on the EDT for legacy code.
- `facade.dispatch(stores, removes)` — async, returns `Promise<Void>`.
  The preferred path; everything new should use this.

Both end up calling `RemoteOperator.storeAndRemoveAsync(...)`, which
builds an `UpdateEvent` and sends it to the server.

```
client                                                 server
─────────────────────────────────────────────────────────────
1. ReservationController.saveReservations(editMap, ctx)
   ├── checkEvents(reservations, ctx)            ← pluggable EventChecks
   │     • conflict checker
   │     • (custom plugins)
   │     → Promise<Boolean>
   └── if all checks pass:
        commandHistory.storeAndExecute(SaveUndo)
          └── facade.dispatch([reservations], [])
                └── RemoteOperator builds UpdateEvent
                     • added/modified entities
                     • removeSet (just refs)
                     • userId
                     • invalidateInterval
                └── HTTP POST /api/storage/dispatch  (UpdateEvent JSON)

2.                                                   RemoteStorageImpl.dispatch(event)
                                                       └── SecurityManager.checkModifyPermissions
                                                             (per-entity owner / EDIT check)
                                                       └── for each Reservation:
                                                             checkPermissions per Allocatable
                                                             (hasPermissionToAllocate)
                                                       └── LocalAbstractCachableOperator.dispatch
                                                             ├── lock (RaplaLock writeLock)
                                                             ├── validate version (RaplaNewVersionException
                                                             │     on stale entity)
                                                             ├── update history (1-week retention)
                                                             ├── update LocalCache
                                                             ├── persist via subclass:
                                                             │   • FileOperator.saveData → rapla.xml
                                                             │   • DbsqlOperator → JDBC tx
                                                             ├── ConflictFinder.update
                                                             │     for affected allocatables
                                                             ├── compute UpdateResult (deltas)
                                                             └── unlock
                                                       └── return UpdateEvent (server's view of changes:
                                                             may include extra entities the client didn't
                                                             know it just affected, e.g. derived conflicts)

3. ← UpdateEvent
   RemoteOperator.refresh(serverEvent)
     ├── update client LocalCache
     ├── compute UpdateResult on the client side
     └── fire StorageUpdateListener
           → ClientFacadeImpl.objectsUpdated(ModificationEvent)
             → every registered ModificationListener
               → CalendarView.dataChanged → repaint
               → ConflictsView.dataChanged → refresh count badge
               → open edit dialogs check if their entity was changed
                 by someone else and prompt the user

4. Promise<Void> resolves on the EDT
   ├── eventBus.publish(stopEvent)  → close save spinner
   └── facade.refreshAsync()        → catch up to any other concurrent
                                      changes the server has
```

### `UpdateEvent` shape

`rapla-core/src/main/java/org/rapla/storage/UpdateEvent.java`

| Field | Meaning |
|---|---|
| `categories`, `users`, `resources`, `reservations`, `conflicts`, `preferences` | Lists of entities to **add** or **modify**. The server decides which is which by checking whether the id is already in cache. |
| `removeSet` | Set of `(id, type)` refs to **delete**. Removes don't carry content. |
| `preferencesPatches` | Incremental deltas for large preferences (avoids resending the whole blob for a one-key change). |
| `userId` | Whose action this is. |
| `invalidateInterval` | Time range whose cached appointment lookups are stale (clients use this to invalidate query caches). |
| `needResourcesRefresh` | Force-refresh flag — used after a structural change like a DynamicType edit. |
| `forceAllocatableDeletesIgnoreDependencies` | Dangerous; admin "remove this resource even though it's allocated" override. |

### `UpdateResult` shape

`rapla-core/src/main/java/org/rapla/storage/UpdateResult.java`

Computed on both sides post-dispatch. It contains:

- `since`, `until` — transaction window.
- `Map<ReferenceInfo, Entity> oldEntities` — pre-update state.
- `Map<ReferenceInfo, Entity> updatedEntities` — post-update state.
- `List<UpdateOperation>` — `Add | Change | Remove` per ref.

`ModificationEventImpl` wraps an `UpdateResult` for the listener API
(`hasChanged`, `isRemoved`, `isModified(Class)`).

### Conflict reindex

After every dispatch, `LocalAbstractCachableOperator` walks the
allocatables affected by the dispatch and reruns the conflict
calculation for each. The resulting `UpdateResult` carries any new /
disappeared / re-keyed conflicts back to the client. See
[conflicts-and-events.md](conflicts-and-events.md) for what
`ConflictFinder` does.

## 4. Refresh — the polling cycle

Rapla doesn't push from server to client. It polls.

```
RemoteOperator's scheduler:
  every 30 s (configurable via rapla.refreshInterval)
     refreshAsync()
       └── HTTP POST /api/storage/refresh?lastValidated=lastSeenTimestamp
       ← UpdateEvent (only entities changed since `since`)
       └── refresh(serverEvent)
             • merge into LocalCache
             • compute UpdateResult on the client
             • fire ModificationEvent
             • update lastRefreshed
```

So:

- **Latency** for cross-client visibility is up to 30 s + RTT.
- **Bandwidth** is proportional to changes, not total state.
- **Correctness** depends on `lastChanged` timestamps being
  monotonic per entity (the server enforces this on dispatch).

The poll happens on `CommandScheduler`, which on the Swing client is
the EDT-coupled executor. The actual HTTP call runs off-EDT inside
`RemoteOperator`; the resulting cache update fires back on the EDT.

When a dispatch from this same client returns, the resulting
`UpdateEvent` is folded in **immediately** (the client doesn't wait
for the next poll to see its own changes). The next poll just won't
re-deliver them.

Right after a dispatch, the client also calls `refreshAsync()`
explicitly to catch up to any other concurrent changes that landed
between the dispatch and the response.

## 5. Server-side persistence

Two backends:

### `FileOperator` (default for dev)

`rapla-server/src/main/java/org/rapla/storage/dbfile/FileOperator.java`

XML-on-disk. The whole world is one `data.xml` file (or `rapla.xml`
in some deployments). Dispatch path:

1. Acquire `RaplaLock.writeLock`.
2. Apply event to in-memory cache.
3. Update history.
4. Serialize **the entire cache** to XML (write to a temp file, rename).
5. Release lock.

This is fine for hundreds of reservations; not fine for a university
deployment. Production uses JDBC.

### `DbsqlOperator` + `RaplaSQL`

`rapla-server/src/main/java/org/rapla/storage/dbsql/RaplaSQL.java`

JDBC. `RaplaSQL` is the dispatcher: per-entity-type storage classes
(`CategoryStorage`, `UserStorage`, `AllocatableStorage`,
`ReservationStorage`, `AppointmentStorage`, `ConflictStorage`,
`HistoryStorage`, `PreferenceStorage`, …) each handle their CRUD.
Each storage class:

- Knows its table schema (created via `create*` methods on
  startup / upgrade).
- Encodes complex fields (Permission lists, Classifications) as
  inline XML in a CLOB column.
- Tracks foreign keys and ordering.

Dispatch on the JDBC operator:

1. Acquire write lock.
2. Open a JDBC transaction.
3. For each entity in the event: route to its `*Storage` for
   INSERT / UPDATE / DELETE.
4. Commit (or rollback on error).
5. Update in-memory `LocalCache`.
6. Reindex conflicts.

The history table retains the previous N versions of each entity
(default ~1 week), used for concurrency conflict detection
(`RaplaNewVersionException`) and audit.

For the table schema details and the migration history (Spring Boot
4 / Jackson 3 forced a few subtle changes around `final` fields), see
[PRD 011](../prd/done/011-spring-boot-4-jackson-3.md) and [PRD 017](../prd/017-test-coverage-strategy.md)'s coverage of `DbOperatorBootTest`.

## 6. Concurrency

- **Server-side dispatch is serialized** under `RaplaLock.writeLock`.
  Reads use the read lock and proceed in parallel with each other
  but not with writes.
- **Optimistic concurrency** between clients is via `lastChanged`:
  the client sends the version it edited; the server rejects if the
  cached version is newer. The client gets `RaplaNewVersionException`
  and prompts the user to reload.
- **Client-side**: `RemoteOperator` synchronises `connect`, `dispatch`,
  and `refresh` so the cache is never inconsistent. The Swing client
  treats `Promise` callbacks as EDT-bound.

## See also

- [overview.md](overview.md) — module map
- [reservation-edit.md](reservation-edit.md) — store flow with the
  edit dialog wrapped around it
- [permissions.md](permissions.md) — what `SecurityManager` enforces
- [conflicts-and-events.md](conflicts-and-events.md) — what the
  conflict reindex covers
- PRD 008 — sync server / async client facade split (history)
- [PRD 011](../prd/done/011-spring-boot-4-jackson-3.md) — Spring Boot 4 / Jackson 3 migration (wire format)
- [PRD 017](../prd/017-test-coverage-strategy.md) — test coverage including DBOperator round-trip tests
- PRD 021 — client-side resource stubs (read filter scaling)
