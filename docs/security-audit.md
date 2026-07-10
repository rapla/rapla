# Security decision audit — rapla (spring-boot branch)

**Generated:** 2026-07-10 · **Branch:** spring-boot · **Method:** automated sweep of 26
security-relevant PRDs + docs (558 security decisions extracted and classified), adversarial
code-grounding of **all 341** claimed-implemented controls (219 verified individually, the
remaining 123 in a batched pass), plus three cross-cutting passes (cross-doc contradictions,
unaddressed-surface sweep, code TODO/suppression scan) and first-hand code checks on the
highest-risk surfaces. **45 doc↔code drifts surfaced**; the highest-severity are consolidated below.

> **A0c phantom knobs — resolved 2026-07-10:** **#1 auto-provision**, **#2 local-accounts-enabled**,
> **#3 impersonation.enabled** are now *implemented* (see below). **#4 revoke-on-logout** — dead code
> binding retained, but all doc references removed (the knob does nothing). **#5 `typ:"impersonation"`**
> — not added: the RFC 8693 `act` claim is the standard
> impersonation signal and *is* emitted; `typ` as a claim is non-standard, so alerting should key on
> `act` (doc to be corrected, no code). **#6 client-secret restriction** — not enforced: Keycloak and
> any confidential OIDC provider legitimately hold a client secret, so the code (secret available to
> all provider types) is correct; the doc claim is wrong and to be struck.
>
> **Config knobs now live** (default = current behaviour, so no deployment changes unless opted in):
> `rapla.oauth.external.<id>.auto-provision` (gates new-user creation on external login),
> `rapla.oauth.local-accounts-enabled` (default true; false refuses the password grant with
> `unsupported_grant_type`), `rapla.auth.impersonation.enabled` (default true; false refuses both
> `/api/auth/impersonate` and `/api/auth/impersonate/switch`).

> **Docs reconciled 2026-07-10:** the §A/§F documentation issues were corrected across `deployment.md`,
> `sslconfig.md`, `graphql.md`, `authentication.md`, and PRDs [029](prd/029-swing-oauth-login.md)/[035](prd/done/035-graphql-foundations.md)/[036](prd/036-external-idp-oauth-login.md)/[051](prd/done/051-switch-user-with-oauth.md)/[071](prd/done/071-web-security-hardening.md)/[072](prd/072-server-side-login-dialog.md)/[076](prd/076-scoped-api-keys-self-rotation.md)/[097](prd/097-event-html-templates-mustache.md) — A0b
> (`FRAMEWORK`→`native`), A0 (GraphQL permitAll is the intended §12-gating model, not a 401), A3/F2
> (impersonation scope contradiction), A4 (offline_access is path-specific), A5/F3 (refresh-token
> storage: plaintext JWT / 21d / no rotation), A6/A7 (removed `id_token_hint`/`refreshUrl` mechanisms),
> F1 ([PRD 071](prd/done/071-web-security-hardening.md) CSP enforce via 102), F6 (no `GraphQlLeakTest`/DataLoader), F8 (097 cookie premise),
> F9 (api-key limits + 076 default-scope), and the new config knobs documented. F5 (thin csrf/upload
> doc coverage) and F4 (plaintext-password sunset — lives in a code comment, no doc to annotate) remain.

> **Fixes landed 2026-07-10 (test-first):** **A1** (password-grant now maps server faults to
> `server_error` + ERROR log, not `invalid_grant` — `PasswordGrantErrorMappingTest`); **A0d** (mutation
> controllers no longer leak existence — unreadable ids return `REFERENCE_NOT_FOUND`, pinned by
> `MutationExistenceLeakTest`); **F7** (`WhereEvaluator.nameContains` now `canRead`-gated — a dedicated
> reference-chain leak test is a recommended follow-up); **§G** (per-execution wall-clock deadline
> `GraphQlExecutionDeadlineInstrumentation`, default 30s via `rapla.graphql.execution-budget-millis`,
> `GraphQlExecutionDeadlineInstrumentationTest`). Phantom knobs (§A0c) and the doc reconciliations
> (§F) remain open.

> **Scope of confidence.** The doc/code **drift** findings in §A and the §F inconsistencies are
> grounded in code with file:line refs and are high/medium confidence. The §B inventory is what
> the docs *claim*. §D/§E are transcribed from the docs' own open-question / rejected sections.
> This is a **decision & documentation audit**, not a penetration test — no dynamic exploitation
> was performed. Where a finding says a config property "does not exist," it means a full-reactor
> grep found no binding — an operator setting it gets silence, not an error.

---

## A. Confirmed doc ↔ code drift (actionable)

These are places where a security doc asserts a control the code does **not** implement as
written. Ordered by risk. Each is a real "the next engineer will trust the doc and be wrong" trap.

