# PRD 097 — Event HTML templates (stored Mustache over GraphQL views)

**Status:** draft — 2026-07-08
**Related:** [PRD 074](074-graphql-declarative-views.md) (declarative GraphQL views — the stored-view + `@view` mechanism this reuses),
[PRD 077](077-calendar-model-graphql.md) (calendar-model & saved views over GraphQL — render-modes, saved-view persistence),
[PRD 078](078-spa-graphql-view-renderer.md) (SPA view renderer), [PRD 030](030-server-side-view-rendering.md) (server-side view rendering — `CalendarLayoutEngine`, parks
the "HTML autoexport → engine" migration this PRD's grid phase would consume), [PRD 095](095-month-grid-render-mode.md) (SPA
month-grid — proves GraphQL feeds a calendar grid, but layout in TS not a template),
[PRD 011](done/011-spring-boot-4-jackson-3.md) (Jackson 3 — the runtime is Jackson-2-free, which constrains engine choice),
[PRD 098](done/098-server-artifact-store.md) (server artifact store, DONE 2026-07-08 — templates are stored as kind=TEMPLATE artifacts
there, NOT in a new preferences key; the generic artifact CRUD/upload endpoint and the IMAGE
mimeType guard were deliberately left to this PRD as the store's first consumer),
[PRD 102](102-browser-credential-hardening.md) (browser credential hardening — owns the auth/credential constraints for the semi-trusted
document pages + template preview this PRD renders: `CSP: sandbox` opaque origin, `connect-src 'none'`,
scoped `{read}` capability tokens, and the "one un-forgettable render-untrusted path" invariant)
**Use-case catalog:** [`docs/usecases/htmltemplates.md`](../usecases/htmltemplates.md) — 40 collected
use cases (real university-deployment evidence, data sources, tiers, auth classes, link navigation;
gathered 2026-07-10), the grounding for OQ1 and the phase cut

## Abstract

Let admins/groups store **HTML templates** that are filled with event data on request — the
document analogue of the stored GraphQL views ([PRD 074](074-graphql-declarative-views.md)). A stored **GraphQL view supplies the data
(and its §12 authorization); a stored logic-less template arranges it into HTML**; the browser turns
HTML into PDF via native print. The measurable end state: an admin saves a template referencing a
view, and `GET`ting the rendered document returns permission-safe HTML that `window.print()` saves as
a vector PDF — with zero new expression-evaluation attack surface.

**Direction (refocused 2026-07-09).** The primary purpose is *server-side document rendering*, in two
shapes: **event documents** (Leihschein, lists, letters — the new capability) and, mid-term, a
**replacement for the rigid hand-coded calendar export pages** (`AbstractHTMLCalendarPage` and the
`/rapla/calendar` family), which today can only be changed by editing Java. Rendering *SPA views*
through Mustache is explicitly **secondary** and drips in last (Phase 7): the SPA's calendar surfaces
are interactive (drag-create, drag-move, selection, popups) and most of that dynamism does not want to
live in a logic-less template. Engine + editor come first; the publish/anonymous-URL authorization
model comes last (Phase 8 below — *this* PRD's Phase 8; every reference to [PRD 072](072-server-side-login-dialog.md)'s Phase 8 is
spelled out with its PRD number), because the calendar replacement can inherit the existing routes'
auth unchanged.

## Motivation

Today the server renders HTML only by hand (Java text blocks / `PrintWriter` / `StringBuilder` in
`LoginPageController`, `AbstractHTMLCalendarPage`, etc.) and event notifications are plain text.
There is no way for an admin to define a print/letter/list layout for events without a code change.
The stored-view mechanism already proves the pattern: user-authored, server-stored, validated,
group-scoped, filled on request. This PRD applies the same pattern to *presentation*.

The same rigidity is what makes the **calendar export pages** hard to evolve: their HTML is assembled
in Java (`rapla-server/.../plugin/abstractcalendar/server/AbstractHTMLCalendarPage.java`), so every
deployment that wants a different export layout needs a `custom/` overlay or a patch. [PRD 030](030-server-side-view-rendering.md) already
parked this as "future migration of HTML autoexport calendar pages to `CalendarLayoutEngine` is a
separate PRD" — Phases 5–6 below **are** that PRD: the layout engine produces a positioned model, a
stored template paints it, and the frozen §15 routes keep serving the same URLs with the same
authorization.

## Implementation

### The pairing model — a "document" = view + template

Both halves are stored the same way (the [PRD 098](done/098-server-artifact-store.md) server artifact store — admin/group-authored,
validated at save; originally sketched on system `Preferences` like `StoredViewData`, superseded
2026-07-08 by [PRD 098](done/098-server-artifact-store.md)). A document pairs a **data source** (a stored
GraphQL view, by reference) with a **presentation** (a Mustache template):

```
DocumentTemplate(name, viewName, mustacheTemplate, defaultVariables, isPublic, groups)
                         │                │
               existing StoredView   new presentation layer
```

### Render pipeline (server-side)

```
render(docName, variables, caller):
  1. doc    = documentCatalog.find(docName)                     // new, mirrors ViewCatalogService
  2. view   = viewCatalog.findView(doc.viewName)                // PRD 074, existing
  3. result = executeGraphQL(view.queryText, vars, caller)      // existing view execution
  4. data   = result.toSpecification().get("data")              // Map<String,Object>
  5. html   = Mustache.compiler().compile(doc.mustacheTemplate).execute(data)
  6. return html                                                // → browser → window.print()
```

The GraphQL result JSON tree **is** the Mustache data model — no impedance mismatch: lists → sections
`{{#appointmentBlocks}}…{{/appointmentBlocks}}`, fields → `{{name}}`, booleans → conditional sections
`{{#canModify}}…{{/canModify}}`, null → renders empty.

### Security — ownership boundary (read this first)

Security here splits across two PRDs, and keeping the split straight is what keeps both readable:

- **097 (this PRD) owns the engine side:** the logic-less no-SSTI guarantee (D2/D3), the jsoup
  **sanitizer** that strips `<script>`/handlers from the author body (D6), the HTML-escaping rule for
  interpolated data (D6), and the document = view + template pairing model.
- **[PRD 102](102-browser-credential-hardening.md) owns the browser-containment side:** the
  `CSP: sandbox` opaque origin, `connect-src 'none'`, the **script allowlist** (102 D5 — a
  deployment-owned path allowlist, **not** a nonce), the two CSP tiers (102 D4), interactive
  components (102 D6), native-form save + write-capability tokens (102 D7), and the `{read}`
  capability bearers for data (102 D3). Every CSP directive and every token is 102's; 097 Phase 9
  *implements* that spec where the document-engine code lives.

The rule of thumb: if it is an **HTTP response header or a credential**, it is 102's; if it is what
the **template engine does to the author's markup and the query's data**, it is 097's. The
subsections below cover the 097 side; they reference 102 for the containment they rely on rather than
restating it.

### Security — two "no-eval" layers in series

- **No SSTI:** the template engine is **logic-less** — no expression language, nothing to inject
  (same structural guarantee GraphQL gets from parse + schema-validation + no-eval, see
  `ViewCatalogService.validate` + `WhereEvaluator`).
- **No data leak:** the query already passed schema validation + §12 `canRead` filtering at the
  resolvers + `DEPTH_CAP`. Mustache has **no independent data access** — it can only render what the
  authorized query returned. It cannot reach anything the GraphQL layer didn't already release.
- **No XSS:** Mustache `{{ }}` auto-escapes the interpolated query values into HTML.
- **No ambient authority even if XSS slips through:** document responses carry
  `Content-Security-Policy: sandbox` → opaque origin, no cookies/storage/readable API responses
  (D6a) — so a script on a document page has nothing to read even though the HttpOnly
  `access_token` cookie is ambient on the origin ([PRD 102](102-browser-credential-hardening.md) D1, 2026-07-09, kept the cookie and
  rejected the memory-only token). Containment here rests on the `sandbox` opaque origin, not on
  the absence of an ambient credential.

### View complexity tiers — where the engine is / isn't needed

The flat/grouped vs 2D-positioned distinction decides feasibility. **Mustache can paint but not
compute** — it has no arithmetic/overlap/positioning:

| Tier | Views | Needs | GraphQL+Mustache directly? |
|---|---|---|---|
| **Flat** | events (reservation list), appointments (block list) | nothing | ✅ trivial |
| **Grouped (1D)** | per-day (`appointments_per_day`), per-resource, week-as-list | a **group-by-key projection** (cheap resolver field or thin Java pass) — *not* the layout engine | ✅ nested sections |
| **2D grid** | week, month (time×day, overlap columns, spanning bars) | **`CalendarLayoutEngine`** ([PRD 030](030-server-side-view-rendering.md) — deleted `f4e9c048`, resurrectable from git; pure-Java strategies) to emit positioned `RenderedBlock`s, *then* Mustache paints | ⚠️ deferred phase |

Grouping is 1-dimensional bucketing (`groupBy(date)`); the grid is 2-dimensional positioning with
overlap resolution — categorically different. Most "calendar" list/agenda views fall in the easy
tiers; only true time-grids need the engine.

### PDF — browser-native, no library

`window.print()` + `@media print` CSS. Native vector PDF (selectable text), identical across
Chrome/Edge/Firefox/Safari (all have "Save as PDF" in the print dialog; Firefox since 82). **No
server-side PDF** (decided out). **No JS PDF lib** (jsPDF/html2pdf produce raster-image PDFs) unless
a *dialogless one-click* download turns out to be a hard requirement — see OQ2.

## Goal

- An admin saves `DocumentTemplate("termine_liste", viewName:"rapla_appointments", mustache:"…")`.
- `GET /api/documents/termine_liste?…vars` returns HTML whose event rows are **byte-identical** to the
  visible-only subset for a non-admin caller (tier-3 MockMvc leak test, §12).
- The HTML prints to a vector PDF via the browser with no added dependency.
- Arch/unit test: a template containing an injection payload (`{{T(java.lang.Runtime)…}}`) renders it
  as inert empty text — no evaluation.

## Scope

### In scope
- `DocumentTemplate` storage as kind=TEMPLATE artifacts in the [PRD 098](done/098-server-artifact-store.md) store + CRUD +
  `isPublic`/`groups` visibility (via `ArtifactCatalogService`), reusing the `ViewCatalogService` shape.
- JMustache engine wiring (server-side, fed `Map` data — no Jackson path, see D2).
- Render endpoint (REST `GET /api/documents/{name}` or a GraphQL field — see OQ4) with §12 leak test.
- Flat + grouped (1D) views: events, appointments, per-day.
- A `@media print` stylesheet + a "Print / PDF" action that opens the rendered HTML and calls
  `window.print()` — the Leihschein flow (Phase 2), session-authenticated.
- A **template-authoring UI** (static admin page, GraphiQL-style — see Phase 4) with an
  engine-truthful live preview.
- **2D time-grid HTML** (week/month) via `CalendarLayoutEngine` → positioned model → template
  (Phase 5) — moved *into* scope 2026-07-09: it is the prerequisite for the calendar-page
  replacement, which is now a primary goal, not a nice-to-have.
- **Replacement of the hand-coded HTML calendar export pages** behind the existing §15-frozen routes
  (Phase 6), authorization inherited unchanged from `CalendarPageController` (publish flag +
  optional URL encryption).

### Out of scope
- Rendering the SPA's *interactive* calendar surfaces through Mustache. Phase 7 drips in the static
  parts only; drag-create/drag-move/selection/popups stay TS (PRDs [094](094-spa-main-view-actions-and-popups.md)/[095](095-month-grid-render-mode.md)/[100](100-spa-block-renderer-unification.md)/[101](101-transpose-anchors-move-copy-paste.md)).
- New anonymous/publish URL shapes for documents — Phase 8, deliberately last (see "Authorization").
- Server-side PDF generation (openhtmltopdf/PDFBox) — explicitly rejected.
- Any expression-language / sandboxed-eval engine (Thymeleaf, FreeMarker, liqp-with-custom-filters).

## Plan

### Phase 1 — Storage + engine
- [x] `DocumentTemplate` = kind=TEMPLATE artifact in the [PRD 098](done/098-server-artifact-store.md) store; `DocumentCatalogService`
      is a thin consumer of `ArtifactCatalogService` (no new preferences key — superseded
      2026-07-08 by [PRD 098](done/098-server-artifact-store.md), which is DONE: the store + catalog are live, gate cleared).
- [x] Add JMustache dependency (`com.samskivert:jmustache`, ~100 KB, zero transitive deps).
- [x] Unit: SSTI-inert test (injection payload renders empty), auto-escape test.

### Phase 2 — Render pipeline (flat views) + the Leihschein flow
- [x] Render endpoint: resolve doc → execute referenced view (caller-scoped) → feed `data` Map to
      Mustache → return HTML. **Credential-agnostic**: authorization comes from the standard Spring
      Security chain, so the same controller serves a cookie session or a Bearer header
      interchangeably (the HttpOnly `access_token` cookie is the established credential — [PRD 102](102-browser-credential-hardening.md) D1,
      2026-07-09, kept it and rejected the memory-only-token plan).
- [x] Variables (e.g. `eventId`) are ordinary request parameters. Security = the referenced view
      executes in the **caller's §12 read-scope**; unreadable and non-existent must be **byte-identical
      404s** (existence never leaks). Both artifacts — template *and* referenced view — must be
      visible to the caller, else 404.
- [x] Tier-3 MockMvc **leak test** (non-admin, mixed visible/hidden ids → byte-identical to
      visible-only, and to the all-non-existent case). AGENTS.md §12 — mandatory, not optional.
- [x] "Print / PDF": `@media print` stylesheet (hides chrome, `@page` margins) — shipped in
      `DocumentShell`. **The SPA action moved to Phase 7** with the rest of the SPA surface.
      *(Deviation from D4, adopted 2026-07-10: the shell is **script-free**, so there is no print
      button and no auto-print — printing is the browser's own Ctrl+P, announced by a print hint that
      hides itself when printing. This is D6a's "script-free print affordance" branch, and it is what
      lets the response carry `script-src 'none'` with no nonce plumbing and no `allow-scripts` in
      the sandbox.)*
      `window.open(url)` suffices (the `access_token` cookie is HttpOnly and auto-attached,
      `CookieAuthSupport.java:77`). [PRD 102](102-browser-credential-hardening.md) D1 (2026-07-09) kept that cookie — the memory-only-token
      plan was rejected — so this stays a plain navigation permanently; no `fetch` + `Authorization`
      → Blob fallback is needed.

### Phase 3 — Grouped (1D) views
- [x] Group-by-key projection for per-day (resolver field `appointmentBlocksByDay` **or** thin Java
      pass — OQ3), nested-section template. Completes the event-document shapes (lists, per-day
      agendas, the Leihschein of Appendix A).

### Phase 4 — Template-authoring UI (the GraphiQL analogue for the presentation layer)

GraphiQL is the tooling for the **data** layer (the view); this phase gives the **presentation**
layer its equivalent — an editor where an author composes a document (view + template + CSS)
with immediate, engine-truthful feedback. Motivation: this is also where **AI-assisted authoring**
lands — describe → AI generates the template → live preview validates → iterate. Without an
engine-truthful preview, AI generation is a blind flight.

**Delivery shape (decided 2026-07-09): a static admin page after the GraphiQL pattern — NOT part
of the Angular SPA.** Like `static/graphiql/index.html`: a `static/template-editor/` page loading
its editor from CDN, vanilla-JS toolbar speaking `fetch` against the server endpoints; the SPA
only links to it from the admin menu. This drops the whole Monaco-in-Angular embedding cost
(lazy-loading, worker config, bundle weight) and makes `/graphiql` (data authoring) and
`/template-editor` (presentation authoring) two identically-built admin tool pages — same
cookie-session auth, same CDN trade-off (both need internet access; if an air-gapped deployment
ever needs them, serving the assets locally fixes both pages together).

**Editor engine (decided 2026-07-09): Monaco** — the same engine GraphiQL 4+/5 (which rapla embeds
at 5.2.1) runs on, so admins get identical editor behaviour across both tool pages; MIT-licensed,
framework-free (no React needed on our page). Monaco's built-in `handlebars` language mode covers
Mustache **highlighting only** (a Monarch tokenizer — it validates nothing; Handlebars-only syntax
like `{{#if}}` highlights fine but is caught by the engine-truthful validation below, which is the
actual "restrict to JMustache" mechanism). Web research 2026-07-09: no Mustache LSP / ready-made
data-aware completion exists anywhere (Monaco feature request microsoft/monaco-editor#1731 open for
years); everyone who has Mustache completion built a schema source themselves (Ember→Glint/TS,
Qute→param declarations) — ours is the referenced view's selection set, the nicest of the lot.

- [x] **Preview endpoint** `POST /api/documents/preview` — body `{ template, viewName, variables }`.
      Renders with the **real engine** (JMustache — NOT a client-side mustache.js, whose output can
      differ), against the **real view data executed in the author's §12 read-scope** (author sees
      only their own data — no leak). Applies the same D6 pipeline: `<script>`-strip + sandboxed
      `<iframe>` + CSP, so the preview itself is never an XSS vector against the author.
