# PRD 101 — Transpose & anchors: the move/copy/paste/template mutation family

**Status:** in-progress — 2026-07-09. Phases 0–5 DONE: findings + solution
drafted, Swing copy-SINGLE fix (D9), server move family (`moveReservations`/
`moveAppointment`/`splitOccurrence`/`copyReservations` reworked, `Duration`
scalar deleted), curl-verified, and the SPA basic move + resize with the
EVENT/SERIE/SINGLE scope dialog. Phase 6+ (copy verbs, SPA copy/paste,
`instantiateTemplate`, `exchangeAllocatable`, month drag) deferred.
This PRD is the durable record of a long design dialog (2026-07-09) plus a six-track
research sweep (5-agent workflow over the Swing codebase + 1 multi-reservation deep-dive
+ external API survey). Read this before touching move/copy/paste/template mutations —
it supersedes the interim `moveAppointment` sketch that briefly lived in [PRD 056](056-graphql-events-write-api.md).

**Related:** [PRD 056](056-graphql-events-write-api.md) (reservation write surface — the verbs land there; its
`moveReservations`/`copyReservations(dateShift: Duration)` are the migration targets),
[PRD 094](094-spa-main-view-actions-and-popups.md) (SPA calendar drag/resize consumer — Phase 4 + D5: scope logic stays server-side),
[PRD 091](091-spa-reservation-edit-and-availability.md) (recurrence editor — SINGLE-split semantics), PRD 099 (table selection — the SPA
multi-select that feeds bulk verbs), [PRD 095](095-month-grid-render-mode.md) (month grid — the day-granular drag surface),
`docs/architecture/reservation-edit.md` (Swing drag/resize/delete flows).

## Abstract

The SPA needs drag/drop move + resize + copy/paste + template instantiation across
view bases (week grid, month grid, Wochenprogramm, resource columns, table
multi-select). Research shows rapla already has ONE normative client-side transpose
primitive and a consistent anchor model hidden inside a `keepTime` boolean — but the
server GraphQL surface can express almost none of it. This PRD records the current
state, the external precedent, the locked doctrine, and the anchor taxonomy, so the
solution draft starts from facts instead of re-research.

## Current state — research findings (2026-07-09, file:line verified)

### 1. The one shared transpose primitive (client-side only)

**`FacadeImpl.copyReservations(Collection<Reservation>, LocalDateTime beginn, boolean keepTime, User)`**
(`rapla-core/.../facade/internal/FacadeImpl.java:1066–1136`) is used by BOTH
multi-reservation paste (`ReservationPaste`, `ReservationControllerImpl:1469–1512`) AND
template instantiation (`EditTaskPresenter.java:175–215`, `ImportTemplateMenu.java:421–431`).
Semantics:

- **Anchor = earliest appointment start across the whole set**
  (`ReservationStartComparator`, `:1068`); it lands on the target `beginn`; every other
  appointment of every reservation keeps its relative offset.
- **`keepTime=true`** (day-granular): `offsetDays = countDays(firstStart, oldStart)`;
  `newStart = toDateTime(destStart.plusDays(offsetDays), oldStart)` — each appointment
  keeps its OWN time-of-day (`DateTools.toDateTime`, `DateTools.java:456–459`).
- **`keepTime=false`**: exact `Duration.between(firstStart, destStart)` on each start.
- **Exceptions are re-based** by day-count (`:1113–1120`) and a non-fixed `until` end is
  re-based to preserve series length in days (`:1122–1132`) — *both branches, always
  day-granular*. (The exception re-base is now judged WRONG — see doctrine D2 below.)
- Ids batch-minted; restrictions remapped in `cloneReservation` (`:1231+`); template
  annotation stripped → `copyof` provenance marker (`:1271–1283`).

The single-reservation clipboard paste (`AppointmentPaste`,
`ReservationControllerImpl:1318–1408`) is a divergent second implementation (pure
millisecond offset via `getOffset`) with the same result semantics — the facade
primitive subsumes it; a server port needs exactly one implementation.

### 2. Swing's normative move/resize model

- Wire shape everywhere: **(AppointmentBlock ≈ appointmentId + occurrenceStart,
  newStart[, newEnd], keepTime)** — `ReservationController.java:48,51`. Delta derived in
  ONE function: `getOffset(appStart, newStart, keepTime)`
  (`ReservationControllerImpl:805–814`). `keepTime=true` rebases the target to
  (target date + original time-of-day) ⇒ offset is whole days.
