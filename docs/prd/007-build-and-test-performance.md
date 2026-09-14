# PRD 007: Build & Test Performance

**Status:** in-progress (Phase 0 + Phase 1 landed)
**Date:** 2026-05-07 (last update: 2026-05-08)

### 2026-05-08 — `RaplaSpringBootApplicationTest` isolated

`RaplaSpringBootApplicationTest` now uses `@TempDir` + `@DynamicPropertySource` (matching `UrlPreservationTest`/`AuthControllerIntegrationTest`) so it doesn't write to shared `data/data.xml`. The Phase 1 idempotency issue (second `mvn test` failing with `UnsatisfiedDependencyException` on corrupted shared state) is resolved — test reads from `testdefault.xml` copied into per-test `@TempDir`. 23/23 spring tests green.

## Goal

Cut wall-clock time for the two commands developers and CI run most:

| Command | Before any change | After Phase 0 + 1 | Target |
|---|---|---|---|
| `mvn clean compile` (cold) | ~47 s | **~21 s** | ≤ 30 s ✓ |
| `mvn compile` (no source change) | ~30–37 s — recompiles all 976 sources | **~6.5 s** | < 5 s |
| `mvn test` (full suite) | ~1 m 22 s **but only 39 tests across 10 of 30 classes actually ran** | **~1 m 9 s with 94 tests across the full suite** | < 90 s ✓ |

…and fix two correctness issues the perf audit surfaced:

- **19 JUnit 4 test classes silently skipped** because Surefire 3.x has no `junit-vintage-engine` on the classpath. The suite is much smaller than it appears.
- **`mvn test` is not idempotent.** A second run on a clean tree fails with Spring `UnsatisfiedDependencyException`s because `RaplaSpringBootApplicationTest` writes to default `data/data.xml` and corrupts shared state.

## Why now (and multi-module sequencing)

[PRD 004](done/004-multi-module-architecture-analysis.md) recommends a 5-module split, with pom-property/dependencyManagement/plugin config moving from `parent/pom.xml` into `rapla-bom`. Most surefire/compiler/resources tweaks land cleanly in that BOM and are easier to verify per-module. Doing them before the split means re-doing them after. **So the bulk of this PRD waits for PRD 005.**

Two items ship **before** PRD 005:

1. **Add `junit-vintage-engine`.** [PRD 004](done/004-multi-module-architecture-analysis.md) cycle audit needs the suite to actually exercise touched code. Restructuring with 60% of tests silently muted is dangerous.
2. **Fix `mvn test` non-idempotence.** Same reason: if the suite can't be re-run, every migration step requiring "did tests still pass" is unreliable.

Everything else waits.

## Scope

| File | Change | Phase |
|---|---|---|
| `parent/pom.xml` | Add `junit-vintage-engine` test dep | **Done Phase 0 #4** |
| `parent/pom.xml` | Upgrade `maven-resources-plugin` 3.0.2 → 3.3.1 | **Done Phase 0 #3** |
| `src/main/resources-filtered/.../RaplaSystemInfo_{cs,de,es,fr,nl,pl}.properties` | Normalise to UTF-8 | **Done Phase 0 #3** |
| `parent/pom.xml` | `<useIncrementalCompilation>false</useIncrementalCompilation>` | **Done Phase 0 #1** |
| `parent/pom.xml` | Remove second `resources:resources` execution bound to `compile` | **Done Phase 0 #2** |
| `RaplaSpringBootApplicationTest.java` | Add `@TempDir` + `@DynamicPropertySource` | Pre-split (Phase 1) |
| `parent/pom.xml` → `rapla-bom/pom.xml` | `<forkCount>1</forkCount>` + `<reuseForks>true</reuseForks>` | Post-split |
| (new) `SpringIntegrationTestBase.java` | Shared `@TempDir` + `@DynamicPropertySource` base so the three integration tests reuse one cached Spring context | Post-split |
| Root `pom.xml` (aggregator) | Document `mvn -T 1C` as recommended invocation; CI stays serial for log clarity | Post-split |

## Plan

### Phase 0 — Quick wins, shipped 2026-05-07

All in `parent/pom.xml`. Don't depend on the multi-module split.

1. **`<useIncrementalCompilation>false</useIncrementalCompilation>`** on `maven-compiler-plugin` 3.13.0. With this plugin, `true` is the over-conservative *recompile-everything-when-anything-changes* mode; `false` does proper mtime-based incremental. This is what dropped no-op compile from 33 s to 7 s.

2. **Removed duplicate `resources:resources` execution bound to `compile`.** Default lifecycle already binds it to `process-resources`; the second execution copied all 911 resources twice per build.

3. **Upgraded `maven-resources-plugin` 3.0.2 → 3.3.1.** Faster on no-op runs, clearer log lines. Upgrade exposed encoding rot in 6 files under `src/main/resources-filtered/org/rapla/RaplaSystemInfo_*.properties` (mostly UTF-8 with stray `0xc2` bytes between words — half-encoded NBSP — plus German file in pure ISO-8859-1). Files normalised in-place: orphan `0xc2` completed to `0xc2 0xa0` (NBSP), German file transcoded iso-8859-1 → utf-8. Verified: every file `file`-reports as UTF-8 and renders diacritics correctly.

