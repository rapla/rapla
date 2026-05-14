---
name: java-debugger
description: Use when stepping into a live rapla JVM via JDWP — to set breakpoints, inspect stack frames, evaluate expressions in scope, or watch state at the moment a bug fires. Mostly the Spring Boot dev server (the long-lived target); Swing client occasionally. Skip when logs + a regression test would be cheaper. The `jdwp` MCP server is wired (tools surface as `mcp__jdwp__*`); install steps are in `docs/development.md` step 8.
---

# Step-debugging a rapla JVM

Tools surface as `mcp__jdwp__attach`, `mcp__jdwp__set_breakpoint`, `mcp__jdwp__step_over`, `mcp__jdwp__evaluate`, etc. — 13 in total per dronsv/jdwp-mcp v0.4.1. Connect via host:port (default port 5005 for the server, 5006 for Swing — see below).

## Just start the dev server in debug mode by default

The dev server runs in the background (`run_in_background=true` per AGENTS.md §8); the JDWP MCP is its own subprocess. They don't fight. The JDWP listener costs nothing when nothing's attached — an idle TCP socket on loopback. So the cleanest workflow is:

**Always start the dev server with `-agentlib:jdwp=…address=localhost:5005`** (loopback only — see §8). MCP tools are there when you need them; ignored when you don't.

No "switch into debug mode" step. No "session juggling." The standard agent loop — **investigate → fix → test → continue** — fits in one session because:

- Re-attach after a server restart is one `mcp__jdwp__attach` call.
- Re-setting breakpoints is one call each.
- Editing code while attached is fine — you just lose the breakpoint after restart (re-set it if still relevant).

**Rule of thumb:** if the bug is "I don't even know which branch is being taken," attach and inspect. Once you know, leave the attach alone (idle) or `mcp__jdwp__disconnect`; write the regression test. Total cycle: ~10 minutes for most bugs.

The one case where a sibling worktree pays off (AGENTS.md §7 / `git-worktrees` skill): a multi-day investigation that you want to keep alive while doing *unrelated* feature work elsewhere. Skip for routine debugging.

## End-to-end code-flow inspection in one session

The full recipe for inspecting an entire request path — SPA action (or curl) → controller → facade → storage → response — in a single Claude Code session:

```
1.  Bash: start server in debug mode (run_in_background=true)
        — already idle on :5005 if you started per AGENTS.md §8

2.  mcp__jdwp__attach host="localhost" port=5005
        — attaches the debugger

3.  mcp__jdwp__set_breakpoint
        class="org.rapla...PermissionController" method="canRead"
        — or wherever in the path you want to inspect; arms the trap

4.  Trigger (non-blocking, so the agent stays free for JDWP work):
    a) Bash with run_in_background=true:
       curl -H "Authorization: Bearer $TOKEN" http://localhost:8051/api/storage/queryAppointments …
    b) OR mcp__playwright__browser_evaluate:
       "document.querySelector('button.submit').click(); 'fired'"

5.  mcp__jdwp__get_stack
        — see where execution is paused

6.  mcp__jdwp__evaluate expression="entity.toString()"
        — read locals + fields in scope

7.  mcp__jdwp__evaluate expression="user.getPermissions().size()"
        — expand state by invoking methods

8.  mcp__jdwp__continue
        — releases the suspended thread; request completes

9.  Observe the downstream result:
    a) BashOutput(shell-id-from-step-4)        → curl response body
    b) OR mcp__playwright__browser_snapshot     → resulting DOM

10. Refine and repeat (move the breakpoint, retrigger). Same session,
    same agent context, full path visible.
```

The whole SPA → REST → facade → permission → storage → response path is observable in one conversation. State at any layer is captured via the right tool; the agent never loses context between layers. This is the practical payoff of having both MCPs wired.

The trick that makes it work is step 4 — **the trigger has to be non-blocking** so the agent isn't held inside a Playwright/Bash tool call while the JVM is suspended. The mechanics are spelled out in the next section.

## Combining JDWP with Playwright MCP — the trigger-deadlock pattern

The agent's tool loop is sequential, and both `mcp__playwright__browser_*` calls and `mcp__jdwp__*` calls block. So if the agent fires `browser_click` and the server thread suspends on a JDWP breakpoint, the `browser_click` call stays pending waiting for the HTTP response — and the agent can't issue `mcp__jdwp__continue` to release it. Two parallel Claude Code sessions don't fix this either: JDWP typically accepts one attached debugger, and stdio MCP servers are per-session.

