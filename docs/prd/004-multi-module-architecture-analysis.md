# PRD 004: Multi-Module Architecture Analysis

**Status:** decided — implementation in [PRD 005](005-multi-module-split.md), reactor live on `spring-boot` branch since 2026-05-07. **Decision document, kept for future reference.**
**Date:** 2026-05-06

## Goal

Evaluate the best **module structure and build layout** for the Rapla codebase as it absorbs three converging pressures:

1. **Spring Boot migration (PRD 001)** has already turned the project into a single fat JAR with a server `@SpringBootApplication`, a Swing client driven by a separate Spring `AnnotationConfigApplicationContext` (Phase 4 partial), and JNLP delivery preserved.
2. **Custom deployments (PRD 003, e.g. `../dhbwrapla`)** need a clean way to depend on a *library* form of Rapla — not a WAR overlay — and contribute their own beans, config, REST endpoints, and Swing UI.
3. **A future Angular client** is expected to take over the main frontend role from Swing, consuming the same REST API the Swing client uses.

This PRD does **not** prescribe an implementation plan. It compares options across three independent axes — module layout, build tool, and frontend integration strategy — and lands on a recommended target architecture so PRDs 005+ can flesh out individual phases.

## Current State (what's actually true today)

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

So we have *logical* multi-module structure (parent + main + custom + container aggregator) but a **single compiled artifact** (the rapla JAR). All ~1100 `.java` files compile together into one classpath.

### Logical separation already encoded in packages

The source tree already uses these conventions consistently:

- `org.rapla.client.*` — client-side classes (toolkit-agnostic)
- `org.rapla.client.swing.*` — Swing-specific implementations
- `org.rapla.server.*` — server-side classes
- `org.rapla.server.spring.*` — Spring Boot wiring (added by PRD 001)
- `org.rapla.entities.*`, `org.rapla.facade.*`, `org.rapla.framework.*`, `org.rapla.scheduler.*`, `org.rapla.storage.*`, `org.rapla.components.*`, `org.rapla.logger.*`, `org.rapla.rest.*` — shared / "core"
- `org.rapla.plugin.<name>.{client,client/swing,server}` — every plugin uses this triplet

So the *intent* of a client/server/core split has been there all along; only the build layer doesn't enforce it. Today, nothing prevents a server class from importing a Swing class — only convention does.

### What's already client-only or server-only at runtime

- **Server-only:** Spring Boot starters, Tomcat, Spring Security, JWT (nimbus-jose-jwt), the REST controllers, `ServerServiceContainer`, Exchange Web Services, iCal4j (server-side serializers), JDBC drivers, mail
- **Client-only:** Swing, AWT, JLayer, calendar widgets, `RaplaClient`/`MainWebclient` bootstrap
- **Shared:** Entity model, RxJava promises, framework, i18n, logger, REST DTOs, RaplaFacade, scheduler

This division is observable today by inspecting which packages each subset of classes touches. A multi-module build would just **enforce** what's already conventionally true.

### Frontend assets that already exist

- `src/main/resources/static/Rapla/` and `src/main/resources/static/jsclient/` — small jQuery-based JS UI (date pickers, jstree, etc.) shipped by the existing servlet pages
- `src/main/resources/static/rapla.html`, `apiTest.html`, `redirect.html` — static HTML pages
- No Angular, no React, no TypeScript build pipeline today
- The Swing client is **the** main frontend; the JS bits are accessory (calendar embeds, redirects, terminal displays)

## Decision dimensions

The three orthogonal questions:

| Axis | Choices | Coupling |
|------|---------|----------|
| **A. Module layout** | Single module / Maven multi-module / multi-repo | Independent of B and C — but multi-module is a prerequisite for cleanly publishing client-only and server-only artifacts to Maven Central |
| **B. Build tool** | Maven (current) / Gradle / Bazel | Mostly independent of A; Maven multi-module is well-supported, Gradle multi-project too |
| **C. Frontend integration** | Bundle Angular in fat JAR / separate Angular module producing static assets / standalone Angular SPA repo | Coupled to A — the answer changes if there are real modules to attach to |

## Axis A — Module Layout

### Option A1: Stay single-module

