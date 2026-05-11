package org.rapla.plugin.adminpanels;

/** Whether a panel edits system-wide config (admin-only) or the calling user's
 *  per-user preferences. The two values are surfaced as separate menu entries
 *  on the client (Edit > Admin Settings vs Edit > Options) sharing the same
 *  generic renderer. */
public enum PanelScope
{
    /** Admin-only panels — edit values from {@code facade.getSystemPreferences()} or
     *  similar deployment-wide stores. The REST controller gates these on
     *  {@code User.isAdmin()}. */
    SYSTEM,

    /** Per-user panels — edit values from {@code facade.getPreferences(callingUser)}.
     *  Visible to every authenticated user; values are scoped to the caller. */
    PER_USER
}