- **`keepTime` is set statically per VIEW**, not per gesture: month
  (`SwingMonthCalendar:136`) + compact week (`SwingCompactWeekCalendar:194`) = `true`;
  week/day = `false`. Month-view drops genuinely carry midnight
  (`SwingMonthView.createDate:305–312`); time preservation happens in `getOffset`.
- **Scope dialog** (EVENT/SERIE/SINGLE, `showDialog`, `:506–539`) runs AFTER the drop;
  `includeEvent = (newEnd == null)` (`:773`) — **resize never offers EVENT scope**.
- **SINGLE split recipe** (`AppointmentResize.change`, `:1202–1251`): clone (fresh id) +
  `setRepeatingEnabled(false)` + move clone from the *occurrence* start + `addAppointment`
  + **copy per-allocatable restrictions to the clone** (`:1246–1248`,
  `ReservationImpl.java:560–579`) + `repeating.addException(cutDate(occurrenceStart))`
  (`:1249`), guarded by the `isNotEmptyWithExceptions` escalation
  (exception → remove appointment → remove reservation, `:473–504`, `:152–191`).
- **Resize support matrix**: week/day views only (`DraggingHandler` `supportsResizing`);
  month/compact/timeslot = move-only. Per-block gates: multi-day blocks
  (`!startsAndEndsOnSameDay()`) and exception occurrences are never resizable;
  exception occurrences aren't draggable at all (`RaplaBlock.java:116–117, 180–186`).
- **Slot-drag can mean resource exchange, not time move**: dayresource / compact-week /
  timeslot views route a column/row change to
  `exchangeAllocatable(appointmentBlock, oldAlloc, newAlloc, newStart, ctx)`
  (`SwingDayResourceCalendar:187–209`, `SwingCompactWeekCalendar:167–191`).
- Timeslot views snap minute-of-day to the target band (`SwingCompactCalendar:170–197`).

### 3. Copy/cut/paste specifics

- Clipboard (`RaplaClipboard.java:32–94`) holds detached clones + a CopyType
  (`CUT_BLOCK/CUT_RESERVATION/COPY_RESERVATION/COPY_BLOCK`) + the source resource
  context; multi-reservation clipboard stores the **earliest appointment** as anchor
  representative.
- Copy/cut run the same EVENT/SERIE/SINGLE dialog as move/delete
  (`copyCutAppointment`, `:606–683`). Copy-SINGLE strips the repeating rule; copy-SERIE
  keeps rule + exceptions verbatim.
- **"Paste"** (into existing event; only for single-block copy types) = add the shifted
  appointment clone to the ORIGINAL reservation (`:1344–1360`). **"Paste as new event"**
  = clone reservation(s), shift, create new (`:1372–1390`). Pasting into a different
  resource slot silently exchanges the allocatable (`:728–750`).
- **Cut = copy + immediate scoped delete at cut time** (`:670–675` → `deleteAppointment`);
  cut-SINGLE writes `addException(cutDate(occurrenceStart))` on the source (`:425–433`).
- **Calendar multi-block copy does NOT exist** — table view multi-selects whole
  reservations only (`SwingTableView:629–661`); `MenuFactoryImpl.java:170` carries the
  TODO. The SPA (PRD 099 selection) would exceed Swing here.

### 4. Repeating model — move implications

| Type | Move by hours | days ∤7 | 7·n days |
|---|---|---|---|
| WEEKLY | clean | weekday set self-heals (swap, `AppointmentImpl:115–128`) | set untouched |
| DAILY | clean | clean | clean |
| MONTHLY | clean | nth-weekday-of-month silently re-derived from new start | nth-rank CHANGES (2nd→3rd Tue) |
| YEARLY | clean | anniversary redefined | anniversary redefined |

- Fixed-count series keep their count on move; **absolute-end series silently change
  occurrence count** (`RepeatingImpl:226–332`).
- **Exceptions**: stored day-truncated (`addException` cutDates, `RepeatingImpl:335–345`),
  matched per whole calendar day (`:285–301`). NO existing *move* path shifts them
  (Swing SERIE move and GraphQL `moveReservations` agree). Orphaned exceptions are
  expansion-harmless (soft harms: `matches()` diffs, UI noise; no GC).
