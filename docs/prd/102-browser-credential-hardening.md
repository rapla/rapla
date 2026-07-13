# PRD 102 — Browser credential hardening vs. untrusted same-origin content

**Status:** proposed — 2026-07-09 (awaiting maintainer sign-off). Split out of [PRD 072](072-server-side-login-dialog.md) on 2026-07-09
(072 was "server-side login dialog"; issuing/refreshing the session cookie is 072's shipped scope,
*hardening that credential against untrusted same-origin pages* is this PRD).
**Related:** [PRD 072](072-server-side-login-dialog.md) (the browser credential model — `CookieAuthSupport`/`CookieToBearerFilter` — that
this PRD hardens), [PRD 097](097-event-html-templates-mustache.md) (server-rendered semi-trusted HTML document templates + the template editor
that create the untrusted surface), [PRD 076](076-scoped-api-keys-self-rotation.md) (api-key scope vocabulary reused for capability tokens),
[PRD 071](done/071-web-security-hardening.md) (the CSP research this builds on).

## Abstract

[PRD 097](097-event-html-templates-mustache.md) puts **semi-trusted, author-written pages** (rendered document templates + the editor's live
preview) on rapla's **main origin**. A cookie's `Path` scopes only the *destination* of a request, not
the *page* it originates from, so any script on any same-origin page can ride the ambient
`access_token`/`refresh_token` cookies (`HttpOnly` stops *reading*, not *riding*). This PRD decides how
the browser session credential is contained so that untrusted same-origin content cannot escalate to
the user's session — **without** weakening the trusted SPA. The measurable end state: an injected
`fetch('/api/graphql', {credentials:'include'})` (or `…/session/refresh`) from a rendered 097 document
or preview yields **no usable response**, while the SPA / GraphiQL / Swagger keep working unchanged.

## Implementation

**Decision (D1): keep the `access_token` HttpOnly cookie; do NOT migrate to a memory-only token.**
The security budget goes to two load-bearing layers plus one hygiene fix:

- **Untrusted pages get an opaque origin** via `Content-Security-Policy: sandbox` (+ `connect-src
  'none'` + `script-src` lockdown). Opaque origin ⇒ every request is cross-origin ⇒ `SameSite`
  attaches **no** cookie and CORS makes responses unreadable. This is *the* control.
- **Untrusted pages that must read data client-side get a scoped capability token** minted off the
  trusted session — a normal `/api` bearer carrying only [PRD 076](076-scoped-api-keys-self-rotation.md) scope `{read}`, ~5 min TTL. It queries
  GraphQL but the *existing* scope enforcement (`ApiKeyScopeContext` write-chokepoint,
  `@requiresAccessDetails`, `requireInteractiveSession`) confines it. Never a cookie, never the session
  token. Ideally the page needs no credential at all (data baked in server-side).
- **`/app` CSP flips from report-only to enforced** — the real mitigation for XSS *inside* the SPA
  (scheduled **last**, Phase 5 — a strict `script-src` would churn against SPA components under active
  development; it does not gate the untrusted-page containment).

Touch points: `SecurityConfig` (CSP enforcement), a session-authenticated capability-mint endpoint
(reuses `ApiKeyJwtDecoder`/`ApiKeyScopeContext` — no new `typ`/decoder), the 097 render path (owns the
`sandbox` header emission), the template editor (trust split). `CookieAuthSupport`,
`CookieToBearerFilter`, `CookieAuthCsrfMatcher`, the SPA cookie model, and the explorer shells **stay
as shipped**.

## The threat

With [PRD 097](097-event-html-templates-mustache.md) the main origin gains **semi-trusted pages** (author-written templates rendered with real
data). A cookie's `Path` controls only *which destination URLs* the browser attaches it to — never
*which page* a request originates from. So any script on any **same-origin** page can
`fetch('/api/graphql', {credentials:'include'})` and the browser attaches the `access_token` cookie; it
can likewise ride the ambient `refresh_token` cookie against `/api/auth/session/refresh` and read the
fresh access token out of the response body. Narrowing `access_token` to `Path=/api` gives **zero**
protection (the destination always matches) — obsolete.

## Options evaluated

1. **Prevent script on semi-trusted pages** (sanitize + strict CSP) — [PRD 097](097-event-html-templates-mustache.md) D6, owned by the template
   engine. In scope for the renderer.
2. **Opaque origin via `CSP: sandbox`** on document responses — cross-origin ⇒ no cookies (SameSite +
   CORS), no storage, unreadable responses. Never combine `allow-same-origin` with `allow-scripts`
   (restores the real origin, disables the protection). → **ADOPTED (load-bearing, D2).**
3. **Separate origin (sandbox subdomain)** — the hard SOP boundary (githubusercontent pattern).
   → **rejected: rapla deployments have no control over subdomains.**
4. **Access token in SPA memory only**, `Authorization` header. → **REJECTED (D1 reversal).**
5. **Scoped capability token for untrusted pages** — a `{read}` bearer minted off the trusted session,
   confined by existing [PRD 076](076-scoped-api-keys-self-rotation.md) enforcement. → **ADOPTED (load-bearing, D3).**

### Why option 4 (memory token) was reversed (D1)

