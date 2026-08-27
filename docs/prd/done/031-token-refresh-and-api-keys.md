# PRD 031: Refresh Tokens & API Keys — IdP-portable design

**Status:** effectively complete / supersedable — refresh-token half consolidated onto `/oauth2/token` 2026-05-16 ([PRD 041](../041-openapi-runtime-removal.md)); API-keys half **superseded by [PRD 043](../043-api-keys-jwt-pat.md)** (server-minted asymmetric JWT, GitHub-PAT flow); legacy HMAC token path **removed 2026-06-24**. Only residual is the PRD-043-tracked API-key UI. Candidate for `done/`.
**Date:** 2026-05-12

> **2026-06-24 update — legacy HMAC token path removed (final cleanup).** The last
> remnant of the pre-OAuth token system is gone: `TokenHandler`, `SignedToken`/
> `ValidToken`, and the `RemoteSessionImpl` fallback (the `?access_token=` /
> `raplaLoginToken`-cookie / Bearer HMAC branches) were deleted. Auditing confirmed
> nothing mints the rapla-custom `userId$signature` token anymore — its only minter
> chain hung off the already-deleted refresh path, the Swing fallback password dialog
> issues RSA JWTs via the `/oauth2/token` password grant, and iCal subscribers
> authenticate via `?user=` + published calendar (not a token). Auth is now genuinely
> JWT-only (`SpringSecurityRemoteSession` + `JwtUserResolver`). The `"refreshToken"`
> API-key slot is now read-only legacy, consulted by no auth path; the `storeAPIKey`
> family rename foreseen in [PRD 043](../043-api-keys-jwt-pat.md) is unblocked. See `docs/authentication.md`.

> **2026-05-16 update.** Refresh-token consolidation done in [PRD 041](../041-openapi-runtime-removal.md) — both
> `/api/auth/login`-issued and `/oauth2/authorize`-issued refresh tokens now
> share `RefreshSessionService` (single hash slot per user, never-rotate, full
> JWT in user prefs for multi-tab share). The API-key half of THIS PRD is
> superseded by [PRD 043](../043-api-keys-jwt-pat.md) (server generates a one-shot asymmetric keypair, signs
> a single JWT with the private key, stores only the public key, discards the
> private key). Below text retained for historical context.

**Shipped 2026-05-12 (refresh-token half):**
- `/api/auth/login` mints access (1h) + refresh (30d) as RSA-signed JWTs (signing key from `RaplaKeyStorage`, survives restart).
- `/api/auth/refresh` validates refresh JWT (signature + `typ=refresh`) + SHA-256 hash stored under `user.preferences["org.rapla.auth.session"]`. **Rotate-when-stale**: refresh reissued only when within `REFRESH_RENEWAL_THRESHOLD_SECONDS` (7 days) of expiry. ~1/user/week DB writes.
- `/api/auth/logout` clears the `org.rapla.auth.session` preference (Bearer-authenticated; revokes all sessions for the user).
- `MyCustomConnector.reauth` tries `refreshUsingToken` first (POST to `connectionInfo.refreshUrl` — read from discovery), falls back to password reauth.
- Discovery endpoint (`/api/auth/oauth/config`) emits `refreshUrl` and `logoutUrl` — IdP swap becomes a one-env-var change (`RAPLA_OAUTH_ISSUER`).
- Client-side refresh-token cache: hybrid `TokenStore` (JNLP `PersistenceService` → `~/.rapla/tokens.json` 0600 → NoOp) — see [PRD 029](../029-swing-oauth-login.md) OQ 10.

**Open / API-keys half:** mechanism landed via [PRD 043](../043-api-keys-jwt-pat.md) (server-side complete); only the end-user **UI** (Swing dialog / Angular admin panel) is still pending — tracked in [PRD 043](../043-api-keys-jwt-pat.md), not here.

## Goal

Make rapla's token-refresh and long-lived-API-key story survive (a) server restarts and (b) a future swap of the IdP from rapla's embedded Spring Authorization Server to an external IdP (Keycloak / Azure AD / Google Workspace).

Two concerns to keep separate:

1. **User session refresh** — short-lived refresh tokens used to silently re-mint access tokens. Today `/auth/login` issues and `/auth/refresh` redeems them, but no client code calls `/auth/refresh`. `MyCustomConnector.reauth` re-runs full password login — broken for OAuth sessions.
2. **Long-lived API keys** — opaque rapla-issued bearer tokens stored server-side per user for scripts/integrations/ical feeds. `RaplaKeyStorage.storeAPIKey(user, ...)` already exists, persists to data file — just under-used.

Goal: both work today (embedded auth server) and survive a future switch to Keycloak with **zero client code change** for refresh and **zero IdP involvement** for API keys.

## Why this is needed now

