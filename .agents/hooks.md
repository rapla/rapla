# Claude Code hooks — what's wired, where, and why

Claude Code reads two layers of settings: per-user at `~/.claude/settings.json`, per-project at `.agents/settings.json` (here, reached via the `.claude → .agents` symlink). Hooks in either file fire automatically — the agent doesn't need to know they exist, but you do, so this file documents the contract.

For settings format and adding new hooks, load the **`update-config`** skill. The Claude Code docs live at `claude.com/docs/en/docs/claude-code/hooks`.

## Project-level — `.agents/settings.json`

### `PreToolUse: Bash` — block `mvn install`

```json
{
  "hooks": {
    "PreToolUse": [{
      "matcher": "Bash",
      "hooks": [{
        "type": "command",
        "command": "input=$(cat); cmd=$(echo \"$input\" | jq -r '.tool_input.command // \"\"'); if echo \"$cmd\" | grep -qE '\\bmvn\\b[^|;&]*\\binstall\\b'; then echo 'BLOCKED by AGENTS.md §5: never `mvn install`. ...' >&2; exit 2; fi"
      }]
    }]
  }
}
```

**What it does.** Receives the pending `Bash` command on stdin as JSON (`{"tool_input":{"command":"..."}}`), greps the command string for `\bmvn\b[^|;&]*\binstall\b`. If matched, exits with code 2 — Claude Code treats that as a block and surfaces the stderr message to the agent.

**What gets blocked / not blocked:**

