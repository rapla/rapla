# PRD 007: Build & Test Performance

**Status:** in-progress (Phase 0 + Phase 1 landed)
**Date:** 2026-05-07 (last update: 2026-05-08)

### 2026-05-08 — `RaplaSpringBootApplicationTest` isolated

`RaplaSpringBootApplicationTest` now uses `@TempDir` + `@DynamicPropertySource` (matching the pattern in `UrlPreservationTest`/`AuthControllerIntegrationTest`) so it doesn't write to the shared `data/data.xml`. The test idempotency issue described in Phase 1 (second `mvn test` run failing with `UnsatisfiedDependencyException` because of corrupted shared state) is now resolved — the test reads from `testdefault.xml` copied into the per-test `@TempDir`. 23/23 spring tests still green.

## Goal

Cut wall-clock time for the two commands developers and CI run most:

| Command | Before any change | After Phase 0 + 1 (today) | Target |
|---|---|---|---|
| `mvn clean compile` (cold) | ~47 s | **~21 s** | ≤ 30 s ✓ |
| `mvn compile` (no source change) | ~30–37 s — recompiles all 976 sources | **~6.5 s** | < 5 s |
| `mvn test` (full suite) | ~1 m 22 s **but only 39 tests across 10 of 30 classes actually ran** | **~1 m 9 s with 94 tests across the full suite** | < 90 s for the full suite ✓ |

…and along the way, fix two correctness issues that the perf audit surfaced:

- **19 JUnit 4 test classes are silently skipped** because Surefire 3.x has no `junit-vintage-engine` on the classpath. The suite is much smaller than it appears.
- **`mvn test` is not idempotent.** A second run on a clean working tree fails with Spring `UnsatisfiedDependencyException`s because `RaplaSpringBootApplicationTest` writes to the default `data/data.xml` and corrupts shared state for subsequent tests.

## Why now (and the multi-module sequencing)

PRD 004 recommends a 5-module split, with the pom-property/dependencyManagement/plugin config moving from `parent/pom.xml` into a new `rapla-bom`. Most surefire/compiler/resources tweaks land cleanly in that BOM and are easier to verify per-module (`mvn -pl rapla-server test`). Doing them before the split means re-doing them after. **So the bulk of this PRD waits for PRD 005 (multi-module split) to land.**

Two items are exceptions and should ship **before** PRD 005:

1. **Add `junit-vintage-engine`.** The cycle audit in PRD 004 Risk 1 needs the test suite to actually exercise the code it touches. Restructuring modules with 60 % of the tests silently muted is dangerous — refactors that break `AppointmentTest`, `IDGeneratorTest`, `TestEntityHistory`, etc. would go unnoticed.
2. **Fix `mvn test` non-idempotence.** Same reason: if the suite can't be re-run on the same tree, every multi-module migration step that requires "did the tests still pass after I moved this package?" is unreliable.

Everything else waits.

## Scope

| File | Change | Phase |
|---|---|---|
| `parent/pom.xml` | Add `junit-vintage-engine` test dep | **Done in Phase 0 #4** |
| `parent/pom.xml` | Upgrade `maven-resources-plugin` 3.0.2 → 3.3.1 | **Done in Phase 0 #3** |
| `src/main/resources-filtered/org/rapla/RaplaSystemInfo_{cs,de,es,fr,nl,pl}.properties` | Normalise to UTF-8 (orphan-byte cleanup + ISO-8859-1 transcode) | **Done in Phase 0 #3** |
| `parent/pom.xml` | `<useIncrementalCompilation>false</useIncrementalCompilation>` | **Done in Phase 0 #1** |
| `parent/pom.xml` | Remove the second `resources:resources` execution bound to the `compile` phase | **Done in Phase 0 #2** |
| `src/test/java/org/rapla/server/spring/RaplaSpringBootApplicationTest.java` | Add `@TempDir` + `@DynamicPropertySource` so it doesn't share `data/data.xml` with the world | Pre-split (Phase 1) |
| `parent/pom.xml` → `rapla-bom/pom.xml` | `<forkCount>1</forkCount>` + `<reuseForks>true</reuseForks>` for surefire | Post-split |
| (new) `src/test/java/org/rapla/server/spring/SpringIntegrationTestBase.java` | Shared `@TempDir` + `@DynamicPropertySource` base class so `ServerServiceIntegrationTest`, `AuthControllerIntegrationTest`, `UrlPreservationTest` reuse one cached Spring context | Post-split |
| Root `pom.xml` (now an aggregator) | Document `mvn -T 1C` as the recommended invocation; keep default serial for CI clarity | Post-split |

## Plan

### Phase 0 — Quick wins, shipped 2026-05-07

All in `parent/pom.xml` unless noted. Don't depend on the multi-module split.

