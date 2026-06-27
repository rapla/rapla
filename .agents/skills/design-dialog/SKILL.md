---
name: design-dialog
description: Use before coding a feature/change that's bigger than a trivial edit but
  doesn't yet warrant a full PRD — when the design space is open, you're weighing 2+
  approaches, or the user said "lass uns überlegen"/"discuss". Stop and design first.
---

# Design Dialog

The gap between a one-line Edit and a full PRD (`prd-management`). For changes where
*how* to do it isn't obvious yet — don't jump to code, design first.

## When to use
- 2+ plausible approaches with real trade-offs.
- Touches a cross-cutting seam (auth, storage, permissions, GraphQL schema, the reactor split).
- User signalled discussion: "lass uns überlegen", "discuss", "wait" → NO edits until
  explicit go (the discuss-means-wait feedback rule).

## When NOT to use
- Trivial/mechanical fix → just do it (test-first per AGENTS.md §1).
- Decision with rationale + scope + alternatives worth keeping → that's a PRD, load
  `prd-management` instead. If a design-dialog grows past ~3 sections, promote it to a PRD.

## Process
1. Read the relevant `docs/` + matching PRD first (§2a) — the invariant may already be documented.
2. Ask clarifying questions one at a time (AskUserQuestion for real forks). Don't batch.
3. Propose 2-3 approaches with trade-offs, scaled to the change. Recommend one.
4. Present the design in sections; get approval per section.
5. Apply YAGNI — target only the necessary change, no drive-by refactors.
6. On approval: if it's PRD-worthy, write it up (`prd-management`); else proceed
   test-first. The design lives in the conversation/PRD, NOT a separate specs/ tree.
