---
name: skill-authoring
description: Use when writing a new .agents/skills/<name>/SKILL.md or substantially
  editing one — especially a discipline/guardrail skill that must survive an agent
  rationalizing its way around it. Pressure-test wording with a fresh subagent first.
---

# Skill Authoring

Writing a skill is TDD for process docs: a skill that isn't pressure-tested is untested
code. Sibling of `agents-cleanup` (which trims AGENTS.md, not the skills).

## RED — does a fresh agent fail without the skill?
Dispatch an Explore/general-purpose subagent (Agent tool) with a realistic rapla
scenario, WITHOUT the new skill in context. Record verbatim what it does wrong and the
rationalizations it gives ("it's just a 1-liner, skip the test"). That's your test case.

## GREEN — write the minimal skill that closes those exact rationalizations
- Frontmatter `description:` starts "Use when…" and lists ONLY triggers + searchable
  keywords (error strings, symptoms, tool names). Never put the workflow in the
  description — agents follow it instead of reading the body.
- Match form to failure: guardrail under pressure → prohibition + red-flags list;
  wrong-output-shape → positive recipe/template; missing element → REQUIRED-field
  template. Prohibitions backfire on shape problems.
- Re-run the same subagent scenario WITH the skill; confirm it now complies.

## REFACTOR — close new loopholes
New rationalization appears under pressure → add an explicit counter, re-test until it
holds. Add "violating the letter is violating the spirit" to discipline skills.

## rapla fit
- One fact / one concern per skill; keep AGENTS.md a pointer, the detail in the skill
  (the §pattern already in use).
- Cross-link sibling skills by name.
- Don't duplicate an AGENTS.md rule — reference it.
