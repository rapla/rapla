package org.rapla.server.spring.graphql;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rapla.entities.Entity;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.Permission.AccessLevel;
import org.rapla.entities.domain.internal.AllocatableImpl;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.entities.internal.CategoryImpl;
import org.rapla.entities.internal.UserImpl;
import org.rapla.entities.storage.EntityResolver;
import org.rapla.server.spring.graphql.PermissionInputMapper.Kind;
import org.rapla.server.spring.graphql.ReservationMutationController.ReservationMutationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 113 § 5c (W1) — tier 1 for {@link PermissionInputMapper}: the eight server rules, null / [] semantics,
 * and the round trip through the R1 projection. Plain JUnit; resolution runs against an in-memory
 * {@link EntityResolver} over real entity implementations (no rapla-type mocks, AGENTS.md § 13).
 */
class PermissionInputMapperTest
{
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 13, 12, 0);
    private static final String PATH = "input.permissions";

    private final Map<String, Entity> store = new HashMap<>();
    private final EntityResolver resolver = new EntityResolver()
    {
        @Override
        public <T extends Entity> T tryResolve(String id, Class<T> entityClass)
        {
            Entity e = store.get(id);
            return entityClass.isInstance(e) ? entityClass.cast(e) : null;
        }

        @Override
        public DynamicType getDynamicType(String key)
        {
            return null;
        }
    };

    private UserImpl lenny;
    private CategoryImpl myGroup;
    private CategoryImpl outsideUserGroups;
    private AllocatableImpl container;

    @BeforeEach
    void setUp()
    {
        CategoryImpl root = category("super");
        CategoryImpl userGroups = category(Permission.GROUP_CATEGORY_KEY);
        myGroup = category("my-group");
        outsideUserGroups = category("room-categories");
        root.addCategory(userGroups);
        userGroups.addCategory(myGroup);
        root.addCategory(outsideUserGroups);

        lenny = new UserImpl(NOW, NOW);
        lenny.setId(UUID.randomUUID().toString());
        lenny.setUsername("lenny");
        lenny.setResolver(resolver);
        store.put(lenny.getId(), lenny);

        container = new AllocatableImpl(NOW, NOW);
        container.setId(UUID.randomUUID().toString());
        container.setResolver(resolver);
    }

    private CategoryImpl category(String key)
    {
        CategoryImpl c = new CategoryImpl(NOW, NOW);
        c.setId(UUID.randomUUID().toString());
        c.setKey(key);
        c.setResolver(resolver);
        store.put(c.getId(), c);
        return c;
    }

    private static Map<String, Object> principal(String key, Object value)
    {
        Map<String, Object> p = new HashMap<>();
        p.put(key, value);
        return p;
    }

    private static Map<String, Object> row(Map<String, Object> principal, String level, Object... window)
    {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("principal", principal);
        r.put("level", level);
        for (int i = 0; i < window.length; i += 2)
        {
            r.put((String) window[i], window[i + 1]);
        }
        return r;
    }

    private ReservationMutationException rejected(Map<String, Object> row)
    {
        return assertThrows(ReservationMutationException.class,
                () -> PermissionInputMapper.toRows(container, List.of(row), Kind.RESOURCE, PATH, resolver));
    }

    private Permission single(Map<String, Object> row)
    {
        List<Permission> rows = PermissionInputMapper.toRows(container, List.of(row), Kind.RESOURCE, PATH, resolver);
        assertEquals(1, rows.size());
        return rows.get(0);
    }

    // === rule 1 ==============================================================

    @Test
    void everyoneFalseRejected()
    {
        ReservationMutationException e = rejected(row(principal("everyone", false), "READ"));
        assertEquals("INVALID_VALUE", e.code());
        assertEquals(PATH + "[0].principal.everyone", e.path());
    }

    // === rule 2 ==============================================================

    @Test
    void unknownUserRejected()
    {
        ReservationMutationException e = rejected(row(principal("userId", UUID.randomUUID().toString()), "READ"));
        assertEquals("REFERENCE_NOT_FOUND", e.code());
        assertEquals(PATH + "[0].principal.userId", e.path());
    }

    // === rule 3 ==============================================================

    @Test
    void groupOutsideUserGroupsAnswersLikeUnknown()
    {
        ReservationMutationException unknown = rejected(row(principal("groupId", UUID.randomUUID().toString()), "READ"));
        ReservationMutationException outside = rejected(row(principal("groupId", outsideUserGroups.getId()), "READ"));
        assertEquals("REFERENCE_NOT_FOUND", unknown.code());
        assertEquals(PATH + "[0].principal.groupId", unknown.path());
        assertEquals(unknown.code(), outside.code());
        assertEquals(unknown.path(), outside.path());
        assertEquals(unknown.getMessage(), outside.getMessage(), "§12 — no existence signal in the message");
    }

    // === rule 4 ==============================================================

    @Test
    void windowOnReadRowRejected()
    {
        ReservationMutationException e = rejected(row(principal("everyone", true), "READ", "start", NOW));
        assertEquals("INVALID_VALUE", e.code());
        assertEquals(PATH + "[0].level", e.path());
    }

    @Test
    void windowOnRequestRowAccepted()
    {
        Permission p = single(row(principal("everyone", true), "REQUEST", "minAdvance", 2, "maxAdvance", 30));
        assertEquals(AccessLevel.REQUEST, p.getAccessLevel());
        assertEquals(2, p.getMinAdvance());
        assertEquals(30, p.getMaxAdvance());
    }

    // === rule 5 ==============================================================

    @Test
    void absoluteAndRelativeRejected()
    {
        ReservationMutationException e = rejected(row(principal("everyone", true), "ALLOCATE", "start", NOW, "minAdvance", 1));
        assertEquals("INVALID_VALUE", e.code());
        assertEquals(PATH + "[0].start", e.path());
    }

    // === rule 6 ==============================================================

    @Test
    void startAfterEndRejected()
    {
        ReservationMutationException e = rejected(row(principal("everyone", true), "EDIT", "start", NOW, "end", NOW.minusDays(1)));
        assertEquals("INVALID_VALUE", e.code());
        assertEquals(PATH + "[0].end", e.path());
    }

    @Test
    void negativeAdvanceRejected()
    {
        ReservationMutationException e = rejected(row(principal("everyone", true), "ALLOCATE", "minAdvance", -1));
        assertEquals("INVALID_VALUE", e.code());
        assertEquals(PATH + "[0].minAdvance", e.path());
    }

    @Test
    void minAdvanceAboveMaxAdvanceRejected()
    {
        ReservationMutationException e = rejected(row(principal("everyone", true), "ALLOCATE", "minAdvance", 10, "maxAdvance", 5));
        assertEquals("INVALID_VALUE", e.code());
        assertEquals(PATH + "[0].maxAdvance", e.path());
    }

    // === rule 7 ==============================================================

    @Test
    void principalMapsToExclusiveSetters()
    {
        Permission user = single(row(principal("userId", lenny.getId()), "READ"));
        assertSame(lenny, user.getUser());
        assertNull(user.getGroup());

        Permission group = single(row(principal("groupId", myGroup.getId()), "EDIT"));
        assertSame(myGroup, group.getGroup());
        assertNull(group.getUser());

        Permission everyone = single(row(principal("everyone", true), "READ"));
        assertNull(everyone.getUser());
        assertNull(everyone.getGroup());

        LocalDateTime end = NOW.plusDays(7);
        Permission window = single(row(principal("everyone", true), "ALLOCATE", "start", NOW, "end", end));
        assertEquals(NOW, window.getStart());
        assertEquals(end, window.getEnd());
    }

    // === rule 8 ==============================================================

    @Test
    void duplicateRowsStoredAsSent()
    {
        Map<String, Object> r = row(principal("groupId", myGroup.getId()), "READ");
        assertEquals(2, PermissionInputMapper.toRows(container, List.of(r, r), Kind.RESOURCE, PATH, resolver).size());
    }

    // === null / [] ============================================================

    @Test
    void applyNullLeavesRowsUntouched()
    {
        PermissionInputMapper.apply(container, List.of(row(principal("everyone", true), "READ")), Kind.RESOURCE, PATH, resolver);
        PermissionInputMapper.apply(container, null, Kind.RESOURCE, PATH, resolver);
        assertEquals(1, container.getPermissionList().size());
    }

    @Test
    void applyEmptyListEmptiesRows()
    {
        PermissionInputMapper.apply(container, List.of(row(principal("everyone", true), "READ")), Kind.RESOURCE, PATH, resolver);
        PermissionInputMapper.apply(container, List.of(), Kind.RESOURCE, PATH, resolver);
        assertTrue(container.getPermissionList().isEmpty());
    }

    @Test
    void applyReplacesWholeList()
    {
        PermissionInputMapper.apply(container, List.of(row(principal("everyone", true), "READ")), Kind.RESOURCE, PATH, resolver);
        PermissionInputMapper.apply(container, List.of(row(principal("userId", lenny.getId()), "ADMIN"),
                row(principal("groupId", myGroup.getId()), "EDIT")), Kind.RESOURCE, PATH, resolver);
        List<AccessLevel> levels = new ArrayList<>();
        container.getPermissionList().forEach(p -> levels.add(p.getAccessLevel()));
        assertEquals(List.of(AccessLevel.ADMIN, AccessLevel.EDIT), levels);
    }

    // === round trip through the R1 projection ================================

    @Test
    void validListRoundTripsThroughProjection()
    {
        List<Map<String, Object>> input = List.of(
                row(principal("userId", lenny.getId()), "ADMIN"),
                row(principal("groupId", myGroup.getId()), "ALLOCATE", "start", NOW, "end", NOW.plusDays(1)),
                row(principal("everyone", true), "REQUEST", "minAdvance", 1, "maxAdvance", 14));
        List<PermissionDto> read = PermissionDto.of(
                PermissionInputMapper.toRows(container, input, Kind.RESOURCE, PATH, resolver), p -> true);

        assertEquals(3, read.size());
        assertEquals("ADMIN", read.get(0).level());
        assertEquals(lenny.getId(), read.get(0).principal().user().id());
        assertEquals("ALLOCATE", read.get(1).level());
        assertEquals(myGroup.getId(), read.get(1).principal().group().id());
        assertEquals(NOW, read.get(1).start());
        assertEquals(NOW.plusDays(1), read.get(1).end());
        assertEquals("REQUEST", read.get(2).level());
        assertTrue(read.get(2).principal().everyone());
        assertEquals(1, read.get(2).minAdvance());
        assertEquals(14, read.get(2).maxAdvance());
    }

    // === W2 — DynamicType lists (§ 1b, § 5d) ==================================

    private DynamicTypeImpl typeWithStoredRows()
    {
        DynamicTypeImpl type = new DynamicTypeImpl(NOW, NOW);
        type.setId(UUID.randomUUID().toString());
        type.setResolver(resolver);
        PermissionInputMapper.apply(type, List.of(
                row(principal("everyone", true), "READ_TYPE"),
                row(principal("groupId", myGroup.getId()), "CREATE"),
                row(principal("userId", lenny.getId()), "ADMIN"),
                row(principal("groupId", myGroup.getId()), "READ")), Kind.RESOURCE, PATH, resolver);
        return type;
    }

    private static List<String> levels(DynamicTypeImpl type)
    {
        List<String> out = new ArrayList<>();
        type.getPermissionList().forEach(p -> out.add(p.getAccessLevel().name()));
        return out;
    }

    @Test
    void typeAccessRowsMintedWithGroupOrEveryone()
    {
        List<Permission> rows = PermissionInputMapper.toRows(container, List.of(
                row(principal("groupId", myGroup.getId()), "CREATE"),
                row(principal("everyone", true), "READ_TYPE")), Kind.TYPE_ACCESS, "input.typeAccess", resolver);
        assertEquals(AccessLevel.CREATE, rows.get(0).getAccessLevel());
        assertSame(myGroup, rows.get(0).getGroup());
        assertEquals(AccessLevel.READ_TYPE, rows.get(1).getAccessLevel());
        assertNull(rows.get(1).getGroup());
        assertNull(rows.get(1).getUser());
    }

    @Test
    void nullTypeAccessWithEmptyInstanceDefaultsKeepsTypeRows()
    {
        DynamicTypeImpl type = typeWithStoredRows();
        PermissionInputMapper.replaceTypeLists(type, null, List.of(), Kind.RESOURCE,
                "input.typeAccess", "input.resourceInstanceDefaults", resolver);
        assertEquals(List.of("READ_TYPE", "CREATE"), levels(type));
    }

    @Test
    void emptyTypeAccessWithNullInstanceDefaultsKeepsInstanceRows()
    {
        DynamicTypeImpl type = typeWithStoredRows();
        PermissionInputMapper.replaceTypeLists(type, List.of(), null, Kind.RESOURCE,
                "input.typeAccess", "input.resourceInstanceDefaults", resolver);
        assertEquals(List.of("ADMIN", "READ"), levels(type), "a stored ADMIN row belongs to the instance defaults");
    }

    @Test
    void bothListsReplacedTypeRowsFirst()
    {
        DynamicTypeImpl type = typeWithStoredRows();
        PermissionInputMapper.replaceTypeLists(type,
                List.of(row(principal("everyone", true), "CREATE")),
                List.of(row(principal("groupId", myGroup.getId()), "EDIT")), Kind.RESOURCE,
                "input.typeAccess", "input.resourceInstanceDefaults", resolver);
        assertEquals(List.of("CREATE", "EDIT"), levels(type));
    }

    @Test
    void instanceDefaultErrorsUseTheirListPath()
    {
        DynamicTypeImpl type = typeWithStoredRows();
        ReservationMutationException e = assertThrows(ReservationMutationException.class,
                () -> PermissionInputMapper.replaceTypeLists(type, null,
                        List.of(row(principal("everyone", true), "READ", "start", NOW)), Kind.RESOURCE,
                        "input.typeAccess", "input.resourceInstanceDefaults", resolver));
        assertEquals("input.resourceInstanceDefaults[0].level", e.path());
        assertEquals(List.of("READ_TYPE", "CREATE", "ADMIN", "READ"), levels(type), "a rejected list changes nothing");
    }
}
