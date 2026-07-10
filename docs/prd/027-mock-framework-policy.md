# PRD 027 — Mock-framework policy for rapla tests

**Status:** decision — adopted 2026-05-11.
**Author:** Christopher Kohlhaas (with AI assistance)
**Created:** 2026-05-11

## Goal

Decide whether `Mockito` (and mock frameworks generally) belong in the rapla test suite, and write the policy down before it drifts. [PRD 024](024-server-side-edit-services.md) lands three new server endpoints; [PRD 025](025-headless-client-test-harness.md) adds a presenter test harness. Both touch the question. Mockito is on the classpath (transitively via `spring-boot-starter-test`) and used in exactly one test today; without a rule, that count grows to "everywhere" the next time someone is in a hurry.

## Decision

**Default: no mocks of internal rapla types.** Use the real thing — `FacadeTestSupport` ([PRD 017](017-test-coverage-strategy.md)) for tier 2, real Spring context + MockMvc for tier 3. The reactor's existing harnesses make "real" cheap enough that the usual speed/fidelity trade-off flips: mocks don't buy speed and they do cost fidelity.

**Allowed exceptions, narrow:**
1. Servlet-API types you don't own and don't want to bring up (`HttpServletRequest`/`Response`/`ServletContext`). Mockito is pragmatic — no real container in scope.
2. External integrations behind a single rapla-owned interface (`MailInterface`, `JndiLookupService`, EWS client, iCal fetcher). Prefer a hand-rolled double (`MockMailer`-style, ~50 lines, records args). Reach for `Mockito.mock(...)` only when the interface has > ~6 methods and the hand-roll is disproportionate.

**Not allowed:**
1. `Mockito.mock(RaplaFacade.class)`, `mock(RaplaOperator.class)`, `mock(LocalCache.class)`, `mock(PermissionController.class)`, `mock(ConflictFinder.class)`, `mock(ClassificationImpl.class)` — any rapla entity, facade, storage, or permission type. Real is cheap (`FacadeTestSupport` boots in ~150 ms); the mock hides the class of bug rapla has shipped.
2. `@MockBean` / `@SpyBean` in `@SpringBootTest`. Each busts the Spring context cache (fights [PRD 007](007-build-and-test-performance.md) Phase 2.7). For controller isolation, use a `@TestConfiguration` static class with hand-rolled stub beans (pattern: `StubPanelsConfig` in `PreferencesAdminControllerIntegrationTest`).
3. `@Mock` partial mocks of pure-Java models carved out by PRDs [023](023-presenter-view-extraction.md) / [024](024-server-side-edit-services.md) (`AllocationConflictModel`, `RepeatingRuleValidator`, `RaplaBuilder`, layout strategies). No I/O — construct directly.

## Why mocks are net-negative here

| Mock-framework upside | Status in this codebase |
|---|---|
| Faster than booting collaborators | Already fast — `FacadeTestSupport` first test ~550 ms, subsequent ~250 ms. A mock saves < 100 ms per test. Negligible. |
| Isolates one class for true unit test | Most rapla classes are coordinators (`FacadeImpl`, controllers) or value-mutators (entities). Behaviour emerges from interaction with `LocalCache` / `ConflictFinder` / `PermissionController`. A "unit" test with those mocked tests the wiring, not the behaviour. |
| Easier to simulate error paths | Sites needing error simulation (DB driver throws, mail unreachable, EWS 503) are already behind rapla-owned interfaces. A hand-rolled stub returning the failure is clearer than `when(mock.foo()).thenThrow(...)` and doesn't drift. |
| Argument-capture / verification | Hand-rolled `Recording*` doubles capture args directly (see `MockMailer.getMailBody()`, `RecordingPanel.lastSavedValue`). [PRD 025](025-headless-client-test-harness.md)'s `RecordingView<P>` generalises this with a reflective proxy. |

**Bugs that have shipped — and that integration-style tests caught — would have been invisible to mock-based tests:**

