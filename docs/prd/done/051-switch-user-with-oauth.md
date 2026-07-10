# PRD 051: Restore "switch to user" admin feature with OAuth-only auth

**Status:** done — SPA shipped 2026-05-22. Swing parity superseded by [PRD 052](../052-client-clean-restart.md) (see § Closed scope below).
**Date:** 2026-05-21 (closed 2026-05-22)

## Closed scope (2026-05-22)

### Shipped

- **Server** — `ImpersonationController` + `UsersController` ([PRD 049](../049-controller-interface-deduplication.md)
  `@HttpExchange` pattern), `JwtConfig.JwtIssuer.issueImpersonationToken`,
  `AuthorizationServerConfig.jwtTokenCustomizer` injecting
  `preferred_username` + `name` on rapla-SAS tokens so the SPA's user
  chip works for every grant type. Audit logging, `canAdminUser` gate,
  no-server-side-state guarantee — all live.
- **Angular SPA** — chip + dialog flow, mid-impersonation switching using
  admin Bearer, impersonation-override interceptor renewal sequencer,
  "Switch back" / "Sign out" toolbar, `swap_horiz` / `person_search`
  chip-icon affordance.
- **Tests landed** — tier-3 MockMvc for `/api/auth/impersonate` (10
  cases) and `/api/users` (5 cases), tier-5 Vitest for `UsersService`
  admin-Bearer routing and `AuthService` logout paths clearing the override.
- **Docs landed** — `docs/authentication.md` § "Group administration
  policy", § "Admin impersonation", § "Switching from one target to
  another mid-impersonation" (with the authoritative "impersonation
  tokens cannot themselves invoke the impersonation endpoints" rule —
  the one deliberate exception is the SPA cookie switch endpoint
  `AuthCookieController.impersonateSwitch`, which re-resolves the admin
  via `act.sub` and is CSRF-protected), endpoint-reference table
  additions, visual-indicator section.

### Superseded by [PRD 052](../052-client-clean-restart.md)

- **Plan §7** — Swing `RaplaClientServiceImpl.switchTo(User)` rewrite
  (in-place token swap). [PRD 052](../052-client-clean-restart.md) resolves OQ6: switch-to-user and
  switch-back ride the same close+recreate session channel as logout.
  [PRD 052](../052-client-clean-restart.md)'s `BlockingQueue<NextSession>` + `NextSession.reconnectAs(...)`
  is the live design.
- **Plan §8** — Swing status-bar indicator. The visible UI element stays
  in scope; only the underlying session-swap mechanism changes per
  [PRD 052](../052-client-clean-restart.md). Ships under [PRD 052](../052-client-clean-restart.md).
- **Tests S2 / S3** — `RefreshOn401InterceptorImpersonationTest`,
  `MyCustomConnectorReauthImpersonationTest`. Designed against in-place
  token-swap; replaced by [PRD 052](../052-client-clean-restart.md)'s close-recreate test plan.

### Deferred / accepted gaps

| Gap | Why deferred | If/when to revisit |
|---|---|---|
| `ImpersonationWithExternalIdpTest` (Keycloak-authenticated admin can impersonate) | Stubbed-JWKS fixture work non-trivial; multi-issuer decoder path covered by `ExternalAuthLifecycleIntegrationTest`; impersonation correctness is independent of actor's issuer. | When DHBW runs the feature for the first time. |
| Group-admin → out-of-scope target (403) | `testdefault.xml` has no third user outside `monty`'s scope. 403 branch exercised via `groupAdminCannotImpersonateGlobalAdmin`; `belongsTo`-false branch asserted via unit `PermissionController` coverage. | Add a fixture user when another PRD needs one. |
| `CallbackComponentClearsImpersonationOverrideTest` (tier 6) | Lower-risk path — override is in-memory only; a fresh OAuth code lands in a new `AuthService` lifecycle in production. | When the override moves out of `signal()` memory. |
| Live cross-IdP browser verification (DHBW Keycloak admin → local user) | All-rapla-SAS flow verified end-to-end 2026-05-22. | Next session with DHBW Keycloak available. |
| Native Swing flow | User explicitly scoped to SPA-only ("we only do spa", 2026-05-22). | Handled by [PRD 052](../052-client-clean-restart.md). |

PRD closed: SPA feature is live + verified + regression-tested; Swing path has successor [PRD 052](../052-client-clean-restart.md).

## Goal

Restore the admin-only **"Switch to user"** feature in the rapla
clients so an admin can view rapla as another user without their
password, work in their context, and switch back with one click. The
feature shipped pre-[PRD 041](../041-openapi-runtime-removal.md) via `/api/auth/login`'s `connectAs` field;
[PRD 041](../041-openapi-runtime-removal.md) deleted that endpoint when consolidating onto `/oauth2/token`,
and the OAuth2 password grant has no equivalent. With [PRD 036](../036-external-idp-oauth-login.md) +
Keycloak, rapla no longer mints the token for external-IdP logins at
all, so even reintroducing `connectAs` wouldn't cover the dominant SSO case.

## Why this is needed

1. **Daily admin operation.** Triage of "user says reservation X
   disappeared" / "why can't student Y allocate room Z" is a guessing
   game without it. Pre-OAuth, every admin used it routinely.
2. **OAuth2 password grant has no `connectAs`** by RFC 6749 design.
   Impersonation is a separate OAuth concern (RFC 8693 Token Exchange).
   Re-introducing `connectAs` would be a rapla-only extension breaking
   the "any standards-compliant OIDC client works" property [PRD 041](../041-openapi-runtime-removal.md) set up.
3. **External IdPs issue the token.** Rapla can't produce a Keycloak-
   signed token claiming `sub: <target>`. The impersonation token must
   be signed by rapla's own SAS — and the auth pipe must accept it
   alongside externally-issued tokens.
