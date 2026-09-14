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

@RestController
public class StatusPageController
{
    private final RaplaSystemInfo i18n;
    private final RaplaServerProperties properties;

    public StatusPageController(RaplaSystemInfo i18n, RaplaServerProperties properties)
    {
        this.i18n = i18n;
        this.properties = properties;
    }

    @GetMapping("/server")
    public void server(HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        PrintWriter out = response.getWriter();
        response.setContentType("text/html; charset=ISO-8859-1");
        if (!properties.isServiceEnabled(RaplaStatusEntry.ID))
        {
            out.println("Server Status disabled");
            response.setStatus(404);
            out.close();
            return;
        }
        String linkPrefix = request.getPathTranslated() != null ? "../" : "";

        out.println("<html>");
        out.println("<head>");
        out.println("  <link REL=\"stylesheet\" href=\"" + linkPrefix + "default.css\" type=\"text/css\">");
        out.println("  <title>Rapla Server status</title>");
        out.println("</head>");
        out.println("<body>");
        String javaversion = System.getProperty("java.version");
        out.println("<p>Server running </p>" + i18n.infoText(javaversion));
        out.println("<hr>");
        out.println("</body>");
        out.println("</html>");
        out.close();
    }
}
