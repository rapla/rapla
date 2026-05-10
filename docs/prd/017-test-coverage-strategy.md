# PRD 017: Test Coverage Strategy

**Status:** in-progress (Phases 1–3 + Phase 4 expanded with hardening + perf round 2026-05-10)
**Date:** 2026-05-10

### 2026-05-10 — Hardening + performance round

Two new tests for conflict / overlap detection, plus a docs page and one
real bug found.

**`AppointmentOverlapHardeningTest`** (rapla-core, tier-1, 23 tests, 110 ms):
edge cases for `AppointmentImpl.overlapsAppointment` — containment, touching,
adjacent, single×repeating, repeating×repeating, exceptions, DST,
leap year, zero-duration, MONTHLY semantic. Every test asserts symmetry
(`a.overlaps(b) == b.overlaps(a)`), the largest class of conflict-detection
bugs.

**`ConflictPerformanceTest`** (rapla-server, tier-2, `@Tag("perf")`, ~18 s):
1000-reservation deterministic fixture across 6 allocatables, mix of
single/DAILY/WEEKLY/MONTHLY with exceptions. Measures bulk store, full-graph
`getConflicts()`, per-reservation conflict, range query, incremental store+reindex,
and `overlapsAppointment` throughput. Generous order-of-magnitude budgets
(flag 5–10× regressions, not noise). Live `[perf]` phase markers to stderr.

`perf` tag added to `<test.excludedGroups>` default in `rapla-bom/pom.xml`
alongside `db,e2e`. Runs only on demand via `-Dtest.excludedGroups=db,e2e`
(or empty for everything). Default `mvn test` ignores it transparently.

Reference numbers (Linux WSL2, May 2026):
- Build 1000 reservations: ~130 ms
- Bulk `storeObjects(1000)`: ~7–13 s (dominated by XML serialize + reindex)
- `getConflicts()` returning 16,259 conflicts: ~280–700 ms
- `getConflictsForReservation(pivot)`: ~10–15 ms
- Incremental store on 1000-reservation graph: ~1.3–3.6 s
- `overlapsAppointment(daily-365, weekly-52)`: ~30–60 µs/call

**Documentation:** [`docs/conflict-detection.md`](conflict-detection.md)
covers the algorithm (closed-open intervals, gcd fast-path for fixed-interval
repeats, sweep-line for ConflictFinder), the surprising MONTHLY semantic
(Nth-weekday-of-month, not same-day-of-month), the perf-test fixture, the
sanity-budget assertions, and how to run with live output
(`-Dsurefire.useFile=false`).

