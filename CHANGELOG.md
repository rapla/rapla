# Changelog

All notable changes on the `spring-boot` branch since divergence from `master`.
Sections are organized by the PRD that drove each change — see `docs/prd/done/`
for the full design notes and decisions, `docs/prd/` for work still in flight.

## Unreleased — `spring-boot` branch

Diverged from `master` at `fb64da61` (`add AGENTS.md, CLAUDE.md and .claude
-> .agents symlink for AI agent config`). 64 commits, ~2,600 files touched.

### Architecture

- **PRD 001 — Spring Boot migration.** Replaced embedded Jetty + custom
  `SimpleRaplaInjector` + RESTEasy + compile-time `restinject` proxy
  generation with Spring Boot (embedded Tomcat), Spring DI, and
  `HttpServiceProxyFactory` typed REST clients. RxJava3 retained on the
  client only. Eliminates the `restinject` annotation processor and
  `META-INF/services` wiring.
- **PRD 002 — Swing UI Spring DI migration.** Every wired class now uses
  `@Service` / `@Component` / `@Configuration` + constructor injection.
  The legacy `org.rapla.inject.*` package and `jakarta.inject-api` are
  removed from the dependency tree.
- **PRD 005 — Multi-module split.** Single-module Maven build replaced
  with a 5-module reactor: `rapla-bom`, `rapla-core`, `rapla-client`,
  `rapla-server`, `rapla-app`. Build-time enforcement of layer
  boundaries; smaller surface for downstream consumers (`dhbwrapla`,
  custom deployments); reactor aggregator at the repo root.
- **PRD 008 — RxJava confined to `rapla-client`.** `rapla-core` and
  `rapla-server` have zero RxJava on the classpath. Server-side storage
  calls converted from `SynchronizedCompletablePromise.waitFor(...)`
  blocking to direct synchronous calls via `SyncStorageOperator`.
- **PRD 019 — Spring Boot lifecycle migration.** Bespoke
  `ServerExtension start/stop` driven by `ServerServiceImpl` replaced
  with Spring-native lifecycle: `@Scheduled`,
  `@EventListener(ApplicationReadyEvent)`, `@PostConstruct` /
  `@PreDestroy`, `SmartLifecycle` for the rare phased cases.

### Wire format & data model

- **PRD 010 — Jackson field-based JSON wire format.** Single shared
  Jackson configuration (field introspection + `transient`-honoring +
  JSR-310) between Spring server and Swing client, replacing Gson.
  Locks down the configuration that survives Rapla's bidirectional
  entity graph (`ReferenceHandler.links` + `transient EntityResolver`)
  without the `StackOverflowError` / `InaccessibleObjectException`
  failure modes that bit PRD 009 mid-implementation.
- **PRD 011 — Spring Boot 4 + Jackson 3 upgrade.** Reactor moved from
  Spring Boot 3.2 / Jackson 2 (`com.fasterxml.jackson.*`) to Spring
  Boot 4 / Jackson 3 (`tools.jackson.*`). Combined with PRD 010 because
  both touched the same `JacksonObjectMapperFactory` plumbing.
- **PRD 001-A — `java.util.Date` → `java.time.LocalDateTime`.** Entity
  layer flipped wholesale; mechanical migration via audited per-file
  batches (zero-flag quick wins, then risk-tier 2.5–3.0 files).
- **PRD 014 — Appointment arithmetic on `java.time`.** Removed the
  carried-forward `LocalDateTime → long-millis → LocalDateTime` idiom
  in appointment / repeating / permission code. Native `Duration` /
  `Period` arithmetic throughout.
- **PRD 015 — Finish Date migration in `rapla-client`.** Drove
  `rapla-client` to zero compile errors. `DateField` → `LocalDate`,
  `TimeField` → `LocalTime`; `RaplaCalendar` / `RaplaTime` keep
  `LocalDateTime` public API and convert at the boundary so the 18
  listener consumers stayed put.
- **PRD 013 — Date-script collateral-damage recovery.** Catalogued and
  restored the ~1,068 lines across 148 files that the Date migration
  scripts incorrectly stripped (regex matched code-statement lines as
  method declarations). Baseline `HEAD` (post-multimodule), not
  `origin/master`.
- **PRD 016 — Pre-check-in deletion audit.** Added the audit pass over
  `git diff HEAD` after the Date migration to catch unintended
  deletions before commit. `.audit/` gitignored.

### Removed

- **GWT module and all GWT support code.** The Angular SPA (PRD 026,
  in progress) replaces it.
- **`restinject` annotation processor** and generated `_JavaJsonProxy`
  classes — superseded by Spring's `HttpServiceProxyFactory` typed
  proxies (PRD 001).
- **`org.rapla.inject` and `jakarta.inject-api`** — superseded by
  Spring stereotypes (PRD 002).

### Tooling & process

- **PRD 027 — Mock-framework policy.** Decision: no mocks of internal
  rapla types (facade, operator, cache, permission). Use real
  `FacadeImpl` via `FacadeTestSupport` at tier 2; Mockito allowed for
  Servlet-API types and external integrations behind narrow interfaces.
- **PRD 033 — Playwright MCP browser testing.** `@playwright/mcp`
  registered as a Claude Code MCP server so an AI agent can drive a
  real browser against the SPA — navigate, walk the OAuth2 + PKCE
  flow, inspect DOM / network / storage, take screenshots. Replaces
  the "agent describes flow → user opens browser → pastes errors back"
  loop.
- **PRD 004 — Multi-module architecture analysis (decided).**
  Settled the layer boundaries PRD 005 implemented.

### In progress (not landing in this set)

Active PRDs whose work is visible on the branch but not complete —
see `docs/prd/` for status detail:

- **PRD 003** — Custom deployment model after Spring migration.
- **PRD 007** — Build & test performance.
- **PRD 009** — Server-side bulk-storage REST API (port `RemoteStorage`
  to Spring controllers).
- **PRD 012** — Migrate `dhbwrapla` client-side plugin to server pages
  / general rapla.
- **PRD 017** — Test coverage strategy (phased rollout).
- **PRD 020** — Server-driven admin / preferences panels.
- **PRD 022** — Architecture reference documentation.
- **PRD 023** — Presenter / model carve-out from Swing components.
- **PRD 024** — Server-side edit services (Angular precursor).
- **PRD 026** — Angular frontend (reservation editing). Prototype
  ships on this branch (reservations list, OAuth2 + PKCE login, sign
  out via OIDC RP-initiated logout against Spring Authorization
  Server) — full editing surface still in progress.
- **PRD 029** — Swing login via OAuth 2.0 browser-based PKCE loopback.
- **PRD 030** — Server-side view rendering.
- **PRD 031** — API namespace redesign (`/api/`, `/app/`,
  `/rapla/{calendar,ical,…}`) and token refresh / API keys.
