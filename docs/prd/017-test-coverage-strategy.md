# PRD 017: Test Coverage Strategy

**Status:** Phases 1–4 ✅ done 2026-05-10. Phase 5 in progress (first test landed).
**Date:** 2026-05-10

### 2026-05-10 — Phase 5 first test: DBOperator boot

`DbOperatorBootTest` (rapla-server, tier-2 `@Tag("db")`, 2 tests, 7.6 s): fresh HSQLDB temp file, file→db import via `ImportExportManagerImpl` exercises all 15 `create*` tables + per-entity inserts; reconnect on seeded DB skips upgrade/import (asserts no double-import).

**Coverage delta on `org.rapla.storage.dbsql`:** ~10 % → **58 % instruction** (5,950/10,320), ~5 % → **43 % branch**. All four core classes (`RaplaSQL`, `DBOperator`, `AbstractTableStorage`, `EntityStorage`) now have direct exercise. **+48 instruction-points off one test class** — leverage from the import path walking the entire RaplaSQL writer tree.

Implementation note: `DBOperator`'s package-private `Supplier<ImportExportManager>` field lets tests swap `ImportExportManagerImpl(file, db)` post-construction (production uses same late-binding via `ServerStorageSelector`).

Not yet covered: refresh-tick (`getRefreshData`/`refreshWithoutLock`), lock-timeout edges, pre-2.0 schema upgrade. Phase 5 follow-up candidates.

### 2026-05-10 — Phase 5 second test: DBOperator live round-trip

`DbOperatorRoundTripTest` (rapla-server, tier-2 `@Tag("db")`, 3 tests, 12.9 s): exercises live UPDATE/INSERT/DELETE paths of `RaplaSQL` (boot test only hits bulk importer). Each test: mutate via `FacadeImpl` on DB-backed operator → disconnect → reconnect → assert round-trip:
- `editAttributeRoundTripsThroughDb` — edit→setValue→store→reconnect→re-read.
- `newAllocatableInsertedRoundTripsThroughDb` — new→store→reconnect→`tryResolve` returns entity.
- `removedAllocatableRoundTripsThroughDb` — insert→reconnect→remove→reconnect→`tryResolve` returns null.

**Combined Phase 5 delta on `org.rapla.storage.dbsql`:** instr ~10 % → 58 % → **72 %** (7,443/10,320); branch ~5 % → 43 % → **58 %** (542/930). **+62 instr, +53 branch off 5 tests / ~20 s**. Round-trip adds ~14 instr-points by hitting per-row UPDATE + DELETE paths the bulk importer skips.

### 2026-05-10 — Phase 4 close-out: final aggregate

Full-lane `mvn -Pcoverage verify -Dtest.excludedGroups=`. vs Phase 3 baseline (2026-05-09):

| Bundle | Phase 3 | Phase 4 | Δ |
|---|---:|---:|---:|
| **Aggregate instr** | 12 % | **15 %** | +3 |
| **Aggregate branch** | 10 % | **14 %** | +4 |
| rapla-core | 26 % | **35 %** | +9 |
| rapla-server | 16 % | **21 %** | +5 |
| rapla-client | 1 % | 2 % | +1 |

Raw: 41,072 / 264,024 instr, 3,470 / 24,644 branch. rapla-client low — existing tests are interactive Swing harnesses (out of scope).

**Phase 4 deliverables:**

| Test | Tier | Tests | Wall |
|---|---|---:|---:|
| `AppointmentBlocksExpansionTest` | 1 | 14 | 84 ms |
| `PermissionMatrixTest` | 2 | 12 | 5 s |
| `XmlRoundTripTest` | 2 | 6 | 3.5 s |
| `ClassificationAndNameformatTest` | 2 | 6 | 4 s |
| `FacadeMutationTest` | 2 | 9 | 5 s |
| `ConflictFinderViaFacadeTest` | 2 | 5 | 15 s |
| `AppointmentOverlapHardeningTest` | 1 | 23 | 110 ms |
| `ConflictPerformanceTest` | 2 perf | 3 | 18 s |
| **Total** | | **78** | |

