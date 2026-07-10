# PRD 071 — Web security hardening (audit findings, CSP/CORS/headers, XSS)

**Status:** done (2026-06-21) — all audit findings shipped, mitigated, or reframed. H7 → [PRD 076](../076-scoped-api-keys-self-rotation.md)
(scoped API keys). The soft-shell CSP enforce-flip that was deferred here (report-only; the
critical `script-src` was already enforced) **shipped in [PRD 102](../102-browser-credential-hardening.md) Phase 1 (2026-07-10)** —
`/app` is now enforced via `RaplaCspHeaderWriter`. The Phase-4 gate's CI-wiring is [PRD 034](../034-ci-baseline-workflow.md)'s scope.

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
build config. Out: auth *flows* (PRDs [029](../029-swing-oauth-login.md)/031/[036](../036-external-idp-oauth-login.md)/[043](../043-api-keys-jwt-pat.md)/[050](../050-external-auth-user-lifecycle.md) own those); GraphQL
read-scope (§12 / [PRD 069](../069-graphql-resource-access-read-api.md)); operational follow-ups (ops); deployment-specific auth
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

### Phase 2 — CSP + security headers (A6 Layer 2 + H1) — DONE (enforce-flip completed in [PRD 102](../102-browser-credential-hardening.md) Phase 1, 2026-07-10)

**Step 1 — server security headers + path-scoped enforce [shipped 2026-06-18/19]**
- `SecurityConfig.headers(...)`: `RaplaCspHeaderWriter` (path-scoped CSP) +
  `Referrer-Policy: strict-origin-when-cross-origin` + `X-Frame-Options: DENY`.
- **CSP is path-scoped** — a single global policy cannot fit rapla's mixed surfaces:
  | path | policy | mode |
  |---|---|---|
  | `/api/**` (JSON) | `CspPolicyBuilder.jsonApiPolicy()` — `default-src 'none'` | **ENFORCE** |
  | `/rapla/**` (calendar + iCal HTML pages) | `CspPolicyBuilder.serverPagePolicy()` — `default-src 'none'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; form-action 'self'` | **ENFORCE** |
  | SPA `/app` | `CspPolicyBuilder.build(idp)` (SPA policy — no `script-src`/`style-src`, those ride the Angular autoCsp `<meta>`, [PRD 102](../102-browser-credential-hardening.md) Phase 6) | **ENFORCE** (flipped in [PRD 102](../102-browser-credential-hardening.md) Phase 1, 2026-07-10) |
  | `/login` (inline submit script), GraphiQL/Swagger explorers | SPA policy | report-only |
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
- **RESOLVED — dropped the lowest-criticality directives + codified the Phase-4 gate [2026-06-21].**
  Threat-model triage: the **critical** CSP directive is `script-src` (code execution =
  XSS→localStorage-JWT→takeover, H4/A6) — and it is **already enforced** (SPA autoCsp `<meta>`;
  `/api`+`/rapla` `default-src 'none'`). `style-src`/`img-src`/`font-src` are the **lowest tier**
  (no code execution; worst case CSS defacement / weak CSS-exfil), so the SPA `CspPolicyBuilder.build()`
  policy now **omits them entirely** (no `default-src` fallback → those resource types unrestricted —
  acceptable for a no-code-exec class). Bonus: dropping `style-src` removes the one real blocker the
  Playwright gate had found — a runtime-injected inline `<style>` (Angular/Material component styles;
  `style-src-elem, blockedURI: inline`) that the manual MCP walk missed (its listener was installed
  after load). **The remaining soft shell stays report-only** (per decision):
  `connect-src 'self' <idp-origins>; object-src 'none'; base-uri 'self'; frame-ancestors 'none';
  frame-src 'none'; form-action 'self'`. Tests updated: `CspPolicyBuilderTest`, `SecurityHeadersTest`.
- **Phase-4 gate (shipped 2026-06-21):** `rapla-angular/tests/csp-enforce-readiness.spec.ts`
  (tier-7, not yet in CI per [PRD 034](../034-ci-baseline-workflow.md) Phase 4). Logs in (admin/empty → B3 nag → skip), walks the
  CDK overlays that exist, and asserts the authenticated SPA produces **zero** report-only
  `securitypolicyviolation`s — now **strict-green** (the soft shell is clean). A load-bearing
  **control canary** (an `<object>` that MUST trip `object-src 'none'`) keeps it from passing
  blind. As components land it fails on any new violation → the measured signal for "would
  enforcing the remaining shell break anything?". Run: `npm run e2e -- csp-enforce-readiness`
  (dev server up). NB the listener must be installed via `addInitScript` (pre-load) or it
  misses load-time violations — the lesson from the missed inline-`<style>`.