**Real bug surfaced (#3 of the session):**
`LocalAbstractCachableOperator.storeAndRemoveAsync(...)` is an empty stub
([source](../../rapla-server/src/main/java/org/rapla/storage/impl/server/LocalAbstractCachableOperator.java#L214)) —
returns `scheduler.run(() -> { })` with an empty lambda. So
`facade.dispatch(Collection, Collection)` silently no-ops on the
file-operator backend. ConflictPerformanceTest's first run reported 1000
reservations dispatched in 54 ms with 0 actually persisted; the test was
adjusted to use `facade.storeObjects(T[])` (sync, works) and the bug
flagged separately. **Affected callers:** anyone calling `facade.dispatch(...)`
against a file-backed operator. **Workaround for now:** sync APIs all work.

**Test discovery worth flagging:** my initial assertion
`monthlyRepeatOverlapsWithSingleOnTargetDay` (Apr 15) failed; investigation
showed Rapla's MONTHLY follows the "Nth weekday of the month" convention
(Apr 16 = third Thursday, the actual target). Not a bug — same as Outlook /
Google Calendar — but a load-bearing semantic that nothing else in the test
suite pinned. The hardening test now documents and pins it.

### 2026-05-10 — Phase 4 follow-up: ConflictFinder via facade landed

`ConflictFinderViaFacadeTest` (rapla-server, tier-2 via `FacadeTestSupport`,
5 tests, 15 s wall):

- Two reservations on the same allocatable in overlapping windows → both see
  each other in `getConflictsForReservation` (symmetric).
- Touching-edge appointments (A ends 11:00, B starts 11:00) → assertion is
  symmetry, not a specific count (touching-edge convention may vary).
- Two reservations on *different* allocatables in the same window → neither
  references the other in their conflict list.
- Two reservations on the same allocatable on *different days* → neither
  references the other.
- Global `facade.getConflicts()` count strictly increases after storing two
  overlapping reservations on the same allocatable.

Each test exercises the full save chain: `newReservation` →
`addAppointment` → `addAllocatable` → `store` → `ConflictFinder` →
`getConflictsForReservation`. That's why the per-test wall is ~3 s — heavier
than reads, but still 3-5× faster than the equivalent `@SpringBootTest`.

**Coverage delta — biggest jump of Phase 4 so far:**

| | Before | After |
|---|---:|---:|
| `org.rapla.storage.impl.server` package | 31 % | **44 %** (+13 pts, +1,355 covered) |
| `ConflictFinder` class | (~25 % indirect) | **42 % line / branch ~30 %** |
| Aggregate **rapla-core** | 26 % | **33 %** (+7 pts) |
| Aggregate **rapla-server** | 16 % | **20 %** (+4 pts) |
| **Aggregate overall** | 13 % | **14 %** (branch 11 → 13 %) |

The ripple effect is bigger than the per-package win because storing a
reservation cascades through `FacadeImpl.store` → operator dispatch →
LocalCache mutation → ConflictFinder reindex → write-out — 5 layers each
of which got new coverage.

### 2026-05-10 — Phase 4 follow-up: FacadeImpl mutation cycle landed

`FacadeMutationTest` (rapla-server, tier-2 via `FacadeTestSupport`,
8 tests, 5 s wall):

- `edit → modify → store → re-read` cycle persists attribute changes
- `newAllocatable → store` makes the entity visible in `getAllocatables` and
  resolvable by `tryResolve(reference)`
- `remove` reduces the count and `tryResolve` returns null
- `clone(source, user)` produces independent entity with fresh id; mutating
  the clone doesn't affect the original
- `editListAsync` returns a map keyed by each input (writable counterpart
  per entry)
- `getReservationsForAllocatable` and `getReservations(user, range, …)`
  return fixture reservations (testdefault.xml has them in 2002)
- Future-window query returns consistent (possibly empty) result without
  throwing

`waitFor(Promise<T>)` helper promoted from `ClassificationAndNameformatTest`
to `FacadeTestSupport` so tier-2 tests stop redefining it. Backed by
`thenAccept` + `exceptionally` + `CountDownLatch` (the custom
`org.rapla.scheduler.Promise` has no `.get()` and no `whenComplete`).

**Coverage delta:**

| | After |
|---|---:|
| `FacadeImpl` (instr / branch) | **23 % / 22 %** (738 of 3,099 instructions, 61 of 272 branches) |
| `org.rapla.facade.internal` package | 8 % → **9 %** (+87 instructions) |
| Aggregate overall | 13 % → 13 % |

Package headline barely moves because `facade.internal` also contains
`ClientFacadeImpl`, `CalendarModelImpl`, `ConflictImpl`, `AllocationChangeFinder`
which my test doesn't touch. FacadeImpl's 23 % is the meaningful number —
the most-called facade methods (edit / store / remove / clone / dispatch /
getReservations*) now have direct tier-2 pressure where they had only
indirect @SpringBootTest exercise before.

**Test discovery:** Initial `assertNotEquals(original, edited)` failed
because `Allocatable.equals` is id-based — same logical entity, even when
one is read-only and the other writable. Switched to `assertNotSame` for
instance identity. Worth flagging as a foot-gun for future facade-mutation
tests: don't use `assertNotEquals` to assert "is this a different copy?"

### 2026-05-10 — Phase 4 follow-up: Classification + ParsedText landed

`ClassificationAndNameformatTest` (rapla-server, tier-2 via `FacadeTestSupport`,
6 tests, 4 s wall):

- Every allocatable's `getName(locale)` is non-null, non-empty, and not the
  entity id (the PRD 011 bug shape — tier-2 canary for the slow-tier-4
  `HeadlessClientNameResolutionIntegrationTest`)
- `facade.editAsync(allocatable).get()` → `setValue` → `getName` reflects the
  change on the editable copy; original (read-only) view is untouched
- `getValue(key)` and `getValueForAttribute(attr)` return the same value for
  the same attribute
- `getValueAsString` non-null for every present attribute
- `type.getAttribute(key)` returns the same instance as iteration
  (post-deserialize identity)
- `allocatable.getClassification().getType()` always non-null and matches one
  of `facade.getDynamicTypes()` (resolver wiring intact)

**Coverage delta on `org.rapla.entities.dynamictype.internal`:**

| Class | After |
|---|---:|
| `ClassificationImpl` | 54 % line / 37 % branch |
| `ParsedText` | 51 % line / 36 % branch |
| `DynamicTypeImpl` | 36 % line / 22 % branch |
| Package total | 27 % → **29 %** (194 more instructions covered) |

Package headline barely moved because the 10K-instruction package is
dominated by ParsedText's many evaluation branches and DynamicTypeImpl's
many helpers. The wins are concentrated on the high-value entry points
(`getName`, `getValue`, `getValueAsString`) — exactly the chain that broke
in PRD 011 / cleanup 016. Future regressions in that chain now surface in
under 4 s rather than only via the 10–15 s tier-4 e2e.

**Implementation note worth flagging.** The custom `org.rapla.scheduler.Promise`
interface has no `.get()` and no `whenComplete` — only `thenAccept` /
`exceptionally` / `handle` / `finally_`. Tier-2 tests that need to wait on
a facade Promise must use a `CountDownLatch` + `AtomicReference` pattern.
Captured this in the test as a private `waitFor` helper; if a third tier-2
test needs it, promote to a static utility on `FacadeTestSupport`.

### 2026-05-10 — Phase 4 #11 (permission matrix) landed

`PermissionMatrixTest` (rapla-server, tier-2 via `FacadeTestSupport`,
12 tests, 5 s wall):

- Admin shortcut: admin can modify / admin / delete every allocatable, can
  create + read every dynamic type.
- Non-admin default-grant path: monty can read & allocate the default-permission
  resources (`allocate_conflicts` granted to everyone — includes ALLOCATE → REQUEST → READ).
- Non-admin negative path: cannot modify or admin unowned resources, cannot
  create instances of types that don't grant CREATE explicitly.
- `isOwner` static: matches when ownerRef.isSame; false when ownerRef is null
  (regardless of which user asks).
- `canCreateReservation` doesn't NPE on either user path.

Test discovery: `nonAdminCannotEditDynamicTypes` (initial form) failed because
the `event` type explicitly grants `access="create"` to everyone — that's by
design, not a bug. Reframed the test as "find at least one type where admin
can but non-admin cannot create" so we exercise the restricted path without
asserting blanket admin-only on dynamic types.

**Coverage delta:**

| Class | Before | After |
|---|---:|---:|
| `RaplaDefaultPermissionImpl` (instr / branch) | (~0 % direct) | **45 % / 24 %** |
| `PermissionController` (instr / branch) | (~25 % indirect) | **30 % / 22 %** |
| Overall aggregate | 13 % | 13 % |

Headline doesn't move — these are small classes (177 + 1,170 instructions
combined) relative to the 261K reactor total. The wins are local:
- The admin/owner/group/EDIT/ADMIN matrix in `RaplaDefaultPermissionImpl.hasAccess`
  now has direct test pressure rather than being exercised only as a side
  effect of facade flows.
- Branch coverage on `PermissionController` doubled from "incidentally hit"
  to "deliberately exercised" — future refactors of the 11 public can*
  methods will surface as test failures, not silent behavior changes.

### 2026-05-10 — Phase 4 #12 (XML round-trip) landed

`XmlRoundTripTest` (rapla-server, tier-2 via `FacadeTestSupport`, 6 tests,
3.5 s wall):

- Categories: top-level keys + ids identical after round-trip
- Dynamic types: count + key set identical
- Allocatable names: every name non-null and non-empty after round-trip (the
  exact bug shape PRD 011 introduced and `HeadlessClientNameResolutionIntegrationTest`
  still catches at the much higher tier-4 cost)
- User logins: count + login set identical
- Allocatable classification → DynamicType resolver intact (server-side
  twin of the failing e2e test)
- Idempotency under double round-trip (load → save → load → save → load)

Test pattern: `operator.saveData()` → `operator.disconnect()` → `operator.connect()`,
then re-snapshot facade state and compare.

**Coverage delta on `org.rapla.storage.xml`:**

| | Before #12 | After #12 |
|---|---:|---:|
| Instruction coverage | 41 % (3,375 of 8,122 covered) | **68 % (5,593 of 8,122 covered)** |
| Branch coverage | (~30 %) | **58 %** |
| Overall aggregate | 12 % | **13 %** |

The +27-point package jump comes from now exercising the writer path
(`RaplaMainWriter` + the per-entity write helpers were near-zero coverage
before — only the read path had been exercised by every operator-using test).

No bugs surfaced. The 6 round-trips all succeed first try, which is itself
useful evidence: the wire format isn't currently asymmetric in any of the
fields snapshotted.

### 2026-05-09 — Phase 3 follow-up + Phase 4 first test landed

**Aggregator JaCoCo report.** Wired in the root `pom.xml` under the same
`coverage` profile. Approach uses the standard `report-aggregate` plus a
preceding `merge` step that folds rapla-app's `jacoco.exec` into the
aggregator's `target/jacoco.exec` (which the report-aggregate's default
`**/target/jacoco.exec` discovery picks up). rapla-app couldn't be added as
a `<dependency>` directly because its `target/classes` contains JNLP
webclient/ jars (multi-release shaded Jackson) that crash JaCoCo's class
scanner. Per-module rapla-app report is also skipped (`<phase>none</phase>`
override in `rapla-app/pom.xml` for the same reason).

Run: `mvn -Pcoverage verify -Dtest.excludedGroups=` → output at
`target/site/jacoco-aggregate/index.html`.

**Aggregate baseline (full lane):** 31,803 / 261,713 instructions covered =
**12 % overall, 10 % branch.**

| Module bundle in aggregate | Coverage | Per-module solo (Phase 3) |
|---|---:|---:|
| rapla-core | **26 %** | 5 % |
| rapla-server | **16 %** | 7 % |
| rapla-client | **1 %** | 0 % |

The rapla-core jump (5 → 26 %) and rapla-server jump (7 → 16 %) are the
@SpringBootTest contributions correctly attributed for the first time.
rapla-client stays low — its existing tests are interactive Swing harnesses,
not real automated assertions.

**Phase 4 first test landed.**
`AppointmentBlocksExpansionTest` (rapla-core, 14 tier-1 tests, 84 ms wall):

- DAILY / WEEKLY / MONTHLY / YEARLY repeating expansion via `createBlocks`
- Custom intervals (`setInterval(3)`, every-3-days)
- Bounded by `setNumber(N)` vs `setEnd(date)`
- Exception handling (with and without `excludeExceptions`)
- DST boundaries (Europe spring-forward 2026-03-29 + fall-back 2026-10-25)
- Window filtering, zero-length windows
- Block timestamp + duration invariants

After backfill, **`AppointmentImpl` alone reached 59 % line / 52 % branch**
in the aggregate report. Package-level (`entities.domain.internal`) stayed
at 42 % — that aggregate already included @SpringBootTest contributions, so
the marginal impact is on the *speed and locality* of feedback rather than
the headline number: failures in `processBlocks` now surface in 84 ms, not
in a 7-second @SpringBootTest cycle.

No bugs surfaced — all 14 tests passed first try after the package-private
`RepeatingImpl` accessibility fix (use `Appointment` interface in helper
return type, not the concrete class).

### 2026-05-09 — Phase 3 landed

JaCoCo wired into `rapla-bom/pom.xml` under a `coverage` profile (skip-by-default).
Run with `mvn -Pcoverage test`. Profile flips surefire to `forkCount=1` (JaCoCo's
`-javaagent` arg needs a forked JVM; the default `forkCount=0` swallows
`argLine`). Reports land at `<module>/target/site/jacoco/index.html`.

**Baseline (full lane, `-Pcoverage test -Dtest.excludedGroups= -Dmaven.test.failure.ignore=true`):**

| Module | Covered / total | Coverage % | Branch % |
|---|---:|---:|---:|
| rapla-core | 4,047 / 78,478 | **5%** | 4% |
| rapla-client | 558 / 131,908 | **0%** | 0% |
| rapla-server | 3,659 / 51,332 | **7%** | 5% |
| rapla-app | 7 / 12 | 58% | n/a |

**Caveat — per-module reports undercount.** JaCoCo only attributes coverage
inside the surefire that produced its `jacoco.exec`. The Spring Boot tests in
rapla-app exercise rapla-server/rapla-core code, but that lands in
*rapla-app*'s `jacoco.exec` (which is scoped to rapla-app's bytecode = just
`RaplaSpringBootApplication.java`). True full-stack coverage needs
`jacoco-maven-plugin`'s `report-aggregate` goal at the aggregator level —
**deferred as a Phase 3 follow-up** (would need each module added as a dep on
the root `pom.xml`).

