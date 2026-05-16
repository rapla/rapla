# PRD 043: API Keys (GitHub-PAT flow, server-minted asymmetric JWT)

**Status:** in-progress (server-side complete, docs + UI pending)
**Date:** 2026-05-16
**Supersedes:** PRD 031 §"API key surface" (was draft, now superseded by this design)

## Goal

Give rapla users long-lived programmatic credentials that integrators
(CI scripts, MCP servers, third-party tooling, periodic exporters) can
register, list, and revoke without going through OAuth's interactive
flows.

UX flow follows **GitHub Personal Access Tokens**: user clicks
"create key", gets a single Bearer string they copy once (we'll show
the copy-paste box in the UI later); server keeps verification
material only, never the secret. Concretely:

- Client POSTs `{ label, expiresInDays }`.
- Server generates a fresh **asymmetric keypair** (RSA-2048 or
  Ed25519), signs a single JWT with the new private key (claims:
  `sub=userId`, user-chosen `exp` or absent, `kid=<thumbprint>`,
  `typ=api_key`), stores **only the public key** in the user's prefs,
  and **discards the private key**.
- Response returns the signed JWT once (`"key": "<jwt>"`) plus
  metadata. The JWT is the API key — the user copies it like a GitHub
  PAT and uses it as `Authorization: Bearer <jwt>` on every API call.
- Server verifies incoming JWTs against the stored public key, checks
  `exp`, and confirms the matching public-key entry is still
  registered.

The private key exists only for the few milliseconds it takes to
mint one JWT, then is dropped. After that, **nobody — not the
client, not the server — can issue another JWT against the same
public key.** A stolen server backup yields public keys only
(useless for impersonation); revocation is a single prefs delete.

## Why now

- Refresh-token consolidation lands in PRD 041 (`/oauth2/token` for
  session refresh, `/oauth2/revoke` for logout). API keys are the
  natural follow-on: long-lived integrator credentials use the same
  per-user prefs registry as session refresh tokens.
- Several ongoing initiatives need long-lived credentials:
  - PRD 035 (rapla MCP server) — service tokens for AI agents
  - PRD 039 (external iCal subscriptions per resource) — feed URLs
    bearing per-user credentials
  - CI / deploy scripts that today re-login on every run
- Asymmetric-only avoids the bearer-secret leak class entirely. A
  stolen backup, a leaked database snapshot, or even a compromised
  prefs store yields **public** keys only — useless for minting new
  credentials. This matches the security posture we want for
  long-lived integrator credentials and removes a class of incidents
  the GitHub-PAT model has had to manage operationally.

## Scope

### In scope

- **Create**: `POST /api/auth/api-keys` accepts `{ label, expiresInDays }`.
  Server generates a fresh keypair, signs one JWT with the new private
  key, stores the public key + metadata in user prefs, **discards the
  private key**, and returns:
  `{ id, label, alg, thumbprint, createdAt, expiresAt, key: "<JWT>" }`.
  **The `key` field is the only time the JWT crosses the wire** — same
  contract as GitHub PAT issuance.
- **List**: `GET /api/auth/api-keys` returns metadata for the user's
  keys (label, algorithm, RFC 7638 JWK thumbprint, `createdAt`,
  `expiresAt`). **No `key` field.** No JWK / key material.
- **Revoke**: `DELETE /api/auth/api-keys/{id}` removes the public-key
  entry. Idempotent. Invalidates the issued JWT (signature still
  verifies cryptographically, but the prefs lookup that gates
  acceptance returns absent).
