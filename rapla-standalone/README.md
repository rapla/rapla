# rapla-standalone — Windows 11 desktop trial installer

A [Tauri 2](https://tauri.app) shell wrapping the standalone Spring Boot
build of rapla, producing a signed MSI installer for Windows 11 x64.

**This is a trial install** — single-user, no auth, file-backed storage in
`%APPDATA%\rapla\`. Meant for evaluating rapla in 60 seconds before
deciding whether to deploy a real multi-user server. See
[`../docs/prd/054-standalone-windows-installer.md`](../docs/prd/054-standalone-windows-installer.md).

## Layout

```
rapla-standalone/
├── README.md                  this file
├── build.ps1                  Windows build pipeline (Maven → jlink → cargo tauri → signtool)
├── build.cmd                  one-line wrapper around build.ps1 (for double-click / cmd users)
├── build.sh                   WSL convenience wrapper (forwards to build.ps1 over interop)
├── src-tauri/                 Rust shell sources
│   ├── Cargo.toml             Tauri 2 deps
│   ├── tauri.conf.json        Bundle config — MSI, icon, signing
│   ├── build.rs               Tauri build hook
│   ├── src/main.rs            Child-process launcher + window lifecycle
│   └── icons/                 App icon (drop a real icon.ico here)
├── runtime/                   produced by build.ps1 (jlinked JRE + staged server) — gitignored
└── .gitignore
```

`rapla-standalone/` lives **outside the Maven reactor** — it's a Rust /
Tauri project with its own toolchain (`rustup` + `cargo-tauri`). Same model
as `rapla-angular/` (PRD 026).

## Prerequisites (Windows 11 build host, one-time setup)

```powershell
# JDK 21+ (jlink + jpackage)
winget install --id EclipseAdoptium.Temurin.21.JDK

# Apache Maven
winget install --id Apache.Maven

# Rust toolchain
winget install --id Rustlang.Rustup
rustup default stable

# Visual Studio 2022 Build Tools (Rust needs the MSVC linker)
# Pick "Desktop development with C++" workload
winget install --id Microsoft.VisualStudio.2022.BuildTools

# Tauri CLI
cargo install tauri-cli --version "^2.0"
```

Sanity: `cargo tauri info` prints all detected tooling and flags any gaps.

## Build

```powershell
cd rapla-standalone
.\build.ps1                # full pipeline (Maven + jlink + Tauri + MSI), unsigned
.\build.ps1 -Sign          # signed via SSL.com YubiKey
.\build.ps1 -SkipMaven     # skip Maven (faster iteration if the JAR is already built)
.\build.ps1 -SkipJlink     # skip jlink (faster iteration during Tauri/Rust dev)
```

Output: `src-tauri\target\release\bundle\msi\Rapla_2.1.0_x64_en-US.msi`
(~110 MB).

Don't have PowerShell-fluent users? Same thing via `.cmd`:

```cmd
build.cmd
build.cmd -Sign
```

### From WSL

```bash
./build.sh
./build.sh -Sign
./build.sh -SkipMaven
```

`build.sh` calls Windows-native PowerShell via WSL interop, with automatic
WSL → Windows path translation.

**Important — filesystem layout for performance:**

| Setup | Speed | Notes |
|---|---|---|
| Repo on Windows (`C:\git\rapla\`), WSL accesses via `/mnt/c/...` | Fast both ways | Recommended for routine work |
| Repo on WSL (`/home/.../git/rapla/`), Windows accesses via `\\wsl$\...` | Slow for Tauri (~5-10× slower cargo builds) | OK for one-off builds, painful for iteration |

If you'll do more than one build, clone the repo on the Windows side
(`C:\git\rapla\`) and let WSL reach it through `/mnt/c/git/rapla/`. WSL
still does fast Maven builds (9P read of NTFS is fast), Windows-side
`cargo tauri build` runs at native NTFS speed.

## Architecture

```
┌─ rapla.exe (Tauri main process, Rust) ────────────────────────┐
│                                                                │
│  ├─ pick random local port (portpicker)                        │
│  ├─ spawn rapla-server.bat (jlinked JRE + standalone fat JAR)  │
│  │   with --spring.profiles.active=standalone + server.port=N  │
│  ├─ TCP-poll 127.0.0.1:N for 30 s (Tomcat-bind readiness)      │
│  ├─ open WebView2 window → http://127.0.0.1:N/app/             │
│  │                                                             │
│  └─ on window close: SIGTERM child, wait 5 s, taskkill         │
│                                                                │
└────────────────────────────────────────────────────────────────┘

Single-instance lock (tauri-plugin-single-instance): re-launching focuses
the existing window instead of starting a second server.

Data file: %APPDATA%\rapla\rapla-data.xml  (XML FileOperator)
Logs:      %LOCALAPPDATA%\rapla\logs\rapla.log
```

## What gets bundled

| Component | Size (approx) | Source |
|---|---|---|
| Tauri Rust shell (rapla.exe) | ~6 MB | `cargo tauri build` |
| jlinked JRE (Windows x64) | ~50 MB | `build.ps1` jlink step |
| Standalone fat JAR | ~45 MB | `mvn -Pstandalone` |
| WiX MSI overhead | ~3 MB | wix bundling |
| **Total MSI installer** | **~110 MB** | |

WebView2 itself is **not bundled** — ships pre-installed on every Windows 11.

## Signing

v1 default: unsigned. SmartScreen warns on install ("Windows protected
your PC" → "More info" → "Run anyway"). Fine for testing.

Production: `.\build.ps1 -Sign` invokes `signtool` against the SSL.com
`erdkante GmbH` cert in the Windows Certificate Store. Requires:

1. YubiKey plugged in (PIV cert auto-appears in `certmgr.msc` → Personal)
2. `$env:RAPLA_SIGN_THUMBPRINT` set to the cert thumbprint (or pass
   `-CertThumbprint <hex>` to `build.ps1`)

See [`../docs/signing.md`](../docs/signing.md) for the YubiKey setup
shared with the JNLP signing path.

## v1 limitations

- **Windows 11 x64 only.** No macOS, no Linux, no Windows 10.
- **No auto-update.** v1 ships as a one-shot MSI; users re-download
  for updates. Tauri's built-in updater + JSON manifest comes in Phase 2.
- **Self-signed by default.** SmartScreen warns. Use `-Sign` for
  production signing via the YubiKey.
- **No real auth.** `passwordCheckDisabled=true` — any password works.
  The OAuth code path runs normally (SPA login screen + Spring AS form
  login), the server-side password check is just no-oped.

See [`../docs/prd/054-standalone-windows-installer.md`](../docs/prd/054-standalone-windows-installer.md)
for the full design + scope + deferred items.
