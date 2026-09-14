package org.rapla.client.menu;

import org.junit.jupiter.api.Test;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 coverage of {@link RaplaObjectActionPolicy} via the
 * predicate-based overload. Stubs {@link User}, {@link Entity},
 * {@link Category} via {@code Proxy} — the test never touches the real
 * {@link org.rapla.storage.PermissionController} since its methods are
 * final / heavy to set up.
 */
class RaplaObjectActionPolicyTest
{
    private static final BiPredicate<Entity<?>, User> CAN_MODIFY_ALL  = (e, u) -> true;
    private static final BiPredicate<Entity<?>, User> CAN_MODIFY_NONE = (e, u) -> false;
    private static final Predicate<User> REGISTERER_YES = u -> true;
    private static final Predicate<User> REGISTERER_NO  = u -> false;

    @Test
    void nullUserDisablesEverything()
    {
        Entity<?> obj = stubEntity("e1");
        assertFalse(RaplaObjectActionPolicy.isEnabled(
                RaplaObjectActionPolicy.NEW, obj, null, null, null,
                CAN_MODIFY_ALL, REGISTERER_YES));
        assertFalse(RaplaObjectActionPolicy.isEnabled(
                RaplaObjectActionPolicy.EDIT, obj, null, null, null,
                CAN_MODIFY_ALL, REGISTERER_YES));
    }

    // ---------- EDIT / DELETE ----------

    @Test
    void editEnabledWhenUserCanModify()
    {
        assertTrue(RaplaObjectActionPolicy.isEnabled(
                RaplaObjectActionPolicy.EDIT, stubEntity("e1"), null, null, regular(),
                CAN_MODIFY_ALL, REGISTERER_NO));
    }

    @Test
    void editDisabledWhenUserCannotModify()
    {
        assertFalse(RaplaObjectActionPolicy.isEnabled(
                RaplaObjectActionPolicy.EDIT, stubEntity("e1"), null, null, regular(),
                CAN_MODIFY_NONE, REGISTERER_YES));
    }

    @Test
    void deleteFollowsCanModify()
    {
        Entity<?> obj = stubEntity("e1");
        assertTrue(RaplaObjectActionPolicy.isEnabled(
                RaplaObjectActionPolicy.DELETE, obj, null, null, regular(),
                CAN_MODIFY_ALL, REGISTERER_NO));
        assertFalse(RaplaObjectActionPolicy.isEnabled(
                RaplaObjectActionPolicy.DELETE, obj, null, null, regular(),
                CAN_MODIFY_NONE, REGISTERER_YES));
    }

    // ---------- NEW ----------

    @Test
    void newEnabledForAdmin()
    {
        // Admin bypasses every entity-type-specific check.
        assertTrue(RaplaObjectActionPolicy.isEnabled(
                RaplaObjectActionPolicy.NEW, null, null, Allocatable.class, admin(),
                CAN_MODIFY_NONE, REGISTERER_NO));
    }

    @Test
    void newDisabledForNonAdminWithoutRaplaType()
    {
        assertFalse(RaplaObjectActionPolicy.isEnabled(
                RaplaObjectActionPolicy.NEW, null, null, null, regular(),
                CAN_MODIFY_ALL, REGISTERER_YES));
    }

    @Test
    void newOnAllocatableEnabledForRegisterer()
    {
        assertTrue(RaplaObjectActionPolicy.isEnabled(
                RaplaObjectActionPolicy.NEW, null, null, Allocatable.class, regular(),
                CAN_MODIFY_NONE, REGISTERER_YES));
    }

    @Test
    void newOnAllocatableDisabledWhenNotRegisterer()
    {
        assertFalse(RaplaObjectActionPolicy.isEnabled(
                RaplaObjectActionPolicy.NEW, null, null, Allocatable.class, regular(),
                CAN_MODIFY_ALL, REGISTERER_NO));
    }

    @Test
    void newOnCategoryUsesCanModifyOnParentCategory()
    {
        Entity<?> parent = stubCategory("cat-parent");
        assertTrue(RaplaObjectActionPolicy.isEnabled(
                RaplaObjectActionPolicy.NEW, parent, null, Category.class, regular(),
                CAN_MODIFY_ALL, REGISTERER_NO));
        assertFalse(RaplaObjectActionPolicy.isEnabled(
                RaplaObjectActionPolicy.NEW, parent, null, Category.class, regular(),
                CAN_MODIFY_NONE, REGISTERER_YES));
    }