**Shape:** keep today's `pom.xml` + `parent` + `custom` setup. Custom deployments depend on the rapla JAR; client/server separation remains convention-only.

**Pros:**
- Zero migration cost
- Single `mvn compile` covers everything
- Works *today* — PRD 001 lands without any module restructure
- Plugin authors don't have to pick a module to contribute to

**Cons:**
- Custom deployments pull in **all** server-side dependencies even if they only need the client (Spring Security, Tomcat, Exchange Web Services, JDBC drivers — currently ~50 MB of transitive deps a Swing-only consumer doesn't need)
- Nothing prevents accidental cross-layer imports (e.g. a server service touching `javax.swing.JFrame` — once shipped, painful to undo)
- JNLP `webclient/` set today includes server classes that the Swing client never loads (because everything is in one JAR) — wastes download bytes
- Angular module would have to live *somewhere*; without modules, "alongside `src/main/resources/static/`" is the only answer (cramped)
- Publishing to Maven Central as `rapla-client` / `rapla-server` requires post-hoc filtering with `maven-shade-plugin` — fragile

### Option A2: Maven multi-module split

**Shape:** turn the existing `master/pom.xml` aggregator into a real reactor with one module per logical layer. Suggested split (**5 modules**):

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

A finer split (separate `rapla-entities`, `rapla-rest-api`, per-plugin modules, or splitting client/swing apart) is possible but **not justified by current or planned consumers** — see "Why not split rapla-client into two modules?" below.

#### Why not split rapla-client into two modules?

The source already maintains a clean package-level convention:

| | `org.rapla.client.*` (top level, 19 files) | `org.rapla.client.swing.*` (hundreds of files) |
|---|---|---|
| Role | Presenters + view interfaces — *what* the app does | Swing rendering of view interfaces — *how* it draws |
| `javax.swing` imports | **0** (verified by grep) | many |
| Example | `CalendarPlacePresenter`, `ApplicationView` (interface), `EditController` | `RaplaGUIComponent`, `EditField`, `OptionPanel` |

The convention holds at the top level and leaks one level deeper (files under `client/internal/edit/`, `client/dialog/`, etc. import from `client/swing/*`). **Keep this package-level split**; it documents intent and is cheap to maintain.

**Don't split it into two Maven modules.** A separate `rapla-client` artifact would only be useful if a second *Java* client consumed it — e.g. a JavaFX desktop client, a headless Java integration client, an SWT port. None of those exist or are on the roadmap.

**Angular is not such a consumer.** Angular consumes the REST API over HTTP; it doesn't `import` Java classes. What Angular actually needs from the Java side is the **wire contract** (REST DTOs and endpoint definitions), which lives in `rapla-core` and is consumed via:

- An OpenAPI spec generated at runtime by `springdoc-openapi` (one extra dependency in `rapla-server`)
- A code-gen step on the Angular side (`openapi-generator-cli` produces TypeScript clients)
- Optional build-time generation of i18n JSON from `.properties` (cross-language string sharing)

None of that requires `rapla-client` to exist as a separately-published Maven artifact. Splitting it now creates two modules where one suffices, with no measurable consumer-side win.

**If a second Java client ever materialises**, splitting `rapla-client` from `rapla-client-swing` becomes a 1–2 day refactor against a known-clean top-level boundary. Defer until that day.

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

**Verified by grep:** `org.rapla.server.*` imports from `org.rapla.client.*` only **2 times** (`ClientService`, `RaplaClientListenerAdapter`) — both interfaces, both trivially moved to `rapla-core`. `org.rapla.client.*` imports from `org.rapla.server.*` zero times. The "server depends on core only" edge is honest, not aspirational.

**Pros:**
- Custom deployments declare exactly what they need: `rapla-client` for a Swing-only addon, `rapla-server` for backend-only, both for full deployments
- Build-time enforcement of layering — server can't import Swing, client can't import JDBC
- Maven Central publishing becomes natural (`org.rapla:rapla-core:2.1`, `org.rapla:rapla-client:2.1`, etc.) — opens the door to third-party integrations
- JNLP `webclient/` set is well-defined: `rapla-core` + `rapla-client` + `rapla-client-swing` + transitive client deps. No accidental server classes shipped to the desktop
- Angular module slots in cleanly as `rapla-client-web/` (peer of `rapla-client-swing`) — same client API surface, different rendering
- Per-module test execution (`mvn -pl rapla-server test`) — faster feedback loops
- Forces explicit handling of currently-implicit cross-cutting beans (e.g. `RaplaFacade` injected into both Swing and REST contexts)

**Cons:**
- One-time migration cost: every package import that crosses the new boundaries needs reviewing. Probably 100–300 cycle violations to break (educated guess; needs an `archunit` audit to know for sure)
- Cyclic dependencies that are tolerable in a single jar become hard errors in a reactor build — and there *are* cycles between `client/internal/*` and `client/swing/*` packages today
- Plugin authors must choose a module per source file. The current `plugin/<name>/{client,server}` sub-tree pattern would split each plugin across at least two new modules (or each plugin becomes its own module)
- IDE setup is slightly more involved (multiple modules vs one)
- `dhbwrapla` is fundamentally a *single* deployment; splitting it to mirror would be over-engineering. It stays single-module, just depending on multiple rapla artifacts

### Option A3: Multi-repo

**Shape:** `rapla-core`, `rapla-client`, `rapla-server`, `rapla-swing`, `rapla-angular`, `rapla-app` each in its own git repo, each releasing its own version, depending on each other via Maven coordinates.

**Pros:**
- Strongest isolation; each repo can have its own release cadence, contributors, CI
- Forces tight, versioned API contracts at every boundary

**Cons:**
- A change touching three layers requires three PRs across three repos — punishing for a small team
- Cross-repo refactoring is painful (rename a method in `rapla-core`, version-bump and PR in every consumer)
- Currently the team has effectively two repos (`rapla` + `dhbwrapla`); going to six is a leap
- Most of the wins (build-time layering, separate publishable artifacts) are already delivered by Option A2 without the coordination tax

**Verdict on A:** **Option A2 (Maven multi-module).** A1 leaves real wins on the table (custom-deployment dep weight, layer enforcement, Angular slot) and A3 is too heavy for the team size and change frequency.

## Axis B — Build Tool

| | Maven (current) | Gradle | Bazel |
|---|---|---|---|
| **Multi-module support** | First-class (reactor) | First-class (multi-project) | First-class (workspace) |
| **Spring Boot integration** | Mature, official `spring-boot-maven-plugin` | Mature, official `org.springframework.boot` plugin | Possible, more setup |
| **Incremental builds** | Limited (`-pl`/`-amd`/incremental compile) | Strong (build cache, configuration cache, incremental tasks) | Strongest (hermetic, remote cache) |
| **Plugin ecosystem (jarsigner, JNLP, archetype, license, GPG)** | Complete | Complete | Sparse |
| **Custom-build complexity** | Declarative XML — verbose but predictable | Imperative Groovy/Kotlin DSL — flexible but easy to over-engineer | Bzl/Starlark — steepest learning curve |
| **PRD 003 custom-deployment ergonomics** | `<dependency>` + `<parent>` is well-understood | Same model, slightly nicer DSL | Forces consumers onto Bazel — non-starter for downstream Maven consumers |
| **Concurrent invocations on the *same* workspace / `target/` directory** | **Unsafe — and silently so.** Maven holds no project-level lock. Two `mvn compile` runs race on `target/classes/`; `mvn compile` + `mvn test` overlapping causes `NoClassDefFoundError` / stale-class failures; two `mvn package` runs can truncate the same `target/*.jar` mid-write. The local-repo install step *does* take a per-artifact lock, so `~/.m2/repository` stays consistent — but mismatched artifact versions from interleaved builds can still land there. | **Unsafe but loud.** Gradle takes an explicit project lock; the second invocation blocks with "Waiting for an exclusive lock on project '…'". Slower (serialised) but correct — no corruption. Daemon contention still applies on top of this. | Same workspace serialises on the output-base lock — same "blocks but doesn't corrupt" semantics as Gradle. |
| **Concurrent invocations on *different worktrees* of the same repo (the recommended pattern)** | **Excellent.** Each `git worktree` has its own absolute path → its own `target/`. The only shared state is `~/.m2/repository`, which has proper per-artifact locks. This is where Maven's stateless model shines for parallel agents. | Better than same-workspace but not great. The Gradle daemon keys project state by absolute path so worktrees look like separate projects, but the daemon JVM is still shared (memory pressure) and `~/.gradle/caches/` is contended. A poisoned daemon affects every in-flight build across worktrees. | Excellent if each worktree has its own output base; a remote cache amplifies the win. |
| **Migration cost from current Maven setup** | None | Moderate — POMs convert mechanically with `gradle init`, but the jarsigner/JNLP/Spring-Boot/sign-pkcs11 profiles need rewriting | High — full rewrite of build files, no automated migration |
| **CI/IDE familiarity for the team** | Already in use | New | New |

**Verdict on B:** **Stay on Maven.** Three concrete reasons, in order of weight:

1. **Maven fits a parallel-agent-per-worktree workflow better than Gradle does.** Important nuance: on the *same* workspace, **Maven is actually less safe than Gradle** — it has no project-level lock, so concurrent `mvn` runs race on `target/` and silently corrupt class files / JARs, while Gradle blocks with an explicit project-lock message. The recommended pattern is therefore one git worktree per agent, not parallel invocations against one checkout. **In the worktree pattern, Maven wins decisively:** each worktree gets its own `target/`, and only `~/.m2/repository` is shared (proper per-artifact locks). Gradle in worktrees still funnels through one shared daemon JVM and one `~/.gradle/caches/`, so a poisoned daemon or cache-write contention affects every in-flight build. As the team shifts toward agent-assisted development with worktrees, **the build tool's behaviour under concurrent worktree invocation matters more than its incremental-build speed.**
2. **Migration cost is real and the payoff is small.** Gradle's incremental-build advantage is 10–30 % on a multi-module reactor — not worth rewriting four signing profiles, the Spring Boot packaging, the assembly descriptor, and the JNLP plumbing while PRD 001 is mid-migration.
3. **Bazel forces consumers.** Pushing downstream `dhbwrapla` (and any future custom deployment) onto Bazel is a non-starter.

**Reconsider Gradle in 12–18 months** only if reactor build times become a developer-experience pain point *and* the parallel-agent friction has been characterised on real workloads. **Mitigation if Gradle is ever adopted:** standardise on `--no-daemon` for agent and CI runs, give each worktree its own `GRADLE_USER_HOME`, and accept the cold-start tax as the cost of safe concurrency.

## Axis C — Angular Client Integration

The Angular client consumes the same REST API the Swing client uses (already exists post-PRD 001 — `RaplaResourcesController`, `RaplaEventsController`, etc.) and progressively takes over functionality.

**Angular's relationship to the Java reactor:** none at compile time. Angular has its own presenter logic in TypeScript and doesn't `import` Java code. What it needs from the Java side:

1. **Wire contract (REST DTOs and endpoints).** Add `springdoc-openapi` to `rapla-server` to generate `/v3/api-docs` (an OpenAPI 3 spec). The Angular build runs `openapi-generator-cli` against that spec to produce typed TypeScript clients (`RaplaResourcesService`, `RaplaEventsService`, etc.) — generated code, not hand-written.
2. **i18n strings.** Generate `.json` translation files from the existing `RaplaResources.properties` at build time so translators have one source of truth. A small Maven step in `rapla-app` (or `rapla-client-web`) does this.
3. **Auth token format.** JWT (PRD 001 Phase 3) — Angular puts it in `Authorization: Bearer …`; same flow as the Swing client.

That's the entire dependency surface. No Maven dependency on `rapla-client`, `rapla-server`, or anything else from the Java side at compile time.

### Option C1: Bundle Angular build inside `rapla-app` fat JAR

**Shape:** Angular project lives at `rapla-client-web/` (peer of `rapla-client-swing/`). Build pipeline: `frontend-maven-plugin` runs `npm install && ng build --output-path target/dist`, then `maven-resources-plugin` copies to `src/main/resources/static/app/`. Spring Boot serves `/app/index.html` from the fat JAR.

**Pros:**
- Single deployable unit — one `java -jar rapla-app.jar` boots backend + serves the SPA
- No CORS concerns (same origin)
- Versions move in lockstep — no "frontend says v3, backend is v2" drift
- Existing reverse proxy / OpenWebStart URL space is preserved

**Cons:**
- Fat JAR grows by Angular bundle size (~2–5 MB compressed)
- Frontend dev cycle is slower (`mvn package` instead of `ng serve`) — mitigated by `ng serve` in dev with a proxy to localhost:8051

### Option C2: Separate `rapla-client-web/` module producing a static-assets JAR

**Shape:** Same source layout as C1, but the Angular build produces `rapla-client-web-2.1.jar` (a JAR containing only `META-INF/resources/app/**`). `rapla-app` declares it as a runtime dependency. Spring Boot's `META-INF/resources/` convention serves it automatically.

**Pros:**
- Custom deployments can choose to ship the Angular client or not, independently of choosing the server
- The Angular artifact can be republished independently for a frontend-only patch
- Cleaner separation: the Angular module owns its own `package.json`, `node_modules` cache, CI lane

**Cons:**
- Slightly more complex build wiring
- Versions can drift (mitigate via the BOM)

### Option C3: Standalone Angular repo, deployed independently

**Shape:** Angular lives in `rapla-web/` — a separate git repo, deployed to a CDN or static-hosting bucket. Talks to the rapla server via CORS-enabled REST.

**Pros:**
- Frontend team can iterate without touching Java repo
- Different release cadence
- CDN-served assets, edge caching, etc.

**Cons:**
- CORS, auth-cookie, and JWT-cookie domain configuration
- Two-repo coordination for breaking API changes
- Custom deployments now need to deploy *and* host two artifacts
- Premature for current scale — there is no separate frontend team

**Verdict on C:** **Option C2** as the target, falling back to **C1** if the build-step complexity isn't worth it. Both keep the Angular client first-class and inside the reactor; the only difference is whether the build artifact is bundled into the rapla-app fat JAR or shipped as a separable JAR. Either way the Angular project lives at `rapla-client-web/` as a peer of `rapla-client-swing/`. Avoid C3 until there is a separate frontend team or a CDN strategy.

### Notes on the Swing → Angular transition

- The Swing client and Angular client should **co-exist for a long time** — Swing is the only path to many advanced features today (admin tools, plugin option panels, complex reservation editors). Angular can take over **read-heavy / mobile-friendly / public-facing** flows first (calendar views, room finder, conflict viewer).
- Both share the same REST API surface (`/resources`, `/events`, `/dynamictypes`, `/auth/login`). Any new endpoint should be designed REST-first to serve both.
- Plugin extensions need to grow a "web-client" sibling alongside the existing "client/swing" tree — `plugin/<name>/client/web/` analogous to `plugin/<name>/client/swing/`. Each plugin can choose to ship a Swing impl, a web impl, both, or neither.
- The `PluginOptionPanel` extension point is Swing-specific. A web equivalent will be needed (a separate `WebPluginOptionPanel` extension point, or a polymorphic one keyed by render target).

## Custom Deployment Fit (relation to PRD 003)

PRD 003 already lays out how `dhbwrapla` migrates to Spring Boot. A multi-module split makes that simpler:

| PRD 003 concern | Single-module today | Multi-module (this PRD) |
|---|---|---|
| Custom project parent POM | `org.rapla:custom` (WAR overlay) | `org.rapla:rapla-archetype` (Maven archetype) — or just declare deps directly |
| Custom project dependencies | `org.rapla:rapla` (everything) | `org.rapla:rapla-server` + `org.rapla:rapla-client` (or only what's needed) |
| Pulling in Spring Security for a Swing-only customization | Forced | Avoided — declare `rapla-client` only |
| Pulling in Swing for a server-only customization | Forced | Avoided — declare `rapla-server` only |
| `@Import(RaplaSpringBootApplication.class)` foot-gun (PRD 003 OQ6) | Required | Replaced by depending on `rapla-server` which exposes a `RaplaServerAutoConfiguration` — clean Spring Boot auto-config pattern |
| JNLP `webclient/` content | All rapla classes | `rapla-core` + `rapla-client` + custom JAR — well-defined, smaller |
| Custom Angular extensions | No place to put them | Custom Angular code lives next to `rapla-client-web/` (separate npm project under `dhbwrapla/web/` or similar) — consumes the same OpenAPI spec |

So the multi-module split is **not just an internal cleanup** — it directly resolves three of PRD 003's open questions (OQ2 archetype, OQ5 client `@ComponentScan` composition, OQ6 auto-configuration).

## Recommendation (proposed target architecture)

1. **Module layout (A):** Maven multi-module reactor with **5 modules**: `rapla-bom`, `rapla-core`, `rapla-client` (Swing included), `rapla-server`, `rapla-app`. Optional `rapla-archetype`. When Angular work begins, add `rapla-client-web/` as a peer (npm-built, no Maven dep on the other client modules). Splitting `rapla-client` apart from `rapla-client-swing` is **not** part of this recommendation — the package convention inside `rapla-client` already documents the boundary, and the split has no Java consumer (Angular consumes REST, not Java code). If a second Java client ever materialises (JavaFX etc.), revisit then.

2. **Build tool (B):** **Stay on Maven**, and **standardise on one git worktree per agent** for parallel work. Maven's stateless per-invocation model is a strong fit for the worktree pattern (each worktree has its own `target/`, only `~/.m2/repository` is shared). It is *not* safe for concurrent builds in a single workspace — neither is Gradle in that pattern, but Gradle at least blocks rather than corrupting. Reconsider Gradle in 12–18 months only if reactor times become painful *and* the parallel-agent friction has been characterised on real workloads.

3. **Frontend (C):** Angular lives at `rapla-client-web/` inside the reactor. Build artifact is a static-assets JAR consumed by `rapla-app` (Option C2). Falls back to bundling under `static/app/` (Option C1) if the JAR-of-resources pattern adds friction. **Not** a separate repo (C3).

4. **Angular and Swing coexist indefinitely.** No deprecation of Swing as part of this PRD. Angular takes over user-facing flows first; admin/plugin-config flows stay in Swing until a web equivalent ships.

5. **`dhbwrapla` stays single-module** — it's one deployment, not a platform. It just depends on the new `rapla-server` and `rapla-client-swing` artifacts instead of the monolithic `rapla` JAR.

## Target Directory Structure

The recommendation above translates to this concrete tree. Module count and source layout are independent of the build tool — only the build descriptors change between Maven and Gradle (see "Gradle delta" at the end of this section).

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

Source tree, module count, and module names are **identical** under Gradle. Only the build descriptors change. Captured here so a future Gradle reconsideration knows exactly what's involved:

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

The Angular module (`rapla-client-web/`) is identical under both — it's an npm project regardless. The Java build tool only orchestrates `npm install` + `npm run build`. The **dhbwrapla side stays Maven either way**, since Gradle publishes plain Maven coordinates — Gradle in rapla doesn't force consumers to migrate. (Bazel would; that's the structural reason Bazel is ruled out in Axis B.)

So: same shape, same source, only the build descriptors differ. The cost of switching is in those descriptors — especially the four signing/JNLP profiles — not in the layout.

## Risks

1. **Cycle breaking is unbounded.** Until an `archunit` (or `jdeps`) pass quantifies cross-package cycles in the current code, the migration cost in Option A2 is unknown. **Action before commitment:** run `jdeps -e 'org.rapla.*' -dotoutput out/ target/classes` and grep for cycles between proposed module boundaries. If <50 cycles, the split is a 1–2 week task; if >300, it's a quarter.

2. **Plugin layout decision is load-bearing.** Splitting each plugin across `rapla-client`, `rapla-client-swing`, `rapla-server` is conceptually clean but creates ~30 plugins × 3 modules = 90 sub-modules — too many. The realistic options are: (a) keep plugins as packages inside the three main modules (chosen here), or (b) make each plugin one module that internally has client/server source roots (`src/client/java`, `src/server/java`). Option (a) is simpler; option (b) lets a single plugin be published independently. **Defer this decision** until at least one plugin needs to be released independently.

3. **PRD 001 is mid-migration.** Restructuring modules while the Spring Boot port is still in Phase 4–7 multiplies merge conflicts. **Sequence:** finish PRD 001 (at least Phases 1–7 + 8 cleanup), then start the multi-module split. Don't interleave.

4. **Angular learning curve.** The team has no Angular code today. Any C-axis decision should include a small spike (a single page consuming `/resources`) before committing to a framework. **Alternative:** evaluate React, Vue, Svelte, or HTMX in the same spike — Angular is a reasonable default but not the only viable choice.

5. **JNLP signing across modules.** With `webclient/` content sourced from multiple Maven artifacts (`rapla-core`, `rapla-client`, `rapla-client-swing`), the signing pass needs to operate on the assembled set in `rapla-app` or `dhbwrapla`'s build — not in each module. PRD 003's signing-chain section already assumes this; multi-module just makes the source-of-jars more explicit.

6. **Maven Central coordinates.** Once `rapla-client` and `rapla-server` are independent published artifacts, version skew between them in downstream consumers becomes possible. **Mitigate** by publishing `rapla-bom` and requiring consumers to import it. dhbwrapla and any other custom deployment imports the BOM and never specifies versions for individual rapla artifacts.

## Open Questions

1. **Do we want each plugin as its own module?** See Risk 2. Recommended answer: not yet.

2. **Where does `org.rapla.components.*` go?** It's a grab-bag (i18n, calendarview, util). Some pieces (`calendarview/swing`) are clearly client-Swing; some (`i18n`) are core. Either split it across modules during the migration, or treat it as part of `rapla-core` and accept that it pulls in some Swing types. **Recommendation:** split — i18n + util to `rapla-core`, `calendarview/swing` to `rapla-client`.

3. **Angular framework choice.** Angular vs React vs Vue vs Svelte vs HTMX. The user named Angular, so this PRD assumes Angular, but the spike in Risk 4 should validate before committing.

4. **Authentication for the Angular client.** JWT (already used by Swing client per PRD 001 Phase 3), or session cookies? JWT is consistent with Swing today; cookies are easier for SPAs. **Recommendation:** JWT in `Authorization: Bearer` header, stored in `sessionStorage` (not `localStorage`). Decide before C-work begins.

5. **i18n source of truth.** The Swing client uses `RaplaResources` (Java `ResourceBundle`-based). Angular needs translations too — share the `.properties` files via a build step, or maintain separate `.json` translations? **Recommendation:** generate `.json` from the existing `.properties` at build time so translators have one source of truth.

6. **Should `rapla-archetype` actually be created, or is a documented `pom.xml` template enough?** PRD 003 leaned toward "documentation only — likely 1–2 custom deployments." This PRD doesn't change that — `rapla-archetype` is listed as *optional*.

7. **Repository layout.** Stay in one repo (`rapla/`) with a 6-module reactor, or split each module to its own repo (Option A3)? **Recommendation:** stay in one repo. If a single module starts attracting external contributors at a different cadence, split *that one out* later.

## Dependencies on Other PRDs

| PRD | Relationship |
|-----|-------------|
| **001: Spring Boot Migration** | **Hard prerequisite.** Wait for PRD 001 Phases 1–7 + 8 cleanup before starting any module split. Restructuring during PRD 001 is a merge-conflict trap (Risk 3). |
| **001-A: Date → LocalDateTime** | Independent. Can proceed in parallel; touches signatures inside `rapla-core` and won't move once modules exist. |
| **002: Multi-Tenancy** | Independent. `TenantAwareFacade` lives in `rapla-server`; tenant-routing concerns don't cross module boundaries. |
| **003: Custom Deployments** | **Bidirectional.** PRD 003 assumes a single-module rapla today; this PRD's recommendation simplifies three of PRD 003's open questions (OQ2, OQ5, OQ6). If this PRD is approved, PRD 003 should be updated to target the multi-module shape. If this PRD is deferred, PRD 003 proceeds against the single-module rapla and is not blocked. |
| **(future) PRD 005: Multi-Module Split** | This PRD's recommendation is the input. PRD 005 would lay out the actual phased split: cycle audit, module creation, package moves, dependency declarations, CI updates. |
| **(future) PRD 006: Angular Client** | Depends on PRD 005 having created `rapla-client-web/` or having a clearly-defined home for the Angular code. Could also proceed against single-module rapla by living under `src/main/angular/` — less clean. |
