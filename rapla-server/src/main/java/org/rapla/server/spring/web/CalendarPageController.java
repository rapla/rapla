package org.rapla.server.spring.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.rapla.RaplaResources;
import org.rapla.components.util.IOUtil;
import org.rapla.components.util.ParseDateException;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.User;
import org.rapla.entities.configuration.CalendarModelConfiguration;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaMap;
import org.rapla.entities.domain.Allocatable;
import org.rapla.facade.CalendarNotFoundExeption;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.internal.AbstractRaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.plugin.abstractcalendar.server.AbstractHTMLCalendarPage;
import org.rapla.plugin.autoexport.AutoExportPlugin;
import org.rapla.plugin.autoexport.AutoExportResources;
import org.rapla.plugin.urlencryption.UrlEncryptionPlugin;
import org.rapla.server.extensionpoints.HTMLViewPage;
import org.rapla.storage.StorageOperator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * Preserves four legacy URL paths used by published calendar widgets and exports
 * (HARD CONSTRAINT — see PRD URL Path Preservation):
 * <ul>
 *   <li>{@code /calendar} — HTML calendar export</li>
 *   <li>{@code /calendar.csv} — CSV export</li>
 *   <li>{@code /internal_calendar} — internal-network HTML</li>
 *   <li>{@code /internal_calendar.csv} — internal-network CSV</li>
 * </ul>
 *
 * <p>Dispatches into the {@link HTMLViewPage} plugin SPI for the actual rendering.
 *
 * <p>USAGE: {@code rapla/calendar?user=<username>&file=<export_name>}.
 * Optional params: {@code &hide_nav}, {@code &day=<day>}, {@code &month=<month>},
 * {@code &year=<year>}, {@code &today}, {@code &allocatable_id=<id>}.
 */
@RestController
@RequestMapping("/rapla")
@ConditionalOnProperty(prefix = "rapla.services", name = "org.rapla.plugin.autoexport", matchIfMissing = true)
public class CalendarPageController
{
    private final Map<String, Supplier<HTMLViewPage>> factoryMap;
    private final RaplaFacade facade;
    private final Logger logger;
    private final RaplaLocale raplaLocale;
    private final RaplaResources i18n;
    private final AutoExportResources autoexportI18n;

    public CalendarPageController(Map<String, Supplier<HTMLViewPage>> factoryMap,
                                   RaplaFacade facade,
                                   Logger logger,
                                   RaplaLocale raplaLocale,
                                   RaplaResources i18n,
                                   AutoExportResources autoexportI18n)
    {
        this.factoryMap = factoryMap;
        this.facade = facade;
        this.logger = logger;
        this.raplaLocale = raplaLocale;
        this.i18n = i18n;
        this.autoexportI18n = autoexportI18n;
    }

