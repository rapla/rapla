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

## Keep all output OUT of the git repo (profiles AND crash dumps)

**Never write profiler output — or let JVM dumps land — inside a project checkout.**
async-profiler's `file=` path and, crucially, the JVM's own dump files (OpenJ9
`core.*.dmp` / `javacore.*.txt` / `jitdump.*.dmp` / `Snap.*.trc`; HotSpot
`hs_err_pid*.log` / `*.hprof`) default to the **JVM's working directory** — which for
a `spring-boot:run` boot is the *project root*. That dumps trash straight into the repo
(and worse, into a *plugin* checkout like `dhbwrapla` whose `logs/` is gitignored but
whose root is not). Always send them to a scratch dir outside any git repo:

```bash
PROF=/tmp/rapla-prof            # or your session scratchpad — anywhere NOT under a git checkout
mkdir -p "$PROF"
```

- Write every `-f` output there (`-f "$PROF/profile.collapsed"`).
- When you start a JVM with the agent baked in (boot profiling, below), also pin JVM
  dumps there so a crash can't litter the repo:
  - OpenJ9 (this machine's Semeru JDK): `-Xdump:directory=$PROF`
  - HotSpot: `-XX:HeapDumpPath=$PROF -XX:ErrorFile=$PROF/hs_err_%p.log`

## Profiling startup / boot (agent baked in at JVM launch)

Attach-after-start misses the boot. To profile startup, load the agent at JVM launch via
`-agentpath` and dump after the `Started …` marker. **Pick the event by what you're
measuring:** `wall` captures blocked/IO time (e.g. a slow DB connection open); `itimer`
captures on-CPU work (entity load, JSON, index build) and is **more robust on OpenJ9**.

```bash
PROF=/tmp/rapla-prof; mkdir -p "$PROF"
ASO=/home/chris/.local/share/async-profiler/lib/libasyncProfiler.so
AGENT="-agentpath:${ASO}=start,event=itimer,interval=5ms"   # or event=wall for IO-bound phases
# Run via the normal dev recipe (server-lifecycle skill), adding the agent + dump dir as JVM args.
# For dhbw use its aggregator recipe; the key part is:
mvn ... spring-boot:run -Dspring-boot.run.jvmArguments="$AGENT -Xdump:directory=$PROF"
# After the "Started RaplaSpringBootApplication in …" marker appears:
PID=$(jps -l | awk '/RaplaSpringBoot/ {print $1}')
$ASPROF stop -o collapsed -f "$PROF/boot.collapsed" $PID
```

**OpenJ9 caveat (learned the hard way, 2026-07-09):** `asprof stop` can crash the
Semeru/OpenJ9 VM while flushing — **especially with `-o jfr`** (native crash in the
FlightRecorder writer). Prefer `-o collapsed`; the output file is still written before the
VM dies. Because the VM may crash on stop, the `-Xdump:directory=$PROF` above is what keeps
the resulting core/javacore/jitdump/Snap files out of the repo. If you must produce a JFR,
expect the crash and convert with `jfrconv -o collapsed in.jfr out` (a truncated JFR often
won't parse — another reason to prefer collapsed directly).

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

## Known findings — PRD 035 Cut C GraphQL slow query (2026-05-27, fixed)

The Person query `{ allocatables(filter: {typeKeyEq: "Person"}) { displayName classification { ... on PersonClassification { firstname … 11 fields … } } } }` took **15.0 s** for 42k rows under the dhbw admin user, dropping to **6.35 s** after the fix set below (58% reduction).

### Initial profile (3259 samples at 5 ms)

| Top leaf method | Samples | Root cause |
|---|---:|---|
| `ExecutionStrategy.lambda$fetchField$8` | 33 | graphql-java per-field dispatch — framework floor |
| `Method.toGenericString` (via `Executable.sharedToGenericString`) | 24 | Spring's `DataFetcherHandlerMethodSupport.<init>` allocates a HandlerMethod **per dispatch** |
| `ExecutionStrategy.fetchField` | 25 | framework |
| `ExecutionStepInfoFactory.createExecutionStepInfo` | 18 | framework |
| `DefaultContextSnapshotFactory.captureFromContext` | 16 | **Micrometer Context Propagation** — Spring's `ContextDataFetcherDecorator` captures thread-locals per field |
| `DataFetcherHandlerMethodSupport.<init>` | 10 | Same as line 2 |
| `Logger.isTraceEnabled` (logback) | 12 | Trace-level check at 462k field invocations |

### Fixes applied (documented in `docs/graphql.md` § "Performance patterns")

1. **`LightDataFetcher` singletons** for per-row fields — the `TrivialDataFetcher` marker tells Spring's `ContextTypeVisitor` to skip the Micrometer wrap AND tells graphql-java's `ExecutionStrategy` to skip heavy instrumentation hooks. Replaces `@SchemaMapping` methods on the hot path. Mechanism + code template in `docs/graphql.md`.
2. **Per-attribute `LightDataFetcher` class** for typed classification fields — one instance per `(DynamicType, Attribute)`, allocated once at schema build, reused for every row.
3. **`RequestContextInstrumentation`** — caches caller / PermissionController / RaplaLocale once at `beginExecution` in the per-query `GraphQLContext`. Fetchers read from there instead of re-resolving per field.
4. **`HotSwappableGraphQlSource.validateInterfaceCoverage`** — boot-time check that every interface field has an explicit DataFetcher on every implementation. Catches the "forgot to re-wire on a new generated type" silent-null bug.
5. **`-Dspring-boot.run.optimizedLaunch=false`** — removes the dev-mode `-XX:TieredStopAtLevel=1` that limits JIT to tier 1. Saves ~1 s on hot-path queries.

**Result:** 15.0 s → 6.35 s (Tier 1 floor). Below that requires Tier 2 architectural changes (batched/projected GraphQL fields). The remaining 6.35 s is graphql-java's framework floor (~3 s) + Jackson serialization of the 14 MB response (~3 s) + our resolver bodies (~0.5 s).

### Don't try

**`ClassificationImpl.getType()` caching was considered and rejected.** The candidate fix (cache `resolver.tryResolve` result on the Classification instance) only saves ~25-70 ms, and breaks correctness on DynamicType admin updates — the operator's update path does NOT walk referencing Classifications to invalidate held DynamicTypeImpl references, so a cached pointer goes stale until something forces a new lookup. Not worth the correctness risk for the small win.

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
- A startup performance issue — use the **Profiling startup / boot** section above (agent baked in at launch, dumps pinned to a scratch dir). For a coarse class-load view instead, `-Xverbose:class` or actuator's `/startup` are lighter.
- A multi-pod production issue — need a deployable profiling agent, not async-profiler ad-hoc attach
