---
status: "accepted"
date: 2026-06-27
decision-makers: Christopher Kohlhaas
consulted: 2026-06-27 web research on AI-authored documentation (generated-vs-manual, living-architecture, AI-doc-regeneration failure modes, Red Hat code-diff→docs-PR)
informed: future contributors, AI coding agents
---

# Docs are AI-maintained by conversational targeted diff, not regenerated

## Context and Problem Statement

PRDs are *change-shaped* (a decision over time); the docs for the architecture, admin, and developer
audiences are *state-shaped* ("what the system does now"). Deriving current state from 90 PRD deltas by hand
does not happen, so those docs drift (the `docs/prd/README.md` index ran ~40 PRDs stale).
How do we keep the architecture/admin/developer docs current **without** falling into the AI-regeneration
trap, where regenerating a doc from code/PRDs strips the human-curated context, warnings and "why"?

(End-user *usage* docs are out of scope here — they describe using the app, which no build-time PRD contains,
so they are authored separately, not derived.)

Related: [PRD 088](../prd/088-spec-graph-formalization.md) (this MADR is the extracted, durable form of its
decision **D7**, and the readability evidence behind **D6**); [ADR 0002](0002-no-sdd-framework-as-tool.md)
(no SDD framework as a tool); [ADR 0001](0001-use-madr-for-architecture-decisions.md) (MADR / the durable-why
layer this maintains).

## Decision Drivers

- **Trust the agent, but never let it silently wipe curation.** It is enough that the agent *says* what it
  wants to change; the human discusses or says yes.
- **Flexibility** — docs must carry whatever the content needs (inline code examples for the AI, PlantUML for
  humans); a fixed schema cannot (D6).
- **Solo maintainer, low ceremony** — no CI-checkbox-PR bot, no second doc tree.
- **The doc layer already exists** — `docs/architecture/` (15 pages), `docs/decisions/` (MADR),
  `docs/development.md`. This is a *keep-current* problem, not a *create-docs* problem.

## Considered Options

- **A. Wholesale AI regeneration** of each doc from PRDs/code on change.
- **B. Machine-generate everything** deterministically (OpenAPI/schema dumps).
- **C. Manual hand-authoring only.**
- **D. AI-maintained by conversational targeted diff** — agent announces the intended change, human
  discusses or says yes, edit is scoped to the affected section. *(chosen)*
- **E. Heavy CI bot** — GitHub Action posts a docs-PR with accept/reject checkboxes per file (Red Hat shape).

## Decision Outcome

Chosen option: **D — AI-maintained by conversational targeted diff.** The model collapses to **one track**:
the docs are AI-authored prose, and the agent keeps them current by **announcing the intended change**
("PRD Z makes §Y of `architecture/foo.md` stale — I'd change it to …"), the human **discussing it or saying
yes**, and the edit being **targeted to the affected section, never a wholesale page rewrite**. The
machine-generated reference layer (schema/endpoint catalog) is a vanishing fraction at rapla — mentioned
inline where useful, not a structured pillar — so B is a footnote, not a mode.

Two guardrails remain, and both are about **correctness, not ceremony**:

1. **Announce-before-apply** — the agent states what+why before editing, so a wholesale change is never
   *silent*. A single "yes" suffices. (This is the user's "es reicht wenn sie sagt was sie ändern will".)
2. **Targeted-not-wholesale** — the agent edits the affected section, not the whole page, so human-curated
   context/warnings survive. This holds even for a *trusted* agent; it is not a trust gate.

### Consequences

- Good, because docs stay current with near-zero ceremony and human curation is preserved.
- Good, because it needs no tool, no second tree, no schema — just a skill rule over the existing `docs/`.
- Bad, because there is no hard CI gate; correctness rests on the agent honouring the two guardrails and on
  the human reading the announced change. Accepted — a non-blocking advisory drift-check (below) is the only
  mechanical aid we add.

### Confirmation

This is a process decision, so the check is softer than an arch-test, but it is nameable and reviewable:

- A **doc-coauthoring skill** (`.agents/skills/doc-coauthoring/`) encodes the two guardrails: announce intent + targeted edit;
  no wholesale page rewrite without an explicit "yes".
- **git history review:** doc commits are section-scoped diffs, not full-file rewrites — a full-file rewrite
  of a curated page is the smell this decision exists to prevent.
- An **optional non-blocking advisory drift-check** (a glob mapping changed files/PRDs → possibly-stale doc
  sections, posting an informational note — never blocking) — the Living-Architecture pattern, added only if
  drift recurs.
- **This ADR is the first dogfood:** it was authored via the announce→yes workflow (the maintainer's
  instruction to create it was the "yes").

## Pros and Cons of the Options

### A. Wholesale AI regeneration

- Bad, because regeneration **wipes human additions** — "when code changes and AI regenerates documentation,
  all that work disappears" (shellnetsecurity, 2026). The central failure mode this decision rejects.
- Bad, because hallucination ships "90% accurate and 10% dangerously wrong" with equal confidence.

### B. Machine-generate everything

- Good, because deterministic and drift-free for reference material (schema, signatures).
- Bad, because it cannot encode architectural reasoning, the "why", migration narratives (Fern,
  "generate API references, manually author conceptual guides"). At rapla the reference slice is tiny.

### C. Manual only

- Good, because maximal control and curation.
- Bad, because it drifts — nobody re-derives state from 90 PRDs by hand; "4–6 hours per release" is endemic.

### D. Conversational targeted diff (chosen)

- Good, because it pairs the agent's drafting speed with the human's final authority at near-zero ceremony.
- Good, because "AI suggests, you decide; nothing gets merged without human approval" (Red Hat) — but
  conversational, not a checkbox PR.
- Bad, because no mechanical gate (see Confirmation).

### E. Heavy CI checkbox bot

- Good, because explicit per-file accept/reject, good for a large distrusting doc team.
- Bad, because it is ceremony rapla does not need — a solo maintainer who trusts the agent wants
  "announce → yes", not a PR with checkboxes.

## Future possibilities

- Pressure-test + finalize the **`doc-coauthoring` skill** once the workflow is proven on one architecture page (PRD 088 Phase 1).
- Add the **advisory drift-check** as a glob-based PR comment if drift recurs despite the announce workflow.
- An **admin/ops spine**: consolidate the scattered `deployment.md` / `signing.md` / `sslconfig.md` /
  `setup-wsl.md` / `plugins.md` under one index — the admin audience's keep-current entry point.

## More Information

- PRD 088 **D6** (rigidity, not binary, is the objection to SDD tooling) and **D7** (this decision).
- Evidence: "The Documentation Problem: How AI Changes Technical Writing" (shellnetsecurity, 2026) —
  regeneration-wipe + hallucination; Fern "Generated vs Manual Documentation" — the generate/author split;
  "Living Architecture for AI Agents" (ceaksan) — the advisory glob drift-check; Red Hat "code diff → docs PR
  in one comment" — propose-diff-not-regenerate; Böckeler (martinfowler.com) — "I'd rather review code than
  all these markdown files".
- Related: [ADR 0001](0001-use-madr-for-architecture-decisions.md), [ADR 0002](0002-no-sdd-framework-as-tool.md).
