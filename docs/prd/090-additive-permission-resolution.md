# PRD 090 — Purely additive permission resolution + soft-deny migration

**Status:** in-progress — Phases 1–4 landed 2026-06-28 (server resolver + migration + worklist + admin
REST API + SPA dialog + docs, all tested). Editor effective-access transparency deferred to a future SPA
permission editor (no Swing work). Phase 5 cleanup is post-migration future.
**Related:** [ADR 0003](../decisions/0003-permissions-are-grant-only.md) (revised 2026-06-28 — records this decision); `docs/architecture/permissions.md`; `docs/spec/permissions.md`

## Abstract

Replace rapla's `USER > GROUP > WORLD` **precedence** resolution with a **purely additive**
model: a user's effective access is the *highest* level granted by any matching row (union),
with **no precedence and no subtraction**. This abolishes the "soft deny" — both the `DENIED`
level *and* the user-row-below-their-group cap (which precedence honoured as a downward override).
A dhbw store audit measured the real impact at **2 entities** (1 active, 0 events), so the change
is near-zero-impact. Migration is a one-shot at first boot that freezes the affected allocatable
ids into a worklist; an admin SPA UI drains it. Resolution stays a **single live model** (additive);
precedence survives only as throwaway one-shot code.

## Implementation

### The one resolver (landed)
- `RaplaDefaultPermissionImpl.hasAccess` takes the `max` access level over every matching row
  (user / group / world), reusing `PermissionContainer.Util.getUserEffect` only to decide *matching*
  (membership), **not** for precedence weighting. `getInterval` unions the windows of every qualifying
  grant. The old precedence branch is gone from the live path.
- `DENIED` (0) is the floor → inert under `max` (an already-dominated row is skipped, which also skips
  `DENIED`). A user-below-group row is dominated → inert. Neither subtracts anything ever again.
