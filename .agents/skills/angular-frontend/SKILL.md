---
name: angular-frontend
description: Use when editing, building, type-checking, testing, debugging in a real browser, or running the rapla Angular SPA (`rapla-angular/`, served at `/app/`) — including the build/lint commands, code-style/Prettier/ESLint config, the Vitest + TestBed patterns for tier-5 (TS unit) and tier-6 (component) tests from AGENTS.md §10's pyramid, the cookie-session auth model and the reactive-401 refresh interceptor (`auth.interceptor.ts`) with its "never tie the shared refresh to a request subscription" footgun, the GraphQL-only data model (the SPA has no generated OpenAPI client — auth + `/api/graphql` via plain `HttpClient`), the "probe the wire with curl before coding against an endpoint" workflow, and the Playwright MCP usage patterns (`mcp__playwright__browser_*` tools) for browser-driven debug and prototyping of OAuth flows, CORS, JS-side state, and DOM rendering. Skip for server-only or Swing-only work; the never-do rule (don't restart `ng serve`) lives inline in AGENTS.md §14 and applies without loading this skill.
---

# Angular frontend (`rapla-angular/`)

The SPA lives in `rapla-angular/` (Angular 21, served at `/app/`). Full layout + URL space is in `rapla-angular/README.md` and PRD 026.

## Build discipline — mirrors AGENTS.md §5

| Routine | Command | When |
|---|---|---|
| Type-check after an edit | `cd rapla-angular && npm run build:fast` (= `ng build --configuration development`) | After every edit. ~15–25 s cold, ~5–10 s warm. The Angular equivalent of `mvn compile`. |
| Full lint + build | `npm run build` (= `npm run lint && ng build`) | Session end or before handoff. Confirms a clean state. |
| Auto-fix formatting | `npm run format` | When lint flags Prettier-fixable noise. |

**Don't run `npm test` / `ng test` during a session** — Vitest + jsdom is slow and noisy; the user runs it at session end or in CI. Same rationale as AGENTS.md §5's "`mvn test` only at session end" rule.

**Don't run `npm run lint` repeatedly during a session** — walks the whole tree. One pass at session end via `npm run build` is enough.

## The `ng serve` dev server — owned by the user

The user runs `ng serve` in their own terminal via `npm run start:ai`, which selects the `ai-develop` config in `angular.json` (`liveReload: false` / `hmr: false` / `poll: 3000` — avoids mid-edit reloads when an agent is rapidly editing). Do not start, stop, restart, or `kill` it. If a change isn't visible, ask the user to check their `ng serve` terminal. Spring Boot is fine to restart per AGENTS.md §8; this rule applies only to the Angular dev server.

## No generated client — the SPA is GraphQL + cookie-`HttpClient` only

There is **no** generated OpenAPI client (`src/app/api/`) and **no** `npm run gen:api` step. The SPA reaches the server through two plain `HttpClient` paths only: auth (`/api/auth/*` via `AuthService` + the refresh interceptor) and the GraphQL view transport (`POST /api/graphql` via `GraphqlService`). All data is the declarative view system (PRD 074/078); there are no per-endpoint typed DTOs to regenerate. The server still produces its OpenAPI spec at `/api/v3/api-docs` for the Scalar/Swagger explorers — that's server-side, unrelated to the SPA.

## Code-style

Same defaults as AGENTS.md §4 (no comments unless requested, constructor injection — use Angular `inject()` or constructor params, not the `@Inject` property decorator). Formatting/lint is Prettier (`.prettierrc`, 100-col, single-quote) + ESLint flat config (`eslint.config.js`, typescript-eslint + `@angular-eslint` recommended).

## Calendar surfaces — own implementation, EventCalendar as attributed reference only

The calendar-library question is **decided** (PRD `done/032` §Calendar view decision, 2026-07-07) —
don't re-litigate it: rapla builds its calendar surfaces itself, in the existing Angular build
chain. **No runtime calendar library, no fork, no second build chain.** The month grid (PRD 095)
is the first surface: `views/month-chunks.ts` (chunking/stacking math, pure TS, tier-5-tested) +
`views/month-grid.component.ts` (spanning bars), mounted by `ViewHostComponent` when
`renderMode === 'month'`; `renderModes` come from the server's `@view` directive via
`extensions.view`.

