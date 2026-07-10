# PRD 037: Shibboleth via Reverse-Proxy Trusted Headers

**Status:** draft
**Date:** 2026-05-15

## Goal

Support SAML 2.0 / Shibboleth IdPs for rapla deployments that cannot use Keycloak SAML brokering ([PRD 036](036-external-idp-oauth-login.md) §2.2), **without implementing SAML inside rapla.** Apache + the Shibboleth SP module (de-facto standard in academic IT) handles the full SAML protocol; rapla picks up the resulting identity from trusted HTTP headers and immediately switches to rapla-native auth (JWT for SPA, existing `ExternalUserResolver` for user lookup, existing refresh-token / API-key infrastructure for downstream).

> **In one sentence:** Shibboleth does the credential check at a reverse proxy in front of rapla; rapla reads identity headers, resolves to a rapla `User`, mints a rapla-local JWT, and from that point on the SPA and API surface are 100% rapla-native — Shibboleth never enters the rapla codebase.

## Why this is needed

[PRD 036](036-external-idp-oauth-login.md) §2.2 already covers SAML for most deployments via Keycloak brokering (zero rapla code). This PRD addresses the narrower segment that wants SAML federation but cannot or won't run Keycloak:

1. **Deployment already runs Apache + Shibboleth** for other institutional apps and wants rapla to fit the same operational pattern. Adding Keycloak would mean a second auth service.
2. **Federation registration is in the institution's name**, SP entity ID `https://rapla.uni.de/shibboleth` (Apache's metadata) — not Keycloak's. Re-registering through Keycloak's metadata adds federation paperwork.
3. **Operational preference for "Apache + app behind it"** over "auth-service + app behind it." Common stance for IT shops that know mod_shib but don't know Keycloak.

This pattern is the **standard SAML integration for legacy academic webapps**: Apache (with `mod_shib`) terminates SAML and reverse-proxies authenticated requests to the backend, passing identity attributes as request headers. Examples: many Moodle, Confluence, GitLab, Mattermost and homegrown installations across DFN-AAI, eduGAIN, InCommon.

## Scope

### In scope

- **A "shibboleth" provider type** in rapla's external IdP config, alongside [PRD 036](036-external-idp-oauth-login.md)'s OIDC providers (Microsoft, Google, Keycloak). Discriminator: `saml-proxy`.
- **`/api/auth/shibboleth/handshake/{providerId}`** endpoint that reads identity attributes from configurable HTTP headers, resolves to a rapla `User` via [PRD 036](036-external-idp-oauth-login.md)'s `ExternalUserResolver`, mints a rapla-local JWT via existing `JwtIssuer`, and redirects the browser to `/app/auth/saml-callback?token=<jwt>` (existing JWT-bridge UX).
- **Trust-boundary enforcement**: rapla refuses identity headers unless the request originated from a configured trusted proxy IP/CIDR. Defense in depth on top of the assumption that rapla is not directly exposed to the public internet.
- **Attribute-to-claim mapping** configurable per-provider (academic defaults: `eppn` → username, `mail` → email, `persistent-id` → external-id, `cn` → name).
- **Logout coordination**: SPA's sign-out clears its rapla JWT and redirects the browser through Apache's `/Shibboleth.sso/Logout`, which handles SAML SLO with the upstream IdP and bounces back to rapla's `/app/login`.
- **Picker UI integration** in Angular SPA: Shibboleth provider appears as a button in `LoginPickerComponent` alongside OIDC entries. Click → full-page navigation to a rapla endpoint that forwards through Apache → Shib → IdP → assertion → headers → JWT.
- **One Shibboleth provider per rapla deployment** for v1 (multiple upstream IdPs / federation discovery at the Apache+Shib layer, transparent to rapla).
- **Setup recipes** for Shibboleth IdP registration, Apache + mod_shib config, attribute release, federation metadata management.

### Out of scope