- The migration keeps its *own* precedence + additive level calc in `SoftDenyAnalyzer` (the only place
  precedence survives, to compute the flip's escalation diff) — deleted with that class in Phase 5.

### The migration (one-shot, `GraphqlKeyMigration` pattern)
- Marker-guarded one-shot at first boot of the transition release. Uses the **old precedence calc**
  (kept only inside the one-shot) vs `additiveEffectiveLevel` to find the **true escalation set**:
  `(entity, principal)` where `additive > precedence`.
- Persist the result as a **frozen list of allocatable ids** in a system preference. Nothing else is
  stored — principal, type, levels, and the explanation text are **recomputed from live permissions**
  when the GUI renders.
- After the one-shot, the live resolver is additive forever. **A later release deletes the precedence
  code entirely.**

### List minimization (keep the worklist tiny)
1. **True-diff filter** (the big one): keep a finding only where `additiveEffective > precedenceEffective`.
   The real precedence calc already drops owner/admin bypass, expired time-windows, and
   multi-group-covered users — dhbw `2,778 structural → 1`.
2. **Group-keying**: a `GROUP`-deny-over-world is one finding (the group), not one-per-member.
3. **Table keyed by allocatable**: one row + one checkbox per allocatable (findings listed within if
   more than one); sort by highest escalated level so the admin triages by risk. Never filter by magnitude.

### Worklist resolution — "Resolved" means different things per finding form
A finding is either a **`DENIED` row** or a **soft-deny user-row-below-group**. They resolve
differently because `DENIED` is *inexpressible and fully inert* whereas a soft-deny user row is a
*valid, GraphQL-expressible grant that may carry out-of-window access* — so it must not be pruned:

- **`DENIED` finding → prune.** Checking "resolved" **strips the inert `DENIED` row(s)**. Safe always
  (`DENIED` does literally nothing under `max`), and it makes the allocatable additive-clean →
  GraphQL-saveable (the input enum has no `DENIED`). After pruning, recompute finds nothing → the
  finding self-clears without needing a flag.
- **Soft-deny finding → accept (acknowledge), do *not* prune.** The dominated user row stays: it is a
  valid grant, round-trips through GraphQL fine, and **may grant access outside a time-windowed group
  grant** — pruning it could *remove* real access (a de-escalation), which is wrong. So "resolved"
  here only sets the per-allocatable `acknowledged` flag; accepting the escalation changes no
  permission data, so the inert-during-the-window row would otherwise nag forever.
- **Recompute-clean (either form):** the admin edited/restructured (e.g. dropped the `DENIED`, or
  removed the user from the group to *preserve* the limit) → recompute finds no soft-deny → drops off
  automatically.

An allocatable may carry both forms; one "resolved" check does the right thing for each (prune the
`DENIED`s, ack the soft-denies). So persisted state = the frozen allocatable ids **+ an `acknowledged`
boolean each** (default false) — the flag is load-bearing only for soft-deny findings; `DENIED`-only
allocatables drop off by recompute after pruning. Everything else (principal, levels, text) is
recomputed from live permissions for display.

### Admin migration endpoint (REST, like api-keys / auth) + SPA migration UI (a simple table)
- A **REST** `@RestController` under `/api/` (admin-scoped) — *not* GraphQL. The migration UI is an
  operational admin tool, not domain data, so it sits with the auth / api-key REST surfaces (AGENTS.md
  §14 "GraphQL-only" is about data). Follows PRD 049's `@HttpExchange`-interface routing and the
  `rest-endpoint-creation` conventions.
  - `GET` → **structured findings** (not pre-baked strings) for the un-acknowledged, still-soft-deny
    allocatables: `{entityId, entityName, form, principalType, principalName, currentLevel, additiveLevel, via}`.
  - `POST`/`PATCH .../{allocatableId}/resolve` → set the per-allocatable `acknowledged` flag.
- The SPA renders a table **keyed by allocatable** — one row, **one checkbox per allocatable**. An
  allocatable may carry **more than one** finding (several users/groups gaining access); they are
  listed together in that row:

  | Allocatable | Who gains access | Resolved |
  |---|---|---|
  | MA-Flügel | user *username1*: READ → REQUEST | ☐ |
  | RoomX | group *groupname2*: — → READ; user *userZ*: READ → EDIT | ☐ |

  Checking **Resolved** acknowledges the **whole allocatable** (PATCH its `acknowledged` flag) → the
  allocatable is **removed from the list**, covering all its findings at once. Names resolved at
  display; **only ids + the ack flag persisted** (§17). Optional self-explaining sentence per finding
  for a tooltip/expanded view.
- §12: the endpoint leaks usernames/group names → enforce admin read-scope server-side + tier-3 leak test.
- **Admin-only and transient.** It's invisible to normal API/GraphQL consumers (not domain data), and
  can be **removed in a later release** once deployments have migrated — same throwaway lifecycle as
  the precedence one-shot.

### All surfaces inherit the change centrally
- Permission resolution has a single authority: `RaplaDefaultPermissionImpl` (the only
  `PermissionExtension`) behind `PermissionController`, in **rapla-core** (shared by Swing client *and*
  server). Changing it to additive flips **every** surface at once — Swing GUI, calendar/iCal export
  (`Export2iCalConverter` filters by `canRead`), GraphQL, and the §12 REST output filters. No
  per-surface permission logic to touch. Server stays authoritative, so a stale webclient shows old UX
  hints but can't grant wrong access (rebuild + resign webclient jars so hints match).
- **`PermissionIndex` must be rebuilt at the flip.** The cached read-model
  (`storage/impl/server/readmodel/PermissionIndex.java`) bakes in resolved access; the one-shot (or
  boot) must rebuild it under additive, or index-served queries keep answering with precedence.
- **Custom deployments**: dhbwrapla inherits additive on rebuild and runs the one-shot on its store; if
  it ships its own `PermissionExtension`, that must adopt additive independently — verify before the flip.

### Editor transparency for new rows (deferrable)
- The permission editor shows **live additive effective access** per principal, so a newly-created
  soft-deny is a visible no-op. Optional hint on a dominated row. No hard block — `DENIED` is already
  removed from the new-row dropdown (Swing, done); a user-below-group is two valid rows and inert.

### GraphQL implications (future permission-write API — deferred)
- The migration is **REST, not GraphQL** (admin-only operational tool). GraphQL needs no migration verb.
- When a permission-*write* mutation lands (PRD 063 OQ2), additive makes it simpler/safer:
  - **Input enum `SettableAccessLevel` without `DENIED`** (output enum keeps `DENIED` to read legacy rows).
  - **Whole-list-replace is now safe** — additive has no load-bearing subtraction to accidentally drop,
    so a `setPermissions(allocatable, [grants])` shape no longer risks silent escalation.
  - **A `DENIED` row blocks the save** — it is inexpressible in the input enum. `setPermissions`
    **refuses** an allocatable that still has a `DENIED` row; the caller must resolve it first
    (the migration UI's "resolved" = prune does exactly this, and the prune is itself behaviour-
    preserving since `DENIED` is inert). This is the only thing the write API rejects.
  - **A soft-deny (user-below-group) is two valid grants** → it **round-trips fine** and does *not*
    block the save. Accept it and return the computed `effectiveAccess` so the client sees the no-op
    (reveal, don't hard-block — mirrors the editor). The migration UI's "resolved" for these is
    *accept (acknowledge)*, not prune, so they remain expressible.
  - Add an **`effectiveAccess(forUser/forGroup)`** read field — clean `max` under additive.
- PRD 069 access-query: **`accessLevel: DENIED` becomes degenerate** (matches all) → drop it from that
  filter input or document as match-all.

### Already landed (compatible, keep)
- Swing `DENIED` deprecation (`PermissionField` filters it off new rows; renders existing as
  *"(deprecated)"*).
- Load-time redundant-`DENIED` normalizer (`PermissionContainer.Util.normalizeRedundantDenies`, wired
  into `FileOperator`/`DBOperator`) — behaviour-preserving hygiene, independent of the flip.

## Goal

- `RaplaDefaultPermissionImpl` resolves additively — `userReadCapsBelowGroupAllocate`-style tests
  *invert* (the user gets the group level); a new tier-2 suite pins max-wins, deny-inert, and
  user-grant-still-elevates.
- On a store with legacy soft-denies, first boot writes a frozen allocatable-id worklist; the SPA
  migration UI lists exactly the true-escalation entities (dhbw: 1) with self-explaining text.
- No precedence branch remains in the live access path (grep + arch check).

## Scope

### In scope
- Additive resolution change + the shared `additiveEffectiveLevel` helper.
- One-shot migration computing + freezing the allocatable-id worklist.
- Admin findings endpoint (structured) + SPA migration GUI (render + recompute-to-resolve).
- Test rewrites for the inverted precedence cases; new additive pins.
- ADR 0003 already revised; update `architecture/permissions.md` + `spec/permissions.md` to additive.

### Out of scope
- A gated-flip / `requireAck` opt-in — **explicitly rejected** (Option A only; see D3).
- Physically deleting inert soft-deny rows from stores (optional later hygiene).
- Retiring the `DENIED` level from the type system (later, once stores are clean).
- The editor effective-access display may ship in a follow-up phase.

## Plan

### Phase 1 — Additive resolver ✅ (2026-06-28)
- [x] Switch `RaplaDefaultPermissionImpl.hasAccess` + `PermissionContainer.Util.getInterval` to additive
  `max`; remove the live precedence branch.
- [x] `PermissionIndex` inherits additive automatically — it delegates to `PermissionController.canRead`
  and is rebuilt fresh on boot (in-memory), so the code deploy *is* the rebuild.
- [x] Replaced `GrantOverridesDenyAtEqualPrecedenceTest` with `AdditivePermissionResolutionTest`
  (11 tests: max-wins, deny-inert, user-grant-elevates, group-below-world; cascade/union unchanged).
  Inverted `PermissionIndexScenarioTest.userDeniedNoLongerOverridesGroupRead`.
- [x] All surfaces inherit centrally (single `PermissionExtension` in rapla-core) — full server lane green (418).

### Phase 2 — One-shot migration + worklist ✅ (2026-06-28)
- [x] `SoftDenyAnalyzer` (throwaway precedence+additive calc + structural pre-filter, OQ1 future-cap skip).
- [x] `AdditivePermissionMigration` marker-guarded one-shot → frozen allocatable-id list in the system pref
  (`AdditiveMigrationState`); wired as `migrateAdditivePermissionsIfNeeded()` after `migrateGraphqlKeysIfNeeded()`.
- [x] INFO-log count of resources with a silent deny/cap removed.
- [x] Tier-2 `AdditivePermissionMigrationTest` (escalation freeze + OQ1 exclusion + idempotence).

### Phase 3 — Admin REST endpoint + SPA migration UI ✅ (2026-06-28)
- [x] `PermissionMigrationService` (`@HttpExchange("/api/admin/permission-migration")`, rapla-core) +
  `PermissionMigrationController` (admin-gated, 403 for non-admin): `GET /findings` (recompute live,
  skip acknowledged/clean), `POST /{id}/resolve` (prune `DENIED` + ack). Tier-3 `PermissionMigrationControllerTest` (leak + flow).
- [x] SPA: `PermissionMigrationService` + `PermissionMigrationDialogComponent` (table keyed by allocatable,
  one checkbox each, who-gains-access list). Toolbar menu entry shown **only to a global admin AND only
  when the worklist is non-empty** (fetched on init, re-checked after the dialog closes so draining the
  last item hides it). Vitest service + toolbar specs.

### Phase 4 — Editor transparency (OQ2)
- [x] Docs synced to additive: ADR 0003 Confirmation/Future, `architecture/permissions.md`, `spec/permissions.md`.
- [x] Swing: the `DENIED`-deprecation dropdown filtering already landed — **no further Swing work** (decided
  2026-06-28: the legacy Swing editor won't get the live effective-access display).
- [ ] **Deferred to a future SPA permission editor:** live additive effective-access display / dominated-row
  hint belongs with the SPA's permission-editing UI when it exists — not built here (there is no SPA
  permission editor yet). Tracked for that future PRD.

### Phase 5 — Cleanup (later)
- [ ] Delete the precedence one-shot code (`SoftDenyAnalyzer` + migration) once deployments have migrated;
  consider retiring `DENIED`.

## Tests

- Tier-2 (`rapla-server`, `FacadeTestSupport`): additive resolution matrix — max-wins, `DENIED` inert,
  user-grant-elevates, user-row-below-group **no longer caps**, cascade + union unchanged. (Rewrites
  + extends `GrantOverridesDenyAtEqualPrecedenceTest`; keeps `RedundantDenyNormalizationTest`.)
- One-shot migration test: a fixture store with each soft-deny form → worklist contains exactly the
  true-escalation allocatable ids; expired-window / owner / admin / multi-group-covered cases excluded.
- Tier-3 MockMvc: admin findings endpoint enforces read-scope (non-admin / out-of-scope → no leak).
- Recompute-to-resolve: edit a flagged entity's permissions → finding drops off.

## Open Questions

*All resolved 2026-06-28.*

- **OQ1** — future-windowed caps (limit set to start later): include in the worklist? *Resolution:*
  **No — exclude.** They never apply post-flip and the user never saw the cap bite, so there is nothing
  for an admin to review. The migration skips findings that arise only from rows whose time-window is
  not currently in effect (future-start or expired).
- **OQ2** — does Phase 4 (editor effective-access) ship with Phase 1–3 or as a follow-up? *Resolution:*
  **Ship all phases 1–4 together.**
- **OQ3** — system-pref worklist vs a dedicated store for the id list? *Resolution:* **Server system
  preference**, written once by the one-shot (the marker, the frozen id list, and the acknowledged-id
  set all live there).

## Decisions locked

**D1 — Purely additive, single live model.** Effective = `max` over all matching rows; no precedence,
no subtraction. One resolver in production; precedence is throwaway one-shot code, deleted later.
Rationale + blast-radius evidence in ADR 0003 (revised 2026-06-28). Rejected: keeping precedence
(its downward override is a soft deny); a permanent dual-resolver / per-request switch.

**D2 — Soft deny is one concept.** `DENIED` and user-below-group are the same subtraction in two
forms; additive neutralizes both. The migration, the audit, and the deprecation treat them uniformly.

**D3 — Option A only: flip immediately, migrate after.** The flip is the deploy; escalated users gain
their *group's* level immediately and keep it until the admin resolves the worklist — a **bounded,
tiny (dhbw: 1), visible, accepted** temporary elevation. The gated-flip / `requireAck` opt-in is
**rejected** — it re-introduces a pre-flip window where new permissions get old semantics and a
gated dual path. Simplicity of one live model wins.

**D4 — Persist ids + an `acknowledged` flag; recompute the rest. "Resolved" prunes `DENIED` but
accepts soft-deny. Resolution is per-allocatable.** The worklist is the frozen list of allocatable ids
(written once by the one-shot), each with one `acknowledged` boolean — **one checkbox per
allocatable**, even when it carries several findings; checking it covers the whole allocatable at once.
Checking "resolved" does the form-appropriate thing for each finding on it:
- **`DENIED` finding → prune** the inert row (safe always; makes it additive-clean and GraphQL-
  saveable). Self-clears by recompute afterwards — no flag needed for `DENIED`-only allocatables.
- **soft-deny finding → accept** (set `acknowledged`); the dominated user row is **not** pruned —
  it is a valid grant and may carry access outside a time-windowed group grant, so stripping it could
  *remove* real access. The flag is load-bearing here because accepting changes no permission data.

An allocatable also leaves the list by **recompute-clean** when the admin instead edits/restructures
(drops the `DENIED`, or removes the user from the group to *preserve* the limit). Principal, type,
levels, explanation text are recomputed from live permissions for display. No legacy flag *on the
permission rows* (they have no creation timestamp — legacy/new is not derivable, only snapshot-able
into this worklist).

**D5 — No hard block on new soft-denies; reveal instead.** `DENIED` stays removed from the new-row
dropdown; a user-below-group is two valid rows and inert, so the editor *shows effective access*
(a visible no-op) rather than forbidding it.
