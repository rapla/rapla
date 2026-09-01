# PRD 108 — CHANGES history timestamp convention (legacy in-place DB migration)

**Status:** implemented (Option A) — 2026-08-28
**Related:** [PRD 058](058-graphql-key-spec-migration.md) (startup migration that surfaced the bug), PRD 054 references in `RaplaSQL` (the `Timestamp.valueOf` "self-consistent" CHANGES binding), [migration guide](../migration-rapla2-to-rapla3.md) (in-place DB path)

## Abstract

Rapla 3 writes `CHANGES.CHANGED_AT` with a different wall-clock convention than every
other timestamp column — and than Rapla 2 did. On an in-place migrated Rapla 2 MariaDB
the legacy history rows of the last 1–2 h before the copy therefore look like they lie
in the future: they are replayed into the cache on every refresh and win against Rapla 3's
own writes. First observed 2026-08-28 on a legacy rapla 2.0 MariaDB instance migrated
in place: the PRD 058 key migration committed correctly to the DB, the subsequent cache
refresh replayed the legacy row of the same DynamicType, and
`GraphqlKeyMigration.assertCacheSpecCompliant` refused startup with
`ATTR:<type>.<old-key>`. End state: legacy history rows can never mask Rapla 3 writes,
verified by a `@Tag("db")` test that reproduces the scenario.

## Findings (verified 2026-08-28, file:line against `spring-boot` @ 392e31d58)

**Timestamp model of Rapla 3.** Every `LocalDateTime` in storage is a *UTC wall-clock*
LDT: `DateTools.toLocalDateTime(millis)` / `toMilli(ldt)` use `ZoneOffset.UTC`
(`rapla-core/.../DateTools.java:144-151`); `LocalAbstractCachableOperator.getCurrentTimestamp()`
(`:1024`) returns `toLocalDateTime(System.currentTimeMillis())`;
`RaplaSQL.getDatabaseTimestamp()` (`:745-758`) reads `CURRENT_TIMESTAMP` and converts
to a UTC LDT.

**Entity tables — correct absolute time.** `AbstractTableStorage.setTimestamp`
(`:388-398`) does `new Timestamp(toMilli(ldt))` + `stmt.setTimestamp(col, x, datetimeCal)`
with `datetimeCal = Calendar.getInstance(TimeZone.getDefault())` (`:61`, `:491`) —
i.e. the driver stores the **true local wall-clock** (`DYNAMIC_TYPE.LAST_CHANGED`
observed `15:11:06` local for a write at 15:11 CEST). `getTimestampOrNow` (`:335-349`)
is the exact inverse and additionally guards *"Timestamp in table in the future … Ignoring"*.
Rapla 2 (`origin/master`) used the same helper with `java.util.Date` — so legacy rows
in entity tables are also true local time. **Entity tables are consistent between
Rapla 2 and Rapla 3.**

**CHANGES table — UTC wall-clock stored verbatim.** `HistoryStorage.write` binds
`stmt.setTimestamp(5, java.sql.Timestamp.valueOf(timestamp))` (`RaplaSQL.java:3037`),
which stores the UTC-LDT *verbatim* as the column's wall-clock → `13:11:06` for the
same 15:11 CEST write (1 h off in winter, 2 h in summer). The read predicate
`WHERE CHANGED_AT >= ?` in `update(lastUpdated)` (`:2754`, `:3056`) and `cleanupHistory`
(`:2936-2999`) bind the same way, with comments crediting PRD 054 for making the
CHANGES path *self-consistent*. It is self-consistent — but **shifted by the local UTC
offset against every other column and against Rapla 2's CHANGES rows** (observed
legacy row `14:03:25` = true local time, same DB, same column).

**Consequence on an in-place migrated DB.** With `lastUpdated` ≈ DB-now − offset,
every legacy CHANGES row from the last `offset` hours before the migration satisfies
`CHANGED_AT >= lastUpdated` forever (until real time catches up) and is replayed by
`DBOperator.refreshWithoutLock` (`:415-436`) on every dispatch/refresh.
`history.getLatest(id)` orders by that stamp, so a legacy row (14:03) beats a Rapla 3
row written later (13:11 verbatim) → the cache gets the *old* entity while the DB holds
the new one. Side effects seen in the same boot: `ClassificationFilterReader` parsed
user-preference filters (already rewritten to the new attribute key) against the stale
cached DynamicType → `Error reading filter … Attribute: <new-key>` → **those filter
rules are silently dropped**; `updateAppointmentBindings` logged
`Reservation thats is scheduled to delete not found` for a replayed legacy delete.

**No guard on the history side.** The entity-table reader refuses future timestamps;
the CHANGES reader has no equivalent — nothing checks that a replayed row is older than
the moment the cache was loaded.

**Migration-time workaround used on the customer copy (data only, no code):**
`TRUNCATE TABLE changes` before starting Rapla 3. The PRD 058 renames were already
committed correctly; after the truncate the marker-gated boot loads the migrated
entity rows and passes the assertion. (Outcome of that restart not yet verified —
see local deploy note.)

