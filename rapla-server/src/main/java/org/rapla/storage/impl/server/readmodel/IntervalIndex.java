package org.rapla.storage.impl.server.readmodel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A generic, in-memory, per-key interval index supporting an O(log n + k)
 * two-sided overlap query, designed to replace the half-range prefilter
 * pathology of the legacy {@code appointmentMap} (PRD 082 #1/#2, PRD 086).
 *
 * <p>Per key {@code K} the index keeps:
 * <ul>
 *   <li>a {@link NavigableSet} of entries sorted by {@code start} (backed by a
 *       {@link ConcurrentSkipListSet} for lock-free reads),</li>
 *   <li>a side-set holding open-ended and "long-runner" entries whose duration
 *       exceeds the duration cap {@code D} — these are always scanned and are
 *       <em>excluded</em> from {@code maxBlockDuration},</li>
 *   <li>a high-water-mark {@code maxBlockDuration}, capped at {@code D}, used as
 *       the tight lower bound of the start sub-range: a bounded block can only
 *       overlap {@code [winStart, winEnd)} if its start is
 *       {@code >= winStart - maxBlockDuration}.</li>
 * </ul>
 *
 * <p><b>Thread-safety:</b> reads ({@link #overlapping}) are lock-free, relying
 * on the concurrency of {@link ConcurrentSkipListSet} and the atomic
 * {@code maxBlockDuration}. Mutations ({@link #put}, {@link #remove}) are
 * expected to run under the operator write lock; they touch concurrent
 * structures so an interleaved read never sees a torn structure, but no
 * cross-key atomicity is provided beyond that.
 *
 * @param <K> the per-key partition type (e.g. an allocatable id)
 * @param <V> the payload type returned by queries (the caller dedups)
 */
public final class IntervalIndex<K, V>
{
    /** Duration cap D: entries longer than this are routed to the side-set. */
    private final long durationCap;

    private final ConcurrentHashMap<K, PerKey<V>> byKey = new ConcurrentHashMap<>();

    /** Monotonic identity tiebreaker so equal-start entries stay distinct. */
    private final AtomicLong seqGen = new AtomicLong(0L);

    /**
     * @param durationCap the threshold D (same unit as the start/end values,
     *                    e.g. millis for ~14 days). Entries with
     *                    {@code (end - start) > durationCap} or
     *                    {@code openEnded == true} go to the side-set.
     */
    public IntervalIndex(long durationCap)
    {
        this.durationCap = durationCap;
    }

    private static final class Entry<V>
    {
        final V value;
        final long start;
        final long end;
        final boolean openEnded;
        final long seq;

        Entry(V value, long start, long end, boolean openEnded, long seq)
        {
            this.value = value;
            this.start = start;
            this.end = end;
            this.openEnded = openEnded;
            this.seq = seq;
        }
    }

    private static final class PerKey<V>
    {
        // Sorted by start, then by a per-entry identity tiebreaker so that
        // distinct entries with equal start are kept distinct (no dedup by start).
        final NavigableSet<Entry<V>> byStart = new ConcurrentSkipListSet<>(
                Comparator.<Entry<V>>comparingLong(e -> e.start)
                        .thenComparingLong(e -> e.seq));
        // Open-ended / long-runner entries, always scanned.
        final Set<Entry<V>> sideSet = ConcurrentHashMap.newKeySet();
        // High-water mark of bounded-entry durations, capped at D, never recomputed on remove.
        final AtomicLong maxBlockDuration = new AtomicLong(0L);
    }

    /**
     * Insert an interval {@code [start, end)} carrying {@code value} under {@code key}.
     * An entry is routed to the side-set when {@code openEnded} is true OR its
     * duration exceeds the cap {@code D}; such entries are excluded from
     * {@code maxBlockDuration} (which therefore stays {@code <= D}).
     *
     * @param value     the payload (returned verbatim by {@link #overlapping})
     * @param start     inclusive interval start
     * @param end       exclusive interval end (ignored when {@code openEnded})
     * @param openEnded whether the interval has no defined end (forward-infinite)
     */
    public void put(K key, V value, long start, long end, boolean openEnded)
    {
        final long seq = seqGen.getAndIncrement();
        final Entry<V> entry = new Entry<>(value, start, end, openEnded, seq);
        final PerKey<V> pk = byKey.computeIfAbsent(key, k -> new PerKey<>());
        final long duration = end - start;
        if (openEnded || duration > durationCap)
        {
            pk.sideSet.add(entry);
        }
        else
        {
            pk.byStart.add(entry);
            if (duration > 0)
            {
                // Raise the high-water mark, but never above the cap D.
                final long capped = Math.min(duration, durationCap);
                pk.maxBlockDuration.accumulateAndGet(capped, Math::max);
            }
        }
    }

    /**
     * Remove the entry matching {@code value} (by {@code equals}) with the given
     * {@code start}/{@code end}/{@code openEnded} from {@code key}. A non-matching
     * or already-removed entry is a no-op. {@code maxBlockDuration} is deliberately
     * not recomputed (it stays a monotonic high-water mark capped at D).
     */
    public void remove(K key, V value, long start, long end, boolean openEnded)
    {
        final PerKey<V> pk = byKey.get(key);
        if (pk == null)
        {
            return;
        }
        final long duration = end - start;
        if (openEnded || duration > durationCap)
        {
            pk.sideSet.removeIf(e -> e.start == start && e.end == end
                    && e.openEnded == openEnded && Objects.equals(e.value, value));
        }
        else
        {
            // Entries are ordered by (start, seq), so all entries sharing this start are
            // contiguous: narrow to that slice and scan only it (O(log n + dups-at-start))
            // rather than the whole per-key set — the write path removes one appointment's
            // blocks from dense keys (the "collector") on every mutation.
            final Entry<V> lo = new Entry<>(null, start, 0L, false, Long.MIN_VALUE);
            final Entry<V> hi = new Entry<>(null, start, 0L, false, Long.MAX_VALUE);
            pk.byStart.subSet(lo, true, hi, true).removeIf(
                    e -> e.end == end && e.openEnded == openEnded && Objects.equals(e.value, value));
        }
        if (pk.byStart.isEmpty() && pk.sideSet.isEmpty())
        {
            // Prune the empty per-key structure. Use computeIfPresent so we don't
            // drop a per-key map that a concurrent put has just re-populated.
            byKey.computeIfPresent(key, (k, cur) ->
                    (cur.byStart.isEmpty() && cur.sideSet.isEmpty()) ? null : cur);
        }
    }

    /**
     * All payloads whose interval {@code [start, end)} overlaps the window
     * {@code [winStart, winEnd)} under {@code key}, in O(log n + k). Overlap uses
     * half-open semantics: {@code end > winStart && start < winEnd} — touching
     * edges do not overlap. {@code winStart == Long.MIN_VALUE} and
     * {@code winEnd == Long.MAX_VALUE} are handled (unbounded window sides).
     * The caller is responsible for de-duplicating across keys.
     */
    public Collection<V> overlapping(K key, long winStart, long winEnd)
    {
        final ArrayList<V> result = new ArrayList<>();
        final PerKey<V> pk = byKey.get(key);
        if (pk == null)
        {
            return result;
        }

        // Bounded entries: tight two-sided start sub-range
        // [winStart - maxBlockDuration, winEnd).
        final long maxDur = pk.maxBlockDuration.get();
        final long lowerBound;
        if (winStart == Long.MIN_VALUE)
        {
            lowerBound = Long.MIN_VALUE;
        }
        else
        {
            final long candidate = winStart - maxDur;
            // guard against underflow (winStart - maxDur wrapping below MIN_VALUE)
            lowerBound = (candidate > winStart) ? Long.MIN_VALUE : candidate;
        }

        // seq = MIN_VALUE makes the synthetic lo sort before any real entry with
        // the same start; tailSet(lo, true) therefore includes start == lowerBound.
        final Entry<V> lo = new Entry<>(null, lowerBound, 0L, false, Long.MIN_VALUE);
        final NavigableSet<Entry<V>> sub;
        if (winEnd == Long.MAX_VALUE)
        {
            sub = pk.byStart.tailSet(lo, true);
        }
        else
        {
            // Upper bound start < winEnd: hi has seq = MIN_VALUE and is excluded,
            // so any real entry with start == winEnd is also excluded (strict <).
            final Entry<V> hi = new Entry<>(null, winEnd, 0L, false, Long.MIN_VALUE);
            sub = pk.byStart.subSet(lo, true, hi, false);
        }
        for (Entry<V> e : sub)
        {
            if (e.end > winStart && e.start < winEnd)
            {
                result.add(e.value);
            }
        }

        // Side-set: always scanned (open-ended + long-runners).
        for (Entry<V> e : pk.sideSet)
        {
            final boolean overlaps = e.openEnded
                    ? (e.start < winEnd)                       // forward-infinite
                    : (e.end > winStart && e.start < winEnd);  // bounded long-runner
            if (overlaps)
            {
                result.add(e.value);
            }
        }
        return result;
    }
}
