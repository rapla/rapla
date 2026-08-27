# PRD 004: Multi-Module Architecture Analysis

**Status:** decided — implementation in [PRD 005](005-multi-module-split.md), reactor live on `spring-boot` branch since 2026-05-07. **Decision document, kept for future reference.**
**Date:** 2026-05-06

## Goal

Evaluate **module structure and build layout** for Rapla absorbing three converging pressures:

1. **Spring Boot migration (PRD 001)** — single fat JAR with server `@SpringBootApplication`, Swing client driven by separate `AnnotationConfigApplicationContext` (Phase 4 partial), JNLP preserved.
2. **Custom deployments ([PRD 003](../003-custom-deployments-after-spring-migration.md), e.g. `../dhbwrapla`)** — need a clean library form (not WAR overlay) plus own beans, config, REST endpoints, Swing UI.
3. **Future Angular client** — takes over main frontend from Swing, consuming same REST API.

This PRD does **not** prescribe an implementation plan. It compares options across three independent axes (module layout, build tool, frontend integration) and lands on a recommended target so PRDs 005+ can flesh out phases.

## Current State

### Build layout

```
rapla/
├── pom.xml             ← the single source module (jar packaging, ~1100 java files)
├── parent/pom.xml      ← BOM-like parent: properties, dependencyManagement, plugins
├── master/pom.xml      ← aggregator listing { parent, ., custom }
├── custom/pom.xml      ← parent POM for downstream WAR-overlay projects (dhbwrapla)
└── src/                ← server + client + plugins + tests, all in one tree

dhbwrapla/              ← lives outside rapla repo
├── pom.xml             ← parent: org.rapla:custom (WAR overlay)
└── dhbwrapla-container/pom.xml  ← aggregator listing rapla/parent, rapla/custom, rapla, dhbwrapla
```

*Logical* multi-module structure (parent + main + custom + container aggregator) but a **single compiled artifact** (the rapla JAR). All ~1100 `.java` files compile into one classpath.

### Logical separation already encoded in packages

Source tree consistently uses:
- `org.rapla.client.*` — client (toolkit-agnostic)
- `org.rapla.client.swing.*` — Swing-specific
- `org.rapla.server.*` — server
- `org.rapla.server.spring.*` — Spring Boot wiring (PRD 001)
- `org.rapla.entities.*`, `org.rapla.facade.*`, `org.rapla.framework.*`, `org.rapla.scheduler.*`, `org.rapla.storage.*`, `org.rapla.components.*`, `org.rapla.logger.*`, `org.rapla.rest.*` — shared / "core"
- `org.rapla.plugin.<name>.{client,client/swing,server}` — every plugin uses this triplet

Intent of client/server/core split has been there all along; only the build layer doesn't enforce it. Multi-module would enforce conventionally-true rules.

### What's client-only / server-only at runtime

- **Server-only:** Spring Boot starters, Tomcat, Spring Security, JWT, REST controllers, `ServerServiceContainer`, Exchange Web Services, iCal4j (server serializers), JDBC drivers, mail
- **Client-only:** Swing, AWT, JLayer, calendar widgets, `RaplaClient`/`MainWebclient` bootstrap
- **Shared:** Entity model, RxJava promises, framework, i18n, logger, REST DTOs, RaplaFacade, scheduler

### Frontend assets that already exist

- `src/main/resources/static/Rapla/`, `static/jsclient/` — small jQuery-based JS UI (date pickers, jstree)
- `static/rapla.html`, `apiTest.html`, `redirect.html`
- No Angular/React/TypeScript build pipeline. Swing client is **the** main frontend.

## Decision dimensions

| Axis | Choices | Coupling |
|------|---------|----------|
| **A. Module layout** | Single / Maven multi-module / multi-repo | Independent of B/C — multi-module is prerequisite for cleanly publishing client-only and server-only artifacts to Maven Central |
| **B. Build tool** | Maven (current) / Gradle / Bazel | Mostly independent of A |
| **C. Frontend integration** | Bundle Angular in fat JAR / separate module / standalone repo | Coupled to A |

## Axis A — Module Layout

### Option A1: Stay single-module

Keep today's `pom.xml` + `parent` + `custom`. Custom deployments depend on rapla JAR; client/server separation stays convention-only.