- **Custom expiration per key** (days, or null = never expire).
  `expiresInDays` is baked into the JWT's `exp` claim at issuance
  (the server-generated private key is gone afterwards, so the
  expiry can't be extended — to renew, create a new key).
- **API-key JWT format** — signed by the per-key private key the
  server generates and immediately discards:
  - `sub`: username (the issuing user)
  - `iat`: issued-at
  - `exp`: `iat + expiresInDays*86400`, or absent for never-expire
  - `kid`: RFC 7638 thumbprint of the public key — links the JWT to
    its stored verification material
  - `typ`: `"api_key"` (distinguishes from `typ=access` / `typ=refresh`)
- **Resource-server verification** on every request:
  - Decode JWT, read `typ`, `sub`, `kid`.
  - If `typ != "api_key"`, fall through to existing access-token path.
  - Load the user's `API_KEYS` prefs; find the entry whose thumbprint
    matches `kid`. If absent ⇒ reject with `invalid_token` (covers
    both "never existed" and "revoked", indistinguishable on the wire).
  - Verify JWT signature against the stored public key.
  - Validate `exp` (if present), `sub`.
- **Supported algorithms**: `RS256` (RSA ≥ 2048) or `EdDSA` (Ed25519).
  Server picks (configurable, default Ed25519 for short JWTs). Reject
  `HS*` and `none` at verification time. The `alg` header on
  incoming JWTs must match the registered key's algorithm.

### Out of scope

- **Backward compatibility with the legacy `TokenHandler` API-key
  path.** Legacy HMAC-signed `SignedToken` API keys (stored via
  `RaplaKeyStorage.storeAPIKey`) are not migrated, not dual-written,
  and not consulted by the new code path. Users with legacy keys
  re-create via the new endpoint.
- **Symmetric bearer secrets** (server signs with its own shared RSA
  key, stores a `jti` denylist). Rejected: per-key keypair is the
  whole security point — a leaked prefs store yields useless public
  keys, and the server itself cannot mint new JWTs against an
  existing key once the private half is discarded.
- **Client-side keypair generation** (user generates keypair locally,
  uploads only the public half). Considered and dropped: more
  complex UX (users hold key material, must use a signing tool),
  no security win over server-generate-and-forget for this threat
  model (the threat being a server-side leak — and the server keeps
  only public keys in either design). If future use cases need the
  user to hold a long-lived signing key, a separate PRD.
- **Per-key scopes** (read-only, scope-restricted). All keys today
  carry full access of the issuing user. Future PRD.
- **Per-JWT revocation** (revoke a single issued JWT but keep the
  key trusted). Each key has exactly one issued JWT (private half
  discarded), so per-key revocation IS per-JWT revocation.
- **Renewing / rotating** an existing key (extending `exp`, re-issuing
  with a longer lifetime). Impossible by design — the private half is
  gone. Renewal = create a new key + revoke the old one.
- **Swing UI for key management.** Spec'd in PRD 031 §"API key surface".
  Server endpoints land in this PRD; Swing dialog deferred.
- **Angular UI for key management** with one-time copy-paste box.
  Same — endpoints land first, UI follows in a separate PRD.

## Architecture

### Storage

Reuse the existing **`RaplaKeyStorage`** interface — the abstraction
that already manages rapla's crypto material (root RSA keypair in
system prefs, login secrets, refresh tokens via the `APIKEY` slot).
No new methods, no new types, no new service class. The three existing
methods already have the right shape; only the implementation needs
to honour `clientId` and be actually multi-slot:

```java
void          storeAPIKey(User user, String clientId, String apiKey);  // upsert by clientId
Collection<String> getAPIKeys(User user);                              // values of every slot
void          removeAPIKey(User user, String clientId);                // remove one slot
```

Implementation stores a single JSON map `{clientId: apiKey, ...}` per
user under the existing `APIKEY` `TypedComponentRole`, same pattern
that `RefreshSessionService.SESSION` uses (one prefs key, one JSON
value per user). Legacy `TokenHandler.refresh` keeps its existing
`clientId="refreshToken"` slot — same behaviour: one refresh token
per user, overwritten on regenerate. PRD 043 keys use
`clientId = <RFC 7638 thumbprint>` — one slot per registered key,
near-zero collision probability across independent keypairs.

The **value** stored in each slot is the **signed JWT itself**. The
JWT carries everything needed for listing + verification:

| Listing field | Source in the JWT |
|---|---|
| `sub` (username) | claim |
| `exp` / `iat` | claims |
| `alg` | header |
| `kid` / thumbprint | header (== the storage `clientId`) |
| `jwk` (public key) | embedded in header per RFC 7515 §4.1.3 |
| `label` | custom claim (`name`) |

The public key travels inside the JWT (RFC 7515 `jwk` header), so
verification needs only the JWT plus a "is this `kid` still in the
user's `getAPIKeys()` set?" check. No parallel public-key table, no
denormalised metadata cache. Revocation = remove the slot.

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

Minted exactly once, in the POST handler, with a freshly-generated
keypair. The private key never leaves the handler's local scope.
The signed compact JWT string is returned in the response's `key`
field; the user copies it and uses it as
`Authorization: Bearer <jwt>` until it expires or is revoked.

