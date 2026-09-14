# PRD 009: Server-side bulk-storage REST API (port `RemoteStorage` to Spring controllers)

**Status:** in-progress (Phases 0–4 implemented; Phase 5 error-handling cleanup pending — see endpoint sweep findings below)
**Date:** 2026-05-08
**Decision input:** PRD 001 §"What's still alive but dead at runtime" — Phase 4 partial means the server has CRUD endpoints (`/resources`, `/events`, `/dynamictypes`) but never got the bulk-storage operations.
**Triggering symptom:** `404 /rapla/storage/resources` from the Swing client during initial sync (2026-05-08).

## Goal

Make the Swing client (and any other `RemoteStorage` consumer) work end-to-end against the post-Spring-Boot server by porting **every `RemoteStorage` method** to a Spring `@RestController` endpoint. Today the client's `@HttpExchange("/storage")` interface declares 20 methods; the server exposes one matching shape (`/resources`). Most operations 404.

After this PRD lands: Swing client can `start(connectInfo)` against the dev server and complete the initial sync without a 404; an integration test boots reactor + Swing client + server end-to-end; `RemoteStorage` interface and server controllers stay aligned so future endpoint additions are paired by construction.

## Current state — what's there, what's not

**Server controllers already wired** (Spring Boot Phase 1–3 deliverables):

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

**`RemoteStorage` methods with no server endpoint** (the gap this PRD closes):

| `RemoteStorage` method | Wire path | What it does |
|---|---|---|
| `canChangePassword` | `GET /storage/change/canchangepassword` | UI gating for password change |
| `changePassword(PasswordPost)` | `POST /storage/change/password` | user changes own password |
| `changeName(...)` | `POST /storage/change/name` | admin changes user display name |
| `changeEmail(username, email)` | `POST /storage/change/email` | admin changes user email |
| `confirmEmail(...)` | `POST /storage/confirm/email` | email verification token flow |
| `getResources()`, `getResourcesSync()` | `GET /storage/resources`, `/resourcesSync` | fetch entities for cache priming |
| `(unnamed)` | `POST /storage` | resourcesSync POST — purpose TBD, likely one-shot full-state sync |
| `recursiveSync(ReferenceInfo)` | `POST /storage/entity/recursiveSync` | child-entity expansion |
| `getDependent(...)` | `POST /storage/entity/dependent` | dependency check before delete |
| `refresh(lastSyncedTime)`, `refreshSync(...)` | `POST /storage/refresh*` | poll-style sync since last tick |
| `refreshSyncAllEvents` | `POST /storage/refreshSyncAllEvents` | sync events tree only |
| `dispatch(UpdateEvent)`, `dispatchSync(...)` | `POST /storage/dispatch*` | atomic batch write — the central save path |
| `restart()` | `POST /storage/restart` | server restart hint after config change |
| `createIdentifier(type, count)`, `createIdentifierSync(...)` | `POST /storage/identifier*` | server-allocated entity-ID batch reservation |
| `getConflicts()` | `GET /storage/conflicts` | conflict-finder list |
| `getFirstAllocatableBindings(...)`, `getAllAllocatableBindings(...)` | `POST /storage/allocatable/bindings/{first,all}` | overlap lookup |
| `getNextAllocatableDate(...)` | `POST /storage/allocatable/date/next` | suggest next free slot |
| `getUser()` | `GET /storage/user` | logged-in user record |
| `doMerge(...)` | `POST /storage/merge` | merge two allocatables under one |

That's **23 methods** the Swing client calls and the server can't handle.

## Decisions

**URL shape — keep `/storage/*`.** Mirrors the client's existing `@HttpExchange` interface 1:1; zero client-side change while the Swing client is mid-migration to Spring DI (PRD 001 Phase 4). Per-resource splitting is cleaner in the abstract but the coordination cost vs cleanup gain doesn't justify it now. Rename incrementally later if needed. Rejected: split per concern (`/account/*`, `/sync/*`, `/conflicts`, `/allocatable/*`) — ~20 client path edits with no behavioural win.

**Implementation — one `RemoteStorageController` mirroring `RemoteStorage` 1:1.** Easy to spot missing methods; ~25-method controller is fat but pragmatic. Split into per-concern controllers only if it grows past ~600 LOC.

