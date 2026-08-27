# PRD 062 — GraphQL API Robustness Patterns

**Status:** draft (placeholder — not slated for implementation; renumbered from 058 → 062 on 2026-05-29 due to a number collision with [PRD 058 — GraphQL key-spec migration](058-graphql-key-spec-migration.md))

**Parent:** [PRD 035 (done) — GraphQL foundations](done/035-graphql-foundations.md). **Siblings:** [PRD 056 — events write API](056-graphql-events-write-api.md), [PRD 057 (done) — DynamicType mutations v1](done/057-graphql-dt-mutations-v1.md), [PRD 061 — DT mutations v2](061-graphql-dt-mutations-v2.md).

**Triggered by:** [PRD 056](056-graphql-events-write-api.md) OQ5 (idempotency on retry) — the basic
same-UUID-content-match semantic ships in 056 because it's a few lines
of comparison logic and matches industry practice. The heavier
infrastructure (lock acquisition for in-flight retries, TTL on
idempotency cache, rate limiting, etc.) is deferred here because rapla's
current usage doesn't justify Stripe-scale robustness.

## Goal

Catalogue the API-robustness patterns we know we'll eventually want, with
sketches and references, so when rapla outgrows its current scale and
duplicate-create vectors / runaway clients / distributed-coordination
issues become real, the design groundwork is here.

**Explicitly not in scope:** any implementation. This PRD is a parking
lot. Promote individual sections to their own PRDs (or fold into [PRD 056](056-graphql-events-write-api.md)
/ 057 / future events) when there's real consumer demand.

## Why this isn't in [PRD 056](056-graphql-events-write-api.md)

Rapla's `createReservation` traffic profile:
- A few dozen creates per day in typical deployments
- Hundreds at admin/import peaks (semester planning, bulk template instantiation)
- Single-pod-of-the-cluster handles each request
- Concurrent retries arrive seconds apart, not milliseconds

That doesn't earn the engineering / operational cost of:
- A Redis / persistent idempotency-key cache layer
- Distributed locks across pods for race-free retry handling
- TTL + GC + cache eviction tuning
- Rate-limiting infrastructure
- Backoff signaling protocols

When rapla either grows the traffic to need it OR moves toward
multi-tenant SaaS (where one tenant's runaway client can DoS another's
shared resources), this becomes load-bearing.

## Patterns to revisit

### 1. In-flight idempotency lock

**Problem:** Two retries of the same UUID arrive concurrently while the
original is still in-flight. Without a lock, both can pass the "does
storage contain this UUID?" check (storage doesn't yet — original hasn't
committed) and create duplicate entities.

