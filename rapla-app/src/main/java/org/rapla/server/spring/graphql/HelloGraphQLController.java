package org.rapla.server.spring.graphql;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.rapla.entities.Category;
import org.rapla.entities.User;
import org.rapla.entities.domain.Period;
import org.rapla.facade.PeriodModel;
import org.rapla.framework.RaplaException;
import org.rapla.storage.PermissionController;
import org.rapla.storage.StorageOperator;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Controller;

/**
 * PRD 035 testbed — GraphQL controller. Resolves through {@link StorageOperator}
 * (NOT {@link org.rapla.facade.RaplaFacade} per the 2026-05-25 decision: GraphQL
 * resolvers are the §12 boundary and call the same low-level operator + permission
 * controller pattern that {@code RaplaResourcesController.list()} uses inline —
 * no separate service-layer bean tier.
 *
 * <p>The three legacy "external REST CRUD" services
 * ({@code RaplaResourcesService}, {@code RaplaEventsService},
 * {@code RaplaDynamicTypesService}) are replaced by this GraphQL endpoint and
 * are being deleted in the same change.
 *
 * <p>Pattern notes:
 * <ul>
 *   <li>{@link StorageOperator} is the low-level data substrate. {@link PermissionController}
 *       (accessed via {@code operator.getPermissionController()}) provides per-entity
 *       §12 checks. Inline filter pattern matches {@code RaplaResourcesController.list()}
 *       and {@code UsersController.list()}.</li>
 *   <li>Category entities are returned directly to GraphQL; {@link SchemaMapping}
 *       methods cover the fields whose getters don't match (locale-aware name + path,
 *       array → list children).</li>
 *   <li>Periods/categories don't currently have per-user §12 rules in rapla — TODO
 *       markers added where future visibility filtering would land.</li>
 * </ul>
 */
@Controller
public class HelloGraphQLController
{
    private final StorageOperator operator;

    public HelloGraphQLController(StorageOperator operator)
    {
        this.operator = operator;
    }

    // --- trivial probes --------------------------------------------------------

    @QueryMapping
    public String hello(@Argument("name") String name)
    {
        return "Hello, " + name + "!";
    }

    @QueryMapping
    public OffsetDateTime serverTime()
    {
        // DateTime scalar (ExtendedScalars.DateTime) — OffsetDateTime, ISO_OFFSET_DATE_TIME.
        return Instant.now().atOffset(ZoneOffset.UTC);
    }

    @QueryMapping
    public String version()
    {
        return "2.1-SNAPSHOT";
    }

    // --- auth / user lookup ---------------------------------------------------

    /**
     * The authenticated user, or null if the request is anonymous / no JWT.
     * Lookup chain: Spring SecurityContext → JWT {@code preferred_username}
     * claim → {@link StorageOperator#getUser(String)}.
     */
    @QueryMapping
    public UserDto me()
    {
        User caller = resolveCaller();
        return caller == null ? null : UserDto.from(caller);
    }

    /**
     * Users visible to the caller, narrowed by an optional {@link UserFilter}.
     * §12 always applies first:
     * <ul>
     *   <li>Anonymous → empty list.</li>
     *   <li>Self always visible.</li>
     *   <li>Other users only if {@link PermissionController#canAdminUser(User, User)}.</li>
     * </ul>
     * The filter then narrows the §12-visible subset further (never the raw
     * user table — so a filter can never reveal a user the caller couldn't
     * already see).
     */
    @QueryMapping
    public List<UserDto> users(@Argument("filter") UserFilter filter) throws RaplaException
    {
        User caller = resolveCaller();
        if (caller == null) return List.of();
        Collection<User> all = operator.getUsers();
        List<UserDto> visible = new ArrayList<>();
        for (User candidate : all)
        {
            if (candidate == null) continue;
            if (!isSelf(caller, candidate) && !PermissionController.canAdminUser(caller, candidate)) continue;
            if (!matches(candidate, filter)) continue;
            visible.add(UserDto.from(candidate));
        }
        return visible;
    }

    /** AND-combination of all non-null predicates in the filter. */
    private static boolean matches(User u, UserFilter f)
    {
        if (f == null) return true;
        String src = u.getAuthenticationSource();
        boolean srcNonBlank = src != null && !src.isBlank();
        if (f.hasAuthSource() != null && srcNonBlank != f.hasAuthSource()) return false;
        if (f.authSourceEq() != null && !f.authSourceEq().isBlank()
                && !f.authSourceEq().equals(src)) return false;
        if (f.isAdmin() != null && u.isAdmin() != f.isAdmin()) return false;
        if (f.usernameContains() != null && !f.usernameContains().isBlank())
        {
            String haystack = u.getUsername() == null ? "" : u.getUsername().toLowerCase();
            if (!haystack.contains(f.usernameContains().toLowerCase())) return false;
        }
        return true;
    }

    /**
     * User by username. §12: returns null if the caller can't see the target.
     * Caller always sees themselves; otherwise canAdminUser gate (existence
     * not leaked to other non-admin callers).
     */
    @QueryMapping
    public UserDto user(@Argument("username") String username) throws RaplaException
    {
        if (username == null || username.isBlank()) return null;
        User caller = resolveCaller();
        if (caller == null) return null;
        User target = operator.getUser(username);
        if (target == null) return null;
        if (!isSelf(caller, target) && !PermissionController.canAdminUser(caller, target)) return null;
        return UserDto.from(target);
    }

