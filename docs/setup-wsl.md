# setup-wsl.md — the WSL home-folder environment

This is an inventory of everything in the WSL2 home folder (`/home/chris`) that
is **not** under version control but is required to build, run, test and debug
rapla. The home folder is not a git repo — so "uncommitted changes" means the
entire hand-built dev environment. Use this to reproduce the box, or to audit
what a session depends on.

For the step-by-step bootstrap with install commands see
[`development.md`](development.md) §"Bootstrap a fresh WSL2 Ubuntu environment".
This file is the **state snapshot**; that one is the **recipe**.

Snapshot date: 2026-05-16.

## Platform

| Item | Value |
|---|---|
| Distro | Ubuntu 24.04 LTS (`CanonicalGroupLimited.Ubuntu` Store package) |
| Kernel | `6.6.87.2-microsoft-standard-WSL2` |
| Shell | bash (login → `.profile` → `.bashrc`); `.zshrc` also present |
| `/etc/wsl.conf` | `[boot]\nsystemd=true` — systemd enabled (needed for some services) |

## apt packages (system, `sudo apt install`)

Not in the home folder, but the home-folder tools assume these exist:

| Package | Version | Used for |
|---|---|---|
| `curl`, `zip`, `unzip`, `jq`, `wget` | — | install scripts (SDKMAN/nvm), JSON probing |
| `openjdk-21-jre-headless` | `21.0.10+7-1~24.04` | system JRE fallback (SDKMAN Java is primary) |
| `icedtea-netx` | `1.8.8-2ubuntu1` | JNLP launcher for `test-jnlp-launch` skill |
| `wslu` | `3.2.3-0ubuntu3` | `wslview` — opens URLs/files in Windows |
| `xvfb` | `2:21.1.12` | headless display for Swing/Playwright in CI-style runs |
| `gh` | `2.92.0` | GitHub CLI (official keyring repo, not Ubuntu universe) |

## SDKMAN — `~/.sdkman`

JVM toolchain. Loaded by the block at the **end** of `.bashrc` and `.zshrc`.

| Candidate | Installed | `current` |
|---|---|---|
| java | `21.0.11-sem`, `26.0.1-sem` | → `21.0.11-sem` (Semeru / Temurin build) |
| maven | `3.9.15` | → `3.9.15` |
| gradle | installed | — |

rapla targets Java 17 source, runs on Java 21. `26.0.1-sem` is present but not
selected — don't `sdk default java` it without checking the build still passes.

## nvm + Node — `~/.nvm`, `~/.npm-global`

| Item | Value |
|---|---|
| nvm | installed, sourced from `.bashrc` (`NVM_DIR=$HOME/.nvm`) |
| Node versions | `v24.15.0`, `v25.9.0` |
| nvm `default` alias | `node` (resolves to newest = v25) |
| Angular CLI pin | Angular 21 needs Node ≥ 20.19; the repo expects **v24.15.0** — `nvm use v24.15.0` before frontend work (v25 is odd-numbered, no LTS) |
| npm prefix | `~/.npm-global` (set in `~/.npmrc`) — avoids `sudo npm i -g` |

### Global npm packages — `~/.npm-global/lib/node_modules`

| Package | bin | Purpose |
|---|---|---|
| `@anthropic-ai/claude-code` | `claude` | Claude Code CLI |
| `@angular/cli` | `ng` | Angular SPA build/serve (`rapla-angular/`) |
| `@openapitools/openapi-generator-cli` | `openapi-generator-cli` | OpenAPI client codegen |

## `~/.local/bin` — hand-placed binaries

On PATH via the `.bashrc` block. Pre-built single-file binaries:

| Binary | Purpose |
|---|---|
| `jdwp-mcp` | JDWP debugger MCP server (see `java-debugger` skill, `development.md` §8) |
| `wsl-screenshot-cli` | pastes Windows clipboard screenshots into Claude Code; daemon auto-started from `.bashrc` |

## `~/.opencode`