**Rejected** because: custom deployments pull in all server deps (~50 MB) even if Swing-only; nothing prevents accidental cross-layer imports; JNLP `webclient/` ships server classes; Angular module has nowhere to live; publishing as `rapla-client`/`rapla-server` to Maven Central needs post-hoc shade-plugin filtering.

### Option A2: Maven multi-module split

**Shape:** turn `master/pom.xml` aggregator into a real reactor. Suggested split (**5 modules**):

```
rapla/
├── pom.xml                     ← aggregator (was master/)
├── rapla-bom/                  ← BOM + dependencyManagement (was parent/)
├── rapla-core/                 ← entities, facade, framework, scheduler, storage, rest (DTOs + endpoint interfaces),
│                                  components, logger, i18n
├── rapla-client/               ← org.rapla.client.* (incl. swing) + plugin/*/client/* + Swing/AWT deps
├── rapla-server/               ← org.rapla.server.* (incl. Spring config) + plugin/*/server/* + REST controllers
├── rapla-app/                  ← @SpringBootApplication + assembly that produces the runnable fat JAR
└── rapla-archetype/            ← optional: maven-archetype for new custom deployments (replaces custom/pom.xml)
```

A finer split (separate `rapla-entities`, per-plugin modules, splitting client/swing apart) is possible but **not justified** by current/planned consumers — see "Why not split rapla-client into two modules?" below.

#### Why not split rapla-client into two modules?

Source already maintains clean package-level convention:

| | `org.rapla.client.*` (top level, 19 files) | `org.rapla.client.swing.*` (hundreds of files) |
|---|---|---|
| Role | Presenters + view interfaces — *what* the app does | Swing rendering of view interfaces — *how* it draws |
| `javax.swing` imports | **0** (verified by grep) | many |
| Example | `CalendarPlacePresenter`, `ApplicationView` (interface), `EditController` | `RaplaGUIComponent`, `EditField`, `OptionPanel` |

Convention holds at top level and leaks one level deeper (`client/internal/edit/`, `client/dialog/` import from `client/swing/*`). **Keep package-level split**; cheap to maintain.

**Don't split into two Maven modules.** A separate `rapla-client` artifact only helps if a second *Java* client consumed it (JavaFX, headless integration client, SWT). None exist or are planned.

**Angular is not such a consumer.** Angular consumes REST over HTTP, doesn't `import` Java. What it needs from Java is the **wire contract** (REST DTOs + endpoint defs) in `rapla-core`, via runtime OpenAPI spec from `springdoc-openapi`, code-gen by `openapi-generator-cli`, optional build-time i18n JSON from `.properties`. None requires `rapla-client` published as a separate Maven artifact.

If a second Java client ever materialises, splitting `rapla-client` from `rapla-client-swing` is a 1–2 day refactor against a known-clean top-level boundary. Defer.

**Dependency direction (must be acyclic):**

```
rapla-client ──► rapla-core ◄── rapla-server
                     ▲                ▲
                     └───┐    ┌───────┘
                         │    │
                       rapla-app  ──► (also depends on rapla-client for JNLP staging:
                                       rapla-app owns the assembly that bundles the Swing
                                       client jars into webclient/ — the server module
                                       itself does not depend on the client)

rapla-client-web/  (Angular project, npm-built static assets — depends on
                    nothing from the Java reactor at compile time; consumes
                    the REST API at runtime)
```

**Verified by grep:** `org.rapla.server.*` imports from `org.rapla.client.*` only **2 times** (`ClientService`, `RaplaClientListenerAdapter`) — both interfaces, both trivially moved to `rapla-core`. `org.rapla.client.*` imports from `org.rapla.server.*` zero times. "Server depends on core only" is honest, not aspirational.

**Pros:**
- Custom deployments declare exactly what they need: `rapla-client` Swing-only, `rapla-server` backend-only, both for full deployments
- Build-time layering — server can't import Swing, client can't import JDBC
- Maven Central publishing natural
- JNLP `webclient/` set well-defined: `rapla-core` + `rapla-client` + transitive client deps. No accidental server classes
- Angular slots in as `rapla-client-web/` (peer of `rapla-client-swing`)
- Per-module test execution (`mvn -pl rapla-server test`)
- Forces explicit handling of cross-cutting beans (e.g. `RaplaFacade` in both Swing + REST contexts)

