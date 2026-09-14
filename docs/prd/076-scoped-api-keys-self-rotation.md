# PRD 076 — Scoped API keys + self-rotation

**Status:** REOPENED 2026-06-25 — Phases 1-3 shipped 2026-06-21; a follow-up redesign (Phase 4
+ 5 below, from the 2026-06-25 code-smell audit) is in progress. Phase 4 (no-legacy default,
config token-gate, bootstrap strip, dualis exemption) is **landed + green**; Phase 5 (api-keys
GraphQL-only + `access_details` at the GraphQL seams) is **planned, not yet built**.
**Phase 6** (interactive human rotation from the SPA + grace cap + expired-entry compaction,
from the 2026-06-27 SPA key-management UI) is **in progress** — see Plan §Phase 6, D7 update, D11/D12.
**Related:** [PRD 043](043-api-keys-jwt-pat.md) (API key mechanism — server-minted asymmetric JWT, this builds on it), [PRD 071](done/071-web-security-hardening.md) §H7 (origin: "API key has no server-side max TTL")

**Follow-up findings (2026-06-24, code-smell audit):** two limits of the "one write chokepoint" model surfaced and are recorded here so they aren't re-discovered:

1. **Reads are not scope-gated at all.** The scope axis bounds *writes only*. The single
   `ApiKeyScopeContext.current()` consultation is `guardApiKeyScopes`, which iterates the
   `UpdateEvent`'s store/remove sets — there is **no read-side chokepoint**. Reads
   (`getResources` → `getVisibleEntities`, `queryAppointments`, GraphQL fetchers) are filtered
   solely by the *user's* `PermissionController.canRead*`, never by the *key's* scopes. So `{read}`
   is a floor/marker (the key may do GETs of everything its user can see); there is no
   `read_events`/`read_resources` split and no write-only key. Adding real read scopes needs a new
   read chokepoint (the read surface has no single `dispatch()`-style funnel). **Deferred** — when
   tackled, likely a new PRD. Documented in `docs/authentication.md` §"Reads are NOT scope-gated".
2. **Non-`dispatch()` writes bypass the guard** and must gate by hand. `ImportExportManager.saveData`
   (bulk import/export/restore), raw JDBC and file writes emit no `UpdateEvent`, so
   `guardApiKeyScopes` never sees them. Concretely the `ArchiverService` (`backup`/`restore`/`delete`)
   was reachable by an admin's read-only key. **Fixed 2026-06-24:** added
   `ApiKeyScopeContext.requireWriteAllForBulk(...)`, called from `ArchiverServiceImpl.checkAccess()`
   (gates on `write_all`); regression lock `ArchiverServiceAccessTest`; convention added to the
   `rest-endpoint-creation` skill so future non-dispatch writes gate themselves.