1. **`<useIncrementalCompilation>false</useIncrementalCompilation>`** on `maven-compiler-plugin` 3.13.0. With this plugin, `true` is the over-conservative *recompile-everything-when-anything-changes* mode; `false` does proper per-file mtime-based incremental compilation. This single change is what dropped the no-op compile from 33 s to 7 s.

2. **Removed the duplicate `resources:resources` execution bound to the `compile` phase.** The default lifecycle already binds it to `process-resources`; the second execution copied all 911 resources a second time on every build.

3. **Upgraded `maven-resources-plugin` 3.0.2 → 3.3.1** (latest in 3.x line). Faster on no-op runs and emits clearer log lines. The upgrade exposed pre-existing encoding rot in 6 files under `src/main/resources-filtered/org/rapla/RaplaSystemInfo_*.properties` (mostly UTF-8 with stray orphan `0xc2` bytes between words like "SANS GARANTIE" — a half-encoded UTF-8 NBSP — plus the German file in pure ISO-8859-1). Files were normalised in-place to clean UTF-8: orphan `0xc2` bytes completed to `0xc2 0xa0` (proper NBSP), German file transcoded `iso-8859-1` → `utf-8`. Verified: every file now `file`-reports as "Unicode text, UTF-8 text" and renders correct diacritics (`Für`, `Página`, `ŻADNEJ`, `ŽÁDNÁ`).

4. **Added `org.junit.vintage:junit-vintage-engine`** (test scope; version managed by the Spring Boot BOM at 5.10.2). Surefire 3.x only discovers JUnit Platform engines, so without this dep all JUnit 4 tests (`@RunWith(JUnit4.class)` and `extends TestCase`) were silently ignored. Test count: **39 → 94**. One newly-activated test fails (`MailTest.testMailSend` — `NoClassDefFoundError: javax.activation.DataSource` from `MailapiClient.java:285`); this is a real pre-existing bug in the source code that Java 11+ exposed when it removed `javax.activation`. Tracked separately, not in scope here.

Verified: `mvn package -DskipTests` still produces `target/rapla-2.1-SNAPSHOT/{META-INF,WEB-INF,webclient}` and a 2647-file fat jar identical in shape to before.

Measurements (3 consecutive runs, median):

| | Before any change | After Phase 0 |
|---|---|---|
| `mvn clean compile` (cold) | 47 s | **21 s** |
| `mvn compile` (no-op) | 33 s | **6.5 s** |
| `mvn test` (full) | 1 m 22 s @ 39 tests | **1 m 9 s @ 94 tests** |

The remaining ~6.5 s on no-op compile is dominated by Maven JVM startup (~3 s) and the resources plugin re-stamping 921 files (no built-in incremental detection in 3.x without an IDE BuildContext). Going lower would need `mvnd` — out of scope.

### Phase 1 — Pre-split correctness

1. ~~Add `junit-vintage-engine`~~ — **done in Phase 0 #4.** Test count jumped 39 → 94. One real bug surfaced (`MailTest.testMailSend`, `javax.activation.DataSource` removed in Java 11+) — track separately.

2. **Fix the `data/data.xml` mutation.** Still pending. `RaplaSpringBootApplicationTest` (and a few others not using `@DynamicPropertySource`) write to the default `data/data.xml` and corrupt it for subsequent runs. Mirror the pattern already used in `ServerServiceIntegrationTest`: `@TempDir static Path tempDir` + `@BeforeAll` copy + `@DynamicPropertySource` registering `rapla.file-datasources.raplafile`. After this, `mvn test && mvn test` should both pass green on a clean checkout.

3. **Triage the `MailTest` failure.** Either add `jakarta.activation:jakarta.activation-api` (or upgrade the `javax.mail` dep that brings it in transitively), or rewrite `MailapiClient.sendWithReflection` to use the `jakarta.mail` API. Outside the scope of this PRD; just track that vintage-engine surfaced it.

### Phase 2 — Post-split optimisations (after PRD 005 lands)

Apply each change *individually*, re-measure against the Phase 1 baseline, commit one at a time so a regression can be bisected.

4. ~~Compiler incremental fix~~ — **done in Phase 0.** Carry the `<useIncrementalCompilation>false</useIncrementalCompilation>` setting forward when `parent/pom.xml` becomes `rapla-bom/pom.xml`.

5. ~~Drop the duplicate resources binding~~ — **done in Phase 0.** No carry-forward needed; the binding is gone.

