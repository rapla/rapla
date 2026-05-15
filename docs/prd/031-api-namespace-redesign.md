# 031 — API namespace redesign

**Status:** in-progress (Phases 1+2+3+4 landed 2026-05-12; rememberMe re-enable still owed by PRD 029)

## Goal

Adopt a clean top-level URL layout that separates concerns:

- **`/app/`** — Angular SPA (peer mount, framework-agnostic name).
- **`/api/`** — REST surface consumed by the SPA (and migrated Swing
  client). Prefix is industry-standard, supports future versioning.
- **`/rapla/`** — narrowed to the six legacy load-bearing URLs only
  (`calendar`, `internal_calendar`, `ical`, `internal_ical`, plus
  `.csv` variants). External consumers (iCal subscribers, embedded
  calendar pages) depend on these — they must not move.
- Root-level standards stay where conventions / RFCs require:
  `/oauth2/**`, `/.well-known/**`, `/swagger-ui/**`, `/login`,
  `/error`, `/raplaclient.jnlp`, `/webclient/**`.
- **`/`** and **`/index`** — the existing `IndexPageController` /
  `RaplaIndexPageGenerator` chooser landing page. Becomes the
  natural entry point after the context-path drops.

## Why

Three reasons it's worth doing now, before the Angular SPA grows
load-bearing dependencies on the current URL shape:

1. **`/rapla/` is historical baggage.** It was the servlet path in the
   WAR-deployment days, not the application root. The Spring Boot
   migration preserved it as `server.servlet.context-path: /rapla`,
   which over-prefixes every URL.
2. **`/api/` is the modern convention** for REST consumed by SPAs.
   Frontend devs / future contributors expect it. Also supports
   `/api/v2/...` versioning when the time comes.
3. **The SPA mount belongs at root**, not nested inside what was a
   REST servlet. Peer mount keeps URLs short and the mental model
   honest.

## Scope

### Target URL layout

| Today | Proposed | Notes |
|---|---|---|
| (context-path `/rapla/`) | **(dropped)** | Stops over-prefixing |
| `/rapla/` (no path) | **`/`** | Chooser landing page |
| `/rapla/index` | **`/index`** | Same chooser, alias |
| `/rapla/spa/**` | **`/app/**`** | SPA peer mount |
| `/rapla/auth/**` | **`/api/auth/**`** | Login/logout/refresh/token |
| `/rapla/storage/**` | **`/api/storage/**`** | Reservation+resource storage |
| `/rapla/edit/**` | **`/api/edit/**`** | Edit-time validation services |
| `/rapla/locale/{id}` | **`/api/locale/{id}`** | i18n bundles |
| `/rapla/settings/**` | **`/api/settings/**`** | Settings REST |
| `/rapla/plugins/**` | **`/api/plugins/**`** | Plugin enable/disable |
| `/rapla/eventtimecalculator/**` | **`/api/eventtimecalculator/**`** | Plugin config |
| `/rapla/exchange/**` | **`/api/exchange/**`** | Exchange connector config |
| `/rapla/ical/config/**` | **`/api/ical/config/**`** | iCal export *config* (not the feed) |
| `/rapla/ical/timezones/**` | **`/api/ical/timezones/**`** | Timezone catalog |
| `/rapla/v3/api-docs` | **`/api/v3/api-docs`** | OpenAPI spec |
| `/rapla/swagger-ui/**` | **`/swagger-ui/**`** | SpringDoc UI |
| `/rapla/oauth2/**` | **`/oauth2/**`** | RFC 6749 |
| `/rapla/.well-known/**` | **`/.well-known/**`** | RFC 8615 OIDC discovery |
| `/rapla/login` | **`/login`** | Spring form login HTML |
| `/rapla/error` | **`/error`** | Spring error |
| `/rapla/logger/**` | **`/api/logger/**`** ❓ | Internal — TBD |
| `/rapla/server` | **`/api/server`** ❓ | Status — TBD |
| `/rapla/dhbw/**` | **`/dhbw/**`** | Deployment-specific |
| `/rapla/webclient/**` | **`/webclient/**`** | JNLP jar bundle |
| `/rapla/raplaclient.jnlp` | **`/raplaclient.jnlp`** | JNLP launcher entry |
| `/rapla/raplaclient` | **`/raplaclient`** | JNLP launcher alias |
| `/rapla/images/**` | **`/images/**`** | Static (favicon, button.gif) |
| `/rapla/*.css` | **`/*.css`** | Static CSS |
| **`/rapla/calendar`** | **`/rapla/calendar`** | 🔒 KEEP — external iCal subscribers |
| **`/rapla/calendar.csv`** | **`/rapla/calendar.csv`** | 🔒 KEEP |
| **`/rapla/internal_calendar`** | **`/rapla/internal_calendar`** | 🔒 KEEP |
| **`/rapla/internal_calendar.csv`** | **`/rapla/internal_calendar.csv`** | 🔒 KEEP |
| **`/rapla/ical`** | **`/rapla/ical`** | 🔒 KEEP — Outlook/iOS subscriptions |
| **`/rapla/internal_ical`** | **`/rapla/internal_ical`** | 🔒 KEEP |

