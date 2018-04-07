/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/
package org.rapla.plugin.autoexport.server;

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
import org.rapla.server.extensionpoints.HTMLViewPage;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import java.io.IOException;
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

/******* USAGE: ************
 * ReadOnly calendarview view.
 * You will need the autoexport plugin to createInfoDialog a calendarview-view.
 *
 * Call:
 * rapla/calendar?user=<username>&file=<export_name>
 *
 * Optional Parameters:
 *
 * &hide_nav: will hide the navigation bar.
 * &day=<day>:  int-value of the day of month that should be displayed
 * &month=<month>:  int-value of the month
 * &year=<year>:  int-value of the year
 * &today:  will set the view to the current day. Ignores day, month and year
 */
@Path(AutoExportPlugin.CALENDAR_GENERATOR)
@Singleton
public class CalendarPageGenerator
{
    @Inject 
    Map<String, Provider<HTMLViewPage>> factoryMap;
    @Inject 
    RaplaFacade facade;
    @Inject 
    Logger logger;
    @Inject 
    RaplaLocale raplaLocale;
    @Inject 
    RaplaResources i18n;
    @Inject 
    AutoExportResources autoexportI18n;

    @Inject 
    public CalendarPageGenerator()
    {
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

    class TitleComparator implements Comparator<String>
    {

        Map<String, CalendarModelConfiguration> base;

        public TitleComparator(Map<String, CalendarModelConfiguration> base)
        {
            this.base = base;
        }

        public int compare(String a, String b)
        {

            final String title1 = getTitle(a, base.get(a));
            final String title2 = getTitle(b, base.get(b));
            int result = title1.compareToIgnoreCase(title2);
            if (result != 0)
            {
                return result;
            }
            return a.compareToIgnoreCase(b);

        }
    }

    private void generatePageList(User[] users,  HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException
    {
        java.io.PrintWriter out = response.getWriter();
        try
        {
            response.setContentType("text/html; charset=" + raplaLocale.getCharsetNonUtf());

            SortedSet<User> sortedUsers = new TreeSet<>(User.USER_COMPARATOR);
            sortedUsers.addAll(Arrays.asList(users));

            String calendarName = facade.getSystemPreferences().getEntryAsString(AbstractRaplaLocale.TITLE, i18n.getString("rapla.title"));
            out.println("<html>");
            out.println("<head>");
            out.println("<title>" + calendarName + "</title>");
            String charset = raplaLocale.getCharsetNonUtf();//
    		out.println("  <meta HTTP-EQUIV=\"Content-Type\" content=\"text/html; charset=" + charset + "\">");
            out.println("</head>");
            out.println("<body>");

            out.println("<h2>" + autoexportI18n.getString("webserver") + ": " + calendarName + "</h2>");

            for (User user : sortedUsers)
            {
                Preferences preferences = facade.getPreferences(user);
                LinkedHashMap<String, CalendarModelConfiguration> completeMap = new LinkedHashMap<>();
                CalendarModelConfiguration defaultConf = preferences.getEntry(CalendarModelConfiguration.CONFIG_ENTRY);
                if (defaultConf != null)
                {
                    completeMap.put(i18n.getString("default"), defaultConf);
                }

                final RaplaMap<CalendarModelConfiguration> raplaMap = preferences.getEntry(AutoExportPlugin.PLUGIN_ENTRY);
                if (raplaMap != null)
                {
                    for (Map.Entry<String, CalendarModelConfiguration> entry : raplaMap.entrySet())
                    {
                        CalendarModelConfiguration value = entry.getValue();
                        completeMap.put(entry.getKey(), value);
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
                        out.println("<h3>" + userName + "</h3>"); //BJO
                        out.println("<ul>");
                    }
                    count++;
                    String title = getTitle(key, conf);

                    String filename = URLEncoder.encode(key, "UTF-8");
                    out.print("<li>");
                    String test = AbstractHTMLCalendarPage.getUrl( request, "rapla/calendar");
                    out.print("<a href=\""+test+"?user=" + user.getUsername() + "&file=" + filename + "&details=*" + "&folder=true" + "\">");
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

    @GET
    //@Produces("text/html;charset=ISO-8859-1")
    @Produces("text/html;UTF-8")
    public void generatePage(@Context HttpServletRequest request, @Context HttpServletResponse response) throws IOException, ServletException
    {
        try
        {
            String username = request.getParameter("user");
            if (username == null)
            {
                User[] users;
                // TODO add special param if you want to request the calendar lists for a user
                if (username != null)
                {
                    users = new User[] { facade.getUser(username) };
                }
                else
                {
                    users = facade.getUsers();
                }
                final Boolean entryAsBoolean = facade.getSystemPreferences().getEntryAsBoolean(AutoExportPlugin.SHOW_CALENDAR_LIST_IN_HTML_MENU, true);
                if ( entryAsBoolean)
                {
                    generatePageList(users, request, response);
                }
                else
                {
                    java.io.PrintWriter out = response.getWriter();
                    response.setStatus( 404);
                    out.println("Calender menu disabled. You used the wrong url.");
                    out.close();
                }
                return;
            }
            String filename = request.getParameter("file");

            CalendarSelectionModel model = null;
            User user;
            try
            {
                user = facade.getUser(username);
            }
            catch (EntityNotFoundException ex)
            {
                String message = "404 Calendar not availabe  " + username + "/" + filename;
                write404(response, message);
                logger.getChildLogger("html.404").warn("404 User not found " + username);
                return;
            }
            try
            {
                model = facade.newCalendarModel(user);
                model.load(filename);
            }
            catch (CalendarNotFoundExeption ex)
            {
                String message = "404 Calendar not availabe  " + user + "/" + filename;
                write404(response, message);
                return;
            }
            String allocatableId = request.getParameter("allocatable_id");
            if (allocatableId != null)
            {
                Collection<Allocatable> selectedAllocatables =model.getSelectedAllocatablesAsList();
                Allocatable foundAlloc = null;
                for (Allocatable alloc : selectedAllocatables)
                {
                    if (alloc.getId().equals(allocatableId))
                    {
                        foundAlloc = alloc;
                        break;
                    }
                }
                if (foundAlloc != null)
                {
                    model.setSelectedObjects(Collections.singleton(foundAlloc));
                    request.setAttribute("allocatable_id", allocatableId);
                }
                else
                {
                    String message = "404 allocatable with id '" + allocatableId + "' not found for calendar " + user + "/" + filename;
                    write404(response, message);
                    return;
                }
            }
            final Object isSet = model.getOption(AutoExportPlugin.HTML_EXPORT);
            if (isSet == null || isSet.equals("false"))
            {
                String message = "404 Calendar not published " + username + "/" + filename;
                write404(response, message);
                return;
            }

            final String viewId = model.getViewId();
            final Provider<HTMLViewPage> htmlViewPageProvider = factoryMap.get(viewId);

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

    }

    private void writeStacktrace(HttpServletResponse response, Exception ex) throws IOException
    {
        String charsetNonUtf = raplaLocale.getCharsetNonUtf();
        response.setContentType("text/html; charset=" + charsetNonUtf);
        java.io.PrintWriter out = response.getWriter();
        out.println(IOUtil.getStackTraceAsString(ex));
        out.close();
    }

    protected void write404(HttpServletResponse response, String message) throws IOException
    {
        response.setStatus(404);
        response.getWriter().print(message);
        logger.getChildLogger("html.404").warn(message);
        response.getWriter().close();
    }

    private void writeError(HttpServletResponse response, String message) throws IOException
    {
        response.setStatus(500);
        response.setContentType("text/html; charset=" + raplaLocale.getCharsetNonUtf());
        java.io.PrintWriter out = response.getWriter();
        out.println(message);
        out.close();
    }

}