**Where coverage *is* concentrated** (per-module top packages):

- **rapla-core**: `rest.client.internal.isodate` (49%), `components.util` (42%),
  `entities.domain.internal` (25%), `rest` (20%), `entities.dynamictype` (17%),
  `rest.jackson` (16%), `entities.domain` (11%). Maps directly to the
  existing tier-1 tests (`DateToolsTest`, `AppointmentTest`,
  `Jackson3TransientInitializerTest`, `JsonReaderTest`).
- **rapla-server**: `endpoints.server.token` (76% — `SignedTokenTest`),
  `components.i18n.server` (63% — `RaplaLocaleTest`, `TestI18nLocaleFormats`),
  `plugin.mail.server` (30%), `storage.impl.server` (23% —
  `TestEntityHistory` + new `FacadeTestSupportTest`), `storage.dbfile` (21% —
  via `FacadeTestSupportTest`).
- **rapla-server 0% packages** (own-tests only): `storage.dbsql` (10K instr),
  `server.spring.web` (707 instr), `server.spring` (1.1K), `server.servletpages`
  (726). These run via rapla-app's `@SpringBootTest` ring — coverage shows up
  there, just not in rapla-server's own report.

**Phase 4 implication:** the dbfile (FileOperator) and storage.impl.server
packages are the highest-leverage targets. New `FacadeTestSupport`-based
tests will land here cheaply now that the base class is in place.