Two rules that fire whenever you extend these surfaces (week grid, drag interactions, …):

- **Reference hierarchy:** rapla's own Swing/HTML/builder code is the PRIMARY reference
  (interaction semantics: `rapla-client/.../calendarview/swing/DraggingHandler` +
  `SelectionHandler`; multi-resource grid: `rapla-server/.../dayresource/server/`); the
  EventCalendar source (github.com/vkurko/calendar) is a SECONDARY, **read-only** reference for
  browser pointer patterns and grid CSS — clone it as a sibling/scratchpad checkout, never into
  this repo, never consume it from npm.
- **MIT attribution is mandatory for copied OR closely-translated code** (a Svelte→TS port of
  their algorithm counts; patterns/ideas are free): per-file header
  `Portions derived from EventCalendar (https://github.com/vkurko/calendar), Copyright (c)
  Vladimir Kurko, MIT License — see LICENSE_MIT_EVENTCALENDAR.` — the full MIT text lives at
  repo root `LICENSE_MIT_EVENTCALENDAR`. `month-chunks.ts` is the existing example.

## Writing tests — tier 5 (TS unit) and tier 6 (component)

AGENTS.md §10's pyramid covers the decision rule (default tier 5; mount with `TestBed` only when behaviour depends on template/DOM). Conventions for this repo:

- **Test runner:** Vitest. **DOM env:** jsdom (configured by `@angular/build`).
- **File location:** `*.spec.ts` colocated with the source it tests. `app.spec.ts` next to `app.ts`.
- **Pure-TS test (tier 5)** — construct the unit with `new` (or `inject` inside a `runInInjectionContext`), assert on its return values. No `TestBed`. Fastest, default choice.
- **Component test (tier 6)** — canonical shape:

  ```typescript
  import { TestBed } from '@angular/core/testing';
  import { MyComponent } from './my-component';

  describe('MyComponent', () => {
    beforeEach(async () => {
      await TestBed.configureTestingModule({ imports: [MyComponent] }).compileComponents();
    });

    it('renders the title', async () => {
      const fixture = TestBed.createComponent(MyComponent);
      await fixture.whenStable();
      const el = fixture.nativeElement as HTMLElement;
      expect(el.querySelector('h1')?.textContent).toContain('expected');
    });
  });
  ```

- **Mocking guidance** — mirrors AGENTS.md §13's spirit. Don't mock your own services or models if you can construct them with `new`. Use `vi.fn()` / `vi.spyOn()` for true collaborators you don't own (the `HttpClient` boundary, `Router`, `OAuthService`). Prefer real `BehaviorSubject` / `of(...)` over mock observables — they're free.
- **jsdom limits** — no real layout (no `getBoundingClientRect` heuristics), no real `fetch` (use `HttpTestingController`), no real navigation. If a test needs any of those, it's a browser e2e (PRD 033 Playwright MCP), not tier 6.
- **Don't run `npm test` / `ng test` mid-session** (see Build discipline above). Type-check via `npm run build:fast`; let the user / CI run the suite.

## Auth — cookie session + reactive-401 refresh (`auth.interceptor.ts`)

The SPA holds NO token: the rapla JWT is in an HttpOnly `access_token` cookie
(TTL ~1 h) the browser auto-sends same-origin; a longer-lived `refresh_token`
cookie (21 d, `Path=/api/auth/refresh`) backs silent renewal. `auth.interceptor.ts`
does the reactive refresh — on a 401 from `/api`, POST `/api/auth/refresh` once,
replay the original request, sharing ONE refresh across concurrent 401s. Identity
comes from `GET /api/auth/me` (`AuthService`). Full design: `docs/authentication.md`
§ "Angular SPA". Don't add an `Authorization` header — the cookie carries auth.

**Footgun — never tie the shared refresh to a request's subscription.** The
refresh must be a module-global, eagerly-subscribed `Observable` (`shareReplay(1)`
+ `finalize`-reset), independent of whichever request first hit the 401. If you
instead own the refresh inside that request's `from(doRefresh()).pipe(switchMap(...))`
chain and track it in an `isRefreshing` flag reset only inside that `switchMap`,
a torn-down owner wedges the whole app: `omnibox.component.ts` cancels its search
on every keystroke via `switchMap`, and route changes / tab-freeze do the same —
so the reset never runs, the flag sticks, and every later 401 hangs forever.

