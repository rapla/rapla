# PRD 036: External IdP OAuth 2.0 Login (Microsoft Entra ID + Google)

> **Update 2026-06-24 — legacy HMAC token path removed.** The `AuthController` /
> `TokenHandler` integration points referenced below no longer exist: `TokenHandler`,
> `SignedToken`/`ValidToken`, and `RemoteSessionImpl` were deleted. Inbound external-IdP
> JWT validation/dispatch lives entirely in `JwtConfig`/`IssuerAwareJwtDecoder` +
> `JwtUserResolver`/`ExternalUserResolver` (the `iss`-dispatch this PRD describes), not in
> `TokenHandler`. Mentions of `TokenHandler` below are historical.

**Status:** in-progress
**Date:** 2026-05-14

> **Progress (2026-05-21):** v1 (Microsoft + Google) and Phase 2.1 (Keycloak)
> landed, exercised end-to-end against DHBW Mosbach realm
> `dhbwmos-lehre`. Two design pivots from the original draft:
>
> 1. **Identity keyed on rapla username**, not per-provider
>    `external-id.<provider>` preference. Lookup: token's
>    `upn` → `preferred_username` → `email` claims, case-insensitively
>    against `user.getUsername()`, with email-against-`user.getEmail()`
>    as a tier-2 fallback. Auto-provision uses lowercased
>    upn/preferred_username/email as the username. The
>    `org.rapla.auth.external-id.<provider>` and `.provider`
>    preferences are no longer written or read. Reason: external-id
>    broke first-login for any user not provisioned via the IdP
>    (CSV-imported, hand-created, LDAP-migrated) and silently broke on
>    realm rotation. Tradeoff: IdP-side username rename produces an
>    orphaned rapla user that an admin renames manually — same operator
>    burden as the legacy LDAP path.
> 2. **SPA 401 handling pops a blocking dialog** showing the actual
>    server reason instead of silently rerouting to `/login`. The
>    interceptor tries `oauth.refreshToken()` once before showing the
>    dialog; `setupAutomaticSilentRefresh()` wired at boot.
>    Details in `docs/authentication.md` § "401 handling on the SPA".
>
> Phase 2.2 (Shibboleth-via-Keycloak docs) and **Phase 3 (multiple external
> OIDC providers via a registration map — added 2026-06-24, in progress)** are
> the open work.

## Goal

Let a rapla deployment delegate authentication to an **external OIDC
identity provider** instead of validating passwords against the local
user store. v1 ships two production-ready providers:

- **Microsoft Entra ID** — for the Microsoft 365 / DHBW / German
  university audience.
- **Google** (consumer + Workspace) — for Workspace-based deployments
  and as a low-friction option for evaluators.

A deployment can enable any combination of providers simultaneously
(embedded Spring AS, Microsoft, Google). Each is an independent config
block (`rapla.oauth.external.microsoft.*`,
`rapla.oauth.external.google.*`); enabling one doesn't affect others.

**Web (Angular) — user-facing IdP picker, configurable.** Discovery
emits a `providers[]` array + `picker` block. The Angular login is
driven by `picker.mode`:

| `picker.mode` | Behaviour |
|---|---|
| `auto` (default) | Show picker when ≥2 providers; auto-fire primary when only 1 |
| `always` | Always show, even with one provider |
| `never` | Auto-fire primary; ignore others (useful when other providers exist for API but web should be single-IdP) |

Each provider declares `display-name`, `icon` (well-known: `microsoft`,
`google`, `rapla`, or URL), and `order`. No per-deployment build
config — buttons/labels/order come from `application.yml`.
`web-picker-visible: false` hides a provider from the web picker while
keeping its token validation (useful e.g. keep rapla-local password for
API/Swing while only showing SSO on web).

**Swing — rapla embedded SAS only, no external IdP support.** Swing is
being deprecated in favour of the Angular SPA (PRD 026). Wiring
Microsoft/Google into Swing means a new picker dialog state,
browser-launch handling for Google's `access_type=offline`, more test
surface — all work that ages out when Swing is removed. Swing keeps
PRD 029 Phase 2 behaviour: probe discovery, auto-fire rapla embedded
SAS. The discovery endpoint's flat top-level fields always point at
the embedded SAS regardless of which providers are enabled, so Swing's
probe sees today's URLs unchanged. Deployments needing SSO from Swing
before removal can (a) federate rapla SAS to an upstream IdP via
Spring SAS's `oauth2Login` configurer, or (b) move users to the SPA.

The architecture is provider-pluggable: Entra and Google are the
shipped implementations; adding a third (Keycloak, Okta, Auth0) is a
small follow-up because provider-specific logic lives behind one
interface (`ExternalUserResolver`).

## Why this is needed

1. **Corporate SSO is the most-requested missing feature** at
   universities and DHBW. Today an admin provisions rapla-local users
   mirroring the corp directory — duplicate password policy, separate
   offboarding, no MFA.
2. **The plumbing is already in place.** `OAuthConfigController`
   exposes `authorize-url`, `token-url`, `jwks-url`, `userinfo-url`,
   `end-session-url`, `issuer` overrides (class Javadoc mentions
   "deployments that delegate auth to Keycloak / Auth0 / Okta / etc.").
   PRD 029 Phase 1 + PRD 031 unified the client view of refresh and
   logout. Missing: (a) wiring JWT decoder to validate externally-issued
   tokens, (b) mapping external claims to a rapla user, (c) setup recipe.
3. **Microsoft + Google cover the bulk of the addressable audience.**
   DHBW, most German universities, and many municipal deployments are
   M365 shops; smaller orgs and education deployments are on Google
   Workspace. Keycloak (PRD 029 Phase 2 placeholder) is a self-hosted
   intermediate step few deployments will run if they can point at
   Entra/Google directly. Shipping both means the abstraction
   (`ExternalUserResolver`, multi-issuer decoder, discovery re-pointing)
   is exercised by two real providers from day one.
4. **Same shape as PRD 029.** Configuration recipe + multi-issuer JWT
   decoder + provider-pluggable user-mapping. Discovery endpoint shape
   stays; Swing/Angular clients stay unchanged.

## Scope

### In scope

- **Multi-issuer JWT decoder.** `JwtConfig` validates tokens signed by
  rapla's own JWKS, Microsoft's
  (`https://login.microsoftonline.com/{tenant}/discovery/v2.0/keys`),
  or Google's (`https://www.googleapis.com/oauth2/v3/certs`). Selection
  per-token by `iss`. Any combination of {local, microsoft, google}
  can be enabled simultaneously.