The only reason to remove the access *cookie* was that a same-origin untrusted page could ride it. But:

- **A sandboxed page (option 2) rides no cookie at all** — opaque origin ⇒ `SameSite` blocks *both*
  cookies. The capability model (option 5) *requires* sandboxing untrusted pages anyway, so the threat
  option 4 targeted is already closed by option 2.
- **The "defense-in-depth vs a forgotten sandbox" argument fails.** With the memory model a forgotten
  sandbox header still loses: the page `fetch`es `/api/auth/session/refresh` (ambient, same-origin →
  passes any `Origin`/custom-header check) and reads the new access token from the body. Full
  compromise, one extra fetch. The sandbox is load-bearing either way.
- **Memory-only is *worse* against XSS in the trusted SPA** — an HttpOnly cookie can be *ridden* but not
  *read* (no token to exfiltrate; blast radius = the injection window); a memory token is readable
  in-realm → XSS exfiltrates a 1 h bearer to replay offline.
- **It costs real complexity** — a bootstrap-refresh round-trip on every reload, cookie-swapping
  impersonation-endpoint rework, CSRF re-keying, explorer churn — for a redundant/absent benefit.

## Adopted model

- **Trusted SPA (`/app`)** keeps [PRD 072](072-server-side-login-dialog.md) credential model A unchanged: `access_token` HttpOnly cookie
  (1 h) + path-scoped `refresh_token` cookie (21 d, `Path=/api/auth/session`). Seamless reloads, silent
  refresh, best posture against SPA-XSS (token never JS-readable).
- **Untrusted pages (097 documents + template preview)** are served `CSP: sandbox` (opaque origin) +
  `connect-src 'none'` + a **script policy by tier** (D4: static docs `script-src 'none'`; component
  docs a deployment **allowlist**, never a nonce — D5) with the engine sanitizer stripping author
  script (097 D6). No cookie attachable, nothing exfiltratable.
- **When an untrusted page needs real data**, it gets a capability token (below) — never the session
  cookie/token. Ideally data is baked in server-side and it needs no credential.
- **`/app` CSP moved report-only → enforced** — deferred to Phase 5 (last); does not gate the
  untrusted-page containment.

### Capability tokens for untrusted pages (scoped downscoping, reuses [PRD 076](076-scoped-api-keys-self-rotation.md))

A sandboxed page has an opaque origin, so it attaches **no cookie** — to read data client-side it must
hold an **explicit bearer**. That bearer is NOT the session access token (which may carry
write/`access_details`/full interactive power); it is a **downscoped token minted off the trusted
session**, reusing rapla's scope machinery rather than a new `typ`/endpoint:

1. The **trusted SPA** (session-authenticated) calls a mint endpoint (e.g. `POST /api/auth/scoped-token`
   or `POST /api/documents/{docId}/token`). The untrusted page cannot mint one — it holds no session.
2. The server mints a **short-lived JWT (`exp ≈ 5 min`) carrying [PRD 076](076-scoped-api-keys-self-rotation.md) `scopes:{read}`** (least
   privilege; `ApiKeyScopes.normaliseForNewKey` guarantees `read` and nothing more). Signed with
   `RaplaKeyStorage`; **stateless** (validates on any pod).
3. Handed to the sandboxed page as a bearer (capability URL or injected) — **never a cookie**.

**It is a normal `/api` token, deliberately — NOT rejected at GraphQL.** The confinement is the scope,
enforced by machinery that already exists:
- **Mutations blocked** at the operator write chokepoint (`LocalAbstractCachableOperator` /
  `ApiKeyScopeContext` — the single seam REST *and* GraphQL writes converge): `{read}` cannot mutate.
- **Sensitive expansions blocked** — no `access_details` → the `@requiresAccessDetails` GraphQL seam
  withholds user PII (`groups`/`email`/`isAdmin`/`authSource`) and permission structure.
- **Interactive-only reads blocked** — `ApiKeyScopeContext.requireInteractiveSession` keeps
  SMTP/LDAP/Exchange/plugin secrets off any scoped token.
- **Other endpoints** are reachable only if the scope grants them (`write_*`); `{read}` only reads.

Residual + the layer that closes it: a `{read}` token can read everything the user may read (scopes are
capability axes, not a per-document subset), so it is **not** a data-narrowing boundary on its own. That
is acceptable because the page is **sandboxed with `connect-src 'none'`** — it renders the data but
cannot exfiltrate it, and the data was the user's to see anyway. For true per-document narrowing, either
**bake the data in server-side** (no client token) or add a data-scoping claim later; not required v1.

## End-state security matrix

**Surfaces — who holds what, and what it can do:**

