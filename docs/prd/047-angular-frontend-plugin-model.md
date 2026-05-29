# PRD 047: Angular Frontend Plugin Model (Native Federation remotes)

**Status:** draft — 2026-05-18
**Date:** 2026-05-18

## Goal

Let custom deployments (dhbwrapla and similar) ship their own Angular views, routes, and UI patterns — without forking `rapla-angular`, without customer TypeScript in the rapla repo, and without dormant customer code in stock builds.

Frontend counterpart of PRD 003 (custom server deployments) and PRD 045 §4 (drop-in server plugin jars). Unlike PRD 003 (2026-05-07), this PRD does NOT pull customer code into rapla — the two drivers behind that Swing decision don't transfer:

1. **Signing.** Swing ships as signed JNLP jars; web bundles are not code-signed (no jarsigner / JNLP / YubiKey).
2. **Migration ease.** A one-time SB4 / `jakarta.*` concern, not steady-state.

The `rapla-client-api` no-split argument is Java-module-specific; `rapla-angular` lives outside the Maven reactor. Customer Angular code stays with the customer.

## Decision (2026-05-18): Model B — runtime micro-frontends

Model A (dhbw code in `rapla-angular/src/app/plugins/dhbw/` gated by flag, compiled into every stock build) rejected: dhbw code in stock bundle, cross-repo release coupling.

Model B (chosen): dhbw owns its Angular code as a separately built micro-frontend **remote** loaded by the shell at runtime. Stock rapla carries no dhbw code, dhbw releases on one cadence from one repo, and the unsigned-bundle property avoids the cost that made Swing Model A win.

### Build tool: Native Federation, not Webpack Module Federation

