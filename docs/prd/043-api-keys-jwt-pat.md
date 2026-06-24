# PRD 043: API Keys (GitHub-PAT flow, server-minted asymmetric JWT)

> **Update 2026-06-24 — `TokenHandler` retired; its interop is moot.** The legacy
> HMAC token path (`TokenHandler`/`SignedToken`/`ValidToken`/`RemoteSessionImpl`) was
> deleted. Everywhere below that describes `RaplaKeyStorage`/`ApiKeyJwtDecoder`
> coexisting with the `TokenHandler.refresh` `"refreshToken"` slot is now historical:
> nothing mints or reads that slot for auth anymore. The multi-slot key storage and
> the PRD 043 asymmetric-key path are unaffected; only the `TokenHandler` co-tenant is
> gone. The cleanup foreseen in "Open questions" (rename the `storeAPIKey` family once
> `TokenHandler` is retired) is now unblocked.

**Status:** in-progress (server-side complete, docs + UI pending)
**Date:** 2026-05-16
**Supersedes:** PRD 031 §"API key surface" (was draft, now superseded by this design)

## Goal

Give rapla users long-lived programmatic credentials so integrators (CI scripts, MCP servers, third-party tooling, periodic exporters) can register, list, and revoke without OAuth's interactive flows.

UX flow follows **GitHub Personal Access Tokens**: user clicks "create key", gets a single Bearer string they copy once; server keeps verification material only, never the secret.

- Client POSTs `{ label, expiresInDays }`.
- Server generates a fresh **asymmetric keypair** (RSA-2048 or Ed25519), signs a single JWT with the new private key (claims: `sub=userId`, user-chosen `exp` or absent, `kid=<thumbprint>`, `typ=api_key`), stores **only the public key** in user prefs, and **discards the private key**.
- Response returns the signed JWT once (`"key": "<jwt>"`) plus metadata. User uses it as `Authorization: Bearer <jwt>` on every API call.
- Server verifies incoming JWTs against the stored public key, checks `exp`, confirms the public-key entry is still registered.

The private key exists only for the few milliseconds it takes to mint one JWT, then is dropped. After that, **nobody — not the client, not the server — can issue another JWT against the same public key.** A stolen server backup yields public keys only (useless for impersonation); revocation is a single prefs delete.

## Why now

- Refresh-token consolidation lands in PRD 041 (`/oauth2/token` for session refresh, `/oauth2/revoke` for logout). API keys are the natural follow-on — long-lived integrator credentials share the per-user prefs registry.
- Several ongoing initiatives need long-lived credentials: PRD 035 (rapla MCP server), PRD 039 (external iCal subscriptions per resource), CI/deploy scripts.
- Asymmetric-only avoids the bearer-secret leak class entirely. A stolen backup, leaked DB snapshot, or compromised prefs store yields **public** keys only — useless for minting new credentials. Removes a class of incidents the GitHub-PAT model has had to manage operationally.

## Scope

### In scope

- **Create**: `POST /api/auth/api-keys` accepts `{ label, expiresInDays }`. Server generates fresh keypair, signs one JWT with new private key, stores public key + metadata, **discards private key**, returns `{ id, label, alg, thumbprint, createdAt, expiresAt, key: "<JWT>" }`. **`key` field is only returned here** — same contract as GitHub PAT issuance.
- **List**: `GET /api/auth/api-keys` returns metadata (no `key` field, no JWK material).
- **Revoke**: `DELETE /api/auth/api-keys/{id}` removes the public-key entry. Idempotent. Invalidates the issued JWT (signature still verifies cryptographically, but the prefs lookup that gates acceptance returns absent).
- **Custom expiration per key** (days, or null = never expire). `expiresInDays` baked into JWT's `exp` at issuance — can't be extended (private key is gone); to renew, create a new key.
- **API-key JWT format** — signed by per-key private key (server-discarded):
  - `sub`: username
  - `iat`: issued-at
  - `exp`: `iat + expiresInDays*86400`, or absent
  - `kid`: RFC 7638 thumbprint of the public key
  - `typ`: `"api_key"` (vs `access`/`refresh`)
