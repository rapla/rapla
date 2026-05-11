package org.rapla.plugin.jndi.server;

import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.adminpanels.ActionButton;
import org.rapla.plugin.adminpanels.ActionResult;
import org.rapla.plugin.adminpanels.Field;
import org.rapla.plugin.adminpanels.FieldType;
import org.rapla.plugin.adminpanels.PanelDefinition;
import org.rapla.plugin.adminpanels.PanelScope;
import org.rapla.plugin.jndi.JNDIPlugin;
import org.rapla.plugin.jndi.internal.JNDIConf;
import org.rapla.plugin.jndi.internal.JNDIConfig;
import org.rapla.server.adminpanels.PreferencesPanel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Server-driven replacement for the legacy {@code JNDIOption} Swing panel.
 *
 *  <p>LDAP/JNDI connection config persisted as a {@link RaplaConfiguration}
 *  under {@link JNDIPlugin#JNDISERVER_CONFIG}, attributes named
 *  {@code enabled}, {@code connectionName}, {@code connectionPassword},
 *  {@code connectionURL}, {@code contextFactory}, {@code digest},
 *  {@code userPassword}, {@code userMail}, {@code userCn}, {@code userSearch},
 *  {@code userBase} — same legacy keys the JNDI auth store reads.
 *
 *  <p>"Test access" is exposed as an ACTION_BUTTON that consumes the
 *  panel's current {@code testUsername}/{@code testPassword} fields and
 *  invokes {@link JNDIConfig#test(JNDIConfig.MailTestRequest)} server-side.
 *  Legacy panel popped a separate password dialog; we put the inputs
 *  inline (DISPLAY_ONLY between the LDAP config and the action) so the
 *  panel layout stays one-shot.
 *
 *  <p>USERGROUP_CONFIG (a list of Category references) is out of scope —
 *  needs a category-picker field type the renderer doesn't have yet. The
 *  legacy panel's group-list editor still works through the legacy bridge
 *  if absolutely needed; routine ops can manage groups elsewhere. */
@Service
public class JNDIPreferencesPanel implements PreferencesPanel
{
    private final RaplaFacade facade;
    private final ObjectProvider<JNDIConfig> jndiConfigProvider;

    @Autowired
    public JNDIPreferencesPanel(RaplaFacade facade, ObjectProvider<JNDIConfig> jndiConfigProvider)
    {
        this.facade = facade;
        this.jndiConfigProvider = jndiConfigProvider;
    }

    @Override public String getId() { return JNDIPlugin.PLUGIN_ID; }
    @Override public PanelScope scope() { return PanelScope.SYSTEM; }
    @Override public List<String> path() { return List.of("Plugins"); }
    @Override public String title(Locale locale) { return "JNDI / LDAP Plugin"; }

    @Override
    public PanelDefinition getDefinition(Locale locale, User user) throws RaplaException
    {
        Preferences prefs = facade.getSystemPreferences();
        org.rapla.framework.Configuration config = prefs.getEntry(JNDIPlugin.JNDISERVER_CONFIG, null);
        if (config == null) config = jndiConfigProvider.getObject().getConfig();

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("enabled",            Boolean.parseBoolean(config.getAttribute(JNDIConf.ENABLED, "false")));
        values.put("connectionName",     config.getAttribute(JNDIConf.CONNECTION_NAME,     "uid=admin,ou=system"));
        values.put("connectionPassword", config.getAttribute(JNDIConf.CONNECTION_PASSWORD, "secret"));
        values.put("connectionURL",      config.getAttribute(JNDIConf.CONNECTION_URL,      "ldap://localhost:10389"));
        values.put("contextFactory",     config.getAttribute(JNDIConf.CONTEXT_FACTORY,     "com.sun.jndi.ldap.LdapCtxFactory"));
        values.put("digest",             config.getAttribute(JNDIConf.DIGEST,              ""));
        values.put("userPassword",       config.getAttribute(JNDIConf.USER_PASSWORD,       ""));
        values.put("userMail",           config.getAttribute(JNDIConf.USER_MAIL,           "mail"));
        values.put("userCn",             config.getAttribute(JNDIConf.USER_CN,             "cn"));
        values.put("userSearch",         config.getAttribute(JNDIConf.USER_SEARCH,         "(uid={0})"));
        values.put("userBase",           config.getAttribute(JNDIConf.USER_BASE,           "dc=example,dc=com"));
        values.put("testUsername",       "");
        values.put("testPassword",       "");

        return new PanelDefinition(getId(), scope(), path(), title(locale),
                "WARNING: rapla's standard auth is used as fallback if LDAP fails. "
                        + "Fill testUsername/testPassword and click 'Test access' to validate the (unsaved) config.",
                List.of(
                        new Field("enabled",            "Enabled",                FieldType.BOOL,     null, false, Map.of()),
                        new Field("connectionURL",      "Connection URL",         FieldType.TEXT,     null, false, Map.of()),
                        new Field("connectionName",     "Bind DN (connectionName)", FieldType.TEXT,   null, false, Map.of()),
                        new Field("connectionPassword", "Bind password",          FieldType.PASSWORD, null, false, Map.of()),
                        new Field("contextFactory",     "Context factory",        FieldType.TEXT,     null, false, Map.of()),
                        new Field("digest",             "Digest",                 FieldType.TEXT,     null, false, Map.of()),
                        new Field("userBase",           "User base",              FieldType.TEXT,     null, false, Map.of()),
                        new Field("userSearch",         "User search filter",     FieldType.TEXT,     null, false, Map.of()),
                        new Field("userCn",             "User CN attribute",      FieldType.TEXT,     null, false, Map.of()),
                        new Field("userMail",           "User mail attribute",    FieldType.TEXT,     null, false, Map.of()),
                        new Field("userPassword",       "User password attribute", FieldType.TEXT,    null, false, Map.of()),
                        new Field("testUsername",       "Test username",          FieldType.TEXT,     null, false, Map.of()),
                        new Field("testPassword",       "Test password",          FieldType.PASSWORD, null, false, Map.of())),
                List.of(new ActionButton("testAccess", "Test access",
                        "Authenticate testUsername/testPassword against the (unsaved) LDAP config above.",
                        false, null)),
                values);
    }

    @Override
    public PanelDefinition save(User user, Map<String, Object> wireValues) throws RaplaException
    {
        Preferences editable = facade.edit(facade.getSystemPreferences());
        RaplaConfiguration newConfig = new RaplaConfiguration("config");
        applyAttributes(newConfig, wireValues);
        editable.putEntry(JNDIPlugin.JNDISERVER_CONFIG, newConfig);
        facade.storeAndRemove(new Entity[]{editable}, Entity.ENTITY_ARRAY, user);
        return getDefinition(Locale.getDefault(), user);
    }

    @Override
    public ActionResult invokeAction(User user, String actionId, Map<String, Object> currentValues)
    {
        if (!"testAccess".equals(actionId))
        {
            return ActionResult.fail("Unknown action: " + actionId);
        }
        String username = String.valueOf(currentValues.getOrDefault("testUsername", "")).trim();
        String password = String.valueOf(currentValues.getOrDefault("testPassword", ""));
        if (username.isEmpty())
        {
            return ActionResult.fail("testUsername is required");
        }
        try
        {
            DefaultConfiguration testConfig = new DefaultConfiguration("test");
            applyAttributes(testConfig, currentValues);
            boolean ok = jndiConfigProvider.getObject().test(
                    new JNDIConfig.MailTestRequest(testConfig, username, password));
            return ok ? ActionResult.ok("LDAP auth succeeded for " + username + ".")
                      : ActionResult.fail("LDAP auth failed for " + username + ".");
        }
        catch (Exception e)
        {
            return ActionResult.fail(e.getMessage() == null ? "Test failed" : e.getMessage());
        }
    }

    private static void applyAttributes(DefaultConfiguration newConfig, Map<String, Object> values)
    {
        setAttr(newConfig, JNDIConf.ENABLED,             Boolean.toString(Boolean.TRUE.equals(values.get("enabled"))));
        setAttr(newConfig, JNDIConf.CONNECTION_NAME,     values.get("connectionName"));
        setAttr(newConfig, JNDIConf.CONNECTION_PASSWORD, values.get("connectionPassword"));
        setAttr(newConfig, JNDIConf.CONNECTION_URL,      values.get("connectionURL"));
        setAttr(newConfig, JNDIConf.CONTEXT_FACTORY,     values.get("contextFactory"));
        setAttr(newConfig, JNDIConf.DIGEST,              values.get("digest"));
        setAttr(newConfig, JNDIConf.USER_BASE,           values.get("userBase"));
        setAttr(newConfig, JNDIConf.USER_SEARCH,         values.get("userSearch"));
        // Legacy panel set userCn/userMail/userPassword unconditionally (no trim/skip),
        // matching here so the persisted shape matches what JNDI auth expects.
        newConfig.setAttribute(JNDIConf.USER_CN,         String.valueOf(values.getOrDefault("userCn",       "")));
        newConfig.setAttribute(JNDIConf.USER_MAIL,       String.valueOf(values.getOrDefault("userMail",     "")));
        newConfig.setAttribute(JNDIConf.USER_PASSWORD,   String.valueOf(values.getOrDefault("userPassword", "")));
    }

    private static void setAttr(DefaultConfiguration config, String name, Object raw)
    {
        if (raw == null) return;
        String v = raw.toString().trim();
        if (!v.isEmpty()) config.setAttribute(name, v);
    }
}
