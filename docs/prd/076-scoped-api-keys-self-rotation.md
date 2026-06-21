# PRD 076 — Scoped API keys + self-rotation

**Status:** done — 2026-06-21 (all three phases shipped + tested; 39 tests green). One
discovered out-of-scope gap noted under Phase 2: api-key Bearer tokens can't drive GraphQL
mutations yet (caller resolved by username, not subject) — enforcement is still uniform at the
operator seam, so it applies automatically once that's fixed.
**Related:** PRD 043 (API key mechanism — server-minted asymmetric JWT, this builds on it), PRD 071 §H7 (origin: "API key has no server-side max TTL")
**Prior art:** GitLab [`rotate_self` token scope](https://gitlab.com/gitlab-org/gitlab/-/issues/430748) (the `rotate_self` name + endpoint-bound self-rotation), AWS IAM `${aws:username}`-scoped self key-rotation, Stripe Restricted API Keys + GitHub fine-grained PATs (per-resource read/write least-privilege model)

## Abstract

rapla API keys today authenticate as their owning user and inherit that user's **full
permissions** — a leaked key can write/delete everything the user can. They also have no
server-side maximum lifetime (H7). Rather than force expiry (which breaks automation),
this PRD shrinks the blast radius of a leak by giving each key a **data scope**
(read-only by default), and adds **possession-based self-rotation** so a key can replace
itself without ever needing a data-write scope. End state: a leaked read-only key can read
only what its owner can see and can be rotated/revoked, never mutate data or escalate.

## Background — the two findings this resolves

- **H7 (max TTL).** Largely already mitigated: api_key JWTs are verified by a **membership
  check against `RaplaKeyStorage.getAPIKeys`** (`ApiKeyJwtDecoder`), so **deleting a key
  revokes it immediately** — unlike stateless access tokens, which rely on a short TTL
  because they cannot be revoked. A never-expiring key is therefore *revocable*, not
  "valid forever". A forced max-TTL would break long-running automation; rejected.
- **Rotation is already possible** with today's primitives: `POST` (new key, old still
  valid) → migrate → `DELETE /{thumbprint}` (revoke old). That **is** the AWS/GCP
  overlap-rotation model — multiple simultaneous keys + per-key revoke. No dedicated
  "rotate" endpoint is strictly required (AWS/GCP don't have one either).
- **The real gap** is least-privilege: keys are all-or-nothing full-permission. Industry
  practice (AWS/GCP) separates **credential lifecycle** from **resource permissions** on
  two independent axes — which is exactly what enables a low-privilege key to still manage
  its own lifecycle.

## Scope model (the core design)

A key carries a **set of scopes** drawn from a small fixed vocabulary. Two kinds:

| Kind | Scopes | Governs |
|---|---|---|
| **Data** | `read`, `write_events`, `write_resources`, `write_all` | what the key may read / mutate |
| **Management** | `rotate_self` | may the key rotate itself (issue a same-scope successor) |

The `rotate_self` name is taken verbatim from the established analog — GitLab's
[`rotate_self` token scope](https://gitlab.com/gitlab-org/gitlab/-/issues/430748), whose
motivation is identical: without it a scoped automation token must either take a full-write
scope just to re-key itself (violating least privilege) or rely on a second high-privilege
token. Using the same name makes the capability self-documenting.

Semantics (start simple, extend later):
- `read` — read-only (everything the owning user can see).
- `write_events` — mutate events (reservations/appointments); implies read.
- `write_resources` — mutate resources (allocatables); implies read.
- `write_all` — mutate everything; implies read.
- `rotate_self` — **management, orthogonal to the data scopes.** Additive: e.g. a key with
  `{read, rotate_self}` can read app data and rotate itself, but write nothing.

Three invariants:
- **`rotate_self` is not a data-write capability.** A `{read, rotate_self}` key rotating
  itself never lets it mutate app data — the management axis is independent of the data axis.
- **Rotation never escalates.** The successor inherits the predecessor's scope set
  **exactly** (including whether it keeps `rotate_self`). A read-only key can only rotate into
  a read-only successor. Rotation is not a privilege-escalation vector.
- **`rotate_self` reaches ONLY the self-rotate / self-delete endpoints (D10).** It must NOT
  open the generic `POST /api/auth/api-keys` create endpoint — otherwise a `{read, rotate_self}`
  key could mint itself a fresh `write_all` key and the "never escalates" invariant collapses.
  Like GitLab's scope, it is endpoint-bound to rotation, nothing else.

## Goal

- A `read`-scoped key cannot mutate anything (write attempt → 403, byte-identical to a
  permission denial); a `write_events` key can mutate events but not resources; `write_all`
  can mutate both.
- A key with `rotate_self` can issue a same-scope successor and revoke itself, with no
  data-write power; a key without `rotate_self` cannot self-rotate.
- Default scope for a new key is `{read}` (least privilege); any write/`rotate_self` scope is
  explicit opt-in.

## Scope

### In scope
- A per-key **scope set** (`read`, `write_events`, `write_resources`, `write_all`,
  `rotate_self`) stored with the key metadata + carried/derived on the resolved authentication.
- Write-boundary enforcement that rejects a mutation whose entity kind (event vs resource)
  is not covered by the key's scopes.
- `POST /api/auth/api-keys/{id}/rotate` — gated by the `rotate_self` scope; issues a same-scope
  successor, sets a short grace TTL on the old (see OQ2/D7).
- Self-revoke already exists (`DELETE /{id}`) — confirm it is reachable by the key itself.

### Out of scope
- Fine-grained per-resource scopes (only the coarse read/write axis here).
- Forced max-TTL (rejected — see Background).
- Changing the asymmetric mint/discard mechanism (PRD 043 owns that).
- Swing client changes.

## Plan

### Phase 1 — scope set
- [ ] `ApiKeyController.CreateRequest` gains `scopes` (set; default `{read}`); validated
      against the fixed vocabulary, stored in the key metadata JSON (`serialiseEntry`) and
      echoed in `KeyMetadata`.
- [ ] `ApiKeyJwtDecoder` surfaces the stored scopes as authorities/claims on the resolved
      `Jwt` so downstream enforcement can read them.

### Phase 2 — write-boundary enforcement ✅ (2026-06-21)
- [x] Reject a mutation whose entity kind isn't covered: events need `write_events` or
      `write_all`; resources need `write_resources` or `write_all`; `read`/`rotate_self`-only →
      reject. Seam = `LocalAbstractCachableOperator.check()` → `guardApiKeyScopes` (D6) — both
      REST and GraphQL converge there via `operator.dispatch()`. Scopes reach the storage-layer
      seam via `ApiKeyScopeContext` (a Spring-free static bridge), populated from
      `SecurityContextHolder` by `ApiKeyScopeContextInitializer`. `null` scope set ⇒ caller is
      not a scoped api-key ⇒ unrestricted (interactive session / internal thread).
- [x] Tier-2 `ApiKeyScopeGuardTest` (10 cases, guard logic at the chokepoint, all axes + remove
      path + unrestricted) and tier-3 `ApiKeyWriteScopeTest` (real chain: read/`write_events`
      keys → 401, `write_all` → 200 on a REST `User` write). Rejection throws
      `RaplaSecurityException` → 401, byte-identical to a permission denial.

> **Discovered gap (out of PRD 076 scope):** the GraphQL mutation controllers
> (`AllocatableMutationController`/`ReservationMutationController`) resolve the caller via
> `requireCaller()` using the `preferred_username` claim / `auth.getName()`. An api-key JWT
> carries neither a username nor `preferred_username` (only `sub` = user UUID), so api-key
> Bearer tokens **cannot drive GraphQL mutations at all today** (`caller not resolvable`),
> independent of scopes. The scope ENFORCEMENT is still uniform — it sits at the shared operator
> chokepoint, so the moment GraphQL caller-resolution is fixed, scopes apply there automatically.
> Fixing `requireCaller()` to also resolve by subject is a separate change; flagged for the user.

### Phase 3 — self-rotation + grace-expiry enforcement ✅ (2026-06-21)
- [x] **Decoder enforces `min(jwt.exp, stored.exp)` (D9).** `ApiKeyJwtDecoder.earliest(...)` takes
      the earliest present of the JWT and stored `exp`. **Missing `stored.exp` ⇒ ignored**
      (fall back to `jwt.exp`; absent → never expires). `ApiKeyExpiryTest` proves: past stored
      exp rejects despite future JWT exp; no-`exp` legacy entry still authenticates.
- [x] `POST /api/auth/api-keys/{id}/rotate` gated by `rotate_self`: mints a same-scope successor
      (via shared `mintAndStore`), returns it once, then sets the OLD entry's `exp = now + grace`
      (default 5 min, `?graceSeconds=` override, `0` = immediate) — D7. The two key-storage
      writes run inside `ApiKeyScopeContext.callUnrestricted` so the scope guard doesn't block a
      low-privilege key persisting its own successor (key storage lands in Preferences).
- [x] `ApiKeySelfRotationTest`: rotates only ITS OWN key (id must equal caller's `kid`), successor
      scope set equals predecessor's (no escalation), key without `rotate_self` → 401, **api-key
      cannot reach the generic create endpoint (D10)**, old key valid within grace then rejected.

### Documentation
- [x] `docs/authentication.md` — api-key scope + self-rotation flow
- [x] `docs/architecture/rest-api.md` — the rotate endpoint + scope param

## Tests

`ApiKeyScopeTest` (read-only key write → 403; write key → ok; existing no-`scopes` entry ⇒
`write_all`), `ApiKeySelfRotationTest` (rotate issues same-scope successor, old key valid
within grace then rejected after, no cross-user, no escalation), and `ApiKeyExpiryTest` —
**the backward-compat guard**: an existing stored entry with NO `exp` field still authenticates
after D9 (missing `stored.exp` must not be read as expired). Existing `ApiKeyController`/
`ApiKeyJwtDecoder` tests stay green.

## Open Questions

*(all resolved 2026-06-21 — see Decisions locked D6/D7/D8)*

- **OQ1 — write-boundary seam.** ✅ **Resolved → `LocalAbstractCachableOperator.check()`.**
  Both REST mutation controllers and GraphQL mutation resolvers converge on
  `operator.dispatch(UpdateEvent) → preprocessEventStorage → check(evt, store)` (verified:
  `ReservationMutationController`/`AllocatableMutationController` and the REST path both call
  `operator.dispatch(evt)`). Enforcing in `check()` covers REST + GraphQL + in-process facade
  callers with one implementation. Entity kind is read from `evt.getStoreObjects()`
  (`entity.getTypeClass()`) and `evt.getRemoveIds()` (`ref.getType()`). See D6.
- **OQ2 — rotation overlap.** ✅ **Resolved → short server-side grace TTL on the old key**
  (default 5 min, `?graceSeconds=` override). Revised from the initial "immediate revoke" lean
  after web research (AWS/GCP/Stripe all favour bounded overlap) and the multi-pod reality: a
  shared key cached across pods needs a brief window to pick up the successor. Enabled by D9
  (decoder enforces `min(jwt.exp, stored.exp)`, tightening-only). Indefinite overlap stays the
  manual create→migrate→`DELETE` path. See D7 + D9.
- **OQ3 — backward compat.** ✅ **Resolved → missing `scopes` ⇒ `write_all` for existing
  keys; `{read}` default only for newly-created keys.** Scope lives in the **stored key entry**
  (the `serialiseEntry` JSON blob the decoder already parses for the membership check), not
  the signed JWT claim — so it is server-side migratable and read for free at verify time. A
  pre-existing entry simply has no `scopes` field; the decoder defaults that to `write_all`,
  which is behaviour-identical to today (existing keys already carry the user's full write
  power). Non-breaking. See D8.

## Decisions locked

**D1 — no forced max-TTL.** API keys are revocable (membership check), unlike access
tokens. A forced expiry breaks automation for no real gain. Rejected; H7 closed via
revocation + scoping instead.

**D2 — fixed simple scope vocabulary to start.** `read`, `write_events`, `write_resources`,
`write_all` (data) + `rotate_self` (management). Event/resource granularity matches rapla's two
main entity classes; finer per-resource scopes are deferred. `rotate_self` is a scope, not
implicit possession — explicit and auditable. The name is taken verbatim from GitLab's
[`rotate_self` token scope](https://gitlab.com/gitlab-org/gitlab/-/issues/430748) so the
capability is self-documenting and matches the established analog.

**D3 — `rotate_self` is orthogonal to data scopes and never escalates.** `rotate_self` grants
only self-rotation, no data-write power; a `{read, rotate_self}` key writes nothing. The
successor inherits the predecessor's scope set exactly. Directly mirrors two external
precedents: GitLab's `rotate_self` (a token that can rotate itself without taking a full-API
scope or a second high-privilege token) and AWS IAM, which scopes self-rotation to the caller's
own credential via the `arn:…:user/${aws:username}` resource pattern — the same "only its own
key" boundary this PRD enforces. Both embody the AWS/GCP separation of credential lifecycle from
resource permissions.

**D4 — rotation needs no new mint mechanism, scoping does.** Overlap rotation already works
via create+delete (AWS/GCP model); the `rotate` endpoint is a same-scope convenience over
it. PRD 043 stays the base mint mechanism.

**D5 — default `{read}` (least privilege) for new keys; any write/`rotate_self` scope is
explicit.** (Existing keys' default handled by D8.)

**D10 — `rotate_self` is endpoint-bound: it reaches ONLY the self-rotate (`POST
…/api-keys/{id}/rotate`) and self-delete (`DELETE …/{id}` of its own key) endpoints, never the
generic create `POST /api/auth/api-keys`.** Otherwise a `{read, rotate_self}` key could mint
itself a fresh `write_all` key, defeating D3's no-escalation guarantee. This matches GitLab's
`rotate_self`, which is bound to the rotation endpoint and grants no other API access. The
rotate endpoint structurally mints a SAME-scope successor only (no caller-supplied scope set);
the generic create endpoint stays gated by a full user session / non-`rotate_self` credential.

**D6 — write-boundary seam = `LocalAbstractCachableOperator.check(UpdateEvent, EntityStore)`.**
The single chokepoint both REST controllers and GraphQL resolvers reach via
`operator.dispatch(evt)`. Scope enforcement here is uniform across REST + GraphQL + in-process
facade callers — no per-controller duplication, no GraphQL-shaped hole (§12 mindset). Entity
kind is derived from `evt.getStoreObjects()` / `evt.getRemoveIds()`. Rejected: a request-scoped
read-only flag on `RemoteSession` checked per-controller — would have to be re-applied at every
write site and would miss any new mutation path.

**D7 — rotation sets a short server-side grace TTL on the old key (not an immediate hard
revoke).** On `rotate`: mint the same-scope successor, then rewrite the OLD key's stored entry
to `exp = now + grace` (default 5 min, overridable via `rotate?graceSeconds=`; `0` = immediate).
The old key keeps working briefly and auto-expires — bounding the overlap without an indefinite
dual-key phase and without a manual `DELETE`. This is the AWS/GCP "phased rotation / grace
period" pattern, scoped to a short fixed window. Why not immediate hard revoke: rapla is
multi-pod capable, so a shared automation key may be cached across pods; a 0-second overlap can
break in-flight callers that haven't picked up the successor yet. Why not indefinite overlap:
that's the manual create→migrate→`DELETE` path, still available for deliberate long migrations.
Depends on D9 (server must be able to tighten a key's effective expiry).

**D9 — effective expiry = the EARLIEST of the present `exp` values; the server can only tighten,
never loosen; a MISSING `exp` is ignored.** Today `ApiKeyJwtDecoder` enforces expiry from the
immutable JWT `exp` claim only (`claims.getExpirationTime()`, line 147–150) and ignores the
`exp` field already written into the stored entry. D9 makes the decoder ALSO honour the stored
`exp`, taking `effectiveExp = min(jwt.exp, stored.exp)` over whichever are present. Properties:
- **Tightening-only.** The signed JWT `exp` is the hard ceiling; the stored `exp` can only pull
  expiry *earlier*, never extend it. Enables D7's grace-revoke and a general
  *revoke-with-grace* (shorten any key's TTL) that doesn't exist today (only hard `DELETE`).
- **Backward compatible — the load-bearing rule.** `stored.exp` is OPTIONAL. A pre-existing
  entry that has NO `exp` (a never-expiring key, today's common case) MUST be read as "no
  server-side limit" → fall back to `jwt.exp` alone; if that is also absent the key never
  expires, exactly as today. A missing `stored.exp` must NEVER be interpreted as `0`/epoch/now
  — that would instantly kill every existing never-expiring key. Same shape as D8 (missing
  field ⇒ preserve current behaviour). A mandatory regression test asserts an existing
  no-`exp` entry still authenticates after D9 lands.

**D8 — scope is stored in the key entry, not the JWT; missing scope ⇒ `write_all`.** The
authoritative scope source is the per-key `serialiseEntry` JSON blob (already parsed by
`ApiKeyJwtDecoder` during the membership check), so it is server-side migratable and read at no
extra cost. The signed JWT is immutable in the client's hand and is NOT the scope source. An
existing key's blob has no `scopes` field → defaults to `write_all`, behaviour-identical to
today (full write power) → backward-compatible, non-breaking. Only newly-minted keys get the
`{read}` default (D5).