- [x] **Static editor page** (`static/template-editor/`): view picker + variables input + Monaco
      template editor + live preview pane (renders the preview HTML in a sandboxed iframe) +
      load/save toolbar (the `graphiql/index.html` toolbar pattern against the template CRUD).
- [x] **Result-shape endpoint** (~100–200 lines): parse the referenced view's query with the
      existing `graphql.parser.Parser` (+ schema walk for alias resolution and list-vs-object) and
      return its selection-set tree as JSON — the single source for the fields pane, completion,
      and unknown-field warnings.
- [x] **Available-fields pane** — the GraphiQL-schema-explorer analogue, fed by the result-shape
      endpoint, so the author knows which `{{fields}}` exist.
- [x] **Field completion** (~150–250 lines JS, separately unit-testable): a Monaco
      `CompletionItemProvider` on `{{` contexts; walks the open-section stack above the cursor
      (`{{#…}}`/`{{^…}}`/`{{/…}}`) to descend the result-shape tree, proposes context-valid fields
      and the closing tag of the open section. Suggests **only JMustache-valid constructs** — no
      Handlebars helpers.
- [x] **Engine-truthful validation**: server compiles with the real JMustache
      (`MustacheParseException` carries the line number) → editor shows red markers via
      `setModelMarkers`, debounced; `saveTemplate` uses the same check as its save gate (the
      `saveView` pattern). Optional second tier: traverse the compiled template with JMustache's
      `Mustache.Visitor`, cross-check variable/section names against the result-shape tree →
      yellow unknown-field warnings (a missing field can be intentional — Mustache renders empty).