- **SAML protocol implementation in rapla** — no XML-DSIG, no SP metadata generation, no `AuthnRequest`/`SAMLResponse` parsing. All in `mod_shib`.
- **Discovery service / WAYF** — runs at Apache+Shib layer (`SessionInitiator` discovery profile, or federation's central WAYF). Rapla sees one Shibboleth provider entry.
- **Embedded SAML SP for deployments without a reverse proxy** — documented as deferred alternative in Appendix. Add only if a real deployment specifically requires "rapla as a single deployment artifact" AND cannot run Apache in front. Native SAML in rapla is ~3x higher cost (~1700 LOC).
- **Native multi-Shibboleth-IdP routing.** One Apache+Shib instance handles one institutional SP identity.
- **Keycloak migration tools.** Switching between Keycloak brokering ([PRD 036](036-external-idp-oauth-login.md) §2.2) and Shibboleth-via-proxy requires re-keying `external-id` preferences; manual SQL/data-file edit for the rare migration.

## Architecture

### Request flow

```
                          (1) Browser navigates to /app/login (rapla SPA)
                              SPA fetches /api/auth/oauth/config
                              Picker shows "Sign in with university SSO"
                              (a Shibboleth provider entry)
   
                          (2) User clicks the Shibboleth button
                              SPA navigates (full page) to
                              /api/auth/shibboleth/handshake/<providerId>
   
   Apache (mod_shib)    ←─── (3) Apache intercepts: this location is
                              "AuthType shibboleth + requireSession 1"
                              Apache redirects to its Shibboleth.sso/Login
                              which builds an AuthnRequest and
                              redirects browser to the IdP SSO endpoint
   
   Shibboleth IdP       ←─── (4) Browser GET → IdP SSO endpoint
                              User signs in (Kerberos / password / MFA)
   
   Apache (mod_shib)    ←─── (5) IdP POSTs SAMLResponse to
                              Apache's /Shibboleth.sso/SAML2/POST
                              Apache validates signature, audience,
                              timestamps, replay (all via mod_shib)
                              Apache sets identity attribute headers
                              Apache 302's browser BACK to the original
                              /api/auth/shibboleth/handshake/<providerId>
   
                          (6) Apache reverse-proxies the request to
                              rapla (127.0.0.1:8051) WITH the headers:
                                eppn:        alice@uni.de
                                mail:        alice@uni.de
                                cn:          Alice Smith
                                Shib-Identity-Provider: https://...
                                Shib-Session-ID:        _abc123...
   
                          (7) Rapla's ShibbolethHandshakeController:
                              - verify request came via a trusted-proxy IP
                                (config: rapla.oauth.shibboleth.<id>.trusted-proxies)
                              - read configured attribute headers per the
                                per-provider attribute-map
                              - build a claim map
                              - dispatch to ExternalUserResolver (PRD 036)
                                three-step lookup: external-id pref → email →
                                auto-provision a rapla User
                              - JwtIssuer mints a rapla access_token JWT
                                (sub = user.UUID) — same as /api/auth/login
                              - HTTP-302 to /app/auth/saml-callback?token=<jwt>
                                                                  &provider=<id>
   
                          (8) SPA's saml-callback route:
                              - parse token from URL
                              - store in localStorage (under the keys
                                angular-oauth2-oidc already uses:
                                access_token, expires_at)
                              - set activeProvider = providerId
                              - navigate to /reservations
   
                          (9) From here on: SPA carries
                              Authorization: Bearer <rapla-local JWT> on
                              every API call. JwtConfig.jwtDecoder() (the
                              existing local path) validates it. The whole
                              downstream stack — interceptor, resource
                              server, RemoteSession, controllers — is
                              completely unaware that the user authenticated
                              via Shibboleth. From rapla's perspective the
                              JWT looks identical to one issued by
                              /api/auth/login.
```

### Trust boundary

The whole design assumes rapla is not directly reachable from the public internet — only Apache is. Enforcement:

1. **Bind rapla to localhost only**: `server.address: 127.0.0.1` in `application.yml` (or `application-local.yml`). Apache is the only thing that can connect.
2. **Trusted-proxy IP check** on the handshake endpoint: rapla refuses identity headers unless `X-Forwarded-For` (or direct peer address) matches a configured trusted CIDR. Default trusted list: `127.0.0.1/32`, `::1/128`.
3. **Documentation drumbeat**: every recipe explicitly notes that exposing rapla's `server.port` to the public internet bypasses Apache's auth. Make it impossible to set up Shibboleth-via-proxy without seeing this warning.

If someone misconfigures and exposes rapla directly, Apache's auth is bypassed; the trusted-proxy IP check is the second line of defense. Defence-in-depth combination is the same one used by every header-trust-based webapp in the academic world.

### Reuse from [PRD 036](036-external-idp-oauth-login.md)

| Component | Reused? |
|---|---|
| `IssuerAwareJwtDecoder` (multi-issuer JWT routing) | ❌ — the rapla-local decoder validates the bridged JWT (existing path). |
| `OAuthExchangeController` (BFF) | ❌ — no SAML token to exchange. |
| `ExternalUserResolver` + 3-step lookup | ✅ — accepts a generic claim map; SAML attributes feed it identically to OIDC claims. |
| "Keep the rapla User" architecture | ✅ — same auto-provision + email-match + external-id pinning logic. |
| `JwtIssuer` (rapla SAS local JWT minting) | ✅ — the handshake endpoint calls it. |
| `application-local.yml` config pattern | ✅ — new `rapla.oauth.shibboleth.<id>` block. |
| Picker UI (`LoginPickerComponent`) | ✅ — renders the Shibboleth button. Click handler branches on provider `type`: OIDC types call `OAuthService.initCodeFlow`; SAML-proxy navigates to `provider.loginUrl`. |
| Discovery endpoint extension | ✅ — emits Shibboleth entries with `type: "saml-proxy"` and `loginUrl`. |
| `activeProvider` localStorage persistence | ✅ — same key, different value. |
| Auto-provision + `USERGROUP_CONFIG` defaults | ✅ — identical. |
| Logout | ⚠️ — branches. SPA's `signOut()` for SAML-proxy navigates to `provider.shibbolethLogoutUrl`, which is Apache's `/Shibboleth.sso/Logout?return=<post-logout-redirect>`. |
| New SPA route `/app/auth/saml-callback` | ✅ — same one we'd build for any "JWT delivered via redirect" pattern (already needed for native SAML in option A as well). |

Rule of thumb: **~85% reuses [PRD 036](036-external-idp-oauth-login.md) infrastructure**; new parts are the handshake endpoint, trusted-proxy guard, attribute-map adapter, and ~80 lines of Angular for picker / saml-callback.

### Config shape

```yaml
rapla:
  oauth:
    shibboleth:
      shibboleth:                 # provider id (admin-chosen; can repeat)
        enabled: true
        # UX / picker
        display-name: "Sign in with university SSO"
        icon: shibboleth
        order: 15
        web-picker-visible: true
        # Trust boundary
        trusted-proxies:
          - 127.0.0.1/32
          - ::1/128
        # Attribute-to-claim mapping — these are HTTP HEADER NAMES the
        # reverse proxy sets after Shibboleth auth, NOT SAML attribute
        # URNs. Apache's mod_shib's attribute-map.xml decides how SAML
        # attrs translate to header names; the defaults below match the
        # standard Shibboleth attribute-map.xml that ships with mod_shib.
        attributes:
          username:    eppn
          email:       mail
          external-id: persistent-id
          name:        cn
          given-name:  givenName
          family-name: sn
        # User-resolution
        auto-provision: true
        hosted-domain: ""         # optional email-domain guard
        # Logout — relative URL on the rapla origin that the SPA
        # browser-navigates to on sign-out. Apache must serve this
        # path via mod_shib.
        shibboleth-logout-url: /Shibboleth.sso/Logout
```

### Discovery shape extension

[PRD 036](036-external-idp-oauth-login.md)'s discovery JSON gets a `type` discriminator field per provider entry:

```json
{
  "providers": [
    { "id": "rapla",      "type": "oidc",       "tokenUrl": "...", ... },
    { "id": "microsoft",  "type": "oidc",       "tokenUrl": "...", ... },
    { "id": "google",     "type": "oidc",       "tokenUrl": "...", ... },
    { "id": "shibboleth", "type": "saml-proxy", "loginUrl": "/api/auth/shibboleth/handshake/shibboleth",
      "shibbolethLogoutUrl": "/Shibboleth.sso/Logout",
      "displayName": "Sign in with university SSO", "icon": "shibboleth", ... }
  ]
}
```

OIDC entries keep their `authorizeUrl`/`tokenUrl`/`jwksUrl`; `saml-proxy` entries only carry the rapla-side init URL plus the Shibboleth logout path — everything else lives at the Apache layer.

### Apache + mod_shib config sketch (admin recipe)

```apache
# Shibboleth SP configuration is in /etc/shibboleth/shibboleth2.xml
# This is just the Apache integration.

# Protect the rapla handshake endpoint with Shibboleth — only requests
# with a valid Shib session reach the backend.
<Location /api/auth/shibboleth/handshake>
    AuthType shibboleth
    ShibRequestSetting requireSession 1
    Require valid-user
    ShibUseHeaders On     # pass attributes as request headers (default)
</Location>

# Strip incoming identity headers on ALL requests to prevent client
# spoofing. mod_shib re-sets them within protected Locations.
RequestHeader unset eppn
RequestHeader unset mail
RequestHeader unset cn
RequestHeader unset givenName
RequestHeader unset sn
RequestHeader unset persistent-id
RequestHeader unset Shib-Identity-Provider
RequestHeader unset Shib-Session-ID

# Reverse-proxy everything else to rapla on localhost
ProxyPass        /api/  http://127.0.0.1:8051/api/
ProxyPassReverse /api/  http://127.0.0.1:8051/api/
ProxyPass        /app/  http://127.0.0.1:8051/app/
ProxyPassReverse /app/  http://127.0.0.1:8051/app/
# ... other rapla paths (/oauth2/, /connect/, /static/, etc.)

# Shibboleth's own paths must be handled by Apache, NOT proxied:
# /Shibboleth.sso/* is the mod_shib handler.

# Forward client IP so rapla's trusted-proxy check sees the right
# peer. (X-Forwarded-For is set by mod_proxy automatically.)
```

Combined with `server.address: 127.0.0.1` in rapla's config, the deployment surface is: Apache on :80/:443 (public) → rapla on :8051 (loopback only).

## Plan

1. **Config + provider model**
   - New `ShibbolethProvidersProperties` `@ConfigurationProperties` class with `Map<String, ShibbolethProvider>` (keyed by admin-chosen id, repeatable).
   - `ShibbolethProvider` POJO with the fields from the Config shape section above.
   - `ShibbolethProvider.toProviderConfig()` returns a `ProviderConfig` (the same type from [PRD 036](036-external-idp-oauth-login.md)) with `type = SAML_PROXY`. `ProviderConfig.type` enum: `OIDC` (default) | `SAML_PROXY`.
   - Validation: `trusted-proxies` non-empty when enabled.

2. **Handshake endpoint**
   - New `ShibbolethHandshakeController` in `rapla-server`, packaged alongside `OAuthConfigController`.
   - `GET /api/auth/shibboleth/handshake/{providerId}`:
     - Reject if request's peer IP isn't in the provider's `trusted-proxies` CIDR list. Return 403 with a clear message.
     - Read the configured headers, build a `Map<String,String>` of claims.
     - Call `ExternalUserResolver.resolve(claims, providerConfig)` (existing [PRD 036](036-external-idp-oauth-login.md) resolver).
     - On success: `JwtIssuer.issueAccessToken(user.getId(), 3600)`, 302 to `/app/auth/saml-callback?token=<urlencoded>&provider=<id>`.
     - On user-resolution failure: 302 to `/app/login?error=<reason>`.

3. **Discovery endpoint update**
   - `OAuthConfigController.buildProviders()` adds enabled Shibboleth providers to `providers[]` with `type: "saml-proxy"` and `loginUrl: "/api/auth/shibboleth/handshake/<id>"` plus `shibbolethLogoutUrl`.
   - Flat top-level fields stay rapla-SAS (unchanged Swing regression-guard, same as [PRD 036](036-external-idp-oauth-login.md)).

4. **Angular: `OAuthProviderEntry` discriminator + click handler branching**
   - Add `type: 'oidc' | 'saml-proxy'` to `OAuthProviderEntry` in `auth.service.ts`.
   - `signInWithProvider(providerId)`:
     - OIDC path: existing — reconfigure `OAuthService`, `initCodeFlow`.
     - SAML-proxy path: `window.location.href = provider.loginUrl` (full-page navigation, no PKCE/state — Apache handles that).
   - `signOut()`:
     - OIDC path: existing.
     - SAML-proxy path: clear local tokens, then `window.location.href = '<origin><provider.shibbolethLogoutUrl>?return=<origin>/app/login'`.

5. **Angular: saml-callback route**
   - New `SamlCallbackComponent` at `/app/auth/saml-callback`.
   - Reads `?token=<jwt>&provider=<id>` from URL.
   - Stores `access_token` + `expires_at` in localStorage (matching keys angular-oauth2-oidc uses for OIDC).
   - Sets `localStorage.setItem('rapla.oauth.activeProvider', provider)`.
   - Navigates to `/reservations`.

6. **Setup recipes** in `docs/authentication.md`:
   - "Shibboleth via reverse proxy" main recipe: install Apache + `libapache2-mod-shib`, install Shibboleth daemon, register SP metadata with your IdP/federation, configure attribute release, wire up Apache vhost, set `server.address: 127.0.0.1`, set `rapla.oauth.shibboleth.<id>.*`. ~150 lines.
   - "Federation registration" supplements for DFN-AAI, eduGAIN, InCommon. ~50 lines each.

7. **Manual e2e** against a real or stubbed Shibboleth setup. Easiest local dev: SimpleSAMLphp container as the IdP + `shibboleth/sp` Apache image in front of rapla. Tagged `e2e`, manual only.

8. **PRD close**: when status hits `done`, `git mv` to `docs/prd/done/`. Cross-reference from [PRD 036](036-external-idp-oauth-login.md) §2.2.

## Tests

| Tier | Test | What it locks in |
|------|------|------------------|
| 1 | `ShibbolethProvidersPropertiesTest` | Env-var binding (`RAPLA_OAUTH_SHIBBOLETH_<ID>_*`), default attribute map, validation (trusted-proxies non-empty when enabled). |
| 1 | `TrustedProxyMatcherTest` | CIDR matching: `127.0.0.1/32`, `192.168.0.0/16`, `::1/128`, IPv4-mapped IPv6, multiple hops in `X-Forwarded-For`. |
| 2 | `ShibbolethAttributeMapAdapterTest` | Translates request headers (`eppn`, `mail`, …) into the claim map shape `ExternalUserResolver` expects. |
| 3 | `ShibbolethHandshakeHappyPathTest` (MockMvc) | `GET /api/auth/shibboleth/handshake/<id>` with simulated trusted-proxy peer + identity headers → 302 to `/app/auth/saml-callback?token=…`, JWT decodes to the right user UUID. |
| 3 | `ShibbolethHandshakeAutoProvisionsUserTest` | Unknown user + auto-provision=true → new rapla User created with attributes from headers. |
| 3 | `ShibbolethHandshakeRejectsUntrustedProxyTest` | Request from an IP outside `trusted-proxies` → 403, no JWT minted, no user created. |
| 3 | `ShibbolethHandshakeRejectsMissingRequiredAttrsTest` | Missing required claim (no `eppn` header) → redirect to `/app/login?error=…`, no JWT. |
| 3 | `ShibbolethHandshakeRespectsHostedDomainTest` | Email not in configured `hosted-domain` → rejected. |
| 3 | `DiscoveryEmitsShibbolethProvidersTest` | `/api/auth/oauth/config` includes Shibboleth provider entries with `type: "saml-proxy"`, `loginUrl`, `shibbolethLogoutUrl`. |
| 3 | `BridgedJwtIntegrationTest` | After handshake mints a JWT, that JWT validates via the existing `JwtConfig.jwtDecoder()` and unlocks rapla REST endpoints. |
| 6 | `LoginPickerComponent.spec.ts` (extended) | SAML-proxy buttons render alongside OIDC; clicking calls `window.location.href = provider.loginUrl` (not `OAuthService.initCodeFlow`). |
| 6 | `SamlCallbackComponent.spec.ts` (new) | `?token=…&provider=…` is parsed, stored in localStorage, navigates to `/reservations`. |
| e2e (manual) | SimpleSAMLphp IdP + Apache+mod_shib container | Full round-trip on a developer machine. |
| e2e (manual) | DFN-AAI Test or local Shibboleth IdP | Federation-membership round-trip. |

Total: 8 new tier-3 MockMvc tests, 3 tier-1/2 unit tests, 2 Angular tier-6 tests, 2 manual e2e. Reuses [PRD 036](036-external-idp-oauth-login.md)'s `ExternalUserResolverTest` and `IssuerAwareJwtDecoderTest` (unchanged).

## Open Questions

1. **Shared secret between Apache and rapla?** Trust model relies on `trusted-proxies` IP check + Apache's own auth. Adding `X-Rapla-Proxy-Secret: <token>` would be defence in depth at the cost of admin-config surface area. **Tentative**: skip; document network-firewall + bind-loopback hardening clearly. Revisit if a deployment articulates a stronger threat model.

2. **Shibboleth session timeout vs rapla JWT expiry.** Apache + mod_shib maintains its own session (default 8h); rapla's bridged JWT is 1h. When rapla's JWT expires:
   - If Shib session still valid: SPA does NOT auto-reauthenticate through Shibboleth — bounces to `/app/login`, user clicks SSO button, Apache transparently reuses existing Shib session, rapla mints fresh JWT. One redirect, no re-prompt.
   - If Shib session also expired: same flow, but Apache redirects to IdP, IdP either reuses SSO cookies (silent) or prompts (visible). Standard SSO UX.
   Acceptable for v1. Silent reauth via "background iframe" probe is future enhancement.

3. **Auto-detect Apache via `X-Forwarded-Proto`?** Trust-boundary check uses peer IP. Accepting `X-Forwarded-By: <known-shibboleth-marker>` would be spoofable. Stick with IP check.

4. **Multi-deployment Apache.** If one Apache fronts multiple rapla deployments, `trusted-proxies` list is the same for all. Acceptable.

5. **Federation membership: WHO publishes SP metadata?** Apache+mod_shib publishes at `https://rapla.uni.de/Shibboleth.sso/Metadata`. Rapla's own URL becomes the SP entity ID. **Operationally cleaner than Keycloak brokering** (where Keycloak's URL would be the SP entity).

