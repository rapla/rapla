# PRD 054: Standalone Windows 11 desktop installer

**Status:** in-progress — Phase 1 landed 2026-05-25 (Maven profile +
application-standalone.yml + StandaloneBootTest + Tauri scaffold under
`rapla-standalone/`). Phases 2-5 (jlink + Tauri build + signing + MSI)
run on the maintainer's Windows machine per `rapla-standalone/README.md`.
**Date:** 2026-05-25

## Goal

Ship a single-MSI Windows 11 installer that lets someone **try rapla in 60
seconds** — download, double-click, see calendar — with zero IT setup, no
server admin, no auth friction. The intended use case is **evaluation /
trial**, not long-term single-user operation: someone curious about rapla
installs the standalone to see if it fits their organization, then either
moves on or graduates to a proper multi-user server deployment.

Rapla per its actual use case is **multi-user**: shared resources, multiple
schedulers, permissioned access. The standalone deliberately strips that down
to "one user, one XML file, no auth" so the trial install needs no DB, no IT
involvement, no decisions. The on-disk XML data file is the same format a
file-backed server deployment uses, so a happy trial user can graduate to
multi-user by pointing a real `rapla-app` server at the same XML file — no
data migration step.

The standalone build is **distributed separately from the server distribution**
— its own MSI artifact, its own release cadence. The server distribution
(`rapla-app` fat JAR) is unchanged.

**Not in scope: dhbwrapla.** Institutional deployments (DHBW Mosbach +
similar) need NTLM/Keycloak federation, multi-user permissions, and the
server's auth surface. Those are handled by the full server build with the
dhbwrapla plugin — standalone is explicitly a trial-only install, not an
institutional one.

## Scope

### In scope