### A0. `/api/graphql` is `permitAll()` — intended (resolvers are the auth boundary), stale comment — LOW
`docs/graphql.md` and `docs/prd/done/035-graphql-foundations.md:917` describe `POST /api/graphql`
as sitting behind Spring Security requiring a Bearer JWT and returning **401** to unauthenticated
callers. `SecurityConfig:161` actually puts `/api/graphql` in `permitAll()`, so a no-token request
returns **HTTP 200 with a GraphQL `UNAUTHENTICATED` error**, not a 401, and reaches the resolvers.
**This is by design, not a leak** — verified 2026-07-10:
- **Reads:** an anonymous request resolves to a **null user** (`JwtUserResolver.resolveCurrentUserOrNull`
  → null for `anonymousUser`/blank) → **empty §12 `canRead` scope**. Requiring `.authenticated()`
  would only narrow the *caller set* to "anyone who can get a valid token"; it would not change what
  data is exposed, because every field is `canRead`-gated per-request regardless of transport auth,
  and an authenticated non-admin hits the identical boundary. **Security belongs in the resolvers**,
  and that is where it is.
- **Writes:** fail closed — `requireCaller()` (`AllocatableMutationController:293-302`,
  `ReservationMutationController` same seam) throws `PERMISSION_DENIED "Mutations require an
  authenticated caller"` when the resolved user is null. No anonymous mutation.
- **Residual (genuine):** (1) the inline comment "[PRD 035](prd/done/035-graphql-foundations.md) testbed … Tighten to `.authenticated()`
  when real resolvers land" is **stale/misleading** — the resolvers landed and permitAll is now the
  intended design; the docs (graphql.md / [PRD 035](prd/done/035-graphql-foundations.md) §917) and the comment should be corrected to state
  that **§12 resolver gating is the auth model** and permitAll is deliberate.
- **Availability note (secondary):** the primary GraphQL availability risk is a query that runs
  10s+ and blocks the shared server (§G) — and requiring auth would *not* fix that, since a
  logged-in user can issue the same expensive query. That belongs to the execution-deadline fix in
  §G, not to the transport gate. Requiring auth here buys only attribution (a token to tie a runaway
  query to), which is minor next to actually bounding execution time. Optionally pin resolver
  discipline with an anonymous-request leak test — that guards the resolvers, not the transport gate.

### A0b. `deployment.md` instructs operators to reintroduce a fixed spoofing vuln — HIGH
The runtime deliberately sets `forward-headers-strategy: native` (`application.yml:18`, Tomcat
`RemoteIpValve`, trusts `X-Forwarded-*` only from RFC1918 internal proxies) — a comment at
`AuthorizationServerConfig.java:367` flags this as the **B5 fix**: `FRAMEWORK` had trusted *any*
caller's `X-Forwarded-*`. But `docs/deployment.md:108` (and `sslconfig.md:137`) tell operators the
default is / should be **`FRAMEWORK`**.
- **Impact:** an operator following the deployment doc sets `FRAMEWORK` and **reopens B5** — a
  direct client can forge `X-Forwarded-Host` to hijack the OAuth redirect-URI same-origin check.
- **Fix seam:** correct `deployment.md`/`sslconfig.md` to prescribe `native`; add a note on why
  `FRAMEWORK` is unsafe without an app-level trusted-proxy allowlist.

### A0c. Phantom security config knobs — documented controls with no code behind them — HIGH
A cluster of security switches the docs tell operators to set **do not exist in the source tree**
(full-reactor grep empty) or are bound but never read. Setting them yields *silent no-ops*:

| Documented knob / control | Doc | Reality |
|---|---|---|
| `rapla.oauth.external.<id>.auto-provision=false` (stop auto-provisioning) | [PRD 036](prd/036-external-idp-oauth-login.md) | Bound in `ProviderDef`, but `ProviderConfig.autoProvision()` has **zero callers**; `provision()` runs unconditionally. Only hosted-domain gates. |
| `rapla.oauth.external.local-accounts-enabled=false` (hide password path) | [PRD 029](prd/029-swing-oauth-login.md) | Property **does not exist** anywhere in the reactor. |
| `rapla.auth.impersonation.enabled=false` (disable impersonation entirely) | [PRD 051](prd/done/051-switch-user-with-oauth.md) | Kill switch **does not exist**; the endpoint is always reachable, gated only by `canAdminUser`. |
| `rapla.oauth.external.<id>.revoke-on-logout=true` | PRD [051](prd/done/051-switch-user-with-oauth.md)/[036](prd/036-external-idp-oauth-login.md) | Config binding is **dead** — `AuthService.signOut()` never revokes at the IdP. |
| `typ:"impersonation"` JWT claim for SIEM/ops alerting | [PRD 051](prd/done/051-switch-user-with-oauth.md) | `JwtConfig.issueImpersonationToken:360` sets `typ:"access"` — identical to a normal token. Alerting on `typ=="impersonation"` **never fires**; only the `act` claim distinguishes them. |
| Client-secret restricted to microsoft/google provider types | [PRD 072](prd/072-server-side-login-dialog.md) | `ProviderDef` exposes one `clientSecret` to **all** provider types; the restriction is unenforced convention. |

- **Impact:** the worst class of security-doc bug — an operator *believes* a control is active. The
  `local-accounts-enabled` and `impersonation.enabled` cases in particular mean documented
  hardening steps are inert.
