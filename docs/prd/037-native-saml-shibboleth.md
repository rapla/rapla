# PRD 037: Native SAML 2.0 / Shibboleth Support

**Status:** draft (build-only-if-asked)
**Date:** 2026-05-15

## Goal

Add native SAML 2.0 Service Provider support to rapla so deployments
that cannot use Keycloak SAML brokering (PRD 036 §2.2) can federate
directly with Shibboleth, ADFS, SimpleSAMLphp, or any other SAML 2.0
IdP. The resulting identity flows into the same user-resolution
pipeline introduced in PRD 036, and the SPA's API auth surface stays
JWT-Bearer based — SAML lives only at the edge.

> **Building scope**: only land this PRD when a real deployment has
> committed to using rapla and specifically cannot run Keycloak as a
> SAML→OIDC broker (PRD 036 §2.2 covers ~99% of academic deployments
> with zero rapla code). Native SAML is **~1500–2000 LOC of code that
> exists solely to avoid running Keycloak**. The cost-to-value ratio
> is poor in the speculative case — Keycloak brokering is operationally
> standard for HE/academic federations, has the same security and
> user experience, and adds one stable open-source component to the
> deployment. Reach for native SAML only when (a) a deployment has
> formal compliance / infrastructure / federation-membership reasons
> not to run Keycloak, (b) those reasons are documented, and (c) the
> deployment is funding the implementation work.

## Why this is needed

Per PRD 036 §2.2, the standard pattern for SAML federation in
modern stacks is to run Keycloak as a broker:

```
Browser → SPA → Keycloak (SAML SP) → Shibboleth IdP → SAML assertion
                ↓ Keycloak mints OIDC token
       ← OIDC token ← Keycloak
       
Subsequent API calls: Authorization: Bearer <Keycloak JWT>
```

This works for DFN-AAI, eduGAIN, InCommon, and every other SAML
federation rapla is likely to encounter. No rapla code changes needed
beyond PRD 036 Phase 2.1 (Keycloak as a generic OIDC provider).

There are narrow scenarios where Keycloak brokering may not be viable:

1. **Compliance / sovereignty mandate** forbids running a second
   open-source authentication component or any non-rapla service that
   touches user credentials.
2. **Federation-membership constraint** — some federations register
   SPs by metadata, and rapla's metadata (rapla.example.com) is what
   gets registered; routing through Keycloak would require Keycloak's
   metadata to be the SP-of-record, which the deployment can't
   organize.
3. **Operational simplicity at small scale** — for a deployment with
   one rapla server and one SAML IdP, an admin may prefer to avoid a
   separate auth service entirely.

In all three cases the deployment must articulate the reason in
writing before this PRD's work starts. The default answer is still
Keycloak brokering.

## Scope

### In scope

- **SAML 2.0 Web Browser SSO profile** — the workhorse SAML auth flow.
  SP-initiated authentication (rapla redirects browser to IdP) and
  IdP-initiated authentication (IdP POSTs unsolicited assertion).
- **HTTP-Redirect binding** for `AuthnRequest` (default), **HTTP-POST
  binding** for `SAMLResponse` (default). Other bindings out of scope.
- **Single Sign-On (SSO)** — assertion validation: signature, audience,
  recipient, NotBefore/NotOnOrAfter, InResponseTo, replay protection.
- **Single Logout (SLO)** — SP-initiated and IdP-initiated, via
  HTTP-Redirect binding.
- **Per-deployment SP metadata** generated at runtime from configured
  properties (entity ID, ACS URL, SLO URL, signing cert, etc.). Available
  at a documented endpoint (e.g. `/api/auth/saml/metadata`).
- **IdP metadata import** — either a single IdP metadata XML file or
  URL, OR a federation metadata aggregate (DFN-AAI, eduGAIN). Refresh
  on a schedule for aggregates.
