package org.rapla.server.spring.web;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.facade.RaplaFacade;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.plugin.autoexport.AutoExportPlugin;
import org.rapla.scheduler.sync.SynchronizedCompletablePromise;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Security audit PH4/HC1 — resource names on the public HTML calendar exports (/rapla/calendar, /rapla/internal_calendar)
 * are escaped at every sink, and a name without special characters renders exactly as before.
 * Sinks: page title/heading (allocatable_id), resource link list, day-resource and compact-day column headers.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class CalendarExportEscapingTest
{
    private static final String XSS = "<script>alert(1)</script>";
    private static final String XSS_ESCAPED = "&lt;script&gt;alert(1)&lt;/script&gt;";
    private static final String NORMAL = "Normal Room";
    private static final String WEEK = "year=2026&month=06&day=01";

    @TempDir
    static Path tempDir;
    static Path dataFile;
    static boolean seeded;
    static String xssId;
    static String normalId;

    @BeforeAll
    static void copyTestData() throws Exception
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = CalendarExportEscapingTest.class.getResourceAsStream("/testdefault.xml"))
        {
            assertNotNull(in);
            Files.copy(in, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry)
    {
        registry.add("rapla.file-datasources.raplafile", () -> dataFile.toAbsolutePath().toString());
    }

    @Autowired
    MockMvc mockMvc;
    @Autowired
    CachableStorageOperator operator;
    @Autowired
    RaplaFacade facade;

    @BeforeEach
    void seedOnce() throws Exception
    {
        if (seeded) return;
        User homer = operator.getUser("homer");
        Allocatable xss = room(XSS, homer);
        Allocatable normal = room(NORMAL, homer);
        xssId = xss.getId();
        normalId = normal.getId();

        DynamicType eventType = facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION)[0];
        Reservation r = facade.newReservation(eventType.newClassification(), homer);
        Appointment app = facade.newAppointmentWithUser(LocalDateTime.of(2026, 6, 1, 10, 0), LocalDateTime.of(2026, 6, 1, 11, 0), homer);
        r.addAppointment(app);
        r.addAllocatable(xss);
        r.addAllocatable(normal);
        facade.storeObjects(new Entity[] { r });

        export(homer, "xssweek", "week", xss, normal);
        export(homer, "xssday", "day_resource", xss, normal);
        export(homer, "xsstimeslot", "day_timeslot", xss, normal);
        export(homer, "noviewplugin", "no-such-view", normal);
        seeded = true;
    }

    private Allocatable room(String name, User owner) throws Exception
    {
        Classification c = operator.getDynamicType("room").newClassification();
        c.setValue("name", name);
        Allocatable a = facade.newAllocatable(c, owner);
        facade.storeObjects(new Entity[] { a });
        return a;
    }

    private void export(User user, String file, String viewId, Allocatable... allocatables) throws Exception
    {
        CalendarSelectionModel model = facade.newCalendarModel(user);
        model.setViewId(viewId);
        model.setSelectedObjects(List.of((Object[]) allocatables));
        model.setOption(AutoExportPlugin.HTML_EXPORT, "true");
        SynchronizedCompletablePromise.waitFor(model.save(file), 10000);
    }

    private String page(String path, String query) throws Exception
    {
        return mockMvc.perform(get(path + "?user=homer&" + query)).andReturn().getResponse().getContentAsString();
    }

    private static void contains(String body, String expected)
    {
        assertTrue(body.contains(expected), () -> "expected <" + expected + "> in:\n" + body);
    }

    // === title / heading (AbstractHTMLCalendarPage getTitle(request)) =======

    @Test
    void titleEscapesResourceName() throws Exception
    {
        contains(page("/rapla/calendar", "file=xssweek&" + WEEK + "&allocatable_id=" + xssId), "<title>" + XSS_ESCAPED + "</title>");
        contains(page("/rapla/internal_calendar", "file=xssweek&" + WEEK + "&allocatable_id=" + xssId), "<title>" + XSS_ESCAPED + "</title>");
    }

    @Test
    void titleOfNormalNameUnchanged() throws Exception
    {
        contains(page("/rapla/calendar", "file=xssweek&" + WEEK + "&allocatable_id=" + normalId), "<title>" + NORMAL + "</title>");
    }

    // === resource link list ===================================================

    @Test
    void resourceListEscapesName() throws Exception
    {
        String body = page("/rapla/calendar", "file=xssweek&" + WEEK + "&selected_allocatables=true");
        contains(body, "<td>" + XSS_ESCAPED + "</td>");
        contains(body, "<td>" + NORMAL + "</td>");
    }

    // === day-resource column headers ==========================================

    @Test
    void dayResourceHeaderEscapesName() throws Exception
    {
        String body = page("/rapla/calendar", "file=xssday&" + WEEK);
        contains(body, "<nobr>" + XSS_ESCAPED + "</nobr>");
        contains(body, "<nobr>" + NORMAL + "</nobr>");
    }

    // === garbled request parameters ===========================================

    @Test
    void garbledPagesParameterBehavesLikeAnAbsentOne() throws Exception
    {
        String query = "?user=homer&file=xssweek&" + WEEK + "&next=%3E%3E";
        var response = mockMvc.perform(get("/rapla/calendar" + query + "&pages=1/images/favicon.ico")).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        assertEquals(page("/rapla/calendar", query.substring(1)), response.getContentAsString());
    }

    @Test
    void unknownCalendarIsLoggedWithPathLinkKindAndLookupParameters() throws Exception
    {
        Logger notFoundLog = (Logger) LoggerFactory.getLogger("rapla.404");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        notFoundLog.addAppender(appender);
        try
        {
            var response = mockMvc.perform(get("/rapla/calendar?user=homer&file=nosuchcalendar")).andReturn().getResponse();
            assertEquals(404, response.getStatus());
            String line = appender.list.get(0).getFormattedMessage();
            assertTrue(line.endsWith("[calendar not found; path=/rapla/calendar, plain link, user=homer, file=nosuchcalendar]"), line);
            assertFalse(response.getContentAsString().contains("path="), response.getContentAsString());
        }
        finally
        {
            notFoundLog.detachAppender(appender);
        }
    }

    @Test
    void serverErrorIsLoggedInTheNormalLogNotInRapla404() throws Exception
    {
        Logger notFoundLog = (Logger) LoggerFactory.getLogger("rapla.404");
        Logger pageLog = (Logger) LoggerFactory.getLogger(CalendarPageController.class);
        ListAppender<ILoggingEvent> notFound = new ListAppender<>();
        ListAppender<ILoggingEvent> page = new ListAppender<>();
        notFound.start();
        page.start();
        notFoundLog.addAppender(notFound);
        pageLog.addAppender(page);
        try
        {
            var response = mockMvc.perform(get("/rapla/calendar?user=homer&file=noviewplugin")).andReturn().getResponse();
            assertEquals(500, response.getStatus());
            assertTrue(notFound.list.isEmpty(), () -> notFound.list.get(0).getFormattedMessage());
            assertTrue(page.list.get(0).getFormattedMessage().startsWith("page error 500 - No view available for exportfile 'noviewplugin'"),
                    page.list.get(0).getFormattedMessage());
        }
        finally
        {
            notFoundLog.detachAppender(notFound);
            pageLog.detachAppender(page);
        }
    }

    // === compact day (timeslot) column headers ================================

    @Test
    void compactDayHeaderEscapesName() throws Exception
    {
        String body = page("/rapla/calendar", "file=xsstimeslot&" + WEEK);
        contains(body, "<nobr>" + XSS_ESCAPED + "</nobr>");
        contains(body, "<nobr>" + NORMAL + "</nobr>");
    }
}
