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
- Requires SDKMAN (Java 21 + Maven) on WSL2 Ubuntu

The reactor aggregator (`pom.xml` at the repo root, packaging=pom, artifactId=`rapla-aggregator`) lists the 5 module siblings. Running `mvn` from the repo root walks the whole reactor.

## Rules

### 1. Test-First Approach
- Always write a failing test before implementing a feature or bug fix.
- Run `mvn test` to verify the test fails, then implement, then verify it passes.
- Tests go in `src/test/java/` mirroring the source package structure.

### 2. PRD-Driven Development
- Before implementing **anything**, check `docs/prd/` for an existing PRD that matches the feature or bug.
- If a matching PRD exists, read it, update it if needed, and plan implementation there.
- If no PRD exists, create one in `docs/prd/` before writing any code.

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

### 5. Build Discipline
- Use `mvn compile` for the routine compile-check after edits. It's fast and catches type errors. The repo-root `pom.xml` is the reactor aggregator (post-PRD-005).
- **NEVER `mvn install`.** Same rule as §8 for the dev server, applies equally to test runs. Installing JARs into `~/.m2/repository` shadows in-reactor `target/classes` for sibling modules and silently runs *stale* code on the next test invocation. Use `-am` instead — see below.
- **Do not run the full `mvn test` after every small step or after every PRD step.** Full tests are slow (~60–120 s reactor-wide with Spring context startup overhead) and accumulate cost across many incremental edits.
- **During an active session, only run targeted tests.** Use `mvn -pl rapla-app -am test -Dtest=ClassName` for a single test class. The `-am` ("also-make") flag tells Maven to build dependent modules from the in-reactor source tree before running the test — no `install` needed. **The `-am` flag is mandatory** for any cross-module test in this reactor; without it Maven resolves siblings from `~/.m2/repository`, which may be stale or missing entirely. Pick the tests that exercise the code you just touched — for example, after editing an XML reader/writer, run the XML round-trip tests, not the whole suite.
- **Run the full reactor `mvn test` only at session end** (when you're about to hand off, or when the user explicitly asks for a green-build sign-off). Don't run it as a "checkpoint" between iterations of the same task — `mvn compile` already catches type errors, and full-suite runs in a tight loop just burn time without catching anything `mvn compile` + targeted tests wouldn't.
- If a targeted test fails in a way that suggests a wider regression, *then* expand to the full suite — but only as an investigation step, not as routine.
- **Targeted tests across the multi-module reactor:** when a test class lives in (say) `rapla-app` but the simplest `mvn -pl rapla-app -am test -Dtest=Foo` invocation also test-compiles upstream modules, *and* one of those upstream modules has broken test sources, you'll get a spurious red. Two fixes: (a) add `-Dsurefire.failIfNoSpecifiedTests=false` so per-module surefire skips modules where no test matches the `-Dtest` pattern; (b) explicitly list the modules whose tests should run, e.g. `mvn -pl rapla-bom,rapla-core,rapla-server,rapla-app test -Dtest=Foo -Dsurefire.failIfNoSpecifiedTests=false` — bypasses `rapla-client`'s test-compile entirely. Use (a) by default; reach for (b) when there's a known-broken sibling module.
- **Don't run `mvn clean` routinely.** `mvn clean` deletes `target/` and forces full recompilation, which is slow. Maven's incremental compile is reliable in normal use. **Reach for `mvn clean` only after renaming, moving, or deleting classes** — that's the case incremental compile can't cover, because stale `.class` files for the old name linger in `target/` and the JVM happily loads them. For everything else (type errors, regressions, "the build feels weird"), plain `mvn compile` / `mvn test -Dtest=...` is correct — investigate what's actually wrong rather than reaching for clean. Even a final hand-off build doesn't need clean unless you've moved files in this session. The goal is to keep the inner loop fast.

### 6. Git
- Never commit unless explicitly asked.
- Never push unless explicitly asked.
- **Before ending a session, update outdated PRDs.** Any PRD whose Plan, Open Questions, or Status no longer matches what's actually in the codebase (because of work landed during the session) gets a brief edit reflecting the new reality — close the resolved OQs, mark phases done/in-progress, note any direction changes. PRDs are the long-term context for future sessions; if they're stale, the next session re-litigates decisions you already made.

### 7. Parallel Work — Use a Git Worktree

If you may run in parallel with another agent, or you need a long-running dev server alongside an existing one, **work in your own git worktree** instead of the canonical checkout. The full protocol (creation, port allocation, sharing model, hard rules) lives in the **`git-worktrees`** skill at `.agents/skills/git-worktrees/SKILL.md` — load it before doing anything that touches a worktree. On a quiet `master`/main with no other agent active, you can ignore this rule.

**Compile errors in files you didn't edit:** If `mvn compile` surfaces an error in a file that's not on your change list, before reaching for a fix, check whether another agent (or the user via a linter) modified it concurrently. `stat -c '%Y %y' <file>` against `git log -1 --format=%ct -- <file>` shows the working-copy mtime vs the last-commit time — if the working-copy is newer than your session start *and* you didn't touch it, it's a parallel edit. **Don't fix it.** Surface it to the user and ask whether to wait for the parallel work to land or coordinate. Parallel-edit fixes risk reverting in-flight refactors and step on the other agent's work.

**Hard rule: NEVER fix other sessions' work.** This applies even when:
- the failure looks "trivial" (one missing import, one signature mismatch)
- the fix is in a `@Bean` factory or config class you've edited before in this session
- the failure looks like a transitive consequence of your own change

Other sessions are mid-refactor. Their files in working-copy state are *snapshots, not finished work*. Touching them — even to "make the build green" — produces merge conflicts, reverts in-progress work, or makes the other session's next edit collide with yours. If a parallel-session change broke the build for you, the other session is already on the path to fixing it; your job is to stay out.

**Keep your own changes.** Don't revert work you've completed just because the build is currently red from parallel-session activity in unrelated files. Confirm the failing file is parallel-session-modified (mtime check above), then either: (a) move to other work that doesn't touch their area, or (b) tell the user and stop. **Never: "just one quick fix in their file."**

**Never stash, `git checkout`, or otherwise discard your own work when you notice parallel-session activity.** Stashing your changes to "let them land cleanly" loses your work or creates merge conflicts later. The right response to discovering a parallel session is to *stop touching their files* — not to undo your own progress. Your changes stay in the working tree until the user explicitly asks you to revert them.

### 8. Server lifecycle — start, stop, restart, inspect

The dev server is a Spring Boot application started via `mvn spring-boot:run` (no package step needed — runs from `target/classes`). Per §7 port convention, the canonical checkout binds **8051**; worktree N uses `8051 + 10·N`. PID/log file paths below assume the canonical checkout — substitute `logs/rapla-N.{pid,log}` in worktrees so multiple servers don't fight for the same files.

**Hard rules — never violate these:**

- **NEVER `mvn install`** — installing JARs into `~/.m2/repository` shadows in-reactor `target/classes` for sibling modules and silently runs *stale* code (the JAR from your last install, not your current edits). Hours-of-debugging trap. There is no scenario where the dev workflow needs `install`; the reactor's in-tree classpath handles all sibling-dependency resolution.
- **NEVER run JARs from `~/.m2/repository`** — same reason. The `mvn spring-boot:run` invocation below uses `-pl rapla-app -am` from the repo root so Maven puts each sibling module's *in-reactor* `target/classes` on the classpath. If you `cd rapla-app && mvn spring-boot:run`, Maven treats rapla-app as standalone and pulls siblings from `~/.m2` — running stale code. Always run from the repo root with `-pl rapla-app -am`.

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

#### Inspect logs

**One-shot snapshots** (no waiting, no streams):

```bash
tail -100 logs/rapla.log                                          # last 100 lines
grep -E 'ERROR|WARN' logs/rapla.log | tail -50                    # recent errors/warnings only
grep -c 'ERROR' logs/rapla.log                                    # error count
grep "Started.*in [0-9.]+ seconds" logs/rapla.log | tail -1       # is startup complete?
awk -v c=10 '/^[0-9]/{n++; if(n>c) exit} {print}' logs/rapla.log  # first c log records (multi-line aware)
```

**Wait for a specific event** (faster than `sleep 15` — returns the moment the line appears, or fails after a timeout):

```bash
# Wait up to 30 s for Spring Boot to report "Started ... in N seconds":
timeout 30 sh -c 'until grep -q "Started.*in [0-9.]+ seconds" logs/rapla.log; do sleep 0.3; done' \
  && echo "READY" || echo "TIMEOUT"

# Wait for an ERROR/WARN to appear (use when watching for a specific failure):
timeout 30 sh -c 'until grep -qE "ERROR|WARN" logs/rapla.log; do sleep 0.3; done' \
  && tail -5 logs/rapla.log || echo "no error in 30 s"
```

The `until + sleep 0.3 + timeout 30` pattern is the responsive AI-friendly equivalent of `tail -f | grep` — it returns as soon as the condition matches (median ~150 ms), with a hard upper bound. Run as a foreground Bash call.

**Stream every new log line as a tool event** — start `tail -F` in the background, then attach `Monitor` to that shell ID. Each new line becomes a stream event the agent can react to without polling:

```bash
# Start the tailer (use run_in_background=true on this Bash call).
tail -F logs/rapla.log
# → Bash returns a shell ID. Pass that shell ID to Monitor with an until-loop or a content
# matcher to react to specific lines (e.g. "stop tailing when you see 'Started' or 'ERROR'").
```

`-F` (capital) instead of `-f` keeps following across log rotation — useful since Spring Boot's log appender may rotate `logs/rapla.log` if it's reconfigured later.

**Status of the in-flight log file** (size, last-modified, growing?):

```bash
ls -lh logs/rapla.log && wc -l logs/rapla.log    # size + line count snapshot
stat -c '%Y' logs/rapla.log                       # mtime as epoch — call twice to check growth
```

If `mtime` is the same across two calls a second apart, the server is silent (idle or stuck). Combine with the process status check above to disambiguate.

#### Conventions & hard rules

- **`logs/`** is the project-root `logs/` directory (already gitignored). Worktrees use the same path inside their own checkout.
- **PID file** at `logs/rapla.pid`, **log file** at `logs/rapla.log`.
- **Never `kill -9` first** — the stop snippet above gives 10 s for graceful shutdown so JDBC connections and file locks release cleanly.
- **Never run two servers in the same checkout** — the second one fails with `BindException` on port 8051. Use a worktree (§7) with its port offset and substitute `logs/rapla-N.{pid,log}`.
- **Never start the server during a `mvn` build** if the build will produce a fat JAR (`spring-boot:repackage` writes to the same JAR `java -jar` would run). Not a concern for `spring-boot:run` alone.
- **Never `mvn install`** (repeats the rule from the top of this section because it's the most common foot-gun). Restart cycles use `mvn -pl rapla-app -am compile` only — that produces in-reactor `target/classes` which the next `mvn spring-boot:run` reads directly.
- **Never run a rapla JAR from `~/.m2/repository/`** — only the in-reactor `target/classes` reflects current source. Running an installed JAR runs whatever was installed last, which is almost certainly stale.

### 9. Swing client lifecycle — start, stop, restart, inspect

The Swing client is a plain-Spring (NOT Spring Boot) main class — `org.rapla.client.spring.SpringRaplaClient` — launched via `mvn -pl rapla-client exec:java` (PRD 002). The `exec-maven-plugin` is preconfigured in `rapla-client/pom.xml` with `mainClass=SpringRaplaClient` and `classpathScope=runtime`. The client uses plain `AnnotationConfigApplicationContext` (no `@SpringBootApplication`), so `spring-boot:run` is **not** the right launcher — it silently no-ops because there's no Boot main class to detect. PID/log file paths below assume the canonical checkout — substitute `logs/rapla-client-N.{pid,log}` in worktrees.

**Stale m2-jar trap.** `exec:java` resolves rapla-core via `~/.m2/repository/.../rapla-core-2.1-SNAPSHOT.jar`. When a parallel session is editing rapla-core, that jar lags and you get cryptic `NoSuchMethodError` / "cannot find symbol method ..." failures at runtime — the rapla-client `target/classes` was compiled against the *new* rapla-core source but the runtime classloader pulls the *old* jar from m2. **Solution:** before launching, run `mvn -pl rapla-bom,rapla-core install -DskipTests` to refresh the m2 jar. This is the only safe way today — the alternatives (`-am exec:java`, `spring-boot:run`) either fail with parameter errors or no-op silently. Document the install step inline whenever you write a launch sequence.

**The client connects to a server on the URL set by `RemoteConnectionInfo.serverURL`** (defaulted by `StartupEnvironment.getDownloadURL()`, currently hardcoded to `http://localhost:8051/`). Make sure §8's server is running and answering 8051 before launching the client, or the login dialog will sit at "401 Unauthorized" (server up but credentials wrong) or "Connection refused" (server down).

#### Start

```bash
mkdir -p logs
# 1. Refresh m2 jar of rapla-core (so exec:java picks up latest source).
mvn -pl rapla-bom,rapla-core install -DskipTests -q
# 2. Compile rapla-client against the freshly-installed rapla-core jar.
mvn -pl rapla-client compile -q
# 3. Launch.
mvn -pl rapla-client exec:java > logs/rapla-client.log 2>&1 &
CLIENT_PID=$!
echo $CLIENT_PID > logs/rapla-client.pid
echo "Started client, PID=$CLIENT_PID"
```

For auto-login: `mvn -pl rapla-client exec:java -Dexec.args="username password"`. Without args, the Swing login dialog opens.

Use `run_in_background=true` on the Bash tool call (same reason as §8 — avoids the agent stalling on a long-lived process). `exec-maven-plugin` runs the `main()` *inside the Maven JVM* (no fork by default), so `$!` is the actual app PID. SIGTERM to that PID terminates the Swing app and Spring context shutdown hook fires cleanly.

Wait ~5–15 s before issuing further actions; confirm with the status check below. The client takes longer than the server to fully come up because of Swing widget construction.

#### Stop

```bash
if [ -f logs/rapla-client.pid ]; then
  kill "$(cat logs/rapla-client.pid)" 2>/dev/null && echo "Sent SIGTERM to $(cat logs/rapla-client.pid)"
  for i in 1 2 3 4 5; do
    if ! kill -0 "$(cat logs/rapla-client.pid)" 2>/dev/null; then break; fi
    sleep 1
  done
  if kill -0 "$(cat logs/rapla-client.pid)" 2>/dev/null; then
    kill -9 "$(cat logs/rapla-client.pid)" && echo "Sent SIGKILL after 5 s wait"
  fi
  rm -f logs/rapla-client.pid
else
  # Fallback when the PID file is missing/stale.
  pkill -f 'rapla-client.*exec:java' && echo "Killed by command-line match"
fi
```

The 5-second graceful window (vs. 10 for the server) reflects that the Swing client has fewer resources to release — no JDBC connection pool, no file locks. Spring's `context.close()` runs synchronously, then the JVM exits. Killing the Maven PID directly works because `exec-maven-plugin` runs the main inline (no child JVM fork) — same process, same shutdown hook, no zombies.

#### Restart

Same rule as §8 — stop in one Bash call, start in a separate `run_in_background=true` call. Don't chain.

#### Status / health

```bash
# Process alive?
[ -f logs/rapla-client.pid ] && kill -0 "$(cat logs/rapla-client.pid)" 2>/dev/null \
  && echo "RUNNING ($(cat logs/rapla-client.pid))" || echo "NOT RUNNING"

# Did the Swing GUI start? (look for the bootstrap log line)
grep -q "Starting gui" logs/rapla-client.log && echo "GUI STARTED" || echo "GUI NOT STARTED"

# Did the auth POST go through? (after a login attempt)
grep -E "(POST|GET) request for \"http" logs/rapla-client.log | tail -3
```

There's no HTTP endpoint to probe — the client is a window, not a server. Use the log markers above instead.

#### Inspect logs

**One-shot snapshots:**

```bash
tail -100 logs/rapla-client.log                                          # last 100 lines
grep -E 'ERROR|WARN' logs/rapla-client.log | tail -50                    # recent errors/warnings only
grep -c 'ERROR' logs/rapla-client.log                                    # error count
grep "Starting gui" logs/rapla-client.log | tail -1                      # is the Swing tier up?
grep -E "(POST|GET) request" logs/rapla-client.log | tail -10            # last few REST calls the client made
```

**Wait for a specific event** (faster than fixed `sleep`):

```bash
# Wait up to 20 s for the Swing tier to be up:
timeout 20 sh -c 'until grep -q "Starting gui" logs/rapla-client.log; do sleep 0.3; done' \
  && echo "GUI READY" || echo "TIMEOUT"

# Wait for the next REST round-trip (use after triggering a click in the GUI):
timeout 20 sh -c 'until grep -qE "(POST|GET) request for" logs/rapla-client.log; do sleep 0.3; done' \
  && tail -5 logs/rapla-client.log || echo "no REST call in 20 s"

# Wait for any ERROR/WARN:
timeout 20 sh -c 'until grep -qE "ERROR|WARN" logs/rapla-client.log; do sleep 0.3; done' \
  && tail -5 logs/rapla-client.log || echo "no error in 20 s"
```

**Stream every new log line as a tool event** — same `tail -F` + `Monitor` pattern as §8:

```bash
tail -F logs/rapla-client.log    # run_in_background=true, then Monitor on the shell ID
```

#### Conventions & hard rules

- **`logs/`** is the project-root `logs/` directory (gitignored). Worktrees use the same path inside their own checkout, with PID/log filenames suffixed by the worktree's port offset.
- **PID file** at `logs/rapla-client.pid`, **log file** at `logs/rapla-client.log`. The server uses unsuffixed names (`logs/rapla.{pid,log}`) so the two are easy to tell apart.
- **Never run two clients pointing at the same server in the same checkout** without coordinating — they'll both try to login as the same admin user and the second one will see stale data after the first one mutates. Worktrees are the way; each can talk to its own port-offset server.
- **Never `kill -9` first** — Spring's `context.close()` runs the bean shutdown hooks (e.g. `@PreDestroy` on `RaplaClientServiceImpl`) which invalidate the auth token cleanly so the server's session list doesn't fill up with zombies.
- **The client and the server have separate logs** — `logs/rapla.log` is server-side (Spring Boot, JDBC, REST handlers), `logs/rapla-client.log` is client-side (Swing, REST proxies, login flow). When debugging an end-to-end issue, look at *both* — a 401 in the client log usually has a matching auth-failure entry in the server log.