- **Per-provider config blocks** in `application.yml`, each independently enable-able:
  ```yaml
  rapla:
    oauth:
      external:
        microsoft:
          enabled: false                 # default off
          tenant: ${RAPLA_OAUTH_MICROSOFT_TENANT:}   # GUID or domain;
                                                     # use 'common' for multi-tenant
          client-id: ${RAPLA_OAUTH_MICROSOFT_CLIENT_ID:}
          hosted-domain:                 # optional, restrict to a domain claim
          auto-provision: true            # LDAP-era precedent
          display-name: "Sign in with Microsoft"
          icon: microsoft                # well-known string or URL
          order: 10
          web-picker-visible: true
          # Endpoints below are derived from tenant by default; override for
          # sovereign-cloud Entra (china/govcloud).
          authorize-url:
          token-url:
          jwks-url:
          end-session-url:
          # Claim mapping (Entra defaults). external-id-claim is retained in
          # config for backwards-compat but inert post-2026-05-21 — the
          # resolver matches by username (upn → preferred_username → email)
          # against user.getUsername(), not by IdP stable id.
          username-claim: preferred_username
          email-claim: email
          external-id-claim: oid          # currently unused; see § Resolving the rapla User

        google:
          enabled: false                 # default off
          client-id: ${RAPLA_OAUTH_GOOGLE_CLIENT_ID:}
          hosted-domain:                 # optional, restrict to a Workspace
                                          # domain via the `hd` claim
          auto-provision: true            # narrow with hosted-domain for safety
          display-name: "Sign in with Google"
          icon: google
          order: 20
          web-picker-visible: true
          # Google endpoints are static — override only for testing.
          authorize-url: https://accounts.google.com/o/oauth2/v2/auth
          token-url:     https://oauth2.googleapis.com/token
          jwks-url:      https://www.googleapis.com/oauth2/v3/certs
          # Google has no proper RP-initiated OIDC logout. Leave blank —
          # client clears local session only.
          end-session-url:
          # Username and email both come from the `email` claim for Google
          # (no `upn`, no separate `preferred_username` for the username).
          # external-id-claim is retained for backwards-compat but inert —
          # the resolver matches by username, not by IdP stable id.
          username-claim: email
          email-claim: email
          external-id-claim: sub          # currently unused

      web:
        picker:
          mode: auto                     # auto | always | never
          primary: rapla                 # id of the auto-fire provider for
                                          # mode=auto (single) and mode=never
  ```
  All fields settable via `RAPLA_OAUTH_*` env vars. No `client-secret`:
  rapla stays a public PKCE client against both providers.
- **Discovery endpoint emits `providers[]` array.** Backwards-compatible
  with PRD 029: flat `authorizeUrl`/`tokenUrl`/`clientId`/`issuer`/`endSessionUrl`
  remain at the top level and **always reflect rapla embedded SAS**
  regardless of which external providers are enabled. Swing's
  discovery probe sees today's values; external IdPs are web-only. New
  `providers[]` lists every enabled provider (including rapla); each
  carries its own URLs, display metadata, `webPickerVisible` flag.
  New `picker` carries `{ mode, primary }`. Angular reads `providers` +
  `picker` and ignores the flat top-level OAuth fields.
