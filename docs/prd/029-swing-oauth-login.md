# PRD 029: Swing Login via OAuth 2.0 (Browser-based, PKCE Loopback)

**Status:** in-progress — phase 1 done (2026-05-12). Shipped: discovery endpoint, single `rapla-client` RegisteredClient covering both Swing and (planned) Angular, `SwingOAuthLoginFlow`, `ConnectInfo` token path, UI button wired end-to-end, custom redirect URI validator with WSL bridge (172.16.0.0/12 dev convenience) and same-origin (zero-config Angular) allowances, paste-URL fallback dialog with cancel-aborts-flow, server-driven paste fallback toggle (`rapla.oauth.show-paste-fallback`, default off). **Token unification**: `/auth/login` now signs with the same RSA JWKSource as `/oauth2/token` (Option A from chat 2026-05-12) — composite HMAC+RSA decoder collapsed to a single RSA decoder, both login paths produce structurally identical tokens. **Persistent JWK**: the RSA keypair is read from `RaplaKeyStorage` (rapla preferences, persisted to data file) instead of being regenerated in-memory each startup — tokens survive server restart on both paths. End-to-end verified live with embedded auth server in WSL2. Setup doc at `docs/authentication.md`. Phase 2 (see §Phase 2 below) reframes the login UX with browser-OAuth as the primary path and Swing form as a fallback. PRD 031 handles refresh-token mechanics; PRD 032 (future) handles external IdP. Phase 2 includes those plus the UX direction change.
**Date:** 2026-05-12

## Goal

Add a second sign-in path to the Swing login dialog: a **"Sign in with
browser…"** button that runs the RFC 8252 Authorization Code + PKCE flow
against the rapla-app Spring Authorization Server, captures the access
token via a localhost loopback redirect, and uses it as `Authorization:
Bearer …` against the rapla REST endpoints — same surface the existing
JWT-handling `AuthController` / `TokenHandler` already understand.

The existing username/password form stays — this is an additional path,
not a replacement. The motivation is twofold:

1. **SSO**. Today end-users have to type a rapla-local password. With the
   OAuth path, rapla can delegate authentication to whatever identity
   provider the deployment configures (the embedded Spring Authorization
   Server in the default deployment; an external IdP like Keycloak, Azure
   AD, or Google Workspace in custom deployments).
2. **No shared-secret on the wire/disk**. PKCE is designed for public
   clients that can't keep a `client_secret`. JNLP-shipped Swing is
   exactly such a client. The user's browser cookie does the heavy
   lifting; rapla holds only a short-lived access token in memory.

## Scope

### In scope

- A third button on `LoginDialog` (next to "exit" + "login") labelled per
  a new i18n key `login.oauth.button`. Hidden by default; visible only
  when the server signals OAuth support (see "Discovery" below).
- A new `SwingOAuthLoginFlow` class in `rapla-client` (no Spring DI
  coupling — plain Java + JDK `HttpServer`/`HttpClient`) that runs the
  6-step flow: bind loopback → PKCE → `Desktop.browse` → wait for
  callback → exchange code → return token.
- A WSL-aware browser-launch fallback chain (`Desktop.browse` → tier-2
  CLI shell-out → tier-3 copy/paste dialog).
- A new `ConnectInfo` variant carrying an access token instead of
  `username + password`. `RemoteConnectionInfo` already supports
  `setAccessToken(...)` — we wire it into the login path.
- Server-side: register a single `RegisteredClient` named `rapla-client`
  with `client_authentication_methods: none`, grant types
  `authorization_code, refresh_token`, `require_proof_key: true`,
  and multiple loopback redirect URIs covering both the Swing client
  (`http://127.0.0.1/login/oauth2/code/rapla`) and the Angular SPA
  (`http://localhost/auth/callback`, `http://127.0.0.1/auth/callback`).
  All hosts are loopback so Spring Security ≥6.1 any-port matching
  applies. Production deployments behind a real hostname add their
  URI via the standard Spring env-override path.
- A discovery endpoint `GET /rapla/auth/oauth/config` returning the
  authorize/token URLs and `client_id` (so the Swing client doesn't
  hardcode them — different deployments point at different IdPs).
