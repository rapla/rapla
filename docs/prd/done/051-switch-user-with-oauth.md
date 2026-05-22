# PRD 051: Restore "switch to user" admin feature with OAuth-only auth

**Status:** done — SPA shipped 2026-05-22. Swing parity superseded by PRD 052 (see § Closed scope below).
**Date:** 2026-05-21 (closed 2026-05-22)

## Closed scope (2026-05-22)

### Shipped

- **Server** — `ImpersonationController` + `UsersController` (PRD 049
  `@HttpExchange` single-source-of-truth pattern), `JwtConfig.JwtIssuer`
  extended with `issueImpersonationToken` and a `username`/`displayName`-bearing
  `issueAccessToken` overload, `AuthorizationServerConfig`'s
  `jwtTokenCustomizer` injects `preferred_username` + `name` on access /
  id tokens (rapla-SAS path) so the SPA's user chip works for every
  grant type. Audit logging, `canAdminUser` gate, no-server-side-state
  guarantee — all live.
- **Angular SPA** — chip + dialog flow, mid-impersonation switching
  using admin Bearer, impersonation-override interceptor renewal
  sequencer, "Switch back" / "Sign out" mutually-exclusive toolbar
  button, `swap_horiz` / `person_search` chip-icon affordance.
- **Tests landed** — see § Tests (rows marked ✅): tier-3 MockMvc
  coverage for `/api/auth/impersonate` (10 cases) and `/api/users`
  (5 cases), tier-5 Vitest coverage for `UsersService` admin-Bearer
  routing and `AuthService` logout paths clearing the override.
- **Docs landed** — `docs/authentication.md` § "Group administration
  policy", § "Admin impersonation", § "Switching from one target to
  another mid-impersonation" (with the authoritative "impersonation
  tokens cannot themselves invoke the impersonation endpoints" rule),
  endpoint-reference table additions, visual-indicator section.

### Superseded by PRD 052

- **Plan §7** — *Swing `RaplaClientServiceImpl.switchTo(User)` rewrite
  (in-place token swap).* PRD 052 (Client clean restart, 2026-05-22)
  resolves OQ6: switch-to-user and switch-back ride the same
  close+recreate session channel as logout. The in-place token-swap
  approach drafted here is **not** the Swing implementation path. PRD
  052's `BlockingQueue<NextSession>` + `NextSession.reconnectAs(...)`
  is the live design.
- **Plan §8** — *Swing status-bar indicator.* The visible UI element
  (the "Acting as &lt;target&gt;" status label + "Switch back" link)
  stays in scope; only the underlying session-swap mechanism changes
  per PRD 052. Implementation lives in `ApplicationViewSwing` +
  `RaplaClientServiceImpl` and ships under PRD 052.
- **Tests S2 / S3** — `RefreshOn401InterceptorImpersonationTest`,
  `MyCustomConnectorReauthImpersonationTest`. These were designed
  against the in-place token-swap renewal path. Replaced by PRD 052's
  test plan (close-recreate semantics: a 401 in the impersonated
  session triggers a fresh session start with `NextSession.reconnectAs(target)`,
  not an in-place renewal).

### Deferred / accepted gaps

Items that would have been in v1 if shipped, but are explicitly accepted
as out of scope. None of these blocks shipping — each is a hardening
or coverage gap rather than a missing capability.

| Gap | Why deferred | If/when to revisit |
|---|---|---|
| `ImpersonationWithExternalIdpTest` (Keycloak-authenticated admin can impersonate) | Stubbed-JWKS fixture work non-trivial; the multi-issuer decoder path is covered by existing `ExternalAuthLifecycleIntegrationTest` and the impersonation path's correctness is independent of the actor's issuer. | When DHBW (or another Keycloak deployment) runs the feature for the first time, capture an integration test against their stub. |
| Group-admin → out-of-scope target (403) | `testdefault.xml` has only `homer` (global admin) + `monty` (group admin of `/powerplant`); no third user outside `monty`'s scope. The 403 branch is exercised via `groupAdminCannotImpersonateGlobalAdmin` (target.isAdmin path); the `belongsTo`-returns-false branch is asserted via unit-level `PermissionController` coverage elsewhere. | Add a fixture user outside `/powerplant` once another PRD also needs one. |
| `CallbackComponentClearsImpersonationOverrideTest` (tier 6) | Lower-risk path — the override is in-memory only and a fresh OAuth code naturally lands in a new `AuthService` lifecycle in production. Risk: if the SPA evolves to persist the override (e.g. to `sessionStorage`), the callback would need to clear it explicitly. The other logout paths cover the common surface. | When the override moves out of `signal()` memory, add the test alongside that change. |
| Live cross-IdP browser verification (DHBW Keycloak admin → local user) | Needs a live DHBW Keycloak session. The all-rapla-SAS flow was verified end-to-end 2026-05-22 (chip click mid-impersonation, switch back, `/api/users` admin-Bearer path). | Next session with DHBW Keycloak available. |
| Native Swing flow | User explicitly scoped to SPA-only ("we only do spa", 2026-05-22). | Handled by PRD 052. |

None of these gaps affects the SPA shipping path. The PRD is closed
on the basis that the SPA feature is live + verified + regression-tested
and the Swing path has a successor PRD (052) with a different
underlying mechanism.

## Goal

Restore the admin-only **"Switch to user"** feature in the rapla
clients (Swing today; Angular admin UI when it lands) so an admin can
view rapla as another user without that user's password, work in their
context, and switch back with a single click. The feature shipped
pre-PRD 041 via the rapla-custom `/api/auth/login`'s `connectAs` field;
PRD 041 deleted that endpoint when consolidating onto OAuth2's
`/oauth2/token`, and the OAuth2 password grant has no equivalent. With
PRD 036 + Keycloak support, rapla no longer mints the access token for
external-IdP logins at all, so even reintroducing a `connectAs` field
in the password grant wouldn't cover the dominant SSO case.

## Why this is needed

1. **It's a daily admin operation.** Triage of "this user says reservation
   X disappeared from their view" / "why can't student Y allocate room Z"
   is currently a guessing game without it. Pre-OAuth, every admin used
   it routinely; the recent silent removal is a regression.
