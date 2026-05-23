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

## Windows-side `.wslconfig` — `C:\Users\<you>\.wslconfig`

Lives outside WSL but governs how it boots. Read at every `wsl --shutdown` /
restart cycle. Current contents:

```ini
[wsl2]
memory=20GB
processors=12
swap=8GB
networkingMode=mirrored

[experimental]
sparseVhd=true
autoMemoryReclaim=gradual
```

`networkingMode=mirrored` is the load-bearing line for the cross-OS dev flow:
**Windows-side processes reach WSL-side servers via `localhost`** (and vice
versa). Without it, WSL2 runs in NAT mode and Windows → WSL works only via the
`localhostForwarding=true` default, which is flaky under firewalls/VPNs and
breaks the inverse direction entirely. With mirrored mode the two sides share
the network stack — no port forwarding, no IP drift between reboots.

Why we care: the Swing client is sometimes launched from a Windows-side IntelliJ
run-config using a **Windows JDK** against a rapla server running inside WSL on
`:8051`. The default OAuth-discovery probe (`GET /api/auth/oauth/config`) and
every REST call use `localhost:8051` — without mirrored mode those connects can
fail and the client aborts at startup (see the connect-error path in
`RaplaClientServiceImpl` → `abortWithConnectError`). Mirrored mode makes the
zero-config "launch on Windows, server in WSL" path Just Work.

Apply: edit the file, then from Windows PowerShell `wsl --shutdown` and start
a fresh WSL terminal. Requires Windows 11 22H2+. Known incompatibilities: some
VPNs (Cisco AnyConnect, GlobalProtect) and certain Docker Desktop configs — if
networking breaks after the restart, remove the line and `wsl --shutdown` again.

### Running the Swing client from Windows IntelliJ against a WSL server

Useful when you want the Swing GUI to run on the Windows JDK (faster Swing
rendering on a real Windows display, native font hinting, no WSLg quirks)
while the Spring Boot server, the data, and the build still live in WSL.

Open the project from `\\wsl.localhost\Ubuntu\home\chris\git\rapla\`. Then:

**Split the JDKs** — build in WSL, run on Windows.
- `File → Project Structure → SDKs` → add a Windows JDK 21 (e.g.
  `C:\Program Files\Java\jdk-21`). Set as **Project SDK**.
- `Settings → Build Tools → Maven → Runner` → **JRE: WSL JDK** (e.g.
  `\\wsl.localhost\Ubuntu\home\chris\.sdkman\candidates\java\current`).
  Every `mvn` goal now forks into WSL's JVM.

**Run config** (`Run → Edit Configurations → + Application`):
- Main class: `org.rapla.client.spring.SpringRaplaClient`
- Module: `rapla-client`
- Program arguments: `admin` (auto-login as admin, dev DB default empty password)
- **JRE: Windows JDK 21** (the override that makes the launch use `java.exe`)
- Working directory: `\\wsl.localhost\Ubuntu\home\chris\git\rapla` so client-side
  files (logs, prefs) land on the WSL side
- **Modify options → "Do not build before run"** to disable IntelliJ's
  Windows-side incremental compile
- **Modify options → Add before-launch task → Run Maven Goal:**
  `-pl rapla-client -am compile` — fires in WSL via the Maven Runner JRE set
  above, updates `target/classes` in WSL

Net flow on Run: Maven-in-WSL compiles → `target/classes` updates in WSL →
Windows `java.exe` launches, reading the freshly-built classes over
`\\wsl.localhost\...`. UNC classloading adds a few seconds to startup but is
otherwise transparent.

**Storage stays in WSL.** The Swing client only talks to the server over
`localhost:8051` — it never touches `data/` directly. The server (still
running in WSL per §8) owns the storage; moving only the client JVM to
Windows leaves the data untouched.

**Debugging.** Add `-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005`
to the run config's VM options, then create a "Remote JVM Debug" config
pointing at `localhost:5005`. With mirrored mode the Windows JVM's debug port
is reachable from anywhere on the box.

### Overriding the Swing client's server URL

The Swing client picks its server via the `rapla.download.url` system property
(default `http://localhost:8051/`, read in `ClientConfig.startupEnvironment`).
On Windows JDK runs against a WSL server, **with mirrored mode the default
works as-is** — nothing to set.