6. **Re-enable surefire forking.** Set `<forkCount>1</forkCount>` + `<reuseForks>true</reuseForks>`. This:
   - restores JVM isolation between Maven and the test code (the main reason the comment in today's pom says "temporary")
   - is a prerequisite for `<parallel>classes</parallel>` later
   - matters more once the suite is split per-module — a `mvn -pl rapla-server test` shouldn't pollute the IDE's Maven daemon JVM

7. **Share Spring contexts.** Extract a `SpringIntegrationTestBase` (or a JUnit 5 `@TestConfiguration` annotated meta-annotation) so the three integration tests with per-class `@TempDir` + `@DynamicPropertySource` share one cached context. Verify via Spring's `ContextCache` log (`-Dlogging.level.org.springframework.test.context.cache=DEBUG`) that the cache size is 1, not 3, after a full `mvn -pl rapla-server test`. Expected: ~5 s saved per CI run.

8. **Parallel reactor.** Document `mvn -T 1C` as the recommended developer invocation. Don't enable in CI by default until Phase 2 #4–#7 are stable; parallel + corrupted incremental state is the worst combination to debug.

### Phase 3 — Optional, defer until measured (only if Phase 2 doesn't get us under target)

9. Surefire test parallelism (`<parallel>classes</parallel>`, `<threadCount>2</threadCount>`). Risky: many of the JUnit 4 tests that step 1 unblocks were written assuming serial execution and shared static state. Don't turn this on without an `archunit` / static-state audit of the JUnit 4 suite.
10. Maven build cache (the `maven-build-cache-extension`). Worth ~30–50 % on a multi-module reactor with stable inputs, but adds a config surface that's another thing to maintain. Re-evaluate after a quarter on Phase 2.
11. Upgrade `maven-compiler-plugin` to 3.14+ and use the new `<incrementalCompilation>` parameter (which deprecates `useIncrementalCompilation`). Pure cleanup once the rest is stable.

## Dev-loop monitoring patterns (Phase 3 — added 2026-05-07)

Documented as part of PRD 007 because they directly cut the wait time inside the inner dev loop, even when the build itself doesn't get faster.

### Wait-for-condition pattern (replaces `sleep N` heuristics)

When an AI agent (or developer) starts a long-running process and needs to know when it's "ready", the naive pattern is `sleep 15` followed by a status check. This is bad in two directions: too long (still waits the full 15 s when ready in 8 s) and too short (15 s isn't enough when startup is degraded). The replacement is a `timeout` + `until grep` loop with a 0.3 s poll interval:

```bash
timeout 60 sh -c 'until grep -q "Started.*in [0-9.]\+ seconds" logs/rapla.log; do sleep 0.3; done' \
  && echo "READY" || echo "TIMEOUT"
```

Properties:
- **Returns within `~startup_time + 0.15 s`** (median half-poll-interval) once the line appears. For a 10 s Spring Boot startup that's ~10.15 s, vs `sleep 15` blocking the full 15 s every time.
- **Hard upper bound** at the `timeout` value (60 s here). Distinguishes "startup hung" from "startup completed" with a clear TIMEOUT signal — `sleep 15 && curl` returns "not ready" with no diagnostic and the agent has to retry-loop manually.
- **Foreground Bash call** — short enough to not stall the agent (the timeout caps it), no `run_in_background` needed.
- **Polling load** is negligible — 0.3 s sleeps with a single `grep -q` call (returns immediately on first match).

Measured on rapla startup (10.255 s warm-JVM Spring Boot context):

| Pattern | Detection latency over the 10.255 s startup |
|---|---|
| `sleep 15` | always 15 s — never less, never more |
| `timeout 60 sh -c 'until grep -q ... ; do sleep 0.3; done'` | ~10.4 s on the first run, **15 ms when re-checked after server is already up** |

### Stream-events pattern for long-lived watching

When the agent needs to react to multiple log events over the server's lifetime (not just startup), use `tail -F` (capital F — survives log rotation) in `run_in_background=true`:

```bash
# Bash tool with run_in_background=true — returns a shell ID immediately:
tail -F logs/rapla.log
```

Then attach `Monitor` against that shell ID. Each new log line becomes a stream event the agent reacts to without polling. Use case: wait for a 2nd event ("Reservation X saved") after seeing the 1st ("Login by user Y"), or react to ERRORs as they happen during a load test.

### Why not `tail -f | grep …` in foreground

Open-ended: the agent's Bash tool blocks until the pipe closes, which only happens when `tail` is killed. The two patterns above (timeout + until / Monitor) both have explicit termination conditions and are agent-safe.

### Documented in AGENTS.md §8

The full server lifecycle (start, stop, status, log-inspect) using these patterns is in **AGENTS.md §8**. PRD 007 is the architectural rationale; AGENTS.md is the operational recipe.

## Tests

This PRD changes build/test infrastructure, so "tests" here means **test-suite invariants** that prove the changes work, not new feature tests.

