# PRD 036: External IdP OAuth 2.0 Login (Microsoft Entra ID + Google)

**Status:** draft
**Date:** 2026-05-14

## Goal

Let a rapla deployment delegate authentication to an **external OIDC
identity provider** instead of validating passwords against rapla's
local user store. v1 ships two production-ready providers:

- **Microsoft Entra ID** (formerly Azure Active Directory) — for the
  Microsoft 365 / DHBW / German university audience.
- **Google** (consumer accounts + Google Workspace) — for the
  Workspace-based deployments and as a low-friction option for
  evaluators who already have a Google account handy.

Configurability is the design center. A deployment can enable any
combination of providers simultaneously — embedded Spring Authorization
Server (today's default), Microsoft Entra ID, and/or Google. Each
provider is an independent config block (`rapla.oauth.external.microsoft.*`,
`rapla.oauth.external.google.*`); enabling one doesn't affect the others.

**Web (Angular) — user-facing IdP picker, configurable.** Discovery
emits a `providers[]` array and a `picker` config block. The Angular
login screen renders behaviour driven by `picker.mode`:

| `picker.mode` | Behaviour |
|---|---|
| `auto` (default) | Show picker buttons when ≥2 providers enabled; auto-fire the primary provider when only 1 is enabled. Sensible default for most deployments. |
| `always` | Always show the picker, even with a single provider. Useful when an admin wants the user to see an explicit "Sign in" affirmation before being redirected. |
| `never` | Never show a picker. Auto-fire the primary provider; ignore the others (still useful if other providers were enabled for API clients but the web SPA should be single-IdP). |

Each enabled provider declares its own `display-name` ("Sign in with
Microsoft"), `icon` (well-known string: `microsoft`, `google`, `rapla`,
or custom URL), and `order` (sort key, lower first). The SPA needs no
per-deployment build config — buttons, labels, and order all come from
the server's `application.yml`. A deployment with three providers can
hide one from the web picker by setting `web-picker-visible: false` on
that provider (the provider still validates tokens — useful e.g. for
keeping rapla-local password for API/Swing use while only showing SSO
buttons on the web).

**Swing — rapla embedded SAS only, no external IdP support.** The
Swing client is being deprecated in favour of the Angular SPA
(PRD 026). Wiring Microsoft/Google into Swing would mean a new picker
dialog state, browser-launch handling for Google's `access_type=offline`
extra param, and more test surface — all work that ages out the moment
Swing is removed. Swing keeps its current PRD 029 Phase 2 behaviour:
probe discovery, auto-fire the rapla embedded SAS flow. External IdPs
are **web-only**. The discovery endpoint's flat top-level fields
therefore always point at the embedded SAS (regardless of which
providers are enabled or what the picker primary is), so Swing's
discovery probe sees today's URLs unchanged. A Swing-using deployment
that needs SSO before Swing is removed can configure rapla SAS to
federate to their IdP server-side (Spring SAS supports an upstream
`oauth2Login` configurer — separate small effort, out of scope here)
or move to the Angular SPA.

The architecture is provider-pluggable: Entra and Google are the two
shipped implementations, but adding a third (Keycloak, Okta, Auth0) is
a small follow-up because everything provider-specific lives behind one
interface (`ExternalUserResolver`).

## Why this is needed

1. **Corporate SSO is the most-requested missing feature** from rapla
   deployments at universities and DHBW. Today an admin has to provision
   rapla-local users that mirror the corp directory — duplicate password
   policy, separate offboarding, no MFA.
2. **The plumbing is already in place.** `OAuthConfigController` exposes
   `authorize-url`, `token-url`, `jwks-url`, `userinfo-url`,
   `end-session-url`, and `issuer` overrides (the class Javadoc explicitly
   mentions "deployments that delegate auth to Keycloak / Auth0 / Okta /
   etc."). PRD 029 Phase 1 + PRD 031 unified the client view of refresh
   and logout. What's missing is (a) wiring rapla's JWT decoder to validate
   externally-issued tokens, (b) mapping the external `sub` /
   `preferred_username` / `email` claim to a rapla user, and (c) a setup
   recipe an admin can follow.
3. **Microsoft + Google cover the bulk of the addressable audience.**
   DHBW, most German universities, and a large fraction of municipal
   deployments are Microsoft 365 shops; smaller orgs and many education
   deployments are on Google Workspace. Keycloak (the placeholder
   external IdP in PRD 029 Phase 2) is a self-hosted intermediate step
   that few deployments will actually run if they can point at
   Entra/Google directly. Shipping both at once means the same internal
   plumbing — `ExternalUserResolver`, multi-issuer JWT decoder,
   discovery-endpoint re-pointing — is exercised by two real providers
   from day one, so the abstraction is proven before a third is added.
4. **Same shape as PRD 029.** This is not a redesign — it's a
   configuration recipe plus a multi-issuer JWT decoder and a
   provider-pluggable user-mapping component. The discovery endpoint
   stays the same shape; the Swing/Angular clients stay unchanged.

## Scope

### In scope

- **Multi-issuer JWT decoder.** `JwtConfig` learns to validate tokens
  signed by rapla's own JWKS, Microsoft's
  (`https://login.microsoftonline.com/{tenant}/discovery/v2.0/keys`), or
  Google's (`https://www.googleapis.com/oauth2/v3/certs`). Selection is
  per-token, by `iss` claim, against the set of issuers configured at
  startup. Any combination of {local, microsoft, google} can be enabled
  simultaneously — not a global mode switch.
- **Per-provider config blocks** in `application.yml`, each independently
  enable-able:
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
          auto-provision: false
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
          # Claim mapping (Entra defaults)
          username-claim: preferred_username
          email-claim: email
          external-id-claim: oid          # stable per-(tenant,user) GUID

        google:
          enabled: false                 # default off
          client-id: ${RAPLA_OAUTH_GOOGLE_CLIENT_ID:}
          hosted-domain:                 # optional, restrict to a Workspace
                                          # domain via the `hd` claim
          auto-provision: false
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
          # Google's `sub` is stable per (Google account, client_id), so
          # it works as the external-id directly. Note this is different
          # from Microsoft, where we prefer `oid` over `sub`.
          username-claim: email
          email-claim: email
          external-id-claim: sub

      web:
        picker:
          mode: auto                     # auto | always | never
          primary: rapla                 # id of the auto-fire provider for
                                          # mode=auto (single) and mode=never
  ```
  All fields are settable via the matching `RAPLA_OAUTH_*` env vars.
  No `client-secret` field anywhere: rapla stays a public PKCE client
  against both providers.
- **Discovery endpoint emits a `providers[]` array.** Backwards-compatible
  with PRD 029: today's flat `authorizeUrl` / `tokenUrl` / `clientId` /
  `issuer` / `endSessionUrl` fields remain at the top level and
  **always reflect the rapla embedded SAS**, regardless of which
  external providers are enabled. This keeps the Swing client's
  discovery probe seeing today's values — Swing stays on rapla SAS,
  external IdPs are web-only. New `providers[]` field lists every
  enabled provider (including rapla); each entry carries its own URLs,
  display metadata, and `webPickerVisible` flag. New `picker` block
  carries `{ mode, primary }`. Angular reads `providers` + `picker`
  and ignores the flat top-level OAuth fields.
- **External-user mapping.** A new `ExternalUserResolver` interface with
  two implementations: `EntraUserResolver` and `GoogleUserResolver`.
  Input: a validated JWT + the resolved provider ID. Output: a rapla
  `User` entity, either looked up or auto-provisioned. Provider-aware
  three-step lookup:
  1. Match by external-id stored in
     `user.preferences["org.rapla.auth.external-id." + providerId]`
     (set on first successful login). Per-provider key so the same
     person logging in via Microsoft *and* Google doesn't collide in
     preferences storage.
  2. Fall back to match by email (`user.getEmail()`). On first match,
     set the per-provider external-id preference. Google's
     `email_verified` claim **must be true** for the email-match path;
     Entra doesn't expose `email_verified` reliably, so this guard
     applies to Google only.
  3. If the provider's `auto-provision: true` *and* no match, create a
     new rapla `User` with role `Role.USER`, email from the token,
     surname/firstname from `name`/`given_name`/`family_name` claims.
     Default: `false` — admin pre-creates users (safer for v1).
- **Account merging across providers.** Same email signing in via
  Microsoft *then* Google attaches both external-ids to the same rapla
  `User` (preferences are per-provider, but the underlying `User` is
  one). Documented behaviour, not an accident. See OQ §3 for the
  cross-tenant-collision edge case.
- **Provider-specific authorize-URL parameters (web only).** Standard
  PKCE parameters (`response_type`, `client_id`, `redirect_uri`,
  `scope`, `state`, `code_challenge`, `code_challenge_method`) work
  identically for all providers, **but**:
  - **Google** requires `access_type=offline` and `prompt=consent` on
    the authorize request to issue a refresh token. The `offline_access`
    scope used by Entra / Spring SAS doesn't apply.
  - **Entra** can take an optional `domain_hint` to skip the
    "Work or school / Personal" account picker.
  These are encoded in discovery as a per-provider
  `extraAuthorizeParams` map. The Angular login flow appends every
  key=value pair from this map to the authorize URL. Swing ignores
  it — Swing only uses the rapla-SAS entry, whose `extraAuthorizeParams`
  is always empty.
- **Angular login-screen picker UI.** New `LoginPickerComponent`
  renders one button per provider in `providers[]` (filtered to
  `webPickerVisible: true`), sorted by `order`. Each button kicks off
  the existing `angular-oauth2-oidc` flow with that provider's URLs.
  `picker.mode` drives whether the picker renders at all (`auto` shows
  the picker when ≥2 providers; `always` always; `never` auto-fires
  `picker.primary`). Routes use `angular-oauth2-oidc`'s multi-config
  support — `OAuthService` is reconfigured with the chosen provider's
  endpoints before each `initLoginFlow()`.
- **`AuthController` / `TokenHandler` integration.** When an incoming
  `Authorization: Bearer ...` carries an external-issuer JWT,
  `TokenHandler.validate` decodes via the matching JWKS, dispatches to
  the right `ExternalUserResolver`, and proceeds with the resulting
  rapla `User` — same downstream code paths as today's local-issuer
  case.
- **Setup recipes** in `docs/authentication.md`: one section per
  provider. Microsoft: register an App in Entra, find
  tenant/client IDs, whitelist redirect URIs, scopes
  (`openid profile email offline_access`). Google: create an OAuth 2.0
  Client ID in Google Cloud Console (project setup, OAuth consent
  screen, "Web application" credential, authorized redirect URIs,
  scopes `openid profile email`). ~80 + 80 lines of admin copy.
- **Tests** — see Tests section below; all tier-3 MockMvc or tier-1
  unit, plus one tier-6 Angular component test for the picker, plus
  one manual e2e per provider.

### Out of scope

- **Keycloak, Auth0, Okta, generic OIDC.** The `ExternalUserResolver`
  interface and decoder logic are generic enough that adding a third
  resolver is a small follow-up. Shipping Microsoft + Google in v1
  exercises the abstraction enough to lock it in; later providers are
  config + 50-LOC resolver each.
- **Swing-side IdP picker.** Swing keeps its current "auto-fire the
  primary provider" behaviour for v1. A Swing picker is a clean UX
  problem but adds a new dialog state machine; defer to OQ §1 unless
  a deployment asks for it.
- **Token revocation pushed from the IdP.** When an admin disables a
  user in Entra/Google, rapla won't know until the user's refresh token
  expires (Entra default: 90 days idle; Google: 6 months idle for
  consumer accounts, indefinite for Workspace until revoked). Future
  PRD if needed.
- **Group / role sync.** v1 maps to a single rapla `Role.USER`; admin
  promotion stays a rapla-internal preference. Group sync is a
  follow-up PRD (likely tied to multi-tenancy work in PRD 002).
- **Microsoft Graph / Google Workspace API calls.** We use only the
  OIDC `id_token` / `userinfo` endpoint claims. No Graph or Directory
  API calls — those need different scopes and confidential clients.
- **Cross-provider account *merging* by user action.** A user signing
  in via Microsoft and Google with different email addresses ends up
  with two rapla `User` records. v1 has no in-app "link these accounts"
  flow; an admin can manually copy the external-id preference between
  the two if needed. Future PRD if there's demand.
- **Migrating existing rapla-local users.** Existing users continue to
  log in with their rapla password until an admin disables the local
  account *or* sets `rapla.oauth.external.local-accounts-enabled: false`.
  Migration is "log in once via the external provider, the resolver
  attaches your external-id to your existing rapla `User` by email
  match."

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

The clients only know about discovery. PKCE works identically across
all three providers. The Bearer token a request carries is signed by
whichever IdP issued it; the server's multi-issuer decoder routes by
`iss` — Swing-issued tokens (from rapla SAS) and Angular-issued tokens
(from any of the three) all flow through the same validation path.

### Token validation: multi-issuer decoder

`JwtConfig` today wires a single `JwtDecoder` against the embedded
JWKS. The change builds a map of `iss → JwtDecoder` at startup, one
entry per enabled issuer:

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

`IssuerAwareJwtDecoder.decode(token)` peeks the unverified `iss`
claim, routes to the matching decoder, and rejects unknown issuers.
Each decoder fully validates signature + standard claims, so a typo'd
issuer can't sneak a token through.

### Why we keep the rapla `User` for externally-authed identities

External authentication answers "who is this person?" — it does not
answer "what can they do in rapla?" The latter is rapla domain state
that has to live server-side regardless of the IdP:

- **Groups are `Category` entities, not strings.**
  `User.getGroupList()` returns real `Category` references that the
  permission system walks (`PermissionController.canRead` etc.). Even
  if a token claim carried `groups: ["staff", "students"]`, we'd still
  have to resolve those names to rapla `Category` entities. That
  mapping has to live somewhere.
- **Every domain entity references a `User`.** `Reservation.getOwner()`,
  `Allocatable.getLastChangedBy()`, audit trails, per-user preferences,
  per-user filter state, calendar configs — all point at a rapla `User`
  UUID. If externally-authed identities had no rapla-side record,
  references would dangle on every entity they touch.
- **Neither provider reliably puts groups in tokens.** Entra has a
  `groups` claim with a ~150-group cap (overflow → Microsoft Graph
  URL); values are GUIDs, not human-readable names. Google's OIDC
  tokens carry no group memberships at all (would need a Directory
  API call with extra scopes and a confidential client).

So the architecture: every authenticated identity — local or external —
resolves to a rapla `User` entity. For externally-authed identities,
that `User` is a "shadow" record automatically created (or matched by
email) on first login. The token tells us who; the rapla `User` stores
what they can do.

What *does* differ for an externally-authed `User`:

- **Password is unused.** `User.password` stays empty / placeholder;
  rapla never validates against it. Admin UI should disable the
  password field on these users (visual cue: badge with provider icon).
- **Provider attribution.** `user.preferences["org.rapla.auth.provider"]`
  records which provider the user authenticates through (`"microsoft"`,
  `"google"`, or absent = local). Used by admin UI to surface the
  source and (Phase 2) by group-sync logic to know which token claims
  to honour.
- **Group sync from token claims** — deferred to a follow-up PRD.
  Mechanism would be: on each login, pull a configured claim (e.g.
  `groups` for Entra), map each value through an admin-maintained
  `entra-group-guid → rapla-category-id` lookup, replace the user's
  group list. Skipped from v1 because (a) the mapping table is admin
  UX work and (b) it's orthogonal to the auth flow itself. v1 sets
  groups manually after auto-provision; admins can tweak in the user
  editor afterwards.

### Resolving the rapla User

`TokenHandler.handleToken(Jwt)` today does
`facade.getOperator().getUser(jwt.getSubject())` (UUID-keyed lookup).
For external tokens we add a dispatch:

```java
String issuer = jwt.getClaimAsString("iss");
ProviderConfig provider = providers.byIssuer(issuer).orElse(null);
if (provider != null) {
    ExternalUserResolver resolver = resolvers.forProvider(provider.id());
    User user = resolver.resolve(jwt, provider);
    return new TokenHandler.Principal(user);
}
// existing local path
return new TokenHandler.Principal(operator.getUser(jwt.getSubject()));
```

`EntraUserResolver` keys off `oid` (stable per (Entra tenant, user) —
survives email changes, account renames). `GoogleUserResolver` keys
off `sub` (stable per (Google account, OAuth client) — survives email
changes). PRD 029's `user.preferences["org.rapla.auth.session"]`
already establishes the pattern of storing per-user auth state in
preferences; we use the same store under per-provider keys
(`org.rapla.auth.external-id.microsoft`,
`org.rapla.auth.external-id.google`).

### Discovery shape

Top-level fields **always reflect the rapla embedded SAS** (Swing's
target). The `providers[]` array carries the per-provider config that
Angular renders the picker from.

```json
{
  "enabled": true,
  "clientId": "rapla-client",
  "issuer":         "http://localhost:8051",
  "authorizeUrl":   "http://localhost:8051/oauth2/authorize",
  "tokenUrl":       "http://localhost:8051/oauth2/token",
  "refreshUrl":     "http://localhost:8051/api/auth/refresh",
  "logoutUrl":      "http://localhost:8051/connect/logout",
  "jwksUrl":        "http://localhost:8051/oauth2/jwks",
  "userinfoUrl":    "http://localhost:8051/userinfo",
  "endSessionUrl":  "http://localhost:8051/connect/logout",
  "scopes":         ["openid", "profile", "email", "offline_access"],
  "showPasteFallback": false,

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

The flat top-level fields are the **primary provider's** values, kept
for Swing backwards-compat. Angular reads `providers[]` + `picker` and
ignores the top-level OAuth fields.

### Refresh path

Both Entra (`/oauth2/v2.0/token`) and Google (`/token`) accept
`grant_type=refresh_token` and return a fresh access + refresh token —
same shape as Spring SAS and the unified `/api/auth/refresh` PRD 031
ships. The client doesn't refresh through rapla's
`/api/auth/refresh` when the active provider is external; the
provider entry's `tokenUrl` becomes the `refreshUrl`. PRD 031's
per-provider `refresh-url` override field exists for exactly this case.

The refresh-token rotation policy is the provider's, not rapla's: the
"single-token-per-user, rotate-when-stale" logic in `AuthController`
doesn't apply when external is active. Rapla can't force-revoke an
externally-issued refresh token; revocation happens IdP-side (admin
disables user, token blacklist) and rapla learns at the next
refresh-fails → reauth cycle.

**Google refresh quirk.** Google issues a refresh token *only on the
first consent* unless `prompt=consent` is passed every time. To make
silent reauth reliable across multi-rapla-launch usage, the Swing /
Angular code path that *first* obtains the token must persist it via
the PRD 029 `TokenStore`; subsequent launches refresh against the
stored token rather than re-running the authorize flow.

### Logout

Entra has a proper OIDC RP-initiated logout endpoint:
`https://login.microsoftonline.com/{tenant}/oauth2/v2.0/logout`. Swing
and Angular redirect to it; `id_token_hint` is recommended (already
plumbed by the Swing client per PRD 029 Phase 2 2026-05-13 fix).

Google **has no proper RP-initiated OIDC logout**. The
`https://accounts.google.com/Logout` URL exists but logs the user out
of *every* Google product (Gmail, Drive, YouTube) — almost never the
desired UX. Behaviour when the active provider is Google:
1. Clear the local rapla access/refresh tokens (TokenStore + memory).
2. Optionally POST to `https://oauth2.googleapis.com/revoke` with the
   refresh token to revoke the grant server-side (configurable via
   `rapla.oauth.external.google.revoke-on-logout: true`; default off
   because users typically expect "log out of rapla", not "uncouple
   the OAuth grant").
3. Do **not** open a browser tab. The user's Google session in the
   browser stays untouched (their choice — they're still signed in to
   other Google services).

Rapla's local remember-me cookie + `/connect/logout` flow is bypassed
entirely when external IdP is active: there's no rapla session to
clear because the user never authenticated against rapla's form login.

### Why configurable multi-provider is the right shape

A flag-based "single external IdP" design would have been simpler
internally but worse for deployments:

- Most universities have *both* Microsoft and Google identities for
  different user groups (staff vs students; permanent staff vs guests).
  Forcing them to pick one freezes out the other.
- A migration from "embedded SAS" to "Microsoft only" has no rollback —
  if the Entra config has a typo, every user is locked out. With
  multi-provider, the rapla password path stays as the emergency lane.
- The added complexity over single-external-IdP is bounded: a `Map<iss,
  JwtDecoder>` instead of a single decoder, a registry of resolvers
  instead of one resolver, a `providers[]` array instead of flat
  fields in discovery. All linear in the number of providers, all
  exercised by the two shipped providers from day one.

## Plan

1. **Server: provider config + multi-issuer JWT decoder.**
   - Add `ExternalProvidersProperties` (`@ConfigurationProperties`,
     `rapla-server`, package `org.rapla.server.spring`) with nested
     `Microsoft` and `Google` blocks, each with its own `enabled` flag.
   - Add `IssuerAwareJwtDecoder` keyed by `iss`.
   - Wire both in `JwtConfig`. When no external provider is enabled,
     behaviour is byte-identical to today.
   - Tier-1 + tier-3 tests as in Tests section below.

2. **Server: `ExternalUserResolver` + two implementations.**
   - Interface in `rapla-server`, plain Java, takes a validated `Jwt`
     plus the resolved `ProviderConfig`, returns a rapla `User`.
   - `EntraUserResolver`: `oid` external-id, `preferred_username`,
     `email`, `name`/`given_name`/`family_name` claims, optional
     `hosted-domain` check.
   - `GoogleUserResolver`: `sub` external-id, `email` username,
     `email_verified` guard on the email-match branch, optional
     `hd` claim check for Workspace-domain restriction.
   - Resolver registry component (`ExternalUserResolvers`) dispatches
     by provider id.
   - All state lives in user preferences under per-provider keys
     (`org.rapla.auth.external-id.microsoft`,
     `org.rapla.auth.external-id.google`,
     `org.rapla.auth.provider`).
   - Tier-1 unit tests: each lookup branch per resolver.

3. **Server: `TokenHandler` integration.**
   - Dispatch on `iss` claim, route through the resolver registry.
   - `/api/auth/logout` becomes a no-op when the resolved provider is
     external (nothing to revoke server-side).
   - Tier-3 MockMvc tests for both providers.

4. **Server: discovery endpoint emits `providers[]` + `picker`.**
   - Extend `OAuthConfigController` to read
     `ExternalProvidersProperties` and emit the `providers[]` array.
   - Flat top-level fields **always reflect the rapla embedded SAS**,
     regardless of which external providers are enabled — Swing's
     discovery probe stays unchanged.
   - `picker.primary` defaults to `rapla` (changeable for Angular
     auto-fire behaviour).
   - Tier-3 MockMvc tests: each combination of (microsoft-enabled,
     google-enabled, picker.mode) emits the right shape and the
     top-level fields stay rapla-SAS across all combinations.

5. **Server: provider-specific authorize-URL parameters.**
   - Each `ProviderConfig` carries an `extraAuthorizeParams` map.
   - Google's defaults: `{ access_type: "offline", prompt: "consent" }`.
   - Tier-1 test: serialized into discovery correctly.

6. **Swing client: no changes.** External IdPs are web-only (Swing
   deprecation context — see Goal). Swing's existing PRD 029 Phase 2
   discovery probe + auto-fire-rapla-SAS flow stays as-is. Confirm by
   running existing Swing OAuth tests against a server config with
   Microsoft + Google enabled — Swing should ignore them and use the
   flat top-level rapla-SAS fields.

7. **Angular client: `LoginPickerComponent` + multi-config OAuth.**
   - New `LoginPickerComponent` reads `providers[]` + `picker` from the
     discovery response, renders one button per `webPickerVisible:true`
     entry sorted by `order`, with `icon` + `displayName`.
   - On click: reconfigure `OAuthService` with the chosen provider's
     endpoints + `extraAuthorizeParams` and call `initLoginFlow()`.
   - `picker.mode = auto` auto-fires when there's only one provider;
     `always` always shows the picker; `never` auto-fires
     `picker.primary`.
   - Tier-6 Angular component test (Vitest + TestBed): renders the
     right number of buttons per mode, clicking a button initiates the
     right `OAuthService` flow (mocked).

8. **Documentation.**
   - New `docs/authentication.md` sections:
     - "External IdP — Microsoft Entra ID": Entra App Registration
       steps (tenant ID, client ID, redirect URI whitelist, scopes
       `openid profile email offline_access`).
     - "External IdP — Google": Google Cloud Console setup (project,
       OAuth consent screen, "Web application" credential, authorized
       redirect URIs, scopes `openid profile email`).
     - "Multi-provider deployments": the `web.picker` knobs, what a
       login screen with three buttons looks like, hidden-from-web
       providers (`webPickerVisible:false`).
   - Cross-link from PRD 029 Phase 2 and PRD 031.

9. **Manual smoke**:
   - Entra test tenant: Angular login → main view.
   - Google OAuth test client (Chris's personal or a project test
     client): Angular login → main view. Verify refresh after token
     expiry uses the stored Google refresh token (no second consent
     screen).
   - Multi-provider (both enabled + rapla): Angular picker shows three
     buttons in the right order; each one ends up signed in.
   - Swing regression: with all three providers enabled server-side,
     Swing still launches into the rapla SAS flow and signs in
     unchanged. Tagged `e2e`, manual only.

10. **PRD close**: when all phases done, `git mv` to `docs/prd/done/`.
    Update PRD 029 Phase 2 OQ §5 ("Hide the username/password path
    entirely?") — `localAccountsEnabled=false` becomes meaningful once
    one or more external providers are configured.

## Tests

| Tier | Test | What it locks in |
|------|------|------------------|
| 1 | `IssuerAwareJwtDecoderTest` | Routes by `iss`, rejects unknown issuer, rejects malformed JWT, coexists with 3 issuers active |
| 1 | `EntraUserResolverTest` | Each of the 3 lookup branches; external-id-preference written under `org.rapla.auth.external-id.microsoft` on first match; auto-provision off-default; `hosted-domain` check |
| 1 | `GoogleUserResolverTest` | Same shape as Entra test; uses `sub` not `oid`; `email_verified=false` blocks the email-match path; `hd` claim check |
| 1 | `ExternalProvidersPropertiesTest` | Env-var binding (`RAPLA_OAUTH_EXTERNAL_MICROSOFT_TENANT` etc.), default values, `enabledProviders()` reflects per-provider `enabled` flags |
| 3 | `EntraTokenIntegrationTest` (MockMvc + stubbed Entra JWKS) | Entra Bearer unlocks rapla REST, resolved user is correct, scoped to the right `oid` |
| 3 | `GoogleTokenIntegrationTest` (MockMvc + stubbed Google JWKS) | Google Bearer unlocks rapla REST, resolved user is correct, scoped to the right `sub` |
| 3 | `MultiProviderCoexistenceIntegrationTest` | With local + Microsoft + Google all enabled, all three token kinds validate against the same `JwtDecoder` |
| 3 | `DiscoveryWithoutExternalProvidersTest` | Default config: discovery byte-identical to today's output, `providers[]` contains only rapla (regression guard) |
| 3 | `DiscoveryWithMicrosoftOnlyTest` | Microsoft enabled: `providers[]` has rapla + microsoft; primary=microsoft; picker.mode=auto; flat top-level fields point at Entra |
| 3 | `DiscoveryWithGoogleOnlyTest` | Symmetric for Google; verifies `extraAuthorizeParams` carries `{access_type, prompt}` |
| 3 | `DiscoveryWithAllThreeTest` | All three providers in `providers[]`, ordered by `order`; `webPickerVisible` flag honoured |
| 3 | `DiscoveryPickerModesTest` | Each `picker.mode` value (auto/always/never) round-trips through discovery correctly |
| 3 | `ExternalTokenRefreshTest` | `MyCustomConnector.reauth` against stubbed Entra/Google token endpoints succeeds via the discovery-emitted `refreshUrl` |
| 3 | `ApiKeyWorksWithExternalProviderConfiguredTest` | A rapla-issued API key (PRD 031) keeps working when external providers are enabled — multi-issuer decoder doesn't break the local path |
| 2 | `SwingDiscoveryIgnoresExternalProvidersTest` | With Microsoft + Google enabled server-side, the Swing client's existing discovery-probe + auto-fire-rapla-SAS path is unchanged; the flat top-level fields still point at rapla SAS |
| 6 | `LoginPickerComponent.spec.ts` (Angular Vitest + TestBed) | `picker.mode=auto + 1 provider` → no picker, auto-fire; `auto + 2 providers` → picker visible; `always` → always picker; `never` → no picker; click on a button reconfigures `OAuthService` and calls `initLoginFlow()` |
| 5 | `OAuthProviderConfig.spec.ts` (Angular Vitest, no TestBed) | Sorting by `order`, filtering by `webPickerVisible`, icon → image mapping |
| e2e (manual) | Real Entra tenant, Angular login | Full stack via Microsoft (web-only — Swing not exercised) |
| e2e (manual) | Real Google client, Angular login | Full stack via Google, including refresh after token expiry |
| e2e (manual) | Both providers enabled, Angular picker | Three-button picker renders, each button leads to a valid signed-in state |
| e2e (manual) | Both providers enabled, Swing login | Swing ignores them and signs in via rapla SAS unchanged (regression guard for the deprecation-window) |

## Open Questions

1. **Swing SSO during the deprecation window.** Resolved: not in
   scope. External IdPs are web-only. Swing keeps using the rapla
   embedded SAS until the Swing client is removed (PRD 026 roadmap).
   Deployments that need SSO from Swing before then have two options
   neither of which require changes to this PRD: (a) federate rapla
   SAS to an upstream IdP server-side via Spring SAS's `oauth2Login`
   configurer (a separate small effort — rapla SAS itself becomes the
   relying party to Entra/Google, Swing still talks to rapla SAS),
   or (b) move the affected users to the Angular SPA. Not a
   future-PRD item.

2. **Auto-provisioning default per provider.** Off (admin pre-creates
   rapla users) is safer but doubles onboarding work. On (any valid
   token = rapla account) matches the SSO expectation but means an
   accidentally-multi-tenant Entra setup or a wide-open Google client
   gets every Google user on Earth a rapla account. **Tentative**: off
   by default per provider; document the `hosted-domain` /
   single-tenant guards as preconditions for turning it on.

3. **Cross-provider account collisions.** Same email signing in via
   Microsoft *and* Google: do we (a) attach both external-ids to the
   same rapla `User` (current design — preferences are per-provider,
   the User entity is one) or (b) treat them as separate users? (a) is
   what users expect for personal-email-on-both-clouds, but it can
   silently merge an Entra-shared-mailbox identity with a Google
   personal identity that happen to share an email. Leaning (a) for
   v1 because (b) requires inventing a "linked accounts" UI.

4. **Single-tenant vs multi-tenant Entra.** Single-tenant is the right
   default — restricts logins to the deployment's own Entra tenant.
   `tenant` config field takes a GUID/domain (single) or `common`
   (multi). Document both; sample config uses single-tenant placeholder.

5. **Google `hosted-domain` enforcement.** The `hd` claim on a Google
   token tells us which Workspace domain the user signed in from.
   Enforcing `hd == configured-domain` server-side prevents personal
   `@gmail.com` accounts from logging into a Workspace-only rapla
   deployment. Default: enforce when `hosted-domain` is set, allow
   any when blank. Note the `hd` claim is not signed on the
   authorize-URL side (`hd` query param), so server-side check is
   mandatory.

6. **What happens to existing rapla-local users when external is on?**
   They keep logging in via the legacy password form *unless*
   `rapla.oauth.local-accounts-enabled: false`. With external + local
   both on, the Angular picker shows a "Sign in with rapla password"
   button alongside the SSO buttons. Some deployments will want that;
   some will want to forbid the legacy path entirely after migration.
   Configurable via the same flag PRD 029 OQ §5 had.

7. **`oid` vs `sub` for Entra; `sub` for Google.** Microsoft documents
   `oid` as stable per (tenant, user) and `sub` as a pairwise
   pseudonym that varies per-application — we want `oid`. Google's
   `sub` is stable per (Google account, OAuth client), which is what
   we want there. Document the asymmetry, default per provider.

8. **API keys + external providers.** API keys are rapla-issued bearer
   tokens (PRD 031) stored in user preferences. They work today
   against the local JWT decoder. With multi-issuer decoder, API keys
   keep working because they're locally signed and carry the local
   `iss`. **Answer**: yes, no extra work, lock in with
   `ApiKeyWorksWithExternalProviderConfiguredTest`.

9. **Scopes.** Entra: `openid profile email offline_access`
   (`offline_access` gets the refresh token). Google: `openid profile
   email` + `access_type=offline` as authorize param (Google doesn't
   use `offline_access` scope). Defaults live in the per-provider
   config block; admins shouldn't usually need to override.

10. **Confidential vs public client.** Both Entra and Google support
    public PKCE clients — no `client-secret`. Stay public. If a future
    requirement forces confidential (e.g. Microsoft Graph calls for
    group sync), that's a different PRD with its own threat model.

11. **`post_logout_redirect_uri` for Entra.** Yes — without it Entra
    shows a generic "you're signed out" page; with it, the user lands
    back on the rapla SPA. Configurable via
    `rapla.oauth.external.microsoft.post-logout-redirect-uri`; default
    to the deployment's public base URL. Entra requires the URI to be
    pre-registered on the Application; cover in the setup recipe.

12. **Token revocation on Google logout.** Default off — typical user
    expectation of "log out of rapla" is to clear the rapla session,
    not to uncouple the Google grant (next login would re-trigger the
    consent screen). Enable
    `rapla.oauth.external.google.revoke-on-logout: true` for
    deployments where revocation is preferred (high-security or
    compliance contexts).

13. **Group sync from token claims.** Out of v1 scope; called out in
    the "Why we keep the rapla `User`" section. A follow-up PRD would
    add an admin-maintained `external-group-id → rapla-category-id`
    table and on-login group reconciliation. Decision: ship v1 without
    it, gather real-deployment demand first.

14. **Provider icons.** v1 ships hardcoded icons for `microsoft`,
    `google`, `rapla` (SVGs bundled with the Angular app). Custom
    icons via `icon: "https://..."` resolve to image URLs the admin
    hosts. Acceptable for v1 — a more elaborate "upload your logo via
    the admin UI" is overkill before there's demand.
