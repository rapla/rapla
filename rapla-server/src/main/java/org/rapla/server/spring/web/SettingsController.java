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
import org.rapla.rest.SettingsService;
import org.rapla.rest.dto.SystemSettings;
import org.rapla.rest.dto.UserSettings;
import org.rapla.server.RemoteSession;
import org.rapla.storage.RaplaSecurityException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnBean(RemoteSession.class)
public class SettingsController implements SettingsService
{
    private final RaplaFacade facade;
    private final RemoteSession session;
    private final HttpServletRequest request;

    public SettingsController(RaplaFacade facade, RemoteSession session, HttpServletRequest request)
    {
        this.facade = facade;
        this.session = session;
        this.request = request;
    }

    @Override
    public SystemSettings getSystem() throws RaplaException
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

    @Override
    public SystemSettings setSystem(SystemSettings body) throws RaplaException
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
        return getSystem();
    }

    @Override
    public RaplaConfiguration getCalendar() throws RaplaException
    {
        session.checkAndGetUser(request);
        RaplaConfiguration c = facade.getSystemPreferences().getEntry(CalendarOptionsImpl.CALENDAR_OPTIONS);
        return c != null ? c : new RaplaConfiguration("calendar");
    }

    @Override
    public RaplaConfiguration setCalendar(RaplaConfiguration body) throws RaplaException
    {
        User user = session.checkAndGetUser(request);
        if (!user.isAdmin())
        {
            throw new RaplaSecurityException("Only admins can change system calendar defaults");
        }
        Preferences edit = facade.edit(facade.getSystemPreferences());
        edit.putEntry(CalendarOptionsImpl.CALENDAR_OPTIONS, body);
        facade.store(edit);
        return getCalendar();
    }

    @Override
    public UserSettings getMe() throws RaplaException
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

    @Override
    public UserSettings setMe(UserSettings body) throws RaplaException
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
        return getMe();
    }

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
