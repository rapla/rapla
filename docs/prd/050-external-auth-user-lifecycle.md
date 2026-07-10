# PRD 050 — External-auth user lifecycle (passwords, names, emails, disconnect)

**Status:** in-progress (Phases 1–4 + 6 landed 2026-05-21; Phase 5 cut; dispatch-path audit follow-up open; re-opened 2026-05-28 for Phases 7+8 — provisioning extraction)
**Date:** 2026-05-21 (re-opened 2026-05-28)
**Related:** [PRD 036](036-external-idp-oauth-login.md) (external IdP OAuth login), [PRD 037](037-native-saml-shibboleth.md) (native SAML / Shibboleth), [PRD 049](049-controller-interface-deduplication.md) (controller interface dedup — where the wire shape changes land), [PRD 053](done/053-replace-rapla-logger-with-slf4j.md) (plugin coordination pattern for cross-repo refactors), JNDI plugin (LDAP auth), AGENTS.md §16 (read APIs don't mutate — the rule that motivates Phase 7)

## 2026-05-28 — Re-opened for provisioning extraction (Phases 7+8)

Phase 2's source-marker write through `ExternalUserResolver.syncFromIdp` (`facade.store(...)`) sits on the resource-server request path — wrong on two axes:

1. **Read-path-with-side-effects.** `ExternalUserResolver.resolve(jwt, provider)` runs from `SpringSecurityRemoteSession.resolveJwtOrThrow` on every authenticated request and may issue `storeAndRemove`. AGENTS.md §16 forbids this — provisioning belongs at a write/lifecycle seam.
2. **Two-writer churn for deployments with both stores enabled (DHBW).** `JNDIAuthenticationStore` / `DhbwNtlmAuthStore` write `name`/`email` in one canonical form; `ExternalUserResolver` rewrites them in the JWT-claim form (inconsistent: case-sensitive `.equals` on name, equalsIgnoreCase on email). The row oscillates on login bursts. Concurrent SPA bootstrap hits `RaplaNewVersionException`; an exception-wrap bug in `resolveJwtOrThrow` (catches `RaplaException` → rethrows as `RaplaSecurityException`) surfaces this as a 401 "Sign-in rejected" modal. Observed 2026-05-28 against DHBW Keycloak.

Phase 7 moves provisioning to the at-login seam. Phase 8 extracts a common provisioner core so DHBW's deployment-specific behaviour (AD-role groups, "email only when empty", no name overwrite) lives in one class in dhbwrapla.

The exception-wrap bug is out of scope — Phase 7 makes the underlying `RaplaNewVersionException` unreachable from `resolve` by construction. If a future change reintroduces a write into resolve, rule-16 audit + the architecture test catches it.

## Goal

Stop rapla from silently maintaining a "shadow" local password / name / email for users whose identity lives in an external IdP (Keycloak, LDAP, future SAML). Today the rapla client lets such users (or admins) change rapla-local fields, which never sync back to the IdP — creating drift and (for passwords) two independent credentials under the same username.

After this PRD: rapla tracks each user's identity source; external-auth users (and admins acting on them) cannot change password / name / email through rapla at all. The only admin action is **"disconnect from external auth"**, which clears the marker and converts the user back to local-only.

## Why no admin password-set fallback

An admin "temporary rapla password while Keycloak is down" creates exactly the shadow-credential this PRD prevents — two valid credentials, the rapla one outside IdP session policy / lockout / rotation. The secure alternative is the explicit one-way disconnect (auditable, re-attachable on next IdP login).

## Scope

### Persisted state — one field on the User entity

```java
String getAuthenticationSource();         // null = local, otherwise "<scheme>:<tag>"
void   setAuthenticationSource(String);
```

Values: `null`/omitted = local; `"keycloak:<realm>"`; `"ldap:<store-id>"`; future `"saml:<idp-tag>"`, `"azure-ad:<tenant>"`, `"google:..."`. Operator-readable — surfaced directly in UI tooltips.

### Storage changes

| Backend | Change |
|---|---|
| XML (`FileOperator`) | Round-trip `authentication-source="..."` on `<user>`; omit when null. |
| SQL (`DBOperator`/`RaplaSQL`) | `AUTHENTICATION_SOURCE VARCHAR(64) NULL` on `RAPLA_USER` via `checkAndAdd(schema, "AUTHENTICATION_SOURCE")` in `UserStorage.createOrUpdateIfNecessary`. Additive migration; existing rows null = local. |
| `UserImpl` | New backing field + serialization. |

### Stamping at auth time

- `ExternalUserResolver.resolve` (OAuth) — set `"<scheme>:<tag>"` on match/auto-provision if null. Don't overwrite.
- `AuthenticationStoreAuth.authenticate` (JNDI) — set `"ldap:<store-id>"` if null.
- Direct DB login — no change.

### Decision matrix

| Actor | Target identity source | Password | Name | Email | Disconnect-from-external |
|---|---|---|---|---|---|
| Self (actor == target) | local | allowed | allowed | allowed | N/A |
| Self (actor == target) | external | **blocked** ("managed by …") | **blocked** | **blocked** | **blocked** (admin-only) |
| Admin, target ≠ actor | local | allowed | allowed | allowed | N/A |
| Admin, target ≠ actor | external | **blocked** (no fallback — disconnect first) | **blocked** | **blocked** | **allowed** |

Server-side enforcement at every endpoint — not UI-only. UI hides buttons based on the capability struct; server still rejects if the UI fails.

### Endpoint changes

- `RemoteStorage.{changePassword,changeName,changeEmail,confirmEmail}` — reject (`RaplaSecurityException`, IdP-naming message) if target's `authenticationSource != null`.
- **New** `POST /api/storage/user/{userId}/disconnect-external-auth` — admin-only; clears the marker; idempotent.
- **New** `GET /api/storage/profile/capabilities`:
  ```java
  record ProfileEditCapabilities(
      boolean canChangePassword, boolean canChangeName, boolean canChangeEmail,
      String externalIdpLabel  // e.g. "Keycloak (realm-vrz)"; null when local
  );
  ```
  Replaces single-boolean `canChangePassword()`. Old method kept one Swing release then removed.

Per AGENTS.md §15 — declared on `RemoteStorage`, controller `implements` per [PRD 049](049-controller-interface-deduplication.md).

### UI changes

- **Swing `UserOption`**: probe capabilities; hide the three buttons whose capability is false; show "managed by Keycloak (realm-vrz). Change it there." when external.
- **Swing admin `UserEditUI`**: badge + "Disconnect from external auth" button; hide local change buttons when external.
- **Angular SPA**: same shape via the capabilities endpoint (deferred until SPA user-settings page lands).

## Plan

- [ ] Phase 1 — Persistence: User field, XML round-trip, SQL `checkAndAdd`, round-trip tests.
- [ ] Phase 2 — Stamp at auth time: `ExternalUserResolver` + `AuthenticationStoreAuth` set marker on first match. Tier-3 tests.
- [ ] Phase 3 — Server-side guard: block 4 change-methods on external users; capabilities struct; disconnect endpoint; Tier-3 MockMvc per rejection.
- [ ] Phase 4 — Swing UI: capability probe in `UserOption`; admin badge + disconnect button.
- [ ] Phase 5 — Angular SPA: same shape (defer until SPA settings page lands).
- [ ] Phase 6 — Remove old `canChangePassword()` boolean.
- [ ] Phase 7 — Move provisioning off resource-server resolve path. `resolve` becomes pure (lookup → User or throw). Sync/auto-provision only at login seams: `OAuthExchangeController.exchange` + `RaplaAuthentificationService.authenticate`. Per AGENTS.md §16.
- [ ] Phase 8 — Extract `UserProvisioner` + `DefaultUserProvisioner`. Consolidate the three write sites onto common provisioner. Single canonical equals/equalsIgnoreCase (fixes name case-sensitivity inconsistency). Single `authenticationSource` stamp (fixes DHBW mislabel `"ldap"`). Operator-not-facade (no workingUser leak).

## Phase 7 + 8 detail — Provisioning extraction

### What ExternalUserResolver becomes (Phase 7)

`resolve(jwt, provider) → User` becomes pure: lookup by `upn` → `preferred_username` → `email` (case-insensitive); email-as-username fallback when `email_verified` (Google) / claim absent (Entra); returns matched User or throws `RaplaSecurityException("No rapla user matched …")`. **No write, no auto-provision.** `syncFromIdp` / `autoProvisionUser` / `applyConfiguredGroupsIfPresent` move out.

### Where provisioning runs (Phase 7)

| Login seam | Action |
|---|---|
| `OAuthExchangeController.exchange` (SPA Keycloak/Google/Microsoft) | After Keycloak returns 200, decode access token via `IssuerAwareJwtDecoder`, build `IdentityClaims`, call `provisioner.provision`. Runs once per `authorization_code` / `refresh_token` exchange. |
| `RaplaAuthentificationService.authenticate` (password grant + form-login + JNDI/DHBW direct-Bearer) | After `authStore.authenticate` returns true, call `authStore.extractClaims` → `provisioner.provision`. |
| `SpringSecurityRemoteSession.resolveJwtOrThrow` (resource-server) | **No provisioning.** Pure resolve only. |

Direct-Bearer clients hitting `/api/*` with a self-acquired Keycloak token get `No rapla user matched` until an admin provisions them or they enter via `/api/auth/oauth/exchange`. Intentional: provisioning ties to a deliberate sign-in event.

### IdentityClaims + UserProvisioner (Phase 8)

```java
public record IdentityClaims(
    String username,                      // never null
    String displayName,                   // null = don't touch
    String email,                         // null = don't touch
    String sourceId,                      // "keycloak"/"ldap"/"dhbw-ntlm" — never null
    Collection<String> groupKeys          // null = use provisioner default
) {}

public interface UserProvisioner {
    User provision(IdentityClaims claims) throws RaplaException;
}
```

`DefaultUserProvisioner`:

- Find by `claims.username()`; if absent, allocate UserImpl + `operator.createIdentifier` + default groups via `resolveGroups(claims)`.
- Edit clone via `operator.editObjects(singleton, null)` (operator-not-facade, no workingUser leak).
- Universal field rules: `setUsername` if differs; `setName`/`setEmail` if `!equalsIgnoreCase(...)` (consistent with email — fixes existing case-sensitivity bug); `setAuthenticationSource(claims.sourceId())` if differs.
- `operator.storeAndRemove(singleton, emptyList, null)`.
- `protected Collection<Category> resolveGroups(IdentityClaims)` — default reads `JNDIPlugin.USERGROUP_CONFIG`; subclasses override for AD-role / JWT-claim groups.

Registered `@Bean @ConditionalOnMissingBean` so plugins override by contributing their own.

### Plugin override (dhbwrapla)

`DhbwUserProvisioner extends DefaultUserProvisioner` in dhbwrapla's `@AutoConfiguration`:

- Override `resolveGroups` — wraps `DhbwLdapGroupMapper` via `DhbwAuthPreferences.CONFIG`'s `RoleMapping`.
- Override per-field: email only when empty (don't overwrite AD-set values), name never touched.

`DhbwNtlmAuthStore.initUser(...)` deleted; replaced by `extractClaims → IdentityClaims` with `sourceId = "dhbw-ntlm"` (fixes today's mislabel where `RaplaAuthentificationService` stamps `"ldap"` regardless of which store ran).

### AuthenticationStore interface change

```java
public interface AuthenticationStore {
    boolean authenticate(String username, String password) throws RaplaException;
    IdentityClaims extractClaims(String username, String password) throws RaplaException;
    // initUser(...) deleted — provisioning moves to UserProvisioner
}
```

Existing impls: `JNDIAuthenticationStore` (`"ldap"`), `DhbwNtlmAuthStore` (`"dhbw-ntlm"`). Both shrink to two methods. `boolean modified` return goes away — provisioner decides.

### Cross-repo coordination

Same pattern as [PRD 053](done/053-replace-rapla-logger-with-slf4j.md): land rapla-side with a temporary `initUser` no-op default so dhbwrapla builds during the gap, then dhbwrapla lands its provisioner + `extractClaims`, then rapla removes the default. Tracked in dhbwrapla's CLAUDE.md.

### Smallest related fix — RemoteLocaleController single-call

`RemoteLocaleController.locale(...):56-58` calls both `session.isAuthentified(request)` and `session.checkAndGetUser(request)` in sequence — each fires `resolveJwtOrThrow → externalUserResolver.resolve → syncFromIdp → write`, so one locale fetch fires IdP sync twice. Fix: single `checkAndGetUser` in `try/catch (RaplaSecurityException)`. Lands as Phase 7's first deliverable.

## Tests

- **Tier-2 (storage)** — XML + SQL round-trip `authenticationSource = "keycloak:realm-vrz"`; verify `checkAndAdd` column; backfill test for legacy rows.
- **Tier-2 (entity)** — `UserImpl.setAuthenticationSource` + clone preserve field.
- **Tier-3 (auth stamp)** — Keycloak JWT POST to `/oauth2/token` → auto-provisioned user has `"keycloak:..."`. Re-login → marker not overwritten.
- **Tier-3 (block)** — 4 change methods × {self, admin} on external user → 403 with `externalIdpLabel`.
- **Tier-3 (disconnect)** — non-admin 403; admin 200, marker null, subsequent `changePassword` succeeds.
- **Tier-3 (capabilities)** — local user: all true, null label. External: all false, label populated.
- **Tier-3 (Phase 7 — pure resolve)** — `checkAndGetUser` with external JWT whose claims disagree → no write. (Fails today, passes after Phase 7.)
- **Tier-3 (Phase 7 — RemoteLocaleController)** — `GET /api/locale` fires `resolve` exactly once (was 2).
- **Tier-3 (Phase 7 — provisioning at exchange)** — `/api/auth/oauth/exchange/{provider}` with new-user Keycloak token → user exists with `"keycloak"` source. Same token as Bearer to `/api/...` without exchange → 401 `No rapla user matched`.
- **Tier-2 (Phase 8 — DefaultUserProvisioner)** — `FacadeTestSupport`-based; `provision` writes correct fields with `equalsIgnoreCase` on both name and email.
- **Tier-2 (Phase 8 — sourceId stamp)** — `sourceId = "dhbw-ntlm"` claims → user has `"dhbw-ntlm"`, not `"ldap"`.
- **Tier-3 (Phase 8 — dhbwrapla override)** — `DhbwUserProvisioner` uses role-mapping, email-only-when-empty, no name touch.
- **Architecture test** — `ApiResolveSideEffectArchitectureTest`: scan `ExternalUserResolver.resolve` + `RemoteSession` public methods for transitive writes; fails CI if reintroduced. Cements §16 for this surface.

## Open questions

1. **Decided 2026-05-21: capability endpoint self-only** — admin UI doesn't need server probe; server still enforces matrix on every change-method.
2. **Open:** Disconnect → require password reset on next login? Recommend admin UI flow combines "disconnect" + "set password" as single dialog.
3. **Decided 2026-05-21:** `authenticationSource` = simple provider id (`"keycloak"` / `"ldap"` / `"google"`), no realm tag; can extend later.

## Known follow-up — dispatch-path security gap

Endpoint-level guards cover the named-endpoint surface but **NOT the bulk `dispatch(UpdateEvent)` path**. A non-admin user could:

1. Pull own User via `getEntityRecursive`.
2. Locally edit `authenticationSource` to `null`.
3. POST via `dispatch(UpdateEvent)` — `checkWritePermissions` passes (user can write themselves).
4. Marker cleared; can now set local password.

Same shape applies to `isAdmin` and other server-side-only fields. Per-field admin-only mutation checks at the storage layer for User entities tracked as a PRD 050 follow-up; not blocking the named-endpoint guards. `UserEditUI` is admin-only, so this requires deliberate HTTP exploitation.

## Phase status (2026-05-21)

- [x] Phase 1 — Persistence: User field, XML round-trip, SQL `checkAndAdd`. 7/7 XmlRoundTripTest green.
- [x] Phase 2 — Stamp at auth (idempotent).
- [x] Phase 3 — Server guards: 4 blocks + capabilities + admin-only `disconnectExternalAuth`. 10/10 ExternalAuthLifecycleIntegrationTest green.
- [x] Phase 4 — Swing UI: admin `UserEditUI.AuthenticationSourceField` + disconnect; self `UserOption` probes capabilities, disables buttons + "Managed by &lt;label&gt;" tooltip when external.
- [x] Phase 5 — Angular SPA: **shipped 2026-06-27** (branch spring-boot). Originally cut 2026-05-21 (no SPA settings page); un-cut once the SPA grew a central user menu. `EditAccountDialogComponent` (`rapla-angular/src/app/account/`) over `ProfileService` consumes the existing `GET /api/storage/profile/capabilities` + `POST /api/storage/change/{name,email,password}`. The menu hides "Edit account" entirely when `externalIdpLabel != null`; the dialog additionally gates each section on the `canChange*` flags and shows the "managed by &lt;label&gt;" banner — same matrix as Swing's `UserOption`. No new server endpoints. Tier-5 `ProfileService` spec + tier-6 dialog spec (local vs. provisioned).
- [x] Phase 6 — `RemoteStorage.canChangePassword()` removed; `RemoteOperator.canChangePassword()` routes through capabilities. `legacyCanChangePasswordEndpoint_is404_afterPrd050Removal` pins it.
- [ ] **Follow-up audit** — dispatch-path bypass (non-admin clears own `authenticationSource` via `dispatch(UpdateEvent)`); same for `isAdmin`. Tracked separately; not blocking.

## Phase status (2026-05-28 re-open)

Landed in a coordinated rapla + dhbwrapla session 2026-05-28 (no prod traffic on spring-boot branch — clean break, no keep-alive shim).

- [x] **Phase 7** — Provisioning moved off resolve path.
  - [x] 7a. `RemoteLocaleController.locale` — single `checkAndGetUser` in try/catch (was two).
  - [x] 7b. `ExternalUserResolver.resolve` pure (User or `RaplaSecurityException`); `syncFromIdp` / `autoProvisionUser` / `applyConfiguredGroupsIfPresent` removed. New `claimsFor(jwt, provider) → IdentityClaims` for the exchange seam. Constructor takes `CachableStorageOperator` not `RaplaFacade`.
  - [x] 7c. `OAuthExchangeController.exchange` — decode `id_token` or JWT-shaped `access_token` via configured `JwtDecoder`, build claims via `claimsFor`, call `provisioner.provision`. Provisioning failures logged + swallowed; IdP token still returns. Opaque tokens (Google `access_token` without `openid`) skipped.
  - [ ] 7d. `ApiResolveSideEffectArchitectureTest` — deferred. Tier-2 `resolveDoesNotWriteEvenWhenClaimsDiffer` covers the invariant; CI-level sweep when the rule generalises.
- [x] **Phase 8** — Common provisioner core + plugin SPI.
  - [x] 8a. `IdentityClaims` record (`org.rapla.server.IdentityClaims` — server-only, used at auth seam).
  - [x] 8b. `UserProvisioner` + `DefaultUserProvisioner`. `@Bean @ConditionalOnMissingBean` in `ServerServiceConfig`. `operator` field protected for subclass reuse.
  - [x] 8c. `AuthenticationStore`: `initUser` deleted; `extractClaims → IdentityClaims` added. No keep-alive default.
  - [x] 8d. `JNDIAuthenticationStore.extractClaims` returns `sourceId = "ldap"`, `groupKeys = null` (provisioner falls back to `JNDIPlugin.USERGROUP_CONFIG`).
  - [x] 8e. `RaplaAuthentificationService.authenticate` — calls `authStore.authenticate` → `extractClaims` → `provisioner.provision`. Old find-or-create block + hardcoded `"ldap"` stamp at lines 158-162 deleted.
  - [x] 8f. dhbwrapla — `DhbwNtlmAuthStore.extractClaims` returns `sourceId = "dhbw-ntlm"` + `DhbwUserProvisioner extends DefaultUserProvisioner` (`@Component @ConditionalOnBean(DhbwNtlmAuthStore.class)`). DHBW policy: name never overwritten, email only when local empty, groups from `DhbwLdapGroupMapper`. Standort-required guard moved into `DhbwUserProvisioner.provision`. Existing users re-stamp on next login.
  - [ ] 8g. `canChangePassword()` removal — tracked in Phase 6 checkbox.

### Verified tests (2026-05-28)

- `ExternalUserResolverTest` 18/18.
- dhbwrapla aggregator 36/36 (10 skipped `db`/`e2e`).
- `mvn clean test-compile` clean for rapla-bom/core/client/server and dhbwrapla.
- rapla-app suite not run: parallel-session edits in `graphql/` (mtime 20:17–20:19, §7). `AuthenticationStoreInjectionTest` updated for new shape; test-compile clean.
