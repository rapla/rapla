---
name: data-leak-prevention
description: Use whenever you touch a rapla REST endpoint — controllers (@RestController / @RequestMapping / @Get/Post/Put/DeleteMapping), DTO shapes, response builders, anything that turns rapla entities into JSON for the wire. Carries the implementation patterns and tier-3 MockMvc leak-test recipe for the permission-leak invariants in AGENTS.md §12 (filter at every output boundary, existence is information, per-entity not per-collection, server-derived inherits strictest permission). **Critical security skill — load on any controller change, no matter how small.** The Swing client today only holds allocatables / reservations / classifications the current user can read; new server endpoints must preserve that invariant.
---

# Permission-leak prevention for rapla REST endpoints

AGENTS.md §12 spells out *what* the rules are; this skill carries *how* to implement them and *how* to test that you did.

## Mental model

Behave as if the JSON response were a CSV dump emailed to the user. If anything in the body — id, name, count, attribute, column header, latency-distinguishable, error text — is something the same user couldn't have got via the Swing client today, the endpoint is broken.

The Swing client has always run the permission check at the entity boundary: `RaplaFacade.getAllAllocatables()` returns only what the current user can read; reservations get the same filter. Every new REST endpoint must replicate that, regardless of how the entities arrive at the controller.

## Implementation patterns

### Pattern 1 — filter the output collection by `PermissionController`

The base pattern at any boundary that returns entities:

```java
@GetMapping("/api/storage/allocatables")
public List<AllocatableDto> list(HttpServletRequest request) {
    User user = session.checkAndGetUser(request);
    PermissionController pc = facade.getPermissionController();

    List<Allocatable> result = facade.getAllocatables();
    result.removeIf(a -> !pc.canRead(a, user));   // ← the gate

    return result.stream().map(AllocatableDto::from).toList();
}
```

Apply the filter at **every** boundary that returns entities. Don't trust that an upstream `facade.getX()` did it — facade returns can broaden over time (cache hits, internal callers); your controller is the contract.

### Pattern 2 — id-list endpoints: silently drop, don't distinguish

If the client passes a list of ids (`?allocatables=a1,a7,a99`), the response must not differentiate between "id doesn't exist" and "id exists but you're not allowed to see it." Drop both silently.

```java
@PostMapping("/api/storage/queryAppointments")
public QueryAppointmentsResponse query(@RequestBody QueryAppointments req, HttpServletRequest request) {
    User user = session.checkAndGetUser(request);
    PermissionController pc = facade.getPermissionController();

    // Resolve ids → entities, silently drop non-existent AND non-readable.
    List<Allocatable> resources = req.resources().stream()
        .map(facade::resolveAllocatable)        // returns null for unknown id
        .filter(Objects::nonNull)
        .filter(a -> pc.canRead(a, user))       // drops non-readable
        .toList();

    // … query against the filtered set …
}
```

**Why:** if the response leaked "id `a7` was rejected but `a1` was processed," a malicious user could enumerate hidden allocatables by binary-searching ids. The Swing client doesn't expose this distinction; the REST endpoint shouldn't either.

### Pattern 3 — re-check per-entity, not per-collection

A reservation the user can read may reference an allocatable the user **can't** read (cross-resource bookings). The reservation passes `pc.canRead(reservation, user)`, but its contained allocatables don't.

Before exposing names/ids of contained entities in the response shape:

```java
ReservationDto buildDto(Reservation r, User user, PermissionController pc) {
    ReservationDto dto = new ReservationDto();
    dto.id = r.getId();
    dto.title = r.getName(...);

    // Per-allocatable re-check
    dto.allocatables = r.getAllocatables().stream()
        .filter(a -> pc.canRead(a, user))
        .map(a -> a.getId())
        .toList();
    return dto;
}
```

If you skip this and a reservation lists 5 allocatables but the user can only read 2, the user just learned 3 hidden allocatable ids exist + are bookable + are scheduled for that time slot.

### Pattern 4 — server-derived data inherits the strictest permission

Computed/aggregated responses (a `RenderedBlock` that mixes reservation data with allocatable colours; a calendar view with row labels) are only safe to return when the user can read **every** input entity. If any input is private, drop the whole block — don't return a partially-redacted version that reveals the structure.

```java
List<RenderedBlock> blocks = ...;
blocks.removeIf(block -> {
    return !pc.canRead(block.getReservation(), user)
        || block.getAllocatables().stream().anyMatch(a -> !pc.canRead(a, user));
});
```

Half-redacted server-derived data is worse than no data — the shape itself is the leak.

## Reference implementation

