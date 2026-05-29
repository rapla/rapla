# PRD 030 — Server-side view rendering (the complete picture)

**Status:** in-progress — Phases 1–6 landed 2026-05-12. **84 new tests.** Phase 6 deletes the back-edge as an arch-test invariant. Future migration of HTML autoexport calendar pages to `CalendarLayoutEngine` is a separate PRD.

**Update 2026-05-12: Phase 7–9 added** — Swing reservation/appointment table views migrate to `/table/*` endpoints. Triggered by a bug report: switching to the Swing reservation-table view throws `UnsupportedOperationException` at `CalendarModelImpl.requireSyncOperator` because the multimodule shift (commit `6b65470d`, 2026-05-08) moved sync methods to `SyncStorageOperator` (server-only). The bug exposed that the original "Swing keeps the in-process model" decision was factually wrong — the Swing client has always been a REST consumer via `RemoteOperator`. A three-method stopgap fix (PRD 008 split applied to `CalendarModelImpl.queryReservations`/`queryBlocks`/`queryAppointments` Promise wrappers, 2026-05-12) restores Swing immediately; Phases 7–9 retire the stopgap.

Phases:
- Phase 1: `TableViewEngine` + records + 26 tier-1 tests
- Phase 2: `TableViewService` (`/table/reservations`, `/table/appointments`) + `TableViewController` + 9 contract + 8 MockMvc tests
- Phase 3: `/table/config` + `/table/columns/catalog` + 5 contract + 7 MockMvc tests
- Phase 4: `BlockColors` helper (12 tier-1) + `BlockDecorator` interface + `RaplaBlockDecorator` + engine overload + `RaplaBlock.getColorsAsHex()` refactored to delegate + `CalendarViewController` wired to ship colors
- Phase 5: `CsvSerializer` (12 tier-1) + `/export/csv` route on `ExportController` + 6 MockMvc tests. ~~`ExportService` interface~~ **removed 2026-05-21 (PRD 049)** — zero Java consumers; `ExportController.csvDownload(...)` is the single source of truth (its `ResponseEntity<byte[]>` with `Content-Disposition` was always richer than the interface's `String` return).
- Phase 6: `LocalCache.cachedReservations` documented as Swing-legacy + `NoRaplaClientImportInServerTest` arch-test pinning the no-back-edge state + three stale `javax.swing.table.TableColumn` imports cleaned out of the table-view-server pages
**Author:** Christopher Kohlhaas (with AI assistance)
**Created:** 2026-05-12
**Related:** PRD 020 (server-driven admin panels — already-done piece), PRD 024 (server-side edit services, calendar layout done), PRD 026 (Angular frontend, primary consumer), PRD 028 (Angular power search), PRD 021 wont-fix (resource stubs, historical motivation), PRD 005 (multi-module split, D3 back-edge), PRD 008 (server-sync update events), PRD 009 (bulk storage REST, other thin-client foundation)

## The big idea

Move every read-side render decision — calendar layout, table-row projection, sidebar listings, CSV/HTML/iCal export — to the server. Client becomes a **thin viewer** that receives pre-projected records and paints; it does **not** hold the reservation graph or run layout strategies.

- Today: `/storage/reservations?from=…&to=…` ships full graphs. Client expands, runs `RaplaBuilder` + strategy, sorts, paints.
- Tomorrow: `/calendar/view`, `/table/reservations`, `/export/csv` ship **flat positioned/projected records** (~100 bytes per visible item). Server does layout/projection/sort once; client paints.

This is an **architecture-of-information project**. Done well:

- Cuts wire payload ~10× on common views.
- Eliminates the parallel TypeScript port of `RaplaBuilder` + table-sort logic Angular would otherwise need.
- Closes the rapla-server → rapla-client back-edge (PRD 005 D3) — the dependency exists today only because `RaplaBuilder` lives in `rapla-client`.
- Subsumes PRD 021's wont-fix motivation (clients can't hold N entities) and extends it to reservations and table projections.

## What's already done in this picture

