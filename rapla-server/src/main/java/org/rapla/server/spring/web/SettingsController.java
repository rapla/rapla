package org.rapla.server.spring.web;

import jakarta.servlet.http.HttpServletRequest;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.facade.internal.CalendarOptionsImpl;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.internal.AbstractRaplaLocale;
import org.rapla.rest.dto.SystemSettings;
import org.rapla.rest.dto.UserSettings;
import org.rapla.server.RemoteSession;
import org.rapla.storage.RaplaSecurityException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST surface for non-plugin Rapla settings. Replaces the bulk-bootstrap
 * preference reads in the Swing client's RaplaStartOption, CalendarOption,
 * WarningsOption, and UserOption screens. Three scopes:
 *
 * <ul>
 *   <li>{@code /settings/system}  — deployment-wide (title, timezone, locale,
 *       charsets, refresh-interval). GET: any authenticated user; PUT: admin.</li>
 *   <li>{@code /settings/calendar} — system-default calendar view options
 *       (CALENDAR_OPTIONS blob). GET: any user; PUT: admin.</li>
 *   <li>{@code /settings/me}      — per-user preferences (language + UI warnings).
 *       Self-scope: caller's own preferences only.</li>
 * </ul>
 *
 * <p>Each PUT goes through {@link RaplaFacade#edit} / {@link RaplaFacade#store},
 * the same write path the Swing screens used to take. {@code null}-valued
 * strings remove the entry (lets future default changes propagate).
 */
@RestController
@ConditionalOnBean(RemoteSession.class)
@RequestMapping("/settings")
public class SettingsController
{
    private final RaplaFacade facade;
    private final RemoteSession session;

    public SettingsController(RaplaFacade facade, RemoteSession session)
    {
        this.facade = facade;
        this.session = session;
    }

    // ============ system (admin) ============

    @GetMapping("/system")
    public SystemSettings getSystem(HttpServletRequest request) throws RaplaException
    {
        session.checkAndGetUser(request);
        Preferences p = facade.getSystemPreferences();
        return new SystemSettings(
                p.getEntryAsString(AbstractRaplaLocale.TITLE, ""),
                p.getEntryAsString(AbstractRaplaLocale.TIMEZONE, ""),
                p.getEntryAsString(AbstractRaplaLocale.LOCALE, ""),
                p.getEntryAsString(AbstractRaplaLocale.CSV_CHARSET, AbstractRaplaLocale.CSV_CHARSET_DEFAULT),
                p.getEntryAsString(AbstractRaplaLocale.HTML_CHARSET, AbstractRaplaLocale.HTML_CHARSET_DEFAULT),
                p.getEntryAsInteger(ClientFacade.REFRESH_INTERVAL_ENTRY, ClientFacade.REFRESH_INTERVAL_DEFAULT)
        );
    }

    @PutMapping("/system")
    public SystemSettings setSystem(@RequestBody SystemSettings body, HttpServletRequest request) throws RaplaException
    {
        User user = session.checkAndGetUser(request);
        if (!user.isAdmin())
        {
            throw new RaplaSecurityException("Only admins can change system settings");
        }
        Preferences edit = facade.edit(facade.getSystemPreferences());
        putOrRemove(edit, AbstractRaplaLocale.TITLE, body.title());
        putOrRemove(edit, AbstractRaplaLocale.TIMEZONE, body.timezone());
        putOrRemove(edit, AbstractRaplaLocale.LOCALE, body.locale());
        putOrRemoveDefault(edit, AbstractRaplaLocale.CSV_CHARSET, body.csvCharset(), AbstractRaplaLocale.CSV_CHARSET_DEFAULT);
        putOrRemoveDefault(edit, AbstractRaplaLocale.HTML_CHARSET, body.htmlCharset(), AbstractRaplaLocale.HTML_CHARSET_DEFAULT);
        putOrRemoveIntDefault(edit, ClientFacade.REFRESH_INTERVAL_ENTRY, body.refreshIntervalMs(), ClientFacade.REFRESH_INTERVAL_DEFAULT);
        facade.store(edit);
        return getSystem(request);
    }

    // ============ calendar (admin write / any user read) ============

    @GetMapping("/calendar")
    public RaplaConfiguration getCalendar(HttpServletRequest request) throws RaplaException
    {
        session.checkAndGetUser(request);
        RaplaConfiguration c = facade.getSystemPreferences().getEntry(CalendarOptionsImpl.CALENDAR_OPTIONS);
        return c != null ? c : new RaplaConfiguration("calendar");
    }

    @PutMapping("/calendar")
    public RaplaConfiguration setCalendar(@RequestBody RaplaConfiguration body, HttpServletRequest request) throws RaplaException
    {
        User user = session.checkAndGetUser(request);
        if (!user.isAdmin())
        {
            throw new RaplaSecurityException("Only admins can change system calendar defaults");
        }
        Preferences edit = facade.edit(facade.getSystemPreferences());
        edit.putEntry(CalendarOptionsImpl.CALENDAR_OPTIONS, body);
        facade.store(edit);
        return getCalendar(request);
    }

    // ============ me (per-user) ============

    @GetMapping("/me")
    public UserSettings getMe(HttpServletRequest request) throws RaplaException
    {
        User user = session.checkAndGetUser(request);
        Preferences p = facade.getPreferences(user);
        return new UserSettings(
                p.getEntryAsString(RaplaLocale.LANGUAGE_ENTRY, ""),
                p.getEntryAsBoolean(CalendarOptionsImpl.SHOW_CONFLICT_WARNING, true),
                p.getEntryAsBoolean(CalendarOptionsImpl.SHOW_NOT_IN_CALENDAR_WARNING, true),
                p.getEntryAsBoolean(CalendarOptionsImpl.SHOW_ABORT_EDIT_WARNING, true),
                p.getEntryAsBoolean(CalendarOptionsImpl.SHOW_HOLIDAY_WARNING, true),
                p.getEntryAsBoolean(CalendarOptionsImpl.SHOW_HOLIDAY_WARNING_SINGLE_APPOINTMENT, true)
        );
    }

    @PutMapping("/me")
    public UserSettings setMe(@RequestBody UserSettings body, HttpServletRequest request) throws RaplaException
    {
        User user = session.checkAndGetUser(request);
        Preferences edit = facade.edit(facade.getPreferences(user));
        putOrRemove(edit, RaplaLocale.LANGUAGE_ENTRY, body.language());
        putOrRemoveBool(edit, CalendarOptionsImpl.SHOW_CONFLICT_WARNING, body.showConflictWarning(), true);
        putOrRemoveBool(edit, CalendarOptionsImpl.SHOW_NOT_IN_CALENDAR_WARNING, body.showNotInCalendarWarning(), true);
        putOrRemoveBool(edit, CalendarOptionsImpl.SHOW_ABORT_EDIT_WARNING, body.showAbortEditWarning(), true);
        putOrRemoveBool(edit, CalendarOptionsImpl.SHOW_HOLIDAY_WARNING, body.showHolidayWarning(), true);
        putOrRemoveBool(edit, CalendarOptionsImpl.SHOW_HOLIDAY_WARNING_SINGLE_APPOINTMENT, body.showHolidayWarningSingleAppointment(), true);
        facade.store(edit);
        return getMe(request);
    }

    // ============ helpers (mirror AbstractPluginPreferencesPanel.putOrRemove) ============

    private static void putOrRemove(Preferences prefs, org.rapla.framework.TypedComponentRole<String> role, String value)
    {
        if (value == null || value.isEmpty()) prefs.removeEntry(role.getId());
        else                                  prefs.putEntry(role, value);
    }

    private static void putOrRemoveDefault(Preferences prefs, org.rapla.framework.TypedComponentRole<String> role,
                                            String value, String defaultValue)
    {
        if (value == null || value.equals(defaultValue)) prefs.removeEntry(role.getId());
        else                                              prefs.putEntry(role, value);
    }

    private static void putOrRemoveIntDefault(Preferences prefs, org.rapla.framework.TypedComponentRole<Integer> role,
                                               int value, int defaultValue)
    {
        if (value == defaultValue) prefs.removeEntry(role.getId());
        else                       prefs.putEntry(role, value);
    }

    private static void putOrRemoveBool(Preferences prefs, org.rapla.framework.TypedComponentRole<Boolean> role,
                                         boolean value, boolean defaultValue)
    {
        if (value == defaultValue) prefs.removeEntry(role.getId());
        else                       prefs.putEntry(role, value);
    }
}