- **External-user mapping.** Single `ExternalUserResolver` (no
  per-provider subclasses — algorithm identical; per-provider behaviour
  like the `email_verified` guard flows through `ProviderConfig`).
  Input: validated JWT + resolved provider. Output: rapla `User`,
  either looked up or auto-provisioned. **Identity is the rapla username.** Lookup:
  1. Match `user.getUsername()` (case-insensitive via `LocalCache.getUser`'s
     `equalsIgnoreCase` fallback) against the token's `upn` claim.
     `upn` first because AD-federated Keycloak emits both `upn` (the
     AD UserPrincipalName, e.g. `Pat.Test@adcorp.example.org`) and
     `preferred_username` (the bare login, `pat.test`); existing rapla
     deployments key on the UPN form.
  2. Same lookup against `preferred_username` (or provider's `usernameClaim`).
  3. Same lookup against `email`. Google requires `email_verified=true`;
     Entra often omits the claim so we trust email when absent (Entra
     controls the directory).
  4. Tier-2 fallback: `user.getEmail()` case-insensitively against
     token's `email`. Catches deployments where stored username is
     unrelated to the IdP form (CSV-imported bare names) but email matches.
  5. If still no match and `auto-provision: true`, create new rapla
     `User`. Username = lowercased(`upn` ?? `preferred_username` ??
     `email`). Display name from `name`/`given_name`/`family_name`.
     Groups from JNDI-plugin "default external user groups" preference
     if configured, otherwise `facade.newUser()` defaults. Default
     `auto-provision`: `true` — LDAP-era precedent.
  6. Else throw `RaplaSecurityException`; `SpringSecurityRemoteSession`
     propagates the message into the 401 response body so the SPA
     dialog (PRD 036 § "SPA 401 handling") shows the real reason.
- **Account merging across providers** is implicit: a human whose
  Microsoft `upn` and Google `email` both match the same rapla
  username resolves to the same `User` from either provider. No
  per-provider preference, no merge ceremony.
- **Provider-specific authorize-URL parameters (web only).** Standard
  PKCE params identical across providers, **but**:
  - **Google** requires `access_type=offline` and `prompt=consent` to
    issue a refresh token. `offline_access` scope doesn't apply.
  - **Entra** can take optional `domain_hint` to skip the
    "Work or school / Personal" picker.
  Encoded in discovery as per-provider `extraAuthorizeParams` map.
  Angular appends every key=value. Swing ignores (only uses rapla-SAS
  entry, whose map is always empty).
- **Angular login-screen picker UI.** `LoginPickerComponent` renders
  one button per `providers[]` entry (filtered to `webPickerVisible: true`),
  sorted by `order`. Each kicks off the `angular-oauth2-oidc` flow
  with that provider's URLs. `picker.mode` drives whether the picker
  renders at all. `OAuthService` is reconfigured before each
  `initLoginFlow()`.
- **`AuthController` / `TokenHandler` integration.** When inbound
  `Authorization: Bearer` carries an external-issuer JWT,
  `TokenHandler.validate` decodes via matching JWKS, dispatches to the
  right `ExternalUserResolver`, proceeds with the resulting rapla
  `User` — same downstream paths as local-issuer.
- **Setup recipes** in `docs/authentication.md`: one section per
  provider. Microsoft: register an App in Entra, find tenant/client
  IDs, whitelist redirects, scopes (`openid profile email offline_access`).
  Google: create OAuth 2.0 Client ID in Cloud Console, OAuth consent
  screen, "Web application" credential, scopes (`openid profile email`).
  ~80 + 80 lines admin copy.
- **Tests** — see below; tier-3 MockMvc or tier-1 unit, plus one
  tier-6 Angular picker test, plus one manual e2e per provider.

### Out of scope

- **Keycloak, Auth0, Okta, generic OIDC** — abstraction is generic
  enough that adding a resolver is a small follow-up. Microsoft +
  Google in v1 exercises the abstraction; later providers are config
  + 50-LOC resolver each. **Keycloak is in scope for Phase 2 below.**
- **Shibboleth / SAML 2.0.** v1 is OIDC-only. Native SAML in rapla
  would be substantial. Phase 2 brokers Shibboleth through Keycloak
  (Keycloak speaks SAML upstream, rapla speaks OIDC to Keycloak) —
  zero rapla code beyond the Keycloak provider. Native SAML deferred
  to a future PRD if Keycloak brokering proves insufficient.
- **Swing-side IdP picker.** Swing keeps auto-fire-primary for v1.
  Defer unless asked.
- **Token revocation pushed from the IdP.** When an admin disables a
  user in Entra/Google, rapla won't know until the user's refresh token
  expires (Entra default: 90 days idle; Google: 6 months idle for
  consumer accounts, indefinite for Workspace until revoked). Future
  PRD if needed.
- **Group / role sync.** v1 maps to single rapla `Role.USER`; admin
  promotion stays a rapla preference. Follow-up PRD (likely tied to
  PRD 002 multi-tenancy).
- **Microsoft Graph / Google Workspace API calls.** OIDC `id_token` /
  userinfo claims only.
- **Cross-provider account *merging* by user action.** Different
  username AND different email → two rapla `User` records. No in-app
  "link these accounts" flow; admin can manually rename to merge.
  Future PRD if there's demand.
- **Migrating existing rapla-local users.** They continue to log in
  with their rapla password until an admin disables the local account
  *or* sets `rapla.oauth.external.local-accounts-enabled: false`.
  Migration is "log in once via external — resolver matches existing
  rapla `User` by username (case-insensitive) or by email." No
  preference is written. If rapla username doesn't match what the IdP
  emits, admin renames the rapla user — same one-time task the legacy
  LDAP path always required. Recipe in `docs/authentication.md` §
  "Migrating existing rapla-local users".

## Architecture

### Multiple IdPs active, user picks on web

```
                  application.yml
   rapla.oauth.external.microsoft.enabled = true/false
   rapla.oauth.external.google.enabled    = true/false
   rapla.oauth.web.picker.mode            = auto/always/never
                          │
        ┌─────────────────┼────────────────┐
        │                 │                │
  ┌─────▼──────┐   ┌──────▼──────┐  ┌──────▼──────┐
  │ embedded   │   │  Microsoft  │  │   Google    │
  │ Spring SAS │   │   Entra ID  │  │             │
  │ (always on)│   │ (optional)  │  │  (optional) │
  └─────┬──────┘   └──────┬──────┘  └──────┬──────┘
        │                 │                │
        │                 └────────┬───────┘
        │                          │
        │ discovery flat top-level │  discovery providers[] + picker
        │ (rapla SAS only)         │  (all enabled providers)
        ▼                          ▼
  ┌─────────────────┐    ┌─────────────────────────────┐
  │  Swing client   │    │       Angular SPA           │
  │  (deprecated;   │    │  Renders picker buttons     │
  │   rapla SAS     │    │  per webPickerVisible       │
  │   only)         │    │  providers, ordered by      │
  │                 │    │  `order`. picker.mode       │
  │                 │    │  controls auto-fire vs.     │
  │                 │    │  always-show.                │
  └─────────────────┘    └─────────────────────────────┘
```

Clients only know about discovery. PKCE works identically across all
three. Token signed by issuing IdP; server's multi-issuer decoder
routes by `iss` — Swing-issued (rapla SAS) and Angular-issued (any)
flow through the same validation.

### Token validation: multi-issuer decoder

`JwtConfig` builds a map of `iss → JwtDecoder` at startup, one entry per enabled issuer:

```java
@Bean
JwtDecoder jwtDecoder(
    JWKSource<SecurityContext> embedded,
    ExternalProvidersProperties props)
{
    Map<String, JwtDecoder> decoders = new HashMap<>();
    decoders.put(LOCAL_ISSUER, NimbusJwtDecoder.withJwkSource(embedded).build());

    for (ProviderConfig p : props.enabledProviders()) {
        NimbusJwtDecoder dec = NimbusJwtDecoder
            .withJwkSetUri(p.jwksUrl())
            .jwsAlgorithm(SignatureAlgorithm.RS256)
            .build();
        dec.setJwtValidator(JwtValidators.createDefaultWithIssuer(p.issuer()));
        decoders.put(p.issuer(), dec);
    }
    return new IssuerAwareJwtDecoder(decoders);
}
```

`IssuerAwareJwtDecoder.decode(token)` peeks the unverified `iss`,
routes to the matching decoder, rejects unknown issuers. Each decoder
fully validates signature + standard claims so a typo'd issuer can't
sneak through.

### Why we keep the rapla `User` for externally-authed identities

External auth answers "who is this person?" — not "what can they do in
rapla?" The latter is rapla domain state that must live server-side:

- **Groups are `Category` entities, not strings.** `User.getGroupList()`
  returns real `Category` references the permission system walks.
  Even if a token carried `groups: [...]`, we'd still resolve names
  to `Category` entities — that mapping has to live somewhere.
- **Every domain entity references a `User`.** `Reservation.getOwner()`,
  `Allocatable.getLastChangedBy()`, audit trails, per-user preferences,
  filter state, calendar configs — all point at a rapla `User` UUID.
- **Neither provider reliably puts groups in tokens.** Entra has a
  `groups` claim with ~150-group cap (overflow → Graph URL), GUIDs not
  names. Google's OIDC carries no group memberships (needs Directory
  API + extra scopes + confidential client).

Architecture: every authenticated identity — local or external —
resolves to a rapla `User`. For external, the `User` is a "shadow"
record auto-created on first login. Token tells us who; rapla `User`
stores what they can do.

What differs for externally-authed `User`:

- **Password unused.** `User.password` stays empty; rapla never validates.
  Admin UI should disable the field (visual cue: provider-icon badge).
- **No server-side provider attribution.** 2026-05-21 refactor dropped
  the `user.preferences["org.rapla.auth.provider"]` write. SPA tracks
  its OWN active-provider key in `localStorage` for sign-out URL
  routing and display tweaks. If a future admin-UI needs "this user
  signs in via Microsoft", re-introduce the pref or derive from token
  `iss` at login — neither is wired today.
- **Group sync from token claims** — deferred. Would pull a configured
  claim, map via admin-maintained `entra-group-guid → rapla-category-id`,
  replace user's group list. Skipped from v1 because admin UX work and
  orthogonal to auth flow. v1 sets groups manually after auto-provision.

### Resolving the rapla User

`SpringSecurityRemoteSession.resolveJwtOrThrow(Jwt)` dispatches. If
the JWT's `iss` matches an enabled external provider, hands to
`ExternalUserResolver`; otherwise a local-issuer UUID lookup.
Resolver failures throw `RaplaSecurityException` with original
message — propagated unwrapped into the 401 body so SPA dialog shows
the actual cause:

```java
private User resolveJwtOrThrow(Jwt jwt) throws RaplaSecurityException {
    if (externalProviders != null && externalUserResolver != null) {
        String issuer = jwt.getClaimAsString("iss");
        ProviderConfig provider = externalProviders.byIssuer(issuer).orElse(null);
        if (provider != null) {
            try {
                return externalUserResolver.resolve(jwt, provider);
            } catch (RaplaException ex) {
                throw new RaplaSecurityException(ex.getMessage(), ex);
            }
        }
    }
    String subject = jwt.getSubject();
    if (subject == null) {
        throw new RaplaSecurityException("JWT has no subject and no matching external provider");
    }
    return operator.resolve(new ReferenceInfo<>(subject, User.class));
}
```

