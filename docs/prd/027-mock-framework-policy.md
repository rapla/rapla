# PRD 027 — Mock-framework policy for rapla tests

**Status:** decision — adopted 2026-05-11.
**Author:** Christopher Kohlhaas (with AI assistance)
**Created:** 2026-05-11

## Goal

Decide whether `Mockito` (and mock frameworks generally) belong in the
rapla test suite, and write the policy down before it drifts.

PRD 024 will land at least three new server-side endpoints
(`/edit/check-conflicts`, `/edit/validate-recurrence`, `/calendar/view`).
PRD 025 (draft) will add a presenter test harness. Both touch the
question: *do we reach for `Mockito.mock(...)` here?* The current
codebase has Mockito on the classpath (transitively, via
`spring-boot-starter-test`) and uses it in exactly **one** test.
Without a rule, the count goes up to "everywhere" the next time
someone is in a hurry. That is the wrong direction.

## Decision

**Default: no mocks of internal rapla types.** Use the real thing —
`FacadeTestSupport` (PRD 017) for tier 2, real Spring context + MockMvc
for tier 3. The reactor's existing harnesses make the cost of "real"
small enough that the usual mocking trade-off (speed vs fidelity)
flips: mocks do not buy speed and they do cost fidelity.

**Allowed exceptions, narrow:**
1. Servlet-API types you don't own and don't want to bring up
   (`HttpServletRequest`/`Response`/`ServletContext`). Mockito here is
   pragmatic — there is no real container in scope.
2. External integrations behind a single rapla-owned interface
   (`MailInterface`, `JndiLookupService`, EWS client, iCal HTTP
   fetcher). Prefer a **hand-rolled** test double (`MockMailer`-style,
   ~50 lines, records call args). Reach for `Mockito.mock(...)` only
   when the interface has > ~6 methods and the hand-roll is
   disproportionate.

**Not allowed:**
1. `Mockito.mock(RaplaFacade.class)`, `mock(RaplaOperator.class)`,
   `mock(LocalCache.class)`, `mock(PermissionController.class)`,
   `mock(ConflictFinder.class)`, `mock(ClassificationImpl.class)` —
   any rapla entity, facade, storage, or permission type. The real
   version is cheap (`FacadeTestSupport` boots in ~150 ms) and the
   mock hides the class of bug rapla has historically shipped.