- **Fix seam:** for each, either implement the gate or strike the knob from the doc. Add a config
  smoke-test asserting every property named in the auth docs binds to a live `@ConfigurationProperties`.

### A0d. Write-side existence leak on GraphQL mutations — MEDIUM-HIGH
§12 requires unreadable and non-existent ids to be indistinguishable. On the **read** side this
holds (`ResourceAccessQueryGraphQLTest` pins uniform empty/forbidden). On the **write** side it
does not: `AllocatableMutationController:157-168` and `ReservationMutationController:184-185`
resolve via `operator.tryResolve` (global, bypasses read scope) then split — a nonexistent id →
`REFERENCE_NOT_FOUND`, an existing-but-hidden id → `PERMISSION_DENIED`.
- **Impact:** an attacker probing `modifyAllocatable`/`modifyReservation` with guessed ids can
  distinguish "no such id" from "exists but hidden from me" — an existence oracle, the exact leak
  §12 forbids.
- **Fix seam:** collapse both to one indistinguishable error on the mutation path.

### A1. Password-grant error mapping collapses server errors into "bad credentials" — HIGH
`docs/authentication.md:277-292` says the OAuth2 password-grant provider distinguishes
auth-failure (`invalid_grant`) from server-fault (`server_error`, logged). That contract holds
only for the **form-login** provider (`AuthorizationServerConfig.raplaAuthenticationProvider`,
lines 512-583). The actual `grant_type=password` path is wired to an inner
`PasswordGrantAuthenticationProvider` (lines 907-975) whose credential-lookup catch block
(943-947) catches `Exception` and maps **every** failure — including DB / provisioner-store
errors — to `invalid_grant "Bad credentials"` with **no ERROR log**.
- **Impact:** a real server fault on `/oauth2/token` is returned as bad credentials and silently
  swallowed; users retype valid passwords, operators see nothing.
- **No test pins the `server_error` side** (`RemoteStorageErrorMappingIntegrationTest:98` only
  pins bad-creds → invalid_grant).
- **Fix seam:** narrow the catch in `PasswordGrantAuthenticationProvider` to auth exceptions;
  let others surface as `server_error` + `LOGGER.error`, mirroring the form-login provider. Add a
  regression test.

### A2. External-IdP `auto-provision=false` is a dead knob — HIGH
`docs/prd/036-external-idp-oauth-login.md` prescribes setting
`rapla.oauth.external.<id>.auto-provision=false` to stop "any verified Google account on Earth"
from becoming a rapla user. The property is bound (`ProviderDef:34,187`) and carried into
`ProviderConfig.autoProvision` — but **`ProviderConfig.autoProvision()` has zero callers**. Both
provisioning entry points (`OAuthExchangeController.mintRaplaToken:227`,
`OidcLoginSuccessHandler:117`) call `UserProvisioner.provision()` unconditionally.
- **Impact:** the documented mitigation is a no-op. The only real gate is hosted-domain
  enforcement (`ExternalUserResolver.enforceHostedDomain`, which *is* implemented and tested).
- **Fix seam:** gate `provision()` on `autoProvision()`, or delete the knob and document
  hosted-domain as the sole control. Add a test for `auto-provision=false`.

### A3. Impersonation invariant is inverted on the SPA cookie path — MEDIUM
`docs/authentication.md` (~1238-1258) claims impersonation tokens cannot invoke the impersonation
endpoints and that `GET /api/users` returns the *target's* scope "never the admin's". Code:
- `GET /api/users` → `UsersController.resolveRealActor()` returns the **admin's** scope (pinned by
  `UsersControllerLeakTest.usersListWhileImpersonatingUsesRealAdminNotEffectiveUser`) — the
  opposite of the doc sentence (the doc even contradicts itself two sentences later).
- `POST /api/auth/impersonate/switch` → `AuthCookieController.impersonateSwitch()` calls
  `resolveRealActor()` (154, 277-290), so an **impersonation cookie can re-escalate** to the
  original admin's full switch scope using only the impersonation token.
- The **Bearer** path (`ImpersonationController.impersonate`) and Swing `adminToken()` *do* enforce
  the stated invariant.
- **Impact:** deliberate (Review B2 chained-switch, CSRF+HttpOnly-mitigated) but the doc's literal
  control is false for the cookie surface. **Reconcile the doc**; the internally-contradictory
  paragraph is the real defect.

### A4. "Server never requests IdP refresh tokens" is false for the BFF/discovery surface — HIGH
`docs/authentication.md` / [PRD 072](prd/072-server-side-login-dialog.md) claim rapla "does not even request" IdP refresh tokens. True for
the server-side `oauth2Login` path (`RaplaClientRegistrationConfig` strips `offline_access`,
146-153). **False** for the discovery/exchange surface: `ProviderDef` defaults Microsoft to
`offline_access` (183) and Google to `access_type=offline` (250-256), and `OAuthConfigController`
advertises both **unfiltered** via `/api/auth/oauth/config` (210-211). A live test
(`OAuthConfigControllerExternalProvidersTest:139-140`) *asserts* `access_type=offline` is
advertised — pinning the opposite of the doc.
- **Impact:** the IdP *does* mint a refresh token; it transits the BFF exchange response and is
  discarded (not stored, refresh-grant rejected at the exchange endpoint). Residual: enlarged
  IdP-side offline-consent surface, and neither the `offline_access` strip nor the exchange
  refresh-grant rejection has a regression test, so a drift re-enabling relay would pass CI.