**Solution: separate triggering from waiting.** Three patterns ranked by fit:

| Trigger | When | Pattern |
|---|---|---|
| **`curl` via Bash with `run_in_background=true`** | REST endpoint bugs — the vast majority | Bash fires curl, returns shell ID immediately. Agent drives JDWP. `mcp__jdwp__continue`. Then `BashOutput(shellID)` to read curl's response. |
| **`mcp__playwright__browser_evaluate("…click(); 'fired'")`** | The trigger has to go through SPA UI (form state, JS-side cache, OAuth library) | JS click returns synchronously; the resulting XHR is async. Agent gets control back, drives JDWP, then `browser_snapshot` to see the resulting DOM. |
| **Sequential probes** | You actually need *concurrent* Playwright + JDWP state (rare) | Drop one side. Either rewrite the repro as curl-only, or accept server-side logging in place of JDWP. |

**When the combination matters less than it seems.** Most bugs sit on one side:

- Server-only (Jackson, permission, OAuth validator, conflict detection) → curl + JDWP, no Playwright.
- Browser-only (CORS, OAuth redirect chain, `sessionStorage`, Material rendering) → Playwright MCP alone; server's just returning what it returns.

Genuinely-need-both is "the SPA does X which makes the server do Y which the SPA misinterprets." For those, the curl-decoupled pattern almost always works: re-issue the underlying request with curl + the right headers, bypass the SPA, get JDWP intervention, then verify wire-shape matches what the SPA expects.

## When to use JDWP (and when not)

| Situation | Right tool |
|---|---|
| "Why does this return null?" — logs aren't enough | **JDWP** — break at the return, inspect locals |
| Spring bean wiring confusion — "what got injected?" | **JDWP** — break in `@PostConstruct`, dump injected fields |
| OAuth redirect-URI validator drama (PRD 029) | **JDWP** — break in `AuthorizationServerConfig.RedirectUriValidator`, see what URI is being matched |
| Jackson final-field deserialization bugs (PRD 011) | **JDWP** — break in `readValue`, inspect the visibility config + JSON simultaneously |
| Permission-leak edge cases (AGENTS.md §12) | **JDWP** — break in `PermissionController.canRead`, see user + entity + decision in one snapshot |
| Conflict-detection edge cases (PRD 023) | **JDWP** — break in `AllocationConflictModel`, inspect `LocalCache` state |
| "Why is this test red?" with a clear assertion failure | **Test, not JDWP** — `mvn -pl rapla-app -am test -Dtest=Foo` with `println` first |
| "What does `/api/foo` return?" | **`api-testing` skill (curl)** — JDWP is overkill |
| "Is the data wrong on the wire vs in storage?" | **`storage-inspection` skill** — read `data.xml` directly |

## Starting the server in debug mode

Two changes to the AGENTS.md §8 start recipe:

```bash
> /home/chris/git/rapla/logs/rapla.log    # truncate stale lines
mvn -f /home/chris/git/rapla/pom.xml -pl rapla-app -am spring-boot:run \
    -Dspring-boot.run.fork=false \
    -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005" \
    > /home/chris/git/rapla/logs/rapla.log 2>&1 &
echo "spawned"
```

Run with `run_in_background=true` on the Bash tool call.

- `server=y` — JVM listens for incoming debugger connections.
- `suspend=n` — don't pause at startup; the server boots normally and accepts an attach later. (If you need to break before `main()` runs, flip to `suspend=y` — the JVM hangs at startup until the debugger attaches, useful for early-init bugs in `RaplaSpringBootApplication.main`.)
- `address=*:5005` — bind all interfaces (in WSL2, `localhost` is fine; `*` is just defensive).
- `transport=dt_socket` — TCP. The alternative `dt_shmem` is Windows-only.

Confirm:

```bash
jps -l | grep RaplaSpringBoot                        # PID present
grep "Listening for transport dt_socket at address: 5005" logs/rapla.log
```

The "Listening for transport" line appears within the first few milliseconds of JVM startup, before Spring Boot banner. If you see the banner but not that line, JDWP isn't enabled.

## Starting the Swing client in debug mode (rarer)

Different port — don't collide with the server's 5005. The Swing client uses 5006 by convention:

```bash
mvn -pl rapla-client -am compile exec:java \
    -Dexec.args="admin" \
    -Dexec.daemonThreadJoinTimeout=86400000 \
    -Dexec.jvmArgs="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5006" \
    > logs/rapla-client.log 2>&1 &
```

Most rapla agent debugging is server-side; touch this only when investigating a Swing-only bug (e.g. EDT thread issues, Spring DI in `SwingClientConfig`). The same session-juggling concerns apply.

## Attach + drive — the agent-friendly workflow

Interactive step-debug (humans) is *not* the right paradigm for LLM agents. Stepping one frame at a time wastes turns. The agent-friendly pattern is **conditional breakpoint → dump full state → resume**:

```
1. mcp__jdwp__attach              → host="localhost", port=5005
2. mcp__jdwp__set_breakpoint      → class="org.rapla.server...PermissionController",
                                    method="canRead", condition="user.username.equals(\"admin\")"
3. (trigger the bug from a client — curl, browser, etc.)
4. (MCP fires a hit-event when the breakpoint is reached)
5. mcp__jdwp__get_stack           → see where you are
6. mcp__jdwp__evaluate            → expression="entity.toString()" — read state in scope
7. mcp__jdwp__evaluate            → expression="this.facade.getOwner(entity)" — call methods to expand state
8. mcp__jdwp__continue            → let execution run on
9. mcp__jdwp__clear_breakpoint    → when done
10. mcp__jdwp__disconnect         → release the attach
```

Specifically **don't** use `step_into` / `step_over` in loops — each step costs an MCP round-trip + an agent turn. Use `evaluate` to read whatever state you need at the breakpoint, then continue.

## HotSwap — keep the attach across small recompiles

JDWP's `RedefineClasses` lets the JVM swap a class's bytecode without restart. Spring Boot DevTools uses this automatically. Manual flow:

1. `mvn -pl <module> -am compile` to refresh `target/classes`.
2. The MCP server's `update_class` (or equivalent) command pushes new bytecode into the live JVM.
3. Existing stack frames in the redefined method keep running with old bytecode; new invocations use the new bytecode.

**Works for:** method body changes, new local variables.
**Doesn't work for:** adding fields, changing method signatures, adding/removing methods, changing class hierarchy. The JVM refuses; you have to restart.

If you do any "structural" change while attached, expect a `redefine failed` error → detach, restart server, re-attach.

## Common breakpoint patterns for rapla

| Bug shape | Where to break | What to evaluate |
|---|---|---|
| Authentication 401 | `AuthController.login` | `username`, `request.getHeader("Authorization")` |
| OAuth redirect 401 | `AuthorizationServerConfig$RedirectUriValidator.validate` | `requestedUri`, `registeredUris` |
| JWT decode failure | `JwtAuthenticationFilter.doFilter` or similar | `token`, `RaplaKeyStorage.getPublicJwk()` |
| Permission-filter leak (§12) | `PermissionController.canRead` | `user`, `entity.getId()`, `entity.getPermissionList()` |
| Wrong reservation in response | `RemoteStorageImpl.queryAppointments` | `request.getResources()`, intermediate `result` map |
| Conflict false-positive (PRD 023) | `AllocationConflictModel.findConflicts` | `existing`, `proposed`, `appointment.expandRepeating()` |
| Storage round-trip mismatch | `JacksonObjectMapperFactory$ObjectMapperHolder.MAPPER` | Inspect mapper config; break in `readValue` for the specific call site |

## Cross-references

- AGENTS.md §8 — the non-debug start recipe.
- AGENTS.md §9 / `swing-client-launch` skill — Swing client baseline launch.
- AGENTS.md §7 / `git-worktrees` skill — the worktree pattern for long-running debug sessions.
- `server-lifecycle` skill — stop / restart / status (applies the same in debug mode, just with `-agentlib:jdwp` added).
- `oauth-flow` skill — the bug shapes that benefit most from JDWP.

## Cleanup

When you're done debugging:

```
mcp__jdwp__disconnect          # release the TCP connection to the JVM
```

Then restart the server without `-Dspring-boot.run.jvmArguments` to drop the JDWP listener. Leaving JDWP open on 5005 doesn't hurt locally (loopback only when started with `address=localhost:5005`), but it's a meaningful attack surface in any non-dev context — **never start a production rapla with `-agentlib:jdwp`**.