- Tests:
  - Tier 3 MockMvc: discovery endpoint returns the right shape.
  - Tier 3 MockMvc: bearer-token gate accepts a token minted by the
    embedded auth server, rejects garbage.
  - Tier 1/2 unit: PKCE generation, redirect-URI building, loopback
    listener round-trip (no real browser — drive the callback with
    `HttpClient` directly).
  - Manual smoke under `test-jnlp-launch` skill (real OWS, real browser).

### Out of scope (this PRD)

- Replacing the username/password path. Both stay.
- Refresh-token-driven silent reauth on token expiry. Phase 2.
- External IdP wiring (Keycloak, Azure AD). Phase 2 — covered by making
  the discovery endpoint return whichever IdP the deployment configures;
  no client changes needed.
- Storing tokens on disk. Tokens live in process memory only.
- An OAuth-aware Web/Angular front-end (PRD 026). Different surface;
  share the discovery endpoint when we get there.

## Architecture

### Flow (RFC 8252)

```
Rapla Swing                 system browser                  Auth server
    │ ① bind 127.0.0.1:N        │                              │
    │ ② Desktop.browse(authorize) ─▶                            │
    │                           │ ③ GET /authorize ───────────▶ │
    │                           │ ◀── login page                │
    │                           │ ④ POST creds ───────────────▶ │
    │ ◀── ⑤ 302 redirect to ────│ ◀── 302 to 127.0.0.1:N        │
    │     127.0.0.1:N/cb?code=… │                              │
    │ ⑥ POST /oauth2/token (code + PKCE verifier) ───────────▶ │
    │ ◀── ⑦ { access_token, refresh_token }                  ─│
    │ ⑧ Authorization: Bearer eyJ… → rest of rapla REST       │
```

Steps ①–⑦ are entirely client-driven; the server is the standard Spring
Authorization Server endpoints. Step ⑧ is the existing rapla REST surface
unchanged.

### Server-side (rapla-app)

- `application.yml`: add a `rapla-client` `RegisteredClient` block alongside
  the existing `rapla-swagger` one. Same authorize/token endpoints.
- New `OAuthConfigController` exposing `GET /rapla/auth/oauth/config`:
  ```json
  {
    "enabled": true,
    "clientId": "rapla-client",
    "authorizeUrl": "http://localhost:8051/oauth2/authorize",
    "tokenUrl":     "http://localhost:8051/oauth2/token",
    "scopes": ["openid", "profile"]
  }
  ```
  In dev the URLs are `localhost:8051`; in prod the same controller emits
  whatever public base URL the deployment configured. When OAuth is
  disabled in `application.yml`, `enabled: false` and the Swing button
  stays hidden.

### Client-side (rapla-client)

- `SwingOAuthLoginFlow.start(OAuthConfig cfg) → CompletableFuture<String>`
  — returns the access token. Internals:
  ```java
  HttpServer srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
  int port = srv.getAddress().getPort();
  // … PKCE, state, /cb handler, Desktop.browse, code exchange …
  ```
- `LoginDialog`: a third `JButton oauthBtn` in the `buttonPanel`. New
  setter `setOauthAction(Action)`. Hidden when `setOauthAction(null)`.
- `RaplaClientServiceImpl.startLoginInThread`: probe
  `/rapla/auth/oauth/config` once on dialog show; if `enabled`, wire the
  third button to a `SwingOAuthLoginFlow`-driven action that on success
  builds a `ConnectInfo` carrying the access token and calls the existing
  `login(reconnectInfo)`.
- `ConnectInfo`: add an `accessToken` field + matching constructor. The
  existing username/password constructor stays untouched. `RemoteConnectionInfo`
  already has `setAccessToken(...)` — `RaplaClientServiceImpl.login` picks
  the token path when `ConnectInfo.accessToken != null`.

### Browser-launch fallback chain

```java
void openBrowser(URI url) {
    if (tryDesktopBrowse(url)) return;
    if (isWsl() && tryProcess("wslview", url)) return;
    if (isWsl() && tryProcess("cmd.exe", "/c", "start", "", url)) return;
    String os = osName();
    if (os.contains("win"))  tryProcess("rundll32", "url.dll,FileProtocolHandler", url);
    else if (os.contains("mac")) tryProcess("open", url);
    else for (String cmd : List.of("xdg-open","gio","kde-open5","sensible-browser","x-www-browser","firefox"))
        if (tryProcess(cmd, url)) return;
    showCopyPasteFallbackDialog(url);   // last-resort UI
}
```

