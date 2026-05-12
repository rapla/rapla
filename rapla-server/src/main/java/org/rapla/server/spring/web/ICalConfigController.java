package org.rapla.server.spring.web;

import jakarta.servlet.http.HttpServletRequest;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.internal.PreferencesImpl;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.export2ical.Export2iCalPlugin;
import org.rapla.plugin.export2ical.UserICalSettings;
import org.rapla.server.RemoteSession;
import org.rapla.storage.RaplaSecurityException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnBean(RemoteSession.class)
@RequestMapping("/ical/config")
public class ICalConfigController
{
    private final RaplaFacade facade;
    private final RemoteSession session;

    public ICalConfigController(RaplaFacade facade, RemoteSession session)
    {
        this.facade = facade;
        this.session = session;
    }

    @GetMapping
    public DefaultConfiguration getConfig(HttpServletRequest request) throws RaplaException
    {
        User user = session.checkAndGetUser(request);
        if (!user.isAdmin())
        {
            throw new RaplaSecurityException("Access only for admin users");
        }
        return loadConfig();
    }

    @GetMapping("/default")
    public DefaultConfiguration getUserDefaultConfig(HttpServletRequest request) throws RaplaException
    {
        session.checkAndGetUser(request);
        return loadConfig();
    }

    @GetMapping("/user")
    public UserICalSettings getUserSettings(HttpServletRequest request) throws RaplaException
    {
        User user = session.checkAndGetUser(request);
        Preferences prefs = facade.getPreferences(user);
        Integer daysBefore = prefs.hasEntry(Export2iCalPlugin.PREF_BEFORE_DAYS)
                ? prefs.getEntryAsInteger(Export2iCalPlugin.PREF_BEFORE_DAYS, 0) : null;
        Integer daysAfter = prefs.hasEntry(Export2iCalPlugin.PREF_AFTER_DAYS)
                ? prefs.getEntryAsInteger(Export2iCalPlugin.PREF_AFTER_DAYS, 0) : null;
        Boolean exportAttendees = prefs.hasEntry(Export2iCalPlugin.EXPORT_ATTENDEES_PREFERENCE)
                ? prefs.getEntryAsBoolean(Export2iCalPlugin.EXPORT_ATTENDEES_PREFERENCE, false) : null;
        String participationStatus = prefs.hasEntry(Export2iCalPlugin.EXPORT_ATTENDEES_PARTICIPATION_STATUS_PREFERENCE)
                ? prefs.getEntryAsString(Export2iCalPlugin.EXPORT_ATTENDEES_PARTICIPATION_STATUS_PREFERENCE, null) : null;
        return new UserICalSettings(daysBefore, daysAfter, exportAttendees, participationStatus);
    }

    private DefaultConfiguration loadConfig() throws RaplaException
    {
        Preferences preferences = facade.getSystemPreferences();
        DefaultConfiguration config = preferences.getEntry(Export2iCalPlugin.ICAL_CONFIG);
        if (config == null)
        {
            config = (DefaultConfiguration) ((PreferencesImpl) preferences).getOldPluginConfig(Export2iCalPlugin.class.getName());
        }
        return config;
    }
}
