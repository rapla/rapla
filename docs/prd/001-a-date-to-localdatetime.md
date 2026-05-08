# PRD 001-A: Replace java.util.Date with java.time.LocalDateTime

**Status:** in-progress — Phases A1–A6 substantially landed; Phase A7 partial (8/8 entity impls migrated, 22 first-touch files cleaned of `java.util.Date` imports, Gson `LocalDateTime` adapter shipped, `LocalCache.conflictLastChanged` migrated, `Export2iCalServlet:211` migrated). Phase A8 just-started — flip the polarity: remove `Date` from the public API (interfaces, factories, ctors) and let `Date` overloads stay only as `default` delegates that convert via `DateTools.toDate(LocalDateTime)`.
**Date:** 2026-05-06 (last update: 2026-05-08)

## Goal — strategy update 2026-05-08

The earlier additive-overload approach (add `LocalDateTime` defaults next to `Date` abstract methods) is now producing dead weight: many entity interfaces and DTOs carry **both** `Date` and `LocalDateTime` getters/setters, with the `LocalDateTime` ones implemented by converting through `DateTools.toLocalDateTime(date)`. As of 2026-05-08, ~80 files in the codebase carry these dual overloads, and ~169 files still import `java.util.Date`.

**Phase A8 — flip the polarity, drop the conversion overloads.** New target: remove `Date`-typed methods from interfaces / public ctors / public factories where every reachable caller can be migrated to the `LocalDateTime` variant in the same change. Where `Date` *must* stay (legacy public API used by integrators outside the codebase, JDBC ResultSet binders, Swing widget APIs), demote the `Date` method to a `default` delegate around the `LocalDateTime` primary so the conversion is colocated and obvious.

**Concrete cleanup targets** (each is its own small PR-style change; no big-bang):
- Entity interfaces: `Timestamp.getCreateDate()`, `LastChangedTimestamp.getLastChanged()/setLastChanged(Date)`, `Permission.getStart/getEnd/setStart/setEnd(Date)`, `Repeating.setEnd(Date)/getExceptions(): Date[]`, `Period.getStart()/getEnd()`, `Allocatable.getAllocateInterval(User, Date)`, `Reservation.getFirstDate()/getMaxEnd()`, `Conflict.getStartDate()`, `RaplaFacade.today()`/`getCurrentTimestamp()`. Each: pick a primary `LocalDateTime` method, demote the `Date` method to `default`-delegate.
- DTOs / wire-format classes: `RemoteStorage.QueryAppointments.start/end`, `UpdateResult` (already migrated, retain `Date` getter as deprecated), `LoginTokens.validUntil` (already migrated).
- Storage operator interfaces: `StorageOperator.getCurrentTimestamp()/today()`, `CachableStorageOperator.getLastRefreshed()/getHistoryValidStart()/getConnectStart()`. Already have `*AsLocalDateTime` accessors; flip primary.
- `RaplaLocale` formatters: `formatDate(Date)`, `formatTime(Date)`, `formatTimestamp(Date)`. Demote to `default` delegating to existing `LocalDate`/`LocalTime`/`LocalDateTime` overloads.

**What this enables**: once the `Date` API is `default`-only (with conversion via `DateTools.toDate`), the next sweep removes the conversions where callers no longer need `Date` — leaving cleaner `LocalDateTime`-native call paths. The **end state** is a codebase where `java.util.Date` appears only at well-known boundaries (JDBC binding, Swing renderers, iCal4j input legacy paths), not in entity / facade / storage / wire APIs.

## Implementation Status

### Phase A1 — Foundation (completed 2026-05-06)

`DateTools` extended with `java.time` overloads:

| Method | Signature |
|--------|-----------|
| `toMilli(LocalDateTime)` | `long` (UTC) |
| `toMilli(LocalDate)` | `long` (UTC midnight) |
| `toDate(LocalDateTime)` | `java.util.Date` |
| `toDate(LocalDate)` | `java.util.Date` (midnight) |
| `getHourOfDay(LocalDateTime/LocalTime)` | `int` |
| `getMinuteOfHour(LocalDateTime/LocalTime)` | `int` |
| `getSecondOfMinute(LocalDateTime)` | `int` |
| `getMinuteOfDay(LocalDateTime)` | `int` |
| `cutDate(LocalDateTime)` | `LocalDateTime` (midnight) |
| `cutDate(LocalDate)` | `LocalDate` (identity) |
| `isMidnight(LocalDateTime)` | `boolean` |
| `addDay(LocalDateTime/LocalDate)`, `subDay`, `addDays(...,long)`, `subDays(...,int)` | overloads |
| `countDays(LocalDate, LocalDate)`, `countDays(LocalDateTime, LocalDateTime)` | `long` |

`SerializableDateTimeFormat` extended with `java.time` overloads (delegate to existing `Date`-based methods via `DateTools.toDate`/`toMilli` to guarantee byte-identical wire format):

| Method | Signature |
|--------|-----------|
| `formatTimestamp(LocalDateTime)` | `String` (ISO 8601, same as `Date` overload) |
| `formatDate(LocalDate)` | `String` |
| `formatTime(LocalTime)` | `String` |
| `parseLocalDateTime(String)` | `LocalDateTime` |
| `parseLocalDate(String)` | `LocalDate` |
| `parseLocalTime(String)` | `LocalTime` |

**`DateToolsLocalDateTimeTest`** (new — 12 tests) verifies:
- `LocalDateTime ↔ millis` round-trip
- `LocalDate → millis` produces UTC midnight
- `Date → LocalDateTime → Date` preserves millis
- `cutDate(LocalDateTime)` returns midnight; `isMidnight` toggles correctly
- `addDay`/`subDay`/`addDays`/`subDays` arithmetic for `LocalDate`
- `countDays(LocalDate, LocalDate)` matches `ChronoUnit.DAYS.between`
- **Wire-format parity**: `formatTimestamp(localDateTime)` produces the **byte-identical** string as `formatTimestamp(date)` for the same instant — same for `formatDate(LocalDate)`/`formatTime(LocalTime)`
- Round-trip parse: `format(t).parseX(...) == t` for all three java.time types

`mvn test` → **35 tests passing** (12 new + 23 existing). No existing test broke; foundation is purely additive.

### Phase A2 — Entity interfaces (additive overloads, completed 2026-05-06)

Rather than the big-bang interface rewrite originally proposed (which would cascade compile errors through hundreds of callers), Phase A2 adopts the **additive overload** approach: add `java.time` accessors as `default` methods on entity interfaces alongside the existing `Date`-returning ones. Existing `Date` callers are unaffected; new code can use the new types.

#### Interfaces extended