WSL detection: `System.getenv("WSL_DISTRO_NAME") != null
|| Files.exists(Path.of("/proc/sys/fs/binfmt_misc/WSLInterop"))`.

### JNLP / OWS permissions

The flow needs:
- `SocketPermission("127.0.0.1:1024-", "listen,accept,resolve")` — already
  covered by `<all-permissions/>`.
- `RuntimePermission("exec")` for `ProcessBuilder` fallbacks — covered.
- `AWTPermission("showWindowWithoutWarningBanner")` for `Desktop.browse` —
  covered.

Every signed jar on the OWS classpath needs `Permissions: all-permissions`
in its manifest (see `project_jnlp_signing_pitfalls.md`). No JNLP schema
change required; the existing webclient/ assembly already sets the right
attributes.

## Plan

1. **Server: discovery endpoint + registered client.**
   - Add `OAuthConfigController` in rapla-server. Tier-3 MockMvc test
     covers both `enabled: true` and `enabled: false`.
   - Add `rapla-swing` `RegisteredClient` to `application.yml`.
   - Verify the existing `AuthController` / `TokenHandler` accept the
     access token minted by the auth server (one tier-3 test: get a
     token via the auth-server programmatically, call
     `/rapla/storage/resources` with it, assert 200).

2. **Client: SwingOAuthLoginFlow (no UI yet).**
   - `SwingOAuthLoginFlow` + browser-launch helper + WSL detection.
   - Tier-1/2 tests with `HttpClient` driving the callback — no real
     browser needed. Tests that the loopback handler extracts `code`
     and `state`, rejects mismatched `state`, exchanges the code with
     the right PKCE verifier.

3. **Client: ConnectInfo + RaplaClientServiceImpl wiring.**
   - Add `accessToken` field to `ConnectInfo` + constructor.
   - `RaplaClientServiceImpl.login(...)`: when token present, skip
     username/password REST call and set the token directly on
     `RemoteConnectionInfo`.
   - Headless integration test: build a `ConnectInfo` with a real
     token from the embedded auth server, complete login end-to-end.

4. **UI: third button on LoginDialog.**
   - Add `oauthBtn`, `setOauthAction`, i18n key, show/hide logic.
   - Wire the discovery probe in `startLoginInThread`.
   - Manual smoke under `test-jnlp-launch`.

5. **Documentation.**
   - Update `AGENTS.md` §9 with the new login flow and how to test
     locally (server already running per §8 + click "Sign in with
     browser…").
   - When status hits `done`, `git mv` to `docs/prd/done/`.

## Tests

| Tier | Test                                                    | What it locks in |
|------|---------------------------------------------------------|------------------|
| 3    | `OAuthConfigControllerTest`                             | Discovery shape + enabled/disabled toggle |
| 3    | `BearerTokenAuthIntegrationTest`                        | Token from auth server unlocks rapla REST |
| 1    | `PkceUtilTest`                                          | verifier length, BASE64URL no-padding, SHA-256 challenge |
| 2    | `SwingOAuthLoginFlowTest` (driven by `HttpClient`)      | Full handshake without a browser; state mismatch rejected |
| 2    | `BrowserLaunchFallbackTest`                             | Each tier of the chain tried in order; WSL detection |
| 3    | `OAuthLoginConnectInfoIntegrationTest`                  | `ConnectInfo` with token → full login → first REST call works |
| e2e  | Manual `test-jnlp-launch` smoke (tagged `e2e`)          | Real OWS, real system browser, real human |

## Phase 2 — Browser-OAuth as the primary login

Phase 1 (above) added "Sign in with browser…" as one of three buttons on a
Swing dialog where username/password is still the default. Phase 2 inverts
this: the browser flow becomes the **primary login UX**, the Swing
username/password form becomes a fallback used only when (a) no external
IdP is configured *and* (b) the browser flow couldn't reach the auth
server.

### Vision

- **Startup**: the Swing client probes discovery, then immediately starts
  the OAuth flow if enabled. It does NOT show the Swing login dialog
  unless the OAuth flow can't proceed (auth server unreachable, browser
  launch failed and paste fallback disabled, etc.).
