# PRD 047: Angular Frontend Plugin Model (Native Federation remotes)

**Status:** draft — 2026-05-18
**Date:** 2026-05-18

## Goal

Let a custom deployment (dhbwrapla and similar) ship its **own Angular views,
routes, and UI patterns** — without forking `rapla-angular`, without placing
customer-specific TypeScript in the rapla repo, and without the stock rapla
bundle carrying dormant customer code.

This is the frontend counterpart of PRD 003 (custom *server* deployments) and
PRD 046 (drop-in *server* plugin jars). PRD 003's 2026-05-07 decision pulled
dhbw **Swing** code into `rapla-client`; this PRD deliberately does **not** do
the equivalent for Angular. The two drivers behind the Swing decision do not
transfer:

1. **Signing.** Swing ships as signed JNLP `webclient/*.jar`; collapsing to a
   single signing pass in `rapla-app` is what got dhbw signing to one click.
   Web bundles are **not code-signed** — no jarsigner, no JNLP, no YubiKey.
   The objection is simply absent.
2. **Migration ease.** A one-time Spring Boot 4 / `jakarta.*` / DI concern, not
   a steady-state rule.

The `rapla-client-api` no-split argument is also Java-module-specific —
`rapla-angular` already lives outside the Maven reactor, so there is no module
to split. Customer Angular code therefore **stays with the customer**, in the
dhbwrapla repo, alongside its REST controllers and config.

## Decision (2026-05-18): Model B — runtime micro-frontends

Two models were considered:

- **Model A** — dhbw Angular code lives in `rapla-angular/src/app/plugins/dhbw/`,
  gated by a feature flag, compiled dormant into every stock build.
- **Model B (chosen)** — dhbw owns its Angular code; it builds a separately
  deployed micro-frontend **remote** that the rapla shell loads at runtime.

Model B was chosen: stock rapla carries no dhbw code, dhbw frontend and backend
release on one cadence from one repo, and — because nothing is signed — Model B
costs none of the pain that made the analogous Swing setup expensive.

### Build tool: Native Federation, not Webpack Module Federation

