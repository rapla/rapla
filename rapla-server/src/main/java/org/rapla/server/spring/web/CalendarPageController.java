package org.rapla.server.spring.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.rapla.plugin.autoexport.server.CalendarPageGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * Preserves four legacy URL paths used by published calendar widgets and exports
 * (HARD CONSTRAINT — see PRD URL Path Preservation):
 * <ul>
 *   <li>{@code /calendar} — HTML calendar export</li>
 *   <li>{@code /calendar.csv} — CSV export</li>
 *   <li>{@code /internal_calendar} — internal-network HTML</li>
 *   <li>{@code /internal_calendar.csv} — internal-network CSV</li>
 * </ul>
 */
@RestController
@RequestMapping("/rapla")
@ConditionalOnBean(CalendarPageGenerator.class)
public class CalendarPageController
{
    private final CalendarPageGenerator generator;

    public CalendarPageController(CalendarPageGenerator generator)
    {
        this.generator = generator;
    }

    @GetMapping("/calendar")
    public void calendarHtml(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
    {
        generator.generatePage("calendar", request, response);
    }

    @GetMapping("/calendar.csv")
    public void calendarCsv(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
    {
        generator.generatePage("calendar.csv", request, response);
    }

    @GetMapping("/internal_calendar")
    public void internalCalendarHtml(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
    {
        generator.generatePage("internal_calendar", request, response);
    }

    @GetMapping("/internal_calendar.csv")
    public void internalCalendarCsv(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
    {
        generator.generatePage("internal_calendar.csv", request, response);
    }
}