| Surface | Status | Where |
|---|---|---|
| **Admin panel rendering** | **Done** — PRD 020 | Server publishes structured panel definitions; client renders. 5/11 vanilla + 4 dhbw panels migrated. |
| **External-event-import wizard** | **Done** — PRD 012 | Server-driven metadata + render. Pattern for plugin-contributed views. |
| **Calendar layout (read-side core)** | **Done** — PRD 024 Phase 3, 2026-05-11 | `CalendarLayoutEngine` (rapla-core) + `CalendarViewController` + 16 tier-1 + 7 MockMvc tests. Returns `CalendarPage` with positioned `RenderedBlock`s. Used by nobody yet. |
| **HTML export** | **Partial — server-side already** | Autoexport plugin server-renders HTML; re-implements parts of `RaplaBuilder`. Phase 5 harmonises. |
| **iCal export** | **Done — server-side already** | `org.rapla.plugin.export2ical`. No change. |
| **Bulk allocatable / reservation fetch** | **Done** — PRD 009 | Thin-client foundation. Serves full entities — this PRD adds projected-view endpoints alongside. |

## What's still on the client

After PRD 030 lands, client keeps: dynamic types + categories (small, static-ish, eager-load); the currently-edited entity graph (lazy via `/storage/reservation/{id}`); per-form transient state; recently-touched IDs (PRD 028); user identity + JWT.

Does **not** keep: full reservation graph for visible date range; full allocatable graph (PRD 028 server-side); locally-computed layout/sort/projection/color; row cache for tables.

## Concrete sizing — why this is worth doing

| Surface | Today (full entities) | With server rendering |
|---|---:|---:|
| Week calendar, 200 reservations | ~400 KB | ~25 KB |
| Month calendar, 800 reservations | ~1.5 MB | ~80 KB |
| Reservation table, semester (5 000 × 6 cols) | ~10 MB | ~500 KB |
| CSV export, semester | ~10 MB JSON → client formats | ~700 KB CSV streamed |
| DHBW admin sidebar (30 000 allocatables) | ~5 MB (PRD 021 wont-fix) | ~0 — PRD 028 server-driven |

Wire reduction is the headline; **memory** reduction on the client is just as important. A browser tab holding a megabyte JSON in JS heap costs you.

## Cross-PRD picture

PRD 030 is one of a constellation:

```
                     ┌─────────────────────────────────────────────┐
                     │  Angular thin client                         │
                     │  (PRD 026 — UX)                              │
                     └─────────────────────────────────────────────┘
                       │           │           │           │           │
                       │ search    │ calendar  │ table     │ export    │ edit
                       ▼           ▼           ▼           ▼           ▼
                   /search/*  /calendar/* /table/*    /export/*   /storage/
                                                                  /reservation/
                                                                  /{id}
                   ┌────┐    ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐
                   │PRD │    │PRD 024 │  │PRD 030 │  │PRD 030 │  │PRD 009 │
                   │028 │    │Phase 3 │  │ NEW    │  │ NEW    │  │+ PRD 024│
                   │    │    │ DONE   │  │        │  │        │  │ edit   │
                   └────┘    └────────┘  └────────┘  └────────┘  └────────┘
                       │           │           │           │           │
                       └───────────┴───────────┴───────────┴───────────┘
                                          ▼
                                   ┌─────────────────────┐
                                   │ rapla-core engines:  │
                                   │ CalendarLayoutEngine │  ◀── exists
                                   │ TableViewEngine       │  ◀── PRD 030 Phase 1
                                   │ NameSearchMatcher     │  ◀── exists
                                   │ AllocationConflictModel│  ◀── exists
                                   │ RepeatingRuleValidator│  ◀── exists
                                   │ BlockColors           │  ◀── PRD 030 Phase 4
                                   └─────────────────────┘
                                                ▲
                                                │
                                ┌────────────────┴────────────────┐
                                │  Swing client (in-process path) │
                                │  - calls engines directly        │
                                │  - keeps LocalCache for           │
                                │    its own use                    │
                                │  - never touches the REST views   │
                                └─────────────────────────────────┘
```

PRDs map to verticals: **028** → search; **024 P3** → calendar (done); **030** → table + CSV export + arch cleanup (this PRD); **009** → bulk read for edit hydration (done); **020** → admin panel structure (done).

