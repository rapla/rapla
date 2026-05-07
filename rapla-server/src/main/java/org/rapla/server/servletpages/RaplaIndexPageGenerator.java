/**
 *
 */
package org.rapla.server.servletpages;

import org.rapla.RaplaResources;
import org.rapla.components.util.Tools;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.internal.AbstractRaplaLocale;
import org.rapla.plugin.abstractcalendar.server.AbstractHTMLCalendarPage;
import org.rapla.server.extensionpoints.HtmlMainMenu;
import org.rapla.server.internal.ServerContainerContext;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.TreeMap;

@Path("index")
@Singleton
public class RaplaIndexPageGenerator
{
    @Inject
    Map<String, HtmlMainMenu> entries;
    @Inject
    RaplaResources i18n;

    @Inject
    RaplaFacade facade;

    @Inject
    ServerContainerContext serverContainerContext;

    @Inject
    public RaplaIndexPageGenerator()
    {
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public void generatePage(@Context HttpServletRequest request, @Context HttpServletResponse response) throws IOException, ServletException
    {
        if (request.getParameter("page") == null && request.getRequestURI().endsWith("/rapla/index/"))
        {
            response.sendRedirect("../index");
        }
        response.setContentType("text/html; charset=ISO-8859-1");
        java.io.PrintWriter out = response.getWriter();
        out.println("<html>");
        out.println("  <head>");
        // add the link to the stylesheet for this page within the <head> tag
        out.println("    " + AbstractHTMLCalendarPage.getCssLine(request, "default.css"));
        // tell the html page where its favourite icon is stored
        out.println("    " + AbstractHTMLCalendarPage.getFavIconLine(request));
        out.println("    <title>");
        String title;
        final String defaultTitle = i18n.getString("rapla.title");
        try
        {
            title = Tools.createXssSafeString(facade.getSystemPreferences().getEntryAsString(AbstractRaplaLocale.TITLE, defaultTitle));
        }
        catch (RaplaException e)
        {
            title = defaultTitle;
        }

        out.println(title);
        out.println("    </title>");
        out.println("  </head>");
        out.println("  <body>");
        out.println("    <h3>");
        out.println(title);
        out.println("    </h3>");
        generateMenu(request, out);
        out.println(i18n.getString("webinfo.text"));
        out.println("  </body>");
        out.println("</html>");
        out.close();
    }

    public void generateMenu(HttpServletRequest request, PrintWriter out)
    {
        // there is an ArraList of entries that wants to be part of the HTML
        // menu we go through this ArraList,
        for (Map.Entry<String,HtmlMainMenu> entry :new TreeMap<>(entries).entrySet() )
        {
            final String key = entry.getKey();
            final RaplaMenuGenerator value = entry.getValue();
            if ( !serverContainerContext.isServiceEnabled(key) || !value.isEnabled())
            {
                continue;
            }
            out.println("<div class=\"menuEntry\">");

            value.generatePage(request, out);
            out.println("</div>");
        }
    }

}