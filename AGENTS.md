# AGENTS.md - Opencode Rules

## Project Overview

**Rapla** is a Java-based resource scheduling and event planning application (v2.1-SNAPSHOT, AGPL/Apache2). It uses Maven, targets Java 17, runs on Java 21. Key technologies: Spring Boot 3.2 (Tomcat 10), Swing, JAX-RS / Spring MVC, RxJava3, iCal4j, Exchange Web Services.

The codebase is a **5-module Maven reactor** (PRD 005, 2026-05-07):

| Module | Role |
|---|---|
| `rapla-bom` | BOM + parent POM (versions, plugin config). Was `parent/`. |
| `rapla-core` | Shared layer: entities, facade, framework, scheduler, storage interfaces, REST DTOs/endpoint interfaces, components/{util,layout,restproxy,i18n}, logger, inject. NO Spring Boot, NO Swing. |
| `rapla-client` | Swing client + presenters: `org.rapla.client.*`, components/{calendar,calendarview,iolayer,tablesorter,treetable}, plugin `*/client/*` and `*/swing/*`. Uses `spring-context` only — explicit `AnnotationConfigApplicationContext`, NOT `@SpringBootApplication`. Depends on rapla-core. |
| `rapla-server` | Server: `org.rapla.server.*` (excl. the `RaplaSpringBootApplication` entry point), JDBC storage, REST controllers, Spring autoconfig (`META-INF/spring/AutoConfiguration.imports`), plugin `*/server/*`. Depends on rapla-core + rapla-client (known compromise — see PRD 005 D3 for the abstractcalendar/RaplaBuilder coupling). |
| `rapla-app` | Runnable Spring Boot application: `RaplaSpringBootApplication`, `application.yml`, `src/assembly/`, `src/main/distribution/`, signing profiles, JNLP webclient/ staging. Produces `rapla-2.1-SNAPSHOT.jar` (Spring Boot fat JAR). |

The repo-root `pom.xml` is the reactor aggregator (artifactId `rapla-aggregator`, packaging=pom, lists all five modules).
`custom/` is intentionally NOT in the reactor (its WAR-overlay shape is being rethought; future PRD).

**Build & Test:**
- Reactor compile: `mvn compile`
- Reactor test: `mvn test`
- Per-module compile: `mvn -pl rapla-server -am compile`  *(use `-am` to also build module deps in-tree; otherwise Maven looks in `~/.m2/repository`)*
- Per-module test: `mvn -pl rapla-server -am test`
- Targeted test: `mvn -pl rapla-app -am test -Dtest=RaplaSpringBootApplicationTest`
- Run the dev server: `mvn -pl rapla-app -am spring-boot:run -Dspring-boot.run.fork=false` *(must use `-am` from repo root — see §8 hard rules; do not `cd rapla-app`, do not `mvn install`)*
- Full server lifecycle (background, PID/logs, graceful stop): see §8 below
- Test the deployable fat JAR + signed JNLP webclient: load the **`test-deployment`** skill
- Probe the running server's REST API directly (login, getResources, queryAppointments, etc.) — load the **`api-testing`** skill
- Requires SDKMAN (Java 21 + Maven) on WSL2 Ubuntu

The reactor aggregator (`pom.xml` at the repo root, packaging=pom, artifactId=`rapla-aggregator`) lists the 5 module siblings. Running `mvn` from the repo root walks the whole reactor.

## Rules

### 1. Test-First Approach

The order is **(a) understand → (b) write failing test → (c) fix → (d) verify test passes**, then commit. Skipping the test step for "obvious" fixes is the most common form of slippage in this codebase — don't.

- **Write the failing test before any production code change** for: bug fixes (every one — including 1-line ones), new features, behaviour changes, refactors that should preserve observable behaviour. Run `mvn test` to confirm it fails for the right reason, then fix, then re-run to confirm it passes. Tests live in `src/test/java/` mirroring the source package.
- **Diagnostics-first is fine when** you're still locating the root cause — curl probes, log inspection, exploratory println/diagnostic dumps in a test, integration-test bisects, reading code. Once you can name the broken function/field/method, switch to test-first for the fix.
- **No exception for "trivial" fixes.** A removed `final` keyword, a missing null-check, a typo'd config key — all bug fixes get a regression test. The point isn't to verify the fix works; it's to lock the fix in so the next refactor doesn't reopen the bug. The Jackson-3 `final`-field bugs (PRD 011 follow-up, 2026-05-09) reopened the same pattern five times across different fields — a per-bug regression test would have caught the second one immediately.
- **Verifying the test would catch the bug:** After writing the fix and seeing the test go green, briefly revert the fix and re-run the test to confirm it goes red for the right reason. Re-apply the fix. This costs one extra test run and prevents tests that pass for the wrong reason (e.g. asserting on a field that gets initialized in setUp regardless of the bug).