4. **Added `org.junit.vintage:junit-vintage-engine`** (test scope; version managed by Spring Boot BOM at 5.10.2). Surefire 3.x only discovers JUnit Platform engines; without this all JUnit 4 tests (`@RunWith(JUnit4.class)`, `extends TestCase`) were silently ignored. Test count: **39 → 94**. One newly-activated test fails (`MailTest.testMailSend` — `NoClassDefFoundError: javax.activation.DataSource`); pre-existing bug Java 11+ exposed when it removed `javax.activation`. Tracked separately.

Verified: `mvn package -DskipTests` still produces 2647-file fat jar identical in shape.

Measurements (3 runs, median):

| | Before | After Phase 0 |
|---|---|---|
| `mvn clean compile` (cold) | 47 s | **21 s** |
| `mvn compile` (no-op) | 33 s | **6.5 s** |
| `mvn test` (full) | 1 m 22 s @ 39 tests | **1 m 9 s @ 94 tests** |

Remaining ~6.5 s on no-op compile is dominated by Maven JVM startup (~3 s) and the resources plugin re-stamping 921 files (no built-in incremental in 3.x without an IDE BuildContext). Going lower would need `mvnd` — out of scope.

### Phase 1 — Pre-split correctness

1. ~~Add `junit-vintage-engine`~~ — **done Phase 0 #4.** Test count jumped 39 → 94. One real bug surfaced (`MailTest.testMailSend`, `javax.activation.DataSource`).

2. **Fix `data/data.xml` mutation.** Pending. `RaplaSpringBootApplicationTest` (and a few others not using `@DynamicPropertySource`) write default `data/data.xml`, corrupting subsequent runs. Mirror the pattern in `ServerServiceIntegrationTest`: `@TempDir static Path tempDir` + `@BeforeAll` copy + `@DynamicPropertySource rapla.file-datasources.raplafile`. After this, `mvn test && mvn test` both pass green.

3. **Triage `MailTest` failure.** Either add `jakarta.activation:jakarta.activation-api` (or upgrade `javax.mail` transitively), or rewrite `MailapiClient.sendWithReflection` to use `jakarta.mail`. Outside this PRD's scope.

### Phase 2 — Post-split optimisations (after PRD 005 lands)

Apply each change *individually*, re-measure, commit one at a time so regressions bisect.

4. ~~Compiler incremental fix~~ — **done Phase 0.** Carry forward when `parent/pom.xml` becomes `rapla-bom/pom.xml`.

5. ~~Drop duplicate resources binding~~ — **done Phase 0.**

6. **Re-enable surefire forking.** `<forkCount>1</forkCount>` + `<reuseForks>true</reuseForks>` — restores JVM isolation between Maven and tests, prerequisite for `<parallel>classes</parallel>`, matters more once the suite is split per-module.

7. **Share Spring contexts.** Extract a `SpringIntegrationTestBase` so the three integration tests with per-class `@TempDir` + `@DynamicPropertySource` share one cached context. Verify via Spring's `ContextCache` log (`-Dlogging.level.org.springframework.test.context.cache=DEBUG`) that cache size is 1, not 3. Expected: ~5 s saved per CI run.

8. **Parallel reactor.** Document `mvn -T 1C` for developers. Don't enable in CI until Phase 2 #4–#7 stable; parallel + corrupted incremental state is the worst combination to debug.

### Phase 3 — Optional, defer until measured

9. Surefire test parallelism (`<parallel>classes</parallel>`, `<threadCount>2</threadCount>`). Risky — many JUnit 4 tests from step 1 assume serial execution and shared static state. Don't turn on without an audit.
10. Maven build cache extension. Worth ~30–50% on stable inputs but adds config surface. Re-evaluate after a quarter on Phase 2.
11. Upgrade `maven-compiler-plugin` to 3.14+ and use new `<incrementalCompilation>` parameter. Pure cleanup.

## Dev-loop monitoring patterns (Phase 3 — added 2026-05-07)

Documented as part of PRD 007 because they directly cut wait time inside the inner dev loop.

### Wait-for-condition pattern (replaces `sleep N` heuristics)

When an AI agent starts a long-running process and needs to know when it's "ready", `sleep 15` + status check is bad both ways: too long when ready in 8 s, too short when startup is degraded. Replace with `timeout` + `until grep` with 0.3 s poll:

```bash
timeout 60 sh -c 'until grep -q "Started.*in [0-9.]\+ seconds" logs/rapla.log; do sleep 0.3; done' \
  && echo "READY" || echo "TIMEOUT"
```

Properties: returns within `~startup_time + 0.15 s` (median half-poll); hard upper bound at `timeout` distinguishes "hung" from "completed"; foreground Bash call (no `run_in_background`); polling load negligible.