`CalendarViewController.resolveResourceFilter` (in `rapla-server`) is the canonical example. The class-level comment spells out the enumeration risk and shows the silent-drop pattern in production code. Read it before designing a new id-list endpoint.

## The leak test — mandatory tier-3 MockMvc pattern

Every new endpoint that takes ids or filters gets a regression test. The test fixture is a non-admin user who has read access to *some* but not *all* of the test data, and asserts the response is identical to "the resource doesn't exist" for the hidden ids.

Canonical shape:

```java
@SpringBootTest
@AutoConfigureMockMvc
class FooControllerLeakTest {

    @Autowired MockMvc mvc;

    @Test
    void nonAdminCantProbeHiddenIds() throws Exception {
        // Set up: user "limited" can read allocatable a1 but NOT a99.
        // (a99 exists in the test fixture but limited has no permission)
        String token = loginAs("limited", "...");

        String allRequest    = "{\"resources\":[\"a1\",\"a99\"]}";
        String onlyRequest   = "{\"resources\":[\"a1\"]}";
        String hiddenRequest = "{\"resources\":[\"a99\"]}";
        String fakeRequest   = "{\"resources\":[\"a-does-not-exist\"]}";

        // The hidden id MUST produce the same response shape as a non-existent id.
        // Cannot differ in: status code, body, header set, response time bucket.
        var hidden = mvc.perform(post("/api/foo").content(hiddenRequest)
            .header("Authorization", "Bearer " + token)).andReturn();
        var fake = mvc.perform(post("/api/foo").content(fakeRequest)
            .header("Authorization", "Bearer " + token)).andReturn();

        assertEquals(hidden.getResponse().getStatus(), fake.getResponse().getStatus());
        assertEquals(hidden.getResponse().getContentAsString(),
                     fake.getResponse().getContentAsString());

        // Mixed request: result is identical to the "only readable" subset.
        var mixed = mvc.perform(post("/api/foo").content(allRequest)
            .header("Authorization", "Bearer " + token)).andReturn();
        var only = mvc.perform(post("/api/foo").content(onlyRequest)
            .header("Authorization", "Bearer " + token)).andReturn();

        assertEquals(mixed.getResponse().getContentAsString(),
                     only.getResponse().getContentAsString());
    }
}
```

What this test catches in practice:

- "I forgot to filter at the output boundary" — `a99`'s id appears in mixed response, fails `assertEquals(mixed, only)`.
- "I return 404 for unknown ids but 200-with-empty for hidden ids" — fails `assertEquals(hidden.status, fake.status)`.
- "My error message says 'allocatable a99 was filtered'" — fails the body-equality check.
- "I add a column per requested id, including hidden ones" — column count differs, body inequality.

## What leaks beyond the body

Permission leaks aren't only about JSON content. Audit all of:

| Channel | What can leak |
|---|---|
| **HTTP status** | `404 Not Found` for unknown id vs `403 Forbidden` for hidden id — different = leak |
| **Headers** | `Content-Length`, `X-Total-Count`, ETag — anything derived from full vs filtered set |
| **Latency** | If hidden ids take longer to "filter out" than non-existent ids return → timing oracle |
| **Error text** | "Allocatable a99 not visible" vs "Allocatable a99 not found" — both bad; both must say the same thing |
| **Shape implications** | Column count in tabular response; presence of nested objects; array length mod something |
| **Side effects** | Audit log entries, conflict-detection cache invalidation, etc. — even if user can't read, server-side state may differ |

For an id-list endpoint, the safe response is structurally identical regardless of why an id was dropped.

## Quick checklist before merging a new REST endpoint

- [ ] Every entity-returning path has `pc.canRead(entity, user)` filter at the output boundary
- [ ] Id-list endpoints silently drop both unknown AND non-readable ids
- [ ] Nested entities (allocatables inside reservations, etc.) are re-checked per-entity
- [ ] Server-derived/aggregated data drops the whole row if any input is private
- [ ] Tier-3 MockMvc leak test exists (`*LeakTest` or in the endpoint's existing integration test class)
- [ ] Manual smoke: `curl` the endpoint as a non-admin user with a mixed visible/hidden id list; confirm output matches the visible-only subset

## Cross-references

- AGENTS.md §12 — the always-loaded nevers (this skill expands them).
- AGENTS.md §13 + `testing-conventions` skill — tier-3 MockMvc test framework, mock policy (use real `RaplaFacade`, not `mock(PermissionController.class)`).
- AGENTS.md §10 — pyramid; leak tests live at tier 3.
- `CalendarViewController.resolveResourceFilter` (rapla-server) — reference implementation.
- `PreferencesAdminControllerIntegrationTest` — example tier-3 MockMvc test class with non-admin user fixtures (look at the `loginAs` helper).