### Already removed (prerequisite cleanup, 2026-05-12)

- `static/Rapla/` (~412 KB GWT artifacts) — referenced only by `rapla.html`
- `static/jsclient/` — referenced only by `apiTest.html`
- `static/rapla.html` — no live references
- `static/apiTest.html` — only as fallback target in `RaplaAuthRestPage:94`
  (fallback updated to `swagger-ui/index.html`)
- `static/images/{empty.gif, rapla_128x128.ico, rapla_16x16.ico, rapla_32x32.ico}` —
  no references
- `SecurityConfig` permit list: `/Rapla/**` + `/jsclient/**` removed

### Still expected to remove during this PRD

- `static/redirect.html` — meta-refresh to `rapla/index`; obsolete after
  context-path drop (root `/` already is the index).

### Index page change

`IndexPageController` already handles `@GetMapping({"/", "/index"})`.
Two changes:

1. **Add SPA entry to `HtmlMainMenu`** — new class
   `RaplaSpaEntry extends DefaultHTMLMenuEntry implements HtmlMainMenu`
   with title `"Open web app"` and URL `/app/`. Register as a `@Bean`
   in `ServerServiceConfig` next to `raplaJnlpEntry` / `raplaStatusEntry` /
   `exportMenuEntry`. ~15 LOC total.
2. **No other change needed** — the page renders all `HtmlMainMenu`
   entries automatically.

## Dev workflow implication

Once Phases 1+2 land, the Angular SPA's dev workflow becomes
`ng serve` with a proxy — URL-symmetric with prod:

| | Dev (`ng serve` :4200) | Prod (Spring :8051) |
|---|---|---|
| SPA | `:4200/app/` | `:8051/app/` |
| REST | `:4200/api/auth/login` | `:8051/api/auth/login` |
| OAuth2 | `:4200/oauth2/...` (proxied) | `:8051/oauth2/...` |
| Legacy iCal | `:4200/rapla/ical` (proxied) | `:8051/rapla/ical` |

`ng serve` flags: `--serve-path /app/ --base-href /app/`.
`proxy.conf.json` forwards everything NOT under `/app/`:
`/api/**`, `/oauth2/**`, `/.well-known/**`, `/swagger-ui/**`,
`/rapla/{calendar,ical}/**`, `/login`, `/error` → `:8051`.

Until Phase 1 lands, the SPA stays on the `ng build --watch` +
Spring static handler pattern (PRD 026 §Phase 0 prototype). Wiring
`ng serve` before the URL space is normalized would mean wiring it
twice — not worth it.

## Plan

### Status snapshot (2026-05-12)

