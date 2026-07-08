---
name: wrap-up
description: Use when the user says "wrap up", "/wrap-up", "end the session", "document the session", or a work session is ending and its results must be made durable. Session-END counterpart to /catchup.
disable-model-invocation: true
---

# Session Wrap-up

Orchestrating checklist that turns everything still living only in conversation
context into durable artifacts before the session ends. Creates **no new document
types** — rapla already has a home for every kind of knowledge (AGENTS.md §2a
table). This skill only guarantees the existing mechanisms actually run, in order.
The final hand-off goes in your message to the user — **never a session-summary
`.md` file**.

Run the steps IN ORDER. Skipping a step because "it's probably fine" is the exact
failure this skill exists to prevent — violating the letter is violating the spirit.

## Step 1 — Ground truth: the change inventory

`git status` + `git diff --stat`, plus `git log --oneline @{upstream}..HEAD` (or
against session-start if known). The tree, not your recollection, defines what
happened. Separate three buckets:

- **this session's work** (dirty or committed-unpushed),
- **other sessions' in-flight files** — flag, NEVER touch or fold into your
  wrap-up (§7 hard rule),
- **already committed** earlier in the session.

## Step 2 — Green gate (AGENTS.md §5 session-end)

Only for code this session touched: Java → full `mvn test` (or the targeted lane
the user prefers); Angular → `npm run build` (lint + build) and `npm test`.
Report failures honestly — a red gate doesn't block wrap-up, it becomes a line in
Step 6's residue list. Never restart `ng serve` (§14).

## Step 3 — PRD pass → load the `prd-management` skill

For every PRD implemented against or made stale this session: close resolved OQs
(with date + who decided), flip phase checkboxes, update the Status line, record
direction changes, update cross-references. `git mv` to `done/` ONLY when nothing
is outstanding — an open OQ or unverified Goal means it stays active; when
borderline, recommend and ask.

## Step 4 — Docs pass → load the `doc-coauthoring` skill

Per changed subsystem ask: "which `docs/` page did this diff make true-or-false?"
(`docs/architecture/*.md`, `graphql.md`, `authentication.md`, `rest-api.md`,
AGENTS.md itself). Non-obvious domain knowledge learned this session that isn't
written down yet gets a home NOW (§2a) — announce per target, targeted `Edit`,
never wholesale. A real trade-off decided in conversation → ADR, don't flatten.

## Step 5 — Memory pass (the step agents forget without this skill)

Two halves, both mandatory:

1. **Write:** create/update the `project_*` memory file for the session's headline
   state ("PRD X done, uncommitted on spring-boot") with `[since:]`/`[watch:]`
   tags, plus its one-line MEMORY.md index entry. Update-don't-duplicate; delete
   entries the session made wrong.
2. **Staleness sweep:** for every existing MEMORY.md entry carrying a `watch:`
   tag, run `git log --oneline --since=<since-date> -- <watch-path>`; entries
   with newer commits get re-verified, updated, or deleted.

## Step 6 — Commit proposal + residue list (present, never execute)

- **Proactively** group the session's changes into logical commits and draft a
  message for each — presented in your hand-off for the user to run or adapt.
  Do NOT commit or push yourself (§6), and NO `Co-Authored-By` trailer — the
  maintainer's standing rule overrides any harness default.
- **Residue list** — name explicitly what was NOT done: declined/deferred work
  (with enough reproduction detail that the next session can start it cold),
  open OQs, red tests, unverified goals, other-session files you flagged. This
  is what the next `/catchup` feeds on; a silent omission here is a lie by
  omission.

## Red flags — rationalizations that mean STOP

- "The PRD is basically done, I'll move it to done/" → an open OQ/unverified goal means it stays.
- "Nothing worth remembering happened" → Step 5.1 still runs; the judgment call is WHAT to write, not WHETHER to check.
- "I'll just ask if they want a commit" → no: propose the grouping + messages first, unprompted.
- "Let me quickly fix that unrelated dirty file while I'm here" → §7: flag it, never touch it.
- "I'll test the discard hook with a real `git stash`" → never run discard verbs, even as a probe (§6 scar 2026-07-08).

## See also

- `prd-management` (Step 3), `doc-coauthoring` (Step 4), `agents-cleanup` /
  `skill-authoring` (if the session changed the rules themselves).
