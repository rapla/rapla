package org.rapla.client.menu;

import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.storage.PermissionController;

import java.util.Collection;
import java.util.function.BiPredicate;

/**
 * Pure-Java decision logic for the {@code isEnabled()} check of
 * {@code RaplaObjectActions} (rapla-client). Carved out per PRD 023 so
 * the per-action-type / per-entity-type permission decision is tier-1
 * testable and reusable from server REST.
 * <p>
 * The action-type constants match
 * {@code rapla-client/.../menu/impl/RaplaObjectActions} ({@code NEW},
 * {@code EDIT}, {@code DELETE}, {@code EDIT_SELECTION}, {@code DELETE_SELECTION}).
 * The action class keeps the constants for backward compat; this class
 * mirrors them as static finals so the carved-out unit doesn't depend
 * on the Swing-side action class.
 */
public final class RaplaObjectActionPolicy
{
    private RaplaObjectActionPolicy() {}

    public static final int NEW              = 5;
    public static final int EDIT             = 6;
    public static final int DELETE           = 1;
    public static final int EDIT_SELECTION   = 9;
    public static final int DELETE_SELECTION = 8;

    /**
     * Decide whether the action should be enabled given the current
     * selection and the user's permissions.
     *
     * @param type the action type (one of the constants above; matching values
     *             from {@code RaplaObjectActions} are also valid)
     * @param object single selected entity (used for {@code EDIT} / {@code DELETE} /
     *               {@code NEW} on a category context)
     * @param objectList collection of selected entities for the bulk types
     * @param raplaType the entity type associated with the action (e.g.
     *                  {@link Allocatable#getClass()} or {@link Category#getClass()})
     * @param user the acting user
     * @param permissionController shared permission engine
     */
    /**
     * Production overload — delegates to the test-friendly variant. The
     * Swing action wires the real {@link PermissionController}.
     */
    public static boolean isEnabled(int type,
                                    Entity<?> object,
                                    Collection<Entity<?>> objectList,
                                    Class<? extends RaplaObject> raplaType,
                                    User user,
                                    PermissionController permissionController)
    {
        return isEnabled(type, object, objectList, raplaType, user,
                permissionController::canModify,
                u -> permissionController.isRegisterer(null, u));
    }

    /**
     * Test-friendly variant that doesn't depend on the full
     * {@link PermissionController}. Callers pass the two predicates the
     * policy actually consults — used by tier-1 tests to stub freely
     * (the production overload bridges to the real controller).
     *
     * @param canModify    {@code (entity, user) → boolean}
     * @param isRegisterer {@code user → boolean} (matches the legacy
     *                     {@code isRegisterer(null, user)} call site)
     */
    public static boolean isEnabled(int type,
                                    Entity<?> object,
                                    Collection<Entity<?>> objectList,
                                    Class<? extends RaplaObject> raplaType,
                                    User user,
                                    BiPredicate<Entity<?>, User> canModify,
                                    java.util.function.Predicate<User> isRegisterer)
    {
        if (user == null) return false;

        if (type == EDIT || type == DELETE)
        {
            return canModify.test(object, user);
        }
        if (type == NEW)
        {
            boolean admin = user.isAdmin();
            if (raplaType != null && !admin)
            {
                if (raplaType == Allocatable.class)
                {
                    return isRegisterer.test(user);
                }
                if (raplaType == Category.class && object instanceof Category)
                {
                    return canModify.test(object, user);
                }
                return false;
            }
            return admin;
        }
        if (type == EDIT_SELECTION || type == DELETE_SELECTION)
        {
            if (objectList == null || objectList.isEmpty()) return false;
            for (Entity<?> entity : objectList)
            {
                if (!canModify.test(entity, user)) return false;
            }
            return true;
        }
        return true;   // unknown / informational types default to enabled (preserves legacy behaviour)
    }
}
