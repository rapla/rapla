# PRD 005: Multi-Module Split (Implementation)

**Status:** done — Phases A, B, C, D, E, F all complete as of 2026-05-08. Phase G (`dhbwrapla` update) is **explicitly deferred with custom/** and owned by a future PRD aligned with whatever replaces custom/. Phase E close-out: signing profiles already in rapla-app (E.4 done earlier); iCal4j cleanup done (E.5); per-module `dependency:analyze` ran 2026-05-08 — actioned 2 used-undeclared deps in rapla-core (`jackson-annotations`, `spring-beans`); BOM-level `maven-jar-plugin` configuration added with manifest entries (`Implementation-Title`, `Implementation-Version`, `Implementation-Vendor`, `Built-By`, `Build-Jdk`) so all module jars get a sensible `MANIFEST.MF` (E.3). Other "Used undeclared" / "Unused declared" warnings from `dependency:analyze` are dominated by Spring Boot starter-pack false positives (dep:analyze can't see runtime usage of starter-meta-deps); not actionable.
**Date:** 2026-05-07
**Decision input:** [PRD 004](004-multi-module-architecture-analysis.md) (target architecture)
**Hard prerequisite:** [PRD 001](001-spring-boot-migration.md) Phases 1–8 (server-side Spring Boot migration, GWT removal, war/jetty cleanup) — **already complete on `spring-boot` branch**

## Implementation status (2026-05-07)

Reactor: `master/pom.xml` aggregates 5 modules (`rapla-bom`, `rapla-core`, `rapla-client`, `rapla-server`, `rapla-app`). `mvn -f master/pom.xml clean test` → BUILD SUCCESS, **94 tests pass / 0 failures / 2 skipped** (matches pre-split baseline). `rapla-app` produces a Spring Boot fat JAR at `rapla-app/target/rapla-2.1-SNAPSHOT.jar` (filename preserved per OQ2).

Source distribution after Phase D6:
- `rapla-core`: 380 .java files (entities, framework, facade, scheduler, storage interfaces, REST DTOs, components/{util,layout,restproxy,i18n}, shared plugin descriptors, `client/api/`)
- `rapla-client`: 440 .java files (`client/*` incl. `swing`, components/{calendar,calendarview,iolayer,tablesorter,treetable}, all plugin client/swing subpackages)
- `rapla-server`: 156 .java files (`server/*` minus the @SpringBootApplication entry point, JDBC + dbfile storage, plugin server-sides)
- `rapla-app`: ~3 .java files (RaplaSpringBootApplication, plus all the resources, distribution, assembly, signing config inherited from the old monolith pom — to be redistributed in Phase E follow-up)

Tests are currently all in `rapla-app/src/test/` (D5 pragmatic placement — rapla-app has all transitive deps). Per-module test redistribution is a Phase E follow-up that needs to weigh splitting vs the simplicity of all-in-app.

Compromise that turned out not to be needed (D3, resolved 2026-05-07): the initial split had `rapla-server` depending on `rapla-client` for HTML calendar rendering (`RaplaBuilder`/`RaplaBlock` family + `components.calendarview.html.*`). A small follow-up refactor — moving 27 toolkit-agnostic files from rapla-client to rapla-core — eliminated the edge entirely. **`rapla-server` now depends only on `rapla-core`.** Detail in `005-cycle-audit.md` §0.

The `rapla-client-api` extraction (further splitting rapla-client into a presenter-API tier and a Swing-impl tier) is **permanently off the table** per PRD 003 direction change 2026-05-07 — dhbwrapla becomes server-only, with its dhbw-specific Swing code living inside `rapla-client`, so no consumer ever needs a Swing-API-without-Swing-impl distribution.

## Goal

Turn the existing single-module Maven build into a **5-module reactor** along the layer boundaries PRD 004 settled on, while keeping `mvn test` green at every step. The split delivers:

- **Build-time enforcement** of the package-level layering that already exists by convention
- **Smaller dependency surface** for downstream consumers (`dhbwrapla`, future custom deployments) — pull `rapla-server` only, not the whole monolith
- **A clean home** for the future Angular SPA (`rapla-client-web/`, peer of `rapla-client-swing` packages — added in PRD 006, not here)
- **Maven Central publishability** for `rapla-core`, `rapla-client`, `rapla-server`

What this PRD does **not** do (out of scope, deferred):
- Add Angular code or `rapla-client-web/` (PRD 006)
- Split `rapla-client` into `rapla-client-api` + `rapla-client-swing` (PRD 004 says defer until a second Java client appears)
- Per-plugin modules (PRD 004 Risk 2 — defer)
- Migrate the live Swing UI graph from legacy DI to Spring (PRD 001 Phase 4 follow-up — independent track, can proceed in parallel)
- Update `dhbwrapla` (separate PR/PRD against `dhbwrapla` repo, blocked on this PRD landing)

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

**Source-of-truth file count today** (from `find src/main/java -name '*.java' | wc -l`): **976 files**. Plus tests under `src/test/java`.

## Decisions ratified from PRD 004

| Axis | Choice | Reference |
|---|---|---|
| **Module count** | 5 (`rapla-bom`, `rapla-core`, `rapla-client`, `rapla-server`, `rapla-app`) — no per-plugin modules, no `rapla-client-api/swing` split | PRD 004 §A2, §Risk 2 |
| **Build tool** | Maven (no Gradle migration in this PRD) | PRD 004 §B verdict |
| **Plugin layout** | Plugins remain as packages inside the three main modules; `plugin/<name>/{client,server,extensionpoints}/` distributes naturally | PRD 004 §Risk 2 (option a) |
| **`components.*` placement** | Split: `i18n/{,client/}`, `util`, `layout`, `restproxy` → `rapla-core`; `calendar`, `calendarview`, `iolayer`, `tablesorter`, `treetable`, `i18n/client/swing/` → `rapla-client` | PRD 004 §OQ2 recommendation |
| **`custom/` POM** | **Defer.** Per user direction 2026-05-07: custom/ is dropped from the reactor (not built, not part of any module list) but the directory and its pom.xml stay on disk for reference. Its WAR-overlay shape is being rethought; a future PRD will replace it. dhbwrapla integration in Phase G needs to handle the absence of `org.rapla:custom` separately. | User direction; supersedes earlier "delete" plan |
| **`rapla-archetype`** | **Not** created in this PRD (optional per PRD 004; PRD 003 OQ6 leans "documented template, not archetype") | PRD 004 §OQ6 |

## Cycle audit (verified 2026-05-07, before any code change)

Grep-based scan of the **proposed module boundaries** in the current source tree:

| Boundary | Imports today | Action |
|---|---|---|
| `server.*` → `client.*` | **2** (both interfaces — `ClientService`, `RaplaClientListenerAdapter` — both in `server/internal/console/GUIStarter.java`) | Move both interfaces to `rapla-core`; `GUIStarter` keeps importing them |
| `client.*` → `server.*` | **0** | none |
| `facade.client.*` → `server.*` | **0** | none |
| `facade.server.*` → `client.*` | **0** | none |
| `entities.*` → `client.*` / `server.*` / `javax.swing` | **0** | none |
| `facade.*` → `javax.swing` | **0** | none |
| `rest.*` → `javax.swing` | **0** | none |
| `components/i18n/*` → `javax.swing` | **2 files**, both already in a `client/swing/` subpackage (`SwingBundleManager`, `SwingIcon`) | Move that subpackage to `rapla-client`; rest of `i18n` → `rapla-core` |

**Conclusion:** the package convention has held up. This is the "<50 cycles → 1–2 weeks" scenario from PRD 004 Risk 1, not the "quarter" scenario. **A formal `jdeps -dotoutput` audit is still part of Phase B below**, but the headline number (cross-boundary edges that must be broken: ~3 files) is small enough that the migration is dominated by mechanical `git mv` + pom-writing, not dependency surgery.

## Plan

Eight phases. Each phase ends with a green build (`mvn compile` + targeted test class). A full `mvn test` runs at session end of each phase, not after every step (per AGENTS.md §5).

### Phase A — Confirm prerequisites & branch hygiene (0.5 day)

1. Verify on `spring-boot` branch — current state, PRD 001 Phase 1–8 complete.
2. Run `mvn test` once on the unmodified single-module build; record baseline (current count: 23 tests across 7 Spring contexts).
3. Capture the unmodified build's output JAR sizes (`target/rapla-2.1-SNAPSHOT.jar`) for later regression check (rapla-app fat JAR should be ~same size; per-module JARs sum should be smaller than the monolith).
4. Create worktree per AGENTS.md §7: `git worktree add ../rapla-prd-005 -b prd-005-multi-module`. All work happens in that worktree.

**Test:** baseline `mvn test` green; nothing new written.

### Phase B — Prep cleanup in single module (1–2 days)

Cleans up the few cross-boundary edges before introducing module walls. Done in single-module form so each step is verifiable independently.

1. **Move `ClientService` + `RaplaClientListenerAdapter` to `org.rapla.client.api`** (still in `client/`, but explicitly the cross-module-stable interfaces). Update the 1 importer in `server/internal/console/GUIStarter.java`. *Compile-check after.*
2. **Reshape `components/i18n/`:** move `i18n/client/swing/SwingBundleManager.java` and `SwingIcon.java` to a new package `client/swing/i18n/` under the existing `client/swing` tree. Update imports. *Compile-check after.*
3. **Run `jdeps` cycle audit** against `target/classes` after compile:
   ```
   mvn compile
   jdeps -dotoutput out/jdeps -e 'org.rapla.*' target/classes
   # examine out/jdeps/*.dot for any cycles between the proposed module boundaries
   ```
   Document any residual cycles in this PRD before Phase C. **If >50 cycles surface, stop and reassess scope** — Phase B may need to grow.
4. **Move `client/spring/{ClientConfig,ClientProxyConfig,SpringRaplaClient,SwingClientConfig}.java`** — these are already correctly placed (Spring config for the Swing client). No physical move; just confirm they will land in `rapla-client`, not `rapla-app`. (`rapla-app` only owns `RaplaSpringBootApplication` for the server-side boot.)
5. **Identify shared test utilities.** Per OQ1 decision: no test-jar published. Confirm `RaplaTestCase` and `AbstractTestWithServer` are gone (PRD 001 §1.7). Catalogue any surviving test helpers and which 1–2 modules they need to live in. If two modules need the same helper, duplicate; only lift if a third module appears.

**Test:** `mvn test` green after each interface move; full suite once at end of Phase B.

### Phase C — Create the reactor skeleton (1 day)

Introduces the module shells as **empty modules** (no source moved yet). The single-module build keeps working because no source is moved; the reactor just gains 5 empty siblings.

1. Rename `parent/` → `rapla-bom/`. Adjust `rapla-bom/pom.xml`:
   - `<artifactId>rapla-bom</artifactId>`, `<packaging>pom</packaging>`
   - Strip `<plugins>` config that is build-time concern (not BOM concern); keep `<dependencyManagement>` and properties
   - Add `<dependencyManagement>` entries for the soon-to-exist `rapla-core/-client/-server` artifacts (using `${project.version}`)
2. Create root `pom.xml` (was `master/pom.xml`):
   ```xml
   <packaging>pom</packaging>
   <modules>
     <module>rapla-bom</module>
     <module>rapla-core</module>
     <module>rapla-client</module>
     <module>rapla-server</module>
     <module>rapla-app</module>
     <module>.</module>   <!-- TEMPORARY: keeps current single-module build alive during Phase D -->
   </modules>
   ```
   The `<module>.</module>` line is the **transitional escape hatch** so the existing single-module build continues working while source is being moved out into the new modules. It gets deleted at the end of Phase D.
3. Create `rapla-core/pom.xml`, `rapla-client/pom.xml`, `rapla-server/pom.xml`, `rapla-app/pom.xml` — each with packaging=jar, parent=rapla-bom, **no source yet**, no dependencies yet.
4. Delete `master/pom.xml` (its content moved to root) and `custom/pom.xml` (per ratified decision).
5. `mvn -pl rapla-bom install` — verify BOM resolves.
6. `mvn compile` — single-module build still works via the `.` aggregator entry.

**Test:** `mvn -pl rapla-bom install` succeeds; `mvn compile` from root still produces the legacy `target/classes/`.

### Phase D — Source migration, package by package (4–6 days)

The bulk of the work. Each step is a `git mv` of one or more packages followed by a compile. Order matters — start with leaves (no dependencies on other to-be-moved packages), end with edges that depend on already-moved packages.

**D1. `rapla-core` first** (~330 files):
   1. `entities/**` → `rapla-core/src/main/java/org/rapla/entities/`
   2. `framework/**` → `rapla-core/...`
   3. `logger/**`, `inject/**`, `scheduler/**` → `rapla-core/...`
   4. `rest/**` (DTOs + endpoint *interfaces*; controllers stay in server) → `rapla-core/...`
   5. `components/{util,layout,restproxy,i18n}/**` (minus the swing subpackage moved in Phase B) → `rapla-core/...`
   6. `enpoints/**` (note: original typo — `enpoints` not `endpoints`. Keep the typo to avoid mass rename in this PRD; rename in a follow-up.)
   7. `facade/**` (incl. `facade/server/**` and `facade/client/**` — the names refer to which side instantiates the `RaplaFacade`, not module placement; both subpackages compile against core only)
   8. `storage/**` core pieces (`storage.impl`, abstract operators) — leave JDBC implementation (`storage/dbsql/**`) for D3
   9. Add core's own deps to `rapla-core/pom.xml`: SLF4J, Jackson, jakarta.inject API, jakarta.ws.rs API, RxJava3, iCal4j (used by some entity helpers — verify before dragging it into core; may belong in server)
   10. `mvn -pl rapla-core compile` ← **must pass before continuing**.

**D2. `rapla-client`** (~520 files — biggest module, but no inward dependencies once core lands):
   1. `client/**` (incl. `client/swing/**`, `client/spring/**`)
   2. `components/{calendar,calendarview,iolayer,tablesorter,treetable}/**`
   3. `plugin/<name>/client/**` for every plugin; `plugin/<name>/extensionpoints/**` (these are client extension points)
   4. Move plugin-shared classes (`plugin/<name>/<Name>Plugin.java`, `<Name>Resources.java` at the plugin root) — these are typically referenced from both client and server; **decision needed during D2**: keep shared plugin classes in a `rapla-core` subpackage (`org.rapla.plugin.<name>`) or duplicate per side? **Recommendation:** keep shared plugin classes in `rapla-core` — they are facade-level descriptors, not Swing or Spring specific.
   5. Add `rapla-client/pom.xml` deps: `rapla-core`, Swing/AWT (provided by JDK), JLayer if used, the calendar widgets currently pulled in
   6. `mvn -pl rapla-client compile` ← must pass.

**D3. `rapla-server`** (~80 files — small because most logic was core):
   1. `server/**` (excluding `server/spring/**` — that goes to rapla-app? **No** — see PRD 004 target tree: `server/spring/` is the Spring config for the server module itself, AutoConfiguration-imported. Stays in `rapla-server`.)
   2. `storage/dbsql/**` and `storage/dbrm/**` (server-side storage; client uses RemoteStorage which is in core)
   3. `plugin/<name>/server/**` for every plugin
   4. `plugin/<name>/internal/**` where it's server-side (e.g. `jndi/internal/`)
   5. `rest/server/**` if any (the `@RestController` classes — verify location; PRD 001 puts them under `server/spring/web/`)
   6. `application.yml`, `static/**` legacy resources, `META-INF/spring/` AutoConfiguration imports
   7. Add `rapla-server/pom.xml` deps: `rapla-core`, `spring-boot-starter-web`, `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server`, JDBC drivers, Exchange Web Services, mail, iCal4j (server-side)
   8. `mvn -pl rapla-server compile` ← must pass.

**D4. `rapla-app`** (~3 files):
   1. `RaplaSpringBootApplication.java` (the `@SpringBootApplication` entry point)
   2. `src/assembly/rapla.distribution.xml` → `rapla-app/src/assembly/`
   3. Top-level integration `application.yml` (deployment defaults; per-environment overrides come from `application-local.yml` in the worktree per AGENTS.md §7)
   4. `rapla-app/pom.xml` declares `rapla-server` + `rapla-client` deps and the `spring-boot-maven-plugin` for repackaging
   5. Move JNLP staging — the `maven-dependency-plugin:copy-dependencies` execution that builds `webclient/` runs in `rapla-app` because that is where both client and server JARs are visible
   6. `mvn -pl rapla-app package` ← must produce a runnable fat JAR

**D5. Tests**:
   1. Move test classes to live next to the package they test:
      - `RaplaSpringBootApplicationTest`, `ServerServiceIntegrationTest`, all controller tests → `rapla-server/src/test/java/...` (or `rapla-app` if they require the full @SpringBootApplication context — keep both options open and decide per-test)
      - `ClientConfigTest`, `SpringRaplaClientTest` → `rapla-client/src/test/java/...`
      - Any pure-entity / pure-facade tests → `rapla-core/src/test/java/...`
   2. Resolve shared test fixtures per Phase B step 5.
   3. Run `mvn -pl rapla-server test`, then `mvn -pl rapla-client test`, etc., individually.

**D6. Remove the transitional aggregator entry**: delete `<module>.</module>` from root `pom.xml`. The legacy single-module build is gone. Move the few stragglers in root `src/` to whichever module owns them, or delete if dead.

**Test cadence during Phase D:** `mvn -pl <module-just-touched> compile` after each `git mv` step. Run `mvn -pl <module> test -Dtest=<class>` for the most relevant test after a non-trivial source move. Full `mvn test` from root only at the end of D5 and D6.

### Phase E — Build hygiene & per-module deps (1–2 days)

Once source is in place, deal with the long tail.

1. **Audit `rapla-bom/pom.xml`** for dependencies that should NOT be in the BOM (test-only, per-module concerns). The BOM should only declare third-party version pins that multiple modules consume.
2. **Per-module `<dependency>` minimisation**: each module declares only what it directly imports. Run `mvn dependency:analyze -pl <module>` and prune unused, surface used-undeclared.
3. **`maven-jar-plugin` manifest entries** per module — `Implementation-Title`, `Implementation-Version`, plus any Class-Path entries the JNLP signing pass needs.
4. **Apply the artifact rename** (per OQ2): set `rapla-app/pom.xml` to `<artifactId>rapla-app</artifactId>` and `<finalName>rapla-2.1-SNAPSHOT</finalName>` so the on-disk JAR keeps its current filename for deployer scripts. Verify the assembly descriptor's `<id>` and `<formats>` still produce the same on-disk artefacts.
5. **Signing profiles** (`sign-jks`, `sign-pkcs11`) move from `parent/pom.xml` to `rapla-app/pom.xml` — they only operate on the assembled `webclient/` jars, which are only visible at the app level.
6. **Worktree port convention** (AGENTS.md §7): no change needed — Spring Boot still reads from `application-local.yml`, just inside `rapla-app/`.

### Phase F — Documentation & developer ergonomics (0.5 day)

1. Update `AGENTS.md` build section: `mvn -pl rapla-server test`, `mvn -pl rapla-client compile`, etc. The "always start with `mvn compile`" rule still applies, just at module granularity.
2. Update `AGENTS.md` §7 worktree section: `mvn` commands now operate on the reactor — `mvn install` from the worktree root publishes ALL modules to `~/.m2/repository`. Confirm this is desired (or use `-pl rapla-server -am` for narrower installs).
3. Update `CLAUDE.md` if it has any module-specific guidance (currently a symlink to `AGENTS.md`).
4. Add a brief "module map" table to README or a new `docs/architecture.md` — one row per module, what it contains, who depends on it.
5. Update PRD 003 with a "supersedes notes" entry pointing to this PRD's actual `dhbwrapla` integration shape (depend on `rapla-server` + optionally `rapla-client`, no more WAR overlay).
6. Update PRD 004 status: `decided — implementation in PRD 005`.

### Phase G — `dhbwrapla` update (separate PR, separate session, 1 day) — **DEFERRED with custom/**

Originally Phase G was scoped to update dhbwrapla to depend on `rapla-server` directly instead of inheriting from `org.rapla:custom`. Per user direction 2026-05-07, the custom/ rework is deferred to a future PRD; Phase G is therefore also deferred. The dhbwrapla build does NOT need to keep working through PRD 005's lifetime — the user accepts that dhbwrapla will need its own follow-up PRD aligned with whatever replaces custom/.

If/when dhbwrapla needs to compile against a post-PRD-005 rapla:
1. Replace `<parent>org.rapla:custom</parent>` with direct `rapla-bom` import.
2. Replace `<dependency>org.rapla:rapla</dependency>` with `<dependency>org.rapla:rapla-server</dependency>` (and add `rapla-client` if dhbwrapla ships Swing customisations — verify by grepping its source for `org.rapla.client.swing`).
3. Verify `dhbwrapla-container/pom.xml` still resolves `../../rapla` correctly — its module list shrinks to `[parent (now rapla-bom), rapla-app, dhbwrapla]` instead of the current 4-entry list.
4. Run dhbwrapla's full build + tests.

### Phase H — Maven Central readiness (out of scope for this PRD; tracked here for completeness)

Items to address before any public Maven Central release of `rapla-core` / `rapla-client` / `rapla-server`:
- Source + Javadoc JARs per module (`maven-source-plugin`, `maven-javadoc-plugin`)
- `nexus-staging-maven-plugin` or `central-publishing-maven-plugin` config in `rapla-bom`
- GPG signing in CI
- POM metadata (description, url, scm, licenses, developers) per module
- License file per module if AGPL/Apache2 dual-license requires per-artifact tagging

**Not part of PRD 005.** Captured here so it's not forgotten.

## Tests

Tests are written **before** each phase's implementation per AGENTS.md §1, but for a refactor like this most "tests" are existing tests that must stay green. New tests are scoped to module boundaries.

| Phase | Test artefact | When written |
|---|---|---|
| A | Baseline: capture current `mvn test` results, JAR size of monolith. **Recorded as a checked-in `005-baseline.md` snapshot** (in this `done/` folder). | Before any change |
| B (interface moves) | Existing `mvn test` suite stays green. | Each `git mv` |
| B (i18n swing move) | `RaplaSpringBootApplicationTest` covers i18n init via `RaplaResources` bean — must still pass after move. | After move |
| C (skeleton) | `mvn -pl rapla-bom install` exits 0; `mvn compile` from root still produces `target/classes/` from the transitional `.` module. | After skeleton |
| D1 (rapla-core) | NEW: `rapla-core/src/test/java/org/rapla/core/CoreModuleFitnessTest.java` — ArchUnit test asserting `org.rapla.{client,server}.*` is **never** imported from `rapla-core` source. Detects regressions early. | Before D1 implementation |
| D2 (rapla-client) | NEW: `rapla-client/src/test/java/.../ClientModuleFitnessTest.java` — ArchUnit test asserting `org.rapla.server.*` is never imported from `rapla-client` source (Spring autoconfig packages excepted: `client.spring` may reference Spring core, but not server packages). | Before D2 |
| D3 (rapla-server) | NEW: `rapla-server/src/test/java/.../ServerModuleFitnessTest.java` — ArchUnit test asserting `javax.swing.*` is never imported from `rapla-server` source. | Before D3 |
| D4 (rapla-app) | NEW: `rapla-app/src/test/java/.../AppPackagingTest.java` — verifies the fat JAR produced by `spring-boot-maven-plugin` contains the expected entries (`BOOT-INF/classes/RaplaSpringBootApplication.class`, `BOOT-INF/lib/rapla-server-*.jar`, `BOOT-INF/lib/rapla-client-*.jar`, `BOOT-INF/lib/rapla-core-*.jar`). | Before D4 |
| D5 (tests moved) | All 23 PRD-001 tests still pass, distributed across modules. | Continuous |
| D6 (remove `.` aggregator) | Full reactor `mvn clean install` from root. Compares output JARs against Phase A baseline. | After D6 |
| E | `mvn dependency:analyze` clean for each module (no warnings). | After E |
| F | None — docs only. | — |

**ArchUnit dependency:** introduce `com.tngtech.archunit:archunit-junit5:1.3.0` as a test-scope dep in `rapla-bom`. ~3 MB; well-established library; all module-fitness tests use it.

## Open Questions

1. **Test fixture sharing — DECIDED: no test-jar.** Post-PRD 001 §1.7 the cross-module fixture surface is essentially gone (`RaplaTestCase`, `AbstractTestWithServer` deleted). Each module owns its own `src/test/java/`; the few cross-cutting helpers get duplicated across the 1–2 modules that need them. If a third user appears, lift to `rapla-core` AND add `<goal>test-jar</goal>` then — the decision is cheaply reversible. Phase B step 5 simplifies to a one-line confirmation rather than a design step.

2. **App artifact rename — DECIDED: rename to `rapla-app`, preserve on-disk filename.** Final coordinate map:

   | Coordinate | Packaging | Purpose | On-disk filename |
   |---|---|---|---|
   | `org.rapla:rapla:2.1-SNAPSHOT` | `pom` | Root reactor aggregator | — |
   | `org.rapla:rapla-bom:2.1-SNAPSHOT` | `pom` | BOM (dependencyManagement) | — |
   | `org.rapla:rapla-core:2.1-SNAPSHOT` | `jar` | Shared entities/facade/REST DTOs | `rapla-core-2.1-SNAPSHOT.jar` |
   | `org.rapla:rapla-client:2.1-SNAPSHOT` | `jar` | Swing client + presenters | `rapla-client-2.1-SNAPSHOT.jar` |
   | `org.rapla:rapla-server:2.1-SNAPSHOT` | `jar` | Spring Boot server | `rapla-server-2.1-SNAPSHOT.jar` |
   | `org.rapla:rapla-app:2.1-SNAPSHOT` | `jar` (Spring Boot repackaged) | Runnable fat JAR | **`rapla-2.1-SNAPSHOT.jar`** (preserved via `<finalName>`) |

   Reusing `rapla` for both the root aggregator (pom) and runnable JAR (jar) creates an `org.rapla:rapla` namespace collision; splitting them out kills the ambiguity. `<finalName>rapla-2.1-SNAPSHOT</finalName>` on `rapla-app` keeps any `java -jar rapla-2.1-SNAPSHOT.jar` wrapper script working unchanged. Phase F adds a one-line update to PRD 003: dhbwrapla parents on no rapla artifact, depends directly on `rapla-server` (and optionally `rapla-client`) with versions resolved via `rapla-bom`.

3. **`enpoints` typo** — kept in this PRD to avoid mass rename mid-split. **Open follow-up:** rename to `endpoints` after PRD 005 lands. Two-step rename (`enpoints` → `endpoints` move) is cleaner than mixing it into the module split.

4. **`iCal4j` placement — DECIDED: server-only.** Verified 2026-05-07: zero `net.fortuna.ical4j` imports under `org.rapla.entities.*` or `org.rapla.facade.*`. All 6 importing files are under `org.rapla.server.*` or `org.rapla.plugin.*.server.*`. **No source migration needed.** The POM cleanup is a separate (and easy) win: `parent/pom.xml` line ~268 currently puts `org.mnode.ical4j:ical4j:4.2.0` in the top-level `<dependencies>` block, forcing iCal4j onto every consumer of the parent — including a Swing-only `dhbwrapla` install that has no use for it. Action: in Phase E, declare iCal4j only in `rapla-server/pom.xml`, and demote the BOM entry to `<dependencyManagement>` (version pin only). Net result: `rapla-client` and any client-only consumer drop ~5 MB of unwanted classpath.

5. **JNLP `webclient/` content.** Today the `maven-dependency-plugin:copy-dependencies` execution in `pom.xml` filters by `<excludeArtifactIds>`. After the split, the *includeable* set changes (now `rapla-core`, `rapla-client`, `rapla-client-swing`-equivalent code inside `rapla-client`, plus their transitive deps). **Action:** rewrite the copy-dependencies config in `rapla-app/pom.xml` to use `<includeArtifactIds>rapla-core,rapla-client,...</includeArtifactIds>` instead of an exclusion list. Verify the JNLP set is correct before Phase F docs.

6. **Spring Boot AutoConfiguration imports — DECIDED: server-only autoconfig; client stays explicit.** `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` moves to `rapla-server/src/main/resources/META-INF/spring/`. **The client deliberately does NOT use autoconfig.** Reasons (in descending weight):
   1. **JNLP delivery is structurally incompatible with Spring Boot autoconfig.** The Swing client ships via Java Web Start; JNLP loads a flat list of signed JARs and does not run Spring Boot's `JarLauncher`, so the autoconfig bootstrap path doesn't fire. PRD 001 marks `/raplaclient.jnlp` as a HARD CONSTRAINT.
   2. **Spring Boot is server-shaped.** `@SpringBootApplication` brings in autoconfigs for embedded web servers, datasources, security, Actuator, etc. — dead weight on a desktop, and the exclusion list would be longer than the current `ClientConfig`.
   3. **Deterministic plugin wiring beats classpath scanning for multi-customer deployments.** `SwingClientConfig` explicitly imports each plugin's client `@Configuration`. With autoconfig scanning, every plugin JAR on the JNLP codebase would silently inject Swing beans — bad for `dhbwrapla` (and any future custom deployment) that wants to opt out of stock plugins.
   4. **Faster startup, no `@ConditionalOnXxx` graph evaluation.** `new AnnotationConfigApplicationContext(ClientConfig.class)` boots in ~10× less time than `SpringApplication.run` — visible to the desktop user.
   5. **Test ergonomics.** `ClientConfigTest` is a plain JUnit 5 test, not `@SpringBootTest` — sub-second context boot.

   Architectural framing: autoconfig is the right tool when N independently-shipped libraries each contribute beans to an application that doesn't know they exist. That fits `rapla-server` (custom deployments may add starters). It does not fit `rapla-client`, where the plugin set is decided at deployment time by which JARs are placed on the JNLP codebase. Post-split, `rapla-client/pom.xml` depends on `spring-context` only, NOT `spring-boot-starter-*`.

7. **Phase 4 ordering — DECIDED: split first, live Swing-DI conversion second.** Three reasons:
   - **Bounded vs unbounded scope.** The split is bounded (~976 files, ~3 cross-boundary edges, ~1.5 weeks). The Swing-DI conversion is unbounded — every legacy field-injected class in `client/swing/internal/`, `client/dialog/`, etc., one at a time. AGENTS.md §4 already mandates constructor injection on every edit, so the conversion happens organically. Sequencing the bounded work first lets the unbounded work absorb the new module structure as a constraint, not a moving target.
   - **Smaller blast radius after the split.** Once `rapla-client` exists as its own module, every Swing-DI commit is `mvn -pl rapla-client test` — fast, contained.
   - **Phase 4 may dissolve into BAU.** If contributors honour AGENTS.md §4, "Phase 4" stops being a discrete project and becomes the slow grind that happens whenever a Swing class is touched.

   Edge case: some `client/swing/` files already have a mix of constructor-injected and field-injected dependencies (PRD 001 Phase 4 partial state). The split treats them as one input — they all move to `rapla-client/` as-is, mixed-injection state preserved. The conversion continues organically inside `rapla-client/` afterward.

8. **CI matrix.** Today there's no CI config in the rapla repo (verify by `ls .github/workflows/`). If/when CI is added, should it run a per-module job matrix (`-pl rapla-server`, `-pl rapla-client`, ...) or one full `mvn install`? **Defer** — CI setup is its own PRD.

## Risks & mitigations

| Risk | Mitigation |
|---|---|
| Cycle audit (Phase B step 3) reveals more cycles than the grep-based scan caught (e.g. via reflection or string-based class loading) | Phase B is timeboxed; if `>50` cycles surface, escalate before Phase C. The 5-module split is a *target*, not a contract — if reality forces 4 modules or different boundaries, that's better than a quarter of cycle-breaking. |
| Mid-split, someone (or another agent) lands a commit on `master` that crosses what will become a module boundary | Worktree on `prd-005-multi-module` branch. Rebase frequently. Phase D's per-step compile-checks catch newly-introduced cycles within hours. |
| `dhbwrapla` build breaks before Phase G is merged | `dhbwrapla` builds against published Maven coords. Until PRD 005 is tagged, `dhbwrapla` resolves against the OLD `rapla` JAR — no change. Phase G is a coordinated PR after PRD 005 ships. |
| JNLP signing pass fails because the assembled `webclient/` content changed | Phase E step 5 explicitly addresses signing-profile relocation. Test by running the signing profile against a sample build before Phase F. |
| The `<module>.</module>` transitional aggregator (Phase C) is forgotten or causes ambiguity | Phase D6 explicitly removes it. `grep -r "<module>\.</module>" .` at end of Phase D as a checklist item. |

## Effort estimate

Aggregate: **~1.5 weeks of focused work** for a single agent in a worktree, assuming the cycle audit (Phase B step 3) confirms the headline numbers from the §"Cycle audit" section.

| Phase | Work |
|---|---|
| A | 0.5 day |
| B | 1–2 days (depends on cycle audit findings) |
| C | 1 day |
| D | 4–6 days (the bulk; mechanical but each step needs a compile) |
| E | 1–2 days |
| F | 0.5 day |
| G | 1 day (separate session in dhbwrapla worktree) |

If the cycle audit surfaces >50 cycles between proposed boundaries, expect Phase B to grow to 3–5 days and total effort to ~3 weeks.

## Dependencies on other PRDs

| PRD | Relationship |
|---|---|
| **001** Spring Boot Migration | **Hard prerequisite** — Phases 1–8 must be done. **STATUS: done on `spring-boot` branch.** Phase 4 follow-up (live Swing UI to Spring) is independent and can run in parallel after PRD 005 lands. |
| **001-A** Date → LocalDateTime | Independent. Touches `rapla-core` signatures; can run in parallel. |
| **002** Multi-Tenancy | Independent. `TenantAwareFacade` lives in `rapla-server`; no module-boundary impact. |
| **003** Custom Deployments | **Bidirectional.** PRD 005 simplifies PRD 003's OQ2/OQ5/OQ6. Update PRD 003 in Phase F to reflect the multi-module deployment shape. |
| **004** Multi-Module Architecture Analysis | **Decision document.** PRD 005 is the implementation. Mark PRD 004 status `decided — implemented in PRD 005`. |
| **006** Angular Client (future) | Depends on PRD 005 being done so `rapla-client-web/` has a clean home as a peer of the Java reactor. |
| **007** Build & Test Performance | Likely benefits from the split (per-module `mvn test` is faster) but does not block PRD 005. |