- **Resource-server verification** on every request:
  - Decode JWT, read `typ`, `sub`, `kid`.
  - If `typ != "api_key"`, fall through to existing access-token path.
  - Load user's `API_KEYS` prefs; find entry whose thumbprint matches `kid`. Absent ⇒ reject `invalid_token` (covers "never existed" + "revoked" indistinguishably).
  - Verify JWT signature against the stored public key.
  - Validate `exp` (if present), `sub`.
- **Supported algorithms**: `RS256` (RSA ≥ 2048) or `EdDSA` (Ed25519). Server picks (configurable, default Ed25519 for short JWTs). Reject `HS*` and `none` at verification. The `alg` header on incoming JWTs must match the registered key's algorithm.

### Out of scope

- **Backward compat with legacy `TokenHandler` API-key path.** Legacy HMAC-signed `SignedToken` API keys (stored via `RaplaKeyStorage.storeAPIKey`) not migrated/dual-written/consulted. Users re-create via the new endpoint.
- **Symmetric bearer secrets** (server signs with own shared RSA key, stores `jti` denylist). Rejected: per-key keypair is the whole security point.
- **Client-side keypair generation**. Considered: more complex UX (users hold key material), no security win over server-generate-and-forget for this threat model.
- **Per-key scopes** (read-only, scope-restricted). Future PRD.
- **Per-JWT revocation** (revoke single issued JWT keeping key trusted). Each key has exactly one issued JWT — per-key = per-JWT.
- **Renewing / rotating**. Impossible by design (private half is gone). Renewal = create new + revoke old.
- **Swing UI for key management**. Spec'd in PRD 031; deferred.
- **Angular UI for key management** with one-time copy-paste box. Endpoints land first; UI follows in a separate PRD.

## Architecture

### Storage

Reuse the existing **`RaplaKeyStorage`** interface — the abstraction that already manages rapla's crypto material (root RSA keypair, login secrets, refresh tokens via the `APIKEY` slot). No new methods/types/service class; the three existing methods have the right shape, only the impl needs to honour `clientId` and be actually multi-slot:

```java
void          storeAPIKey(User user, String clientId, String apiKey);  // upsert by clientId
Collection<String> getAPIKeys(User user);                              // values of every slot
void          removeAPIKey(User user, String clientId);                // remove one slot
```

Impl stores a single JSON map `{clientId: apiKey, ...}` per user under the existing `APIKEY` `TypedComponentRole`, same pattern as `RefreshSessionService.SESSION`. Legacy `TokenHandler.refresh` keeps `clientId="refreshToken"` slot. PRD 043 keys use `clientId = <RFC 7638 thumbprint>` — one slot per registered key.

The **value** stored in each slot is the **signed JWT itself**. The JWT carries everything for listing + verification:

| Listing field | Source in the JWT |
|---|---|
| `sub` (username) | claim |
| `exp` / `iat` | claims |
| `alg` | header |
| `kid` / thumbprint | header (== the storage `clientId`) |
| `jwk` (public key) | embedded in header per RFC 7515 §4.1.3 |
| `label` | custom claim (`name`) |

Public key travels inside the JWT (RFC 7515 `jwk` header), so verification needs only the JWT plus a `getAPIKeys()` membership check. No parallel public-key table, no denormalised metadata cache. Revocation = remove the slot.

### API-key JWT (server-minted, signed by a per-key private key)

```
header:  {
  alg:  RS256,
  typ:  "JWT",
  kid:  <RFC 7638 thumbprint>,     // == storage clientId
  jwk:  { kty: "RSA", n: "...", e: "AQAB" }   // RFC 7515 §4.1.3
}
payload: {
  sub:  <userId>,                  // username
  iat:  <issued-at>,
  exp:  <iat + expiresInDays·86400> | <absent for never-expire>,
  typ:  "api_key",                 // distinguishes from access/refresh
  name: <label>                    // user-supplied display name
}
```

