package org.rapla.storage.impl.server.readmodel;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A generic in-memory membership index: each key maps to a set of member ids.
 *
 * <p>This is one of PRD 082's two shared in-memory index kinds (the bucket index;
 * the other is the interval index). Its first consumer is PRD 087's type-bucket
 * (allocatable / reservation classification-type {@code typeKey -> {entityId}}),
 * supporting single-key lookup, multi-key union, and a re-key {@link #move} for
 * when an entity's bucket changes (e.g. an allocatable's classification type is
 * edited).
 *
 * <h2>Thread-safety</h2>
 * Backed by a {@link ConcurrentHashMap} of synchronized {@link Set}s. Reads
 * ({@link #members}, {@link #membersUnion}) are safe to run concurrently with
 * other reads and with mutations: {@link #members} returns an immutable defensive
 * snapshot, never the live set. Mutations ({@link #put}, {@link #remove},
 * {@link #move}) are expected to run under the operator's write lock — the
 * read-model is rebuilt/maintained single-writer — so the per-bucket
 * empty-prune compound action does not need an additional cross-bucket guard.
 *
 * @param <K> bucket key type (e.g. classification {@code typeKey})
 * @param <V> member id type (e.g. entity id)
 */
public final class BucketIndex<K, V>
{
    private final ConcurrentHashMap<K, Set<V>> buckets = new ConcurrentHashMap<>();

    /**
     * Add {@code member} to {@code key}'s bucket. Idempotent: re-adding an
     * existing member is a no-op (no duplicate). Creates the bucket on first use.
     */
    public void put(K key, V member)
    {
        if (key == null || member == null)
        {
            return;
        }
        buckets.computeIfAbsent(key, k -> Collections.synchronizedSet(new LinkedHashSet<>())).add(member);
    }

    /**
     * Remove {@code member} from {@code key}'s bucket. No-op if the key is absent
     * or the member is not in the bucket. Prunes the bucket once it becomes empty
     * so a stale key never lingers as an empty set.
     */
    public void remove(K key, V member)
    {
        if (key == null || member == null)
        {
            return;
        }
        buckets.computeIfPresent(key, (k, set) -> {
            set.remove(member);
            return set.isEmpty() ? null : set;
        });
    }

    /**
     * Relocate {@code member} from {@code oldKey} to {@code newKey} (remove from
     * the old bucket, add to the new). Used when an entity's bucket changes —
     * e.g. an allocatable's classification type is edited. A no-op move
     * ({@code oldKey.equals(newKey)}) leaves the member in place.
     */
    public void move(K oldKey, K newKey, V member)
    {
        remove(oldKey, member);
        put(newKey, member);
    }

    /** Drop every bucket — used for a full rebuild (boot / drift recovery). */
    public void clear()
    {
        buckets.clear();
    }

    /**
     * The members of {@code key}'s bucket as an immutable snapshot. Returns an
     * empty set when the key is absent — never {@code null}.
     */
    public Set<V> members(K key)
    {
        if (key == null)
        {
            return Collections.emptySet();
        }
        Set<V> set = buckets.get(key);
        if (set == null)
        {
            return Collections.emptySet();
        }
        synchronized (set)
        {
            return Collections.unmodifiableSet(new LinkedHashSet<>(set));
        }
    }

    /**
     * The union of the members of all the given {@code keys} as a fresh, immutable
     * set. Empty when no key matches; never {@code null}. Duplicate members across
     * buckets appear once.
     */
    public Set<V> membersUnion(Collection<K> keys)
    {
        if (keys == null || keys.isEmpty())
        {
            return Collections.emptySet();
        }
        Set<V> union = new LinkedHashSet<>();
        for (K key : keys)
        {
            if (key == null)
            {
                continue;
            }
            Set<V> set = buckets.get(key);
            if (set != null)
            {
                synchronized (set)
                {
                    union.addAll(set);
                }
            }
        }
        return Collections.unmodifiableSet(union);
    }
}
