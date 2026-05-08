# PRD 005 — Baseline (pre-split)

**Captured:** 2026-05-07
**Branch:** `spring-boot`
**Head commit:** `fb64da61` ("add AGENTS.md, CLAUDE.md and .claude -> .agents symlink for AI agent config")
**Working tree state:** dirty — 548 uncommitted modifications (PRD 001 Phase 4 follow-up in progress, deliberately not committed). Baseline numbers below reflect the build with those uncommitted changes in place.

## Build & test results

| Metric | Value |
|---|---|
| `mvn test` | **BUILD SUCCESS** — 94 tests run, 0 failures, 0 errors, **2 skipped** |
| `mvn test` wall time | ~1m05s |
| `mvn package -DskipTests` | BUILD SUCCESS, 14 s |

**Test count delta vs PRD 001 snapshot:** PRD 001 last recorded 23 tests across 7 Spring contexts; baseline now shows 94 tests. ~71 tests were added between PRD 001's last snapshot (2026-05-06) and PRD 005 baseline (2026-05-07).

## Build artefacts

| Artefact | Size |
|---|---|
| `target/rapla-2.1-SNAPSHOT.jar` (monolith) | **4.7 MB** |
| `target/raplabootstrap-tests.jar` | 62 KB |
| `target/distribution/rapla-2.1-SNAPSHOT.tar.gz` | 3.5 MB |
| `target/distribution/rapla-2.1-SNAPSHOT.zip` | 3.5 MB |
| `target/webclient/` (JNLP staging) | not produced by default `mvn package` (gated by profile) |

## Source surface

| Tree | `.java` count |
|---|---|
| `src/main/java/` | **976** |
| `src/test/java/` | 55 |

## Build warnings (informational)

`maven-assembly-plugin` reported two never-triggered exclusion patterns in `src/assembly/rapla.distribution.xml`:
- `*jetty*` — Jetty was removed in PRD 001 Phase 1.8; the exclusion is dead. Will be cleaned up incidentally during Phase D4 when the assembly descriptor moves to `rapla-app/`.
- `javax.servlet:javax.servlet-api*` — also dead since the jakarta.servlet migration.

Not split-blocking. Recorded so they don't surprise anyone reviewing the post-split build.

## Post-split regression checks (use this snapshot as the comparand)

After Phase D6 finishes:

| Comparand | Expectation |
|---|---|
| `mvn test` from reactor root | ≥ 94 tests pass, 0 failures (the 2 skipped should remain skipped for the same reason; investigate if either skip turns into a failure). |
| `mvn -pl rapla-app package` | Produces `rapla-app/target/rapla-2.1-SNAPSHOT.jar` (preserved via `<finalName>` per OQ2). Size should be ≈ 4.7 MB ± 5 % — Spring Boot repackaging may add a few hundred KB of launcher classes; per-module JAR layout shouldn't materially change uncompressed size. |
| Per-module JAR sum (`rapla-core` + `rapla-client` + `rapla-server`) | Should be **smaller** than the monolith because each module strips dependencies it doesn't import. The win is per-consumer (client-only, server-only); the sum number is mostly diagnostic. |
| Distribution archives | tar.gz/zip should remain ~3.5 MB; `<distribution>` assembly descriptor relocates to `rapla-app/src/assembly/` but the archive content shape is preserved. |
