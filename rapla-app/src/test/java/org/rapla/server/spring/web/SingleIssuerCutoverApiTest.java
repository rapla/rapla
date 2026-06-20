package org.rapla.server.spring.web;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRD 072 Phase 6 — single-issuer cutover, full-context proof. With the DEFAULT
 * config ({@code rapla.oauth.trust-external-issuers} unset → false) and an
 * external IdP (Keycloak) ENABLED, {@code /api}:
 * <ul>
 *   <li>accepts a rapla-issued access token (via password grant) → 200;</li>
 *   <li>rejects a token whose {@code iss} is the external IdP → 401.</li>
 * </ul>
 * The tier-1 {@code JwtConfigSingleIssuerCutoverTest} proves the decoder seam;
 * this proves the wired {@code jwtDecoder} bean honours the cutover default even
 * with a provider configured.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class SingleIssuerCutoverApiTest
{
    @TempDir static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyTestData() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = SingleIssuerCutoverApiTest.class.getResourceAsStream("/testdefault.xml"))
        {
            assertNotNull(in, "testdefault.xml must be on the classpath");
            Files.copy(in, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry)
    {
        registry.add("rapla.file-datasources.raplafile", () -> dataFile.toAbsolutePath().toString());
        // External provider ENABLED — its login/chooser still works, but its
        // tokens are NOT trusted at /api under the cutover default.
        registry.add("rapla.oauth.external.keycloak.enabled", () -> "true");
        registry.add("rapla.oauth.external.keycloak.base-url", () -> "https://keycloak.example.com");
        registry.add("rapla.oauth.external.keycloak.realm", () -> "dhbw");
        registry.add("rapla.oauth.external.keycloak.client-id", () -> "rapla-client");
        // NOTE: rapla.oauth.trust-external-issuers deliberately UNSET → defaults to false.
    }

    @Autowired
    MockMvc mockMvc;

    @Test
    void raplaIssuedTokenAuthorizesApi() throws Exception
    {
        String access = OAuthTestSupport.loginAs(mockMvc, "homer", "duffs");
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("homer"));
    }

    @Test
    void externalIssuerTokenIsRejectedAtApi() throws Exception
    {
        // A well-formed JWT carrying the Keycloak issuer, signed by a throwaway key
        // (no live IdP needed). Under the cutover it never reaches the Keycloak
        // decoder — only rapla's local decoder is trusted → 401.
        String foreign = foreignKeycloakToken();
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + foreign))
                .andExpect(status().isUnauthorized());
    }

    private static String foreignKeycloakToken() throws Exception
    {
        RSAKey key = new RSAKeyGenerator(2048).keyID("foreign-key").generate();
        long now = System.currentTimeMillis();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("external-user")
                .issuer("https://keycloak.example.com/realms/dhbw")
                .issueTime(new Date(now))
                .expirationTime(new Date(now + 3600_000))
                .claim("preferred_username", "someone")
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
                claims);
        jwt.sign(new RSASSASigner(key.toRSAPrivateKey()));
        return jwt.serialize();
    }
}
