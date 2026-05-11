package org.rapla.server.adminpanels;

import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.adminpanels.ActionResult;
import org.rapla.plugin.adminpanels.PanelDefinition;
import org.rapla.plugin.adminpanels.PanelScope;

import java.util.List;
import java.util.Map;

/** Convenience base for {@link PreferencesPanel} impls that read/write the
 *  rapla SYSTEM preferences ({@code RaplaFacade.getSystemPreferences()}).
 *
 *  <p>Subclasses implement {@link #buildDefinition(Preferences, java.util.Locale)}
 *  to render the panel from current state, and {@link #applyValues(Preferences, Map)}
 *  to write changes onto an editable preferences clone. The base class handles
 *  the editable-preferences clone + dispatch lifecycle.
 *
 *  <p>Always SYSTEM-scoped — vanilla plugins live under {@code Plugins/...}
 *  in the admin tree. Subclasses that need PER_USER scope should not extend
 *  this and instead implement {@link PreferencesPanel} directly. */
public abstract class AbstractPluginPreferencesPanel implements PreferencesPanel
{
    protected final RaplaFacade facade;

    protected AbstractPluginPreferencesPanel(RaplaFacade facade)
    {
        this.facade = facade;
    }

    @Override
    public PanelScope scope() { return PanelScope.SYSTEM; }

    @Override
    public List<String> path() { return List.of("Plugins"); }

    @Override
    public final PanelDefinition getDefinition(java.util.Locale locale, User user) throws RaplaException
    {
        Preferences prefs = facade.getSystemPreferences();
        return buildDefinition(prefs, locale);
    }

    @Override
    public final PanelDefinition save(User user, Map<String, Object> values) throws RaplaException
    {
        Preferences editable = facade.edit(facade.getSystemPreferences());
        applyValues(editable, values);
        facade.storeAndRemove(new Entity[]{editable}, Entity.ENTITY_ARRAY, user);
        return buildDefinition(facade.getSystemPreferences(), java.util.Locale.getDefault());
    }

    @Override
    public ActionResult invokeAction(User user, String actionId, Map<String, Object> currentValues) throws RaplaException
    {
        return ActionResult.fail("Action not supported by this panel: " + actionId);
    }

    /** Build the definition from current (read-only) preferences. */
    protected abstract PanelDefinition buildDefinition(Preferences prefs, java.util.Locale locale) throws RaplaException;

    /** Apply submitted wire values onto an editable {@link Preferences} clone.
     *  The base class handles the edit/dispatch boilerplate; this method only
     *  needs to {@code preferences.putEntry(...)} based on {@code values}. */
    protected abstract void applyValues(Preferences preferences, Map<String, Object> values) throws RaplaException;
}
