# PRD 071 — Web security hardening (audit findings, CSP/CORS/headers, XSS)

**Status:** in-progress (2026-06-18)

## Goal

Close the web-facing attack surface found in the 2026-06-18 security audit of rapla,
with a hard constraint: **production deployments
must stay config-free** — every fix ships a secure default that needs zero
deploy-time tuning, reusing config that already exists for another reason
(Tomcat internal-proxies, RFC1918 ranges, the OAuth IdP base-url). A secondary
constraint: **dev == prod** for the XSS-relevant policy, so nothing "works in dev
and breaks at deployment".

## Background — the audit

Two lenses: **(A)** data/password leak, **(B)** admin/account takeover, **(H)**
defense-in-depth hardening. Full finding list lives in this PRD's Plan. The
single most dangerous chain for rapla is **XSS → read JWT from localStorage →
exfiltrate → account/admin takeover** (the SPA stores tokens in `localStorage`,
H4); CSP is the structural defense that breaks it even for unknown future sinks.

## Scope

In: rapla server (Spring Security, page controllers, JWT decode) and the Angular SPA
build config. Out: auth *flows* (PRDs 029/031/036/043/050 own those); GraphQL
read-scope (§12 / PRD 069); operational follow-ups (ops); deployment-specific auth
adapters (belong with their deployment, not this core PRD).

## Plan

### Phase 1 — shipped (2026-06-18, commit "security fixes" + follow-up)
- **B1 [Critical]** Refresh-token accepted as access-token on `/api/**`. Fix:
  `RaplaTokenTypeValidator` in `JwtConfig` rejects `typ ∈ {refresh, api_key}` at
  the resource-server decoder. Test `JwtConfigTokenTypeTest`.
- **B2 [High]** Archiver `checkAccess()` fail-open on null user. Fix: `user==null
  || !isAdmin`. (Audit's "no admin gate" premise was wrong — the service *does*
  gate; the real bug was fail-open.) Test `ArchiverServiceAccessTest`.
- **B4 [Med]** `password-check-disabled=true` (standalone) reachable on a
  non-loopback bind = credential-free admin login. Fix: `PasswordCheckBindingGuard`
  refuses boot unless `server.address` is loopback; `application-standalone.yml`
  now binds `127.0.0.1`. Test `PasswordCheckBindingGuardTest`.
- **B5 [Med]** OAuth redirect same-origin check trusted raw `X-Forwarded-*`. Fix:
  `forward-headers-strategy: native` (Tomcat RemoteIpValve, built-in RFC1918
  internal-proxies → any private-range LB/proxy covered, zero config) + read
  `getServerName()` in `isSameOriginRedirect` instead of raw headers.
- **A2 [High]** CORS `allowedOriginPatterns("*") + allowCredentials(true)`. Fix:
  `CorsOriginPolicy` reflects only same-origin + loopback + WSL-bridge (172.16/12)
  + optional `rapla.cors.allowed-origins`; never a wildcard. Test
  `CorsOriginPolicyTest`.
- **A6 Layer 1 [Med]** Reflected XSS + stack-trace leak in `CalendarPageController`
  / `Export2iCalController` (public `/rapla/calendar`, `/ical`). Fix: `SafePageError`
  helper — always `text/plain`, escaped, never a stack trace in the body (logged
  server-side). All 8 sinks routed through it. Test `SafePageErrorTest`.
- **A8a [Med]** `/api/mail/send` (`MailToUserInterface`) — orphan endpoint, no
  caller, let any auth user mail any user. Fix: removed controller + interface +
  dead `RaplaMailToUserOnLocalhost` + client proxy. Real mail paths (test-mail,
  email-change, Exchange-sync) untouched.

### Phase 2 — CSP + security headers (A6 Layer 2 + H1) — IN PROGRESS

**Step 1 — server security headers + path-scoped enforce [shipped 2026-06-18/19]**
- `SecurityConfig.headers(...)`: `RaplaCspHeaderWriter` (path-scoped CSP) +
  `Referrer-Policy: strict-origin-when-cross-origin` + `X-Frame-Options: DENY`.
- **CSP is path-scoped** — a single global policy cannot fit rapla's mixed surfaces:
  | path | policy | mode |
  |---|---|---|
  | `/api/**` (JSON) | `CspPolicyBuilder.jsonApiPolicy()` — `default-src 'none'` | **ENFORCE** |
  | `/rapla/**` (calendar + iCal HTML pages) | `CspPolicyBuilder.serverPagePolicy()` — `default-src 'none'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; form-action 'self'` | **ENFORCE** |
  | SPA `/app`, `/login`, explorers | `CspPolicyBuilder.build(idp)` (SPA policy) | report-only |
- **Why enforce on `/rapla/**` first:** the calendar pages are public, render
  user-controlled data (resource/event names — where A6 lived), and run **no scripts
  of their own**. `default-src 'none'` makes an injected `<script>` structurally
  unable to execute → the A6 reflected-XSS class dies there. `style-src` allows the
  external `calendar.css` + the inline `style=` attributes (`AbstractHTMLCalendarPage`);
  `form-action 'self'` the GET filter form. Verified safe for the 🔒 external iCal
  subscribers (`.ics`/`.csv` are non-browser consumers → ignore the header).
