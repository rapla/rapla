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
- File naming: `docs/prd/NNN-short-name.md` (e.g., `docs/prd/001-spring-boot-migration.md`).
- Each PRD must contain:
  - **Title** and **Status** (draft / in-progress / done)
  - **Goal** — what and why
  - **Scope** — what files/packages are affected
  - **Plan** — ordered implementation steps
  - **Tests** — what tests to write and when
  - **Open Questions** — unresolved decisions
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
- **NEVER `mvn install`** and **never run rapla JARs from `~/.m2/repository/`**. Both shadow in-reactor `target/classes` for sibling modules with stale code — an hours-of-debugging trap. The reactor's in-tree classpath handles all sibling resolution.
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

### 6a. Lessons learned — bulk-refactor scripts (Date migration, 2026-05-09)

- **Signature regexes must anchor `<rt>` to `\w` and require typed args** (≥2 tokens per
  arg). Otherwise call statements like `throwParseDateException(date);` get matched as
  method declarations and silently deleted.
- **Diff-based recovery against master is dangerous when working tree has diverged.**
  `SequenceMatcher` `replace` opcodes interleave OLD-signature lines into the NEW body.
  Only restore inside `insert` opcodes, and only single call-statement lines.
- **No conversion wrappers in entity/facade/storage tier.** Once a `DateTools.toX(...)`
  is stripped, fix the cascade by flipping the surrounding type — never re-wrap to
  silence the error. Wrappers belong only at JDBC / Swing widget / ical4j / wire-format
  boundaries.
- **No parallel-named methods** (`*AsLocalDateTime`, `set*LocalDateTime`,
  `ofLocalDateTime`). Flip the type at the master name; don't double the API surface.
- **Strip script must distinguish overloads by arg shape.** `DateTools.toDate` has four
  overloads; only `(LocalDateTime)` and `(LocalDate)` are conversions to strip. Skip if
  arg has `.getTime()`, `MILLISECONDS_PER`, or top-level comma.
- **Compile after every script, not after a chain.** Time-box each fix to one error
  pattern; cascading three scripts blind leaves the tree unrecoverable by diff.

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

```bash
mkdir -p logs
# CRITICAL: run from the repo ROOT with -pl rapla-app -am.
# Do NOT `cd rapla-app && mvn spring-boot:run` — that pulls sibling modules from ~/.m2 (stale).
mvn -pl rapla-app -am compile -q
mvn -pl rapla-app -am spring-boot:run -Dspring-boot.run.fork=false \
  > logs/rapla.log 2>&1 &
SERVER_PID=$!
echo $SERVER_PID > logs/rapla.pid
echo "Started, PID=$SERVER_PID"
```

