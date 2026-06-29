---
status: "accepted"
date: 2026-06-24
updated: 2026-06-28
decision-makers: Christopher Kohlhaas
consulted: code archaeology of RaplaDefaultPermissionImpl + PermissionController (2026-06-24); dhbw store blast-radius audit (2026-06-28)
informed: future contributors, AI coding agents
---

# Permissions are grant-only and purely additive; the effective level is the highest matching grant

> **Revised 2026-06-28 — from precedence to purely additive.** The original decision (2026-06-24)
> kept a `USER > GROUP > WORLD` precedence under which a more-specific row could override a broader
> grant *downward* (and `DENIED` capped to nothing). That downward override is itself a soft
> subtraction. We now go fully additive: **the effective level is the highest grant from any matching
> row — no precedence, no deny.** Rationale and the blast-radius evidence are below. The
> resolution-code change (`RaplaDefaultPermissionImpl` + `PermissionContainer.Util.getInterval`)
> **landed in PRD 090 (2026-06-28)**, with a marker-guarded one-shot migration that freezes a
> worklist of escalated allocatables for admin review.

## Context and Problem Statement

rapla's access control must resolve a user's effective access on an entity from a list of permission
rows that may match via the user directly, via any of the user's (transitively inherited) groups, or
via a wildcard. When several rows match — e.g. a permissive group grant and a restrictive
individual rule — what is the effective level? Is access *additive* (strongest grant wins) or
*subtractive* (any matching deny removes access, as in POSIX/NTFS ACLs)?

Derived from `RaplaDefaultPermissionImpl.hasAccess` (`:29-122`) and `PermissionContainer.Util`.
Contract: [[permissions]] (`docs/spec/permissions.md`).

## Decision Drivers

- Predictable, explainable resolution for non-technical admins building ACLs in the Swing UI.
- Group hierarchy: a grant on a parent category should cascade to descendants.
- Must still allow "open the group, but exclude/limit specific members".

## Considered Options

Two layers of choice. First, additive vs subtractive:

- **Grant-only / additive** (grants only ever add access; no deny)
- **Subtractive ACL** (explicit deny rows that override grants, NTFS/POSIX-style)
- **Order-dependent first-match** (first matching row in list order wins, firewall-style)

Then, *within* grant-only, how to combine the matching rows:

- **Pure additive — highest level across all matching rows wins**, no precedence — **chosen (2026-06-28)**
- **Precedence — strongest-*effect* row wins, `USER (10000) > GROUP (5000) > WORLD (-1)`** — the
  original 2026-06-24 choice; its downward override (a more-specific row capping a broader grant) is
  a soft subtraction, which is why it was revised out.

## Decision Outcome

Chosen option: **grant-only and purely additive — the effective level is the *highest* `accessLevel`
granted by *any* row that matches the user** (directly, via a group, or via the all-users `WORLD`
row). Access is granted iff that maximum `includes()` the requested level. There is **no precedence**
between user, group, and world rows, and there are **no deny rows**: a grant can only ever *add*
access, and a user can never end up with *less* than one of their groups grants. To limit a person
you change their group membership (or, future, a per-row cap) — never a subtractive row.

**There is one concept here — the *soft deny* — in two syntactic forms.** A `DENIED` (0) row caps a
matching principal *to nothing*; a higher-precedence *lower-level* row (e.g. a `USER` `READ` over a
`GROUP` `ALLOCATE`) caps them *to less*. Both subtract; both are non-obvious; both are exactly what
this decision abolishes. Additive resolution neutralizes both uniformly: `DENIED` is the floor so
`max(…, 0)` lowers nothing, and a lower-level row can never beat a higher one. So "no deny" and "no
user-below-group cap" are **the same rule**, not two.

`DENIED` is deprecated accordingly: the Swing UI no longer offers it on new rows, and the load-time
normalizer (`PermissionContainer.Util.normalizeRedundantDenies`) strips provably-redundant `DENIED`
rows as entities load. The level survives only to read legacy data and will be retired once stores
are clean.

