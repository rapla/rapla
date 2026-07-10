---
kind: research-findings
topic: spec-driven development, spec-as-source, spec graphs, AI-driven specs
gathered: 2026-06-24
method: multi-agent web research (10 agents across 4 rounds)
informs:
  - "[[088-spec-graph-formalization]]"
  - docs/decisions/0001-use-madr-for-architecture-decisions.md
  - docs/decisions/0002-no-sdd-framework-as-tool.md
status: reference — evidence base, not a decision (decisions live in docs/decisions/)
---

# Spec-driven development & spec-as-source — research findings

Evidence base for rapla's spec-as-source / spec-graph work ([PRD 088](../prd/088-spec-graph-formalization.md)). External research only —
the decisions this evidence supports live in `docs/decisions/` (MADRs) and [PRD 088](../prd/088-spec-graph-formalization.md). Every claim
is cited; this file is a durable reference so future sessions don't re-research the same ground.

## 1. Why this matters for rapla

rapla is mature 5-module Java + Angular, single maintainer, heavily AI-agent-driven. It already
practices a form of SDD (90 PRDs, 15 architecture docs, §2a knowledge routing, D1..Dn locks). The
question is **how to formalize that toward "spec as source" without adopting an off-the-shelf tool
wholesale** — and specifically what helps *AI-driven development*.

## 2. Framework landscape (2025–2026)

| Tool | Model | On-disk | Fit for rapla |
|---|---|---|---|
| **OpenSpec** (Fission-AI) | brownfield; `specs/`=durable, `changes/`=delta, `archive` merges back | `openspec/specs/<cap>/spec.md`, `changes/<name>/{proposal,design,tasks}.md` + path-mirrored delta | closest fit; **plundered as ideas, not adopted as tool** (D1) |
| **GitHub Spec Kit** | 5-phase gate (constitution→specify→plan→tasks→implement), Python CLI | `.specify/`, spec.md/plan.md/tasks.md | too ceremonious for solo maintainer |
| **AWS Kiro** | agentic IDE, EARS requirements, steering files | requirements.md/design.md/tasks.md | commercial IDE lock-in |
| **Tessl** (Guy Podjarny, $125M Series A) | purest spec-as-single-source; code marked `GENERATED FROM SPEC` | 1:1 spec↔file | private beta, non-deterministic regen, unrealistic at 189k LOC |
| **BMAD** | agent personas (analyst/PM/architect/dev) | PRD→story shards | persona theater for one maintainer |

## 3. The proponent ↔ skeptic debate

**Proponents — "spec is the new source code":**
- **Sean Grove (OpenAI), "The New Code"**: code is *"10–20% of the value… the other 80–90% is structured communication"*; *"code is a lossy projection from the specification"*; vibe-coding *"shreds the source and version-controls the binary."* OpenAI's Model Spec (markdown, IDs + example prompts as tests) is the worked example.
- **Andrej Karpathy (Software 3.0)**: *"the English instructions to an LLM are the code."*
- **Tessl / Podjarny**: AI-native, spec-centric; Spec Registry of 10k+ library specs.

**Skeptics:**
- **Fowler & Böckeler** (the most-cited measured skeptic): spec-as-source risks *"the downsides of both MDD and LLMs: **inflexibility AND non-determinism**."* Kiro made *"4 user stories + 16 acceptance criteria for a small bug fix"*; spec-kit markdown is *"verbose and tedious to review"* — *"I'd rather review code."* Fowler's positive alternative: **Structured-Prompt-Driven Development** (governed, versioned prompts — not full spec-as-source).
- **"Waterfall in Markdown"** (Alvis Ng): *"If nobody read the Confluence page, nobody is reading your .specify folder."*
- **marmelab "Waterfall Strikes Back"**: six failure modes (context blindness, 1300 lines for a date feature, over-engineering, double review burden, false security, diminishing returns).
- **The "you just wrote a program" paradox** (Jason Gorman): pushing a NL spec toward unambiguity drives it toward formal notation — *"source code is a program specification."* If it's precise enough to be unambiguous, it *is* code, and you've lost stakeholder readability.

**Empirical retrospective — Scott Logic / Colin Eberhardt** (the load-bearing number): GitHub Spec Kit vs plain iterative prompting on a real feature → **agent 33m vs 8m, human review ~3.5h vs ~15min, 2,577 lines of markdown for 689 lines of code, ~10× slower — and still shipped an obvious bug.** Verdict: *"not a viable process, at least not in its purest form."* (A DEV experiment likewise found a plain `instructions.md` beat OpenSpec.)

