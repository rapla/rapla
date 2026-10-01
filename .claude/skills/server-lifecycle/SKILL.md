---
name: server-lifecycle
description: Use whenever the user asks to start, stop, restart, status-check, or inspect the logs of the rapla dev (Spring Boot) server — including plain-chat phrasing like "start the server", "restart rapla", "stop the server", "is the server up/running?", "bounce the server", "show/tail the server logs", "bring the server up with the dhbw plugin". ALSO load it whenever YOU (the agent) are about to script any stop / restart / bounce cycle yourself — e.g. restarting to pick up a recompile, or passing run args / Spring flags like `-Dspring-boot.run.arguments=--rapla.foo=true` — not only when the user phrases it; a "plain fresh-checkout start" is the only case that stays inline per AGENTS.md §8, everything past that loads this skill. Carries — graceful-shutdown stop (10 s window, never kill -9 first), the restart procedure (separate stop + background-start Bash calls), the own-PID-only stop rule (never pkill/pgrep/kill by pattern; exit 144), ss/HTTP status probes, the is-the-server-fresh probe (build timestamp vs newer target/classes — 'does the server have my latest code?', 'is the fix deployed?'), tail -F log streaming + the wait-for-"Started Rapla"-marker recipe, the external-plugin (dhbwrapla) run recipe, and the lifecycle conventions (one server per checkout, worktree port offsets, never start during a package build). The minimal vanilla start command + the never-`mvn install` hard rules also live always-on in AGENTS.md §8.
---

# Server lifecycle — stop, restart, status, inspect

For the **start** recipe and the hard rules (never `mvn install`, never run from `~/.m2`, etc.) see AGENTS.md §8 — they need to be in always-loaded context. Everything below is reach-for-when-needed.

## Stop — only your own numeric PID

