# PRD 097 — Event HTML templates (stored Mustache over GraphQL views)

**Status:** draft — 2026-07-08
**Related:** PRD 074 (declarative GraphQL views — the stored-view + `@view` mechanism this reuses),
PRD 077 (calendar-model & saved views over GraphQL — render-modes, saved-view persistence),
PRD 078 (SPA view renderer), PRD 030 (server-side view rendering — `CalendarLayoutEngine`, parks
the "HTML autoexport → engine" migration this PRD's grid phase would consume), PRD 095 (SPA
month-grid — proves GraphQL feeds a calendar grid, but layout in TS not a template),
PRD 011 (Jackson 3 — the runtime is Jackson-2-free, which constrains engine choice),
PRD 098 (server artifact store, DONE 2026-07-08 — templates are stored as kind=TEMPLATE artifacts
there, NOT in a new preferences key; the generic artifact CRUD/upload endpoint and the IMAGE
mimeType guard were deliberately left to this PRD as the store's first consumer)

## Abstract

Let admins/groups store **HTML templates** that are filled with event data on request — the
document analogue of the stored GraphQL views (PRD 074). A stored **GraphQL view supplies the data
(and its §12 authorization); a stored logic-less template arranges it into HTML**; the browser turns
HTML into PDF via native print. The measurable end state: an admin saves a template referencing a
view, and `GET`ting the rendered document returns permission-safe HTML that `window.print()` saves as
a vector PDF — with zero new expression-evaluation attack surface.

## Motivation

Today the server renders HTML only by hand (Java text blocks / `PrintWriter` / `StringBuilder` in
`LoginPageController`, `AbstractHTMLCalendarPage`, etc.) and event notifications are plain text.
There is no way for an admin to define a print/letter/list layout for events without a code change.
The stored-view mechanism already proves the pattern: user-authored, server-stored, validated,
group-scoped, filled on request. This PRD applies the same pattern to *presentation*.

## Implementation

### The pairing model — a "document" = view + template

Both halves are stored the same way (the PRD 098 server artifact store — admin/group-authored,
validated at save; originally sketched on system `Preferences` like `StoredViewData`, superseded
2026-07-08 by PRD 098). A document pairs a **data source** (a stored
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
  (D6a); and the SPA access token is memory-held, not a cookie (PRD 072 Phase 8) — so a script on a
  document page has nothing to ride and nothing to read.

### View complexity tiers — where the engine is / isn't needed

The flat/grouped vs 2D-positioned distinction decides feasibility. **Mustache can paint but not
compute** — it has no arithmetic/overlap/positioning:

| Tier | Views | Needs | GraphQL+Mustache directly? |
|---|---|---|---|
| **Flat** | events (reservation list), appointments (block list) | nothing | ✅ trivial |
| **Grouped (1D)** | per-day (`appointments_per_day`), per-resource, week-as-list | a **group-by-key projection** (cheap resolver field or thin Java pass) — *not* the layout engine | ✅ nested sections |
| **2D grid** | week, month (time×day, overlap columns, spanning bars) | **`CalendarLayoutEngine`** (PRD 030 — deleted `f4e9c048`, resurrectable from git; pure-Java strategies) to emit positioned `RenderedBlock`s, *then* Mustache paints | ⚠️ deferred phase |

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
- `DocumentTemplate` storage as kind=TEMPLATE artifacts in the PRD 098 store + CRUD +
  `isPublic`/`groups` visibility (via `ArtifactCatalogService`), reusing the `ViewCatalogService` shape.
- JMustache engine wiring (server-side, fed `Map` data — no Jackson path, see D2).
- Render endpoint (REST `GET /api/documents/{name}` or a GraphQL field — see OQ4) with §12 leak test.
- Flat + grouped (1D) views: events, appointments, per-day.
- A `@media print` stylesheet + a "Print / PDF" action in the SPA that opens the rendered HTML and
  calls `window.print()`.
- A **template-authoring UI** in the SPA (the GraphiQL analogue for the presentation layer) with an
  engine-truthful live preview — see Phase 6.

### Out of scope
- 2D time-grid HTML (week/month) — deferred to a later phase gated on `CalendarLayoutEngine`
  resurrection **and** the §15 URL-sensitivity of the legacy `/rapla/calendar` pages (external iCal
  subscribers depend on those literal URLs/output — a migration must not break them). Possibly its
  own PRD.
- Server-side PDF generation (openhtmltopdf/PDFBox) — explicitly rejected.
- Any expression-language / sandboxed-eval engine (Thymeleaf, FreeMarker, liqp-with-custom-filters).

## Plan

### Phase 1 — Storage + engine
- [ ] `DocumentTemplate` = kind=TEMPLATE artifact in the PRD 098 store; `DocumentCatalogService`
      is a thin consumer of `ArtifactCatalogService` (no new preferences key — superseded
      2026-07-08 by PRD 098, which is DONE: the store + catalog are live, gate cleared).
- [ ] Add JMustache dependency (`com.samskivert:jmustache`, ~100 KB, zero transitive deps).
- [ ] Unit: SSTI-inert test (injection payload renders empty), auto-escape test.

### Phase 2 — Render pipeline (flat views)
- [ ] Render endpoint: resolve doc → execute referenced view (caller-scoped) → feed `data` Map to
      Mustache → return HTML.
- [ ] Tier-3 MockMvc **leak test** (non-admin, mixed visible/hidden ids → byte-identical to
      visible-only).

### Phase 3 — Grouped (1D) views
- [ ] Group-by-key projection for per-day (resolver field `appointmentBlocksByDay` **or** thin Java
      pass — OQ3), nested-section template.

### Phase 4 — SPA print
- [ ] "Print / PDF" action opens rendered HTML; `@media print` stylesheet hides chrome, sets
      `@page` margins; `window.print()`.

### Phase 5 (deferred) — 2D grid HTML
- [ ] Resurrect/port `CalendarLayoutEngine` → positioned model → Mustache paints week/month.
      Gated on §15 URL compatibility for legacy autoexport calendar pages.

### Phase 6 — Template-authoring UI (the GraphiQL analogue for the presentation layer)

GraphiQL is the tooling for the **data** layer (the view); this phase gives the **presentation**
layer its equivalent — an in-SPA editor where an author composes a document (view + template + CSS)
with immediate, engine-truthful feedback. Motivation: this is also where **AI-assisted authoring**
lands — describe → AI generates the template → live preview validates → iterate. Without an
engine-truthful preview, AI generation is a blind flight.

- [ ] **Preview endpoint** `POST /api/documents/preview` — body `{ template, viewName, variables }`.
      Renders with the **real engine** (JMustache — NOT a client-side mustache.js, whose output can
      differ), against the **real view data executed in the author's §12 read-scope** (author sees
      only their own data — no leak). Applies the same D6 pipeline: `<script>`-strip + sandboxed
      `<iframe>` + CSP, so the preview itself is never an XSS vector against the author.
- [ ] **SPA editor**: view picker + variables input + template editor + live preview pane (renders
      the preview HTML in a sandboxed iframe).
- [ ] **Available-fields pane** — the GraphiQL-schema-explorer analogue: run the referenced view
      once and surface its result shape (the JSON keys) so the author knows which `{{fields}}` exist.
- [ ] Engine-agnostic: the preview renders with whichever engine the DevOps flag selects (D2) — the
      tooling does not change between Mustache and Handlebars.
- Note: public JS playgrounds (handlebarsjs.com "Try", online mustache testers) are fine for
  *learning syntax* but render with the JS impl + no access to our data — not usable for authoring
  against our server engine/§12 data, hence the server-backed preview.

## Tests

- Tier-1: JMustache SSTI-inert + auto-escape unit tests.
- Tier-3: MockMvc leak test on the render endpoint (the §12 recipe).
- Manual: render a document, `window.print()` → PDF in Chrome + Firefox.

## Open Questions

- **OQ1 — document as a separate entity vs a render-mode on the view.** PRD 074/077 views already
  carry render-modes (table/month). Option A: a standalone `DocumentTemplate` pairing `viewRef` +
  template (this PRD's sketch). Option B: an `html`/`print` **render-mode** on the view itself, whose
  render-meta carries the Mustache template. B is tighter integration; A keeps presentation cleanly
  separate and lets one view feed many documents. *Resolution:* pending.
- **OQ2 — is a dialogless one-click PDF download a hard requirement?** If yes, `window.print()` can't
  do it (browser security boundary) and a JS lib (raster PDF) would be needed. If a dialog click is
  acceptable, no lib — `window.print()` wins on quality. *Resolution:* pending (leaning: dialog is
  acceptable → no lib).
- **OQ3 — grouping: GraphQL field vs thin Java projection** for per-day. Native `…ByDay` field is
  schema-clean; a Java `groupBy` avoids schema growth. *Resolution:* pending.
- **OQ4 — render transport: REST `GET /api/documents/{name}` vs a GraphQL field** returning HTML.
  *Resolution:* pending.
- **OQ5 — Mustache partials for shared layout** (`{{> letterhead}}`) — store partials too, for
  print letterhead/footer across documents? *Resolution:* pending (cheap to add later).
- **OQ6 — author-embedded `<script>` for less-trusted authors.** D6 strips `<script>`/handlers by
  default. Open: do we allow full raw HTML+JS for **admin-only** templates (they "own" the page
  anyway), gated on an author-trust flag? Or is stripping unconditional? *Resolution:* **resolved
  2026-07-08 — stripping is unconditional** (PRD 098 D6): never keyed on author trust, so a later
  loosening of the write rule cannot change the security posture; "an admin authored it" is not
  "an admin's session wasn't riding". No admin raw-JS exception.
- **OQ7 — document-page auth after PRD 072 Phase 8.** D7 relies on the browser sending an auth
  cookie on the top-level navigation; Phase 8 removes the `access_token` cookie. Candidates: the
  form-login `JSESSIONID` session (see PRD 072 OQ-P8.1 on its lifetime), or a dedicated
  narrowly-path-scoped page cookie for page generators. A memory token cannot authenticate a
  navigation. *Resolution:* pending — resolve together with PRD 072 Phase 8.

## Decisions locked

**D1 — Server-side rendering, reusing the stored-view storage mechanism.** Consistent with PRD
074's proven pattern (validated, group-scoped, filled on request); central management + §12 gating
for free. Client-side template storage rejected (no central admin, duplicated logic).
*Amended 2026-07-08:* the storage mechanism is now the PRD 098 server artifact store, not system
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
quality (native vector, selectable), zero dependency, universal across browsers. The print trigger
lives in the **standalone page's server-authored shell** (auto-print on load or a print button —
see D7), NOT in the SPA. (Revisit only if OQ2 flips.)

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
event-handler attributes from the **author template body** (jsoup safelist) so only the
server-authored page shell may carry script; (b) set a **response `Content-Security-Policy`** header
forbidding inline/external script except the trusted shell's print snippet (nonce-allowlisted). CSS
is permitted (cannot execute code); external `url(...)` loads are constrained by the same CSP
(`img-src`/`font-src`/`style-src`). Escaping rule for interpolated data: `{{ }}` (HTML-escaped) only;
never `{{{ }}}` for query-derived values, and never interpolate untrusted values into `<script>`/
attribute/URL contexts (HTML-escaping ≠ JS/URL-context escaping — the gap Soy's contextual
autoescaping fills and Mustache does not). Admin-only raw-HTML+JS is left open (OQ6).

**D6a — Document responses carry `Content-Security-Policy: sandbox` (decided 2026-07-08, dialog
recorded in PRD 072's 2026-07-08 follow-up).** Second, independent layer under D6: the `sandbox`
directive gives the top-level document an **opaque origin**, so even a script that survives
stripping + CSP executes in a devalued context — its `fetch`es are cross-origin (no cookies
attached, responses unreadable under CORS), no `localStorage`/`sessionStorage`, no window handles
onto same-host documents. This defends the *user's session*, complementing D6 which defends against
*content* (defacement/phishing stay a sanitizing concern). Flags: none by default (static HTML
needs no scripts); **never `allow-same-origin` together with `allow-scripts`** — that restores the
real origin and voids the layer. Set centrally (path-matched in `RaplaCspHeaderWriter`) so no
per-controller response can forget it; note the shell's nonce'd print snippet (D6) must fit the
sandbox policy (script blocked unless `allow-scripts` — resolve when the shell lands: either
`allow-scripts` + nonce-CSP, or a script-free print affordance). A **separate sandbox origin**
(githubusercontent pattern) was evaluated and rejected — rapla deployments have no control over
subdomains; `CSP: sandbox` is the in-origin approximation. Paired with PRD 072 Phase 8
(memory-held SPA token — no ambient access credential on the origin at all); the two layers cover
each other's residual gaps. Tests: tier-3 header assertion on document responses (and its absence
on SPA/API responses); Playwright: injected same-origin `fetch` with credentials from a document
page yields no authenticated response.

**D6b — CSP is a response-layer concern, engine-agnostic; the template engine contributes only
HTML-escaping + adding no inline runtime of its own.** No template engine "does CSP" — CSP is an
HTTP response header (D6/D6a) enforced by the browser. Mustache's only CSP-relevant properties are
(a) `{{ }}` auto-escapes HTML (reduces the XSS that CSP backstops — but HTML-context only, not
context-aware; the gap Soy's contextual autoescaping fills, ruled out for weight and unneeded for
HTML-text documents) and (b) it emits exactly the authored text and **injects no runtime
script/inline handlers of its own** — so a logic-less engine is inherently CSP-friendly (nothing to
allow-list). The CSP nonce (D6, author-script case) is threaded as an ordinary data-model variable
(`{{meta.cspNonce}}`), not an engine feature — identical in Handlebars. Consequence: the CSP posture
is unchanged by the engine-selection flag (D2); switching Mustache↔Handlebars neither strengthens nor
weakens it.

**D7 — Served as a standalone page-generator URL, session-cookie authenticated, §12-scoped.** The
document is opened directly in the browser at its own human-navigable URL (like the existing
`/rapla/calendar` pages), NOT fetched/embedded by the SPA. Consequences:
- **Page generator, §15 category.** A page controller (cf. `CalendarPageController`,
  `IndexPageController`) serves `text/html`. If the URL sits outside `/api/` (e.g.
  `/rapla/document/{name}`), it must be added to the §15 allow-list + `ApiPrefixArchitectureTest`;
  under `/api/` it needs no exception. *URL space — OQ4.*
- **Auth = browser session cookie**, resolved server-side (not a SPA bearer token). The GraphQL view
  executes as the **session user**, so §12 filtering scopes the document to that user's read scope —
  a shared/bookmarked URL never leaks beyond what its opener may read. ⚠️ PRD 072 Phase 8 removes
  the `access_token` cookie — which credential then authenticates this top-level navigation is
  OQ7. (`CSP: sandbox` (D6a) is unaffected: cookies are attached on the *request*; the sandbox
  constrains the resulting document.)
- **Server-authored shell** wraps the (stripped) author body: doctype, `@media print`/`@page` CSS,
  the print trigger (auto-print or button), and the CSP nonce. Aligns with PRD 030's parked
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