4. **`canAdminUser` is the authorization rule already.** Legacy
   `checkConnectAsRights` already enforces "actor must be admin of
   target" — keep that unchanged; only how the impersonation intent
   reaches the server changes.

## Scope

### In scope

- **New endpoint `POST /api/auth/impersonate`** (rapla-namespaced; under
  `/api/auth/oauth/**` so it benefits from the dedicated SecurityFilterChain
  added 2026-05-21 — see `docs/authentication.md` § "401 handling on
  the SPA"). Request:
  ```http
  POST /api/auth/impersonate
  Authorization: Bearer <admin's current access token, any issuer>
  Content-Type: application/x-www-form-urlencoded

  target_username=alice
  ```
  Response on success (`200`):
  ```json
  {
    "access_token": "<rapla-SAS-signed JWT, sub=alice's UUID, act={sub: admin UUID}>",
    "token_type":   "Bearer",
    "expires_in":   3600
  }
  ```
  **No refresh_token is issued** — see § "Token renewal model".
  Impersonation tokens are short-lived (1 hour, matching rapla-SAS's
  `access-token-time-to-live: 1h`); renewal happens by calling
  `/api/auth/impersonate` again with the admin's current Bearer.
  Eliminates server-side state, removes the long-lived impersonation
  credential from client disk, forces every renewal through a fresh
  `canAdminUser` check.
- **`act` claim per RFC 8693** carries the original actor:
  ```json
  "act": { "sub": "<admin UUID>", "username": "admin" }
  ```
  Every action under an impersonation token is traceable to the
  initiating admin; audit log emits on every issuance.
- **Server-side validation chain:**
  1. Decode the Bearer via the multi-issuer `JwtDecoder` (any
     accepted issuer).
  2. Resolve actor → rapla `User` via `SpringSecurityRemoteSession.resolveJwtOrThrow`
     (username-as-identity per [PRD 036](../036-external-idp-oauth-login.md)).
  3. Resolve `target_username` → rapla `User` via case-insensitive
     lookup. 404 if not found.
  4. `PermissionController.canAdminUser(actor, target)` — same rule as
     legacy `connectAs`. 403 on failure.
  5. Mint rapla-SAS JWT with `sub=target.id`, `username=target.username`,
     `act.sub=actor.id`, `act.username=actor.username`, standard
     `iat`/`exp`/`iss`/`aud`. TTL: 1 h (matching rapla-SAS).
- **Refresh of impersonation tokens** uses the standard
  `/oauth2/token grant_type=refresh_token` path because the refresh
  token is rapla-SAS-issued. Spring AS gets a small adapter to preserve
  the `act` claim across refresh (without it, refresh silently drops
  `act` — audit trail breaks).
- **Swing wiring (`RaplaClientServiceImpl.switchTo(User)`):** while
  still authenticated as admin, call `/api/auth/impersonate { target_username }`,
  receive new tokens, store OLD admin tokens in `TokenStore` under
  `"original_admin_tokens"`, swap active tokens, restart session.
  "Switch back" reads the stored admin tokens and swaps them back.
  Removes Swing's dependency on having the admin's password in memory
  (`ConnectInfo.password` was a pre-OAuth holdover and security smell).
- **`canSwitchBack()`** stays as today's API but flips based on
  `original_admin_tokens` presence in `TokenStore`.
- **Visual indicator in Swing menu bar** while impersonating: status-bar
  "Signed in as alice (as admin)" with a `[Switch back]` link. The
  `act` claim on the live access token is the source of truth.
- **Server-side endpoint security:** dedicated SecurityFilterChain
  matcher `/api/auth/impersonate` requires an authenticated Bearer
  (the `/api/auth/oauth/**` `permitAll` carve-out does NOT apply).
- **Audit log line on every issuance:**
  ```
  INFO  rapla.audit - Impersonation: actor=admin (uuid=...) target=alice (uuid=...) at=<iso8601>
  ```

### Out of scope

- **Self-service "switch to me"** for non-admins — same rule as legacy.
- **Hybrid mode** (rapla authorizes, IdP issues token) — deferred (§ Future enhancements).
- **Server-pushed "this admin is impersonating you" notification** — audit log is the record.
- **Time-bounded impersonation session** — standard 1-h TTL + manual "Switch back" cover v1.
- **Angular SPA "switch to user" UI** — server endpoint is shipped now (Swing uses it); SPA picks it up when the user-admin screen lands.

## Architecture

### Rapla's group-administration policy — the authorization rule

The "who can impersonate whom" decision is `canAdminUser(adminUser,
target)`, in
[`rapla-core/.../storage/PermissionController.java`](../../rapla-core/src/main/java/org/rapla/storage/PermissionController.java)
at line 697:

```java
public static boolean canAdminUser(User adminUser, User target) {
    if (adminUser.isAdmin()) return true;                       // global admin → all
    if (getAdminGroups(adminUser).isEmpty()) return false;      // not a group-admin → none
    if (target.isAdmin()) return false;                         // group-admin can't impersonate global admin
    for (Category scope : getGroupsToAdmin(adminUser)) {
        if (target.belongsTo(scope)) return true;
    }
    return false;
}
```

Two layers of admin authority:

| Layer | Source | Scope |
|---|---|---|
| **Global admin** | `User.isAdmin() == true` | All users |
| **Group admin** | `Category.getAnnotation(CategoryAnnotations.CAN_ADMIN_PARENT) == "true"` on a category in the user's `groupList` | Users whose group list intersects `group.getParent()` transitively |

#### How the `can_admin_parent` annotation works

The annotation key is
[`CategoryAnnotations.CAN_ADMIN_PARENT`](../../rapla-core/src/main/java/org/rapla/entities/CategoryAnnotations.java)
(string `"can_admin_parent"`). When set to `"true"` on category C,
any user whose group list contains C is treated as admin of C's
*parent* category and transitively of every user whose group list
belongs (transitively) to that parent.

Worked example:

```
/groups
  /groups/department-1
    /groups/department-1/admins        ← annotation: can_admin_parent=true
    /groups/department-1/students
  /groups/department-2
    /groups/department-2/admins        ← annotation: can_admin_parent=true
    /groups/department-2/staff
```

- `alice` ∈ `/groups/department-1/admins`:
  `getAdminGroups(alice) = [/groups/department-1/admins]`,
  `getGroupsToAdmin(alice) = [/groups/department-1]`. Admin of every
  user in department-1, **not** department-2.
- `bob` ∈ `/groups/department-1/students`: `getAdminGroups(bob) = []` — not a group-admin.
- `User.belongsTo(group)` (`UserImpl.java:215`) is transitive via `getGroupsIncludingParents`.

#### Why this rules out IdP-side authorization

The full scoping rule reads four pieces of rapla state (`Category`
tree, `can_admin_parent` annotation, admin's `groupList`, target's
`groupList`, both `isAdmin` flags) — none of which is exposed to the
IdP. A rapla `Category` is not a Keycloak group; Keycloak's
`impersonate-users` permission is one binary realm-level flag. Even
with token-exchange + `impersonate-users`, the resulting authority
would be **realm-wide** — including users in departments the rapla
admin has no authority over. The scoping data isn't in the JWT or
anywhere outside rapla, so the `canAdminUser` check has to run in
rapla code, and the impersonation token has to be minted on the same
side as the check.

### Load-bearing principle: rapla can mint tokens for any rapla user

Rapla's embedded Spring Authorization Server controls its own
RSA-signing key (`RaplaKeyStorage`, per [PRD 029](../029-swing-oauth-login.md) Phase 1) and can sign
a JWT for any rapla user identity provided server-side rules allow.
External IdPs **cannot** do this — they only issue tokens whose `sub`
is the human who authenticated to them.

The token-swap design exploits this asymmetry: **regardless of which
IdP the admin originally authenticated with, the impersonation token
is always minted by rapla's own SAS.** The admin's incoming Bearer
(any issuer) is decoded, actor resolved via `ExternalUserResolver`,
`canAdminUser` runs against rapla data, and the new token is signed
by rapla. Keycloak never sees the impersonation request.

This is why every alternative requiring the IdP to issue the
impersonation token (RFC 8693 strict, RFC 7523 user-assertion,
`act_as:` scopes) is rejected: they only work when IdP policy
cooperates, and rapla deployments don't control external IdP realm
policy. Token swap is the **only** option that works for the
IdP-replaceable architecture PRD 031 + [PRD 036](../036-external-idp-oauth-login.md) established.

### Why a rapla-namespaced endpoint, not a SAS extension

Spring Authorization Server's `/oauth2/token` is RFC-bound. Adding a
non-standard parameter or custom grant would break standards
conformance and tie the impersonation contract to the SAS endpoint
(which in external-IdP setups hits the IdP, not rapla). A
rapla-namespaced `/api/auth/impersonate` is cleanly under rapla's
control regardless of which IdP issued the actor's Bearer.

### SecurityFilterChain wiring

| Chain | Matcher | Auth required | Why |
|---|---|---|---|
| `@Order(0)` (existing) | `/api/auth/oauth/**` | none | Pre-auth endpoints (BFF code exchange, discovery). Skips the resource-server JWT filter so stale Bearers don't 401 before the controller runs. |
| `@Order(0.5)` (new) | `/api/auth/impersonate` | **yes** | Impersonation requires a valid actor Bearer. Standard `oauth2ResourceServer().jwt(...)`. Adding it to the pre-auth chain would make it callable anonymously — privilege escalation. |
| `@Order(2)` (existing main) | `anyRequest()` | as today | Untouched. |

A third `SecurityFilterChain` bean with `securityMatcher("/api/auth/impersonate")`
and `oauth2ResourceServer` explicitly installed. Verified by tier-3
MockMvc: anon → 401, non-admin → 403, admin + missing target → 404.

### Wire format vs. RFC 8693

Standards-compliant RFC 8693 would be:
```
POST /oauth2/token
grant_type=urn:ietf:params:oauth:grant-type:token-exchange
subject_token=<target's token>
actor_token=<admin's token>
requested_token_type=urn:ietf:params:oauth:token-type:access_token
```
But rapla doesn't have the target's token — the whole point is the
admin doesn't know their password. So the rapla endpoint takes
`target_username` and mints a token. **Not** RFC 8693 (we don't have
the inputs); the response shape and `act` semantics are borrowed.

The rapla-namespaced URL makes this asymmetry explicit. A future
deployment wanting strict RFC 8693 compliance could add a parallel
endpoint doing the proper token-exchange dance via Keycloak —
orthogonal effort.

### Token renewal model

Once impersonation is active, the client holds:

| Where | Holds | Issuer | Used for | Persisted? |
|---|---|---|---|---|
| Normal OAuth-library slots | Admin's access + refresh JWT (`sub=admin`) | Admin's original IdP | (a) inbound to `/api/auth/impersonate`, (b) eventually outbound when impersonation ends | yes — `TokenStore` / `localStorage`, refreshed by `setupAutomaticSilentRefresh` |
| Impersonation override (single slot) | Impersonation access JWT (`sub=target`, `act={sub:admin}`) | rapla SAS — always | Outbound Bearer while impersonation is active | yes for cross-restart survival, but **no refresh token to store** |
| Server-side | nothing impersonation-specific | — | — | **no per-impersonation record on disk** |

**Key design choice (2026-05-21 design review):** no impersonation
refresh token is issued. Trade-off: an 8-h refresh would have made
mid-action expiry less likely, at the cost of (1) a long-lived
impersonation credential on client disk, (2) a server-side
`sha256(refresh)` pref entry adding attack surface, and (3)
preserving `act` across Spring AS refreshes (an `OAuth2TokenGenerator`
change). All avoided by short-lived tokens renewed via `/api/auth/impersonate`.
Renewal round-trip ~50–100 ms — invisible against any meaningful API call.

#### How renewal works in practice

The HTTP interceptor on every outbound API request:

1. **Pick the effective Bearer.** If `impersonation_override` is set
   and its `exp` hasn't passed, use it; else use the admin's access token.
2. **On 401 with impersonation Bearer attached:** call
   `POST /api/auth/impersonate target_username=<target>` with the
   admin's Bearer. Response replaces the override; original request
   replays once with the fresh override.
3. **If step 2 itself 401s:** admin's Bearer is also stale — fall
   into existing refresh-then-retry (`oauth.refreshToken()`), then
   retry the impersonate call once. If *that* fails, open
   `AuthErrorDialogComponent`, clear `impersonation_override`,
   route to `/login`.

Common case: admin access alive, only impersonation expired → one
extra round-trip per hour. Idle: next API call after long idle pays
step 2 + step 3 once, then succeeds.

#### Switch-back

Clearing `impersonation_override` is the entire operation. Admin's
tokens are unchanged in their OAuth-library slots throughout the
impersonation lifetime. Active Bearer reverts on the very next
outbound request. No "stashed admin token" restore, no `tokenUrl`
reconfigure, no refresh dance.

#### What we get from "no impersonation refresh"

- **No long-lived impersonation credential anywhere.** Client carries
  at most a 1-h-lived access JWT; server carries no per-impersonation
  record. A client-disk compromise during active impersonation exposes
  the admin's normal tokens (same as always) plus an access token good
  for the remainder of its 1-h TTL — a ceiling, not 8-h+. Operators
  can tighten via `spring.security.oauth2.authorizationserver.client.rapla-client.token.access-token-time-to-live`.
- **Authorization is fresh on every renewal.** If admin loses
  group-admin rights mid-impersonation, the next renewal fails on
  server-side `canAdminUser`. Impersonation ends within ~1 h.
- **No `OAuth2TokenGenerator` `act`-preservation work.** Impersonation
  tokens go through `ImpersonationTokenService.mintForImpersonation`,
  never through Spring AS's refresh path.
- **No OAuth library `tokenUrl` reconfigure.** The library always
  refreshes the admin's tokens against the admin's IdP; the override
  is invisible to the library.

#### Audit log on every renewal

Each renewal call hits `/api/auth/impersonate` and fires the same audit line:

```
INFO  rapla.audit - Impersonation: actor=admin (uuid=…) target=alice (uuid=…) at=…
```

A long impersonation session produces a sequence every ~10 min — the
renewal cadence is *the* audit cadence. No separate "refresh" event class.

#### All 401-handling and token-renewal hooks — integration map

The impersonation override lives outside any existing 401/refresh hook
— it sits in client-app state (Angular `AuthService` / Swing
`RemoteConnectionInfo`) and interceptors consult it on every outbound request.

**Angular hooks:**

| # | Hook | Source | Integration |
|---|---|---|---|
| A1 | `authInterceptor` | `rapla-angular/src/app/auth/auth.interceptor.ts` | Bearer-attach from `auth.token()`; on 401, call `/api/auth/impersonate` first (if override active), then admin refresh, then dialog |
| A2 | `AuthService.token()` | `auth.service.ts:362` | Returns impersonation override's access token when set and unexpired, else `oauth.getAccessToken()` |
| A3 | `setupAutomaticSilentRefresh()` | `auth.service.ts:317`, wired at boot in `app.config.ts:67` | **No change.** Library timer keeps admin tokens fresh proactively (necessary because impersonation renewal needs a valid admin Bearer) |
| A4 | `OAuthService.events` subscription | `auth.service.ts:114` | **No change.** Library events are admin-tokens-only |
| A5 | `CallbackComponent.ngOnInit` | `auth/callback.component.ts:25` | **Clear `impersonation_override` on entry.** Landing on `/callback` means a fresh OAuth flow just completed |
| A6 | `AuthService.handleAuthRejection()` | `auth.service.ts:341` | **Clear `impersonation_override`** |
| A7 | `AuthService.handleUnauthenticated()` | `auth.service.ts:325` | **Clear `impersonation_override`** |
| A8 | `AuthService.signOut()` | `auth.service.ts:295` | **Clear `impersonation_override`** |

**Swing hooks:**

| # | Hook | Source | Integration |
|---|---|---|---|
| S1 | `RemoteConnectionInfo` Bearer storage | `rapla-core/.../dbrm/RemoteConnectionInfo.java` | Add `setImpersonationToken(String, Instant exp)` + `getEffectiveAccessToken()` + `hasImpersonationToken()`. Effective returns impersonation when set, else `getAccessToken()` (admin's stored Bearer) |
| S2 | `RefreshOn401Interceptor.intercept` | `rapla-client/.../spring/ClientProxyConfig.java:121` | Bearer-attach uses `getEffectiveAccessToken()`; on 401 with impersonation attached, call `ImpersonationClient.impersonate(...)` first, attach new token, retry. If that 401s, fall into existing refresh-or-fireAuthDead path |
| S3 | `MyCustomConnector.reauth(Class proxy)` | `rapla-core/.../dbrm/MyCustomConnector.java:43` | Insert "if impersonation token present, call `/api/auth/impersonate` first" as step 0; on failure fall into existing refresh-then-password chain |
| S4 | `JavaClientServerConnector.handleResponse` | `rapla-core/.../rest/client/swing/JavaClientServerConnector.java:156` | **No direct change** — calls `customConnector.reauth(...)` which now handles impersonation |
| S5 | `RaplaClientServiceImpl.switchTo(User)` | `rapla-client/.../swing/internal/RaplaClientServiceImpl.java:400` | Rewrite: call `ImpersonationClient.impersonate(target_username)`, store via `info.setImpersonationToken(...)`, fire view refresh. `switchTo(null)` clears |
| S6 | `RaplaClientServiceImpl.canSwitchBack()` | `RaplaClientServiceImpl.java:436` | Returns `info.hasImpersonationToken()` |

**Worst-case renewal chain** (both impersonation AND admin access expired — fires at most once per hour):

```
Outbound API/REST request                                            (1)
└─ 401 (impersonation token expired)
   └─ /api/auth/impersonate                                          (+1)
      └─ 401 (admin access expired too)
         └─ admin refresh:
            • Angular: oauth.refreshToken() → admin's IdP tokenUrl   (+1)
            • Swing:   POST /oauth2/token grant_type=refresh_token   (+1)
         └─ retry /api/auth/impersonate                              (+1)
            └─ retry original request                                (+1)
                                                                    = 5 round trips
```

Common case (admin access valid, only impersonation expired): 2 round
trips. Worst case fires at most every ~1 h. Each hop has bounded timeout.

#### Edge case: client restart mid-impersonation

**Swing default:** discard the override on cold startup. A restart is an
explicit state-change moment; silently resuming surprises the admin.
Audit log preserves the record; admin can re-trigger "Switch to alice".

**SPA behavior (revised 2026-05-22):** persist the override to
**`sessionStorage`** (tab-scoped). F5 reload in same tab keeps the
admin acting as the target (without persistence, every navigation
triggering a full reload silently reverted to admin). New tab still
starts fresh as admin; closing the tab discards naturally. Clear paths:

- `endImpersonation()` (explicit "switch back" + the OAuth callback's
  pre-existing call before processing fresh `?code=…`)
- `signOut()`, `handleUnauthenticated()`, `handleAuthRejection()`
- `impersonate(targetUsername)` with own `preferred_username` —
  short-circuits to `endImpersonation()` (re-selecting yourself is a
  clean switch-back, not a wasted mint + audit entry).

The PRD-051 line-63 follow-up ("if SPA evolves to persist the override
… callback would need to clear it explicitly") was already satisfied —
`CallbackComponent.ngOnInit` calls `endImpersonation()` before
processing the OAuth code. Coverage in `auth.service.spec.ts`, 12 cases total.

### Why we don't store the original admin password anywhere

Legacy `ConnectInfo.password` carried the admin's clear-text password
through "switch user" so Swing could re-login. Security smell, now
unnecessary: the admin's tokens *never leave* their normal
OAuth-library slots throughout impersonation. "Switch back" just
clears `impersonation_override`.

## Plan

1. **Server: `ImpersonationController`** in
   `rapla-server/src/main/java/org/rapla/server/spring/web/`.
   - `@PostMapping("/api/auth/impersonate")`, form-encoded body.
   - Reads `JwtAuthenticationToken` from `SecurityContextHolder` for
     actor. Resolves both actor and target via existing
     `ExternalUserResolver` / operator lookup.
   - `PermissionController.canAdminUser` check.
   - Calls new `ImpersonationTokenService.mintAccessToken(actor, target)`
     signing via existing rapla `JwkSource`.
   - Adds `act` claim with `sub`/`username` of actor.
   - Returns 200 + `{access_token, token_type, expires_in}`. **No
     refresh_token.** Same endpoint handles initial issuance + renewal.
2. **Server: dedicated `SecurityFilterChain` for `/api/auth/impersonate`.**
   `@Order(1)`, `securityMatcher("/api/auth/impersonate")`,
   `oauth2ResourceServer().jwt(...)`. Tier-3 MockMvc: 401 anon, 403
   non-admin, 200 admin.
3. **Server: audit log.** One INFO line per call (issuance + renewal)
   with usernames + UUIDs.
4. **Client: `ImpersonationClient`** (new) in
   `rapla-core/src/main/java/org/rapla/storage/dbrm/`. `@HttpExchange`
   interface paired with the controller ([PRD 049](../049-controller-interface-deduplication.md) pattern).
5. **Client: impersonation override storage.** One field in
   `AuthService` (Angular) / `RaplaClientServiceImpl` (Swing):
   `{ accessToken, target, expAt }`. In-memory by default. No
   `TokenStore` slot 2 / no `localStorage.original_admin_*` keys.
6. **Client: HTTP interceptor renewal sequencer.** Per § "How renewal
   works in practice".
7. **Swing: `RaplaClientServiceImpl.switchTo(User)` rewrite.** Replace
   password reconnect with `/api/auth/impersonate` call + override
   storage. `canSwitchBack()` reads `impersonation_override != null`.
   `switchTo(null)` clears.
8. **Swing: status-bar indicator while impersonating.** Read `act`
   claim from effective access token; render "Signed in as $sub (as
   $act.sub) [Switch back]".
9. **Documentation.** `docs/authentication.md`: new section "Admin
   impersonation ('switch to user')" with wire format, audit log
   shape, "no impersonation refresh anywhere" + "no admin password
   stored" properties. Cross-ref [PRD 036](../036-external-idp-oauth-login.md) § "Out of scope" → "Admin
   impersonation".

## Tests

| Tier | Test | What it covers |
|---|---|---|
| 3 | `ImpersonationControllerTest` (MockMvc) | 200 on admin Bearer + valid target; response shape `{access_token, token_type, expires_in}` with **no** `refresh_token`; `act` claim present; `sub` is target's UUID |
| 3 | `ImpersonationControllerAuthTest` (MockMvc) | 401 anon; 403 non-admin actor; 404 unknown target; 403 admin-of-different-scope |
| 3 | `ImpersonationRenewalTest` (MockMvc) | Second call with same target returns fresh token; `canAdminUser` re-evaluated on each call (admin losing group-admin between calls → 403) |
| 3 | `ImpersonationControllerTest#auditLogEmittedForEachIssuanceIncludingRenewal` ✅ + `#authorizationFailureDoesNotProduceSuccessAuditLine` ✅ (landed 2026-05-22) | Every successful issuance emits INFO audit line with usernames/UUIDs; 403 path does NOT emit success line |
| 3 | `ImpersonationWithExternalIdpTest` (MockMvc + stubbed Keycloak JWKS) | Admin via Keycloak Bearer can impersonate — actor resolution uses `ExternalUserResolver` |
| 3 | `ImpersonationControllerTest#noSessionPreferenceWrittenForTargetAsSideEffect` ✅ (landed 2026-05-22) | No `org.rapla.auth.session` preference written for target as side effect |
| 2 | `ImpersonationTokenServiceTest` (no Spring) | `act` claim correctly assembled; sub is target's UUID; signature validates; no refresh-token code path exists |
| 2 | `RaplaClientServiceImplSwitchToTest` (no AWT) | `switchTo(target)` calls impersonate with right username; override set; `switchTo(null)` clears; admin slots untouched throughout |
| 2 | `AuthInterceptorImpersonationTest` (no Angular runtime) | Bearer attached is impersonation when override set; on 401, calls `/api/auth/impersonate` with override's `target_username` + admin's Bearer; replays; on second 401 falls into admin-refresh; on third 401 opens dialog + clears override |
| 5 | `users.service.spec.ts` ✅ (landed 2026-05-22) | `GET /api/users` uses `auth.adminToken()` and raw `fetch` (not `HttpClient`) so the interceptor cannot attach the override. Mid-impersonation chip-click verified live in browser the same day |
| 5 | `auth.service.spec.ts` ✅ (landed 2026-05-22) | `signOut()`, `handleUnauthenticated()`, `handleAuthRejection()`, `endImpersonation()` each clear override; `adminToken()` returns admin's Bearer even while impersonation active. Regression guard for A6/A7/A8 + mid-impersonation switch invariant |
| 5 | `CallbackComponentClearsImpersonationOverrideTest` (Vitest + TestBed) | Landing on `/callback` with fresh OAuth code clears any pre-existing override. Regression guard for A5 |
| 2 | `RefreshOn401InterceptorImpersonationTest` (no Spring, Java) | Bearer-attach uses `info.getEffectiveAccessToken()`; on 401, calls `ImpersonationClient.impersonate(...)` before falling into refresh; retries with new token. Regression guard for S2 |
| 2 | `MyCustomConnectorReauthImpersonationTest` (no Spring, Java) | `reauth()` step-0 calls `/api/auth/impersonate` when token present; on failure falls into existing refresh-then-password chain. Regression guard for S3 |
| 6 | `SwitchUserMenuItem.spec.ts` (when Angular admin UI lands) | Picker renders only for admins; click calls impersonate endpoint; status bar shows indicator |
| e2e (manual) | Swing flow | Admin right-click → "Switch to" → see as user → menu bar indicator → wait ~1 h, observe silent renewal in server log → "Switch back" → restored with admin's Bearer |
| e2e (manual) | Cross-IdP flow | Admin authenticated via Keycloak impersonates; token is rapla-SAS-issued; audit log shows actor via UPN; renewal works without re-prompting |
| e2e (manual) | Permission-revocation mid-session | Group-admin starts impersonation; remove their `can_admin_parent` group in another session; next renewal returns 403 within ~1 h, dialog fires |

## Security & compliance considerations

**Acknowledged downside: rapla SAS continues to issue user-identity
tokens even in IdP-only deployments.** A deployment migrated entirely
to e.g. DHBW Keycloak might prefer the org-approved IdP to be the
*sole* token issuer, both as defence in depth and to fit a "the IdP
is the auth boundary" governance posture. PRD 051 keeps rapla's SAS
alive to mint impersonation tokens — by design (see § Architecture).
The trade-off:

- Rapla SAS is technically secure (RSA in `RaplaKeyStorage`, JWKS
  published, standard Spring AS — same code path used for rapla-local
  logins + refresh).
- It's *organizationally* a second identity boundary the org's
  identity team may not have audited as deeply. A CISO could
  legitimately ask "you said Keycloak issues tokens — why does rapla also?"
- Blast radius of a compromised SAS signing key is bounded: only mints
  tokens whose `sub` is a rapla user UUID, only rapla's own
  resource server trusts that JWKS. Inside rapla, can mint any user identity.
- Further bounded by `canAdminUser` gate: attacker needs (a) signing
  key AND (b) active admin Bearer, or (c) signing key alone bypassing
  the endpoint (at which point the endpoint isn't the weakest link).

**Mitigations (configurable per deployment):**

1. **Opt-in flag.** `rapla.auth.impersonation.enabled` (default
   `true`) — implemented. Set `false` → both impersonation entry
   points refuse (`ImpersonationController.impersonate` and the SPA
   cookie switch `AuthCookieController.impersonateSwitch`), returning
   404 (not 403 — invisible, not just denied), and Swing hides the
   menu action. Documented in `docs/authentication.md`.
2. **JWT `act` claim (RFC 8693).** Impersonation tokens carry the
   standard `act` claim (`typ` stays `"access"` — no non-standard
   `typ: "impersonation"` is emitted), so ops pipelines alert on
   presence of `act` to catch impersonation issuance.
3. **Mandatory audit-log line.** Already in design — every issuance
   logs actor+target UUIDs/usernames.
4. **Per-deployment audit sink** (follow-up PRD) — route audit line
   to separate Logger appender. Out of scope for v1.

**Not mitigated by v1:**

- **No external approval workflow.** Some regimes require two-person
  review per impersonation. v1 trusts `canAdminUser` + audit log.
  Future PRD if needed.
- **No IdP-side audit trail.** Keycloak doesn't see the event because
  rapla mints. Back-channel notification would add IdP-cooperation
  requirement we deliberately avoid.

**Net assessment:** the rapla-mints-its-own-tokens design has a real
compliance cost in IdP-only deployments. Cost is bounded (admin-gated,
audited, opt-out-able); alternatives that avoid it are inapplicable
for the IdP-replaceable architecture. v1 ships with mitigations 1–3.

## Alternatives Considered

Reviewed against RFC 8693 and the Curity "Impersonation Approaches"
catalogue.

### Per-request impersonation header — REJECTED

Admin's Bearer unchanged; every request carries `X-Rapla-Impersonate:
alice`; server filter checks header + `canAdminUser`, overrides
principal. Simplicity is illusory: every controller reading
`SecurityContextHolder` would have to consult the override filter,
and missing one is "admin's privileges silently applied to alice's
data". Also wrong attribution for monitoring/quotas (everything
recorded as admin), no standards alignment, doesn't survive the wire
boundary if a future feature calls a separate microservice. Token-swap
moves the override into the token itself where the JWT filter already
validates it on every request.

### RFC 8693 token exchange — INAPPLICABLE (three independent reasons)

Strict form: `subject_token=<alice's token>` + `actor_token=<admin's
token>`. Keycloak's `requested_subject` extension swaps subject_token
for the admin's and adds `requested_subject=alice`.

1. **Strict form is unbuildable** — we don't have alice's token; the
   whole point is admin doesn't know her password.
2. **Realm-admin coordination problem** — Keycloak's `requested_subject`
   needs (a) admin's role with `impersonate-users` realm-management
   permission and (b) `token-exchange` permission on the rapla client.
   In typical university/municipal deployments the realm admin is
   central IT (e.g. DHBW IT for `dhbwmos-lehre`), separate from rapla
   admins. They'll (rightly) refuse: it would let rapla admins
   impersonate users in *any* application federating through that Keycloak.
3. **Rapla's per-group admin model isn't expressible at the IdP.**
   `canAdminUser` reads `can_admin_parent` annotation +
   `getGroupsToAdmin` scope; Keycloak's `impersonate-users` is
   realm-global, all-or-nothing. No concept of "this admin can
   impersonate Mosbach students only". Even if reason #2 were solved,
   every rapla group-admin would effectively get realm-wide impersonation.

Reason #3 is most fundamental: the authorization scoping rapla wants
isn't expressible at the IdP. **Can-admin-X-impersonate-Y can only be
made by rapla code reading rapla data.**

### RFC 7523 user assertion — INAPPLICABLE FOR SAME REASON

Admin's app constructs signed JWT asserting "I am authorized to act
for alice", presents at token endpoint. Requires trusted JWT-signing
key on the client. Swing/Angular don't sign JWTs — embedding a signing
key in the client is a security hole; having the server sign means
we're back to token-swap with extra steps.

### OAuth flow with `act_as:alice` scope — REJECTED (UX + scope)

Admin clicks → fresh authorize flow with `scope=act_as:alice`. Forces
a full interactive OAuth redirect per switch (visible flicker, bad
ergonomics for "quickly check what alice sees"); only works if rapla's
SAS handles the scope (external IdPs don't), so we'd implement
server-side anyway — essentially `/api/auth/impersonate` minus the redirect.

### Authentication-action / account-picker at login — REJECTED (one-shot)

IdP shows "act as which user?" at login; token's `sub` is selected
user. One-shot per login; doesn't support right-click "Switch to" UX.
Entirely IdP-side — rapla can't drive it.

### Embedded tokens (token-in-a-token) — OVERKILL

Solves "downstream service should only see limited authority", which
rapla doesn't have (single-process app, no downstream service). Pattern
designed for microservice meshes; expensive plumbing for zero benefit.

### Other patterns evaluated and discarded

| Pattern | Why discarded |
|---|---|
| **Cookie-based session impersonation** (Django admin, Rails "become") | Requires server-side session state; [PRD 041](../041-openapi-runtime-removal.md) explicitly moved rapla off sessions onto stateless Bearer. |
| **Microsoft On-Behalf-Of (OBO)** | Solves service-to-service token propagation, not admin-impersonating-user. Wrong shape. |
| **DPoP-bound impersonation tokens (RFC 9449)** | Cryptographic device binding; strict security win but heavy plumbing (DPoP nonce per request, key registration). Disproportionate for rapla's threat model. Future PRD if needed. |
| **[PRD 043](../043-api-keys-jwt-pat.md) API key with `act_as` flag** | API keys are long-lived headless access; reusing for interactive impersonation muddies threat model. Keep orthogonal. |
| **OIDC CIBA (RFC 9126)** | Out-of-band user consent — target user approves via push. Inapplicable: admin impersonation by definition bypasses target. |
| **Cookie + Bearer pair** | Same failure modes as per-request header; cookies survive across tabs unintentionally. |
| **Reverse-proxy JWT rewriter** | Equivalent to Option 1 at network edge; doesn't fit single-process deployment, splits auth logic between rapla and proxy. |

### Sources

- [RFC 8693 — OAuth 2.0 Token Exchange](https://datatracker.ietf.org/doc/html/rfc8693)
- [Curity — Impersonation Approaches with OAuth and OpenID Connect](https://curity.io/resources/learn/impersonation-flow-approaches/)
- [ZITADEL — OAuth 2.0 Token Exchange (RFC 8693): Impersonation & Delegation](https://zitadel.com/docs/guides/integrate/token-exchange)

## Open Questions

1. **Should impersonation tokens have shorter TTL than regular access tokens?**
   Today 1-h (`access-token-time-to-live: 1h`, [PRD 041](../041-openapi-runtime-removal.md) — Spring AS stock
   is 5 min). Could shorten to 10–15 min to limit blast radius. Lean:
   same TTL for now; manual "Switch back" + standard expiry covers it.
2. **Multiple-level impersonation** (admin → user → some-other-user).
   `act` supports nesting. Lean: **disallow** — if current Bearer
   already has `act`, refuse a second impersonation. Swing UI doesn't
   expose it anyway.
3. **Should `/api/auth/impersonate` return an `id_token`?** Probably
   not — `id_token` is "identity as seen by the IdP", doesn't map
   cleanly to "who I'm impersonating". Skip for v1.
4. **Audit log destination separate from main rapla log?** v1: piggy-back
   on existing `Logger`. Revisit if a deployment needs a separate sink.
5. **Impersonation override persistence across client restart.** 2026-05-21
   review: no impersonation refresh anywhere. Open: persist the
   *current* still-valid override to TokenStore for crash recovery?
   Lean: **discard on cold restart** (Swing); restart is a clean
   state-change moment. SPA already persists to `sessionStorage` per
   § "Edge case".
6. **Swing menu bar for tokens whose `act` was minted by a *different*
   admin?** Token IS that admin's impersonation; audit reflects that;
   probably fine to show "Signed in as X (as A)". Confirm in code review.
7. **Renewal cadence vs. real-clock drift.** Interceptor renews on 401,
   not timer. Idle admin pays one extra round-trip on next call.
   Acceptable for v1. Proactive timer adds background `canAdminUser`
   re-evaluation every ~h regardless of activity (noisier audit log).
   Lean: 401-driven.

## Future enhancements

### Option 3b — hybrid mode for IdP-only token issuance

**Status:** deferred. Documented so next reviewer has the design captured.

**The compliance need.** Deployments under "IdP is *the* identity
boundary; only the IdP issues user-identity tokens" governance
(typical CISO requirement) are uncomfortable with v1's
rapla-mints-impersonation-tokens design. This is the path to closing
that gap for the subset of deployments that need to.

**Mechanism.** Same `/api/auth/impersonate` endpoint; auth-check /
token-mint split across rapla and IdP:

1. Client → rapla: `POST /api/auth/impersonate target_username=alice`
   with admin's Bearer (any issuer).
2. Rapla resolves admin + alice, runs `canAdminUser` (same per-group
   scoping as v1 — it has to be, IdP can't express it).
3. Rapla → Keycloak: `POST <provider.tokenUrl>` with
   `grant_type=urn:ietf:params:oauth:grant-type:token-exchange`,
   `subject_token=<admin's Bearer>`, `requested_subject=alice`,
   `client_id=rapla-app`, `client_secret=<server-held>`.
4. Keycloak issues fresh access + refresh with `sub=alice` and
   `act={sub: admin}` — fully Keycloak-signed.
5. Rapla forwards to client; client swaps tokens (same as Option 1).

**Required Keycloak realm config (one-time, by realm admin — NOT by rapla admins):**

1. Grant the `rapla-app` *client* (not any human admin role) the
   `token-exchange` permission against realm's user-account scope.
2. Grant `rapla-app` service-account role the composite
   `realm-management › impersonate-users`.

**Crucial: the permission is at CLIENT level, not admin USER roles.**
Central IT is asked to allow "the rapla app may impersonate users for
its admin functions" — rapla enforces per-admin scoping internally via
`canAdminUser`. Far easier ask than "grant every rapla group-admin
realm-wide impersonation rights".

**Properties:**

| | Option 1 (v1) | Option 3b (deferred) |
|---|---|---|
| Authorization | rapla `canAdminUser` | rapla `canAdminUser` (same) |
| Per-group admin scoping honored | ✅ | ✅ |
| Token signing | rapla SAS key | Keycloak realm key |
| Sole-IdP-issuer compliance | ❌ | ✅ |
| Audit trail | rapla logs + `act` claim | rapla logs + Keycloak logs + `act` claim |
| Works for rapla-SAS-only logins | ✅ | ❌ — falls back to Option 1 |
| Works for Microsoft Entra | ✅ | ❌ — no equivalent grant |
| Works for Google | ✅ | ❌ — no equivalent grant |
| Works for Keycloak | ✅ | ✅ — if realm is configured |
| Works when IdP is unreachable | ✅ | ❌ |

**Activation model (proposed):** `rapla.auth.impersonation.mode`:

- `rapla-sas` (default in v1) — Option 1 unconditionally.
- `idp-exchange` — Option 3b when active provider supports it; fall
  back to Option 1 with log warning otherwise.
- `disabled` — endpoint returns 404.

**Why deferred:** niche audience (Keycloak-active + CISO mandate for
no-rapla-tokens); realm-config dependency (one-time grant required
per deployment); audit-trail splits across rapla + Keycloak event
logs; `mode=idp-exchange` fallback paths multiply the test matrix.

**When to revive:** a DHBW-style deployment requests it on compliance
grounds AND the realm admin commits to keeping the client-level
permission. Follow-up PRD would carry the implementation: confirm
Keycloak's token-exchange config against current release, build the
Option-1 fallback, tier-3 MockMvc against stubbed exchange endpoint,
e2e against a configured realm.
