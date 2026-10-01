# PRD 125 — SPA caching + client/server version skew

**Status:** implemented — 2026-10-01 (Phase 1 + 2 incl. the `beforeunload` guard reviewed and fixed, committed 3dead9ffc + 746eb3a06; live on demo.rapla.org; OQ3/OQ4 open)
**Related:** [PRD 124](124-spa-i18n.md) (SPA i18n — caching of `/api/locale` moved here), [PRD 026](026-angular-frontend.md) (SPA hosting, `SpaResourceConfig`), [PRD 118](118-rapla3-demo-usecases.md) (demo.rapla.org serves the fat JAR)

## Abstract

The SPA is never cached: every start downloads index.html, all hashed JS/CSS bundles and
the i18n catalogue again. An open tab also keeps running old JavaScript across a deploy
and talks to a newer server (GraphQL schema drift) without noticing. One build id,
generated in the Maven package build and known to both SPA and server, fixes both: it
drives cache validation and lets the SPA offer a reload when the server is newer.

## Findings (2026-09-30)

- `SpaResourceConfig` serves `/app/**` with `setCachePeriod(0)` → `Cache-Control:
  no-store` for everything, no profile or file-type distinction
  (`rapla-app/src/main/java/org/rapla/server/spring/SpaResourceConfig.java:24`).
- Probed: `/app/` and the hashed `main-WYE6IOZV.js` on 8051 → `no-store`;
  `https://demo.rapla.org/app/` (fat JAR) → `no-store`. `Last-Modified` is sent but
  useless under `no-store`.
- The production Angular build already hashes file names (`angular.json:52`
  `outputHashing: all`) — cache-safe by name.
