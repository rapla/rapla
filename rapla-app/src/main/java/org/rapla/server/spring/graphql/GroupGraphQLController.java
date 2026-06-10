package org.rapla.server.spring.graphql;

import java.util.ArrayList;
import java.util.List;
import org.rapla.entities.Category;
import org.rapla.entities.User;
import org.rapla.framework.RaplaException;
import org.rapla.server.spring.graphql.HelloGraphQLController.UserDto;
import org.rapla.storage.StorageOperator;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Controller;

/**
 * PRD 035 §5c — permission group resolvers. Surface the user-groups category
 * subtree as a first-class {@code type Group} so the API contract doesn't
 * leak the storage representation.
 *
 * <p>Internally each Group is backed by a {@link Category} under the
 * {@code user-groups} root. The DTO carries only the fields the API exposes
 * (id, key, name) — hierarchy support deferred to a separate PRD.
 *
 * <p>§12: anonymous callers see empty lists / null lookups. Authenticated
 * callers see all groups (rapla's permission model treats Categories as
 * global metadata; there's no per-user category read permission today).
 * If/when group-visibility filtering lands, gate here.
 */
@Controller
public class GroupGraphQLController
{
    private final StorageOperator operator;
    private final org.rapla.server.spring.JwtUserResolver jwtUserResolver;

    public GroupGraphQLController(StorageOperator operator,
            org.rapla.server.spring.JwtUserResolver jwtUserResolver)
    {
        this.operator = operator;
        this.jwtUserResolver = jwtUserResolver;
    }

    /** All permission groups (children of the user-groups subtree). */
    @QueryMapping
    public List<GroupDto> groups()
    {
        UnauthenticatedException.require(resolveCaller());
        Category userGroupsRoot = userGroupsRoot();
        if (userGroupsRoot == null) return List.of();
        return collectGroups(userGroupsRoot);
    }

    /** Single group by id, or null if not found / anonymous. */
    @QueryMapping
    public GroupDto group(@Argument("id") String id)
    {
        if (id == null || id.isBlank()) return null;
        UnauthenticatedException.require(resolveCaller());
        Category userGroupsRoot = userGroupsRoot();
        if (userGroupsRoot == null) return null;
        // Walk the subtree looking for a Category with matching id.
        return findById(userGroupsRoot, id);
    }

    // === User.groups ===

    /**
     * The groups Alice (the source User) is a member of. Source is a
     * {@link UserDto} carrying the username; we re-look up the User entity
     * to read its group list. Per-row Spring @SchemaMapping cost is
     * acceptable for the User type (low-traffic — handful of users per
     * typical query) — for hot-path resolvers see {@code StructuralTypeFetchers}.
     */
    @SchemaMapping(typeName = "User", field = "groups")
    public List<GroupDto> userGroups(UserDto userDto) throws RaplaException
    {
        User user = operator.getUser(userDto.username());
        if (user == null) return List.of();
        Category[] groups = user.getGroups();
        if (groups == null || groups.length == 0) return List.of();
        List<GroupDto> out = new ArrayList<>(groups.length);
        for (Category g : groups)
        {
            if (g != null) out.add(GroupDto.from(g));
        }
        return out;
    }

    /** Walk the user-groups subtree depth-first, collect every node as a Group. */
    private static List<GroupDto> collectGroups(Category root)
    {
        List<GroupDto> out = new ArrayList<>();
        Category[] direct = root.getCategories();
        if (direct == null) return out;
        for (Category c : direct)
        {
            collectRecursive(c, out);
        }
        return out;
    }

    private static void collectRecursive(Category c, List<GroupDto> out)
    {
        if (c == null) return;
        out.add(GroupDto.from(c));
        Category[] children = c.getCategories();
        if (children == null) return;
        for (Category child : children)
        {
            collectRecursive(child, out);
        }
    }

    private static GroupDto findById(Category subtreeRoot, String id)
    {
        Category[] children = subtreeRoot.getCategories();
        if (children == null) return null;
        for (Category c : children)
        {
            if (c == null) continue;
            if (id.equals(c.getId())) return GroupDto.from(c);
            GroupDto deep = findById(c, id);
            if (deep != null) return deep;
        }
        return null;
    }

    private Category userGroupsRoot()
    {
        Category superCat = operator.getSuperCategory();
        if (superCat == null) return null;
        return superCat.getCategory(CategoryKindClassifier.USER_GROUPS_KEY);
    }

    private User resolveCaller()
    {
        return jwtUserResolver.resolveCurrentUserOrNull();
    }

    /**
     * Mirror of the {@code Group} GraphQL type. Currently flat — hierarchical
     * extensions (parent / children) deferred to a separate PRD per §5c.
     */
    public record GroupDto(String id, String key, String name)
    {
        static GroupDto from(Category c)
        {
            return new GroupDto(c.getId(), c.getKey(), c.getName(java.util.Locale.getDefault()));
        }
    }
}