- **RESOLVED in [PRD 102](../102-browser-credential-hardening.md) Phase 1 (2026-07-10):** the soft shell was gate-confirmed clean, so the
  `/app` policy was **flipped report-only → enforce** via `RaplaCspHeaderWriter`. The enforced
  `/app` header carries the soft shell (`connect-src`/`object-src`/`base-uri`/`frame-ancestors`/
  `frame-src`/`form-action`) but **no** `script-src`/`style-src` — those ride the Angular autoCsp
  `<meta>` ([PRD 102](../102-browser-credential-hardening.md) Phase 6). `/login` (legit inline submit script) and the GraphiQL/Swagger
  explorers **remain report-only**.
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

**Shipped [2026-06-20]**
- **A7 [Med] — local password store → BCrypt.** New `RaplaPasswordEncoder`
  (`spring-security-crypto` BCrypt). rapla **only ever writes** `bcrypt:<hash>` (salted,
  slow, constant-time `matches`). `LocalAbstractCachableOperator.checkPassword` dispatches
  on the existing `algo:` prefix convention: `bcrypt:` → BCrypt; `sha-1:`/`md5:`/bare
  plaintext → verify, then **rehash-on-login** to bcrypt (`upgradePasswordHashIfNeeded`,
  best-effort, on the auth lifecycle seam — §16-safe). **Plaintext acceptance is kept on
  purpose** as the admin hand-edits-a-reset-value hatch; non-empty plaintext is rehashed
  on next login, empty (seed admin) is left untouched (B3 owns that). The static
  `encrypt(algo,…)` stays (Exchange-sync content hash uses it). Tests:
  `RaplaPasswordEncoderTest` (bcrypt/sha-1/md5/plaintext verify + needs-upgrade),
  `PasswordRehashAuthenticateTest` (tier-2: plaintext login rewrites the on-disk store to
  `bcrypt:`, not plaintext).
- **H3 [Med] — AES/ECB + SHA-1 → AES-256-GCM** (see the detailed design below; both
  `CryptoHandler` and `UrlEncryptor` wired, legacy decrypt retained). Tests:
  `CryptoHandlerTest`, `UrlCipherV2Test`, `UrlEncryptionControllerIntegrationTest`
  (v2 deterministic GCM via the `?algo=v2` endpoint param). Sibling fix: the
  `URL_ENCRYPTION.equals("true")` checks in `CalendarPageController.isEncrypted` and
  `UrlEncryptionServletRequestResponsePreprocessor` were widened to
  `UrlEncryptionPlugin.isEnabled(...)` so a `"v2"` calendar still counts as encrypted
  (the access-only-via-`?key=` guard would otherwise fail open for v2 exports).

