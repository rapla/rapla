# PRD 093 — Loan lifecycle (check-out / return / overdue)

**Status:** draft — 2026-07-06
**Related:** PRD 091 (SPA reservation edit + `resourceAvailability` — the finder that must respect open loans), PRD 092 (free-slot search — *not* required for the lending archetype), `docs/usecases/equipment-planning.md` (archetype C, UC-C3/UC-C4 — the requirements ground truth)

## Abstract

Lending-desk deployments (archetype C) fake a loan lifecycle today: a "ZURÜCK"
pseudo-resource type marks returns, overdue detection is "read the calendar". This PRD
adds a minimal **loan status on the reservation** (planned ▸ out ▸ returned), derives
**overdue** instead of storing it, and makes **open loans block availability beyond their
planned end**. End state: the ZURÜCK pseudo-resource type is obsolete, the SPA shows an
active-loans / overdue table, and a device that is physically out never shows up as free
in the finder.

Packaging: the feature ships as a **plugin** (`org.rapla.plugin.loanstatus`, working
name), following the `planningstatus` precedent — activated per deployment via a
`rapla:entry` config key. Status storage is a **designated classification attribute**
marked by an attribute annotation (D4), so Swing displays/edits it for free and no core
schema changes are needed.

## The status model

```
planned ──checkOut──▸ out ──return──▸ returned
```

- **`planned`** — loan booked, items not yet handed over. Default for every new loan.
- **`out`** — items handed over to the borrower.
- **`returned`** — items back at the desk. Terminal.
- **`overdue` is NOT a status.** It is derived: `status == out ∧ loanEnd < now`. Storing
  it would need a background job to flip it and could drift; deriving it is one predicate
  in the query layer.

Cancellation stays what it is today: deleting the reservation. No `cancelled` state.

## Core rules

1. **Overdue is derived, never persisted** (see above).
2. **An `out` loan blocks its items beyond the planned end, until `returned`**
   ("open-ended until returned"). An overdue camera is physically absent — it must fall
   out of every availability answer (`resourceAvailability`, conflict engine input for
   *new* loans, finder) even though its booked window has elapsed. This is an
   availability-computation rule, not a data change: the loan's effective blocking
   interval is `[start, max(end, now)]` while `status == out`.
3. **No persistent "is lent out" flag on the resource.** Rejected (see D2): it is fully
   derivable (resource is out ⇔ allocated to a loan with `status == out`), a stored
   boolean is a second source of truth needing two entity writes per checkout/return, and
   resource updates are expensive in the multi-pod update-history model. The SPA gets a
   **computed** GraphQL field instead (`Allocatable.currentLoan`, §12-scoped) plus an
   optional finder filter (`excludeCurrentlyOut`) for the walk-in "I need it *now*" case.
4. **§12 applies to `currentLoan`:** who borrowed a device and until when is reservation
   data — expose the loan details only when the caller can read the loan reservation;
   otherwise the field collapses to `isOut: true` with no counterparty, no dates.
5. **§16 applies throughout:** status transitions are mutations; no read path
   (availability query, table lens, `currentLoan`) may write status.

## Goal

- A lending deployment records checkout/return with one click each, sees an
  active-loans + overdue table, and never allocates a ZURÜCK marker again.
- `resourceAvailability` for a window starting today excludes an item whose loan ended
  yesterday but was never returned.
- Tier-3 leak test: unreadable loan → `currentLoan` reveals nothing but `isOut`.

## Scope

### In scope
- Loan status storage on the reservation + wire exposure (GraphQL type + mutation).
- Derived overdue + the open-ended-until-returned blocking rule in availability.
- Computed `Allocatable.currentLoan` / `isOut` + finder filter.
- SPA: status chip in the event sheet, active-loans/overdue table lens, one-click
  checkout/return actions.

### Out of scope
- Resource operational state (defect / in repair / retired) — a *real* resource-level
  state, not derivable from loans. Deliberately separate; candidate follow-up PRD
  (deployments fake it with `rapla:disabled` colors today).
