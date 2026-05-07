package org.rapla.server.spring.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.rapla.plugin.export2ical.server.Export2iCalServlet;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

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
@ConditionalOnBean(Export2iCalServlet.class)
public class Export2iCalController
{
    private final Export2iCalServlet servlet;

    public Export2iCalController(Export2iCalServlet servlet)
    {
        this.servlet = servlet;
    }

    @RequestMapping(path = "/ical", method = {RequestMethod.GET, RequestMethod.HEAD})
    public void icalExport(HttpServletRequest request,
                           HttpServletResponse response,
                           @RequestParam(value = "file", required = false) String file,
                           @RequestParam(value = "user", required = false) String user) throws IOException, ServletException
    {
        servlet.generatePage("ical", request, response, file, user);
    }

    @RequestMapping(path = "/internal_ical", method = {RequestMethod.GET, RequestMethod.HEAD})
    public void internalIcalExport(HttpServletRequest request,
                                    HttpServletResponse response,
                                    @RequestParam(value = "file", required = false) String file,
                                    @RequestParam(value = "user", required = false) String user) throws IOException, ServletException
    {
        servlet.generatePage("internal_ical", request, response, file, user);
    }
}