- [ ] Engine-agnostic: the preview renders with whichever engine the DevOps flag selects (D2) — the
      tooling does not change between Mustache and Handlebars (Monaco's mode is named `handlebars`
      anyway; validation follows the real compiler automatically).
- Note: public JS playgrounds (handlebarsjs.com "Try", online mustache testers) are fine for
  *learning syntax* but render with the JS impl + no access to our data — not usable for authoring
  against our server engine/§12 data, hence the server-backed preview.

### Phase 5 — 2D time-grid rendering (the layout engine half)
- [ ] Resurrect/port `CalendarLayoutEngine` ([PRD 030](030-server-side-view-rendering.md) / [PRD 024](024-server-side-edit-services.md) Phase 3 read-side core) so it emits a
      **positioned model** — lanes, slots, block geometry — as a plain data tree.
- [ ] A stored template paints week/month from that model; the template does no layout maths (a
      logic-less engine cannot, by construction). Colour resolution + always-black text per
      `docs/architecture/calendar-rendering.md`.
- [ ] Golden-output tests against the current `AbstractHTMLCalendarPage` rendering for a fixture
      calendar — the diff is the migration's acceptance criterion.

### Phase 6 — Calendar-export page replacement (the primary strategic goal)
- [ ] Swap the hand-assembled HTML in `AbstractHTMLCalendarPage` for a stored template rendered by
      the Phase-5 model, **behind the existing routes**: `/rapla/calendar(.csv)?`,
      `/rapla/internal_calendar(.csv)?`. These URLs are 🔒-frozen (AGENTS.md §15 — external
      subscribers depend on them literally); the migration changes the renderer, not the address.
- [ ] **Authorization is inherited, not rebuilt**: `CalendarPageController` already gates on the
      per-calendar publish flag (`AutoExportPlugin.HTML_EXPORT`, `CalendarPageController.java:169`)
      and the URL-encryption preprocessor decrypts params before the controller sees them
      (`ServerServiceConfig.java:221`). That is why this phase can land long before Phase 8.
- [ ] Ship a default template reproducing today's output, so an untouched deployment sees no change;
      the win is that an admin can now edit it.
- [ ] Free request parameters stay validated against the published artifact's own configuration —
      the existing `allocatable_id` ∈ `model.getSelectedAllocatablesAsList()` check
      (`CalendarPageController.java:176-199`) is the pattern to preserve, not to loosen.

### Phase 7 — SPA integration (secondary, drip-in)
- [ ] Render the **static** parts of SPA view surfaces through stored templates where it buys
      admin-editability. Interactive behaviour (drag-create, drag-move, selection, popups, undo
      toasts — PRDs [094](094-spa-main-view-actions-and-popups.md)/[095](095-month-grid-render-mode.md)/[100](100-spa-block-renderer-unification.md)/[101](101-transpose-anchors-move-copy-paste.md)) stays in TypeScript: a logic-less template cannot carry it, and
      forcing it in would trade a working interaction model for an editable one.
- [ ] Deliberately last: nothing above depends on it, and the boundary between "static enough for a
      template" and "must stay TS" is best drawn once the engine and editor exist in practice.

### Phase 8 — Publish + capability URLs for document routes (last)
- [ ] Only needed for **new** document URLs that must serve callers who cannot log in (an external
      borrower, a display, a machine). The Phase-6 calendar routes need none of this — they inherit.
- [ ] Two document classes, decided per document, never per request:
      **published** (opt-in flag, anonymous, publisher's scope, variables pinned or validated against
      the document's declaration, revocable) vs **session** (free variables, caller's §12 scope, no
      shareable link). Personal documents — the Leihschein — are session documents by nature.
- [ ] If a capability URL is built, reuse `UrlCipherV2` (AES-256-GCM, authenticated — tampering with
      an encrypted `eventId` breaks the tag) with **two additions**: an `exp` claim inside the
      plaintext (a permanent bearer URL printed in a page header is a standing credential), and a
      **separately derived key**, because the calendar root key is effectively unrotatable (the
      legacy ECB path is "kept forever — old subscriber URLs live in the wild", `UrlCipherV2.java:23`).
- [ ] `Referrer-Policy: no-referrer` on rendered documents; mask the token query parameter in access
      logs.

### Phase 9 — Interactive tier: components + native save