6. **Migrating users between Keycloak brokering and Shibboleth-via-proxy.** Existing `external-id` preferences are under `org.rapla.auth.external-id.keycloak` (or `.shibboleth`). Switching requires either re-resolving by email on next login (slightly slower but works automatically thanks to [PRD 036](036-external-idp-oauth-login.md)'s three-step lookup) or admin SQL/XML editing to copy preferences. Document this; no migration tool in v1.

## Effort estimate

| Item | LOC | Notes |
|---|---|---|
| `ShibbolethProvidersProperties` + `ShibbolethProvider` POJO | 150 | Boilerplate getters/setters |
| `TrustedProxyMatcher` (CIDR / IPv4+IPv6 matching) | 80 | Could use Spring's `IpAddressMatcher` |
| `ShibbolethAttributeMapAdapter` (headers → claims) | 50 | Read configured headers, build map |
| `ShibbolethHandshakeController` | 150 | Trusted-proxy check + resolver dispatch + JWT mint + redirect |
| `OAuthConfigController.buildProviders()` extension | 50 | Emit `type: "saml-proxy"` entries |
| `ProviderConfig` gains a `type` enum | 20 | Small refactor; OIDC entries are `OIDC` by default |
| Angular `OAuthProviderEntry` discriminator + click branch | 80 | `auth.service.ts` + `login.component.ts` |
| Angular `SamlCallbackComponent` + route | 80 | Parse token, store, navigate |
| Tests | 250 | Tier 1/2/3 + Angular tier 6 |
| Setup recipes (Apache + mod_shib, DFN-AAI, eduGAIN, IdP wiring) | 400 lines markdown | |
| **Total** | **~900 LOC + 400 lines docs** | + manual e2e setup time |

About **53% of the full-native-SAML budget** (~1700 LOC for option A in Appendix), and **all the SAML protocol heavy lifting is delegated to Apache + mod_shib**.

## When to build

Build when a deployment formally requests SAML federation AND either:

- They already run Apache+Shibboleth and want rapla to fit the same pattern (most common), OR
- They want SAML without Keycloak as a separate auth service for operational reasons (next most common), OR
- Their federation registration is in their own SP-metadata name and Keycloak brokering would shift it.

If the deployment is open to running Keycloak, **prefer [PRD 036](036-external-idp-oauth-login.md) §2.2 (Keycloak brokering)** — zero rapla code, same operational outcome.

If the deployment must avoid a reverse proxy entirely AND must avoid Keycloak — fall through to the native-SAML alternative in the Appendix.

---

## Appendix: Native SAML 2.0 SP in rapla (deferred fallback)

Kept as documentation for the rare case where a deployment cannot run Apache+mod_shib in front of rapla AND cannot run Keycloak as a SAML broker.

**Scope**: Spring Security SAML2 service provider inside rapla. SP metadata served by rapla, IdP metadata configured via URL or local XML, AuthnRequest sent from rapla, SAMLResponse validated in rapla (signature, audience, replay), then bridged to a rapla JWT exactly as the reverse-proxy approach does post-bridge.

**Cost**: ~1700 LOC + 400 lines docs. Roughly 3x the reverse-proxy PRD's footprint because rapla now owns the SAML protocol code:

- Spring Security SAML2 wiring (~200 LOC)
- SP metadata generation (~200 LOC)
- AuthnRequest init endpoint (~100 LOC)
- ACS endpoint with signature validation, replay protection, audience/recipient checks (~300 LOC)
- Single Logout endpoints (~150 LOC)
- SP signing key management + auto-generation + `RaplaKeyStorage` persistence (~200 LOC)
- The rest is shared with the reverse-proxy approach (resolver, JWT bridge, picker, callback).

**Why deferred**: Apache + mod_shib is the academic-IT standard. A deployment that won't run a reverse proxy is rare. Push back hard on the deployment about running Apache before committing to ~1700 LOC of SAML protocol code.

**When to revisit**: open as a fresh PRD (038?) if a real deployment articulates the constraint AND commits to funding the work.
