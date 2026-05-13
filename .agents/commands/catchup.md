---
description: Print a tight orientation report — branch state, recent commits, modified files, active PRDs, dev-server status. Useful after /clear, /compact, or when resuming work on long-lived branches like spring-boot. Read-only; no side effects.
---

Probe the current state of the rapla checkout and produce a single scannable report. Run the probes in parallel where possible. Keep the output under ~40 lines; don't explain the sections, the structure is enough.

## Probes to run

1. **Branch and divergence**
   ```bash
   git -C /home/chris/git/rapla branch --show-current
   git -C /home/chris/git/rapla rev-list --left-right --count master...HEAD 2>/dev/null
   ```

2. **Working-copy state** (uncommitted edits left behind?)
   ```bash
   git -C /home/chris/git/rapla status -s | head -30
   ```

3. **Recent commits**
   ```bash
   git -C /home/chris/git/rapla log --oneline -10
   ```
   If the active branch is `spring-boot`, also: `git log master..spring-boot --oneline | wc -l` to confirm the divergence count.

4. **Files touched in the last 7 days** (where activity actually happened)
   ```bash
   git -C /home/chris/git/rapla log --since='7 days ago' --name-only --pretty=format: 2>/dev/null | sort -u | grep -v '^$' | head -20
   ```

5. **Active PRDs** (in-progress status)
   ```bash
   grep -lE '^\*\*Status:?\*\*.*in-progress' /home/chris/git/rapla/docs/prd/*.md 2>/dev/null | xargs -I{} sh -c 'basename "{}" .md; head -1 "{}" | sed "s/^# */    /"'
   ```

6. **Active worktrees** (only if >1)
   ```bash
   git -C /home/chris/git/rapla worktree list
   ```

7. **Service status**
   ```bash
   jps -l 2>/dev/null | grep RaplaSpringBoot && echo "  Spring Boot: RUNNING" || echo "  Spring Boot: not running"
   curl -sf -o /dev/null -w "  HTTP :8051 → %{http_code}\n" http://localhost:8051/raplaclient.jnlp 2>/dev/null || echo "  HTTP :8051 → DOWN"
   claude mcp list 2>/dev/null | grep -E 'playwright|github' | sed 's/^/  /'
   wsl-screenshot-cli status 2>/dev/null | head -2 | sed 's/^/  /'
   ```

## Output format

Present as a single block, scannable in one glance:

```
═══ rapla catchup ═══

Branch: <current>  (master..HEAD: <ahead> | <behind>)

Working copy: <N files modified | clean>
  <flag any conflicts or staged-but-uncommitted state>

Recent commits (last 10):
  <hash> <subject>
  …

Files touched this week:
  <top 10 modified paths>

Active PRDs (in-progress):
  - PRD NNN: <short title>
  …

Services:
  Spring Boot: <status>
  HTTP :8051 → <code>
  playwright MCP: <connected?>
  wsl-screenshot-cli: <status>

(N worktrees: <list if >1>)
```

After printing the report, if anything is in a "needs attention" state (uncommitted edits, server down when it should be up, etc.), call it out in one sentence at the end. Don't propose next actions — just orient.