- **Attribute mapping** — admin-configurable map from SAML attribute
  names to PRD 036's `ProviderConfig` claim slots
  (`username-claim`, `email-claim`, `external-id-claim`). Defaults match
  the academic conventions (`eduPersonPrincipalName` → `username`,
  `mail` → `email`, `eduPersonTargetedID` → `external-id`).
- **JWT bridge** — after SAML assertion validation succeeds, rapla
  resolves the identity to a rapla `User` (reusing PRD 036's
  `ExternalUserResolver`), mints a rapla-locally-issued JWT via the
  existing `JwtIssuer` (the one rapla SAS uses), and hands it back to
  the SPA. **The Angular SPA's `Authorization: Bearer <jwt>` flow,
  `IssuerAwareJwtDecoder`, and the entire downstream auth stack work
  unchanged.**
- **Picker integration** — each enabled SAML provider gets a button
  in the Angular login picker, sorted by `order` alongside the OIDC
  providers. Click → SPA navigates to rapla's SAML init endpoint,
  which builds the `AuthnRequest` and redirects to the IdP.
- **One SAML provider per rapla deployment** for v1. Repeat the
  config block to add more (if/when needed).
- **Auto-provisioning** behaviour identical to PRD 036 (default on,
  guarded by `hosted-domain` or equivalent attribute filter).
- **Setup recipes** in `docs/authentication.md` — Shibboleth IdP setup,
  DFN-AAI / eduGAIN registration, ADFS (Windows), SimpleSAMLphp.

### Out of scope

- **ECP profile (Enhanced Client / Proxy)** — used for non-browser
  clients (command-line tools, mobile apps that can't render the IdP
  page). Rapla's API clients use rapla-issued JWTs or API keys; ECP
  doesn't add value.
- **Holder-of-Key SubjectConfirmation method** — vanishingly rare.
- **Encrypted assertions** (XML-ENC of the entire assertion, NameID
  encryption) — supported by Spring Security SAML2 but adds key-mgmt
  complexity; defer until a deployment needs it.
- **SAML 1.1** — long deprecated, no real-world need.
- **Discovery service / WAYF** — required when multiple SAML IdPs
  must be offered to the same user (e.g. eduGAIN). For multi-IdP
  scenarios, redirect to Keycloak (PRD 036 §2.2) and let Keycloak's
  identity-provider selection screen handle the discovery. Native
  WAYF in rapla is significant additional UI work — not justified.
- **SAML Attribute Query Service (AQS)** — back-channel attribute
  lookup after assertion consumption. Rare; rely on assertion
  attributes.
- **Persisting SAML sessions** server-side — rapla's session model
  is JWT-based after the bridge. SAML SLO does not require server-side
  session tracking when the JWT is the only credential. *Caveat*:
  this means IdP-initiated SLO won't actually invalidate active rapla
  JWTs (the JWTs are stateless). The PRD 029 "Single token per user"
  pattern from `/api/auth/session` *could* be reused — see Open
  Question §3.
- **Native multi-tenant SAML** (one rapla, many SAML federations) —
  add via repeating the config block; full discovery/WAYF is the
  out-of-scope item above.
- **Migrating away from Keycloak brokering** to native SAML for an
  existing deployment — operationally fine but not part of this PRD's
  scope.

## Architecture

### Bridge SAML to JWT at the edge