Minted exactly once in the POST handler with a freshly-generated keypair. Private key never leaves the handler's local scope. Compact JWT returned in `key`; user uses as `Authorization: Bearer <jwt>`.

Public key in JWT header (`jwk` per RFC 7515 §4.1.3) so verification doesn't need a parallel table — extract JWK from header, verify signature with it, confirm `kid` is still in `getAPIKeys()` set. Revocation = remove slot; signature verifies cryptographically but membership check fails.

User's **username** in JWT (`sub` claim) so server can scope `getAPIKeys()` at verify time without a session — exactly like GitHub PAT.

### Endpoints

```
POST /api/auth/api-keys
  Body:    {
             "label": "...",
             "expiresInDays": 365 | null,
             "alg": "EdDSA" | "RS256"      // optional, default EdDSA
           }
  Returns: {
             "id": "ak_xxx",
             "label": "...",
             "alg": "EdDSA",
             "thumbprint": "<RFC 7638>",
             "createdAt": "...",
             "expiresAt": "..." | null,
             "key": "<JWT — only returned here, never again>"
           }
  Auth:    Bearer access token (caller mints own keys)

GET  /api/auth/api-keys
  Returns: [ { "id", "label", "alg", "thumbprint",
               "createdAt", "expiresAt" }, ... ]
  Auth:    Bearer access token
  Note:    No `key` field, no JWK material — thumbprint only.

DELETE /api/auth/api-keys/{id}
  Returns: 204 (idempotent)
  Auth:    Bearer access token
```

### Validation chain

When `Authorization: Bearer <jwt>` arrives:

1. Decode header + payload without verifying; read `typ`, `sub`, `kid`, embedded `jwk`.
2. If `typ != "api_key"`, fall through to existing access-token verification (PRDs 031/041).
3. Resolve user from `sub`. Call `keyStore.getAPIKeys(user)`; confirm presented JWT is in the set. Absent ⇒ reject `invalid_token`.
4. Verify signature against public key embedded in header (`jwk`).
5. Validate `exp` (if present) is in the future; validate `iat` not impossibly far in future.
6. Auth succeeds; principal is `sub`.

A `RaplaJwtDecoder` wrapper in `rapla-server/spring/` peeks at `typ` and dispatches: existing `NimbusJwtDecoder` path for access/refresh, api-key path for `typ=api_key`. Wired in place of the existing `jwtDecoder` bean.

### Implementation steps

1. **Make `RaplaKeyStorageImpl` actually multi-slot** — no new methods/types. Legacy impl ignored `clientId` (single string under `APIKEY`, `removeAPIKey` threw `UnsupportedOperationException`); new impl stores JSON `Map<clientId, apiKey>` under same role. Legacy bare-string values detected on read and promoted to `clientId="refreshToken"` slot so `TokenHandler` keeps working.
2. **Keypair generation + JWT signing** in POST handler. Use Nimbus JOSE+JWT (on classpath via Spring Authorization Server) for generation, JWK encoding, RFC 7638 thumbprint, signing. Default `alg=EdDSA` (~88-byte JWK, short signatures, fast verification); `RS256` opt-in. **Mint sequence**: generate keypair → compute thumbprint → build claims → sign → persist public JWK + metadata → return. Private key ref goes out of scope at handler end. Never log private key or compact JWT.
3. **`ApiKeyController`** (`/api/auth/api-keys`) POST/GET/DELETE. Auth via existing Bearer access-token validation.
4. **`ApiKeyJwtAuthenticationProvider`** in `rapla-server/spring/`: triggers when `typ=api_key`, looks up public key by `kid`, verifies signature (Nimbus `JWSVerifier`), validates `exp`/`iat`/`sub`. Wires into Spring Security filter chain alongside access-token decoder; `typ` claim dispatches.
5. **Documentation**: `docs/authentication.md` API-key section. End-to-end curl flow + "You can only see this once" callout matching GitHub PAT pattern.

## Tests

