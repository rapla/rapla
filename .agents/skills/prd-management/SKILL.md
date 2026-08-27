---
name: prd-management
description: When to create, reopen, or close a PRD in rapla. Load before starting any new feature, fix, or refactor.
---

# PRD Management

Format reference: see any PRD in `docs/prd/` (e.g. `docs/prd/067-server-mutation-unification.md`).

## When to create
No matching PRD in `docs/prd/` or `docs/prd/done/` → create `docs/prd/NNN-short-name.md` before writing code. Pick the next free NNN (`ls docs/prd/ docs/prd/done/ | grep -oE '^[0-9]+' | sort -n | tail -1` + 1).

## When to reopen
Matching PRD in `docs/prd/done/`:
```
git mv docs/prd/done/NNN-name.md docs/prd/NNN-name.md
```
Flip status to `in-progress`, add new work.

## When to close
All phases shipped, status `done`:
```
git mv docs/prd/NNN-name.md docs/prd/done/NNN-name.md
```
Update cross-references in still-active PRDs that link to it.

## When a decision is overturned mid-session

Grep the PRD for **every** statement of the old decision and rewrite them all in the same
edit — never append the new winner as a section while a stale "Recommendation:"/decision
header stands above it. Scar (2026-06-20, PRD 074): the directives-vs-CEL winner was
appended, CEL stayed "the decision" in four places, and a day of examples was authored
against the dead decision before the user caught it twice.

## Multi-session PRD areas

Assert only decisions the PRD records. A decision you remember (or infer) that isn't
written down is flagged as unconfirmed — "the PRD doesn't record this; I recall X, please
confirm" — never re-proposed as if open or asserted as settled. Scar (2026-07): a
discarded CSP design was re-proposed because the decision lived only in a parallel
session's context; the user had to point at the other transcript.

Phase plans always carry checkbox status (`- [ ]` / `- [x]`, per the Template below) —
a PRD where "what's already implemented" isn't visible from the Plan section is stale.

## Before ending a session
Update any PRD touched this session: close resolved open questions, mark phases done/in-progress, note direction changes. Stale PRDs cause the next session to re-litigate decisions already made.

Throughout PRD creation and implementation — not just at the end — ask: which `docs/` files are affected by what I just learned or shipped? Update or create them inline. Domain knowledge that lives only in session context disappears.

Typical candidates: `docs/architecture/rest-api.md` (endpoints), `docs/architecture/flows.md` (data flow), `docs/authentication.md` (auth), `docs/graphql.md` (schema/resolvers), `docs/architecture/<topic>.md` (domain knowledge), `AGENTS.md` (new rules or wiring patterns).

Whenever you learn something non-obvious about how a subsystem works, that's a signal a doc needs updating or creating — add it immediately, not as a follow-up.

## Template

```markdown
# PRD NNN — Short descriptive name

**Status:** draft — YYYY-MM-DD
**Related:** PRD NNN (reason)

## Abstract

2-3 sentences: what this PRD does, why now, and what the measurable end state is.

## Implementation

Key technical decisions and patterns — classes to touch, abstractions to extend,
what to avoid. Updated as work progresses so future sessions see what was done.

## Goal

Measurable end state from the outside (curl probe, arch-test, zero occurrences of X).

## Scope

### In scope
- ...

### Out of scope
- ...

## Plan

### Phase 1 — Name
- [ ] Task

## Tests

How to verify done: targeted test class, curl probe, arch-test.

## Open Questions

- **OQ1** — question. *Resolution:* pending.

## Decisions locked

**D1 — title.** Rationale. Alternatives rejected and why.
```
