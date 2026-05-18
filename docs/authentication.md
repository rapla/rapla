# Rapla Authentication

Rapla uses **OAuth 2.0 / OIDC exclusively** for token issuance, refresh,
and revocation (PRD 041, 2026-05-16). All three flows go through the
bundled Spring Authorization Server's `/oauth2/*` endpoints:

| Path | Used by | Credentials |
|---|---|---|
| `GET /oauth2/authorize` + `POST /oauth2/token` (code + PKCE) | Angular SPA, Swing "Sign in with browser…" button, Scalar/Swagger UI explorers | redirected through system browser → RSA JWT |
| `POST /oauth2/token grant_type=password` | Direct-password integrators (CI scripts, curl) — replaces former `/api/auth/login` | username + password → RSA JWT |
| `POST /oauth2/token grant_type=refresh_token` | Anything refreshing — Swing, SPA, scripts | refresh JWT → fresh access token |
| `POST /oauth2/revoke` | Logout from any client | refresh (or access) token → server-side revocation |

All access + refresh tokens are RSA-signed JWTs (RS256) issued with the
**persistent** RSA key stored in `RaplaKeyStorage` (rapla preferences).
A token issued before a server restart still validates after the JVM
comes back up. The resource server validates them through a single
decoder in `JwtConfig.java`.