| Surface | Trust | Origin | Credential | Scope / power | Hardening |
|---|---|---|---|---|---|
| **SPA** (`/app`) | trusted | real | `access_token` HttpOnly cookie (1 h) + `refresh_token` HttpOnly cookie (21 d, `Path=/api/auth/session`) | full interactive user (§12 filters) | **enforced** CSP, `SameSite=Lax`, `Secure` |
| **GraphiQL / Swagger** | trusted dev tool | real | same session cookie, behind `.authenticated()` | full interactive user | as SPA; shells stay gated |
| **Template editor chrome** | trusted | real | session cookie | full — **template writes admin/role-gated** | enforced CSP |
| **Template preview** | **untrusted** | **opaque** (`CSP: sandbox`) | **none** (baked-in) *or* `{read}` capability bearer | read-only; no PII, no writes | `sandbox` + `connect-src 'none'` + `script-src` lockdown |
| **097 document** (standalone URL) | **untrusted** | **opaque** (`CSP: sandbox`) | **none** (baked-in) *or* `{read}` capability bearer | read-only; no PII, no writes | `sandbox` + `connect-src 'none'` + sanitize |
| **Swing** | trusted native | n/a | re-minted rapla Bearer (memory, M2) | full user | native, no browser cookie |
| **iCal / API keys** | semi-trusted | n/a | Bearer / API key | **[PRD 076](076-scoped-api-keys-self-rotation.md) scope** (`read` or granted `write_*`) | header-Bearer only |

**Threats — what an attacker gets, and what stops them:**

| Attacker | Outcome | What stops it |
|---|---|---|
| **Cross-site page** (evil.com) | **Nothing** | `SameSite=Lax` · can't set `Authorization` cross-site · CORS |
| **Malicious template, sandboxed** (designed case) | **No escalation, no exfiltration** — renders only what the user could already see, into a page that can't send it out | opaque origin → no cookie ridable · `{read}` capability · `connect-src 'none'` · sanitize |
| **Same-origin untrusted page IF a sandbox header is forgotten** (failure mode) | Rides the access cookie → full session | **the one residual risk** → one hardened "render untrusted" path that *always* sets sandbox + a regression test; credential-model-independent (memory-token lost here too, via refresh-ride) |
| **XSS in the trusted SPA** (`/app`) | Acts as the user **during the window**; **cannot steal a token to replay offline** | **enforced `/app` CSP** · **HttpOnly** cookies (no token exfiltration) · 1 h TTL |
| **Stolen `refresh_token` cookie** (device/transport, not JS) | Mint access tokens until the 21 d cap or a revoke | `Secure` · `HttpOnly` (needs device/proxy compromise) · 21 d cap · revoke-in-rapla (≤ 1 h) · optional rotation+reuse (OQ2) |
| **Leaked `{read}` capability token** | Read what the user can read for **≤ 5 min** | short TTL · read-only scope · `connect-src 'none'` prevented the leak at source |

**Load-bearing invariants (everything rests on these four):**
1. **Untrusted = opaque origin** — every 097 document *and* every preview is served `CSP: sandbox` from
   **one** hardened render path so it cannot be forgotten per-route. The single most important control.
2. **`connect-src 'none'`** on those surfaces — turns "can read" into "can't exfiltrate".
3. **Untrusted pages never get an ambient credential** — no cookie (sandbox blocks it); at most a
   `{read}`, ~5 min capability minted off the trusted session; ideally data is baked in.
4. **Session cookie stays HttpOnly + `SameSite` + `Secure`, `/app` CSP enforced** — cross-site can't
   ride it, SPA-XSS can't *steal* it.

## Access-token refresh per surface

A sandboxed page (opaque origin) attaches **no cookie**, so it **cannot refresh itself** —
`POST /api/auth/session/refresh` from it is cross-origin, the `refresh_token` cookie is not attached,
the response is unreadable. Refresh therefore differs by trust tier:

- **SPA / editor chrome / GraphiQL / Swagger (trusted, cookie):** refresh is the **existing** cookie
  flow — the browser auto-sends the `access_token` cookie; on 401 (or GraphQL `UNAUTHENTICATED`) the
  client POSTs `/api/auth/session/refresh`, the server sets a fresh `access_token` cookie, and the
  request replays. **No change** — this is what ships today.
- **Template preview / 097 document (untrusted, sandboxed):** the page never refreshes; the **trusted
  parent** (editor chrome / SPA, which holds the cookie) is the token authority:
  - **Preferred — baked-in server render:** the parent calls a render endpoint with its (cookie-auth,
    auto-refreshing) session; the server returns sandboxed HTML with data already in it. The preview
    holds **no token and needs no refresh** — "refresh" = the parent re-renders. Sidesteps the problem.
  - **If the preview must fetch live data:** the parent mints a `{read}` capability, injects it (srcdoc /
    capability URL / `postMessage`); on expiry the sandboxed page signals the parent (postMessage or a
    401 from its own fetch) and the **parent re-mints** and re-injects.

### GraphiQL refresh gap (found 2026-07-09 — carry the fix)

The GraphiQL shell (`static/graphiql/index.html`) has **two** fetch paths and only one refreshes:
- The **`createGraphiQLFetcher({ fetch })`** path (main query execution + on-mount introspection)
  **does** 401 → `/api/auth/session/refresh` → replay. ✅
- The **toolbar helper `gqlFetch`** (used by *Load view* `getViewQuery`/`listViews`, *Save view*
  `saveView`, *Delete view*, and the groups list) does a plain fetch with **no refresh** — on a 401 it
  just alerts. ❌ So the view toolbar breaks on an expired access token even though queries recover.