**Industry pattern (Stripe, Brandur's idempotency-keys article):** Server
acquires an in-memory lock keyed on the UUID at request entry. Subsequent
retries either:
- **Wait** for the original to complete, then return its cached response
- **Fail-fast** with `IDEMPOTENCY_LOCK_HELD` / `409 in progress` —
  client retries with backoff

**Implementation sketch:**
```java
ConcurrentHashMap<UUID, CompletableFuture<Result>> inFlight;

public Result createReservation(input) {
  UUID id = input.getId();
  var existing = inFlight.putIfAbsent(id, new CompletableFuture<>());
  if (existing != null) {
    // Another retry is in progress; either wait or fail fast
    throw new IdempotencyLockHeldException(id);
  }
  try {
    return doCreate(input);          // commits via operator.dispatch
  } finally {
    inFlight.remove(id);              // release lock on completion
  }
}
```

**Multi-pod consideration:** the `ConcurrentHashMap` is process-local.
Two retries hitting different pods get separate locks → race re-opens.
Mitigations:
- Sticky sessions for create requests (LB-level)
- Distributed lock (Redis SETNX with TTL, or rapla's existing storage
  lock layer per [`docs/architecture/locking.md`](../architecture/locking.md))
- Accept the rare cross-pod race (rapla's storage layer has its own
  duplicate-detection on commit; worst case is one creates + one fails
  with a storage-level collision)

**Defer trigger:** start when (a) duplicate-create incidents appear in
production logs, OR (b) we have evidence of clients firing concurrent
retries (mobile / shaky-network clients).

### 2. Idempotency TTL + cache eviction

**Problem:** Long-lived UUIDs accumulate. [PRD 056](056-graphql-events-write-api.md) currently inspects
storage on every create for UUID collision — fine while UUIDs are
unique-per-request, problematic if clients reuse old UUIDs by accident
months later.

**Industry pattern:** Idempotency keys + their cached responses are
recycled after 24-72 hours. Past the window, the same UUID is treated
as a fresh request.

**Implementation considerations:**
- Where lives the cache? In-memory (Caffeine, with size + TTL) or
  persistent (Postgres table with cleanup job)?
- What goes in the cache? Full response body? Just enough to detect
  conflict? Hash of input + reference to stored entity?
- Cleanup job cadence — daily? hourly?

**Defer trigger:** when production logs show false `ID_COLLISION` errors
for UUIDs older than a day (indicates clients reusing UUIDs across
sessions).

### 3. Rate limiting

**Problem:** A misbehaving client (or hostile actor with valid creds)
fires thousands of mutations/sec. Server is fine but storage backend
(HSQLDB / Postgres) may not be; other users get degraded service.

**Industry patterns:**
- Token bucket per-user / per-tenant
- Backpressure via `429 Too Many Requests`
- Adaptive throttling based on storage backend health

**Rapla specifics:** Storage write lock is the actual contention point
(per AGENTS.md: storage layer uses lock layers). Rate limiting at the
GraphQL boundary prevents the worst client behavior before it reaches
the storage lock.

**Defer trigger:** first observed denial-of-service incident OR
multi-tenant deployment.

#### 3a. Per-user concurrency limit on read queries (design, 2026-08-12)

Narrower and cheaper than general rate limiting, and the concrete variant
[PRD 106](106-query-request-lifecycle.md) asks for: cap the number of GraphQL **read**
queries a single principal may have executing at once.

**Who it protects against.** Not the SPA — [PRD 078](078-spa-graphql-view-renderer.md)
Phase 5 makes it stop firing superseded requests. This is for clients that cannot be fixed
by shipping SPA code: API-key scripts, a runaway poll, the Swing client, a second browser
tab, an older SPA version still in a user's cache.

**Sketch.** A `WebGraphQlInterceptor` — the same seam `StoredViewInterceptor`
(`rapla-app/.../graphql/StoredViewInterceptor.java:34`) already occupies — holding a
`ConcurrentHashMap<userId, Semaphore>`.

- **Keyed on the authenticated principal, not the session or transport.** Cookie session,
  bearer token and API key all share one slot; otherwise a script sidesteps the limit by
  minting a second key.
- **Reads only** ([106 D1](106-query-request-lifecycle.md#decisions-locked)). A shared pool
  would queue a save in the event sheet behind a slow view query.
- **Reject, don't wait.** The MVC stack runs the interceptor's `Mono` on the servlet
  thread, so blocking on `acquire()` pins exactly the Tomcat thread the limit is meant to
  protect. `tryAcquire()` → GraphQL error with `extensions.code = TOO_MANY_REQUESTS` is
  both cheaper and the right signal to a script (back off). A "latest wins" variant
  (newcomer evicts the incumbent, which gets an `AbortExecutionException`) is strictly
  nicer but depends on § 5's cancellation handle.
- **Detection without a parser:** the heavy read path is exactly
  `extensions.storedView == true` — a boolean on the request, no document parsing. An
  interceptor that reads the *document* instead must be ordered **after**
  `StoredViewInterceptor`, which swaps the dummy `{ __typename }` for the stored query.
- **Process-local, deliberately.** Each pod limits for itself; a global limit would have to
  go through the store/lock layer, which is wildly disproportionate. Document it so nobody
  later files it as a bug.
- **Never hold a permit across a store-lock acquisition** — that would serialise exactly
  what the lock layer already coordinates.
- Configurable with a sane default (≈2 concurrent reads/user), disablable in
  `application.yml`: deployments with legitimate batch jobs (dhbwrapla) must be able to
  raise it without a code change.

**Cost:** ~60 lines plus a tier-3 MockMvc test (two concurrent reads, the second rejected).

**Defer trigger:** first observed incident where one principal's read traffic degrades
service for others — or the first external integrator whose client we don't control.

### 4. Query / mutation complexity limits

**Problem:** Pathological GraphQL queries (deep nesting, large
`limit:`, recursive fragments) can pin a server thread for seconds.

**Industry pattern:** `MaxQueryComplexityInstrumentation` /
`MaxQueryDepthInstrumentation` from graphql-java. Each field gets a
complexity score; queries above threshold are rejected at parse time.

**Rapla specifics:** [PRD 055](055-graphql-events-read-api.md) perf round (2026-05-27 profiling) already
identified the 14s admin query as graphql-java per-field overhead. A
complexity cap would have rejected that query before it ran.

**Existing leaning** (carry-over from [PRD 056](056-graphql-events-write-api.md) perf discussion):
- Cap at ~10000 complexity units
- Per-field weight: 1 unit; list-typed field weighs `child_complexity ×
  expected_list_size`
- Reject with clear error message + the offending path

**Defer trigger:** when (a) production logs show slow-query patterns
correlated with consumer behavior, OR (b) we have an external integrator
audience whose query shapes we don't control.

### 5. Request-level timeout + cancellation

**Problem:** Long-running queries hold server threads. Spring's default
timeout is generous; a runaway query can block other requests.

**Industry pattern:** per-request timeout (e.g., 30s) with explicit
cancellation propagation into the resolver chain. graphql-java supports
`AbortExecutionException` from `DataFetcher`s.

**Rapla specifics:** the storage layer's promise-based query path is
already cooperative — `queryAppointments(...)` returns a `Promise`;
timeout via `CompletableFuture.orTimeout`.

**Defer trigger:** first long-query incident affecting overall server
responsiveness.

#### 5a. Cancellation on client disconnect (design, 2026-08-12)

The cancellation half of § 5, as scoped by [PRD 106](106-query-request-lifecycle.md):
when a client aborts an in-flight **read**, stop executing it.

**The engine can already do it.** graphql-java **25.0** (the version resolved in the
reactor) exposes `ExecutionInput.cancel()` / `isCancelled()` and
`EngineRunningState.throwIfCancelled()` → `AbortExecutionException` — verified against
`graphql-java-25.0.jar`. The work is in the two steps before that:

1. **Noticing the disconnect — the expensive step, and the open risk.** Rapla serves
   GraphQL from blocking Spring MVC on Tomcat 11. A blocking servlet thread is never told
   the client went away; it finds out when it writes the response and gets a broken pipe —
   i.e. after doing all the work. Learning earlier requires the request to run
   asynchronously so `AsyncListener.onError` fires. Whether Spring GraphQL's MVC handler
   already does this in our configuration is [106 OQ1](106-query-request-lifecycle.md#open-questions),
   and it is what separates "a day" from "a week".
2. **Wiring the signal — small.** A `WebGraphQlInterceptor` parks the `ExecutionInput`
   (or a cancel handle) in the `GraphQLContext`; the listener calls `cancel()`. ~100 lines
   on an existing seam.

**What it would actually save.** Cancellation bites *between field fetches*, not inside a
blocking resolver — so the root fetch (`queryAppointmentsSync` via `StorageOperator`) runs
to completion regardless. That is not where the time goes, though: for a 500-row response
the cost is per-row, per-field resolution (see [PRD 035](done/035-graphql-foundations.md)'s
profiled hot spots — Micrometer context, `HandlerMethod`, `Classification.getType`), which
*is* field-granular. Realistically a large share of a 4–7 s response is abortable.

**Reads only** ([106 D1](106-query-request-lifecycle.md#decisions-locked)) — a mutation
aborted part-way can leave partially applied writes.

**Explicitly not worth it:** threading a cancellation token down through `StorageOperator`
into the query loops. Invasive, multi-pod-relevant, weeks of work, for the one part that
the field-level cancellation above mostly covers anyway.

**Defer trigger:** same as § 5 — plus [106 OQ1](106-query-request-lifecycle.md#open-questions)
answered first.

### 6. Distributed tracing on mutations

**Problem:** When something goes wrong (concurrent edits, race conditions,
storage failures), debugging requires correlating events across pods +
client + storage layer.

**Industry pattern:** OpenTelemetry spans on every mutation entry,
propagating through `operator.dispatch(...)` into the storage layer.
Trace ID in error responses so clients can supply it for support tickets.

**Rapla specifics:** [PRD 035](done/035-graphql-foundations.md) perf observability discussion already
sketches Micrometer-based timing instrumentation. Tracing is the next
layer up.

**Defer trigger:** when support tickets routinely require "what
happened on the server at 14:32" investigation.

## Pattern selection criteria

When to promote a section to its own PRD:

- **Production evidence** of the problem in question (logs, incidents,
  user reports)
- **Architectural readiness** — does rapla's deployment topology already
  have the necessary primitives (e.g., Redis for distributed cache,
  observability backend, rate-limit middleware)?
- **Implementation cost vs. user impact** — robust patterns add
  ongoing maintenance; ensure the user impact justifies it

## What's currently shipped (lean version, in [PRD 056](056-graphql-events-write-api.md))

The bare minimum for safe-retry semantics:
- Client UUID for new entities ([PRD 056](056-graphql-events-write-api.md) §6 — `client UUIDs` lock)
- Same UUID + matching content on retry → no-op success ([PRD 056](056-graphql-events-write-api.md) OQ5)
- Same UUID + differing content → `ID_COLLISION` ([PRD 056](056-graphql-events-write-api.md) OQ5)
- Single-pod single-thread atomicity via `operator.dispatch(UpdateEvent)`
  ([PRD 035](done/035-graphql-foundations.md) architecture)

That covers the network-glitch retry case for rapla's current scale.
The patterns in this PRD are the next-level concerns when rapla
outgrows that.

## Decision log

- **2026-05-28** — PRD opened as draft / placeholder. Triggered by PRD
  056 OQ5 idempotency discussion — the heavy patterns (in-flight lock,
  TTL, distributed cache, rate limiting) don't earn their cost at
  rapla's current scale. Parked here for revival when scale or
  multi-tenant deployment changes the calculus.
