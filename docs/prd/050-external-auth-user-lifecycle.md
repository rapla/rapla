# PRD 050 — External-auth user lifecycle (passwords, names, emails, disconnect)

**Status:** in-progress (Phases 1–4 + 6 landed 2026-05-21; Phase 5 cut; dispatch-path audit follow-up open; re-opened 2026-05-28 for Phases 7+8 — provisioning extraction)
**Date:** 2026-05-21 (re-opened 2026-05-28)
**Related:** PRD 036 (external IdP OAuth login), PRD 037 (native SAML / Shibboleth), PRD 049 (controller interface dedup — where the wire shape changes land), PRD 053 (plugin coordination pattern for cross-repo refactors), JNDI plugin (LDAP auth), AGENTS.md §16 (read APIs don't mutate — the rule that motivates Phase 7)

## 2026-05-28 — Re-opened for provisioning extraction (Phases 7+8)

Phase 2 ("stamp at auth time") landed the source marker on every successful external-auth match by writing through `ExternalUserResolver.syncFromIdp` (`facade.store(...)`) on the resource-server request path. That placement turned out to be wrong on two axes:

1. **Read-path-with-side-effects.** `ExternalUserResolver.resolve(jwt, provider)` is called from `SpringSecurityRemoteSession.resolveJwtOrThrow` on every authenticated request bearing an external-IdP JWT. Each call may issue a `storeAndRemove` against the User row. AGENTS.md §16 (added 2026-05-28) forbids this — anything shaped as a read (`resolve*`, auth-filter identity resolution) must be side-effect-free. Provisioning belongs at a write/lifecycle seam, not the per-request read.
2. **Two-writer churn for deployments with both stores enabled (DHBW).** When `JNDIAuthenticationStore` / `DhbwNtlmAuthStore` write `name`/`email` in one canonical form and `ExternalUserResolver` rewrites them in the JWT-claim form (case-sensitive `.equals` on name, equalsIgnoreCase on email — also inconsistent), the row oscillates on every login burst. Concurrent SPA bootstrap requests hit `RaplaNewVersionException` on the optimistic version check; an exception-wrapping bug in `SpringSecurityRemoteSession.resolveJwtOrThrow` (catches `RaplaException` and rethrows as `RaplaSecurityException`) surfaced this as a 401 "Sign-in rejected" modal in the SPA. Symptom observed 2026-05-28 against the DHBW Keycloak realm with a Swing client also open under the same identity.

Phase 7 moves provisioning to the at-login seam. Phase 8 extracts a common provisioner core with a single plugin override bean so DHBW's deployment-specific behaviour (AD-role-mapped groups, "email only when empty", no name overwrite) lives in one class in dhbwrapla instead of three half-overlapping ones across rapla + dhbwrapla.

The exception-wrap bug in `SpringSecurityRemoteSession.resolveJwtOrThrow` (catches `RaplaException` → rethrows as `RaplaSecurityException` → 401) is the symptom path. It's not in scope of this PRD because Phase 7 makes the underlying `RaplaNewVersionException` unreachable from `resolve` by construction (resolve becomes pure). If a future change reintroduces a write into resolve, this exception-wrap will re-surface the same misleading 401; the rule-16 audit + the architecture test under Phase 7 catches that.

## Goal

Stop rapla from silently maintaining a "shadow" local password / name / email for users whose identity actually lives in an external IdP (Keycloak, LDAP, future SAML). Today the rapla client lets such users (or admins) change their rapla-local fields, which never sync back to the IdP — creating drift, leaking the impression that the change took effect, and (for passwords) creating two independent credentials that grant access to rapla under the same username.

After this PRD: rapla knows for each user where their identity lives; users with external auth cannot change their own password / name / email through rapla at all; admins cannot either (no fallback — explicitly chosen as the secure default); the only admin action available is **"disconnect from external auth"**, which clears the marker and converts the user back to local-only, after which the usual change flows apply.

## Why no admin password-set fallback

An admin "I'll just set them a temporary rapla password while Keycloak is down" creates exactly the shadow-credential the rest of this PRD is trying to prevent. The user now has two valid credentials, the rapla one isn't tracked by the IdP's session policy / lockout / rotation, and revoking the IdP doesn't revoke rapla access. The secure alternative is the disconnect: admin explicitly says "this user is no longer external — they're rapla-local now", which is auditable and one-way (the user can be re-attached on next IdP login if the IdP comes back, but only after a fresh login flow). No surprise shadow credentials.

## Scope

### Persisted state — one field on the User entity

```java
// rapla-core entity model
String getAuthenticationSource();         // null = local, otherwise "<scheme>:<tag>"
void   setAuthenticationSource(String);
```

Convention:
- `null` or omitted = local (rapla DB password)
- `"keycloak:<realm>"` = OAuth2 via Keycloak realm
- `"ldap:<store-id>"` = LDAP via the JNDI plugin
- Future: `"saml:<idp-tag>"`, `"azure-ad:<tenant>"`, `"google:..."`

The string is operator-readable — used directly in the "managed by Keycloak (realm-vrz)" tooltip on the UI.

### Storage changes

| Backend | Change |
|---|---|
| XML (`FileOperator`) | Round-trip new attribute `authentication-source="..."` on `<user>` element; omit when null. |
| SQL (`DBOperator`/`RaplaSQL`) | Add column `AUTHENTICATION_SOURCE VARCHAR(64) NULL` to `RAPLA_USER`. Use the existing additive pattern: `checkAndAdd(schema, "AUTHENTICATION_SOURCE")` in `UserStorage.createOrUpdateIfNecessary`. Existing rows stay null = local. |
| `LocalCache` | No new key — entity field propagates. |
| `UserImpl` | New backing field + serialization. |

Migration is additive — existing users default to null = local; no backfill.

### Stamping at auth time

| Path | Action |
|---|---|
| `ExternalUserResolver.resolve(...)` (PRD 036 OAuth) | On match or auto-provision, set `authenticationSource = "<scheme>:<tag>"` from the provider config if currently null. Don't overwrite an existing marker. |
| `AuthenticationStoreAuth.authenticate(...)` (JNDI/LDAP plugin) | Same — set `"ldap:<store-id>"` if null. |
| Direct DB login (`grant_type=password` against rapla itself) | No change. Leaves the marker as-is. |

### Decision matrix

| Actor | Target identity source | Password | Name | Email | Disconnect-from-external |
|---|---|---|---|---|---|
| Self (actor == target) | local | allowed | allowed | allowed | N/A |
| Self (actor == target) | external | **blocked** ("managed by …") | **blocked** | **blocked** | **blocked** (admin-only) |
| Admin, target ≠ actor | local | allowed | allowed | allowed | N/A |
| Admin, target ≠ actor | external | **blocked** (no fallback — disconnect first) | **blocked** | **blocked** | **allowed** |

Server-side enforcement at every endpoint — not UI-only. UI hides buttons based on the capability struct; server still rejects if the UI fails.

### Endpoint changes

- `RemoteStorage.changePassword(...)` — reject (`RaplaSecurityException`) if target's `authenticationSource != null`, with a message naming the IdP.
- `RemoteStorage.changeName(...)` — same.
- `RemoteStorage.changeEmail(...)` — same.
- `RemoteStorage.confirmEmail(...)` — same.
- **New** `POST /api/storage/user/{userId}/disconnect-external-auth` — admin-only. Clears `authenticationSource` on the target user. Idempotent (no-op if already null).
- **New** `GET /api/storage/profile/capabilities` — returns the current user's self-edit capabilities:
  ```java
  record ProfileEditCapabilities(
      boolean canChangePassword,
      boolean canChangeName,
      boolean canChangeEmail,
      String externalIdpLabel  // e.g. "Keycloak (realm-vrz)"; null when local
  );
  ```
  Replaces the existing single-boolean `canChangePassword()`. Old method kept as a thin delegate for one Swing release, then removed.

The block + disconnect endpoints respect AGENTS.md §15 — declared on `RemoteStorage` interface, controller `implements` per PRD 049.

### UI changes

- **Swing `UserOption`**: probe `getProfileEditCapabilities()` on dialog open; hide the three buttons whose capability is false; show a "Your name / email / password is managed by Keycloak (realm-vrz). Change it there." label when external.
- **Swing admin user-management** (`UserEditUI` or similar): for each user, show an "External auth: Keycloak (realm-vrz)" badge when `authenticationSource != null`, plus a "Disconnect from external auth" button (admin only, calls the new endpoint). Hide the local change-password / name / email buttons in admin view too when external.
- **Angular SPA** (when the user-settings page lands): same shape via the capabilities endpoint.

## Plan

- [ ] Phase 1 — Persistence: User entity field, XML round-trip, SQL `checkAndAdd("AUTHENTICATION_SOURCE")` + `UserStorage.read/write`, XML round-trip test, SQL round-trip test.
- [ ] Phase 2 — Stamp at auth time: `ExternalUserResolver` + `AuthenticationStoreAuth` set the marker on first external-auth match. Tier-3 tests.
- [ ] Phase 3 — Server-side guard: block the four change-methods on external users. Replace single-boolean capability with the struct. New disconnect endpoint. Tier-3 MockMvc tests for each rejection.
- [ ] Phase 4 — Swing UI: capability probe in `UserOption`, "managed by" hint, admin badge + disconnect button.
- [ ] Phase 5 — Angular SPA: same shape on the user-settings page. Defer until SPA page lands (PRD 026 follow-up).
- [ ] Phase 6 — Cleanup: remove old `canChangePassword()` boolean once one Swing release has shipped with the capabilities struct.
- [ ] Phase 7 — Move provisioning off the resource-server resolve path. `ExternalUserResolver.resolve(jwt, provider)` shrinks to pure identity translation (username/email lookup, returns User or throws). Sync + auto-provision called only from the at-login seams: `OAuthExchangeController.exchange` (after decoding the IdP token returned by Keycloak/Google/Microsoft) and `RaplaAuthentificationService.authenticate` (already at-login — password grant + form-login + JNDI/DHBW direct-Bearer path). Resource-server access becomes side-effect-free per AGENTS.md §16.
- [ ] Phase 8 — Extract common `UserProvisioner` interface + `DefaultUserProvisioner` core. Consolidate the three current write sites (`ExternalUserResolver.syncFromIdp` + `.autoProvisionUser`, `JNDIAuthenticationStore.initUser`, dhbwrapla's `DhbwNtlmAuthStore.initUser`) onto the common provisioner. Single canonical equals/equalsIgnoreCase rules (fixes the name-case-sensitivity inconsistency). Single `authenticationSource` stamp site (fixes DHBW users being mislabeled `"ldap"` because the stamp lives outside `initUser` in `RaplaAuthentificationService.authenticate`). Operator-not-facade (fixes the workingUser-leak risk on the server-singleton FacadeImpl).

## Phase 7 + 8 detail — Provisioning extraction

### What ExternalUserResolver becomes (Phase 7)

`resolve(jwt, provider) → User` becomes pure:

- Lookup by `upn` → `preferred_username` → `email`, case-insensitively against `user.getUsername()`.
- Email-as-username fallback when `email_verified` (Google guard) / claim absent (Entra).
- Returns the matched User from cache. **Does not write. Does not auto-provision.**
- Throws `RaplaSecurityException("No rapla user matched …")` when no match.

`syncFromIdp` and `autoProvisionUser` move out. `applyConfiguredGroupsIfPresent` moves out (it's part of provisioning).

### Where provisioning runs (Phase 7)

| Login seam | Caller | What it does |
|---|---|---|
| `OAuthExchangeController.exchange` (`POST /api/auth/oauth/exchange/{provider}`) — SPA Keycloak/Google/Microsoft | After Keycloak returns 200, decode the access token using `IssuerAwareJwtDecoder`, build `IdentityClaims` from the JWT, call `provisioner.provision(claims)`. Returns the original Keycloak token to the SPA unchanged. | Runs once per `authorization_code` exchange and once per `refresh_token` exchange. |
| `RaplaAuthentificationService.authenticate(...)` — password grant + form-login + JNDI/DHBW direct-Bearer | After `authenticationStore.authenticate(username, password)` returns true, build `IdentityClaims` via the auth store's new `extractClaims(...)` method, call `provisioner.provision(claims)`. | Already at-login. Today's `initUser` body inlined into the provisioner. |
| `SpringSecurityRemoteSession.resolveJwtOrThrow` (resource-server) | **No provisioning.** Calls only `externalUserResolver.resolve` (pure) or `operator.resolve(sub)` for rapla-SAS tokens. | Side-effect-free per AGENTS.md §16. |

Direct-Bearer clients hitting `/api/*` with a Keycloak token they obtained themselves (no exchange) get `No rapla user matched …` until either an admin provisions them or they re-enter via `/api/auth/oauth/exchange`. Documented as the intentional policy (provisioning is tied to a deliberate sign-in event, not "any Keycloak token I happened to acquire").

### IdentityClaims + UserProvisioner (Phase 8)

```java
public record IdentityClaims(
    String username,                      // rapla username key — never null
    String displayName,                   // null = "no info, don't touch"
    String email,                         // null = "no info, don't touch"
    String sourceId,                      // e.g. "keycloak", "ldap", "dhbw-ntlm" — never null
    Collection<String> groupKeys          // null = use provisioner's default group resolution
) {}

public interface UserProvisioner {
    User provision(IdentityClaims claims) throws RaplaException;
}
```

`DefaultUserProvisioner`:

- Find by `claims.username()` via `operator.getUser(...)`; if absent, allocate UserImpl + `operator.createIdentifier(User.class, 1)` + add default groups via `resolveGroups(claims)`.
- Edit clone via `operator.editObjects(singleton, null)` (operator-not-facade — explicit null actor, no workingUser leak).
- Apply universal field rules:
  - `setUsername` if differs.
  - `setName` if `displayName != null && !displayName.equalsIgnoreCase(getName())` (consistent with email — fixes the existing case-sensitivity bug).
  - `setEmail` if `email != null && !email.equalsIgnoreCase(getEmail())`.
  - `setAuthenticationSource(claims.sourceId())` if differs.
- `operator.storeAndRemove(singleton, emptyList, null)`.
- `protected Collection<Category> resolveGroups(IdentityClaims claims) throws RaplaException` — default reads `JNDIPlugin.USERGROUP_CONFIG` system pref (today's `ConfiguredGroupResolver` behaviour). Subclasses override for AD-role-mapped groups, JWT-claim-derived groups, etc.

Registered as `@Bean @ConditionalOnMissingBean` in core so plugins replace by contributing their own `UserProvisioner` bean.

### Plugin override (dhbwrapla)

dhbwrapla ships `DhbwUserProvisioner extends DefaultUserProvisioner` in its `@AutoConfiguration` (same pattern as `DhbwNtlmAuthStore` today):

- Override `resolveGroups(claims)` — wraps `DhbwLdapGroupMapper` to map username → group keys via `DhbwAuthPreferences.CONFIG`'s `RoleMapping`.
- Override the per-field policy where DHBW differs: email **only when empty** (don't overwrite user-set values from AD), name **never touched** (DHBW doesn't sync display name today).

`DhbwNtlmAuthStore.initUser(...)` body deletes; replaced by `extractClaims(username, password) → IdentityClaims` (with `sourceId = "dhbw-ntlm"`, fixing today's mislabel where `RaplaAuthentificationService` stamps `"ldap"` for DHBW users regardless of which auth store ran). The provisioning side is the responsibility of the shared `UserProvisioner` bean (DHBW's override).

### AuthenticationStore interface change

```java
public interface AuthenticationStore {
    boolean authenticate(String username, String password) throws RaplaException;
    IdentityClaims extractClaims(String username, String password) throws RaplaException;
    // initUser(...) deleted — provisioning moves to UserProvisioner
}
```

Existing impls: `JNDIAuthenticationStore` (`sourceId = "ldap"`), `DhbwNtlmAuthStore` (`sourceId = "dhbw-ntlm"`). Both shrink to the two-method shape. The `boolean modified` return goes away — the provisioner decides.

### Cross-repo coordination

Same pattern as PRD 053 (logger refactor): land rapla-side first with the new SPI + a temporary keep-alive of the old `AuthenticationStore.initUser` default method (no-op) so dhbwrapla builds during the gap, then dhbwrapla lands its `DhbwUserProvisioner` + the new `extractClaims` method, then rapla removes the keep-alive default. Tracked as a paired-PRD note in dhbwrapla's CLAUDE.md / AGENTS.md.

### Smallest related fix — RemoteLocaleController single-call

`RemoteLocaleController.locale(...)` (`rapla-server/.../RemoteLocaleController.java:56-58`) today calls both `session.isAuthentified(request)` and `session.checkAndGetUser(request)` in sequence. Each call invokes `SpringSecurityRemoteSession.resolveJwtOrThrow` → `externalUserResolver.resolve` → (today) `syncFromIdp` → write. So one locale fetch fires the IdP sync **twice** per request, in sequence. Even after Phase 7 makes `resolve` pure, the double cache-lookup + double user-resolution is wasted work.

Fix: collapse to a single `checkAndGetUser` inside a `try`/`catch (RaplaSecurityException)`. Anonymous callers (no Bearer attached) get caught; rest falls through. Lands as the first deliverable in Phase 7 — independent of the bigger refactor, no PRD-coordination needed.

## Tests

- **Tier-2 (storage)** — XML and SQL round-trip a user with `authenticationSource = "keycloak:realm-vrz"`. Verify the column appears after `checkAndAdd`. Backfill test: existing rows with no column → load as null.
- **Tier-2 (entity)** — `UserImpl.setAuthenticationSource` + clone preserve the field.
- **Tier-3 (auth stamp)** — POST a fresh JWT to `/oauth2/token` (Keycloak issuer); the auto-provisioned user has `authenticationSource = "keycloak:..."`. Login again — marker not overwritten.
- **Tier-3 (block)** — for each of the four change methods, with target user marked `"keycloak:..."`:
  - Self-call → 403 with `externalIdpLabel` in the body.
  - Admin call → 403 (no fallback).
- **Tier-3 (disconnect)** — non-admin POST → 403. Admin POST → 200, target's `authenticationSource` is null, subsequent `changePassword` for target succeeds.
- **Tier-3 (capabilities)** — local user: all three booleans true, label null. Keycloak user: all false, label = `"Keycloak (realm-vrz)"`.
- **Tier-3 (Phase 7 — pure resolve)** — `SpringSecurityRemoteSession.checkAndGetUser` with an external-IdP JWT, where the rapla user's `name`/`email`/`authenticationSource` disagree with the JWT claims, must **not** write to the User row. Assert `getLastChanged()` and the field values are unchanged after the call. (Negative test that fails today, passes after Phase 7.)
- **Tier-3 (Phase 7 — RemoteLocaleController)** — `GET /api/locale` with an external-IdP JWT triggers `externalUserResolver.resolve` exactly once. Spy or instrumented counter; assert N=1 after one HTTP call (today: N=2).
- **Tier-3 (Phase 7 — provisioning at exchange)** — `POST /api/auth/oauth/exchange/{provider}` with a Keycloak token whose claims describe a brand-new user → after the call, the user exists in rapla with `authenticationSource = "keycloak"`, claimed name/email, configured groups. Without going through `/exchange`, the same Keycloak token presented as Bearer to `/api/...` gets 401 `No rapla user matched`.
- **Tier-2 (Phase 8 — DefaultUserProvisioner)** — pure-Java unit test against `FacadeTestSupport`. Construct claims, call `provision(...)`, assert exactly the right fields written and `equalsIgnoreCase` applied to both name and email.
- **Tier-2 (Phase 8 — sourceId stamp)** — claims with `sourceId = "dhbw-ntlm"` produce a user with `authenticationSource = "dhbw-ntlm"`, not `"ldap"`. (Fixes the existing mislabel.)
- **Tier-3 (Phase 8 — dhbwrapla plugin override)** — with `DhbwUserProvisioner` registered, `provision(claims)` invokes the role-mapping group resolver, applies "email only when empty", does not touch name. Lives in dhbwrapla repo, runs against rapla's `DefaultUserProvisioner` via Spring `@AutoConfiguration`.
- **Architecture test** — `ApiResolveSideEffectArchitectureTest` (new): scan `ExternalUserResolver.resolve` + every public method on `RemoteSession` for transitive calls to `operator.storeAndRemove*` / `facade.store*` / `editObjects` followed by a store. Fails CI if a write reappears on the resolve path. Cements AGENTS.md §16 for this specific surface.

## Open questions

1. **Capability endpoint per-user or self-only?** Recommend self-only — admin UI doesn't need a server probe (admin knows the rules) and self-only avoids the "tell me about every user" iteration cost. Server still enforces the matrix on every change-method regardless of who's asking. **Decided 2026-05-21: self-only.**
2. **Disconnect → require password reset on next login?** When admin disconnects a Keycloak user, their `authenticationSource` becomes null and they have no rapla password. The next admin step is to set a password (which is now allowed). Recommend: the admin UI flow is "disconnect" + "set password" as a single dialog (two HTTP calls but one UI action). **Open.**
3. **`authentication-source` value format** — proposed `"<scheme>:<tag>"`. Open: do we need a separate `external-user-id` field for re-linking on username rename? PRD 036 explicitly said no. Stick with no. **Decided 2026-05-21: simple provider id (`"keycloak"` / `"ldap"` / `"google"`), no realm tag for now; can extend later.**

## Known follow-up — dispatch-path security gap

The new endpoint-level guards (`requireLocalIdentity` in `RemoteStorageController.changePassword`/`changeName`/`changeEmail`/`confirmEmail`) and the admin-only `disconnectExternalAuth` cover the named-endpoint surface. **They do NOT cover the bulk `dispatch(UpdateEvent)` path.** A malicious / careless non-admin user with a `keycloak`-marked entity could in principle:

1. Pull their own User entity via `getEntityRecursive`.
2. Locally edit `authenticationSource` to `null` in the entity.
3. POST the modified entity back via `dispatch(UpdateEvent)`.
4. `LocalAbstractCachableOperator.dispatch` runs `securityManager.checkWritePermissions(user, entity)` — passes (user can write themselves).
5. Marker is cleared. User can now set a local password.

Same shape applies to other server-side-only fields (e.g. `isAdmin` — also editable by anyone via dispatch unless there's a hidden check I haven't found). Worth a separate audit / PRD: per-field admin-only mutation checks at the storage layer for User entities. Tracked as PRD 050 follow-up; not blocking the named-endpoint guards landing.

The Swing UI's `UserEditUI` correctly only exposes the disconnect button to admins (the dialog itself is admin-only), so this gap requires deliberate HTTP-level exploitation, not an accidental UI click.

## Phase status (2026-05-21)

- [x] Phase 1 — Persistence: User field, XML round-trip, SQL column + `checkAndAdd` migration. 7/7 XmlRoundTripTest green.
- [x] Phase 2 — Stamp at auth: ExternalUserResolver + RaplaAuthentificationService set the marker on first external match (idempotent on subsequent logins).
- [x] Phase 3 — Server-side guards: 4 change-method blocks + new `getProfileEditCapabilities()` + admin-only `disconnectExternalAuth()`. 10/10 ExternalAuthLifecycleIntegrationTest green.
- [x] Phase 4 — Swing UI complete: (a) admin `UserEditUI` `AuthenticationSourceField` shows source + "Disconnect" button that clears the marker on save; (b) self `UserOption` probes `getProfileEditCapabilities()` and disables change-name/email/password buttons with "Managed by &lt;label&gt; — change there" tooltip + visible hint when external.
- [x] ~~Phase 5 — Angular SPA~~ **Cut from scope 2026-05-21** — no SPA user-settings page in current plan.
- [x] Phase 6 — `RemoteStorage.canChangePassword()` REST endpoint removed; controller impl deleted; `RemoteOperator.canChangePassword()` now routes through `getProfileEditCapabilities().canChangePassword()` so the local `ClientFacade.canChangePassword()` call site (used by `UserAction`) keeps working without code changes. New test `legacyCanChangePasswordEndpoint_is404_afterPrd050Removal` pins the removal.
- [ ] **Follow-up audit** — close the dispatch-path bypass (a non-admin can in principle clear their own `authenticationSource` via `dispatch(UpdateEvent)`). Same shape applies to `isAdmin` and any other server-side-only User field. Tracked separately; not blocking PRD 050.

## Phase status (2026-05-28 re-open)

Landed in a single coordinated rapla + dhbwrapla session 2026-05-28 (no
prod traffic on the spring-boot branch, so Pattern B clean — no
`initUser` keep-alive shim).

- [x] **Phase 7** — Provisioning moved off the resource-server resolve path.
  - [x] 7a. `RemoteLocaleController.locale(...)` — single `checkAndGetUser` inside try/catch. One `resolveJwtOrThrow` per locale request (was two).
  - [x] 7b. `ExternalUserResolver.resolve` is pure: lookup → User or `RaplaSecurityException`. `syncFromIdp` / `autoProvisionUser` / `applyConfiguredGroupsIfPresent` removed. New `claimsFor(jwt, provider) → IdentityClaims` is the read-side translator the exchange seam consumes. Constructor takes `CachableStorageOperator`, not `RaplaFacade` (no workingUser leak).
  - [x] 7c. `OAuthExchangeController.exchange` — after Keycloak returns 2xx, decode `id_token` (or JWT-shaped `access_token`) via the configured `JwtDecoder`, build claims via `ExternalUserResolver.claimsFor`, call `provisioner.provision`. Provisioning failures logged + swallowed; the IdP token still returns to the SPA. Opaque tokens (Google access_token without `openid` scope) skipped — log + no-op.
  - [ ] 7d. `ApiResolveSideEffectArchitectureTest` — deferred. The tier-2 test `resolveDoesNotWriteEvenWhenClaimsDiffer` in `ExternalUserResolverTest` covers the resolve-no-write invariant. CI-level architecture sweep can land when the rule is generalised beyond this surface.
- [x] **Phase 8** — Common provisioner core + plugin SPI.
  - [x] 8a. `IdentityClaims` record (`org.rapla.server.IdentityClaims` — rapla-server, not rapla-core; the type is only used at the server-auth seam).
  - [x] 8b. `UserProvisioner` interface (`org.rapla.server.UserProvisioner`) + `DefaultUserProvisioner` impl (`org.rapla.server.internal.DefaultUserProvisioner`). Registered as `@Bean @ConditionalOnMissingBean defaultUserProvisioner` in `ServerServiceConfig`. `operator` field is `protected` so subclasses can do their own pre-provision lookups without holding a duplicate reference.
  - [x] 8c. `AuthenticationStore` interface — `initUser(...)` deleted; `extractClaims(username, password) → IdentityClaims` added. No keep-alive default (clean break; no prod traffic on spring-boot branch).
  - [x] 8d. `JNDIAuthenticationStore.extractClaims` returns `sourceId = "ldap"`; `groupKeys = null` so the default provisioner falls back to `JNDIPlugin.USERGROUP_CONFIG`. `initUser` body removed.
  - [x] 8e. `RaplaAuthentificationService.authenticate` — calls `authStore.authenticate` → `authStore.extractClaims` → `provisioner.provision`. The old in-line find-or-create block + the hardcoded `"ldap"` stamp at lines 158-162 deleted. `sourceId` now comes from the auth store via `IdentityClaims`.
  - [x] 8f. dhbwrapla — `DhbwNtlmAuthStore.extractClaims` returning `sourceId = "dhbw-ntlm"` + `DhbwUserProvisioner extends DefaultUserProvisioner` (registered as `@Component @ConditionalOnBean(DhbwNtlmAuthStore.class)`). DHBW per-field policy: name never overwritten, email only when local is empty, groups exclusively from `DhbwLdapGroupMapper` (no JNDI-pref fallback). The "kein Standort zugeordnet" Standort-required guard moved into `DhbwUserProvisioner.provision` (uses the `groupKeys=null` signal from `extractClaims`). Existing DHBW users keep `authenticationSource = "ldap"` until their next login, when the provisioner re-stamps to `"dhbw-ntlm"`.
  - [ ] 8g. PRD 050 Phase 6's `canChangePassword()` boolean removal — orthogonal to Phases 7/8 and tracked in its existing checkbox. No additional cleanup needed under the new shape.

### Verified tests this session (2026-05-28)

- `ExternalUserResolverTest` — 18/18 pass after rewrite (split resolve-pure tests from claims-extraction + provisioner tests).
- dhbwrapla suite via aggregator — 36/36 pass (10 skipped tagged `db`/`e2e` per the standard exclusion).
- `mvn clean test-compile` clean for rapla-bom/core/client/server and dhbwrapla.
- rapla-app test suite not run in this session due to **parallel-session in-flight edits** in `rapla-app/src/main/java/org/rapla/server/spring/graphql/` (mtime 20:17–20:19, AGENTS.md §7: never fix/revert work in files another session is editing). The auth-side test `AuthenticationStoreInjectionTest` was updated for the new interface shape; test-compile clean.