    private static boolean isSelf(User caller, User candidate)
    {
        return caller != null && candidate != null
                && caller.getId() != null
                && caller.getId().equals(candidate.getId());
    }

    // --- periods --------------------------------------------------------------

    /**
     * All periods. TODO §12: no per-user visibility rules on periods today —
     * if/when they're added, filter here. The set is small (~dozens) so no
     * pagination yet.
     */
    @QueryMapping
    public List<PeriodDto> periods() throws RaplaException
    {
        PeriodModel model = operator.getPeriodModelFor(null);
        if (model == null) return List.of();
        return Arrays.stream(model.getAllPeriods())
                .filter(Objects::nonNull)
                .map(PeriodDto::from)
                .toList();
    }

    // --- categories -----------------------------------------------------------

    /**
     * Category by slash-separated key path (e.g. {@code "user-groups/staff"}).
     * Resolved by walking from the super-category through each path segment.
     * Returns null on any miss or on the empty/super path (super is not exposed).
     * TODO §12: no per-user visibility on categories yet.
     */
    @QueryMapping
    public Category category(@Argument("path") String path)
    {
        if (path == null || path.isBlank()) return null;
        Category cur = operator.getSuperCategory();
        if (cur == null) return null;
        for (String segment : path.split("/"))
        {
            if (segment.isBlank()) continue;
            cur = cur.getCategory(segment);
            if (cur == null) return null;
        }
        if (cur == operator.getSuperCategory()) return null;
        // PRD 035 §5a: user-groups subtree is filtered out — surfaces only via Group.
        if (CategoryKindClassifier.isUnderUserGroups(cur, operator.getSuperCategory())) return null;
        return cur;
    }

    /**
     * Immediate children of a root category — super by default, or the named
     * subtree if {@code rootKey} is given. Empty list if the named root doesn't
     * exist OR if the rootKey targets the user-groups subtree (PRD 035 §5a —
     * permission groups surface via {@code type Group}, not Category).
     */
    @QueryMapping
    public List<Category> categories(@Argument("rootKey") String rootKey)
    {
        Category root = operator.getSuperCategory();
        if (root == null) return List.of();
        if (rootKey != null && !rootKey.isBlank())
        {
            // PRD 035 §5a — user-groups subtree is not addressable via Category API.
            if (CategoryKindClassifier.USER_GROUPS_KEY.equals(rootKey)) return List.of();
            Category sub = root.getCategory(rootKey);
            if (sub == null) return List.of();
            root = sub;
        }
        Category[] children = root.getCategories();
        if (children == null) return List.of();
        // When rootKey is null we return super's immediate children; filter
        // user-groups out of that top-level list so it never surfaces.
        List<Category> out = new ArrayList<>(children.length);
        for (Category c : children)
        {
            if (c == null) continue;
            if (CategoryKindClassifier.USER_GROUPS_KEY.equals(c.getKey())) continue;
            out.add(c);
        }
        return out;
    }

    // Category.name/path/parent/children resolvers were @SchemaMapping methods
    // here; they're now LightDataFetcher singletons in StructuralTypeFetchers
    // (wired at schema build) to bypass Spring's per-dispatch HandlerMethod
    // construction. ~80-120k Category dispatches per 42k-Person query saw
    // ~5-10 µs / call instead of ~25-30 µs after the conversion.

    // --- helpers --------------------------------------------------------------

    /**
     * Returns the rapla {@link User} behind the current request, or null.
     * Resolution order:
     *   1. {@code preferred_username} claim on a Jwt principal (production OAuth path)
     *   2. {@link Authentication#getName()} (fallback — works for the test path
     *      and for non-JWT authenticated calls)
     * Anonymous / unknown / blank → null.
     */
    private User resolveCaller()
    {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        String username = null;
        if (auth.getPrincipal() instanceof Jwt jwt)
        {
            username = jwt.getClaimAsString("preferred_username");
        }
        if (username == null || username.isBlank())
        {
            username = auth.getName();
        }
        if (username == null || username.isBlank() || "anonymousUser".equals(username)) return null;
        try
        {
            return operator.getUser(username);
        }
        catch (RaplaException e)
        {
            return null;
        }
    }

    // --- DTOs -----------------------------------------------------------------

    /**
     * Mirror of the {@code UserFilter} GraphQL input. Spring for GraphQL binds
     * the input map into this record by field name.
     */
    public record UserFilter(
            Boolean hasAuthSource,
            String  authSourceEq,
            Boolean isAdmin,
            String  usernameContains) {}

    /** Mirror of the {@code User} GraphQL type. The §12 output boundary for users. */
    public record UserDto(
            String id,
            String username,
            String name,
            String email,
            boolean isAdmin,
            String authSource)
    {
        static UserDto from(User u)
        {
            return new UserDto(
                    u.getId(),
                    u.getUsername(),
                    u.getName(),
                    u.getEmail(),
                    u.isAdmin(),
                    u.getAuthenticationSource());
        }
    }

    /** Mirror of the {@code Period} GraphQL type. rapla Periods have no synthetic id. */
    public record PeriodDto(String name, LocalDateTime start, LocalDateTime end)
    {
        static PeriodDto from(Period p)
        {
            return new PeriodDto(p.getName(), p.getStart(), p.getEnd());
        }
    }
}
