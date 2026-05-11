# PRD 009: Server-side bulk-storage REST API (port `RemoteStorage` to Spring controllers)

**Status:** in-progress (Phases 0–4 implemented; Phase 5 error-handling cleanup pending — see endpoint sweep findings below)
**Date:** 2026-05-08
**Decision input:** PRD 001 §"What's still alive but dead at runtime" — Phase 4 partial means the server has CRUD endpoints (`/resources`, `/events`, `/dynamictypes`) but never got the bulk-storage operations.
**Triggering symptom:** `404 /rapla/storage/resources` from the Swing client during initial sync (2026-05-08). After PRD 001 Phase 4 alignment of `/auth/login` (commit pending), this is the next gap blocking a working Swing session.

## Goal

Make the Swing client (and any other `RemoteStorage` consumer) work end-to-end against the post-Spring-Boot server by porting **every `RemoteStorage` method** to a Spring `@RestController` endpoint. Today the client's `@HttpExchange("/storage")` interface declares 20 methods; the server exposes one matching shape (`/resources`). Most operations 404.

After this PRD lands:
- Swing client can `start(connectInfo)` against the dev server and complete the initial sync without a single 404.
- An `mvn test` integration test boots the full reactor + Swing client + server and verifies the connect → sync → fetch-resources → fetch-events flow.
- `RemoteStorage` interface and the new server controllers stay aligned so future endpoint additions are paired by construction.

## Current state — what's there, what's not

**Server controllers that already work** (Spring Boot Phase 1–3 deliverables):

| Controller | Path | Methods |
|---|---|---|
| `AuthController` | `/auth` | `POST /login`, `POST /refresh` |
| `RaplaResourcesController` | `/resources` | `GET`, `GET /{id}`, `PUT`, `POST`, `DELETE /{id}` |
| `RaplaEventsController` | `/events` | `GET`, `GET /{id}`, `PUT`, `POST`, `PATCH /{id}`, `DELETE /{id}` |
| `RaplaDynamicTypesController` | `/dynamictypes` | `GET` |
| `RemoteLocaleController` | `/locale` | locale/country lookup |
| `RemoteLoggerController` | `/logger` | client log forwarding |
| `MailConfigController`, `MailToUserController` | `/mail/*` | mail plugin |
| `ICalImportController`, `ICalConfigController`, `ICalTimezonesController`, `Export2iCalController` | `/ical*` | ical plugin |
| `ArchiverController`, `JNDIConfigController`, `UrlEncryptionController` | `/archiver`, `/jndi`, `/urlencryption` | misc plugins |

**Client `RemoteStorage` methods with no server endpoint** (the gap this PRD closes):

| `RemoteStorage` method | Wire path the client uses | What it does |
|---|---|---|
| `canChangePassword` | `GET /storage/change/canchangepassword` | UI gating for password change UI |
| `changePassword(PasswordPost)` | `POST /storage/change/password` | user changes own password |
| `changeName(...)` | `POST /storage/change/name` | admin changes user display name |
| `changeEmail(username, email)` | `POST /storage/change/email` | admin changes user email |
| `confirmEmail(...)` | `POST /storage/confirm/email` | email verification token flow |
| `getResources()`, `getResourcesSync()` | `GET /storage/resources`, `/resourcesSync` | fetch all entities for cache priming |
| `(unnamed)` | `POST /storage` | resourcesSync POST — purpose to confirm, may be the one-shot full-state sync |
| `recursiveSync(ReferenceInfo)` | `POST /storage/entity/recursiveSync` | child-entity expansion |
| `getDependent(...)` | `POST /storage/entity/dependent` | dependency check before delete |
| `refresh(lastSyncedTime)`, `refreshSync(...)` | `POST /storage/refresh*` | poll-style sync since last seen tick |
| `refreshSyncAllEvents` | `POST /storage/refreshSyncAllEvents` | sync the events tree only |
| `dispatch(UpdateEvent)`, `dispatchSync(...)` | `POST /storage/dispatch*` | atomic write of a batch of edits (the central save path for the Swing client) |
| `restart()` | `POST /storage/restart` | server restart hint after a config change |
| `createIdentifier(type, count)`, `createIdentifierSync(...)` | `POST /storage/identifier*` | server-allocated entity-ID batch reservation |
| `getConflicts()` | `GET /storage/conflicts` | conflict-finder list for the conflicts dialog |
| `getFirstAllocatableBindings(...)`, `getAllAllocatableBindings(...)` | `POST /storage/allocatable/bindings/{first,all}` | which appointments overlap which allocatables |
| `getNextAllocatableDate(...)` | `POST /storage/allocatable/date/next` | suggest next free slot |
| `getUser()` | `GET /storage/user` | the logged-in user record |
| `doMerge(...)` | `POST /storage/merge` | merge two allocatables under one |

