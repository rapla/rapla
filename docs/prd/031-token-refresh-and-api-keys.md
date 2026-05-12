# PRD 031: Refresh Tokens & API Keys — IdP-portable design

**Status:** draft
**Date:** 2026-05-12

## Goal

Make rapla's token-refresh and long-lived-API-key story survive (a) server
restarts and (b) a future swap of the IdP from rapla's embedded Spring
Authorization Server to an external IdP (Keycloak / Azure AD / Google
Workspace).

Two concerns are easy to conflate but should stay separated:

1. **User session refresh** — short-lived refresh tokens (hours/days) used to
   silently re-mint an access token when the previous one expires mid-session.
   Today: rapla's `/auth/login` issues these and `/auth/refresh` redeems them,
   but no client code actually calls `/auth/refresh`. `MyCustomConnector.reauth`
   instead re-runs the full username+password login from cached credentials —
   which breaks for OAuth-logged-in sessions (no cached password).
2. **Long-lived API keys** — opaque rapla-issued bearer tokens stored
   server-side per user, used by scripts/integrations/ical feeds. Today:
   `RaplaKeyStorage.storeAPIKey(user, ...)` already exists, stores in user
   preferences, persists to data file. Works fine, just under-used.

The goal is to make both concerns work today (against rapla's embedded auth
server) and survive a future switch to Keycloak with **zero client code
change** for refresh and **zero IdP involvement** for API keys.

## Why this is needed now

1. **PRD 029 (OAuth) Phase 1 shipped without refresh.** OAuth-logged-in
   Swing sessions die at the 1-hour access-token expiry. User has to click
   "Sign in with browser…" again, including the form login step. UX regression
   vs the legacy password path.
2. **Restart-survival is half-shipped.** PRD 029's persistent-JWKSource
   change (2026-05-12) made legacy `/auth/refresh` tokens survive restart by
   accident, because the endpoint is stateless and signs with a persistent
   key. OAuth `/oauth2/token` refresh tokens don't survive restart because
   SAS's `InMemoryOAuth2AuthorizationService` keeps state in memory.
3. **PRD 029 Phase 2 lists Keycloak** as the natural next-IdP. If we add
   refresh handling now without thinking about Keycloak-portability, we paint
   ourselves into a corner where the migration to Keycloak forces a refresh-path
   rewrite. Better to design once.
4. **API keys are an underused existing feature.** No client surfaces them
   in a UX today, but the storage mechanism is sitting there. Exposing this
   properly closes the gap for non-interactive integrations.

## Scope

### In scope

- `MyCustomConnector.reauth` rewritten to attempt **refresh-token-based** reauth
  first, falling back to password reauth, falling back to "session expired".
- Server-side `OAuth2TokenCustomizer<JwtEncodingContext>` bean that adds
  `typ=refresh` to refresh tokens issued by Spring Authorization Server,
  so `/auth/refresh` can validate both legacy and OAuth refresh tokens.
- `OAuthConfigController` (discovery endpoint) gains a `refreshUrl` field.
  Today returns `<server>/auth/refresh`; in a future Keycloak deployment
  this becomes Keycloak's token endpoint via env var override.
- Client `OAuthConfig` (the DTO `RaplaClientServiceImpl.fetchOauthConfig`
  parses) gains the matching field, plumbed into the connector's reauth
  path.
- Optional toggle to enable OAuth-refresh-tokens-survive-restart via a
  persistent `OAuth2AuthorizationService` — design decision in Open
  Questions §1.
- An API key UI / endpoint surface that exposes the existing
  `RaplaKeyStorage.storeAPIKey` to end users (so they can mint a key from
  the Swing client and use it from a script).
- Documentation: extend `docs/authentication.md` with refresh + API key
  sections; cross-link from PRD 029 and PRD 030.

### Out of scope (this PRD)

- Migration to Keycloak itself. This PRD only ensures the design
  *survives* the migration; the actual switch is its own PRD.
- Token revocation API. We get bulk invalidation via JWK rotation
  (already possible) but no per-token revoke. Future PRD.
- Refresh-token rotation (issuing a new refresh token alongside the new
  access token on every refresh). Spring SAS does this by default; the
  unified `/auth/refresh` path could too with one extra issue() call.
  Worth doing in this PRD if cheap, skip otherwise.
- Long-running daemon / service-account credentials beyond API keys.

## Architecture

### Two refresh paths under embedded auth server, one client view

Today's reality:

| Login | Issues refresh token | Validates refresh token |
|---|---|---|
| `POST /auth/login` (username+password) | `JwtIssuer` → RSA-signed, `typ=refresh`, no state | `AuthController.refresh` — signature + `typ` check only |
| `POST /oauth2/token` (OAuth code grant) | `OAuth2TokenGenerator` → RSA-signed, no `typ` claim | SAS's `OAuth2RefreshTokenAuthenticationProvider` — signature + `OAuth2Authorization` lookup |

