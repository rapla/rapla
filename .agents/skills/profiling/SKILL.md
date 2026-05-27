---
name: profiling
description: Use when chasing CPU performance problems in the running rapla JVM — slow queries, slow controllers, hot loops, framework overhead. Covers async-profiler 4.0 (works with both HotSpot and Eclipse OpenJ9, the SDKMAN-installed JDK on this machine), the attach-to-running-PID workflow, output formats (HTML flame graph + collapsed-text for grep), and reading the results. Skip when targeted regression tests + log timing would be cheaper — profiling is for "where does the time actually go?"
---

# Profiling rapla (CPU sampling with async-profiler)

The JDK on this machine (SDKMAN `21.0.11.fx-sem` / Eclipse OpenJ9 Semeru) is
**OpenJ9, not HotSpot**, so `-XX:StartFlightRecording` / `jcmd JFR.*` do not
work — they're HotSpot-only. **async-profiler** is the cross-VM sampling
profiler that works against both.

## Setup (one-time)

```bash
mkdir -p /home/chris/.local/share/async-profiler
cd /home/chris/.local/share/async-profiler
curl -sL https://github.com/async-profiler/async-profiler/releases/download/v4.0/async-profiler-4.0-linux-x64.tar.gz \
  | tar xz --strip-components=1
./bin/asprof --version   # → "Async-profiler 4.0 built on ..."
```

Files of interest:
- `bin/asprof` — the CLI that attaches via JVM TI to a running JVM
- `lib/libasyncProfiler.so` — the native agent (auto-loaded)
- `bin/jfrconv` — converts to JFR format if you want to use JMC

## Event types

| Event | Mechanism | Works on OpenJ9 | Notes |
|---|---|---|---|
| `cpu` (default) | Linux `perf_event_open` | ✗ (requires `kernel.perf_event_paranoid <= 1`) | Most accurate. Needs root or kernel tuning. |
| `itimer` | SIGPROF signal | ✓ | Reliable everywhere. Slightly less accurate than perf events. **Default for this machine.** |
| `alloc` | JVM TI sampling object allocations | ✓ | Allocation pressure / GC investigation. |
| `wall` | Wall-clock sampling (every thread) | ✓ | Includes blocked time — useful for IO/lock contention. |

`kernel.perf_event_paranoid` is `2` on this WSL2 install (`cat /proc/sys/kernel/perf_event_paranoid`), which blocks the `cpu` event without root. **Use `-e itimer`** unless you've tuned the kernel.

## Attach-to-running workflow

The dev server is already running per AGENTS.md §8 — don't restart it for profiling. async-profiler attaches via JVM TI to the live PID.

```bash
PID=$(jps -l | awk '/RaplaSpringBoot/ {print $1}')
ASPROF=/home/chris/.local/share/async-profiler/bin/asprof

# Start sampling. -i sets the sampling interval (default 10ms; lower = more
# samples + more overhead). 5ms is usually fine for an interactive query.
$ASPROF start -e itimer -i 5ms $PID

# Run the workload you want to profile — query, controller call, anything.
# The profiler captures all threads while it's running.
curl -s ... # or trigger your slow path

# Stop and dump. Pick ONE output format:
$ASPROF stop -f /tmp/profile.html $PID                   # interactive flame graph
$ASPROF stop -f /tmp/profile.collapsed -o collapsed $PID # text format for grep/awk
$ASPROF stop -f /tmp/profile.jfr -o jfr $PID             # JFR file for JDK Mission Control
$ASPROF stop -f /tmp/profile.txt -o flat $PID            # flat hottest-leaves text report
```

`asprof status $PID` shows whether profiling is currently running and how long.

## Reading the output

### Flame graph (HTML)

Open `/tmp/profile.html` in a browser. **Width = sample count = wall-clock time.** Click a frame to zoom in. The hottest leaf frames (where the CPU actually is) appear at the **top** of each stack column.

Useful for visual exploration when you don't know where to look yet.

### Collapsed text format

Each line is `frame1;frame2;...;leaf  sample_count`. Lots of grep / awk leverage:

```bash
# Top 20 hottest leaf methods
awk -F';' '{print $NF}' /tmp/profile.collapsed \
  | sort | uniq -c | sort -rn | head -20

# Top callstacks by sample count (last 4 frames)
awk -F';' '{n=NF; printf "%s\n", $(n-3) ";" $(n-2) ";" $(n-1) ";" $n}' /tmp/profile.collapsed \
  | sort | uniq -c | sort -rn | head -15

# Where rapla code shows up
grep -oE "(org/rapla/[a-zA-Z/]+\.[a-zA-Z\$]+)" /tmp/profile.collapsed \
  | sort | uniq -c | sort -rn | head -20

# Frame-count by package (frames, not samples — heavier the package's footprint
# in the call tree, the higher the number)
grep -oE "[a-z]+/[a-z]+/[a-z]+" /tmp/profile.collapsed \
  | sort | uniq -c | sort -rn | head -15
```

**Interpreting the counts:**
- The leaf-frame counts (first awk above) tell you where CPU time is actually being spent.
- The package counts measure how much call-tree depth a package contributes — useful for "is this framework doing too much work?" but inflated by deep stacks.

### Reading the stack direction

async-profiler's collapsed format is `caller;...;callee`. The LAST frame on the line is the leaf (where the CPU was sampled). graphql-java's `ExecutionStrategy.fetchField` appearing as a leaf means the CPU is spinning inside graphql-java's per-field dispatch machinery, NOT in our resolver bodies.

