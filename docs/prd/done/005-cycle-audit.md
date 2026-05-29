# PRD 005 — Cycle Audit (Phase B3 output)

**Captured:** 2026-05-07
**Tool:** `jdeps -dotoutput out/jdeps -e 'org\.rapla\..*' -verbose:package target/classes` (Java 21)
**Performed against:** `target/classes` after Phase B1 + B2 source moves applied (clean `mvn compile`).
**Total intra-rapla package edges:** 2,017
**Total bidirectional package pairs (any cycle):** 63 → **61 after Phase B4**
**Bidirectional pairs that cross proposed module boundaries:** 3 → **2 after Phase B4** → **0 after the rapla-server → rapla-client edge removal (2026-05-07)**
**Unidirectional edges that violate the proposed module DAG:** 92 (mostly plugin-classification questions, deferred to Phase D)

## Module classification used in this audit

Packages were classified into proposed modules as:

- **`rapla-server`**: `org.rapla.server.*`, `org.rapla.enpoints.server.*`, `org.rapla.storage.dbsql.*`, `org.rapla.storage.impl.server.*`, plus `plugin.<name>.server.*`
- **`rapla-client`**: `org.rapla.client.*`, `org.rapla.components.{calendar,calendarview,iolayer,tablesorter,treetable}.*`, plus `plugin.<name>.client.*` and `plugin.<name>.swing.*`
- **`rapla-core`**: everything else under `org.rapla.*` — `entities.*`, `facade.*`, `framework.*`, `logger.*`, `scheduler.*`, `storage.{dbrm,impl}` (non-server parts), `rest.*`, `components.{util,layout,restproxy,i18n}` (excluding swing), `inject.*`, plus `plugin.<name>.extensionpoints.*` and shared plugin descriptors. Plugin bare-roots and `plugin.<name>.<misc>` packages were inspected for Swing/JDBC imports to refine classification.

## Real cross-module cycles (3 bidirectional pairs)

### 0. `rapla-server → rapla-client` Maven edge — **RESOLVED 2026-05-07**

`rapla-server/pom.xml` had a runtime dep on `rapla-client` for HTML calendar rendering (`RaplaBlock`/`RaplaBuilder` + `components.calendarview.html.*`), pulling Swing/AWT into server-only deployments.

Verified with grep: every shared class either had zero Swing/AWT imports or only imported other classes in the same shared subset. 27 files moved from `rapla-client` to `rapla-core`:

- `org.rapla.components.calendarview.*` (12 top-level: `AbstractCalendar`, `Block`, `Builder`, `CalendarView`, `WeekdayMapper`, …) — `swing/` subpackage stays in `rapla-client`.
- `org.rapla.components.calendarview.html.*` (5: `AbstractHTMLView`, `HTMLBlock`, `HTMLWeekView`, `HTMLMonthView`, `HTMLCompactWeekView`).
- `org.rapla.plugin.abstractcalendar.{RaplaBlock, RaplaBuilder, HTMLRaplaBlock, HTMLRaplaBuilder, GroupAllocatablesStrategy}` — toolkit-agnostic + HTML pieces. Swing pieces (`DateChooserPanel`, `RaplaCalendarViewListener`, `client/swing/*`) stay in `rapla-client`.
- `org.rapla.client.internal.{HTMLInfo, ClassificationInfoUI, ReservationInfoUI, AppointmentInfoUI, RaplaColors, LinkController}` — six "UI-named-but-actually-HTML-text" helpers, no Swing/AWT imports despite the names.

Deleted the `rapla-client` dep from `rapla-server/pom.xml`. `mvn compile` SUCCESS; `mvn test` 94/0/0/2-skipped; `dependency:tree` confirms only `rapla-core` remains. **No Swing/AWT in server classpath; dhbwrapla-server-only loses ~5 MB.**