| Bug | Where caught | What a mocked test would have shown |
|---|---|---|
| Jackson 3 `final`-field bugs ([PRD 011](done/011-spring-boot-4-jackson-3.md) follow-up; 5 fields × different entity types) | `XmlRoundTripTest` (tier 2, real reader/writer) | Green — mock returns whatever test set up; wire format never exercised. |
| `LocalAbstractCachableOperator.storeAndRemoveAsync` empty stub (silent no-op on file backend) | `ConflictPerformanceTest` (tier 2 perf, real facade) | Green — `mock(Operator.class).storeAndRemove(...)` returns happy `Promise`. Stub bug is in the real impl; mocks bypass it. |
| MONTHLY = Nth-weekday-of-month semantic | `AppointmentOverlapHardeningTest` (tier 1, real `AppointmentImpl`) | A mock of `Appointment.overlaps(...)` returns whatever test stubs — real `processBlocks` math never runs. |
| Permission leak via server endpoint (AGENTS.md §12) | Tier-3 MockMvc with real `PermissionController` | `mock(PermissionController.class).canRead(...)` returns true by default — leak invisible. |
| `FacadeImpl` constructor signature drift ([PRD 017](017-test-coverage-strategy.md) risk #1) | The four `@SpringBootTest` acceptance tests | Mocked `FacadeImpl` doesn't have a constructor; drift never surfaces. |

Rapla bugs cluster at the boundaries between layers (wire format, cache invalidation, permission gate, repeating-rule math). Mocks erase those boundaries by replacing them with test-author assumptions; historical hit rate of "assumption matches production" is poor enough that the policy defaults against it.

## Why mocks at the Servlet boundary *are* fine

`RaplaJNLPPageGeneratorTest` (rapla-server, 4 tests, Mockito-based) is the one place mocks earn their keep: `HttpServletRequest`/`Response`/`ServletContext` are container types we don't own with ~20 methods each (hand-roll is ~200 lines per test); the generator's behaviour under test (JNLP XML shape, double-slash defect, `main="true"` placement) doesn't depend on servlet semantics — it just needs `getServerName()` / `getContextPath()` / `getRealPath()` returning canned values; the mocked `RaplaFacade` returns one stubbed `Preferences` echoing its default. This is the right use: mocking at the system boundary, where the real thing is disproportionate and the test isn't exercising the boundary type's behaviour. Policy grandfathers this file and accepts the pattern at the same boundary.

## Scope

| Path | Change |
|---|---|
| `AGENTS.md` | Add **§13 — Mock-framework policy**: short rule (default no, exceptions are servlet API + external integrations), pointer here. |
| `docs/prd/027-mock-framework-policy.md` | This file. |
| `rapla-bom/pom.xml` | **No change** — Mockito stays on the classpath via `spring-boot-starter-test`. Policy is enforced socially. |
| `rapla-server/.../RaplaJNLPPageGeneratorTest.java` | **No change** — grandfathered as the canonical example. |
| (future) `docs/architecture/testing.md` | If [PRD 022](022-architecture-documentation.md)'s tree adopts a testing page, fold this policy there too. Not in scope. |

Out of scope: migrating existing tests (nothing currently breaks the policy except the grandfathered case); banning Mockito at the build level (transitive dep stays — this is a code-review rule); [PRD 025](025-headless-client-test-harness.md)'s `RecordingView<P>` proxy (that's [PRD 025](025-headless-client-test-harness.md)'s call).

## Plan

1. Land this PRD so future PRDs can cite it.
2. Add AGENTS.md §13 — ~15 lines summarising + link back. Reviewers cite §13 when a PR introduces a disallowed mock.
3. Audit at PRD-024 review time. New endpoints land with MockMvc + real-facade tier-3 tests per AGENTS.md §10.
4. Re-evaluate after 6 months (≈ 2026-11). If a recurring need shows up that the policy disallows, revise the exceptions list — don't abandon the policy.

## Tests

This PRD adds no tests; it constrains how future tests are written. Verification is review-time:

| Phase | Verification |
|---|---|
| 1 | This PRD merged + AGENTS.md §13 added. |
| 2 (ongoing) | PRD [024](024-server-side-edit-services.md) / [025](025-headless-client-test-harness.md) / [026](026-angular-frontend.md) tests land without `Mockito.mock(rapla.*)`. Grep audit at each close-out: `grep -rE "mock\(.*(Facade\|Operator\|Cache\|Permission\|Conflict)\.class\)" rapla-*/src/test`. Expected: zero matches outside the grandfathered file. |

## Risks

1. **Socially enforced, not mechanical.** A reviewer could miss a `mock(...)` in a large PR. *Mitigation:* the grep audit is cheap (~50 ms); add as `make audit-mocks` target if violations recur. Don't pre-emptively automate.
2. **Test-author frustration.** Someone hitting an awkward facade setup may feel mocking would be faster. *Mitigation:* the answer is usually "extend `FacadeTestSupport` with the helper you wanted" (`waitFor(Promise)` was promoted this way in [PRD 017](017-test-coverage-strategy.md) Phase 4 follow-up). Document this loop in §13.
3. **Servlet exception drift.** The carve-out could expand. *Mitigation:* new servlet-API mocks should match the grandfathered shape (mock the container type, not rapla types alongside it). If a second test needs the same setup, extract a hand-rolled `StubServletRequestBuilder`.
4. **Mock framework upgrades.** Since we use it in one test, breakage is local. Rewriting `RaplaJNLPPageGeneratorTest` to hand-rolled stubs is a one-afternoon job, not a policy reversal.

## Considered & rejected

- **"Allow mocks freely; reviewers gate."** Historical bug data above shows mock-based tests would have shipped real defects. Default-allow inverts review load and doesn't match where rapla bugs cluster.
- **"Ban Mockito at the build level."** Would break the grandfathered test for no policy gain; fights `spring-boot-starter-test`; future legitimate boundary cases would have to re-add it. Social policy is cheaper.
- **"Adopt ArchUnit to enforce."** Adds a tier-1 test that lints test source — the kind of meta-testing [PRD 016](done/016-pre-checkin-deletion-audit.md) flagged as expensive maintenance. Revisit if violations recur.
- **"Allow `@MockBean` for expensive `@SpringBootTest` collaborators."** Defeats [PRD 007](007-build-and-test-performance.md) Phase 2.7 context-cache. Right answer is `@TestConfiguration` + hand-rolled stub bean.

## Open Questions

1. **Cover `@WebMvcTest`?** `@WebMvcTest` mocks collaborators by default. We use `@SpringBootTest` + `@AutoConfigureMockMvc` (full context), so the question is moot today. Revisit if `@WebMvcTest` adopted — likely allowed with stub-bean pattern preferred over `@MockBean`.
2. **Long-term home.** AGENTS.md §13 is primary; this PRD is rationale. If `docs/architecture/` grows a testing page under [PRD 022](022-architecture-documentation.md), mirror §13 there.
3. **Constrain [PRD 026](026-angular-frontend.md) (Angular)?** Angular uses Jest/Vitest with its own mocking story. This policy is Java-side only. Angular-side mocking is the next PRD's call.

## Cross-references

| PRD | Relationship |
|---|---|
| **017** test coverage strategy | Source of tiers 1–4 and `FacadeTestSupport`. This PRD: stay on those tiers, don't graduate to mocks to "go faster". |
| **007** build & test performance | Phase 2.7 (context-cache sharing) is what `@MockBean` defeats. |
| **011** Spring Boot 4 / Jackson 3 | Surfaced the `final`-field bug class. Evidence that integration-style tests catch what mocks miss. |
| **024** server-side edit services | Writes new tier-3 tests; this policy is the constraint. |
| **025** headless client test harness | Independently chose "no mocking framework" for the same reasons. This PRD makes it project-wide default. |
| **022** architecture documentation | Long-term home for a testing page mirroring AGENTS.md §13. |
| **AGENTS.md §10** + new §13 | Primary day-to-day reference for contributors. |