    @GetMapping("/calendar")
    public void calendarHtml(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
    {
        renderCalendar("calendar", request, response);
    }

    @GetMapping("/calendar.csv")
    public void calendarCsv(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
    {
        renderCalendar("calendar.csv", request, response);
    }

    @GetMapping("/internal_calendar")
    public void internalCalendarHtml(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
    {
        renderCalendar("internal_calendar", request, response);
    }

    @GetMapping("/internal_calendar.csv")
    public void internalCalendarCsv(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
    {
        renderCalendar("internal_calendar.csv", request, response);
    }

    private void renderCalendar(String path, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
    {
        StorageOperator operator = facade.getOperator();
        Map<String, Object> threadContextMap = operator.getThreadContextMap();
        try
        {
            if (path.startsWith("internal"))
            {
                threadContextMap.put("internal_request", Boolean.TRUE);
            }
            String username = request.getParameter("user");
            if (username == null)
            {
                User[] users = facade.getUsers();
                final Boolean entryAsBoolean = facade.getSystemPreferences().getEntryAsBoolean(AutoExportPlugin.SHOW_CALENDAR_LIST_IN_HTML_MENU, false);
                if (entryAsBoolean)
                {
                    generatePageList(users, request, response);
                }
                else
                {
                    PrintWriter out = response.getWriter();
                    response.setStatus(404);
                    out.println("Calender menu disabled. You used the wrong url.");
                    out.close();
                }
                return;
            }
            String filename = request.getParameter("file");

            CalendarSelectionModel model;
            User user;
            try
            {
                user = facade.getUser(username);
            }
            catch (EntityNotFoundException ex)
            {
                String message = "404 Calendar not available. User not found ";
                write404(response, message);
                logger.getChildLogger("html.404").warn("404 Username not found ");
                return;
            }
            try
            {
                model = facade.newCalendarModel(user);
                model.loadRegardingPlanningStatus(filename, facade.getSystemPreferences());
            }
            catch (CalendarNotFoundExeption ex)
            {
                String message = "404 Calendar not found for " + user.getId();
                write404(response, message);
                return;
            }
            final Object isSet = model.getOption(AutoExportPlugin.HTML_EXPORT);
            if (isSet == null || isSet.equals("false"))
            {
                String message = "404 Calendar not published for " + user.getId();
                write404(response, message);
                return;
            }
            String allocatableId = request.getParameter("allocatable_id");
            if (allocatableId != null)
            {
                Collection<Allocatable> selectedAllocatables = model.getSelectedAllocatablesAsList();
                Allocatable foundAlloc = null;
                for (Allocatable alloc : selectedAllocatables)
                {
                    if (alloc.getId().equals(allocatableId))
                    {
                        foundAlloc = alloc;
                        break;
                    }
                }
                if (allocatableId.isEmpty() || foundAlloc != null)
                {
                    request.setAttribute("allocatable_id", allocatableId);
                    if (foundAlloc != null)
                    {
                        model.setSelectedObjects(Collections.singleton(foundAlloc));
                    }
                }
                else
                {
                    String message = "404 Ressource with id '" + allocatableId + "' not found for calendar " + user.getId() + "/" + filename;
                    write404(response, message);
                    return;
                }
            }

            final String viewId = model.getViewId();
            final Supplier<HTMLViewPage> htmlViewPageProvider = factoryMap.get(viewId);

            if (htmlViewPageProvider != null)
            {
                HTMLViewPage currentView = htmlViewPageProvider.get();
                if (currentView != null)
                {
                    try
                    {
                        currentView.generatePage(request.getServletContext(), request, response, model);
                    }
                    catch (ServletException ex)
                    {
                        Throwable cause = ex.getCause();
                        if (cause instanceof ParseDateException)
                        {
                            write404(response, cause.getMessage() + " in calendar " + user + "/" + filename);
                        }
                        else
                        {
                            throw ex;
                        }
                    }
                }
                else
                {
                    write404(response,
                            "No view available for calendar " + user + "/" + filename + ". Rapla has currently no html support for the view with the id '"
                                    + viewId + "'.");
                }
            }
            else
            {
                writeError(response, "No view available for exportfile '" + filename + "'. Please install and select the plugin for " + viewId);
            }
        }
        catch (Exception ex)
        {
            writeStacktrace(response, ex);
            throw new ServletException(ex);
        }
        finally
        {
            threadContextMap.remove("internal_request");
        }
    }

    private void generatePageList(User[] users, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
    {
        PrintWriter out = response.getWriter();
        try
        {
            response.setContentType("text/html; charset=" + raplaLocale.getCharsetForHtml());
            SortedSet<User> sortedUsers = new TreeSet<>(User.USER_COMPARATOR);
            sortedUsers.addAll(Arrays.asList(users));

            String calendarName = facade.getSystemPreferences().getEntryAsString(AbstractRaplaLocale.TITLE, i18n.getString("rapla.title"));
            out.println("<html>");
            out.println("<head>");
            out.println("<title>" + calendarName + "</title>");
            String charset = raplaLocale.getCharsetForHtml();
            out.println("  <meta HTTP-EQUIV=\"Content-Type\" content=\"text/html; charset=" + charset + "\">");
            out.println("</head>");
            out.println("<body>");
            out.println("<h2>" + autoexportI18n.getString("webserver") + ": " + calendarName + "</h2>");

            for (User user : sortedUsers)
            {
                Preferences preferences = facade.getPreferences(user);
                LinkedHashMap<String, CalendarModelConfiguration> completeMap = new LinkedHashMap<>();
                CalendarModelConfiguration defaultConf = preferences.getEntry(CalendarModelConfiguration.CONFIG_ENTRY);
                if (defaultConf != null && !isEncrypted(defaultConf))
                {
                    completeMap.put("", defaultConf);
                }

                final RaplaMap<CalendarModelConfiguration> raplaMap = preferences.getEntry(AutoExportPlugin.PLUGIN_ENTRY);
                if (raplaMap != null)
                {
                    for (Map.Entry<String, CalendarModelConfiguration> entry : raplaMap.entrySet())
                    {
                        CalendarModelConfiguration value = entry.getValue();
                        if (!isEncrypted(value))
                        {
                            completeMap.put(entry.getKey(), value);
                        }
                    }
                }
                SortedMap<String, CalendarModelConfiguration> sortedMap = new TreeMap<>(new TitleComparator(completeMap));
                sortedMap.putAll(completeMap);
                Iterator<Map.Entry<String, CalendarModelConfiguration>> it = sortedMap.entrySet().iterator();

                int count = 0;
                while (it.hasNext())
                {
                    Map.Entry<String, CalendarModelConfiguration> entry = it.next();
                    String key = entry.getKey();
                    CalendarModelConfiguration conf = entry.getValue();
                    final Object isSet = conf.getOptionMap().get(AutoExportPlugin.HTML_EXPORT);
                    if (isSet != null && isSet.equals("false"))
                    {
                        it.remove();
                        continue;
                    }
                    if (count == 0)
                    {
                        String userName = user.getName();
                        if (userName == null || userName.trim().length() == 0)
                            userName = user.getUsername();
                        out.println("<h3>" + userName + "</h3>");
                        out.println("<ul>");
                    }
                    count++;
                    String title = getTitle(key, conf);

                    String filename = URLEncoder.encode(key, "UTF-8");
                    out.print("<li>");
                    String baseUrl = getBaseUrl(request);

                    String link = baseUrl + "?user=" + user.getUsername();
                    if (filename != null && !filename.isEmpty())
                    {
                        link += "&file=" + filename;
                    }
                    link += "&details=*";
                    link += "&folder=true";
                    out.print("<a href=\"" + link + "\">");
                    out.print(title);
                    out.print("</a>");
                    out.println("</li>");
                }
                if (count > 0)
                {
                    out.println("</ul>");
                }
            }
            out.println("</body>");
            out.println("</html>");
        }
        catch (Exception ex)
        {
            out.println(IOUtil.getStackTraceAsString(ex));
            throw new ServletException(ex);
        }
        finally
        {
            out.close();
        }
    }

    private boolean isEncrypted(CalendarModelConfiguration conf)
    {
        String encyrptionSelected = conf.getOptionMap().get(UrlEncryptionPlugin.URL_ENCRYPTION);
        return "true".equals(encyrptionSelected);
    }

    private String getTitle(String key, CalendarModelConfiguration conf)
    {
        String title = conf.getTitle();
        if (title == null || title.trim().length() == 0)
        {
            title = key;
        }
        return title;
    }

    @NotNull
    private String getBaseUrl(HttpServletRequest request)
    {
        return AbstractHTMLCalendarPage.getUrl(request, "calendar");
    }

    private void writeStacktrace(HttpServletResponse response, Exception ex) throws IOException
    {
        String charsetNonUtf = raplaLocale.getCharsetForHtml();
        response.setContentType("text/html; charset=" + charsetNonUtf);
        PrintWriter out = response.getWriter();
        out.println(IOUtil.getStackTraceAsString(ex));
        out.close();
    }

    private void write404(HttpServletResponse response, String message) throws IOException
    {
        response.setStatus(404);
        response.getWriter().print(message);
        logger.getChildLogger("html.404").warn(message);
        response.getWriter().close();
    }

    private void writeError(HttpServletResponse response, String message) throws IOException
    {
        response.setStatus(500);
        response.setContentType("text/html; charset=" + raplaLocale.getCharsetForHtml());
        PrintWriter out = response.getWriter();
        out.println(message);
        out.close();
    }

    private class TitleComparator implements Comparator<String>
    {
        private final Map<String, CalendarModelConfiguration> base;

        TitleComparator(Map<String, CalendarModelConfiguration> base)
        {
            this.base = base;
        }

        @Override
        public int compare(String a, String b)
        {
            final String title1 = getTitle(a, base.get(a));
            final String title2 = getTitle(b, base.get(b));
            int result = title1.compareToIgnoreCase(title2);
            if (result != 0) return result;
            return a.compareToIgnoreCase(b);
        }
    }
}