The design unifies the client view by exposing a single `refreshUrl` in the
discovery endpoint and ensuring both flows produce refresh tokens that the
unified `/auth/refresh` endpoint can validate. This means OAuth-issued
refresh tokens need a `typ=refresh` claim — added by an `OAuth2TokenCustomizer`
that runs whenever SAS generates a refresh token.

The `/auth/refresh` endpoint stays stateless (signature + `typ` check),
which is the property that makes it survive restart on the legacy path. By
routing OAuth refresh through the same endpoint, OAuth refresh tokens
*also* survive restart.

This bypasses SAS's `OAuth2AuthorizationService` state for refresh, which
trades two things:
- ✗ No refresh-token rotation tracking (Spring SAS would normally rotate
  refresh tokens — we won't).
- ✗ No per-token revocation (we'd revoke by JWK rotation only).
- ✓ Restart-survival for free.
- ✓ Symmetric refresh across login paths.

The trade is defensible for rapla's threat model. See Open Question §1
if we want to revisit.

### Discovery endpoint extension

`OAuthConfigController.config()` adds one field:

```java
return new OAuthConfig(
    enabled, clientId, authorizeUrl, tokenUrl, scopes, showPasteFallback,
    base + "/auth/refresh"   // ← new: refreshUrl
);
```

In `application.yml`:

```yaml
rapla:
  oauth:
    refresh-url: ${RAPLA_OAUTH_REFRESH_URL:}     # blank → derived from request as <base>/auth/refresh
```

A Keycloak deployment sets:

```
RAPLA_OAUTH_REFRESH_URL=https://keycloak.uni.de/realms/rapla/protocol/openid-connect/token
```

Done. Client code unaware.

### Client-side reauth flow

`MyCustomConnector.reauth(Class proxy)` becomes:

```java
String refreshToken = connectionInfo.getRefreshToken();
if (refreshToken != null && oauthConfig.getRefreshUrl() != null) {
    try {
        LoginTokens newTokens = refreshTokens(oauthConfig.getRefreshUrl(), refreshToken);
        connectionInfo.setAccessToken(newTokens.getAccessToken());
        connectionInfo.setRefreshToken(newTokens.getRefreshToken());
        return newTokens.getAccessToken();
    } catch (Exception refreshFailed) {
        logger.info("refresh failed, falling back to password reauth: " + refreshFailed.getMessage());
    }
}

ConnectInfo info = connectionInfo.getConnectInfo();
if (info != null && info.getPassword() != null) {
    // existing password-reauth path
    ...
}

throw new RaplaSecurityException(i18n.getString("error.session_expired"));
```

The `refreshTokens(url, refreshToken)` helper POSTs `refresh_token=...`
as form-encoded body to the configured refresh URL. For rapla's
`/auth/refresh` the body is JSON (`{"refreshToken": "..."}`); for
Keycloak/OAuth-standard endpoints it's form-encoded
(`grant_type=refresh_token&refresh_token=...&client_id=...`). The
helper picks the body shape based on URL pattern or based on a
discovered `refreshUrlGrantType` field in `OAuthConfig`. See Open
Question §2.

### Server-side `OAuth2TokenCustomizer`

```java
@Bean
public OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer() {
    return ctx -> {
        if (OAuth2TokenType.REFRESH_TOKEN.getValue().equals(ctx.getTokenType().getValue())) {
            ctx.getClaims().claim("typ", "refresh");
        }
    };
}
```

Wires via Spring Auth Server's bean discovery. Adds `typ=refresh` to
SAS-issued refresh tokens; access tokens unchanged.

### API key surface

Add to `AuthController` (or a new `ApiKeyController`):

- `POST /auth/api-keys` — body `{ "label": "ical-feed-2026" }`, returns
  `{ "id": "...", "key": "rapla_xxxxx...", "label": "..." }`. The `key`
  is shown once; only its hash is stored server-side.
- `GET /auth/api-keys` — list user's API keys (id, label, createdAt;
  no key material).
- `DELETE /auth/api-keys/{id}` — revoke.

Storage uses `RaplaKeyStorage` (which already has `storeAPIKey` /
`getAPIKeys` / `removeAPIKey`). The `key` itself is a high-entropy
opaque string (e.g., 32 random url-safe bytes prefixed with `rapla_`),
not a JWT — these aren't user-session tokens, they're long-lived
credentials.

Resource-server validation: `BearerTokenAuthenticationFilter` already
checks `Authorization: Bearer …` against the `JwtDecoder`. We extend
it with a fallback: if the token doesn't parse as a JWT (no dots, not
url-safe-base64), try the API-key path — look up by hash in
`RaplaKeyStorage`.

Swing UI: a small dialog accessible from the user menu — "API Keys",
list + new + revoke buttons. Mirror this on a future Angular admin
panel.

## Plan

1. **Discovery endpoint: add `refreshUrl`.**
   - `OAuthConfigController.OAuthConfig` + Java `OAuthConfig` DTO.
   - Default: `<base>/auth/refresh`. Env override.
   - Tier-3 test confirming the field appears.
