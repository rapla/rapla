package org.rapla.server.spring.web;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The "stay signed in on this device" tick is a per-browser preference: it must survive
 * the logout that deliberately kills the remember-me cookie, so the login page comes back
 * with the box in the state the user last chose.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class RememberMeChoiceTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws Exception
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = RememberMeChoiceTest.class.getResourceAsStream("/testdefault.xml"))
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

    @Test
    void tickedBoxComesBackTickedOnTheNextLoginPage() throws Exception
    {
        MvcResult login = mockMvc.perform(post("/login")
                        .param("username", "monty").param("password", "burns")
                        .param("remember-me", "on")
                        .with(csrf()).session(new MockHttpSession()))
                .andReturn();
        Cookie choice = login.getResponse().getCookie("rapla-remember-choice");
        assertNotNull(choice, "login must record the remember-me choice");
        assertEquals("1", choice.getValue());
        assertTrue(choice.getMaxAge() > 30 * 24 * 3600, "choice must outlive the remember-me cookie");

        String html = mockMvc.perform(get("/login").cookie(choice))
                .andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("name=\"remember-me\" value=\"on\" checked"),
                "checkbox must render checked, was: " + snippet(html));
    }

    @Test
    void untickedBoxStaysUnticked() throws Exception
    {
        MvcResult login = mockMvc.perform(post("/login")
                        .param("username", "monty").param("password", "burns")
                        .with(csrf()).session(new MockHttpSession()))
                .andReturn();
        Cookie choice = login.getResponse().getCookie("rapla-remember-choice");
        assertNotNull(choice, "login must record the remember-me choice");
        assertEquals("0", choice.getValue());

        String html = mockMvc.perform(get("/login").cookie(choice))
                .andReturn().getResponse().getContentAsString();
        assertFalse(html.contains("checked"), "checkbox must render unchecked, was: " + snippet(html));
    }

    private static String snippet(String html)
    {
        int i = html.indexOf("remember-me");
        return i < 0 ? "<no checkbox>" : html.substring(Math.max(0, i - 80), Math.min(html.length(), i + 80));
    }
}