1. **[PRD 029](../029-swing-oauth-login.md) (OAuth) Phase 1 shipped without refresh.** OAuth-logged-in Swing sessions die at 1-hour access-token expiry. User has to click "Sign in with browser…" again.
2. **Restart-survival is half-shipped.** [PRD 029](../029-swing-oauth-login.md)'s persistent-JWKSource change (2026-05-12) made legacy `/auth/refresh` tokens survive restart by accident (stateless, signs with persistent key). OAuth `/oauth2/token` refresh tokens don't survive — SAS's `InMemoryOAuth2AuthorizationService` is in-memory.
3. **[PRD 029](../029-swing-oauth-login.md) Phase 2 lists Keycloak** as the next-IdP. Designing refresh without Keycloak-portability paints us into a rewrite later.
4. **API keys are an underused existing feature.** Storage mechanism is sitting there.

## Scope

### In scope

- `MyCustomConnector.reauth` rewritten: refresh-token first → password fallback → "session expired".
- Server-side `OAuth2TokenCustomizer<JwtEncodingContext>` adding `typ=refresh` to SAS-issued refresh tokens so `/auth/refresh` validates both legacy and OAuth.
- `OAuthConfigController` gains `refreshUrl` (today `<server>/auth/refresh`; future Keycloak deployment overrides via env var).
- Client `OAuthConfig` DTO gains matching field, plumbed into connector reauth.
- Optional toggle for OAuth-refresh-survives-restart via persistent `OAuth2AuthorizationService` — see Open Question §1.
- API key UI / endpoint surface exposing `RaplaKeyStorage.storeAPIKey` to end users (Swing dialog + script consumption).
- Documentation: extend `docs/authentication.md`; cross-link PRD [029](../029-swing-oauth-login.md) + [030](../030-server-side-view-rendering.md).

### Out of scope (this PRD)

- Migration to Keycloak itself. This PRD ensures the design *survives* the migration.
- Token revocation API (bulk invalidation via JWK rotation works; no per-token revoke).
- Refresh-token rotation. SAS does this by default; `/auth/refresh` could too with one extra `issue()`. Worth doing if cheap.
- Long-running daemon / service-account credentials beyond API keys.

## Architecture — final state (2026-05-16, [PRD 041](../041-openapi-runtime-removal.md) consolidation)

> Original design (kept in historical section below) had two refresh endpoints sharing a JWT format but separate storage. [PRD 041](../041-openapi-runtime-removal.md) collapsed it to a single endpoint family — `/oauth2/*` — with one storage backend in `RefreshSessionService`. The rapla-custom `/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout` are deleted.

### Single endpoint family: `/oauth2/*`

| Endpoint | Grant / op | Role |
|---|---|---|
| `POST /oauth2/token` | `grant_type=authorization_code` | Browser-based interactive login (Swing OAuth dialog, SPA, explorers) |
| `POST /oauth2/token` | `grant_type=password` | Direct-password integration (CI scripts) — replaces former `/api/auth/login`. OAuth 2.1 BCP deprecates this grant; re-enabled here for the transient legacy path. |
| `POST /oauth2/token` | `grant_type=refresh_token` | Refresh — replaces former `/api/auth/refresh`. Same JWT format; same `RefreshSessionService.validate` backend. |
| `POST /oauth2/revoke` | RFC 7009 | Logout / explicit revocation — replaces former `/api/auth/logout`. |

All grants produce RSA-signed JWTs (RS256, `typ=access` or `typ=refresh`, persistent `RaplaKeyStorage` signing key). Resource server validates through a single `JwtDecoder` in `JwtConfig.java`.

### `RefreshSessionService` — single source of truth

Lives in `rapla-server/.../spring/RefreshSessionService.java`. Used by Spring AS custom token generator (issuance) and custom refresh provider (validation). Storage: full JWT in user prefs under `org.rapla.auth.session` (single slot per user).

| Concern | Behavior |
|---|---|
| Storage | Full JWT in `org.rapla.auth.session` prefs entry, single slot |
| Multi-tab share | `issueAndPersist(User)` returns existing valid stored token instead of minting new one — multiple devices share the same refresh JWT |
| Rotation | **Never rotate** — refresh returns same token + fresh access token until refresh expires |
| Restart-safe | Validation is stateless (signature + `typ=refresh` + exact match against prefs); persistent RSA key preserves tokens across restart |
| Revocation | `/oauth2/revoke` clears prefs → all user's refresh tokens invalidated. In-flight access tokens work until 1 h TTL |
| Single-token-per-user | No per-session tracking; one prefs slot per user |

Trade-offs (accepted): constant-size storage, multi-device trivial, no rotation conflicts, no theft-detection complexity. Con: no per-device revocation, no theft detection via rotation chain. For both → migrate to Keycloak (IdP swap is env-var only).

### Custom Spring AS wiring (`AuthorizationServerConfig`)