### A5. Refresh-session storage: plaintext JWT, no rotation, 21-day TTL — HIGH (doc overstates)
`docs/authentication.md` describes sha256-hashed storage + 30-day TTL + 7-day rotate-when-stale,
naming `AuthController`. The live `RefreshSessionService` **stores the full refresh JWT**
(documented "Why store the full JWT, not a hash?"), TTL is **21 days** (not 30), and there is **no
rotation** ("Never rotate at refresh time"). The one-session-per-user, server-side-revocable-on-
logout intent holds; the specific hardening the doc claims does not exist (superseded per [PRD 041](prd/041-openapi-runtime-removal.md)).
- **Impact:** a leaked refresh token is usable up to 21 days with no rotation-based theft
  detection; no per-device revocation (logout kicks all devices). Documented tradeoff — but the doc
  must be corrected to match.

### A6. Swing OIDC RP-initiated logout (`id_token_hint`) is documented but removed — MEDIUM (drift only)
`docs/prd/029-swing-oauth-login.md:29` describes logout appending `?id_token_hint=…` and opening a
`/connect/logout` tab. Code: `RaplaClientServiceImpl.logout()` (1344-1367) explicitly *no longer*
does this ("M2 broker model": `POST /oauth2/revoke` best-effort + `prompt=login` next time). The
captured `idToken` is **dead plumbing** — `RemoteConnectionInfo.getIdToken()`/`getLogoutUrl()` have
no main-code readers.
- **Impact:** upstream IdP SSO session is **not** ended at logout; survives until next
  `prompt=login`. No exploit beyond documentation drift, but "RP-initiated logout enforced?" → no.

### A7. [PRD 029](prd/029-swing-oauth-login.md) provider-aware refresh routing (`refreshUrl`) documented but deleted — LOW (drift only)
[PRD 029](prd/029-swing-oauth-login.md) asserts per-provider refresh routing + a named test
(`whenProviderRefreshUrlIsSet_thenRefreshHitsThatUrlNotRaplaSas`). Removed by [PRD 072](prd/072-server-side-login-dialog.md) Phase 5:
both refresh paths hardcode rapla's `/oauth2/token` (`MyCustomConnector:116-117`,
`ClientProxyConfig:304-305`); the test file pins the *opposite*
(`refreshAlwaysHitsRaplaOauth2TokenWithRaplaClientId`). No security impact (architecturally moot) —
pure stale-doc.

---

## B. Implemented controls (what IS in place)

Claimed-implemented decisions total **341** across the corpus. Coverage by area (extracted counts,
implemented + other statuses):

| Area | Items | Notable implemented controls |
|---|---:|---|
| authn | 136 | OAuth2 SAS (auth-code+PKCE, password, refresh, revoke), form login, API-key JWT, bcrypt password encoding, hosted-domain enforcement |
| authz | 88 | `PermissionController.canRead/canAdminUser`, additive permission resolver ([PRD 090](prd/090-additive-permission-resolution.md), max-wins), §12 read-scope filtering at output boundaries |
| data-leak | 60 | §12 output-boundary filtering, id-existence non-leak, tier-3 MockMvc leak tests |
| session/cookies | 53 | HttpOnly access_token cookie, CSRF cookie filter, single-session-per-user revocable on logout |
| api-keys | 49 | Scoped keys ([PRD 076](prd/076-scoped-api-keys-self-rotation.md)), self-rotation w/ graceMinutes, config token-gate, bootstrap strip |
| csp/xss | 34 | Path-scoped CSP (`RaplaCspHeaderWriter`): `/api`, `/rapla`, `/api/documents` enforced; `/app` enforced ([PRD 102](prd/102-browser-credential-hardening.md) Ph1); sandbox opaque-origin for rendered documents |
| injection | 33 | Parameterized JDBC, verbatim GraphQL key emission, no embeddable EL engine adopted |
| transport/tls | 14 | HTTPS connector config (`sslconfig.md`), signed JNLP/artifacts (`signing.md`) |
| dos | 27 | `LoginRateLimitFilter` (per-pod exponential backoff on `/login` + password grant) |
| secrets | 27 | YubiKey PKCS#11 signing, no secrets in committed config (per policy) |

**First-hand-verified implemented controls (this session):**
- **CSP `/app` enforce** — `RaplaCspHeaderWriter:80-86` enforces the SPA policy ([PRD 102](prd/102-browser-credential-hardening.md) Ph1). See
  §F1 for the important nuance (no `script-src` in the header).
- **Login rate limiting** — `LoginRateLimitFilter`/`LoginAttemptTracker` exist; keyed by IP+username,
  per-pod by documented design (§E accepted risk).
