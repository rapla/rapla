package org.rapla.plugin.adminpanels;

import java.util.List;

/** Lightweight descriptor returned by {@code PreferencesAdminService.listPanels()}.
 *  Carries enough info to build the navigation tree without fetching every
 *  panel's full definition.
 *
 *  @param id    stable panel identifier — used in subsequent get/save/action calls
 *  @param scope SYSTEM (admin-only) or PER_USER
 *  @param path  breadcrumb path; client builds the tree from these arrays
 *               (e.g. {@code ["Plugin Settings", "Notification"]} or
 *               {@code ["DHBW", "LDAP"]}).
 *  @param title leaf-node label, locale-aware (server fills in the user's locale)
 */
public record PanelSummary(
        String id,
        PanelScope scope,
        List<String> path,
        String title)
{
}