**Why precedence was dropped (2026-06-24 → 2026-06-28).** The original `USER > GROUP > WORLD`
precedence let a more-specific row override a broader grant *downward* — a soft subtraction that
produces exactly the "why am I capped?" surprises a grant-only model is meant to avoid (it caused a
real one: the same group rendered under two locales looked like two different rules). A dhbw store
audit measured the cost of relying on it: of ~2,900 user rows, **only 2 entities** used a genuine
downward cap (1 still active, 0 on events) — every other apparent cap was a user row sitting below a
group the user is *not* a member of. With the blast radius that small, dropping precedence buys full
monotonicity for a near-empty migration.

### Migration to additive — one strategy for any rapla deployment

Because a `DENIED` row and a user-below-group cap are the *same* soft deny, there is **one**
migration, not one-per-form. It ships in rapla core (operator-level, store-backend-agnostic) and
runs for every deployment — vanilla or custom. Dropping precedence can only ever *raise* effective
access (a soft deny stops biting), so the only risk is **silent escalation, never lockout**; the
strategy is therefore **measure → flip → clean**:

1. **Pre-normalize the free ones (behaviour-preserving, ships first).** The load-time normalizer
   strips *redundant* `DENIED` rows — soft denies that have no effect even under today's precedence.
   Safe and automatic; no review needed.
2. **Audit the load-bearing soft denies (read-only, on by default during transition).** One detector
   finds *every* row that actually subtracts today — both forms in a single pass: a `DENIED` at
   higher precedence than a grant, **and** a user row below a group the user belongs to. With
   precedence still live it reports, per `(principal, entity)`, `current → additive` — the
   deployment's exact escalation set. This is the union; dhbw's number is 2 (1 active).
3. **Flip resolution to additive, gated on the audit.** The change refuses to flip a store whose
   audit is non-empty unless explicitly acknowledged (config flag), and logs the before/after diff.
   For each flagged row the admin chooses **accept the escalation** or **preserve the limit by group
   restructuring** (pure grant-only has no exclusion primitive, so there is no behaviour-preserving
   *automated* rewrite — but the set is small and explicit).
4. **Clean (optional, post-flip).** Once additive is live, *all* soft-deny rows are inert dead data;
   a normalizer pass strips them on load, letting `DENIED` be retired.

If a deployment's audit ever shows a *large* escalation set, the fallback is a hybrid (additive for
deny, retain user-row downward override) for that store — but that re-introduces the soft deny and
is explicitly not the default.

### Consequences

- Good, because resolution is fully monotonic and order-independent: effective access is the union
  (max) of every grant that applies — no precedence, no "specific overrides general" surprises.
- Good, because it matches the model most admins already meet in Keycloak / RBAC (additive role
  union) and is trivial to cache (`PermissionIndex`) and to explain.
- Good, because both `DENIED` rows and any user-below-group row become inert — *no* subtraction is
  expressible, which closes the "deny-light" loophole the precedence rule left open.
- Bad, because you cannot limit an individual below their group through permissions at all;
  exclusion must be modelled via group membership/structure (or a future per-row cap/mask).
- Bad, because existing downward caps escalate unless migrated — handled by the measure-then-flip
  process above (dhbw: 1 active row).

### Confirmation

> **Additive resolution has landed (PRD 090, 2026-06-28).** `RaplaDefaultPermissionImpl` and
> `PermissionContainer.Util.getInterval` now resolve additively (max over matching rows, no
> precedence). The precedence-era `GrantOverridesDenyAtEqualPrecedenceTest` was **replaced** by
> `AdditivePermissionResolutionTest` (rapla-server, tier-2), whose inverted cases pin max-wins:
> `userReadNoLongerCapsBelowGroupAllocate`, `userDeniedNoLongerOverridesGroupGrant`,
> `groupDeniedNoLongerOverridesAllUsersGrant`, `userDeniedIsOrderIndependentlyInert`,
> `groupBelowWorldGrantNoLongerCaps`. The redundant-`DENIED` normalizer keeps
> `RedundantDenyNormalizationTest`; the cascade/union cases
> (`parentGroupPermissionCascadesToChildGroup`, `unionAcrossGroups`) stay valid unchanged.