- **Browser-side**: rapla's `/rapla/login` form (and Keycloak's, when it's
  the IdP) supports **"remember me"**. Once a user logs in once and ticks
  remember-me, the browser cookie keeps them signed in. Subsequent rapla
  launches drive the OAuth flow silently through the browser — the browser
  hits the SSO cookie, the auth server immediately redirects back with a
  code, the loopback handler catches it, the Swing client gets a token,
  the main view opens. **The user never sees a login dialog.**
- **Mid-session 401**: when the access token expires and refresh-token
  reauth (PRD 031) also fails, the Swing client transparently re-runs the
  OAuth flow. With the SSO cookie still valid, this is silent. Without
  it, the browser pops the login form for one round-trip.
- **Logout** / **rapla client restart**: clears the local tokens and
  refresh URL cache, then drops back to "trigger OAuth flow on next
  action". The user can sign in with whatever flow the IdP exposes
  (different account, password reset, federated SSO, etc.).
- **Swing username/password form**: hidden by default in Phase 2. Only
  surfaces in the narrow case "no external IdP configured AND OAuth flow
  cannot start". Effectively an **emergency-only fallback**: the auth
  server is unreachable, the browser launch failed, or it's a first-time
  setup before SSO is configured — situations where the user couldn't get
  in any other way. Accessed via an "Other options" expander on the
  login dialog. When external IdP is configured (Keycloak), this form is
  hidden unconditionally — there's no local user store to authenticate
  against anyway.

### Why this is the right direction

1. **Matches modern desktop OAuth UX.** Every modern desktop app that
   uses SSO (Slack, Zoom, GitHub Desktop, the JetBrains IDEs, the
   Microsoft Teams native client) does exactly this — the local
   application doesn't render a credentials form; it bounces the user
   into the system browser, which already knows them.
2. **Forces all authentication through one funnel.** The auth server
   (or Keycloak) handles password validation, MFA, lockouts, audit
   logs, password reset flows. The Swing client never sees a password
   in memory. Cleaner security boundary.
3. **Plays nicely with external IdPs.** A Keycloak deployment expects
   the user to land on Keycloak's login page. Today's Swing dialog
   undermines that by trying to validate passwords locally against
   `/auth/login`. Phase 2 removes that conflict.
4. **Closes the regression introduced by OAuth.** Phase 1's
   `MyCustomConnector.reauth` had to learn about "OAuth session has no
   password to re-submit." Phase 2 makes mid-session reauth re-run the
   OAuth flow instead — symmetric with the initial login, no fallback
   logic needed.

### Scope

#### In scope

- **`/rapla/login` "remember me"**: extend Spring Security's form-login
  config with `RememberMeServices` (persistent token, default 30 d).
  Add a checkbox to `LoginPageController`'s HTML form. Configurable
  TTL via `rapla.auth.remember-me-days`.
- **Auto-start OAuth flow on Swing client launch**: in
  `RaplaClientServiceImpl.startLogin`, when discovery reports OAuth is
  enabled, skip showing the login dialog and run `runOauthLogin()`
  immediately. Show the dialog only if (a) discovery fails or
  (b) `cfg.isEnabled() == false`.
- **OAuth retry on 401 after refresh fails**: when
  `MyCustomConnector.reauth`'s refresh path fails and there's no
  cached password (OAuth-only session), trigger a new OAuth flow via
  a small callback registered by `RaplaClientServiceImpl`. UX: the
  busy spinner reactivates and the browser opens for ~1 second
  (silent if cookie present, prompts if not).
- **Hide password fields by default**: `LoginDialog` no longer shows
  the username/password fields unless explicitly toggled. The fallback
  path renders just an "Other login options" link that expands the
  fields if the user wants to. Visually similar to how Slack hides
  email-password login behind "Try another way", or JetBrains IDEs
  put license-server login two clicks deep — the primary path
  visually dominates, the emergency path is reachable but unobtrusive.
- **Hide password path entirely under external IdP**: the discovery
  endpoint reports a new boolean `localAccountsEnabled`. When `false`
  (typical Keycloak deployment), `LoginDialog` never offers the
  password fields, even on fallback.