| Tier | Class | What |
|---|---|---|
| 3 | `ApiKeyCreateTest` | POST returns `key` once; carries id/label/alg/thumbprint/expiresAt; GET omits `key` |
| 3 | `ApiKeyBearerAuthTest` | POST → use returned JWT as Bearer on `/api/resources` → 200 |
| 3 | `ApiKeyPrivateKeyDiscardedTest` | Same key cannot be re-signed (POST always generates new keypair) |
| 3 | `ApiKeyExpiryTest` | `expiresInDays=1` → use OK now; advance past day 1 → 401. `null` → no `exp`, indefinite |
| 3 | `ApiKeyRevocationTest` | Create → use OK → DELETE → re-use same JWT fails 401 |
| 3 | `ApiKeyMultiKeyTest` | Three keys per user, revoke middle one, others' JWTs keep working |
| 3 | `ApiKeyAlgConfusionTest` | JWT with `alg=HS256` or `alg=none` and same `kid` → 401 |
| 3 | `ApiKeyKidMismatchTest` | JWT with `kid` not in user's stored set → 401 |
| 3 | `ApiKeyCrossUserTest` | User A's JWT against user B's prefs → 401 (`sub` mismatch) |

## Open Questions

1. **Default algorithm** — EdDSA (short, fast) or RS256 (more universally supported)? Lean: EdDSA default, RS256 opt-in.
2. **`kid` matching strategy** — exact thumbprint vs accept-no-`kid` + try every key? Lean: require `kid` (faster, avoids signature-verify DoS).
3. **Last-used timestamp** — track for cleanup? Adds prefs write per use. Lean: skip v1.
4. **Maximum keys per user** — limit? Lean: no limit v1; revisit if abused.
5. **Audit log** — emit event on create/revoke/first-use? Future PRD.
6. **Clock skew tolerance for `exp`** — ±60 s matches Spring's default.
7. **Renewal flow** — UI "rotate" (mint new + revoke old in one call)? Server-side it's just create+delete; let UI bundle.

## Implementation notes (2026-05-16)

Server-side work landed; UI + docs are the remaining gaps.

### File map (additions + minimal edits)

| File | Purpose |
|---|---|
| `rapla-server/.../server/RaplaKeyStorage.java` | Javadoc clarified `clientId` is the slot key, value is the API-key string. **No new methods.** |
| `rapla-server/.../server/internal/RaplaKeyStorageImpl.java` | `storeAPIKey`/`getAPIKeys`/`removeAPIKey` now actually multi-slot. Storage: `Map<clientId, value>` JSON under existing `APIKEY` (`org.rapla.crypto.server.refreshToken`) prefs role. Legacy bare-string auto-promoted to `"refreshToken"` slot on read so `TokenHandler` keeps working. |
| `rapla-server/.../server/spring/web/ApiKeyController.java` | `@RestController` at `/api/auth/api-keys`. POST mints RSA-2048 keypair, signs one `typ=api_key` JWT (header: `kid` only — no embedded `jwk`), persists JSON `{kid, jwk, alg, label?, iat, exp?}`, returns JWT once. GET lists metadata (no key material). DELETE by `id`. `@PathVariable("id")` name explicit — compiler isn't preserving parameter names. |
| `rapla-server/.../server/spring/ApiKeyJwtDecoder.java` | Wraps existing `JwtDecoder`. Dispatches on `typ=api_key`: resolves user from `sub`, finds stored entry by `kid`, verifies signature against the **stored** JWK (never trusts JWT-embedded `jwk`), checks `exp`. Falls through for `typ=access`/`refresh`/external IdP tokens. |
| `rapla-server/.../server/spring/JwtConfig.java` | `jwtDecoder` bean now takes `RaplaKeyStorage` + `RaplaFacade`, wraps base decoder with `ApiKeyJwtDecoder`. Existing routing extracted into private `buildBaseDecoder` helper, unchanged. |
| `rapla-server/.../server/spring/SecurityConfig.java` | Added `auth.requestMatchers("/api/auth/api-keys", "/api/auth/api-keys/**").authenticated()` **before** the broader `/api/auth/**` permit-all — order matters. |
| `rapla-app/.../web/ApiKeyControllerIntegrationTest.java` | 8 tier-3 tests, all green. |

