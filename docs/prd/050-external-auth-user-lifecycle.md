# PRD 050 — External-auth user lifecycle (passwords, names, emails, disconnect)

**Status:** done (Phases 1–4 + 6 landed 2026-05-21; Phase 5 cut; dispatch-path audit follow-up open)
**Date:** 2026-05-21
**Related:** PRD 036 (external IdP OAuth login), PRD 037 (native SAML / Shibboleth), PRD 049 (controller interface dedup — where the wire shape changes land), JNDI plugin (LDAP auth)

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

## Tests

- **Tier-2 (storage)** — XML and SQL round-trip a user with `authenticationSource = "keycloak:realm-vrz"`. Verify the column appears after `checkAndAdd`. Backfill test: existing rows with no column → load as null.
- **Tier-2 (entity)** — `UserImpl.setAuthenticationSource` + clone preserve the field.
- **Tier-3 (auth stamp)** — POST a fresh JWT to `/oauth2/token` (Keycloak issuer); the auto-provisioned user has `authenticationSource = "keycloak:..."`. Login again — marker not overwritten.
- **Tier-3 (block)** — for each of the four change methods, with target user marked `"keycloak:..."`:
  - Self-call → 403 with `externalIdpLabel` in the body.
  - Admin call → 403 (no fallback).
- **Tier-3 (disconnect)** — non-admin POST → 403. Admin POST → 200, target's `authenticationSource` is null, subsequent `changePassword` for target succeeds.
- **Tier-3 (capabilities)** — local user: all three booleans true, label null. Keycloak user: all false, label = `"Keycloak (realm-vrz)"`.

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