`ExternalUserResolver.resolve(jwt, provider)` runs the username-keyed
algorithm from § "External-user mapping". One resolver covers Entra,
Google, and Keycloak — per-provider behaviour (`email_verified` for
Google, hosted-domain for Entra/Keycloak, `usernameClaim`/`emailClaim`)
flows through `ProviderConfig`.

**Why no per-provider stable-id mapping (e.g. Entra `oid`, Google `sub`)?**
The original design (pre-2026-05-21) keyed identity on a per-provider
`external-id.<provider>` pref. Two failure modes bit DHBW pilot: (a)
first-login lookups missed any user not provisioned via the IdP
(CSV-imported, hand-created, LDAP-migrated) because they had no
preference set, and email-match fallback was fragile when the IdP's
emitted email differed from the stored one; (b) when a Keycloak realm
was reset/swapped, stored `sub` values became meaningless but prefs
lingered, silently blocking login. Username-as-identity removes both.
Cost: IdP-side username rename produces an orphaned rapla user — admin
renames manually, same as the legacy LDAP path.

### Discovery shape

Top-level fields **always reflect rapla embedded SAS** (Swing's
target). `providers[]` carries the per-provider config Angular renders
the picker from.

```json
{
  "enabled": true,
  "clientId": "rapla-client",
  "issuer":         "http://localhost:8051",
  "authorizeUrl":   "http://localhost:8051/oauth2/authorize",
  "tokenUrl":       "http://localhost:8051/oauth2/token",
  "logoutUrl":      "http://localhost:8051/connect/logout",
  "jwksUrl":        "http://localhost:8051/oauth2/jwks",
  "userinfoUrl":    "http://localhost:8051/userinfo",
  "endSessionUrl":  "http://localhost:8051/connect/logout",
  "scopes":         ["openid", "profile", "email", "offline_access"],

  "picker": {
    "mode": "auto",
    "primary": "microsoft"
  },
  "providers": [
    {
      "id": "rapla",
      "displayName": "Sign in with rapla password",
      "icon": "rapla",
      "order": 0,
      "webPickerVisible": false,
      "clientId": "rapla-client",
      "issuer":         "http://localhost:8051",
      "authorizeUrl":   "http://localhost:8051/oauth2/authorize",
      "tokenUrl":       "http://localhost:8051/oauth2/token",
      "endSessionUrl":  "http://localhost:8051/connect/logout",
      "jwksUrl":        "http://localhost:8051/oauth2/jwks",
      "scopes":         ["openid","profile","email","offline_access"],
      "extraAuthorizeParams": {}
    },
    {
      "id": "microsoft",
      "displayName": "Sign in with Microsoft",
      "icon": "microsoft",
      "order": 10,
      "webPickerVisible": true,
      "clientId": "<entra-app-client-id>",
      "issuer":         "https://login.microsoftonline.com/{tenant}/v2.0",
      "authorizeUrl":   "https://login.microsoftonline.com/{tenant}/oauth2/v2.0/authorize",
      "tokenUrl":       "https://login.microsoftonline.com/{tenant}/oauth2/v2.0/token",
      "endSessionUrl":  "https://login.microsoftonline.com/{tenant}/oauth2/v2.0/logout",
      "jwksUrl":        "https://login.microsoftonline.com/{tenant}/discovery/v2.0/keys",
      "scopes":         ["openid","profile","email","offline_access"],
      "extraAuthorizeParams": {}
    },
    {
      "id": "google",
      "displayName": "Sign in with Google",
      "icon": "google",
      "order": 20,
      "webPickerVisible": true,
      "clientId": "<google-oauth-client-id>",
      "issuer":         "https://accounts.google.com",
      "authorizeUrl":   "https://accounts.google.com/o/oauth2/v2/auth",
      "tokenUrl":       "https://oauth2.googleapis.com/token",
      "endSessionUrl":  "",
      "jwksUrl":        "https://www.googleapis.com/oauth2/v3/certs",
      "scopes":         ["openid","profile","email"],
      "extraAuthorizeParams": {
        "access_type": "offline",
        "prompt": "consent"
      }
    }
  ]
}
```

Flat top-level fields are the **primary provider's** values, kept for
Swing backwards-compat. Angular reads `providers[]` + `picker`.

### Refresh path

Entra (`/oauth2/v2.0/token`), Google (`/token`), Keycloak
(`/protocol/openid-connect/token`) all accept `grant_type=refresh_token`
and return fresh tokens. SPA POSTs to the **provider's `tokenUrl`
from discovery**, which for confidential clients routes via the BFF
(`/api/auth/oauth/exchange/{providerId}`) so the server-held
`client_secret` is added before forwarding — same shape as initial
code-exchange. PRD 041 removed standalone `/api/auth/refresh`; no
rapla-specific refresh route.

Refresh-token rotation policy is the provider's, not rapla's: the
single-token-per-user logic rapla applies to its own embedded-SAS
refreshes doesn't apply when external is active. Rapla can't
force-revoke an externally-issued refresh token; revocation happens
IdP-side and rapla learns at next refresh-fails → reauth cycle.

**SPA refresh wiring** (`rapla-angular`):

- `app.config.ts` calls `oauth.setupAutomaticSilentRefresh({}, 'access_token')`
  at boot when a valid token is present — most users never see a 401
  from token expiry.
- `auth.interceptor.ts` runs 401-with-Bearer recovery: try
  `oauth.refreshToken()` once, replay on success, open
  `AuthErrorDialogComponent` on failure (with server's `error_description`
  from body or `WWW-Authenticate`). Dialog blocks until acknowledged;
  closing bumps `oauthFailures` so LoginComponent's auto-fire guard
  prevents an immediate re-loop.

**Keycloak refresh-token nuance.** Keycloak issues refresh tokens
**by default without `offline_access`**, but those are SSO-session-bound
and expire when the realm's SSO Session Max elapses (typically ~8 h
sliding). True offline tokens (months-long, survive logout) require
`offline_access` AND the client must allow it (Keycloak rejects with
`error=invalid_scope` otherwise — login fails entirely, no graceful
degradation). Rapla defaults to `["openid","profile","email"]` —
SSO-session refresh is enough for interactive SPA use. See
`docs/authentication.md` § "Refresh tokens and `offline_access`".

**Google refresh quirk.** Google issues a refresh token *only on the
first consent* unless `prompt=consent` is passed every time. Google's
discovery entry carries `extraAuthorizeParams: { access_type: offline, prompt: consent }`.

### Logout

Entra has a proper OIDC RP-initiated logout endpoint:
`https://login.microsoftonline.com/{tenant}/oauth2/v2.0/logout`. Both
Swing and Angular redirect to it; `id_token_hint` is recommended
(plumbed by Swing per PRD 029 Phase 2 2026-05-13 fix).

Google **has no proper RP-initiated OIDC logout**. The
`https://accounts.google.com/Logout` URL logs the user out of *every*
Google product — almost never desired. When active provider is Google:

1. Clear local rapla tokens (TokenStore + memory).
2. Optionally POST to `https://oauth2.googleapis.com/revoke` to revoke
   server-side (configurable via
   `rapla.oauth.external.google.revoke-on-logout: true`; default off —
   users typically expect "log out of rapla", not "uncouple the OAuth grant").