PRD 030 specifically: builds table-view engine + endpoints (heaviest payload area), adds column-config endpoint, refactors block coloring as shared helper (closes duplication PRD 024 P3 deferred), migrates CSV export, does post-Angular architectural cleanup (LocalCache de-emphasis, D3 deletion).

## Goal

Land the remaining read-side render surfaces — table view, column config, CSV export — and finish the architectural moves the calendar phase started.

Net: Angular renders the full reservation-table flow without re-deriving column rules; DHBW-scale exports stop pulling megabytes of JSON; Swing keeps its in-process model unchanged; `RaplaBuilder` has one canonical consumer per render path; PRD 005 D3 back-edge becomes deletable.

## Scope

### In scope

| # | Surface | Endpoint | What it returns |
|---|---|---|---|
| 1 | Appointment table | `/table/appointments` (new) | `TablePage` with rows projected per column config |
| 2 | Reservation table | `/table/reservations` (new) | `TablePage` |
| 3 | Table column configuration per user | `/table/config` (new) | `TableColumnConfig[]` — id, label, visibility, sort default |
| 4 | CSV export | `/export/csv` (new) | streaming CSV body using the same engine |
| 5 | Block-color sharing | (in-process refactor — no new endpoint) | `BlockColors.resolve(...)` shared between `RaplaBlock` and (future) `RaplaBlockDecorator` |
| 6 | Architectural cleanup | (no endpoint — code-mod) | `LocalCache.cachedReservations` documented as Swing-legacy; PRD 005 D3 back-edge deletion via arch-test |
| 7 | Swing reservation-table | (consumer change, no new endpoint) | `ReservationTableViewFactory` + `SwingTableView<TableRow>` fetch from `/table/reservations`; lazy entity rehydrate for tooltip / edit / copy / cut |
| 8 | Swing appointment-table + per-day | (consumer change) | Same for `AppointmentTableViewFactory`, `AppointmentsPerDayViewFactory`, `CSVExportMenu → /export/csv` |
| 9 | `CalendarModelImpl` client-async cleanup | (code-mod) | Retire stopgap async fallbacks added 2026-05-12 once Phases 7+8 ship |

### Explicitly out of scope (separate PRDs)

- **Edit-time services** — PRD 024 Phases 1+2.
- **Power search / sidebar allocatable listing** — PRD 028.
- **Angular UI implementation** — PRD 026.
- **Real-time push.** Polling stays.
- ~~**GraphQL / aggregated queries.**~~ **Narrowed 2026-05-24 by [PRD 035 (done)](done/035-graphql-foundations.md).** Per-view shape principle still applies (no cross-view aggregation). GraphQL is now the SPA's transport for per-view reads: `renderedBlocks` is a thin wrapper over `CalendarLayoutEngine` + `CalendarViewController`. REST endpoints stay for direct callers (including Swing); CSV/iCal export stays REST permanently.
- ~~**Swing migration to REST.**~~ **REVERSED 2026-05-12.** Swing has always been a REST consumer via `RemoteOperator`; there was no in-process path for table views. Phases 7+8 migrate Swing table views to `/table/*`. Swing *calendar* views still keep `RaplaBuilder` pipeline.
- **Calendar surface itself** — already PRD 024 P3. Phase 4 (block-color sharing) is the only calendar-touching item here (sits at Swing/server seam).
- **Swing calendar views** (week/month/day/compactweek/dayresource/timeslot) — out of scope. Only **Swing table views** migrate.

## Plan

Each phase **independently shippable**, in this order to minimise risk:

### Phase 1 — `TableViewEngine` (rapla-core, ≈5 days)

Mirrors `CalendarLayoutEngine`. Pure-Java, no facade, no Swing.