**Body shapes — reuse the legacy DTOs.** `RemoteStorage` defines `PasswordPost`, `MergeRequest`, `AllocatableBindingsRequest` etc. as inner static classes in `rapla-core`; same class on both classpaths means Jackson roundtrips cleanly. For methods today taking multiple `@RequestParam` + body (e.g. `changeName(username, title, surename, lastname)`), wrap into a small DTO during the port.

## Scope

**Files modified:**

- `rapla-core/.../RemoteStorage.java` — keep `@HttpExchange("/storage")`, add request/response DTOs for `@RequestParam` mosaics.
- `rapla-server/.../web/RemoteStorageController.java` — NEW. ~600 LOC mirror of `RemoteStorage`.
- `rapla-server/.../SecurityConfig.java` — `/storage/**` to JWT-protected matcher set.

**Not changed:** internal `ServerService` / `CachableStorageOperator` / `RaplaAuthentificationService` already implement the business logic; the new controller is a thin wrapper. Swing client unchanged.

## Plan

Each phase ends with `mvn -pl rapla-app -am compile` green and server starting. End-of-phase: targeted integration test only (full `mvn test` per AGENTS.md §5).

### Phase 0 — wire the controller skeleton (1 day)

1. Create `RemoteStorageController` with `@RestController @RequestMapping("/storage")`, constructor-inject deps.
2. Add `auth.requestMatchers("/storage/**").authenticated()` to `SecurityConfig`.
3. Implement **`getUser()`** end-to-end as the template (tiny, on the connect path).
4. Test: `curl -H "Authorization: Bearer $TOKEN" http://localhost:8051/rapla/storage/user` returns user JSON.

### Phase 1 — sync operations (1–2 days)

Swing client connect flow needs these in order:

1. `getResources()` / `getResourcesSync()` → full entity tree.
2. `refresh(lastSyncedTime)` / `refreshSync` → incremental since last tick.
3. `dispatch(UpdateEvent)` / `dispatchSync` → the save path. **Highest test coverage:** edit reservation → dispatch → server commits → next refresh sees the change.
4. `createIdentifier(type, count)` / `createIdentifierSync` → ID reservation for new entities.

Phase 1 acceptance: Swing client `start(connectInfo)`, initial resource fetch, dispatch round-trip.

### Phase 2 — conflicts + bindings (1–2 days)

5. `getConflicts()` (sync variant per PRD 008 Phase 5).
6. `getFirstAllocatableBindings(req)` / `getAllAllocatableBindings(req)`.
7. `getNextAllocatableDate(req)`.

Phase 2 acceptance: calendar view + conflicts panel + binding lookups work.

### Phase 3 — account / user (1 day)

8. `canChangePassword()`, `changePassword(PasswordPost)`.
9. `changeName(...)` → bundle into `ChangeNameRequest` DTO.
10. `changeEmail(...)` → `ChangeEmailRequest` DTO.
11. `confirmEmail(...)`.

Phase 3 acceptance: user can change own password from Swing.

### Phase 4 — entity ops + lifecycle (0.5 day)

12. `recursiveSync(ReferenceInfo)`, `getDependent(...)`, `doMerge(MergeRequest)`.
13. `restart()` → returns 202; server initiates orderly shutdown; supervisor restarts.

### Phase 5 — error handling + DTO cleanup (1 day)

14. Centralize exception → HTTP via `@RestControllerAdvice`:

    | Java exception / condition | HTTP status | Use when |
    |---|---|---|
    | `RaplaSecurityException` | **403** | authenticated user lacks permission |
    | `RaplaInvalidTokenException` / unauthenticated | **401** | no/expired/bad token |
    | `EntityNotFoundException` | **404** | well-formed request, entity at that ID doesn't exist |
    | Missing `@RequestParam`, deserialize failure, `AssertionError` from `Assert.notNull` | **400** | request itself is malformed |
    | Generic `RaplaException` | **500** | server-side fault, message in body |

    Don't conflate 400 and 404 — curl sweep showed both as 500; missing-arg → 400, missing-resource → 404.

15. Audit every `@RequestParam(required=false) String foo`: if `null foo` causes the impl to throw, flip to `required=true` (auto-400) or add a typed-exception check handled by #14.
16. Rename awkward inner-class DTOs (`PasswordPost` → `ChangePasswordRequest`).
17. Drop `@RequestParam` mosaics in favour of explicit DTOs.

**Endpoint sweep findings (2026-05-08):** 33 of 38 endpoints green; 5 issues land here:

| Endpoint | Symptom | Target |
|---|---|---|
| `GET /storage/user` (no userId) | 500 NPE / `Assert.notNull` | 400 |
| `POST /storage/refreshSync` (no `lastValidated`) | 500 NPE | 400 |
| `GET /resources/{id}` nonexistent | 500 raw `EntityNotFoundException` | 404 |
| `GET /events/{id}` nonexistent | 500 raw `EntityNotFoundException` | 404 |
| `POST /auth/refresh` stub body | 401 | re-verify with a real refresh token from `/auth/login`; if still 401, debug separately |

`GET /calendar` / `/calendar.csv` returning 404 is expected — needs query params (resource/event IDs, date range); document in `CalendarPageController` Javadoc.

## Tests

| Phase | Test artifact |
|---|---|
| 0 | `RemoteStorageControllerTest.getUser_returnsAuthenticatedUser` — `@SpringBootTest`, get token, call `/storage/user`, assert seeded admin. |
| 1 | `RemoteStorageControllerTest.dispatch_persistsAndIsVisibleOnRefresh` — POST UpdateEvent (one new allocatable), GET `/storage/resources`, assert presence. |
| 1 | `SwingClientStartIntegrationTest` — boots `SpringRaplaClient` against random-port test server, calls `client.start(adminConnectInfo)`, asserts `isConnected()`. **The acceptance test for this PRD.** |
| 2 | `RemoteStorageControllerTest.conflicts_returnsForLoggedInUser` |
| 3 | `RemoteStorageControllerTest.changePassword_succeedsThenLoginWithNewPasswordWorks` |
| 4 | `RemoteStorageControllerTest.recursiveSync_expandsHierarchy` |
| 5 | `RemoteStorageErrorMappingTest` — each subclass of `RaplaException` maps to right HTTP status. |

`SwingClientStartIntegrationTest` is the single test verifying the PRD's success criterion.

## Risks

1. **`UpdateEvent` Jackson roundtrip — PARTIALLY HIT, partial fix landed 2026-05-08.** Legacy RESTeasy used Gson with custom adapters; Spring Boot is Jackson by default. `UpdateEvent`'s `Map<String, List<EntityImpl>>` shapes don't serialize cleanly — entity classes inherit public `getResolver()` from `ReferenceHandler` that transitively reaches `FileOperator.scheduler → ScheduledThreadPoolExecutor.threadFactory` (unserializable). **Decision:** stay on Jackson. **Fix (Phase 1):** `@JsonIgnore` on `ReferenceHandler.getResolver()`. More back-refs may surface — apply `@JsonIgnore` per-getter as they do; don't switch the global mapper.

2. **`RemoteSession` user identification.** New `AuthController` issues JWT with `sub = user.getId()`. `RemoteStorageController` needs to look the user back up. `SpringSecurityRemoteSession` already does this from SecurityContext; verify it works inside controller methods.

3. **Double `/resources` confusion.** Post-PRD, both `/resources` (per-entity CRUD, new style) and `/storage/resources` (bulk fetch, legacy interface) exist. Both correct; document the difference in class-level Javadoc.

4. **PRD 008 sync siblings.** New controller methods should call sync versions (`getConflictsSync`, `queryAppointmentsSync`) per PRD 008 Phase 5 — saves a `.waitFor()` per call.

## Open Questions

1. **`POST /storage` (root) — what does it do?** Likely `resourcesSync` (initial full-tree fetch with optional filter); confirm before writing the endpoint.
2. **`canChangePassword()` — per-user or global "is local file backend" check?** Today true only for file-based deployments. Verify the impl reads the actual auth-source config.
3. **`restart()` in production?** Gate behind `@ConditionalOnProperty rapla.allow-restart-via-rest=true`.
4. **Versioning.** Keep unversioned `/storage/*` to match existing controllers. Add `/v2/storage/*` later if wire format evolves.

## Dependencies on other PRDs

| PRD | Relationship |
|---|---|
| **001** Spring Boot Migration | **Hard prerequisite.** Phase 1–3 controllers provide the template. |
| **001 Phase 4 follow-up** | **Coordinate.** SwingClientStartIntegrationTest needs Swing's Spring-DI wiring far enough along. |
| **005** Multi-Module Split | Done. New controller lives in `rapla-server`. |
| **008** rxjava removal | **Coordinate.** Use `SyncStorageOperator` (Phase 5) for new methods. |
| **003** Custom Deployments | dhbw deployments need every `RemoteStorage` operation working. |

## Effort estimate

~5–7 days, dominated by Phase 1 (dispatch is trickiest, highest-stakes).
