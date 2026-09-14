package org.rapla.server.adminpanels;

import org.rapla.entities.User;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.adminpanels.ActionResult;
import org.rapla.plugin.adminpanels.PanelDefinition;
import org.rapla.plugin.adminpanels.PanelScope;
import org.rapla.plugin.adminpanels.PanelSummary;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Server-side SPI for admin / per-user preferences panels (PRD 020).
 *
 *  <p>Implementations are Spring beans (typically {@code @Service}) that
 *  publish a single panel to the {@code Edit > Admin Settings} or
 *  {@code Edit > Options} dialog on the client. The dispatcher
 *  ({@code PreferencesAdminController}) injects {@code Set<PreferencesPanel>}
 *  and routes by {@link #getId()}.
 *
 *  <p>The wire format is generic JSON {@code Map<String, Object>}. Each impl
 *  is responsible for translating between the wire shape (in
 *  {@link #getDefinition} / {@link #save}) and whatever native storage it uses
 *  (rapla {@code Preferences}, {@code RaplaConfiguration} tree, anywhere).
 *  See PRD 020 §"Wire format" for the design rule.
 */
public interface PreferencesPanel
{
    /** Stable panel identifier. Used in the URL path of get/save/action calls. */
    String getId();

    /** {@link PanelScope#SYSTEM} (admin-only) or {@link PanelScope#PER_USER}.
     *  The controller filters on this for the {@code GET /admin/panels?scope=...}
     *  listing and gates SYSTEM-scoped operations on {@code User.isAdmin()}. */
    PanelScope scope();

    /** Breadcrumb path used by the client to build the navigation tree.
     *  E.g. {@code ["Plugin Settings", "Notification"]} or
     *  {@code ["DHBW", "LDAP"]}. The leaf node label comes from
     *  {@link #title(Locale)} — keep {@code path} as the parents and
     *  separately return the leaf label. */
    List<String> path();

    /** Locale-aware leaf-node label (the displayed name in the tree and the
     *  panel header). */
    String title(Locale locale);

    /** Build the full panel for the requesting user. The impl reads its
     *  current state (typically from {@code Preferences}) and emits a
     *  {@link PanelDefinition} with fields, actions, and current values
     *  in the wire JSON shape. */
    PanelDefinition getDefinition(Locale locale, User user) throws RaplaException;

    /** Persist the submitted values. Server returns the refreshed
     *  {@link PanelDefinition} (covers values that are server-computed or
     *  normalized after save). */
    PanelDefinition save(User user, Map<String, Object> values) throws RaplaException;

    /** Invoke an action button. {@code currentValues} are the panel's
     *  in-memory field values (NOT yet saved) — actions can compute
     *  against them. */
    ActionResult invokeAction(User user, String actionId, Map<String, Object> currentValues) throws RaplaException;

    /** Convenience: derive a {@link PanelSummary} from this panel's
     *  metadata. Default impl is correct for the common case; override
     *  only if the summary needs to differ from the definition. */
    default PanelSummary toSummary(Locale locale)
    {
        return new PanelSummary(getId(), scope(), path(), title(locale));
    }
}
