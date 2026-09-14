package org.rapla.client.edit.reservation;

import org.rapla.entities.User;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.storage.PermissionController;

import java.util.Collection;

/**
 * Pure-Java decision for the visible / writable state of one classification
 * field on a reservation (or any classifiable) edit form. Carved out of
 * {@code ClassificationEditUI.createEditField(...)} (PRD 023 Phase 6c).
 * <p>
 * Multi-edit semantics: when editing several classifications at once,
 * the decision is conservative —
 * <ul>
 *   <li>If <b>any</b> object hides the attribute (user can't read), the
 *       field is invisible (and not writable).</li>
 *   <li>If <b>any</b> object denies write but all permit read, the field
 *       is visible but read-only.</li>
 *   <li>If the panel is globally read-only (e.g. the user is browsing
 *       not editing), write is forced false regardless.</li>
 * </ul>
 * <p>
 * The Angular reservation-edit form needs the same decision per
 * attribute — exposed either by calling this class directly (when the
 * entities are on the client) or via a future REST endpoint that
 * computes it server-side and ships {@code computed.fields[]} as part
 * of the entity response (PRD 026 §6).
 */
public final class ClassificationFieldVisibility
{
    private ClassificationFieldVisibility() {}

    /**
     * Result of the decision. {@code !visible} implies {@code !writable}.
     */
    public record Result(boolean visible, boolean writable)
    {
        public static Result of(boolean visible, boolean writable)
        {
            return new Result(visible, writable && visible);
        }
    }

    /**
     * Compute visibility and writability for one attribute across the
     * given objects.
     *
     * @param objects              the classifications being edited (multi-edit list)
     * @param attribute            the attribute / field being rendered
     * @param user                 the acting user
     * @param permissionController permission engine
     * @param panelReadOnly        true when the surrounding panel is read-only
     *                             (e.g. info view, no edit mode)
     */
    public static Result resolve(Collection<? extends Classification> objects,
                                 Attribute attribute,
                                 User user,
                                 PermissionController permissionController,
                                 boolean panelReadOnly)
    {
        if (objects == null || objects.isEmpty())
        {
            // No objects → nothing to render. Keep both false.
            return new Result(false, false);
        }
        boolean visible = true;
        boolean writable = true;
        for (Classification object : objects)
        {
            if (permissionController.canRead(object, attribute, user))
            {
                if (!permissionController.canWrite(object, attribute, user))
                {
                    writable = false;
                }
            }
            else
            {
                visible = false;
                writable = false;
                break;
            }
        }
        if (panelReadOnly)
        {
            writable = false;
        }
        return new Result(visible, writable);
    }
}
