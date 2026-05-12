package org.rapla.server.spring.web;

import jakarta.servlet.http.HttpServletRequest;
import org.rapla.entities.User;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.adminpanels.ActionResult;
import org.rapla.plugin.adminpanels.PanelDefinition;
import org.rapla.plugin.adminpanels.PanelScope;
import org.rapla.plugin.adminpanels.PanelSummary;
import org.rapla.plugin.adminpanels.PreferencesAdminService;
import org.rapla.server.RemoteSession;
import org.rapla.server.adminpanels.PreferencesPanel;
import org.rapla.storage.RaplaSecurityException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** REST surface for admin / preferences panels (PRD 020).
 *
 *  <p>Dispatches to {@link PreferencesPanel} beans by id. Filters listings by
 *  {@link PanelScope}; gates SYSTEM-scoped operations on {@code User.isAdmin()}.
 */
@RestController
@RequestMapping(value = "/admin/panels", produces = "application/json")
public class PreferencesAdminController implements PreferencesAdminService
{
    private final Set<PreferencesPanel> panels;
    private final RemoteSession session;
    private final HttpServletRequest request;

    @Autowired
    public PreferencesAdminController(Set<PreferencesPanel> panels, RemoteSession session, HttpServletRequest request)
    {
        this.panels = panels;
        this.session = session;
        this.request = request;
    }

    @Override
    @GetMapping
    public List<PanelSummary> listPanels(@RequestParam("scope") PanelScope scope) throws RaplaException
    {
        User user = session.checkAndGetUser(request);
        Locale locale = LocaleContextHolder.getLocale();
        boolean admin = user.isAdmin();

        List<PanelSummary> result = new ArrayList<>();
        for (PreferencesPanel panel : panels)
        {
            if (panel.scope() != scope) continue;
            if (panel.scope() == PanelScope.SYSTEM && !admin) continue;
            result.add(panel.toSummary(locale));
        }
        return result;
    }

    @Override
    @GetMapping("/{id}")
    public PanelDefinition getPanel(@PathVariable("id") String id) throws RaplaException
    {
        User user = session.checkAndGetUser(request);
        PreferencesPanel panel = requirePanel(id);
        gate(panel, user);
        return panel.getDefinition(LocaleContextHolder.getLocale(), user);
    }

    @Override
    @PostMapping("/{id}/save")
    public PanelDefinition savePanel(@PathVariable("id") String id,
            @RequestBody Map<String, Object> values) throws RaplaException
    {
        User user = session.checkAndGetUser(request);
        PreferencesPanel panel = requirePanel(id);
        gate(panel, user);
        return panel.save(user, values);
    }

    @Override
    @PostMapping("/{id}/action/{actionId}")
    public ActionResult invokeAction(@PathVariable("id") String id,
            @PathVariable("actionId") String actionId,
            @RequestBody Map<String, Object> currentValues) throws RaplaException
    {
        User user = session.checkAndGetUser(request);
        PreferencesPanel panel = requirePanel(id);
        gate(panel, user);
        return panel.invokeAction(user, actionId, currentValues);
    }

    private PreferencesPanel requirePanel(String id) throws RaplaException
    {
        for (PreferencesPanel p : panels)
        {
            if (p.getId().equals(id)) return p;
        }
        throw new RaplaException("Unknown admin panel id: " + id);
    }

    private static void gate(PreferencesPanel panel, User user) throws RaplaSecurityException
    {
        if (panel.scope() == PanelScope.SYSTEM && !user.isAdmin())
        {
            throw new RaplaSecurityException("Admin required for system-scope panel: " + panel.getId());
        }
    }
}
