# PRD 072 — Server-side login dialog with provider chooser (SPA + secured pages)

**Status:** almost done — original scope **done (2026-06-20)** — all 7 phases shipped; Phases 5 + 6 verified live 2026-06-19/20, docs (Phase 7) in `docs/authentication.md`. The [PRD 097](097-event-html-templates-mustache.md)-driven **credential-hardening** discussion (2026-07-08/09) was **split out to [PRD 102](102-browser-credential-hardening.md)** on 2026-07-09 (keep the cookie, sandbox + capability for untrusted pages). 072 moves to `docs/prd/done/` once 102 is under way. See the 2026-07-09 follow-up at the end.

## Goal

Replace the SPA-piggyback login on the **browser** surfaces — the Angular SPA
(`/app`) and the secured API-explorer pages (`/graphiql`, `/swagger-ui`) — with a
**server-rendered login dialog** that:

1. offers the **provider chooser** (Keycloak / Microsoft / Google + username/password),
2. drives the OAuth **authorization-code flow server-side** (rapla as the OAuth client),
3. hands the browser a credential that works without the localStorage piggyback.

This decouples GraphiQL/Swagger from the SPA's `localStorage.access_token` (today a
bespoke, origin-bound, refresh-less, fake-logout hack — see [PRD 071](done/071-web-security-hardening.md) thread). It is also
the **BFF direction** from the [PRD 071](done/071-web-security-hardening.md) CSP research that structurally closes **H4** (JWT
out of `localStorage`).

**Phasing (2026-06-19) — full M2 program, explorer-first de-risking:** this PRD now carries the
**complete** migration to the single-issuer broker model (M2), not just the explorers. The driver:
`/api` can only drop multi-issuer trust once **every** token-presenting client (SPA + Swing) re-mints
to a rapla token — so committing both, plus a final single-issuer cutover, reaches the M2 end-state
cleanly with no prolonged multi-issuer provisorium. Order preserves de-risking: **explorers first**
(lowest-risk surface, proves the cookie path), **then SPA ∥ Swing**, **then the cutover last**.
Everything is still built **additively** alongside the running flows (reuse the public `rapla-app`
client, each client flips when green, free rollback); "additive" now means additive-build +
committed-cutover, not "SPA deferred".

## Scope