`-Dspring-boot.run.fork=false` runs the app in the Maven JVM so `$!` is the actual app PID (otherwise it's the Maven wrapper PID, and killing Maven leaves the forked app running).
`-pl rapla-app -am` is mandatory — it tells Maven to also-make the dependent modules in-reactor, so the running JVM's classpath references `rapla-{core,client,server}/target/classes`, not `~/.m2/repository/.../*.jar`.

If you're a shell user (not the Bash tool), use `nohup ... < /dev/null &` + `disown` instead — same effect.

Wait ~10 s before issuing HTTP requests; confirm with the status check below.

#### Stop

```bash
if [ -f logs/rapla.pid ]; then
  kill "$(cat logs/rapla.pid)" 2>/dev/null && echo "Sent SIGTERM to $(cat logs/rapla.pid)"
  # Wait up to 10 s for graceful shutdown — never SIGKILL first; lets JDBC connections / file locks release.
  for i in 1 2 3 4 5 6 7 8 9 10; do
    if ! kill -0 "$(cat logs/rapla.pid)" 2>/dev/null; then break; fi
    sleep 1
  done
  if kill -0 "$(cat logs/rapla.pid)" 2>/dev/null; then
    kill -9 "$(cat logs/rapla.pid)" && echo "Sent SIGKILL after 10 s wait"
  fi
  rm -f logs/rapla.pid
else
  # Fallback when the PID file is missing/stale.
  pkill -f 'spring-boot:run|RaplaSpringBootApplication' && echo "Killed by command-line match"
fi
```

#### Restart

Stop in one Bash call (returns immediately), then start in a separate Bash call with `run_in_background=true`. **Do not chain stop + start in one Bash call** — `kill ... ; sleep ; mvn spring-boot:run` makes the whole call a long-running process from the agent's perspective.

#### Status / health

```bash
# Process alive?
[ -f logs/rapla.pid ] && kill -0 "$(cat logs/rapla.pid)" 2>/dev/null \
  && echo "RUNNING ($(cat logs/rapla.pid))" || echo "NOT RUNNING"

# HTTP port answering? (no Actuator endpoint enabled today; hit a known URL.)
curl -sf -o /dev/null -w '%{http_code}\n' "http://localhost:8051/rapla/raplaclient.jnlp" \
  && echo "HTTP OK" || echo "HTTP DOWN"
```

`server.servlet.context-path=/rapla` (PRD 001 Phase 0) means all URLs live under `/rapla/...`.

#### Default credentials (dev only)

The bundled dev DB ships with one admin: **username `admin`, empty password**. To get a JWT for direct REST probing:

```bash
ACCESS=$(curl -s -X POST "http://localhost:8051/rapla/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":""}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])")
curl -s -H "Authorization: Bearer $ACCESS" "http://localhost:8051/rapla/storage/resources" | head -c 500
```

#### Inspect logs

**One-shot snapshots:**

```bash
tail -100 logs/rapla.log                                          # last 100 lines
grep -E 'ERROR|WARN' logs/rapla.log | tail -50                    # recent errors/warnings
grep "Started.*in [0-9.]+ seconds" logs/rapla.log | tail -1       # is startup complete?
```

**Wait for a specific event** — faster than fixed `sleep`; returns the moment the line appears (median ~150 ms) or fails after the timeout:

```bash
# Wait up to 30 s for Spring Boot startup:
timeout 30 sh -c 'until grep -q "Started.*in [0-9.]+ seconds" logs/rapla.log; do sleep 0.3; done' \
  && echo "READY" || echo "TIMEOUT"
```

**Stream every new log line as a tool event:** start `tail -F logs/rapla.log` with `run_in_background=true`, then attach `Monitor` to the shell ID with an until-loop or content matcher. `-F` follows across log rotation.

#### Conventions

- **`logs/`** is the project-root directory (gitignored). PID file at `logs/rapla.pid`, log at `logs/rapla.log`.
- **Never `kill -9` first** — the stop snippet gives 10 s for graceful shutdown so JDBC connections and file locks release cleanly.
- **Never run two servers in the same checkout** — second one fails with `BindException` on 8051. Use a worktree (§7) with port offset and `logs/rapla-N.{pid,log}`.
- **Never start the server during a `mvn package` build** that produces a fat JAR (`spring-boot:repackage` writes the same JAR `java -jar` reads). Not a concern for `spring-boot:run` alone.
- Restart cycles use `mvn -pl rapla-app -am compile` only — never `install` (see §5).

### 9. Swing client lifecycle

**Lifecycle follows §8** — same `run_in_background` pattern, same PID-file convention, same log-inspection commands (substitute `logs/rapla-client.{pid,log}`). Stop snippet identical to §8 except the graceful window is **5 s** (no JDBC pool, no file locks).

**Differences from the server:**
- **Launch:** `mvn -pl rapla-client -am compile exec:java` — NOT `spring-boot:run` (client uses plain `AnnotationConfigApplicationContext`, not `@SpringBootApplication`; `spring-boot:run` silently no-ops). For auto-login: `-Dexec.args="user pass"` (e.g. `admin` with no password against the dev DB, or `admin admin-password` if changed; the bundled testdefault.xml additionally has `homer/duffs`). Without args, the Swing login dialog opens. Wait ~5–15 s for the Swing tier to come up.
- **No HTTP probe** — the client is a window, not a server. Use log markers: `grep -q "Starting gui" logs/rapla-client.log` for readiness; `grep -E "(POST|GET) request"` for REST round-trips.
- **Connects to `http://localhost:8051/`** (`RemoteConnectionInfo.serverURL`, defaulted from `StartupEnvironment.getDownloadURL()`). Start §8's server first, or the login dialog hangs at "Connection refused" / "401 Unauthorized".
- **Two logs in play.** `logs/rapla.log` is server-side (Spring Boot, JDBC, REST handlers); `logs/rapla-client.log` is client-side (Swing, REST proxies, login). For end-to-end issues, check both — a 401 in the client log usually has a matching auth-failure entry server-side.
- **Don't run two clients against the same server** — both try to login as the same admin, and the second sees stale data after the first mutates. Use worktrees.

**Why the launch needs `-am compile exec:java`:** `mvn -pl rapla-client exec:java` (single-module) can't resolve rapla-core unless it's in m2. The fix lives in `rapla-bom/pom.xml`: `exec-maven-plugin` is bound with `<skip>true</skip>` and a placeholder `<mainClass>java.lang.Object</mainClass>` (the plugin validates `mainClass` before honouring `skip`). `rapla-client/pom.xml` overrides `skip=false` and the real `mainClass=SpringRaplaClient`. Net: `-am exec:java` walks the reactor, skips the goal on parents, runs only on rapla-client — which sees freshly-compiled in-reactor `target/classes` of every sibling. Per §5, never `mvn install`.

#### Start — two modes

**Foreground** — interactive, log streams to terminal, Ctrl-C stops cleanly. Use for human debugging or when you want stdout in your shell:

```bash
mvn -pl rapla-client -am compile exec:java -Dexec.args="admin" -Dexec.daemonThreadJoinTimeout=86400000
```

**Background** — returns immediately, log to file, PID tracked. Use this when an agent launches the client (Bash tool with `run_in_background=true`), or when you also want to keep using the shell:

```bash
mkdir -p logs
mvn -pl rapla-client -am compile exec:java -Dexec.args="admin" \
    -Dexec.daemonThreadJoinTimeout=86400000 \
    > logs/rapla-client.log 2>&1 &
CLIENT_PID=$!
echo $CLIENT_PID > logs/rapla-client.pid
echo "Started client, PID=$CLIENT_PID"
```

In both modes the Swing window opens on `$DISPLAY` regardless — `mvn exec:java` runs `main()` in the Maven JVM (no fork), so the EDT is alive whether or not stdout is on a TTY. `$!` in the background snippet is the actual app PID and SIGTERM triggers Spring's context shutdown hooks (e.g. `@PreDestroy` on `RaplaClientServiceImpl`) cleanly.

**Why the long `daemonThreadJoinTimeout`:** `exec-maven-plugin` interrupts every thread (including the EDT and `raplascheduler-N` workers) after `main()` returns + this timeout (default 15 s). Since `clientService.start()` is async and `main()` returns immediately, the default kills the client ~15 s after login. 86 400 000 ms (24 h) effectively disables the kill so the JVM lives until the user closes the window or you SIGTERM the PID.

#### Restart

Two separate Bash calls (don't chain — start would hang the agent):

```bash
# 1) stop
[ -f logs/rapla-client.pid ] && kill "$(cat logs/rapla-client.pid)" 2>/dev/null
for i in 1 2 3 4 5; do
  [ -f logs/rapla-client.pid ] && kill -0 "$(cat logs/rapla-client.pid)" 2>/dev/null || break
  sleep 1
done
rm -f logs/rapla-client.pid
```

Then re-run the Background start snippet above.

### 10. Testing conventions — pyramid + base classes

PRD 017 establishes a four-tier pyramid; pick the cheapest tier that exercises
your code path. **Default to tier 1 or 2; reach for tier 3/4 only when you
actually need a Spring context.**

| Tier | Where | Engine | Cost / first test | Use for |
|---|---|---|---:|---|
| 1. Pure unit | `rapla-core/src/test/...` | plain JUnit, **no Spring** | < 100 ms | Entities, util, date math, parsing, repeating-rule logic, permission rules, JSON wire-format |
| 2. Facade / storage unit | `rapla-server/src/test/...` extending `FacadeTestSupport` | plain JUnit, **no Spring** | ~150 ms | `RaplaFacade`-level behaviour, XML round-trip, conflict detection, anything that needs real `LocalCache` over real `FileOperator` |
| 3. Web slice | `rapla-app/src/test/...` with `@SpringBootTest` + `@AutoConfigureMockMvc` | Spring context (cached) | ~3–5 s amortised | Controllers, error-mapping, JWT gate, JSON DTO contracts |
| 4. Full E2E | `rapla-app/src/test/...` with `@SpringBootTest(webEnvironment=RANDOM_PORT)` | Spring + Tomcat | 7–15 s | Server↔REST-client round-trips, login → query → mutate. Keep small. |

**Speed numbers, measured 2026-05-09** (`mvn -pl <module> -am test -Dtest=Class`):

| Test | Wall (Surefire-reported) | Per test |
|---|---:|---:|
| `FacadeTestSupportTest` (tier 2, 6 tests) | 3.3 s | ~550 ms (incl. 150 ms fresh facade) |
| `ServerServiceIntegrationTest` (tier 3, 2 tests, shared context) | 7.2 s | ~3.6 s (cold-boot dominates the first; second amortised) |

Net: **first-test cold ~45× faster at tier 2**; for a 6-test class, ~4× faster.

#### Using `FacadeTestSupport`

`org.rapla.test.util.FacadeTestSupport` (in `rapla-server/src/test/...`) gives
each `@Test` a freshly-connected `RaplaFacade` over a temp-dir copy of
`testdefault.xml`:

```java
class MyFacadeTest extends FacadeTestSupport {
    @Test
    void categoriesLoad() throws Exception {
        Category[] children = facade.getSuperCategory().getCategories();
        assertEquals(2, children.length);
    }
}
```

- JUnit 5 (`@TempDir`, `@BeforeEach`). Don't mix with JUnit 4 in the same class.
- Override `fixtureResource()` to point at a smaller hand-written XML when
  the 500-line default is overkill.
- Mirrors production wiring in `ServerCoreConfig.raplaFacade()` +
  `ServerStorageSelector.createFileOperator()`. **If you add a constructor
  argument to `FacadeImpl` or `FileOperator`, update this base class in the
  same change** — the rapla-app `@SpringBootTest` ring is the only thing that
  catches drift in CI.

#### When NOT to use `FacadeTestSupport`

- Pure entity/util logic: stay in tier 1 (rapla-core). Don't pull in `FileOperator`
  to test `DateTools` or appointment expansion.
- Controller behaviour, JSON shape, error mapping, JWT gate: tier 3 with
  `@AutoConfigureMockMvc`. MockMvc beats `RANDOM_PORT` ~5–10×.
- The very few "does the bean graph wire end-to-end" smoke tests: tier 4. One
  per major surface is enough.

#### Tagging — fast lane vs. full lane

Slow / environment-dependent tests carry a JUnit 5 `@Tag`:

| Tag | Meaning | Currently tagged |
|---|---|---|
| `db` | Hits a JDBC target (HSQLDB embedded today; still seconds) | `ConcurrentTests` |
| `e2e` | Full `@SpringBootTest` acceptance — cold context, often `RANDOM_PORT` | `RaplaSpringBootApplicationTest`, `ServerServiceIntegrationTest`, `HeadlessClientNameResolutionIntegrationTest`, `SwingClientStartIntegrationTest` |

Default `mvn test` excludes both via surefire `<excludedGroups>${test.excludedGroups}</excludedGroups>`
(default value `db,e2e` set in `rapla-bom/pom.xml`). To include them:

```bash
mvn test -Dtest.excludedGroups=         # run everything
mvn test -Dtest.excludedGroups=db       # full + e2e, skip db
```

When you add a test that fits one of these categories, tag it. New tags need
a row above and a corresponding entry in PRD 017's plan.

#### Coverage report (JaCoCo)

```bash
mvn -Pcoverage test                                    # per-module reports only
mvn -Pcoverage verify -Dtest.excludedGroups=           # per-module + aggregate (recommended)
```

- Per-module: `<module>/target/site/jacoco/index.html` — what each module's *own* test suite covers.
- Aggregate: `target/site/jacoco-aggregate/index.html` — full-stack picture, rolls up @SpringBootTest contributions back to rapla-core / rapla-server bytecode.

The profile flips surefire from `forkCount=0` to `forkCount=1` because
JaCoCo's `-javaagent` argLine needs a forked JVM. Off by default — the fork
costs ~10 s.

The aggregate excludes rapla-app's `target/classes` from class-scanning
(JNLP webclient/ jars crash JaCoCo's bundle analyzer). rapla-app's
`jacoco.exec` is still folded in via a `merge` step, so its @SpringBootTest
runs *do* attribute back to rapla-server/rapla-core in the aggregate.