Documents that carry **rapla UI components** (dropdowns, nav buttons, pickers) and can **save**, while
the author stays untrusted (AI/user). **The security model + decisions live in
[PRD 102 § Interactive document tier](102-browser-credential-hardening.md#interactive-document-tier-untrusted-authored-components--native-save) (D4–D9);** this phase is the
*implementation* of that spec, built here where the document-engine code lives. Static documents are
unaffected (they keep `script-src 'none'`; see the current `RaplaCspHeaderWriter.documentPagePolicy`).

**The decisions being implemented** (full text + rationale in [PRD 102 § Decisions locked](102-browser-credential-hardening.md#decisions-locked)):
- **D4** — two document CSP tiers, selected per document by component usage (static `script-src 'none'`;
  interactive allowlist + `allow-scripts`; `connect-src 'none'` in both).
- **D5** — script loading via a deployment-owned path-scoped **allowlist, NOT nonce** (sandbox carries
  containment; no `'unsafe-inline'`; never a public CDN; external JS only as a vetted/SRI exception).
- **D6** — interactivity via **precompiled, form-associated** rapla/plugin custom elements, referenced by
  a **component registry** key/tag (never a URL). Rejected: SSR Angular (Node sidecar); admin-authored
  Angular templates (SSTI).
- **D7** — save via **native `<form>` POST + scoped write-capability** (cookieless/opaque → no session
  ride; declared save; short TTL; POST not GET; embedded docs `postMessage`-to-parent instead).
- **D8** — **untrusted-author** model: structural safety (logic-less JMustache + declarative components);
  AI emits a structured tree, not raw HTML; links scheme-allowlisted (`javascript:`/`data:` stripped).
- **D9** — **author-JS config escape hatch, default OFF** (high-trust deployments only; never AI/open).

- [ ] **Per-document CSP tier selection** (102 **D4**): in `RaplaCspHeaderWriter`, choose the document
      policy from whether the validated template uses registered component tags — static → `script-src
      'none'` (unchanged); interactive → the allowlist policy + `sandbox allow-scripts`. `connect-src
      'none'` in both.
- [ ] **Deployment-owned script allowlist** (102 **D5**): config of exact/narrow path-scoped script
      sources (rapla + plugin component paths); no `'unsafe-inline'`; **no nonce** (the sandbox carries
      containment); never a public CDN. External JS only as a specific vetted (SRI-pinned) exception.
- [ ] **Component registry** (102 **D6**): `key → { bundleUrl, tags, attributes, capabilityNeeds }`,
      populated by rapla + plugins (JAR `META-INF/resources`; ESM bundles served with
      `Access-Control-Allow-Origin: *`) + deployment. Authors reference by **key/tag, never URL**; the
      registry drives the allowlist match, the sanitizer tag allowlist, and capability minting.
- [ ] **First precompiled, form-associated custom element** (102 **D6**): AOT (no `unsafe-eval`),
      `formAssociated` + `ElementInternals.setFormValue` so its value submits with a native form.
- [ ] **Native-form save endpoint + write-capability** (102 **D7**): `POST …/submit` validates a scoped
      **write** capability (cookieless, opaque-origin `Origin: null`), minted server-side at render for a
      *declared* save (entity+fields+action, short TTL); performs only that save, §12/§16 enforced; POST
      not GET. `allow-forms` + `form-action <endpoint>` for standalone docs; embedded docs `postMessage`
      to the SPA parent instead (**OQ4 in 102** decides embedded vs standalone).
- [ ] **Form authoring — how a template names its submit target (097's side of 102 D7's registry).**
      The author writes a native `<form action="pluginname.submitMyAvailableTimes">` — a **literal
      registry key**, never a URL. Validate-on-save resolves the key against the submit-endpoint
      registry (102): **hit** → the renderer sets the real `action`, pins `form-action`, injects the
      sealed capability (hidden field), flips to `sandbox allow-forms`; **miss** → invalid document,
      surfaced not deleted (same as a dangling view reference). Hard rules: the action key must be a
      **literal, never `{{interpolated}}`** (a render-time value could aim the form after save-time
      validation ran); **strip `formaction`** on buttons (it would override the pinned target — use
      `name`/`value` to distinguish sub-actions, e.g. `<button name="decision" value="accept">`);
      forbid `type=password`. The author never writes a URL, a credential, or a CSP directive — only a
      form the server has already aimed, sealed, and contained. Worked shapes: the two write-back use
      cases in [`docs/usecases/htmltemplates.md`](../usecases/htmltemplates.md) § "Formulare & Workflows".
- [ ] **Render-as-service with baked-in data** — the render pipeline (`DocumentRenderService`) is a
      plugin-callable bean that renders a template (mail body, decision page) from **caller-supplied
      data**, not only a view executed in the caller's §12 scope. The approval/slot flows have **no
      caller scope** (the clicker may not be logged in); the plugin resolves the request's details with
      its own authority and passes them as the model, the seal bounding what that data is. The preview
      route already renders from a supplied map, so the seam exists.
- [ ] **Sanitizer / validate-on-save** (102 **D8**): strip author `<script>`/`on*`/unknown tags +
      `javascript:`/`data:` hrefs; allowlist registered component tags+attributes + safe layout HTML;
      `href` scheme-allowlist (`http`/`https`/`mailto`/`#`); forbid sensitive inputs (`type=password`).
- [ ] **AI structured authoring** (102 **D8**): AI emits a structured component tree (JSON), rendered
      from trusted templates (no markup-injection surface); validated identically to user templates.
- [ ] **(later) author-JS config escape hatch** (102 **D9**): permissive per-document policy variant,
      default OFF, high-trust deployments only, never for AI/open-user content.

Depends on: the shared **capability-mint primitive** ([PRD 102](102-browser-credential-hardening.md) Phase 2 — `{read}` base; this phase adds
the **write**-scoped save capability on top). Blocked on **102 OQ4** (embedded-vs-standalone) for the
save/data plumbing.

## Tests

- Tier-1: JMustache SSTI-inert + auto-escape unit tests.
- Tier-3: MockMvc leak test on the render endpoint (the §12 recipe).
- Manual: render a document, `window.print()` → PDF in Chrome + Firefox.

## Implementation findings (Phases 1–4, 2026-07-10)

Three things the implementation forced that the plan did not anticipate.

**1. A document's visibility does not cover the view it points at.** The first run of the §12 leak
test caught a real leak: `GET /api/documents` listed a *public* document whose *view* was private,
exposing the hidden view's name through the document's `viewName`. Visibility is now a conjunction —
`DocumentCatalogService.isVisible` requires `views.viewVisible(doc.viewName(), caller)` before its
own public/groups check — so listing, `findVisible` and the render path all agree. Generalises: an
artifact that references another artifact must AND their visibilities, never just its own.

**2. Sanitize the rendered output, not the template.** jsoup is an HTML parser: fed a raw template it
foster-parents `{{#rows}}` out of `<table>` and destroys the loop. `DocumentSanitizer` therefore runs
on the render *result*, which is also the only order that is actually safe — the dangerous markup can
come from the data, not just the template.

**3. `/source`, preview and result-shape are write surfaces.** A template is code that renders into
other users' browsers, so *reading* one is an authoring action: all three sit behind
`DocumentCatalogService.requireAuthor` (i.e. `ArtifactCatalogService.checkWrite`), and `save()` gates
the caller **before** validating, so a non-admin cannot probe template or view validity through the
error channel. Note the status code is **401**, not 403: `RaplaExceptionHandler` maps every
`RaplaSecurityException` to UNAUTHORIZED — the codebase has no permission-denied exception type.

Shipped: `document/{DocumentApi,DocumentController,DocumentCatalogService,DocumentRenderService,
DocumentRenderer,DocumentSanitizer,DocumentShell,DocumentEntry,RowGrouping,ResultShapeService}`,
`static/template-editor/index.html`, the sandbox CSP in `RaplaCspHeaderWriter`, and
`graphql/ViewVariables` (extracted from `StoredViewInterceptor` so the SPA transport and the document
render path resolve the date window identically).

## Open Questions

- **OQ1 — document as a separate entity vs a render-mode on the view.** PRD [074](074-graphql-declarative-views.md)/[077](077-calendar-model-graphql.md) views already
  carry render-modes (table/month). Option A: a standalone `DocumentTemplate` pairing `viewRef` +
  template (this PRD's sketch). Option B: an `html`/`print` **render-mode** on the view itself, whose
  render-meta carries the Mustache template. B is tighter integration; A keeps presentation cleanly
  separate and lets one view feed many documents. *Resolution:* **resolved 2026-07-10 — Option A
  (standalone document artifact).** Grounded in the 40-use-case catalog
  ([`docs/usecases/htmltemplates.md`](../usecases/htmltemplates.md)); two independent advocates,
  arguing A and B over that catalog, converged on the same three structural failures of B:
  1. **The D6 delegation seam closes.** [PRD 098](done/098-server-artifact-store.md) D6 pre-designed: views are delegatable (they execute
     in the *caller's* scope, §12-gated at the resolvers, worst case an expensive query bounded by
     `DEPTH_CAP`); templates/CSS render HTML into *other users'* browsers (stored-XSS surface) and
     stay admin-only. Under B one artifact carries both, so whoever may edit the query may inject
     HTML. The exam-schedule use case ("the exam planner maintains it himself") dies.
  2. **The auth class belongs to the document, not the data.** The evacuation/fire-safety list
     (session-only, personal data) and the foyer notice board (published, anonymous) share one data
     root. Under B a view carries exactly one visibility. Worse, B's own advocate found the leak: two
     templates on one view force the query to fetch the field *union*, so on the anonymous fetch —
     which runs in the publisher's scope — the sensitive fields travel and only the template omits
     them. §12 forbids exactly that.
  3. **Document-to-document links need addressable documents.** The chain timetable → lecturer →
     room → building, plus "the dunning row links to *the loan slip of that event*", addresses named
     documents with their own parameter contracts, not views.
  Also: shared `{{> letterhead}}` partials (OQ5) across document families have no home in per-view
  metadata, and letters/CSV/mail bodies would pollute `listViews` (the SPA's view picker).
  **Two corrections adopted *from* B's case:** (a) "many documents per view" is often really
  *parameter pinning* — thousands of door signs are ONE document published N times with a pinned
  `roomId`; that belongs in the Phase-8 publish mechanism, not in document multiplication.
  (b) B's ergonomic win (a print button appears where the user already is) is reachable under A as a
  catalog filter — `documents where viewName == currentView` — and should be built that way.
  **Reversibility (asked 2026-07-10):** the switch stays possible but gets costlier with time —
  free before Phase 1 (no rows exist), a local refactor + one-shot startup migration after Phases
  2–4, and effectively frozen once Phase 8 publishes URLs into the wild (door-sign displays, QR
  codes, subscriber links — the §15 lesson), because inter-document links live *inside the
  templates*. Insurance, adopted regardless of model: **the address is decoupled from the model** —
  the render route is `/api/documents/{name}` under A *and* would remain so under B (where `{name}`
  would map to view+mode). Only the address truly freezes; the model behind it stays a refactor.
- **OQ2 — is a dialogless one-click PDF download a hard requirement?** If yes, `window.print()` can't
  do it (browser security boundary) and a JS lib (raster PDF) would be needed. If a dialog click is
  acceptable, no lib — `window.print()` wins on quality. *Resolution:* **resolved 2026-07-09 — the
  print dialog is acceptable.** No JS PDF library; D4 stands unchanged.
- **OQ3 — grouping: GraphQL field vs thin Java projection** for per-day. *Resolution:* **resolved
  2026-07-09 — thin Java projection; no schema growth, no `…ByDay` resolver field.** Verified against
  the running mechanism: grouping is *already declared* in the view via `@column(group: true)`, and
  `ViewMetaInstrumentation.java:87-95` already emits `extensions.view.groupBy` (the group column's
  **alias**) plus `groupFormat` (an opaque header-format token). The SPA then buckets flat rows with
  the pure function `groupByColumn(rows, alias)` (`rapla-angular/src/app/graphql/weekday-grouping.ts`,
  [PRD 078](078-spa-graphql-view-renderer.md)) and formats each header with `formatGroupLabel(key, fmt)`. The server-side Mustache path
  needs the **same two functions in Java** (~30 lines) to turn the flat `data` rows into nested
  sections — no new GraphQL field, and the grouping *semantics* stay single-sourced in the view's
  own directive. Consequence: any view that already renders as `ViewRenderMode.grouped` in the SPA
  (the Übersicht/Wochenansicht) is server-renderable as-is. ⚠️ Parity risk to watch: `groupFormat`
  is applied client-side today; the Java formatter must produce identical headers (German locale,
  date part only, no host-timezone shift) or a server-rendered Übersicht drifts from the SPA's.
- **OQ4 — render transport: REST `GET /api/documents/{name}` vs a GraphQL field** returning HTML.
  *Resolution:* **resolved 2026-07-09 — REST.** The response is a *page*: it must be navigable,
  printable, cacheable, and carry its own `Content-Type`/CSP/`Referrer-Policy` headers (D6a). A
  GraphQL field returning an HTML string can be none of those. Follow `rest-endpoint-creation`
  (the `@HttpExchange` interface owns the route).
- **OQ5 — Mustache partials for shared layout** (`{{> letterhead}}`) — store partials too, for
  print letterhead/footer across documents? *Resolution:* pending (cheap to add later).
- **OQ6 — author-embedded `<script>` for less-trusted authors.** D6 strips `<script>`/handlers by
  default. Open: do we allow full raw HTML+JS for **admin-only** templates (they "own" the page
  anyway), gated on an author-trust flag? Or is stripping unconditional? *Resolution:* **resolved
  2026-07-08 — stripping is unconditional** ([PRD 098](done/098-server-artifact-store.md) D6): never keyed on author trust, so a later
  loosening of the write rule cannot change the security posture; "an admin authored it" is not
  "an admin's session wasn't riding". No admin raw-JS exception.
- **OQ7 — document-page auth after [PRD 072](072-server-side-login-dialog.md) Phase 8.** *Resolution:* **moot 2026-07-09 ([PRD 102](102-browser-credential-hardening.md)
  D1).** The premise was that Phase 8 would remove the `access_token` cookie, leaving a top-level
  navigation with no credential. [PRD 102](102-browser-credential-hardening.md) D1 reversed that: the HttpOnly `access_token` cookie
  **stays** (the memory-only-token plan was rejected). D7's top-level navigation is therefore
  authenticated by the ambient `access_token` cookie exactly as it is today — no `JSESSIONID` /
  page-cookie / `fetch`→Blob workaround is needed.

- **OQ8 — `kind=TEMPLATE` collides with rapla's own "template" concept.** Rapla already calls a
  Reservation annotated `RaplaObjectAnnotations.KEY_TEMPLATE` a *template/Vorlage*
  (`RaplaComponent.isTemplate`, permission group `edit-templates`), and DynamicType nameformats are
  "name templates" too — three unrelated meanings, now catalogued in
  `docs/architecture/glossary.md`. Proposal: rename this PRD's artifact kind to
  **`KIND_DOCUMENT`** (`StoredArtifact.KIND_TEMPLATE` → `KIND_DOCUMENT`) before Phase 1 writes the
  first row. It is free today — the store has no TEMPLATE producer yet, so there is nothing to
  migrate. The PRD's own vocabulary ("document = view + template") already points that way.
  *Resolution:* **resolved 2026-07-10 — renamed to `KIND_DOCUMENT`.** Only tests referenced
  `KIND_TEMPLATE`; no production producer existed, so the rename cost nothing. A document artifact's
  body IS the Mustache template; its metadata carries `viewName` + visibility + the parameter
  contract. `KIND_CSS`/`KIND_PARTIAL`/`KIND_IMAGE` keep their names (no collision).

- **OQ9 — the parameter contract: how a document's URL maps onto the view's GraphQL variables.**
  *Resolution:* **decided 2026-07-11 — a declared `@param` contract on the view (public name ↔ private
  path), NOT raw dotted GraphQL paths.** (The `@param` directive + the `inputs`-projection are a
  [PRD 074](074-declarative-graphql-views.md) *view-mechanism* change that this PRD consumes; document
  `pins`/`clamp` are this PRD's. Recorded here because 097 is where it was designed and where the
  document side lives — mirror the mechanism into 074 when 074 is next touched.)

  **The problem with the shipped provisional (dotted paths).** Phase-2 shipped `RequestVariables`
  (labelled OQ9 in its own Javadoc): `?filter.allocatableIdsIn=r1` — the URL names the **raw GraphQL
  input field**, dotted into the variables. Three faults: (1) it welds a public, long-lived URL (door
  signs, QR codes, printed links — the §15 lesson) to a schema field name, so renaming
  `allocatableIdsIn` breaks every link; (2) it leaks the query shape; (3) caller variables **override**
  the document's stored defaults, so a reader appends `&filter.limit=5000` /
  `&filter.accessibleByUsername=x` — harmless while every document is session-scoped (the caller's own
  §12 bounds it), a real data-exposure the moment Phase 8 publishes an anonymous, publisher-scope
  document (the class OQ1 killed Option B over).

  **The decision.** The **view declares its public parameters** with `@param`; the URL speaks only
  public names; anything undeclared is **rejected**, never merged:

  ```graphql
  query uebersicht($filter: ReservationFilter!)
    @view(title: "Übersicht", renderModes: [week, day, table])
    @param(name: "in",   into: "filter.allocatableIdsIn", type: ID_LIST,  role: RESOURCE_SELECTION)
    @param(name: "from", into: "filter.from",             type: DATETIME, role: DATE_RANGE_START,
           default: { anchor: WEEK_START, offset: 0 })
    @param(name: "to",   into: "filter.to",               type: DATETIME, role: DATE_RANGE_END,
           default: { anchor: WEEK_START, offset: 7 })
  { appointmentBlocks(filter: $filter) { … } }
  ```

  - **`name` public ↔ `into` private.** The URL is `?in=<id>`, not `?filter.allocatableIdsIn=<id>`.
    Rename the schema field → only `into:` changes; the URL survives. `?filter.limit=…` is rejected
    (undeclared). The query body is unchanged — the SPA still passes the whole `$filter`.
  - **`inputs` becomes a projection of `@param`** (unifying the two half-mechanisms). Today
    `extensions.view.inputs` is a *heuristic* ("a variable named `filter` of type `ReservationFilter`
    → two date controls") and its `name` is secretly the dotted path; the resource picker is
    inexpressible (bound implicitly by type via `variables`). After: `inputs` carries `param` (public
    name) + `into` + `type` + `control` + `default` per declared parameter — including a
    `RESOURCE_SELECTION` control the heuristic can't emit. One source drives **both** the URL contract
    and the SPA widgets, so they cannot drift; `variables` folds away.
  - **`role` (data) vs `control` (presentation) vs `default` (position).** `role` is what the parameter
    *is* in the variables (`DATE_RANGE_START`) — stable. The **widget is the renderer's**, per render
    mode: a *table* render draws two date fields; a *week* render draws a `◀ ▶` pager over the same
    `from`/`to`. The view declares the role; it never declares the widget.

  **The window / anchor split (decided 2026-07-11).** Window **shape + navigation step** is the
  *renderer's* (a week is 7 days stepping by 7; a month is the anchor month — mode-intrinsic, the SPA
  already owns it). The `@param default` is only the **declarative starting position**, and it is
  required wherever there is no renderer to decide:

  | Consumer | shape + step | starting position |
  |---|---|---|
  | SPA week/month/day grid | renderer (mode-intrinsic) | renderer defaults to *now*; anchor only for a non-now default |
  | SPA table / agenda | — (no implied shape) | **anchor required** — no window otherwise |
  | server document / print / email | — (no renderer) | **anchor required** — one-shot, nothing decides |

  Consequence: the anchor **must be evaluated server-side** (a Java twin of the SPA's
  `resolveAnchorOffset`), because the document render is the consumer that has no renderer and cannot
  run the client TS. This also fixes a **live bug**: `ViewVariables.mergeDefaults` today hardcodes
  Monday→Monday+7 and ignores `@view(fromAnchor:…)`, so a `TODAY,-7…+7` view renders one window in a
  document and another in the SPA. The Übersicht renders correctly only by coincidence.

  **Document-side scoping — `pins` + `clamp` (this PRD's half).** A document pins values a reader may
  not replace; `clamp` says whether a reader's URL value may *narrow* a pin:

  ```jsonc
  // document "fakultaet-tuerschild"
  { "viewName": "uebersicht", "pins": { "in": ["r1", "r2", "r3"] } }   // @param in has clamp: SUBSET
  ```
  ```
  ?in=r2   → shows r2        (r2 ∈ pinned set)
  ?in=r9   → shows nothing   (r9 ∉ pinned set — clamped out, not an error)
  (none)   → shows r1,r2,r3  (the pin)
  ```
  `clamp: SUBSET` for id sets (intersect), `NOT_AFTER`/`NOT_BEFORE` for date windows (shrink only). A
  parameter with no clamp is either pinned-and-closed or open. This is what makes a published
  document's scope a *guarantee* rather than a hope — the exact hole in the shipped dotted-path code.

  **Three declaration layers, three sinks (decided 2026-07-11).** A URL parameter is routed by *where
  it is declared*; undeclared → rejected. This partitions the URL namespace cleanly:

  | Declared on | What it is | Sink |
  |---|---|---|
  | **View `@param`** (PRD 074) | a data input | GraphQL query variable (`into: filter…`) — and also exposed in the model |
  | **Document `templateParams`** (this PRD) | a presentation / plugin / action / component input | the **Mustache model** — never GraphQL |
  | **Document policy** (this PRD) | `pins`, `clamp`, `requiresScope`, per-param `required` | the render gate |

  `templateParams` is the third layer we hadn't named: a value a plugin/action/component *referenced in
  the template* needs but the query does not (e.g. `?showPrices=true` → `{{#showPrices}}`; `?signWith=x`
  → a hidden field a submit plugin reads). It lives on the **template**, not `@param`, because the
  reference lives in the template and the view must not know the template's plugin/presentation
  concerns. Distinguish a template *param* (a value: `signWith`) from template *wiring* (the reference
  itself: `<form action="plugin.method">`, `<rapla-*>` tags — validated against the registries, not a
  reader input). Near-term minimal step: expose resolved params in the model as `{{params.x}}`; full
  `templateParams` declarations land with Phase 9 (plugins/actions/components).

  **Three scope entry points, all resolving to the one allocatable scope (decided 2026-07-11).** Decide
  group-vs-id **by the declared param, never by the value** — value-guessing (UUID vs name) is the
  polymorphic oracle OQ1 warns of. So:

  - **`in`** (`type: ID_LIST` → `filter.allocatableIdsIn`) — explicit ids.
  - **`group`** (`type: GROUP` → `filter.allocatableMatching`) — a named handle for a **stored
    `AllocatableFilter`**. A group is a *live predicate*, not a frozen id-set — "all rooms > 50 seats"
    (`typeIn:[Raum], whereRaum:{seats gt 50}`) forced this; a hand-picked set is just `idIn:[…]`.
    Referenced by a **namespaced unique name** (`all-rooms` global / `owner_name` user-defined, the
    calendar `userName_exportName` convention). Unifies with the export-alias idea (an export is a named
    filter too). **Groups are deferred** — forward-declared in schema+docs now, resolver wired later.
  - **declared dimension params** (`minSeats` → `whereRaum.seats.gt`, `q` → `searchText`) — ad-hoc
    tuning of a *specific* predicate the author chose to expose. An arbitrary predicate the author did
    NOT declare is deliberately not URL-expressible (make it a group instead).

  **Two kinds of `required` (decided 2026-07-11).** They are different primitives:

  - **Param-level `required: true`** — one *specific mandatory* input. The event-document case: a
    Leihschein / booking-request is *about* one `eventId` (`query leihschein($eventId: ID!)` — also
    GraphQL non-null). Missing → empty render, not a firehose, not a crash.
  - **Document `requiresScope`** — *at least one* RESOURCE_SELECTION input (`in`/`group`/dimension) or a
    pin must resolve non-empty; else render empty. Enforced by **short-circuiting to an empty render
    before the query runs** — never by passing an empty filter (an empty scope reads as *unscoped =
    all*, §12-checked below). This reinstates the legacy "no selection → nothing" at the document layer.

  **SPA-exempt by construction.** `pins`/`clamp`/`requiresScope`/`required` live in
  `DocumentRenderService`; the SPA reaches views via `/api/graphql` → `StoredViewInterceptor` and never
  runs that code, so it obeys none of the policy (it defaults to "all readable", browse-then-filter) —
  no `if (spa)` branch needed. The only shared obligation is GraphQL's own non-null (`$eventId: ID!`),
  which is the query's, not the document's.

  **§12 verified (2026-07-11) — the resolver is correct as-is; do not change it.** Every reservation
  passes `pc.canRead(r, caller)` (`ReservationGraphQLController:262`) and every contained allocatable
  passes `canReadAllocatable` (`StructuralTypeFetchers`), so an unresolvable/unreadable scope → empty
  (never everything), and an unreadable resource on a readable event is dropped from its column.
  GraphQL's "no filter = all readable" is intended and differs from old Swing's "no selection =
  nothing"; the legacy convention is restored at the **document** layer via `requiresScope`, NOT by
  changing the resolver.

  **Interim scoping — what works TODAY, before groups + `@param`:**
  - **Single-id documents** (Leihschein) — `?eventId=<id>` already works (top-level scalar via
    `RequestVariables`); §12-safe; missing → empty via the GraphQL error path (`required` cleans it).
  - **Author-fixed scope** — pin it inline in the document's `defaultVariables` (the save API carries
    the field; the editor UI does not expose it yet). "Large rooms" = an inline `allocatableMatching`
    filter. **Soft default, not a hard pin** (overridable via raw dotted paths until `reject-undeclared`
    + true pins land) — "scoped, not sealed"; fine in practice, closed properly by the two fixes.
  - **Reader-supplied resources** — `?filter.allocatableIdsIn=<id>` (provisional dotted path).
  - **Reader-choose-else-nothing** — a sentinel bogus-id in `defaultVariables` fakes `requiresScope`
    today (empty when unscoped, overridden by a real reader id); a hack, replaced by the real
    short-circuit later.
  - **Migration is clean**: an inline `defaultVariables` filter → `pins: { group: "…" }` when groups
    land (same resolved scope, named+reusable+live); `?filter.allocatableIdsIn=` → `?in=`; no reader URL
    changes for the id case.
  - **Discipline until `requiresScope` exists**: *always* pin a scope (or the sentinel) — a document
    with neither a pin nor a reader param firehoses.

  **Two fixes that land regardless of the full `@param` adoption** (both independent of the redesign,
  both worth doing now): (a) the `ViewVariables` server-side anchor evaluation (a shipped defect); (b)
  **reject-undeclared** parameters (closes the override hole before Phase 8). The shipped
  `RequestVariables` (dotted paths) is the **provisional Option C**, superseded in design by this
  resolution; migrating the render path to `@param` is a follow-up (naturally lands with Phase 6, the
  first consumer that genuinely needs pinned/clamped published documents).

### OQ9 — implementation plan (the parameter contract)

Ordered by dependency and value. Steps A–B are the `@window`/`@param` mechanism (PRD 074 —
final contract: [074 § Window and inputs directives](074-graphql-declarative-views.md#window-and-inputs-directives-decided-2026-07-12),
which supersedes the arg lists sketched here); C–E are document policy (this PRD); groups (D)
are deferred. Each step is independently shippable and test-first.

- **Step A — window server-side** *(✅ landed 2026-07-12)*
  - [x] `WindowResolver` — Java anchor evaluation (three anchors × three units) with a tier-1
        parity suite; `ViewVariables.mergeDefaults` resolves the view's `@window`/mode default
        instead of hardcoding Monday; `extensions.view.window` emitted; the SPA seeds from it and
        the client-side anchor resolver (`view-inputs.ts`) is **deleted**.
- **Step B — `@param` gate** *(✅ landed 2026-07-12 — final shape is `@param(name, into, required)`;
  `type` is derived from `into`, no `role`/`clamp`, `@window` is its own directive)*
  - [x] `@window`/`WindowAnchor`/`@param` in the SDL; `@view` anchor args deleted.
  - [x] Render path maps **public name → `into` path** (`ViewParamDirectives` +
        `DocumentRenderService.gateParams`); **undeclared → 400**; `from`/`to` accepted iff
        `@window`; missing `required` → the same 404 (§12). Raw dotted paths are gone from the
        URL surface. Lists via repeated keys, never comma-split. Tier-3: `DocumentParamGateTest`.
  - [ ] Save-time validation (`into` resolves against the schema, unique public names).
  - SPA: consumes nothing — binds by type + `view.window`; controls (`ParamControl`) deferred.
- **Step C — document policy** *(remaining; the first consumer is Phase 6's calendar-export replacement)*
  - [ ] Document metadata + editor UI: `pins`, `requiresScope` (the ≥1-of-a-set scope gate —
        per-param `required` landed in Step B).
  - [ ] `DocumentRenderService` resolution order: `@window` default → view defaults →
        document defaults → URL params → `pins`. `requiresScope` unmet → **short-circuit
        to empty render** (no query).
  - [ ] Clamping (subset for id sets, not-before/after for windows) — deferred with the trust tiers.
  - [x] Tier-3 leak tests: unreadable/nonexistent id → empty render, never an error; missing
        required byte-identical to 404 (`DocumentParamGateTest`, `DocumentControllerLeakTest`).
- **Step D — groups** *(deferred; forward-declared in schema+docs now)*
  - [ ] `@param(type: GROUP)`, the group entity = a stored `AllocatableFilter` addressed by a namespaced
        name; resolver expands name → `allocatableMatching` (live). Migrate inline `defaultVariables`
        filters → `pins: { group }`.
- **Step E — `templateParams`** *(Phase 9, with plugins/actions/components)*
  - [ ] Near-term: expose resolved params in the model as `{{params.x}}`.
  - [ ] `templateParams` declaration on the document → Mustache model (never GraphQL); routed separately
        from `@param`; undeclared rejected. Feeds plugin/action/component inputs referenced in the template.

## Decisions locked

**D1 — Server-side rendering, reusing the stored-view storage mechanism.** Consistent with PRD
074's proven pattern (validated, group-scoped, filled on request); central management + §12 gating
for free. Client-side template storage rejected (no central admin, duplicated logic).
*Amended 2026-07-08:* the storage mechanism is now the [PRD 098](done/098-server-artifact-store.md) server artifact store, not system
`Preferences` — 098 records why preferences is the wrong home for template/CSS bodies (whole-entity
conflicts, client-sync bloat, no per-artifact metadata) and lands before this PRD's Phase 1.

**D2 — Engine = JMustache (logic-less).** It is the only candidate that combines the **safe "no-eval"
class** (no SSTI, structurally — same principle as the GraphQL stack) with **standard status**
(Mustache has a formal cross-language spec + conformance suite + ~40 implementations) and **zero
friction** (~100 KB, no transitive deps, no Jackson). Fed `Map` data directly, so its optional
Jackson-for-`Inspectable` path is never touched. It is also the engine behind Spring Boot's official
Mustache support (`spring-boot-starter-mustache` / `MustacheViewResolver`), and the Spring Boot 4.0
BOM rapla builds against already manages it (`jmustache.version=1.16` in
`spring-boot-dependencies-4.0.0.pom`) — add the dependency **without a version tag**; Boot governs
the version and keeps it compatible.
- *Rejected — Thymeleaf / FreeMarker / Velocity:* full expression language (SpringEL/VTL) → SSTI on
  stored strings; the Scriban CVSS-9.1 sandbox-escape (2025) shows expression sandboxes keep getting
  broken.
- *Rejected — Google Soy:* AOT-compile-to-jar model mismatches runtime admin-authored templates, and
  ~tens-of-MB deps (Guava/ICU4J/protobuf/ASM); its one unique win (contextual autoescaping) targets
  `<script>`/URL/CSS contexts we don't render into.
- *Rejected — liqp (Liquid/JVM):* ~2 MB (ANTLR) **and pulls Jackson 2**, which the runtime is now
  free of (AGENTS.md: Jackson 2 forbidden outside build-time OpenAPI) — would require a mandatory
  exclusion; only justified if authors need author-side filters, which the GraphQL layer removes the
  need for (D3).
- *Rejected — StringTemplate (ST4):* same safe class (formal strict model-view separation), but its
  `<…>` delimiters collide with HTML tags and it is not a cross-language standard.
- *Step-up if needed — Handlebars.java* (Mustache superset, registered helpers) only if author-side
  formatting is later required.

**D3 — GraphQL is the data + authorization layer; Mustache is presentation only.** Formatting and
selection live in the query/resolvers (`displayName`, `@column`/`@join`, localized values), so the
template needs interpolation only — which is exactly what logic-less does, and why author-side
template filters (liqp/Handlebars) are unnecessary. Mustache inherits the view's §12 authorization
because it can only render the already-filtered result.

**D4 — PDF via browser `window.print()` + `@media print`; no server PDF, no JS PDF lib.** Best
quality (native vector, selectable), zero dependency, universal across browsers. Printing is the
**browser's own Ctrl+P**, invoked by the reader — the script-free shell shows a self-hiding hint
rather than an auto-print or button (D6a: no script in the static response), NOT the SPA.
(Revisit only if OQ2 flips.)

**D5 — Feasibility tiers: flat + grouped (1D) ship first; 2D grids are a separate gated phase.**
Mustache can paint a positioned grid but cannot compute layout; only true time-grids (week/month)
need `CalendarLayoutEngine`. Agenda/list/per-day views (the common case) need at most a group-by-key
projection and ship without the engine.

**D6 — Author template body is static HTML; `<script>`/handlers stripped; page protected by a
response CSP.** Mustache emits any text including full pages with `<script>`/CSS — fine for
*author-written static* content but reintroducing a **client-side stored-XSS** surface (orthogonal to
SSTI, which logic-less already eliminates) if a semi-trusted (group-scoped) author can embed JS that
runs in every viewer's browser. Because the document is served as a **standalone top-level page**
(D7), there is no parent SPA iframe to sandbox it in — so protection is: (a) strip `<script>` +
event-handler attributes from the **author template body** (jsoup safelist) so no author-supplied
script reaches the browser; (b) a **response `Content-Security-Policy`** (owned by
[PRD 102](102-browser-credential-hardening.md), see the ownership boundary above) that forbids
inline/external script save what its **deployment-owned allowlist** admits (102 D5 — a path
allowlist of vetted rapla/plugin component sources, **not** a nonce; the static shell carries no
script at all). CSS is permitted (cannot execute code); external `url(...)` loads are constrained by
the same CSP (`img-src`/`font-src`/`style-src`). Escaping rule for interpolated data: `{{ }}` (HTML-escaped) only;
never `{{{ }}}` for query-derived values, and never interpolate untrusted values into `<script>`/
attribute/URL contexts (HTML-escaping ≠ JS/URL-context escaping — the gap Soy's contextual
autoescaping fills and Mustache does not). Admin-only raw-HTML+JS is left open (OQ6).

**D6a — Document responses carry `Content-Security-Policy: sandbox` (decided 2026-07-08, dialog
recorded in [PRD 072](072-server-side-login-dialog.md)'s 2026-07-08 follow-up).** Second, independent layer under D6: the `sandbox`
directive gives the top-level document an **opaque origin**, so even a script that survives
stripping + CSP executes in a devalued context — its `fetch`es are cross-origin (no cookies
attached, responses unreadable under CORS), no `localStorage`/`sessionStorage`, no window handles
onto same-host documents. This defends the *user's session*, complementing D6 which defends against
*content* (defacement/phishing stay a sanitizing concern). Flags: none by default (static HTML
needs no scripts); **never `allow-same-origin` together with `allow-scripts`** — that restores the
real origin and voids the layer. Set centrally (path-matched in `RaplaCspHeaderWriter`) so no
per-controller response can forget it. *Resolved (2026-07-10): the shipped shell is **script-free** —
printing is the browser's own Ctrl+P behind a self-hiding hint, so the static document response
carries `script-src 'none'` with no `allow-scripts` and no nonce. Scripts return only in the
**interactive tier** (102 D4/D5): documents that use registered `<rapla-*>` components get the
deployment **allowlist** + `sandbox allow-scripts`; static documents keep `'none'`.* A **separate sandbox origin**
(githubusercontent pattern) was evaluated and rejected — rapla deployments have no control over
subdomains; `CSP: sandbox` is the in-origin approximation. The `access_token` cookie **is** ambient
on the origin ([PRD 102](102-browser-credential-hardening.md) D1, 2026-07-09, kept it and rejected the memory-only token), so the sandbox
opaque origin is doing the real containment work: a script on a document page cannot read cookies or
API responses even though the credential is present. Tests: tier-3 header assertion on document responses (and its absence
on SPA/API responses); Playwright: injected same-origin `fetch` with credentials from a document
page yields no authenticated response.

**D6b — CSP is a response-layer concern, engine-agnostic; the template engine contributes only
HTML-escaping + adding no inline runtime of its own.** No template engine "does CSP" — CSP is an
HTTP response header (D6/D6a) enforced by the browser. Mustache's only CSP-relevant properties are
(a) `{{ }}` auto-escapes HTML (reduces the XSS that CSP backstops — but HTML-context only, not
context-aware; the gap Soy's contextual autoescaping fills, ruled out for weight and unneeded for
HTML-text documents) and (b) it emits exactly the authored text and **injects no runtime
script/inline handlers of its own** — so a logic-less engine is inherently CSP-friendly (nothing to
allow-list). Because the script model is a **deployment allowlist, not a nonce** (102 D5), the engine
threads no CSP token into the template at all — there is no `{{meta.cspNonce}}`; the allowlist is
matched against the response's script sources server-side, independent of the template text.
Consequence: the CSP posture is unchanged by the engine-selection flag (D2); switching
Mustache↔Handlebars neither strengthens nor weakens it.

**D7 — Served as a standalone page-generator URL, session-cookie authenticated, §12-scoped.** The
document is opened directly in the browser at its own human-navigable URL (like the existing
`/rapla/calendar` pages), NOT fetched/embedded by the SPA. Consequences:
- **Page generator, §15 category.** A page controller (cf. `CalendarPageController`,
  `IndexPageController`) serves `text/html`. If the URL sits outside `/api/` (e.g.
  `/rapla/document/{name}`), it must be added to the §15 allow-list + `ApiPrefixArchitectureTest`;
  under `/api/` it needs no exception. *URL space — OQ4.*
- **Auth = browser session cookie**, resolved server-side (not a SPA bearer token). The GraphQL view
  executes as the **session user**, so §12 filtering scopes the document to that user's read scope —
  a shared/bookmarked URL never leaks beyond what its opener may read. The top-level navigation is
  authenticated by the ambient HttpOnly `access_token` cookie ([PRD 102](102-browser-credential-hardening.md) D1, 2026-07-09, kept it and
  rejected the memory-only token — so OQ7 is moot). (`CSP: sandbox` (D6a) is unaffected: cookies are
  attached on the *request*; the sandbox constrains the resulting document.)
- **Server-authored shell** wraps the (stripped) author body: doctype, `@media print`/`@page` CSS,
  and a script-free print hint (Ctrl+P — no auto-print, no button, no CSP nonce; the shell carries no
  script, per D6a). Aligns with [PRD 030](030-server-side-view-rendering.md)'s parked
  "HTML autoexport calendar pages" migration — the same standalone-server-rendered-HTML shape.

## Appendix A — Worked example: Leihschein (single reservation → loan slip)

Illustrates the flat/grouped tier (no layout engine) **and** the key modelling lesson: role-based
splitting belongs in the query, not the template.

**Modelling lesson — `isPerson` does not separate borrower from loaned.** On a loan slip both the
borrower AND a loanable resource can be persons (`isPerson=true`). The separating axis is
**role/source in the data model**, not person-ness:

| Group | Source in the model | GraphQL access |
|---|---|---|
| Borrower / responsible | a **classification attribute** of the loan event type (person reference — emitted by `ClassificationSdlGenerator` as a navigable `Allocatable` field) | `classification { … entleiher { name } }` |
| Loaned (persons *and* equipment) | the **allocations** on the appointments | `allocatables(filter:)` |

The query splits by role/source; `isPerson` is used only *within* the loaned set to separate loaned
persons from equipment. A logic-less template could never derive this distinction — it just renders
the three pre-separated lists.

**Stored view `leihschein`** (`ausleiheClassification` / `entleiher` / `geraetClassification` are
deployment-specific — depend on the configured loan/equipment DynamicTypes; if the borrower is the
reservation creator, use `owner { username }` instead of the attribute):

```graphql
query leihschein($reservationId: ID!) @view(title: "Leihschein") {
  reservation(id: $reservationId) {
    name
    classification {
      ... on ausleiheClassification { entleiher { name } }   # responsible person(s)
    }
    appointments {
      start
      end
      ausgeliehenePersonen: allocatables(filter: { isPersonEq: true })  { name }
      ausgelieheneGeraete:  allocatables(filter: { isPersonEq: false }) {
        name
        classification { ... on geraetClassification { inventarnummer } }
      }
    }
  }
}
```

**Result (Mustache model):**

```json
{ "reservation": {
    "name": "Kameraausleihe Projektwoche",
    "classification": { "entleiher": { "name": "Mustermann, Max" } },
    "appointments": [{
      "start": "2026-07-10T09:00", "end": "2026-07-17T17:00",
      "ausgeliehenePersonen": [ { "name": "Beispiel, Anna (Tutorin)" } ],
      "ausgelieheneGeraete": [
        { "name": "Canon EOS R5",     "classification": { "inventarnummer": "INV-00421" } },
        { "name": "Stativ Manfrotto", "classification": { "inventarnummer": "INV-00887" } }
      ]
    }]
} }
```

**Stored Mustache template** (`{{#list.0}}…{{/list.0}}` = logic-less "if not empty" via existence of
the first element):

```html
{{#reservation}}
<div class="leihschein">
  <h1>Leihschein</h1>
  <p class="titel">{{name}}</p>
  {{#classification.entleiher}}<p>Entleiher: {{name}}</p>{{/classification.entleiher}}
  {{#appointments}}
  <p>Leihzeitraum: {{start}} – {{end}}</p>
  {{#ausgeliehenePersonen.0}}<h2>Ausgeliehene Personen</h2>{{/ausgeliehenePersonen.0}}
  {{#ausgeliehenePersonen}}<p>{{name}}</p>{{/ausgeliehenePersonen}}
  {{#ausgelieheneGeraete.0}}<h2>Ausgeliehene Geräte</h2>{{/ausgelieheneGeraete.0}}
  <table>
    {{#ausgelieheneGeraete}}
    <tr><td>{{name}}</td><td>{{classification.inventarnummer}}</td></tr>
    {{/ausgelieheneGeraete}}
  </table>
  {{/appointments}}
  <p class="sig">Unterschrift Entleiher: ____________________</p>
</div>
{{/reservation}}
```

**Invoke:** `GET /api/documents/leihschein?reservationId=res-12345` → HTML → SPA opens it →
`window.print()` → "Save as PDF".

**Formatting note (D3):** `start`/`end` arrive as ISO (`2026-07-10T09:00`). Logic-less Mustache
cannot format — the server provides formatted values (`AppointmentBlock.times`, `compute(expr:)`, or
a dedicated formatted field). Formatting lives in the query, not the template — which is why
JMustache suffices and no author-side template filters are needed.
