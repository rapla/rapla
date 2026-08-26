package org.rapla.server.spring.web;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.entities.User;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.RaplaFacade;
import org.rapla.plugin.autoexport.AutoExportPlugin;
import org.rapla.plugin.export2ical.Export2iCalPlugin;
import org.rapla.plugin.urlencryption.UrlEncryptionPlugin;
import org.rapla.scheduler.sync.SynchronizedCompletablePromise;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * URL-encryption plugin: a calendar published with {@code URL_ENCRYPTION} on is reachable ONLY
 * through its {@code ?key=} capability URL — plain {@code ?user=&file=} must be refused on every
 * export path — and the {@code /api/urlencryption} minting endpoint only signs URLs for the
 * caller's own calendars (a user must not be able to mint a key for someone else's calendar).
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class EncryptedCalendarGateTest
{
    private static final String FILE = "secretcal";

    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = EncryptedCalendarGateTest.class.getResourceAsStream("/testdefault.xml"))
        {
            assertNotNull(in, "testdefault.xml must be on the classpath");
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
    RaplaFacade facade;

    private void publishEncrypted() throws Exception
    {
        User homer = facade.getUser("homer");
        CalendarSelectionModel model = facade.newCalendarModel(homer);
        model.setOption(AutoExportPlugin.HTML_EXPORT, "true");
        model.setOption(Export2iCalPlugin.ICAL_EXPORT, "true");
        model.setOption(UrlEncryptionPlugin.URL_ENCRYPTION, UrlEncryptionPlugin.ALGO_V2);
        SynchronizedCompletablePromise.waitFor(model.save(FILE), 10000);
    }

    private String mintKey(String token, String plain) throws Exception
    {
        return mockMvc.perform(post("/api/urlencryption")
                        .param("algo", "v2")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(plain)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void plainParametersAreRefusedOnEveryExportPath() throws Exception
    {
        publishEncrypted();
        for (String path : new String[] { "/rapla/calendar", "/rapla/calendar.csv", "/rapla/ical" })
        {
            // Tomcat maps the DispatcherServlet to "/": servletPath is the full path, pathInfo null.
            mockMvc.perform(get(path).servletPath(path).param("user", "homer").param("file", FILE))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void capabilityUrlStillWorks() throws Exception
    {
        publishEncrypted();
        String homer = OAuthTestSupport.loginAs(mockMvc, "homer", "duffs");
        String key = mintKey(homer, "user=homer&file=" + FILE);
        mockMvc.perform(get("/rapla/calendar").param("key", key)).andExpect(status().isOk());
        mockMvc.perform(get("/rapla/ical").param("key", key)).andExpect(status().isOk());
    }

    @Test
    void mintingIsLimitedToTheCallersOwnCalendars() throws Exception
    {
        String monty = OAuthTestSupport.loginAs(mockMvc, "monty", "burns");
        mockMvc.perform(post("/api/urlencryption")
                        .param("algo", "v2")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("user=homer&file=" + FILE)
                        .header("Authorization", "Bearer " + monty))
                .andExpect(status().isForbidden());
        mintKey(monty, "user=monty&file=mine");
    }
}
