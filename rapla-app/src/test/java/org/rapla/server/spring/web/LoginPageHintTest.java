package org.rapla.server.spring.web;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.entities.User;
import org.rapla.facade.RaplaFacade;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.rapla.storage.CachableStorageOperator;
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

/**
 * B3 — the login page advertises the "default admin / empty password" hint ONLY while
 * that default is actually in effect, and hides it once the admin sets a real password.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class LoginPageHintTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws Exception
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = LoginPageHintTest.class.getResourceAsStream("/testdefault.xml"))
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
    @Autowired
    CachableStorageOperator operator;

    @Test
    void hintShownOnlyWhileAdminPasswordIsEmpty() throws Exception
    {
        // no admin user in the fixture → no hint
        mockMvc.perform(get("/login").accept(MediaType.TEXT_HTML))
                .andExpect(content().string(not(containsString("Dev default"))));

        // create an admin with an empty password → hint appears
        User admin = facade.newUser();
        admin.setUsername("admin");
        admin.setName("Administrator");
        facade.store(admin);
        operator.changePassword(facade.getUser("admin"), new char[0], new char[0]);
        mockMvc.perform(get("/login").accept(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Dev default")));

        // admin sets a real password → hint disappears
        operator.changePassword(facade.getUser("admin"), new char[0], "realpw".toCharArray());
        mockMvc.perform(get("/login").accept(MediaType.TEXT_HTML))
                .andExpect(content().string(not(containsString("Dev default"))));
    }
}
