package org.rapla.plugin.adminpanels;

/** A button that triggers a server-side action (compute, test connection,
 *  trigger a sync, etc.). On click, the client POSTs the panel's current
 *  field values to the action endpoint; server returns an {@link ActionResult}.
 *
 *  @param key             stable action identifier (used in the action URL)
 *  @param label           user-visible button label, locale-aware
 *  @param description     optional tooltip / help text
 *  @param confirmRequired if true, client shows a confirm dialog before invoking
 *  @param confirmMessage  message shown in the confirm dialog when
 *                         {@code confirmRequired == true}
 */
public record ActionButton(
        String key,
        String label,
        String description,
        boolean confirmRequired,
        String confirmMessage)
{
}
