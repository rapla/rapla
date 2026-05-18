# PRD 029: Swing Login via OAuth 2.0 (Browser-based, PKCE Loopback)

**Status:** in-progress — phase 1 done (2026-05-12), phase 2 mostly landed (2026-05-12, awaits e2e verification 2026-05-13). Phase 1 shipped: discovery endpoint, single `rapla-client` RegisteredClient covering both Swing and (planned) Angular, `SwingOAuthLoginFlow`, `ConnectInfo` token path, UI button wired end-to-end, custom redirect URI validator with WSL bridge (172.16.0.0/12 dev convenience) and same-origin (zero-config Angular) allowances, paste-URL fallback dialog with cancel-aborts-flow, server-driven paste fallback toggle (`rapla.oauth.show-paste-fallback`, default off). **Token unification**: `/auth/login` now signs with the same RSA JWKSource as `/oauth2/token` — composite HMAC+RSA decoder collapsed to a single RSA decoder, both login paths produce structurally identical tokens. **Persistent JWK**: RSA keypair read from `RaplaKeyStorage` (rapla preferences, persisted to data file) instead of being regenerated each startup — tokens survive server restart.

Phase 2 shipped 2026-05-12 (awaits e2e verification 2026-05-13):
- **Auto-OAuth on client launch.** `RaplaClientServiceImpl.startLogin` probes discovery; if `enabled==true`, fires `runOauthLogin` directly. Login dialog only opens when discovery is unreachable or returns `enabled==false`.
- **Refresh-token bootstrapping.** `MyCustomConnector.refreshUsingToken` reads `connectionInfo.refreshUrl` (added) and POSTs cached refresh JWT to `/api/auth/refresh`. Falls through to password-reauth only if refresh fails.
- **Hybrid `TokenStore`** for refresh-token caching: JNLP `PersistenceService` → `~/.rapla/tokens.json` (0600) → NoOp. All operations are `catch(Throwable)`; never surface storage errors as login failures.
- **In-JVM relaunch on logout.** `RaplaClientServiceImpl.logout()` POSTs `/api/auth/logout` (Bearer), opens browser tab to discovery's `logoutUrl`, clears `TokenStore`, then `SwingUtilities.invokeLater(start(null))`. Menu's "Logout / Restart" delegates to `logout()`.
- **"Waiting for browser sign-in" mode** on `LoginDialog` (`setBrowserLoginInProgress(msg)` hides fields/login/oauth buttons, keeps Exit) shown after logout while OAuth re-runs.
- **`prompt=login` race-defeater.** After logout, next `runOauthLogin` adds `prompt=login` to authorize URL so Spring SAS re-prompts even if the browser raced ahead of the server's session clear.
- **Server-side single-token-per-user**, rotate-when-stale (7-day renewal of 30-day TTL). `AuthController` stores `sha256(refreshToken)` under `user.preferences["org.rapla.auth.session"]`. `/api/auth/logout` clears the entry. Bounds DB writes to ~1/user/week.
- **REST path migration to `/api/`**. `RaplaSpringBootApplication` context-path dropped; all REST under `/api/`. Discovery's `refreshUrl` points at `/api/auth/refresh`; client's `serverURL` is `baseUrl + "api"`.
- **Functional "Remember me" checkbox** on `LoginPageController` HTML (browser `/login` page). NOT in Swing dialog — Swing has its own refresh-token cache via `TokenStore`.
- **`raplaUserDetailsService` bean** (`AuthorizationServerConfig`) adapting `RaplaFacade` — UUID-first (form-login stores `user.getId()` as auth name), username fallback. Returns `UserDetails` with placeholder password (`PersistentTokenBased…` doesn't use it).
- **`PersistentTokenBasedRememberMeServices` + `RaplaTokenRepository`** (`rapla-server/.../internal/`). Stores `{series → {username, tokenValue, lastUsed}}` as JSON under system preferences `org.rapla.auth.rememberMeTokens` — cookies survive server restart (matches persistent-JWK pattern).

Landed 2026-05-13 (signout bug fixes — both clients):
- **`/connect/logout` now clears the remember-me cookie.** Spring SAS's `OidcLogoutAuthenticationSuccessHandler` only clears HttpSession + SecurityContext by default — it never consults `RememberMeServices`, so `/connect/logout` was leaving `rapla-remember-me` alive and the next `/oauth2/authorize` silently re-authenticated via the surviving cookie ("sign out → instantly signed back in"). Fix: `SecurityConfig` promoted `RememberMeServices` to an explicit `@Bean` (PersistentTokenBasedRememberMeServices); `AuthorizationServerConfig.authorizationServerSecurityFilterChain` injects it and attaches a custom `OidcLogoutAuthenticationSuccessHandler` whose `logoutHandler = CompositeLogoutHandler(SecurityContextLogoutHandler, rememberMeServices)`. Verified live: after `/connect/logout` succeeds, the cookie is gone (`Set-Cookie: rapla-remember-me=; Max-Age=0`) and the persistent token row in `RaplaTokenRepository` is removed; next `/oauth2/authorize` requires fresh login.
- **`logoutUrl` in discovery flipped from `/logout` to `/connect/logout`** (`OAuthConfigController`). Both Swing and Angular now go through the OIDC RP-initiated logout endpoint; the prior split (`logoutUrl` form-logout + `endSessionUrl` OIDC) is collapsed.
- **Swing client plumbs `id_token` through to logout.** OIDC RP-initiated logout requires an `id_token_hint` parameter — without it Spring SAS returns 404 and the success handler (the one that clears the remember-me cookie) never runs, reintroducing the "signed back in" bug for Swing even though Angular was fixed. Fix: `OAuthTokens` gained an `idToken` field; `SwingOAuthLoginFlow.exchangeCodeForTokens` parses `id_token` from the token-endpoint response (scope=openid already enabled); `RaplaClientServiceImpl.finishOauthLogin` stores it in `RemoteConnectionInfo.idToken`; `logout()` appends `?id_token_hint=<urlencoded>` to the discovery `logoutUrl` before opening the browser tab. `RemoteConnectionInfo` gained `idToken` field + setter/getter. Log line `logout: opened browser ... (with id_token_hint)` confirms the hint is being sent.

Awaits e2e verification of remaining items: (a) `prompt=login` forces fresh form even with a surviving remember-me cookie (now moot for OIDC logout since the cookie is cleared, but still relevant for the Swing menu's "Logout / Restart" path that uses the in-JVM relaunch), (b) full Swing round-trip after `/api/` migration. Setup doc at `docs/authentication.md`. PRD 031 covers refresh-token mechanics (now mostly shipped); PRD 032 (future) handles external IdP.

**Known gotcha — server-restart invalidates id_token `sid` claims.** Spring SAS's `OidcSessionRegistry` is in-memory (the only built-in implementation). On every restart the registry is empty, so id_tokens minted before the restart have a `sid` that no longer matches any tracked session — `OidcLogoutAuthenticationProvider` rejects them with "OpenID Connect 1.0 Logout Request Parameter: id_token_hint" and `/connect/logout` returns 404 (Spring SAS doesn't expose authentication-failure responses on this endpoint per OIDC spec). Workaround for users: refresh the page (re-login → fresh id_token with currently-tracked `sid`). Proper fix: persistent `OidcSessionRegistry` backed by `RaplaFacade` system preferences, same pattern as `RaplaKeyStorage` (JWK) and `RaplaTokenRepository` (remember-me). Deferred — non-urgent in dev where restarts are explicit; users get a 404 once and re-login fixes it.

**Date:** 2026-05-12 (last updated 2026-05-13)

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

1. ✅ **Server: `/rapla/login` remember-me.** Shipped 2026-05-12 — `PersistentTokenBasedRememberMeServices` with `RaplaTokenRepository` backing (system preferences). Checkbox in `LoginPageController` HTML. TTL `rapla.auth.remember-me-days` (default 30).
2. ⏸ **Server: `localAccountsEnabled` flag in discovery.** Not yet wired — defer until PRD 032 (external IdP) needs it; the embedded SAS path doesn't gate on this today.
3. ✅ **Client: auto-start OAuth flow on launch when enabled.** Shipped 2026-05-12 — `startLogin` probes discovery; if enabled, runs `runOauthLogin` directly and only shows the dialog for fallbacks/waiting state.
4. ✅ **Client: OAuth retry on 401 when refresh fails.** Shipped 2026-05-12 — `MyCustomConnector.reauth` tries refresh-token first via `connectionInfo.refreshUrl`, falls through to password reauth, eventually to OAuth flow.
5. ⏸ **Client: hide password fields by default, show "Other options" expander.** Phase 2 chose a simpler shape — the dialog is hidden entirely when OAuth is enabled (auto-fires the browser flow); a "waiting for browser sign-in" variant of the dialog replaces field-hiding. Collapsible expander deferred — revisit when `localAccountsEnabled=false` configurations land.
6. ✅ **Client: logout flow.** Shipped 2026-05-12 — POST `/api/auth/logout`, browser tab to discovery's `logoutUrl`, `prompt=login` on next OAuth flow, in-JVM relaunch via `invokeLater(start(null))`. 2026-05-13: discovery `logoutUrl` flipped from `/logout` to `/connect/logout` (OIDC RP-initiated). `/connect/logout` success handler wraps `RememberMeServices` as a `CompositeLogoutHandler` so the cookie + persistent token are cleared in the same shot — fixes "signout → silently re-authed via remember-me cookie" bug.
7. ⏸ **Documentation.** `docs/authentication.md` updated for Phase 1; Phase 2 UX additions still to write up (auto-OAuth, waiting dialog, remember-me).
8. ⏸ **Persistent `OidcSessionRegistry`.** Spring SAS's default registry is in-memory — every server restart invalidates the `sid` claim of all outstanding id_tokens, causing `/connect/logout` to 404 (rejects id_token_hint). Workaround today: user re-logs in to get a fresh id_token. Proper fix: implement a `RaplaOidcSessionRegistry` over `facade.getSystemPreferences()` (same pattern as `RaplaTokenRepository`); register via `OidcLogoutEndpointConfigurer`. Non-urgent — restarts are rare in production; one-time 404 + re-login is tolerable in dev.

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

6. ✅ **Persistent vs. signed remember-me?** *Resolved 2026-05-12.* Chose `PersistentTokenBasedRememberMeServices` because Rapla doesn't expose encoded user passwords (auth goes through opaque `operator.authenticate`), making the `TokenBased` variant's password-derived hash infeasible. Backing implementation: `RaplaTokenRepository` (system-preferences-backed, single JSON blob keyed by series). Survives server restart. Per-user-preferences variant deferred until "signed-in devices" UX is actually needed — the system-wide blob is simpler and adequate at expected scale.
7. **Browser cookie scope.** Spring's `/rapla/login` cookie is scoped
   to `localhost:8051` in dev, the deployment domain in prod. Same
   browser already has session cookies for that domain — does
   remember-me actually buy us anything beyond what the browser does
   automatically? Probably yes: session cookies clear when the
   browser closes; remember-me persists across browser restarts.
8. ✅ **Should "auto-start OAuth flow" be configurable?** *Resolved
   2026-05-18 — see Phase 3 above.* Shipped as two server-side booleans
   (`rapla.oauth.swing-legacy-login`, `rapla.oauth.swing-legacy-show-sso-button`)
   delivered through the discovery endpoint, rather than the single
   `rapla.oauth.auto-start` originally sketched here — the two-flag
   shape lets an admin keep the legacy dialog *and* optionally surface
   the SSO button on it for opt-in user testing. Both default `false`
   (Phase-2 OAuth-first behaviour unchanged).
9. **What about Swing client restarts in offline mode?** If the
   server is unreachable, discovery fails. The fallback must give the
   user a way to retry. Today's "Exit" button is fine; consider also
   a "Retry connection" button on the failure screen.

10. ✅ **Refresh-token storage backend?** *Resolved 2026-05-12.* Shipped hybrid `TokenStore`: JNLP `PersistenceService` → `~/.rapla/tokens.json` (0600) → NoOp fallback. Plain bytes, no app-level encryption (matches AWS CLI / GitHub CLI convention). Constructor-time lookup chain in `TokenStores`. Original analysis below kept for context.

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

## Phase 3 — Admin-selectable legacy Swing login

**Status:** in-progress (2026-05-18).

Phase 2 made the browser-OAuth flow the *only* Swing login UX when
discovery reports `enabled: true` — the username/password dialog never
shows. This is the right default, but two deployment situations need the
old dialog back:

1. **Rollout / pilot.** An admin wants to keep end-users on the familiar
   password dialog while OAuth is being validated, then flip the whole
   estate over once confident.
2. **Letting users opt into testing SSO.** While still defaulting to the
   password dialog, the admin wants a *"Sign in with browser…"* button
   on that dialog so willing users can try the new flow without it being
   forced on everyone.

This resolves Open Question 8 (below) with two server-side booleans,
delivered to the Swing client through the existing
`GET /api/auth/oauth/config` discovery endpoint (no new endpoint, no JNLP
change — the client already probes discovery at startup):

| Property | Env var | Default | Effect |
|---|---|---|---|
| `rapla.oauth.swing-legacy-login` | `RAPLA_OAUTH_SWING_LEGACY_LOGIN` | `false` | `false` → Phase-2 behaviour: auto-fire the browser OAuth flow, no dialog. `true` → show the legacy username/password dialog instead. |
| `rapla.oauth.swing-legacy-show-sso-button` | `RAPLA_OAUTH_SWING_LEGACY_SHOW_SSO_BUTTON` | `false` | Only effective when `swing-legacy-login=true`. `true` → also render the "Sign in with browser…" button on the legacy dialog so users can try SSO. `false` → password fields only. |

Both default `false`, so an unconfigured deployment keeps today's
OAuth-first behaviour exactly. The flags are independent of
`rapla.oauth.enabled`: when OAuth is disabled entirely, the legacy dialog
shows with no SSO button regardless of these flags (an SSO button would
have nothing to talk to). When discovery is unreachable, the client falls
back to the legacy dialog with no SSO button — discovery failure means
OAuth support can't be confirmed.

Decision matrix the Swing client applies in `startLoginInThread`:

| `enabled` | `swing-legacy-login` | `swing-legacy-show-sso-button` | Swing UX |
|---|---|---|---|
| `true` | `false` | (ignored) | Auto-fire browser OAuth (Phase-2 default) |
| `true` | `true` | `false` | Legacy password dialog, no SSO button |
| `true` | `true` | `true` | Legacy password dialog **+** SSO button |
| `false` | (ignored) | (ignored) | Legacy password dialog, no SSO button |
| discovery fails | — | — | Legacy password dialog, no SSO button |

### Scope (Phase 3)

- **Server**: two `@Value` props on `OAuthConfigController`; two boolean
  fields (`swingLegacyLogin`, `swingLegacyShowSsoButton`) on the
  `OAuthConfig` discovery DTO; `application.yml` documentation. Both
  fields emitted on the wire even when `enabled: false` (as `false`).
- **Client**: `OAuthConfig` (rapla-client) gains the two flags;
  `fetchOauthConfig` parses them; `startLoginInThread` applies the
  decision matrix above. The legacy dialog's SSO button is hidden via
  the existing `LoginDialog.setOauthAction(null)`.
- **Abort button on the browser-login wait.** While the browser OAuth
  flow runs, the dialog's "waiting for browser sign-in" state previously
  offered only **Exit** (which quits the whole app). Added an **Abort**
  button (`LoginDialog.setAbortAction`, i18n key `abort`) that cancels
  the `SwingOAuthLoginFlow` session future — `CancellationException`
  propagates, the loopback listener is stopped, and the existing
  exceptionally branch in `runOauthLogin` restores the credential
  dialog. `runOauthLogin` now drives the wait via
  `setBrowserLoginInProgress` (Exit + Abort row) instead of the
  glass-pane `busy()` overlay, which would have covered the new button.
  Applies to both the auto-OAuth (Phase 2) and SSO-button (Phase 3)
  paths.
- **Tests**: tier-3 MockMvc — discovery emits both fields `false` by
  default (`OAuthConfigControllerTest`), and both `true` when the
  properties are set (`OAuthConfigControllerSwingLegacyLoginTest`).

### Out of scope (Phase 3)

- Unit-testing the EDT-bound `startLoginInThread` decision branch — no
  test harness exists for it (Phase 2's planned `AutoStartOauthFlowTest`
  was never written); covered by manual smoke under `test-jnlp-launch`.
- Per-user / per-group selection of the login mode — this is a
  deployment-wide toggle.

## Phase 4 — Sign-in method dropdown (Swing + Keycloak)

**Status:** in-progress (2026-05-18).

Phase 3 gave the legacy dialog a single "Sign in with browser…" button for
the rapla SAS. Phase 4 replaces that button with a **sign-in method
dropdown** so the Swing client can offer multiple providers — starting
with **Keycloak** alongside the rapla SAS and the local password.

### UX

- A **method dropdown** sits above the username/password fields. Entry 0
  is **Password** (local `grant_type=password`, typed credentials); the
  remaining entries are the browser-based OAuth providers from discovery's
  `providers[]` (rapla SAS, Keycloak, …), labelled by capitalised provider
  id (`Rapla`, `Keycloak`).
- Picking a browser provider **greys out the username/password fields**
  (they don't apply); the **Login** button runs that provider's PKCE
  browser flow. Picking Password re-enables them.
- The dropdown only appears when `swing-legacy-login=true` **and**
  `swing-legacy-show-sso-button=true` — it supersedes the Phase-3 SSO
  button. Without the SSO flag, or when OAuth is unavailable / discovery
  fails, only the plain password form shows (no dropdown).

### Scope (Phase 4) — Keycloak only

- **Client only.** The server already emits a per-provider `providers[]`
  array (PRD 036), the resource server already validates external-IdP
  JWTs by `iss` (`IssuerAwareJwtDecoder`), and the Keycloak realm's
  `rapla-app` client already lists the Swing loopback redirect URI
  (`http://127.0.0.1/login/oauth2/code/rapla`) — so no server or realm
  change was needed.
- `OAuthConfig` (rapla-client) gained `id` / `displayName` / `providers`
  (each provider is itself an `OAuthConfig`). `fetchOauthConfig` parses
  the `providers[]` array.
- `LoginDialog`: the standalone OAuth button (`oauthBtn` /
  `setOauthAction`) is removed; replaced by a `JComboBox`
  (`setLoginMethods` / `getSelectedMethodIndex` / `setMethodChangeListener`
  / `setCredentialsEnabled`).
- `runOauthLogin` is now provider-aware — takes the chosen provider's
  `OAuthConfig` and feeds it straight to `SwingOAuthLoginFlow` (which was
  already endpoint-driven, so it needed no change).
- **Confidential Keycloak client via the BFF.** The Keycloak `rapla-app`
  client can run confidential (secret held server-side). When
  `rapla.oauth.external.keycloak.client-secret` is set, `OAuthConfigController`
  routes its `tokenUrl` through the BFF (`/api/auth/oauth/exchange/keycloak`),
  which injects the secret — the Swing client never sees it. The token leg
  needed no `SwingOAuthLoginFlow` change: it POSTs the standard
  `authorization_code` form body to whatever `tokenUrl` discovery gives, and
  the BFF already accepts that shape.
- **Provider-aware refresh.** `RemoteConnectionInfo` gained `refreshUrl` +
  `oauthClientId`; `runOauthLogin` stores the chosen provider's token endpoint
  + client_id; `MyCustomConnector.refreshUsingToken` refreshes against them
  (the BFF, for Keycloak), falling back to rapla SAS `/oauth2/token` +
  `rapla-client` for password / rapla-SAS sessions. Closes the pre-existing
  gap where every external-IdP Swing session silently re-logged-in on
  access-token expiry.
- **Login dialog remembers language + method.** The `TokenStore` (file /
  JNLP-`PersistenceService`) was extended from a single-token store to a
  flat key/value document — refresh token plus `language` and `loginMethod`
  preferences. On a successful login `RaplaClientServiceImpl` persists the
  selected language + sign-in method; on next launch the dialog renders in
  that language and pre-selects that method in the dropdown. `tryClear()`
  (logout) drops only the token — the preferences survive so the dialog
  still defaults well after a logout.

### Why Keycloak first

Keycloak's `providers[]` discovery entry is **directly usable by Swing**:
it's a public PKCE client, no `client_secret`, so `tokenUrl` points at
Keycloak's real token endpoint (no BFF), and loopback redirects just need
a realm redirect-URI entry. Microsoft and Google can't reuse their SPA
discovery entries — Entra's registration is SPA-platform (rejects desktop
loopback) and Google's routes through the BFF. Bringing them to Swing
needs separate **native/desktop** OAuth client registrations at each IdP;
deferred until asked. This is the part PRD 036 deferred ("Swing always
uses the embedded SAS") — Phase 4 reopens it for Keycloak only.

### Out of scope (Phase 4)

- Microsoft / Google in the Swing dropdown — needs native-app client
  registrations at Entra/Google (IdP-side config) + discovery changes to
  carry native-client values.
- Automated tests for the dropdown — Swing/EDT UI, no headless harness
  (see Phase 3 out-of-scope). Verified by compile + live smoke test.

### Deadlock fix found during Phase 4 testing (2026-05-18)

Phase 4 testing surfaced a hang — the Swing client froze on "loading
data" after login. Root cause is a **pre-existing lock-order inversion
in `RemoteOperator`, unrelated to OAuth**: it held its intrinsic
`synchronized` monitor while calling `fireStorageUpdated`, whose
listeners re-enter Spring bean creation. A concurrent GUI-bean
constructor holding the Spring singleton lock and calling back into a
`synchronized` `RemoteOperator` method (`isRestartPossible` from
`RaplaMenuBar.<init>`) deadlocks against it.

It is a timing race, most easily triggered by **switching the UI
language at login** — that persists `org.rapla.language` to the user's
preferences, a client-side store whose `refresh` continuation fires the
storage-update event concurrently with `Application.start`. (Not
Keycloak-specific; not the multi-pod refresh poll.)

Fix: `RemoteOperator.refresh(UpdateEvent)` / `refreshAll()` compute the
`UpdateResult` under `synchronized (this)` and call `fireStorageUpdated`
*outside* the monitor, ordered by a dedicated `fireLock`; the no-arg
`refresh()` is de-`synchronized`. Full write-up of the rule —
"never fire listener events under a lock" — added to
[`docs/architecture/locking.md`](../architecture/locking.md). No unit
test: `RemoteOperator` is not unit-instantiable without a connected
server; verified by reproducing the exact repro (login + language
switch) live. `rapla-core` test suite (512 tests) stays green.

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