**Prior art:** GitLab [`rotate_self` token scope](https://gitlab.com/gitlab-org/gitlab/-/issues/430748) (the `rotate_self` name + endpoint-bound self-rotation), AWS IAM `${aws:username}`-scoped self key-rotation, Stripe Restricted API Keys + GitHub fine-grained PATs (per-resource read/write least-privilege model)

## 2026-06-25 redesign — api-keys are GraphQL-only (Phases 4 + 5)

The 2026-06-25 audit (see *Follow-up findings* above) led to a sharper authorization boundary that
supersedes the original "an api-key is usable on **every** authenticated endpoint" assumption.

### Decision
- **api-keys are confined to a small allow-list of URL surfaces; everywhere else they are rejected
  by token-kind** (deny-by-default — the inverse of the original model). The interactive
  RemoteOperator/REST surface (`/api/storage/**`, the config controllers, settings) is
  **user-token only** — an api-key has no business driving `dispatch`, `changePassword`, the
  bootstrap, or reading server-side credential config.
- api-keys operate on the **GraphQL layer** (`/api/graphql`) — where integrations actually read.
  DualisAPIImpl is the one REST write exception.

### api-key allow-list
| Surface | api-key? | note |
|---|---|---|
| `/api/graphql/**` | ✅ | the core surface (reads today; mutations once they resolve the caller by `sub`) |
| `/api/users/**` | ✅ | narrow §12 surface: `GET /api/users` (UserSummary = username+displayName, admin-visible-filtered) + `GET /api/users/me` (own id/username/displayName). No email/groups. |
| `/api/dhbwsync/**` | ✅ | Dualis exception (writes via `callUnrestricted`, role-gated) |
| `POST /api/auth/api-keys/{id}/rotate` | ✅ | self-rotation runs *through* the key |
| `/rapla/ical`, `/rapla/calendar` | n/a | already public (`?user=` published), not bearer-gated for anyone |
| `/api/storage/**`, config controllers, settings, `/api/auth/api-keys` POST/GET/DELETE | ❌ | user-token only |

### Consequence
Closing the REST write path means api-keys can currently only **read** (GraphQL queries). The
write scopes (`write_events`/`write_resources`/`write_all`) stay in the vocabulary but are
**latent** until GraphQL mutations resolve the caller by `sub` (the existing Phase-2 gap). The only
active api-key write is Dualis (scope-exempt). `ApiKeyWriteScopeTest`'s REST-write scenario is
superseded and will be reworked to the GraphQL path.

### Phase 4 — landed 2026-06-25 (green)
- **No-legacy default:** `ApiKeyScopes.resolveStored(empty)` + the decoder default → `{read}` (was
  `write_all`); `LEGACY_FULL` removed. Pre-scopes keys are now read-only; the one legacy writer
  (dualis) is exempted via `ApiKeyScopeContext.callUnrestricted`.
- **read floor on create:** `normaliseForNewKey` always includes `read` (a stored scopes array is
  never write-only — "at least read is set").
- **`access_details` scope added** (`hasAccessDetails`; landed as `read_users`/`canReadUsers`,
  renamed in Phase 5 to the generalized name): gates sensitive identity/permission expansions —
  user PII + (future) resource permission lists. `write_all` implies it; plain `read`/`write_events`
  do not.
- **Config token-gate:** `ApiKeyScopeContext.requireInteractiveSession(...)` rejects api-keys from
  the 5 plugin system/admin config reads (Mail/JNDI/Exchange/ICal/EventTimeCalc). SettingsController
  (`getSystem`/`getCalendar`) deliberately left open — non-sensitive display metadata.
- **Bootstrap strip consistency:** `RemoteStorageController.getResources` now strips `.server.*`
  from ALL prefs (system AND user-owned), matching `processClientReadable` — closes the
  user-`refreshToken` bootstrap leak.
- Tests: `ApiKeyScopesTest`, `ApiKeyScopeContextTest`, `ArchiverServiceAccessTest`, updated
  `ApiKeyScopeTest`; dhbwrapla `DualisAPIImpl` `callUnrestricted` wrap.

### Phase 5 — planned (not built)
1. **Central token-kind gate** in `SecurityConfig`: an api-key JWT reaches only the allow-list
   above, else 403. One chokepoint; the Phase-4 per-config-controller gates become
   defense-in-depth.
2. **Sensitive-expansion enforcement = one generalized scope + a schema directive**, field-level,
   NOT a type-level block. The critical exposure is never the type itself but its *expandable*
   sensitive fields — for users: `groups` (permission / membership structure), `isAdmin`,
   `authSource`, `email`; for resources (future): a permission/access-control list. These are two
   *categories* (`read_user`, `read_permissions`) of one sensitive class, governed by a **single
   scope `access_details`** — no growing `expand_X` zoo.

   **Mechanism (declarative, one enforcement point):** a GraphQL schema directive
   `@requiresAccessDetails(kind: USER | PERMISSIONS)` tags the sensitive fields; ONE instrumentation
   reads it and enforces. The `kind` arg is for schema readability / audit only — enforcement is the
   single `access_details` scope. Benefits: the complete sensitive surface is
   `grep @requiresAccessDetails schema.graphqls` (not scattered resolver code); a new sensitive
   field is gated by tagging it in the *schema*, so it can't be forgotten; and because the tag sits
   on the field, every path that reaches it is covered automatically — `query.users`/`user`/`search`,
   the `Allocatable.owner` / `Reservation.owner` expansions, the top-level `groups`/`group(id)`
   queries. Leave `id`/`username`/`name` untagged (the §12-narrow "who owns this" surface, matching
   the `/api/users` allow-list). Person-type allocatables: identity (`name`) is the display surface;
   sensitive classification attributes follow the same per-field tagging.

   **Two orthogonal dimensions, composed with AND — must NOT break admin user-management.** The
   `access_details` check is the *token-kind/scope* dimension and applies to **api-keys only**: an
   interactive session (or internal thread) has `ApiKeyScopeContext.current() == null` and therefore
   ALWAYS passes it. So the gate reads `current() == null || hasAccessDetails(current())`, and only
   THEN runs the existing `canAdminUser`/`canAdminGroup`/`canRead` *permission* filter. Net effect:
   an admin (or group-admin) doing user management via the SPA/Swing (a user token) sees groups and
   permissions exactly as today — `access_details` never subtracts from an interactive session, it
   only adds a scope barrier for api-keys (an admin who wants a user-management or permission-audit
   *automation* mints that key with `access_details`).

   **Resource permissions today:** as of 2026-06-25 the GraphQL `Allocatable` type exposes NO
   permission-list field (only `owner`, itself covered; `accessLevel` occurrences are [PRD 069](069-graphql-resource-access-read-api.md) filter
   *inputs*, not output) — no current resource-permission leak. The directive is the guardrail: when
   such a field is added it gets `@requiresAccessDetails(kind: PERMISSIONS)` and is gated by the same
   `access_details` scope from day one.

   *Naming note:* the Phase-4 `read_users` scope is renamed to `access_details` to reflect this
   generalization (enforcement isn't built yet, so it's a cheap constant + test rename).
3. Rework `ApiKeyWriteScopeTest` to the GraphQL path.

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
- Changing the asymmetric mint/discard mechanism ([PRD 043](043-api-keys-jwt-pat.md) owns that).
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

### Phase 6 — interactive rotation + key lifecycle ⏳ (2026-06-27)
Driven by the SPA "Manage API keys" UI ([PRD 043](043-api-keys-jwt-pat.md) §Angular UI). The shipped rotate (Phase 3) is
machine-only — endpoint-bound to the api-key's own credential (D10). The SPA user needs to rotate
their own keys from the browser cookie session, and expired entries must not accumulate.
- [x] **Unified rotate endpoint (D12).** `POST /api/auth/api-keys/{id}/rotate` accepts EITHER an
      api-key bearer OR a cookie-session user. Authorization branches by principal: an **api-key**
      may rotate ONLY itself (token `kid == id`) and must hold `rotate_self` (preserves D3/D10
      no-escalation — the successor inherits the rotated key's scopes); a **human user** may rotate
      ANY key they own (resolved from their own keystore → no cross-user, no existence leak, §12).
- [x] **`graceMinutes` (D7 update).** Renamed `graceSeconds` → `graceMinutes`. Default **180 min**;
      **server rejects > 2 days (2880) → HTTP 400**; `0` = immediate. The successor gets a fresh
      **180-day** expiry (matches the SPA create default; does not violate D1 — that forbids a forced
      *key* TTL, this is a default, null still allowed).
- [x] **OLD key sheds `rotate_self` on rotation (D13).** Successor keeps it (rotation repeats); the
      rotated-away old key loses it (can't self-rotate during grace). Only the latest token in a chain
      can rotate ⇒ revoking it strips rotation from the whole chain.
- [x] **Max-2-per-chain sprawl guardrail (D14).** Successor stores `prev`; a 2nd rotation while the
      predecessor is still live → **409**. Pure anti-sprawl, explicitly NOT a compromise control.
- [x] **Expired-entry compaction (D11).** On every keystore write (create / rotate / revoke), prune
      stored entries whose `exp` is already past; `list()` filters expired entries out. No
      decoder-side deletion (§16 — reads stay side-effect-free), no scheduled sweep ([PRD 089](089-server-side-recents-favorites.md) D6 pattern).
- [x] Tier-3 `ApiKeyInteractiveRotationTest` (5/5): human rotates own key (old valid during grace);
      `graceMinutes > 2880` → 400; cross-user id → not-found (§12); chain capped at 2 (409 then ok
      after delete); expired entry pruned + absent from `list()`. `ApiKeySelfRotationTest` (6/6)
      adapted to `graceMinutes` + D13/D14.

### Documentation
- [x] `docs/authentication.md` — api-key scope + self-rotation flow
- [x] `docs/architecture/rest-api.md` — the rotate endpoint + scope param
- [ ] `docs/authentication.md` / `rest-api.md` — refresh for the unified rotate + `graceMinutes` + compaction (Phase 6)

## Tests

`ApiKeyScopeTest` (read-only key write → 403; write key → ok; existing no-`scopes` entry ⇒
`{read}` read-only per Phase 4, write → 403), `ApiKeySelfRotationTest` (rotate issues same-scope successor, old key valid
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
- **OQ3 — backward compat.** ✅ **Resolved → missing `scopes` ⇒ `{read}` (fail-safe read-only),
  for both existing and newly-created keys.** ~~missing `scopes` ⇒ `write_all` for existing
  keys; `{read}` default only for newly-created keys~~ — **superseded by Phase 4** (`LEGACY_FULL`
  removed; pre-scopes keys are now read-only). Scope lives in the **stored key entry**
  (the `serialiseEntry` JSON blob the decoder already parses for the membership check), not
  the signed JWT claim — so it is server-side migratable and read for free at verify time. A
  pre-existing entry simply has no `scopes` field; `ApiKeyScopes.resolveStored(empty)` and the
  decoder default that to `{read}`, so a leaked pre-scopes key can no longer write. The one
  legacy writer (dualis) is exempted via `ApiKeyScopeContext.callUnrestricted`. See D8.

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
it. [PRD 043](043-api-keys-jwt-pat.md) stays the base mint mechanism.

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

> **Phase 6 update (2026-06-27).** The grace param is now `graceMinutes` (renamed from
> `graceSeconds` — endpoint unpublished), **default 180 min**, and the **server caps it at 2 days
> (2880) → HTTP 400** so a "grace" can't become an unbounded second lifetime. The successor minted
> on rotate gets a fresh **180-day** expiry (was: null/never), matching the SPA create default.

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

**D8 — scope is stored in the key entry, not the JWT; missing scope ⇒ `{read}` (fail-safe).** The
authoritative scope source is the per-key `serialiseEntry` JSON blob (already parsed by
`ApiKeyJwtDecoder` during the membership check), so it is server-side migratable and read at no
extra cost. The signed JWT is immutable in the client's hand and is NOT the scope source. An
existing key's blob has no `scopes` field → defaults to `{read}` (read-only), as fixed by Phase 4
(`LEGACY_FULL` removed). ~~defaults to `write_all`, behaviour-identical to today~~ — **superseded
by Phase 4**: pre-scopes keys can no longer write; the sole legacy writer (dualis) is exempted via
`ApiKeyScopeContext.callUnrestricted`. New keys get the same `{read}` default (D5).

**D11 — expired key entries are pruned by opportunistic write-time compaction, not a scheduled
sweep or a read-path delete (Phase 6).** D9 makes an expired key *unusable* (decoder rejects on
`exp < now`), but nothing removed the dead stored entry — it lingered in the user's Preferences
keystore and still showed in `list()`. Fix: on every keystore write (create / rotate / revoke),
drop entries whose `exp` is already past, and filter expired entries out of `list()`. Rejected
alternatives: (a) **decoder-side deletion** — the decoder is a read/verify path; deleting there
violates AGENTS.md §16 (reads stay side-effect-free); (b) **scheduled sweep** — heavier, and the
codebase favours opportunistic compaction (this is exactly the [PRD 089](089-server-side-recents-favorites.md) D6 / `UserListsService`
pattern). Accepted tradeoff: a user who rotates once and never touches keys again leaves one dead
entry (unusable, invisible in the UI) until their next write — bounded, not unbounded growth.

**D12 — one rotate endpoint for both machine and human; authorization branches by principal
(Phase 6).** `POST /api/auth/api-keys/{id}/rotate` accepts an api-key bearer OR a cookie-session
user — same endpoint, no human/machine fork (it is unpublished, so the contract was changed
freely). The authorization, however, MUST differ: an **api-key** may rotate ONLY itself (token
`kid == id`) and must hold `rotate_self`; a **human user** may rotate ANY key they own. Why the
asymmetry is mandatory: the successor inherits the rotated key's scopes (D3), so if a
`{read, rotate_self}` api-key could rotate a *sibling* `write_all` key it would mint itself a
`write_all` successor — escalation. A human session already holds full authority over its own
keys, so "any owned key" is safe there. Ownership is resolved from the caller's own keystore, so
an id the caller doesn't own is indistinguishable from a nonexistent id (no existence leak, §12).
D12 supersedes D10's "endpoint-bound to the key's own credential" framing for the human case;
D10 still governs the api-key case (a key reaches only self-rotate/self-delete, never `create`).

**D13 — on rotation the OLD key sheds `rotate_self`; the successor keeps it (Phase 6).** The
successor inherits the predecessor's scope set EXACTLY — *including* `rotate_self` — so rotation is
repeatable (the new key can be rotated again). The rotated-away OLD key, however, has `rotate_self`
stripped from its stored scopes at the same moment its `exp` is grace-shortened. Mechanism: scope
lives in the stored entry (D8), which the decoder reads on the old key's next call — so within the
grace window the old key still authenticates (in-flight callers keep working) but can no longer
self-rotate. Why: you rotate because the old credential may be compromised; if the old key could
still self-rotate during grace it could mint its OWN successor and re-establish persistence, side-
stepping the rotation. Stripping `rotate_self` from the old key (a pure de-escalation of a
key that is about to expire anyway) closes that. The successor keeping `rotate_self` is essential —
without it you could only ever rotate once. Locks `ApiKeySelfRotationTest`
(`rotateSelfIssuesSameScopeSuccessor` = successor keeps `rotate_self`;
`rotatedOldKeyLosesRotateSelfButSuccessorKeepsIt` = old key 401s on re-rotate, successor rotates
once its predecessor is deleted).

**D14 — at most 2 live tokens per rotation chain; this is a SPRAWL guardrail, not a security
control (Phase 6).** Each rotation successor stores its predecessor's kid (`prev`). A rotation is
refused with **HTTP 409** if the key being rotated still has a `prev` that resolves to a
*still-valid* (un-expired, un-deleted) entry — so a chain never holds more than the active key +
one grace predecessor. **Why it exists:** without it, rapid re-rotation with a small grace window
could accidentally pile up an unbounded number of simultaneously-valid tokens. **Why it is NOT a
compromise defence** (explicitly): a stolen `rotate_self` token is contained by *revoking the
active key* (immediate DELETE), never by this cap — in fact the cap is irrelevant to an attacker
who simply holds the latest token. It only bounds accidental sprawl. Routine slow rotation is
unaffected: by the next cycle the predecessor has long since expired (grace ≤ 2 days) and been
compacted (D11), so it no longer blocks. Locks `ApiKeyInteractiveRotationTest.chainIsCappedAtTwoLiveTokens`
(2nd rotation → 409; allowed again after the predecessor is deleted).

**Security model (recorded so it isn't re-litigated):** rotation is a *routine hygiene* feature
(zero-downtime key refresh), **NOT** an incident-response tool. Grace deliberately keeps the old
token alive briefly, and `rotate_self` is a self-renewing credential — both are wrong for
containment. To contain a compromise you **REVOKE** (immediate `DELETE`, no grace) the active key
— D13 guarantees only the latest token in a chain can rotate, so deleting it strips rotation from
the whole chain (the grace predecessor can't rotate and self-expires). `rotate_self` stays opt-in
(default `{read}`, D5) and is documented as a persistence risk if leaked. This is written up in
`docs/authentication.md` § API keys.

**D15 — rotation requires the TARGET key to hold `rotate_self`, uniformly for human AND machine
callers (Model B, Phase 6).** Supersedes D12's "a human may rotate ANY key they own." Rationale
(user, 2026-06-27): D12 let a logged-in owner rotate even a `read`-only key, so `rotate_self` — a
scope literally named for rotation — was invisible/irrelevant in the SPA, and "only the latest
token can rotate" (D13) held only for the machine path. **Inconsistent ⇒ users get sceptical.**
Model B is one rule: a key is rotatable iff its **stored** scopes (D8 — never the JWT, which
carries no scopes at all) include `rotate_self`. The SPA shows the rotate button only for
`rotate_self` keys; a `read`-only key is not rotatable (delete + create a fresh one). The human
path still differs in ONE inherent way — a cookie session has no `kid`, so it can't be restricted
to "self only" (the machine's `kid == id` check) — but the owner rotating any of *their own*
`rotate_self` keys is safe. Composes with D13: the rotated-away old key has `rotate_self` stripped
from its stored entry, so it is **immediately non-rotatable even via the UI** (button vanishes,
server 401s) — "only the latest token rotates" now holds everywhere. Locks
`ApiKeyInteractiveRotationTest` (`readOnlyKeyIsNotRotatable`, `rotatedOldKeyIsNoLongerRotatable`)
+ SPA `isRotatable` button gate.

> **Note on the wire format (D8 recap):** the api-key the client holds is a JWT carrying only
> `sub`/`iat`/`typ=api_key`/optional `exp`+`name` and a header `kid` — **no scopes**. Scopes live
> server-side in the stored entry keyed by `kid`, read fresh on every request. That's why D13's
> strip and D15's gate take effect against a token the user still physically holds.