- **Logout / restart returns to clean OAuth start**: the existing
  logout path clears `RemoteConnectionInfo`. Add: clear cached
  `refreshUrl`, clear the persisted refresh token (see below), clear
  any browser session cookie that the auth server remembers (Spring
  `RememberMeServices.logout()`). On next rapla launch, the user gets
  a fresh OAuth flow.

- **Persist the refresh token client-side**: on a successful login,
  store the refresh token in a per-user file in the rapla profile
  directory (or OS keychain — see Open Question 10). On next launch:
  read it, hit `refreshUrl` directly, mint a fresh access token. No
  browser tab opens at all. **This is the "zero-browser-on-launch"
  property**: with a cached refresh token, the cookie-based silent
  OAuth flow above is unnecessary — we skip the browser entirely
  until the refresh token itself expires (~30 days idle). Fallback:
  if the cached refresh token doesn't work (expired, revoked, server
  refuses), drop back to the cookie-based silent OAuth flow, which
  drops back to the form-login UX. Three layers of "try the cheapest
  thing first" before showing the user anything.

- **Token store is best-effort, never blocks login**. The persistence
  backend (JNLP `PersistenceService`, dotfile, or future OS keychain)
  can fail for many reasons: read-only filesystem, missing
  permissions, sandbox restrictions, corrupted store, missing
  library at runtime, disk full. Every `TokenStore` operation must
  be wrapped in `catch (Throwable)` (not just `Exception` — we also
  want to swallow `LinkageError`, `NoClassDefFoundError`, etc., that
  might come from optional dependencies like a keychain library)
  and degrade gracefully:
  - **Read failure** → treat as "no token cached" → fall through to
    cookie-based silent OAuth flow → falls through to form login if
    needed. User sees a normal login, not an error.
  - **Write failure** → log a warning, proceed without persistence.
    The user is logged in for the current session; next launch will
    just re-trigger the OAuth flow. Don't surface the warning to the
    user — it's not their problem to solve.
  - **Delete failure on logout** → log a warning, complete logout
    anyway. The stale token in the store will fail validation on
    next attempt (server-side refresh-token state, JWT signature
    rotation, or just normal expiry), so the worst case is a
    one-time silent-fail-then-prompt, not a security regression.
  Catch-Throwable is intentional here because the *purpose of the
  token store is to skip the login prompt*, and failure of that
  goal should always degrade to "show the login prompt", never to
  "error out the entire application launch".

#### Out of scope (Phase 2)

- External IdP wiring itself. That's PRD 032. Phase 2 is the
  client-side UX shift; IdP swap is a separate concern.
- Federated identity reconciliation (mapping Keycloak `sub` → rapla
  user UUID). Belongs in PRD 032.
- Persistent token revocation / "log me out everywhere" admin
  surface. Future PRD.
- API key UI from PRD 031 §6. Different surface; tracked there.

### Plan

1. **Server: `/rapla/login` remember-me.**
   - Wire `PersistentTokenBasedRememberMeServices` (or
     `TokenBasedRememberMeServices` — see Open Question 6) into
     Spring Security's form-login.
   - Add checkbox to `LoginPageController` HTML.
   - Configurable TTL via `rapla.auth.remember-me-days` (default 30).
2. **Server: `localAccountsEnabled` flag in discovery.**
   - Add to `OAuthConfigController.OAuthConfig`. Defaults to `true`
     for embedded auth server. Will flip to `false` when external IdP
     is configured (PRD 032).
3. **Client: auto-start OAuth flow on launch when enabled.**
   - `RaplaClientServiceImpl.startLogin` probes discovery first.
   - If `enabled == true`, run `runOauthLogin` directly (no Swing
     login dialog). The dialog only opens if discovery says OAuth is
     off OR the flow throws before the user sees a browser.
4. **Client: OAuth retry on 401 when refresh fails.**
   - In `MyCustomConnector.reauth`, after refresh and password reauth
     both fail, surface a `SessionExpiredException`.
   - `RaplaClientServiceImpl` (or a `ReauthCoordinator` injected into
     `MyCustomConnector`) catches it and re-runs `runOauthLogin`.
   - Cached refresh URL is reused; cached OAuth client_id is reused.