3. Do **not** open a browser tab. The user's Google session stays untouched.

Rapla's local remember-me cookie + `/connect/logout` flow is bypassed
when external IdP is active: no rapla session to clear.

### Why configurable multi-provider is the right shape

A flag-based "single external IdP" design would be simpler internally
but worse for deployments: most universities have *both* Microsoft and
Google identities for different user groups (staff vs students;
permanent vs guests); a migration from embedded SAS to "Microsoft only"
has no rollback (config typo = everyone locked out). Added complexity
over single-external-IdP is bounded — `Map<iss, JwtDecoder>` instead
of a single decoder, a registry of resolvers, a `providers[]` array
— all linear in provider count, all exercised by two shipped providers from day one.

## Plan

1. **Server: provider config + multi-issuer JWT decoder.**
   - Add `ExternalProvidersProperties` (`@ConfigurationProperties`,
     `rapla-server`, `org.rapla.server.spring`) with nested
     `Microsoft` and `Google` blocks, each with its own `enabled` flag.
   - Add `IssuerAwareJwtDecoder` keyed by `iss`.
   - Wire both in `JwtConfig`. When no external is enabled, behaviour
     is byte-identical to today.

2. **Server: `ExternalUserResolver` (single class — landed).**
   - Plain Java in `rapla-server`; takes validated `Jwt` + `ProviderConfig`,
     returns rapla `User`. No subclasses — algorithm identical;
     per-provider behaviour flows through `ProviderConfig`.
   - Lookup order: `upn` → `preferred_username` → `email` against
     `user.getUsername()` (case-insensitive); then email-against-email;
     auto-provision on miss.
   - **No state in user preferences.** 2026-05-21 refactor dropped
     the per-provider prefs.
   - Tier-2 tests in `rapla-server/.../oauth/external/ExternalUserResolverTest`
     (16 tests covering each lookup branch, lowercase normalization,
     hosted-domain enforcement, auto-provision fallback chain,
     returning-user-via-username).

3. **Server: `TokenHandler` integration.** Dispatch on `iss`, route
   through resolver registry. `/api/auth/logout` becomes no-op when
   resolved provider is external. Tier-3 MockMvc for both providers.

4. **Server: discovery endpoint emits `providers[]` + `picker`.**
   - Extend `OAuthConfigController` to read `ExternalProvidersProperties`.
   - Flat top-level fields **always reflect rapla embedded SAS** —
     Swing's discovery probe stays unchanged.
   - `picker.primary` defaults to `rapla`.
   - Tier-3 MockMvc: each (microsoft-enabled, google-enabled,
     picker.mode) combination emits right shape; top-level stays
     rapla-SAS across all combinations.

5. **Server: provider-specific authorize-URL parameters.** Each
   `ProviderConfig` carries `extraAuthorizeParams` map. Google
   defaults: `{ access_type: offline, prompt: consent }`. Tier-1 test:
   serialized correctly.

6. **Swing client: no changes.** External IdPs are web-only. Existing
   PRD 029 Phase 2 discovery probe + auto-fire-rapla-SAS flow stays.
   Confirm by running existing Swing OAuth tests against a server
   config with Microsoft + Google enabled.

7. **Angular client: `LoginPickerComponent` + multi-config OAuth.**
   - Reads `providers[]` + `picker`; renders one button per
     `webPickerVisible:true` entry sorted by `order`.
   - On click: reconfigure `OAuthService` with the chosen provider's
     endpoints + `extraAuthorizeParams` and call `initLoginFlow()`.
   - `picker.mode = auto` auto-fires when single provider; `always`
     always shows; `never` auto-fires `picker.primary`.
   - Tier-6 Angular component test.

8. **Documentation.** New `docs/authentication.md` sections:
   - "External IdP — Microsoft Entra ID": Entra App Registration
     (tenant, client ID, redirects, scopes `openid profile email offline_access`).
   - "External IdP — Google": Cloud Console setup (project, consent
     screen, "Web application" credential, scopes `openid profile email`).
   - "Multi-provider deployments": `web.picker` knobs, login screen
     with three buttons, hidden-from-web providers.
   - Cross-link from PRD 029 Phase 2 and PRD 031.

9. **Manual smoke:**
   - Entra test tenant: Angular login → main view.
   - Google OAuth test client: Angular login → main view; verify
     refresh after token expiry uses stored refresh (no second consent).
   - Multi-provider: Angular picker shows three buttons in right order;
     each ends up signed in.
   - Swing regression: with all three providers enabled, Swing still
     launches rapla SAS flow unchanged. Tagged `e2e`, manual.

10. **PRD close:** when all phases done, `git mv` to `docs/prd/done/`.
    Update PRD 029 Phase 2 OQ §5 — `localAccountsEnabled=false`
    becomes meaningful once external is configured.

## Tests

| Tier | Test | What it locks in |
|------|------|------------------|
| 1 | `IssuerAwareJwtDecoderTest` | Routes by `iss`, rejects unknown issuer, rejects malformed JWT, coexists with 3 issuers active |
| 2 | `ExternalUserResolverTest` (real `FacadeTestSupport`) | Each lookup branch (`upn` / `preferred_username` / `email` against username, then email-against-email); case-insensitive; `auto-provision` chain; lowercase normalization on store; `email_verified=false` blocks email branch for Google; `hosted-domain` (`hd`) check; returning-user without stored external-id pref |
| 1 | `ExternalProvidersPropertiesTest` | Env-var binding, default values, `enabledProviders()` reflects per-provider `enabled` flags |
| 3 | `EntraTokenIntegrationTest` (MockMvc + stubbed Entra JWKS) | Entra Bearer unlocks rapla REST, resolved user correct, scoped to right `oid` |
| 3 | `GoogleTokenIntegrationTest` (MockMvc + stubbed Google JWKS) | Google Bearer unlocks rapla REST, resolved user correct, scoped to right `sub` |
| 3 | `MultiProviderCoexistenceIntegrationTest` | With local + Microsoft + Google all enabled, all three token kinds validate against the same decoder |
| 3 | `DiscoveryWithoutExternalProvidersTest` | Default config: discovery byte-identical to today; `providers[]` contains only rapla (regression guard) |
| 3 | `DiscoveryWithMicrosoftOnlyTest` | Microsoft enabled: `providers[]` has rapla + microsoft; primary=microsoft; picker.mode=auto; flat top-level points at Entra |
| 3 | `DiscoveryWithGoogleOnlyTest` | Symmetric for Google; `extraAuthorizeParams` carries `{access_type, prompt}` |
| 3 | `DiscoveryWithAllThreeTest` | All three in `providers[]`, ordered by `order`; `webPickerVisible` honoured |
| 3 | `DiscoveryPickerModesTest` | Each `picker.mode` value round-trips correctly |
| 3 | `ExternalTokenRefreshTest` | `oauth.refreshToken()` against stubbed Entra/Google/Keycloak token endpoints succeeds via discovery-emitted `tokenUrl` (BFF-routed for confidential, direct for public PKCE) |
| 3 | `ApiKeyWorksWithExternalProviderConfiguredTest` | Rapla-issued API key (PRD 031) keeps working when external is enabled |
| 2 | `SwingDiscoveryIgnoresExternalProvidersTest` | With Microsoft + Google enabled, Swing's probe + auto-fire-rapla-SAS path is unchanged |
| 6 | `LoginPickerComponent.spec.ts` (Angular Vitest + TestBed) | `picker.mode=auto + 1 provider` → no picker, auto-fire; `auto + 2 providers` → picker visible; `always` → always picker; `never` → no picker; click reconfigures `OAuthService` and calls `initLoginFlow()` |
| 5 | `OAuthProviderConfig.spec.ts` (Angular Vitest, no TestBed) | Sorting by `order`, filtering by `webPickerVisible`, icon → image mapping |
| e2e (manual) | Real Entra tenant, Angular login | Full stack via Microsoft (web-only) |
| e2e (manual) | Real Google client, Angular login | Full stack via Google, including refresh after token expiry |
| e2e (manual) | Both providers enabled, Angular picker | Three-button picker renders, each button signs in |
| e2e (manual) | Both providers enabled, Swing login | Swing ignores them and signs in via rapla SAS unchanged (regression guard) |

