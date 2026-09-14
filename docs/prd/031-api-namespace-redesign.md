# 031 — API namespace redesign

**Status:** in-progress (Phases 1+2+3+4 landed 2026-05-12; rememberMe re-enable still owed by [PRD 029](029-swing-oauth-login.md))

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

Before the Angular SPA grows load-bearing deps on the current URL shape:

1. **`/rapla/` is historical baggage** — servlet path from WAR days, preserved as `server.servlet.context-path: /rapla`, over-prefixes every URL.
2. **`/api/` is the modern convention** for SPA REST; supports future `/api/v2/...` versioning.
3. **SPA belongs at root**, not nested inside a REST servlet. Peer mount keeps URLs short.

## Scope

### Target URL layout

Context-path `/rapla/` dropped. Three groups:

- **Drop prefix entirely** (move to root): `/`, `/index`, `/app/**` (SPA), `/swagger-ui/**`, `/oauth2/**`, `/.well-known/**`, `/login`, `/error`, `/webclient/**`, `/raplaclient.jnlp`, `/raplaclient`, `/images/**`, `/*.css`, `/dhbw/**`.
- **Move to `/api/` prefix**: `/api/auth/**` (login/logout/refresh/token), `/api/storage/**`, `/api/edit/**`, `/api/locale/{id}`, `/api/settings/**`, `/api/plugins/**`, `/api/eventtimecalculator/**`, `/api/exchange/**`, `/api/ical/config/**` (export *config*, not feed), `/api/ical/timezones/**`, `/api/v3/api-docs`, `/api/logger/**`, `/api/server`.
- **🔒 KEEP under `/rapla/`** (external iCal/Outlook/iOS subscribers depend on these): `/rapla/calendar(.csv)?`, `/rapla/internal_calendar(.csv)?`, `/rapla/ical`, `/rapla/internal_ical`.

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

Post-Phase 1+2, Angular SPA dev becomes `ng serve --serve-path /app/ --base-href /app/` URL-symmetric with prod. `proxy.conf.json` forwards everything not under `/app/` (`/api/**`, `/oauth2/**`, `/.well-known/**`, `/swagger-ui/**`, `/rapla/{calendar,ical}/**`, `/login`, `/error`) to `:8051`. Until then SPA stays on `ng build --watch` + Spring static handler ([PRD 026](026-angular-frontend.md) §Phase 0).

## Plan

### Status snapshot (2026-05-12)

| Phase | State | Notes |
|---|---|---|
| 1 — drop context-path | **DONE** | `application.yml`, `SecurityConfig`, `CalendarPageController` + `Export2iCalController` anchored at `@RequestMapping("/rapla")`, JNLP codebase via `getContextPath()`, Swing client base URL, OAuth2 redirect URIs, ~15 MockMvc tests updated |
| 2 — `/api/` prefix | **DONE** | Every `@RestController` carries the literal `/api/...` in its class-level `@RequestMapping`; matching `@HttpExchange` proxy interfaces in `rapla-core` carry the same path in lockstep. The 6 exclusions (IndexPage, LoginPage, CalendarPage, Export2iCal, RaplaJNLP, StatusPage) keep their root/`/rapla/` mappings. SpringDoc reads the literal mapping → `/api/v3/api-docs`. SPA `BASE_PATH=''` (empty) since spec emits absolute `/api/...` paths. Swing client's `serverURL` is the server root; manual URL builders (`/api/auth/refresh`, `/api/auth/logout`, `/api/auth/oauth/config`) prepend the prefix at the call site. Enforced by `ApiPrefixArchitectureTest` (see AGENTS.md §15). |

> **2026-05-14 update.** Phase 2 originally used `ApiPathPrefixConfig` (a `WebMvcConfigurer.addPathPrefix("/api", predicate)`) to inject the prefix at dispatcher time. That configurer is now deleted: the prefix is literal on each controller. Reason: discoverability (`grep '/api/storage'` finds the source), simpler reasoning (the wire URL is the source URL), and removes the SpringDoc-prefix coupling that needed dual `/api/v3/api-docs` + `/v3/api-docs` security entries. The architecture test in §15 is the mechanical safety net that the configurer's all-or-nothing default previously provided implicitly.
| 3 — SPA entry on index | **DONE** | `RaplaSpaEntry` registered as `@Bean(name = "0_spa")` in `ServerServiceConfig`. Index page shows "Open web app → /app/" alongside JNLP, Status, Export entries. |
| 4 — cleanup | **DONE** | `redirect.html` deleted. CSS audit done (all 6 files referenced by live HTML generators — kept). PRD URL example pass complete ([PRD 026](026-angular-frontend.md) updated). `frontend-maven-plugin` + `maven-resources-plugin` wired in `rapla-app/pom.xml` under `-Pspa` profile; `mvn -Pspa package` builds the SPA and bundles it into `target/classes/static/app/` for inclusion in the fat JAR. |
| 5 — OpenAPI spec grouping | **DONE 2026-05-15** | The single `/api/v3/api-docs` spec is split into four `GroupedOpenApi` beans in `SpringDocGroupsConfig`: `auth`, `client`, `rest`, `exports`. Each group's spec is served at `/api/v3/api-docs/<group>` and shows up as a dropdown in Swagger UI. The Angular codegen (`rapla-angular/package.json` `gen:api`) targets `/api/v3/api-docs/client` so the SPA's generated client only ships services it actually uses. The default `/api/v3/api-docs` URL continues to serve a merged union spec (SpringDoc 2.x preserves it) — useful for external auditing tools. The `ApiPrefixArchitectureTest` is extended to assert every non-allow-listed `@RestController` belongs to exactly one group, with `auth ⊆ client` as the only permitted overlap (drift prevention). |

