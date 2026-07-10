# PRD 029: Swing Login via OAuth 2.0 (Browser-based, PKCE Loopback)

> **Update 2026-06-24 — legacy HMAC token path removed.** `TokenHandler`,
> `SignedToken`/`ValidToken`, and `RemoteSessionImpl` were deleted; auth is now
> JWT-only (`SpringSecurityRemoteSession` + `JwtUserResolver`, Bearer header or
> `access_token` cookie — no HMAC header/cookie/query-param fallback). Mentions of
> `AuthController`/`TokenHandler`/`RemoteSessionImpl` below are historical.

**Status:** in-progress — phase 1 done (2026-05-12), phase 2 mostly landed (2026-05-12). Phase 1 shipped discovery endpoint, single `rapla-client` RegisteredClient (Swing + planned Angular), `SwingOAuthLoginFlow`, `ConnectInfo` token path, UI button, custom redirect URI validator (WSL-bridge 172.16.0.0/12 + same-origin allowances), token unification (`/auth/login` + `/oauth2/token` both sign via single RSA JWKSource), persistent JWK via `RaplaKeyStorage` (tokens survive server restart).

**2026-05-24 — paste-URL fallback removed.** Paste-callback dialog, `rapla.oauth.show-paste-fallback` toggle, `OAuthCallbackPasteDialog`, `SwingOAuthLoginFlow.Session.deliverPasted(...)` and German/English `login.oauth.paste.*` keys all removed: WSL-bridge IP path is the real fix when loopback isn't reachable. Discovery JSON no longer carries `showPasteFallback`.