    @Test
    void newOnCategoryWithoutObjectIsDisabled()
    {
        assertFalse(RaplaObjectActionPolicy.isEnabled(
                RaplaObjectActionPolicy.NEW, null, null, Category.class, regular(),
                CAN_MODIFY_ALL, REGISTERER_YES));
    }

    // ---------- EDIT_SELECTION / DELETE_SELECTION ----------

    @Test
    void selectionEnabledWhenUserCanModifyAll()
    {
        List<Entity<?>> selection = List.of(stubEntity("e1"), stubEntity("e2"), stubEntity("e3"));
        assertTrue(RaplaObjectActionPolicy.isEnabled(
                RaplaObjectActionPolicy.EDIT_SELECTION, null, selection, null, regular(),
                CAN_MODIFY_ALL, REGISTERER_NO));
    }

    @Test
    void selectionDisabledWhenAnyItemUnmodifiable()
    {
        Entity<?> e1 = stubEntity("e1");
        Entity<?> e2 = stubEntity("e2");
        Entity<?> e3 = stubEntity("e3");
        BiPredicate<Entity<?>, User> canModify = (e, u) -> e != e2;
        assertFalse(RaplaObjectActionPolicy.isEnabled(
                RaplaObjectActionPolicy.EDIT_SELECTION, null, List.of(e1, e2, e3), null, regular(),
                canModify, REGISTERER_NO));
    }

    @Test
    void emptySelectionIsDisabled()
    {
        assertFalse(RaplaObjectActionPolicy.isEnabled(
                RaplaObjectActionPolicy.EDIT_SELECTION, null, List.of(), null, regular(),
                CAN_MODIFY_ALL, REGISTERER_NO));
        assertFalse(RaplaObjectActionPolicy.isEnabled(
                RaplaObjectActionPolicy.DELETE_SELECTION, null, null, null, regular(),
                CAN_MODIFY_ALL, REGISTERER_NO));
    }

    @Test
    void deleteSelectionFollowsCanModify()
    {
        List<Entity<?>> selection = List.of(stubEntity("e1"));
        assertTrue(RaplaObjectActionPolicy.isEnabled(
                RaplaObjectActionPolicy.DELETE_SELECTION, null, selection, null, regular(),
                CAN_MODIFY_ALL, REGISTERER_NO));
        assertFalse(RaplaObjectActionPolicy.isEnabled(
                RaplaObjectActionPolicy.DELETE_SELECTION, null, selection, null, regular(),
                CAN_MODIFY_NONE, REGISTERER_YES));
    }

    // ---------- unknown type ----------

    @Test
    void unknownTypeDefaultsEnabled()
    {
        assertTrue(RaplaObjectActionPolicy.isEnabled(
                99, null, null, null, regular(),
                CAN_MODIFY_NONE, REGISTERER_NO));
    }

    // ---------- helpers ----------

    private static User admin()   { return stubUser("admin", true);  }
    private static User regular() { return stubUser("monty", false); }

    private static User stubUser(String id, boolean isAdmin)
    {
        return (User) Proxy.newProxyInstance(
                User.class.getClassLoader(),
                new Class[] { User.class },
                (proxy, method, args) ->
                {
                    switch (method.getName())
                    {
                        case "isAdmin":  return isAdmin;
                        case "getId":    return id;
                        case "equals":   return args[0] == proxy;
                        case "hashCode": return System.identityHashCode(proxy);
                        case "toString": return "StubUser[" + id + "]";
                        default: return null;
                    }
                });
    }

    private static Entity<?> stubEntity(String id)
    {
        return (Entity<?>) Proxy.newProxyInstance(
                Entity.class.getClassLoader(),
                new Class[] { Entity.class },
                (proxy, method, args) ->
                {
                    switch (method.getName())
                    {
                        case "getId":    return id;
                        case "equals":   return args[0] == proxy;
                        case "hashCode": return System.identityHashCode(proxy);
                        case "toString": return "StubEntity[" + id + "]";
                        default: return null;
                    }
                });
    }

    private static Category stubCategory(String id)
    {
        return (Category) Proxy.newProxyInstance(
                Category.class.getClassLoader(),
                new Class[] { Category.class },
                (proxy, method, args) ->
                {
                    switch (method.getName())
                    {
                        case "getId":    return id;
                        case "equals":   return args[0] == proxy;
                        case "hashCode": return System.identityHashCode(proxy);
                        case "toString": return "StubCategory[" + id + "]";
                        default: return null;
                    }
                });
    }
}
