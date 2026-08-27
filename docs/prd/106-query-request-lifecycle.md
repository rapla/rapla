# PRD 106 — Query request lifecycle & overload protection

**Status:** in progress — 2026-08-12. Umbrella/collector PRD: the *client* slice is
implemented here-and-now ([PRD 078](078-spa-graphql-view-renderer.md) Phase 5); the two
*server* slices stay deferred design in [PRD 062](062-graphql-api-robustness.md).

**Related:**
[PRD 078 § Phase 5 — query lifecycle](078-spa-graphql-view-renderer.md#plan--phased) (SPA — the implemented slice) ·
[PRD 062 § 3. Rate limiting](062-graphql-api-robustness.md#3-rate-limiting) (server — per-user read concurrency) ·
[PRD 062 § 5. Request-level timeout + cancellation](062-graphql-api-robustness.md#5-request-level-timeout--cancellation) (server — engine cancellation) ·
[PRD 077](077-calendar-model-graphql.md) (owns the window/navigation semantics the client slice throttles) ·
[PRD 095](095-month-grid-render-mode.md) (month nav — a consumer of the same window signal)

## Abstract

Rapid date-navigation in the SPA (clicking ▶ ten times) fires ten `/api/graphql`
POSTs; none of the superseded ones is cancelled and all ten run to completion on the
server. This PRD collects the three independent mitigations — one on the client, two on
the server — decides which are built now, and records the scoping decision that binds all
three: **they apply to queries only, never to mutations.**

Measurable end state for the shipped slice: ten rapid ▶ clicks produce **two** HTTP
requests (the leading one, aborted, plus one trailing request on the final window),
instead of ten completed ones.

## The problem, precisely

`ViewHostComponent` has one query effect (`view-host.component.ts:962`) that calls `run()`
on any change of view / window / filter chips. `run()` subscribes directly
(`view-host.component.ts:1022`) and guards against out-of-order responses with a monotonic
`reqToken` (`:900-902`, checked at `:1024` and `:1055`).

That guard discards stale **responses**. It does not cancel stale **requests**: the POST
stays in flight, the server executes the full query, and the response travels back over the
wire only to hit `if (token !== this.reqToken) return`. There is no debounce or throttle
anywhere on the window path — `view-control-strip.component.ts:334` writes
`viewState.setWindow(...)` synchronously on every click.

Note on the trigger shape: holding the mouse button down produces exactly **one** click —
the buttons are plain `<button (click)="next()">` (`view-control-strip.component.ts:124-134`)
and there is no keyboard window-navigation (the only `keydown` handler,
`view-host.component.ts:189`, is PRD 099 table-row selection). The bursts come from **rapid
clicking**, plus the edge case of a held Enter key on a focused button (key-repeat fires
repeated clicks).

## The three mitigations

| # | Where | What | Status |
|---|---|---|---|
| **A** | Client (078) | `switchMap` + leading/trailing throttle on the view-query trigger | **built** |
| **B** | Server (062 §5) | graphql-java execution cancellation on client disconnect | deferred design |
| **C** | Server (062 §3) | per-user concurrency limit on read queries | deferred design |

**A alone solves the reported problem.** B and C exist for clients that cannot be fixed by
shipping SPA code: API-key scripts, a runaway poll, the Swing client, a second tab, an older
SPA version still in someone's browser cache. They are robustness, not the fix.

### A — client-side (built, see 078 Phase 5)

`switchMap` unsubscribes the previous inner subscription, and Angular's `HttpXhrBackend`
calls `xhr.abort()` on unsubscribe — so the request is genuinely aborted, not merely
ignored. That makes `reqToken` and both `token !== this.reqToken` checks dead code; they are
removed in the same change (the net diff is negative).

`throttleTime(250, {leading: true, trailing: true})` is what handles the burst: the first
click goes out immediately (a single click must not feel laggy), everything inside the
window is swallowed, and one trailing request fires on the final window position. The pair
is complementary — `switchMap` alone would still *send* every request and let the server
start work before the abort arrives; the throttle prevents the send.

### B — server-side execution cancellation (deferred)

graphql-java **25.0** already has the primitives: `ExecutionInput.cancel()` /
`isCancelled()` and `EngineRunningState.throwIfCancelled()` → `AbortExecutionException`
(verified against `graphql-java-25.0.jar`). The cost is not the cancelling, it is the two
steps before it — see [062 § 5](062-graphql-api-robustness.md#5-request-level-timeout--cancellation).

### C — per-user read concurrency limit (deferred)

A `WebGraphQlInterceptor` (the seam `StoredViewInterceptor` already uses) holding a
`Semaphore` per authenticated principal, gating **read** operations only — see
[062 § 3](062-graphql-api-robustness.md#3-rate-limiting).

## Scope

### In scope
- The client-side query lifecycle for server-declared views (A).
- Recording the queries-only scoping decision (D1) and the server designs (B, C) in 062.

### Out of scope
- Any server implementation — B and C keep 062's "parking lot, no implementation" status.
- Result caching / prefetching of adjacent windows (see D4).
- Mutations, in every respect (D1).

## Plan

### Phase 1 — client query lifecycle *(PRD 078 Phase 5)*
- [x] Route the view-query effect through a `Subject` → `throttleTime(leading+trailing)` → `switchMap`.
- [x] Delete `reqToken` and both staleness checks.
- [x] Tier-5 test: a burst of window changes yields the leading + trailing request only, and the in-flight one is unsubscribed.

### Phase 2 — server designs recorded, not built
- [x] [062 § 3](062-graphql-api-robustness.md#3-rate-limiting) extended with the per-user read-concurrency design + defer trigger.
- [x] [062 § 5](062-graphql-api-robustness.md#5-request-level-timeout--cancellation) extended with the graphql-java 25 cancellation design, the async-disconnect risk, and the defer trigger.

## Tests

Tier 5 (Vitest, no TestBed) in `rapla-angular/src/app/views/` — drive the window signal
several times in a row against a stubbed `GraphqlService` and assert the call count and
that the superseded subscription was torn down. No Playwright: the behaviour is fully
observable at the service boundary.

## Decisions locked

**D1 — queries only; mutations are never cancelled, throttled, or rate-limited.**
A read aborted mid-flight costs only time; a write aborted mid-flight can leave partially
applied changes. And a concurrency limit spanning both would queue a save in the event
sheet behind a slow view query. Client-side this is free: `GraphqlService.mutate()`
(`graphql.service.ts:124`) is already a separate path from `executeView()` (`:142`), and
the `switchMap` lives in `ViewHostComponent`, not in the service. Server-side, both B and C
gate on the read path.

**D2 — `switchMap` stays at the call site; no central cancellation registry.**
`switchMap` operates on one stream and can only supersede within that stream;
`GraphqlService.query()` returns a fresh cold Observable per call and has no notion of
"B replaces A". Centralising would mean an explicit cancel-key plus a `Map<key, Subject>`
in the service — a global registry for what is, after the audit below, a single open call
site. The convention instead: *a repeatable user-driven trigger gets its own `Subject` +
`switchMap` at the call site*, exactly as `event-sheet.component.ts:233,256` already does
with `debounceTime(250)` + `switchMap`.

Audit of the ~20 read call sites (2026-08-12):
- *Needs it:* `view-host.component.ts:1022` (this PRD), `shell/omnibox.component.ts` (has it),
  `event/event-sheet.component.ts` (has it).
- *Does not:* the id-keyed one-shot loads — `event-data.service`, `allocatable-data.service`,
  `classification-schema.service`, `view-catalog.service`, `new-event-options.service`, the
  type lists. A second call there is a *different* request, not a replacement.
- *Covered from outside:* `occurrence-preview.service`, `availability-search.service` — no
  `switchMap` of their own, but every caller sits behind the event sheet's debounced stream.

**D3 — 250 ms leading+trailing throttle, not a debounce.**
A plain `debounceTime` would delay *every* single click, and the single click is the normal
case. Leading+trailing throttle fires immediately and still collapses a burst. 250 ms
matches the existing SPA precedent (`event-sheet.component.ts:233`) and is a calibration
knob, not a constant of nature.

**D4 — no result cache or adjacent-window prefetch.**
Would make paging back free, but costs invalidation against `MutationBus`. Revisit only if
A proves insufficient in practice.

## Open Questions

- **OQ1** — Does Spring GraphQL's MVC handler already run the request asynchronously
  (`AsyncListener`), or would B require converting the GraphQL endpoint to async first?
  This single question decides whether B is a day or a week. *Resolution:* pending —
  unblock before promoting 062 § 5.