Phase 2 shipped 2026-05-12:
- **Auto-OAuth on launch** — `startLogin` probes discovery; if `enabled==true`, fires `runOauthLogin` directly. Dialog only opens when discovery unreachable or `enabled==false`.
- **Refresh-token bootstrapping** — `MyCustomConnector.refreshUsingToken` reads `connectionInfo.refreshUrl`, POSTs to `/api/auth/refresh`, falls through to password-reauth on failure.
- **Hybrid `TokenStore`** — JNLP `PersistenceService` → `~/.rapla/tokens.json` (0600) → NoOp; all ops `catch(Throwable)`.
- **In-JVM relaunch on logout** — POST `/api/auth/logout` (Bearer), browser tab to `logoutUrl`, clear `TokenStore`, `SwingUtilities.invokeLater(start(null))`.
- **"Waiting for browser sign-in" mode** — `LoginDialog.setBrowserLoginInProgress(msg)` hides fields/buttons, keeps Exit.
- **`prompt=login` race-defeater** — appended to authorize URL after logout so Spring SAS re-prompts even if browser races ahead of session clear.
- **Server-side single-token-per-user**, rotate-when-stale (7-day renewal of 30-day TTL); `AuthController` stores `sha256(refreshToken)` under `user.preferences["org.rapla.auth.session"]`. Bounds DB writes to ~1/user/week.
- **REST path migration to `/api/`** — context-path dropped; discovery's `refreshUrl` → `/api/auth/refresh`; client `serverURL` is `baseUrl + "api"`.
- **"Remember me" checkbox** on `LoginPageController` HTML (browser only; Swing uses `TokenStore`).
- **`raplaUserDetailsService` bean** adapting `RaplaFacade` — UUID-first, username fallback. Placeholder password (`PersistentTokenBased…` doesn't use it).
- **`PersistentTokenBasedRememberMeServices` + `RaplaTokenRepository`** — `{series → {username, tokenValue, lastUsed}}` as JSON under system preferences; cookies survive server restart.

Landed 2026-05-13 (signout bug fixes):
- **`/connect/logout` now clears the remember-me cookie.** Spring SAS's `OidcLogoutAuthenticationSuccessHandler` only clears HttpSession + SecurityContext by default, so the surviving `rapla-remember-me` cookie silently re-authed the next `/oauth2/authorize` ("sign out → instantly signed back in"). Fix: promote `RememberMeServices` to `@Bean`; `authorizationServerSecurityFilterChain` attaches a custom success handler whose `logoutHandler = CompositeLogoutHandler(SecurityContextLogoutHandler, rememberMeServices)`. Verified live: `Set-Cookie: rapla-remember-me=; Max-Age=0` and the persistent row is removed.
- **`logoutUrl` in discovery flipped from `/logout` to `/connect/logout`** — both clients now use OIDC RP-initiated logout; the prior split is collapsed.
- **Swing plumbs `id_token` through to logout.** OIDC RP-initiated logout requires `id_token_hint`; without it Spring SAS returns 404 and the cookie-clearing handler never runs. Fix: `OAuthTokens` + `RemoteConnectionInfo` gained `idToken`; `SwingOAuthLoginFlow.exchangeCodeForTokens` parses it (scope=openid already enabled); `logout()` appends `?id_token_hint=<urlencoded>` before opening the browser tab. **Superseded by [PRD 072](072-server-side-login-dialog.md) ("M2 broker model").** `RaplaClientServiceImpl.logout()` no longer appends `?id_token_hint=<...>` nor opens a `/connect/logout` browser tab — logout is now a best-effort `POST /oauth2/revoke` plus `prompt=login` on the next OAuth flow. The captured `idToken` is now unused on the logout path.

Setup doc: `docs/authentication.md`. PRD 031 covers refresh-token mechanics; [PRD 032](done/032-angular-ui-library-evaluation.md) handles external IdP.

**Known gotcha — server-restart invalidates id_token `sid` claims.** Spring SAS's `OidcSessionRegistry` is in-memory only. After restart the registry is empty, so pre-restart id_tokens have a `sid` no longer tracked; `/connect/logout` returns 404. Workaround: re-login. Proper fix: persistent `OidcSessionRegistry` over `RaplaFacade` system preferences (same pattern as `RaplaKeyStorage` / `RaplaTokenRepository`). Deferred — non-urgent in dev.

**Date:** 2026-05-12 (last updated 2026-05-13)

## Goal

Add a second sign-in path to `LoginDialog`: a **"Sign in with browser…"** button that runs RFC 8252 Authorization Code + PKCE against the rapla-app Spring Authorization Server, captures the access token via a localhost loopback redirect, and uses it as `Authorization: Bearer …` against rapla REST (same surface `AuthController`/`TokenHandler` already understand). The username/password form stays — this is additive. Motivation: (1) SSO — delegate to whatever IdP the deployment configures (embedded SAS by default; Keycloak/Azure AD/Google in custom deployments); (2) no shared-secret on the wire/disk — PKCE is designed for public clients (JNLP-shipped Swing is one); rapla holds only a short-lived access token in memory.

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
- An OAuth-aware Web/Angular front-end ([PRD 026](026-angular-frontend.md)). Different surface;
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

All needed perms (`SocketPermission` for loopback bind, `RuntimePermission("exec")` for `ProcessBuilder`, `AWTPermission("showWindowWithoutWarningBanner")` for `Desktop.browse`) covered by `<all-permissions/>`. Signed jars need `Permissions: all-permissions` in manifest (see `project_jnlp_signing_pitfalls.md`); existing webclient/ assembly already sets them. No JNLP schema change.

## Plan

1. **Server: discovery + registered client.** `OAuthConfigController` + tier-3 MockMvc test (enabled/disabled). `rapla-swing` `RegisteredClient` in `application.yml`. Verify `AuthController`/`TokenHandler` accept the SAS-minted token via one tier-3 test.

2. **Client: SwingOAuthLoginFlow (no UI yet).** Flow + browser-launch helper + WSL detection. Tier-1/2 tests drive the loopback with `HttpClient` (no real browser): extract code+state, reject state-mismatch, exchange with correct PKCE verifier.

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

Inverts Phase 1: browser flow becomes the **primary login UX**; username/password is a fallback only when (a) no external IdP configured *and* (b) browser flow can't reach the auth server.

### Vision

- **Startup**: client probes discovery, fires OAuth flow if enabled. Login dialog only shows when OAuth can't proceed.
- **Browser-side "remember me"** on rapla's `/rapla/login` (and Keycloak's, when IdP) keeps the SSO cookie. Subsequent launches → silent redirect → token → main view. **User never sees a login dialog.**
- **Mid-session 401**: when access-token + refresh-token reauth both fail, re-run OAuth flow. Silent with SSO cookie; one round-trip via browser without.
- **Logout / restart**: clears tokens + refresh-URL cache; next action triggers OAuth flow afresh.
- **Username/password form hidden by default**. Surfaces only when "no external IdP AND OAuth can't start" — emergency-only fallback (auth server unreachable, browser failed, first-time setup). Reached via "Other options" expander. Hidden unconditionally under external IdP (no local user store).

### Why this is the right direction

