# `.claude/` — Claude Code only

Claude Code reads project config from `.claude/` and nothing else. Tracked here: `commands/`
(slash commands) and `hooks.md` (the guard hooks every rapla developer should copy into their
own `~/.claude/settings.json`). `settings.json` and `settings.local.json` are per user and gitignored.

The skills are **not** here. They live in `.agents/skills/` (the cross-engine
Agent Skills standard, read natively by opencode, Codex, Copilot, Gemini CLI).
Claude Code does not read `.agents/`, so pick one of two ways to see them:

1. **Plugin (default, portable, no symlink):** `.claude-plugin/plugin.json` at the repo
   root points at `.agents/skills`. Start Claude Code with

       claude --plugin-dir .

   Skills then appear as `rapla:<name>`. A shell wrapper can add the flag
   automatically when `.claude-plugin/plugin.json` exists in the current directory.

2. **Symlink (Linux/macOS, or Windows with `git config core.symlinks true`):**

       ln -s ../.agents/skills .claude/skills

   Skills then appear under their plain names. Don't combine both — you'd get
   every skill twice.

Instructions for all engines are in `AGENTS.md`; `CLAUDE.md` only imports it.