## Phase 2: Keycloak + Shibboleth federation

The v1 architecture (multi-issuer decoder, provider-pluggable resolver,
BFF token-exchange, picker UI) was designed provider-agnostic. Adding
Keycloak is a small extension of the same shape. Shibboleth (SAML 2.0)
won't fit OIDC framework — standard deployment is brokering through
Keycloak (Keycloak speaks SAML to upstream Shibboleth IdP, rapla
speaks OIDC to Keycloak). That keeps SAML out of rapla entirely.

### 2.1 Keycloak as a first-class provider

Keycloak is open-source OIDC with realm-based isolation (one server
hosts multiple "realms", each a separate IdP). One rapla deployment
integrates with one realm — same model as Microsoft/Google.

#### In scope

- **`Keycloak` config block** in `ExternalProvidersProperties`,
  mirroring the Microsoft/Google block shape:
  ```yaml
  rapla:
    oauth:
      external:
        keycloak:
          enabled: false
          base-url: https://keycloak.example.com   # the Keycloak server's public URL
          realm: rapla                              # the realm name
          client-id: rapla-app
          client-secret: <optional — set for "confidential access type">
          hosted-domain:                            # optional email-domain guard
          auto-provision: true
          display-name: "Sign in with university SSO"
          icon: keycloak
          order: 15
          web-picker-visible: true
          username-claim: preferred_username
          email-claim: email
          external-id-claim: sub          # currently unused; resolver matches by username
  ```
- **URL derivation** in `Keycloak.toProviderConfig()`:
  ```
  issuer       = {base-url}/realms/{realm}
  authorizeUrl = {base-url}/realms/{realm}/protocol/openid-connect/auth
  tokenUrl     = {base-url}/realms/{realm}/protocol/openid-connect/token
  jwksUrl      = {base-url}/realms/{realm}/protocol/openid-connect/certs
  endSessionUrl = {base-url}/realms/{realm}/protocol/openid-connect/logout
  ```
- **New `ExternalProviderId.KEYCLOAK`** + populate `enabledProviders()`.
- **`ExternalUserResolver`** — no new resolver class. Keycloak's
  default claims (`sub`, `preferred_username`, `email`, `name`,
  `given_name`, `family_name`) are exactly what the generic resolver
  handles. Pass `ProviderConfig` with Keycloak's claim names (OIDC defaults).
- **Confidential vs public client**: public (PKCE, no secret) → direct
  route; confidential → BFF route. Default for SPAs is public.
  Detection: `clientSecret` empty → direct, set → BFF.
- **OIDC RP-initiated logout** at
  `{base-url}/realms/{realm}/protocol/openid-connect/logout` — works
  out of the box with existing `AuthService.signOut()`.
- **Multi-tenant**: out of scope. A Keycloak realm is already a tenant;
  multiple realms behind one rapla is a future PRD.

#### Out of scope (Phase 2)

- **Group / role sync** from `realm_access.roles`. Stays deferred.
- **Keycloak SPI / Admin REST API integration** — rapla only consumes OIDC.
- **Multi-realm rapla** — one realm per deployment.

#### Implementation tasks

1. ✅ **Server: Keycloak provider class.** `ExternalProvidersProperties.Keycloak`
   nested POJO; `toProviderConfig()` derives every OIDC URL from
   `base-url + realm`; validation requires `base-url`, `realm`, `client-id` when enabled.
2. ✅ **Server: `ExternalProviderId.KEYCLOAK` enum entry.**
3. ✅ **Server: `enabledProviders()` updated** to include the Keycloak
   block. No `JwtConfig` / `OAuthConfigController` change needed —
   both iterate `enabledProviders()` generically, so Keycloak slots
   into the multi-issuer decoder + discovery `providers[]` automatically.
4. **Tests:**
   - ✅ Tier-1 `ExternalProvidersPropertiesKeycloakTest` — URL
     derivation, trailing-slash strip, required-field validation,
     default OIDC claims, `enabledProviders()` inclusion.
   - ✅ Tier-3 `OAuthConfigControllerKeycloakTest` — Keycloak alone:
     discovery emits rapla + keycloak `providers[]`, URLs derived
     from `base-url + realm`, top-level stays rapla SAS, no
     `clientSecret` on the wire.
   - ⏳ `KeycloakUserResolverTest` not added — Keycloak uses standard
     OIDC claims so the generic `ExternalUserResolver` (already
     covered) handles it with no new code.
   - ⏳ `DiscoveryWithAllFourTest`, `KeycloakTokenIntegrationTest` —
     deferred; not blocking local-dev use case.
5. ✅ **Angular: login picker icon for Keycloak.** `keycloak` →
   `shield` Material glyph in `LoginComponent.iconNameFor()`.
6. ✅ **Setup recipe** — `docs/authentication.md` "External IdP —
   Keycloak" section + `tools/keycloak/README.md`.
7. ⏳ **Manual e2e**: local Keycloak (`tools/keycloak/keycloak.sh start`)
   — Angular login → main view; verify `Authorization: Bearer
   <keycloak-id-token>` validates server-side.

**Estimated total:** ~400 LOC server + ~50 LOC test setup + ~100 lines
docs. Smaller than Microsoft+Google because the framework is in place.

### 2.2 Shibboleth via Keycloak SAML brokering

Shibboleth is SAML 2.0. The academic federation pattern (DFN-AAI,
eduGAIN, InCommon) is:

```
Browser → rapla SPA
        → Keycloak (acting as SAML SP)
        → Shibboleth IdP (the institution's existing SAML auth)
        → SAML assertion back to Keycloak
        → Keycloak mints an OIDC token from the SAML attributes
        → Token flows back to rapla
```

For rapla this is **transparent — rapla only sees OIDC between itself
and Keycloak**. SAML lives entirely inside Keycloak. Zero rapla code
beyond Phase 2.1.

#### In scope