```
                          (1) Browser navigates to /app/login
                              SPA fetches /api/auth/oauth/config
                              Picker shows "Sign in with university SSO"
                              (a SAML provider entry)
   
                          (2) User clicks the SAML button
                              SPA navigates to /api/auth/saml/login?provider=shibboleth
   
                          (3) Server (Spring Security SAML2 SP filter):
                              - builds AuthnRequest
                              - signs with SP private key
                              - HTTP-Redirect to IdP SSO endpoint
   
   Shibboleth IdP   ←──── (4) browser GET → IdP's SSO endpoint
                              (user signs in at IdP — Kerberos, password,
                               MFA, whatever the IdP enforces)
   
                  ────→   (5) IdP HTTP-POST SAMLResponse to
                              /api/auth/saml/acs/shibboleth
   
                          (6) Server (ACS endpoint):
                              - validate XML-DSIG signature against IdP's
                                public key from the metadata
                              - validate timestamps, audience, recipient,
                                InResponseTo, NotBefore/NotOnOrAfter
                              - replay check (NotOnOrAfter or jti-equivalent)
                              - extract attributes per the config mapping
                              - dispatch to ExternalUserResolver from PRD 036:
                                  external-id → email → auto-provision
                              - resolve to a rapla User
                              - JwtIssuer mints an access_token (and
                                optionally a refresh_token via existing
                                rapla SAS path) with sub = user.UUID
                              - HTTP-302 to /app/auth/saml-callback?token=<jwt>
   
                          (7) SPA's saml-callback route:
                              - parse the token from the URL fragment
                              - store in localStorage (matching the
                                angular-oauth2-oidc storage layout)
                              - navigate to /reservations
                              - clear oauthFailures, set activeProvider="shibboleth"
   
   Rapla API        ←──── (8) Angular API calls carry
                              Authorization: Bearer <rapla-locally-issued JWT>
                              JwtConfig.jwtDecoder() validates it (the
                              existing local path) → SpringSecurityRemoteSession
                              → User UUID lookup → request proceeds.
```

Key insight: **after step 6, rapla forgets about SAML entirely.**
The SPA holds a rapla-local JWT; the rest of the system thinks the
user authenticated via rapla SAS. The SAML protocol bits live in
exactly two endpoints (`/api/auth/saml/login/{provider}` and
`/api/auth/saml/acs/{provider}`) plus the SP metadata endpoint.

### Reuse from PRD 036

| Component | Reused? | Notes |
|---|---|---|
| `IssuerAwareJwtDecoder` (multi-issuer JWT routing) | ❌ | SAML assertions aren't JWTs. The local rapla decoder handles the bridged JWT. |
| `OAuthExchangeController` (BFF) | ❌ | No SAML equivalent. |
| `ExternalUserResolver` + 3-step lookup | ✅ | Operates on a `Map<String,String>` of claims — works for SAML attributes just as well as JWT claims. New `SamlAttributeMap → ProviderConfig claim` adapter feeds it. |
| "Keep the rapla User" architecture | ✅ | Same auto-provision + email-match + external-id pinning. |
| `JwtIssuer` (rapla SAS local JWT minting) | ✅ | Already exists for `/api/auth/login`. The SAML ACS endpoint calls it. |
| `application-local.yml` config pattern | ✅ | New `rapla.oauth.saml.<id>` block alongside the existing `rapla.oauth.external.*`. |
| Picker UI (`LoginPickerComponent`) | ✅ partial | Renders the SAML button. Click handler branches: for OIDC providers it calls `OAuthService.initCodeFlow`, for SAML it navigates the browser to the rapla SAML init endpoint. |
| Discovery endpoint (`/api/auth/oauth/config`) | ✅ partial | Emits SAML providers in the same `providers[]` array. New per-entry `type: "saml"` discriminator field; OIDC entries get `type: "oidc"`. SAML entries carry a `loginUrl` pointing at the rapla SAML init endpoint instead of an IdP authorize URL. |
| Per-provider `activeProvider` persistence | ✅ | Just stores the SAML provider's id; the SPA's logout/token logic branches on the id. |
| Auto-provision + `USERGROUP_CONFIG` defaults | ✅ | Identical. |
| Logout | ⚠️ | SAML SLO is a separate code path. SPA's `signOut()` branches on provider type: OIDC → `oauth.logOut()`; SAML → navigate to `/api/auth/saml/logout/{provider}` which initiates SLO. |

The rule of thumb: **~30% of the SAML auth flow's code is new SAML
protocol bits; ~70% reuses PRD 036 infrastructure** (user resolution,
JWT issuance, picker UI, config).