2. `@MockBean` / `@SpyBean` in `@SpringBootTest`. Each one busts the
   Spring context cache (PRD 007 Phase 2.7 is the cache-sharing fix —
   we don't want to actively fight it). If a controller test needs to
   isolate a collaborator, use a `@TestConfiguration` static class
   that wires a hand-rolled stub bean (pattern: `StubPanelsConfig` in
   `PreferencesAdminControllerIntegrationTest`).
3. `@Mock` partial mocks of pure-Java models carved out by PRDs 023 /
   024 (`AllocationConflictModel`, `RepeatingRuleValidator`,
   `RaplaBuilder`, layout strategies). They have no I/O. Construct
   them directly with whatever input the test needs.

## Why mocks are net-negative here (the case the policy answers)

| Mock-framework upside | Status in this codebase |
|---|---|
| Faster than booting collaborators | Already fast — `FacadeTestSupport` first test ~550 ms (300 ms boot + 250 ms test), subsequent ~250 ms. A mock would save < 100 ms per test. Negligible vs the cost. |
| Isolates one class for true unit test | Most rapla classes don't have a meaningful isolated behaviour — they're coordinators (`FacadeImpl`, controllers) or value-mutators (entities). Behaviour emerges from interaction with `LocalCache` / `ConflictFinder` / `PermissionController`. A "unit" test with those mocked tests the wiring, not the behaviour. |
| Easier to simulate error paths | The places that need error simulation (DB driver throws, mail server unreachable, EWS 503) are already behind rapla-owned interfaces. A small hand-rolled stub returning the failure is clearer than `when(mock.foo()).thenThrow(...)` and doesn't drift when the interface changes. |
| Argument-capture / verification | Hand-rolled `Recording*` doubles capture call args directly (see `MockMailer.getMailBody()`, `RecordingPanel.lastSavedValue`). PRD 025's `RecordingView<P>` generalises this with a reflective proxy. No mock framework needed. |

**Concretely, the bugs that have shipped (and that integration-style
tests caught) would have been invisible to mock-based tests:**

| Bug | Where caught | What a mocked test would have shown |
|---|---|---|
| Jackson 3 `final`-field bugs (PRD 011 follow-up; 5 fields × different entity types) | `XmlRoundTripTest` (tier 2, real reader/writer) | Green — the mock returns whatever the test set up, so the wire format never gets exercised. |
| `LocalAbstractCachableOperator.storeAndRemoveAsync` empty stub (silent no-op on file backend) | `ConflictPerformanceTest` (tier 2 perf, real facade) | Green — `mock(Operator.class).storeAndRemove(...)` returns a happy `Promise`. The stub bug is in the **real** implementation; mocks bypass it. |
| MONTHLY = Nth-weekday-of-month semantic | `AppointmentOverlapHardeningTest` (tier 1, real `AppointmentImpl`) | A mock of `Appointment.overlaps(...)` returns whatever the test stubs — the actual `processBlocks` math is never run. |
| Permission leak via server endpoint (AGENTS.md §12) | Tier-3 MockMvc with real `PermissionController` | `mock(PermissionController.class).canRead(...)` returns true by default — the leak is invisible. |
| `FacadeImpl` constructor signature drift (PRD 017 risk #1) | The four `@SpringBootTest` acceptance tests | A mocked `FacadeImpl` doesn't have a constructor; the drift never surfaces in tests at all. |

The pattern: **rapla bugs cluster at the boundaries between layers
(wire format, cache invalidation, permission gate, repeating-rule
math).** Mocks erase those boundaries by replacing them with
test-author assumptions. The historical hit rate of "test-author
assumption matches production behaviour" in this codebase is poor
enough that the policy should default against it.

## Why mocks at the Servlet boundary *are* fine

`RaplaJNLPPageGeneratorTest` (rapla-server, 4 tests, Mockito-based) is
the one place mocks earn their keep:

- `HttpServletRequest`/`Response`/`ServletContext` are container
  types we don't own and that have ~20 methods each. Hand-rolling a
  stub is ~200 lines per test.
- The generator's behaviour we're testing (JNLP XML shape, double-
  slash defect, `main="true"` on the right jar) doesn't depend on
  servlet semantics — it just needs `getServerName()` / `getContextPath()`
  / `getRealPath()` to return canned values.
- The mocked `RaplaFacade` returns one stubbed `Preferences` that
  echoes its default — there is no facade behaviour under test here,
  just "feed the generator the right inputs".

This is the **right** use of Mockito: mocking *at the system boundary*,
where the real thing is disproportionate and the test is not
exercising the boundary type's behaviour. Policy keeps this test as-is
and accepts the pattern at the same boundary.

## Scope

| Path | Change |
|---|---|
| `AGENTS.md` | Add **§13 — Mock-framework policy**: short rule (default no, exceptions are servlet API + external integrations), pointer here. |
| `docs/prd/027-mock-framework-policy.md` | This file. |
| `rapla-bom/pom.xml` | **No change** — Mockito stays on the classpath via `spring-boot-starter-test` (don't introduce a brittle exclusion). The policy is enforced socially, not by classpath. |
| `rapla-server/src/test/java/org/rapla/server/servletpages/RaplaJNLPPageGeneratorTest.java` | **No change** — grandfathered as the canonical "mocking at the servlet boundary" example. |
| (future) `docs/architecture/testing.md` | If PRD 022's architecture-doc tree adopts a testing page, fold this policy there too. Not in scope for this PRD. |

Out of scope:
- Migrating existing tests. Nothing currently breaks the policy except the one grandfathered case.
- Banning Mockito at the build level. The transitive dep stays; this is a code-review rule, not a Maven enforcer rule.
- The PRD 025 `RecordingView<P>` proxy. That's PRD 025's call; this PRD only notes it's consistent with the policy.

## Plan

1. **Land this PRD** (status `decision — adopted`) so future PRDs can
   cite it.
2. **Add AGENTS.md §13** — ~15 lines summarising the rule + linking
   back here. Reviewers cite §13 when a PR introduces a disallowed
   mock.
3. **Audit at PRD-024 review time.** The three new endpoints land
   with MockMvc + real-facade tier-3 tests per AGENTS.md §10. If a
   reviewer notices `mock(RaplaFacade.class)` slipped in, point at
   §13.
4. **Re-evaluate after 6 months** (≈ 2026-11). If a recurring need
   shows up that the policy disallows (e.g. simulating a flaky JDBC
   driver becomes routine), revise the "allowed exceptions" list
   rather than abandoning the policy.

## Tests

This PRD does not add tests. It constrains how future tests are
written. The verification is review-time:

| Phase | Verification |
|---|---|
| 1 | This PRD merged + AGENTS.md §13 added. |
| 2 (ongoing) | PRD 024 / 025 / 026 tests land without `Mockito.mock(rapla.*)` calls. Grep audit at each PRD's close-out: `grep -rE "mock\(.*(Facade\|Operator\|Cache\|Permission\|Conflict)\.class\)" rapla-*/src/test`. Expected: zero matches outside the one grandfathered file. |

## Risks

1. **Policy is socially enforced, not mechanically.** A reviewer could
   miss a `mock(...)` in a large PR. *Mitigation:* the grep audit
   above is cheap (~50 ms across the reactor); add it as a `make
   audit-mocks` target if violations recur. Don't pre-emptively
   automate.
2. **Test-author frustration.** Someone hitting an awkward facade
   setup may feel mocking would be faster. *Mitigation:* the answer
   is usually "extend `FacadeTestSupport` with the helper you wanted"
   (`waitFor(Promise)` was promoted this way in PRD 017 Phase 4
   follow-up). Document this loop in §13.
3. **Servlet exception drift.** The "allowed at the servlet boundary"
   carve-out could expand. *Mitigation:* the grandfathered file is
   named; new servlet-API mocks should match its shape (mock the
   container type, not rapla types alongside it). If a second test
   needs the same setup, extract a hand-rolled
   `StubServletRequestBuilder` rather than copy-paste mock chains.
4. **Mock framework upgrades.** Mockito's transitive version bumps
   with Spring Boot. Since we use it in one test, breakage is local.
   If a future Mockito breaks `RaplaJNLPPageGeneratorTest`, rewriting
   it to hand-rolled stubs is a one-afternoon job, not a policy
   reversal.

## Considered & rejected

- **"Allow mocks freely; reviewers gate."** Rejected — the historical
  bug data (above) shows mock-based tests would have shipped real
  defects. Default-allow inverts the load on reviewers and doesn't
  match where rapla bugs cluster.
- **"Ban Mockito at the build level (exclusion in `rapla-bom`)."**
  Rejected — it would break `RaplaJNLPPageGeneratorTest` for no
  policy gain, fights `spring-boot-starter-test`, and a future
  legitimate boundary case (next Servlet-API style test) would have
  to re-add the dep with explanation. Social policy is cheaper.
- **"Adopt a stricter framework like ArchUnit to enforce."** Rejected
  for now — ArchUnit rules add a tier-1 test that lints test source,
  which is the kind of meta-testing PRD 016 already flagged as
  expensive maintenance. Revisit if violations recur.
- **"Allow `@MockBean` for `@SpringBootTest` collaborators that are
  expensive to wire."** Rejected — PRD 007 Phase 2.7 is the
  context-cache fix; `@MockBean` actively defeats it. The right
  answer for expensive collaborators is the `@TestConfiguration` +
  hand-rolled stub bean pattern from `PreferencesAdminControllerIntegrationTest`.

## Open Questions

1. **Should the policy also cover `@WebMvcTest`?** `@WebMvcTest` is a
   sliced Spring context that does mock collaborators by default.
   We currently use `@SpringBootTest` + `@AutoConfigureMockMvc` (full
   context, real collaborators), so the question is moot today.
   Revisit if we adopt `@WebMvcTest` for any future controller —
   likely allowed for controllers whose dependencies are genuinely
   peripheral, with the stub-bean pattern preferred over default
   `@MockBean`.
2. **Where does the policy live long-term?** AGENTS.md §13 is the
   primary home; this PRD is the rationale. If `docs/architecture/`
   grows a testing page under PRD 022, mirror §13 there with a link
   back to this PRD.
3. **Does this constrain PRD 026 (Angular)?** Angular tests use a
   separate ecosystem (Jest / Vitest, plus their own mocking story).
   This policy is Java-side only. Angular-side mocking is the next
   PRD's call; flag here so it's not forgotten.

## Cross-references

| PRD | Relationship |
|---|---|
| **017** test coverage strategy | Source of the pyramid (tiers 1–4) and `FacadeTestSupport`. This PRD says: stay on those tiers, don't graduate to mocks to "go faster". |
| **007** build & test performance | Phase 2.7 (context-cache sharing) is what `@MockBean` defeats. Reinforces "no `@MockBean` in `@SpringBootTest`". |
| **011** Spring Boot 4 / Jackson 3 | Surfaced the `final`-field bug class. Cited as evidence integration-style tests catch what mocks miss. |
| **024** server-side edit services | Will write new tier-3 tests; this policy is the constraint they're written under. |
| **025** headless client test harness | Independently chose "no mocking framework" for the same reasons. This PRD makes that choice the project-wide default rather than a per-PRD note. |
| **022** architecture documentation | Long-term home for a "testing" page that mirrors AGENTS.md §13. |
| **AGENTS.md §10** (testing conventions) + new §13 (this rule) | Primary day-to-day reference for contributors. |
