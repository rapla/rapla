package org.rapla.server.spring.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.entities.User;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.rapla.storage.StorageOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * PRD 097 (2026-07-15) — ad-hoc navigation links on rendered documents: script-free
 * {@code ?date=} links in the shell ("resolve the window as if today were this day"). Prev/next
 * are unit-free: prev = window start minus one day (the anchor snaps it into the previous
 * period), next = the exclusive window end's date (the first day of the next period) — correct
 * for weeks AND months, February included.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc(addFilters = false)
class DocumentNavTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyFixture() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = DocumentNavTest.class.getResourceAsStream("/testdefault.xml"))
        {
            Files.copy(in, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry)
    {
        registry.add("rapla.file-datasources.raplafile", () -> dataFile.toAbsolutePath().toString());
    }

    @Autowired MockMvc mockMvc;
    @Autowired DocumentCatalogService documents;
    @Autowired StorageOperator operator;

    private String render(String uri) throws Exception
    {
        var response = mockMvc.perform(get(uri)).andReturn().getResponse();
        assertEquals(200, response.getStatus(), uri);
        return response.getContentAsString();
    }

    @Test
    @WithMockUser(username = "homer")
    void dateParameterResolvesTheWindowAsIfTodayWereThatDay() throws Exception
    {
        // 2026-03-04 is a Wednesday; the wochenplan week window snaps to Mo 2026-03-02.
        String page = render("/api/documents/wochenplan?resource=a1&date=2026-03-04");
        assertTrue(page.contains("Mo 02.03."), "week of the reference date expected: " + page);
    }

    @Test
    @WithMockUser(username = "homer")
    void weekNavLinksStepOneWeek() throws Exception
    {
        String page = render("/api/documents/wochenplan?resource=a1&date=2026-03-04");
        assertTrue(page.contains("date=2026-03-01"), "prev = window start minus 1 day: " + page);
        assertTrue(page.contains("date=2026-03-09"), "next = exclusive window end: " + page);
        assertTrue(page.contains("02.03.2026") && page.contains("08.03.2026"),
                "range label expected: " + page);
    }

    @Test
    @WithMockUser(username = "homer")
    void monthNavLinksStepCalendarMonthsNotDurations() throws Exception
    {
        // February 2026 has 28 days — duration-shifting would land next on Mar 29, not Mar 1.
        String page = render("/api/documents/monatsplan?resource=a1&date=2026-02-10");
        assertTrue(page.contains("date=2026-01-31"), "prev = Feb 1 minus 1 day: " + page);
        assertTrue(page.contains("date=2026-03-01"), "next = the exclusive month end: " + page);
    }

    @Test
    @WithMockUser(username = "homer")
    void navLinksCarryOtherParametersThroughAndTodayDropsTheDate() throws Exception
    {
        String page = render("/api/documents/wochenprogramm?date=2026-03-04&resource=a1");
        assertTrue(page.contains("resource=a1"), "declared params must survive navigation: " + page);
        assertTrue(page.contains("href=\"?resource=a1\""), "Heute = same URL without date: " + page);
    }

    @Test
    @WithMockUser(username = "homer")
    void malformedDateIsA400() throws Exception
    {
        var response = mockMvc.perform(get("/api/documents/wochenplan?date=banana"))
                .andReturn().getResponse();
        assertEquals(400, response.getStatus());
    }

    /**
     * Nav is TEMPLATE data ({@code {{#nav}}…{{/nav}}}), and {@code ?date=} is a caller gesture
     * that OUTRANKS stored defaults (like {@code ?from/?to}): a document with pinned absolute
     * dates shows its pinned range on the bare URL, but a nav click navigates away from it.
     */
    @Test
    @WithMockUser(username = "homer")
    void navRendersWhereTheTemplatePlacesItAndDateOutranksPinnedDefaults() throws Exception
    {
        User admin = operator.getUser("homer");
        List<String> errors = documents.save("nav_pinned", "rapla_kalender",
                "{{#nav}}<nav class=\"rapla-nav\"><a href=\"{{prevUrl}}\">p</a>"
                        + "<span>{{label}}</span></nav>{{/nav}}",
                true, List.of(),
                "{\"filter\":{\"from\":\"2026-06-15T00:00:00\",\"to\":\"2026-06-22T00:00:00\"}}",
                null, admin);
        assertEquals(List.of(), errors);
        try
        {
            String pinned = render("/api/documents/nav_pinned?resource=a1");
            assertTrue(pinned.contains("15.06.2026 – 21.06.2026"),
                    "bare URL shows the pinned range: " + pinned);
            String navigated = render("/api/documents/nav_pinned?resource=a1&date=2026-03-04");
            assertTrue(navigated.contains("02.03.2026 – 08.03.2026"),
                    "?date= outranks the pinned defaults: " + navigated);
        }
        finally
        {
            documents.delete("nav_pinned", admin);
        }
    }

    @Test
    @WithMockUser(username = "homer")
    void templatesWithoutANavSectionRenderNone() throws Exception
    {
        User admin = operator.getUser("homer");
        List<String> errors = documents.save("nav_less", "rapla_kalender",
                "<p>plain</p>", true, List.of(), null, null, admin);
        assertEquals(List.of(), errors);
        try
        {
            String page = render("/api/documents/nav_less?resource=a1");
            assertFalse(page.contains("<nav"), "no {{#nav}} in the template = no nav: " + page);
            // Affordances are template content: without the partials the page has NO chrome —
            // the signage case (print hint moved out of the shell 2026-07-15).
            assertFalse(page.contains("class=\"rapla-print-hint\""),
                    "no {{> rapla/print-hint}} = no hint: " + page);
        }
        finally
        {
            documents.delete("nav_less", admin);
        }
    }

    @Test
    @WithMockUser(username = "homer")
    void builtinTemplatesRenderTheNavBlock() throws Exception
    {
        String page = render("/api/documents/wochenplan?resource=a1");
        assertTrue(page.contains("<nav class=\"rapla-nav\""),
                "builtin templates carry the nav block: " + page);
        assertTrue(page.contains("Heute"), page);
    }
}