### Config shape

```yaml
rapla:
  oauth:
    saml:
      shibboleth:                 # provider id (admin-chosen)
        enabled: true
        # SAML SP identity
        sp-entity-id: https://rapla.example.com/saml/sp
        sp-base-url: https://rapla.example.com    # used to derive ACS / SLO URLs
        # SP signing key (private key for signing AuthnRequest, decrypting
        # encrypted assertions). Auto-generated on first start if blank;
        # admin can also provide a PEM file path.
        sp-signing-key: ""
        sp-signing-cert: ""
        # IdP metadata — either an explicit XML file path or a URL.
        idp-metadata-url: https://shibboleth.example.org/idp/shibboleth
        # idp-metadata-file: /etc/rapla/idp-metadata.xml   # alternative
        # If the metadata is a federation aggregate (DFN-AAI, eduGAIN), pick
        # the specific IdP by its entity ID.
        idp-entity-id: https://shibboleth.example.org/idp/shibboleth
        # Picker / UX
        display-name: "Sign in with university SSO"
        icon: shibboleth
        order: 15
        web-picker-visible: true
        # User-resolution
        auto-provision: true
        hosted-domain: ""         # if set, require an email or attr value
                                  # ending in this domain
        # SAML attribute → resolver claim mapping (defaults match the
        # academic conventions; override for ADFS / non-Shibboleth IdPs).
        attributes:
          username: urn:oid:1.3.6.1.4.1.5923.1.1.1.6        # eduPersonPrincipalName
          email:    urn:oid:0.9.2342.19200300.100.1.3        # mail
          external-id: urn:oid:1.3.6.1.4.1.5923.1.1.1.10     # eduPersonTargetedID
          name:     urn:oid:2.16.840.1.113730.3.1.241        # displayName
          given-name: urn:oid:2.5.4.42                       # givenName
          family-name: urn:oid:2.5.4.4                        # sn
```

### Discovery shape extension

PRD 036's discovery JSON gains a `type` field per provider entry:

```json
{
  "providers": [
    { "id": "rapla",      "type": "oidc", "tokenUrl": "...", ... },
    { "id": "microsoft",  "type": "oidc", "tokenUrl": "...", ... },
    { "id": "google",     "type": "oidc", "tokenUrl": "...", ... },
    { "id": "shibboleth", "type": "saml", "loginUrl": "https://rapla.example.com/api/auth/saml/login/shibboleth",
      "displayName": "Sign in with university SSO", "icon": "shibboleth", ... }
  ]
}
```

The Angular picker reads `type` to decide which click handler to
invoke. OIDC entries keep their `authorizeUrl`/`tokenUrl`/`jwksUrl`;
SAML entries only carry the rapla-side init URL (everything else lives
server-side).

## Plan

1. **Spring Security SAML2 dependency**
   - Add `spring-security-saml2-service-provider` to `rapla-app/pom.xml`.
   - Verify it doesn't conflict with the existing
     `spring-security-oauth2-resource-server` setup.

2. **Config + provider model**
   - New `SamlProvidersProperties` class with a
     `Map<String, SamlProvider>` (one entry per provider id). Keyed by
     admin-chosen string (`shibboleth`, `adfs`, etc.).
   - `SamlProvider` POJO with the fields shown in the Config shape
     section above.
   - `SamlProvider.toProviderConfig()` returns the same `ProviderConfig`
     type as PRD 036, plus SAML-specific extras (entity IDs, attribute
     map). `ProviderConfig` gets a `type` enum: `OIDC` (default) | `SAML`.

3. **SP key material**
   - On first startup, if `sp-signing-key` is empty, auto-generate an
     RSA keypair and persist via `RaplaKeyStorage` (same pattern as
     the existing rapla SAS JWK).
   - Expose the public cert in the auto-generated SP metadata.