- **Documentation recipe** in `docs/authentication.md`:
  - Configure Keycloak as a SAML 2.0 SP in your Shibboleth IdP's metadata.
  - Keycloak admin: Realm settings → Identity providers → Add SAML 2.0
    → IdP entity ID, SSO service URL, signing certificate.
  - SAML attribute mappers: `eduPersonPrincipalName` → `preferred_username`,
    `mail` → `email`, etc.
  - Federate with eduGAIN / DFN-AAI by importing federation metadata aggregate.
- **No rapla code** beyond Phase 2.1.
- **Recipe references:**
  - Keycloak: <https://www.keycloak.org/docs/latest/server_admin/#_saml>
  - DFN-AAI: <https://www.aai.dfn.de/>
  - eduGAIN: <https://edugain.org/>

#### Out of scope

- **Native SAML in rapla.** Would require Spring Security SAML2 SP,
  metadata management, message signing, assertion consumer service,
  single logout, attribute mapping (~1500–2000 LOC + tests). Deferred
  indefinitely — Keycloak brokering covers 100% of realistic HE/academic
  deployments. Open as future PRD only with a strong reason.
- **Discovery service / WAYF.** Multiple Shibboleth IdPs → configure
  in Keycloak; Keycloak shows its built-in IdP discovery on its login
  page. Rapla sees a single Keycloak entry.

#### Implementation tasks

1. **Verify Phase 2.1 Keycloak provider works.**
2. **Add "Federate with Shibboleth via Keycloak" section** to
   `docs/authentication.md` — Keycloak admin recipe + typical attribute
   mapping (`eduPersonPrincipalName` / `mail` / `cn` → OIDC claims) +
   pointers to upstream Shibboleth metadata.
3. **Manual e2e** against a real Shibboleth IdP if available (DFN-AAI
   Test, eduGAIN sandbox, or local). Tagged `e2e`, manual.

**Estimated total:** ~100 lines docs. Zero rapla code beyond Phase 2.1.

### 2.3 Sequencing

1. **Land Phase 2.1 (Keycloak provider)** as follow-up PR to PRD 036.
2. **Add Shibboleth-via-Keycloak docs** in same PR or small follow-up.
3. If a real deployment needs native SAML, open a new PRD then —
   separate scope, not folded into PRD 036.

## Phase 3: Multiple external OIDC providers (registration map)

> **Added 2026-06-24.** Phase 2.1 shipped Keycloak as a single fixed slot
> (`rapla.oauth.external.keycloak`). That conflated *provider type* with
> *registration identity*: a deployment can run **one** Keycloak, not two
> (e.g. DHBW Mosbach **and** a second realm), and an arbitrary key like
> `rapla.oauth.external.dhbw` binds to nothing and is silently ignored — the
> bug that surfaced 2026-06-24 (a `dhbw` SSO button silently absent from the
> picker). Spring Security's native model is a **map keyed by an arbitrary
> `registrationId`** supporting N providers of any type; rapla discarded that
> generality when it built its own `InMemoryClientRegistrationRepository` from
> three fixed fields. Phase 3 restores the map while keeping rapla's value-adds
> (discovery-from-base-url+realm, BFF token exchange, identity-only brokering,
> picker metadata, claim mapping).

### Shape

`rapla.oauth.external` becomes a `Map<String, ProviderDef>` (key =
`registrationId`). `ProviderDef` is a single concrete class (Spring binds
`Map<String, ConcreteClass>` natively — no polymorphic binding) holding the
**union** of the former three inner classes' fields plus a `type`
discriminator (`microsoft | google | keycloak`).
`ProviderDef.toProviderConfig(registrationId)` switches on `type` to apply the
per-type derivation + defaults the three former `toProviderConfig()` methods
did (Entra multi-tenant issuer pattern; Google hardcoded endpoints +
`extraAuthorizeParams` + `revoke-on-logout`; Keycloak `base-url`+`realm`
derivation).

```yaml
rapla:
  oauth:
    external:
      keycloak:                 # registrationId; type inferred from key
        type: keycloak
        base-url: https://login.mosbach.dhbw.de
        realm: dhbwmos-lehre
      dhbw:                     # second Keycloak, parallel — needs explicit type
        type: keycloak
        base-url: https://keycloak.dhbw.de
        realm: rapla
```

Each entry yields its own `/login/oauth2/code/{registrationId}` callback.

### `id` vs `type` split

`ProviderConfig.id()` was the enum string (`"keycloak"`). After Phase 3:
- `id()` = **registrationId** (map key) — callback path, BFF exchange route
  (`/api/auth/oauth/exchange/{id}`), issuer-aware JWT decoder cache, picker id,
  logging. Nearly every `p.id()` site already meant this → unchanged.
- `type()` (alias of `provider()`, returns `ExternalProviderId`, now documented
  as the provider *type*) — used only where behaviour is genuinely type-specific.

The PRD-072 DHBW legacy callback turned out **not** to be type-specific: it is a
property of one *specific registration* (the IdP whose realm can't register the
conformant URI), so it became a **per-provider `legacy-callback: true` flag**
(see D-3.4), not a `type == KEYCLOAK` check. The global
`rapla.oauth.web.dhbw-legacy-callback` flag and `LegacyKeycloakCallbackBridgeFilter`
were replaced by the per-provider flag + the renamed, dynamically-targeted
`LegacyAppCallbackBridgeFilter`.

### Back-compat: type inferred from key, fail-fast otherwise

A map entry omitting `type:` infers it from the key when the key is a known
type name (`microsoft`/`google`/`keycloak`) — so the legacy fixed-key configs
bind unchanged. An arbitrary key (`dhbw`, `keycloak-2`) **requires** explicit
`type:`. An `enabled` entry whose type can be neither parsed nor inferred fails
fast at startup with a clear message — never silently dropped (that was the
original bug; a missing SSO button is a confusing outage, not a safe default).

### Implementation tasks — shipped 2026-06-24

- [x] `ProviderDef` (new) — union fields + `type`; `toProviderConfig(id)` switch.
- [x] `ExternalProvidersProperties` → `Map<String, ProviderDef>` (prefix now
      `rapla.oauth`, field `external`); `enabledProviders()` iterates the map;
      type inference + fail-fast; `byId`/`byIssuer` signatures unchanged.
- [x] `ExternalProviderId` — repurposed as the type enum (values unchanged;
      `parse()` helper for inference).
- [x] `ProviderConfig` — carry `registrationId` distinct from `type`;
      `id()` → registrationId, `type()` → enum.
- [x] `RaplaClientRegistrationConfig` — legacy callback keyed on `type()`.
- [x] Recompile — the `enabledProviders()`/`p.id()` consumers
      (`ExternalUserResolver`, `ExternalIdTokenVerifier`, `OAuthExchangeController`,
      `OAuthConfigController`, `JwtConfig`, `SecurityConfig`, `LoginPageController`)
      needed no change beyond recompile (their `p.id()` already meant registrationId).
- [x] Tier-2 test `ExternalProvidersPropertiesKeycloakTest` (13 cases): two
      keycloak entries → distinct ids + derived issuers; type-inference;
      fail-fast on unknown type. 5 OAuth test classes migrated to the map API,
      all 45 green.