- New package `org.rapla.plugin.tableview`.
- `TableViewEngine.project(Collection<Reservation>, TableColumnConfig[], SortSpec, PageSpec) → TablePage`.
- `TableRow` record: `String id`, `Map<String, Object> cells` (column id → scalar).
- Scalar cell values only — Strings, numbers, ISO date strings, booleans. No entity refs.
- **Cell extractor SPI**: `interface CellExtractor { Object extract(Reservation r, Appointment a); }` + registry keyed by column id. Plugins implement for custom columns (Exchange, ExternalEventImport).
- Sort: stable, multi-column comparator wiring.
- Pagination: cursor-based (opaque token).
- Tier-1 tests via `Proxy.newProxyInstance` stubs (same pattern as `CalendarLayoutEngineTest`).

### Phase 2 — Table REST surface (rapla-server, ≈3 days)

- `TableViewService` `@HttpExchange` in rapla-core. Two `@GetExchange`: `/table/appointments`, `/table/reservations`.
- Request: `from`, `to`, `columns[]`, `sort[]`, `cursor?`, `pageSize?`, `allocatables[]?`, `reservationTypes[]?`.
- Response: `TablePage`.
- `TableViewController` (rapla-server): parse → `facade.getReservations` → engine → return. Permission filter via facade (per PRD 024 P3).
- Contract test (rapla-core); MockMvc test (rapla-app) covering: requires-auth, happy path, pagination boundaries, AGENTS.md §12 leak probe (unknown ids silently dropped), permission filter.

### Phase 3 — Column config endpoint (≈3 days)

- `/table/config` GET → `TableColumnConfig[]`.
- Server reads from user prefs (`TableviewOption` — same as Swing).
- Plugin column contributions visible server-side; Angular needn't know the plugin.
- MockMvc: empty config → defaults, plugin column appears, per-user override wins.

Pattern: PRD 020.

### Phase 4 — Shared block colors (rapla-core, ≈2 days)

Refactor `RaplaBlock.getColorsAsHex()` to delegate to new `BlockColors.resolve(Reservation, List<Allocatable>, eventColoring, resourceColoring)` in rapla-core. Future `RaplaBlockDecorator` (in-flight piece reverted from this session) uses the same helper.

- New: `org.rapla.plugin.calendarview.BlockColors`.
- `RaplaBlock.getColorsAsHex()` becomes a 3-line delegate.
- Tier-1: event-color, resource-color, both, neither, fold-empty.
- Tier-2 (`FacadeTestSupport`): real entity classifications.

Natural completion of PRD 023 Phase 4.

### Phase 5 — CSV export (≈3 days, landed 2026-05-12; `ExportService` interface deleted 2026-05-21 per PRD 049)

