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

The legacy rapla-custom `/api/auth/login` endpoint is **deleted**.
`/api/auth/refresh` and `/api/auth/logout` exist again as cookie-based
endpoints on `AuthCookieController` (PRD 072 — the SPA reactive-401
refresh + sign-out path). `/api/auth/oauth/config`
(discovery) + `/api/auth/oauth/exchange/{providerId}` (BFF for external
IdPs that need server-held client_secret) +
`/api/auth/oauth/token-exchange/{providerId}` (PRD 072 external-id-token
→ rapla-token exchange) + `/api/auth/api-keys/*`
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
refresh token expires (21 d default). At expiry, all sessions for the
user re-Authorize together — predictable, no surprise "logged out in
this tab but not that one" events.

Revocation is **explicit only**: `POST /oauth2/revoke` (any client) or
form-login `/logout` clears the prefs entry. All refresh tokens for
that user are immediately invalid; in-flight access tokens keep working
until their 1 h TTL elapses.

| Property | Default | Where to override |
|---|---|---|
| Access-token TTL | 1 h | `spring.security.oauth2.authorizationserver.client.rapla-client.token.access-token-time-to-live` |
| Refresh-token TTL | 21 d | (constant in `RefreshSessionService.REFRESH_TOKEN_TTL_SECONDS`) |
| Rotation policy | never rotate | by design — see PRD 041 |

For per-device revocation, theft detection via rotation conflict, and
session inventory UI, deploy against Keycloak (PRD 031: IdP swap is an
env-var override of the discovery endpoint URLs).

### Access-token claim shape (rapla-SAS)

Rapla's resource-server JWT decoder validates incoming Bearers against
the JWKS published at `/oauth2/jwks`. Since PRD 072 (default
`rapla.oauth.trust-external-issuers=false`) `/api` accepts **only
rapla-issued tokens** — external IdP tokens are consumed at login and
never reach `/api` (see § "Single-issuer `/api`"); set the flag `true`
to restore the legacy multi-issuer decoder. Tokens minted by rapla-SAS
itself carry:

| Claim | Type | When emitted | Purpose |
|---|---|---|---|
| `sub` | string (UUID) | always | rapla User id. Resolver-of-record for `RemoteSession#checkAndGetUser`. |
| `typ` | `"access"` \| `"refresh"` \| `"api_key"` | always | Distinguishes token kind. `refresh` and `api_key` are rejected on the resource-server path. |
| `iss` | string | authorization_code grant only | Issuer URL. `IssuerAwareJwtDecoder` keys its decoder selection on this — rapla-SAS or one of the configured external IdPs. Direct-mint (password / refresh_token / impersonation) rapla-SAS tokens **omit** `iss`; the local decoder accepts them via a `self` sentinel. |
| `aud` | string | authorization_code grant only | `"rapla-client"` for rapla-SAS authorization_code tokens. Direct-mint (password / refresh_token / impersonation) tokens carry **no** `aud` claim. |
| `iat` / `exp` | int (UNIX) | always | Issued-at + expiry. 1 h TTL on access; 21 d on refresh. |
| `preferred_username` | string | rapla-SAS access tokens (PRD 051, 2026-05-22) | OIDC standard claim. Used by the SPA toolbar chip to render the effective user without an extra `/api/users/me` round-trip. Emitted by both the Spring AS `OAuth2TokenCustomizer` path (authorization_code grant) and the `JwtConfig.JwtIssuer` direct-mint path (password / refresh_token grants) — both grants produce structurally identical tokens. |
| `name` | string | rapla-SAS access tokens (PRD 051, 2026-05-22) | Display name (may be empty if the rapla User has no `name`). |
| `act` | object `{sub, username}` | impersonation tokens only (PRD 051) | RFC 8693 delegation actor. Names the admin who minted the impersonation token; the effective subject (`sub`) is the impersonation target. Absent on regular access tokens. See § "Admin impersonation" below. |
| `jti` | string (UUID) | rapla-SAS | Per-token unique id. Used for audit correlation. |

External-IdP tokens (Keycloak, Entra, Google) carry their own claim
shape — rapla's `ExternalUserResolver` reads `upn` → `preferred_username`
→ `email` (case-insensitive) to map them onto a rapla User. See
§ "External IdP login" for the resolver rules.

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
  -d "scope=openid profile" \
  -d "client_id=rapla-client"