### 2026-05-09 — Phase 2 landed

Tagging is wired in. `@Tag("db")` on `ConcurrentTests` (migrated to JUnit 5 in
the same change — straight rename, no arg-flips needed since every `Assert.*`
call was 1- or 2-arg). `@Tag("e2e")` on the four `@SpringBootTest` acceptance
tests in rapla-app (`RaplaSpringBootApplicationTest`,
`ServerServiceIntegrationTest`, `HeadlessClientNameResolutionIntegrationTest`,
`SwingClientStartIntegrationTest`).

`rapla-bom/pom.xml` gained `<excludedGroups>${test.excludedGroups}</excludedGroups>`
in surefire config + `<test.excludedGroups>db,e2e</test.excludedGroups>` as the
default property. Override with `mvn test -Dtest.excludedGroups=` (empty) to
run everything.

Verification:

| Lane | Command | Wall | Result |
|---|---|---:|---|
| Fast (default) | `mvn test` | 1m 51s | green; 4 e2e + ConcurrentTests skipped |
| Full | `mvn test -Dtest.excludedGroups=` | — | one pre-existing 401 in `HeadlessClientNameResolutionIntegrationTest` (homer/duffs no longer authenticates), unrelated to tagging — flagged separately |

**Note on fast-lane wall time (1m 51s):** higher than PRD 007's 1m 9s baseline.
The gap is the MockMvc controller tests in rapla-app — each cold-boots its
own Spring context (the cache key isn't shared because of per-class
`@TempDir`/`@DynamicPropertySource`). PRD 007 Phase 2.7 plans to consolidate
those into one shared context; that's the right home for that fix, not this
PRD. Recorded here so the next baseline check doesn't double-attribute.

