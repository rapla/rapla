package org.rapla.server.spring.graphql;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.domain.Permission;
import org.rapla.storage.PermissionController;

/**
 * PRD 113 § 5b — mirror of the {@code Permission} GraphQL type: the one row projection shared by every
 * permission container (Allocatable, Reservation, EventTemplate, Period, DynamicType).
 */
public record PermissionDto(Principal principal, String level, LocalDateTime start, LocalDateTime end,
        Integer minAdvance, Integer maxAdvance)
{
    /** Mirror of {@code PermissionPrincipal}; a stored row with neither user nor group reads as everyone. */
    public record Principal(PrincipalUser user, PrincipalGroup group, boolean everyone) {}

    /** OQ 3 — id + name only; never the User type (no email / authSource / groups outside the admin scope). */
    public record PrincipalUser(String id, String username, String name) {}

    public record PrincipalGroup(String id, String name) {}

    static PermissionDto from(Permission p)
    {
        User user = p.getUser();
        Category group = p.getGroup();
        Principal principal = new Principal(
                user == null ? null : new PrincipalUser(user.getId(), user.getUsername(), user.getName()),
                group == null ? null : new PrincipalGroup(group.getId(), group.getName(java.util.Locale.getDefault())),
                user == null && group == null);
        return new PermissionDto(principal, p.getAccessLevel().name(), p.getStart(), p.getEnd(),
                p.getMinAdvance(), p.getMaxAdvance());
    }

    static List<PermissionDto> of(Collection<Permission> rows, Predicate<Permission> keep)
    {
        List<PermissionDto> out = new ArrayList<>();
        for (Permission p : rows)
        {
            if (keep.test(p)) out.add(from(p));
        }
        return out;
    }

    /** DynamicType projection (§ 1b): READ_TYPE and CREATE rows are type access, every other row an instance default. */
    static boolean isTypeAccess(Permission p)
    {
        return p.getAccessLevel() == Permission.AccessLevel.READ_TYPE || p.getAccessLevel() == Permission.AccessLevel.CREATE;
    }

    static boolean canAdmin(Entity<?> entity, User caller, PermissionController pc)
    {
        if (caller == null) return false;
        if (caller.isAdmin()) return true;
        return pc != null && pc.canAdmin(entity, caller);
    }

    /** §12 — the rows iff the caller may admin the entity, else null (never [] for a non-admin, § 5b rule 5). */
    static List<PermissionDto> visible(Entity<?> entity, Collection<Permission> rows, User caller, PermissionController pc)
    {
        return canAdmin(entity, caller, pc) ? of(rows, p -> true) : null;
    }
}