### 2. PRD-Driven Development
- Before implementing anything, check **both `docs/prd/` AND `docs/prd/done/`** for an existing PRD. The `done/` subfolder holds completed PRDs — read them too; they capture decisions and alternatives already considered.
- Matching PRD in `docs/prd/`: read, update, plan there.
- Matching PRD in `docs/prd/done/`: `git mv` it back to `docs/prd/`, flip status to `in-progress`, then add new work. The move signals the PRD is reopened.
- No matching PRD: create one in `docs/prd/` before writing code.
- When a PRD is fully complete (status `done`, all phases shipped): `git mv` to `docs/prd/done/` and update cross-references in still-active PRDs.

### 3. PRD Format
- File naming: `docs/prd/NNN-short-name.md` (e.g., `001-spring-boot-migration.md`).
- Required sections: Title, Status (draft / in-progress / done), Goal, Scope, Plan, Tests, Open Questions.
- Keep PRDs concise. Update the status as work progresses.

### 4. Code Style
- No comments unless explicitly requested.
- Follow existing code conventions in the codebase.
- **Use constructor injection, never field injection.** All `@Inject` / `@Autowired` should be on a constructor parameter list, not on a field. New code (including Spring `@Bean` factory methods, `@Component`/`@Service` classes, and ported legacy classes) must use constructor injection. Existing field-injected code may be left alone until it's touched, but any class you edit should be migrated to constructor injection in the same change. Rationale: constructor injection makes dependencies explicit, supports `final` fields, and lets the class be instantiated for tests without a DI container.
- **Spring DI wiring patterns differ between client and server in this codebase.**
  - **Client (rapla-client)**: `SwingClientConfig` has `@ComponentScan(basePackages = {"org.rapla.client", "org.rapla.plugin"}, ...)`. `@Service`/`@Component` annotations on classes in those packages are picked up automatically. Add `@Service` (with optional `("id")` for `Map<String, T>` consumers) to `@DefaultImplementation` / `@Extension` classes when wiring them. This is the dominant pattern for the Swing tier.
  - **Server (rapla-server / rapla-app)**: `RaplaSpringBootApplication` uses the default `@SpringBootApplication` scan, which only covers its own package (`org.rapla.server.spring`). Server-internal classes (`org.rapla.server.internal.*`, `org.rapla.plugin.*.server.*`) are wired via explicit `@Bean` factory methods in `ServerCoreConfig` / `ServerServiceConfig` — most use `new XImpl(...)` + `beanFactory.autowireBean(impl)` for request-scoped or `new XImpl(deps...)` for singletons. Adding `@Service` to a server-internal class without extending the `@ComponentScan` is dead code (Spring won't see it). Extending the scan creates duplicate-bean conflicts with the existing `@Bean` factories. **Until a coordinated refactor flips server wiring to `@ComponentScan` + `@Service` (and removes the matching `@Bean` factories in one sweep), continue the explicit-`@Bean`-factory pattern on the server side.**

### 5. Build Discipline

**Reactor hard rules** (referenced from §8 and §9):
- **NEVER `mvn install`** and **never run rapla JARs from `~/.m2/repository/`**. Both shadow in-reactor `target/classes` for sibling modules with stale code — an hours-of-debugging trap. The reactor's in-tree classpath handles all sibling resolution. Enforced by a `PreToolUse: Bash` hook in `.agents/settings.json` that exit-2's `mvn install` calls — see `.agents/hooks.md` for the wired hooks and how to add more.
- **The `-am` ("also-make") flag is mandatory** for any cross-module compile/test/run. Without it, Maven resolves siblings from `~/.m2/repository`, which is stale or missing.
- Run from the repo root with `-pl <module> -am` — never `cd <module>`.

**Routine workflow:**
- Use `mvn compile` for the post-edit type-check. The repo-root `pom.xml` is the reactor aggregator.
- **Targeted tests only during a session.** `mvn -pl rapla-app -am test -Dtest=ClassName`. Pick tests that exercise the code you just touched (e.g. after editing an XML reader/writer, run XML round-trip tests, not the whole suite).
- **Full `mvn test` only at session end** or when the user asks for a green-build sign-off. Don't checkpoint between iterations — `mvn compile` + targeted tests already cover it. Full reactor takes ~60–120 s with Spring context overhead.
- If a targeted test fails in a way that suggests a wider regression, *then* expand to the full suite — as investigation, not routine.
- **Cross-module test gotcha:** if `mvn -pl rapla-app -am test -Dtest=Foo` test-compiles an upstream module with broken test sources, you get a spurious red. Add `-Dsurefire.failIfNoSpecifiedTests=false` so per-module surefire skips modules where no test matches; or explicitly list modules: `mvn -pl rapla-bom,rapla-core,rapla-server,rapla-app test -Dtest=Foo -Dsurefire.failIfNoSpecifiedTests=false`.
- **Don't `mvn clean` routinely** — incremental compile is reliable. Reach for it only after renaming/moving/deleting classes (stale `.class` files for the old name linger in `target/`). Even hand-off builds don't need clean unless you've moved files this session.

### 6. Git
- Never commit unless explicitly asked.
- Never push unless explicitly asked.
- **Never `git checkout HEAD -- <file>`, `git restore <file>`, or otherwise revert
  user-visible files to a committed state without explicit user approval.** Restoring
  silently destroys session work — yours and the user's. If a file looks broken and
  you're tempted to "reset and start over," ASK FIRST. The exception is files YOU just
  edited in the same turn (you can revert your own immediate edit). Anything older —
  including files edited earlier this session, files modified by other sessions, files
  that arrived via a script — needs an explicit "yes, restore X" from the user.
- **Before ending a session, update outdated PRDs.** Any PRD whose Plan, Open Questions, or Status no longer matches what's actually in the codebase (because of work landed during the session) gets a brief edit reflecting the new reality — close the resolved OQs, mark phases done/in-progress, note any direction changes. PRDs are the long-term context for future sessions; if they're stale, the next session re-litigates decisions you already made.

### 6a. Bulk-refactor scripts — see the `bulk-refactor-scripts` skill

Before writing a script that mechanically rewrites Java sources across the
reactor (cross-module rename, signature-regex sweep, diff-based recovery),
load the **`bulk-refactor-scripts`** skill. It encodes the scars from the
2026-05-09 Date → LocalDateTime migration — anchor rules for signature
regexes, the SequenceMatcher diff-recovery trap, the no-wrapper rule for
entity/facade/storage tier, compile-after-every-script discipline.

### 7. Parallel Work — Use a Git Worktree

If you may run in parallel with another agent, or you need a long-running dev server alongside an existing one, **work in your own git worktree** instead of the canonical checkout. The full protocol (creation, port allocation, sharing model, hard rules) lives in the **`git-worktrees`** skill at `.agents/skills/git-worktrees/SKILL.md` — load it before doing anything that touches a worktree. On a quiet `master`/main with no other agent active, you can ignore this rule.

**Compile errors in files you didn't edit:** If `mvn compile` surfaces an error in a file that's not on your change list, before reaching for a fix, check whether another agent (or the user via a linter) modified it concurrently. `stat -c '%Y %y' <file>` against `git log -1 --format=%ct -- <file>` shows the working-copy mtime vs the last-commit time — if the working-copy is newer than your session start *and* you didn't touch it, it's a parallel edit. **Don't fix it.** Surface it to the user and ask whether to wait for the parallel work to land or coordinate. Parallel-edit fixes risk reverting in-flight refactors and step on the other agent's work.

**Hard rule: NEVER fix or revert work in files another session is editing.** Even when the failure looks trivial (one missing import), looks like a transitive consequence of your own change, or the fix is in a config class you've already touched. Other sessions' working-copy files are snapshots, not finished work — touching them produces merge conflicts or reverts in-progress work.

If a parallel-session change broke your build: confirm via the mtime check above, then either move to unrelated work or stop and tell the user. **Don't stash, `git checkout`, or otherwise discard your own changes** to "let theirs land cleanly" — that loses your work or creates merge conflicts later. Stay out of their files; keep your own.

### 8. Server lifecycle — start, stop, restart, inspect

The dev server is a Spring Boot application started via `mvn spring-boot:run` (no package step needed — runs from `target/classes`). Per §7 port convention, the canonical checkout binds **8051**; worktree N uses `8051 + 10·N`. PID/log file paths below assume the canonical checkout — substitute `logs/rapla-N.{pid,log}` in worktrees so multiple servers don't fight for the same files.

**Hard rules:** never `mvn install`; never run rapla JARs from `~/.m2/repository/` (both shadow in-reactor `target/classes` with stale code — see §5). Always run from the repo root with `-pl rapla-app -am`, never `cd rapla-app`.

> **Testing the deployable fat JAR (`mvn package` + signed JNLP webclient/) is a separate concern** — see the **`test-deployment`** skill at `.agents/skills/test-deployment/SKILL.md`. AGENTS.md only covers the dev server.

#### How AI agents should start the server (avoiding stalls)

The Bash tool waits for the spawned process to exit. A long-running server started in the foreground hangs the agent forever. **The right pattern is `run_in_background=true` on the Bash tool call** — the tool spawns the process, returns a shell ID immediately, and the agent keeps working.

**Reliable recipe (lessons learned 2026-05-13):**

```bash
# 1. ABSOLUTE PATHS — the Bash tool's CWD persists between calls but can shift
#    if any earlier command `cd`-ed elsewhere. Don't rely on relative paths
#    here; use either `-f /home/chris/git/rapla/pom.xml` or explicitly
#    `cd /home/chris/git/rapla &&` at the start of THIS command.
# 2. SEPARATE STOP FROM START — never chain `pkill … ; sleep … ; mvn … &` in
#    the same Bash invocation. The pkill races with the new spring-boot:run
#    inside the same backgrounded shell — the new server gets SIGTERM'd
#    right after startup.
# 3. ABSOLUTE log path — `> /home/chris/git/rapla/logs/rapla.log` so the
#    grep-wait pattern below finds the log regardless of CWD drift.

> /home/chris/git/rapla/logs/rapla.log    # truncate so stale "Started" lines don't match
mvn -f /home/chris/git/rapla/pom.xml -pl rapla-app -am spring-boot:run \
    -Dspring-boot.run.fork=false \
    > /home/chris/git/rapla/logs/rapla.log 2>&1 &
echo "spawned"
```

Run with `run_in_background=true` on the Bash tool call.

`-Dspring-boot.run.fork=false` runs the app in the Maven JVM so the
classpath stays in-reactor (`rapla-{core,client,server,app}/target/classes`)
rather than dropping back to `~/.m2/repository`.
`-pl rapla-app -am` is mandatory.

If you're a shell user (not the Bash tool), use `nohup ... < /dev/null &` + `disown` instead — same effect.

**Confirm startup before issuing requests.** Use the EXACT `Started Rapla`
marker — `Started.*in [0-9.]+ seconds` alone matches older Spring lines or
stale log entries from a previous run:

```bash
# In a separate Bash call (NOT chained to the start above):
timeout 120 sh -c 'until grep -q "Started Rapla.*in [0-9.]\+ seconds" \
    /home/chris/git/rapla/logs/rapla.log 2>/dev/null; do sleep 1; done' \
  && echo READY || echo TIMEOUT
jps -l | grep RaplaSpringBoot   # find the actual JVM PID
```

**Don't rely on `logs/rapla.pid`** for the Bash-tool flow. With
`-Dspring-boot.run.fork=false`, `$!` from a backgrounded `mvn` is the
Maven wrapper PID, not the JVM. The reliable PID source is
`jps -l | grep RaplaSpringBoot`. The PID file pattern in the snippets
below is kept for compatibility with the shell-user case, but for
agents `pkill -f RaplaSpringBootApplication` is simpler and equivalent.

#### Stop / restart / status / log inspection — see the `server-lifecycle` skill

The longer snippets (graceful-shutdown stop, restart procedure, `jps`/HTTP status probes, `tail -F` log streaming, conventions) live in the `server-lifecycle` skill. Load it when managing server state.

Quick essentials that stay inline:
- Stop: `pkill -f RaplaSpringBootApplication` (10 s graceful window — never `kill -9` first).
- One server per checkout (port 8051 binds once); use a worktree per §7 for parallel work.
- Never start the server during a `mvn package` build (`spring-boot:repackage` rewrites the same JAR).

#### Default credentials + REST probing — see the `api-testing` skill

Dev DB ships one admin (`admin` / empty password). For the JWT login recipe + bootstrap fetch + queryAppointments + the full URL-namespace map, load the `api-testing` skill.

### 9. Swing client lifecycle — see the `swing-client-launch` skill

The Swing desktop client launches via `mvn exec:java` (NOT
`spring-boot:run` — it uses plain `AnnotationConfigApplicationContext`),
with `-Dexec.daemonThreadJoinTimeout=86400000` to keep the EDT alive
past `main()` return. For the full start/stop/restart procedure, the
two-log convention (`logs/rapla.log` + `logs/rapla-client.log`),
`-Dexec.args="admin"` auto-login, and the "don't run two clients
against the same server" rule, load the **`swing-client-launch`**
skill. Server must be running per §8 first.

### 10. Testing conventions — pyramid + nevers

PRD 017's pyramid. Pick the cheapest tier that exercises your code path. **Java: default to tier 1 or 2; reach for tier 3/4 only when you actually need a Spring context. Angular: default to tier 5; only mount a component (tier 6) when behaviour depends on template/DOM.** Browser e2e for the SPA is planned per PRD 033 (Playwright MCP), not yet a tier.

| Tier | Where | Engine | Cost / first test | Use for |
|---|---|---|---:|---|
| 1. Pure unit | `rapla-core/src/test/...` | plain JUnit, **no Spring** | < 100 ms | Entities, util, date math, parsing, repeating-rule logic, permission rules, JSON wire-format |
| 2. Facade / storage unit | `rapla-server/src/test/...` extending `FacadeTestSupport` | plain JUnit, **no Spring** | ~150 ms | `RaplaFacade`-level behaviour, XML round-trip, conflict detection, anything that needs real `LocalCache` over real `FileOperator` |
| 3. Web slice | `rapla-app/src/test/...` with `@SpringBootTest` + `@AutoConfigureMockMvc` | Spring context (cached) | ~3–5 s amortised | Controllers, error-mapping, JWT gate, JSON DTO contracts |
| 4. Full E2E | `rapla-app/src/test/...` with `@SpringBootTest(webEnvironment=RANDOM_PORT)` | Spring + Tomcat | 7–15 s | Server↔REST-client round-trips, login → query → mutate. Keep small. |
| 5. Angular unit (TS) | `rapla-angular/src/**/*.spec.ts` | Vitest, **no `TestBed`** | < 50 ms | Pure-TS services, validators, RxJS pipelines, formatters, route guards — anything you can construct with `new` |
| 6. Angular component | `rapla-angular/src/**/*.spec.ts` with `TestBed.createComponent(...)` | Vitest + Angular TestBed + jsdom | ~200–500 ms first, ~50 ms subsequent in same `describe` | Template bindings, `@Input`/`@Output` wiring, directives, `*ngIf`/`*ngFor` rendering, Material-driven a11y. Don't reach for it to test logic a tier-5 test could cover. |

#### Nevers (apply to every test you write)

- **Never add a constructor argument to `FacadeImpl` or `FileOperator` without updating `FacadeTestSupport` in the same change** — the rapla-app `@SpringBootTest` ring is the only CI signal that catches drift.
- **Never tag inconsistently** — JDBC-hitting tests get `@Tag("db")`, `@SpringBootTest` acceptance tests get `@Tag("e2e")`. Both are excluded from the default `mvn test`; without the tag they break the fast lane.
- **Never use `@SpringBootTest(webEnvironment=RANDOM_PORT)` when MockMvc would do.** MockMvc is ~5–10× faster.

#### Howtos — see the `testing-conventions` skill

For the `FacadeTestSupport` usage pattern, the "when NOT to use it" bullets, the `@Tag` table with currently-tagged classes, and the `mvn test -Dtest.excludedGroups=` recipes for running the full lane, load the `testing-conventions` skill.

For JaCoCo coverage reports, load the `coverage-report` skill (release-prep only — off by default).

### 11. Never delete code to fix compile errors

Don't delete code to make a compile pass — unless the removal is part of the plan.

### 12. Never leak server-side data past the user's read scope

When you move logic from the Swing client to the server (any REST endpoint
that returns entities, ids, names, or **the existence** of entities), the
client must only ever see what the **current user is already permitted to
see** on the Swing side. The Swing client today only holds allocatables /
reservations / classifications the user can read — REST endpoints must
preserve that invariant.

Concrete rules for new server endpoints:

- **Filter by user permission at every output boundary.** Don't trust that
  upstream queries did it. Pattern:
  ```java
  User user = session.checkAndGetUser(request);
  PermissionController pc = facade.getPermissionController();
  result.removeIf(a -> !pc.canRead(a, user));
  ```
- **Existence is information.** If the client passes a list of ids (e.g.
  `?allocatables=a1,a7,a99`), the response must not differentiate between
  "id doesn't exist" and "id exists but you're not allowed to see it" —
  silently drop both. A user must not be able to probe for hidden ids by
  watching which ones echo back as columns / blocks / facets.
- **Per-entity, not per-collection.** A reservation the user can read may
  reference an allocatable the user **can't** read. Re-check the contained
  allocatables before exposing their names/ids in the response shape.
- **Server-derived data inherits the strictest permission of its inputs.**
  A computed `RenderedBlock` that mixes reservation data + allocatable
  colours is only safe to return when the user can read both. If either
  is private, drop the block (or strip the private field).
- **Reference implementation:** `CalendarViewController.resolveResourceFilter`
  (rapla-server). The comment there spells out the probe risk.
- **Test the leak.** Every new endpoint that takes ids or filters needs a
  tier-3 MockMvc test where a non-admin user requests data they shouldn't
  see and the response is verified to contain neither the id nor any
  attribute that reveals existence (column count, error message text,
  HTTP status differentiation, latency, …).

When in doubt: behave as if the response were a CSV dump emailed to the
user. If anything in the response is something they couldn't have got via
the Swing client, the endpoint is broken.

### 13. Mock-framework policy — nevers

**Default: no mocks of internal rapla types.** Use the real thing — `FacadeTestSupport` at tier 2, real Spring context + MockMvc at tier 3. Mocks save < 100 ms per test and silently bypass the bugs rapla actually ships (Jackson final-field round-trip, operator stub no-ops, permission leaks, MONTHLY semantic, constructor drift). Full rationale: PRD 027.

- **Never `mock(...)` any rapla facade, operator, cache, permission, entity, or storage type** (`RaplaFacade`, `LocalCache`, `PermissionController`, etc.). Construct the real one via `FacadeTestSupport`.
- **Never `@MockBean` / `@SpyBean` in `@SpringBootTest`** — they bust the Spring context cache (fights PRD 007 Phase 2.7). Use a `@TestConfiguration` static class with hand-rolled stub beans (pattern: `PreferencesAdminControllerIntegrationTest.StubPanelsConfig`).
- **Never mock pure-Java models from PRDs 023 / 024** (`AllocationConflictModel`, `RepeatingRuleValidator`, `RaplaBuilder`, layout strategies). No I/O — just construct them.

For the Allowed list (Servlet-API mocks, hand-rolled doubles for `MailInterface`/`PreferencesPanel`) and the audit-grep, load the `testing-conventions` skill.

### 14. Angular frontend (`rapla-angular/`) — see the `angular-frontend` skill

The SPA lives in `rapla-angular/` (Angular 21, served at `/app/`). For build/lint commands, OpenAPI-client regeneration, code-style, Vitest/TestBed test patterns, and the wire-probe workflow, load the `angular-frontend` skill. Full layout + URL space is in `rapla-angular/README.md` and PRD 026.

For browser-driven SPA debug/prototype the `playwright` MCP server is wired (tools surface as `mcp__playwright__browser_*`) — usage patterns are in the `angular-frontend` skill, install steps in `docs/development.md`.

Two rules that fire without the skill (footguns that catch agents who skim Angular files without loading the skill):

- **Never start, stop, restart, or `kill` the `ng serve` dev server.** The user runs it in their own terminal (`npm run start:ai` selects a no-live-reload config). If a change isn't visible, ask the user to check their terminal — don't relaunch.
- **Never edit files under `rapla-angular/src/app/api/`.** That tree is generated by `npm run gen:api` from the live OpenAPI doc and gitignored — any edit is lost on next regen. If a generated DTO is wrong, fix the server-side Spring/Jackson annotation, then regenerate.
