---
status: "accepted"
date: 2026-06-24
decision-makers: Christopher Kohlhaas
consulted: code archaeology of RaplaDefaultPermissionImpl + PermissionController (2026-06-24)
informed: future contributors, AI coding agents
---

# Permissions are grant-only; the effective level is the strongest matching row

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

- **Grant-only, strongest matching row wins** (with `USER > GROUP > WORLD` effect precedence)
- **Subtractive ACL** (explicit deny rows that override grants, NTFS/POSIX-style)
- **Order-dependent first-match** (first matching row in list order wins, firewall-style)

## Decision Outcome

Chosen option: **grant-only, strongest matching row wins**, with effect precedence
`USER (10000) > GROUP (5000) > WORLD (-1)`. There are **no deny rows**. The effective level is the
`accessLevel` of the highest-effect row that matches the user; access is granted iff that level
`includes()` the requested level. The one subtraction mechanism is the `DENIED` (0) level used on a
*more-specific* row to override an inherited *less-specific* grant — e.g. a `USER`-effect `DENIED`
row beats a `GROUP`-effect `ALLOCATE` row, giving "open to the group, closed for this member". You
cannot stack a separate "deny" on top of a grant; you change the existing row or add a
higher-precedence `DENIED`.

### Consequences

- Good, because resolution is monotonic and explainable: "your access is the best grant that
  applies to you", no rule-ordering surprises.
- Good, because group-cascade + `USER`-beats-`GROUP` precedence covers the "allow group, exclude
  member" case without a separate deny concept.
- Bad, because admins coming from NTFS/POSIX expect deny rows and must learn the `DENIED`-on-a-
  more-specific-row idiom instead. (Documented in `architecture/permissions.md` worked example.)
- Bad, because you cannot express "everyone in group X except during window W" as an *added*
  restriction; you must edit the grant row's time bounds.

### Confirmation

`PermissionControllerAccessQueryTest` pins precedence + cascade + union
(`groupPermissionGrantsAtLevelButNotAbove`, `parentGroupPermissionCascadesToChildGroup`,
`unionAcrossGroups`). **Gap:** no test currently pins the `USER`-`DENIED`-overrides-`GROUP`-grant
path — listed in `docs/spec/permissions.md` § Unverified surface; add a `PermissionMatrixTest`
case to raise this decision's confirmation from prose to ✅ PINNED.

## Pros and Cons of the Options

### Grant-only, strongest matching row wins (chosen)

- Good, because monotonic and order-independent — easy to reason about and to cache
  (`PermissionIndex`).
- Good, because `USER > GROUP > WORLD` gives a clean override path via `DENIED`.
- Bad, because no additive restrictions; some policies require editing the grant itself.

### Subtractive ACL (deny rows override grants)

- Good, because familiar to NTFS/POSIX admins; expresses "all except" directly.
- Bad, because resolution becomes non-monotonic and order/precedence-of-deny rules get subtle;
  harder to cache and to explain; a stray deny silently removes access.

### Order-dependent first-match (firewall-style)

- Good, because very expressive.
- Bad, because the ACL becomes position-sensitive — reordering rows changes outcomes; worst fit for
  a non-technical admin UI.

## Future possibilities

- A pinning test for the `USER`-`DENIED` override path (closes the confirmation gap above).
- If additive time-restrictions are ever needed, model them as a per-row constraint, not as a new
  deny-row concept (preserve monotonicity).

## More Information

- Resolution algorithm + ladder: [`docs/spec/permissions.md`](../spec/permissions.md).
- Worked example of `DENIED`-override idiom: [`docs/architecture/permissions.md`](../architecture/permissions.md).
