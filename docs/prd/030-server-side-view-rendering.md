# PRD 030 — Server-side view rendering (the complete picture)

**Status:** in-progress — Phases 1–6 landed 2026-05-12. **84 new tests.** Phase 6 deletes the back-edge as an arch-test invariant (the pom.xml dependency was already gone; this commit pins it via `NoRaplaClientImportInServerTest`). Future migration of HTML autoexport calendar pages to `CalendarLayoutEngine` is a separate PRD.

**Update 2026-05-12: Phase 7–9 added** — Swing reservation/appointment table views migrate to `/table/*` endpoints. Triggered by a bug report: switching to the Swing reservation-table view throws `UnsupportedOperationException` at `CalendarModelImpl.requireSyncOperator` because the multimodule shift (commit `6b65470d`, 2026-05-08) moved sync methods to `SyncStorageOperator` (server-only). The bug exposed that the original "Swing keeps the in-process model" decision was factually wrong — the Swing client has always been a REST consumer via `RemoteOperator`. A three-method stopgap fix (PRD 008 split applied to `CalendarModelImpl.queryReservations` / `queryBlocks` / `queryAppointments` Promise wrappers, 2026-05-12) restores the Swing table view immediately; Phases 7–9 retire the stopgap by routing Swing's table fetches through `/table/*`, then deleting the now-dead `CalendarModelImpl` async fallbacks.

Phases:
- Phase 1: `TableViewEngine` + records + 26 tier-1 tests
- Phase 2: `TableViewService` (`/table/reservations`, `/table/appointments`) + `TableViewController` + 9 contract + 8 MockMvc tests
- Phase 3: `/table/config` + `/table/columns/catalog` + 5 contract + 7 MockMvc tests
- Phase 4: `BlockColors` helper (12 tier-1) + `BlockDecorator` interface + `RaplaBlockDecorator` + engine overload + `RaplaBlock.getColorsAsHex()` refactored to delegate + `CalendarViewController` wired to ship colors
- Phase 5: `CsvSerializer` (12 tier-1) + `ExportService` (`/export/csv`) + `ExportController` + 4 contract + 6 MockMvc tests
- Phase 6: `LocalCache.cachedReservations` documented as Swing-legacy + `NoRaplaClientImportInServerTest` arch-test pinning the no-back-edge state + three stale `javax.swing.table.TableColumn` imports cleaned out of the table-view-server pages
**Author:** Christopher Kohlhaas (with AI assistance)
**Created:** 2026-05-12
**Related:** PRD 020 (server-driven admin panels — already-done piece of this picture), PRD 024 (server-side edit services, calendar layout already done), PRD 026 (Angular frontend, primary consumer), PRD 028 (Angular power search, sidebar replacement), PRD 021 wont-fix (resource stubs, historical motivation), PRD 005 (multi-module split, D3 back-edge), PRD 008 (server-sync update events, invalidation rides on this), PRD 009 (bulk storage REST, the other thin-client foundation)

## The big idea

Move every read-side render decision — calendar layout, table-row projection, sidebar listings, CSV/HTML/iCal export — to the server. The client becomes a **thin viewer** that receives pre-projected records and paints them; it does **not** hold the reservation graph or run the layout strategies.

Concretely:
- Today: `/storage/reservations?from=…&to=…` ships full `Reservation` + `Appointment` + `Allocatable` + `Classification` + `Permission` graphs. Client expands appointments, runs `RaplaBuilder`, runs a layout strategy, sorts table rows, paints.
- Tomorrow: `/calendar/view`, `/table/reservations`, `/export/csv` ship **flat positioned/projected records** (~100 bytes per visible item). Server does the layout / projection / sort once. Client only paints.

This isn't a Swing-replacement project; it's an **architecture-of-information project**. Done well, it:

- Cuts wire payload by ~10× on the common-case views.
- Eliminates the parallel TypeScript port of `RaplaBuilder` and the table-sort logic that Angular would otherwise need.
- Closes the rapla-server → rapla-client back-edge (PRD 005 D3) — the dependency exists today only because `RaplaBuilder` lives in `rapla-client`.
- Subsumes PRD 021's wont-fix motivation (clients can't hold N entities) — and extends the same answer to reservations and table projections.

## What's already done in this picture

The shift isn't theoretical — three pieces already ship in this direction. PRD 030 is about completing the picture, not starting it.