4. **SP metadata endpoint**
   - `GET /api/auth/saml/metadata/{providerId}` → SP metadata XML
     (entity ID, ACS URL, SLO URL, certificate, supported bindings).
   - Admin downloads this and registers with the IdP / federation.

5. **AuthnRequest / SSO init endpoint**
   - `GET /api/auth/saml/login/{providerId}` builds an `AuthnRequest`
     using Spring Security SAML2's `Saml2RedirectAuthenticationRequestResolver`,
     signs it, encodes per the HTTP-Redirect binding, and redirects
     the browser to the IdP's SSO URL.

6. **ACS endpoint**
   - `POST /api/auth/saml/acs/{providerId}` validates the SAMLResponse:
     - XML-DSIG signature against the IdP's metadata public key.
     - Audience, Recipient, NotBefore, NotOnOrAfter, InResponseTo.
     - Replay protection (track recently-seen assertion IDs in the
       same system-preferences pattern as `RaplaTokenRepository`).
   - Extracts attributes per the configured map.
   - Calls `ExternalUserResolver.resolve(claims, providerConfig)` from
     PRD 036.
   - Calls `JwtIssuer.issueAccessToken(user.getId(), 3600)`.
   - Redirects to `/app/auth/saml-callback?token=<jwt>&provider=<id>`.

7. **Single Logout endpoint**
   - `GET /api/auth/saml/logout/{providerId}` builds and redirects a
     `LogoutRequest` to the IdP's SLO endpoint.
   - `GET /api/auth/saml/slo/{providerId}` handles the IdP's
     `LogoutResponse`, redirects browser to `/app/login`.
   - Clear the SPA's locally-stored JWT before redirecting (the SPA's
     SAML logout handler does this).

8. **Discovery endpoint update**
   - `OAuthConfigController.buildProviders()` adds enabled SAML
     providers to the same `providers[]` array, each with `type: "saml"`
     and `loginUrl` pointing at the rapla SAML init endpoint.
   - `clientId`/`tokenUrl`/`jwksUrl` etc. are not present for SAML
     entries.

9. **Angular picker integration**
   - `OAuthProviderEntry` interface in `auth.service.ts` gains a
     `type: 'oidc' | 'saml'` discriminator.
   - `signInWithProvider(providerId)` branches on `type`:
     - OIDC: existing path (reconfigure `OAuthService`, `initCodeFlow`).
     - SAML: `window.location.href = provider.loginUrl` (full-page
       navigation, no PKCE/state).
   - `signOut()` branches similarly: OIDC → `oauth.logOut()`; SAML →
     `window.location.href = '/api/auth/saml/logout/' + providerId`.
   - New route `/app/auth/saml-callback` reads the token from the URL,
     stores it in localStorage (matching angular-oauth2-oidc's storage
     keys: `access_token`, `expires_at`, `id_token`), redirects to
     `/reservations`.

10. **Setup recipes** in `docs/authentication.md`:
    - **Shibboleth IdP**: register rapla's SP metadata, configure
      attribute release for `eduPersonPrincipalName`, `mail`,
      `eduPersonTargetedID`, `displayName`.
    - **DFN-AAI / eduGAIN**: federation registration walkthrough,
      metadata aggregate URL configuration.
    - **ADFS** (Windows): RelyingPartyTrust setup, claim rules.
    - **SimpleSAMLphp**: minimal IdP setup for local testing.

11. **Manual e2e** against a real SAML IdP (DFN-AAI Test, eduGAIN
    sandbox, local SimpleSAMLphp, or local Shibboleth in Docker).
    Tagged `e2e`, manual only.

12. **PRD close**: when status hits `done`, `git mv` to `docs/prd/done/`.
    Cross-reference from PRD 036's §2.2 Shibboleth-via-Keycloak
    section to indicate native SAML is now also an option.

## Tests