- A single signed MSI installer targeting **Windows 11 x64 only**.
- Bundles a [Tauri](https://tauri.app) Rust shell + a jlinked JRE + a stripped
  Spring Boot fat JAR (standalone variant) into one ~120 MB MSI.
- Renders the existing Angular SPA inside a Tauri window. WebView2 (Edge
  Chromium, V8) handles rendering — the same engine as Chrome, kept current
  by Windows Update.
- Launches the Spring Boot JAR as a Tauri child process on window open; kills
  it cleanly on window close. No background service.
- Storage: XML `FileOperator` writing `rapla-data.xml` to
  `%APPDATA%\rapla\` — **same on-disk shape as a file-backed server
  deployment**, so a trial user who decides to deploy multi-user can point
  a real `rapla-app` server at the same XML file with zero migration. The
  XML format is the load-bearing portability story here, which is why we
  picked it over HSQLDB.
- Auth: `passwordCheckDisabled=true` server-side — the OAuth surface
  stays enabled (SPA still runs its normal login flow, Spring AS still
  serves `/login` form), but any submitted password is accepted because
  the server-side password check is no-oped. Same auth code path as
  production; one extra click vs zero-login but cleaner architectural
  consistency with the multi-user server.
- Code signing via the existing SSL.com `erdkante GmbH` cert + YubiKey
  (`docs/signing.md`), extended to cover Windows PE binaries (the Tauri
  `rapla.exe` + the MSI installer). Build host is Windows; `signtool.exe`
  signs against the YubiKey via the Windows Certificate Store.
  **v1 ships unsigned / self-signed for trial test builds** (see the
  `-Sign` flag on `rapla-standalone/build/build.ps1`) — production-grade
  signing requires the maintainer present with the YubiKey plugged in.
- Standalone build produced via a Maven profile **`-Pstandalone`** that
  excludes the rapla-client (Swing) module + rxjava and skips the JNLP
  webclient staging steps. v1 keeps the security/OAuth libs on the
  classpath (runtime-disabled via `passwordCheckDisabled=true` instead
  of dep-excluded — sidesteps the `@ConditionalOnClass` surgery that
  full dep exclusion would need). Net standalone fat JAR: ~45 MB
  (vs ~49 MB default). Bigger savings from full security-dep exclusion
  are deferred to Phase 1.5 — see Open Questions.

### Out of scope (this PRD)

- **Auto-update**. v1 is manual download per release. Phase 2 will wire
  Tauri's built-in updater + a JSON manifest on a static host. Keeping it out
  of v1 lets us ship faster without standing up update-manifest hosting.
  Acceptable because a trial install isn't a long-term workflow — users who
  decide to keep using rapla graduate to the server.
- **macOS / Linux** desktop installers. Single-target keeps the build matrix
  trivial. Cross-platform can be a Phase 3 if demand appears.
- **Multi-user on the same machine**. The trial install is single-user by
  design. Multi-user → graduate to the full server deployment.
- **dhbwrapla / institutional deployments**. Standalone explicitly drops
  auth and external IdPs — the dhbwrapla plugin (NTLM, Keycloak federation)
  is not loaded, not built into the standalone fat JAR. Institutional
  deployments continue to be the full server build (per dhbwrapla's own
  `AGENTS.md`).
- **Microsoft Store distribution (MSIX)**. MSI is fine for a trial install;
  MSIX is a Phase 2+ option if Store delivery becomes useful.
- **CI release automation**. v1 release is run manually on the maintainer's
  Windows machine with the YubiKey plugged in (same touch-and-hold UX as
  the existing JAR signing). CI signing via SSL.com eSigner cloud can be a
  Phase 2 if release cadence picks up.
- **Documented "promote standalone → multi-user server" workflow**. The
  XML format is portable by construction — a full server can load the same
  `rapla-data.xml` — but a step-by-step doc explaining the graduation path
  is a separate (small) follow-up doc, not part of this PRD.

## Architecture

```
┌─ rapla.exe  (Tauri main process, Rust)  ─────────────────────┐
│                                                               │
│  ├─ spawn rapla-server.exe child (jlinked JRE wrapping the    │
│  │   standalone Spring Boot fat JAR) on random localhost port │
│  │                                                            │
│  ├─ Tauri Window → http://127.0.0.1:<port>/app/  (Angular SPA)│
│  │   rendered by Windows WebView2 (Edge Chromium / V8)        │
│  │                                                            │
│  └─ On window close: SIGTERM child, wait 5 s, taskkill if not │
│      gone. No orphans, no background services.                │
│                                                               │
└───────────────────────────────────────────────────────────────┘

Single-instance lock via Tauri's plugin-single-instance — only one rapla.exe
per Windows user; double-launching focuses the existing window instead.

Data:  %APPDATA%\rapla\rapla-data.xml         (XML FileOperator, autosaved)
Logs:  %LOCALAPPDATA%\rapla\logs\rapla.log    (Logback file appender)
```

### Build artifacts

| File | Source | Size (approx) |
|---|---|---|
| `rapla.exe` | Tauri Rust shell, signed | ~6 MB |
| `rapla-server.exe` | jlinked JRE + standalone fat JAR, signed | ~95 MB |
| `rapla-installer.msi` | WiX MSI bundle, signed | ~120 MB total |

The MSI installs to `%ProgramFiles%\rapla\`, writes a Start Menu shortcut,
and registers an uninstaller in Add/Remove Programs. No system-wide changes,
no services, no registry pollution beyond standard uninstall metadata.

### Standalone Spring Boot variant

A new Maven profile `-Pstandalone` on `rapla-app` (and an accompanying
`application-standalone.yml`):

- **Excludes** at the dependency level: `spring-security-*`,
  `spring-security-oauth2-authorization-server`, `nimbus-jose-jwt`,
  `springdoc-openapi`, the rapla-client module entirely, and the
  external-IdP plugin jars (Microsoft, Google, Keycloak external).
- **Disables** at runtime: OAuth discovery returns `enabled: false`,
  `passwordCheckDisabled=true`, XML `FileOperator` selected over JDBC.
- **Guards** with `@ConditionalOnClass` / `@ConditionalOnProperty` on the
  security-related `@Configuration` classes so the missing dependencies
  don't break the boot.
- Net JAR size: ~30-35 MB (vs ~60 MB full server).

The non-standalone build (`mvn package` with no profile) is unchanged — same
fat JAR as today, with the full security/auth/rapla-client surface.

## Plan

Build top-down. Each phase is verifiable in isolation before moving on.

### Phase 1 — Standalone Spring Boot variant ✓ landed 2026-05-25

1. ✓ Added `-Pstandalone` profile to `rapla-app/pom.xml` — excludes
   rapla-client + rxjava (`<scope>provided</scope>`), sets
   `webclient.skip=true` so the JNLP webclient staging executions no-op.
2. ✓ Added `application-standalone.yml` — `passwordCheckDisabled=true`,
   `server.port=0` (random), `file-datasources.raplafile` →
   `${APPDATA}/rapla/rapla-data.xml`. OAuth surface stays enabled.
3. ✓ Converted `RaplaAuthentificationService.passwordCheckDisabled` from a
   static unused field to a Spring `@Value` injected via the constructor
   (key: `rapla.password-check-disabled`, default false).
4. ✓ `StandaloneBootTest` (rapla-app, `@Tag("e2e")`) boots the standalone
   profile via `@SpringBootTest` + MockMvc and asserts `/api/auth/oauth/config`
   returns `enabled=true` (regression-tests the "keep the OAuth stuff"
   decision + catches future configuration drift on every PR).
5. ✓ Verified locally: `mvn -pl rapla-app -am package -Pstandalone -DskipTests`
   produces a ~45 MB fat JAR (vs ~49 MB default). All existing tests
   still pass.

**Phase 1.5 (deferred, follow-up PR)** — bigger JAR slim: add
`@ConditionalOnClass` guards on `SecurityConfig`, `JwtConfig`,
`AuthorizationServerConfig`, exchange-sync plugin, etc. → exclude
spring-security-*, spring-authorization-server, springdoc-openapi,
nimbus-jose-jwt, exchange-sync. Projected: another ~20 MB shaved.
Skipped in v1 because the @ConditionalOnClass discipline is fragile and
needs careful iteration; trial-install UX value is the same at 45 MB vs
25 MB.

### Phase 2 — jlinked JRE

5. Determine the minimal JDK module set via `jdeps --module-path` against the
   standalone fat JAR. Probably `java.base`, `java.desktop`, `java.logging`,
   `java.management`, `java.naming`, `java.net.http`, `java.sql`,
   `jdk.crypto.cryptoki`, `jdk.unsupported`, plus whatever Spring needs.
6. `jlink --module-path <jdk-modules> --add-modules <set> --strip-debug
   --no-header-files --no-man-pages --compress=zip-9 --output rapla-jre`.
   Target Windows x64 JRE distribution (~45-55 MB stripped).
7. Verify: wrap the standalone fat JAR + jlinked JRE in a quick launcher
   script; confirm it boots without a system Java installed.

### Phase 3 — Tauri shell ✓ scaffolded 2026-05-25 (Windows verification pending)

8. ✓ Created `rapla-standalone/` sibling tree (outside the Maven reactor —
   same model as `rapla-angular/` per PRD 026). Contains:
   - `README.md` — what the tree is + how to build on Windows
   - `src-tauri/Cargo.toml`, `tauri.conf.json`, `build.rs`, `src/main.rs`
   - `src-tauri/icons/` — placeholder; needs real `icon.ico`
   - `build/build.ps1` — Windows build orchestrator (jlink + cargo tauri build + signtool)
   - `.gitignore` — for `target/`, `runtime/`, signing material
9. ✓ `main.rs` implements the child-process launcher: pick free port via
   `portpicker`, spawn `rapla-server.bat` with `--spring.profiles.active=standalone`
   + `--server.port=N`, TCP-poll the port for readiness (30 s timeout),
   navigate the Tauri window to `http://127.0.0.1:N/app/`. Single-instance
   lock via `tauri-plugin-single-instance`. Window close → SIGTERM child →
   wait 5 s → hard-kill via `wait_timeout`.
10. **Pending verification on Windows** — `cargo tauri dev` on a Windows 11
    machine has not yet been run by anyone. Maintainer-side checklist:
    - `cargo install tauri-cli --version "^2.0"` succeeds
    - `cargo tauri dev` boots a window pointing at the standalone server
    - Close window → child JVM exits within 5 s (Task Manager confirms)
    - Re-launch focuses existing window instead of starting a second JVM

### Phase 4 — MSI packaging + signing ✓ partially scaffolded 2026-05-25 (Windows verification pending)

11. ✓ `tauri.conf.json` declares `bundle.targets: ["msi"]` and bundles
    the jlinked JRE + standalone fat JAR via `bundle.resources` pointing
    at `runtime/rapla-server`.
12. ✓ `build/build.ps1` accepts a `-Sign` flag. When set, runs `signtool sign`
    against the SSL.com cert via the Windows Certificate Store, looking
    up the cert by thumbprint (`$env:RAPLA_SIGN_THUMBPRINT` or the
    `-CertThumbprint` script arg). Without `-Sign`, build produces an
    unsigned MSI (SmartScreen warns on install — acceptable for a trial
    install of the trial install).
13. **TODO** (Windows-side, maintainer): document the
    Windows-side YubiKey + signtool setup as a new section in
    `docs/signing.md`. Mostly: install YubiKey Manager for Windows, the
    PIV cert auto-appears in Windows Cert Store (Personal → Certificates),
    grab its thumbprint, export as `RAPLA_SIGN_THUMBPRINT`.
14. **Pending verification on Windows** — `./build/build.ps1 -Sign`
    produces a signed MSI. Right-click → Properties → Digital Signatures
    shows the `erdkante GmbH` cert chain. SmartScreen accepts on install
    (instant since SSL.com EV cert has reputation).

### Phase 5 — End-to-end manual verification

15. Fresh Windows 11 VM (or clean user profile). Install via the MSI.
    Confirm:
    - No SmartScreen warning (signed)
    - Start Menu shortcut present
    - Double-click opens the Tauri window
    - Calendar loads, no login dialog
    - Create / edit / delete a reservation persists across restart
    - Close window → `java.exe` child also exits (Task Manager confirms)
    - Uninstall removes everything from `%ProgramFiles%\rapla\` (leaves
      `%APPDATA%\rapla\` data intact for reinstall)
16. Document the end-user workflow in a new `docs/standalone-install.md`
    (≤1 page).

## Tests

- **Phase 1 (standalone JAR)**: existing tier-3 / tier-4 tests pass with
  `-Pstandalone` activated (most security-flavored tests will be skipped via
  `@Tag` or simply not loaded). Add one acceptance test:
  `StandaloneBootTest` boots the standalone variant via `@SpringBootTest`
  with the `standalone` profile, hits `/api/resources`, asserts 200 (no
  auth gate). Lives in `rapla-app/src/test/.../StandaloneBootTest.java`,
  tagged `@Tag("standalone")`.
- **Phase 2 (jlink)**: no automated test — manual smoke (does the JAR boot
  with only the stripped JRE?). Lives in `docs/development.md` as a
  "verify jlink" recipe.
- **Phase 3-4 (Tauri shell + MSI)**: no automated test in the Maven reactor.
  The Rust shell can have unit tests for the child-process launcher logic,
  but the MSI itself is verified manually on Windows 11. Track regressions
  via a `rapla-standalone/CHECKLIST.md` that the maintainer walks through
  before each release (one or two pages, ~10 items).
- **Phase 5 (end-to-end)**: the manual checklist above.

No tier-7 browser e2e for standalone in v1 — adds CI complexity without
catching things the existing `rapla-angular` Playwright suite doesn't
already cover for the SPA.

## Open Questions

1. **Where does the `rapla-standalone/` tree live?** Sibling to
   `rapla-angular/` (outside the Maven reactor) seems right — it's not Java,
   not Maven-built, has its own toolchain (Rust + Tauri CLI). Follow PRD 026
   precedent.

2. **Signing on CI vs maintainer's Windows machine.** v1 is "maintainer
   builds + signs locally on Windows with YubiKey plugged in." If release
   cadence picks up (>monthly), add a GH Actions windows-latest job that
   signs via SSL.com eSigner cloud (no physical YubiKey in CI). For a
   trial-install use case where releases are infrequent, manual is fine.

3. **Standalone JAR `@ConditionalOnClass` discipline.** Adding the profile
   exclusions means several `@Configuration` classes need careful guards.
   Risk: a future PR adds a security-flavored bean without a guard,
   breaking the standalone build silently. Mitigation: the
   `StandaloneBootTest` from the Tests section catches this — if the JAR
   fails to boot, the test goes red. Make sure CI runs it on every PR
   (not gated behind `-Pstandalone` only — run it as part of the default
   reactor test phase against the standalone fat JAR).

4. **First-launch data file.** Does the standalone ship a starter
   `rapla-data.xml` (with the bundled `admin` user + a minimal demo
   schedule so the empty calendar isn't *too* empty) that gets copied to
   `%APPDATA%\rapla\` on first launch? Or does the standalone server
   auto-create a fresh data file on boot if none exists?

   For a trial install, **a small starter file is probably better UX** —
   the user sees rapla doing something on first open instead of staring
   at an empty grid. Bundle a tiny `testdefault.xml`-shaped file with
   2-3 sample resources and 1-2 sample reservations. Costs ~5 KB; lets
   the user click around immediately. Lean: starter file. Confirm in
   Phase 1.
