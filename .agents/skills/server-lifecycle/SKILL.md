---
name: server-lifecycle
description: Use whenever the user asks to start, stop, restart, status-check, or inspect the logs of the rapla dev (Spring Boot) server — including plain-chat phrasing like "start the server", "restart rapla", "stop the server", "is the server up/running?", "bounce the server", "show/tail the server logs", "bring the server up with the dhbw plugin". ALSO load it whenever YOU (the agent) are about to script any stop / restart / bounce cycle yourself — e.g. restarting to pick up a recompile, or passing run args / Spring flags like `-Dspring-boot.run.arguments=--rapla.foo=true` — not only when the user phrases it; a "plain fresh-checkout start" is the only case that stays inline per AGENTS.md §8, everything past that loads this skill. Carries — graceful-shutdown stop (10 s window, never kill -9 first), the restart procedure (separate stop + background-start Bash calls), the pkill self-match footgun, jps/HTTP status probes, tail -F log streaming + the wait-for-"Started Rapla"-marker recipe, the external-plugin (dhbwrapla) run recipe, and the lifecycle conventions (one server per checkout, worktree port offsets, never start during a package build). The minimal vanilla start command + the never-`mvn install` hard rules also live always-on in AGENTS.md §8.
---

# Server lifecycle — stop, restart, status, inspect

For the **start** recipe and the hard rules (never `mvn install`, never run from `~/.m2`, etc.) see AGENTS.md §8 — they need to be in always-loaded context. Everything below is reach-for-when-needed.

## Stop

> **Safe for ng-serve users:** the patterns below match `spring-boot:run`
> and `RaplaSpringBootApplication` only. They will NOT touch `ng serve` /
> `node` / Vite worker processes — feel free to run them while an Angular
> dev server is up in another terminal.

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

For the agent flow where the PID file doesn't track the JVM (see §8), `pkill -f RaplaSpringBootApplication` is simpler and equivalent.

## Restart

Stop in one Bash call (returns immediately), then start in a separate Bash call with `run_in_background=true`. **Do not chain stop + start in one Bash call** — `kill ... ; sleep ; mvn spring-boot:run` makes the whole call a long-running process from the agent's perspective.

> **pkill self-match footgun (cost a chain of exit-144 mysteries, 2026-06-21).**
> `pkill -f <pattern>` matches against the FULL command line of every process —
> **including the very shell running your `pkill`.** So a one-liner that both kills
> and starts, like `pkill -f 'spring-boot:run'; mvn ... spring-boot:run ...`, makes
> pkill match its own parent shell (whose argv contains `spring-boot:run`) and SIGTERM
> it before `mvn` ever runs — the call dies with exit 144 (128+SIGTERM-ish) and an
> **empty log**. Same trap if you `pkill -f RaplaSpringBootApplication` inside a script
> whose later lines mention `RaplaSpringBootApplication`. Two fixes:
> 1. Keep stop and start in **separate** Bash calls (the rule above already does this).
> 2. When a kill pattern could appear in your own command, break the literal with a
>    regex class so it can't self-match: `pkill -f 'RaplaSpringBoot[A]pplication'`
>    (matches the JVM, never the shell line `…[A]…`). `pgrep -f` self-matches the same
>    way — that's why a bare `pgrep -f RaplaSpringBootApplication` reports "still
>    running" forever (it's seeing your own grep). Verify down via the **port**
>    (`curl localhost:8051/server`) or `ps -eo args | grep` excluding bash, not pgrep.

## Status / health

```bash
# Process alive? (Use jps for the Bash-tool flow; the PID file is unreliable
# when started via run_in_background.)
jps -l | grep RaplaSpringBoot && echo RUNNING || echo "NOT RUNNING"

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

## Conventions

- **`logs/`** is the project-root directory (gitignored). PID file at `logs/rapla.pid`, log at `logs/rapla.log`.
- **Never `kill -9` first** — the stop snippet gives 10 s for graceful shutdown so JDBC connections and file locks release cleanly.
- **Never run two servers in the same checkout** — second one fails with `BindException` on 8051. Use a worktree (AGENTS.md §7) with port offset and `logs/rapla-N.{pid,log}`.
- **Never start the server during a `mvn package` build** that produces a fat JAR (`spring-boot:repackage` writes the same JAR `java -jar` reads). Not a concern for `spring-boot:run` alone.
- Restart cycles use `mvn -pl rapla-app -am compile` only — never `install` (see AGENTS.md §5).
