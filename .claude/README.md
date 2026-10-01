# `.claude/` — Claude Code only

Claude Code reads project config from `.claude/` and nothing else. Tracked here: `commands/`
(slash commands) and `hooks.md` (the guard hooks every rapla developer should copy into their
own `~/.claude/settings.json`). `settings.json` and `settings.local.json` are per user and gitignored.

The project skills live in `skills/` (the cross-engine Agent Skills `SKILL.md` format).
Claude Code, opencode and the GitHub Copilot CLI all read `.claude/skills/` natively — no
plugin, no `--plugin-dir`, no symlink. Why they moved here from `.agents/skills/`:
[`docs/development.md` § Agent skills](../docs/development.md#agent-skills--how-engines-find-them).

Instructions for all engines are in `AGENTS.md`; `CLAUDE.md` only imports it.
