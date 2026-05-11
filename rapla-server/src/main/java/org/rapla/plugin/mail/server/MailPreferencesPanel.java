package org.rapla.plugin.mail.server;

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
import org.rapla.plugin.mail.MailConfigService;
import org.rapla.plugin.mail.MailPlugin;
import org.rapla.server.adminpanels.PreferencesPanel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Server-driven replacement for the legacy Swing {@code MailOption}.
 *
 *  <p>SMTP server config persisted under
 *  {@link MailPlugin#MAILSERVER_CONFIG} as a {@link RaplaConfiguration} with
 *  children {@code smtp-host}, {@code smtp-port}, {@code ssl}, {@code startTls},
 *  {@code username}, {@code password}; default sender persisted separately
 *  under {@link MailPlugin#DEFAULT_SENDER_ENTRY}.
 *
 *  <p>Auth method modeled as a single {@link FieldType#SELECT} (values
 *  {@code none}/{@code ssl}/{@code startTls}) — equivalent to the legacy
 *  three-radio-button mutually-exclusive choice, but cleaner over the wire.
 *
 *  <p>When the servlet container provides mail config (Spring Boot mail
 *  starter or similar), the editable fields are hidden behind a single
 *  DISPLAY_ONLY notice — same UX as the legacy panel which collapsed all
 *  inputs in that case. */
@Service
public class MailPreferencesPanel implements PreferencesPanel
{
    private static final int NO_AUTH_DEFAULT_PORT = 25;
    private static final int SSL_DEFAULT_PORT = 465;
    private static final int STARTTLS_DEFAULT_PORT = 587;

    private final RaplaFacade facade;
    private final ObjectProvider<MailConfigService> mailConfigProvider;

    @Autowired
    public MailPreferencesPanel(RaplaFacade facade, ObjectProvider<MailConfigService> mailConfigProvider)
    {
        this.facade = facade;
        this.mailConfigProvider = mailConfigProvider;
    }

    @Override public String getId() { return MailPlugin.PLUGIN_ID; }
    @Override public PanelScope scope() { return PanelScope.SYSTEM; }
    @Override public List<String> path() { return List.of("Plugins"); }
    @Override public String title(Locale locale) { return "Mail Plugin"; }

    @Override
    public PanelDefinition getDefinition(Locale locale, User user) throws RaplaException
    {
        Preferences prefs = facade.getSystemPreferences();
        MailConfigService configService = mailConfigProvider.getObject();
        boolean externalConfigEnabled = configService.isExternalConfigEnabled();

        Configuration config = prefs.getEntry(MailPlugin.MAILSERVER_CONFIG, null);
        if (config == null) config = configService.getConfig();
        String defaultSender = prefs.getEntryAsString(MailPlugin.DEFAULT_SENDER_ENTRY, "rapla@domainname");

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("externalConfigEnabled", externalConfigEnabled
                ? "Mail config is provided by servlet container; editable fields below are ignored."
                : "");
        values.put("smtpHost", config.getChild("smtp-host").getValue("localhost"));
        values.put("smtpPort", (long) config.getChild("smtp-port").getValueAsInteger(NO_AUTH_DEFAULT_PORT));
        boolean ssl = config.getChild("ssl").getValueAsBoolean(false);
        boolean startTls = config.getChild("startTls").getValueAsBoolean(false);
        values.put("authMethod", ssl ? "ssl" : startTls ? "startTls" : "none");
        values.put("username", config.getChild("username").getValue(""));
        values.put("password", config.getChild("password").getValue(""));
        values.put("defaultSender", defaultSender);

        Map<String, Object> authOptions = Map.of("options", List.of(
                Map.of("value", "none",     "label", "None"),
                Map.of("value", "ssl",      "label", "SSL"),
                Map.of("value", "startTls", "label", "STARTTLS")));

        List<Field> fields = new java.util.ArrayList<>();
        fields.add(new Field("externalConfigEnabled", "External config",
                FieldType.DISPLAY_ONLY, null, true, Map.of()));
        if (!externalConfigEnabled)
        {
            fields.add(new Field("smtpHost",     "Mail server",   FieldType.TEXT,     null, false, Map.of()));
            fields.add(new Field("authMethod",   "Auth method",   FieldType.SELECT,   null, false, authOptions));
            fields.add(new Field("smtpPort",     "Port",          FieldType.INT,      null, false, Map.of()));
            fields.add(new Field("username",     "Username",      FieldType.TEXT,     null, false, Map.of()));
            fields.add(new Field("password",     "Password",      FieldType.PASSWORD, null, false, Map.of()));
            fields.add(new Field("defaultSender","Default sender",FieldType.TEXT,     null, false, Map.of()));
        }

        return new PanelDefinition(getId(), scope(), path(), title(locale),
                "SMTP server config. Default ports per auth method: "
                        + NO_AUTH_DEFAULT_PORT + " (none), "
                        + SSL_DEFAULT_PORT + " (SSL), "
                        + STARTTLS_DEFAULT_PORT + " (STARTTLS).",
                fields,
                List.of(new ActionButton("testMail", "Send test mail",
                        "Tests the current (unsaved) config by sending a mail to the calling admin's address.",
                        false, null)),
                values);
    }

    @Override
    public PanelDefinition save(User user, Map<String, Object> wireValues) throws RaplaException
    {
        Preferences editable = facade.edit(facade.getSystemPreferences());

        // Default-detection: legacy panel's defaults are localhost/25/none/no
        // username/no password. If everything matches, remove the entry.
        String authMethod = String.valueOf(wireValues.getOrDefault("authMethod", "none"));
        String host       = String.valueOf(wireValues.getOrDefault("smtpHost", "localhost"));
        int port          = toInt(wireValues.get("smtpPort"), NO_AUTH_DEFAULT_PORT);
        String username   = String.valueOf(wireValues.getOrDefault("username", "")).trim();
        String password   = String.valueOf(wireValues.getOrDefault("password", "")).trim();
        boolean mailServerDefault = "none".equals(authMethod)
                && "localhost".equals(host)
                && port == NO_AUTH_DEFAULT_PORT
                && username.isEmpty() && password.isEmpty();

        if (mailServerDefault)
        {
            editable.removeEntry(MailPlugin.MAILSERVER_CONFIG.getId());
        }
        else
        {
            RaplaConfiguration newConfig = new RaplaConfiguration("config");
            applyConfigChildren(newConfig, wireValues);
            editable.putEntry(MailPlugin.MAILSERVER_CONFIG, newConfig);
        }

        // Default sender — legacy default "rapla@domainname". Remove if matching.
        Object sender = wireValues.get("defaultSender");
        String senderStr = sender == null ? "" : sender.toString();
        putOrRemoveSender(editable, senderStr);

        facade.storeAndRemove(new Entity[]{editable}, Entity.ENTITY_ARRAY, user);
        return getDefinition(Locale.getDefault(), user);
    }

    private static void putOrRemoveSender(Preferences prefs, String sender)
    {
        if ("rapla@domainname".equals(sender) || sender.isEmpty())
        {
            prefs.removeEntry(MailPlugin.DEFAULT_SENDER_ENTRY.getId());
        }
        else
        {
            prefs.putEntry(MailPlugin.DEFAULT_SENDER_ENTRY, sender);
        }
    }

    @Override
    public ActionResult invokeAction(User user, String actionId, Map<String, Object> currentValues)
    {
        if (!"testMail".equals(actionId))
        {
            return ActionResult.fail("Unknown action: " + actionId);
        }
        try
        {
            DefaultConfiguration newConfig = new DefaultConfiguration("config");
            applyConfigChildren(newConfig, currentValues);
            String defaultSender = String.valueOf(currentValues.getOrDefault("defaultSender", ""));
            mailConfigProvider.getObject().testMail(newConfig, defaultSender);
            return ActionResult.ok("Test mail sent — check the calling admin's inbox.");
        }
        catch (Exception e)
        {
            return ActionResult.fail(e.getMessage() == null ? "Test mail failed" : e.getMessage());
        }
    }

    private static void applyConfigChildren(DefaultConfiguration root, Map<String, Object> values)
    {
        String authMethod = String.valueOf(values.getOrDefault("authMethod", "none"));
        boolean ssl      = "ssl".equals(authMethod);
        boolean startTls = "startTls".equals(authMethod);

        DefaultConfiguration smtpPort = new DefaultConfiguration("smtp-port");
        smtpPort.setValue(toInt(values.get("smtpPort"), defaultPort(authMethod)));
        root.addChild(smtpPort);

        DefaultConfiguration smtpHost = new DefaultConfiguration("smtp-host");
        smtpHost.setValue(String.valueOf(values.getOrDefault("smtpHost", "localhost")));
        root.addChild(smtpHost);

        DefaultConfiguration sslChild = new DefaultConfiguration("ssl");
        sslChild.setValue(ssl);
        root.addChild(sslChild);
        DefaultConfiguration startTlsChild = new DefaultConfiguration("startTls");
        startTlsChild.setValue(startTls);
        root.addChild(startTlsChild);

        DefaultConfiguration username = new DefaultConfiguration("username");
        String u = String.valueOf(values.getOrDefault("username", "")).trim();
        if (!u.isEmpty()) username.setValue(u);
        root.addChild(username);

        DefaultConfiguration password = new DefaultConfiguration("password");
        String p = String.valueOf(values.getOrDefault("password", "")).trim();
        if (!p.isEmpty()) password.setValue(p);
        root.addChild(password);
    }

    private static int defaultPort(String authMethod)
    {
        return switch (authMethod)
        {
            case "ssl"      -> SSL_DEFAULT_PORT;
            case "startTls" -> STARTTLS_DEFAULT_PORT;
            default          -> NO_AUTH_DEFAULT_PORT;
        };
    }

    private static int toInt(Object v, int fallback)
    {
        if (v instanceof Number n) return n.intValue();
        if (v == null) return fallback;
        try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return fallback; }
    }
}
