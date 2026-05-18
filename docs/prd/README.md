# PRD index

48 PRDs total — **31 active** in this directory, **17 done** under `done/`. AGENTS.md §2 + §3 cover the lifecycle (move to `done/` when complete; `git mv` back if reopening). This index exists so agents and humans don't `ls` and guess.

> **Numbering note:** there are two `031` files (`031-api-namespace-redesign.md` and `031-token-refresh-and-api-keys.md`). Both are in-progress and on different tracks. Future PRDs should pick the next free number (currently 042) rather than reusing.

## Active — in-progress

### Auth / OAuth / IdP

| File | Title | Status |
|---|---|---|
| [029-swing-oauth-login.md](029-swing-oauth-login.md) | Swing Login via OAuth 2.0 (Browser-based, PKCE Loopback) | phase 1 done 2026-05-12; phase 2 mostly landed |
| [031-token-refresh-and-api-keys.md](031-token-refresh-and-api-keys.md) | Refresh Tokens & API Keys — IdP-portable design | refresh-token half shipped 2026-05-12 |
| [036-external-idp-oauth-login.md](036-external-idp-oauth-login.md) | External IdP OAuth 2.0 Login (Microsoft Entra ID + Google) | draft 2026-05-14 |
| [037-native-saml-shibboleth.md](037-native-saml-shibboleth.md) | Shibboleth via Reverse-Proxy Trusted Headers | draft 2026-05-15 |

### REST API / wire format

| File | Title | Status |
|---|---|---|
| [009-server-bulk-storage-rest-api.md](009-server-bulk-storage-rest-api.md) | Server-side bulk-storage REST API (port `RemoteStorage` to Spring controllers) | Phases 0–4 implemented; Phase 5 error-handling in progress |
| [031-api-namespace-redesign.md](031-api-namespace-redesign.md) | API namespace redesign | Phases 1+2+3+4 landed 2026-05-12 |
| [041-openapi-runtime-removal.md](041-openapi-runtime-removal.md) | OpenAPI — build-time generation + runtime plugin filtering (SpringDoc out of the runtime) | draft 2026-05-15 |
| [043-api-keys-jwt-pat.md](043-api-keys-jwt-pat.md) | API Keys — GitHub-PAT flow, server-minted asymmetric JWT (supersedes PRD 031 §"API key surface") | draft 2026-05-16 |

### Server-side rendering / models / views

| File | Title | Status |
|---|---|---|
| [020-server-driven-admin-panels.md](020-server-driven-admin-panels.md) | Server-Driven Admin / Preferences Panels | foundation done; 5/11 vanilla + 4 dhbw panels landed |
| [023-presenter-view-extraction.md](023-presenter-view-extraction.md) | Presenter / Model carve-out from Swing components | Phases 1, 2, 3 (model + refactor + tier-1 tests) landed |
| [024-server-side-edit-services.md](024-server-side-edit-services.md) | Server-side edit services (Angular precursor) | in-progress |
| [030-server-side-view-rendering.md](030-server-side-view-rendering.md) | Server-side view rendering (the complete picture) | Phases 1–6 landed 2026-05-12; 84 new tests |

### Frontend / SPA

| File | Title | Status |
|---|---|---|
| [026-angular-frontend.md](026-angular-frontend.md) | Angular frontend (reservation editing) | Phase 0 prototype landed 2026-05-12 |
| [047-angular-frontend-plugin-model.md](047-angular-frontend-plugin-model.md) | Angular frontend plugin model (Native Federation remotes) | draft 2026-05-18 |

### External integrations / sync

| File | Title | Status |
|---|---|---|
| [035-rapla-mcp-server.md](035-rapla-mcp-server.md) | External integration API + MCP server (GraphQL + MCP transports) | draft 2026-05-13; scope broadened 2026-05-15 |
| [038-graph-calendar-sync.md](038-graph-calendar-sync.md) | Microsoft Graph calendar sync (Exchange Online / M365) — write-only push | draft 2026-05-15 |
| [039-external-ical-subscription-per-resource.md](039-external-ical-subscription-per-resource.md) | Per-resource external iCal subscriptions for conflict awareness | draft 2026-05-15 |

### Testing / quality

| File | Title | Status |
|---|---|---|
| [007-build-and-test-performance.md](007-build-and-test-performance.md) | Build & Test Performance | Phase 0 + Phase 1 landed |
| [017-test-coverage-strategy.md](017-test-coverage-strategy.md) | Test Coverage Strategy | Phases 1–4 done 2026-05-10; Phase 5 in progress |
| [034-ci-baseline-workflow.md](034-ci-baseline-workflow.md) | CI baseline workflow (GitHub Actions for tiers 1–2 + Angular build) | draft 2026-05-13 |

### Architecture / deployments

| File | Title | Status |
|---|---|---|
| [003-custom-deployments-after-spring-migration.md](003-custom-deployments-after-spring-migration.md) | Custom Deployment Model After Spring Migration | major direction change 2026-05-07; rapla-side carve-out in progress |
| [012-dhbwrapla-client-migration.md](012-dhbwrapla-client-migration.md) | Migrate dhbwrapla client-side plugin code to server pages / general rapla | rapla-side carve-out fully landed 2026-05-10 |
| [022-architecture-documentation.md](022-architecture-documentation.md) | Architecture reference documentation | in-progress |
| [040-dispatch-validate-before-lock.md](040-dispatch-validate-before-lock.md) | Dispatch validates against a stale cache before locking (multi-pod) | draft 2026-05-15 |
| [045-end-user-deployment-and-db-config.md](045-end-user-deployment-and-db-config.md) | End-user deployment model & database configuration | in-progress — Phases 1+2+3 landed 2026-05-18; OQ5 packaging deferred |
| [046-drop-in-plugin-model.md](046-drop-in-plugin-model.md) | Drop-in server plugin / extension model (`./plugins/` autoconfig jars) | draft 2026-05-18 |
| [048-eliminate-server-container-context.md](048-eliminate-server-container-context.md) | Eliminate `ServerContainerContext` + implement reload-on-restart | design complete 2026-05-18; ready to implement |