## Goal

1. A `@Tag("db")` DBOperator test that seeds a CHANGES row with `CHANGED_AT` = DB-now +
   2 h for an existing DynamicType (simulating a Rapla 2 local-time row), then renames
   an attribute via `storeAndRemove` and asserts the cache shows the new key. Red before,
   green after.
2. After the fix, an in-place migrated Rapla 2 MariaDB boots without manual SQL, and
   `docs/migration-rapla2-to-rapla3.md` documents whatever one-time step remains.

## Options (decided — see D1)

- **A — Fix the convention at the root:** bind `CHANGED_AT` on INSERT / `WHERE` /
  cleanup through the same `setTimestamp` helper as every other column (true instant,
  `datetimeCal`). Removes the shift; legacy rows are then naturally `< lastUpdated`.
  Upgrade impact on *existing* Rapla 3 DBs (dhbw HSQLDB): rows written before the
  upgrade are read as `offset` hours *older* than intended — safe direction (they were
  already loaded at boot; `getLatest` prefers newer post-upgrade rows). Needs the PRD 054
  reasoning re-read before flipping — the earlier `new Timestamp(toMilli(LDT))` bind
  was reverted once because it missed just-written rows; the reason was the *INSERT*
  being verbatim, so INSERT and reads must flip together.
- **B — Guard only:** in `DBOperator.loadData` (or `HistoryStorage`) discard/ignore CHANGES
  rows with `CHANGED_AT > DB-now` (mirror of `getTimestampOrNow`'s future-guard) with a
  WARN naming the count. Minimal, but leaves the shifted convention in place and only
  covers rows that happen to be "in the future"; a legacy row 30 min old with a 2 h
  offset still masks a Rapla 3 write for 90 min.
- **C — Migration step:** clear CHANGES when `DBOperator.upgradeDatabase` adds schema
  for a legacy DB. Simple, but the "Normal Database upgrade" path has no reliable
  legacy detector today.

**Chosen: A.** OQ1 removed the only argument against it (see below) — the verbatim
convention was never a decision, and the "it was reverted once" caveat applied to
flipping the *readers* alone, which is not what A does. B was dropped: with a 2 h
offset a legacy row 30 min old still masks a Rapla 3 write for 90 min, so the guard
does not close the hole it is aimed at, and after A there is nothing left for it to
catch. C was dropped with it.

## Scope

### In scope
- CHANGES write/read/cleanup binding in `RaplaSQL.HistoryStorage`
- `AbstractTableStorage.getTimestamp`'s `checkCurrent` contract (Phase 2a)
- The reproducing `@Tag("db")` test
- Migration guide update

### Out of scope
- Any change to the UTC-LDT model of entity timestamps (correct as is)
- PRD 058 migration logic (behaved correctly; its assertion only exposed the replay)

## Plan

### Phase 1 — Reproduce ✅
- [x] `rapla-server/src/test/java/org/rapla/storage/dbsql/HistoryTimestampConventionTest.java`,
      `@Tag("db")`, HSQLDB + `TimeZone.setDefault("Europe/Berlin")` (the shift is
      JVM-side, so the test is vacuous in a UTC JVM and asserts a non-UTC zone).
      Two tests: the convention itself (`CHANGES.CHANGED_AT` vs `RAPLA_USER.LAST_CHANGED`
      from the same dispatch — 7200 s apart before the fix) and the migration symptom
      (a legacy row rewritten as Rapla 2 wrote it must not win on `refresh()`).
      The legacy row's wall-clock is derived from the entity's `lastChanged`
      (the true instant), **not** from the stored `CHANGED_AT` — otherwise the
      simulation moves with the convention under test and the fix looks red.

### Phase 2 — Fix ✅
- [x] D1 = A. Five bindings in `RaplaSQL.HistoryStorage` moved onto the shared
      `AbstractTableStorage` helper pair: INSERT (`write`), the `WHERE CHANGED_AT >= ?`
      predicate (`update`), `load`, and `cleanupHistory`'s read + DELETE predicate.
      `loadAll`'s `getTimestamp(rset, 5, false)` needed no change — it was already
      on the shared helper and is now correct instead of offset (see OQ2).
- [x] Revert-check: both tests red against `HEAD`'s `RaplaSQL`, green with the fix.
      Regression lane green — 28 `db`-tagged storage tests (incl. the 7 PRD 054
      cache-drift tests, the ones that would break on an inconsistent round-trip),
      full `rapla-server` lane (489), `rapla-app` `UnifiedRefreshDbIntegrationTest`
      + `DbDatasourceBootIntegrationTest`.

### Phase 2a — Follow-up: the shared reader must not require a connection timestamp ✅
- [x] Reported from the redeploy 2026-08-28: `could not clean up history: NPE … "other" is null`,
      once per boot, no boot blocker — but the daily cleanup never ran and `CHANGES` grew.
      Phase 2 routed `cleanupHistory` onto `AbstractTableStorage.getTimestamp(rset, col, false)`,
      which evaluated `date.isAfter(currentTimestamp) && checkCurrent` — `isAfter` runs first
      and dereferences the null even when the caller asked for no check. `DBOperator`'s
      cleanup job deliberately runs with `history.setConnection(con, null)` (`RaplaSQL:633`),
      so `getConnectionTimestamp()` is null there and the whole job died inside its
      catch-all logger.
- [x] Fixed at the root, in the shared helper (`AbstractTableStorage:354-366`):
      `checkCurrent && date.isAfter(getConnectionTimestamp())` — the timestamp is only
      fetched when it is actually going to be compared. One line, covers every caller.
- [x] Sibling audit: `ArtifactStorage` also reads `getTimestamp(rs, .., false)` and is the
      only other storage set up with a `null` timestamp — but on a different method
      (`saveAllArtifacts`, no reads; the reading `getAllArtifactsMetadata` passes a real
      `getDatabaseTimestamp`). No live second occurrence; the helper fix covers any future one.
- [x] Tests: `AbstractTableStorageTimestampTest` (tier-1 on the helper, `@Tag("db")` for the
      HSQLDB fixture) and `historyCleanupRunsWithNullConnectionTimestamp` in
      `HistoryTimestampConventionTest`, which drives `RaplaSQL.cleanupHistory(con, date)`
      exactly as `DBOperator:148-155` does. Both reproduce the reported NPE verbatim
      against the unfixed helper.

### Phase 3 — Docs ✅
- [x] `docs/migration-rapla2-to-rapla3.md` § Migrating your data: note that an
      in-place 1.8+ upgrade needs no manual `CHANGES` step, with the pre-fix
      `TRUNCATE TABLE changes` workaround named for anyone on an older build.
      `docs/architecture/locking.md` unchanged — the refresh contract is the same,
      only the column encoding changed.

## Tests

Targeted: the new DBOperator test (`mvn -pl rapla-server -am test -Dtest=<Class> -Dtest.excludedGroups=`).
Live check: in-place migrated MariaDB copy boots with legacy CHANGES rows left in place,
`GraphqlKeyMigration` assertion passes, no `Error reading filter` lines.

## Open Questions

- **OQ1 — closed 2026-08-28: PRD 054 never chose it.** `git log -S'Timestamp.valueOf'`
  on `RaplaSQL.java` returns exactly two commits. The convention is born in
  **edd6e65eb** (2026-05-09, *"cleanup: remove redundant *DateTime methods"* — the
  Date → LocalDateTime sweep) as a one-line mechanical substitution:
  `stmt.setTimestamp(5, new Timestamp(timestamp.getTime()))` →
  `stmt.setTimestamp(5, Timestamp.valueOf(timestamp))`. No comment, no rationale —
  with a `Date` the new form would not have compiled, with a `LocalDateTime` it is
  the obvious replacement and a different semantic. The entity tables escaped because
  they go through the helper, which converts explicitly via `toMilli()`.
  **a0acc953e** (2026-05-25, PRD 054) then only aligned the *readers* with that
  already-present INSERT. So the "the earlier bind was reverted once" caveat is about
  flipping readers alone; INSERT + readers together was never tried, which is exactly
  what A does.
- **OQ2 — closed 2026-08-28: CHANGES was the only outlier, and it was not even
  internally consistent.** All SQL touching the table: `loadAllUpdatesSql` (`:2754`),
  `cleanupHistory` select + delete (`:2775`/`:2817`), `deleteAll` (`:2846`),
  `hasHistory` (`:2920`, does not read the stamp). Two further reads were on the
  *other* convention already and were therefore wrong before the fix:
  `HistoryStorage.loadAll`'s early-stop compared `getTimestamp(rset, 5, false)`
  (shared helper) against `supportTimestamp` = `lastRefreshed − HISTORY_DURATION`,
  so the loaded history window per entity was short by the local UTC offset; and
  `cleanupHistory` compared `result.getTimestamp(2).getTime()` against
  `toMilli(date)` with the same shift. Both are correct now without special-casing.
  The other `CHANGED_AT` columns (`WRITE_LOCK` `:3216`, `ARTIFACT` `:3328`) and
  `PreferenceStorage.getPatches` / `readLockTimestamp` were already on the true-instant
  convention — they are untouched.

## Decisions locked

- **D1 (2026-08-28) — Option A: `CHANGES.CHANGED_AT` uses the same convention as every
  other timestamp column.** Write and read both go through
  `AbstractTableStorage.setTimestamp` / `getTimestamp`, i.e. the true instant rendered
  by the driver in the JVM zone. This is not a new convention — it is what Rapla 2 wrote
  (`origin/master` `RaplaSQL:2921` binds `new Timestamp(timestamp.getTime())`, read back
  at `:3018` as `new Date(rs.getTimestamp(5).getTime())`) and what Rapla 3 already does
  everywhere else. Upgrade impact on an existing Rapla 3 DB: rows written before the
  upgrade read as `offset` hours *older* than intended — safe, they were loaded at boot
  anyway and `getLatest` prefers the newer post-upgrade rows.