`MariadbTest` left untouched — it's a `public static void main()` scratchpad
with hardcoded credentials, not a JUnit test, so surefire never picks it up.
Flagged to user as a separate concern (credentials in git).

### 2026-05-09 — Phase 1 landed

`FacadeTestSupport` is in tree at
`rapla-server/src/test/java/org/rapla/test/util/FacadeTestSupport.java` with
six self-tests (`FacadeTestSupportTest`) exercising
`facade.getSuperCategory()`, `getDynamicTypes()`, `getAllocatables()`,
`getUsers()`. All pass. AGENTS.md gained §10 documenting the pyramid + how
to use the base class.

Measured speed delta (`mvn -pl <module> -am test -Dtest=Class`, Surefire's
own "Time elapsed" line, not the reactor wall):

| Test | Tests | Wall | Per test |
|---|---:|---:|---:|
| `FacadeTestSupportTest` (tier 2, no Spring) | 6 | 3.3 s | ~550 ms |
| `ServerServiceIntegrationTest` (tier 3, `@SpringBootTest`) | 2 | 7.2 s | ~3.6 s |

The first fresh facade in a class boots in ~150 ms (xml SAX parse + cache
init). The first `@SpringBootTest` boots in ~7 s. **45× faster cold-start**;
**~4× faster for a 6-test class**. The savings compound across separate
`mvn test -Dtest=…` invocations because there's no Spring context cache to
warm.

Open Question #1 resolved: **copy** `testdefault.xml` rather than move +
test-jar dep. Move adds a pom edit to rapla-app for Phase-1 scope, doesn't
unblock anything. The 30 KB duplicate is an acceptable price; revisit if a
third copy appears or fixtures start diverging.

## Goal

Grow the automated test suite into a fast, layered safety net so refactors
(date migration, Spring Boot 4 / Jackson 3, multi-module split) can be done
with confidence and CI stays under ~90 s.

PRD 007 fixed *suite execution time* and the silently-skipped JUnit 4 classes.
This PRD addresses *what to test, where to put it, and how to keep new tests
cheap*. They are complementary — 007 makes the train fast, 017 fills the
carriages.

