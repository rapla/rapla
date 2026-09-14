package org.rapla.server.spring.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.rapla.RaplaResources;
import org.rapla.components.util.Tools;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.internal.AbstractRaplaLocale;
import org.rapla.plugin.abstractcalendar.server.AbstractHTMLCalendarPage;
import org.rapla.server.extensionpoints.HtmlMainMenu;
import org.rapla.server.servletpages.RaplaMenuGenerator;
import org.rapla.server.spring.RaplaServerProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.TreeMap;

@RestController
public class IndexPageController
{
    private final Map<String, HtmlMainMenu> entries;
    private final RaplaResources i18n;
    private final RaplaFacade facade;
    private final RaplaServerProperties properties;

    public IndexPageController(Map<String, HtmlMainMenu> entries,
                                RaplaResources i18n,
                                RaplaFacade facade,
                                RaplaServerProperties properties)
    {
        this.entries = entries;
        this.i18n = i18n;
        this.facade = facade;
        this.properties = properties;
    }

    @GetMapping({ "/", "/index" })
    public void index(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
    {
        if (request.getParameter("page") == null && request.getRequestURI().endsWith("/rapla/index/"))
        {
            response.sendRedirect("../index");
        }
        response.setContentType("text/html; charset=ISO-8859-1");
        PrintWriter out = response.getWriter();
        out.println("<html>");
        out.println("  <head>");
        out.println("    " + AbstractHTMLCalendarPage.getCssLine(request, "default.css"));
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

    private void generateMenu(HttpServletRequest request, PrintWriter out)
    {
        for (Map.Entry<String, HtmlMainMenu> entry : new TreeMap<>(entries).entrySet())
        {
            final String key = entry.getKey();
            final RaplaMenuGenerator value = entry.getValue();
            if (!properties.isServiceEnabled(key) || !value.isEnabled())
            {
                continue;
            }
            out.println("<div class=\"menuEntry\">");
            value.generatePage(request, out);
            out.println("</div>");
        }
    }
}