The public key travels in the JWT header (`jwk` per RFC 7515 §4.1.3)
so verification doesn't need a parallel public-key table — extract
the JWK from the header, verify the signature with it, and confirm
the JWT's `kid` is still in the user's `getAPIKeys()` set. Revocation
= remove the slot; the JWT's signature still verifies cryptographically
but the membership check fails.

The user's **username** is part of the JWT (`sub` claim) so the
server can scope its `getAPIKeys()` lookup at verify time without
needing a session — exactly like a GitHub PAT carries enough
information for the server to identify the owning user.

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

When an `Authorization: Bearer <jwt>` arrives at any rapla REST endpoint:

1. Decode the JWT header + payload without verifying signature; read
   `typ`, `sub`, `kid`, and the embedded `jwk`.
2. If `typ != "api_key"`, fall through to the existing access-token
   verification (PRDs 031/041).
3. Resolve user from `sub`. Call `keyStore.getAPIKeys(user)` and
   confirm the presented JWT string is in the set (membership check).
   If absent ⇒ reject with `invalid_token` (covers "never existed"
   and "revoked" indistinguishably).
4. Verify the JWT signature against the public key embedded in its
   own header (`jwk`).
5. Validate `exp` (if present) is in the future; validate `iat` is
   not impossibly far in the future (clock-skew sanity).
6. Authentication succeeds; principal is `sub`.

A `RaplaJwtDecoder` wrapper in `rapla-server/spring/` peeks at `typ`
and dispatches: existing `NimbusJwtDecoder` path for
access/refresh tokens (rapla-signed RSA), api-key path for
`typ=api_key`. Wired in place of the existing `jwtDecoder` bean —
SecurityConfig's `oauth2ResourceServer.jwt(decoder)` picks it up
without further changes.

1. **Make `RaplaKeyStorageImpl` actually multi-slot** — no new
   interface methods, no new types. The legacy impl ignored
   `clientId` (single string under `APIKEY` role, `removeAPIKey`
   threw `UnsupportedOperationException`); the new impl stores a
   JSON `Map<clientId, apiKey>` under the same `APIKEY` role.
   Legacy bare-string values detected on read and promoted to the
   `clientId="refreshToken"` slot so `TokenHandler` keeps working
   across the format flip.
2. **Keypair generation + JWT signing** in the POST handler.
   - Use Nimbus JOSE+JWT (already on classpath via Spring Authorization
     Server) for keypair generation, JWK encoding, RFC 7638 thumbprint,
     and signing.
   - Default `alg=EdDSA` (Ed25519) — ~88-byte JWK, short signatures,
     fast verification. `RS256` opt-in via the `alg` request field
     for clients that can't do EdDSA yet.
   - **Mint sequence**: generate keypair → compute thumbprint → build
     claims (`sub`, `iat`, `exp?`, `typ=api_key`) → sign JWT with
     private key → persist public JWK + metadata to prefs → return
     JWT + metadata. The private key reference goes out of scope at
     the end of the handler; no further references exist.
   - Defensive: never log the private key or the compact JWT (the JWT
     contains no secrets by itself, but it IS the credential — keep
     it out of access logs).
3. **`ApiKeyController`** (`/api/auth/api-keys`) with POST/GET/DELETE.
   - Auth via existing Bearer access-token validation (so the user
     must be logged in to manage their keys).
4. **`ApiKeyJwtAuthenticationProvider`** in `rapla-server/spring/`:
   - Triggered when an incoming Bearer JWT has `typ=api_key`.
   - Looks up the user's public key by `kid`, verifies the signature
     (Nimbus `JWSVerifier`), validates `exp`/`iat`/`sub`.
   - Wires into the existing Spring Security filter chain alongside
     the access-token decoder; `typ` claim dispatches.
5. **Documentation**: `docs/authentication.md` API-key section.
   - End-to-end curl flow: log in → POST `{label, expiresInDays}` →
     copy `key` from response → use as `Authorization: Bearer` on
     subsequent calls.
   - "You can only see this once" callout matching the GitHub PAT
     pattern (when the future UI lands, the same callout goes on the
     create-key dialog).

## Tests

