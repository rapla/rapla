package org.rapla.server.spring.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.rapla.RaplaSystemInfo;
import org.rapla.server.internal.RaplaStatusEntry;
import org.rapla.server.spring.RaplaServerProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** The status page: templates/status.mustache, restyled by the patch directory's index.css (shared with the start page) or replaced by its status.mustache. */
@RestController
public class StatusPageController
{
    private final RaplaSystemInfo i18n;
    private final RaplaServerProperties properties;
    private final PatchablePage page;

    public StatusPageController(RaplaSystemInfo i18n, RaplaServerProperties properties)
    {
        this.i18n = i18n;
        this.properties = properties;
        this.page = new PatchablePage("templates/status.mustache", Path.of(properties.getPatchDir()), "status.mustache", "index.css");
    }

    @GetMapping("/server")
    public void server(HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        if (!properties.isServiceEnabled(RaplaStatusEntry.ID))
        {
            PrintWriter out = response.getWriter();
            response.setContentType("text/html; charset=ISO-8859-1");
            out.println("Server Status disabled");
            response.setStatus(404);
            out.close();
            return;
        }
        String linkPrefix = request.getPathTranslated() != null ? "../" : "";
        Map<String, Object> model = new HashMap<>();
        model.put("cssUrl", linkPrefix + "default.css");
        String javaversion = System.getProperty("java.version");
        model.put("info", i18n.infoText(javaversion));
        response.setContentType("text/html; charset=UTF-8");
        response.getWriter().print(page.render(model));
    }
}