**Real bugs surfaced (3):** PRD 011 follow-up Jackson 3 `final`-field bugs (via `XmlRoundTripTest`); `LocalAbstractCachableOperator.storeAndRemoveAsync(...)` empty-stub no-op (via `ConflictPerformanceTest`, fixed same session); MONTHLY = Nth-weekday-of-month convention pinned (not a bug — load-bearing semantic).

**Full-lane health:** 211 tests / 49 classes, 0 failures, including 4 `@SpringBootTest` e2e + `ConcurrentTests`. `HeadlessClientNameResolutionIntegrationTest` (prior 401 bug from Phase 2) now passes — likely fixed transitively by PRDs 011/014.

Phase 4 ✅ done. Phase 5 began same day with `DbOperatorBootTest` (above). Remaining weakest areas: `rapla-server.server.spring.web` (partially covered by rapla-app `*ControllerIntegrationTest` ring) + DBOperator follow-ups (refresh-tick, conflict DB round-trip, pre-2.0 schema upgrade). rapla-client Swing UI out of scope for unit-style coverage.

### 2026-05-10 — Hardening + performance round

**`AppointmentOverlapHardeningTest`** (rapla-core, tier-1, 23 tests, 110 ms): edge cases for `AppointmentImpl.overlapsAppointment` — containment, touching, adjacent, single×repeating, repeating×repeating, exceptions, DST, leap year, zero-duration, MONTHLY semantic. Every test asserts symmetry — largest class of conflict-detection bugs.

**`ConflictPerformanceTest`** (rapla-server, tier-2 `@Tag("perf")`, ~18 s): 1000-reservation deterministic fixture across 6 allocatables (single/DAILY/WEEKLY/MONTHLY + exceptions). Measures bulk store, full-graph `getConflicts()`, per-reservation conflict, range query, incremental store+reindex, `overlapsAppointment` throughput. Order-of-magnitude budgets (flag 5-10× regressions). Live `[perf]` markers to stderr.

`perf` added to `<test.excludedGroups>` alongside `db,e2e`. Default `mvn test` ignores transparently.

Reference numbers (Linux WSL2, May 2026): build 1000 reservations ~130 ms; bulk `storeObjects(1000)` ~7-13 s; `getConflicts()` returning 16,259 conflicts ~280-700 ms; `getConflictsForReservation(pivot)` ~10-15 ms; incremental store ~1.3-3.6 s; `overlapsAppointment(daily-365, weekly-52)` ~30-60 µs/call.

**Docs:** [`docs/conflict-detection.md`](conflict-detection.md) covers algorithm (closed-open intervals, gcd fast-path, sweep-line), the Nth-weekday MONTHLY semantic, perf-test fixture, sanity-budgets, live-output flag.