| Tier | Class | What |
|---|---|---|
| 3 | `ApiKeyCreateTest` | POST returns `key` once; response carries id/label/alg/thumbprint/expiresAt; GET afterwards omits the `key` field |
| 3 | `ApiKeyBearerAuthTest` | POST → use returned JWT as `Authorization: Bearer` on `/api/resources` → 200 |
| 3 | `ApiKeyPrivateKeyDiscardedTest` | Same key cannot be re-signed: server has no API to mint another JWT for an existing thumbprint (POST always generates a new keypair) |
| 3 | `ApiKeyExpiryTest` | `expiresInDays=1` → use OK now; advance clock past day 1 → 401. `expiresInDays=null` → no `exp` in JWT, accepted indefinitely |
| 3 | `ApiKeyRevocationTest` | Create → use OK → DELETE → re-use same JWT fails 401 (signature still verifies; revocation via prefs lookup) |
| 3 | `ApiKeyMultiKeyTest` | Three keys per user, revoke middle one, others' JWTs keep working |
| 3 | `ApiKeyAlgConfusionTest` | JWT with `alg=HS256` or `alg=none` and same `kid` → 401 (no algorithm-confusion attacks) |
| 3 | `ApiKeyKidMismatchTest` | JWT with `kid` not in the user's stored set → 401 (covers "never existed" + "revoked" indistinguishably) |
| 3 | `ApiKeyCrossUserTest` | User A's JWT against user B's prefs → 401 (`sub` mismatch caught even if `kid` accidentally collided) |

## Open Questions

1. **Default algorithm** — EdDSA (short, fast) or RS256 (more
   universally supported by older client libraries)? Lean: EdDSA
   default, RS256 opt-in via the `alg` request field.
2. **`kid` matching strategy** — match by exact thumbprint (current
   plan), or also accept JWTs with no `kid` and try every registered
   key? Lean: require `kid` (faster, clearer errors, avoids
   signature-verify DoS over many keys).
3. **Last-used timestamp** — track and expose in GET response? Useful
   for "abandoned key" cleanup. Adds a prefs write per use (cost).
   Lean: skip for v1.
4. **Maximum keys per user** — limit? Keycloak has a soft limit (~10
   per user). Lean: no limit for v1; revisit if abuse seen.
5. **Audit log** — emit event on create / revoke / first-use? Future
   PRD alongside other audit work.
6. **Clock skew tolerance for `exp`** — strict or ±60 s? Most JWT
   libraries default to ~30 s. Lean: ±60 s, matches Spring's default.
7. **Renewal flow** — should the UI later offer "rotate" (mint a new
   key with the same label + expiry, revoke the old one in a single
   call)? Convenient but server-side it's just create+delete. Lean:
   keep server endpoints atomic; let the UI bundle the two calls.

## Implementation notes (2026-05-16)

Server-side work landed; UI + docs are the remaining gaps.

### File map (additions + minimal edits)

| File | Purpose |
|---|---|
| `rapla-server/.../server/RaplaKeyStorage.java` | Javadoc clarified `clientId` is the slot key, value is the API-key string. **No new interface methods.** |
| `rapla-server/.../server/internal/RaplaKeyStorageImpl.java` | `storeAPIKey`/`getAPIKeys`/`removeAPIKey` now actually multi-slot. Storage: `Map<clientId, value>` JSON under the existing `APIKEY` (`org.rapla.crypto.server.refreshToken`) prefs role. Legacy bare-string values auto-promoted to the `"refreshToken"` slot on read so `TokenHandler` keeps working. |
| `rapla-server/.../server/spring/web/ApiKeyController.java` | `@RestController` at `/api/auth/api-keys`. POST mints RSA-2048 keypair, signs one `typ=api_key` JWT (header: `kid` only — no embedded `jwk`), persists JSON `{kid, jwk, alg, label?, iat, exp?}`, returns the JWT once. GET lists metadata (no key material). DELETE by `id`. `@PathVariable("id")` name explicit — compiler isn't preserving parameter names. |
| `rapla-server/.../server/spring/ApiKeyJwtDecoder.java` | Wraps the existing `JwtDecoder`. Dispatches on `typ=api_key`: resolves user from `sub`, finds stored entry by matching `kid`, verifies signature against the **stored** JWK (never trusts JWT-embedded `jwk`), checks `exp`. Falls through for `typ=access` / `typ=refresh` / external IdP tokens. |
| `rapla-server/.../server/spring/JwtConfig.java` | `jwtDecoder` bean now takes `RaplaKeyStorage` + `RaplaFacade` and wraps the base decoder with `ApiKeyJwtDecoder`. Existing routing logic extracted into a private `buildBaseDecoder` helper, unchanged. |
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

