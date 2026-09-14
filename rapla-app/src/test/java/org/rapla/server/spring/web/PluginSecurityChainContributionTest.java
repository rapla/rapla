package org.rapla.server.spring.web;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Locks the plugin-security extension contract (Option C, PRD point 3 / 2026-06-18):
 * vanilla {@code SecurityConfig} carries NO plugin-specific paths. A plugin that
 * needs an unauthenticated endpoint contributes its own higher-precedence
 * {@code @Order(1)} {@link SecurityFilterChain} with a narrow {@code securityMatcher},
 * mirroring the built-in {@code oauthHelperFilterChain}.
 *
 * <p>Spring picks the FIRST chain whose matcher matches and runs only that one
 * (no fall-through). So:
 * <ul>
 *   <li>a path the plugin chain matches is governed entirely by the plugin chain
 *       (here: permitAll);</li>
 *   <li>any path it does NOT match falls through to the main catch-all chain and
 *       inherits vanilla's {@code authenticated()} gate.</li>
 * </ul>
 *
 * The third test is the regression lock for removing the hard-coded
 * {@code /dhbw/status} / {@code /api/dhbw/stele} permitAll from vanilla: with no
 * plugin present those paths must be auth-gated, not silently public.
 */
@SpringBootTest(classes = {RaplaSpringBootApplication.class,
        PluginSecurityChainContributionTest.PluginChainConfig.class})
@AutoConfigureMockMvc
class PluginSecurityChainContributionTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = PluginSecurityChainContributionTest.class.getResourceAsStream("/testdefault.xml"))
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
    void pluginChainMakesItsPathPublic() throws Exception
    {
        // The @Order(1) plugin chain matches this exact path and permits it, so
        // the request is NOT auth-gated. (The sibling path below, governed by the
        // main chain, returns 401 — that contrast is the security signal. The
        // exact post-permit status is incidental, hence "not 401".)
        mockMvc.perform(get("/api/test-probe/public"))
                .andExpect(status().is(not(401)));
    }

    @Test
    void pathOutsideThePluginMatcherFallsThroughToTheAuthGate() throws Exception
    {
        // Sibling path NOT in the plugin chain's matcher: it falls through to the
        // main catch-all chain -> authenticated -> 401 (before any routing).
        mockMvc.perform(get("/api/test-probe/other"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void vanillaDoesNotAutoPermitPluginEndpoints() throws Exception
    {
        // /api/dhbw/stele is public ONLY when a plugin contributes a chain for it.
        // Vanilla carries no such path, so it must be auth-gated (401), not public.
        mockMvc.perform(get("/api/dhbw/stele"))
                .andExpect(status().isUnauthorized());
    }

    @TestConfiguration
    static class PluginChainConfig
    {
        @Bean
        @Order(1)
        SecurityFilterChain testProbeChain(HttpSecurity http) throws Exception
        {
            http
                    .securityMatcher("/api/test-probe/public")
                    .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                    .csrf(c -> c.disable())
                    .cors(Customizer.withDefaults());
            return http.build();
        }
    }
}
