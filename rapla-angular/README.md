# rapla-angular

Phase 0 prototype of the Angular reservation UI (PRD 026). Served same-origin
from the Spring Boot app at `http://localhost:8051/rapla/spa/`.

## Prerequisites

See PRD 026 §Phase 0 "What needs to be installed". Short version:

```bash
sudo apt-get install -y libatomic1
nvm use --lts                                          # Node 22+
npm install -g @angular/cli @openapitools/openapi-generator-cli
```

## First run

```bash
cd rapla-angular
npm install                  # once
```

## Dev workflow (two terminals)

Terminal A — keep the Angular bundle fresh on every edit:

```bash
cd rapla-angular
ng build --watch --configuration development
```

Output lands in `dist/rapla-angular/browser/`. Spring Boot's `SpaResourceConfig`
serves `/spa/**` from there directly (see `rapla.spa.dev-dir` property).

Terminal B — start the Spring Boot dev server (from the repo root, per
AGENTS.md §8):

```bash
mvn -pl rapla-app -am spring-boot:run -Dspring-boot.run.fork=false
```

Open `http://localhost:8051/rapla/spa/`, log in as `admin` with empty
password (dev DB default), and the reservation table loads.

## Regenerating the typed client

The TypeScript client under `src/app/api/` is generated from the live
server's `/v3/api-docs`:

```bash
npm run gen:api
```

Requires the server to be running. Generated files are gitignored —
rerun whenever the REST surface changes.

## Distribution build

Building the fat JAR with the SPA bundled inside is gated on a Maven
profile (not yet wired — see PRD 026 §Distribution path). Until then,
manually copy `dist/rapla-angular/browser/*` into
`rapla-app/src/main/resources/static/spa/` before running `mvn package`,
or use the dev workflow above.

## Layout

| Path | Purpose |
|---|---|
| `src/app/auth/` | Login form, `AuthService`, JWT `HttpInterceptor`, route guard |
| `src/app/reservations/` | Read-only reservation list |
| `src/app/api/` | Auto-generated TypeScript-Angular client (gitignored) |
| `src/app/app.config.ts` | Bootstrap providers: `provideHttpClient` + `BASE_PATH=/rapla` |
| `src/app/app.routes.ts` | `/login`, `/reservations` (guarded), `**` → login |

The `BASE_PATH` override at bootstrap re-routes all generated client
calls from the hard-coded `http://localhost:8051/rapla` default to
`/rapla` so the SPA works behind any host/port via same-origin
requests.