2. **Server: `OAuth2TokenCustomizer` for `typ=refresh`.**
   - Bean in `AuthorizationServerConfig`.
   - Tier-3 test: drive a full PKCE flow, decode the resulting refresh
     token, assert `typ=refresh` claim.
3. **Server: relax `AuthController.refresh`** to accept SAS-issued
   refresh tokens (it should already work once `typ=refresh` is present).
   - Add a tier-3 test driving a PKCE flow then refreshing via
     `/auth/refresh` and using the new access token.
4. **Client: `MyCustomConnector.reauth` refresh path.**
   - Pull `refreshUrl` from `OAuthConfig` (already fetched at login).
   - Implement `refreshTokens(url, refreshToken)` helper.
   - Refresh-first-then-password fallback order.
   - Tier-2 test in rapla-client driving a real refresh via the test
     server's `/auth/refresh`.
5. **Client: cache `OAuthConfig`** somewhere accessible by `MyCustomConnector`
   (currently fetched only at login). Likely add to `RemoteConnectionInfo`.
6. **API key endpoint surface.**
   - `ApiKeyController` with POST/GET/DELETE.
   - Hash-on-store, plaintext-once-in-response pattern.
   - Resource-server fallback: when JWT decode fails, try API key lookup.
   - Tier-3 tests for each operation + bearer-auth via API key.
7. **Swing UI for API keys.**
   - Small Swing dialog accessible from the existing user menu.
   - List, create, revoke.
   - Tier-1 view-presenter test (per PRD 023 pattern).
8. **Documentation.**
   - `docs/authentication.md`: refresh and API key sections.
   - PRD 029 + 030 cross-references.
9. **Status flip to `done` once shipped + smoke-tested.**

## Tests

| Tier | Test | What it locks in |
|---|---|---|
| 3 | `OAuthConfigControllerTest` (extend) | `refreshUrl` appears in discovery |
| 3 | `AuthorizationServerTypClaimTest` | OAuth refresh tokens carry `typ=refresh` |
| 3 | `UnifiedRefreshIntegrationTest` | OAuth refresh token redeemable via `/auth/refresh` |
| 2 | `MyCustomConnectorReauthTest` | reauth on 401 hits refresh URL then retries; falls back to password; surfaces session-expired when both fail |
| 3 | `ApiKeyControllerTest` | create / list / revoke / bearer-auth round trip |
| 3 | `ApiKeyAcceptedAsBearerTest` | API key in `Authorization: Bearer` unlocks `/storage/resources` |
| 2 | `MyCustomConnectorRefreshSurvivesRestartTest` | start server, login, kill server, restart, reuse refresh token → succeeds |
| e2e | Manual `test-deployment` smoke after switching `RAPLA_OAUTH_REFRESH_URL` to a stub URL | discovery + client behavior in production-shape config |

## Open Questions

1. **Bypass SAS state or persist it?** The recommended design routes both
   refresh paths through stateless `/auth/refresh`, giving free restart-
   survival but losing per-token revocation. The alternative is to keep
   SAS's stateful refresh and add a persistent
   `OAuth2AuthorizationService` (JDBC- or rapla-preferences-backed).
   Decision: bypass SAS state for now. Revisit if revocation becomes a
   requirement.
2. **Refresh request body shape: JSON vs OAuth form-encoded?** Today
   `/auth/refresh` accepts JSON `{"refreshToken": "..."}`. Keycloak and
   Spring SAS's `/oauth2/token` use form-encoded
   `grant_type=refresh_token&refresh_token=...&client_id=...`. Three
   options: (a) make `/auth/refresh` accept both; (b) add a discovery
   field `refreshUrlGrantType: "json"|"oauth"` so client knows; (c) pivot
   `/auth/refresh` to OAuth form-encoded and deprecate JSON variant.
   Decision: lean toward (b) — explicit, simple to evolve. (a) is also
   fine if we don't want to expand the discovery shape.
3. **Refresh-token rotation?** Spring SAS rotates by default. Should
   our `/auth/refresh` also issue a new refresh token alongside the
   access token, invalidating the old? Today it returns a new refresh
   token but old ones stay valid (no state tracking). Worth a note in
   docs — "refresh tokens valid until expiry, not per-use".
4. **API key prefix?** Convention is to prefix with a recognisable string
   for security scanners (GitHub finds leaked credentials by pattern).
   Use `rapla_` or `rk_` or `rcs_` (rapla credential string)? Cosmetic
   but worth picking now.
5. **API key scopes?** Today everything an API key holder does is "the
   user this key was issued for". Should we add scope restrictions (read-
   only key, ical-only key)? Adds complexity; skip until requested.
6. **Where does the Swing "API Keys" dialog live?** User menu seems
   right but conflicts with the new login dialog real estate question
   (PRD 029 Phase 2 Open Q5). Decide alongside that.
7. **Migration of existing /auth/login clients.** Some custom rapla
   deployments may have third-party REST clients hitting `/auth/login`
   directly and using its custom JSON refresh shape. Changing that
   endpoint's contract risks breaking them. Decision: keep current
   contract; the unification is additive (OAuth refresh tokens also
   accepted at `/auth/refresh`).
