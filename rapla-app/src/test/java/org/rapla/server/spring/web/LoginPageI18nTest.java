package org.rapla.server.spring.web;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.entities.configuration.Preferences;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.internal.AbstractRaplaLocale;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;

/**
 * The login page is rendered in the server-configured language — the admin
 * "Server Sprache" setting, stored as the {@link AbstractRaplaLocale#LOCALE}
 * system preference (e.g. {@code de_DE}); when that is unset it follows the
 * browser's {@code Accept-Language}.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class LoginPageI18nTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws Exception
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = LoginPageI18nTest.class.getResourceAsStream("/testdefault.xml"))
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

    @Test
    void serverLanguageWinsElseBrowserLocale() throws Exception
    {
        // no server language set → browser Accept-Language decides
        mockMvc.perform(get("/login").accept(MediaType.TEXT_HTML).header("Accept-Language", "de"))
                .andExpect(content().string(containsString("Benutzername")))
                .andExpect(content().string(containsString("lang=\"de\"")))
                .andExpect(content().string(not(containsString("Username"))));

        mockMvc.perform(get("/login").accept(MediaType.TEXT_HTML).header("Accept-Language", "en"))
                .andExpect(content().string(containsString("Username")))
                .andExpect(content().string(containsString("lang=\"en\"")));

        // Server Sprache set to de_DE (what the Swing admin panel writes) → wins
        // over the browser's en
        Preferences edit = facade.edit(facade.getSystemPreferences());
        edit.putEntry(AbstractRaplaLocale.LOCALE, "de_DE");
        facade.store(edit);
        try
        {
            mockMvc.perform(get("/login").accept(MediaType.TEXT_HTML).header("Accept-Language", "en"))
                    .andExpect(content().string(containsString("Benutzername")))
                    .andExpect(content().string(containsString("lang=\"de\"")));
        }
        finally
        {
            Preferences reset = facade.edit(facade.getSystemPreferences());
            reset.removeEntry(AbstractRaplaLocale.LOCALE.getId());
            facade.store(reset);
        }
    }

    @Test
    void langChooserCookieBeatsBrowserAndServerSetting() throws Exception
    {
        // the page carries a language chooser
        mockMvc.perform(get("/login").accept(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("id=\"lang\"")));

        // ?lang=de → German page + persistent choice cookie, browser is en
        mockMvc.perform(get("/login").param("lang", "de")
                        .accept(MediaType.TEXT_HTML).header("Accept-Language", "en"))
                .andExpect(content().string(containsString("Benutzername")))
                .andExpect(cookie().value("raplaLocale", "de"));

        // cookie alone → German, beats the browser's en
        mockMvc.perform(get("/login").cookie(new jakarta.servlet.http.Cookie("raplaLocale", "de"))
                        .accept(MediaType.TEXT_HTML).header("Accept-Language", "en"))
                .andExpect(content().string(containsString("Benutzername")));

        // cookie beats the server language too (server de, cookie en → English)
        Preferences edit = facade.edit(facade.getSystemPreferences());
        edit.putEntry(AbstractRaplaLocale.LOCALE, "de_DE");
        facade.store(edit);
        try
        {
            mockMvc.perform(get("/login").cookie(new jakarta.servlet.http.Cookie("raplaLocale", "en"))
                            .accept(MediaType.TEXT_HTML).header("Accept-Language", "en"))
                    .andExpect(content().string(containsString("Username")));
        }
        finally
        {
            Preferences reset = facade.edit(facade.getSystemPreferences());
            reset.removeEntry(AbstractRaplaLocale.LOCALE.getId());
            facade.store(reset);
        }

        // unknown language is ignored: no cookie set, browser decides
        mockMvc.perform(get("/login").param("lang", "xx")
                        .accept(MediaType.TEXT_HTML).header("Accept-Language", "en"))
                .andExpect(content().string(containsString("Username")))
                .andExpect(cookie().doesNotExist("raplaLocale"));
    }
}