That's **23 methods** the Swing client calls and the server can't handle.

## Decision dimensions

### A. URL shape — keep `/storage/...` or split per concern?

| Option | Pro | Con |
|---|---|---|
| **A1.** Keep `/storage/*` exactly as the client expects. One big `RemoteStorageController` (or a small group) under `@RequestMapping("/storage")`. | Zero client-side change. Each method on `RemoteStorage` maps 1:1 to a server method. Smallest diff to the client (matters because the Swing client is mid-rewrite per PRD 001 Phase 4). | Cements the legacy "one big API" shape. Doesn't follow the per-resource REST style the rest of the new controllers (`/resources`, `/events`, `/dynamictypes`) use. The `/storage/resources` and `/resources` paths will both exist (one going through the bulk operator, one the focused per-entity CRUD) — duplication of intent. |
| **A2.** Split per concern: `/account/*` for password/email, `/sync/*` for refresh/dispatch/identifier, `/conflicts`, `/allocatable/*`, drop `/storage` from the client. | Matches the per-resource style of the other controllers. No /storage/resources duplication. | Client needs ~20 path edits in `RemoteStorage.java`. Each method's `@PostExchange("/X")` updated. |

**Recommendation: A1.** The Swing client is in active migration to Spring DI (PRD 001 Phase 4 follow-up); minimising churn in `RemoteStorage` lets that work proceed without coordinating wire-shape changes. Per-resource splitting is appealing in the abstract but produces real cross-PR coordination cost for marginal cleanup gain. If we later want a cleaner shape we can rename incrementally — once the Swing client is healthy enough that breaking changes don't strand a dev environment.

### B. Where to put the implementation?

| Option | Pro | Con |
|---|---|---|
| **B1.** One `RemoteStorageController` (mirrors `RemoteStorage` interface 1:1). | Trivially aligned with the client interface. Easy to spot missing methods. | ~25-method controller is fat. |
| **B2.** Split into 3–4 controllers under `/storage/*` grouped by concern: `StorageAccountController`, `StorageSyncController`, `StorageConflictsController`, `StorageBindingsController`. | Less fat per file. | More boilerplate. Same wire surface either way. |

**Recommendation: B1 to start, split if a controller grows past ~600 LOC.** Pragmatic.

### C. Body shapes — reuse legacy DTOs or new POJOs?