Fallback for NAT mode (or if mirrored isn't taking effect): pass the WSL IP
explicitly via IntelliJ Run config → **VM options**:

```
-Drapla.download.url=http://<wsl-ip>:8051/
```

Find the current WSL IP with `hostname -I` (first token) inside WSL. **The IP
drifts on every WSL reboot in NAT mode** — that's the whole reason mirrored
mode is the durable fix. As an alternative to the per-config VM option, set
`JAVA_TOOL_OPTIONS=-Drapla.download.url=...` in Windows user env vars (every
JVM on the box picks it up — global, so use with care).

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
| `google-chrome-stable` | `148.0.7778.167-1` | browser for Playwright MCP — Google `.deb`, not Ubuntu universe (see `docs/development.md` § Playwright MCP) |

## SDKMAN — `~/.sdkman`

JVM toolchain. Loaded by the block at the **end** of `.bashrc` and `.zshrc`.

| Candidate | Installed | `current` |
|---|---|---|
| java | `21.0.11-sem`, `26.0.1-sem` | → `21.0.11-sem` (Semeru / Temurin build) |
| maven | `3.9.15` | → `3.9.15` |
| gradle | installed | — |

rapla targets Java 17 source, runs on Java 21. `26.0.1-sem` is present but not
selected — don't `sdk default java` it without checking the build still passes.

## nvm + Node — `~/.nvm`

| Item | Value |
|---|---|
| nvm | installed, sourced from `.bashrc` (`NVM_DIR=$HOME/.nvm`) |
| Node versions | `v24.15.0` (the only one installed) |
| nvm `default` alias | → `v24.15.0` |
| Angular / `.nvmrc` | `rapla-angular/.nvmrc` is `lts/*`, which resolves to `lts/krypton` = `v24.15.0`. Angular 21 needs Node ≥ 20.19; v24.15.0 satisfies it. |
| npm globals | Managed by nvm **per node version** — no `NPM_CONFIG_PREFIX` / `~/.npmrc` prefix override (that form conflicts with nvm and makes `nvm use` refuse to switch). `~/.nvm/default-packages` lists the three packages below so a fresh `nvm install` re-installs them automatically. |

### Global npm packages — `~/.nvm/versions/node/<version>/lib/node_modules`

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

### Fixing copy/paste screenshots when `status` says "not running"

The `wsl-screenshot-cli` daemon (v1.3.1) can land in an inconsistent state:
`wsl-screenshot-cli status` reports **"not running"** while `wsl-screenshot-cli
start` correctly reports the live PID. This happens when a previous daemon
(e.g. the one auto-started by `.bashrc` on the prior shell) died uncleanly and
left stale state — a freshly-started daemon won't reconcile with it, so
`status` keeps reading the dead PID.

Screenshots may still be captured in this state, but the broken `status` makes
it look dead. Fix with a clean stop + restart (NOT just another `start`):

```bash
wsl-screenshot-cli update              # clean-stops the daemon; no-op if already latest
wsl-screenshot-cli start --daemon --quiet
wsl-screenshot-cli status              # should now show: Status: running + a PID
```

`wsl-screenshot-cli stop` followed by `start --daemon --quiet` works too — the
point is the clean `stop` clears the stale state. PNGs land in
`/tmp/.wsl-screenshot-cli/` (tmpfs — cleared on reboot). Paste a screenshot
into Claude Code with Win+Shift+S → snip → **Ctrl+Shift+V** in the prompt.

### Daemon ownership — `.bashrc`, not Claude Code hooks

The screenshot daemon's lifecycle is owned by **`.bashrc`** (the
`wsl-screenshot-cli start --daemon` line auto-starts it once per interactive
shell). `~/.claude/settings.json` also has a **`SessionStart`** hook that runs
the same `start --daemon` — a harmless no-op re-assertion if the daemon is
already up.

There is deliberately **no `SessionEnd` stop hook**. A `wsl-screenshot-cli stop`
on session end was removed because it tears the daemon down out from under any
*other* Claude Code session still running, and an unclean session exit skips it
anyway — both leave the stale state described above. Letting `.bashrc` own the
daemon and never stopping it on session end avoids that. If you re-add a
`SessionEnd` hook, expect the stale-`status` bug to come back.

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
| `playwright` | `npx @playwright/mcp@latest --browser chrome` | browser-driven SPA debug |
| `claude.ai Google Drive` | remote HTTP | needs re-auth on use |

Playwright uses **system Google Chrome** (`google-chrome-stable`, from Google's
`.deb`) — not bundled Chromium. `npx playwright install` has no build for
Ubuntu 26.04, so there is no `~/.cache/ms-playwright`; the MCP server is
registered with `--browser chrome` to use the system binary. Full install +
the WSLg headed-window troubleshooting: `docs/development.md` § "Playwright
MCP — install".

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
export BROWSER=wslview                 # opens links in the Windows browser
export PATH="$HOME/.local/bin:$PATH"
wsl-screenshot-cli start --daemon      # auto-start screenshot daemon
source <(ng completion script)         # Angular CLI autocompletion
```

> **Note:** no `NPM_CONFIG_PREFIX` / `~/.npm-global` PATH line — those were
> removed because they conflict with nvm (nvm manages its own per-version
> prefix and refuses `nvm use` while a prefix override is set).

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
- `~/.npm-global/` — orphaned. Was the old `NPM_CONFIG_PREFIX` target; the
  global packages have been reinstalled under nvm's per-version dir and the
  `.bashrc` prefix lines removed. Safe to `rm -rf`.

## Reproducing this box

1. On the Windows side, create `C:\Users\<you>\.wslconfig` with the contents
   shown above (in particular `networkingMode=mirrored`); `wsl --shutdown`
   after every edit.
2. Install the apt packages above.
3. Install SDKMAN → `java 21.0.11-sem`, `maven`.
4. Install nvm → Node `v24.15.0`. Do **not** set `NPM_CONFIG_PREFIX` or an
   `~/.npmrc` prefix — it conflicts with nvm.
5. `npm i -g` the three global packages, and list them in `~/.nvm/default-packages`
   so future `nvm install`s re-install them automatically.
6. Drop `jdwp-mcp` + `wsl-screenshot-cli` into `~/.local/bin`.
7. Append the `.bashrc` blocks shown above.
8. `gh auth login`; set `~/.gitconfig`.
9. Register the `jdwp` and `playwright` MCP servers with `claude mcp add`.

Full commands: [`development.md`](development.md).