- **Why SPA/login stay report-only:** Material/CDK inject inline styles and the login
  page has a legit inline submit script — both would break under `default-src 'none'`.
  They wait for the report-only walk (Step 3).
- `connect-src` (SPA policy) derived from the configured OAuth IdP endpoints
  (`ExternalProvidersProperties.enabledProviders()` → issuer/authorize/token/jwks
  origins) → **zero CSP-specific config**. Effective policy boot-logged (auditable).
- **Live-verified on the dhbwrapla deployment (Mosbach Keycloak):** the SPA report-only
  header automatically carries `connect-src 'self' … https://login.mosbach.dhbw.de`
  (+ Microsoft/Google when configured) — no CSP config touched.
- Tests: `CspPolicyBuilderTest`, `SecurityHeadersTest` (MockMvc: `/rapla/calendar` +
  `/api/**` enforced `default-src 'none'`, `/login` report-only).

**Step 2 — autoCsp (build-side script-src) [shipped 2026-06-18]**
- `angular.json`: `security.autoCsp: true` → build injects a `<meta>` CSP with
  `script-src 'strict-dynamic' <per-build-hashes>` into `index.html`. In the build
  artifact → identical in dev (`ng serve`) and prod. AOT (no `unsafe-eval`), no-SSR
  (no nonce). Verified in `dist/.../index.html`.
- **Clean division:** the autoCsp `<meta>` owns `script-src`; the Spring header owns
  everything else and emits **no** `script-src`/`default-src` (a header default-src
  would re-impose a script policy and conflict with the autoCsp loader).

**Step 3 — report-only walk → enforce [partial walk done 2026-06-19, authenticated leg remaining]**
- **Walk method:** drove Playwright against the running `ng serve` SPA (:4200), injecting
  the SPA report-only policy onto the document via route interception (the source
  `spring-boot:run` returns 404 for `/app`, so the Spring header can't be observed on the
  real document directly). Collected console CSP violations + DOM inline-style stats.
- **Findings (unauthenticated shell):**
  1. ✅ Material's static/component styles are **external** CSS (esbuild extracts to
     `styles.css` via `<link>`) — zero runtime `<style>` injection, zero inline `style=`
     on the shell → `style-src 'self'` covers the static styles. The "Material breaks
     style-src 'self'" assumption is **false for the shell**.
  2. 🔴 **`base-uri 'none'` breaks the SPA** — it ships `<base href="/app/">`. Fixed:
     `CspPolicyBuilder.build()` now emits `base-uri 'self'` (the `/api` + `/rapla`
     policies keep `'none'` — no `<base>` there). Test updated.
  3. ⚪ `connect-src` violation seen was a dev-only artifact (4200→8051 split); prod is
     same-origin/IdP, already covered.
- **Remaining (needs IdP login — could not reach authenticated UI):** CDK overlays
  (mat-select, datepicker, menus) set inline `style="transform:…"` for positioning, which
  **cannot be hashed/nonced** (no SSR → no per-request nonce) and would need
  `style-src 'self' 'unsafe-inline'`. This single open question decides SPA enforce. Until
  an authenticated walk confirms it, the SPA header **stays report-only** (enforcing
  partially-blind is the login-page mistake). `style-src` deliberately left at `'self'`
  so the authenticated walk still surfaces overlay violations.
- **To finish:** log in (Keycloak), open a mat-select/datepicker/dialog, watch for
  `[Report Only] … style-src` violations. If only CDK inline-style attrs violate → set
  the SPA `style-src 'self' 'unsafe-inline'` (script-src stays strict via the autoCsp
  `<meta>`), then drop report-only to enforce for `/app`.
- **dev parity:** dev runs `npm run start:ai` (no-live-reload) → no HMR WebSocket → no
  `connect-src ws:` exception → dev CSP == prod. Source maps unaffected.

**Deferred (not needed yet):** the `CspContributor` plugin-contribution mechanism and
the optional `rapla.csp.*` append — current connect-src derivation from the OAuth
config covers every real case. Add when a plugin/feature first needs an extra origin.
HSTS is Spring's default on HTTPS (left as-is). No OAuth iframes (code-flow full
redirect + refresh_token grant; no `silentRefreshRedirectUri`/`sessionChecksEnabled`)
→ `frame-ancestors 'none'` / `frame-src 'none'` are safe.

### Phase 3 — point findings

**Shipped [2026-06-18/19]**
- **A9 [Med]** XXE: `XMLReaderAdapter.harden()` — `disallow-doctype-decl=true` +
  external-entities off + `FEATURE_SECURE_PROCESSING`, applied centrally to every SAX
  parser (dataset load, DB-CLOB reparse, config). rapla's XML never uses a DOCTYPE.
  Test `XMLReaderAdapterTest`; regression: normal dataset load still works.
