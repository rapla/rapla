package org.rapla.server.spring.web;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.SyncStorageOperator;
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
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /change-password} is the B3 nag for users WITHOUT a password. It must not be a
 * generic "set my password without knowing the old one" endpoint: a user with a real password
 * (XSS / unattended browser → persistent takeover) and an externally managed identity (would
 * reopen the local-password path) are refused.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class ChangePasswordGateTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws Exception
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = ChangePasswordGateTest.class.getResourceAsStream("/testdefault.xml"))
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

    private int setPassword(String token, String newPassword) throws Exception
    {
        return mockMvc.perform(post("/change-password")
                        .header("Authorization", "Bearer " + token)
                        .param("newPassword", newPassword)
                        .param("confirmPassword", newPassword))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void userWithRealPasswordCannotReplaceItWithoutTheOldOne() throws Exception
    {
        String monty = OAuthTestSupport.loginAs(mockMvc, "monty", "burns");
        assertTrue(setPassword(monty, "hijacked") == 403, "must be refused");
        // the old password still works, the new one does not
        OAuthTestSupport.loginAs(mockMvc, "monty", "burns");
        assertFalse(((SyncStorageOperator) operator).isPasswordChangeRequired(operator.getUser("monty")));
    }

    @Test
    void nagUserWithoutPasswordCanSetOne() throws Exception
    {
        User homer = operator.getUser("homer");
        operator.changePassword(homer, "duffs".toCharArray(), new char[0]);
        String token = OAuthTestSupport.loginAs(mockMvc, "homer", "");
        assertTrue(setPassword(token, "duffs") / 100 == 3, "nag user must be allowed and redirected");
        assertFalse(((SyncStorageOperator) operator).isPasswordChangeRequired(operator.getUser("homer")));
    }

    @Test
    void externallyManagedIdentityCannotCreateALocalPassword() throws Exception
    {
        // a nag candidate (no password) whose identity is externally managed
        operator.changePassword(operator.getUser("homer"), "duffs".toCharArray(), new char[0]);
        String token = OAuthTestSupport.loginAs(mockMvc, "homer", "");
        setAuthenticationSource("homer", "keycloak");
        try
        {
            assertTrue(setPassword(token, "local-again") == 403, "external identity must be refused");
            assertTrue(((SyncStorageOperator) operator).isPasswordChangeRequired(operator.getUser("homer")),
                    "no local password may have been created");
        }
        finally
        {
            setAuthenticationSource("homer", null);
        }
    }

    private void setAuthenticationSource(String username, String source) throws Exception
    {
        User user = operator.getUser(username);
        User edit = (User) operator.editObjects(Collections.singletonList((Entity) user), null).values().iterator().next();
        edit.setAuthenticationSource(source);
        operator.storeAndRemove(Collections.singletonList((Entity) edit), Collections.emptyList(), null);
    }
}