| Phase | State | Notes |
|---|---|---|
| 1 — drop context-path | **DONE** | `application.yml`, `SecurityConfig`, `CalendarPageController` + `Export2iCalController` anchored at `@RequestMapping("/rapla")`, JNLP codebase via `getContextPath()`, Swing client base URL, OAuth2 redirect URIs, ~15 MockMvc tests updated |
| 2 — `/api/` prefix | **DONE** | Every `@RestController` carries the literal `/api/...` in its class-level `@RequestMapping`; matching `@HttpExchange` proxy interfaces in `rapla-core` carry the same path in lockstep. The 6 exclusions (IndexPage, LoginPage, CalendarPage, Export2iCal, RaplaJNLP, StatusPage) keep their root/`/rapla/` mappings. SpringDoc reads the literal mapping → `/api/v3/api-docs`. SPA `BASE_PATH=''` (empty) since spec emits absolute `/api/...` paths. Swing client's `serverURL` is the server root; manual URL builders (`/api/auth/refresh`, `/api/auth/logout`, `/api/auth/oauth/config`) prepend the prefix at the call site. Enforced by `ApiPrefixArchitectureTest` (see AGENTS.md §15). |

> **2026-05-14 update.** Phase 2 originally used `ApiPathPrefixConfig` (a `WebMvcConfigurer.addPathPrefix("/api", predicate)`) to inject the prefix at dispatcher time. That configurer is now deleted: the prefix is literal on each controller. Reason: discoverability (`grep '/api/storage'` finds the source), simpler reasoning (the wire URL is the source URL), and removes the SpringDoc-prefix coupling that needed dual `/api/v3/api-docs` + `/v3/api-docs` security entries. The architecture test in §15 is the mechanical safety net that the configurer's all-or-nothing default previously provided implicitly.
| 3 — SPA entry on index | **DONE** | `RaplaSpaEntry` registered as `@Bean(name = "0_spa")` in `ServerServiceConfig`. Index page shows "Open web app → /app/" alongside JNLP, Status, Export entries. |
| 4 — cleanup | **DONE** | `redirect.html` deleted. CSS audit done (all 6 files referenced by live HTML generators — kept). PRD URL example pass complete (PRD 026 updated). `frontend-maven-plugin` + `maven-resources-plugin` wired in `rapla-app/pom.xml` under `-Pspa` profile; `mvn -Pspa package` builds the SPA and bundles it into `target/classes/static/app/` for inclusion in the fat JAR. |

### Outstanding follow-ups

- **`.rememberMe(...)` block in `SecurityConfig`** temporarily disabled (commented) to unblock Phase 1+2 testing — `RememberMeConfigurer` requires a `UserDetailsService` bean that PRD 029 was meant to add. Re-enable once that wiring lands.
- **MockMvc test fallout** beyond the bulk-rename: any tests that hit OAuth2 endpoints or have assertions on full URLs may still need touch-ups. Full `mvn test` pass owed.
- **JNLP `getContextPath()`** uses `request.getContextPath()` which is now empty; the generator's existing logic handles the empty case automatically (verified) but the unit test `RaplaJNLPPageGeneratorTest` still mocks `/rapla` context-path for regression coverage — keep.

### Phase 1 — Drop context-path, no API rename yet

Foundation that everything else builds on. Smallest possible diff to
prove the core mechanics work.

- Remove `server.servlet.context-path: /rapla` from `application.yml`.
- Every URL that *was* under `/rapla/X` is now `/X`.
- `SecurityConfig` permit list: drop the `/rapla/` prefix from every
  entry; the new `/rapla/calendar`, `/rapla/ical`, etc. entries are
  explicit.
- Six legacy controllers — `CalendarPageController`, the iCal
  feed controllers — change their `@GetMapping` paths to include
  `/rapla/calendar`, `/rapla/ical`, etc. explicitly (anchored under
  `/rapla/` namespace, NOT under context-path).
- `application.yml` OAuth2 redirect URIs: drop `/rapla/` literals.
  Affected lines:
  - `swagger-ui` client `redirect-uris: http://localhost:8051/swagger-ui/oauth2-redirect.html`
- JNLP descriptor generator (`RaplaJNLPPageGenerator`): `<codebase>` URL
  becomes `http://host:8051/` (no `/rapla/`).
- Swing client `RemoteConnectionInfo.serverURL` default + any
  hardcoded literals.
- MockMvc integration tests: global `/rapla/` → `` rename.
- Test deployment + Swing client launch end-to-end.

### Phase 2 — Add `/api/` prefix to MVC controllers