- Reminder mails / borrower notifications.
- Barcode / scanner integration, asset management (serials stay plain attributes).
- Swing UI for the lifecycle (SPA-only, consistent with PRD 090's direction).
- Migration tooling for existing ZURÜCK allocations (the observed deployment has zero —
  the workaround is staged, not in routine use).

## Plan

### Phase 1 — Plugin skeleton + status attribute
- [ ] Plugin `org.rapla.plugin.loanstatus`: enable key (`rapla:entry`), attribute
      annotation key (`loan-status`), resolver helper "loan status of reservation R"
      (annotation lookup, default `planned` when attribute unset).
- [ ] GraphQL: expose `loanStatus` on Reservation (null when lifecycle inactive per D5);
      `setLoanStatus` mutation (or `checkOutReservation`/`returnReservation` verbs —
      OQ3) with transition validation (no `returned → out`), permission = edit right on
      the loan.
- [ ] Tier 2/3 tests: default status, valid/invalid transitions, permission denial.

### Phase 2 — Availability honors open loans
- [ ] Blocking-interval rule `[start, max(end, now)]` for `status == out` in the
      availability computation (PRD 091 resolver path; check interaction with PRD 086
      block index — OQ4).
- [ ] Computed `Allocatable.currentLoan` (§12-scoped) + `excludeCurrentlyOut` filter.
- [ ] Tier-3 leak test per §12 (unreadable loan → `isOut` only).

### Phase 3 — SPA surfaces
- [ ] Status chip + checkout/return actions in the event sheet (PRD 091 Phase 2 host).
- [ ] Active-loans table lens: filter by status, derived overdue column, sort by end.

## Tests

- Tier 2 (`FacadeTestSupport`): transition matrix, overdue derivation, blocking interval.
- Tier 3 MockMvc/GraphQL: mutation permission checks, `currentLoan` leak test
  (mixed-visibility loans, byte-identical to visible-only per §12).
- Availability regression: item with overdue loan absent from `resourceAvailability`
  result for a window after the planned end.

## Open Questions

- **OQ1 — where does the status live?** *Resolution 2026-07-06:* → **D4** (designated
  classification attribute + annotation). Rejected: (b) first-class `Reservation` field
  (core entity + XML/DB schema + update-history + Swing-compat change for a
  single-archetype feature); (c) extending the `planningstatus` plugin — maintainer:
  `planningstatus` is the *publication/planning* state of seminar events (archetype B,
  "in Planung" vs. "geplant"), an orthogonal axis to physical checkout; overloading it
  would conflate two lifecycles.
- **OQ2 — is the lifecycle per event type opt-in?** *Resolution 2026-07-06:* → **D5**:
  double opt-in — the plugin must be enabled for the deployment AND the event type must
  carry the annotated status attribute. No annotated attribute → no checkout UI, no
  blocking rule. Timetabling deployments (archetype A/B) are untouched by default.
- **OQ3 — mutation shape:** generic `setLoanStatus(status)` vs explicit verbs
  (`checkOut`/`return`). Verbs are self-documenting and validate transitions naturally.
  *Resolution:* pending.
- **OQ4 — PRD 086 block index:** the open-ended blocking interval depends on *now*, so
  it cannot be materialized into a static block index. Does the index path need a
  status-aware overlay, or is the loan volume small enough to always resolve open loans
  live? *Resolution:* pending.
- **OQ5 — partial return:** borrower returns 3 of 4 items. v1 answer is "split the
  loan manually"; is per-allocation status ever worth it? *Resolution:* deferred, out of
  v1.

## Decisions locked

**D1 — overdue is derived, not stored.** `out ∧ end < now`. No background job, no drift.
Rejected: stored overdue flag (needs a scheduler to flip it; wrong the moment the clock
passes the end).

**D2 — no persistent lent-out flag on the resource.** Derivable from loan status; a
stored boolean is a second source of truth requiring paired writes (drift on abort,
parallel pods, Swing clients that don't know the field) and makes every checkout a
resource update in the multi-pod update history. Replaced by computed
`Allocatable.currentLoan` + the blocking rule. Rejected alternative: boolean/status
attribute on the Allocatable.

**D3 — open loans block until returned.** Availability treats `status == out` as
`[start, max(end, now)]`. This is what makes the status *load-bearing* instead of
decorative — without it, an overdue device shows as free and gets double-lent.

**D4 — status = designated classification attribute + annotation (2026-07-06).** A
category-typed attribute on the loan event type, marked with an attribute annotation
(working key: `loan-status`) following the existing annotation pattern
(`color`/`sorting`/`categorization`). Swing displays/edits it with zero Swing code; the
availability rule and GraphQL resolve it via the annotation. No core schema change.
Rejected: first-class `Reservation` field; reuse of `planningstatus` (orthogonal axis —
see OQ1).

**D5 — packaged as a plugin, double opt-in (2026-07-06).** Ships as
`org.rapla.plugin.loanstatus` (working name), `planningstatus` precedent: a
`rapla:entry` enable key per deployment. Active lifecycle on an event requires plugin
enabled AND the annotated attribute on its event type — either missing means no checkout
UI and no blocking rule, so archetype A/B deployments are untouched.