- **Password hashing** — `RaplaPasswordEncoder`/`LocalAbstractCachableOperator:175` write bcrypt;
  legacy `sha-1:`/`md5:` **and bare plaintext** accepted on verify + rehashed (§F4).
- **CSRF** — `CsrfCookieFilter` (XSRF-TOKEN cookie / `_csrf`), `ChangePasswordPageController`
  escapes + injects token.

---

## C. Planned, not yet built (by PRD)

97 decisions carry `planned` status. Highest-density active security PRDs:

- **[PRD 102](prd/102-browser-credential-hardening.md) — browser credential hardening** (15 planned; status *proposed, awaiting sign-off*):
  CSP script-src backstop (autoCsp/nonce+strict-dynamic) Phase 6, capability-token scoping for
  untrusted pages, interactive-document two-tier CSP model (D4-D9). Phase 1 (`/app` enforce) landed.
- **[PRD 060](prd/060-graphql-mcp-foundations.md) — GraphQL MCP foundations** (15 planned; *draft*): search-window DoS caps, §12 handling
  of half-readable conflict hits, bounded default time windows.
- **[PRD 037](prd/037-native-saml-shibboleth.md) — native SAML/Shibboleth** (12 planned; *draft*): trusted-proxy header trust model.
- **[PRD 074](prd/074-graphql-declarative-views.md) — GraphQL declarative views** (11 planned; *draft*): untrusted expression evaluation.
- **PRD 002 — multi-tenancy** (7 planned; *draft*): tenant isolation, ThreadLocal context clearing,
  tenant-ID validation, per-tenant resource caps.
- **PRD [076](prd/076-scoped-api-keys-self-rotation.md) / [043](prd/043-api-keys-jwt-pat.md) — API keys**: Phase 6 doc refresh; clock-skew tolerance; Angular key UI (043 is
  *in-progress, Angular UI incomplete*).

---

## D. Open / unaddressed surfaces (docs raise, nobody owns)

66 items are explicitly open questions or deferred-with-no-owner. Grouped by risk theme:

**GraphQL execution has no time bound — a single query can block the server (no owner; [PRD 062](prd/062-graphql-api-robustness.md) is a placeholder draft):**
- The real concern is **availability, not adversarial DoS**: a legitimate-but-expensive query that
  runs 10s+ ties up a worker thread and, on rapla's dispatch-locked shared store, can stall other
  work. No **per-request timeout / cooperative cancellation** exists ([PRD 062](prd/062-graphql-api-robustness.md) sketches a ~30s
  deadline + `AbortExecutionException`, unbuilt).
- **A depth/complexity cap is the wrong tool for this.** What hurts rapla is *shallow-but-wide*
  queries — the "42k-Person / 462k-dispatch" shape its own perf comments reference — which sail past
  a depth limit while still running for seconds. The load-bearing levers are: (1) a **per-request
  execution deadline** (an instrumentation that throws `AbortExecutionException` at the next field
  boundary once the budget is blown, stopping a runaway mid-flight), and (2) **result/window caps**
  to bound data volume up front. rapla has partial row caps (`ReservationGraphQLController:148`) and
  an optional window cap (`RaplaGraphqlProperties`) that **defaults null/off**. A depth cap is only a
  cheap secondary pre-filter for pathological *deep* queries.
- Also absent: mutation rate limiting, idempotency-key TTL, in-flight idempotency lock ([PRD 062](prd/062-graphql-api-robustness.md)).
- **This is the single largest unowned surface.** An expensive query on `/api/graphql` has nothing
  bounding its wall-clock.

**Authorization gaps:**
- **API-key reads are NOT scope-gated** ([PRD 076](prd/076-scoped-api-keys-self-rotation.md) + `authentication.md`): the scope axis bounds
  writes only; a `{read}` key reads everything its owner can. Documented known limitation.
- **Dispatch-path bypass** ([PRD 050](prd/050-external-auth-user-lifecycle.md)): endpoint guards don't cover bulk `dispatch(UpdateEvent)`; a
  non-admin can self-clear `authenticationSource` via that path.
- API-key Bearers **cannot drive GraphQL mutations** (caller resolved by `preferred_username`,
  absent on api-key JWTs) — functional gap, but signals inconsistent caller-resolution.

**Session / logout:**
- In-memory `OidcSessionRegistry` — server restart breaks back-channel logout (404) ([PRD 029](prd/029-swing-oauth-login.md)).
- Persistent-token "log out everywhere" admin revocation deferred ([PRD 029](prd/029-swing-oauth-login.md)).
- OQ1 ([PRD 102](prd/102-browser-credential-hardening.md)): JSESSIONID is `Path=/` ambient — unverified whether it reaches `/api`. Needs the
  tier-3 test the doc calls for.
- Sliding idle timeout deferred ([PRD 072](prd/072-server-side-login-dialog.md)).

**Multi-tenancy (all Phase-0 open, PRD 002 draft):** ThreadLocal cross-tenant leak risk,
static-per-tenant-state audit, tenant-ID format validation (feeds filesystem paths → traversal),
file-tenant data-dir rooting, per-tenant heap/DB-pool exhaustion.