- `/api/export/csv` GET → CSV with `Content-Disposition: attachment`.
- Reuses `TableViewEngine.project(...)` + CSV serializer.
- Headers from `TableColumnConfig.label` honour `Accept-Language`.
- Client menu becomes download link / browser GET.
- Tier-3 MockMvc: column header line, row count, comma-escaping, locale-aware dates.
- **`ExportService` interface removed 2026-05-21** — original Phase 5 introduced it as wire contract, but actual wire is `ExportController.csvDownload(...)` returning `ResponseEntity<byte[]>` with `Content-Disposition` (interface's `String csv()` was narrower). Zero callers in Java production code; SpringDoc reads controller annotations directly.
- Bonus: HTML autoexport plugin can switch to `CalendarLayoutEngine` + `TableViewEngine` instead of its bespoke loop. Deferrable.

### Phase 6 — Architectural cleanup (post-Angular, ≈3 days)

**Only land once Angular consumes the endpoints in production.** Until then, `LocalCache.cachedReservations` is load-bearing for Swing and back-edge is needed for Swing-side calendar render.

- Document `LocalCache.cachedReservations` as Swing-legacy.
- `CalendarSelectionModel.queryReservations(...)` keeps Swing role; mark not-for-new-callers.
- **rapla-server → rapla-client back-edge deletion** (PRD 005 D3): once all server flows route through rapla-core engines, `<dependency>rapla-client</dependency>` in `rapla-server/pom.xml` can go.
- Arch-test (`NoRaplaClientImportFromServerTest`) fails CI if anything in `rapla-server/src/main` imports `org.rapla.client.*`.

May span multiple sessions and waits on Angular's actual deployment.

### Phase 7 — Swing reservation-table → `/table/reservations` (rapla-client, ≈3-4 days)

Migrates `ReservationTableViewFactory` + `SwingTableView<Reservation>` to fetch from `/table/reservations` and render `TableRow` directly. Retires `model.queryReservations(...)` for the table-view path.

**Sub-phases:**

- **7.1 — `TableViewService` REST proxy bean.** **DONE 2026-05-12.** Added `@Bean tableViewServiceProxy` to `ClientProxyConfig`, wired via `HttpServiceProxyFactory`. Mirrors `iCalConfigServiceProxy` etc.
- **7.2 — `RaplaTableColumn<TableRow>` adapter.** New impl reading from `TableRow.cells` keyed by column id. Same `RaplaTableColumn<T>` interface, but `getValue(row, format)` looks up scalar by column key rather than projecting from entity.
- **7.3 — Lazy entity rehydration hook.** Tooltips, edit-dialog launch, copy/cut menu all need a live `Reservation`. View caches `Map<String, Reservation>`; on interaction, look up by `row.id()` — if absent, fire `GET /storage/reservation/{id}` and resolve. Single hit per row per session.
- **7.4 — Plugin summary extension adapter.** `ReservationSummaryExtension.init(table, panel)` currently reads `List<Reservation>` from `JTable` model. New shape: extension receives `RowSelectionProvider` with `List<String> getSelectedIds()` + `Promise<List<Reservation>> resolveSelected()`. Existing extensions get adapter classes.
- **7.5 — Factory rewire.** `ReservationTableViewFactory.createSwingView` builds `Supplier<Promise<List<TableRow>>>` from `tableViewService.reservations(...)`. Column ids from `tableConfigLoader.loadColumns(EVENTS_VIEW, user)`.
- **7.6 — Update column-id contract.** Server's `TableColumnDescriptor` keys must match `tableConfigLoader.loadColumns` output. Audit; align if drifted.

**Tests:** Tier-3 MockMvc smoke (extend Phase 2 tests as needed); tier-2 (rapla-client) headless harness for `SwingTableView<TableRow>` asserting row count, column rendering, tooltip lazy-fetch trigger.

### Phase 8 — Swing appointment-table + appointments-per-day → `/table/appointments` (rapla-client, ≈2 days)

Same migration applied to:
- `AppointmentTableViewFactory` (rows = `AppointmentBlock`).
- `AppointmentsPerDayViewFactory` (rows = `AppointmentBlock`, day grouping).
- `CSVExportMenu` — switch to `GET /export/csv` (already exists from Phase 5); browser download.

Depends on Phase 7's TableRow infrastructure. Mostly mechanical.

### Phase 9 — Retire `CalendarModelImpl` client-async fallbacks (rapla-core, ≈1 day)

Once Phases 7+8 ship and no client-side caller of `CalendarModelImpl.queryReservations` / `queryBlocks` / `queryAppointments` remains:

- Delete stopgap async-fallback branches added 2026-05-12. Remaining body is just the `SyncStorageOperator` server path.
- Optionally hoist the three Promise methods into `SyncCalendarModel` only — remove from client-facing `CalendarModel`.
- Audit `CopyDialog`, `CalendarTableViewPresenter` for stragglers — route through `/table/reservations` or `/storage/queryAppointments`.
- Update `MEMORY.md` / arch-test if any test pins the old async-fallback shape.

Net delete: ~30 LOC across `CalendarModelImpl`. The `instanceof SyncStorageOperator` check disappears from the client path.

## Architecture — pipeline shape

```
client                                            server
  │   GET /table/reservations?from=…&columns=…&sort=…
  │ ────────────────────────────────────────────▶
  │                                                JWT check + permission scope
  │                                                facade.getReservations(user, from, to, …)
  │                                                TableViewEngine.project(...)
  │                                                sort + paginate
  │ ◀────────────────────────────────────────────
  │   TablePage { columns: [id,label,…], rows: [{id, cells: {colId: scalar}}], nextCursor }
  │
  │   renderer paints (Angular table component, or Swing JTable post-Phase 7+8)
```

Mirrors PRD 024 P3 `/calendar/view`, same JWT, same permission gate, same cursor pagination. Both Angular and Swing (post-7+8) consume the same endpoint; differences are pagination shape (Angular paginates, Swing fetches all) and entity-rehydrate strategy (Angular never rehydrates, Swing lazy-fetches via `/storage/reservation/{id}`).

## Migration strategy — Swing and Angular both consume `/table/*`

**Revised 2026-05-12.** Original premise ("Swing keeps in-process model unchanged") was wrong — Swing has always been a REST consumer via `RemoteOperator`. So "not migrating Swing" was really "Swing keeps `/storage/queryAppointments` while Angular uses `/table/*`" — two REST paths for the same UX surface.

Revised: **both clients consume `/table/*` for table views**. One server-side projection pipeline, one wire shape. Net effects:

- PRD 008 split (server-sync / client-async on `CalendarModelImpl`) becomes irrelevant for the table-view path.
- Stopgap async fallback added 2026-05-12 becomes deletable in Phase 9.
- `LocalCache` keeps role for calendar views (still on in-Swing pipeline).

### Calendar views still aren't migrated

This PRD only migrates Swing **table views**. Swing **calendar views** (week/month/day/compactweek/dayresource/timeslot/appointments-per-day-grid) keep their existing `RaplaBuilder` pipeline. The Swing calendar uses local layout via `AbstractRaplaSwingCalendar` + `SwingRaplaBlock`. Migrating to consume `CalendarPage` (PRD 024 P3) requires a parallel set of view adapters; multi-week effort. Future PRD.

### Pagination — Swing keeps monolithic-scroll, opt-in for Angular

Both clients call `/table/*`, but UX differs:

- **Swing** — calls without `pageSize`, gets all rows (subject to 50 000-row server cap), renders in `JTable` with local scroll. `incomplete: true` + cursor triggers "narrow your date range" dialog.
- **Angular** — picks explicit pagination (`pageSize=N`, follow `nextCursor`) or virtual scrolling. Pagination recommended; virtual scroll as escape hatch.

Endpoint identical; client decides whether to use `pageSize`. ~50 KB/page Angular; ~1 MB at DHBW scale for Swing.

## Tests

| Phase | Tier | Where | Notes |
|---|---|---|---|
| 1 | 1 | `rapla-core/.../tableview/TableViewEngineTest` | Proxy stubs, no facade |
| 2 | 1 + 3 | rapla-core (contract) + rapla-app (MockMvc) | Cursor + AGENTS.md §12 leak probe |
| 3 | 3 | rapla-app MockMvc | Plugin column visibility |
| 4 | 1 + 2 | rapla-core + rapla-server (FacadeTestSupport) | Real entity color extraction |
| 5 | 3 | rapla-app MockMvc | Streaming CSV body, locale |
| 6 | arch | rapla-server | `NoRaplaClientImportFromServerTest` |
| 7 | 1 (rapla-core adapter) + 2 (rapla-client headless harness) | TableRow column adapter + SwingTableView rehydrate trigger | Row-count, column rendering, lazy fetch fires on tooltip |
| 8 | same as 7 | Appointment block + per-day flavours | Block-id stable key, day grouping |
| 9 | arch | rapla-core | `NoClientAsyncFallbackInCalendarModelTest` |

Reuse `PreferencesAdminControllerIntegrationTest` as MockMvc template — same Spring context, JWT setup, AGENTS.md §12 leak-probe pattern.

## Risks

### Engineering risks

1. **Per-render round-trip cost (TABLES specifically).** Calendar: no change (already wire-hit per render). Tables: today client has full graph in memory once fetched, so sort/page-down is instant. With server rendering, every sort/page = round-trip. **Mitigation:** Angular keeps one page in state after first fetch, re-sorts there for next 1–2 interactions; refetch only on date-range/filter change.

2. **No cache invalidation problem at v1.** No server-side rendered-page caching. Each request runs the engine; output isn't cached. Matches today's client behaviour (`cachingEnabled = false`). If perf later motivates, PRD 008's update-event flow is the natural invalidation source.

3. **Plugin SPI duality.** Existing table-view plugin defines columns via Swing-flavoured SPI (`JComponent` return). Parallel **value-oriented** SPI needed. **Mitigation:** default `ToValueAdapter` wrapping Swing extractors; document new SPI as recommended.

4. **CSV export determinism — non-risk if locale + columns are explicit request parameters.** Contract makes the response a function of the request: `/export/csv?from=…&to=…&columns=name,start,end,room&sort=start:asc&locale=de-DE`. No mitigation needed beyond API design. Documenting so future contributor doesn't reintroduce "smart defaults" on server side.

### Architectural risks

5. **Permission drift between Swing and Angular — not a structural risk; server-side rendering *reduces* drift.** Angular never computes permissions locally for server-rendered surfaces. Swing keeps local computation but calls the same `PermissionController.canRead(...)` in rapla-core. Only residual gap is eventual-consistency window — exists today regardless. Documenting so future readers don't reintroduce client-side permission filtering as a "perf optimisation".

6. **`LocalCache` removal risky, may be deferred indefinitely.** Phase 6 says "after Angular consumes". But Swing uses `LocalCache` for in-session navigation. **Acceptable end state:** keep for Swing; mark `cachedReservations` as Swing-legacy; never remove. Phase 6 reduces to back-edge deletion only.

7a. **Swing table-view migration: plugin extension breakage (Phase 7-8).** `ReservationSummaryExtension` is an extension point — third-party plugins (DHBW) implement it and consume `List<Reservation>`. Migrating `SwingTableView<T>` to `T = TableRow` breaks every existing impl. **Mitigation:** introduce `RowSelectionProvider` with `getSelectedIds()` + `Promise<List<Reservation>> resolveSelected()`; provide `ReservationSummaryAdapter` wrapping legacy API. ~1-day adapter work.

7b. **Swing table-view migration: lazy-rehydrate UX latency.** Tooltips, right-click, double-click-to-edit need a `Reservation`. Lazy fetch via `GET /storage/reservation/{id}` on first access. **Risk:** noticeable lag (50-300 ms). **Mitigation:** prefetch visible-row entities in background after initial render; cache in-memory. If lag remains, add bulk `POST /storage/reservations?ids=…` as follow-up.

7c. **Swing table-view migration: server-side sort vs. client-side `JTable` sort.** Every column-header click would be a new round-trip. **Mitigation:** for no-`pageSize` Swing case (all rows fetched), keep local `TableRowSorter` on rendered `TableRow` cells — no round-trip. Server `sort` only used for initial fetch order.

8. **HTML autoexport coupling — applies to *calendar-grid* HTML views only, not plain tables.** Autoexport renders two distinct families:
    - **Calendar-grid HTML pages** (`HTMLWeekViewPage`, `HTMLMonthViewPage`, `HTMLDayViewPage`, `HTMLCompactWeekViewPage`, `HTMLDayResourcePage`, two `timeslot` views, `AppointmentPerDayViewPage`) — these run through `HTMLRaplaBuilder` (subclass of `RaplaBuilder`) and `AbstractHTMLView`. PRD 030 builds `CalendarLayoutEngine` (Angular-bound) **alongside** that pipeline, not as replacement. Risk: rendering rule changes land in one pipeline but not the other. **Mitigation:** audit both call sites on rule changes. Future PRD can migrate HTML pages — pure code-deletion, ~1500 LOC. Not in scope because DHBW relies on autoexport for public schedule pages; risk-bearing for zero user-visible benefit.
    - **Plain HTML / CSV table pages** (`ReservationTableViewPage`, `AppointmentTableViewPage`) — these don't use `RaplaBuilder`. They walk `RaplaTableColumn<T>` plugin columns and emit `<table>` HTML. No coupling to calendar-grid pipeline. Only overlap with PRD 030's `TableViewEngine` is the column abstraction (Risk #3). Once Risk #3 mitigation lands (new `CellExtractor` SPI alongside `RaplaTableColumn`), one column-value source feeds both HTML and JSON outputs. **Not a risk for plain tables.**

## Open questions

1. **Table column config — server-owned or client-owned? RESOLVED 2026-05-12: server-owned** (PRD 020 pattern). Backed by user `Preferences`. Angular fetches `/table/config` once at boot. Plugin column catalog (universe of available column ids) is sibling `/table/columns/catalog`.

2. **CSV as separate endpoint or content negotiation? RESOLVED 2026-05-12: separate `/export/csv`.** Cleaner contract, easier streaming.

3. **Pagination strategy. RESOLVED 2026-05-12.** Cursor-based, opt-in via `pageSize`. Omitting returns all rows (Swing default). Server caps no-`pageSize` response (default 50 000) with `incomplete: true` + cursor when hit. Response includes `totalCount` regardless.

4. **Plugin extractor SPI. RESOLVED 2026-05-12: parallel value-oriented SPI** (`CellExtractor<T>`) alongside Swing-flavoured `RaplaTableColumn<T>`. No behaviour change for Swing.

5. **Authoritative export vs. live view. RESOLVED 2026-05-12: snapshot at request time T.** Same engine + same query params = same bytes out.

6. **Calendar `period` overlay. RESOLVED 2026-05-12: separate `/periods` endpoint** for now. Revisit if round-trip count proves a UX problem.

7. **Cell type info on columns. RESOLVED 2026-05-12: type descriptor on `TableColumnDescriptor`, scalar values on rows.** Angular renders consistently without server-side pre-formatting. CSV export adds thin formatter on top.

8. **`LocalCache` permanent retention. RESOLVED 2026-05-12: retain indefinitely for Swing.** Document `cachedReservations` as Swing-legacy; never remove. Phase 6 reduces to back-edge deletion only.

9. **Phase ordering vs Angular v1. RESOLVED 2026-05-12.** Angular v1 (PRD 026) is reservation **edit**; Phases 1–3 not on critical path. Schedule: Phase 1+2 in parallel with PRD 024 P1+2 (Angular v1 prep); Phases 3+4+5 during v1.1; Phase 6 only after Angular ships and stabilises.

10. **Lazy vs eager entity rehydration (Phase 7.3).** Options:
    - **Lazy on first touch** (recommended): zero up-front cost, 50-300 ms latency on first interaction. Matches "you only edit a few rows per session."
    - **Eager prefetch in background**: hides latency, doubles wire traffic on first load.
    - **Bulk endpoint** `POST /storage/reservations?ids=…`: one round-trip for prefetch. Add only if needed.

    Decision: lazy by default; measure in 7.3; add prefetch/bulk if motivated.

11. **`RaplaTableColumn<TableRow>` adapter location (Phase 7.2).** rapla-core (alongside `CellExtractor`) vs rapla-client (next to `RaplaSwingTableColumnImpl`). Decision: rapla-core for value-lookup adapter (`TableRowColumn implements RaplaTableColumn<TableRow>`); thin Swing wrapper in rapla-client only if needed for `init(TableColumn)`. **Pending 7.2.**

12. **`ReservationSummaryExtension` adapter shape (Phase 7.4).** Adapter wraps `RowSelectionProvider` to look like today's `List<Reservation>` consumer (lower friction) vs new `RowSelectionProvider` as primary + deprecation marker on legacy (cleaner long term, more churn). Decision: adapter route initially; deprecation pass + cleanup in follow-up PRD. **Pending 7.4.**

13. **Where does the rehydration cache live? (Phase 7.3)** View-scope (invalidates on close, simple) vs session-scope (survives view switches, coupled to `LocalCache` invalidation). Decision: view-scope initially. **Pending 7.3.**

## Cross-references

- **PRD 020** — server-driven admin panels. Pattern template.
- **PRD 024** — server-side edit services (sibling, edit-side). P3's `/calendar/view` is the prior art.
- **PRD 026** — Angular frontend. Primary consumer.
- **PRD 028** — Angular power search. Parallel PRD on allocatable surface.
- **PRD 021 wont-fix** — client resource stubs. Motivation subsumed.
- **PRD 005** — multi-module split. Phase 6 deletes D3 back-edge.
- **PRD 008** — server-sync update events. Phase 2's cache-invalidation rides on this.
- **PRD 009** — bulk storage REST. Edit-side companion.
- **PRD 023** — pure-Java carve-outs. Phase 4 (`BlockColors`) is natural completion of 023 P4.