`PermissionControllerAccessQueryTest` pins cascade + union
(`groupPermissionGrantsAtLevelButNotAbove`, `parentGroupPermissionCascadesToChildGroup`,
`unionAcrossGroups`).

`AdditivePermissionResolutionTest` (rapla-server, tier-2) pins the **additive** matrix
(it replaced the precedence-era `GrantOverridesDenyAtEqualPrecedenceTest`):

- `DENIED` is the floor — a sibling/parent/sub-group `DENIED` never subtracts from any grant
  (`groupGrantBeatsGroupDenied_*`, `grantOnSubGroupSurvivesDenyOnParentGroup`,
  `denyOnSubGroupCannotCarveOutAParentGroupGrant`, order-independent);
- a `GROUP`-`DENIED` no longer overrides a `WORLD` (all-users) grant — the world grant wins
  (`groupDeniedNoLongerOverridesAllUsersGrant`);
- a `USER` row never caps below a `GROUP` row — the max wins (`userReadNoLongerCapsBelowGroupAllocate`,
  `userDeniedNoLongerOverridesGroupGrant`, `userDeniedIsOrderIndependentlyInert`), while a `USER`
  *grant* still elevates over a `GROUP`-`DENIED` (`userGrantStillElevatesOverGroupDenied`);
- a `GROUP` grant below a `WORLD` grant no longer caps its members (`groupBelowWorldGrantNoLongerCaps`).

Empirical corollary (dhbw store audit, 2026-06-27): no `DENIED` row there sits next to an
all-users grant and there are no user-targeted `DENIED` rows, so every legacy `DENIED` row is
redundant under this resolution.

## Pros and Cons of the Options

### Grant-only, purely additive — highest matching grant wins (chosen)

- Good, because fully monotonic and order-independent — the union (max) of every applicable grant;
  trivial to reason about and to cache (`PermissionIndex`).
- Good, because no precedence means no "specific overrides general" surprises and no soft-deny;
  matches Keycloak/RBAC additive role-union, which admins already understand.
- Bad, because individuals cannot be limited below their group via permissions — exclusion must be
  expressed through group membership/structure (or a future per-row cap/mask).

### Grant-only with `USER > GROUP > WORLD` precedence (original, superseded 2026-06-28)

- Good, because the precedence override let you cap a single member below their group without a
  separate deny concept.
- Bad, because that downward override *is* a soft subtraction — non-obvious ("why am I capped?"),
  and the one seam that kept the model from being purely additive. Blast radius of relying on it
  (dhbw) was 2 entities, so it was removed.

### Subtractive ACL (deny rows override grants)

- Good, because familiar to NTFS/POSIX admins; expresses "all except" directly.
- Bad, because resolution becomes non-monotonic and order/precedence-of-deny rules get subtle;
  harder to cache and to explain; a stray deny silently removes access.

### Order-dependent first-match (firewall-style)

- Good, because very expressive.
- Bad, because the ACL becomes position-sensitive — reordering rows changes outcomes; worst fit for
  a non-technical admin UI.

## Future possibilities

- ✅ Done (PRD 090, 2026-06-28): the additive resolution change in `RaplaDefaultPermissionImpl` +
  `PermissionContainer.Util.getInterval`, the one-shot migration freezing the escalation worklist,
  and the admin REST endpoint + SPA dialog that drains it.
- Retire the `DENIED` level outright once the normalizer + audit confirm no store still depends on it.
- If a per-individual or time-boxed *limit* is ever genuinely needed, model it as a per-row
  constraint / cap (a POSIX-ACL-style mask), **not** a deny row — preserving monotonicity.

## More Information

- Resolution algorithm + ladder: [`docs/spec/permissions.md`](../spec/permissions.md).
- Permission model + worked examples: [`docs/architecture/permissions.md`](../architecture/permissions.md).
- Prior art (Unix/POSIX ACL, NTFS, AWS IAM, Keycloak) and where rapla sits: same architecture page.