| Tier | Test | What it locks in |
|------|------|------------------|
| 1 | `SamlProvidersPropertiesTest` | Env-var binding (`RAPLA_OAUTH_SAML_SHIBBOLETH_SP_ENTITY_ID` etc.), default attribute map, validation (entity-id + idp-metadata required when enabled). |
| 1 | `SamlAttributeMapAdapterTest` | Translates a `Map<String, List<String>>` of SAML attrs into the claim map shape `ExternalUserResolver` expects. |
| 2 | `SamlSpMetadataGenerationTest` | Generates valid XML SP metadata, contains the right entity ID, ACS/SLO URLs, signing cert. |
| 2 | `SamlSpKeyMaterialPersistenceTest` | First start auto-generates keypair and persists via `RaplaKeyStorage`; second start reuses it. |
| 3 | `SamlAuthnRequestEndpointTest` | `GET /api/auth/saml/login/{id}` returns a 302 to the configured IdP SSO URL with a signed `SAMLRequest`. |
| 3 | `SamlAcsEndpointHappyPathTest` (MockMvc + stubbed IdP keypair) | Valid signed SAMLResponse → user resolved → rapla JWT minted → 302 to `/app/auth/saml-callback?token=…`. |
| 3 | `SamlAcsEndpointRejectsBadSignatureTest` | Tampered SAMLResponse → 400 with no JWT minted. |
| 3 | `SamlAcsEndpointRejectsExpiredAssertionTest` | NotOnOrAfter in the past → rejected. |
| 3 | `SamlAcsEndpointRejectsReplayTest` | Same assertion ID submitted twice → second rejected (replay store works). |
| 3 | `SamlAcsAutoProvisionsUserTest` | Unknown user + auto-provision=true → new rapla User created with attributes from the assertion. |
| 3 | `SamlAcsAttributeMappingTest` | Custom attribute names in config map to the right claims. |
| 3 | `SamlLogoutEndpointTest` | SLO request builds and redirects; IdP response endpoint handles the LogoutResponse. |
| 3 | `DiscoveryEmitsSamlProvidersTest` | `/api/auth/oauth/config` includes the SAML provider entries with `type: "saml"` and `loginUrl`. |
| 3 | `RaplaJwtBridgeIntegrationTest` | After a successful SAML auth, the minted JWT validates via the existing `JwtConfig.jwtDecoder()` and unlocks rapla REST. |
| 6 | `LoginPickerComponent.spec.ts` (extended) | SAML provider buttons render alongside OIDC; clicking a SAML button calls `window.location.href = provider.loginUrl` (not `OAuthService.initCodeFlow`). |
| 6 | `SamlCallbackComponent.spec.ts` (new) | `/app/auth/saml-callback?token=...` extracts the token, stores in localStorage, navigates to `/reservations`. |
| e2e (manual) | Local SimpleSAMLphp IdP | Full round-trip on a developer machine without external dependencies. |
| e2e (manual) | DFN-AAI Test federation | Federation-membership round-trip. |
| e2e (manual) | ADFS | Windows-ecosystem round-trip. |

## Open Questions

1. **Should SAML logout invalidate active rapla JWTs?**
   The bridge mints stateless JWTs; SAML SLO doesn't naturally
   propagate to them. Three options:
   (a) Accept the gap: SLO clears the IdP session, rapla JWTs expire
       naturally (1h access token, 30d refresh — both can be revoked
       via JWK rotation if urgent).
   (b) Track active SAML-bridged JWTs server-side (extend PRD 029's
       single-token-per-user pattern), invalidate on SLO.
   (c) Move the bridged token to a server-side session cookie,
       invalidate on SLO.
   **Tentative**: (a) — matches OIDC behavior (Microsoft/Google logout
   doesn't invalidate active rapla JWTs either; existing tokens expire
   in ≤1 hour). (b) and (c) are bigger lifts.

2. **Federation metadata aggregate refresh.**
   DFN-AAI and eduGAIN publish ~20 MB metadata aggregates updated
   daily. Refresh on a cron (24h)? On every restart? Manual? Lean
   toward 24h scheduled refresh with a manual-trigger admin endpoint.

