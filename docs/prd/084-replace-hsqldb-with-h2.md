# PRD 084 — replace the embedded HSQLDB persistence backend with H2

**Status:** draft — 2026-06-24
**Related:** [PRD 082](082-storage-memory-model.md) (storage memory model — the new read-model uses H2; this consolidates the *persistence* backend on the same engine), [PRD 083](083-user-change-subscription.md) (user change-subscription), AGENTS.md §8 (server lifecycle), [PRD 054](054-standalone-windows-installer.md) (timestamp/wall-clock storage scars)

## Abstract

rapla's embedded/dev persistence backend is **HSQLDB** (`DBOperator`, `jdbc:hsqldb:file:...`). [PRD 082](082-storage-memory-model.md) selects **H2** for the in-memory read-model (MVStore/MVCC, built-in full-text, richer JSON). Running two embedded SQL engines (HSQLDB for persistence + H2 for the read-model) is avoidable: **replace the shipped HSQLDB with H2** and consolidate on one engine. Independently of [PRD 082](082-storage-memory-model.md) it is also a modernization win (H2's MVStore is faster and more concurrent than HSQLDB's older engine).

**Scope is the embedded backend only.** The external `DBOperator` backends — **MariaDB / PostgreSQL — are unaffected**; this PRD swaps the bundled embedded default and its dev/distribution config. The `FileOperator` (XML) backend is also unaffected.

## Current footprint (scanned 2026-06-24)

- **Dependency:** `org.hsqldb:hsqldb` in `rapla-server/pom.xml`; version `2.7.1` managed in `rapla-bom/pom.xml`.
- **Dialect handling — H2 is already partially wired:** `AbstractTableStorage` has both `isHsqldb()` (`dbProductName.contains("hsql")`) **and `isH2()`** branches (e.g. `if (!isHsqldb() && !isH2())`, `if (isHsqldb() || isH2())`); `RaplaSQL` has `isHsqldb()` branches. So the dialect abstraction already anticipates H2 — Phase 1 is auditing completeness, not greenfield.
- **`DBOperator`:** a `boolean hsqldb` flag + an HSQLDB-special `SHUTDOWN COMPACT` on disconnect.
- **Config / distribution:** `application-local.yml` → `jdbc:hsqldb:file:./data/rapla-hsqldb`; `src/main/distribution/contexts/rapla.xml` and `README-Server.txt` use `org.hsqldb.jdbc.JDBCDataSource` URLs.
- **Timestamp semantics:** `RaplaSQL` has wall-clock/timezone handling ("same wall-clock representation HSQL/MariaDB stored", [PRD 054](054-standalone-windows-installer.md) scars) that must be preserved byte-for-byte on H2.

## Why

- **Consolidate on one embedded engine** with [PRD 082](082-storage-memory-model.md)'s read-model → one dependency, one dialect to reason about, one set of quirks.
- **Modernization:** H2 MVStore (MVCC, log-structured) > HSQLDB's older lock-oriented engine for concurrent read/write.
- **Low risk:** the dialect layer already has `isH2()` branches; H2 and HSQLDB share a near-identical SQL dialect.

**`MODE=HSQLDB` as a dialect bridge.** H2 ships compatibility modes (`jdbc:h2:...;MODE=HSQLDB`) that make H2 behave like HSQLDB at the SQL-dialect level — so most HSQLDB-specific SQL in `RaplaSQL` likely runs **unchanged**, sharply reducing the Phase-1 dialect-audit work. It even enables a staged cutover: migrate first with `MODE=HSQLDB`, then move to native H2 dialect later. **Caveat: `MODE=HSQLDB` affects only the dialect, not the on-disk format** — it does *not* let H2 read HSQLDB data files (the copy step above is still mandatory). The two are orthogonal:

| | HSQLDB data files | HSQLDB SQL dialect |
|---|---|---|
| H2 takes over directly? | **no** — copy step required | **yes** — `MODE=HSQLDB` |

## The hard part: migrating existing deployments' data

The shipped default writes HSQLDB files (`rapla-hsqldb.script` / `.data` / `.properties`). Switching the engine means **existing installs must migrate their data** — this is the real risk, not the code swap.

**H2 cannot read HSQLDB files directly.** Despite the shared SQL ancestry, the on-disk storage formats are completely different (H2 MVStore vs HSQLDB's format) — there is no "point H2 at the old file and it works". A **data copy step is mandatory** (via JDBC dual-read or rapla's engine-neutral XML export/import). This is independent of the SQL-dialect compatibility below.