- Whole-day flag (`isWholeDaysSet`) silently degrades on non-midnight moves
  (`AppointmentImpl:129–135`) — date-only integrity is a client `keepTime` behavior,
  not a domain rule.

### 5. Server GraphQL surface today — the gaps

- `moveReservations(ids, dateShift: Duration!)` / `copyReservations(ids, dateShift:
  Duration!)` (`schema.graphqls:1179,1182`; `ReservationMutationController:292–374`)
  apply ONE uniform `java.time.Duration` per appointment. **The `keepTime=true`
  (each-event-keeps-own-time) semantics is inexpressible** as a single Duration when
  appointments have different times.
- No SINGLE split, no per-appointment (SERIE) move, no resize, no paste-into-existing,
  no template instantiation (`rapla:template` is client-only; server refs are filtering
  only). `Duration` scalar used ONLY by these two verbs (`GraphQlScalarConfig:107–150`);
  its schema description over-claims `P[n]Y[n]M…` — `java.time.Duration.parse` rejects
  years/months.
- Note: appointments store zone-less `LocalDateTime` → `plus(Duration)` is already
  wall-clock-safe; DST was a red herring in the design dialog.
- `checkIdIntegrity` ([PRD 056](056-graphql-events-write-api.md) §9) resolves appointment ids globally → `reservationId`
  args are redundant on appointment-addressed verbs.

### 6. External API survey (Google / MS Graph / RFC 5545 / FullCalendar)

- **Occurrence identity is unanimous**: (series id, original rule-generated start) —
  Google `originalStartTime`, Graph `originalStart` / `OID.{masterId}.{date}`,
  RFC 5545 `RECURRENCE-ID`. No stored occurrence ids anywhere. Rapla's
  `(appointmentId, occurrenceStart)` matches the industry shape.
- **Absolute target times on the wire, never deltas** (deltas live only in UI callbacks
  — FullCalendar hands both delta and old/new absolutes).
- **Anchor granularity is a TYPE**: RFC `RECURRENCE-ID` is `DATE` for all-day series,
  `DATE-TIME` for timed; Graph's occurrence id embeds a bare date. Nobody has a
  `keepTime` flag — it falls out of whole-day deltas / date-typed values.
- **"Keep weekday" as a wire intent exists nowhere**; **copy-with-time-shift exists
  nowhere** (copy = fresh insert; exceptions/overrides dropped). Rapla's transpose
  primitive is richer than the industry norm.
- RFC 5545 keeps a moved occurrence ATTACHED to its series (same UID + RECURRENCE-ID);
  rapla's SINGLE split detaches it permanently — a documented rapla semantic, not an
  accident.
- Latent Swing bug found in passing: copy-SINGLE strips time-of-day
  (`toTime(cutDate(x))` ≡ 0, `ReservationControllerImpl:638–641`) — clone lands at
  midnight. Not yet fixed.

### 7. Slot generalization — views are slot lattices

A drag is always *same block, from-slot → to-slot*; views differ in the slot's
coordinate system:

| View base | Slot coordinate | Anchor type | Existing mechanic |
|---|---|---|---|
| Week/day grid | (date, time) | dateTime | `keepTime=false` drag |
| Month / compact week | date | day | `keepTime=true`, midnight drops |
| Timeslot views | (date, band) | day + band snap | `SwingCompactCalendar:170–197` |
| Wochenprogramm (program mode) | (weekday, time) — **but always a concrete week** | day | `move()` weekday-swap fires automatically |
| Day/resource columns | (time, allocatable) | orthogonal resource axis | `exchangeAllocatable` |
| Table | none (selection, not slots) | bulk verbs | table multi-select |