### Decisions / policies (no implementation phase)

| File | Title | Status |
|---|---|---|
| [027-mock-framework-policy.md](027-mock-framework-policy.md) | Mock-framework policy for rapla tests | decision adopted 2026-05-11 |

## Active — draft / research only

| File | Title | Notes |
|---|---|---|
| [002-multi-tenancy.md](002-multi-tenancy.md) | Multi-Tenancy Support | future / speculative |
| [018-fat-jar-classloader-defect.md](018-fat-jar-classloader-defect.md) | Spring Boot 4.0.6 Fat-JAR Classloader Defect — Extract-and-Run Workaround | workaround documented; upstream fix tracked |
| [025-headless-client-test-harness.md](025-headless-client-test-harness.md) | Headless client test harness | scoping |
| [028-angular-power-search.md](028-angular-power-search.md) | Angular power search (single-calendar shell) | research / scoping only |
| [032-angular-ui-library-evaluation.md](032-angular-ui-library-evaluation.md) | Angular calendar view + UI component library | research 2026-05-12 |

## Done (17 PRDs)

### Migration / modernization

| File | Title | Done |
|---|---|---|
| [done/001-spring-boot-migration.md](done/001-spring-boot-migration.md) | Spring Boot Migration | Phases 1–9 complete 2026-05-08 |
| [done/002-swing-spring-di.md](done/002-swing-spring-di.md) | Swing UI Spring DI Migration | Phases A–F complete 2026-05-08 |
| [done/011-spring-boot-4-jackson-3.md](done/011-spring-boot-4-jackson-3.md) | Upgrade to Spring Boot 4 + Jackson 3 | Phases 1–5 landed 2026-05-08; Phase 6 2026-05-11 |
| [done/019-spring-boot-lifecycle-migration.md](done/019-spring-boot-lifecycle-migration.md) | Spring Boot Lifecycle Migration — Replacing `ServerExtension` | Phases 1–4 landed 2026-05-10 |

### Date → LocalDateTime migration

| File | Title | Done |
|---|---|---|
| [done/001-a-date-to-localdatetime.md](done/001-a-date-to-localdatetime.md) | Replace java.util.Date with java.time.LocalDateTime | All phases A1–A9 landed 2026-05-11 |
| [done/013-date-script-collateral-damage.md](done/013-date-script-collateral-damage.md) | Recover collateral damage from Date migration scripts | 2026-05-11 (~1 068 LOC recovered) |
| [done/014-appointment-long-to-java-time.md](done/014-appointment-long-to-java-time.md) | Replace long-millis date arithmetic with java.time API | Phases 1–9 landed 2026-05-11 |
| [done/015-finish-date-migration-rapla-client.md](done/015-finish-date-migration-rapla-client.md) | Finish Date → LocalDateTime migration in rapla-client | 2026-05-11 (DoD met) |
| [done/016-pre-checkin-deletion-audit.md](done/016-pre-checkin-deletion-audit.md) | Pre-check-in audit of `git diff HEAD` after Date migration | 2026-05-11 (6 follow-up commits) |

### Multi-module split

| File | Title | Done |
|---|---|---|
| [done/004-multi-module-architecture-analysis.md](done/004-multi-module-architecture-analysis.md) | Multi-Module Architecture Analysis | decided → PRD 005 |
| [done/005-baseline.md](done/005-baseline.md) | Baseline (pre-split) | reference snapshot |
| [done/005-cycle-audit.md](done/005-cycle-audit.md) | Cycle Audit (Phase B3 output) | reference snapshot |
| [done/005-multi-module-split.md](done/005-multi-module-split.md) | Multi-Module Split (Implementation) | Phases A–F complete 2026-05-07 |

### Architecture / wire format

| File | Title | Done |
|---|---|---|
| [done/008-server-sync-client-async-facade-split.md](done/008-server-sync-client-async-facade-split.md) | Confine rxjava to rapla-client | Phases 0–4 shipped 2026-05-07 |
| [done/008-server.md](done/008-server.md) | Server Cleanup — superseded by 008-server-sync-client-async-facade-split | reference only |
| [done/010-jackson-field-based-wire-format.md](done/010-jackson-field-based-wire-format.md) | Jackson field-based JSON wire format (shared client + server) | 2026-05-08 |

### Tooling

| File | Title | Done |
|---|---|---|
| [done/033-playwright-mcp-browser-testing.md](done/033-playwright-mcp-browser-testing.md) | Playwright MCP for browser-driven SPA testing | 2026-05-13 — MCP server installed; usage in `angular-frontend` skill, install in `docs/development.md` |

## How to add a PRD

See AGENTS.md §2 (PRD-Driven Development) and §3 (PRD Format). Pick the next free number (currently **048**). File naming: `docs/prd/NNN-short-name.md`. Required sections: Title, Status, Goal, Scope, Plan, Tests, Open Questions.

When a PRD finishes, `git mv` it to `docs/prd/done/` and update this index in the same change.