| Surface | Status | Where |
|---|---|---|
| **Admin panel rendering** | **Done** — PRD 020 | Server publishes structured panel definitions; client renders. 5/11 vanilla + 4 dhbw panels migrated. |
| **External-event-import wizard** | **Done** — PRD 012 | Server-driven metadata + render. Pattern for plugin-contributed views. |
| **Calendar layout (read-side core)** | **Done** — PRD 024 Phase 3, 2026-05-11 | `CalendarLayoutEngine` (rapla-core) + `CalendarViewController` (rapla-server) + 16 tier-1 + 7 MockMvc tests. Returns `CalendarPage` with positioned `RenderedBlock`s. **Used by nobody yet** — Swing keeps the in-process path; Angular hasn't shipped. |
| **HTML export** | **Partial — server-side already** | The autoexport plugin server-renders HTML; today it re-implements parts of `RaplaBuilder`. Phase 5 here harmonises it. |
| **iCal export** | **Done — server-side already** | `org.rapla.plugin.export2ical`. No change. |
| **Bulk allocatable / reservation fetch** | **Done** — PRD 009 | The thin-client foundation. Today serves full entities — this PRD doesn't change that endpoint; it adds projected-view endpoints alongside. |

## What's still on the client

Even after PRD 030 lands, the client keeps:

- **Dynamic types + categories.** Small, static-ish. Eager load at boot for forms / dropdowns. Already JSON-only via PRD 009.
- **The currently-edited entity graph.** Lazy-fetched via `/storage/reservation/{id}` when a user opens an edit dialog.
- **Per-form transient state.** Form values, dirty flag, undo stack scoped to the dialog.
- **Recently-touched IDs** (per PRD 028 recency model) — tiny, in browser storage.
- **The user identity + JWT** for the session.

It does **not** keep:

- The full reservation graph for the visible date range.
- The full allocatable graph (PRD 028 handles allocatable listings server-side; see "Cross-PRD picture" below).
- Locally-computed layout, sort, projection, color rules.
- A row cache for tables.

## Concrete sizing — why this is worth doing

Numbers are order-of-magnitude estimates, gzipped wire size on a typical request:

| Surface | Today (full entities) | With server rendering |
|---|---:|---:|
| Week calendar, 200 reservations visible | ~400 KB | ~25 KB |
| Month calendar, 800 reservations visible | ~1.5 MB | ~80 KB |
| Reservation table, semester (5 000 reservations × 6 columns) | ~10 MB | ~500 KB |
| CSV export, semester | ~10 MB JSON → client formats | ~700 KB CSV streamed |
| DHBW admin sidebar (30 000 allocatables) | ~5 MB (PRD 021 wont-fix motivation) | ~0 — PRD 028 server-driven |

The wire reduction is the headline; the **memory** reduction on the client is just as important. A browser tab holding a megabyte JSON in JS heap costs you. A row payload of `~100 bytes × what's on screen` keeps the client small.

## Cross-PRD picture (what makes the "complete" view)

PRD 030 is **one** PRD in a constellation. The complete server-side-rendering picture is:

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

The PRDs map to verticals:
- **PRD 028** → search surface (allocatables + reservations + conflicts ranked into a single list)
- **PRD 024 Phase 3** → calendar surface (already done)
- **PRD 030** → table surface + CSV export + arch cleanup (this PRD)
- **PRD 009** → bulk read for edit hydration (already done)
- **PRD 020** → admin panel structure (already done for the surfaces it covers)

Each is independently shippable. PRD 030 specifically:
1. Builds the table-view engine + endpoints (the heaviest payload area).
2. Adds the column-config endpoint (Angular needs to know what columns to render).
3. Refactors block coloring as a shared helper (closes the duplication PRD 024 Phase 3 deferred).
4. Migrates CSV export.
5. Does the post-Angular architectural cleanup (LocalCache de-emphasis, D3 back-edge deletion).

## Goal

Land the remaining read-side render surfaces — table view, column config, CSV export — and finish the architectural moves the calendar phase started.

Net deliverables:
- Angular can render the full reservation-table flow against REST endpoints without re-deriving column rules.
- DHBW-scale exports stop pulling megabytes of JSON.
- The Swing client keeps its in-process model unchanged (no regression, no migration cost).
- `RaplaBuilder` ends up with one canonical consumer per render path; the parallel Swing-and-server color logic is gone.
- PRD 005 D3 back-edge becomes deletable.

## Scope

### In scope