Angular 21 builds with the esbuild-based Application Builder; Webpack Module
Federation does not work with it. The supported path is
[`@angular-architects/native-federation`](https://www.npmjs.com/package/@angular-architects/native-federation)
(21.1.x for Angular 21) — a browser-native (ESM + Import Maps) implementation of
the same mental model that delegates to Angular's esbuild builder.

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

- **`rapla-angular` becomes the host (shell).** It owns the application bell,
  routing, auth, and an **extension-point registry**: a `RAPLA_FEATURE`
  multi-provider `InjectionToken` (the Angular analogue of the server's
  `Map<String, ServerExtension>`). Each contribution is a `RaplaFeature`
  descriptor — `{ id, routes?, navEntries?, panels?, requiresFlag? }`. Stock
  rapla features register through the *same* registry (dogfooded).
- **A remote** is a separately built Native Federation bundle exposing one or
  more `RaplaFeature` descriptors. Its views are standalone components,
  lazy-loaded via the route's `loadComponent`.
- **Discovery is server-driven.** `GET /api/ui-config` returns branding plus a
  list of remote descriptors `{ id, remoteEntry (URL), exposedModule,
  requiresFlag? }`. The controller builds that list by injecting
  `List<RaplaUiRemote>` — a small rapla-server interface — so any bean of that
  type contributed by *any* config (a `DhbwRaplaApplication` `@Bean`, a drop-in
  jar's `@AutoConfiguration`) is collected automatically; stock rapla
  contributes none and the list is empty. This is the same Spring-collection
  pattern as the server's `List<ServerExtension>`. A `provideAppInitializer`
  hook fetches the config at bootstrap, then `loadRemoteModule`s each remote
  whose flag is on, registering its `RaplaFeature`s. The router and nav are
  assembled dynamically from the collected features.
- **Same-origin serving.** dhbwrapla serves its remote bundle as static
  resources from its own Spring Boot server (`static/plugins/dhbw/`). The shell
  loads `remoteEntry.json` from the same origin it was served from — no CORS,
  no CDN.
- **Shared singletons.** `@angular/core`, `@angular/router`, RxJS, etc. are
  shared via import maps so host and remote use one Angular instance and the
  remote inherits the shell's `EnvironmentInjector` — including the auth
  `HttpInterceptor`.

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

dhbwrapla today is pure Java/Maven, server-only. Model B adds **one** new thing:
a frontend remote subtree built by the existing Maven build.

1. **New subtree `dhbwrapla/rapla-plugin-web/`** — a Native Federation *remote*
   Angular project. `federation.config.js` exposes
   `./Feature → src/dhbw.feature.ts`, which exports the `RaplaFeature`
   descriptor (Dualis import route + nav entry). It depends on the published
   `@rapla/feature-api` for the contract type and generates its own OpenAPI
   client from dhbwrapla's root `OPENAPI.yaml`.
2. **Maven wires the ng build.** `frontend-maven-plugin` (pinned Node, hermetic)
   runs `npm ci && npm run build` for `rapla-plugin-web/`, output copied to
   `src/main/resources/static/plugins/dhbw/`. The dhbwrapla fat JAR then serves
   it at `/plugins/dhbw/`. No signing step — operator install stays one-click;
   only the *developer* build gains an npm step.
3. **`DhbwRaplaApplication` overrides the `ui-config` bean** to add
   `{ id:"dhbw", remoteEntry:"/plugins/dhbw/remoteEntry.json",
   exposedModule:"./Feature", requiresFlag:"dhbw.dualis-import" }`, gated by the
   existing `rapla.plugins.dhbw.*` flags in dhbwrapla's `application.yml`.
4. **Runtime:** browser loads the stock SPA from `/app/` (re-exported via the
   `rapla-app` dependency, exactly as the JNLP `webclient/` set is — PRD 003
   §5). SPA calls `/api/ui-config`, gets the dhbw remote descriptor, loads
   `/plugins/dhbw/remoteEntry.json` same-origin, registers the dhbw
   `RaplaFeature`, and the dhbw route appears. The dhbw component calls dhbw
   REST endpoints served by dhbwrapla's own Java controllers, through the
   shell's auth interceptor.

dhbwrapla thus owns its frontend code without ever touching `rapla-angular`,
and stock rapla ships zero dhbw bytes.

## How it works as a drop-in jar (stock deployable)

The dhbwrapla path above is the heavyweight delivery: a full custom deployable
with its own `@SpringBootApplication` and release cadence. The **same remote
artifact** can also be delivered as a PRD 046 drop-in jar — for the lighter
case of "I run a stock rapla and want to add one frontend feature without
forking or rebuilding anything."

The enabling fact: Spring Boot's `WebMvcAutoConfiguration` maps `/**` to
`classpath:/static/` across **every** classpath entry, jars included. PRD
045/046 already add a `./plugins/*` glob to the flat classpath. So a jar in
`./plugins/` carrying `static/plugins/<id>/remoteEntry.json` + chunks is served
at `/plugins/<id>/…` with **no controller** — a Native Federation `remoteEntry`
and its esbuild chunks are plain static files.

A frontend drop-in jar carries both halves in one artifact:

```
rapla-plugin-<id>-1.0.jar               ← dropped into ./plugins/
├── META-INF/spring/…AutoConfiguration.imports → <Id>PluginAutoConfiguration
├── org/rapla/plugin/<id>/…             → server REST controllers
│                                         + @Bean RaplaUiRemote (the bridge)
└── static/plugins/<id>/
    ├── remoteEntry.json                ← the Native Federation remote
    └── chunk-*.js                         (built by an ng build at jar-build time)
```

`<Id>PluginAutoConfiguration` is discovered exactly the way PRD 046's
server plugins are — Spring Boot aggregates `AutoConfiguration.imports` from
every classpath jar. Its one frontend-specific contribution is a `RaplaUiRemote`
`@Bean` (`new RaplaUiRemote("<id>", "/plugins/<id>/remoteEntry.json",
"./Feature")`), which the stock `/api/ui-config` controller collects via
`List<RaplaUiRemote>`. Drop the jar in → the bean appears → the host shell sees
the remote and loads it same-origin. No custom `@SpringBootApplication`, no
rebuild of rapla, no npm on the operator's machine, no signing.

**dhbwrapla and a drop-in jar are the same artifact shape** — identical
`static/plugins/<id>/` layout, identical `RaplaUiRemote` bean, identical
`RaplaFeature` contract. The only differences:

| | dhbwrapla (Model B) | Drop-in jar (PRD 046) |
|---|---|---|
| Remote bundle location | baked into the dhbwrapla fat JAR | inside a plain library jar in `./plugins/` |
| `RaplaUiRemote` registration | `@Bean` in `DhbwRaplaApplication` | `@Bean` in the jar's `@AutoConfiguration` |
| Discovery | dhbwrapla's component scan | `AutoConfiguration.imports` aggregation |
| Delivery | full custom deployable | one jar beside stock rapla |
| Gating | `rapla.plugins.dhbw.*` flag | the jar's *presence* is the toggle (no flag) |

The plugin author still runs an `ng build` at jar-build time (the
`frontend-maven-plugin` step) — the plugin project is dhbwrapla's
`rapla-plugin-web/` remote plus its server controllers, packaged as one library
jar instead of a fat JAR. The *operator* never sees npm.

**Mandatory namespacing.** Two jars both shipping `static/index.html` would
collide. Every frontend plugin must serve only under
`static/plugins/<unique-id>/`; an architecture test enforces it (Phase 5).

## Scope

In scope:
- `RaplaFeature` contract + `RAPLA_FEATURE` registry + dynamic route/nav
  assembly in the `rapla-angular` host.
- `RaplaUiRemote` server interface + `/api/ui-config` endpoint and default bean.
- Native Federation host wiring + runtime `loadRemoteModule`.
- `@rapla/feature-api` published contract package.
- dhbwrapla `rapla-plugin-web/` remote + Maven build integration.
- The PRD 046 drop-in-jar delivery: a frontend plugin jar carrying server beans
  *and* `static/plugins/<id>/` remote assets, served from `./plugins/` on a
  stock deployable. Same artifact shape as the dhbwrapla remote.

Out of scope:
- Converting stock rapla features into remotes (they stay in the host bundle).
- A CDN / cross-origin remote hosting story (same-origin only for now).

## Plan

- **Phase 1 — Host registry.** `RaplaFeature` interface + `RAPLA_FEATURE` token
  in `rapla-angular`; refactor the stock app's own routes/nav to register
  through it. No federation yet.
- **Phase 2 — Server config.** `/api/ui-config` controller + bean (branding +
  `remotes:[]` default); `provideAppInitializer` config loader in the host.
- **Phase 3 — Native Federation host.** Add `@angular-architects/native-federation`,
  `withNativeFederation` bootstrap, runtime `loadRemoteModule` for each
  configured remote, flag filtering.
- **Phase 4 — Contract package.** Publish `@rapla/feature-api` (the
  `RaplaFeature` types + the shared-dependency manifest a remote builds against).
- **Phase 5 — dhbwrapla remote.** Scaffold `rapla-plugin-web/`, wire
  `frontend-maven-plugin`, the `DhbwRaplaApplication` `ui-config` `@Bean`
  override, and a dhbw OpenAPI client.
- **Phase 6 — Drop-in delivery.** Confirm classpath-jar `static/` serving for a
  `./plugins/` jar; the `static/plugins/<id>/` namespacing architecture test;
  package the dhbw remote as a PRD 046 drop-in jar variant to prove the shared
  artifact shape.
- **Phase 7 — Docs + e2e.** Operator/developer docs; tier-7 Playwright tests
  for both delivery modes.

## Tests

- **Tier 5 (Angular unit):** host registry collects `RAPLA_FEATURE` providers
  and filters by `requiresFlag`; `provideAppInitializer` config loader parses
  `/api/ui-config`.
- **Tier 3 (MockMvc):** `/api/ui-config` — stock returns `remotes:[]`; assert it
  exposes no feature whose flag is off (existence of a customer feature is
  itself information — apply the AGENTS.md §12 / `data-leak-prevention` lens to
  the flag list).
- **Tier 7 (Playwright):** two delivery modes — (a) boot the dhbwrapla
  deployable; (b) boot a stock rapla deployable with the dhbw plugin jar in
  `./plugins/`. In both, assert the remote loads from `remoteEntry.json`, its
  route renders, and it reaches a dhbw endpoint.
- **Architecture test:** every frontend plugin jar serves only under
  `static/plugins/<id>/` — no top-level `static/` collision with the host.
- **Version-skew test:** a remote built against an older `@rapla/feature-api`
  major — confirm the host rejects it cleanly rather than crashing.

## Open Questions

- **Version skew.** Host shell and remote are built and released separately;
  Native Federation shares Angular singletons via import maps, so a major
  Angular skew between shell and remote breaks at runtime. Proposed policy: a
  remote declares the `@rapla/feature-api` major it targets; the host refuses to
  load an incompatible remote and surfaces a clear error; a dhbwrapla release is
  pinned to a compatible rapla release. Needs a documented compatibility matrix.
- **Build coupling.** dhbwrapla's Maven build gains a Node/npm step. The
  operator install stays one-click (no signing, no extra runtime step), but the
  developer build is heavier and needs network for the pinned Node download.
  Acceptable? Alternative: build the remote out-of-band and commit the artifact.
- **Auth.** Confirm the remote's generated OpenAPI client routes through the
  shell's `HttpInterceptor` (inherited via the shared injector) rather than
  carrying its own auth — it should, but verify in Phase 5.
- **Panels / extension slots.** Phase 1 ships `routes` + `navEntries`. The
  `panels` contribution (toolbar buttons, edit-dialog tabs — the Swing
  `ReservationToolbarExtension` / `ReservationWizardExtension` analogue) needs
  named slots in the shell; defer the slot catalogue to a Phase 1 follow-up.
