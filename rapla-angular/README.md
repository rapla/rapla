# rapla-angular

Angular SPA for rapla. Served at `/app/` in both dev (via `ng serve`
proxy) and prod (via Spring Boot static handler).

## Quick start

```bash
# 1) one-time install + generate the typed API client
cd rapla-angular && npm install && npm run gen:api

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
| `npm run gen:api` | Generate `src/app/api/` from the committed OpenAPI spec (`rapla-app/src/main/resources/openapi/client.json`) | One-time setup + after server REST changes |

## Prerequisites (once-off)

See PRD 026 §Phase 0 "What needs to be installed". Short version:

```bash
sudo apt-get install -y libatomic1
nvm use --lts                                          # Node 22+
npm install -g @angular/cli @openapitools/openapi-generator-cli
```

## First run

```bash
cd rapla-angular
npm install
npm run gen:api   # generates src/app/api/ — the build won't compile without it
```

`src/app/api/` is gitignored and **not** part of a fresh checkout, but
several components import from it (`app.config.ts`, the reservations
component, …). A fresh clone must run `npm run gen:api` once before the
first `npm start` / `npm run build`.

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

## Regenerating the typed client

The TypeScript client under `src/app/api/` is generated from the
committed OpenAPI spec `rapla-app/src/main/resources/openapi/client.json`
(no running server needed):

```bash
npm run gen:api
```

Generated files are gitignored, so `src/app/api/` is absent from a fresh
checkout — run `gen:api` once during setup, then again whenever the REST
surface changes (controller added, request/response DTO changed, etc.).
The `client.json` spec itself is committed and refreshed server-side; if
the server's REST surface changed, regenerate `client.json` first, then
run `gen:api`.

`BASE_PATH` is set to `''` (empty) in `app.config.ts` because SpringDoc
emits absolute paths that already include the `/api` prefix — adding
it again at the client would double-prefix.

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
