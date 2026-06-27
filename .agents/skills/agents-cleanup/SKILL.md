---
name: agents-cleanup
description: Use when the user asks to clean up, trim, slim, or reorganize AGENTS.md. AGENTS.md is always-on context loaded every session, so it has to stay short and rule-dense — this skill carries the methodology for keeping it that way — the four lenses (move-to-skills, dedup, de-verbose, move-to-docs), the "is it a rule or is it detail?" decision test, and the relocate-don't-delete invariant. Takes free-text args naming what to focus on this pass (e.g. "trim §8 and dedup the build rules"); with no args, does a full four-lens scan and proposes findings before touching anything. Skip for editing the *content* of a single rule — that's just an Edit; this is for structural cleanup.
---

# Cleaning up AGENTS.md

AGENTS.md (included into context via `CLAUDE.md`'s `@AGENTS.md`) is **always-on**: every
agent in every session pays for it in context budget. Its job is to fire **behavioural
rules** — the things an agent must do or never do — as densely as possible. Anything that
isn't a rule-that-fires-every-session is bloat, and bloat in an always-on file is the most
expensive kind.

This skill is the repeatable methodology for trimming it. The repo already follows the
pattern (18 skills, a `docs/` tree, section cross-refs); this just makes the cleanup pass
consistent.

## Args

Read `$ARGUMENTS` as a free-text instruction naming the focus for **this** pass — e.g.
`trim §8 and dedup the build rules`, `move the worked-examples out`, `look for anything
that belongs in docs/`. Honour it as the scope.

**With no args**, run the full four-lens scan below across the whole file and present a
findings list before changing anything.

## The one invariant — relocate, never lose

**Cleanup moves detail; it never drops a rule.** Before removing any sentence, classify it
(see the decision test). If it's a behavioural rule, it stays — terser, maybe relocated,
but a future session must still be told to do/not-do the thing.

**Every chunk you move out leaves a pointer with a 1–2 line summary** — never a bare
`see docs/Y.md`. The pointer is what the always-on reader gets *without* loading the target,
so it has to carry the gist: what the moved content is and when to go get it. Shape:

> *good* — "Build the deployable via `mvn clean package -DskipTests`; signed JNLP webclient
> bundling has six known pitfalls. Full recipe + pitfalls: `test-deployment` skill."
>
> *bad* — "See the `test-deployment` skill."

A reader skimming AGENTS.md should understand the rule and know whether they need the
target, from the pointer alone. The general-and-load-bearing the content is, the more the
summary matters — for niche detail a one-liner is fine; for anything an agent might need
every session, give it the two lines.

When in doubt whether something is load-bearing, keep it and flag it in your findings rather
than cutting silently. Per AGENTS.md §6, never revert user-visible files to a committed
state without asking — that applies to AGENTS.md itself.

## Target size

AGENTS.md is always-on: its token cost is paid on every message of every session, whether
or not it's relevant to the task. Industry guidance puts the sweet spot at **~150–250 lines**
— past that, the model applies the guidance less consistently and the per-session cost climbs
(≈1.5–2 k tokens at 200 lines, more beyond). Treat a number well above that as a signal to
run this skill, not a hard limit — a dense rule file that's all load-bearing is fine; a long
one padded with recipes, examples, and defaults is the target.

## The decision test — for every candidate chunk

Ask, in order:

1. **Is it a behavioural rule that must fire every session?** (a "always X", "never Y",
   "use Z not W") → **stays inline**, trimmed to the imperative + the one-line why. This is
   the only content that earns always-on space.
2. **Is it stated somewhere else in the file already?** → **dedup** (lens 2): keep one
   canonical statement, replace the others with a `see §N` cross-ref.
3. **Is it how-to / reach-for-when-doing-a-task detail** (commands, recipes, step lists,
   worked debugging procedures)? → **move to a skill** (lens 1).
4. **Is it durable reference / background** (topology, design rationale, env setup, a long
   worked example that motivated a rule)? → **move to `docs/`** (lens 4), or trim to a
   pointer if a doc already covers it.
5. **Is it none of the above** (filler, restated overview, dead cross-ref)? → cut.

## The four lenses

### Lens 1 — move to skills
Verbose how-to that's only needed while performing a specific task does not belong in
always-on context. Target: command recipes, multi-step procedures, debugging playbooks,
worked configuration. Move the body into the matching `.agents/skills/<name>/SKILL.md`
(create one if none fits — frontmatter format below), and leave inline only the
always-on rule + `load the <name> skill`.

The split to preserve: **the rule stays, the recipe goes.** "Always `mvn clean` before
`mvn package`" is a rule (inline); the 6-line clean-package invocation with flags is a
recipe (skill).

### Lens 2 — redundant information
The same hard rule often gets restated in several sections (the classic in this repo:
"never `mvn install`, never run from `~/.m2`" appears in §5, §8, §9). Pick the **one**
canonical home (usually the lowest-numbered / most general section), state it fully there,
and replace every other copy with `see §N`. Watch for near-duplicates that drift —
two copies of a rule that no longer say the same thing is worse than one.

### Lens 3 — too verbose
Long worked-examples ("Worked example (motivated this rule, DATE): …"), repeated rationale,
and belt-and-suspenders restatement. The rule needs the *what* and a one-clause *why*; the
full incident narrative is reference material. Trim to the rule + a pointer to where the
example now lives (a skill or a doc). Don't delete the example — it's institutional memory —
relocate it.

### Lens 4 — move to docs/
Durable, non-behavioural reference belongs in `docs/`, not always-on context. The tree
already has homes: `docs/development.md` (dev workflow, env, recipes), `docs/architecture/*`
(topology, design), `docs/deployment.md`, `docs/signing.md`, `docs/authentication.md`, the
PRDs. Move background/topology/rationale into the right doc (or a new one), and cross-ref
from AGENTS.md. If the content is *partly* a rule, split it: rule inline, background to docs.

### Lens 5 — default-behaviour rules (cut, don't relocate)
A rule that just restates what the agent does by default earns no space — it costs tokens
and changes nothing. Watch for: verbose role/altitude preambles, over-specified obvious
style ("don't use tabs", "write clear names"), and process the harness already enforces.
**This is the one lens where the action is delete, not relocate** — there's nowhere to move
"behave normally" to. When unsure whether a rule is load-bearing or default, surface it in
the findings as a *delete-and-test candidate* rather than cutting it unilaterally: the user
removes it, and if behaviour degrades it goes back. (rapla's rules are mostly hard-won and
non-default — expect this lens to fire rarely here; it's in the kit for completeness.)

## Skill frontmatter (when lens 1 creates a new skill)

```
---
name: <kebab-case>            # matches the directory name
description: Use when … — <what it covers>. Skip when … .
---
```

`description` is what the agent reads to decide relevance — and it's the **only** thing the
agent sees before deciding whether to load the skill. The skill body is invisible at
selection time, so discovery lives entirely in the description. Write it to match:

- **Lead with the trigger and the literal phrases a user would type.** Skill auto-selection
  is fuzzy matching of the user's request against this text — a description containing
  "start, stop, restart the server … 'restart rapla', 'is the server up?'" fires on those
  requests; a vague "manage server state" matches weakly and the skill silently doesn't load.
  Spell out the verbs and the natural-chat phrasings.
- **Name the concrete things it covers** (commands, file paths, the recipe names) so a
  keyword in the user's request can hit.
- **Add a "Skip when …"** so it isn't loaded needlessly.
- **After moving content into a skill, update its description to mention what you moved** —
  otherwise the relocated content is undiscoverable and the cleanup has effectively hidden it.
- **Never write a bare `: ` (colon-space) inside the description value.** It's a YAML
  mapping-key delimiter — strict parsers reject the file, and a lenient loader may silently
  fall back to showing the body's `# H1` heading as the description (so the skill stops
  matching its triggers). Use ` — ` or `;` instead. Verify after editing:
  `python3 -c "import yaml,sys; yaml.safe_load(open(sys.argv[1]).read().split('---',2)[1])" <SKILL.md>`.

Watch for the trap this skill itself hit: an extraction that says "the X basics stay in
AGENTS.md, this skill is only for the *advanced* case" can **exclude the common trigger word
from the description** — so the plain request never loads the skill. If the skill is the real
home for a verb (start/stop/restart), put that verb in the description even when a minimal
inline version also exists in AGENTS.md.

## Process

1. **Scope** — parse `$ARGUMENTS`; if empty, full four-lens scan.
2. **Read** the current AGENTS.md, list `.agents/skills/`, and skim the `docs/` tree so you
   know what receiving homes already exist (don't create a duplicate skill/doc).
3. **Classify** each candidate with the decision test. Build a findings list: for each item —
   the location (§N), the lens, the proposed action (inline-trim / dedup→§N / →skill / →doc),
   and the destination.
4. **Present the findings** to the user before editing (especially anything that removes a
   rule rather than relocating it). For a scoped pass the user already named (`$ARGUMENTS`),
   apply directly but still report what moved where.
5. **Apply** — move bodies into skills/docs, leave one-line pointers, dedup to cross-refs,
   trim verbosity. Keep section numbering stable; if you must renumber, grep AGENTS.md for
   `§N` cross-refs and fix them.
6. **Verify** — re-read the trimmed AGENTS.md end to end: (a) every relocated chunk has a
   findable pointer, (b) no behavioural rule was lost, (c) cross-refs resolve, (d) any skill
   you wrote into has a description that mentions the new content. Report the line-count
   before/after and the list of moves.

## What NOT to touch

- The **Project Overview** module table and Jackson-3 / topology notes — that's the orientation
  every session needs; trim wording, don't relocate.
- Numbered-rule **headlines** — even when the body moves to a skill, the `### N. <rule>`
  heading + one-line statement stays so the rule is visible in always-on context.
- Anything the user's `$ARGUMENTS` put out of scope.

## Further reading

The four-lens model is rapla's framing of widely-shared guidance: keep instruction files
small and point elsewhere (progressive disclosure into skills/docs), lead with commands, and
cut rules the model already follows. Sources:
[agents.md](https://agents.md/) ·
[A Complete Guide to AGENTS.md](https://www.aihero.dev/a-complete-guide-to-agents-md) ·
[How to write good AGENTS.md files (Augment)](https://www.augmentcode.com/blog/how-to-write-good-agents-dot-md-files) ·
[Context engineering for IDEs (LogRocket)](https://blog.logrocket.com/context-engineering-for-ides-agents-md-agent-skills/) ·
[CLAUDE.md token-budget optimization](https://thepromptshelf.dev/blog/claude-md-token-budget-optimization/).
