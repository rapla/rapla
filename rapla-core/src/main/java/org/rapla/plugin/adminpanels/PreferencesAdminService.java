package org.rapla.plugin.adminpanels;

import org.rapla.framework.RaplaException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;
import java.util.Map;

/** Generic admin / preferences panel REST surface. Server-side {@code PreferencesPanel}
 *  beans publish themselves through this; the rapla-client renders them with one
 *  generic Swing dialog (PRD 020 §3 Plan).
 *
 *  <p>The wire format is generic JSON {@code Map<String, Object>}. Server is the
 *  translator between wire shape and native storage (rapla {@code Preferences},
 *  {@code RaplaConfiguration} tree, yaml fallback, etc.) — see PRD 020 §"Wire
 *  contract" for the design rule.
 */
@HttpExchange("/api/admin/panels")
public interface PreferencesAdminService
{
    /** List panels visible to the calling user, scoped by parameter.
     *  SYSTEM-scoped panels require admin. */
    @GetExchange
    List<PanelSummary> listPanels(@RequestParam("scope") PanelScope scope) throws RaplaException;

    /** Fetch a panel's full definition + current values. */
    @GetExchange("/{id}")
    PanelDefinition getPanel(@PathVariable("id") String id) throws RaplaException;

    /** Persist the submitted values. Server returns the refreshed
     *  {@link PanelDefinition} so the client can rebind widgets to the new
     *  state (covers DISPLAY_ONLY fields whose value depends on the just-saved
     *  inputs, or sanitization/normalization done server-side). */
    @PostExchange("/{id}/save")
    PanelDefinition savePanel(@PathVariable("id") String id,
                              @RequestBody Map<String, Object> values) throws RaplaException;

    /** Invoke an action button. The body carries the panel's current
     *  in-memory field values (NOT yet persisted) so the action can compute
     *  against them. */
    @PostExchange("/{id}/action/{actionId}")
    ActionResult invokeAction(@PathVariable("id") String id,
                              @PathVariable("actionId") String actionId,
                              @RequestBody Map<String, Object> currentValues) throws RaplaException;
}