Concretely:

1. Establish a test pyramid that matches the 5-module reactor (PRD 005).
2. Provide a tier-2 base class (`FacadeTestSupport`) so storage/facade tests
   don't need `@SpringBootTest`.
3. Tag and gate slow tests (`@Tag("db")`, `@Tag("e2e")`) so the dev-loop
   `mvn test` excludes them by default.
4. Add JaCoCo coverage reporting at the module level so the gap is visible.
5. Backfill coverage in three areas the date migration left thin:
   appointment expansion, permission predicates, XML round-trip.

## Why now

- The Date → `LocalDateTime` migration (PRDs 001a, 014, 015) and the
  Jackson 3 cutover (PRD 011) both rewrote storage/wire-format code that has
  weak test coverage. PRD 016 already triaged deletion fallout from the
  bulk-refactor scripts (AGENTS.md §6a). A coverage uplift now is cheap
  insurance against the next refactor.
- PRD 007 baseline is **94 tests across the reactor** after vintage-engine.
  By module:

  | Module | Test files |
  |---|---:|
  | rapla-core | 14 |
  | rapla-client | 16 (mostly interactive Swing harnesses, not real assertions) |
  | rapla-server | 11 |
  | rapla-app | 19 (mostly `@SpringBootTest`) |

  Most assertions live in the slowest tier. Inverting that ratio is the
  point of this PRD.
- Spring context-cache work (PRD 007 Phase 2.7) reduces the marginal cost
  of `@SpringBootTest` but *not* the cold-boot cost of the first test to
  request a given context. Pushing logic-level coverage out of `@SpringBootTest`
  is the larger win and only this PRD addresses it.

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

