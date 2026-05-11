package org.rapla.plugin.archiver.server;

import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.Configuration;
import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.adminpanels.ActionButton;
import org.rapla.plugin.adminpanels.ActionResult;
import org.rapla.plugin.adminpanels.Field;
import org.rapla.plugin.adminpanels.FieldType;
import org.rapla.plugin.adminpanels.PanelDefinition;
import org.rapla.plugin.adminpanels.PanelScope;
import org.rapla.plugin.archiver.ArchiverService;
import org.rapla.server.adminpanels.PreferencesPanel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Server-driven replacement for the legacy {@code ArchiverOption} Swing panel.
 *
 *  <p>Two scalar settings + three actions:
 *  <ul>
 *    <li>{@code removeOlder} BOOL + {@code days} INT — gate + retention window.
 *        Persisted as a {@code RaplaConfiguration} child {@code remove-older-than}
 *        (whose presence implies the BOOL is on; absence implies off).</li>
 *    <li>{@code exportToDataXML} BOOL — backup/restore toggle. Persisted as the
 *        {@code export} child. Disabled at the UI level when
 *        {@link ArchiverService#isExportEnabled()} returns false (file-storage only
 *        — JDBC deployments can't dump data.xml).</li>
 *    <li>Action buttons: {@code deleteNow} (purge events older than {@code days}),
 *        {@code backupNow} (export current state to data.xml), {@code restoreAndRestart}
 *        (overwrite live DB from data.xml — confirmation required).</li>
 *  </ul>
 *
 *  <p>Doesn't extend {@code AbstractPluginPreferencesPanel} because the actions
 *  need direct access to {@link ArchiverService} (and the restore action needs the
 *  caller's context for the restart hook). */
@Service
public class ArchiverPreferencesPanel implements PreferencesPanel
{
    private final RaplaFacade facade;
    /** {@code ArchiverServiceImpl} is request-scoped (it needs the live
     *  {@link jakarta.servlet.http.HttpServletRequest} for user resolution),
     *  so this singleton panel can't hold a direct ref. {@code ObjectProvider}
     *  resolves a fresh instance at action-invocation time — guaranteed to
     *  be inside an HTTP request because the only caller is the controller. */
    private final ObjectProvider<ArchiverServiceImpl> archiverProvider;

    @Autowired
    public ArchiverPreferencesPanel(RaplaFacade facade, ObjectProvider<ArchiverServiceImpl> archiverProvider)
    {
        this.facade = facade;
        this.archiverProvider = archiverProvider;
    }

    @Override public String getId() { return "org.rapla.plugin.archiver"; }
    @Override public PanelScope scope() { return PanelScope.SYSTEM; }
    @Override public List<String> path() { return List.of("Plugins"); }
    @Override public String title(Locale locale) { return "Archiver Plugin"; }

    @Override
    public PanelDefinition getDefinition(Locale locale, User user) throws RaplaException
    {
        Preferences prefs = facade.getSystemPreferences();
        RaplaConfiguration config = prefs.getEntry(ArchiverService.CONFIG, new RaplaConfiguration());

        int days = config.getChild(ArchiverService.REMOVE_OLDER_THAN_ENTRY).getValueAsInteger(-1);
        boolean removeOlder = days >= 0;
        if (!removeOlder) days = 30;     // default for the input when the flag is off

        boolean exportSelected = config.getChild(ArchiverService.EXPORT).getValueAsBoolean(false);
        boolean exportSupported = archiverProvider.getObject().isExportEnabled();

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("removeOlder", removeOlder);
        values.put("days", (long) days);
        values.put("exportToDataXML", exportSelected && exportSupported);

        return new PanelDefinition(getId(), scope(), path(), title(locale),
                exportSupported
                        ? "Periodic event cleanup + data-file backup/restore."
                        : "Periodic event cleanup. Backup/restore unavailable (JDBC storage in use).",
                List.of(
                        new Field("removeOlder", "Remove events older than", FieldType.BOOL, null, false, Map.of()),
                        new Field("days", "Days", FieldType.INT, null, false, Map.of()),
                        new Field("exportToDataXML", "Export DB to file", FieldType.BOOL,
                                exportSupported ? null : "Disabled — JDBC storage doesn't support data.xml export.",
                                !exportSupported, Map.of())),
                List.of(
                        new ActionButton("deleteNow", "Delete now",
                                "Run the cleanup once with the current 'days' value.", false, null),
                        new ActionButton("backupNow", "Backup now",
                                "Write current state to data.xml.", false, null),
                        new ActionButton("restoreAndRestart", "Restore and restart",
                                "Overwrite the live DB from data.xml and restart the server.",
                                true, "Current data will be overwritten by the backup version. Continue?")),
                values);
    }

    @Override
    public PanelDefinition save(User user, Map<String, Object> wireValues) throws RaplaException
    {
        Preferences editable = facade.edit(facade.getSystemPreferences());
        boolean removeOlder    = Boolean.TRUE.equals(wireValues.get("removeOlder"));
        boolean exportToDataXML = Boolean.TRUE.equals(wireValues.get("exportToDataXML"));

        if (!removeOlder && !exportToDataXML)
        {
            // Matches the deployment default (both off ⇒ empty config). Remove
            // the entry so future default changes propagate without an explicit
            // override pinning this deployment.
            editable.removeEntry(ArchiverService.CONFIG.getId());
        }
        else
        {
            RaplaConfiguration newConfig = new RaplaConfiguration("config");
            if (removeOlder)
            {
                int days = toInt(wireValues.get("days"), 30);
                DefaultConfiguration child = new DefaultConfiguration(ArchiverService.REMOVE_OLDER_THAN_ENTRY);
                child.setValue(days);
                newConfig.addChild(child);
            }
            if (exportToDataXML)
            {
                DefaultConfiguration child = new DefaultConfiguration(ArchiverService.EXPORT);
                child.setValue(true);
                newConfig.addChild(child);
            }
            editable.putEntry(ArchiverService.CONFIG, newConfig);
        }
        facade.storeAndRemove(new Entity[]{editable}, Entity.ENTITY_ARRAY, user);
        return getDefinition(Locale.getDefault(), user);
    }

    @Override
    public ActionResult invokeAction(User user, String actionId, Map<String, Object> currentValues)
    {
        try
        {
            ArchiverServiceImpl archiver = archiverProvider.getObject();
            switch (actionId)
            {
                case "deleteNow":
                    Integer days = toIntOrNull(currentValues.get("days"));
                    archiver.delete(days);
                    return ActionResult.ok("Deleted events older than " + days + " days.");
                case "backupNow":
                    archiver.backupNow();
                    return ActionResult.ok("Backup complete.");
                case "restoreAndRestart":
                    archiver.restore();
                    return ActionResult.ok("Restore initiated. Server is restarting — reconnect in a few seconds.");
                default:
                    return ActionResult.fail("Unknown action: " + actionId);
            }
        }
        catch (Exception e)
        {
            return ActionResult.fail(e.getMessage() == null ? "Action failed" : e.getMessage());
        }
    }

    private static int toInt(Object v, int fallback)
    {
        if (v instanceof Number n) return n.intValue();
        if (v == null) return fallback;
        try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return fallback; }
    }

    private static Integer toIntOrNull(Object v)
    {
        if (v instanceof Number n) return n.intValue();
        if (v == null) return null;
        try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return null; }
    }
}