- `/api/*` responses carry Spring Security's default `Cache-Control: no-cache, no-store,
  max-age=0, must-revalidate` (`SecurityConfig.java:207` does not override
  `cacheControl`); `/api/locale` has no ETag.
- SPA and server come from one Maven build: `rapla-app/pom.xml:258–339` runs `ng build`
  in `prepare-package` and copies `dist/` into `target/classes/static/app/`. A fresh JAR
  is therefore always consistent; skew only arises from (a) a tab open across a deploy,
  (b) a rolling deploy with mixed pods (AGENTS.md, deployment topology).
- There is no build id constant in the SPA, and none on the server beyond the version.
  Swing's `error.wrong_rapla_version` key still exists in the bundles but no Java code
  uses it (grep).
- `ng serve` (4200, dev) does not go through `SpaResourceConfig` (AGENTS.md §14).

## Implementation

### 1. Static SPA files (no build id needed)
Split the `/app/**` handler:
- hashed files (`[\w-]+-[\w-]+\.(js|css)` — Angular's chunk hashes are base64url and
  contain `-`/`_`, so any root js/css name with a hyphen counts as hashed; everything in
  `media/`) → `public, max-age=31536000, immutable`;
- `index.html` (including the SPA-route fallback) and unhashed files → `no-cache`
  (revalidate every time, `Last-Modified`/ETag → 304).

### 2. Build id
- The build id is the hashed main bundle name (`main-<hash>.js`) — D2. The SPA reads it
  from its own `<script src>`; the server reads it from the `index.html` it serves (same
  `file:` dev-dir → `classpath:/static/app/` order as `SpaResourceConfig`, re-read when
  its `lastModified` changes). No Maven or `ng build --define` plumbing.
- Dev: under `ng serve` the bundle is `main.js` (no hash) → id `dev` → check off; no
  `index.html` on the server → check off.

### 3. Version skew
- SPA sends `X-Rapla-Build: <id>` on every `/api` request (`buildCheckInterceptor`).
- Server: header absent (Swing, API keys, iCal) or `dev` → no check. On a mismatch the
  server only adds `X-Rapla-Build-Mismatch: <server id>` to the response; **no request is
  rejected**, reads and mutations run as before (D1).
- SPA: on the first mismatch header it shows a dialog "new version available — reload?"
  with **Reload** and **Cancel**; no automatic reload. Cancel keeps the user working
  (unsaved changes stay) and suppresses the dialog for that server id; a different
  server id shows it again. There is no app-wide unsaved-changes guard yet (no
  `beforeunload`/`canDeactivate`; only per-component `dirty()` in
  `event/event-sheet.component.ts:227` and `resource/resource-edit-dialog.component.ts`),
  so a `beforeunload` handler fed by those `dirty()` signals is planned — Reload then
  warns if a draft is open (also protects a manual F5). Until then the dialog text warns
  that unsaved changes are lost.

### 4. i18n catalogue (from PRD 124 OQ7)
- `/api/locale` gets a strong ETag = build id + resolved language and
  `Cache-Control: private, no-cache` (path-scoped override of the Spring Security
  default). `If-None-Match` hit → `304` before the catalogue is assembled.
- `private` is mandatory: without `?locale` the response depends on the user's preference.
- Not chosen: versioned `?v=<build>` URL with `immutable` — the SPA would need its
  language before the request (not in `/api/auth/me` today) and saves only one
  revalidation request.

## Goal

- `curl -I /app/main-*.js` → `max-age=31536000, immutable`; `curl -I /app/` → `no-cache`.
- Second SPA start in the same browser transfers only index.html (304) + API calls.
- `/api/locale` with a matching `If-None-Match` → `304`.
- A request with an outdated `X-Rapla-Build` succeeds and carries
  `X-Rapla-Build-Mismatch`; the SPA shows the reload dialog once per server id.

## Scope

### In scope
- Cache headers for `/app/**`, build id plumbing, skew filter + SPA reaction, ETag for
  `/api/locale`.

### Out of scope
- Service worker / offline caching.
- Skew handling for Swing (own version check, separate topic).
- CDN or reverse-proxy cache configuration.

## Plan

### Phase 0 — Decide
- [x] Go (2026-09-30); OQ1 → D2, OQ2 → D1.
- [ ] Resolve OQ3, OQ4.

### Phase 1 — Static cache headers (~0.5 day, estimate)
- [x] Three resource handlers in `SpaResourceConfig`: `/app/{file:[\w-]+-[\w-]+\.(?:js|css)}`
      and `/app/media/**` → `max-age=31536000, public, immutable` (no index fallback, so a
      stale chunk name 404s); `/app/**` → `no-cache` with the SPA fallback. Unhashed
      names (`main.js` of a development build, `favicon.ico`) fall to `no-cache`.
      `SpaCacheHeadersTest` (5, green 2026-09-30); fixtures in
      `rapla-app/src/test/resources/static/app/`.
- [x] Review (rapla-review, 2026-09-30): no HIGH/MEDIUM; L1 (PRD regex text) and L2
      (hyphen comment) fixed, L3 `@Tag("e2e")` kept per AGENTS.md §10, L4 cosmetic.

### Phase 2 — Build id + skew (~1–1.5 days, estimate)
- [x] Build id = main bundle name (D2): `SpaBuildMismatchFilter` (rapla-app,
      `org.rapla.server.spring`) adds `X-Rapla-Build-Mismatch` on `/api/**`, never rejects;
      `SpaBuildMismatchTest` (5, green 2026-10-01; red first); fixture `index.html`
      references `main-TESTAB12.js`.
- [x] SPA: `shell/build-check.ts` — `clientBuild`, `BuildCheckService` (one dialog per
      server build, Abbrechen / Neu laden), `buildCheckInterceptor` wired before
      `authInterceptor` in `app.config.ts`; `build-check.spec.ts` (5, green).
- [x] `beforeunload` guard: `shell/unsaved-changes.ts` (`UnsavedChangesService`, root);
      the event sheet and the resource dialog register their `dirty()` with `DestroyRef`
      (owners released the spots 2026-10-01); `unsaved-changes.spec.ts` (2).
- [x] Re-review (rapla-review, 2026-10-01): M1 spec now dispatches a real `beforeunload`
      on the jsdom window; L1 `returnValue = ''` added; L2 outdated (create-mode baseline
      is set, `resource-edit-dialog.component.ts:278/287`); L3 (dev-index stats) accepted.
- [x] Review (rapla-review, 2026-10-01): 0 HIGH, 2 MEDIUM, 5 LOW — M1 lock-free
      `servedBuild()` (classpath index read once, dev index by `lastModified`), M2
      interceptor spec (`HttpTestingController`), L2 non-`/api` test, L3 mutation test
      compares with the same request without header, L5 via M1; L1/L4 below.

### Phase 3 — `/api/locale` ETag (~0.5 day, estimate; after PRD 124 Phase 1)
- [ ] ETag + `private, no-cache`; tier-3 test for 200 → 304 and language change → 200.

## Tests

Tier 3 MockMvc for all header contracts; tier 5 for the dialog show/suppress decision;
manual: deploy a new JAR with a tab open and an unsaved draft → dialog, Cancel keeps
the draft and saving still works.

## Order relative to PRD 124

Phases 1–2 are independent of PRD 124 and go first; the dialog text starts as a German
literal and moves into the catalogue with PRD 124 Phase 2. Phase 3 needs PRD 124 Phase 1
(the SPA must actually load `/api/locale`).

## Open Questions

- **OQ1** — Build id: git commit + timestamp, or `${maven.build.timestamp}` only?
  *Resolution:* neither — the hashed main bundle name, see D2.
- **OQ2** — Mismatch: header only or also reject requests? *Resolution:* header only, never
  reject — see D1.
- **OQ3** — Does a rolling deploy really mix pods behind one user session (sticky
  sessions?) — with alternating pods the dialog could reappear per server id; decides
  whether suppression should key on "newer than mine" instead of "different".
  *Resolution:* pending.
- **Note (L1)** — CORS sets no exposed headers, so a cross-origin client cannot read
  `X-Rapla-Build-Mismatch` and the check is silently off there. The SPA is same-origin
  (prod and the `ng serve` proxy); expose the header only if a cross-origin SPA appears.
- **Note (L4)** — after "Abbrechen" the dialog stays silent for that server build for the
  rest of the page's life (D1). A later reminder (e.g. next day) is a possible follow-up.
- **OQ4** — Reuse `error.wrong_rapla_version` for the dialog text or a new key (PRD 124
  catalogue)? *Resolution:* pending.

## Decisions locked

**D1 — No mutation guard; reload dialog with cancel.** User ruling 2026-09-30. The server
never rejects a request because of a build mismatch — the client may hold unsaved
changes that must still be savable. It only signals the mismatch; the SPA offers a
reload dialog the user can cancel. Rejected: `409` on mismatched mutations and automatic
reload (both can lose unsaved input).

**D2 — Build id = hashed main bundle name.** User ruling 2026-10-01. It answers exactly
"would a reload load a different SPA": a server-only deploy keeps the name and shows no
dialog (a reload would not help). No Maven/`--define` plumbing. Cost: depends on
Angular's `main-<hash>.js` naming — if it changes, the check silently goes off
(`SpaBuildMismatchTest` fails first, since its fixture follows the naming). Rejected:
`${maven.build.timestamp}` (space in the format, dialogs on server-only deploys).