Matches modern desktop OAuth UX (Slack/Zoom/GitHub Desktop/JetBrains/Teams all bounce to system browser). Forces all auth through one funnel — auth server handles password validation, MFA, lockouts, audit, reset; Swing never sees a password. Plays nicely with external IdPs (Keycloak expects users on its login page; today's dialog undermines that). Closes the Phase 1 regression where `MyCustomConnector.reauth` had to special-case "OAuth session has no password to re-submit" — Phase 2 makes mid-session reauth re-run OAuth, symmetric with initial login.

### Scope

#### In scope

- **`/rapla/login` "remember me"** — Spring Security `RememberMeServices` (persistent token, default 30 d), checkbox in HTML form, TTL via `rapla.auth.remember-me-days`.
- **Auto-start OAuth flow on launch** — `startLogin` skips dialog and runs `runOauthLogin()` when discovery `enabled`; dialog only on discovery failure.
- **OAuth retry on 401 after refresh fails** — for OAuth-only sessions (no cached password), trigger a new OAuth flow via callback registered by `RaplaClientServiceImpl`. Browser opens ~1s (silent if cookie present).
- **Hide password fields by default** — `LoginDialog` shows only "Other login options" link that expands fields. Modeled on Slack's "Try another way" / JetBrains' license-server login depth.
- **Hide password path entirely under external IdP** — discovery's `localAccountsEnabled=false` (typical Keycloak) hides fields even on fallback.
- **Logout / restart returns to clean OAuth start** — also clear `refreshUrl`, persisted refresh token, server-side remember-me cookie via `RememberMeServices.logout()`.
- **Persist the refresh token client-side** — store per-user in rapla profile dir (or OS keychain — OQ 10). On launch, read it, hit `refreshUrl` directly — zero browser tab. The "zero-browser-on-launch" property; only the refresh token's ~30-day idle expiry forces a browser. Three-layer fallback: cached refresh → cookie-based silent OAuth → form login.

- **Token store is best-effort, never blocks login**. Every backend (JNLP `PersistenceService`, dotfile, future keychain) can fail (read-only fs, sandbox, corruption, missing library, disk full). All `TokenStore` ops wrap `catch (Throwable)` — also swallows `LinkageError` / `NoClassDefFoundError` from optional deps. Degradation: **read failure** → "no token cached" → cookie-based silent OAuth → form login; **write failure** → log warning, session continues; **delete failure on logout** → log warning, complete logout (stale token will fail validation on next use). Failure of "skip the login prompt" should always degrade to "show the login prompt", never error out the launch.

#### Out of scope (Phase 2)

- External IdP wiring itself ([PRD 032](done/032-angular-ui-library-evaluation.md)) — Phase 2 is client UX shift.
- Federated identity reconciliation (Keycloak `sub` → rapla UUID) — [PRD 032](done/032-angular-ui-library-evaluation.md).
- Persistent token revocation / "log out everywhere" admin surface — future PRD.
- API key UI from PRD 031 §6 — tracked there.

### Plan

1. ✅ **Server: `/rapla/login` remember-me.** Shipped 2026-05-12 — `PersistentTokenBasedRememberMeServices` with `RaplaTokenRepository` backing (system preferences). Checkbox in `LoginPageController` HTML. TTL `rapla.auth.remember-me-days` (default 30).
2. ✅ **Server: `localAccountsEnabled` flag in discovery.** Implemented as `rapla.oauth.local-accounts-enabled` (env `RAPLA_OAUTH_LOCAL_ACCOUNTS_ENABLED`), default `true`. When `false`, the server refuses the `grant_type=password` login (no local password path) and discovery reports the flag so clients hide the credential fields.
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

10. ✅ **Refresh-token storage backend?** *Resolved 2026-05-12.* Shipped hybrid `TokenStore`: JNLP `PersistenceService` → `~/.rapla/tokens.json` (0600) → NoOp. Plain bytes, no app-level encryption (matches AWS CLI / GitHub CLI / `~/.netrc`). Refresh tokens are signed JWTs (~700 B); threat model is solved by file perms (0600) or unsolvable by app-level encryption (same-user attacker derives any system-property key). Custom AES is snake-oil. Backends ~50 LOC each; all wrap `catch (Throwable)` → silent "no cached token". Interface:
    ```java
    interface TokenStore {
        Optional<String> read();      // never throws; returns empty on any failure
        void tryWrite(String token);  // never throws; logs warning on failure
        void tryClear();              // never throws; logs warning on failure
    }
    ```
    Construction tries `JnlpTokenStore` (succeeds under OWS) → `FileTokenStore` → `NoOpTokenStore`. Worst case: "we never persist", not "app crashes". OS keychain (`java-keyring`) skipped until asked.

## Phase 3 — Admin-selectable legacy Swing login

**Status:** in-progress (2026-05-18).

Phase 2 forced OAuth-only when discovery `enabled: true`. Two deployment situations need the old dialog back: (1) **rollout/pilot** — keep users on password while OAuth is validated; (2) **opt-in SSO** — default to password but expose a "Sign in with browser…" button for willing users. Resolved with two server-side booleans on the existing discovery endpoint (no new endpoint, no JNLP change):

| Property | Env var | Default | Effect |
|---|---|---|---|
| `rapla.oauth.swing-legacy-login` | `RAPLA_OAUTH_SWING_LEGACY_LOGIN` | `false` | `false` → Phase-2 behaviour: auto-fire the browser OAuth flow, no dialog. `true` → show the legacy username/password dialog instead. |
| `rapla.oauth.swing-legacy-show-sso-button` | `RAPLA_OAUTH_SWING_LEGACY_SHOW_SSO_BUTTON` | `false` | Only effective when `swing-legacy-login=true`. `true` → also render the "Sign in with browser…" button on the legacy dialog so users can try SSO. `false` → password fields only. |

Both default `false` (preserves OAuth-first). Independent of `rapla.oauth.enabled`: when OAuth disabled or discovery unreachable, legacy dialog shows without SSO button.

Decision matrix the Swing client applies in `startLoginInThread`:

| `enabled` | `swing-legacy-login` | `swing-legacy-show-sso-button` | Swing UX |
|---|---|---|---|
| `true` | `false` | (ignored) | Auto-fire browser OAuth (Phase-2 default) |
| `true` | `true` | `false` | Legacy password dialog, no SSO button |
| `true` | `true` | `true` | Legacy password dialog **+** SSO button |
| `false` | (ignored) | (ignored) | Legacy password dialog, no SSO button |
| discovery fails | — | — | Legacy password dialog, no SSO button |

### Scope (Phase 3)

- **Server**: two `@Value` props on `OAuthConfigController`; two `boolean` fields on the `OAuthConfig` DTO (emitted even when `enabled: false`); `application.yml` docs.
- **Client**: `OAuthConfig` gains both flags; `fetchOauthConfig` parses them; `startLoginInThread` applies the matrix above; SSO button hidden via existing `setOauthAction(null)`.
- **Abort button on the browser-login wait.** Previously only Exit (kills app). Added Abort (`setAbortAction`, i18n `abort`) — cancels `SwingOAuthLoginFlow` session future, propagates `CancellationException`, stops loopback listener, exceptionally branch in `runOauthLogin` restores credential dialog. `runOauthLogin` now drives the wait via `setBrowserLoginInProgress` (Exit + Abort row) instead of glass-pane `busy()` overlay which would have covered the new button. Applies to Phase 2 + 3 paths.
- **Tests**: tier-3 MockMvc — `OAuthConfigControllerTest` (both false default), `OAuthConfigControllerSwingLegacyLoginTest` (both true when set).

### Out of scope (Phase 3)

- Unit-testing the EDT-bound `startLoginInThread` decision branch — no harness; manual smoke under `test-jnlp-launch`.
- Per-user / per-group login mode — deployment-wide toggle only.

## Phase 4 — Sign-in method dropdown (Swing + Keycloak)

**Status:** in-progress (2026-05-18).

Replaces Phase 3's single SSO button with a **sign-in method dropdown** so Swing can offer multiple providers — starting with **Keycloak** alongside rapla SAS and local password.

### UX

- **Method dropdown** above username/password. Entry 0 is **Password** (local `grant_type=password`); remaining entries are browser providers from discovery's `providers[]` (`Rapla`, `Keycloak`).
- Picking a browser provider greys out username/password; Login runs that PKCE flow. Picking Password re-enables fields.
- Appears only when `swing-legacy-login=true` AND `swing-legacy-show-sso-button=true` (supersedes Phase-3 button). Without SSO flag or with OAuth unavailable, plain password form (no dropdown).

### Scope (Phase 4) — Keycloak only

- **Client only.** The server already emits a per-provider `providers[]`
  array ([PRD 036](036-external-idp-oauth-login.md)), the resource server already validates external-IdP
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
- **Provider-aware refresh.** *(Reverted by [PRD 072](072-server-side-login-dialog.md) Phase 5 — historical.)* This
  originally routed refresh to a per-provider `refreshUrl` + `oauthClientId` on
  `RemoteConnectionInfo`. [PRD 072](072-server-side-login-dialog.md) Phase 5 made rapla the single federating
  Authorization Server, so both refresh paths now hardcode rapla's `/oauth2/token`
  with `client_id=rapla-client` (`MyCustomConnector.refreshUsingToken`,
  `ClientProxyConfig`); `refreshUrl`/`oauthClientId` are no longer read. The gap it
  closed is now moot — every Swing login yields a rapla-issuer token.
- **Login dialog remembers language + method.** The `TokenStore` (file /
  JNLP-`PersistenceService`) was extended from a single-token store to a
  flat key/value document — refresh token plus `language` and `loginMethod`
  preferences. On a successful login `RaplaClientServiceImpl` persists the
  selected language + sign-in method; on next launch the dialog renders in
  that language and pre-selects that method in the dropdown. `tryClear()`
  (logout) drops only the token — the preferences survive so the dialog
  still defaults well after a logout.

### Why Keycloak first

Keycloak's `providers[]` entry is directly Swing-usable: public PKCE client (no `client_secret`), `tokenUrl` is real Keycloak endpoint (no BFF), loopback redirects need only a realm redirect-URI entry. Microsoft/Google can't reuse SPA discovery — Entra rejects desktop loopback (SPA-platform), Google routes through BFF. Both need separate native/desktop OAuth client registrations; deferred until asked. Reopens what [PRD 036](036-external-idp-oauth-login.md) deferred ("Swing always uses embedded SAS") for Keycloak only.

### Out of scope (Phase 4)

- Microsoft / Google in the Swing dropdown — needs native-app client
  registrations at Entra/Google (IdP-side config) + discovery changes to
  carry native-client values.
- Automated tests for the dropdown — Swing/EDT UI, no headless harness
  (see Phase 3 out-of-scope). Verified by compile + live smoke test.

### Deadlock fix found during Phase 4 testing (2026-05-18)

Hang on "loading data" after login. **Pre-existing lock-order inversion in `RemoteOperator`, unrelated to OAuth**: it held its `synchronized` monitor while calling `fireStorageUpdated`, whose listeners re-enter Spring bean creation. Concurrent GUI-bean ctor holding Spring singleton lock + calling back into `synchronized` `isRestartPossible` (from `RaplaMenuBar.<init>`) deadlocks. Most easily triggered by switching UI language at login (persists `org.rapla.language` → client-side store's `refresh` continuation fires storage-update concurrently with `Application.start`). Not Keycloak-specific.

Fix: `RemoteOperator.refresh(UpdateEvent)` / `refreshAll()` compute `UpdateResult` under `synchronized (this)` then call `fireStorageUpdated` outside the monitor, ordered by a dedicated `fireLock`; no-arg `refresh()` de-`synchronized`. Rule "never fire listener events under a lock" added to [`docs/architecture/locking.md`](../architecture/locking.md). No unit test (`RemoteOperator` not unit-instantiable); verified live (login + language switch). `rapla-core` 512 tests green.

## Phase 5 — Keycloak refresh fix + credentials cleanup (2026-05-25)

**Status:** in-progress. Triggered by live-Keycloak bug: `dhbwrapla` + Keycloak Swing sessions showed `session_expired: access + refresh tokens both rejected` exactly 10 min after every login (Keycloak access-token TTL). Root cause: HTTP-interface-proxy interceptor refreshed against rapla's own `/oauth2/token` with `client_id=rapla-client`, ignoring the per-provider endpoint stashed at login by `SwingOAuthLoginFlow`. Keycloak's RSA-signed refresh JWT can't be validated by rapla SAS → 400 → auth-dead → re-login. Fix grew into a wider cleanup of the credentials lifecycle as audit turned up sibling bugs.

### Scope (Phase 5)

1. **Route provider refreshes correctly.** `ClientProxyConfig.RefreshOn401Interceptor.doRefresh()`
   now reads `RemoteConnectionInfo.refreshUrl` + `oauthClientId` (which
   `SwingOAuthLoginFlow` was already stashing per Phase 4) before
   falling back to rapla-SAS defaults. Mirrors what
   `MyCustomConnector.refreshUsingToken()` was already doing on the RPC
   tier. Regression test: `RefreshOn401InterceptorAuthDeadTest.whenProviderRefreshUrlIsSet_thenRefreshHitsThatUrlNotRaplaSas`.

   **Reverted by [PRD 072](072-server-side-login-dialog.md) Phase 5.** Per-provider refresh routing is gone:
   both refresh paths (`RefreshOn401Interceptor.doRefresh()` and
   `MyCustomConnector.refreshUsingToken()`) now hardcode rapla's own
   `/oauth2/token` with `client_id=rapla-client`; they no longer read
   `refreshUrl` / `oauthClientId`. The `whenProviderRefreshUrlIsSet...`
   test above no longer exists — the current regression test pins the
   opposite (refresh always hits rapla SAS regardless of stashed
   provider URL).

2. **Persist rotated refresh tokens.** Keycloak rotates the refresh
   token on each refresh by default. The interceptor updated
   `RemoteConnectionInfo` but never wrote the new token to `TokenStore`
   — silent reauth at next cold start would always present the stale
   pre-rotation token. Fix: inject `TokenStore` into
   `RefreshOn401Interceptor`; call `tokenStore.tryWrite(newRefresh)`
   after `info.setRefreshToken(newRefresh)`, matching
   `MyCustomConnector.refreshUsingToken()`.

3. **Fix cold-startup silent reauth wire format.** Pre-Phase-5,
   `RaplaClientServiceImpl.tryRestoreFromCachedRefreshToken()` POSTed
   `{"refreshToken":"..."}` JSON to `/api/auth/refresh` — both the
   endpoint and the wire format had been deleted by [PRD 041](041-openapi-runtime-removal.md). Result:
   every Swing cold start with a cached token 404'd and fell through to
   the login dialog, defeating the whole point of caching. Phase 5
   switches to OAuth2-standard `grant_type=refresh_token` form body
   against the persisted provider URL (`TokenStore.KEY_REFRESH_URL` +
   `KEY_OAUTH_CLIENT_ID`, written by `persistLoginPrefs`), with
   snake_case response parsing. HTTP core extracted to a static helper
   `executeSilentRefresh(tokenUrl, clientId, refreshToken)` for unit
   testability — see `RaplaClientServiceImplSilentReauthHelperTest`.

4. **Delete dead `RemoteAuthentificationService.refresh()`.** The
   interface declared a `refresh(RefreshRequest)` method nobody called
   in production (`MyCustomConnector.refreshUsingToken()` and
   `RefreshOn401Interceptor.doRefresh()` both built their own HTTP
   requests). The orphan had the same bug shape as #1 — `OAuth2RemoteAuthentificationService.refresh()`
   hardcoded rapla-SAS — so leaving it as a trap for a future caller
   was worse than removing it. Removed: interface method, impl method,
   `RefreshRequest` inner class, one orphan test.

5. **Drop password caching for reauth.** `MyCustomConnector.reauth()`
   used to fall back to re-running the password-grant login from a
   cached `ConnectInfo.password` when refresh-token reauth failed.
   That cached password lived in `RemoteConnectionInfo.connectInfo` for
   the entire session lifetime — a heap-resident `String` (then a
   `char[]` reference, both readable from a heap dump). The fallback
   was useful in exactly one corner case ("refresh token expired but
   password still valid"); every other failure mode failed both paths.
   Phase 5 removes the fallback. Both password and OAuth sessions get
   identical refresh-only reauth; refresh failure surfaces
   "session expired" → login dialog. Removed: the password-fallback
   block, `wrongLoginCounter` state, and the
   `Supplier<RemoteAuthentificationService>` constructor parameter on
   `MyCustomConnector` (no longer needed). After successful login,
   `RaplaClientServiceImpl.login()` and `RemoteOperator.connect()` both
   stash a token-only `ConnectInfo.withAccessToken(...)` and zero the
   source password `char[]` in place.

6. **`LoginCredentials.password` → `char[]`.** Even after #5, the
   transient String materialization at the URL-encode boundary
   (`new String(connectInfo.getPassword())`) was unnecessary heap
   exposure. Changed `LoginCredentials.password` field from `String`
   to `char[]`. The plaintext lives as a `String` only for the
   duration of one URL-encode call (built inside
   `OAuth2RemoteAuthentificationService.login()`, GC-eligible the
   moment the HTTP body is sent). Added `LoginCredentials.clearPassword()`
   for callers to zero the array right after `serv.login()` returns.
   Server-side bridges (`AuthorizationServerConfig`,
   `RaplaAuthentificationService.getUserFromCredentials`) convert
   String↔`char[]` at the in-server seam — the request-scoped String
   was already short-lived there.

7. **Slim `ConnectInfo` to tokens + provider routing; localize password handling; wire dual-slot impersonation correctly.**
   `ConnectInfo` historically carried `(username, password)` + `(accessToken, refreshToken)` with an `if (getAccessToken() != null)` switch at every dispatch site — making "password might be here" a live concern at every signature.

   Phase 5 drops password fields entirely; class now carries `(accessToken, refreshToken, refreshUrl, oauthClientId)` — the full session needed across a close+recreate context boundary. Password flow is *localized to the user-input boundary*: legacy Swing Login button converts password→tokens inline via `RemoteAuthentificationService.login()`, zeros the `char[]`, then only token-bearing `ConnectInfo` flows.

   ```java
   public class ConnectInfo {
       private final String accessToken;
       private final String refreshToken;
       // no username, no password, no connectAs
   }
   ```

   **Rejected:** sealed-type hierarchy (`Credentials` / `PasswordCredentials` / `TokenCredentials`) — cost (10-file migration, Java 21 bump for switch patterns) outweighed payoff for only 2 dispatch sites already structurally contained.

   Password handling end-state — three live sites only:
   - `OAuth2PasswordLogin.login(LoginCredentials)` (rapla-core) —
     the HTTP seam itself; POSTs OAuth2 `grant_type=password` to
     `/oauth2/token`. Takes a password by definition. PRD 029
     Phase 5 dropped the `RemoteAuthentificationService` interface
     it used to implement (one method, one impl, no plugin point)
     and renamed it from `OAuth2RemoteAuthentificationService`.
   - Legacy Swing dialog Login button — user-input → seam → tokens,
     all inside one lambda, `char[]` zeroed before return. Also
     dropped the `" su "` shorthand parsing — the OAuth2 password
     grant has no `connect_as` parameter; impersonation is now a
     dedicated endpoint (`/api/auth/impersonate`).
   - Server-side: `RaplaAuthentificationService.getUserFromCredentials`
     receives credentials from Spring SAS's password-grant handler
     and delegates to the `AuthenticationStore` plugin chain
     (`DhbwNtlmAuthStore`, `JNDIAuthenticationStore`). Necessary for
     deployments that delegate password verification to external
     stores. Simplified Phase 5: 3-arg `authenticate(..., connectAs)`
     became 2-arg; dropped `getUserWithPassword` (only caller was the
     legacy username/password request-param auth in `RemoteSessionImpl`
     — also dropped as confirmed dead via audit).

   Plus one unit-test caller (`OAuth2PasswordLoginTest`, rapla-core)
   that exercises the seam itself.

   `ClientFacade.login(String, char[])` was deleted (replaced by
   `ClientFacade.connect(ConnectInfo)` for the test-bootstrap path);
   the two tier-3 integration tests
   (`SwingClientStartIntegrationTest`,
   `HeadlessClientNameResolutionIntegrationTest`) now mint a JWT
   server-side via
   `RefreshSessionService.issueAndPersist(homer)` and pass it to
   `facade.connect(info)`. Zero password references in test
   fixture code.

   `RemoteOperator` lost its `RemoteAuthentificationService`
   constructor parameter — `connect(ConnectInfo)` is token-only
   after Phase 5, so the auth seam isn't needed at this layer.
   `RemoteSessionImpl` (legacy iCal/permit-all session fallback)
   lost the `RaplaAuthentificationService` constructor parameter
   for the same reason (its `?username=...&password=...`
   request-param branch was dead).

   The dispatch sites that used to switch on
   `accessToken != null` collapse to single token-only paths:
   - `RaplaClientServiceImpl.login(ConnectInfo)` — stashes tokens,
     persists refresh to `TokenStore`. No password branch.
   - `RemoteOperator.connect(ConnectInfo)` — stashes tokens on
     `connectionInfo`, no auth seam call. No password branch.

   **CLI bootstrap change**: `SpringRaplaClient.parseConnectInfo(args)`
   used to accept `args=[username, password]`; now accepts a single
   API token JWT (`args=[jwt]`). The bootstrap workflow is to mint an
   API key once via `POST /api/auth/api-keys` ([PRD 043](043-api-keys-jwt-pat.md), via Scalar UI
   at `/scalar`), copy the returned JWT, and use
   `-Dexec.args="$RAPLA_DEV_TOKEN"`. No password handling in the
   launcher.

   Touchpoints (~20 files): `ConnectInfo` (slim 4-tuple), `LoginCredentials` (`password: char[]`, dropped `connectAs`), `RemoteAuthentificationService` (interface deleted), `OAuth2PasswordLogin` (new top-level in rapla-core, replaces nested impl + deleted interface), `RemoteConnectionInfo` (harmonization renames + Angular cross-reference Javadoc), `RemoteOperator.connect()` (token-only, drop auth-seam ctor param), `RemoteSessionImpl` (drop dead `?username=...&password=...` request-param branch + ctor param), `RaplaAuthentificationService` (3-arg → 2-arg `authenticate`, drop `getUserWithPassword` + `checkConnectAsRights`), `ClientFacade` / `ClientFacadeImpl` (`login(String, char[])` → `connect(ConnectInfo)`), `RaplaClientServiceImpl.startLoginInThread` (inline password→tokens; drop `" su "` parsing), `login(ConnectInfo)` (token-only + 4-tuple), `switchTo()` (admin 4-tuple + impersonation token via `NextSession`), `setImpersonation()` (new), `finishOauthLogin` + `tryRestoreFromCachedRefreshToken` (build 4-tuple), `SpringRaplaClient.parseConnectInfo()` (CLI takes JWT only), `.main()` (apply impersonation post-start), `stop()` (drop placeholder password), `NextSession` (carry impersonation separately), `ClientService.setImpersonation` (new default), `ClientConfig` / `ClientProxyConfig` / `ServerServiceConfig` (drop dead ctor params), caller renames: `hasImpersonationToken` → `isImpersonating`, `setImpersonationToken` → `setImpersonationAccessToken`, `getAccessToken` → `adminToken` at impersonation-renewal call site (`MyCustomConnector` / `ClientProxyConfig` / `ApplicationViewSwing`), `AuthorizationServerConfig` / `RaplaAuthentificationService` (bridge String↔`char[]` at request-scope boundary). Tests: `LogoutSignalTest` (switchTo signature), `SwingClientStartIntegrationTest` / `HeadlessClientNameResolutionIntegrationTest` (mint via `RefreshSessionService.issueAndPersist` + `facade.connect`), `BadLoginErrorMessageTest` (use `OAuth2PasswordLogin` directly, expect `RaplaSecurityException` carrying server body), `OAuth2PasswordLoginTest` (renamed + moved to rapla-core).

   **Dual-slot impersonation correctness**: pre-Phase-5, [PRD 052](052-client-clean-restart.md) Phase 2's close+recreate context model put the impersonation token in the regular `accessToken` slot of the new context, leaving `impersonationAccessToken` unused. `tryRenewImpersonation()` gated on `isImpersonating()` → always false → renewal never fired → admin kicked back to login dialog after 1h. Worse, admin's Keycloak refresh URL wasn't carried across the context boundary, so switch-back-after-expiry also failed. Phase 5 carries admin's full 4-tuple via `NextSession` and applies the impersonation token to its dedicated slot post-start. Mirrors the Angular SPA's two-slot model (`oauth.getAccessToken()` admin in `localStorage` + `AuthService.impersonationOverride` in `sessionStorage`).

   **Harmonization renames for cross-client parity**: `impersonationToken` → `impersonationAccessToken` (matches Angular `override.accessToken`), `hasImpersonationToken()` → `isImpersonating()`, added `adminToken()` alias for `getAccessToken()`. `RemoteConnectionInfo` Javadoc cross-references the Angular file.

### Out of scope (Phase 5)

- Server-side `LoginCredentials.username` cleanup. Server already
  treats the password as request-scoped; the threat model is different.
- Backwards-compat shim for `org.rapla.ConnectInfo`. Internal API;
  external rapla-client consumers (if any) update at the same time.
- Wider sealed-types refactor (e.g. `LoginTokens`, `RemoteConnectionInfo`).
  Same shape but each is its own value proposition; deferred.

### Tests (Phase 5)

| Test class | Locks in |
|---|---|
| `RefreshOn401InterceptorAuthDeadTest` (5 cases incl. 3 new) | Provider URL routing + rotated-token persistence + auth-dead hook |
| `MyCustomConnectorReauthTest` (4 new) | Refresh-only reauth contract; session-expired with no password fallback |
| `RaplaClientServiceImplSilentReauthHelperTest` (5 new) | Cold-startup OAuth2 wire format + saved provider URL + snake_case parsing |
| `OAuth2PasswordLoginTest` (renamed from `OAuth2RemoteAuthentificationServiceTest`, moved to rapla-core, 2 cases) | Password-grant body shape + server body preserved in `RaplaSecurityException` |

Verified live: dhbw-Keycloak refresh round-trips through the BFF and
returns `200 {access_token, refresh_token (rotated), ...}` 62 seconds
after the access token expires. No `session_expired` log line on the
in-app path. (See `logs/rapla-client.log`, 18:42:41 entry, 2026-05-25.)

### Open Questions (Phase 5)

1. **`RemoteAuthentificationService` interface — deleted.** Originally
   a deferred OQ; resolved in the same pass. One method, one impl, no
   plugin point → dropped the interface, promoted the impl to a
   top-level concrete class `OAuth2PasswordLogin` in rapla-core. The
   `RemoteOperator` auth-seam constructor param + the legacy
   `RemoteSessionImpl` username/password request-param branch were
   both dead and dropped at the same time (separate audit confirmed
   nothing sends `?username=...&password=...` for auth).

2. **`ConnectInfo.toString()` password masking.** Resolved as part of
   Phase 5's #7: slim `ConnectInfo` has no password field, so the
   `toString()` cannot leak one. The legacy `Arrays.toString(password)`
   branch is gone.

3. **Hardening `LoginCredentials` further: byte[] all the way to
   socket write.** Java's `URLEncoder` has no `char[]`-native API,
   so the plaintext briefly becomes a `String` during form-encoding.
   A full hardening would reimplement URL-encoding to emit `byte[]`
   directly and clear it after socket write. Marginal benefit given
   the existing transient is sub-second and the cleartext was already
   transmitted over the wire one HTTP-encode step ago. Not pursuing.

4. **Dual-slot impersonation across context restart — resolved.**
   Originally a gap: [PRD 052](052-client-clean-restart.md) Phase 2's close+recreate context model
   put the impersonation token in the regular `accessToken` slot,
   leaving `impersonationAccessToken` unused → `tryRenewImpersonation()`
   never fired → kicked back to login dialog after 1h. Resolved by
   Phase 5 §7: admin's full 4-tuple session is carried via
   `NextSession` and applied to the new context as primary; the
   impersonation token is applied to the dedicated slot via
   `ClientService.setImpersonation(...)` after start. Mirrors the
   Angular SPA's two-slot model.

5. **Switch-back after admin's access token expired — resolved.**
   Originally a gap: if admin switched to user X for >10 min on a
   Keycloak deployment, admin's access token expired during
   impersonation; switch-back restored admin's tokens but lost the
   provider routing (refreshUrl + oauthClientId) → first REST call
   401 → interceptor's `doRefresh()` fell back to rapla-SAS →
   Keycloak refresh token rejected → re-login dialog. Resolved by
   carrying provider routing in the slimmed `ConnectInfo` 4-tuple.

## Open Questions

1. **Multiple IdPs.** Single IdP per deployment is the default; if a choice is needed, discovery returns a list and the button becomes a dropdown (now implemented in Phase 4).
2. **OIDC vs OAuth.** Skip OIDC for now; the existing `getUser` REST call carries profile info. Single scope `rapla`.
3. ✅ **Token expiry mid-session.** Addressed via PRD 031 refresh + Phase 2's "OAuth retry on 401 when refresh fails" (silent SSO-cookie re-auth).
4. **External IdP allow-list.** "Configured in `application.yml`" is trust enough — same model as any Spring Security `jwk-set-uri`.
5. ✅ **Hide the username/password path entirely?** Now in scope as Phase 2: discovery `localAccountsEnabled` hides the form; even with locals enabled it lives behind an "Other options" expander.