Migration options (decide in OQ1):
- **A — XML round-trip (preferred, reuses existing machinery):** on the old build, `ImportExportManager.doExport()` to rapla XML; on the H2 build, import. rapla already has full XML export/import; the format is engine-neutral. Cleanest, no SQL-dialect translation. Requires a documented one-time operator step (or an automated "detect HSQLDB files → export → import to H2 → archive old" boot path).
- **B — dual-read migration on first boot:** if HSQLDB files are present, open them read-only with the (retained, temporary) HSQLDB driver, copy entities into H2, archive the HSQLDB files. Automatic but keeps the HSQLDB dependency through one release.
- **C — operator-run SQL dump/restore:** HSQLDB `SCRIPT` → translate → H2 `RUNSCRIPT`. Dialect-fragile; not preferred.

dhbwrapla and other downstream deployments must be coordinated (their data is the live concern).

## Scope

### In scope
- Swap `hsqldb` → `h2` dependency (`rapla-server/pom.xml`, `rapla-bom/pom.xml`).
- Audit + complete the `isH2()` dialect branches; replace the `hsqldb` flag / `SHUTDOWN COMPACT` special with the H2 equivalent (H2 also supports `SHUTDOWN [COMPACT]`).
- Update embedded URLs/driver in `application-local.yml`, `contexts/rapla.xml`, `README-Server.txt` → `jdbc:h2:file:...` / `org.h2.Driver`.
- Preserve timestamp/wall-clock semantics ([PRD 054](054-standalone-windows-installer.md)) — verified by round-trip test.
- A migration path for existing HSQLDB data (OQ1).
- `@Tag("db")` tests run green against H2.

### Out of scope
- MariaDB / PostgreSQL backends (unchanged).
- `FileOperator` (XML) backend (unchanged).
- The [PRD 082](082-storage-memory-model.md) read-model (separate; this PRD only makes the *persistence* engine match it).

## Plan

### Phase 1 — dialect audit + dependency swap (no data yet)
- [ ] Start with `jdbc:h2:...;MODE=HSQLDB` to let most HSQLDB SQL run unchanged; this scopes the audit to what `MODE=HSQLDB` does *not* cover.
- [ ] Audit every `isHsqldb()` site for a correct `isH2()` counterpart; complete missing branches. Add H2 product-name detection.
- [ ] Swap the dependency in BOM + rapla-server; replace the `SHUTDOWN COMPACT` special path for H2 (H2 also supports `SHUTDOWN [COMPACT]`).
- [ ] Run the full `@Tag("db")` suite against a fresh H2 (empty store) — green.
- [ ] (Optional later) drop `MODE=HSQLDB` and move to native H2 dialect, re-running the suite.

### Phase 2 — schema + timestamp parity
- [ ] Verify every `RaplaSQL` table DDL (types: `VARCHAR(255)`, `DATETIME`, `INTEGER`, …) creates correctly on H2.
- [ ] Timestamp/wall-clock round-trip test: write via H2, read back, assert identical `LocalDateTime` to the HSQLDB baseline ([PRD 054](054-standalone-windows-installer.md) regression).

### Phase 3 — data migration
- [ ] Decide OQ1 (XML round-trip vs dual-read vs dump).
- [ ] Implement + document the chosen path; "detect old HSQLDB files" handling.
- [ ] Migrate a real dhbwrapla snapshot end-to-end; diff entity counts + spot-check reservations/appointments/permissions.

### Phase 4 — config, distribution, docs
- [ ] Update all distribution configs + README; bump AGENTS.md §8 dev-server notes if the local data path/URL changes.
- [ ] Coordinate dhbwrapla cutover.

## Tests

- `@Tag("db")` suite green on H2 (Phase 1).
- Timestamp/wall-clock round-trip parity vs HSQLDB baseline (Phase 2).
- Migration correctness: HSQLDB snapshot → H2, assert entity-count + content parity (Phase 3).
- A boot test: fresh H2 file created, server starts, default admin present.

## Open Questions

- **OQ1** — Migration strategy: **A (XML round-trip)** vs B (dual-read first-boot) vs C (SQL dump). A reuses existing export/import and is dialect-neutral → leaning A, with an automated detect-and-migrate boot path; confirm against operator ergonomics and the dhbwrapla dataset size.
- **OQ2** — Keep the HSQLDB dependency for one transitional release (needed by migration option B; lets old installs still be read)? Or hard-cut and require migration before upgrade?
- **OQ3** — H2 storage mode: classic PageStore vs MVStore (MVCC). MVStore is the modern default and aligns with [PRD 082](082-storage-memory-model.md)'s read-model rationale — confirm it's used for persistence too.
- **OQ4** — Are there HSQLDB-specific SQL constructs in `RaplaSQL` beyond the audited `isHsqldb()` branches (sequences, identity, function names) that H2 spells differently?
- **OQ5** — Does any external tooling/runbook (backup scripts, dhbwrapla ops) depend on the `rapla-hsqldb.*` file layout? Cutover must not silently break operator backups.