Stop only a PID you recorded for a port you own. Match-and-kill in any form — `pkill -f`,
`pgrep -f … | kill`, a loop over `/proc/*/cmdline`, `jps | grep` + kill — is the same mistake:
a main class (`RaplaSpringBootApplication`, `RaplaServerLoader`), a Maven goal
(`spring-boot:run`) or an artifact name matches every peer's server and wrapper AND your own
calling shell. Violating the letter is violating the spirit: filtering the matches first,
"only this once", "they're all mine anyway", an urgent user order ("stop it NOW, it's sending
mails") is still killing by name. So is a PID picked from `ps` by eye, and a process-group kill
(`kill -- -PGID`). **Your port** = one you started in this session and can point to (tool call or
`logs/`); unsure → ask, don't kill. Scar 2026-09-30:
another session's 8051 server killed twice (a `/proc` loop, then `pgrep -f 'spring-boot:run'
| kill` as "cleanup" after a correct PID-file stop); its owner restarted three times.

Record the PID once the port answers (separate Bash call after the start):
```bash
PORT=8051; ss -lntpH "sport = :$PORT" | grep -oP 'pid=\K[0-9]+' > logs/rapla.pid
```
Anchor the extraction on `pid=` — a bare `grep -oE '[0-9]+'` also grabs digits from labels
(`R2_JAVA_PID=…` → `2`).

Stop:
```bash
PID=$(cat logs/rapla.pid); kill "$PID"
for i in $(seq 10); do kill -0 "$PID" 2>/dev/null || break; sleep 1; done
kill -0 "$PID" 2>/dev/null && kill -9 "$PID"; rm -f logs/rapla.pid
```
- **No PID file:** take the PID from `ss -lntp` on YOUR port — never search by name. Port not
  yours (8051 = canonical checkout, often another session): announce + ask the owner first
  (AGENTS.md §7a).
- **A wrapper survives** (the mvn JVM; the listener is the forked app JVM): leave it and
  report it — don't hunt for it.
- **Always verify:** `ss -lntp "sport = :<port>"` after the stop, report port + PID.
- **Exit 144:** your pattern hit your own shell, so it probably hit more. Don't retry;
  `ss -lntp` to see which servers are still up, then report.

## Restart

Stop in one Bash call (returns immediately), then start in a separate Bash call with `run_in_background=true`. **Do not chain stop + start in one Bash call** — `kill ... ; sleep ; mvn spring-boot:run` makes the whole call a long-running process from the agent's perspective.

**A restart replays the exact previous start command — never reconstruct it from memory.**
Profiles, `-P<plugin-id>`, run arguments: dropping any of them silently changes server
behavior (scar 2026-06-24: a restart without `profiles=local` lost an OAuth flag and broke
the production-Keycloak login; the user found out via screenshot 30 min later). Recipe: on
every start, first write the full command to `logs/rapla.cmd` (`echo "<full mvn command>"
> logs/rapla.cmd`); on restart, `cat logs/rapla.cmd` and reuse it verbatim. If there is no
`.cmd` file (server started by the user), ask or check `ps -eo args` for the running
command line before stopping — see also the `feedback_dev_server_flavor` memory (the
running server is often dhbw-flavored, not vanilla).

**No multi-minute foreground watch loops.** Background start + one instant probe
(`ss -lntp` / `curl`), report the result immediately; the 30 s startup-wait below is the upper
bound for foreground waiting. If something needs ongoing watching, use a background
`tail -F` + Monitor — and when launching long background work, tell the user the expected
duration and report on completion; don't leave them typing "status" (happened 4× in one
session, plus "are you still running?" during a rejected 400 s foreground wait).

## Status / health

```bash
# Who listens on my port? (read-only; the pid here is what Stop kills)
ss -lntp "sport = :8051"

# HTTP port answering? (no Actuator endpoint enabled today; hit a known URL.)
curl -sf -o /dev/null -w '%{http_code}\n' "http://localhost:8051/raplaclient.jnlp" \
  && echo "HTTP OK" || echo "HTTP DOWN"
```

URL layout per PRD 031: context-path dropped; REST under `/api/`, SPA at `/app/`, legacy iCal/calendar load-bearing URLs at `/rapla/{calendar,ical,…}`. JNLP launcher at `/raplaclient.jnlp` (root).

## Inspect logs

**One-shot snapshots:**

```bash
tail -100 logs/rapla.log                                          # last 100 lines
grep -E 'ERROR|WARN' logs/rapla.log | tail -50                    # recent errors/warnings
grep "Started.*in [0-9.]+ seconds" logs/rapla.log | tail -1       # is startup complete?
```

**Wait for a specific event** — faster than fixed `sleep`; returns the moment the line appears or fails after the timeout:

```bash
# Wait up to 30 s for Spring Boot startup:
timeout 30 sh -c 'until grep -q "Started.*in [0-9.]+ seconds" logs/rapla.log; do sleep 0.3; done' \
  && echo "READY" || echo "TIMEOUT"
```

**Stream every new log line as a tool event:** start `tail -F logs/rapla.log` with `run_in_background=true`, then attach `Monitor` to the shell ID with an until-loop or content matcher. `-F` follows across log rotation.

## Testing an external plugin (e.g. dhbwrapla)

Run `spring-boot:run` **through the plugin's aggregator pom** AND pin the working directory to
the plugin checkout root. Custom plugins reference their dataset / yaml files by **relative
path** (`./data/rapla-hsqldb`, `./local/`, …) keyed off the plugin repo root. `spring-boot:run`'s
default `workingDirectory` is the rapla-app module dir, so those relative paths land in
rapla-app's vanilla dev DB instead of the plugin's dataset — boot then fails the moment a plugin
bean looks up a plugin-seeded resource (e.g. `EntityNotFoundException: No dynamictype with
elementKey X`). Canonical recipe, `<PLUGIN_ROOT>` = the plugin checkout root (e.g. `~/git/dhbwrapla`):

```
mvn -f <PLUGIN_ROOT>/aggregator-pom.xml -pl ../rapla/rapla-app -am -P<plugin-id> \
    spring-boot:run \
    -Dspring-boot.run.fork=false \
    -Dspring-boot.run.workingDirectory=<PLUGIN_ROOT> \
    -Dspring-boot.run.profiles=local \
    -Dspring-boot.run.arguments="--spring.config.additional-location=file:<PLUGIN_ROOT>/local/"
```

The aggregator's reactor pulls the plugin's `target/classes` onto rapla-app's classpath; the
`-P<plugin-id>` profile in `rapla-app/pom.xml` declares the plugin as a `runtime`-scope dep, so
the plugin's `@AutoConfiguration` actually fires. Running rapla's pom in isolation silently leaves
the plugin off the classpath and its beans (auth stores, sync services, …) never get created.
Never `java -jar` the packaged fat JAR for routine dev — that's deployment testing only (see the
`test-deployment` skill). And read the plugin repo's own `AGENTS.md` first — it carries the
plugin-specific knobs (workingDirectory, additional config locations, conditional beans).

## Traps (2026-09-02, Siegen delivery)

- **`MAVEN_OPTS` never reaches the app** — `spring-boot:run` forks a second JVM even with
  `-Dspring-boot.run.fork=false`; JVM flags go in `-Dspring-boot.run.jvmArguments="…"` only.
  Symptom when missed: locale stays English (weekdays "Tue", `Appointment.times` "0:00 PM").
- **`rapla.file-datasources.raplafile` resolves against the CWD, which is `rapla-app/`** under
  `spring-boot:run` — a repo-root relative path silently creates an EMPTY default system
  instead of failing. Use `../data/<file>.xml` in profiles run from the repo root.
- **`spring-boot:run` test-compiles first** — a red test-first file anywhere in the reactor
  blocks the dev restart; start with `-Dmaven.test.skip=true` only as a stop-gap and say so.
- **Port answers but no WSL process:** a Windows instance of the packaged JAR on mirrored
  networking owns 8051; `ss -lntp` shows no pid. Stop it on the Windows side.

## Conventions

- **`logs/`** is the project-root directory (gitignored). PID file at `logs/rapla.pid`, log at `logs/rapla.log`.
- **Never `kill -9` first** — the stop snippet gives 10 s for graceful shutdown so JDBC connections and file locks release cleanly.
- **Never run two servers in the same checkout** — second one fails with `BindException` on 8051. Use a worktree (AGENTS.md §7) with port offset and `logs/rapla-N.{pid,log}`.
- **Never start the server during a `mvn package` build** that produces a fat JAR (`spring-boot:repackage` writes the same JAR `java -jar` reads). Not a concern for `spring-boot:run` alone.
- Restart cycles use `mvn -pl rapla-app -am compile` only — never `install` (see AGENTS.md §5).

## Freshness probe — does the running server have my latest code? (moved from AGENTS.md §8)

Check yourself; never ask the user "did you restart?". One self-contained probe — empty output = server is fresh; listed files = the server predates them:

```bash
find rapla-app/target/classes -name '*.class' -newermt "$(curl -s localhost:8051/server | grep -o '[0-9-]\{10\} [0-9:]\{5\} GMT' | head -1 | sed 's/ GMT/:00Z/; s/ /T/')" | head
```

The ISO-8601 conversion is required — `find` is `bfs` on this machine and rejects the raw `… GMT` string. In a worktree, substitute its port. Run it FIRST when a user reports a server-side fix "doesn't work", or before attributing any symptom to a stale server; only a non-empty result justifies suggesting a restart.
