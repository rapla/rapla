---
name: oauth-flow
description: Use when working on rapla's authentication — the OAuth 2.0 flows against Spring Authorization Server (`/oauth2/token` grants: authorization_code + PKCE, password, refresh_token), refresh-token mechanics, remember-me cookies, JWT signing/verification, or the discovery endpoint at `/api/auth/oauth/config`. Covers both client surfaces (Swing via `SwingOAuthLoginFlow`, Angular via `angular-oauth2-oidc`). Skip for purely-server endpoints that don't deal with auth (queryAppointments, storage/resources, etc.) — those need the `api-testing` skill instead.
---

# Auth in rapla — the full picture

All authentication goes through Spring Authorization Server's `/oauth2/token` endpoint — three grants: `authorization_code` + PKCE (interactive browser login), `password` (direct username/password), and `refresh_token`. All produce structurally identical RSA-signed JWTs so REST endpoints don't have to know which grant the caller used. PRDs 029 (Swing OAuth), 031 (token refresh) and 041 (OAuth-only consolidation) carry the design history; this skill captures what an agent needs to *use* the system.

## URL surface (post PRD 031 namespace redesign)

| Path | Purpose | Auth required |
|---|---|---|
| `/oauth2/authorize` | RFC 6749 — start of the Authorization Code flow. PKCE required. | session cookie or form-login |
| `/oauth2/token` `grant_type=authorization_code` | Code → token exchange (interactive login). | PKCE verifier |
| `/oauth2/token` `grant_type=password` | Direct username/password login. Form-encoded `username` / `password` / `client_id=rapla-client` → `{access_token, refresh_token, expires_in, token_type}` (snake_case). | none |
| `/oauth2/token` `grant_type=refresh_token` | Mint a fresh access token from a refresh token. | refresh JWT in body |
| `/oauth2/revoke` | Revoke a token; clears the user's `org.rapla.auth.session` preference (all that user's sessions). | the token being revoked |
| `/api/auth/oauth/config` | Discovery — emits `enabled`, `clientId`, `authorizeUrl`, `tokenUrl`, `logoutUrl`, etc. Lets clients avoid hardcoding paths. | none |
| `/.well-known/openid-configuration` | Standard OIDC discovery (Spring AS exposes it). | none |
| `/login` | HTML form login page rendered by `LoginPageController` (form login + the OAuth browser bounce). | none |

> The rapla-custom `/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout`
> endpoints were removed in PRD 041 — all token issuance, refresh and
> revocation is now on the `/oauth2/*` endpoints above.

## Token system

- **RSA-signed JWTs** (no HMAC). Signing key read from `RaplaKeyStorage` (system preferences) at startup — persisted to the data file, **survives server restart**. Pre-PRD 031 the key was regenerated every boot, invalidating all live tokens.
- A single RSA `JWKSource` signs all `/oauth2/token` output; one RSA decoder validates it (the old composite HMAC+RSA decoder is gone). Every grant produces structurally identical tokens.
- **Access token TTL: 1 h.** Refresh token TTL: 30 d.
- **Rotate-when-stale.** `/oauth2/token grant_type=refresh_token` reissues a refresh token only when within `REFRESH_RENEWAL_THRESHOLD_SECONDS` (7 d) of expiry. Bounds DB writes to ~1/user/week.
- **Single-token-per-user model.** Server stores `sha256(refreshToken)` under `user.preferences["org.rapla.auth.session"]`. `/oauth2/revoke` clears it; any older refresh token from that user becomes invalid.
- **JWT claims to know about:** `sub` (UUID), `username`, `typ` (`access` vs `refresh`), `exp`, `iss`, `aud`. The `typ=refresh` claim is what `grant_type=refresh_token` validates.

## Authorization Code + PKCE flow (the OAuth path)

Single registered client `rapla-client` covers both Swing and Angular. Public client (no `client_secret`). PKCE mandatory.

```
Client                        Browser                     rapla-app (Spring AS)
  │                              │                              │
  ├── 1. open authorize URL ────► │                              │
  │   /oauth2/authorize           │ ── 2. GET ──────────────────►│
  │   ?response_type=code         │                              │
  │   &client_id=rapla-client     │                              │
  │   &redirect_uri=…             │                              │
  │   &code_challenge=…           │                              │
  │   &code_challenge_method=S256 │                              │
  │   &scope=openid               │ ◄── 3. /login form ──────────│
  │                               │ ── 4. POST creds ───────────►│
  │                               │ ◄── 5. 302 redirect ─────────│
  │                               │   to redirect_uri?code=…     │
  │ ◄── 6. capture code ──────────│                              │
  │                                                              │
  ├── 7. POST /oauth2/token ─────────────────────────────────────►│
  │   grant_type=authorization_code                              │
  │   code=…                                                     │
  │   code_verifier=…  (PKCE proof)                              │
  │                                                              │
  │ ◄── 8. {access_token, refresh_token, id_token} ───────────────│
```

Steps 6–8 work because the client kept the `code_verifier` whose SHA-256 matches the `code_challenge` from step 1. No `client_secret` on disk or in the JNLP cache.

### Custom redirect-URI validator (rapla-specific)

Spring's default `RegisteredClient` only allows exact-match redirect URIs. `AuthorizationServerConfig` installs a custom validator that allows:

- **Same-origin redirects** for Angular (zero-config — SPA at `http://localhost:4200` can use `http://localhost:4200/app/auth/callback`).
- **Loopback redirects** for Swing per RFC 8252 (`http://127.0.0.1:<random-port>/callback`).
- **WSL bridge** for dev convenience: any `172.16.0.0/12` is allowed so the Windows browser can hit the Linux loopback during WSL2 development.

## Client surfaces

### Swing — `SwingOAuthLoginFlow`

- Login dialog has a **"Sign in with browser…"** button next to the password form.
- Flow: spawn an ephemeral loopback server on a random port → open Windows browser to the authorize URL → user logs in → browser is redirected to `http://127.0.0.1:<port>/callback?code=…` → loopback server captures the code → exchanges it at `/oauth2/token`.
- **Refresh-token cache:** hybrid `TokenStore` (JNLP `PersistenceService` → `~/.rapla/tokens.json` 0600 → NoOp). All operations `catch(Throwable)` — never surfaces storage errors as login failures.
- **Logout** (`RaplaClientServiceImpl.logout()`) POSTs `/oauth2/revoke`, opens a browser tab to discovery's `logoutUrl`, clears `TokenStore`, then `SwingUtilities.invokeLater(start(null))` to in-JVM relaunch. After logout, the next `runOauthLogin` adds `prompt=login` to defeat the race where the browser keeps the cookie.

### Angular — `angular-oauth2-oidc`

- Library: `angular-oauth2-oidc@20.0.2`.
- **Talks to the Authorization Server at `:8051` directly, not via the `:4200` ng-serve proxy.** This is deliberate — it makes the SPA Keycloak-replaceable with one env var change (the IdP origin) without needing to reconfigure proxying.
- **Skips discovery doc** (`/.well-known/openid-configuration`) to avoid CORS preflight from cross-origin SPA. Endpoints are configured manually via `window.location.origin` substitution; rapla's `/api/auth/oauth/config` provides the URLs the SPA actually needs.
- After successful login, the SPA stores the access token in memory (or `sessionStorage` if persistence is enabled) and attaches it as `Authorization: Bearer …` on every REST call via an `HttpInterceptor`.

## Remember-me cookies (browser `/login`)

- Mechanism: `PersistentTokenBasedRememberMeServices` + `RaplaTokenRepository` (`rapla-server/.../internal/`).
- Stored under system preferences `org.rapla.auth.rememberMeTokens` as JSON: `{series → {username, tokenValue, lastUsed}}`.
- Survives server restart (matches the persistent-JWK pattern).
- Used by `/login` (the browser page) only — Swing has its own refresh-token cache via `TokenStore`.
- Logout drops the remember-me cookie AND clears the `series` entry server-side.

## Debugging — common failure modes

| Symptom | Likely cause |
|---|---|
| 401 on `/api/...` calls after server restart | RSA key isn't being read from `RaplaKeyStorage` (regression of PRD 029 Phase 1). Check startup logs for "Generated new RSA keypair" — should only appear on first boot of a fresh data file. |
| `/login` form rejects valid credentials | `raplaUserDetailsService` not wired or UUID/username lookup broken. Bean lives in `AuthorizationServerConfig`. UUID-first, username fallback. |
| OAuth callback page 401s mid-flow | Almost always the redirect-URI validator. Check `AuthorizationServerConfig.RedirectUriValidator` — likely the test URI isn't in same-origin / loopback / 172.16/12. |
| CORS error on `/.well-known/openid-configuration` from SPA | SPA shouldn't be hitting it. Angular config is supposed to skip discovery. If it's hitting it, the `OAuthService` config isn't suppressing discovery. |
| Refresh token rejected | Either signature mismatch (rotating RSA key — should be impossible post PRD 029, but check) or `sha256(token)` doesn't match the stored preference. Use `gh api`-style introspection by reading the user's preferences XML from the data file. |
| `prompt=login` not forcing reprompt | Spring SAS race — see PRD 029 Phase 2. The `prompt=login` parameter is the workaround. Verify the authorize URL includes `&prompt=login` on the post-logout re-auth. |

## Probing the wire

For a hands-on `curl` workflow against `/oauth2/token`, see the `api-testing` skill — it has the full login → bearer-token → request loop, including the `admin` / empty password dev credentials.

For browser-side OAuth debugging (CORS, redirect chains, cookie state, JS-side state), use Playwright MCP via the `angular-frontend` skill. `browser_network_requests` is the right tool for "where exactly did the flow break" — replaces guessing at HAR exports.

## Test entry points

| Test | What it covers |
|---|---|
| `UnifiedRefreshIntegrationTest`, `OAuthConfigControllerTest` (`rapla-app/src/test/java/.../web/`) | Tier-3 MockMvc tests for the `/oauth2/token` grants + discovery. `OAuthTestSupport.loginAs(...)` is the shared password-grant login helper — use it in new auth tests. |
| `OpenApiSmokeTest` | Confirms the OpenAPI spec exposes all auth endpoints with the right content-types. |

Both tests use the real `AuthorizationServerConfig` — no mocks (per PRD 027 / AGENTS.md §13).

## When the design feels weird, check PRD 029 and 031

The cross-origin "SPA at :4200 talks to AS at :8051" is intentional — see PRD 029 OQ 11 + PRD 031 §"Why this is needed now". Don't proxy auth traffic through ng-serve "for consistency" — it breaks the Keycloak-swap story.