**Cons:**
- One-time cost: every cross-boundary package import needs review. ~100–300 cycle violations (guess; needs `archunit` audit)
- Cyclic deps tolerable in one jar become hard errors in reactor — there *are* cycles between `client/internal/*` and `client/swing/*`
- Plugin authors must choose a module per file; `plugin/<name>/{client,server}` splits each plugin across at least two new modules (or each plugin becomes its own module)
- Slightly more IDE setup
- `dhbwrapla` is fundamentally a *single* deployment; stays single-module, just depending on multiple rapla artifacts

### Option A3: Multi-repo

Each rapla layer in its own git repo, own version, depend via Maven coords.

**Rejected** because: change touching three layers requires three PRs across three repos — punishing for a small team; cross-repo refactoring painful; team currently has two repos, going to six is a leap; most wins already delivered by A2.

**Verdict on A: Option A2 (Maven multi-module).** A1 leaves real wins on the table; A3 is too heavy for team size + change frequency.

## Axis B — Build Tool

| | Maven (current) | Gradle | Bazel |
|---|---|---|---|
| **Multi-module support** | First-class (reactor) | First-class (multi-project) | First-class (workspace) |
| **Spring Boot integration** | Mature, official `spring-boot-maven-plugin` | Mature, official `org.springframework.boot` plugin | Possible, more setup |
| **Incremental builds** | Limited (`-pl`/`-amd`/incremental compile) | Strong (build cache, configuration cache, incremental tasks) | Strongest (hermetic, remote cache) |
| **Plugin ecosystem (jarsigner, JNLP, archetype, license, GPG)** | Complete | Complete | Sparse |
| **Custom-build complexity** | Declarative XML — verbose but predictable | Imperative Groovy/Kotlin DSL — flexible but easy to over-engineer | Bzl/Starlark — steepest learning curve |
| **[PRD 003](../003-custom-deployments-after-spring-migration.md) custom-deployment ergonomics** | `<dependency>` + `<parent>` well-understood | Same model, slightly nicer DSL | Forces consumers onto Bazel — non-starter |
| **Concurrent invocations on the *same* workspace / `target/` directory** | **Unsafe — and silently so.** Maven holds no project-level lock. Two `mvn compile` runs race on `target/classes/`; `mvn compile` + `mvn test` overlapping causes `NoClassDefFoundError` / stale-class failures; two `mvn package` can truncate the same `target/*.jar` mid-write. The local-repo install step *does* take per-artifact lock, so `~/.m2/repository` stays consistent — but mismatched artifact versions from interleaved builds can still land there. | **Unsafe but loud.** Gradle takes explicit project lock; second invocation blocks with "Waiting for an exclusive lock on project '…'". Slower (serialised) but correct. Daemon contention still applies on top. | Same workspace serialises on output-base lock — same "blocks but doesn't corrupt" as Gradle. |
| **Concurrent invocations on *different worktrees* of the same repo (the recommended pattern)** | **Excellent.** Each `git worktree` has its own absolute path → its own `target/`. Only shared state is `~/.m2/repository` (proper per-artifact locks). Where Maven's stateless model shines for parallel agents. | Better than same-workspace but not great. Daemon keys project state by absolute path so worktrees look like separate projects, but daemon JVM is shared (memory pressure) and `~/.gradle/caches/` is contended. Poisoned daemon affects every in-flight build. | Excellent if each worktree has its own output base; remote cache amplifies. |
| **Migration cost from current Maven setup** | None | Moderate — POMs convert mechanically with `gradle init`, but jarsigner/JNLP/Spring-Boot/sign-pkcs11 profiles need rewriting | High — full rewrite, no automated migration |
| **CI/IDE familiarity for the team** | Already in use | New | New |

**Verdict on B: Stay on Maven.** Three reasons:

1. **Maven fits parallel-agent-per-worktree workflow better than Gradle.** On the *same* workspace, **Maven is actually less safe than Gradle** (no project lock, silent corruption). Recommended pattern is one git worktree per agent. **In the worktree pattern Maven wins decisively:** each worktree gets its own `target/`, only `~/.m2/repository` is shared. Gradle in worktrees still funnels through one shared daemon JVM and one `~/.gradle/caches/`. As the team shifts toward agent-assisted development with worktrees, **build-tool behaviour under concurrent worktree invocation matters more than incremental-build speed.**
2. **Migration cost real, payoff small.** Gradle's 10–30% incremental advantage isn't worth rewriting four signing profiles, Spring Boot packaging, assembly descriptor, JNLP plumbing while PRD 001 is mid-migration.
3. **Bazel forces consumers.** Pushing `dhbwrapla` onto Bazel is non-starter.

