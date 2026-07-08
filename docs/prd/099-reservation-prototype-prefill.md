# PRD 099 — Reservation prototype: server-computed prefill for new events

**Status:** implemented — 2026-07-08 (Phases 1–4 done, uncommitted on
spring-boot; open: live browser smoke + the optional dropdown hardening;
templates/permission-preview deferred per D6/OQ1)
**Related:** PRD 096 (classification editor — consumes the prototype in the sheet),
PRD 056 (events write API — create/update semantics, client-minted ids §9),
PRD 090 (additive permissions — the permission model the create-seed feeds into)

## Abstract

New events in the SPA start as empty client drafts, but the server silently
applies attribute defaults (`DynamicTypeImpl.newClassification()`) on create —
the user first sees a default (e.g. a loan status) after save/reload. This PRD
makes the prefill visible: a side-effect-free `reservationPrototype(typeKey)`
query returns the server-computed default classification, the SPA seeds new
drafts (and type switches) from it. Ride-alongs: the GraphQL create path is
missing Swing's `copyPermissions(type, reservation)` (a real parity bug), and
the input null-semantics must distinguish "clear" from "omitted" before
defaults become visible.

## Implementation

Key design decisions from the 2026-07-08 design dialog (see Decisions locked):
prototype is a QUERY, not a persisted draft and not a mutation; ids stay
client-minted (PRD 056 §9 unchanged); permissions are a create-seed applied
server-side, never client-supplied, never remapped on type change; templates
are OUT of scope but constrained the API shape (list-form, re-keying,
value-faithful copy).

## Goal

- Opening a new-event sheet for a type with attribute defaults shows those
  defaults prefilled (e.g. loan = "Geplant"), and the saved event matches the
  preview byte-for-byte.
- A reservation created via GraphQL carries the same permission list a
  Swing-created one would (type permissions copied at create).
- Clearing a defaulted attribute in the sheet and saving keeps it cleared.

## Scope

### In scope
- `reservationPrototype(typeKey: String!)` GraphQL query (values only).
- SPA: seed `newDraft` from the prototype; per-typeKey cache in
  `ClassificationSchemaService` (same lifetime as the SDL cache — app reload
  refreshes); type-switch gap-fill (remapped values win, defaults fill holes).
- `copyPermissions(dt, r)` in `createReservation` (+ batch create path).
- Input null-semantics: explicit `null` clears, omitted key falls back to the
  type default.

### Out of scope
- Templates (instantiate-from-template, picker, multi-reservation batch
  create) — future PRD; design constraints recorded below.
- A persisted server-side draft entity (rejected — D1).
- Server-allocated ids (rejected — D2).
- Event permission EDITING in the SPA (future permission editor; the
  prototype query grows a permissions preview field then).

## Plan

### Phase 1 — createReservation permission parity (bugfix, test-first) — DONE 2026-07-08
- [x] Tier-3 red test: `createReservationCopiesTypePermissions` — READ copied,
      CREATE/READ_TYPE not (red: permission list was `[]`).
- [x] Fix: `PermissionContainer.Util.copyPermissions(dt, r)` in
      `ReservationMutationController.createReservation` AND
      `buildReservationFromCreateInput` (batch path). Note:
      `AllocatableMutationController.createAllocatable` already had the copy —
      the reservation create was the outlier.
- [x] Update path leaves entity permissions untouched (it never touches the
      permission list — type change included, D4).

### Phase 2 — input null-semantics — DONE 2026-07-08
- [x] Tier-3 red test: `explicitNullClearsDefaultedAttributeOmittedKeyGetsDefault`
      (topic default set via facade in-test; red: null resurrected the default).
