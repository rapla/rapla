package org.rapla.storage.impl.server.readmodel;

import org.rapla.entities.User;
import org.rapla.entities.internal.UserImpl;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * PRD 129 — a user's groups including parents, cached per resident user instance. Recomputing them was
 * 91 % of the permission time. An entry is only served for the exact instance it was computed for, so a
 * changed user (new resident instance) or an unstored draft never gets a stale set; a category change
 * drops everything, and the generation keeps a computation that raced with it out of the cache.
 */
public class UserGroupsCache
{
    private record Entry(User user, Collection<String> groups) {}

    private final Map<String, Entry> byUserId = new ConcurrentHashMap<>();
    private final AtomicLong generation = new AtomicLong();

    public Collection<String> groupsOf(User user)
    {
        long gen = currentGeneration();
        Entry entry = byUserId.get(user.getId());
        if (entry != null && entry.user() == user)
        {
            return entry.groups();
        }
        Collection<String> groups = java.util.Set.copyOf(UserImpl.getGroupsIncludingParents(user));
        if (currentGeneration() == gen)
        {
            Entry fresh = new Entry(user, groups);
            byUserId.put(user.getId(), fresh);
            if (currentGeneration() != gen)
            {
                byUserId.remove(user.getId(), fresh);
            }
        }
        return groups;
    }

    protected long currentGeneration()
    {
        return generation.get();
    }

    /** The user instance the entry for {@code userId} was computed for, or null. */
    public User cachedUser(String userId)
    {
        Entry entry = byUserId.get(userId);
        return entry != null ? entry.user() : null;
    }

    public void invalidate(String userId)
    {
        if (userId != null)
        {
            byUserId.remove(userId);
        }
    }

    public void invalidateAll()
    {
        generation.incrementAndGet();
        byUserId.clear();
    }
}