### Storage shape

Single JSON map per user under `APIKEY` prefs role:

```json
{
  "refreshToken": "<legacy refresh JWT — TokenHandler slot>",
  "<RFC-7638-thumbprint-1>": "{\"kid\":\"...\",\"jwk\":\"...JWK JSON...\",\"alg\":\"RS256\",\"label\":\"CI deploy\",\"iat\":...,\"exp\":...}",
  "<RFC-7638-thumbprint-2>": "..."
}
```

Each slot value is *either* a bare string (legacy refresh JWT) *or* structured JSON entry (PRD 043 asymmetric key). `ApiKeyJwtDecoder.findStoredJwk` and `TokenHandler.refresh` both tolerate the other's slots — TokenHandler does string-equality on full slot value, decoder parses each slot and skips ones that aren't valid PRD-043 JSON.

### Test coverage

`mvn -pl rapla-app -am test -Dtest=ApiKeyControllerIntegrationTest` — 8/0/0:

- `createReturnsKeyOnceListOmitsKeyMaterial`, `apiKeyJwtWorksAsBearerOnProtectedEndpoint`, `revokingRemovesAccess`, `expiredKeyIsRejected`, `neverExpiresWhenExpiresInDaysOmitted`, `multipleKeysCoexistAndIndependentRevocation`, `forgedJwtWithUnregisteredKeyIsRejected`, `twoUsersIndependentKeys`.

Side tests verified green: `ApiPrefixArchitectureTest` 3/3, `UnifiedRefreshIntegrationTest` 4/4, `OpenApiSmokeTest` 7/7.

### What's done

Storage (multi-slot, public-key-only, legacy compat); endpoints (POST/GET/DELETE authenticated); verification (api-key JWTs accepted as Bearer); security (only public keys server-side, per-key revocation, alg pinned to RS256, no algorithm-confusion attack surface — JWT-embedded `jwk` is ignored). Docs: `docs/authentication.md` API-key section rewritten end-to-end.

### What's missing

- **Angular UI** for key management (Out-of-scope, deferred) — where the GitHub-PAT-style one-time copy-paste box lives.
- **Swing UI** for key management (Out-of-scope, deferred).
- **Cleanup**: legacy `storeAPIKey`/`getAPIKeys`/`removeAPIKey` names could be renamed once `TokenHandler` is retired. Not done — interface name churn isn't worth it.

### Known limitations / OQ still pending

- **Algorithm**: RS256 only. EdDSA deferred (BouncyCastle availability check).
- **`kid` matching**: strict thumbprint equality. JWT without `kid` ⇒ `BadJwtException`. No "try every stored key" fallback (intentional — avoids DoS).
- **No `last_used_at`** tracking.
- **No max-keys-per-user** limit.
- **No audit log** entry on register/revoke/first-use.
- **Clock skew** for `exp`: Spring default for access/refresh; for api-key strict `exp.isBefore(Instant.now())` no tolerance — tests use 50 ms sleep after issuing `expiresInDays=0`. ±60 s is the right v2 default.

## Cross-references

- [PRD 031 — Refresh Tokens & API Keys](031-token-refresh-and-api-keys.md) — API-key half superseded by this PRD's asymmetric design; refresh-token half stays canonical (consolidated onto `/oauth2/token` per PRD 041).
- [PRD 041 — OpenAPI runtime removal](041-openapi-runtime-removal.md) — established `RefreshSessionService` as unified refresh-token store; this PRD shares the same prefs persistence model.
- [PRD 035 (done) — GraphQL foundations](done/035-graphql-foundations.md) + [PRD 060 — MCP foundations](060-graphql-mcp-foundations.md) — natural consumer for AI-agent service tokens.
- [PRD 039 — external iCal subscriptions per resource](039-external-ical-subscription-per-resource.md) — consumer for per-user feed credentials.