Angular 21's esbuild Application Builder doesn't work with Webpack Module Federation. Supported path: [`@angular-architects/native-federation`](https://www.npmjs.com/package/@angular-architects/native-federation) (21.1.x) — browser-native (ESM + Import Maps), delegates to Angular's esbuild builder.

## Architecture

```
                 rapla-app fat JAR (stock)            dhbwrapla fat JAR
                 ─────────────────────────            ─────────────────────────
  HOST (shell)   static/app/  = rapla-angular host  ← re-exported transitively
                 GET /api/ui-config → remotes: []      GET /api/ui-config →
                                                         remotes:[{id:"dhbw", …}]
  REMOTE                                     ──────►   static/plugins/dhbw/
                                                         remoteEntry.json + chunks
```

- **`rapla-angular` = host (shell)** — owns routing, auth, and a `RAPLA_FEATURE` multi-provider `InjectionToken` (Angular analogue of the server's `Map<String, ServerExtension>`). Each contribution is a `RaplaFeature` descriptor `{ id, routes?, navEntries?, panels?, requiresFlag? }`. Stock rapla features register through the same registry (dogfooded).
- **A remote** = separately-built Native Federation bundle exposing `RaplaFeature` descriptors. Views are standalone components, lazy-loaded via route `loadComponent`.
- **Server-driven discovery.** `GET /api/ui-config` returns branding + remote descriptors `{ id, remoteEntry, exposedModule, requiresFlag? }`. Controller injects `List<RaplaUiRemote>` (small rapla-server interface) — any bean of that type contributed by any config is collected (same pattern as `List<ServerExtension>`). Stock rapla contributes none. A `provideAppInitializer` fetches config, `loadRemoteModule`s each enabled remote, registers its features; router + nav assembled dynamically.
- **Same-origin serving.** dhbwrapla serves the remote bundle from its own Spring Boot `static/plugins/dhbw/`. Shell loads `remoteEntry.json` same-origin — no CORS, no CDN.
- **Shared singletons.** `@angular/core`, `@angular/router`, RxJS shared via import maps — one Angular instance, remote inherits shell's `EnvironmentInjector` (incl. auth `HttpInterceptor`).

### What lives where

| Concern | Stock rapla repo | dhbwrapla repo |
|---|---|---|
| Host shell + `RAPLA_FEATURE` registry | `rapla-angular/` | — |
| `RaplaFeature` contract (types, shared) | published as `@rapla/feature-api` npm pkg | consumed as dep |
| dhbw Angular views / routes / patterns | — | new `rapla-plugin-web/` remote project |
| `/api/ui-config` bean (default: `remotes:[]`) | `rapla-server` | `@Bean` override in `DhbwRaplaApplication` |
| dhbw remote bundle | — | built by Maven into `static/plugins/dhbw/` |
| dhbw REST endpoints the views call | — | already there (`ImportController`, Dualis) |

## How it works in dhbwrapla

Model B adds one thing to today's pure Java/Maven dhbwrapla: a frontend remote subtree built by Maven.

1. **`dhbwrapla/rapla-plugin-web/`** — Native Federation remote project. `federation.config.js` exposes `./Feature → src/dhbw.feature.ts`. Depends on `@rapla/feature-api` and generates its own OpenAPI client from dhbwrapla's `OPENAPI.yaml`.
2. **Maven wires the ng build.** `frontend-maven-plugin` (pinned Node) runs `npm ci && npm run build`; output to `src/main/resources/static/plugins/dhbw/`, served at `/plugins/dhbw/` from the fat JAR. No signing. Only developer build gains npm.
3. **`DhbwRaplaApplication` overrides `ui-config` bean** with `{ id:"dhbw", remoteEntry:"/plugins/dhbw/remoteEntry.json", exposedModule:"./Feature", requiresFlag:"dhbw.dualis-import" }`, gated by existing `rapla.plugins.dhbw.*` flags.
4. **Runtime:** browser loads SPA from `/app/` (re-exported via `rapla-app` dep, like JNLP `webclient/`), calls `/api/ui-config`, loads `/plugins/dhbw/remoteEntry.json` same-origin, registers feature, route appears. dhbw component calls dhbw REST endpoints through the shell's auth interceptor.

Stock rapla ships zero dhbw bytes.

## How it works as a drop-in jar (stock deployable)

dhbwrapla above is the heavyweight delivery (full `@SpringBootApplication`). The **same remote artifact** also works as a PRD 045 §4 drop-in jar for "stock rapla + one frontend feature, no fork, no rebuild."

Enabler: Spring Boot's `WebMvcAutoConfiguration` maps `/**` to `classpath:/static/` across every classpath entry including jars. PRD 045 adds `./plugins/` to `loader.path` — so a jar in `./plugins/` carrying `static/plugins/<id>/remoteEntry.json` + chunks is served at `/plugins/<id>/…` with no controller (esbuild chunks are plain static files).

A frontend drop-in jar carries both halves:

```
rapla-plugin-<id>-1.0.jar               ← dropped into ./plugins/
├── META-INF/spring/…AutoConfiguration.imports → <Id>PluginAutoConfiguration
├── org/rapla/plugin/<id>/…             → server REST controllers
│                                         + @Bean RaplaUiRemote (the bridge)
└── static/plugins/<id>/
    ├── remoteEntry.json                ← the Native Federation remote
    └── chunk-*.js                         (built by an ng build at jar-build time)
```

`<Id>PluginAutoConfiguration` is discovered via PRD 045 §4's `AutoConfiguration.imports` aggregation. Its frontend contribution is a `RaplaUiRemote` `@Bean`; the stock `/api/ui-config` controller collects via `List<RaplaUiRemote>`. Drop jar in → bean appears → shell loads remote same-origin. No custom app, no rebuild, no operator npm, no signing.

**dhbwrapla and drop-in jar are the same artifact shape** — identical layout, bean, contract. Differences:

| | dhbwrapla (Model B) | Drop-in jar (PRD 045 §4) |
|---|---|---|
| Bundle location | dhbwrapla fat JAR | plain library jar in `./plugins/` |
| `RaplaUiRemote` reg | `@Bean` in `DhbwRaplaApplication` | `@Bean` in jar's `@AutoConfiguration` |
| Discovery | dhbwrapla scan | `AutoConfiguration.imports` |
| Delivery | full custom deployable | one jar beside stock rapla |
| Gating | `rapla.plugins.dhbw.*` flag | jar presence is the toggle |

Plugin author runs `ng build` at jar-build time (frontend-maven-plugin). Operator never sees npm.

**Mandatory namespacing.** Every plugin serves only under `static/plugins/<unique-id>/`; architecture test enforces it (Phase 5).

## Scope

In scope:
- `RaplaFeature` contract + `RAPLA_FEATURE` registry + dynamic route/nav
  assembly in the `rapla-angular` host.
- `RaplaUiRemote` server interface + `/api/ui-config` endpoint and default bean.
- Native Federation host wiring + runtime `loadRemoteModule`.
- `@rapla/feature-api` published contract package.
- dhbwrapla `rapla-plugin-web/` remote + Maven build integration.
- The PRD 045 §4 drop-in-jar delivery: a frontend plugin jar carrying server
  beans *and* `static/plugins/<id>/` remote assets, served from `./plugins/`
  on a stock deployable. Same artifact shape as the dhbwrapla remote.

Out of scope:
- Converting stock rapla features into remotes (they stay in the host bundle).
- A CDN / cross-origin remote hosting story (same-origin only for now).

## Plan

- **Phase 1** — Host registry: `RaplaFeature` + `RAPLA_FEATURE` token; refactor stock routes/nav through it. No federation yet.
- **Phase 2** — Server config: `/api/ui-config` + `remotes:[]` default; `provideAppInitializer` loader.
- **Phase 3** — Native Federation host: `withNativeFederation` bootstrap, `loadRemoteModule`, flag filtering.
- **Phase 4** — Contract package: publish `@rapla/feature-api` + shared-dependency manifest.
- **Phase 5** — dhbwrapla remote: scaffold `rapla-plugin-web/`, wire `frontend-maven-plugin`, `ui-config` override, OpenAPI client.
- **Phase 6** — Drop-in delivery: classpath-jar `static/` serving, namespacing arch test, package dhbw remote as drop-in jar variant.
- **Phase 7** — Docs + tier-7 Playwright tests for both modes.

## Tests

- **Tier 5 (Angular unit):** host collects `RAPLA_FEATURE` providers, filters by `requiresFlag`; config loader parses `/api/ui-config`.
- **Tier 3 (MockMvc):** stock returns `remotes:[]`; flag-off features not exposed (apply §12 data-leak lens to the flag list).
- **Tier 7 (Playwright):** (a) dhbwrapla deployable; (b) stock + dhbw jar in `./plugins/` — both load `remoteEntry.json`, render the route, hit dhbw endpoint.
- **Architecture test:** every plugin under `static/plugins/<id>/`.
- **Version-skew:** remote built against older `@rapla/feature-api` major — host rejects cleanly.

## Open Questions

- **Version skew.** Native Federation shares Angular singletons via import maps; major Angular skew breaks at runtime. Policy: remote declares `@rapla/feature-api` major; host refuses incompatible. Needs documented compat matrix.
- **Build coupling.** dhbwrapla Maven build gains Node/npm. Operator install stays one-click; developer build needs network for pinned Node. Alternative: build out-of-band and commit artifact.
- **Auth.** Confirm remote's OpenAPI client routes through shell's `HttpInterceptor` (via shared injector). Verify Phase 5.
- **Panels / extension slots.** Phase 1 ships `routes` + `navEntries`. `panels` (toolbar / edit-dialog tabs — Swing `ReservationToolbarExtension` analogue) needs named slots; defer to Phase 1 follow-up.