## Known findings — PRD 035 Cut C GraphQL slow query (2026-05-27)

The Person query `{ allocatables(filter: {typeKeyEq: "Person"}) { displayName classification { ... on PersonClassification { firstname … 11 fields … } } } }` took **15s** for 42k rows under the dhbw admin user. Profile breakdown (3259 samples at 5ms intervals):

| Top leaf method | Samples | Root cause |
|---|---:|---|
| `ExecutionStrategy.lambda$fetchField$8` | 33 | graphql-java's per-field dispatch state machine — unavoidable framework |
| `Method.toGenericString` (via `Executable.sharedToGenericString`) | 24 | Reflection metadata reconstruction by Spring's `DataFetcherHandlerMethodSupport.<init>` — **per dispatch** |
| `ExecutionStrategy.fetchField` | 25 | framework |
| `ExecutionStepInfoFactory.createExecutionStepInfo` | 18 | framework |
| `DefaultContextSnapshotFactory.captureFromContext` | 16 | **Micrometer Context Propagation** captures thread-local state per field — observability hook we don't use |
| `DataFetcherHandlerMethodSupport.<init>` | 10 | **Spring constructs a new HandlerMethod per dispatch** — `@SchemaMapping` allocates per call instead of caching |
| `Logger.isTraceEnabled` (logback) | 12 | Trace-level check is hot at 462k field invocations |
| `ClassificationImpl.getType` | 21 | **Our code** — `resolver.tryResolve(parentId, DynamicType.class)` per `getValue` call instead of caching |

**Three concrete fixes the profile justified** (none of which we'd have found by guessing):

1. **Disable Micrometer GraphQL context-snapshot propagation.** Spring auto-installs `ContextDataFetcherDecorator` which calls `DefaultContextSnapshotFactory.captureFromContext` per field. If we don't observability-trace GraphQL fields, this is pure overhead.

2. **Make our generated DataFetchers `TrivialDataFetcher`** to bypass Spring's per-dispatch `HandlerMethod` construction. Saves the `Method.toGenericString` + reflection metadata cost.

3. **Cache `Classification.getType()`** per Classification instance (the parent DynamicType doesn't change after construction). Currently fires `resolver.tryResolve` per attribute access.

Pre-fix baseline: 15s for 42k × 11 fields. Estimated combined gain from the three above: 4-7s.

## Common pitfalls

- **`Profiling started` returns immediately, but `--- Execution profile ---  Total samples : N` only prints on stop.** Always confirm sample count is non-trivial (>100 for an interactive query) before drawing conclusions.
- **First-run samples are often misleading** — JIT compilation, class loading, and Tomcat warm-up dominate the first request after server start. Run the workload once to warm, then start a fresh profile.
- **`asprof status $PID` shows seconds since start, not sample count** — use `stop` to see total samples.
- **flame graph HTML files are ~50 KB and open locally** — no server needed. Just `xdg-open /tmp/profile.html` or scp to a Mac and open in Safari.
- **OpenJ9 vs HotSpot detection**: `java -version 2>&1 | grep -q OpenJ9 && echo "use -e itimer" || echo "use -e cpu"` — the failure mode for `-XX:StartFlightRecording` on OpenJ9 is `JVMJ9VM007E Command-line option unrecognised`, **fatal at JVM launch** (no server starts). Recover by removing the JFR flag.

## Quick recipe for "this controller / query is slow"

```bash
PID=$(jps -l | awk '/RaplaSpringBoot/ {print $1}')
ASPROF=/home/chris/.local/share/async-profiler/bin/asprof
$ASPROF start -e itimer -i 5ms $PID

# Warm: one call to flush JIT
curl -s ... > /dev/null
# Measure: the actual run we want to profile (2-3 iterations is fine)
curl -s ... > /dev/null
curl -s ... > /dev/null

$ASPROF stop -f /tmp/profile.html $PID
$ASPROF stop -f /tmp/profile.collapsed -o collapsed $PID
# (stop only fires once; run start+stop twice if you want both formats)

# Headline numbers
awk -F';' '{print $NF}' /tmp/profile.collapsed | sort | uniq -c | sort -rn | head -10
grep -oE "(org/rapla/[a-zA-Z/]+\.[a-zA-Z\$]+)" /tmp/profile.collapsed | sort | uniq -c | sort -rn | head -10
```

If the top frames are all in `graphql-java/Spring/Tomcat` and not in `org/rapla`, the cost is framework overhead — applying tuning to graphql-java configuration (instrumentation chain, executor strategy, trivial-fetcher markers) typically wins more than micro-optimizing the rapla code. If the top frames are in `org/rapla`, look there first.

## Allocation profile (for GC issues)

Swap the event:

```bash
$ASPROF start -e alloc -i 1m $PID   # sample every 1 MB allocated
# run workload
$ASPROF stop -f /tmp/alloc.html $PID
```

Useful for "why is GC spiking under load" or "we allocate too much per request."

## When NOT to use this skill

- A single failing test — log statements + targeted debug
- A startup performance issue — `mvn spring-boot:run` startup goes through plugin classloading; profile-on-startup is awkward. Easier to use `-Xverbose:class` or check actuator's `/startup`
- A multi-pod production issue — need a deployable profiling agent, not async-profiler ad-hoc attach
