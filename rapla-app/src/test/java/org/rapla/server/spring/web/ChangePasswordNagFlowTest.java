package org.rapla.server.spring.web;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.entities.User;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.rapla.storage.CachableStorageOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B3 — the change-password nag: a Spring {@code /login} (browser/SPA) login by a user
 * whose password is empty is redirected to {@code /change-password}; a user with a real
 * password is not.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class ChangePasswordNagFlowTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws Exception
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = ChangePasswordNagFlowTest.class.getResourceAsStream("/testdefault.xml"))
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
    CachableStorageOperator operator;

    @Test
    void changePasswordPageRenders() throws Exception
    {
        mockMvc.perform(get("/change-password"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Set a password")));
    }

    @Test
    void emptyPasswordLoginIsRedirectedToChangePassword() throws Exception
    {
        // give homer an empty password (the "unset" state)
        User homer = operator.getUser("homer");
        operator.changePassword(homer, "duffs".toCharArray(), new char[0]);

        mockMvc.perform(formLogin("/login").user("homer").password(""))
                .andExpect(redirectedUrl("/change-password"));
    }

    @Test
    void realPasswordLoginIsNotNagged() throws Exception
    {
        // monty keeps a real password → straight through to the app, never the nag page
        mockMvc.perform(formLogin("/login").user("monty").password("burns"))
                .andExpect(redirectedUrl("/app/"));
    }
}