**In (this PRD — full M2 program):**
- A server-rendered login dialog + provider chooser, **driven by `oauth2Login()` reusing the existing
  public `rapla-app` PKCE client for Keycloak** (per-provider `ClientRegistration`s; Google/MS confidential
  via their `ExternalProvidersProperties` secrets). No new rapla-AS client (OQ #5).
- The **explorers** (`/graphiql`, `/swagger-ui`) use it instead of the localStorage piggyback (first).
- Credential delivery (cookie model A) + the cookie-based refresh.
- The **SPA** redirects to the same dialog and **drops in-browser PKCE / `angular-oauth2-oidc` +
  `localStorage`** (closes H4). Committed (no longer deferred), done after the explorer phase proves
  the cookie path; built additively with free rollback.
- **Swing (option A)**: its **external** IdP login re-mints to a rapla token via the exchange, so it
  too presents a rapla-issuer token at `/api` (stays native/Bearer; rapla-auth login unchanged).
- **Single-issuer cutover**: once SPA + Swing + explorers all present rapla tokens, drop the external
  issuers from `IssuerAwareJwtDecoder` → `/api` validates one issuer.

**Out (explicitly unchanged — additive guarantee):**
- The working **`rapla-app` public PKCE client** (Keycloak) — SPA `/app/auth/callback` +
  Swing loopback `/login/oauth2/code/rapla`. The server-side flow adds a *separate*
  confidential client; nothing about the working integration is modified.
- **Swing stays native/Bearer (not cookie).** Swing keeps its loopback `authorization_code` flow
  against rapla `/oauth2/authorize` (`SwingOAuthLoginFlow`, `/login/oauth2/code/rapla`) and gets a
  **Bearer** token, not a browser cookie. *Changed* under M2 + Option Y (see "In"): its per-provider
  menu (`OAuthConfig.providers[]`) collapses to a **single "SSO" entry** that opens the rapla `/login`
  page (chooser lives there now); rapla brokers the upstream IdP and returns a **rapla** Bearer.
  **No capability lost:** Keycloak/Google/MS stay fully reachable from Swing — the user just picks them
  on the page instead of the native menu.
- **iCal / API-keys** keep their existing Bearer/token model unchanged.

## Background — building blocks that already exist

Verified 2026-06-19:
- **`ExternalProvidersProperties`** carries `clientId` **and** `clientSecret` per
  provider → a server-side **confidential** OAuth client is possible with the existing
  config (no new provider config for already-secret-backed providers).
- **`enabledProviders()`** → the provider list; today feeds the SPA chooser + the Swing
  menu. Becomes the single source for the server chooser too.
- **`RefreshSessionService.issueAndPersist(user)`** → mints/persists rapla tokens server-side.
- **`OAuthExchangeController`** (`/api/auth/oauth/exchange/{provider}`) → already does the
  IdP-token → rapla-user → rapla-token exchange server-side (BFF).
- **No `oauth2Login()` yet** — the server is not currently an OAuth *client*; the external
  OAuth dance runs client-side in the SPA.

**Google/Microsoft feasibility:** server-side authorization-code (server = confidential
client) is the **standard** path for both, natively supported by Spring `oauth2Login()`.
For Google it is *cleaner* than today (opaque access token handled server-side via `id_token`).

## Plan

### Design

**One login page for every surface (DECIDED 2026-06-19).** A single server-rendered login page
(`/login`) that:
- **always** renders the **SSO chooser** — `enabledProviders()` as buttons;
- **optionally** renders the **legacy login** — the rapla username/password form — controlled by a
  single server config flag (see below);
- each provider button starts the **server-side** authorization-code flow via Spring `oauth2Login()`
  (the head — state/PKCE/nonce/`id_token` validation), with `ClientRegistration`s built from
  `ExternalProvidersProperties`; an `AuthenticationSuccessHandler` runs the rapla tail
  (provision + mint rapla JWT + set cookie/Bearer). See Open Question #2 (RESOLVED: Hybrid);
- on success, issues the credential (cookie for browsers; loopback Bearer for Swing — see below);
- **SPA, explorers, and Swing all land here** when unauthenticated.

**No two-surface separation, no Swing menu chooser (revised — Option Y, 2026-06-19).** The earlier
design split a chooser-free `/login` from a separate chooser entry page, on the assumption that Swing
kept its own per-provider menu. That assumption is **dropped**: Swing now has a **single "SSO" login
entry** that opens this same page; the per-provider `OAuthConfig.providers[]` menu + per-IdP URLs in
the client go away. rapla becomes Swing's **single federating Authorization Server** (broker): Swing
does `authorization_code` against rapla `/oauth2/authorize` → `/login` (unchanged loopback flow); the
**`/login` page now carries the chooser**, the user picks an upstream IdP there, rapla federates via
`oauth2Login()` and completes its own `authorization_code` back to Swing's loopback as a **rapla
Bearer**. The old "don't put the chooser on `/login`" reason evaporates because Swing no longer
pre-selects in a menu. One page, one chooser, reused by all surfaces.

**Legacy-login flag (the only remaining variation).** A single server config flag controls whether
`/login` also shows the username/password form (`SSO + legacy`) or **SSO-only** (chooser, no password
form). Default **on** (vanilla rapla, admin/offline); an SSO-mandating deployment (e.g. DHBW) sets it
**off**. **Safety: UI-only.** The flag hides the *form*; it must **not** disable rapla-SAS itself —
the admin bootstrap account, API keys, and the emergency direct-grant stay reachable so a broken IdP
can't lock a deployment out.

*Implementation note:* this is **Spring's native behaviour** — when both `formLogin()` and
`oauth2Login()` are configured, Spring Security's `DefaultLoginPageGeneratingFilter` already renders the
password form **and** the OAuth2 provider links on the same `/login` page. The legacy-login flag maps to
enabling/disabling the `formLogin` part; the custom page just styles what Spring already combines. No
separate page, no extra click.

*Two separate flags (review S3 — do NOT unify):* `rapla.oauth.swing-legacy-login` **already exists**
(default **off**) and gates the **Swing** password fallback; this PRD adds a **new** web flag (default **on**)
gating the `/login` password form for the **browser** surfaces. Different surfaces, different defaults — keep
them independent.

### Credential model — DECIDED 2026-06-19: A (stateless HttpOnly JWT cookie)

The token in an HttpOnly cookie is accepted ("der token darf ruhig im cookie stehen").
After a server-side login the browser needs a credential for `/api/**`. Three options
were considered; **A is chosen**:

| Option | How | REST-stateless? | localStorage gone? | Cost |
|---|---|---|---|---|
| **A — stateless HttpOnly JWT cookie** (recommended) | issue the rapla JWT in an `HttpOnly; Secure; SameSite` cookie; `/api` reads the JWT from the cookie or the `Authorization` header | **yes** (no server session store) | **yes** (H4 closed) | needs CSRF (SameSite + token) |
| **B — server session** | Spring default `oauth2Login()` session (JSESSIONID) | no — server session state | yes | rejected earlier for REST-statelessness |
| **C — in-memory mint-token** | session-gated `GET /api/auth/explorer-token` mints a short-lived Bearer; client holds it in JS memory | yes (`/api` stays Bearer) | partial (memory, not persisted) | needs a session for the mint gate |

**Revocation granularity — DECIDED 2026-06-19: no instant revocation required.**
The mainstream "token-handler" BFF (Duende, Curity) deliberately uses an *opaque session
id + server-side token store* to get **instant** revocation, and Duende explicitly
recommends *against* a self-contained JWT in a cookie. rapla picks the **minority
variant** (JWT in cookie) on purpose: a stateless self-signed JWT validates without a
shared session store, which fits the multi-pod topology (no process-shared session
state). The cost is that revocation is **not instant** — it lands at the **next refresh
boundary**. The user accepted this: *"wir brauchen keine sofortige revocation, 1 Stunde
access token länge reicht."* So the access JWT TTL is **1 hour**; clearing the single-slot
takes effect within ≤1 h. (rapla is thus a hybrid: stateless 1 h access JWT + a small
stateful single-slot refresh record giving revocation at the refresh boundary — not pure
stateless, not the instant-revoke opaque-session BFF. See Open Questions for the analysis
that produced this decision.)

**Why A.** It keeps `/api` stateless (the constraint locked in the [PRD 071](done/071-web-security-hardening.md) thread),
removes `localStorage` (no JS-readable token → not exfiltratable by XSS; the honest delta
is "not exfiltratable", not "token left the browser" — the JWT still lives in the browser,
just in a JS-unreadable cookie), is uniform for SPA + explorers, and works in the
`:4200`/`:8051` dev split (the cookie is same-origin, unlike the cross-origin localStorage
piggyback). Cost: CSRF protection (SameSite=Lax + double-submit token; Angular's
`HttpClient` XSRF support is built-in).

### Token-issuance model — DECIDED 2026-06-19: M2 (rapla re-mints; identity broker)

rapla **always mints its own rapla-JWT** after login — for **every** provider, including
Keycloak/Google/Microsoft. The external IdP authenticates *once*; its token is verified
server-side and then **consumed** — it never reaches `/api`. `/api` validates **exactly one
issuer** (rapla's own signing key, `RaplaKeyStorage`), statelessly.

This is the **identity-broker** pattern (not a token-relay BFF): rapla sits in front of the
upstream IdP and re-issues its own session — exactly like **Backstage** (auth backend issues its
own tokens with its own signing key after an upstream provider), **AWS Cognito**, **Auth0**, and
**Firebase**. Formally it is **RFC 8693 Token Exchange** at the login boundary (external
`subject_token` → verify signature/issuer/exp → mint rapla token); the condition that keeps a
"broker in front of a broker" legitimate — *verify the external token before re-issuing* — is met
by construction. (Today's code already does this for rapla-SAS logins; M2 extends it to IdP logins,
which today still pass the IdP `id_token` straight to `/api`.)

**What the rapla token IS — an access token, NOT an id_token.** The credential a client presents at
`/api` is a rapla-minted **access token** (`typ=access`): `iss` + signature = rapla's own key
(`RaplaKeyStorage`), `sub` = the rapla user **UUID**, plus `preferred_username` / `name` / (when
impersonating) the `act` claim. rapla is **both the Authorization Server and the Resource Server for
itself** — it issues the token *and* validates it at `/api` — so this access token carries the
identity directly (it *looks* id_token-ish in content, but structurally it is an access token used as
a Bearer). rapla also mints, separately: a `typ=refresh` token (used at `/oauth2/token` refresh +
`/api/auth/refresh`, **never** a `/api` Bearer), and — on `scope=openid` flows — a standard OIDC
**id_token** (the identity assertion *for an OIDC relying party*, also **never** the `/api` Bearer).
**Token-exchange shape:** an external **id_token** (identity proof, `aud`=rapla) → a rapla
**access token** (+ refresh). Input = identity assertion; output = API credential. An external
*access* token is the **wrong input** — its `aud` is the IdP's resource API (not rapla), and Google's
is opaque — which is exactly why rapla consumes the `id_token`, not the access token (see "Why M2"
below; the Google-opaque point is the second "Why M2" bullet).

**Why M2 over M1 (keep the IdP token as the `/api` credential):**
- **Multi-auth normalization.** rapla supports rapla-SAS (admin, API-keys, Swing default) **plus**
  ≥1 external IdP, so `/api` faces ≥2 issuers regardless. M2 collapses them to **one** issuer at
  `/api`; M1 needs per-issuer trust + JWKS + claim-mapping, growing with every provider/mandant.
- **Google.** Google's access token is **opaque (not a JWT)** — `/api` cannot validate it as a JWT;
  M1 would have to misuse the `id_token` as an API bearer (anti-pattern). M2 sidesteps it entirely.
- **Deployment portability.** Same `/api` model whether a deployment uses Keycloak, Azure-AD direct,
  or no external IdP.
- **Decoupling.** IdP token-lifetime quirks (Keycloak ~8 h SSO) are absorbed once at login.

**Note — Keycloak is itself a broker.** At DHBW, Keycloak already federates AD (the NTLM SPI) etc.
If *all* external IdPs federate **through** Keycloak, rapla sees few clean issuers and M2's marginal
value shrinks to "2 issuers → 1" — but rapla-SAS is an irreducible second issuer, and as a *product*
(deployments without Keycloak) M2's portability still pays. Whether Google/MS federate **through**
Keycloak or **direct** to rapla is a **per-deployment config choice**; M2 works identically either way.

### Refresh mechanism (cookie-based, reactive on 401)

JS can no longer read the token or its `exp`, so refresh is **server-driven and reactive**.
The login dialog sets **two** HttpOnly cookies:

| Cookie | TTL | Path scope | sent on |
|---|---|---|---|
| `access_token` (JWT) | **1 h** (DECIDED 2026-06-19) | `/api/**` | every API call |
| `refresh_token` | **21 d** absolute, non-sliding (DECIDED 2026-06-19) | **only** `/api/auth/refresh` | refresh calls only |

The path-scoped refresh cookie keeps the long-lived token off every API call.

Flow:
1. Client → `/api/...`; browser auto-sends the `access_token` cookie.
2. Access expired → `/api` returns **401**.
3. Client catches 401 → `POST /api/auth/refresh` (browser auto-sends the `refresh_token`
   cookie; JS never sees it).
4. Server validates the refresh token against the existing server-side single-slot
   (`org.rapla.auth.session`), mints a new access JWT via **`RefreshSessionService`**, sets
   a fresh `access_token` cookie (and rotates the refresh cookie if the IdP rotates).
5. Client replays the original request → succeeds.
6. Refresh token also invalid → `/api/auth/refresh` 401 → redirect to the login dialog.

- **SPA:** an Angular `HttpInterceptor` (the cookie-era successor to `RefreshOn401Interceptor`)
  does the 401 → refresh → replay; it reads/writes **no** tokens, only retries. Refresh is a
  state-changing POST → carries the CSRF token.
- **GraphiQL/Swagger:** the custom fetcher does plain `fetch` (cookie auto-sent); on 401 →
  refresh → replay; on refresh-401 → redirect to the dialog. The bespoke
  `getToken()`/Bearer-setting/"Drop token" code is removed.

Side effects vs today: **multi-tab is automatic** (cookies are shared per-origin → the
`storage`-event dance in `graphiql/index.html` disappears); **partial revocability returns**
(clearing the single-slot kills the next refresh → logout within one access-TTL); the
**access path stays stateless** (`/api` only validates the access JWT — only the *refresh*
endpoint consults the single-slot, which rapla already does today).

Optional: a non-HttpOnly `expires_at` cookie (timestamp only, no token) would let the SPA
refresh **proactively** before expiry instead of reacting to a 401. Not required.

### Revocation, session cap & IdP-token handling — DECIDED 2026-06-19: #7 = (a)

Under the broker model rapla **owns the session**, so the upstream IdP refresh tokens are
**discarded** (option (a), *not* (b) token-mediating). Cleanest form: on the **server-side
`oauth2Login()` path** rapla **does not even request** them — no `offline_access` (Keycloak),
no `access_type=offline`+`prompt=consent` (Google); only the `id_token` (identity) is needed.
**No external secrets at rest.** (Qualification: this holds for the `oauth2Login()` login path
only — the discovery/BFF exchange surface still advertises the offline scopes: `OAuthConfigController`
at `/api/auth/oauth/config` publishes Microsoft `offline_access` and Google `access_type=offline`
for the client-side PKCE/exchange flow, so a client using that surface *does* request IdP refresh tokens.)

| IdP token | Fate under (a) |
|---|---|
| `id_token` | verified once at login → user provisioned → discarded |
| access_token | not used, not stored |
| refresh_token | **not even requested** (no offline scopes) |
| rapla access/refresh | rapla mints + owns (single-slot, cookie) |

**Revocation happens *in rapla* (the broker), not at the IdP** — same as Cognito `GlobalSignOut`,
Keycloak session-invalidate, Firebase `revokeRefreshTokens`:

| Revoke triggered… | Effective | Mechanism |
|---|---|---|
| **in rapla** (admin disables / logs out a user) | **≤ 1 h** (next access refresh fails) | `clearSession` — the primary axis |
| **at the IdP** (DHBW disables the AD/Keycloak account) | at next forced re-federation (≤ cap) | only at the re-auth boundary |

**Session cap.** rapla's refresh token is **absolute & non-sliding** — `RefreshSessionService`
reuses the same token until expiry and never extends it on use. Lowered **30 d → 21 d**
(2026-06-19, `REFRESH_TOKEN_TTL_SECONDS`, regression-tested in `RefreshTokenTtlTest`) to bound the
IdP-disable worst-case window. At the cap the user is forced back through the IdP, where an
IdP-side disable is finally caught.

The one residual gap — an **IdP-only** disable of a user still present in rapla — is bounded by the
21 d cap; tighten further by mirroring the disable **into rapla** (deprovisioning, cross-ref [PRD 050](050-external-auth-user-lifecycle.md)
JIT provisioning) so the ≤ 1 h `clearSession` axis applies.

**Precedent — every brokered system does exactly this:** short access token + absolute refresh cap +
revoke-at-broker + upstream-disable-caught-at-re-federation. Keycloak (SSO Session Max 10 h), Auth0
(absolute lifetime), Cognito (`GlobalSignOut`), Firebase (`revokeRefreshTokens`). rapla sits at the
**conservative end**: longer cap (21 d), **no rotation**, **no idle timeout**.

**Optional hardening (precedent, not adopted now):**
- **Sliding idle timeout** *on top of* the absolute cap (Keycloak SSO Session Idle 30 min; Auth0
  inactivity lifetime) — kills abandoned sessions sooner. Real security gain, no multi-tab downside;
  rapla has only the absolute cap today. The strongest candidate if stricter behaviour is wanted —
  **DEFERRED 2026-06-19 ("idle timeout später"): a later phase/PRD, not in this PRD's scope.**
- **Refresh-token rotation** (Keycloak Max-Reuse=0; Auth0 default) — adds theft-detection but
  **reintroduces the multi-tab stampede (risk #1 below)**; rapla deliberately does **not** rotate
  (static single-slot token). Not recommended.

**(b) is reactivated only if** rapla ever needs to call upstream provider APIs server-side (Google
Calendar, MS Graph) — then it must store **and encrypt** the IdP refresh token (a new sensitive
persistence path; today `OAuthExchangeController` deliberately stores no IdP tokens server-side).

### Standards conformance + risks (2026-06-19 web research)

**Verdict: standard-conform.** The design matches IETF BCP *OAuth 2.0 for Browser-Based
Apps* (BFF "strongly recommended"), OWASP (never localStorage), and the **official Spring
position** (Joe Grandja, `spring-authorization-server#297`: browser apps → confidential-client
BFF). The reactive 401 interceptor is the Angular community de-facto standard.

**Deliberate, justified deviation:** rapla carries the rapla **JWT itself** in the HttpOnly
cookie + a client-driven reactive refresh, *not* the full Spring `oauth2Login` session-BFF
(server holds OAuth tokens, browser gets an opaque session id). This is the **right** call
for rapla: a self-signed JWT validates **statelessly** → fits the **multi-pod** topology;
the session-BFF would force a shared session store (Redis/JDBC) or sticky routing. Only the
single-slot refresh record is stateful and **must live in the shared store** (not process-local).

**Two mandatory risks to solve:**
1. **Multi-tab refresh race vs the single-slot record** — the most likely prod failure.
   Two tabs expire, both POST `/refresh`; with rotation the second presents an already-rotated
   token → reuse-detection revokes the slot → **both tabs logged out**. This is the direct
   consequence of the single-slot model (which also makes logout global — see logout policy).
   **Required:** `isRefreshing` + `BehaviorSubject` stampede-guard **plus** cross-tab
   coordination (Web Locks API / `BroadcastChannel`) **or** a short server-side **grace period**
   (accept the prior refresh token once more for a few seconds).
2. **CSRF / dev-proxy / relative-URL triad** — `SameSite` alone is insufficient; needs the
   XSRF token (Angular `withXsrfConfiguration` + Spring `CookieCsrfTokenRepository.withHttpOnlyFalse()`).
   Angular's XSRF interceptor **no-ops on absolute/cross-origin URLs** → use relative `/api`
   paths. Dev-proxy (`:4200`≠`:8051`) needs `proxy.conf.json` cookie rewrites. CVE-2025-66035
   (XSRF leak via protocol-relative URLs) is fixed in Angular ≥21.0.1 — **rapla on 22 is fine**.

**Secondary:** keep JWT claims slim (<4 KB or the cookie is silently dropped → silent logout);
optional proactive refresh (timer at ~80 % of access TTL) cuts 401 latency + stampede odds;
verify CSRF with a tier-3 test (mutating request without `X-XSRF-TOKEN` → 403).

### Logout policy — DECIDED: global ("logout everywhere")

rapla's `RefreshSessionService` is **single-token-per-user** (one slot, `org.rapla.auth.session`;
the code notes "no per-device revocation (logout kicks all devices)"). Logout clears the slot +
this browser's cookies → all devices fail their next refresh and drop within one access-TTL
(≤1 h; not instant, since the access JWT is stateless — accepted, no instant revocation
required, see Credential model). Per-device logout would require
multi-slot (per-session) refresh records — out of scope unless explicitly requested. The
single-slot is also what couples to risk #1 above.

### Phases

**This PRD (full M2 program; 4 ∥ 5 are parallel, 6 last):**
1. ✅ **DONE (2026-06-19).** **Server login dialog + chooser + per-provider `ClientRegistration`s.** Build the `oauth2Login()`
   `ClientRegistration`s from `ExternalProvidersProperties` — **Keycloak reuses the public `rapla-app`
   PKCE client** (`client-authentication-method=none`, callback `…/login/oauth2/code/keycloak`, already
   accepted by Keycloak's wildcard — no Keycloak change); **Google/MS confidential** via their secrets.
   No new rapla-AS client (OQ #5). Render the combined `/login`: `enabledProviders()` buttons (always) +
   the inline password form (legacy-login flag). Wire it to the `oauth2Login()` HEAD + the
   `AuthenticationSuccessHandler` TAIL (#2 Hybrid). Add `/login/oauth2/code/**` to permitAll (the list
   has bare `/login`, not `/login/**` — review S1). Tier-3 MockMvc: chooser renders configured providers;
   provider button → 302 to the IdP authorize URL.
2. ✅ **DONE (2026-06-19).** **Credential model (A) + refresh + identity/impersonation endpoints.** Issue the stateless HttpOnly
   JWT cookie + path-scoped refresh cookie at login success; make `/api` accept the JWT from the cookie
   **and** the `Authorization` header; cookie-based reactive 401 refresh via `RefreshSessionService`.
   **New cookie-shaped endpoints this phase OWNS (review B2 — the SPA cannot read the HttpOnly cookie, so
   it must ask the server):**
   - **`GET /api/auth/me`** — server reads+validates the cookie, returns the current identity (username,
     name, roles, impersonation state) as JSON. Replaces the SPA's old client-side JWT decode. §12 leak-tested.
   - **`POST /api/auth/impersonate/switch` + `/api/auth/impersonate/end`** — cookie-shaped impersonation
     (set/clear an impersonation cookie server-side after `canAdminUser`), replacing the token-returning
     `POST /api/auth/impersonate` (which stays for the Bearer/Swing path). JS can't hold/swap tokens under cookies.
   **CSRF (review B1):** key the `CookieCsrfTokenRepository` matcher on the **new `access_token` cookie
   specifically** (not "any cookie/JSESSIONID") so header-Bearer + the current SPA's session-cookie POSTs are
   unaffected. **Entry-point (review S2):** pin it explicitly — `/api/**`+JSON/Bearer → **401**, HTML navigation
   → **302→/login** — so adding `oauth2Login().loginPage("/login")` doesn't flip the explorer 401 to a 302.
   Tests: cookie issued; `/api` auth via cookie; `/api/auth/me` identity + leak test; refresh on 401; CSRF only
   on `access_token`-cookie requests (regression: header-Bearer + bare-session POST still 200); 401-vs-302 split.
3. ✅ **DONE (2026-06-19).** **Explorers onto the dialog (first, lowest-risk proof).** Drop the `localStorage.access_token`
   piggyback in `graphiql/index.html` + `swagger-ui/index.html`; plain `fetch` + cookie;
   remove the bespoke `getToken()`/`logout()`/"Drop token". Verifies the whole cookie path on
   a low-risk surface (dev tools only).
4. ✅ **DONE (2026-06-19).** **SPA onto the dialog (closes H4) — clean rebuild, NO backward-compat (DECIDED 2026-06-19).** The SPA
   is a clean rebuild on the cookie model: redirect to the server dialog when unauthenticated; consume the
   cookie; identity via `GET /api/auth/me`; impersonation via the cookie switch/end endpoints; Angular
   `HttpInterceptor` does reactive-401 refresh; **remove `angular-oauth2-oidc` + all `localStorage` token
   handling**; chooser becomes the server chooser. **No backward-compat needed: the SPA ships *with* the
   server (same `/app` release), so there is never an old-SPA-vs-new-server state in prod** — this downgrades
   review B1 from "blocker" to "keep CSRF scoping clean" (no live-SPA breakage window). **Swing remains the
   real transition constraint** (separately distributed → the Phase-6 cutover still waits for Swing). CSRF
   (review S5): use **relative `/api` URLs**, `withXsrfConfiguration`, and a `proxy.conf.json` that forwards
   `X-Forwarded-Host/Proto` so the dev `:4200`↔`:8051` split keeps cookies same-origin and the OAuth callback
   `{baseUrl}` resolves to the proxy origin. Tests: unauth `/app` → dialog; cookie auth on `/api`;
   `/api/auth/me` drives login state; refresh on 401; **no token in `localStorage`**.
   *Phase-4 review should-fix landed in the same change:* sign-out was a `GET /logout` that Spring's
   `LogoutFilter` (POST-only, clears `JSESSIONID` only) never handled and which is unaware of rapla's
   stateless auth cookies → cookies survived, user stayed logged in. Fixed with a dedicated
   `POST /api/auth/logout` (`AuthCookieService.logout()` / `AuthCookieController`) that EXPIRES both the
   `access_token` (Path `/`) and `refresh_token` (Path `/api/auth/refresh`) cookies and invalidates the
   session; `/api/auth/**` is `permitAll` so it works even with an expired token (idempotent). The SPA
   `signOut()` now POSTs it (XSRF auto-attached) then lands on `/login`. Tier-3 MockMvc + Vitest covered;
   OpenAPI specs regenerated. **Server-side decision still open (flagged, NOT worked around):**
   `UsersController.list()` resolves the *effective* user, so the "switch to user" candidate list degrades
   while already impersonating (switch *authorization* stays correct via `resolveRealActor` → `act.sub`).
   Needs a server change to list against the real actor — own decision, not part of this SPA phase.
   *Second Phase-4-gate finding (fixed):* the browser **form-login** (`POST /login`, username +
   password) had **no rapla TAIL** — it established only a `JSESSIONID` session, no `access_token`
   cookie. The stateless `/api` ignores the session → `GET /api/auth/me` 401 ("No user found in
   session") → SPA bounced straight back to `/login` (login loop); the GraphiQL/Swagger explorers
   showed "authenticated via session cookie" yet every `/api/graphql` POST was `UNAUTHENTICATED`; and
   sign-out errored. Root cause: Phase 1/2 wired cookie-minting only for the password grant
   (`/oauth2/token`) and the OIDC login (`oauth2Login()` → `OidcLoginSuccessHandler`), never for
   `formLogin()`. Fixed with `FormLoginSuccessHandler` (the form-login counterpart of the OIDC TAIL):
   resolves the rapla `User` from the principal (the `UserDetailsService` sets the principal name to
   `user.getId()` → resolve by id with username fallback), `issueAndPersist`, sets both cookies, then
   `SavedRequestAwareAuthenticationSuccessHandler` redirect. Wired in `SecurityConfig.formLogin` via an
   `ObjectProvider<FormLoginSuccessHandler>` (unconditional `@Bean`; a `@ConditionalOnBean` on a plain
   `@Configuration` evaluated unreliably and silently dropped the handler). Tier-3 MockMvc covered.
   **Browser e2e gate PASSED** (Playwright, live server + ng serve): `/app`→`/login`→form login→
   cookies→SPA at `/app/reservations` with live `/api` data→cookie-auth `/api/graphql` 200 (the
   deferred Phase-3 CSRF positive round-trip, real browser)→sign-out→`/api/auth/logout`→cookies
   cleared→`/api/auth/me` 401→`/login`. `access_token` HttpOnly (JS-unreadable). **Carry-over note:**
   stale `angular-oauth2-oidc` localStorage entries (`id_token`/`refresh_token`/`PKCE_verifier`/
   `rapla.oauth.activeProvider`) survive in browsers that ran the old SPA — the new SPA never reads
   them (Vitest grep + live cookie-only operation confirm), but a one-time cleanup on first new-SPA
   load would tidy them.
5. ✅ **DONE (2026-06-19).** **Swing (option A + Y) — rapla becomes Swing's single IdP.** Collapse the per-provider menu
   (`OAuthConfig.providers[]` + per-IdP authorize/token URLs) to a **single "SSO" entry that is the
   default Swing login action** (the rapla password / fallback path stays available but is no longer
   the default), opening rapla `/oauth2/authorize` → `/login` (chooser lives there; rapla federates
   the upstream IdP
   server-side via `oauth2Login()` and completes its own `authorization_code` back to the loopback as
   a **rapla** Bearer). `SwingOAuthLoginFlow.exchangeCodeForTokens` then **always** hits rapla
   `/oauth2/token` — no per-provider branching. Drop the IdP-refresh-routing branch in
   `RefreshOn401Interceptor.doRefresh` + `MyCustomConnector.refreshUsingToken` + the
   `refreshUrl`/`oauthClientId` external special-case in `RemoteConnectionInfo` — **all** logins
   refresh against rapla (significant net code removal; Swing only ever talks to rapla). **Review B3:
   include `RaplaClientServiceImpl:489`** — the 4-arg `ConnectInfo(access, refresh, refreshUrl, oauthClientId)`
   that captures the admin session across a context restart on impersonation **switch-back**; deleting the
   4-arg ctor + `getRefreshUrl/getOauthClientId` accessors breaks it, so collapse this site to the 2-arg form
   (rapla is now the only token endpoint) and verify switch-back still works. The Swing password form is gated
   by the **existing** `rapla.oauth.swing-legacy-login` flag (default off) — a **separate** flag from the new
   web one (review S3). Confidential external providers now work for Swing (secret stays server-side; Swing
   never sees it). Independent of phase 4 → parallel. Tests: Swing SSO login (rapla-brokered Keycloak) yields a
   rapla-issuer Bearer; refresh hits rapla's `/oauth2/token`; rapla-password login + impersonation switch-back unchanged.
   *Landed (2026-06-19):* `ConnectInfo` collapsed to the two-token form (4-arg ctor + `getRefreshUrl`/`getOauthClientId`
   removed); `RemoteConnectionInfo` dropped `refreshUrl`/`oauthClientId`; `MyCustomConnector.refreshUsingToken` +
   `RefreshOn401Interceptor.doRefresh` now always POST rapla `/oauth2/token` with `client_id=rapla-client` (provider
   branch deleted); `RaplaClientServiceImpl` lost the provider dropdown (`configureLoginMethods`/`applySavedLoginMethod`/
   `SWING_OAUTH_PROVIDERS`/`providerLabel`) — replaced by a single SSO method (`configureLegacyDialogMethods`) that points
   at the top-level rapla config; `fetchOauthConfig` no longer parses `providers[]`; the switch-back capture at
   `RaplaClientServiceImpl:489` uses the 2-arg `ConnectInfo`; `TokenStore.KEY_REFRESH_URL`/`KEY_OAUTH_CLIENT_ID` removed.
   Default Swing login remains the auto-fired rapla SSO flow; the password form stays gated by the existing
   `rapla.oauth.swing-legacy-login` flag. Tests: `ConnectInfoTest` (collapse), `MyCustomConnectorReauthTest` (refresh →
   `client_id=rapla-client`), `RefreshOn401InterceptorAuthDeadTest.refreshAlwaysHitsRaplaOauth2TokenWithRaplaClientId`.
   **Not yet live-verified in a running Swing client** (server/Swing held by the user testing live) — needs a manual
   rapla-brokered Keycloak SSO login + impersonation switch-back smoke test.
6. ✅ **DONE (2026-06-20). Single-issuer cutover (last).** `/api` now trusts **only** rapla-issued
   tokens by default. External-issuer trust is gated behind a new flag
   `rapla.oauth.trust-external-issuers` (**default `false`** = cutover is the default). In
   `JwtConfig.buildBaseDecoder` (now 4-arg, `trustExternalIssuers`): when false the decoder is the
   bare local rapla `NimbusJwtDecoder` even with providers enabled (no `IssuerAwareJwtDecoder` /
   pattern routes wired); when true the legacy multi-issuer decoder is built (escape hatch). The
   local-only path now also pins the configured `rapla.oauth.issuer` so a foreign-iss token can't pass
   it. Rationale (non-circular, three points):
   - **§16 — identity resolution/provisioning off the read path.** While `/api` accepted external
     tokens, every call resolved the external identity (`JwtUserResolver.resolveExternal` →
     `ExternalUserResolver`) and, with `auto-provision: true`, *created* the user on the read path —
     the 2026-05-28 `RaplaNewVersionException`→401 bug. With single-issuer the rapla token already
     carries `sub=<UUID>` → a plain lookup; provisioning happens once at login (write path).
   - **One trust anchor instead of N** — validate one signature/issuer, not rapla-SAS + Entra + Google
     + Keycloak (no multi-tenant pattern-issuer edge cases, smaller attack surface, fewer JWKS deps).
   - **Consistent token contract** — every `/api` token has `sub=UUID` + rapla claims + the `act`
     impersonation model; no "which kind of token" branching in resolvers/controllers.

   **`OAuthExchangeController` — investigated, left untouched (no work invented).** It still forwards
   the raw IdP token response to the caller (and provisions as a side effect, [PRD 050](050-external-auth-user-lifecycle.md)). But the shipped
   SPA (Phase 4) is fully cookie-based and dropped `angular-oauth2-oidc` + client-side PKCE, so **no
   live client calls `/api/auth/oauth/exchange/*` anymore** (no Angular `.ts` reference; only docs,
   `OAuthConfigController`'s legacy chooser config, and `OAuthExchangeStaleTokenAnonymousTest`).
   Minting a rapla token there would be work for a dead path. The cutover invariant is upheld a
   different way: nothing rapla *currently hands a client* is a raw IdP token — the SPA/explorers get
   the rapla cookie, Swing re-mints to a rapla Bearer (Phase 5), iCal/API-keys are rapla tokens. The
   only residual is that the *legacy* exchange endpoint, if a third party still POSTs to it, returns a
   raw IdP token that now fails `/api` — see the risk note below; minting-or-removing it is a clean
   follow-up, not a cutover blocker.

   Tests: `JwtConfigSingleIssuerCutoverTest` (tier-1: default rejects external-iss + bare-local-decoder
   identity, accepts rapla token incl. configured-issuer branch; opt-in wires the issuer-aware decoder)
   and `SingleIssuerCutoverApiTest` (tier-3 MockMvc, Keycloak enabled + default flag: rapla token → 200,
   foreign-iss Bearer → 401). `JwtConfigTokenTypeTest` updated to the 4-arg call.

   **Risk note:** the legacy `OAuthExchangeController` (`/api/auth/oauth/exchange/{provider}`) and the
   `tokenUrl` it advertises in `OAuthConfigController` for confidential providers still return a raw
   IdP token. No first-party client uses them post-Phase-4, but a stale/3rd-party SPA doing client-side
   PKCE would now get a token that 401s at `/api`. Follow-up (Phase 7 or a small PRD): mint a rapla
   token there via `RefreshSessionService.issueAndPersist`, or retire the endpoint.
7. ✅ **DONE (2026-06-20). Deployment + docs.** Redirect-URI registration is **NOT** a blanket no-op:
   the **fremdverwaltete DHBW prod realm** (`login.mosbach.dhbw.de`, realm `dhbwmos-lehre`) only
   whitelists the **legacy** `/app/auth/callback` for localhost and the maintainer has no admin to add
   the conformant `/login/oauth2/code/keycloak` — so it needs the **TEMPORARY dev bridge**
   (`rapla.oauth.web.dhbw-legacy-callback=true` → keycloak `ClientRegistration` sends the legacy URI, and
   `LegacyKeycloakCallbackBridgeFilter` on `:8051` + the ng-serve proxy on `:4200` route it back onto
   `/login/oauth2/code/keycloak`; remove once DHBW IT registers the conformant URI). The earlier "no-op"
   assumption held only for the dev `172.24.157.92:*` host wildcard, **not** for the prod realm. Any
   *stricter* third-party Keycloak that pins exact redirect URIs likewise needs `…/login/oauth2/code/keycloak`
   added. **`docs/authentication.md` updated (2026-06-20)** with the full server-side model below:
   — this is a hard requirement, the whole server-side model must land there:
   - the **single login page** (`/login`) for all surfaces (SPA + explorers + Swing): SSO chooser
     always shown + optional legacy password form (the config flag, UI-only); rapla as Swing's single
     federating AS (Option Y — Swing's per-provider menu removed, loopback Bearer kept);
   - the **token-issuance / identity-broker model (M2)**: rapla re-mints its own JWT for every
     provider, `/api` validates one issuer; framed as RFC 8693 token-exchange (Backstage / Cognito
     / Auth0 precedent);
   - the **server orchestration (#2 = Hybrid)**: Spring `oauth2Login()` for the head + an
     `AuthenticationSuccessHandler` for the rapla tail; the `OAuthExchangeController` ([PRD 036](036-external-idp-oauth-login.md) BFF)
     **stays** untouched for the SPA path; provisioning ([PRD 050](050-external-auth-user-lifecycle.md)) shared in the success handler;
   - **credential model A** (HttpOnly access cookie + path-scoped refresh cookie) + the
     **cookie-based reactive-401 refresh**, CSRF wiring, logout policy;
   - the **revocation & session-cap story (#7 = a)**: IdP tokens discarded, revoke-in-rapla
     (`clearSession`, ≤ 1 h), **21 d absolute non-sliding cap**, the two-axis revoke table, and the
     optional idle-timeout/rotation hardenings (cross-ref [PRD 036](036-external-idp-oauth-login.md) § "Refresh path");
   - which clients use what (SPA/explorers = cookie; Swing = re-minted rapla Bearer; iCal/API-keys = Bearer);
   - the **single-issuer cutover** (#6 step): `/api` drops external-issuer trust once all clients re-mint.

   **Landed in `docs/authentication.md` (2026-06-20):** corrected the four provider recipes (Microsoft
   Entra = **Web platform + secret**, SPA platform causes `AADSTS9002326`; Google = Web application;
   Keycloak confidential/public) to register the server-side callback
   **`{baseUrl}/login/oauth2/code/{registrationId}`** (not the old `/app/auth/callback`), with the dev
   `:4200`/`:8051` pair; added the **"Per-provider vs single callback"** note (RFC 9700 mix-up-attack
   mitigation — per-provider is the framework default + BCP); documented the **TEMPORARY DHBW dev bridge**
   (`rapla.oauth.web.dhbw-legacy-callback` + `LegacyKeycloakCallbackBridgeFilter`, with the removal
   condition); added the **single-issuer `/api`** section (`rapla.oauth.trust-external-issuers`, the three
   non-circular reasons, and the `OAuthExchangeController` raw-token follow-up); rewrote the **Swing SSO
   flow** (single SSO entry, rapla as the federating broker, the two `OidcLoginSuccessHandler` fixes —
   UUID-principal re-auth + `SavedRequestAware` resume) and removed the now-stale per-provider Swing
   refresh-routing subsection.

## Tests

- Tier-3 MockMvc: login dialog renders the `enabledProviders()` chooser + password form.
- Provider button → 302 to the configured IdP authorize URL (per provider).
- Callback → credential (cookie) issued; `/api` authenticates via the cookie.
- Unauthenticated SPA `/app` and explorer `/graphiql` → redirect to the server dialog.
- CSRF: state-changing `/api` request without the token → rejected.
- SPA: after migration, **no token in `localStorage`**; cookie auth on `/api`; 401 → refresh → replay.
- Swing (A+Y): the single "SSO" entry opens rapla `/login`; a rapla-brokered Keycloak login yields a
  **rapla-issuer** Bearer (not an IdP token); refresh hits rapla's `/oauth2/token`, not the IdP.
  Regression: Swing's rapla-password login still works; no per-provider menu remains.
- Login page: SSO chooser always rendered; legacy password form shown/hidden per the config flag;
  with the flag off, rapla-SAS admin/direct-grant is still reachable (not locked out).
- Single-issuer cutover: `/api` **rejects** a raw external-IdP token and accepts only rapla-issued ones.
- `GET /api/auth/me`: returns the cookie-user's identity; §12 leak test (no other user's data); 401 without a valid cookie.
- Cookie impersonation: `switch` requires `canAdminUser`; `end` restores the admin; impersonation state visible via `/api/auth/me`.
- Dual presentation (review N3): one rapla-issued token authenticates at `/api` via **both** the cookie AND the `Authorization` header (200 each).
- Single-slot re-login (review S6): a second login (SSO / password / Swing / SPA) overwrites the prior refresh slot → the prior refresh token is invalidated.

## Open Questions

1. ~~**Credential model**~~ — **RESOLVED 2026-06-19: A** (stateless HttpOnly JWT cookie +
   path-scoped refresh cookie). The token in a cookie is accepted. A deeper BFF research pass
   surfaced that the textbook token-handler BFF (Duende/Curity) uses an *opaque session id +
   server-side store* for **instant** revocation and recommends *against* JWT-in-cookie; rapla
   knowingly takes the minority variant because **instant revocation is not required** (decided:
   *"wir brauchen keine sofortige revocation, 1 Stunde access token länge reicht"*) and the
   stateless JWT avoids a shared session store in the multi-pod topology. Revocation lands at the
   ≤1 h refresh boundary. See the Credential-model section for the full trade-off.
2. ~~**OAuth orchestration**~~ — **RESOLVED 2026-06-19: Hybrid.** Split the flow into the
   security-critical **head** (authorize-redirect, state, PKCE, nonce, callback, `code`→token,
   `id_token` validation) and the rapla-specific **tail** (provision per [PRD 050](050-external-auth-user-lifecycle.md), mint the rapla
   JWT via `RefreshSessionService.issueAndPersist`, set cookie). Use Spring **`oauth2Login()`** for
   the head (framework handles the dangerous parts correctly; OIDC `scope=openid` → `id_token`-only,
   aligns with M2/(a)); reuse rapla's building blocks for the tail in an
   `AuthenticationSuccessHandler`. Do **not** reuse `OAuthExchangeController`'s redirect-less
   exchange for the server dialog (Spring already does `code`→token) — it **stays untouched** for
   the current SPA path. Rationale: the reuse appeal of the controller is only the *tail* (the easy,
   already-existing part); the *head* is the risky part you must not hand-roll. Couples to #6: needs
   the `…/login/oauth2/code/{id}` callback registered (empirically already accepted at Mosbach; dev
   wildcard covers it).
3. ~~**`/api` auth source**~~ — **RESOLVED by A**: `/api` accepts the JWT from the
   `access_token` cookie **and** still from the `Authorization` header (so Swing / iCal /
   API-keys keep working unchanged).
4. ~~**SPA refactor split**~~ — **REVISED 2026-06-19: SPA committed into this PRD** (was deferred).
   Including the SPA migration lets `/api` reach single-issuer cleanly (no prolonged multi-issuer
   provisorium) and is what actually closes H4 (`angular-oauth2-oidc` + `localStorage` removed).
   De-risking preserved via ordering: explorers first (Phase 3), then SPA ∥ Swing (4 ∥ 5), cutover last (6).
5. ~~**Confidential client**~~ — **REVISED 2026-06-19: no new client; reuse `rapla-app` + per-provider
   registrations** (the earlier "additive confidential `rapla-server` client" is dropped). The review
   (S4) found it a *solution without a consumer*: `oauth2Login()` makes rapla a **client of the external
   IdP** (Keycloak/Google/MS), built from per-provider `ClientRegistration`s — it does **not** consume a
   client registered on rapla's *own* AS; the tail mints via `RefreshSessionService.issueAndPersist`
   directly (not rapla's `/oauth2/token`); the rapla-password path is `formLogin`; Swing keeps the public
   `rapla-app`. So **nothing** consumes a new confidential rapla-AS client. Correct shape: **Keycloak =
   reuse the public `rapla-app` PKCE client** (`client-authentication-method=none`, no secret — and
   **nothing changes at Keycloak**: its existing wildcard redirect URIs already accept the server callback
   `…/login/oauth2/code/keycloak`, empirically verified); **Google/MS = confidential by nature**, secret
   from `ExternalProvidersProperties` (this is the only "confidential" that the BFF position meant — per
   external provider, not a rapla-AS client).
6. ~~**IdP redirect-URI**~~ — **LARGELY RESOLVED 2026-06-19 (empirical).** A live test against
   Mosbach Keycloak showed the server callback `https://rapla-test.dhbw.de/login/oauth2/code/keycloak`
   is **already accepted** (login form shown, no `Invalid redirect_uri`) — the `rapla-app` client
   has a path wildcard for that host; dev is covered by the `172.24.157.92:*` wildcard. So **if we
   reuse the existing public `rapla-app` client** (server-side `oauth2Login` with PKCE,
   `client-authentication-method=none`), **no new client and no new redirect-URI registration are
   needed** (dev + prod-test already covered). A *new confidential* client would instead start with
   an empty redirect list → one registration per env. (Note: the broad `…/*` wildcard is a mild
   existing security smell, not introduced here; a path-exact entry would be cleaner.)
7. ~~**Keycloak refresh model**~~ — **RESOLVED 2026-06-19: (a) identity-only** + the broker
   model (M2). rapla validates the IdP once at login, mints + owns the session, and **discards
   the IdP tokens** (ideally never requests the IdP refresh token). Revocation is a **rapla**
   operation (`clearSession`, ≤ 1 h); an IdP-side disable is bounded by the **21 d absolute,
   non-sliding refresh cap** (lowered from 30 d, `REFRESH_TOKEN_TTL_SECONDS`). (b) token-mediating
   is reactivated only if rapla ever calls upstream provider APIs server-side. Full rationale +
   precedent (Backstage / Cognito / Auth0 / Keycloak / RFC 8693) in the **Token-issuance model**
   and **Revocation, session cap & IdP-token handling** sections above.

## Follow-up (2026-06-24) — session-namespace logout, server-side revoke, prompt=login

Tightening of the M2 logout path after the realisation that the cookie-model logout
neither revoked server-side nor handled the surviving upstream-IdP SSO session.

### Endpoint move: `/api/auth/session/{refresh,logout}`

Refresh and logout moved from `/api/auth/refresh` + `/api/auth/logout` into a shared
**`/api/auth/session`** namespace, and the httpOnly `refresh_token` cookie's `Path`
moved from `/api/auth/refresh` to `/api/auth/session` (`CookieAuthSupport.REFRESH_TOKEN_PATH`).
Rationale (least-privilege): the durable refresh cookie now reaches *exactly* the two
endpoints that legitimately need it — refresh and logout — and nothing else. Logout sits
there specifically so it can read the refresh token and resolve the user **even when the
access token has expired** (the access cookie's maxAge = token TTL, so it's gone from the
browser after ~1 h; the refresh cookie lives 21 d).

### Logout now revokes server-side

`AuthCookieController.logout()` resolves the user from the path-scoped `refresh_token`
cookie (`RefreshSessionService.peekUser`, which decodes the durable refresh token) and
calls `clearSession(user)` before expiring the cookies. Previously it only cleared cookies,
leaving the server-side `org.rapla.auth.session` slot intact. `clearSession` is per-user
(single-token-per-user) → **logout-everywhere**, a deliberate feature for shared/pool machines.

`/oauth2/revoke` (Spring SAS, RFC 7009) is unchanged and stays the path for external API
clients; rapla's own SPA uses `/api/auth/session/logout`.

### Swing logout: revoke + login flow, no background tab

Swing already revoked via `POST /oauth2/revoke` with its **refresh token** (correct: the
revoke provider resolves the user from the durable refresh token, so it works with an
expired access token). Removed: the `/connect/logout` browser tab (and the `appendIdTokenHint`
helper). In M2 that OIDC RP-initiated logout only ends rapla's *own* SAS session (its
`id_token_hint` is a rapla token, not the upstream IdP's), needs a non-expired hint (400s on
a stale one), and a tab popping up on logout is poor UX. The surviving upstream SSO session is
handled at the next login by `prompt=login`.

### `prompt=login` — defeat silent re-login / enable account switch

In the broker model rapla's logout never propagates to the upstream IdP, so the next SSO login
would silently re-authenticate against the still-live Keycloak SSO session (same user, no way
to switch accounts). Fix:

- `RaplaOAuth2AuthorizationRequestResolver` forwards a whitelisted `prompt` (`login` /
  `select_account`) request param into the IdP authorize request; wired into `oauth2Login()`.
- The server `/login` page (`LoginPageController`) emits `?prompt=login` on SSO links:
  **always for Keycloak-type providers** (the `?logout` one-shot marker proved unreliable as
  the sole trigger), and for **all** providers after an explicit logout (`/login?logout`).
- Verified live against local Keycloak 26.6.1: with a live SSO session, `prompt=login` makes
  KC show "Please re-authenticate to continue" (the reauth page with a "Restart login" affordance)
  instead of silently bouncing straight back into the app. Without it, the SSO link logs straight
  in. (Note: KC 26 shows reauth-as-current-user, not a free username field; switching to a
  different account goes via "Restart login".)

Real upstream single-logout (ending the KC session via a fresh `id_token_hint`) was considered
and **deferred**: it would require keeping the IdP token server-side to mint a fresh hint at
logout, and `prompt=login` already covers the silent-re-login + account-switch needs.

## Follow-up (2026-07-09) — credential *hardening* split out to [PRD 102](102-browser-credential-hardening.md)

The 2026-07-08/09 discussion about **hardening the browser session cookie against untrusted
same-origin content** (semi-trusted [PRD 097](097-event-html-templates-mustache.md) document pages + the template editor) grew past the scope
of "server-side login dialog". It was extracted to **[PRD 102 — Browser credential hardening vs.
untrusted same-origin content](102-browser-credential-hardening.md)** on 2026-07-09.

What 072 still owns (all shipped): the login dialog + chooser, `oauth2Login` head + rapla tail, the M2
identity-broker token model, **credential model A** (the `access_token` + `refresh_token` HttpOnly
cookies) and its cookie-based reactive refresh, revocation/session-cap, the single-issuer cutover, the
Swing SSO flow, and the 2026-06-24 logout follow-up above.

What moved to [PRD 102](102-browser-credential-hardening.md): the memory-token question **and its 2026-07-09 reversal** (the cookie *stays*),
the `CSP: sandbox`/opaque-origin decision for untrusted pages, scoped **capability tokens** ([PRD 076](076-scoped-api-keys-self-rotation.md)
`{read}` reuse), the end-state security matrices, the refresh-per-surface flows, the `/app` CSP
report-only → enforced fix, and the GraphiQL `gqlFetch` refresh-gap finding. [PRD 102](102-browser-credential-hardening.md) `Related:`-links
back here as the credential model it hardens.

**Net for 072:** original scope is done; only the "almost-done" residual is that [PRD 102](102-browser-credential-hardening.md) (which depends
on [PRD 097](097-event-html-templates-mustache.md)) is not yet implemented. 072 moves to `docs/prd/done/` once 102 is under way and nothing
here needs re-touching.