```

Response:

```json
{
  "access_token": "<JWT, typ=access, 1 h TTL>",
  "refresh_token": "<JWT, typ=refresh, 21 d TTL>",
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

## Pluggable external authentication stores

Rapla's password-validation step is pluggable via a single optional
`org.rapla.server.AuthenticationStore` bean. `ServerServiceConfig` wires
it via `ObjectProvider<AuthenticationStore>.getIfAvailable()` — at most
one store may be active (vanilla rapla has none, so the field is null and
auth falls through to the local-DB path). When present and enabled, an
external success short-circuits the local password check; declaring two
`AuthenticationStore` beans throws at startup (`ObjectProvider`
ambiguity).

Built-in stores:

| Class | Origin | Bean trigger |
|---|---|---|
| `JNDIAuthenticationStore` | rapla-server LDAP/JNDI plugin | **Not auto-wired** — the class exists but is never registered as a store bean; `JNDIConfigController` only instantiates it for a connection self-test |
| `DhbwNtlmAuthStore` | external dhbwrapla plugin jar (verified against `~/git/dhbwrapla`) | `@Component("org.rapla.dhbw.server.auth")`. `isEnabled()` is gated on `rapla.dhbw.auth.ldap-server` being set **and** the service id `org.rapla.dhbw.server.auth` being enabled (`RaplaServerProperties.isServiceEnabled`) — not a `rapla.plugins.dhbw.enabled` flag. Stamps `authenticationSource = "dhbw-ntlm"` (`SOURCE_ID`), deliberately distinct from the generic `"ldap"` marker. When present it must be the **sole** `AuthenticationStore` bean (single-store model — a second store fails startup on `ObjectProvider` ambiguity). |

Vanilla rapla ships **no** registered `AuthenticationStore` bean; the
only real-world store today is the dhbwrapla NTLM plugin.

Per-login server-side log trail (visible in `logs/rapla.log` /
`logs/run.log`):

```
RaplaAuthentificationService : User 'X' is requesting login.
RaplaAuthentificationService : Checking external authentication for user X   ← fires once, for the single store
DhbwLdapAuthenticate         : Successfully authenticated X with NTLM        ← store success line
RaplaAuthentificationService : Successfull login for 'X'
```

If you see `"Check password for X"` (i.e. local-store-only branch) where
you expected an external-store check, the single `AuthenticationStore`
field is null — no store bean is present, because the plugin isn't on
the classpath at boot. For dhbwrapla in dev, this means you launched
stock rapla-app without `-Pdhbw` and aggregator-pom (see dhbwrapla's
`AGENTS.md` § Server lifecycle for the canonical recipe).
`AuthenticationStoreInjectionTest` is the regression guard — it asserts
that exactly one plugin-provided store bean is injected via
`ObjectProvider.getIfAvailable()`. (Declaring two store beans instead
fails at startup on `ObjectProvider` ambiguity.)

### The `authenticationSource` user marker

After the first successful external authentication, rapla stamps
`user.authenticationSource = IdentityClaims.sourceId()` — the label is
supplied by the active store (no longer a hardcoded `"ldap"`). The
stamping and persistence happen in `DefaultUserProvisioner.provision()`
via `operator.storeAndRemove` (PRD 050 Phase 8), not inline in the auth
service. The marker drives every downstream "is this an external user?"
gate:

- `RemoteStorageController.changePassword` / `changeName` /
  `changeEmail` / `confirmEmail` return 401 with the IdP name in the
  message — even for the user themselves (PRD 050).
- `GET /api/storage/profile/capabilities` returns all-false +
  `authenticationSource` for stamped users; for local users
  `canChangeName` / `canChangeEmail` are true, `canChangePassword`
  reflects `operator.canChangePassword()` (may be false for some stores)
  and source is null. The Angular SPA / Swing client toggles its
  self-edit UI off this.
- `POST /api/storage/user/{id}/disconnect-external-auth` is admin-only,
  idempotent, and clears the marker — used when an external IdP is
  decommissioned or a user needs to revert to local password auth.

The Swing user-edit panel (`UserEditUI$AuthenticationSourceField`)
displays `"Local"` when the marker is null and `"External: ldap"` (etc.)
otherwise; the **Disconnect** button stages the same admin action.

Mental model: the marker is **sticky** — once an external IdP has
claimed the username, subsequent logins through any store may refresh
name/email/groups (each store decides — see § "Per-store sync policy"
below) but never change the source. Only admin disconnect clears it.

### Per-store sync policy for name / email / groups

When an external login succeeds, how much of the user's local rapla
record is refreshed from the IdP depends on which code path you're on.
There are two distinct paths, and they don't share a table.

**OIDC / browser path — `ExternalUserResolver`.** This resolver is *not*
an `AuthenticationStore`; it's the OIDC-login resolver that maps an
external IdP token onto a rapla User (see § "External IdP login"). It
holds no password — the IdP does. Email and name are authoritative
(overwrite every login when the IdP value differs, case-insensitive);
groups stay rapla-managed (not synced).

**Password-grant path — the single `AuthenticationStore` +
`DefaultUserProvisioner`.** Here the field-sync policy lives in the
single `DefaultUserProvisioner`, whose **default** policy is:

- Name / email: overwrite when the IdP value differs
  (`equalsIgnoreCase` compare).
- Groups: applied only on new-user creation.

Plugins override via `shouldOverwriteName` / `shouldOverwriteEmail` /
`resolveGroups`. dhbwrapla supplies a `DhbwUserProvisioner extends
DefaultUserProvisioner` (the sync no longer lives inline in
`DhbwNtlmAuthStore`, which only returns the bare identity blob). Its
**current** policy, verified against `~/git/dhbwrapla`:

- **Name** — never written: `DhbwNtlmAuthStore.extractClaims` returns
  `displayName = null`, and `DefaultUserProvisioner` skips the name write
  when displayName is null. (The legacy `shouldOverwriteName` override is
  commented out / dead.)
- **Email** — the legacy "only-when-empty" override is **commented out**;
  the default **overwrite-on-mismatch** (`equalsIgnoreCase`) now applies.
  The value is the IdP-provided email when present, else the Standort
  email derived by `DhbwLdapGroupMapper`. PRD 050 makes this safe: name /
  email change is 403-gated for any user with a non-null
  `authenticationSource`, so the AD/IdP side is structurally authoritative.
- **Groups** — re-derived from the DHBW LDAP role mapping on every login
  (`resolveGroups` uses the claims-provided keys exclusively, ignoring
  vanilla's `JNDIPlugin.USERGROUP_CONFIG` fallback). A login whose mapping
  yields **no** groups is **refused** with a Standort error unless the
  user is already an admin or already has groups.

Two consequences worth remembering:

1. **The DHBW side is authoritative under PRD 050.** Because name / email
   change is 403-gated for stamped users and `DhbwUserProvisioner` runs the
   default overwrite-on-mismatch policy, an org-wide email-domain rename in
   AD *is* pushed on the next login — there is no stale-local-value to clear.
   (A deployment that genuinely needs preserve-local-on-empty semantics
   would un-comment the `shouldOverwriteEmail` override in
   `DhbwUserProvisioner`.)
2. **All email comparisons are
   `equalsIgnoreCase`** — same-domain mailboxes that just change case
   never trigger spurious saves (and never hit the optimistic-lock /
   delete-then-insert path that would otherwise cause a PK violation on
   no-op updates).

### Error-message contract on failed auth

The OAuth2 password-grant authentication provider
(`AuthorizationServerConfig.raplaAuthenticationProvider`) distinguishes
two failure modes:

| Java exception thrown by `getUserFromCredentials` | Spring exception | OAuth2 error code | What it means |
|---|---|---|---|
| `RaplaSecurityException` (or no user returned) | `BadCredentialsException` | `invalid_grant` | Real auth failure — wrong password, disabled user, external store rejected |
| Any other `Exception` (DB error, NPE, etc.) | `InternalAuthenticationServiceException` | `server_error` (mapped) | Credentials were accepted but a downstream server step failed — the catch block logs the actual cause at ERROR with stacktrace; check `logs/rapla.log` for the real reason |

Never collapse the latter into "invalid username/password" — that
misleads users into retyping a valid password and hides actionable
server-side errors (DB rollbacks, permission-store gone, etc.).

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

### Angular SPA — server-brokered cookie session

The SPA holds no OAuth client; `AuthService.redirectToLogin()` does a full
navigation to the server-rendered `/login` chooser. The server runs the
OAuth / form login and sets httpOnly `access_token` + `refresh_token`
cookies. The SPA reads identity from `GET /api/auth/me`, refreshes via
`POST /api/auth/refresh` (`AuthCookieController`), and signs out via
`POST /api/auth/logout`. No PKCE / no `/oauth2/authorize` / no
`/auth/callback` in the SPA (PRD 072).

#### Reactive-401 refresh must not be tied to a request subscription

`auth.interceptor.ts` does the reactive refresh: on a 401 from `/api`,
it calls `POST /api/auth/refresh` once and replays the original request,
sharing one in-flight refresh across all concurrent 401s (stampede
guard). **The shared refresh is a module-global, eagerly-subscribed
`Observable` (`shareReplay(1)` + `finalize`-reset) — deliberately
decoupled from any single request's subscription.**

This is load-bearing, not incidental. An earlier version owned the
refresh inside the triggering request's `from(doRefresh()).pipe(switchMap(...))`
chain and tracked progress in a module flag (`isRefreshing`) reset only
inside that `switchMap`. If the request that hit the 401 was torn down
*mid-refresh* — and `omnibox.component.ts` cancels its search request on
every keystroke via `switchMap`, route changes / tab freeze do the same —
the reset `switchMap` never ran, the flag stuck `true`, and every later
401 waited forever on an outcome that never came. Symptom: after a long
idle (access token expired, refresh token still valid for its 21 d), the
first interaction 401s and the SPA hangs; **only a full page reload (fresh
JS context) clears it.** The fix makes the refresh a self-driving worker
that completes and resets regardless of who unsubscribes. Regression
test: `auth.interceptor.spec.ts` → "recovers when the refresh-owning
request is torn down mid-refresh". Don't reintroduce a refresh whose
lifecycle hangs off the request observable.

### Swing client — default OAuth, fallback password dialog

`RaplaClientServiceImpl.startLoginInThread()` drives Swing login:

1. Builds a `LoginDialog` (username/password fields + an OAuth button) and
   probes discovery via `fetchOauthConfig()`.
2. **Default — OAuth enabled, `swing-legacy-login=false`**: the dialog
   shows in "browser login in progress" mode (credential fields hidden)
   and auto-fires `SwingOAuthLoginFlow` — the standard authorization_code
   + PKCE flow against rapla's `/oauth2/token` (PRD 029 Phase 2). Since
   PRD 072 this is the **single "SSO" entry** — rapla brokers the upstream
   IdP via the `/login` chooser (see § "Swing SSO flow" below); there is no
   per-provider Swing menu. The credential fields are never shown.
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
`POST /oauth2/token grant_type=refresh_token`
(`MyCustomConnector.refreshUsingToken(...)`,
`ClientProxyConfig.RefreshOn401Interceptor.doRefresh(...)`, PRD 041).

#### Swing SSO flow — rapla as the single federating IdP (PRD 072 Phase 5)

Since PRD 072, Swing has **one** "SSO" login entry (the default) instead of
the old per-provider menu — rapla is Swing's single federating Authorization
Server (broker). The flow:

1. Swing opens the system browser to rapla `/oauth2/authorize` (its own
   loopback `authorization_code` flow, unchanged).
2. rapla renders the `/login` **chooser** (the per-provider buttons live on
   the page now, not in the Swing menu); the user picks an upstream IdP.
3. rapla federates that IdP **server-side** via `oauth2Login()`, then
   completes its **own** `authorization_code` back to Swing's loopback as a
   **rapla** Bearer (rapla-issuer, `sub=UUID`).

Because Swing now only ever talks to rapla, **all** refresh hits rapla's
`/oauth2/token` with `client_id=rapla-client` — the per-provider refresh
routing (`refreshUrl`/`oauthClientId` on `RemoteConnectionInfo`, the
Keycloak-BFF branch in `RefreshOn401Interceptor.doRefresh` +
`MyCustomConnector.refreshUsingToken`, `TokenStore.KEY_REFRESH_URL` /
`KEY_OAUTH_CLIENT_ID`) was **removed** (significant net code deletion).
`ConnectInfo` collapsed to the two-token form. The Swing login dialog
defaults to SSO and remembers the last-used method via
`TokenStore.KEY_LOGIN_METHOD`; the rapla password form stays available,
gated by `rapla.oauth.swing-legacy-login`.

Two bugs were fixed (verified PRD 072) to make rapla-brokered SSO work:

- **`OidcLoginSuccessHandler` re-authenticates the `SecurityContext` as the
  rapla user** (UUID principal, mirroring `raplaAuthenticationProvider`'s
  password-grant convention) before the Authorization Server issues the
  loopback code/tokens. Without it, the loopback `authorization_code` carried
  `sub=` the external OIDC username, which the rapla token generators + `/api`
  (which resolve `sub` as a rapla UUID) cannot resolve → `invalid_grant`.
- **`OidcLoginSuccessHandler` is a `SavedRequestAwareAuthenticationSuccessHandler`**,
  so the Swing `/oauth2/authorize` saved request is **resumed** after the
  upstream federation completes — instead of dumping the browser into `/app`.

#### CLI bootstrap (dev / CI)

The Swing client's CLI launcher (`SpringRaplaClient.main`) accepts a single
API token JWT as the only CLI arg:

```bash
# Get a JWT once (do this in the browser via the Scalar UI at /scalar):
# POST /api/auth/api-keys { "label":"dev-cli", "expiresInDays":365 }
# Copy the "key" field from the response.

export RAPLA_DEV_TOKEN="eyJhbGciOiJSUzI1NiIs..."

mvn exec:java -Dexec.daemonThreadJoinTimeout=86400000 \
    -Dexec.args="$RAPLA_DEV_TOKEN" \
    -Dexec.mainClass=org.rapla.client.spring.SpringRaplaClient
```

No args → dialog. With one arg → treats it as an access token (no refresh —
API keys carry their own `exp`). Pre-Phase-5 the CLI accepted
`-Dexec.args="admin password"`; that path is gone. To use the default empty-
password `admin` account once for bootstrap, launch with no args and type
`admin` + blank password in the dialog, then mint a key for future launches.

### Swing fallback password dialog → OAuth2 password grant

When the fallback dialog is used, `loginAction` (`RaplaClientServiceImpl.startLoginInThread`)
takes the typed username + password `char[]` and exchanges them for tokens via
`OAuth2PasswordLogin.login(LoginCredentials)` *inline at the dialog button*
(PRD 029 Phase 5). The password `char[]` is zeroed in the same lambda that
calls the seam, so no password reference survives past the HTTP round-trip.
Only the resulting `(accessToken, refreshToken)` are stashed on the session.

`OAuth2PasswordLogin` (rapla-core) POSTs `grant_type=password` to
`/oauth2/token` (RFC 6749 §4.3) and parses the snake_case token response into
a `LoginTokens`.

> **PRD 029 Phase 5 (2026-05-25): no password caching anywhere.** Pre-Phase-5,
> the dialog built a `ConnectInfo(username, password, connectAs)` polymorphic
> object that propagated through `start() → login() → dispatch` and was stashed
> in `RemoteConnectionInfo.connectInfo` for the session's lifetime — keeping
> the cleartext password reachable from a heap dump until logout. Phase 5
> slimmed `ConnectInfo` to tokens only (`accessToken` + `refreshToken`),
removed the
> polymorphic dispatch from every layer, and confined password handling to
> (a) the dialog Login button, and (b) the `OAuth2PasswordLogin.login()` seam
> itself. The `RemoteAuthentificationService` interface (with one method, one
> impl, no plugin point) was deleted; the impl was promoted to the top-level
> concrete class `OAuth2PasswordLogin` in rapla-core.
>
> The test-bootstrap entry point `ClientFacade.login(String, char[])` was
> replaced with `ClientFacade.connect(ConnectInfo)`. Integration tests
> (`SwingClientStartIntegrationTest`, `HeadlessClientNameResolutionIntegrationTest`)
> mint a JWT server-side via `RefreshSessionService.issueAndPersist(homer)`
> and pass it to `facade.connect(info)` — zero password references in test
> fixture code.
>
> `MyCustomConnector.reauth()` also lost its password-fallback path; mid-session
> reauth is now refresh-token-only for both password and OAuth sessions. When
> the refresh token is dead, the user sees the re-login dialog — same UX
> Keycloak users already had.
>
> The dialog's `" su "` impersonation shorthand was dropped — the OAuth2
> password grant has no `connect_as` parameter; modern admin "switch to user"
> is the dedicated `/api/auth/impersonate` endpoint with dual-slot client
> state (PRD 051 + Phase 5 §7).
>
> The legacy `?username=...&password=...` request-param branch in
> `RemoteSessionImpl.extractUser` was dropped as dead — confirmed via audit
> that nothing in the codebase sends those params for auth. The
> `?access_token=...` query param + `raplaLoginToken` cookie + `Authorization: Bearer`
> header branches remain (used by external iCal subscribers + legacy browser
> session login).

**Legacy JAX-RS / `raplaLoginToken` cookie — cleanup status.** The pre-Spring-Boot
JAX-RS `RaplaAuthRestPage` (`@Path("login")`) + its `AuthController`, and the sibling
`RaplaEventsRestPage` / `RaplaResourcesRestPage` / `RaplaDynamicTypesRestPage`, are
**all gone** — JAX-RS is fully removed from the reactor (`ApiPrefixArchitectureTest`
bans any `jakarta.ws.rs.*` import, AGENTS.md §15). The only legacy remnant is
`RemoteSessionImpl`'s `raplaLoginToken` cookie **read** branch — its own
`LOGIN_COOKIE` constant, one of the identity-resolution branches alongside the
`Authorization` header and the URL-embedded `?access_token=` (external iCal
subscribers). **Nothing in the reactor sets that cookie anymore**, so the branch is
read-only legacy (a candidate for removal once confirmed no deployed client relies
on a pre-existing `raplaLoginToken` cookie).

### Auth endpoint reference

| Path | Status | Handler |
|---|---|---|
| `POST /login`, `POST /logout` (form/session) | live | Spring Security filters; `LoginPageController` renders the page |
| `GET /oauth2/authorize`, `POST /oauth2/token`, `POST /oauth2/revoke` | live | Spring Authorization Server (`AuthorizationServerConfig`) |
| `POST /oauth2/token grant_type=password` | live | `PasswordGrantAuthenticationConverter` + `…Provider` |
| `GET /api/auth/oauth/config`, `POST /api/auth/oauth/exchange/{id}` | live | `OAuthConfigController`, `OAuthExchangeController` |
| `POST`/`GET`/`DELETE` `/api/auth/api-keys[/{id}]` | live | `ApiKeyController` |
| `POST /api/auth/impersonate` | live (PRD 051) | `ImpersonationController` — mints impersonation access token (`act` claim, no refresh) |
| `GET /api/users` | live (PRD 051) | `UsersController` — narrow `{username, displayName}[]` filtered by `canAdminUser`; typeahead source for the "Switch to user" dialog |
| `POST /api/auth/login` (old `AuthController`) | **removed** — PRD 041, commit `d64e8553` | none — no replacement route |
| `POST /api/auth/refresh`, `/api/auth/logout` | **live** (PRD 072) | `AuthCookieController` — cookie-model refresh + logout |
| `POST /api/auth/oauth/token-exchange/{id}` | live (PRD 072) | `OAuthExchangeController` — RFC 8693: external id_token → rapla token |

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
| `POST` | `/api/auth/api-keys` | `{id, label, alg, thumbprint, createdAt, expiresAt, scopes, key}` — `key` is the JWT, shown once. Body may carry `scopes` (default `["read"]`). **Rejected for api-key principals (D10).** |
| `GET`  | `/api/auth/api-keys` | `[{id, label, alg, thumbprint, createdAt, expiresAt, scopes}, …]` — no key material |
| `POST` | `/api/auth/api-keys/{id}/rotate` | `{…, scopes, key}` — same shape as create. Self-rotation; see below |
| `DELETE` | `/api/auth/api-keys/{id}` | `204 No Content` (idempotent) |

`POST`/`GET`/`DELETE` require a valid **access-token** `Bearer` (you must be logged
in to manage your own keys) — an api-key principal is **not** allowed to mint keys
via `POST` (D10). `rotate` is the exception: it is called **by the api-key itself**
and is the only key-management action an api-key can perform (and only on itself).
The API key itself becomes usable on **every** authenticated rapla endpoint once
minted, subject to its data scope on writes (see below).

### Scopes + self-rotation (PRD 076)

Each key carries a **scope set** that bounds the blast radius of a leak. Two axes:

| Kind | Scopes | Governs |
|---|---|---|
| **Data** | `read`, `write_events`, `write_resources`, `write_all` | what the key may read / mutate. `write_*` implies read |
| **Management** | `rotate_self` | may the key rotate itself (issue a same-scope successor) |

- **Default for a new key is `{read}`** (least privilege); any write/`rotate_self` scope is
  explicit opt-in. An **existing** key minted before PRD 076 (no `scopes` field) resolves to
  `write_all` — behaviour-identical to before, non-breaking.
- **Write enforcement** is at the operator chokepoint (`LocalAbstractCachableOperator.check`),
  so it covers REST and GraphQL uniformly: events (Reservation/Appointment) need `write_events`
  or `write_all`; resources (Allocatable) need `write_resources` or `write_all`; anything else
  (User, DynamicType, …) needs `write_all`. A denied write returns the SAME 401 a permission
  denial does — indistinguishable.
- **Self-rotation** (`POST /{id}/rotate`, gated by `rotate_self`): mints a **same-scope**
  successor (never escalates), returns it once, and sets a short server-side **grace TTL** on the
  old key (`?graceSeconds=`, default 300, `0` = immediate). The old key keeps working for the
  grace window then expires — bounded overlap without an indefinite dual-key phase. A key may
  rotate **only itself** (the `{id}` must equal the caller's `kid`).
- **Server can only tighten expiry, never extend it (D9):** the effective expiry is
  `min(jwt.exp, stored.exp)`. The signed JWT `exp` is the hard ceiling; shortening the stored
  `exp` (what `rotate` does) can pull it earlier. A missing stored `exp` is ignored — a legacy
  never-expiring key keeps working.

> **Known gap:** api-key Bearer tokens can't drive GraphQL **mutations** yet — those resolvers
> resolve the caller by `preferred_username`, which api-key JWTs don't carry (only `sub`). REST
> writes work. The scope enforcement is already uniform at the operator seam, so it applies to
> GraphQL automatically once caller-resolution there is fixed by subject.

### Storage layout

Stored under the existing `RaplaKeyStorage.storeAPIKey` API — same
prefs slot the legacy refresh-token mechanism uses, now actually
multi-slot. One slot per registered key, keyed by the public JWK's
RFC 7638 thumbprint:

```json
{
  "refreshToken": "<the user's session refresh JWT — single slot, see above>",
  "<thumbprint-1>": "{\"kid\":\"…\",\"jwk\":\"…public-JWK…\",\"alg\":\"RS256\",\"label\":\"CI deploy\",\"iat\":…,\"exp\":…,\"scopes\":[\"read\"]}",
  "<thumbprint-2>": "…"
}
```

Note what's **not** stored: the JWT itself, the signature, the private
key. Only the public JWK plus listing metadata. The `scopes` and `exp`
fields in each entry are **authoritative** — the decoder reads them on
every request (not the signed JWT's copy), which is what lets the server
migrate scopes and shorten expiry (`rotate`) without re-minting the key.

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
| `GET /api/auth/oauth/config` | **Unchanged** — discovery endpoint stays | unchanged |
| `POST /api/auth/oauth/exchange/{providerId}` | **Changed** — still the BFF for external IdPs, but as of PRD 072 it re-mints a rapla session token rather than brokering the raw external token | unchanged |
| (none) | `POST /api/auth/oauth/token-exchange/{providerId}` (PRD 072 — external `id_token` → rapla token, RFC 8693) | new |
| (none) | `POST /api/auth/api-keys` (mint), `GET /api/auth/api-keys` (list), `DELETE /api/auth/api-keys/{id}` (revoke) | new — PRD 043 |

Note: `POST /api/auth/refresh` and `POST /api/auth/logout` are **not**
removed — as of PRD 072 these paths exist again as the SPA
cookie-credential endpoints (`AuthCookieController` / `AuthCookieService`),
distinct from the old PRD-041 Bearer-JSON forms. The OAuth
password / refresh / revoke flow via `/oauth2/*` remains for Swing / API
clients.

The `refreshUrl` field is **gone** from the discovery response; clients
use `tokenUrl` for both initial code exchange and subsequent refresh
(OAuth standard).

## Quick start (default deployment)

Defaults are correct for a single rapla server at `https://rapla.yourdomain.com`:

```bash
java -jar rapla-2.1-SNAPSHOT.jar
```

The Swing client's "Sign in with browser…" button works out of the
box. End-users log in via the form at `/login`, the auth server
redirects to a loopback URL on their machine, and the Swing client
completes the token exchange. No per-deployment OAuth config required.

The Angular SPA at `/app/` uses the conformant redirect
`/login/oauth2/code/{registrationId}`; the legacy DHBW callback
`/app/auth/callback` is supported behind the
`rapla.oauth.web.dhbw-legacy-callback` flag via
`LegacyKeycloakCallbackBridgeFilter` — also zero-config.

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
| `rapla.oauth.scopes` | `RAPLA_OAUTH_SCOPES` | `openid,profile,offline_access` | Scopes advertised in the discovery payload (`/api/auth/oauth/config`) that OAuth-aware clients request. Comma-separated. (The Spring AS registration grants `openid`, `profile`, `offline_access`.) |
| `rapla.oauth.swing-legacy-login` | `RAPLA_OAUTH_SWING_LEGACY_LOGIN` | `true` | When `true`, the Swing client shows the legacy username/password dialog instead of auto-firing the browser OAuth flow. Use it to keep end-users on the familiar dialog during an OAuth rollout. The browser flow still works from the dialog when the SSO button is also enabled (below). (The bare `@Value` fallback in `OAuthConfigController` is `false`, but `application.yml` overrides it to `true` — the effective runtime default.) |
| `rapla.oauth.swing-legacy-show-sso-button` | `RAPLA_OAUTH_SWING_LEGACY_SHOW_SSO_BUTTON` | `true` | Only effective when `swing-legacy-login=true`. Adds a "Sign in with browser…" button to the legacy dialog so willing users can opt into testing SSO without it being forced on everyone. (Effective `application.yml` default is `true`; the bare `@Value` fallback is `false`.) |
| `rapla.oauth.allow-wsl-bridge-redirects` | `RAPLA_OAUTH_ALLOW_WSL_BRIDGE_REDIRECTS` | `true` | **Dev-only**: accept any port for redirect URIs in `172.16.0.0/12` (Hyper-V WSL2 bridge). Lets developers run the Swing client in WSL2 without enabling mirrored networking. **Set to `false` in production.** |
| `rapla.oauth.allow-same-origin-redirects` | `RAPLA_OAUTH_ALLOW_SAME_ORIGIN_REDIRECTS` | `true` | Accept any redirect URI whose scheme/host/port match the public origin (honoring `X-Forwarded-*`), path gated by the `same-origin-callback-paths` allowlist. Lets a prod deployment auto-accept its own `/app/auth/callback` without registering it. Safe with PKCE — recommend keeping on. |
| `rapla.oauth.public-base-url` | `RAPLA_OAUTH_PUBLIC_BASE_URL` | *(empty)* | Origin the SPA / Swing call for the OAuth2/OIDC endpoints (authorize, token, jwks, userinfo, end-session); returned by `/api/auth/oauth/config`. Empty → falls back to the request-derived (same-origin) origin. Set to the IdP origin for external IdPs, or `http://localhost:8051` for the dev `ng serve` proxy split. |
| `rapla.oauth.trust-external-issuers` | `RAPLA_OAUTH_TRUST_EXTERNAL_ISSUERS` | `false` | PRD 072 Phase 6 single-issuer cutover. When `false`, `/api` trusts **only** rapla-issued tokens; external IdP tokens are consumed once at login and re-minted. Set `true` only as a transitional escape hatch to restore legacy multi-issuer acceptance (wires `IssuerAwareJwtDecoder`). (Commented out in `application.yml` — the effective default is the `JwtConfig` `@Value` fallback `false`.) |
| `rapla.oauth.allow-loopback-redirects` | `RAPLA_OAUTH_ALLOW_LOOPBACK_REDIRECTS` | `true` | Accept `127.0.0.1` / `[::1]` redirect URIs at any port (RFC 8252 §7.3), gated by the `same-origin-callback-paths` allowlist. (Also listed above under validation order.) |
| `rapla.oauth.same-origin-callback-paths` | *(list)* | `[/login/oauth2/code/rapla, /app/auth/callback]` | Single source-of-truth path allowlist consumed by **both** the WSL-bridge and same-origin redirect validators. |
| `rapla.oauth.web.dhbw-legacy-callback` | `RAPLA_OAUTH_WEB_DHBW_LEGACY_CALLBACK` | `false` | **Dev-only** (PRD 072 dev bridge for the DHBW Keycloak prod realm — no admin access to register conformant redirect URIs). When `true`: the keycloak `ClientRegistration` sends the registered `/app/auth/callback` `redirect_uri`, and `LegacyKeycloakCallbackBridgeFilter` server-side-redirects `/app/auth/callback` → `/login/oauth2/code/keycloak`. Must be server-side because the outgoing `redirect_uri` is built server-side in `RaplaClientRegistrationConfig`. Not for production. (Lives in `application-local.yml`, not committed `application.yml`.) |

### Spring redirect URIs

The path allowlist lives under `rapla.oauth.same-origin-callback-paths`
(a dedicated list). The
`spring.security.oauth2.authorizationserver.client.rapla-client.registration.redirect-uris`
entry is now a single non-matched placeholder. The same-origin / WSL /
loopback validators match a candidate URI's **path** against
`same-origin-callback-paths`. The two real allowlisted paths are
`/login/oauth2/code/rapla` (Swing) and `/app/auth/callback` (SPA).

#### Validation order

1. **Loopback validator** (rapla custom, default on via
   `rapla.oauth.allow-loopback-redirects=true`) — accept `127.0.0.1` /
   `localhost` / `::1` on any port (RFC 8252), provided its **path**
   matches one of the registered callback paths.
2. **Same-origin validator** (rapla custom, default on via
   `rapla.oauth.allow-same-origin-redirects=true`) — accept any URI
   whose scheme/host/port match the auth-server request's public
   origin (honoring `X-Forwarded-*`), provided its **path** matches
   one of the registered paths.
3. **WSL bridge validator** (rapla custom, default on via
   `rapla.oauth.allow-wsl-bridge-redirects=true`) — dev convenience
   for hosts in `172.16.0.0/12`.

(The Spring AS default exact-match validator still runs against the
placeholder `redirect-uris` entry, but it never matches a real
deployment URI — the three custom validators above carry the load.)

#### Production override — almost never needed

A deployment at `https://rapla.yourdomain.com` works out of the box.
Since PRD 072 Phase 4 the SPA no longer computes its own OAuth redirect:
login is the server-rendered `/login` page (`oauth2Login` +
`OidcLoginSuccessHandler`), and the OAuth redirect URI is the conformant
`/login/oauth2/code/{registrationId}`. The same-origin validator matches
that path against `same-origin-callback-paths` and accepts.
`/app/auth/callback` persists only as an explorer-shared allowlist entry
(no longer the SPA's primary login path).

The only time you need to redefine `redirect-uris` is for a path that
isn't registered — e.g. an explorer hosted on a separate origin from
rapla itself. Two safe ways:

```bash
# A. SPRING_APPLICATION_JSON — atomic full redefinition
export SPRING_APPLICATION_JSON='{"spring":{"security":{"oauth2":{"authorizationserver":{"client":{"rapla-client":{"registration":{"redirect-uris":["https://rapla.example.com/auth/callback","https://rapla.example.com/app/auth/callback","https://explorer.example.com/oauth/callback"]}}}}}}}}'
```

```yaml
# B. application-prod.yml overlay (activate with SPRING_PROFILES_ACTIVE=prod)
spring:
  security:
    oauth2:
      authorizationserver:
        client:
          rapla-client:
            registration:
              redirect-uris:
                - https://rapla.example.com/auth/callback
                - https://rapla.example.com/app/auth/callback
                - https://explorer.example.com/oauth/callback
```

**Do not use indexed env vars** like
`SPRING_..._REDIRECT-URIS_3=…`. Spring Boot list-binding resolves
each index from the highest-priority property source — so an indexed
override silently **overwrites** the YAML entry at that index rather
than appending. (The `redirect-uris` list is now a single placeholder
entry anyway; the real allowlist is
`rapla.oauth.same-origin-callback-paths`, a flat list bound the same
way and subject to the same hazard.) Appending without breakage would
require knowing the list's current length and choosing the next free
index, which drifts as the config evolves.

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

## Group administration policy

Rapla has two layers of admin authority — a **global admin** flag on
the User entity, and a **per-group admin** mechanism keyed off a
category annotation. Both feed into the same authorization rule,
[`PermissionController.canAdminUser`](../rapla-core/src/main/java/org/rapla/storage/PermissionController.java)
(in `rapla-core`):

```java
public static boolean canAdminUser(User adminUser, User target) {
    if (adminUser.isAdmin()) return true;                       // global admin → all
    if (getAdminGroups(adminUser).isEmpty()) return false;      // not a group-admin → none
    if (target.isAdmin()) return false;                         // group-admin can't admin global admin
    for (Category scope : getGroupsToAdmin(adminUser)) {
        if (target.belongsTo(scope)) return true;
    }
    return false;
}
```

This rule governs every admin operation: changing another user's
password, switching to a user, deleting / disabling another user,
editing another user's preferences, etc.

### Layer 1 — global admin

`User.isAdmin() == true` on the User entity. A global admin can
admin every other user, including another global admin. There is
always at least one global admin in the system (the bundled `admin`
account, on a fresh deployment).

### Layer 2 — group admin via `can_admin_parent` annotation

A `Category` carries a `can_admin_parent="true"` annotation
([`CategoryAnnotations.CAN_ADMIN_PARENT`](../rapla-core/src/main/java/org/rapla/entities/CategoryAnnotations.java)).
Any user whose group list contains that category is treated as
**admin of the category's parent**, and transitively of every user
whose group list belongs (transitively) to that parent.

Worked example — suppose the category tree is:

```
/groups
  /groups/department-1
    /groups/department-1/admins        ← annotation: can_admin_parent=true
    /groups/department-1/students
  /groups/department-2
    /groups/department-2/admins        ← annotation: can_admin_parent=true
    /groups/department-2/staff
```

Then:

| User | Group list | Effect |
|---|---|---|
| `alice` | `[/groups/department-1/admins]` | Admin of `/groups/department-1` (the parent of `admins`). Can admin every user in department-1, including students and fellow admins. Cannot admin anyone in department-2. |
| `bob` | `[/groups/department-1/students]` | Not a group-admin (the student category has no `can_admin_parent` annotation). Cannot admin anyone. |
| `carol` | `[/groups/department-1/admins, /groups/department-2/admins]` | Admin of both `/groups/department-1` AND `/groups/department-2`. Effectively two scopes. |

### Setting up group admins via the Swing client

1. Open the **resource editor** in the Swing client (admin only).
2. Navigate to the `Groups` / `Categories` tree (left pane).
3. Right-click the group whose members should be admins of its
   parent — e.g. `/groups/department-1/admins` — and choose
   *Edit annotations*.
4. Add an annotation: key `can_admin_parent`, value `true`. Save.
5. Add users to that group via the user editor. They become group
   admins of the parent on next login.

### How `belongsTo` transitivity works

`User.belongsTo(Category group)`
([`UserImpl.java`](../rapla-core/src/main/java/org/rapla/entities/internal/UserImpl.java))
returns true when **any** of the user's groups equals `group` *or* is
a descendant of `group`, walking the category tree via
`getGroupsIncludingParents`. So a user in
`/groups/department-1/students` `belongsTo(/groups/department-1)`
returns true — which is why a department-1 admin can admin students
in the department, not just other admins.

### Implications for OAuth / external IdPs

**The scoping information lives entirely in rapla data.** Keycloak
(or Entra, or Google) sees no rapla categories, no `can_admin_parent`
annotations, no admin-scope tree. A Keycloak realm can't model rapla
group-admin scoping at all — its `impersonate-users` permission is
realm-global, all-or-nothing.

That means **every operation gated by `canAdminUser` must run in
rapla code**, including impersonation, admin-resets-password, and
admin-disables-user. Even when the deployment migrates to Keycloak
for primary auth, these admin operations stay rapla-server-side.
See [PRD 051](prd/done/051-switch-user-with-oauth.md) § "Rapla's
group-administration policy — the authorization rule" for the
specific implication for the "switch to user" feature.

## Admin impersonation ("switch to user")

Admins can temporarily act as another user to troubleshoot
"why can't user X see resource Y" issues. The feature is invoked
from the Swing client's user editor (right-click a user → *Switch to
user*) and, once the SPA admin surface lands, from the Angular UI.
Authorization runs through
[`PermissionController.canAdminUser`](../rapla-core/src/main/java/org/rapla/storage/PermissionController.java)
— global admins can impersonate anyone, group admins can impersonate
users whose group list intersects their `can_admin_parent` scope
(see § "Group administration policy" above).

### Wire format

```
POST /api/auth/impersonate                 HTTP/1.1
Authorization: Bearer <admin's current access token, any issuer>
Content-Type: application/x-www-form-urlencoded

target_username=alice
```

```
HTTP/1.1 200 OK
Content-Type: application/json

{
  "access_token": "<rapla-SAS-signed JWT, sub=alice's UUID, act={sub: admin UUID, username: admin}>",
  "token_type":   "Bearer",
  "expires_in":   3600
}
```

| Field / behaviour | Value |
|---|---|
| Path | `/api/auth/impersonate` |
| Auth required | Yes — any rapla-accepted Bearer (rapla-SAS, Keycloak, Entra, Google) |
| Authorization | `canAdminUser(actor, target)` — runs in rapla code against rapla data |
| Issued by | rapla-SAS (always, regardless of which IdP the actor authenticated with) |
| TTL | 1 h — a hardcoded `ImpersonationController.IMPERSONATION_TTL_SECONDS = 3600L` constant (chosen to match the OAuth2 access-token TTL; **not** sourced from `access-token-time-to-live`, which governs the `/oauth2/token` access-token TTL) |
| **No `refresh_token`** | Renewal is via repeat call to the same endpoint with the admin's Bearer |
| `act` claim | `{sub: <admin UUID>, username: <admin>}` per RFC 8693 delegation semantics — see § "Access-token claim shape" for the rest of the JWT |

Failure codes:

| HTTP | Cause |
|---|---|
| 401 | No Bearer presented (or Bearer doesn't resolve to a rapla user) |
| 403 | Actor authenticated but `canAdminUser(actor, target)` is false |
| 404 | `target_username` is not a known rapla user |

### Audit log

Every successful issuance — initial and every renewal — emits one
INFO line to the standard rapla log:

```
INFO  rapla - Impersonation: actor=admin (uuid=u5d2ad2b-…) target=alice (uuid=uc0fc61a-…)
```

The audit trail is the renewal cadence: an hour-long impersonation
session produces one initial line plus one renewal line per hour.

### What's NOT stored anywhere

The 2026-05-21 design (PRD 051) deliberately ships *zero* impersonation
state outside the issued JWT itself. PRD 029 Phase 5 (2026-05-25) wires
the client-side dual-slot model so the in-memory storage matches the
Angular SPA's pattern:

| | Stored where? |
|---|---|
| Impersonation **refresh** token | **Nowhere.** Not issued. |
| Impersonation **access** token (Swing) | In-memory only on `RemoteConnectionInfo.impersonationAccessToken` — a sidecar slot distinct from admin's `accessToken`. Discarded on client cold restart, on logout, and on switch-back. |
| Impersonation **access** token (Angular) | **Not client-side.** Since PRD 072 Phase 4 the SPA holds no token; impersonation state is server-side, surfaced via `GET /api/auth/me` (`{impersonating, actor, target}`). The `access_token` is an HttpOnly cookie JS cannot read. |
| Admin's password | Never stored anywhere by the impersonation feature (Phase 5 removed all password caching — the admin's refresh token is the only credential needed for renewal) |
| Per-impersonation server-side session record | **None.** Each `/api/auth/impersonate` call is independent; authorization is re-checked from scratch |

**Bearer model — the two clients diverge:**

The Swing client still uses the two-slot bearer model (admin slot +
impersonation sidecar slot on `RemoteConnectionInfo`). The Angular SPA
(PRD 072 Phase 4) has **one cookie credential, not two slots** — admin
vs impersonation identity is tracked server-side and read from
`GET /api/auth/me`; there is no client-held token.

| | Swing (`RemoteConnectionInfo`) |
|---|---|
| Admin's access token | `accessToken` |
| Admin's refresh token | `refreshToken` |
| Effective bearer (override if set, else admin) | `getEffectiveAccessToken()` |
| Admin's bearer always (ignore override) | `adminToken()` |
| Impersonation token | `impersonationAccessToken` |
| Impersonation target | `impersonationTargetUsername` |
| Is impersonating? | `isImpersonating()` |

(The Angular surface is now: `identity` signal, `isImpersonating`
computed, `username` / `actorUsername` computed, `loadIdentity()` via
`GET /api/auth/me`, `impersonate()` via `POST /api/auth/impersonate/switch`,
`endImpersonation()` via `POST /api/auth/impersonate/end`. The old
`token()` / `adminToken()` / `impersonationOverride()` methods no longer
exist.)

The **Swing** interceptor (`MyCustomConnector.reauth`) handles 401 by
trying impersonation renewal via `adminToken()` first; on failure it
refreshes admin's tokens then retries; on total failure it shows the
re-login dialog. The renewal path **never** uses the
impersonation token to authenticate the impersonation endpoint itself
(server rule — see § "Switching from one target to another
mid-impersonation").

If an admin loses their group-admin rights mid-session (e.g. an
admin removes them from `/groups/department-1/admins`), the next
hourly renewal returns 403 and the impersonation ends within
~1 hour — `canAdminUser` is fresh-evaluated each call.

### Renewal model

Impersonation tokens are short-lived (1 h matching rapla's standard
access TTL) and there is no impersonation refresh token.

On the **Swing** client, when the impersonation token nears expiry the
next API call gets 401, and the client (`RefreshOn401Interceptor` +
`MyCustomConnector.reauth`) calls `/api/auth/impersonate` again with the
admin's current Bearer. If the admin's own access token has also expired,
the standard refresh chain (against the admin's original IdP) runs first,
then the impersonation renewal retries. Net cost: ~1 extra POST per hour
of active impersonation; the renewal is invisible to the user (no UI
prompt) until and unless the admin's refresh token has also expired —
then the auth-error dialog opens.

On the **Angular** SPA (PRD 072), impersonation is cookie-based: the
interceptor calls `POST /api/auth/refresh` once on a 401 (cookie
refresh), and impersonation start/swap goes through
`POST /api/auth/impersonate/switch` — there is no client-side Bearer
renewal of `/api/auth/impersonate`.

### Switch back

On **Swing**, clearing the impersonation override is the entire
operation — the admin's tokens are already in their normal slots
(`RemoteConnectionInfo.accessToken`) throughout the impersonation
lifetime. Active Bearer reverts to the admin's own on the very next
outbound request. On the **Angular** SPA, switch-back is
`POST /api/auth/impersonate/end` (`AuthService.endImpersonation()`);
the server re-mints the `access_token` cookie back to the admin.

### Switching from one target to another mid-impersonation

If an admin is impersonating user A and wants to switch to user B
(without first switching back to admin), the client UI flow is:

1. The admin clicks the user chip in the toolbar (it still shows
   "Impersonating A" — but it's still clickable while impersonating).
2. The "Switch to user" dialog opens.
3. The dialog's typeahead — `GET /api/users` — must reflect the
   **admin's** admin-scope, not A's. On Swing this means attaching the
   admin's Bearer (not the impersonation Bearer); on the Angular SPA
   (PRD 072) there is no client-held token — the request relies on the
   `access_token` cookie, and the cookie/identity model surfaces the
   admin actor server-side so `canAdminUser` runs against the admin.
4. The admin picks B; the SPA POSTs `/api/auth/impersonate/switch`
   (cookie-authenticated — the server validates the admin's cookie and
   re-mints the `access_token` cookie for B). On Swing the equivalent
   is `POST /api/auth/impersonate` with the admin's Bearer.
5. On success, the impersonation is replaced (A → B). One uninterrupted
   session.

**Authoritative rule: impersonation tokens cannot themselves invoke
the impersonation endpoints.** Specifically:

- `GET /api/users` called with an impersonation Bearer returns the
  *target's* admin-scope (usually empty for non-admin targets) —
  never the original admin's scope. On the Angular SPA (PRD 072)
  `UsersService.list()` (`rapla-angular/src/app/auth/users.service.ts`)
  no longer attaches an admin Bearer — it does a plain
  `GET /api/users` and relies on the `access_token` cookie; the
  cookie/identity model must surface the admin actor so the listing
  reflects the admin's scope during an active impersonation.
- `POST /api/auth/impersonate` called with an impersonation Bearer
  resolves the actor to the impersonated user; if that user isn't
  themselves admin/group-admin (the typical case), the server returns
  403. On the SPA the cookie-based `POST /api/auth/impersonate/switch`
  validates the admin's cookie instead.
- This is intentional: an impersonation token grants *exactly* the
  target user's permissions for the duration of the impersonation.
  It does NOT grant the admin's "switch to another user" capability
  — that authority belongs to the admin alone, and is reachable only
  through the admin's own Bearer.

Implementation cross-reference (Angular, PRD 072): the SPA holds no
token, so there is no `AuthService.adminToken()` / `renewImpersonation()`.
Impersonation switch/end go through `AuthService.impersonate()` /
`AuthService.endImpersonation()` via
`POST /api/auth/impersonate/{switch,end}`, both cookie-authenticated;
`UsersService.list()` is a plain cookie-authenticated `GET /api/users`.

### Visual indicator

Both clients show the effective target user plus an "this is an
impersonation" cue while the override is active:

| Client | Where | Visual |
|---|---|---|
| Angular SPA | Top-right of the toolbar | The user chip text is the impersonation target (not the admin's name); chip icon flips to Material `person_search` (a magnifying-glass-over-head); the right-hand action button changes from "Sign out" to "Switch back" (the two are mutually exclusive — there is no "Sign out" available while impersonating) |
| Angular SPA — admin-not-yet-impersonating | Same chip | When the admin can impersonate but isn't currently, the chip stays on the admin's name and the icon shows `swap_horiz` to signal the chip is clickable; the button remains "Sign out" |
| Swing | Right side of the menu bar | Status label "Acting as &lt;target&gt;" plus a "Switch back" link. Implementation rides PRD 052's close+recreate session channel (PRD 051 Plan §7–§8 was superseded — see PRD 051 § "Closed scope") |

Clicking the chip in the SPA opens the "Switch to user" dialog
(when `canImpersonate` is true OR an impersonation is active —
see the mid-impersonation flow above). The `GET /api/users`
typeahead call always uses the admin Bearer, so an already-active
impersonation does not hide the list of users the admin can
switch to.

### Why not RFC 8693 token exchange?

PRD 051 § "Alternatives Considered" lists ten established
impersonation patterns and the reasons each was rejected. Short
answer: RFC 8693 token-exchange requires either the target's token
(unbuildable — admin doesn't know the target's password) or a
realm-level Keycloak `impersonate-users` permission that's
all-or-nothing at realm scope. Rapla's per-group
`canAdminUser` scoping isn't expressible at the IdP at all — it
lives only in rapla data. So the authorization check has to run in
rapla code, and the token issuance has to happen on the same side
as the check.

See [PRD 051](prd/done/051-switch-user-with-oauth.md) for the full design,
including the comparison table and the deferred Option 3b (hybrid
IdP-issued tokens via Keycloak's `requested_subject` extension —
documented as a future enhancement, not v1).

## Verifying the setup

After launching the server, hit the discovery endpoint:

```bash
curl -s http://localhost:8051/api/auth/oauth/config | python3 -m json.tool
```

Expected response (defaults):

```json
{
  "enabled": true,
  "issuer": "http://localhost:8051",
  "clientId": "rapla-client",
  "authorizeUrl": "http://localhost:8051/oauth2/authorize",
  "tokenUrl": "http://localhost:8051/oauth2/token",
  "jwksUrl": "http://localhost:8051/oauth2/jwks",
  "userinfoUrl": "http://localhost:8051/userinfo",
  "logoutUrl": "http://localhost:8051/connect/logout",
  "endSessionUrl": "http://localhost:8051/connect/logout",
  "scopes": ["openid", "profile", "offline_access"],
  "swingLegacyLogin": true,
  "swingLegacyShowSsoButton": true,
  "picker": false,
  "providers": []
}
```

The `<origin>` is computed from the incoming request (honoring
`X-Forwarded-*`) unless `rapla.oauth.public-base-url` is set — note the
endpoint paths are `/oauth2/authorize` and `/oauth2/token` (no `/rapla/`
prefix). The advertised `scopes` are openid/profile/offline_access; the
actually granted scopes come from the Spring `rapla-client` registration
(openid, profile, offline_access).

For a deployment at `https://rapla.yourdomain.com` behind a proxy,
the URLs in `authorizeUrl`/`tokenUrl` should be the public HTTPS URLs,
not the internal localhost ones. If you see `localhost` in production,
your proxy isn't passing `X-Forwarded-*` headers — fix that.

## Common issues

### "Whitelabel error page" after clicking Sign in with browser

The auth server rejected the redirect URI. Cause: deployment-specific
redirect URI isn't on the registered list AND doesn't match any
loopback / WSL bridge / same-origin allowance. Fix: redefine
`redirect-uris` via `SPRING_APPLICATION_JSON` or an
`application-prod.yml` overlay — see "Production override" under
"Spring redirect URIs" above. Avoid indexed env vars
(`…_REDIRECT-URIS_n=…`) — they overwrite YAML entries by index, not
append.

### "This site can't be reached" after sign-in

The system browser can't open the loopback URL on the user's machine.
Causes vary — WSL2 NAT, corporate firewall blocking inbound localhost,
some antivirus configurations. Investigate the specific network
restriction.

### Token issued by `/oauth2/token` rejected as Bearer

All tokens (access + refresh + API keys) are RSA-signed JWTs validated
through a single `JwtDecoder` in `JwtConfig.java` against rapla's JWKS.
If a token fails validation, check that `AuthorizationServerConfig`
booted — it's a plain `@Configuration` (not `@AutoConfiguration`),
picked up by the default `@SpringBootApplication` component scan because
it lives in the scanned base package `org.rapla.server.spring`. If it
didn't load, the `jwkSource` `@Bean` it declares is missing and no
tokens can be issued in the first place. With external IdPs configured
(PRD 036) **and** `rapla.oauth.trust-external-issuers=true`, the decoder
wraps an `IssuerAwareJwtDecoder` that routes by `iss` claim (check the
provider's JWKS URL is reachable from the rapla server); in the default
single-issuer mode (`trust-external-issuers=false`) external tokens are
not accepted at `/api` at all — see the "Single-issuer `/api`" section
below.

### After server restart, sessions survive (PRD 029 Option A)

The auth server's RSA keypair is **persisted** in `RaplaKeyStorage`
(rapla preferences, same data file as the rest of the application
state). A token issued before a JVM restart still validates after the
restart — same key, same signature. The refresh-token hash is also in
preferences, so refresh requests after a restart also succeed.

## Single-issuer `/api` (PRD 072, default since 2026-06-20)

rapla's `/api` resource server trusts **only rapla-issued tokens** by
default. The flag **`rapla.oauth.trust-external-issuers`** (default
`false`) gates this; setting it `true` restores the legacy multi-issuer
decoder (`IssuerAwareJwtDecoder` routing by `iss` — rapla-SAS + Entra +
Google + Keycloak JWKS) as an escape hatch. In the default state
`JwtConfig.buildBaseDecoder` builds the bare local rapla
`NimbusJwtDecoder` even with external providers enabled, and pins the
configured `rapla.oauth.issuer` so a foreign-`iss` token can't pass.

This is the **identity-broker** end-state (M2): rapla federates the
upstream IdP at login via `oauth2Login()`, mints its **own** rapla JWT
(`RefreshSessionService.issueAndPersist`), and the external IdP token is
consumed server-side and never reaches `/api`. Three **non-circular**
reasons drive the cutover (the "rapla discards IdP tokens" / "uniform
refresh" properties are downstream *consequences*, not reasons):

1. **Identity resolution/provisioning moves off the read path onto the
   login write path (§16).** While `/api` accepted external tokens, every
   call resolved the external identity (`JwtUserResolver.resolveExternal`
   → `ExternalUserResolver`) and — with `auto-provision: true` —
   *created* the user on the **read** path. That is the §16 violation
   behind the 2026-05-28 `RaplaNewVersionException`→401 bug. Single-issuer:
   the rapla token already carries `sub=<rapla UUID>` → a plain lookup;
   provisioning happens once at login.
2. **One trust anchor instead of N.** `/api` validates a single
   signature/issuer (rapla's own key) instead of rapla-SAS + Entra +
   Google + Keycloak JWKS — smaller attack surface, fewer JWKS
   dependencies, no multi-tenant pattern-issuer edge cases.
3. **Consistent token contract.** Every `/api` token carries `sub=UUID` +
   rapla claims + the `act` impersonation model — no "which kind of token
   is this" branching in resolvers/controllers.

### What the rapla token IS — an access token, not an id_token

The credential every rapla surface presents at `/api` is a rapla-minted
**access token** (`typ=access`): rapla's own signature, `sub=<rapla UUID>`,
`preferred_username` / `name` / (when impersonating) `act`. rapla is **both
the Authorization Server and the Resource Server for itself**, so this access
token carries the identity directly — it *looks* id_token-ish in content, but
structurally it is an access token used as a Bearer.

**After the IdP exchange, rapla's own clients (SPA, Swing, `/api`) need only the
rapla access token (+ the rapla refresh token to renew it) — never an id_token:**

- the external IdP **`id_token`** is needed exactly **once**, as the *input* of
  the exchange/login — rapla verifies its signature / `iss` / `aud`=rapla / `exp`,
  provisions, then **discards** it (#7=a). It never reaches `/api`.
- rapla also issues a `typ=refresh` token (used only at `/oauth2/token` refresh +
  `/api/auth/refresh`, never a `/api` Bearer) and, on `scope=openid` flows, its
  **own** OIDC `id_token` — but that id_token is a standards artifact for OIDC
  *relying parties*; rapla's own surfaces (SPA/Swing/`/api`) don't consume it.

**Token-exchange shape:** external **`id_token`** (identity proof, `aud`=rapla)
→ rapla **access token** (+ refresh). An external *access* token is the wrong
input — its `aud` is the IdP's resource API (not rapla), and Google's is opaque
— so the exchange (and at-login provisioning) consumes the **`id_token`**,
verified against the provider's JWKS with an explicit `aud` pin.

### Getting a rapla token when you hold an external token

Since `/api` trusts only rapla-issued tokens, anyone holding an **external
IdP `id_token`** (and no rapla token) gets a rapla token through the
**identity-broker exchange**, then uses *that* at `/api`:

- **`POST /api/auth/oauth/token-exchange/{providerId}`** (RFC 8693 token-exchange) —
  form param `id_token`=the external IdP id_token. rapla verifies it on its **own**
  trust chain — `ExternalIdTokenVerifier`: signature against the provider's JWKS,
  `iss` (== `provider.issuer()` or the multi-tenant pattern), `exp`/`nbf`, and an
  explicit **`aud` pin** (`aud` MUST contain rapla's `client_id` for that provider —
  rejects a token minted for another audience) — provisions the rapla user (the §16
  write seam), and returns `{access_token, refresh_token, token_type, expires_in}`
  (rapla tokens). Verification failure → `401 invalid_token`/`invalid_grant`, no
  claim leak.
- **`POST /api/auth/oauth/exchange/{providerId}`** (the BFF code-exchange used by a
  client-side-PKCE flow) now **mints + returns a rapla token** after the upstream
  code exchange + provisioning — it no longer forwards the raw IdP token. Its
  `grant_type=refresh_token` path is rejected with `400 unsupported_grant_type`
  (rapla owns the session — #7=a — and does not relay IdP refresh tokens; refresh
  via rapla's own `/api/auth/refresh` / `/oauth2/token`).

**Replay caveat (token-exchange):** RFC 8693 has no authorization request, so the
exchanged id_token carries **no `nonce` binding** — its replay window is bounded
only by its own `exp` plus the `aud` pin. A caller who captures a valid id_token
*for rapla's client_id* can replay it until expiry to mint a rapla session.
Acceptable for the "I already hold a finished id_token for rapla" use case; if
stricter replay protection is needed, add a one-time `jti` check or a fresh
`auth_time` requirement.

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
> live there, not in the token). The rapla **username is the identity**:
> the resolver matches the token's `upn` → `preferred_username` → `email`
> claims case-insensitively against `user.getUsername()`. Falls back to
> email-against-`user.getEmail()`; otherwise (and if `auto-provision: true`)
> creates a new rapla user with the lowercased UPN/preferred_username/email
> as the username. See PRD 036 "Why we keep the rapla User".
>
> *History (2026-05-21)*: previously rapla matched on a per-provider
> `org.rapla.auth.external-id.<provider>` preference holding the IdP's
> `sub`. That design broke down on first login for any user not provisioned
> via the IdP (CSV-imported, hand-created, LDAP-migrated), and silently
> broke when an IdP realm rotated (`sub` changes but the stale pref
> doesn't). Username-as-identity matches what rapla already uses
> internally for permissions and ownership; the tradeoff is that an
> IdP-side username rename produces an orphaned rapla user that an admin
> renames manually — same operator burden as the legacy LDAP path.

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

> **Legacy multi-issuer model (`rapla.oauth.trust-external-issuers=true`).**
> The table below describes the pre-PRD-072 SPA, which presented the IdP's
> `id_token` directly to `/api` under the `IssuerAwareJwtDecoder`. The
> default since PRD 072 is **single-issuer** (§ "Single-issuer `/api`"):
> the SPA is cookie-based, every surface presents a **rapla** token, and
> `/api` validates only rapla's issuer. This subsection applies only when
> the escape-hatch flag is turned back on.

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

> **PRD 072 cookie model.** The per-provider logout above describes the
> legacy multi-issuer SPA. Under the default single-issuer cookie model
> the SPA no longer drives any per-provider IdP logout: `AuthService.signOut()`
> simply does `POST /api/auth/logout` (clearing the httpOnly cookies +
> the rapla session) then navigates to `/login`. It does **not** inspect
> per-provider `endSessionUrl` or drive an angular-oauth2-oidc IdP
> redirect. (The server still derives `endSessionUrl` per provider for
> the discovery payload, but the SPA no longer consumes it.)

### 401 handling on the SPA — refresh-then-retry, redirect on real rejection

Under the PRD 072 cookie model the SPA holds no OAuth client, no
`AuthService.token()`, and attaches **no** `Authorization: Bearer`
header — the rapla JWT rides the httpOnly `access_token` cookie
(`rapla-angular/src/app/auth/auth.interceptor.ts`). There is no
`AuthErrorDialogComponent`, no `oauth.refreshToken()`, no
`sessionStorage.oauthFailures`, and no angular-oauth2-oidc in the
interceptor. The actual policy on every response:

| Condition | Action |
|---|---|
| Non-401 | Pass through. |
| 401 | Call `POST /api/auth/refresh` (`AuthCookieController`) **once** and replay the original request. If the refresh itself returns 401, clear local identity and `window.location` to `/login`. |

> *(Legacy multi-issuer mode, `rapla.oauth.trust-external-issuers=true`,
> used a Bearer-attaching interceptor with `oauth.refreshToken()` and an
> `AuthErrorDialogComponent` — that path is gone in the default cookie
> model. `IssuerAwareJwtDecoder` still exists server-side but is only
> wired when the flag is on.)*

Pre-authentication paths skip the whole machinery — `Bearer` is never attached and 401s do not show the dialog:
- `/oauth2/*` — Spring AS endpoints
- `/.well-known/*` — OIDC discovery
- `/api/auth/oauth/*` — BFF code exchange + provider discovery (these endpoints are *how* you obtain a token; attaching a stale Bearer would cause the resource-server JWT filter to reject before the controller runs — see [`SecurityConfig`](../rapla-server/src/main/java/org/rapla/server/spring/SecurityConfig.java)'s dedicated `@Order(0)` filter chain matching `/api/auth/oauth/**`)

The server propagates the resolver's actual exception message in the
401 body so the dialog can show the real reason (e.g.
"The name 'alice@example.org' is already taken") instead of a generic
"No user found in session." This is done in
[`SpringSecurityRemoteSession.resolveJwtOrThrow`](../rapla-server/src/main/java/org/rapla/server/spring/SpringSecurityRemoteSession.java)
— when a JWT is present, a resolver failure throws the original
`RaplaSecurityException` instead of returning null and falling back to
the legacy session path.

There is **no proactive/silent refresh** in the cookie model — refresh
is purely reactive (on a 401, via `POST /api/auth/refresh`). The old
`app.config.ts` `oauth.setupAutomaticSilentRefresh(...)` wiring does not
exist.

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

> **Redirect URIs are server-side now (PRD 072).** After the
> `oauth2Login()` refactor rapla redeems the authorization code
> **server-side**, so every external IdP registration uses rapla's
> server-side callback **`{baseUrl}/login/oauth2/code/{registrationId}`**
> — `registrationId` is `google` / `microsoft` / `keycloak`. The old SPA
> redirect `/app/auth/callback` (and the Entra **SPA platform**) are
> **gone**; do not register them. For each provider, dev registers both
> `http://localhost:4200/login/oauth2/code/{id}` (the ng-serve proxy
> origin) and `http://localhost:8051/login/oauth2/code/{id}`.

#### Per-provider vs single callback — why per-provider

rapla keeps Spring's **per-provider** `/login/oauth2/code/{registrationId}`
callback rather than one shared callback path. This is both Spring
Security's framework default **and** an OAuth 2.0 Security BCP
([RFC 9700](https://datatracker.ietf.org/doc/rfc9700/)) mitigation against
**IdP mix-up attacks**: a distinct redirect URI per authorization server
lets the client (here rapla, the relying party) tell which IdP a given
callback belongs to, so an attacker can't splice a code issued by IdP A
into a flow the client thinks is with IdP B. Other RP frameworks do the
same — NextAuth (`/api/auth/callback/{provider}`), Passport
(`/auth/{provider}/callback`), django-allauth, OmniAuth. A single shared
callback is only appropriate when there is exactly one upstream broker;
rapla is the broker-RP to *multiple* IdPs (Keycloak / Microsoft / Google),
so per-provider is the correct shape.

#### TEMPORARY DHBW dev bridge — `rapla.oauth.web.dhbw-legacy-callback`

A dev-only workaround exists for the DHBW production Keycloak
(`login.mosbach.dhbw.de`, realm `dhbwmos-lehre`, client `rapla-app`):
that realm only whitelists the **legacy** `/app/auth/callback` redirect
for localhost, and the maintainer has no admin on the prod realm to
register the conformant `/login/oauth2/code/keycloak`. With
**`rapla.oauth.web.dhbw-legacy-callback=true`** (default `false`; set
only in the gitignored `application-local.yml`):

- the keycloak `ClientRegistration` (built in `RaplaClientRegistrationConfig`)
  **sends** the registered `/app/auth/callback` as its `redirect_uri`;
- the callback is then routed back onto Spring's real
  `/login/oauth2/code/keycloak` endpoint by **both** (a) the ng-serve
  proxy on `:4200` (the SPA dev origin) and (b) a server-side
  `LegacyKeycloakCallbackBridgeFilter` on `:8051` — the Swing-SSO browser
  hits `:8051` directly, where there is no proxy, so the filter is what
  bridges it there.

Spring validates the OAuth `state` (not the request path) and uses the
**saved** `redirect_uri` (`/app/auth/callback`) for the token call, so it
still matches what DHBW issued the code for. The conceptual point: the
**outgoing** `redirect_uri` rapla sends to the IdP is built server-side
(`RaplaClientRegistrationConfig`), so a proxy rewrite alone can't fix it —
the server-side flag is required to make rapla *send* the legacy URI.

**Removal condition:** delete the flag, the `LegacyKeycloakCallbackBridgeFilter`,
and the keycloak `redirectUri` override once DHBW IT registers the
conformant `/login/oauth2/code/keycloak` redirect URI on the prod realm.

### Recipe: Microsoft Entra ID (Web platform — required)

Because rapla redeems the code server-side, the Entra registration must
be the **Web platform** (a confidential client) **with a client secret**.
The SPA platform fails with `AADSTS9002326` ("cross-origin token
redemption is permitted only for the 'Single-Page Application' client
type") — Entra refuses the server-side redemption an SPA-platform client
makes (verified live, PRD 072).

1. **Register the application** in Entra:
   - Azure portal → Microsoft Entra ID → App registrations → New registration.
   - Name: `rapla` (or anything you like).
   - Supported account types: **Accounts in this organizational directory only** (single-tenant — strongly recommended; multi-tenant requires `tenant=common` and accepts users from any Entra tenant).
   - Redirect URI: select platform **Web** from the dropdown, then enter `https://rapla.yourdomain.com/login/oauth2/code/microsoft`. For dev, add `http://localhost:4200/login/oauth2/code/microsoft` and `http://localhost:8051/login/oauth2/code/microsoft`.
   - Click **Register**.
2. **Note the IDs** (Overview tab):
   - **Application (client) ID** → `RAPLA_OAUTH_EXTERNAL_MICROSOFT_CLIENT_ID`.
   - **Directory (tenant) ID** → `RAPLA_OAUTH_EXTERNAL_MICROSOFT_TENANT`.
3. **Generate a client secret**: App Registration → Certificates & secrets → New client secret → copy the **Value** field (not the Secret ID) → `RAPLA_OAUTH_EXTERNAL_MICROSOFT_CLIENT_SECRET`.
4. **API permissions**: the default delegated `User.Read` is fine. No admin consent needed.
5. **Run rapla** with:
   ```bash
   export RAPLA_OAUTH_EXTERNAL_MICROSOFT_ENABLED=true
   export RAPLA_OAUTH_EXTERNAL_MICROSOFT_TENANT=11111111-2222-3333-4444-555555555555
   export RAPLA_OAUTH_EXTERNAL_MICROSOFT_CLIENT_ID=66666666-7777-8888-9999-aaaaaaaaaaaa
   export RAPLA_OAUTH_EXTERNAL_MICROSOFT_CLIENT_SECRET=<secret-value>
   java -jar rapla-2.1-SNAPSHOT.jar
   ```

### Recipe: Google (Web application — requires secret + BFF)

Google's Web application client type enforces `client_secret` on the
token endpoint even with PKCE. The BFF route handles this.

1. **Create OAuth credentials** in Google Cloud Console:
   - https://console.cloud.google.com → APIs & Services → Credentials → Create credentials → OAuth client ID.
   - Application type: **Web application**.
   - **Authorised JavaScript origins**: `https://rapla.yourdomain.com` (and `http://localhost:4200` for Angular dev, `http://localhost:8051` for rapla direct).
   - **Authorised redirect URIs**: `https://rapla.yourdomain.com/login/oauth2/code/google` (and for dev `http://localhost:4200/login/oauth2/code/google` + `http://localhost:8051/login/oauth2/code/google`). This is rapla's server-side callback — **not** the old `/app/auth/callback`. Verified live (PRD 072).
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

> **No more Google "Desktop app" route (PRD 072).** Because rapla
> redeems the code server-side, Google must be a **Web application**
> client with a registered redirect URI + secret (above). The former
> secret-less Desktop-app path relied on the SPA doing the loopback
> redemption itself — that path is gone with the server-side
> `oauth2Login()` flow.

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
     authentication** OFF for a public PKCE client (rapla reuses the public
     `rapla-app` client server-side with PKCE); turn it ON for a confidential
     client (then set `client-secret`).
   - *Login settings*:
     - **Valid redirect URIs**: `https://rapla.yourdomain.com/login/oauth2/code/keycloak`
       (for dev also `http://localhost:4200/login/oauth2/code/keycloak` and
       `http://localhost:8051/login/oauth2/code/keycloak`). This is rapla's
       server-side callback — **not** `/app/auth/callback`. Verified live
       against the DHBW Mosbach realm (PRD 072).
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

#### Refresh tokens and `offline_access` — the distinction

Keycloak emits **two different kinds of refresh tokens**:

| Kind | Scope required | Lifetime | Survives logout? | Use case |
|---|---|---|---|---|
| **SSO-session refresh** | none — issued by default with any login | governed by realm's *SSO Session Idle/Max* (typically ~8 h sliding) | no — invalidated on logout | normal interactive web/SPA login |
| **Offline refresh** | `offline_access` (must be in client's allowed scopes AND requested) | governed by realm's *Offline Session Idle/Max* (typically days to months) | yes — survives logout | CLI tools, background jobs |

> **Under PRD 072 the IdP refresh-token kind is moot — rapla discards all IdP tokens.** rapla is an identity-only broker (#7=a): the server-side `oauth2Login` HEAD verifies the IdP `id_token` *once* at login, then `RaplaClientRegistrationConfig` actively **strips `offline_access`** from the outgoing IdP scopes, so rapla never receives an IdP refresh token it would only throw away. The SPA/Swing session is governed entirely by **rapla's own** refresh token — `RefreshSessionService.REFRESH_TOKEN_TTL_SECONDS` (21 d), delivered to the SPA as the httpOnly `refresh_token` cookie and consumed reactively via `POST /api/auth/refresh`. There is **no** `setupAutomaticSilentRefresh` and **no** silent-iframe refresh (see the SPA cookie-model section above). The realm's SSO Session Max and the IdP refresh lifetimes in the table above do **not** bound a rapla session — the table is background only, explaining why rapla requests the *minimal* identity scopes and not `offline_access`.

Adding `offline_access` to a provider's `rapla.oauth.external.<id>.scopes` has **no effect under the broker model** — `RaplaClientRegistrationConfig` strips it before the authorize call. The block below is retained only for a hypothetical future *non-broker* deployment that needs the IdP's own offline refresh token; in today's PRD 072 model it is a no-op:

```yaml
rapla.oauth.external.keycloak.scopes:
  - openid
  - profile
  - email
  - offline_access
```

…AND ensure the realm's `rapla-app` client has `offline_access` in its
**Optional client scopes** list. If it doesn't, Keycloak rejects the
entire `/authorize` call with `error=invalid_scope` — login completely
fails. There is no graceful degradation: it's all-or-nothing per client.

**Querying the IdP for these settings.** The values are *not* in the
discovery doc (`/.well-known/openid-configuration`) — OIDC keeps them
private to the IdP. To learn them at runtime:
1. The `expires_in` and `refresh_expires_in` fields on every `/token`
   response give the access and refresh lifetimes authoritatively.
2. The absolute *SSO Session Max* is only observable empirically (keep
   refreshing until refresh fails) or via Keycloak's Admin REST API
   (`/admin/realms/{realm}` returns `ssoSessionMaxLifespan`,
   `ssoSessionIdleTimeout`, `accessTokenLifespan`,
   `offlineSessionIdleTimeout`, `offlineSessionMaxLifespan`).
3. Easier: ask the realm admin.

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
On first external login, the resolver looks the user up by username
(`upn` → `preferred_username` → `email`, case-insensitive against
`user.getUsername()`), falling back to email-against-`user.getEmail()`.
**No data migration required**, as long as the rapla username matches
what the IdP emits for one of those claims. For AD-federated Keycloak
deployments this is the UPN form (e.g.
`firstname.lastname@intern.example.org`); for Entra it's the
`preferred_username` / UPN; for Google it's the email.

If your existing rapla usernames are in a different shape (e.g. bare
`firstname.lastname` while the IdP emits the full UPN), the existing
users won't match — auto-provision creates duplicates. Two ways to
fix:
1. **Rename rapla users** to match what the IdP emits (admin UI: User
   editor → change username). One-time operator task.
2. **Disable auto-provision** (`auto-provision: false`) and admin-create
   the matching usernames before users log in. Same effect, more manual.

Stale `org.rapla.auth.external-id.<provider>` and
`org.rapla.auth.provider` preferences from the pre-2026-05-21 resolver
design are now dead data — the new resolver doesn't read them. Safe to
leave in place; they don't affect anything.

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
— see `.gitignore` line 41):

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