**Shipped [2026-06-20] (cont.)**
- **B3 [Med] — default admin credential.** Reframed during implementation: **an empty
  admin password stays allowed** (single-user/desktop/demo use it deliberately) — so the
  fix is *awareness + a per-login nag*, not enforcement. No boot-refusal, no rejecting
  empty-password auth (the originally-planned items 3–5 were dropped as incompatible with
  "empty allowed"). What shipped:
  1. **Change-password nag** — the Spring `/login` (browser/SPA SSO) flow redirects to a
     new `/change-password` page whenever the logged-in user's password is *unset* (empty)
     and they are not the fix-admin-password-locked admin. **Skippable but not disableable**
     ("Later" continues; the nag returns next login until a real password is set). Seam:
     `FormLoginSuccessHandler` → `SyncStorageOperator.isPasswordChangeRequired(user)` →
     `ChangePasswordPageController`. **Swing login is deliberately untouched** (admins move
     to SPA+GraphQL). Applies to *any* empty-password user, not just admin.
  2. **Conditional login hint** — `LoginPageController` shows the "default admin / empty
     password" hint **only while the admin password is actually empty**
     (`isAdminPasswordUnset()`); it disappears once a real password is set. Shown even under
     fix-admin-password (it is the demo's login instruction).
  3. **`rapla.fix-admin-password` flag** (`RaplaServerProperties`) — locks the built-in
     `admin`: its password cannot be changed, AND the account can be neither **deleted nor
     modified** (rename / re-permission / disable). Enforced at two operator chokepoints:
     `changePassword` (password) and **`guardFixedAdmin(evt)` called from `check(evt,store)`**
     — the universal write gate every `dispatch` runs via `preprocessEventStorage` on BOTH
     backends (FileOperator + DBOperator), for store AND remove. **Seam history (caught by
     live testing):** first put on `storeAndRemove` → bypassed by REST/SPA/GraphQL (they call
     `dispatch` directly); moved to `checkNoDependencies` → covered delete only; finally
     `check()` → covers store+remove + force-delete (force only ignores *allocatable*
     dependencies, after the guard). The guard targets the `User` entity only, so the admin's
     own Preferences/session writes (login persistence) are unaffected — verified live.
     Regression tests: `blocksAdminDeletionViaDispatchWhenFlagSet`,
     `blocksAdminModificationWhenFlagSet`. Also **suppresses the nag** for the admin. Intended
     for managed/demo deployments running `admin` with a fixed (e.g. empty) credential.
  - **Empty-password detection** is robust: `RaplaPasswordEncoder.isUnset(stored)` verifies
     the empty string against the stored value (literal `""`/blank, or a legacy `sha-1`/`md5`
     hash of `""`). `null` is **not** unset (a null entry can't authenticate at all). bcrypt
     is short-circuited to "not unset" — Spring 7's BCrypt can't verify an empty raw
     password, and the **invariant "never hash `""`"** (`changePassword` keeps `""` literal)
     guarantees `bcrypt("")` never exists.
  - Tests: `RaplaPasswordEncoderTest` (isUnset across formats), `FixAdminPasswordGuardTest`
     (lock + nag-required logic), `PasswordRehashAuthenticateTest` (empty stays literal),
     `ChangePasswordNagFlowTest` (tier-3: empty login → `/change-password`, real login →
     `/app/`, page renders), `LoginPageHintTest` (tier-3: hint shown only while empty).
- **H3 [shipped 2026-06-20]** AES/ECB + SHA-1 key derivation
  (`CryptoHandler`, `UrlEncryptor`). Both move to AES-256-GCM (authenticated) with a
  **self-describing version marker** on the ciphertext; the **legacy ECB decrypt path
  stays indefinitely** (we never control when a deployment updates, and old subscriber
  URLs live in external clients forever). No big-bang migration.
  - **`CryptoHandler`** (encrypts `login:secret` Exchange creds at rest via
    `RaplaKeyStorageImpl`): reversible by necessity — the server replays the cleartext
    to EWS, so it can't be hashed like a login password. `encrypt` → versioned
    AES-256-GCM (random IV). `decrypt` → marker present = GCM, absent = legacy ECB.
    **Lazy upgrade**: existing secrets keep decrypting via legacy; the next
    `storeLoginInfo` (resync / credential re-entry) rewrites them as GCM. No boot sweep
    (would be a §16 write-on-read or a startup iteration that the retained legacy path
    makes unnecessary).
  - **`UrlEncryptor`** (encrypts the `user=…&file=…` calendar-export URL): the URL is
    computed on demand client-side and **never persisted**, so any change to the
    *generation* cipher changes the string every existing calendar shows. Resolution:
    **new exports get GCM, existing ones keep their URL.** `UrlEncryptionPlugin.URL_ENCRYPTION`
    changes from a boolean to the **algorithm tag**:
    | stored value | meaning |
    |---|---|
    | absent / `""` / `"false"` | encryption off |
    | `"true"` | legacy export → **old algo (ECB)** — URL stays byte-stable |
    | `"v2"` | new export → **deterministic AES-256-GCM** |
    - **Assign the tag only on the off→on transition** (a genuinely new export). A
      calendar already reading `"true"` and still enabled keeps `"true"` — never rewrite
      it to `"v2"`, or its URL would change. Empty/unset counts as off, so enabling it
      then is a new export → `"v2"`.
    - **Deterministic GCM** (so a v2 URL is itself stable across views/re-saves): JDK has
      no `AES/GCM-SIV`, so derive a synthetic IV from the plaintext —
      `IV = HMAC-SHA256(key, plain)[0:12]` + `AES/GCM/NoPadding`. Same plaintext → same IV
      → same URL; different plaintext → different IV → GCM stays safe.
    - The v2 cipher lives in `UrlCipherV2` (server). `UrlEncryptor.encrypt(plain, userId,
      algo)` picks the cipher; the endpoint gained a `?algo=` query param
      (`UrlEncryption.encrypt(@RequestBody plain, @RequestParam("algo") algo)`), and the
      Swing publish dialog (`URLEncyrptionPublicExtensionFactory`) reads the per-calendar
      `URL_ENCRYPTION` tag and passes it, applying the off→on assignment rule in
      `mapOptionTo`. Decrypt is version-detected (`UrlCipherV2.isV2`); legacy ECB kept
      forever (old subscriber URLs always resolve).
    - The homegrown `salt=userId.hashCode()` + double-shuffle is dropped for `v2` (GCM
      supersedes it; the salt leaked in cleartext anyway) and retained only in the legacy
      branch.
  - Tests: `CryptoHandlerTest` (GCM round-trip; non-ECB; locked legacy-ECB-decrypt compat;
    version marker), `UrlCipherV2Test` (deterministic round-trip; same plain → same URL;
    marker detection; GCM tamper-reject), `UrlEncryptionControllerIntegrationTest`
    (`?algo=v2` → deterministic `v2:` ciphertext, no legacy `&salt=`; no-algo default stays
    legacy ECB). Key derivation locked at `SHA-256` (the root key is already high-entropy —
    no slow KDF needed).
- **H4** JWT in `localStorage` — mitigated by the Phase-2 CSP.
- **H7** API-key no server-side max TTL. **Reframed → [PRD 076](../076-scoped-api-keys-self-rotation.md).** API keys are revocable
  (membership check in `ApiKeyJwtDecoder`), unlike stateless access tokens, so a forced
  max-TTL was rejected (breaks automation, little gain). Instead [PRD 076](../076-scoped-api-keys-self-rotation.md) shrinks the leak
  blast radius with **scoped API keys** (`read`/`write_events`/`write_resources`/`write_all`/
  `rotate`, default `read`) + possession-/`rotate`-scoped **self-rotation**. Overlap
  rotation already works today via create+delete (AWS/GCP model).

### Phase 4 — verify [shipped 2026-06-21]
Per AGENTS.md §1 every fix landed test-first. The Phase-2 CSP gate now exists:
`rapla-angular/tests/csp-enforce-readiness.spec.ts` — drives the authenticated SPA, walks
the CDK overlays, and fails on any new report-only `securitypolicyviolation` (with a control
canary so it can't pass blind). Currently strict-green. The only remaining piece is wiring it
into CI, which is **[PRD 034](../034-ci-baseline-workflow.md) Phase 4's job** (the browser-e2e lane), not 071.

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
Phase-1/3 point fixes: `JwtConfigTokenTypeTest`, `ArchiverServiceAccessTest`,
`PasswordCheckBindingGuardTest`, `CorsOriginPolicyTest`, `SafePageErrorTest`,
`CspPolicyBuilderTest`, `SecurityHeadersTest`, `XMLReaderAdapterTest`,
`Export2iCalContentDispositionTest`, `LoginAttemptTrackerTest`, `LoginRateLimitTest`.
A7/B3/H3 (this session): `RaplaPasswordEncoderTest`, `PasswordRehashAuthenticateTest`,
`FixAdminPasswordGuardTest`, `CryptoHandlerTest`, `UrlCipherV2Test`,
`UrlEncryptionControllerIntegrationTest`, `ChangePasswordNagFlowTest`, `LoginPageHintTest`.
CSP Phase-4 gate: `rapla-angular/tests/csp-enforce-readiness.spec.ts` (tier-7, run via
`npm run e2e`; not yet in CI per [PRD 034](../034-ci-baseline-workflow.md) Phase 4). All green.

## Open Questions
- ~~Phase 2: confirm `@angular/build` (vite) dev-server leaves no residual WebSocket
  under `start:ai`; if it does, a dev-build-only `connect-src ws://localhost:*` line
  (never prod).~~ **Closed** — the `/app` enforce-flip ([PRD 102](../102-browser-credential-hardening.md) Phase 1, 2026-07-10) ships
  the soft shell (incl. `connect-src`) in enforce mode with no dev-only `ws:` exception, so
  the residual-WebSocket concern is settled.
- A7: migration of existing unsalted hashes — rehash-on-login vs forced reset.