- [x] Fix `buildClassificationFromInput` in BOTH controllers (the method is
      duplicated in `ReservationMutationController` +
      `AllocatableMutationController`): key present with null →
      `setValueForAttribute(attr, null)`; key absent → `newClassification()`
      default stays (replace semantics locked by the test's third assert).

### Phase 3 — reservationPrototype query — DONE 2026-07-08
- [x] Schema: `reservationPrototype(typeKey: String!): ReservationPrototype!`
      — `{ typeKey, classification }` reusing the generated classification
      types; deliberately NO id/owner/lastModifiedAt/permissions.
- [x] Resolver in `ReservationGraphQLController` (record + @QueryMapping):
      `dt.newClassification()`; unknown / non-RESERVATION / `!canCreate` all
      throw the IDENTICAL error (REFERENCE_NOT_FOUND "not found or not
      creatable") — §12 existence non-leak.
- [x] Tier-3: `reservationPrototypeReturnsTypeDefaults` (topic default via
      facade → echoed; no-default attrs null),
      `reservationPrototypeNonCreatableAnswersLikeUnknown` (monty on `room`
      vs unknown key — error strings identical modulo the echoed key).

### Phase 4 — SPA seeding — DONE 2026-07-08
- [x] `ClassificationSchemaService.prototype(typeKey)`: per-typeKey cache
      (shareReplay, SDL-cache lifetime; transport errors NOT cached), reuses
      `valueSelections` + `normalizeClassificationValues`; null for
      unknown/non-creatable/non-RESERVATION types.
- [x] LAZY fetch: sheet open fetches ONE prototype (the draft's own type).
      `setTypeKey` awaits the target prototype (3 s timeout → remap-only)
      before mutating — remap + gap-fill ONE undoable step; latest-wins
      token for rapid double-switches. Prefetch of all dropdown types stays
      a noted option, not built.
- [x] New-event seeding (`seedDefaults`): applies only while the draft is
      pristine (no history, not dirty, same typeKey, not persisted); nulls
      stay OMITTED (wire: absent = default, explicit null = clear); baseline
      includes seeds → fresh draft not dirty, history empty.
- [x] Type switch gap-fill (new AND persisted — Swing `newClassificationFrom`
      parity): remapped values win, defaults fill gaps only. A→B→A refills
      with defaults; full restore via Undo.
- [ ] Optional hardening (not built): filter the type dropdown to typeKeys
      present in the parsed SDL.
- [x] Tier-6 specs: seeding (not dirty, no history, nulls omitted),
      type-switch gap-fill + one-step undo, remap-only fallback (prototype
      null). Explicit-null clearing is covered tier-3 (Phase 2).

## Tests

Tier 3: permission-parity test, null-semantics test, prototype-query test
(incl. §12 non-leak) in the mutation/classification controller test classes.
Tier 5/6: draft seeding, dirty baseline, type-switch merge in
`event-draft.spec.ts` / sheet specs. Live probe: create an event of a type
with a defaulted category, verify sheet preview == saved entity.

### Ride-along — VALUE_LIST enum input never resolved (live bug 2026-07-08) — DONE
User report: "changing loan from geplant to verliehen and saving does not
work". Root cause: the @oneOf variant field for a VALUE_LIST category
attribute is the generated enum whose values are LEAF KEYS, but CATEGORY
input coercion resolved by ID only — the enum key resolved to null and the
value was silently dropped on every create/update (reservations); the
allocatable controller had NO coercion at all (raw pass-through predating
the SPA editor). Fix: resolve by id first (tree input), then by leaf key
below the attribute's root-category constraint. Tier-3 red→green:
`valueListEnumInputResolvesToCategoryOnCreateAndUpdate` (event.belongsto,
enum values discovered via introspection) +
`valueListEnumInputResolvesToCategoryOnUpdate` (allocatable). DEDUP (user
call-out): the whole @oneOf classification-input mapping (builder +
coercion + key search) moved to the shared `ClassificationInputMapper`;
both controllers keep a one-line delegate — the two private copies had
already drifted once (the allocatable pass-through was the drift).
Second sweep: `resolveType` unified via
`ClassificationInputMapper.tryResolveType` (also used by the prototype
resolver), and `AllocatableMutationController.requireCaller` switched from
its hand-rolled preferred_username lookup to the shared `JwtUserResolver`
— the hand-rolled path was the documented naive-lookup antipattern that
silently fails for external-IdP (Keycloak/Entra) tokens, i.e. allocatable
mutations were broken for externally-authenticated users.

## Open Questions

- **OQ1** — should `reservationPrototype` also return the type-derived
  permission list as a read-only preview once the SPA grows an event
  permission editor? *Resolution:* deferred until that editor exists; the
  response type is extensible.
- **OQ2** — defaults preview: `reservationPrototype` query (D3's pick) vs. a
  `@defaultValue` SDL directive. *Resolution:* 2026-07-08 — QUERY (D3 stands,
  reaffirmed after full weighing). Directive rejected because: (a) it would be
  a second implementation of `newClassification()` semantics (SDL-gen encode +
  client parse + draft apply vs. one executed code path); (b) value encoding
  in the SDL can't carry entity-reference defaults — the SDL is global and
  unfiltered, an allocatable-id default would leak existence (§12), so v1
  would need a "these valueTypes have no preview" footnote; (c) a directive is
  static-only — computed defaults (current date/semester, per-user) are
  query-only territory; (d) SDL descriptors stay metadata-only (structure vs.
  values layering). Roundtrips were dismissed as an argument (user); the
  type-switch atomicity concern is solved LAZILY: `setTypeKey` awaits the
  target prototype (cache or fetch) before mutating, so remap + gap-fill stay
  ONE undoable mutateDraft step (prefetching all dropdown types stays a noted
  option, not built). Residual staleness = mid-session schema drift — the
  same class PRD 096 D1 already accepts (rare; save validates, reload heals).
  Hardening (optional): filter the type dropdown to types present in the
  parsed SDL so options/descriptors/prototypes are guaranteed one consistent
  snapshot.

## Decisions locked

**D1 — Prototype is a side-effect-free QUERY, not a persisted draft, not a
mutation.** Server computes (`newClassification()`), returns, stores nothing —
§16-clean, per-typeKey cacheable (covers the type switch), gracefully degrades
to today's empty draft. Rejected: persisted server drafts (garbage/TTL,
cancel becomes a delete, drafts leak into queries/conflicts, fights memento
undo — Swing itself does not persist before save); a draft-building MUTATION
whose only write is id allocation (collapsed once D2 removed the id argument).

**D2 — Ids stay client-minted (PRD 056 §9 unchanged).** Typed UUIDs make
collisions negligible and the ID_COLLISION check remains as the retry/
idempotency contract. A server-built draft would have solved the id question
only for the initial state anyway — appointment ids are minted continuously
during editing (+ Termin), so server ids would have created a mixed regime or
per-click roundtrips. If server-authoritative ids are ever wanted, that is a
separate id-strategy decision, not a draft-design ride-along.

**D3 — @defaultValue SDL directive rejected.** With the prototype query the
default semantics stay 100% server-side (`newClassification()` — the exact
code the create runs), preview and persisted result can never diverge, and no
default-value encoding (category keys, dates, lists) enters the SDL. The SDL
descriptor pipeline (PRD 096 D1/D5) stays attribute-STRUCTURE only.

**D4 — Permissions are a CREATE-SEED, not a live binding to the type**
(user decision 2026-07-08). `copyPermissions(type, reservation)` happens
server-side at create — exactly Swing's `FacadeImpl.newReservation` seam. The
client never supplies initial permissions (tamper surface). A later type
change does NOT remap/re-copy permissions (an admin may have customized them;
silent replacement would destroy data) — anyone wanting different initial
permissions uses a template ("für alles andere gibt es templates"). For
unsaved drafts this is automatically correct: permissions do not exist until
create, which copies from the then-current type.

**D5 — Type change stays save-time-only (reaffirmed).** No per-switch server
call, no `reshapeReservation`: the sheet remaps client-side (PRD 096), the
save is a plain update with the new typeKey. The prototype cache is therefore
the defaults source on switch.

**D6 — Template design constraints (recorded, NOT built).** Templates are
`rapla:template` Allocatables holding 1..n reservations
(`RaplaFacade.getTemplateReservations`). The future instantiation surface is
a QUERY on the same line as the prototype —
`reservationsFromTemplate(templateId): [ReservationPrototype!]!` — list-form
from day one (multi-reservation templates exist; v1 UI handles length == 1).
Server copies value-faithfully via the existing Swing copy logic (strips ids,
owner, lastModifiedAt, template binding; keeps classification, appointments
incl. repeating rules/exceptions, allocations incl. appointment subsets);
client re-keys ALL ids and applies a template-wide date shift to the target
slot (relative distances between reservations preserved); save = existing
create mutation, multi later via batch create. Template-specific permissions
(deviating from the type) ride the template query when it comes. §12:
template visibility filtered at the output boundary — existence must not
leak.