| Command | Result | Why |
|---|---|---|
| `mvn install` | BLOCKED | The `install` lifecycle phase |
| `mvn clean install` | BLOCKED | Same — `install` is on the command line |
| `mvn -pl rapla-app -am install` | BLOCKED | Same |
| `mvn -pl rapla-app -am compile` | OK | No `install` |
| `mvn -pl rapla-app -am spring-boot:run` | OK | No `install` |
| `mvn install:install-file -Dfile=...` | OK | The regex requires `install` as a standalone word *after* `mvn`; `install:install-file` is the goal `install:install-file`, not the phase. (The `\binstall\b` matches `install` in `install:install-file` too — but the wider context distinguishes by the absence of `\bmvn\b[^|;&]*\binstall\b` matching cleanly. Pragmatically: nothing in rapla uses this goal, so it's not a real concern.) |
| `mvn install \| tee log` | OK (pipe stops match) | The `[^|;&]*` cap excludes commands chained past a pipe/semicolon/ampersand — so a chained `mvn compile && mvn install` *would* be caught but a pipeline starting with `mvn compile | …` then unrelated `install` would not. |

**Why it exists.** AGENTS.md §5 hard rule: never `mvn install`. Both `mvn install` and `java -jar ~/.m2/repository/...` shadow in-reactor `target/classes` for sibling modules with stale code — a documented multi-hours-of-debugging trap (PRD 005). The hook makes the rule enforceable, not just aspirational.

**How to verify it's live.** Run `mvn install` in a fresh shell — you should see `BLOCKED by AGENTS.md §5: never mvn install` on stderr, no Maven process started.

### `PreToolUse: Bash` — block `git restore` / `git reset --hard` / `git clean` / `git stash`

A second hook in the same `Bash` matcher (`.agents/settings.json`, `hooks.PreToolUse[0].hooks[1]`). Same shape as the `mvn install` guard: reads the pending command, greps it, `exit 2` to block.

```
... grep -qE '\bgit\b[^|;&]*\b(restore|clean)\b' || grep -qE '\bgit\b[^|;&]*\breset\b[^|;&]*--hard'
    || { grep -qE '\bgit\b[^|;&]*\bstash\b' && ! grep -qE '\bgit\b[^|;&]*\bstash\b\s+(list|show)\b'; } ...
```

**What gets blocked / not blocked:**

| Command | Result | Why |
|---|---|---|
| `git restore <file>` | BLOCKED | discards working-tree changes |
| `git reset --hard [<ref>]` | BLOCKED | `reset` + `--hard` (mixed/soft NOT matched) |
| `git clean -fd` | BLOCKED | deletes untracked files |
| `git stash` / `stash push` / `stash -q -- <file>` | BLOCKED | rewrites tracked working files to HEAD — a discard with a recovery buffer, still a discard |
| `git stash pop` / `apply` / `drop` | BLOCKED | pop/apply mutate the tree, drop destroys the buffer |
| `git stash list` / `git stash show …` | OK | read-only |
| `git -C <dir> restore …` / `git -C <dir> stash` | BLOCKED | the loose `\bgit\b[^|;&]*` span catches the `-C` evasion form too |
| `git checkout <branch>` / `-b` | OK | branch switch — common, safe |
| `git checkout … -- <file>` | **OK (hook can't tell it apart)** | same verb as branch-switch → NOT hook-guarded; rides on the AGENTS.md §6 prose rule instead |
| `git reset --soft` / `git reset` | OK | doesn't touch the working tree |
| `git status` / `git diff` / `git add` | OK | no discard |

**Why it exists.** AGENTS.md §6: never discard uncommitted work in tracked files without explicit per-file approval. **Crucially, this is a hook (not an `ask`/`deny` permission) because the maintainer runs with `bypassPermissions`, where `allow`/`deny`/`ask` rules are all ignored — only PreToolUse hooks still fire.** Scar (2026-06-21): a `spring-boot:run` regenerated a tracked `schema.graphqls` with bad escaping, and it got `git checkout`'d on an ambiguous "musste gefixt sein" instead of an explicit go — a §6 violation. Scar (2026-07-08): an agent ran `git stash -- <file>` to lint the HEAD version of a file — rationalized as "temporary + backed up" — reverting a file that carried two sessions' uncommitted work; recovered only because of the manual backup. The hook now hard-blocks the four discard verbs (stash except its read-only `list`/`show`); `git checkout … -- <file>` is unguardable (overloaded verb) and relies on §6 discipline. To compare against HEAD, extract a copy instead: `git show HEAD:<path> > /tmp/…`. If a discard is genuinely wanted, the user runs it via the `!` prefix.

**How to verify it's live.** Run `git restore .` (or `git reset --hard`, or `git stash`) — you should see `BLOCKED by AGENTS.md §6:` on stderr, nothing discarded.

## User-level — `~/.claude/settings.json`

These apply to every project, not just rapla. Documented here for completeness so you know what fires on session start/end.

### `SessionStart` — boot `wsl-screenshot-cli` daemon

```json
{ "type": "command", "command": "wsl-screenshot-cli start --daemon 2>/dev/null; echo 'wsl-screenshot-cli started'" }
```

Starts the clipboard-watching daemon at the beginning of every session. Idempotent — if already running, the start call is a no-op. Output `wsl-screenshot-cli started` lands as a system reminder so you can see it succeeded. See `docs/development.md` for what the daemon does (Win+Shift+S → Ctrl+Shift+V paste).

### `SessionEnd` — stop the daemon

```json
{ "type": "command", "command": "wsl-screenshot-cli stop 2>/dev/null" }
```

Cleans up the daemon on session exit so it doesn't accumulate across long-running shells.

## `autoMode` policy lines — not hooks, but related

`~/.claude/settings.json` also carries `autoMode.environment` and `autoMode.soft_deny` arrays. These are *policy text* the agent reads as context — they aren't enforced by a hook, just inform the agent's behaviour. Examples:

- "`mvn install` to `~/.m2/repository` is fine; `mvn deploy` is not." — guides the agent without blocking the tool call. (The actual block on `mvn install` here in rapla is a project-level override of that user-level allow, because rapla's reactor specifically hates it.)
- "Editing `.github/workflows/` requires explicit user direction." — surfaces as a `soft_deny` reminder, not a hard block.

`.agents/settings.local.json` adds the project-specific `autoMode.environment` notes (worktree layout, port offsets, cross-repo dhbwrapla read access).

## Adding a new hook

The lowest-friction path is the `update-config` skill — it knows the JSON shape and where to put a new entry. Manually:

1. Decide *user-level* (applies everywhere → `~/.claude/settings.json`) or *project-level* (rapla-only → `.agents/settings.json`).
2. Pick the event: `PreToolUse`, `PostToolUse`, `SessionStart`, `SessionEnd`, `UserPromptSubmit`, etc. The hook docs at `claude.com/docs/en/docs/claude-code/hooks` list the full set.
3. Pick the matcher (regex against the tool name — `Bash`, `Edit|Write`, etc.) — `""` matches everything.
4. The command receives the tool call as JSON on stdin. Use `jq` to extract fields. Exit code 0 = allow, 2 = block with stderr surfaced to the agent.
5. Verify the hook fires (e.g. for `PreToolUse: Bash`, trigger the matched pattern and confirm the block message).

## Worth adding (gaps)

Candidates that fit the rapla workflow but aren't wired yet — call out before implementing:

- **`PreToolUse: Bash` on `git push --force` to master** — soft-deny coverage exists in user-level `autoMode`, but no hard block. The user-level policy already covers feature-branch pushes.
- **`PostToolUse: Edit|Write` matching `**/*.java`** — auto-`mvn -pl <module> -am compile` to type-check the touched module. AGENTS.md §5 says to do this manually; a hook would make it free.
- **`PostToolUse: Edit|Write` matching `rapla-angular/src/**/*.ts`** — auto-`npm run build:fast`. Same shape.
- **`UserPromptSubmit`** — a one-line system reminder when the prompt mentions specific keywords (e.g. "deploy", "release") to surface relevant skills.

Don't add these blind — each adds latency to routine actions. Discuss before wiring.
