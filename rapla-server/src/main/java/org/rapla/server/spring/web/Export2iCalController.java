package org.rapla.server.spring.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.data.FoldingWriter;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.validate.ValidationException;
import org.rapla.RaplaResources;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarNotFoundExeption;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.RaplaLocale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.rapla.plugin.export2ical.Export2iCalPlugin;
import org.rapla.plugin.export2ical.server.Export2iCalConverter;
import org.rapla.storage.StorageOperator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.SimpleTimeZone;

/**
 * Preserves the legacy iCal subscription URL contract:
 * <ul>
 *   <li>{@code /ical?file=...&user=...} — public iCal export (HARD CONSTRAINT — see PRD)</li>
 *   <li>{@code /internal_ical?file=...&user=...} — internal-network export</li>
 * </ul>
 * Subscription URLs distributed to third-party calendar clients (Outlook, Google
 * Calendar, Apple Calendar, Thunderbird) must continue to resolve byte-identically.
 */
@RestController
@RequestMapping("/rapla")
@ConditionalOnProperty(prefix = "rapla.services", name = "org.rapla.plugin.export2ical", matchIfMissing = true)
public class Export2iCalController
{
    private static final LocalDateTime FIRST_PLUGIN_START_DATE = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(0), java.time.ZoneOffset.UTC);
    private static final Logger ICAL_LOG = LoggerFactory.getLogger("rapla.ical");
    private static final Logger NOT_FOUND_LOG = LoggerFactory.getLogger("rapla.404");

    private final RaplaFacade facade;
    private final RaplaLocale raplaLocale;
    private final RaplaResources i18n;
    private final Export2iCalConverter converter;

    private final int globalDaysBefore;
    private final int globalDaysAfter;
    private final boolean globalInterval;
    private final int lastModifiedIntervall;
    private final SimpleDateFormat rfc1123DateFormat;

    public Export2iCalController(RaplaFacade facade,
                                  RaplaLocale raplaLocale,
                                  RaplaResources i18n,
                                  Export2iCalConverter converter)
    {
        this.facade = facade;
        this.raplaLocale = raplaLocale;
        this.i18n = i18n;
        this.converter = converter;

        RaplaConfiguration config;
        try
        {
            config = facade.getSystemPreferences().getEntry(Export2iCalPlugin.ICAL_CONFIG, new RaplaConfiguration());
        }
        catch (RaplaException e)
        {
            throw new RaplaInitializationException(e.getMessage(), e);
        }
        this.globalInterval = config.getChild(Export2iCalPlugin.GLOBAL_INTERVAL).getValueAsBoolean(Export2iCalPlugin.DEFAULT_globalIntervall);
        this.globalDaysBefore = config.getChild(Export2iCalPlugin.DAYS_BEFORE).getValueAsInteger(Export2iCalPlugin.DEFAULT_daysBefore);
        this.globalDaysAfter = config.getChild(Export2iCalPlugin.DAYS_AFTER).getValueAsInteger(Export2iCalPlugin.DEFAULT_daysAfter);
        this.lastModifiedIntervall = config.getChild(Export2iCalPlugin.LAST_MODIFIED_INTERVALL).getValueAsInteger(10);

        this.rfc1123DateFormat = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z", Locale.US);
        this.rfc1123DateFormat.setTimeZone(new SimpleTimeZone(0, "GMT"));
    }

    @RequestMapping(path = "/ical", method = { RequestMethod.GET, RequestMethod.HEAD })
    public void icalExport(HttpServletRequest request,
                           HttpServletResponse response,
                           @RequestParam(value = "file", required = false) String file,
                           @RequestParam(value = "user", required = false) String user) throws IOException, ServletException
    {
        renderIcal("ical", request, response, file, user);
    }

    @RequestMapping(path = "/internal_ical", method = { RequestMethod.GET, RequestMethod.HEAD })
    public void internalIcalExport(HttpServletRequest request,
                                   HttpServletResponse response,
                                   @RequestParam(value = "file", required = false) String file,
                                   @RequestParam(value = "user", required = false) String user) throws IOException, ServletException
    {
        renderIcal("internal_ical", request, response, file, user);
    }

    private void renderIcal(String path, HttpServletRequest request, HttpServletResponse response, final String filename, final String username) throws IOException, ServletException
    {
        ICAL_LOG.debug("File: {}", filename);
        ICAL_LOG.debug("User: {}", username);
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);

        StorageOperator operator = facade.getOperator();
        Map<String, Object> threadContextMap = operator.getThreadContextMap();
        try
        {
            if (path.startsWith("internal"))
            {
                threadContextMap.put("internal_request", Boolean.TRUE);
            }
            final User user;
            String message = "The calendar '" + filename + "' you tried to retrieve is not published or available for the user " + username + ".";
            try
            {
                user = facade.getUser(username);
            }
            catch (EntityNotFoundException ex)
            {
                SafePageError.write(response, 404, message, NOT_FOUND_LOG, null);
                return;
            }
            final Preferences preferences = facade.getPreferences(user);
            final CalendarModel calModel = getCalendarModel(preferences, user, filename);

            if (calModel == null)
            {
                SafePageError.write(response, 404, message, NOT_FOUND_LOG, null);
                return;
            }

            response.setHeader("Last-Modified", rfc1123DateFormat.format(getLastModified(calModel)));
            final Object isSet = calModel.getOption(Export2iCalPlugin.ICAL_EXPORT);

            if (isSet == null || isSet.equals("false"))
            {
                SafePageError.write(response, 404, message, NOT_FOUND_LOG, null);
                return;
            }

            if (request.getMethod().equals("HEAD"))
            {
                return;
            }

            Collection<Appointment> appointments = ((org.rapla.facade.SyncCalendarModel) calModel)
                    .queryAppointmentsSync(new TimeInterval(null, null));
            write(response, appointments, filename, user, null);
        }
        catch (Exception e)
        {
            // A6: generic message (no reflected user/filename), never a stack trace in the body.
            SafePageError.write(response, 500, "An error occurred while generating the calendar.", ICAL_LOG, e);
        }
        finally
        {
            threadContextMap.remove("internal_request");
        }
    }

    /**
     * Retrieves CalendarModel by username + filename, sets before / after window
     * (only if global interval is false).
     */
    private CalendarModel getCalendarModel(Preferences preferences, User user, String filename)
    {
        try
        {
            final CalendarSelectionModel calModel = facade.newCalendarModel(user);
            calModel.loadRegardingPlanningStatus(filename, facade.getSystemPreferences());

            int daysBefore = globalInterval ? globalDaysBefore : preferences.getEntryAsInteger(Export2iCalPlugin.PREF_BEFORE_DAYS, globalDaysBefore);
            int daysAfter  = globalInterval ? globalDaysAfter  : preferences.getEntryAsInteger(Export2iCalPlugin.PREF_AFTER_DAYS, globalDaysAfter);

            final LocalDateTime today = facade.today().atStartOfDay();
            calModel.setStartDate(DateTools.add(today, DateTools.IncrementSize.DAY_OF_YEAR, -daysBefore));
            calModel.setEndDate(DateTools.add(today, DateTools.IncrementSize.DAY_OF_YEAR, daysAfter));
            return calModel;
        }
        catch (CalendarNotFoundExeption ex)
        {
            return null;
        }
        catch (RaplaException e)
        {
            NOT_FOUND_LOG.error("The Calendarmodel {} could not be read for the user {} due to {}", filename, user, e.getMessage());
            return null;
        }
        catch (NullPointerException e)
        {
            return null;
        }
    }

    /**
     * H5: build a safe {@code Content-Disposition} value from the user-controlled
     * calendar name — strip CR/LF and quotes (header-injection / filename spoofing)
     * and wrap in quotes per RFC 6266.
     */
    static String contentDispositionAttachment(String filename)
    {
        String safe = filename == null ? "" : filename.replaceAll("[\\r\\n\"]", "");
        return "attachment; filename=\"" + safe + ".ics\"";
    }

    private void write(final HttpServletResponse response, final Collection<Appointment> appointments, String filename, User user, final Preferences preferences) throws RaplaException, IOException
    {
        if (filename == null)
        {
            filename = i18n.getString("default");
        }
        response.setContentType("text/calendar; charset=UTF-8");
        response.setHeader("Content-Disposition", contentDispositionAttachment(filename));
        response.setCharacterEncoding("UTF-8");
        if (appointments == null)
        {
            throw new RaplaException("Error with returning '" + filename);
        }
        final Calendar iCal = converter.createiCalender(appointments, preferences, user);
        final CalendarOutputter calOutputter = new CalendarOutputter();
        calOutputter.setValidating(true);
        StringWriter stringWriter = new StringWriter();
        final PrintWriter responseWriter = response.getWriter();
        try
        {
            calOutputter.output(iCal, new FoldingWriter(stringWriter, 255));
            String fixedIcal = sanitizeIcalOutput(stringWriter.toString());
            responseWriter.write(fixedIcal);
        }
        catch (ValidationException e)
        {
            ICAL_LOG.error("The calendar file is invalid!\n{}", e.toString());
        }
        finally
        {
            responseWriter.close();
        }
    }

    /**
     * Global last-modified by modulo: returns a fresh stamp every N days
     * (N = lastModifiedIntervall). Stable for ETag-like caching.
     */
    private LocalDateTime getGlobalLastModified()
    {
        if (lastModifiedIntervall == -1)
        {
            return FIRST_PLUGIN_START_DATE;
        }
        LocalDateTime today = DateTools.cutDate(LocalDateTime.now());
        long daysSinceStart = java.time.temporal.ChronoUnit.DAYS.between(FIRST_PLUGIN_START_DATE, today);
        return today.minusDays(daysSinceStart % lastModifiedIntervall);
    }

    private LocalDateTime getLastModified(CalendarModel calModel) throws RaplaException
    {
        LocalDateTime endDate = null;
        LocalDateTime startDate = facade.today().atStartOfDay();
        final Collection<Reservation> reservations = ((org.rapla.facade.SyncCalendarModel) calModel)
                .queryReservationsSync(new TimeInterval(startDate, endDate));
        LocalDateTime maxDate = LocalDateTime.MIN;
        for (Reservation r : reservations)
        {
            LocalDateTime lastMod = r.getLastChanged();
            if (lastMod != null && maxDate.isBefore(lastMod))
            {
                maxDate = lastMod;
            }
        }
        if (lastModifiedIntervall != -1 && DateTools.countDays(maxDate, LocalDateTime.now()) < lastModifiedIntervall)
        {
            return maxDate;
        }
        return getGlobalLastModified();
    }

    private static String sanitizeIcalOutput(String rawIcalText)
    {
        return rawIcalText
                .replaceAll("(?m)(MAILTO:[^\\r\\n]*)\\r?\\n[ \\t]+([^\\r\\n]*)", "$1$2")
                .replaceAll("(?m);CN=([^\\r\\n]*)\\r?\\n[ \\t]+", ";CN=$1")
                .replaceAll("(?m):MAILTO:\\s*", ":MAILTO:");
    }
}