**Real bug (#3):** `LocalAbstractCachableOperator.storeAndRemoveAsync(...)` is an empty stub ([source](../../rapla-server/src/main/java/org/rapla/storage/impl/server/LocalAbstractCachableOperator.java#L214)) — `scheduler.run(() -> { })`. So `facade.dispatch(Collection, Collection)` silently no-ops on file-operator backend. ConflictPerformanceTest's first run dispatched 1000 reservations in 54 ms with 0 persisted; switched to `facade.storeObjects(T[])` (sync works). **Affected callers:** anyone calling `facade.dispatch(...)` on a file-backed operator. **Workaround:** sync APIs work.

**Test discovery:** Initial `monthlyRepeatOverlapsWithSingleOnTargetDay` (Apr 15) failed because Rapla's MONTHLY is "Nth weekday of month" (Apr 16 = 3rd Thursday) — same as Outlook/Google Calendar but never previously pinned. Hardening test now documents it.

### 2026-05-10 — Phase 4 follow-up: ConflictFinder via facade landed

`ConflictFinderViaFacadeTest` (rapla-server, tier-2 via `FacadeTestSupport`, 5 tests, 15 s):
- Same-allocatable overlapping → both see each other (symmetric).
- Touching-edge → symmetry-only (convention may vary).
- Different allocatable / different days → no cross-reference.
- Global `getConflicts()` count strictly increases after 2 overlapping store.

Full save chain (`newReservation` → `addAppointment` → `addAllocatable` → `store` → `ConflictFinder` → `getConflictsForReservation`) per test → ~3 s/test (still 3-5× faster than `@SpringBootTest`).

**Coverage delta — biggest Phase 4 jump:** `storage.impl.server` 31 → **44 %** (+1,355); `ConflictFinder` ~25 → **42 % line / ~30 % branch**; aggregate rapla-core 26 → **33 %**, rapla-server 16 → **20 %**, overall 13 → **14 %**. Ripple is bigger than the per-package win because store cascades through 5 layers (FacadeImpl.store → operator → LocalCache → ConflictFinder reindex → write-out).

### 2026-05-10 — Phase 4 follow-up: FacadeImpl mutation cycle landed

`FacadeMutationTest` (rapla-server, tier-2 via `FacadeTestSupport`, 8 tests, 5 s):
- edit→modify→store→re-read persists attribute changes; `newAllocatable→store` makes visible + resolvable; `remove` drops count + nulls `tryResolve`; `clone` is independent; `editListAsync` returns per-input writable map; `getReservationsForAllocatable` / `getReservations(user, range, …)` return fixture data (2002); future-window returns consistent (possibly empty).

`waitFor(Promise<T>)` helper promoted to `FacadeTestSupport` (backed by `thenAccept`+`exceptionally`+`CountDownLatch` — custom `org.rapla.scheduler.Promise` has no `.get()`).

**Coverage delta:** `FacadeImpl` **23 % instr / 22 % branch** (738/3,099 + 61/272); `facade.internal` 8 → 9 % (+87). Headline barely moves — package also contains `ClientFacadeImpl`/`CalendarModelImpl`/`ConflictImpl`/`AllocationChangeFinder` untouched. FacadeImpl 23 % is the meaningful number: the most-called methods now have direct tier-2 pressure.

**Foot-gun discovery:** `assertNotEquals(original, edited)` failed because `Allocatable.equals` is id-based (same logical entity even when one is read-only and the other writable). Use `assertNotSame` for "different copy" assertions.

### 2026-05-10 — Phase 4 follow-up: Classification + ParsedText landed

`ClassificationAndNameformatTest` (rapla-server, tier-2 via `FacadeTestSupport`, 6 tests, 4 s):
- Every allocatable `getName(locale)` non-null/non-empty/not-the-id (PRD 011 bug shape; tier-2 canary for the slow tier-4 `HeadlessClientNameResolutionIntegrationTest`).
- `editAsync` → setValue → getName reflects change on editable, original untouched.
- `getValue(key)` == `getValueForAttribute(attr)`; `getValueAsString` non-null.
- `type.getAttribute(key)` matches iteration (post-deserialize identity).
- `getClassification().getType()` non-null + in `facade.getDynamicTypes()` (resolver wiring intact).

**Coverage delta on `entities.dynamictype.internal`:** `ClassificationImpl` 54 % line / 37 % branch; `ParsedText` 51 / 36; `DynamicTypeImpl` 36 / 22; package 27 → **29 %** (+194). Headline barely moves (10K-instr package dominated by `ParsedText` branches + `DynamicTypeImpl` helpers); wins concentrate on the high-value PRD 011 chain (`getName`/`getValue`/`getValueAsString`) — regressions now surface in <4 s vs 10-15 s tier-4 e2e.

**Implementation note:** `org.rapla.scheduler.Promise` has no `.get()` / no `whenComplete` — only `thenAccept` / `exceptionally` / `handle` / `finally_`. Tier-2 waits use `CountDownLatch` + `AtomicReference`; `waitFor` helper promoted to `FacadeTestSupport`.

### 2026-05-10 — Phase 4 #11 (permission matrix) landed

`PermissionMatrixTest` (rapla-server, tier-2 via `FacadeTestSupport`, 12 tests, 5 s):
- Admin shortcut: modify/admin/delete every allocatable; create+read every dynamic type.
- Non-admin default-grant: monty reads & allocates default-permission resources (`allocate_conflicts` → ALLOCATE → REQUEST → READ).
- Non-admin negative: no modify/admin on unowned; no create on types without CREATE.
- `isOwner` static: matches when `ownerRef.isSame`; false when null (any asker).
- `canCreateReservation` no-NPE on either user.

**Test discovery:** initial `nonAdminCannotEditDynamicTypes` failed because the `event` type grants `access="create"` to everyone (by design). Reframed as "find at least one type where admin can but non-admin cannot create".

**Coverage delta:** `RaplaDefaultPermissionImpl` ~0 → **45 % instr / 24 % branch**; `PermissionController` ~25 → **30 % / 22 %**; aggregate 13 → 13 %. Headline doesn't move (small classes — 177 + 1,170 instr of 261K). Local wins: admin/owner/group/EDIT/ADMIN matrix in `hasAccess` now has direct pressure; `PermissionController` branch doubled → future refactors surface as test failures.

### 2026-05-10 — Phase 4 #12 (XML round-trip) landed

`XmlRoundTripTest` (rapla-server, tier-2 via `FacadeTestSupport`, 6 tests, 3.5 s):
- Categories: top-level keys + ids identical.
- Dynamic types: count + key set identical.
- Allocatable names: non-null/non-empty (PRD 011 bug shape — server-side tier-2 twin of slow tier-4 `HeadlessClientNameResolutionIntegrationTest`).
- User logins: count + login set identical.
- Classification → DynamicType resolver intact.
- Idempotency under double round-trip (load → save → load → save → load).

Pattern: `operator.saveData()` → `disconnect()` → `connect()` → re-snapshot and compare.

**Coverage delta on `org.rapla.storage.xml`:** instr 41 % (3,375/8,122) → **68 %** (5,593); branch ~30 → **58 %**; aggregate 12 → **13 %**. +27 pts from now exercising the writer path (`RaplaMainWriter` + per-entity helpers were near-zero before — only read path had been exercised).

No bugs surfaced — 6 round-trips green first try is useful evidence the wire format isn't asymmetric in snapshotted fields.

### 2026-05-09 — Phase 3 follow-up + Phase 4 first test landed

**Aggregator JaCoCo report.** Wired in root `pom.xml` under `coverage` profile via standard `report-aggregate` + preceding `merge` step (folds rapla-app's `jacoco.exec` into aggregator). rapla-app can't be a `<dependency>` directly — JNLP webclient jars (multi-release shaded Jackson) crash JaCoCo's class scanner; per-module rapla-app report skipped (`<phase>none</phase>` override). Run: `mvn -Pcoverage verify -Dtest.excludedGroups=` → `target/site/jacoco-aggregate/index.html`.

**Aggregate baseline (full lane):** 31,803 / 261,713 instr = **12 % overall, 10 % branch**.

| Module | Aggregate | Solo |
|---|---:|---:|
| rapla-core | **26 %** | 5 % |
| rapla-server | **16 %** | 7 % |
| rapla-client | **1 %** | 0 % |

Solo→aggregate jump is `@SpringBootTest` contributions correctly attributed for the first time. rapla-client stays low (interactive Swing harnesses).

**Phase 4 first test landed.** `AppointmentBlocksExpansionTest` (rapla-core, 14 tier-1 tests, 84 ms): DAILY/WEEKLY/MONTHLY/YEARLY via `createBlocks`, custom intervals, `setNumber(N)` vs `setEnd(date)`, exception handling, DST boundaries (2026-03-29 + 2026-10-25), window filtering, zero-length, block invariants.

After backfill `AppointmentImpl` reached **59 % line / 52 % branch**. Package (`entities.domain.internal`) stayed at 42 % (already included `@SpringBootTest`); marginal impact is on speed/locality — `processBlocks` failures now surface in 84 ms vs 7-s `@SpringBootTest`. All 14 green first try after package-private `RepeatingImpl` accessibility fix (use `Appointment` interface in helper return type).

### 2026-05-09 — Phase 3 landed

JaCoCo wired in `rapla-bom/pom.xml` under skip-by-default `coverage` profile. `mvn -Pcoverage test`. Profile flips surefire to `forkCount=1` (JaCoCo's `-javaagent` needs forked JVM). Reports at `<module>/target/site/jacoco/index.html`.

**Baseline (per-module solo, `-Pcoverage test -Dtest.excludedGroups=`):**

| Module | Covered/total | Instr % | Branch % |
|---|---:|---:|---:|
| rapla-core | 4,047 / 78,478 | **5%** | 4% |
| rapla-client | 558 / 131,908 | **0%** | 0% |
| rapla-server | 3,659 / 51,332 | **7%** | 5% |
| rapla-app | 7 / 12 | 58% | n/a |

**Caveat — per-module reports undercount.** JaCoCo only attributes coverage in the surefire that produced its `jacoco.exec`. `@SpringBootTest` in rapla-app exercises rapla-server/rapla-core but lands in rapla-app's `jacoco.exec` (scoped to rapla-app bytecode = just `RaplaSpringBootApplication.java`). Full-stack needs `report-aggregate` — deferred as Phase 3 follow-up.

**Coverage hotspots:**
- **rapla-core**: `rest.client.internal.isodate` 49 %, `components.util` 42 %, `entities.domain.internal` 25 %, `rest` 20 %, `entities.dynamictype` 17 %, `rest.jackson` 16 %, `entities.domain` 11 % — maps to existing tier-1 tests.
- **rapla-server**: `endpoints.server.token` 76 % (`SignedTokenTest`), `components.i18n.server` 63 %, `plugin.mail.server` 30 %, `storage.impl.server` 23 %, `storage.dbfile` 21 % (via `FacadeTestSupportTest`).
- **rapla-server 0 % packages** (own-tests only): `storage.dbsql` (10K instr), `server.spring.web` (707), `server.spring` (1.1K), `server.servletpages` (726). Covered via rapla-app's `@SpringBootTest` but not attributed.

**Phase 4 implication:** dbfile (FileOperator) + storage.impl.server are highest-leverage `FacadeTestSupport` targets.

### 2026-05-09 — Phase 2 landed

Tagging wired. `@Tag("db")` on `ConcurrentTests` (also JUnit-5-migrated — straight rename, all `Assert.*` were 1-/2-arg). `@Tag("e2e")` on 4 `@SpringBootTest` acceptance tests in rapla-app (`RaplaSpringBootApplicationTest`, `ServerServiceIntegrationTest`, `HeadlessClientNameResolutionIntegrationTest`, `SwingClientStartIntegrationTest`). `rapla-bom/pom.xml` adds `<excludedGroups>${test.excludedGroups}</excludedGroups>` + default `db,e2e`. Override: `mvn test -Dtest.excludedGroups=`.

| Lane | Command | Wall | Result |
|---|---|---:|---|
| Fast | `mvn test` | 1m 51s | green; 4 e2e + ConcurrentTests skipped |
| Full | `mvn test -Dtest.excludedGroups=` | — | one pre-existing 401 in `HeadlessClientNameResolutionIntegrationTest`, flagged |

Fast-lane 1m 51s is above PRD 007's 1m 9s baseline — gap is MockMvc controller tests each cold-booting their own Spring context (cache key not shared due to per-class `@TempDir`/`@DynamicPropertySource`). PRD 007 Phase 2.7 owns the fix.

`MariadbTest` left untouched (a `main()` scratchpad with hardcoded creds, never discovered by surefire; flagged separately).

### 2026-05-09 — Phase 1 landed

`FacadeTestSupport` at `rapla-server/src/test/java/org/rapla/test/util/FacadeTestSupport.java` with 6 self-tests (`FacadeTestSupportTest`) exercising `facade.getSuperCategory()`, `getDynamicTypes()`, `getAllocatables()`, `getUsers()`. All pass. AGENTS.md §10 documents the pyramid + base-class usage.

| Test | Tests | Wall | Per test |
|---|---:|---:|---:|
| `FacadeTestSupportTest` (tier 2, no Spring) | 6 | 3.3 s | ~550 ms |
| `ServerServiceIntegrationTest` (tier 3, `@SpringBootTest`) | 2 | 7.2 s | ~3.6 s |

Fresh facade boots ~150 ms (SAX parse + cache init) vs `@SpringBootTest` ~7 s — **45× faster cold-start, ~4× faster for a 6-test class**. Savings compound across separate `mvn test -Dtest=…` invocations (no Spring context cache to warm).

OQ #1 resolved: **copy** `testdefault.xml` rather than move + test-jar dep. The 30 KB duplicate is acceptable; revisit on third copy or fixture divergence.

## Goal

Grow the automated test suite into a fast, layered safety net so refactors (date migration, Spring Boot 4 / Jackson 3, multi-module split) stay safe and CI stays under ~90 s. PRD 007 fixed suite execution time + silently-skipped JUnit 4 classes; this PRD addresses *what to test, where, and how to keep new tests cheap*.

1. Test pyramid matching the 5-module reactor (PRD 005).
2. Tier-2 base class (`FacadeTestSupport`) so storage/facade tests skip `@SpringBootTest`.
3. Tag & gate slow tests (`@Tag("db")`, `@Tag("e2e")`) so dev-loop `mvn test` excludes by default.
4. JaCoCo coverage reporting at module level.
5. Backfill three weak areas (appointment expansion, permission predicates, XML round-trip).

## Why now

Date → `LocalDateTime` (PRDs 001a, 014, 015) + Jackson 3 (PRD 011) rewrote storage/wire-format with weak coverage; PRD 016 triaged the deletion fallout. PRD 007 baseline is **94 tests** (rapla-core 14, rapla-client 16 mostly interactive Swing, rapla-server 11, rapla-app 19 mostly `@SpringBootTest`) — most assertions in slowest tier. Spring context-cache work (PRD 007 Phase 2.7) reduces marginal `@SpringBootTest` cost but not cold-boot of the first test per context — pushing logic out of `@SpringBootTest` is the larger win.

## Scope

| Path | Change |
|---|---|
| `rapla-server/src/test/java/org/rapla/test/util/FacadeTestSupport.java` | New tier-2 base class — `@TempDir` + `FileOperator` + `FacadeImpl`, wired by hand, no Spring |
| `rapla-server/src/test/resources/testdefault.xml` | Move (or copy) from `rapla-app/src/test/resources/` so server-tier tests can use it without depending on rapla-app's test classpath |
| `rapla-bom/pom.xml` | Add `<excludedGroups>db,e2e</excludedGroups>` to surefire default config; document `-DexcludedGroups=` override for full-suite runs |
| `rapla-server/src/test/java/org/rapla/storage/dbsql/tests/MariadbTest.java`, `ConcurrentTests.java` | Add `@Tag("db")` |
| `rapla-app/src/test/java/.../*IntegrationTest.java` (the `@SpringBootTest` ring) | Add `@Tag("e2e")` to the four full-context tests; keep MockMvc tests in the default lane |
| `rapla-bom/pom.xml` | Add JaCoCo plugin (skip-by-default; opt in via `-Pcoverage`) |
| (new) `rapla-core/src/test/java/org/rapla/entities/tests/AppointmentExpansionTest.java` | Backfill repeating-rule expansion edge cases |
| (new) `rapla-core/src/test/java/org/rapla/entities/tests/PermissionPredicateTest.java` | Backfill `RaplaDefaultPermissionImpl` matrix |
| (new) `rapla-core/src/test/java/org/rapla/storage/xml/XmlRoundTripTest.java` | Read `testdefault.xml`, serialize, re-read, assert structural equality |
| `AGENTS.md` | Add §10 "Testing conventions" — pyramid + tagging + how to use `FacadeTestSupport` |

Out of scope: rewriting the existing interactive Swing tests in `rapla-client`
(`*EditTest`, `GUITestCase` descendants). Those are manual harnesses, not
candidates for CI.

## Plan

### Phase 1 — Tier-2 base class + one demo conversion ✅ Done 2026-05-09

Shipped: `testdefault.xml` copied to `rapla-server/src/test/resources/`; `FacadeTestSupport` at `rapla-server/src/test/java/org/rapla/test/util/`; `FacadeTestSupportTest` self-test (audit of rapla-app `@SpringBootTest` ring showed all 6 tests genuinely need Spring); AGENTS.md §10 updated with pyramid + base-class usage.

Sketch of the base class:

   ```java
   public abstract class FacadeTestSupport
   {
       protected static final String DEFAULT_FIXTURE = "/testdefault.xml";

       @TempDir Path tempDir;

       protected Logger logger;
       protected FileOperator operator;
       protected RaplaFacade facade;

       /** Override to swap the fixture (e.g. a smaller, hand-written one). */
       protected String fixtureResource() { return DEFAULT_FIXTURE; }

       @BeforeEach
       void setUpFacade() throws Exception
       {
           logger = RaplaBootstrapLogger.createRaplaLogger();
           Path dataFile = tempDir.resolve("rapla-data.xml");
           try (InputStream in = getClass().getResourceAsStream(fixtureResource()))
           {
               if (in == null) throw new IllegalStateException(fixtureResource() + " not on classpath");
               Files.copy(in, dataFile, StandardCopyOption.REPLACE_EXISTING);
           }

           AbstractBundleManager bundleManager = new ServerBundleManager();
           RaplaResources i18n = new RaplaResources(bundleManager);
           RaplaLocale raplaLocale = new RaplaLocaleImpl(bundleManager);
           CommandScheduler scheduler = new DefaultScheduler(logger);

           Set<PermissionExtension> permissionExtensions = new LinkedHashSet<>();
           permissionExtensions.add(new RaplaDefaultPermissionImpl());

           Map<String, FunctionFactory> functionFactoryMap = Map.of(
                   StandardFunctions.NAMESPACE, new StandardFunctions(raplaLocale));

           operator = new FileOperator(logger, i18n, raplaLocale, scheduler,
                   functionFactoryMap, dataFile.toAbsolutePath().toString(),
                   permissionExtensions);
           operator.connect();

           FacadeImpl impl = new FacadeImpl(i18n, scheduler, logger);
           impl.setOperator(operator);
           facade = impl;
       }

       @AfterEach
       void tearDownFacade()
       {
           if (operator != null && operator.isConnected())
           {
               try { operator.disconnect(); } catch (Exception ignored) {}
           }
       }
   }
   ```

Notes: `FunctionFactory` production registers 3 (`StandardFunctions`/`DurationFunctions`/`AppointmentNoteFunctions`); tests get just `StandardFunctions` by default. `FileOperator.connect()` is 200-400 ms for the 500-line fixture — startup dominated by SAX parse.

### Phase 2 — Tagging and dev-loop gating ✅ Done 2026-05-09

Shipped: `@Tag("db")` on `ConcurrentTests` (+ JUnit 5 migration); `@Tag("e2e")` on 4 acceptance tests; surefire `<excludedGroups>${test.excludedGroups}</excludedGroups>` + default `db,e2e`. `MariadbTest` skipped (a `main()` scratchpad never discovered by surefire). Default `mvn test`: 1m 51s, 49+ tests, green. `< 60 s` target not hit — gap owned by PRD 007 Phase 2.7 (Spring context-cache sharing).

### Phase 3 — Coverage reporting ✅ Done 2026-05-09

Shipped: JaCoCo behind `coverage` profile, surefire `forkCount=1` (JaCoCo agent needs forked JVM). `mvn -Pcoverage test` ~1m 17s vs default ~1m 51s — instrumentation cheap; forking adds ~10s. Baseline in dated section. Follow-ups deferred: aggregator `report-aggregate` (needs each module as dep), CI matrix (decide once hosting decided), floor gating (per-module 0-7 % too shaky).

### Phase 4 — Backfill the three weakest areas ✅ Done 2026-05-10

3 original items + 6 follow-ups (78 new test methods). Aggregate 12 → 15 % instr / 10 → 14 % branch; rapla-core 26 → 35 %, rapla-server 16 → 21 %. Close-out at top of PRD.

10. **Appointment expansion** ✅ — `AppointmentBlocksExpansionTest` (rapla-core, 14 tier-1, 84 ms) on `AppointmentImpl.createBlocks(...)`. `AppointmentImpl` reached 59 % line / 52 % branch.
11. **Permission predicates** ✅ — `PermissionMatrixTest` (rapla-server, tier-2, 12 tests, 5 s). Tier-2 instead of pure tier-1 because the real user/group/permission graph is more readable than mocks. `RaplaDefaultPermissionImpl` → 45 / 24 %; `PermissionController` → 30 / 22 %. Time-bound permission paths NOT covered (no time-bound perms in fixture) — follow-up if area churns.
12. **XML round-trip** ✅ — `XmlRoundTripTest` (rapla-server, tier-2, 6 tests, 3.5 s). Uses `operator.saveData()` → `disconnect()` → `connect()` (less plumbing than driving `RaplaMainReader`/`Writer` directly). `org.rapla.storage.xml` 41 → 68 % instr, ~30 → 58 % branch. No drift bugs.

## Tests

This PRD is mostly *adding* tests; "tests" here means invariants that prove
the infrastructure works.

| Phase | Verification |
|---|---|
| 1 | `mvn -pl rapla-server -am test -Dtest=<demo-conversion>` runs in < 2 s wall (was > 10 s as `@SpringBootTest`). Test passes. |
| 2 | `mvn test` (default) excludes `MariadbTest`, `ConcurrentTests`, the four E2E tests. `mvn test -DexcludedGroups=` includes them. |
| 3 | `mvn -Pcoverage test` produces `rapla-core/target/site/jacoco/index.html`. Default `mvn test` is unaffected (no instrumentation overhead — verify by comparing wall-clock to PRD 007's < 90 s target). |
| 4 | New tests added pass on a clean checkout. Coverage delta visible in JaCoCo report. |

## Risks

1. **`FacadeTestSupport` drifts from production wiring.** If `ServerCoreConfig` adds a new `FacadeImpl`/`FileOperator` dep and the base class isn't updated, tests pass while production breaks. *Mitigation:* the 4 `@SpringBootTest` acceptance tests (Phase-2 tagged `e2e`) catch wiring regressions; Javadoc notes the shadowing of `ServerCoreConfig.raplaFacade` + `ServerStorageSelector.createFileOperator`.
2. **Tag exclusion creates "did it run?" blind spot.** *Mitigation:* CI matrix runs fast + full lanes; local dev fast only.
3. **JaCoCo can interact badly with reflection.** Rapla uses Jackson 3 field-based serialization + ECJ reflection. *Mitigation:* JaCoCo opt-in via `-Pcoverage`; if a test fails only under coverage, it's a JaCoCo quirk.
4. **Backfill tests calcify a current bug.** *Mitigation:* write tests against expected behaviour (PRD/spec) first, then check code matches. If it doesn't, separate bug PRD.

## Open Questions

1. ✅ **Move vs copy `testdefault.xml`** — copy (2026-05-09). Revisit on third copy or divergence.
2. **JUnit 5 migration of rapla-core JUnit 4 tests?** Recommendation: JUnit 5 for everything new; don't touch existing JUnit 4 except when editing.
3. **`FacadeTestSupport` operator choice?** `FileOperator` is the cheapest real operator (no JDBC, no in-memory variant exists); introduce `InMemoryOperator` only if temp-file overhead becomes a bottleneck (< 1 s/test today).
4. **Minimum-viable tier-1 fixture?** Share `testdefault.xml` for facade-level; hand-write tiny XML for focused unit tests. Don't engineer a builder until the pattern repeats.
5. **CI matrix or single lane?** Decide post-Phase-2 numbers.

## Dependencies on Other PRDs

| PRD | Relationship |
|---|---|
| **007: Build & Test Performance** | Direct complement. 007 made the suite executable; 017 fills it. Phase 2 of this PRD touches `rapla-bom/pom.xml` surefire config — coordinate with 007 Phase 2 changes. |
| **005: Multi-Module Split** | Hard prerequisite (already landed). The pyramid here only makes sense with rapla-core / rapla-server / rapla-app separate. |
| **011: Spring Boot 4 / Jackson 3** | Surfaced wire-format coverage gaps (PRD 016). Phase 4 #12 (XML round-trip) is the durable answer. |
| **016: Pre-checkin Deletion Audit** | Tactical fix for past damage. 017 is the strategic prevention. |