**Auth defaults:**
- **Default admin ships with empty password** (`authentication.md`, `deployment.md`) — no
  production-mode lockout forcing a change. Operator-dependent.
- Legacy unsalted sha-1/md5 migration is rehash-on-login-only (no forced reset) — open question.

**iCal / external:** Shibboleth 8h session vs rapla 1h JWT mismatch accepted for v1 ([PRD 037](prd/037-native-saml-shibboleth.md)).

---

## E. Accepted risks / explicitly rejected (40 decisions)

These are **decided**, not gaps — but they define the residual risk surface and should be revisited
if the threat model changes:

- **No refresh-token rotation / reuse detection** ([PRD 072](prd/072-server-side-login-dialog.md), [PRD 102](prd/102-browser-credential-hardening.md) OQ2, `RefreshSessionService`) —
  leaked refresh token usable to TTL (21d). *Accepted.*
- **No instant access-token revocation** — 1h TTL accepted as the revocation window ([PRD 072](prd/072-server-side-login-dialog.md)).
- **GraphQL introspection kept fully on in production** — recon surface accepted ([PRD 035](prd/done/035-graphql-foundations.md)).
- **rapla logout does not propagate to upstream IdP** — `prompt=login` compensates (`authentication.md`).
- **No IdP-pushed revocation** — disabled IdP users retain access until refresh expiry ([PRD 036](prd/036-external-idp-oauth-login.md)).
- **API-key scopes bound writes only, not reads** ([PRD 076](prd/076-scoped-api-keys-self-rotation.md), PRD 031 originally rejected scopes
  entirely).
- **No forced max-TTL on API keys** — revocability substitutes (PRD [076](prd/076-scoped-api-keys-self-rotation.md) / [071](prd/done/071-web-security-hardening.md) H7).
- **Token-exchange replay window accepted, no nonce binding** (`authentication.md`).
- **Cross-pod idempotency race — accept-the-risk on record** ([PRD 062](prd/062-graphql-api-robustness.md)).
- **Embeddable EL engines rejected as a class** (SpEL/MVEL/JEXL/JSONata — RCE/DoS history; [PRD 075](prd/075-expression-language-standardization.md),
  097). CEL named as the future safe template.
- **Exception-wrap bug in `resolveJwtOrThrow` left in place** ([PRD 050](prd/050-external-auth-user-lifecycle.md)) — worth a second look; a
  knowingly-left auth-path bug.
- **SPA soft-shell CSP directives dropped / left report-only** ([PRD 071](prd/done/071-web-security-hardening.md)) — but see §F1: `/app` was
  since enforced by [PRD 102](prd/102-browser-credential-hardening.md), so this rejection is partly superseded.

---

## F. Documentation inconsistencies

### F1. [PRD 071](prd/done/071-web-security-hardening.md) is filed `done` but internally reads `IN PROGRESS`, and its CSP deferral is stale
`docs/prd/done/071-web-security-hardening.md` header says `**Status:** done (2026-06-21)` and
justifies a **report-only** SPA CSP as an accepted deferral. But its Phase 2 heading still says
`### Phase 2 — CSP + security headers … — IN PROGRESS`, and the deferral is **obsolete**:
`RaplaCspHeaderWriter:80-86` now *enforces* the `/app` SPA policy ([PRD 102](prd/102-browser-credential-hardening.md) Phase 1, 2026-07-10).
**Nuance worth documenting:** the enforced `/app` header carries **no `script-src`/`style-src`** —
those are delegated to Angular's `autoCsp` `<meta>` tag ([PRD 102](prd/102-browser-credential-hardening.md) Phase 6, still open per §D). So the
header enforces `connect-src`/`object-src`/`base-uri`/`frame-*`/`form-action` only; the anti-XSS
script directive rides on a meta tag. `/login` (server-rendered **with inline script**) and the
GraphiQL/Swagger explorers remain **report-only**. → Update 071 to point at 102 and close its Phase 2
+ Open Questions; a reader today concludes "CSP fully handled" incorrectly.

### F2. `authentication.md` self-contradicts on impersonation `GET /api/users` scope
Within ~1238-1258 the doc says the endpoint returns the target's scope "never the admin's" and then
a sentence later that it must reflect the admin's. Code returns the admin's (§A3). Pick one — the
code is right.

### F3. Stale PRD claims naming removed mechanisms (audit-by-doc will chase deleted code)
[PRD 029](prd/029-swing-oauth-login.md)'s `id_token_hint` logout (§A6) and `refreshUrl` routing (§A7); `authentication.md`'s hashed
refresh-token storage (§A5). All name mechanisms and/or tests that no longer exist.

### F4. Plaintext password acceptance lives only in a code comment
`RaplaPasswordEncoder`/`LocalAbstractCachableOperator:175` accept bare plaintext stored passwords on
verify (rehash-to-bcrypt after). This is a migration affordance with **no documented expiry** and no
open PRD item — it should have a sunset date, not live implicitly in a comment.

