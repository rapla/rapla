package org.rapla.server.spring.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.rapla.RaplaResources;
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
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** The start page: templates/index.mustache, restyled by the patch directory's index.css or replaced by its index.mustache. */
@RestController
public class IndexPageController
{
    private final Map<String, HtmlMainMenu> entries;
    private final RaplaResources i18n;
    private final RaplaFacade facade;
    private final RaplaServerProperties properties;
    private final PatchablePage page;

    public IndexPageController(Map<String, HtmlMainMenu> entries,
                                RaplaResources i18n,
                                RaplaFacade facade,
                                RaplaServerProperties properties)
    {
        this.entries = entries;
        this.i18n = i18n;
        this.facade = facade;
        this.properties = properties;
        this.page = new PatchablePage("templates/index.mustache", Path.of(properties.getPatchDir()), "index.mustache", "index.css");
    }

    @GetMapping({ "/", "/index", "/rapla/index" })
    public void index(HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        if (request.getParameter("page") == null && request.getRequestURI().endsWith("/rapla/index/"))
        {
            response.sendRedirect("../index");
        }
        String title;
        final String defaultTitle = i18n.getString("rapla.title");
        try
        {
            title = facade.getSystemPreferences().getEntryAsString(AbstractRaplaLocale.TITLE, defaultTitle);
        }
        catch (RaplaException e)
        {
            title = defaultTitle;
        }
        Map<String, Object> model = new HashMap<>();
        model.put("cssUrl", AbstractHTMLCalendarPage.getUrl(request, "default.css"));
        model.put("faviconUrl", AbstractHTMLCalendarPage.getUrl(request, "images/favicon.ico"));
        model.put("title", title);
        model.put("menu", menu(request));
        model.put("info", i18n.getString("webinfo.text"));
        response.setContentType("text/html; charset=UTF-8");
        response.getWriter().print(page.render(model));
    }

    private List<Map<String, Object>> menu(HttpServletRequest request)
    {
        List<Map<String, Object>> menu = new ArrayList<>();
        for (Map.Entry<String, HtmlMainMenu> entry : new TreeMap<>(entries).entrySet())
        {
            final String key = entry.getKey();
            final RaplaMenuGenerator value = entry.getValue();
            if (!properties.isServiceEnabled(key) || !value.isEnabled())
            {
                continue;
            }
            StringWriter html = new StringWriter();
            value.generatePage(request, new PrintWriter(html));
            menu.add(Map.of("html", html.toString()));
        }
        return menu;
    }
}
