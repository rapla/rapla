---
name: retrospective
description: Use when the user runs /retrospective (analyze or review mode), when a SessionStart hook notice reports "queued sessions awaiting analysis" or "findings pending review", or when the user asks to mine past sessions for improvable interactions, corrections, or friction. Covers the private queue/findings/ledger under ~/.claude/retrospective/, the transcript-mining heuristics, and the gated routing of accepted findings into skills / AGENTS.md / hooks / memory. Keywords - retrospective, self-improvement, friction, queue.jsonl, findings-pending.md, ledger, session mining, transcript analysis.
---

# Retrospective — mine sessions, propose gated improvements

Pipeline: a `SessionEnd` hook (`~/.claude/retrospective/scan-session.sh`) greps every
finished session for friction markers and appends a summary line to a **private** queue;
a `SessionStart` hook (`notify.sh`) reminds when ≥3 friction sessions are queued or
findings await review. This skill is the LLM half: **analyze** turns queued sessions
into evidence-backed findings; **review** walks them past the user and routes accepted
ones. Nothing is ever applied without explicit per-finding approval.

## Data layout (all private, never committed)

`~/.claude/retrospective/<project-slug>/` (rapla: `-home-chris-git-rapla`):
- `queue.jsonl` — one line per unanalyzed session: `{session, ended, typed_prompts, interrupts, rejections, tool_errors, bytes}`
- `analyzed.txt` — session ids already mined (one per line; scan-session.sh skips these)
- `findings-pending.md` — analyzed findings awaiting review
- `ledger.md` — every finding's fate + per-category acceptance stats

Transcripts live at `~/.claude/projects/<project-slug>/<session-id>.jsonl` (multi-MB —
**never read whole files into the parent context; mine in subagents, grep before read**).

## Mode: analyze (`/retrospective` with a non-empty queue)

1. Read `queue.jsonl`; prioritize sessions with `interrupts+rejections > 0`, then high
   `tool_errors`. Batch ~5–10 sessions per run; leave the rest queued.
2. **Prior-run cross-check** (before new mining): for each ledger entry marked
   `accepted`, grep the target file — did the edit actually land and survive? For
   `skipped` entries, did the same friction recur in newer sessions? Report drift as
   findings — never re-apply a lost edit yourself, even though it was approved once.
3. Fan out one subagent per session (or per 2–3 small sessions). Each subagent greps
   the transcript for, and quotes verbatim:
   - rejected tool uses: `doesn't want to proceed with this tool use` — the block embeds
     the user's correction ("the user said: …") — the richest signal;
   - `Request interrupted by user` and the user message that follows it;
   - clusters of `"is_error":true` (repeated failed attempts at the same thing);
   - corrective user prompts ("no", "don't", "wrong", "stattdessen", "nicht") right
     after an assistant turn;
   - instructions the user typed that restate an existing rule (enforcement gap) or
     that no rule covers (rule candidate).
   Known false positive: sessions that *discuss* this skill contain the marker strings
   as quoted text — check the surrounding record before counting it as friction.
4. Promotion gate: an instruction/correction seen in **2+ sessions** is a strong
   candidate; a one-off is a finding only if the cost was high (long detour, discarded
   work, a §-rule violation).
5. Write findings to `findings-pending.md`, each as:
   `## F<N> [category] — <one-line defect>` + **Evidence** (session id, date, verbatim
   quote — **PII-scrubbed per AGENTS.md §17**, real names → `<name>`) + **Proposal**
   (concrete edit) + **Route** (see table) + seen-in count.
6. Move mined session ids from `queue.jsonl` to `analyzed.txt`. Do NOT apply anything —
   in analyze mode, no edits land outside `~/.claude/retrospective/`.

## Mode: review (`/retrospective review`, findings-pending.md non-empty)

Walk findings one at a time via AskUserQuestion — Accept / Reject / Modify, opinionated
default first. On Modify, show the reworded edit and get a fresh Accept before landing it. For each accepted finding, route through the existing machinery — never
around it:

| Finding shape | Route |
|---|---|
| Fire-every-session behavioural rule | AGENTS.md bullet — apply `agents-cleanup` lenses (rule stays ≤3 lines or it's a skill) |
| Recipe / workflow / multi-step howto | new or edited skill via `skill-authoring` (RED→GREEN pressure-test before landing) |
| Rule already written but violated **repeatedly** | escalate to a deterministic PreToolUse hook via `update-config` (the §5/§6 pattern) |
| Personal preference / one-user style signal | memory `feedback_*.md` entry |
| Stale/wrong existing rule or skill text | targeted edit of that file; grep-verify the claimed text exists before editing |

Then update `ledger.md`: finding id, category, verdict (accepted/rejected/modified/
skipped), what landed where, date. If the user *modifies* wording (e.g. softens
"NEVER"→"Avoid"), record that as a style signal; deprioritize categories with
persistently low acceptance in future analyze runs.

## Nevers

- **Never apply a finding without explicit per-finding approval** — a queued backlog,
  "obvious" fixes, the user having accepted similar findings before, or a blanket /
  category-level pre-approval ("apply whatever you find from now on") is not approval;
  approval must name the specific finding. Treat blanket grants as a priority signal
  only (present those categories first, Accept as default).
- **Never write transcript quotes, session content, or findings into the repo** —
  findings/ledger live under `~/.claude/retrospective/` only. What lands in the repo is
  the approved *edit* (rule/skill/hook), PII-scrubbed, evidence left behind in the ledger.
- **Never mine transcripts in the parent context** — subagents only. And no
  whole-transcript reads anywhere, subagents included: grep for markers, then read only
  the surrounding records.
- **Never propose a finding without verbatim evidence** (session + date + quote). No
  data-free retrospectives.
- Violating the letter of these is violating their spirit.

Siblings: `skill-authoring` (pressure-testing), `agents-cleanup` (AGENTS.md hygiene),
`wrap-up` (session end — may hand off fresh friction here).