5. **Client: hide password fields by default, show "Other options"
   expander.**
   - `LoginDialog` gains a collapsible fields panel.
   - When discovery says `localAccountsEnabled == false`, the
     expander is also hidden — only "Sign in with browser…" remains.
   - When discovery is unreachable, default to "show fields" so the
     user has a way to log in.
6. **Client: logout flow.**
   - Existing logout clears `RemoteConnectionInfo`.
   - Add: revoke the SSO cookie if the auth server exposes a logout
     endpoint (Spring SAS does at `/logout`; Keycloak at
     `/protocol/openid-connect/logout`). Discovery endpoint exposes
     this URL.
   - On next launch, OAuth flow runs and prompts the user (since the
     cookie was revoked).
7. **Documentation.**
   - Update `docs/authentication.md` with the new UX flow + screenshot
     of the "Other options" expander.
   - Update PRD 029 status to Phase 2 in-progress.

### Tests

| Tier | Test | What it locks in |
|---|---|---|
| 3 | `RememberMeFormLoginTest` | Form POST with `remember-me=on` sets a persistent cookie; subsequent `/oauth2/authorize` skips the form |
| 3 | `DiscoveryReportsLocalAccountsFlagTest` | `localAccountsEnabled` field present and reflects config |
| 2 | `AutoStartOauthFlowTest` | When discovery says OAuth enabled, Swing client triggers `runOauthLogin` without showing the dialog |
| 2 | `OauthRetryOn401Test` | After refresh-token reauth fails, the connector triggers a fresh OAuth flow via the registered coordinator |
| 2 | `LoginDialogPasswordFieldsHiddenTest` | When `localAccountsEnabled == false`, password fields aren't visible |
| e2e | Manual smoke: log in, close client, relaunch — should land in main view with no dialog | The "no login dialog on startup" property |
| e2e | Manual smoke: log in, wait 1 h (or shorten TTL), trigger action — should silently reauth via cookie-backed OAuth flow | Mid-session OAuth retry works |

### Open Questions (Phase 2 specific)

6. **Persistent vs. signed remember-me?** Spring offers two
   `RememberMeServices` flavours. `TokenBasedRememberMeServices` signs
   a cookie with a server-side key (no DB rows); simpler, doesn't
   support revocation. `PersistentTokenBasedRememberMeServices` stores
   a row per device (revokable, audit-friendly).
   **No JDBC table needed**: rapla can back the persistent variant with
   its own preferences storage — either a single system-level JSON
   blob, or per-user preferences with a custom RememberMeServices that
   includes the username in the cookie. The preferences layer already
   persists to data file / JDBC (whichever the deployment uses), so
   restart-survival is free. Recommended: per-user variant for the
   future "show me signed-in devices" UX; or token-based for the
   simplest possible thing.
7. **Browser cookie scope.** Spring's `/rapla/login` cookie is scoped
   to `localhost:8051` in dev, the deployment domain in prod. Same
   browser already has session cookies for that domain — does
   remember-me actually buy us anything beyond what the browser does
   automatically? Probably yes: session cookies clear when the
   browser closes; remember-me persists across browser restarts.
8. **Should "auto-start OAuth flow" be configurable?** A deployment
   might want the explicit dialog for compliance reasons (visual
   confirmation that the user is about to enter credentials, even
   though they actually enter them in the browser).
   `rapla.oauth.auto-start: true` default, set `false` to keep the
   Phase-1 dialog UX. Cheap to add.
9. **What about Swing client restarts in offline mode?** If the
   server is unreachable, discovery fails. The fallback must give the
   user a way to retry. Today's "Exit" button is fine; consider also
   a "Retry connection" button on the failure screen.

