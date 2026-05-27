# rapla-angular

Angular SPA for rapla. Served at `/app/` in both dev (via `ng serve`
proxy) and prod (via Spring Boot static handler).

## Quick start

```bash
# 1) one-time install
cd rapla-angular && npm install

# 2) Terminal A — Spring Boot (REST + OAuth2 on :8051)
mvn -pl rapla-app -am spring-boot:run -Dspring-boot.run.fork=false

# 3) Terminal B — Angular dev server (proxies API to :8051)
cd rapla-angular && npm start          # human dev, with HMR
# OR
cd rapla-angular && npm run start:ai   # agent-driven dev, no auto-reload

# 4) open http://localhost:4200/app/
```

Default login on the bundled dev DB: **user `admin`, empty password**.

## Available scripts

| Script | What it does | When |
|---|---|---|
| `npm start` | `ng serve` with HMR + live-reload | Default human dev workflow |
| `npm run start:ai` | `ng serve` with `liveReload:false`, `hmr:false`, `poll:3000` | Use when an AI agent is editing — page won't keep reloading mid-edit |
| `npm run build:fast` | `ng build` only | Type-check after a code change |
| `npm run build` | `npm run lint && ng build` | Session end / pre-handoff sanity check |
| `npm run lint` | ESLint + Prettier `--check` | Run via `build`; rarely directly |
| `npm run format` | Prettier `--write` | Auto-fix formatting before commit |
| `npm test` | Vitest (`ng test`) | Session end / CI |

## Prerequisites (once-off)

See PRD 026 §Phase 0 "What needs to be installed". Short version:

```bash
sudo apt-get install -y libatomic1
nvm use --lts                                          # Node 22+
npm install -g @angular/cli
```

## First run

```bash
cd rapla-angular
npm install
```

REST wire-format types (`TablePage`, `TableRow`, etc.) live as hand-rolled
TypeScript interfaces under each feature module (e.g.
`reservations/table.types.ts`). Type drift is guarded by Java contract
tests on the server side — e.g. `TableViewServiceContractTest` pins the
record shapes. The SPA is on the path to GraphQL; REST codegen was
removed to avoid maintaining a throwaway build artefact.

## Dev workflow A — HMR via `ng serve` (recommended for humans)

Two terminals. The Angular dev server at `:4200` handles SPA assets;
the proxy forwards `/api/**`, `/oauth2/**`, `/rapla/**`, etc. to Spring.

```bash
# Terminal A — Angular dev server with HMR
cd rapla-angular && npm start
# (this runs: ng serve --proxy-config proxy.conf.js)

# Terminal B — Spring Boot
mvn -pl rapla-app -am spring-boot:run -Dspring-boot.run.fork=false
```

Open **http://localhost:4200/app/** — same path as prod, just a
different port. Edits to `.ts`/`.html` hot-reload in the browser.

API calls from the SPA still hit `/api/auth/login`, `/api/storage/...`
etc. — the proxy forwards them to Spring on `:8051` transparently.

### Dev workflow A' — `npm run start:ai` for agent-driven sessions

When an AI agent is editing files, default `ng serve` rebuilds and live-reloads
on every save — bursts of edits cause partial reloads, broken in-between
states, and re-init traffic. Use the **`ai-develop`** serve configuration
instead:

```bash
cd rapla-angular && npm run start:ai
# == ng serve --proxy-config proxy.conf.js --configuration ai-develop
```

The `ai-develop` configuration (in `angular.json`):

- `liveReload: false` — browser stays put; reload manually (`Cmd/Ctrl-R`)
  when you want to see the latest.
- `hmr: false` — no HMR either.
- `poll: 3000` — file watcher polls every 3 s, so rapid bursts of saves
  coalesce into one rebuild.

The dev server still rebuilds in the background, but it won't push to the
browser. Same URL (`http://localhost:4200/app/`), same proxy.

## Dev workflow B — prod-parity via `ng build --watch`

Spring serves the SPA directly from the watched build output. Use this
to test against the *production* path (`:8051/app/`) — e.g. before
shipping, or to verify the `SpaResourceConfig` filesystem-handler is
correct.

```bash
# Terminal A — Angular rebuilds on save
cd rapla-angular
ng build --watch --configuration development

# Terminal B — Spring Boot serves /app/** from rapla-angular/dist/...
mvn -pl rapla-app -am spring-boot:run -Dspring-boot.run.fork=false
```

Open **http://localhost:8051/app/**. Manual browser refresh required
(no HMR), but the URL exactly matches prod.

## REST wire types

Each feature module owns the TypeScript shape of the endpoints it calls
(e.g. `reservations/table.types.ts` for `/api/table/*`). No code
generation: requests go through `HttpClient` directly with same-origin
URLs (`/api/...`). The server's record-component contract tests (e.g.
`TableViewServiceContractTest` in `rapla-core`) keep the wire shape
honest; if a server-side record adds or renames a field, those tests
fail loudly and the matching TS interface gets a manual update in the
same change.

The OpenAPI spec at `/api/v3/api-docs` (committed snapshots in
`rapla-app/src/main/resources/openapi/`) is still produced and served —
useful for SwaggerUI, external integrators who want to generate their
own client, and `OpenApiSpecCaptureTest` for drift detection. It just
isn't consumed by this SPA.

## Distribution build

```bash
mvn -pl rapla-app -am package
```

(The `frontend-maven-plugin` Maven wiring is **not yet done** — for now
the SPA must be manually built with `ng build` and copied into
`rapla-app/src/main/resources/static/app/` before `mvn package`, or use
dev workflow B which obviates the need.)

## Layout

| Path | Purpose |
|---|---|
| `src/app/auth/` | Login form, `AuthService`, JWT `HttpInterceptor`, route guard |
| `src/app/reservations/` | Read-only reservation list |
| `src/app/api/` | Auto-generated TypeScript-Angular client (gitignored) |
| `src/app/app.config.ts` | Bootstrap providers: `provideHttpClient` + `BASE_PATH=''` |
| `src/app/app.routes.ts` | `/login`, `/reservations` (guarded), `**` → login |
| `proxy.conf.js` | `ng serve` proxy: forwards REST + OAuth2 + legacy to `:8051` |

## URL space (post PRD 031)

| URL | Purpose |
|---|---|
| `/` and `/index` | Chooser landing page (HTML, plugin-extensible) |
| `/app/` | This SPA |
| `/api/auth/**`, `/api/storage/**`, etc. | REST endpoints |
| `/oauth2/**`, `/.well-known/**` | OAuth2 / OIDC (RFC paths) |
| `/raplaclient.jnlp`, `/webclient/**` | JNLP Swing launcher |
| `/rapla/calendar`, `/rapla/ical`, … | Legacy external URLs (preserved) |
| `/swagger-ui/**` | API explorer |