Six pieces — see [PRD 041](../041-openapi-runtime-removal.md#oauth-refresh-consolidation--final-architecture-added-2026-05-16) for the full table. Summary:

1. `tokenGenerator()` bean → `DelegatingOAuth2TokenGenerator` with custom `JwtRefreshTokenGenerator` (issues via `RefreshSessionService`).
2. `PublicClientRefreshTokenAuthenticationConverter` + `Provider` — public-client auth for refresh/password/revoke (Spring AS stock only handles PKCE).
3. `RaplaRefreshTokenAuthenticationProvider` — refresh-grant logic via `RefreshSessionService.validate`, never rotates.
4. `PasswordGrantAuthenticationConverter` + `Provider` — re-enables OAuth ROPC grant.
5. `RaplaTokenRevocationAuthenticationProvider` — `/oauth2/revoke` hook that clears user's session entry.

### Discovery endpoint (`/api/auth/oauth/config`)

Survivor of [PRD 041](../041-openapi-runtime-removal.md) cleanup. Emits `tokenUrl` (used for both code exchange and refresh), `authorizeUrl`, `logoutUrl` (points at `/connect/logout` for OIDC RP-initiated logout), `jwksUrl`, etc. The previous `refreshUrl` is **gone**; clients use `tokenUrl` for refresh (OAuth 2.0 standard). External-IdP providers list ([PRD 036](../036-external-idp-oauth-login.md)) also in this response — unchanged.

### Client-side wiring

- **Swing** (`MyCustomConnector.refreshUsingToken`): POST `/oauth2/token grant_type=refresh_token` form-encoded against `serverUrl + /oauth2/token`. Response parses snake_case (`access_token`, `refresh_token`).
- **Swing logout** (`RaplaClientServiceImpl`): POST `/oauth2/revoke` form-encoded with refresh JWT.
- **SPA**: `angular-oauth2-oidc` uses `tokenEndpoint` (`tokenUrl` from discovery) for both initial code exchange and refresh — no client-side change.
- **`ClientProxyConfig.RefreshOn401Interceptor.doRefresh`**: same Swing pattern, form-encoded POST to `/oauth2/token`.

---

## Architecture — historical alternatives (pre-2026-05-16)

> Retained for context. The "two refresh endpoints, one client view" design below was the original PRD intent; [PRD 041](../041-openapi-runtime-removal.md) collapsed it. Reading the historical design helps understand why some artifacts (the `jwtRefreshTokenCustomizer` `typ=refresh` claim, `RaplaKeyStorage`'s persistent signing key) exist.

### Two refresh paths under embedded auth server, one client view

Today's reality:

| Login | Issues refresh token | Validates refresh token |
|---|---|---|
| `POST /auth/login` (username+password) | `JwtIssuer` → RSA-signed, `typ=refresh`, no state | `AuthController.refresh` — signature + `typ` check only |
| `POST /oauth2/token` (OAuth code grant) | `OAuth2TokenGenerator` → RSA-signed, no `typ` claim | SAS's `OAuth2RefreshTokenAuthenticationProvider` — signature + `OAuth2Authorization` lookup |

Design unifies the client view by exposing a single `refreshUrl` in discovery and ensuring both flows produce refresh tokens that the unified `/auth/refresh` endpoint can validate. OAuth-issued refresh tokens need `typ=refresh` — added by `OAuth2TokenCustomizer`.

`/auth/refresh` stays stateless (signature + `typ`) — which is why it survives restart on the legacy path. Routing OAuth refresh through the same endpoint = OAuth refresh tokens *also* survive restart.

This bypasses SAS's `OAuth2AuthorizationService` state for refresh, which trades two things:
- ✗ No refresh-token rotation tracking.
- ✗ No per-token revocation (revoke by JWK rotation only).
- ✓ Restart-survival for free.
- ✓ Symmetric refresh across login paths.

Defensible for rapla's threat model. See Open Question §1 to revisit.

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

`refreshTokens(url, refreshToken)` POSTs `refresh_token=...` form-encoded to the configured URL. For rapla's `/auth/refresh` body is JSON (`{"refreshToken": "..."}`); for Keycloak/OAuth-standard endpoints form-encoded (`grant_type=refresh_token&...`). Helper picks shape via URL pattern or discovered `refreshUrlGrantType`. See Open Question §2.

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

Wires via Spring Auth Server's bean discovery. Adds `typ=refresh` to SAS-issued refresh tokens; access tokens unchanged.

### API key surface

Add to `AuthController` (or new `ApiKeyController`):

- `POST /auth/api-keys` — body `{ "label": "ical-feed-2026" }`, returns `{ "id": "...", "key": "rapla_xxxxx...", "label": "..." }`. `key` shown once; only its hash stored.
- `GET /auth/api-keys` — list (id, label, createdAt; no key material).
- `DELETE /auth/api-keys/{id}` — revoke.

Storage uses `RaplaKeyStorage` (already has `storeAPIKey`/`getAPIKeys`/`removeAPIKey`). `key` is a high-entropy opaque string (e.g., 32 random url-safe bytes prefixed `rapla_`), not a JWT — long-lived credentials, not user-session tokens.

Resource-server validation: `BearerTokenAuthenticationFilter` already checks `Authorization: Bearer …` against `JwtDecoder`. Extend with fallback: if token doesn't parse as JWT, try API-key path — look up by hash in `RaplaKeyStorage`.

Swing UI: small dialog accessible from user menu — "API Keys", list + new + revoke. Mirror on future Angular admin panel.

## Plan

1. **Discovery endpoint: add `refreshUrl`.** `OAuthConfigController.OAuthConfig` + Java `OAuthConfig` DTO. Default `<base>/auth/refresh`; env override. Tier-3 test.
2. **Server: `OAuth2TokenCustomizer` for `typ=refresh`.** Bean in `AuthorizationServerConfig`. Tier-3: PKCE flow, decode refresh token, assert claim.
3. **Server: relax `AuthController.refresh`** to accept SAS-issued refresh tokens. Tier-3: PKCE flow + `/auth/refresh` + use new access token.
4. **Client: `MyCustomConnector.reauth` refresh path.** Pull `refreshUrl` from `OAuthConfig`; implement `refreshTokens(url, refreshToken)`; refresh-first-then-password fallback. Tier-2 in rapla-client against test server.
5. **Client: cache `OAuthConfig`** accessible by `MyCustomConnector` (currently login-only). Likely add to `RemoteConnectionInfo`.
6. **API key endpoint surface.** `ApiKeyController` POST/GET/DELETE. Hash-on-store, plaintext-once. Resource-server fallback. Tier-3 tests per op + bearer-auth via API key.
7. **Swing UI for API keys.** Small dialog from user menu — list/create/revoke. Tier-1 view-presenter test ([PRD 023](../023-presenter-view-extraction.md) pattern).
8. **Documentation.** `docs/authentication.md` sections. PRD [029](../029-swing-oauth-login.md) + [030](../030-server-side-view-rendering.md) cross-references.
9. **Status flip to `done`** once shipped + smoke-tested.

## Tests

| Tier | Test | What it locks in |
|---|---|---|
| 3 | `OAuthConfigControllerTest` (extend) | `refreshUrl` appears in discovery |
| 3 | `AuthorizationServerTypClaimTest` | OAuth refresh tokens carry `typ=refresh` |
| 3 | `UnifiedRefreshIntegrationTest` | OAuth refresh token redeemable via `/auth/refresh` |
| 2 | `MyCustomConnectorReauthTest` | reauth on 401 hits refresh URL then retries; password fallback; session-expired when both fail |
| 3 | `ApiKeyControllerTest` | create / list / revoke / bearer-auth round trip |
| 3 | `ApiKeyAcceptedAsBearerTest` | API key in Bearer unlocks `/storage/resources` |
| 2 | `MyCustomConnectorRefreshSurvivesRestartTest` | start server, login, kill server, restart, reuse refresh token → succeeds |
| e2e | Manual `test-deployment` smoke after switching `RAPLA_OAUTH_REFRESH_URL` to a stub URL | discovery + client behavior in production-shape config |

## Open Questions

1. **Bypass SAS state or persist it?** Recommended design routes both refresh paths through stateless `/auth/refresh`, free restart-survival but losing per-token revocation. Alternative: keep SAS's stateful refresh + persistent `OAuth2AuthorizationService` (JDBC- or prefs-backed). Decision: bypass SAS state for now.
2. **Refresh request body shape: JSON vs OAuth form-encoded?** Today `/auth/refresh` accepts JSON. Keycloak/SAS `/oauth2/token` use form-encoded. Options: (a) accept both; (b) discovery field `refreshUrlGrantType`; (c) pivot to form-encoded + deprecate JSON. Decision: lean (b) — explicit, simple to evolve.
3. **Refresh-token rotation?** Spring SAS rotates by default. Should `/auth/refresh` issue new refresh too? Today returns new but old stays valid. Worth a docs note.
4. **API key prefix?** Convention is recognisable string for security scanners. `rapla_` / `rk_` / `rcs_`?
5. **API key scopes?** Today everything = "the user this key was issued for". Read-only? ical-only? Adds complexity; skip until requested.
6. **Where does the Swing "API Keys" dialog live?** User menu seems right but conflicts with new login dialog real estate ([PRD 029](../029-swing-oauth-login.md) Phase 2 OQ5). Decide together.
7. **Migration of existing /auth/login clients.** Custom rapla deployments may have third-party REST clients on `/auth/login` with its custom JSON shape. Decision: keep contract; unification is additive.