| Phase | Verification |
|---|---|
| 1.1 | `mvn test` reports ≥ 100 tests run (vs 39 today). Surefire output includes `org.rapla.entities.tests.AppointmentTest`, `org.rapla.components.util.DateToolsTest`, `org.rapla.storage.impl.server.TestEntityHistory`, `org.rapla.rest.server.SignedTokenTest` (these four are a fixed sentinel list — if any goes missing, vintage isn't picking them up). |
| 1.2 | `mvn test && mvn test` both succeed on a clean checkout. `git status data/` reports no changes after either run. |
| 0 (done) | Two consecutive `mvn compile`: the second runs in ~7 s and the compiler step prints nothing (no `Compiling N source files`). `mvn clean compile` log shows `Copying 911 resources` once, not twice. `mvn package -DskipTests` still produces `target/rapla-2.1-SNAPSHOT/` with `META-INF`, `WEB-INF`, `webclient`. |
| 2.6 | `mvn test` produces one surefire fork per JVM (visible in process list during run). All 100+ tests still pass. |
| 2.7 | `mvn -pl rapla-server test -Dlogging.level.org.springframework.test.context.cache=DEBUG` logs `Spring test ApplicationContext cache statistics: [size = 1, …]`. Total Spring-test wall time drops by ≥ 4 s vs Phase 1 baseline. |

No new functional tests are required for any phase; existing tests are the verification.

## Risks

1. **JUnit 4 tests have rotted.** Some of the 19 silently-skipped classes likely fail when finally exercised — code they tested may have changed underneath them. Phase 1.1 explicitly carves out time to triage; the PRD considers "X failures, Y skipped, Z fixed" an acceptable Phase 1.1 outcome as long as the failing ones are tracked.

2. **`forkCount=1` exposes a missing dependency.** The pom comment that disabled forking says "this creates a classpath where dependencies seems to be missing." That was Surefire ~2.x behaviour with manifest-jar classpaths; 3.x uses the long-classpath argfile by default and shouldn't recur. If it does, set `<useManifestOnlyJar>false</useManifestOnlyJar>`.

3. **Phase 2 lands on top of PRD 005's pom restructure.** Merge conflict surface is small (different files), but ordering matters: PRD 005 moves the configs into `rapla-bom/pom.xml` first, then this PRD edits them there. Don't start Phase 2 until PRD 005 has merged.

4. **Spring context sharing changes test isolation guarantees.** If two integration tests pass with separate contexts but fail when sharing, the failure exposes a real ordering bug — but it's still a bug *this PRD created the conditions for*. Phase 2.7 should be reverted (not patched in flight) if it surfaces flakiness; investigate separately.

## Open Questions

1. **Is upgrading to JUnit 5 across the board preferable to vintage-engine long-term?** Vintage works but means two engines, two assertion styles, two `@Before*` annotations forever. A migration script (e.g. `org.junit:junit-migration` / IntelliJ's converter) could move the 19 classes in an afternoon. **Decide:** before Phase 1.1, or accept vintage indefinitely.

2. **Should Phase 1 ship as one PR or two?** Adding vintage and fixing data.xml are independent; reviewing one diff that does both is cleaner *if* no JUnit 4 tests fail under vintage. Decide after running step 1.1 once.

3. **Does CI need `-T 1C` or only developers?** Parallel reactor builds change failure logs (interleaved). For a small team, the developer-side latency win outweighs the log-readability cost; CI runtime is not currently the bottleneck. **Recommendation:** developers via `mvn -T 1C` (document in `AGENTS.md`); CI stays serial for log clarity.

4. **`maven-build-cache-extension` (Phase 3 #10) — adopt or skip?** It's powerful but adds a `.mvn/maven-build-cache-config.xml` to maintain. For a single-author repo with ~5-module reactor, the cache hit rate may not justify the surface area. Defer; revisit after one quarter of post-Phase-2 measurements.

5. **What's the actual cause of "Recompiling the module because of changed source code"?** The hypothesis is the duplicate resources binding (Phase 2 #5) touching `target/classes/` mtimes, plus the `useIncrementalCompilation=true` over-conservatism (Phase 2 #4). If both fixes don't yield the < 3 s no-op compile, the deeper cause needs investigation (possibly resources-plugin filter timestamps on resources-filtered/, or annotation-processor output detection).

## Dependencies on Other PRDs

| PRD | Relationship |
|---|---|
| **004: Multi-Module Architecture Analysis** | Informs Phase 2 — settings move into `rapla-bom`. |
| **005: Multi-Module Split** (planned, not written) | **Hard prerequisite for Phase 2.** Phase 1 is independent and can ship first. |
| **001: Spring Boot Migration** | The Spring context-cache work in Phase 2.7 only matters because PRD 001 added Spring to the test classpath. Stable enough to assume done for this PRD's purposes. |