Measured on rapla startup (10.255 s warm-JVM Spring Boot):

| Pattern | Detection latency |
|---|---|
| `sleep 15` | always 15 s |
| `timeout 60 sh -c 'until grep -q ...; do sleep 0.3; done'` | ~10.4 s first run, **15 ms when re-checked after server up** |

### Stream-events pattern for long-lived watching

For multi-event reactions over the server's lifetime, use `tail -F` (capital F survives log rotation) in `run_in_background=true`, then attach `Monitor` against the shell ID. Each new line becomes a stream event.

### Why not `tail -f | grep …` in foreground

Open-ended: Bash blocks until pipe closes, which only happens when `tail` is killed. The two patterns above have explicit termination.

### Documented in AGENTS.md §8

Full server lifecycle using these patterns is in **AGENTS.md §8**. PRD 007 is the architectural rationale; AGENTS.md is the operational recipe.

## Tests

This PRD changes build/test infrastructure; "tests" means **test-suite invariants** proving the changes work.

| Phase | Verification |
|---|---|
| 1.1 | `mvn test` ≥ 100 tests (vs 39 today). Surefire output includes `AppointmentTest`, `DateToolsTest`, `TestEntityHistory`, `SignedTokenTest` (sentinel list — if any goes missing, vintage isn't picking them up). |
| 1.2 | `mvn test && mvn test` both succeed on clean checkout. `git status data/` reports no changes after either. |
| 0 (done) | Two consecutive `mvn compile`: second runs in ~7 s and compiler prints nothing. `mvn clean compile` shows `Copying 911 resources` once. `mvn package -DskipTests` still produces `target/rapla-2.1-SNAPSHOT/`. |
| 2.6 | `mvn test` produces one surefire fork per JVM. All 100+ tests pass. |
| 2.7 | `mvn -pl rapla-server test -Dlogging.level.org.springframework.test.context.cache=DEBUG` logs `cache statistics: [size = 1, …]`. Total Spring-test wall time drops by ≥ 4 s vs Phase 1 baseline. |

No new functional tests required.

## Risks

1. **JUnit 4 tests have rotted.** Some of the 19 silently-skipped classes likely fail when finally exercised. Phase 1.1 carves out time to triage; "X failures, Y skipped, Z fixed" is acceptable as long as failing ones are tracked.

2. **`forkCount=1` exposes a missing dependency.** The pom comment that disabled forking says "this creates a classpath where dependencies seems to be missing." That was Surefire ~2.x with manifest-jar classpaths; 3.x uses long-classpath argfile by default. If it recurs, set `<useManifestOnlyJar>false</useManifestOnlyJar>`.

3. **Phase 2 lands on top of PRD 005's pom restructure.** Conflict surface small (different files), but ordering matters: PRD 005 moves configs into `rapla-bom/pom.xml` first; don't start Phase 2 until PRD 005 merged.

4. **Spring context sharing changes test isolation.** If two integration tests pass separately but fail when sharing, the failure exposes a real ordering bug — but a bug this PRD created the conditions for. Phase 2.7 should be reverted (not patched in flight) if it surfaces flakiness; investigate separately.

## Open Questions

1. **JUnit 5 across the board vs vintage-engine long-term?** Vintage works but means two engines, two assertion styles, two `@Before*` annotations forever. A migration script (`org.junit:junit-migration`, IntelliJ's converter) could move the 19 classes in an afternoon. Decide before Phase 1.1, or accept vintage indefinitely.

2. **Phase 1 as one PR or two?** Adding vintage and fixing data.xml are independent; one diff is cleaner *if* no JUnit 4 tests fail under vintage. Decide after step 1.1.

3. **CI `-T 1C`?** Parallel reactor builds interleave failure logs. For a small team, developer-side latency wins outweigh log-readability cost; CI runtime isn't currently the bottleneck. Developers via `mvn -T 1C` (document in AGENTS.md); CI stays serial.

4. **`maven-build-cache-extension`?** Powerful but adds `.mvn/maven-build-cache-config.xml` to maintain. For a single-author repo with ~5 modules, cache hit rate may not justify the surface area. Defer; revisit after a quarter on Phase 2.

5. **Actual cause of "Recompiling the module because of changed source code"?** Hypothesis: duplicate resources binding (Phase 2 #5) touching `target/classes/` mtimes, plus `useIncrementalCompilation=true` over-conservatism (Phase 2 #4). If both fixes don't yield < 3 s no-op compile, deeper cause needs investigation (resources-plugin filter timestamps on resources-filtered/, or annotation-processor output detection).

## Dependencies on Other PRDs

| PRD | Relationship |
|---|---|
| **004: Multi-Module Architecture Analysis** | Informs Phase 2 — settings move into `rapla-bom`. |
| **005: Multi-Module Split** (planned) | **Hard prerequisite for Phase 2.** Phase 1 independent. |
| **001: Spring Boot Migration** | Spring context-cache in Phase 2.7 only matters because PRD 001 added Spring to test classpath. |