### F6. Claimed-but-nonexistent regression tests (drift that CI won't catch)
Several docs assert a control is "locked in by a dedicated test" where **no such test exists**:
- [PRD 035](prd/done/035-graphql-foundations.md) §1004 — five "locked" DataLoader cross-user-cache-leak rules + a `GraphQlLeakTest`:
  neither the DataLoader nor the test exists. Actual GraphQL leak protection is per-controller
  inline `canRead` (real, but not what the doc describes).
- The §12 "mandatory tier-3 MockMvc leak test for every new id-list/filter endpoint" is a
  **convention only** — unlike `ApiPrefixArchitectureTest` it is not mechanically enforced. Only
  3 `*LeakTest` classes exist; the GraphQL filter surfaces (`reservations(filter:)`, `search`,
  `allocatables(filter:)`, view resolvers) have no dedicated byte-identical leak test.
- API-keys-survive-external-providers (`authentication.md`) claims "a dedicated integration test
  locks it" — the named test does not exist; a `jwtDecoder`-bean refactor would break it unnoticed.
- [PRD 029](prd/029-swing-oauth-login.md) refresh-routing (`whenProviderRefreshUrlIsSet…`, §A7) and [PRD 051](prd/done/051-switch-user-with-oauth.md) override-sequencer/
  `sessionStorage` mechanisms (`051` describes a removed pre-PRD-072 Bearer architecture; the SPA
  now holds no token — `auth.service.ts:14`) name tests/paths that were deleted.

**Why this matters:** a doc that cites a nonexistent locking test invites a future refactor to
break the invariant with a green build. These need the test written or the claim struck.

### F7. Minor data-leak drift — `WhereEvaluator.nameContains` reference predicate
`WhereEvaluator:430-436` matches on a referenced entity's display name with **no `canRead` check**
(the gate exists only on the nested-`where` path, 442+). Low severity (admin-authored predicates,
building names), but it's an un-gated reference-name match on the stats/where path — worth closing.

### F8. [PRD 097](prd/097-event-html-templates-mustache.md) reasons from a premise [PRD 102](prd/102-browser-credential-hardening.md) reversed (cookie removal) — HIGH doc risk
`docs/prd/097-event-html-templates-mustache.md` repeatedly asserts the SPA token is memory-held and
the `access_token` cookie is gone (lines 98, 557-558, 584-585, and all of OQ7). `docs/prd/102-
browser-credential-hardening.md` **D1** *reversed* that (2026-07-09): the HttpOnly `access_token`
cookie **stays**; the memory-only token is rejected. 097's containment argument for sandboxed
document pages ("no ambient access credential on the origin") is therefore **false** — the cookie
*is* ambient. An implementer following 097 could conclude a forgotten `CSP: sandbox` header is
harmless when 102 (line 148) calls it "the one residual risk → full session ride." OQ7 is moot as
written. → Update 097 to 102's cookie model.

### F9. `authentication.md` "Current limits (v1)" contradicts its own shipped API-key sections — MEDIUM
`docs/authentication.md:767-779` ("Current limits (v1)") states API keys have **no per-key scopes**
("every key carries the issuing user's full access"), **no rotation**, and **no UI** — the exact
*opposite* of the [PRD 076](prd/076-scoped-api-keys-self-rotation.md) scopes/rotation sections **earlier in the same file** and the shipped
`/rotate` endpoint + SPA dialog. A reader landing on the limits block designs around
every-key-is-write_all — the security posture that was explicitly replaced with least-privilege.
Relatedly, **[PRD 076](prd/076-scoped-api-keys-self-rotation.md) internally contradicts itself on the default scope**: D8/OQ3 say missing
`scopes` ⇒ `write_all`; Phase 4 says missing `scopes` ⇒ `{read}` (`LEGACY_FULL` removed, pre-scopes
keys now read-only). The Phase-4 `{read}` default is current — a security-relevant contradiction
(fail-open `write_all` vs fail-safe `read`). → Strike the stale limits block; reconcile 076's D8/OQ3.

### F5. Thin doc coverage where controls exist
`csrf` (2 extracted items) and `file-upload` (1 item) are under-documented relative to their code
footprint (`CsrfCookieFilter`; [PRD 098](prd/done/098-server-artifact-store.md) upload endpoint + IMAGE MIME guard). Thin docs on a real
control = the next editor won't know the invariant they must preserve.

---

## G. Surfaces not addressed anywhere (no PRD, no doc, no TODO)

First-hand checks found these with no owning artifact (candidates for a new PRD or explicit
accept-the-risk record):

- **No GraphQL execution-time bound (query timeout / cancellation)** — the real risk is a query that
  runs 10s+ and blocks the shared, dispatch-locked server, not adversarial DoS (see §D). Confirmed
  absent: no per-request deadline, and the only `Instrumentation` bean is `RequestContextInstrumentation`.
  Worse than unowned: **`docs/prd/done/035-graphql-foundations.md` (§731, §1053) claims
  depth+complexity limiting is the shipped defense** — it is not, and depth limiting wouldn't cover
  the shallow-but-wide queries that actually run long anyway. *Highest priority.* Primary fix: a
  per-request execution deadline (`AbortExecutionException`) + turning on the result/window caps;
  a depth cap is only a cheap secondary pre-filter.
