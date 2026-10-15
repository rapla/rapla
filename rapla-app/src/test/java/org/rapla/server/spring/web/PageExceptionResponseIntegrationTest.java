package org.rapla.server.spring.web;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Page requests get a writable plain-text error from RaplaExceptionHandler (or keep the answer they already wrote); JSON clients and /api keep JSON. */
@SpringBootTest(classes = { RaplaSpringBootApplication.class, PageExceptionResponseIntegrationTest.TestRouteConfig.class })
@AutoConfigureMockMvc
@Tag("e2e")
class PageExceptionResponseIntegrationTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws Exception
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = PageExceptionResponseIntegrationTest.class.getResourceAsStream("/testdefault.xml"))
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

    private static final String BROWSER_ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";

    private MockHttpServletResponse call(String path) throws Exception
    {
        return call(path, null);
    }

    private MockHttpServletResponse call(String path, String accept) throws Exception
    {
        var request = get(path).header("Authorization", "Bearer " + OAuthTestSupport.loginAs(mockMvc, "homer", "duffs"));
        if (accept != null)
        {
            request = request.header("Accept", accept);
        }
        return mockMvc.perform(request).andReturn().getResponse();
    }

    @Test
    void pageThatAnsweredAndRethrewKeepsItsOwnErrorAndTheHandlerDoesNotFail() throws Exception
    {
        Logger resolverLog = (Logger) LoggerFactory.getLogger(ExceptionHandlerExceptionResolver.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        resolverLog.addAppender(appender);
        try
        {
            MockHttpServletResponse response = call("/__test/answered-page");
            assertEquals(500, response.getStatus());
            assertEquals("Internal error generating the calendar view.", response.getContentAsString());
            assertTrue(appender.list.isEmpty(), () -> appender.list.get(0).getFormattedMessage());
        }
        finally
        {
            resolverLog.detachAppender(appender);
        }
    }

    @Test
    void browserPageThatHadNotAnsweredGetsAPlainTextStatusLine() throws Exception
    {
        MockHttpServletResponse response = call("/__test/html-page", BROWSER_ACCEPT);
        assertEquals(400, response.getStatus());
        assertTrue(response.getContentType().startsWith("text/plain"), response.getContentType());
        assertEquals("400 Bad Request", response.getContentAsString());
    }

    @Test
    void nonApiJsonClientKeepsTheJsonShape() throws Exception
    {
        MockHttpServletResponse response = call("/__test/bad-argument", "application/json");
        assertEquals(400, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"), response.getContentType());
        assertTrue(response.getContentAsString().contains("\"message\":\"count must be positive\""), response.getContentAsString());
    }

    @Test
    void apiKeepsTheJsonShape() throws Exception
    {
        MockHttpServletResponse response = call("/api/__test/bad-argument");
        assertEquals(400, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"), response.getContentType());
        assertTrue(response.getContentAsString().contains("\"message\":\"count must be positive\""), response.getContentAsString());
    }

    @TestConfiguration
    static class TestRouteConfig
    {
        @RestController
        static class ThrowingController
        {
            @GetMapping("/__test/answered-page")
            public void answeredPage(HttpServletResponse response) throws ServletException
            {
                SafePageError.write(response, 500, "Internal error generating the calendar view.", null, null);
                throw new ServletException(new NumberFormatException("For input string: \"1/images/favicon.ico\""));
            }

            @GetMapping("/__test/html-page")
            public void htmlPage(HttpServletResponse response)
            {
                response.setContentType("text/html; charset=UTF-8");
                throw new NumberFormatException("For input string: \"x\"");
            }

            @GetMapping({ "/api/__test/bad-argument", "/__test/bad-argument" })
            public String badArgument()
            {
                throw new IllegalArgumentException("count must be positive");
            }
        }
    }
}
