package org.rapla.server.spring.graphql;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.domain.Permission.AccessLevel;
import org.rapla.framework.RaplaException;
import org.rapla.storage.PermissionController;
import org.rapla.storage.StorageOperator;

/**
 * PRD 069 — resolves the access-by-target filter fields shared by
 * {@code allocatables(filter:)} and {@code reservations(filter:)}
 * ({@code accessibleByUsername} / {@code accessibleByUserId} /
 * {@code accessibleByGroup} + {@code accessLevel}) into a per-entity predicate.
 *
 * <p>Admin-scoped: resolution enforces that the caller may administer the named
 * user ({@code canAdminUser}) or every named group ({@code canAdminGroup}).
 * An unknown handle and an out-of-scope handle both raise the same
 * {@link ForbiddenException} — the caller cannot probe for existence (§12).
 *
 * <p>The resolved predicate is the *target's* effective access. The resolver
 * still applies the *caller's* own {@code canRead} separately, so the result is
 * the intersection (a resource the target can reach but the caller cannot read
 * is dropped).
 */
final class AccessTargetFilter
{
    private final User targetUser;            // set iff a user selector was used
    private final List<Category> targetGroups; // set iff the group selector was used
    private final AccessLevel level;
    private final PermissionController pc;

    private AccessTargetFilter(User targetUser, List<Category> targetGroups, AccessLevel level,
            PermissionController pc)
    {
        this.targetUser = targetUser;
        this.targetGroups = targetGroups;
        this.level = level;
        this.pc = pc;
    }

    /**
     * Build the filter, or return {@code null} when no access selector is set.
     *
     * @throws IllegalArgumentException if more than one selector is set
     * @throws ForbiddenException       if a handle is unknown or out of the caller's admin scope
     */
    static AccessTargetFilter create(String username, String userId, List<String> groupPaths,
            AccessLevel accessLevel, User caller, StorageOperator operator, PermissionController pc)
            throws RaplaException
    {
        boolean hasUsername = username != null && !username.isBlank();
        boolean hasUserId = userId != null && !userId.isBlank();
        boolean hasGroup = groupPaths != null && !groupPaths.isEmpty();

        int selectors = (hasUsername ? 1 : 0) + (hasUserId ? 1 : 0) + (hasGroup ? 1 : 0);
        if (selectors == 0)
        {
            return null;
        }
        if (selectors > 1)
        {
            throw new IllegalArgumentException(
                    "Set exactly one of accessibleByUsername, accessibleByUserId, accessibleByGroup");
        }
        AccessLevel level = accessLevel != null ? accessLevel : AccessLevel.READ;

        if (hasGroup)
        {
            List<Category> groups = resolveGroups(groupPaths, operator);
            // Global admins administer every group; canAdminGroup only covers
            // can-admin-parent group delegation, so check isAdmin first.
            if (!caller.isAdmin())
            {
                Collection<Category> adminGroups = PermissionController.getGroupsToAdmin(caller);
                for (Category g : groups)
                {
                    if (!PermissionController.canAdminGroup(adminGroups, g))
                    {
                        throw new ForbiddenException();
                    }
                }
            }
            return new AccessTargetFilter(null, groups, level, pc);
        }

        User target = hasUsername ? operator.getUser(username)
                                  : operator.tryResolve(userId, User.class);
        // unknown handle == out-of-scope: identical response, no existence leak.
        if (target == null || !PermissionController.canAdminUser(caller, target))
        {
            throw new ForbiddenException();
        }
        return new AccessTargetFilter(target, null, level, pc);
    }

    /** True if the target holds at least the requested access level on {@code entity}. */
    boolean test(Entity<?> entity)
    {
        if (targetUser != null)
        {
            return pc.hasUserAccessAtLeast(entity, targetUser, level);
        }
        return pc.hasGroupAccessAtLeast(entity, targetGroups, level);
    }

    private static List<Category> resolveGroups(List<String> paths, StorageOperator operator)
    {
        Category superCategory = operator.getSuperCategory();
        Category userGroups = superCategory != null
                ? superCategory.getCategory(CategoryKindClassifier.USER_GROUPS_KEY) : null;
        if (userGroups == null)
        {
            throw new ForbiddenException();
        }
        List<Category> result = new ArrayList<>();
        for (String path : paths)
        {
            if (path == null || path.isBlank())
            {
                throw new ForbiddenException();
            }
            Category cur = userGroups;
            for (String segment : path.split("/"))
            {
                if (segment.isBlank())
                {
                    continue;
                }
                cur = cur.getCategory(segment);
                if (cur == null)
                {
                    throw new ForbiddenException();
                }
            }
            if (cur == userGroups)
            {
                throw new ForbiddenException();
            }
            result.add(cur);
        }
        return result;
    }
}