The `opencode` agent CLI — 146 MB binary at `~/.opencode/bin/opencode`, plus a
small `node_modules`. On PATH via `.bashrc`. Independent of Claude Code.

## Claude Code — `~/.claude`

| Path | Contents |
|---|---|
| `settings.json` | global Claude Code settings |
| `.credentials.json` | auth token (secret — do not commit/share) |
| `projects/` | per-project state incl. this repo's auto-memory under `memory/` |
| `plugins/`, `tasks/`, `sessions/`, `file-history/` | session/agent runtime state |

### Registered MCP servers (`claude mcp list`)

| Name | Command | Scope |
|---|---|---|
| `jdwp` | `jdwp-mcp` | live-JVM debugger (project) |
| `playwright` | `npx @playwright/mcp@latest --browser chromium` | browser-driven SPA debug |
| `claude.ai Google Drive` | remote HTTP | needs re-auth on use |

Playwright browsers are cached in `~/.cache/ms-playwright` (chromium 1223/1224,
firefox, webkit, ffmpeg) — installed by `npx playwright install`.

## Git & GitHub config

| File | Key contents |
|---|---|
| `~/.gitconfig` | `user.name`/`user.email`; `core.autocrlf = true`; GitHub credential helper → `gh auth git-credential` |
| `~/.config/git/ignore` | global gitignore: `**/.claude/settings.local.json` |
| `~/.config/gh/` | `gh` authentication (logged in) |

## Shell config — what's been added to `~/.bashrc`

The stock Ubuntu `.bashrc` plus these appended blocks (order matters — SDKMAN must be last):

```bash
export NVM_DIR="$HOME/.nvm"            # nvm loader
... SDKMAN init block (MUST be last) ...
export PATH=/home/chris/.opencode/bin:$PATH
export PATH=~/.npm-global/bin:$PATH
export BROWSER=wslview                 # opens links in the Windows browser
export PATH="$HOME/.local/bin:$PATH"
wsl-screenshot-cli start --daemon      # auto-start screenshot daemon
source <(ng completion script)         # Angular CLI autocompletion
```

`.zshrc` carries only the SDKMAN block. `.profile` is stock (sources `.bashrc`,
adds `~/bin` and `~/.local/bin` to PATH).

## JNLP / OpenWebStart client config — `~/.config/icedtea-web`, `~/.java`

Created by running the JNLP webclient (`test-jnlp-launch` skill):

- `~/.config/icedtea-web/security/trusted.*` — accepted self-signed test certs
- `~/.config/icedtea-web/deployment.properties` — netx deployment config
- `~/.java/.userPrefs/com/install4j/` + `~/.java/fonts/` — OpenWebStart artifacts

Safe to delete to force a clean JNLP trust prompt on next launch.

## Maven local repository — `~/.m2`

`~/.m2/repository` only — **no `settings.xml`** (default central + the reactor's
in-tree classpath cover everything). Per AGENTS.md §5, never `mvn install` rapla
artifacts here; they would shadow in-reactor `target/classes`.

## Known cruft

- `~/F:\entwicklung\maven\repository/` — a directory literally named with a
  Windows path, created by accident when a Windows-style `-Dmaven.repo.local`
  value was passed on Linux. Not used by anything. Safe to `rm -rf`.
- `~/.rapla/` — empty; rapla writes user prefs here at runtime.

## Reproducing this box

1. Install the apt packages above.
2. Install SDKMAN → `java 21.0.11-sem`, `maven`.
3. Install nvm → Node `v24.15.0` (+ `v25.9.0` optional); set npm prefix `~/.npm-global`.
4. `npm i -g` the three global packages.
5. Drop `jdwp-mcp` + `wsl-screenshot-cli` into `~/.local/bin`.
6. Append the `.bashrc` blocks shown above.
7. `gh auth login`; set `~/.gitconfig`.
8. Register the `jdwp` and `playwright` MCP servers with `claude mcp add`.

Full commands: [`development.md`](development.md).