3. **SP signing key rotation.**
   The SP signing cert is registered with the federation; rotating it
   requires re-registration. Implement as: keep the old cert valid in
   metadata for an overlap window, allow admin to mark a new key as
   primary. Defer the implementation; v1 documents "rotate by
   regenerating + re-registering."

4. **Discovery / WAYF for native multi-SAML-IdP.**
   If a deployment needs to offer the user a choice of upstream SAML
   IdPs (e.g. multiple universities federating through one rapla),
   the natural UX is a WAYF (Where Are You From) screen. That's
   additional UI work in the picker. **Strong recommendation**: if
   multi-IdP is needed, run Keycloak as an internal broker (PRD 036
   §2.2) — Keycloak's identity-provider-selection screen is the
   battle-tested WAYF. Don't build a WAYF in rapla.

5. **Should the bridged JWT include SAML attributes?**
   The minted token's `sub` is the rapla user UUID. Should it also
   include `name`/`email`/`groups` claims from the SAML assertion?
   Pro: SPA can show display name without an extra `/userinfo` call.
   Con: claims drift if the user is renamed at the IdP between the
   SAML auth and JWT expiry. **Tentative**: include `name` + `email`
   only (visible on the rapla User entity anyway), skip groups (those
   live on the rapla User, not in the JWT).

6. **IdP-initiated SSO support.**
   Some SAML deployments (corporate intranets) link to the SP from
   a portal — the IdP POSTs an unsolicited assertion to the ACS
   without a prior `AuthnRequest`. Spring Security SAML2 supports
   this. Should rapla? **Tentative**: yes, low cost. Useful for the
   "intranet portal links to rapla" UX.

7. **ECP profile.**
   Out of scope per the Scope section. Re-evaluate only if a deployment
   has command-line API clients that need to authenticate via SAML
   (rare — they'd typically use rapla API keys per PRD 031 §6).

## Effort estimate

| Item | LOC | Notes |
|---|---|---|
| SAML SP filter chain + Spring Security SAML2 wiring | 200 | Mostly Spring config |
| `SamlProvidersProperties` + `SamlProvider` POJO | 150 | Boilerplate getters/setters |
| SP metadata generation + key persistence | 200 | XML generation via OpenSAML |
| AuthnRequest init endpoint | 100 | Thin wrapper over Spring Security SAML2 |
| ACS endpoint (validate + extract + bridge) | 300 | Heaviest piece — signature validation, attribute extraction, replay protection, bridge to `ExternalUserResolver` + `JwtIssuer` |
| SLO endpoint (request + response handling) | 150 | Symmetric with init |
| Discovery endpoint update + `type` discriminator | 50 | Small extension of PRD 036's `OAuthConfigController` |
| Angular picker branching | 80 | Discriminator + new click handler + SAML callback route |
| Angular saml-callback component | 80 | Parse token from URL, store, navigate |
| Tests | 400 | The tier-1/2/3 suite in the Tests section |
| Docs (4 IdP recipes) | 400 lines markdown | Shibboleth, DFN-AAI / eduGAIN, ADFS, SimpleSAMLphp |
| **Total** | **~1700 LOC + 400 lines docs** | + integration time for real-IdP testing |

This is ~3x the Keycloak (PRD 036 §2.1) cost (~400 LOC). The cost
difference is the SAML protocol bits — XML-DSIG, metadata, bindings,
replay protection — none of which are needed when Keycloak handles
SAML upstream.

## When to build

Only when a real deployment has formally requested it AND can articulate
why Keycloak brokering (PRD 036 §2.2) is not viable for them. Until
then, the recommended answer for SAML / Shibboleth is "use Keycloak
as a broker" — same operational outcome, ~5% of the implementation
cost, no rapla code changes beyond the PRD 036 Phase 2.1 Keycloak
provider.