Insights: (a) *(revised — OQ4 closed won't-do)* the Wochenprogramm columns look like
abstract weekdays but every block carries a concrete occurrence start; there is **no
dateless entity anywhere in the domain** (even templates store absolute dates), so a
dateless `weekday` target variant has nothing to bind to — the weekday-rule adjustment
is `AppointmentImpl.move` behavior reached through ordinary day/dateTime targets;
(b) the resource axis is orthogonal and combinable with a time move — and Swing's
`exchangeAllocatable` already carries an optional `newStart` (the diagonal drag is
real, `ReservationControllerImpl:934–941`).

## Drafted solution (locked in the 2026-07-09 design dialog; implementation pending)

The pair-anchor concept **decomposed** under the API lens into two parameters with
distinct roles: a **reference** (where the delta is measured from — explicit or
server-derived) and a **target** (where it goes — carrying the granularity as a type).

### Shared inputs

```graphql
"@oneOf (standard GraphQL directive, engine-enforced exactly-one — same machinery as
ChangeOp). The variant type IS what the move preserves: day = whole-day delta against
the reference's date, time-of-day preserved BY CONSTRUCTION (≙ Swing keepTime=true,
RFC 5545 DATE); dateTime = exact delta (≙ keepTime=false, RFC DATE-TIME)."
input Target          { day: Date, dateTime: LocalDateTime }          # move-only flavor
input ResizableTarget { day: Date, dateTime: DateTimeTarget }         # single-block move verbs
input DateTimeTarget  { start: LocalDateTime!, end: LocalDateTime }   # end present ⇒ resize

enum ExchangeScope { EVENT SERIE SINGLE }
```

Structural guarantees (no runtime validators, unwritable instead): day targets cannot
resize (no `end` field); set/copy verbs cannot resize (their `Target` has no `end`
anywhere); a codegen client gets discriminated unions via `@oneOf` introspection.

### Verb family — one grammar: `verb(address, reference|occurrence?, target [, options])`

```graphql
# ── set-addressed. reference = UNVALIDATED arithmetic pivot;
#    default = derived earliest appointment start across the set AT EXECUTION TIME
#    (the FacadeImpl rule — all other appointments keep relative offsets).
moveReservations(ids: [ID!]!, reference: LocalDateTime, target: Target!): BulkResult!
copyReservations(ids: [ID!]!, reference: LocalDateTime, target: Target!): BulkResult!
instantiateTemplate(templateId: ID!, reference: LocalDateTime, target: Target!): BulkResult!

# ── appointment-addressed. occurrence = VALIDATED reference (must match a real
#    occurrence, else OCCURRENCE_NOT_FOUND); default = the appointment's start —
#    uniform for repeating and non-repeating (§ "occurrence default" below).
moveAppointment(appointmentId: ID!, occurrence: LocalDateTime,
                target: ResizableTarget!, expectedLastChanged: LocalDateTime): Reservation!
copyAppointment(appointmentId: ID!, occurrence: LocalDateTime, target: Target!,
                asNewReservation: Boolean = false): Reservation!

# ── occurrence-addressed. occurrence required — it IS the address.
splitOccurrence(appointmentId: ID!, occurrence: LocalDateTime!,
                target: ResizableTarget!, expectedLastChanged: LocalDateTime): Reservation!
exchangeAllocatable(appointmentId: ID!, occurrence: LocalDateTime,
                    from: ID!, to: ID!, scope: ExchangeScope! = SERIE,
                    target: Target, expectedLastChanged: LocalDateTime): Reservation!
```

| Verb | Effect | Creates entities? | Resize? |
|---|---|---|---|
| `moveReservations` | shift every appointment of every listed reservation | no | no (structural) |
| `copyReservations` | clone + shift (server-minted ids) | new reservations | no |
| `instantiateTemplate` | read template reservations server-side, transpose, strip `rapla:template` → `copyof`, honor `fixedtimeandduration` (rejects `dateTime` target when true) | new reservations | no |
| `moveAppointment` | shift/resize ONE appointment (rule rides along; weekly weekday set self-heals) | no | via `dateTime.end` |
| `copyAppointment` | clone one appointment (rule + exceptions verbatim). `asNewReservation=false` → add to the SAME reservation (Swing "Einfügen"); `=true` → NEW reservation from just this appointment (Swing "Einfügen als neuer Termin") — mirrors Swing's own `pasteAppointment(…, asNewReservation, …)` param | new appointment; `asNewReservation` → new reservation (server ids) | no |
| `splitOccurrence` | detach one occurrence: clone as non-repeating at target (restrictions carried) + `addException(cutDate(occurrence))` + `isNotEmptyWithExceptions` escalation | new appointment (server id) | via `dateTime.end` |
| `exchangeAllocatable` | swap `from`→`to` allocatable at scope, with the full Swing restriction algebra (`ReservationControllerImpl:817–975`); SINGLE = split + swap; optional `target` = the diagonal drag (lane/column drop at a different time — atomic, Swing parity) | SINGLE: new appointment | no (Swing parity) |

`exchangeAllocatable` is the one verb with a scope **enum** — justified because, unlike
move, its argument shape does not vary by scope (always `appointmentId, occurrence?,
from, to`); the single runtime rule is SINGLE-requires-repeating, matching Swing's
dialog gating (`:853`). `includeEvent` precedent: EVENT offered when the old
allocatable has no restriction (`:841`).

### The occurrence default (uniform, no repeating-conditional)

`occurrence` **defaults to the appointment's start** — which is by definition
occurrence #1, for repeating and non-repeating alike. Two call styles fall out:
*intent style* (omit it: "make this appointment start at X" — scripts/MCP/editors) and
*drag style* (pass the grabbed occurrence — the drag client MUST, or a mid-series grab
shifts by the wrong delta; documented in the field description). Guard is opt-in
coherently: given → validated (`OCCURRENCE_NOT_FOUND` on stale — distinct from
`CONCURRENT_MODIFICATION`); omitted → only `expectedLastChanged` guards.

### Set-verb `reference` (why it exists — the EVENT-drag hole)

`target`-only set verbs would force the EVENT-drag client to know the reservation's
earliest appointment start (grabbed block ≠ earliest ⇒ wrong delta) — the same
pre-query staleness race as client-supplied template anchors. So set verbs take an
optional unvalidated `reference`; the EVENT drag sends the grabbed occurrence start,
"put these at X" callers omit it, and scripts get the delta style free (any pivot +
pivot-relative target). Dialog mapping: *Ganze Veranstaltung* →
`moveReservations([id], reference: grabbedStart, …)`; *Serie* → `moveAppointment`;
*Nur dieser Termin* → `splitOccurrence` (repeating) / `moveAppointment` (non-repeating
appointment of a multi-appointment reservation — Swing offers SINGLE-split only for
repeating).

### Ids, retry, response contract

- **Copy family mints ids server-side** ([PRD 056](056-graphql-events-write-api.md) §9 carve-out: "server-initiated
  creates keep server-generated ids" — the client sends no entity content). Client ids
  on copy/instantiate were also a staleness race (template event count). NOT idempotent:
  a retried copy creates a second copy — documented on the verbs; visible + cheap to fix,
  matches Google/Graph. Optional client id = additive later if a consumer needs it.
- **Responses must return what was created** (never make a consumer diff): copy verbs
  return the clones with ids; `splitOccurrence`/`exchangeAllocatable`-SINGLE responses
  must expose the new appointment id.
- Error codes (extends the [PRD 056](056-graphql-events-write-api.md) taxonomy): `OCCURRENCE_NOT_FOUND` (stale/wrong
  `occurrence`), plus existing `CONCURRENT_MODIFICATION`, `REFERENCE_NOT_FOUND` (§12
  uniform), `PERMISSION_DENIED`; `INVALID_SHIFT` semantics = OQ7 (open).

### Migration

`moveReservations`/`copyReservations` lose `dateShift: Duration!` in favor of
`(reference?, target!)`; the **`Duration` scalar is deleted** (these two verbs were its
only consumers; its description over-claimed ISO 8601 anyway). Breaking is free —
spring-boot branch, no released consumers. `instantiateTemplate` is NEW server surface
(template instantiation is client-only today) and unblocks SPA templates.

## Decisions locked

**D1 — the transpose logic lives in GraphQL mutations, not the client (2026-07-09).**
Maintainer directive; recorded as [PRD 094](094-spa-main-view-actions-and-popups.md) D5. The SPA never rebuilds
`updateReservation` payloads for scope moves.

**D2 — exceptions are absolute calendar facts; NO operation re-bases them
(2026-07-09, maintainer, CONFIRMED).** An exception means "no lecture on May 1
*because May 1 is a holiday*" — the reason is bound to the date, not the series shape.
Round-trip stability clinches it: move a series away and back and the holiday exclusion
still holds. Consequences: (a) **move** keeps exceptions absolute (Swing parity —
`AppointmentImpl.move` verified never touches them); the occurrence resurfacing
off-holiday after a shift is CORRECT, not a wart; (b) **copy/template** ALSO keeps
them absolute — a **deliberate divergence from Swing**, whose `FacadeImpl.copy`
re-bases by day-count (`:1113–1120`). Swing's re-base is judged *wrong*: on a
near-range copy it actively suppresses the wrong day (copy +7d → the copy's May-1
lecture happens and May-8 is wrongly skipped); keep-absolute stays correct near-range
and goes harmlessly inert far-range (holidays don't translate by day-count anyway).
OQ2 resolved → keep-absolute everywhere.

**D3 — `until` is asymmetric: absolute on move, length-preserving on copy
(2026-07-09, CONFIRMED against Swing).** Only absolute-end series are affected
(fixed-count is start-relative → preserved automatically). Verified Swing behavior:
`AppointmentImpl.move` leaves `until` absolute (a forward move shrinks the tail —
correct, the semester still ends when it ends); `FacadeImpl.copy` re-bases `until`
length-preserving (`:1122–1132`) — necessary, else a far-range template copy is empty
(zero occurrences). The asymmetry is principled: an out-of-range exception is *inert*,
an out-of-range `until` is *load-bearing* (kills the series). We match Swing on both.

**D4 — one transpose implementation server-side.** The facade primitive's anchor rule
(earliest-start, relative offsets, per-variant arithmetic, restriction rewrite) is the
spec; the `AppointmentPaste` millisecond path is subsumed and not ported.

**D5 — verified Swing exception/until behavior (the port target).**

| | exceptions | `until` (absolute-end) | fixed count |
|---|---|---|---|
| move (`AppointmentImpl.move`) | absolute | absolute | preserved (start-relative) |
| copy + template (`FacadeImpl.copy`) | re-based by day-count | re-based length-preserving | preserved |
| **our design** | **absolute always** (D2 — diverges from Swing copy) | absolute on move / length-preserving on copy (D3 — matches Swing) | preserved |

**D6 — naming: keep `copy*`, no cut verb, `splitOccurrence` over `moveOccurrence`
(2026-07-09, maintainer).** "copy" = the complete source→destination operation (API
sense: `cp`, Drive `files.copy`), not the clipboard step; the clipboard is client
state the stateless API doesn't model (UI paste = call `copyReservations`/`moveReservations`
with the remembered selection + `target`). No cut verb — cut/paste is a pure client
composition (delete + copy/move). `splitOccurrence` names the observable consequence
(a new appointment id appears; the occurrence detaches permanently — unlike RFC 5545's
attached override) rather than hiding it behind "move".

**D7 — `exchangeAllocatable` ported literally (2026-07-09, maintainer).** Including:
scope enum (its arg shape doesn't vary by scope, so an enum is right here where it was
wrong for move); SINGLE = split + swap; EVENT + `target` shifts only the grabbed
appointment's time (`ReservationControllerImpl:934–941`, `app = addAppointment ?:
appointment`); the full restriction algebra (`:817–975`).

**D8 — MONTHLY rank drift accepted + documented (2026-07-09; OQ8).** MONTHLY has no
stored pattern — nth-weekday re-derives from the start (`RepeatingImpl:631–640`), so a
±7d SERIE move silently turns "2nd Tue" → "3rd Tue". Server accepts any move (Swing
parity, arguably correct — the pattern IS the start); schema description warns; any
snap/preview mitigation is a client concern, not in the contract.

**D9 — Swing copy-SINGLE midnight bug FIXED (2026-07-09; OQ9).**
`ReservationControllerImpl:638` used `toTime(cutDate(start))` (always 0 → clone at
00:00); fixed to `toTime(start)`, extracted to the testable `singleCopyStart` helper,
pinned by `SingleCopyStartTest` (tier-1, 3 cases, red-green verified). Independent of
the GraphQL work; landed alongside it.

## Open questions (remaining)

- **OQ7 — `INVALID_SHIFT` semantics**: define exactly — a move emptying an absolute-end
  series (all occurrences past `until`)? `until < start`? Currently [PRD 056](056-graphql-events-write-api.md) prose,
  unimplemented. Resolve when the move verb lands (Phase 2).
- **OQ6 — orphaned-exception display**: D2 makes orphans *meaningful* (dormant holiday
  facts), so GC would be a bug. At most a UI "inactive exceptions" grouping — a client
  concern, deferred.
- **OQ-copy — single-block paste-as-new**: RESOLVED — `copyAppointment.asNewReservation`
  boolean (mirrors Swing's `pasteAppointment(…, asNewReservation, …)`).

## Plan

Implementation order (maintainer directive 2026-07-09): server first → Swing fix →
restart + curl the API → SPA basic move + resize. **Copy/paste in the SPA deferred to a
later phase** (server copy verbs may land opportunistically but the SPA consumes only
move/resize now).

### Phase 0 — findings capture (this document) — DONE 2026-07-09
### Phase 1 — solution draft — DONE 2026-07-09 (§ "Drafted solution", Decisions D1–D9)
### Phase 2 — Swing copy-SINGLE fix — DONE 2026-07-09 (D9; `SingleCopyStartTest` green)
### Phase 3 — server: move family (test-first, tier-3 GraphQL)
- [ ] Schema: `Target`, `ResizableTarget`, `DateTimeTarget` `@oneOf` inputs; drop
      `Duration` scalar; rework `moveReservations` to `(reference?, target!)`
- [ ] Shared server transpose helper (port `FacadeImpl.copy` anchor rule to the
      operator; exceptions absolute per D2, `until` per D3/D5)
- [ ] `moveAppointment` (SERIE; move + resize via `dateTime.end`; weekday self-heal;
      `occurrence` default + `OCCURRENCE_NOT_FOUND` guard)
- [ ] `splitOccurrence` (SINGLE; clone + restriction copy + `addException` +
      `isNotEmptyWithExceptions` escalation)
- [ ] `moveReservations` rework (EVENT/bulk; `reference` pivot)
- [ ] tier-3 tests: one per verb/scope + resize + split-empties-series edge + §12 leak
### Phase 4 — verify: restart dev server, curl the API (login → query → move → re-query)
### Phase 5 — SPA: basic move + resize — DONE 2026-07-09
- [x] Week grid drag → scoped move with the EVENT/SERIE/SINGLE dialog
      (`MoveScopeDialogComponent`). Verb dispatch (`move-scope.ts`
      `buildMoveScopeCommand`): EVENT → `moveReservations([id], reference)`,
      SERIE → `moveAppointment`, SINGLE → `splitOccurrence` (repeating) /
      `moveAppointment` (non-repeating appointment). A simple single
      non-repeating block moves straight with no dialog (the old fast path).
- [x] Week grid edge-resize (bottom handle) → `moveAppointment` with
      `dateTime.end` (SERIE) / `splitOccurrence` with `dateTime.end` (SINGLE);
      resize never offers EVENT (Swing parity).
- [x] Wired through the [PRD 094](094-spa-main-view-actions-and-popups.md) command/undo infra (`UndoToastService`).
      Move + resize carry compensating inverses; **`splitOccurrence` is
      not undoable in v1** (`undo: null`) — a clean inverse needs the minted
      appointment id + an updateReservation rebuild (D1 keeps that server-side);
      deferred.
- [x] Drag gate widened: `block-style.isDraggableRow` (canModify + resolvable
      appointment id + not an exception occurrence) replaces the single-
      appointment-non-repeating `isMovableRow` on BOTH the week and month grids.
      Month drag now pops the same scope dialog (move-only, whole-day = keepTime;
      no resize — Swing parity); pulled forward from Phase 6.
- Tests: `move-scope.spec.ts` (facts/options/verb-dispatch, tier-5),
  `event-commands.spec.ts` (move/resize/split builders + undo, tier-5), a month
  repeating-drag tier-6 case, plus the existing week-grid/view-host tier-6 specs.
  Full SPA suite green.
- **v1 deviations / deferred:** no keyboard resize; `splitOccurrence`
  non-undoable (above). Browser (tier-7) verification of the live drag/resize
  gestures not yet run.
### Phase 6+ (deferred) — copy verbs, SPA copy/paste, `instantiateTemplate`,
      `exchangeAllocatable` (month scoped-drag landed early in Phase 5)

Then: rewrite [PRD 056](056-graphql-events-write-api.md) verb notes to the final shape (drop the superseded sketch),
update [PRD 094](094-spa-main-view-actions-and-popups.md) Phase 4 task list, distill §1–§7 into `docs/architecture/reservation-edit.md`.

## Tests

Per-verb tier-3 GraphQL (`@SpringBootTest` + MockMvc / HttpGraphQlTester): happy path
per scope, resize, the split-empties-series cascade, `OCCURRENCE_NOT_FOUND` on stale
occurrence, and a §12 leak variant (non-admin cannot move an unreadable reservation).
Swing fix pinned by `SingleCopyStartTest` (tier-1, done).