### Phase 5 — group layout

The four groups + their inclusion rules:

| Group | URL | Includes | Why this audience |
|---|---|---|---|
| `auth` | `/api/v3/api-docs/auth` | `/api/auth/**` (AuthController, OAuthConfigController, OAuthExchangeController) | OAuth + JWT mechanics, stable contract for external integrators wiring SSO |
| `client` | `/api/v3/api-docs/client` | `/api/auth/**` + everything SPA-internal / admin-UI (`/api/storage/**`, `/api/edit/**`, `/api/calendar/view`, `/api/table/**`, `/api/dynamictypes`, `/api/locale/**`, `/api/logger/**`, `/api/plugins/**`, `/api/settings/**`, `/api/admin/panels/**`, `/api/mail/**`, `/api/ical/config/**`, `/api/ical/timezones/**`, `/api/exchange/config/**`, `/api/jndi/**`, `/api/eventtimecalculator/**`, `/api/archiver/**`, `/api/urlencryption`) | The rapla SPA / Swing client — these endpoints change in lockstep with the UI and are not a stable third-party contract |
| `rest` | `/api/v3/api-docs/rest` | `/api/events/**`, `/api/resources/**` ([PRD 009](009-server-bulk-storage-rest-api.md) bulk REST) | External scripts and integrators — fine-grained CRUD with full REST verbs |
| `exports` | `/api/v3/api-docs/exports` | `/api/export/**`, `/api/ical/import**`, `/api/externaleventimport/**`, `/rapla/calendar(.csv)?`, `/rapla/internal_calendar(.csv)?`, `/rapla/ical`, `/rapla/internal_ical` | Data in/out — file imports, table exports, calendar feeds (including the 🔒 legacy `/rapla/*` URLs external iCal subscribers depend on) |

The `auth` group is intentionally **also a subset of `client`**: SpringDoc allows overlap between groups, and the SPA's codegen needs login endpoints in the same spec so generated `AuthControllerService` is part of the SPA's bundle.

**Rule for new controllers** (also enforced by `ApiPrefixArchitectureTest`): when you add a new `@RestController`, decide which group(s) it belongs to and add its path to the group's `pathsToMatch` in `SpringDocGroupsConfig`. A controller missing from every group fails the architecture test; a controller in two groups (other than the deliberate `auth ⊆ client` overlap) also fails.

### Outstanding follow-ups

- **`.rememberMe(...)` in `SecurityConfig`** disabled to unblock Phase 1+2 (`RememberMeConfigurer` needs `UserDetailsService` from [PRD 029](029-swing-oauth-login.md)). Re-enable when wiring lands.
- **MockMvc test fallout** beyond bulk-rename: OAuth2 endpoints / full-URL assertions may need touch-ups. Full `mvn test` pass owed.
- **JNLP `getContextPath()`** now empty; generator handles empty case; `RaplaJNLPPageGeneratorTest` keeps mocked `/rapla` for regression coverage.

### Phase 1 — Drop context-path

- Remove `server.servlet.context-path: /rapla`. URLs `/rapla/X` → `/X`.
- `SecurityConfig` permit list: drop `/rapla/` prefix; explicit entries for legacy `/rapla/calendar`, `/rapla/ical`.
- Six legacy controllers (Calendar, iCal feeds) — `@GetMapping` includes `/rapla/calendar` etc. explicitly (anchored, not context-pathed).
- `application.yml` OAuth2 redirect URIs: drop `/rapla/` literals (e.g. `swagger-ui` redirect → `http://localhost:8051/swagger-ui/oauth2-redirect.html`).
- `RaplaJNLPPageGenerator` `<codebase>` → `http://host:8051/`.
- Swing `RemoteConnectionInfo.serverURL` default updated.
- MockMvc tests: global `/rapla/` → `` rename.

### Phase 2 — Add `/api/` prefix

- `WebMvcConfigurer.configurePathMatch().addPathPrefix("/api", predicate)`; `predicate` excludes IndexPage, CalendarPage, iCal feeds (anchored `/rapla/`), LoginPage / error (root by convention), RaplaJNLP (root `/raplaclient.jnlp`, `/webclient/**`), static handlers.
- SPA `BASE_PATH` → `/api`; regenerate TS client.
- Swing REST proxies → `/api/auth`, `/api/storage`, etc.

### Phase 3 — SPA entry on index

`RaplaSpaEntry` + `@Bean` (~15 LOC). Verify chooser link → SPA loads.

### Phase 4 — Cleanup

Delete `static/redirect.html`; update example URLs in cross-referenced PRDs; audit static CSS for orphans.

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

1. **Phasing** — phased (1→2→3→4), not big-bang. Smaller blast radius per merge.
2. **`/v3/api-docs` placement** — `/api/v3/api-docs` for consistency.
3. **`/logger` and `/server` placement** — `/api/` for consistency.
4. **Backwards-compat redirects** — hard cutover; external load-bearing URLs (calendar/iCal) explicitly preserved.

## Cross-references

- PRD 001 — Spring Boot migration (introduced the `/rapla/` context-path).
- [PRD 026](026-angular-frontend.md) — Angular frontend (depends on this for clean URL layout).
- [PRD 027](027-mock-framework-policy.md) — Mock framework policy (MockMvc test updates will be the
  bulk of the test fallout).
