# PRD 008: Server Cleanup

**Status:** draft — placeholder, scope to be filled in
**Date:** 2026-05-07

## Goal

Server-side cleanup follow-ups identified during PRD 005 multi-module split.

## Scope (placeholder — fill in)

Known items called out in earlier conversations / PRD 005 cleanup:

- **Remove reactive abstractions (`io.reactivex.rxjava3`, `org.reactivestreams`).** Per direction 2026-05-07: rxjava and reactive-streams are scheduled for removal across the codebase. PRD 005 cleanup E1 deliberately did NOT add explicit `<dependency>` declarations for these in `rapla-client` / `rapla-app` (they're flagged as "used undeclared" by `dependency:analyze`); those flags resolve themselves once the imports are gone.
- Replace each `Promise<T>` / `CommandScheduler` / RxJava observable with the equivalent `CompletableFuture<T>` (or similar JDK-native primitive). The existing `org.rapla.scheduler.UnsynchronizedPromise` and `SynchronizedPromise` adapters become deletable once consumers move off `Promise`.
- Audit `CommandScheduler` callers for blocking patterns that would become trivial under `CompletableFuture`.

## Plan

To be filled in.

## Tests

Existing tests must stay green throughout.

## Open Questions

- Scope: just rxjava/reactive removal, or broader server simplification?
- Sequencing relative to PRD 002 multi-tenancy and the dhbw direction-change in PRD 003?
