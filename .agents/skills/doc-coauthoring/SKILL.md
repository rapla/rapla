---
name: doc-coauthoring
description: Use when writing or updating rapla docs — an architecture/admin/developer page under docs/, a PRD, or an ADR — especially after a PRD lands and its docs must catch up. Carries the announce→approve, targeted-edit (never wholesale-rewrite) co-authoring workflow from ADR 0004. Skip for code-only changes and for end-user *usage* docs (those are authored separately, not derived from build-time PRDs).
---

# Doc Co-Authoring

How rapla's architecture / admin / developer docs stay current as PRDs land — without the
AI-regeneration trap (regenerating a doc wipes the human-curated context/warnings/why). The
durable decision is **[ADR 0004](../../../docs/decisions/0004-ai-maintained-docs-conversational-diff.md)**
(extracted from PRD 088 D7). This skill is the *how*; the mechanics are adapted from Anthropic's
`doc-coauthoring` skill.

## Trigger (pull) + the optional nudge

- **Pull:** the user says "update the docs for PRD X", "the architecture page is stale", "write an ADR",
  or you just closed a PRD (`git mv docs/prd/NNN → done/`) and its docs must catch up.
- **Optional push nudge:** a non-blocking hook on PRD close *reminds* you to run this — it does not run it.
  The nudge is the "when"; this skill is the "how"; the human is the "yes".

## The two guardrails (correctness, not ceremony)

1. **Announce before you apply.** State *what* you'll change and *why* before editing, per target. The
   user discusses or says yes. A single "yes" is enough — don't build a checkbox gate.
2. **Targeted, never wholesale.** Edit the affected section with `Edit` (str_replace), never reprint or
   regenerate a whole page. Wholesale rewrites strip human-curated context — the central failure mode.

## Workflow

1. **Find the targets.** Which docs does this change make true-or-false? Typical candidates (from
   `prd-management`): `docs/architecture/rest-api.md` (endpoints), `flows.md` (data flow),
   `domain-model.md`, `permissions.md`, `docs/authentication.md`, `docs/graphql.md`,
   admin pages (`deployment.md`, `signing.md`, `sslconfig.md`, `setup-wsl.md`, `plugins.md`),
   `AGENTS.md` (new rules/wiring), an ADR (a cross-cutting decision), **and the PRD(s) that spec this
   change** — a PRD is both a grounding source (the intent) and a possible stale target (AGENTS.md §6:
   update outdated PRDs). List them for the user.

2. **Ground the change in the source — read the code, not your memory.** A doc mirrors code reality; to know
   what it *should* say, inspect the source it describes, not just the PRD: the live `schema.graphqls` +
   GraphQL resolvers for `graphql.md`, the `@HttpExchange` interfaces for `rest-api.md`, `SecurityConfig` +
   grants for auth, the entity classes for `domain-model.md`. Diff the doc against that source to find what
   *actually* drifted — that is the answer to a vague "update the X docs". Also read the PRD that specs the
   change: it carries the *intent*. A code-vs-PRD mismatch (e.g. the schema caps a list at 20 but the PRD
   still says 50) is drift in **one** of them — surface it as a question, never silently pick a side. Skip
   only for a self-evidently doc-internal edit (a typo, a dead link). A high keyword count is not a target:
   `fat-jar-inventory.md` mentions "graphql" 23× but it's a dependency list, not prose.

3. **Announce per target.** "PRD Z makes §Y of `architecture/foo.md` stale — I'd change <this> to <that>
   because <reason>." Wait for discuss-or-yes. On "lets discuss"/"wait" → present options, don't edit
   (AGENTS.md §6 / discuss-means-wait).

4. **Apply targeted edits.** `Edit` the affected lines only (or create the file, for a new ADR/page). Match
   the existing tone, depth and structure — you are amending a curated doc, not regenerating one. Authoring a
   *new* doc uses the same loop, not a separate one: its context already lives in the PRD/session, so skip the
   cold-start interview Anthropic's `doc-coauthoring` runs (ADR 0004 was written this way).

5. **Preserve productive tensions — don't flatten.** If the change surfaces a *real* trade-off (two valid
   approaches optimizing different priorities), do NOT resolve it silently in prose. Capture it in an
   **ADR** (`docs/decisions/`, MADR format) and link it. Flattening the "why" is how AI docs go hollow.

6. **Reader-test substantial additions (scaled).** For a new section or a non-trivial rewrite, dispatch a
   fresh subagent with *only* the edited doc + 5–10 questions a real reader would ask; fix what it gets
   wrong or finds ambiguous. Skip for a one-line correction — pick the cheapest check that fits.

## Nevers

- **Never wholesale-rewrite or regenerate a curated page** to apply a small change — targeted `Edit` only.
- **Never apply a doc change silently** — announce intent first; a "yes" (or the explicit instruction that
  started the task) is the gate.
- **Never flatten a real trade-off into a single prose choice** — route it to an ADR.
- **Never derive end-user *usage* docs from PRDs** — PRDs describe building, not using; author those separately.
- **Never invent facts** — verify endpoint paths, config keys, signatures against the source before writing
  them; flag uncertainty rather than guessing (AI docs ship "90% right, 10% dangerously wrong").

## See also

- [ADR 0004](../../../docs/decisions/0004-ai-maintained-docs-conversational-diff.md) — the durable decision + evidence.
- `prd-management` skill — the PRD lifecycle that triggers this (its inline "which docs are affected?" prose points here).
- [PRD 088](../../../docs/prd/088-spec-graph-formalization.md) D6/D7 — the rationale (rigidity, flexibility, three-modes-to-one).
- `skill-authoring` skill — pressure-test this skill with a subagent before relying on it.