- **Path traversal via tenant-ID → filesystem** — raised inside PRD 002 (draft) only; no validation
  today, and multi-tenancy is unbuilt, so it's latent rather than live.
- **Per-pod-only login throttle** under the documented multi-pod topology — reasoned in
  `LoginAttemptTracker` javadoc (sticky LB), but no shared-store fallback if stickiness is lost.
- **bcrypt cost factor undocumented + no password policy / account lockout.** `RaplaPasswordEncoder`
  uses `new BCryptPasswordEncoder()` — **default strength 10**, no explicit/documented cost. There is
  **no password-complexity policy and no account lockout** (only the IP+username login throttle).
  Low severity given bcrypt + throttle, but genuinely unhandled and undocumented.
- **GraphQL query aliasing amplification** — named nowhere. `WhereEvaluator.DEPTH_CAP=10` bounds only
  *where-predicate* recursion, not overall query depth or alias multiplication. Part of the §G
  execution-time picture (alias fan-out is another way to run a query long).
- **`SecurityConfig:193` decoder-less fail-open** — `anyRequest().permitAll()` fires when no
  `JwtDecoder` bean exists (dev affordance). Harmless in the normal server profile (`JwtConfig`
  supplies the decoder), but a prod boot without a decoder opens the whole app. Worth a boot-time
  assert that production never starts decoder-less.

**Cleared by the TODO/suppression + unaddressed-surface passes (verified, not gaps):**
- **CORS is clean** — the old `allowedOriginPatterns("*") + allowCredentials(true)` reflect-any-origin
  bug is fixed (`CorsOriginPolicy`); reflects only loopback / WSL-bridge / configured origins.
- **Exchange-connector TrustAll TLS** (`EWSConnector:118-119`, `TrustAllStrategy`/`NoopHostnameVerifier`)
  is **gated behind `developmentMode`** (system prop `org.rapla.developmentmode`, default false) —
  not a prod hole unless the property is set.
- **URL-encryption `AES/ECB`** (`UrlEncryptor`) is legacy, **intentionally retained for byte-stability
  of existing external iCal subscriber URLs**; new exports use authenticated `AES-256-GCM`
  (`UrlCipherV2`). Documented compat tradeoff.
- **Admin endpoints** all enforce `user.isAdmin()` in-body (no `@PreAuthorize`, but the established
  pattern is present on every mutating admin path).
- Remember-me falls back to a **static key only when `RaplaKeyStorage` is absent** (degenerate
  no-keystore path); normal mode uses the persisted root key.

> **Still not verified** (would need a dedicated pass): XXE/entity-expansion in the XML `FileOperator`
> path; Jackson polymorphic-typing/deserialization exposure; open-redirect specifics in
> `RaplaOauthRedirectProperties`/`LegacyAppCallbackBridgeFilter` (the `X-Forwarded-Host` angle is
> covered by §A0b).

---

## Recommended priority

1. **§G** — **bound GraphQL execution time** so one query can't run 10s+ and block the shared,
   dispatch-locked server. Add a per-request execution deadline (`AbortExecutionException` at the
   next field boundary) and turn on the result/window caps that today default off. A depth cap is
   only a cheap secondary pre-filter — it misses the shallow-but-wide queries that actually hang.
   Correct [PRD 035](prd/done/035-graphql-foundations.md) §731/§1053, which wrongly claims this ships. Highest priority. (`/api/graphql`
   `permitAll` in §A0 is intended — leave it, or gate it only for attribution; it is not the fix.)
2. **A0b** (deployment doc reintroduces the B5 `X-Forwarded` spoof) — a doc fix that prevents an
   operator from reopening a fixed vuln. Cheap, urgent.
3. **A0c** (phantom config knobs) — implement or strike each; add a config smoke-test that every
   auth-doc property binds live. The `local-accounts-enabled` / `impersonation.enabled` /
   `revoke-on-logout` / `typ:"impersonation"` cases are inert documented hardening.
4. **A1** (password-grant error swallowing) + **A2** (dead `auto-provision`) + **A0d** (write-side
   existence oracle) — HIGH/MED, clean fix seams, each needs the missing regression test.
5. **F1/F2/F3/F6/A5/A6/A7** — documentation reconciliation: close [PRD 071](prd/done/071-web-security-hardening.md) Phase 2 (point to 102),
   fix the `authentication.md` impersonation contradiction, correct the refresh-token storage
   description, strike removed mechanisms (`id_token_hint`/`refreshUrl`/051 `sessionStorage`), and
   write-or-strike the claimed-nonexistent locking tests.
6. **F4** — give plaintext-password acceptance a documented sunset.
7. Optionally re-run the three cross-cutting passes (contradiction / unaddressed-surface /
   TODO-scan) that were cut for cost, to close the "not separately verified" list in §G (XXE,
   Jackson deserialization, OAuth open-redirect, CORS breadth, iCal-export auth).