| Interface | New `java.time` API |
|-----------|---------------------|
| `LastChangedTimestamp` | `default LocalDateTime getLastChangedAsLocalDateTime()` |
| `Timestamp` | `default LocalDateTime getCreateDateAsLocalDateTime()` |
| `Appointment` | `default void move(LocalDateTime, LocalDateTime)`, `default void moveTo(LocalDateTime)`, `default boolean overlaps(LocalDateTime, LocalDateTime)` (existing `getStartDateTime/getEndDateTime/getMaxEndDateTime` already returned `LocalDateTime`) |
| `Period` | `default LocalDate getStartAsLocalDate()`, `default LocalDate getEndAsLocalDate()`, `default boolean contains(LocalDateTime)` |
| `TimeInterval` | `static of(LocalDateTime, LocalDateTime)` factory (avoids ambiguous-overload trap with the existing `(Date, Date)` constructor when called with `null, null`); `getStartAsLocalDateTime()`, `getEndAsLocalDateTime()` |
| `Repeating` | `default void setEndLocalDateTime(LocalDateTime)` (distinct name — `setEnd(LocalDateTime)` was ambiguous with `setEnd(Date)` at `null` callsites); `default void addException(LocalDateTime)`, `removeException(LocalDateTime)` |
| `Permission` | `default LocalDateTime getStartAsLocalDateTime()`, `getEndAsLocalDateTime()` (read-only; write side `setStart(LocalDateTime)`/`setEnd(LocalDateTime)` would collide with the existing `setStart(null)`/`setEnd(null)` callsites — use `setStart(DateTools.toDate(localDateTime))` from the caller until A7) |
| `Reservation` | `default LocalDateTime getFirstDateAsLocalDateTime()`, `getMaxEndAsLocalDateTime()` |
| `Allocatable` | `default TimeInterval getAllocateInterval(User, LocalDate)` (`LocalDate` variant — `today` is a date, no time component) |
| `RaplaFacade` | `default LocalDate todayAsLocalDate()` |
| `DateTools` (foundation extension) | `static LocalDate toLocalDate(Date)` overload (was missing — only `toLocalDate(long)` existed) |
| `Conflict` (facade interface) | `default LocalDateTime getStartDateAsLocalDateTime()` |
| `RaplaFacade` (continued) | `default Promise<Collection<Reservation>> getReservationsByLocalDateTime(User, LocalDateTime, LocalDateTime, ClassificationFilter[])` — distinct method name (the obvious overload `getReservations(..., LocalDateTime, LocalDateTime, ...)` is ambiguous at `null`-passing call sites in `Application.java:217`, same trap as `Repeating.setEndLocalDateTime`) |
| `Permission` (write side + LocalDate convenience) | `default void setStartLocalDateTime(LocalDateTime)`, `default void setEndLocalDateTime(LocalDateTime)`, `default Date getMaxAllowed(LocalDate)`, `default Date getMinAllowed(LocalDate)` — write-side now covered with distinct names; `LocalDate` convenience for `today` parameters |
| `Appointment.createBlocks` | `default void createBlocksLocalDateTime(LocalDateTime, LocalDateTime, Collection<AppointmentBlock>)` (+ `excludeExceptions` overload) — distinct name |
| `RaplaFacade` (more) | `default Promise<LocalDateTime> getNextAllocatableLocalDateTime(...)`, `default Appointment newAppointmentWithUserLocalDateTime(LocalDateTime, LocalDateTime, User)` |
| `CalendarModel` | `default LocalDate getSelectedLocalDate/setSelectedLocalDate/getStartLocalDate/setStartLocalDate/getEndLocalDate/setEndLocalDate` (6 methods) — selectedDate/startDate/endDate are date-only, so `LocalDate` not `LocalDateTime` |
| `CalendarSelectionModel` | `default void markIntervalLocalDateTime(LocalDateTime, LocalDateTime)` — distinct name |
| `CachableStorageOperator` | `default UpdateResult getUpdateResultLocalDateTime(LocalDateTime)` (×2 overloads), `default LocalDateTime getLastRefreshedAsLocalDateTime/getHistoryValidStartAsLocalDateTime/getConnectStartAsLocalDateTime` (5 methods) — first storage-layer interface migration entry |
| `StorageOperator` | `default LocalDate todayAsLocalDate()`, `default LocalDateTime getCurrentTimestampAsLocalDateTime()` |
| `Repeating.getExceptions` | `default LocalDateTime[] getExceptionsAsLocalDateTime()` array overload (used in `Export2iCalConverter`) |
| `UpdateResult` (concrete class, not interface) | `LocalDateTime getSinceAsLocalDateTime/getUntilAsLocalDateTime()` |
| `PreferencePatch` (concrete) | `LocalDateTime getLastChangedAsLocalDateTime()`, `setLastChangedLocalDateTime(LocalDateTime)` — distinct setter name |
| `AbstractTableStorage` (Phase A5 foundation) | `setTimestampLocalDateTime`, `setDateLocalDateTime`, `getTimestampAsLocalDateTime`, `getTimestampOrNowAsLocalDateTime` — internal protected helpers; SQL layer can now write `LocalDateTime` values via `java.sql.Timestamp` (matching nanosecond precision) without touching the existing `setTimestamp(Date)`/`getTimestamp(Date)` callers |
| `RaplaXMLWriter.printTimestamp` (Phase A4 first call-site refactor) | switched from `stamp.getCreateDate()/getLastChanged()` to `getCreateDateAsLocalDateTime()/getLastChangedAsLocalDateTime()`. Wire format identical (verified via `DateToolsLocalDateTimeTest.formatTimestampLocalDateTimeMatchesDate`) |
| `RaplaXMLWriter` Permission block | switched `p.getStart()/getEnd()` (Date) → `p.getStartAsLocalDateTime().toLocalDate()/getEndAsLocalDateTime().toLocalDate()` then `formatDate(LocalDate)` (overload added in Phase A1). Wire format identical |
| `RaplaXMLReader` Phase A1 reader helpers | new `parseLocalDateTime(String)` and `parseLocalDate(String)` methods paralleling `parseTimestamp`/`parseDate` |
| `PermissionReader` (XML reader) | switched from `permission.setStart(parseDate(s, false))` → `permission.setStartLocalDateTime(parseLocalDate(s).atStartOfDay())` for both start-date and end-date. Round-trip semantics preserved (formatDate is date-only, parseLocalDate.atStartOfDay() = midnight UTC ⇔ original Date(midnight UTC) behavior) |
| `ReservationReader` (XML reader) | `repeating.setEnd(parseDate(s, true))` → `repeating.setEndLocalDateTime(parseLocalDate(s).atStartOfDay())`. The `null`-clear path also switched (`setEndLocalDateTime(null)`) |
| `ReservationReader` exception loop | `repeating.addException(parseDate(dateString, false))` → `repeating.addException(parseLocalDate(dateString).atStartOfDay())` (uses the existing `addException(LocalDateTime)` overload added in Phase A2) |
| Entity impl factories (Phase A3) | `AllocatableImpl.ofLocalDateTime(LocalDateTime, LocalDateTime)`, `ReservationImpl.ofLocalDateTime`, `CategoryImpl.ofLocalDateTime`, `UserImpl.ofLocalDateTime`, `PreferencesImpl.ofLocalDateTime`, `DynamicTypeImpl.ofLocalDateTime` — all 6 entity impls now have `LocalDateTime` factory paralleling their `(Date, Date)` constructor |
| `RaplaXMLReader.TimestampDates` | `getCreateTimeAsLocalDateTime/getChangeTimeAsLocalDateTime` accessors |
| 6 XML readers (Phase A4) | `AllocatableReader`, `PreferenceReader`, `UserReader`, `ReservationReader`, `CategoryReader`, `DynamicTypeReader` — all switched from `new EntityImpl(ts.createTime, ts.changeTime)` (Date) → `EntityImpl.ofLocalDateTime(ts.getCreateTimeAsLocalDateTime(), ts.getChangeTimeAsLocalDateTime())`. The central XML reader timestamp pipeline is now fully `LocalDateTime` from `parseTimestamp` through to entity construction (entity stores still Date internally; that's Phase A3 cleanup work) |
| `SerializableDateTimeFormat` | new `formatDate(LocalDateTime, boolean adaptDay)` overload paralleling `formatDate(Date, boolean)` (used by `ReservationWriter` for whole-day end-of-day cut-back) |
| **`ReservationWriter.java` Phase A4+A6+A7 second file complete** | Switched 6 sites: appointment start-date/end-date/start-time/end-time (whole-day + non-whole-day branches), repeating end-date, exception loop. Now uses `appointment.getStartDateTime()`/`getEndDateTime()` and `r.getExceptionsAsLocalDateTime()` directly. `import java.util.Date` removed — second file in codebase to complete A6+A7. |
| `UpdateEvent` (concrete) | `LocalDateTime getLastValidatedAsLocalDateTime()`, `setLastValidatedLocalDateTime(LocalDateTime)` — distinct setter name |
| **`RaplaEventsController.java` Phase A6 — REST endpoint** | `@RequestParam Date start/end` → `LocalDateTime start/end`. Spring's `@DateTimeFormat(ISO.DATE_TIME)` parses both with identical wire format (ISO 8601). Conversion at the endpoint boundary dropped after migrating `RaplaEventsRestPage` (next row). Third file in codebase fully `Date`-free |
| `StorageOperator` interface (more) | `default Promise<AppointmentMapping> queryAppointmentsByLocalDateTime(...LocalDateTime, LocalDateTime,...)` overload — distinct method name |
| **`RaplaEventsRestPage.java` Phase A6+A7 — fourth complete file** | `@QueryParam("start") Date start/end` → `LocalDateTime`. Switched call to `operator.queryAppointmentsByLocalDateTime(...)`. Now flows `LocalDateTime` end-to-end: HTTP request param → controller (`LocalDateTime`) → page (`LocalDateTime`) → operator overload (`LocalDateTime`) → existing `Date`-based impl. The endpoint-boundary conversion that was added one iteration ago is now gone — the prediction in that PRD entry held. `import java.util.Date` removed |
| `BuildStrategy` interface (calendarview) | `default void buildLocalDate(BlockContainer, List<Block>, LocalDate)` — distinct method name |
| **`Timeslot.java` Phase A6+A7 — fifth complete file** | `formatTime(new Date(DateTools.toTime(hour, minute, 0)))` → `formatTime(LocalTime.of(hour, minute, 0))`. Used the existing `formatTime(LocalTime)` overload from Phase A1. Removed `import java.util.Date` and `import org.rapla.components.util.DateTools` (no longer needed) |
| `ReservationEdit` interface | `default Promise<Void> addAppointmentLocalDateTime(LocalDateTime, LocalDateTime)` — distinct name |
| `CalendarOptions` interface | `default int getFirstDayOfWeek(LocalDate today)` — `today` is date-only |
| `PermissionExtension` interface | `default boolean hasAccessLocalDateTime(Entity, User, AccessLevel, LocalDateTime start, LocalDateTime end, LocalDate today, boolean)` — distinct name; mixed `LocalDateTime`/`LocalDate` for the appropriately-typed parameter |
| `IRowScale` (calendarview swing) | `default int getYCoord(LocalTime)` — distinct name |
| `RaplaResources.calendarweek` | `LocalDate calendarweek(LocalDate)` overload |
| **`RaplaAuthentificationService.java` Phase A6+A7 — sixth complete file** | New-user creation site: `Date now = operator.getCurrentTimestamp()` + `new UserImpl(now, now)` → `LocalDateTime now = operator.getCurrentTimestampAsLocalDateTime()` + `UserImpl.ofLocalDateTime(now, now)`. Both APIs were added in earlier iterations (Phase A2 storage interface, Phase A3 entity factories) — the migration shows the `LocalDateTime` infrastructure clicking together end-to-end. `import java.util.Date` removed |
| `RaplaLocale` interface (foundation) | `default String formatDate(LocalDate)`, `formatDate(LocalDateTime)`, `formatTimestamp(LocalDateTime)`, `formatDateLong(LocalDateTime)`, `formatTime(LocalTime)`, `formatTime(LocalDateTime)` — 6 default methods that delegate to existing `Date`-based abstract methods. Big enabler for client-side A6 work because every Swing/HTML formatting site can now switch without touching `RaplaLocale` impl |
| **`ConflictText.java` Phase A6+A7 — seventh complete file** | `Date startDate = conflict.getStartDate()` + `raplaLocale.formatDate(Date)` + `DateTools.cutDate(startDate).equals(startDate)` → `LocalDateTime startDate = conflict.getStartDateAsLocalDateTime()` + `raplaLocale.formatDate(LocalDateTime)` + `startDate.toLocalDate().atStartOfDay().equals(startDate)`. Removed `java.util.Date` and `DateTools` imports |
| `PeriodModel` interface | `default Period getPeriodFor(LocalDate)`, `default Period getPeriodFor(LocalDateTime)` |
| `RaplaLocale` (more) | `default LocalDate toRaplaLocalDate(int, int, int)` |
| **`RaplaDateRenderer.java` Phase A6+A7 — eighth complete file** | `Date date = raplaLocale.toRaplaDate(year, month, day)` + `periodModel.getPeriodFor(date)` → `LocalDate date = raplaLocale.toRaplaLocalDate(year, month, day)` + `periodModel.getPeriodFor(date)` (uses the new `LocalDate` overloads). `import java.util.Date` removed |
| `PermissionController` (concrete) | `canAllocate(Allocatable, User, LocalDate)`, `isRequestOnly(Allocatable, User, LocalDate)` overloads |
| **`ComplexTreeCellRenderer.java` Phase A6+A7 — ninth complete file** | `Date today = raplaFacade.today()` → `LocalDate today = raplaFacade.todayAsLocalDate()`. Uses the existing `RaplaFacade.todayAsLocalDate` (Phase A2) and the new `PermissionController.canAllocate(LocalDate)` / `isRequestOnly(LocalDate)`. `import java.util.Date` removed |
| `ReservationView.Presenter` interface | `default void timeChangedLocalDateTime(LocalDateTime, LocalDateTime)` — distinct name |
| `Block` interface (calendarview) | `default LocalDateTime getStartAsLocalDateTime/getEndAsLocalDateTime()` |
| `ModifiableTimestamp` interface | `default void setLastChangedLocalDateTime(LocalDateTime)`, `setCreateDateLocalDateTime(LocalDateTime)` — distinct names |
| **`SaveUndo.java` Phase A6+A7 — tenth complete file** | Switched 2 sites: `Date lastChanged = ...getType().getLastChanged()` → `LocalDateTime lastChanged = ...getLastChangedAsLocalDateTime()`; `Date version = ...getLastChanged()`/`setLastChanged(version)` → `LocalDateTime version = ...getLastChangedAsLocalDateTime()`/`setLastChangedLocalDateTime(version)`. `import java.util.Date` removed |
| stale-import cleanup | `AttributeDefaultConstraints.java` — attempted Date-import removal but found unused-import filter was wrong; restored. (Lesson: my heuristic for "no body Date use" missed casts like `(Date) attribute.defaultValue()`) |
| `RaplaLocale.getWeekday(LocalDate)/(LocalDateTime)` | new default overloads |
| **`HTMLInfo.java` Phase A6+A7 — eleventh complete file** | `Date createTime/lastChangeTime` → `LocalDateTime` via `getCreateDateAsLocalDateTime/getLastChangedAsLocalDateTime`; `raplaLocale.formatTimestamp(LocalDateTime)` (Phase A6 RaplaLocale overload). `import java.util.Date` removed |
| **`PeriodInfoUI.java` Phase A6+A7 — twelfth complete file** | `Date periodStart/periodEnd = period.getStart()/getEnd()` → `LocalDate ... = period.getStartAsLocalDate()/getEndAsLocalDate()`; `loc.getWeekday(Date)/formatDate(Date)/DateTools.subDay(Date)` → `LocalDate` overloads (all added in earlier iterations). `import java.util.Date` removed |
| `DateTools.formatTime(long)` overload | new — accepts millis directly, no `new Date(...)` allocation |
| **`RaplaLock.java` Phase A6+A7 — thirteenth complete file** | `DateTools.formatTime(new Date(lockTime))` → `DateTools.formatTime(lockTime)` (new long overload). `import java.util.Date` removed |
| `JavaJsonSerializer.serializeDate(LocalDateTime)` | new overload (REST client) |
| `RaplaXMLReader.getReadLocalDateTime()` | new — paralleling `getReadTimestamp(): Date` |
| **`DynamicTypeReader.java` Phase A6+A7 — fourteenth complete file** | `Date date = getReadTimestamp()` + `new CategoryImpl(date, date)` → `LocalDateTime date = getReadLocalDateTime()` + `CategoryImpl.ofLocalDateTime(date, date)`. `import java.util.Date` removed |
| **`PeriodReader.java` Phase A6+A7 — fifteenth complete file** | `new AllocatableImpl(new Date(), new Date())` → `AllocatableImpl.ofLocalDateTime(getReadLocalDateTime(), getReadLocalDateTime())`. `import java.util.Date` removed |
| `CalendarPlugin` interface | `default LocalDate calcNextLocalDate(LocalDate)`, `calcPreviousLocalDate(LocalDate)` — distinct names |
| **`ExchangeAppointmentStorage.java` Phase A6+A7 — sixteenth complete file** | `new SerializableDateTimeFormat().formatTimestamp(operator.getCurrentTimestamp())` → `formatTimestamp(operator.getCurrentTimestampAsLocalDateTime())`. `import java.util.Date` removed |
| `SignedToken` | `newToken(String, LocalDateTime)`, `checkToken(String, String, LocalDateTime)` — distinct overloads (still has both Date and LocalDateTime variants for compat) |
| `LoginTokens` | `static ofLocalDateTime(String, LocalDateTime)` factory + `LocalDateTime getValidUntilAsLocalDateTime()` |
| **`TokenHandler.java` Phase A6+A7 — seventeenth complete file** | Three call sites in `validateUser`, `generateAccessToken`, `regenerateRefreshToken` switched: `Date now = operator.getCurrentTimestamp()` → `LocalDateTime`. `getSignedToken(String, Date)` → `getSignedToken(String, LocalDateTime)` (replaced, not added — only one internal caller). `new LoginTokens(token, validUntil)` → `LoginTokens.ofLocalDateTime(token, validUntil)`. `Date validUntil = new Date(now.getTime() + 1000L * validityInSeconds)` → `now.plusSeconds(validityInSeconds)`. `import java.util.Date` removed. Auth integration tests all 4/4 passing |
| **`UserReader.java` Phase A6+A7 — eighteenth complete file** | `Date createTime = group.getCreateDate()` + `Date categoryCreateTime = dynamicTypeReader.getReadTimestamp()` → `LocalDateTime` via the existing `getCreateDateAsLocalDateTime` (Phase A2) and `getReadLocalDateTime` (added previous iteration). `import java.util.Date` removed |
| `SerializableDateTimeFormat.parseLocalDateTime(String, String)` | new (separate date+time string overload) |
| `RaplaXMLReader.parseLocalDateTime(String, String)` | new — paralleling existing `parseDateTime` |
| **`ReservationReader.java` Phase A6+A7 — nineteenth complete file** | Appointment date/time parse: `Date start/end` from `parseDateTime(date, time)` or `parseDate(date, true/false)` → `LocalDateTime` via new `parseLocalDateTime(date, time)` and `parseLocalDate(date).atStartOfDay()`/`.plusDays(1)` (the `fillDate=true` semantics is `day+=1` per source). `new AppointmentImpl(start,end)` → `AppointmentImpl.ofLocalDateTime(start,end)`. `import java.util.Date` removed. Tested via 7/7 `RaplaSpringBootApplicationTest` (XML roundtrip) |
| `ConflictImpl.ofLocalDateTime(String, LocalDateTime, LocalDateTime)` | new factory + `initFromId` extracted from constructor body |
| **`ConflictReader.java` Phase A6+A7 — twentieth complete file** | `Date today/lastChanged` → `LocalDateTime` via `getReadLocalDateTime()` and `readTimestamps(atts).getChangeTimeAsLocalDateTime()`. `new ConflictImpl(...)` → `ConflictImpl.ofLocalDateTime(...)`. `import java.util.Date` removed. 7/7 RaplaSpringBootApplicationTest passing |
| **`RaplaMainReader.java` Phase A6+A7 — twenty-first complete file** | `Date startDate/endDate = parseDate(...,false/true)` → `LocalDateTime` via `parseLocalDate(...).atStartOfDay()`/`.plusDays(1)`; `new TimeInterval(...)` → `TimeInterval.of(LocalDateTime, LocalDateTime)`. `startDate.getTime()` → `DateTools.toMilli(LocalDateTime)`. `import java.util.Date` removed |
| **`RaplaMainWriter.java` Phase A6+A7 — twenty-second complete file** | `invalidateInterval.getStart/getEnd` (Date) → `getStartAsLocalDateTime/getEndAsLocalDateTime`; `new Date(0)` → `LocalDateTime.of(1970, 1, 1, 0, 0)`; `formatDate(Date)` → `formatDate(LocalDate)` via `.toLocalDate()`. `import java.util.Date` removed. 7/7 tests still passing |
| `RaplaSQL` (Phase A5 first batch — 11 sites) | `Allocatable` insert (lines 1292-3); `Reservation` insert (lines 1406-7); `Category` insert (line 1152); `Appointment` insert (lines 1758-9); `Permission` insert (lines 1648-9); `Repeating` insert end-date (line 1790); `DynamicType` insert (line 2034); `User` insert (lines 2463-4); `Conflict` insert (line 2575); exception loop (lines 1975-8); `Permission` read (lines 1679-80). All 11 sites now read/write entity timestamps as `LocalDateTime` via the `setTimestampLocalDateTime`/`setDateLocalDateTime`/`getDateAsLocalDateTime` helpers — same `java.sql.Timestamp` storage path, byte-identical column data |
| `AbstractTableStorage.getDateAsLocalDateTime` | new read helper paralleling existing `getDate` |
| `AppointmentImpl.ofLocalDateTime` | static factory `(LocalDateTime, LocalDateTime) → AppointmentImpl` — for future SQL/XML readers when their callers all accept the new type |
| `UpdateDataManager` interface | `default UpdateEvent createUpdateEventLocalDateTime(User, LocalDateTime)`, `createUpdateEventReservationsLocalDateTime(User, LocalDateTime)` — distinct names |
| **Dead-code cleanup** in `RemoteStorageImpl.java` | 3 unused `Date` variables removed: `Date serverTime = operator.getCurrentTimestamp()` (line 91 area), `Date repositoryVersion = operator.getCurrentTimestamp()` (line 132 area), `Date currentTimestamp = operator.getCurrentTimestamp()` (line 281 area). All assigned-but-never-read; no behavior change. Each call into `operator.getCurrentTimestamp()` was a wasted operation. Reduces `Date` ref count for future migration |
| **Phase A6 — `FacadeImpl.java` partial migration (5 entity-creation sites)** | `newReservation` (line 708-9), `newAllocatable` (line 740-1), `newCategory` (line 784-5), `newDynamicType` (line 799-800), `newUser` (line 879-80) — all switched from `Date now = operator.getCurrentTimestamp(); new EntityImpl(now, now)` → `LocalDateTime now = operator.getCurrentTimestampAsLocalDateTime(); EntityImpl.ofLocalDateTime(now, now)`. File still has other Date usages (`today()` return type, etc.) so import stays — but the entity-creation pipeline is now `LocalDateTime` end-to-end. 7/7 RaplaSpringBootApplicationTest passing |
| **Phase A6 — `UpdateDataManagerImpl.java` (1 site)** | Appointment-interval loop: `Date start = app.getStart(); Date end = app.getMaxEnd(); new TimeInterval(start, end)` → `LocalDateTime start = app.getStartDateTime(); LocalDateTime end = app.getMaxEndDateTime(); TimeInterval.of(start, end)` |
| **Phase A6 — `AbstractCachableOperator.java` (1 site)** | `newPreferences()`: `Date now = getCurrentTimestamp(); new PreferencesImpl(now, now)` → `LocalDateTime now = getCurrentTimestampAsLocalDateTime(); PreferencesImpl.ofLocalDateTime(now, now)` |
| **Phase A6 — `RaplaSQL.java` SQL read paths (4 sites)** | `AllocatableImpl` load (line 1304-7): `Date createDate/lastChanged = getTimestampOrNow(rset, ...)` + `new AllocatableImpl(...)` → `LocalDateTime` + `AllocatableImpl.ofLocalDateTime(...)`. Same for `ReservationImpl` load (line 1422-4), `UserImpl` load (line 2483-6), `PreferencesImpl` load (line 2348-53). The Phase A5 `getTimestampOrNowAsLocalDateTime` helper added earlier now has its first 4 callers. The `PreferencesImpl` load also migrated `lastChanged.before(lastUpdateDate)` Date comparison → `LocalDateTime.isBefore` and `setLastChanged(Date)` → `setLastChangedLocalDateTime(LocalDateTime)`. 7/7 RaplaSpringBootApplicationTest passing |
| **Phase A6 — `LocalAbstractCachableOperator.java` partial migration (7 sites in `createDefaultSystem` + 1 in template-creation loop)** | `Date now = getCurrentTimestamp()` → `LocalDateTime now = getCurrentTimestampAsLocalDateTime()`; `new PreferencesImpl(now, now)` / `new CategoryImpl(now, now)` (×4: groups, periods, holidays, per-group loop) / `new UserImpl(now, now)` / `new AllocatableImpl(now, now)` → corresponding `EntityImpl.ofLocalDateTime(now, now)` factories. Template-creation loop in `templateMap` also migrated. 7/7 RaplaSpringBootApplicationTest passing — exercises the default-system bootstrap path |
| **Phase A6 — `NotificationService.java` (1 site)** | `new ReservationImpl(new Date(), new Date())` (anonymous reservation fallback) → `LocalDateTime now = LocalDateTime.now(); ReservationImpl.ofLocalDateTime(now, now)` |
| `AbstractTableStorage.getConnectionTimestampAsLocalDateTime()` | new accessor paralleling `getConnectionTimestamp()` |
| **Phase A6 — `RaplaSQL.java` Conflict (2 sites)** | Conflict delete loop: `Date connectionTimestamp = getConnectionTimestamp(); new ConflictImpl(id, ts, ts)` → `LocalDateTime ts = getConnectionTimestampAsLocalDateTime(); ConflictImpl.ofLocalDateTime(id, ts, ts)`. Conflict load: `Date timestamp = getTimestamp(rset, 6, true); Date today = getConnectionTimestamp(); new ConflictImpl(id, today, timestamp)` → `LocalDateTime` via `getTimestampAsLocalDateTime` + `getConnectionTimestampAsLocalDateTime` + `ConflictImpl.ofLocalDateTime`. 7/7 RaplaSpringBootApplicationTest passing |
| **Phase A6 — `ConflictFinder.java` (1 site)** | Dummy-conflict construction in `getConflicts(ref)`: `Date dummyLastChanged = new Date(); Date date = new Date(); new ConflictImpl(ref.getId(), date, dummyLastChanged)` → `LocalDateTime ... = LocalDateTime.now(); ConflictImpl.ofLocalDateTime(ref.getId(), date, dummyLastChanged)`. Compile passes; not yet test-verified — `mvn test` should be run when the surrounding work is finalised |

**Status update 2026-05-07 (continuation):**
- `LocalCache.java:483` ✅ migrated. `Map<String, Date> conflictLastChanged` → `Map<String, LocalDateTime>`. Three call sites (`put` in `add()`, `fillConflictDisableInformation` and `getDisabledConflicts`) use `getLastChangedAsLocalDateTime` / `setLastChangedLocalDateTime` / `ConflictImpl.ofLocalDateTime`. `Date.after(...)` swapped for `LocalDateTime.isAfter(...)`. Wire format unchanged (UTC).
- `ConflictFinder.java` lines 328 and 703 (4-arg `new ConflictImpl(allocatable, app1, app2, today)`): `ConflictImpl.ofLocalDateTime(Allocatable, Appointment, Appointment, LocalDateTime)` factory exists (added earlier). The migration is blocked at the constructor seam — `ConflictFinder(AllocationMap, Date today, ...)` threads `Date today` through ~20 internal sites. Migrating means changing the public ctor signature (caller in `LocalAbstractCachableOperator`) and threading `LocalDateTime` through. Deferred — voluminous, no functional gain (wire format identical).
- `SynchronisationManager.java:787` — body is unreachable (`if (true) return new LinkedHashSet<>();` at line 781). Not worth migrating until the dead-code branch is reactivated.
- `Export2iCalServlet.java:211` ✅ migrated 2026-05-08. Added `DateTools.add(LocalDateTime, IncrementSize, int)` and `DateTools.add(LocalDate, IncrementSize, int)` overloads (the existing `setStartLocalDate/setEndLocalDate` defaults on `CalendarModel` from Phase A2 already covered the consumer side). Site now uses `facade.todayAsLocalDate()` and `calModel.setStartLocalDate/setEndLocalDate(LocalDate)`. `new Date()` allocation gone. Removed unused `Date now = new Date()` lines. Note: this file still has a different `Date firstPluginStartDate` field for HTTP `Last-Modified` header logic — that's a separate site, deferred.

### 2026-05-08 (continued) — Additional `LocalDateTime` overloads on framework interfaces

- `DateTools.getWeekInYear(LocalDate, Locale)` and `getWeekInYear(LocalDateTime, Locale)` — overloads delegating to existing `Date` variant. Used by `RaplaResources.calendarweek(LocalDate)` which now no longer round-trips through `Date`.
- `TimeZoneConverter.fromRaplaTime(TimeZone, LocalDateTime)` and `toRaplaTime(TimeZone, LocalDateTime)` — `default` methods on the interface, delegate to existing `long`-millis variants. Adds a `LocalDateTime`-native path for callers without forcing the `Date` boundary.

### 2026-05-08 (continued) — `ConflictImpl` and `CalendarModelConfigurationImpl` field migration

`ConflictImpl.startDate` + `lastChanged` migrated from `Date` to `LocalDateTime`. New `LocalDateTime` ctor added; `Date` ctor delegates. New `LocalDateTime` accessors (`getStartDateAsLocalDateTime`, `getLastChangedAsLocalDateTime`, `getCreateDateAsLocalDateTime`, `setLastChangedLocalDateTime`, `setCreateDateLocalDateTime`). All `Date` getters/setters preserved as boundary converters. The `Allocatable.getLastChanged().after(...)` arithmetic in `getLastChanged(allocatable, app1, app2)` stays in `Date` for now since it operates on entity getters that still return `Date`.

`CalendarModelConfigurationImpl.startDate`/`endDate`/`selectedDate` migrated from `Date` to `LocalDateTime`. New `LocalDateTime` ctor added (parallel to `Date` ctor). New `LocalDateTime` accessors. `Date` getters retained as boundary converters.

`RaplaCalendarSettingsReader.startDate`/`endDate`/`selectedDate` migrated to `LocalDateTime` fields. The XML reader now passes `LocalDateTime` directly to the new `CalendarModelConfigurationImpl` ctor. Wire format unchanged (XML ISO-8601 strings parse to identical millis).

### 2026-05-08 (continued) — Phase A7 cleanups: `PreferencePatch`, `UpdateResult`, `LoginTokens` field migration

Three more entity / DTO classes migrated from `Date` field types to `LocalDateTime`:

| Class | Field(s) | Notes |
|-------|----------|-------|
| `PreferencePatch` | `lastChanged` | Was `Date`. Now `LocalDateTime`; `getLastChanged()`/`setLastChanged(Date)` retained as boundary converters. |
| `UpdateResult` | `since`, `until` (and `HistoryEntry.timestamp`) | Constructor now accepts `Date` (legacy) and `LocalDateTime`. Internal storage `LocalDateTime`. `getSince()`/`getUntil()` still return `Date`. |
| `LoginTokens` | `validUntil` | Was `Date`. `LocalDateTime` field; new `LocalDateTime` ctor; `Date` ctor + `Date getValidUntil()` retained. `expiresIn` recomputation uses `DateTools.toMilli(LocalDateTime)`. |

Pattern: keep `Date` boundary API to avoid breaking callers; migrate field type + arithmetic; provide `LocalDateTime` accessors with distinct names. Wire format unchanged (UTC millis identical).

### 2026-05-08 (continued) — Gson dead-code removal: `GsonParserWrapper`, `JsonMergePatch`, plus `HTTPWithJsonConnector` migrated to Jackson

Three Gson cleanups in this iteration:
1. **Deleted** `org.rapla.rest.gson.GsonParserWrapper` and `org.rapla.rest.gson.JsonMergePatch` — both became unreachable after `JsonParserWrapper.factory` swapped to Jackson default; only self-referenced before deletion.
2. **Migrated** `HTTPWithJsonConnector` (deprecated, but on classpath) and chain (`HTTPJsonConnector` extends it) from raw Gson API (`Gson`, `GsonBuilder`, `JsonObject`, `JsonElement`, `JsonParser`) to Jackson (`ObjectMapper`, `JsonNode`, `ObjectNode`). Public method signatures shifted from `JsonObject`/`JsonElement` to `ObjectNode`/`JsonNode` — `@Deprecated` class so consumers migrating with it.
3. **Migrated** `JsonReaderTest` from Gson to Jackson. Required enabling `JsonReadFeature.ALLOW_SINGLE_QUOTES` because the test JSON uses single-quoted keys (Gson default; Jackson strict).

Remaining Gson on the client classpath: `RestAPIExample.java` (test/example main, not a real test) still uses Gson API directly. Production code on the client side now has zero direct Gson API calls.

### 2026-05-08 — Jackson `JavaTimeModule` registered (PRD 001 Phase 9 unblocking)

`rapla-core/pom.xml` now depends on `jackson-datatype-jsr310` (provided scope, Spring Boot BOM-managed). `JacksonParserWrapper` registers `new JavaTimeModule()` and disables `WRITE_DATES_AS_TIMESTAMPS`. Wire format for `LocalDateTime` is now ISO-8601 strings, matching the Gson adapter from earlier this session. Both serializers can now round-trip `LocalDateTime`/`LocalDate`/`LocalTime` entity fields, which means PRD 001 Phase 9 (Gson → Jackson swap for SQL history) is no longer blocked by serialization concerns.

### 2026-05-08 — `DateTools` `long` overloads + `AppointmentImpl`/`AppointmentBlock` cleanup

`DateTools.formatDateTime(long)` and `DateTools.formatDate(long)` overloads added — both delegate to existing `Date`-based methods. Three caller sites migrated to drop `new Date(millis)` allocations:
- `AppointmentBlock.toString()` — was `formatDateTime(new Date(start/end))`, now `formatDateTime(start/end)`
- `AppointmentImpl.f(long)` — debug helper, was `formatDateTime(new Date(n))`, now `formatDateTime(n)`
- `AppointmentImpl.fe(long)` — debug helper, was `formatDate(new Date(n))`, now `formatDate(n)`

These are pure cleanup — same behavior, fewer Date allocations on hot debug-format paths. Tests green (`DateToolsTest`, `DateToolsLocalDateTimeTest`, `TimeIntervalLocalDateTimeTest` — 25/25).

### 2026-05-08 — `TimeInterval` field migration attempted, reverted

Migrated `TimeInterval` from `Date start, end` fields to `LocalDateTime` and removed the `Date` ctor. Reverted on user request — the ctor removal cascaded into many `new TimeInterval(null, null)` callsites needing `(LocalDateTime) null` casts (ambiguous overload trap). Re-adding the `Date` ctor brings the same ambiguity. Cleaner future approach: do the field migration AND add a `LocalDateTime` ctor as a parallel-named factory only (no `Date` ctor change), or migrate all `new TimeInterval(...)` callsites in one sweep. **Status: deferred.** Wire format and external API unchanged.

### 2026-05-07 — Phase A2 entity-impl factories: ConflictImpl 4-arg variants

`ConflictImpl.ofLocalDateTime(Allocatable, Appointment, Appointment, LocalDateTime)` and `ConflictImpl.ofLocalDateTime(Allocatable, Appointment, Appointment, LocalDateTime, String)` factories added — the 2 deferred `ConflictFinder.java` sites (line 328, 703) can now be migrated whenever `today` becomes a `LocalDateTime`. Currently `today` is propagated as `Date` through `ConflictFinder`'s public API (`new ConflictFinder(AllocationMap, Date today, ...)`), so the migration must happen at the constructor seam — done in a future pass.

### 2026-05-07 — Phase A7 entity-impl field migration (8 of 8 done) + Gson LocalDateTime adapter

**All 8 entity impls migrated.** Field types now use `LocalDateTime` directly; `Date`-typed accessors retained as boundary converters via `DateTools.toDate(LocalDateTime)`.

**Gson `LocalDateTime` / `LocalDate` / `LocalTime` TypeAdapters registered in `GsonParserWrapper.defaultGsonBuilder()`** — needed because `LocalDateTime` is a value-type with private fields (`#date`, `#time`) that Gson's reflective serializer cannot access on JDK 17+. Without these, server bootstrap fails at first SQL/EntityHistory write with `Failed making field 'java.time.LocalDateTime#date' accessible`. The new adapters serialize via `ISODateTimeFormat` (same wire format as `Date` adapter) and via ISO-8601 `toString()` for `LocalDate`/`LocalTime`. Risk #1 from PRD plan now mitigated.

**Full reactor test run: 23 tests passing, BUILD SUCCESS.**

**Migrated `Date` → `LocalDateTime` field types in entity impls:**

| Entity | Fields migrated | Notes |
|--------|----------------|-------|
| `CategoryImpl` | `createDate`, `lastChanged` | Trivial — only the createDate/lastChanged pair |
| `UserImpl` | `createDate`, `lastChanged` | Same pattern |
| `PreferencesImpl` | `createDate`, `lastChanged` | + setLastChanged auto-fills createDate if null — preserved in both Date and LocalDateTime overloads |
| `DynamicTypeImpl` | `createDate`, `lastChanged` | Same pattern |
| `AllocatableImpl` | `createDate`, `lastChanged` | + lastChanged falls back to createDate if null — preserved |
| `ReservationImpl` | `createDate`, `lastChanged` | + null createDate defaults to `LocalDateTime.now()` (was `new Date()`) |
| `PermissionImpl` | `pStart`, `pEnd` | Most complex: `getMinAllowed(Date)/getMaxAllowed(Date)` and `covers(...)` use `getTime()` arithmetic — migrated to `DateTools.toMilli(LocalDateTime)` for the comparison sites; the Date-typed parameters and Date-typed returns at the public API are preserved |
| `AppointmentImpl` | ✅ migrated 2026-05-07 | `Date start, Date end` fields → `LocalDateTime`. ~30 `.getTime()` arithmetic sites switched to `DateTools.toMilli(LocalDateTime)`. Added `DateTools.fillDate(LocalDateTime)` and `DateTools.getWeekday(LocalDateTime)` overloads. Wire format unchanged (UTC millis identical). |
| `RepeatingImpl` | ✅ migrated 2026-05-07 | `Date end` field → `LocalDateTime`; `Set<Date> exceptions` → `Set<LocalDateTime>`. Public `getExceptions(): Date[]` getter still returns `Date[]` (boundary converter). Override `addException(LocalDateTime)`/`removeException(LocalDateTime)` from interface defaults; `setEndLocalDateTime(LocalDateTime)` overrides default. Full reactor compiles. |

**Pattern applied** (uniform across the 6 done):

```java
// before:
private Date createDate;
private Date lastChanged;
public Date getCreateDate() { return createDate; }
public void setCreateDate(Date d) { this.createDate = d; }

// after:
private LocalDateTime createDate;          // field is LocalDateTime
private LocalDateTime lastChanged;
public Date getCreateDate() {              // legacy getter still returns Date
    return createDate == null ? null : DateTools.toDate(createDate);
}
public LocalDateTime getCreateDateAsLocalDateTime() { return createDate; }   // new accessor
public void setCreateDate(Date d) {        // legacy setter still accepts Date
    this.createDate = d == null ? null : DateTools.toLocalDateTime(d);
}
public void setCreateDateLocalDateTime(LocalDateTime d) { this.createDate = d; }  // new setter

// + a parallel ctor (LocalDateTime, LocalDateTime) alongside (Date, Date)
// + ofLocalDateTime(LocalDateTime, LocalDateTime) factory delegates to the new ctor
// + ReferenceHandler.java:295 fixed for the now-ambiguous (null, null) ctor call
//   by explicit cast: new AllocatableImpl((LocalDateTime) null, (LocalDateTime) null)
// + MyCustomConnector.java:68 unrelated fix while in the area: parallel session
//   changed RemoteAuthentificationService.login(...) signature to take LoginCredentials
//   record; updated caller to match
```

**Wire format unchanged** (`Date.getTime()` ↔ `DateTools.toMilli(LocalDateTime)` produce identical millis at UTC). All XML/SQL serialization paths still work — they go through the `Date` getter which is now a converter wrapper. **`SpringRaplaClientTest` passes** after `mvn -pl rapla-bom,rapla-core,rapla-client install -DskipTests`.

### 2026-05-07 — Phase A7 status (entity-impl field migration ~75% done)

**169 files still import `java.util.Date`** (down from the original 186; ~9% reduction in absolute count, but the strategic interfaces and most-used readers/writers/factories are clean).

**The remaining files split into three categories:**

1. **Legacy interfaces with `LocalDateTime` overload added but `Date` overload kept** (~80 files): the additive-overload approach means the `Date` overload stays until all callers migrate. Real cleanup is removing the `Date` overload — but only after every caller switches to `LocalDateTime`.

2. **`Date` field types in entity impls / DTOs / wire format-anchored classes** (~50 files): `AppointmentImpl.start`/`end`, `RepeatingImpl.end`, `Permission.startDate`/`endDate`, `LoginTokens.validUntil`, `UpdateEvent.lastValidated`, etc. Migrating the field type means simultaneously updating every getter/setter caller. Phase A7 work, scoped per-class.

3. **Date arithmetic / mutable-Date sites** (~40 files): `Export2iCalServlet.java`, `SynchronisationManager.java`, `LocalCache.java`, calendar-printing classes — these treat `Date` as a long-millis carrier and use `getTime()` arithmetic. Migration requires reasoning about the arithmetic in `Instant` or `Duration` terms. Per-site judgement required.

**The 22 fully-clean files** validate the migration approach end-to-end (XML reader/writer, REST endpoint, server-side iCal export, entity-creation paths). The remaining work is voluminous but mechanical — same patterns repeated across more files. **PRD 001-A's strategic goal (Phase 9 of PRD 001 — Gson → Jackson switch) is unblocked**: the entity interfaces and core readers/writers now have `LocalDateTime` accessors throughout, so a Jackson `jackson-datatype-jsr310` swap is feasible without touching application logic.

**Lesson learned 2026-05-06 — don't migrate read sites where the consumer is still `Date`-typed.** Tried `Date start = getDate(rset, 3)` → `LocalDateTime startLdt = getDateAsLocalDateTime(rset, 3); Date start = DateTools.toDate(startLdt)` and reverted. The `LocalDateTime` intermediate adds zero value when the next line passes the value into a `Date`-taking constructor (`new AppointmentImpl(start, end)`). The whole chain becomes Date → LocalDateTime → Date. Real migration of these sites must wait for the consumer (e.g. `AppointmentImpl` constructor) to accept `LocalDateTime` directly, then both source and sink convert simultaneously. Phase A5 reads should target sites whose immediate consumer already takes `LocalDateTime` (e.g. `permission.setStartLocalDateTime(...)` works because the setter accepts `LocalDateTime`).

**Lesson learned 2026-05-06 — overload-ambiguity trap:** adding `default void setEnd(LocalDateTime)` next to `void setEnd(Date)` makes `setEnd(null)` ambiguous and breaks every caller that does so. The fix: use a **distinct method name** (`setEndLocalDateTime`, `getStartAsLocalDateTime`, etc.) for the new variant. Uglier than overloading but unbreaks all existing callers. Phase A7 cleanup can rename freely once `Date` overloads are gone.

#### Tests

`TimeIntervalLocalDateTimeTest` (new — 4 tests) verifies:
- `TimeInterval.of(LocalDateTime, LocalDateTime)` round-trips through `Date` storage
- `TimeInterval.of(null, null)` returns nulls correctly
- `LocalDateTime` accessors are consistent with the underlying `Date` value
- Existing `Date` API path is unaffected

`mvn test` → **39 tests passing** (4 new + 35 existing).

**Why additive instead of big-bang:** the original PRD plan called for replacing `Date getStart()` with `LocalDateTime getStart()` directly on the `Appointment` interface, then fixing every caller. With ~190 files importing `java.util.Date`, this would produce hundreds of cascading compile errors and force every Phase A3–A6 change to land in one massive commit. The additive approach lets each caller migrate independently when it's touched for other reasons (e.g., a Swing class touched in PRD 002 can simultaneously switch to `LocalDateTime`). Once all callers have migrated, a final "remove deprecated" sweep (Phase A7) can drop the `Date` overloads.

### Phase A6+A7 first complete file — `Export2iCalConverter` (completed 2026-05-06)

`Export2iCalConverter.java` is now **fully `java.util.Date`-free** — the first file in the codebase to complete the full A6 (call-site refactor) → A7 (`Date` import removed) journey.

Steps applied in order:

| Step | Method | Before | After |
|------|--------|--------|-------|
| 1 | `addLastModifiedDateToEvent` | `Date lastChange = appointment.getReservation().getLastChanged()` | `LocalDateTime lastChange = appointment.getReservation().getLastChangedAsLocalDateTime()` |
| 2 | `addCreateDateToEvent` | `Date createTime = appointment.getReservation().getCreateDate()` | `LocalDateTime createTime = appointment.getReservation().getCreateDateAsLocalDateTime()` |
| 3 | `addStartDateToEvent` | `Date startDate = appointment.getStart()` + 3-branch dispatch | `LocalDateTime startDateTime = appointment.getStartDateTime()` inlined |
| 4 | `addEndDateToEvent` | `Date endDate = appointment.getEnd()` + 3-branch dispatch | `LocalDateTime endDateTime = appointment.getEndDateTime()` inlined |
| 5 | exception loop | `for (Iterator<Date> itExceptions = Arrays.asList(repeating.getExceptions()).iterator(); ...)` + `DateTools.toLocalDateTime(date)` | `for (LocalDateTime exception : repeating.getExceptionsAsLocalDateTime())` (new accessor on `Repeating`) |
| 6 | dead-code removal | `getStartDateProperty(Date)`, `getDtStartFromAllDayEvent(Date)`, `getEndDateProperty(Date)`, `getDtEndFromAllDayEvent(Date)`, `convertRaplaLocaleToUTC(Date)` — 5 helpers removed | n/a |
| 7 | A7 cleanup | `import java.util.Date;` | removed |

Tests green at every step (39/39, including `ICalTimezonesControllerTest`). This is the empirical proof that Risk #4 (iCal4j compatibility) is already resolved — the `Date` glue existed *only* because Rapla's domain handed out `Date`, not because iCal4j needed `Date`. Once the entity interfaces expose `LocalDateTime` accessors, the conversion glue collapses entirely.

**Pattern for future A6 sites:** for each `Date d = entity.getX()` followed by `DateTools.toLocalDateTime(d)`, replace with `entity.getXAsLocalDateTime()`. For each `for (Date d : array)` over a `Date[]`-returning method, add a `getXAsLocalDateTime()` array overload to the interface.

### Next up — Phase A3 onwards (incremental adoption)

Each future client/server class that's edited should switch to the `LocalDateTime` overload in the same change, accumulating the migration. The `RaplaSQL` history serializer, `RaplaXMLReader`/`RaplaXMLWriter`, REST endpoints, and Swing presenters are the highest-value targets — once they're on `LocalDateTime` end-to-end, Phase 9 (Gson → Jackson) of PRD 001 can complete because Jackson's `jackson-datatype-jsr310` module handles `LocalDateTime` natively.

## Goal

Replace all `java.util.Date` usage in entity classes and APIs with `java.time.LocalDateTime` (for date+time) and `java.time.LocalDate` (for date-only). This is a prerequisite for switching from Gson to Jackson in PRD 001 Phase 9 — Jackson natively supports `java.time` types via `jackson-datatype-jsr310` but has poor `java.util.Date` support.

## Why LocalDateTime

- Rapla stores all dates internally in UTC/GMT (no timezone) — `LocalDateTime` isme` natively with `jackson-datatype-jsr310` the correct type
- `java.util.Date` is mutable and deprecated; `LocalDateTime` is immutable
- Jackson serializes `LocalDateTi
- `long` millis arithmetic stays as-is (no change to internal calculations in `AppointmentBlock`, `DateTools`, etc.)
- `LocalDate` for date-only values (periods, exception dates) — cleaner API than `cutDate(date)`

## Scope

### Scale

**186 files** in `src/main/java/` import `java.util.Date`. Key areas:

| Area | Files | Approach |
|------|-------|----------|
| Entity interfaces (`Appointment`, `Repeating`, `Period`, `Permission`, `Allocatable`) | 28 | Change method signatures from `Date` → `LocalDateTime` / `LocalDate` |
| Entity impls (`AppointmentImpl`, `RepeatingImpl`, `PeriodImpl`, etc.) | ~16 | Change field types, update method bodies |
| `DateTools` utility | 1 | Add `LocalDateTime`/`LocalDate` overloads, deprecate `Date` overloads |
| `SerializableDateTimeFormat` | 1 | Add `LocalDateTime`/`LocalDate` format/parse methods |
| XML readers/writers (12 files) | 12 | Parse to `LocalDateTime`, format from `LocalDateTime` |
| SQL storage (`RaplaSQL`, `AbstractTableStorage`) | ~10 | Use `java.sql.Timestamp.toLocalDateTime()`, `Timestamp.valueOf(localDateTime)` |
| REST endpoints | ~7 | Already receive/send via JSON — transparent after serializer change |
| Plugin code | 41 | Update to new API signatures |
| Client code (Swing) | 32 | Update to new API signatures |
| Calendar components | 33 | Update `Date` → `LocalDateTime` in calendar renderers |
| `AppointmentBlock` | 1 | Keep `long start/end` internally — `LocalDateTime` only at API boundary |
| `TimeInterval` | 1 | Change fields from `Date` → `LocalDateTime` |

### Key type mapping

| Current | Replacement | Notes |
|---------|-------------|-------|
| `Date` (date+time) | `LocalDateTime` | Appointments, timestamps |
| `Date` (date-only, midnight) | `LocalDate` | Period start/end, exception dates |
| `Date` (time-of-day) | `LocalTime` | Appointment start time, end time |
| `long` (millis) | `long` (unchanged) | Internal arithmetic, `AppointmentBlock` |
| `Set<Date>` (exceptions) | `Set<LocalDate>` | Repeating exception dates |

### What stays

- `long` millisecond arithmetic in `DateTools`, `AppointmentBlock`, `processBlocks()` — no change
- `SerializableDateTimeFormat` string formats (ISO 8601) — no change to wire/disk format
- XML file format — no change (same attribute names and values)
- SQL schema — no change (`DATETIME`/`TIMESTAMP` columns)
- REST API wire format — no change (ISO 8601 strings)

### What changes

- All `java.util.Date` fields → `LocalDateTime` / `LocalDate` / `LocalTime`
- All method signatures taking/returning `Date` → `java.time` equivalents
- `DateTools` gains new overloads for `java.time` types
- `SerializableDateTimeFormat` gains new methods for `java.time` types
- `java.sql.Timestamp` conversion uses `toLocalDateTime()` / `valueOf()`
- `new Date(millis)` → `DateTools.toLocalDateTime(millis)` (or `LocalDateTime.ofInstant(...)`)
- `date.getTime()` → `DateTools.toMilli(localDateTime)` (or custom conversion)

## Plan

### Phase A1: Foundation — DateTools + SerializableDateTimeFormat
1. Add `java.time` conversion methods to `DateTools`:
   - `toMilli(LocalDateTime)` → `long`
   - `toLocalDateTime(long)` → exists already
   - `toLocalDate(long)` → exists already
   - `LocalDate` overloads for `cutDate`, `fillDate`, `addDays`, `countDays`, etc.
   - `LocalTime` overloads for `getHourOfDay`, `getMinuteOfHour`, etc.
2. Add `java.time` methods to `SerializableDateTimeFormat`:
   - `formatDate(LocalDate)` → `String`
   - `parseDate(String)` → `LocalDate` (new overload)
   - `formatTime(LocalTime)` → `String`
   - `parseTime(String)` → `LocalTime`
   - `formatTimestamp(LocalDateTime)` → `String`
   - `parseTimestamp(String)` → `LocalDateTime`
3. Write tests for all new methods comparing output with existing `Date`-based methods
4. Run `mvn test` — all existing tests pass (no behavior change yet)

### Phase A2: Core Entity Interfaces
1. Change `Appointment` interface:
   - `Date getStart()` → `LocalDateTime getStart()`
   - `Date getEnd()` → `LocalDateTime getEnd()`
   - `Date getMaxEnd()` → `LocalDateTime getMaxEnd()`
   - `void move(Date, Date)` → `void move(LocalDateTime, LocalDateTime)`
   - `boolean overlaps(Date, Date)` → `boolean overlaps(LocalDateTime, LocalDateTime)`
   - Remove `getStartDateTime()` bridge methods (now redundant)
2. Change `Repeating` interface:
   - `Date getEnd()` → `LocalDate getEndDate()`
   - `Set<Date> getExceptions()` → `Set<LocalDate> getExceptions()`
   - `void addException(Date)` → `void addException(LocalDate)`
3. Change `Period` interface:
   - `Date getStart()` → `LocalDate getStart()`
   - `Date getEnd()` → `LocalDate getEnd()`
4. Change `Permission`, `Allocatable`, `Timestamp`, `LastChangedTimestamp` interfaces
5. Change `TimeInterval` — fields from `Date` to `LocalDateTime`
6. Compile will break everywhere — this is expected, fix in Phase A3

### Phase A3: Entity Implementations
1. `AppointmentImpl`:
   - `private Date start` → `private LocalDateTime start`
   - `private Date end` → `private LocalDateTime end`
   - `transient Date maxDate` → `transient LocalDateTime maxDate`
   - Update `processBlocks()` — keep `long` arithmetic, convert to/from `LocalDateTime` at boundaries
2. `RepeatingImpl`:
   - `private Date end` → `private LocalDate end`
   - `private Set<Date> exceptions` → `private Set<LocalDate> exceptions`
3. `PeriodImpl` — delegate `TimeInterval` changes
4. `ReservationImpl`, `AllocatableImpl`, `PermissionImpl`:
   - `private Date lastChanged` → `private LocalDateTime lastChanged`
   - `private Date createDate` → `private LocalDateTime createDate`
5. `CategoryImpl`, `UserImpl`, `DynamicTypeImpl` — same timestamp field changes
6. Write tests: entity creation, date arithmetic, serialization

### Phase A4: XML Storage Layer
1. Update `RaplaXMLWriter` base class:
   - `printTimestamp(Timestamp)` → use `formatTimestamp(localDateTime)` instead of `formatTimestamp(date)`
2. Update `RaplaXMLReader` base class:
   - `parseDate()` returns `LocalDate`, `parseTime()` returns `LocalTime`, `parseTimestamp()` returns `LocalDateTime`
3. Update all 12 XML readers/writers to use `java.time` types
4. Write test: round-trip XML read/write produces identical output

### Phase A5: SQL Storage Layer
1. Update `AbstractTableStorage`:
   - `setDate(PreparedStatement, int, LocalDateTime)` — convert via `Timestamp.valueOf(localDateTime)`
   - `getDate(ResultSet, int)` → returns `LocalDateTime` — convert via `timestamp.toLocalDateTime()`
   - Same for `setTimestamp`/`getTimestamp`
2. Update `RaplaSQL` — all date column reads/writes use new methods
3. Write test: SQL round-trip produces identical data

### Phase A6: REST + Client Layer
1. Update REST endpoint method signatures (`@QueryParam` dates) — use `LocalDateTime` param converter
2. Update `RemoteOperator` — use `LocalDateTime` for sync timestamps
3. Update all 41 plugin files, 32 client files, 33 calendar component files
4. Write test: REST API returns same JSON format as before

### Phase A7: Cleanup
1. Remove deprecated `Date` overloads from `DateTools`
2. Remove `getStartDateTime()` bridge methods from `Appointment`
3. Remove `java.util.Date` imports across codebase
4. Run `mvn compile` — zero compilation errors
5. Run `mvn test` — all 188 tests pass

## Tests

| Phase | Test | When |
|-------|------|------|
| A1 | `DateTools` new methods produce same results as `Date` methods | Before entity changes |
| A2 | Interfaces compile (with implementation changes coming) | After interface changes |
| A3 | Entity creation, date arithmetic, equals/hashCode | After impl changes |
| A4 | XML round-trip: write entity → read back → identical | After XML changes |
| A5 | SQL round-trip: store entity → read back → identical | After SQL changes |
| A6 | REST API returns same JSON as before | After REST changes |
| A7 | Full `mvn test` passes, zero `java.util.Date` imports remain | After cleanup |

## Risks

1. **Gson serialization of `LocalDateTime`** — Gson doesn't handle `LocalDateTime` natively. Need to register a `TypeAdapter<LocalDateTime>` or `TypeAdapterFactory`. This is a small, one-time setup. The `TypeAdapter` should use `SerializableDateTimeFormat` to maintain wire compatibility.
2. **SQL `DATETIME` column precision** — `java.sql.Timestamp` has nanosecond precision, `LocalDateTime` does too. No data loss. Verify with existing SQL test data.
3. **`long` ↔ `LocalDateTime` conversion** — Rapla stores dates in UTC/GMT with epoch millis. `LocalDateTime` doesn't have timezone, so conversion goes through `Instant.ofEpochMilli(long)` at UTC. `DateTools` already has `toLocalDateTime(long)` — verify it uses UTC.
4. **iCal4j compatibility — NOT a risk in 4.x.** Verified 2026-05-06 against `parent/pom.xml: ical4j.version = 4.2.0`. iCal4j 4.x speaks `java.time` natively: `DtStart(ZonedDateTime)`, `DtStart(Instant)`, `DtStart(LocalDate)`, `ExDate<ZonedDateTime>`, `DateList<ZonedDateTime>`. `Export2iCalConverter.java` already imports `java.time.{Instant, LocalDateTime, ZonedDateTime, LocalDate}` and uses them directly — the only `java.util.Date` it touches comes *from Rapla's domain* (`appointment.getStart()`, `reservation.getLastChanged()`, etc.), and is immediately funnelled through `DateTools.toLocalDateTime(date)` before being handed to iCal4j. **Implication:** once Rapla's domain returns `LocalDateTime`/`Instant` directly, the conversion glue inside `Export2iCalConverter` (`convertRaplaLocaleToUTC(Date)`, `getDtEndFromAllDayEvent(Date)`, `getDtStartFromAllDayEvent(Date)`, etc.) collapses — pass `appointment.getStartDateTime()` straight into `DtStart`. **No adapter layer is needed** — Phase A6 here is just call-site refactoring, not type-bridging. Same logic applies to `RaplaICalImport.java` (uses iCal4j 4.x reading APIs which yield `Temporal`/`ZonedDateTime`).
5. **Exchange Web Services** — EWS API uses `java.util.Date`. Need adapter in `AppointmentSynchronizer`, `EWSConnector`. Small scope (2-3 files).

## Dependencies on Other PRDs

| PRD | Relationship |
|-----|-------------|
| **001: Spring Boot Migration** | **This PRD is a prerequisite for Phase 9** (Gson → Jackson switch). Phases 0-8 of PRD 001 proceed independently. |

## Open Questions

1. **`LocalDate` vs `LocalDateTime` for periods?** Periods are date-only (no time). Using `LocalDate` is cleaner but means `Appointment.getStart()` returns `LocalDateTime` while `Period.getStart()` returns `LocalDate`. Need to handle comparison carefully. **Recommendation: Use `LocalDate` for periods** — add conversion helpers in `DateTools`.
2. **Gradual or big-bang?** 186 files is a lot. Can we do it package-by-package? **Recommendation: Big-bang for interfaces (Phase A2)** — this causes compile errors everywhere, but fixes cascade naturally. Package-by-package for implementations (Phase A3-A6).
3. **Gson `TypeAdapter` registration?** Where to register the `LocalDateTime` TypeAdapter? **Recommendation: In the `ClientProxyConfig` (after PRD 001) or in a shared `GsonConfig` class.** Until PRD 001 is done, add it to the existing `JavaJsonSerializer`.