- **H5 [Low]** `Content-Disposition` filename injection (`Export2iCalController`): the
  user-controlled calendar name is stripped of CR/LF/quotes and wrapped per RFC 6266
  via `contentDispositionAttachment(...)`. Test `Export2iCalContentDispositionTest`.
- **H2 [Med]** No login throttle: `LoginAttemptTracker` (in-memory exponential backoff,
  keyed by client-IP + username) + `LoginRateLimitFilter` on both `POST /login` (main
  chain) and the OAuth password grant `POST /oauth2/token` (auth-server chain). First 3
  attempts free, then 1s/2s/4s/… up to a 5 min cap; success or 1 h idle resets; over
  threshold → `429 + Retry-After`. **Per-pod by design** — no DB: with rapla's 2 pods +
  LB source-IP stickiness the per-pod counter is effectively global per IP, and even
  without stickiness exponential backoff throttles each pod independently. Tests
  `LoginAttemptTrackerTest`, `LoginRateLimitTest`. Keyed by IP+user (never username
  alone) so an attacker cannot lock out a real user. IP is `getRemoteAddr()` (real
  client, thanks to B5 `native`).
- **Explorer hardening [Low]** `/swagger-ui/**` and `/graphiql/**` now require an
  authenticated session (moved out of the `permitAll` list in `SecurityConfig` to an
  explicit `.authenticated()` matcher, ahead of the broad permit). The explorer pages
  are static HTML; in a browser only cookies (form-login JSESSIONID / remember-me) are
  sent on a navigation GET — the SPA's localStorage Bearer is not — so the session is
  the only viable gate. The OAuth2 SPA login (`/oauth2/authorize` via form-login) also
  establishes a JSESSIONID, so a dev signed into the SPA already satisfies the gate; the
  GraphiQL/Swagger "log in via SPA" ritual is unchanged. Unauthenticated access returns
  **401** (the resource-server Bearer entry point is active on the main chain), not a
  302→/login. The underlying public specs (`/v3/api-docs/**`, `/api/graphql/schema`)
  stay open — only the human-facing explorer UIs are gated. Test `ExplorerAuthGateTest`.

**Remaining (not yet scheduled)**
- **A7 [Med]** Local password store: unsalted MD5/SHA, accepts plaintext, non-const
  compare → BCrypt/Argon2 via `DelegatingPasswordEncoder` + transparent rehash.
- **B3 [Med]** Default admin credential (`admin` + empty password) ships in the seed
  `data.xml` on **every** install — a known default credential that **must be changed
  on every system**, not just network-exposed ones. The login page currently even
  advertises it. Layered fix:
  1. remove the credential hint from the login page (don't advertise defaults anywhere);
  2. **timely reminder on every system** — a prominent startup log warning *and* a UI /
     login banner while any admin still holds the default/empty password (so it gets
     changed, not silently left);
  3. force-change-on-first-admin-login;
  4. **boot-refusal** (`PasswordCheckBindingGuard`-style, cf. B4) when an admin has an
     empty password AND the connector binds a non-loopback address;
  5. reject empty-password authentication outside the `local`/`standalone` profiles.
- **H3** AES/ECB + SHA-1 key derivation (`CryptoHandler`, `UrlEncryptor`).
- **H4** JWT in `localStorage` — mitigated by the Phase-2 CSP.
- **H7** API-key no server-side max TTL.

### Phase 4 — verify
Per AGENTS.md §1 every fix lands test-first. Phase-2 CSP additionally needs a
Playwright report-only pass (PRD 034 Phase 4 wiring) that fails CI on violations —
the strongest "no surprise at deploy" gate.

## Decisions (locked 2026-06-18)
1. **Zero deploy-config** is a hard requirement; reuse existing config, never add a
   security-only knob to the deploy surface. Pattern proven in B5 (Tomcat default
   proxies), A2 (loopback+WSL ranges), Phase-2 connect-src (OAuth IdP base-url).
2. **dev == prod** for XSS-relevant CSP → CSP script/style ship in the build
   (`autoCsp`), not only the server header; dev uses no-live-reload for strict parity.
3. CSP is **extensible in code** via `CspContributor` (plugins declare their own
   needs, travel with the jar) + an optional config append + per-request capability
   from the server header (nonce/per-route) for future dynamic libs.

## Tests
`JwtConfigTokenTypeTest`, `ArchiverServiceAccessTest`, `PasswordCheckBindingGuardTest`,
`CorsOriginPolicyTest`, `SafePageErrorTest`, `CspPolicyBuilderTest`,
`SecurityHeadersTest`, `XMLReaderAdapterTest`, `Export2iCalContentDispositionTest`,
`LoginAttemptTrackerTest`, `LoginRateLimitTest` (all green). Remaining: Playwright
report-only walk (Step 3, PRD 034 Phase 4) that fails CI on violations — the strongest
"no surprise at deploy" gate.

## Open Questions
- Phase 2: confirm `@angular/build` (vite) dev-server leaves no residual WebSocket
  under `start:ai`; if it does, a dev-build-only `connect-src ws://localhost:*` line
  (never prod).
- A7: migration of existing unsalted hashes — rehash-on-login vs forced reset.