The `RemoteStorage` interface already defines `PasswordPost`, `MergeRequest`, `AllocatableBindingsRequest`, etc. as inner static classes. Use these as both the client `@RequestBody` parameter type and the server `@RequestBody` parameter type. Jackson serializes/deserializes by field name, so as long as the same class is on both classpaths (it's in `rapla-core`, which both modules see), there's no shape mismatch.

For methods that today take multiple `@RequestParam` + body parameters (e.g., `changeName(username, title, surename, lastname)`), wrap into a small DTO at the same time as the port. Multiple `@RequestParam` plus body is awkward to mock in tests and uncomfortable to evolve.

## Scope

**Files modified:**

- **`rapla-core/src/main/java/org/rapla/storage/dbrm/RemoteStorage.java`** — keep `@HttpExchange("/storage")`, may add small request/response DTO inner classes for methods currently using `@RequestParam` mosaics (decide per method as you port).
- **`rapla-server/src/main/java/org/rapla/server/spring/web/RemoteStorageController.java`** — NEW. ~600 LOC mirror of `RemoteStorage` interface.
- **`rapla-server/src/main/java/org/rapla/server/spring/SecurityConfig.java`** — add `/storage/**` to the JWT-protected matcher set (anything that isn't already `permitAll`).

**Files NOT changed:**

- The internal `ServerService` / `CachableStorageOperator` / `RaplaAuthentificationService` already implement the business logic for every `RemoteStorage` method. The new controller is a thin wrapper — no business logic moves.
- The Swing client side stays as-is. It already calls every method on the interface; once the interface implementations exist on the server, calls succeed.

## Plan

Each phase ends with `mvn -pl rapla-app -am compile` green and the server able to start. End of each phase: re-run the targeted integration test. Don't run the full `mvn test` between phases (per AGENTS.md §5).

### Phase 0 — wire the controller skeleton (1 day)

1. Create `RemoteStorageController` with `@RestController @RequestMapping("/storage")`, constructor-injecting `ServerServiceContainer` (or its component beans) + `RaplaAuthentificationService` + the things needed.
2. Add to `SecurityConfig`: `auth.requestMatchers("/storage/**").authenticated()` (already covered by `anyRequest().authenticated()` per current config — explicit entry for clarity).
3. Implement ONE method end-to-end as the template: **`getUser()`** because it's tiny, returns the authenticated user, and is one of the first calls on the Swing connect path. Wire it through `RemoteSession.getUser()`.
4. Test: `curl -H "Authorization: Bearer $TOKEN" http://localhost:8051/rapla/storage/user` returns the user JSON.

### Phase 1 — sync operations (1–2 days)

The Swing client connect flow needs these in order:

1. `getResources()` / `getResourcesSync()` → existing `RemoteStorageImpl.getResources` (already exists internally; just expose). Returns the full entity tree.
2. `refresh(lastSyncedTime)` / `refreshSync` → `RemoteStorageImpl.refresh`. Incremental sync since last server tick.
3. `dispatch(UpdateEvent)` / `dispatchSync` → `RemoteStorageImpl.dispatch`. The save path. **Highest test coverage** of any method: edit a reservation in the Swing client → dispatch fires → server commits → next refresh sees the change.
4. `createIdentifier(type, count)` / `createIdentifierSync` → ID reservation for new entities.

Phase 1 acceptance: Swing client can `start(connectInfo)`, complete initial resource fetch, and round-trip a save through dispatch.

### Phase 2 — conflicts + bindings (1–2 days)

5. `getConflicts()` → `RemoteStorageImpl.getConflicts` (and the sync variant in `SyncStorageOperator` from PRD 008 Phase 5).
6. `getFirstAllocatableBindings(req)` / `getAllAllocatableBindings(req)` → existing impl in operator.
7. `getNextAllocatableDate(req)` → existing impl.

Phase 2 acceptance: Swing client can open the calendar view, conflicts panel populates, allocatable-binding lookups during edit don't 404.

### Phase 3 — account / user (1 day)

8. `canChangePassword()` — boolean from auth service.
9. `changePassword(PasswordPost)` — validates old, updates new.
10. `changeName(...)` — bundle into `ChangeNameRequest` DTO at the same time.
11. `changeEmail(username, email)` — bundle into `ChangeEmailRequest` DTO.
12. `confirmEmail(...)` — same.

Phase 3 acceptance: user can change own password from Swing.

### Phase 4 — entity ops + lifecycle (0.5 day)

13. `recursiveSync(ReferenceInfo)` → entity tree expansion.
14. `getDependent(...)` → dependency check.
15. `doMerge(MergeRequest)` → existing impl.
16. `restart()` → server restart hint (returns 202; server initiates orderly shutdown signaling Spring Boot to exit, deployer's process supervisor restarts it).

Phase 4 acceptance: admin operations (merge, restart) work from Swing.

### Phase 5 — error handling + DTO cleanup (1 day)

17. Centralize exception → HTTP status mapping in a `@RestControllerAdvice`. Standard mapping (validated against the curl sweep on 2026-05-08, see "Endpoint sweep findings" below):

    | Java exception / condition | HTTP status | Use when |
    |---|---|---|
    | `RaplaSecurityException` | **403 Forbidden** | authenticated user lacks permission |
    | `RaplaInvalidTokenException` / unauthenticated | **401 Unauthorized** | no token, expired token, bad token |
    | `EntityNotFoundException` | **404 Not Found** | well-formed request, but the entity at that ID doesn't exist (e.g. `GET /resources/ghost`, `GET /storage/user?userId=ghost`) |
    | Missing required `@RequestParam`, body that fails to deserialize, `AssertionError` from a `null`-arg `Assert.notNull` deep in the call chain | **400 Bad Request** | request itself is malformed (e.g. `GET /storage/user` with no userId, `POST /storage/refreshSync` with no `lastValidated`) |
    | Generic `RaplaException` | **500 Internal Server Error** | server-side fault, surface the message in the body |

    Don't conflate 400 and 404 — the curl sweep showed both currently return 500, and the right answer is *not* "use 404 for both" but "missing-arg → 400, missing-resource → 404". 422 Unprocessable Entity is allowed for semantically wrong but well-shaped requests; rarely needed here.

18. Audit every `@RequestParam(required = false) String foo` in `RemoteStorageController`: if a `null foo` causes the underlying impl to throw (typical: `Assert.notNull` deep in a `ReferenceInfo<>` constructor), either flip to `required = true` (Spring auto-400s on absence) or add an explicit null-check that throws a typed exception the advice in #17 handles.
19. Rename inner classes inside `RemoteStorage.java` if any feel awkward (e.g., `PasswordPost` → `ChangePasswordRequest`).
20. Drop `@RequestParam` on legacy multi-param methods in favour of explicit DTOs.

**Endpoint sweep findings (2026-05-08):** ran `/tmp/sweep.sh` against a live dev server with admin token. 33 of 38 endpoints green; 5 issues land in this phase:

| Endpoint | Symptom | Target |
|---|---|---|
| `GET /storage/user` (no userId) | 500 NPE / `Assert.notNull` | 400 |
| `POST /storage/refreshSync` (no `lastValidated`) | 500 NPE | 400 |
| `GET /resources/{id}` with nonexistent id | 500 (raw `EntityNotFoundException`) | 404 |
| `GET /events/{id}` with nonexistent id | 500 (raw `EntityNotFoundException`) | 404 |
| `POST /auth/refresh` with stub body | 401 | re-verify with a real refresh token from `/auth/login` response; if still 401, debug separately — likely `AuthController.refresh` not yet wired |

`GET /calendar` / `/calendar.csv` returning 404 is *expected* — those endpoints need query params (resource/event IDs and date range); the sweep called them without args. Document the required params in `CalendarPageController`'s Javadoc once we touch it.

## Tests

| Phase | Test artifact |
|---|---|
| 0 | `RemoteStorageControllerTest.getUser_returnsAuthenticatedUser` — `@SpringBootTest` boots the full app + AuthController, obtains a token, calls `/storage/user`, asserts the returned user matches the seeded admin. |
| 1 | `RemoteStorageControllerTest.dispatch_persistsAndIsVisibleOnRefresh` — POST a small UpdateEvent (one new allocatable), GET `/storage/resources`, assert the new allocatable is in the response. End-to-end through the operator. |
| 1 | `SwingClientStartIntegrationTest` — boots `SpringRaplaClient` against a local `RaplaSpringBootApplication` test server (random port), calls `client.start(adminConnectInfo)`, asserts `client.getFacade().getOperator().isConnected()` and that no exception was raised in the connect flow. **The acceptance test for this PRD.** |
| 2 | `RemoteStorageControllerTest.conflicts_returnsForLoggedInUser` |
| 3 | `RemoteStorageControllerTest.changePassword_succeedsThenLoginWithNewPasswordWorks` |
| 4 | `RemoteStorageControllerTest.recursiveSync_expandsHierarchy` |
| 5 | `RemoteStorageErrorMappingTest` — assert each subclass of `RaplaException` maps to the right HTTP status. |

The `SwingClientStartIntegrationTest` from Phase 1 is the **single test that verifies the PRD's success criterion**. Without it the PRD isn't done; with it green, the GUI works.

## Risks

1. **`UpdateEvent` Jackson serialization roundtrip — PARTIALLY HIT, partial fix landed 2026-05-08.** The legacy RESTeasy stack used Gson with custom adapters. Spring Boot is on Jackson by default. `UpdateEvent` has `Map<String, List<EntityImpl>>` shapes that don't serialize cleanly because entity classes inherit a public `getResolver()` from `ReferenceHandler` that transitively reaches `FileOperator.scheduler → ScheduledThreadPoolExecutor.threadFactory` (an unserializable inner class). **Decision (user direction 2026-05-08):** stay on Jackson. **Fix (Phase 1):** added `@JsonIgnore` to `ReferenceHandler.getResolver()` so the runtime back-reference doesn't follow into the wire output. More similar back-refs may surface — apply `@JsonIgnore` per-getter as they do, don't switch the global mapper.

2. **`RemoteSession` user identification.** The new `AuthController` issues JWT with `sub = user.getId()`. `RemoteStorageController` needs to look the user back up. The existing `SpringSecurityRemoteSession` already does this from the SecurityContext; verify it works inside the controller methods (vs only in `RemoteAuthentificationService`).

3. **Double `/resources` confusion.** After this PRD, there will be `/resources` (per-entity CRUD, RaplaResourcesController, the per-resource REST style) **and** `/storage/resources` (bulk fetch, the legacy interface). Both correct, both should exist. Document the difference in the new controller's class-level Javadoc so a future reader doesn't try to "consolidate" them.

4. **Mid-flight rxjava removal interaction (PRD 008).** PRD 008 §"Sync siblings on server-internal services" has the pattern: sync impl is the source of truth, async wraps sync. New `RemoteStorageController` methods should call the sync versions of operator methods (e.g., `getConflictsSync`, `queryAppointmentsSync`) rather than the `Promise<>` async wrappers. Saves one `.waitFor()` ceremony per call.

## Open Questions

1. **`POST /storage` (root) — what does it do?** Inspect `RemoteStorage.java`: there's a `@PostExchange` with no path on the interface, mapping to `POST /storage`. Need to identify the corresponding business method before writing the controller endpoint. Likely `resourcesSync` (initial full-tree fetch with optional filter) but confirm.

2. **`canChangePassword()` — is this a per-user check or a global "is local file backend" check?** Today it returns true only for file-based deployments (passwords editable). For LDAP/JNDI-backed setups, it's false. Verify the implementation reads the actual auth-source config.

3. **`restart()` — should it work in dev mode?** A "restart server" REST call from the GUI is dangerous in production but useful in dev. Gate behind `@ConditionalOnProperty` (e.g., `rapla.allow-restart-via-rest=true`) and document.

4. **Versioning.** Should the new `/storage/*` endpoints be `/v1/storage/*` or just `/storage/*`? No versioning today on the existing `/auth`, `/resources`, etc. — keeping unversioned matches the rest. If we later need to evolve the wire format, add `/v2/storage/*` then.

## Dependencies on other PRDs

| PRD | Relationship |
|---|---|
| **001** Spring Boot Migration | **Hard prerequisite.** Phase 1–3 controllers (auth, resources, events) provide the model this PRD copies. |
| **001 Phase 4 follow-up** (ongoing in user's parallel session) | **Coordinate.** This PRD's Phase 1 acceptance test (SwingClientStartIntegrationTest) needs the Swing client's Spring-DI wiring to be far enough along that `SpringRaplaClient` boots. |
| **005** Multi-Module Split | Already done. The new controller lives in `rapla-server`. |
| **008** rxjava removal | **Coordinate.** Use the `SyncStorageOperator` interface from PRD 008 Phase 5 for the new controller methods — saves a `.waitFor()` per call and matches the established pattern. |
| **003** Custom Deployments — dhbw direction change (server-only) | This PRD makes that change deliverable: dhbw deployments will need every `RemoteStorage` operation working. |

## Effort estimate

~5–7 days of focused work for a single agent, dominated by Phase 1 (dispatch is the trickiest method and the highest-stakes test).