- **Fix:** extract one refresh-aware `refreshableFetch` and route **both** the GraphiQL fetcher and
  `gqlFetch` through it. Check Swagger-UI's shell for the same shape. This is also the design rule for
  the template editor: **every** data path (main render *and* every side helper) must go through a
  single refresh-aware fetch, or the gap recurs.

## Template editor — trust split (constraints; design in [PRD 097](097-event-html-templates-mustache.md))

- **Editor chrome** (source pane, toolbar, save) = **trusted SPA surface** → normal cookie auth; inherits
  the SPA's cookie refresh. Template writes land in `StoredArtifact` (kind `TEMPLATE`/`PARTIAL`) and are
  **admin/trusted-role gated** (a template renders for *other* users → open authoring = stored-XSS).
- **Live preview** = renders author markup with real data → **must never be injected into the trusted
  editor DOM** (that runs author script in the trusted realm = self-XSS). Render into a **sandboxed
  iframe** (`CSP: sandbox`, `srcdoc`/capability URL, `connect-src 'none'`); data via **capability token
  or baked-in**, never the editor's session. The sandbox protects the previewing author *and* every
  later viewer regardless of who authored (or compromised) the template.

## Interactive document tier (untrusted-authored components + native save)

*(Design settled 2026-07-10. This extends the containment model above so **untrusted-authored** documents
— AI-generated or arbitrary-user — can carry rapla UI components and save data, without trusting the
author. The document **engine/editor/feature** side — including the implementation checklist — is
[PRD 097 Phase 9](097-event-html-templates-mustache.md#phase-9--interactive-tier-components--native-save);
the **security model** is here.)*

**Threat framing.** Once AI or arbitrary users author templates, the author is **untrusted** (prompt
injection, careless/hostile input). Safety is structural, not trust-based: the template stays
**logic-less** (author emits layout + data bindings + `<rapla-*>` component tags, never code), behavior
lives only in **rapla's** components, and everything runs in the opaque-origin sandbox above — so even a
smuggled script can't reach the session or exfiltrate.

**Two CSP tiers, selected per document by component usage** (the renderer validates on save, so it knows
whether the template uses registered component tags):

| Document | `script-src` | sandbox tokens |
|---|---|---|
| **Static** (no component tags) | **`'none'`** (unchanged 097 D6a) | `sandbox` (+ `allow-forms` only if it has a native save form) |
| **Interactive** (uses `<rapla-*>` tags) | **`<deployment allowlist>`** | `sandbox allow-scripts` (+ `allow-forms` if it saves) |

Least privilege: most documents are static and keep `script-src 'none'`; only component-using docs carry
the allowlist. `connect-src 'none'` stays in **both** (data baked-in / `postMessage`).

**Script loading — deployment-owned path ALLOWLIST, not nonce.** The renderer gates script sources
against a **deployment-configured, exact/narrow, path-scoped** allowlist (rapla + plugin component
paths), **no `'unsafe-inline'`** (which blocks inline `<script>` *and* `onclick` for free). The
nonce+`strict-dynamic` approach was **rejected**: the sandbox already traps any smuggled script (no
cookies, `connect-src 'none'`), so the nonce's gadget-resistance buys little here, and dropping it
removes the nonce-leak footgun. **Never whitelist a public package CDN** (anyone can publish → bypass);
external JS only as a specific vetted (ideally SRI-pinned) deployment exception. **Operational
commitments to name:** (a) the `sandbox` header is always present, (b) allowlisted paths stay gadget-free
(no JSONP/redirect/upload/gadget-libs).

**Components = precompiled, form-associated custom elements** (AOT → no `'unsafe-eval'`), served from the
backend (`/app/elements/…`) or a plugin JAR (`META-INF/resources/…`; ES-module bundles need
`Access-Control-Allow-Origin: *` — public JS — because the doc is opaque-origin; classic bundles need no
CORS). A **component registry** (`key → {bundleUrl, tags, attributes, capabilityNeeds}`, populated by
rapla + plugins + deployment) drives the allowlist match, the sanitizer tag allowlist, and capability
minting. Authors reference components by **key/tag, never URL** → unknown key resolves to nothing (no
URL to validate/get wrong).

**Save = native `<form>` POST + scoped write-capability.** A native form (needs only `allow-forms` +
`form-action <endpoint>`, no scripts) POSTs cross-origin from the opaque doc → `Origin: null`, cookieless
→ **can't ride the reader's session**; it authenticates via a **capability token** (hidden field) minted
server-side at render, bound to a **declared** save (entity+fields+action), `write` scope, short TTL. A
dedicated submit endpoint validates the token (not cookies), performs only the declared save, §12/§16
enforced. **POST not GET** (§16). Embedded docs may instead `postMessage` the save to the SPA parent
(keep `form-action 'none'`); standalone docs use the native-form path.

**Submit-endpoint registry (the `form-action` allowlist).** The sibling of the component registry and
the script allowlist: `key → { url, allowed document names, capability scope, declared-save shape }`,
namespaced `pluginname.method`, populated by rapla + plugins + deployment. A template names its target
by **key** (`<form action="pluginname.submitMyAvailableTimes">` — 097's authoring side), and the
renderer resolves key→url server-side to (a) pin `form-action` to that one url, (b) allow the `<form>`
through the sanitizer, (c) mint the capability. **Reference by key, never URL**; an unknown key is a
resolve-or-reject at save (invalid document, surfaced — never fall through to treating the value as a
URL). Plugins own their `pluginname.*` half of the namespace (cannot forge into another plugin's).

**The capability seal — contract.** The hidden-field token is trusted **because it is sealed, not
because it is hidden** (a hidden field is view-source-visible and editable): `UrlCipherV2` (AES-256-GCM
— editing breaks the auth tag) or a signed JWT. Rules:
- **`exp` lives inside the sealed plaintext** (a `?exp=` param or clear field would be editable), so the
  holder cannot extend the window. Two independent deactivation conditions, both checked: **by action**
  (the context was acted on) and **by time** (`exp` passed).
- **Trust only what is inside the seal** for authorization-bearing values (`contextId`/`allowedActions`/
  subject). Never pass a *clear* `contextId` alongside — and if one exists, read it from the seal, never
  the clear field (else a valid seal for R + clear `id=R'` is a confused deputy).
- **Authorization vs choice are separate.** The seal carries *authorization* (which object, which
  actions permitted, `exp`, minted-for-whom — known at mint). The visible fields carry the user's
  *choice* (accept/deny, which slot — not known at mint). The endpoint validates **choice ⊆ the seal's
  permitted set**; the choice field is freely editable but bounded by the seal.
- **Mintable by a server event, not only at render.** The email-approval flow mints the seal when the
  request is created and mails the link; render just re-presents it. So the write-capability primitive
  must accept `mintWrite(subject, object, allowedActions, exp, …)` from a lifecycle event, not only a
  browsing session.

**Consumption — state-based, not a revocation store.** A decision seal is **stateless**; consumption is
enforced by the **domain object's state**, not by tracking the token (stateless → validates on any pod,
no cleanup, no shared store). The endpoint asks "has this context already been acted on?" and the
object's status answers — a second submit for the same context fails. Requirements:
- **Atomic check-and-act.** "Still pending?" and "record the decision" are **one** operation — rapla's
  optimistic concurrency (version-precondition `storeAndRemove` / `RaplaNewVersionException`): the
  concurrent loser gets a version conflict → mapped to "already decided". Check-then-act would let two
  submits both pass (double-click, re-clicked mail).
- **Context, not token instance, is consumed.** Two seals for the same `contextId` (re-sent mail) both
  fail once the context is acted on — the shared state check needs no explicit seal invalidation.
- **Fallback for stateless actions.** Where the action has no checkable "already done" state (append-only,
  legitimately repeatable), fall back to an explicit single-use marker (`jti`/nonce tracked server-side).
  State-based for decisions on stateful objects; explicit tracking otherwise.
- **Graceful failure without a leak.** A *valid* seal on an already-acted context → show the outcome
  ("already decided on <date>"); an *invalid/forged* seal → generic failure (never confirm the context
  exists).

**Navigation:** `<a href>` links allowed but sanitizer **scheme-allowlists** `http`/`https`/`mailto`/`#`
(strips `javascript:`/`data:`); native `<details>`/`<select>` need no scripts; `target="_blank"` needs
`allow-popups`; rich nav = a component.

**Sanitizer / validate-on-save:** strip author `<script>`/`on*`/unknown tags + `javascript:`/`data:`
hrefs; allowlist registered component tags+attributes + safe layout HTML; forbid sensitive inputs
(`type=password`). AI authoring prefers emitting a **structured component tree (JSON)** over raw HTML →
rapla renders from trusted templates → no markup-injection surface at all.

**Config escape hatch (default OFF):** a permissive per-document policy variant (no allowlist,
`'unsafe-inline'`) that allows **author** inline JS — high-trust self-hosted deployments only,
all-or-nothing (CSP has no "onclick-only" granularity), **never** for AI/open-user content.

## Scope

### In scope
- The credential-containment decision (keep cookie; sandbox + capability + enforced `/app` CSP).
- The session-authenticated capability-mint endpoint (`{read}`, short TTL) reusing [PRD 076](076-scoped-api-keys-self-rotation.md) enforcement.
- Enforcing `/app` CSP (report-only → enforced).
- The GraphiQL/Swagger `gqlFetch` refresh-gap fix.
- The template-editor auth/trust constraints (the *rendering/sandbox* implementation is [PRD 097](097-event-html-templates-mustache.md)).
- The **interactive document tier** security model (two CSP tiers, script allowlist, component-registry
  gating, native-save capability, sanitizer) — see § "Interactive document tier".

### Out of scope
- The document-template engine, storage, Mustache rendering, editor UX, **component catalog/registry
  feature** — **[PRD 097](097-event-html-templates-mustache.md)** (this PRD owns only the security/CSP/capability model for it).
- Login/OAuth/session issuance/refresh/revocation/single-issuer — **[PRD 072](072-server-side-login-dialog.md)** (shipped).
- API-key scope vocabulary itself — **[PRD 076](076-scoped-api-keys-self-rotation.md)** (reused, not changed).

## Plan

**Ordered by risk/impact:** the side-effect-free, high-impact CSP baseline goes **first** (Phase 1);
the 097-coupled containment work is the middle; the churn-prone `script-src` backstop is the deferred
tail (**Phase 6**). Every phase is independent enough to land on its own.

### Phase 1 — Enforce the non-script `/app` CSP header (side-effect-free; highest priority)

Promote this to first: it is a one-branch change, code-verified zero side-effect, and delivers
immediate hardening (exfiltration confinement + injection-surface closure) with no dependency on 097.

**Mechanism note:** `script-src` for the SPA is **not** in this header — `CspPolicyBuilder.build`
deliberately omits `script-src`/`default-src` (the Angular build's `autoCsp` is meant to own it, but
`autoCsp` is **not enabled** today, so the SPA currently has no `script-src` at all; Angular's framework
escaping + the confirmed absence of `bypassSecurityTrust*` are the only active in-SPA script defense).
That is exactly why enforcing this header is safe — it can't touch script/style. The `script-src`
backstop is **Phase 6**.

- [x] **LANDED 2026-07-10.** In `RaplaCspHeaderWriter`, added a `path.startsWith("/app")` branch that
      writes the **existing** `CspPolicyBuilder.build` policy under the **enforce** header (before the
      report-only `else`). One branch, **no policy change**. Scoped to `/app` only — `/login` (inline
      script) and the explorers `/graphiql`,`/swagger-ui` stay report-only. Verified: unit
      `RaplaCspHeaderWriterTest` (`/app` enforced; `/login`+explorers report-only) + tier-3
      `SecurityHeadersTest.spaAppGetsEnforcedNonScriptCsp` (enforced through the real filter chain,
      no `script-src`, no report-only header).
      **Tier C resolved:** `frame-ancestors 'none'` was included — it's zero new risk because
      `X-Frame-Options: DENY` is already globally enforced (the SPA is already un-embeddable), and
      `/rapla/**` + `/api/**` already enforce `frame-ancestors 'none'`. No embedding-check blocker.

Directives split by risk/impact (all in one header; a cautious deploy can stage them via a second
report-only header, but tiers A+B are code-verified safe):

- **Tier A — zero breakage risk, do immediately:** `object-src 'none'`, `base-uri 'self'`,
      `form-action 'self'`, `frame-src 'none'`. Verified: no `<object>`/`<embed>`, `<base href>` is
      `'self'`-compatible (validated in the report-only walk), no native `<form>` submit, no iframe in
      `rapla-angular/src`. *(Caveat: `frame-src` will need `'self'` once the Phase-4 editor preview iframe
      lands — loosening later doesn't break anything.)*
- **Tier B — biggest security impact, low risk:** `connect-src 'self' <IdP>`. Confines exfiltration to
      the own origin. Verified no cross-origin `fetch`/WS in the SPA (cookie-only, same-origin `/api`;
      `angular-oauth2-oidc` removed in 072 Phase 4), so nothing to whitelist. **Does not break auth** —
      the OAuth flow is top-level navigations (`/login` → `/oauth2/authorization/{id}` → IdP → callback →
      `/app`), which CSP fetch/form/frame directives do not govern.
- **Tier C — one prerequisite check:** `frame-ancestors 'none'`. Assumes rapla is **never embedded in an
      iframe** by a deployment (e.g. a DHBW portal/LMS). Confirm first; if embedded, use `'self'`/the
      specific ancestor, or start `frame-ancestors` in a report-only header until confirmed.

### Phase 2 — Capability-mint endpoint
- [ ] Session-authenticated endpoint issues a short-TTL JWT carrying `scopes:{read}` via the existing
      scoped-token path (`ApiKeyScopeContext` + `@requiresAccessDetails`); no new decoder. Verify it
      requires the session and never issues a scope broader than `{read}`.

### Phase 3 — Explorer/editor refresh-gap fix
- [ ] Extract one `refreshableFetch` in the GraphiQL shell; route the fetcher **and** `gqlFetch` through
      it. Check Swagger-UI. Establish the "single refresh-aware fetch" rule for the template editor.

### Phase 4 — Untrusted render path hardening (jointly with [PRD 097](097-event-html-templates-mustache.md))
- [ ] One "render untrusted document/preview" server path that *always* emits `CSP: sandbox` +
      `connect-src 'none'` + `script-src` lockdown, so the sandbox cannot be forgotten per-route.
- [ ] Template-editor trust split wired (chrome cookie; preview sandboxed iframe + capability/baked-in).

### Phase 5 — Docs
- [ ] `docs/authentication.md`: record that the cookie model is retained and *why* the memory-token
      migration was rejected (so it is not re-proposed), plus the capability-token model + the matrices.

### Phase 6 — `script-src` XSS backstop (DEFERRED — the whole phase waits for the SPA to stabilize)

Everything here is deferred: it hardens XSS *inside* the trusted SPA (a separate axis from the
untrusted-page containment, already carried by Phases 1–4) and churns against active SPA development.
The report-only header **stays on** meanwhile so violation telemetry keeps flowing; Angular's framework
sanitization is the interim in-SPA defense.

- [ ] Enable Angular **`autoCsp`** (or ship a `<meta>` CSP: `script-src` nonce/hash + `'strict-dynamic'`,
      no `'unsafe-inline'`/`'unsafe-eval'`). An **Angular-build** change, not `SecurityConfig`.
- [ ] `style-src` for Material/CDK inline styles — take `'unsafe-inline'` (low-risk; styles don't
      execute) rather than fighting nonce plumbing.
- [ ] **Last-of-the-last (optional):** `require-trusted-types-for 'script'` + `trusted-types` — the
      strongest DOM-XSS defense but the highest breakage (every raw-string DOM sink, incl. transitive
      deps). Essentially opt-in "someday"; evaluate against Phase-1's collected reports.
- **Handling library CSP friction:** origin allowlists work for `connect-src`/`img-src`/`font-src`/
      `style-src` (add the origin — rapla already does this for IdP `connect-src`); for `script-src` under
      `'strict-dynamic'` host allowlists are **ignored** → whitelist by nonce/hash, and the only
      policy-wide escape hatches are `'unsafe-eval'`/`'unsafe-hashes'` (last resort). The report-only walk
      is how you discover which each library needs before flipping to enforce.
- **Kept OUT of this PRD (not merely deferred):** refresh-token rotation + reuse-detection (OQ2) — it
      touches the Swing-shared single-slot and reintroduces the multi-tab stampede; a separate,
      carefully-tested change, and the HttpOnly cookie already makes the token unstealable by JS.

## Tests

- Tier-3 MockMvc: a `{read}` capability token **can query `/api/graphql`** but a mutation is denied
  (write chokepoint), an `access_details`-gated field is withheld, and an interactive-only read (e.g.
  mail config) is refused — all via existing [PRD 076](076-scoped-api-keys-self-rotation.md) enforcement, no new gate.
- Tier-3: the scoped-token mint endpoint requires the session (401 unauthenticated) and never issues a
  scope broader than `{read}`.
- Playwright: an injected `fetch('/api/graphql', {credentials:'include'})` from a **sandboxed** 097
  document/preview page gets no usable response (opaque origin → no cookie, unreadable body); the same
  fetch to `/api/auth/session/refresh` likewise yields nothing.
- CSP (5a): `/app` responses carry the **enforced** non-script header (`connect-src 'self'`,
  `object-src 'none'`, `base-uri 'self'`, `frame-ancestors 'none'`, `frame-src 'none'`,
  `form-action 'self'`); `/login` + explorers stay report-only. (5b) once `autoCsp` lands, `/app`
  carries an enforced `script-src` with `'strict-dynamic'` + nonce/hash, no `'unsafe-inline'`.
- GraphiQL: with an expired access token, both an executed query **and** a *Load view* toolbar action
  recover via refresh (regression for the `gqlFetch` gap).

## Open Questions

- **OQ1 — `JSESSIONID` reach.** The form-login session cookie is `Path=/` and ambient; confirm (tier-3
  test) it **cannot** authenticate `/api` (bearer-only) — if it can, it is itself a ridable ambient
  credential and needs `SessionCreationPolicy.STATELESS` on the `/api` chain. *Resolution: **verified — a
  bare `JSESSIONID` cannot read `/api` data**, but NOT via the mechanism this OQ guessed.* The chain is
  **not** stateless — a form-login session *does* satisfy Spring's `.authenticated()` gate (the explorer
  pages `/graphiql` + `/swagger-ui` deliberately rely on exactly that). The guard is a **second layer**:
  `SpringSecurityRemoteSession.checkAndGetUser` derives the rapla `User` **only** from a
  `JwtAuthenticationToken` (Bearer header or `access_token` cookie promoted by `CookieToBearerFilter`); a
  form-login session carries a `UsernamePasswordAuthenticationToken`, so every `/api` data controller
  resolves *no* rapla user and answers `RaplaSecurityException` → **401**. Locked by
  `SessionCookieCannotAuthApiTest` (tier-3): one test proves the session is a *live* chain authentication
  (`/graphiql` → 200), the other proves that live session still can't read `/api/users` (→ 401). **Caveat
  / follow-up:** because the protection is the JWT-identity requirement and *not* statelessness, any future
  `/api` endpoint gated by `.authenticated()` alone — one that does NOT call `checkAndGetUser`/resolve a
  JWT — would be reachable by a bare `JSESSIONID`. Every data controller today routes identity through
  `checkAndGetUser` (or the GraphQL `JwtUserResolver`), so the surface is closed now; a broad audit +
  optionally flipping the `/api` matcher to `SessionCreationPolicy.STATELESS` as defence-in-depth is a
  worthwhile future hardening, tracked here rather than done now.
- **OQ2 — refresh-token rotation/reuse detection.** *Resolution: NOT for rapla — architecturally
  incompatible with the single-slot policy, not merely deferred.* Rotation changes the slot's value on
  every refresh; rapla runs **one shared slot per user** (logout-everywhere), where the token being
  **static** is exactly what lets multiple devices/tabs coexist. Under rotation, the moment any device
  rotates, every other device holds a now-stale token → it is rejected (logged out), and with
  reuse-detection an innocent second device is indistinguishable from a thief replaying a stolen token →
  the whole family is revoked → **global logout**. Rotation is only coherent with **per-session/per-device
  refresh records** (multi-slot) — the opposite of rapla's deliberate single-slot, multi-pod-friendly,
  logout-everywhere design; adding it would first require that redesign (new per-session storage, changed
  revocation semantics, Swing's shared-slot refresh path). Out of scope. The residual stolen-*cookie*
  risk is accepted instead, bounded by `HttpOnly` (no JS theft), the 21 d absolute cap, and `clearSession`
  (≤ 1 h).
- **OQ3 — capability delivery + baked-in vs fetched data.** Whether 097 documents bake data in
  server-side (no client credential) or fetch via capability, and how a standalone document URL's
  top-level navigation is authorized, is owned by **[PRD 097](097-event-html-templates-mustache.md)** (this PRD supplies the credential
  constraints). *Resolution:* in [PRD 097](097-event-html-templates-mustache.md).
- **OQ4 — interactive documents: embedded vs standalone.** Are they shown **inside the SPA** (→
  `postMessage`-brokered data/save, keep `form-action`/`connect-src` at `'none'`) or at **standalone
  URLs** (→ native-form save + capability + `form-action <endpoint>`)? Decides the save/data plumbing for
  the interactive tier. *Resolution:* pending.

## Decisions locked

**D1 — keep the `access_token` HttpOnly cookie; do NOT migrate to a memory-only token.** Reverses the
2026-07-08 provisional "2+4" call. The memory-token benefit is redundant with the sandbox (option 2)
that 097 needs anyway, fails against a forgotten sandbox (refresh-ride), is *worse* against SPA-XSS
(readable/exfiltratable token), and costs real complexity. Alternatives rejected: memory token (option
4), `Path=/api` cookie narrowing (no protection — destination always matches).

**D2 — untrusted pages served at an opaque origin (`CSP: sandbox`) + `connect-src 'none'`.** The single
load-bearing control. Rejected: sandbox subdomain (no subdomain control in rapla deployments).

**D3 — capability tokens are scoped `{read}` bearers reusing [PRD 076](076-scoped-api-keys-self-rotation.md), NOT a new `typ`/render endpoint.**
They *do* reach GraphQL; existing scope enforcement confines them (no writes/PII/secrets/other
endpoints). Rejected: a `typ=document`/`aud`-gated token rejected at GraphQL (reinvents the scope layer
rapla already has).

*(D4–D9 below cover the interactive document tier — see § "Interactive document tier".)*

**D4 — two document CSP tiers, selected per document by component usage.** Static (no component tags) →
`script-src 'none'` (unchanged 097 D6a); interactive (uses `<rapla-*>` tags) → allowlist + `sandbox
allow-scripts`. `connect-src 'none'` in both. The renderer picks the tier at validate-on-save from
whether the template references registered component tags.
*Why keep a `script-src 'none'` tier at all, given we do allow (allowlisted) scripts?* Because the
tiers are **automatic and least-privilege, not a burden on the author**: the ~majority of documents
(Leihschein, lists, signage) use no component, so they get `'none'` and pay nothing; only a document
that actually uses a `<rapla-*>` component opts into the allowlist tier. A blanket allowlist on every
document would widen the script surface of pages that never needed it — the static tier is free
hardening, not an obstacle.

**D5 — script loading via a deployment-owned path-scoped ALLOWLIST, not nonce.** The sandbox neutralizes
the gadget payoff, so the nonce's gadget-resistance isn't needed here, and skipping it removes the nonce
footgun. Constraints: deployment-owned (not author), exact/narrow, never a public CDN, no `'unsafe-inline'`.
Rejected: nonce+`strict-dynamic` (extra plumbing + footgun for marginal benefit); CSP host-allowlist under
`strict-dynamic` (ignored). External JS only as a specific vetted (SRI-pinned) deployment exception.

**D6 — interactivity via precompiled, form-associated rapla/plugin custom elements**, referenced by a
component **registry** key/tag (never a URL). AOT (no `unsafe-eval`), served from backend/plugin JARs.
Author never writes JS. Rejected: SSR Angular (Node sidecar cost, still needs client hydration); admin
authoring Angular templates (reintroduces SSTI).

**D7 — save via native `<form>` POST + scoped write-capability.** Cookieless (opaque origin → no session
ride), capability minted server-side for a *declared* save, short TTL, dedicated submit endpoint, §12/§16
enforced, POST not GET. Embedded docs may `postMessage`-to-parent instead. Full write-path contract —
the **submit-endpoint registry** (`form-action` allowlist, keyed `pluginname.method`), the **capability
seal** (sealed-not-hidden, `exp`-in-plaintext, trust-only-the-seal, choice-bounded-by-the-seal,
server-event-mintable), and **state-based consumption** (atomic optimistic-concurrency check; explicit
`jti` fallback for stateless actions) — is specified in § "Interactive document tier".

**D8 — untrusted-author model.** Authors (AI/user) untrusted; safety is structural (logic-less JMustache
template + declarative components); AI prefers structured-tree emission over raw HTML. Links allowed but
scheme-allowlisted (`http`/`https`/`mailto`/`#`; `javascript:`/`data:` stripped). Rejected: trusting admin
authorship to allow author scripts — the *viewer* must be protected regardless of author.

**D9 — author-JS config escape hatch, default OFF.** A permissive per-document policy variant for
high-trust deployments only; all-or-nothing; never for AI/open-user content.
