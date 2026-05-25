package org.rapla.server.spring.web;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.entities.Category;
import org.rapla.entities.User;
import org.rapla.framework.RaplaException;
import org.rapla.server.AuthenticationStore;
import org.rapla.server.internal.RaplaAuthentificationService;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression: a plugin that publishes an {@link AuthenticationStore} as a
 *  Spring bean (e.g. dhbwrapla's {@code DhbwNtlmAuthStore}) must end up
 *  wired into {@link RaplaAuthentificationService#authenticationStore}.
 *  Single-store model (2026-05-25): rapla allows AT MOST one external
 *  AuthStore active per server — vanilla has none (field is null);
 *  dhbwrapla NTLM or rapla JNDI/LDAP or a Keycloak adapter contributes
 *  exactly one; declaring two would throw at startup via Spring's
 *  {@code ObjectProvider.getIfAvailable} ambiguity check.
 *  See {@code ServerServiceConfig#raplaAuthentificationService}. */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@Tag("e2e")
class AuthenticationStoreInjectionTest
{
    @TempDir static Path tempDir;

    @DynamicPropertySource
    static void setup(DynamicPropertyRegistry r) throws IOException
    {
        Path dataXml = tempDir.resolve("data.xml");
        try (InputStream in = AuthenticationStoreInjectionTest.class.getResourceAsStream("/testdefault.xml"))
        {
            assertNotNull(in, "testdefault.xml must be on the test classpath");
            Files.copy(in, dataXml, StandardCopyOption.REPLACE_EXISTING);
        }
        r.add("rapla.store-file", () -> dataXml.toString());
    }

    @TestConfiguration
    static class StubAuthStoreConfig
    {
        @Bean
        AuthenticationStore stubPluginAuthStore()
        {
            return new AuthenticationStore()
            {
                @Override public boolean isEnabled() { return false; }
                @Override public boolean authenticate(String u, String p) { return false; }
                @Override public boolean initUser(User user, String username, String password, Category groupRoot) { return false; }
            };
        }
    }

    @Autowired
    private RaplaAuthentificationService authService;

    @Autowired
    private AuthenticationStore stubPluginAuthStore;

    /** ObjectProvider.getIfAvailable() in ServerServiceConfig must hand the
     *  plugin-provided AuthStore bean to RaplaAuthentificationService's
     *  constructor. */
    @Test
    void pluginAuthStoreBeanGetsInjectedAsTheSingleAuthStore() throws Exception
    {
        Field field = RaplaAuthentificationService.class.getDeclaredField("authenticationStore");
        field.setAccessible(true);
        AuthenticationStore wired = (AuthenticationStore) field.get(authService);
        assertNotNull(wired, "authenticationStore must be non-null when exactly one plugin bean is registered");
        assertTrue(wired == stubPluginAuthStore,
                "Spring must inject the plugin-provided AuthenticationStore @Bean as "
                        + "RaplaAuthentificationService.authenticationStore. If this fails, "
                        + "ObjectProvider.getIfAvailable() didn't see the plugin bean.");
    }
}
