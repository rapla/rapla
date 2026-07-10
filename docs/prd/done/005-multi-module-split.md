# PRD 005: Multi-Module Split (Implementation)

**Status:** done — Phases A, B, C, D, E, F all complete as of 2026-05-08. Phase G (`dhbwrapla` update) is **explicitly deferred with custom/** and owned by a future PRD aligned with whatever replaces custom/. Phase E close-out: signing profiles already in rapla-app (E.4 done earlier); iCal4j cleanup done (E.5); per-module `dependency:analyze` ran 2026-05-08 — actioned 2 used-undeclared deps in rapla-core (`jackson-annotations`, `spring-beans`); BOM-level `maven-jar-plugin` configuration added with manifest entries (`Implementation-Title`, `Implementation-Version`, `Implementation-Vendor`, `Built-By`, `Build-Jdk`) so all module jars get a sensible `MANIFEST.MF` (E.3). Other dep:analyze warnings are Spring Boot starter-pack false positives; not actionable.
**Date:** 2026-05-07
**Decision input:** [PRD 004](004-multi-module-architecture-analysis.md) (target architecture)
**Hard prerequisite:** [PRD 001](001-spring-boot-migration.md) Phases 1–8 — **already complete on `spring-boot` branch**

## Implementation status (2026-05-07)

Reactor aggregates 5 modules. `mvn -f master/pom.xml clean test` → **94 pass / 0 fail / 2 skipped** (matches pre-split baseline). `rapla-app` produces fat JAR at `rapla-app/target/rapla-2.1-SNAPSHOT.jar` (filename preserved per OQ2).

Source distribution after Phase D6: `rapla-core` 380 files; `rapla-client` 440 files; `rapla-server` 156 files; `rapla-app` ~3 files. Tests all in `rapla-app/src/test/` (D5 pragmatic placement; per-module redistribution is Phase E follow-up).

D3 compromise resolved 2026-05-07: 27 toolkit-agnostic files moved from rapla-client to rapla-core eliminated the original rapla-server → rapla-client edge. **`rapla-server` now depends only on `rapla-core`.** Detail in `005-cycle-audit.md` §0. The `rapla-client-api` extraction is **permanently off the table** per [PRD 003](../003-custom-deployments-after-spring-migration.md) direction change 2026-05-07 — dhbwrapla becomes server-only.

## Goal

Turn the existing single-module Maven build into a **5-module reactor** along [PRD 004](004-multi-module-architecture-analysis.md)'s layer boundaries, while keeping `mvn test` green at every step. The split delivers:

- **Build-time enforcement** of the package-level layering that already exists by convention
- **Smaller dependency surface** for downstream consumers (`dhbwrapla`, future custom deployments) — pull `rapla-server` only, not the whole monolith
- **A clean home** for the future Angular SPA (PRD 006, not here)
- **Maven Central publishability** for `rapla-core`, `rapla-client`, `rapla-server`

Out of scope: Angular code (PRD 006); splitting rapla-client into api+swing ([PRD 004](004-multi-module-architecture-analysis.md) defers); per-plugin modules ([PRD 004](004-multi-module-architecture-analysis.md) Risk 2); Swing UI legacy-DI migration (PRD 001 Phase 4 follow-up); `dhbwrapla` update (separate PR/PRD).

## Scope

**Files/packages affected (rapla repo only):**

```
pom.xml                                  → splits into aggregator + per-module poms
parent/pom.xml                           → renamed to rapla-bom/pom.xml; adjusted to packaging=pom + dependencyManagement only
master/pom.xml                           → deleted; replaced by root aggregator
custom/pom.xml                           → deleted (PRD 003 supersedes via direct Maven coords + optional archetype)
src/main/java/org/rapla/{core packages}  → moved to rapla-core/src/main/java/...
src/main/java/org/rapla/{client packages}→ moved to rapla-client/src/main/java/...
src/main/java/org/rapla/{server packages}→ moved to rapla-server/src/main/java/...
src/main/java/org/rapla/RaplaSpringBootApplication... → moved to rapla-app/src/main/java/...
src/assembly/rapla.distribution.xml      → moved to rapla-app/src/assembly/
src/main/resources/application.yml       → moved to rapla-app/src/main/resources/
src/main/resources/static/**             → moved to rapla-server/src/main/resources/static/ (legacy jQuery + JNLP)
src/test/java/**                         → split per module by package
.gitignore                               → add per-module target/ entries
AGENTS.md, CLAUDE.md                     → update build commands for reactor (`mvn -pl rapla-server test`, etc.)
docs/prd/004-...                         → status note: superseded-by-005 for the implementation question
```

**Source-of-truth file count today**: **976 files** + tests under `src/test/java`.

## Decisions ratified from [PRD 004](004-multi-module-architecture-analysis.md)

| Axis | Choice | Reference |
|---|---|---|
| **Module count** | 5 (`rapla-bom`, `rapla-core`, `rapla-client`, `rapla-server`, `rapla-app`) — no per-plugin modules, no `rapla-client-api/swing` split | [PRD 004](004-multi-module-architecture-analysis.md) §A2, §Risk 2 |
| **Build tool** | Maven (no Gradle migration in this PRD) | [PRD 004](004-multi-module-architecture-analysis.md) §B verdict |
| **Plugin layout** | Plugins remain as packages inside the three main modules; `plugin/<name>/{client,server,extensionpoints}/` distributes naturally | [PRD 004](004-multi-module-architecture-analysis.md) §Risk 2 (option a) |
| **`components.*` placement** | Split: `i18n/{,client/}`, `util`, `layout`, `restproxy` → `rapla-core`; `calendar`, `calendarview`, `iolayer`, `tablesorter`, `treetable`, `i18n/client/swing/` → `rapla-client` | [PRD 004](004-multi-module-architecture-analysis.md) §OQ2 recommendation |
| **`custom/` POM** | **Defer.** Per user direction 2026-05-07: dropped from reactor but directory stays on disk for reference. dhbwrapla integration in Phase G handles absence of `org.rapla:custom` separately. | User direction; supersedes earlier "delete" plan |
| **`rapla-archetype`** | **Not** created in this PRD | [PRD 004](004-multi-module-architecture-analysis.md) §OQ6 |

## Cycle audit (verified 2026-05-07, before any code change)

Grep-based scan of the **proposed module boundaries**:

| Boundary | Imports today | Action |
|---|---|---|
| `server.*` → `client.*` | **2** (both interfaces — `ClientService`, `RaplaClientListenerAdapter` — in `server/internal/console/GUIStarter.java`) | Move both interfaces to `rapla-core` |
| `client.*` → `server.*` | **0** | none |
| `facade.client.*` → `server.*` | **0** | none |
| `facade.server.*` → `client.*` | **0** | none |
| `entities.*` → `client.*` / `server.*` / `javax.swing` | **0** | none |
| `facade.*` → `javax.swing` | **0** | none |
| `rest.*` → `javax.swing` | **0** | none |
| `components/i18n/*` → `javax.swing` | **2 files**, both in `client/swing/` subpackage (`SwingBundleManager`, `SwingIcon`) | Move that subpackage to `rapla-client`; rest of `i18n` → `rapla-core` |

**Conclusion:** "<50 cycles → 1–2 weeks" scenario from [PRD 004](004-multi-module-architecture-analysis.md) Risk 1. Migration is dominated by mechanical `git mv` + pom-writing, not dependency surgery.

## Plan

Eight phases. Each ends green; full `mvn test` at session end per AGENTS.md §5.

- **Phase A — Prerequisites & branch hygiene (0.5d)**: confirm `spring-boot` branch with PRD 001 done; capture baseline `mvn test` + monolith JAR size; worktree per AGENTS.md §7.
- **Phase B — Prep cleanup in single module (1–2d)**: move `ClientService` + `RaplaClientListenerAdapter` to `org.rapla.client.api`; relocate `i18n/client/swing/SwingBundleManager.java` + `SwingIcon.java` to `client/swing/i18n/`; run `jdeps -dotoutput out/jdeps -e 'org.rapla.*' target/classes` cycle audit (stop if >50 cycles); confirm `client/spring/{ClientConfig,ClientProxyConfig,SpringRaplaClient,SwingClientConfig}.java` will land in `rapla-client`; per OQ1 no test-jar published.
- **Phase C — Reactor skeleton (1d)**: rename `parent/` → `rapla-bom/` (packaging=pom, dependencyManagement only); create root aggregator with 5 modules + a transitional `<module>.</module>` escape hatch (deleted at end of D); create empty `rapla-core/`, `rapla-client/`, `rapla-server/`, `rapla-app/` poms (parent=rapla-bom, packaging=jar); delete `master/pom.xml` and `custom/pom.xml`.
- **Phase D — Source migration, package by package (4–6d)**. Each step is a `git mv` followed by a compile; leaves first.
  - **D1 (`rapla-core`, ~330 files):** entities → framework → logger/inject/scheduler → rest (DTOs + interfaces) → components/{util,layout,restproxy,i18n} → enpoints (keep typo) → facade → storage core. Deps: SLF4J, Jackson, jakarta.inject API, jakarta.ws.rs API, RxJava3, iCal4j (verify, may belong in server).
  - **D2 (`rapla-client`, ~520 files):** client/** + client/spring/** + components/{calendar,calendarview,iolayer,tablesorter,treetable} + plugin/*/client + plugin/*/extensionpoints. Plugin-shared classes (`<Name>Plugin.java`, `<Name>Resources.java`) keep in `rapla-core` (facade-level descriptors).
  - **D3 (`rapla-server`, ~80 files):** server/** (incl. server/spring/**) + storage/{dbsql,dbrm} + plugin/*/server + plugin/*/internal + rest/server + application.yml + static/** + META-INF/spring AutoConfig imports.
  - **D4 (`rapla-app`, ~3 files):** RaplaSpringBootApplication + src/assembly/rapla.distribution.xml + application.yml + spring-boot-maven-plugin + JNLP staging via maven-dependency-plugin:copy-dependencies.
  - **D5 (tests):** move alongside the package they test; resolve shared fixtures per Phase B.
  - **D6:** delete `<module>.</module>` from root pom; move/delete root `src/` stragglers.
- **Phase E — Build hygiene & per-module deps (1–2d)**: audit BOM (third-party pins only); `mvn dependency:analyze -pl <module>`; manifest entries per module; rename `rapla-app/pom.xml` to `<artifactId>rapla-app</artifactId>` + `<finalName>rapla-2.1-SNAPSHOT</finalName>` (preserves on-disk JAR filename); move signing profiles from `parent/pom.xml` to `rapla-app/pom.xml`.
- **Phase F — Documentation (0.5d)**: AGENTS.md build commands + §7 worktree notes; module-map table in README or `docs/architecture.md`; [PRD 003](../003-custom-deployments-after-spring-migration.md) supersedes notes; [PRD 004](004-multi-module-architecture-analysis.md) status flip.
- **Phase G — `dhbwrapla` update — DEFERRED with custom/**. Originally to update dhbwrapla to depend on `rapla-server` directly. Per user direction 2026-05-07, deferred to a future PRD. dhbwrapla build does NOT need to keep working through PRD 005's lifetime. When done: replace `<parent>org.rapla:custom</parent>` with `rapla-bom`; replace `<dependency>org.rapla:rapla</dependency>` with `rapla-server` (+ `rapla-client` if Swing customisations); verify `dhbwrapla-container/pom.xml` resolves `../../rapla`.
- **Phase H — Maven Central readiness (out of scope; tracked here)**: source + Javadoc JARs per module; `nexus-staging-maven-plugin`; GPG signing in CI; POM metadata; per-module license tagging.

## Tests

Tests are written **before** each phase's implementation per AGENTS.md §1; for a refactor, most "tests" are existing tests that must stay green.

| Phase | Test artefact | When written |
|---|---|---|
| A | Baseline: `mvn test` results + JAR size of monolith. **Recorded as `005-baseline.md` snapshot.** | Before any change |
| B (interface moves) | Existing `mvn test` suite stays green. | Each `git mv` |
| B (i18n swing move) | `RaplaSpringBootApplicationTest` (i18n init via `RaplaResources`) must still pass. | After move |
| C (skeleton) | `mvn -pl rapla-bom install` exits 0; `mvn compile` from root still produces `target/classes/`. | After skeleton |
| D1 (rapla-core) | NEW: `CoreModuleFitnessTest` — ArchUnit asserts `org.rapla.{client,server}.*` never imported from `rapla-core`. | Before D1 |
| D2 (rapla-client) | NEW: `ClientModuleFitnessTest` — ArchUnit asserts `org.rapla.server.*` never imported from `rapla-client` (Spring autoconfig packages excepted). | Before D2 |
| D3 (rapla-server) | NEW: `ServerModuleFitnessTest` — ArchUnit asserts `javax.swing.*` never imported from `rapla-server`. | Before D3 |
| D4 (rapla-app) | NEW: `AppPackagingTest` — verifies fat JAR contains expected entries (`BOOT-INF/classes/RaplaSpringBootApplication.class`, `BOOT-INF/lib/rapla-{server,client,core}-*.jar`). | Before D4 |
| D5 (tests moved) | All 23 PRD-001 tests still pass. | Continuous |
| D6 (remove `.`) | Full reactor `mvn clean install` from root; compare to Phase A baseline. | After D6 |
| E | `mvn dependency:analyze` clean per module. | After E |
| F | None — docs only. | — |

**ArchUnit dependency:** `com.tngtech.archunit:archunit-junit5:1.3.0` as test-scope in `rapla-bom`. ~3 MB; well-established.

## Open Questions

1. **Test fixture sharing — DECIDED: no test-jar.** Post-PRD 001 §1.7 the cross-module fixture surface is gone. Each module owns `src/test/java/`; cross-cutting helpers duplicated. Lift to `rapla-core` + `<goal>test-jar</goal>` if a third user appears.

2. **App artifact rename — DECIDED: rename to `rapla-app`, preserve on-disk filename.** Final coordinate map:

   | Coordinate | Packaging | Purpose | On-disk filename |
   |---|---|---|---|
   | `org.rapla:rapla:2.1-SNAPSHOT` | `pom` | Root reactor aggregator | — |
   | `org.rapla:rapla-bom:2.1-SNAPSHOT` | `pom` | BOM | — |
   | `org.rapla:rapla-core:2.1-SNAPSHOT` | `jar` | Shared | `rapla-core-2.1-SNAPSHOT.jar` |
   | `org.rapla:rapla-client:2.1-SNAPSHOT` | `jar` | Swing client | `rapla-client-2.1-SNAPSHOT.jar` |
   | `org.rapla:rapla-server:2.1-SNAPSHOT` | `jar` | Spring Boot server | `rapla-server-2.1-SNAPSHOT.jar` |
   | `org.rapla:rapla-app:2.1-SNAPSHOT` | `jar` (repackaged) | Runnable fat JAR | **`rapla-2.1-SNAPSHOT.jar`** (via `<finalName>`) |

   Reusing `rapla` for both root aggregator and runnable JAR creates `org.rapla:rapla` collision; splitting kills the ambiguity. dhbwrapla depends directly on `rapla-server` (+ `rapla-client`) with versions via `rapla-bom`.

3. **`enpoints` typo** — kept in this PRD; rename to `endpoints` after as separate two-step rename.

4. **`iCal4j` placement — DECIDED: server-only.** Verified 2026-05-07: zero `net.fortuna.ical4j` imports in `entities.*` or `facade.*`. All 6 importing files are under `server.*` or `plugin.*.server.*`. Phase E declares iCal4j only in `rapla-server/pom.xml`, demotes BOM entry to `<dependencyManagement>`. Net: `rapla-client` drops ~5 MB.

5. **JNLP `webclient/` content.** Rewrite `rapla-app/pom.xml`'s `maven-dependency-plugin:copy-dependencies` to use `<includeArtifactIds>rapla-core,rapla-client,...</includeArtifactIds>` instead of an exclusion list. Verify JNLP set before Phase F docs.

6. **Spring Boot AutoConfiguration imports — DECIDED: server-only autoconfig; client stays explicit.** `META-INF/spring/...AutoConfiguration.imports` moves to `rapla-server/`. **The client deliberately does NOT use autoconfig:** (1) JNLP delivery loads a flat list of signed JARs and doesn't run `JarLauncher`; (2) Spring Boot autoconfigs are server-shaped (embedded web server, datasource, security, Actuator); (3) deterministic plugin wiring beats classpath scanning for multi-customer deployments — autoconfig would silently inject Swing beans from every JNLP-codebase JAR; (4) `new AnnotationConfigApplicationContext(ClientConfig.class)` boots ~10× faster than `SpringApplication.run`; (5) `ClientConfigTest` is plain JUnit 5, sub-second. Post-split `rapla-client/pom.xml` depends on `spring-context` only, NOT `spring-boot-starter-*`.

7. **Phase 4 ordering — DECIDED: split first, live Swing-DI conversion second.** Split is bounded (~976 files, ~3 cross-boundary edges, ~1.5 weeks); Swing-DI conversion is unbounded — AGENTS.md §4's mandate makes it happen organically. After the split every Swing-DI commit is `mvn -pl rapla-client test` — fast, contained. Mixed-injection `client/swing/` files move as-is.

8. **CI matrix.** No CI config exists today. Per-module job matrix vs full `mvn install` — **defer**, its own PRD.

## Risks & mitigations

| Risk | Mitigation |
|---|---|
| Cycle audit (Phase B step 3) reveals more cycles than grep caught | Phase B timeboxed; if >50 cycles, escalate before Phase C. 5-module split is a *target*. |
| Mid-split, parallel agent lands commit crossing what will become a module boundary | Worktree on `prd-005-multi-module` branch. Rebase frequently. Per-step compile-checks catch newly-introduced cycles within hours. |
| `dhbwrapla` build breaks before Phase G is merged | dhbwrapla resolves against the OLD `rapla` JAR until PRD 005 is tagged. Phase G is coordinated PR after PRD 005 ships. |
| JNLP signing pass fails because assembled `webclient/` changed | Phase E step 5 addresses signing-profile relocation. Test signing profile against sample build before Phase F. |
| The `<module>.</module>` transitional aggregator is forgotten | Phase D6 explicitly removes it. `grep -r "<module>\.</module>" .` at end of Phase D as checklist. |

## Effort estimate

Aggregate: **~1.5 weeks of focused work** for a single agent in a worktree.

| Phase | Work |
|---|---|
| A | 0.5 day |
| B | 1–2 days |
| C | 1 day |
| D | 4–6 days |
| E | 1–2 days |
| F | 0.5 day |
| G | 1 day (separate session) |

If cycle audit surfaces >50 cycles, Phase B grows to 3–5 days and total to ~3 weeks.

## Dependencies on other PRDs

| PRD | Relationship |
|---|---|
| **001** Spring Boot Migration | **Hard prerequisite** — Phases 1–8 done on `spring-boot` branch. Phase 4 follow-up runs independently in parallel. |
| **001-A** Date → LocalDateTime | Independent; touches `rapla-core` signatures. |
| **002** Multi-Tenancy | Independent; `TenantAwareFacade` lives in `rapla-server`. |
| **003** Custom Deployments | **Bidirectional.** PRD 005 simplifies [PRD 003](../003-custom-deployments-after-spring-migration.md)'s OQ2/OQ5/OQ6; update [PRD 003](../003-custom-deployments-after-spring-migration.md) in Phase F. |
| **004** Multi-Module Architecture Analysis | **Decision document.** PRD 005 implements it. |
| **006** Angular Client (future) | Depends on PRD 005 for clean home as peer of Java reactor. |
| **007** Build & Test Performance | Likely benefits from split but doesn't block. |
