# PRD 101 — Transpose & anchors: the move/copy/paste/template mutation family

**Status:** draft — 2026-07-09; **solution drafted same day** (§ "Drafted solution") —
verb family + typed targets locked in the design dialog; implementation not started.
This PRD is the durable record of a long design dialog (2026-07-09) plus a six-track
research sweep (5-agent workflow over the Swing codebase + 1 multi-reservation deep-dive
+ external API survey). Read this before touching move/copy/paste/template mutations —
it supersedes the interim `moveAppointment` sketch that briefly lived in PRD 056.

**Related:** PRD 056 (reservation write surface — the verbs land there; its
`moveReservations`/`copyReservations(dateShift: Duration)` are the migration targets),
PRD 094 (SPA calendar drag/resize consumer — Phase 4 + D5: scope logic stays server-side),
PRD 091 (recurrence editor — SINGLE-split semantics), PRD 099 (table selection — the SPA
multi-select that feeds bulk verbs), PRD 095 (month grid — the day-granular drag surface),
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
- `checkIdIntegrity` (PRD 056 §9) resolves appointment ids globally → `reservationId`
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
| `copyAppointment` | clone one appointment (rule + exceptions verbatim) **into the same reservation** (Swing "paste into existing"; paste-as-new = OQ1 residue) | new appointment (server id) | no |
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

- **Copy family mints ids server-side** (PRD 056 §9 carve-out: "server-initiated
  creates keep server-generated ids" — the client sends no entity content). Client ids
  on copy/instantiate were also a staleness race (template event count). NOT idempotent:
  a retried copy creates a second copy — documented on the verbs; visible + cheap to fix,
  matches Google/Graph. Optional client id = additive later if a consumer needs it.
- **Responses must return what was created** (never make a consumer diff): copy verbs
  return the clones with ids; `splitOccurrence`/`exchangeAllocatable`-SINGLE responses
  must expose the new appointment id.
- Error codes (extends the PRD 056 taxonomy): `OCCURRENCE_NOT_FOUND` (stale/wrong
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
Maintainer directive; recorded as PRD 094 D5. The SPA never rebuilds
`updateReservation` payloads for scope moves.

**D2 — exceptions are absolute calendar facts; NO operation re-bases them
(2026-07-09, maintainer).** An exception means "no lecture on May 1 *because May 1 is
a holiday*" — the reason is bound to the date, not the series shape. Consequences:
(a) move: keep absolute — Swing/GraphQL status quo confirmed *with rationale*; the
occurrence resurfacing off-holiday after a shift is CORRECT behavior, not a wart;
(b) copy/template: the facade's day-count re-base (`FacadeImpl:1113–1120`) is judged
WRONG under this doctrine — the server port deliberately does NOT copy it (a re-based
exception lands on a meaningless date; an absolute one stays correct for near-range
copies and goes inert for far-range ones ≈ Google/Graph drop-on-copy);
(c) the GraphQL `copyReservations` (which never re-based) is accidentally correct.
Open sub-choice OQ2 (keep-absolute vs drop-on-copy).

**D3 — `until` is asymmetric: absolute on move, length-preserving on copy
(2026-07-09, direction — confirm in solution draft).** "Until end of semester" is
date-reasoned → a move must not extend it (forward moves shrink the series; emptying
may warrant `INVALID_SHIFT`). A far-range copy with an absolute until would be empty →
copy re-bases until to preserve length in days (facade behavior kept for copy only).

**D4 — one transpose implementation server-side.** The facade primitive's anchor rule
(earliest-start, relative offsets, per-variant arithmetic, restriction rewrite) is the
spec; the `AppointmentPaste` millisecond path is subsumed and not ported.

## Open questions

- **OQ1 — verb family final shape**: exact verb list + which take `anchor: Anchor` vs
  positional from/to; naming (`moveAppointment` keeps implicit resize?); does
  paste-into-existing become `addAppointment`-style or a copy-variant flag.
- **OQ2 — exceptions on copy**: keep-absolute (lean — correct for near-range copies,
  inert for far-range) vs drop-on-copy (Google/Graph precedent, cleaner storage).
- **OQ3 — resize/`newEnd` placement**: sibling arg constrained to `dateTime` anchors vs
  inside `DateTimeAnchor`; top/bottom-edge resize both expressible.
- **OQ4 — `WeekdayAnchor`**: third variant now, or defer until the Wochenprogramm SPA
  surface exists? (No industry precedent; rapla's weekday-swap gives the mechanic.)
  Multi-weekday sets need it to be a set edit, not a date shift.
- **OQ5 — resource axis**: `exchangeAllocatable` as its own verb (Swing shape) vs an
  optional `resource: {from,to}` component combinable with a time anchor.
- **OQ6 — orphaned-exception hygiene**: leave forever (status quo) vs opportunistic GC
  on save vs UI-only display filter. (D2 makes orphans *meaningful* — GC may be wrong.)
- **OQ7 — `INVALID_SHIFT` semantics**: currently claimed in PRD 056 prose but not
  implemented; define exactly (move emptying an absolute-end series? until < start?).
- **OQ8 — MONTHLY/YEARLY day-moves silently change pattern rank** (2nd Tue → 3rd Tue).
  Accept (Swing behavior), warn, or reject rank-changing day-moves on monthly series?
- **OQ9 — Swing copy-SINGLE midnight bug** (`:638–641`): fix in Swing, or note-and-leave
  (SPA won't share the code path)?

## Plan

### Phase 0 — this document (findings capture) — DONE 2026-07-09
### Phase 1 — solution draft (NOT started)
- [ ] Resolve OQ1–OQ5 in a design dialog; lock the verb family + anchor input
- [ ] Rewrite PRD 056's verb-level notes + examples to the final shape; drop the
      interim `moveAppointment` sketch + `Duration` migration decision there
- [ ] Update PRD 094 Phase 4 to consume the final verbs
- [ ] Distill the durable Swing findings (§1–§4, §7) into
      `docs/architecture/reservation-edit.md` / a new architecture doc
### Phase 2+ — implementation (test-first per verb; not scoped here)

## Tests

Solution-draft phase: none (docs only). Implementation phases will pin each verb with
tier-3 GraphQL tests (one per scope + split/empty-series edges + §12 permission cases)
per the test-first rule.