2. **OAuth2 password grant has no `connectAs`** by RFC 6749 design.
   The grant exchanges credentials for a token; impersonation is a
   *separate* OAuth concern handled by the **Token Exchange** grant
   ([RFC 8693](https://datatracker.ietf.org/doc/html/rfc8693)).
   Re-introducing `connectAs` on the password grant would be a
   rapla-only extension that breaks the "any standards-compliant OIDC
   client works" property PRD 041 set up.
3. **External IdPs (Keycloak, Entra, Google) issue the token.** Rapla
   can't unilaterally produce a token claiming `sub: <target-user>`
   signed by Keycloak. Whatever the design, the impersonation token
   has to be signed by rapla's *own* embedded SAS — and the auth pipe
   has to accept it alongside externally-issued tokens.
4. **`canAdminUser` is the authorization rule already.** Server-side
   `RaplaAuthentificationService.checkConnectAsRights(...)` already
   enforces "actor must be admin of target" — we keep that check
   unchanged; the only thing that changes is how the actor's intent to
   impersonate reaches the server.

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
  **No refresh_token is issued.** This is deliberate — see
  § "Token renewal model" below. Impersonation tokens are short-lived
  (1-hour access only, matching rapla-SAS's
  `access-token-time-to-live: 1h` per PRD 041 in
  `rapla-app/src/main/resources/application.yml:95`); renewal happens
  by calling `/api/auth/impersonate` again with the admin's current
  Bearer.
  Eliminates server-side state for the impersonation session, removes
  the long-lived impersonation credential from client disk, and forces
  every renewal through a fresh `canAdminUser` authorization check.
- **`act` claim per RFC 8693.** Carries the original actor:
  ```json
  "act": { "sub": "<admin UUID>", "username": "admin" }
  ```
  Auditable: every action taken under an impersonation token is
  traceable to the admin who initiated it. Server-side audit log
  emits a line on every impersonation issuance.
- **Server-side validation chain:**
  1. Decode the incoming Bearer via the existing multi-issuer
     `JwtDecoder` (any issuer the deployment accepts).
  2. Resolve the actor to a rapla `User` via
     `SpringSecurityRemoteSession.resolveJwtOrThrow` (already in place;
     uses username-as-identity per PRD 036).
  3. Resolve `target_username` to a rapla `User` via the same
     case-insensitive lookup. 404 if not found.
  4. `PermissionController.canAdminUser(actor, target)` — same rule as
     the legacy `connectAs` path. 403 on failure.
  5. Mint a rapla-SAS-signed JWT with `sub=target.id`,
     `username=target.username`, `act.sub=actor.id`,
     `act.username=actor.username`, standard `iat`/`exp`/`iss`/`aud`.
     TTL: same as rapla-SAS access tokens (1 hour per
     `access-token-time-to-live` in `application.yml`; PRD 041
     bumped from Spring AS's stock 5-min default). Renewal via a
     fresh `/api/auth/impersonate` call extends the impersonation
     session indefinitely while admin's authority remains valid.
- **Refresh of impersonation tokens** goes through the standard
  `/oauth2/token grant_type=refresh_token` path because the refresh
  token is rapla-SAS-issued. Spring AS's refresh-token authentication
  provider gets a small adapter that preserves the `act` claim across
  refresh. Without this, the refresh would silently drop the `act`
  claim — a security hole (audit trail breaks).
- **Swing wiring (`RaplaClientServiceImpl.switchTo(User)`):**
  - Currently calls `stop(new ConnectInfo(admin, password, target))` —
    stops the session, re-runs `start(reconnectInfo)` which calls the
    legacy login.
  - New flow: while still authenticated as admin, call
    `POST /api/auth/impersonate { target_username }`, receive new
    tokens, **store the OLD admin tokens** in `TokenStore` under a
    second slot (`"original_admin_tokens"`), swap the active tokens to
    the new pair, kick the session restart so the new Bearer is used.
  - "Switch back" reads the stored admin tokens, swaps them back,
    drops the impersonation pair. No password is involved on either
    direction.
  - Removes Swing's dependency on having the admin's password in
    memory (`ConnectInfo.password`) — which itself was a holdover from
    pre-OAuth and a security smell.
- **`canSwitchBack()`** stays as today's API but the underlying flag
  flips based on whether `original_admin_tokens` is populated in
  `TokenStore`. Surfaces in `UserAction.setSwitchToUser()` via the
  existing `service.canSwitchBack()` check — no UI change.
- **Visual indicator in the Swing menu bar** while impersonating:
  status-bar text "Signed in as alice (as admin)" with a `[Switch back]`
  link. The presence of the `act` claim on the live access token is
  the source of truth; the client reads it locally (it's a public
  claim, no signature check needed since the token itself was just
  signature-checked by Spring Security upstream).
- **Server-side endpoint security:** dedicated SecurityFilterChain
  matcher `/api/auth/impersonate` requires an authenticated Bearer
  (so the existing `/api/auth/oauth/**` `permitAll` carve-out does
  NOT apply here — see § Architecture). The `canAdminUser` check is
  the authorization layer.
- **Audit log line on every impersonation issuance:**
  ```
  INFO  rapla.audit - Impersonation: actor=admin (uuid=...) target=alice (uuid=...) at=<iso8601>
  ```
  Goes to the standard rapla log via the `Logger` facade so it lands
  wherever ops already collects rapla logs (file, syslog, journald).

### Out of scope

- **Self-service "switch to me" for a non-admin user.** Only users
  satisfying `canAdminUser(actor, target)` can impersonate. Same rule
  as the legacy `connectAs` path.
- **Hybrid mode (rapla authorizes, IdP issues the token).** Documented
  as a deferred future enhancement — see § "Future enhancements:
  Option 3b — hybrid mode for IdP-only token issuance" below.
- **Server-pushed "this admin is impersonating you" notification.**
  Out of scope; the audit log is the record.
- **Time-bounded "impersonation session"** (e.g. auto-expire after 30
  min regardless of token TTL). The standard 1-hour access TTL +
  manual "Switch back" button cover this for v1. A configurable
  hard-cap is a future PRD if needed.
- **Angular SPA "switch to user" UI.** The SPA's user-admin surface
  isn't built yet. Server-side endpoint is shipped now (Swing uses
  it); the SPA picks it up when the user-admin screen lands.

## Architecture

### Rapla's group-administration policy — the authorization rule

The "who can impersonate whom" decision is `canAdminUser(adminUser,
target)`, implemented in
[`rapla-core/.../storage/PermissionController.java`](../../rapla-core/src/main/java/org/rapla/storage/PermissionController.java)
at line 697. The full rule is:

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
| **Global admin** | `User.isAdmin() == true` flag on the User entity | All users (excluding nothing) |
| **Group admin** | `Category.getAnnotation(CategoryAnnotations.CAN_ADMIN_PARENT) == "true"` on a category that's in the user's `groupList` | Users whose group list intersects `group.getParent()` transitively |

#### How the `can_admin_parent` annotation works

The annotation is stored on a `Category` entry under the key
[`CategoryAnnotations.CAN_ADMIN_PARENT`](../../rapla-core/src/main/java/org/rapla/entities/CategoryAnnotations.java)
(string value `"can_admin_parent"`). When set to `"true"` on a
category C, **any user whose group list contains C is treated as
admin of C's *parent* category**, and transitively of every user
whose group list belongs (transitively) to that parent.

Worked example. Suppose the category tree is:

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
- User `alice` ∈ `/groups/department-1/admins`:
  `getAdminGroups(alice)` = `[/groups/department-1/admins]`
  `getGroupsToAdmin(alice)` = `[/groups/department-1]` (the parent)
  `alice` is admin of every user whose group list intersects
  `/groups/department-1` or any descendant — so all students and
  fellow admins of department-1, but **not** anyone in department-2.
- User `bob` ∈ `/groups/department-1/students`:
  `getAdminGroups(bob)` = `[]` (no `can_admin_parent` annotation on
  the student category) — `bob` is not a group-admin.
- `User.belongsTo(group)` (`UserImpl.java:215–222`) is transitive: it
  walks parent chains via `getGroupsIncludingParents`, so a user in
  `/groups/department-1/students` `belongsTo(/groups/department-1)`
  returns true.

#### Why this rules out IdP-side authorization

The full scoping rule reads four pieces of rapla state:

1. `Category` entries and their tree structure
2. The `can_admin_parent` annotation on those categories
3. The `User.groupList` of the admin
4. The `User.groupList` of the target
5. The `User.isAdmin()` flag on both

**None of this is exposed to the IdP.** A rapla `Category` is not a
Keycloak group; a Keycloak group has no `can_admin_parent`
annotation. Keycloak's `impersonate-users` permission is one binary
flag at the realm level. Even if a deployment configured Keycloak's
RFC 8693 token-exchange with `impersonate-users` granted to a rapla
admin's role, the resulting authority would be **realm-wide
impersonation of any user in the realm** — including users in
departments the rapla admin has no authority over. The scoping
information that would correctly limit them isn't in Keycloak, isn't
in the JWT, isn't anywhere outside rapla.

This is why the `canAdminUser` check has to run in rapla code,
against rapla data, and the impersonation token has to be minted on
the same side as the check. The two are inseparable.

### Load-bearing principle: rapla can mint tokens for any rapla user

Rapla's embedded Spring Authorization Server controls its own
RSA-signing key (`RaplaKeyStorage`, persisted in system preferences —
see PRD 029 Phase 1). With that key, rapla can sign a JWT for **any**
rapla user identity, including one the requesting client isn't logged
in as — provided server-side authorization rules allow it. There's no
external counterparty whose consent or policy we need.

External IdPs (Keycloak, Entra, Google) **cannot** do this for rapla.
Each only issues tokens whose `sub` is the human who completed *their*
authentication ceremony. An admin authenticated to Keycloak cannot ask
Keycloak for an "act as alice" token unless Keycloak's per-realm
token-exchange policy has been explicitly enabled and configured —
which rapla deployments can't assume.

The token-swap design exploits this asymmetry: **regardless of which
IdP the admin originally authenticated with, the impersonation token
is always minted by rapla's own SAS.** The admin's incoming Bearer
(rapla-SAS, Keycloak, Entra, or Google) is decoded, the actor is
resolved to a rapla `User` via the existing `ExternalUserResolver`
pipeline, the `canAdminUser(actor, target)` check runs in rapla code
against rapla data, and the new token is signed by rapla. Keycloak
never sees the impersonation request — it isn't even told about it.

This is why every alternative that requires the IdP to issue the
impersonation token (RFC 8693 strict token-exchange, RFC 7523
user-assertion flow, `act_as:` scopes at authorize-time) is rejected:
they only work when the IdP's policy cooperates, and a rapla
deployment that picks "Sign in with DHBW Mosbach" doesn't control the
DHBW Keycloak realm policy. Token swap is the **only** option that
works for the IdP-replaceable architecture PRD 031 + PRD 036
established.

### Why a rapla-namespaced endpoint, not a SAS extension

Spring Authorization Server's `/oauth2/token` is RFC-bound. Adding a
non-standard parameter (`connectAs`) or a custom grant type that
behaves differently from RFC 8693 would:
- Break standards conformance — every OIDC discovery tool that probes
  rapla would see a spec violation.
- Tie the impersonation contract to the SAS endpoint, which would
  conflict with external-IdP setups where `/oauth2/token` calls hit
  the IdP, not rapla.

A rapla-namespaced `/api/auth/impersonate` endpoint is cleanly under
rapla's control regardless of which IdP issued the actor's Bearer.
It accepts any Bearer rapla can decode (multi-issuer) and emits a
rapla-SAS Bearer for downstream use.

### SecurityFilterChain wiring

Two filter chains relevant here (both already exist or build on
existing ones — see `SecurityConfig` + the 2026-05-21 work):

| Chain | Matcher | Auth required | Why |
|---|---|---|---|
| `@Order(0)` (existing) | `/api/auth/oauth/**` | none | Pre-auth endpoints (BFF code exchange, discovery). The dedicated chain skips the resource-server JWT filter so stale Bearers don't cause 401 before the controller runs. |
| `@Order(0.5)` (new) | `/api/auth/impersonate` | **yes** (rapla-SAS or external) | Impersonation requires a valid actor Bearer. Standard `oauth2ResourceServer().jwt(...)` filter. Adding `/api/auth/impersonate` to the pre-auth chain would make it callable anonymously — a privilege escalation. |
| `@Order(2)` (existing main chain) | `anyRequest()` | as today | Untouched. |

Implemented as a third `SecurityFilterChain` bean with
`securityMatcher("/api/auth/impersonate")` and `oauth2ResourceServer`
explicitly installed. Verified by a tier-3 MockMvc test that POST
without a Bearer returns 401, POST with a non-admin Bearer returns
403, POST with admin Bearer + missing target returns 404.

### Wire format vs. RFC 8693

The proper standards-compliant shape would be:
```
POST /oauth2/token
grant_type=urn:ietf:params:oauth:grant-type:token-exchange
subject_token=<target's token>
actor_token=<admin's token>
requested_token_type=urn:ietf:params:oauth:token-type:access_token
```
But rapla doesn't *have* the target's token — the whole point is the
admin doesn't know their password. So the rapla-specific endpoint
takes `target_username` (a name, not a token), validates the
admin-can-admin-target relationship, and mints a token. This is
**not** RFC 8693 because we don't have the inputs for it; the response
shape and `act` claim semantics are borrowed.

The PRD deliberately uses a rapla-namespaced URL to make this
asymmetry explicit. A future deployment that wants strict RFC 8693
compliance could add a parallel endpoint that does the proper
token-exchange dance via Keycloak — orthogonal effort.

### Token renewal model

Once impersonation is active, the client holds:

| Where | Holds | Issuer | Used for | Persisted? |
|---|---|---|---|---|
| Normal OAuth-library slots | Admin's access + refresh JWT (`sub=admin`) | The admin's original IdP (rapla SAS, Keycloak, Entra, Google) | (a) inbound to `/api/auth/impersonate` to mint impersonation tokens, (b) eventually outbound when impersonation ends | yes — `TokenStore` / `localStorage`, refreshed transparently by `setupAutomaticSilentRefresh` |
| Impersonation override (single slot) | Impersonation access JWT (`sub=target`, `act={sub:admin}`) | rapla SAS — always | Outbound `Authorization: Bearer …` while impersonation is active | yes for cross-restart survival, but **no refresh token to store** |
| Server-side | nothing impersonation-specific | — | — | **no per-impersonation record on disk** |

**Key design choice (2026-05-21 design review):** no impersonation
refresh token is issued. The trade-off was: an 8-hour refresh window
would have made expiry less likely to hit a user mid-action, at the
cost of (1) a long-lived impersonation credential stored on client
disk, (2) a server-side `sha256(refresh)` entry under some pref slot
adding attack surface to rapla's identity store, and (3) needing to
preserve the `act` claim across Spring AS refreshes (an `OAuth2TokenGenerator`
change with its own test surface). All of those are avoided by making
the impersonation token short-lived and renewing it via a fresh call
to `/api/auth/impersonate` whenever it expires. The renewal round-trip
is ~50–100 ms — invisible to the user against any meaningful API call.

#### How renewal works in practice

The HTTP interceptor sequences three things on every outbound rapla
API request:

1. **Pick the effective Bearer.** If `impersonation_override` is set
   and its `exp` claim hasn't passed yet, use it; else use the
   admin's access token from the standard OAuth-library slot.
2. **On 401 with the impersonation Bearer attached:** call
   `POST /api/auth/impersonate` with `target_username=<the
   target the override was for>` and the admin's current Bearer in
   the Authorization header. The response replaces the override; the
   original request is replayed once with the fresh override.
3. **If step 2 itself returns 401:** the admin's own Bearer is also
   stale — fall into the existing refresh-then-retry path
   (`oauth.refreshToken()` against the admin's IdP), then retry the
   `/api/auth/impersonate` call once. If *that* fails, the admin
   session is genuinely over; open `AuthErrorDialogComponent`, clear
   `impersonation_override`, route to `/login`.

In the common case the admin's access token is alive and only step 2
fires — one extra round-trip per renewal cycle (~every hour if the
admin is actively impersonating, matching the 1-hour access TTL).
When the admin is idle, no renewal happens — the next API call after
a long idle pays both step 2 (impersonate) and step 3 (admin refresh)
once, then succeeds.

#### Switch-back

Clearing the `impersonation_override` is the entire operation. The
admin's tokens are already where they belong (the OAuth library's
standard slots, unchanged throughout the impersonation lifetime).
Active Bearer reverts to the admin's access token on the very next
outbound request. No "stashed admin token" restore, no `tokenUrl`
reconfigure, no refresh dance.

#### What we get from "no impersonation refresh"

- **No long-lived impersonation credential anywhere.** Client carries
  at most a 1-hour-lived access JWT. Server carries no
  per-impersonation record at all. A client-disk compromise during
  active impersonation exposes the admin's normal tokens (same as
  always) plus an access token good for the remainder of its 1-hour
  TTL — a ceiling, not the 8-hour or longer window an impersonation
  refresh would have carried. (Operators concerned about the 1-hour
  ceiling can override `spring.security.oauth2.authorizationserver.client.rapla-client.token.access-token-time-to-live`
  in their `application.yml` to tighten it deployment-wide; that
  applies to all rapla-SAS-issued tokens uniformly, not just
  impersonation.)
- **Authorization is fresh on every renewal.** If the admin loses
  group-admin rights mid-impersonation (e.g. an audit removes them
  from `/groups/department-1/admins` for some reason), the next
  renewal fails on the server-side `canAdminUser` check. The
  impersonation ends within ~1 hour, not 8 h.
- **No `OAuth2TokenGenerator` `act`-preservation work.** Impersonation
  tokens are *issued* via `ImpersonationTokenService.mintForImpersonation`
  (Plan step 1); they never go through Spring AS's refresh path, so
  the generator doesn't need to know about `act`.
- **No OAuth library `tokenUrl` reconfigure.** The library always
  refreshes the admin's tokens against the admin's IdP. The
  impersonation override is invisible to the library — the
  interceptor uses it directly, the library has no opinion.

#### Audit log on every renewal

Each renewal call goes through the same `/api/auth/impersonate`
endpoint as the initial issuance, so the same audit log line fires:

```
INFO  rapla.audit - Impersonation: actor=admin (uuid=…) target=alice (uuid=…) at=…
```

A long impersonation session produces a sequence of these every ~10
min — the renewal cadence is *the* audit cadence. No separate
"refresh" event class is needed.

#### All 401-handling and token-renewal hooks — integration map

The codebase has several existing 401 / token-renewal touchpoints
beyond the Angular HTTP interceptor we built earlier this session.
Each needs explicit handling for impersonation, because the
impersonation override lives outside any of them — it sits in
client-app state (Angular `AuthService` / Swing `RemoteConnectionInfo`)
and the interceptors need to consult it on every outbound request.

**Angular hooks:**

| # | Hook | Source | Integration |
|---|---|---|---|
| A1 | `authInterceptor` | `rapla-angular/src/app/auth/auth.interceptor.ts` | Bearer-attach from `auth.token()`; on 401 with Bearer attached, call `/api/auth/impersonate` first (if override is active), then admin refresh, then dialog |
| A2 | `AuthService.token()` | `rapla-angular/src/app/auth/auth.service.ts:362` | Returns impersonation override's access token when set and unexpired, else `oauth.getAccessToken()`. Single source of truth |
| A3 | `setupAutomaticSilentRefresh()` | `auth.service.ts:317`, wired at boot in `app.config.ts:67` | **No change.** Library timer keeps admin tokens fresh proactively. Necessary because the impersonation renewal call needs a valid admin Bearer in the Authorization header |
| A4 | `OAuthService.events` subscription | `auth.service.ts:114` | **No change.** Library events are about admin tokens only — the override is invisible to the library |
| A5 | `CallbackComponent.ngOnInit` | `auth/callback.component.ts:25` | **Clear `impersonation_override` on entry.** Landing on `/callback` means a fresh OAuth flow just completed — any prior impersonation belongs to the previous session and must be discarded |
| A6 | `AuthService.handleAuthRejection()` | `auth.service.ts:341` | **Clear `impersonation_override`.** Otherwise a stale override could survive into the next login session |
| A7 | `AuthService.handleUnauthenticated()` | `auth.service.ts:325` | **Clear `impersonation_override`.** Same reasoning — silent logout shouldn't leave an override behind |
| A8 | `AuthService.signOut()` | `auth.service.ts:295` | **Clear `impersonation_override`.** User-initiated logout |

**Swing hooks:**

| # | Hook | Source | Integration |
|---|---|---|---|
| S1 | `RemoteConnectionInfo` Bearer storage | `rapla-core/.../dbrm/RemoteConnectionInfo.java` | Add `setImpersonationToken(String, Instant exp)` + `getEffectiveAccessToken()` + `hasImpersonationToken()`. `getEffectiveAccessToken()` returns the impersonation token when set, else `getAccessToken()`. `getAccessToken()` keeps its old meaning (admin's stored Bearer) so the renewal path can read the admin's token explicitly |
| S2 | `RefreshOn401Interceptor.intercept` | `rapla-client/.../spring/ClientProxyConfig.java:121` | (a) Bearer-attach line 129 uses `info.getEffectiveAccessToken()`. (b) On 401 (line 131), if `info.hasImpersonationToken()`, call `ImpersonationClient.impersonate(...)` first via the seam, attach the new token, retry. If that 401s too, fall into the existing refresh-or-fireAuthDead path |
| S3 | `MyCustomConnector.reauth(Class proxy)` | `rapla-core/.../dbrm/MyCustomConnector.java:43` | Insert "if impersonation token present, call `/api/auth/impersonate` first" as step 0 of the existing chain. On success → return new impersonation token. On failure → fall into the existing refresh-then-password chain (admin's authority is the second line) |
| S4 | `JavaClientServerConnector.handleResponse` | `rapla-core/.../rest/client/swing/JavaClientServerConnector.java:156` | **No direct change.** Calls `customConnector.reauth(...)` which now handles impersonation. This connector just consumes the Bearer reauth returns |
| S5 | `RaplaClientServiceImpl.switchTo(User)` | `rapla-client/.../swing/internal/RaplaClientServiceImpl.java:400` | Rewrite: call `ImpersonationClient.impersonate(target_username)`, store result via `info.setImpersonationToken(...)`, fire view refresh. `switchTo(null)` clears the impersonation token. No `stop()`, no reconnect, no password |
| S6 | `RaplaClientServiceImpl.canSwitchBack()` | `RaplaClientServiceImpl.java:436` | Returns `info.hasImpersonationToken()` |

**Worst-case renewal chain (both impersonation AND admin access
simultaneously expired — fires at most once per hour):**

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

Common case (admin access still valid, only impersonation expired):
2 round trips. Pathological case fires at most every ~1 hour. Each
hop has its own short timeout (~10 s connect, ~15 s read), so
worst-case latency is bounded regardless of network conditions.

#### Edge case: client restart mid-impersonation

If the Swing client crashes or is force-killed while impersonating:

- The OAuth library's admin token slots are on disk (normal startup
  resume restores the admin session).
- The impersonation override may or may not have been persisted,
  depending on whether it was kept in memory or in TokenStore.

**Default behavior:** discard the impersonation override on cold
startup. Rationale: a restart is an explicit state-change moment;
silently resuming a pre-restart impersonation surprises the admin.
The audit log records the impersonation event so ops still has the
record. Admin can right-click → "Switch to alice" again if they
intended to continue.

This matches PRD 051 Open Question §5's "lean: persist across
restart" — *only that lean is now reversed for the impersonation
override* (which we control entirely) while the admin's normal
tokens remain persistent as before (those are the library's
business, and they survive restart like any other login session).

### Why we don't store the original admin password anywhere

The legacy `ConnectInfo.password` field carried the admin's clear-text
password through the entire "switch user" round-trip so the Swing
client could re-login as the admin when switching back. That was a
security smell before and is unnecessary now: the admin's tokens
*never leave* their normal OAuth-library slots throughout the
impersonation lifetime. "Switch back" is just clearing the
`impersonation_override` — the admin's access + refresh tokens are
already where they belong, ready for the next outbound request to
use directly.

## Plan

1. **Server: `ImpersonationController`** in
   `rapla-server/src/main/java/org/rapla/server/spring/web/`.
   - `@PostMapping("/api/auth/impersonate")`, form-encoded body.
   - Reads `JwtAuthenticationToken` from `SecurityContextHolder` to
     get the actor. Resolves both actor and target via the existing
     `ExternalUserResolver` path (when external) or operator lookup
     (when local).
   - `PermissionController.canAdminUser` check.
   - Calls a new `ImpersonationTokenService.mintAccessToken(actor, target)`
     that signs via the existing rapla `JwkSource`. Same key material,
     same algorithm — the new token validates against the same JWKS
     the resource server already trusts.
   - Adds `act` claim with `sub`/`username` of actor.
   - Returns 200 + `{access_token, token_type, expires_in}`. **No
     refresh_token field.** Same endpoint handles initial issuance
     and renewal — they're indistinguishable on the wire.
2. **Server: dedicated `SecurityFilterChain` for `/api/auth/impersonate`.**
   - `@Order(1)` (between the existing `/api/auth/oauth/**` `@Order(0)`
     and the main `@Order(2)`). `securityMatcher("/api/auth/impersonate")`,
     `oauth2ResourceServer().jwt(...)`.
   - Tier-3 MockMvc tests: 401 anon, 403 non-admin, 200 admin.
3. **Server: audit log.**
   - One INFO line per `/api/auth/impersonate` call (both initial
     issuance and renewal). Includes both usernames and UUIDs. No
     PII beyond what's already in the logs.
4. **Client: `ImpersonationClient`** (new) in
   `rapla-core/src/main/java/org/rapla/storage/dbrm/`.
   - `@HttpExchange` interface paired with the controller (per PRD 049
     single-source-of-truth routing).
   - Client-side proxy used by Swing's `RaplaClientServiceImpl.switchTo`
     and the SPA's `AuthService.impersonate` (when the admin UI lands).
5. **Client: impersonation override storage.**
   - One field in `AuthService` (Angular) / `RaplaClientServiceImpl`
     (Swing): `{ accessToken, target, expAt }`. In-memory by default
     (cleared on cold restart per § "Edge case: client restart
     mid-impersonation").
   - No `TokenStore` slot 2 / no `localStorage.original_admin_*` keys —
     the admin's tokens stay in their normal OAuth-library slots
     throughout the impersonation lifetime.
6. **Client: HTTP interceptor renewal sequencer.**
   - When `impersonation_override` is set, attach its access token
     as `Authorization: Bearer …`.
   - On 401 with the impersonation Bearer attached: call
     `/api/auth/impersonate` with the override's `target_username`
     and the admin's current Bearer (read from OAuth library) in
     the Authorization header. Replace override with the response,
     replay the original request.
   - On 401 from the impersonate call itself: fall into the existing
     refresh-then-retry path for the admin's Bearer, then retry the
     impersonate call once. If still 401, open
     `AuthErrorDialogComponent`, clear `impersonation_override`,
     route to `/login`.
7. **Swing: `RaplaClientServiceImpl.switchTo(User)` rewrite.**
   - Replace the password-based reconnect (`stop(new ConnectInfo(admin, password, target))`)
     with: call `/api/auth/impersonate`, store the result in
     `impersonation_override`, fire a re-fetch of whatever views the
     active session was rendering.
   - `canSwitchBack()` reads `impersonation_override != null`.
   - `switchTo(null)` clears the override; next outbound request
     uses the admin's normal Bearer.
8. **Swing: status-bar indicator while impersonating.**
   - Read the `act` claim from the current effective access token;
     if present, render "Signed in as $sub (as $act.sub)
     [Switch back]" in the status bar.
9. **Documentation.**
   - `docs/authentication.md`: new section "Admin impersonation
     ('switch to user')" with the wire format, audit log shape, the
     "no impersonation refresh token anywhere" property, and the
     "no admin password is stored" property.
   - Update PRD 036 cross-ref: link to this PRD from PRD 036
     § "Out of scope" → "Admin impersonation by user action" bullet.

## Tests

| Tier | Test | What it covers |
|---|---|---|
| 3 | `ImpersonationControllerTest` (MockMvc) | 200 on admin Bearer + valid target; response shape `{access_token, token_type, expires_in}` with **no** `refresh_token` field; `act` claim present on the access token; `sub` is target's UUID |
| 3 | `ImpersonationControllerAuthTest` (MockMvc) | 401 anon; 403 non-admin actor; 404 unknown target; 403 admin-of-different-scope (canAdminUser=false because target is outside the admin's group scope) |
| 3 | `ImpersonationRenewalTest` (MockMvc) | Calling `/api/auth/impersonate` a second time with the same target returns a fresh access token; `canAdminUser` is re-evaluated on each call (regression test: simulate admin losing group-admin status between calls, expect 403) |
| 3 | `ImpersonationControllerTest#auditLogEmittedForEachIssuanceIncludingRenewal` ✅ + `#authorizationFailureDoesNotProduceSuccessAuditLine` ✅ (landed 2026-05-22) | Every successful issuance — initial and renewals — emits the INFO audit line with actor+target UUIDs and usernames; the 403 path does NOT emit a success line |
| 3 | `ImpersonationWithExternalIdpTest` (MockMvc + stubbed Keycloak JWKS) | Admin authenticated via Keycloak Bearer can still impersonate — actor resolution uses the existing `ExternalUserResolver` |
| 3 | `ImpersonationControllerTest#noSessionPreferenceWrittenForTargetAsSideEffect` ✅ (landed 2026-05-22) | Negative: no `org.rapla.auth.session`-style preference is written for the target as a side effect of impersonation; the target's own refresh token (if any) is unaffected |
| 2 | `ImpersonationTokenServiceTest` (no Spring) | `act` claim assembled correctly; subject is target's UUID; signature validates against the test JWKS; no refresh-token issuance code path exists |
| 2 | `RaplaClientServiceImplSwitchToTest` (no AWT) | `switchTo(target)` calls the impersonate endpoint with the right username; on success the `impersonation_override` is set with the returned token; `switchTo(null)` clears it; admin's normal token slots untouched throughout |
| 2 | `AuthInterceptorImpersonationTest` (no Angular runtime) | Bearer attached is impersonation token when override is set; on 401, calls `/api/auth/impersonate` with override's `target_username` + admin's Bearer; replays original request; on second 401 falls into admin-refresh path; on third 401 opens dialog and clears override |
| 5 | `users.service.spec.ts` ✅ (landed 2026-05-22) | `GET /api/users` uses `auth.adminToken()` (not `token()`) and goes through raw `fetch`, not `HttpClient` — so the auth interceptor cannot accidentally attach the impersonation override. Mid-impersonation chip-click verified live in browser the same day |
| 5 | `auth.service.spec.ts` ✅ (landed 2026-05-22) | `signOut()`, `handleUnauthenticated()`, `handleAuthRejection()`, and `endImpersonation()` each clear the impersonation override; `adminToken()` returns the admin's Bearer even while an impersonation is active. Regression guard for hooks A6/A7/A8 plus the mid-impersonation switch invariant |
| 5 | `CallbackComponentClearsImpersonationOverrideTest` (Vitest + TestBed) | Landing on `/callback` with a fresh OAuth code clears any pre-existing override before completing the new session. Regression guard for hook A5 |
| 2 | `RefreshOn401InterceptorImpersonationTest` (no Spring context, Java) | Bearer-attach uses `info.getEffectiveAccessToken()`; on 401 with impersonation token attached, calls `ImpersonationClient.impersonate(...)` before falling into refresh; retries original request with the new impersonation token. Regression guard for hook S2 |
| 2 | `MyCustomConnectorReauthImpersonationTest` (no Spring context, Java) | `reauth()` step-0 calls `/api/auth/impersonate` when an impersonation token is present; on success returns the new impersonation Bearer; on failure falls into the existing refresh-then-password chain. Regression guard for hook S3 |
| 6 | `SwitchUserMenuItem.spec.ts` (eventually, when Angular admin UI lands) | Picker renders only for admins; clicking it calls the impersonate endpoint; status bar shows the indicator |
| e2e (manual) | Swing flow | Admin right-click on user → "Switch to" → see rapla as that user → menu bar shows indicator → wait ~1 h, observe a silent renewal round-trip in the server log → "Switch back" → restored cleanly with admin's normal Bearer in use |
| e2e (manual) | Cross-IdP flow | Admin authenticated via Keycloak impersonates a user; impersonation token is rapla-SAS-issued, audit log shows actor via UPN; renewal cycle works without re-prompting the admin |
| e2e (manual) | Permission-revocation mid-session | Start impersonation as a group-admin, then (in another admin session) remove the group-admin from their `can_admin_parent` group, observe the next renewal returns 403 within ~1 h and the impersonation dialog fires |

## Security & compliance considerations

**Acknowledged downside: rapla SAS continues to issue user-identity
tokens even in IdP-only deployments.** A deployment that has migrated
all users to (say) DHBW Keycloak might prefer the org-approved IdP to
be the *sole* token issuer for the deployment, both as defence in
depth and to fit a "the IdP is the auth boundary" governance posture
that the org's identity team has audited and signed off on. PRD 051
keeps rapla's SAS alive to mint impersonation tokens — by design,
because that's the load-bearing property that makes the feature work
uniformly across IdPs (see § Architecture). The trade-off:

- The rapla SAS is technically secure (RSA signing key persisted in
  `RaplaKeyStorage`, JWKS published, standard Spring Authorization
  Server implementation, same code path that's already used for
  rapla-local password logins and refresh).
- It's *organizationally* a second identity boundary that the org's
  identity team may not have audited or approved at the same depth as
  the central IdP. A CISO reviewing the deployment could legitimately
  flag "you said Keycloak issues tokens for this app — why does rapla
  also issue tokens?"
- The blast radius of a compromised rapla SAS signing key is bounded:
  it can only mint tokens whose `sub` is a rapla user UUID, and only
  rapla's own resource server trusts that JWKS. An attacker with the
  key can't authenticate against anything *outside* rapla. But
  inside rapla, they can mint a token claiming any user's identity.
- The blast radius is further bounded by the `canAdminUser` gate at
  the impersonation endpoint: only admin Bearers can request a
  mint. So an attacker would need (a) the SAS signing key AND (b) an
  active admin Bearer, or (c) the signing key alone if they bypass
  the endpoint and mint a token directly — at which point the
  endpoint isn't the weakest link anyway.

**Mitigations (configurable per deployment):**

1. **Opt-in flag.** `rapla.auth.impersonation.enabled` (default
   `true` to preserve legacy behaviour). Deployments under strict
   IdP-only governance set it to `false`; the `/api/auth/impersonate`
   endpoint then returns 404 (not 403 — make the feature invisible,
   not just denied) and the Swing menu hides the "Switch to user"
   action. Documented in `docs/authentication.md` with the trade-off
   explained.
2. **JWT header marker.** Impersonation tokens carry an explicit
   `typ: "impersonation"` claim in addition to `act`. Lets ops
   pipelines that scrape rapla's audit log or token introspection
   specifically count / alert on impersonation issuance. (Already
   discoverable via `act` claim presence, but a header field makes
   it grep-friendly.)
3. **Mandatory audit-log line.** Already in the design (see Plan
   step 4) — every successful issuance logs actor+target UUIDs and
   usernames. Lands wherever ops collects rapla logs.
4. **Per-deployment audit sink.** A follow-up PRD could route the
   audit line to a separate Logger appender (file, syslog, journald,
   external log shipper) for compliance archival — out of scope for
   v1.

**Not mitigated by v1 (explicit limitations):**

- **No external approval workflow.** Some governance regimes require
  every impersonation to be reviewed/approved by a second admin
  before issuance. v1 trusts the `canAdminUser` rule + the audit log
  as the after-the-fact record. A two-person-rule mode is a future
  PRD if a deployment specifically needs it.
- **No IdP-side audit trail.** The IdP (Keycloak) doesn't see the
  impersonation event because rapla mints the token. Mitigation
  would be a back-channel notification from rapla to the IdP — adds
  the IdP-cooperation requirement we deliberately avoid, and not
  worth it unless a specific deployment requires IdP-side visibility.

**Net assessment:** the load-bearing rapla-mints-its-own-tokens
design has a real compliance cost in IdP-only deployments. The cost
is bounded (admin-gated, audited, opt-out-able), the alternative
designs that would avoid it (RFC 8693 / 7523 / scope-driven) are
inapplicable for the IdP-replaceable architecture rapla wants. v1
ships with mitigations 1–3; mitigation 4 is a follow-up; the
two-person-rule and IdP-side-audit limitations are explicit
non-goals documented for the next PRD reviewer to push back on if
the use case demands them.

## Alternatives Considered

Reviewed against established impersonation patterns from RFC 8693 and
the Curity "Impersonation Approaches with OAuth and OpenID Connect"
catalogue. Captured here so the next-time-someone-asks moment doesn't
re-litigate the decision.

### Per-request impersonation header — REJECTED

**Mechanism:** Admin's Bearer stays unchanged. Every API request
carries `X-Rapla-Impersonate: alice`. A server-side filter checks the
header, verifies `canAdminUser(real-subject, alice)`, and overrides
the effective principal for that request only.

**Why considered:** Smallest server-side change. No new endpoint, no
new token-mint code, no `act` claim handling on refresh.

**Why rejected:**
- **Single chokepoint fragility.** Every controller that reads
  `SecurityContextHolder` directly (bypassing the override filter)
  would silently run as the admin. Adding a new endpoint without
  consulting the impersonation filter becomes a security regression
  with no tier-3 test to catch it.
- **Monitoring / quota / rate-limit attribution.** Any system that
  keys on the token's `sub` (logs, metrics, audit aggregators,
  external observability) would record all impersonated traffic as
  the admin's. Wrong attribution by construction.
- **No standards alignment.** Not RFC 8693, not RFC 7523. OIDC-aware
  tools (Spring AS introspection, audit chains, downstream
  resource-server filters) ignore the header entirely.
- **Doesn't survive the wire boundary.** A future feature that needs
  to call a *separate* rapla microservice over OAuth would lose the
  impersonation context — the header doesn't propagate.

The simplicity benefit is illusory: the override filter has to be
applied *exactly* at every code path that consults the principal,
and the failure mode of missing one is "admin's privileges silently
applied to alice's data". The token-swap design moves the principal
override into the token itself, where the resource-server JWT filter
already validates it on every request without us reinventing the
plumbing.

### RFC 8693 token exchange — INAPPLICABLE (three independent reasons)

**Mechanism — strict form:** `POST /oauth2/token grant_type=urn:ietf:params:oauth:grant-type:token-exchange`
with `subject_token=<alice's token>` and `actor_token=<admin's token>`.

**Mechanism — Keycloak's `requested_subject` extension:** same grant
type but with `subject_token=<admin's token>` and
`requested_subject=alice`. The IdP runs its own `impersonate`
permission check and returns a token whose `sub` is alice (signed by
the IdP, with `act` claim).

**Why rejected — three reasons, any one of which is sufficient:**

1. **Strict form is unbuildable.** We don't have alice's token. The
   entire premise of admin impersonation is that the admin doesn't
   know alice's credentials. Without `subject_token`, the strict
   RFC 8693 call literally cannot be constructed.
2. **Realm-admin coordination problem.** Keycloak's
   `requested_subject` extension requires (a) the admin's role to
   carry the `impersonate-users` realm-management permission and
   (b) the rapla client to be granted `token-exchange` permission
   pointing at the user-account scope. **Both are per-realm
   Keycloak admin tasks that rapla admins are not normally
   authorized to perform.** In typical university / municipal
   deployments the realm admin is a central-IT team (e.g. DHBW IT
   for the `dhbwmos-lehre` realm), separate from the rapla admin
   group. Asking the realm admin to grant rapla admins a
   realm-wide impersonation permission is a request the realm
   admin will (rightly) refuse: it would let rapla admins
   impersonate users in *any* application federating through that
   Keycloak, not just rapla.
3. **Rapla's per-group admin model isn't expressible at the IdP.**
   The authorization rule for impersonation isn't "any admin can
   impersonate any user" — it's
   `PermissionController.canAdminUser(adminUser, userToAdmin)` in
   `rapla-core/.../storage/PermissionController.java:697`, which
   reads the `can_admin_parent=true` annotation
   (`CategoryAnnotations.CAN_ADMIN_PARENT`) on rapla `Category`
   entries to compute a per-admin scope of which users they may
   impersonate (those whose group list intersects with the admin's
   `getGroupsToAdmin` set). Keycloak's `impersonate-users`
   permission is realm-global, all-or-nothing — it has no concept
   of "this admin can impersonate users in the Mosbach department
   only" or "this admin can impersonate students in their seminar
   group but not the dean". The group structure, the annotation,
   and the belongsTo logic all live in rapla data and rapla code.
   Even if reason #2 were solved (DHBW IT enabled token-exchange),
   the impersonate permission would be granted at the wrong
   granularity — every rapla group-admin would effectively get
   realm-wide impersonation rights.

Reason #3 is the most fundamental: even in an idealized deployment
where rapla admin == realm admin and `token-exchange` is enabled,
the authorization scoping rapla actually wants is not expressible at
the IdP. **The decision of "can admin X impersonate user Y" can only
be made by rapla code reading rapla data.** That makes rapla SAS the
right place to mint the token — the same code that runs the check
signs the result.

### RFC 7523 user assertion — INAPPLICABLE FOR SAME REASON

**Mechanism:** Admin's app constructs a signed JWT asserting "I am
authorized to act for alice", presents it at the token endpoint, gets
back an alice-token.

**Why rejected:** Requires a trusted JWT-signing key held by the
admin's client. The rapla Swing client and Angular SPA don't sign
JWTs — they consume them. Building this would mean either embedding
a signing key in the client (security hole) or… having the server
sign the assertion, at which point we're back to the token-swap
design with extra steps.

### OAuth flow with `act_as:alice` scope — REJECTED (UX + scope)

**Mechanism:** Admin clicks "switch to alice" → SPA initiates a fresh
OAuth authorization code flow with scope `act_as:alice` → server's
authorize endpoint recognises the scope, runs the admin check, issues
a token for alice.

**Why rejected:**
- **Forces a full interactive OAuth redirect per switch.** Right-click
  → redirect to /authorize → callback → swap → restart Swing — a
  visible flicker and ~1 s round-trip when the legacy UX is
  instantaneous. Bad ergonomics for "quickly check what alice sees".
- **Only works if rapla's SAS handles the scope.** External IdPs
  (Keycloak, Entra, Google) don't know what `act_as:alice` means and
  wouldn't honour it. So we'd implement it server-side anyway — which
  is essentially what `/api/auth/impersonate` does, minus the
  redirect.
- The "fresh authorize" property doesn't buy us anything: rapla's
  `canAdminUser` check is the actual authorization gate, and it runs
  the same regardless of whether the request arrived via /authorize
  or via /api/auth/impersonate.

### Authentication-action / account-picker at login — REJECTED (one-shot)

**Mechanism:** During the admin's login ceremony, the IdP shows an
"act as which user?" picker; the issued token's `sub` is the selected
user.

**Why rejected:** One-shot per login. Doesn't support the
"right-click any user → switch" UX where the admin's perspective
needs to change multiple times in a single session. Also entirely
IdP-side — rapla doesn't drive it, can't add it to Keycloak/Entra.

### Embedded tokens (token-in-a-token) — OVERKILL

**Mechanism:** Issued token wraps a narrower inner token meant for a
specific downstream service.

**Why rejected:** Solves the "downstream service should only see
limited authority" problem, which rapla doesn't have. Rapla is a
single-process app — there's no downstream service. Pattern is
designed for microservice meshes; adding the mechanism here is
expensive plumbing for zero benefit.

### Other patterns evaluated and discarded

Listed here so the PRD reviewer can confirm we considered the
full landscape, not just the OAuth-standard ones above.

| Pattern | Why discarded |
|---|---|
| **Cookie-based session impersonation** (Django admin, Rails "become") | Requires server-side session state. PRD 041 explicitly moved rapla off sessions onto stateless Bearer; reintroducing session storage just for impersonation undoes that work. |
| **Microsoft On-Behalf-Of (OBO) flow** | Solves a different problem (service-to-service token propagation, e.g. middle-tier API → downstream service), not admin-impersonating-user. Wrong shape. |
| **DPoP-bound impersonation tokens (RFC 9449)** | Would cryptographically bind the impersonation token to the admin's device key — defence-in-depth against token theft. Strict security win but heavy plumbing (DPoP nonce per request, key registration on the client). Disproportionate for rapla's threat model. Future PRD if a deployment specifically needs it. |
| **PRD 043 API key with `act_as` flag** | API keys are for long-lived headless access (CI scripts, exporters). Reusing them for interactive impersonation muddies the threat model — an API key shouldn't be able to do something an interactive admin can do only with the explicit `act` claim. Keep them orthogonal. |
| **OIDC CIBA (Client-Initiated Backchannel Authentication, RFC 9126)** | Designed for *out-of-band user consent* — the target user gets a push notification and approves. Inapplicable here: admin impersonation by definition bypasses the target user. |
| **Cookie + Bearer pair** | Same failure modes as the per-request header (above). Cookies just make it worse — they survive across tabs unintentionally. |
| **Reverse-proxy JWT rewriter** | Functionally equivalent to Option 1 implemented at network edge. Doesn't fit rapla's single-process deployment shape; would split the auth logic between rapla code and proxy config, making it harder to test. |

### Sources

- [RFC 8693 — OAuth 2.0 Token Exchange](https://datatracker.ietf.org/doc/html/rfc8693)
- [Curity — Impersonation Approaches with OAuth and OpenID Connect](https://curity.io/resources/learn/impersonation-flow-approaches/)
- [ZITADEL — OAuth 2.0 Token Exchange (RFC 8693): Impersonation & Delegation](https://zitadel.com/docs/guides/integrate/token-exchange)

## Open Questions

1. **Should impersonation tokens have a shorter TTL than regular access
   tokens?** Today rapla-SAS issues 1-hour access tokens
   (`access-token-time-to-live: 1h` in `application.yml:95`, set by
   PRD 041 — Spring AS's stock default is 5 min). An impersonation
   token could be shorter (e.g. 10–15 min) to limit blast radius if
   the admin walks away mid-impersonation. Lean: same TTL for now
   — the manual "Switch back" + standard expiry covers it. Revisit
   if ops asks.
2. **Multiple-level impersonation** (admin → user → some-other-user).
   Should we allow it? The `act` claim format supports it (nested
   `act` chains). Lean: **disallow** — if the current Bearer already
   has an `act` claim, refuse a second impersonation. The Swing UI
   doesn't expose it anyway (right-click "Switch to" is only available
   from a user-admin view, which the impersonated session typically
   doesn't have access to). Revisit if an admin specifically asks.
3. **Should `/api/auth/impersonate` return a `id_token`?** Probably
   not — `id_token` is OIDC-spec for "the identity of the
   authentication subject as seen by the IdP", which doesn't map
   cleanly to "this is who I'm impersonating". Skip for v1; revisit
   if a client surface needs it.
4. **Audit log destination separate from the main rapla log?** A
   dedicated audit channel makes compliance review easier. v1: piggy
   back on the existing `Logger` so it lands wherever ops collects
   rapla logs; revisit if a deployment specifically needs a separate
   sink.
5. **Impersonation override persistence across client restart.** The
   2026-05-21 design review settled on "no impersonation refresh
   token anywhere"; the override is a single short-lived access
   token. The follow-on question is whether the *current* override
   (target_username + the still-valid access token) should be
   persisted to TokenStore so a Swing client crash mid-impersonation
   resumes into the impersonation on restart. Lean: **discard on
   cold restart** (current § "Edge case: client restart
   mid-impersonation"). Restart is a clean state-change moment;
   resuming a pre-restart impersonation surprises the admin. Worth
   re-considering only if an operator complains.
6. **What does the Swing menu bar do for tokens whose `act` claim was
   minted by a *different* admin?** Scenario: admin A impersonates
   user X, leaves the Swing client open, admin A's session is later
   shared / inherited. The token still has `act=A` so the indicator
   would still show "Signed in as X (as A)". Probably fine — the
   token IS A's impersonation, and the audit trail reflects that —
   but worth confirming in code review.
7. **Renewal cadence vs. real-clock drift.** The interceptor renews
   on 401, not on a timer. If the impersonation access expires while
   the admin is idle, the very next outbound request pays one extra
   round-trip. Acceptable for v1. A proactive renewal timer (similar
   to `setupAutomaticSilentRefresh`) is a possible polish item if
   the renewal latency ever bothers anyone — but adds a background
   process that re-evaluates `canAdminUser` server-side every ~hour
   regardless of whether the admin is actively using rapla, which is
   noisier in the audit log. Lean: leave as 401-driven for now.

## Future enhancements

### Option 3b — hybrid mode for IdP-only token issuance

**Status:** deferred. Not in v1; documented here so the next reviewer
has the design captured.

**The compliance need.** Deployments under "the IdP is *the* identity
boundary; only the IdP issues user-identity tokens" governance
posture (typical CISO requirement when central IT has audited and
approved a single IdP for the org) are uncomfortable with v1's
rapla-mints-impersonation-tokens design. The trade-off is
documented in § Security & compliance considerations; this enhancement
is the path to closing it for the subset of deployments that need to.

**Mechanism.** Same `/api/auth/impersonate` endpoint, but the
authorization-check / token-mint responsibilities split across rapla
and the IdP at a clean seam:

1. Client → rapla: `POST /api/auth/impersonate target_username=alice`
   with admin's Bearer (any issuer).
2. Rapla resolves admin from the JWT, resolves alice from the rapla
   user store, runs `canAdminUser(admin, alice)` — same per-group
   admin scoping as v1, evaluated against rapla data (it has to be —
   the IdP can't express it; see § "Rapla's group-administration
   policy — the authorization rule").
3. Rapla → IdP (Keycloak): `POST <provider.tokenUrl>` with
   `grant_type=urn:ietf:params:oauth:grant-type:token-exchange`,
   `subject_token=<admin's Bearer>`,
   `requested_subject=alice`, `client_id=rapla-app`,
   `client_secret=<server-held>`.
4. Keycloak issues a fresh access + refresh token with `sub=alice`
   and `act={sub: admin}` — fully Keycloak-signed.
5. Rapla forwards to the client; client swaps tokens (same flow as
   Option 1).

**Required Keycloak realm config (one-time, by the realm admin —
NOT by rapla admins):**

1. Grant the `rapla-app` *client* (not any human admin role) the
   `token-exchange` permission against the realm's user-account
   scope.
2. Grant the `rapla-app` service-account role the composite
   `realm-management › impersonate-users`.

**Crucial property: the permission is at the CLIENT level, not at
admin USER roles.** DHBW IT (or any central-IT) is asked to allow
"the rapla app may impersonate users for its admin functions" —
rapla then enforces the per-admin scoping internally via
`canAdminUser`. This is a far easier ask than "grant every rapla
group-admin realm-wide impersonation rights".

**Properties:**

| | Option 1 (v1) | Option 3b (deferred) |
|---|---|---|
| Authorization | rapla `canAdminUser` | rapla `canAdminUser` (same) |
| Per-group admin scoping honored | ✅ | ✅ |
| Token signing | rapla SAS key | Keycloak realm key |
| Sole-IdP-issuer compliance | ❌ | ✅ |
| Audit trail | rapla logs + `act` claim | rapla logs + Keycloak logs + `act` claim |
| Works for rapla-SAS-only logins | ✅ | ❌ — falls back to Option 1 |
| Works for Microsoft Entra | ✅ | ❌ — Entra has no equivalent grant |
| Works for Google | ✅ | ❌ — Google has no equivalent grant |
| Works for Keycloak | ✅ | ✅ — if realm is configured |
| Impersonation works when IdP is unreachable | ✅ | ❌ |

**Activation model (proposed):** new config
`rapla.auth.impersonation.mode` with three values:

- `rapla-sas` (default in v1) — Option 1 unconditionally.
- `idp-exchange` — Option 3b when the active provider supports it;
  fall back to Option 1 (with a server log warning) when it doesn't
  (Entra, Google, rapla-SAS-only).
- `disabled` — feature unavailable; endpoint returns 404.

**Why deferred:**

1. **Niche audience.** Only deployments with (a) Keycloak as the
   active IdP and (b) a CISO mandate for "no rapla-issued tokens"
   benefit. Most rapla deployments don't have both.
2. **Realm-config dependency.** Even for benefiting deployments,
   the realm admin must do the one-time client permission grant —
   that's a coordination cost that has to be borne *per
   deployment*, not absorbed by the rapla codebase.
3. **Audit-trail story splits.** With Option 1, the entire
   impersonation event is captured in one log stream (rapla's). With
   3b, the actor decision and the token issuance live in two stores
   (rapla logs + Keycloak event log); ops needs to correlate them.
   That's not bad, but it's extra work the v1 reviewer shouldn't
   underestimate.
4. **`mode=idp-exchange` fallback paths increase the test matrix.**
   Each provider × each fallback condition × each authorization
   outcome needs explicit coverage. Worth doing later, not now.

**When to revive:**

- A DHBW-style deployment specifically requests it on compliance
  grounds AND
- the realm admin commits to keeping the client-level permission in
  place (otherwise v1 + mitigations 1–3 in § Security & compliance
  is the better answer).

A follow-up PRD would carry the implementation. Spec for that PRD
should at minimum: confirm Keycloak's specific token-exchange
configuration steps against the latest Keycloak release at the time,
build out the fallback-to-Option-1 behavior, write the tier-3
MockMvc test against a stubbed token-exchange endpoint, and add an
e2e scenario against a real configured Keycloak realm.
