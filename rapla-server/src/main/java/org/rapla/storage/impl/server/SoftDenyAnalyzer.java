package org.rapla.storage.impl.server;

import org.rapla.entities.User;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.Permission.AccessLevel;
import org.rapla.entities.domain.PermissionContainer;
import org.rapla.entities.domain.internal.PermissionImpl;
import org.rapla.entities.internal.UserImpl;
import org.rapla.storage.PermissionController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * PRD 090 — throwaway soft-deny detector used by the additive-permission
 * migration one-shot and the admin migration REST endpoint.
 *
 * <p>Compares the <em>superseded</em> precedence resolution against the new
 * additive resolution for every principal that matches a container's rows and
 * reports each principal whose effective access <b>rises</b> under additive
 * ({@code additive > precedence}). That rise is exactly the "soft deny" the flip
 * removes — either a {@code DENIED} row that capped to nothing or a
 * higher-precedence lower-level row that capped to less.
 *
 * <p>The precedence calc lives ONLY here (and is deleted with this class once
 * deployments have migrated — PRD 090 Phase 5); the live resolver is additive.
 *
 * <p>OQ1 (resolved 2026-06-28): rows whose fixed time-window is not in effect at
 * {@code today} (future-start or already expired) are ignored — a cap that never
 * bites today is nothing for an admin to review.
 */
public final class SoftDenyAnalyzer
{
    /** Which syntactic form the removed cap took, for display + resolution. */
    public enum Form { DENIED, SOFT_DENY }

    public enum PrincipalType { USER }

    /** One escalated principal on one container. {@code currentLevel} is the
     * precedence-effective level today; {@code additiveLevel} the (higher) level
     * the principal gains under additive. */
    public record Finding(PrincipalType principalType, String principalId,
                          AccessLevel currentLevel, AccessLevel additiveLevel, Form form) {}

    private SoftDenyAnalyzer() {}

    /**
     * Cheap structural pre-filter — true if the container <em>could</em> carry a
     * soft deny (a higher-precedence row with a strictly lower level than a
     * lower-precedence row, or any {@code DENIED} row). No user iteration; used to
     * skip the per-user scan on the overwhelming majority of clean containers.
     */
    public static boolean mightHaveSoftDeny(PermissionContainer container)
    {
        int worldMax = -1, groupMax = -1, userMin = Integer.MAX_VALUE, groupMin = Integer.MAX_VALUE;
        boolean anyUser = false, anyGroup = false, anyDenied = false;
        for (Permission p : container.getPermissionList())
        {
            int level = p.getAccessLevel().getNumericLevel();
            if (level == Permission.DENIED.getNumericLevel()) anyDenied = true;
            String uid = p.getUserId();
            String gid = ((PermissionImpl) p).getGroupId();
            if (uid == null && gid == null) { worldMax = Math.max(worldMax, level); }
            else if (gid != null) { anyGroup = true; groupMax = Math.max(groupMax, level); groupMin = Math.min(groupMin, level); }
            else { anyUser = true; userMin = Math.min(userMin, level); }
        }
        if (anyDenied) return true;
        // a USER row strictly below some group/world grant it could be dominated by
        if (anyUser && userMin < Math.max(groupMax, worldMax)) return true;
        // a GROUP row strictly below a WORLD grant
        if (anyGroup && groupMin < worldMax) return true;
        return false;
    }

    /**
     * Every principal on {@code container} whose effective access rises under
     * additive resolution. {@code users} is the candidate principal set (the
     * operator's full user list); admins and the owner are skipped (they bypass
     * permissions). Empty list ⇒ no soft deny ⇒ the container is additive-clean.
     */
    public static List<Finding> findEscalations(PermissionContainer container, Collection<? extends User> users, LocalDateTime today)
    {
        List<Permission> rows = new ArrayList<>();
        for (Permission p : container.getPermissionList())
        {
            if (currentlyEffective(p, today)) rows.add(p);
        }
        List<Finding> findings = new ArrayList<>();
        for (User user : users)
        {
            if (user == null || user.isAdmin()) continue;
            if (PermissionController.isOwner(container, user)) continue;
            Collection<String> groups = UserImpl.getGroupsIncludingParents(user);
            AccessLevel prec = precedenceLevel(rows, user, groups);
            AccessLevel add = additiveLevel(rows, user, groups);
            if (add.getNumericLevel() > prec.getNumericLevel())
            {
                Form form = prec == AccessLevel.DENIED ? Form.DENIED : Form.SOFT_DENY;
                findings.add(new Finding(PrincipalType.USER, user.getId(), prec, add, form));
            }
        }
        return findings;
    }

    /** The superseded precedence resolution (USER &gt; GROUP &gt; WORLD), returning the
     * effective level. Kept only in this throwaway class. */
    private static AccessLevel precedenceLevel(List<Permission> rows, User user, Collection<String> groups)
    {
        AccessLevel maxAccessLevel = AccessLevel.DENIED;
        int maxEffectLevel = PermissionImpl.NO_PERMISSION;
        for (Permission p : rows)
        {
            int effect = PermissionContainer.Util.getUserEffect(user, p, groups);
            if (effect >= maxEffectLevel && effect > PermissionImpl.NO_PERMISSION)
            {
                if (maxAccessLevel.excludes(p.getAccessLevel()) || effect > maxEffectLevel)
                {
                    maxAccessLevel = p.getAccessLevel();
                }
                maxEffectLevel = effect;
            }
        }
        return maxAccessLevel;
    }

    /** The live additive resolution: max level over all matching rows. */
    private static AccessLevel additiveLevel(List<Permission> rows, User user, Collection<String> groups)
    {
        AccessLevel max = AccessLevel.DENIED;
        for (Permission p : rows)
        {
            if (PermissionContainer.Util.getUserEffect(user, p, groups) > PermissionImpl.NO_PERMISSION
                    && max.excludes(p.getAccessLevel()))
            {
                max = p.getAccessLevel();
            }
        }
        return max;
    }

    /** OQ1: a row is ignored when its fixed window is entirely in the future or
     * already expired relative to {@code today}. Relative (advance) windows are not
     * a future-start cap and are left in. */
    private static boolean currentlyEffective(Permission p, LocalDateTime today)
    {
        if (today == null) return true;
        LocalDateTime start = p.getStart();
        LocalDateTime end = p.getEnd();
        if (start != null && start.isAfter(today)) return false;
        if (end != null && end.isBefore(today)) return false;
        return true;
    }
}