**Strongest pro-rebuttal — Marc Brooker (AWS):** *"almost all programs are already specified in natural language"*; precision is recovered by *slipping into* Rust/SQL/TLA+ only where it matters; SDD *"pulls designs up, not up-front"* (iterated, upstream — the opposite of waterfall's all-requirements-known premise).

**Convergence (mid-2026):** nobody serious defends *pure* spec-as-source with throwaway code today. The realistic, defended position is **spec-anchored / spec-as-review-gate**, with behaviour pinned by **property/example-based tests**, not byte-identical regeneration.

## 4. Documenting decision rationale + rejected alternatives + future ideas

Universal pattern across ADR/MADR/RFC/PEP/RFD: **two artifacts, different lifespans, linked not merged.** Nobody merges rationale into the living spec.
- **MADR** (richest for weighed alternatives): `Considered Options` + per-option `Pros and Cons` (`Good/Neutral/Bad, because…`) + `Confirmation` (binds the decision to an enforceable check) + `status` (proposed/accepted/superseded).
- **Rust RFC** splits forward-thinking into three non-interchangeable sections: `Rationale and alternatives` (roads not taken, decided now) · `Unresolved questions` (deferred but bounded, must-answer) · `Future possibilities` (parking lot, **no commitment**).
- **Immutability + superseding**: never rewrite an accepted decision; new doc, status flips, reciprocal links (PEP `Replaces`/`Superseded-By`, IETF `Obsoletes`/`Obsoleted by`, adr-tools `adr new -s N` auto-rewrites the superseded one).
- **The OpenSpec gap** ([intent-driven.dev](https://intent-driven.dev/blog/2026/04/29/spec-driven-development-with-adr/)): `design.md` is archived with the change → *"that rationale becomes invisible to future proposals"* → community fix is to keep **ADRs as a separate durable tree**. This is exactly rapla's `docs/decisions/`.

## 5. Format weaknesses & reviewability (OpenSpec / EARS / Gherkin) — and fixes

- **OpenSpec format is rigid** (`## Requirements`/`### Requirement:`/`#### Scenario:` hardcoded in parsers — issue #666; *"scenarios MUST use exactly 4 hashtags … fail silently"*). Doesn't scale to complex requirements (repetitive preconditions — #1077). Users ask for diagrams (#439). Even the *generator AI* mis-formats it (#201).
- **EARS** (`While/When/Where/If…the system shall`): great for atomic SHALL requirements; **no construct for flow / holistic behaviour / relationships** — each requirement is an island.
- **Gherkin/BDD**: conveys concrete intent but degrades into verbose, imperative, brittle scenarios at scale; Cucumber's own guidance: 3–7 steps, declarative-not-imperative, one requirement per scenario.
- **Specification by Example** (Gojko Adzic): concrete *key examples* communicate intent better than abstract requirements and double as living-documentation tests. But *"the value is the conversation, not the artifact."*
- **The consensus (no single format suffices):** layer **atomic requirements + worked examples + diagrams-as-code (C4/Mermaid/PlantUML) + narrative prose**, each covering the others' blind spot. Cognitive basis: **Dual Coding Theory** (verbal + visual channels lower cognitive load — *when aligned and non-redundant*).
- **"Illusion of work"** (Spec Kit #1784): *"LLMs don't degrade gracefully with blob specs; they prioritize whichever instructions appear most"* → **split into typed semantic blocks.**

## 6. What helps AI-driven development specifically (the key section)

- **Examples — strongest, best-established lever.** Few-shot beats instructions-alone consistently, *largest gains on complex output formats* (the spec case). A worked example needs **both input AND output** — dropping either *"substantially decreases performance"* (TABLET, arXiv 2304.13188). Anthropic: examples are *"one of the most reliable ways to steer output,"* 3–5, relevant + diverse + structured. Addy Osmani: *"One real code snippet beats three paragraphs."*
- **Diagrams help AI — but ONLY as text.** **IsoBench (COLM 2024, arXiv 2404.01266): the identical problem as an image vs. text costs Claude-3 Opus −28.7 pts, GPT-4 Turbo −18.7, Gemini −14.9.** LLMs read Mermaid/PlantUML *source* competently (MermaidSeqBench ~91% syntax). **Rule: keep diagram source text in the spec; render to image only for humans; never give the agent image-only.**
- **Executable acceptance criteria > prose** (Simon Willison: conformance suite as spec; Anthropic: *"show passing test output"*). This is rapla's arch-test culture and our ✅ PINNED tags.
- **Specificity, not volume**: long abstract requirement lists are a documented anti-pattern (Böckeler); the signal is concrete behaviours + examples + verifiable "done."

## 7. AI-driven merge — the decisive finding

**Even OpenSpec does not trust the LLM to perform the change→spec merge.** Two distinct mechanisms ship, depending on entry point:
- **`openspec archive` (CLI) — deterministic.** `specs-apply.js` applies deltas in fixed order **RENAMED → REMOVED → MODIFIED → ADDED**, matches requirements **by normalized header text** (`normalizeRequirementName`), three validation gates. ([DeepWiki: archive Command](https://deepwiki.com/Fission-AI/OpenSpec/6.6-archive-command))
- **`/opsx:sync` (agent skill) — LLM-driven "Intelligent Merging."** The agent reads deltas + main specs and applies changes *"using its reasoning."* ([DeepWiki: /opsx:sync](https://deepwiki.com/Fission-AI/OpenSpec/3.8-opsx:sync-spec-synchronization))

The shipped agent path **bypasses the tested CLI** — flagged as a risk in issues [#863](https://github.com/Fission-AI/OpenSpec/issues/863) (*"Why ask an AI to re-implement archive logic manually when a tested CLI command exists?"* → *"inconsistency, missed edge cases, divergence"*) and [#913](https://github.com/Fission-AI/OpenSpec/issues/913).

OpenSpec's own checked-in [`openspec-parallel-merge-plan.md`](https://github.com/Fission-AI/OpenSpec/blob/main/openspec-parallel-merge-plan.md):
- concedes header-text matching is **brittle** (*"renaming a requirement could sever its connection"*);
- proposes a **fingerprint/hash check** — recompute the requirement hash from the live spec, *"if the hash differs from the stored base, abort and instruct the user to rebase"*;
- explicitly **keeps humans in the loop**: *"a deterministic, reviewable conflict resolution flow that mirrors source-control best practices… keeping humans in the critical path rather than delegating merge decisions to LLM-based tooling."*

**Spec Kit**: AI-proposes / human-approves — `spec-kit-sync` flags drift, AI suggests one of {Backfill, Align, Supersede, Human}, then `propose --interactive` → `apply` with safety checks. `/speckit.analyze` is **read-only**.
**Kiro**: spec↔code sync is **not automated**; specs are largely static.
**Governance** ([TrueFoundry](https://www.truefoundry.com/blog/spec-driven-development-ai-agents)): *"a spec edit is the highest-leverage change in the system… and in most adoptions also the least-ceremonied."* Version every spec edit, gate it behind evals, track which spec version produced behaviour.

> **Takeaway for rapla:** the industry conclusion — including OpenSpec's own maintainers — is that **the source-of-truth merge should be deterministic and/or human-gated, never a blind LLM edit.** This *vindicates* D2 (merge-by-judgment as a reviewed skill step, not a silent automated merge) and the verification-anchor approach. Where we do let an agent draft the merge, a human reviews before it lands — exactly the Spec Kit propose/approve and the OpenSpec parallel-merge-plan stance.

## 8. Implications for rapla (how the evidence maps to decisions)

1. **No tool adoption; plunder ideas** — vindicated by the ~10× ceremony cost (Scott Logic) and the "I'd rather review code" verbosity critique. ([ADR 0002](../decisions/0002-no-sdd-framework-as-tool.md))
2. **Target = spec-as-review-gate + durable contract, not generation** — the convergent mid-2026 position; generation is non-deterministic and the "you just wrote a program" paradox bites. ([PRD 088](../prd/088-spec-graph-formalization.md) D2)
3. **Three-artifact model** (change PRD / capability spec / MADR) — directly the intent-driven.dev fix for OpenSpec's archived-rationale gap. ([ADR 0001](../decisions/0001-use-madr-for-architecture-decisions.md))
4. **Capability-dependent format**: rule-enumerable capabilities (permissions, REST) suit atomic requirements + examples; narrative capabilities (locking, storage) keep prose + diagrams. No single format.
5. **Author specs for human AND AI the same way**: worked examples (input→output) **in** the spec, text-encoded diagrams (Mermaid/PlantUML source, never image-only to the agent), verification anchors (✅ PINNED), typed semantic blocks, specificity over volume.
6. **Merge is human-gated**: the promote/archive step may be agent-*drafted* but is human-*reviewed* before it updates a capability spec — never a silent LLM merge. Our spec-lint ([PRD 088](../prd/088-spec-graph-formalization.md) Phase 0) is the deterministic guardrail (the rapla analogue of `specs-apply.js` validation gates).

## Sources

Framework/landscape & debate: [Fowler/Böckeler — SDD: Kiro/spec-kit/Tessl](https://martinfowler.com/articles/exploring-gen-ai/sdd-3-tools.html) · [Fowler — SPDD](https://martinfowler.com/articles/structured-prompt-driven/) · [Sean Grove "The New Code"](https://my.infocaptor.com/hub/summaries/ai-engineer/the-new-code-sean-grove-openai-8rABwKRsec4) · [Karpathy — Software 3.0](https://www.latent.space/p/s3) · [Tessl Series A](https://tessl.io/blog/announcing-our-series-a-for-ai-native-software-development/) · [Ng — Waterfall in Markdown](https://medium.com/@iamalvisng/spec-driven-development-is-waterfall-in-markdown-e2921554a600) · [marmelab — Waterfall Strikes Back](https://marmelab.com/blog/2025/11/12/spec-driven-development-waterfall-strikes-back.html) · [Scott Logic — Spec Kit Paces](https://blog.scottlogic.com/2025/11/26/putting-spec-kit-through-its-paces-radical-idea-or-reinvented-waterfall.html) · [Gorman — "Precise" Specification](https://codemanship.wordpress.com/2026/01/15/yeah-about-your-precise-specification/) · [Brooker — NL programming](https://brooker.co.za/blog/2025/12/16/natural-language.html) · [Brooker — SDD isn't Waterfall](https://brooker.co.za/blog/2026/04/09/waterfall-vs-spec.html) · [HN — code IS spec?](https://news.ycombinator.com/item?id=47194035)

Decision-rationale formats: [MADR](https://adr.github.io/madr/) · [Nygard ADR](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions) · [Rust RFC template](https://github.com/rust-lang/rfcs/blob/master/0000-template.md) · [Rust RFC 2561 Future possibilities](https://rust-lang.github.io/rfcs/2561-future-possibilities.html) · [PEP 1](https://peps.python.org/pep-0001/) · [Oxide RFD 1](https://rfd.shared.oxide.computer/rfd/0001) · [intent-driven.dev OpenSpec+ADR](https://intent-driven.dev/blog/2026/04/29/spec-driven-development-with-adr/) · [log4brains](https://github.com/thomvaill/log4brains)

Format/readability: [OpenSpec #666](https://github.com/Fission-AI/OpenSpec/issues/666) · [#1077](https://github.com/Fission-AI/OpenSpec/issues/1077) · [#439](https://github.com/Fission-AI/OpenSpec/issues/439) · [#201](https://github.com/Fission-AI/OpenSpec/issues/201) · [Spec Kit #1784](https://github.com/github/spec-kit/discussions/1784) · [EARS (Mavin)](https://alistairmavin.com/ears/) · [Gojko Adzic — Specification by Example](https://gojko.net/books/specification-by-example/) · [Cucumber — better Gherkin](https://cucumber.io/docs/bdd/better-gherkin/) · [C4 model](https://c4model.com/) · [Addy Osmani — good spec for AI](https://addyosmani.com/blog/good-spec/)

AI comprehension: [TABLET (arXiv 2304.13188)](https://arxiv.org/pdf/2304.13188) · [IsoBench (arXiv 2404.01266)](https://arxiv.org/abs/2404.01266) · [MermaidSeqBench (arXiv 2511.14967)](https://arxiv.org/html/2511.14967v1) · [Anthropic — prompting best practices](https://platform.claude.com/docs/en/build-with-claude/prompt-engineering/claude-prompting-best-practices) · [Simon Willison — agentic patterns](https://simonwillison.net/guides/agentic-engineering-patterns/how-coding-agents-work/)

AI-driven merge: [DeepWiki archive](https://deepwiki.com/Fission-AI/OpenSpec/6.6-archive-command) · [DeepWiki /opsx:sync](https://deepwiki.com/Fission-AI/OpenSpec/3.8-opsx:sync-spec-synchronization) · [OpenSpec #863](https://github.com/Fission-AI/OpenSpec/issues/863) · [#913](https://github.com/Fission-AI/OpenSpec/issues/913) · [parallel-merge-plan.md](https://github.com/Fission-AI/OpenSpec/blob/main/openspec-parallel-merge-plan.md) · [spec-kit-sync](https://github.com/bgervin/spec-kit-sync) · [TrueFoundry — governing specs](https://www.truefoundry.com/blog/spec-driven-development-ai-agents)
