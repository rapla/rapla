# PRD 076 — Scoped API keys + self-rotation

**Status:** draft — 2026-06-20
**Related:** PRD 043 (API key mechanism — server-minted asymmetric JWT, this builds on it), PRD 071 §H7 (origin: "API key has no server-side max TTL")

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
| **Management** | `rotate` | may the key rotate itself (issue a same-scope successor) |

Semantics (start simple, extend later):
- `read` — read-only (everything the owning user can see).
- `write_events` — mutate events (reservations/appointments); implies read.
- `write_resources` — mutate resources (allocatables); implies read.
- `write_all` — mutate everything; implies read.
- `rotate` — **management, orthogonal to the data scopes.** Additive: e.g. a key with
  `{read, rotate}` can read app data and rotate itself, but write nothing.

Two invariants:
- **`rotate` is not a data-write capability.** A `{read, rotate}` key rotating itself never
  lets it mutate app data — the management axis is independent of the data axis.
- **Rotation never escalates.** The successor inherits the predecessor's scope set
  **exactly** (including whether it keeps `rotate`). A read-only key can only rotate into a
  read-only successor. Rotation is not a privilege-escalation vector.

## Goal

- A `read`-scoped key cannot mutate anything (write attempt → 403, byte-identical to a
  permission denial); a `write_events` key can mutate events but not resources; `write_all`
  can mutate both.
- A key with `rotate` can issue a same-scope successor and revoke itself, with no data-write
  power; a key without `rotate` cannot self-rotate.
- Default scope for a new key is `{read}` (least privilege); any write/rotate scope is
  explicit opt-in.

## Scope

### In scope
- A per-key **scope set** (`read`, `write_events`, `write_resources`, `write_all`, `rotate`)
  stored with the key metadata + carried/derived on the resolved authentication.
- Write-boundary enforcement that rejects a mutation whose entity kind (event vs resource)
  is not covered by the key's scopes.
- `POST /api/auth/api-keys/{id}/rotate` — gated by the `rotate` scope; issues a same-scope
  successor, revokes the old (grace overlap — see OQ2).
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

### Phase 2 — write-boundary enforcement
- [ ] Reject a mutation whose entity kind isn't covered: events need `write_events` or
      `write_all`; resources need `write_resources` or `write_all`; `read`/`rotate`-only →
      reject. Seam TBD (OQ1) — operator `storeAndRemove`/`SecurityManager` (sees entity
      kinds) vs a request-scoped scope set on `RemoteSession`. Must cover REST + GraphQL
      uniformly (cf. §12 mindset).
- [ ] Tier-3 leak test: read-only key write → 403 (identical to a permission denial);
      `write_events` key event-write → ok, resource-write → 403; `write_all` → both ok.

### Phase 3 — self-rotation
- [ ] `POST /api/auth/api-keys/{id}/rotate` gated by the `rotate` scope: mint a same-scope
      successor, return it once, revoke the old (grace per OQ2).
- [ ] Verify: a `rotate`-scoped key rotates/deletes only ITS OWN key (not another user's),
      the successor's scope set equals the predecessor's (no escalation), and a key without
      `rotate` gets 403 on rotate.

### Documentation
- [ ] `docs/authentication.md` — api-key scope + self-rotation flow
- [ ] `docs/architecture/rest-api.md` — the rotate endpoint + scope param

## Tests

`ApiKeyScopeTest` (read-only key write → 403; write key → ok), `ApiKeySelfRotationTest`
(rotate issues same-scope successor, old revoked, no cross-user, no escalation), plus the
existing `ApiKeyController`/`ApiKeyJwtDecoder` tests stay green.

## Open Questions

- **OQ1 — write-boundary seam.** Where is "this principal may not mutate" enforced so it
  covers REST + GraphQL + internal uniformly? Operator `storeAndRemove`/`SecurityManager`
  vs a request-scoped read-only flag on `RemoteSession`. *Resolution:* pending.
- **OQ2 — rotation overlap.** Does `rotate` revoke the old key immediately, or give it a
  short grace TTL so in-flight clients don't break (the AWS/GCP overlap)? *Resolution:*
  pending — lean toward immediate (client already holds the new key) with manual `DELETE`
  available for explicit overlap.
- **OQ3 — backward compat.** Existing keys have no `scope` in their stored entry. Treat a
  missing scope as `write` (preserve today's behaviour) or `read` (secure default, may
  break existing automation)? *Resolution:* pending — lean `write` for existing keys,
  `read` default only for newly-created ones.

## Decisions locked

**D1 — no forced max-TTL.** API keys are revocable (membership check), unlike access
tokens. A forced expiry breaks automation for no real gain. Rejected; H7 closed via
revocation + scoping instead.

**D2 — fixed simple scope vocabulary to start.** `read`, `write_events`, `write_resources`,
`write_all` (data) + `rotate` (management). Event/resource granularity matches rapla's two
main entity classes; finer per-resource scopes are deferred. `rotate` is a scope, not
implicit possession — explicit and auditable.

**D3 — `rotate` is orthogonal to data scopes and never escalates.** `rotate` grants only
self-rotation, no data-write power; a `{read, rotate}` key writes nothing. The successor
inherits the predecessor's scope set exactly. Matches AWS/GCP separation of credential
lifecycle from resource permissions.

**D4 — rotation needs no new mint mechanism, scoping does.** Overlap rotation already works
via create+delete (AWS/GCP model); the `rotate` endpoint is a same-scope convenience over
it. PRD 043 stays the base mint mechanism.

**D5 — default `{read}` (least privilege) for new keys; any write/rotate scope is explicit.**
(Existing keys' default handled by OQ3.)