`WeekdayMapperTest` stays in `rapla-app` (needs `rapla-server`'s `ServerBundleManager` for i18n setup).

### 1. `core/framework.internal` ↔ `server/server.internal` — **RESOLVED in Phase B4 (2026-05-07)**

Single offender: `org.rapla.framework.internal.DefaultScheduler` imports `org.rapla.server.internal.TimeZoneConverterImpl`:

```java
this(logger, new TimeZoneConverterImpl());                    // line 41
TimeZoneConverterImpl converter = new TimeZoneConverterImpl();  // line 76
```

`TimeZoneConverter` + `TimeZoneConverterImpl` are pure-Java (no servlet/Spring/JDBC) — framework-shaped, misplaced in `server.*` from the start. Moved:

| File | Move to |
|---|---|
| `org.rapla.server.TimeZoneConverter` | `org.rapla.framework.TimeZoneConverter` |
| `org.rapla.server.internal.TimeZoneConverterImpl` | `org.rapla.framework.internal.TimeZoneConverterImpl` |

Importer fan-out: 8 files, mechanical import-line changes.

**Phase B4 executed 2026-05-07:** 2 `git mv`, 14 import updates across 13 files, 1 FQN at `ServerServiceConfig.java:263`, 2 missing-import additions caught by `mvn clean compile` (same-package unqualified token resolution). After fix: `mvn clean compile` SUCCESS; 14 targeted tests pass; jdeps re-run confirms `framework.* → server.*` is **0 edges**.

**Lesson:** mass-rename refactors require `mvn clean compile` — incremental compile reuses cached `.class` files, hiding broken unqualified-name resolution. Run `mvn clean compile` at the end of each D-step.

### 2. `server/plugin.exchangeconnector.server` ↔ `core/plugin.exchangeconnector.server.exchange`

**Misclassification, not a real cycle.** The audit's automatic classifier put `plugin.exchangeconnector.server.exchange` in `core` because its directory contains files with no Swing or HTTP imports. But it's structurally a `*.server` sub-package and belongs in `rapla-server` regardless of import shape. **No code change needed** — Phase D3 will correctly assign all `plugin.exchangeconnector.server.*` (including the `.exchange` sub-package) to `rapla-server`.

### 3. `core/plugin.tableview.extensionpoints` ↔ `client/plugin.tableview.internal`

**Misclassification of `tableview.internal`.** The `extensionpoints` package contains the `TableViewExtensionPoints` interface family (correctly core). The `internal` package is the client-side implementation. The cycle disappears once `plugin.tableview.internal` is unambiguously assigned to `rapla-client` and `plugin.tableview.extensionpoints` to `rapla-core`. **No code change needed** before Phase C; resolve in Phase D2.

## Unidirectional edges flagged "illegal" (92)

Almost all are plugin-classification questions, not architectural problems — the audit pessimistically classified some plugin packages as `core`, making every `core → client` edge from them look illegal.

Categorisation by source pattern:

| Pattern | Count | Action |
|---|---|---|
| `plugin.<name>.client.swing` → `client.*` | ~30 | **Not illegal** — these packages should be classified as `client`. Fix the classification, not the code. |
| `plugin.<name>.{client, server}` (bare) → `client.*` or `server.*` | ~40 | Classification question per plugin (PRD 004 Risk 2 deferral). Most resolve to `client` or `server` once each plugin is laid out. |
| `client.spring` → `plugin.<name>.{client,server}` | 6 | `client.spring.SwingClientConfig` `@Import`s plugin client configs. After PRD 005 splits the modules, `client.spring` lives in `rapla-client`; the plugin `*.client.swing` packages also live in `rapla-client`; plugin `*.server` packages live in `rapla-server`. The "illegal" flag is wrong — these are intra-`rapla-client` edges except for one entry that needs investigation. |
| `framework.internal → server.*` (1) | 1 | The `TimeZoneConverterImpl` cycle (resolved by Phase B4 above). |
| `storage.dbfile → storage.impl.server` (1) | 1 | One file: `storage.dbfile.RaplaCoreFileService` references `storage.impl.server`. Likely belongs in `rapla-server` rather than `rapla-core`. Resolve in Phase D3. |

After applying Phase B4 + the plugin classification cleanup in Phase D2/D3, the residual count of true cross-module violations should be **0**.

## Verdict against PRD 004 Risk 1

PRD 004 Risk 1 set the threshold:
> If <50 cycles, the split is a 1–2 week task; if >300, it's a quarter.

**Observed: 1 real cross-module cycle + 92 plugin-classification questions.** This is the easy end of PRD 004's range. The 1.5-week effort estimate in PRD 005 stands.

## Next concrete steps before Phase C

1. **Phase B4 (new, ~30 min):** Move `TimeZoneConverter` + `TimeZoneConverterImpl` from `org.rapla.server[.internal]` to `org.rapla.framework[.internal]`. Update 8 importer files. `mvn compile` after.
2. **Re-run `jdeps`** after B4 to confirm the cycle is gone.
3. Then proceed to Phase C (reactor skeleton).

The plugin-classification questions (the 92 one-way edges) are deliberately deferred to Phase D2/D3 — they can't be answered without committing to a per-plugin module layout, which PRD 004 Risk 2 says to defer until at least one plugin needs independent release.