Each slot value is *either* a bare string (legacy refresh JWT) *or* a structured JSON entry (PRD 043 asymmetric key). `ApiKeyJwtDecoder.findStoredJwk` and `TokenHandler.refresh` both tolerate the other's slots — TokenHandler does string-equality on the full slot value, the decoder parses each slot and skips ones that aren't valid PRD-043 JSON.

### Test coverage

`mvn -pl rapla-app -am test -Dtest=ApiKeyControllerIntegrationTest` — 8 / 0 / 0 (run / fail / error):

- `createReturnsKeyOnceListOmitsKeyMaterial` — POST returns `key`, GET omits it
- `apiKeyJwtWorksAsBearerOnProtectedEndpoint` — api-key JWT works on `/api/resources`
- `revokingRemovesAccess` — DELETE → subsequent Bearer fails 401
- `expiredKeyIsRejected` — `expiresInDays=0` JWT rejected after sleep
- `neverExpiresWhenExpiresInDaysOmitted` — null `exp` works indefinitely
- `multipleKeysCoexistAndIndependentRevocation` — 3 keys, revoke middle, others keep working
- `forgedJwtWithUnregisteredKeyIsRejected` — attacker-minted JWT with own keypair → 401 (kid not registered)
- `twoUsersIndependentKeys` — homer + monty each mint a key, both work for owner

Side tests verified green:
- `ApiPrefixArchitectureTest` 3/3 (controller in `/api/` namespace + exactly one openapi group)
- `UnifiedRefreshIntegrationTest` 4/4 (`TokenHandler`'s `"refreshToken"` slot intact under the multi-slot storage)
- `OpenApiSmokeTest` 7/7

### What's done

- Storage: multi-slot, public-key-only entries, legacy compat preserved.
- Endpoints: POST/GET/DELETE wired and authenticated.
- Verification: api-key JWTs accepted as `Authorization: Bearer` on every protected endpoint.
- Security: server-side leak yields only public keys; per-key revocation; alg pinned to RS256; no algorithm-confusion attack surface (JWT-embedded `jwk` is ignored).

### What's missing

- **`docs/authentication.md` API-key section** (Plan §5). End-to-end curl flow + "you can only see this once" callout. Untouched.
- **Angular UI** for key management (Out-of-scope, deferred). The future UI is where the GitHub-PAT-style one-time copy-paste box lives.
- **Swing UI** for key management (Out-of-scope, deferred).
- **Cleanup**: the legacy `storeAPIKey`/`getAPIKeys`/`removeAPIKey` names in `RaplaKeyStorage` could be renamed (`storeApiKey` lowercase) once `TokenHandler` is retired. Not done — touching the interface name churns more than it helps.

### Known limitations / Open Questions still pending

- **Algorithm**: RS256 only. EdDSA (Ed25519) deferred — would need BouncyCastle availability check on classpath, not done.
- **`kid` matching**: strict thumbprint equality. JWT without `kid` ⇒ `BadJwtException`. No "try every stored key" fallback (intentional — avoids DoS through signature-verify over many keys).
- **No `last_used_at`** tracking — would need a per-verify prefs write (cost).
- **No max-keys-per-user** limit.
- **No audit log** entry on register / revoke / first-use.
- **Clock skew** for `exp`: handled by Spring's default `JwtDecoder` for access/refresh; for api-key JWTs the decoder does a strict `exp.isBefore(Instant.now())` with no tolerance — tests rely on a 50 ms sleep after issuing an `expiresInDays=0` key. Tolerance window ±60 s is the right v2 default.

## Cross-references

- [PRD 031 — Refresh Tokens & API Keys](031-token-refresh-and-api-keys.md) —
  the API-key half of PRD 031 (which assumed bearer-secret tokens) is
  superseded by this PRD's asymmetric design; the refresh-token half
  stays canonical (now consolidated onto `/oauth2/token` per PRD 041).
- [PRD 041 — OpenAPI runtime removal](041-openapi-runtime-removal.md) —
  established `RefreshSessionService` as the unified refresh-token
  store; this PRD shares the same user-prefs persistence model.
- [PRD 035 — rapla MCP server](035-rapla-mcp-server.md) — natural
  consumer of API keys for AI-agent service tokens.
- [PRD 039 — external iCal subscriptions per resource](039-external-ical-subscription-per-resource.md) —
  consumer for per-user feed credentials.