Pure config + a few annotations. Doesn't affect the calendar/iCal
controllers (they're anchored to `/rapla/`).

- Add `WebMvcConfigurer.configurePathMatch().addPathPrefix("/api", predicate)`
  where `predicate` excludes:
  - `IndexPageController` (renders `/` and `/index`)
  - `CalendarPageController`, iCal feed controllers (anchored to `/rapla/`)
  - `LoginPageController`, error page handlers (root by convention)
  - `RaplaJNLPController` (root: `/raplaclient.jnlp`, `/webclient/**`)
  - Static resource handlers (`SpaResourceConfig` etc.)
- SPA: `BASE_PATH` from `/` (post-Phase 1) to `/api`.
- Regenerate TS client via `npm run gen:api`.
- Swing client REST proxies: update to call `/api/auth`, `/api/storage`, etc.

### Phase 3 — Add SPA entry to index page

- Add `RaplaSpaEntry` + `@Bean` registration. ~15 LOC.
- Test: visit `/`, confirm "Open web app" link appears, click → SPA loads.

### Phase 4 — Cleanup

- Delete `static/redirect.html`.
- Update PRDs that quote example URLs (PRD 026 mainly).
- Audit remaining static CSS for orphans (`bootstrap.min.css`,
  `calendar.css`, `export.css`, `login.css`, `rapla.css` — verify
  which are still used).

## Tests

| Phase | Test type | What to verify |
|---|---|---|
| 1 | Tier-3 MockMvc | `GET /` returns chooser HTML; `GET /auth/login` returns 405 (POST-only) confirming the endpoint moved; iCal feeds at `/rapla/ical` still return 200 |
| 1 | Manual | Spring Boot dev server boots; Swing client launches via JNLP; OAuth2 login flow round-trips |
| 1 | Manual | An existing iCal subscriber URL (e.g. `/rapla/ical?key=...`) still returns valid iCal |
| 2 | Tier-3 MockMvc | `GET /api/auth/login` returns 405; `GET /api/storage/resources` returns 200; `GET /api/v3/api-docs` returns spec |
| 2 | Manual | Swagger UI explorer at `/swagger-ui/index.html` shows `/api/`-prefixed paths; SPA still works against `/api/` |
| 3 | Tier-3 MockMvc | `GET /` contains link to `/app/` |
| 3 | Manual | Click chooser entry → `/app/` loads SPA |

## Risks

| Risk | Mitigation |
|---|---|
| Swing client breaks after path moves | Phase 1 and Phase 2 each include a Swing smoke test; Swing config updates ship in the same PR |
| OAuth2 redirect-URI mismatch | Grep `application.yml` for `/rapla/` literals before commit; manual OAuth2 login round-trip after |
| Live iCal subscribers see 404 | Calendar/iCal controllers explicitly re-mount under `/rapla/` in Phase 1 — never accidental |
| JNLP launcher breaks | Manual JNLP launch smoke test post-Phase 1 |
| MockMvc test fallout | Phase 1 includes global rename; expect ~50–100 test files touched |

## Open questions

1. **Phasing.** Sequential (Phase 1 → 2 → 3 → 4) or big-bang (one PR)?
   Recommendation: phased — smaller blast radius per merge, easier to
   bisect failures.
2. **`/v3/api-docs` placement.** `/api/v3/api-docs` (under the SPA's
   REST namespace) or `/v3/api-docs` (root, SpringDoc's default)?
   Lean: `/api/v3/api-docs` for consistency.
3. **`/logger` and `/server` placement.** Move under `/api/` (consumed
   by code, including the Swing client's remote logger) or stay at
   root (root is fine; consistency argues `/api/`)? Lean: `/api/` for
   consistency.
4. **Backwards-compat redirects.** Do any moving paths need a 308
   redirect from the old `/rapla/...` URL for a transition period
   (e.g. `/rapla/auth/login` → 308 → `/api/auth/login` for two
   releases)? Only matters if there are external consumers of the
   internal endpoints. Default: hard cutover, since the load-bearing
   external URLs (calendar/iCal) are explicitly preserved.

## Cross-references

- PRD 001 — Spring Boot migration (introduced the `/rapla/` context-path).
- PRD 026 — Angular frontend (depends on this for clean URL layout).
- PRD 027 — Mock framework policy (MockMvc test updates will be the
  bulk of the test fallout).
