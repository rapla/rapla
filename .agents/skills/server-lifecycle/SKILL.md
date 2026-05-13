---
name: server-lifecycle
description: Use when stopping, restarting, status-probing, or log-inspecting the running rapla dev server. The minimal start recipe + hard rules stay in AGENTS.md §8 because every session needs them; this skill carries the longer snippets (stop with graceful-shutdown window, restart procedure, HTTP/JVM status checks, log streaming, conventions). Skip during a fresh session where you just need to start the server — that's a few lines in AGENTS.md.
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

## Conventions

- **`logs/`** is the project-root directory (gitignored). PID file at `logs/rapla.pid`, log at `logs/rapla.log`.
- **Never `kill -9` first** — the stop snippet gives 10 s for graceful shutdown so JDBC connections and file locks release cleanly.
- **Never run two servers in the same checkout** — second one fails with `BindException` on 8051. Use a worktree (AGENTS.md §7) with port offset and `logs/rapla-N.{pid,log}`.
- **Never start the server during a `mvn package` build** that produces a fat JAR (`spring-boot:repackage` writes the same JAR `java -jar` reads). Not a concern for `spring-boot:run` alone.
- Restart cycles use `mvn -pl rapla-app -am compile` only — never `install` (see AGENTS.md §5).