The legacy rapla-custom `/api/auth/login`, `/api/auth/refresh`, and
`/api/auth/logout` endpoints are **deleted**. `/api/auth/oauth/config`
(discovery) + `/api/auth/oauth/exchange/{providerId}` (BFF for external
IdPs that need server-held client_secret) + `/api/auth/api-keys/*`
(personal-access-token management, see [API keys](#api-keys-personal-access-tokens))
remain.

This document covers configuring authentication for a rapla
deployment. For the design rationale of the refresh-token model see
[PRD 031](prd/031-token-refresh-and-api-keys.md) +
[PRD 041](prd/041-openapi-runtime-removal.md); for the OAuth flow see
[PRD 029](prd/029-swing-oauth-login.md) (Swing) and
[PRD 036](prd/036-external-idp-oauth-login.md) (external IdPs).

## The refresh-token model

One refresh token per user. **Stored as the full JWT** in user
preferences under `org.rapla.auth.session` (single slot). Each fresh
login (any grant: code, password) returns the existing valid token
instead of minting a new one — so opening rapla in a second tab or on
a second device hands back the same refresh JWT both ends use.

Refresh requests **never rotate** the token: `/oauth2/token grant_type=refresh_token`
returns the same refresh token plus a fresh access token, until the
refresh token expires (30 d default). At expiry, all sessions for the
user re-Authorize together — predictable, no surprise "logged out in
this tab but not that one" events.

Revocation is **explicit only**: `POST /oauth2/revoke` (any client) or
form-login `/logout` clears the prefs entry. All refresh tokens for
that user are immediately invalid; in-flight access tokens keep working
until their 1 h TTL elapses.

| Property | Default | Where to override |
|---|---|---|
| Access-token TTL | 1 h | `spring.security.oauth2.authorizationserver.client.rapla-client.token.access-token-time-to-live` |
| Refresh-token TTL | 30 d | (constant in `RefreshSessionService.REFRESH_TOKEN_TTL_SECONDS`) |
| Rotation policy | never rotate | by design — see PRD 041 |

For per-device revocation, theft detection via rotation conflict, and
session inventory UI, deploy against Keycloak (PRD 031: IdP swap is an
env-var override of the discovery endpoint URLs).

## Direct password via OAuth2 password grant

Replaces the deleted `/api/auth/login` rapla-custom JSON endpoint with
the OAuth 2.0 standard form-encoded body (RFC 6749 §4.3). OAuth 2.1
deprecates this grant; we re-enable it as a transient replacement for
the legacy path until everyone migrates to interactive code+PKCE.

```bash
curl -X POST http://localhost:8051/oauth2/token \
  -d "grant_type=password" \
  -d "username=admin" \
  -d "password=" \
  -d "client_id=rapla-client"
```

Response:

```json
{
  "access_token": "<JWT, typ=access, 1 h TTL>",
  "refresh_token": "<JWT, typ=refresh, 30 d TTL>",
  "scope": "openid profile",
  "token_type": "Bearer",
  "expires_in": 3599
}
```

Refresh later:

```bash
curl -X POST http://localhost:8051/oauth2/token \
  -d "grant_type=refresh_token" \
  -d "refresh_token=<the refresh JWT>" \
  -d "client_id=rapla-client"
```

Logout:

```bash
curl -X POST http://localhost:8051/oauth2/revoke \
  -d "token=<refresh JWT>" \
  -d "token_type_hint=refresh_token" \
  -d "client_id=rapla-client"
```

Returns HTTP 200 either way (RFC 7009 — opaque "did this token exist?"
non-disclosure). Side effect: clears the user's session entry; all
their refresh tokens become invalid.

## Client login flows — browser, SPA, Swing

Three client surfaces authenticate against rapla — all on OAuth 2.0.

### Browser form login — `POST /login`

A server-rendered **session** login, not a token flow. `LoginPageController`
(`@RequestMapping("/login")`, rapla-app) renders a minimal form; the POST is
intercepted by Spring Security's `UsernamePasswordAuthenticationFilter`
(`SecurityConfig` → `formLogin().loginPage("/login")`). Success → `302` to
the saved request (or `/`) plus a `JSESSIONID` cookie; failure → `302` to
`/login?error`. This backs the `/oauth2/authorize` consent step and direct
server-page access. `admin` / empty password works here — the form
deliberately omits `required` on the password field.

### Angular SPA — OAuth2 authorization_code + PKCE

`GET /oauth2/authorize` → system browser → `/auth/callback` →
`POST /oauth2/token grant_type=authorization_code`. Refresh via
`grant_type=refresh_token`. Entirely on the `/oauth2/*` endpoints.

### Swing client — default OAuth, fallback password dialog

`RaplaClientServiceImpl.startLoginInThread()` drives Swing login:

1. Builds a `LoginDialog` (username/password fields + an OAuth button) and
   probes discovery via `fetchOauthConfig()`.
2. **Default — OAuth enabled, `swing-legacy-login=false`**: the dialog
   shows in "browser login in progress" mode (credential fields hidden)
   and auto-fires `SwingOAuthLoginFlow` — the standard authorization_code
   + PKCE flow against `/oauth2/token` (PRD 029 Phase 2). The credential
   fields are never shown.
3. **Admin opted into the legacy dialog — OAuth enabled,
   `swing-legacy-login=true`**: the dialog is shown in full state with
   username/password fields (PRD 029 Phase 3). When
   `swing-legacy-show-sso-button=true`, the "Sign in with browser…"
   button is also rendered so users can try SSO; otherwise it's hidden.
4. **Fallback — OAuth disabled server-side, or the discovery probe
   fails**: the dialog is shown in full state with username/password
   fields and no SSO button (a button would have no auth server to
   reach).

Token refresh for every client kind is OAuth-standard:
`POST /oauth2/token grant_type=refresh_token` (`MyCustomConnector.java:129`,
`ClientProxyConfig.java:159`, PRD 041).

### Swing fallback password dialog → OAuth2 password grant

When the fallback dialog is used, `loginAction` builds a
`ConnectInfo(username, password, connectAs)` with no access token
(`RaplaClientServiceImpl.java:651-696`) and calls `RemoteOperator.connect()`.
With a null access token, `connect()` takes the password else-branch
(`RemoteOperator.java:144-152`) and calls `RemoteAuthentificationService.login()`.

That seam is implemented by `ClientProxyConfig.OAuth2RemoteAuthentificationService`,
which POSTs `grant_type=password` to `/oauth2/token` (RFC 6749 §4.3) and parses
the snake_case token response into a `LoginTokens`. `MyCustomConnector`'s
password-reauth path (`MyCustomConnector.java:96`) calls the same seam.

> **Historical note.** Before PRD 041's client-side cleanup,
> `RemoteAuthentificationService` was a Spring HTTP-interface proxy
> (`@HttpExchange("/api/auth")` + `@PostExchange("/login")`) pointing at the
> rapla-custom `POST /api/auth/login`. That server endpoint was deleted with
> `AuthController` (commit `d64e8553`), so the fallback dialog returned **404**
> until the proxy was swapped for the OAuth2-backed implementation. The
> interface and both call sites (`RemoteOperator`, `MyCustomConnector`) were
> left untouched — only the `ClientProxyConfig` bean implementation changed.

**Limitation:** the OAuth2 password grant has no `connectAs` parameter, so the
dialog's `" su "` impersonation shorthand is rejected with a clear error
rather than silently logging in as the typed user. Restoring impersonation
needs a dedicated change — a `connect_as` parameter on
`PasswordGrantAuthenticationConverter`, threaded into
`RaplaAuthentificationService`.

**Remaining dead code.** The JAX-RS `org.rapla.endpoints.server.RaplaAuthRestPage`
(`@Path("login")`) is unmounted — its `AuthController` is gone. It is *not*
freely removable: `RemoteSessionImpl` still reads its `LOGIN_COOKIE` constant
for an (also-dead) `raplaLoginToken`-cookie auth branch, so deleting it means
retiring that branch too — a separate cleanup. The orphaned
`RemoteAuthentificationServiceImpl` (an unused `RemoteAuthentificationService`
subclass — no bean, no callers) **has been removed**. Note the sibling
`RaplaEventsRestPage` / `RaplaResourcesRestPage` / `RaplaDynamicTypesRestPage`
are **not** dead — they are live beans the matching `@RestController`s
delegate to.

### Auth endpoint reference

| Path | Status | Handler |
|---|---|---|
| `POST /login`, `POST /logout` (form/session) | live | Spring Security filters; `LoginPageController` renders the page |
| `GET /oauth2/authorize`, `POST /oauth2/token`, `POST /oauth2/revoke` | live | Spring Authorization Server (`AuthorizationServerConfig`) |
| `POST /oauth2/token grant_type=password` | live | `PasswordGrantAuthenticationConverter` + `…Provider` |
| `GET /api/auth/oauth/config`, `POST /api/auth/oauth/exchange/{id}` | live | `OAuthConfigController`, `OAuthExchangeController` |
| `POST`/`GET`/`DELETE` `/api/auth/api-keys[/{id}]` | live | `ApiKeyController` |
| `POST /api/auth/login`, `/api/auth/refresh`, `/api/auth/logout` | **removed** — PRD 041, commit `d64e8553` (was `AuthController`) | none — no replacement route |
| JAX-RS `RaplaAuthRestPage` `@Path("login")` | **dead code** — never mounted, 404 | legacy pre-Spring-Boot |

## API keys (Personal Access Tokens)

Long-lived JWTs that users mint, list, and revoke for integrations
(CI scripts, MCP servers, periodic exporters, external iCal subscribers).
GitHub-style PAT flow. Full design: [PRD 043](prd/043-api-keys-jwt-pat.md).

### How it works

When you mint a key, **rapla generates an RSA-2048 keypair in memory,
signs one JWT with the private half, and returns the JWT in the
response — once.** Only the **public** key is persisted to
`RaplaKeyStorage` (alongside the label, issuance time, and expiry).
The private key is discarded at end-of-handler.

The returned JWT is the credential: copy it, store it where your
integration can read it, and use it as `Authorization: Bearer <jwt>`
on subsequent calls. Same shape as a GitHub PAT — you see it once,
then it's gone from the server's perspective. Rapla can't re-show it
because the private key needed to mint another one is no longer in
memory.

**Security property:** a leaked rapla data file yields only public
keys — useless for impersonation. To revoke a key, delete its prefs
entry; the JWT's signature still verifies cryptographically, but the
server-side membership check fails (`getAPIKeys` no longer returns it).

### End-to-end curl flow

```bash
# 0. Get a session access token (your normal login)
ACCESS=$(curl -s -X POST http://localhost:8051/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&username=homer&password=duffs&client_id=rapla-client" \
  | jq -r .access_token)

# 1. Mint a new API key (the response shows the JWT ONCE — save it now)
RESPONSE=$(curl -s -X POST http://localhost:8051/api/auth/api-keys \
  -H "Authorization: Bearer $ACCESS" \
  -H "Content-Type: application/json" \
  -d '{"label":"CI deploy","expiresInDays":365}')
echo "$RESPONSE" | jq .
# {
#   "id":         "<RFC 7638 thumbprint of the public JWK>",
#   "label":      "CI deploy",
#   "alg":        "RS256",
#   "thumbprint": "<same as id>",
#   "createdAt":  "2026-05-16T12:00:00Z",
#   "expiresAt":  "2027-05-16T12:00:00Z",
#   "key":        "eyJhbGc...<the signed JWT — shown ONLY here>"
# }
API_KEY=$(echo "$RESPONSE" | jq -r .key)
KEY_ID=$( echo "$RESPONSE" | jq -r .id)

# 2. List your keys (metadata only — no JWT material)
curl -s http://localhost:8051/api/auth/api-keys \
  -H "Authorization: Bearer $ACCESS" | jq .

# 3. Use the API key as Bearer on any rapla endpoint
curl -s http://localhost:8051/api/resources \
  -H "Authorization: Bearer $API_KEY" | jq .

# 4. Revoke (idempotent — DELETE always returns 204)
curl -X DELETE "http://localhost:8051/api/auth/api-keys/$KEY_ID" \
  -H "Authorization: Bearer $ACCESS"
```

> ⚠️ **The `key` field is shown exactly once.** Subsequent `GET
> /api/auth/api-keys` calls return metadata only (id, label, alg,
> thumbprint, createdAt, expiresAt) — never the JWT. Lose it and you
> must mint a new one + revoke the old.

### JWT shape

```
header:  { alg: "RS256", typ: "JWT", kid: "<RFC 7638 thumbprint>" }
payload: {
  sub:  "<userId>",
  iat:  <issued-at>,
  exp:  <expiresInDays·86400 from iat>  // absent if expiresInDays was null
  typ:  "api_key",                      // distinguishes from access/refresh
  name: "<label>"
}
```

The JWT carries **no embedded public key**. The server looks up the
trusted JWK from its own storage by matching `kid` — JWT-self-claimed
key material is never trusted.

### Endpoints

| Verb | Path | Returns |
|---|---|---|
| `POST` | `/api/auth/api-keys` | `{id, label, alg, thumbprint, createdAt, expiresAt, key}` — `key` is the JWT, shown once |
| `GET`  | `/api/auth/api-keys` | `[{id, label, alg, thumbprint, createdAt, expiresAt}, …]` — no key material |
| `DELETE` | `/api/auth/api-keys/{id}` | `204 No Content` (idempotent) |

All three require a valid access-token `Bearer` (you must be logged
in to manage your own keys). The API key itself becomes usable on
**every** authenticated rapla endpoint (`/api/resources`, `/api/events`,
`/api/auth/api-keys`, etc.) once minted.

### Storage layout

Stored under the existing `RaplaKeyStorage.storeAPIKey` API — same
prefs slot the legacy refresh-token mechanism uses, now actually
multi-slot. One slot per registered key, keyed by the public JWK's
RFC 7638 thumbprint:

```json
{
  "refreshToken": "<the user's session refresh JWT — single slot, see above>",
  "<thumbprint-1>": "{\"kid\":\"…\",\"jwk\":\"…public-JWK…\",\"alg\":\"RS256\",\"label\":\"CI deploy\",\"iat\":…,\"exp\":…}",
  "<thumbprint-2>": "…"
}
```

Note what's **not** stored: the JWT itself, the signature, the private
key. Only the public JWK plus listing metadata.

### Current limits (v1)

- **Algorithm**: RS256 only. EdDSA / Ed25519 deferred to a future
  iteration.
- **Lifetime**: capped only by the user-supplied `expiresInDays`
  (omit it for never-expire). No server-side ceiling.
- **Per-key scopes**: not supported — every key carries the issuing
  user's full access. Scope-restricted keys are a future PRD.
- **No rotation**: to "rotate" a key, mint a fresh one and revoke the
  old (two calls).
- **No `last_used_at` tracking** — there's no "abandoned key" report
  in v1.
- **No UI yet** — Angular + Swing dialogs land in a follow-up PRD.

## Migration from `/api/auth/*` (pre-PRD-041)

| Old | New | Body shape |
|---|---|---|
| `POST /api/auth/login` JSON `{username, password}` | `POST /oauth2/token` form-encoded `grant_type=password&username=…&password=…&client_id=rapla-client` | snake_case response (`access_token`, `refresh_token`, `expires_in`, `token_type`) |
| `POST /api/auth/refresh` JSON `{refreshToken}` | `POST /oauth2/token` form-encoded `grant_type=refresh_token&refresh_token=…&client_id=rapla-client` | snake_case response |
| `POST /api/auth/logout` with Bearer | `POST /oauth2/revoke` form-encoded `token=…&token_type_hint=refresh_token&client_id=rapla-client` | empty 200 |
| `GET /api/auth/oauth/config` | **Unchanged** — discovery endpoint stays | unchanged |
| `POST /api/auth/oauth/exchange/{providerId}` | **Unchanged** — BFF for external IdPs stays | unchanged |
| (none) | `POST /api/auth/api-keys` (mint), `GET /api/auth/api-keys` (list), `DELETE /api/auth/api-keys/{id}` (revoke) | new — PRD 043 |

The `refreshUrl` field is **gone** from the discovery response; clients
use `tokenUrl` for both initial code exchange and subsequent refresh
(OAuth standard).

## Quick start (default deployment)

Defaults are correct for a single rapla server at `https://rapla.yourdomain.com`:

```bash
java -jar rapla-2.1-SNAPSHOT.jar
```

The Swing client's "Sign in with browser…" button works out of the
box. End-users log in via the form at `/rapla/login`, the auth server
redirects to a loopback URL on their machine, and the Swing client
completes the token exchange. No per-deployment OAuth config required.

Once an Angular SPA is added at the same origin, its
`/auth/callback` redirect is auto-accepted by the same-origin
validator — also zero-config.

The only authentication concern for a typical deployment is replacing
the default empty admin password (see "Replacing the default admin"
below).

## Configuration reference

All properties live under `rapla.oauth.*` in `application.yml`. Each can
be overridden with the matching env var.

| Property | Env var | Default | Meaning |
|---|---|---|---|
| `rapla.oauth.enabled` | `RAPLA_OAUTH_ENABLED` | `true` | Master toggle for the OAuth flow. When `false`, the Swing "Sign in with browser…" button is hidden and the discovery endpoint reports `enabled: false`. `/oauth2/token grant_type=password` direct-login still works. |
| `rapla.oauth.client-id` | `RAPLA_OAUTH_CLIENT_ID` | `rapla-client` | OAuth client id. Both Swing and Angular use this single id; their redirect URIs differ. |
| `rapla.oauth.scopes` | `RAPLA_OAUTH_SCOPES` | `openid,profile` | Scopes granted to issued tokens. Comma-separated. |
| `rapla.oauth.show-paste-fallback` | `RAPLA_OAUTH_SHOW_PASTE_FALLBACK` | `false` | Show a "paste callback URL here" dialog alongside the browser launch. Enable only for environments where the automatic loopback redirect can't reach the client. |
| `rapla.oauth.swing-legacy-login` | `RAPLA_OAUTH_SWING_LEGACY_LOGIN` | `false` | When `true`, the Swing client shows the legacy username/password dialog instead of auto-firing the browser OAuth flow. Use it to keep end-users on the familiar dialog during an OAuth rollout. The browser flow still works from the dialog when the SSO button is also enabled (below). |
| `rapla.oauth.swing-legacy-show-sso-button` | `RAPLA_OAUTH_SWING_LEGACY_SHOW_SSO_BUTTON` | `false` | Only effective when `swing-legacy-login=true`. Adds a "Sign in with browser…" button to the legacy dialog so willing users can opt into testing SSO without it being forced on everyone. |
| `rapla.oauth.allow-wsl-bridge-redirects` | `RAPLA_OAUTH_ALLOW_WSL_BRIDGE_REDIRECTS` | `true` | **Dev-only**: accept any port for redirect URIs in `172.16.0.0/12` (Hyper-V WSL2 bridge). Lets developers run the Swing client in WSL2 without enabling mirrored networking. **Set to `false` in production.** |
| `rapla.oauth.allow-same-origin-redirects` | `RAPLA_OAUTH_ALLOW_SAME_ORIGIN_REDIRECTS` | `true` | Accept any redirect URI whose scheme/host/port match the auth-server request's public origin (honoring `X-Forwarded-*`), provided the path matches a registered URI. Lets Angular at any deployment hostname auto-register. Safe with PKCE — recommend keeping on. |

### Spring redirect URIs

The "registered" redirect URIs live under
`spring.security.oauth2.authorizationserver.client.rapla-client.registration.redirect-uris`:

```yaml
redirect-uris:
  - http://127.0.0.1/login/oauth2/code/rapla   # Swing loopback (end users)
  - http://localhost/auth/callback             # Angular dev (any port)
  - http://127.0.0.1/auth/callback             # Angular dev alt
```

Spring matches **any port** on loopback hosts (`127.0.0.1`, `localhost`,
`::1`). For non-loopback hosts, exact match required. To register
additional URIs in production:

```bash
export SPRING_SECURITY_OAUTH2_AUTHORIZATIONSERVER_CLIENT_RAPLA-CLIENT_REGISTRATION_REDIRECT-URIS_3=https://rapla.yourdomain.com/auth/callback
```

(Use `_0`, `_1`, `_2`, `_3` etc. to append to the array.)

In practice you usually don't need this because
`rapla.oauth.allow-same-origin-redirects=true` auto-accepts URIs at
the deployment's own origin.

## Deployment recipes

### Single rapla server at a public URL

```bash
SERVER_PORT=8051
RAPLA_FILE_DATASOURCES_RAPLAFILE=/var/lib/rapla/data.xml

# OAuth: hardening on
RAPLA_OAUTH_ALLOW_WSL_BRIDGE_REDIRECTS=false

# Logging
LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_SECURITY=INFO
```

Reverse proxy in front of port 8051 with TLS termination. Make sure the
proxy sets `X-Forwarded-Proto` and `X-Forwarded-Host` so the
auth-server's discovery endpoint emits the public URL and the
same-origin validator sees the correct public origin. Nginx/traefik/caddy
all do this by default.

### Behind a reverse proxy at a path other than `/rapla`

```bash
SERVER_SERVLET_CONTEXT_PATH=/scheduling
```

The discovery endpoint emits `authorizeUrl`/`tokenUrl` based on the
public URL it sees on incoming requests, so this picks up
automatically.

### Multiple deployment surfaces (Swing + Angular)

Both use the same `rapla-client` OAuth client. No additional
configuration beyond the defaults — the same-origin validator handles
the Angular redirect URI, and the loopback rule handles the Swing one.

### Restricted-network environments (corporate firewall, locked-down Defender)

Some users may hit "This site can't be reached" after signing in
because the system browser can't open a connection back to the Swing
client's local listener. For these deployments:

```bash
RAPLA_OAUTH_SHOW_PASTE_FALLBACK=true
```

This makes the paste-the-callback-URL dialog appear ~12 s after the
browser launch. If the automatic redirect succeeded the dialog never
opens; if it didn't, the user can paste the URL from their browser
and the flow completes via in-process HTTP.

## Replacing the default admin

The bundled `data/data.xml` ships with one admin account: **username
`admin`, empty password**. This is intentional for first-time dev use
but should not survive into production. Two options:

1. **Edit `data.xml`** before first launch: set a password element on
   the admin user.
2. **Through the running Swing client**: log in as admin, open the
   user editor, change the password, log out.

There is no production-mode lockout enforcing a non-empty admin
password today — that's planned hardening (see issue/PRD on
production-mode hardening when it lands).

## Verifying the setup

After launching the server, hit the discovery endpoint:

```bash
curl -s http://localhost:8051/api/auth/oauth/config | python3 -m json.tool
```

Expected response (defaults):

```json
{
  "enabled": true,
  "clientId": "rapla-client",
  "authorizeUrl": "http://localhost:8051/rapla/oauth2/authorize",
  "tokenUrl": "http://localhost:8051/rapla/oauth2/token",
  "scopes": ["openid", "profile"],
  "showPasteFallback": false
}
```

For a deployment at `https://rapla.yourdomain.com` behind a proxy,
the URLs in `authorizeUrl`/`tokenUrl` should be the public HTTPS URLs,
not the internal localhost ones. If you see `localhost` in production,
your proxy isn't passing `X-Forwarded-*` headers — fix that.

## Common issues

### "Whitelabel error page" after clicking Sign in with browser

The auth server rejected the redirect URI. Cause: deployment-specific
redirect URI isn't on the registered list AND doesn't match any
loopback / WSL bridge / same-origin allowance. Fix: register the URI
explicitly with `SPRING_SECURITY_OAUTH2_…_REDIRECT-URIS_n=…`.

### "This site can't be reached" after sign-in

The system browser can't open the loopback URL on the user's machine.
Causes vary — WSL2 NAT, corporate firewall blocking inbound localhost,
some antivirus configurations. Workaround:
`RAPLA_OAUTH_SHOW_PASTE_FALLBACK=true`. Long-term: investigate the
specific network restriction.

### Token issued by `/oauth2/token` rejected as Bearer

All tokens (access + refresh + API keys) are RSA-signed JWTs validated
through a single `JwtDecoder` in `JwtConfig.java` against rapla's JWKS.
If a token fails validation, check that `AuthorizationServerConfig`
booted (Spring `@AutoConfiguration` loads it by default) — if it didn't,
the `JWKSource` bean is missing and no tokens can be issued in the
first place. With external IdPs configured (PRD 036), the decoder
wraps an `IssuerAwareJwtDecoder` that routes by `iss` claim; check the
provider's JWKS URL is reachable from the rapla server.

### After server restart, sessions survive (PRD 029 Option A)

The auth server's RSA keypair is **persisted** in `RaplaKeyStorage`
(rapla preferences, same data file as the rest of the application
state). A token issued before a JVM restart still validates after the
restart — same key, same signature. The refresh-token hash is also in
preferences, so refresh requests after a restart also succeed.

## External IdP (Microsoft Entra ID + Google + Keycloak)

Rapla can delegate authentication to **Microsoft Entra ID** (formerly
Azure AD), **Google**, and **Keycloak** alongside — or instead of — the
bundled Spring Authorization Server. Multiple providers can be enabled
simultaneously;
the Angular SPA shows a "Sign in with …" picker on `/login`. Swing
always uses the embedded SAS (deprecation context — see
[PRD 036](prd/036-external-idp-oauth-login.md)).

> **Core concept**: every authenticated identity — local or external —
> resolves to a rapla `User` entity (groups, permissions, ownership
> live there, not in the token). Externally-authed users are matched
> by stable external-id claim first, then by email, and optionally
> auto-provisioned. See PRD 036 "Why we keep the rapla User".

### How the SPA reaches the IdP — direct vs BFF

The token-exchange leg of the OAuth flow happens through one of two
routes, chosen per-provider by whether a `client-secret` is configured:

| Configured `client-secret` | Token endpoint route | When this applies |
|---|---|---|
| **Empty / unset** | SPA POSTs directly to the IdP's `/token` (CORS, no secret) | Entra **Single-page application** platform, Google **Desktop app** type — true public PKCE clients |
| **Set** | SPA POSTs to rapla's **BFF** (`/api/auth/oauth/exchange/{providerId}`); rapla adds the server-held `client_secret` and forwards to the IdP | Entra **Web** platform, Google **Web application** type — confidential clients that demand a secret even with PKCE |

Either route preserves PKCE end-to-end. The BFF exists because Google's
"Web application" client type *requires* `client_secret` on the token
endpoint regardless of PKCE — sending that to the SPA would defeat the
"secret" property. Routing through the BFF keeps it server-side.

**Entra SPA platform forbids server-side token requests** with
`AADSTS9002327: must be cross-origin`. So for Entra SPA-platform
clients, the BFF is bypassed automatically (no secret configured →
direct route). For Entra Web-platform clients (which support secrets),
configure `client-secret` and the BFF takes over. Both work.

### Bearer token choice on rapla API calls

The Angular SPA sends `Authorization: Bearer <token>` on every rapla
API call. The token *value* depends on the active provider:

| Active provider | Bearer token sent | Why |
|---|---|---|
| rapla embedded SAS | `access_token` (a JWT signed by rapla with the user's UUID as `sub`) | Single-issuer setup, rapla's resource server validates against its own JWKS. |
| Microsoft Entra | `id_token` (JWT, signed by Entra) | Entra's access_token is *not* always a JWT (it's resource-scoped); the OIDC id_token always is. |
| Google | `id_token` (JWT, signed by Google) | Google's access_token is opaque, not a JWT. Only the id_token can be validated as a Bearer JWT against Google's JWKS. |

Rapla's resource server runs an `IssuerAwareJwtDecoder` that routes JWT
validation by the unverified `iss` claim — local tokens hit rapla's
JWKS, Entra tokens hit Entra's tenant JWKS, Google tokens hit Google's
JWKS. The Angular `AuthService.token()` picks `id_token` vs
`access_token` based on which provider the user is signed in with.

### Logout behaviour per provider

| Provider | Logout |
|---|---|
| rapla SAS | OIDC RP-initiated logout at `/connect/logout` — accepts `id_token_hint` + `post_logout_redirect_uri`, terminates the rapla session and bounces back to `/app/`. |
| Microsoft Entra | OIDC RP-initiated logout at `/oauth2/v2.0/logout` — same shape. |
| Google | **No proper RP-initiated OIDC logout.** SPA clears local tokens and navigates back to `/login` without redirecting anywhere external. Optionally, set `rapla.oauth.external.google.revoke-on-logout=true` to revoke the Google grant via `/revoke` on logout (off by default — users typically expect "log out of rapla", not "uncouple my Google account"). |

`AuthService.signOut()` checks each provider's `endSessionUrl`: if set,
it triggers angular-oauth2-oidc's IdP redirect; if empty (Google), it
clears tokens locally and routes to `/login`.

### Config reference — external providers

All settable via the matching `RAPLA_OAUTH_EXTERNAL_*` env vars.

| Property | Default | Meaning |
|---|---|---|
| `rapla.oauth.external.microsoft.enabled` | `false` | Enable the Microsoft Entra provider. |
| `rapla.oauth.external.microsoft.tenant` | *(required)* | Entra tenant GUID (single-tenant — recommended) or `common` (multi-tenant). |
| `rapla.oauth.external.microsoft.client-id` | *(required)* | Application (client) ID from your Entra App Registration. |
| `rapla.oauth.external.microsoft.client-secret` | *(empty)* | **Leave empty for Entra SPA platform** (default — true public PKCE client). Set only when using Entra Web platform (confidential client). When set, the BFF route is used. |
| `rapla.oauth.external.microsoft.hosted-domain` | *(empty)* | Optional email-domain guard; rejects logins from any other domain. |
| `rapla.oauth.external.microsoft.auto-provision` | `true` | Create a rapla `User` on first sign-in if no match. Matches the LDAP precedent. Single-tenant Entra is already scoped to your directory; set `false` to require admin pre-provisioning. |
| `rapla.oauth.external.microsoft.display-name` | `Sign in with Microsoft` | Picker button label. |
| `rapla.oauth.external.microsoft.order` | `10` | Sort key in the picker (lower = first). |
| `rapla.oauth.external.microsoft.web-picker-visible` | `true` | Show on the web picker. Set `false` to keep token validation working but hide the button. |
| `rapla.oauth.external.google.enabled` | `false` | Enable the Google provider. |
| `rapla.oauth.external.google.client-id` | *(required)* | OAuth 2.0 Client ID from Google Cloud Console. |
| `rapla.oauth.external.google.client-secret` | *(empty)* | **Required when using Google Web application client type** (Google enforces it even with PKCE). Leave empty for Google Desktop app type (no secret needed). When set, the BFF route is used. |
| `rapla.oauth.external.google.hosted-domain` | *(empty)* | Restrict to a Google Workspace domain via the `hd` claim. |
| `rapla.oauth.external.google.auto-provision` | `true` | Create a rapla `User` on first sign-in if no match. **For consumer Google (no `hosted-domain`), set this to `false`** — otherwise any verified Google account on Earth becomes a rapla user. With a `hosted-domain` set, this is scoped to your Workspace. |
| `rapla.oauth.external.google.revoke-on-logout` | `false` | POST to Google's `/revoke` on sign-out (off by default — logging out of rapla shouldn't uncouple the user's other Google services). |
| `rapla.oauth.external.keycloak.enabled` | `false` | Enable the Keycloak provider. |
| `rapla.oauth.external.keycloak.base-url` | *(required)* | The Keycloak server's public base URL, e.g. `https://keycloak.example.com`. Every OIDC endpoint is derived from `base-url` + `realm`. |
| `rapla.oauth.external.keycloak.realm` | *(required)* | The Keycloak realm name. A realm is already a tenant — one rapla deployment maps to one realm. |
| `rapla.oauth.external.keycloak.client-id` | *(required)* | Client ID registered in the realm. |
| `rapla.oauth.external.keycloak.client-secret` | *(empty)* | Leave empty for a Keycloak **public** client (PKCE-only — recommended for SPAs). Set it for a **confidential** client; the BFF route is then used, same as Google Web app. |
| `rapla.oauth.external.keycloak.hosted-domain` | *(empty)* | Optional email-domain guard. |
| `rapla.oauth.external.keycloak.auto-provision` | `true` | Create a rapla `User` on first sign-in if no match. A Keycloak realm is already scoped, so on is the expected SSO behaviour. |
| `rapla.oauth.external.keycloak.display-name` | `Sign in with Keycloak` | Picker button label. |
| `rapla.oauth.external.keycloak.order` | `15` | Sort key in the picker (lower = first). |
| `rapla.oauth.external.keycloak.web-picker-visible` | `true` | Show on the web picker. |
| `rapla.oauth.web.picker.mode` | `auto` | `auto` (show when ≥2 visible providers), `always`, or `never`. |
| `rapla.oauth.web.picker.primary` | `rapla` | Which provider to auto-fire in `auto`/`never` modes. |
| `rapla.oauth.web.rapla-in-picker` | `true` | Show the "Sign in with rapla password" entry in the web picker alongside external providers. Keeping it visible matters for admin break-glass access when an external IdP is misconfigured or down. Set `false` only for strict SSO-only deployments. With a single visible provider, the picker doesn't render at all (`mode=auto` needs ≥2). |

Endpoint URLs (authorize, token, jwks, end-session) are derived per
provider — for Entra from `tenant`, for Google these are static. All
URLs can be overridden via individual properties for sovereign-cloud
Entra (China, GovCloud) or other special cases.

> **Secrets handling.** `client-secret` values *never* appear in the
> discovery response (`/api/auth/oauth/config`) — they stay server-side
> and are added by the BFF on outbound token requests. The Angular SPA
> never sees them. See "Managing secrets" below.

### Recipe: Microsoft Entra ID (SPA platform — recommended)

This is the cleaner path: a true public PKCE client, no secret to
manage. Works because the SPA POSTs to Entra directly (CORS).

1. **Register the application** in Entra:
   - Azure portal → Microsoft Entra ID → App registrations → New registration.
   - Name: `rapla` (or anything you like).
   - Supported account types: **Accounts in this organizational directory only** (single-tenant — strongly recommended; multi-tenant requires `tenant=common` and accepts users from any Entra tenant).
   - Redirect URI: select platform **Single-page application** from the dropdown, then enter `https://rapla.yourdomain.com/app/auth/callback`. For dev: `http://localhost:4200/app/auth/callback` (Angular dev server) and/or `http://localhost:8051/app/auth/callback` (rapla direct).
   - Click **Register**.
2. **Note the IDs** (Overview tab):
   - **Application (client) ID** → `RAPLA_OAUTH_EXTERNAL_MICROSOFT_CLIENT_ID`.
   - **Directory (tenant) ID** → `RAPLA_OAUTH_EXTERNAL_MICROSOFT_TENANT`.
3. **API permissions**: the default delegated `User.Read` is fine. No admin consent needed.
4. **Run rapla** with:
   ```bash
   export RAPLA_OAUTH_EXTERNAL_MICROSOFT_ENABLED=true
   export RAPLA_OAUTH_EXTERNAL_MICROSOFT_TENANT=11111111-2222-3333-4444-555555555555
   export RAPLA_OAUTH_EXTERNAL_MICROSOFT_CLIENT_ID=66666666-7777-8888-9999-aaaaaaaaaaaa
   java -jar rapla-2.1-SNAPSHOT.jar
   ```

### Recipe: Microsoft Entra ID (Web platform — secret-based)

Use this when your security policy mandates confidential clients, or
when integrating with a legacy Entra registration that's already on the
Web platform.

1. Same as SPA recipe step 1, **except**: pick platform **Web** instead of Single-page application.
2. Note the IDs (same as above).
3. **Generate a client secret**: App Registration → Certificates & secrets → New client secret → copy the **Value** field (not the Secret ID).
4. **Run rapla** with the extra secret env var:
   ```bash
   export RAPLA_OAUTH_EXTERNAL_MICROSOFT_ENABLED=true
   export RAPLA_OAUTH_EXTERNAL_MICROSOFT_TENANT=<tenant-guid>
   export RAPLA_OAUTH_EXTERNAL_MICROSOFT_CLIENT_ID=<client-guid>
   export RAPLA_OAUTH_EXTERNAL_MICROSOFT_CLIENT_SECRET=<secret-value>
   java -jar rapla-2.1-SNAPSHOT.jar
   ```
The BFF route is used automatically when `client-secret` is set.

### Recipe: Google (Web application — requires secret + BFF)

Google's Web application client type enforces `client_secret` on the
token endpoint even with PKCE. The BFF route handles this.

1. **Create OAuth credentials** in Google Cloud Console:
   - https://console.cloud.google.com → APIs & Services → Credentials → Create credentials → OAuth client ID.
   - Application type: **Web application**.
   - **Authorised JavaScript origins**: `https://rapla.yourdomain.com` (and `http://localhost:4200` for Angular dev, `http://localhost:8051` for rapla direct).
   - **Authorised redirect URIs**: `https://rapla.yourdomain.com/app/auth/callback` (and matching localhost variants for dev).
2. **Configure the OAuth consent screen** (one-time): User type Internal (Workspace) or External (consumer Gmail). Scopes: `openid`, `profile`, `email`.
3. **Copy both**:
   - **Client ID** → `RAPLA_OAUTH_EXTERNAL_GOOGLE_CLIENT_ID`.
   - **Client Secret** → `RAPLA_OAUTH_EXTERNAL_GOOGLE_CLIENT_SECRET`.
4. **Run rapla** with:
   ```bash
   export RAPLA_OAUTH_EXTERNAL_GOOGLE_ENABLED=true
   export RAPLA_OAUTH_EXTERNAL_GOOGLE_CLIENT_ID=000000000000-aaaa.apps.googleusercontent.com
   export RAPLA_OAUTH_EXTERNAL_GOOGLE_CLIENT_SECRET=<secret>
   export RAPLA_OAUTH_EXTERNAL_GOOGLE_HOSTED_DOMAIN=yourdomain.com
   java -jar rapla-2.1-SNAPSHOT.jar
   ```
   `HOSTED_DOMAIN` is optional but **strongly recommended for Workspace
   deployments** — without it, any verified Google account (including
   `@gmail.com` consumers) can authenticate. With it, the server
   rejects tokens whose `hd` claim doesn't match.

### Recipe: Google (Desktop app — no secret, direct route)

Alternative client type that avoids the secret entirely. Suitable for
dev or smaller deployments; production may prefer Web application.

1. Same as Web application recipe step 1, **except**:
   - Application type: **Desktop app**.
   - No JavaScript origins / redirect URIs to register — Desktop app allows any loopback URI by default.
2. Copy the **Client ID** (no secret is generated for Desktop apps).
3. **Run rapla** with `RAPLA_OAUTH_EXTERNAL_GOOGLE_CLIENT_ID` only — leave `RAPLA_OAUTH_EXTERNAL_GOOGLE_CLIENT_SECRET` unset. The direct (no-BFF) route is used automatically.

### Recipe: Keycloak

[Keycloak](https://www.keycloak.org/) is a self-hosted OIDC provider with
realm-based isolation. One rapla deployment integrates with one Keycloak
realm. Unlike Microsoft/Google, rapla derives every OIDC endpoint from just
`base-url` + `realm` — no per-URL config.

1. **Create a realm** in the Keycloak admin console: *Realms → Create realm*
   → name it (e.g. `rapla`).
2. **Create a client** in that realm: *Clients → Create client*.
   - Client type: **OpenID Connect**, Client ID e.g. `rapla-app`.
   - *Capability config*: **Standard flow** on. Leave **Client
     authentication** OFF for a public PKCE client (recommended for the SPA);
     turn it ON for a confidential client (then set `client-secret`).
   - *Login settings*:
     - **Valid redirect URIs**: `https://rapla.yourdomain.com/app/auth/callback`
       (for dev also `http://localhost:4200/app/auth/callback` and
       `http://localhost:8051/app/auth/callback`).
     - **Web origins**: the rapla origin(s), or `+` to reuse the redirect-URI
       origins (CORS).
3. **Run rapla** with:
   ```bash
   export RAPLA_OAUTH_EXTERNAL_KEYCLOAK_ENABLED=true
   export RAPLA_OAUTH_EXTERNAL_KEYCLOAK_BASE_URL=https://keycloak.yourdomain.com
   export RAPLA_OAUTH_EXTERNAL_KEYCLOAK_REALM=rapla
   export RAPLA_OAUTH_EXTERNAL_KEYCLOAK_CLIENT_ID=rapla-app
   # Confidential client only — omit for a public PKCE client:
   # export RAPLA_OAUTH_EXTERNAL_KEYCLOAK_CLIENT_SECRET=<secret>
   java -jar rapla-2.1-SNAPSHOT.jar
   ```

rapla derives the endpoints as:

```
issuer        = {base-url}/realms/{realm}
authorizeUrl  = {base-url}/realms/{realm}/protocol/openid-connect/auth
tokenUrl      = {base-url}/realms/{realm}/protocol/openid-connect/token
jwksUrl       = {base-url}/realms/{realm}/protocol/openid-connect/certs
endSessionUrl = {base-url}/realms/{realm}/protocol/openid-connect/logout
```

> **Local testing.** The repo ships a ready-to-run local Keycloak under
> `tools/keycloak/` — `./keycloak.sh start` boots Keycloak 26 on
> `:8080` with a pre-imported `rapla` realm, a public `rapla-app` client,
> and test users. It is not auto-started. See `tools/keycloak/README.md`.
> The matching `rapla.oauth.external.keycloak` block is already in the
> gitignored `application-local.yml`.

### Multi-provider deployments

Enable any combination by setting their respective `*_ENABLED=true`.
The Angular login page renders one button per visible provider, sorted
by `order`. With `web.picker.mode=auto` (default), the picker appears
when ≥2 providers are visible; with a single visible provider it
auto-fires.

To hide a provider from the picker without disabling token validation:

```bash
RAPLA_OAUTH_EXTERNAL_GOOGLE_WEB_PICKER_VISIBLE=false
```

The `rapla` (embedded SAS) entry is shown in the web picker by
default (`rapla.oauth.web.rapla-in-picker=true`). Every deployment has
at least one rapla-local admin account, and that path is the
break-glass route when external IdPs are misconfigured, expired, or
unreachable — hiding it by default makes a chicken-and-egg problem
("can't reach SSO → can't fix SSO config because there's no way to
sign in as admin"). To strictly enforce SSO-only and hide the rapla
button, set `rapla.oauth.web.rapla-in-picker=false`. The OAuth2
password grant (`POST /oauth2/token grant_type=password`) stays
available for API clients regardless of the picker visibility setting.

When only the rapla provider is enabled (no external IdPs), the
picker does not render at all (`mode=auto` requires ≥2 visible
providers) — the SPA auto-fires the rapla SAS flow as before. The
default is a no-op for single-IdP deployments and only kicks in once
external providers are enabled.

> **Default groups for auto-provisioned users.** New users get
> `FacadeImpl.newUser()`'s standard groups: can-read-events-from-others,
> can-create-events, modify-preferences. If you've already configured
> the LDAP `USERGROUP_CONFIG` system preference (an admin-curated
> `Category` list assigned to externally-authed users), those groups
> are used instead — same knob serves LDAP and OAuth. *(The preference
> key is currently plugin-namespaced as
> `org.rapla.plugin.jndi.newusergroups` for historical reasons;
> rename to a non-plugin name is tracked separately.)*

### Migrating existing rapla-local users

Existing rapla users keep working through the OAuth2 password grant
(`POST /oauth2/token grant_type=password`) regardless of which external
providers are enabled.
On first external login, the resolver attaches the external-id to
the matching rapla user by email — subsequent logins skip the email
lookup. No data migration required.

When all users are migrated, set
`RAPLA_OAUTH_LOCAL_ACCOUNTS_ENABLED=false` (planned — see PRD 029
Phase 2 OQ §5) to disable the password grant. Today the password grant
stays available; remove rapla-local passwords from `data.xml` to
disable per-user as a stopgap.

## Managing secrets

`client-secret` values (when configured for the BFF route) and tenant
IDs are **deployment secrets** that should never be committed to git.
Three patterns for local dev (pick one), all of which keep secrets out
of git:

### Pattern A: `application-local.yml` (recommended for local dev)

Create `rapla-app/src/main/resources/application-local.yml` (gitignored
— see `.gitignore` line 37):

```yaml
rapla:
  oauth:
    external:
      microsoft:
        enabled: true
        tenant: <your-tenant-guid>
        client-id: <your-client-id>
        # client-secret only when using Entra Web platform; leave
        # unset for SPA platform (true public PKCE client).
      google:
        enabled: true
        client-id: <client-id>.apps.googleusercontent.com
        client-secret: GOCSPX-...     # Google Web app needs this
        # hosted-domain: yourdomain.com   # Workspace deployments
```

Spring Boot loads `application-{profile}.yml` **only when that profile
is active**. Activate via `SPRING_PROFILES_ACTIVE=local`:

```bash
SPRING_PROFILES_ACTIVE=local \
mvn -f /home/chris/git/rapla/pom.xml -pl rapla-app -am spring-boot:run \
    -Dspring-boot.run.fork=false
```

Without `SPRING_PROFILES_ACTIVE=local`, the file is ignored and rapla
falls back to `application.yml` defaults (external IdPs disabled).
The `local` profile name is conventional and matches the gitignore
pattern; you can also use other profile names (`dev`, `test`, etc.) —
match the filename and the env var to whatever you pick.

Verify the profile loaded:

```bash
grep "profile is active" logs/rapla.log
# 2026-05-15T08:35:10.111+03:00 INFO ... The following 1 profile is active: "local"
```

### Pattern B: Env-var file outside the repo

Keep secrets in a `.env` file in your home directory, sourced before
launching rapla:

```bash
# ~/.rapla.env  (NOT in the repo — outside the git tree entirely)
export RAPLA_OAUTH_EXTERNAL_MICROSOFT_ENABLED=true
export RAPLA_OAUTH_EXTERNAL_MICROSOFT_TENANT=<tenant-guid>
export RAPLA_OAUTH_EXTERNAL_MICROSOFT_CLIENT_ID=<client-id>
export RAPLA_OAUTH_EXTERNAL_GOOGLE_ENABLED=true
export RAPLA_OAUTH_EXTERNAL_GOOGLE_CLIENT_ID=<id>.apps.googleusercontent.com
export RAPLA_OAUTH_EXTERNAL_GOOGLE_CLIENT_SECRET=GOCSPX-...
```

```bash
source ~/.rapla.env && \
mvn -f /home/chris/git/rapla/pom.xml -pl rapla-app -am spring-boot:run \
    -Dspring-boot.run.fork=false
```

The repo's `.gitignore` excludes `.env` and `*.env` patterns to make
accidental commits hard inside the working tree; placing the file
outside the repo entirely (e.g. `$HOME/.rapla.env`) eliminates the
risk completely.

### Pattern C: OS keychain / platform secret manager

For production deployments use the platform's standard secret
mechanism — systemd `LoadCredential=`, Kubernetes Secrets injected as
env vars, Vault agent sidecar, AWS Secrets Manager, etc. The rapla
server reads env vars; how they arrive there is the deployment's
choice. Never put production secrets in any file inside the repo
working tree, even gitignored ones.

### Universal rules

1. **Never inline secrets in `application.yml`** (the tracked one) or
   commit them to the repo in any form.
2. **Don't paste real values into commit messages, PRs, issues, or
   chat logs.** Once a secret is in git history (even a deleted file)
   or a publicly-accessible log, rotate it.
3. **Rotation**: Entra client secrets expire by default (12–24
   months); Google client secrets don't expire automatically but
   should be rotated periodically. rapla picks up the new value at
   next restart.
4. **Verify nothing leaks to the wire**: the
   `/api/auth/oauth/config` endpoint returns `clientId` (public) but
   **never** `clientSecret`:
   ```bash
   curl -s http://localhost:8051/api/auth/oauth/config \
     | jq '[.providers[] | select(.clientSecret)] | length'
   # 0 — secrets are never in the discovery response.
   ```

## Verifying the discovery shape

After launching the server, hit the discovery endpoint:

```bash
curl -s http://localhost:8051/api/auth/oauth/config | jq
```

You should see:
- Top-level flat fields point at rapla SAS (unchanged regardless of external providers — Swing's surface).
- `providers[]` array with one entry per enabled provider, sorted by `order`.
- Each external entry's `tokenUrl` either points at the IdP direct (no secret configured) or at `/api/auth/oauth/exchange/{providerId}` (secret configured → BFF).
- No `clientSecret` field anywhere.

Spot-check the per-provider token URL routing:

```bash
curl -s http://localhost:8051/api/auth/oauth/config | jq '.providers[] | {id, tokenUrl}'
```

`tokenUrl` showing `/api/auth/oauth/exchange/...` = BFF route active for
that provider; otherwise it's the IdP's direct URL.