| # | Surface | Endpoint | What it returns |
|---|---|---|---|
| 1 | Appointment table | `/table/appointments` (new) | `TablePage` with rows projected per column config |
| 2 | Reservation table | `/table/reservations` (new) | `TablePage` |
| 3 | Table column configuration per user | `/table/config` (new) | `TableColumnConfig[]` — id, label, visibility, sort default |
| 4 | CSV export | `/export/csv` (new) | streaming CSV body using the same engine |
| 5 | Block-color sharing | (in-process refactor — no new endpoint) | `BlockColors.resolve(...)` shared between `RaplaBlock` and the (future) `RaplaBlockDecorator` |
| 6 | Architectural cleanup | (no endpoint — code-mod) | `LocalCache.cachedReservations` documented as Swing-legacy; PRD 005 D3 back-edge deletion via arch-test |
| 7 | Swing reservation-table | (consumer change, no new endpoint) | `ReservationTableViewFactory` + `SwingTableView<TableRow>` fetch from `/table/reservations`; lazy entity rehydrate for tooltip / edit / copy / cut |
| 8 | Swing appointment-table + per-day | (consumer change) | Same for `AppointmentTableViewFactory`, `AppointmentsPerDayViewFactory`, `CSVExportMenu → /export/csv` |
| 9 | `CalendarModelImpl` client-async cleanup | (code-mod) | Retire the stopgap async fallbacks added 2026-05-12 once Phases 7+8 ship |

### Explicitly out of scope (separate PRDs)

- **Edit-time services** — PRD 024 Phases 1+2. Different lifecycle.
- **Power search / sidebar allocatable listing** — PRD 028. Same architectural move applied to allocatables and to ranking.
- **Angular UI implementation** — PRD 026. PRD 030 produces the wire; PRD 026 produces the consumer.
- **Real-time push.** Polling stays. SSE / WebSocket is a future PRD.
- **GraphQL / aggregated queries.** Each surface = one REST endpoint. No combined queries.
- ~~**Swing migration to the REST endpoints.** Swing keeps the in-process model~~ **— REVERSED 2026-05-12. The Swing client has always been a REST consumer via `RemoteOperator`; there was no in-process path for the table views. Phases 7+8 now migrate Swing reservation/appointment table views to `/table/*`. Swing *calendar* views (week / month / day / …) still keep their `RaplaBuilder` pipeline and remain out of scope for this PRD.**
- **Calendar surface itself** — already covered by PRD 024 Phase 3. PRD 030 references the existing work but doesn't duplicate it. Phase 4 (block-color sharing) is the only calendar-touching item in PRD 030 because it sits at the seam between Swing and server.
- **Swing calendar views** (week / month / day / compactweek / dayresource / timeslot) — out of scope. Only **Swing table views** migrate (Phases 7+8). Calendar surfaces keep their current `RaplaBuilder` + `AbstractRaplaSwingCalendar` pipeline; consolidation there is a future PRD if motivated.

## Plan

Each phase is **independently shippable**, in this order to minimise risk:

### Phase 1 — `TableViewEngine` (rapla-core, ≈5 days)

Mirrors the shape of `CalendarLayoutEngine`. Pure-Java, no facade, no Swing.