- [x] Migrated in-repo YAMLs (vanilla `application-local.yml` — `application.yml`
      has no external block; dhbwrapla `local/application.yml` gains the second
      Keycloak `dhbw`, plus `docs/application-{web,test}.yml`) — explicit `type:`
      on every entry.

**Verified live (dhbw dev server):** `keycloak` (Mosbach) + `dhbw` (local
`localhost:8080`) both in `GET /api/auth/oauth/config` with distinct ids and
callbacks; CSP `connect-src` carries both issuer hosts. Phase 3 goal met.

### Phase 3 goal

With `keycloak` (Mosbach) + `dhbw` (local) both enabled,
`curl localhost:8051/api/auth/oauth/config` lists **both** with distinct `id`s,
and the CSP `connect-src` contains both issuer hosts.

### Phase 3 decisions locked

- **D-3.1 — flat `ProviderDef` + `type`, not polymorphic binding.** Spring binds
  `Map<String, ConcreteClass>` natively; a `type`-keyed switch reproduces the
  three former methods. A sealed hierarchy with a custom config `Converter` would
  be more "typed" but Spring config has no first-class polymorphic-map support —
  not worth the machinery.
- **D-3.2 — `id()` = registrationId, `type()` = enum.** The 2026-06-24 bug (a
  `dhbw` key silently ignored because only `keycloak` was a valid field) proved
  the conflation is harmful; splitting them makes arbitrary registrationIds
  first-class and reduces type-coupled sites to one (the legacy callback).
- **D-3.3 — type inferred from key for back-compat; fail-fast otherwise.**
  Silent drop is forbidden.
- **D-3.4 — legacy callback is a per-provider flag, not global, not type-gated.**
  `rapla.oauth.external.<id>.legacy-callback: true` replaces the global
  `rapla.oauth.web.dhbw-legacy-callback`. The bridge is inherently about one
  specific registration (the IdP whose realm whitelists only `/app/auth/callback`),
  and with multiple Keycloaks a `type == KEYCLOAK` gate would wrongly route the
  *other* Keycloak through the bridge too. `LegacyKeycloakCallbackBridgeFilter`
  (hardcoded `/login/oauth2/code/keycloak`) → `LegacyAppCallbackBridgeFilter`
  (target `registrationId` injected from the single provider that sets the flag).
  At most one provider may set it — the `/app/auth/callback` path is singular.
  Verified live 2026-06-25: `keycloak` (Mosbach, flag) sends `/app/auth/callback`,
  `dhbw` (local, no flag) sends `/login/oauth2/code/dhbw`.

## Open Questions

1. **Swing SSO during deprecation window.** Resolved: not in scope.
   External IdPs are web-only. Deployments needing Swing SSO can (a)
   federate rapla SAS to an upstream IdP via Spring SAS's `oauth2Login`
   (rapla SAS becomes RP to Entra/Google, Swing still talks to rapla
   SAS), or (b) move users to the SPA.

2. ✅ **Auto-provisioning default per provider.** *Resolved 2026-05-14.*
   Defaults **on** for both, matching LDAP precedent
   (`RaplaAuthentificationService.authenticate()` auto-creates on
   successful external auth — no opt-in in the LDAP path). Entra
   single-tenant (default) scopes to deployment's directory. Google:
   document that `hosted-domain` is the practical scope guard — without
   it, every verified Google account becomes a rapla user, so
   deployments without a Workspace should explicitly set
   `auto-provision: false`. Default groups: shared with LDAP via
   `JNDIPlugin.USERGROUP_CONFIG` system preference.

3. **Cross-provider account collisions.** Resolved by username-as-identity
   (2026-05-21): same human via Microsoft *and* Google resolves to the
   same rapla `User` iff stored username matches one of the token's
   username-bearing claims from either provider (usually the case —
   `alice@example.org` is identical in both). Email fallback still
   converges if email matches. When IdPs disagree on both username
   AND email, two separate users get auto-provisioned — admin can
   rename one to merge. Previous "silently merge by email-with-different-usernames"
   risk is gone; new failure mode is "silently DON'T merge when IdPs
   disagree" — safer default.

4. **Single-tenant vs multi-tenant Entra.** Single-tenant is right
   default — restricts logins to deployment's own Entra tenant.
   `tenant` config takes GUID/domain (single) or `common` (multi).
   Sample config uses single-tenant placeholder.

5. **Google `hosted-domain` enforcement.** `hd` claim tells which
   Workspace domain the user signed in from. Enforcing
   `hd == configured-domain` prevents personal `@gmail.com` accounts
   into a Workspace-only deployment. Default: enforce when set, allow
   any when blank. The `hd` query param isn't signed, so server-side
   check is mandatory.

6. **What happens to existing rapla-local users when external is on?**
   Keep logging in via legacy password form unless
   `rapla.oauth.local-accounts-enabled: false`. With external + local
   both on, Angular picker shows "Sign in with rapla password"
   alongside SSO buttons. Configurable via the flag PRD 029 OQ §5 had.

7. **`oid` vs `sub` for Entra; `sub` for Google.** Microsoft documents
   `oid` as stable per (tenant, user) and `sub` as pairwise pseudonym
   varying per-application — we want `oid`. Google's `sub` is stable
   per (account, OAuth client) — what we want. Document the asymmetry.

8. **API keys + external providers.** API keys (PRD 031) are
   rapla-issued bearers in user prefs. They work today against local
   decoder. With multi-issuer, API keys keep working — locally signed,
   carry local `iss`. **Answer**: yes, no extra work, lock in with
   `ApiKeyWorksWithExternalProviderConfiguredTest`.

9. **Scopes.** Entra: `openid profile email offline_access`
   (`offline_access` → refresh token). Google: `openid profile email`
   + `access_type=offline` as authorize param (no `offline_access`
   scope). Defaults in per-provider block; admins shouldn't usually override.

10. **Confidential vs public client.** Both Entra and Google support
    public PKCE. Stay public. Confidential would be a different PRD
    with its own threat model.

11. **`post_logout_redirect_uri` for Entra.** Yes — without it Entra
    shows generic "you're signed out"; with it, user lands back on
    rapla SPA. Configurable via
    `rapla.oauth.external.microsoft.post-logout-redirect-uri`; default
    to deployment's public base URL. Entra requires the URI to be
    pre-registered. Cover in the setup recipe.

12. **Token revocation on Google logout.** Default off — typical user
    expectation of "log out of rapla" is to clear the rapla session,
    not to uncouple the Google grant (next login would re-trigger
    consent). Enable
    `rapla.oauth.external.google.revoke-on-logout: true` for
    high-security or compliance contexts.

13. **Group sync from token claims.** Out of v1 scope. Follow-up PRD
    would add admin-maintained `external-group-id → rapla-category-id`
    + on-login reconciliation. Ship v1 without, gather demand first.

14. **Provider icons.** v1 ships hardcoded icons for `microsoft`,
    `google`, `rapla` (SVGs bundled with Angular app). Custom icons
    via `icon: "https://..."` resolve to URLs. Acceptable for v1.