**Reconsider Gradle in 12–18 months** only if reactor build times become DX pain *and* parallel-agent friction is characterised. **Mitigation if Gradle ever adopted:** standardise `--no-daemon` for agent/CI, give each worktree its own `GRADLE_USER_HOME`, accept cold-start tax as the price of safe concurrency.

## Axis C — Angular Client Integration

Angular consumes the same REST API the Swing client uses (already exists post-PRD 001) and progressively takes over functionality.

**Angular's relationship to Java reactor:** none at compile time. Angular has TypeScript presenter logic and doesn't `import` Java. What it needs:

1. **Wire contract.** Add `springdoc-openapi` to `rapla-server` → `/v3/api-docs`. Angular build runs `openapi-generator-cli` → typed TypeScript clients (generated, not hand-written).
2. **i18n strings.** Generate `.json` from `RaplaResources.properties` at build time. Small Maven step in `rapla-app` (or `rapla-client-web`).
3. **Auth token format.** JWT (PRD 001 Phase 3); Angular puts in `Authorization: Bearer …`.

That's it. No Maven dependency on `rapla-client`/`rapla-server` at compile time.

### Option C1: Bundle Angular build inside `rapla-app` fat JAR

Angular at `rapla-client-web/`. Build: `frontend-maven-plugin` runs `npm install && ng build --output-path target/dist`; `maven-resources-plugin` copies to `src/main/resources/static/app/`. Spring Boot serves `/app/index.html`.

Pros: single deployable; no CORS; lock-step versions; preserves existing reverse-proxy / OWS URL space.
Cons: fat JAR grows ~2–5 MB compressed; frontend dev cycle slower (`mvn package` vs `ng serve`) — mitigated by `ng serve` in dev with proxy to localhost:8051.

### Option C2: Separate `rapla-client-web/` module producing static-assets JAR

Same source layout. Build produces `rapla-client-web-2.1.jar` (only `META-INF/resources/app/**`). `rapla-app` declares as runtime dep. Spring Boot's `META-INF/resources/` serves automatically.

Pros: custom deployments can choose to ship Angular independently of server; frontend-only patch republish; cleaner separation (own `package.json`, `node_modules`, CI lane).
Cons: slightly more complex wiring; version drift (mitigate via BOM).

### Option C3: Standalone Angular repo, deployed independently

Angular at `rapla-web/` — separate git repo, CDN/static-bucket. Talks via CORS-enabled REST.

**Rejected** because: CORS/auth-cookie/JWT-cookie domain configuration; two-repo coordination for breaking API changes; custom deployments deploy two artifacts; premature for current scale — no separate frontend team.

**Verdict on C: Option C2** as target, falling back to **C1** if JAR-of-resources adds friction. Both keep Angular first-class inside reactor; only difference is bundling vs separable JAR. Angular lives at `rapla-client-web/` either way. Avoid C3 until separate frontend team or CDN strategy.

### Notes on Swing → Angular transition

- Swing + Angular **co-exist for a long time** — Swing is the only path to many advanced features today (admin tools, plugin option panels, complex reservation editors). Angular takes over read-heavy / mobile-friendly / public-facing flows first.
- Both share REST API surface. Any new endpoint should be REST-first to serve both.
- Plugin extensions need a "web-client" sibling alongside `client/swing` — `plugin/<name>/client/web/`. Each plugin can ship Swing, web, both, or neither.
- `PluginOptionPanel` is Swing-specific. Web equivalent needed (separate `WebPluginOptionPanel` or polymorphic keyed by render target).

## Custom Deployment Fit (relation to [PRD 003](../003-custom-deployments-after-spring-migration.md))

[PRD 003](../003-custom-deployments-after-spring-migration.md) lays out how `dhbwrapla` migrates to Spring Boot. Multi-module split simplifies:

| [PRD 003](../003-custom-deployments-after-spring-migration.md) concern | Single-module today | Multi-module (this PRD) |
|---|---|---|
| Custom project parent POM | `org.rapla:custom` (WAR overlay) | `org.rapla:rapla-archetype` (archetype) — or declare deps directly |
| Custom project dependencies | `org.rapla:rapla` (everything) | `org.rapla:rapla-server` + `org.rapla:rapla-client` (or only what's needed) |
| Pulling Spring Security for Swing-only customization | Forced | Avoided — declare `rapla-client` only |
| Pulling Swing for server-only customization | Forced | Avoided — declare `rapla-server` only |
| `@Import(RaplaSpringBootApplication.class)` foot-gun ([PRD 003](../003-custom-deployments-after-spring-migration.md) OQ6) | Required | Replaced by depending on `rapla-server` which exposes `RaplaServerAutoConfiguration` |
| JNLP `webclient/` content | All rapla classes | `rapla-core` + `rapla-client` + custom JAR — well-defined, smaller |
| Custom Angular extensions | Nowhere | Next to `rapla-client-web/` (separate npm under `dhbwrapla/web/`) — consumes same OpenAPI spec |

Multi-module split is **not just internal cleanup** — directly resolves three of [PRD 003](../003-custom-deployments-after-spring-migration.md)'s open questions (OQ2 archetype, OQ5 client `@ComponentScan`, OQ6 auto-configuration).

## Recommendation (proposed target architecture)

1. **Module layout (A):** Maven multi-module reactor with **5 modules**: `rapla-bom`, `rapla-core`, `rapla-client` (Swing included), `rapla-server`, `rapla-app`. Optional `rapla-archetype`. When Angular begins, add `rapla-client-web/` peer (npm-built, no Maven dep). Splitting `rapla-client` from `rapla-client-swing` **not** part of this — package convention already documents boundary, no Java consumer (Angular consumes REST). If second Java client ever materialises, revisit.

2. **Build tool (B):** **Stay on Maven**, **standardise on one git worktree per agent** for parallel work. Maven's stateless per-invocation model fits worktree pattern (each worktree own `target/`, only `~/.m2/repository` shared). It is *not* safe for concurrent same-workspace builds — neither is Gradle, but Gradle blocks rather than corrupting. Reconsider Gradle in 12–18 months only if reactor times become painful *and* parallel-agent friction characterised.

3. **Frontend (C):** Angular lives at `rapla-client-web/` inside reactor. Build artifact is static-assets JAR consumed by `rapla-app` (Option C2). Falls back to bundling under `static/app/` (C1) if friction. **Not** separate repo (C3).

4. **Angular and Swing coexist indefinitely.** No Swing deprecation in this PRD. Angular takes user-facing flows first; admin/plugin-config in Swing until web equivalent.

5. **`dhbwrapla` stays single-module** — one deployment, not a platform. Just depends on `rapla-server` + `rapla-client-swing` instead of monolithic `rapla` JAR.

## Target Directory Structure

### Maven layout (recommended)

```
/home/chris/git/rapla/                          ← repo root
├── pom.xml                                     ← aggregator, imports rapla-bom
├── .mvn/, mvnw, mvnw.cmd                       ← Maven wrapper
│
├── rapla-bom/                                  ← BOM (packaging=pom)
│   └── pom.xml                                 ← properties + dependencyManagement
│
├── rapla-core/                                 ← shared. NO Spring, NO Swing
│   ├── pom.xml                                 ← deps: logging + jakarta APIs only
│   └── src/main/java/org/rapla/
│       ├── entities/        facade/        framework/        scheduler/
│       ├── storage/         logger/        components/i18n/  components/util/
│       └── rest/                               ← REST DTOs + endpoint interfaces
│
├── rapla-client/                               ← presenters + Swing (single module — see PRD)
│   ├── pom.xml                                 ← deps: rapla-core + Swing/AWT
│   └── src/main/java/org/rapla/
│       ├── client/                             ← toolkit-agnostic presenters & view ifaces
│       ├── client/swing/                       ← Swing renderers
│       ├── components/calendarview/swing/
│       └── plugin/<name>/client/{,swing/}
│
├── rapla-server/                               ← Spring + REST controllers + JDBC
│   ├── pom.xml                                 ← deps: rapla-core + spring-boot-starter-{web,security,jdbc}
│   └── src/main/
│       ├── java/org/rapla/
│       │   ├── server/                         ← server domain
│       │   ├── server/spring/                  ← Spring config (autoconfig, security, web)
│       │   ├── storage/dbsql/                  ← JDBC implementation
│       │   ├── rest/server/                    ← @RestController classes
│       │   └── plugin/<name>/server/
│       └── resources/
│           ├── application.yml
│           ├── META-INF/spring/                ← AutoConfiguration.imports
│           └── static/                         ← legacy jQuery static assets (kept until Angular replaces)
│
├── rapla-app/                                  ← runnable: @SpringBootApplication + fat JAR + JNLP staging
│   ├── pom.xml                                 ← deps: rapla-server + rapla-client + spring-boot-maven-plugin
│   └── src/main/
│       ├── java/org/rapla/RaplaApplication.java       ← @SpringBootApplication entry point
│       ├── resources/application.yml                  ← deployment defaults
│       └── assembly/rapla.distribution.xml            ← was src/assembly/, signs webclient/*.jar here
│
├── rapla-archetype/                            ← OPTIONAL: archetype for new custom deployments
│   └── src/main/resources/archetype-resources/...
│
├── rapla-client-web/                           ← Angular SPA. NPM project, NOT a Maven module
│   ├── package.json, angular.json, tsconfig.json
│   ├── proxy.conf.json                         ← committed default → http://localhost:8051
│   ├── proxy.conf.local.json                   ← gitignored, per-worktree port override
│   ├── src/
│   │   ├── main.ts, index.html
│   │   ├── app/
│   │   │   ├── api/                            ← GENERATED from /v3/api-docs (gitignored)
│   │   │   ├── i18n/                           ← GENERATED from .properties (gitignored)
│   │   │   ├── pages/                          ← hand-written
│   │   │   └── components/                     ← hand-written
│   │   ├── assets/, environments/
│   ├── e2e/                                    ← Playwright/Cypress
│   └── dist/                                   ← gitignored. Consumed by rapla-app via static-assets JAR
│                                                  (Option C2) or copied into static/app/ (Option C1)
│
├── docs/prd/                                   ← unchanged
└── (gitignored, per-worktree) application-local.yml, proxy.conf.local.json
```

**dhbwrapla side, unchanged location:**

```
/home/chris/git/dhbwrapla/
├── pom.xml                                     ← parent now imports rapla-bom (was: org.rapla:custom)
└── src/main/java/org/rapla/dhbw/...            ← deps on rapla-server (and optionally rapla-client)
                                                  instead of the monolithic rapla JAR
```

### Wiring summary

| Module | Depends on |
|---|---|
| `rapla-bom` | nothing |
| `rapla-core` | `rapla-bom` |
| `rapla-client` | `rapla-core` |
| `rapla-server` | `rapla-core` |
| `rapla-app` | `rapla-server` + `rapla-client` (the only module that knows both) |
| `rapla-client-web` | nothing in the Java reactor (consumes REST + OpenAPI spec at build time) |
| `dhbwrapla` | `rapla-server` (required) + `rapla-client` (only if shipping Swing customisations) |

### Gradle delta (if Axis B is ever revisited)

Source tree, module count, names are **identical** under Gradle. Only build descriptors change:

| Maven file | Gradle equivalent |
|---|---|
| Root `pom.xml` (aggregator) | `settings.gradle.kts` (`include(":rapla-core", …)`) |
| Root `pom.xml` (parent properties) | Root `build.gradle.kts` with `subprojects { … }` + `gradle/libs.versions.toml` (version catalog) |
| `rapla-bom/pom.xml` (packaging=pom) | `rapla-bom/build.gradle.kts` applying the `java-platform` plugin |
| Per-module `pom.xml` | Per-module `build.gradle.kts` |
| `dependencyManagement` block | `platform("org.rapla:rapla-bom")` or version-catalog reference |
| `<profile>` blocks (sign-jks, sign-pkcs11) | Gradle task variants — **non-trivial rewrite** |
| `spring-boot-maven-plugin` | `id("org.springframework.boot")` plugin |
| `frontend-maven-plugin` (npm in `rapla-client-web`) | `com.github.node-gradle.node` plugin |
| `maven-assembly-plugin` (rapla.distribution.xml) | Custom `Zip`/`Tar`/`Jar` tasks |
| `maven-jarsigner-plugin` | `signing` plugin or custom `JarSigner` task |
| `mvnw` / `mvnw.cmd` | `gradlew` / `gradlew.bat` |

Angular module identical under both — npm regardless. Java build tool only orchestrates `npm install` + `npm run build`. **dhbwrapla side stays Maven either way** since Gradle publishes plain Maven coordinates. (Bazel would force consumer migration; that's the structural reason Bazel is ruled out in Axis B.)

Same shape, same source — cost of switching is in those descriptors, especially the four signing/JNLP profiles, not the layout.

## Risks

1. **Cycle breaking is unbounded.** Until `archunit` (or `jdeps`) quantifies cross-package cycles, migration cost in A2 is unknown. **Action:** run `jdeps -e 'org.rapla.*' -dotoutput out/ target/classes` and grep cycles between proposed boundaries. <50 cycles → 1–2 week task; >300 → quarter.

2. **Plugin layout decision load-bearing.** Splitting each plugin across `rapla-client`/`rapla-client-swing`/`rapla-server` is clean but yields ~30 plugins × 3 = 90 sub-modules — too many. Realistic options: (a) plugins as packages inside three main modules (chosen here), or (b) one module per plugin with internal client/server source roots. (a) simpler; (b) lets a plugin publish independently. **Defer** until at least one plugin needs independent release.

3. **PRD 001 mid-migration.** Restructuring during PRD 001 Phase 4–7 multiplies conflicts. **Sequence:** finish PRD 001 (Phases 1–7 + 8 cleanup), then start split. Don't interleave.

4. **Angular learning curve.** No Angular code today. C-axis decision needs a small spike (one page consuming `/resources`). Evaluate React/Vue/Svelte/HTMX in same spike — Angular is reasonable default but not only choice.

5. **JNLP signing across modules.** With `webclient/` content from multiple Maven artifacts, signing operates on assembled set in `rapla-app` or `dhbwrapla`'s build — not per module. [PRD 003](../003-custom-deployments-after-spring-migration.md)'s signing-chain section already assumes this.

6. **Maven Central coordinates.** Independent `rapla-client`/`rapla-server` opens version skew in downstream. **Mitigate** by publishing `rapla-bom` and requiring consumers to import it. `dhbwrapla` and any future custom deployment imports BOM, never specifies individual versions.

## Open Questions

1. **Each plugin as own module?** See Risk 2. Recommendation: not yet.
2. **Where does `org.rapla.components.*` go?** Grab-bag (i18n, calendarview, util). Some clearly client-Swing; some core. **Recommendation:** split — i18n + util to `rapla-core`, `calendarview/swing` to `rapla-client`.
3. **Angular framework choice.** Angular vs React vs Vue vs Svelte vs HTMX. User named Angular, but spike in Risk 4 should validate.
4. **Authentication for Angular client.** JWT (already used by Swing per PRD 001 Phase 3), or session cookies? **Recommendation:** JWT in `Authorization: Bearer`, `sessionStorage` (not `localStorage`). Decide before C-work.
5. **i18n source of truth.** Swing uses `RaplaResources` (Java `ResourceBundle`). Angular needs translations — share `.properties` via build step, or maintain separate `.json`? **Recommendation:** generate `.json` from `.properties` at build time, one source of truth.
6. **Should `rapla-archetype` be created, or is a documented `pom.xml` template enough?** [PRD 003](../003-custom-deployments-after-spring-migration.md) leaned "documentation only — 1–2 custom deployments." `rapla-archetype` listed *optional*.
7. **Repository layout.** Stay one repo with 5-module reactor, or split per module (A3)? **Recommendation:** stay. If one module attracts external contributors at different cadence, split *that one* later.

## Dependencies on Other PRDs

| PRD | Relationship |
|-----|-------------|
| **001: Spring Boot Migration** | **Hard prerequisite.** Wait for Phases 1–7 + 8 cleanup before any module split (Risk 3). |
| **001-A: Date → LocalDateTime** | Independent; parallel ok. Touches signatures inside `rapla-core`, won't move once modules exist. |
| **002: Multi-Tenancy** | Independent. `TenantAwareFacade` lives in `rapla-server`; doesn't cross module boundaries. |
| **003: Custom Deployments** | **Bidirectional.** [PRD 003](../003-custom-deployments-after-spring-migration.md) assumes single-module today; this PRD simplifies three of its open questions (OQ2, OQ5, OQ6). If approved, [PRD 003](../003-custom-deployments-after-spring-migration.md) should target multi-module shape. If deferred, [PRD 003](../003-custom-deployments-after-spring-migration.md) proceeds against single-module and is not blocked. |
| **(future) PRD 005: Multi-Module Split** | This PRD's recommendation is the input. PRD 005 lays out actual phased split: cycle audit, module creation, package moves, dependency declarations, CI updates. |
| **(future) PRD 006: Angular Client** | Depends on PRD 005 having created `rapla-client-web/` or a clearly-defined home. Could proceed against single-module by living under `src/main/angular/` — less clean. |