- New package `org.rapla.plugin.tableview` (next to the existing `org.rapla.plugin.calendarview`).
- `TableViewEngine.project(Collection<Reservation> reservations, TableColumnConfig[] config, SortSpec sort, PageSpec page) → TablePage`.
- `TableRow` record: `String id`, `Map<String, Object> cells` (column id → scalar value).
- Scalar cell values only — Strings, numbers, ISO date strings, booleans. No entity refs on the wire. Resolution of "what's the cell value" lives in cell extractors.
- **Cell extractor SPI**: `interface CellExtractor { Object extract(Reservation r, Appointment a); }` plus a registry keyed by column id. Plugin authors implement this for their custom columns (Exchange, ExternalEventImport).
- Sort: stable, multi-column. Pure comparator wiring on the engine side.
- Pagination: cursor-based (opaque token encoded as the last row's natural key).
- Tier-1 tests using `Proxy.newProxyInstance` stubs same as `CalendarLayoutEngineTest`.

### Phase 2 — Table REST surface (rapla-server, ≈3 days)

- `TableViewService` `@HttpExchange` interface in rapla-core. Two `@GetExchange` endpoints: `/table/appointments` and `/table/reservations`.
- Request: `from`, `to`, `columns[]`, `sort[]`, `cursor?`, `pageSize?`, `allocatables[]?` (filter), `reservationTypes[]?`.
- Response: `TablePage` record.
- `TableViewController` in rapla-server: parse → `facade.getReservations(user, ...)` → engine → return. Permission filter via facade as in PRD 024 Phase 3.
- Contract test (rapla-core) pins record fields, paths, methods.
- MockMvc test (rapla-app) covers: requires-auth, happy path, pagination boundaries, AGENTS.md §12 leak probe (unknown ids silently dropped), permission filter (admin vs. non-admin).

### Phase 3 — Column config endpoint (≈3 days)

Angular needs to know which columns to render and their defaults.

- `/table/config` GET → `TableColumnConfig[]`.
- Server reads from the user's preferences (`TableviewOption` data — same source as Swing).
- Plugin column contributions visible server-side; Angular doesn't need to know the plugin.
- MockMvc test covers: empty config falls back to defaults, plugin column appears, per-user override wins.

Pattern: PRD 020 (server publishes structure + per-user data).

### Phase 4 — Shared block colors (rapla-core, ≈2 days)

Refactor `RaplaBlock.getColorsAsHex()` to delegate to a new `BlockColors.resolve(Reservation, List<Allocatable>, eventColoring, resourceColoring)` helper in rapla-core. The future `RaplaBlockDecorator` (the in-flight piece reverted from this session) uses the same helper.

- New: `org.rapla.plugin.calendarview.BlockColors`.
- `RaplaBlock.getColorsAsHex()` becomes a 3-line delegate.
- Tier-1 tests pin event-color, resource-color, both-modes, neither-mode, fold-empty-strings.
- Tier-2 test using `FacadeTestSupport` validates the helper against real entity classifications.

This is the natural completion of PRD 023's Phase 4 row.

### Phase 5 — CSV export (≈3 days)

- `/export/csv` GET → CSV body (streaming).
- Reuses `TableViewEngine.project(...)` then a CSV serializer.
- Headers from `TableColumnConfig.label` honour `Accept-Language`.
- Client menu becomes a download link / browser GET — no in-process serialization.
- Tier-3 MockMvc test covers: column header line, row count, comma-escaping, locale-aware dates.
- Bonus: the HTML autoexport plugin can switch to `CalendarLayoutEngine` + `TableViewEngine` instead of its bespoke loop. Deferrable.

### Phase 6 — Architectural cleanup (post-Angular, ≈3 days)

**Only land once Angular consumes the endpoints in production.** Until then, `LocalCache.cachedReservations` is load-bearing for the Swing path and the back-edge is still needed for the Swing-side calendar render.

- Document `LocalCache.cachedReservations` as Swing-legacy.
- `CalendarSelectionModel.queryReservations(...)` keeps its Swing role; mark as not-for-new-callers in javadoc.
- **rapla-server → rapla-client back-edge deletion** (PRD 005 D3): once all server flows route through rapla-core engines, the `<dependency>rapla-client</dependency>` in `rapla-server/pom.xml` can go.
- Add an arch-test (`NoRaplaClientImportFromServerTest`) that fails CI if anything in `rapla-server/src/main` imports from `org.rapla.client.*` or `rapla-client` packages.

This phase may span multiple sessions and waits on Angular's actual deployment.

### Phase 7 — Swing reservation-table → `/table/reservations` (rapla-client, ≈3-4 days)

Migrates `ReservationTableViewFactory` + `SwingTableView<Reservation>` to fetch from `/table/reservations` and render `TableRow` rows directly. Retires `model.queryReservations(...)` for the table-view path.

**Sub-phases (each independently shippable):**

- **7.1 — `TableViewService` REST proxy bean.** **DONE 2026-05-12.** Added `@Bean tableViewServiceProxy` to `ClientProxyConfig`, wired via the existing `HttpServiceProxyFactory`. Mirrors the pattern of `iCalConfigServiceProxy`, `mailToUserProxy`, etc.
- **7.2 — `RaplaTableColumn<TableRow>` adapter.** New implementation reading from `TableRow.cells` keyed by column id. Same `RaplaTableColumn<T>` interface, but `getValue(row, format)` looks up the scalar by column key rather than projecting from an entity. Replaces the column-projection responsibility on the client.
- **7.3 — Lazy entity rehydration hook.** Tooltips (`infoFactory.getToolTip(rowObject)`), edit-dialog launch (`editController.edit(reservation)`), copy/cut menu items all need a live `Reservation`. New interaction protocol: the view caches a `Map<String, Reservation>` of resolved entities; on interaction, look up by `row.id()` — if absent, fire `GET /storage/reservation/{id}` and resolve. Single hit per row per session.
- **7.4 — Plugin summary extension adapter.** `ReservationSummaryExtension.init(table, panel)` currently reads `List<Reservation>` directly from the `JTable` model. New shape: the extension receives a `RowSelectionProvider` interface with `List<String> getSelectedIds()` + `Promise<List<Reservation>> resolveSelected()`. Existing extensions (DHBW counters, etc.) get adapter classes; new extensions use the protocol directly.
- **7.5 — Factory rewire.** `ReservationTableViewFactory.createSwingView` builds the `Supplier<Promise<List<TableRow>>>` from `tableViewService.reservations(from, to, columnIds, sort, null, null)` instead of `model.queryReservations(...)`. Column ids come from `tableConfigLoader.loadColumns(EVENTS_VIEW, user)`.
- **7.6 — Update column-id contract.** Server's `TableColumnDescriptor` keys must match what `tableConfigLoader.loadColumns` returns. Audit; align if drifted.

**Tests:**
- Tier-3 MockMvc smoke test: ReservationTableViewFactory's REST call shape; permission-leak probe; sort + filter pass-through (already covered by Phase 2 MockMvc tests — extend as needed).
- Tier-2 (rapla-client): a headless harness test for `SwingTableView<TableRow>` that asserts row count, column rendering, and tooltip lazy-fetch trigger.

### Phase 8 — Swing appointment-table + appointments-per-day → `/table/appointments` (rapla-client, ≈2 days)

Same migration as Phase 7 applied to:

- `AppointmentTableViewFactory` (rows = `AppointmentBlock`).
- `AppointmentsPerDayViewFactory` (rows = `AppointmentBlock`, with day grouping).
- `CSVExportMenu` — switch to `GET /export/csv` (already exists from Phase 5); no in-process serialization, browser download.

Depends on Phase 7's TableRow infrastructure (column adapter, lazy entity rehydration, plugin extension adapter). Mostly mechanical once 7 lands.

### Phase 9 — Retire `CalendarModelImpl` client-async fallbacks (rapla-core, ≈1 day)

Once Phases 7+8 ship and no client-side caller of `CalendarModelImpl.queryReservations` / `queryBlocks` / `queryAppointments` remains:

- Delete the stopgap async-fallback branches added 2026-05-12 from those three Promise wrappers. The remaining body is just the `SyncStorageOperator` server path (which never broke).
- Optionally hoist the three Promise methods into `SyncCalendarModel` only — i.e. remove them from the client-facing `CalendarModel` interface entirely. The client side never needs them again.
- Audit `CopyDialog`, `CalendarTableViewPresenter` for stragglers — if they still call `model.queryReservations(...)`, route them through `/table/reservations` (or `/storage/queryAppointments` if they need entities, not table rows).
- Update `MEMORY.md` / arch-test if any rapla-client-side test or arch invariant pins the old async-fallback shape.

Net delete: ~30 LOC across `CalendarModelImpl`. Net cleanup: client-facing `CalendarModel` API shrinks; the "is this sync available?" `instanceof SyncStorageOperator` check disappears from the client path.

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

Mirror of the existing PRD 024 Phase 3 `/calendar/view` flow, same JWT, same permission gate, same cursor pagination. Both Angular and Swing (post-Phase 7+8) consume the same endpoint; their differences are pagination shape (Angular paginates, Swing fetches all) and entity-rehydrate strategy (Angular never rehydrates, Swing lazy-fetches via `/storage/reservation/{id}` for interaction).

## Migration strategy — Swing and Angular both consume `/table/*`

**Revised 2026-05-12.** The original premise (this PRD's first draft, May 11) was "Swing keeps the in-process model unchanged". The multimodule-shift bug on 2026-05-12 made it clear that premise was wrong: the Swing client has always been a REST consumer via `RemoteOperator`. There is no in-process path for Swing today. So "not migrating Swing" was really "Swing keeps using `/storage/queryAppointments` while Angular uses `/table/*`" — two REST consumption paths for the same UX surface.

The revised strategy: **both clients consume `/table/*` for table views**. One server-side projection pipeline, one wire shape, one place to fix bugs. Net effects:

- The PRD 008 split (server-sync / client-async on `CalendarModelImpl`) becomes irrelevant for the table-view path — Swing stops calling `model.queryReservations` and friends entirely.
- The stopgap async fallback added to `CalendarModelImpl.queryReservations` / `queryBlocks` / `queryAppointments` (2026-05-12) becomes deletable in Phase 9.
- `LocalCache` keeps its role for calendar views (still on the in-Swing pipeline); the table-view migration doesn't depend on it.

### Calendar views still aren't migrated

This PRD only migrates Swing **table views**. Swing **calendar views** (week / month / day / compactweek / dayresource / timeslot / appointments-per-day-grid) keep their existing `RaplaBuilder`-based pipeline. Reasons:

- The Swing calendar uses local layout via `AbstractRaplaSwingCalendar` + `SwingRaplaBlock`. Migrating that to consume `CalendarPage` (PRD 024 Phase 3) requires a parallel set of view adapters and is its own multi-week effort.
- The table-view migration has a contained interaction surface (no resizable blocks, no drag-to-move). It is the natural first target.

A future PRD can revisit Swing calendar migration once Phase 7+8 is shipped and the table-view experience is concrete.

### Pagination — Swing keeps monolithic-scroll, opt-in for Angular

Both clients call `/table/*`, but their UX shapes differ:

- **Swing** — calls `/table/*` **without** `pageSize`, gets all rows back (subject to the 50 000-row server cap), renders in `JTable` with local scroll. Same look-and-feel as today's `JTable`-monolithic behaviour. `incomplete: true` + cursor in the response triggers a "narrow your date range" dialog rather than a paging UI.
- **Angular** — picks either explicit pagination (`pageSize=N`, follow `nextCursor`) or virtual scrolling (one big response, render only visible rows via `cdk-virtual-scroll-viewport`). Pagination recommended; virtual scroll as escape hatch.

The endpoint is identical; the client decides whether to use `pageSize`. ~50 KB per page for Angular; ~1 MB at DHBW scale for Swing's no-`pageSize` response.

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
| 9 | arch | rapla-core | `NoClientAsyncFallbackInCalendarModelTest` — pins that the three Promise wrappers are sync-only post-deletion |

Reuse `PreferencesAdminControllerIntegrationTest` as the MockMvc template per phase — same Spring context, same JWT setup, same AGENTS.md §12 leak-probe regression pattern.

## Risks

### Engineering risks

1. **Per-render round-trip cost (TABLES specifically).** For the calendar, no change: today's client already hits the wire on every render (`CalendarModelImpl.cachingEnabled = false`; every `queryAppointmentBindings(interval)` is a remote call). For **tables**, the situation is different — today the client has the full reservation graph in memory once it's fetched, so column-header sort or page-down is instant. With server-side rendering, every sort or page = round-trip. **Mitigation:** keep one page of rows in Angular state after the first fetch and re-sort there for the next 1–2 interactions; refetch only on date-range / filter change. Same pattern used by modern data-grid components.

2. **No cache invalidation problem at v1.** The system doesn't add server-side rendered-page caching in PRD 030. Each request runs the engine; output isn't cached. This deliberately matches today's client-side behaviour (`cachingEnabled = false`), so there's no new invalidation contract to honour. **If perf later motivates server-side caching,** PRD 008's update-event flow is the natural invalidation source — invalidate by date range on `ModificationEvent`. That's a follow-up PRD, not a v1 risk.

3. **Plugin SPI duality.** The existing table-view plugin defines columns via a Swing-flavoured SPI (`JComponent` return). A parallel **value-oriented** SPI is needed for the engine. **Risk:** plugin authors must implement both during the transition. **Mitigation:** provide a default `ToValueAdapter` that wraps Swing extractors; document the new SPI as the recommended path.

4. **CSV export determinism — non-risk if locale + columns are explicit request parameters.** The original concern was "the server picks a locale or column set the user didn't expect". The contract obviates it by design: `/export/csv?from=…&to=…&columns=name,start,end,room&sort=start:asc&locale=de-DE` (or locale via `Accept-Language`). With every variable that affects output passed in, the response is a function of the request — same inputs, same bytes out. Angular sends its current visible-column set + the user's locale; the server has no ambient state to drift on. **No mitigation needed beyond the API design.** Documenting here so a future contributor doesn't reintroduce "smart defaults" on the server side (e.g. falling back to a default column set when none is sent) — that's precisely the surface that *would* drift.

### Architectural risks

5. **Permission drift between Swing and Angular — not a structural risk; server-side rendering actually *reduces* the drift surface.** The Angular client never computes permissions locally for a server-rendered surface — it just paints what arrives. The Swing client keeps its local computation, but calls the same `PermissionController.canRead(...)` code in rapla-core that the server uses, so the *rule* can't diverge. The only residual gap is the eventual-consistency window between a server-side change and the client's next sync delta — which exists today regardless of PRD 030. **Net effect:** moving rendering server-side gives Angular one execution site for permission decisions, instead of two paths that could diverge. No mitigation needed; documenting here so a future reader doesn't reintroduce client-side permission filtering as a "perf optimisation" for Angular.

6. **`LocalCache` removal is risky and may be deferred indefinitely.** Phase 6 says "after Angular consumes". But Swing keeps using `LocalCache` for in-session navigation cache. **Acceptable end state:** keep `LocalCache` for Swing; mark its `cachedReservations` field as Swing-legacy in javadoc; never remove. Phase 6 then reduces to just the back-edge deletion.

7a. **Swing table-view migration: plugin extension breakage (Phase 7-8).** `ReservationSummaryExtension` is an extension point — third-party plugins (DHBW, etc.) implement it, and they currently consume `List<Reservation>` derived from selected rows. Migrating `SwingTableView<T>` to `T = TableRow` breaks every existing implementation. **Mitigation:** introduce a new `RowSelectionProvider` interface that exposes `getSelectedIds()` + `Promise<List<Reservation>> resolveSelected()`; provide a `ReservationSummaryAdapter` that wraps the legacy `List<Reservation>` API on top of the new shape. Audit each existing extension during Phase 7.4. Estimated 1-day adapter work.

7b. **Swing table-view migration: lazy-rehydrate UX latency.** Tooltips on hover, right-click context menus, double-click-to-edit each need a `Reservation` entity. The new flow lazy-fetches via `GET /storage/reservation/{id}` on first access. **Risk:** noticeable lag (50-300 ms) on first interaction per row. **Mitigation:** prefetch the visible-row entities in the background after the initial table render; cache in-memory for the session. If perceived lag remains, add a bulk `POST /storage/reservations?ids=…` endpoint as a follow-up. Decision deferred until Phase 7.3 lands and the actual UX can be measured.

7c. **Swing table-view migration: server-side sort vs. client-side `JTable` sort.** `JTable` supports column-header click sorting. With `/table/reservations` returning pre-sorted rows, every column-header click is a new REST round-trip. **Mitigation:** for the no-`pageSize` Swing case (all rows fetched), keep local `TableRowSorter` on the rendered `TableRow` cells — no round-trip; sort works on the scalar cells already in memory. The server-side `sort` parameter is only used for the initial fetch order. Net effect: Swing sort UX matches today's behaviour.

8. **HTML autoexport coupling — applies to *calendar-grid* HTML views only, not plain tables.** The HTML autoexport plugin renders two distinct families:
    - **Calendar-grid HTML pages** (`HTMLWeekViewPage`, `HTMLMonthViewPage`, `HTMLDayViewPage`, `HTMLCompactWeekViewPage`, `HTMLDayResourcePage`, the two `timeslot` views, and `AppointmentPerDayViewPage`) — these run through `HTMLRaplaBuilder` (subclass of `RaplaBuilder`) and `AbstractHTMLView`, i.e. the same `BuildContext` + `BuildStrategy` pipeline the Swing client uses. PRD 030 builds `CalendarLayoutEngine` (Angular-bound) **alongside** that pipeline, not as a replacement. Risk: rendering rule changes (colors, visibility, conflict markers) land in one pipeline but not the other. **Mitigation:** when changing rendering rules, audit both call sites. A future PRD can migrate the HTML pages to consume the engine — pure code-deletion, ~1500 LOC of `HTMLRaplaBuilder` + `HTMLRaplaBlock` + much of `AbstractHTMLView` becomes deletable. Not in PRD 030 scope because DHBW relies on the autoexport for public schedule pages; the migration is risk-bearing for zero user-visible benefit.
    - **Plain HTML / CSV table pages** (`ReservationTableViewPage`, `AppointmentTableViewPage`) — these don't use `RaplaBuilder` at all. They query reservations directly, walk `RaplaTableColumn<T>` plugin columns, and emit `<table>` HTML. No coupling to the calendar-grid pipeline. The only overlap with PRD 030's `TableViewEngine` is the column abstraction itself, which Risk #3 (plugin SPI duality) already covers. Once Risk #3's mitigation lands (the new `CellExtractor` SPI alongside `RaplaTableColumn`, or `RaplaTableColumn` delegating to a `CellExtractor`), there's one column-value source feeding both HTML and JSON outputs. No code-duplication maintenance burden remains. **Not a risk for plain tables.**

## Open questions

1. **Table column config — server-owned or client-owned? RESOLVED 2026-05-12: server-owned** (PRD 020 pattern). Backed by user `Preferences` (same store as Swing's `TableviewOption`). Angular fetches `/table/config` once at boot, caches in memory for the session. Plugin column catalog (the universe of available column ids) is a sibling endpoint `/table/columns/catalog`. Cross-device persistence + Swing/Angular consistency + matching the existing store outweigh the one round-trip cost.

2. **CSV as a separate endpoint or content negotiation? RESOLVED 2026-05-12: separate `/export/csv`.** Cleaner contract, easier streaming.

3. **Pagination strategy. RESOLVED 2026-05-12.** Cursor-based, opt-in via `pageSize` request parameter. Omitting `pageSize` returns all rows (Swing default, also matches a future Swing migration). Server caps the no-`pageSize` response (default 50 000 rows) with `incomplete: true` + a cursor when the cap is hit. Response includes `totalCount` regardless of paging mode. Angular picks pagination vs. virtual scrolling as a UX choice; Swing keeps its monolithic-scroll JTable unchanged.

4. **Plugin extractor SPI. RESOLVED 2026-05-12: parallel value-oriented SPI** (`CellExtractor<T>`) alongside the Swing-flavoured `RaplaTableColumn<T>`. No behaviour change for Swing; new SPI for the engine. Plugins can register both, or `RaplaTableColumn` can delegate to a `CellExtractor` + a per-column HTML formatter as a cleanup follow-up.

5. **Authoritative export vs. live view. RESOLVED 2026-05-12: snapshot at request time T.** Table view and export use the same engine + same query parameters; same inputs → same bytes out. Any future server-side caching may serve a slightly older snapshot if invalidation is lazy; document that explicitly in the cache PRD when it lands.

6. **Calendar `period` overlay. RESOLVED 2026-05-12: separate `/periods` endpoint** for now. Revisit only if the round-trip count proves a UX problem in Angular.

7. **Cell type info on columns. RESOLVED 2026-05-12: type descriptor on `TableColumnDescriptor`, scalar values on rows.** Lets Angular render dates / numbers / strings consistently without server-side pre-formatting. CSV export adds a thin formatter on top (the export *does* render strings).

8. **`LocalCache` permanent retention. RESOLVED 2026-05-12: retain indefinitely for Swing.** Document `cachedReservations` as Swing-legacy in javadoc; never remove. Phase 6 reduces to back-edge deletion only.

9. **Phase ordering vs Angular v1. RESOLVED 2026-05-12.** Angular v1 (PRD 026) is reservation **edit**; Phases 1–3 are not on the v1 critical path. Schedule: Phase 1+2 in parallel with PRD 024 Phases 1+2 (during Angular v1 prep); Phases 3+4+5 during v1.1; Phase 6 only after Angular ships and stabilises in production.

10. **Lazy vs eager entity rehydration (Phase 7.3).** When `SwingTableView<TableRow>` needs a live `Reservation` (tooltip / edit / copy / cut), fetch options:
    - **Lazy on first touch** (recommended): zero up-front cost, 50-300 ms latency on first interaction per row. Matches the pattern of "you only edit a few rows per session."
    - **Eager prefetch in background** after initial render: hides the latency entirely but doubles wire traffic on first load. Worth doing only if Risk 7b's measured latency is actually painful.
    - **Bulk endpoint** `POST /storage/reservations?ids=…`: bridges the two — one round-trip for prefetch instead of N. Add only if needed; not a Phase 7 deliverable.

    Decision: lazy by default; measure in Phase 7.3; add prefetch / bulk if motivated.

11. **`RaplaTableColumn<TableRow>` adapter location (Phase 7.2).** Where does the new adapter live?
    - `rapla-core/.../tableview` (alongside `CellExtractor`) — visible to both client tiers, but rapla-core has no Swing dep.
    - `rapla-client/.../tableview/client/swing` (next to `RaplaSwingTableColumnImpl`) — Swing-flavoured. The adapter is purely a value-lookup, no Swing imports needed, so either location is technically OK.

    Decision: rapla-core for the value-lookup adapter (`TableRowColumn implements RaplaTableColumn<TableRow>`); a thin Swing wrapper in rapla-client only if needed for `init(TableColumn)` calls during render setup. **Pending Phase 7.2 implementation pass.**

12. **`ReservationSummaryExtension` adapter shape (Phase 7.4).** Two routes:
    - Adapter wraps the new `RowSelectionProvider` to look like today's `List<Reservation>` consumer; existing extensions keep working unchanged. Lower migration friction; defers the API shift.
    - New `RowSelectionProvider` interface as primary; legacy extensions get a deprecation marker and an adapter; new extensions consume the new interface. Cleaner long term; more churn in DHBW now.

    Decision: adapter route initially (Phase 7.4); deprecation pass + cleanup in a follow-up PRD. **Pending Phase 7.4 pass.**

13. **Where does the rehydration cache live? (Phase 7.3)** The lazy fetch needs a `Map<String, Reservation>` somewhere — in the `SwingTableView` instance (lifecycle = view open) or in a session-scope service (lifecycle = whole client run)?
    - View-scope: invalidates on view close. Simple. Loses cache on view switch.
    - Session-scope: survives view switches. Coupled to `LocalCache` invalidation (server changes invalidate the cache).

    Decision: view-scope initially, smallest blast radius. **Pending Phase 7.3 pass.**

## Cross-references

- **PRD 020** — server-driven admin panels. The pattern template; same idea applied to admin surfaces.
- **PRD 024** — server-side edit services (sibling, edit-side). Phase 3's `/calendar/view` is the prior art this PRD generalises.
- **PRD 026** — Angular frontend. Primary consumer.
- **PRD 028** — Angular power search. Parallel PRD applied to the allocatable surface.
- **PRD 021 wont-fix** — client resource stubs. Motivation subsumed by this PRD's broader shift.
- **PRD 005** — multi-module split. Phase 6 deletes the D3 back-edge.
- **PRD 008** — server-sync update events. Phase 2's cache-invalidation rides on this.
- **PRD 009** — bulk storage REST. The edit-side companion (load full entity on demand).
- **PRD 023** — pure-Java carve-outs. Phase 4 (`BlockColors`) is also a natural completion of 023's Phase 4 row.
