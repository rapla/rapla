# PRD 029: Swing Login via OAuth 2.0 (Browser-based, PKCE Loopback)

**Status:** in-progress — phase 1 done (2026-05-12): discovery endpoint, single `rapla-client` RegisteredClient covering both Swing and (planned) Angular, `SwingOAuthLoginFlow`, `ConnectInfo` token path, UI button wired end-to-end, paste-URL fallback dialog for WSL2 NAT mode and other restricted-network cases. Phase 2 (refresh-token silent reauth, external IdP support, optional username/password hide) deferred.
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

3. **Token expiry mid-session.** Access tokens are short-lived (1h
   default in Spring Auth Server). Today the Swing client assumes the
   credential is valid for the whole session. Options: (a) silent
   refresh via refresh token, (b) re-pop the OAuth flow on first 401,
   (c) hard logout on expiry. Phase 2 — out of scope here.

4. **External IdP allow-list.** When a deployment configures an external
   IdP (Phase 2), does rapla need to validate that the issuer's
   public key matches a pinned set, or is "configured in
   `application.yml`" trust enough? Likely the latter — same trust model
   as any other Spring Security `jwk-set-uri`.

5. **Hide the username/password path entirely?** Some deployments may
   want OAuth-only (no rapla-local accounts). Add a server flag
   `rapla.auth.password-login.enabled` (default `true`) that, when
   `false`, hides the username/password fields and shows only the OAuth
   button. Stretch goal for Phase 2; out of scope here.
