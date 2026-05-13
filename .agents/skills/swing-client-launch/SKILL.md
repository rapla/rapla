---
name: swing-client-launch
description: Use when launching, restarting, or debugging rapla's Swing desktop client (the legacy GUI alongside the Angular SPA). Covers the `mvn exec:java` invocation (NOT `spring-boot:run` — Swing uses plain `AnnotationConfigApplicationContext`), the daemon-thread-timeout magic that keeps the EDT alive, log markers for readiness probing, and the two-client / two-log gotchas. Skip when only the server, REST API, or Angular SPA is in scope — those have their own lifecycle rules in AGENTS.md §8 and §14.
---

# Swing client lifecycle

Same shape as AGENTS.md §8 server lifecycle (run_in_background pattern,
PID-file convention, log-inspection commands — substitute `logs/rapla-client.{pid,log}`)
but with three key differences and a couple of footguns.

## Differences from the server (§8)

- **Launch with `exec:java`, NOT `spring-boot:run`.** The client uses plain
  `AnnotationConfigApplicationContext`, not `@SpringBootApplication`.
  `spring-boot:run` silently no-ops on rapla-client.
- **No HTTP probe** — the client is a window, not a server. Use log
  markers: `grep -q "Starting gui" logs/rapla-client.log` for readiness;
  `grep -E "(POST|GET) request"` for REST round-trips.
- **Connects to `http://localhost:8051/`** via
  `RemoteConnectionInfo.serverURL` (defaulted from
  `StartupEnvironment.getDownloadURL()`). Start the §8 server first or
  the login dialog hangs at "Connection refused" / "401 Unauthorized".
- **Graceful-shutdown window is 5 s** (not §8's 10 s) — no JDBC pool or
  file locks to release.

## Start — two modes

### Foreground (interactive, log streams to terminal, Ctrl-C stops cleanly)

Use for human debugging or when you want stdout in your shell:

```bash
mvn -pl rapla-client -am compile exec:java \
    -Dexec.args="admin" \
    -Dexec.daemonThreadJoinTimeout=86400000
```

### Background (returns immediately, log to file, PID tracked)

Use this when an AI agent launches the client (Bash tool with
`run_in_background=true`) or when you want to keep using the shell:

```bash
mkdir -p logs
mvn -pl rapla-client -am compile exec:java \
    -Dexec.args="admin" \
    -Dexec.daemonThreadJoinTimeout=86400000 \
    > logs/rapla-client.log 2>&1 &
CLIENT_PID=$!
echo $CLIENT_PID > logs/rapla-client.pid
echo "Started client, PID=$CLIENT_PID"
```

In both modes the Swing window opens on `$DISPLAY` regardless —
`mvn exec:java` runs `main()` in the Maven JVM (no fork), so the EDT is
alive whether or not stdout is on a TTY. `$!` in the background snippet
is the actual app PID; SIGTERM triggers Spring's context shutdown hooks
(e.g. `@PreDestroy` on `RaplaClientServiceImpl`) cleanly.

### `-Dexec.args` — credentials

- `-Dexec.args="admin"` — auto-login as admin with empty password (dev DB default)
- `-Dexec.args="admin admin-password"` — if you changed the admin password
- `-Dexec.args="homer duffs"` — alternative testdefault.xml user
- Omit entirely → the Swing login dialog opens

## Why the long `daemonThreadJoinTimeout`

`exec-maven-plugin` interrupts every thread (including the EDT and
`raplascheduler-N` workers) after `main()` returns + this timeout (default
15 s). Since `clientService.start()` is async and `main()` returns
immediately, the default kills the client ~15 s after login.
`86400000 ms` (24 h) effectively disables the kill so the JVM lives until
the user closes the window or you `SIGTERM` the PID.

## Why the launch needs `-am compile exec:java`

`mvn -pl rapla-client exec:java` (single-module) can't resolve rapla-core
unless it's in `~/.m2`. The fix lives in `rapla-bom/pom.xml`:
`exec-maven-plugin` is bound with `<skip>true</skip>` and a placeholder
`<mainClass>java.lang.Object</mainClass>` (the plugin validates
`mainClass` before honouring `skip`). `rapla-client/pom.xml` overrides
`skip=false` and the real `mainClass=SpringRaplaClient`. Net: `-am
exec:java` walks the reactor, skips the goal on parents, runs only on
rapla-client — which sees freshly-compiled in-reactor `target/classes` of
every sibling. Per AGENTS.md §5, never `mvn install`.

## Restart

Two separate Bash calls (don't chain — start would hang the agent):

```bash
# 1) Stop
[ -f logs/rapla-client.pid ] && kill "$(cat logs/rapla-client.pid)" 2>/dev/null
for i in 1 2 3 4 5; do
  [ -f logs/rapla-client.pid ] && kill -0 "$(cat logs/rapla-client.pid)" 2>/dev/null || break
  sleep 1
done
rm -f logs/rapla-client.pid
```

Then re-run the Background start snippet above.

## Two logs in play

| Path | Origin |
|---|---|
| `logs/rapla.log` | Server-side — Spring Boot, JDBC, REST handlers |
| `logs/rapla-client.log` | Client-side — Swing, REST proxies, login |

For end-to-end issues, check **both** — a 401 in the client log usually
has a matching auth-failure entry server-side.

## Footguns

- **Don't run two clients against the same server.** Both try to log in
  as the same admin, and the second sees stale data after the first
  mutates. Use a worktree (AGENTS.md §7) for parallel work.
- **The §8 server must be running first.** Without it the login dialog
  hangs at "Connection refused" or returns 401.
- **JNLP launch is a separate concern** — for that, load the
  `test-jnlp-launch` skill instead. This skill covers the dev-loop
  `mvn exec:java` path only.
