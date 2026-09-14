package org.rapla.server.spring.graphql;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.Permission.AccessLevel;
import org.rapla.entities.domain.PermissionContainer;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.server.spring.graphql.ReservationMutationController.ReservationMutationException;

/**
 * PRD 113 § 5c — the permission-input → stored-row mapping shared by every save input that carries a
 * {@code permissions} list. Validates only what the schema cannot (§ 2a); the gate (canAdmin when the list
 * changes) stays in {@code SecurityManager.checkModifyPermissions}, reached through {@code WriteGate}.
 */
final class PermissionInputMapper
{
    enum Kind { RESOURCE, SIMPLE, TYPE_ACCESS }

    /** OQ 7 — the levels a window is evaluated for. */
    private static final Set<AccessLevel> WINDOWED =
            EnumSet.of(AccessLevel.REQUEST, AccessLevel.ALLOCATE, AccessLevel.ALLOCATE_CONFLICTS, AccessLevel.EDIT);

    private PermissionInputMapper()
    {
    }

    /** {@code null} = untouched; otherwise the whole list is replaced ({@code []} empties it). */
    static void apply(PermissionContainer container, List<Map<String, Object>> input, Kind kind, String path,
            EntityResolver resolver)
    {
        if (input == null) return;
        PermissionContainer.Util.replace(container, toRows(container, input, kind, path, resolver));
    }

    /**
     * PRD 113 § 1b / § 5d — the one stored DynamicType list behind the two API lists. Each non-null input replaces
     * only its own subset (READ_TYPE | CREATE rows vs every other row); a null input keeps the stored subset.
     * The stored list is rewritten once, type rows first. Both inputs are mapped before anything is replaced,
     * so a rejected row leaves the list untouched.
     */
    static void replaceTypeLists(PermissionContainer container, List<Map<String, Object>> typeAccess,
            List<Map<String, Object>> instanceDefaults, Kind instanceKind, String typeAccessPath, String instancePath,
            EntityResolver resolver)
    {
        if (typeAccess == null && instanceDefaults == null) return;
        List<Permission> typeRows = new ArrayList<>();
        List<Permission> instanceRows = new ArrayList<>();
        for (Permission p : container.getPermissionList())
        {
            (PermissionDto.isTypeAccess(p) ? typeRows : instanceRows).add(p);
        }
        if (typeAccess != null)
        {
            typeRows = toRows(container, typeAccess, Kind.TYPE_ACCESS, typeAccessPath, resolver);
        }
        if (instanceDefaults != null)
        {
            instanceRows = toRows(container, instanceDefaults, instanceKind, instancePath, resolver);
        }
        List<Permission> merged = new ArrayList<>(typeRows);
        merged.addAll(instanceRows);
        PermissionContainer.Util.replace(container, merged);
    }

    @SuppressWarnings("unchecked")
    static List<Permission> toRows(PermissionContainer container, List<Map<String, Object>> input, Kind kind,
            String path, EntityResolver resolver)
    {
        List<Permission> rows = new ArrayList<>(input.size());
        for (int i = 0; i < input.size(); i++)
        {
            Map<String, Object> in = input.get(i);
            String at = path + "[" + i + "]";
            Permission p = container.newPermission();
            p.setAccessLevel(AccessLevel.valueOf((String) in.get("level")));
            setPrincipal(p, (Map<String, Object>) in.get("principal"), at + ".principal", resolver);
            if (kind == Kind.RESOURCE)
            {
                setWindow(p, in, at);
            }
            rows.add(p);
        }
        return rows;
    }

    private static void setPrincipal(Permission p, Map<String, Object> principal, String at, EntityResolver resolver)
    {
        if (Boolean.FALSE.equals(principal.get("everyone")))
        {
            throw new ReservationMutationException("INVALID_VALUE", at + ".everyone", "everyone must be true when set");
        }
        String userId = (String) principal.get("userId");
        String groupId = (String) principal.get("groupId");
        if (userId != null)
        {
            User user = tryResolve(resolver, userId, User.class);
            if (user == null)
            {
                throw new ReservationMutationException("REFERENCE_NOT_FOUND", at + ".userId", "User not found");
            }
            p.setUser(user);
        }
        else if (groupId != null)
        {
            // §12 — a category outside the user-groups subtree answers exactly like an unknown id.
            Category group = tryResolve(resolver, groupId, Category.class);
            if (group == null || !isUserGroup(group))
            {
                throw new ReservationMutationException("REFERENCE_NOT_FOUND", at + ".groupId", "Group not found");
            }
            p.setGroup(group);
        }
    }

    private static void setWindow(Permission p, Map<String, Object> in, String at)
    {
        LocalDateTime start = (LocalDateTime) in.get("start");
        LocalDateTime end = (LocalDateTime) in.get("end");
        Integer minAdvance = (Integer) in.get("minAdvance");
        Integer maxAdvance = (Integer) in.get("maxAdvance");
        boolean absolute = start != null || end != null;
        boolean relative = minAdvance != null || maxAdvance != null;
        if (!absolute && !relative) return;
        if (!WINDOWED.contains(p.getAccessLevel()))
        {
            throw new ReservationMutationException("INVALID_VALUE", at + ".level",
                    "Time windows are only allowed on REQUEST, ALLOCATE, ALLOCATE_CONFLICTS and EDIT rows");
        }
        if (absolute && relative)
        {
            throw new ReservationMutationException("INVALID_VALUE", at + ".start",
                    "An absolute window (start/end) and a relative window (minAdvance/maxAdvance) are exclusive");
        }
        if (start != null && end != null && start.isAfter(end))
        {
            throw new ReservationMutationException("INVALID_VALUE", at + ".end", "end must not be before start");
        }
        if (minAdvance != null && minAdvance < 0)
        {
            throw new ReservationMutationException("INVALID_VALUE", at + ".minAdvance", "minAdvance must not be negative");
        }
        if (maxAdvance != null && maxAdvance < 0)
        {
            throw new ReservationMutationException("INVALID_VALUE", at + ".maxAdvance", "maxAdvance must not be negative");
        }
        if (minAdvance != null && maxAdvance != null && minAdvance > maxAdvance)
        {
            throw new ReservationMutationException("INVALID_VALUE", at + ".maxAdvance",
                    "maxAdvance must not be below minAdvance");
        }
        p.setStart(start);
        p.setEnd(end);
        p.setMinAdvance(minAdvance);
        p.setMaxAdvance(maxAdvance);
    }

    private static <T extends Entity> T tryResolve(EntityResolver resolver, String id, Class<T> type)
    {
        try
        {
            return resolver.tryResolve(id, type);
        }
        catch (RuntimeException e)
        {
            return null;
        }
    }

    /** A group is a category below the {@code user-groups} root, which itself sits directly under the super category. */
    static boolean isUserGroup(Category category)
    {
        for (Category ancestor = category.getParent(); ancestor != null; ancestor = ancestor.getParent())
        {
            if (Permission.GROUP_CATEGORY_KEY.equals(ancestor.getKey()))
            {
                return ancestor.getParent() != null && ancestor.getParent().getParent() == null;
            }
        }
        return false;
    }
}