Symptom to recognise (cost a session to diagnose): after a long idle the access
token has expired but the refresh token is still valid; the first interaction
401s and the SPA hangs on "loading", and **only a full page reload (fresh JS
context) fixes it** — a refresh that works on reload but not in-app is the
signature of stuck module-global refresh state, not a server/cookie TTL problem.
Regression test: `auth.interceptor.spec.ts` → "recovers when the refresh-owning
request is torn down mid-refresh". Test refresh-state changes with
`HttpTestingController`, draining microtasks between the 401, the `/refresh`, and
the replay (the refresh is Promise-backed — see the spec's `flushMicrotasks`).

## Probe the wire first — `curl` the REST call before coding against it

Before writing SPA code that consumes a REST endpoint, `curl` it with the exact request the SPA will send (auth header, query params, JSON body) and inspect the response shape. Avoids guessing field names, missing required filters, or assuming the wrong JSON nesting. The **`api-testing`** skill bundles the login → bearer-token → request loop.

Concretely: empty `resources` array on `POST /api/storage/queryAppointments` returns no events (saved hours of "why is the table empty" debugging); reservation links are under `links.resources`, not `links.allocatable` (different relation entirely). Probe first, then write to match.

## Browser-driven debug + prototype — Playwright MCP

Setup is in `docs/development.md` § "Playwright MCP — install" (one-off: system Chrome `.deb` + `claude mcp add playwright … --browser chrome` — that section also has the WSLg headed-window troubleshooting). When installed, the agent has `mcp__playwright__browser_*` tools.

**Reach for Playwright when** the bug or the prototype lives in the browser — OAuth redirects, CORS, cookies / `sessionStorage`, JS-side state, Material theming, what-the-DOM-actually-renders. `curl` proves wire shape; Playwright shows what the browser does with it.

**Skip Playwright when** you can answer the question with `curl` (use `api-testing` skill) or with a `TestBed` component test (tier 6 above) — both are seconds faster and don't need a running dev stack.

**Core tools (the ones that matter day-to-day):**

| Tool | Use |
|---|---|
| `browser_navigate` | Go to a URL. Start at `http://localhost:4200/app/` (ng serve dev) or `http://localhost:8051/app/` (fat-JAR prod-parity). |
| `browser_snapshot` | Structured accessibility-tree dump of the page. Cheaper + more reliable than a screenshot for "what's on the page right now". Returns refs you can click/fill against. |
| `browser_click`, `browser_fill`, `browser_select_option` | Drive the UI. Use the refs from `browser_snapshot`. |
| `browser_network_requests` | Inspect requests + responses (status, headers, JSON body). Replaces "user pasted Network tab" in the old loop. |
| `browser_console_messages` | JS errors / warnings. Always read this before diagnosing a SPA bug. |
| `browser_evaluate` | Run JS in page context — read `sessionStorage`, inspect Angular component state, etc. |
| `browser_take_screenshot` | Visual confirmation. Drop into `.playwright-mcp/` (gitignored). |

**Canonical debug loop:**

1. `browser_navigate` to the page where the bug shows.
2. `browser_console_messages` — read JS errors first (90% of SPA bugs surface here).
3. `browser_network_requests` — find the failing request, inspect status + body.
4. `browser_snapshot` to confirm rendered state, then `browser_evaluate` if you need to peek at JS-side state.
5. Edit code → `npm run build:fast` → user reloads their `ng serve` tab → re-run from step 1.

**Login shortcut for the dev server:** with `--user-data-dir` enabled (see setup doc), one OAuth login persists for the session. With `--isolated` (default for unattended), each navigate triggers a fresh OAuth roundtrip — fine for one-shot probes, painful for iteration.

**Artefacts (`.playwright-mcp/*.yml`, `*.png`) are gitignored.** Don't commit them.

### Throwaway UI prototypes — local-first, Artifact only on request

Christopher develops alone; a shareable URL has no value during iteration.
For HTML mockups/prototypes the loop is strictly local:

1. Write the prototype into the session scratchpad.
2. Serve it locally (`python3 -m http.server <port>` in the scratchpad) —
   the Playwright browser cannot open `file://`.
3. **Self-test with Playwright BEFORE showing it**: load the page, read
   `browser_console_messages`, click through the core flow. A prototype
   that was never loaded in a browser is untested (a single quote-escaping
   syntax error once shipped a completely dead page — 2026-07-06).
4. Iterate on the local URL — the user watches the same Playwright window;
   after an edit just reload. Don't drive clicks while the user is
   interacting with the page.

Publish an Artifact ONLY when explicitly asked, or when the user wants to
test on another device (mobile) / keep a milestone beyond the session.
Artifact pages run under a strict CSP (no CDN, no external JS/fonts) —
hand-roll widgets there; never conclude from an Artifact prototype that a
library "isn't available" for the real SPA.

### Material widget traps (learned 2026-07-07, quick-edit prototype)

For a Material prototype, hand-rolled HTML is useless — build a throwaway
standalone component under `src/app/proto/` + one clearly-marked route in
`app.routes.ts` (delete both afterwards). Note: the app-initializer loads
`/api/auth/me` and the interceptor bounces ANY page to `/login` on failure —
guard-less routes still need a logged-in session.

- **`mat-timepicker` `valueChange` fires on programmatic `[value]` writes
  too** (unlike `mat-datepicker`'s `dateChange`, which is user-only). A
  handler that writes back a fresh `Date` instance on every echo loops
  change detection forever (`NG0103`) — and a side effect of the loop is
  that ALL CDK overlays stop positioning (panes stuck at 0,0 top-left).
  Fix: equality-guard the setters (`if (next.getTime() === cur.getTime())
  return`). Same family: `[value]="new Date(iso)"`-style bindings mint a NEW
  instance every CD cycle and loop the same way even with guarded setters —
  bind STABLE `Date` references (memoize per iso string). If overlays pile
  up unpositioned at the viewport corner, check the console for NG0103
  FIRST — it's not an overlay/CSS problem.
- **Never `position: fixed` overlays inside routed components.** Routed
  content renders inside the sidenav content's stacking context, so the
  drawer paints OVER your overlay regardless of z-index. Use `MatDialog`
  (global overlay container): backdrop-less + `position` at the click point
  for popup cards, `width/height/maxWidth: 100vw/vh` for fullscreen sheets.
  Draggable dialog = `cdkDrag` + `cdkDragRootElement=".cdk-overlay-pane"` +
  `cdkDragHandle` on the grip.

### Playwright Agents (Planner / Generator / Healer)

Set up by PRD 044 (`npx playwright init-agents`). They live **under `rapla-angular/`**
and are **Claude Code subagents**, not a CLI — there is no `npx playwright agent`
command. Use them with a session rooted at `rapla-angular/` so the subagents,
`.mcp.json`, and the relative `tests/`/`specs/` paths resolve.

| Subagent (`.claude/agents/`) | Input | Output |
|---|---|---|
| `playwright-test-planner` | NL scenario + `tests/seed.spec.ts` | A test plan under `specs/` |
| `playwright-test-generator` | A plan | An executable `tests/**/*.spec.ts` |
| `playwright-test-healer` | A failing spec | Patched selectors / assertions |

They drive the browser via a *second* MCP server, **`playwright-test`**
(`npx playwright run-test-mcp-server`, declared in `rapla-angular/.mcp.json`) —
tools surface as `mcp__playwright-test__*`, distinct from the interactive
`playwright` MCP (`mcp__playwright__*`).

Reach for these when authoring tier-7 **browser e2e tests** (AGENTS.md §10; not
yet wired into CI per PRD 034 Phase 4). Don't reach for them for Vitest
`TestBed` component tests — that's tier 6.

**Workflow for adding a new e2e test:**

1. Dev stack up (Spring Boot per AGENTS.md §8 — specs hit `:8051/app/`).
2. Delegate to the `playwright-test-planner` subagent with a scenario → it
   writes a plan into `rapla-angular/specs/`.
3. Delegate to `playwright-test-generator` with that plan → it writes a
   `rapla-angular/tests/**/*.spec.ts`.
4. `cd rapla-angular && npm run e2e` (= `playwright test`, system Chrome via
   `channel: 'chrome'`). If a spec fails on selector drift, delegate to
   `playwright-test-healer`.