1. ~~Move `testdefault.xml`~~ **Copied** to `rapla-server/src/test/resources/`
   (Open Question #1 above). Original copy in `rapla-app/src/test/resources/`
   stays untouched.

2. Add `FacadeTestSupport` in `rapla-server/src/test/java/org/rapla/test/util/`.
   Sketch:

   ```java
   package org.rapla.test.util;

   import org.junit.jupiter.api.AfterEach;
   import org.junit.jupiter.api.BeforeEach;
   import org.junit.jupiter.api.io.TempDir;
   import org.rapla.RaplaResources;
   import org.rapla.components.i18n.internal.AbstractBundleManager;
   import org.rapla.components.i18n.server.ServerBundleManager;
   import org.rapla.entities.domain.permission.PermissionExtension;
   import org.rapla.entities.domain.permission.impl.RaplaDefaultPermissionImpl;
   import org.rapla.entities.dynamictype.internal.StandardFunctions;
   import org.rapla.entities.extensionpoints.FunctionFactory;
   import org.rapla.facade.RaplaFacade;
   import org.rapla.facade.internal.FacadeImpl;
   import org.rapla.framework.RaplaLocale;
   import org.rapla.framework.internal.DefaultScheduler;
   import org.rapla.framework.internal.RaplaLocaleImpl;
   import org.rapla.logger.Logger;
   import org.rapla.logger.RaplaBootstrapLogger;
   import org.rapla.scheduler.CommandScheduler;
   import org.rapla.storage.dbfile.FileOperator;

   import java.io.InputStream;
   import java.nio.file.Files;
   import java.nio.file.Path;
   import java.nio.file.StandardCopyOption;
   import java.util.LinkedHashSet;
   import java.util.Map;
   import java.util.Set;

   /**
    * Tier-2 base for tests that need a real {@link RaplaFacade} backed by a
    * {@link FileOperator} on a temp-dir copy of {@code testdefault.xml}. No
    * Spring, no MockMvc, no random port — boots in &lt; 1 s.
    *
    * <p>Subclass and use {@link #facade} / {@link #operator} directly. Each
    * test method gets a fresh facade, fresh temp file, fresh in-memory caches.
    */
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

   Notes:
   - `FunctionFactory` map: production registers three (`StandardFunctions`,
     `DurationFunctions`, `AppointmentNoteFunctions`). For most tests only
     `StandardFunctions` matters — the others can be added by overriders.
   - `connect()` on `FileOperator` reads the XML and populates `LocalCache`.
     Around 200–400 ms in practice for the 500-line `testdefault.xml`.
   - No DB, no Tomcat, no Spring context cache — startup cost is dominated
     by the XML SAX parse.

3. ~~Convert one tier-3 candidate test~~ **Replaced with a fresh tier-2
   self-test** (`FacadeTestSupportTest`). Audit of the rapla-app
   `@SpringBootTest` ring showed all six existing tests genuinely need the
   Spring graph (auth chain beans, MockMvc-routed controllers, full E2E
   login flow) — none could be moved to tier 2 without losing what they
   actually verify. The self-test serves the same demo purpose (proves the
   base class works + provides the speed-delta measurement) without forcing
   a contrived conversion that would lose coverage at tier 3.

4. ~~Document in AGENTS.md §10~~ Done. Pyramid table, `FacadeTestSupport`
   usage example, and tier-selection guidance landed.

### Phase 2 — Tagging and dev-loop gating ✅ Done 2026-05-09

5. ~~Add `@Tag("db")` to `MariadbTest`, `ConcurrentTests`~~ **`ConcurrentTests`
   only** — `MariadbTest` is a `main()` scratchpad with hardcoded creds, never
   discovered by surefire (no `@Test` methods), and shouldn't be in git at all
   (separate concern flagged to user). Migrated `ConcurrentTests` to JUnit 5
   in the same change — straight rename of `org.junit.*` → `org.junit.jupiter.api.*`,
   `Assert.` → `Assertions.`, `@Before/@After` → `@BeforeEach/@AfterEach`. All
   6 tests pass.

6. ~~Add `@Tag("e2e")` to the four acceptance tests~~ Done. All four are tagged.

7. ~~Wire surefire `<excludedGroups>db,e2e</excludedGroups>`~~ Done via a
   property `test.excludedGroups` (default `db,e2e`) so users can override
   with `mvn test -Dtest.excludedGroups=`.

   Ratio achieved after Phase 2:
   - Default `mvn test`: 1m 51s, 49+ tests, green
   - `mvn test -Dtest.excludedGroups=`: includes 4 e2e + 1 db class. 3 of 4
     e2e and the db class pass; one e2e (`HeadlessClientNameResolution…`)
     has a pre-existing 401 auth bug unrelated to tagging.

   The < 60 s default-lane target wasn't hit. Gap is owned by PRD 007 Phase 2.7
   (Spring context-cache sharing across MockMvc tests) — not this PRD.

### Phase 3 — Coverage reporting ✅ Done 2026-05-09

8. ~~Add JaCoCo behind a `coverage` profile~~ Done. Profile also flips
   surefire to `forkCount=1` because JaCoCo's `-javaagent` arg needs a forked
   JVM (default `forkCount=0` swallows `argLine`). `mvn -Pcoverage test`
   wall: ~1m 17s vs default ~1m 51s — instrumentation is cheap, but the
   forking switch alone is a +10s cost.

9. ~~Establish a baseline~~ Done. See header note. Three follow-ups
   identified, none in Phase 4 scope:

   a. **Aggregator `report-aggregate`** — adds full-stack attribution. Needs
      each module declared as a dep on the root pom. Defer.
   b. **CI matrix** — keep "coverage profile" out of the default CI run; add
      a nightly or PR-on-main job that publishes the report. Decide once
      hosting is decided.
   c. **No floor gating yet** — the per-module numbers (0–7%) are too
      shaky to gate on. Re-evaluate after Phase 4 lands a few rounds of
      backfill.

### Phase 4 — Backfill the three weakest areas

Pick targets by reading the JaCoCo aggregate report from Phase 3 and
cross-referencing with the date-migration PRDs.

10. **Appointment expansion** ✅ Done 2026-05-09. ~~`AppointmentImpl.expand(...)`~~
    `AppointmentImpl.createBlocks(...)` is the right method name. 14 tier-1
    tests in `AppointmentBlocksExpansionTest` (84 ms total) cover DAILY /
    WEEKLY / MONTHLY / YEARLY, custom intervals, bounded-by-number vs
    bounded-by-end, exception subtraction, DST boundaries, window filtering,
    block-duration invariants. AppointmentImpl reached 59 % line / 52 %
    branch coverage afterward.

11. **Permission predicates** ✅ Done 2026-05-10. `PermissionMatrixTest`
    (rapla-server, tier-2 via `FacadeTestSupport`, 12 tests, 5 s).
    Implemented as tier-2 instead of pure tier-1 because the fixture's real
    user/group/permission graph is more readable than constructing entity
    mocks. Coverage on `RaplaDefaultPermissionImpl` jumped to 45 % line /
    24 % branch (from ~0 % direct); `PermissionController` to 30 % / 22 %.
    Time-bound permission paths NOT covered (fixture has no time-bound
    permissions) — left as a follow-up if the area churns again.

12. **XML round-trip** ✅ Done 2026-05-10. `XmlRoundTripTest` (rapla-server,
    tier-2 via `FacadeTestSupport`, 6 tests, 3.5 s). Uses
    `operator.saveData()` → `disconnect()` → `connect()` rather than driving
    `RaplaMainReader`/`Writer` directly (less plumbing, exercises the same
    code paths in production order). Coverage on `org.rapla.storage.xml`
    jumped 41 → 68 % (instruction) and ~30 → 58 % (branch). No drift bugs
    surfaced.

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

1. **`FacadeTestSupport` drifts from production wiring.** If `ServerCoreConfig`
   adds a new dependency to `FacadeImpl` or `FileOperator` and the test base
   class isn't updated, tests pass while production breaks. *Mitigation:* the
   four `@SpringBootTest` acceptance tests in `rapla-app` (Phase 2 keeps them
   tagged `e2e`) catch wiring regressions in CI. Document in
   `FacadeTestSupport` Javadoc that it shadows `ServerCoreConfig.raplaFacade`
   + `ServerStorageSelector.createFileOperator`.

2. **Tag-based exclusion creates a "did it run?" blind spot.** A test tagged
   `db` that's never explicitly invoked rots silently. *Mitigation:* CI
   matrix runs both the fast lane and `-DexcludedGroups=` (full). Local dev
   uses fast lane only.

3. **JaCoCo bytecode instrumentation can interact badly with reflection-heavy
   code.** Rapla uses Jackson 3 field-based serialization (PRD 010) and
   ECJ-style reflection. *Mitigation:* JaCoCo is opt-in via `-Pcoverage` and
   never enabled in the default test path. If a test fails only under
   `-Pcoverage`, that's a known JaCoCo quirk, not a real regression.

4. **Backfill tests calcify a current bug.** A new "characterization test"
   that asserts current behaviour without checking against the spec just
   pins broken code in place. *Mitigation:* for Phase 4, write the test
   against the expected behaviour (PRD/spec) first, then check the current
   code matches. If it doesn't, that's a separate bug PRD — not silently
   pinned.

## Open Questions

1. ~~**Move `testdefault.xml` to `rapla-server` or copy it?**~~ **Resolved
   2026-05-09: copy.** See header note. Revisit if a third copy appears or
   fixtures diverge; introduce the test-jar dep then.

2. **Junit 5 migration of the rapla-core JUnit 4 tests?** PRD 007 left this
   open. `FacadeTestSupport` is JUnit 5 (`@TempDir`, `@BeforeEach`). If we
   want a single style across new tests this PRD adds, we either migrate
   the tier-1 backfills to JUnit 4 (boring, holds back) or settle on
   JUnit 5 going forward and let vintage-engine carry the legacy tests
   indefinitely. **Recommendation:** JUnit 5 for everything new; don't
   touch existing JUnit 4 tests except when editing them anyway.

3. **Should `FacadeTestSupport` use `FileOperator` or the in-memory variant?**
   There is no in-memory variant today — `FileOperator` is the cheapest
   real operator (XML on disk, no JDBC). If the temp-file write/read
   overhead becomes a bottleneck (unlikely at < 1 s/test), introduce an
   `InMemoryOperator` as a follow-up. Don't preempt.

4. **What's the minimum viable fixture for tier-1 tests?** `testdefault.xml`
   is 500 lines; for a permission-predicate matrix or appointment-expansion
   test, a 30-line hand-written XML is more readable. Decide per-test:
   share `testdefault.xml` for facade-level tests, hand-write tiny XML for
   focused unit tests. Don't over-engineer a fixture builder until the
   pattern is needed twice.

5. **CI matrix or single lane?** Single lane (`mvn test -DexcludedGroups=`)
   is simpler but blends fast and slow signals. Two lanes (fast first, full
   second, full only on `main`) gives faster PR feedback. Decide once
   Phase 2 numbers are in.

## Dependencies on Other PRDs

| PRD | Relationship |
|---|---|
| **007: Build & Test Performance** | Direct complement. 007 made the suite executable; 017 fills it. Phase 2 of this PRD touches `rapla-bom/pom.xml` surefire config — coordinate with 007 Phase 2 changes. |
| **005: Multi-Module Split** | Hard prerequisite (already landed). The pyramid here only makes sense with rapla-core / rapla-server / rapla-app separate. |
| **011: Spring Boot 4 / Jackson 3** | Surfaced wire-format coverage gaps (PRD 016). Phase 4 #12 (XML round-trip) is the durable answer. |
| **016: Pre-checkin Deletion Audit** | Tactical fix for past damage. 017 is the strategic prevention. |
