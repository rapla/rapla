package org.rapla.plugin.adminpanels;

import java.util.Map;

/** Server response to {@code PreferencesAdminService.invokeAction(...)}.
 *
 *  @param success       whether the action ran successfully
 *  @param message       user-visible message (success summary or error
 *                       description); rendered as a toast / dialog
 *  @param updatedValues optional: field values to rebind in the panel after
 *                       the action — useful for "compute X from current
 *                       inputs" actions where the result is shown in a
 *                       {@link FieldType#DISPLAY_ONLY} field. May be null
 *                       or empty when no rebinding is needed.
 */
public record ActionResult(
        boolean success,
        String message,
        Map<String, Object> updatedValues)
{
    public ActionResult
    {
        if (updatedValues == null) updatedValues = Map.of();
    }

    public static ActionResult ok(String message)
    {
        return new ActionResult(true, message, Map.of());
    }

    public static ActionResult ok(String message, Map<String, Object> updatedValues)
    {
        return new ActionResult(true, message, updatedValues);
    }

    public static ActionResult fail(String message)
    {
        return new ActionResult(false, message, Map.of());
    }
}