10. **Refresh-token storage backend?**
    **Encryption note**: refresh tokens are not passwords. They are
    signed JWTs (~700 bytes) with claims `sub`, `typ=refresh`, `exp`.
    The threat model — other users on the same machine, malware
    running as the user, disk forensics — is either solved by
    file permissions (0600) or unsolvable by app-level encryption
    (a same-user attacker can derive any system-property-based
    encryption key). Custom AES with a system-derived key is
    [snake-oil cryptography](https://en.wikipedia.org/wiki/Snake_oil_\(cryptography\))
    — it adds code complexity for negligible security gain and risks
    giving operators a false sense of security. AWS CLI, GitHub CLI,
    Slack, Discord, JetBrains tools, and `~/.netrc` all store
    refresh-token-equivalent credentials in plain text — that's the
    industry standard for this data class.
    Three real options:
    (a) **plain file at `~/.rapla/tokens.json` with `0600` perms** —
    matches `~/.aws/credentials` security model, trivial to implement,
    fine for an internal scheduling tool. The boring right answer for
    rapla today;
    (b) **JNLP `PersistenceService`** (JSR-56, implemented by
    IcedTea-Web / OpenWebStart) — sandbox-scoped to the rapla
    codebase URL so other JNLP apps can't read it, requires no
    `all-permissions`, only works when launched via JNLP. Better
    scoping than (a) when running under OWS;
    (c) **OS keychain** via `java-keyring` or similar — Windows
    Credential Manager, macOS Keychain, Linux libsecret — actually
    meaningful security improvement (covers stolen-laptop disk
    forensics if FDE isn't on), platform-specific code paths, adds a
    small dependency. Skip until a deployment specifically asks.
    **Recommendation**: ship a hybrid: `PersistenceService` when
    `ServiceManager.lookup` succeeds (production OWS launches),
    `~/.rapla/tokens.json` fallback otherwise (dev runs, non-JNLP
    deployments). Same TokenStore interface, both backends ~50 LOC
    each. Plain bytes in both cases. **All backends wrap every
    operation in `catch (Throwable)` and degrade silently to "no
    cached token" — never surface storage errors to the user as
    login failures.** Sketch:
    ```java
    interface TokenStore {
        Optional<String> read();      // never throws; returns empty on any failure
        void tryWrite(String token);  // never throws; logs warning on failure
        void tryClear();              // never throws; logs warning on failure
    }
    ```
    The runtime returns the first store that succeeds at construction
    (e.g. `JnlpTokenStore` calls `ServiceManager.lookup` in its
    constructor, throws `UnavailableServiceException` if not under JNLP,
    factory falls through to `FileTokenStore`). If both fail —
    `~/.rapla/` is read-only, sandbox blocks file access, both
    backends throw — return a `NoOpTokenStore` that always reports
    "no token cached" on read and silently drops writes. The OAuth
    flow then runs every launch as if persistence didn't exist, which
    is the Phase-1 behaviour. Worst-case degradation is "we never
    persist", not "the app crashes on startup".

## Open Questions

1. **Multiple IdPs.** Does any rapla deployment today need to offer the
   user a *choice* of IdPs (e.g. "Sign in with Google" vs "Sign in with
   corporate SSO")? If yes, the discovery endpoint becomes a list and
   the button becomes a dropdown. Default assumption: single IdP per
   deployment, plain button.

2. **OIDC vs OAuth.** Do we want the `id_token` (OIDC) to surface user
   profile info (display name, email) into the Swing UI, or just the
   `access_token` (pure OAuth) and let the existing `getUser` REST call
   carry that? Simpler: skip OIDC for now, single scope `rapla`.

3. **Token expiry mid-session.** ~~Phase 2 — out of scope here.~~
   **Addressed in Phase 2 above + PRD 031**: refresh-token reauth
   (PRD 031) handles the common case; Phase 2's "OAuth retry on 401
   when refresh fails" handles the rest by re-running the OAuth
   flow silently against the SSO cookie.

4. **External IdP allow-list.** When a deployment configures an external
   IdP (Phase 2), does rapla need to validate that the issuer's
   public key matches a pinned set, or is "configured in
   `application.yml`" trust enough? Likely the latter — same trust model
   as any other Spring Security `jwk-set-uri`.

5. **Hide the username/password path entirely?** ~~Stretch goal for
   Phase 2~~ **Now in scope as Phase 2 (above)**: the discovery
   endpoint reports `localAccountsEnabled`, the Swing client hides
   the form when it's `false`, and even with local accounts enabled
   the form is collapsed behind an "Other options" expander —
   browser-OAuth is the primary path, password fields are reserved
   as an emergency-only fallback for offline / misconfigured
   deployments.
