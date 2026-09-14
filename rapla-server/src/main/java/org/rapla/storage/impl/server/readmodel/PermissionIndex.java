package org.rapla.storage.impl.server.readmodel;

import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Permission.AccessLevel;
import org.rapla.framework.RaplaException;
import org.rapla.storage.PermissionController;
import org.rapla.storage.StorageOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PRD 082 #8 / PRD 083 Part A — in-memory permission read index.
 *
 * <p>Answers "the set of allocatable ids a given user may READ" (and the
 * effective {@link AccessLevel}) without the per-query {@code canRead × all}
 * scan in the GraphQL controller loop. This is a bespoke index (not an
 * IntervalIndex / BucketIndex).
 *
 * <p><b>First cut — compute-and-cache per user.</b> On the first ask for a
 * user, the index iterates the resident allocatables once, delegates the
 * permission decision to the real {@link PermissionController} (so the answer
 * can never diverge from {@code canRead} — AGENTS.md §12), and caches the
 * readable-id set keyed by the user's id. Subsequent asks for the same user
 * are O(1). The cache is concurrent so multiple request threads can share it.
 *
 * <p><b>Why delegate to {@code PermissionController} rather than re-derive the
 * rule:</b> the index is about <i>precomputing / caching the answer</i> per
 * user, not re-implementing permission semantics (level ordering, READ_TYPE,
 * owner shortcut, time windows, group/category-hierarchy expansion). Those all
 * live in {@code PermissionController.canRead} / {@code hasUserAccessAtLeast}
 * and are reused verbatim. This keeps the §12 equivalence trivially true.
 *
 * <p><b>Maintenance / invalidation (wired separately).</b> A cached entry is a
 * snapshot of "what this user could read at compute time". It must be dropped
 * when anything that feeds {@code canRead} changes:
 * <ul>
 *   <li>an allocatable's permission list changes → {@link #invalidateAll()}
 *       (an allocatable change can affect many users);</li>
 *   <li>a user's group membership changes → {@link #invalidate(User)} for that
 *       user (the caller-side recompute the PRD calls out — cheap, touches only
 *       the changed principal, not thousands of entity rows);</li>
 *   <li>a category/group-hierarchy change → {@link #invalidateAll()} (rare;
 *       degrades to a full recompute).</li>
 * </ul>
 * The wiring (calling these from {@code LocalAbstractCachableOperator}'s
 * change-notification seam) is done in a separate, sequential step; this class
 * only exposes the hooks.
 *
 * <p>Deferred for a later optimization pass (kept correct, not fast, for now):
 * the inverted {@code access_grant} structure (PRD 083 §"Expand the hierarchy
 * on the CALLER side") that would avoid even the once-per-user full scan, the
 * world-readable / READ_TYPE type-bucket union (PRD 087), and reservation
 * owner-based scoping. The current per-user full scan is correct for all of
 * those because {@code canRead} already folds them in.
 */
public class PermissionIndex
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PermissionIndex.class);

    /** Levels probed, highest first, when reporting effective access. */
    private static final AccessLevel[] PROBE_ORDER =
            { AccessLevel.ADMIN, AccessLevel.ALLOCATE, AccessLevel.READ };

    private final StorageOperator operator;
    private final PermissionController permissionController;

    /** user-id → readable allocatable-id set (immutable snapshot). */
    private final Map<String, Set<String>> readableByUser = new ConcurrentHashMap<>();

    public PermissionIndex(StorageOperator operator, PermissionController permissionController)
    {
        this.operator = operator;
        this.permissionController = permissionController;
    }

    /**
     * The set of allocatable ids {@code user} may READ. Equivalent — by
     * construction — to {@code { a.getId() : canRead(a, user) }} over all
     * resident allocatables. Computed once per user and cached.
     *
     * @return an unmodifiable id set (never null; empty if the user reads
     *         nothing or is null)
     */
    public Set<String> readableAllocatables(User user)
    {
        if (user == null)
        {
            return Collections.emptySet();
        }
        final String userId = user.getId();
        if (userId == null)
        {
            // Unsaved / synthetic user — can't key the cache; compute directly.
            return computeReadable(user);
        }
        return readableByUser.computeIfAbsent(userId, id -> computeReadable(user));
    }

    /**
     * Effective access level of {@code user} on the allocatable with id
     * {@code allocatableId}, or {@code null} if the id is unknown <i>or</i> the
     * user cannot read it (existence does not leak — AGENTS.md §12). Probes
     * ADMIN → ALLOCATE → READ via {@link PermissionController#hasUserAccessAtLeast}.
     */
    public AccessLevel accessLevel(User user, String allocatableId)
    {
        if (user == null || allocatableId == null)
        {
            return null;
        }
        // Gate on the cached readable set first so an unreadable / unknown id
        // returns null without leaking existence.
        if (!readableAllocatables(user).contains(allocatableId))
        {
            return null;
        }
        final Allocatable allocatable = findAllocatable(allocatableId);
        if (allocatable == null)
        {
            return null;
        }
        for (AccessLevel level : PROBE_ORDER)
        {
            if (permissionController.hasUserAccessAtLeast(allocatable, user, level))
            {
                return level;
            }
        }
        // Readable per the set but no probed level matched — report READ.
        return AccessLevel.READ;
    }

    /** Drop the cached entry for one user (membership change at the seam). */
    public void invalidate(User user)
    {
        if (user != null && user.getId() != null)
        {
            readableByUser.remove(user.getId());
        }
    }

    /** Drop the cached entry for one user id. */
    public void invalidate(String userId)
    {
        if (userId != null)
        {
            readableByUser.remove(userId);
        }
    }

    /** Drop every cached entry (allocatable-permission or hierarchy change). */
    public void invalidateAll()
    {
        readableByUser.clear();
    }

    private Set<String> computeReadable(User user)
    {
        final Collection<Allocatable> allocatables;
        try
        {
            allocatables = operator.getAllocatables(null);
        }
        catch (RaplaException ex)
        {
            LOGGER.error("Could not enumerate allocatables for permission index", ex);
            return Collections.emptySet();
        }
        final Set<String> readable = new LinkedHashSet<>();
        for (Allocatable allocatable : allocatables)
        {
            if (permissionController.canRead(allocatable, user))
            {
                readable.add(allocatable.getId());
            }
        }
        return Collections.unmodifiableSet(readable);
    }

    private Allocatable findAllocatable(String allocatableId)
    {
        try
        {
            for (Allocatable allocatable : operator.getAllocatables(null))
            {
                if (allocatableId.equals(allocatable.getId()))
                {
                    return allocatable;
                }
            }
        }
        catch (RaplaException ex)
        {
            LOGGER.error("Could not resolve allocatable {} for permission index", allocatableId, ex);
        }
        return null;
    }
}
